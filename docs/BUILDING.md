# Building TouchPause

## Requirements

- JDK 17
- Android SDK Platform 36
- Android SDK Build Tools installed through the SDK manager
- the repository's Gradle wrapper

TouchPause contains no native code, so the Android NDK is not required.

The machine used during development has a newer default Java runtime that is
not suitable for this Gradle build. Set `JAVA_HOME` to JDK 17 explicitly.

```sh
export JAVA_HOME=/path/to/jdk-17
export ANDROID_HOME=/path/to/android-sdk
```

## Verification build

```sh
./gradlew clean testDebugUnitTest lintDebug assembleDebug
```

Outputs:

- debug APK: `app/build/outputs/apk/debug/app-debug.apk`
- JVM results: `app/build/test-results/testDebugUnitTest/`
- lint report: `app/build/reports/lint-results-debug.html`

Run instrumentation tests on an Android 14+ emulator or device:

```sh
./gradlew connectedDebugAndroidTest
```

## Release bundle

The build reads upload-signing values only from environment variables:

```sh
export TOUCHPAUSE_UPLOAD_KEYSTORE=/absolute/path/to/upload-key.jks
export TOUCHPAUSE_UPLOAD_KEY_ALIAS=upload
export TOUCHPAUSE_UPLOAD_STORE_PASSWORD='...'
export TOUCHPAUSE_UPLOAD_KEY_PASSWORD='...'
./gradlew clean testDebugUnitTest lintRelease bundleRelease
```

The bundle is written to
`app/build/outputs/bundle/release/app-release.aab`. Without all four variables,
Gradle intentionally creates an unsigned release bundle.

Never commit the keystore, passwords, exported Play credentials, or
`local.properties`. Prefer Play App Signing and keep secure backups of the
upload key.

## Artifact checks

Before distributing a build, confirm:

1. package name `io.github.bzhangj13zzz.touchpause`;
2. version code/name match the planned Play release;
3. minimum SDK 34 and target SDK 36;
4. release bundle is signed by the intended upload key;
5. all nine locales are included in the base bundle because language splitting
   is disabled for the offline language picker;
6. no `lib/` entries, `.so` files, native executables, or root/shell helpers are
   present;
7. backup remains disabled;
8. Accessibility metadata cannot retrieve window content and is not marked as
   an accessibility tool;
9. screenshots and disclosure match the exact shipped behavior; and
10. the source commit corresponding to the bundle is tagged and available to
    every APK/bundle recipient as required by GPL-3.0-or-later.

Useful read-only commands include:

```sh
unzip -l app/build/outputs/bundle/release/app-release.aab
shasum -a 256 app/build/outputs/bundle/release/app-release.aab
git diff --check
git status --short
```

## Device matrix

At minimum test:

- Android 14 (API 34) physical device;
- current Android/API 36 emulator and a physical device when available;
- gesture and three-button navigation;
- light/dark mode and all nine locales;
- Quick Settings add/toggle/remove;
- disclosure acceptance and denial;
- Accessibility enable/disable/reconnect;
- touch, local stylus, and Bluetooth stylus where hardware is available;
- Volume Up and Volume Down release;
- touch-exploration and key-filter conflicts;
- force-stop and reboot while a pause is active;
- sessions 1 through 10, blocked session 11, purchase, pending purchase,
  cancellation, offline cache, and restore on another Play-enabled device.
