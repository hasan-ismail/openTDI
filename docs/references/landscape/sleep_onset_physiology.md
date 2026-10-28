# Sleep onset, N1 and hypnagogia: the physiology a wrist device can see

Research note for OpenTDI, written 2026-10-27. It covers the science of the wake-to-sleep transition that matters
for detecting N1 from a Galaxy Watch8 Classic: wrist PPG heart rate and inter-beat intervals (IBI), wrist EDA and a
25 Hz accelerometer. The EDA electrodes are presumably on the dorsal wrist under the case; that is not verified for
SM-L500.

**What this file does not repeat.**
- Dormio's own thresholds, baseline, delays and Figure 4 deltas: see `../horowitz2020_dormio_paper.md` §2 and §7.
- The openSleep code: see `../opensleep_dormio_code_analysis.md`.
- Wrist-versus-palm EDA (van der Mee 2021, Hossain 2022), the arm-drop and jerk pseudo-code: see
  `../dormio_duo_wearable_analysis.md` §10.5 to §10.7.

This file adds the primary physiology literature, with numbers, and compares it with the CLAUDE.md §4 defaults.

**Labels.**
- **FACT**: stated in a source I read, with the place in the source.
- **INFERENCE**: my reasoning or arithmetic.
- **NOT VERIFIED**: I could not read the source, or I am not sure it says this.
- "Abstract only" means that I read only the abstract.

---

## 0. Sources and how each was read

