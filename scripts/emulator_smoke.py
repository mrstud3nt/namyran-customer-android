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
    time.sleep(0.6)
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
    controls={'شماره موبایل','ورود با پیامک'}
    for scroll_attempt in range(6):
        if controls <= labels:
            break
        scroll=next(n for n in root.iter('node') if n.get('scrollable')=='true')
        x1,y1,x2,y2=map(int,re.findall(r'\d+',scroll.get('bounds')))
        x=x1+max(12,(x2-x1)//20)
        adb('shell','input','swipe',str(x),str(y1+(y2-y1)*3//4),str(x),str(y1+(y2-y1)//4),'400')
        time.sleep(0.5)
        root=dump(name+'-scrolled-'+str(scroll_attempt))
        labels.update(n.get('text') for n in root.iter('node'))
    assert controls <= labels,'Login controls missing after scrolling'
    screenshot(name+'-controls')
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
            adb('shell','input','swipe',str(x),str(y1+(y2-y1)//4),str(x),str(y1+(y2-y1)*3//4),'350')
        else:
            adb('shell','input','swipe',str(x),str(y1+(y2-y1)*3//4),str(x),str(y1+(y2-y1)//4),'350')
    raise AssertionError('Missing control '+label)

try:
    adb('shell','settings','put','secure','show_ime_with_hard_keyboard','1')
    adb('shell','am','force-stop',pkg)
    recording=subprocess.Popen(['adb','shell','screenrecord','--time-limit','4','/sdcard/namyran-startup.mp4'])
    time.sleep(0.5)
    adb('shell','am','start','-n',pkg+'/ir.namyranservice.customer.MainActivity')
    recording.wait(timeout=12)
    adb('pull','/sdcard/namyran-startup.mp4',str(out/'startup.mp4'))
    assert_login('fresh-login')
    # Reproduce the user's real failure with the IME visible, not dismissed before capture.
    root=dump('phone-focus')
    field=next(n for n in root.iter('node') if n.get('class')=='android.widget.EditText')
    tap(field);time.sleep(1)
    adb('shell','input','text','09123456789')
    root=dump('keyboard-open')
    assert any(n.get('text')=='09123456789' for n in root.iter('node')), 'Phone lost on IME open'
    (out/'keyboard-ime-state.txt').write_bytes(adb('shell','dumpsys','input_method'))
    screenshot('keyboard-open')
    # Switch login language while editing: the in-memory form must survive.
    node=next(n for n in root.iter('node') if n.get('content-desc')=='Change login language')
    tap(node);root=dump('english-keyboard')
    assert any(n.get('text')=='09123456789' for n in root.iter('node')), 'Phone lost on language change'
    screenshot('english-keyboard')
    tap(next(n for n in root.iter('node') if n.get('content-desc')=='Change login language'))
    tap_label('keyboard-submit-visible','ورود با نام کاربری و رمز عبور')
    root=dump('password-before-focus')
    tap(next(n for n in root.iter('node') if n.get('class')=='android.widget.EditText'))
    adb('shell','input','text','ui_layout_check')
    root=dump('keyboard-password-form')
    assert any(n.get('text')=='ui_layout_check' for n in root.iter('node')), 'Username lost with keyboard'
    assert any(n.get('text')=='نام کاربری' for n in root.iter('node')), 'Username input missing with keyboard'
    screenshot('keyboard-password-form')
    adb('shell','input','keyevent','KEYCODE_BACK')
    adb('shell','am','force-stop',pkg)
    adb('shell','am','start','-W','-n',pkg+'/ir.namyranservice.customer.MainActivity')
    adb('shell','wm','size','720x1280')
    adb('shell','wm','density','320');time.sleep(2)
    assert_login('small-login')
    root=dump('small-phone-focus')
    field=next(n for n in root.iter('node') if n.get('class')=='android.widget.EditText')
    tap(field);time.sleep(1)
    adb('shell','input','text','0912')
    screenshot('small-keyboard-open')
    adb('shell','input','keyevent','KEYCODE_BACK')
    root=dump('small-keyboard-closed')
    assert any(n.get('text')=='0912' for n in root.iter('node')), 'Phone lost on IME close'
    adb('shell','wm','size','reset');adb('shell','wm','density','reset')
    adb('shell','am','force-stop',pkg)
    adb('shell','am','start','-W','-n',pkg+'/ir.namyranservice.customer.MainActivity')
    adb('shell','am','force-stop',pkg)
    adb('shell','am','start','-W','-n',pkg+'/ir.namyranservice.customer.MainActivity')
    assert_login('reopened-login')
    adb('shell','settings','put','system','accelerometer_rotation','0')
    adb('shell','settings','put','system','user_rotation','1');time.sleep(2)
    assert_login('login-landscape')
    adb('shell','settings','put','system','user_rotation','0');time.sleep(2)
    tap_label('password-method', 'ورود با نام کاربری و رمز عبور')
    root=dump('password-controls')
    screenshot('password-screen')
    labels={n.get('text') for n in root.iter('node')}
    assert {'نام کاربری','رمز عبور','فراموشی رمز عبور؟'} <= labels, 'Password form controls missing'
    tap_label('reset-action','فراموشی رمز عبور؟')
    screenshot('recovery-screen')
    tap_label('registration-action','ثبت‌نام')
    screenshot('registration-screen')
    adb('shell','input','keyevent','KEYCODE_BACK')
    enter_field('invalid-number','123')
    tap_label('invalid-send','ورود با پیامک')
    root=dump('invalid-number-state')
    assert any('شماره موبایل نامعتبر' in (n.get('text') or '') for n in root.iter('node')), 'Invalid mobile state missing'
    screenshot('invalid-number')
    adb('shell','am','force-stop',pkg)
    adb('shell','am','start','-W','-n',pkg+'/ir.namyranservice.customer.MainActivity')
    assert_login('after-invalid-restart')
    print('PASS: native login, visible keyboard, compact screen, rotation and errors; no OTP requested')
finally:
    logs=adb('logcat','-d','-s','AndroidRuntime:E','NamyranStage:W').decode(errors='replace')
    (out/'android-runtime.txt').write_text(logs)
    screenshot('last-screen')
    assert not ('FATAL EXCEPTION' in logs and pkg in logs),'Application crashed'
