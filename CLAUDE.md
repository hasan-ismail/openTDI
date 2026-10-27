# CLAUDE.md — OpenTDI: Open Targeted Dream Incubation for Galaxy Watch8 Classic & Android

## 1. Project Identity & Core Objective
- **Project Name:** OpenTDI (`open-tdi`)
- **Root Package Namespace:** `com.opentdi` (`com.opentdi.shared`, `com.opentdi.wear`, `com.opentdi.mobile`)
- **Objective:** Build a complete, production-grade open-source port of the MIT Media Lab **Dormio** Targeted Dream Incubation (TDI) platform engineered specifically for:
  1. **Wearable Target (`:wear`):** Samsung Galaxy Watch8 Classic (46mm, Non-Cellular Bluetooth/Wi-Fi variant, Model `SM-L500`, running Wear OS 6 / One UI 8 Watch, API Level 36, Exynos W1000).
  2. **Host Target (`:mobile`):** Android Smartphone (API Level 35/36) paired via Bluetooth/Wi-Fi and connected via ADB.

OpenTDI detects entry into **Stage N1 Sleep (Hypnagogia)** in real time using the Samsung Health Sensor SDK (`HEART_RATE_CONTINUOUS`, `EDA_CONTINUOUS`, and `ACCELEROMETER_CONTINUOUS`) with a single-session 3-minute rolling baseline (zero multi-night calibration required). Upon N1 detection, it runs a configurable hypnagogic incubation countdown, plays a soft anti-startle audio prompt via the selected audio route, records and transcribes the user's spoken dream report, re-administers the dream theme prompt, and loops across serial awakenings.

---

## 2. Phase 1: Reference Repositories & Specification Scraping
Before writing application code, create a gitignored `/reference_repos` directory and a tracked `/docs/references` directory. Clone the MIT Dormio repos and scrape all required physiological and SDK specifications:

### Repositories to Clone & Analyze
1. `https://github.com/tomasero/openSleep` — MIT Media Lab Fluid Interfaces core platform for sleep-stage biosignal state machines and serial awakening loops.
2. `https://github.com/christinatchen/dormio` — MIT Dormio audio state machine, timer intervals, prompt sequencing, and session recording flows.
3. `https://github.com/ThingEngineer/Dormio-Device-Duo-Wearable` — Hardware/firmware relative delta threshold calculations for HR, EDA, and motion/flex triggers.

### Technical Papers & SDK Documentation to Scrape
- **MIT Landmark Paper:** Horowitz et al. (2020), *"Dormio: A targeted dream incubation device"*, Consciousness and Cognition (`https://pmc.ncbi.nlm.nih.gov/articles/PMC7590944/`). Extract N1 onset criteria, latency windows, and serial awakening timings.
- **Samsung Health Sensor SDK (Wear OS 6 / API 36):**
  - Service package: `com.samsung.android.service.health.tracking` (`HealthTrackingService`)
  - `HealthTrackerType.EDA_CONTINUOUS` -> `ValueKey.EdaSet.SKIN_CONDUCTANCE`, `ValueKey.EdaSet.STATUS` (1 Hz continuous stream, works when screen is off).
  - `HealthTrackerType.HEART_RATE_CONTINUOUS` -> `ValueKey.HeartRateSet.HEART_RATE`, `ValueKey.HeartRateSet.IBI_LIST`, `ValueKey.HeartRateSet.HEART_RATE_STATUS` (1 Hz batched callback).
  - `HealthTrackerType.ACCELEROMETER_CONTINUOUS` -> `ValueKey.AccelerometerSet.ACCELEROMETER_X/Y/Z` (raw actigraphy stream).
- **Galaxy Watch8 Classic (`SM-L500`) Hardware Constraints:**
  - 1.34" Super AMOLED (438x438), physical rotating bezel (`onRotaryScrollEvent` / `MotionEvent.ACTION_SCROLL`), 445mAh battery, Wear OS 6 Foreground Service health permissions (`FOREGROUND_SERVICE_HEALTH`, `BODY_SENSORS`, `BODY_SENSORS_BACKGROUND`).