| Key | Citation | Access | What I read |
|---|---|---|---|
| **LAC21** | Lacaux C, Andrillon T, Bastoul C, Idir Y, Fonteix-Galet A, Arnulf I, Oudiette D (2021). "Sleep onset is a creative sweet spot." *Science Advances* 7(50): eabj5866. DOI 10.1126/sciadv.abj5866, PMC8654287 | Europe PMC `fullTextXML` | Full text, CC BY. Supplement (Table S1) not read |
| **LAC22** | Lacaux C, Andrillon T, Arnulf I, Oudiette D (2022). "Memory loss at sleep onset." *Cereb Cortex Commun* 3(4): tgac042. DOI 10.1093/texcom/tgac042, PMC9677600 | Europe PMC | Abstract and introduction |
| **LAC24** | Lacaux C, Strauss M, Bekinschtein TA, Oudiette D (2024). "Embracing sleep-onset complexity." *Trends Neurosci* 47(4): 273–288. DOI 10.1016/j.tins.2024.02.002 | The journal version is paywalled. I read the **PsyArXiv preprint** (osf.io/yzsmj, posted 2023-11-13, downloaded as PDF) | Full preprint text. The published version may differ |
| **OGI01** | Ogilvie RD (2001). "The process of falling asleep." *Sleep Med Rev* 5(3): 247–270. DOI 10.1053/smrv.2001.0145 | Paywalled | Abstract only, plus how PRE14 summarises it |
| **TAN96** | Tanaka H, Hayashi M, Hori T (1996). "Statistical features of hypnagogic EEG measured by a new scoring system." *Sleep* 19(9): 731–738. DOI 10.1093/sleep/19.9.731 | Europe PMC | Abstract only |
| **TAN97** | Tanaka H, Hayashi M, Hori T (1997). "Topographical characteristics and principal component structure of the hypnagogic EEG." *Sleep* 20(7): 523–534. DOI 10.1093/sleep/20.7.523 | Europe PMC | Abstract only |
| HORI94 | Hori T, Hayashi M, Morikawa T (1994). "Topographical EEG changes and the hypnagogic experience." In Ogilvie & Harsh (eds), *Sleep Onset: Normal and Abnormal Processes*, APA, pp. 237–253 | Book chapter | **NOT READ.** Described here only through LAC24, NIE17, NOR15 and TAN96/97 |
| **STI00** | Stickgold R, Malia A, Maguire D, Roddenberry D, O'Connor M (2000). "Replaying the game: hypnagogic images in normals and amnesics." *Science* 290: 350–353. DOI 10.1126/science.290.5490.350 | Paywalled | Abstract only |
| **NOR15** | Noreika V, Canales-Johnson A, Koh J, Taylor M, Massey I, Bekinschtein TA (2015). "Intrusions of a drowsy mind: neural markers of phenomenological unpredictability." *Front Psychol* 6: 202. DOI 10.3389/fpsyg.2015.00202, PMC4357244 | Europe PMC | Full text. It is a single-participant study |
| **NIE17** | Nielsen T (2017). "Microdream neurophenomenology." *Neurosci Conscious* 2017(1): nix001. DOI 10.1093/nc/nix001, PMC6007184 | Europe PMC | Full text |
| **PRE14** | Prerau MJ, Hartnack KE, Obregon-Henao G, Sampson A, Merlino M, Gannon K, Bianchi MT, Ellenbogen JM, Purdon PL (2014). "Tracking the sleep onset process: an empirical model of behavioral and physiological dynamics." *PLoS Comput Biol* 10(10): e1003866. DOI 10.1371/journal.pcbi.1003866, PMC4183428 | Europe PMC | Full text |
| **BUR99** | Burgess HJ, Kleiman J, Trinder J (1999). "Cardiac activity during sleep onset." *Psychophysiology* 36: 298–306. DOI 10.1017/s0048577299980198 | Paywalled | Abstract only. **No bpm values were available to me** |
| **BUR97** | Burgess HJ, Trinder J, Kim Y, Luke D (1997). "Sleep and circadian influences on cardiac autonomic nervous system activity." *Am J Physiol* 273: H1761–H1768. DOI 10.1152/ajpheart.1997.273.4.h1761 | Paywalled | Abstract only |
| **PIV96** | Pivik RT, Busby K (1996). "Heart rate associated with sleep onset in preadolescents." *J Sleep Res* 5: 33–36. DOI 10.1046/j.1365-2869.1996.d01-50.x | Paywalled | Abstract only |
| **CAR05** | Carrington MJ, Barbieri R, Colrain IM, Crowley KE, Kim Y, Trinder J (2005). "Changes in cardiovascular function during the sleep onset period in young adults." *J Appl Physiol* 98: 468–476. DOI 10.1152/japplphysiol.00702.2004 | Publisher returned HTTP 403 | Abstract only |
| **TRI01** | Trinder J, Kleiman J, Carrington M, et al. (2001). "Autonomic activity during human sleep as a function of time and sleep stage." *J Sleep Res* 10: 253–264. DOI 10.1046/j.1365-2869.2001.00263.x | Paywalled | Abstract only |
| **SHI06** | Shinar Z, Akselrod S, Dagan Y, Baharav A (2006). "Autonomic changes during wake-sleep transition: a heart rate variability based approach." *Auton Neurosci* 130: 17–27. DOI 10.1016/j.autneu.2006.04.006 | Paywalled | Abstract only |
| **BON97** | Bonnet MH, Arand DL (1997). "Heart rate variability: sleep stage, time of night, and arousal influences." *Electroencephalogr Clin Neurophysiol* 102: 390–396. DOI 10.1016/s0921-884x(96)96070-1 | Paywalled | Abstract only |
| **DEZ18** | de Zambotti M, Trinder J, Silvani A, Colrain IM, Baker FC (2018). "Dynamic coupling between the central and autonomic nervous systems during sleep: a review." *Neurosci Biobehav Rev* 90: 84–103. DOI 10.1016/j.neubiorev.2018.03.027, PMC5993613 | PMC author manuscript (HTML) | Full text, §3 |
| **HUA18** | Huang J, Ulke C, Sander C, Jawinski P, Spada J, Hegerl U, Hensch T (2018). "Impact of brain arousal and time-on-task on autonomic nervous system activity in the wake-sleep transition." *BMC Neurosci* 19: 18. DOI 10.1186/s12868-018-0419-y, PMC5896037 | Europe PMC | Full text, including Table 2 |
| **ULK17** | Ulke C, Huang J, Schwabedal JTC, Surova G, Mergl R, Hensch T (2017). "Coupling and dynamics of cortical and autonomic signals are linked to central inhibition during the wake-sleep transition." *Sci Rep* 7: 11804. DOI 10.1038/s41598-017-09513-6, PMC5603599 | Europe PMC | Abstract and results on coupling |
| **NAN19** | Nano M, Fonseca P, Overeem S, Vullings R, Aarts RM (2019). "Lying awake at night: cardiac autonomic activity in relation to sleep onset and maintenance." *Front Neurosci* 13: 1405. DOI 10.3389/fnins.2019.01405, PMC6974549 | Europe PMC | Abstract and methods |
| **CEL18** | Cellini N, Torre J, Stegagno L, Sarlo M (2018). "Cardiac autonomic activity during daytime nap in young adults." *J Sleep Res* 27: 159–164. DOI 10.1111/jsr.12539 | Paywalled | Abstract only |
| **HER17** | Herzig D, Eser P, Omlin X, et al. (2017). "Reproducibility of heart rate variability is parameter and sleep stage dependent." *Front Physiol* 8: 1100. DOI 10.3389/fphys.2017.01100, PMC5767731 | Europe PMC | Full text, results |
| **SCH13** | Schäfer A, Vagedes J (2013). "How accurate is pulse rate variability as an estimate of heart rate variability?" *Int J Cardiol* 166: 15–29. DOI 10.1016/j.ijcard.2012.03.119 | Paywalled | Abstract only |
| **SAN14** | Sano A, Picard RW, Stickgold R (2014). "Quantitative analysis of wrist electrodermal activity during sleep." *Int J Psychophysiol* 94(3): 382–389. DOI 10.1016/j.ijpsycho.2014.09.011, PMC4335672 | PMC author manuscript (HTML) | Full text |
| **HRL19** | Herlan A, Ottenbacher J, Schneider J, Riemann D, Feige B (2019). "Electrodermal activity patterns in sleep stages and their utility for sleep versus wake classification." *J Sleep Res* 28: e12694. DOI 10.1111/jsr.12694 | Paywalled | Abstract only |
| HOR82 | Hori T (1982). "Electrodermal and electro-oculographic activity in a hypnagogic state." *Psychophysiology* 19: 668–672. DOI 10.1111/j.1469-8986.1982.tb02521.x | No abstract indexed | **NOT READ** |
| SAN11 | Sano A, Picard RW (2011), EMBC paper on a taxonomy of sleep EDA patterns | — | **NOT READ.** I used SAN14 for "storms" instead |
| **VDO12** | van Dooren M, de Vries JJ, Janssen JH (2012). "Emotional sweating across the body: comparing 16 different skin conductance measurement locations." *Physiol Behav* 106: 298–304. DOI 10.1016/j.physbeh.2012.01.020 | Paywalled | Abstract only |
| **KRA00** | Kräuchi K, Cajochen C, Werth E, Wirz-Justice A (2000). "Functional link between distal vasodilation and sleep-onset latency?" *Am J Physiol Regul Integr Comp Physiol* 278: R741–R748. DOI 10.1152/ajpregu.2000.278.3.r741 | Paywalled | Abstract only |
| KRA99 | Kräuchi K, Cajochen C, Werth E, Wirz-Justice A (1999). "Warm feet promote the rapid onset of sleep." *Nature* 401: 36–37. DOI 10.1038/43366 | No abstract indexed | **NOT READ** (cited only as the origin of KRA00's line of work) |
| **KRA07** | Kräuchi K (2007). "The thermophysiological cascade leading to sleep initiation in relation to phase of entrainment." *Sleep Med Rev* 11: 439–451. DOI 10.1016/j.smrv.2007.07.001 | Paywalled | Abstract only |
| **SAR08** | Sarabia JA, Rol MA, Mendiola P, Madrid JA (2008). "Circadian rhythm of wrist temperature in normal-living subjects." *Physiol Behav* 95: 570–580. DOI 10.1016/j.physbeh.2008.08.005 | Paywalled | Abstract only |
| **FRO06** | Fronczek R, Overeem S, Lammers GJ, van Dijk JG, Van Someren EJ (2006). "Altered skin-temperature regulation in narcolepsy relates to sleep propensity." *Sleep* 29: 1444–1449. DOI 10.1093/sleep/29.11.1444 | — | Abstract only |
| **WEI21** | Wei J, Boger J (2021). "Sleep detection for younger adults, healthy older adults, and older adults living with dementia using wrist temperature and actigraphy." *JMIR Mhealth Uhealth* 9: e26462. DOI 10.2196/26462, PMC8207254 | — | Abstract only |
| **ROD26** | Rodrigues Neto C, Carvalho F, Grassi-Kassisse D, et al. (2026). "Wrist temperature as a sleep marker in actigraphy." *Chronobiol Int* 43: 201–207. DOI 10.1080/07420528.2025.2587796 | — | Abstract only |
| **COL87** | Colrain IM, Trinder J, Fraser G, Wilson GV (1987). "Ventilation during sleep onset." *J Appl Physiol* 63: 2067–2074. DOI 10.1152/jappl.1987.63.5.2067 | — | Abstract only |
| **TRI92** | Trinder J, Whitworth F, Kay A, Wilkin P (1992). "Respiratory instability during sleep onset." *J Appl Physiol* 73: 2462–2469. DOI 10.1152/jappl.1992.73.6.2462 | — | Abstract only |
| **WOR98** | Worsnop C, Kay A, Pierce R, Kim Y, Trinder J (1998). "Activity of respiratory pump and upper airway muscles during sleep onset." *J Appl Physiol* 85: 908–920. DOI 10.1152/jappl.1998.85.3.908 | — | Abstract only |
| **THO05** | Thomson S, Morrell MJ, Cordingley JJ, Semple SJ (2005). "Ventilation is unstable during drowsiness before sleep onset." *J Appl Physiol* 99: 2036–2044. DOI 10.1152/japplphysiol.01040.2004 | — | Abstract only |
| **NAI81** | Naifeh KH, Kamiya J (1981). "The nature of respiratory changes associated with sleep onset." *Sleep* 4: 49–59. DOI 10.1093/sleep/4.1.49 | — | Abstract only |
| **MAR13** | Marino M, Li Y, Rueschman MN, et al. (2013). "Measuring sleep: accuracy, sensitivity, and specificity of wrist actigraphy compared to polysomnography." *Sleep* 36: 1747–1755. DOI 10.5665/sleep.3142, PMC3792393 | — | Abstract only |
| **VHE15** | van Hees VT, Sabia S, Anderson KN, et al. (2015). "A novel, open access method to assess sleep duration using a wrist-worn accelerometer." *PLoS One* 10: e0142533. DOI 10.1371/journal.pone.0142533, PMC4646630 | — | Abstract only |
| **SCO20** | Scott H, Lack L, Lovato N (2020). "A systematic review of the accuracy of sleep wearable devices for estimating sleep onset." *Sleep Med Rev* 49: 101227. DOI 10.1016/j.smrv.2019.101227 | — | Abstract only |
| **SCO21** | Scott H, Whitelaw A, Canty A, Lovato N, Lack L (2021). "The accuracy of the THIM wearable device for estimating sleep onset latency." *J Clin Sleep Med* 17(5): 973–981. DOI 10.5664/jcsm.9070, PMC8320504 | PMC (HTML) | Full text |
| **CAN02** | Cantero JL, Atienza M, Stickgold R, Hobson JA (2002). "Nightcap: a reliable system for determining sleep onset latency." *Sleep* 25: 238–245. DOI 10.1093/sleep/25.2.238 | — | Abstract only |
| **FIC24** | Fichten CS, Libman E, Bailes S, et al. (2024). "Parasomnias in post-secondary students: prevalence, distress, and coping strategies." *Behav Sci* 14(8): 646. DOI 10.3390/bs14080646, PMC11351849 | Europe PMC | Full text, tables |
| **CLA17** | Clark I, Landolt HP (2017). "Coffee, caffeine, and sleep: a systematic review of epidemiological studies and randomized controlled trials." *Sleep Med Rev* 31: 70–78. DOI 10.1016/j.smrv.2016.01.006 | — | Abstract only |
| CUE15, CB14, OSW59 | Cuellar et al. 2015 (hypnic jerk scoping review, *Sleep Med Clin*); Calandra-Buonaura et al. 2014 (*Sleep Med* 15: 725); Oswald 1959 (*Brain* 82: 92) | — | CUE15 abstract only (no numbers in it). CB14 and OSW59 **NOT READ** |

---

## 1. Key findings (read this first)

1. **N1 is short, unstable and patchy.**
   - FACT: In a 20 min semi-reclined daytime break, the subjects who reached only N1 spent 1.35 ± 1.20 min in it. Those who
     reached N2 spent 1.79 ± 2.11 min in N1 plus 4.18 ± 2.94 min in N2, and 3 of them went "directly into N2 without
     passing by N1" (LAC21, Results, first section).
   - FACT: In 45 nights only 14 continuous 5 min N1 segments existed, against 730 for N2 (HER17, Results).
   - FACT: Hori EEG sub-stages flicker over seconds. Flattening (H4) and vertex-wave stages (H6–H8) mostly last under 30 s;
     alpha stages, ripples (H5) and spindles "tended to last > 2 minutes" (TAN96).
   - INFERENCE: A detector that needs a long hold, or a delay that is long compared with about 1–2 min, will often miss N1
     or land in N2.
2. **The hypnagogic imagery window is early.** FACT: 93.4% (112/120) of spontaneously signalled images arose in Hori
   sub-stages 4/5, where alpha drops out and theta appears (Germain & Nielsen 2001, as reported in NIE17 §2). FACT: In a
   single trained participant, the EEG-scored drowsiness rose sharply in the **last 20 s** before each reported
   intrusion (NOR15, Results).
3. **The Edison object drop marks accumulated microsleep, not consolidated N1** (LAC21, "Edison's technique" section). FACT:
   - Only 41% of 63 drops came after scored N1, but 78% came after microsleep episodes (MSEs);
   - "the bottle dropping after approximately 1 min of accumulated MSEs";
   - delta power rose in the 24 s before the drop;
   - the drop woke the subject every time;
   - only 5 of the 33 sleepers who dropped it had reached N2 before the first drop.

   INFERENCE: A behavioural-failure signal comes **late in the sleep-onset process but usually before N2**. OpenTDI's
   post-detection delay should therefore be short when the trigger is behavioural.
4. **The heart-rate fall at onset is real but small, and it is mixed with posture and time effects.** FACT:
   - HR averaged about 65.6 bpm in relaxed wake (EEG alpha), 61.8 in drowsiness and 61.4 with dominant theta (HUA18
     Table 2, first 30 min, ignored-tone condition). INFERENCE: that is about 4 bpm, or 6%.
   - HR in the same EEG stage fell about 1.5 bpm from the first 30 min to the next. The authors attribute this to the
     change from upright to semi-supine posture at the start (HUA18 Table 2, Discussion).
   - HR and blood pressure also fall after lights out, **before** theta appears (CAR05 abstract).
   - HR "does show a small sleep-related fall at sleep onset" but is mostly circadian (DEZ18 §3).
   - HR slowing starts about 30 s before Stage 1 onset (PIV96, preadolescents).
   - Each spontaneous arousal raises HR, and HR returns to the sleep level within 12 beats (BUR99).
5. **There is no evidence for a vagal or RMSSD rise at onset.** FACT:
   - Respiratory sinus arrhythmia did not change significantly from wake to stable stage 2 (BUR99, 6 participants).
   - HF power "did not change significantly" across the wake-sleep transition, while LF and very-low-frequency (VLF)
     power fell, VLF starting 2 min before onset (SHI06, 34 participants).
   - Whole-night studies do show higher HF in NREM than in wake (BON97; TRI01).

   INFERENCE: CLAUDE.md's "RMSSD +15%" trigger has no onset-period source.
6. **EDA falls into drowsiness, and N1 is the quietest stage.** FACT:
   - Palmar skin conductance level fell from relaxed wake to drowsiness to theta: 3.75 → 2.77 → 2.31 µS (HUA18 Table 2).
   - On the wrist, median EDA was lowest in NREM1 (0.18 µS, against 0.26 in NREM2 and 0.44 in SWS), and only 4% of EDA
     peaks occurred in NREM1, against 55% in NREM2 and 25% in SWS (SAN14 §3).

   INFERENCE: Wrist EDA levels during sleep are tenths of a µS, so Dormio's "> 4 µS" delta (raw ADC units in any case)
   cannot transfer. EDA peaks are an "**already past N1**" or arousal flag, not an onset trigger.
7. **Stillness alone cannot separate quiet wake from sleep.** FACT: Wrist actigraphy is 96.5% sensitive for sleep but only
   32.9% specific for wake (MAR13, 77 participants, 232,849 epochs).
8. **Behavioural stimulus-response tracks sleep onset best, and a wrist device can run one.** FACT:
   - The THIM ring vibrates about every 30 s, the user taps a finger, and two missed responses count as sleep. Its
     sleep-onset latency differed from PSG by 0.08 ± 0.49 min on one night and 0.57 ± 1.10 min on another (SCO21).
   - A review of 71 studies found that behavioural devices "consistently overestimated" PSG sleep-onset latency, "but to a
     comparatively low degree" (SCO20).
   - Responses typically stop "between late-N1 sleep and N2 sleep onset" (SCO21 Introduction, citing its refs 9–10).
9. **Distal skin warming predicts how fast sleep comes, but on a timescale of minutes.** FACT:
   - The distal-proximal skin temperature gradient was "the best predictor variable for sleep-onset latency", better than
     heart rate, core temperature, melatonin or sleepiness (KRA00).
   - Wrist temperature rises "slightly before and significantly after sleep onset" (ROD26).
   - The SDK exposes `SKIN_TEMPERATURE_CONTINUOUS` (`../samsung_sdk_1.4.1_verified_api.md`).
10. **People fall asleep differently.**
    - FACT: In 2 of 9 subjects, alpha fell "up to several minutes" before behavioural responses stopped (PRE14).
    - FACT: Brain-autonomic coupling lags were 0–49 s, mean |lag| 8 s, and 20 of 39 people had no measurable lag (ULK17).
    - FACT: The same person can have a smooth onset one night and a fragmented one the next (PRE14; LAC24 §1).
    - INFERENCE: Fixed population thresholds will fit some users badly. A per-session relative model with a continuous
      confidence is better supported.

---

## 2. What "sleep onset" and "N1" mean

### 2.1 Standard scoring (FACT)
- Under AASM rules, sleep onset is "the first 30-second epoch scored as any stage other than wakefulness, which, in
  practice, typically corresponds to an epoch of N1". This convention "is arbitrary and has changed over time (e.g. sleep
  onset used to be defined as the first epoch of N2 or 3 consecutive epochs of sleep)" (LAC24 preprint §"The standard
  definition").
- N1 is "characterised by a drop in alpha rhythm for over 50% of a 30-second window, slower EEG rhythms (theta/delta),
  and slow eye movements" (LAC24 Glossary).
- "N1 is the stage with the lowest inter-scorer agreement" (LAC24).
- LAC21 put in the Wake group anyone with no epoch of sleep. By definition such a person never had "more than 15 s of
  continuous sleep", although they "could have had shorter episodes of sleep" (LAC21 Methods, sleep scoring).
- Microsleep episodes (MSEs): LAC21 defined one as "any short window of at least 3 s of theta activity with alpha loss"
  (Methods). LAC24 defines them as "any segment of EEG slowing (theta activity) lasting less than 15 seconds", with a
  minimum of 1 to 3 s depending on the method (Glossary).

### 2.2 Ogilvie's view: a process, not a moment (FACT, abstract only)
- "entry into sleep is a continuous, interwoven series of changes which begin in relaxed drowsiness and continue through
  stage 1, often into the first minutes of stage 2" (OGI01).
- "The transition from waking brain to sleeping brain is traced accurately by Hori's nine-stage EEG system" (OGI01).
- As PRE14 reports it, Ogilvie "observed that behavioral responses could persist well into Stage N1" and "went so far as
  to suggest that N1 not be even considered to be true sleep" (PRE14 Discussion).

### 2.3 Hori's nine stages (via secondary sources; the chapter HORI94 was NOT READ)

| Hori stage | Content | Standard stage | Source |
|---|---|---|---|
| H1–H3 | Alpha patterns; H3 is "alpha intermittent, < 50%" | H1–2 = wake, H3 = N1 | TAN96, TAN97, LAC24 |
| H4 | EEG flattening | N1 | TAN96 |
| H5 | Ripples (theta) | N1 | TAN96 |
| H6–H8 | Vertex sharp waves; H7 is "vertex sharp-wave bursts" | N1 | TAN96, TAN97 |
| H9 | Spindles | N2 | TAN96, LAC24 |

- Scoring uses 5 s windows (LAC24) or 4 s epochs (NOR15).
- "H1-2 corresponds to standard wakefulness, H3-8 to N1, and H9 to the beginning of N2" (LAC24).
- Durations: alpha patterns, ripples and spindles "tended to last > 2 minutes", while flattening and vertex sharp waves
  "had peaks that lasted < 30 seconds". Shifts were mostly to adjacent stages, but about 20% of changes from the
  vertex-wave stages jumped more than one stage (TAN96).
- "the sleep onset process probably started before the onset of sleep stage 1 in standard criteria" (TAN97).
- NOT VERIFIED: the exact wording of H2 ("alpha intermittent ≥ 50%") is from general knowledge, not from a source I read.

### 2.4 Brain regions do not fall asleep together (FACT, LAC24 preprint)
- "the thalamus deactivates several minutes before the neocortex, with a temporal delay of 0 to 15 minutes depending on
  the participants".
- Frontal regions fall asleep before posterior ones.
- Slow eye movements appear "even before alpha drop".
- "a decrease in EMG activity, a slowing of heart rate and respiration, as well as variations in pupil diameter and
  electrodermal activity" accompany or precede the EEG changes, and "these changes are transient and non-linear"
  (LAC24 Fig. 2 legend).

---

## 3. Edison's technique: Lacaux 2021 and follow-ups

### 3.1 Protocol (FACT, LAC21 Methods)
- **Sample:** 103 healthy participants (73 women, 23.2 ± 3.6 years). 16 who found the hidden rule before the break were
  excluded, leaving Wake n = 49, N1 n = 24 and N2 n = 14.
- **Setting:** "a 20-min break in a dark room without sensory stimulation", "semi-reclined position in a chair, with their
  eyes closed and legs on a footrest".
- **Object:** held "in their right hand, with their hand carefully placed outside the armrest". It was a 14.5 cm × 5.5 cm,
  55 g cup or bottle, chosen to make a noise, be light ("to avoid cramped arms"), slippery, and "large enough so that the
  fist cannot close on it".
- **Procedure:** after a drop the subject reported "what was going through their mind before it fell", picked the object
  up and carried on.
- **Recording:** PSG with FP1, C3 and O1 EEG, EOG, **chin** EMG, a microphone and infrared video. No limb EMG or
  accelerometer was used.

### 3.2 Results relevant to timing (FACT, LAC21 Results)

| Measure | Value | Where |
|---|---|---|
| Time in N1 (N1 group) | 1.35 ± 1.20 min | Results §1 |
| Time in N1 + N2 (N2 group) | 1.79 ± 2.11 min N1 + 4.18 ± 2.94 min N2 | Results §1 |
| Insight after the break | N1 83% (20/24), Wake 31% (15/49), N2 14% (2/14) | Results §1 |
| Drops after scored N1 | 26 of 63 (41.27%) | "Edison's technique" § |
| Drops after microsleep | 49 of 63 (77.78%), "after approximately 1 min of accumulated MSEs" | same |
| Pre-drop EEG | Delta increase starting about 24 s before the drop (cluster −23.8 to −0.2 s) | same, Fig. 4A |
| Subjects "drifting to sleep" at the drop | 81.48% (self-report) | same |
| Hypnagogia collected at a drop | 61.70%, against 22.50% at the end of the nap without a drop | same, Fig. 4C |
| Hypnagogia in the control experiment | 36.49% (50/137) of sound awakenings after MSEs (30 min rest, mean MSE time 1.10 ± 1.08 min) | same |
| Drop woke the subject | 100% | same |
| N2 before the first drop | 5 of 33 sleepers who dropped (15%) | same |
| EEG "sweet spot" | Intermediate alpha (9–9.8 Hz, quadratic effect) and low delta (3.2–4.4 Hz, linear) predicted insight | "Neurophysiology of the sweet spot", Fig. 3 |

INFERENCES for OpenTDI:
- The drop needs a **loss of grip** (hand and finger tone), the same thing Dormio's flex sensor measured (see
  `../horowitz2020_dormio_paper.md` §2.1).
