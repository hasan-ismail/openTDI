# OpenTDI

Open Targeted Dream Incubation for the Samsung Galaxy Watch8 Classic and Android.

OpenTDI is an open-source Android and Wear OS implementation of targeted dream incubation (TDI): a sleeper is
cued with a chosen theme while falling asleep, woken in the hypnagogic stage (N1) to say what is going through
their mind, and cued again as they fall back asleep, in a loop. It follows Dormio, the MIT Media Lab device that
detects sleep onset with finger and wrist sensors and runs this loop with spoken prompts (Horowitz, Cunningham,
Maes and Stickgold, "Dormio: A targeted dream incubation device", *Consciousness and Cognition*, 2020,
[PMC7590944](https://pmc.ncbi.nlm.nih.gov/articles/PMC7590944/)), and Adam Haar Horowitz's sensor-free
"Dormio Light" protocol, which replaces sensing with timed audio cues (Bellaiche, Haar Horowitz et al.,
*Frontiers in Sleep*, 2024, [doi:10.3389/frsle.2024.1258345](https://doi.org/10.3389/frsle.2024.1258345)).
OpenTDI offers both as session modes: `DETECTION`, where a Galaxy Watch8 Classic (SM-L500, Wear OS 6, API 36)
estimates sleep onset from heart rate, inter-beat intervals, skin conductance (EDA) and motion read through
Samsung's Health Sensor SDK, and `TIMED_CUES`, which needs only the phone. An Android phone (Android 13 or
newer; developed on API 37) plays the cues.

> **Not a medical device.** OpenTDI is open-source wellness and research software for exploring targeted dream
> incubation at sleep onset. It does not diagnose, treat, cure, mitigate or prevent any disease or condition, and it is
> not a sleep-staging or medical device. Its sleep-onset estimates come from unvalidated thresholds and may be wrong.
> Heart rate, inter-beat interval, skin conductance and motion data from the Samsung Health Sensor SDK are "for fitness
> and wellness information only, not for the diagnosis or treatment of any medical condition" (Samsung). Do not use
> OpenTDI if you have a history of psychotic disorders, narcolepsy, recurrent sleep paralysis, parasomnias or other
> sleep disorders without talking to a clinician first. This project is not affiliated with or endorsed by the MIT Media
> Lab or Samsung.

## Status

Phase 1 is under construction:

- **Watch:** connection to the Samsung Health Sensor SDK with on-watch status messages; heart rate, IBI, EDA and
  accelerometer tracking in a health foreground service; a fallback to Android's basic sensors; 25 Hz motion
  processing and 1 Hz data packets; CSV logging of each session, which is started and stopped on the watch.
- **Phone:** `TIMED_CUES` sessions on the Dormio Light timeline, with text-to-speech cues on the phone's speaker, a
  2-second fade-in, a hard volume cap, a Do Not Disturb check and audio focus; a fixed 60-second report window
  (nothing is recorded yet); CSV logging.

In Phase 1 the watch and the phone do not talk to each other, and sleep-onset detection does not run yet. Later
phases (spec §0):

| Phase | Planned |
|---|---|
| 2 | Watch–phone Data Layer link; sleep-onset detection on the watch (`DETECTION` mode) with a replay harness and a simulation receiver; dream-report recording and on-device speech-to-text; journal; the "And were you asleep?" question; detection confidence score; watch bezel settings; automatic fallback to timed cues |
| 3 | Theme studio with own-voice cues; telemetry dashboard; Bluetooth sleep-audio route with a keep-alive carrier |
| Deferred | USB DAC and watch-speaker audio routes; wrist-microphone streaming; standalone watch mode; 60 fps charts; an arm-raised "limp" task |

## Repository layout

| Path | Contents |
|---|---|
| `shared/` | `:shared`, pure Kotlin (JVM): the data contracts and the signal-processing and scheduling logic both apps use, with JUnit tests |
| `wear/` | `:wear`, the watch app. `wear/libs/` is where the Samsung SDK AAR goes |
| `mobile/` | `:mobile`, the phone app |
| `samsung-health-stub/` | `:samsung-health-stub`, a compile-only stand-in for the Samsung SDK, used when the AAR is absent |
| `docs/` | the engineering spec, the research notes it cites, and the developer setup guide |

Both apps use the application ID `com.opentdi` (spec §10.1).

## Building

You need JDK 17 or newer and an Android SDK with platform 37 (details in [docs/SETUP.md](docs/SETUP.md)). Point
Gradle at the SDK with `sdk.dir` in `local.properties` or with `ANDROID_HOME`. The Gradle wrapper downloads Gradle
itself.

```bash
./gradlew :shared:test :wear:assembleDebug :mobile:assembleDebug
```

On Windows, run this in Git Bash, or use `gradlew.bat` from cmd or PowerShell. The debug APKs are written to
`wear/build/outputs/apk/debug/` and `mobile/build/outputs/apk/debug/`.

**Samsung Health Sensor SDK.** The SDK's AAR is not in this repository: each developer downloads it from Samsung and
accepts Samsung's license (see [wear/libs/README.md](wear/libs/README.md)). Without the AAR, `:wear` compiles against
`:samsung-health-stub` and the watch falls back to Android's basic sensors (heart rate and accelerometer; no EDA and
no IBI). To build against the stub even when the AAR is present, for example to test that fallback:

```bash
./gradlew :wear:assembleDebug -Popentdi.forceSamsungStub=true
```

Installing on the devices, permissions and retrieving logs are covered in [docs/SETUP.md](docs/SETUP.md).

## Device setup: developer mode on the phone and the watch

These steps are for developers running builds from source on their own devices. The menu names below are from a
Samsung Galaxy phone (One UI) and a Galaxy Watch8 Classic (One UI 8 Watch, Wear OS 6); other software versions may
word them slightly differently. adb comes with the Android SDK's platform-tools, and your computer must be able to
reach both devices' IP addresses (normally: the same Wi-Fi network).

### Phone

1. **Developer options:** Settings > About phone > Software information > tap **Build number** 7 times and enter your
   PIN, until "Developer mode has been turned on" appears. **Developer options** is now at the bottom of Settings.
2. **Wireless debugging:** Settings > Developer options > **Wireless debugging** > On (allow it on this network).
   A USB cable with **USB debugging** works too.
3. **Pair once:** in Wireless debugging, tap **Pair device with pairing code**, then on the computer run, with the
   IP address, port and code from that pop-up:
   ```bash
   adb pair <ip>:<pairing-port> <pairing-code>
   ```
4. **Connect:** the main Wireless debugging screen shows a second IP address and port, which is the one to connect to:
   ```bash
   adb mdns services        # lists every paired device's _adb-tls-connect._tcp address
   adb connect <ip>:<port>
   adb devices -l
   ```

### Watch

1. **Wi-Fi:** connect the watch to Wi-Fi (Settings > Connections > Wi-Fi). Wireless debugging needs it, even while the
   watch is also connected to the phone over Bluetooth.
2. **Developer options:** Settings > About watch > Software information > tap **Software version** repeatedly (about
   5–7 times) until "Developer mode turned on" appears. **Developer options** is now at the bottom of Settings.
3. **ADB debugging:** Settings > Developer options > **ADB debugging** > On.
4. **Wireless debugging:** Developer options > **Wireless debugging** > On > **Pair new device**. The watch shows a
   pairing code and an IP address and port. Pair with exactly that address:
   ```bash
   adb pair <ip>:<pairing-port> <pairing-code>
   ```
   Type the address exactly as the watch shows it and avoid repeated failed attempts: on the SM-L500 a failed
   pairing attempt once crashed the watch's system server and restarted its interface
   ([docs/references/device_audit.md](docs/references/device_audit.md)).
5. **Connect**, and later **reconnect**, without pairing again. The watch drops adb when its Wi-Fi idles; the
   connection port changes, so look it up each time:
   ```bash
   adb mdns services
   adb connect <ip>:<port>
   ```

### Watch: Health Sensor Service developer mode (needed for the Samsung sensors)

Samsung's Health Sensor SDK only serves apps that Samsung has registered, or any app while **developer mode** is on in
the watch's Health Sensor Service. Builds from source are not registered, so turn developer mode on. Without it the
watch app reports "Samsung blocked sensor access (SDK_POLICY_ERROR)" and gets no heart rate, IBI, EDA or motion data
from the SDK.

1. On the watch: Settings > Apps > **Health Sensor Service**. On the Galaxy Watch8 Classic (One UI 8 Watch) it is
   listed as **Health Platform**.
2. Open it. Quickly tap the **title** at the top of that screen ("Health Platform") about 10 times. Tap the title
   itself, not the version number under it: tapping the version does nothing.
3. A **Developer mode** row appears below the version number (scroll down if you don't see it). Tap it.
4. Agree to the Samsung Health Partner Service & SDK License and switch developer mode **On**.
5. Keep the watch online (Wi-Fi, or connected to the phone). Samsung names a missing network connection as another
   cause of `SDK_POLICY_ERROR`.

The watch also needs Health Sensor Service **1.6.5 or newer** for SDK 1.4.x; the Galaxy Watch8 Classic used for
development has 1.7.00.05. Check it read-only with
`adb -s <watch> shell dumpsys package com.samsung.android.service.health | grep versionName`.

Samsung's own page is the authority for these steps:
<https://developer.samsung.com/health/sensor/guide/developer-mode.html>. Samsung says developer mode "is intended only
for testing or debugging your app" and asks developers not to share its developer-mode guide with app users. This
section is for developers building OpenTDI from source; it is not an end-user step. Registering the app with Samsung
(partner registration) is needed only for public distribution.

## Documentation

- [docs/ARCHITECTURE_AND_THRESHOLDS.md](docs/ARCHITECTURE_AND_THRESHOLDS.md): the engineering spec (v1.1) the code is
  written from: phases, state machine, thresholds, data contracts, CSV formats, permissions and the hardware
  checklist. It labels its thresholds and timings as SOURCED (from the papers or the MIT code) or GUESSED (an
  unvalidated OpenTDI default).
- [docs/references/](docs/references/): the research notes the spec cites: the Dormio papers and code, the Samsung
  SDK, the Android platform, audio and speech-to-text, Wear Compose, and an audit of the development devices.
- [docs/SETUP.md](docs/SETUP.md): developer setup.
- [CLAUDE.md](CLAUDE.md): the original project brief. Where it and the spec disagree, the spec wins.

## License

OpenTDI is free software under the GNU General Public License, version 3 only (GPL-3.0-only); see
[LICENSE](LICENSE). [NOTICE](NOTICE) adds an additional permission under GPLv3 section 7 for combining OpenTDI with
the Samsung Health Sensor SDK: the SDK is not made subject to the GPL, and its source is not part of the
Corresponding Source.

The Samsung Health Sensor SDK is proprietary to Samsung Electronics, is licensed by Samsung under its own terms (the
Samsung Health Partner Service & SDK License Agreement), and is never part of this repository. Those terms forbid

or publicly demonstrated. OpenTDI is therefore released as source code only: there will be no prebuilt watch APK

not legal advice.) `:samsung-health-stub` is original OpenTDI code written from Samsung's public API reference; it
contains no Samsung code.

## Acknowledgements

OpenTDI builds on the Dormio targeted dream incubation research of Adam Haar Horowitz and colleagues in the Fluid
Interfaces group at the MIT Media Lab. The public [openSleep](https://github.com/tomasero/openSleep),
[dormio](https://github.com/christinatchen/dormio) and
[Dormio Device Duo Wearable](https://github.com/ThingEngineer/Dormio-Device-Duo-Wearable) repositories were studied
as references; OpenTDI reimplements the published behaviour and copies no code, cue wording or audio from them
(spec §1.3). OpenTDI is not affiliated with or endorsed by MIT, the MIT Media Lab or Samsung.
