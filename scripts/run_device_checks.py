"""Install both APKs, run independent image checks, then public native smoke."""
import pathlib, subprocess, sys
root=pathlib.Path(sys.argv[1]);out=pathlib.Path('smoke-evidence');out.mkdir(parents=True,exist_ok=True)
for apk in (root/'debug/app-debug.apk',root/'androidTest/debug/app-debug-androidTest.apk'):
 assert apk.is_file(),f'APK missing: {apk}'
 subprocess.run(['adb','install','-r',str(apk)],check=True)
r=subprocess.run(['adb','shell','am','instrument','-w','-r','-e','class','ir.namyranservice.customer.AttachmentStoreTest','ir.namyranservice.customer.stage.test/androidx.test.runner.AndroidJUnitRunner'],capture_output=True,text=True,timeout=180)
log=r.stdout+'\n'+r.stderr;(out/'image-tests.txt').write_text(log);print(log)
assert r.returncode==0 and 'OK (2 tests)' in log and 'FAILURES' not in log,'Image instrumentation failed'
subprocess.run([sys.executable,'scripts/emulator_smoke.py',str(root/'debug/app-debug.apk')],check=True)
