#!/system/bin/sh
# CorePatch Android 17 Functional Test Suite v3
# No arguments. Isolated packages only. Automatically cleans up.
# Test packages:
#   dev.axymorrsen.corepatch.test
#   dev.axymorrsen.corepatch.shared.a
#   dev.axymorrsen.corepatch.shared.b

BASE_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" 2>/dev/null && pwd)"
APK_DIR="$BASE_DIR/apks"
STAGE="/data/local/tmp/corepatch-functional-test-$$"
OUT="/sdcard/Download/CorePatch_FunctionalTest_v3_$(date +%Y%m%d_%H%M%S).txt"
PKG="dev.axymorrsen.corepatch.test"
SHARED_A="dev.axymorrsen.corepatch.shared.a"
SHARED_B="dev.axymorrsen.corepatch.shared.b"
PASS=0
FAIL=0
INFO=0

say(){ echo "$*" | tee -a "$OUT"; }
pass(){ PASS=$((PASS+1)); say "[PASS] $*"; }
fail(){ FAIL=$((FAIL+1)); say "[FAIL] $*"; }
info(){ INFO=$((INFO+1)); say "[INFO] $*"; }
section(){ say ""; say "============================================================"; say "$1"; say "============================================================"; }

if [ "$(id -u 2>/dev/null)" != "0" ] && command -v su >/dev/null 2>&1; then
  exec su -c "sh '$0'"
fi

cleanup(){
  pm uninstall "$PKG" >/dev/null 2>&1
  pm uninstall "$SHARED_A" >/dev/null 2>&1
  pm uninstall "$SHARED_B" >/dev/null 2>&1
  rm -rf "$STAGE" >/dev/null 2>&1
}
trap cleanup EXIT INT TERM

stage_fixtures(){
  rm -rf "$STAGE" >/dev/null 2>&1
  mkdir -p "$STAGE" || return 1
  chmod 0755 "$STAGE" 2>/dev/null

  for F in \
    base-gen1-keyA.apk \
    base-gen2-keyA.apk \
    diff-gen3-keyB.apk \
    tampered-gen4-keyB.apk \
    shared-a-keyA.apk \
    shared-b-keyB.apk
  do
    cp "$APK_DIR/$F" "$STAGE/$F" || return 1
    chmod 0644 "$STAGE/$F" 2>/dev/null
  done

  if command -v restorecon >/dev/null 2>&1; then
    restorecon -RF "$STAGE" >/dev/null 2>&1 || true
  fi

  say "staging_dir=$STAGE"
  ls -lZ "$STAGE" 2>/dev/null | tee -a "$OUT"
  return 0
}

install_apk(){
  LABEL="$1"
  APK="$2"
  EXPECTED="$3"
  say ""
  say "[$LABEL]"
  say "apk=$APK"
  RES="$(pm install -r "$APK" 2>&1)"
  RC=$?
  say "$RES"
  if [ "$RC" -eq 0 ] && echo "$RES" | grep -q "Success"; then
    if [ -n "$EXPECTED" ]; then
      GOT="$(dumpsys package "$PKG" 2>/dev/null | sed -n 's/.*versionCode=\([0-9][0-9]*\).*/\1/p' | head -n1)"
      say "installed_version=$GOT expected=$EXPECTED"
      [ "$GOT" = "$EXPECTED" ] || return 2
    fi
    return 0
  fi
  return 1
}

: > "$OUT"
say "CorePatch Android 17 Functional Test Suite v3"
say "Generated: $(date)"
say "Mode: isolated install matrix / no arguments"
say "Report: $OUT"

section "[0] Preconditions"
say "CorePatch:"
dumpsys package org.lsposed.corepatch 2>/dev/null | grep -m1 -E 'versionCode=|versionName=' | tee -a "$OUT"
say ""
say "Before running this suite, enable these Core Patch switches:"
say "  - Bypass downgrade"
say "  - Bypass verification"
say "  - Bypass digest"
say "  - Bypass shared user verification"
say "  - Bypass Android developer verification"
say ""
say "LuckyTool-CorePatch or other competing PackageManager signature hooks should remain disabled."

for F in   base-gen1-keyA.apk   base-gen2-keyA.apk   diff-gen3-keyB.apk   tampered-gen4-keyB.apk   shared-a-keyA.apk   shared-b-keyB.apk
do
  if [ ! -f "$APK_DIR/$F" ]; then
    fail "Missing fixture: $APK_DIR/$F"
  fi
done
[ "$FAIL" -eq 0 ] || exit 1

section "[0.5] SELinux-safe staging"
if stage_fixtures; then
  pass "All fixtures staged under /data/local/tmp for system_server access."
else
  fail "Could not stage fixtures under /data/local/tmp."
  say "VERDICT=TEST_INFRASTRUCTURE_FAILURE"
  exit 2
fi

pm uninstall "$PKG" >/dev/null 2>&1
pm uninstall "$SHARED_A" >/dev/null 2>&1
pm uninstall "$SHARED_B" >/dev/null 2>&1
SYS_BEFORE="$(pidof system_server 2>/dev/null | awk '{print $1}')"
say "system_server_before=$SYS_BEFORE"

