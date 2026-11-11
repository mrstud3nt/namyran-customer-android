"""Read-only native smoke against Stage. Never sends OTP or submits a request."""
import subprocess, sys, time, pathlib, re, xml.etree.ElementTree as ET
out=pathlib.Path('smoke-evidence');out.mkdir(exist_ok=True)
pkg='ir.namyranservice.customer.stage'
def adb(*args):
    return subprocess.check_output(['adb',*args])
def dump(name):
    adb('shell','rm','-f','/sdcard/customer.xml')
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
    for attempt in range(4):
        root=dump(name)
        labels={n.get('text') for n in root.iter('node')} | {n.get('content-desc') for n in root.iter('node')}
        if 'نامیران سرویس' in labels:
            break
        time.sleep(1)
    assert 'نامیران سرویس' in labels, f'Login header missing on {name}'
    screenshot(name)
    controls={'شماره موبایل','دریافت کد ورود'}
    if not controls <= labels:
        scroll=next(n for n in root.iter('node') if n.get('scrollable')=='true')
        x1,y1,x2,y2=map(int,re.findall(r'\d+',scroll.get('bounds')))
        x=(x1+x2)//2
        adb('shell','input','swipe',str(x),str(y2-25),str(x),str(y1+25),'400')
        labels.update(n.get('text') for n in dump(name+'-scrolled').iter('node'))
    assert controls <= labels,'Login controls missing'
    assert not {'خانه','حساب من','ثبت درخواست جدید','منوی حساب'} & labels,'Workspace visible before verification'

def tap(node):
    x1,y1,x2,y2=map(int,re.findall(r'\d+',node.get('bounds')))
    adb('shell','input','tap',str((x1+x2)//2),str((y1+y2)//2))

def enter_field(name,value):
    root=dump(name)
    field=next(n for n in root.iter('node') if n.get('class')=='android.widget.EditText')
    tap(field)
    adb('shell','input','text',value)
    adb('shell','input','keyevent','KEYCODE_BACK')

def tap_label(name,label):
    for attempt in range(7):
        root=dump(name+'-'+str(attempt))
        node=next((n for n in root.iter('node') if n.get('text')==label),None)
        if node is not None:
            tap(node)
            return
        scroll=next((n for n in root.iter('node') if n.get('scrollable')=='true'),None)
        assert scroll is not None, 'Missing control '+label
        x1,y1,x2,y2=map(int,re.findall(r'\d+',scroll.get('bounds')))
        x=(x1+x2)//2
        if attempt==0:
            adb('shell','input','swipe',str(x),str(y1+20),str(x),str(y2-20),'350')
        else:
            adb('shell','input','swipe',str(x),str(y2-20),str(x),str(y1+20),'350')
    raise AssertionError('Missing control '+label)

try:
    assert_login('fresh-login')
    adb('shell','am','force-stop',pkg)
    adb('shell','am','start','-W','-n',pkg+'/ir.namyranservice.customer.MainActivity')
    assert_login('reopened-login')
    adb('shell','settings','put','system','accelerometer_rotation','0')
    adb('shell','settings','put','system','user_rotation','1');time.sleep(2)
    assert_login('login-landscape')
    adb('shell','settings','put','system','user_rotation','0');time.sleep(2)
    tap_label('password-method', 'نام کاربری و رمز')
    screenshot('password-screen')
    root=dump('password-controls')
    labels={n.get('text') for n in root.iter('node')}
    assert {'نام کاربری','رمز عبور','فراموشی رمز عبور؟'} <= labels, 'Password form controls missing'
    tap_label('reset-action','فراموشی رمز عبور؟')
    screenshot('recovery-screen')
    tap_label('registration-action','ثبت‌نام')
    screenshot('registration-screen')
    tap_label('back-to-sms','ورود با پیامک')
    enter_field('invalid-number','123')
    tap_label('invalid-send','دریافت کد ورود')
    root=dump('invalid-number-state')
    assert any('شماره موبایل نامعتبر' in (n.get('text') or '') for n in root.iter('node')), 'Invalid mobile state missing'
    screenshot('invalid-number')
    adb('shell','am','force-stop',pkg)
    adb('shell','am','start','-W','-n',pkg+'/ir.namyranservice.customer.MainActivity')
    assert_login('after-invalid-restart')
    enter_field('demo-number' ,'09022896363')
    tap_label('demo-send','دریافت کد ورود')
    screenshot('otp-screen')
    enter_field('demo-wrong-code','0000')
    tap_label('demo-wrong-submit','تأیید و ورود')
    root=dump('otp-wrong')
    assert any('کد آزمایشی صحیح نیست' in (n.get('text') or '') for n in root.iter('node')), 'Wrong OTP state missing'
    screenshot('otp-wrong')
    root=dump('clear-code')
    field=next(n for n in root.iter('node') if n.get('class')=='android.widget.EditText')
    tap(field)
    adb('shell','input','keyevent','KEYCODE_MOVE_END')
    for _ in range(4): adb('shell','input','keyevent','KEYCODE_DEL')
    adb('shell','input','text','1111')
    adb('shell','input','keyevent','KEYCODE_BACK')
    tap_label('demo-submit','تأیید و ورود')
    assert any(n.get('text')=='خانه آزمایشی' for n in dump('demo-home').iter('node'))
    screenshot('demo-home')
    print('PASS: login gate and isolated demo entry; no OTP requested')
finally:
    logs=adb('logcat','-d','-s','AndroidRuntime:E','NamyranStage:W').decode(errors='replace')
    (out/'android-runtime.txt').write_text(logs)
    screenshot('last-screen')
    assert not ('FATAL EXCEPTION' in logs and pkg in logs),'Application crashed'
