# OpenTDI: Architecture and Thresholds (engineering spec)

Version 1.1, 2026-10-27 (1.0 revised after review). This is the spec that implementing engineers code from.

**Order of authority.** (1) Owner requirements A1–G23. (2) Lead-engineer design decisions D1–D18. (3) This document.
(4) `CLAUDE.md`. Where this document departs from D1–D18, the place is marked **[EXT]** (an extension the decisions
do not cover) or **[DEV]** (a deviation), and every one is listed in §17.1.

**Labels.**
- **SOURCED**: the number or behaviour is quoted in a reference note, which cites the original source.
- **GUESSED**: an unvalidated OpenTDI default. It must be a config value, logged per session, and tuned on recorded
  naps with the replay harness (§12). Exception: the **structural** GUESSED constants listed in §8.15 are fixed in code
  (not config values); they are written to `w_config.csv` / `p_config.csv` under `const.*` keys so every session
  records them, and changing one needs a code change plus a replay re-run.
- **FACT** / **INFERENCE** follow the reference notes: FACT is stated by a source, INFERENCE is reasoning.
- **VERIFY ON DEVICE**: no document settles it; the check is in §15.

No number in this document is "the MIT value" unless it is marked SOURCED with a Dormio citation. MIT's own thresholds
were raw ADC deltas fitted on 5 pilot participants and called provisional by the authors ([P] §2.2, §2.3).

**Citation keys** (all files are in `docs/references/`). Each reference note cites its original source (paper section,
`file:line`, or URL); this document cites the note plus the original where it matters.

| Key | Reference note | Main originals |
|---|---|---|
| [P] | `horowitz2020_dormio_paper.md` | P20 = Horowitz et al. 2020 (PMC7590944); SR23; FS24 (Dormio Light); FS26; PhD22; SM19 |
| [C] | `opensleep_dormio_code_analysis.md` | `reference_repos/openSleep`, `reference_repos/dormio` |
| [D] | `dormio_duo_wearable_analysis.md` | `reference_repos/Dormio-Device-Duo-Wearable` |
| [S] | `samsung_health_sensor_sdk.md` | developer.samsung.com/health/sensor (SDK 1.4.1 javadoc and guides) |
| [A] | `android_platform_notes.md` | developer.android.com, AOSP android-16.0.0_r1 |
| [W] | `wear_compose_and_versions.md` | Wear Compose 1.7.1 sources, Maven metadata |
| [U] | `android_audio_stt_notes.md` | developer.android.com media/speech, AOSP audio, Android 16 CDD |
| [DA] | `device_audit.md` | read-only `adb getprop` / `dumpsys package` on the owner's SM-L500 and phone, 2026-10-27 |
| [V] | `samsung_sdk_1.4.1_verified_api.md` | the downloaded SDK 1.4.1 zip and Samsung's public API reference. It states "Where another note … disagrees with this file, this file wins", so it overrides [S] |

---

## 0. Scope and phasing

Phases follow owner requirements G20 and G21 and decision D17.

