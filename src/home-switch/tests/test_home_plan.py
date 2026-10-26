from pathlib import Path
import os,subprocess,tempfile,json,shutil,sys
PROJECT=Path(__file__).resolve().parents[1]
JAVA=Path(os.environ['JAVA_HOME'])/'bin/java' if 'JAVA_HOME' in os.environ else Path(shutil.which('java'))
RUN='0123456789abcdef0123456789abcdef'
CLEAN='com.gathin.tvhome/com.gathin.tvhome.MainActivity'
STOCK='com.xgimi.home/com.xgimi.module.cellview.home.ui.HomeActivity'
MOCK='#!' + sys.executable + '''
import sys,os
from pathlib import Path
state=Path(os.environ['HOME_TEST_STATE'])
mode=os.environ['HOME_TEST_MODE']
if sys.argv[2]=='query-activities':
 print('com.gathin.tvhome/.MainActivity');sys.exit(0)
if sys.argv[2]=='resolve-activity':
 assert 'android.intent.category.DEFAULT' in sys.argv, 'unsafe HOME resolution without DEFAULT' 
 print(state.read_text().replace('com.gathin.tvhome/com.gathin.tvhome.MainActivity','com.gathin.tvhome/.MainActivity'));sys.exit(0)
if sys.argv[2]!='set-home-activity':sys.exit(99)
with open(str(state)+'.calls','a') as f:f.write(sys.argv[-1]+'\\n')
n=len(Path(str(state)+'.calls').read_text().splitlines())
if mode=='fail-first' and n==1:state.write_text('unknown/home');sys.exit(1)
if mode=='mismatch' and n==1:state.write_text('unknown/home');sys.exit(0)
if mode=='rollback-fails':state.write_text('unknown/home');sys.exit(1)
state.write_text(sys.argv[-1]);print('Success')
'''
cases=[('select','clean',STOCK,'normal','0',0,CLEAN,'status=selected'),('restore','stock',CLEAN,'normal','0',0,STOCK,'status=selected'),('already','clean',CLEAN,'normal','0',0,CLEAN,'status=already-selected'),('failed-set','clean',STOCK,'fail-first','0',43,STOCK,'rollback_verified=true'),('mismatch','clean',STOCK,'mismatch','0',43,STOCK,'rollback_verified=true'),('failed-rollback','clean',STOCK,'rollback-fails','0',43,'unknown/home','rollback_verified=false'),('unknown-home','clean','unknown/home','normal','0',42,'unknown/home','status=unexpected-original-home'),('nonroot','clean',STOCK,'normal','10053',40,STOCK,None)]
RESOLVER='android/com.android.internal.app.ResolverActivity'
cases += [('resolver-select','clean',RESOLVER,'normal','0',0,CLEAN,'status=selected'),('resolver-restore','stock',RESOLVER,'normal','0',0,STOCK,'status=selected'),('resolver-failed-set','clean',RESOLVER,'fail-first','0',43,STOCK,'rollback_verified=true')]
cases += [('repeat-clean','clean',STOCK,'normal','0',0,CLEAN,'status=selected'), ('repeat-stock','stock',CLEAN,'normal','0',0,STOCK,'status=selected')]
results=[]
for name,action,before,mode,uid,exitcode,after,marker in cases:
 script=subprocess.check_output([str(JAVA),'-cp',str(PROJECT/'test-classes'),'local.projector.homeswitch.HomePlanHarness',RUN,action],text=True)
 with tempfile.TemporaryDirectory(prefix='z8x-home-test-') as temp:
  d=Path(temp);bin=d/'bin';bin.mkdir();ssh=d/'ssh';ssh.mkdir();run=ssh/('codex-probe-'+RUN);run.mkdir();state=d/'state';state.write_text(before)
  def write(name,text):
   p=bin/name;p.write_text(text);p.chmod(0o700)
  for name2 in ['chmod','cat','rm','sh','head']:(bin/name2).symlink_to(shutil.which(name2))
  for name2 in ['setprop','killall','sleep','dumpsys']:write(name2,'#!/bin/sh\nexit 0\n')
  write('id','#!/bin/sh\necho '+uid+'\n');write('am','#!/bin/sh\necho 0\n');write('timeout','#!/bin/sh\nshift\nexec "$@"\n');write('cmd',MOCK)
  transformed=script.replace('/system/bin/',str(bin)+'/').replace('/data/ssh/',str(ssh)+'/')
  params='-oBatchMode=yes -oConnectTimeout=75 -oConnectionAttempts=1 -oProxyCommand='+str(run)+'/probe.sh 127.0.0.1\n';(ssh/'params').write_text(params)
  env=dict(os.environ,HOME_TEST_STATE=str(state),HOME_TEST_MODE=mode)
  out=subprocess.run(['/bin/sh'],input=transformed,text=True,capture_output=True,timeout=15,env=env)
  assert out.returncode==exitcode,(name,out.returncode,out.stderr)
  assert state.read_text()==after,name
  if marker:assert marker in (run/'home.txt').read_text(),name
  if uid=='0':assert (run/'cleanup.txt').exists() and not (ssh/'params').exists(),name
  else:assert not (run/'home.txt').exists() and not Path(str(state)+'.calls').exists()
  results.append({'case':name,'passed':True})
for run,action in [('invalid','clean'),(RUN,'invalid')]:
 r=subprocess.run([str(JAVA),'-cp',str(PROJECT/'test-classes'),'local.projector.homeswitch.HomePlanHarness',run,action],capture_output=True)
 assert r.returncode!=0
results.append({'case':'invalid-input-rejected','passed':True})
(PROJECT/'shell-tests.json').write_text(json.dumps(results,indent=2)+'\n')
print(json.dumps(results,indent=2))