- With the forearm resting on an armrest, the drop is a finger or hand event. A dorsal-wrist accelerometer may see little
  of the release itself.
- It would clearly see the **startle and arousal movement after the impact**, since the drop always woke people.
- The impact sound is also loud and easy to detect with the phone microphone, which OpenTDI already opens for the report.
  None of this has been tested on SM-L500 hardware.

### 3.3 Follow-ups
- **LAC22 (FACT, abstract).** After a 30 min rest, object-location recall was lower ("10% forgetting") when the rest
  contained only N1 than when it reached N2. The introduction cites older work on "retrograde amnesia of the materials
  encoded in the few minutes prior to sleep onset" (Wyatt et al. 1994, 1997; NOT READ). INFERENCE: An incubation cue given
  just before sleep may be partly forgotten. Dormio repeats the cue after every report, and that is worth keeping.
- **LAC24 (FACT, preprint).** A review of the whole sleep-onset period. It names Edison's drop and Dormio's EDA, HR and EMG
  thresholds as the two existing ways to time hypnagogia sampling. It calls for "(sub)second-by-(sub)second"
  multimodal tracking with "behavioural and subjective measures in addition to" EEG. Hypnagogia "happens early (e.g.,
  after just a second of alpha-dropout)".
- **Other Oudiette-lab work** found in a Europe PMC author search (Bieth et al. 2024 and 2026 on semantic memory; Wong et
  al. 2025, the DREAM EEG database) concerns semantic restructuring or databases, not wrist physiology. I did not read it
  further. INFERENCE: The DREAM database (Nat Commun 2025, PMC12350935) may hold sleep-onset awakenings with
  physiology that could serve as replay fixtures; check its contents and licence before using it.

