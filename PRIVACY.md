# TouchPause privacy notice

Effective: 2026-08-17

TouchPause pauses touchscreen input locally. It has no advertising, developer
analytics, developer account, or developer-operated server.

## Data collection and network access

TouchPause does not collect, sell, share, or transmit personal data to the
developer.

The optional lifetime unlock uses Google Play Billing. The Billing Library
declares `INTERNET` and `ACCESS_NETWORK_STATE` and may exchange billing requests
and diagnostic information with Google under Google's terms. Google Play
returns localized product details and purchase status. TouchPause handles a
purchase token in memory long enough to grant and acknowledge access, but does
not store it or send it to the developer. Touch or key events are never part of
billing traffic.

## Data stored on the device

TouchPause stores these values in private app data:

- selected volume release key;
- whether Android has observed the Quick Settings tile;
- message and vibration choices;
- selected app language;
- the version of the Accessibility disclosure accepted by the user;
- number of successfully started free sessions;
- cached Google Play lifetime-access status; and
- short-lived advisory state indicating that a pause is active.

TouchPause disables Android backup and excludes app storage from cloud backup
and device-to-device transfer. Clearing app data or uninstalling removes local
preferences and the trial count. Google Play can restore a lifetime purchase
for the purchasing account.

## Accessibility service and input data

TouchPause requires Android 14 or newer. After the user accepts the in-app
disclosure and explicitly enables the service in Android settings, the service:

- cannot retrieve window content;
- requests touchscreen and stylus motion plus hardware-key filtering only
  while a user-started pause is active;
- prevents other apps from receiving the captured touchscreen and stylus
  motion;
- receives all hardware-key events during that pause, uses them only to detect
  the selected volume release key, and forwards unrelated keys;
- processes all input only in memory on the device; and
- immediately discards every received input event without storing or
  transmitting it.

Raw touchscreen and stylus motion can include coordinates, timing, pressure,
and tool information. TouchPause receives those fields only because Android
must route the motion to the Accessibility service in order to withhold it from
other apps.

TouchPause does not request root access and packages no native input helper.
Disabling its Accessibility service prevents all touch-pausing functionality.
Stopping the service or rebooting removes active capture.

## Changes and questions

Material changes update this notice and its effective date. The current policy
is published at
[github.com/bzhangj13zzz/touchpause](https://github.com/bzhangj13zzz/touchpause/blob/main/PRIVACY.md).
That URL must be public before Play publication. The Play listing must also
provide the developer's support and privacy contact address.