---

## 3. System Architecture (Multi-Module Gradle Project)
Structure the project as a clean multi-module Kotlin + Jetpack Compose build:

### `:shared` (`com.opentdi.shared`)
- Pure Kotlin module shared between `:wear` and `:mobile`.
- **Serialization & Data Layer Contracts:** Kotlinx Serialization models for 1 Hz `BiosignalPacket` (`timestamp`, `hr`, `ibiList`, `rmssd`, `skinConductance`, `accelRms`, `edaStatus`, `hrStatus`), `SessionConfig`, `TdiState`, and Wear Data Layer paths:
  - `/opentdi/sensor_stream` (1 Hz telemetry stream)
  - `/opentdi/state_command` (Start, Pause, Abort, Config sync)
  - `/opentdi/hypnagogia_trigger` (Immediate N1 onset event + countdown trigger)
  - `/opentdi/wrist_audio_channel` (Optional wrist mic/speaker fallback stream)
- **`HypnagogiaDetectionEngine`:** Self-contained, unit-tested state machine implementing the MIT relative-delta algorithm.

### `:wear` (`com.opentdi.wear`)
- Jetpack Compose for Wear OS + Horologist libraries targeting `SM-L500`.
- **`HealthTrackingForegroundService`:** Acquires `PARTIAL_WAKE_LOCK` and runs a persistent notification so `HEART_RATE_CONTINUOUS`, `EDA_CONTINUOUS`, and `ACCELEROMETER_CONTINUOUS` never throttle when the watch display turns off.
- **Physical Rotating Bezel UI:** Uses `Modifier.onRotaryScrollEvent` so the user can rotate the Watch8 Classic physical bezel to dial in the **N1 Incubation Delay (`60s–300s`)** and **Sensitivity Threshold (`Low / MIT Standard / High`)** directly from the wrist.
- **Standalone Fallback:** If Bluetooth to the phone drops mid-nap, the watch can vibrate gently and play prompts/record audio locally using the Watch8 Classic's built-in speaker and microphone.
- **`SimulationBroadcastReceiver`:** Listens for `com.opentdi.ACTION_SIMULATE_BIOSIGNALS` over ADB to inject deterministic waking-to-N1 biosignal curves for automated hardware-in-the-loop verification.

### `:mobile` (`com.opentdi.mobile`)
- Material 3 Jetpack Compose dark-First UI (AMOLED pitch-black theme for bedroom use).
- **Dream Incubation Theme Studio:** Create/select dream themes (e.g., *"A forest tree"*, *"Solving [Problem]"*) using either custom voice recordings or natural on-device TTS for all three MIT cues:
  1. *Initial Incubation Cue:* "Remember to think of [THEME]..."
  2. *Hypnagogic Wakeup Prompt:* "You're falling asleep. Please tell me, what's going through your mind?"
  3. *Re-Incubation Cue:* "Remember to think of [THEME]. You can fall back asleep now."
- **Live Telemetry & State Dashboard:** Real-time 60fps Compose Canvas charts plotting Heart Rate (BPM), Heart Rate Variability (`IBI RMSSD`), Wrist EDA (`μS`), and Wrist Actigraphy (`RMS g`), overlaid with baseline bands ($\mu \pm \sigma$) and active state markers.
- **Automated Dream Journal & Recorder:** Captures spoken dream reports with silence-detection auto-stop (4s silence or 45s max), runs on-device speech-to-text transcription, and saves `.m4a`/`.wav` audio synced to the exact physiological graph slice from that awakening cycle.

---

## 4. The Wrist-Adapted MIT Hypnagogia Algorithm (Zero Multi-Night Calibration)
Implement a self-calibrating **Rolling Baseline State Machine** in `com.opentdi.shared.engine.HypnagogiaDetectionEngine`:

