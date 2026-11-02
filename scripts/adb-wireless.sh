#!/usr/bin/env bash
# Wireless ADB helper for PocketWeibo development on macOS.
#
# Works with Android 11+ "Wireless debugging" (dynamic port + pairing code) and with
# legacy "ADB over network" (fixed :5555).
#
# The script discovers the phone automatically, no configuration needed:
#   1. an explicit IP[:PORT] argument or DEVICE_IP env,
#   2. an already-connected device,
#   3. mDNS (_adb-tls-connect) — for paired Android 11+ devices,
#   4. a cached last-known host + port scan of the dynamic range 30000–50000
#      (that range is where Android picks the wireless-debugging TLS port;
#      mDNS can be blocked on some routers / stop advertising while connected),
#   5. a subnet scan for open :5555 — for legacy-mode devices.
#
# Workflow after a new feature:
#   1. ./scripts/adb-wireless.sh connect          # pair once, then this every session
#   2. ./scripts/adb-wireless.sh install-debug    # builds + installs debug build over Wi-Fi
#      (or) ./scripts/adb-wireless.sh install-release  # signed release install onto the phone
#   3. ./scripts/adb-wireless.sh logcat           # follow crash logs
#
# On-device enabling: Settings > Developer options > Wireless debugging > ON
# First-time pairing: tap "Pair device with pairing code" and run:
#   ./scripts/adb-wireless.sh pair <pairing-ip:port> <6-digit-code>
# then run `connect <ip>` — the dynamic TLS port is found by scanning
# PORT_SCAN_START–PORT_SCAN_END on that host (python3 asyncio, a few seconds).
# The working address is cached locally (~/.cache/pocketweibo-adb), so plain
# `connect` reconnects instantly from then on.
#
# Usage:
#   ./scripts/adb-wireless.sh pair [IP:PORT [CODE]]
#   ./scripts/adb-wireless.sh connect [IP[:PORT]]  # omit to auto-discover
#   ./scripts/adb-wireless.sh status
#   ./scripts/adb-wireless.sh disconnect [IP]
#   ./scripts/adb-wireless.sh enable-tcpip       # only for USB-connected device; frees :5555
#   ./scripts/adb-wireless.sh install-debug [extra gradle args]
#   ./scripts/adb-wireless.sh install-release [extra gradle args]
#   ./scripts/adb-wireless.sh logcat
#   ./scripts/adb-wireless.sh crash
#
# ENV:
#   DEVICE_IP         optional phone host (bare IP or ip:port); none = auto-discover
#   DEVICE_PORT       legacy fixed ADB port (default 5555)
#   PORT_SCAN_START   dynamic wireless-debugging port range start (default 30000)
#   PORT_SCAN_END     dynamic wireless-debugging port range end   (default 50000)
#   PORT_SCAN_JOBS    parallel probes (default 256)
#
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

DEVICE_IP="${DEVICE_IP:-}"
DEVICE_PORT="${DEVICE_PORT:-5555}"
PORT_SCAN_START="${PORT_SCAN_START:-30000}"
PORT_SCAN_END="${PORT_SCAN_END:-50000}"
PORT_SCAN_JOBS="${PORT_SCAN_JOBS:-256}"
CACHE_DIR="${XDG_CACHE_HOME:-$HOME/.cache}/pocketweibo-adb"
CACHE_TARGET_FILE="$CACHE_DIR/last_target"

ok() { echo "[OK] $*"; }
fail() { echo "[FAIL] $*" >&2; exit 1; }
usage() { sed -n '2,45p' "$0" | sed 's/^# \{0,1\}//'; }

if command -v adb >/dev/null 2>&1; then
  ADB="$(command -v adb)"
elif [ -x "$HOME/Library/Android/sdk/platform-tools/adb" ]; then
  ADB="$HOME/Library/Android/sdk/platform-tools/adb"
else
  fail "adb not found; install platform-tools via Android Studio or: brew install --cask android-platform-tools"
fi

# JDK for Gradle: $JAVA_HOME, else Android Studio's bundled JetBrains Runtime.
if [ -z "${JAVA_HOME:-}" ]; then
  JBR="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
  if [ -x "$JBR/bin/java" ]; then
    export JAVA_HOME="$JBR"
    export PATH="$JAVA_HOME/bin:$PATH"
  fi
