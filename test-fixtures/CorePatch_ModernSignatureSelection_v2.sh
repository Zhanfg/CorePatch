#!/system/bin/sh
# CorePatch Android 17 Modern Signature Selection Test v2
# No arguments. Requires CorePatch 1.3.1 / versionCode 11+ debug build.

BASE_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" 2>/dev/null && pwd)"
STAGE="/data/local/tmp/corepatch-modern-selection-$$"
OUT="/sdcard/Download/CorePatch_ModernSignatureSelection_v2_$(date +%Y%m%d_%H%M%S).txt"

V32_VALID="$BASE_DIR/v32-valid.apk"
V32_TAMPERED="$BASE_DIR/v32-tampered.apk"
V41_VALID="$BASE_DIR/corepatch-v41.apk"
V41_VALID_IDSIG="$BASE_DIR/corepatch-v41.apk.idsig"
V41_TAMPERED="$BASE_DIR/corepatch-v41-tampered.apk"
V41_TAMPERED_IDSIG="$BASE_DIR/corepatch-v41-tampered.apk.idsig"

V32_PKG="android.appsecurity.cts.tinyapp"
V41_PKG="dev.axymorrsen.corepatch.test"

PASS=0
FAIL=0
INFO=0
SKIP=0
V32_STATE="UNPROVEN"
V41_STATE="UNPROVEN"
V41_TAMPER_STATE="UNTESTED"

say(){ echo "$*" | tee -a "$OUT"; }
pass(){ PASS=$((PASS+1)); say "[PASS] $*"; }
fail(){ FAIL=$((FAIL+1)); say "[FAIL] $*"; }
info(){ INFO=$((INFO+1)); say "[INFO] $*"; }
skip(){ SKIP=$((SKIP+1)); say "[SKIP] $*"; }
section(){
  say ""
  say "============================================================"
  say "$1"
  say "============================================================"
}

if [ "$(id -u 2>/dev/null)" != "0" ] && command -v su >/dev/null 2>&1; then
  exec su -c "sh '$0'"
fi

cleanup(){
  pm uninstall "$V32_PKG" >/dev/null 2>&1
  pm uninstall "$V41_PKG" >/dev/null 2>&1
  rm -rf "$STAGE" >/dev/null 2>&1
}
trap cleanup EXIT INT TERM

modern_logs_since(){
  start="$1"
  logcat -d -b all -v epoch 2>/dev/null |
    awk -v s="$start" '($1 + 0) >= s' |
    grep '\[ModernSignature\]' || true
}

install_normal(){
  apk="$1"
  pm install -r "$apk" 2>&1
}

install_incremental(){
  apk="$1"
  pm install-incremental "$apk" 2>&1
}

: > "$OUT"
say "CorePatch Android 17 Modern Signature Selection Test v2"
say "Generated: $(date)"
say "Report: $OUT"

section "[0] Preconditions"

for f in   "$V32_VALID"   "$V32_TAMPERED"   "$V41_VALID"   "$V41_VALID_IDSIG"   "$V41_TAMPERED"   "$V41_TAMPERED_IDSIG"
do
  [ -f "$f" ] || fail "Missing fixture: $f"
done
[ "$FAIL" -eq 0 ] || exit 1

CP="$(dumpsys package org.lsposed.corepatch 2>/dev/null)"
CP_CODE="$(printf '%s\n' "$CP" | sed -n 's/.*versionCode=\([0-9][0-9]*\).*/\1/p' | head -n1)"
CP_NAME="$(printf '%s\n' "$CP" | sed -n 's/.*versionName=\([^ ]*\).*/\1/p' | head -n1)"
say "corepatch_versionCode=$CP_CODE"
say "corepatch_versionName=$CP_NAME"

if [ -n "$CP_CODE" ] && [ "$CP_CODE" -ge 11 ]; then
  pass "CorePatch 1.3.1 diagnostic generation detected."
else
  fail "CorePatch versionCode 11+ is required."
  exit 2
fi

INC_FEATURE="$(pm has-feature android.software.incremental_delivery 2>/dev/null | tail -n1)"
say "incremental_delivery=$INC_FEATURE"

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

SYS_BEFORE="$(pidof system_server 2>/dev/null | awk '{print $1}')"
say "system_server_before=$SYS_BEFORE"

section "[1] Exact v3.2 selector"

pm uninstall "$V32_PKG" >/dev/null 2>&1
T1="$(date +%s)"
R1="$(install_normal "$STAGE/v32-valid.apk")"
say "$R1"
sleep 1
L1="$(modern_logs_since "$T1")"
printf '%s\n' "$L1" | tail -n 80 | tee -a "$OUT"

if echo "$R1" | grep -q "Success"; then
  pass "v3.2 fixture package installed."
else
  fail "v3.2 fixture package installation failed."
fi

if printf '%s\n' "$L1" | grep -q 'v3-selected block=0x70e1c89f'; then
  V32_STATE="SELECTED"
  pass "Android 17 actually selected APK Signature Scheme v3.2 block 0x70e1c89f."
elif printf '%s\n' "$L1" | grep -q 'pqcHybrid=false'; then
  V32_STATE="PLATFORM_FLAG_OFF"
  info "Platform apkPqcHybridSigning flag is false; Android intentionally falls back below v3.2."
elif printf '%s\n' "$L1" | grep -q 'v3-selected'; then
  V32_STATE="OTHER_BLOCK"
  fail "PQC selector ran but v3.2 block was not selected."
else
  V32_STATE="NO_DIAGNOSTIC"
  fail "No low-level v3 selection diagnostic was captured."
fi

section "[2] Tampered v3.2 content"

