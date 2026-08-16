# TouchPause architecture

## Design goals

TouchPause has one job: stop touchscreen input temporarily while preserving a
dependable, non-touch escape path. The implementation favors a small number of
Android components and explicit ownership over background orchestration.

The primary invariants are:

1. Never announce an active block before the selected backend owns capture.
2. Never start rootless capture unless its hardware release key is guaranteed.
3. Restore normal input before announcing release.
4. Do not restore blocking after process death, service restart, or reboot.
5. Let an old backend exit clear only the state it owns.

## Component overview

```text
Quick Settings tile
        |
        +-- API 34+ and safe volume release --> transparent toggle activity
        |                                      --> Accessibility service
        |
        +-- root-required configuration ------> root controller --> su
                                                          |
                                                          v
                                                libtouchpause-input.so
```

The launcher activity hosts the small setup and preferences screen. The Quick
Settings tile is the normal runtime entry point. Shared preference helpers hold
configuration and advisory UI state; they do not own an input grab.

## Backend selection

Each tile click selects a backend from current conditions rather than a stored
mode switch:

1. API 34+ may use Accessibility when the release key is Volume Up or Volume
   Down, the current disclosure has been accepted, the service is connected,
   touch exploration is off, no other service owns key filtering, and no root
   invocation is pending.
2. Power release always selects the root backend.
3. API 24–33 selects the root backend.
4. An API 34+ safety conflict may select the disclosed root fallback.

This keeps persisted state from overriding current Android capabilities.

## Rootless flow

Android requires a `PendingIntent` when a Quick Settings tile starts an
activity on recent releases. A transparent, no-history activity collapses the
status shade and asks the already connected Accessibility service to toggle.

To start blocking, the service:

1. saves its previous motion-event source mask and service flags;
2. adds touchscreen, stylus, and Bluetooth-stylus sources and requests key
   filtering through the API 34 Accessibility service API;
3. applies and verifies both configuration changes;
4. freezes the release key and feedback settings for this session; and
5. publishes advisory active state and user feedback.

Touchscreen and stylus motion delivered to the service is intentionally
discarded. Android withholds those requested sources from other consumers,
which creates the block without drawing an overlay. Capturing all three source
constants is necessary because a single Android input device can advertise
touchscreen and stylus capabilities together.

The service consumes the actionable down event for the selected volume key and
also consumes its matching up event if Android delivers it before filtering is
restored. This releases touch without changing media volume. It then restores
the prior motion-source mask and service flags, clears only
Accessibility-owned state, and shows configured feedback. A stale matching-up
latch is cleared before every new block so a missing up event cannot affect the
next session.

If capture setup fails, the service restores its previous input configuration.
It disables itself only if that restoration also fails, ensuring Android
removes its input filter. A service enabled directly in Android Settings is
also disabled before it registers listeners when the in-app disclosure has not
been accepted. Touch-exploration and enabled-service listeners stop an active
block if the release-key guarantee changes.

## Root flow

The app validates the configured trigger, creates a random invocation token,
and reserves a pending-root marker before launching one complete, shell-quoted
command through `su -c`.

The packaged `libtouchpause-input.so` helper:

1. opens `touch-blocker.lock` and attempts a kernel `fcntl` write lock;
2. if another owner holds the lock, identifies that owner with `F_GETLK` and
   sends it `SIGINT` instead of starting a second grab;
3. scans `/dev/input/event*` for every direct touchscreen and all devices
   capable of emitting the selected release key;
4. takes `EVIOCGRAB` on every discovered touchscreen;
5. writes a readiness line only after capture succeeds;
6. polls input until the release event, a signal, or a device failure; and
7. releases every grab and the lock during cleanup.

The Android process promotes pending state to active root ownership only after
it observes readiness. When the helper exits, an explicit broadcast protected
by a signature permission clears state for the matching token and delivers
optional stop feedback. The broadcast includes Android's stopped-package flag
so it can reconcile a helper that outlived an app force-stop. That root-shell
broadcast is the process-death recovery path; the process output observer
performs the same reconciliation while the launching Android process remains
alive.

## State and concurrency

Preferences store the active backend, an active root token, and a pending root
token so System UI can render the tile. That state is advisory because a process
can die between a kernel or Android input change and a preference write.
The store records Android's boot count and discards all runtime ownership after
a reboot, because neither input backend can survive one.

Three ownership checks prevent stale callbacks:

- synchronized app-state edits serialize in-process decisions;
- random tokens associate root readiness and exit with one invocation; and
- the native kernel lock identifies the real root owner even if its PID text is
  stale.

Accessibility and root capture are mutually exclusive. Root startup is not
allowed to replace an Accessibility owner, and rootless capture waits while a
root invocation is pending.

## Security boundaries

The exported tile and Accessibility service require Android system binding
permissions. The root completion receiver is explicit and protected by a
signature-level app permission. The transparent toggle activity is not
exported.

The Accessibility configuration does not permit retrieval of window content.
The installed app has no network permission. Root mode has broader device
authority by definition, so its source is vendored, auditable, and rebuilt for
the four packaged Android ABIs.

See [../SECURITY.md](../SECURITY.md) for the threat and recovery model and
[../PRIVACY.md](../PRIVACY.md) for input-data handling.

## Native artifacts

The preferred native source and Apache license live under `app/src/main/cpp`.
`update_lib.sh` compiles `libtouchpause-input.so` for:

- `armeabi-v7a`;
- `arm64-v8a`;
- `x86`; and
- `x86_64`.

The linker uses a 16 KiB maximum page size for compatibility with newer Android
devices. Packaged binaries are generated artifacts; source and the rebuild
script are the reviewable authority.

## Verification strategy

- JVM tests cover release-key parsing, shell quoting, root command construction,
  and token propagation.
- Android lint checks manifest/component and API usage.
- Instrumentation tests cover runtime ownership, consent persistence, and
  localized resources.
- APK checks cover signing, alignment, packaged ABIs, and native ELF alignment.
- Device or emulator tests exercise tile state, raw touchscreen and stylus
  blocking, and hardware-key release. Root behavior must also be tested on a
  rooted device because an emulator's app process is not a substitute for a
  real root manager.
