# TouchPause security and recovery

TouchPause deliberately interferes with the primary input device. Its design
therefore treats a dependable hardware or out-of-band recovery path as a core
security requirement.

## Supported code

Version 1.0.x and the current default branch receive fixes. Historical Snowy
releases and separately modified APKs are outside this project's support scope.

Report a suspected vulnerability privately to the repository owner. Use a
private GitHub security report when that feature is enabled; otherwise contact
the owner without posting exploit details in a public venue.

## Trust boundaries

### Rootless backend

On API 34+, Android's Accessibility framework owns the input filter.
TouchPause requires its versioned in-app disclosure to be accepted before the
service remains enabled. It requests touchscreen and stylus motion plus key
filtering only after it confirms that the selected volume key can reach its
service, and only for an active pause. It restores the previous motion-source
mask and service flags before reporting that blocking stopped.

Rootless capture is rejected when:

- the release key is Power;
- the current Accessibility disclosure has not been accepted;
- touch exploration is active;
- another enabled Accessibility service owns hardware-key filtering;
- a root invocation is being reconciled; or
- the TouchPause service is not connected and ready.

Service interruption, force-stop, or reboot removes the Android input filter.
TouchPause does not automatically restore blocking. A boot-count marker also
clears stale advisory tile ownership after reboot.

### Root backend

Root is not an Android manifest permission. TouchPause starts `su`, and the
device's root manager grants or rejects that request. A granted session runs the
vendored `libtouchpause-input.so` helper with root privileges.

The helper:

- enumerates Linux input nodes and classifies the direct touchscreen;
- monitors every eligible node for the configured release key;
- uses `EVIOCGRAB` only on the touchscreen nodes it blocks;
- owns an advisory PID file through a kernel `fcntl` lock at
  `touch-blocker.lock`;
- treats a second valid invocation as a request to signal the current owner;
  and
- releases all grabs on a trigger, handled termination signal, input failure,
  or normal exit.

The root process can outlive the Android app process. App state is advisory;
the kernel lock is authoritative. Random invocation tokens stop an old process
exit from clearing the state of a newer session. A signature-protected,
explicit broadcast returns completion state to the app.

Only app-owned paths and a validated release-key value are passed to the root
shell, and values are shell-quoted. Even with those controls, approving root
for an APK gives that binary significant authority. Use builds produced from a
reviewed commit and a signing key you trust.

## Recovery order

1. Press the configured Volume Up, Volume Down, or Power key.
2. If an external mouse is available and not part of a grabbed touchscreen,
   use it to toggle the Quick Settings tile off.
3. Reboot the phone. Blocking is not restored after boot.
4. If USB debugging was authorized before the incident, use the backend-specific
   command below.

Stop the Accessibility-backed app process:

```sh
adb shell am force-stop io.github.bzhangj13zzz.touchpause
```

The native root helper survives an app force-stop. Signal only processes whose
executable is the packaged TouchPause helper:

```sh
adb shell su -c 'for d in /proc/[0-9]*; do case "$(readlink "$d/exe")" in */libtouchpause-input.so|*/libtouchpause-input.so\ \(deleted\)) kill -INT "${d##*/}";; esac; done'
```

This deliberately checks `/proc/<pid>/exe` instead of trusting PID text in the
lock file, which can be stale after an abnormal exit. Reboot remains the
universal recovery.

## Signing and secrets

- Debug APKs are for development and private testing, not trusted production
  distribution.
- Never commit a release keystore, its passwords, `local.properties`, or local
  SDK paths.
- An update must use the same application ID and signing certificate as the
  installed TouchPause build.
- Preserve the exact source commit and native sources used for every APK shared
  with another person.

## Purchase boundary

TouchPause uses Google Play Billing only for the non-consumable lifetime
unlock. It does not accept payment details itself and has no developer server
or app account. The client grants access only for a `PURCHASED` item, asks
Google Play to acknowledge new purchases, and refreshes ownership when settings
connects. The last successful entitlement is cached so a paid user can operate
offline.

Google recommends server-side verification for stronger fraud, refund, and
revocation handling. TouchPause deliberately accepts the lower assurance of a
client-only implementation for this low-cost utility rather than introducing a
server and user identity system. Commercial gating runs only before a new block
starts; it never blocks a release action or tears down an active session.
