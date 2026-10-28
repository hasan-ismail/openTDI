# Developer setup

This guide is for developers who build OpenTDI from source and run it on their own Galaxy Watch and Android phone.
It is not a guide for end users. The commands are for a POSIX shell (Linux, macOS, or Git Bash on Windows). In them,
`$W` and `$P` are the adb serials of the watch and the phone, as `adb devices -l` lists them:

```bash
W=<WATCH_SERIAL>; P=<PHONE_SERIAL>
```

## 1. Toolchain

- **JDK 17 or newer**, for example the one bundled with Android Studio. Set `JAVA_HOME` to it.
- **Android SDK** with these packages, from Android Studio's SDK Manager or `sdkmanager`:
  - Android SDK Platform 37 (`platforms;android-37.0`): the apps compile against API 37 and target API 36;
  - Android SDK Build-Tools 36 (`build-tools;36.0.0`);
  - Android SDK Platform-Tools (`platform-tools`), which provides `adb`.

  ```bash
  sdkmanager "platforms;android-37.0" "build-tools;36.0.0" "platform-tools"
  ```
- Tell Gradle where the SDK is, either with `ANDROID_HOME` or with a `local.properties` file in the repository root
  (it is gitignored). On Windows, write the path with forward slashes:
  ```properties
  sdk.dir=C:/Users/<you>/AppData/Local/Android/Sdk
  ```
- **Gradle:** always use the wrapper, `./gradlew` (`gradlew.bat` in cmd or PowerShell). It downloads Gradle 9.6.1 and
  checks its SHA-256. The Android Gradle Plugin and Kotlin versions are pinned in `gradle/libs.versions.toml`, so
  nothing else needs installing.

Build everything and run the unit tests:

```bash
./gradlew :shared:test :wear:assembleDebug :mobile:assembleDebug
```

## 2. Samsung Health Sensor SDK

The watch app reads heart rate, inter-beat intervals (IBI), EDA and the accelerometer through Samsung's Health Sensor

downloads their own copy ([wear/libs/README.md](../wear/libs/README.md) has the same steps).

1. Sign in with a Samsung account at <https://developer.samsung.com/health/sensor/overview.html>, choose
   **Download**, and accept the Samsung Health Partner Service & SDK License Agreement. Among other terms, it limits

2. Unzip `samsung-health-sensor-sdk-v1.4.1.zip` and copy `1.4.1/libs/samsung-health-sensor-api-1.4.1.aar` into
   `wear/libs/`.
3. Check the file against the copy OpenTDI was built with:
   ```bash
   sha256sum wear/libs/samsung-health-sensor-api-1.4.1.aar    # macOS: shasum -a 256 <file>
   # expected: 893cd5d6564db0f304bf511a555c1d65ca6bccc8475fc979ff1d71d50680344c
   ```
   A different hash means a different SDK build from the one OpenTDI was tested with.
4. Keep exactly one `samsung-health-sensor-api*.aar` in `wear/libs/`. The build stops with an error if it finds more
   than one.

Never commit the AAR (`.gitignore` excludes `wear/libs/*.aar`), and do not share APKs built with it.

Without the AAR, or with `-Popentdi.forceSamsungStub=true` on the Gradle command line, `:wear` compiles against
`:samsung-health-stub` and the watch uses Android's basic heart-rate and accelerometer sensors (no EDA, no IBI). It
then shows "This build has no Samsung sensor SDK. Using basic watch sensors (no EDA or beat-to-beat data)."

The SDK does not work on an emulator, so sensor work needs a real Galaxy Watch. EDA needs a Galaxy Watch8-series
watch or later.

## 3. Health Sensor Service developer mode (watch)

Samsung's SDK delivers data only to apps registered with Samsung (package name, signing-key SHA-256 and tracker
types), or to any app while developer mode is on in Health Sensor Service. Builds from source are not registered, so
turn developer mode on. Without it the trackers fail with `SDK_POLICY_ERROR`, and the watch app reports "Samsung
blocked sensor access (SDK_POLICY_ERROR)". Samsung lists a missing or poor network connection as another cause of the
same error, so also check that the watch is online.

