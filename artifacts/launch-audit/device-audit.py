"""Operate only the owned Gratify audit emulator; redact temporary credentials."""
from pathlib import Path
import argparse, json, re, subprocess, xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
ADB = Path.home() / 'AppData/Local/Android/Sdk/platform-tools/adb.exe'
SERIAL = 'emulator-5580'
PRIVATE = ROOT / '.local-release-access/test-accounts.json'

def adb(*args):
    result = subprocess.run([str(ADB), '-s', SERIAL, *args], capture_output=True, timeout=40)
    if result.returncode:
        raise RuntimeError('Owned emulator operation failed')
    return result.stdout

def tree():
    adb('shell', 'uiautomator', 'dump', '/sdcard/gratify-audit-ui.xml')
    return ET.fromstring(adb('shell', 'cat', '/sdcard/gratify-audit-ui.xml'))

def tap(node):
    x1,y1,x2,y2 = map(int,re.findall(r'\d+',node.get('bounds')))
    adb('shell','input','tap',str((x1+x2)//2),str((y1+y2)//2))

def tap_text(text):
    matches=[n for n in tree().iter('node') if n.get('text')==text or n.get('content-desc')==text]
    if not matches: raise RuntimeError('Requested control not visible')
    tap(matches[0])

def redact(text):
    if PRIVATE.is_file():
        for account in json.loads(PRIVATE.read_text()):
            for key in ('email','password','access_token'):
                if account.get(key): text=text.replace(account[key],'[test account]')
    return text

parser=argparse.ArgumentParser()
parser.add_argument('action',choices=['snapshot','tap','login','screenshot','runtime'])
parser.add_argument('value',nargs='?')
args=parser.parse_args()
if args.action=='snapshot':
    for node in tree().iter('node'):
        text=node.get('text') or node.get('content-desc')
        if text and node.get('password')!='true':print(redact(text)[:150],node.get('bounds'))
elif args.action=='tap': tap_text(args.value)
elif args.action=='login':
    account=json.loads(PRIVATE.read_text())[0]
    for index,key in enumerate(('email','password')):
        fields=[n for n in tree().iter('node') if n.get('class')=='android.widget.EditText']
        node=fields[index];tap(node)
        adb('shell','input','keyevent','123')
        if node.get('text'):adb('shell','input','keyevent',*['67']*len(node.get('text')))
        adb('shell','input','text',account[key])
        adb('shell','input','keyevent','4')
    tap_text('Log In')
    print('Temporary account login submitted privately.')
elif args.action=='screenshot':
    destination=ROOT/'artifacts/launch-audit'/args.value
    assert destination.suffix=='.png' and destination.parent==(ROOT/'artifacts/launch-audit')
    destination.write_bytes(adb('exec-out','screencap','-p'))
    print(destination)
else:
    crash=adb('logcat','-d','-b','crash').decode(errors='replace')
    pid=adb('shell','pidof','com.tan.gratify').decode().strip()
    print(json.dumps({'process_running':bool(pid),'crash_buffer_empty':not crash.strip(),'page_size':adb('shell','getconf','PAGE_SIZE').decode().strip()}))
