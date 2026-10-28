# Sleep and dream statistics: what the watch estimates and how to show it (2026-10-28)

This file backs the phone's Stats screens (owner, 2026-10-28: "nice graphs ... of your sleep time and dream state over months and weeks"): how a wrist estimates sleep and how well, OpenTDI's estimate and night measures, how many nights a figure needs, how to word and draw the figures, dream metrics, and the cue/no-cue experiment. It builds on sleep_and_dreams.md, cited as **[SD n]** or **SD §n**, and does not repeat it. Bracketed numbers point to §A11.

Evidence tags are **[strong]**, **[moderate]**, **[weak]**, **[contested]** and **[preprint]**; **[guideline]** marks position statements and government guidance. Design rules that are our own reasoning from the sources are marked *(inference)*. Parameters are **SOURCED**, **GUESSED** or **CALCULATED** (our own stated arithmetic or simulation).

This is wellness software. It never names sleep stages, never calls anything abnormal and never diagnoses.

## A1. What the app estimates, and how well

The watch has no EEG, so every sleep figure is a **sleep/wake estimate from wrist movement**. Heart rate is shown but is not used to decide sleep or wake (§A2.4). Timing is far more robust than anything measured inside the night.

| Figure | How well a wrist measures it | Tag |
|---|---|---|
| Sleep window (onset → final wake) and mid-sleep | Over 7 home nights in 80 adults, mid-sleep agreed between diary, actigraphy and single-channel EEG at r = .98–.99, with bias ≤ 6 min [18]. HDCZA against PSG: onset −20 min (mean absolute error 32.9), waking time −17 min (mean absolute error 21.0) [2]. | [moderate] |
| Total sleep (TST) | Diary vs devices r = .71–.75 [18]. Meta-analysis in healthy adults, actigraphy − PSG: +11.2 min (95% CI −4.0 to 26.4) [10]. Consumer devices −0.3 to +46.8 min (wrist devices +2.6 to +46.8), with limits of agreement as wide as −93 to +120 min [16]. | [strong] for direction, [moderate] for size |
| Time to fall asleep (SOL) | Actigraphy − PSG −8.1 min (−12.5 to −3.7) [10]. Quiet wake is scored as sleep [14]. HDCZA does not detect latency [2]. | [strong] |
| Wake after sleep onset (WASO), awakenings | Under-detected. WASO −12.6 ± 34.0 min, and 0.93 min missed for every minute of WASO above 30 [9]. Consumer devices −2 to −50 min [16]. ICC as low as 0.08 [8]. WASO error about ±160 min in sleep-clinic patients [7]. | [strong] that it is weak |
| Sleep efficiency (SE) | Diary vs devices r = .18 (.25 between the two devices) [18]. Least reliable figure. | [moderate] |
| Wake epochs (specificity) | Healthy adults: specificity .53 [10]. Cole-Kripke specificity .329 [9]. Wrist devices detect 18–54% of wake epochs [16]. | [strong] |

- **Quiet wake looks like sleep.** Lying still while awake is the main error source [1][9][10]. Dormio incubation and WBTB are exactly "lying still, awake", so the app marks the wake periods it knows about as wake (see Implementation).
- **Accuracy worsens on worse nights.** Bias and spread grew on nights with less sleep, lower SE, longer latency and more WASO [16] **[moderate]**.
- **No validation on this watch.** No study validates any sleep/wake rule on a Galaxy Watch, at 25 Hz, on the back of the wrist, or for a sleeper whom a device wakes on purpose. The figures above come from research actigraphs and other consumer devices.
- **Clinical position.** The AASM actigraphy guideline gives only **conditional** recommendations for TST, SOL, WASO, SE and timing. It covers FDA-cleared devices and recordings of 72 h to 14 days; consumer wearables are outside its scope [13] **[guideline]**. The review's mean differences in adult insomnia (actigraphy minus PSG) were TST +10.1 min (95% CI −8.1 to +27.1) and sleep latency −6.2 min (−9.1 to −2.3); it judged these ranges narrow enough for clinical use. WASO (1.5 min, CI −13.7 to +19.5) and SE (1%, CI −4.9 to +3.0) were too wide to replace PSG [12] **[strong]**. Consumer sleep technology cannot diagnose [SD 34].
- **Ranking for display** *(inference)*: timing > duration > latency > WASO/awakenings > SE.

## A2. The estimate (v1)

### A2.1 What the watch logs

- **Raw accelerometer.** About 25 Hz, with gravity (`gravityMode=WITH_GRAVITY`). The raw file `w_accel_raw.csv` stays on the watch, and the phone never receives it.
- **Per-second packet columns** (`w_packets.csv`, copied to the phone):
  - **`accel_rms_g`**: the RMS of |a − g_lp|, a high-pass movement intensity close to an activity count.
  - **`stillness`**: clamp(1 − (rms − 0.003)/(0.05 − 0.003), 0, 1). A second below 0.80 counts as moving. A major movement is 3 of the last 5 s moving (StillnessGate, §8.10).
  - **`tilt_deg`** **cannot be used for sleep/wake.** Its reference resets after movement, after a limp event, or once it is 60 s old (`limpRefMaxAgeS`), so it never shows posture change over more than about 60 s.
- **Absolute arm angle.** Since 2026-10-28 `w_packets.csv` ends with **`angle_z_deg`**: atan(z/√(x² + y²)) of the per-second mean orientation vector that `AccelProcessor.closeSecond` computes. It is empty when the second has fewer than 20 samples, or when the gravity mode is not WITH_GRAVITY, since only then does that vector point along gravity. Older nights lack the column.
- **Off-wrist codes.** `hr_status −3` and `eda_status −5` mean "detached" (Samsung SDK; `samsung_health_sensor_sdk.md`).

### A2.2 Validation of candidate methods

**Count algorithms** (1-min or 30-s epochs, weighted activity counts)

- Cole-Kripke 1992: n = 41, about 88% correct, latency r = .90 with PSG [3] **[moderate; abstract only]**.
- Sadeh 1994: 20 adults and 16 adolescents, 91–93% agreement, higher for sleep than wake, same result on either wrist [4] **[moderate; abstract only]**.
- These need activity counts from a particular actigraph. Webster's 1982 rescoring rules could only be traced to secondary descriptions [11], so they are not used.

