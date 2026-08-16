# Notices and attribution

## TouchPause

TouchPause is a substantially modified and rebranded derivative of Snowy.

- TouchPause modifications: Copyright (C) 2026 bzhangj13zzz
- Original project: [Snowy](https://github.com/nmelihsensoy/snowy)
- Original copyright: Copyright (C) 2022 N. Melih Sensoy
- License: GNU General Public License, version 3 or later

The TouchPause name, application ID, user interface, rootless Android 14
backend, state ownership, recovery behavior, native integration, build, and
documentation materially differ from the original project.

**Modification notice:** the original work was substantially modified and
rebranded as TouchPause on 2026-08-16. The original author is not affiliated
with, responsible for, or endorsing this derivative.

The complete GPLv3 terms are in [LICENSE](LICENSE).

## blockevent native helper

TouchPause includes a modified version of
[blockevent](https://github.com/nmelihsensoy/blockevent):

- Copyright (C) 2022 N. Melih Sensoy
- License: Apache License 2.0
- Local license: [app/src/main/cpp/LICENSE.blockevent](app/src/main/cpp/LICENSE.blockevent)
- Packaged binary name: `libtouchpause-input.so`

**Modification notice (2026-08-12 and 2026-08-16):** the vendored helper was
changed for multi-node release-key and direct-touchscreen discovery, safe
single-instance locking through `touch-blocker.lock`, readiness signaling,
fail-safe input polling and cleanup, and Android 16 KiB page-compatible builds.

The original copyright and Apache license apply to this component. The
repository includes its preferred source form and the script used to rebuild
the packaged ABI binaries.

## Android and build dependencies

TouchPause uses AndroidX, Material Components for Android, Kotlin, and the
Gradle wrapper. These projects are distributed under their respective terms;
the directly used components are predominantly licensed under Apache License
2.0. Their names identify their upstream projects and do not imply endorsement.

Android is a trademark of Google LLC.
