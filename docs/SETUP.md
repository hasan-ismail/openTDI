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
./gradlew :shared:test :wear:testDebugUnitTest :mobile:testDebugUnitTest :wear:assembleDebug :mobile:assembleDebug
```

## 2. Samsung Health Sensor SDK

The watch app reads heart rate, inter-beat intervals (IBI), EDA and the accelerometer through Samsung's Health Sensor
SDK 1.4.1. OpenTDI contains no Samsung code. Samsung's Health Sensor SDK is optional; each user downloads it from
Samsung, under Samsung's own terms, and adds it in the app. For a build of your own you can instead put it in
`wear/libs/` ([wear/libs/README.md](../wear/libs/README.md) has the same steps):

1. Sign in with a Samsung account at <https://developer.samsung.com/health/sensor/overview.html> and choose
   **Download**.
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

Never commit the AAR (`.gitignore` excludes `wear/libs/*.aar`). A watch APK built with it contains Samsung's code, and
so does a phone APK built with it (inside its copy of the watch app); OpenTDI's published builds contain none (§12).

Without the AAR, or with `-Popentdi.forceSamsungStub=true` on the Gradle command line, `:wear` compiles against
`:samsung-health-stub` and the watch uses Android's basic heart-rate and accelerometer sensors (no EDA, no IBI). It
then shows "This build has no Samsung sensor SDK. Using basic watch sensors (no EDA or beat-to-beat data)."

The SDK does not work on an emulator, so sensor work needs a real Galaxy Watch. EDA needs a Galaxy Watch8-series
watch or later.

## 3. Health Sensor Service developer mode (watch)

Samsung's SDK delivers data only to apps registered with Samsung (package name, signing-key SHA-256 and tracker
types), or to any app while developer mode is on in Health Sensor Service. OpenTDI is not registered, so turn
developer mode on. Without it the trackers fail with `SDK_POLICY_ERROR`, and the watch app reports "Samsung
blocked sensor access (SDK_POLICY_ERROR)". Samsung lists a missing or poor network connection as another cause of the
same error, so also check that the watch is online.

Samsung's page is the authority for the steps:
<https://developer.samsung.com/health/sensor/guide/developer-mode.html>. In short: on the watch, open Settings > Apps >
Health Sensor Service (on the Galaxy Watch8 Classic with One UI 8 Watch the entry reads "Health Platform"), quickly
tap the **title** at the top about 10 times (the title, not the version number: tapping the version does nothing)
until a "Developer mode" row appears below the version, tap it, accept Samsung's terms when asked, and turn developer
mode on. The README's "Device setup" section has the same steps with the phone's and the watch's own developer options.

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
- Phase 1 files: on the watch `w_config.csv`, `w_sensor_raw.csv`, `w_packets.csv`, `w_accel_events.csv` and, only
  when "Log raw accelerometer" (watch, Details) is on, `w_accel_raw.csv`; on the phone `p_config.csv`, `p_transitions.csv` and
  `p_audio.csv`. Spec §12.2–12.3 describes every file and column. From Phase 2 the phone also keeps `files/journal/`.
- All but the last 30 sessions are deleted, on the phone when the app starts and on the watch when a logging session
  starts, so copy what you want to keep.

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

## 11. Natural voice (optional)

The phone can render the cues with an on-device neural voice instead of Android's text-to-speech: Kokoro-82M v0.19
(int8) through sherpa-onnx, with American and British voices (three women, three men). It runs only when you tap **Prepare cues**, never
during a session, and needs no network. The model is not in the APK or the repository: download it once (103 MB, 152 MB
unpacked; Kokoro weights Apache-2.0, espeak-ng data GPL-3.0) and copy it into the app's private storage.

```bash
tools/voice/fetch_voice_model.sh        # into tools/voice/models/ (gitignored), checked against a pinned SHA-256
tools/voice/push_voice_model.sh $P      # into files/voice/kokoro-int8-en-v0_19/ of com.opentdi on the phone
```

The push script copies the model to `/data/local/tmp`, moves it into the app's storage with `run-as --user 0
com.opentdi` and deletes the temporary copy; it touches nothing else. Uninstalling the app deletes the model, so run
the push script again after a reinstall (`install -r` keeps it).

Then, in the cue sound settings, turn on **Natural voice**, choose a voice and tap **Prepare cues**. If the model is
missing, does not load or fails while rendering, the app says so and prepares the cues with the phone's own voice. The
build fetches the sherpa-onnx AAR (`mobile/libs/`, gitignored, pinned SHA-256) by itself.

## 12. Making a release (maintainers)

GitHub Actions runs the unit tests, both debug builds and lint on every push to `main` and every pull request
(`.github/workflows/ci.yml`; the debug APKs stay downloadable from the run for 7 days). Pushing a tag `v<versionName>`
runs `.github/workflows/release.yml`: it builds both release APKs signed with one release key, checks with
`tools/release/dexcheck.py` that neither APK (nor the phone's `assets/watch.apk`) contains Samsung code, and publishes
`OpenTDI-phone.apk`, `OpenTDI-watch.apk` and `SHA256SUMS` as a GitHub Release whose notes are the tag's message. Both
workflows build without Samsung's SDK (`-Popentdi.forceSamsungStub=true`).

1. Create the release key once. With the default PKCS12 keystore the key password is the keystore password:
   ```bash
   keytool -genkeypair -v -keystore opentdi-release.jks -alias opentdi -keyalg RSA -keysize 4096 -validity 10000
   ```
   Keep `opentdi-release.jks` and its password private and backed up, outside the repository. Released apps can only
   be updated by apps signed with the same key: without it, users must uninstall (losing their data) to update.
2. Store it as four repository secrets. `gh secret set` reads a value from stdin or asks for it:
   ```bash
   base64 -w0 opentdi-release.jks | gh secret set OPENTDI_KEYSTORE_BASE64    # macOS: base64 -i opentdi-release.jks
   gh secret set OPENTDI_KEYSTORE_PASSWORD
   gh secret set OPENTDI_KEY_ALIAS                                           # opentdi
   gh secret set OPENTDI_KEY_PASSWORD                                        # the same as the keystore password
   ```
3. Raise `versionCode` by one and set `versionName` in both `mobile/build.gradle.kts` and `wear/build.gradle.kts`,
   then merge to `main` and wait for CI to pass.
4. Tag that commit with the release notes as the message, and push the tag. The release workflow stops if the tag is
   not `v` followed by the `versionName`:
   ```bash
   git tag -a v0.3.0 -m "OpenTDI 0.3.0" -m "What changed, in a few lines."
   git push origin v0.3.0
   ```

To sign a release build locally, set the same values as environment variables or Gradle properties, with
`OPENTDI_KEYSTORE_FILE` the path to the keystore (a relative path starts at the repository root):

```bash
OPENTDI_KEYSTORE_FILE=/path/to/opentdi-release.jks OPENTDI_KEY_ALIAS=opentdi \
OPENTDI_KEYSTORE_PASSWORD='...' OPENTDI_KEY_PASSWORD='...' \
  ./gradlew :wear:assembleRelease :mobile:assembleRelease -Popentdi.forceSamsungStub=true
python3 tools/release/dexcheck.py wear/build/outputs/apk/release/wear-release.apk \
  mobile/build/outputs/apk/release/mobile-release.apk
```

Without these values a release build is unsigned (`*-release-unsigned.apk`) and debug builds keep the machine's debug
key, as before. The phone APK carries the watch APK of the same build type, so a signed release phone app installs a
watch app signed with the same key.

## 13. Offline transcription (optional)

After each copy from the watch (opening **Stats**, or the end of a phone session), the phone app turns new dream
recordings into text: `w_dream_HHmmss.txt` beside each `w_dream_HHmmss.m4a` in `files/watch/<session>/`, where an empty
file means no speech was found. It runs sherpa-onnx with the Silero VAD and a speech model on the phone, never during a
session and never while **Prepare cues** uses the natural voice. Transcription itself uses no network; how recordings
reach the phone is in the README ("Your data stays with you"). A transcript is a written copy of your dream report,
unchecked: speech recognition has not been tested on sleepy speech, so the recording stays the record. The model is in
neither the APK nor the repository: download it once from the sherpa-onnx releases on GitHub (an ordinary download, so
GitHub sees the request) and copy it into the app's private storage.

```bash
tools/stt/fetch_stt_model.sh        # into tools/stt/models/ (gitignored), checked against pinned SHA-256s
tools/stt/push_stt_model.sh $P      # into files/stt/ of com.opentdi on the phone
```

The default model is NVIDIA Parakeet TDT-CTC 110M int8 (104 MB download, 132 MB installed, CC-BY-4.0, see NOTICE).
Add `moonshine` to both commands for the smaller fallback, Moonshine tiny English (30 MB download, 44 MB installed,
MIT); when both are installed the app uses Parakeet. The push script works like the voice one (section 11): through
`/data/local/tmp` and `run-as com.opentdi --user 0`, touching nothing else. Uninstalling the app deletes the model, so
run the push script again after a reinstall (`install -r` keeps it). Without a model the app reports "Transcription
model not installed"; once one is pushed, the next copy transcribes the recordings that are waiting. Each recording is
transcribed once: delete its `.txt` to redo it, for example with the other model. `adb -s $P logcat -s OTDI.Stt` shows
which model loaded and each file's timings, never the text.
