#!/usr/bin/env bash
# Wireless ADB helper for PocketWeibo development on macOS.
#
# Works with Android 11+ "Wireless debugging" (dynamic port + pairing code) and with
# legacy "ADB over network" (fixed :5555).
#
# The script discovers the phone automatically, no configuration needed:
#   1. any already-connected device,
#   2. an optional DEVICE_IP env or IP[:PORT] argument,
#   3. mDNS (_adb-tls-connect) — for paired Android 11+ devices,
#   4. a subnet scan for open :5555 — for legacy-mode devices.
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
# then run `connect <ip:port>` with the address shown on the Wireless debugging screen.
# (After the first successful pairing+connect, `connect` finds the device by itself.)
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
#   DEVICE_IP   optional default phone address (none = auto-discover)
#   DEVICE_PORT legacy :5555 port (default 5555)
#
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

DEVICE_IP="${DEVICE_IP:-}"
DEVICE_PORT="${DEVICE_PORT:-5555}"

ok() { echo "[OK] $*"; }
fail() { echo "[FAIL] $*" >&2; exit 1; }
usage() { sed -n '2,31p' "$0" | sed 's/^# \{0,1\}//'; }

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

wireless_serial() {
  # First adb-over-Wi-Fi device currently connected ("ip:port  device"), empty otherwise.
  $ADB devices | awk 'NR>1 && $2=="device" {print $1}' | grep ':' | head -1 || true
}

any_connected_serial() {
  $ADB devices | awk 'NR>1 && $2=="device" {print $1}' | head -1 || true
}

# Scan mDNS-announced wireless-debugging services and print "ip:port" (or empty).
# Works when the device has already been paired with THIS Mac at least once.
mdns_discover() {
  $ADB mdns check >/dev/null 2>&1 || return 1
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

# Probe one host for an open legacy ADB port; prints "ip" on hit (used via xargs -P).
probe_5555() {
  nc -z -G 1 "$1" "$DEVICE_PORT" >/dev/null 2>&1 && echo "$1"
}

# Fall back: scan the /24 subnet for devices with port 5555 open (legacy ADB over network).
subnet_scan() {
  local prefix i
  prefix="$(local_lan_prefix)" || return 1
  echo "[..] Scanning ${prefix}.1–254 for open :${DEVICE_PORT} …" >&2
  for i in $(seq 1 254); do echo "${prefix}.${i}"; done \
    | xargs -n 1 -P 64 "$0" __probe 2>/dev/null | head -1
}

cmd_status() {
  echo "--- adb devices ---"
  $ADB devices -l
  echo "--- mDNS services ---"
  $ADB mdns services 2>/dev/null || echo "(mDNS not available/disabled)"
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
    ok "Paired with $target. Now run: $0 connect <ip:port-from-wireless-debugging-screen>"
  else
    fail "Pairing failed (code expired? — regenerate the pairing dialog and retry)"
  fi
}

cmd_connect() {
  local arg="${1:-}" target=""

  # 1. Explicit argument or DEVICE_IP env.
  if [ -n "$arg" ]; then
    target="$arg"
  elif [ -n "$DEVICE_IP" ]; then
    target="$DEVICE_IP"
  fi
  case "$target" in
    *:*) : ;;
    "")  : ;;
    *)   target="$target:$DEVICE_PORT" ;;
  esac

  # 2. Already connected over Wi-Fi? nothing to do.
  if [ -z "$target" ]; then
    local have
    have="$(wireless_serial)"
    if [ -n "$have" ]; then
      ok "Already connected: $have"
      cmd_status
      return 0
    fi
  fi

  # 3. mDNS discovery (paired Android 11+ devices announce ip:port here).
  #    Some OEM phones only advertise in bursts or after a few seconds, so retry patiently.
  if [ -z "$target" ]; then
    local attempt
    for attempt in 1 2 3 4 5 6; do
      target="$(mdns_discover || true)"
      [ -n "$target" ] && { echo "[..] mDNS found: $target (attempt $attempt/6)"; break; }
      sleep 2
    done
  fi

  # 4. Legacy devices (fixed :5555): try subnet scan, then the default-ip form.
  if [ -z "$target" ]; then
    target="$(subnet_scan || true)"
    [ -n "$target" ] && echo "[..] Subnet scan found: $target" && target="$target:$DEVICE_PORT"
  fi

  if [ -n "$target" ]; then
    if $ADB connect "$target" >/dev/null 2>&1 && \
       $ADB devices | awk 'NR>1 {print $1" "$2}' | grep -q "^$(echo "$target" | sed 's/[.]/\\./g') device"; then
      ok "Connected: $target"
      cmd_status
      return 0
    fi
    fail "Could not connect to $target (pairing expired? run: $0 pair)"
  fi

  fail "No device found.
  - If the phone shows 'IP address & Port' under Wireless debugging, run: $0 connect <that-ip:port>
  - If never paired on this Mac: $0 pair <pair-ip:port> <code>"
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
  if subnet_scan | grep -q .; then
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

# internal helper for subnet scan (never document)
case "${1:-}" in
  "__probe") probe_5555 "${2:-}" 2>/dev/null; exit 0 ;;
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
