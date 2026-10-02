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
    result = adb('shell', 'uiautomator', 'dump', '/sdcard/gratify-audit-ui.xml').decode(errors='replace')
    if 'UI hierchary dumped to:' not in result and 'UI hierarchy dumped to:' not in result:
        raise RuntimeError('Fresh native UI hierarchy unavailable; cached nodes must not be used')
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
        accounts = json.loads(PRIVATE.read_text())
        for account in accounts if isinstance(accounts, list) else []:
            for key in ('email','password','access_token'):
                if account.get(key): text=text.replace(account[key],'[test account]')
    return text

parser=argparse.ArgumentParser()
parser.add_argument('action',choices=['snapshot','tap','fill','login','screenshot','runtime'])
parser.add_argument('value',nargs='?')
parser.add_argument('--serial',choices=['emulator-5580','emulator-5582'],default='emulator-5580')
parser.add_argument('--account',type=int,choices=[0,1],default=0)
parser.add_argument('--field',type=int,default=0)
parser.add_argument('--package', choices=['com.tan.gratify', 'com.tan.gratify.dev'], default='com.tan.gratify')
args=parser.parse_args()
SERIAL=args.serial
if args.action=='snapshot':
    for node in tree().iter('node'):
        text=node.get('text') or node.get('content-desc')
        if text and node.get('password')!='true':print(redact(text)[:150],node.get('bounds'))
elif args.action=='tap': tap_text(args.value)
elif args.action=='fill':
    fields=[n for n in tree().iter('node') if n.get('class')=='android.widget.EditText']
    node=fields[args.field];tap(node)
    adb('shell','input','keyevent','123')
    if node.get('text'):adb('shell','input','keyevent',*['67']*len(node.get('text')))
    adb('shell','input','text',args.value)
    adb('shell','input','keyevent','4')
elif args.action=='login':
    account=json.loads(PRIVATE.read_text())[args.account]
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
    pid=adb('shell','pidof',args.package).decode().strip()
    print(json.dumps({'package':args.package,'process_running':bool(pid),'crash_buffer_empty':not crash.strip(),'page_size':adb('shell','getconf','PAGE_SIZE').decode().strip()}))
