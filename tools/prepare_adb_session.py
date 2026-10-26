#!/usr/bin/env python3
"""Generate a private, task-scoped ADB bootstrap build. Never overwrite a session."""
import argparse,ipaddress,json,secrets,shutil,subprocess
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
def main():
 p=argparse.ArgumentParser(description=__doc__)
 p.add_argument('--host',required=True,help='Computer LAN IPv4')
 p.add_argument('--projector',required=True,help='Projector LAN IPv4')
 p.add_argument('--adb',default='adb',help='Path to Android SDK adb')
 a=p.parse_args()
 host=str(ipaddress.IPv4Address(a.host));projector=str(ipaddress.IPv4Address(a.projector))
 out=ROOT/'.local/adb-session'
 if out.exists():p.error('Session already exists. Verify device cleanup first; archive it before generating another.')
 out.mkdir(parents=True,mode=0o700);out.chmod(0o700)
 key=out/'session-key';subprocess.run([a.adb,'keygen',str(key)],check=True,capture_output=True);key.chmod(0o600)
 public=key.with_suffix('.pub').read_text().split()[0]+' codex-z8x-temporary'
 state='/data/local/tmp/codex-adb-'+secrets.token_hex(8)
 token=secrets.token_hex(32)
 project=out/'maintenance';shutil.copytree(ROOT/'src/maintenance',project)
 assets=project/'app/src/main/assets'
 for name in ['adb_open.sh','adb_close.sh']:
  f=assets/name;f.write_text(f.read_text().replace('@SESSION_STATE@',state).replace('@SESSION_PUBLIC_KEY@',public))
 (assets/'receiver.json').write_text(json.dumps(dict(host=host,port=18767,token=token)))
 f=project/'app/src/main/res/xml/network_security_config.xml';f.write_text(f.read_text().replace('127.0.0.1',host))
 config=out/'receiver.json';config.write_text(json.dumps(dict(host=host,projector=projector,port=18767,token=token,result_path=str(out/'result.json')),indent=2));config.chmod(0o600)
 (out/'device-state-path.txt').write_text(state+'\n')
 print('Prepared private session at',out)
 print('Build maintenance/ with JDK17 and Android SDK34. Start tools/adb_receiver.py with the generated receiver.json before opening ADB.')
 print('Keep this session and signing key until device cleanup is verified. Do not share generated APK/config/private key.')
if __name__=='__main__':main()
