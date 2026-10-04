#!/system/bin/sh
# CorePatch OShin-port smoke test v1
# Read-only / no arguments.

OUT="/sdcard/Download/CorePatch_OShinPortSmoke_$(date +%Y%m%d_%H%M%S).txt"
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

: > "$OUT"
say "CorePatch OShin-port smoke test v1"
say "Generated: $(date)"
say "Mode: read-only"
say "Report: $OUT"

section "[1] CorePatch baseline"
CP="$(dumpsys package org.lsposed.corepatch 2>/dev/null)"
say "$(printf '%s\n' "$CP" | grep -m1 'versionCode=' | sed 's/^[[:space:]]*//')"
say "$(printf '%s\n' "$CP" | grep -m1 'versionName=' | sed 's/^[[:space:]]*//')"
PID_BEFORE="$(pidof system_server 2>/dev/null | awk '{print $1}')"
say "system_server_before=$PID_BEFORE"

section "[2] pm pms command"
HELP="$(pm pms help 2>&1)"
say "$HELP"
if echo "$HELP" | grep -q 'CorePatch PMS diagnostics'; then
  pass "pm pms command is registered."
else
  fail "pm pms command was not intercepted by CorePatch."
fi

section "[3] Package diagnostic"
PKG="$(pm pms package org.lsposed.corepatch 2>&1)"
say "$PKG"
if echo "$PKG" | grep -q '^package=org.lsposed.corepatch$'; then
  pass "Package diagnostic resolved CorePatch."
else
  fail "Package diagnostic did not resolve CorePatch."
fi

if echo "$PKG" | grep -q '^signatureScheme='; then
  pass "Package diagnostic exposes the signing scheme."
else
  info "Signing scheme was not available through SigningInfo reflection."
fi

UID="$(echo "$PKG" | sed -n 's/^uid=\([0-9][0-9]*\)$/\1/p' | head -n1)"
if [ -n "$UID" ]; then
  UID_OUT="$(pm pms uid "$UID" 2>&1)"
  say ""
  say "$UID_OUT"
  if echo "$UID_OUT" | grep -q '^package=org.lsposed.corepatch$'; then
    pass "UID diagnostic maps CorePatch back to UID $UID."
  else
    fail "UID diagnostic did not return CorePatch for UID $UID."
  fi
else
  fail "Could not parse CorePatch UID."
fi

section "[4] New hook-resolution evidence"
LOGS="$(logcat -d -b all 2>/dev/null)"
DV="$(printf '%s\n' "$LOGS" | grep '\[DomainVerificationHook\]' | tail -n 20)"
OV="$(printf '%s\n' "$LOGS" | grep '\[InstallPackageHelperHook\]' | tail -n 20)"

say "--- DomainVerificationHook ---"
say "$DV"
if echo "$DV" | grep -q 'installed [1-9][0-9]* domain broadcast gate hooks'; then
  pass "Domain Verification broadcast gate resolved."
else
  info "No retained DomainVerificationHook install-count line."
fi

say ""
say "--- InstallPackageHelperHook ---"
say "$OV"
if echo "$OV" | grep -q 'init: InstallPackageHelperHook done'; then
  pass "InstallPackageHelper hook initialized (Overlay Validation target lives here)."
else
  info "No retained InstallPackageHelper initialization line."
fi

section "[5] Stability"
PID_AFTER="$(pidof system_server 2>/dev/null | awk '{print $1}')"
say "system_server_after=$PID_AFTER"
if [ -n "$PID_BEFORE" ] && [ "$PID_BEFORE" = "$PID_AFTER" ]; then
  pass "system_server remained stable."
else
  fail "system_server PID changed during the read-only smoke test."
fi

section "[6] Result"
say "PASS=$PASS INFO=$INFO FAIL=$FAIL"
if [ "$FAIL" -eq 0 ]; then
  say "VERDICT=OSHIN_PORT_SMOKE_PASS"
else
  say "VERDICT=OSHIN_PORT_SMOKE_GAPS_FOUND"
fi
say "Saved report: $OUT"
