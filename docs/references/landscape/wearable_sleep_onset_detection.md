# Detecting sleep onset with wearables: algorithms, validation evidence, and what OpenTDI can expect

Research extract for OpenTDI, made 2026-10-27. Scope: detecting sleep onset (especially N1) with
consumer wearables, and if possible in real time. It covers:

- actigraphy algorithms;
- wrist PPG and motion sleep staging;
- the consumer-device validations that matter for wake vs. light sleep and sleep-onset latency (SOL);
- real-time or closed-loop drowsiness and onset detection;
- wrist EDA;
- a self-evaluation protocol the owner can run on himself.

**Labels.**
- **FACT**: stated in a source I read. Each one is tagged with how much I read: full text, abstract only, code, or
  vendor document.
- **INFERENCE**: my own reasoning or arithmetic.
- **NOT VERIFIED**: I could not confirm it.

**What this file does not repeat.** It points to these sibling files instead:

- `../horowitz2020_dormio_paper.md` [P]: Dormio thresholds, the 120 s baseline, the 1–5 min post-onset delay and the
  self-report question.
- `../opensleep_dormio_code_analysis.md` [O]: the openSleep and dormio detector code.
- `../dormio_duo_wearable_analysis.md` [D]: §10.5 wrist EDA site, §10.6 "limp wrist" from the accelerometer.
- `../samsung_health_sensor_sdk.md` [S]: screen-off batching, `flush()`, and gravity-free accelerometer data.
- `sleep_onset_physiology.md` [PHYS]: the physiology of the wake→N1 transition (HR, HRV, EDA, temperature, Edison drop,
  THIM ring). Section 5 below only summarises it.

---

## 0. Key findings (read this first)

1. **No source I found validates real-time sleep-onset detection on a consumer smartwatch against PSG.**
   - FACT: Every consumer validation I read scores 30 s epochs **offline**, after the night.
   - FACT: Most report "light" sleep (N1+N2 merged) and never report wake vs. N1.
   - FACT: The only device in these studies that reports N1 separately (Somfit, a forehead-EEG device) detected **1%** of
     N1 epochs (MIL22 Table 3).
   - FACT: Even human scorers agree on N1 only 63% of the time (ROS13).
   - INFERENCE: OpenTDI's detector will be one of the first open real-time attempts. Its claims should stay modest.
2. **Every standard algorithm is non-causal.** Each one uses data from *after* the epoch it scores:
   - Cole-Kripke: 2 min of look-ahead.
   - Sadeh: 5 min.
   - Oakley/Actiware: 1–2 min.
   - Walch 2019: ±5 min windows, plus a whole-night HR normalisation.
   - van Hees: 5 min of no angle change.
   - Oura (Altini 2021): per-night normalisation.

   FACT: from the code or the methods ([PYA]; WAL19; HVH15; ALT21). INFERENCE: A real-time port needs explicit causal
   windows and inherits a detection lag of one to several minutes.
3. **Motion alone mistakes quiet wakefulness for sleep. This is the main false-positive source.**
   - FACT: Wrist actigraphy sensitivity for sleep is 0.965, but its specificity (wake correctly scored) is 0.329 (MAR13).
   - FACT: The Actiwatch underestimated SOL by 7.6 min on average, with limits of agreement −24.1 to +8.8 min (CHI21
     Table 3).
   - FACT: An AASM meta-analysis found actigraphy SL 6.17 min lower than PSG in insomnia (12 studies; AASM18).
   - FACT: In a PSG-recorded **no-nap rest** condition, most actigraph settings "misidentif[ied] sleep" (KAN11, abstract).
   - FACT: Apple's 2025 algorithm update was aimed specifically at "quiet wake ... when users are awake but very still,
     often occurring just before or after sleep" (APL25 p.9).
4. **Adding heart rate helps staging much more than it helps wake vs. sleep.**
   - FACT: In Walch 2019, HR alone scored only 24–33% of wake epochs correctly at 90% sleep sensitivity. Motion alone
     scored 48–55%. Motion + HR added roughly 3%; the circadian "clock proxy" added about 14%.
   - FACT: HR improved the NREM/REM accuracy by 15–25% (WAL19, Results).
5. **Wake detection in current Galaxy Watches is about a coin flip at the epoch level.** FACT:
   - Galaxy Watch 3: sleep-detection specificity 0.524 (KIM23).
   - Galaxy Watch 5: wake sensitivity 0.481, wake specificity 0.910, 4-stage κ 0.418 (LEE23 Tables 2–3, n = 22).
   - Other wrist devices fall in the same band:
     - Apple Watch S6: 26%; Garmin: 27%; Polar: 51%; Oura Gen2: 57%; WHOOP 3.0: 56% (MIL22);
     - consumer devices 0.18–0.54 (CHI21);
     - Apple's own 2025 figure is 78.9% (vendor white paper, APL25).
6. **The physiology a wrist can see changes slowly and is mixed with "lying down" effects.** See [PHYS] §1 and §6.
   - FACT: HR falls about 0.53 bpm every 30 s over the 5 min before stable sleep (DZ20).
   - FACT: HR and blood pressure fall "after lights out before the initial onset of theta activity", that is, during
     wakefulness (CAR05, abstract).
   - INFERENCE: An HR drop from a lie-down baseline is confounded with time in bed. It will tend to fire **early**.
7. **Wrist EDA is weak for onset.**
   - FACT: 4% of the night's wrist EDA peaks fell in NREM1 (a share of all peaks, which mostly reflects little time in N1), and wrist EDA is analysed only during sleep, on the inner wrist (SAN14). No source tests wrist SCRs for wake versus N1.
   - FACT: An EDA-only sleep/wake algorithm reached 97% sensitivity and 75% specificity offline (HER19, abstract only).
   - FACT: A 2026 preprint reports an "inverted" tonic-EDA stage pattern on the E4 wrist. It attributes this to sweat
     building up in deep sleep (PAR26, abstract only, preprint).
   - INFERENCE: EDA could serve as an arousal veto or a "too deep" flag, as [D] §10.5 concludes, but a veto conflicts with owner requirement 11 (EDA bonus-only) and needs the owner's sign-off.
8. **Real-time systems that do work use EEG or a behavioural task.**
   - FACT: Elemind-type closed-loop alpha stimulation is EEG-based (BRE24).
   - FACT: The Edison bottle drop marks about 1 min of accumulated microsleep. It woke subjects every time (LAC21).
   - FACT: The best wearable-only drowsiness detector I found is driver-HRV anomaly detection. It caught 12 of 13 pre-N1
     episodes at **1.7 false positives per hour** (FUJ19, abstract only).
9. **Per-person models beat population models by a wide margin.**
   - FACT: Wrist HRV drowsiness accuracy was >92% user-dependent. User-independent tests were lower, e.g. 65.64% (SVM);
     the best was 78.94%, and that was with the ECG reference rather than the wristband (KUN20).
   - INFERENCE: A single-session baseline is a crude form of personalisation. Learning thresholds across sessions from
     the owner's own labels would help more.
10. **Usable open datasets exist to replay a detector against PSG-labelled N1.**
    - FACT: PhysioNet `sleep-accel` (Walch): Apple Watch raw acceleration + HR, PSG labels with N1 = 1, Open Access, ODC-By
      1.0 (SLA19).
    - FACT: DREAMT: Empatica E4 BVP/ACC/**EDA**/TEMP, PSG with separate N1, restricted access under a data use
      agreement (DREAMT).

---

## 1. Sources and how each was read

