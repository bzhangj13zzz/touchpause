# Security policy

## Supported versions

Only the latest TouchPause release is supported. TouchPause requires Android 14
or newer.

## Reporting a vulnerability

Do not open a public issue for a vulnerability that could leave input blocked,
expose Accessibility data, or bypass purchase state. Before publication, add a
monitored security contact to the GitHub repository and Play listing. Until
then, contact the repository owner privately through GitHub.

Include the app version, Android version, device model, reproduction steps,
whether another Accessibility service was enabled, and any relevant logs. Do
not include private screen content or unrelated user data.

## Security and recovery model

TouchPause is completely rootless. It contains Kotlin/Java Android code only,
does not execute shell commands, and does not access `/dev/input` directly.

Its user-enabled Accessibility service uses Android 14's motion-source capture
API. Capture is enabled only after all of these checks pass:

- the in-app input-access disclosure has been accepted;
- the service is connected;
- touch exploration is off;
- no competing Accessibility service is filtering hardware keys; and
- the trial, lifetime, or signed store-review entitlement allows another
  session.

The service cannot retrieve window content. During an active pause it receives
touchscreen/stylus motion and hardware-key events, discards all input events in
memory, forwards unrelated hardware keys, and uses only the selected volume key
to restore normal input.

The app restores the service's prior motion-source and flag configuration
before reporting a pause as stopped. If restoration fails, it disables its own
Accessibility service so Android removes the input filter. Process death,
force-stop, disabling the service, or reboot also removes capture; sessions are
never restored automatically.

Emergency recovery:

1. Press the selected Volume Up or Volume Down release key.
2. Reboot the device with the physical power button.
3. If USB debugging was already authorized, run:

   ```sh
   adb shell am force-stop io.github.bzhangj13zzz.touchpause
   ```

## Purchase integrity

The 10-session counter and cached entitlement are local usability state, not a
security boundary. Google Play is authoritative for the non-consumable
`lifetime_access` purchase. TouchPause verifies active purchases through Play
Billing and acknowledges successful purchases. It deliberately has no server,
so clearing app data can reset the local trial count; this is an accepted
privacy-versus-enforcement tradeoff.

Store-review access uses an Ed25519 signature. The APK contains only the public
verification key; the private signing key and reusable reviewer code must stay
outside the repository and be backed up securely. If the reviewer code is
disclosed publicly, rotate the embedded public key, generate a replacement
code, and upload a new build before review.

## Release hygiene

- Never commit signing keys, passwords, Play service-account credentials, or
  private contact data. This includes the store-review private key and code.
- Build releases from a clean, reviewed commit with JDK 17.
- Sign the App Bundle with the registered upload key and use Play App Signing.
- Confirm the final bundle contains no native libraries or shell/root code.
- Publish the corresponding GPL source for every distributed APK or bundle.
