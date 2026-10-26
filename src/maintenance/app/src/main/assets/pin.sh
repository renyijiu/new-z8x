cfg=/data/local/xgimi_gmui_manager/sys_admin_pkg.json
backup=/data/local/tmp/codex-launcher-background-pin-v1
tmp=/data/local/xgimi_gmui_manager/.codex-launcher-background.tmp
fail() { echo "whitelist=failed:$1"; exit 61; }
[ -f "$cfg" ] && [ ! -L "$cfg" ] || fail config-type
[ ! -e "$tmp" ] && [ ! -L "$tmp" ] || fail temp-exists
[ "$(/system/bin/cat "$cfg")" = '{"broadcastblack":["cn.cibntv.ott_4k","com.ktcp.tvvideo","com.gitvjimi.video","com.xiaobaifile.tv"],"backgroundrunningblack":["com.pptv.tvsports","com.sohuott.tv.vod","com.cibn.tv","com.hunantv.license","com.gitvjimi.video","com.ktcp.tvvideo","cn.wps.moffice_i18n_TV","cn.cibntv.ott_4k","com.xiaodianshi.tv.yst","com.xiaobaifile.tv","com.baidu.netdisk.tv"],"persistent":["com.mstar.tv.tvplayer.ui","com.xgimi.duertts","com.xgimi.wirelessscreen","com.xgimi.vcontrol","com.xgimi.iot","com.xgimi.upgrade","com.xgimi.inuiserver","com.android.newsettings","com.android.defcontainer","com.xgimi.shutdown"],"musicplayer":["com.dangbei.dbmusic","com.tencent.qqmusictv","com.kugou.android.tv","org.xbmc.kodi"],"backgroundrunningwhite":["com.xgimi.vcontrol:iot","com.xgimi.vcontrol","com.xgimi.vcontrol:netty","com.xgimi.filemanager","com.xgimi.account","com.xgimi.iot","com.xgimi.manager","com.xgimi.datareporter","com.xgimi.inuiserver","com.android.newsettings"],"appInstalledWhiteList":["com.xgimi.vcontrol","com.xgimi.newappmarket","com.xgimi.upgrade","com.gitvjimi.video","com.cibn.tv","com.hunantv.license","com.ktcp.tvvideo","com.xgimi.inuiserver"]}' ] || fail config-changed
[ ! -e "$backup" ] && [ ! -L "$backup" ] || fail backup-exists
/system/bin/mkdir "$backup" || fail backup-create
/system/bin/chmod 700 "$backup" || fail backup-mode
/system/bin/cp -p "$cfg" "$backup/original.json" || fail backup-copy
/system/bin/cmp -s "$cfg" "$backup/original.json" || fail backup-verify
/system/bin/cp -p "$cfg" "$tmp" || fail temp-copy
printf '%s\n' '{"broadcastblack":["cn.cibntv.ott_4k","com.ktcp.tvvideo","com.gitvjimi.video","com.xiaobaifile.tv"],"backgroundrunningblack":["com.pptv.tvsports","com.sohuott.tv.vod","com.cibn.tv","com.hunantv.license","com.gitvjimi.video","com.ktcp.tvvideo","cn.wps.moffice_i18n_TV","cn.cibntv.ott_4k","com.xiaodianshi.tv.yst","com.xiaobaifile.tv","com.baidu.netdisk.tv"],"persistent":["com.mstar.tv.tvplayer.ui","com.xgimi.duertts","com.xgimi.wirelessscreen","com.xgimi.vcontrol","com.xgimi.iot","com.xgimi.upgrade","com.xgimi.inuiserver","com.android.newsettings","com.android.defcontainer","com.xgimi.shutdown"],"musicplayer":["com.dangbei.dbmusic","com.tencent.qqmusictv","com.kugou.android.tv","org.xbmc.kodi"],"backgroundrunningwhite":["com.gathin.tvhome","com.xgimi.vcontrol:iot","com.xgimi.vcontrol","com.xgimi.vcontrol:netty","com.xgimi.filemanager","com.xgimi.account","com.xgimi.iot","com.xgimi.manager","com.xgimi.datareporter","com.xgimi.inuiserver","com.android.newsettings"],"appInstalledWhiteList":["com.xgimi.vcontrol","com.xgimi.newappmarket","com.xgimi.upgrade","com.gitvjimi.video","com.cibn.tv","com.hunantv.license","com.ktcp.tvvideo","com.xgimi.inuiserver"]}' > "$tmp" || fail temp-write
[ "$(/system/bin/cat "$tmp")" = '{"broadcastblack":["cn.cibntv.ott_4k","com.ktcp.tvvideo","com.gitvjimi.video","com.xiaobaifile.tv"],"backgroundrunningblack":["com.pptv.tvsports","com.sohuott.tv.vod","com.cibn.tv","com.hunantv.license","com.gitvjimi.video","com.ktcp.tvvideo","cn.wps.moffice_i18n_TV","cn.cibntv.ott_4k","com.xiaodianshi.tv.yst","com.xiaobaifile.tv","com.baidu.netdisk.tv"],"persistent":["com.mstar.tv.tvplayer.ui","com.xgimi.duertts","com.xgimi.wirelessscreen","com.xgimi.vcontrol","com.xgimi.iot","com.xgimi.upgrade","com.xgimi.inuiserver","com.android.newsettings","com.android.defcontainer","com.xgimi.shutdown"],"musicplayer":["com.dangbei.dbmusic","com.tencent.qqmusictv","com.kugou.android.tv","org.xbmc.kodi"],"backgroundrunningwhite":["com.gathin.tvhome","com.xgimi.vcontrol:iot","com.xgimi.vcontrol","com.xgimi.vcontrol:netty","com.xgimi.filemanager","com.xgimi.account","com.xgimi.iot","com.xgimi.manager","com.xgimi.datareporter","com.xgimi.inuiserver","com.android.newsettings"],"appInstalledWhiteList":["com.xgimi.vcontrol","com.xgimi.newappmarket","com.xgimi.upgrade","com.gitvjimi.video","com.cibn.tv","com.hunantv.license","com.ktcp.tvvideo","com.xgimi.inuiserver"]}' ] || fail temp-verify
[ "$(/system/bin/stat -c '%a:%u:%g' "$cfg")" = "$(/system/bin/stat -c '%a:%u:%g' "$tmp")" ] || fail metadata
/system/bin/chown 0:1000 "$tmp" || fail pin-owner
/system/bin/chmod 444 "$tmp" || fail pin-mode
/system/bin/cp -p "$tmp" "$backup/applied.json" || fail applied-copy
/system/bin/cmp -s "$cfg" "$backup/original.json" || fail concurrent-change
/system/bin/mv "$tmp" "$cfg" || fail replace
/system/bin/restorecon "$cfg" || fail selinux-label
/system/bin/cmp -s "$cfg" "$backup/applied.json" || fail readback
[ "$(/system/bin/stat -c '%a:%u:%g' "$cfg")" = '444:0:1000' ] || fail pin-verify
echo 'whitelist=readonly-applied-reboot-required'
