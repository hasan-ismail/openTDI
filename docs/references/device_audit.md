# Device audit (Phase 2) — 2026-10-27

Read-only `adb shell getprop` / `dumpsys package` queries over Wi-Fi wireless debugging


| | Watch | Phone |
|---|---|---|
| Model (`ro.product.model`) | SM-L500 Galaxy Watch8 Classic (`wise8bs`) | SM-S948U1 (`m3q`, SoC SM8850) |
| Android / API | 16 / **36** | 17 / **37** (CLAUDE.md assumed 35/36) |
| Build | BP2A.250325.020.L500XXS2AZF4, patch 2026-07-05 | CP2A.260605.016.S948U1UES4BZIG, patch 2026-10-05 |
| ABIs | **armeabi-v7a, armeabi only (32-bit)** | arm64-v8a |
| Google Play services | 26.41.31 | 26.41.31 |
| Wear OS app / companion | com.google.android.wearable.app 1.39.23 | com.samsung.android.app.watchmanager 2.2.72 |
| TTS engines | com.samsung.SMT 3.5.01.65, com.google.android.tts (wear) | com.samsung.SMT 3.5.02.58 (default), com.google.android.tts |
| Speech recognizer | — | `voice_recognition_service` = com.google.android.tts/…GoogleTTSRecognitionService |

## Samsung Health Platform (watch)

- The Health Platform APK is **`com.samsung.android.service.health`**, versionName **1.7.00.05**
  (versionCode 170005001, targetSdk 36). `com.samsung.android.service.health.tracking` is *not* an installed
  package — it is the SDK's Java package. Status checks must look up `com.samsung.android.service.health`.
- 1.7.00.05 ≥ 1.6.5, the minimum the owner cites for SDK 1.4.0.
- Developer mode state is not visible over adb; it shows up as `SDK_POLICY_ERROR` (off) or a successful connection (on).

## Permissions defined on the watch (`dumpsys package permissions`)

| Permission | Protection | Defined by |
|---|---|---|
| com.samsung.android.hardware.sensormanager.permission.READ_ADDITIONAL_HEALTH_DATA | dangerous (runtime, `pm grant`-able) | com.samsung.android.hardware.sensormanager.permission (v16) |
| android.permission.health.READ_HEART_RATE | dangerous | health permission controller |
| android.permission.health.READ_HEALTH_DATA_IN_BACKGROUND | dangerous | health permission controller |
| android.permission.health.READ_SKIN_TEMPERATURE | dangerous | health permission controller |
| android.permission.BODY_SENSORS / BODY_SENSORS_BACKGROUND | dangerous | android |

Which of these the Samsung SDK path actually needs is still to be verified on device (owner requirement B6).

## Wearable Data Layer

Watch `WearableService` dump: `1 connected out of 1`, a Bluetooth Classic (`Type=BTC`) connection to the phone node,
`IsConnected=true`. Phone ↔ watch Data Layer transport is up.

## adb notes

- Pair once per device (`adb pair <ip:pairing-port> <code>`); afterwards `adb mdns services` shows the
  `_adb-tls-connect._tcp` port and `adb connect <ip:port>` re-attaches without a new code.
- The watch drops its adb transport when Wi-Fi idles; reconnect with the mDNS-advertised port.
- The phone has a second profile, **Secure Folder (user 150)**; `pm list packages` without `--user 0` throws
  `SecurityException: Shell does not have permission to access user 150`. Always pass `--user 0`.


  `system_server_native_crash`: thread `AdbDebuggingMan`, SIGABRT, `FORTIFY: pthread_mutex_lock called on a destroyed
  mutex`, in `android::native_pairing_wait` ← `AdbDebuggingManager$PairingThread.run` — a teardown race in the
  platform's wireless-debugging pairing code. Kernel uptime was unaffected (soft restart only). Avoid new pairing
  attempts; reattach the existing pairing instead.

## First on-device run, 2026-10-27 21:12–21:19 (Phase 1 integrated build 0.1.0)

Read-only from the app's own CSVs (`run-as com.opentdi`, session `20261028T011650Z-21a0`, 134 packets) and logcat.

- **Samsung SDK works with developer mode on:** `HSS_VERSION 1.7.00.05 supported=true`, `SDK_VERSION 1.4.01.00`,
  `STATUS CONNECTED_SAMSUNG` about 5 s after start with HR, EDA and accelerometer trackers (spec §15 #1).
- **The accelerometer stream INCLUDES gravity** on the SM-L500, contrary to the javadoc ("doesn't include gravity"):
  `accel_mean_g` = 0.995–1.004 g in every second, still or not (spec §15 #5). Limp-wrist detection is therefore
  possible on the SDK path.
- **25 Hz confirmed:** `n_accel_samples` = 24–25 per second (spec §15 #6, screen-on so far).
- **`DataPoint.getTimestamp()` is epoch milliseconds:** packet `ts_ms` 1793150325000 = 2026-10-28T01:18:45Z, matching
  the wall clock (spec §15 #8 timestamp base).
- HR 66–67 bpm with status 1; EDA 1.213–1.214 µS with status 0 at 1 Hz (one null second).
- **IBIs are sparse:** most 1 s bins carry no IBI and RMSSD stayed null over 2 min (it needs 20 consecutive
  accepted pairs in 60 s). Relevant to the Phase 2 decision to keep RMSSD logged-only.
- Gravity mode stayed `UNKNOWN`: only 17 of 134 seconds had stillness ≥ 0.8 (longest run 14 s) while the owner was
  handling the devices, so the 30-still-second rule never completed. Not a defect in the code; the rule is tightened
  after this run.
- Phone: first cue routed to the built-in speaker (`routedType=2`) with focus granted, but `streamIndex=0` (media
  volume 0), so it was silent; a media-volume guard is added after this run.
