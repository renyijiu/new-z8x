# Generated session required; do not run this template directly.
close_adb_session() {
STATE='@SESSION_STATE@'
KEY='@SESSION_PUBLIC_KEY@'
KEYS=/data/misc/adb/adb_keys
if [ ! -e "$STATE" ]; then echo 'adb_close=no-session'; return 0; fi
[ -d "$STATE" ] && [ ! -L "$STATE" ] && [ "$(/system/bin/stat -c %u "$STATE")" = 0 ] || return 61
if [ -f "$STATE/prepared" ]; then
  /system/bin/setprop ctl.stop adbd || return 62
  /system/bin/setprop service.adb.tcp.port "$(/system/bin/cat "$STATE/tcp_before")" || return 62
  [ ! -L "$KEYS" ] || return 63
  if [ -f "$KEYS" ]; then
    if /system/bin/cmp -s "$KEYS" "$STATE/expected_keys"; then
      if [ -f "$STATE/original_keys" ]; then /system/bin/cp -p "$STATE/original_keys" "$KEYS" || return 64
      else /system/bin/rm "$KEYS" || return 64; fi
    else
      (
        while :; do
          line=
          IFS= read -r line
          read_rc=$?
          [ "$read_rc" = 0 ] || [ -n "$line" ] || break
          if [ "$line" != "$KEY" ]; then
            printf '%s' "$line" || exit 64
            if [ "$read_rc" = 0 ]; then printf '\n' || exit 64; fi
          fi
          [ "$read_rc" = 0 ] || break
        done < "$KEYS"
      ) > "$STATE/filtered_keys" || return 64
      /system/bin/cat "$STATE/filtered_keys" > "$KEYS" || return 64
    fi
  fi
  if [ -f "$KEYS" ]; then
    while IFS= read -r line || [ -n "$line" ]; do
      [ "$line" != "$KEY" ] || return 65
    done < "$KEYS"
  fi
fi
/system/bin/rm -f "$STATE/original_keys" "$STATE/expected_keys" "$STATE/tcp_before" "$STATE/prepared" "$STATE/filtered_keys"
/system/bin/rmdir "$STATE" || return 66
/system/bin/sleep 2
[ "$(/system/bin/getprop init.svc.adbd)" = stopped ] || return 67
echo 'adb_close=verified'
}
close_adb_session || { echo adb_close=failed; exit 68; }