fi

esc_ip() { echo "$1" | sed 's/[.]/\\./g'; }

wireless_serial() {
  # First adb-over-Wi-Fi device currently connected ("ip:port  device"), empty otherwise.
  $ADB devices | awk 'NR>1 && $2=="device" {print $1}' | grep ':' | head -1 || true
}

any_connected_serial() {
  $ADB devices | awk 'NR>1 && $2=="device" {print $1}' | head -1 || true
}

connected_to() {
  $ADB devices | awk 'NR>1 && $2=="device" {print $1}' | grep -qx "$(esc_ip "$1")"
}

remember_target() {
  mkdir -p "$CACHE_DIR" 2>/dev/null || return 0
  echo "$1" > "$CACHE_TARGET_FILE" 2>/dev/null || true
}

cached_target() {
  [ -f "$CACHE_TARGET_FILE" ] || return 0
  head -1 "$CACHE_TARGET_FILE" | tr -d '[:space:]'
}

cached_host() {
  local t
  t="$(cached_target)"
  [ -n "$t" ] && echo "${t%%:*}"
}

# Probe one host for the legacy fixed port; prints "ip" on hit (used via xargs).
probe_5555() {
  nc -z -G 1 "$1" "$DEVICE_PORT" >/dev/null 2>&1 && echo "$1"
  return 0
}

# Scan mDNS-announced wireless-debugging services and print "ip:port" (or empty).
mdns_discover() {
  $ADB mdns check >/dev/null 2>&1 || return 0
  # Typical line: adb-<serial>-<name>	_adb-tls-connect._tcp.	<ip>:<port>
  $ADB mdns services 2>/dev/null \
    | grep -i "_adb-tls-connect._tcp" \
    | grep -oE "[0-9]{1,3}(\.[0-9]{1,3}){3}:[0-9]+" | head -1 || true
}

# Detect the local LAN prefix from the default route's interface, e.g. "192.168.1"
local_lan_prefix() {
  local iface prefix
  iface=$(route -n get default 2>/dev/null | awk '/interface:/{print $2}')
  [ -n "$iface" ] || return 1
  prefix=$(ifconfig "$iface" 2>/dev/null | awk '/inet /{print $2}' | grep -E '^[0-9]+\.' | head -1)
  [ -n "$prefix" ] || return 1
  echo "$prefix" | awk -F. '{print $1"."$2"."$3}'
}

# Fall back: scan the /24 subnet for devices with the legacy port open.
subnet_scan_legacy() {
  local prefix i
  prefix="$(local_lan_prefix)" || return 1
  echo "[..] Scanning ${prefix}.1–254 for open :${DEVICE_PORT} …" >&2
  for i in $(seq 1 254); do echo "${prefix}.${i}"; done \
    | xargs -n 1 -P 64 "$0" __probe_host 2>/dev/null | head -1
}

