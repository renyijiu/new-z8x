cfg=/data/local/xgimi_gmui_manager/sys_admin_pkg.json
backup=/data/local/tmp/codex-launcher-background-pin-v1
tmp=/data/local/xgimi_gmui_manager/.codex-launcher-background.tmp
fail() { echo "whitelist=failed:$1"; exit 61; }
[ -f "$cfg" ] && [ ! -L "$cfg" ] || fail config-type
[ ! -e "$tmp" ] && [ ! -L "$tmp" ] || fail temp-exists
[ -d "$backup" ] && [ ! -L "$backup" ] || fail backup-missing
[ -f "$backup/original.json" ] && [ ! -L "$backup/original.json" ] || fail original-missing
/system/bin/cmp -s "$cfg" "$backup/applied.json" || fail config-changed
/system/bin/cp -p "$backup/original.json" "$tmp" || fail restore-copy
/system/bin/cmp -s "$tmp" "$backup/original.json" || fail restore-verify
/system/bin/cmp -s "$cfg" "$backup/applied.json" || fail concurrent-change
/system/bin/mv "$tmp" "$cfg" || fail restore-replace
/system/bin/restorecon "$cfg" || fail selinux-label
/system/bin/cmp -s "$cfg" "$backup/original.json" || fail restore-readback
echo 'whitelist=restored-reboot-required'
