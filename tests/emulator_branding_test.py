"""Upgrade a saved, removed locale; verify brand, remaining languages and data.
Runs only in the isolated task AVD and requires the retained 2.0 development APK.
"""
import json
import emulator_test as t
from emulator_features_test import AMAP,tap,launcher,clipboard

checks=[]
def passed(label):
    checks.append(label);print('PASS:',label,flush=True)
    (t.OUT/'branding-results.json').write_text(json.dumps({'version':'2.1','passed':checks},indent=2),encoding='utf-8')

assert t.adb('emu','avd','name').splitlines()[0].strip()=='MapRelay_Test'
old=t.ROOT/'build/baseline-v2.0/MapRelay-Amap.apk'
assert old.is_file(),'Retained version 2.0 APK is required for this migration check'
# Remove only this test package in the task-owned AVD to allow repeated upgrade runs.
t.adb('uninstall','com.autonavi.minimap',check=False)
t.adb('install',str(old))
for name in ('caller','yandex','clipboard'):
    t.adb('install','-r',str(t.ROOT/f'build/fixtures/{name}.apk'))
t.start(AMAP);tap('Settings');tap('Save recent locations');tap('Back')
tap('Remember this map');tap('Yandex Maps (test receiver)')
t.received('ru.yandex.yandexmaps','yandexmaps://maps.yandex.ru/?pt=44.50349,40.1772&z=17&l=map')
launcher();tap('Settings');tap('Language');tap('\u0540\u0561\u0575\u0565\u0580\u0565\u0576')
assert t.text_present('\u053f\u0561\u0580\u0563\u0561\u057e\u0578\u0580\u0578\u0582\u0574\u0576\u0565\u0580')

for name in ('Amap','Baidu','Tencent'):
    t.adb('install','-r',str(t.ROOT/f'dist/MapRelay-{name}.apk'))
passed('All three version 2.1 compatibility APKs install with the existing signatures')
launcher();assert t.text_present('Li Auto Map Relay')
tap('Settings');assert t.text_present('Settings')
passed('Upgrade from saved Armenian preference falls back to system English without a crash')
tap('Language')
choices=[n for n in t.ui().iter('node') if n.get('class')=='android.widget.CheckedTextView']
assert {n.get('text') for n in choices}=={'System default','English','\u0420\u0443\u0441\u0441\u043a\u0438\u0439'}
assert any(n.get('text')=='System default' and n.get('checked')=='true' for n in choices)
passed('Language chooser contains only system default, English and Russian')
tap('\u0420\u0443\u0441\u0441\u043a\u0438\u0439');assert t.text_present('\u041d\u0430\u0441\u0442\u0440\u043e\u0439\u043a\u0438')
tap('\u042f\u0437\u044b\u043a');tap('English');tap('Back')
passed('Russian and English still switch correctly after the upgrade')
tap('Recent locations');tap('My car',True);tap('Copy coordinates')
assert clipboard()=='40.1772, 44.50349'
passed('Existing saved location and its coordinates survive the update')
t.start(AMAP.replace('40.1772','40.18'))
t.received('ru.yandex.yandexmaps','yandexmaps://maps.yandex.ru/?pt=44.50349,40.18&z=17&l=map')
passed('Remembered map survives the update and receives the new destination')
launcher();tap('Recent locations');tap('My car',True);tap('Diagnostics');tap('Copy report')
assert clipboard().startswith('Li Auto Map Relay 2.1\n')
passed('Diagnostic reports use the new app name and version')
tap('Back');t.screenshot('v21-branding')
print('All',len(checks),'branding and language upgrade checks passed.',flush=True)