# Scan the dynamic wireless-debugging port range on one host and print open ports.
# Uses a Python asyncio scanner (one process — fast); falls back to nc+xargs without Python 3.
port_scan() {
  local host="$1"
  if command -v python3 >/dev/null 2>&1; then
    python3 - "$host" "$PORT_SCAN_START" "$PORT_SCAN_END" <<'PY'
import asyncio, resource, sys

host, start, end = sys.argv[1], int(sys.argv[2]), int(sys.argv[3])
try:
    soft, _ = resource.getrlimit(resource.RLIMIT_NOFILE)
except Exception:
    soft = 256
concurrency = max(64, min(1500, soft // 2))
sem = asyncio.Semaphore(concurrency)

async def probe(port, out):
    async with sem:
        try:
            _r, w = await asyncio.wait_for(asyncio.open_connection(host, port), 0.6)
            w.close()
            out.append(port)
        except Exception:
            pass

async def main():
    out = []
    await asyncio.gather(*(probe(p, out) for p in range(start, end + 1)))
    for p in sorted(out):
        print(p)

asyncio.run(main())
PY
  else
    seq "$PORT_SCAN_START" "$PORT_SCAN_END" \
      | xargs -n 1 -P "$PORT_SCAN_JOBS" -I{} sh -c "nc -z -G 1 '$host' {} 2>/dev/null && echo {}" \
      | sort -n
  fi
}

# Try to reach a known host: cached target, then legacy fixed port, then the dynamic range.
# Prints the connected "ip:port" on success.
connect_host() {
  local host="$1" port cached
  cached="$(cached_target)"
  case "$cached" in
    "$host":*)
      if $ADB connect "$cached" >/dev/null 2>&1 && connected_to "$cached"; then
        echo "$cached"
        return 0
      fi
      ;;
  esac
  if $ADB connect "$host:$DEVICE_PORT" >/dev/null 2>&1 && connected_to "$host:$DEVICE_PORT"; then
    echo "$host:$DEVICE_PORT"
    return 0
  fi
  echo "[..] Scanning $host ports ${PORT_SCAN_START}–${PORT_SCAN_END} for Wireless debugging…" >&2
  while IFS= read -r port; do
    [ -n "$port" ] || continue
    if $ADB connect "$host:$port" >/dev/null 2>&1 && connected_to "$host:$port"; then
      echo "$host:$port"
      return 0
    fi
  done < <(port_scan "$host")
  return 1
}

cmd_status() {
  echo "--- adb devices ---"
  $ADB devices -l
  echo "--- mDNS services ---"
  $ADB mdns services 2>/dev/null || echo "(mDNS not available/disabled)"
  echo "--- cached host ---"
  printf '%s\n' "$(cached_host)" | sed 's/^$/ (none)/'
}

cmd_pair() {
  local target="${1:-}" code="${2:-}"
  if [ -z "$target" ] || [ -z "$code" ]; then
    echo "Open the phone: Developer options > Wireless debugging > 'Pair device with pairing code'."
    printf "Pairing address shown on phone (e.g. 192.168.1.10:41234): "
    read -r target
    printf "6-digit pairing code (e.g. 123456): "
    read -r code
  fi
  case "$target" in
    *:*) : ;;
    *) target="$target:$DEVICE_PORT" ;;
  esac
  echo "[..] adb pair $target"
  if $ADB pair "$target" "$code"; then
    ok "Paired with $target. Now run: $0 connect ${target%%:*}"
  else
    fail "Pairing failed (code expired? — regenerate the pairing dialog and retry)"
  fi
}

cmd_connect() {
  local arg="${1:-}" target="" host=""

  # 1. Explicit argument or DEVICE_IP env: bare host or host:port.
  if [ -n "$arg" ]; then
    case "$arg" in *:*) target="$arg" ;; *) host="$arg" ;; esac
  elif [ -n "$DEVICE_IP" ]; then
    case "$DEVICE_IP" in *:*) target="$DEVICE_IP" ;; *) host="$DEVICE_IP" ;; esac
  fi

  # 2. Direct connect when we already have host:port.
  if [ -n "$target" ]; then
    if $ADB connect "$target" >/dev/null 2>&1 && connected_to "$target"; then
      remember_target "$target"
      ok "Connected: $target"
      return 0
    fi
    fail "Could not connect to $target (pairing expired? run: $0 pair)"
  fi

  # 3. Already connected over Wi-Fi? nothing to do.
  local have
  have="$(wireless_serial)"
  if [ -n "$have" ] && [ -z "$host" ]; then
    remember_target "$have"
    ok "Already connected: $have"
    return 0
  fi

  # 4. mDNS (paired devices may announce their dynamic port).
  if [ -z "$target" ]; then
    local discovered
    discovered="$(mdns_discover || true)"
    if [ -n "$discovered" ] && { [ -z "$host" ] || [ "${discovered%%:*}" = "$host" ]; }; then
      if $ADB connect "$discovered" >/dev/null 2>&1 && connected_to "$discovered"; then
        remember_target "$discovered"
        ok "Connected (mDNS): $discovered"
        return 0
      fi
    fi
  fi

  # 5. Known/cached host: scan the dynamic wireless-debugging port range.
  if [ -z "$host" ]; then
    host="$(cached_host)"
    [ -n "$host" ] && echo "[..] Using cached phone host: $host"
  fi
  if [ -n "$host" ]; then
    if target="$(connect_host "$host")"; then
      remember_target "$target"
      ok "Connected: $target"
      return 0
    fi
    fail "Reachable host $host, but no ADB port found in ${PORT_SCAN_START}–${PORT_SCAN_END}.
  - Is 'Wireless debugging' ON (and the phone screen on)? Toggle it off/on to get a fresh port.
  - Pairing expired after a reboot/toggle? run: $0 pair <pair-ip:port> <code>"
  fi

  # 6. Legacy devices on the subnet (fixed :5555).
  host="$(subnet_scan_legacy || true)"
  if [ -n "$host" ]; then
    if target="$(connect_host "$host")"; then
      remember_target "$target"
      ok "Connected: $target"
      return 0
    fi
  fi

  fail "No device found.
  - Phone on Wireless debugging: run: $0 connect <phone-ip>  (one time; the host is cached)
  - Or read 'IP address & Port' on the Wireless debugging screen: $0 connect <ip:port>
  - Never paired on this Mac: $0 pair <pair-ip:port> <code>"
}