| Key | Citation | DOI / URL | Read |
|---|---|---|---|
| AASM18 | Smith MT, McCrae CS, Cheung J, et al. (2018). Use of actigraphy for the evaluation of sleep disorders and circadian rhythm sleep-wake disorders: an AASM systematic review, meta-analysis, and GRADE assessment. *J Clin Sleep Med* | 10.5664/jcsm.7228; PMC6040804 | Full text (HTML). The meta-analysis forest plots are in the supplement, which I did not read |
| ALT21 | Altini M, Kinnunen H (2021). The promise of sleep: a multi-sensor approach for accurate sleep stage detection using the Oura ring. *Sensors* 21:4302 | 10.3390/s21134302; PMC8271886 | Full text |
| APL25 | Apple Inc. (Oct 2025). *Estimating Sleep Stages from Apple Watch* (white paper) | https://www.apple.com/kr/health/pdf/Estimating_Sleep_Stages_from_Apple_Watch_Oct_2025.pdf | Full text (vendor document, not peer reviewed) |
| BEA17 | Beattie Z, Oyang Y, Statan A, et al. (2017). Estimation of sleep stages in a healthy adult population from optical plethysmography and accelerometer signals. *Physiol Meas* 38:1968–1979 | 10.1088/1361-6579/aa9047 | Abstract only |
| BM82 | Bonnet MH, Moore SE (1982). The threshold of sleep: perception of sleep as a function of time asleep and auditory threshold. *Sleep* 5:267–276 | 10.1093/sleep/5.3.267 | Abstract only |
| BRE24 | Bressler S, Neely R, Yost RM, Wang D (2024). A randomized controlled trial of alpha phase-locked auditory stimulation to treat symptoms of sleep onset insomnia. *Sci Rep* 14:13039 | 10.1038/s41598-024-63385-1; PMC11156862 | Abstract only |
| BUR99 | Burgess HJ, Kleiman J, Trinder J (1999). Cardiac activity during sleep onset. *Psychophysiology* 36:298–306 | 10.1017/s0048577299980198 | Abstract only |
| CAR05 | Carrington MJ, Barbieri R, Colrain IM, et al. (2005). Changes in cardiovascular function during the sleep onset period in young adults. *J Appl Physiol* 98:468–476 | 10.1152/japplphysiol.00702.2004 | Abstract only |
| CEL13 | Cellini N, Buman MP, McDevitt EA, Ricker AA, Mednick SC (2013). Direct comparison of two actigraphy devices with polysomnographically recorded naps in healthy young adults. *Chronobiol Int* 30:691–698 | 10.3109/07420528.2013.782312 | Abstract only |
| CHI21 | Chinoy ED, Cuellar JA, Huwa KE, et al. (2021). Performance of seven consumer sleep-tracking devices compared with polysomnography. *Sleep* 44:zsaa291 | 10.1093/sleep/zsaa291; PMC8120339 | Full text |
| CK92 | Cole RJ, Kripke DF, Gruen W, Mullaney DJ, Gillin JC (1992). Automatic sleep/wake identification from wrist activity. *Sleep* 15:461–469 | 10.1093/sleep/15.5.461 | Abstract, plus the pyActigraphy implementation [PYA]. Original PDF not read |
| DREAMT | Wang K, Yang J, Shetty A, Dunn J (2026). *DREAMT: Dataset for Real-time sleep stage EstimAtion using Multisensor wearable Technology*, v2.2.0. PhysioNet | 10.13026/3f7y-2d80 | Dataset landing page |
| DZ11 | de Zambotti M, Covassin N, De Min Tona G, Sarlo M, Stegagno L (2011). Sleep onset and cardiovascular activity in primary insomnia. *J Sleep Res* 20:318–325 | 10.1111/j.1365-2869.2010.00871.x | Abstract only |
| DZ20 | de Zambotti M, Goldstone A, Forouzanfar M, et al. (2020). The falling asleep process in adolescents. *Sleep* 43:zsz312 | 10.1093/sleep/zsz312; PMC7294410 | Full text |
| DZ24 | de Zambotti M, Goldstein C, Cook J, et al. (2024). State of the science and recommendations for using wearable technology in sleep and circadian research. *Sleep* 47:zsad325 | 10.1093/sleep/zsad325 | Abstract only |
| FON17 | Fonseca P, Weysen T, Goelema MS, et al. (2017). Validation of photoplethysmography-based sleep staging compared with polysomnography in healthy middle-aged adults. *Sleep* 40:zsx097 | 10.1093/sleep/zsx097 | Abstract only |
| FUJ19 | Fujiwara K, Abe E, Kamata K, et al. (2019). Heart rate variability-based driver drowsiness detection and its validation with EEG. *IEEE Trans Biomed Eng* 66:1769–1778 | 10.1109/tbme.2018.2879346 | Abstract only. Window lengths and training data NOT VERIFIED |
| GPPG25 | Park S, Zheng D, Lee U (2025). A PPG signal dataset collected in semi-naturalistic settings using Galaxy Watch. *Sci Data* 12:892 | 10.1038/s41597-025-05152-z; PMC12119839 | Full text (relevant tables) |
| HAG19a | Haghayegh S, Khoshnevis S, Smolensky MH, Diller KR, Castriotta RJ (2019). Performance comparison of different interpretative algorithms utilized to derive sleep parameters from wrist actigraphy data. *Chronobiol Int* 36:1752–1760 | 10.1080/07420528.2019.1679826 | Abstract only |
| HAG19b | Haghayegh S, et al. (2019). Accuracy of wristband Fitbit models in assessing sleep: systematic review and meta-analysis. *J Med Internet Res* 21:e16273 | 10.2196/16273; PMC6908975 | Abstract only |
| HER19 | Herlan A, Ottenbacher J, Schneider J, Riemann D, Feige B (2019). Electrodermal activity patterns in sleep stages and their utility for sleep versus wake classification. *J Sleep Res* 28:e12694 | 10.1111/jsr.12694 | Abstract only |
| HVH15 | van Hees VT, Sabia S, Anderson KN, et al. (2015). A novel, open access method to assess sleep duration using a wrist-worn accelerometer. *PLoS One* 10:e0142533 | 10.1371/journal.pone.0142533; PMC4646630 | Full text |
| HVH18 | van Hees VT, Sabia S, Jones SE, et al. (2018). Estimating sleep parameters using an accelerometer without sleep diary. *Sci Rep* 8:12975 | 10.1038/s41598-018-31266-z; PMC6113241 | Full text (methods) |
| IDE | `idealemu/TDI`, "A successful replication of Dormio TDI" (README), commit e4c49d3 | https://github.com/idealemu/TDI | Code read (`TDI.py`, `Board/code.py`). **No license** (GitHub API `license: null`) |
| KAN11 | Kanady JC, Drummond SP, Mednick SC (2011). Actigraphic assessment of a polysomnographic-recorded nap: a validation study. *J Sleep Res* 20:214–222 | 10.1111/j.1365-2869.2010.00858.x | Abstract only |
| KIM23 | Kim D, Joo EY, Choi SJ (2023). Validation of the Samsung smartwatch for sleep–wake determination and sleep stage estimation. *J Sleep Med* 20(1):28–34 | 10.13078/jsm.230004; https://www.e-jsm.org/journal/view.php?number=358 | Full text HTML, read through a WebFetch summary. I checked the key sentences in the raw HTML with curl |
| KRA00 | Kräuchi K, Cajochen C, Werth E, Wirz-Justice A (2000). Functional link between distal vasodilation and sleep-onset latency? *Am J Physiol Regul Integr Comp Physiol* 278:R741–R748 | 10.1152/ajpregu.2000.278.3.r741 | Abstract only |
| KUN20 | Kundinger T, Sofra N, Riener A (2020). Assessment of the potential of wrist-worn wearable sensors for driver drowsiness detection. *Sensors* 20:1029 | 10.3390/s20041029; PMC7070962 | Full text (methods, results) |
| LAC21 | Lacaux C, Andrillon T, Bastoul C, et al. (2021). Sleep onset is a creative sweet spot. *Sci Adv* 7:eabj5866 | 10.1126/sciadv.abj5866; PMC8654287 | Full text |
| LEE23 | Lee T, Cho Y, Cha KS, et al. (2023). Accuracy of 11 wearable, nearable, and airable consumer sleep trackers: prospective multicenter validation study. *JMIR mHealth uHealth* 11:e50983 | 10.2196/50983; PMC10654909 | Full text |
| LEEHS26 | Lee HS, Song J, Jung M, et al. (2026). Prediction of vasovagal syncope using AI-enabled smartwatch PPG-derived HRV. *Eur Heart J Digit Health* 7:ztag053 | 10.1093/ehjdh/ztag053 | Abstract only |
| MAR13 | Marino M, Li Y, Rueschman MN, et al. (2013). Measuring sleep: accuracy, sensitivity, and specificity of wrist actigraphy compared to polysomnography. *Sleep* 36:1747–1755 | 10.5665/sleep.3142; PMC3792393 | Full text |
| MEN21 | Menghini L, Cellini N, Goldstone A, Baker FC, de Zambotti M (2021). A standardized framework for testing the performance of sleep-tracking technology. *Sleep* 44:zsaa170 | 10.1093/sleep/zsaa170 | Abstract only |
| MIL22 | Miller DJ, Sargent C, Roach GD (2022). A validation of six wearable devices for estimating sleep, heart rate and heart rate variability in healthy adults. *Sensors* 22:6317 | 10.3390/s22166317; PMC9412437 | Full text (tables) |
| OGI89 | Ogilvie RD, Wilkinson RT, Allison S (1989). The detection of sleep onset: behavioral, physiological, and subjective convergence. *Sleep* 12:458–474 | 10.1093/sleep/12.5.458 | Abstract only |
| PAR26 | Parry YD, Briganti G (2026). Validity and limitations of the Empatica E4 wristband for autonomic and thermoregulatory sleep monitoring against concurrent polysomnography (preprint) | 10.64898/2026.06.10.26355348 | Abstract only. **Preprint, not peer reviewed** |
| PYA | `ghammad/pyActigraphy`, `pyActigraphy/sleep/scoring_base.py`, commit c32eb08 (2026-10-27). **GPL-3.0** | https://github.com/ghammad/pyActigraphy | Code and docstrings read |
| ROB24 | Robbins R, Weaver MD, Sullivan JP, et al. (2024). Accuracy of three commercial wearable devices for sleep tracking in healthy adults. *Sensors* 24:6532 | 10.3390/s24206532 | Abstract only |
| ROS13 | Rosenberg RS, Van Hout S (2013). The AASM inter-scorer reliability program: sleep stage scoring. *J Clin Sleep Med* 9:81–87 | 10.5664/jcsm.2350 | Abstract only |
| SAD94 | Sadeh A, Sharkey KM, Carskadon MA (1994). Activity-based sleep-wake identification: an empirical test of methodological issues. *Sleep* 17:201–207 | 10.1093/sleep/17.3.201 | Abstract, plus [PYA] |
| SAN14 | Sano A, Picard RW, Stickgold R (2014). Quantitative analysis of wrist electrodermal activity during sleep. *Int J Psychophysiol* 94:382–389 | 10.1016/j.ijpsycho.2014.09.011; PMC4335672 | Full text |
| SDB24 | Samsung Developers blog (2024-01-22). "Managing Sleep Data with Samsung Health and Health Connect" | https://developer.samsung.com/health/blog/en/managing-sleep-data-with-samsung-health-and-health-connect | WebFetch summary |
| SHI06 | Shinar Z, Akselrod S, Dagan Y, Baharav A (2006). Autonomic changes during wake-sleep transition: a heart rate variability based approach. *Auton Neurosci* 130:17–27 | 10.1016/j.autneu.2006.04.006 | Abstract only |
| SIL24 | Silva FB, Uribe LFS, Cepeda FX, et al. (2024). Sleep staging algorithm based on smartwatch sensors for healthy and sleep apnea populations. *Sleep Med* 119:535–548 | 10.1016/j.sleep.2024.05.033 | Abstract only. It appeared in a Europe PMC search restricted to `AFF:"Samsung Electronics"`; I did not see the author affiliations themselves |
| SLA19 | Walch O (2019). *Motion and heart rate from a wrist-worn wearable and labeled sleep from polysomnography*, v1.0.0. PhysioNet | 10.13026/hmhs-py35 | Dataset landing page |
| TAL26 | Tallal U, Agrawal R, Kshirsagar S (2026). Wearable sleep-stage classification: a systematic analysis of signal modalities... *Sensors* 26:5746 | 10.3390/s26185746 | Abstract only |
| TEL13 | te Lindert BH, Van Someren EJ (2013). Sleep estimates using microelectromechanical systems (MEMS). *Sleep* 36:781–789 | 10.5665/sleep.2648 | Abstract only |
| TRI01 | Trinder J, Kleiman J, Carrington M, et al. (2001). Autonomic activity during human sleep as a function of time and sleep stage. *J Sleep Res* 10:253–264 | 10.1046/j.1365-2869.2001.00263.x | Abstract only |
| VIC16 | Vicente J, Laguna P, Bartra A, Bailón R (2016). Drowsiness detection using heart rate variability. *Med Biol Eng Comput* 54:927–937 | 10.1007/s11517-015-1448-7 | Abstract only |
| WAL19 | Walch O, Huang Y, Forger D, Goldstein C (2019). Sleep stage prediction with raw acceleration and photoplethysmography heart rate data derived from a consumer wearable device. *Sleep* 42:zsz180 | 10.1093/sleep/zsz180; PMC6930135 | Full text |

