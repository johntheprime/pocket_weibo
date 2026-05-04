#!/usr/bin/env bash
# Build a signed release APK the same way as GitHub Actions (.github/workflows/build-apk.yml):
#   ./gradlew assembleRelease
# with signing from repo-root keystore.properties (see keystore.properties.example).
#
# CI writes keystore.properties from secrets ANDROID_KEYSTORE_BASE64, KEYSTORE_PASSWORD,
# KEY_PASSWORD, KEY_ALIAS — locally you keep a real keystore file + keystore.properties (gitignored).
#
# After build: renames the APK to pocketweibo-v<versionName>-<versionCode>-local-<timestamp>.apk
# in the repo root (same version fields as parsed from app/build.gradle.kts).
#
# Default: adb push to /storage/emulated/0/Downloads (override with --copy-dest; many devices use
# /storage/emulated/0/Download — use --copy-dest if push fails). Skip copy with --no-copy / -n.
#
# Usage:
#   ./scripts/build-pocketweibo-release.sh
#   ./scripts/build-pocketweibo-release.sh --no-copy
#   ./scripts/build-pocketweibo-release.sh --copy-dest /storage/emulated/0/Download
#
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

DO_COPY=true
COPY_DEST="/storage/emulated/0/Downloads"

usage() {
  cat <<EOF
Build signed release APK (same as CI: ./gradlew assembleRelease + keystore.properties).

Usage: $(basename "$0") [options]

Options:
  --no-copy, -n     Do not adb push after rename (build + rename only).
  --copy-dest PATH  Device directory for adb push (default: ${COPY_DEST}).
  -h, --help        Show this help.

Requires: repo-root keystore.properties + keystore file; JDK 17; Android SDK.
See: .github/workflows/build-apk.yml, keystore.properties.example
EOF
}

while [ $# -gt 0 ]; do
  case "$1" in
    --no-copy|-n)
      DO_COPY=false
      shift
      ;;
    --copy-dest)
      if [ $# -lt 2 ]; then echo "[FAIL] --copy-dest requires a path" >&2; exit 2; fi
      COPY_DEST="$2"
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "[FAIL] Unknown argument: $1 (try --help)" >&2
      exit 2
      ;;
  esac
done

ok() { echo "[OK] $*"; }
fail() { echo "[FAIL] $*" >&2; exit 1; }

# --- 1) Version from Gradle (same sed as CI) ---
VN=$(sed -n 's/.*versionName *= *"\([^"]*\)".*/\1/p' app/build.gradle.kts | head -1)
VC=$(sed -n 's/.*versionCode *= *\([0-9][0-9]*\).*/\1/p' app/build.gradle.kts | head -1)
if [ -z "${VN:-}" ] || [ -z "${VC:-}" ]; then
  fail "Could not parse versionName/versionCode from app/build.gradle.kts"
fi
ok "App version from Gradle: versionName=$VN versionCode=$VC"

# --- 2) Keystore (local keystore.properties + store file, same contract as app/build.gradle.kts) ---
PROPS="$ROOT/keystore.properties"
if [ ! -f "$PROPS" ]; then
  fail "Missing $PROPS. Copy keystore.properties.example to keystore.properties and set storeFile, passwords, keyAlias. See .github/workflows/build-apk.yml header for CI secret mapping."
fi

STORE_REL=$(sed -n 's/^storeFile[[:space:]]*=[[:space:]]*//p' "$PROPS" | head -1 | tr -d '\r')
STORE_REL="${STORE_REL#"${STORE_REL%%[![:space:]]*}"}"
STORE_REL="${STORE_REL%"${STORE_REL##*[![:space:]]}"}"
if [ -z "$STORE_REL" ]; then
  fail "keystore.properties has no storeFile=..."
fi
if [[ "$STORE_REL" == /* ]]; then
  STORE_ABS="$STORE_REL"
else
  STORE_ABS="$ROOT/$STORE_REL"
fi
if [ ! -f "$STORE_ABS" ]; then
  fail "Keystore file not found: $STORE_ABS (from storeFile=$STORE_REL in keystore.properties)"
fi
ok "Signing: keystore.properties present, storeFile resolves to existing file"

# --- 3) Gradle assembleRelease (same as CI) ---
if [ ! -x ./gradlew ]; then
  fail "./gradlew not found or not executable in $ROOT"
fi

echo "[..] Running ./gradlew assembleRelease --no-daemon --stacktrace"
if ! ./gradlew assembleRelease --no-daemon --stacktrace; then
  fail "Gradle assembleRelease failed"
fi
ok "assembleRelease finished"

# --- 4) Locate release APK ---
REL_DIR="$ROOT/app/build/outputs/apk/release"
if [ ! -d "$REL_DIR" ]; then
  fail "Expected output directory missing: $REL_DIR"
fi
mapfile -t APK_CANDIDATES < <(find "$REL_DIR" -maxdepth 1 -name '*.apk' -type f | sort)
if [ "${#APK_CANDIDATES[@]}" -eq 0 ]; then
  fail "No APK found under $REL_DIR"
fi
if [ "${#APK_CANDIDATES[@]}" -gt 1 ]; then
  echo "[WARN] Multiple APKs in release dir; using first: ${APK_CANDIDATES[0]}"
fi
SRC="${APK_CANDIDATES[0]}"
ok "Built APK: $SRC"

# --- 5) Rename: pocketweibo first + version + build (versionCode) + local timestamp ---
TS=$(date +%Y%m%d-%H%M%S)
DEST_NAME="pocketweibo-v${VN}-${VC}-local-${TS}.apk"
DEST="$ROOT/$DEST_NAME"
mv "$SRC" "$DEST"
ok "Renamed to $DEST"

# --- 6) Optional adb copy ---
if [ "$DO_COPY" = true ]; then
  if ! command -v adb >/dev/null 2>&1; then
    fail "adb not found in PATH; install platform-tools or use --no-copy"
  fi
  if ! adb devices 2>/dev/null | grep -qE '[[:space:]]device$'; then
    fail "No device in 'adb devices' with state 'device'. Connect a phone/emulator or use --no-copy"
  fi
  REMOTE_PATH="${COPY_DEST%/}/$DEST_NAME"
  echo "[..] adb push \"$DEST\" \"$REMOTE_PATH\""
  if adb push "$DEST" "$REMOTE_PATH"; then
    ok "Copied to device: $REMOTE_PATH"
  else
    fail "adb push failed. Try --copy-dest /storage/emulated/0/Download (singular) if your device has no Downloads folder."
  fi
else
  ok "Skipped device copy (--no-copy)"
fi

echo ""
echo "========== SUCCESS =========="
echo "  APK:     $DEST"
echo "  version: $VN (code $VC)"
if [ "$DO_COPY" = true ]; then
  echo "  device:  ${COPY_DEST%/}/$DEST_NAME"
else
  echo "  device:  (not copied)"
fi
echo "=============================="
