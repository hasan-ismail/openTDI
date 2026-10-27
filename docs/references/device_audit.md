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