Full texts came from the Europe PMC REST `fullTextXML` endpoint or from PMC HTML, fetched with curl. Abstracts came from
the Europe PMC REST search (`resultType=core`).

---

## 2. Ground truth is itself fuzzy at sleep onset

Read this before judging any detector. Numbers in this section are FACT unless marked.

- **Human scorers agree least on N1.** Inter-scorer agreement was 82.6% overall, but "lowest for stage N1 at 63.0%".
  "Scorers had particular difficulty with the last epoch of stage W before sleep onset" (ROS13, 2,500+ scorers,
  abstract).
- **N1 is brief in naps.** In a 20 min semi-reclined break, subjects who reached only N1 spent 1.35 ± 1.20 min in it,
  and 3 of the 14 who reached N2 went straight into N2 without N1 (LAC21, Results). [PHYS] §1 has more.
- **Consumer EEG does not fix this.** The forehead-EEG Somfit detected 1% of N1 epochs (MIL22 Table 3). Its N1 bias
  was −35.7 ± 17.1 min per night (MIL22 Table 4).
- **Behaviour and subjective report lag the EEG.**
  - "responding continued in both light 'sleep' stages" (stages 1 and 2) to a faint auditory cue (OGI89, abstract).
  - The point at which subjects reported sleep 50% of the time "occurred 2–4 min after the first sleep spindle", that
    is, in N2 (BM82, abstract).
  - INFERENCE: Many correct N1 awakenings will be answered "I was awake". MIT's three-way Awake / Halfway / Asleep
    answer ([P] §2.2) is therefore a biased label. Treat "Halfway" as a hit.
- **The definition matters.**
  - SOL is "the time from bedtime to the first epoch scored as any sleep stage".
  - Latency to persistent sleep (LPS) is the time to "the first epoch of 10 consecutive minutes scored as any sleep
    stage" (CHI21 Methods).
  - DZ20 used three onset definitions. Stable sleep (SO-C) is "the first epoch of four consecutive epochs of stable sleep
    (N2/N3)" (DZ20 Methods).
  - INFERENCE: OpenTDI's target is the *start* of N1, the most fragile of these definitions.

---

## 3. Actigraphy algorithms

### 3.1 Definitions and causality