Samsung's page is the authority for the steps:
<https://developer.samsung.com/health/sensor/guide/developer-mode.html>. In short: on the watch, open Settings > Apps >
Health Sensor Service (on the Galaxy Watch8 Classic with One UI 8 Watch the entry reads "Health Platform"), quickly
tap the **title** at the top about 10 times (the title, not the version number: tapping the version does nothing)
until a "Developer mode" row appears below the version, tap it, agree to the Samsung Health Partner Service & SDK
License, and turn developer mode on. The README's "Device setup" section has the same steps with the phone's and the
watch's own developer options.

Samsung says developer mode "is intended only for testing or debugging your app" and asks developers not to share its
developer-mode guide with app users. This section is for developers building from source; do not present it as a step
for end users. Registering the app with Samsung (partner registration) is needed only for public distribution.

## 4. Health Sensor Service version

SDK 1.4.x needs Health Sensor Service 1.6.5 or newer. (Samsung's compatibility table lists 1.6.5 for SDK 1.4.0 and has
no row for 1.4.1; OpenTDI assumes the same requirement.) The installed app is `com.samsung.android.service.health`;
`com.samsung.android.service.health.tracking` is only the SDK's Java package. Read the version without changing
anything:

```bash
adb -s $W shell dumpsys package com.samsung.android.service.health | grep -E "versionName|versionCode"
```

Compare it numerically, one component at a time: `1.7.00.05` is newer than `1.6.5`. If Health Sensor Service is
missing or too old, the watch app says so and offers to open the Galaxy Store to install or update it.

## 5. Wireless debugging

adb reaches the watch over Wi-Fi with wireless debugging (Android 11 and later). The phone can use USB or the same
method.

1. Turn on developer options and wireless debugging on each device: step by step for a Galaxy phone and a Galaxy
   Watch in the README's "Device setup" section; Android's generic pages are
   <https://developer.android.com/studio/debug/dev-options> (phone) and
   <https://developer.android.com/training/wearables/get-started/debug-wifi> (watch).
2. Pair each device once. On the device, open Wireless debugging and choose to pair a new device with a pairing code.
   Use the IP address and port shown on that pairing screen, typed exactly as shown:
   ```bash
   adb pair <ip>:<pairing-port>       # then enter the pairing code shown on the device
   ```
   On one SM-L500 software build (October 2026), a failed pairing attempt crashed the watch's system server (a soft
   restart; see [references/device_audit.md](references/device_audit.md)), so avoid repeated pairing attempts.
3. Connect. The connection port differs from the pairing port. Later sessions reattach without pairing again:
   ```bash
   adb mdns services                  # lists each paired device's _adb-tls-connect._tcp address
   adb connect <ip>:<port>
   adb devices -l
   ```
   The watch drops its adb connection when its Wi-Fi idles. Reconnect the same way, with the port `adb mdns services`
   shows at that time, instead of pairing again.

## 6. Installing debug builds

Both apps have the application ID `com.opentdi`. From Phase 2 the watch–phone link also needs both APKs signed with
the same key, so build both on the same machine (its debug keystore).

```bash
adb -s $W install -r -g --user 0 wear/build/outputs/apk/debug/wear-debug.apk
adb -s $P install -r -g --user 0 mobile/build/outputs/apk/debug/mobile-debug.apk
```

`-r` replaces an installed copy and `-g` grants the app's runtime permissions. Always pass `--user 0`: some phones have
a Secure Folder or work profile, which is a second Android user. Without `--user`, a package is installed for every
user on the device, and some shell commands fail with a `SecurityException` on such a profile. Apart from the
read-only `dumpsys` in §4, every command in this guide touches only the `com.opentdi` package.

If an install fails with `INSTALL_FAILED_UPDATE_INCOMPATIBLE` (the installed copy was signed with another key),
uninstall first. This deletes the app's data on that device, including its session logs, so copy them first (§8):