**Arm-angle methods** (they suit a MEMS watch)

- **van Hees 2015, sustained inactivity bouts (SIB)** [1] **[moderate]**:
  - angle_z = atan(a_z / √(a_x² + a_y²)) · 180/π, with z perpendicular to the skin. The angle is taken from 5-s rolling medians of each axis and averaged per 5-s epoch.
  - An epoch belongs to a SIB when the angle changes by no more than 5° for at least 5 min.
  - Against PSG in 28 sleep-clinic adults: accuracy 83 ± 8%, sensitivity 91 ± 13%, **specificity 45 ± 23%**, bias +31 ± 38 min (sleep over-estimated). Across the other window/angle settings in the same patients, specificity ranged 28–72% and bias −86 to +74 min.
- **van Hees 2018, HDCZA sleep-period window** [2] **[moderate]**:
  - Steps: take |Δangle_z| between 5-s epochs; apply a 5-min rolling median; set the threshold to the 10th percentile × 15; keep blocks below the threshold that last more than 30 min; merge blocks separated by gaps under 60 min; the longest block is the window.
  - For PSG-length recordings the authors constrained the threshold to **0.13–0.50**.
  - Healthy good sleepers (n = 22): onset −20 min, waking time −17 min, window duration +2 min (mean absolute error 37.7), accuracy 89%. Sleep clinic (n = 28): waking time −37 min. Against diaries: C-statistic 0.95.
  - Limits: it does not detect latency or time in bed; split nights and long night-time wake break it; its parameters were tuned by eye on 20 recordings.
- **Plekhanova 2023** [8] **[moderate; abstract only]**: the same method across Axivity, GENEActiv and ActiGraph, on either wrist, in 30 adults. Within ±10% of PSG for window, duration, onset and waking time. "A poor measure of wake during the sleep period."
- **Head-to-head comparisons:**
  - Patterson 2023 [7] **[moderate; authors employed by ActiGraph]**, 28 sleep-clinic patients:

    | Method | Accuracy % | Sensitivity % | Specificity % |
    |---|---|---|---|
    | van Hees | 76.2 | 83.6 | 47.5 |
    | Sadeh | 75.3 | 82.6 | 49.7 |
    | Cole-Kripke | 74.5 | 81.4 | 50.3 |
    | LSTM-100 (deep model with the best specificity at that accuracy; CNN-50 had 77.3% accuracy but 42.0% specificity) | 77.2 | 78.6 | 58.9 |

    The simple methods generalised as well as or better than machine learning.
  - Sundararajan 2021 [6] **[moderate]**: F1 van Hees 70.9% (κ .42), Sadeh 68.1% (κ .37), Cole-Kripke 67.5% (κ .36), random forest 73.9% (κ .50). Mostly GENEActiv data.
  - In children aged 8–16, HDCZA had the best specificity: mean 87.5% (85.9–89.6% across devices and placements), against a mean of 63.8% for all algorithms; count methods ranged 41–83% [11] **[moderate; not adults]**.
- **Rules for deriving night measures** (Fekedulegn 2020, describing Action-W [14] **[moderate]**):
  - Sleep onset is the first block of ≥ 10 min of sleep with ≤ 1 min of wake inside.
  - Final wake is the last minute scored asleep.
  - An awakening is ≥ 1 min of wake; a long awakening is ≥ 5 min.
  - SE is TST divided by either the sleep period or time in bed.

### A2.3 Choice for v1

- **Primary method, `arm-angle v1`:** van Hees SIB [1] inside an HDCZA window [2], with the wake periods the app knows about forced to wake. It uses the per-second `angle_z_deg` column (§A2.1).
- **Fallback method, `stillness v1`:** for nights recorded before that column existed, or without enough angle data. A still bout is ≥ 5 min with no major movement: 3 of 5 s below stillness 0.80, as in StillnessGate, except that a second without data does not count as moving. This has **never been validated against PSG**.
- Every parameter, with its status, is a named constant in the code (see Implementation).
- **Expected error** *(inference from [1][2][10])*: total sleep probably over-estimated by roughly 10–30 min, WASO under-estimated, onset and final wake each ±20–40 min.

### A2.4 Heart rate is shown, not used

- **Walch 2019** [5] **[moderate]**: Apple Watch, n = 31. Share of wake scored correctly at 90% sleep sensitivity (neural-net classifier):

  | Inputs | Wake scored correctly |
  |---|---|
  | Motion | 0.542 |
  | Heart rate only | 0.323 |
  | Motion + heart rate | 0.528 |
  | Motion + heart rate + clock proxy | 0.675 |

  Heart rate added "few improvements" for sleep/wake.
- **Fonseca 2017** [15] **[moderate; abstract only; Philips authors]**: PPG heart-rate variability plus accelerometer gave κ 0.55, against 0.40 for actigraphy. That model was trained on 135 PSG recordings.
- **Conclusion:** gaining from heart rate needs a model fitted on PSG, which OpenTDI does not have. Heart rate is display only: the mean and minimum while estimated asleep.

## A3. Metric definitions

### A3.1 The Consensus Sleep Diary (CSD) and why definitions must be fixed

- **Core CSD items** [17] **[guideline: expert consensus]**:
  1. time into bed
  2. time tried to sleep
  3. time taken to fall asleep
  4. number of wakings
  5. total length of those wakings
  6. time of final awakening
  7. time out of bed
  8. perceived sleep quality (Likert scale)
  9. comments

  The optional morning version (CSD-M) adds a rating of how refreshing sleep was and an estimate of total sleep time; in the core diary TST is calculated from the other items [17].
