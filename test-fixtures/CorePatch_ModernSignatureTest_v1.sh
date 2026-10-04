#!/system/bin/sh
# CorePatch Android 17 Modern Signature Functional Test v1
# No arguments. Uses only isolated/pinned test fixtures and cleans up afterwards.

BASE_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" 2>/dev/null && pwd)"
STAGE="/data/local/tmp/corepatch-modern-signature-$$"
OUT="/sdcard/Download/CorePatch_ModernSignatureTest_$(date +%Y%m%d_%H%M%S).txt"

V32_VALID="$BASE_DIR/v32-valid.apk"
V32_TAMPERED="$BASE_DIR/v32-tampered.apk"
V41_VALID="$BASE_DIR/corepatch-v41.apk"
V41_VALID_IDSIG="$BASE_DIR/corepatch-v41.apk.idsig"
V41_TAMPERED="$BASE_DIR/corepatch-v41-tampered.apk"
V41_TAMPERED_IDSIG="$BASE_DIR/corepatch-v41-tampered.apk.idsig"
V41_PKG="dev.axymorrsen.corepatch.test"
V32_PKG=""

PASS=0
FAIL=0
INFO=0

say(){ echo "$*" | tee -a "$OUT"; }
pass(){ PASS=$((PASS+1)); say "[PASS] $*"; }
fail(){ FAIL=$((FAIL+1)); say "[FAIL] $*"; }
info(){ INFO=$((INFO+1)); say "[INFO] $*"; }
section(){
  say ""
  say "============================================================"
  say "$1"
  say "============================================================"
}

if [ "$(id -u 2>/dev/null)" != "0" ] && command -v su >/dev/null 2>&1; then
  exec su -c "sh '$0'"
fi

snapshot_packages(){
  pm list packages 2>/dev/null | sed 's/^package://' | sort
}

find_new_package(){
  before="$1"
  after="$2"
  while IFS= read -r pkg; do
    [ -n "$pkg" ] || continue
    if ! grep -Fqx "$pkg" "$before" 2>/dev/null; then
      echo "$pkg"
      return 0
    fi
  done < "$after"
  return 1
}

cleanup(){
  [ -n "$V32_PKG" ] && pm uninstall "$V32_PKG" >/dev/null 2>&1
  pm uninstall "$V41_PKG" >/dev/null 2>&1
  rm -rf "$STAGE" >/dev/null 2>&1
}
trap cleanup EXIT INT TERM

install_one(){
  label="$1"
  apk="$2"
  say ""
  say "[$label]"
  say "apk=$apk"
  res="$(pm install -r "$apk" 2>&1)"
  rc=$?
  say "$res"
  [ "$rc" -eq 0 ] && echo "$res" | grep -q "Success"
}

: > "$OUT"
say "CorePatch Android 17 Modern Signature Functional Test v1"
say "Generated: $(date)"
say "Mode: isolated v3.2 / v4.1 install matrix"
say "Report: $OUT"

section "[0] Preconditions"

for f in \
  "$V32_VALID" \
  "$V32_TAMPERED" \
  "$V41_VALID" \
  "$V41_VALID_IDSIG" \
  "$V41_TAMPERED" \
  "$V41_TAMPERED_IDSIG"
do
  if [ ! -f "$f" ]; then
    fail "Missing fixture: $f"
  fi
done
[ "$FAIL" -eq 0 ] || exit 1

CP="$(dumpsys package org.lsposed.corepatch 2>/dev/null)"
say "$(printf '%s\n' "$CP" | grep -m1 'versionCode=' | sed 's/^[[:space:]]*//')"
say "$(printf '%s\n' "$CP" | grep -m1 'versionName=' | sed 's/^[[:space:]]*//')"

CP_CODE="$(printf '%s\n' "$CP" | sed -n 's/.*versionCode=\([0-9][0-9]*\).*/\1/p' | head -n1)"
if [ -n "$CP_CODE" ] && [ "$CP_CODE" -ge 10 ]; then
  pass "CorePatch generation is new enough for the 1.3.0 modern-signature test."
else
  fail "CorePatch versionCode 10+ is required; installed=\${CP_CODE:-unknown}."
  exit 2
fi

say "Required CorePatch option: bypass_verification=true"
say "The test does not change CorePatch preferences."

rm -rf "$STAGE"
mkdir -p "$STAGE" || exit 2
chmod 0755 "$STAGE"