section "[1] Baseline install"
if install_apk "baseline generation 2 / key A" "$STAGE/base-gen2-keyA.apk" 2; then
  pass "Normal baseline install succeeded."
else
  fail "Baseline fixture could not be installed."
  say ""
  say "Baseline installation failed before any CorePatch bypass behavior could be tested."
  say "VERDICT=TEST_INFRASTRUCTURE_OR_BASELINE_FAILURE"
  exit 3
fi

section "[2] Downgrade bypass"
if install_apk "downgrade generation 2 -> 1 / same key A / no -d" "$STAGE/base-gen1-keyA.apk" 1; then
  pass "Downgrade bypass is functionally working."
else
  fail "Downgrade install was rejected. Check BYPASS_DOWNGRADE."
fi

section "[3] Restore baseline"
if install_apk "restore generation 1 -> 2 / key A" "$STAGE/base-gen2-keyA.apk" 2; then
  pass "Baseline restored."
else
  fail "Could not restore baseline v2."
fi

section "[4] Different-signature update"
if install_apk "different signer generation 2/keyA -> 3/keyB" "$STAGE/diff-gen3-keyB.apk" 3; then
  pass "Different-signature replacement is functionally working."
else
  fail "Different-signature replacement was rejected. Check BYPASS_DIGEST / previous-signature path."
fi

section "[5] Tampered APK signature/integrity"
if install_apk "tampered generation 4 / same key B before tamper" "$STAGE/tampered-gen4-keyB.apk" 4; then
  pass "Tampered APK signature/integrity bypass is functionally working."
else
  fail "Tampered APK was rejected. This points to an incomplete integrity/signature bypass on the actual scheme recorded in signing-schemes.txt."
fi

section "[6] sharedUser different-signature admission"
pm uninstall "$SHARED_A" >/dev/null 2>&1
pm uninstall "$SHARED_B" >/dev/null 2>&1

RA="$(pm install "$STAGE/shared-a-keyA.apk" 2>&1)"
RCA=$?
say "shared A: $RA"
RB="$(pm install "$STAGE/shared-b-keyB.apk" 2>&1)"
RCB=$?
say "shared B: $RB"

if [ "$RCA" -eq 0 ] && echo "$RA" | grep -q Success &&
   [ "$RCB" -eq 0 ] && echo "$RB" | grep -q Success; then
  UID_A="$(dumpsys package "$SHARED_A" 2>/dev/null | sed -n 's/.*userId=\([0-9][0-9]*\).*/\1/p' | head -n1)"
  UID_B="$(dumpsys package "$SHARED_B" 2>/dev/null | sed -n 's/.*userId=\([0-9][0-9]*\).*/\1/p' | head -n1)"
  say "shared_uid_a=$UID_A"
  say "shared_uid_b=$UID_B"
  if [ -n "$UID_A" ] && [ "$UID_A" = "$UID_B" ]; then
    pass "Different-signature packages joined the same sharedUser UID."
  else
    fail "Both shared-user fixtures installed but did not receive the same UID."
  fi
else
  fail "Different-signature sharedUser admission failed. Check BYPASS_SHARED_USER."
fi

section "[7] Android Developer Verifier observation"
if pm path com.google.android.verifier >/dev/null 2>&1; then
  pass "Android Developer Verifier is installed."
else
  info "Android Developer Verifier package is not present."
fi

DEVLOG="$(logcat -d -b all 2>/dev/null | grep -Ei 'VERIFY_DEVELOPER|DeveloperVerif|developer verification|com\.google\.android\.verifier' | tail -n 80)"
if [ -n "$DEVLOG" ]; then
  info "Developer-verification-related runtime lines are present:"
  printf '%s\n' "$DEVLOG" | tee -a "$OUT"
else
  info "No developer-verification runtime line was retained. Shell installs may not exercise the production verifier gate."
fi

section "[8] system_server / PackageManager stability"
SYS_AFTER="$(pidof system_server 2>/dev/null | awk '{print $1}')"
say "system_server_after=$SYS_AFTER"
if [ -n "$SYS_BEFORE" ] && [ "$SYS_BEFORE" = "$SYS_AFTER" ]; then
  pass "system_server survived the full install matrix without restart."
else
  fail "system_server PID changed during the test."
fi

if cmd package list packages >/dev/null 2>&1; then
  pass "PackageManager remains responsive."
else
  fail "PackageManager is not responding normally."
fi

section "[9] Cleanup"
cleanup
if ! pm path "$PKG" >/dev/null 2>&1 &&
   ! pm path "$SHARED_A" >/dev/null 2>&1 &&
   ! pm path "$SHARED_B" >/dev/null 2>&1; then
  pass "All isolated test packages were removed."
else
  fail "At least one test fixture remained installed."
fi

section "[10] Result"
say "PASS=$PASS  INFO=$INFO  FAIL=$FAIL"
if [ "$FAIL" -eq 0 ]; then
  say "VERDICT=FUNCTIONAL_CORE_PATHS_PASS"
else
  say "VERDICT=FUNCTIONAL_GAPS_FOUND"
fi
say ""
say "Saved report: $OUT"
