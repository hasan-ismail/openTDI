# Projects similar to OpenTDI: open-source builds, research platforms and commercial devices

Research extract for OpenTDI, made 2026-10-27. Scope: find and compare projects that do what OpenTDI does
(detect sleep onset or a sleep stage from body signals, then play a cue and/or collect a report), open
source first, then research platforms and commercial devices. MIT's own Dormio papers and code are already
covered in `../horowitz2020_dormio_paper.md`, `../opensleep_dormio_code_analysis.md`,
`../dormio_duo_wearable_analysis.md` and `mit_tdi_lineage.md`. Lacaux 2021 and sleep-onset physiology are
covered in depth in `sleep_onset_physiology.md`. Those are only cross-referenced here.

**Labels.** **FACT** = stated in the source I actually read (code line, README, paper text), cited.
**INFERENCE** = my reasoning. **VENDOR CLAIM** = stated by the maker, not independently checked.
**PRESS** = secondary journalism only. **NOT VERIFIED** = I could not read the primary source.
"Of the repos found" means "of the repositories my searches returned", not "of everything that exists".

**Code paths.** For repositories I shallow-cloned (read-only, into a scratch folder), `file:N` is a line
number at the commit given in §1.2. "Stars" and "last commit" were read from the GitHub API on 2026-10-27;
"last commit" is the committer date of the default-branch HEAD.

---

## 0. Key findings (read this first)

1. **No open project found does what OpenTDI plans: wrist PPG + IBI + wrist EDA + accelerometer on a
   consumer watch, real-time N1 detection, and a full serial-awakening loop with transcription.**
   - FACT: GitHub code search for `HEART_RATE_CONTINUOUS EDA_CONTINUOUS` returned zero results (§1.1).
   - FACT: the closest builds each cover part of it. Amulet XPL is a full Dormio-style loop on a finger
     glove plus iPad ([AMU] README). DynamicNapAlarm detects onset on a Galaxy Watch but only starts a
     nap timer ([DNA] README). nap-time-ios detects onset from ring IBIs ([NAP]). home-tmr/SleepStim is
     the closest *architecture*: Fitbit watch plus Android phone, sensor-gated audio cues, with a
     published trial ([HTMR], [WHIT22]).
2. **The best new open-source TDI reference is `erikleanderberg/amulet-xpl` (MIT licence, Oct 2026).** It
   ports the Dormio rule (|ΔHR| > 5 bpm OR |Δflex| > 8 kΩ from a 120 s mean) and adds an **8 s hold**, a
   **60 s refractory period**, a **baseline re-taken in every report window**, a **fixed 1 min
   onset-to-wake window**, and the "were you asleep?" label (FACT, [AMU] `sleep-onset/train.py:37-46`,
   `docs/01-system.md:19-26, 57-86`).
   - It reports one overnight failure caused by a *fixed* 120 s baseline: "98 cues, 92 of them fired at
     the refractory floor, one per minute until the battery died" (FACT, author's anecdote,
     `docs/01-system.md:84-86`).
   - It has **no real-data validation**; its scores are on synthetic sessions only (FACT, [AMU]
     `sleep-onset/README.md`, "About the data").
3. **Onset rules in the wild vary widely, and none is validated against PSG.** Baselines run from 30 s to
   5 min. Thresholds are relative deltas (Dormio, Amulet), percentages (idealemu 3.5 %, nap-time 5 %),
   absolute BPM (SteelBall 70, DreamHacker 77), or z-scores (goofi θ/α z ≥ 2). Holds run from 0 s to
   4 min. Table 4.1 lines them up.
4. **The strongest *field* evidence for sensor-less home cueing comes from Northwestern, not MIT.**
   - Konkoly et al. 2024 ran a phone-only Android TLR app at home: lucid dreams went from 0.74 to 2.11
     per week (n = 19, p = .007). Its cues were gated only by the phone's accelerometer and a timer
     (FACT, [KON24] §2.1.2, §2.2.2).
   - It is also the most detailed published recipe for *non-waking* home audio, which CLAUDE.md §5 needs:
     start at a perceptual threshold measured in the app, step intensity up per cue, pause after
     movement, cap at the level that was incorporated into a dream (FACT, [KON24] §2.1.2, §3.1.2).
5. **Wearable-gated cueing helps, but it is imprecise.**
   - SleepStim (Fitbit HR + motion) put 34.7 % of its cues in N3, which made up 21.1 % of sleep. About 16 %
     of its cues landed in wake (FACT, [WHIT22] Results, Discussion).
   - The open Apple Watch TLR stager has REM precision 34 % and recall 57 % (FACT, [MAL24] Fig. 1).
   - Even an EEG headband scores N1 worst: Muse-S N1 κ = 0.41, against 0.85 for REM (FACT, [LAN25]
     Table 2).
   - INFERENCE: a wrist-only N1 detector should be expected to do worse than these EEG figures.
6. **Cueing during sleep adds less than expected over pre-sleep training.** The preregistered multi-centre
   TLR study found signal-verified lucid dreams in 38.3 % of stimulation naps versus 28.3 % of sham naps,
   with no significant difference (FACT, [PD26] abstract, v2). Bellaiche 2024 (Dormio Light) had already
   shown TDI works with timers alone (see `../horowitz2020_dormio_paper.md` §3).
   - INFERENCE: OpenTDI's sensor mode should be judged against its own `TIMED_CUES` mode as the control
     arm. Morpheus's design document argues the same for TLR ([MOR] `docs/design.md:11-25`).
7. **Cue intensity is the main harm and confound in every home study.**
   - SleepStim: low-intensity cues improved memory and high-intensity cues did not (FACT, [WHIT22]
     abstract).
   - Konkoly 2024: "participants were often awoken by cues" (FACT, [KON24] Discussion, limitations).
   - Multi-centre TLR: cues disrupted sleep in 23.7 % of cued REM periods (FACT, [PD26]).
   - home-tmr's Android app sets `STREAM_MUSIC` to its maximum on start and on resume, and swallows the
     hardware volume keys. It controls loudness only through in-app player gains (FACT, [HTMR]
     `MainActivity.java:225-232, 633, 657-675`). INFERENCE: one wrong gain, or any other app's sound, then
     plays at full system volume. OpenTDI must not copy this startle risk.
8. **Commercial lucid-dream devices are mostly unvalidated.** A 2019 review of ten devices (DreamLight,
   NovaDreamer, Aurora, Remee, REM-Dreamer, ZMax, Neuroon, iBand, LucidCatcher, Aladdin) found that "only
   DreamLight has been empirically tested with published results" (FACT, [MR19]).
   - Of the EEG consumer devices, Dreem, Muse-S and Elemind have peer-reviewed validation or trials
     ([ARN20], [LAN25], [BRE24]). None of them targets hypnagogia.
9. **Sleep as Android is closed source and uses actigraphy for its phases.** Its docs say phases are
   "based purely on the average short-term amount of physical activity". REM is folded into "light sleep",
   and "any attempt to pinpoint the exact beginning of a sleep phase is subject to a high degree of error"
   (FACT, [SAA-T]).
   - Its open Garmin add-on shows the data it ships: accelerometer at 10 Hz, reduced to the maximum
     magnitude per ~10 s window, plus HR (FACT, [SAA-G] `SensorManager.mc`).