cp "$V32_VALID" "$STAGE/v32-valid.apk" || exit 2
cp "$V32_TAMPERED" "$STAGE/v32-tampered.apk" || exit 2
cp "$V41_VALID" "$STAGE/corepatch-v41.apk" || exit 2
cp "$V41_VALID_IDSIG" "$STAGE/corepatch-v41.apk.idsig" || exit 2
cp "$V41_TAMPERED" "$STAGE/corepatch-v41-tampered.apk" || exit 2
cp "$V41_TAMPERED_IDSIG" "$STAGE/corepatch-v41-tampered.apk.idsig" || exit 2
chmod 0644 "$STAGE"/*
restorecon -RF "$STAGE" >/dev/null 2>&1 || true

say ""
say "--- staging ---"
ls -lZ "$STAGE" 2>/dev/null | tee -a "$OUT"

SYS_BEFORE="$(pidof system_server 2>/dev/null | awk '{print $1}')"
say "system_server_before=$SYS_BEFORE"

snapshot_packages > "$STAGE/packages-before.txt"

section "[1] Genuine APK Signature Scheme v3.2"

if install_one "v3.2 hybrid valid" "$STAGE/v32-valid.apk"; then
  pass "Valid v3.2 hybrid fixture installed."
else
  fail "Valid v3.2 hybrid fixture was rejected."
fi

snapshot_packages > "$STAGE/packages-after-v32.txt"
V32_PKG="$(find_new_package "$STAGE/packages-before.txt" "$STAGE/packages-after-v32.txt" | head -n1)"
if [ -n "$V32_PKG" ]; then
  say "v32_package=$V32_PKG"
else
  info "Could not uniquely resolve the v3.2 fixture package name from the package-list delta."
fi

section "[2] Tampered v3.2 with original v3.2 signing block"

if install_one "v3.2 hybrid tampered" "$STAGE/v32-tampered.apk"; then
  pass "Tampered v3.2 fixture installed through the modern certificate-only path."
else
  fail "Tampered v3.2 fixture was rejected."
fi

section "[3] Genuine APK Signature Scheme v4.1"

pm uninstall "$V41_PKG" >/dev/null 2>&1

if install_one "v4.1 valid + matching idsig" "$STAGE/corepatch-v41.apk"; then
  pass "Valid v4.1 fixture installed with adjacent .idsig."
else
  fail "Valid v4.1 fixture was rejected."
fi

section "[4] Tampered APK with stale original v4.1 idsig"

if install_one "v4.1 tampered + stale idsig" "$STAGE/corepatch-v41-tampered.apk"; then
  pass "Tampered v4.1 fixture recovered instead of being blocked by the stale sidecar."
else
  fail "Tampered v4.1 fixture was rejected; inspect the v4-to-v3 fallback."
fi

section "[5] ModernSignature runtime evidence"

LOGS="$(logcat -d -b all 2>/dev/null | grep -E '\[ModernSignature\]' | tail -n 160)"
if [ -n "$LOGS" ]; then
  printf '%s\n' "$LOGS" | tee -a "$OUT"
else
  info "No ModernSignature debug lines retained. Behavior results above remain authoritative."
fi

if printf '%s\n' "$LOGS" | grep -q 'v3.2 hybrid accepted'; then
  pass "Runtime log confirms v3.2 minorVersion=2 path."
else
  info "No retained v3.2 diagnostic line."
fi

if printf '%s\n' "$LOGS" | grep -q 'v4.1 accepted' &&
   printf '%s\n' "$LOGS" | grep -q 'backing=0x1b93ad61' &&
   printf '%s\n' "$LOGS" | grep -q 'selected=0x1b93ad61'; then
  pass "Runtime log confirms v4.1 selected the v3.1 SigningInfoBlock."
else
  info "No complete retained v4.1 block-selection diagnostic line."
fi

if printf '%s\n' "$LOGS" | grep -q 'v4/v4.1 error recovered via v3.1 certificate-only path'; then
  pass "Runtime log confirms stale v4.1 sidecar fallback to v3.1."
else
  info "No retained stale-v4.1 fallback diagnostic line."
fi

section "[6] system_server stability"

SYS_AFTER="$(pidof system_server 2>/dev/null | awk '{print $1}')"
say "system_server_after=$SYS_AFTER"
if [ -n "$SYS_BEFORE" ] && [ "$SYS_BEFORE" = "$SYS_AFTER" ]; then
  pass "system_server remained stable."
else
  fail "system_server PID changed during modern signature tests."
fi

section "[7] Cleanup"
cleanup
pass "Isolated fixture cleanup completed."

section "[8] Result"
say "PASS=$PASS INFO=$INFO FAIL=$FAIL"
if [ "$FAIL" -eq 0 ]; then
  say "VERDICT=MODERN_SIGNATURE_CORE_PATHS_PASS"
else
  say "VERDICT=MODERN_SIGNATURE_GAPS_FOUND"
fi
say ""
say "Saved report: $OUT"
