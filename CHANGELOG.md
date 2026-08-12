# Changelog

This file records user-visible and compatibility changes to TouchQuell.

## [Unreleased]

No changes recorded yet.

## [1.0.0] - 2026-08-12

### Added

- Rootless touchscreen blocking on Android 14 and newer through an explicitly
  enabled Accessibility service.
- Volume Up and Volume Down hardware release paths that consume the complete
  release gesture.
- A minimal setup screen centered on the Quick Settings tile.
- Quick Settings tile setup, state subtitles, and dedicated TouchQuell artwork.
- Root-backend ownership tokens, readiness reporting, and protected completion
  feedback.
- User, privacy, security, recovery, build, and architecture documentation.

### Changed

- Rebranded the substantially modified Snowy derivative as TouchQuell with the
  tagline “Freeze touch. Keep everything else moving.”
- Changed the application ID to `io.github.bzhangj13zzz.touchquell`; TouchQuell
  therefore installs alongside Snowy and does not migrate its preferences.
- Renamed the native helper to `touchquell-input.so` and its single-instance
  lock to `touch-blocker.lock`.
- Retained root blocking for API 24–33, Power-key release, and rootless safety
  conflicts.
- Updated the Android build to API 34-era tooling and 16 KiB-compatible native
  artifacts.

### Fixed

- Release-key discovery across duplicate and composite Android input devices.
- Stale PID handling and races between rapid tile taps, old root exits, and a
  newer active backend.
- Failure paths that could leave advisory tile state out of sync with the
  actual input owner.

### Attribution

- Preserved Snowy and blockevent copyrights and their GPL-3.0-or-later and
  Apache-2.0 terms. See [NOTICE.md](NOTICE.md).
