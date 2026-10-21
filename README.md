# Li Auto Map Relay 2.1

A small Android app that receives Li Auto's external-map destination and asks which installed map to open. Yandex Maps appears first when installed. Leave **Remember this map** unchecked to get the chooser every time.

Unofficial community utility; not affiliated with Li Auto, Amap, Baidu or Tencent.

## Download

Get the signed [Baidu-compatible APK](https://github.com/suro-sk/Li-Auto-Map-Relay/releases/download/v2.1/MapRelay-Baidu.apk) from [GitHub Releases](https://github.com/suro-sk/Li-Auto-Map-Relay/releases/latest). The published release contains the **Baidu variant only**.

The release also includes a source ZIP and `SHA256SUMS.txt`. Google Play Protect may ask to scan an unfamiliar APK; select **Scan / Проверить** and wait for its result. See [Google's scanning guidance](https://support.google.com/googleplay/answer/2812853?hl=en). These APKs have not been preapproved by Google.

## Install and use

Requires Android 7.0 or newer. Install the Baidu-compatible APK while the real Baidu Maps app is not installed:

| Release APK (also built into `dist/`) | Select inside Li Auto | Android package occupied |
| --- | --- | --- |
| MapRelay-Baidu.apk | Baidu Maps | `com.baidu.BaiduMap` |

The app displays **Li Auto Map Relay** on Android, with an original dark-green LI, car and location-pin icon. Android cannot install this relay alongside real Baidu Maps because they occupy the same package; they also cannot update each other. You can keep real Amap or Tencent Maps installed.

1. Install Yandex Maps, or another app that accepts standard map-coordinate links.
2. Install `MapRelay-Baidu.apk` using Android's normal APK installer. Allow installation from the source you used if Android asks.
3. In Li Auto, open the car-location screen and tap the external-map arrow.
4. Select **Baidu Maps**. Li Auto's sheet keeps its original label.
5. Map Relay shows the destination coordinates and the available maps. Tap **Yandex Maps**, or another map.

Choose **Show pin**, **Walking** or **Driving** before selecting a map. Yandex and Google Maps receive the requested route mode; other maps receive a standard pin so you can choose routing inside that app. Route links ask the receiving map to choose the starting location; Map Relay does not read the phone's GPS or start tracking.

To skip the relay chooser next time, enable **Remember this map** before selecting a map. Open Map Relay from its launcher icon, open **Settings**, and tap **Forget default map** to restore the chooser. If the saved map is removed, the chooser returns automatically. The selected route mode is remembered separately.

## New tools

- **Copy coordinates** copies the outgoing WGS84 latitude and longitude, rounded to eight decimal places.
- **Share location** opens Android's share sheet with the label, coordinates and an HTTPS Yandex pin link. The shared link always uses a pin, so its recipient can choose a route.
- **Open in browser** lets you select an installed browser. It sends the current pin or route as HTTPS, targeting the browser explicitly so a map app cannot intercept that action.
- **Diagnostics** shows the original incoming link, original coordinates, inferred and selected formats, converted coordinates, receipt time, default map package and outgoing links for available maps. **Copy report** copies it to the clipboard. Invalid links also have diagnostic details.
- **Recent locations** stores up to 20 destinations only after you enable it in Settings. Entries include receipt time and the coordinate interpretation selected for that receipt. Tap an entry to preview and choose a map again. Repeated identical links move to the top and update their receipt time. **Clear history** deletes them; turning history off also deletes them. Reopening an entry does not trigger the remembered map automatically.
- **Language** in Settings offers system default, English and Russian. Dates follow the selected locale. Map names and received address labels retain their original text.

Receipt times mean when Map Relay received the link, **not when the vehicle obtained its GPS position**. Diagnostic reports and shared links contain location data; they are generated locally and leave the app only through the action you select.

## Update from 1.0 or 2.0

Install the same compatibility variant over your previous Map Relay APK. These 2.1 builds use the same local signing key and package identities, retaining preferences and optional history. A previously selected Armenian language resets to system default; Armenian translations and the language option have been removed. A 1.0 correction preference still migrates, with history off unless enabled. Android cannot update a different compatibility package in place.

## Coordinates and scope

The relay understands the exact Amap, Tencent and Baidu navigation URI formats recovered from Li Auto 8.27.0. Amap/Tencent destinations use GCJ02; Baidu receives a BD09 conversion. The relay normalizes those to WGS84, leaving GCJ02 unchanged outside the same conservative China bounding rectangle used by the vehicle app. Baidu's added offset is removed first, including outside China, because the investigated helper adds it globally.

Coordinate inversion is approximate. In **Settings → Coordinate format**, **Automatic (from link)** is the default. **WGS84 (no correction)** passes the coordinates through; **GCJ02** and **BD09** override the input interpretation. An override affects subsequent requests too. Use Diagnostics to compare input and output. Missing, non-finite, out-of-range or ambiguous destinations produce an error screen.

This changes the external-map handoff. It does not repair the empty map background, address lookup or snapshot images inside Li Auto. It requires no changes to Li Auto, root access or vehicle commands.

## Privacy

The APK requests **no Android permissions**, including location and internet. There are no services, analytics or network libraries. Format, route, language and default-map preferences are saved locally. Optional history stores the original incoming links, receipt times and input-format choices in private app storage; it is off by default, capped at 20 entries and excluded from Android backup. No diagnostic log is written by the app. Android receives the navigation intent, and the map, browser or sharing app you select receives the destination as usual.

The original Li Auto APK was inspected statically and remains unchanged. No physical phone was used during development.

## Verification and limitations

The Java suite covers parsing, coordinate order, Unicode labels, correction, malformed links, ambiguous destinations, route links, explicit format overrides and bounded history serialization. Android emulator integration uses a separate caller matching Li Auto's package-query behavior and test-only map, browser, share and foreground clipboard receiver apps. See `VERIFICATION.md` for the final results, `SHA256SUMS.txt` in the GitHub release for published file hashes, and `dist/build-results.json` after a local build for that build's APK hashes.

**Actual Li Auto and actual Yandex Maps still need to be tried together on your phone.** Emulator checks verify the handoff mechanics and outgoing URI; they do not establish real Yandex rendering or behavior on every Android vendor build.

Do not install the test receiver APKs in `build/fixtures/` on your phone: they are fixtures, not Yandex Maps. The published release APK is `MapRelay-Baidu.apk`.

## Build from source

No Gradle or third-party app dependencies are required. The build script uses installed SDK API 35, Android build-tools 35.0.0, PowerShell and JDK 17 or newer. This build used JDK 26.

```powershell
.\build.ps1
# Or build one identity:
.\build.ps1 -Identity Tencent
# Override local paths if needed:
.\build.ps1 -SdkRoot 'C:\Android\Sdk' -JdkRoot 'C:\Program Files\Java\jdk-26'
```

The script runs the Java checks, compiles with javac/D8 in a fresh build directory, packages with aapt2, aligns and signs each APK, and verifies its v2/v3 signatures. The first build generates a local signing key under `signing/`; retain that directory privately to produce installable updates. It is excluded from source bundles and version control. A fresh source checkout generates a different key and cannot update an APK signed with this build's key.

For emulator integration, `tools/start-test-emulator.ps1` starts an isolated Android 36 AVD on `emulator-5580`, using the installed Google APIs x86_64 system image. Install all three release variants into it, then run `python tests/build_fixtures.py` and `python tests/emulator_test.py`. Additional regressions run with `python tests/emulator_regression_test.py` and `python tests/emulator_history_regression_test.py`. Tests refuse to operate unless the AVD name is `MapRelay_Test`. They clear test app packages in that emulator, temporarily use task lock to switch between two relay instances, and disable preinstalled Google Maps to cover the no-map state. Shut it down with `adb -s emulator-5580 emu kill` when finished.

The development upgrade check, `tests/emulator_upgrade_test.py`, additionally requires the retained original 1.0 APK under `build/baseline-v1/`. That old binary is not included in the source ZIP. It operates only on the isolated emulator's Tencent variant.

## Evidence and protocol documentation

- Recovered Li Auto helper: `analysis/jadx/sources/com/chehejia/oc/m01/helper/j0.java` in the parent analysis workspace. Detection uses `getPackageInfo(packageName, 0)` without checking a signing certificate in this path.
- Screen traces: `analysis/screens/android.md` and `analysis/screens/hermes.md` in the parent workspace.
- [Yandex Android launch links and placemarks](https://yandex.com/dev/yandex-apps-launch-maps/doc/en/concepts/yandexmaps-android-app): Yandex `pt` uses **longitude,latitude**.
- [Android map intents](https://developer.android.com/guide/components/google-maps-intents): standard `geo:` uses **latitude,longitude**.
- [Android package visibility declarations](https://developer.android.com/training/package-visibility/declaring).
- [Amap Android route parameters](https://lbs.amap.com/api/amap-mobile/guide/android/route).
