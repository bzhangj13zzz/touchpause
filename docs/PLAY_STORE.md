# Play Store readiness

This checklist separates work that is verifiable in the repository from work
that requires the owner's Play Console, signing identity, public web presence,
or physical devices. Recheck current Play policy immediately before every
submission.

## Implemented in the app

- application ID `io.github.bzhangj13zzz.touchpause`, version code 1;
- target and compile SDK 36, minimum SDK 24;
- Android App Bundle build support and environment-only upload signing;
- Android 14+ rootless operation with `isAccessibilityTool=false`;
- a standalone input-access disclosure and versioned affirmative consent;
- no window-content access, developer analytics, ads, or developer telemetry;
- privacy policy access from the About dialog;
- Android backup and device transfer disabled;
- nine complete app locales and an app-language picker, with all translations
  kept in the base install so selection also works offline;
- 10 free successful sessions followed by an optional non-consumable
  `lifetime_access` Google Play purchase;
- four native ABIs with 16 KiB-aligned ELF load segments; and
- localized Play listing text for all nine app languages, plus a Play icon,
  localization-neutral feature graphic, and four current phone screenshots
  under `fastlane/metadata/android`.

## Required before the first upload

1. Finalize the package name. A Play package name is permanent after app
   creation.
2. Set the app itself to **Free** and create an active non-consumable one-time
   product with ID `lifetime_access` and the intended regional prices. Once an
   app has been offered free it cannot become a paid download under the same
   package name.
3. Create an upload key outside this repository, set the four
   `TOUCHPAUSE_UPLOAD_*` environment variables documented in
   [BUILDING.md](BUILDING.md), and build `bundleRelease`.
4. Enroll in Play App Signing and retain secure backups of any key material the
   owner controls.
5. Make the comprehensive privacy policy URL public, stable, non-geofenced, and
   accessible without login. Add the same URL in Play Console.
6. Publish the exact Corresponding Source for the distributed GPL build, or
   provide every recipient another GPL-compliant source offer. The current
   private repository alone is not sufficient for Play users who cannot access
   it.
7. Supply a public support email and the developer identity shown on the store
   listing. Add those contact details to the privacy policy.
8. Recheck the four supplied screenshots against the signed release candidate
   and recapture any screen whose visible UI has changed.
9. Generate APKs from the final signed AAB with `bundletool`; verify signatures,
   resources, all required ABIs, and 16 KiB alignment; install those generated
   APKs on representative devices.

## Play Console declarations

- Accessibility: classify TouchPause as **not** an accessibility tool, choose
  app functionality as the purpose, and describe transient local touchscreen
  and stylus motion plus hardware-key processing. Explain that only the selected
  volume key is consumed, unrelated keys are forwarded, and all events are
  immediately discarded. Provide the required demonstration video showing the
  disclosure, both consent choices, service enablement, tile use, and hardware
  release.
- Data safety: submit the form even though TouchPause sends no data to the
  developer and shares no touch input. Google Play processes payment data;
  TouchPause receives purchase status and stores only the local trial count
  and cached access boolean. Billing Library 9.1.0 also declares `INTERNET`
  and `ACCESS_NETWORK_STATE` and may send billing requests or diagnostics to
  Google. Review the current Billing SDK disclosure and decide whether Play's
  form requires purchase history and diagnostics for this exact integration.
- Ads: declare no ads while the source remains unchanged.
- Health and financial features: submit both declarations even when every
  answer is "no features."
- Government affiliation and Advertising ID: declare neither while the source
  and ownership remain unchanged.
- Complete content rating, target audience, app access/reviewer instructions,
  and the privacy-policy section.
- Confirm that the package is registered to the verified developer identity
  before the applicable September 30, 2026 deadline. New Play apps are normally
  registered during creation, but the Console status still needs checking.
- Reviewer instructions should use Android 14+, select a volume key, accept the
  disclosure, enable TouchPause, add the Quick Settings tile, pause touch, and
  release it with that key. Root is not required for this review path.
- Explain the optional root fallback and its bundled helper honestly in the
  listing and review notes; do not imply Play's test devices can exercise root.

## Testing tracks and rollout

- Run automated tests, lint, and release-bundle verification on the exact
  candidate commit.
- Test Android 14, 15, and 16; touchscreen and stylus input; gesture and
  three-button navigation; light and dark themes; every locale; another
  key-filtering service; touch exploration; root denial; and a real rooted
  phone for the native fallback.
- Run on a 16 KiB-page 64-bit Android 15/16 image and verify the actual runtime
  page size.
- Use internal testing first, then the account's required closed-testing path.
  Newer personal developer accounts may require at least 12 continuously opted-
  in testers for 14 days before production access.
- Verify a non-rooted Android 10+ physical device in Play Console if the account
  is subject to the personal-account device-verification requirement.
- Stage the production rollout and monitor crashes, ANRs, Accessibility setup
  failures, and release-key failures before expanding availability.

## Listing files still supplied externally

The repository cannot truthfully invent these owner-specific values:

- public developer/support identity and email;
- final privacy-policy hosting;
- upload/app-signing keys;
- Accessibility declaration video;
- Play Console questionnaire answers tied to the owner's account.

Do not mark a release Play-ready until those items and the final device matrix
are complete.

## Official references

- [Target API requirements](https://developer.android.com/google/play/requirements/target-sdk)
- [AccessibilityService policy and declaration](https://support.google.com/googleplay/android-developer/answer/10964491)
- [User Data and privacy-policy requirements](https://support.google.com/googleplay/android-developer/answer/10144311)
- [Data safety guidance](https://support.google.com/googleplay/android-developer/answer/10787469)
- [Android App Bundles](https://developer.android.com/guide/app-bundle)
- [App signing and Play App Signing](https://developer.android.com/studio/publish/app-signing)
- [16 KiB page-size support](https://developer.android.com/guide/practices/page-sizes)
- [Store listing asset requirements](https://support.google.com/googleplay/android-developer/answer/9866151)
- [Developer verification and package registration](https://support.google.com/googleplay/android-developer/answer/16984799)
- [Health apps declaration](https://support.google.com/googleplay/android-developer/answer/14738291)
- [Financial features declaration](https://support.google.com/googleplay/android-developer/answer/13849271)
