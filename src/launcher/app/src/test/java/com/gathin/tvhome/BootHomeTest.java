package com.gathin.tvhome;
import org.junit.Test;
import static org.junit.Assert.*;
public class BootHomeTest {
 @Test public void firstBootOnlyWhileEnabledAndDefault() {
  assertTrue(BootHome.eligible(true,true,117,116,45000));
  assertFalse(BootHome.eligible(false,true,117,116,45000));
  assertFalse(BootHome.eligible(true,false,117,116,45000));
  assertFalse(BootHome.eligible(true,true,117,117,45000));
  assertFalse(BootHome.eligible(true,true,-1,116,45000));
 }
 @Test public void neverHijackLateServiceRebindOrInvalidClock() {
  assertFalse(BootHome.eligible(true,true,117,116,180000));
  assertFalse(BootHome.eligible(true,true,117,116,-1));
 }
 @Test public void onlyStockHomeCanTriggerBootReturn() {
  assertTrue(ReturnGuardPolicy.shouldRedirect(true,true,true,"com.xgimi.home","com.xgimi.module.cellview.home.ui.HomeActivity"));
  assertFalse(ReturnGuardPolicy.shouldRedirect(true,true,true,"com.xgimi.xhplayer","com.xgimi.xhplayer.InputActivity"));
  assertFalse(ReturnGuardPolicy.shouldRedirect(true,true,true,"com.xgimi.home","other.Activity"));
 }
}