cmd_disconnect() {
  local ip="${1:-${DEVICE_IP:-}}"
  if [ -n "$ip" ]; then
    $ADB disconnect "$ip" >/dev/null 2>&1 || true
    ok "Disconnected from $ip"
  else
    $ADB disconnect >/dev/null 2>&1 || true
    ok "Disconnected"
  fi
}

cmd_enable_tcpip() {
  local -a serial
  serial=($($ADB devices | awk 'NR>1 && $2=="device" {print $1}' | grep -v ':' || true))
  [ "${#serial[@]}" -ge 1 ] || fail "No USB-connected device; plug in a cable first"
  $ADB -s "${serial[0]}" tcpip "$DEVICE_PORT"
  sleep 2
  if subnet_scan_legacy | grep -q .; then
    ok "Device is now reachable over Wi-Fi on fixed :$DEVICE_PORT"
    cmd_connect
  else
    echo "[..] USB cable required later: run $ADB tcpip $DEVICE_PORT once while connected."
  fi
}

install_one() {
  local variant="$1"; shift
  local serial
  serial="$(wireless_serial)"
  [ -n "$serial" ] || { echo "[..] Not connected yet — attempting connect"; cmd_connect; serial="$(wireless_serial)" || true; }
  [ -n "$serial" ] || fail "No connected device; run: $0 connect"
  # bash 3.2 compatible ucfirst (no ${var^}, which needs bash 4)
  local task="installDebug"
  [ "$variant" = "release" ] && task="installRelease"
  echo "[..] ./gradlew $task $*   →  device $serial"
  ./gradlew "$task" --stacktrace "$@"
  ok "Installed ${variant} build on $serial"
  echo "[..] Launch with: $ADB -s $serial shell monkey -p com.pocketweibo -c android.intent.category.LAUNCHER 1"
}

cmd_logcat() {
  local serial
  serial="$(any_connected_serial)" || true
  [ -n "${serial:-}" ] || fail "No connected device; run: $0 connect"
  $ADB -s "$serial" logcat -v color "$@"
}

cmd_crash() {
  local serial
  serial="$(any_connected_serial)" || true
  [ -n "${serial:-}" ] || fail "No connected device; run: $0 connect"
  $ADB -s "$serial" logcat -c
  # Show only fatal exceptions (app crashes) as they happen.
  $ADB -s "$serial" logcat -v color "AndroidRuntime:E" "FatalSignal:F" "*:S"
}

# internal helpers for scanning (never document)
case "${1:-}" in
  "__probe_host") probe_5555 "${2:-}"; exit 0 ;;
esac

# dispatch
case "${1:-}" in
  "")               usage; exit 0 ;;
  "pair")           cmd_pair "${2:-}" "${3:-}" ;;
  "connect")        cmd_connect "${2:-}" ;;
  "status")         cmd_status ;;
  "disconnect")     cmd_disconnect "${2:-}" ;;
  "enable-tcpip")   cmd_enable_tcpip ;;
  "logcat")         shift; cmd_logcat "$@" ;;
  "crash")          cmd_crash ;;
  "install-debug")  shift; install_one debug "$@" ;;
  "install-release") shift; install_one release "$@" ;;
  "install")        fail "Use install-debug or install-release" ;;
  "-h"|"--help")    usage; exit 0 ;;
  *)                usage; fail "Unknown command: $1" ;;
esac
