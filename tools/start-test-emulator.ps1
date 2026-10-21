$ErrorActionPreference='Stop'
$taskRoot=Split-Path $PSScriptRoot -Parent
$sdkRoot=Join-Path $env:LOCALAPPDATA 'Android\Sdk'
$avdHome=Join-Path $taskRoot 'build\emulator'
$avdPath=Join-Path $avdHome 'MapRelay_Test.avd'
New-Item -ItemType Directory -Force -Path $avdPath | Out-Null
@"
avd.ini.encoding=UTF-8
path=$avdPath
target=android-36
"@ | Set-Content -LiteralPath (Join-Path $avdHome 'MapRelay_Test.ini') -Encoding ASCII
@"
AvdId=MapRelay_Test
avd.ini.displayname=Map Relay Test
PlayStore.enabled=false
abi.type=x86_64
hw.cpu.arch=x86_64
hw.cpu.ncore=2
hw.ramSize=2048
hw.gpu.enabled=yes
hw.gpu.mode=swiftshader
hw.keyboard=yes
hw.lcd.density=420
hw.lcd.height=1920
hw.lcd.width=1080
disk.dataPartition.size=2G
image.sysdir.1=$sdkRoot\system-images\android-36\google_apis\x86_64\
tag.id=google_apis
tag.display=Google APIs
target=android-36
showDeviceFrame=no
vm.heapSize=256
fastboot.forceColdBoot=yes
"@ | Set-Content -LiteralPath (Join-Path $avdPath 'config.ini') -Encoding ASCII
$env:ANDROID_AVD_HOME=$avdHome
$emulator=Join-Path $sdkRoot 'emulator\emulator.exe'
$process=Start-Process -FilePath $emulator -ArgumentList @('-avd','MapRelay_Test','-port','5580','-no-window','-no-audio','-no-snapshot','-gpu','swiftshader') -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $avdHome 'stdout.log') -RedirectStandardError (Join-Path $avdHome 'stderr.log')
$process.Id | Set-Content -LiteralPath (Join-Path $avdHome 'owned-pid.txt') -Encoding ASCII
Write-Output ('Started task-local emulator PID '+$process.Id+' on emulator-5580')