---

## 4. Hypnagogic imagery: when it happens

- **Hori-stage dependence (FACT, NIE17 §2, Fig. 2, reporting HORI94).**
  - "The likelihood of recalling dream mentation is present to some degree even at sub-stage 1 and increases
    progressively up to sub-stage 5".
  - "visual quality is high for all sub-stages, but plateaus at sub-stage 5".
  - Germain & Nielsen (2001): "93.4% or 112/120" of signalled images "arose from sub-stages 4/5".
- **Microdreams (FACT, NIE17 Fig. 3, single case).**
  - A microsleep "about 1 s in duration" with "an abrupt decrease in cervical and chin EMG" and a fall in occipital alpha.
  - "Cardiac activity is a steady 60 bpm".
  - Images are typically single movements that "erupt into awareness, eliciting surprise and a quick return to
    consciousness".
- **Last 20 s (FACT, NOR15, single participant, 10 sessions, 126 reports).**
  - The maximal Hori stage rose in the 20–0 s window before a button press, compared with 40–20 s and 60–40 s
    (p < 0.0005).
  - The Hori stages reached went up to 7 ("late NREM Stage 1").
  - Caveat: the authors note the "uncertain timing of the occurrence of reported hypnagogic experiences".
- **Tetris (FACT, STI00 abstract).** Participants who played Tetris "reported intrusive, stereotypical, visual images of
  the game at sleep onset", and so did amnesic patients who could not recall playing it. The timing of those awakenings
  ("after intervals of 15 to 180 s of Nightcap-defined sleep") is quoted from P20; see row 37 of the table in
  `../horowitz2020_dormio_paper.md` §4.
- **Nightcap (FACT, CAN02 abstract).**
  - The Nightcap is a head-worn eyelid and head-movement sensor. It scored onset as "the first of 4 consecutive 30-sec
    epochs with less than 5 [eyelid movements]".
  - Across 10 subjects × 4 naps it agreed with PSG 93% of the time (κ = 0.79), with a mean absolute error of 45 s, and was
    within 1 min in 85% of onsets.
  - It is the detector behind STI00's awakenings, and is head-based, not wrist-based.

INFERENCE: Imagery peaks around alpha dropout and theta onset (H4/H5), which is near the start of AASM N1. Behavioural
failure (a drop or missed responses) comes later (§5). Dormio's 1–5 min delay after a noisy, early autonomic trigger and
LAC21's zero delay after a late behavioural trigger can both land in N1. Copying the delay from one regime into the
other cannot.

---

## 5. Behavioural markers

- **Breathing-squeeze task (FACT, PRE14).** Subjects held "a small, 2oz, gel-filled stress ball" and squeezed it on each
  inhale. A breath with no matching squeeze was an error. 9 healthy subjects were recorded with high-density EEG, EMG over
  the finger flexor (flexor digitorum profundus) and a force glove.
  - The model was built on assumptions that, as sleep approaches, "Probability of a correct behavioral response
    decreases", "EMG squeeze amplitude decreases", alpha falls and theta rises.
  - A continuous "wake probability" model fit the data better than any single-point onset definition: median
    log-likelihood −1589, against −2781 for the first N1 epoch and −5828 for the first N2 epoch (Table 1).
  - In 2 of 9 subjects "alpha power declined up to several minutes prior to the termination of correct responses", and
    "In none of the cases did we observe correct responses in the presence of strong delta and theta".
- **Active probes are arousing (FACT, PRE14 Introduction).** "These active measurement schemes ... have all required the
  use of external stimuli that are potentially arousing and can disrupt sleep".
- **THIM (FACT, SCO21).** A ring on the index finger.
  - It delivers "brief, low-intensity vibrations at intervals averaging 30 seconds apart"; "When the individual does not
    respond to two consecutive vibrations, the device infers that they have fallen asleep".
  - Study 1 (12 participants, 411 and 527 trials): the PSG−THIM latency discrepancy was 0.08 ± 0.49 min on night 2 and
    0.57 ± 1.10 min on night 3.
  - In 23.74% of trials, PSG sleep onset "could not be determined before THIM ended the trial".
  - Study 2 (20 participants, revised algorithm): PSG latency 3.41 ± 2.21 min against THIM 3.65 ± 2.18 min (P = .25),
    r(s) > .73. The trials where PSG onset had not yet occurred fell to 10.24% (Abstract).
  - Responses cease "between late-N1 sleep and N2 sleep onset" (Introduction).
  - False positives, where THIM declared sleep while PSG showed wake, made up 31.04% of night-2 trials and 16.88% of
    night-3 trials (Study 1 Discussion).
  - In most of those trials (71.58% and 58.00%) the participant **had** tapped, but the device did not register it.
    - About 42–49% of the missed taps fell just beyond the time window.
    - Most of the rest were "not vigorous enough to exceed the accelerometer threshold".
- **Wearable review (FACT, SCO20 abstract).** 71 articles. For actigraphy, the average sleep-onset latency was "often not
  significantly different from PSG, but there was large inter-individual variability". Behavioural devices
  "consistently overestimated PSG-determined sleep onset latency, but to a comparatively low degree".

---

## 6. Autonomic and motor changes at sleep onset

