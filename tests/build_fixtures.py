"""Build emulator-only receivers. These APKs must never be distributed as real map apps."""
import os,pathlib,subprocess,zipfile
ROOT=pathlib.Path(__file__).resolve().parents[1]
SDK=pathlib.Path(os.environ['LOCALAPPDATA'])/'Android'/'Sdk'
JAVA=pathlib.Path(r'C:\Program Files\Java\jdk-26')/'bin'
TOOLS=SDK/'build-tools'/'35.0.0'
ANDROID=SDK/'platforms'/'android-35'/'android.jar'
OUT=ROOT/'build'/'fixtures'
OUT.mkdir(parents=True,exist_ok=True)
def run(*args):
    result=subprocess.run([str(a) for a in args],capture_output=True,text=True,encoding='utf8',errors='replace')
    if result.returncode: raise RuntimeError(result.stdout+'\n'+result.stderr)
sources=list((ROOT/'tests'/'android').glob('*.java'))
classes=OUT/'classes';classes.mkdir(exist_ok=True)
run(JAVA/'javac.exe','--release','8','-Xlint:-options','-classpath',ANDROID,'-d',classes,*sources)
run(JAVA/'jar.exe','--create','--file',OUT/'fixtures.jar','-C',classes,'.')
run(JAVA/'java.exe','-cp',TOOLS/'lib'/'d8.jar','com.android.tools.r8.D8','--min-api','24','--lib',ANDROID,'--output',OUT,OUT/'fixtures.jar')
for name,pkg,label,activity,schemes in [
    ('yandex','ru.yandex.yandexmaps','Yandex Maps (test receiver)','FakeMapActivity',['geo','yandexmaps']),
    ('generic','org.li.maprelay.fixturemap','Test Map','FakeMapActivity',['geo']),
    ('browser','org.li.maprelay.fixturebrowser','Test Browser','FakeMapActivity',['https']),
    ('share','org.li.maprelay.fixtureshare','Test Share Receiver','FakeMapActivity',[]),
    ('clipboard','org.li.maprelay.fixtureclipboard','Test Clipboard Reader','ClipboardActivity',[]),
    ('caller','org.li.maprelay.testcaller','Relay Test Caller','CallerActivity',[])]:
    filters=''.join('<intent-filter><action android:name="android.intent.action.VIEW"/><category android:name="android.intent.category.DEFAULT"/><category android:name="android.intent.category.BROWSABLE"/><data android:scheme="'+s+'"/></intent-filter>' for s in schemes)
    if name=='browser': filters='<intent-filter><action android:name="android.intent.action.VIEW"/><category android:name="android.intent.category.DEFAULT"/><category android:name="android.intent.category.BROWSABLE"/><data android:scheme="http"/><data android:scheme="https"/></intent-filter>'
    if name=='share': filters='<intent-filter><action android:name="android.intent.action.SEND"/><category android:name="android.intent.category.DEFAULT"/><data android:mimeType="text/plain"/></intent-filter>'
    queries='<queries>'+''.join('<package android:name="'+p+'"/>' for p in ['com.autonavi.minimap','com.baidu.BaiduMap','com.tencent.map'])+'</queries>'
    manifest=OUT/(name+'.xml')
    manifest.write_text('<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="'+pkg+'" android:versionCode="1" android:versionName="1.0"><uses-sdk android:minSdkVersion="24" android:targetSdkVersion="35"/>'+queries+'<application android:label="'+label+'" android:debuggable="true" android:theme="@android:style/Theme.Material.Light.NoActionBar"><activity android:name="org.li.maprelay.fixture.'+activity+'" android:exported="true" android:launchMode="singleTop">'+filters+'</activity></application></manifest>',encoding='utf8')
    apk=OUT/(name+'-unsigned.apk')
    run(TOOLS/'aapt2.exe','link','-o',apk,'--manifest',manifest,'-I',ANDROID)
    with zipfile.ZipFile(apk,'a',compression=zipfile.ZIP_DEFLATED) as z:z.write(OUT/'classes.dex','classes.dex')
    aligned=OUT/(name+'-aligned.apk');run(TOOLS/'zipalign.exe','-f','4',apk,aligned)
    run(JAVA/'java.exe','--enable-native-access=ALL-UNNAMED','-jar',TOOLS/'lib'/'apksigner.jar','sign','--ks',ROOT/'signing'/'map-relay.p12','--ks-key-alias','maprelay','--ks-pass','file:'+str(ROOT/'signing'/'password.txt'),'--out',OUT/(name+'.apk'),aligned)
    print('Built emulator-only fixture:',name,flush=True)