1. **State Flow:**
   `IDLE` -> `CALIBRATING_BASELINE (180s)` -> `AWAKE_INCUBATING` -> `HYPNAGOGIA_DETECTED (Timer Countdown)` -> `WAKING_USER (Prompt 2)` -> `RECORDING_DREAM` -> `RE_INCUBATING (Prompt 3)` -> loops back to `AWAKE_INCUBATING` for up to $N$ cycles.
2. **Baseline Acquisition (`t = 0s..180s`):**
   - Collect 1 Hz samples of HR, IBI, EDA (`SKIN_CONDUCTANCE`), and Accelerometer magnitude $M = \sqrt{x^2 + y^2 + z^2}$.
   - Compute session baseline mean ($\mu$) and standard deviation ($\sigma$) for each channel after discarding motion artifact frames.
3. **Composite Sleep-Onset Trigger ($S_{\text{onset}}$):**
   - **Gate 1 — Actigraphy Stillness (Replaces MIT Finger Flex Sensor):** Rolling 60-second standard deviation of accelerometer magnitude must stay below stillness threshold ($\sigma_{\text{accel}} < \theta_{\text{still}}$), with bonus weight if a brief `<0.5s` high-amplitude spike followed by dead stillness (hypnic jerk) is observed.
   - **Signal A — Heart Rate & Parasympathetic Shift:** Current 15-second moving average of HR drops below $\mu_{\text{HR}} \times 0.93$ (default 7% drop, user-configurable 4%–12%) OR rolling IBI RMSSD increases by >15% above baseline.
   - **Signal B — Wrist EDA Tonic Decay:** Compute linear regression slope of `SKIN_CONDUCTANCE` over a 30-second window. Trigger when tonic slope is negative or flat ($\le 0$) with zero phasic arousal spikes ($|\Delta \text{EDA}| < 1.5\sigma_{\text{EDA}}$).
   - **Onset Confirmation:** When **Gate 1 (Stillness)** + **at least 1 primary autonomic signal (HR/IBI drop or EDA decay)** hold continuously for `20 seconds` (or all 3 hold for `10 seconds`), transition to `HYPNAGOGIA_DETECTED` and start the N1 incubation countdown timer (default `180s`). If major wrist movement occurs during the countdown, abort countdown and return to `AWAKE_INCUBATING`.

---

## 5. Multi-Route Hypnagogic Audio & Anti-Startle Architecture
The `:mobile` and `:wear` apps must implement `AudioRoutingManager` supporting four selectable topologies without forcing the user to wear earbuds, while solving Bluetooth/DAC hardware sleep bugs when headphones are used:

1. **Four Selectable Output Topologies (`AudioRouteMode`):**
   - `NIGHTSTAND_SPEAKER` (Default MIT Mode): Uses the phone's built-in stereo speakers (`USAGE_MEDIA`) with gentle room-level output.
   - `BLUETOOTH_SLEEP_AUDIO`: Routes prompts to paired Bluetooth headphones, earbuds, or sleep headbands (`TYPE_BLUETOOTH_A2DP`, `TYPE_BLE_HEADSET`, `TYPE_BLE_SPEAKER`).
   - `USB_WIRED_DAC`: Routes prompts to USB-C DAC/amps and wired IEMs (`TYPE_USB_HEADSET`, `TYPE_USB_DEVICE`, `TYPE_WIRED_HEADPHONES`).
   - `WATCH_WRIST_SPEAKER`: Routes prompts directly out of the Galaxy Watch8 Classic (`SM-L500`) built-in speaker (`TYPE_BUILTIN_SPEAKER`) for localized pillow-side whispers.