| Feature | Phase | Notes |
|---|---|---|
| Multi-module build (`:shared`, `:wear`, `:mobile`, `:samsung-health-stub`), shared `applicationId` `com.opentdi` | 1 | §10.1 |
| README: wellness / not-a-medical-device statement, developer setup (Health Sensor Service developer mode, version check, AAR download + license note) | 1 | §17.4, §11.7 |
| Watch: Samsung SDK connection + status checker (AAR missing, service missing, too old, SDK_POLICY_ERROR, permission, unsupported) with on-watch messages | 1 | §11.6 |
| Watch: HR/IBI/EDA/accelerometer trackers, `flush()` timer, health FGS + Ongoing Activity | 1 | §11, §14 |
| Watch: 25 Hz accelerometer processing (stillness, jerk, limp) and 1 Hz `BiosignalPacket` aggregation: `:shared` `AccelProcessor` + `PacketAggregator` with `AccelProcessorTest` + `PacketAggregatorTest` | 1 | §8.1–8.2, §16. Computed in Phase 1 so the very first logs carry the fields |
| Watch: SensorManager fallback (`TYPE_HEART_RATE` + `TYPE_ACCELEROMETER`) | 1 | §11.8 |
| Watch: CSV logging (raw 1 Hz samples, packets, 25 Hz-derived events, config); in Phase 1 a watch logging session is started and stopped on the watch (no Data Layer yet) | 1 | §12 |
| Phone: minimal app, one FGS, `TIMED_CUES` driven by `:shared` `TimedCueScheduler` (+ `TimedCueSchedulerTest`), TTS cues on `NIGHTSTAND_SPEAKER`, 2000 ms fade-in, hard volume cap, DND check, audio focus | 1 | Phase 1 FGS type is `mediaPlayback` only; the report window is a fixed 60 s with no recording (§4.3) |
| Phone: CSV logging (transitions, audio events, config) | 1 | §12 |
| `:shared` `HypnagogiaDetectionEngine` + synthetic generator + replay harness + their unit tests | 2 | §5, §8, §13, §16 |
| Data Layer link: commands, trigger, batched telemetry, capabilities, watch-CSV transfer at session end | 2 | §10 |
| Full DETECTION loop: watch trigger, phone countdown, cycle stages, countdown abort on movement, missed-cycle handling | 2 | §5 |
| Phone FGS gains the `microphone` type; dream recording on the built-in mic with stop rules; on-device STT | 2 | §7.8–7.9 |
| Sleep-state question "And were you asleep?" after each report **[EXT]** | 2 | §7.1 |
| Journal: one JSON per awakening + WAV + transcript + telemetry slice | 2 | §12.4 |
| Detection confidence score: shown as a number and gate chips on the phone session screen and the watch | 2 | §8.9 |
| Configurable baseline window; optional prior-day resting HR | 2 | §8.4 |
| Watch bezel settings (countdown 60–300 s, sensitivity) + ConfigSync | 2 | §10.3 |
| `SimulationBroadcastReceiver` and ADB HIL test | 2 | §13 |
| Automatic DETECTION → TIMED_CUES fallback | 2 | §4.4 |
| Theme studio (several themes, own-voice recordings for all cues) | 3 | Phase 1–2 use one theme text + TTS |
| Dashboard: Compose Canvas charts at 1 Hz (HR, RMSSD, EDA, actigraphy, baseline bands, confidence) | 3 | D14 |
| `BLUETOOTH_SLEEP_AUDIO` route + configurable keep-alive carrier + manual earbud test | 3 | "Extra audio routes" = Phase 3 per G20. The enum value exists from Phase 1 |
| `USB_WIRED_DAC` route | Deferred | Enum value exists, disabled in UI (G21) |
| `WATCH_WRIST_SPEAKER` route | Deferred | Enum value exists, disabled in UI (G21) |
| Wrist mic streaming (`/opentdi/wrist_audio_channel`) | Deferred | Path reserved only (D3) |
| Standalone watch fallback (watch plays and records when the phone is gone) | Deferred | Until then: missed-cycle rule, §5.4 |
| 60 fps charts | Deferred | G21 |
| Arm-raised "limp" task (the closest analogue of Dormio's hand-closure task) | Deferred | Protocol change; needs owner approval ([D] §10.6) |

---

## 1. Provenance

### 1.1 Sources used

| Source | How it was used | Reference note |
|---|---|---|
| Horowitz et al. 2020, *Consciousness and Cognition* (PMC7590944) | Protocol, thresholds, timings, cue wording, results | [P] §1–§7 |
| Haar Horowitz SM thesis 2019 and PhD thesis 2022 (MIT Media Lab DAM PDFs) | App presets, units of thresholds, timer mode, non-contact TDI | [P] §0 |
| Haar Horowitz et al. 2023, *Sci Rep* + Supplement | Verbatim instructions and prompts, report counts | [P] §5 |
| Bellaiche, Haar Horowitz et al. 2024, *Front Sleep* (PMC12713799), "Dormio Light" | Sensor-free timed-cue protocol (basis of `TIMED_CUES`) | [P] §3 |
| Haar Horowitz et al. 2026, *Front Sleep* (PMC13341471) | Interval between sleep onsets | [P] §1.2 |
| `tomasero/openSleep` @efad39f, `christinatchen/dormio` @7c00cc3 | Timer and wearable session logic, defaults, detection code | [C] |
| `ThingEngineer/Dormio-Device-Duo-Wearable` @baeede4 | Hardware and packet design only; it has no detection code | [D] |
| Samsung Health Sensor SDK 1.4.1 docs, javadoc, license | API, units, rates, batching, permissions, policy errors, license | [S] |
| developer.android.com + AOSP android-16.0.0_r1 | Permissions, FGS rules, Doze, broadcasts, Data Layer | [A] |
| Wear Compose 1.7.1 sources and Maven metadata | Rotary input, versions | [W] |
| developer.android.com media/speech, AOSP audio, Android 16 CDD | Routing, focus, DND, standby, recording, STT, TTS | [U] |

Could not be obtained (FACT, [P] §0): P20 Appendix A (PMC proof-of-work page), DSpace (HTTP 429), dream.media.mit.edu
(DNS failure). The instruction text was taken from the Sci Rep 2023 Supplement §1.1 instead.

### 1.2 Licenses

| Item | License finding | Source |
|---|---|---|
| `openSleep` | No LICENSE file; GitHub reports `license: null`; Swift headers say "Copyright © 2017-2019 Tomas Vega. All rights reserved." | FACT [C] §7 |
| `dormio` | No LICENSE file; `license: null`; author Christina Chen | FACT [C] §7 |
| Dormio Device Duo Wearable | "MIT license", stated only as a README line (`README.md:9-11`); no LICENSE file, no copyright line | FACT [D] §1 |


### 1.3 What that implies (INFERENCE, not legal advice)

1. **Clean-room reimplementation.** OpenTDI copies no code, UI text, cue wording from the code repos, or audio assets
   (the 23 word files and `gong.wav`) from `openSleep` or `dormio`. It reimplements the behaviour described in [C] in
   Kotlin and records or synthesises its own audio. Cue wording comes from the published papers (§7.1), not from the repos.
2. **Samsung AAR never in the repository.** `.gitignore` already contains `/wear/libs/*.aar`. Each developer downloads
   `samsung-health-sensor-sdk-v1.4.1.zip` (70.8 KB download, [S] §7) from developer.samsung.com, which also makes them
   accept the license, and copies `1.4.1/libs/samsung-health-sensor-api-1.4.1.aar` into `wear/libs/` (FACT, [V] "SDK
   package facts"; `wear/libs/README.md` step 2; the file in `wear/libs/` is 61 063 bytes). Samsung's own pages call it
   `samsung-health-sensor-api.aar` ([S] §7), so the build matches both names (§11.5). `wear/libs/README.md` documents
   the step and records the file name and SHA-256 (`893cd5d6…0344c`).

   ship source only.
4. **The compile stub** `:samsung-health-stub` is written only from the public javadoc signatures in [S] §2, with no
   copied javadoc text, and contains only the members OpenTDI calls ([S] §2.2 license caution).
5. **OpenTDI's own license is decided: GPL-3.0** (FACT: `LICENSE` line 1 "GNU GENERAL PUBLIC LICENSE Version 3"; commit
   9901e3d "Scaffold OpenTDI: … GPL-3.0") **with a GPL §7 additional permission for linking with the Samsung SDK**
   (`NOTICE`: "… linking or combining it with the Samsung Health Sensor SDK … the Samsung Health Sensor SDK is not made

   forbids making the SDK "subject to the licensing terms" of third-party software; the §7 exception is what addresses
   that (INFERENCE, not legal advice). Do not relicense. §17.2 O1 is closed.


---

## 2. What MIT Dormio actually does

All FACT unless marked. Sources are in [P] unless noted.

### 2.1 Lab protocol (P20 / SR23 / PhD22 Experiment 1)

| Step | What happens | Source |
|---|---|---|
| Setting | Afternoon nap (12:00–16:00), eye mask, experimenter in the room out of sight, 45-min sleep opportunity | [P] §1.1, P20 §2.3 |
| Instructions | Read verbatim by the experimenter (SR23-SM §1.1) | [P] §5.2 |
| Calibration | Mean of each signal "averaged over the first 120 seconds when the subject initially lies down" (P20 §2.2). App default 120 s, customizable (PhD22 p.59). **Conflict:** P20 Figure 1 says "calibration period (3:00 min)" | [P] §1.2, C1 |
| Incubation cue | "Think of a tree" on lying down (P20 §2.4); Figure 1 R1 "Remember to think of a Tree" | [P] §5.1 |
| Onset | **OR** of three absolute deltas from the baseline means: HR > 5 BPM, EDA > 4 "μSemen", flex > 8 "KΩ"; "Awakenings were performed whenever one of these thresholds was passed". No hold or debounce time anywhere | [P] §2.2 |
| Delay | "wakeups from 1:00 to 5:00 minutes after Dormio-detected sleep onset" (variable timer) | [P] §1.2 #5 |
| Wake prompt | "You're falling asleep" + "Please tell me, what's going through your mind" | [P] §5.1 |
| Report | Recorded; "Verbal reports were not subject to a time limit" (SR23) | [P] §1.2 #7 |
| Self-report | "And were you asleep?" → Awake / Halfway / Asleep | [P] §1.2 #8 |
| Re-incubation | "Remember to think of a tree" + "You can fall back asleep now" | [P] §1.2 #9 |
| Loop | Repeated for 45 minutes; then the experimenter tells the participant to wake up fully | [P] §1.2 #10–11 |

### 2.2 Sensors and detection

| Item | Dormio | Source |
|---|---|---|
| HR | Pulse Sensor Amped (PPG) on the middle finger | [P] §2.1 |
| Muscle tone | Flex sensor on a finger (middle vs index: conflict C2) around a **hand held closed on purpose**; onset = the hand slowly opening as the task fails | [P] §2.1 |
| EDA | Two electrodes on "the bottom of the wrist" (ventral wrist) | [P] §2.1 |
| Rate | 100 Hz per sensor | [P] §2.1 |
| Units | "Electrodermal Activity and Flex units are raw analog-to-digital units" (PhD22 p.59) | [P] §2.2 |
| Direction | Not stated; PhD22 p.59 "shifts either up or down in EDA". The web code uses `Math.abs` | [P] §2.2; [C] §5.2 |
| Web code rule | Every 1 s: mean of the last 100 raw samples (EDA, flex) and last 10 BPM values vs calibration means; fire if \|ΔEDA\| ≥ 4 OR \|Δflex\| ≥ 5 OR \|ΔHR\| ≥ 6 BPM. Calibration default 3 min, arithmetic mean only, no SD | [C] §3.3, §5.2 (`script_wearable.js:690-731`, `:221-230`) |
| Later cycles | In the web wearable modes and the iOS guided flow, only the **first** awakening is sensor-gated; later ones are timer-driven | [C] §0 item 4 |
| Measured separation | HR delta Sleep −2.89 ± 22.94 BPM vs Wake +10.01 ± 18.7 (P20 Fig. 4): heavy overlap | [P] §2.3 |

### 2.3 Timings

| Quantity | Value | Source |
|---|---|---|
| Baseline | 120 s (text), 3:00 (P20 Fig. 1), 3 min (web code default) | [P] §4 #1–1c; [C] §3.3 |
| Onset → wake | variable 1–5 min (lab); app presets shallow 1 / medium 1.5 / deep 3 min; v1 pilot fixed 3 min | [P] §4 #8–10 |
| First sleep onset (measured) | 10.3 ± 6.4 min | [P] §4 #25 |
| Interval between onset reports (measured) | 8.23 ± 2.32 min, range 6–12 | [P] §4 #26 |
| Reports per sleep participant in 45 min | 5.15 (SI), 5.75 (SN) | [P] §4 #22 |
| Wake control | 7 min + variable 1–5 min | [P] §4 #27 |

### 2.4 Timer-only, non-contact protocol ("Dormio Light", FS24 = PhD22 Experiment 2)

- PhD22 abstract: "a non-contact version of TDI, in which dream incubation is effective simply via timed audio cues on a
  Dormio web interface without any wearable sleep staging device" ([P] §3.1).
- Protocol (FS24): the user enters a sleep-latency window (example 10–15 min), the site picks a random time in it; then
  "stay in hypnagogia/FMT for 3 min, record 4 rounds … take 60 s to provide verbal reports … take 7 min to fall back into
  sleep" over an hour-long period, cues in the participant's own voice ([P] §3.2).
- Code (`script_timer.js:12-19`): loops 3 (the study used 4), hypna-latency 3 min, time-until-sleep 10–15 min (random
  inclusive whole minutes), time-between-sleep 7 min, recording 60 s. Cue order: prompt at start (`:196`), prompt at
  each dwell start (`:304`), wake message (`:332`), prompt after each report (`:366`), gong ×3 at the end (`:376`,
  `:429-435`) ([C] §3.2).
- Result: 73/80 (91 %) had at least one incorporation vs 34/37 (92 %) in the lab, χ²(1) = 0.01, p = 0.91. Limitation:
  "the timer method is likely less sensitive"; 52 of 132 participants excluded on self-report ([P] §3.2).

### 2.5 CLAUDE.md numbers that no source contains

NOT FOUND in any paper, thesis or repo ([P] §4.1; [C] §10; [D] §11): the 7 % HR drop, the 15 s HR moving average
(15 s windows appear only in HBOSS), the 15 % RMSSD rise (no HRV measure exists anywhere in Dormio), the 30 s EDA slope
and 1.5σ spike rule, the 60 s accelerometer SD (Dormio had no accelerometer), the < 0.5 s hypnic-jerk detector, the
20 s / 10 s holds, AND logic, countdown abort on movement, named Low / "MIT Standard" / High presets, 4 s silence /
45 s maximum recording, the 2000 ms fade-in and the −75 dBFS carrier. The 180 s countdown is partly sourced (the "deep"
preset, the v1 pilot, the Dormio Light dwell); the 60–300 s range is sourced (= 1–5 min).

---

## 3. Sensor mapping: finger sensors → Galaxy Watch8 Classic

| Dormio channel | OpenTDI channel | Consequence | Use in the rule |
|---|---|---|---|
| Finger flex sensor on a hand **held closed on purpose**; onset = hand opens as the task fails ([P] §2.1) | 25 Hz wrist accelerometer, processed on the watch: `stillnessScore`, `jerkEvent`, `limpEvent` (§8.1) | INFERENCE: a relaxed but awake person is also still, so stillness is necessary but not sufficient. The flex task measured *failure of an active task*; stillness measures absence of movement. Only an arm-raised task would be a close analogue (deferred, [D] §10.6) | **Gate** (stillness, required). Jerk and limp are **bonus only** |
| Finger PPG, BPM from raw ADC peaks ([C] §5.1) | Samsung `HEART_RATE_CONTINUOUS`: HR (bpm), IBI list (ms), status codes ([S] §2.3) | Samsung does beat detection; IBIs allow RMSSD, which Dormio never used. Delta in BPM transfers in kind; the percentage form is ours | **Required signal** (HR drop OR RMSSD rise) |
| Ventral-wrist EDA via custom divider, raw ADC counts ([P] §2.1–2.2) | Samsung `EDA_CONTINUOUS` skin conductance in µS on the **dorsal** wrist, Watch8+ only ([S] §2.3) | No absolute threshold transfers: different site, electrodes and units ([D] §10.5). Tonic decline at rest is near-constant, so it has little information (owner D11). Wrist EDA literature conflicts (Sano 2014 vs van der Mee 2021, [D] §10.5) | **Bonus only**: may shorten the hold and raise confidence; never triggers alone |

**Why EDA is bonus-only** (owner requirement 11, decision D7): "slope ≤ 0 with no spikes" holds at rest almost all the
time, so as an independent trigger it would fire on relaxation alone. The simulation in §13 shows the engine never
triggers on EDA when HR/IBI stays near baseline (AWAKE_CONTROL keeps EDA flat, so `E` is often true, and no onset is
emitted).

**Deliberate deviation from Dormio (owner D11).** Dormio used OR with no hold. OpenTDI requires stillness AND HR/IBI,
held for a time. That is stricter and is justified by the heavy Sleep/Wake overlap in P20 Fig. 4 ([P] §2.3, §8 item 2).

---

## 4. Session modes

`SessionMode` has two values (D9).

| Mode | Driven by | Needs watch | Purpose |
|---|---|---|---|
| `DETECTION` | Watch `HypnagogiaDetectionEngine` (authoritative) | Yes | Wearable-driven TDI |
| `TIMED_CUES` | Phone `TimedCueScheduler` (pure, `:shared`) | No (if present, the watch only logs and mirrors: below) | Sensor-free control condition; automatic fallback when sensors fail; works with no watch |

**Watch in a `TIMED_CUES` session** **[EXT]**. `engine.startSession(sessionId, mode = TIMED_CUES)` goes straight to
`AWAKE_INCUBATING` (row 2): no calibration, onset is never evaluated, no trigger is ever sent, and rows 6, 7, 9, 16 and
18 do not apply. The engine still computes `DetectionScore` from the packets for logging (`detectionActive = false`).
It mirrors the phone: the phone sends `CycleStage(cycle, DWELL_START)` at each dwell start (row T1), which moves the
watch to `HYPNAGOGIA_DETECTED` (`TIMED_DWELL`), then the usual `WAKING_USER` … `CYCLE_COMPLETE` stages. After an
automatic fallback (§4.4) the watch engine stays in `IDLE` with the session still open (row 21) and only logs packets
and the stages it receives.

### 4.1 TIMED_CUES parameters

| Parameter | Default | Range | Provenance |
|---|---|---|---|
| `firstSleepMinMin` | 10 min | 1–60 | SOURCED: `script_timer.js:15` ([C] §3.2); FS24 example "10 to 15 min" ([P] §3.2) |
| `firstSleepMaxMin` | 15 min | `firstSleepMinMin`–90 | SOURCED: `script_timer.js:16`; FS24 |
| First latency L | uniform random **whole minute** in [min, max], inclusive | — | SOURCED: `getRandomInt` `script_timer.js:680-684` ([C] §3.2). OpenTDI seeds the RNG with the session ID so tests are deterministic **[EXT]** |
| Dwell (= `countdownS`) | 180 s | 60–300, step 15 | SOURCED: hypna-latency 3 min (`script_timer.js:14`); FS24 "stay in hypnagogia/FMT for 3 min" |
| `betweenSleepS` | 420 s | 60–1200 | SOURCED: `script_timer.js:17`; FS24 "take 7 min to fall back into sleep" |
| Loops (= `maxCycles`) | 4 | 1–10 | SOURCED: FS24 "record 4 rounds" (the code default is 3, `script_timer.js:13`; conflict C10 in [P] §9) |
| Report | Phase 1: fixed 60 s window, no recording. Phase 2+: the same recording stop rules as DETECTION (§7.8) | — | 60 s SOURCED (FS24, `script_timer.js:18`). Using the DETECTION rules in Phase 2 is **[DEV]** from FS24 so both modes are directly comparable as control vs treatment |

### 4.2 TIMED_CUES timeline (derived from the MIT timer mode)

Times are relative to Start. L is the drawn latency; R_k is the real duration of cycle k's wake + report + re-incubation
(variable, roughly 20–70 s).

| Time | State (same `TdiState` values as DETECTION) | Audio | Source |
|---|---|---|---|
| 0 | `AWAKE_INCUBATING` (no calibration) | `INITIAL_INCUBATION` "Remember to think of [THEME]." | `script_timer.js:196` |
| L | `HYPNAGOGIA_DETECTED` (synthetic onset, `onsetTimestampMs` = now, `confidence` = null) | `INITIAL_INCUBATION` again (dwell cue) | `:304` |
| L + 180 s | `WAKING_USER` | `WAKE_PROMPT` | `:313-332` |
| when the prompt ends | `RECORDING_DREAM` | (record; then `SLEEP_STATE_QUESTION` if enabled) | `:335-336` |
| report end | `RE_INCUBATING` | `RE_INCUBATION` "Remember to think of [THEME]. You can fall back asleep now." | `:366` |
| cue end | cycle complete → `AWAKE_INCUBATING` | — | |
| cycle end + 420 s | `HYPNAGOGIA_DETECTED` | `INITIAL_INCUBATION` (dwell cue) | `:282-304` |
| … repeat until 4 cycles | | | |
| after cycle 4 | `SESSION_COMPLETE` | `SESSION_END` chime ×3 (own asset) | `:376`, `:429-435` |

Total ≈ L + 4 × (3 min + R) + 3 × 7 min ≈ L + 37 min ≈ 47–52 min with R ≈ 1 min (INFERENCE, the same arithmetic as
[P] §3.3; it fits FS24's "hour-long").

Bugs in `script_timer.js` that OpenTDI does **not** copy ([C] §3.2): Stop does not cancel pending timers; the report
window is counted from the wake prompt's start, not its end; several countdowns run at once; the gong counter is never
reset. In OpenTDI every timer is owned by one cancellable coroutine scope per session, and the report is timed from the
prompt's end.

### 4.3 Phase 1 minimal phone app

Phase 1 `TIMED_CUES` is the same timeline with `RECORDING_DREAM` replaced by a fixed 60 s "report window" during which
nothing is recorded, and no `SLEEP_STATE_QUESTION`. The FGS is started with type `mediaPlayback` only; `microphone` and
`RECORD_AUDIO` arrive in Phase 2.

### 4.4 Automatic fallback DETECTION → TIMED_CUES (Phase 2)

The phone switches the running session to `TIMED_CUES` (keeping the cycle count and the session ID) when any of these
happens, if `autoFallbackToTimed` = true (default):

| Trigger | Seen by the phone as |
|---|---|
| The engine enters `IDLE` mid-session with reason `BASELINE_INSUFFICIENT_*`, `HR_LOST`, `ACCEL_LOST` or `NO_SENSORS` | a telemetry frame with that `state` + `stateReason` (state-change batches are acknowledged, §10.2) |
| No telemetry batch for `watchLinkTimeoutS` = 120 s (GUESSED: Data Layer reconnects can take "up to 4 minutes", [A] §4.5) | phone timer |
| The user taps "Switch to timed cues" | UI |

After the switch the next dwell starts at `max(now + 60 s, anchor + interval)`, where anchor is the session start (no
cycle done yet, interval = a freshly drawn L) or the last cycle's end (interval = `betweenSleepS`). Triggers that arrive
from the watch later are logged and answered with `Ack(ok = false, code = "MODE_TIMED")`.

---

## 5. State machine

### 5.1 States

| `TdiState` | Meaning | Detection evaluated? |
|---|---|---|
| `IDLE` | No session, or detection unavailable mid-session (with a reason; the session stays open until `AbortSession`, `TIME_LIMIT` or `sessionMaxMin`, row 21) | No |
| `CALIBRATING_BASELINE` | Collecting the baseline window (DETECTION only) | No |
| `AWAKE_INCUBATING` | Waiting for onset; refractory may apply | Yes (unless refractory) |
| `HYPNAGOGIA_DETECTED` | Onset detected; the phone runs the countdown; the watch watches for major movement | Movement only |
| `WAKING_USER` | Wake prompt playing | No (suspended, D4) |
| `RECORDING_DREAM` | Recording the report (and the sleep-state answer) | No |
| `RE_INCUBATING` | Re-incubation cue playing | No |
| `PAUSED` | User paused; baseline kept | No |
| `SESSION_COMPLETE` | Terminal for this session (reason attached) | No |

### 5.2 ASCII diagram (DETECTION; W = watch engine decides, P = phone decides)

The transition table (§5.3) is authoritative. The diagram leaves out rows 10 (`PHONE_REJECTED`), 15 (`CYCLE_ABORTED`),
16 (`STAGE_TIMEOUT`), 17 (forward-stage jump) and 22 (`TIME_LIMIT`).

```
                 StartSession (either device)
   IDLE ───────────────────────────────────────► CALIBRATING_BASELINE ──W: invalid at W─┐
    ▲  ▲  ▲  W: still invalid at 2W (row 5)         │   ▲ (extend to 2W once, row 4)    │
    │  │  └─────────────────────────────────────────┤   └───────────────────────────────┘
    │  │                                            │ W: window done, baseline valid
    │  │ W: HR lost 300 s, accel lost 60 s (row 18) ▼
    │  └─────────────────────────────────────── AWAKE_INCUBATING ◄─────────────────────────┐
    │     (phone falls back to TIMED_CUES)          │ W: gate ∧ (HR drop ∨ RMSSD rise)      │
    │                                               │    held holdReq s, not in refractory │
    │                                               ▼                                      │
    │                                      HYPNAGOGIA_DETECTED ──W: major movement────────►┤ (AbortCountdown)
    │                                               │          ──W: no WAKING_USER by      │
    │                                               │              countdown+15 s ────────►┤ (missed cycle)
    │                                               │ P: countdown expired                 │
    │                                               ▼                                      │
    │                                         WAKING_USER                                  │
    │                                               │ P: wake prompt ended                 │
    │                                               ▼                                      │
    │                                        RECORDING_DREAM                               │
    │                                               │ P: stop rule fired                   │
    │                                               ▼                                      │
    │                                         RE_INCUBATING ──P: cue done, cycles < max───►┘ (refractory)
    │                                               │ P: cue done, cycles == max
    │                                               ▼
    └──────── StartSession (new session) ──── SESSION_COMPLETE ◄── AbortSession (any state)

   PAUSED: entered from any active state by PauseSession; ResumeSession → AWAKE_INCUBATING
           (or CALIBRATING_BASELINE from the start if the baseline was not finished).
```

### 5.3 Transition table

`t` is the timestamp of the packet being processed (the engine's only clock, D5). Owner = the device whose decision
causes the transition; the other device mirrors it.

| # | From | Event / guard | To | Actions / emitted events | Owner |
|---|---|---|---|---|---|
| 1 | `IDLE` or `SESSION_COMPLETE` | `StartSession`, mode `DETECTION`, a sensor source is usable | `CALIBRATING_BASELINE` | reset all buffers; `calStart` = timestamp of the first packet received after the call; `StateChanged` | W |
| 2 | `IDLE` or `SESSION_COMPLETE` | `StartSession`, mode `TIMED_CUES` | `AWAKE_INCUBATING` | phone: play `INITIAL_INCUBATION`, draw L; watch (if present): `startSession(id, TIMED_CUES)`, log + mirror only (§4) | P |
| 3 | `CALIBRATING_BASELINE` | `t − calStart ≥ W·1000`, baseline valid (§8.4) | `AWAKE_INCUBATING` | compute `BaselineStats`; `BaselineReady`; this packet is then evaluated in `AWAKE_INCUBATING`; phone plays `INITIAL_INCUBATION` when it sees the state | W |
| 4 | `CALIBRATING_BASELINE` | `t − calStart ≥ W·1000`, invalid, not yet extended | `CALIBRATING_BASELINE` | window becomes 2W (all packets so far kept); `BaselineExtended(reason)` | W |
| 5 | `CALIBRATING_BASELINE` | `t − calStart ≥ 2W·1000` and still invalid | `IDLE` (`BASELINE_INSUFFICIENT_MOTION` or `_HR`) | `DetectionUnavailable`; phone falls back (§4.4) | W |
| 6 | `AWAKE_INCUBATING` | `t ≥ refractoryUntil` ∧ `hold ≥ holdReq` (§8.7) | `HYPNAGOGIA_DETECTED` | `OnsetDetected(trigger)`; `onsetT = t`; watch sends `HypnagogiaTrigger` (§10.2) | W |
| 7 | `HYPNAGOGIA_DETECTED` | major movement (§8.10) | `AWAKE_INCUBATING` (`MAJOR_MOVEMENT`) | `CountdownAborted`; send `AbortCountdown(cycle, MAJOR_MOVEMENT)`; `hold = 0`; no refractory | W |
| 8 | `HYPNAGOGIA_DETECTED` | phone countdown reaches `countdownMs` | `WAKING_USER` | phone sends `CycleStage(WAKING_USER)`, plays `WAKE_PROMPT` | P |
| 9 | `HYPNAGOGIA_DETECTED` | `t − onsetT ≥ countdownMs + 15 000` and no `WAKING_USER` | `AWAKE_INCUBATING` (`MISSED_CYCLE`) | `CycleMissed`; `refractoryUntil = t + refractoryS·1000`; cycle number unchanged | W |
| 10 | `HYPNAGOGIA_DETECTED` | trigger answered `Ack(ok = false)` | `AWAKE_INCUBATING` (`PHONE_REJECTED`) | log; refractory as row 9 | W |
| 11 | `WAKING_USER` | wake prompt finished, recorder started | `RECORDING_DREAM` | `CycleStage(RECORDING_DREAM)` | P |
| 12 | `RECORDING_DREAM` | stop rule fired (§7.8), sleep-state answer done | `RE_INCUBATING` | play `RE_INCUBATION`; `CycleStage(RE_INCUBATING)` | P |
| 13 | `RE_INCUBATING` | cue finished, `completedCycles + 1 < maxCycles` | `AWAKE_INCUBATING` | `CycleStage(CYCLE_COMPLETE, COMPLETED)` (acknowledged); `completedCycles++`; `refractoryUntil = t + refractoryS·1000`; phone starts STT | P |
| 14 | `RE_INCUBATING` | cue finished, `completedCycles + 1 == maxCycles` | `SESSION_COMPLETE` (`MAX_CYCLES`) | as 13, then phone plays `SESSION_END` | P |
| 15 | `WAKING_USER`, `RECORDING_DREAM`, `RE_INCUBATING` | `CycleStage(CYCLE_COMPLETE, ABORTED_AUDIO)` (focus denied, route lost, capture silenced) | `AWAKE_INCUBATING` (`CYCLE_ABORTED`) | cycle not counted; refractory | P |
| 16 | `WAKING_USER`, `RECORDING_DREAM`, `RE_INCUBATING` | engine: no `CYCLE_COMPLETE` within `cycleStageTimeoutS` = 180 s of `WAKING_USER` | `AWAKE_INCUBATING` or `SESSION_COMPLETE` | treat as `CYCLE_COMPLETE` with reason `STAGE_TIMEOUT` (the phone most likely finished; the message was lost). A `CycleStage(CYCLE_COMPLETE)` that arrives later for a cycle already closed this way is logged (`LATE_STAGE_IGNORED`) and ignored: no second `completedCycles++`, no new refractory. `validate()` keeps the timeout longer than the longest phone cycle (§8.14) | W |
| 17 | `HYPNAGOGIA_DETECTED` | `CycleStage` with a later stage arrives before `WAKING_USER` (lost message) | that stage | accept any forward stage | P |
| 18 | `AWAKE_INCUBATING` | `HR_ma` null for `hrLostS` (300) consecutive s, or no non-null `stillnessScore` for `accelLostS` (60) consecutive s (sensor missing, not "user moving") | `IDLE` (`HR_LOST` / `ACCEL_LOST`) | `DetectionUnavailable`; phone falls back; session stays open | W |
| 19 | any active state (`CALIBRATING_BASELINE` … `RE_INCUBATING`) | `PauseSession` (either device) | `PAUSED` | phone cancels the countdown, stops audio and recording; `resumeTarget` = `CALIBRATING_BASELINE` if the baseline is not ready, else `AWAKE_INCUBATING` | P or W |
| 20 | `PAUSED` | `ResumeSession` | `resumeTarget` | baseline kept; partial calibration restarts from 0; refractory applied when the target is `AWAKE_INCUBATING` | P or W |
| 21 | any state while a session is open, including `IDLE` after `DetectionUnavailable` (rows 5, 18); not `SESSION_COMPLETE` or a session-less `IDLE` | `AbortSession(reason)` (also the phone's `TIME_LIMIT` after a fallback) | `SESSION_COMPLETE` (reason) | stop audio, close CSVs, phone writes the journal, watch CSV transfer (§12.5) | P or W |
| 22 | `AWAKE_INCUBATING` | phone: session time ≥ `sessionMaxMin` | `SESSION_COMPLETE` (`TIME_LIMIT`) | phone sends `AbortSession(TIME_LIMIT)`. If a cycle is running, it waits for that cycle to complete | P |

`TIMED_CUES` uses rows 2, 8, 11–15, 19–22 unchanged, plus:

| # | From | Event / guard | To | Actions | Owner |
|---|---|---|---|---|---|
| T1 | `AWAKE_INCUBATING` | `now ≥ anchor + interval` (L for cycle 1, `betweenSleepS` after) | `HYPNAGOGIA_DETECTED` | play `INITIAL_INCUBATION`; start the dwell countdown (`countdownS`); send `CycleStage(cycle, DWELL_START)` if a watch is in the session **[EXT]** | P |
| T2 | `HYPNAGOGIA_DETECTED` | dwell elapsed | `WAKING_USER` | as row 8 | P |

There is no movement abort, no refractory and no missed-cycle rule in `TIMED_CUES` (no sensors).

---

## 6. Timers

| Timer | Default | Range | Owner | Clock | Provenance |
|---|---|---|---|---|---|
| Baseline window W | 180 s | 60–600, step 30 | Watch engine | packet time | SOURCED: web `calibration-time` 3 min (`script_wearable.js:225`, [C] §3.3); P20 Fig. 1 "3:00 min". P20 text says 120 s ([P] §4 #1), so 120 is within range. Configurable per owner 12 |
| Baseline extension | +W once (max 2W) | — | Watch engine | packet time | GUESSED: one retry before falling back |
| N1 countdown (onset → wake) | 180 s | 60–300, step 15 (bezel) | Phone service | elapsed-realtime under wake lock | Default SOURCED: app "deep (3 minutes)" (PhD22 p.56), v1 "3-minute timer" (SM19 p.73), Dormio Light dwell 3 min; range SOURCED: "1:00 to 5:00 minutes" (P20 §2.4) ([P] §4 #8–10). Note: P20's lab protocol drew it at random per loop; OpenTDI uses a fixed value (§17.2 O4) |
| TIMED dwell | = countdown | same | Phone | elapsed-realtime | SOURCED (§4.1) |
| TIMED first latency L | 10–15 min, whole minutes | 1–90 | Phone | elapsed-realtime | SOURCED (§4.1) |
| TIMED between-sleep | 420 s | 60–1200 | Phone | elapsed-realtime | SOURCED (§4.1) |
| Missed-cycle grace | 15 s after countdown | 5–60 | Watch engine | packet time | GUESSED (D4) |
| Cycle-stage timeout | 180 s after `WAKING_USER` | 90–600 | Watch engine | packet time | GUESSED: longer than prompt (~6 s) + 45 s recording + 8 s answer + re-incubation cue (~8 s) + margin. Cross-rule: `cycleStageTimeoutS·1000 ≥ maxMs + answerMaxMs + 60 000` (§8.14), so raising `maxMs` needs a longer timeout |
| Refractory after a cycle | 120 s | 0–600 | Watch engine | packet time | GUESSED. Context: measured onset-to-onset 6–12 min ([P] §4 #26) |
| Hold (core held) | 20 s; 10 s with a bonus | preset table §8.11 | Watch engine | packet count | GUESSED (CLAUDE.md, NOT FOUND in sources) |
| HR moving average | 15 s | 5–60 | Watch engine | packet time | GUESSED. Dormio web used about 10 s (10 BPM values, [C] §5.2) |
| Stillness gate window | 30 s | 10–120 | Watch engine | packet time | GUESSED **[DEV from CLAUDE.md's 60 s]**: 30 s lets the gate recover from one fidget well within a 3-min awake period; must be ≥ the longest hold (30 s, LOW) |
| Major-movement window | 3 of the last 5 s | — | Watch engine | packet time | GUESSED (§8.10) |
| Jerk/limp bonus lookback | 120 s | 30–300 | Watch engine | packet time | GUESSED |
| EDA slope window | 30 s | 15–120 | Watch engine | packet time | GUESSED (CLAUDE.md) |
| RMSSD window | 60 s | 30–300 | Watch aggregator | IBI time | GUESSED: ≥ 30 s of clean IBIs advised ([D] §10.4) |
| HR sample-and-hold max | 5 s | 1–15 | Watch aggregator | DataPoint time | GUESSED |
| HR lost → fallback | 300 s | 60–900 | Watch engine | packet time | GUESSED |
| Accel lost → fallback | 60 s | 10–300 | Watch engine | packet time | GUESSED |
| SDK `flush()` interval (session) | 5 s | 1–30 | Watch FGS | `SystemClock.elapsedRealtime` under wake lock | GUESSED: Samsung batches screen-off data ("600 samples every 10 minutes", [S] §2.3) |
| Packet lateness deadline | 2 × flush interval = 10 s | — | Watch aggregator | wall clock | GUESSED |
| Telemetry batch flush | 5 s, and immediately on a state change | 1–30 | Watch FGS | wall clock | D3 |
| Trigger retry | attempts at onset + 0, 2, 4, 8 s (offsets from the first send), each with a 2 s app-level reply timeout; stop at countdown + 15 s | — | Watch FGS | elapsed-realtime | GUESSED (structural, §8.15); `sendRequest` itself fails only after 1 min ([A] §4.3), so the app timeout is needed. Details in §10.2 |
| Watch link timeout (phone) | 120 s | 30–600 | Phone | elapsed-realtime | GUESSED ([A] §4.5: reconnect "up to 4 minutes") |
| Fade-in | 2000 ms | 0–5000 | Phone audio | sample count | GUESSED (CLAUDE.md, NOT FOUND in sources) |
| Carrier lead before the first cue | 10 s | 0–60 | Phone audio | elapsed-realtime | GUESSED ([U] §4.5) |
| Recording: trailing silence after speech | 4000 ms | 1000–10 000 | Phone recorder | sample count | GUESSED (CLAUDE.md). Dormio: no limit in the lab, fixed 60 s / 30 s on the web ([P] §4 #12–14; [U] §8.2) |
| Recording maximum | 45 000 ms | 10 000–180 000 | Phone recorder | sample count | GUESSED (CLAUDE.md) |
| Recording no-speech timeout | 15 000 ms | 5000–60 000 | Phone recorder | sample count | GUESSED ([U] §8.2) |
| Noise-floor seed | first 400 ms after the prompt ends | 300–500 | Phone recorder | sample count | GUESSED ([U] §8.2) |
| Sleep-state answer: max / silence / no-speech | 8000 / 1500 / 6000 ms | — | Phone recorder | sample count | GUESSED **[EXT]** |
| Phase 1 TIMED report window | 60 s | 30–90 | Phone | elapsed-realtime | SOURCED (FS24; `script_timer.js:18`) |
| Session maximum | 60 min | 15–180 | Phone | elapsed-realtime | GUESSED. Context: lab 45-min opportunity ([P] §4 #16); Dormio Light ≈ 1 h ([P] §4 #18) |
| End chime | 3 plays | 1–3 (off = `endAlarm = NONE`) | Phone | — | SOURCED: gong ×3 (`script_timer.js:429-435`); PhD22 p.58 allows no alarm ([P] §1.2 #11). Own audio asset |

---

## 7. Audio (phone, `:mobile`)

### 7.1 Cue wording (CLAUDE.md reconciled with the literature)

`[THEME]` is the user's theme text, for example "a tree". All cues are rendered to WAV ahead of time (§7.3).

| `PromptCue` | OpenTDI text | Literature | Reconciliation |
|---|---|---|---|
| `INITIAL_INCUBATION` | "Remember to think of [THEME]." | P20 §2.4 "Think of a tree"; P20 Fig. 1 R1 "Remember to think of a Tree"; FS24 "Remember to think of a Fork/Tree" ([P] §5.1) | SOURCED. CLAUDE.md's "Remember to think of [THEME]..." kept, without the trailing ellipsis |
| `WAKE_PROMPT` | "You're falling asleep. Please tell me, what's going through your mind?" | P20 §2.4 "You're falling asleep," + "Please tell me, what's going through your mind" ([P] §5.1) | SOURCED, verbatim. Matches CLAUDE.md |
| `SLEEP_STATE_QUESTION` **[EXT]** | "And were you asleep?" | P20 §2.4, answers Awake / Halfway / Asleep ([P] §1.2 #8) | SOURCED. CLAUDE.md omits it; it is the only per-awakening ground truth MIT used ([P] §8 item 5). `askSleepState`, default true |
| `RE_INCUBATION` | "Remember to think of [THEME]. You can fall back asleep now." | P20 §2.4 "Remember to think of a tree" + "You can fall back asleep now" ([P] §5.1) | SOURCED. Matches CLAUDE.md |
| `SESSION_END` | soft chime ×3, then "The session is over. You can wake up now." | Code: gong ×3 (`script_timer.js:429-435`); lab: the experimenter "instructed the participant to wake up fully" ([P] §1.2 #11) | Chime count SOURCED; the wording is ours (GUESSED). `endAlarm` = `CHIME` (default) or `NONE` (PhD22 p.58: "set no alarm and continue sleeping") |

The session-start screen shows the SR23 instruction text adapted to the theme (verbatim source in [P] §5.2), and the
FS24 screening advice ([P] §3.2, Safety) next to the wellness statement (§17.4).

**[EXT]** (owner, 2026-10-27) The user can replace the words of any cue (`audio.cueTexts`); the texts above are the
prefilled defaults and come back with "Reset to default" or a blank field. The defaults are prefilled with the literal
`[THEME]`, which is replaced by the theme when the cues are prepared, so edited cues follow a later theme change.

### 7.2 When each cue plays

| Moment | DETECTION | TIMED_CUES |
|---|---|---|
| Session start | — (calibrating; a cue would disturb the baseline) | `INITIAL_INCUBATION` |
| Baseline done (`CALIBRATING_BASELINE` → `AWAKE_INCUBATING`) | `INITIAL_INCUBATION` (PhD22 p.58: "Once the calibration period is done, the dream prompt message plays once", [P] §1.2 #3) | n/a |
| Onset / dwell start | — (no cue at a detected onset; Dormio's web wearable mode plays one there, [C] §4; open decision O5) | `INITIAL_INCUBATION` (dwell cue, `script_timer.js:304`) |
| Countdown or dwell expiry | `WAKE_PROMPT` | `WAKE_PROMPT` |
| Report finished | `SLEEP_STATE_QUESTION` (if enabled), then `RE_INCUBATION` | same |
| Last cycle complete, or time limit | `SESSION_END` | `SESSION_END` |

### 7.3 Rendering cues

- Phases 1–2: TTS via `TextToSpeech.synthesizeToFile`, only with a voice where `Voice.isNetworkConnectionRequired()
  == false`. Cues are rendered when the theme is created or edited, **never during the nap** ([U] §7).
- The sample rate and encoding are read from `UtteranceProgressListener.onBeginSynthesis` (API 24) or the WAV header,
  never hard-coded. A TTS file with format tag 1 and 32 bits per sample holds float data when `onBeginSynthesis`
  reported `ENCODING_PCM_FLOAT` (FACT: `FileSynthesisCallback.java:285-312` writes tag 1 for every encoding, [U] §7).
- **[EXT]** (owner, 2026-10-27: the first cues spoke "way too fast") Slow, separated phrases: each cue's text is split
  after `.` `?` `!` `;` `:` or a comma followed by a space, and at line breaks, keeping the punctuation for prosody.
  Each phrase is one utterance at `TextToSpeech.setSpeechRate(audio.ttsSpeechRate)` (default 0.75); its leading and
  trailing samples below −50 dBFS are trimmed, and the phrases are joined with exactly `audio.phrasePauseMs` (default
  700 ms) of digital silence. Trimmed speech would start at the cue's first sample, where the §7.4 fade makes a short
  first phrase ("You are dreaming.") inaudible, so the first phrase follows ¾ · `fadeInMs` of digital silence
  (`const.SPEECH_LEAD_FADE_FRACTION`): the speech enters where `G_fade` reaches −15 dB (1.5 s by default).
- The cue set stores a fingerprint (SHA-256) of the theme, voice, rate, pause, fade-in and every cue's text; when any of
  them changes, the app asks for Prepare cues again and refuses to start with the old set.
- Every cue is peak-normalised to −3 dBFS at creation ([U] §3.4 step 1), then stored as a PCM16 mono WAV.
- Phase 3 theme studio: own-voice recordings for every cue (experimenter voice and own voice were both used "without a
  significant change in efficacy", PhD22 p.56, [P] §5.1), normalised the same way.

### 7.4 Playback and the 2000 ms logarithmic fade-in

`AudioTrack`, `MODE_STREAM`, `ENCODING_PCM_FLOAT`, `USAGE_MEDIA`, `CONTENT_TYPE_SPEECH`, `PERFORMANCE_MODE_NONE`
([U] §0 D1, §2). The app applies the envelope to the float samples itself, so the gain curve is exact and unit-testable.

```
T  = fadeInMs / 1000                    (default 2.000 s; GUESSED)
tn = n / fs                             (n = 0 is the first sample of the cue)

G_fade(tn) = 0                                   if n == 0            (true zero at t = 0)
           = 10^((-60 + 60 * tn / T) / 20)       if 0 < tn < T        (linear in dB, -60 dB -> 0 dB)
           = 1                                    if tn >= T
           (T = 0 disables the fade: G_fade = 1 for every sample)

G_target   = min(userGain, routeCapGain[route], HARD_MAX_GAIN = 1.0)
out[n]     = clamp(x[n] * G_fade(tn) * G_target, -1.0, 1.0)
```

| t | 0 | 1 ms | 500 ms | 1000 ms | 1500 ms | ≥ 2000 ms |
|---|---|---|---|---|---|---|
| G_fade | 0 | 0.00100 (−59.97 dB) | 0.00562 (−45 dB) | 0.0316 (−30 dB) | 0.178 (−15 dB) | 1.0 |

`track.setVolume()` stays at 1.0; all gain is in the samples. `VolumeShaper` is not used (it would also work, [U] §2.5,
but the in-sample curve is easier to test). Consequence (INFERENCE): speech from the first sample would play its first
≈ 1 s below −30 dB, so a short first phrase would be lost. Since 2026-10-27 the rendered cue starts with ¾ T of
silence (§7.3), so the envelope still rises from true zero but the speech enters at −15 dB and reaches full level ¼ T
later (0.5 s by default). The fade length stays configurable (0–5000 ms) and the effect is checked on hardware (§15,
open decision O6).

### 7.5 Keep-alive carrier (Phase 3, `BLUETOOTH_SLEEP_AUDIO` only)

Why: AudioFlinger puts an output with no active tracks into standby after `kDefaultStandbyTimeInNsecs` = 3 s
(`Threads.cpp:252`), which suspends A2DP, or tears down LE Audio ISO after 500 ms; restarting can clip the first
syllable or pop (FACT, [U] §4.1). A continuously playing track keeps the phone side out of standby whatever the sample
values; a **non-zero** level is a hedge against the headset's own silence detection (INFERENCE, [U] §4.2). The level is
unvalidated (owner 19).

| Setting | Default | Range | Provenance |
|---|---|---|---|
| `carrierMode` | `NOISE` on `BLUETOOTH_SLEEP_AUDIO`; `OFF` on `NIGHTSTAND_SPEAKER` | `OFF`, `NOISE`, `ZERO` (`ZERO` = all-zero samples, for the test only) | GUESSED ([U] §4.5) |
| `carrierLevelDbfs` | −75 | −90 to −50, step 5 | GUESSED ([U] §4.5). CLAUDE.md's −75 is NOT FOUND in any Dormio source |
| `carrierLeadS` | 10 s | 0–60 | GUESSED |

Algorithm (deterministic, unit-tested):

```
fs = 48000; N = 10 * fs                         // a 10 s loop buffer, generated once per session
rng = xorshift32(seed = sessionId.hashCode(), or 1 if that is 0)
w[n] uniform in [-1, 1]
y[n] = y[n-1] + 0.05 * (w[n] - y[n-1])          // one-pole low-pass: flat below fc = -ln(0.95)·fs/2π ≈ 392 Hz, -6 dB/oct
                                                 // above it, i.e. brown-ish (1/f²) warm noise, not pink (-3 dB/oct)
                                                 // (arithmetic). The spectrum is not critical, the level is ([U] §4.5)
drop the first 4800 samples (warm-up), subtract the mean
A    = 10^(carrierLevelDbfs / 20)                // RMS re float full scale 1.0 ([U] §4.4)
c[n] = y[n] * A / rms(y)                         // exact target RMS: -75 dBFS -> 1.778e-4 RMS ≈ 5.83 LSB at 16 bit
play c[] in a loop on its own AudioTrack (same attributes, performance mode and preferred device as the cue track)
```

- It runs from session start (FGS start) to session end, including during recording. It never requests audio focus
  (a GAIN holder is faded out and stays muted until it requests focus again, [U] §3.1).
- No cue may start until the carrier has played for `carrierLeadS`.
- Both tracks must be on the same output thread: check with `adb shell dumpsys media.audio_flinger` (§15).
- Level math: on routes without absolute volume the stream attenuation is digital, so a −75 dBFS carrier at a −30 dB
  media index reaches about −105 dBFS, below one 16-bit LSB (−90.31 dBFS) (INFERENCE, [U] §4.3).

**Manual earbud test (required before relying on the carrier)**, the procedure of [U] §4.6 in short:

1. Record the earbud model and firmware, Bluetooth codec (Developer options), AVRCP absolute volume on/off, media index
   and maximum, phone build. Run `adb shell getprop ro.audio.flinger_standbytime_ms` (empty = the 3 s AOSP default).
2. Test screen (debug build): play the carrier at level L; after a gap G, play a hard-onset test cue (a 1 kHz burst
   with a 5 ms ramp, or the word "Papa" twice) at the calibrated cue gain **with the fade-in off**.
3. Grid: L ∈ {OFF, ZERO, −90, −85, −80, −75, −70, −65, −60}; G ∈ {5, 30, 120, 600 s}; 5 trials per cell in random
   order; screen off; phone in the nap position; earbuds worn.
4. During each gap, record `Standby:` and `Standby delay ns=` for the Bluetooth output thread from
   `adb shell dumpsys media.audio_flinger`, and check that both tracks are on that thread.
5. Score each trial: (a) pop or click, (b) clipped first syllable, (c) carrier audible in a quiet room (2 listeners).
6. Pass rule: that earbud's default is the **lowest** L with 0/5 clipped onsets and 0/5 pops at G = 600 s that neither
   listener hears. If no level passes, use a longer fade-in (3–4 s) and record "clips first syllable". If `ZERO`
   passes, that earbud needs no non-zero carrier.
7. Repeat at the low nap-time media index and at the calibrated maximum; log the results in the CSV format of §12.

### 7.6 Routes

| `AudioRouteMode` | Phase | Accepted `AudioDeviceInfo` types | Notes |
|---|---|---|---|
| `NIGHTSTAND_SPEAKER` (default) | 1 | `TYPE_BUILTIN_SPEAKER` (2) | CLAUDE.md calls it "Default MIT Mode"; the sources only say "the computer" / "computer speakers" ([P] §4.1). The UI says "Nightstand speaker" |
| `BLUETOOTH_SLEEP_AUDIO` | 3 | `TYPE_BLUETOOTH_A2DP` (8), `TYPE_BLE_HEADSET` (26), `TYPE_BLE_SPEAKER` (27) | Never `TYPE_BLUETOOTH_SCO` (7) or `TYPE_BLE_BROADCAST` (30) ([U] §1.1) |
| `USB_WIRED_DAC` | Deferred | (`TYPE_USB_HEADSET`, `TYPE_USB_DEVICE`, `TYPE_WIRED_HEADPHONES`, `TYPE_WIRED_HEADSET`) | Disabled in UI |
| `WATCH_WRIST_SPEAKER` | Deferred | — | Disabled in UI |

`setPreferredDevice` is only a hint (FACT: `Engine.cpp:824-825`, `AudioPolicyManager.cpp:7986-7987`, [U] §2.4). So:
set it on both tracks before `play()`; shortly after `play()` check `getRoutedDevice()?.id == chosen.id`, else stop,
log `route_mismatch` and skip the cue; keep an `OnRoutingChangedListener` on the cue track.

### 7.7 Audio focus, DND, Bluetooth disconnect, hard volume cap

**Audio focus** ([U] §3.1). Apps targeting API 35+ can request focus only as the top app or while running an FGS
(FACT, [A] §2.3); the session FGS covers that.

| Track | Request | On `AUDIOFOCUS_REQUEST_FAILED` | On a loss during playback |
|---|---|---|---|
| Carrier | never | — | — |
| `INITIAL_INCUBATION`, `RE_INCUBATION`, `SLEEP_STATE_QUESTION`, `SESSION_END` | `AUDIOFOCUS_GAIN_TRANSIENT` (setting: `MAY_DUCK` for users who run a white-noise app) | skip the cue, log `focus_denied`, continue | `pause()` + `flush()`, log |
| `WAKE_PROMPT` | `AUDIOFOCUS_GAIN_TRANSIENT` | cycle aborted: `CycleStage(CYCLE_COMPLETE, ABORTED_AUDIO)` (row 15) | stop; cycle aborted |
| Recording | `AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE` for the whole of `RECORDING_DREAM` | record anyway, log | keep recording, log |

Focus is abandoned with the same `AudioFocusRequest` instance after each cue. A loss is `AUDIOFOCUS_LOSS` or
`AUDIOFOCUS_LOSS_TRANSIENT`; `AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK` (e.g. a notification sound) is ignored, because the
system never ducks a speech track and the cue plays on.

**Do Not Disturb** (FACT, AOSP `ZenModeHelper.java:2281-2321`, [U] §3.2): Total silence mutes `USAGE_MEDIA`; Priority
mode mutes media unless media is allowed (AOSP default allows it; the One UI default is unknown); Alarms-only does not
mute media. A muted track plays silence with no error.

| Check (session start and before each cue) | Action |
|---|---|
| `getCurrentInterruptionFilter() == INTERRUPTION_FILTER_NONE` | Block session start: "Do Not Disturb (Total silence) will mute every cue. Turn it off or allow media." Mid-session: log `dnd_muted`; a muted `WAKE_PROMPT` aborts the cycle (row 15) |
| `PRIORITY` and `(getConsolidatedNotificationPolicy().priorityCategories & PRIORITY_CATEGORY_MEDIA) == 0` (64) | Warn at start: "Do Not Disturb may mute cues. Allow 'Media sound' in the Do Not Disturb exceptions." Same mid-session rule |
| always | Log the filter and categories to `p_audio.csv` |

Cues stay on `USAGE_MEDIA`, never `USAGE_ALARM` (an alarm may play on headphones and the speaker together, [U] §0 D2).

**Bluetooth (or any route) disconnect mid-nap** ([U] §3.3). Triggers, whichever comes first:
`ACTION_AUDIO_BECOMING_NOISY`, `onAudioDevicesRemoved` containing the chosen device, or the routed device leaving the
route's allowed set. Action: `cue.pause(); cue.flush(); carrier.pause(); carrier.flush()`, abandon focus, log
`route_lost` with the device type. Policy `routeLossPolicy`:
- `PAUSE_CUES` (default): keep the session and sensing, play nothing, post a silent notification "Earbuds disconnected,
  cues paused". A cycle in progress is aborted (row 15). When an allowed device is re-added, re-verify the route and
  resume at the next cycle, never mid-cue.
- `SPEAKER_FALLBACK`: switch to `NIGHTSTAND_SPEAKER` with the **speaker** cap, never the earbud gain.

The default routing must never play a cue: AOSP falls back to default routing (usually the speaker) when the preferred
device disappears (`AudioPolicyManager.cpp:1318-1322`, [U] §2.4).

**Hard maximum volume cap** ([U] §3.4):
1. Cues are pre-normalised to −3 dBFS peak (§7.3).
2. `HARD_MAX_GAIN` = 1.0: no sample gain above unity, ever (`getMaxVolume()` may be larger; it is not used).
3. `routeCapGain[route]` is set once per route in a calibration screen ("this is the loudest a cue will ever be").
   Defaults: speaker 0.6, Bluetooth 0.3 (GUESSED). `userGain` is **clamped** to it, never rejected: the UI slider stops
   at the cap, playback uses `G_target = min(userGain, routeCapGain[route], 1.0)` (§7.4), and `validate()` only warns
   (`USER_GAIN_CLAMPED`) when `userGain` exceeds the selected route's cap. This is the one rule used everywhere (§8.14).
4. OpenTDI never calls `setStreamVolume` or `adjustStreamVolume` upward.
5. Before each cue, read `getStreamVolume(STREAM_MUSIC)`. If it is above the index saved at calibration: on the
   speaker, scale the linear gain `G_target ×= 10^(−(dB_now − dB_cal)/20)` with `dB = getStreamVolumeDb(STREAM_MUSIC,
   index, deviceType)` at the current and the calibrated index; on Bluetooth (absolute
   volume may hide the real level) skip the cue and log `volume_raised` (a skipped `WAKE_PROMPT` aborts the cycle).
6. The setup docs mention One UI's Media volume limit (Settings > Sounds and vibration > Volume > ⋮ > Media volume limit).

### 7.8 Recording the dream report (Phase 2)

| Rule | Value | Provenance |
|---|---|---|
| Device | phone built-in mic: `setPreferredDevice(TYPE_BUILTIN_MIC)` (15); after `startRecording()` check `getRoutedDevice().type == TYPE_BUILTIN_MIC`, else log it | D12; [U] §5.2 |
| Source | `UNPROCESSED` if `AudioManager.getProperty(PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED) == "true"`, else `MIC` | D12. Research prefers `VOICE_RECOGNITION` to `MIC` (no AGC or noise reduction by CDD §5.4.2, [U] §5.1): open decision O7 |
| Format | 16 kHz, mono, PCM16, WAV (44-byte header, sizes patched every 5 s and on stop) | CDD §5.4.1 guarantees it for every source; it is the recognizer's default ([U] §5.1, §6.2, §8.1) |
| Never | `setMode`, `startBluetoothSco`, `setCommunicationDevice` | [U] §5.3 |
| Guard | if `getMode() != MODE_NORMAL`, or `getCommunicationDevice()?.type` is `TYPE_BLUETOOTH_SCO` or `TYPE_BLE_HEADSET`: skip recording, log `comm_device_busy`, abort the cycle (row 15) | [U] §5.3 |
| Privacy | `AudioRecord.Builder.setPrivacySensitive(true)`; `registerAudioRecordingCallback` before start; `isClientSilenced()` true → log `capture_silenced`, abort the cycle (row 15) | [U] §5.4 |
| Start | when the `WAKE_PROMPT` track has played its last sample (Dormio started on the prompt's `onended`, `script_timer.js:335-336`) | [U] §8.2 |

End-of-report detector (pure function in `:shared`, also run by the replay harness; all values GUESSED, [U] §8.2):

```kotlin
// 20 ms frames (320 samples at 16 kHz). x = 20*log10(rms/32768) dBFS.
class EndOfReportDetector(
    val marginDb: Double = 10.0,        // speech = frame > floor + margin              (range 3..20)
    val hangoverMs: Int = 4000,         // trailing silence after speech that ends it   (1000..10000)
    val noSpeechTimeoutMs: Int = 15000, // nothing said at all                          (5000..60000)
    val maxMs: Int = 45000,             // hard maximum                                 (10000..180000)
    val minSpeechFrames: Int = 5,       // 100 ms above the threshold = speech onset
    val seedMs: Int = 400               // the floor is seeded from the first 400 ms only
)
// per frame: t += 20
//   floor = if (floor is NaN) x else if (x < floor) x else floor + 0.02      // falls fast, rises 1 dB/s
//   while t <= seedMs the frame only updates the floor; it never counts as speech
//   run   = if (x > floor + marginDb) run + 1 else 0
//   if (run >= minSpeechFrames) { spoke = true; lastSpeech = t }
// stop when: t >= maxMs                          -> MAX
//            !spoke && t >= noSpeechTimeoutMs     -> NO_SPEECH
//            spoke && t - lastSpeech >= hangoverMs -> SILENCE
```

No fixed dBFS threshold: the CDD levels (−22.35 dBFS at 90 dB SPL for `VOICE_RECOGNITION`, −36 dBFS at 94 dB SPL for
`UNPROCESSED`) put soft speech close to room noise (INFERENCE, [U] §8.2).

Sleep-state answer **[EXT]**: after the report stops, play `SLEEP_STATE_QUESTION`, then record with the same detector
and `maxMs` 8000, `hangoverMs` 1500, `noSpeechTimeoutMs` 6000 (GUESSED). Parse its transcript (lower case): contains
"half" → `HALFWAY`; else contains "not asleep" or "awake" → `AWAKE`; else contains "asleep" → `ASLEEP`; else
`UNKNOWN`. Without a transcript the answer is `UNKNOWN` and the WAV is kept.

### 7.9 On-device speech-to-text (Phase 2)

Runs after the `RE_INCUBATION` cue has finished, in the session service, from the saved PCM ([U] §6.3):

1. `SpeechRecognizer.isOnDeviceRecognitionAvailable(ctx)` false → transcript status `UNAVAILABLE_NO_ON_DEVICE_SERVICE`.
2. `createOnDeviceSpeechRecognizer(ctx)` on the main thread; `checkRecognitionSupport(intent, …)`. The language
   (`sttLanguage`, default `en-US`) must be in `getInstalledOnDeviceLanguages()`; if it is only supported or pending,
   status `PENDING_MODEL` (the model download is offered later in the UI, never during the nap).
3. Intent: `EXTRA_LANGUAGE_MODEL = LANGUAGE_MODEL_FREE_FORM`, `EXTRA_LANGUAGE`, `EXTRA_AUDIO_SOURCE` = read end of
   `ParcelFileDescriptor.createPipe()`, `EXTRA_AUDIO_SOURCE_ENCODING = ENCODING_PCM_16BIT`,
   `EXTRA_AUDIO_SOURCE_SAMPLING_RATE = 16000`, `EXTRA_AUDIO_SOURCE_CHANNEL_COUNT = 1`,
   `EXTRA_SEGMENTED_SESSION = RecognizerIntent.EXTRA_AUDIO_SOURCE`, `EXTRA_PREFER_OFFLINE = true`.
4. A worker writes the raw PCM samples (no WAV header) to the write end, then closes it. If the result is empty, retry
   once paced at real time (20 ms chunks every 20 ms) (INFERENCE, [U] §6.2).
5. Concatenate `onSegmentResults`; finish on `onEndOfSegmentedSession` (or `onResults`); call `destroy()`.
6. On any error keep the WAV, store status `ERROR_<code>`, allow a manual retry from the journal. If a new cycle
   starts while STT runs, cancel it and mark `RETRY_PENDING`.

`createSpeechRecognizer()` (the default recognizer, "likely to stream audio to remote servers") is **never** called.
"No network" rests on the API contract and the OEM implementation; the framework does not enforce it ([U] §6.3).
Samsung's on-device recognizer support for `EXTRA_AUDIO_SOURCE` is unverified (§15).

---

## 8. Detection math

All code lives in `:shared` (pure Kotlin, no Android types, no wall clock): `AccelProcessor`, `PacketAggregator`,
`HypnagogiaDetectionEngine`. The watch feeds them; unit tests and the replay harness feed them the same way.

### 8.1 Watch-side 25 Hz accelerometer processing (`AccelProcessor`)

| Symbol | Meaning | Default | Provenance |
|---|---|---|---|
| c | Samsung counts → g | `4.0 / 16383.75` = 1/4095.9375 g per count | SOURCED: m/s² = `9.81 / (16383.75 / 4.0) * value` ([S] §2.3), divided by 9.81 |
| — | Android fallback m/s² → g | divide by 9.80665 | definition of standard gravity |
| α_g | gravity low-pass, per sample | 0.04 (τ = 40 ms / 0.04 = 1.0 s at 25 Hz) | GUESSED ([D] §10.6) |
| f | dynamic noise floor | 0.003 g RMS | GUESSED; the measured p95 is logged in `BaselineStats` for tuning |
| M | "clearly moving" dynamic RMS | 0.05 g | GUESSED ([D] §10.6) |
| θ_move | below this stillness a second counts as "moving" | 0.5 | GUESSED |
| J_G | jerk burst threshold on d | 0.30 g | GUESSED ([D] §10.7) |
| burst max | longest jerk burst | 500 ms | GUESSED (CLAUDE.md "< 0.5 s") |
| burst end | samples below J_G that close a burst | 3 (120 ms) | GUESSED |
| pre-still / post-still | seconds with stillness ≥ 0.8 before / after a burst | 10 s / 3 s | GUESSED ([D] §10.7) |
| drift | limp tilt threshold | 3.0° | GUESSED ([D] §10.6) |
| ref | limp reference: still seconds / maximum age | 10 s / 60 s | GUESSED |

Per sample i (timestamps from `DataPoint.getTimestamp()`, ms):

```
a_i   = c * (x_i, y_i, z_i)                       // g
d_i   = |a_i - g_lp|                               // dynamic magnitude in g, against g_lp from before this sample
                                                   // (works with or without gravity in the data)
if not frozen: g_lp = g_lp + α_g * (a_i - g_lp)   // g_lp starts at the first sample
```

**Gravity freeze during a jerk burst.** `frozen` is true from the sample that opens a jerk burst (below) up to and
including the 3 samples that close it, and is cleared at once if the burst stays open longer than `jerkMaxBurstMs`
(500 ms; that is voluntary movement, and g_lp must track the new posture). Without the freeze, g_lp (τ = 1 s) is
pulled towards a one-sided burst and d stays large for 1–3 s after it. A reference model of these formulas (α_g = 0.04,
f = 0.003, M = 0.05, 25 Hz, 10 s still at (0, 0, 1 g), then 8 samples of +0.5 g on x) gives per-second stillness
[0, 0.08, 0.71, 0.94] after an aligned burst and [0, 0, 0.23, 0.76] after one straddling a second boundary (3 seconds
below θ_move); with the freeze, [0, 1, 1, 1] and [0, 0, 1, 1]; a zero-mean ±0.5 g burst gives [0, 1, 1, 1] either way
(INFERENCE, simulated 2026-10-27).

Per second s (samples with timestamps in [s, s + 1000)). With fewer than 20 samples (80 % of 25) all accel fields of
that second are null.

```
accelMean      = mean_i |a_i|                                       // g; ≈ 1 with gravity, ≈ 0 without
accelRms       = sqrt(mean_i d_i²)                                  // g
accelPeak      = max_i d_i                                          // g
stillnessScore = clamp(1 - (accelRms - f) / (M - f), 0, 1)          // 1 = dead still; 0.5 ⇔ accelRms ≈ 0.0265 g
u_s            = normalize(mean_i a_i)                              // orientation unit vector (gravity mode only)
```

**Gravity mode.** Samsung's javadoc says "Accelerometer data doesn't include gravity" (FACT, [S] §0 item 6); CLAUDE.md's
magnitude formula assumes the opposite. **Measured on the SM-L500 (2026-10-27, [DA]): the stream includes gravity**
(`accelMean` 0.995–1.004 g in every second). The processor still decides from the data: once 10 calm seconds
(stillness ≥ θ_move = 0.5) exist, median `accelMean` ∈ [0.8, 1.2] g → `WITH_GRAVITY`; < 0.3 g → `LINEAR_ONLY`;
otherwise `AMBIGUOUS` (GUESSED; it was 30 seconds at ≥ 0.8 until the first device run, where a worn watch reached only
17 such seconds in 134 s and the mode stayed `UNKNOWN`). Stillness and the jerk detector work in every mode (they use d). The limp detector runs only in
`WITH_GRAVITY`; in the other modes `limpEvent` is always false and `LIMP_UNAVAILABLE` is logged once. The mode is
written to `w_config.csv`.

**Hypnic-jerk detector** (raw 25 Hz stream; bonus only):

```
preStill  = each of the 10 complete seconds before the burst's first second had stillness >= 0.8
open      : d_i > J_G and preStill and no burst open
close     : at the first run of 3 consecutive samples with d < J_G;  L = t(last sample above J_G) - t(open) + 40 ms
if L > 500 ms -> not a jerk (voluntary movement); log JERK_REJECTED_LONG
else the 3 complete seconds after the burst's last second must each have stillness >= 0.8
     -> jerkEvent = true in the packet of the 3rd of those seconds; log JERK_CONFIRMED (L, peak d)
     otherwise log JERK_REJECTED_POST_MOVE
```

A burst ≤ 500 ms touches at most 2 seconds. Because g_lp is frozen during it, the seconds after it return to the
pre-burst stillness, so it makes at most 2 "moving" seconds. The stillness gate tolerates 2 (§8.6) and major movement
needs 3 (§8.10), so a jerk never breaks the gate and never aborts a countdown. This holds for a burst that starts and
ends cleanly above J_G; a burst with a slow ramp below J_G still moves g_lp a little (INFERENCE), which the raw-`a` tests
in §16 and the recorded naps of §15 #10 check.

**Limp-wrist detector** (`WITH_GRAVITY` only; passive posture drift, [D] §10.6 option 2; bonus only):

```
reference u_ref = normalize(mean of u_s over 10 consecutive seconds, each with stillness >= 0.8)
  acquired when none exists; cleared after any second with stillness < 0.5 and after a limpEvent;
  re-acquired from the latest 10 still seconds when its age exceeds 60 s
θ_s = acos(clamp(u_s · u_ref, -1, 1)) in degrees
limpEvent at s  if  θ_s >= 3.0  and every second since u_ref was set had stillness >= 0.8  and age(u_ref) <= 60 s
                (u_ref is then cleared)
```

`tiltDeg` = θ_s is logged in `w_packets.csv` only (not sent over the wire).

### 8.2 1 Hz packet aggregation (`PacketAggregator`, watch)

| Field | Rule | Provenance |
|---|---|---|
| `timestamp` | start of the 1 s bin, epoch ms on the watch clock (`DataPoint.getTimestamp()` floored to 1000 ms). The timestamp base is UNVERIFIED, so the arrival wall time is logged too | [S] §2.3 |
| `hr` (Float, bpm) | last `HeartRateSet` point in the bin with `HEART_RATE_STATUS == 1` and 30 ≤ HR ≤ 220; else **hold** the last valid value if it is ≤ 5 s old; else null | status codes FACT ([S] §2.3); bounds and hold GUESSED |
| `hrStatus` | raw status of the last HR point in the bin, else null | [S] §2.3 |
| `ibiList` (ms) | IBIs accepted in this bin (below) | |
| `rmssd` (Float, ms) | rolling RMSSD over accepted IBIs timed in the last 60 s; null with fewer than 20 successive-difference pairs | GUESSED |
| `skinConductance` (Float, µS) | last `EdaSet` point in the bin with `STATUS == 0` and value ≥ 0.05 µS; no hold; else null | status FACT ([S] §2.3); the 0.05 floor GUESSED |
| `edaStatus` | raw status of the last EDA point, else null | [S] §2.3 |
| accel fields, `stillnessScore`, `jerkEvent`, `limpEvent` | from `AccelProcessor` (§8.1) | |
| `source` | `SAMSUNG_SDK`, `ANDROID_SENSORS` or `SIMULATION` | D6 |
| `skinTemperature` (Float, °C) **[EXT]** | newest `SkinTemperatureSet.OBJECT_TEMPERATURE` with `STATUS == 0` sampled in or before the bin, held ≤ 120 s; a point that arrives after its bin went out still feeds later bins. Skin temperature is not an active tracker, so no bin waits for it | status FACT (`ValueKey.SkinTemperatureSet` javadoc); the hold GUESSED |
| `spo2` (Int, %) **[EXT]** | the last completed SpO2 spot check (`STATUS == 2`), held `spo2IntervalMin × 60 + spo2TimeoutS` s, i.e. until the next check could have completed | status FACT (`ValueKey.SpO2Set` javadoc); the hold GUESSED |

**IBI handling.**
- An IBI is valid if `IBI_STATUS_LIST[j] == 0 && IBI_LIST[j] != 0` (FACT, Samsung sample `IBIDataParsing.kt:25-27`,
  [S] §2.3), and 300 ≤ IBI ≤ 2000 ms, and (once 3 IBIs have been accepted) |IBI − median(last 7 accepted)| ≤ 0.20 × that
  median. The bounds and the 20 % rule are GUESSED.
- `IBI_LIST` may be null. In a screen-off batch, all IBIs are in the **first** data point and the rest are NULL (FACT,
  [S] §0 item 9). Timing (INFERENCE, VERIFY): the IBIs of a data point are timed backwards from the timestamp of the
  last point of the same callback batch, `t_j = t_end − Σ_{k>j} IBI_k`.
- RMSSD = sqrt(mean((IBI_k − IBI_{k−1})²)) over pairs of **consecutive accepted** beats (no rejected beat in between)
  whose later beat is timed in the last 60 s.

**Emission.** Bins are emitted in timestamp order, one per second. Bin s is emitted when every active tracker has
delivered a data point with timestamp ≥ s + 1000, or when the wall clock passes the arrival deadline (2 × flush
interval = 10 s after the bin ends); missing channels are then null. Late data for an emitted bin goes to
`w_sensor_raw.csv` only. The engine therefore receives one packet per second even when sensors fail, and its packet
clock keeps running.

### 8.3 Engine inputs and windows

`onPacket(p)` is the engine's only clock: `t = p.timestamp`. `Win(t, L)` = the packets with
`t − L·1000 < timestamp ≤ t` (at most L packets). A gap of more than 1500 ms between packets resets `hold` to 0.

### 8.4 Baseline (configurable window W, default 180 s)

Collected in `CALIBRATING_BASELINE` from packets with `calStart ≤ timestamp < calStart + W·1000` (2W after an
extension). The user is asked to lie down relaxed before pressing Start (owner 12; Dormio: "when the subject initially
lies down", P20 §2.2).

| Step | Rule | Provenance |
|---|---|---|
| Motion-artifact frame | `stillnessScore == null` or `< θ_move` (0.5) → the frame is discarded for **all** channels | GUESSED (CLAUDE.md "after discarding motion artifact frames") |
| Motion validity | motion-free frames ≥ 0.5 · W, else invalid (`BASELINE_INSUFFICIENT_MOTION`) | GUESSED |
| HR | over motion-free frames with `hr != null`: need n ≥ 0.5 · W, else invalid (`BASELINE_INSUFFICIENT_HR`). μ_HR = mean, σ_HR = sample SD floored at 1.0 bpm | mean SOURCED (Dormio used arithmetic means, [C] §5.2); σ and floor GUESSED |
| RMSSD | over motion-free frames with `rmssd != null`: n ≥ 30 → μ_R, σ_R; else `rmssdMean = null` and the RMSSD signal is off for the session (not a baseline failure) | GUESSED |
| EDA | over motion-free frames with EDA non-null: n ≥ 0.5 · W → μ_E, σ_E (σ_E floored at 0.005 µS); else the EDA bonus is off | GUESSED |
| Noise floor log | p95 of `accelRms` over motion-free frames → `BaselineStats.accelNoiseFloorP95G` (logged for tuning, not fed back) | GUESSED |
| Too few frames | first time: extend the window to 2W (keeping the packets); second time → `IDLE` + `DetectionUnavailable` (rows 4–5) | GUESSED |

### 8.5 Optional prior-day resting HR baseline (owner 12)

| Field | Default | Range | Meaning |
|---|---|---|---|
| `priorHrSource` | `NONE` | `NONE`, `USER_ENTERED`, `PREVIOUS_SESSION` | `PREVIOUS_SESSION` = live μ_HR of the last completed DETECTION session, read from that session's `w_config.csv` on the watch |
| `priorRestingHrBpm` | null | 35–120 | The user-entered value, or the value filled in from `PREVIOUS_SESSION` at session start |
| `priorHrWeight` w | 0.3 | 0.0–0.5 | GUESSED |

```
if prior == null                                   :  μ_HR,eff = μ_HR,live
else if prior >= μ_HR,live                         :  μ_HR,eff = μ_HR,live   (log PRIOR_IGNORED_HIGHER)
else if μ_HR,live − prior > 0.15 · μ_HR,live       :  μ_HR,eff = μ_HR,live   (log PRIOR_IGNORED)
else                                               :  μ_HR,eff = (1 − w) · μ_HR,live + w · prior
```

So μ_HR,eff ≤ μ_HR,live always: the prior can only make a trigger harder, never easier.

Rationale (INFERENCE): if the user is still keyed up when the baseline is taken, μ_live is high and relaxation alone
would look like a 7 % drop; blending in a lower habitual resting HR asks for a real drop below it. A prior **above**
μ_live is ignored, because blending it in would raise μ_eff and let the user's own resting HR count as a "drop"
(example, arithmetic: prior = 1.15·μ_live, w = 0.5 → μ_eff = 1.075·μ_live and the `MIT_STANDARD` level 0.93·μ_eff =
0.99975·μ_live). The 15 % guard stops a stale or mistyped value from dominating. MIT gives no basis for this feature
([P] §8 item 10): GUESSED.

### 8.6 Per-frame signals (`AWAKE_INCUBATING`)

Windows come from `DetectionConfig` (§9.5); the defaults are in brackets. Each minimum count is a fixed fraction of
its window (structural constants, §8.15), so every allowed window length can be satisfied:
`nHrMin = ceil(2/3 · hrMaWindowS)` [15 → 10], `nStillMin = ceil(5/6 · stillWindowS)` [30 → 25],
`nEdaMin = ceil(2/3 · edaSlopeWindowS)` [30 → 20].

```
HR_ma   = mean(hr over Win(t, hrMaWindowS) where hr != null)   if at least nHrMin values, else null
d_HR    = (μ_HR,eff − HR_ma) / μ_HR,eff                                // fraction; positive = drop
H_sig   = HR_ma != null  and  d_HR >= θ_drop                           // i.e. HR_ma <= μ_HR,eff · (1 − θ_drop)

r       = (p.rmssd − μ_R) / μ_R                                        // null if either is null
R_sig   = r != null  and  r >= θ_rmssd

A       = H_sig or R_sig                                               // REQUIRED autonomic signal

S_win   = non-null stillnessScore over Win(t, stillWindowS); with fewer than nStillMin values G = false
m_still = mean(S_win);   n_move = count(S_win < θ_move)
G       = m_still >= θ_still  and  n_move <= 2                         // REQUIRED stillness gate

// bonuses (never sufficient on their own)
E_win   = non-null skinConductance over Win(t, edaSlopeWindowS); with fewer than nEdaMin values E = false
β       = Σ (x−x̄)(y−ȳ) / Σ (x−x̄)²   with x = (timestamp − t)/1000 s and y in µS      // OLS slope, µS/s
spikes  = any |y_k − y_{k−1}| > max(1.5 · σ_E, 0.02 µS) over pairs exactly 1 s apart
E       = β <= 0  and  not spikes                                      // tonic decline, no phasic response
J       = any jerkEvent in Win(t, bonusLookbackS)
L       = any limpEvent in Win(t, bonusLookbackS)
```

The EDA rule (30 s slope ≤ 0, no spike above 1.5σ) is CLAUDE.md's, demoted to a bonus per owner 11; its numbers and
the 0.02 µS floor are GUESSED. EDA is always relative to the session baseline, never an absolute µS delta ([D] §10.5).

### 8.7 Onset rule (D7)

```
core     = G and A
hold     = if (t < refractoryUntil) 0 else if (core) hold + 1 else 0
holdReq  = if (E or J or L) holdShortS else holdS
ONSET   <=> state == AWAKE_INCUBATING and hold >= holdReq
```

`hold` counts consecutive 1 Hz packets including the current one, so a core that first becomes true at t₀ triggers at
t₀ + (holdReq − 1) s. EDA, jerk and limp can only shorten the hold (20 s → 10 s at `MIT_STANDARD`) and raise the
confidence; with `A` or `G` false nothing triggers, whatever the bonuses.

### 8.8 HR drop against the moving average: worked numbers

With μ_HR,eff = 70.0 bpm and `MIT_STANDARD` (θ_drop = 0.07) the trigger level is HR_ma ≤ 65.1 bpm. For comparison,
Dormio's absolute 5 BPM is 7.1 % of 70 bpm and 8.3 % of 60 bpm, so "7 %" sits inside that band by arithmetic, not by
any source ([P] §4.1; [D] §10.4).

### 8.9 Detection confidence score (0..1, every frame; D5, owner 14)

```
S  = (n_move <= 2) ? clamp((m_still − θ_move) / (θ_still − θ_move), 0, 1) : 0
H  = d_HR != null ? clamp(d_HR / θ_drop, 0, 1) : 0
R  = r    != null ? clamp(r / θ_rmssd, 0, 1)    : 0
Aₛ = max(H, R)
P  = (t < refractoryUntil) ? 0 : min(hold / holdReq, 1)
B  = min(1, 0.5 · (E + J + L))                       // booleans as 0/1
confidence = clamp(0.30·S + 0.40·Aₛ + 0.15·P + 0.15·B, 0, 1)
```

The weights are GUESSED. At a trigger frame S = Aₛ = P = 1, so **every trigger carries confidence ≥ 0.85** (0.85 with
no bonus, 0.925 with one, 1.00 with two or more). A high score never triggers by itself: the rule of §8.7 decides. The
score is null in `CALIBRATING_BASELINE`; in `HYPNAGOGIA_DETECTED`, `WAKING_USER`, `RECORDING_DREAM`, `RE_INCUBATING` and
`PAUSED` it is still computed for display, with `detectionActive = false` and P = 0.

Display (Phase 2 as a number, Phase 3 as a chart): confidence 0.00–1.00 with a marker at 0.85; chips for Stillness
(G), HR drop, RMSSD, EDA, Jerk and Limp; and "hold 7 / 10 s". The trigger carries the score and its components (§9).

### 8.10 Countdown motion abort

```
majorMovement(t) = count(stillnessScore < θ_move over Win(t,5)) >= 3
```

Evaluated only in `HYPNAGOGIA_DETECTED` (row 7). A jerk (≤ 2 moving seconds) never aborts. After an abort `hold` = 0
and no refractory applies; the 30 s gate has to refill anyway. GUESSED (CLAUDE.md; NOT FOUND in sources).

### 8.11 Sensitivity presets (`SensitivityPreset`; all GUESSED)

`LOW` = fewer, later triggers; `HIGH` = more, earlier triggers. The enum value stays `MIT_STANDARD` (D10), but the UI
label is "Standard", because nothing here is an MIT value (owner 10; [W] §1.7); see O2.

| Parameter | `LOW` | `MIT_STANDARD` | `HIGH` | User range |
|---|---|---|---|---|
| θ_drop (HR drop fraction) | 0.10 | **0.07** | 0.05 | 0.04–0.12 (CLAUDE.md range). An explicit `hrDropFraction` override beats the preset |
| θ_rmssd (RMSSD rise fraction) | 0.25 | 0.15 | 0.10 | 0.05–0.50 |
| θ_still (mean stillness over the gate window) | 0.85 | 0.80 | 0.75 | 0.60–0.95 (`stillThreshold` override) |
| `holdS` | 30 | 20 | 15 | 5–30, ≤ `stillWindowS` (`holdS` override) |
| `holdShortS` (with a bonus) | 15 | 10 | 8 | 3–`holdS` (`holdShortS` override) |
| moving seconds allowed in the gate window | 2 | 2 | 2 | fixed, structural (a ≤ 500 ms jerk spans ≤ 2 s; §8.15) |
| HR moving-average window | 15 s | 15 s | 15 s | 5–60 (`hrMaWindowS`) |

Every override in the "User range" column is a nullable `DetectionConfig` field (§9.5); null = the preset value.

### 8.12 Missing channels

| Situation | Effect |
|---|---|
| `ANDROID_SENSORS` fallback (no IBI, no EDA) | `rmssd` and `skinConductance` are null: R_sig false, E false. The rule runs on G + HR drop; jerk and limp still come from the accelerometer. Without jerk/limp the maximum confidence at a trigger is 0.85 |
| EDA unsupported, or the EDA baseline invalid | E false all session |
| RMSSD baseline null | R_sig false all session |
| HR null for a while | `HR_ma` needs `nHrMin` values (10 of 15 by default), else H_sig is false. After `hrLostS` (300) s with `HR_ma` null → `IDLE` (`HR_LOST`) |
| Accelerometer missing | G false (fewer than `nStillMin` values); after `accelLostS` (60) s with no non-null `stillnessScore` → `IDLE` (`ACCEL_LOST`). DETECTION needs the gate, so there is no accel-free DETECTION |
| No usable sensor at session start | `StartSession` in DETECTION is refused with `NO_SENSORS`; the UI offers TIMED_CUES |

### 8.13 Re-arm, refractory, max cycles

- After `CYCLE_COMPLETE` (completed or aborted) or a missed or rejected trigger: `refractoryUntil = t_last +
  refractoryS · 1000`, where `t_last` is the timestamp of the last packet processed. During refractory `hold` stays 0.
- After an `AbortCountdown` (movement) there is no refractory.
- `completedCycles` counts `CYCLE_COMPLETE` with outcome `COMPLETED` and stage timeouts (row 16). At `maxCycles`
  (default 4, range 1–10) → `SESSION_COMPLETE` (`MAX_CYCLES`). The default 4 is SOURCED from FS24's 4 rounds; the web
  code defaults to 3, the bc variant allows up to 10 ([C] §9). The lab protocol was time-boxed at 45 min rather than
  count-limited ([P] §1.2); `sessionMaxMin` covers that.

### 8.14 Consistency checks (asserted in unit tests)

- Every preset's and override's `holdS` ≤ `stillWindowS`, and `holdShortS` ≤ `holdS`.
- Every preset's θ_drop lies in the 0.04–0.12 user range.
- Every minimum count ≤ its window, for every allowed window: `nHrMin ≤ hrMaWindowS`, `nStillMin ≤ stillWindowS`,
  `nEdaMin ≤ edaSlopeWindowS` (true by construction of §8.6; asserted over each range in `ConfigValidationTest`).
- `cycleStageTimeoutS · 1000 ≥ recording.maxMs + recording.answerMaxMs + 60 000` (60 s for the prompt, the
  re-incubation cue and margin); `validate()` rejects configs that break it.
- The 15 s HR moving average lags the raw HR by about 7 s, so a trigger comes about 7 + (holdReq − 1) s after the
  noise-free HR crosses the line (raw crossing at k ≈ 329.4, onset at k = 346 in §13.4).
- Jerk burst ≤ 500 ms (with the g_lp freeze of §8.1) ⇒ ≤ 2 moving seconds ⇒ ignored by both the gate (≤ 2 allowed)
  and the major-movement rule (≥ 3). Asserted from raw `a` samples in `AccelProcessorTest`, not from d.
- `missedCycleGraceS` < `cycleStageTimeoutS`.
- `sessionMaxMin · 60 ≥ 2W + countdownS` (a session can always finish calibration plus one countdown); `validate()`
  rejects configs that break it.
- `firstSleepMinMin ≤ firstSleepMaxMin`; `0 ≤ routeCapGain[route] ≤ 1.0` (rejected otherwise). `userGain` above the
  route's cap is clamped with a warning, not rejected (§7.7 step 3).

### 8.15 Structural constants (GUESSED, fixed in code, logged as `const.*` in `w_config.csv` / `p_config.csv`)

These are GUESSED but are not config values (label rule, top of document). The tunable thresholds are config values
(§9.5).

| Constant | Value | Where |
|---|---|---|
| Minimum-count fractions | 2/3 (HR_ma), 5/6 (stillness), 2/3 (EDA) | §8.6 |
| Moving seconds allowed in the gate / major movement | ≤ 2 of the gate window / ≥ 3 of the last 5 s | §8.6, §8.10 |
| Packet gap that resets `hold` | > 1500 ms | §8.3 |
| Accel samples for a valid second | ≥ 20 of 25 | §8.1 |
| Gravity-mode rule | 10 calm s (stillness ≥ θ_move); median `accelMean` ∈ [0.8, 1.2] g / < 0.3 g | §8.1 |
| Jerk/limp stillness levels | ≥ 0.8 (still), < 0.5 (clears the limp reference) | §8.1 |
| Baseline validity / extension | motion-free and HR frames ≥ 0.5·W; RMSSD n ≥ 30; one extension to 2W | §8.4 |
| σ floors | σ_HR ≥ 1.0 bpm, σ_E ≥ 0.005 µS | §8.4 |
| Prior-HR guard | prior ≥ 0.85·μ_live and < μ_live | §8.5 |
| HR bounds | 30–220 bpm (the 5 s hold and the 60 s RMSSD window are config: `hrHoldMaxS`, `rmssdWindowS`, §9.5) | §8.2 |
| IBI rules | 300–2000 ms; ≤ 20 % from the median of the last 7 accepted; RMSSD needs ≥ 20 pairs | §8.2 |
| Skin-temperature hold | a packet's `skinTemperature` is the newest valid value sampled ≤ 120 s before its bin | §8.2 |
| EDA floors | value ≥ 0.05 µS; spike floor 0.02 µS; spike factor 1.5σ_E | §8.2, §8.6 |
| Confidence weights | 0.30 S, 0.40 A, 0.15 P, 0.15 B; bonus 0.5 each | §8.9 |
| Command retry | 3× at 2 s if no reply in 5 s | §10.2 |
| Trigger retry | attempts at +0, 2, 4, 8 s, 2 s reply timeout each | §10.2 |
| Cue normalisation | −3 dBFS peak | §7.3 |
| Phrase silence trim | samples below −50 dBFS at a phrase's start and end | §7.3 |
| Speech lead-in | ¾ of `fadeInMs` of silence before a cue's first phrase (speech enters at −15 dB) | §7.3, §7.4 |

---

## 9. Data contracts (`:shared`, package `com.opentdi.shared.model`, kotlinx-serialization)

Wire format: JSON, UTF-8, `Json { classDiscriminator = "type"; ignoreUnknownKeys = true; encodeDefaults = true;
explicitNulls = false }`. Every timestamp is epoch ms. Units are in the field names' comments. `PROTOCOL_VERSION = 1`
**[EXT]**: `StartSession` and `TelemetryBatch` carry it; a mismatch is answered `Ack(false, "PROTOCOL_MISMATCH")`.

### 9.1 Enums

```kotlin
@Serializable enum class TdiState { IDLE, CALIBRATING_BASELINE, AWAKE_INCUBATING, HYPNAGOGIA_DETECTED,
    WAKING_USER, RECORDING_DREAM, RE_INCUBATING, PAUSED, SESSION_COMPLETE }
@Serializable enum class SessionMode { DETECTION, TIMED_CUES }
@Serializable enum class PromptCue { INITIAL_INCUBATION, WAKE_PROMPT, SLEEP_STATE_QUESTION /*[EXT]*/, RE_INCUBATION, SESSION_END }
@Serializable enum class AudioRouteMode { NIGHTSTAND_SPEAKER, BLUETOOTH_SLEEP_AUDIO,
    USB_WIRED_DAC /*deferred, disabled*/, WATCH_WRIST_SPEAKER /*deferred, disabled*/ }
@Serializable enum class SensitivityPreset { LOW, MIT_STANDARD /*UI label "Standard"*/, HIGH }
@Serializable enum class SensorSource { SAMSUNG_SDK, ANDROID_SENSORS, SIMULATION }
@Serializable enum class SimulationMode { FAST_HYPNAGOGIA, REALTIME_HYPNAGOGIA, AWAKE_CONTROL, STOP }
@Serializable enum class CycleStageType { DWELL_START /*[EXT] TIMED_CUES only, §4*/, WAKING_USER, RECORDING_DREAM,
    RE_INCUBATING, CYCLE_COMPLETE }
@Serializable enum class CycleOutcome { COMPLETED, ABORTED_AUDIO }                                   // [EXT]
@Serializable enum class CountdownAbortReason { MAJOR_MOVEMENT }
@Serializable enum class DeviceRole { WATCH, PHONE }
@Serializable enum class SessionEndReason { MAX_CYCLES, TIME_LIMIT, USER, SENSOR_FAILURE, ERROR }
@Serializable enum class StateReason { SESSION_START, BASELINE_READY, BASELINE_EXTENDED,
    BASELINE_INSUFFICIENT_MOTION, BASELINE_INSUFFICIENT_HR, ONSET, TIMED_DWELL, MAJOR_MOVEMENT, MISSED_CYCLE,
    PHONE_REJECTED, PHONE_STAGE, CYCLE_COMPLETED, CYCLE_ABORTED, STAGE_TIMEOUT, HR_LOST, ACCEL_LOST, NO_SENSORS,
    PAUSED, RESUMED, MAX_CYCLES, TIME_LIMIT, USER_ABORT, FALLBACK_TO_TIMED }
@Serializable enum class SensorStatus { CONNECTED_SAMSUNG, FALLBACK_ANDROID_SENSORS, SIMULATION, AAR_NOT_BUNDLED,
    HSS_NOT_INSTALLED, HSS_TOO_OLD, SDK_POLICY_ERROR, PERMISSION_MISSING, TRACKER_UNSUPPORTED, CONNECTION_FAILED,
    NO_SENSORS }
@Serializable enum class GravityMode { UNKNOWN, WITH_GRAVITY, LINEAR_ONLY, AMBIGUOUS }
@Serializable enum class SleepStateAnswer { AWAKE, HALFWAY, ASLEEP, UNKNOWN }                       // [EXT]
@Serializable enum class CarrierMode { OFF, NOISE, ZERO }
@Serializable enum class FocusMode { GAIN_TRANSIENT, GAIN_TRANSIENT_MAY_DUCK }
@Serializable enum class RouteLossPolicy { PAUSE_CUES, SPEAKER_FALLBACK }
@Serializable enum class EndAlarm { CHIME, NONE }
@Serializable enum class PriorHrSource { NONE, USER_ENTERED, PREVIOUS_SESSION }
@Serializable enum class PpgChannel { GREEN, IR, RED }                       // [EXT] Samsung PpgType names
```

### 9.2 `BiosignalPacket` (1 Hz, watch → phone inside `TelemetryFrame`; D6)

```kotlin
@Serializable data class BiosignalPacket(
    val timestamp: Long,            // ms, start of the 1 s bin (watch clock)
    val hr: Float?,                 // bpm; sample-and-hold ≤ 5 s; null = no valid HR      (Float: SensorManager HR is float)
    val ibiList: List<Int>,         // ms, IBIs accepted in this bin (may be empty)
    val rmssd: Float?,              // ms, rolling 60 s; null with < 20 pairs
    val skinConductance: Float?,    // µS, EdaSet.SKIN_CONDUCTANCE with STATUS == 0
    val accelRms: Float?,           // g, RMS of the gravity-removed dynamic magnitude over the second
    val edaStatus: Int?,            // raw EdaSet.STATUS (0, -5, -10)
    val hrStatus: Int?,             // raw HEART_RATE_STATUS (1, 0, -2, -3, -8, -10, -999)
    val accelMean: Float?,          // g, mean |a| over the second (≈1 with gravity in the data, ≈0 without)
    val accelPeak: Float?,          // g, max dynamic magnitude in the second
    val jerkEvent: Boolean,         // a hypnic jerk was confirmed in this second (§8.1)
    val limpEvent: Boolean,         // a limp-wrist drift was confirmed in this second (§8.1)
    val stillnessScore: Float?,     // 0..1; null with < 20 accel samples
    val source: SensorSource,
    // [EXT] additive, logged only (never used for detection):
    val skinTemperature: Float? = null, // °C, SkinTemperatureSet.OBJECT_TEMPERATURE with STATUS 0; held ≤ 120 s (GUESSED)
    val spo2: Int? = null,          // %, last completed SpO2 spot check; held spo2IntervalMin + spo2TimeoutS after it
)
```

No raw accelerometer data goes over the wire (D6, owner 8). A frame is about 250–400 bytes of JSON (INFERENCE).

### 9.3 Engine outputs

```kotlin
@Serializable data class DetectionScore(          // "GateStatus" + score, emitted every frame (D5)
    val confidence: Float,                         // 0..1 (§8.9)
    val detectionActive: Boolean,                  // false outside AWAKE_INCUBATING or during refractory
    val stillMean: Float?, val movingSeconds: Int?, val gate: Boolean,          // G
    val hrMa: Float?, val hrDropFraction: Float?, val hrSignal: Boolean,        // H_sig
    val rmssdRiseFraction: Float?, val rmssdSignal: Boolean,                    // R_sig
    val edaSlopeUsPerS: Float?, val edaBonus: Boolean,                          // E
    val jerkBonus: Boolean, val limpBonus: Boolean,                             // J, L
    val holdS: Int, val holdRequiredS: Int, val refractory: Boolean,
)

@Serializable data class BaselineStats(
    val startMs: Long, val endMs: Long, val windowS: Int,                       // windowS = W or 2W
    val framesTotal: Int, val framesMotionDiscarded: Int,
    val hrMeanLive: Float, val hrMean: Float /*μ_HR,eff*/, val hrSd: Float, val hrN: Int,
    val priorRestingHrBpm: Float?, val priorHrWeightApplied: Float,              // 0 if no prior or ignored
    val rmssdMean: Float?, val rmssdSd: Float?, val rmssdN: Int,
    val edaMean: Float?, val edaSd: Float?, val edaN: Int,
    val accelNoiseFloorP95G: Float?,
)

@Serializable data class HypnagogiaTrigger(       // /opentdi/hypnagogia_trigger, watch → phone
    val sessionId: String,
    val cycle: Int,                                // 1-based number of the cycle this onset starts
    val onsetTimestampMs: Long,                    // packet time of the trigger frame
    val countdownMs: Long,                         // countdownS * 1000 from the active config
    val confidence: Float,
    val evidence: DetectionScore,
    val baseline: BaselineStats,
    val preset: SensitivityPreset,
    val source: SensorSource,
    val sentAtWallMs: Long,                        // watch wall clock (latency log only)
)

@Serializable data class TelemetryFrame(
    val seq: Long,                                 // per-session frame counter (gap detection, [D] §10.2)
    val packet: BiosignalPacket,
    val state: TdiState,
    val stateReason: StateReason?,                 // set on the frame where the state changed
    val completedCycles: Int,
    val score: DetectionScore?,                    // null in IDLE / CALIBRATING_BASELINE
)

@Serializable data class TelemetryBatch(          // /opentdi/sensor_stream, watch → phone
    val protocolVersion: Int = 1,
    val sessionId: String,
    val batchSeq: Long,
    val sensorStatus: SensorStatus,                // [EXT] so the phone can show it
    val gravityMode: GravityMode,
    val frames: List<TelemetryFrame>,              // one flush period: 2 by default
    val sentAtWallMs: Long,
)

@Serializable data class Ack(val ok: Boolean, val code: String = "OK")   // reply to every sendRequest
```

### 9.4 `TdiCommand` (`/opentdi/state_command`; D3)

```kotlin
@Serializable sealed class TdiCommand {
    @Serializable @SerialName("start")  data class StartSession(            // both directions
        val sessionId: String, val config: SessionConfig, val origin: DeviceRole,
        val startedAtWallMs: Long, val protocolVersion: Int = 1) : TdiCommand()
    @Serializable @SerialName("pause")  data class PauseSession(val sessionId: String, val origin: DeviceRole) : TdiCommand()   // both
    @Serializable @SerialName("resume") data class ResumeSession(val sessionId: String, val origin: DeviceRole) : TdiCommand()  // both
    @Serializable @SerialName("abort")  data class AbortSession(             // both
        val sessionId: String, val origin: DeviceRole, val reason: SessionEndReason) : TdiCommand()
    @Serializable @SerialName("config") data class ConfigSync(               // both; sessionId null outside a session
        val sessionId: String?, val config: SessionConfig) : TdiCommand()
    @Serializable @SerialName("abort_countdown") data class AbortCountdown(  // watch → phone
        val sessionId: String, val cycle: Int, val reason: CountdownAbortReason, val atMs: Long) : TdiCommand()
    @Serializable @SerialName("cycle_stage") data class CycleStage(          // phone → watch
        val sessionId: String, val cycle: Int, val stage: CycleStageType,
        val outcome: CycleOutcome = CycleOutcome.COMPLETED,                   // [EXT] only meaningful with CYCLE_COMPLETE
        val atWallMs: Long) : TdiCommand()
}
```

`sessionId` = UTC `yyyyMMdd'T'HHmmss'Z'` + "-" + 4 random hex digits, made by the device that starts the session.

### 9.5 `SessionConfig` (all fields, defaults, ranges)

```kotlin
@Serializable data class SessionConfig(
    val revision: Long = 0,                         // [EXT] wall ms of the last edit; ConfigSync keeps the higher revision
    val mode: SessionMode = SessionMode.DETECTION,
    val theme: String = "a tree",                   // 1..60 chars
    val maxCycles: Int = 4,                         // 1..10            SOURCED (FS24: 4 rounds)
    val sessionMaxMin: Int = 60,                    // 15..180          GUESSED
    val countdownS: Int = 180,                      // 60..300, step 15 SOURCED (§6); also the TIMED dwell
                                                    // (15 allowed only when simulation == FAST_HYPNAGOGIA, §13.1)
    val detection: DetectionConfig = DetectionConfig(),
    val accel: AccelConfig = AccelConfig(),
    val timed: TimedCueConfig = TimedCueConfig(),
    val audio: AudioConfig = AudioConfig(),
    val recording: RecordingConfig = RecordingConfig(),
    val link: LinkConfig = LinkConfig(),
    val sensors: SensorsConfig = SensorsConfig(),   // [EXT] logging-only Samsung trackers
    val simulation: SimulationMode? = null,         // set only by the simulation receiver
)

@Serializable data class DetectionConfig(
    val baselineWindowS: Int = 180,                 // 60..600, step 30
    val sensitivity: SensitivityPreset = SensitivityPreset.MIT_STANDARD,
    val hrDropFraction: Float? = null,              // 0.04..0.12; null = preset (§8.11)
    val rmssdRiseFraction: Float? = null,           // 0.05..0.50; null = preset
    val stillThreshold: Float? = null,              // 0.60..0.95; null = preset (θ_still)
    val holdS: Int? = null,                         // 5..30, <= stillWindowS; null = preset
    val holdShortS: Int? = null,                    // 3..holdS; null = preset
    val hrMaWindowS: Int = 15,                      // 5..60
    val stillWindowS: Int = 30,                     // 10..120, must be >= the resolved holdS
    val edaSlopeWindowS: Int = 30,                  // 15..120
    val bonusLookbackS: Int = 120,                  // 30..300
    val refractoryS: Int = 120,                     // 0..600
    val missedCycleGraceS: Int = 15,                // 5..60
    val cycleStageTimeoutS: Int = 180,              // 90..600
    val hrLostS: Int = 300,                         // 60..900
    val accelLostS: Int = 60,                       // 10..300
    val hrHoldMaxS: Int = 5,                        // 1..15 (watch aggregator, §8.2)
    val rmssdWindowS: Int = 60,                     // 30..300 (watch aggregator, §8.2)
    val priorHrSource: PriorHrSource = PriorHrSource.NONE,
    val priorRestingHrBpm: Float? = null,           // 35..120
    val priorHrWeight: Float = 0.3f,                // 0.0..0.5
)

@Serializable data class AccelConfig(
    val noiseFloorG: Float = 0.003f, val moveRmsG: Float = 0.05f, val moveStillness: Float = 0.5f,
    val gravityAlpha: Float = 0.04f, val jerkThresholdG: Float = 0.30f, val jerkMaxBurstMs: Int = 500,
    val jerkPreStillS: Int = 10, val jerkPostStillS: Int = 3, val limpDriftDeg: Float = 3.0f,
    val limpRefStillS: Int = 10, val limpRefMaxAgeS: Int = 60,
    val logRawAccel: Boolean = true,                // writes w_accel_raw.csv (§12.2) for AccelConfig replay tuning
)                                                   // all GUESSED (§8.1)

@Serializable data class TimedCueConfig(
    val firstSleepMinMin: Int = 10,                 // 1..60
    val firstSleepMaxMin: Int = 15,                 // firstSleepMinMin..90
    val betweenSleepS: Int = 420,                   // 60..1200
    val phase1ReportWindowS: Int = 60,              // 30..90 (Phase 1 only)
    val autoFallbackToTimed: Boolean = true,
    val watchLinkTimeoutS: Int = 120,               // 30..600
)

@Serializable data class AudioConfig(
    val route: AudioRouteMode = AudioRouteMode.NIGHTSTAND_SPEAKER,
    val userGain: Float = 0.5f,                     // 0..1; playback clamps it to routeCapGain[route] (§7.7)
    val routeCapGain: Map<AudioRouteMode, Float> = mapOf(
        AudioRouteMode.NIGHTSTAND_SPEAKER to 0.6f, AudioRouteMode.BLUETOOTH_SLEEP_AUDIO to 0.3f),   // 0..1, GUESSED
    val fadeInMs: Int = 2000,                       // 0..5000
    val carrierMode: CarrierMode? = null,           // null = route default (§7.5)
    val carrierLevelDbfs: Int = -75,                // -90..-50, step 5
    val carrierLeadS: Int = 10,                     // 0..60
    val focusMode: FocusMode = FocusMode.GAIN_TRANSIENT,
    val routeLossPolicy: RouteLossPolicy = RouteLossPolicy.PAUSE_CUES,
    val endAlarm: EndAlarm = EndAlarm.CHIME,
    val endChimeCount: Int = 3,                     // 1..3; "off" is endAlarm = NONE
    val ttsSpeechRate: Float = 0.75f,               // 0.5..1.2; TextToSpeech.setSpeechRate at Prepare cues (§7.3) [EXT]
    val phrasePauseMs: Int = 700,                   // 0..2000; silence between the phrases of a cue (§7.3) [EXT]
    val cueTexts: Map<PromptCue, String> = emptyMap(), // ≤ 300 chars each; missing or blank = the §7.1 text [EXT]
)

@Serializable data class RecordingConfig(
    val marginDb: Float = 10f, val hangoverMs: Int = 4000, val noSpeechTimeoutMs: Int = 15000,
    val maxMs: Int = 45000, val minSpeechFrames: Int = 5, val seedMs: Int = 400,   // ranges in §7.8
    val askSleepState: Boolean = true,              // [EXT]
    val answerMaxMs: Int = 8000, val answerHangoverMs: Int = 1500, val answerNoSpeechMs: Int = 6000,
    val sttEnabled: Boolean = true, val sttLanguage: String = "en-US",
)

@Serializable data class LinkConfig(
    val telemetryFlushS: Int = 2,                   // 1..30 (D3); 2 s for a live phone display (owner, 2026-10-27)
    val sdkFlushS: Int = 5,                         // 1..30
    val watchWakeLock: Boolean = true,              // measure and possibly turn off (§14.4)
)

@Serializable data class SensorsConfig(             // [EXT] watch, Samsung SDK path; logged, never used for detection
    val skinTempEnabled: Boolean = true,            // SKIN_TEMPERATURE_CONTINUOUS → w_skin_temp.csv
    val ppgRawEnabled: Boolean = true,              // PPG_CONTINUOUS → w_ppg_raw.csv (raw input of a later breathing estimate)
    val ppgChannels: Set<PpgChannel> = setOf(PpgChannel.GREEN), // GREEN, IR, RED; not empty while ppgRawEnabled
    val spo2SpotChecks: Boolean = true,             // SPO2_ON_DEMAND: first check 120 s in, then every interval
    val spo2IntervalMin: Int = 15,                  // 5..120
    val spo2TimeoutS: Int = 60,                     // 30..120 (Samsung advises ≤ 30 s per on-demand measurement)
)
```

`SessionConfig.validate(): List<String>` enforces every range above and the cross-rules of §8.14; the UI and
`ConfigSync` reject invalid configs. `DetectionConfig.thresholds()` resolves the preset plus overrides into
`(θ_drop, θ_rmssd, θ_still, holdS, holdShortS)`.

### 9.6 Engine API (pure, deterministic; D5)

```kotlin
class HypnagogiaDetectionEngine(config: SessionConfig) {
    val state: TdiState
    fun startSession(sessionId: String, mode: SessionMode): EngineResult
        // IDLE/SESSION_COMPLETE → CALIBRATING_BASELINE (DETECTION) or AWAKE_INCUBATING, mirror only (TIMED_CUES, §4)
    fun onPacket(packet: BiosignalPacket): EngineResult              // the only clock
    fun onCycleStage(cycle: Int, stage: CycleStageType, outcome: CycleOutcome = CycleOutcome.COMPLETED): EngineResult
    fun onTriggerRejected(cycle: Int, code: String): EngineResult
    fun pause(): EngineResult
    fun resume(): EngineResult
    fun abort(reason: SessionEndReason): EngineResult
    fun updateConfig(config: SessionConfig): EngineResult            // thresholds and countdown apply from the next packet;
                                                                     // baselineWindowS only for the next session
}
data class EngineResult(val score: DetectionScore?, val events: List<EngineEvent>)
sealed interface EngineEvent {
    val atMs: Long                                                   // packet time (last packet for non-packet calls)
    data class StateChanged(override val atMs: Long, val from: TdiState, val to: TdiState, val reason: StateReason) : EngineEvent
    data class BaselineReady(override val atMs: Long, val stats: BaselineStats) : EngineEvent
    data class BaselineExtended(override val atMs: Long, val reason: StateReason) : EngineEvent
    data class OnsetDetected(override val atMs: Long, val trigger: HypnagogiaTrigger) : EngineEvent
    data class CountdownAborted(override val atMs: Long, val cycle: Int, val reason: CountdownAbortReason) : EngineEvent
    data class CycleMissed(override val atMs: Long, val cycle: Int, val reason: StateReason) : EngineEvent
    data class CycleCompleted(override val atMs: Long, val cycle: Int, val outcome: CycleOutcome, val reason: StateReason) : EngineEvent
    data class DetectionUnavailable(override val atMs: Long, val reason: StateReason) : EngineEvent
    data class SessionCompleted(override val atMs: Long, val reason: SessionEndReason) : EngineEvent
}
```

`TimedCueScheduler` (phone, `:shared`, pure) has the same shape but is clocked by `onTick(elapsedMs)` from the phone
service and draws L with `Random(sessionId.hashCode())`.

---

## 10. Data Layer protocol

### 10.1 Application ID and signing (D2)

FACT, quoted from https://developer.android.com/training/wearables/data/overview (updated 2026-10-23; [A] §4.1):
"The package name must match across devices. The signature of the package must match across devices. No other apps
have access to the data regardless of connection type." "Package name" means the application ID ("some Android APIs use
the term 'package name' … This is actually your application ID", build/configure-app-module).

Consequences:
- `:wear` and `:mobile` both set `applicationId = "com.opentdi"` explicitly. Namespaces stay `com.opentdi.wear` and
  `com.opentdi.mobile`; `:shared` code lives in package `com.opentdi.shared`.
- No `applicationIdSuffix`, or the same one on both.
- Both APKs are signed with the same key: one machine's debug keystore locally; one shared keystore on CI or a second
  machine.
- Component names on the devices are therefore `com.opentdi/com.opentdi.mobile.…` and `com.opentdi/com.opentdi.wear.…`,
  and every `adb shell pm`/`am` command uses the package `com.opentdi`.

### 10.2 Paths, APIs, capabilities

| Path | Direction | API | Payload | Reply |
|---|---|---|---|---|
| `/opentdi/state_command` | both | `MessageClient.sendRequest` | `TdiCommand` | `Ack`; retry 3× at 2 s if no reply in 5 s; the receiver de-duplicates by (sessionId, type, cycle, stage) |
| `/opentdi/hypnagogia_trigger` | watch → phone | `MessageClient.sendRequest` | `HypnagogiaTrigger` | `Ack(ok, code)`: `Ack(true, ACCEPTED)`, `Ack(true, DUPLICATE)` (already accepted; the watch treats it as accepted), `Ack(false, MODE_TIMED)`, `Ack(false, NOT_AWAKE_INCUBATING)`. Attempts at +0, +2, +4, +8 s from the first send, each with a 2 s app-level reply timeout, stopping at the missed-cycle deadline. A new attempt does not cancel the earlier `sendRequest` Task (it cannot be cancelled); the first `Ack` from any attempt wins and later replies are logged and ignored. The phone de-duplicates by (sessionId, cycle, onsetTimestampMs) |
| `/opentdi/sensor_stream` | watch → phone | batched `MessageClient`: `sendMessage` for routine batches (dropped on `TARGET_NODE_NOT_CONNECTED` = 4000), `sendRequest` + up to 3 retries for a batch that contains a state change **[EXT]** | `TelemetryBatch`, flushed every `telemetryFlushS` (2 s) and immediately on any state change | none / `Ack` |
| `/opentdi/session_files` **[EXT]** | watch → phone | `ChannelClient.openChannel` + `sendFile` at session end (Phase 2) | each watch CSV of the session | channel close reason |
| `/opentdi/wrist_audio_channel` | — | `ChannelClient` | reserved for the deferred wrist speaker / mic | — |

**Never `DataClient` for the stream** (D3, owner 17): data items are persisted and cloud-backed ([A] §4.8).
Messages must generally be under 100 KB (FACT, [A] §4.3); a 5-frame batch is about 2 KB (INFERENCE).

Capabilities (`res/values/wear.xml`, string-array `android_wear_capabilities` with `tools:keep`, [A] §4.6):
`opentdi_phone` in `:mobile`, `opentdi_watch` in `:wear`. The sender picks the node from
`CapabilityClient.getCapability(name, FILTER_REACHABLE)`, preferring `isNearby()`.

Listeners. Exactly one component receives each path, so the question "which one answers a `sendRequest` when both are
registered" never arises (the docs do not define it: INFERENCE):

| Path | Received by |
|---|---|
| `/opentdi/state_command` (requests, both devices) | the manifest `OpenTdiListenerService` only (`android:path="/opentdi/state_command"`, action `REQUEST_RECEIVED` only, §14.1). Its `onRequest` checks a process-wide flag set by the session FGS (`SessionServiceRegistry.running`): if the FGS runs, it hands the command to the FGS's handler and returns that handler's `Ack`; if not, a `StartSession` posts "Tap to start OpenTDI" and every command is answered `Ack(false, "WATCH_SERVICE_NOT_RUNNING")` (on the phone `"PHONE_SERVICE_NOT_RUNNING"`), since a background FGS start is not allowed ([A] §2.6, §4.4) |
| `/opentdi/hypnagogia_trigger`, `/opentdi/sensor_stream` (phone) | live listeners inside the phone FGS: `addRpcService` for the requests, `MessageClient.addListener` (`wear://*/opentdi/sensor_stream`, `FILTER_LITERAL`) for routine batches, on a background `Looper` (`Wearable.WearableOptions`), registered only while the FGS runs |

So the manifest service is never bound for the telemetry batches, in line with "If you only need to listen for
events when your app is already running … register a live listener" ([A] §4.4). §15 #23 checks it on the devices.

Live display before Phase 2 **[EXT]** (owner, 2026-10-27): a running watch logging session already sends its packets
as `TelemetryBatch`es (state `IDLE`, no score) with `sendMessage` to the reachable `opentdi_phone` node (nearby
first), dropping a batch it cannot send and logging `LINK_DOWN` / `LINK_UP` once per change. Until the phone FGS
takes the stream, the phone's session screen registers the same live listener while it is visible (started) and
shows the last 180 frames; the phone logs nothing of them yet.

Clock offset: each device logs its wall clock on send and receive; the phone estimates the offset from `sendRequest`
round trips (log only, Phase 2).

### 10.3 Config sync and the bezel

Watch bezel (D10): two Wear Material3 `Picker`s, which have built-in rotary snap and haptics
(`PickerDefaults.rotarySnapBehavior`, `Picker.kt:102, :165, :501`, [W] §1.7): countdown 60, 75, …, 300 s (17 options,
default 180) and sensitivity Low / Standard / High. They consume the same `RotaryScrollEvent` that
`onRotaryScrollEvent` would; a hand-written `onRotaryScrollEvent` handler is used only if a free-form dial is ever
needed, because the per-detent pixel value is not documented ([W] §1.4) **[DEV, minor]**. Any change sends
`ConfigSync(config with revision = now)`; the receiver keeps the higher revision. No vibration during a nap.

### 10.4 Sequence diagrams

**A. Session start (DETECTION)**

```
User          Watch app/FGS (health)                 Phone UI / NapSessionService (mediaPlayback|microphone)
 │ opens watch app ─► starts HealthTrackingForegroundService (visible UI), connects Samsung SDK,
 │                    shows sensor status; engine IDLE; telemetry off
 │ taps Start on phone ──────────────────────────────► startForegroundService() while the UI is visible
 │                                                       ── wake lock, DND/route checks, cue WAVs ready
 │                    ◄── /state_command StartSession(id, config, PHONE) ── sendRequest
 │   engine.startSession → CALIBRATING_BASELINE ─ Ack(ok) ─►
 │   telemetry every 5 s ───────────────────────────► phone: CALIBRATING (progress W s)
 │   ... W s of packets ...
 │   BaselineReady → AWAKE_INCUBATING; state-change batch (sendRequest, ACK) ─► phone plays INITIAL_INCUBATION
```

If the watch FGS is not running when `StartSession` arrives, the watch's `WearableListenerService` posts "Tap to start
OpenTDI sensing" and replies `Ack(false, "WATCH_SERVICE_NOT_RUNNING")`; the phone shows "Open OpenTDI on your watch".
A session can also be started from the watch (origin `WATCH`) if the phone service is already running (armed).

**B. Normal cycle**

```
Watch engine                                    Phone service
AWAKE_INCUBATING: gate ∧ A held holdReq s
OnsetDetected → HYPNAGOGIA_DETECTED
  /hypnagogia_trigger (sendRequest) ───────────► dedupe; state AWAKE? → Ack(ACCEPTED); start countdown (countdownMs)
  keeps evaluating majorMovement                    elapsed-realtime timer under the session wake lock
                                                 countdown expires
  ◄──────── CycleStage(WAKING_USER) ────────────── focus GAIN_TRANSIENT; WAKE_PROMPT with 2 s fade-in
WAKING_USER (detection suspended)                prompt ends → recorder starts (built-in mic)
  ◄──────── CycleStage(RECORDING_DREAM) ───────── EndOfReportDetector … stop (SILENCE / NO_SPEECH / MAX)
RECORDING_DREAM                                  SLEEP_STATE_QUESTION + answer (if enabled)
  ◄──────── CycleStage(RE_INCUBATING) ─────────── RE_INCUBATION cue
RE_INCUBATING                                    cue ends
  ◄──────── CycleStage(CYCLE_COMPLETE, COMPLETED)─ journal JSON + WAV; STT starts
completedCycles++; < max → AWAKE_INCUBATING (refractory 120 s) | == max → SESSION_COMPLETE (phone: SESSION_END)
```

**C. Countdown abort by movement**

```
Watch engine                                    Phone service
HYPNAGOGIA_DETECTED, t = onset + 47 s
3 of the last 5 s have stillness < 0.5
CountdownAborted → AWAKE_INCUBATING (hold = 0, no refractory)
  /state_command AbortCountdown(cycle, MAJOR_MOVEMENT) ─► cancel countdown; state AWAKE_INCUBATING; log
                                                         (if the abort arrives after WAKING_USER was sent: ignore it, log ABORT_TOO_LATE)
```

**D. Phone unreachable (Phases 1–3; the standalone watch fallback is deferred)**

```
Watch engine                                    Phone
OnsetDetected → HYPNAGOGIA_DETECTED
  sendRequest fails / no Ack ─ retry +2 s, +4 s, +8 s ─ ✗
  no CycleStage(WAKING_USER) by onset + countdownMs + 15 s
CycleMissed(MISSED_CYCLE) → AWAKE_INCUBATING, refractory 120 s
  keeps sensing and logging CSV; routine batches dropped (TARGET_NODE_NOT_CONNECTED)
                                                no batch for 120 s → notification "Watch link lost"
                                                autoFallbackToTimed → TIMED_CUES (keeps cycle count, §4.4)
link back:
  trigger ─────────────────────────────────────► Ack(false, "MODE_TIMED") → watch: PHONE_REJECTED, refractory
```

If the phone accepted the trigger but the watch still declares the cycle missed (for example the `Ack` was lost and
`WAKING_USER` was late), the phone sees a telemetry frame with `state = AWAKE_INCUBATING`, `stateReason = MISSED_CYCLE`
for the current cycle: if its countdown is still running it cancels it, and it logs `WATCH_MISSED` in
`p_transitions.csv`. A `CycleStage` that reaches the watch for a cycle it already declared missed is logged
`LATE_STAGE_IGNORED` and ignored (the deadline is 15 s after the countdown, so this needs a link delay above 15 s).

If the phone process dies, the phone sends nothing; the watch keeps detecting, misses cycles and logs them until the
user aborts or `sessionMaxMin` (enforced on the watch too, as a safety net) ends the session.

**E. TIMED_CUES without a watch**

```
Phone service (no watch, or watch not connected)
Start (UI visible) → FGS; AWAKE_INCUBATING; INITIAL_INCUBATION; draw L ∈ {10..15} min
t = L       → HYPNAGOGIA_DETECTED (TIMED_DWELL); INITIAL_INCUBATION; dwell 180 s
t = L+180 s → WAKING_USER → RECORDING_DREAM → RE_INCUBATING → cycle complete
+420 s      → next dwell … ×4 → SESSION_COMPLETE; SESSION_END chime
(no StartSession is sent if no opentdi_watch node is reachable; if one is, StartSession(mode = TIMED_CUES) is sent and
 the watch logs packets and mirrors stages, including DWELL_START at each dwell, §4)
```

---

## 11. Samsung Health Sensor SDK integration (watch)

### 11.1 Versions

- SDK **1.4.1** (2025-08-20, stability patch); 1.4.0 (2025-07-31) added `EDA_CONTINUOUS` and the Android 16 permission
  rules (FACT, [S] §1). Target device: Galaxy Watch4 and later; EDA: Galaxy Watch8 series and later (FACT, [S] §2.3).
- SDK 1.4.0 needs **Health Sensor Service 1.6.5 or above** (formerly "Health Platform"). The compatibility table has no
  1.4.1 row (UNVERIFIED; assume 1.6.5+) (FACT, [S] §5).
- The installed Health Platform / Health Sensor Service package is **`com.samsung.android.service.health`**, observed
  on the owner's SM-L500 as versionName **1.7.00.05** (versionCode 170005001), which is ≥ 1.6.5 (FACT, [DA] "Samsung
  Health Platform (watch)"). `com.samsung.android.service.health.tracking` is the SDK's Java package, not an installed
  app ([DA]); it is used only for `Class.forName` and the R8 rule (§11.5). Every package or version lookup uses
  `com.samsung.android.service.health`.

### 11.2 Trackers, keys, types, units

| Tracker (`HealthTrackerType`) | Keys (`ValueKey`) | Type | Unit / meaning | Rate | Source |
|---|---|---|---|---|---|
| `HEART_RATE_CONTINUOUS` | `HeartRateSet.HEART_RATE` | `Integer` | bpm | 1 Hz | FACT [S] §2.2–2.3 |
| | `HeartRateSet.HEART_RATE_STATUS` | `Integer` | 1 ok; 0 initial / higher-priority sensor; −2 movement; −3 detached; −8 weak PPG; −10 too weak / moving; −999 higher-priority sensor | | |
| | `HeartRateSet.IBI_LIST` | `List<Integer>` | ms, 0–4 per point; all in the first point of a batch, NULL in the rest | | |
| | `HeartRateSet.IBI_STATUS_LIST` | `List<Integer>` | 0 normal, −1 error | | |
| `EDA_CONTINUOUS` | `EdaSet.SKIN_CONDUCTANCE` | `Float` | µS | 1 Hz | FACT [S] §2.3 |
| | `EdaSet.STATUS` | `Integer` | 0 normal; −5 detached; −10 low quality | | |
| `ACCELEROMETER_CONTINUOUS` | `AccelerometerSet.ACCELEROMETER_X/Y/Z` | `Integer` | raw counts; g = value × 4.0/16383.75; "doesn't include gravity" (javadoc; VERIFY) | 25 Hz | FACT [S] §2.3 |

`DataPoint.getTimestamp()` is in ms (epoch base UNVERIFIED; both it and the arrival `System.currentTimeMillis()` are
logged). `getValue` results are treated as nullable (platform types). `AccelerometerSet.STATUS` is left out of the stub
(the 1.2.0 release note mentions it, the 1.4.1 javadoc does not, [S] §2.2).

Startup order: `HealthTrackingService(listener, context).connectService()` → `onConnectionSuccess` →
`getTrackingCapability().getSupportHealthTrackerTypes()` → for each of the three types present:
`getHealthTracker(type)` (it throws `UnsupportedOperationException` if the watch lacks it, `IllegalStateException` if
not connected) → `setEventListener(listener)` (one per type). Several continuous trackers may run at once (FACT,
[S] §2.3).

### 11.3 Delivery, batching, flush strategy

- FACT ([S] §2.3): with the display on, HR and EDA arrive one point per second; with the display off, Samsung batches
  ("tracking continuous heart rate normally gives 600 samples every 10 minutes"). `flush()` delivers the collected data
  at once (then `onFlushCompleted`), is supported for HR, EDA and accelerometer continuous trackers, resets the
  auto-push counter, and "Calling this API too frequently can lead to increased watch battery consumption."
- Design: while a session runs, the watch FGS calls `flush()` on all three trackers every `sdkFlushS` = 5 s (GUESSED),
  from a timer on `SystemClock.elapsedRealtime()` under the session's `PARTIAL_WAKE_LOCK` (`watchWakeLock`, §14.4). The
  aggregator orders data by `DataPoint` timestamp, never by arrival (§8.2). Detection latency is therefore about one
  flush period plus the hold.
- Wear OS 6 with targetSdk 36 is "always-on" (dimmed screen) ([A] §2.5); whether a dimmed screen counts as "display
  off" for Samsung's batching is VERIFY ON DEVICE.

### 11.4 Permissions for targetSdk 36

| Permission | Needed for | Declared | Runtime request | `pm grant` | Status |
|---|---|---|---|---|---|
| `android.permission.health.READ_HEART_RATE` (dangerous) | `HEART_RATE_CONTINUOUS` on targetSdk ≥ 36; also satisfies the `health` FGS type | yes | yes (`SDK_INT >= BAKLAVA`) | INFERENCE: yes | FACT [S] §3.1; [A] §1.2 |
| `com.samsung.android.hardware.sensormanager.permission.READ_ADDITIONAL_HEALTH_DATA` | `EDA_CONTINUOUS` on targetSdk ≥ 36 (owner 6) | yes | yes (Samsung: shows a consent pop-up) | yes: protection level `dangerous` (runtime, `pm grant`-able) on the SM-L500 | FACT [S] §3.1, §3.5; [DA] permissions table |
| `android.permission.ACTIVITY_RECOGNITION` (dangerous) | `ACCELEROMETER_CONTINUOUS`; also satisfies the `health` FGS type without a while-in-use limit | yes | yes | yes | FACT [S] §3.1; [A] §1.5 |
| `BODY_SENSORS` | only targetSdk ≤ 35 / API ≤ 35 watches | `maxSdkVersion="35"` | API ≤ 35 only | fails on API 36 ("has not requested permission") | FACT [A] §1.4; **VERIFY ON DEVICE** that Samsung's trackers do not check it on API 36 (owner 6) |
| `BODY_SENSORS_BACKGROUND` | not on API 36 (replaced by `READ_HEALTH_DATA_IN_BACKGROUND`) | not declared | — | hard-restricted | FACT [A] §1.1, §1.3 |
| `android.permission.health.READ_HEALTH_DATA_IN_BACKGROUND` | only if trackers report `PERMISSION_ERROR` with the screen off | not declared at first | — | INFERENCE: yes | **VERIFY ON DEVICE** ([S] §3.2) |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_HEALTH`, `WAKE_LOCK`, `INTERNET` | FGS; flush wake lock; `hasResolution()`/`resolve()` and the SDK policy download need network | yes | no (normal) | no (not changeable) | FACT [A] §1.3; [S] §3.4 |
| `POST_NOTIFICATIONS` | FGS notification visible in the drawer | yes | yes | yes | FACT [A] §2.1 |

**Conflict with owner requirement 6 (resolved, FACT):** heart rate needs `READ_HEART_RATE`, not
`ACTIVITY_RECOGNITION`; Samsung ties `ACTIVITY_RECOGNITION` to the accelerometer ([S] §0 item 4). OpenTDI requests all
three (`READ_HEART_RATE`, `READ_ADDITIONAL_HEALTH_DATA`, `ACTIVITY_RECOGNITION`), so the owner's intent is met.

`getPackageInfo("com.samsung.android.service.health", 0).versionName` reads the version (owner 4; catch
`NameNotFoundException` → `HSS_NOT_INSTALLED`; compare per numeric component, so "1.7.00.05" ≥ "1.6.5", [S] §5). The
package is visible because the AAR's merged manifest already adds `<queries>` for `com.samsung.android.service.health`
(FACT, [V] "SDK package facts"); in stub builds the app manifest declares the same entry (§14.1) so the check also runs
without the AAR.

### 11.5 Build wiring of the AAR and the stub (D15)

```kotlin
// wear/build.gradle.kts
// Matches samsung-health-sensor-api-1.4.1.aar (the file in the SDK zip, [V]) and samsung-health-sensor-api.aar
// (the name on Samsung's pages, [S] §7). More than one match is a build error, so two SDK versions never mix.
val samsungAars = fileTree("libs") { include("samsung-health-sensor-api*.aar") }.files
check(samsungAars.size <= 1) { "More than one Samsung AAR in wear/libs: $samsungAars" }
val samsungAar: File? = samsungAars.singleOrNull()
logger.lifecycle("Samsung Health Sensor SDK: ${samsungAar?.name ?: "not bundled (stub)"}")
dependencies {
    if (samsungAar != null) implementation(files(samsungAar))
    else compileOnly(project(":samsung-health-stub"))      // classes absent at runtime → AAR_NOT_BUNDLED
}
android {
    buildFeatures { buildConfig = true }                   // off by default since AGP 8.0; the project uses AGP 9.4.0
    defaultConfig {
        buildConfigField("boolean", "SAMSUNG_SDK_BUNDLED", (samsungAar != null).toString())
        buildConfigField("String", "SAMSUNG_SDK_AAR", "\"${samsungAar?.name ?: ""}\"")   // logged to w_config.csv
    }
}
```

`buildFeatures { buildConfig = true }` is required: "AGP 8.0 doesn't generate BuildConfig by default" (FACT,
https://developer.android.com/build/releases/past-releases/agp-8-0-0-release-notes), and `gradle/libs.versions.toml`
pins `agp = "9.4.0"`. The SHA-256 of the AAR is recorded in `wear/libs/README.md` (not recomputed by the build).

At runtime, `BuildConfig.SAMSUNG_SDK_BUNDLED == false` (or `Class.forName(
"com.samsung.android.service.health.tracking.HealthTrackingService")` failing) → `AAR_NOT_BUNDLED`. The AAR sits in
the `:wear` application module ([S] §7). R8: `-keep class com.samsung.android.service.health.tracking.** { *; }` and
`-dontwarn` for the same package (third-party practice, [S] §7; test a minified build on the watch).

### 11.6 Status checker (`SensorStatus`) and exact on-watch texts

| Status | Detected by | On-watch text | Action |
|---|---|---|---|
| `AAR_NOT_BUNDLED` | §11.5 | "This build has no Samsung sensor SDK. Using basic watch sensors (no EDA or beat-to-beat data)." | `ANDROID_SENSORS` fallback (§11.8) |
| `HSS_NOT_INSTALLED` | `onConnectionFailed`, `getErrorCode() == PACKAGE_NOT_INSTALLED` (0) | "Health Sensor Service (Health Platform) is not installed. Tap to install it." | Tap → Activity → `if (e.hasResolution()) e.resolve(activity)`; meanwhile `ANDROID_SENSORS` |
| `HSS_TOO_OLD` | `onConnectionFailed`, `OLD_PLATFORM_VERSION` (1) | "Health Sensor Service (Health Platform) is too old for OpenTDI (needs 1.6.5 or newer). Tap to update it." | same (owner 4) |
| `SDK_POLICY_ERROR` | `TrackerEventListener.onError(TrackerError.SDK_POLICY_ERROR)` (a tracker error, **not** a connection error code: [S] §0 item 3) | "Samsung blocked sensor access (SDK_POLICY_ERROR). Developer builds need Health Sensor Service developer mode: see the OpenTDI developer setup guide. Also check that the watch is online." | Stop the trackers; offer `TIMED_CUES` or `ANDROID_SENSORS` (owner 2) |
| `PERMISSION_MISSING` | `onError(PERMISSION_ERROR)` or a denied runtime request | "OpenTDI needs permission for: heart rate / additional health data / physical activity. Tap to grant." | Request again from an Activity |
| `TRACKER_UNSUPPORTED` | `getHealthTracker` throws `UnsupportedOperationException`, or the type is missing from the capability list | EDA: "This watch does not support EDA. Detection continues without it." HR or accelerometer: "This watch does not support <heart rate / motion> through Samsung's SDK. Using basic watch sensors." | EDA: continue without E. HR/accel: `ANDROID_SENSORS` |
| `CONNECTION_FAILED` | `onConnectionFailed` with another code, or `onConnectionEnded` | "Cannot reach Health Sensor Service: <message>. Retrying." | Back off 1, 2, 4 … 30 s ([S] §8) |
| `CONNECTED_SAMSUNG` | all requested trackers delivering | "Samsung sensors connected: heart rate, IBI, EDA, motion." (EDA dropped from the text if unsupported) | — |
| `FALLBACK_ANDROID_SENSORS` | fallback active | "Using basic watch sensors (heart rate, motion). EDA and IBI are unavailable." | — |
| `SIMULATION` | simulation running | "SIMULATION — not real sensor data." | — |
| `NO_SENSORS` | neither path delivers HR + motion | "No usable sensors. You can still run a timed-cues session from the phone." | DETECTION refused |

The `SDK_POLICY_ERROR` text names developer mode and points to the setup guide but does not reproduce Samsung's steps on
the watch, because Samsung says "Do not share the developer mode guide with app users" ([S] §4). See O3.

### 11.7 Developer setup prerequisite (owner 1; README and `docs/SETUP.md`)

For developers building from source (not an end-user step): enable Health Sensor Service developer mode. Steps per
Samsung's page ([S] §4, https://developer.samsung.com/health/sensor/guide/developer-mode.html): Settings > Apps >
Health Sensor Service (may still read "Health Platform") > tap the title area about 10 times > a "Developer mode"
button appears below the version (older builds may show "[Dev mode]": UNVERIFIED) > tap it > agree to the Samsung
Health Partner Service & SDK License > turn it on. Partner registration (package name + signing SHA-256) is needed only
for public distribution ([S] §4). The setup doc links to Samsung's page as the authority.

### 11.8 SensorManager fallback (`ANDROID_SENSORS`)

| Sensor | Registration | Conversion | Notes |
|---|---|---|---|
| `Sensor.TYPE_HEART_RATE` | `registerListener(…, samplingPeriodUs = 1_000_000)` | `values[0]` bpm; valid if > 0 and accuracy is not `SENSOR_STATUS_UNRELIABLE` / `NO_CONTACT` (INFERENCE, VERIFY) | needs `READ_HEART_RATE` on API 36 (FACT, [A] §1.1) |
| `Sensor.TYPE_ACCELEROMETER` | `samplingPeriodUs = 40_000` (25 Hz) | m/s² / 9.80665 → g; **includes gravity** (standard Android definition) | below the 200 Hz cap, so no `HIGH_SAMPLING_RATE_SENSORS` ([A] §1.6) |

No IBI and no EDA: the effects are in §8.12. Event timestamps are converted from `SensorEvent.timestamp` (ns,
elapsed-realtime base) to epoch ms with one offset taken at registration.

### 11.9 Emulator

"Samsung Health Sensor SDK does not support an emulator" (FACT, [S] §4). Real-sensor work needs the SM-L500; the
simulation receiver (§13) covers only the algorithm and the audio loop (owner 3).

---

## 12. Logging and replay

### 12.1 Location, naming, format, retention

- Both devices: `filesDir/sessions/<sessionId>/` (app-private), e.g. `/data/data/com.opentdi/files/sessions/…`.
- CSV dialect: UTF-8, LF, comma separator, one header row, `.` decimal point, empty field = null, lists joined with
  `;`, booleans `0`/`1`. Floats are written with Kotlin `Float.toString()` (shortest round-trip form), so replay reads
  back bit-identical values.
- Files are flushed every 5 s and on every state change, and closed at session end.
- Rotation and retention (GUESSED): one directory per session, no size split (an hour of `w_packets.csv` is about
  3600 × 200 B ≈ 0.7 MB, INFERENCE); the last 30 sessions are kept and older ones are deleted at app start; if free
  space falls below 50 MB, `w_sensor_raw.csv` stops and `STORAGE_LOW` is logged. `w_ppg_raw.csv`, the biggest file,
  stops first, with its tracker, below 100 MB (GUESSED), also logging `STORAGE_LOW`.

### 12.2 Watch CSVs

| File | One row per | Columns (units) |
|---|---|---|
| `w_config.csv` | key | `key,value`: app version, `applicationId`, device model, SDK `getVersion()`, Health Sensor Service `versionName`, `sensorStatus`, `gravityMode`, every `SessionConfig` field (dotted keys, e.g. `detection.baselineWindowS`), the resolved thresholds, every structural constant of §8.15 (`const.*`), the bundled AAR name (`BuildConfig.SAMSUNG_SDK_AAR`), and every `BaselineStats` field once ready |
| `w_accel_raw.csv` (optional, `accel.logRawAccel`, default on) | raw 25 Hz accelerometer sample | `sdk_ts_ms,x,y,z` (raw counts, or m/s² × 1000 as integers for `ANDROID_SENSORS`), about 90 000 rows ≈ 2–3 MB per hour (INFERENCE). Input of the AccelConfig replay (§12.6) |
| `w_sensor_raw.csv` | SDK HR or EDA data point (the raw 1 Hz data) | `arrival_wall_ms,sdk_ts_ms,tracker(HR/EDA),hr_bpm,hr_status,ibi_ms(;),ibi_status(;),eda_us,eda_status,batch_size,late(0/1)` |
| `w_packets.csv` (**replay input**) | emitted 1 Hz packet | `ts_ms,seq,source,hr_bpm,hr_status,ibi_ms(;),rmssd_ms,eda_us,eda_status,accel_mean_g,accel_rms_g,accel_peak_g,stillness,jerk,limp,tilt_deg,n_accel_samples,emitted_wall_ms,skin_temp_c,spo2_pct` (new columns are only ever appended; readers go by header) |
| `w_accel_events.csv` (25 Hz-derived flags) | event | `sample_ts_ms,event,value1,value2,detail` with events `JERK_BURST_OPEN`, `JERK_CONFIRMED` (burst ms, peak g), `JERK_REJECTED_LONG`, `JERK_REJECTED_POST_MOVE`, `LIMP_REF_SET`, `LIMP_REF_CLEARED`, `LIMP_CONFIRMED` (tilt °), `GRAVITY_MODE`, `LIMP_UNAVAILABLE` |
| `w_engine.csv` | packet processed by the engine | `ts_ms,state,confidence,detection_active,still_mean,moving_s,gate,hr_ma,hr_drop,hr_sig,rmssd_rise,rmssd_sig,eda_slope,eda_bonus,jerk_bonus,limp_bonus,hold_s,hold_req_s,refractory` |
| `w_inputs.csv` (**replay input**) | external call into the engine | `after_packet_ts_ms,wall_ms,input,args` with inputs `START`, `CYCLE_STAGE(cycle,stage,outcome)`, `TRIGGER_REJECTED(cycle,code)`, `PAUSE`, `RESUME`, `ABORT(reason)`, `CONFIG(revision + changed keys)`; `after_packet_ts_ms` = timestamp of the last packet processed before the call (−1 if none) |
| `w_transitions.csv` (**replay golden**) | state change or engine event | `ts_ms,wall_ms,event,from,to,reason,cycle,completed_cycles,detail` (events: `STATE`, `BASELINE_READY`, `BASELINE_EXTENDED`, `ONSET` with confidence and evidence, `COUNTDOWN_ABORTED`, `CYCLE_MISSED`, `CYCLE_COMPLETED`, `DETECTION_UNAVAILABLE`, `SESSION_COMPLETED`) |
| `w_link.csv` | Data Layer send / receive | `wall_ms,dir,path,type,session_seq_or_cycle,result,rtt_ms` |
| `w_skin_temp.csv` (`sensors.skinTempEnabled`) | `SkinTemperatureSet` data point | `arrival_wall_ms,sdk_ts_ms,object_temp_c,ambient_temp_c,status,batch_size` (status 0 normal, −1 error) |
| `w_spo2.csv` (`sensors.spo2SpotChecks`) | `SpO2Set` data point (`event` `DATA`), plus one row when a spot check ends | `arrival_wall_ms,sdk_ts_ms,spo2_pct,hr_bpm,status,event(DATA/SPO2_RESULT/SPO2_TIMEOUT/SPO2_ERROR/SPO2_ABORTED),hr_ok_points,window_ms`; the end row carries the last data point, the HR points with `HEART_RATE_STATUS` 1 that arrived during the check (do spot checks interrupt heart rate?) and the check's length. `SPO2_ERROR`: SpO2 stopped mid-check (tracker error, reconnect, Samsung path stopped); `SPO2_ABORTED`: the session stopped mid-check |
| `w_ppg_raw.csv` (`sensors.ppgRawEnabled`) | raw 25 Hz `PpgSet` sample | `sdk_ts_ms,ppg_green,green_status,ppg_ir,ir_status,ppg_red,red_status` (channels not in `sensors.ppgChannels` stay empty); 90 000 rows ≈ 2.5 MB per hour green only, ≈ 4 MB with all three (INFERENCE: 5–7-digit ADC values). Raw input for a later breathing-rate estimate |

### 12.3 Phone CSVs

| File | One row per | Columns |
|---|---|---|
| `p_config.csv` | key | as `w_config.csv`, plus audio route, cue file hashes, TTS engine, `onBeginSynthesis` rate and encoding, `PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED`, `ro.audio.flinger_standbytime_ms` if readable |
| `p_frames.csv` | received `TelemetryFrame` | `rx_wall_ms,batch_seq,frame_seq` + every `w_packets.csv` packet column + `state,state_reason,completed_cycles,confidence,gate,hr_sig,rmssd_sig,eda_bonus,jerk_bonus,limp_bonus,hold_s,hold_req_s` |
| `p_transitions.csv` | phone-side state change | `wall_ms,elapsed_ms,from,to,reason,cycle,driver(WATCH/PHONE/TIMED)` |
| `p_audio.csv` | audio event | `wall_ms,elapsed_ms,cycle,event,cue,route,routed_type,gain,fade_ms,focus,dnd_filter,dnd_categories,stream_index,detail` with events `CARRIER_START/STOP`, `CUE_START`, `CUE_END`, `CUE_SKIPPED`, `FOCUS_DENIED`, `FOCUS_LOSS`, `ROUTE_MISMATCH`, `ROUTE_LOST`, `ROUTE_RESTORED`, `DND_STATE`, `VOLUME_RAISED`, `REC_START` (source, routed mic), `REC_STOP` (reason, duration, floor dB, spoke), `CAPTURE_SILENCED`, `COMM_DEVICE_BUSY`, `STT_START`, `STT_RESULT` (status, characters), `ANSWER` (sleep state) |
| `p_link.csv` | Data Layer send / receive | as `w_link.csv` |

### 12.4 Journal (D14; Phase 2)

`filesDir/journal/<sessionId>/`: per awakening `cycle_<n>.json`, `cycle_<n>.wav` (report), `cycle_<n>_answer.wav`
(sleep-state answer), `cycle_<n>_telemetry.csv` (the `p_frames.csv` rows from onset − 120 s to the end of
`RE_INCUBATING`; absent in TIMED_CUES without a watch). No database.

```kotlin
@Serializable data class AwakeningRecord(
    val sessionId: String, val cycle: Int, val mode: SessionMode, val theme: String,
    val onsetTimestampMs: Long,          // DETECTION: trigger packet time; TIMED: dwell start (phone wall clock)
    val triggerReceivedWallMs: Long?, val triggerConfidence: Float?, val evidence: DetectionScore?,
    val baseline: BaselineStats?, val countdownMs: Long, val wakePromptWallMs: Long,
    val recordingFile: String?, val recordingDurationMs: Long?, val recordingStopReason: String?,  // SILENCE/NO_SPEECH/MAX
    val transcript: String?, val transcriptStatus: String,   // OK, UNAVAILABLE_NO_ON_DEVICE_SERVICE, PENDING_MODEL, ERROR_<n>, RETRY_PENDING, DISABLED
    val sleepStateAnswer: SleepStateAnswer, val sleepStateTranscript: String?, val answerFile: String?,
    val telemetryFile: String?, val outcome: CycleOutcome, val audioRoute: AudioRouteMode,
)
```

### 12.5 Retrieving logs with adb

Debug builds are debuggable, so `run-as` can read app-private files (INFERENCE: standard Android behaviour; not in the
reference notes):

```bash
W=<WATCH_SERIAL>; P=<PHONE_SERIAL>
adb -s $W exec-out run-as --user 0 com.opentdi tar cf - files/sessions > watch_sessions.tar
adb -s $P exec-out run-as --user 0 com.opentdi tar cf - files/sessions files/journal > phone_sessions.tar
# one file:
adb -s $W exec-out run-as --user 0 com.opentdi cat files/sessions/<sessionId>/w_packets.csv > w_packets.csv
```

Device safety (CLAUDE.md §6): every phone-side command names user 0, because the phone has a Secure Folder profile
(user 150) and `pm list packages` without `--user 0` throws `SecurityException` there ([DA] "adb notes"); the watch
commands do the same for uniformity. That this build's `run-as` accepts `--user` is UNVERIFIED (INFERENCE from AOSP
`run-as` usage); if it refuses it, drop the flag only for `run-as`, which can read nothing but `com.opentdi`'s own files.

Phase 2 also copies the watch's session CSVs to the phone at session end over `/opentdi/session_files`
(`ChannelClient.sendFile`, [A] §4.8) into `files/sessions/<sessionId>/watch/`.

### 12.6 Replay harness (`:shared` test sources; owner 13)

`shared/src/test/kotlin/com/opentdi/shared/replay/ReplayHarness.kt`:

1. `load(dir)`: `w_config.csv` → `SessionConfig`; `w_packets.csv` → `List<BiosignalPacket>`; `w_inputs.csv` → inputs;
   `w_accel_raw.csv` if present.
2. `run(configOverride = null)`: new engine; apply `START`; for each packet in order call `onPacket`, then apply every
   input whose `after_packet_ts_ms` equals that packet's timestamp, in file order. Returns the events and the scores.
3. Assertions:
   - **Golden**: with the recorded config, the sequence `(event, ts_ms, from, to, reason, cycle)` equals
     `w_transitions.csv` exactly, and each confidence equals `w_engine.csv` within 1e-6 (bit-exact inputs, §12.1).
   - **Synthetic fixtures**: the expected onset times of §13.4.
   - **Tuning runs** (not pass/fail): re-run a recorded nap with other presets or overrides and print onset times next
     to the journal's sleep-state answers (AWAKE / HALFWAY / ASLEEP), the only ground truth Dormio used ([P] §2.2).
   - **AccelConfig runs** (only for sessions recorded with `logRawAccel`): feed `w_accel_raw.csv` through
     `AccelProcessor` with another `AccelConfig`, replace `accel_*`, `stillness`, `jerk` and `limp` in each packet of
     `w_packets.csv` by the new values (same `ts_ms` bins), and run the engine. With the recorded `AccelConfig` the
     recomputed fields must equal the logged ones. Without `w_accel_raw.csv`, AccelConfig (f, M, J_G, drift°) can be
     tuned only from live recordings (§15 #10), not by replay.
4. Fixtures live in `shared/src/test/resources/replay/<name>/`. Synthetic ones are generated by a test utility from the
   generator of §13. Real naps (packets and inputs only, no audio, no transcripts) are added only with the recorded
   person's consent (O9).

The live engine and the harness use one code path (`HypnagogiaDetectionEngine`), and the `EndOfReportDetector` can be
replayed from logged frame dB values the same way.

---

## 13. Simulation (Phase 2)

**Simulation validates the algorithm and the audio loop only, not real sensors** (owner 3). The emulator is not
supported by the Samsung SDK ([S] §4), and the synthetic curves below are design inputs, not physiology.

### 13.1 `SimulationBroadcastReceiver` contract (D11)

| Item | Value |
|---|---|
| Action | `com.opentdi.ACTION_SIMULATE_BIOSIGNALS` |
| Extra | `mode` (String): `FAST_HYPNAGOGIA`, `REALTIME_HYPNAGOGIA`, `AWAKE_CONTROL`, `STOP`; anything else → `SIM_REJECTED reason=BAD_MODE` |
| Sender restriction | `android.permission.DUMP` (`signature\|privileged\|development`; held by the shell UID, `packages/Shell/AndroidManifest.xml:164`) (FACT, [A] §3.3) |
| Manifest registration | `wear/src/debug/AndroidManifest.xml`: `<receiver android:name="com.opentdi.wear.sim.SimulationBroadcastReceiver" android:exported="true" android:permission="android.permission.DUMP">` with the action filter. It receives only broadcasts with `-p com.opentdi` or `-n` (an implicit broadcast never reaches a manifest receiver of an app targeting 26+, even while it runs: FACT, `BroadcastSkipPolicy.java:347-354`, [A] §3.1) |
| Dynamic registration | in `Application.onCreate()` of debug builds: `ContextCompat.registerReceiver(ctx, receiver, IntentFilter(ACTION), "android.permission.DUMP", handler, ContextCompat.RECEIVER_EXPORTED)`. This makes the exact command **without `-p`** work while the app process runs ([A] §3.2) |
| Duplicates | with `-p` while the app runs both receivers fire; `SimController` ignores a request with the same mode within 2 s **[EXT]** |
| Build types | debug only **[EXT]**: release builds contain neither registration |
| Behaviour | forwards to the running watch FGS. If it is not running: log `SIM_REJECTED reason=WATCH_SERVICE_NOT_RUNNING` (a receiver cannot start an FGS from the background, [A] §2.6) |
| On a run mode | stop the real trackers; `source = SIMULATION`; abort any running session; start a new DETECTION session with the pinned config and the overrides below and send `StartSession(origin = WATCH)` to the phone (whose service must be running, §13.6) |
| Pinned config **[EXT]** | the user's saved config, except `detection = DetectionConfig(sensitivity = <preset>)`: every detection field at its default (`baselineWindowS = 180`, `priorHrSource = NONE`, no threshold or hold overrides, default windows, refractory 120 s, grace 15 s, stage timeout 180 s). `<preset>` = optional extra `preset` (`LOW`, `MIT_STANDARD`, `HIGH`; default `MIT_STANDARD`; anything else → `SIM_REJECTED reason=BAD_PRESET`). `SIM_START` logs the full effective config (§13.5). The §13.4 numbers and the §13.5 pass criteria hold only for this pinned config |
| `STOP` | `AbortSession(USER)`, restore the real sensors, engine `IDLE` |

| Mode | Config overrides | Time compression | Curves |
|---|---|---|---|
| `FAST_HYPNAGOGIA` | `countdownS = 15` (allowed only with `simulation != null`), `maxCycles = 2` | ×10: one packet per 100 ms of real time **while the engine is in `CALIBRATING_BASELINE` or `AWAKE_INCUBATING`**, one per 1000 ms otherwise **[EXT]**; packet timestamps always advance 1000 ms | hypnagogia (§13.2) |
| `REALTIME_HYPNAGOGIA` | none | none | hypnagogia |
| `AWAKE_CONTROL` | none | ×10 in the same states **[EXT]** | awake control (§13.3) |

Why compression stops outside the waiting states: the missed-cycle deadline (countdown + 15 s, D4) is measured in packet
time, and the phone's countdown in real time. At 1:1 from onset on, the 15 s phone countdown and the watch deadline
agree.

### 13.2 Synthetic generator (pure function in `:shared`)

`SyntheticBiosignals.packet(mode, segment, k, timestampMs)`; `k` = whole seconds since the segment started. The watch
driver picks the segment from the engine state:

| Segment | When | k counts from |
|---|---|---|
| `INITIAL` | from session start until the first `CYCLE_COMPLETE` (incl. calibration, waiting, countdown) | session start |
| `TALK` | engine in `WAKING_USER`, `RECORDING_DREAM`, `RE_INCUBATING` | (not used) |
| `REARM` | after each `CYCLE_COMPLETE` (waiting, countdown) | `T` = timestamp of the last packet processed when `CYCLE_COMPLETE` was applied (packet `T + 1000` has k = 1) |

Deterministic noise, bounded in [−1, 1], period 11, mean 0 over any 11 consecutive k:

```
n_c(k) = ((((k + φ_c) · 7919) mod 11) − 5) / 5        φ_HR = 0, φ_R = 3, φ_E = 5, φ_S = 7
```

Hypnagogia curves (`FAST_HYPNAGOGIA`, `REALTIME_HYPNAGOGIA`):

| Segment / k | HR (bpm) | RMSSD (ms) | EDA (µS) | stillness | events |
|---|---|---|---|---|---|
| `INITIAL` 0 ≤ k < 300 | 70 + n_HR | 40 + n_R | 2.00 + 0.005·n_E | 0.92 + 0.03·n_S; **0.20 at k = 240–243** (awake fidget) | — |
| `INITIAL` 300 ≤ k < 360, f = (k − 300)/60 | 70 − 10f + n_HR | 40 + 8f + n_R | 2.00 − 0.10f + 0.005·n_E | 0.97 + 0.02·n_S; **0.30 at k = 330** (jerk burst second) | `jerkEvent` at k = 333, `limpEvent` at k = 345 |
| `INITIAL` k ≥ 360 | 60 + n_HR | 48 + n_R | 1.90 + 0.005·n_E | 0.97 + 0.02·n_S | — |
| `TALK` | 74 + n_HR | 35 + n_R | 2.10 | 0.60 + 0.03·n_S | — |
| `REARM` 0 < k < 150 | 72 + n_HR | 38 + n_R | 2.05 + 0.005·n_E | 0.92 + 0.03·n_S | — |
| `REARM` 150 ≤ k < 210, f = (k − 150)/60 | 72 − 12f + n_HR | 38 + 10f + n_R | 2.05 − 0.10f + 0.005·n_E | 0.97 + 0.02·n_S; **0.30 at k = 180** | `jerkEvent` at k = 183, `limpEvent` at k = 195 |
| `REARM` k ≥ 210 | 60 + n_HR | 48 + n_R | 1.95 + 0.005·n_E | 0.97 + 0.02·n_S | — |

Other fields: `accelRms = 0.003 + (1 − s)·(0.05 − 0.003)` (consistent with §8.1); `accelPeak = 0.45 g` at the
jerk-burst seconds (`INITIAL` k = 330, `REARM` k = 180), so it exceeds J_G = 0.30 g like a real jerk, and
`min(1.0, 2·accelRms)` otherwise (the formula alone would give 2 × 0.0359 = 0.072 g there); `accelMean = 1.0`, `hrStatus = 1`, `edaStatus = 0`,
`ibiList = [round(60000 / HR)]`, `source = SIMULATION`.

### 13.3 Awake-control curves (`AWAKE_CONTROL`, segment `INITIAL` only)

| k | HR | RMSSD | EDA | stillness |
|---|---|---|---|---|
| 0 ≤ k < 180 | 70 + n_HR | 40 + n_R | 2.00 + 0.005·n_E | 0.92 + 0.03·n_S |
| k ≥ 180 | 70 − 2·min(k − 180, 600)/600 + n_HR (slow relaxation drift to 68) | 40 + n_R | 2.00 + 0.005·n_E (flat, so the EDA bonus is often true) | 0.92 + 0.03·n_S; **0.20 for 3 s every 120 s** (k = 240 + 120j … 242 + 120j) |

No jerk or limp events.

### 13.4 Proof by arithmetic (`MIT_STANDARD` unless stated)

The numbers below were computed with a reference model of §8.6–8.7 applied to these curves; the unit tests in §16
assert them exactly.

**Noise bound.** Any 11 consecutive `n_c` sum to 0, so any 15 consecutive sum to the sum of 4 consecutive values. Over
one period the values run −1, +1, 0.8, 0.6, 0.4, 0.2, 0, −0.2, −0.4, −0.6, −0.8, so 4 consecutive values sum to
between −2.8 and +2.8. Hence the noise term of `HR_ma` lies in [−0.187, +0.187] bpm.

**Baseline** (k = 0–179, 180 frames, none below stillness 0.5, none discarded):
μ_HR = 70 + 1.4/180 = **70.0078** (the 4 residual values at k = 176–179 sum to 1.4), σ_HR = 0.6403 bpm,
μ_R = 40.0067 ms, μ_E = 1.99999 µS, σ_E = 0.00314 → floored to 0.005 µS, so the spike limit is max(1.5 × 0.005,
0.02) = 0.02 µS. Thresholds: HR_ma ≤ 70.0078 × 0.93 = **65.107**; RMSSD ≥ 40.0067 × 1.15 = **46.008**.

**No trigger during calibration:** the rule is not evaluated in `CALIBRATING_BASELINE` (row 3).

**No trigger in the first awake minutes** (k = 180–299): HR_ma ≥ 70 − 0.187 = 69.813, so d_HR ≤ (70.0078 − 69.813) /
70.0078 = 0.28 %; RMSSD ≤ 41.0, so r ≤ 2.48 %. Both are below the smallest thresholds anywhere (θ_drop ≥ 0.04,
θ_rmssd ≥ 0.05), so A is false for every preset and override. In addition the fidget at k = 240–243 (4 moving
seconds) keeps G false from k = 242 to k = 270: at k = 240–241 the 30 s window holds only 1–2 moving seconds and
m_still ≥ 0.87 ≥ 0.80, and from k = 271 only k = 242–243 are left in it.

**Cycle 1 onset.** For k ≥ 314 the 15 s window lies inside the descent, so HR_ma(k) = 70 − (k − 307)/6 + noise, with
the noise in [−0.187, 0.187]:
- H_sig cannot be true before (k − 307)/6 ≥ 70 − 65.107 − 0.187 = 4.706 → k ≥ 336, and must be true by
  (k − 307)/6 ≥ 5.080 → k ≥ 338. Exactly: **first at k = 337** (HR_ma 65.027, d_HR = 7.12 %).
- R_sig cannot be true before 40 + 8(k − 300)/60 + 1 ≥ 46.008 → k ≥ 338; exactly first at k = 339. So HR leads.
- G at k = 337: window k = 308–337 has one moving second (k = 330, stillness 0.30) and m_still ≥ (29 × 0.95 + 0.30)/30
  = 0.928 ≥ 0.80, so G is true.
- Bonuses: J true from k = 333; E true (EDA falls 0.00167 µS/s; consecutive steps ≤ 0.00167 + 0.01 < 0.02). So
  holdReq = 10.
- HR keeps falling, so the core stays true: **onset at k = 337 + 9 = 346**. At the onset HR_ma = 63.633 (d_HR =
  9.11 %, 1.47 bpm below the line), r = 14.3 %, S = A = P = B = 1, **confidence = 1.00**. From the bounds alone the
  onset must fall in [345, 347].
- Margin: on the plateau (k ≥ 375) HR_ma = 60 ± 0.19, d_HR ≈ 14.3 % = 2.0 × θ_drop, and RMSSD 47–49 ≥ 46.008.
- Countdown (15 s at 1:1): stillness ≥ 0.95 on the plateau, so no major movement and no abort; the phone sends
  `WAKING_USER` at about k = 361.

**Cycle 2 onset** (segment `REARM`, k counted from T). Refractory blocks k < 120 (k = 1–119; the guard is
`t < refractoryUntil = T + 120 000`). For 1 ≤ k < 150, HR_ma ≥ 71.81 >
μ_HR (d_HR < 0) and RMSSD ≤ 39 < μ_R, so A is false regardless. In the descent HR_ma(k) = 72 − (k − 157)/5 + noise;
H_sig needs (k − 157)/5 ≥ 6.893 ∓ 0.187 → k ∈ [191, 193]; exactly **first core at k = 193** (HR_ma 64.880, 7.32 %);
G is true (one moving second at k = 180); J is true from k = 183, so holdReq = 10; **onset at k = 202** (HR_ma 63.187,
d_HR 9.74 %, r 16.2 %, confidence 1.00). This does not depend on how long the phone's cycle took, because k restarts
at T, and the windows at k ≥ 193 lie entirely inside `REARM`.

**End:** the second `CYCLE_COMPLETE` makes `completedCycles` = 2 = `maxCycles` → `SESSION_COMPLETE` (`MAX_CYCLES`).

**Other presets** (exact, same curves): `LOW` onset k = 363 (first core 349, hold 15; plateau HR 60 < 63.007), cycle 2
at k = 217. `HIGH` onset k = 335 (first core 328, hold 8), cycle 2 at k = 192.

**AWAKE_CONTROL never triggers.** Same baseline (k < 180 is identical). For every k ≥ 180: HR_ma ≥ 68 − 0.187 = 67.813,
so d_HR ≤ (70.0078 − 67.813)/70.0078 = **3.14 % < 4 %** (the smallest θ_drop allowed anywhere); RMSSD ≤ 41.0, so
r ≤ **2.48 % < 5 %** (the smallest θ_rmssd). A is therefore false at every k, for every preset and every allowed
θ_drop / θ_rmssd override, with the pinned config of §13.1 (`priorHrSource = NONE`). A prior HR cannot change this
either, since §8.5 only ever lowers μ_HR,eff and so only lowers d_HR. So the core is never true and nothing triggers, even though G is often true between fidgets and E is often
true (flat EDA). The run ends with `STOP` or at `sessionMaxMin` with 0 cycles.

**Real-time length of a FAST run** (INFERENCE): cycle 1 onset after 347 compressed packets ≈ 34.7 s; countdown 15 s;
phone loop ≈ 35–65 s (prompt ≈ 6 s, recording 15 s if silent up to 45 s, answer ≤ 8 s, re-incubation ≈ 6 s); re-arm
202 compressed packets ≈ 20.2 s; countdown 15 s; second loop ≈ 35–65 s. Total ≈ 2.6–3.6 min.

### 13.5 Expected log lines (ADB verification)

Tags: watch `OTDI.Sim`, `OTDI.Engine`, `OTDI.Link`, `OTDI.Sensors`; phone `OTDI.Session`, `OTDI.Link`, `OTDI.Audio`,
`OTDI.Rec`, `OTDI.Stt`. Message format: `EVENT key=value …`. Engine `t` = seconds of packet time since `calStart`.

Watch, `FAST_HYPNAGOGIA`, `MIT_STANDARD`:

```
OTDI.Sim     SIM_START mode=FAST_HYPNAGOGIA compression=10 countdownS=15 maxCycles=2 preset=MIT_STANDARD config=<full effective SessionConfig JSON>
OTDI.Sensors STATUS SIMULATION
OTDI.Engine  STATE IDLE->CALIBRATING_BASELINE t=0 reason=SESSION_START
OTDI.Engine  BASELINE hrMean=70.01 hrSd=0.64 rmssdMean=40.01 edaMean=2.000 n=180 discarded=0
OTDI.Engine  STATE CALIBRATING_BASELINE->AWAKE_INCUBATING t=180 reason=BASELINE_READY
OTDI.Engine  ONSET cycle=1 t=346 conf=1.00 hrDrop=0.091 rmssdRise=0.143 hold=10/10 bonus=EDA,JERK,LIMP
OTDI.Engine  STATE AWAKE_INCUBATING->HYPNAGOGIA_DETECTED t=346 reason=ONSET
OTDI.Link    TX path=/opentdi/hypnagogia_trigger cycle=1 ack=ACCEPTED rttMs=<n>
OTDI.Engine  STATE HYPNAGOGIA_DETECTED->WAKING_USER t=<≈361> reason=PHONE_STAGE
OTDI.Engine  STATE WAKING_USER->RECORDING_DREAM t=<…> reason=PHONE_STAGE
OTDI.Engine  STATE RECORDING_DREAM->RE_INCUBATING t=<…> reason=PHONE_STAGE
OTDI.Engine  STATE RE_INCUBATING->AWAKE_INCUBATING t=<T> reason=CYCLE_COMPLETED
OTDI.Engine  ONSET cycle=2 t=<T+202> conf=1.00 …
OTDI.Engine  STATE RE_INCUBATING->SESSION_COMPLETE t=<…> reason=MAX_CYCLES
```

Phone:

```
OTDI.Session START id=<id> mode=DETECTION origin=WATCH sim=FAST_HYPNAGOGIA
OTDI.Session STATE CALIBRATING_BASELINE->AWAKE_INCUBATING driver=WATCH
OTDI.Audio   CUE_START cue=INITIAL_INCUBATION route=NIGHTSTAND_SPEAKER gain=0.50 fadeMs=2000
OTDI.Session TRIGGER cycle=1 conf=1.00 countdownMs=15000 ack=ACCEPTED
OTDI.Session COUNTDOWN_EXPIRED cycle=1
OTDI.Audio   CUE_START cue=WAKE_PROMPT route=NIGHTSTAND_SPEAKER gain=0.50 fadeMs=2000
OTDI.Rec     REC_START source=<UNPROCESSED|MIC> device=TYPE_BUILTIN_MIC
OTDI.Rec     REC_STOP reason=<SILENCE|NO_SPEECH|MAX> durMs=<n>
OTDI.Audio   CUE_START cue=SLEEP_STATE_QUESTION …
OTDI.Audio   CUE_START cue=RE_INCUBATION …
OTDI.Session STAGE CYCLE_COMPLETE cycle=1 outcome=COMPLETED
OTDI.Stt     STT_RESULT cycle=1 status=<OK|UNAVAILABLE_NO_ON_DEVICE_SERVICE|PENDING_MODEL|ERROR_n> chars=<n>
… cycle 2 …
OTDI.Session SESSION_COMPLETE reason=MAX_CYCLES cycles=2
```

Pass criteria: exactly 2 `ONSET` lines on the watch; no `COUNTDOWN_ABORTED`, `CYCLE_MISSED` or `SIM_REJECTED`; 2
`STAGE CYCLE_COMPLETE` and 1 `SESSION_COMPLETE` on the phone; and the phone log must **not** contain "Foreground
service started from background can not have location/camera/microphone access" (FACT, the AOSP log text,
`ActiveServices.java:2543-2548`, [A] §2.2). `AWAKE_CONTROL`: `BASELINE` and `AWAKE_INCUBATING` lines, then no `ONSET`
before `STOP`.

### 13.6 ADB hardware-in-the-loop procedure

**Before running it: ask the owner** (CLAUDE.md §6 "Ask the owner before the first install on each device and before
any audible playback test"). This run installs on both devices and plays cues aloud on the phone speaker, so it needs
the owner's yes for the first install on each device and for every run (it is an audible playback test).

```bash
W=<WATCH_SERIAL>; P=<PHONE_SERIAL>
./gradlew :wear:assembleDebug :mobile:assembleDebug
# Only com.opentdi, always user 0 (the phone has a Secure Folder profile, user 150; CLAUDE.md §6).
adb -s $W install -r -g --user 0 wear/build/outputs/apk/debug/wear-debug.apk     # -g grants requested runtime permissions
adb -s $P install -r -g --user 0 mobile/build/outputs/apk/debug/mobile-debug.apk
adb -s $W shell am start --user 0 -n com.opentdi/com.opentdi.wear.MainActivity    # visible UI starts the watch FGS
# Phone service: tap "Arm" in the phone app, or (debug builds) start it from the shell:
adb -s $P shell am start-foreground-service --user 0 -n com.opentdi/com.opentdi.mobile.session.NapSessionService -a com.opentdi.ACTION_ARM
# Never `logcat -c` (it clears the shared log buffer): read from a start time instead.
TW=$(adb -s $W shell date +'%m-%d %H:%M:%S.000'); TP=$(adb -s $P shell date +'%m-%d %H:%M:%S.000')
adb -s $W logcat -T "$TW" -v time -s OTDI.Sim OTDI.Engine OTDI.Link OTDI.Sensors > watch.log &
adb -s $P logcat -T "$TP" -v time -s OTDI.Session OTDI.Link OTDI.Audio OTDI.Rec OTDI.Stt > phone.log &
adb -s $W shell am broadcast --user 0 -a com.opentdi.ACTION_SIMULATE_BIOSIGNALS --es mode FAST_HYPNAGOGIA
```

The debug-only phone service entry is `exported="true"` with `android:permission="android.permission.DUMP"`. A
foreground-service start from the shell UID gets the while-in-use exemption (FACT, `ActiveServices.java:8447-8466`,
[A] §2.6); that this keeps microphone access for the run is INFERENCE, checked by the log line above. The broadcast
works without `-p` because of the dynamic receiver; add `-p com.opentdi` if the process may not be running (it will then
log `SIM_REJECTED reason=WATCH_SERVICE_NOT_RUNNING` unless the watch app was opened).

---

## 14. Platform constraints

### 14.1 Manifests

Watch (`:wear`, `applicationId com.opentdi`, namespace `com.opentdi.wear`):

```xml
<uses-feature android:name="android.hardware.type.watch" />
<uses-permission android:name="android.permission.health.READ_HEART_RATE" />
<uses-permission android:name="com.samsung.android.hardware.sensormanager.permission.READ_ADDITIONAL_HEALTH_DATA" />
<uses-permission android:name="android.permission.ACTIVITY_RECOGNITION" />
<uses-permission android:name="android.permission.health.READ_SKIN_TEMPERATURE" />  <!-- SKIN_TEMPERATURE_* -->
<uses-permission android:name="android.permission.health.READ_OXYGEN_SATURATION" /> <!-- SPO2_ON_DEMAND -->
<uses-permission android:name="android.permission.BODY_SENSORS" android:maxSdkVersion="35" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_HEALTH" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.WAKE_LOCK" />
<uses-permission android:name="android.permission.INTERNET" />
<!-- add only if §15 #4 shows PERMISSION_ERROR with the screen off:
<uses-permission android:name="android.permission.health.READ_HEALTH_DATA_IN_BACKGROUND" /> -->
<!-- the installed Health Platform package ([DA]); the AAR's merged manifest adds the same entry ([V]) -->
<queries><package android:name="com.samsung.android.service.health" /></queries>
<application>
  <service android:name=".sensing.HealthTrackingForegroundService"
      android:exported="false" android:foregroundServiceType="health" />
  <!-- state_command requests only; forwards to the FGS when it runs (§10.2) -->
  <service android:name=".link.OpenTdiListenerService" android:exported="true">
    <intent-filter>
      <action android:name="com.google.android.gms.wearable.REQUEST_RECEIVED" />
      <data android:scheme="wear" android:host="*" android:path="/opentdi/state_command" />
    </intent-filter>
  </service>
</application>
```

Phone (`:mobile`, `applicationId com.opentdi`, namespace `com.opentdi.mobile`):

```xml
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MICROPHONE" />   <!-- Phase 2 -->
<uses-permission android:name="android.permission.RECORD_AUDIO" />                    <!-- Phase 2 -->
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.WAKE_LOCK" />
<queries><intent><action android:name="android.speech.RecognitionService" /></intent></queries>
<application>
  <service android:name=".session.NapSessionService" android:exported="false"
      android:foregroundServiceType="mediaPlayback|microphone" />   <!-- Phase 1: mediaPlayback -->
  <service android:name=".link.OpenTdiListenerService" android:exported="true"> <!-- same filter as the watch --> </service>
</application>
```

`BLUETOOTH_CONNECT` (CLAUDE.md §6) is not declared: nothing in this design needs Bluetooth APIs, only
`AudioDeviceInfo` (whether showing a device's name or address needs it is VERIFY, §15 #22). The phone needs no
health permission and no Samsung permission (the SDK does not support phones, [S] §4).

### 14.2 Runtime requests and the `pm grant` list (API 36; replaces CLAUDE.md §6)

| Device | Runtime request in the app | `adb -s <serial> shell pm grant --user 0 com.opentdi …` |
|---|---|---|
| Watch | `READ_HEART_RATE`, `READ_ADDITIONAL_HEALTH_DATA`, `ACTIVITY_RECOGNITION`, `READ_SKIN_TEMPERATURE` and `READ_OXYGEN_SATURATION` (API ≥ 36; each missing one turns off only its tracker), `POST_NOTIFICATIONS` | `android.permission.health.READ_HEART_RATE`; `com.samsung.android.hardware.sensormanager.permission.READ_ADDITIONAL_HEALTH_DATA` (protection level `dangerous`, `pm grant`-able: FACT, [DA]); `android.permission.ACTIVITY_RECOGNITION`; `android.permission.health.READ_SKIN_TEMPERATURE` (`dangerous`, [DA]); `android.permission.health.READ_OXYGEN_SATURATION`; `android.permission.POST_NOTIFICATIONS` |
| Phone | `POST_NOTIFICATIONS`; `RECORD_AUDIO` (Phase 2) | `android.permission.POST_NOTIFICATIONS`; `android.permission.RECORD_AUDIO` |

Dropped from CLAUDE.md's list: `BODY_SENSORS` and `BODY_SENSORS_BACKGROUND` (they fail on API 36), `BLUETOOTH_CONNECT`
(not declared). Normal permissions (`FOREGROUND_SERVICE*`, `WAKE_LOCK`, `INTERNET`) cannot be granted and need no grant
(FACT, `PermissionManagerServiceImpl.java:1411-1420`, [A] §1.4). `adb install -r -g --user 0` grants all requested
runtime permissions in one go. Every command passes `--user 0` and touches only `com.opentdi` (CLAUDE.md §6).

### 14.3 Foreground services

| | Phone `NapSessionService` | Watch `HealthTrackingForegroundService` |
|---|---|---|
| Type | `mediaPlayback\|microphone` (Phase 1: `mediaPlayback`) | `health` |
| Started | `startForegroundService()` from the session screen (Start or Arm) **while the UI is visible**; `startForeground(id, n, MEDIA_PLAYBACK or MICROPHONE)` with both types at once in `onStartCommand` (`RECORD_AUDIO` granted first) | from the watch activity while visible; `startForeground(id, n, HEALTH)` |
| Lifetime | whole nap; never `stopForeground()` mid-nap (it clears while-in-use, and it cannot be regained from the background: FACT, AOSP, [A] §2.2) | whole nap (Ongoing Activity attached, `androidx.wear:wear-ongoing:1.1.0`, static icon required, [A] §2.5) |
| Wake-ups | the watch trigger and commands only call into this running service (owner 16) | phone commands only call into this running service |
| Type check | microphone needs `RECORD_AUDIO`; mediaPlayback none (FACT, [A] §2.1) | `FOREGROUND_SERVICE_HEALTH` + one of `ACTIVITY_RECOGNITION`, `READ_HEART_RATE`, … (FACT, `ForegroundServiceTypePolicy.java:490-522`) |
| Time limit | none (only `dataSync` and `mediaProcessing` are limited, [A] §2.1) | none |
| Killed by the user (Task Manager Stop) | no callback; on the next launch read `ApplicationExitInfo` `REASON_USER_REQUESTED` and mark the session aborted ([A] §2.2) | same |
| Restarted by the system | assume the mic is lost: detect (log line in §13.5, `isClientSilenced`) and tell the user | re-register trackers |

Never start an FGS from a receiver or a Data Layer callback (not a background-start exemption, [A] §2.6).
CSV writing and transcription run in the service's own coroutines, not WorkManager (Android 16 quotas, [A] §2.2).

### 14.4 Wake locks, Doze, timers

- `Handler.postDelayed` uses uptime ("time spent in deep sleep will add an additional delay"), and `Dispatchers.Main`
  `delay` uses it (FACT, [A] §2.4). So every countdown, dwell, between-sleep and flush timer runs under a
  `PARTIAL_WAKE_LOCK` and is computed from `SystemClock.elapsedRealtime()`.
- Phone: the FGS holds one `PARTIAL_WAKE_LOCK` for the whole session, with timeout `sessionMaxMin + 10 min`.
- Watch: the FGS holds a `PARTIAL_WAKE_LOCK` for the session when `watchWakeLock = true` (default), because the 5 s
  `flush()` timer must fire with the screen off. Wear guidance says workouts don't need a wake lock ([A] §2.4), so
  measure both settings (§15 #11) and turn it off if the trackers deliver on time without it.
- Doze: the doc says Doze "Ignores wake locks", but AOSP keeps the partial wake locks of a process running an FGS
  (`PowerManagerService.java:4409-4418`); OpenTDI relies on the AOSP behaviour ([A] §2.4) and verifies it passively
  with long screen-off runs and read-only `dumpsys deviceidle` / `dumpsys power` (§15 #12). `dumpsys deviceidle
  force-idle` changes system state, so it is run only with the owner's explicit approval for that run (CLAUDE.md §6).

### 14.5 Broadcast restrictions

See §13.1: an implicit broadcast never reaches a manifest receiver; the dynamic receiver needs `RECEIVER_EXPORTED`
(Android 14+), and the shell cannot reach a non-exported receiver ([A] §3.1–3.2). The system broadcast
`ACTION_AUDIO_BECOMING_NOISY` is registered at runtime without an export flag ([U] §3.3).

### 14.6 Battery notes (445 mAh watch)

- No official current figures exist for the trackers, `flush()`, or Data Layer messages ([A] §4.8; [S] §2.3). Drain is
  therefore measured, not assumed: %/h = I_avg (mA) / 445 mAh × 100 (arithmetic; e.g. 20 mA → 4.5 %/h).
- Levers, in order: `sdkFlushS` (1/5/30 s), `watchWakeLock` on/off, `telemetryFlushS` (1/5/30 s), screen: never
  `keepScreenOn` ("severely impacts battery life", [W] §2.3), near-black ambient layout.
- Wear OS 6 + targetSdk 36 is always-on (dimmed, still visible, [W] §2.3): draw dim HR/state text only, so a lit face
  does not disturb sleep.
- Measure passively: record the battery level and `adb -s $W shell dumpsys batterystats com.opentdi` (read-only)
  before and after a 30-minute screen-off run per setting and take the difference, plus `adb -s $W shell dumpsys
  sensorservice`. `dumpsys batterystats --reset` wipes the device's battery history, so it is used only with the
  owner's explicit approval for that run (CLAUDE.md §6).

### 14.7 Versions (D16; [W] §4)

`compileSdk 37`, `targetSdk 36` (both), `minSdk` wear 30, mobile 33. Wear Compose Material3 1.7.1 (needs compileSdk ≥ 37,
AGP ≥ 9.1.0), `play-services-wearable` 20.0.1, `kotlinx-coroutines-play-services` 1.11.0, `androidx.wear:wear-ongoing`
1.1.0, `androidx.wear:wear` 1.4.0 only if `wear-sdk` is missing on the watch (`AmbientLifecycleObserver` fallback). No
Horologist (the only stable line, 0.7.15, targets Wear Compose 1.5.0-rc01 and M2.5, [W] §3.3). No
`compose-navigation` (it pulls in M2.5).

---

## 15. Hardware verification checklist (manual + adb)

| # | Check | How | Pass / decides |
|---|---|---|---|
| 1 | Health Sensor Service version, developer mode | package already known: `com.samsung.android.service.health`, 1.7.00.05 on 2026-10-27 ([DA]); re-read after updates with `adb -s $W shell dumpsys package com.samsung.android.service.health \| grep -E "versionName\|versionCode"`; check the in-app `getPackageInfo` result matches; enable developer mode (§11.7); log `getTrackingCapability().getVersion()` | `CONNECTED_SAMSUNG`; version ≥ 1.6.5; record whether the label reads "Developer mode" or "[Dev mode]" |
| 2 | Supported trackers | log `getSupportHealthTrackerTypes()` once; save to `docs/references` | HR, EDA, accelerometer continuous present |
| 3 | Permissions actually required (owner 6) | install without `BODY_SENSORS` granted (it is `maxSdk 35`); `adb -s $W shell dumpsys package com.opentdi \| grep -iE "heart\|body_sensors\|ADDITIONAL\|ACTIVITY_RECOGNITION"`; run each `pm grant --user 0` of §14.2 (`READ_ADDITIONAL_HEALTH_DATA` is already known to be `dangerous`, [DA]) | trackers run with `READ_HEART_RATE` + `READ_ADDITIONAL_HEALTH_DATA` + `ACTIVITY_RECOGNITION` only; `BODY_SENSORS` not needed on API 36; `pm grant` works for each, or document the exception |
| 4 | Background health permission | 10+ min with the screen off | no `PERMISSION_ERROR`; otherwise declare `READ_HEALTH_DATA_IN_BACKGROUND` |
| 5 | Gravity in the accelerometer | watch flat and still: log raw `ACCELEROMETER_Z` | ≈ ±4096 → gravity included; ≈ 0 → javadoc right; decides `gravityMode` handling and limp availability |
| 6 | Accelerometer rate and batching | samples per second over 10 min, screen on / off / dimmed; callback batch sizes; timestamp jitter | 25 ± 1 Hz; ≥ 20 samples in ≥ 99 % of seconds |
| 7 | EDA with the screen off for 10+ min (owner 9) | log every `EdaSet` timestamp and status, with and without the 5 s `flush()` | no gaps > 2 s after re-ordering; status mostly 0; batch interval recorded |
| 8 | HR / IBI with the screen off | as #7; check IBIs only in the first point of a batch; compare `getTimestamp()` with `System.currentTimeMillis()` | packet latency ≤ flush + 2 s with flush on; timestamp base = epoch |
| 9 | Does the dimmed always-on screen count as "display off" for batching? | compare #7 with the screen dimmed vs off | recorded |
| 10 | Accel noise floor and jerk/limp parameters | 10 min still lying; 10 deliberate small jerks; slow wrist tilts; 3 real naps; all with `accel.logRawAccel = true` | p95 of `accelRms` (sets f, M); J_G, drift° tuned through the AccelConfig replay of §12.6 on `w_accel_raw.csv`; check that real jerks leave ≤ 2 moving seconds (§8.1 freeze) |
| 11 | Battery | §14.6 grid on 30-min screen-off runs | settings chosen; budget documented |
| 12 | Doze and timers | passive: a 30–60 min screen-off run with the device still, reading `adb -s <serial> shell dumpsys deviceidle` (state) and `dumpsys power \| grep -i wake` (read-only); a 180 s countdown with the screen off. `dumpsys deviceidle force-idle` only with the owner's explicit approval for that run | wake locks held; countdown fires within ±1 s; Doze state reached recorded |
| 13 | Phone mic for a whole nap | 30–60 min screen off; `adb -s $P shell dumpsys activity services com.opentdi \| grep -i allowWiu`; then a recording | `mAllowWiu_noBinding` not DENIED; recording RMS above the floor |
| 14 | Keep-alive earbud test (owner 19) | §7.5 procedure per earbud | per-earbud carrier default recorded |
| 15 | Disconnect mid-nap | power off the earbuds mid-carrier and mid-cue | emergency stop before any speaker output; which callback fires first |
| 16 | Recording during A2DP playback | listen for the HFP beep; codec in Developer options; routed device | no beep; codec unchanged; `TYPE_BUILTIN_MIC`; `isClientSilenced` false |
| 17 | Mic sources | `PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED`; bedroom noise floor (dBFS) for `UNPROCESSED`, `MIC`, `VOICE_RECOGNITION` | feeds O7 and `marginDb` |
| 18 | On-device STT | `isOnDeviceRecognitionAvailable`; `checkRecognitionSupport` lists for `en-US`; segmented `EXTRA_AUDIO_SOURCE` pipe; faster-than-real-time feed; `getOnlineLanguages()` empty | transcripts produced offline, or status `UNAVAILABLE_*` documented |
| 19 | TTS output format | log `onBeginSynthesis` rate/encoding for each installed engine | reader handles it |
| 20 | One UI DND | default of "Media sound"; does a `USAGE_MEDIA` cue play in Priority mode | setup docs updated |
| 21 | Bezel | `adb -s $W shell pm list features \| grep -i rotary` (expect `android.hardware.rotaryencoder.lowres`); `pm list libraries \| grep -i wear-sdk`; log `verticalScrollPixels` per detent and its sign; Picker haptics | Picker works and ticks; ambient API choice |
| 22 | Bluetooth device names | does reading `AudioDeviceInfo` names/addresses need `BLUETOOTH_CONNECT` | declare it only if needed |
| 23 | Data Layer | messages flow between the two debug builds (same `applicationId` + keystore); a `state_command` request with the FGS running and with it stopped; reconnect time after Bluetooth off/on mid-session (done by the owner on the device, never over adb, CLAUDE.md §6) | link works; with the FGS running the `Ack` comes from the FGS handler, without it `WATCH_SERVICE_NOT_RUNNING` (§10.2); the manifest service is not bound for telemetry; reconnect time recorded (docs: up to 4 min) |
| 24 | One UI battery managers | 60 min nap with default battery settings; check both FGS survive | if killed: setup docs ask for "Unrestricted" battery |
| 25 | Watch face and screen | Ongoing Activity icon visible; dimmed session screen brightness in a dark room | acceptable to the owner |
| 26 | Split permission flag | `adb -s $W shell dumpsys package com.opentdi \| grep -i -E "heart\|body_sensors"` after install | `replace_body_sensor_permission_enabled` behaviour confirmed |

---

## 16. Unit test plan (`:shared`, JUnit 4.13.2)

Inputs are synthetic packet lists built from the generator of §13 or small hand-made sequences. "t" is packet seconds.

| Suite | Test | Input | Expected |
|---|---|---|---|
| `AccelProcessorTest` | `countsToG_4096CountsIsOneG` | raw 4095.9375 | 1.0 g |
| | `stillness_mapping` | accelRms 0.003 / 0.0265 / 0.05 g | 1.0 / 0.5 / 0.0 |
| | `fewerThan20Samples_nullFields` | 19 samples in a second | accel fields and stillness null |
| | `gravityMode_detected` | 30 still s at \|a\| = 1 g; then at \|a\| = 0.02 g | `WITH_GRAVITY`; `LINEAR_ONLY` |
| | `jerk_300msAfter10sStill_confirmedInThirdSecond` | raw `a`: 10 still s at (0, 0, 1 g), 8 samples of +0.5 g on x inside one second, 3 still s | `jerkEvent` only in the 3rd post-burst second; ≤ 2 seconds with stillness < 0.5; `majorMovement` never true |
| | `jerk_unidirectionalStraddling_confirmed` | raw `a`: as above, burst across a second boundary | confirmed; ≤ 2 moving seconds; no `majorMovement` |
| | `jerk_zeroMean_confirmed` | raw `a`: 8 samples alternating ±0.5 g on x | confirmed; ≤ 2 moving seconds |
| | `gravityFreeze_releasedAfter500ms` | raw `a`: 30 samples (1.2 s) of a sustained 0.5 g offset | `JERK_REJECTED_LONG`; g_lp tracks the new offset again from 500 ms on |
| | `jerk_700msBurst_rejected` | 18 samples above 0.3 g | no jerk; `JERK_REJECTED_LONG` |
| | `jerk_followedByMovement_rejected` | burst, then a moving second | `JERK_REJECTED_POST_MOVE` |
| | `jerk_withoutPreStill_ignored` | burst 5 s after movement | no burst opened |
| | `limp_driftAfterStillRef_emits` | ref over 10 still s, then 3.5° tilt over 30 s | one `limpEvent` |
| | `limp_linearOnly_never` / `limp_movementResetsRef` | — | no events / reference cleared |
| `PacketAggregatorTest` | `hr_invalidStatus_holds5s_thenNull` | status 1 at t = 0, then status −8 | hr held t = 1–5, null at t = 6 |
| | `ibi_batchInFirstPoint_timedBackwards` | batch of 5 points, 6 IBIs in the first | IBIs binned by back-computed time |
| | `ibi_rejects20PercentOutlier` | 1000 × 5, then 1300 | 1300 rejected; no diff across it |
| | `rmssd_knownValue` | 25 IBIs alternating 1000/1020 | RMSSD = 20.0 ms |
| | `rmssd_nullUnder20Pairs` | 15 IBIs | null |
| | `eda_detachedStatus_nullNoHold` | status −5 | null that second |
| | `emission_lateTracker_nullAfterDeadline` | EDA silent | packet emitted 10 s late with EDA null |
| `BaselineTest` | `baseline_syntheticFast` | §13 k = 0–179 | μ_HR 70.0078 (±1e-4), σ_HR 0.6403, μ_R 40.0067, σ_E floored 0.005, n 180 |
| | `baseline_motionFramesDiscarded` | 30 frames at stillness 0.3 | 150 used, `framesMotionDiscarded` 30 |
| | `baseline_insufficientHr_extendsThenIdle` | hr null in 60 % of frames | `BaselineExtended` at W, `IDLE` + `BASELINE_INSUFFICIENT_HR` at 2W |
| | `baseline_priorBlend` | live 72, prior 64, w 0.3 | μ_eff = 69.6 |
| | `baseline_priorIgnoredBeyond15Percent` | live 72, prior 55 | μ_eff = 72, `PRIOR_IGNORED` |
| | `baseline_priorHigherIgnored` | live 70, prior 80 | μ_eff = 70, `PRIOR_IGNORED_HIGHER` |
| | `baseline_noRmssd_disablesRmssdOnly` | rmssd null | `rmssdMean` null; HR rule still works |
| `EngineRuleTest` | `edaAlone_neverTriggers` | G true, A false, E true, 3600 s | no onset |
| | `jerkAndLimpAlone_neverTrigger` | jerk + limp every 60 s, A false | no onset |
| | `stillnessAlone_neverTriggers` / `hrDropWithoutStillness_neverTriggers` | — | no onset |
| | `core_noBonus_triggersAtT0Plus19` | core true from t₀, no bonus | onset at t₀ + 19 |
| | `core_withBonus_triggersAtT0Plus9` | core from t₀, jerk 5 s earlier | onset at t₀ + 9 |
| | `coreBreak_resetsHold` / `packetGap_resetsHold` | one false second / 2 s gap | hold restarts |
| | `refractory_blocks120s` | core true right after `CYCLE_COMPLETE` | no onset before T + 120; then after the hold |
| | `majorMovementInCountdown_aborts` | 3 moving s at onset + 47 s | `CountdownAborted(MAJOR_MOVEMENT)`, `AWAKE_INCUBATING` |
| | `jerkInCountdown_doesNotAbort` | 2 moving s | stays `HYPNAGOGIA_DETECTED` |
| | `missedCycle_afterCountdownPlus15` | no `WAKING_USER` | `AWAKE_INCUBATING` (`MISSED_CYCLE`) at onset + 195 s (countdown 180); refractory set |
| | `triggerRejected_returnsAwake` | `onTriggerRejected` | `PHONE_REJECTED` |
| | `lostWakingUser_acceptsLaterStage` | `RECORDING_DREAM` first | moves to `RECORDING_DREAM` |
| | `stageTimeout180_completesCycle` | `WAKING_USER`, then silence | `CycleCompleted(STAGE_TIMEOUT)` at +180 s |
| | `lateCycleCompleteAfterTimeout_ignored` | stage timeout, then `CYCLE_COMPLETE` for that cycle | `completedCycles` incremented once; `LATE_STAGE_IGNORED` |
| | `timedMode_mirrorsOnly` | `startSession(id, TIMED_CUES)`, packets that would satisfy the core, then `DWELL_START` … `CYCLE_COMPLETE` | no `OnsetDetected`; states follow the stages |
| | `abortFromIdleAfterFallback` | `HR_LOST` → `IDLE`, then `abort(USER)` | `SESSION_COMPLETE` |
| | `windowsFromConfig_minCounts` | `hrMaWindowS` 5, `stillWindowS` 10, `edaSlopeWindowS` 15 | H_sig, G and E can become true |
| | `abortedAudio_notCounted` | `CYCLE_COMPLETE(ABORTED_AUDIO)` | `completedCycles` unchanged |
| | `maxCycles_sessionComplete` | 4 completed cycles | `SESSION_COMPLETE(MAX_CYCLES)` |
| | `pause_keepsBaseline_resumeAwakeWithRefractory` / `pauseInCalibration_restarts` | — | as rows 19–20 |
| | `abort_fromEveryState` | each state | `SESSION_COMPLETE` |
| | `hrLost300_idle` / `accelLost60_idle` | — | `IDLE` with the reason |
| | `androidSensors_triggersOnHrAndGate` | rmssd and EDA null | onset after `holdS` (20 s) |
| | `confidence_components` / `confidence_atTrigger_ge085` | fixed S, A, P, B | formula value; every onset ≥ 0.85 |
| | `presets_consistency` | §8.14 rules | all hold |
| | `deterministic` | the same input twice | identical outputs |
| `TimedCueSchedulerTest` | `timed_latencyInclusive10to15` | 10 000 seeds | values ⊂ {10…15}, all six occur |
| | `timed_defaultTimeline` | seed giving L = 12 | dwell cue 720 s, wake 900 s, … cycle 4 end, `SESSION_END` |
| | `timed_fallbackMidSession` | fallback at a given time | next dwell at max(now + 60 s, anchor + interval) |
| `SimulationTest` | `fast_std_cycle1` | §13.2 | first core 337, onset 346, HR_ma 63.633, confidence 1.00 |
| | `fast_std_cycle2` | `REARM` | first core T + 193, onset T + 202 |
| | `fast_low_high` | presets | LOW 363 / T + 217; HIGH 335 / T + 192 |
| | `fast_noOnsetBefore300` | k < 300 | no onset; G false exactly at k = 242–270 |
| | `fast_sessionCompleteAfter2` | `maxCycles` 2 | `SESSION_COMPLETE(MAX_CYCLES)` |
| | `awakeControl_noOnset_1h_allPresetsAndMinOverrides` | `AWAKE_CONTROL`, θ_drop 0.04, θ_rmssd 0.05 | no onset in 3600 s |
| | `awakeControl_withPrior_noOnset` | `AWAKE_CONTROL`, prior 80.5 (higher) and 64 (lower), w 0.5 | no onset in 3600 s |
| | `simReceiver_pinsDetectionConfig` | saved config with `baselineWindowS` 600, a prior HR, `holdS` 30 | effective config = §13.1 pinned config |
| | `noise_period11_sumZero` | n_c | 11-sums 0; 4-sums within ±2.8 |
| `EndOfReportDetectorTest` | noise only / speech 5 s then silence / speech with a 3 s pause / continuous speech | frame dB sequences | `NO_SPEECH` at 15 000 ms / `SILENCE` 4000 ms after the last speech frame / continues / `MAX` at 45 000 ms; seed frames never count as speech |
| `FadeEnvelopeTest` | gain curve | — | G(0) = 0 exactly; 0.00562 / 0.0316 / 0.178 / 1.0 at 500 / 1000 / 1500 / 2000 ms (±1e-4); monotonic; T = 0 → 1 |
| `CarrierTest` | level and determinism | −75, −60 dBFS; same seed twice | RMS within 0.5 % of 10^(L/20); identical buffers |
| `SleepStateParserTest` | keywords | "halfway", "I was not asleep", "asleep", "" | HALFWAY, AWAKE, ASLEEP, UNKNOWN |
| `SerializationTest` | round trips | every contract; unknown field; each `TdiCommand` | equal; ignored; discriminators `start`, `pause`, `resume`, `abort`, `config`, `abort_countdown`, `cycle_stage` |
| `ConfigValidationTest` | ranges and cross-rules | out-of-range values; every window value with its minimum count; `maxMs` 180 000 with `cycleStageTimeoutS` 180; `userGain` 0.5 on Bluetooth (cap 0.3) | rejected with field names; min count ≤ window everywhere; timeout config rejected; `userGain` accepted with `USER_GAIN_CLAMPED` and played at 0.3 |
| `WavIoTest` | PCM16 round trip; odd-sized `LIST` chunk; header with size 0 | — | samples equal; chunk skipped; read to EOF |
| `ReplayHarnessTest` | `replay_syntheticFast_reproducesGolden` | generated fixture | transitions equal the golden file |
| | `replay_inputsAppliedAfterPacket` | input after packet X | applied before packet X + 1 |
| | `replay_floatRoundTrip_bitExact` | random floats through CSV | identical bits |
| | `replay_realNap_<date>` (added per recording) | recorded nap | golden transitions unchanged unless thresholds were changed on purpose |

---

## 17. Open decisions, risks, deviations, README statement

### 17.1 Deviations from and extensions to D1–D18 (also in the final summary)

| # | Where | Kind | What and why |
|---|---|---|---|
| 1 | §7.1, §7.8 | EXT | `SLEEP_STATE_QUESTION` ("And were you asleep?") + `SleepStateAnswer`: it is part of the P20 sequence and the only ground truth for tuning |
| 2 | §9.4 | EXT | `CycleStage.outcome` (`ABORTED_AUDIO`) so a cycle whose wake prompt could not play is not counted |
| 3 | §9.3, §10.2 | EXT | `TelemetryFrame.stateReason`, `seq`; `TelemetryBatch.sensorStatus`, `gravityMode`; batches that contain a state change go by `sendRequest` with retries (still MessageClient). Fallback reasons travel this way, so no new command is needed |
| 4 | §10.2 | EXT | `/opentdi/session_files` (ChannelClient `sendFile`) to copy watch CSVs to the phone at session end |
| 5 | §9 | EXT | `SessionConfig.revision` for ConfigSync conflicts; `PROTOCOL_VERSION`; `Ack` reply type |
| 6 | §13.1 | EXT | FAST time compression only in `CALIBRATING_BASELINE`/`AWAKE_INCUBATING`, so D4's packet-time missed-cycle deadline matches the phone's real-time countdown |
| 7 | §13.1 | EXT | `AWAKE_CONTROL` also compressed ×10 (D11 states compression only for FAST) |
| 8 | §13.1, §13.6 | EXT | Simulation receivers in debug builds only; duplicate suppression; requires the watch FGS to be running; debug-only phone `ACTION_ARM` service entry for scripted runs |
| 9 | §10.3 | DEV (minor) | Bezel uses Wear Material3 `Picker` (built-in rotary snap and haptics, which consumes the same `RotaryScrollEvent`) instead of a hand-written `onRotaryScrollEvent` handler (per-detent pixels are undocumented) |
| 10 | §8.11 | DEV (label only) | `MIT_STANDARD` keeps its enum name (D10) but is labelled "Standard" in the UI: no value in it is MIT's (owner 10) |
| 11 | §5.3, §6 | EXT | Baseline extension once (2W); `HR_LOST` / `ACCEL_LOST` → `IDLE` and phone fallback; cycle-stage timeout 180 s; `sessionMaxMin` 60 (enforced by the phone, and by the watch as a safety net) |
| 12 | §4.1 | DEV | TIMED_CUES in Phase 2+ uses the DETECTION recording stop rules rather than FS24's fixed 60 s, so both modes are comparable |
| 13 | §4.1 | EXT | `maxCycles` default 4 (FS24); the TIMED latency RNG is seeded by the session ID |
| 14 | §0 | Phase | `BLUETOOTH_SLEEP_AUDIO` and the carrier are Phase 3 (owner G20 "extra audio routes"), although D12 lists Bluetooth as in scope. Phase 1's phone FGS is `mediaPlayback` only; `microphone` arrives with recording in Phase 2 |
| 15 | §11.4 | Owner conflict resolved | Heart rate needs `READ_HEART_RATE`, not `ACTIVITY_RECOGNITION` (owner B6); all three permissions are requested |
| 16 | §11.6 | DEV (wording) | The `SDK_POLICY_ERROR` message points to developer mode via the setup guide instead of listing Samsung's steps on the watch |
| 17 | §3, §6, §8, §7.5 | DEV from CLAUDE.md, and from D12's "pink-noise" wording | Stillness window 30 s (not 60 s); packet `hr` is `Float`; the carrier is one-pole low-passed noise (−6 dB/oct above ≈ 392 Hz, i.e. brown-ish), not pink (1/f, −3 dB/oct); only its level matters for the keep-alive ([U] §4.5); gate tolerates 2 moving seconds so a jerk does not break it |
| 18 | §7.8 | follows D12, flagged | Recording source `UNPROCESSED` else `MIC` as D12 says, although research prefers `VOICE_RECOGNITION` over `MIC` (O7) |
| 19 | §10.4 A | clarification | The watch FGS must be started from the watch UI (the user opens the watch app); a phone message cannot start it in the background |
| 20 | §4, §5.3 T1, §9.1, §9.6 | EXT | `CycleStageType.DWELL_START` (phone → watch, TIMED_CUES only) and `startSession(sessionId, mode)`, so a watch in a TIMED_CUES session can mirror the dwell start; D3 lists only four stages |
| 21 | §13.1 | EXT | The simulation receiver pins the detection config to defaults (plus an optional `preset` extra, D11 lists only `mode`), so the §13.4 proof and the HIL pass criteria hold whatever the user saved |
| 22 | §8.1 | EXT | Gravity low-pass frozen during a jerk burst, so a ≤ 500 ms jerk leaves ≤ 2 moving seconds |
| 23 | §12.2, §12.6 | EXT | Optional `w_accel_raw.csv` (raw 25 Hz) and an AccelConfig replay mode; on by default since 2026-10-27 (owner), the watch toggle still turns it off. Raw accel is logged locally only, never sent over the wire (D6) |
| 24 | §10.2, §14.1 | clarification | The manifest `WearableListenerService` takes only `/opentdi/state_command` requests and forwards them to the running FGS; telemetry and triggers go to live listeners only |
| 25 | §0, §10.2, §9.5 | Phase (owner, 2026-10-27) | Routine telemetry (display only) is pulled forward from Phase 2: a watch logging session streams `TelemetryBatch`es every `telemetryFlushS`, now 2 s instead of 5 s, and the visible phone screen shows them live with 3-minute sparklines (1 Hz redraws, not the deferred 60 fps charts) |
| 26 | §7.1, §7.3, §7.4, §9.5 | EXT (owner, 2026-10-27) | Own words per cue (`audio.cueTexts`, `[THEME]` for the theme), a slower default speaking speed (`ttsSpeechRate` 0.75) and phrase-by-phrase rendering with `phrasePauseMs` pauses, so cues are slow and clearly separated; the speech enters ¾ of the way into the fade (at −15 dB) so the first phrase is heard |

**Loud flags: decisions that research shows cannot work as literally written**
- **D11 "registered in the manifest … so the exact command without `-p` works":** the manifest receiver can never get
  the command without `-p` (FACT, [A] §3.1). Only the dynamic receiver gets it, and only while the watch process runs.
  This spec implements exactly that.
- **CLAUDE.md §4 magnitude-of-gravity stillness and owner C7 "limp-wrist":** if the SM-L500's Samsung accelerometer
  stream really excludes gravity (javadoc, [S] §0 item 6), orientation-based limp detection is **impossible** on the
  SDK path. The design keeps stillness and jerk (they need no gravity) and turns limp off automatically
  (`LINEAR_ONLY`); detection does not depend on it (bonus only). Check §15 #5 first.
- **Owner C9 "EDA keeps streaming with the display off":** it streams but arrives **batched** (Samsung's HR example is
  600 samples per 10 minutes). Real-time use needs the periodic `flush()` and a CPU-awake timer, at a battery cost
  nobody has measured ([S] §2.3).

### 17.2 Open decisions (owner or lead engineer)

| ID | Decision | Options / recommendation |
|---|---|---|

| O2 | Enum name `MIT_STANDARD` | Keep (D10) with the "Standard" label, or rename to `STANDARD` before Phase 2 freezes the wire format |
| O3 | `SDK_POLICY_ERROR` text | Samsung: "Do not share the developer mode guide with app users" vs owner A2. Current text names developer mode and points to the developer guide |
| O4 | Fixed vs random countdown | P20's lab protocol drew 1–5 min at random per loop ([P] §1.2 #5); the app presets were fixed (1 / 1.5 / 3 min). Current: fixed 180 s; a "random 60–300 s per cycle" option is cheap to add |
| O5 | Cue at the detected onset | Dormio web wearable plays the incubation prompt at onset ([C] §4); P20 does not say. Current: no cue |
| O6 | Fade-in vs intelligibility | With −60 → 0 dB over 2 s, speech from the first sample spends ≈ 1 s below −30 dB. Current (2026-10-27): ¾ T of silence under the fade, so the speech enters at −15 dB (§7.3). Other options: speech from the first sample; shorten to ~1 s; or prepend a soft chime under the fade |
| O7 | Recording source | D12 `UNPROCESSED` else `MIC`; research recommends `VOICE_RECOGNITION` as the fallback (no AGC/NR by CDD) ([U] §5.1). Decide after §15 #17 |
| O8 | Arm-raised limp task | Closest analogue of Dormio's hand task ([D] §10.6); changes the user protocol |
| O9 | Real nap data as test fixtures | Packets only, with the person's written consent; or keep them out of the public repo |
| O10 | Sensor detection on every cycle | OpenTDI detects every cycle (as P20's lab loop did). The MIT web and iOS code detect only the first onset and time the rest ([C] §0 item 4). A "detect first, then timer" option is possible |
| O11 | `watchWakeLock` default | true until §15 #11 shows timely delivery without it |
| O12 | `sessionMaxMin` default | 60 (covers 4 TIMED cycles ≈ 52 min) vs P20's 45 |

### 17.3 Risks

| Risk | Impact | Mitigation |
|---|---|---|
| Every detection threshold is unvalidated (no OpenTDI data; MIT's were n = 5 pilots, provisional) | false or missed onsets | presets, confidence logging, sleep-state labels, replay tuning (§12.6) |
| Samsung accelerometer may exclude gravity | no limp bonus | `gravityMode` auto-detect; limp is bonus only |
| Screen-off batching of HR/EDA/accel | detection latency, battery | 5 s `flush()` under a wake lock; measure (§15 #6–9, #11) |
| Which of `READ_HEART_RATE` / `READ_ADDITIONAL_HEALTH_DATA` / `ACTIVITY_RECOGNITION` / `BODY_SENSORS` the trackers really check on API 36 (the protection levels are known: all `dangerous`, [DA]) | trackers refuse data | request all three in the UI; §15 #3 |
| Health Sensor Service is updated or renamed on a later watch build (today `com.samsung.android.service.health` 1.7.00.05, [DA]) | version check may fail | `NameNotFoundException` → fall back to the connection error codes (`PACKAGE_NOT_INSTALLED`, `OLD_PLATFORM_VERSION`); re-check §15 #1 after updates |
| Samsung on-device STT support for `EXTRA_AUDIO_SOURCE` unknown | no transcripts | WAV is primary; status `UNAVAILABLE_*`; retry from the journal |
| One UI DND default for media unknown | silent cues | checks before each cue; setup docs |
| Headset-side standby despite the carrier | clipped first syllable or pop | manual earbud test; longer fade; per-earbud defaults |
| Data Layer reconnect up to 4 min | missed cycles | watch-side logging; missed-cycle rule; timed fallback |
| One UI battery managers kill an FGS | session lost | §15 #24; ApplicationExitInfo check |
| Integer HR quantisation (1 bpm ≈ 1.4 % at 70 bpm) | jitter near the threshold | 15 s moving average; hold |
| Always-on dimmed watch screen in a dark room | sleep disturbance | near-black ambient layout; §15 #25 |
| Dorsal-wrist EDA differs from Dormio's ventral wrist | EDA bonus may be noise | bonus only; relative to the baseline |


### 17.4 README statement (ready to paste)

> **Not a medical device.** OpenTDI is open-source wellness and research software for exploring targeted dream
> incubation at sleep onset. It does not diagnose, treat, cure, mitigate or prevent any disease or condition, and it is
> not a sleep-staging or medical device. Its sleep-onset estimates come from unvalidated thresholds and may be wrong.
> Heart rate, inter-beat interval, skin conductance and motion data from the Samsung Health Sensor SDK are "for fitness
> and wellness information only, not for the diagnosis or treatment of any medical condition" (Samsung). Do not use
> OpenTDI if you have a history of psychotic disorders, narcolepsy, recurrent sleep paralysis, parasomnias or other
> sleep disorders without talking to a clinician first. This project is not affiliated with or endorsed by the MIT Media
> Lab or Samsung.