### 6.1 Heart rate
- **Direction and timing (FACT, abstracts).**
  - "HR and RR [respiration rate] were lower in stable Stage 2 sleep compared with wakefulness".
  - "During the second and third phases of sleep onset, HR decreased at each transition into sleep and increased
    following each spontaneous arousal. This increase resolved rapidly, with a return to sleep levels by 12 beats after
    the arousal" (BUR99; 6 participants × 3 nights).
  - "a significant slowing of heart rate beginning 30s prior to Stage 1 onset, and a further decrease within 30s of
    Stage 2 onset" (PIV96; preadolescent boys).
  - "substantial falls in BP and HR after LO [lights out] before the initial onset of theta-activity ... and again after
    the onset of stable sleep". During phases with "repeated arousals ... the fall in both variables was retarded"
    (CAR05; 21 young adults, supine).
- **Magnitude (FACT, HUA18 Table 2).** 2 h eyes-closed EEG on a reclined lounger; ECG from the forearms; EEG vigilance
  scored per 1 s with the VIGALL algorithm (vigilance stages explained under the table). Mean HR in bpm (SD), first 30 min block:

| Condition | A1 relaxed wake (alpha) | B1 drowsy (alpha gone, slow eye movements) | B2/3 dominant theta/delta | Change A1 → B2/3 (INFERENCE) |
|---|---|---|---|---|
| Ignored tones | 65.63 (7.38), n = 22 | 61.79 (6.85), n = 36 | 61.42 (6.61), n = 24 | −4.2 bpm (−6.4%) |
| Attended tones | 65.76 (9.49), n = 32 | 63.45 (9.50), n = 34 | 60.00 (8.08), n = 19 | −5.8 bpm (−8.8%) |

  Caveats:
  - The n differs per stage, because a subject entered only the stages they reached. The across-stage differences above
    are therefore my arithmetic on group means, not paired effects.
  - The paper's paired t tests show a "continuous decline" from A1 to C (14 of 15 pairs significant, ignored condition).
  - VIGALL stage C ("sleep onset": K-complexes and spindles) is N2-like. B2/3 is the closest to N1.
- **Time-on-task and posture (FACT, HUA18).**
  - HR within the **same** EEG stage fell from the first to the second 30 min block: A1 65.63 → 64.04 and B1 61.79 →
    60.26 (ignored condition).
  - The authors write that this "could have been a consequence of postural change at the beginning of the experiment"
    (upright to semi-supine).
- **Circadian dominance (FACT, DEZ18 §3).**
  - "heart rate is strongly influenced by the circadian system"; "heart rate does show a small sleep-related fall at sleep
    onset".
  - Blood pressure falls by "approximately 4 mmHg at the point that the decision is made to go to sleep". Then, during
    N1, "each transient entry into dominant EEG theta rhythm is associated with a fall in blood pressure and each
    transient period of dominant EEG alpha rhythm is associated with an increase".
- **Arousal signature (FACT, BON97 abstract).** "heart rate acceleration at least 10 beats prior to the EEG arousal".
- **Dormio's own data** (finger PPG; see `../horowitz2020_dormio_paper.md` §2.3): HR delta Sleep −2.89 ± 22.94 against
  Wake +10.01 ± 18.7 bpm.
- **INFERENCE.**
  - The true group effect from relaxed wake to N1-like EEG is about 3–6 bpm (5–9%).
  - It is the same size as the drift from settling into posture and lights out (≥ 1.5 bpm), and as the within-person
    noise of a wrist PPG.
  - CLAUDE.md's 7% default is a plausible magnitude, but only against a baseline that has already settled. Measured from
    the first 3 min after lying down, part of any "drop" is posture.

### 6.2 HRV (RMSSD, HF, LF, VLF)
- **Onset-period studies (FACT, abstracts).**
  - BUR99: "PEP, TWA and RSA did not change significantly" (PEP: pre-ejection period; TWA: T-wave amplitude; RSA:
    respiratory sinus arrhythmia).
  - SHI06 (34 subjects: normal, apnoea and other sleep disorders):
    - "a significant increase in RR interval during sleep onset, although its variability decreased";
    - "Very-low-frequency power started to decrease significantly 2 min before sleep onset in all groups";
    - "low-frequency power decreased and high-frequency power did not change significantly", so LF/HF fell;
    - "All parameters reached a nadir at a defined time point during the process of falling asleep".
- **Stage and whole-night studies (FACT, abstracts).**
  - BON97 found "increases in high frequency components and decreases in low frequency components ... across NREM sleep
    stages".
  - TRI01: for BP, HF and LF, "the change occurred abruptly at sleep onset" (2 min epochs).
  - BUR97: respiratory sinus arrhythmia "showed a 24-h rhythm independent of sleep".
- **Pre-sleep arousal (FACT).**
  - People with a long sleep-onset latency had lower normalised HF power "over 3 time periods (first 10 min in bed
    intending to sleep, 10 min before, and 10 min after sleep onset)" (NAN19; 176 participants; 2.5 min sliding windows).
  - In naps, "pre-nap PEP and HFnu ... were associated negatively with sleep latency" (CEL18; 25 participants).
- **Measurement (FACT).**
  - Pulse-rate variability from PPG has "sufficient accuracy when subjects are at rest, although many studies suggest that
    short-term variability is somewhat overestimated by PRV" (SCH13).
  - Between-subject variance dominates sleep HRV. The median RMSSD in sleep was about 67–80 ms in young men (HER17
    Table 1; N1 was too rare to analyse).
- **INFERENCE.**
  - RMSSD tracks HF/RSA, which does not reliably rise during the onset period.
  - Breathing becomes irregular at alpha-theta switches (§6.5), which shifts RSA without any change in vagal tone.
  - VLF and LF need windows of minutes (VLF 0.005–0.04 Hz), far longer than an N1 bout.
  - So use RMSSD for logging and as a weak readiness signal before the session, not as an onset trigger.

### 6.3 EDA
- **Into drowsiness (FACT, HUA18).** Palmar SCL (thenar and hypothenar, non-dominant hand) showed "a continuous decrease
  from stage A1 to C". Raw means, first 30 min: 3.75 → 2.77 → 2.31 µS (ignored) and 2.66 → 2.51 → 2.03 µS (attended).
  The SDs are large (1–5 µS).
- **Coupling with EEG vigilance (FACT, ULK17).**
  - The maximal cross-correlation with EEG vigilance averaged 0.362 for HR and 0.277 for SCL.
  - Mean delays were −1.6 s (SD 20.5) and −1.4 s (SD 26.5).
  - The absolute lag Tmax averaged 8.1 s (SD 15.4, range 0–49 s).
- **Wrist EDA during sleep (FACT, SAN14 §2–3).** Affectiva Q sensor, 1 cm Ag/AgCl dry electrodes, worn on the "outer or
  inner wrist (dorsal or ventral forearm)", logging at 32 Hz.
  - 15 PSG nights in young adults: median EDA (averaged across participants) was 0.44 µS in SWS, 0.26 in NREM2, 0.18 in
    NREM1 and 0.26 in REM.
  - Distribution of peaks: NREM2 55 ± 4%, SWS 25 ± 4%, REM 12 ± 1%, NREM1 4 ± 0%. This order held at every
    peak-threshold tested (0.005–0.05 µS/s).
  - More than 60% of 30 s epochs had no peak.
  - About 85% of "storms" (two or more adjacent peak epochs) lasted under 5 min.
  - Wrist SCL was ≥ 0.03 µS higher than palm SCL in 74% of samples. Both sites showed the same number of peaks in 71% of
    epochs.
- **Wake versus N1 (FACT, HRL19 abstract).** Wrist SCL ("EDASEF") differed significantly between wake and N1, and between
  wake and every other stage. The direction is not stated in the abstract.
- **Site (FACT, VDO12 abstract).** For emotional responses, "arm, back, armpit, and thighbone were least responsive". See
  `../dormio_duo_wearable_analysis.md` §10.5 for the conflicting wrist-versus-palm literature.
- **HOR82** (skin potential in hypnagogia) was NOT READ. SM19 cites it for "changes in skin potential activity" at H2–H4
  (see `../horowitz2020_dormio_paper.md` §7).
- **INFERENCE.**
  - The expected onset pattern is a slow tonic decline with an absence of phasic peaks.
  - Peaks during a countdown mean arousal. Peaks after several minutes of sleep mean the user is probably past N1.
  - The Samsung sensor presumably sits on the dorsal wrist under the case (not verified). SAN14's dorsal and ventral
    data are the closest published analogue, but the electrode geometry differs.

