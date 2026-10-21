"""V2 UI tests. Literal outgoing values are independent protocol expectations."""
import emulator_test as t
import time
import re

AMAP='androidamap://route?sourceApplication=appName&slat=&slon=&dlat=40.1772&dlon=44.50349&dname=My%20car&dev=0&t=2'

def tap(value,prefix=False):
    for attempt in range(7):
        for node in t.ui().iter('node'):
            label=node.get('text','')
            if (label.startswith(value) if prefix else label==value):
                box=list(map(int,re.findall(r'\d+',node.get('bounds'))))
                if len(box)==4 and box[3]>box[1] and box[1]<1850:
                    t.shell('input','tap',(box[0]+box[2])//2,(box[1]+box[3])//2)
                    return
        below={'Settings','Recent locations','Diagnostics','Copy coordinates','Share location'}
        if attempt==0 and value not in below: t.shell('input','swipe',500,450,500,1650,250)
        else: t.shell('input','swipe',500,1550,500,450,250)
    raise AssertionError('UI item not found: '+value)

def clipboard():
    pkg='org.li.maprelay.fixtureclipboard'
    t.adb('shell','run-as',pkg,'rm','-f','files/received.txt',check=False)
    t.shell('am','start','-W','-f','0x10008000','-n',pkg+'/org.li.maprelay.fixture.ClipboardActivity')
    return t.received(pkg)

def launcher(pkg='com.autonavi.minimap'):
    t.shell('am','start','-W','-a','android.intent.action.MAIN','-n',pkg+'/org.li.maprelay.RelayActivity')

def set_format(label):
    tap('Settings');tap('Coordinate format:',True);tap(label);tap('Back')

def choose_share_receiver():
    tree=t.ui()  # Wait for the system sharesheet, including its opening animation.
    header=next((n for n in tree.iter('node') if n.get('text')=='Sharing text'),None)
    assert header is not None,'Android sharesheet did not open'
    bounds=list(map(int,re.findall(r'\d+',header.get('bounds'))))
    t.shell('input','swipe',500,max(160,bounds[1]-25),500,100,450)
    for _ in range(5):
        for node in t.ui().iter('node'):
            if node.get('text')=='Test Share Receiver':
                box=list(map(int,re.findall(r'\d+',node.get('bounds'))))
                t.shell('input','tap',(box[0]+box[2])//2,(box[1]+box[3])//2)
                return
        t.shell('input','swipe',500,1800,500,800,450)
    raise AssertionError('Share fixture not visible in expanded system sharesheet')

def main():
    assert t.adb('emu','avd','name').splitlines()[0].strip()=='MapRelay_Test'
    for pkg in ('com.autonavi.minimap','com.baidu.BaiduMap','com.tencent.map'):
        t.shell('pm','clear',pkg)
    for name in ('yandex','generic','browser','share','clipboard','caller'):
        t.adb('install','-r',str(t.ROOT/f'build/fixtures/{name}.apk'))
    t.start(AMAP)
    labels=[n.get('text') for n in t.ui().iter('node')]
    assert all(value in labels for value in ('Show pin','Walking','Driving'))
    t.passed('Destination offers pin, walking and driving modes')
    t.screenshot('v2-chooser')
    tap('Walking');tap('Yandex Maps (test receiver)')
    t.received('ru.yandex.yandexmaps','yandexmaps://maps.yandex.ru/?rtext=~40.1772,44.50349&rtt=pd')
    t.passed('Walking route delivers pd with latitude then longitude')
    t.start(AMAP);tap('Driving');tap('Yandex Maps (test receiver)')
    t.received('ru.yandex.yandexmaps','yandexmaps://maps.yandex.ru/?rtext=~40.1772,44.50349&rtt=auto')
    t.passed('Driving route delivers auto mode')
    t.start(AMAP);tap('Show pin');tap('Yandex Maps (test receiver)')
    t.received('ru.yandex.yandexmaps','yandexmaps://maps.yandex.ru/?pt=44.50349,40.1772&z=17&l=map')
    t.passed('Pin mode retains longitude then latitude')
    t.start('qqmap://map/routeplan?to=Car&tocoord=40.1772,44.50349');tap('Test Map')
    t.received('org.li.maprelay.fixturemap','geo:40.1772,44.50349?q=40.1772,44.50349%28Car%29&z=17')
    t.passed('Tencent handoff still reaches a generic geo map')
    t.start('baidumap://map/direction?destination=latlng:39.9165658186,116.4100864544|name:Car&mode=driving')
    tap('Copy coordinates');coords=clipboard();lat,lon=map(float,coords.split(','))
    assert abs(lat-39.908823)<.000003 and abs(lon-116.39747)<.000003,coords
    t.passed('Baidu conversion and coordinate clipboard output are correct')

    t.start(AMAP);tap('Copy coordinates');assert clipboard()=='40.1772, 44.50349'
    t.passed('Copy coordinates writes the destination to Android clipboard')
    tap('Share location');choose_share_receiver()
    text=t.received('org.li.maprelay.fixtureshare')
    assert text=='My car\n40.1772, 44.50349\nhttps://yandex.com/maps/?pt=44.50349,40.1772&z=17&l=map',text
    t.passed('Android share sheet delivers a labelled HTTPS location link')
    t.start(AMAP);tap('Diagnostics');tap('Copy report');report=clipboard()
    assert AMAP in report and 'Original coordinates (lat, lon): 40.1772, 44.50349' in report
    assert 'yandexmaps://maps.yandex.ru/?pt=44.50349,40.1772' in report
    assert 'not when the vehicle obtained' in report
    t.screenshot('v2-diagnostics')
    t.passed('Diagnostic clipboard report contains raw request, coordinates, outgoing links and receipt-time distinction')
    tap('Back');tap('Open in browser');tap('Test Browser')
    t.received('org.li.maprelay.fixturebrowser','https://yandex.com/maps/?pt=44.50349,40.1772&z=17&l=map')
    t.passed('Browser fallback explicitly targets a browser and sends HTTPS pin')
    t.start(AMAP);tap('Walking');tap('Open in browser');tap('Test Browser')
    t.received('org.li.maprelay.fixturebrowser','https://yandex.com/maps/?rtext=~40.1772,44.50349&rtt=pd')
    t.passed('Browser fallback preserves requested walking mode')

    china='androidamap://route?dlat=39.9102264981&dlon=116.4037135824&dev=1'
    t.start(china);set_format('GCJ02');tap('Copy coordinates');lat,lon=map(float,clipboard().split(','))
    assert abs(lat-39.908823)<.000003 and abs(lon-116.39747)<.000003
    t.passed('GCJ02 override corrects a link declared WGS84')
    set_format('WGS84 (no correction)');tap('Copy coordinates');assert clipboard()=='39.9102265, 116.40371358'
    t.passed('WGS84 override passes raw coordinates through')
    t.start('androidamap://route?dlat=39.9165658186&dlon=116.4100864544')
    set_format('BD09');tap('Copy coordinates');lat,lon=map(float,clipboard().split(','))
    assert abs(lat-39.908823)<.000003 and abs(lon-116.39747)<.000003
    t.passed('BD09 selector removes both Baidu and GCJ offsets')
    set_format('Automatic (from link)')

    t.start(AMAP);tap('Recent locations');assert t.text_present('History is off')
    t.passed('History is off by default and prior destinations are not listed')
    tap('Settings');tap('Save recent locations');tap('Back')
    t.start(AMAP.replace('40.1772','40.18').replace('My%20car','Second%20car'))
    launcher();tap('Recent locations');tap('My car',True);tap('Copy coordinates');assert clipboard()=='40.1772, 44.50349'
    t.passed('Opt-in history restores a previous destination without auto-launching a map')
    tap('Settings');tap('Language');tap('\u0420\u0443\u0441\u0441\u043a\u0438\u0439')
    assert t.text_present('\u041d\u0430\u0441\u0442\u0440\u043e\u0439\u043a\u0438')
    tap('\u042f\u0437\u044b\u043a');tap('English');tap('Back');tap('Copy coordinates');assert clipboard()=='40.1772, 44.50349'
    t.passed('English and Russian localize settings; language recreation preserves restored destination')
    tap('Recent locations');tap('Clear history');assert t.text_present('No saved locations yet')
    tap('Back');tap('Settings');tap('Save recent locations');tap('Back')
    t.start(AMAP);tap('Settings');tap('Save recent locations');tap('Back');tap('Recent locations')
    assert not t.text_present('Second car')
    t.passed('Clear history and disabling history remove saved records')
    tap('Back');tap('Settings');tap('Save recent locations');tap('Back')

    t.start(AMAP);tap('Show pin');tap('Remember this map');tap('Yandex Maps (test receiver)')
    t.start(AMAP.replace('40.1772','40.19'))
    t.received('ru.yandex.yandexmaps','yandexmaps://maps.yandex.ru/?pt=44.50349,40.19&z=17&l=map')
    t.passed('Remembered map receives a fresh destination')
    t.adb('uninstall','ru.yandex.yandexmaps');t.start(AMAP)
    assert t.text_present('Your saved map is no longer available')
    t.passed('Uninstalled default map returns to chooser')
    t.start(AMAP.replace('40.1772','40.2'),'com.autonavi.minimap');tap('Test Map')
    t.received('org.li.maprelay.fixturemap','geo:40.2,44.50349?q=40.2,44.50349%28My%20car%29&z=17')
    t.passed('Repeated onNewIntent replaces current destination')
    for bad in ('androidamap://route?dlat=NaN&dlon=2','baidumap://map/direction?destination=latlng:-90,-180'):
        t.start(bad);assert t.text_present('Cannot open this destination')
    t.passed('Malformed and conversion-out-of-range links show errors without crashing')
    t.adb('uninstall','org.li.maprelay.fixturemap');t.shell('pm','disable-user','--user','0','com.google.android.apps.maps')
    t.start(AMAP);assert t.text_present('No compatible map app was found')
    tap('Open in browser');tap('Test Browser')
    t.received('org.li.maprelay.fixturebrowser','https://yandex.com/maps/?pt=44.50349,40.1772&z=17&l=map')
    t.passed('No installed maps still allows the browser fallback')
    proof=t.shell('run-as','org.li.maprelay.testcaller','cat','files/detected.txt')
    assert proof.count('=detected')==3
    t.passed('Caller still detects all three compatibility package identities')
    print('All',len(t.RESULTS),'v2 emulator checks passed.',flush=True)

if __name__=='__main__': main()
