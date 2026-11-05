# Volume Booster 2.0 preview — audit and device validation

Source baseline: 495164bc15b6a7f4adfa49b1700f3c86b4fb10db (V1.5).
Branch: repair/v2-audio-lifecycle. This preview is not a production approval.

## Repairs
- Runtime gain/readback failures release native effects, including NaN/infinite readback.
- Failed/disabled effects trigger at most three recovery attempts per explicit command or route/session change, with backoff. No endless retries on unsupported hardware.
- Stop/null commands cannot briefly activate saved gain during service creation.
- Stale native callbacks are ignored; route callback cleanup cannot prevent releasing effects.
- Diagnostics use the build version and report the recovery budget. Enabled/control/readback remain software checks, never acoustic proof.
- Ad loading checks current UMP eligibility and Activity lifetime. Initialization returns to the UI thread. Privacy changes discard cached ads and invalidate pending interstitial loads.
- Foreground service uses specialUse with a concrete manifest explanation, since this app does not itself play media. Play Console review is still required; the manifest alone does not grant approval.

## Compatibility limits and preserved behavior
DynamicsProcessing remains first in Automatic mode. The original 0–40 control mapping (0–12 dB requested input gain), vendor DP configuration and manual engine choices remain unchanged. This preserves the known software path; TECNO regression testing is still required. 40% is not 40% measured loudness and +12 dB may clip or be limited.

Session 0 is a best-effort vendor path. Android documents global insert effects as deprecated. A successful constructor/readback cannot establish that another app's audio travels through the effect. Cooperating player sessions are preferred and never stacked with session 0. Arbitrary players need not announce sessions; offload, Bluetooth and vendor processing can bypass effects. No microphone, audio capture, root workaround or universal enhancement claim was added.

## Required physical-device matrix (not run here)
| Device | Android | V1.5 user report | V2 preview |
|---|---|---|---|
| TECNO BG6 | 13 | Clear boost at 10–40 across apps | Pending |
| itel A665L | 13 | No audible boost | Pending |
| TCL 6156D | 11 | No audible boost | Pending |
| Xiaomi 220333QNY | 11 | No audible boost | Pending |

For each device, use the same media excerpt, fixed media volume and output route. Start at a low listening volume. Compare Off/0/10/20/30/40, stopping if distortion or discomfort occurs. Test Automatic, then each manual engine. Record audible difference separately from engine attachment/readback; save diagnostics per route and engine. Repeat on speaker, wired and Bluetooth where available. Do not increase gain to compensate for an unsupported path.

Also test Stop from notification, cold Stop, repeated start/stop, app background/screen-off, process kill, player reopen, route connect/disconnect, another EQ taking control and notification permission denied. On API 34–36 test specialUse foreground startup. A unit-test fake cannot establish any of these Android runtime results.

## Build, identity and signing
Open this folder in Android Studio with JDK 17, Android SDK 36 and Gradle 8.13. There is no Gradle wrapper in the original project. CI installs Gradle explicitly, runs `bash tests/run.sh`, then `gradle :app:assembleDebug :app:lintDebug`.

Application ID remains `com.volumeboostersoundboost.app`; preview versionCode is 7 and versionName is 2.0-preview01. Verify that a production versionCode exceeds every version already uploaded before release. No release key was read, changed, generated or committed. Debug signing will usually not match the installed Play/release app, so this APK cannot normally update it. Do not uninstall a user's production copy without considering loss of local settings. A release update requires the correct upload/signing setup and Play App Signing continuity.

Debug uses Google's test AdMob IDs. Production IDs are preserved. Publishing is blocked pending real publisher identity/contact/public privacy-policy URL (the existing privacy policy explicitly lacks these), Play Data safety and ads declarations, UMP message configuration and regional consent tests, foreground-service declaration/review, and physical-device validation. No Play Console or AdMob account state was inspected.

## References checked 2026-11-05
- https://developer.android.com/reference/android/media/audiofx/AudioEffect
- https://developer.android.com/develop/background-work/services/fgs/service-types
- https://developers.google.com/admob/android/privacy

The old claimed `/mnt/data/VolumeBooster_V2_Audio_Repair_Kit.zip` was not present in the accessible workspace or returned chat attachments. Prior claimed test counts are not used as evidence for this preview.
