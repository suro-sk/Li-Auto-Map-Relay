"""Integration checks against the isolated MapRelay_Test AVD; never a physical device."""
import json
import math
import os
from pathlib import Path
import re
import shlex
import subprocess
import time
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
ADB = Path(os.environ['LOCALAPPDATA']) / 'Android/Sdk/platform-tools/adb.exe'
SERIAL = 'emulator-5580'
OUT = ROOT / 'build/emulator-results'
OUT.mkdir(parents=True, exist_ok=True)
RESULTS = []
DELIVERIES = []


def adb(*args, check=True):
    result = subprocess.run([str(ADB), '-s', SERIAL, *args], capture_output=True, timeout=35)
    if check and result.returncode:
        raise RuntimeError(result.stderr.decode(errors='replace'))
    return result.stdout.decode('utf-8', errors='replace').strip()


def shell(*args):
    return adb('shell', ' '.join(shlex.quote(str(x)) for x in args))


def passed(name):
    RESULTS.append(name)
    print('PASS:', name, flush=True)
    (OUT / 'results.json').write_text(json.dumps({'passed': RESULTS, 'deliveries': DELIVERIES}, indent=2), encoding='utf-8')


def ui():
    shell('uiautomator', 'dump', '/sdcard/maprelay-test.xml')
    xml = adb('exec-out', 'cat', '/sdcard/maprelay-test.xml')
    (OUT / 'latest.xml').write_text(xml, encoding='utf-8')
    return ET.fromstring(xml)


def text_present(value):
    return any(value in node.get('text', '') for node in ui().iter('node'))