| Algorithm | Epoch | Scoring rule (FACT) | Data used around the scored epoch | Look-ahead (INFERENCE from the window) |
|---|---|---|---|---|
| **Cole-Kripke 1992** (CK92; formula from [PYA] `CK` docstring) | 1 min | D = P·Σ Wᵢ·Aᵢ over minutes −4…+2. **D < 1 = sleep**. In the pyActigraphy "mean" setting, P = 0.001 and the weights are 106, 54, 58, 76, **230** (present minute), 74, 67. Other device modes use different scale factors ([PYA] lines 425–521) | 4 min before, 2 min after | **2 min** |
| Webster rescoring (used with CK) | 1 min | "After at least 4 minutes scored as wake, the next 1 minute scored as sleep is rescored as wake". Also 10→3 min, 15→4 min, and isolated sleep runs of ≤ 6 min (inside ≥ 15 min of wake) or ≤ 10 min (inside ≥ 20 min of wake) are rescored as wake ([PYA] docstring) | up to 20 min | up to 20 min (offline) |
| **Sadeh 1994** (SAD94; [PYA] `Sadeh`) | 1 min | PS = 7.601 − 0.065·meanW5 − 1.08·NAT − 0.056·sdLast6 − 0.703·ln(act+1); **PS ≥ 0 = sleep**. meanW5 = mean counts over ±5 epochs. NAT = number of epochs with 50 ≤ counts < 100 in the 11-epoch window. sdLast6 = SD of the scored epoch and the 5 before it | ±5 min | **5 min** |
| **Oakley / Actiware** ([PYA] `Oakley`; Oakley 1997 technical report, not read) | 15 s, 30 s, 1 min, 2 min | Weighted sum compared with a threshold (pyActigraphy default 40; Actiware "medium" in CHI21). At 30 s epochs: W±4 = W±3 = 1/25, W±2 = W±1 = 1/5, W0 = 2. An "automatic" threshold uses **all** counts in the recording × 0.88888 / mobile time | ±2 min at 30 s epochs | **2 min**. The automatic threshold is offline |
| **van Hees 2015** (HVH15) | 5 s | Arm angle = atan(a_z / √(a_x²+a_y²))·180/π, from 5 s rolling medians. "Periods of time during which there was no change larger than 5° over at least 5 minutes were classified as bouts of sustained inactivity" | 5 min | **≥ 5 min**: a bout is known only 5 min after it starts |
| **HDCZA** (HVH18) | 5 s → 5 min rolling | Threshold = 10th percentile of the day's angle-change series × 15; the longest block of the noon-to-noon day is the sleep period. "The factor 15 ... was derived iteratively using visual inspection" | whole day | offline only |
| Raw MEMS → counts (TEL13; used by WAL19) | 15 s counts from 50 Hz | A transformation from raw accelerometry to traditional counts. Epoch agreement with actigraph scoring κ = 0.87 / 0.85 / 0.83 at the low / medium / high thresholds (abstract) | — | — |