- **Formulas** (as given by [18], since [17]'s table could not be read):
  - TIB = from "tried to sleep" to "out of bed"
  - TST = TIB − (SOL + WASO + TWAK), where TWAK is the time from final awakening to getting up
  - SE = TST / TIB × 100
  - Mid-sleep = halfway between onset and wake **[moderate]**
- **No single definition exists for TIB, SE or WASO.**
  - SE and WASO have been computed both with and without the final awakening [17].
  - In one case, "literal" TIB gave SE 66% and "intention to sleep" TIB gave 73% [19] **[weak: letter, single case]**.
  - Actigraphy guides also use onset → offset as the denominator [14].
  - *Inference:* the definitions below are fixed. Changing any of them changes `method` and `SUMMARY_VERSION`.

### A3.2 OpenTDI's definitions (from `NightSummary`)

| # | Shown as | Definition | CSD equivalent | Robustness |
|---|---|---|---|---|
| 1 | Sleep window | `sleep.onsetMs` → `sleep.finalWakeMs` | items 2+3 → 6 | high [18] |
| 2 | Mid-sleep | (onset + final wake) / 2, as minutes after noon of `nightOf` (no wrap at midnight) | midpoint [18] | highest [18] |
| 3 | Estimated sleep | `sleep.sleepMin`: sleep epochs between onset and final wake | derived TST | moderate [16][18] |
| 4 | Time recorded | `recordEndMs − recordStartMs` | TIB **only if** the user starts recording on lying down to sleep and stops it on getting up | depends on use |
| 5 | Time to estimated sleep | `sleep.latencyMin` = onset − recording start | item 3 (proxy) | moderate; skewed |
| 6 | Awake during the night | `sleep.wasoMin`: wake epochs between onset and final wake, **excluding the planned WBTB break and no-data gaps** *(inference)* | item 5 | low [9][16] |
| 7 | Lie-in after waking | `recordEndMs − finalWakeMs` | item 7 − item 6 | moderate |
| 8 | Share of the night asleep, up to final wake | `sleep.efficiencyPct` = sleepMin / (final wake − recording start − gaps). This is **not** CSD SE, because it leaves out TWAK; the name says so. | SE (own variant; not one of [14]'s) | **lowest** [18] |
| 9 | Long wakings | `sleep.awakenings`: wake runs ≥ 5 min between onset and final wake, excluding the WBTB break (the "long wake episode" in [14]) | item 4 | low |
| 10 | How rested | `DreamReport.refreshed` (1–5) from the WAKE report | CSD morning "refreshed" item [17] | self-report |
| 11 | Regularity | SD in minutes of onset, final wake and mid-sleep over the last 14 valid nights | — [23] | high for timing |
| 12 | Free-morning shift | median mid-sleep on free nights − median on alarm nights (signed) | MSF − MSW [24] | moderate |
| 13 | Heart rate while estimated asleep | `hrSleepMeanBpm`, `hrSleepMinBpm` | — | display only |

- **Night flags.** A WBTB night has `wbtb.ringAtMs != null && wbtb.skipped == null`. A cue night has `lateCues.cuesPlayed > 0`. A Dormio night has `promptedWakings > 0`, counted from the phone's own `p_transitions.csv` (see Implementation). An alarm night has `wake != null`. WBTB, cue and Dormio nights add wake on purpose.
- **Optional CSD quality item.** The quality rating is a core CSD item [17]; `refreshed` is the morning-version item. Adding one quality tap would complete the CSD core set; `refreshed` is enough for v1. HCI work recommends pairing sensor data with the user's own rating [25] **[moderate]**.

## A4. Regularity and timing

- **Sleep Regularity Index (SRI)** is the probability of being in the same state 24 h apart, rescaled to 200(x − ½). 100 means perfectly regular.
  - Phillips 2017: regular students scored 83–84 and irregular students 52–56. Irregular sleepers had a melatonin onset about 2.5 h later. SRI did not track duration (r = .05) [21] **[moderate]**.
  - UK Biobank median 81 (IQR 73.8–86.3), from ≥ 5 days of pairs [22] **[moderate]**.
- **Fischer 2021** compared regularity metrics [23] **[moderate]**:
  - SRI is unbiased by study length but has wide intervals.
  - SD and interdaily stability need more than a week.
  - SRI reacts to naps and night wakings; SD does not.
  - With only sleep timing available, use SD.
- **Applied here** *(inference)*: the watch records nights only, so an SRI from this data is non-standard and cannot be compared with published values. Show the SD of onset, final wake and mid-sleep. Keep SRI over consecutive recorded night pairs only as a research-export field.
- **Why regularity matters:** a National Sleep Foundation panel agreed that regular timing matters for health and performance [26] **[guideline; abstract only]**. Variability is a dimension of its own beyond the mean [27] **[moderate; systematic review, abstract only]**.
- **MCTQ definitions** [24] **[moderate]**:
  - MSW and MSF are mid-sleep on workdays and on free days.
  - Chronotype MSFsc = SOf + SDweek/2 when free-day sleep is longer than workday sleep; otherwise MSFsc = MSF.
  - Social jetlag SJL = |MSF − MSW|. The authors note that the signed difference MSF − MSW is "often also informative".
  - Free days count only if no alarm clock was used.
  - Background: large work-day/free-day differences, with late types oversleeping on free days [28] **[moderate; abstract only]**; social jetlag associated with lower wellbeing [29] **[weak–moderate; cross-sectional, abstract only]**.
- **Applied here** *(inference)*:
  - An "alarm" night has `wake != null`. A "free" night has no WAKE alarm, no WBTB, no late cues and no Dormio wakings.
  - Show the signed difference of medians, never the term "social jetlag" or a chronotype label.

## A5. How many nights a figure needs

| Source | Finding |
|---|---|
| Aili 2017, 54 workers, 7 nights, reliability ≥ .70 [30] **[moderate]** | Share of time asleep: ≥ 2 nights. SE: ≥ 5. TST: more than 7 (not reached in a week). Weekends *lowered* reliability. |
| Wohlgemuth 1999 [31] **[moderate; abstract only]** | About 1 week was usually enough (PSG or logs). With sleep logs in insomnia, WASO needed 3 weeks and SOL more than 2. |
| Rowe 2008 [32] **[moderate; abstract only]** | 3-day means were comparable to 7- or 14-day means. Variability needs ≥ 7 days, and SOL 14 days. |
| Van Someren 2007 [33] **[weak; n = 22, abstract only]** | Reliability rose steadily with more nights; interdaily stability needed more than 7 days (10 insomnia, 12 dementia). |
| Fekedulegn 2020 [14] | ≥ 7 days for day-to-day variability; 14 preferable for SOL. |
| Fischer 2021 [23]; Windred 2024 [22] | SD needs more than 7 days; SRI was computed from ≥ 5 days of pairs. |
| Sleep experts in Ravichandran 2017 [25] | A two-week baseline before personal suggestions **[weak: 5 experts]**. |
| AASM guideline [13] | Clinical recordings run 72 h to 14 days **[guideline]**. |

- **Noise floor** *(inference from [16][18]; provisional until this watch is validated)*:
  - A single night's TST is within about ±45 min of PSG on a good device and about ±90 min on a poor one, so never comment on a single-night duration difference under about 60 min.
  - A 14-night change is worth naming only if duration moved ≥ 30 min or timing moved ≥ 20 min.

## A6. Display and wording evidence

- **Charts.**
  - Noon-to-noon daily plots are standard in actigraphy [14]. An actogram puts one day per line, aligned on clock time [34] **[moderate]**.
  - HCI work recommends long-term views of bedtime, wake time and duration over nightly stage feedback, ranges instead of single values, and published formulas [25] **[moderate: 87 surveyed, 17 interviewed, 6,986 reviews]**.
  - Fitbit users gained awareness but did not improve their sleep. The barriers were not knowing what normal sleep is or what to do [36] **[weak; n = 12, abstract only]**.
  - Users reviewed graphs alongside recordings to find patterns [35] **[weak; abstract only]**.
- **Feedback changes how people feel.**
  - People with insomnia told via a watch that they had slept poorly reported worse alertness (d = 0.79) and more sleepiness (d = 0.55), with no change in objective vigilance [41] **[moderate; abstract only]**.
  - Students told they had little REM did worse on 2 of 4 cognitive tests [42] **[moderate; abstract only]**.
  - Wearable data with *guidance on interpreting it* reduced insomnia severity (d = 0.51) without raising anxiety [43] **[moderate]**.
- **Orthosomnia.**
  - Three patients spent extra time in bed to improve tracker numbers and judged their day by tracker scores. The authors advise using trackers for timing and time in bed [20] **[weak: case series]**.
  - A validated scale has "interference" and "rigidity" factors, both linked to health anxiety and perfectionism [40] **[moderate]**.
- **Reference values.**
  - Adults should sleep **7 h or more on a regular basis** (AASM/SRS) [37] **[guideline]**. More than 9 h "may be appropriate" for young adults and is uncertain for others, so no 9-h ceiling is drawn. The NSF 7–9 h band [38] **[guideline; abstract only]** is not used.
  - Clinical "normal" cut-offs (SE, WASO, awakenings, SOL [14]; NSF quality indicators [39]) and UK Biobank SRI mortality strata [22] are **not** drawn.
- **Uncertainty communication.**
  - Numeric ranges barely reduced trust (meta d = −0.15); verbal uncertainty statements reduced it more (d = −0.55) [59] **[strong; 5 experiments, n = 5,780]**.
  - Icon arrays improved understanding for people with low numeracy: inaccuracy fell from 74% to 42% [60] **[moderate]**.
  - Error bars are often misread as high/low values [61] **[moderate]**.
  - Icon arrays that show ranges helped verbatim recall (3.75 vs 3.15 of 8) [62] **[moderate]**.
  - Low-density quantile dotplots worked best on phone screens [63] **[moderate]**.

## A7. Dream, lucid and nightmare metrics

- **Dream recall frequency (DRF).**
  - Schredl's 7-point scale runs from never to almost every morning, with retest r = .85 [44][47] **[strong]**. It suits a one-off baseline, not night-to-night change.
  - A diary gives higher rates than a retrospective estimate: 5.04 recalled mornings a week by voice-recorder diary, against 2.66 ± 2.29 by questionnaire (204 adults) [SD 49] **[moderate]**.
  - Retrospective estimates run low, and keeping a logbook also raises recall [45][46] **[moderate; abstracts only]**. A diary "can increase dream recall frequency dramatically", especially in low recallers [47].
  - **So early rises in recall are partly the diary itself.**
- **Covariates.**
  - Recall was lower in winter than spring (|g| = 0.59) [SD 49] **[moderate; one study]**.
  - Age did not predict recall, but older people reported more white dreams [SD 49].
  - Waking by alarm, buzzer or tone went with about 10 percentage points lower recall than waking by name, across 69 awakening studies (correlational) [54] **[weak–moderate]**.
  - Recall by weekday is unstudied.
- **Lucid dream frequency.**
  - The 8-point scale has 4-week retest r = .89 (Stumbrys et al. 2013, as reported in [47]) **[moderate]**.
  - Reliable diary measures need long periods [47].
  - Lucid frequency correlates r = .57 with recall [SD 53].
- **LuCiD scale** [48] **[strong for what it says; full text]**:
  - 28 items, each rated 0–5. INSIGHT has 6 items and is "the core criterion of dream lucidity"; it separated lucid from non-lucid reports by 2.93 scale points. CONTROL (5 items) is "subordinate to lucid insight".
  - **No lucidity cut-off is proposed.** The only anchor is that a factor mean above 2.5 is "high in absolute terms". Later studies treat the scores as continuous [49][50] **[moderate]**.
  - OpenTDI asks one item per factor, so its ratings are "inspired by" LuCiD, not LuCiD scores.
  - **A self-rated lucid morning = insight ≥ 3 is GUESSED.** Self-rated lucidity is unverified (SD §4).
- **White dreams.**
  - Definition: the feeling of having dreamed without any content. About one third of dream reports in a review [51] **[moderate; abstract only]**; 14 ± 13% of mornings at home [SD 49].
  - Keep them as their own category, never merged with "dream" or "no dream".
- **Nightmares.**
  - Standard definition: "dreams with strong negative emotions that result in awakening". The frequency scale has retest r = .765 [53] **[moderate]**.
  - Daily logs give more bad dreams than retrospective estimates [52] **[moderate; abstract only]**.
  - Distress is separate from frequency: the Nightmare Distress Questionnaire correlates only r = .26–.44 with frequency in earlier studies summarised in [53]; no cut-off is used there. The app does not score distress.
  - The watch's "2 nightmares in 7 days → consider a few nights off" hint is **GUESSED**; the AASM gives no number [SD 96].
- **Sleep paralysis.** No frequency scale was opened. Show counts only, split by WAKE and WBTB report.

## A8. The cue/no-cue experiment (n-of-1)

- **Guidance** [55][56] **[guideline]**:
  - Randomise and report how.
  - Account for carryover and period effects.
  - Pre-specify outcomes.
  - Give exact p-values.
  - With few crossovers, report the posterior probability that one arm is better. AHRQ recommends the Bayesian posterior and the sign test, and "the simplest possible graph".
  - In blocks of two, the second arm "is predetermined by the first", so users can guess it. Use blocks that are a multiple of the number of treatments, or randomised block sizes. ABBA-type orders balance linear time trends.
  - Randomisation tests can be built into single-case designs [57] **[moderate; abstract only]**.
- **Exact test floor** (CALCULATED; logic from [56][57]):
  - With a binary outcome only discordant blocks carry information.
  - When every discordant block favours one arm, the smallest two-sided p is 0.25 / 0.125 / 0.0625 / 0.031 for 3 / 4 / 5 / 6 blocks, so **at least 6 discordant blocks are needed before p < .05 is possible**.
- **Intervals.** For small n, use the Wilson or Jeffreys interval, not the Wald interval [58] **[strong; abstract only]**.
- **Lessons from self-experiment apps.**
  - **TummyTrials** [64] **[moderate]**: "no evidence" was the most common result, and users read it as "not a trigger". Unbalanced random runs worried users.
  - **SleepBandits** [65] **[moderate]**: showed early likelihoods such as "76% … from 12 nights". None of the results would have reached p < .05, and the authors note that peeking reduces validity.
- **Carryover** *(inference from SD §1, §6)*:
  - REM lost on a cue night may rebound on the next night [SD 14][SD 15], biasing the comparison toward no difference.
  - Cue training runs on both arms, so learning is a period effect.
  - Guessing the arm biases self-ratings [56].
  - Mitigations: randomise within blocks; leave an ordinary night between experiment nights (washout length GUESSED); check for a period effect at ≥ 20 blocks.
- **Power** (CALCULATED):
  - Assumptions: independent nights, 15% lucid rate on no-cue nights (SD §5), exact two-sided sign test at α = .05, 4,000 simulations per cell. The effects are GUESSED scenarios.

    | Cue vs no-cue per night | 20 pairs | 50 | 80 | 120 | 200 |
    |---|---|---|---|---|---|
    | 15% → 25% | 5% | 18% | 28% | 43% | 66% |
    | 15% → 30% | 9% | 34% | 54% | 77% | 94% |
    | 15% → 45% | 40% | 88% | 98% | 100% | — |
    | Recall 72% → 80% | 3% | 11% | 17% | 25% | 41% |

  - A doubling of lucid nights needs about **80–120 complete pairs**. Small recall effects are practically undetectable for one person.
  - Positive autocorrelation between nights would make these numbers worse.
- **Peeking** (CALCULATED, no true effect, checking after every pair from 6 to 60):
  - p < .05 gave 8–13% false "differences".
  - A flat-prior posterior > .95 gave 28–37%; > .90 gave 53–64%.
  - **Checking only at milestones** (10, 20, 30, 40, 60, 80, 100 pairs) with a **Beta(5,5)** prior and a 0.975/0.025 threshold gave 4–7% false "clear" results. It detected 15% → 30% by 100 pairs 67% of the time, and 15% → 45% 99.7% of the time.
  - Simulation code is not in the repo; the assumptions above are enough to rerun it.

## A9. Wording rules

1. **Ask before you tell.** Show the morning questions before last night's figures [41].
2. **Name every figure as an estimate**, with its source: "Estimated sleep (from wrist movement): about 6 h 50 min". Add once per screen: "Lying still while awake counts as sleep." Never "you slept", and never a stage name.
3. **Round to what the sensor supports.** Durations and clock times to 5 min, prefixed "about". No seconds or decimals. Monthly figures as a range (the IQR) [25].
4. **Neutral comparison words only:** longer / shorter / earlier / later / more or less regular than *your usual*. Compare only with the person's own nights.
5. **No warning colours** and no icons that mark a night as worse.
6. **Show the denominator** on every average ("6 of 7 nights").
7. **Name a change only under the §A5 rules.** Otherwise say "about the same as your usual".
8. **Do not push more time in bed** [20]. The 7 h line is information, not a goal. No nightly sleep notifications.
9. **Link the method.** "How these numbers are estimated" opens a plain-language summary of this file, written under §A9 and the Stats screen's do-not-say list (Stats spec §C7), never this file itself.
10. **Offer quieter modes:** "weekly summary only" and "hide last night's numbers" [40].
11. **Label experiment nights** ("planned wake-up") so their extra wake is never shown as worse sleep.
12. **Heart rate** is shown as "average heart rate while estimated asleep", with no interpretation.
13. **Escalation wording** follows SD §6 exactly: no condition names, no thresholds [SD 100].

## A10. Limitations

- **Quiet wake counts as sleep.** Specificity is 33–53% across studies [1][7][9][10] **[strong]**.
- **WASO and awakenings are the weakest outputs** [8][9].
- **No validation on the Galaxy Watch8.** The `stillness v1` fallback has no PSG check at all.
- **Small single-night PSG samples.** The validation studies used 22–77 people, mostly one night each.
- **HDCZA was built for 24-h recordings.** Computing its threshold on the recording alone is our adaptation.
- **Truncated mornings.** A recording stopped while the user is still in bed cuts final wake at the stop.
- **A watch lying still off the wrist looks like sleep** unless the detached status codes catch it. That check is untested on real data.
- **Night-count minimums and noise floors** come from research actigraphs, not this watch.
- **The experiment simulations assume independent nights.**
- **Time recorded equals time in bed only if the user records from lying down to getting up.**

## Implementation

- **`shared/src/main/kotlin/com/opentdi/shared/stats/NightSummaries.kt`**: `summarizeNight` reads `w_packets.csv` line by line, `w_alarm.csv`, `w_morning.csv` and the phone's own `p_transitions.csv`, and fills `NightSummary` (`NightSummary.kt`). The watch never runs the Dormio states, so `mobile/.../stats/Nights.kt` passes in the rows of the phone sessions that overlap the recording. No clock and no randomness: the same files always give an equal summary.
- **`shared/src/main/kotlin/com/opentdi/shared/stats/SleepWake.kt`**: both methods, the HDCZA window, no-data gaps (an invalid run of ≥ 10 min, excluded from every sleep figure), the night measures of §A3.2 and the minute timeline. Every parameter is a named constant there, marked SOURCED with its reference number here or GUESSED; changing one changes the method's version and `SUMMARY_VERSION`.
- **Known wake** (§A1), forced to wake and ending any still bout: an alarm ring someone snoozed or stopped (one that rang out may have been slept through); a stopped wake-up alarm to the end of the recording; the WBTB break, once someone stopped its alarm, from its ring to the logged re-onset or, without one, to the first 10 min of sleep after the back-to-sleep buzz (kept out of WASO and the long wakings); each Dormio prompt until its report and re-incubation ended; the 5 min before each saved morning report.
- **`angle_z_deg`** is computed in `AccelProcessor.closeSecond` and written by the watch's `LoggingSession`.
- **Tests:** `NightSummariesTest` uses synthetic nights built in code only. `RealNightTest` summarises a local folder named by `OPENTDI_NIGHT_DIR` and is skipped without it. Real recordings are personal data and never go into this repository.

## A11. References

Sleep/wake estimation
1. van Hees VT et al. 2015. A novel, open access method to assess sleep duration using a wrist-worn accelerometer. PLoS One 10:e0142533. https://doi.org/10.1371/journal.pone.0142533 · https://pmc.ncbi.nlm.nih.gov/articles/PMC4646630/
2. van Hees VT et al. 2018. Estimating sleep parameters using an accelerometer without sleep diary. Sci Rep 8:12975. https://doi.org/10.1038/s41598-018-31266-z · https://pmc.ncbi.nlm.nih.gov/articles/PMC6113241/
3. Cole RJ et al. 1992. Automatic sleep/wake identification from wrist activity. Sleep 15:461–469. https://doi.org/10.1093/sleep/15.5.461 *(abstract only)*
4. Sadeh A, Sharkey KM, Carskadon MA. 1994. Activity-based sleep-wake identification. Sleep 17:201–207. https://doi.org/10.1093/sleep/17.3.201 *(abstract only)*
5. Walch O et al. 2019. Sleep stage prediction with raw acceleration and photoplethysmography heart rate data. Sleep 42:zsz180. https://doi.org/10.1093/sleep/zsz180 · https://pmc.ncbi.nlm.nih.gov/articles/PMC6930135/
6. Sundararajan K et al. 2021. Sleep classification from wrist-worn accelerometer data using random forests. Sci Rep 11:24. https://doi.org/10.1038/s41598-020-79217-x · https://pmc.ncbi.nlm.nih.gov/articles/PMC7794504/
7. Patterson MR et al. 2023. 40 years of actigraphy in sleep medicine and current state of the art algorithms. NPJ Digit Med 6:51. https://doi.org/10.1038/s41746-023-00802-1 · https://pmc.ncbi.nlm.nih.gov/articles/PMC10039037/ *(authors employed by ActiGraph)*
8. Plekhanova T et al. 2023. Validation of an automated sleep detection algorithm using data from multiple accelerometer brands. J Sleep Res 32:e13760. https://doi.org/10.1111/jsr.13760 *(abstract only)*
9. Marino M et al. 2013. Measuring sleep: accuracy, sensitivity, and specificity of wrist actigraphy compared to polysomnography. Sleep 36:1747–1755. https://doi.org/10.5665/sleep.3142 · https://pmc.ncbi.nlm.nih.gov/articles/PMC3792393/
10. Conley S et al. 2019. Agreement between actigraphic and polysomnographic measures of sleep in adults with and without chronic conditions: a systematic review and meta-analysis. Sleep Med Rev 46:151–160. https://doi.org/10.1016/j.smrv.2019.05.001 · https://pmc.ncbi.nlm.nih.gov/articles/PMC6594867/
11. Meredith-Jones KA et al. 2024. Validation of actigraphy sleep metrics in children aged 8 to 16 years. Int J Behav Nutr Phys Act 21:40. https://doi.org/10.1186/s12966-024-01590-x · https://pmc.ncbi.nlm.nih.gov/articles/PMC11020269/ *(a correction relabels Table 5 "Van Hees" as HDCZA)*
12. Smith MT et al. 2018. Use of actigraphy for the evaluation of sleep disorders and circadian rhythm sleep-wake disorders: an AASM systematic review, meta-analysis, and GRADE assessment. J Clin Sleep Med 14:1209–1230. https://doi.org/10.5664/jcsm.7228 · https://pmc.ncbi.nlm.nih.gov/articles/PMC6040804/
13. Smith MT et al. 2018. Use of actigraphy for the evaluation of sleep disorders and circadian rhythm sleep-wake disorders: an AASM clinical practice guideline. J Clin Sleep Med 14:1231–1237. https://doi.org/10.5664/jcsm.7230 · https://pmc.ncbi.nlm.nih.gov/articles/PMC6040807/
14. Fekedulegn D et al. 2020. Actigraphy-based assessment of sleep parameters. Ann Work Expo Health 64:350–367. https://doi.org/10.1093/annweh/wxaa007 · https://pmc.ncbi.nlm.nih.gov/articles/PMC7191872/
15. Fonseca P et al. 2017. Validation of photoplethysmography-based sleep staging compared with polysomnography in healthy middle-aged adults. Sleep 40:zsx097. https://doi.org/10.1093/sleep/zsx097 *(abstract only; Philips authors)*
16. Chinoy ED et al. 2021. Performance of seven consumer sleep-tracking devices compared with polysomnography. Sleep 44:zsaa291. https://pmc.ncbi.nlm.nih.gov/articles/PMC8120339/ *(= SD 25)*

Definitions, timing, night counts
17. Carney CE et al. 2012. The Consensus Sleep Diary: standardizing prospective sleep self-monitoring. Sleep 35:287–302. https://pmc.ncbi.nlm.nih.gov/articles/PMC3250369/ *(Table 1 not readable)*
18. Dietch JR, Taylor DJ. 2021. Evaluation of the Consensus Sleep Diary in a community sample. J Clin Sleep Med 17:1389–1399. https://pmc.ncbi.nlm.nih.gov/articles/PMC8314633/
19. Hand LK, Siengsukon CF. 2025. Actigraphy-derived time in bed: it's time to put this issue to bed. Sleep Adv 6:zpaf045. https://pmc.ncbi.nlm.nih.gov/articles/PMC12448683/ *(letter)*
20. Baron KG et al. 2017. Orthosomnia: are some patients taking the quantified self too far? J Clin Sleep Med 13:351–354. https://pmc.ncbi.nlm.nih.gov/articles/PMC5263088/
21. Phillips AJK et al. 2017. Irregular sleep/wake patterns are associated with poorer academic performance and delayed circadian and sleep/wake timing. Sci Rep 7:3216. https://pmc.ncbi.nlm.nih.gov/articles/PMC5468315/
22. Windred DP et al. 2024. Sleep regularity is a stronger predictor of mortality risk than sleep duration. Sleep 47:zsad253. https://pmc.ncbi.nlm.nih.gov/articles/PMC10782501/
23. Fischer D, Klerman EB, Phillips AJK. 2021. Measuring sleep regularity: theoretical properties and practical usage of existing metrics. Sleep 44:zsab103. https://pmc.ncbi.nlm.nih.gov/articles/PMC8503839/
24. Roenneberg T et al. 2019. Chronotype and social jetlag: a (self-) critical review. Biology 8:54. https://pmc.ncbi.nlm.nih.gov/articles/PMC6784249/
25. Ravichandran R et al. 2017. Making sense of sleep sensors: how sleep sensing technologies support and undermine sleep health. CHI 2017. https://doi.org/10.1145/3025453.3025557
26. Sletten TL et al. 2023. The importance of sleep regularity: a consensus statement of the National Sleep Foundation sleep timing and variability panel. Sleep Health 9:801–820. https://doi.org/10.1016/j.sleh.2023.07.016 *(abstract only)*
27. Bei B et al. 2016. Beyond the mean: a systematic review on the correlates of daily intraindividual variability of sleep/wake patterns. Sleep Med Rev 28:108–124. https://doi.org/10.1016/j.smrv.2015.06.003 *(abstract only)*
28. Roenneberg T, Wirz-Justice A, Merrow M. 2003. Life between clocks: daily temporal patterns of human chronotypes. J Biol Rhythms 18:80–90. https://doi.org/10.1177/0748730402239679 *(abstract only)*
29. Wittmann M et al. 2006. Social jetlag: misalignment of biological and social time. Chronobiol Int 23:497–509. https://doi.org/10.1080/07420520500545979 *(abstract only)*
30. Aili K et al. 2017. Reliability of actigraphy and subjective sleep measurements in adults: the design of sleep assessments. J Clin Sleep Med 13:39–47. https://pmc.ncbi.nlm.nih.gov/articles/PMC5181612/
31. Wohlgemuth WK et al. 1999. How many nights are enough? The short-term stability of sleep parameters in elderly insomniacs and normal sleepers. Psychophysiology 36:233–244. https://doi.org/10.1111/1469-8986.3620233 *(abstract only)*
32. Rowe M et al. 2008. Actigraphy in older adults: comparison of means and variability of three different aggregates of measurement. Behav Sleep Med 6:127–145. https://doi.org/10.1080/15402000801952872 *(abstract only)*
33. Van Someren EJ. 2007. Improving actigraphic sleep estimates in insomnia and dementia: how many nights? J Sleep Res 16:269–275. https://doi.org/10.1111/j.1365-2869.2007.00592.x *(abstract only)*

Display, wording, feedback effects
34. Refinetti R, Cornélissen G, Halberg F. 2007. Procedures for numerical analysis of circadian rhythms. Biol Rhythm Res 38:275–325. https://pmc.ncbi.nlm.nih.gov/articles/PMC3663600/
35. Kay M et al. 2012. Lullaby: a capture & access system for understanding the sleep environment. UbiComp 2012:226–234. https://doi.org/10.1145/2370216.2370253 *(abstract only)*
36. Liang Z, Ploderer B. 2016. Sleep tracking in the real world: a qualitative study into barriers for improving sleep. OzCHI 2016:537–541. https://doi.org/10.1145/3010915.3010988 *(abstract only)*
37. Watson NF et al. 2015. Recommended amount of sleep for a healthy adult (AASM/SRS). https://pmc.ncbi.nlm.nih.gov/articles/PMC4442216/ *(= SD 19)*
38. Hirshkowitz M et al. 2015. National Sleep Foundation's sleep time duration recommendations: methodology and results summary. Sleep Health 1:40–43. https://doi.org/10.1016/j.sleh.2014.12.010 *(abstract only)*
39. Ohayon M et al. 2017. National Sleep Foundation's sleep quality recommendations: first report. Sleep Health 3:6–19. https://doi.org/10.1016/j.sleh.2016.11.006 *(abstract only; cited only as "not drawn")*
40. Guldbrandsen BV et al. 2025. Development of a scale for measuring orthosomnia: the Bergen Orthosomnia Scale. Front Sleep 4:1640355. https://pmc.ncbi.nlm.nih.gov/articles/PMC12713983/ *(full text)*
41. Gavriloff D et al. 2018. Sham sleep feedback delivered via actigraphy biases daytime symptom reports in people with insomnia. J Sleep Res 27:e12726. https://doi.org/10.1111/jsr.12726 *(abstract only)*
42. Draganich C, Erdal K. 2014. Placebo sleep affects cognitive functioning. J Exp Psychol Learn Mem Cogn 40:857–864. https://doi.org/10.1037/a0035546 *(abstract only)*
43. Spina MA et al. 2023. Does providing feedback and guidance on sleep perceptions using sleep wearables improve insomnia? Findings from "Novel Insomnia Treatment Experiment": a randomized controlled trial. Sleep 46:zsad167. https://pmc.ncbi.nlm.nih.gov/articles/PMC10485571/ *(full text)*

Dreams
44. Schredl M. 2004. Reliability and stability of a dream recall frequency scale. Percept Mot Skills 98:1422–1426. https://doi.org/10.2466/pms.98.3c.1422-1426 *(abstract only)*
45. Aspy DJ, Delfabbro P, Proeve M. 2015. Is dream recall underestimated by retrospective measures and enhanced by keeping a logbook? A review. Conscious Cogn 33:364–374. https://doi.org/10.1016/j.concog.2015.02.005 *(abstract only; erratum 2023, Conscious Cogn 113:103550 / 103549; not read)*
46. Aspy DJ. 2016. Is dream recall underestimated by retrospective measures and enhanced by keeping a logbook? An empirical investigation. Conscious Cogn 42:181–203. https://doi.org/10.1016/j.concog.2016.03.015 *(abstract only; erratum 2023, Conscious Cogn 113:103550 / 103549; not read)*
47. Schredl M et al. 2022. Dream recall frequency, lucid dream frequency, and personality during the Covid-19 pandemic. Imagin Cogn Pers 42:113–133. https://pmc.ncbi.nlm.nih.gov/articles/PMC9149660/ *(source of the Stumbrys et al. 2013 retest figure; that paper could not be opened)*
48. Voss U et al. 2013. Measuring consciousness in dreams: the lucidity and consciousness in dreams scale. Conscious Cogn 22:8–21. https://doi.org/10.1016/j.concog.2012.11.001 *(full text, author copy)*
49. Loo MR, Cheng SK. 2021. Dream lucidity and the Attentional Network Task. Front Psychol 12:586808. https://pmc.ncbi.nlm.nih.gov/articles/PMC7876060/
50. García-Campayo J et al. 2021. Validation of the Spanish version of the LuCiD scale. Front Psychol 12:742438. https://pmc.ncbi.nlm.nih.gov/articles/PMC8566340/
51. Fazekas P, Nemeth G, Overgaard M. 2019. White dreams are made of colours: what studying contentless dreams can teach about the neural basis of dreaming and conscious experiences. Sleep Med Rev 43:84–91. https://doi.org/10.1016/j.smrv.2018.10.005 *(abstract only)*
52. Robert G, Zadra A. 2008. Measuring nightmare and bad dream frequency: impact of retrospective and prospective instruments. J Sleep Res 17:132–139. https://doi.org/10.1111/j.1365-2869.2008.00649.x *(abstract only)*
53. Schredl M et al. 2021. Nightmare Distress Questionnaire: associated factors. J Clin Sleep Med 17:61–67. https://pmc.ncbi.nlm.nih.gov/articles/PMC7849636/
54. Stucky B. 2025. How awakenings during sleep influence dream recall. Nat Sci Sleep 17:709–729. https://pmc.ncbi.nlm.nih.gov/articles/PMC12053782/ *(non-systematic review)*

N-of-1 method and uncertainty
55. Shamseer L et al. 2015. CONSORT extension for reporting N-of-1 trials (CENT) 2015: explanation and elaboration. BMJ 350:h1793. https://doi.org/10.1136/bmj.h1793
56. Kravitz RL, Duan N, eds. 2014. Design and Implementation of N-of-1 Trials: A User's Guide, ch. 4 (Schmid CH, Duan N). AHRQ Publication No. 13(14)-EHC122-EF. https://effectivehealthcare.ahrq.gov/products/n-1-trials/research-2014-1 *(read from the AHRQ PDF archived Feb 2014)*
57. Heyvaert M, Onghena P. 2014. Analysis of single-case data: randomisation tests for measures of effect size. Neuropsychol Rehabil 24:507–527. https://doi.org/10.1080/09602011.2013.818564 *(abstract only)*
58. Brown LD, Cai TT, DasGupta A. 2001. Interval estimation for a binomial proportion. Stat Sci 16:101–133. https://doi.org/10.1214/ss/1009213286 *(abstract only)*
59. van der Bles AM et al. 2020. The effects of communicating uncertainty on public trust in facts and numbers. PNAS 117:7672–7683. https://pmc.ncbi.nlm.nih.gov/articles/PMC7149229/
60. Garcia-Retamero R, Galesic M. 2009. Communicating treatment risk reduction to people with low numeracy skills. Am J Public Health 99:2196–2202. https://pmc.ncbi.nlm.nih.gov/articles/PMC2775772/
61. Padilla LM et al. 2018. Decision making with visualizations: a cognitive framework across disciplines. Cogn Res Princ Implic 3:29. https://pmc.ncbi.nlm.nih.gov/articles/PMC6091269/
62. Recchia G, Lawrence ACE, Freeman ALJ. 2021. Investigating the presentation of uncertainty in an icon array: a randomized trial. PEC Innov 1:100003. https://pmc.ncbi.nlm.nih.gov/articles/PMC9731905/
63. Kay M, Kola T, Hullman J, Munson SA. 2016. When (ish) is my bus? User-centered visualizations of uncertainty in everyday, mobile predictive systems. CHI 2016. https://doi.org/10.1145/2858036.2858558
64. Karkar R et al. 2017. TummyTrials: a feasibility study of using self-experimentation to detect individualized food triggers. CHI 2017:6850–6863. https://doi.org/10.1145/3025453.3025480 · https://pmc.ncbi.nlm.nih.gov/articles/PMC5432136/
65. Daskalova N et al. 2020. SleepBandits: guided flexible self-experiments for sleep. CHI 2020. https://doi.org/10.1145/3313831.3376584

**Checked, with limits.** An independent check (2026-10-28) opened 57 of these records and corrected this file against them. It could see only metadata or abstracts for [25], [35], [36], [55], [63] and [65], so the "87 / 17 / 6,986" counts and the SleepBandits "76% … from 12 nights" quote are unchecked; only the abstract of [48], so its 28 items, 2.93 points and 2.5 anchor are unchecked; and not the AHRQ chapter [56], so its quotes are unchecked. The §A8 peeking simulation was not rerun.

**Not used.**
- The SBSM actigraphy guide (Ancoli-Israel 2015): paywalled, no abstract.
- Webster 1982 original rescoring rules: not opened.
- The Oakley Actiwatch report: not peer reviewed.
- GGIR software documentation: not a permitted source.
- Vohra 2015 CENT statement: record only; its checklist was read via [55].
- Choe 2011, Spiegelhalter 2017, Newcombe 1998, Hullman 2019, van der Bles 2019, Galesic 2009 and De Carvalho 2026: not opened, or added nothing.
- The IJoDR weekday papers: blocked.
- Natale 2015, Kushida 2001, te Lindert 2013 and Ancoli-Israel 2003: no numbers used.
- Applied but not repeated: SD 34 (AASM consumer sleep technology statement) and SD 100 (FDA 2026 general wellness guidance).
