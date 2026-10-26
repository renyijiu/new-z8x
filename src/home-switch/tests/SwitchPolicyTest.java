package local.projector.homeswitch;
public final class SwitchPolicyTest {
    static final String HASH = "b559aa0e7c54ea2b746e0e607c97e6141f68a0c277f507c4dacdc587dec153ad";
    static final String INIT = "on boot\n    setprop example 0\n\nservice xgimi_ssh /vendor/bin/xgimi_ssh.sh\n    class main\n    oneshot\n    seclabel u:r:xgimi_ssh:s0\n\nservice unrelated /bin/example\n    user shell\n";
    static int count;
    static void check(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
        count++; System.out.println("PASS " + label);
    }
    public static void main(String[] args) {
        check(SwitchPolicy.compatible(26,"ironman","m7221","v12.15.214",HASH,INIT), "known firmware accepted");
        check(!SwitchPolicy.compatible(27,"ironman","m7221","v12.15.214",HASH,INIT), "unsupported Android refused");
        check(!SwitchPolicy.compatible(26,"other","m7221","v12.15.214",HASH,INIT), "other device refused");
        check(!SwitchPolicy.compatible(26,"ironman","other","v12.15.214",HASH,INIT), "other hardware refused");
        check(!SwitchPolicy.compatible(26,"ironman","m7221","new",HASH,INIT), "updated firmware refused");
        check(!SwitchPolicy.compatible(26,"ironman","m7221","v12.15.214","changed",INIT), "changed vendor script refused");
        check(!SwitchPolicy.compatible(26,"ironman","m7221","v12.15.214",HASH,INIT.replace("    oneshot","    user shell\n    oneshot")), "changed service permissions refused");
        check(!SwitchPolicy.compatible(26,"ironman","m7221","v12.15.214",HASH,INIT+INIT), "duplicate service refused");
        check(!SwitchPolicy.compatible(26,"ironman","m7221","v12.15.214",HASH,INIT.replace("/vendor/bin/xgimi_ssh.sh","/vendor/bin/other.sh")), "changed executable refused");
        check(!SwitchPolicy.compatible(26,"ironman","m7221","v12.15.214",HASH,""), "missing service refused");
        check(SwitchPolicy.success(true,true,HomePlan.CLEAN,HomePlan.CLEAN), "actual readback accepted even without log marker");
        check(SwitchPolicy.success(true,true,HomePlan.STOCK,HomePlan.STOCK), "stock readback accepted");
        check(!SwitchPolicy.success(true,false,HomePlan.CLEAN,HomePlan.CLEAN), "cleanup failure cannot report success");
        check(!SwitchPolicy.success(false,true,HomePlan.CLEAN,HomePlan.CLEAN), "missing proof cannot report success");
        check(!SwitchPolicy.success(true,true,HomePlan.CLEAN,HomePlan.STOCK), "wrong default cannot report success");
        check(!SwitchPolicy.success(true,true,HomePlan.CLEAN,"unknown"), "unknown readback cannot report success");
        System.out.println(count + " policy tests passed");
    }
}
