# TouchPause

*Pause touch. Keep everything running.*

TouchPause is an Android Quick Settings tool that temporarily blocks touchscreen
input. Its launcher screen is intentionally small: use it to complete setup,
choose a hardware release key, and adjust feedback. Day-to-day control belongs
in the Quick Settings tile.

Every installation includes 10 successfully started touch-pausing sessions.
After those sessions, Google Play offers a one-time lifetime unlock; there are
no subscriptions, ads, or developer-operated accounts.

TouchPause 1.0.0 uses the application ID
`io.github.bzhangj13zzz.touchpause`. It is a substantially modified and
rebranded derivative of Snowy; see [NOTICE.md](NOTICE.md) for its history and
attributions.

## Compatibility

| Android version | Touch blocking | Root required |
| --- | --- | --- |
| Android 14 or newer (API 34+) | Accessibility backend with Volume Up or Volume Down release | No |
| Android 14 or newer with Power release, touch exploration, or a key-filter conflict | Native fallback | Yes |
| Android 7.0–13 (API 24–33) | Native backend | Yes |
| Older than Android 7.0 | Unsupported | — |

The root backend also depends on a compatible `su` implementation and approval
from the device's root manager.

## Install and migrate

No public APK location is assumed. Build and sign an APK locally, or install one
provided through a trusted private channel.

TouchPause installs alongside Snowy and earlier TouchQuell test builds because
each has a different application ID. Their settings are not imported. Remove
an old app and its Quick Settings tile manually when you no longer need them;
otherwise Android may show multiple tiles.

An APK can update an existing TouchPause installation only when both APKs use
the same application ID and signing key.

## Set up rootless mode on Android 14+

1. Open TouchPause.
2. Select **Volume Up** or **Volume Down** as the release key.
3. Open **Rootless setup**, read the standalone input-access disclosure, and
   choose **Agree and continue**. Then enable TouchPause in Android's
   Accessibility settings.
4. Return to TouchPause and tap **Add TouchPause tile**. If Android does not
   show the add-tile prompt, swipe down twice, choose **Edit**, and drag the
   tile into the active area.
5. Tap the tile once to pause touch. Press the configured volume key to
   release it.

Android may restrict Accessibility for an APK installed outside an app store.
If TouchPause is unavailable or disabled in the Accessibility list, open
**App info → More/overflow menu → Allow restricted settings**, then return to
Accessibility. Names and locations vary slightly between device makers.

TouchPause does not retrieve window content. After consent, the Accessibility
service requests touchscreen and stylus motion plus hardware-key filtering only
while a pause is active. It receives hardware-key events only to detect the
selected volume key and forwards unrelated keys. Every event is handled in
memory and immediately discarded. See [PRIVACY.md](PRIVACY.md) for the full
disclosure.

## Set up the root fallback

The root fallback is used on API 24–33, for the Power release key, or when the
rootless backend cannot guarantee a safe release key. Tapping the tile in one of
those configurations can display a root-manager prompt.

Approve access only for a build you trust. The native helper
`libtouchpause-input.so` runs as root for the blocking session, finds the
touchscreen under `/dev/input`, takes an exclusive Linux `EVIOCGRAB`, and watches
eligible input devices for the configured release key. It does not make network
requests.

On an unrooted device, a configuration that requires this backend fails without
blocking touch.

## Everyday use

- Tap the inactive Quick Settings tile to pause touchscreen input.
- Press the configured hardware release key to restore touch. Do not rely on an
  on-screen control as the primary escape path.
- An external mouse that is not part of a grabbed touchscreen may still be able
  to reach the tile and toggle it off.
- Blocking is never restored automatically after a reboot or service restart.

Touch exploration and hardware-key filtering are shared Android facilities. If
touch exploration is active, or another enabled Accessibility service already
owns hardware-key filtering, TouchPause does not start rootless capture and may
use the disclosed root fallback instead.

## Trial and lifetime access

TouchPause counts a trial session only after a backend has successfully started
blocking touch. Failed root requests, setup screens, and stopping an active
block do not consume a session. The release key always remains available,
including on the tenth session.

After 10 successful starts, the Quick Settings tile opens the lifetime-access
screen instead of beginning another block. The unlock is a non-consumable,
one-time Google Play product named `lifetime_access`, not a subscription.
Google Play restores ownership on another device using the purchasing Google
account. TouchPause caches the entitlement for offline use and stores the local
trial count in private app data. Clearing app data can reset the local trial;
preventing that would require a developer server, which this project
deliberately avoids.

## Emergency recovery

Try the configured release key first. If that fails:

1. Reboot the phone. Neither backend restores blocking after reboot.
2. With USB debugging already authorized, stop the rootless backend:

   ```sh
   adb shell am force-stop io.github.bzhangj13zzz.touchpause
   ```

3. The root helper can outlive the Android app process. With root access, stop
   only processes whose executable is the packaged TouchPause helper:

   ```sh
   adb shell su -c 'for d in /proc/[0-9]*; do case "$(readlink "$d/exe")" in */libtouchpause-input.so|*/libtouchpause-input.so\ \(deleted\)) kill -INT "${d##*/}";; esac; done'
   ```

   This validates the executable instead of trusting the potentially stale PID
   text in the lock file. Reboot remains the universal recovery path.

More detail is available in [SECURITY.md](SECURITY.md).

## Build and test

The Android app requires JDK 17 and Android SDK Platform 36. Rebuilding the
vendored native helper additionally requires Android NDK r28 or newer.

```sh
export JAVA_HOME=/path/to/jdk-17
export ANDROID_HOME=/path/to/android-sdk
./gradlew clean testDebugUnitTest lintDebug assembleDebug
```

To rebuild `libtouchpause-input.so` for all packaged ABIs first:

```sh
ANDROID_NDK_HOME=/path/to/android-ndk ./update_lib.sh
./gradlew assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`. See
[docs/BUILDING.md](docs/BUILDING.md) for signing, native builds, and artifact
handling. See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for the backend and
safety design.

## Languages

TouchPause follows the system language by default and also offers an in-app
language picker. The interface and safety disclosures are available in English,
Spanish, French, German, Brazilian Portuguese, Japanese, Korean, Simplified
Chinese, and Traditional Chinese.

## Play Store preparation

The repository includes Play listing text, icon artwork, and a feature graphic.
Publishing still requires a public privacy-policy URL, an upload key, a signed
Android App Bundle, Play Console declarations, and device testing. The exact
completed and external steps are tracked in
[docs/PLAY_STORE.md](docs/PLAY_STORE.md).

## Project repository

The canonical repository is
[github.com/bzhangj13zzz/touchpause](https://github.com/bzhangj13zzz/touchpause).
Access follows the repository's current visibility settings.

## License and credits

TouchPause is licensed under the
[GNU General Public License, version 3 or later](LICENSE). It is based on Snowy,
Copyright (C) 2022 N. Melih Sensoy.

The vendored and modified `blockevent` helper remains under the Apache License
2.0. Its license is in
[app/src/main/cpp/LICENSE.blockevent](app/src/main/cpp/LICENSE.blockevent).
Complete attribution and the dated modification notice are in
[NOTICE.md](NOTICE.md).
