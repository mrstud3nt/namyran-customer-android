"""Read-only native smoke against Stage. Never sends OTP or submits a request."""
import subprocess, sys, time, pathlib, xml.etree.ElementTree as ET, re
out=pathlib.Path('smoke-evidence');out.mkdir(exist_ok=True)
pkg='ir.namyranservice.customer.stage'
def adb(*args):
    return subprocess.check_output(['adb',*args])
def dump(name):
    adb('shell','uiautomator','dump','/sdcard/customer.xml')
    data=adb('shell','cat','/sdcard/customer.xml')
    (out/(name+'.xml')).write_bytes(data)
    return ET.fromstring(data)
def screenshot(name):
    (out/(name+'.png')).write_bytes(adb('exec-out','screencap','-p'))
def click(root,label):
    node=next((n for n in root.iter('node') if n.get('text')==label or n.get('content-desc')==label),None)
    assert node is not None, f'Native element missing: {label}'
    x1,y1,x2,y2=map(int,re.findall(r'\d+',node.get('bounds')))
    adb('shell','input','tap',str((x1+x2)//2),str((y1+y2)//2));time.sleep(1)
adb('install','-r',sys.argv[1]);adb('logcat','-c')
result=adb('shell','am','start','-W','-n',pkg+'/ir.namyranservice.customer.MainActivity').decode()
assert 'Error:' not in result,result
try:
    for attempt in range(60):
        root=dump('home')
        if any(n.get('text')=='ثبت درخواست جدید' for n in root.iter('node')):break
        # Fresh emulator Wi-Fi/DNS can become ready after the app's first GET.
        # Retry only the visible read-only catalog action; never OTP or submission.
        retry=next((n for n in root.iter('node') if n.get('text')=='تلاش دوباره' and n.get('enabled')=='true'),None)
        if retry is not None and attempt%5==0:click(root,'تلاش دوباره')
        time.sleep(1)
    else:raise AssertionError('Live Stage configuration did not load; inspect captured UI')
    screenshot('home-light')
    click(root,'تغییر حالت نمایش');screenshot('home-dark')
    root=dump('home-dark');click(root,'حساب من');root=dump('login')
    assert any(n.get('text')=='شماره موبایل' for n in root.iter('node')),'Native mobile input missing'
    screenshot('login')
    adb('shell','settings','put','system','accelerometer_rotation','0')
    adb('shell','settings','put','system','user_rotation','1');time.sleep(2)
    root=dump('login-landscape');screenshot('login-landscape')
    assert any(n.get('text')=='شماره موبایل' for n in root.iter('node')),'Login lost on rotation'
    print('PASS: launch, real Stage metadata, light/dark, native login navigation, rotation')
finally:
    logs=adb('logcat','-d','-s','AndroidRuntime:E','NamyranStage:W').decode(errors='replace')
    (out/'android-runtime.txt').write_text(logs)
    screenshot('last-screen')
    assert not ('FATAL EXCEPTION' in logs and pkg in logs),'Application crashed'