def tap(text):
    for node in ui().iter('node'):
        if node.get('text') == text:
            values = [int(x) for x in re.findall(r'\d+', node.get('bounds'))]
            shell('input', 'tap', (values[0]+values[2])//2, (values[1]+values[3])//2)
            return
    raise AssertionError('UI item missing: '+text)


def screenshot(name):
    result = subprocess.run([str(ADB), '-s', SERIAL, 'exec-out', 'screencap', '-p'], capture_output=True, check=True, timeout=15)
    (OUT / (name+'.png')).write_bytes(result.stdout)


def start(uri, direct=None):
    # Ensure a previous destination cannot make a delivery assertion pass.
    for pkg in ('ru.yandex.yandexmaps', 'org.li.maprelay.fixturemap', 'org.li.maprelay.fixturebrowser', 'org.li.maprelay.fixtureshare'):
        adb('shell', 'run-as', pkg, 'rm', '-f', 'files/received.txt', check=False)
    if direct:
        shell('am', 'start', '-W', '-a', 'android.intent.action.VIEW', '-n', direct+'/org.li.maprelay.RelayActivity', '-d', uri)
    else:
        shell('am', 'start', '-W', '-f', '0x10008000', '-n', 'org.li.maprelay.testcaller/org.li.maprelay.fixture.CallerActivity', '--es', 'uri', uri)


def received(pkg, expected=None):
    for _ in range(30):
        # exec-out preserves the fixture's exact UTF-8/newlines on Windows.
        command='run-as '+shlex.quote(pkg)+' cat files/received.txt 2>/dev/null'
        value = adb('exec-out', command, check=False)
        if value and (expected is None or value == expected):
            DELIVERIES.append({'package': pkg, 'uri': value})
            return value
        time.sleep(.15)
    raise AssertionError('Expected map URI was not delivered: '+str(expected)+'; got '+value)


def yandex(lon, lat):
    return 'yandexmaps://maps.yandex.ru/?pt='+str(lon)+','+str(lat)+'&z=17&l=map'


def main():
    assert SERIAL.startswith('emulator-')
    assert adb('emu', 'avd', 'name').splitlines()[0].strip() == 'MapRelay_Test', 'Wrong emulator: aborting'
    assert shell('getprop', 'sys.boot_completed') == '1'
    # Only clear apps in this named, task-owned emulator.
    for pkg in ('com.autonavi.minimap', 'com.baidu.BaiduMap', 'com.tencent.map'):
        shell('pm', 'clear', pkg)
    for fixture in ('ru.yandex.yandexmaps', 'org.li.maprelay.fixturemap'):
        adb('uninstall', fixture, check=False)
    shell('pm', 'disable-user', '--user', '0', 'com.google.android.apps.maps')
    adb('install', '-r', str(ROOT / 'build/fixtures/caller.apk'))
    amap = 'androidamap://route?sourceApplication=appName&slat=&slon=&sname=My location&dlat=40.1772&dlon=44.50349&dname=My car&dev=0&t=2'
    start(amap)
    assert text_present('No compatible map app was found')
    passed('No installed map is handled without a crash')
    proof = shell('run-as', 'org.li.maprelay.testcaller', 'cat', 'files/detected.txt')
    assert proof.count('=detected') == 3
    passed('Caller using getPackageInfo detects all three compatibility packages')
    start('androidamap://route?dlat=NaN&dlon=2')
    assert text_present('Cannot open this destination')
    passed('Malformed destination produces an error screen')
    start('baidumap://map/direction?destination=latlng:-90,-180')
    assert text_present('Cannot open this destination')
    passed('Out-of-range normalized Baidu destination is handled without a crash')

    for fixture in ('yandex', 'generic'):
        adb('install', '-r', str(ROOT / ('build/fixtures/'+fixture+'.apk')))
    start(amap)
    assert text_present('Yandex Maps (test receiver)') and text_present('Test Map')
    screenshot('chooser')
    tap('Yandex Maps (test receiver)')
    assert received('ru.yandex.yandexmaps', yandex('44.50349', '40.1772'))
    passed('Amap request shows both maps and delivers Yandex longitude,latitude')

    start('qqmap://map/routeplan?type=drive&from=&fromcoord=&to=Car&tocoord=40.1772,44.50349&policy=0&referer=appName')
    tap('Test Map')
    assert received('org.li.maprelay.fixturemap', 'geo:40.1772,44.50349?q=40.1772,44.50349%28Car%29&z=17')
    passed('Tencent request delivers standard geo URI to another map')

    # Li Auto applies its BD09 transform even outside China. Undo that offset.
    x, y = 44.50349, 40.1772
    z = math.sqrt(x*x+y*y)+.00002*math.sin(y*math.pi*3000/180)
    theta = math.atan2(y,x)+.000003*math.cos(x*math.pi*3000/180)
    blat, blon = z*math.sin(theta)+.006, z*math.cos(theta)+.0065
    start(f'baidumap://map/direction?destination=latlng:{blat},{blon}|name:Car&mode=driving&src=com.chehejia.oc.m01')
    tap('Yandex Maps (test receiver)')
    uri = received('ru.yandex.yandexmaps')
    lon, lat = map(float, re.search(r'pt=([^&]+)', uri).group(1).split(','))
    assert abs(lon-x)<.000003 and abs(lat-y)<.000003, uri
    passed('Baidu request reverses BD09 offset outside China')

    start(amap)
    tap('Remember this map')
    tap('Yandex Maps (test receiver)')
    received('ru.yandex.yandexmaps', yandex('44.50349', '40.1772'))
    start(amap.replace('40.1772', '40.18'))
    received('ru.yandex.yandexmaps', yandex('44.50349', '40.18'))
    passed('Remembered map opens automatically with the new destination')
    adb('uninstall', 'ru.yandex.yandexmaps')
    start(amap)
    assert text_present('Your saved map is no longer available') and text_present('Test Map')
    passed('Removing saved map returns to chooser and clears the default')

    start(amap.replace('40.1772', '40.19'), 'com.autonavi.minimap')
    assert text_present('40.19, 44.50349')
    tap('Test Map')
    received('org.li.maprelay.fixturemap', 'geo:40.19,44.50349?q=40.19,44.50349%28My%20car%29&z=17')
    passed('onNewIntent replaces a destination while chooser is open')

    start('androidamap://route?dlat=39.9102264981&dlon=116.4037135824&dev=0')
    tap('Correct Chinese-map coordinate offset')
    tap('Test Map')
    received('org.li.maprelay.fixturemap', 'geo:39.9102265,116.40371358?q=39.9102265,116.40371358%28Car%20location%29&z=17')
    passed('Coordinate correction can be disabled for an exact raw handoff')

    shell('am', 'start', '-W', '-a', 'android.intent.action.MAIN', '-n', 'com.autonavi.minimap/org.li.maprelay.RelayActivity')
    tap('Forget default map')
    assert text_present('Default cleared')
    screenshot('ready')
    passed('Launcher exposes settings and reset without a destination')
    print('All', len(RESULTS), 'emulator checks passed.', flush=True)


if __name__ == '__main__':
    from emulator_features_test import main as current_suite
    current_suite()
