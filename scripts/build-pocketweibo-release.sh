#!/usr/bin/env bash
# Build a signed release APK the same way as GitHub Actions (.github/workflows/build-apk.yml):
#   decode ANDROID_KEYSTORE_BASE64 → ci-release.keystore, write keystore.properties, then
#   ./gradlew assembleRelease
#
# Signing material is read from repo-root github-keystore-secrets.local.txt (gitignored) — use the
# same names/values as GitHub Actions secrets (see FIX.md and the workflow file).
#
# File format (one KEY=value per line, # comments and blank lines allowed):
#   ANDROID_KEYSTORE_BASE64=<base64 -w0 of your .keystore / .p12 / .jks>
#   KEYSTORE_PASSWORD=...
#   KEY_PASSWORD=...          # optional; defaults to KEYSTORE_PASSWORD
#   KEY_ALIAS=...             # optional; defaults to pocketweibo
#
# After build: renames the APK to pocketweibo-v<versionName>-<versionCode>-local-<timestamp>.apk
# in the repo root.
#
# Default: cp the APK to /storage/emulated/0/Download (override with --copy-dest). Skip with --no-copy.
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
COPY_DEST="/storage/emulated/0/Download"

SECRETS_FILE="${ROOT}/github-keystore-secrets.local.txt"
KS_OUT="${ROOT}/ci-release.keystore"
PROPS_OUT="${ROOT}/keystore.properties"

usage() {
  cat <<EOF
Build signed release APK using the same inputs as CI (secrets file → keystore + keystore.properties).

Usage: $(basename "$0") [options]

Options:
  --no-copy, -n       Do not copy the renamed APK after build (cp skipped).
  --copy-dest PATH    Local directory to copy the APK into (default: ${COPY_DEST}).
  --secrets-file F    Path to secrets file (default: ${SECRETS_FILE}).
  -h, --help          Show this help.

Requires: ${SECRETS_FILE} with ANDROID_KEYSTORE_BASE64 + KEYSTORE_PASSWORD; JDK 17; Android SDK.
Copy step uses plain cp (destination must exist or be creatable with mkdir -p).
See: .github/workflows/build-apk.yml, FIX.md
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
    --secrets-file)
      if [ $# -lt 2 ]; then echo "[FAIL] --secrets-file requires a path" >&2; exit 2; fi
      SECRETS_FILE="$2"
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

# Parse KEY=value secrets file (first '=' separates key from value; value may contain '=').
# Sets: ANDROID_KEYSTORE_BASE64, KEYSTORE_PASSWORD, KEY_PASSWORD, KEY_ALIAS (last wins if duplicated).
load_github_secrets() {
  local f="$1"
  ANDROID_KEYSTORE_BASE64=""
  KEYSTORE_PASSWORD=""
  KEY_PASSWORD=""
  KEY_ALIAS=""
  [ -f "$f" ] || fail "Secrets file not found: $f"
  while IFS= read -r line || [ -n "$line" ]; do
    line="${line#"${line%%[![:space:]]*}"}"
    line="${line%"${line##*[![:space:]]}"}"
    [[ -z "$line" || "$line" == \#* ]] && continue
    [[ "$line" != *=* ]] && continue
    local key val
    key="${line%%=*}"
    val="${line#*=}"
    key="${key#"${key%%[![:space:]]*}"}"
    key="${key%"${key##*[![:space:]]}"}"
    case "$key" in
      ANDROID_KEYSTORE_BASE64) ANDROID_KEYSTORE_BASE64="$val" ;;
      KEYSTORE_PASSWORD) KEYSTORE_PASSWORD="$val" ;;
      KEY_PASSWORD) KEY_PASSWORD="$val" ;;
      KEY_ALIAS) KEY_ALIAS="$val" ;;
    esac
  done < "$f"
}

# --- 1) Version from Gradle (same sed as CI) ---
VN=$(sed -n 's/.*versionName *= *"\([^"]*\)".*/\1/p' app/build.gradle.kts | head -1)
VC=$(sed -n 's/.*versionCode *= *\([0-9][0-9]*\).*/\1/p' app/build.gradle.kts | head -1)
if [ -z "${VN:-}" ] || [ -z "${VC:-}" ]; then
  fail "Could not parse versionName/versionCode from app/build.gradle.kts"
fi
ok "App version from Gradle: versionName=$VN versionCode=$VC"

# --- 2) Signing from github-keystore-secrets.local.txt (same as CI) ---
load_github_secrets "$SECRETS_FILE"
if [ -z "$ANDROID_KEYSTORE_BASE64" ]; then
  fail "Missing ANDROID_KEYSTORE_BASE64 in $SECRETS_FILE (same as GitHub secret; see FIX.md)"
fi
if [ -z "$KEYSTORE_PASSWORD" ]; then
  fail "Missing KEYSTORE_PASSWORD in $SECRETS_FILE"
fi
KEY_PASS="${KEY_PASSWORD:-$KEYSTORE_PASSWORD}"
ALIAS="${KEY_ALIAS:-pocketweibo}"

umask 077
printf '%s' "$ANDROID_KEYSTORE_BASE64" | base64 -d > "$KS_OUT"
chmod 600 "$KS_OUT"
if [ ! -s "$KS_OUT" ]; then
  rm -f "$KS_OUT"
  fail "Decoded keystore is empty — check ANDROID_KEYSTORE_BASE64 (e.g. base64 -w0 my.keystore)"
fi
{
  echo "storeFile=ci-release.keystore"
  echo "storePassword=${KEYSTORE_PASSWORD}"
  echo "keyAlias=${ALIAS}"
  echo "keyPassword=${KEY_PASS}"
} > "$PROPS_OUT"
chmod 600 "$PROPS_OUT"
ok "Wrote $KS_OUT and $PROPS_OUT from $SECRETS_FILE (same layout as CI)"

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

# --- 6) Optional local copy (cp only; no adb) ---
if [ "$DO_COPY" = true ]; then
  mkdir -p "$COPY_DEST" || fail "mkdir -p failed: $COPY_DEST"
  COPY_PATH="${COPY_DEST%/}/$DEST_NAME"
  echo "[..] cp \"$DEST\" \"$COPY_PATH\""
  if cp "$DEST" "$COPY_PATH"; then
    ok "Copied with cp to: $COPY_PATH"
  else
    fail "cp failed. Check permissions and that $COPY_DEST is a writable directory on this machine."
  fi
else
  ok "Skipped copy (--no-copy)"
fi

echo ""
echo "========== SUCCESS =========="
echo "  APK:     $DEST"
echo "  version: $VN (code $VC)"
if [ "$DO_COPY" = true ]; then
  echo "  copy:    ${COPY_DEST%/}/$DEST_NAME"
else
  echo "  copy:    (skipped)"
fi
echo "=============================="
