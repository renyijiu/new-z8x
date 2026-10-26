"""One bounded diagnostic receiver. No command/control endpoint."""
import json,hmac,time,argparse,os
from pathlib import Path
from http.server import HTTPServer,BaseHTTPRequestHandler

def run(path,duration=3600):
 config=json.loads(Path(path).read_text());state={'done':False}
 class Handler(BaseHTTPRequestHandler):
  def log_message(self,*args):pass
  def setup(self):super().setup();self.connection.settimeout(5)
  def auth(self):return self.client_address[0]==config['projector'] and hmac.compare_digest(self.headers.get('X-Report-Token',''),config['token'])
  def reply(self):
   body=b'{"received":true}';self.send_response(200);self.send_header('Content-Length',str(len(body)));self.end_headers();self.wfile.write(body)
  def do_GET(self):
   if not self.auth() or self.path!='/ready':self.send_error(403);return
   self.reply()
  def do_POST(self):
   if not self.auth() or self.path!='/result':self.send_error(403);return
   try:
    size=int(self.headers.get('Content-Length','0'))
    if self.headers.get('Transfer-Encoding') or not 0<size<=1048576:raise ValueError()
    raw=self.rfile.read(size)
    if len(raw)!=size:raise ValueError()
    data=json.loads(raw)
    if not isinstance(data,dict) or not isinstance(data.get('run_id'),str):raise ValueError()
    p=Path(config['result_path']);tmp=p.with_suffix('.tmp');tmp.write_text(json.dumps(data,ensure_ascii=False,indent=2));tmp.chmod(0o600);os.replace(tmp,p)
   except (ValueError,OSError):self.send_error(400);return
   self.reply()
   if data.get('run_id') in config.get('ignore_run_ids',[]):
    print('Previous report resent; still waiting for a new diagnostic run',flush=True)
   else:
    state['done']=True;print('Diagnostic report received; receiver closing',flush=True)
 server=HTTPServer((config['host'],config['port']),Handler);server.timeout=1;deadline=time.monotonic()+duration
 print('Return diagnostic receiver ready',flush=True)
 try:
  while not state['done'] and time.monotonic()<deadline:server.handle_request()
 finally:server.server_close()
if __name__=='__main__':
 p=argparse.ArgumentParser();p.add_argument('config');p.add_argument('--duration',type=int,default=3600);a=p.parse_args();run(a.config,a.duration)
