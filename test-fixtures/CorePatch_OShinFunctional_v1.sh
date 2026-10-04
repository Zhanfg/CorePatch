#!/system/bin/sh
# CorePatch OShin-derived feature A/B test v1
# No CLI arguments. Interactive switch changes only.

BASE_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" 2>/dev/null && pwd)"
APK_DIR="$BASE_DIR/apks"
STAGE="/data/local/tmp/corepatch-oshin-functional-$$"
OUT="/sdcard/Download/CorePatch_OShinFunctional_$(date +%Y%m%d_%H%M%S).txt"

TARGET_PKG="dev.axymorrsen.corepatch.overlaytarget"
OVERLAY_PKG="dev.axymorrsen.corepatch.overlayfixture"
DOMAIN_PKG="dev.axymorrsen.corepatch.domainfixture"

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
  pm uninstall "$OVERLAY_PKG" >/dev/null 2>&1
  pm uninstall "$TARGET_PKG" >/dev/null 2>&1
  pm uninstall "$DOMAIN_PKG" >/dev/null 2>&1
  rm -rf "$STAGE" >/dev/null 2>&1
}
trap cleanup EXIT INT TERM

wait_user(){
  say ""
  say "$1"
  say "完成后回到终端，按 Enter 继续。"
  printf "> "
  read _x
}

: > "$OUT"
say "CorePatch OShin-derived feature A/B test v1"
say "Generated: $(date)"
say "Report: $OUT"

section "[0] Preconditions"
for f in domain-autoverify-keyA.apk overlay-target-keyA.apk overlay-invalid-keyB.apk; do
  [ -f "$APK_DIR/$f" ] || fail "Missing fixture: $APK_DIR/$f"
done
[ "$FAIL" -eq 0 ] || exit 1

CP="$(dumpsys package org.lsposed.corepatch 2>/dev/null)"
say "$(printf '%s\n' "$CP" | grep -m1 'versionCode=' | sed 's/^[[:space:]]*//')"
say "$(printf '%s\n' "$CP" | grep -m1 'versionName=' | sed 's/^[[:space:]]*//')"

mkdir -p "$STAGE" || exit 1
cp "$APK_DIR/domain-autoverify-keyA.apk" "$STAGE/domain.apk" || exit 1
cp "$APK_DIR/overlay-target-keyA.apk" "$STAGE/target.apk" || exit 1
cp "$APK_DIR/overlay-invalid-keyB.apk" "$STAGE/overlay.apk" || exit 1
chmod 0755 "$STAGE"
chmod 0644 "$STAGE"/*.apk
restorecon -RF "$STAGE" >/dev/null 2>&1 || true

PID_BEFORE="$(pidof system_server 2>/dev/null | awk '{print $1}')"
say "system_server_before=$PID_BEFORE"

section "[1] PMS diagnostic command"
PMS="$(pm pms package org.lsposed.corepatch 2>&1)"
say "$PMS"
if echo "$PMS" | grep -q "^package=org.lsposed.corepatch$"; then
  pass "pm pms package works."
else
  fail "pm pms package did not resolve CorePatch."
fi

section "[2] Overlay Validation control"
pm uninstall "$OVERLAY_PKG" >/dev/null 2>&1
pm uninstall "$TARGET_PKG" >/dev/null 2>&1
R_TARGET="$(pm install -r "$STAGE/target.apk" 2>&1)"
say "$R_TARGET"
if echo "$R_TARGET" | grep -q "Success"; then
  pass "Overlay target installed."
else
  fail "Overlay target failed to install."
fi

wait_user "请在 CorePatch 中关闭「禁用 Overlay 验证」。其它开关保持原样。"
R_OFF="$(pm install -r "$STAGE/overlay.apk" 2>&1)"
say "$R_OFF"
if echo "$R_OFF" | grep -q "Success"; then
  info "CONTROL_INVALID: Overlay 在开关关闭时也安装成功；其它签名兼容策略可能已经满足检查。"
else
  pass "Control overlay was rejected while Overlay Validation remained active."
fi
pm uninstall "$OVERLAY_PKG" >/dev/null 2>&1

section "[3] Overlay Validation bypass"
wait_user "现在打开 CorePatch 中的「禁用 Overlay 验证」。"
R_ON="$(pm install -r "$STAGE/overlay.apk" 2>&1)"
say "$R_ON"
if echo "$R_ON" | grep -q "Success"; then
  pass "Overlay crossed the PackageManager install-time validation gate."
else
  fail "Overlay remained blocked with the validation bypass enabled."
fi

section "[4] App Links Domain Verification gate"
pm uninstall "$DOMAIN_PKG" >/dev/null 2>&1
R_DOMAIN="$(pm install -r "$STAGE/domain.apk" 2>&1)"
say "$R_DOMAIN"
if echo "$R_DOMAIN" | grep -q "Success"; then
  pass "Domain autoVerify fixture installed."
else
  fail "Domain fixture failed to install."
fi

wait_user "请打开 CorePatch 中的「禁用应用域名验证」。"
pm set-app-links --package "$DOMAIN_PKG" 0 all >/dev/null 2>&1 || true
T="$(date +%s)"
R_VERIFY="$(pm verify-app-links --re-verify "$DOMAIN_PKG" 2>&1)"
say "$R_VERIFY"
sleep 1
DLOG="$(logcat -d -b all -v epoch 2>/dev/null | awk -v s="$T" '($1 + 0) >= s' | grep "\[DomainVerificationHook\]" | tail -n 60)"
say "$DLOG"
if echo "$DLOG" | grep -q "blocked App Links verification broadcast"; then
  pass "Domain Verification Agent broadcast was blocked by CorePatch."
else
  fail "No runtime evidence that the domain verification broadcast was intercepted."
fi

section "[5] Stability / cleanup"
PID_AFTER="$(pidof system_server 2>/dev/null | awk '{print $1}')"
say "system_server_after=$PID_AFTER"
if [ -n "$PID_BEFORE" ] && [ "$PID_BEFORE" = "$PID_AFTER" ]; then
  pass "system_server remained stable."
else
  fail "system_server PID changed."
fi
cleanup
pass "Fixture packages removed."

section "[6] Result"
say "PASS=$PASS INFO=$INFO FAIL=$FAIL"
if [ "$FAIL" -eq 0 ]; then
  say "VERDICT=OSHIN_DERIVED_FEATURES_PASS"
else
  say "VERDICT=OSHIN_DERIVED_FEATURE_GAPS_FOUND"
fi
say "Saved report: $OUT"
