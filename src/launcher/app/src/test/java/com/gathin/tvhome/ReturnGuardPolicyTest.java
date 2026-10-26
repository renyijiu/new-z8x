package com.gathin.tvhome;
import org.junit.Test;
import static org.junit.Assert.*;
public class ReturnGuardPolicyTest {
 @Test public void onlyArmsExternalAppLaunches() {
  assertTrue(ReturnGuardPolicy.shouldArm(true,"com.tencent.qqmusictv"));
  for (String pkg : new String[]{null,"com.gathin.tvhome","com.xgimi.home","local.projector.homeswitch","local.projector.returndiag"})
   assertFalse(ReturnGuardPolicy.shouldArm(true,pkg));
  assertFalse(ReturnGuardPolicy.shouldArm(false,"com.tencent.qqmusictv"));
 }
 @Test public void onlyExactStockHomeIsRedirectedWhenArmedAndDefault() {
  String cls="com.xgimi.module.cellview.home.ui.HomeActivity";
  assertTrue(ReturnGuardPolicy.shouldRedirect(true,true,true,"com.xgimi.home",cls));
  assertFalse(ReturnGuardPolicy.shouldRedirect(false,true,true,"com.xgimi.home",cls));
  assertFalse(ReturnGuardPolicy.shouldRedirect(true,false,true,"com.xgimi.home",cls));
  assertFalse(ReturnGuardPolicy.shouldRedirect(true,true,false,"com.xgimi.home",cls));
  assertFalse(ReturnGuardPolicy.shouldRedirect(true,true,true,"another.app",cls));
  assertFalse(ReturnGuardPolicy.shouldRedirect(true,true,true,"com.xgimi.home","com.xgimi.module.myapp.view.MyAppActivity"));
 }
 @Test public void timingRejectsPreviousBootStaleAndBackwardsSamples() {
  assertTrue(ReturnGuardPolicy.validTiming(1000,1200,2,2));
  assertFalse(ReturnGuardPolicy.validTiming(1000,1200,1,2));
  assertFalse(ReturnGuardPolicy.validTiming(1000,999,2,2));
  assertFalse(ReturnGuardPolicy.validTiming(1000,17000,2,2));
  assertFalse(ReturnGuardPolicy.validTiming(1000,1200,-1,-1));
 }
 @Test public void togglingPreservesOtherAccessibilityServices() {
  String existing="reader/.Service:remote/.Keys";
  assertEquals(existing+":"+ReturnGuardPolicy.SERVICE,ReturnGuardPolicy.serviceList(existing,true));
  assertEquals(existing,ReturnGuardPolicy.serviceList(existing+":"+ReturnGuardPolicy.SERVICE,false));
  assertEquals(existing,ReturnGuardPolicy.serviceList(existing+":com.gathin.tvhome/.ReturnGuardService",false));
  String enabled=ReturnGuardPolicy.serviceList(null,true);
  assertEquals(enabled,ReturnGuardPolicy.serviceList(enabled,true));
  assertEquals("",ReturnGuardPolicy.serviceList(enabled,false));
 }
}
