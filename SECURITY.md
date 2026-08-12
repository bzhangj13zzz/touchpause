# TouchQuell security and recovery

TouchQuell deliberately interferes with the primary input device. Its design
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
TouchQuell requests touchscreen motion only after it confirms that the selected
volume key can reach its service. It restores the previous motion-source
configuration before reporting that blocking stopped.

Rootless capture is rejected when:

- the release key is Power;
- touch exploration is active;
- another enabled Accessibility service owns hardware-key filtering;
- a root invocation is being reconciled; or
- the TouchQuell service is not connected and ready.

Service interruption, force-stop, or reboot removes the Android input filter.
TouchQuell does not automatically restore blocking.

### Root backend

Root is not an Android manifest permission. TouchQuell starts `su`, and the
device's root manager grants or rejects that request. A granted session runs the
vendored `touchquell-input.so` helper with root privileges.

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
2. If an uncaptured mouse or stylus is available, use it to toggle the Quick
   Settings tile off.
3. Reboot the phone. Blocking is not restored after boot.
4. If USB debugging was authorized before the incident, use the backend-specific
   command below.

Stop the Accessibility-backed app process:

```sh
adb shell am force-stop io.github.bzhangj13zzz.touchquell
```

The native root helper survives an app force-stop. Signal only processes whose
executable is the packaged TouchQuell helper:

```sh
adb shell su -c 'for d in /proc/[0-9]*; do case "$(readlink "$d/exe")" in */touchquell-input.so|*/touchquell-input.so\ \(deleted\)) kill -INT "${d##*/}";; esac; done'
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
  installed TouchQuell build.
- Preserve the exact source commit and native sources used for every APK shared
  with another person.
