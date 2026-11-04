"""Opt-in local real Stage mutation. Never print session credentials or tracking codes.

Requires a running emulator, installed app and androidTest APK, and a mode-0600
JSON session file with mobile + verification_token from an authorized real OTP.
No credentials are accepted from the repository or committed by this script.
"""
import argparse, json, os, pathlib, subprocess, re
p=argparse.ArgumentParser();p.add_argument('session_file');args=p.parse_args()
source=pathlib.Path(args.session_file)
if source.stat().st_mode & 0o077:raise SystemExit('Session file must be private (chmod 600).')
session=json.loads(source.read_text())
assert re.fullmatch(r'09[0-9]{9}',session['mobile'])
assert re.fullmatch(r'[A-Za-z0-9_-]{20,256}',session['verification_token'])
target=pathlib.Path('docs/evidence/android-native-submission.json');target.unlink(missing_ok=True)
cmd=['adb','shell','am','instrument','-w','-r','-e','class','ir.namyranservice.customer.NativeSubmissionTest','-e','authorized_phone',session['mobile'],'-e','authorized_token',session['verification_token'],'ir.namyranservice.customer.stage.test/androidx.test.runner.AndroidJUnitRunner']
log=pathlib.Path('/tmp/namyran-native-acceptance-private.log')
with log.open('w') as stream:
 os.chmod(log,0o600)
 result=subprocess.run(cmd,stdout=stream,stderr=subprocess.STDOUT,text=True,timeout=600)
output=log.read_text()
if result.returncode or 'OK (1 test)' not in output or 'FAILURES' in output:
 tags=subprocess.run(['adb','logcat','-d','-s','NamyranStage:W'],capture_output=True,text=True,timeout=10).stdout
 known=['Stage operation failed','Authorized session refused','No real receipt','Image storage unconfirmed','Request missing from account','Failed to perform scroll','Expected exactly','Cannot perform']
 evidence={'success':False,'reason':next((x for x in known if x in output),'native_assertion_or_runtime'),'test_source_lines':sorted(set(map(int,re.findall(r'NativeSubmissionTest.kt:(\d+)',output)))),'operation_error_classes':re.findall(r'operation_failed:([A-Za-z0-9_:]+)',tags)}
 target.write_text(json.dumps(evidence,indent=2)+'\n')
 print('Sanitized native failure:',json.dumps(evidence))
 raise SystemExit('Native acceptance failed; private details were not published.')
evidence=subprocess.check_output(['adb','exec-out','run-as','ir.namyranservice.customer.stage','cat','files/native-submission-evidence.json'])
value=json.loads(evidence)
assert value.get('success') and value.get('image_count')==1 and value.get('account_readback')
target=pathlib.Path('docs/evidence/android-native-submission.json');target.write_text(json.dumps(value,indent=2)+'\n')
print('PASS: real native request with image, Core receipt and account readback. Sanitized evidence saved.')