### 6.4 Skin temperature
- **FACT.**
  - KRA00: the distal-proximal skin temperature gradient (DPG) "was the best predictor variable for sleep-onset latency
    (compared with core body temperature or its rate of change, heart rate, melatonin onset, and subjective sleepiness
    ratings)".
  - KRA07: "Body heat loss before lights off, via selective vasodilatation of distal skin regions, promotes sleepiness and
    the rapid onset of sleep".
  - FRO06 (MSLT daytime naps; 15 narcolepsy patients and 15 controls): "The increase in DPG was related to a shorter
    subsequent sleep-onset latency".
  - SAR08 (99 students, iButton on the inner wrist):
    - "WT started to increase in association to bed time and dropped sharply after awakening";
    - "A secondary WT increase, independent of feeding, was observed in the early afternoon";
    - the wrist temperature rhythm is phase-advanced 60 min relative to oral temperature.
  - ROD26: "Wrist temperature consistently rose slightly before and significantly after sleep onset".
  - WEI21: the mean sleep-minus-wake wrist temperature difference was 2.48 ± 0.88 °C in younger adults (10 people), and
    lower in older adults.
  - SAN14: wrist skin temperature "tends to climb for most of our participants during sleep".
- **INFERENCE.**
  - Distal warming is a slow propensity signal (minutes to tens of minutes) that also depends on blankets and room
    temperature. It cannot time an N1 bout of about 1 min.
  - It can serve as a prior ("this user is ready to sleep") and as a logged covariate.
  - The sampling rate and resolution of `SKIN_TEMPERATURE_CONTINUOUS` on SM-L500 are NOT VERIFIED.

### 6.5 Respiration
- **FACT (abstracts).**
  - "the loss of alpha-activity in the electroencephalogram was associated with a substantial, rapid, and highly
    predictable reduction in ventilation", "typically due to a reduction in tidal volume" (COL87; 5 men). The same holds in
    women, with no menstrual-phase effect (Colrain et al. 1990, *Sleep* 13: 491).
  - "transitions from alpha to theta were associated with falls, and from theta to alpha with increases, in ventilation"
    (TRI92; 21 subjects, about 11.3 onsets each).
  - At alpha-theta transitions, diaphragm and upper-airway muscle activity "fell" (WOR98).
  - With ventilator support, breath duration rose from 5.2 (SD 1.3) to 13.0 s (SD 2.1) at alpha→theta (THO05; 8
    subjects; an artificial set-up).
  - SHI06: "respiratory rate did not change, yet respiration became more stable". BUR99: the respiration rate was lower
    in stable stage 2.
  - NAI81: the changes "cannot be accounted for solely by changes due to habituation, merely lying quietly".
- **INFERENCE.**
  - The watch has no respiration channel. Respiration could be estimated from RSA in the IBI series, or from very small
    wrist movements, but both are crude.
  - Breath-by-breath instability probably adds noise to RMSSD around onset. Treat it as a research log item.

### 6.6 Movement and muscle tone
- **FACT.**
  - Chin and cervical EMG drop abruptly in microsleeps (NIE17).
  - Squeeze EMG amplitude falls (PRE14).
  - Muscle relaxation is part of the onset period (LAC24 Fig. 2B).
  - Wrist actigraphy: sensitivity 0.965, accuracy 0.863, **specificity 0.329** (MAR13).
  - The van Hees angle method scores sustained inactivity as "the absence of change in arm angle greater than 5 degrees
    for 5 minutes or more" (VHE15).
  - The Samsung SDK javadoc says "Accelerometer data doesn't include gravity" (`../samsung_health_sensor_sdk.md` row 6;
    not yet checked on hardware).
- **INFERENCE.**
  - Stillness is necessary but not sufficient.
  - Angle-based methods (VHE15, or the limp-wrist drift in `../dormio_duo_wearable_analysis.md` §10.6) need the gravity
    vector. If the SDK really removes it, only dynamic events (jerks, drops, arousal movements) are visible. Verify this
    first.
  - A 5 min inactivity window is far too long for N1. Use stillness as a gate over 30–60 s at most.

### 6.7 Hypnic jerks (sleep starts)
- **FACT.**
  - FIC24 (77 post-secondary students, Munich Parasomnia Screening Questionnaire, MUPS) found hypnic jerks in **53%**.
    The same table reports **79%** for Kirwan & Fortune's 135 Irish students (one-year prevalence, as FIC24 describes it;
    the original was NOT READ).
  - MUPS describes them as "Leg or body twitches that occur suddenly and unintentionally when falling asleep, often with
    the sensation of falling".
  - Case reports link them to SSRIs (Sathe et al. 2015; Kumar et al. 2023; abstracts only).
  - CUE15 calls them "a parasomnia categorized as a sleep-wake transition disorder".
- **NOT VERIFIED.** Their timing in Hori or AASM stages, their duration, and how often they occur per nap. OSW59 and
  CB14 would be the primary sources, and I could not read them.
- **INFERENCE.**
  - Prevalence is per person over a lifetime or a year, not per nap, so most naps will have no jerk.
  - A jerk is often followed by an arousal.
  - A 25 Hz accelerometer can see a whole-limb jerk.
  - Use it as an event to log, and as a weak bonus only when the following stillness and HR show no arousal.

---

## 7. Quantitative effects table

| Variable | Change at onset | Time course relative to onset | Source (FACT) | Measurement site in source |
|---|---|---|---|---|
| N1 duration in a 20 min nap | 1.35 ± 1.20 min (N1-only sleepers) | — | LAC21 Results | PSG (FP1, C3, O1) |
| N2 reached in a 20 min nap | 14 of 87 analysed; 4.18 ± 2.94 min N2 | Some go straight to N2 | LAC21 | PSG |
| Hori H4 / H6–H8 bouts | Typically < 30 s | — | TAN96 | Scalp EEG |
| Hori H1–3, H5 and H9 bouts | Typically > 2 min | — | TAN96 | Scalp EEG |
| Imagery | 93.4% of images in H4/H5 | Alpha dropout and theta onset | NIE17 (Germain & Nielsen 2001) | EEG + self-signal |
| Drowsiness before a hypnagogic report | Hori stage rises | Last 20 s before the report | NOR15 (n = 1) | 128-channel EEG |
| Edison object drop | 78% after microsleep; 41% after scored N1 | About 1 min of accumulated microsleep; delta rises 24 s before | LAC21 | PSG + video |
| Behavioural responses cease | Tap or squeeze stops | Late N1 to N2 onset; can lag alpha loss by "several minutes" | SCO21 (citing refs); PRE14 | Finger ring; forearm EMG |
| HR | 3.5–8.8% (−2.31 to −5.76 bpm; unpaired group means, first 30-min block, both conditions; percentages are arithmetic) from relaxed wake (A1) to drowsy or theta (B1, B2/3), group means | Tracks EEG vigilance; lag 0–49 s | HUA18 Table 2; ULK17 | Forearm ECG |
| HR | Slowing | Starts about 30 s before Stage 1; further fall within 30 s of Stage 2 | PIV96 | ECG (children) |
| HR | Falls after lights out, again at stable sleep; slowed by arousals | Before theta | CAR05 | ECG/BP, supine |
| HR after an arousal | Rises, back to sleep level | Within 12 beats | BUR99 | ECG |
| HR before an arousal | Accelerates | ≥ 10 beats before the EEG arousal | BON97 | ECG |
| HR drift (time and posture) | About −1.5 bpm within the same EEG stage, first versus second 30 min | First 30 min after posture change | HUA18 | ECG |
| RSA / HF | No significant change | — | BUR99; SHI06 | ECG |
| LF, VLF | Decrease | VLF from 2 min before onset | SHI06 | ECG |
| RR interval | Increases; variability decreases | During onset | SHI06 | ECG |
| Blood pressure | About −4 mmHg at "the decision to sleep"; dips with each theta bout | Lights out, then N1 fluctuations | DEZ18 | Continuous BP |
| SCL (palm) | 3.75 → 2.77 → 2.31 µS (A1 → B1 → B2/3, ignored) | Tracks vigilance | HUA18 Table 2 | Thenar and hypothenar |
| EDA level (wrist) | Lowest in NREM1 (median 0.18 µS) | — | SAN14 §3 | Dorsal or ventral forearm, dry electrodes |
| EDA peaks (wrist) | NREM1 4%, NREM2 55%, SWS 25% of all peaks | Storms mostly in the first half of the night | SAN14 §3 | Same |
| Distal-proximal temperature gradient | Rises; best predictor of sleep-onset latency | Before lights out to onset (minutes) | KRA00; FRO06 | Hands and feet against trunk |
| Wrist temperature | Rises slightly before, significantly after onset; sleep − wake about 2.5 °C | Minutes to hours | ROD26; WEI21; SAR08 | Wrist |
| Ventilation | Falls (tidal volume) | Abrupt at each alpha→theta switch; recovers at theta→alpha | COL87; TRI92; WOR98 | Face mask, pneumotachograph |
| Respiration rate | Unchanged (SHI06) or lower in stable N2 (BUR99) | — | SHI06; BUR99 | PSG |
| Chin and neck EMG | Abrupt fall in microsleeps | About 1 s events | NIE17 (case) | Surface EMG |
| Wrist actigraphy | Specificity for wake 0.329 | — | MAR13 | Wrist |
| Hypnic jerk prevalence | 53% to 79% of students report them | "when falling asleep" (timing NOT VERIFIED) | FIC24 | Questionnaire |

---

