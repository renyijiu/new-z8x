package local.projector.returndiag;
import android.system.Os;
import android.system.OsConstants;
import android.system.ErrnoException;
import java.io.FileDescriptor;
/** Fixed path, non-truncating open only. Never writes bytes or changes the policy. */
public final class PolicyWriteCheck {
 public static void main(String[] args) throws Exception {
  if (Os.getuid() != 0) throw new SecurityException("root entry required");
  Os.setgid(1000); Os.setuid(1000);
  if (Os.getuid()!=1000 || Os.geteuid()!=1000) throw new SecurityException("uid drop failed");
  try {
   FileDescriptor fd=Os.open("/data/local/xgimi_gmui_manager/sys_admin_pkg.json",OsConstants.O_WRONLY,0);
   Os.close(fd); System.out.println("system_uid_write=ALLOWED");
  } catch (ErrnoException e) {
   if (e.errno != OsConstants.EACCES) throw e;
   System.out.println("system_uid_write=DENIED_EACCES");
  }
 }
}
