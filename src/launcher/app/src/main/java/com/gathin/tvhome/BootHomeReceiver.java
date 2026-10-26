package com.gathin.tvhome;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
/** Restores only this user's enabled return helper, preserving all other accessibility services. */
public final class BootHomeReceiver extends BroadcastReceiver {
 @Override public void onReceive(Context context,Intent intent) {
  if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction()) || !BootHome.pending(context)) return;
  ReturnGuard.prefs(context).edit().putString("boot_home_status","收到开机通知，恢复兼容服务").apply();
  try {
   ReturnGuard.setEnabled(context,true);
   if (ReturnGuardService.connected) BootHome.probe(context);
  } catch (RuntimeException e) {
   ReturnGuard.prefs(context).edit().putString("boot_home_status","开机恢复服务失败："+e.getClass().getSimpleName()).apply();
  }
 }
}
