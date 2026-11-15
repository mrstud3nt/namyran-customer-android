"""Clone2 UI only. No credentials entered and no OTP or business submit clicked."""
import pathlib,subprocess,time,re,xml.etree.ElementTree as ET,sys
pkg="ir.namyranservice.customer.stage.clone2"
out=pathlib.Path("smoke-evidence");out.mkdir(exist_ok=True)
def adb(*args):return subprocess.check_output(["adb",*args])
def tree():
    adb("shell","uiautomator","dump","/sdcard/clone2-host.xml")
    return ET.fromstring(adb("shell","cat","/sdcard/clone2-host.xml"))
adb("install","-r",sys.argv[1]);adb("shell","am","start","-W","-n",pkg+"/ir.namyranservice.customer.MainActivity");time.sleep(2)
for attempt in range(8):
    root=tree();node=next((n for n in root.iter("node") if n.get("text")=="دسترسی محیط آزمایشی"),None)
    if node is not None:break
    scroll=next((n for n in root.iter("node") if n.get("scrollable")=="true"),None)
    assert scroll is not None,"Missing Clone2 access control"
    x1,y1,x2,y2=map(int,re.findall(r"\d+",scroll.get("bounds")));x=(x1+x2)//2
    adb("shell","input","swipe",str(x),str(y1+(y2-y1)*3//4),str(x),str(y1+(y2-y1)//4),"400")
assert node is not None,"Missing Clone2 access button"
x1,y1,x2,y2=map(int,re.findall(r"\d+",node.get("bounds")));adb("shell","input","tap",str((x1+x2)//2),str((y1+y2)//2))
root=tree();labels={n.get("text") for n in root.iter("node")}
assert {"نام کاربری محافظ","رمز محافظ محیط","ثبت دسترسی"} <= labels,"Host credential dialog missing controls"
(out/"clone2-host-access.png").write_bytes(adb("exec-out","screencap","-p"))
adb("shell","input","keyevent","KEYCODE_BACK");adb("shell","am","force-stop",pkg)
print("PASS: Clone2 access dialog opens; no credentials entered and no OTP requested")
