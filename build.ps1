param(
    [ValidateSet('All','Amap','Baidu','Tencent')][string]$Identity='All',
    [string]$SdkRoot=(Join-Path $env:LOCALAPPDATA 'Android\Sdk'),
    [string]$JdkRoot='C:\Program Files\Java\jdk-26'
)
$ErrorActionPreference='Stop'
$taskRoot=$PSScriptRoot
$buildBase=Join-Path $taskRoot 'build'
# Use a fresh directory so removed anonymous classes/resources cannot leak into a rebuild.
$buildRoot=Join-Path $buildBase ('compile-'+[Guid]::NewGuid().ToString('N'))
$outputRoot=Join-Path $taskRoot 'dist'
$toolsRoot=Join-Path $SdkRoot 'build-tools\35.0.0'
$androidJar=Join-Path $SdkRoot 'platforms\android-35\android.jar'
$env:JAVA_HOME=$JdkRoot
function Invoke-Checked([string]$Program,[string[]]$Arguments) {
    $previousError=$ErrorActionPreference
    $ErrorActionPreference='Continue'
    try { & $Program @Arguments 2>&1 | ForEach-Object { $_.ToString() }; $nativeExit=$LASTEXITCODE }
    finally { $ErrorActionPreference=$previousError }
    if ($nativeExit -ne 0) { throw ($Program+' exited '+$nativeExit) }
}
foreach ($path in @($androidJar,(Join-Path $toolsRoot 'aapt2.exe'),(Join-Path $JdkRoot 'bin\javac.exe'))) {
    if (!(Test-Path -LiteralPath $path)) { throw ('Required installed build tool missing: '+$path) }
}
New-Item -ItemType Directory -Force -Path $buildRoot,$outputRoot,(Join-Path $buildRoot 'classes'),(Join-Path $buildRoot 'dex'),(Join-Path $buildRoot 'unit') | Out-Null
$sources=@(Get-ChildItem -LiteralPath (Join-Path $taskRoot 'src\main\java') -Recurse -Filter '*.java' | Select-Object -ExpandProperty FullName)
Invoke-Checked (Join-Path $JdkRoot 'bin\javac.exe') (@('--release','8','-Xlint:-options','-classpath',$androidJar,'-d',(Join-Path $buildRoot 'classes'))+$sources)
$testSources=@(Get-ChildItem -LiteralPath (Join-Path $taskRoot 'tests') -Filter '*Test.java' | Select-Object -ExpandProperty FullName)
Invoke-Checked (Join-Path $JdkRoot 'bin\javac.exe') (@('--release','8','-Xlint:-options','-classpath',(Join-Path $buildRoot 'classes'),'-d',(Join-Path $buildRoot 'unit'))+$testSources)
Invoke-Checked (Join-Path $JdkRoot 'bin\java.exe') @('-cp',((Join-Path $buildRoot 'classes')+';'+(Join-Path $buildRoot 'unit')),'org.li.maprelay.MapRequestTest')
Invoke-Checked (Join-Path $JdkRoot 'bin\java.exe') @('-cp',((Join-Path $buildRoot 'classes')+';'+(Join-Path $buildRoot 'unit')),'org.li.maprelay.MapFeaturesTest')
Invoke-Checked (Join-Path $JdkRoot 'bin\jar.exe') @('--create','--file',(Join-Path $buildRoot 'classes.jar'),'-C',(Join-Path $buildRoot 'classes'),'.')
Invoke-Checked (Join-Path $toolsRoot 'd8.bat') @('--min-api','24','--lib',$androidJar,'--output',(Join-Path $buildRoot 'dex'),(Join-Path $buildRoot 'classes.jar'))
Invoke-Checked (Join-Path $toolsRoot 'aapt2.exe') @('compile','--dir',(Join-Path $taskRoot 'src\main\res'),'-o',(Join-Path $buildRoot 'resources.zip'))
$signingRoot=Join-Path $taskRoot 'signing'
New-Item -ItemType Directory -Force -Path $signingRoot | Out-Null
$keystore=Join-Path $signingRoot 'map-relay.p12'
$passFile=Join-Path $signingRoot 'password.txt'
if (!(Test-Path -LiteralPath $keystore)) {
    [Guid]::NewGuid().ToString('N') | Set-Content -LiteralPath $passFile -Encoding ASCII -NoNewline
    Invoke-Checked (Join-Path $JdkRoot 'bin\keytool.exe') @('-genkeypair','-noprompt','-keystore',$keystore,'-storetype','PKCS12','-storepass:file',$passFile,'-keypass:file',$passFile,'-alias','maprelay','-keyalg','RSA','-keysize','3072','-validity','3650','-dname','CN=Map Relay, OU=Personal Android utilities, O=Local build')
}
$variants=@(
    @{Name='Amap'; Package='com.autonavi.minimap'; Schemes='<data android:scheme="androidamap" /><data android:scheme="amapuri" />'},
    @{Name='Baidu'; Package='com.baidu.BaiduMap'; Schemes='<data android:scheme="baidumap" />'},
    @{Name='Tencent'; Package='com.tencent.map'; Schemes='<data android:scheme="qqmap" />'}
)
Add-Type -AssemblyName System.IO.Compression.FileSystem
Add-Type -AssemblyName System.IO.Compression
$outputs=@()
foreach ($variant in $variants) {
    if ($Identity -ne 'All' -and $Identity -ne $variant.Name) { continue }
    $variantRoot=Join-Path $buildRoot $variant.Name
    New-Item -ItemType Directory -Force -Path $variantRoot | Out-Null
    $manifestPath=Join-Path $variantRoot 'AndroidManifest.xml'
    $manifest=(Get-Content -LiteralPath (Join-Path $taskRoot 'src\main\AndroidManifest.xml.in') -Raw).Replace('@PACKAGE@',$variant.Package).Replace('@SCHEMES@',$variant.Schemes)
    [System.IO.File]::WriteAllText($manifestPath,$manifest,(New-Object System.Text.UTF8Encoding($false)))
    $unsigned=Join-Path $variantRoot 'unsigned.apk'
    Invoke-Checked (Join-Path $toolsRoot 'aapt2.exe') @('link','-o',$unsigned,'--manifest',$manifestPath,'-I',$androidJar,(Join-Path $buildRoot 'resources.zip'))
    $archive=[System.IO.Compression.ZipFile]::Open($unsigned,[System.IO.Compression.ZipArchiveMode]::Update)
    try { [System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile($archive,(Join-Path $buildRoot 'dex\classes.dex'),'classes.dex',[System.IO.Compression.CompressionLevel]::Optimal) | Out-Null }
    finally { $archive.Dispose() }
    $aligned=Join-Path $variantRoot 'aligned.apk'
    Invoke-Checked (Join-Path $toolsRoot 'zipalign.exe') @('-f','4',$unsigned,$aligned)
    $apk=Join-Path $outputRoot ('MapRelay-'+$variant.Name+'.apk')
    Invoke-Checked (Join-Path $JdkRoot 'bin\java.exe') @('--enable-native-access=ALL-UNNAMED','-jar',(Join-Path $toolsRoot 'lib\apksigner.jar'),'sign','--ks',$keystore,'--ks-key-alias','maprelay','--ks-pass',('file:'+$passFile),'--v1-signing-enabled','false','--v2-signing-enabled','true','--v3-signing-enabled','true','--out',$apk,$aligned)
    Invoke-Checked (Join-Path $JdkRoot 'bin\java.exe') @('--enable-native-access=ALL-UNNAMED','-jar',(Join-Path $toolsRoot 'lib\apksigner.jar'),'verify','--verbose',$apk)
    Invoke-Checked (Join-Path $toolsRoot 'zipalign.exe') @('-c','4',$apk)
    $outputs+=@{variant=$variant.Name;package=$variant.Package;file=$apk;bytes=(Get-Item -LiteralPath $apk).Length;sha256=(Get-FileHash -LiteralPath $apk -Algorithm SHA256).Hash.ToLowerInvariant()}
}
$outputs | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath (Join-Path $outputRoot 'build-results.json') -Encoding UTF8
$outputs | ForEach-Object { Write-Output ($_.variant+': '+$_.bytes+' bytes - '+$_.file) }
