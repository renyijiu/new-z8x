package com.gathin.tvhome;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import android.os.SystemClock;
/** One bounded boot attempt, shared by the boot receiver and accessibility bind callback. */
final class BootHome {
 static int boot(Context c) { return Settings.Global.getInt(c.getContentResolver(), Settings.Global.BOOT_COUNT, -1); }
 static boolean eligible(boolean enabled, boolean defaultHome, int boot, int consumed, long uptime) {
  return enabled && defaultHome && boot >= 0 && boot != consumed && uptime >= 0 && uptime < 180000;
 }
 static boolean pending(Context c) {
  return eligible(ReturnGuard.enabled(c),ReturnGuard.isDefault(c),boot(c),
   ReturnGuard.prefs(c).getInt("boot_home_consumed_v13",-1),SystemClock.elapsedRealtime());
 }
 static void consume(Context c) {
  ReturnGuard.prefs(c).edit().putInt("boot_home_consumed_v13",boot(c)).commit();
 }
 static void probe(Context c) {
  if (!pending(c)) return;
  int boot=boot(c);
  if (ReturnGuard.prefs(c).getInt("boot_home_probe_v13",-1)==boot) return;
  ReturnGuard.prefs(c).edit().putInt("boot_home_probe_v13",boot).putString("boot_home_status","开机服务已连接，等待首页事件").commit();
  try { c.startActivity(new Intent(c,ProbeActivity.class)
    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_NO_ANIMATION)); }
  catch (android.content.ActivityNotFoundException|SecurityException e) {
   ReturnGuard.prefs(c).edit().putString("boot_home_status","开机窗口检查失败").apply();
  }
 }
}
