VOLUME BOOSTER - SOUND BOOSTER
v1.5 — ADMOB INTEGRATION SOURCE BUILD

WHAT CHANGED IN v1.5
- Integrated Google Mobile Ads SDK 25.4.0.
- Integrated Google User Messaging Platform (UMP) 4.0.0 for privacy/consent handling.
- Replaced the bottom “Loading advertisement…” placeholder with a real anchored adaptive Banner ad container.
- Added an Interstitial ad that can appear only after a natural pause: every 3rd service stop, with an additional 2-minute in-app cooldown.
- Debug builds use Google's official test App ID and test Banner/Interstitial IDs.
- Release builds use the publisher's production AdMob App ID and the two production ad-unit IDs created in AdMob.
- Added INTERNET, ACCESS_NETWORK_STATE, and AD_ID permissions required for advertising functionality.
- Added an AdMob APPLICATION_ID manifest entry.
- Added a “Privacy choices” menu item only when UMP reports that a privacy-options entry point is required.
- Updated the in-app privacy policy for AdMob/UMP and advertising-related data practices.
- App label is now “Volume Booster - Sound Booster”.
- versionCode 6 / versionName 1.5.

IMPORTANT: ADMOB ACCOUNT / APP STATUS
The source integration can be tested immediately with debug builds because debug uses Google's demo ad IDs. Production ad serving can remain limited until the AdMob account/app is approved and the published Play Store listing is linked and reviewed.

IMPORTANT: CONSENT MESSAGE
The UMP SDK is integrated, but the publisher must still configure the appropriate message(s) in AdMob > Privacy & messaging. The SDK cannot invent or publish the message from source code.

DEBUG VS RELEASE ADS
Debug build (including the included GitHub Actions APK): Google's official TEST ads only. Safe for testing.
Release build: the publisher's real AdMob IDs. Do not click your own live ads.

The IDs are supplied through build-type resource values in app/build.gradle, and AndroidManifest.xml reads @string/admob_app_id.

BOOST LIMITATIONS — IMPORTANT
Volume changes Android's media-volume steps. Boost requests Android audio effects. These are different operations. The Boost percentage is a control scale, NOT a measured percentage increase in acoustic loudness. Android/device/player limitations described in v1.4 still apply.

PHONE TEST
1. Build/install a DEBUG APK from this source.
2. Confirm the banner shows a visible “Test Ad” label.
3. Start/stop the boost service three times; if the interstitial is loaded and the 2-minute cooldown permits it, confirm a full-screen TEST ad appears after the stop action.
4. Confirm the app works normally if ads fail to load or the network is offline.
5. Confirm Boost still works independently of advertising.
6. In a region where a privacy message is required, configure the message in AdMob > Privacy & messaging and test the consent flow on a registered UMP test device.

BUILD INFO
applicationId: com.volumeboostersoundboost.app
versionCode: 6
versionName: 1.5
minSdk: 23
targetSdk/compileSdk: 36
Java: 17
AGP: 8.13.2
Google Mobile Ads SDK: 25.4.0
Google UMP SDK: 4.0.0

GITHUB BUILD
A GitHub Actions workflow is included at .github/workflows/build-apk.yml.
1. Upload this project's CONTENTS to the repository root.
2. Commit/push to main, or run the workflow manually in Actions.
3. It runs tests/run.sh and then :app:assembleDebug.
4. Download the artifact named Volume-Booster-v1.5-TEST-APK.

A debug APK is for testing. It deliberately uses Google's demo ad IDs. Use your own production signing key and proper release process before publishing to Google Play.

PRIVACY
The complete in-app privacy text is:
app/src/main/assets/privacy_policy.txt

Before public release, add a real publisher/developer identity, a monitored privacy email, and a public URL for the privacy policy. Also complete Google Play Data safety and AdMob Privacy & messaging based on the final app configuration.
