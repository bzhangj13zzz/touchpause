# Closed testing

This document is the operating checklist for TouchPause's first Google Play
closed test. It keeps recruitment, feedback, and the production-access answers
grounded in actual tester activity.

## Tester requirements

- Use a Google account and a device running Android 14 or newer.
- Join through the Google Play opt-in link before installing the app.
- Remain opted in for at least 14 continuous days.
- Exercise the app and provide honest feedback. Merely joining is not useful
  evidence for the production-access application.

Recruit 16–20 people so the test remains above Google's 12-tester minimum if a
few people leave early.

## What to test

1. Install TouchPause from the closed-test Play listing.
2. Open the app and review both choices in the Accessibility disclosure.
3. Enable the TouchPause Accessibility service.
4. Add the TouchPause tile to Quick Settings.
5. Start and release touch pausing with both supported volume-key choices.
6. Confirm video or other on-screen content continues while touch is paused.
7. Confirm unrelated hardware keys continue to work.
8. Exercise the 10-session trial counter without purchasing.
9. Check the settings screen in light and dark mode.
10. Report the device model, Android version, result, and any confusing step.

## Recruitment post

The Google Group is public and self-joinable. The Play link becomes installable
after Google approves and publishes the closed-test release.

> **Android 14+ testers wanted for TouchPause — test-for-test offered**
>
> TouchPause temporarily pauses touchscreen and stylus input while video,
> presentations, and other content keep running. A selected volume key always
> restores touch.
>
> I am looking for Android 14+ testers who can remain opted into the Google Play
> closed test for at least 14 days and provide honest feedback. I will
> reciprocate by genuinely testing your Android app.
>
> Join the tester group: https://groups.google.com/g/touchpause-closed-testers
>
> Opt in and install: https://play.google.com/apps/testing/io.github.bzhangj13zzz.touchpause
>
> Please test Accessibility setup, the Quick Settings tile, touch pause and
> volume-key release, the 10-session trial, and light/dark mode. TouchPause has
> no ads, developer account, or developer analytics.

Suitable communities include `r/AndroidClosedTesting` and
`r/TestersCommunity`. Do not buy bot accounts, promise positive reviews, or
fabricate feedback.

## Feedback questions

- What device and Android version did you use?
- Did the disclosure make the Accessibility access understandable?
- Could you add and find the Quick Settings tile?
- Did touch pause immediately and release on the selected volume key?
- Did any touch, stylus, or key behavior surprise you?
- Was the 10-session trial and lifetime-unlock explanation clear?
- What should be changed before release?

## Tracking

Keep a private list outside the repository with each tester's email address,
opt-in date, device, Android version, latest confirmation, and feedback. Do not
commit tester email addresses or other personal information.

When applying for production access, describe only real recruitment,
engagement, feedback, and changes made during the test.
