"""Read-only native smoke against Stage. Never sends OTP or submits a request."""
import subprocess, sys, time, pathlib, xml.etree.ElementTree as ET
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
adb('install','-r',sys.argv[1]);adb('logcat','-c')
result=adb('shell','am','start','-W','-n',pkg+'/ir.namyranservice.customer.MainActivity').decode()
assert 'Error:' not in result,result
def assert_login(name):
    root=dump(name)
    labels={n.get('text') for n in root.iter('node')} | {n.get('content-desc') for n in root.iter('node')}
    assert 'نامیران سرویس' in labels,'Login header missing'
    screenshot(name)
    controls={'شماره موبایل','دریافت کد ورود'}
    if not controls <= labels:
        import re
        scroll=next(n for n in root.iter('node') if n.get('scrollable')=='true')
        x1,y1,x2,y2=map(int,re.findall(r'\d+',scroll.get('bounds')))
        x=(x1+x2)//2
        adb('shell','input','swipe',str(x),str(y2-25),str(x),str(y1+25),'400')
        labels.update(n.get('text') for n in dump(name+'-scrolled').iter('node'))
    assert controls <= labels,'Login controls missing'
    assert not {'خانه','حساب من','ثبت درخواست جدید','منوی حساب'} & labels,'Workspace visible before verification'

try:
    assert_login('fresh-login')
    adb('shell','am','force-stop',pkg)
    adb('shell','am','start','-W','-n',pkg+'/ir.namyranservice.customer.MainActivity')
    assert_login('reopened-login')
    adb('shell','settings','put','system','accelerometer_rotation','0')
    adb('shell','settings','put','system','user_rotation','1');time.sleep(2)
    assert_login('login-landscape')
    print('PASS: login gate on fresh launch, restart and rotation; no OTP requested')
finally:
    logs=adb('logcat','-d','-s','AndroidRuntime:E','NamyranStage:W').decode(errors='replace')
    (out/'android-runtime.txt').write_text(logs)
    screenshot('last-screen')
    assert not ('FATAL EXCEPTION' in logs and pkg in logs),'Application crashed'
