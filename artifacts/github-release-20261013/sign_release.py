"""Sign local release artifacts with the existing key; never publish or print secrets."""
from pathlib import Path
import hashlib, json, os, re, struct, subprocess, sys, zipfile

ROOT=Path(__file__).resolve().parents[2]
OUT=Path(__file__).resolve().parent
BT=Path('C:/Users/LENOVO/AppData/Local/Android/Sdk/build-tools/37.0.0')
JAVA=Path('D:/Tan/APK/JDK/bin/java.exe')
properties={line.split('=',1)[0].strip():line.split('=',1)[1].strip()
    for line in (ROOT/'local.properties').read_text(encoding='utf-8-sig').splitlines()
    if '=' in line and not line.lstrip().startswith('#')}
keystore=Path(properties['KEYSTORE_PATH'])
secret_file=next(ROOT.glob('*Keystore*.txt'))
entries={k.strip():v.strip() for k,v in (line.split(':',1) for line in secret_file.read_text(encoding='utf-8-sig').splitlines() if ':' in line)}
env=dict(os.environ,GRATIFY_STORE_PASSWORD=entries['Keystore Password'],GRATIFY_KEY_PASSWORD=entries['Key Password'])
expected_certificate='efefe1b8bf7c6502aeeb60db9a45b430dc50040e11c6f76eb22e2308a51c316a'

def run(args,timeout=180):
    p=subprocess.run(list(map(str,args)),capture_output=True,text=True,errors='replace',env=env,timeout=timeout,creationflags=0x08000000)
    if p.returncode:
        safe=p.stdout+p.stderr
        for value in entries.values(): safe=safe.replace(value,'[redacted]')
        raise RuntimeError(safe)
    return p.stdout+p.stderr

def preflight():
    p=subprocess.run([str(JAVA.parent/'keytool.exe'),'-exportcert','-keystore',str(keystore),'-alias',entries['Key Alias'],
        '-storepass:env','GRATIFY_STORE_PASSWORD'],capture_output=True,env=env,timeout=60,creationflags=0x08000000)
    if p.returncode: raise RuntimeError('Existing release key could not be opened. No APK signed.')
    certificate=hashlib.sha256(p.stdout).hexdigest()
    if certificate!=expected_certificate: raise RuntimeError('Release certificate differs from the existing Gratify successor. No APK signed.')
    payload={'existing_successor_certificate_matches':True,'certificate_sha256':certificate}
    (OUT/'signing-preflight.json').write_text(json.dumps(payload,indent=2),encoding='utf-8')
    print(json.dumps(payload))

def sign():
    preflight();records=[]
    for abi in ('arm64-v8a','universal'):
        unsigned=ROOT/f'androidApp/build/outputs/apk/release/androidApp-{abi}-release-unsigned.apk'
        aligned=OUT/f'aligned-{abi}.apk'
        target=OUT/f'Gratify-2.1.5-release-{abi}.apk'
        run([BT/'zipalign.exe','-f','-P','16','4',unsigned,aligned])
        run([JAVA,'-jar',BT/'lib/apksigner.jar','sign','--ks',keystore,'--ks-key-alias',entries['Key Alias'],
            '--ks-pass','env:GRATIFY_STORE_PASSWORD','--key-pass','env:GRATIFY_KEY_PASSWORD','--out',target,aligned])
        certificate_output=run([JAVA,'-jar',BT/'lib/apksigner.jar','verify','--verbose','--print-certs',target])
        certificate=re.search(r'certificate SHA-256 digest:\s*([a-fA-F0-9]{64})',certificate_output)[1].lower()
        assert certificate==expected_certificate
        (OUT/f'{target.stem}-signature.txt').write_text(certificate_output,encoding='utf-8')
        badging=run([BT/'aapt.exe','dump','badging',target])
        assert "package: name='com.tan.gratify.next' versionCode='66' versionName='2.1.5'" in badging
        assert "application-label:'Gratify'" in badging and 'application-debuggable' not in badging
        (OUT/f'{target.stem}-badging.txt').write_text(badging,encoding='utf-8')
        run([BT/'zipalign.exe','-c','-P','16','4',target])
        with zipfile.ZipFile(target) as signed,zipfile.ZipFile(unsigned) as source:
            paths={p for p in signed.namelist() if not p.startswith('META-INF/')}
            assert paths=={p for p in source.namelist() if not p.startswith('META-INF/')}
            assert all(signed.read(p)==source.read(p) for p in paths)
            native=[]
            for name in signed.namelist():
                if not name.startswith(('lib/arm64-v8a/','lib/x86_64/')) or not name.endswith('.so'):continue
                data=signed.read(name);assert data[:4]==b'\x7fELF' and data[4]==2
                endian='<' if data[5]==1 else '>';offset=struct.unpack_from(endian+'Q',data,32)[0]
                size,count=struct.unpack_from(endian+'HH',data,54)
                for i in range(count):
                    base=offset+i*size
                    if struct.unpack_from(endian+'I',data,base)[0]!=1:continue
                    file_offset,vaddr=struct.unpack_from(endian+'QQ',data,base+8)
                    align=struct.unpack_from(endian+'Q',data,base+48)[0]
                    assert align>=16384 and file_offset%16384==vaddr%16384,(name,align)
                native.append(name)
        records.append({'path':str(target),'bytes':target.stat().st_size,'sha256':hashlib.sha256(target.read_bytes()).hexdigest(),
            'certificate_sha256':certificate,'release_signature_verified':True,'debuggable':False,'zipalign_16k':True,
            'native_64bit_alignment_checked':native,'matches_final_unsigned_payload':True,'experimental_playlist_features_enabled':False})
    (OUT/'release-apks.json').write_text(json.dumps(records,indent=2),encoding='utf-8')
    print(json.dumps([{'apk':Path(r['path']).name,'bytes':r['bytes'],'signature_verified':True,'16k_alignment':True} for r in records],indent=2))

if __name__=='__main__':
    if len(sys.argv)>1 and sys.argv[1]=='preflight':preflight()
    else:sign()
