"""Regression: a new destination must invalidate any old modal browser choice."""
import emulator_test as t
from emulator_features_test import AMAP,tap

assert t.adb('emu','avd','name').splitlines()[0].strip()=='MapRelay_Test'
t.adb('install','-r',str(t.ROOT/'build/fixtures/browser.apk'))
t.shell('pm','clear','com.autonavi.minimap')
t.start(AMAP)
tap('Open in browser')
assert t.text_present('Choose a browser')
t.start(AMAP.replace('40.1772','40.25'),'com.autonavi.minimap')
assert not t.text_present('Choose a browser'),'Old modal chooser survived new destination'
tap('Open in browser');tap('Test Browser')
t.received('org.li.maprelay.fixturebrowser','https://yandex.com/maps/?pt=44.50349,40.25&z=17&l=map')
print('PASS: New intent dismisses old browser dialog and sends the latest destination',flush=True)
