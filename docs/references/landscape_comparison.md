# OpenTDI against MIT Dormio and similar projects: comparison and parameter evidence

Research note for OpenTDI, written 2026-10-27. It brings together the five landscape surveys, the two
original MIT notes and the current engineering spec. It then compares OpenTDI with MIT Dormio and with
the closest independent projects and devices, and checks every literature-informable default in the spec.

**What this file is.** It is a synthesis and comparison. It adds no new primary-source reading. Every
FACT below is a fact **as reported in the sibling note cited next to it**, and those notes cite the
original paper, thesis, repository or web page (section, table, figure or `file:line`). A later revision
re-read some primaries and says so where it does (§7). Where a sibling note read only an abstract, I say so. Where the notes
disagree, §1.3 says how this file resolves the disagreement.

**Labels.**
- **FACT**: stated in a source, as reported by the cited note.
- **INFERENCE**: my reasoning or arithmetic. Arithmetic is shown.
- **ANECDOTAL**: a README or hobby-project claim, not a study.
- **ABSTRACT ONLY**: the cited note could read only the abstract.
- **SOURCED / GUESSED**: as defined at the top of the spec. SOURCED means some source states the number
  for this use. GUESSED means it does not. I add **GUESSED (lit-consistent)** for a guessed value that
  falls inside a range the literature reports for a related measure, without any source using it as a
  threshold.

**Note keys** (all in `docs/references/`):

| Key | File | What it covers |
|---|---|---|
| [P] | `horowitz2020_dormio_paper.md` | P20, SM19, PhD22, SR23, FS24, FS26 |
| [C] | `opensleep_dormio_code_analysis.md` | openSleep / dormio web and iOS code |
| [LIN] | `landscape/mit_tdi_lineage.md` | CHI18, Y25, MAKE22, idealemu/TDI, SteelBall, W25, LAC21 as comparator |
| [PHYS] | `landscape/sleep_onset_physiology.md` | N1 physiology: HR, HRV, EDA, temperature, behaviour |
| [WEAR] | `landscape/wearable_sleep_onset_detection.md` | Actigraphy and wrist staging algorithms, consumer validations, datasets |
| [PROJ] | `landscape/similar_projects.md` | Open-source builds, TLR/TMR apps, commercial devices, licences |
| [CUE] | `landscape/cue_and_report_design.md` | Cue loudness, fades, wake prompts, report capture, STT, earbuds |
| [SPEC] | `../ARCHITECTURE_AND_THRESHOLDS.md` | v1.1, 2026-10-27 (read only; it may still be under revision) |

**Primary-source keys used below** (full citations are in the notes named; DOIs as given there):

| Key | Citation | Note |
|---|---|---|
| P20 | Horowitz AH, Cunningham TJ, Maes P, Stickgold R (2020). Dormio: a targeted dream incubation device. *Conscious Cogn*. DOI 10.1016/j.concog.2020.102938, PMC7590944 | [P] |
| SR23 | Haar Horowitz A, Esfahany K, Vega Gálvez T, Maes P, Stickgold R (2023). Targeted dream incubation at sleep onset increases post-sleep creative performance. *Sci Rep* 13:7319. DOI 10.1038/s41598-023-31361-w | [P], [LIN] |
| FS24 | Bellaiche L, Haar Horowitz A, et al. (2024). Targeted dream incubation at a distance. *Front Sleep* 3:1258345. DOI 10.3389/frsle.2024.1258345 | [P] |
| FS26 | Haar Horowitz A, Konkoly KR, Carr M, Stickgold R, et al. (2026). TDI at sleep onset can influence later dream content in REM sleep. *Front Sleep* 5:1812535. DOI 10.3389/frsle.2026.1812535 | [P] |
| SM19 / PhD22 | Haar Horowitz A, SM thesis 2019 (DSpace 1721.1/124208); PhD thesis 2022 (DSpace 1721.1/151983) | [P] |
| CHI18 | Haar Horowitz A, Grover I, Reynolds-Cuéllar P, Breazeal C, Maes P (2018). Dormio: interfacing with dreams. *CHI'18 EA*. DOI 10.1145/3170427.3188403 | [LIN] |
| Y25 | Youngren WA, Haar Horowitz A, et al. (2025). Targeted dream incubation and dream self-efficacy. *Sleep Adv* 6:zpaf013. DOI 10.1093/sleepadvances/zpaf013 | [LIN] |
| MAKE22 | Vega T, Perry E, Haar A, Rosello O, Jain A (2022). Dormio Dream Incubator. *Make:* Vol. 81, makezine.com/projects/dormio-dream-incubator/ | [LIN] |
| W25 | Whitmore NW, …, Maes P. Slow wave entrainment using a smartwatch. bioRxiv 10.1101/2025.07.28.666762 (ABSTRACT ONLY) | [LIN] |
| LAC21 | Lacaux C, Andrillon T, et al. (2021). Sleep onset is a creative sweet spot. *Sci Adv* 7:eabj5866. DOI 10.1126/sciadv.abj5866 | [PHYS], [LIN] |
| HUA18 | Huang J, Ulke C, et al. (2018). *BMC Neurosci* 19:18. DOI 10.1186/s12868-018-0419-y | [PHYS] |
| CAR05 | Carrington MJ, et al. (2005). *J Appl Physiol* 98:468–476. DOI 10.1152/japplphysiol.00702.2004 (ABSTRACT ONLY) | [PHYS], [WEAR] |
| BUR99 | Burgess HJ, Kleiman J, Trinder J (1999). Cardiac activity during sleep onset. *Psychophysiology* 36:298–306 (ABSTRACT ONLY) | [PHYS] |
| SHI06 | Shinar Z, et al. (2006). *Auton Neurosci* 130:17–27 (ABSTRACT ONLY) | [PHYS] |
| DZ20 | de Zambotti M, et al. (2020). The falling asleep process in adolescents. *Sleep* 43:zsz312. DOI 10.1093/sleep/zsz312 | [WEAR] |
| SAN14 | Sano A, Picard RW, Stickgold R (2014). Wrist EDA during sleep. *Int J Psychophysiol* 94:382–389. DOI 10.1016/j.ijpsycho.2014.09.011 | [PHYS], [WEAR] |
| MAR13 | Marino M, et al. (2013). Wrist actigraphy vs PSG. *Sleep* 36:1747–1755. DOI 10.5665/sleep.3142 | [WEAR] |
| CHI21 | Chinoy ED, et al. (2021). Seven consumer sleep trackers vs PSG. *Sleep* 44:zsaa291. DOI 10.1093/sleep/zsaa291 | [WEAR] |
| WAL19 | Walch O, Huang Y, Forger D, Goldstein C (2019). *Sleep* 42:zsz180. DOI 10.1093/sleep/zsz180 | [WEAR] |
| KIM23 / LEE23 | Kim D, Joo EY, Choi SJ (2023) *J Sleep Med* 20:28–34, DOI 10.13078/jsm.230004; Lee T, et al. (2023) *JMIR mHealth uHealth* 11:e50983, DOI 10.2196/50983 | [WEAR] |
| GPPG25 | Park S, Zheng D, Lee U (2025). Galaxy Watch PPG dataset. *Sci Data* 12:892. DOI 10.1038/s41597-025-05152-z | [WEAR] |
| PRE14 | Prerau MJ, et al. (2014). Tracking the sleep onset process. *PLoS Comput Biol* 10:e1003866. DOI 10.1371/journal.pcbi.1003866 | [PHYS] |
| SCO21 | Scott H, et al. (2021). Accuracy of the THIM wearable for SOL. *J Clin Sleep Med* 17:973–981. DOI 10.5664/jcsm.9070 | [PHYS] |
| WAM10 | Wamsley EJ, et al. (2010). Cognitive replay at sleep onset: temporal dynamics. *Sleep* 33:59–68. DOI 10.1093/sleep/33.1.59 | [CUE] |
| SIC13 | Siclari F, et al. (2013). Serial awakening paradigm. *Front Psychol* 4:542. DOI 10.3389/fpsyg.2013.00542 | [CUE] |
| WHI22a | Whitmore NW, Harris JC, Kovach T, Paller KA (2022). Automated TMR (SleepStim). *J Sleep Res* e13731. DOI 10.1111/jsr.13731 | [CUE], [PROJ] |
| KON24 | Konkoly KR, et al. (2024). Provoking lucid dreams at home with sensory cues. *Conscious Cogn* 103759. DOI 10.1016/j.concog.2024.103759 | [CUE], [PROJ] |
| GOL19 | Göldi M, Rasch B (2019). TMR at home. *npj Sci Learn* 4:5. DOI 10.1038/s41539-019-0044-2 | [CUE] |
| KOE24 | Koenecke A, et al. (2024). Careless Whisper. arXiv:2402.08021 (ABSTRACT ONLY) | [CUE] |
| LAN25 | Lanthier M, et al. (2025). Portable EEG vs level 1 PSG. *Sleep Adv* 7(1):zpaf089. DOI 10.1093/sleepadvances/zpaf089 | [PROJ] |
| MAL24 | Mallett R, Mallela R (2024). Apple Watch TLR app. PsyArXiv 10.31234/osf.io/e9u8v | [PROJ] |
| S24 | Salvesen L, Capriglia E, Dresler M, Bernardi G (2024). Influencing dreams through sensory stimulation. *Sleep Med Rev* 74:101908. DOI 10.1016/j.smrv.2024.101908 | [LIN] |
| Others | ANT12, CRE15, KON21, WHI22b, PAP17, BRE24, WHO09, HVH15, FIC24, BON97, KRA00, ROD26, KUN20, FUJ19, MEN21, OGI89, BM82, ROS13, MIL22, KAN11: full citations and DOIs are in the source table of the note cited next to each use ([CUE] §1, [PHYS] §0, [WEAR] §1, [PROJ] §9) | — |
| Repos | `erikleanderberg/amulet-xpl` [AMU], `Ermond16/DynamicNapAlarm` [DNA], `andreykats/nap-time-ios` [NAP], `idealemu/TDI` [IDE], `nathanww/home-tmr` [HTMR], `rmallela26/TLR`, all on github.com, read through the GitHub API on 2026-10-27 | [PROJ], [LIN], [CUE] |

