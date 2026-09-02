# Google Play submission materials

## App identity

- App name: `Maxfield Link Overlay`
- Package name: `io.github.xclear0.maxfieldoverlay`
- App category: `Tools`
- App or game: `App`
- Pricing: `Free`
- Default store listing: `Chinese (Simplified) — zh-CN`
- Privacy Policy: <https://xclear0.github.io/Maxfield-Link-Overlay/privacy.html>
- Source and support: <https://github.com/XClear0/Maxfield-Link-Overlay>
- Developer email: enter a monitored, verified support email in Play Console (required).

The localized title, short description, full description, changelog, icon, feature graphic, and phone screenshots are stored under `fastlane/metadata/android/`.

## App content answers

- Ads: No.
- App access: All functionality is available without an account or login.
- Data collection: No user data is collected.
- Data sharing: No user data is shared.
- Account creation: The app does not create or manage user accounts.
- Internet access: The app does not request the Internet permission.
- Target audience: The app is not designed for children; select only the age groups that accurately describe the intended audience.
- News, health, financial, government, or dating app: No.

Suggested target-audience selection: `18 and over`, unless the actual intended audience is broader. Do not select age groups under 13 because this utility is not designed for children.

Content-rating answers: no violence, sexual content, offensive language, controlled substances, gambling, purchases, user-generated content, or user-to-user communication. The app does not share the user's precise location and does not contain unrestricted web browsing.

Imported Maxfield plans are selected with Android's system document picker, parsed on the device, and stored in private local storage. Cloud backup is disabled. Clearing app storage or uninstalling the app deletes its saved plan and preferences.

## Foreground service declaration

Type: `specialUse`

Functionality:

> The user explicitly starts a persistent, interactive overlay that shows the current origin portal, destination portal, assigned agent, and progress while another app is in the foreground. The foreground service keeps this user-visible overlay available until the user stops it from the app or its persistent notification.

Impact if deferred:

> The requested overlay would not appear when the user switches to the game or another app, so the current plan instruction would not be available at the time it is needed.

Impact if interrupted:

> The visible overlay disappears and the user temporarily loses access to the current instruction. The selected plan and current progress remain stored locally, and the user can explicitly restart the overlay.

Review video checklist:

1. Open the app and import a sample Maxfield plan.
2. Grant the "display over other apps" permission.
3. Tap the button that starts the overlay.
4. Switch to another app and show the visible overlay.
5. Use the previous and next controls, including the origin-change color cue.
6. Stop the overlay from the persistent notification.

Upload the video to YouTube as unlisted and paste its URL into the Play Console foreground-service declaration.

## Store listing assets

- High-resolution app icon: `fastlane/metadata/android/<locale>/images/icon.png` (512 × 512)
- Feature graphic: `fastlane/metadata/android/<locale>/images/featureGraphic.png` (1024 × 500)
- Phone screenshots: `fastlane/metadata/android/<locale>/images/phoneScreenshots/` (4 images, 1080 × 1920)
- Localized title and descriptions: `fastlane/metadata/android/<locale>/`

The English listing currently uses screenshots of the app's Chinese interface because the app UI is Chinese. Use `zh-CN` as the default listing so the screenshots accurately represent the installed app.

## Release bundle

Build a signed bundle with:

```powershell
$env:ANDROID_HOME="$env:LOCALAPPDATA\Android\Sdk"
.\gradlew.bat clean verifyReleaseSigning bundleRelease
```

Expected output: `app/build/outputs/bundle/release/app-release.aab`.

Before the first Play rollout, decide whether Play should use the existing app signing key. Providing the existing key preserves signature compatibility with GitHub releases; accepting a Google-generated key creates a separate Play signing identity.

## Console completion checklist

1. Create the app with package name `io.github.xclear0.maxfieldoverlay` and complete the main store listing.
2. Add the public privacy-policy URL and a monitored developer contact email.
3. Complete Ads, App access, Data safety, Target audience, Content rating, and Foreground service declarations using the answers above.
4. Upload the signed AAB to Internal testing first and resolve every Play pre-launch warning.
5. If this is a personal developer account subject to the testing requirement, run the required closed test before requesting production access.
6. Add release notes from `fastlane/metadata/android/<locale>/changelogs/4.txt`, choose countries/regions, and submit the rollout for review.
