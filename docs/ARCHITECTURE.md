# TouchPause architecture

TouchPause is a small Android 14+ application. It intentionally uses Android
framework APIs directly and has no dependency-injection framework, server, root
process, shell command, JNI layer, or native library.

## Component flow

```text
Quick Settings tile
  |-- setup needed ----------> SettingsActivity / SettingsFragment
  |                              |-- disclosure -> Accessibility settings
  |                              `-- trial/lifetime purchase -> Google Play
  |
  `-- ready -----------------> AccessibilityToggleActivity
                                 `-- TouchBlockAccessibilityService
                                      |-- capture touch + stylus motion
                                      |-- filter hardware keys
                                      `-- restore on selected volume key
```

The transparent toggle activity exists because Android requires a
`PendingIntent` to launch UI while collapsing Quick Settings. It immediately
asks the connected service to toggle, reports failure if the service is not
ready, and finishes without animation.

## Main responsibilities

- `settings/SettingsActivity` hosts the minimal preference UI and applies
  edge-to-edge insets.
- `settings/SettingsFragment` owns setup, disclosure, release key, feedback,
  language, trial status, purchase flow, privacy, and add-tile prompting.
- `tile/TouchBlockTileService` is the primary interaction and routes taps to
  setup, purchase, or the toggle activity.
- `accessibility/TouchBlockAccessibilityService` owns all input capture.
- `accessibility/AccessibilityStatus` explains readiness conflicts without
  changing state.
- `preferences/UserPreferences` provides typed user settings.
- `block/BlockSessionStore` shares advisory active state between the service,
  tile, and settings screen.
- `billing/EntitlementStore` owns local trial and cached access state.
- `billing/PlayBillingManager` talks to Google Play Billing.
- `feedback/FeedbackNotifier` emits configured toasts and vibrations.

## Input capture lifecycle

Starting a pause requires explicit disclosure consent, a connected service, no
touch exploration, no competing key filter, and available trial, lifetime, or
store-review access.

The service snapshots its current `AccessibilityServiceInfo`, adds touchscreen,
stylus, and Bluetooth-stylus motion sources, and enables hardware-key filtering.
Android withholds captured motion from other consumers. Only after Android
reports that both capture settings are applied does TouchPause:

1. publish advisory active state;
2. record one successful trial session;
3. refresh the Quick Settings tile; and
4. show configured start feedback.

All received motion events are discarded. Hardware-key events are inspected
only to recognize the selected volume key; unrelated keys are forwarded.

Stopping restores the exact previous motion-source mask and service flags
before clearing advisory state. If restoration throws, the service disables
itself so Android removes the filter. The release-key latch is cleared before
each new session and during disconnect because Android may stop delivering the
previous key's `ACTION_UP` as filtering is removed.

## State and process boundaries

User settings live in Android's default private preferences. Runtime state uses
the named `touchpause_runtime` file. Because Accessibility capture belongs to
the app process and cannot survive process death, stale runtime state is cleared
once when a new app process starts.

The active-state preference is advisory UI state; Android's connected
Accessibility service and its applied service configuration are authoritative.
No session is restored after process death, service disablement, or reboot.

## Trial and billing

`EntitlementStore` permits a start while fewer than 10 sessions have succeeded,
cached lifetime access is true, or signed store-review access is enabled. The
service records usage only after capture is successfully applied. Review
sessions do not consume the trial. The selected release key continues to work
regardless of entitlement state.

Google Play owns the non-consumable `lifetime_access` product. Purchase tokens
are acknowledged but not persisted. The app stores only the successful-session
count and cached entitlement. There is no developer server, so local app-data
clearing can reset the trial by design.

Store reviewers can tap the version row seven times and enter a reusable,
signed review code. `ReviewAccessVerifier` contains only the Ed25519 public key;
the signing key and generated code remain outside the repository. This gives
reviewers full access without a purchase while preventing a plain-text bypass
from being copied out of the APK.

## Exported boundaries

- `SettingsActivity` is exported as the launcher and tile-preferences activity.
- `TouchBlockTileService` is exported only behind
  `android.permission.BIND_QUICK_SETTINGS_TILE`.
- `TouchBlockAccessibilityService` is exported only behind
  `android.permission.BIND_ACCESSIBILITY_SERVICE`.
- The toggle activity is not exported.

No app component accepts shell commands, file paths, PIDs, or native protocol
arguments.

## Verification strategy

- JVM tests cover release-key parsing and entitlement rules.
- Instrumentation tests cover identity, manifest/accessibility metadata,
  locale resources, preferences, runtime state, entitlement persistence, and
  Accessibility consent.
- Lint runs with missing and extra translations fatal.
- Artifact checks confirm API 34 minimum/API 36 target, all nine locales in the
  base bundle, no native libraries, and expected signing state.
- Physical-device testing must cover actual Quick Settings behavior, disclosure
  and service setup, touch/stylus suppression, both volume release keys,
  conflicts, tenth-session behavior, purchase restoration, force-stop, and
  reboot recovery.