---

## 1. Executive summary

### 1.1 Where OpenTDI sits

- **In protocol, OpenTDI is one of the most MIT-faithful builds (INFERENCE from §2).** It keeps the full
  P20 cue sequence, including the "And were you asleep?" question that the MIT web code dropped ([LIN] §4).
  It uses the FS24 sensor-free timer values as a first-class mode ([SPEC] §4.1). It re-detects onset on
  every loop, as the papers describe, rather than only on the first loop as MIT's released code does
  ([C] §10).
- **In detection, OpenTDI is further from MIT than any glove build (INFERENCE).** Dormio's strongest
  channel was a behavioural task: a hand held closed on purpose opens as the task fails ([P] §2.1). OpenTDI
  replaces it with passive wrist stillness, which a relaxed but awake person also produces ([SPEC] §3).
  Wrist actigraphy's specificity for wake is 0.329 (FACT, MAR13 via [WEAR] §0).
- **Nothing open-source does the whole job (FACT, [PROJ] §0).** The search found no project that combines
  a consumer watch's PPG, IBI, EDA and accelerometer with real-time onset detection, a serial-awakening
  loop and transcription. The closest builds each cover one part. amulet-xpl runs a Dormio glove loop on
  an iPad. DynamicNapAlarm runs a Galaxy Watch onset detector that only starts a nap timer. nap-time-ios
  uses ring IBIs. SleepStim pairs a Fitbit with Android cueing in N3.
- **No study validates real-time sleep-onset detection on a consumer smartwatch against PSG (FACT, [WEAR]
  §0).** N1 is the hardest stage for every system: human scorers agree on it 63% of the time (ROS13,
  ABSTRACT ONLY). The Muse-S EEG headband reaches κ 0.41 for N1 (LAN25). A forehead-EEG consumer device
  detected 1% of N1 epochs (MIL22). So OpenTDI's detector is an untested hypothesis, as the spec already
  says ([SPEC] §17.3).
- **The bar to beat is the timer, not PSG (INFERENCE, [WEAR] §9.2).** MIT's sensor-free Dormio Light gave
  91% of 80 participants (73 of 80) at least one incorporation, against 92% in the lab (FACT, FS24, PMC12713799,
  Results). But in the same study, cueing during awake mind-wandering (FMT) gave as many incorporations as
  cueing during hypnagogia: "no main effect of condition [F(1, 80) = 0.61, p = 0.44] ... hypnagogia
  (M = 2.20, SD = 1.93) or FMT (M = 2.33, SD = 2.05)". Reports were typed afterwards, one free-text answer
  per condition covering all four trials (FACT, FS24 Methods, re-read 2026-10-27). So FS24 shows that cueing
  produces incorporation whether or not the person sleeps; it does not show that the timer catches
  hypnagogia (INFERENCE). Theme incorporation therefore cannot validate the detector. The detector earns its
  place only if it beats TIMED_CUES on the owner's own outcomes, with Halfway/Asleep-with-imagery as the
  primary measure (§5).
- **Licence (FACT, [SPEC] §1.3 item 5).** OpenTDI is GPL-3.0 with a §7 additional permission for linking
  the Samsung SDK. Among the projects found, code can be adapted only from those under an MIT, Apache-2.0
  or GPL-3.0-compatible licence, with their notices kept. That includes amulet-xpl, home-tmr,
  rmallela26/TLR, remind-eeg and goofi-pipe (MIT), SMACC and pyActigraphy (GPL-3.0), and GGIR
  (Apache-2.0, which is one-way compatible with GPL-3.0). The MIT Dormio repos, idealemu/TDI,
  DynamicNapAlarm and nap-time-ios have no licence ([PROJ] §5; [WEAR] §0, line 645). INFERENCE, not legal
  advice.

### 1.2 The ten most important lessons

1. **Quiet wakefulness is the main false-positive source.** It shows up as actigraphy specificity of 0.33
   (MAR13), research actigraphy (Actiwatch) underestimating SOL by 7.6 min with a wake specificity of 0.39
   (CHI21, SOL table and Table 8), and Galaxy Watch wake detection near 50% (KIM23, LEE23) ([WEAR] §0,
   §9.3). The consumer devices in CHI21 had small SOL biases (−3.1 to +4.4 min) but wide limits of
   agreement (e.g. Fitbit Alta HR −19.0 to +12.8 min) (FACT, CHI21 SOL table, PMC8120339, re-read
   2026-10-27). OpenTDI's stillness gate cannot fix this; only the autonomic signal and an early-onset
   guard can help. (strong)
2. **HR falls after lying down for reasons that are not sleep.** HUA18 found a time-on-task drop that "was
   most pronounced between the first and all subsequent blocks and could have been a consequence of
   postural change at the beginning of the experiment" (abstract). It is a single early drop of about
   1.5 bpm from the first to the second 30-min block (ignored condition, A1 65.63 → 64.04; B1 61.79 →
   60.26), after which HR is flat or rises; in the attended condition relaxed-wake HR barely moved (65.76 →
   65.21 over four blocks) (FACT, HUA18 Table 2, PMC5896037, re-read 2026-10-27). CAR05 (ABSTRACT ONLY):
   HR falls after lights out before theta ([PHYS] §1). So a baseline taken in the first minutes after lying
   down can make an HR trigger fire early; this supports a settle period, not a continuing drift
   correction (INFERENCE). (moderate)
3. **The 7% HR drop is a guess, and its timescale is the weak part.** HUA18 group means fell 3.5–8.8% from
   relaxed wake (A1) to drowsy (B1) or theta (B2/3) EEG across the two conditions (first 30-min block:
   ignored −3.84 and −4.21 bpm; attended −2.31 and −5.76 bpm). These are unpaired group means; the stages
   had different n (FACT, HUA18 Table 2 and footnotes; percentages are INFERENCE arithmetic). Dormio's
   5 BPM was a two-sided absolute delta, `Math.abs(meanHR - recentMeanHR)` (`dormio/js/script_wearable.js:701`),
   not a drop size. In P20 Fig. 4a, the HR change between Dormio-defined onset and first awakening averaged
   −2.89 ± 22.94 bpm for Sleep reports (about 4% at 70 bpm) (FACT, P20 Fig. 4 caption); that is the change
   after detection, not the triggering drop, but it shows small mean HR differences with very wide spread
   (INFERENCE). Every validated wrist algorithm integrates over minutes, not 15 s ([WEAR] §8). (weak)
4. **RMSSD has no onset-period evidence.** RSA and HF power did not change from wake to stable stage 2
   (BUR99, SHI06; both ABSTRACT ONLY) ([PHYS] §1). No MIT paper or code computes HRV ([LIN] §5 N5). In a
   seated baseline, raw Galaxy Watch PPG gave an RMSSD of 186 ms without denoising and 72 ms with Wiener
   denoising, against 42 ms from the Polar chest strap, so 1.7–4.5× depending on denoising (FACT, GPPG25
   Tables 5 and 6, PMC12119839, re-read 2026-10-27). That came from HeartPy on raw PPG, not from Samsung's
   `IBI_LIST` during sleep, which is untested ([WEAR] §5). (weak–moderate)