```bash
adb -s $W uninstall --user 0 com.opentdi
```

## 7. Runtime permissions

The apps request their permissions at first use, and `install -g` grants them all at once. To grant them one by one
(spec §14.2):

```bash
adb -s $W shell pm grant --user 0 com.opentdi android.permission.health.READ_HEART_RATE    # heart rate
adb -s $W shell pm grant --user 0 com.opentdi com.samsung.android.hardware.sensormanager.permission.READ_ADDITIONAL_HEALTH_DATA    # EDA
adb -s $W shell pm grant --user 0 com.opentdi android.permission.ACTIVITY_RECOGNITION    # accelerometer
adb -s $W shell pm grant --user 0 com.opentdi android.permission.POST_NOTIFICATIONS
adb -s $P shell pm grant --user 0 com.opentdi android.permission.POST_NOTIFICATIONS
```

From Phase 2 the phone also needs `android.permission.RECORD_AUDIO`; the Phase 1 app does not request it, so granting
it fails. `BODY_SENSORS` and `BODY_SENSORS_BACKGROUND` are not used on API 36 (granting them fails), and
`BLUETOOTH_CONNECT` is not declared. Normal permissions such as `FOREGROUND_SERVICE_*`, `WAKE_LOCK` and `INTERNET`
need no grant.

## 8. Retrieving session CSVs

Each session writes its CSV files to the app's private storage, `files/sessions/<sessionId>/`. Debug builds are
debuggable, so `run-as` can read them (spec §12.5):

```bash
adb -s $W exec-out run-as --user 0 com.opentdi tar cf - files/sessions > watch_sessions.tar
adb -s $P exec-out run-as --user 0 com.opentdi tar cf - files/sessions > phone_sessions.tar
# one file:
adb -s $W exec-out run-as --user 0 com.opentdi cat files/sessions/<sessionId>/w_packets.csv > w_packets.csv
```

- If `run-as` rejects `--user`, drop that flag for `run-as` only; `run-as` can read nothing but `com.opentdi`'s own
  files.
- Run these in a POSIX shell (Git Bash on Windows). Windows PowerShell 5.1's `>` re-encodes the output as text and
  corrupts the tar file.
- Phase 1 files: on the watch `w_config.csv`, `w_sensor_raw.csv`, `w_packets.csv` and `w_accel_events.csv`; on the
  phone `p_config.csv`, `p_transitions.csv` and `p_audio.csv`. Spec §12.2–12.3 describes every file and column. From
  Phase 2 the phone also keeps `files/journal/`.
- The apps delete all but the last 30 sessions when they start, so copy what you want to keep.

## 9. Hardware checks

Spec §15 is the hardware verification checklist: Health Sensor Service version and developer mode, supported
trackers, which permissions the trackers actually check, delivery and batching with the screen off, whether the
accelerometer includes gravity, battery drain, Doze and timers, and more. Items the spec marks VERIFY ON DEVICE are
settled by these checks; treat the related defaults as provisional until the checks are run.

## 10. Windows: Smart App Control blocks aapt2

If Windows Smart App Control blocks the `aapt2.exe` that the Android Gradle Plugin downloads (resource tasks then fail
because AAPT2 cannot start), point the plugin at the signed `aapt2.exe` in your SDK's build-tools instead. Put the
setting in your user-level Gradle properties, `~/.gradle/gradle.properties` (`%USERPROFILE%\.gradle\gradle.properties`),
never in the repository's `gradle.properties`: the path is specific to your machine.

```properties
android.aapt2FromMavenOverride=C:/Users/<you>/AppData/Local/Android/Sdk/build-tools/37.0.0/aapt2.exe
```

Use forward slashes, because a backslash is an escape character in `.properties` files. Build-tools 37.0.0 is known
to work with this project's Android Gradle Plugin (`sdkmanager "build-tools;37.0.0"`).
