"""Update v1 in place and prove migration of the old raw/default preferences.
Requires the retained development-only build/baseline-v1/MapRelay-Tencent.apk.
"""
import emulator_test as t
from emulator_features_test import tap,launcher

assert t.adb('emu','avd','name').splitlines()[0].strip()=='MapRelay_Test'
old=t.ROOT/'build/baseline-v1/MapRelay-Tencent.apk'
assert old.is_file(),'The original development build is required'
t.adb('uninstall','com.tencent.map')
t.adb('install',str(old))
t.adb('install','-r',str(t.ROOT/'build/fixtures/yandex.apk'))
launcher('com.tencent.map');tap('Correct Chinese-map coordinate offset')
uri='qqmap://map/routeplan?to=Upgrade%20car&tocoord=39.9102264981,116.4037135824'
t.start(uri);tap('Remember this map');tap('Yandex Maps (test receiver)')
t.received('ru.yandex.yandexmaps','yandexmaps://maps.yandex.ru/?pt=116.40371358,39.9102265&z=17&l=map')
t.adb('install','-r',str(t.ROOT/'dist/MapRelay-Tencent.apk'))
t.start(uri.replace('39.9102264981','39.92'))
t.received('ru.yandex.yandexmaps','yandexmaps://maps.yandex.ru/?pt=116.40371358,39.92&z=17&l=map')
launcher('com.tencent.map');tap('Settings')
assert t.text_present('Coordinate format: WGS84 (no correction)')
tap('Back');tap('Recent locations');assert t.text_present('History is off')
print('PASS: v1 updates in place with same signature, raw format/default retained and history off',flush=True)