2. **Anti-Startle Carrier & Logarithmic Fade-In (Critical for Stage N1):**
   - **A2DP & USB DAC Keep-Alive Carrier:** When `BLUETOOTH_SLEEP_AUDIO` or `USB_WIRED_DAC` is selected, generate a continuous `-75dBFS` sub-audible warm pink-noise PCM stream via `AudioTrack` throughout the nap session. This prevents Bluetooth A2DP codecs and portable USB DAC amplifiers from entering hardware standby, eliminating the startle-inducing wake *pop/click* or clipped first syllable when a voice prompt fires.
   - **Logarithmic Envelope Ramp:** All voice prompts must fade in over a `2,000ms` logarithmic volume curve from `0%` to the calibrated target volume so the voice blends smoothly into the hypnagogic dream state.

3. **Decoupled Split-Input Dream Recording (Zero Bluetooth SCO Profile Switching):**
   - NEVER switch Bluetooth headphones into `MODE_IN_COMMUNICATION` or Bluetooth HFP/SCO microphone mode during `RECORDING_DREAM` (which triggers loud earbud profile-switch beeps and degrades audio to 8kHz mono).
   - Keep output locked to high-resolution `USAGE_MEDIA` and record the user's spoken dream report strictly from the **Phone's built-in microphone** (`MediaRecorder.AudioSource.MIC` / `UNPROCESSED`) or optionally the **Watch8 Classic wrist microphone** streamed over `ChannelClient`.

---

## 6. ADB Hardware-in-the-Loop Verification Protocol
Two physical devices are connected via ADB (`adb devices -l`):
1. **Phone:** Android Host Device.
2. **Watch:** Samsung Galaxy Watch8 Classic (`SM-L500`).

### Device Safety (MANDATORY — these are the owner's PERSONAL phone and watch)
- Touch **only** package `com.opentdi`: `install -r --user 0` our own debug APKs, `pm grant/revoke --user 0 com.opentdi …`, `am start/broadcast` to `com.opentdi`, `am force-stop com.opentdi`, `uninstall --user 0 com.opentdi`, `run-as com.opentdi` / `adb pull` of our own app files.
- Read-only diagnostics are fine: `getprop`, `dumpsys`, `logcat -d` / `logcat` streaming, `pm list`.
- **Never**: change settings (`settings put`, `svc`, `setprop`, `cmd` writes), reboot, uninstall/disable/clear/force-stop any other package, grant or revoke permissions of other packages, push files outside our app's storage, install any other APK, touch Wi-Fi/Bluetooth/accounts, or remove the adb pairing.
- The phone has a **Secure Folder profile (user 150)**: always pass `--user 0`; never target user 150.
- **Do not start new `adb pair` attempts on the watch**: a failed pairing crashes `system_server` on this firmware (soft restart, 2026-10-27). Both devices are already paired; reattach with `adb mdns services` + `adb connect <ip:port>`.
- Ask the owner before the first install on each device and before any audible playback test.

### Automated ADB Test Sequence
- Build and install `:wear:assembleDebug` and `:mobile:assembleDebug` onto the physical devices via `adb -s <SERIAL> install -r`.
- Grant all runtime permissions via `adb shell pm grant` (`BODY_SENSORS`, `BODY_SENSORS_BACKGROUND`, `ACTIVITY_RECOGNITION`, `RECORD_AUDIO`, `POST_NOTIFICATIONS`, `BLUETOOTH_CONNECT`).
- Trigger the end-to-end simulation mode via ADB:
  `adb -s <WATCH_SERIAL> shell am broadcast -a com.opentdi.ACTION_SIMULATE_BIOSIGNALS --es mode "FAST_HYPNAGOGIA"`
- Verify in dual-device `adb logcat` that:
  1. Watch calculates the baseline, detects the synthetic N1 drop, and dispatches `/opentdi/hypnagogia_trigger`.
  2. Phone receives the trigger, runs the countdown, ramps in the Wakeup Prompt over the active audio route, records a test dream snippet without switching Bluetooth profiles, logs the transcript + biosignal snapshot, plays the Re-Incubation prompt, and cycles cleanly into Loop 2.