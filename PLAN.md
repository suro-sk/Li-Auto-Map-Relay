# Map Relay implementation

Version 2.1: the user requested removal of Armenian and a Li Auto-related name/logo. Rename the visible app to Li Auto Map Relay, use an original native vector with an LI monogram, vehicle and pin, remove Armenian resources/choice, and migrate a saved removed locale to system default. Preserve compatibility package names and signing key for in-place updates. Validate this narrow change with the existing Java suite and focused emulator upgrade/language/branding checks; keep the 2.0 feature/regression evidence as the historical baseline.

Version 2 supersedes version 1's no-history requirement below. The user explicitly requested all seven proposed additions: pin/walking/driving modes, copy/share, explicit input-coordinate selector, diagnostics, browser fallback, optional recent destinations, and language selection.

V2 design: preserve the framework-only Activity and compatibility package variants. Split its native UI into destination, settings, history and diagnostic screens. Keep the chooser default. Use official Yandex `rtext` latitude/longitude with `rtt=pd/auto` for routes, Google navigation for Google Maps, and geo pins for generic receivers. Build all outgoing URLs from validated points. Resolve browser handlers separately and target their components. Store only opt-in bounded history (20 records) with receipt time and selected input interpretation; disabling/clearing deletes it. Support English, Russian and Armenian with Android resources. Keep APK permissions empty, use the existing signing key and increment to version 2.0/code 2. Verify pure Java transformations/history and real emulator clipboard, sharesheet, browser, language and multi-instance behavior with separate fixtures.

## Version 1 baseline

User intent: Li Auto accepts Amap/Baidu/Tencent as external destinations. Accept that existing navigation handoff and let the user select an installed map, particularly Yandex Maps. Keep the Li Auto APK unchanged.

The compatibility package is the only Chinese-map identity used. Visible app identity is Map Relay. Build three mutually independent compatibility variants; the user installs one whose real counterpart is absent. The primary output is Amap-compatible; Tencent-compatible allows retaining real Amap.

1. Test the exact recovered URI formats: Amap dlat/dlon/dev, Tencent tocoord, Baidu destination. Reject missing, malformed, ambiguous and non-finite coordinates. Verify numeric order, Unicode/escaped names, coordinate conversion and destination URI encoding.
2. Implement a plain Java parser and WGS84 normalization. Amap dev=1 stays WGS84; GCJ02 is approximately inverted within the same conservative bounding region used by the investigated app. Baidu's added offset is removed first, including outside that region. Offer an automatic/raw override.
3. Implement one framework Android Activity: destination preview, installed-map chooser, optional remembered target, launcher instructions/reset and explicit error states. Yandex receives a placemark URI; generic maps receive geo coordinates. Store preferences only, never destination history. No requested permissions, services or network libraries.
4. Build with the installed Android SDK (API 35), javac, D8, aapt2, zipalign and apksigner. Preserve a local signing key for updates; exclude it from source bundles.
5. Verify on a fresh task-local emulator. Use test-only map receiver and caller APKs to prove package discovery, original URI interception, chooser selection and outgoing destination. Test no compatible maps, invalid input, remembered target and repeated intents. Do not install on a connected physical phone or execute the original Li Auto app.

Material limits: package identity cannot coexist with the real same-package app; this changes external map handoff only; coordinate inversion is approximate and selectable. Actual Yandex UI and Li Auto runtime integration still require the user's phone.

Documentation sources: Android package visibility and geo intents; Yandex's official Android launch URL/placemark documentation; Amap Android route parameters; recovered Li Auto helper j0.java and VehiclePosition Hermes functions 16710/16824/16840.
