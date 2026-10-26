#!/bin/sh
set -eu
transfer_password=$(cat /run/transfer-password)
printf '%s\n%s\n' "$transfer_password" "$transfer_password" | smbpasswd -s -a projector >/dev/null
unset transfer_password
testparm -s >/dev/null
# End this temporary transfer service after one hour, even if the chat stops.
exec timeout -s TERM 3600 smbd --foreground --no-process-group --debug-stdout
