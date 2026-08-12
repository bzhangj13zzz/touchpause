# Building TouchQuell

These instructions build TouchQuell 1.0.0 from the repository source. They do
not assume a public APK or release service.

## Prerequisites

- JDK 17;
- Android SDK Platform 34 and compatible build tools;
- the repository's Gradle wrapper; and
- Android NDK r28 or newer only when rebuilding the native helper.

The app supports API 24 and newer while compiling and targeting API 34.

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

It uses a development certificate. It can replace another TouchQuell debug
installation only if the signing certificate matches.

## Rebuild the native helper

The repository vendors the preferred C source and generated binaries for four
ABIs. Rebuild them when the source, compiler requirements, or native protocol
changes:

```sh
ANDROID_NDK_HOME=/path/to/android-ndk ./update_lib.sh
```

The script produces one executable named `touchquell-input.so` under each ABI
directory in `app/src/main/jniLibs`:

```text
armeabi-v7a/
arm64-v8a/
x86/
x86_64/
```

The `.so` suffix makes Android package and extract the executable native helper;
the app launches it as a separate root process rather than loading it through
JNI. The build uses API 24 compiler targets, hardening flags, and 16 KiB maximum
ELF page alignment.

After rebuilding native artifacts, rerun the complete debug build:

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug
```

Review the C source's 2026-08-12 Apache modification notice and
[../NOTICE.md](../NOTICE.md) whenever native behavior changes.

## Install locally

With one authorized device connected:

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

The package is `io.github.bzhangj13zzz.touchquell`. It installs alongside the
original Snowy package and does not inherit Snowy preferences. If Android
reports a certificate mismatch for an existing TouchQuell installation,
uninstall that installation only after deciding that its local settings can be
discarded.

## Release signing

The repository intentionally contains no release keystore or signing secrets.
For a private signed build:

1. keep the keystore outside the repository;
2. provide credentials through local, ignored configuration;
3. increment `versionCode` for every update;
4. run tests and lint before assembly;
5. verify the final APK signature and zip alignment; and
6. record the exact source commit, native-source commit, toolchain versions, and
   APK checksum.

Never commit keystores, passwords, `local.properties`, or generated APKs.

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
```

Also confirm that the APK contains `touchquell-input.so` for all four ABIs and
that rebuilt ELF load segments satisfy the intended 16 KiB alignment.
