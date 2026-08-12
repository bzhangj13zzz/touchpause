# TouchQuell privacy notice

Effective: 2026-08-12

TouchQuell is designed to block touchscreen input locally. It has no account,
advertising, analytics, telemetry, or app-operated server.

## Data collection and network access

TouchQuell does not collect, sell, share, or transmit personal data. The app
does not declare Android's `INTERNET` permission and does not make network
requests.

The repository and build toolchain may contact dependency hosts when a
developer builds the app. That developer activity is separate from the
installed Android application.

## Data stored on the device

TouchQuell stores a small set of preferences in its private app data:

- selected release key;
- whether Android has observed the Quick Settings tile;
- message and vibration choices; and
- short-lived advisory backend state and random ownership tokens.

The root backend also uses
`/data/user/0/io.github.bzhangj13zzz.touchquell/files/touch-blocker.lock` as a
single-instance lock and PID record. It is not a history of touch or key input.

TouchQuell does not implement its own backup or synchronization. This build
disables Android backup and excludes app storage from cloud backup and
device-to-device transfer. Clearing app data or uninstalling TouchQuell removes
its local preferences; a running root helper must be stopped first or the
device rebooted.

## Accessibility service

On Android 14 and newer, the user may explicitly enable TouchQuell's
Accessibility service for rootless operation.

The service:

- declares that it cannot retrieve window content;
- requests touchscreen motion delivery only while blocking is active;
- discards received motion events instead of storing or transmitting them;
- may receive hardware-key events so it can recognize and consume the selected
  release key during an active block;
- does not record key presses; and
- ignores unrelated Accessibility events.

Raw touchscreen motion can include coordinates and timing. TouchQuell receives
that data only as necessary to prevent Android from delivering the same motion
to other apps, then discards it in memory.

Android controls the Accessibility settings screen and may show additional
system disclosures. Disabling the service prevents use of the rootless backend.

## Root fallback

Root mode is used only when the selected configuration requires it: Android
7.0–13, the Power release key, or an unavailable rootless safety condition. The
device's external root manager decides whether to grant `su` access.

While running, `touchquell-input.so` enumerates `/dev/input/event*`, reads the
touchscreen and candidate release-key devices, and holds an exclusive
touchscreen grab. Events are processed in memory to block touch and detect the
release key. They are not retained or transmitted.

Root managers and Android system services are separate software with their own
privacy practices.

## Changes and questions

Material changes will update this file and its effective date. Questions can
be raised with the repository owner through the private project at
[github.com/bzhangj13zzz/touchquell](https://github.com/bzhangj13zzz/touchquell).
