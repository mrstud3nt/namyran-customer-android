"""Decrypt a user-authorized Stage session with this job's ephemeral private key.
Only ciphertext is read from the approved development branch. Never print secrets.
"""
import base64,json,os,pathlib,subprocess,time,urllib.request,urllib.error
run=os.environ['GITHUB_RUN_ID'];repo=os.environ['GITHUB_REPOSITORY']
url=f'https://api.github.com/repos/{repo}/contents/audit/native-session-{run}.enc?ref=codex%2Fstage-core-integration'
for attempt in range(100):
 try:
  req=urllib.request.Request(url,headers={'Authorization':'Bearer '+os.environ['GH_TOKEN'],'Accept':'application/vnd.github+json'})
  with urllib.request.urlopen(req,timeout=15) as r:data=json.load(r)
  document=json.loads(base64.b64decode(data['content']))
  assert str(document['run_id'])==run
  encrypted=base64.b64decode(document['ciphertext']);assert len(encrypted)<=1024
  path=pathlib.Path('/tmp/native-session.enc');path.write_bytes(encrypted);path.chmod(0o600)
  session=pathlib.Path('/tmp/native-session.json')
  subprocess.run(['openssl','pkeyutl','-decrypt','-inkey','/tmp/native-acceptance.key','-in',str(path),'-out',str(session),'-pkeyopt','rsa_padding_mode:oaep','-pkeyopt','rsa_oaep_md:sha256','-pkeyopt','rsa_mgf1_md:sha256'],check=True,capture_output=True)
  session.chmod(0o600);value=json.loads(session.read_text());assert value.get('mobile') and value.get('verification_token')
  print('Authorized encrypted session received.');break
 except urllib.error.HTTPError as e:
  if e.code!=404:raise RuntimeError('Session transport unavailable') from None
 time.sleep(6)
else:raise SystemExit('No authorized encrypted session received within deadline.')
