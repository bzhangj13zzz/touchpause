# TouchPause

*Pause touch. Keep everything running.*

TouchPause is a completely rootless Android utility that temporarily pauses
touchscreen and stylus input. Its primary control is a Quick Settings tile;
the launcher screen is reserved for setup, safety, feedback, language, and
purchase settings.

Every installation includes 10 successfully started sessions. After that,
Google Play offers a one-time lifetime unlock. There are no subscriptions, ads,
developer accounts, analytics, or developer-operated servers.

TouchPause is a substantially modified derivative of Snowy. See
[NOTICE.md](NOTICE.md) for lineage and attribution.

## Requirements

- Android 14 or newer (API 34+).
- A touchscreen.
- TouchPause enabled as an Android Accessibility service after accepting its
  in-app disclosure.
- Volume Up or Volume Down as the physical release key.

TouchPause never requests root access and contains no native input helper. It
does not support Android 13 or older because Android first exposed the required
motion-source capture API in Android 14.

Touch exploration and hardware-key filtering are shared Accessibility
facilities. TouchPause will refuse to pause touch if touch exploration is
active or another Accessibility service is already filtering hardware keys.

## Install and migrate

Install through Google Play when available, or install an APK signed by a
source you trust. TouchPause uses the application ID
`io.github.bzhangj13zzz.touchpause` and can coexist with Snowy or earlier
TouchQuell test builds. Settings are not imported; remove old apps and their
Quick Settings tiles manually when no longer needed.

Android may restrict Accessibility for an APK installed outside an app store.
If TouchPause is unavailable in Accessibility, open **App info → More/overflow
menu → Allow restricted settings**, then try again. Exact wording varies by
device maker.

## Setup

1. Open TouchPause.
2. Open **Set up touch control**, read the standalone disclosure, and choose
   **Agree and continue**.
3. Enable TouchPause in Android's Accessibility settings.
4. Return to TouchPause and choose **Volume Up** or **Volume Down** as the
   release key.
5. Tap **Add TouchPause tile**. If Android does not add it, swipe down twice,
   choose **Edit**, and drag TouchPause into the active tiles.

## Everyday use

1. Tap the inactive Quick Settings tile to pause touch.
2. Press the selected volume key to restore touch.

The app receives touchscreen and stylus motion plus hardware-key events only
while a pause is active. It withholds touch/stylus motion, uses key events only
to detect the selected volume key, forwards unrelated keys, and immediately
discards every input event. It cannot retrieve window content. See
[PRIVACY.md](PRIVACY.md) for the complete disclosure.

An active pause is not restored after the Accessibility service stops or the
device reboots.

## Trial and lifetime access

A trial session is counted only after input capture starts successfully. Setup
attempts, conflicts, and ending a pause do not consume a session. After 10
successful starts, the tile opens the lifetime-access screen instead of
starting another pause.

The unlock is a non-consumable Google Play product named `lifetime_access`, not
a subscription. TouchPause caches entitlement for offline use and stores the
local trial count in private app data. Clearing app data can reset the local
trial; preventing that would require a developer server, which TouchPause
deliberately avoids.

## Emergency recovery

Press the configured volume key first. If touch does not return:

1. Hold the phone's physical power button and reboot it.
2. If USB debugging was already authorized, run:

   ```sh
   adb shell am force-stop io.github.bzhangj13zzz.touchpause
   ```

Stopping the app or rebooting makes Android remove Accessibility input capture.
See [SECURITY.md](SECURITY.md) for the recovery and trust model.

## Build and test

TouchPause requires JDK 17 and Android SDK Platform 36.

```sh
export JAVA_HOME=/path/to/jdk-17
export ANDROID_HOME=/path/to/android-sdk
./gradlew clean testDebugUnitTest lintDebug assembleDebug bundleRelease
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`. The
release bundle remains unsigned unless the documented upload-key environment
variables are provided. See [docs/BUILDING.md](docs/BUILDING.md) and
[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

## Languages

The interface and safety disclosure are available in English, Spanish, French,
German, Brazilian Portuguese, Japanese, Korean, Simplified Chinese, and
Traditional Chinese. The in-app language picker works offline because all nine
locales remain in the base app bundle.

## Play Store preparation

Listing text, artwork, screenshots, and a publication checklist are in
[fastlane/metadata/android](fastlane/metadata/android) and
[docs/PLAY_STORE.md](docs/PLAY_STORE.md). Publication still requires a public
privacy-policy URL, a signed App Bundle, Play Console declarations, and final
physical-device testing.

## Repository and license

The canonical repository is
[github.com/bzhangj13zzz/touchpause](https://github.com/bzhangj13zzz/touchpause).

TouchPause is licensed under the
[GNU General Public License, version 3 or later](LICENSE). It is based on Snowy,
Copyright (C) 2022 N. Melih Sensoy. Complete attribution and the dated
modification notice are in [NOTICE.md](NOTICE.md).