## 8. What the Galaxy Watch8 can measure

| Signal | Watch channel | Expected onset effect | Can it time N1? | Main risks (INFERENCE) |
|---|---|---|---|---|
| HR | `HEART_RATE_CONTINUOUS`, 1 Hz | −3 to −6 bpm, gradual, starting before N1 | Partly: trend and confirmation | Posture and lights-out drift is about the same size; motion artefacts; arousals add +HR spikes |
| IBI → RMSSD | `IBI_LIST` | No reliable rise at onset | No | PRV overestimates short-term variability; breathing irregularity at onset; ectopic and missed beats |
| IBI → HR acceleration | `IBI_LIST` | Acceleration ≥ 10 beats before an arousal | Yes, as an arousal **veto** | Needs clean beats |
| EDA tonic | `EDA_CONTINUOUS`, 1 Hz, µS | Slow decline; lowest in N1 | No (too slow) | Dorsal site; possibly tenths of a µS; contact and status dropouts |
| EDA phasic peaks | Same (derivative ≥ 0.005–0.05 µS/s) | Absent in N1; common in N2/SWS | Yes, as an arousal or "too deep" flag | 1 Hz limits slope resolution; thermal sweating under the case |
| Skin temperature | `SKIN_TEMPERATURE_CONTINUOUS` (rate NOT VERIFIED) | Rises before and after onset | No (minutes) | Blanket, room and contact effects |
| Stillness | `ACCELEROMETER_CONTINUOUS`, 25 Hz | Movement stops | No (quiet wake is also still) | Specificity about 0.33 |
| Posture or angle drift | Accelerometer, needs gravity | A slow drift | Weak | SDK says gravity is excluded; verify |
| Drop / arm-fall / grip loss (task) | Accelerometer, plus phone microphone for an impact | A discrete event after about 1 min of microsleep | **Yes**: the best-validated timing marker for a cheap device | The user must do the task; the drop wakes the user; the wrist may not see a hand-only release |
| Vibration-response (THIM-like) | Watch haptics plus accelerometer tap detection | Responses stop at late N1 to N2 | **Yes**, late | Active probes may arouse; learning effects; timing windows |
| Hypnic jerk | Accelerometer, 25 Hz | A sudden whole-limb burst | Occasional bonus | Rare per nap; often followed by arousal |

---

## 9. Individual variability and confounds

- **Phenotypes.**
  - FACT: In 2 of 9 subjects alpha dropped "up to several minutes" before responses stopped (PRE14).
  - FACT: Some subjects go straight to N2 (LAC21).
  - FACT: The thalamus leads the cortex by 0–15 min (LAC24, citing Magnin et al. 2010; NOT READ).
  - FACT: Autonomic-EEG lags range from 0 to 49 s (ULK17).
- **Night-to-night variation.** FACT: One participant had "a smooth and rapid transition to sleep on one night but with a
  different sleep onset profile on another night" (LAC24, describing PRE14 Fig. 5).
- **Posture.**
  - FACT: Upright to semi-supine lowers HR by itself over the next 30 min or more (HUA18 Discussion).
  - FACT: LAC21's subjects were semi-reclined in a chair, and Dormio's lay down (P20).
  - INFERENCE: A lying-down baseline taken in the first 2–3 min still contains posture settling.
- **Lights out and the decision to sleep.** FACT: HR and BP fall after lights out, before theta (CAR05); BP falls by about
  4 mmHg (DEZ18). INFERENCE: Dormio's practice of taking the baseline "when the subject initially lies down"
  (`../horowitz2020_dormio_paper.md` §2.2) mixes this effect into the detection delta.
- **Circadian time and naps.**
  - FACT: HR is mostly circadian (DEZ18; BUR97).
  - FACT: Wrist temperature has an early-afternoon rise (SAR08).
  - FACT: Pre-nap autonomic arousal predicts a longer nap latency (CEL18).
  - FACT: LAC21's break was in the daytime; the time of day was not stated in the text I read.
  - INFERENCE: Afternoon naps, like Dormio's 12:00–16:00 sessions, have less sleep pressure than night onsets. Expect
    longer latencies and more wake–N1 fluctuation, so the detector should tolerate re-entries.
- **Pre-sleep arousal.** FACT: People with a long sleep-onset latency show lower normalised HF power from the first
  10 min in bed (NAN19). Caffeine "typically prolonged sleep latency" and increased "stage-1, wakefulness, and arousals",
  with "pronounced individual differences" (CLA17).
- **Age.** FACT: Foot warming no longer accelerated sleep onset in elderly insomniacs (Raymann, Swaab & Van Someren 2007,
  *Physiol Behav* 90: 257; abstract only). FACT: The wrist sleep–wake temperature contrast is smaller in older adults
  (WEI21).
- **Medication.** FACT: SSRIs are linked to hypnic jerks in case reports.
- **Sleep deprivation.** FACT: Frontal delta at onset increases after sleep deprivation (LAC24, citing its ref. 35;
  NOT READ).
- **Measurement site.** FACT: Palm and wrist EDA differ in level and sometimes in peaks (SAN14), and arm sites respond
  least to emotional stimuli (VDO12).

---

## 10. Conflicts, gaps and NOT VERIFIED items

