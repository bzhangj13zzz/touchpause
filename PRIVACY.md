# TouchPause privacy notice

Effective: 2026-08-17

TouchPause is designed to block touchscreen input locally. It has no account,
advertising, developer analytics, or app-operated server.

## Data collection and network access

TouchPause does not collect, sell, share, or transmit personal data to the
developer. It has no developer-operated server.

The optional lifetime unlock uses Google Play Billing. The Billing Library
declares Android's `INTERNET` and `ACCESS_NETWORK_STATE` permissions and may
send billing requests and diagnostic information to Google under Google's
terms. Google Play returns localized product details and purchase status to
TouchPause. TouchPause handles a purchase token in memory long enough to grant
and acknowledge access, but does not store it or send it to the developer.
TouchPause stores only the local trial count and a lifetime-access boolean for
offline use. Touch and input events are never included in billing traffic.

The repository and build toolchain may contact dependency hosts when a
developer builds the app. That developer activity is separate from the
installed Android application.

## Data stored on the device

TouchPause stores a small set of preferences in its private app data:

- selected release key;
- whether Android has observed the Quick Settings tile;
- message and vibration choices;
- selected app language;
- the version of the Accessibility disclosure the user accepted;
- the number of successfully started free sessions;
- cached Google Play lifetime-access status;
- short-lived advisory backend state and random ownership tokens; and
- Android's boot-count marker, used only to discard stale runtime ownership
  after a reboot.

The root backend also uses
`/data/user/0/io.github.bzhangj13zzz.touchpause/files/touch-blocker.lock` as a
single-instance lock and PID record. It is not a history of touch or key input.

TouchPause does not implement its own backup or synchronization. This build
disables Android backup and excludes app storage from cloud backup and
device-to-device transfer. Clearing app data or uninstalling TouchPause removes
its local preferences and trial count. A Google Play lifetime purchase can be
restored from the purchasing Google account. A running root helper must be
stopped first or the device rebooted.

## Accessibility service

On Android 14 and newer, the user may explicitly enable TouchPause's
Accessibility service for rootless operation.

After the in-app disclosure is accepted, the service:

- declares that it cannot retrieve window content;
- requests touchscreen and stylus motion delivery plus hardware-key filtering
  only while blocking is active;
- discards received motion events instead of storing or transmitting them;
- receives hardware-key events only to find and consume the selected volume key
  so it can release an active block without also changing volume;
- forwards unrelated hardware keys;
- does not record key presses; and
- ignores unrelated Accessibility events.

Raw touchscreen and stylus motion can include coordinates, timing, pressure,
and tool information. TouchPause receives that data only as necessary to
prevent Android from delivering the same motion to other apps, then discards it
in memory.

Android controls the Accessibility settings screen and may show additional
system disclosures. Disabling the service prevents use of the rootless backend.

## Root fallback

Root mode is used only when the selected configuration requires it: Android
7.0–13, the Power release key, or an unavailable rootless safety condition. The
device's external root manager decides whether to grant `su` access.

While running, `libtouchpause-input.so` enumerates `/dev/input/event*`, reads the
touchscreen and candidate release-key devices, and holds an exclusive
touchscreen grab. Events are processed in memory to block touch and detect the
release key. They are not retained or transmitted.

Root managers and Android system services are separate software with their own
privacy practices.

## Changes and questions

Material changes will update this file and its effective date. The current
privacy-policy location is
[github.com/bzhangj13zzz/touchpause](https://github.com/bzhangj13zzz/touchpause/blob/main/PRIVACY.md).
That URL must be publicly accessible before Play publication. The public Play
listing must also provide the developer's support and privacy contact details.