T2="$(date +%s)"
R2="$(install_normal "$STAGE/v32-tampered.apk")"
say "$R2"
sleep 1
L2="$(modern_logs_since "$T2")"
printf '%s\n' "$L2" | tail -n 80 | tee -a "$OUT"

if echo "$R2" | grep -q "Success"; then
  if [ "$V32_STATE" = "SELECTED" ] &&
     printf '%s\n' "$L2" | grep -q 'v3-selected block=0x70e1c89f'; then
    pass "Tampered APK succeeded while the real v3.2 block remained selected."
  elif [ "$V32_STATE" = "PLATFORM_FLAG_OFF" ]; then
    info "Tampered APK succeeded, but this device selected an older v3 block because PQC hybrid is disabled."
  else
    info "Tampered APK succeeded; scheme-specific attribution remains unresolved."
  fi
else
  fail "Tampered v3.2 fixture was rejected."
fi

section "[3] Genuine v4.1 via Incremental DataLoader"

pm uninstall "$V41_PKG" >/dev/null 2>&1

if ! echo "$INC_FEATURE" | grep -qi 'true'; then
  V41_STATE="NO_INCREMENTAL_FEATURE"
  skip "android.software.incremental_delivery is unavailable; cannot prove the v4.1 runtime path."
else
  T3="$(date +%s)"
  R3="$(install_incremental "$STAGE/corepatch-v41.apk")"
  say "$R3"
  sleep 2
  L3="$(modern_logs_since "$T3")"
  printf '%s\n' "$L3" | tail -n 120 | tee -a "$OUT"

  if ! echo "$R3" | grep -q "Success"; then
    V41_STATE="INCREMENTAL_INSTALL_FAILED"
    fail "Valid v4.1 incremental installation failed."
  elif printf '%s\n' "$L3" | grep -q 'v4.1 accepted' &&
       printf '%s\n' "$L3" | grep -q 'backing=0x1b93ad61' &&
       printf '%s\n' "$L3" | grep -q 'selected=0x1b93ad61'; then
    V41_STATE="SELECTED"
    pass "Android 17 actually selected the v4.1 SigningInfoBlock for backing v3.1."
  else
    V41_STATE="FELL_BACK_BELOW_V4"
    fail "Incremental install succeeded but no scheme=4/v4.1 selection evidence appeared."
  fi
fi

section "[4] Tampered APK + stale v4.1 idsig boundary"

pm uninstall "$V41_PKG" >/dev/null 2>&1

if [ "$V41_STATE" = "NO_INCREMENTAL_FEATURE" ]; then
  V41_TAMPER_STATE="SKIPPED"
  skip "No incremental delivery support."
else
  T4="$(date +%s)"
  R4="$(install_incremental "$STAGE/corepatch-v41-tampered.apk")"
  RC4=$?
  say "$R4"
  sleep 2
  L4="$(modern_logs_since "$T4")"
  printf '%s\n' "$L4" | tail -n 140 | tee -a "$OUT"

  if [ "$RC4" -eq 0 ] && echo "$R4" | grep -q "Success"; then
    if printf '%s\n' "$L4" | grep -q 'v4/v4.1 error recovered via'; then
      V41_TAMPER_STATE="PACKAGE_FALLBACK"
      pass "PackageManager-level v4 failure reached CorePatch and recovered through the backing signer chain."
    elif printf '%s\n' "$L4" | grep -q 'v4.1 accepted'; then
      V41_TAMPER_STATE="V4_ACCEPTED"
      info "Tampered incremental sample still reached an accepted v4.1 path; inspect IncFS behavior."
    else
      V41_TAMPER_STATE="SUCCESS_WITHOUT_V4_EVIDENCE"
      fail "Tampered incremental install succeeded without v4/fallback evidence."
    fi
  else
    V41_TAMPER_STATE="EXPECTED_KERNEL_BOUNDARY"
    info "Tampered incremental install was rejected before PackageManager fallback; this is consistent with IncFS/Merkle-tree enforcement."
    say "--- incremental failure context ---"
    logcat -d -b all 2>/dev/null |
      grep -Ei 'incfs|incremental|verity|merkle|dataloader|hash|v4 signature' |
      tail -n 100 | tee -a "$OUT"
  fi
fi

section "[5] system_server stability"

SYS_AFTER="$(pidof system_server 2>/dev/null | awk '{print $1}')"
say "system_server_after=$SYS_AFTER"
if [ -n "$SYS_BEFORE" ] && [ "$SYS_BEFORE" = "$SYS_AFTER" ]; then
  pass "system_server remained stable."
else
  fail "system_server PID changed."
fi

section "[6] Cleanup"
cleanup
pass "Fixture packages and staging files removed."

section "[7] Result"

say "V32_STATE=$V32_STATE"
say "V41_STATE=$V41_STATE"
say "V41_TAMPER_STATE=$V41_TAMPER_STATE"
say "PASS=$PASS INFO=$INFO SKIP=$SKIP FAIL=$FAIL"

if [ "$FAIL" -gt 0 ]; then
  say "VERDICT=MODERN_SIGNATURE_SELECTION_GAPS_FOUND"
elif [ "$V32_STATE" = "SELECTED" ] && [ "$V41_STATE" = "SELECTED" ]; then
  say "VERDICT=V32_AND_V41_RUNTIME_SELECTION_PROVEN"
elif [ "$V32_STATE" = "PLATFORM_FLAG_OFF" ] && [ "$V41_STATE" = "SELECTED" ]; then
  say "VERDICT=V41_PROVEN_V32_DISABLED_BY_PLATFORM_FLAG"
else
  say "VERDICT=PARTIAL_OR_PLATFORM_LIMITED"
fi

say "Saved report: $OUT"