| ID | Issue | Sources |
|---|---|---|
| G1 | No paper I read gives a **within-subject** bpm drop from wake to the first N1 epoch. BUR99 and CAR05 report it, but I could only read their abstracts (CAR05's publisher returned HTTP 403). HUA18 gives group means per EEG stage with different n | BUR99, CAR05, HUA18 |
| G2 | RSA/HF unchanged during onset (BUR99, SHI06), against HF changing "abruptly at sleep onset" (TRI01). TRI01 compares 2 min epochs of stable stages, not the transition itself | BUR99, SHI06, TRI01 |
| G3 | Respiration rate unchanged (SHI06) against lower in stable N2 (BUR99) | SHI06, BUR99 |
| G4 | The direction of wrist EDA change from wake to N1 is not in the HRL19 abstract. Palm SCL falls (HUA18). Wrist N1 has the lowest level among sleep stages (SAN14), but SAN14 did not report wake | HRL19, HUA18, SAN14 |
| G5 | Hypnic-jerk timing, duration and per-nap rate: primary sources not read | OSW59, CB14 |
| G6 | Hori stage definitions taken from secondary sources; HORI94 itself not read | HORI94 |
| G7 | The time of day of LAC21's break, and its Table S1 (microsleep times), were not read | LAC21 |
| G8 | Sampling rate and resolution of `SKIN_TEMPERATURE_CONTINUOUS`; whether the accelerometer keeps gravity on SM-L500 | Samsung SDK |
| G9 | The DREAM database (Wong et al. 2025) may hold sleep-onset physiology; its contents and licence are unchecked | PMC12350935 |
| G10 | The full texts of BUR99, CAR05, SHI06, OGI01, HORI94, HOR82 and the hypnic-jerk primaries were paywalled, blocked or not indexed. The HR magnitudes here therefore rest mainly on HUA18's group means. Nothing in this file has been tested on SM-L500 hardware | — |

---

## 11. Comparison with CLAUDE.md §4

| CLAUDE.md default | What the physiology says | Verdict |
|---|---|---|
| 180 s baseline right after lying down | HR keeps drifting down from posture and lights out (HUA18, CAR05) | Keep it, but discard a settling period, or use a slowly updating baseline (INFERENCE) |
| HR 15 s mean < 0.93 × baseline | Group effect 3.5–8.8% from relaxed wake to drowsy or theta (HUA18 Table 2, unpaired group means); starts before N1 (PIV96, CAR05) | The magnitude is plausible. It needs a settled baseline and a longer smoothing window, about 30 s (INFERENCE) |
| RMSSD > +15% | No onset-period evidence of a rise (BUR99, SHI06) | Not supported. Log it; do not trigger on it |
| EDA slope ≤ 0 and no phasic spikes | Tonic SCL falls into drowsiness (HUA18); N1 has the fewest peaks (SAN14; a share of the night's peaks that mostly reflects little time in N1; SAN14 did not compare quiet wake) | The direction is consistent, but this is true of most quiet wake too. Not a trigger. A veto would conflict with owner requirement 11 (EDA bonus-only, [SPEC] §3) and needs the owner's sign-off |
| 60 s accelerometer σ stillness gate | Specificity 0.33 for wake (MAR13) | Necessary, not sufficient |
| Hypnic-jerk bonus | Common per person (53–79%), timing unverified | Weak bonus at most |
| 20 s hold (10 s if all three signals) | Microsleeps are 3–15 s (LAC21, LAC24); H4 and vertex stages last < 30 s (TAN96); imagery comes in the last 20 s (NOR15); autonomic lags reach 0–49 s (ULK17) | No source for these numbers. 20 s has the right order of magnitude; keep it configurable |
| Fixed 180 s post-detection countdown | N1 lasts about 1.35 min in naps (LAC21); N2 removes the insight benefit (LAC21) | Risk of overshooting into N2. The delay should depend on the trigger type (§12) |

---

## 12. Implications for OpenTDI

Evidence strength is labelled as **strong** (several independent primary studies agree), **moderate** (one good primary
study, or several smaller ones), **weak** (indirect, very small samples, or abstract-only) or **anecdotal** (case reports or
single participants). Every parameter value below is INFERENCE unless a source is attached to that value.

1. **Model sleep onset as a continuous probability, not a single threshold crossing.**
   - Expose a `wakeProbability` or confidence (0–1) that integrates HR trend, stillness and EDA veto, and a behavioural
     channel when one is enabled.
   - Fire the trigger when it falls below a configurable level for a short hold.
   - Basis: a continuous model fit behaviour far better than any single-point onset definition (PRE14 Table 1).
     Ogilvie's convergence view (OGI01), and the variability documented in LAC24, ULK17 and PRE14.
   - Evidence: **moderate**.
2. **Settle the baseline before measuring deltas.**
   - Start the HR baseline only after a settling period: suggested default 3 min after lying down and lights out, then the
     existing 180 s window.
   - Or let the baseline track the slow drift, for example an exponential mean with τ of about 10 min that is frozen
     while the trigger is armed.
   - Basis: HR falls about 1.5 bpm within the same EEG stage over the first 30 min after a posture change (HUA18), and HR
     and BP fall after lights out before theta (CAR05).
   - Evidence: **moderate** for the drift; the values are GUESSED.
3. **Keep HR as the main autonomic signal, smoothed over about 30 s, with a default drop of 5% (range 3–9%).**
   - The expected group effect is 3.5–8.8% (−2.31 to −5.76 bpm; unpaired group means, first 30-min block, both conditions; percentages are arithmetic) from relaxed wake to drowsiness or theta (HUA18 Table 2).
   - It starts up to 30 s before Stage 1 (PIV96), and Dormio's absolute 5 bpm sits in the same range (P20).
   - Expect poor specificity on its own: the SDs are about 7–9 bpm between subjects (HUA18) and about 20 bpm in
     Dormio's finger-PPG deltas (P20 Fig. 4).
   - Evidence: **moderate** for the direction and size; **weak** for any single threshold.
4. **Demote RMSSD from trigger to logged feature.**
   - Neither onset-period study found a vagal (RSA/HF) rise (BUR99, SHI06).
   - PPG overestimates short-term variability (SCH13).
   - Breathing is unstable at alpha-theta switches (TRI92, COL87).
   - Keep RMSSD in `BiosignalPacket` for replay and research.
   - Evidence: **moderate** (two abstracts plus a review agree).
5. **Use fast autonomic reversals as an arousal veto during the countdown.**
   - Abort or reset the countdown when either happens:
     - HR rises above the running sleep level by more than about 5 bpm within about 10 beats (BON97: acceleration
       ≥ 10 beats before an EEG arousal; BUR99: post-arousal HR settles within 12 beats);
     - an EDA phasic peak with a derivative ≥ 0.01 µS/s appears (SAN14 tested 0.005–0.05 µS/s).
   - Evidence: **moderate** for the phenomena; the values are GUESSED.
6. **Add a "too deep" guard.**
   - Raise it when, after onset, EDA peaks start appearing (the user is probably in N2: 4% of the night's wrist EDA peaks fell
     in NREM1 against 55% in NREM2, SAN14; these shares mostly reflect time spent in each stage).
   - Raise it also when the time since onset exceeds about 4 min (LAC21: N2 sleepers had 4.18 ± 2.94 min of N2 in a
     20 min nap, and N2 abolished the insight gain).
   - The response: prompt immediately, and shorten the next loop's delay.
   - Evidence: **moderate** for the stage distribution; **weak** for the timing.
7. **Make the post-detection delay depend on the trigger type, not one fixed 180 s.**
   - Autonomic or HR trigger, which comes early and is noisy: keep Dormio's variable 1–5 min with presets of 60, 90 and
     180 s (P20; SR23), and a **default of 90 s**.
   - Behavioural trigger (drop, missed responses), which comes at late N1: a 0–30 s delay, **default 0**.
   - Basis: N1 bouts last about 1.35 min (LAC21); imagery is concentrated at H4/H5 (NIE17); drops follow about 1 min of
     microsleep and come before N2 in 85% of sleepers (LAC21); behavioural responses stop at late N1 to N2 (SCO21).
   - Evidence: **moderate**.
8. **Offer an optional "Edison" protocol (`OnsetTask.OBJECT_DROP`).**
   - The user holds a light object (LAC21 used a 55 g, 14.5 cm bottle) with the hand over the bed edge.
   - Detect the impact with the phone microphone, and the hand-release or arousal burst with the 25 Hz accelerometer.
   - Prompt at once with "what was going through your mind", the LAC21 procedure.
   - Basis: hypnagogia was caught 61.7% of the time at a drop, against 22.5% at the end of a nap without a drop (LAC21).
   - Evidence: **moderate** (n = 103, PSG-validated), but only in a semi-reclined chair. The wrist-side detection is
     untested.
9. **Offer an optional vibration-response task (`OnsetTask.HAPTIC_PROBE`), THIM-style.**
   - A faint watch vibration about every 30 s; the user answers with a small finger or wrist tap, detected by
     accelerometer.
   - Two misses mean onset, then prompt immediately.
   - Use a generous response window (≥ 3 s, GUESSED) and a per-user tap threshold calibrated during the baseline.
     Most of THIM's false "asleep" calls were real taps that fell just outside its window or were too weak for its
     accelerometer threshold (SCO21).
   - Basis: sleep-onset latency within about 0.1–0.6 min of PSG (SCO21); behavioural devices overestimate latency only
     slightly (SCO20).
   - Caveats: false "asleep" calls occurred in 17–31% of trials with THIM's first algorithm (SCO21), and active probes
     "are potentially arousing" (PRE14).
   - Evidence: **moderate** for detecting onset; **weak** for its effect on hypnagogia.
10. **Treat stillness as a gate only, over 30–60 s, never as evidence of sleep.**
    - Wrist actigraphy's specificity for wake is 0.329 (MAR13).
    - Do not use 5 min inactivity windows (VHE15) for N1.
    - Before building any angle or limp-wrist feature, verify on SM-L500 whether `ACCELEROMETER_CONTINUOUS` includes
      gravity.
    - Evidence: **strong** for low specificity (77 participants, 232k epochs; consistent with SCO20).
11. **Log the hypnic jerk; do not weight it much.**
    - Detect a short burst after ≥ 10 s of stillness, followed by stillness and no rise in HR.
    - Store it as an event with at most a small confidence bonus.
    - Basis: 53–79% of students report jerks at all (FIC24), but their timing and per-nap rate are not verified.
    - Evidence: **weak / anecdotal**.
12. **Record skin temperature as a readiness prior and covariate.**
    - Stream `SKIN_TEMPERATURE_CONTINUOUS` at its native rate, once its rate is verified on the device.
    - Show "wrist warming since lie-down" in the dashboard.
    - Optionally use a rise of ≥ 0.3 °C over 10 min (GUESSED) to raise the prior probability of onset.
    - Never use it as a trigger.
    - Basis: distal warming best predicts sleep-onset latency (KRA00, FRO06); wrist temperature rises around onset
      (ROD26, SAR08).
    - Evidence: **moderate** for the propensity link; **weak** for wrist-only real-time use.
13. **Validate against EEG, not self-report alone.**
    - For the replay harness and confidence calibration, record a subset of sessions with a consumer EEG headband or PSG.
    - Score them in AASM 30 s epochs **and** with a microsleep criterion (≥ 3 s theta with alpha loss; LAC21).
    - Report detection latency against EEG onset with a Bland-Altman analysis and ±1 min limits, as done for THIM and the
      Nightcap (SCO21, CAN02).
    - Report the proportion of prompts delivered in N1 against wake against N2.
    - Keep Dormio's "were you asleep?" (Awake / Halfway / Asleep) as the label available at home.
    - Evidence: **strong** that EEG is the reference and that N1 scoring is unreliable (LAC24, PRE14); the methods come
      from SCO21 and CAN02.
14. **Capture the confounds per session.**
    - Log: time of day, nap or night, minutes since lying down, posture (from accelerometer, if gravity is available),
      caffeine in the last 6 h, a self-rated sleepiness score before the nap, and the medication flag "SSRI: yes/no".
    - Use them to stratify replay results.
    - Basis: HUA18, CAR05, CLA17, CEL18, NAN19, case reports on SSRIs and jerks.
    - Evidence: **moderate** that these factors shift onset physiology; **weak** that logging them improves detection.
15. **Keep re-delivering the incubation cue after each report, and let loops re-detect.**
    - N1-only rests produced about 10% forgetting of material learned before (LAC22).
    - Sleep onset fluctuates between wake and N1, with re-entries (PRE14; TAN96; DEZ18 on BP per theta bout).
    - Evidence: **weak** for the cue effect; **moderate** for the fluctuation.
