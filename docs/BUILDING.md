# Building TouchPause

These instructions build TouchPause 1.0.0 from the repository source. They do
not assume a public APK or release service.

## Prerequisites

- JDK 17;
- Android SDK Platform 36 and Build Tools 35.0.0 or newer;
- the repository's Gradle wrapper; and
- Android NDK r28 or newer only when rebuilding the native helper.

The app supports API 24 and newer while compiling and targeting API 36. The
wrapper uses Gradle 8.11.1, Android Gradle Plugin 8.10.1, and Kotlin 2.2.10.
App Bundles keep all nine small translation sets in the base install because
the in-app language picker must also work offline and independently of the
device language.

## Configure the Android toolchain

Set local paths for the current shell. Do not commit them.

```sh
export JAVA_HOME=/path/to/jdk-17
export ANDROID_HOME=/path/to/android-sdk
```

`local.properties` may contain `sdk.dir` for Android Studio and is intentionally
ignored by Git.

Confirm that Gradle sees JDK 17:

```sh
./gradlew --version
```

## Test and build a debug APK

Run unit tests, Android lint, and assembly from the repository root:

```sh
./gradlew clean testDebugUnitTest lintDebug assembleDebug
```

The installable debug APK is:

```text
app/build/outputs/apk/debug/app-debug.apk
```

It uses a development certificate. It can replace another TouchPause debug
installation only if the signing certificate matches.

## Rebuild the native helper

The repository vendors the preferred C source and generated binaries for four
ABIs. Rebuild them when the source, compiler requirements, or native protocol
changes:

```sh
ANDROID_NDK_HOME=/path/to/android-ndk ./update_lib.sh
```

The script produces one executable named `libtouchpause-input.so` under each ABI
directory in `app/src/main/jniLibs`:

```text
armeabi-v7a/
arm64-v8a/
x86/
x86_64/
```

The conventional `lib` prefix and `.so` suffix make Android package and extract
the executable native helper, including on older Android installers. The app
launches it as a separate root process rather than loading it through JNI. The
build uses API 24 compiler targets, hardening flags, and 16 KiB maximum ELF page
alignment.

After rebuilding native artifacts, rerun the complete debug build:

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug
```

Review the C source's 2026-08-12 and 2026-08-16 Apache modification notice and
[../NOTICE.md](../NOTICE.md) whenever native behavior changes.

## Install locally

With one authorized device connected:

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

The package is `io.github.bzhangj13zzz.touchpause`. It installs alongside the
original Snowy package and earlier TouchQuell test builds, and it does not
inherit their preferences. If Android reports a certificate mismatch for an
existing TouchPause installation,
uninstall that installation only after deciding that its local settings can be
discarded.

## Release signing

The repository intentionally contains no release keystore or signing secrets.
It reads an upload key only from these environment variables:

```sh
export TOUCHPAUSE_UPLOAD_KEYSTORE=/absolute/path/to/upload-key.jks
export TOUCHPAUSE_UPLOAD_KEY_ALIAS=upload
export TOUCHPAUSE_UPLOAD_STORE_PASSWORD='...'
export TOUCHPAUSE_UPLOAD_KEY_PASSWORD='...'
```

With all four values set, produce the Play upload bundle with:

```sh
./gradlew clean testDebugUnitTest lintRelease bundleRelease
```

The signed bundle is written to:

```text
app/build/outputs/bundle/release/app-release.aab
```

When the variables are absent, Gradle can still build an unsigned release
bundle for reproducibility checks, but it cannot be uploaded to Play. For a
release:

1. keep the keystore outside the repository;
2. provide credentials through local, ignored configuration;
3. increment `versionCode` for every update;
4. run tests and release lint before bundling;
5. verify the bundle and Play-generated split APKs;
6. enroll in Play App Signing; and
7. record the exact source commit, native-source commit, toolchain versions, and
   AAB checksum.

Never commit keystores, passwords, `local.properties`, generated APKs, or AABs.

If an APK is shared with another person, GPLv3 requires that recipient to be
able to obtain its complete Corresponding Source under GPLv3. A private
repository is sufficient only for recipients who have access; otherwise
provide the matching source by another compliant method. Repository visibility
does not change the software's license.

## Useful verification checks

The Android SDK supplies `apksigner` and `zipalign`:

```sh
apksigner verify --verbose app/build/outputs/apk/debug/app-debug.apk
zipalign -c -v 4 app/build/outputs/apk/debug/app-debug.apk
zipalign -c -P 16 -v 4 app/build/outputs/apk/debug/app-debug.apk
```

Also confirm that the APK contains `libtouchpause-input.so` for all four ABIs and
that rebuilt ELF load segments satisfy the intended 16 KiB alignment. Before a
Play rollout, use `bundletool` to generate and install APKs from the final AAB,
then repeat the checks on those APKs and run them on a 16 KiB-page Android 15 or
newer device/emulator.

`useLegacyPackaging = true` is intentional. The native file is an executable
launched from Android's extracted native-library directory, not a JNI library;
compressed/extracted packaging preserves that execution path.

See [PLAY_STORE.md](PLAY_STORE.md) for the publication checklist.
