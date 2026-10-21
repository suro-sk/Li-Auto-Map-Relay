# Li Auto Map Relay 2.1 verification

Build date: 2026-10-21. Version 2.1/code 3; min SDK 24, compile/target SDK 35. Task-local AVD MapRelay_Test: Android 16/API 36, emulator-5580.

**64 Java assertions and 7 focused emulator upgrade/branding/language checks passed on 2.1.**

All three variants passed signature v2/v3 verification, zip alignment, manifest checks and in-place installation. They retain their compatibility packages and signing key, request no Android permissions, disable backup and are not debuggable. Compiled resources contain default English and Russian; Armenian is absent.

## Focused checks

1. All three version 2.1 compatibility APKs install with the existing signatures.
2. Upgrade from saved Armenian preference falls back to system English without a crash.
3. Language chooser contains only system default, English and Russian.
4. Russian and English still switch correctly after the upgrade.
5. Existing saved location and its coordinates survive the update.
6. Remembered map survives the update and receives the new destination.
7. Diagnostic reports use the new app name and version.

## Prior functional baseline

Version 2.0 previously passed 24 feature checks and 3 additional regressions for URI handoffs, routes, sharing, diagnostics, browser fallback, history, dialogs and the 1.0 upgrade. Those full emulator suites were not rerun for this narrowly scoped branding/language update. Their original, versioned evidence remains in verification/v2/ in the development workspace. The complete Java suite was rerun for 2.1.

Current results, screenshots, manifests and build logs are in verification/v21/. Source bundles contain the current evidence only.

## Limits

No physical phone was used or modified. Actual Li Auto/Yandex/Google rendering and vendor-specific phone behavior still require a device check. Test-only receiver apps verify the outgoing URI, clipboard payload and preserved settings. Coordinate inversion remains approximate, and receipt time is not a vehicle GPS fix time. The original Li Auto APK remains unchanged.