**Samsung-specific caveat** (FACT, [S] §0 #6): the SDK javadoc says "Accelerometer data doesn't include gravity". The
SDK stream is 25 Hz, not the 50 Hz TEL13 assumed.
- INFERENCE: If that is true on the SM-L500, the van Hees arm-angle method cannot be computed. The count conversion
  would also need re-validation.
- Verify this on hardware first. It decides whether "limp wrist" ([D] §10.6) can use orientation at all.

### 3.2 Accuracy against PSG, especially SOL

| Study | Population / setting | Sleep sensitivity | Wake specificity | SOL result |
|---|---|---|---|---|
| CK92 (abstract) | 41 adults, overnight PSG | — | — | "correctly distinguished sleep from wakefulness approximately 88% of the time"; sleep-latency correlation r = 0.90 with PSG |
| SAD94 (abstract) | 20 adults + 16 adolescents | — | — | Agreement 91–93%; "Agreement for sleep scoring was consistently higher than for wake scoring" |
| MAR13 (full text) | 77 participants, 232,849 epochs of 30 s, Actiware 5 | **0.965** | **0.329** | Individual specificity "relatively flat, ranging from 0.0 to 0.7" (Fig. 1). WASO 36.8 vs 49.1 min |
| CHI21 (full text) | 34 young adults, 3 nights, Actiwatch, Actiware 6.0.9 "medium" threshold, 30 s epochs | 0.97 | 0.39 | **SOL bias −7.6 min** (PSG 9.7 ± 8.5 vs Actiwatch 2.1 ± 1.2 min; LoA −24.1 to +8.8; n = 102 nights; Table 3) |
| AASM18 (full text) | Insomnia, meta-analysis of 12 studies vs PSG | — | — | "mean difference of 6.17 minutes" lower, "95% CI: 2.29 to 9.07 minutes lower"; quality "high" |
| HAG19a (abstract) | 40 healthy adults at home, Motionlogger vs Zmachine EEG; CK, rescored CK, Sadeh, UCSD | ~94–98% | ~42–54% | "All of them significantly underestimated Sleep Onset Latency (SOL: ~9 to 20 min)" |
| HVH15 Table 3 (full text) | PSG study, n = 28 | 5 min/5°: 91 ± 13% | 5 min/5°: **45 ± 23%** | Trade-off across window and angle: 3 min/3° gives 92/40%; 15 min/5° gives 73/65% |
| KAN11 (abstract) | **Daytime nap** + no-nap rest, PSG, n = 57 | 92–96% (nap) | 40–67% (nap) | "Medium-40 and low-40 were the only settings that did not misidentify sleep in the no-nap condition". "Discrimination of sleep and wake during periods of waking quiescence is not as robust" |
| CEL13 (abstract) | Daytime nap, n = 30, AW-64 vs GT3X+ | — | — | GT3X+ "underestimated SOL"; AW-64 not significantly different from PSG |

**Synthesis.**
- FACT: Actigraphy systematically **underestimates SOL**, that is, it calls sleep too early. The mean bias is about
  6–8 min at night (AASM18; CHI21), up to 9–20 min with some algorithms (HAG19a).
- FACT: Per-night limits of agreement span about 30 min (CHI21).
- FACT: In naps, specificity is 40–67%, and quiet rest without sleep is misread as sleep (KAN11).
- INFERENCE: Any stillness-gated trigger inherits this bias in the "too early" direction.

---

## 4. Wrist PPG + motion sleep staging and consumer validations

### 4.1 Research algorithms (what the features and windows look like)

**Walch et al. 2019** (WAL19, full text). Apple Watch, 31 subjects analysed (39 recruited), PSG, 30 s epochs.
- FACT, features:
  - Features are "cropped to a local window of 10 minutes around the scored epoch".
  - Motion: activity counts via TEL13, then "convolving ... with a Gaussian (σ = 50 seconds)".
  - HR: "interpolated to have a value for every 1 second, smoothed and filtered ... by convolving with a difference of
    Gaussians filter (σ1 = 120 seconds, σ2 = 600 seconds)". It is normalised "by dividing by the 90th percentile in the
    absolute difference between each heart rate measurement and the mean heart rate **over the sleep period**". The
    feature is the SD of that signal in the window.
  - Clock proxy: a circadian-model input built from the ambulatory days.
- FACT, results:
  - Logistic regression at 90% sleep sensitivity: wake correct 0.549 (motion), **0.326 (HR)**, 0.546 (motion + HR),
    0.556 (all features) (Table 2).
  - Neural net, all features: AUC 0.878.
  - Headline result: "90% of epochs correctly, with 59.6% of true wake epochs ... and 93% of true sleep epochs".
- FACT, limitation: "the problem of greater inaccuracy with larger amounts of wake during the attempted sleep period
  persists".
- INFERENCE: Everything here is non-causal: ±5 min windows, a 10 min DoG and a whole-night normalisation. HR's
  information sits on a **minutes** timescale. A 15 s HR moving average (CLAUDE.md §4) is far shorter than any window
  used here.

**Oura, Altini & Kinnunen 2021** (ALT21, full text). 106 participants, 440 nights.
- FACT: "window lengths of 1 and 5 min were used for HRV analysis". Features are normalised "on a per-night basis using
  a robust method based on the 5–95 percentiles".
- FACT, wake sensitivity (2-stage, Table 2): 72.1% with the accelerometer only, 77.2% with temperature + HRV added,
  80.7% with circadian features as well.
- FACT: "accelerometer-only models can only detect wake". HRV gave the largest 4-stage gain (60% → 76%).
- FACT: Oura merges N1 into "light".

**Beattie et al. 2017, Fitbit** (BEA17, abstract only). 60 adults, Embletta EEG reference, 4 classes.
- FACT: accuracy 69%, κ 0.52 ± 0.14. "The most common misclassifications were light/REM and light/wake".

**Fonseca et al. 2017, Philips** (FON17, abstract only). Wrist PPG HRV + accelerometer, validated on 80 recordings.
- FACT: sleep-wake κ 0.55 ± 0.14; "sensitivity to wake of 58.2 ± 17.3%", against 45.5 ± 19.3% for actigraphy.

**Samsung smartwatch data** (SIL24, abstract only; Samsung affiliation inferred from the search filter).
- FACT: a recurrent neural network with "23 input features extracted from accelerometer and photoplethysmography
  sensors". It predicts "sleep stages for each 30-s epoch", was trained on 1522 nights from "different versions of
  Samsung smartwatch", and reached 4-stage balanced accuracy 71.6% and κ 0.56 on 586 recordings.
- NOT VERIFIED: whether this is the algorithm shipped in Samsung Health, and whether it is causal.

### 4.2 Consumer-device validations (wake vs. light, and SOL)

| Device | Study | Wake detection (PSG wake epochs scored wake) | SOL | Notes |
|---|---|---|---|---|
| **Galaxy Watch 3** | KIM23, n = 32 (16 with OSA) | Pooled specificity **0.524**; per-subject wake sensitivity 0.51 ± 0.22 | **Not recorded**: "sleep latency or WASO, were not collected from GW3" | 4-stage accuracy 0.651. Light-sleep sensitivity 0.695 |
| **Galaxy Watch 5** | LEE23, n = 22 | Wake sensitivity **0.481**, specificity 0.910, F1 0.476 (Table 3) | Wearables as a group: mean SOL bias −2.71 min (Discussion); per device only in Fig. 5 | 4-stage κ 0.418, macro-F1 0.576 (Table 2). Light-sleep sensitivity 0.728, specificity 0.641 |
| Apple Watch 8 | LEE23 | (Table 3, not extracted) | Best wearable SOL bias, **0.81 min** | — |
| Apple Watch S6, Garmin FR245, Polar, Oura Gen2, WHOOP 3.0, Somfit | MIL22, n = 53 | 26, 27, 51, 57, 56, 57% | — | Two-state agreement 86–89%. Somfit N1 sensitivity 1% |
| Fitbit Alta HR, Garmin Fenix 5S / Vivosmart 3, others | CHI21 | Specificity 0.54 (Fitbit), 0.18 / 0.19 (Garmin), 0.45–0.51 others | Fitbit −3.1 min (LoA −19.0 to +12.8); Garmin Vivosmart −1.1 (−12.1 to +9.9); Fenix +0.8 (−26.4 to +28.0) | Consumer SOL biases "low magnitude (each <5 min)" but with wide LoA and proportional bias |
| Fitbit (meta-analysis) | HAG19b | Specificity 0.58–0.69 for sleep-staging models; 0.10–0.52 for older ones | Sleep-staging models "underestimated SOL (P=.03)" | 22 studies, 8 in the meta-analysis |
| Oura Gen3, Fitbit Sense 2, Apple Watch S8 | ROB24 | Sleep sensitivity ≥ 95% (abstract) | — | Apple underestimated wake by 7 min |
| Apple Watch (Apple's own data) | APL25 | Median specificity 75.0% (2022 algorithm) → **78.9%** (2025). Wake correct in the 4-stage matrix 70% → 79% | — | **Accelerometer only**. Naps: "Binary sleep and wake data is written ... if a detected sleep session has a total sleep duration of one to three hours"; full stages only above 3 h |

**Synthesis.**
- FACT: Wearables are very good at detecting that someone is asleep and poor at detecting that someone is awake. The
  pattern is worse "on nights with poorer/disrupted sleep (i.e. longer sleep latency ...)" (CHI21 Discussion).
- FACT: DZ24 lists "the misclassification of wakefulness during the sleep period" and "problems with sleep tracking
  outside of the main sleep bout or nighttime period" as clear limitations (abstract).
- INFERENCE: A nap is outside the main sleep bout.

### 4.3 Samsung's own sleep stages as a reference

- FACT (SDB24): "Sleep data is created on a smartwatch when the user wakes up". The processed record is synchronised to
  Health Connect as a `SleepSessionRecord` with stages, read with `READ_SLEEP`. "Transfer and synchronization tasks can
  be delayed".
- NOT VERIFIED: whether Samsung Health records or stages naps under about 1 h. No Samsung page I found states a minimum.
  Apple's rule (1–3 h binary only) suggests short naps may get no stages at all.
- INFERENCE: Samsung stages are **post hoc only**, with a wake detection of about 50% (KIM23; LEE23). They are a weak
  reference for *whether* the user slept, not for *when* N1 began.

---

## 5. What the physiology offers a real-time detector (summary; details in [PHYS])

| Signal | Behaviour across wake → N1 (FACT) | Usefulness for a causal relative-delta trigger (INFERENCE) |
|---|---|---|
| HR | Slowing "beginning 30s prior to Stage 1 onset" (PIV96, children). Falls at each transition into sleep and rises after each arousal, "with a return to sleep levels by 12 beats" (BUR99). About −0.53 bpm per 30 s over the 5 min before stable sleep (DZ20 Results). Falls "after LO before the initial onset of theta-activity" (CAR05). Shows circadian and time effects (TRI01) | Real but slow (about 1 bpm/min) and **confounded with lie-down relaxation**. A baseline taken in the first 2–3 min after lying down is biased high, so the trigger fires early |
| HRV (RMSSD / HF) | RSA "did not change significantly" (BUR99, n = 6). HF unchanged; VLF fell "2 min before sleep onset"; LF fell (SHI06, n = 34). HF n.u. rose after onset (DZ11) | Mixed. Wrist-PPG RMSSD is artifact-prone (next row). Weak bonus at best |
| PPG artifact | On a seated baseline, RMSSD from **raw Galaxy Watch 5 PPG** (HeartPy, no denoising) was 185.79 ms, against 41.63 ms from the Polar H10 ECG (GPPG25 Table 5) | Shows how badly unfiltered wrist IBIs inflate RMSSD. This was HeartPy on raw PPG, **not** Samsung's on-watch `IBI_LIST`, which is untested |
| Skin temperature | Distal-proximal gradient "was the best predictor variable for sleep-onset latency" (KRA00, abstract). Wrist temperature "started to increase in association to bed time" (SAR08, abstract) | Slow, but a plausible "readiness" prior. `SKIN_TEMPERATURE_CONTINUOUS` exists in SDK 1.4.1 ([S] §2.1); its rate and support on the SM-L500 are NOT VERIFIED |
| Respiration | "Respiratory ... changes were more pronounced between Stages W and 1" (OGI89). "respiration became more stable" (SHI06) | Needs raw PPG or accelerometer respiration. `PPG_CONTINUOUS` exists in the SDK ([S] §2.1). LEEHS26 (abstract) used "continuous multiwavelength PPG acquisition (25 Hz)" on a Galaxy Watch 6; whether it went through this public SDK is NOT VERIFIED |
| EDA | See section 6 | Veto / "too deep" flag |
| Hypnic jerks | No wearable validation found. A case report describes PSG "muscle activity ... during transition from light sleep to wake, as well as an increase in heart rate" (Houter et al. 2026, *J Sleep Res* e70077, DOI 10.1111/jsr.70077, abstract only) | A jerk can wake the user. The "jerk followed by stillness" bonus in CLAUDE.md §4 is anecdotal |

---

## 6. Wrist EDA during sleep and at onset

FACTS:
- **SAN14** (full text): wrist and palm EDA over 56 nights; peaks defined by a derivative threshold.
  - "In subsequent analyses comparing wrist and palm EDA, we used a threshold of 0.01 μS/s". Thresholds from 0.005 to
    0.05 μS/s were tested.
  - Peaks by stage: "55 ± 4% occurred in NREM2 ... 25±4% in SWS, 12±1% in REM and 4±0% in NREM1". The ordering "holds
    regardless of the threshold".
  - Median EDA amplitude was 0.44, 0.26, **0.18** and 0.26 μS in SWS, NREM2, NREM1 and REM.
  - Only epochs that Cole's function scored as sleep were analysed, so wake EDA was not characterised.
  - "Over the fifteen nights, more than 60% of the 30-s epochs did not show any peaks, regardless of peak threshold".
- **HER19** (abstract only): 43 healthy subjects + 48 patients, "ambulatory device worn at the wrist".
  - Skin-conductance-level feature: "significant EDASEF differences between the sleep stages wake versus N1".
  - An EDA-only sleep/wake algorithm: "sensitivity and specificity for sleep detection of 97% and 75%", accuracy 86%.
  - The direction of the wake → N1 change is not in the abstract.
- **PAR26** (abstract only, preprint): E4 on 69 home PSG recordings. "Tonic EDA showed an inverted stage pattern
  attributable to wrist sweat accumulation during deep sleep ... Phasic EDA showed plausible patterns and may be used
  with caution".
- **DREAMT**: E4 EDA at 4 Hz with PSG stages including N1 (dataset page). It could test an EDA rule offline.
- The site and units issues (dorsal wrist on the watch, Dormio's raw ADC units) are already covered in [D] §10.5.

INFERENCES:
1. N1 is the **quietest** EDA stage (lowest median level, fewest peaks; SAN14). So "no SCR in the last 30 s" is true
   almost all the time at rest. It cannot separate quiet wake from N1.
2. The useful EDA signals are:
   - a phasic SCR, which suggests arousal: reset the hold or abort the countdown;
   - a burst of peaks, which suggests N2 or SWS: log as "too deep".
3. HER19's 75% specificity is intriguing, because it exceeds actigraphy. But it is offline and whole-night, from a
   different device. Test it on DREAMT before trusting it.

---

## 7. Real-time, closed-loop and drowsiness detectors

| System | Sensors | Real time? | Ground truth | Reported performance (FACT) | Source |
|---|---|---|---|---|---|
| Driver HRV anomaly detection (MSPC on 8 HRV features) | ECG RRI | Yes (online monitoring) | EEG sleep scoring by a specialist, 34 drivers | "drowsiness was detected in 12 out of 13 pre-N1 episodes prior to the sleep onsets, and the false positive rate was 1.7 times per hour" | FUJ19 (abstract) |
| Driver HRV, minute-level | ECG HRV, 7 features | Per minute | Minutes annotated drowsy / awake | PPV 0.96, **sensitivity 0.59**, specificity 0.98 (3475 min) | VIC16 (abstract) |
| Wrist-worn drowsiness (E4-class wristband vs ECG) | Wrist PPG HRV, **5 min sliding windows** | Near real time | Video ratings + microsleep events | User-dependent > 92%. User-independent mostly lower, e.g. SVM 65.64%; best UIT 78.94% with ECG | KUN20 |
| Elemind-type headband | **EEG**, alpha phase-locked audio | Yes, closed loop | Blinded EEG scoring | SOL reduced by 10.5 ± 15.9 min in SOL > 30 min insomnia (n = 21) | BRE24 (abstract) |
| Edison bottle | Behaviour (55 g bottle, sound of the fall) | Yes, mechanically | PSG | 41% of 63 drops after N1 (30 s scoring), 78% after microsleeps; "approximately 1 min of accumulated MSEs"; "the bottle always woke the subjects up (100%)"; hypnagogic report at the drop 61.70% vs 22.50% without a drop | LAC21 |
| THIM ring (stimulus-response) | Vibration about every 30 s, finger tap | Yes | PSG | SOL difference 0.08 ± 0.49 / 0.57 ± 1.10 min (as reported in [PHYS] §1 #8; SCO21 **not read by me**) | [PHYS] |
| MIT Dormio | Finger PPG, wrist EDA, finger flex | Yes | Self-report (+ PSG pilot) | OR of three absolute deltas from a 120 s mean, no hold | [P] §2.2 |
| MIT Dormio Light | Timer only | — | Self-report | Incubation still worked; "likely less sensitive" | [P] §3 |
| `idealemu/TDI` replication | One FSR/flex sensor in the hand (Itsybitsy at 100 Hz, one-euro filter) | Yes | Self-report (README claims "successful replication") | Code: `CALIBRATION_PERIOD = 90` s mean. Trigger when the 1 s mean rises above baseline × (1 + 0.035) and then holds a new stable state for `NEW_STABLE_STATE_TIME_WINDOW = 10` s (`TDI.py:33-37, 256-279`). Not peer reviewed; no license | IDE |
| Apple / Samsung / Oura / Fitbit staging | Motion (+PPG) | **No**, post hoc per session | PSG | Section 4 | — |

INFERENCES:
- Of all these, FUJ19 is the closest analogue to OpenTDI. It is anomaly detection against the person's own baseline,
  validated against EEG-defined pre-N1, and its false-alarm metric (per hour) suits a nap app.
- Its 1.7 FP/h would mean roughly one false trigger per 35 min of wake. In a 45 min nap, that matters.
- OpenTDI should report false triggers per hour of verified wakefulness the same way.

**Searches that found nothing** (NOT FOUND, 2026-10-27):
- A peer-reviewed real-time sleep-onset detector on a consumer smartwatch validated against PSG. Searched Europe PMC:
  `TITLE:"sleep onset" AND (detection OR prediction) AND (heart rate OR wearable OR wrist OR photoplethysmography OR
  actigraphy OR real-time)`, plus web searches.
- An Apple Watch or Wear OS Dormio or hypnagogia app.
- GitHub searches for `dormio`, `hypnagogia`, `targeted dream incubation`, `sleep onset detection` and `hypnagogic`
  turned up only:
  - the MIT repos;
  - `idealemu/TDI`;
  - this project (`hasan-ismail/openTDI`);
  - unrelated or empty repos. `g3149482122-web/CycleWake-AI` claims "real-time sleep onset detection (via HR/PPG)" but
    contains only a README.

---

## 8. Windows, features, thresholds and hold times across sources

| Source | Baseline / normalisation | Window | Threshold | Persistence / hold |
|---|---|---|---|---|
| Dormio paper [P] | Mean of first 120 s lying down | not stated | HR > 5 BPM; EDA > 4 (or 5); flex > 8 (raw units) | None ("whenever one of these thresholds was passed") |
| openSleep web [O] | Calibration mean | Last 10 s (100 samples or 10 BPM values) | \|ΔHR\| ≥ 6, \|ΔEDA\| ≥ 4, \|Δflex\| ≥ 5 (UI defaults) | None |
| `idealemu/TDI` [IDE] | Mean of first 90 s | 1 s mean | +3.5% relative (2% "sensor repeatability" + 1.5% "state change range") | 10 s stable new level |
| CK92 / Webster [PYA] | none (fixed weights) | −4…+2 min | D < 1 | Rescoring: sleep after ≥ 4 min wake needs > 1 min to count |
| SAD94 [PYA] | none | ±5 min | PS ≥ 0 | — |
| van Hees HVH15 | none | 5 s angle epochs | no Δangle > 5° | **≥ 5 min** |
| Walch WAL19 | Whole-night 90th-percentile HR normalisation | 10 min window; HR DoG σ 120 / 600 s; counts σ 50 s | learned (θW = 0.3 on wake probability for Fig. 5) | — |
| Oura ALT21 | Per-night 5–95 percentile | HRV 1 and 5 min | learned | — |
| Driver HRV KUN20 | per-user (UDT) vs. population (UIT) | 5 min HRV windows | learned | — |
| SAN14 (EDA) | none | 30 s epochs | SCR = derivative > 0.01 μS/s | — |
| Standard definitions (CHI21) | — | 30 s epochs | first sleep epoch (SOL) | LPS = 10 consecutive min |
| **CLAUDE.md §4** | 180 s mean and SD | 60 s accel SD; 15 s HR MA; 30 s EDA slope | HR < 0.93 × μ, or RMSSD +15%; EDA slope ≤ 0 with \|ΔEDA\| < 1.5σ | 20 s (or 10 s with all 3) |

INFERENCES:
- The CLAUDE.md windows (15 s HR, 20 s hold) are 1–2 orders of magnitude shorter than any validated sleep algorithm's
  windows (minutes).
- Its 7% HR drop is about 4.5–5 bpm at a resting HR of 65–70 bpm. That is the same size as Dormio's > 5 BPM (a two-sided absolute delta, `script_wearable.js:701`, not a drop) and DZ20's
  ~5 bpm over the 5 min before stable sleep, so the size of the 7% is plausible.
- Its **timescale** is not plausible.

---

## 9. What accuracy OpenTDI can realistically expect

Everything in this section is **INFERENCE**, built on the facts above.

### 9.1 The ceiling

- No consumer system reliably labels N1 even offline (MIL22: 1%; ROS13: humans 63%).
- The best wrist systems detect roughly 50–80% of wake epochs with the whole night as context (section 4.2).
- A causal, single-session relative-delta detector using HR + stillness (+ EDA veto) should be expected to do **no
  better** than those offline numbers, and probably worse at the epoch level.

### 9.2 Realistic targets for a nap session (to be measured, not assumed)

- **Sensitivity**, meaning some trigger fires before the user is solidly in N2 when PSG would show sleep: likely high.
  HR falls and stillness both accompany real sleep (section 5), and the dominant error of motion-based systems is to
  call sleep *early*, not late.
- **Timing error.** Wrist devices' **night** SOL has limits of agreement of about ±10–28 min even offline (CHI21 Table 3).
  For a nap, expect several minutes of timing error, mostly **early** triggers. N1 lasts about 1–2 min in naps (LAC21),
  so a 1–5 min post-detection delay ([P]) will often land in quiet wake or in N2.
- **Precision / false triggers.** Premature triggers during still wakefulness are the expected dominant failure:
  - KAN11 no-nap misclassification;
  - MAR13 specificity 0.33;
  - APL25's explicit "quiet wake" problem;
  - CAR05's HR fall before theta.

  Quantify it as false triggers per hour of verified wake (FUJ19-style). Plan for a meaningful share of sessions where
  the first prompt arrives before sleep.
- **The relevant bar is the timer, not PSG.** MIT's sensor-free Dormio Light still incubated dreams ([P] §3). The
  detector earns its place only if it beats `TIMED_CUES` on the owner's own outcome: prompts answered "Halfway/Asleep"
  with hypnagogic content.

### 9.3 Main false-positive sources (ranked by evidence)

1. **Lying still while awake (quiet wakefulness).** Strong evidence: MAR13; KAN11; CHI21; APL25.
2. **The pre-sleep HR decline that is not sleep**: relaxation, recumbency and time in bed (CAR05; [PHYS] §1 #4). A
   baseline in the first 2–3 min after lying down amplifies it. Moderate evidence.
3. **Abs-value deltas.** Arousals raise HR (BUR99), so `|ΔHR|` triggers on arousals. This is a code flaw already present
   in openSleep ([O] §5.2).
4. **PPG artifacts** on IBI-derived HRV (GPPG25), and Samsung status codes −2 / −8 / −10 for motion ([S] §2.3).
5. **Screen-off batching.** Data may arrive minutes late unless `flush()` is used ([S] §0 #8). Samples must be aligned
   by `DataPoint` timestamp, or the detector judges stale windows.
6. **EDA sweat drift** (PAR26) and contact changes ([D] §10.5).
7. **Behavioural "trying to sleep"**: deliberately lying motionless during baseline and incubation. INFERENCE, no source.

### 9.4 Main false-negative sources

- Fidgety sleepers. Stillness gating fails when people move while falling asleep. Individual specificity is "relatively
  flat, ranging from 0.0 to 0.7" (MAR13), so individuals differ widely.
- Small or absent HR drops in some people; onset styles differ ([PHYS] §1 #10).
- Going straight to N2 (3 of 14 in LAC21).
- Arousals during onset, which retard the HR fall (CAR05).

---

## 10. Recommended self-evaluation protocol (owner as subject, n-of-1)

INFERENCE throughout, except where cited. Equipment: the watch, the phone, a light plastic bottle, optionally a
Bluetooth clicker. No PSG.

**Step 0. Data hygiene** (strong; [S]).
- Log raw HR, IBI, accelerometer, EDA, status codes, `DataPoint` timestamps and arrival timestamps.
- Log every detector state transition, every prompt, and the audio report.
- Make each session replayable offline (owner requirement D.13).

**Step 1. Offline replay on public PSG data** before any self-testing.
- Run the causal detector over PhysioNet `sleep-accel` (SLA19). It is open, ODC-By, and labels N1 = 1, N2 = 2, wake = 0.
- Measure the trigger time against the first N1 epoch, the first N2 epoch and LPS.
- Caveats:
  - those are night sleeps, not naps;
  - Apple Watch HR was "sampled every several seconds" (WAL19);
  - Apple acceleration includes gravity (units of g).
- Optionally request DREAMT (DUA) to test EDA rules, and MESA via NSRR as WAL19 did.
- Report results with MEN21's framework: Bland–Altman of onset latency, plus epoch-by-epoch agreement.

**Step 2. Quiet-wake control sessions** (≥ 5 × 20 min). These measure false positives directly; KAN11's no-nap
condition is the precedent.
- Same posture, eye mask and time of day as the naps, but the owner **stays awake**.
- Use the responsiveness task below to verify wakefulness.
- Every trigger is a false positive. Report false triggers per hour (FUJ19 metric).

**Step 3. Labelled nap sessions, detector logging only** (≥ 10–20 sessions; prompts on a timer or absent). Collect up to
three independent markers per session:
1. **Responsiveness probe** (OGI89; THIM-style per [PHYS] §1 #8).
   - A faint tone or a gentle phone vibration at random 20–40 s intervals. Respond with the **non-watch** hand (a clicker
     or the phone), so the watch's accelerometer stays clean.
   - Behavioural onset = the first of 2 consecutive missed probes.
   - Caveats: responding can continue into stage 1–2 (OGI89), and probing may delay sleep. Run probe sessions
     separately from real TDI sessions.
2. **Held-object drop** (LAC21).
   - Hold a light, slippery, wide plastic bottle (LAC21 used 14.5 cm, 5.5 cm diameter, 55 g) in the **non-watch** hand,
     over the bed edge.
   - The phone microphone timestamps the thud. Report mentation immediately.
   - Interpretation: a drop marks about 1 min of accumulated microsleep. It usually falls before N2 and wakes the user
     (LAC21).
3. **Self-report at the end or at each prompt.** Use MIT's Awake / Halfway / Asleep question ([P]). Add "Was there
   imagery? Was it involuntary or bizarre?" (LAC21's hypnagogia definition). Remember BM82: N1 is often reported as
   wake.

Metrics per session:
- Δt = t_trigger − t_reference;
- the share of sessions with a trigger before the reference (premature);
- misses (no trigger before the session ends, or more than 5 min after the reference).

**Step 4. Samsung reference (weak).**
- After each session, read Samsung's `SleepSessionRecord` from Health Connect (SDB24). Record whether a sleep session
  exists, its start time and its stages.
- Expect no record for short naps (NOT VERIFIED) and about 50% wake detection (KIM23; LEE23).
- Use it at **night** as well: compare OpenTDI's first trigger at bedtime with Samsung's sleep start. That gives longer,
  more numerous onsets for cheap testing.

**Step 5. A/B against the timer** (the decision test).
- Alternate detector-driven and `TIMED_CUES` sessions, matched for time of day and prior sleep. Aim for at least 10 each.
- Outcome: the fraction of prompts answered Halfway/Asleep with hypnagogic content, and the theme-incorporation rate.
- With n = 1 and about 10 per arm, report descriptive results and confidence intervals. Do not claim significance.

**Step 6. Tune only on held-out sessions.**
- Freeze the parameters, collect new sessions, then evaluate. This avoids fitting thresholds to the same few naps.

---

## 11. Comparison: OpenTDI (CLAUDE.md §4) against MIT and the other approaches

| Aspect | MIT Dormio | MIT Dormio Light | `idealemu/TDI` | Consumer staging (Apple, Samsung, Oura) | Driver HRV MSPC | CLAUDE.md §4 | Evidence-based suggestion (INFERENCE) |
|---|---|---|---|---|---|---|---|
| Primary signal | Finger flex (behavioural task) + HR + EDA | none | Hand-grip FSR/flex | Motion + PPG (Apple: motion only) | ECG HRV | Wrist stillness + HR/RMSSD + EDA | Wrist stillness + HR trend; EDA as veto; optional behavioural task |
| Baseline | 120 s mean | — | 90 s mean | Whole night | Personal normal data | 180 s mean / SD | Start after 60–120 s of settling; robust median/MAD; allow re-baselining |
| Logic | OR, absolute deltas | Timer | Relative +3.5% + 10 s stable | ML over epochs | Anomaly score | Stillness AND (HR or EDA) | Stillness AND HR trend; arousal resets |
| Window | ~10 s ([O]) | — | 1 s | 1–10 min | NOT VERIFIED | 15–60 s | 60–120 s causal windows |
| Hold | none | — | 10 s | n/a | n/a | 20 s / 10 s | 60–120 s, backdated, subtracted from the countdown |
| Validation | Self-report, n = 5 pilots | Self-report | Self-report | PSG | EEG | none | Section 10 |
| Real time | yes | yes | yes | **no** | yes | yes | yes |

---

## 12. Gaps and items not verified

- Original PDFs of CK92, SAD94, Oakley 1997, BEA17, FON17 and FUJ19 were not read. Formulas come from pyActigraphy's
  implementation and docstrings; results come from abstracts.
- FUJ19's window lengths, features and false-positive definition: NOT VERIFIED.
- HER19's EDA direction (wake → N1) and device site: NOT VERIFIED.
- Whether Samsung Health stages naps, and what minimum duration it needs: NOT VERIFIED.
- Whether SIL24 is Samsung's production algorithm: NOT VERIFIED.
- `SKIN_TEMPERATURE_CONTINUOUS` and `PPG_CONTINUOUS` rates and availability on the SM-L500: NOT VERIFIED (see [S] §9).
- The gravity-free accelerometer statement ([S] §0 #6): to be checked on hardware. Orientation-based methods depend on it.
- The THIM / SCO21 numbers are quoted from [PHYS]; I did not read SCO21.
- Galaxy Watch 5 per-device SOL bias (LEE23 Fig. 5): only in a figure, not extracted.
- No validation exists of Galaxy Watch8 EDA, or of Samsung's on-watch `IBI_LIST` RMSSD, against reference devices during
  rest or sleep (searched Europe PMC, 2026-10-27).

---

## Implications for OpenTDI

Each item is tied to its evidence. Strength: **strong** = several primary sources or direct vendor documentation;
**moderate** = one good primary source or consistent indirect evidence; **weak** = indirect, abstract-only or
extrapolated; **anecdotal** = single case or my judgement.

1. **Make `TIMED_CUES` (Dormio Light) the robust default, and present the detector as "adaptive timing", not "N1
   detection".**
   - Evidence: N1 is unreliable even for human scorers (ROS13) and consumer EEG (MIL22: 1%). Wrist SOL errors span tens
     of minutes per night (CHI21). The timer protocol worked ([P] §3).
   - UI wording: "likely falling asleep".
   - Strength: **strong** that N1 cannot be claimed; **moderate** that the timer is a competitive baseline.
2. **Stillness is necessary, never sufficient.** Keep Gate 1 (stillness), but require an autonomic signal as well,
   because stillness alone behaves like actigraphy: specificity 0.33 (MAR13), 0.39 (CHI21), and misread no-nap rest
   (KAN11). Calibrate the stillness threshold to the baseline's own dynamic-acceleration noise floor, because the SDK
   stream may exclude gravity ([S]). Strength: **strong**.
3. **Lengthen the windows to physiological timescales.**
   - HR: replace the 15 s moving average with a 60 s median of valid HR (status = 1). Add a 3–5 min HR slope.
   - Default trigger component: smoothed HR ≥ 4–5 bpm (≈ 6–8%) below the baseline median **and** a negative slope.
   - Rationale: HR changes on the scale of 1 bpm/min (DZ20, −0.53 bpm/30 s); WAL19's HR filter uses σ 120 / 600 s; the
     Dormio threshold is 5 BPM ([P]).
   - Strength: **moderate** for the timescale; **weak** for the exact values.
4. **Replace the 20 s / 10 s holds with a 60–120 s persistence, backdated.**
   - Backdate onset to the start of the qualifying run, and subtract the elapsed hold from the incubation countdown so
     that onset-to-prompt stays within MIT's 1–5 min ([P]).
   - Rationale:
     - every validated algorithm integrates over minutes (section 3.1);
     - Webster's rules distrust sleep under 1 min after a wake bout ([PYA]);
     - N1 in naps lasts about 1–2 min (LAC21), so longer holds risk N2.
   - Strength: **weak–moderate**. Tune with section 10.
5. **Delay or robustify the baseline.**
   - Start the 120–180 s baseline only after 60–120 s of settled stillness.
   - Use the median and MAD, not the mean and SD.
   - Optionally track a slowly updating "awake reference" while the user is clearly awake (probe responses, movement).
   - Rationale: HR falls after lights-out *before* theta (CAR05). A lie-down baseline is biased high and fires early.
     Robust normalisation is what ALT21 and WAL19 use.
   - Strength: **moderate**.
6. **Make arousals reset, never trigger.**
   - Use signed deltas: an HR *drop* counts toward onset; an HR *rise* is an arousal. Never `abs()` as in openSleep ([O]).
   - An HR jump, a movement burst or an EDA SCR (> 0.01 μS/s, SAN14) resets the hold. During a countdown, it aborts or
     extends it.
   - Rationale: HR rises after each spontaneous arousal and returns within about 12 beats (BUR99).
   - Strength: **moderate**.
7. **Demote RMSSD from "Signal A" to a logged, low-weight feature.**
   - Use it only after strict IBI cleaning (status 0, plausible range, successive-difference filter) over ≥ 60 s.
   - Evidence: no onset-period vagal rise (BUR99; SHI06; [PHYS] §1 #5). Raw wrist-PPG RMSSD was inflated 1.7–4.5× in
     GPPG25 (Table 6 with Wiener denoising, Table 5 without; HeartPy, seated baseline).
   - Strength: **moderate**.
8. **EDA is never a trigger; a veto or "too deep" flag is an option that needs the owner's sign-off (owner requirement 11 makes EDA bonus-only).**
   - A flat or negative tonic slope is uninformative: N1 has the lowest median EDA (SAN14). Its 4% share of peaks mostly reflects little time in N1, and SAN14 did not compare quiet wake.
   - Phasic SCRs mean arousal. Peak bursts suggest NREM2/SWS. Watch for tonic sweat drift (PAR26).
   - Test HER19's offline 75% specificity claim on DREAMT before giving EDA more weight.
   - Strength: **moderate** (consistent with [D] §10.5).
9. **Log skin temperature** (if `SKIN_TEMPERATURE_CONTINUOUS` works on the SM-L500) as a slow sleep-readiness prior and
   a future feature. Not a trigger. Evidence: KRA00; SAR08. Strength: **weak**.
10. **Offer an opt-in behavioural task** for high-specificity triggering: the held-object or arm-raised drop, detected by
    the phone microphone or by the watch.
    - Rationale: it marks accumulated microsleep (78% of drops), wakes the user, and yields more hypnagogic reports
      (61.7% vs 22.5%) (LAC21). It is the closest analogue of Dormio's flex task ([D] §10.6).
    - Strength: **moderate** for the bottle; **weak** for a watch-only implementation (untested).
11. **Add an optional personal-learning mode.**
    - After at least 10 labelled sessions (self-report + probe/drop markers), fit the owner's own thresholds and keep the
      session baseline as the default. This keeps "zero multi-night calibration" as the default.
    - Evidence: user-dependent > 92% vs clearly lower user-independent results (KUN20). FUJ19's personal anomaly model.
    - Strength: **moderate**.
12. **Respect the real-time data path.**
    - Call `flush()` on a fixed cadence with the screen off. Compute every window by `DataPoint` timestamp. Treat late or
      missing data as "unknown", never as "still".
    - Evidence: [S] §0 #8–9. Strength: **strong** (vendor documentation).
13. **Report the right metrics:**
    - onset-latency error (Bland–Altman, MEN21);
    - false triggers per hour of verified wake (FUJ19);
    - the share of premature triggers;
    - the yield of Halfway/Asleep reports with hypnagogic content.

    Compare against `TIMED_CUES` (section 10, step 5). Strength: **moderate**.
14. **Run the section 10 protocol before tuning the defaults.**
    - In order: offline replay on `sleep-accel` (open, ODC-By, N1 labelled); quiet-wake controls; probe and bottle
      labelled naps; Samsung stages as a weak check; A/B against the timer.
    - Strength: **strong** that each step is feasible (datasets and methods verified). **Weak** on how many sessions are
      enough, which is my estimate.
15. **Licensing for the GPL-3.0 project.**
    - pyActigraphy is **GPL-3.0** and GGIR is **Apache-2.0** (GitHub API, 2026-10-27). Both can be used or ported into a
      GPL-3.0 codebase, with attribution, for offline analysis tools.
    - `ojwalch/sleep_classifiers` and `idealemu/TDI` have **no license** (GitHub API `license: null`). Read them for
      ideas only; do not copy code.
    - The PhysioNet `sleep-accel` data is ODC-By 1.0. DREAMT is restricted (DUA): never commit it to the repo.
    - Strength: **strong** (license metadata checked). This is not legal advice.
16. **Do not count hypnic jerks as positive evidence** until the owner's own logs show that they precede sleep rather
    than wake him. Log them as events. Evidence: no wearable validation found; a case report links jerks with
    transitions to wake and an HR rise (Houter 2026, abstract). Strength: **anecdotal**.
