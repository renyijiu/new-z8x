# Generated session required; do not run this template directly.
STATE='@SESSION_STATE@'
KEY='@SESSION_PUBLIC_KEY@'
KEYS=/data/misc/adb/adb_keys
[ ! -e "$STATE" ] || { echo 'adb_open=session-exists-close-first'; exit 61; }
[ -d /data/misc/adb ] && [ ! -L /data/misc/adb ] && [ ! -L "$KEYS" ] || exit 62
[ ! -e "$KEYS" ] || [ -f "$KEYS" ] || exit 62
/system/bin/mkdir -m 700 "$STATE" || exit 63
/system/bin/getprop service.adb.tcp.port > "$STATE/tcp_before" || exit 63
if [ -f "$KEYS" ]; then /system/bin/cp -p "$KEYS" "$STATE/original_keys" || exit 63; fi
: > "$STATE/prepared" || exit 63
if [ -s "$KEYS" ]; then printf '\n' >> "$KEYS" || exit 64; fi
printf '%s\n' "$KEY" >> "$KEYS" || exit 64
/system/bin/chown 1000:2000 "$KEYS" || exit 64
/system/bin/chmod 640 "$KEYS" || exit 64
/system/bin/restorecon "$KEYS" || exit 64
/system/bin/cp -p "$KEYS" "$STATE/expected_keys" || exit 64
/system/bin/setprop service.adb.tcp.port 5555 || exit 65
/system/bin/setprop ctl.start adbd || exit 65
/system/bin/sleep 2
printf 'adb_service=%s\n' "$(/system/bin/getprop init.svc.adbd)"
echo 'adb_open=key-installed'