10. **Licensing under GPL-3.0 (the owner's choice).**
    - FACT: these projects are MIT and so GPL-compatible: amulet-xpl, home-tmr, rmallela26/TLR,
      remind-eeg, Dreamento, goofi-pipe, flicker and the lucid-trainer apps. SMACC, zzz, DreamHacker and
      lucidsourcekit are already GPL-3.0.
    - FACT: these have no licence (default copyright): DynamicNapAlarm, nap-time-ios, SteelBall,
      idealemu/TDI, the openSleep and dormio repos, the LucidScribe plugins and Halovision-INSPEC.
      Morpheus is "All Rights Reserved".
    - Details in §5.

---

## 1. Method

### 1.1 Searches run (GitHub, via `gh search repos` / `gh search code`, 2026-10-27)

| Query | Useful hits |
|---|---|
| repos: `dormio` | MIT repos (known), ThingEngineer (known); 25+ unrelated name collisions |
| repos: `dream incubation`, `targeted dream incubation` | `idealemu/TDI`; `albertooishii/dream_incubation` (Flutter, not read); `jhaaswithap/paila-equation-tdi-streamlitapp` (no data, §3.4) |
| repos: `hypnagogia`, `hypnagogic` | **`erikleanderberg/amulet-xpl`**, `jabituyaben/SteelBall`, `wnmurphy/vigilance_device`, `KairosHive/hypnagogia`, `sowbug/hypnagogo`, `DavidLamparter/HypnagogiApp` |
| repos: `sleep onset`, `sleep onset detection`, `nap alarm`, `power nap` | **`andreykats/nap-time-ios`**, **`Ermond16/DynamicNapAlarm`**, `wyatt-js/whoop-sleep-onset` (not read), `g3149482122-web/CycleWake-AI` (README only, no code) |
| repos: `lucid dream`, topic `lucid-dreaming`, `lucid dreaming device/app/mask`, `lucid scribe` | `lucidcode/*`, `BitFlaker/lucidsourcekit`, `Absolute0K/OpenLD-Software`, `remrama/flicker`, `sourcetortoise/zzz`, `DashBarkHuss/dreamphone`, `IAmCoder/awesome-lucid-dreams` |
| repos: `targeted lucidity reactivation`; code: same | `rmallela26/TLR`, `matthewrennie/tlr`, `yuriy-mikityuk/remind-eeg`, `remrama/smacc`, `adamn23/Morpheus` |
| repos: `dream engineering`, `dreamento`, `hypnodyne`, `zmax` | `dreamento/dreamento`, `lucidcode/LucidScribe-Hypnodyne-ZMax` |
| repos: `sleep as android`, `samsung health sensor`, `galaxy watch sleep` | `urbandroid-team/*` add-ons; three Samsung SDK apps (not sleep-related) |
| code: `dream incubation` | also surfaced `dav0dea/goofi-pipe` (`dreaminceptor.py`) |
| from REMind's own landscape notes ([REMIND] `docs/compare.md`) | `nathanww/home-tmr` (SleepStim), plus EazzZyLearn, COsleep and Portiloop (NREM closed-loop, not read) |
| **zero results** | `lucid dream wear os`, `lucid dream wearable`, `lucid dream android`, `openbci sleep`, `openbci dream`, `edison nap`, `steel ball sleep`, `wear os sleep tracking`, `zmax hypnodyne`; code: `HEART_RATE_CONTINUOUS EDA_CONTINUOUS`, `sleep onset rmssd`, `hypnic jerk accelerometer` |

### 1.2 What was read

- **Code read** (shallow clones): amulet-xpl `081f199`; home-tmr (README, `MainActivity.java` and
  `fitbit/companion/index.js` via `git show`, because checkout failed on a long path); nap-time-ios;
  DynamicNapAlarm; SteelBall; idealemu/TDI; vigilance_device; DreamHacker; rmallela26/TLR (server
  `model.py`, `processing.py`, iOS `SoundPlayer`); matthewrennie/tlr; LucidScribe-Hypnodyne-ZMax.
  goofi-pipe `dreaminceptor.py` and Sleep-as-Android-Garmin-Addon `SensorManager.mc` were read via the
  GitHub contents API.
- **READMEs/docs only**: remind-eeg (README, `docs/compare.md`), SMACC (README, `docs/about.md`),
  Dreamento, Halovision-INSPEC, android-lucid-trainer, Morpheus (README, `docs/design.md`), flicker,
  OpenLD, dreamphone, lucidsourcekit, hypnagogo, zzz, KairosHive.
- **Papers read in full text** (Europe PMC XML or author PDF): [KON21], [KON24], [WHIT22], [LAC21],
  [BRE24], [ARN20], [LAN25], [MR19], [MAL24] (PsyArXiv preprint PDF).
- **Abstract only**: [PD26] (bioRxiv API, versions 1 and 2), [CARR05].
- **Not read; secondary description only**: [CARR23] (described by [KON24]); the IJoDR version of
  [MAL24], because the publisher page served a bot-check; Sleep as Android pages beyond §3.

---

## 2. Table 1: open-source projects

Grouped by how close each is to OpenTDI. Rows marked "→ lineage" are analysed in `mit_tdi_lineage.md`
§2.10 and §3.2 and are only summarised here.

### 2.1 Sleep-onset / hypnagogia detectors (the TDI family)

| Project | What it does | Sensors | Detection / trigger logic (from code) | Cue / audio | Data logging | Licence | Evidence / validation | Status |
|---|---|---|---|---|---|---|---|---|
| **[AMU] erikleanderberg/amulet-xpl** | Dormio-style serial awakening for an hour; a glove plus iPad. Second aim: an RF (BLE RSSI) corpus labelled with sleep state (README) | Finger flex/FSR strip (index), finger PPG (middle), XIAO nRF52840; 160 Hz LRA on the wrist (`docs/01-system.md:6-10`) | Baseline is the mean (and SD) of the first 120 s. Score = max(\|ΔHR\|/5 bpm, \|Δflex\|/8 kΩ), with HR as a 15 s mean and flex as a 10 s mean, two-sided by default. Fires at score ≥ 1 held **8 s**, 4 s score smoothing (`train.py:37-46`). **60 s refractory** (`01-system.md:25`). **Baseline re-taken during each report window** (`01-system.md:77-86`). Optional LF/HF fall over 120 s, off by default (`train.py:47-52`). Logistic-regression alternative (`train.py:58-75`) | **Fixed 1 min** onset→wake window (Lacaux rationale). Wake = escalating ~5 s haptic pulse train, then the spoken prompt. Prompts are **pre-rendered WAVs, never live TTS**. "Were you asleep?" is answered aloud; 30 s settle before re-arming (`01-system.md:57-95`; `03-app.md:30-39`) | One file per session: flex, PPG, RSSI 50 Hz, phase and labels on one clock; saved every 2 s; exported as zip; glove keeps the session clock (`03-app.md`; README) | **MIT** | Synthetic sessions only (`sleep-onset/README.md`). `thresholds.py` re-derives the P20 §3.3 table from real captures, but no real data is published | 0 stars; created 2026-09-24; last commit 2026-10-11 |
| **[DNA] Ermond16/DynamicNapAlarm** | Galaxy Watch 7 standalone Wear OS app: waits for sleep onset, *then* starts a 10–60 min nap timer (README) | Platform `Sensor.TYPE_ACCELEROMETER` (~5 Hz per README) and `TYPE_HEART_RATE` (`NapService.kt:72-73, 121-124`); not the Samsung SDK | Still = mean \|Δ\|a\|\| per tick < **0.06 m/s²**. Needs **240 s** continuous stillness with HR, **360 s** without. HR baseline = **median** of EMA-smoothed HR (α = 0.3) over the first **150 s**. Asleep = still **AND** smoothed HR ≤ baseline − **4 bpm** (`SleepDetector.kt:14-38, 95-128`) | Double buzz when the timer arms; vibration or sound alarm; 60 min max-wait timeout (README) | Not described | **None** | None; README: "heuristics ... Expect to tune them" | 0 stars; one commit 2026-06-22 |
| **[NAP] andreykats/nap-time-ios** | iOS smart nap alarm; wakes 20 min after detected onset | Colmi R02 ring over BLE: per-beat IBI, accepted 300–2000 ms (`ColmiRingManager.swift:288-299`) | Calibration **300 s**; 30 s epochs; 5 min sliding HRV window; Welch PSD on IBIs interpolated at 4 Hz. **AND** of RMSSD > μ + **1 SD**, HR < **0.95** × μ, LF/HF < μ, for **2 consecutive** epochs (`SleepDetectionConfig.swift`; `SleepOnsetDetector.swift:121-171`; `HRVCalculator.swift:18, 118-124`) | `alarm_gentle.caf`; snooze 5 min; backstop alarm if onset is never detected | Saved sessions (`SavedNapSession.swift`) | **None** | None. Header cites Burgess 1999, de Zambotti 2011/2016, Tobaldini 2013, Fonseca 2023, but these are not tested on its data | 1 star; last commit 2026-04-18 |
| **[TDI-ID] idealemu/TDI** → lineage | "Successful replication of Dormio TDI" (README) | One FSR/flex channel | 90 s baseline; rise > 3.5 % then 10 s stable; 10 s grace | Fixed 3 min wait; 60 s recording | Raw log for replay (`TDI.py` `LOGGING_DATA`) | None | Claimed user study, unpublished | 0 stars; 2024-01-29 |
| **[SB] jabituyaben/SteelBall** → lineage | Bangle.js "hypnagogia alarm" (Edison) | Wrist PPG | 5-sample mean < user target (default 70 bpm) AND SD < 3; BPM outside 59–100 rejected (`SteelBall.js:1, 66-98`) | Buzz 500 ms × 4, every 2 s; stops after 2 triggers | CSV of start and trigger times | None | Author only | 1 star; 2020-12-14 |
| **[VIG] wnmurphy/vigilance_device** | Edison-style vigilance task with a game controller | Button held in the hand | Release of the held button → continuous rumble (`vigilance_device.py:37-58`) | Controller rumble | Console print only | None | None | 0 stars; 2026-05-30 |
| **[DH] shreyExp/DreamHacker** | Raspberry Pi: play audio once asleep | Pulse sensor | BPM < threshold (default **77**) held for a wait time (example 30 s) → "sleep" (`main.cpp:120-153`; `PulseSensor.h:506-519`) | User audio file | JSON web API | **GPL-3.0** | None (student project) | 4 stars; 2021-04-19 |
| **[GOOFI] dav0dea/goofi-pipe** `DreamInceptor` node | EEG pipeline node that fires a "dream incubation" trigger | EEG (1 channel) | Baseline **30 s** (15–300); θ/α z-score ≥ **2.0** (plus Lempel-Ziv complexity) for **20 successive windows**, or GSSC hypnodensity entropy < 0.2; then `wait_time` **100 s** (15–300) after the trigger (`dreaminceptor.py` `config_params`) | Downstream (node outputs a trigger) | Node outputs | **MIT** | None | 44 stars; 2026-09-24 |
| **[KH] KairosHive/hypnagogia** → lineage | Offline Hori stage 4/5 detector | Muse EEG (Mind Monitor CSV) | Rule-based θ/α and flattening | — | CSV / PNG | None | None | 1 star; 2025-06-27 |
| MIT openSleep / dormio → `../opensleep_dormio_code_analysis.md` | Reference | Glove (PPG, flex, EDA) | OR of absolute deltas, no hold | Recorded prompts | zip | **None** | P20, SR23 | — |

### 2.2 Sleep-stage-gated cueing for TLR/TMR (the same architecture, a different target stage)

| Project | What it does | Sensors | Detection / trigger logic | Cue / audio | Data logging | Licence | Evidence / validation | Status |
|---|---|---|---|---|---|---|---|---|
| **[HTMR] nathanww/home-tmr** ("SleepStim") | Automated TMR at home: cues during N3 | Fitbit Versa/Sense: HR, 3-axis accelerometer, gyro at 1 Hz → Android phone ([WHIT22] Methods) | Neural net on a time-frequency representation of the last 240 s → p(N3) ([WHIT22]). Cue when mean p(N3) over the buffer ≥ **0.90** AND latest ≥ **0.85** (`MainActivity.java:80-83, 784`) | Continuous **white noise bed** at a user-set level. Cue volume **starts at 0 and ramps** by `VOLUME_INC` per second up to noise + `CUE_NOISE_MAX`. When p falls, cues pause, cue level drops 0.3 and the ceiling drops by `MAX_ADAPTION_STEP` 0.015. Backoff timer; `MAX_STIM` 2000 s per night; ISI 10 s; no cues after 3 h (`MainActivity.java:80-100, 784-870`; README) | MediaLog (file, time, volume); raw Fitbit data and p(N3) to file; **live telemetry to a lab server the README calls "not secured or HIPPAA compliant"** | **MIT** | [WHIT22]: n = 61 and 24, home RCT design; cues enriched for N3; low intensity better | 24 stars; 2022-12-06 |
| **[TLRAPP] rmallela26/TLR** | Full TLR on Apple Watch + iPhone: pre-sleep training, REM detection, REM cueing | Apple Watch HR (0.2 Hz) and 30 Hz motion ([MAL24]) | Random forest (100 trees, `min_samples_leaf` 48) on [EMA HR, EMA activity-count motion, time] (`model.py`), trained on the Walch et al. 2019 Apple Watch + PSG dataset ([MAL24]). REM if p ≥ **0.24**; plus a hard-coded REM rule when 1.16 < motion feature < 2.24 (`processing.py:155-160`). Runs on a **Google Kubernetes server**, one POST every 30 s ([MAL24]) | Cue plays on each REM epoch, but not after **≥ 4 consecutive** REM epochs until REM ends ([MAL24] p.5). User sets the loudest volume that does not wake them | Summary count of cues | **MIT** | REM accuracy 70 %, precision 34 %, recall 57 % (cross-validated, [MAL24] Fig. 1); no lucidity trial | 7 stars; 2024-10-29 |
| **[REMIND] yuriy-mikityuk/remind-eeg** | n = 1 REM-gated TMR/TLR research engine | Muse S Athena (EEG, IMU, PPG); optional Polar H10 | 30 s epochs → REM probability → `StableRemGate` (60 s stable, hysteresis, cooldown) → `ArousalGuard` (motion, alpha, HR jump) | Volume cap 0.20 under per-device calibration; fade in/out; emergency stop | JSONL log of every gate and cue decision; replay validators | **MIT** | "No night with sleep-time audio has been run yet" (README, status 2026-10-23) | 7 stars; 2026-10-27 |
| **[DRM] dreamento/dreamento** | Record, monitor and stimulate with ZMax in real time; offline scoring | Hypnodyne ZMax via its TCP socket | Real-time autoscoring "is not ideal yet" (README) | Visual, auditory, tactile stimulation (README) | ZMax data + annotations | **MIT** | Software paper: SoftwareX 24:101595 (2023) [ESF23] | 152 stars; 2024-03-18 |
| **[LSZ] lucidcode/LucidScribe-Hypnodyne-ZMax** (one of ~20 Lucid Scribe plugins) | Windows plugin: ZMax EEG → "REM" → audio | ZMax EEG | User threshold on EEG amplitude; > **6** threshold crossings in a 512-sample history → REM score; resets after > 12 consecutive samples above (artifact) or > 80 below (`PluginHandler.cs:565-651`) | Audio tracks (via Lucid Scribe) | Lucid Scribe logs | **None** | None | 2 stars; 2023-09-09 |
| **[INSPEC] lucidcode/Halovision-INSPEC** | Night-vision camera detects eye movements in REM | Camera (video) | Eye movement from video (not read) | Audio/visual alerts; webhook API | — | **None** | None found | 65 stars; 2026-10-25 |
| **[SMACC] remrama/smacc** | Windows experimenter console for dream studies | None (manual) | Experimenter cues by hand ([REMIND] compare) | Audio cue designer, BlinkStick/Hue lights, masking noise, intercom | Event log; dream-report recording; EEG port codes over LSL/TTL | **GPL-3.0-or-later** | Used in Paller and Carr lab papers 2024–2026 (`docs/about.md`) | 2 stars; 2026-07-08 |
| **[LT] lucid-trainer/android-lucid-trainer + fit-lucid-trainer** | Fitbit → cloud (MongoDB Atlas) → Android; white noise and guided SSILD/MILD prompts | Fitbit | REM-probability prompting is "Future updates" (README) | White noise via speaker or BT; prompts | Cloud DB | **MIT** | None | 1 star each; 2025-09-28 / 2024-08-10 |
| **[MOR] adamn23/Morpheus** | Scheduled TLR plus webcam motion guard; eye tracking only in "shadow mode" | Webcam (no video stored) | Sensor timing is "hard-locked" until it beats the scheduled arm (README) | Scheduled cues; safety limits | Derived per-second features only | **All Rights Reserved** | None (design only) | 1 star; 2026-08-16 |
| **[MRT] matthewrennie/tlr** | Generates TLR training and cueing MP3s | None | Timer: N min delay, then 5 cues 30 s apart (`assemble_rem_cueing.py`) | Tones **500/700/900 Hz** × 200 ms (`cue_sequence_generator.py`), which **differs from the published 400/600/800 Hz** [KON21]; −25 to −20 dBFS "corresponding to 40–45 dB SPL" (an unsupported mapping, INFERENCE) | — | None | None | 0 stars; 2024-12-29 |

### 2.3 Timers, masks, journals and other related builds

| Project | What it does | Sensors / trigger | Licence | Status |
|---|---|---|---|---|
| [HGG] sowbug/hypnagogo | ATtiny13 LED goggles: idle ~3.5 h, then about 30 s LED blinks at halving intervals; 80 min nap mode (README) | None (timer) | None | 3 stars; 2012 |
| [ZZZ] sourcetortoise/zzz | Raspberry Pi: squeezing a trigger mouse during sleep plays sounds (a reality check) | Mouse button | GPL-3.0 | 7 stars; 2026-05-06 |
| [FLK] remrama/flicker | Detects intentional LRLR "flicks" with a MyoWare sensor → bone-conduction audio | EMG/EOG | MIT | 12 stars; 2019 |
| [DPH] DashBarkHuss/dreamphone | EOG Morse code from a lucid dream; REM detection; light induction | EOG (SpikerShield) | None | 4 stars; 2021 |
| [OLD] Absolute0K/OpenLD-Software | ADS1299 EEG/EOG; REM detection after Imtiaz et al.; stimulus options | EEG | MIT | 15 stars; 2016 |
| [LSK] BitFlaker/lucidsourcekit | Kotlin Android toolkit: dream journal (audio/text), binaural beats, alarms, statistics; local-only storage | None | GPL-3.0 | 27 stars; 2026-07-29 |
| lucidcode/LucidScribe-MouseFIELD | "plays an audio track when the mouse button is released" (GitHub description only) | Mouse button | None | 0 stars; 2022 |
| DavidLamparter/HypnagogiApp | Android "forced hypnagogic sleep" (Dalí); not read beyond the file list | — | None | 0 stars; 2016 |

### 2.4 Found but excluded

- `jhaaswithap/paila-equation-tdi-streamlitapp`: an "equation" P_d = min(I·T·S/48, 100) run on
  simulated cohorts, with no measured data (FACT, README). Nothing to borrow.
- `g3149482122-web/CycleWake-AI`: README only, no code (FACT).
- `albertooishii/dream_incubation` (Flutter, 134 MB) and `wyatt-js/whoop-sleep-onset`: not read (size
  and time).
- `modem-works/dream-recorder` (1,625 stars, MIT): a bedside dream-*recording* device; not read in
  detail.

---

## 3. Table 2: devices, apps and research platforms

| Device / platform | What it does | Sensors | Detection / trigger | Cue / audio | Data logging | Openness | Evidence / validation | Status |
|---|---|---|---|---|---|---|---|---|
| **Sleep as Android** (Urbandroid) | Smart alarm, sleep tracking; lucid-dreaming cues | Phone accelerometer or sonar; wearables (Wear OS 2–4, Garmin, Fitbit, Samsung) for movement, HR, SpO2 and HRV where allowed ([SAA-W]) | Phases from "average short-term amount of physical activity". "Deep" ≈ N3 + part N2; "light" ≈ REM + N1 + part N2 ([SAA-T]). Lucid cues start when "REM" is detected, with a "Later" delay and a "Sensitivity" setting ([SAA-L]) | Default voice "You are dreaming"; Repeat; Volume; "Force headphones only"; vibration goes to the wrist when a wearable is paired; light cues via smart lights ([SAA-L]) | CSV export | Closed app; add-ons on GitHub without a LICENSE file ([SAA-G]) | VENDOR CLAIM: phases "strongly correspond" to a sleep-lab comparison ([SAA-T]); no peer-reviewed validation found | Active |
| **Lucid Scribe** apps (lucidcode) | Android/iOS app with "TLR, TDI, prospective memory trainer for MILD" (awesome list) | Works with the INSPEC camera ([INSPEC] README) | NOT VERIFIED (Play page not readable) | — | — | Closed app; plugins on GitHub, no licence | None found | Active |
| **Hypnodyne ZMax** (+ HDRecorder, HDScorer; open tools Dreamento, Lucid Scribe) | Research EEG headband | 2 EEG (F7-Fpz, F8-Fpz), HR, SpO2, forehead and room temperature, light, sound, movement ([ZMAX]). [MR19] places the electrodes at AF7/AF8–FpZ, which conflicts with the vendor | Vendor autoscoring (paid); real-time via TCP socket; proprietary radio with "3.5 ms latency" (VENDOR CLAIM) | Scriptable sound; a researcher quote mentions visual and vibration stimulation (VENDOR) | EDF | Closed hardware, open socket | VENDOR CLAIM of PSG agreement; used in multi-centre TLR [PD26] (v1 title: "wearable EEG") | Sold to labs |
| **Remee** (Bitbanger Labs, 2012) | LED sleep mask | None: "Using a series of smart timers" ([MR19]) | Timers only ("the only one that does not use online sleep stage detection", [MR19]) | Red LED patterns; delays of "4–5 h" and "15 min" are PRESS, NOT VERIFIED | — | Closed | None ([MR19]) | Kickstarter 2012 |
| **DreamLight → NovaDreamer** (LaBerge, Lucidity Institute) | REM-cued light mask | Eye-movement-based REM detection per the [MR19] table (method not described) | Automatic REM detection | Flashing lights | — | Closed | DreamLight: 14 subjects, 4–24 nights; 11 (78 %) had 32 lucid dreams, 22 on cue nights versus 10 on non-cue nights ([MR19], citing LaBerge & Levitan 1995). NovaDreamer itself was not tested | Discontinued 2004; NovaDreamer 2 never shipped ([MR19]) |
| **iBand(+)** | EEG headband with "auto-learning" algorithm | EEG, movement, temperature, HR ([MR19]) | Closed | Sound/light | — | Closed | None ([MR19]) | Crowdfunded 2016 |
| **Aurora** (iWinks) | EEG headband | EEG, accelerometer | "90 %" accuracy claim without data ([MR19]) | Light/sound | — | Platform described as open ([MR19]) | None | Crowdfunded 2013 |
| **Prophetic Halo** | Headband promising lucid-dream induction with focused ultrasound + EEG | EEG (claimed) | — | Transcranial focused ultrasound (claimed) | — | Closed | PRESS: "hasn't provided tangible proof yet" (Business Insider via search summary); NOT VERIFIED | Pre-orders with $100 deposit; shipping unconfirmed (PRESS) |
| **Elemind** | Headband to speed sleep onset (insomnia) | EEG | Alpha phase tracking | **Pink-noise pulses anti-phase to alpha**, bone conduction, for 30 min; rain background **18 dB below** the pulses; user sets the rain "just audible" ([BRE24] Methods) | — | Closed | RCT crossover, n = 21: SOL −10.5 ± 15.9 min (p = 0.0019); all authors are Elemind employees ([BRE24]) | Sold (2024–) |
| **Dreem** (Dreem 2 headband) | EEG headband; research and consumer | Dry EEG, accelerometer, PPG ([ARN20]) | Automatic staging | Closed-loop acoustic stimulation (not covered by [ARN20]) | Cloud | Closed | 25 subjects vs PSG: staging accuracy 83.5 % vs 86.4 % for 5 experts; HR MAE 1.2 bpm ([ARN20]). Used as SleepStim's reference stager ([WHIT22]) | Consumer status not verified |
| **Muse S** (InteraXon) | Meditation + sleep EEG headband | EEG, PPG, IMU | Closed stager | "Dream Recall" is a REM-timed wake alarm ([REMIND] compare, NOT VERIFIED by me) | App | Closed (open LSL/BrainFlow streaming) | n = 47 vs level-1 PSG: κ 0.76 overall; **N1 κ 0.41**; REM 0.85; Wake 0.84 ([LAN25] Table 2) | Sold |
| **Kokoon / Philips Sleep Headphones** | Headphones that fade audio at sleep onset | PRESS: EEG sensors (Kokoon); "biosensors", accelerometers and an optical HR monitor (Philips) | Sleep-onset detection (closed) | Fades audio and equalisation, then switches to masking noise (PRESS) | App (subscription, PRESS) | Closed | None found | Sold (status not verified) |
| **Northwestern home TLR app** [KON24] | Phone-only TLR at home | Phone accelerometer, phone face-down beside the pillow | **No staging**: cues start **6 h** after pre-sleep training ends; repeat every **20–40 s**; pause **5 min** after movement (Exp 2: **1 min**); pause **45 min** after the user reports a cue woke them (§2.1.2, §3.1.2) | Night 1 intensity starts at the user's **perceptual discrimination threshold** (tested in the app); **+0.16 % per cue** (optional slow mode +0.08 %, capped at threshold, halt on arousal). Exp 2 **caps intensity at the level the user reports as incorporated**; 3 s harp melodies with fade in/out. Training cue: 400/600/800 Hz beeps (~650 ms) or a 1000 ms violin | Post-awakening question "Did you wake up because of a sound…?" then a report form | Closed (MIT App Inventor) | Exp 1: lucid dreams 0.74 → 2.11 per week (n = 19, p = .007); 14 of 38 lucid dreams cue-provoked. Exp 2 (blinded, n = 120 with ≥ 1 night per condition): mixed; cues raised movement (0.19 vs 0.10 per cue, p = .002) | Published 2024 |
| **SleepStim** [WHIT22] | See [HTMR] | Fitbit + Android | p(N3) ≥ 0.9 averaged, ≥ 0.85 now; cues every 10 s ([WHIT22] Fig. 3 legend) | Cues over white noise; intensity setpoint from the user's noise slider | Dreem 2 for verification | MIT code | 34.7 % of cues in N3 (N3 = 21.1 % of sleep); 65.5 % in N2+N3; ~16 % in wake; 13 % (Exp 1) and 7 % (Exp 2) of participants heard cues | Published 2022 |
| **Two-way dream communication** [KON21] | Lab: questions to lucid dreamers in REM, answers by eye movement or face muscles | Full PSG (EEG/EOG/EMG) | Experimenter-judged REM; dreamer signals LRLR | TLR cue: three beeps 400/600/800 Hz at **~40–45 dB SPL**, ~650 ms; spoken maths problems, light flashes, tactile | PSG | — | 36 participants, 4 labs; 158 attempts during signal-verified lucid dreams; correct answers on 18.4 %; 29 correct answers from 6 people ([KON21] Summary, Results) | Published 2021 |
| **TLR in the lab** [CARR23] | Pre-sleep training paired with cues, cues again in REM during a morning nap | PSG | Experimenter-cued REM | Sensory cues | PSG | — | "50% of participants had a lucid dream in a single nap session" (via [KON24]; [CARR23] NOT READ) | Online 2020; issue 2023 |
| **Multi-centre TLR** [PD26] | SSILD training + visual/auditory/tactile cues in REM; 2 naps, stim vs sham | Lab (v1 title says "wearable EEG") | — | Multisensory | — | — | n = 60: SVLDs in 51.7 % of participants; stim 38.3 % vs sham 28.3 %, n.s.; cues perceived in 71.1 % of cued REM periods; **cues disrupted sleep in 23.7 %** (abstract v2) | bioRxiv v2, 2026-08-21 |
| **Edison bottle** [LAC21] → `sleep_onset_physiology.md` §3 | 20 min rest holding a bottle; drop wakes the subject | PSG + bottle | Behavioural (drop) | — | — | — | N1 group 83 % insight vs wake 31 % vs N2 14 %. Of 63 drops, 26 (41 %) came after N1; hypnagogic content was reported on 61.7 % of drops vs 22.5 % without a drop | Published 2021 |
| **MIT "Dive"** ([DIVE]) | Android app with Northwestern presenting sounds in REM; credits Nathan Whitmore | Smartwatch or phone motion "being tested" | Not described | Sounds in REM | — | — | None reported | Project page only |

---

## 4. Cross-cutting comparisons

### 4.1 Onset-detection rules, side by side (all FACT from the cited code or paper)

| Rule | Baseline | Signals | Threshold form | Combine | Hold | Refractory / re-arm | Re-baseline |
|---|---|---|---|---|---|---|---|
| Dormio paper (P20 §2.2) | mean of first 120 s | HR, EDA, flex | \|Δ\| > 5 bpm / 4 µS* / 8 kΩ* (*raw ADC units, PhD22) | OR | none stated | — | no |
| Dormio web code | 3 min (UI) | HR, EDA, flex | \|Δ\| ≥ 6 bpm / 4 / 5 counts | OR | none | later wakes timer-only | no |
| Dormio iOS code | 300 s | HR, EDA, flex or HBOS | \|Δ\| ≥ 15 bpm / 10 / 50 counts | one detector selected | none | — | per prompt (per [AMU] `dormio-method.md` §2) |
| **Amulet XPL** | first 120 s (mean, SD) | HR (15 s), flex (10 s) | max(\|ΔHR\|/5, \|Δflex\|/8 kΩ) ≥ 1 | OR | **8 s** | **60 s** + 30 s settle | **every report window** |
| idealemu/TDI | 90 s mean | flex/FSR | +3.5 % rise, then a stable plateau | — | 10 s stable | 10 s grace | no |
| **DynamicNapAlarm** (Wear OS) | 150 s median HR | accelerometer, HR | Δ\|a\| < 0.06 m/s²; HR ≤ base − 4 bpm | **AND** | stillness 240 s | one-shot | no |
| **nap-time-ios** | 300 s | IBI: RMSSD, HR, LF/HF | +1 SD / −5 % / < mean | **AND** | 2 × 30 s epochs | one-shot | no |
| SteelBall | none (absolute) | HR | mean₅ < target, SD₅ < 3 | AND | 5 samples | max 2 triggers | — |
| DreamHacker | none (absolute) | HR | BPM < 77 | — | wait (e.g. 30 s) | — | — |
| goofi DreamInceptor | 30 s | EEG θ/α, LZ | z ≥ 2.0 | — | 20 windows | wait 100 s | — |
| CLAUDE.md §4 (current) | 180 s, μ ± σ | stillness, HR/RMSSD, EDA slope | −7 % HR / +15 % RMSSD / slope ≤ 0 | stillness AND ≥ 1 autonomic | 20 s (10 s if all three) | abort on movement | no |

INFERENCE. Three patterns hold across independent builds.

- **Hold times.** Every build written after MIT's code added a hold: Amulet 8 s, idealemu 10 s,
  DynamicNapAlarm 240 s, nap-time 60 s, CLAUDE.md 20 s.
- **AND logic.** Every wrist or ring build uses AND (stillness AND HR, or HRV AND HR). Only the glove
  builds keep Dormio's OR.
- **Re-baselining.** Only Amulet re-baselines, and it is the only one that reports a real overnight
  failure mode (drift).

Nobody has measured any of these rules against PSG.

### 4.2 Cue-delivery and anti-startle practices (all FACT, cited)

| Practice | Who | Detail |
|---|---|---|
| Measure the user's own threshold in the app | [KON24] §2.1.2 | Auditory perceptual threshold and discrimination test on night 1; cueing starts there |
| Start silent, ramp up | [HTMR] README; [KON24] | home-tmr: from 0, by `VOLUME_INC`/s up to noise + max. KON24: +0.16 % per cue every 20–40 s |
| Cap at the level that worked | [KON24] §3.1.2 | Cap at the intensity the user reports as incorporated into a dream |
| Lower the ceiling after an arousal | [HTMR] `MainActivity.java:802-830` | On arousal: pause, cue −0.3, ceiling −0.015, backoff |
| Pause on movement | [KON24]; [REMIND]; [MOR] | 5 min (Exp 1) / 1 min (Exp 2) after phone-accelerometer movement; 45 min after a self-reported awakening |
| Masking noise bed | [HTMR] ([WHIT22]); [BRE24]; [SMACC] | Cues set relative to a user-chosen white-noise level; Elemind: rain 18 dB under the stimulus, "just audible" |
| Short, faded cues | [KON24] Exp 2 | ~3 s harp melodies with intensity "gradually fading in and out" |
| Per-night cap | [HTMR] | `MAX_STIM` 2000 s per night; no cues after `OFFSET_DELAY` 3 h |
| Do not cue on and on in a long REM run | [MAL24] p.5 | No cue after ≥ 4 consecutive REM epochs until REM ends |
| Haptic wake before voice | [AMU] `01-system.md:10, 60` | Escalating ~5 s LRA pulse train on the wrist, then the spoken prompt |
| Pre-rendered prompts | [AMU] `01-system.md:89-95` | "rendered to WAVs once and played from file, never synthesised live" (fixed stimulus, no synthesis latency) |
| Headphones-only option | [SAA-L] | "Force Headphones Only" routes cues only to headphones |
| **Anti-pattern: force system volume to max** | [HTMR] `MainActivity.java:225-232, 633, 657-675` | Sets `STREAM_MUSIC` to maximum on start and resume, and returns `true` for VOLUME_UP/DOWN so the keys do nothing; loudness lives only in in-app gains |

### 4.3 How well do consumer devices see the stage they target? (FACT)

| System | Target | Reference | Result |
|---|---|---|---|
| Muse-S (EEG) | 5-class staging | Level-1 PSG, n = 47 | κ 0.76; **N1 0.41**; REM 0.85 ([LAN25]) |
| Dreem (EEG) | 5-class staging | PSG, 5 scorers, n = 25 | accuracy 83.5 % vs scorers 86.4 % ([ARN20]) |
| Human scorers | N1 | inter-rater | 63.0 % agreement on N1 (cited in [ARN20] Introduction; primary not read) |
| SleepStim (Fitbit HR + motion) | N3 | Dreem 2 | 34.7 % of cues in N3; ~16 % in wake ([WHIT22]) |
| TLR app (Apple Watch HR + motion) | REM | PSG dataset, cross-validation | accuracy 70 %, precision 34 %, recall 57 % ([MAL24]) |
| Sleep as Android (actigraphy) | phases | vendor's own lab comparison | "subject to a high degree of error" (vendor, [SAA-T]) |

INFERENCE. N1 is the hardest stage for EEG devices and for human scorers. A wrist-only N1 detector should
be presented as a *probabilistic trigger*, as Dormio itself argued ("large margin of error", P20 §4.1),
not as staging.

---

## 5. Licence compatibility with GPL-3.0 (the owner's chosen licence)

| Source | Licence (GitHub API / LICENSE file) | Can OpenTDI copy code under GPL-3.0? |
|---|---|---|
| amulet-xpl, home-tmr, rmallela26/TLR, remind-eeg, Dreamento, goofi-pipe, flicker, OpenLD, lucid-trainer | MIT | Yes; keep the MIT copyright and permission notice (INFERENCE, standard MIT terms) |
| SMACC (GPL-3.0-or-later), zzz, DreamHacker, lucidsourcekit, GiorgioRegni/Binaural-Beats | GPL-3.0 | Yes |
| openSleep, dormio, ThingEngineer firmware, DynamicNapAlarm, nap-time-ios, SteelBall, idealemu/TDI, LucidScribe plugins, Halovision-INSPEC, hypnagogo, dreamphone, matthewrennie/tlr, Sleep-as-Android add-ons | None found | **No.** Re-implement from the described behaviour only, as `../opensleep_dormio_code_analysis.md` §7 already advises for MIT's repos |
| Morpheus | "All Rights Reserved" (README badge) | No |
| Published parameter values (thresholds, timings, tone frequencies) | Facts, not code | Can be used with citation (INFERENCE; not legal advice) |

INFERENCE, not legal advice. A GPL-3.0 APK that bundles Samsung's proprietary Health Sensor SDK is the
usual "proprietary library" case. Projects normally handle it with a GPLv3 §7 additional permission
that allows linking with that SDK, and by not committing the SDK binary to the repository (the repo
already has a `samsung-health-stub` module). Confirm this against Samsung's SDK licence before the first
release.

---

## 6. What OpenTDI does differently, should borrow, should avoid

### 6.1 Differences (INFERENCE from §2–§3, "of the projects found")
- **Sensor set.** No open project uses wrist EDA. The wrist builds use HR + motion (DynamicNapAlarm,
  SleepStim, TLR app, SteelBall) or IBI only (nap-time).
- **Stage.** Everyone with a published trial targets REM or N3. Only Dormio, Amulet and idealemu target
  sleep onset with a serial-awakening loop.
- **Report pipeline.** None of the repos I read transcribes reports on the device. Amulet and Dormio iOS
  record with silence stop; the others record nothing.
- **Platform.** DynamicNapAlarm is the only other Wear OS / Galaxy Watch code. It uses the platform
  `SensorManager`, not the Samsung SDK, and no IBI or EDA.

### 6.2 Borrow
1. **Amulet XPL's detector scaffolding** (MIT, can be copied): a causal 1 Hz feature contract; the
   Dormio rule as a scored ratio max(|Δ|/θ); `HOLD_S`, `SMOOTH_S` and a refractory period; re-baselining
   in the report window; a `thresholds.py`-style script that rebuilds P20's Sleep/Wake percentile table
   from OpenTDI's own sessions; and a synthetic-session generator to test the plumbing.
2. **The "were you asleep?" label at every awakening** (Awake / Halfway / Asleep). Dormio, Amulet and
   Konkoly 2024 all use a self-report of this kind as ground truth.
3. **Konkoly 2024's intensity protocol** for prompts 1 and 3, which must not wake the user: an in-app
   threshold test, start at threshold, small steps, pause on movement, cap at the incorporated level.
4. **home-tmr's adaptive ceiling and backoff** (MIT): after an arousal, lower the maximum and wait.
5. **A noise bed**, optional and user-set, with cues defined relative to it (home-tmr, Elemind). This
   overlaps with CLAUDE.md §5's −75 dBFS keep-alive carrier, but serves masking, not DAC keep-alive.
6. **Pre-render TTS prompts to files** when the theme is created (Amulet's rationale: a fixed stimulus,
   and no synthesis latency inside the window).
7. **A wrist-haptic wake option** before the voice (Amulet LRA; Sleep as Android sends vibration to the
   wrist).
8. **Blinded control nights.** Konkoly 2024 Exp 2 and REMind's N-of-1 blocks compare cue, sham and
   no-cue nights. For OpenTDI, alternate `TIMED_CUES` and sensor-gated sessions.
9. **A watch-kept session clock** (Amulet: "the glove keeps the session clock"), so the session ends on
   time if the phone dies.

### 6.3 Avoid
1. **Absolute HR targets** (SteelBall 70 bpm, DreamHacker 77 bpm). They depend on the person and the
   day.
2. **Cloud inference or telemetry** (TLR app on GKE; home-tmr's unsecured telemetry server; lucid-trainer
   on MongoDB Atlas). They add latency and send personal physiological data off the device.
3. **Forcing the system volume to maximum and disabling the volume keys** (home-tmr). The user loses
   the physical way to turn a too-loud cue down.
4. **One baseline for a whole hour-long session** (Amulet's 98-cue overnight failure).
5. **Accuracy claims without data** (Aurora's "90 %", [MR19]; Prophetic's claims, PRESS). OpenTDI's UI
   should say "estimated sleep onset", not "N1 detected".
6. **Long stillness gates for TDI.** DynamicNapAlarm needs 240 s of stillness, and Lacaux's N1 group
   spent only 1.35 ± 1.20 min in N1 ([LAC21]). A 4 min gate would often fire after the hypnagogic window
   has closed (INFERENCE).
7. **Copying unlicensed code** (§5).

---

## 7. Implications for OpenTDI

Strength scale: **strong** = several independent controlled studies or direct code facts about the
platform; **moderate** = one controlled study or consistent independent builds; **weak** = a single
uncontrolled source or reasoning; **anecdotal** = one person's report.

1. **Add a hold and a refractory period to every trigger mode, including "MIT Standard".** Defaults:
   hold 8–20 s (Amulet 8 s, idealemu 10 s, CLAUDE.md 20 s) and refractory 60 s after any cue. Label the
   values GUESSED in code. Evidence: **weak**. Independent builds converge on it ([AMU], [TDI-ID]), but
   nobody has PSG data. Dormio had no hold (P20).
2. **Re-take the baseline during each report window**, while the user is awake by construction, using a
   30–60 s slice. Keep the first 120–180 s baseline for cycle 1. Evidence: **anecdotal** (the Amulet
   overnight failure, [AMU] `01-system.md:84-86`) plus **weak** reasoning (sensor shift and
   time-of-session drift).
3. **Combine wrist signals with AND, as every wrist or ring build does** (stillness AND an HR drop):
   DynamicNapAlarm uses −4 bpm from a 150 s *median*; nap-time uses −5 % plus HRV. A default HR-drop
   threshold of 4–5 bpm (or 5 %) is consistent with these builds and with Dormio's 5 bpm. Keep it
   user-adjustable. Evidence: **weak**. P20 Fig. 4 shows HR Sleep and Wake deltas overlapping heavily
   (SD ≈ 19–23 bpm); see `../horowitz2020_dormio_paper.md` §2.3.
4. **Use the median, not the mean, for the HR baseline** (DynamicNapAlarm), and reject out-of-range or
   artefact IBIs before computing RMSSD (nap-time accepts 300–2000 ms only). Evidence: **weak** (code
   practice; robust statistics).
5. **Keep the stillness gate short (≤ 60 s, as CLAUDE.md §4 says), not 4–6 min.** Evidence: **moderate**
   for the short hypnagogic window ([LAC21]: N1 group mean 1.35 min of N1; insight lost if N2 was
   reached) and **weak** for the specific 60 s value.
6. **Offer a "Short window" onset-to-wake preset of 60 s next to Dormio's random 60–300 s.** Amulet fixes
   it at 60 s, citing Lacaux's ≥ 15 s of N1 and the loss of the effect in N2. Evidence: **moderate** that
   N2 harms the insight effect ([LAC21], one study, an insight task rather than dream incorporation);
   **weak** for 60 s specifically.
7. **Report recording defaults:** at least 30 s, stop after 6–8 s of silence, cap at 120 s. Amulet uses
   ≥ 30 s then 6 s quiet ([AMU] `03-app.md:36`); Dormio iOS uses 30–120 s with 8 s quiet
   (`../opensleep_dormio_code_analysis.md` §0.8). Evidence: **weak** (code defaults, no study).
8. **Incubation and re-incubation prompts must not wake the user. Use Konkoly 2024's method:** an
   in-app hearing/discrimination threshold test; start at that level; raise in small steps; pause
   cueing 1–5 min after watch-accelerometer movement; cap at the level the user reports as heard in a
   dream; never set the system volume. Evidence: **moderate** ([KON24], two home experiments with a
   blinded arm; [WHIT22]: lower intensity gave better outcomes; [PD26]: 23.7 % of cued periods
   disrupted).
9. **Lower the volume ceiling after any cue followed by movement or an HR jump** (home-tmr's
   `MAX_ADAPTION_STEP` and backoff), and set a per-session cue cap. Evidence: **weak** (code; the
   SleepStim trial used it, but did not test it in isolation).
10. **Optional masking noise bed, with prompts defined relative to it** (white, pink or rain, user-set
    "just audible"). Elemind ran its rain 18 dB under the stimulus ([BRE24]); SleepStim set cue levels
    from the user's white-noise slider ([WHIT22]). Evidence: **weak** for TDI (both are different
    paradigms).
11. **Pre-render all prompts to audio files at theme creation**, including TTS, and never synthesise
    inside a cycle. Evidence: **weak** (design rationale, [AMU] `01-system.md:89-95`).
12. **Log "were you asleep?" (Awake / Halfway / Asleep) after every awakening.** Ship a replay script
    that rebuilds P20 §3.3's table (80th percentile of Sleep, 20th of Wake) per user from those labels,
    and use it to tune thresholds. Evidence: **strong** that this is the ground truth MIT used (P20) and
    that independent builds reuse it ([AMU] `thresholds.py`); **weak** that self-report is accurate
    (P20 §4.1 admits it can be unreliable).
13. **Validate sensor mode against `TIMED_CUES`, not against nothing.** Alternate sessions, blinded
    where possible, and compare incorporation rates and awakenings per cycle. Evidence: **strong** for
    the design need. Timer-only TDI works ([FS24] in `../horowitz2020_dormio_paper.md` §3). Sensor-cued
    TLR did not beat sham ([PD26]). Home cueing with a blinded control needed a between-group design
    ([KON24] Exp 2).
14. **State detection quality honestly in the UI and docs.** Use "estimated sleep onset", and cite that
    EEG devices reach only N1 κ ≈ 0.41 ([LAN25]) and wrist stagers about 34 % REM precision ([MAL24]).
    Evidence: **strong** (multiple validation studies).
15. **Offer an optional wrist "Edison" gate:** the user holds the forearm raised or keeps a finger on the
    bezel, and a sustained drop or orientation change fires the cue (like vigilance_device and
    LucidScribe-MouseFIELD, with a watch accelerometer). Evidence: **moderate** that object drops capture
    hypnagogic content (61.7 % vs 22.5 %, [LAC21]), but only 41 % of drops followed N1. **Weak** for a
    wrist version (untested).
16. **Haptic pre-wake on the watch** (a short escalating vibration) as an alternative or precursor to the
    voice wake prompt. Evidence: **weak** (Amulet design; Sleep as Android feature; not tested for TDI).
17. **Keep every computation and all data on the devices; no telemetry server.** Evidence: **strong** as
    a privacy requirement (home-tmr's own README warns its server is not secure); not an efficacy claim.
18. **Keep the session clock on the watch.** If Bluetooth drops, the watch ends or pauses the session
    itself (Amulet). This matches CLAUDE.md's standalone fallback. Evidence: **weak** (design practice).
19. **GPL-3.0 hygiene.** Copy only MIT/GPL code (Amulet, home-tmr, TLR app, REMind, goofi, SMACC) and keep
    their notices. Re-implement everything from unlicensed repos. Add a §7 linking permission for the
    Samsung SDK. Evidence: **strong** for the licence facts (GitHub API); legal interpretation is
    INFERENCE.
20. **Watch battery.** The Apple Watch TLR app's main stated limitation is battery drain from the
    always-on HR "workout session" ([MAL24] preprint p.8). OpenTDI's foreground health service will have the
    same cost, so measure drain per hour of session on the SM-L500 before setting a maximum session
    length. Evidence: **moderate** (an explicit report on another platform; not measured on Samsung).

---

## 8. Unverified items and gaps

- **[CARR23]** (Carr et al., TLR in a lab nap) was not read. Its design and the "50 %" figure come from
  [KON24]. The task brief says "Carr et al. 2020": the article was online-first in 2020 and appeared in
  vol. 10(4), 2023 (Crossref).
- **[MAL24]**: the IJoDR page (journals.ub.uni-heidelberg.de, article 100797) served a bot-protection
  page, so I read the PsyArXiv preprint (10.31234/osf.io/e9u8v). REMind's notes give IJoDR 17(2):143–147;
  I did not confirm volume and pages.
- **[PD26]**: only the bioRxiv abstract (v1 and v2) was read. v1 was titled "Highly effective verified
  lucid dream induction using combined cognitive-sensory training and wearable EEG", with first author
  Jafarzadeh Esfahani. v2 (2026-08-21) has first author Picard-Deland. v2's conclusion ("our results do
  not show clear benefits of REM cueing over SSILD training alone") is the one used here.
- **Sleep as Android**: docs.sleep.urbandroid.org has a TLS certificate error, which I did not bypass. I
  read the same docs at sleep.urbandroid.org/docs (WebFetch summaries). The phone app's REM/lucid
  algorithm is closed; I saw no numbers.
- **Remee, NovaDreamer, iBand+, Prophetic Halo, Kokoon/Philips**: only [MR19] (a peer-reviewed opinion
  article) and press. No vendor manuals or teardowns were read. Remee's 4–5 h and 15 min timings are
  PRESS, NOT VERIFIED.
- **Dreem**: consumer availability today and its closed-loop stimulation parameters were not verified.
  [ARN20] covers staging only.
- **Muse "Dream Recall"** behaviour comes from REMind's notes, not verified by me.
- **Lucid Scribe Android "TDI" mode**: only the awesome-list description. The Play Store page could not
  be read.
- **Carrington 2005** (HR falls ~6.6 bpm from presleep wake to stable sleep): I read only the abstract,
  which gives no number. The 6.6 bpm figure is from Amulet's notes; see `sleep_onset_physiology.md` §6.1.
- **Not read, for time or size**: `albertooishii/dream_incubation`, `wyatt-js/whoop-sleep-onset`,
  `modem-works/dream-recorder`, Halovision-INSPEC firmware, Dreamento code, REMind code beyond the
  README, EazzZyLearn, COsleep, Portiloop, and the Amulet iPad web app. My clone of `software/` held only
  the native shell, and the README says the app ships through TestFlight.
- None of the open-source onset detectors in §2.1 has been validated against PSG. Every performance
  statement about them is the author's own.

---

## 9. References

**Repositories** (GitHub; stars and last-commit dates as of 2026-10-27)
- [AMU] Leanderberg E. *amulet-xpl*. https://github.com/erikleanderberg/amulet-xpl, commit `081f199` (2026-10-11), MIT.
- [DNA] Ermond16. *DynamicNapAlarm*. https://github.com/Ermond16/DynamicNapAlarm (2026-06-22), no licence.
- [NAP] Kats A. *nap-time-ios*. https://github.com/andreykats/nap-time-ios (2026-04-18), no licence.
- [TDI-ID] Dumelié A. *TDI*. https://github.com/idealemu/TDI (2024-01-29), no licence.
- [SB] jabituyaben. *SteelBall*. https://github.com/jabituyaben/SteelBall (2020-12-14), no licence.
- [VIG] Murphy W. *vigilance_device*. https://github.com/wnmurphy/vigilance_device (2026-05-30), no licence.
- [DH] shreyExp et al. *DreamHacker*. https://github.com/shreyExp/DreamHacker (2021-04-19), GPL-3.0.
- [GOOFI] dav0dea. *goofi-pipe*, `src/goofi/nodes/analysis/dreaminceptor.py`. https://github.com/dav0dea/goofi-pipe (2026-09-24), MIT.
- [KH] KairosHive. *hypnagogia*. https://github.com/KairosHive/hypnagogia (2025-06-27), no licence.
- [HTMR] Whitmore NW. *home-tmr*. https://github.com/nathanww/home-tmr (2022-12-06), MIT.
- [TLRAPP] Mallela R. *TLR*. https://github.com/rmallela26/TLR (2024-10-29), MIT.
- [REMIND] Mikityuk Y. *remind-eeg*. https://github.com/yuriy-mikityuk/remind-eeg (2026-10-27), MIT; `docs/compare.md` dated 2026-10-21.
- [DRM] *Dreamento*. https://github.com/dreamento/dreamento (2024-03-18), MIT.
- [LSZ] lucidcode. *LucidScribe-Hypnodyne-ZMax*. https://github.com/lucidcode/LucidScribe-Hypnodyne-ZMax (2023-09-09), no licence.
- [INSPEC] lucidcode. *Halovision-INSPEC*. https://github.com/lucidcode/Halovision-INSPEC (2026-10-25), no licence.
- [SMACC] Mallett R. *SMACC*. https://github.com/remrama/smacc (2026-07-08), GPL-3.0-or-later.
- [LT] lucid-trainer. *android-lucid-trainer*, *fit-lucid-trainer*. https://github.com/lucid-trainer (2025-09-28 / 2024-08-10), MIT.
- [MOR] adamn23. *Morpheus*. https://github.com/adamn23/Morpheus (2026-08-16), All Rights Reserved.
- [MRT] Rennie M. *tlr*. https://github.com/matthewrennie/tlr (2024-12-29), no licence.
- [HGG] sowbug. *hypnagogo*. https://github.com/sowbug/hypnagogo (2012-05-10). [ZZZ] https://github.com/sourcetortoise/zzz (GPL-3.0). [FLK] https://github.com/remrama/flicker (MIT). [DPH] https://github.com/DashBarkHuss/dreamphone. [OLD] https://github.com/Absolute0K/OpenLD-Software (MIT). [LSK] https://github.com/BitFlaker/lucidsourcekit (GPL-3.0).
- [SAA-G] Urbandroid. *Sleep-as-Android-Garmin-Addon*, `SleepGarmin-watch2/source/SensorManager.mc`. https://github.com/urbandroid-team/Sleep-as-Android-Garmin-Addon (2026-08-28).

**Papers**
- [KON21] Konkoly KR, Appel K, Chabani E, et al., Paller KA (2021). Real-time dialogue between experimenters and dreamers during REM sleep. *Curr Biol* 31(7):1417–1427.e6. doi:10.1016/j.cub.2021.01.026. PMC8162929 (full text).
- [KON24] Konkoly KR, Whitmore NW, Mallett R, Mazurek CY, Paller KA (2024). Provoking lucid dreams at home with sensory cues paired with pre-sleep cognitive training. *Conscious Cogn* 125:103759. doi:10.1016/j.concog.2024.103759. PMC11542932; full text read from https://faculty.wcas.northwestern.edu/paller/C&C24.pdf.
- [CARR23] Carr M, Konkoly K, Mallett R, Edwards C, Appel K, Blagrove M (2023; online 2020). Combining presleep cognitive training and REM-sleep stimulation in a laboratory morning nap for lucid dream induction. *Psychol Conscious Theory Res Pract* 10(4):413–430. doi:10.1037/cns0000227. NOT READ.
- [WHIT22] Whitmore NW, Harris JC, Kovach T, Paller KA (2022). Improving memory via automated targeted memory reactivation during sleep. *J Sleep Res* 31(6):e13731. doi:10.1111/jsr.13731. PMC9649863 (full text).
- [LAC21] Lacaux C, Andrillon T, Bastoul C, et al. (2021). Sleep onset is a creative sweet spot. *Sci Adv* 7(50):eabj5866. doi:10.1126/sciadv.abj5866. PMC8654287 (full text).
- [MAL24] Mallett R, Mallela R (2024). Targeted lucidity reactivation implemented in an open source watchOS app. PsyArXiv doi:10.31234/osf.io/e9u8v (preprint read). Journal version: *Int J Dream Res*, article 100797 (not read).
- [PD26] Picard-Deland C, Jafarzadeh Esfahani M, Salvesen LAC, et al. (2024 v1 / 2026 v2). Inducing lucid dreaming with multisensory stimulation: a preregistered multi-center study. bioRxiv doi:10.1101/2024.06.21.600133 (abstract only).
- [ESF23] Jafarzadeh Esfahani M, Daraie A, Zerr P, Weber F, Dresler M (2023). Dreamento: an open-source dream engineering toolbox for sleep EEG wearables. *SoftwareX* 24:101595. doi:10.1016/j.softx.2023.101595 (metadata only).
- [MR19] Mota-Rolim SA, Pavlou A, Nascimento GC, Fontenele-Araujo J, Ribeiro S (2019). Portable devices to induce lucid dreams—are they reliable? *Front Neurosci* 13:428. doi:10.3389/fnins.2019.00428. PMC6517539 (full text).
- [BRE24] Bressler S, Neely R, Yost RM, Wang D (2024). A randomized controlled trial of alpha phase-locked auditory stimulation to treat symptoms of sleep onset insomnia. *Sci Rep* 14:13039. doi:10.1038/s41598-024-63385-1. PMC11156862 (full text).
- [ARN20] Arnal PJ, Thorey V, Debellemaniere E, et al. (2020). The Dreem Headband compared to polysomnography for electroencephalographic signal acquisition and sleep staging. *Sleep* 43(11):zsaa097. doi:10.1093/sleep/zsaa097. PMC7751170 (full text).
- [LAN25] Lanthier M, Foti MC, Fonseca K, et al. (2025). Assessing the performance of a portable electroencephalographic sleep monitor against level 1 polysomnography. *Sleep Adv* 7(1):zpaf089. doi:10.1093/sleepadvances/zpaf089. PMC12782022 (full text).
- [CARR05] Carrington MJ, Barbieri R, Colrain IM, Crowley KE, Kim Y, Trinder J (2005). Changes in cardiovascular function during the sleep onset period in young adults. *J Appl Physiol*. doi:10.1152/japplphysiol.00702.2004 (abstract only).
- P20, PhD22, SR23, FS24: see `../horowitz2020_dormio_paper.md` §0.

**Web pages**
- [SAA-L] Sleep as Android docs, Lucid dreaming. https://sleep.urbandroid.org/docs/sleep/lucid_dreaming.html (WebFetch summary, 2026-10-27).
- [SAA-T] Sleep as Android docs, Sleep tracking theory. https://sleep.urbandroid.org/docs/sleep/sleep_tracking_theory.html (WebFetch summary).
- [SAA-W] Sleep as Android docs, Wear OS. https://sleep.urbandroid.org/docs/devices/wearos.html (WebFetch summary).
- [ZMAX] Hypnodyne, ZMax product page. https://hypnodynecorp.com/zmax.php (WebFetch summary; VENDOR).
- [DIVE] MIT Media Lab, "Dive" project page. https://www.media.mit.edu/projects/dive-1/overview/ (WebFetch summary).
- PRESS (not relied on for numbers): Prophetic Halo coverage (sciencefocus.com, decrypt.co, benzinga.com); Kokoon/Philips coverage (channelnews.com.au, techadvisor.com); Remee coverage (ubergizmo.com, world-of-lucid-dreaming.com).