5. **No source tests wrist EDA as a wake-versus-N1 discriminator.** SAN14 compared sleep stages only, not
   quiet wake against N1. Its "4 ± 0% [of peaks] in NREM1" is a share of all peaks across the night and
   mostly reflects how little time is spent in N1 ("Most of the night was spent in NREM2 ... most of the
   peaks (55 ± 4%) occurred in NREM2"); the per-epoch rates are in its Fig. 6. Peaks were measured on the
   inner (ventral) wrist with a derivative threshold of 0.01 µS/s, chosen from 0.005–0.05 µS/s tested
   (FACT, SAN14, PMC4335672, re-read 2026-10-27); the Galaxy sensor is dorsal. "Slope ≤ 0 with no spikes"
   is true through most quiet wake ([SPEC] §3). Owner requirement 11 makes EDA bonus-only, and the bonus
   currently halves the hold (§3, row 11). (weak)
6. **N1 in a nap is short, and N2 removes the benefit.** N1-only sleepers in a 20 min nap spent
   1.35 ± 1.20 min in N1. N1 of at least 15 s gave 83% insight, against 30.6% after staying awake, and the
   N2 group lost it (LAC21) ([PHYS] §3). Direct task imagery in reports "reduced by more than half" after
   120 s of sleep (WAM10) ([CUE] §0). (moderate)
7. **MIT's highest per-report incorporation (57–70%) came with a 1–5 min post-onset delay.** The one study
   that woke people right after about 60 s of EEG-scored N1 got 40.6% (Y25), but that comparison is
   confounded ([LIN] §3.3). Lessons 6 and 7 pull the delay in opposite directions. Only the owner's own
   data can settle it, so the delay should be randomised and logged (§5). (weak–moderate)
8. **Every independent build added a hold, and every wrist or ring build uses AND (FACT, [PROJ] §4.1).**
   The holds are amulet-xpl 8 s, idealemu 10 s, DynamicNapAlarm 240 s and nap-time 60 s. None has been
   measured against PSG. (weak)
9. **Reports are short and soft, and STT can invent text.** Sleep-Incubation reports averaged
   14.0 ± 9.86 words (SR23 Table 1). MIT's own silence-stop code used 8 s of silence, 30 s minimum and
   120 s maximum ([C] §10). Whisper invented phrases for about 1% of speakers, mostly those with long pauses
   (KOE24, ABSTRACT ONLY) ([CUE] §0). (moderate)
10. **Cue loudness decides benefit or harm at home, and no MIT source gives a level.** In SleepStim, quiet
    cues helped and loud ones reversed the effect (WHI22a). In unsupervised home TMR, 19 of 66 people were
    woken by the cues (GOL19) ([CUE] §0). Every working home system calibrates the level per user. (moderate)

### 1.3 Where the surveys disagree, and how this file resolves it

| Topic | Positions | Resolution in this file (INFERENCE) |
|---|---|---|
| Hold length | 10–20 s ([LIN] rec. 3; [PROJ] rec. 1; [PHYS] §11 "20 s has the right order of magnitude") vs 60–120 s backdated ([WEAR] rec. 4) | Keep a short hold (20 s), lengthen HR smoothing to 30 s, and make the countdown itself a confirmation window by aborting it on an arousal (HR rise, movement). An EDA response would only be logged: an EDA abort would make EDA a blocker, which owner requirement 11 does not allow without the owner's sign-off. Offer 60 s as the LOW preset. The time budget decides it: N1 lasts about 1.35 min in naps (LAC21), so minutes of persistence plus a 1–5 min delay would often land in N2 |
| Onset → prompt delay | Variable 60–300 s, redrawn each loop ([LIN] rec. 1); 1–5 min plus a 60–120 s "direct" preset ([CUE] rec. 7); depends on the trigger type, 90 s for autonomic ([PHYS] rec. 7); add a 60 s preset ([PROJ]) | Keep a fixed 180 s default (the midpoint of P20's variable 1–5 min; P20 §2.3 does not report the distribution, so "mean 3 min" would be INFERENCE; MIT's wearable code defines `getRandomInt` at `dormio/js/script_wearable.js:1245` but this note found no call that draws the delay). Add `RANDOM_60_300` and use it during validation, so the journal yields a delay-versus-incorporation curve for this user |
| Re-baselining | Re-take each report window ([PROJ] rec. 2, from amulet-xpl); slow drift tracking with the baseline frozen while armed ([PHYS] rec. 2) | Phase 2: log a candidate re-baseline from the last 60 s before re-arming (not during speech, which raises HR), and decide by replay. Do not change the live rule without data |
| Detection on every loop | Papers: yes; MIT code: first loop only ([LIN] rec. 4) | Keep the spec's choice (every loop, O10). Add a no-onset fallback per loop: 900 s on the first loop, 600 s on later loops (GUESSED; §3 row 18). For scale: MIT iOS forced a timer wake 240 s after each re-incubation on loops 2+ (`FlowViewController.swift:596-600`), and FS26 measured 8.23 ± 2.32 min (range 6–12) between successive sleep-onset reports, which already includes the 1–5 min delay and the report |

---

## 2. Comparison matrix

Cells are FACT as reported in the cited note unless marked (I) for INFERENCE. "—" means not applicable or
not reported.

### 2.1 MIT family and OpenTDI

| Row | MIT Dormio lab (P20; PhD22 Exp 1) | SR23 (same Exp 1 cohort) | MIT open code (web wearable / iOS) | MIT sensor-free (FS24 "Dormio Light"; web timer) | OpenTDI as specified (v1.1) |
|---|---|---|---|---|---|
| Sensors | Finger PPG, finger flex on a hand held closed, ventral-wrist EDA, 100 Hz, raw ADC units ([P] §2.1–2.2) | Same ([P]) | Web: same glove (nRF52832). iOS: glove over BLE plus phone drop detection ([LIN] §3.2) | None ([P] §3.2) | Galaxy Watch8: HR + IBI (PPG), dorsal-wrist EDA in µS, 25 Hz accelerometer; Android fallback without IBI and EDA ([SPEC] §3, §8.12) |
| Onset rule | OR of single absolute deltas: HR > 5 BPM, EDA > 4, flex > 8; no hold ([P] §2.2) | Same | Web: \|ΔEDA\| ≥ 4 OR \|Δflex\| ≥ 5 OR \|ΔHR\| ≥ 6 BPM, abs (both directions), no hold. iOS: HR 15 BPM, EDA 10 ([C] §10; [LIN] §4) | Timer: random whole minute in a user window, e.g. 10–15 min ([P] §3.2) | Stillness gate AND (HR 15 s mean ≤ 0.93·μ OR RMSSD ≥ +15%), held 20 s; 10 s if an EDA, jerk or limp bonus holds ([SPEC] §8.6–8.7) |
| Baseline | Mean of the first 120 s lying down (P20 Fig. 1 says 3:00) ([P] §2.3) | Same | Web 3 min (options 3/5/7), mean only. iOS 300 s ([C] §10) | — | 180 s mean and SD; motion frames dropped; optional prior-day resting HR ([SPEC] §8.4–8.5) |
| Delay onset → prompt | Variable 1–5 min, distribution not reported ([P] §2.3; P20 §2.3) | Same | Web 3 min applied twice (≈ 6 min); iOS 15 s (experimental) or 30/60/90 s (guided) ([C] §10) | 3 min dwell ([P] §3.2) | Fixed 180 s (60–300, bezel); aborted by major movement ([SPEC] §6, §8.10) |
| Loops / session | Time box 45 min, about 5 awakenings (5.15 SI); detection each loop ([P] §2.3) | Same | Default 3 loops; after the first onset, wake-ups are timer-only ([C] §10) | 4 rounds over about 1 h, 7 min re-sleep (code default 3) ([P] §3.2) | maxCycles 4, sessionMax 60 min, detect each loop, 120 s refractory; auto-fallback to TIMED on sensor loss ([SPEC] §4.4, §8.13) |
| Cue design | Experimenter's voice; "computer speakers"; no level, fade or route reported ([LIN] §5 N6; [CUE] §0) | Same; verbatim instructions in SR23-SM ([P] §5) | User-recorded prompts; no fades ([C] §10) | Own-voice cues on the user's computer ([P] §3.2) | Pre-rendered TTS (own voice in Phase 3), −3 dBFS peak, 2 s log fade-in, gain caps, phone speaker default, −75 dBFS carrier on Bluetooth ([SPEC] §7) |
| Recording / report | No time limit; ends when the speaker stops; then "And were you asleep?" ([P] §2.1) | "Not subject to a time limit"; 14.0 ± 9.86 words per SI report (Table 1) ([LIN] §2.5) | Web fixed 30 s; iOS 8 s silence, 30–120 s ([C] §10) | 60 s fixed; typed reports in the study ([P] §3.2; [LIN] §3.1) | Phone mic; 4 s silence / 45 s max / 15 s no speech; on-device STT; sleep-state question parsed from STT ([SPEC] §7.8–7.9) |
| Logging | Lab recordings, transcribed by hand ([LIN] §0) | Hand transcription; one condition-blind rater ([LIN] §0) | Raw samples plus EVENT markers to text; zip ([C] §11) | Web form | Full 1 Hz packet, engine, input and transition CSVs on both devices; replay harness; journal with JSON, WAV and transcript per awakening ([SPEC] §12) |
| Validation evidence | n = 49 analysed; 67% of SI reports contained "tree"; thresholds fit on 5 pilots, called provisional ([P] §2.3; [LIN] §3.1) | Creativity Index SI 0.79 ± 0.50 vs SN −0.07 ± 0.70, p = 0.009; groups of 12–13 ([LIN] §0) | None for the code; MAKE22 (2025): record function broken, repo unmaintained ([LIN] §0) | n = 80; 91% with ≥ 1 incorporation, vs 92% lab, p = 0.91 ([P] §3.2) | None yet; arithmetic proofs on synthetic curves only ([SPEC] §13.4) |
| Openness / licence | Paper open access (PMC) | Open access | No licence on any repo ([C] §7) | No licence; the live site matches repo HEAD ([LIN] §0) | GPL-3.0 + §7 Samsung linking permission; SDK AAR never committed ([SPEC] §1.3) |

### 2.2 Independent open-source builds

| Row | idealemu/TDI [IDE] | amulet-xpl [AMU] | DynamicNapAlarm [DNA] | nap-time-ios [NAP] |
|---|---|---|---|---|
| Sensors | One FSR/flex channel ([LIN] §3.2) | Finger flex/FSR, finger PPG, XIAO nRF52840, wrist LRA ([PROJ] §2.1) | Galaxy Watch 7, platform accelerometer (~5 Hz) and `TYPE_HEART_RATE`; not the Samsung SDK ([PROJ] §2.1) | Colmi R02 ring IBIs over BLE ([PROJ] §2.1) |
| Onset rule | Rise > 3.5% above baseline, then a stable new level for 10 s ([LIN] §3.2) | max(\|ΔHR\|/5 bpm, \|Δflex\|/8 kΩ) ≥ 1, held 8 s, 4 s smoothing, OR ([PROJ] §4.1) | Still (mean \|Δ\|a\|\| < 0.06 m/s²) for 240 s AND smoothed HR ≤ baseline − 4 bpm ([PROJ] §2.1) | AND of RMSSD > μ + 1 SD, HR < 0.95·μ, LF/HF < μ, for 2 consecutive 30 s epochs ([PROJ] §2.1) |
| Baseline | 90 s mean | First 120 s mean and SD; re-taken in every report window | Median of EMA-smoothed HR over the first 150 s | 300 s |
| Delay onset → prompt | 3 min fixed | 1 min fixed | Starts a 10–60 min nap timer (not TDI) | Alarm 20 min after onset (not TDI) |
| Loops | 3; 10 s grace after each | Serial awakenings for about 1 h; 60 s refractory + 30 s settle | One-shot | One-shot |
| Cue design | Own-voice prompts, `noisereduce`; PC playback ([CUE] §7) | Escalating ~5 s haptic pulse train, then a pre-rendered spoken prompt ([PROJ] §4.2) | Vibration or sound alarm | Gentle alarm file |
| Recording / report | 60 s fixed | Spoken "Were you asleep?" answer; report window used for re-baselining | — | — |
| Logging | Raw log for replay (`LOGGING_DATA`) | One file per session on one clock, 50 Hz; zip export | Not described | Saved sessions |
| Validation evidence | ANECDOTAL "successful replication"; no data | Synthetic only; author reports one fixed-baseline night gave "98 cues, 92 of them fired at the refractory floor" (ANECDOTAL) ([PROJ] §0) | None ("Expect to tune them") | None |
| Licence | None: do not copy | **MIT**: can be adapted with notice | None: do not copy | None: do not copy |

### 2.3 Devices, protocols and apps

| Row | Edison bottle (LAC21) | THIM ring (SCO21) | SleepStim (WHI22a; [HTMR]) | Northwestern home TLR app (KON24) |
|---|---|---|---|---|
| Sensors | 55 g bottle held in the hand, plus PSG ([PHYS] §3.1) | Finger ring that vibrates and records taps ([PHYS] §1) | Fitbit HR, 3-axis accelerometer and gyro at 1 Hz → Android phone ([PROJ] §2.2) | Phone accelerometer, phone face down by the pillow ([PROJ] §3) |
| Onset rule | Behavioural: the bottle drops | Behavioural: a vibration about every 30 s; 2 missed taps = sleep | Neural net on the last 240 s → p(N3); cue if mean p ≥ 0.90 and latest ≥ 0.85 | No staging; cues start 6 h after pre-sleep training |
| Baseline | — | Per-user tap threshold | — | Per-user perceptual threshold test on night 1 |
| Delay onset → prompt | 0: the drop itself wakes the person | — (measures SOL) | — (cues during N3) | Cues every 20–40 s |
| Loops | Free, within a 20 min rest | — | All night; `MAX_STIM` 2000 s; none after 3 h | All night; pause 5 min (Exp 2: 1 min) after movement, 45 min after a reported awakening |
| Cue design | — | Faint vibration | White-noise bed; cue level ramps from 0; on arousal: pause, lower the gain, lower the ceiling ([PROJ] §4.2) | Starts at the user's threshold, +0.16% per cue; Exp 2 caps at the level the user reports as incorporated ([PROJ] §3) |
| Recording / report | Spoken report right after the drop | — | — | Post-awakening question, then a report form |
| Logging | PSG + video | Device | Raw Fitbit data and p(N3) to file; live telemetry to a lab server the README calls "not secured" ([PROJ] §2.2) | App |
| Validation evidence | n = 103: 41% of 63 drops after scored N1, 78% after microsleeps; hypnagogia on 61.7% of drops vs 22.5% without ([PHYS] §3.2) | SOL within 0.08–0.57 min of PSG; false "asleep" calls in 17–31% of trials, mostly unregistered taps ([PHYS] §1) | 34.7% of cues in N3 (21.1% of sleep), about 16% in wake; low intensity better than high ([PROJ] §3) | Lucid dreams 0.74 → 2.11 per week (n = 19, p = .007); participants "often awoken by cues" ([PROJ] §0) |
| Openness / licence | Paper CC BY ([PHYS] §0) | Commercial device; openness not checked here | **MIT** code | Closed (MIT App Inventor) |

### 2.4 What the matrix shows (INFERENCE)

- OpenTDI is the only system in either table that combines passive wrist sensing, a short post-detection
  delay and a spoken report loop. Each of those three pieces exists elsewhere, but never together.
- The systems with **published validation** of onset timing are behavioural (LAC21 bottle, THIM taps) or
  EEG-based. The passive wearable systems (SleepStim, MAL24) put a large share of cues in the wrong
  stage (about 16% in wake for SleepStim; 34% REM precision for MAL24) ([PROJ] §4.3).
- OpenTDI's logging and replay are stronger than any MIT build's. That is the asset that can turn its
  guessed thresholds into measured ones.

---

## 3. Parameter evidence table

Scope: every numeric default in [SPEC] that the literature can inform. Pure engineering constants
(Data Layer timeouts, flush intervals, CSV retention, packet-gap rules) are left out, because no sleep
literature bears on them. "Label after" is the label this file supports, given the recommendation.
Any recommendation that changes a value or rule fixed in `CLAUDE.md`, or touches an owner requirement in
[SPEC] (A1–G23, which outrank the D-decisions, [SPEC] line 5), is tagged **Conflicts with CLAUDE.md:
owner decision** and must not be adopted without the owner's sign-off.

| # | Parameter ([SPEC] §) | Current | What the literature says | Recommended | Strength | Label after |
|---|---|---|---|---|---|---|
| 1 | Baseline window W (§6, §8.4) | 180 s from Start | P20 text 120 s; P20 Fig. 1 and web code 3 min; iOS 300 s; idealemu 90 s; amulet-xpl 120 s; DNA 150 s; NAP 300 s ([P] §2.3; [PROJ] §4.1) | **120 s** (P20 text) after a settle period (row 2), so the total stays 180 s. Conflicts with CLAUDE.md §4.2 ("Baseline Acquisition (t = 0s..180s)"): owner decision | moderate | SOURCED (P20 §2.2) |
| 2 | Settle before the baseline (none) | 0 | HR drops early after lying down, most between the first and later 30-min blocks, attributed to posture (HUA18 abstract, Table 2); HR falls after lights out (CAR05 ABSTRACT ONLY) ([PHYS] §1); [WEAR] rec. 5: start after 60–120 s of settled stillness | **60 s** discarded after Start (new field `baselineSettleS`, 0–180). Conflicts with CLAUDE.md §4.2: owner decision | moderate (early drop) / weak (value) | GUESSED (lit-consistent) |
| 3 | Baseline statistic (§8.4) | Mean; SD floored at 1 bpm | Dormio: arithmetic mean ([C] §10). DNA: median HR. [WEAR] rec. 5: median/MAD (as Oura and WAL19 normalise robustly) | **Median** for μ_HR (one line; resistant to an early fidget). Keep σ for logging only, since no rule uses it for HR | weak | GUESSED |
| 4 | Re-baselining (none) | Never | amulet-xpl re-baselines each report window after a drift failure (ANECDOTAL); HUA18 shows one early drop of about 1.5 bpm from the first to the second 30-min block within an EEG stage, then flat or rising HR, not a continuing drift (Table 2) ([PHYS] §7) | Log a candidate re-baseline from the last 60 s before each re-arm; decide by replay (§5 step 5). No live change yet | weak | GUESSED |
| 5 | Early-onset guard (none) | Trigger allowed from the first frame after calibration | MIT names false onsets in the first minutes, "safeguarded against by a human eye" (SM19 Limitations) ([LIN] §0). MIT's web code offers a latency window that forces onset at its max ([LIN] §4) | **120 s** after the initial incubation cue ends (`earlyGuardS`, 0–600). Cost: P20's first onset was 10.3 ± 6.4 min; if normal, about 20% of first onsets fall before 5 min (z = (5 − 10.3)/6.4 = −0.83) (INFERENCE, the normality assumption is crude) | moderate (need) / weak (value) | GUESSED |
| 6 | HR moving-average window (§6) | 15 s mean | Dormio web about 10 s ([C] §10); amulet-xpl 15 s; [PHYS] rec. 3 about 30 s; [WEAR] rec. 3 60 s median; HR falls about 0.53 bpm per 30 s before stable sleep (DZ20), so a 15 s mean adds little but noise | **30 s** (range 10–60). The lag rises from about 7 s to about 15 s (half the window, INFERENCE). Conflicts with CLAUDE.md §4.3 ("Current 15-second moving average of HR"): owner decision | weak | GUESSED (lit-consistent) |
| 7 | θ_drop, HR drop (§8.11) | 0.07 (LOW 0.10, HIGH 0.05; 0.04–0.12) | HUA18 group means fell 3.5–8.8% (−2.31 to −5.76 bpm), relaxed wake → drowsy/theta, first 30-min block, unpaired (Table 2); Dormio's 5 BPM was a two-sided absolute delta (`script_wearable.js:701`), not a drop size; DNA 4 bpm; NAP 5%; P20 Fig. 4a (HR change from Dormio-defined onset to first awakening): Sleep −2.89 ± 22.94 vs Wake +10.01 ± 18.7 BPM, d ≈ 0.62 (INFERENCE: 12.9 / pooled SD 20.9) | Leave for replay tuning. Keep 0.07 only as a placeholder; consider the 5% HIGH preset (matches NAP) as the default. Expect poor separation on its own. The 7% figure is in CLAUDE.md §4.3, so changing it is an owner decision | weak | GUESSED |
| 8 | θ_rmssd, RMSSD rise, OR'd into the required signal A (§8.6, §8.11) | +15% (0.25/0.15/0.10) | No RSA/HF change at onset (BUR99, SHI06; ABSTRACT ONLY); no HRV anywhere in Dormio ([LIN] §5 N5); raw Galaxy PPG RMSSD 1.7–4.5× the chest-strap value depending on denoising (GPPG25 Tables 5–6: HeartPy on raw PPG, seated baseline, not `IBI_LIST` in sleep); NAP uses RMSSD in AND, never alone | **Remove R_sig from A** (A = H_sig) if an on-device check of `IBI_LIST` RMSSD against a chest strap at rest shows inflation; keep RMSSD as a logged feature and in the confidence display either way. Conflicts with CLAUDE.md §4: owner decision | weak–moderate | n/a (not a trigger) |
| 9 | RMSSD window; IBI rules (§8.2) | 60 s; 300–2000 ms; ≤ 20% from the median of the last 7 | NAP rejects IBIs outside 300–2000 ms ([PROJ] §0); [WEAR] rec. 7: at least 60 s of cleaned IBIs | Keep | weak | 300–2000 ms: SOURCED (NAP code); rest GUESSED |
| 10 | Stillness gate (§8.6, §8.11) | 30 s window, mean stillness ≥ 0.80, ≤ 2 moving s | Necessary, not sufficient: MAR13 specificity 0.33, CHI21 0.39 ([WEAR] rec. 2); DNA 240–360 s would often fire after N1 has ended (LAC21 N1 1.35 min) ([PROJ] rec. 4); [WEAR] rec. 2: calibrate to the baseline's noise floor | Keep 30 s. Seed `f` (noise floor) from the logged p95 after §15 #10 | strong (insufficiency) / weak (values) | GUESSED |
| 11 | holdS / holdShortS, and which bonuses shorten it (§8.7, §8.11) | 20 s / 10 s; EDA, jerk or limp shortens | Holds in other builds: 8 s (AMU), 10 s (IDE), 60 s (NAP), 240 s (DNA); none tested. Microsleeps 3–15 s, Hori H4 bouts < 30 s, imagery in the last 20 s before a report (n = 1) ([PHYS] §11). **E ("slope ≤ 0, no spike") holds through most quiet wake** ([SPEC] §3, owner D11), so with EDA present holdReq is in practice 10 s | **Keep holdS 20 s**; LOW preset 60 s. Proposal: **remove E from the hold-shortening bonuses** and keep EDA as a logged, confidence-only feature; keep jerk and limp as confidence-only until replay shows they help. EDA's bonus role is owner requirement 11 ([SPEC] lines 207–209) and the 10 s all-three hold is CLAUDE.md §4.3, so this conflicts with CLAUDE.md: owner decision | weak | GUESSED |
| 12 | EDA rule (§8.6) | 30 s slope ≤ 0 and no 1 s step > max(1.5σ_E, 0.02 µS) | SAN14 compared sleep stages only, not quiet wake vs N1; its 4% of peaks in NREM1 is a share of the night's peaks that mostly reflects time spent in N1 (per-epoch rates in its Fig. 6); its 0.01 µS/s peak criterion was chosen from 0.005–0.05 µS/s on an inner-wrist Q sensor, not the dorsal Galaxy sensor; sweat drift inverts tonic patterns (PAR26, preprint, ABSTRACT ONLY) ([WEAR] §6). No source tests wrist SCRs as a wake-vs-N1 discriminator | Default: keep EDA bonus-only per owner requirement 11, or confidence-only (row 11); never let it block or abort a trigger. Log SCRs (step > 0.01 µS/s, and the current max(1.5σ, 0.02 µS)) per frame so replay can test them. **Proposal needing the owner's explicit sign-off:** an SCR resets the hold and aborts the countdown (arousal veto). That would make EDA a blocker, which owner requirement 11 does not allow | weak | GUESSED; 0.01 µS/s is SAN14's analysis criterion for a different sensor and site, so GUESSED for this use |
| 13 | Jerk detector (§8.1) | J_G 0.30 g, ≤ 500 ms, 10 s still before, 3 s after | 53–79% of students report hypnic jerks (FIC24), timing within sleep onset not verified; no wearable validation ([PHYS] §6.7) | Keep as a logged event and a confidence bonus only | anecdotal | GUESSED |
| 14 | Limp drift (§8.1) | 3.0°, gravity mode only | Samsung javadoc: "Accelerometer data doesn't include gravity" ([SPEC] §8.1); van Hees uses > 5° angle change over ≥ 5 min (HVH15) ([WEAR] §8) | Keep, bonus only, after §15 #5 | weak | GUESSED |
| 15 | Countdown, onset → prompt (§6) | Fixed 180 s (60–300) | P20 lab "variable timer" 1–5 min, distribution not reported (P20 §2.3); app presets 1 / 1.5 / 3 min; v1 and Dormio Light 3 min ([P]); direct imagery halves after 120 s of sleep (WAM10); N1 1.35 min, N2 loses the benefit (LAC21); Y25 immediate gave 40.6% (confounded) ([LIN] §3.3); amulet-xpl 1 min | **Keep 180 s fixed** for normal use; add `countdownMode = RANDOM_60_300` (uniform by OpenTDI's choice, redrawn each cycle, logged) for the validation phase (O4) | moderate (range) / weak (best value) | 180 s SOURCED (app preset, PhD22 p.56); range SOURCED (P20) |
| 16 | Countdown abort (§8.10) | ≥ 3 moving s in the last 5 s | No source has a countdown abort; HR rises at arousals and returns within about 12 beats (BUR99, ABSTRACT ONLY); HR accelerates ≥ 10 beats before an EEG arousal (BON97, ABSTRACT ONLY) ([PHYS] §7) | Keep the movement rule; **add an HR-rise abort**: 5 s HR mean back above μ_HR,eff, i.e. the drop fully reversed (GUESSED form). No EDA abort by default (row 12) | moderate (phenomenon) / weak (values) | GUESSED |
| 17 | Refractory after a cycle (§6) | 120 s | No source gives a refractory before detection may fire again. MIT iOS `waitForOnsetTime` (default 240, `AppDelegate.swift:30`) is the opposite: a **maximum** wait that forces the next onset, `max(waitForOnsetTime, 45.0)` then `sleepDetected(trigger: OnsetTrigger.TIMER)` (`FlowViewController.swift:596-600`), exported as `maxTimeBetweenSleeps` (lines 751-752); sensor detection runs only while `numOnsets == 0` (line 479). Dormio Light 7 min re-sleep; FS26 8.23 ± 2.32 min between successive sleep-onset reports (includes the 1–5 min delay and the report); amulet-xpl 60 s + 30 s settle ([CUE] §8.1; [LIN] §3.2; [C] §10) | **Keep 120 s** until replay shows how soon false triggers follow a re-incubation cue | weak | GUESSED |
| 18 | No-onset timeout per loop (none) | None (only sensor-loss fallback) | MIT iOS forces a timer wake 240 s after each re-incubation on loops 2+, with no sensor detection on those loops (row 17); MIT web forces onset at the latency window's max ([LIN] §4); FS26 8.23 ± 2.32 min, range 6–12, between successive sleep-onset reports, including the 1–5 min delay; P20 first onset 10.3 ± 6.4 min ([P] §2.3) | `noOnsetFallbackS`: if no trigger within 900 s of arming (first loop) or 600 s (later loops), run a timed dwell for that loop and log `NO_ONSET_TIMER` | moderate (need) / weak (values) | GUESSED (lit-consistent) |
| 19 | maxCycles / sessionMaxMin (§9.5) | 4 / 60 min for both modes | Lab DETECTION: 45 min time box, 5.15 awakenings ([P]); FS24 TIMED: 4 rounds in about 1 h; Y25 1 h; WAM10 at most 10 prompts per hour ([CUE] §4) | DETECTION **6 / 45 min**; TIMED **4 / 60 min** (mode-specific defaults; closes O12) | moderate | SOURCED (P20 time box; FS24 rounds); 6 GUESSED |
| 20 | TIMED values (§4.1) | 10–15 min, 180 s dwell, 420 s, 4 loops, 60 s report | FS24 study values; P20 first onset 10.3 ± 6.4 min is consistent with 10–15 min ([P]) | Keep | moderate | SOURCED |
| 21 | Cue level: userGain 0.5, caps speaker 0.6 / Bluetooth 0.3 (§7.7) | Fixed gains | No MIT dB, fade or route data ([LIN] §5 N6). TMR cues 35–50 dB SPL over 37–45 dB noise (ANT12, CRE15, KON21, WHI22b); WHO EEG-awakening threshold LAmax 35 dB, "much lower" in stages 1–2; home systems calibrate per user (KON24 threshold test; WHI22a noise anchor) ([CUE] §2) | Add a lying-down **threshold calibration** T (Phase 1 simple form). Theme cue ≈ T + 10 dB, wake prompt ≈ T + 20 dB, at most one +6 dB replay; keep the caps | moderate (calibrate) / weak (offsets) | GUESSED |
| 22 | Fade-in (§7.4) | 2000 ms, −60 → 0 dB on the speech itself | No MIT fade ([C] §10); KON24 Exp 2 ~3 s fades on harp melodies; GOL19 ramp; gradual arousal gave more thought-like reports in 1960s night studies (S24) ([CUE] §2.5; [LIN] rec. 8). The spec notes the first ≈ 1 s of speech plays below −30 dB (O6) | **Keep the 2000 ms log fade on every voice prompt** (CLAUDE.md §5.2) and add O6 option 3: a soft chime under the fade ("prepend a soft chime under the fade", [SPEC] O6), so the first, quiet second of the fade carries a non-speech sound; optional 0.5 s fade-out. Removing or shortening the voice fade would conflict with CLAUDE.md: owner decision | weak–moderate | GUESSED |
| 23 | Carrier −75 dBFS, 10 s lead (§7.5) | Bluetooth only | No sleep source; continuous white noise beds were standard in TMR (CRE15, WHI22a/b, KON21) ([CUE] rec. 4) | Keep the measurement procedure; add an optional user-set noise bed (off by default), which also keeps the output awake | weak | GUESSED |
| 24 | Recording stop rules (§7.8) | 4 s silence, 45 s max, 15 s no speech | MIT iOS 8 s silence, 30 s min, 120 s max; lab unlimited; SI reports 14 words; halting speech; STT hallucination over long pauses (KOE24) ([CUE] §8.1) | **hangover 8000 ms, max 120 000 ms, no-speech 20 000 ms**; optional one soft re-prompt. Conflicts with CLAUDE.md §3 ("4s silence or 45s max"): owner decision | moderate | 8 s and 120 s SOURCED (MIT iOS, [C] §10); 20 s GUESSED |
| 25 | Cycle-stage timeout (§6, §8.14) | 180 s | Cross-rule `cycleStageTimeoutS ≥ (maxMs + answerMaxMs)/1000 + 60` ([SPEC] §8.14) | **240 s**, only if the owner adopts row 24: with row 24, 120 + 8 + 60 = 188 s, so 180 s would fail `validate()` (arithmetic) | — (consequence) | GUESSED |
| 26 | Sleep-state answer (§7.8) | 8 / 1.5 / 6 s | P20 three-way answer; N1 is often reported as wake (BM82, ABSTRACT ONLY); FS26 notes a demand effect ([WEAR] §10; [CUE] §8.1) | Keep the windows; label the answer "self-report, biased" in the journal | moderate | GUESSED windows; question SOURCED |
| 27 | Confidence score and 0.85 marker (§8.9) | Weighted sum; ≥ 0.85 at every trigger | A continuous wake-probability model fit behaviour much better than a single onset point (PRE14) ([PHYS] rec. 1) | Keep as a display and log value. Recompute if the owner adopts rows 8 and 11 (with R removed and E no longer shortening the hold, the components change) | weak | GUESSED |
| 28 | Prior-day HR weight (§8.5) | 0.3 | No source ([P] §8 item 10). Per-user models beat population models in drowsiness detection (KUN20) ([WEAR] §0) | Keep, off by default | weak | GUESSED |
| 29 | Skin temperature (not logged) | — | Distal warming predicts SOL (KRA00, ABSTRACT ONLY); wrist temperature rises slightly before and markedly after onset (ROD26, ABSTRACT ONLY); minutes-scale ([PHYS] §6.4). SDK 1.4.1 lists `SKIN_TEMPERATURE_CONTINUOUS` (FACT, `samsung_sdk_1.4.1_verified_api.md`) | Log it as a covariate if `getSupportHealthTrackerTypes()` lists it on the SM-L500; never a trigger | weak | n/a |
| 30 | End chime ×3; Phase 1 60 s report (§6) | — | MIT web gong ×3; FS24 60 s ([P]) | Keep | moderate | SOURCED |

**Net effect of rows 1–18 on timing (INFERENCE, arithmetic).** With the recommended values, the earliest
possible trigger comes 60 s (settle) + 120 s (baseline) + the cue length + 120 s (guard) ≈ 5 min after
Start. A trigger fires about 15 s (30 s mean lag) + 19 s (20 s hold) after the true HR crossing. The
prompt then comes 180 s later, so about 3.6 min after the crossing. Under the current spec the figure is
7 + 19 + 180 s ≈ 3.4 min ([SPEC] §8.14), so the recommendations barely change prompt timing. If the owner
adopts rows 8 and 11, they mainly remove the RMSSD route to an early trigger and EDA's hold-shortening.

---

## 4. Gaps: what OpenTDI does that no prior work validated

| # | OpenTDI feature | Prior evidence | Risk | Mitigation already in [SPEC] / proposed here |
|---|---|---|---|---|
| G1 | **Wrist-only, passive onset detection in real time** | None. Every consumer validation scores 30 s epochs offline and merges N1 into light sleep ([WEAR] §0). The closest MIT precedent (W25) stages deep sleep on a watch (ABSTRACT ONLY) | High: false triggers in quiet wake; prompts arrive before sleep in a meaningful share of cycles ([WEAR] §9.2) | Replay, sleep-state labels, TIMED_CUES control (spec); quiet-wake control sessions, early guard, A/B (§5) |
| G2 | Stillness replacing Dormio's closed-hand task | MIT's flex measured the failure of an active task; stillness measures absence of movement ([SPEC] §3). Wrist specificity for wake 0.33 (MAR13) | High: the main MIT channel has no wrist equivalent | Stillness is a gate only (spec); an optional arm-raised or held-object task (O8; §6 idea 14) |
| G3 | Percentage HR drop over a 15 s window, held 20 s | Size within HUA18's 3.5–8.8% group-mean range (unpaired); Dormio used a two-sided absolute delta, not a drop; timescale 1–2 orders shorter than any validated method ([WEAR] §8) | Medium: noisy triggers near the threshold; integer HR quantisation (1 bpm ≈ 1.4% at 70 bpm, [SPEC] §17.3) | 30 s smoothing, median baseline, arousal abort (rows 3, 6, 16) |
| G4 | RMSSD from Samsung `IBI_LIST` as a trigger | No onset evidence (BUR99, SHI06); raw Galaxy PPG RMSSD 1.7–4.5× a chest strap depending on denoising (GPPG25 Tables 5–6, HeartPy, seated); Samsung's IBIs untested | Medium: artefact-driven triggers | Remove from the trigger if the on-device `IBI_LIST` check shows inflation (row 8; owner decision) |
| G5 | Dorsal-wrist EDA in µS from the Galaxy Watch8 | No validation of Galaxy EDA during rest or sleep found ([WEAR] §12); Dormio's EDA was ventral and in raw units ([P] §2.2); no source tests wrist SCRs for wake vs N1 (SAN14 compared sleep stages only) | Medium as a bonus (it halves the hold); a veto would add the risk of blocking true onsets on an untested signal | Bonus or confidence-only, logged for replay (rows 11–12); a veto only with the owner's sign-off |
| G6 | Jerk and limp bonuses | No wearable validation; jerk timing not verified ([PHYS] §6.7); limp needs gravity, which the SDK may exclude ([SPEC] §17.1) | Low (bonus only), but they shorten the hold | Confidence-only (row 11) |
| G7 | Unsupervised home use | Every MIT lab study had an experimenter in the room who caught false onsets (SM19). FS24 ran at home with no sensors | Medium: no human check of early triggers | Early guard (row 5); no-onset fallback (row 18) |
| G8 | Anti-startle fade and −75 dBFS carrier | No MIT source gives any level, fade or route ([LIN] §5 N6). The carrier level has no source at all ([SPEC] §7.5) | Medium: a prompt too soft to wake, or a pop | Earbud test (spec §7.5); chime under the 2 s fade (row 22); threshold calibration (row 21) |
| G9 | On-device STT of drowsy speech, and the sleep-state answer parsed from it | No STT validated on dream reports ([CUE] §0); hallucination over long pauses (KOE24, ABSTRACT ONLY) | Medium: wrong incorporation counts; a wrong sleep-state label feeds tuning | WAV primary (spec); morning correction and flag text over silence ([CUE] rec. 12); manual label check during validation (§5) |
| G10 | One-session baseline over a 45–60 min session | HUA18: one early drop of about 1.5 bpm from the first to the second 30-min block, attributed to posture, then flat or rising HR (Table 2; abstract); amulet-xpl drift failure (ANECDOTAL) | Low–medium: mainly an early post-lying-down drop, which the settle period (row 2) addresses; whether later loops drift below the baseline is untested | Settle period (row 2); log the candidate re-baseline (row 4); compare per-loop false-trigger rates in replay |
| G11 | Screen-off batching and late data | Samsung batches screen-off data ([SPEC] §11.3) | Medium: stale windows, delayed triggers | 5 s `flush()`, timestamp-binned packets, lateness deadline (spec §8.2) |
| G12 | Consumer earbuds in sleep modes | Soundcore Sleep A20 "Sleep mode … cannot play Bluetooth music" (vendor FAQ); Bose Sleepbuds II reportedly cannot stream (secondary only) ([CUE] §6) | Medium for `BLUETOOTH_SLEEP_AUDIO` | Phone speaker default (spec); 10 min lie-down test per earbud ([CUE] rec. 13) |

---

## 5. Recommended validation protocol for the owner (n-of-1)

Built mainly on [WEAR] §10, with MIT's outcome measures ([LIN] rec. 12) and the physiology confounds
([PHYS] rec. 14). INFERENCE throughout, except the cited facts. Equipment: the watch, the phone, a light
plastic bottle, and optionally a Bluetooth clicker or an EEG headband.

**What to log in every session** (most of it is already in [SPEC] §12):
- Everything the spec logs: packets, engine frames, inputs, transitions, audio events and the journal.
- New, a short pre-session form written to `p_config.csv`: time of day, nap or night, minutes since
  waking, caffeine in the last 6 h, a 1–9 sleepiness rating, posture, eye mask yes/no, medication
  (SSRI use is on [PHYS]'s confound list, rec. 14). Basis: confounds that shift onset physiology (HUA18, CAR05,
  Clark & Landolt 2017 ABSTRACT ONLY).
- Per awakening: the sleep-state answer, a manual "imagery? involuntary or bizarre?" check in the
  morning (LAC21's hypnagogia definition), the transcript as corrected, and direct and indirect theme
  counts (SR23 scoring).
- The drawn countdown value when `RANDOM_60_300` is on.

**How to label onset** (strongest available reference first):
1. *EEG headband, optional.* Score 30 s epochs; onset = 2 consecutive N1 epochs, as in Y25 and FS26
   ([LIN] rec. 12). ZMax sensitivity against PSG was 68.3% (cited in Y25), and Muse-S N1 κ is 0.41 (LAN25),
   so the reference is itself imperfect.
2. *Bottle drop (LAC21).* Hold a light bottle in the **non-watch** hand over the bed edge. The phone
   microphone timestamps the thud. A drop marks about 1 min of accumulated microsleep and wakes the
   sleeper.
3. *Responsiveness probe (OGI89; THIM-style per SCO21).* A faint tone or phone vibration at random 20–40 s
   intervals. Answer with the non-watch hand. Behavioural onset = the first of 2 consecutive misses. Run
   these sessions separately, because probing may delay sleep.
4. *Self-report.* MIT's Awake / Halfway / Asleep answer. N1 is often reported as awake (BM82).

**Sessions, in order:**

| Step | What | How many | Measures |
|---|---|---|---|
| 0 | Hardware checks [SPEC] §15 #5–10 (gravity, rates, batching, noise floor) | once | Sets `gravityMode`, f, M |
| 1 | Offline replay of the detector on PhysioNet `sleep-accel` (WAL19 data, ODC-By, N1-labelled; Apple Watch HR is sparse and includes gravity; no EDA) | all records | Trigger time vs first N1, first N2 and LPS; Bland–Altman (MEN21 framework). Keep the data out of the repo; add a download script |
| 2 | Quiet-wake control: same posture, mask and time of day, but stay awake, verified by the probe | ≥ 5 × 20 min | False triggers per hour (the FUJ19 metric; FUJ19 reported 1.7 per hour for driver drowsiness, ABSTRACT ONLY) |
| 3 | Labelled naps, detector logging only, no TDI prompts (or timer prompts) | 10–20 | Δt = t_trigger − t_reference; share of premature triggers; misses (no trigger, or > 5 min after the reference) |
| 4 | Samsung reference: read `SleepSessionRecord` from Health Connect after naps and at night | every session | Weak reference only: wake detection about 50% (KIM23, LEE23); naps may not be recorded (NOT VERIFIED) |
| 5 | Replay tuning: rerun steps 2–3 with other presets, rows 3–12 of §3, and the candidate re-baseline | offline | Pick values on steps 2–3; freeze them |
| 6 | A/B: alternate DETECTION and TIMED_CUES full TDI sessions, matched for time of day, with `RANDOM_60_300` on | ≥ 10 per arm | Primary: share of prompts answered Halfway/Asleep **with** hypnagogic content. Secondary: % reports with a direct theme hit, ≥ 1 incorporation per session, awakenings per 45 min |
| 7 | Held-out check: new sessions with frozen parameters | ≥ 5 per arm | Same measures; no tuning |

Total: about 40–50 sessions. At one nap a day, that is roughly two months (INFERENCE). Space sessions
out: in SIC13, recall fell on the 2nd and 3rd consecutive nights in 2 of 7 subjects ([CUE] §4). Track the
recall rate per session.

**How much the A/B can show (INFERENCE, arithmetic).** At about 4.5 awakenings per session, 10 sessions
give about 45 prompts per arm. A 95% binomial interval on 60% with n = 45 is ±1.96·√(0.6·0.4/45) =
±14 percentage points. Prompts cluster within sessions, so the real interval is wider. A difference
smaller than about 25 points between arms will not be distinguishable. Report descriptive results and
intervals, and do not claim significance.

**Benchmarks to compare against** (FACT, [LIN] §3.3, rec. 12): 91–100% of participants with ≥ 1 direct
incorporation; 57–70% of reports with the theme after the 1–5 min delay; about 5 reports per 45 min;
first onset at 10.3 ± 6.4 min. One person's results are not comparable with group statistics. Use them as
orientation, not as pass or fail.

**Decision rule (GUESSED).** Make DETECTION the default only if, in step 7, it beats TIMED_CUES on the
primary measure and produces at most 1 false trigger per 20 min control session. Otherwise ship
TIMED_CUES as the default and DETECTION as experimental.

---

## 6. Ideas worth borrowing, ranked by value / effort

Value and effort are my judgement (INFERENCE). Phases: **P1** sensing + CSV + timed cues; **P2** detection +
loop + recording; **P3** studio, dashboards, routes; **Def** deferred.

| Rank | Idea | From | Value | Effort | Phase | Evidence |
|---|---|---|---|---|---|---|
| 1 | Remove RMSSD from the required signal A, if the on-device `IBI_LIST` check shows inflation; keep it logged (owner decision) | BUR99, SHI06, GPPG25 | High (removes a likely false-trigger route) | Trivial (one line, plus tests) | P2 | weak–moderate |
| 2 | EDA stops shortening the hold and becomes logged, confidence-only; SCRs logged for replay. An SCR veto only with the owner's sign-off (owner requirement 11) | [SPEC] §3's own observation; SAN14 (stages only) | Medium | Low | P2 | weak |
| 3 | Recording 8 s / 120 s / 20 s; cycle-stage timeout 240 s (owner decision: CLAUDE.md §3 fixes 4 s / 45 s) | MIT iOS code; SR23 report length | High | Trivial (config) | P2 | moderate |
| 4 | Timer-forced wake on later loops (MIT iOS: 240 s after each re-incubation) as evidence for the no-onset fallback (rank 9); keep the 120 s refractory | MIT iOS `waitForOnsetTime` (`FlowViewController.swift:596-600`) | Medium | Trivial | P2 | weak |
| 5 | "Estimated sleep onset" wording everywhere; never "N1 detected" | ROS13, LAN25, MIL22 | High (honesty, user trust) | Trivial | P2 | strong |
| 6 | Pre-session confound form in the CSV | HUA18, CAR05 | High for tuning | Low | P1 | moderate || 7 | Early-onset guard after the initial cue | SM19; MIT web latency window | High | Low | P2 | moderate (need) |
| 8 | `RANDOM_60_300` countdown option, logged | P20 | High for learning the best delay | Low | P2 | moderate |
| 9 | No-onset fallback per loop (forced timed dwell): 900 s first loop, 600 s later loops | MIT web and iOS code; FS26 | Medium | Low | P2 | moderate (need) / weak (values) |
| 10 | HR-rise abort during the countdown | BUR99, BON97 | Medium | Low | P2 | weak–moderate |
| 11 | Automatic SR23 scoring: user-editable direct lexicon, per-report counts, plus a control lexicon for a non-incubated object as a false-positive baseline | SR23, FS24 (Fork vs Tree), [CUE] rec. 10 | High (MIT's own outcome measure) | Low–medium | P2 (compute), P3 (dashboard) | strong (method) |
| 12 | Lying-down threshold calibration for cue levels; separate theme and wake levels | KON24, WHI22a, PAP17 | High (wake vs startle) | Medium | P1 simple tap test; P3 full | moderate / weak |
| 13 | Soft chime under the 2 s voice fade (O6 option 3; the fade stays, CLAUDE.md §5.2) | KON24, GOL19 | Medium | Low | P1 | weak–moderate |
| 14 | Mode-specific session defaults (DETECTION 45 min / 6; TIMED 60 min / 4) | P20, FS24 | Medium | Trivial | P2 | moderate |
| 15 | Log `SKIN_TEMPERATURE_CONTINUOUS` if supported | KRA00, ROD26 | Low–medium | Low (needs `READ_SKIN_TEMPERATURE`) | P1 | weak |
| 16 | PhysioNet `sleep-accel` replay adapter | WAL19, [WEAR] §10 | High for offline tests | Medium (format, no EDA, sparse HR) | P2 | strong (feasibility) |
| 17 | Bottle-drop labelling via the phone microphone | LAC21 | High for validation | Medium | P3 (tool), Def as a trigger | moderate |
| 18 | Candidate re-baseline logged per loop | amulet-xpl (ANECDOTAL), HUA18 | Medium | Low | P2 | weak |
| 19 | Optional noise bed at a user-set level | CRE15, WHI22a, BRE24 | Medium | Medium | P3 | weak |
| 20 | Pause after a cue that is followed by movement; lower the ceiling (for theme cues only) | home-tmr (MIT licence), KON24 | Medium | Medium | P3 | moderate |
| 21 | Haptic pre-wake on the watch before the spoken prompt | amulet-xpl (MIT licence) | Unknown | Medium | Def | weak (untested) |
| 22 | THIM-style haptic probe as an optional trigger | SCO21 | Unknown for TDI (probes may arouse) | Medium | Def | moderate (SOL) / weak (TDI) |
| 23 | Per-user threshold refit after ≥ 10 labelled sessions | KUN20, FUJ19 | High long-term | High | Def | moderate |
| 24 | Offline actigraphy scorers for analysis tools | pyActigraphy (GPL-3.0), GGIR (Apache-2.0) | Low–medium | Medium | Def | — |

**Already adopted by the spec, so no action** (FACT, [SPEC]): pre-rendered prompts (as amulet-xpl);
never forcing system volume (the home-tmr anti-pattern); local-only storage with no telemetry server;
the TIMED_CUES control arm; the sleep-state question; AND logic; detection on every loop.

**Do not copy** (FACT, [PROJ] §5): code from openSleep, dormio, idealemu/TDI, DynamicNapAlarm,
nap-time-ios, SteelBall or ojwalch/sleep_classifiers, which have no licence. Their published parameter
values can be cited as facts (INFERENCE, not legal advice).

---

## 7. What this file did not verify

- Most FACTs come through a sibling note. In a revision on 2026-10-27 I re-read these primaries and
  corrected the claims that rest on them: P20 (PMC7590944, §2.3 and Fig. 4), HUA18 (PMC5896037, Table 2,
  abstract), SAN14 (PMC4335672), CHI21 (PMC8120339, SOL table, Table 8), FS24 (PMC12713799), FS26
  (PMC13341471), GPPG25 (PMC12119839, Tables 5–6), and the MIT iOS and web code cited by `file:line`.
- BUR99, CAR05, SHI06, BON97, KRA00, ROD26, ROS13, BM82, OGI89, MEN21, FUJ19, KOE24, PAR26 and W25 were
  ABSTRACT ONLY in the notes. Rows that rest on them (8, 12, 16, 29) carry that limit.
- The P20 Fig. 4 effect size (d ≈ 0.62) and the "about 20% of first onsets before 5 min" figure are my
  arithmetic. The second assumes a normal distribution of latency, which is unlikely to hold.
- Whether the SM-L500 supports `SKIN_TEMPERATURE_CONTINUOUS`, whether its accelerometer includes gravity,
  and how good Samsung's `IBI_LIST` is: all VERIFY ON DEVICE.
- The THIM ring's licence or openness was not checked.
- The spec was read as v1.1 on 2026-10-27, and it may still change. Row numbers refer to that version.

---

## Implications for OpenTDI

Each item states the change, the evidence and its strength. Items 1–6 are the ones I would do first.

1. **Remove the RMSSD rise from the required autonomic signal (A = HR drop), conditional on an on-device
   check.** First compare `IBI_LIST` RMSSD against a chest strap at rest on the SM-L500; remove it if it
   is inflated. Keep RMSSD as a logged feature and in the confidence display either way.
   - Evidence: no RSA/HF change during onset (BUR99, SHI06, ABSTRACT ONLY, [PHYS] §6.2); no HRV in any
     MIT paper or code ([LIN] §5 N5); raw Galaxy PPG RMSSD 1.7–4.5× the chest-strap value depending on
     denoising (GPPG25 Tables 5–6: HeartPy, raw PPG, seated baseline, not `IBI_LIST` in sleep).
   - This conflicts with CLAUDE.md §4, so it is an owner decision.
   - Strength: **weak–moderate**.
2. **Proposal: stop EDA from shortening the hold, and keep it as a logged, confidence-only feature.** Log
   SCRs (derivative > 0.01 µS/s, and the current max(1.5σ, 0.02 µS) step) so replay can test them. Do not
   let EDA block or abort triggers by default.
   - Evidence: the spec itself notes that "slope ≤ 0, no spikes" holds at rest almost all the time
     ([SPEC] §3). As a hold-shortening bonus it therefore turns the 20 s hold into 10 s in most quiet wake.
   - Conflicts with CLAUDE.md §4.3 and owner requirement 11 ("Bonus only: may shorten the hold and raise
     confidence; never triggers alone", [SPEC] lines 207–209), which outranks the D-decisions ([SPEC]
     line 5): owner decision.
   - A further option, an **SCR arousal veto** (an SCR resets the hold and aborts the countdown), would make
     EDA a blocker. It needs the owner's explicit sign-off and replay evidence first. No source tests wrist
     SCRs as a wake-versus-N1 discriminator: SAN14 compared sleep stages only, its 4% of peaks in NREM1 is
     a share of the night's peaks that mostly reflects time in N1, and its 0.01 µS/s criterion came from
     an inner-wrist sensor, not the dorsal Galaxy one (PMC4335672).
   - Strength: **weak**.
3. **Adopt MIT's own recording stop values:** `hangoverMs` 8000, `maxMs` 120 000, `noSpeechTimeoutMs`
   20 000. Raise `cycleStageTimeoutS` to 240 s, because the spec's cross-rule then needs ≥ 188 s.
   - Evidence: MIT iOS code 8 s / 30 s min / 120 s max ([C] §10); reports average 14 words, halting
     (SR23; P20 §3.1); STT hallucinates over long pauses (KOE24, ABSTRACT ONLY).
   - Conflicts with CLAUDE.md §3 ("4s silence or 45s max"): owner decision.
   - Strength: **moderate**; 8 s and 120 s become SOURCED.
4. **Keep the 120 s refractory after each cycle (GUESSED); do not cite MIT iOS for a 240 s refractory.**
   MIT iOS `waitForOnsetTime` (default 240, `AppDelegate.swift:30`) is a maximum wait: 240 s after each
   re-incubation it forces the next onset, `sleepDetected(trigger: OnsetTrigger.TIMER)`
   (`FlowViewController.swift:596-600`, exported as `maxTimeBetweenSleeps`, lines 751-752), and sensor
   detection runs only on the first loop (`numOnsets == 0`, line 479). That is evidence for a short
   no-onset fallback on later loops (item 5), not for a refractory.
   - Other context: Dormio Light 7 min re-sleep; FS26 8.23 ± 2.32 min (range 6–12) between successive
     sleep-onset reports, which includes the 1–5 min delay and the report ([CUE] §8.1; [P] §2.3).
   - Strength: **weak**; the refractory value stays GUESSED until replay shows how soon false triggers
     follow a re-incubation cue.
5. **Add an early-onset guard of 120 s after the initial incubation cue ends (GUESSED), and a no-onset
   fallback.** The fallback runs a timed dwell if nothing triggers within 900 s (15 min) on the first loop
   or 600 s (10 min) on later loops (GUESSED). These are the values in §1.3 and §3 row 18.
   - Evidence: MIT's named failure mode is false onsets in the first minutes, caught only by an
     experimenter (SM19 Limitations, [LIN] §0). MIT's web latency window forces onset at its max
     ([LIN] §4), and MIT iOS forced a timer wake 240 s after each re-incubation on loops 2+ (item 4). The
     first-onset distribution is 10.3 ± 6.4 min (P20).
   - Strength: **moderate** for the need, **weak** for the values.
6. **Settle, then baseline: discard 60 s after Start, then take a 120 s baseline with the median for HR.**
   The total stays 180 s.
   - Evidence: HR drops early after lying down, most between the first and later 30-min blocks,
     attributed to posture (HUA18 abstract, Table 2); HR falls after lights out (CAR05 ABSTRACT ONLY);
     P20's text baseline is 120 s; DynamicNapAlarm and [WEAR] use median baselines.
   - Conflicts with CLAUDE.md §4.2 ("Baseline Acquisition (t = 0s..180s)"): owner decision.
   - Strength: **moderate** for the settle, **weak** for 60 s and the median.
7. **Lengthen the HR moving average from 15 s to 30 s, treat θ_drop 0.07 as a placeholder for replay
   tuning (consider the 5% HIGH preset as the default), and keep the 20 s hold, with 60 s as the LOW
   preset.**
   - Evidence: HUA18 group means fell 3.5–8.8% from relaxed wake to drowsy or theta (Table 2, unpaired);
     in P20 Fig. 4a the HR change from Dormio-defined onset to first awakening averaged −2.89 ± 22.94 bpm
     for Sleep reports (about 4% at 70 bpm); Dormio's 5 BPM was a two-sided absolute delta
     (`script_wearable.js:701`), not a drop size; the HR fall is about 1 bpm/min (DZ20); independent
     builds' holds are 8–10 s (AMU, IDE) and validated algorithms integrate over minutes ([WEAR] §8); N1
     in naps lasts 1.35 ± 1.20 min (LAC21), which caps how long persistence can run.
   - Conflicts with CLAUDE.md §4.3 ("Current 15-second moving average of HR"; 7% default): owner
     decision.
   - Strength: **weak**; θ_drop 0.07 is GUESSED.
8. **Add an HR-rise abort during the countdown.** For example, abort when the 5 s HR mean climbs back
   above μ_HR,eff (GUESSED form). With the existing movement abort, this makes the countdown a
   confirmation window rather than a blind wait.
   - Evidence: HR rises at arousals and accelerates before them (BUR99, BON97; both ABSTRACT ONLY).
   - Strength: **weak–moderate**.
9. **Keep the 180 s fixed countdown as the default, and add `RANDOM_60_300` (uniform by OpenTDI's choice,
   redrawn per cycle, logged) for validation sessions.** This closes O4 with both options.
   - Evidence: P20 used a "variable timer" of 1–5 min, distribution not reported (P20 §2.3); "uniform"
     and "mean 3 min" would be INFERENCE, and MIT's wearable code defines `getRandomInt`
     (`script_wearable.js:1245`) but this note found no call that draws the delay. The literature pulls
     both ways: direct
     imagery halves after 120 s (WAM10) and N2 loses the insight benefit (LAC21), yet MIT's best
     per-report rates came with the 1–5 min delay (57–70%, [LIN] §3.3). Only randomised personal data can
     find this user's best delay.
   - Strength: **moderate** for the range, **weak** for any single best value.
10. **Use mode-specific session defaults: DETECTION 45 min and at most 6 awakenings; TIMED_CUES 60 min and
    4 rounds.** This closes O12.
    - Evidence: P20 45-min time box with about 5 awakenings; FS24 4 rounds in about 1 h; at most 10
      prompts per hour (WAM10) ([CUE] §4).
    - Strength: **moderate**.
11. **Calibrate cue loudness per user while lying down, and give the theme cue and the wake prompt
    separate levels.** Suggested: theme ≈ T + 10 dB, wake ≈ T + 20 dB, at most one +6 dB replay, with the
    existing caps (offsets GUESSED). Keep the 2000 ms log fade on every voice prompt and add a soft chime
    under it, as [SPEC] O6 option 3 says ("prepend a soft chime under the fade"), so the first, quiet
    second of the fade carries a chime and not only attenuated speech.
    - Conflicts with CLAUDE.md: owner decision. The per-user calibration changes how CLAUDE.md §5.2's
      "calibrated target volume" is set; the 2 s voice fade itself stays as the owner mandates.
    - Evidence: no MIT level data ([LIN] §5 N6); TMR cue levels 35–50 dB SPL; WHO: awakening thresholds
      are lowest in stages 1–2; home harm from loud cues (WHI22a, GOL19); per-user calibration in KON24
      ([CUE] §2).
    - Strength: **moderate** for calibration, **weak** for the offsets and the chime.
12. **Score incubation automatically, as SR23 did.** Use a user-editable direct lexicon (theme word,
    plural, parts), count direct hits per report, and add a control lexicon for a non-incubated object.
    Keep the audio as the primary record and allow morning correction of transcripts.
    - Evidence: P20 and SR23 counted direct references; FS24 used Fork vs Tree as a control; human
      indirect scoring reached only κ 0.17 (FS24) ([CUE] §5).
    - Strength: **strong** for the method; STT accuracy on drowsy speech is **unverified**.
13. **Validate in this order: offline replay on PhysioNet `sleep-accel`, then ≥ 5 quiet-wake controls,
    then 10–20 labelled naps, then ≥ 10 A/B sessions per arm against TIMED_CUES, then held-out sessions.**
    Report false triggers per hour, Bland–Altman Δt, and the Halfway/Asleep-with-imagery yield.
    Make DETECTION the default only if it beats TIMED_CUES (§5).
    - Evidence: [WEAR] §10; FS24 shows that cueing produces incorporation whether or not the person
      sleeps (91% with ≥ 1 incorporation, but awake FMT gave as many incorporations as hypnagogia,
      F(1, 80) = 0.61, p = 0.44; reports typed afterwards, PMC12713799), so theme incorporation is not
      evidence about the detector and the Halfway/Asleep-with-imagery yield stays primary; KAN11 no-nap
      misclassification; MAR13.
    - Strength: **strong** for the design need, **weak** for the session counts (about ±14 points
      resolution at 45 prompts per arm, arithmetic).
14. **Present the detector as "estimated sleep onset", never "N1 detected", and make no clinical claims.**
    - Evidence: N1 inter-scorer agreement 63% (ROS13, ABSTRACT ONLY); Muse-S N1 κ 0.41 (LAN25); about
      16% of SleepStim cues landed in wake (WHI22a); the only TDI nightmare study had no control group
      and no subgroup effect (Y25) ([LIN] rec. 14).
    - Strength: **strong**.
15. **Log confounds and skin temperature, but use neither as a trigger.** Log time of day, caffeine,
    sleepiness, posture and medication in a pre-session form (Phase 1). Log
    `SKIN_TEMPERATURE_CONTINUOUS` if the SM-L500 lists it.
    - Evidence: HUA18, CAR05 for the confounds; KRA00, ROD26 for temperature (ABSTRACT ONLY).
    - Strength: **moderate** that confounds shift the physiology; **weak** for temperature.
16. **Keep jerk and limp as logged events and confidence bonuses only, and re-check the limp detector after
    the gravity test (§15 #5).**
    - Evidence: hypnic-jerk timing within sleep onset not verified (FIC24, [PHYS] §6.7); no wearable
      validation; the SDK may exclude gravity.
    - Strength: **anecdotal**.
17. **Licence hygiene for GPL-3.0.** Code may be adapted, with its notices and attribution kept, only
    from projects under MIT-, Apache-2.0- or GPL-3.0-compatible licences: amulet-xpl, home-tmr,
    rmallela26/TLR, remind-eeg and goofi-pipe (MIT), SMACC and pyActigraphy (GPL-3.0), and GGIR
    (Apache-2.0, one-way compatible with GPL-3.0; [WEAR] line 645). Re-implement behaviour from the unlicensed repos and cite their parameter values as
    facts. Keep the §7 Samsung permission and never commit the SDK or DREAMT data.
    - Evidence: GitHub API licence metadata ([PROJ] §5; [WEAR] §0); [SPEC] §1.3.
    - Strength: **strong** for the licence facts; the legal reading is INFERENCE, not legal advice.
