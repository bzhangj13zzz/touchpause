# Changelog

This file records user-visible and compatibility changes to TouchPause.

## [Unreleased]

### Added

- Ten successfully started touch-pausing sessions for every installation.
- A one-time Google Play `lifetime_access` purchase with offline entitlement
  caching and purchase restoration.
- Trial and lifetime-access status in the minimal settings screen.

### Changed

- The Quick Settings tile opens lifetime access after the trial is exhausted,
  while always allowing an active block to be released.
- Privacy and store disclosures now distinguish on-device touch processing from
  Google Play's optional purchase processing.

## [1.0.0] - 2026-08-16

### Added

- Rootless touchscreen and stylus-input blocking on Android 14 and newer through
  an explicitly enabled Accessibility service.
- Volume Up and Volume Down hardware release paths that consume the complete
  release gesture.
- A minimal setup screen centered on the Quick Settings tile.
- Quick Settings tile setup, state subtitles, and dedicated TouchPause artwork.
- Complete English, Spanish, French, German, Brazilian Portuguese, Japanese,
  Korean, Simplified Chinese, and Traditional Chinese interfaces.
- A system-aware in-app language picker.
- Versioned, standalone Accessibility disclosure and affirmative consent.
- Play listing copy, a Play icon, and a localization-neutral feature graphic.
- Root-backend ownership tokens, readiness reporting, and protected completion
  feedback.
- User, privacy, security, recovery, build, and architecture documentation.

### Changed

- Rebranded the substantially modified Snowy derivative as TouchPause with the
  tagline “Pause touch. Keep everything running.”
- Changed the application ID to `io.github.bzhangj13zzz.touchpause`; TouchPause
  therefore installs alongside Snowy and earlier TouchQuell test builds and
  does not migrate their preferences.
- Renamed the native helper to `libtouchpause-input.so` and its single-instance
  lock to `touch-blocker.lock`. The `lib` prefix preserves extraction on older
  Android package installers.
- Retained root blocking for API 24–33, Power-key release, and rootless safety
  conflicts.
- Updated the Android build to target API 36 with current JDK 17-compatible
  tooling and 16 KiB-compatible native artifacts.

### Fixed

- Release-key discovery across duplicate and composite Android input devices.
- Stale PID handling and races between rapid tile taps, old root exits, and a
  newer active backend.
- Failure paths that could leave advisory tile state out of sync with the
  actual input owner.

### Attribution

- Preserved Snowy and blockevent copyrights and their GPL-3.0-or-later and
  Apache-2.0 terms. See [NOTICE.md](NOTICE.md).
