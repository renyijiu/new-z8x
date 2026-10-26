from pathlib import Path
import os, shutil, subprocess, sys
project = Path(__file__).resolve().parents[1]
javac = str(Path(os.environ['JAVA_HOME'])/'bin/javac') if 'JAVA_HOME' in os.environ else shutil.which('javac')
java = str(Path(os.environ['JAVA_HOME'])/'bin/java') if 'JAVA_HOME' in os.environ else shutil.which('java')
source = project/'app/src/main/java/local/projector/homeswitch'
classes = project/'test-classes'
classes.mkdir(exist_ok=True)
subprocess.run([javac,'-d',str(classes), *[str(source/f'{x}.java') for x in ['RootPlan','HomePlan','SwitchPolicy']],
    str(project/'tests/HomePlanHarness.java'),str(project/'tests/SwitchPolicyTest.java')],check=True)
subprocess.run([java,'-cp',str(classes),'local.projector.homeswitch.SwitchPolicyTest'],check=True)
subprocess.run([sys.executable,str(project/'tests/test_home_plan.py')],check=True)
