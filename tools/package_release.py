"""Verify/package 2.1; keep earlier feature-test evidence explicitly historical."""
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import zipfile

ROOT=Path(__file__).resolve().parents[1]
SDK=Path(os.environ['LOCALAPPDATA'])/'Android/Sdk'
AAPT=SDK/'build-tools/35.0.0/aapt2.exe'
EVIDENCE=ROOT/'verification/v21'

def read_log(path):
    data=path.read_bytes()
    return data.decode('utf-16' if data[:2] in (b'\xff\xfe',b'\xfe\xff') else 'utf-8-sig')

def main():
    EVIDENCE.mkdir(parents=True,exist_ok=True)
    results=json.loads((ROOT/'build/emulator-results/branding-results.json').read_text())
    log=read_log(ROOT/'branding-test-log.txt')
    assert results['version']=='2.1' and len(results['passed'])==7
    assert 'All 7 branding and language upgrade checks passed.' in log
    buildlog=read_log(ROOT/'build-log.txt')
    counts=[int(n) for n in re.findall(r'PASS: (\d+) ',buildlog)]
    assert counts==[34,30],counts
    assert buildlog.count('Verified using v2 scheme (APK Signature Scheme v2): true')==3
    assert buildlog.count('Verified using v3 scheme (APK Signature Scheme v3): true')==3
    for filename in ['build-log.txt','branding-test-log.txt']:
        (EVIDENCE/filename).write_text(read_log(ROOT/filename),encoding='utf-8')
    shutil.copy2(ROOT/'build/emulator-results/branding-results.json',EVIDENCE/'results.json')
    shutil.copy2(ROOT/'build/emulator-results/v21-branding.png',EVIDENCE/'branding.png')
    outputs=json.loads((ROOT/'dist/build-results.json').read_text(encoding='utf-8-sig'))
    assert len(outputs)==3
    checks=[]
    for item in outputs:
        apk=Path(item['file'])
        assert hashlib.sha256(apk.read_bytes()).hexdigest()==item['sha256'] and apk.stat().st_size==item['bytes']
        permissions=subprocess.check_output([str(AAPT),'dump','permissions',str(apk)],text=True)
        manifest=subprocess.check_output([str(AAPT),'dump','xmltree',str(apk),'--file','AndroidManifest.xml'],text=True)
        badging=subprocess.check_output([str(AAPT),'dump','badging',str(apk)],text=True)
        assert 'uses-permission' not in permissions and 'debuggable' not in manifest
        assert 'allowBackup(0x01010280)=false' in manifest
        assert "versionCode='3' versionName='2.1'" in badging
        assert "application-label:'Li Auto Map Relay'" in badging
        assert "minSdkVersion:'24'" in badging and "targetSdkVersion:'35'" in badging
        locales=next(line for line in badging.splitlines() if line.startswith('locales:'))
        assert "'hy'" not in locales and "'ru'" in locales,locales
        (EVIDENCE/(item['variant']+'-manifest.txt')).write_text(manifest,encoding='utf-8')
        checks.append({'variant':item['variant'],'package':item['package'],'bytes':item['bytes'],'sha256':item['sha256'],'permissions':[],'debuggable':False,'backup':False,'locales':locales})
    (EVIDENCE/'release-checks.json').write_text(json.dumps({'name':'Li Auto Map Relay','version':'2.1','java_assertions':sum(counts),'focused_emulator_checks':7,'apks':checks},indent=2),encoding='utf-8')
    report=[
        '# Li Auto Map Relay 2.1 verification','',
        'Build date: 2026-10-21. Version 2.1/code 3; min SDK 24, compile/target SDK 35. Task-local AVD MapRelay_Test: Android 16/API 36, emulator-5580.','',
        '**64 Java assertions and 7 focused emulator upgrade/branding/language checks passed on 2.1.**','',
        'All three variants passed signature v2/v3 verification, zip alignment, manifest checks and in-place installation. They retain their compatibility packages and signing key, request no Android permissions, disable backup and are not debuggable. Compiled resources contain default English and Russian; Armenian is absent.','',
        '## Focused checks','',
    ]
    report.extend(f'{i}. {name}.' for i,name in enumerate(results['passed'],1))
    report.extend(['','## Prior functional baseline','',
        'Version 2.0 previously passed 24 feature checks and 3 additional regressions for URI handoffs, routes, sharing, diagnostics, browser fallback, history, dialogs and the 1.0 upgrade. Those full emulator suites were not rerun for this narrowly scoped branding/language update. Their original, versioned evidence remains in verification/v2/ in the development workspace. The complete Java suite was rerun for 2.1.','',
        'Current results, screenshots, manifests and build logs are in verification/v21/. Source bundles contain the current evidence only.','',
        '## Limits','',
        'No physical phone was used or modified. Actual Li Auto/Yandex/Google rendering and vendor-specific phone behavior still require a device check. Test-only receiver apps verify the outgoing URI, clipboard payload and preserved settings. Coordinate inversion remains approximate, and receipt time is not a vehicle GPS fix time. The original Li Auto APK remains unchanged.'])
    (ROOT/'VERIFICATION.md').write_text('\n'.join(report)+'\n',encoding='utf-8')
    files=[ROOT/x for x in ['.gitignore','PLAN.md','README.md','VERIFICATION.md','CHANGELOG.md','build.ps1']]
    for directory in ['src','tests','tools','verification/v21']:
        files.extend(p for p in (ROOT/directory).rglob('*') if p.is_file() and '__pycache__' not in p.parts)
    archive=ROOT/'dist/MapRelay-source.zip'
    with zipfile.ZipFile(archive,'w',compression=zipfile.ZIP_DEFLATED) as output:
        for path in files: output.write(path,'MapRelay/'+path.relative_to(ROOT).as_posix())
    with zipfile.ZipFile(archive) as output:
        assert output.testzip() is None
        assert all('/signing/' not in n and not n.endswith(('.p12','.apk','password.txt','.pyc')) for n in output.namelist())
        assert not any('/values-hy/' in n for n in output.namelist())
        print('Source ZIP:',len(output.namelist()),'files,',archive.stat().st_size,'bytes')
    print('Verified 3 signed 2.1 APKs: new app label, no Armenian, no permissions, matching hashes.')
    print('Source SHA256:',hashlib.sha256(archive.read_bytes()).hexdigest())

if __name__=='__main__': main()
