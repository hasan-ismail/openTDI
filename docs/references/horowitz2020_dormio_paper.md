# Dormio / Targeted Dream Incubation (TDI): what the MIT papers and theses actually say

Research extract for OpenTDI, made 2026-10-27. It answers owner requirements D.10 (where every
timing and threshold comes from), D.11, D.12 and E.15 (timer-only mode).

**Labels.** **FACT** means the source states it, with a quote or a close citation. **INFERENCE**
is my own reasoning or arithmetic. "NOT FOUND" means I searched every source listed below and
the value does not appear in any of them.

---

## 0. Sources and how each one was obtained

| Key | Source | Where from | Status |
|---|---|---|---|
| **P20** | Horowitz AH, Cunningham TJ, Maes P, Stickgold R (2020). "Dormio: A targeted dream incubation device." *Consciousness and Cognition*. DOI 10.1016/j.concog.2020.102938. PMCID PMC7590944, NIHMS1635492, PMID 32480292 | https://pmc.ncbi.nlm.nih.gov/articles/PMC7590944/ (HTML fetched with curl and converted to text) | Full text, all tables and figure captions read. I viewed Figure 1 and Figure 4 as images; Figure 1 was re-downloaded from `https://cdn.ncbi.nlm.nih.gov/pmc/blobs/af91/7590944/3d528b6e46d4/nihms-1635492-f0001.jpg` and compared byte for byte |
| P20-AppA | P20 supplement "Apprendix_A.docx" (the exact instructions) | `https://pmc.ncbi.nlm.nih.gov/articles/instance/7590944/bin/NIHMS1635492-supplement-Apprendix_A.docx` | **NOT OBTAINED.** PMC served a "Preparing to download ..." proof-of-work page; Europe PMC answered "Article with id PMC7590944 is not open access one". I did not try to get around the bot check. The same instruction text is published in SR23-SM §1.1 and PhD22 §5.3.2 (below). INFERENCE: those copies are probably the same text as Appendix A, since PhD22 §5.3.2 belongs to the same experiment, but I could not compare them |
| **SM19** | Haar Horowitz A (2019). *Incubating Dreams: Awakening Creativity*. SM thesis, MIT Media Lab. DSpace handle 1721.1/124208 | `https://dam-prod.media.mit.edu/x/2020/06/09/Incubating%2BDreams%2BAwakening%2BCreativity%2BAdam%2BHaar%2BThesis%2B8.5.19-compressed.pdf` (168-page print of a Google Doc) | Full text read. Pages below are PDF page indexes |
| **PhD22** | Haar Horowitz AJ (2022). *Interfacing With Dreams: Novel Technologies and Protocols for Targeted Dream Incubation*. PhD thesis, MIT, submitted 19 Aug 2022. DSpace handle 1721.1/151983 | `https://dam-prod.media.mit.edu/x/2022/08/18/%20haarhorowitz-adam-phd-MAS-2022.pdf` | Full text read. Pages are PDF page indexes, which match the printed page numbers. Chapter 3 Paper 1 (a reprint of P20) is embedded as page images with no text layer, so P20 is quoted from PMC instead |
| **SR23** | Haar Horowitz A, Esfahany K, Vega Gálvez T, Maes P, Stickgold R (2023). "Targeted dream incubation at sleep onset increases post-sleep creative performance." *Sci Rep* 13:7319. DOI 10.1038/s41598-023-31361-w, PMC10185495 | Europe PMC REST `fullTextXML` | Full text read |
| **SR23-SM** | SR23 Supplementary Materials (`41598_2023_31361_MOESM1_ESM.pdf`) | Europe PMC REST `supplementaryFiles` zip | Read. Contains the verbatim instructions (§1.1) and prompts (§1.2) |
| **FS24** | Bellaiche L, Haar Horowitz A, McClay M, Bottary R, Denis D, Chen C, Maes P, Seli P (2024). "Targeted dream incubation at a distance: the development of a remote and sensor-free tool for incubating hypnagogic dreams and mind-wandering." *Front Sleep* 3:1258345. DOI 10.3389/frsle.2024.1258345, PMC12713799 | Europe PMC REST `fullTextXML` | Full text read. This is the peer-reviewed version of PhD22 Experiment 2, the non-contact "Dormio Light" study |
| **FS26** | Haar Horowitz A, Konkoly KR, Carr M, Stickgold R, et al. (2026). "Targeted dream incubation at sleep onset can influence later dream content in REM sleep: a pilot study." *Front Sleep* 5:1812535. DOI 10.3389/frsle.2026.1812535, PMC13341471 | Europe PMC REST `fullTextXML` | Full text read. This is the published version of PhD22 Experiment 4 |
| **CODE** | `reference_repos/dormio/js/script_timer.js` and `reference_repos/dormio/timer.html` (clone of https://github.com/christinatchen/dormio, HEAD 7c00cc3, dated 2023-07-05) | local clone | Cross-checked only for the timer protocol. FS24 gives the study site as `https://christinatchen.github.io/dormio/timer.html` |
| MLP | MIT Media Lab project page "Targeted Dream Incubation" | https://www.media.mit.edu/projects/targeted-dream-incubation/overview/ (WebFetch summary) | Qualitative only, no numbers |
| — | DSpace records 1721.1/124208 and 1721.1/151983 | dspace.mit.edu | **HTTP 429 (rate limited, Retry-After 900 s).** The PDFs were taken from the Media Lab DAM instead |
| — | dream.media.mit.edu | — | **Could not fetch: DNS ENOTFOUND** |

---

## 1. Protocol timeline (main lab protocol: P20 / SR23 / PhD22 Experiment 1)

### 1.1 Setting and participants
- **FACT.** Daytime nap. "Participants arrived at the laboratory in the afternoon between the hours of 12:00 pm and 4:00pm" (P20 §2.3). SR23 adds the reason, "optimizing for the postprandial increase in sleepiness", and says "Participants were instructed not to consume stimulants on the day of the experiment" and were "screened for exclusion criteria of any self-reported history of sleep or psychiatric disorders" (SR23 Methods > Participants).
- **FACT.** "Participants in the Sleep group were given a 45-min sleep opportunity" (P20 §2.3). Sleep participants wore an eye mask (P20 §2.3).
- **FACT, the experimenter's role.** "Experimenters remained in the room with participants, out of sight" (P20 §2.3). "Experimental instructions were delivered by an experimenter, while audio prompts from the Dormio were delivered via pre-recorded human voice" (P20 §2.4). At the end, "the experimenter instructed the participant to wake up fully" (P20 §2.4, Condition 1).

### 1.2 Sequence of one session (Sleep+Tree condition)
Each step below is FACT unless marked otherwise.

| # | Step | Source and quote |
|---|---|---|
| 0 | Experimenter reads the instructions (verbatim in §5) | SR23-SM §1.1; PhD22 §5.3.2 p.106 |
| 1 | User lies down wearing the hand sensor, closes the hand and keeps it closed | P20 §2.1: "From the moment the user lies down wearing the hand sensor, they are asked to close their hand as they descend into sleep". PhD22 p.58: "Subjects are asked to close their hand, and hold it closed, when they first lie down" |
| 2 | Calibration (baseline) | P20 §2.2: "These three measures are averaged over the first 120 seconds when the subject initially lies down". **But P20 Figure 1 labels it "calibration period (3:00 min)"** (seen in the figure image). PhD22 p.59: "a customizable calibration time, default of 120 seconds". See conflict C1 in §9 |
| 3 | First incubation prompt | P20 §2.4: "Upon lying down, the Dormio web app instructed these participants to 'Think of a tree'". PhD22 p.58: "Once the calibration period is done, the dream prompt message plays once, and subjects begin falling asleep" |
| 4 | Sleep-onset detection: any one threshold crossed | P20 §2.2: "Awakenings were performed whenever one of these thresholds was passed" (thresholds in §2) |
| 5 | Variable delay before waking | P20 §2.4: "a variable timer was triggered. This timer instigated wakeups from 1:00 to 5:00 minutes after Dormio-detected sleep onset, to allow participants to experience different depths of sleep" |
| 6 | Wake prompt | P20 §2.4: "the computer alerted participants, 'You're falling asleep,' and asked them to verbally report, 'Please tell me, what's going through your mind'" |
| 7 | Record the report | P20 §2.4: "and recorded their verbal response". SR23 Methods > Dormio device: "**Verbal reports were not subject to a time limit.**" |
| 8 | Sleep-state self-report | P20 §2.4: "Once participants finished speaking, Dormio asked about their sleep state ('And were you asleep?'), to which participants could respond 'Awake', 'Halfway' or 'Asleep'" |
| 9 | Re-incubation and permission to sleep | P20 §2.4: "Dormio then instructed them, 'Remember to think of a tree' and 'You can fall back asleep now'" |
| 10 | Loop: detect onset again, wait 1–5 min, wake | P20 §2.4: "This loop of events was repeated for 45 minutes, enabling the collection of multiple hypnagogic reports" |
| 11 | End | P20 §2.4: "At the end of the last loop, the experimenter instructed the participant to wake up fully." PhD22 p.58, for home use: "the user can wake up to the sound of an alarm and find their stored audio dream reports, or set no alarm and continue sleeping" |

- **FACT, number of awakenings.** The protocol sets no number; the loop runs for the 45-minute window. "Sleep reports depend on the timing of sleep onset with each awakening within a 45-minute nap opportunity" (P20 footnote 1). Measured: SI "n = 13 provided an average of 5.15 reports", SN "n = 12 ... 5.75" (SR23 Results). For self-directed use the user sets the number: "This cycle of sleep, reporting and re-prompting is repeated a number of times, set by the user" (P20 §2.1).
- **FACT, the user-settable depth (app setting, not the Experiment 1 protocol).** "the user selects the desired biosignal calibration period, number of rounds of hypnagogia and depth of hypnagogic experience: shallow (1 minute), medium (1:30 minutes) or deep (3 minutes) for each round. This time period reflects the period of time that users will be allowed to sleep without interruption after the system detected N1" (PhD22 p.56; same text in SM19 p.61). "This timer can be customized to vary over the course of the session, and can do so pseudo-randomly" (PhD22 p.58).
- **FACT, time to fall back asleep.** The lab protocol has no fixed re-sleep window. After "You can fall back asleep now" the system waits for the next detected onset. FS26 measured "an average of 8.23 min between successive sleep onset reports (SD = 2.32, range = 6–12 min)" (FS26 Results > N1 reports).
- **FACT, first sleep-onset latency.** "Across all participants, sleep onset averaged 10.3 ± 6.4 (S.D.) minutes" (P20 §3.3). SM19 p.89 gives "10.31 (±S.D. 6.39) minutes".

### 1.3 Wake control conditions (useful as an OpenTDI control or timer condition)
- **FACT.** Wake+Tree: participants "sat upright with head unsupported (so the experimenter could survey for muscle tone loss, which would indicate sleep onset), eyes closed, and were instructed to stay awake ... Once 7 minutes had passed, approximating sleep onset time, a variable timer was triggered from 1 to 5 minutes later. At the end of this time, Dormio alerted participants and asked them to 'Please tell me what's going through your mind' and recorded their responses" (P20 §2.4, Condition 3). Wake-Tree is the same with "Remember to observe your thoughts" (Condition 4).
- **FACT.** "Participants in the Wake group were asked for 4–5 mentation reports, depending on timing of sensor setup and length of mentation reports" (P20 §2.3). Measured: WI 4.66 and WN 4.00 reports on average (SR23 Results).
- **INFERENCE.** One wake loop lasts 7 + U(1–5) = 8–12 min plus the report, which roughly matches the 6–12 min measured between sleep-onset reports in FS26.

### 1.4 Earlier protocols (SM19 pilot, Dormio v1)
- **FACT.** v1 used a force-sensitive resistor (FSR) glove and the Jibo robot. "Once a loss of muscle control was picked up by the system, a 3-minute timer was triggered after which the robot would alert participants they were falling asleep. This 3-minute window was chosen to ensure full sleep onset transition, and because previous work with serial awakenings has shown hypnagogic imagery can continue into early stage 2 sleep" (SM19 p.73). "This loop of events was repeated three total times" (SM19 p.73). n = 8 enrolled, 2 excluded for "not tired enough", 6 analyzed (SM19 p.72–73). Evening sessions "between the hours of 5:00pm and 9:00pm" (SM19 p.72).
- **FACT.** v1 dialogue: "Dormio says 'remember to think of [dream cue (a)]', then 'tell me, what are you thinking about' and finally 'can you tell me more?', with a hard-coded 2-minute delay between prompts" (SM19 p.71).

---

## 2. How sleep onset / N1 was detected

### 2.1 Sensors (FACT)
- "Each of the three Dormio sensors is sampled at 100hz" (P20 §2.2; PhD22 p.61).
- HR: "Adafruit's Pulse Sensor Amped on their middle finger" (P20 §2.2).
- Muscle tone: "a voltage divider composed of a resistor and a 4.5" Sparkfun flex sensor wrapped around the middle finger" (P20 §2.2). PhD22 p.61 and SM19 say "**index** finger" (conflict C2).
- EDA: "a constant voltage source, a voltage divider and a low-pass filter to measure conductance between two electrodes placed on the **bottom of the wrist**" (P20 §2.2).
- Behavioural task: "after users gently close their hand when they lie down to sleep, a flex sensor monitors the slow opening of the hand, with its parallel loss of muscle tone" (P20 §2.2). "finger flexion begins to decrease as they forget or fail at this passive behavioral task" (P20 §2.1).
- Hardware: an nRF51 (RFD22301) module on the wrist, BLE, a 3.7 V Li-ion battery "for over 20 hours" (PhD22 p.61).

### 2.2 Algorithm (FACT)
- **Baseline.** "These three measures are averaged over the first 120 seconds when the subject initially lies down, and predefined deviations from these mean values are interpreted as Dormio-defined sleep onset" (P20 §2.2). The baseline is a mean only: no SD, z-score or rolling window is described. "Each of these signals are normalized over a calibration period each time a user starts a Dormio session" (P20 §2.1).
- **Thresholds.** "Heart Rate (BPM) deltas of > 5 BPM, Electrodermal Activity sensor deltas > 4 μSemen or flexor muscle sensor deltas > 8 KΩ were taken as indications of sleep onset" (P20 §2.2). SR23 gives the same values: "heart rate changes of > 5 BPM, electrodermal activity sensor changes > 4 μSiemen, or flexor muscle sensor changes > 8 KΩ".
- **Logic is OR, with no hold time.** "Awakenings were performed whenever one of these thresholds was passed" (P20 §2.2). SR23: "surpassing one of these thresholds triggered a variable timer of 1–5 min". No source describes any persistence, debounce or hold duration.
- **Direction is not stated** for the thresholds. PhD22 p.59: "drops in heartbeats per minute (BPM) and shifts either up or down in EDA coincide with loss of muscle tone".
- **Units are not physical.** PhD22 p.59: "BPM units for delta calculation are beats per minute, while Electrodermal Activity and Flex units are raw analog-to-digital units which index changes in conductance (µS) and resistance (K) respectively"; it calls the EDA threshold "> 4 conductance units" and the flex threshold "> 8 resistance units". SM19 p.65 has the same wording.
- **Where the thresholds came from.** "based on pilot data from 5 participants with repeated descent into sleep followed by awakenings and subjective reports of the sleep state (asleep, half-awake or awake)" (P20 §2.2). SR23 adds that "concurrent polysomnographic data (Fig. S1) identified the following changes as markers of sleep onset", and that the protocol "not only corresponded well with entry into N1 sleep but also effectively limited participants' entry into N2 sleep".
- **Adjustable in the app.** "Each signal is assigned an adjustable threshold for deltas in the app, such that Heart Rate delta (BPM) > X or Electrodermal Activity delta > Y or Muscle Flexion delta > Z" (PhD22 p.59).
- **Home-use alternative (HBOSS).** "For users at home with the iOS app and web app, we also developed a semi-supervised anomaly detection method ... Histogram Based Outlier Score (HBOS) ... Histograms are calculated for the three physiological signals over the calibration time, each histogram representing the distribution of the awake state. Then, 15-second time windows are continuously compared against the histograms, to generate a Histogram Based Outlier Sleep Score (HBOSS) ... HBOSS allows for differential weighting of each signal" (PhD22 p.59–60; SM19 p.65). No threshold is given. Pointer, not analysed here: `reference_repos/openSleep/deprecated/prediction_server/app.py:19` imports `pyod.models.hbos.HBOS`, line 105 sets `HBOS(contamination=0.05)`, and line 115 stores `'hboss_base' : np.min(pred)`.
- **Ground truth.** The only per-awakening label is the self-report ("And were you asleep?" → Awake / Halfway / Asleep). P20 §4.1 admits "This study ... did not have PSG measurements ... experimenters must trust verbal reports with regards to sleep onset, which can be unreliable".
- **What "N1" means in these papers.** SR23: "the term 'N1 sleep' throughout our study refers to the period defined as 1–5 min after Dormio-detected sleep-onset N1". FS26 Limitations: "the sleep onset dreams reported here likely occurred in N1 as well as early N2".
- **Tolerance argument.** "Dormio has a large margin of error for which sleep onset detection can be tolerated without sacrificing the hypnagogic dream incubation goal" (P20 §4.1). "There is evidence that sleep onset imagery occurs in a range of 15 seconds to 5 minutes post sleep onset" (PhD22 p.59; SM19 p.64).

### 2.3 Post-hoc threshold analysis (P20 §3.3, measured results, FACT)
- Method: deltas from session start ("the moment the a participant lay down") to the first self-report of 'Sleep'/'Halfway' versus 'Wake'. "we calculated the 80th percentile of 'Sleep' values, above which subjects would be likely to report being awake, and the 20th percentile for 'Awake' values, below which subjects would likely report being asleep."
- Table (P20 §3.3):

| Measure | Provisional sleep-onset threshold | 80th pct Sleep | 20th pct Wake |
|---|---|---|---|
| Heart rate | > 5 BPM | 6 BPM | −2 BPM |
| Flexion | > 8 KΩ | −4 KΩ | −6 KΩ |
| EDA ("Elcetrodermal activity" sic) | **> 5 μSemen** | 5 μSemen | −1 μSemen |

- Figure 4 caption, measured: "HR Mean Sleep: −2.89 ± 22.94. HR Mean Wake: 10.01 ± 18.7 ... Flex Mean Sleep: −6.74 ± 9.05. Flex Mean Wake: −3.42 ± 6.32 ... EDA Mean Sleep: −4.88 ± 13.44. EDA Mean Wake: 3.36 ± 8.64."
- "Future studies will look at the predictive power of system thresholds combining these presumptive values." So the authors themselves call the thresholds provisional.
- **INFERENCE.** The SDs (about 19–23 BPM for HR) are four times the 5 BPM threshold, and the Sleep and Wake distributions overlap heavily (visible in Figure 4). An HR delta alone separates sleep from wake poorly in this dataset. Finger-PPG artifacts may explain part of the spread; the paper does not say.

---

## 3. Timer-only / non-contact protocol ("Dormio Light")

### 3.1 What the thesis claims (FACT)
- PhD22 abstract (p.5): "This experiment employs and validates a non-contact version of TDI, in which dream incubation is effective simply via timed audio cues on a Dormio web interface without any wearable sleep staging device."
- PhD22 p.66: Experiment 2 "Moves beyond the wearable form factor and offers evidence that TDI can effectively incubate dreams without a sensor." n = 80.
- App modes (PhD22 p.63; SM19 p.69): "The app has two modes, signal based and timer based ... The timer based mode ... is for users who do not have the Dormio hardware, and bases interaction only on user estimates of sleep onset latency. A drop detection feature is included, so people can hold their phone and use the equivalent of the 'Steel Ball Technique' to determine sleep onset latency." The web app "also has two modes, signal and timer based" (PhD22 p.64).

### 3.2 Exact protocol (FS24 Methods > "Dormio Light website"; PhD22 Chapter 4 p.114–115), FACT
- Recruitment: Prolific, US, ≥ 90 % approval rate, ≥ 50 prior studies, Chrome browser. 132 enrolled, **80 analyzed** (FS24 Participants).
- Recorded cues, in the participant's own voice: "(1) an incubation message ('Remember to think of a Fork/Tree') intermittently played throughout the condition, and (2) a report message ('Tell me what is going through your mind') that played four times across the condition" (FS24). PhD22 p.115 gives the prompt as "'Think of a Fork/Tree'" (conflict C9).
- Initial latency: "a latency window of time to begin the condition ... after which the website would pick a random time within this window estimate. For instance, in the hypnagogia condition, a participant could enter that it typically takes him/her 10 to 15 min to fall asleep, and the Dormio Light website would present the incubation message after 13 min" (FS24).
- Fixed parameters: "to stay in hypnagogia/FMT for 3 min, record 4 rounds (i.e., trials) of entry into hypnagogia/FMT and report of thoughts, take 60 s to provide verbal reports as cued by their audio messages, and take 7 min to fall back into sleep/remain in a stable hypnagogia/FMT state following each awakening" (FS24).
- Rationale: "staying 3 min in each state provided adequate time for descriptive stories without a loss of memory ... which can occur if individuals enter N2 ... a short verbal report period allows for capture of cognitive content while mitigating the difficulty in falling back asleep which an increase in arousal during a longer period of awake report might create (Horner et al., 1997)" (FS24).
- Length: "cued four times ... throughout an hour-long period to report their thoughts verbally for 60 s, during which the Dormio Light website recorded audio automatically" (FS24 Procedure). The whole experiment, both conditions plus questionnaires, averaged 2.6 hours (FS24).
- Posture: "lie down with eyes closed and fall asleep" for hypnagogia; "sit upright with eyes closed and remain awake" for mind-wandering (PhD22 p.115).
- State check after each condition: "'To what degree did you fall asleep, if you did?' with options 'Fully asleep,' 'Half asleep,' and 'I did not fall asleep'" (FS24 Procedure). 52 participants were excluded for not staying awake in the mind-wandering condition or not sleeping in the hypnagogia condition.
- Outcomes: "73 (91%) reported at least one incorporation during hypnagogia", compared with "34 of 37 (92%)" in in-person studies, "[χ2 (1) = 0.01, p = 0.91]" (FS24 Results). Cued versus uncued: Tree session direct M = 3.09 (SD 2.19), Fork session direct M = 2.62 (SD 2.60); uncued items were not different from zero (FS24). There was no difference in incorporation count between hypnagogia and mind-wandering, "[F(1, 80) = 0.61, p = 0.44]" (FS24).
- Stated limitation: "the timer method is likely less sensitive to hypnagogic signals than the Dormio glove/muscle-flexion method or polysomnography" (FS24 Limitations). "There is no objective (non-self-report) measure of sleep stage possible with our remote Dormio Light methodology" (FS24).
- Safety: "no adverse events were reported during or after the study" (FS24 Procedure). The authors recommend screening for "any history of psychotic disorders ... or sleep disorders and related symptoms (e.g., narcolepsy, isolated hypnagogic/hypnopompic hallucinations, recurrent sleep paralysis, parasomnias)" (FS24 Limitations).
- Later endorsement: "TDI can be administered effectively using timers, without any physiological monitoring (Bellaiche et al., 2024)" (FS26 Discussion). "the smaller, cheaper Dormio, or perhaps even the hardware-free Dormio Web Interface would be an appropriate choice for clinical use of TDI" (PhD22 p.138).
- **Data caveat (FACT).** The FS24 outcomes come from typed free-text reports written after each condition, not from the audio: "unexpected issues (including technological and participant errors) precluded us from analyzing these verbal reports" (FS24 Procedure).

### 3.3 Code cross-check (CODE; the code may differ from the study build, see C10)
- Defaults at `js/script_timer.js:12-19`: `"loops" : 3`, `"hypna-latency" : 3` (minutes), `"time-until-sleep-min": 10`, `"time-until-sleep-max": 15`, `"time-between-sleep" : 7` (minutes), `"recording-time" : 60` (seconds). **FACT: the study used 4 rounds; the code default is 3.**
- UI choices offered in `timer.html`: loops 1–5 (lines 125-131), hypnagogia 1/2/3 min (lines 142-146), sleep latency min 5/10/15 and max 10/15/25 (lines 157-168), re-sleep 3/5/7 min (lines 179-183), recording 30/60/90 s (lines 194-198).
- Order of events (FACT, from the code):
  1. On Start, the prompt plays once (`:196`).
  2. Initial latency = `getRandomInt(min,max)` whole minutes (`:217`, inclusive integer at `:680-684`).
  3. At the end of the latency, `runHypnaLatency()` (`:296-308`) plays the prompt again (`:304`) and starts the 3-minute dwell.
  4. `startWakeup()` (`:313`) plays the wake message; recording starts when that message ends (`:336`) and stops after `recordingTime` (`:346`).
  5. In `endWakeup()`, if `wakeups < loops` (`:364`) the prompt plays (`:366`) and the 7-minute re-sleep timer runs (`:291-293`); then the dwell starts again, which replays the prompt.
  6. After the last loop a gong plays (`:376`) three times (`:429-435`), and `endSession` runs 4000 ms later (`:380`).
- **INFERENCE.** In the code the incubation prompt plays three times per loop: after the report, at the start of the next dwell, and once at session start. FS24 calls this "intermittently played".
- **INFERENCE.** The 60 s countdown-display delay at `:237-245` is cosmetic; the wake timer starts at t = 0 (`:247-249`).
- **INFERENCE, timeline with study parameters.** Latency L (10–15 min) + 3 min dwell + 60 s report, then 3 × (7 + 3 + 1) min, gives about L + 37 min, or roughly 47–52 min for 4 rounds. That fits "hour-long".

---

## 4. Timings master table

Type: **P** = protocol parameter (set by the experimenter or app); **M** = measured result; **S** = schematic or illustration.

| # | Quantity | Value | Exact quote | Source | Type |
|---|---|---|---|---|---|
| 1 | Baseline / calibration window | 120 s | "averaged over the first 120 seconds when the subject initially lies down" | P20 §2.2 | P |
| 1b | Baseline (app default) | 120 s, customizable | "a customizable calibration time, default of 120 seconds" | PhD22 p.59; SM19 p.65 | P |
| 1c | Baseline (figure) | 3:00 min | Figure 1 label "calibration period (3:00 min)" | P20 Fig. 1 image | S (conflicts with 1) |
| 2 | HR threshold | > 5 BPM change from baseline mean | "Heart Rate (BPM) deltas of > 5 BPM" | P20 §2.2; SR23 | P |
| 3 | EDA threshold | > 4 "μSemen" (raw ADC units) | "Electrodermal Activity sensor deltas > 4 μSemen" / "> 4 conductance units" | P20 §2.2; PhD22 p.59 | P |
| 3b | EDA provisional threshold | > 5 μSemen | Table row "Elcetrodermal activity > 5 μSemen" | P20 §3.3 | P / M (conflicts with 3) |
| 4 | Flex threshold | > 8 KΩ (raw ADC units) | "flexor muscle sensor deltas > 8 KΩ" / "> 8 resistance units" | P20 §2.2; PhD22 p.59 | P |
| 5 | Trigger logic | any one of the three | "Awakenings were performed whenever one of these thresholds was passed" | P20 §2.2 | P |
| 6 | Pilot n for thresholds | 5 | "pilot data from 5 participants" | P20 §2.2 | M |
| 7 | Sensor sampling | 100 Hz | "Each of the three Dormio sensors is sampled at 100hz" | P20 §2.2 | P |
| 8 | Onset → wake delay (lab) | variable 1:00–5:00 min | "wakeups from 1:00 to 5:00 minutes after Dormio-detected sleep onset" | P20 §2.4; SR23; SR23-SM §1.2 | P |
| 9 | Onset → wake delay (app depth) | 1 / 1:30 / 3 min | "shallow (1 minute), medium (1:30 minutes) or deep (3 minutes)" | PhD22 p.56; SM19 p.61 | P |
| 10 | Onset → wake delay (v1 pilot) | 3 min | "a 3-minute timer was triggered" | SM19 p.73 | P |
| 11 | Delay randomization | pseudo-random allowed | "can be customized to vary over the course of the session, and can do so pseudo-randomly" | PhD22 p.58 | P |
| 12 | Report length (lab) | unlimited; ends when the participant stops speaking | "Verbal reports were not subject to a time limit." / "Once participants finished speaking" | SR23; P20 §2.4 | P |
| 13 | Report length (Dormio Light) | 60 s fixed | "take 60 s to provide verbal reports" | FS24 | P |
| 14 | Report length (code options) | 30 / 60 / 90 s, default 60 | `"recording-time" : 60` | CODE `script_timer.js:18`; `timer.html:194-198` | P |
| 15 | Silence auto-stop | exists, no value given | "There is further a silence detection feature, so that recording does not continue when unnecessary" | PhD22 p.63 | P (value NOT FOUND) |
| 16 | Session length (Exp 1, 3) | 45 min | "This loop of events was repeated for 45 minutes" | P20 §2.4; PhD22 p.127 | P |
| 17 | Session length (Exp 4 / FS26 N1 part) | 30 min N1 TDI + 90 min | "repeated for a total time of 30 minutes ... after which participants are given an additional 90 min sleep opportunity" | PhD22 p.142–143; FS26 | P |
| 18 | Session length (Dormio Light) | about 1 h per condition | "an hour-long period" | FS24 | P |
| 19 | Number of loops (lab) | not fixed (time-boxed) | "repeated a number of times, set by the user" | P20 §2.1 | P |
| 20 | Number of loops (Dormio Light) | 4 | "record 4 rounds" | FS24 | P |
| 21 | Number of loops (v1 pilot) | 3 | "repeated three total times" | SM19 p.73 | P |
| 22 | Reports per sleep participant | SI 5.15; SN 5.75 | "SI group (n = 13) provided an average of 5.15 reports ... SN group (n = 12) ... 5.75" | SR23 Results | M |
| 23 | Reports per wake participant | WI 4.66; WN 4.00; 4–5 requested | "asked for 4–5 mentation reports" | SR23; P20 §2.3 | M / P |
| 24 | Total reports | 136 sleep (67 SI + 69 SN); 104 wake (56 + 48) | "collecting 136 dream reports, 67 from the Sleep+Tree condition and 69 from the Sleep No Incubation condition" | P20 §3.1 | M |
| 25 | First sleep-onset latency | 10.3 ± 6.4 min | "sleep onset averaged 10.3 ± 6.4 (S.D.) minutes" | P20 §3.3 | M |
| 26 | Interval between sleep-onset reports | 8.23 ± 2.32 min (range 6–12) | "an average of 8.23 min between successive sleep onset reports (SD = 2.32, range = 6–12 min)" | FS26 Results | M |
| 27 | Wake-control delay | 7 min + variable 1–5 min | "Once 7 minutes had passed, approximating sleep onset time, a variable timer was triggered from 1 to 5 minutes later" | P20 §2.4 | P |
| 28 | Dormio Light initial latency | user window (example 10–15 min), random pick | "the Dormio Light website would present the incubation message after 13 min" | FS24 | P |
| 29 | Dormio Light dwell in hypnagogia | 3 min | "to stay in hypnagogia/FMT for 3 min" | FS24 | P |
| 30 | Dormio Light re-sleep | 7 min | "take 7 min to fall back into sleep" | FS24 | P |
| 31 | v1 inter-prompt delay | 2 min | "a hard-coded 2-minute delay between prompts" | SM19 p.71 | P |
| 32 | HBOSS window | 15 s | "15-second time windows are continuously compared against the histograms" | PhD22 p.60 | P |
| 33 | EEG-scored awakenings (Exp 3) | after 2 × 30 s N1 epochs | "Epochs of 30s were used and awakenings were done after 2 subsequent epochs of N1 sleep were scored" | PhD22 p.126 | P |
| 34 | EEG-scored REM awakenings | after 2 contiguous 30 s REM epochs | "awakenings were done after two contiguous epochs of REM were scored" | PhD22 p.143; FS26 | P |
| 35 | Sleep-onset imagery window (cited literature) | 15 s – 5 min after onset | "sleep onset imagery occurs in a range of 15 seconds to 5 minutes post sleep onset" | PhD22 p.59 | M (literature) |
| 36 | N1 duration (background) | about 1–7 min | "Stage 1 ... typically lasts about 1-7 minutes" | SM19 p.20 | M (literature) |
| 37 | Nightcap intervals (Stickgold 2000, cited) | 15–180 s of sleep | "after intervals of 15 to 180 s of Nightcap-defined sleep" | P20 §1.2 | M (literature) |
| 38 | Post-awakening dlPFC blood flow (cited) | up to 20 min | "it can take up to 20 minutes to be fully re-established in dorsolateral prefrontal cortex" | P20 §1.4 | M (literature) |
| 39 | Nap time of day | 12:00–16:00 | "between the hours of 12:00 pm and 4:00pm" | P20 §2.3 | P |
| 40 | End-of-session alarm (code) | gong × 3, then end after 4 s | `gongs < 3`; `}, 4000);` | CODE `:376-380, :429-435` | P |
| 41 | EEG-scored N1 sessions, awakenings (FS26) | 2–6 per participant within 30 min | "awakened 2–6 times (mean = 2.28, SD = 1.25) during sleep onset" | FS26 Methods | M |

### 4.1 Numbers attributed to Dormio (CLAUDE.md §2–§5) that I could NOT find

I searched every source in §0 with grep for "RMSSD", "heart rate variability", "HRV", "7%", "15%", "93%", "180 s", "20 s", "10 s", accelerometer, actigraphy, silence, fade, dBFS and "1.5 SD". Results:

| CLAUDE.md value | Status | What the sources say instead |
|---|---|---|
| HR 15 s moving average below μ_HR × 0.93 (7 % drop), configurable 4–12 % | **NOT FOUND** | An absolute > 5 BPM delta from a 120 s mean (P20 §2.2). INFERENCE: 5 BPM is about 7 % of 70 BPM, which may be where "7 %" came from, but no source says so. 15 s windows appear only in HBOSS (PhD22 p.60) |
| IBI RMSSD +15 % above baseline | **NOT FOUND** | No HRV, IBI or RMSSD measure anywhere in Dormio. HRV appears only as a cited title: Penzel 2003, "Is Heart Rate Variability the Simple Solution to Diagnose Sleep Apnoea?" (P20 references) |
| EDA 30 s regression slope ≤ 0, \|ΔEDA\| < 1.5 σ | **NOT FOUND** | EDA delta > 4 (or 5) raw units from the baseline mean, direction "either up or down" (PhD22 p.59) |
| 60 s rolling SD of accelerometer magnitude (stillness gate) | **NOT FOUND** | Dormio had no accelerometer. It used a finger flex sensor on an actively held hand closure (P20 §2.2). FS26 used the ZMax accelerometer only to confirm REM ("absence of large bodily movements") |
| Hypnic jerk < 0.5 s spike bonus | **NOT FOUND** as a detector | Hypnic jerks are mentioned only in SM19 background (p.20) and one participant report (P20 Table 3, Subject 35) |
| 20 s hold (stillness + 1 signal) / 10 s hold (all 3) | **NOT FOUND** | "whenever one of these thresholds was passed"; no hold time (P20 §2.2) |
| 180 s baseline | **CONFLICTING SOURCE** | The text says 120 s (P20 §2.2; PhD22 p.59), but P20 Figure 1 shows "calibration period (3:00 min)" |
| 180 s countdown default | **PARTIALLY SOURCED** | Matches the app's "deep (3 minutes)" option (PhD22 p.56), the v1 pilot's "3-minute timer" (SM19 p.73) and Dormio Light's 3 min dwell (FS24). The main study used a variable 1–5 min (P20 §2.4) |
| 60–300 s delay range | **SOURCED** | = "1:00 to 5:00 minutes" (P20 §2.4) |
| Abort countdown on major movement | **NOT FOUND** | — |
| Sensitivity Low / "MIT Standard" / High | **NOT FOUND** | Thresholds were adjustable per signal (PhD22 p.59); no named presets |
| Recording auto-stop 4 s silence / 45 s max | **NOT FOUND** | Silence detection exists with no value given (PhD22 p.63). Lab: no time limit (SR23). Dormio Light: 60 s (FS24). Code: 30/60/90 s |
| 2,000 ms logarithmic fade-in | **NOT FOUND** | — |
| −75 dBFS keep-alive carrier | **NOT FOUND** | — |
| Nightstand speaker as "Default MIT Mode" | **NOT STATED** | Prompts came from "the computer" / "computer audio" (P20 §2.4; SR23-SM §1.2) and "computer speakers" (FS26, REM awakenings). INFERENCE: laptop speakers in the room; headphones are never mentioned |
| Phone 1 Hz telemetry etc. | n/a | Not covered by the papers |

---

## 5. Cue wording (verbatim)

### 5.1 Dormio-delivered prompts in the sleep conditions (P20 §2.4; SR23-SM §1.2; PhD22 p.107), FACT
Pre-recorded human voice. In Experiment 1 this was the experimenter's voice; PhD22 p.56 says "Experiments have used both the voice of the Experimenter for these recorded prompts (Experiment 1) and the voice of the subjects for these prompts (Experiment 2) without a significant change in efficacy".

| When | Prompt (exact) |
|---|---|
| On lying down (after calibration) | "Think of a tree" (P20 §2.4). P20 Figure 1 labels R1 as "Remember to think of a Tree" |
| End of the 1–5 min timer | "You're falling asleep" |
| immediately after | "Please tell me, what's going through your mind" (Figure 1 R2: "Please tell me, what's going through your mind?") |
| Recording | (none) |
| After the participant finishes speaking | "And were you asleep?" (answers: 'Awake', 'Halfway', 'Asleep') |
| Re-incubation | "Remember to think of a tree" |
| Permission to sleep | "You can fall back asleep now" |
| End | experimenter instructs the participant "to wake up fully" (no audio cue) |
| No-incubation control | "Remember to think of a Tree" replaced by "Remember to observe your thoughts" |
| Wake controls | "think of a Tree" → (7 min + 1–5 min) → "Please tell me what's going through your mind" → "Remember to think of a tree" (SR23 summary: "continue thinking about a tree") |

Generic app wording (PhD22 p.56): R1 "'Remember to think of X'", R2 "'Please tell me what is going through your mind'".
Dormio Light (FS24): "Remember to think of a Fork/Tree" and "Tell me what is going through your mind".
v1 pilot (SM19 p.71): "[Name(x)] you are falling asleep", "remember to think of [dream cue (a)]", "tell me, what are you thinking about", "can you tell me more?". Logged example (SM19 p.74): "Ok you can start falling asleep again".

### 5.2 Instructions read by the experimenter, Sleep Incubation group (SR23-SM §1.1 = PhD22 p.106), FACT
> "This experiment is investigating the relationship between mental rest and cognitive flexibility. Varied imagery, memories, words, or bodily feelings may come up throughout the experiment. The aim of this exercise is to observe them, stay with them, follow them lightly and see where they go. One thing not to worry about is questioning whether you are asleep. This period in between sleep and wake feels to some people like sleep, to others just like relaxation or mind wandering. All are completely fine, just watch your mind and relax. Sleep cannot be forced, just allowed. Head towards sleep, but don't worry at all where you are in it, just relax."
>
> "After you lie down, you will be asked to think of a theme. Relax, hold that theme in your mind. A few times, you will be told you are falling asleep and reminded of the dream theme. These prompts are not to wake you up fully, just to make sure you do not descend into deep sleep, and to keep you aware so that you can keep observing your mind. Just stay still when the prompts come. Again, we're interested in your thinking in this semi-lucid period. Whenever you are prompted to report, please just vocally report what was going through your mind, and report whether you think you were asleep, by either saying awake, halfway or asleep. Then relax and drift towards sleep again."

SR23-SM: "attempts at replication should closely mirror the instructions below". In the Sleep No-Incubation group the second paragraph says "observe your thoughts" in place of "think of a theme" (SR23-SM §1.1).

The variant used in Experiment 3 (PhD22 p.127) adds "Whenever you are prompted to report, please just vocally report what was going through your mind **before you were told you were falling asleep**" and "You will then be reminded to think about the dream theme, and you can relax and drift back towards sleep."

Why the wording matters (P20 §2.4): changing the question "from 'tell me if you had a dream' to 'tell me anything that was going through your mind just before you woke up'" raised NREM report rates "to between 23% and 74% (Rechtschaffen 1973)".

---

## 6. Results relevant to the design

### 6.1 Incubation rates (FACT)
- Hypothesis: ">50% of awakenings yielding dream reports that incorporate the auditory prime" (P20 §1.4).
- P20 Table 1:

| Condition | Reports | % with content | Reports with tree ref (%) | Tree refs (direct / indirect) |
|---|---|---|---|---|
| Sleep+Tree | 67 | 99 % | 45 (67 %) | 91 (77 / 14) |
| Sleep−Tree | 69 | 93 % | 2 (3 %) | 2 (1 / 1) |
| Wake+Tree | 56 | 98 % | 29 (52 %) | 71 (51 / 19) |
| Wake−Tree | 48 | 96 % | 0 (0 %) | 0 |

- Statistics: "Kruskal Wallis H test; H=29.69, p<.001". Sleep+Tree 67 ± 35 % vs Sleep−Tree 1 ± 5 % (U = 131.0, p = 0.001). Sleep+Tree vs Wake+Tree was not different: "U=57.0, p=0.129" (P20 Fig. 3 text).
- "All sleeping participants (25/25) were able to recall at least 1 hypnagogic dream" (P20 §3.1).
- SR23: SI participants averaged "70.3% of each participant's reports contained a direct reference". Incubation improved creativity: "Sleep Incubation group ... significantly outperformed each of the other three groups" (SR23 Discussion). More incorporation predicted higher creative performance (SR23 Results, regression).
- REM pilot (FS26): "All 11 participants successfully incubated the target theme at sleep onset"; 57 % of sleep-onset reports contained the theme (15 of 31). The **second sleep onset beat the first: "73% vs. 36%"** (FS26 Discussion).
- Dormio Light: 91 % of participants had at least one incorporation, comparable to the lab (see §3.2).
- EEG adds little (PhD22 p.138): "we see the same proportion of subjects report direct incubation of the theme 'Tree' in N1 dreams in Experiment 1, 3 and 4 ... This suggests the EEG data used in Experiment 3 and 4 in addition to data from the Dormio device for sleep staging added little to improve incorporation rates."

### 6.2 Timing effects
- **FACT.** No source analyses incorporation as a function of the 1–5 min delay; the variable delay was used "to allow participants to experience different depths of sleep" (P20 §2.4). "Dream reports increased in bizarreness and immersion with each awakening, but consensual rating of bizarreness was not performed" (P20 §3.2.1).
- **FACT.** "Higher incorporation rates in N1 than in REM" (PhD22 p.146). REM TDI produced lower AUT semantic distance than N1 TDI (p = .01) (PhD22 p.145).

### 6.3 Side effects, startle and tolerability (FACT)
- "participants have reported such serial awakenings at sleep onset as positive and relaxing (Hayashi, Motoyoshi, and Hori 2005; Horowitz et al. 2018)" (P20 §2.1).
- "One subject reported difficulty speaking while in hypnagogia, but uttered short phrases and elaborated on each after their final awakening" (P20 §3.1).
- Subject 35: "didn't experience any big hypnic jerk but I guess some little ones" (P20 Table 3).
- Volume guidance (qualitative only): the prompt must come at the "correct volume -- loud enough to rouse participants from their descent into deeper sleep, but not so loud as to shock them awake and make reentry into hypnagogia difficult" (SM19 p.71).
- v1 usability problems: "(1) unpleasant voice interface, (2) unnecessary use of a conversational robot, (3) a bulky, wired glove that limited mobility and required a fully closed fist for pressure sensing" (SM19 p.77).
- Remote study: "no adverse events were reported" and screening is advised (FS24; see §3.2).
- Demand effect: "the verbal prompt 'You are falling asleep' delivered prior to dream reports may have influenced participants' subsequent self-reports of their sleep state" (FS26 Limitations).
- Subjective time distortion: "I definitely didn't feel like I had been asleep for 45 minutes. I felt it was more likely around 10–15 minutes" (P20 Table 3, Subject 29).

### 6.4 Participants (FACT)
- P20 / SR23: 50 enrolled (mean age 26.71 ± 7.86, 24 female), 49 analyzed, all university-affiliated. P20 §4.1: "The age range of participants is limited, and all are university-affiliated, yielding a somewhat homogenous population."

---

## 7. Physiology notes (as cited by these sources)

| Claim | Exact wording | Where | Cited references |
|---|---|---|---|
| Muscle-tone loss marks onset and tracks imagery | flex sensor "monitors the slow opening of the hand, with its parallel loss of muscle tone ... temporally tied to the onset of hypnagogic imagery" | P20 §2.2 | Kelly, Strecker & Bianchi 2012; Prerau et al. 2014 (PLoS Comput Biol); Ogilvie 2001; Noreika et al. 2015 |
| HR drops, EDA shifts | "Drops in heart rate and shifts in electrodermal activity (EDA) coincide with this loss of muscle tone" | P20 §2.2 | Penzel 2003; Penzel & Kesper 2006; Herlan et al. 2019 (J Sleep Res, "Electrodermal Activity Patterns in Sleep Stages..."); Ogilvie 2001 (Sleep Med Rev 5(3):247–70); Carskadon & Dement 2011 |
| EDA direction is not fixed | "shifts either up or down in EDA" | PhD22 p.59 | same set |
| Hori stages | "Hori and colleagues have defined nine substages of sleep onset" | P20 §2.2 | Hori, Hayashi & Morikawa 1994; Tanaka, Hayashi & Hori 1999 |
| Passive behavioural measures | "unobtrusive sleep onset trackers that offer information that can supplement physiological measures" | P20 §2.1 | Ogilvie 2001; Casagrande et al. 1997; Prerau et al. 2014 |
| N1 physiology | "a decrease in muscle tone, shallow breathing, a lowered but regular heart rate and falling blood pressure. This period of sleep is where you experience the hypnic jerk" | SM19 p.20 | (none cited for this sentence) |
| Multi-signal onset | "Continuous changes in respiratory rate, transitory drop in oxygenated hemoglobin, heart rate decrease, lowered EMG and changes in skin potential activity line up with the disappearance of alpha in EEG, occurring within a narrow range of Hori stages H2 to H4" | SM19 p.28 | Colrain, Trinder & Fraser 1990; Pivik & Busby 1996 (HR); Hori 1982 (skin potential) |
| Measured Dormio deltas (finger PPG, wrist EDA, finger flex) | HR Sleep −2.89 ± 22.94 BPM vs Wake +10.01 ± 18.7; Flex −6.74 ± 9.05 vs −3.42 ± 6.32; EDA −4.88 ± 13.44 vs +3.36 ± 8.64 | P20 Fig. 4 caption | measured in this study |
| PSG validation sample | "Note the attenuation of alpha (~10Hz) rhythm activity during periods of N1 sleep" (O1 spectrogram) | SR23-SM Fig. S1; PhD22 p.105 | concurrent PSG pilot |

**FACT: no percentages.** None of these sources gives a percentage HR drop, an HRV/RMSSD change, or an EDA slope at sleep onset.

---

## 8. Implications for a wrist-based port (all INFERENCE unless a source is cited)

1. **Do not call any OpenTDI threshold "the MIT value."** MIT's thresholds were absolute deltas (5 BPM; 4–5 and 8 raw ADC units) from a 120 s mean, fixed from n = 5 pilots and called "provisional" (P20 §3.3). The EDA and flex numbers are hardware-specific ADC counts (PhD22 p.59), so they do not transfer to Samsung's `SKIN_CONDUCTANCE`. Mark every engine default as GUESSED except: the baseline length (120 s text, 180 s figure), the 1–5 min post-onset delay, the 1/1.5/3 min depth presets, and the self-report question.
2. **Trigger logic.** Dormio used OR with no hold time. Owner requirement D.11 (stillness AND an HR/IBI drop, EDA as bonus) is stricter than Dormio. That is defensible given Figure 4's overlap, but it is a deliberate deviation and should be documented as one. Dormio's EDA site was "the bottom of the wrist" (volar), while the watch sensor sits on the dorsal wrist; the paper does not address this.
3. **The flex sensor measures a behavioural task, not stillness.** Dormio asked users to actively hold the hand closed and detected when they *failed* at it (P20 §2.1). Accelerometer stillness does not reproduce that, because a relaxed awake person is also still. A wrist analogue of the task (for example holding the forearm raised or the fist clenched and detecting the wrist drop or orientation change, like the "Steel Ball Technique" drop detection in PhD22 p.63) would be closer to the validated signal. It is still untested on hardware.
4. **The post-onset delay is the validated knob.** Use a variable 60–300 s, uniform, re-drawn each loop, or the presets 60 / 90 / 180 s. A fixed 180 s default matches only the "deep" preset and the v1 pilot.
5. **Copy the cue sequence exactly**, including "And were you asleep?" with Awake / Halfway / Asleep (CLAUDE.md omits this step). That answer is the only ground truth MIT used. Logging it per awakening gives the labels for owner requirements D.13 (replay harness) and D.14 (confidence score).
6. **Report recording.** The lab used no time limit, ending when speech stopped; Dormio Light used 60 s fixed. OpenTDI's 4 s silence / 45 s cap is GUESSED. Suggested defaults: silence auto-stop with a 60 s cap (sourced from FS24) and a user option for unlimited.
7. **Session shape.** The validated session is 45 min time-boxed, which yields about 5 awakenings (SR23), at a 12:00–16:00 nap. Loop count is secondary to the time box in the lab protocol but primary in Dormio Light (4).
8. **TIMED_CUES mode (owner requirement E.15).** Implement Dormio Light defaults with sources: user latency window (min to max minutes, random whole minute; example 10–15), prompt at start, then 3 min dwell → wake prompt → 60 s report → prompt → 7 min re-sleep, × 4 rounds, end alarm. Expect lower sensitivity: "the timer method is likely less sensitive" (FS24), and 52 of 132 participants were excluded on self-report. Ask the post-session state question FS24 used.
9. **Audio.** The sources only say pre-recorded voice from "the computer" / "computer speakers", with volume set qualitatively (SM19 p.71). Own voice and experimenter voice were both acceptable (PhD22 p.56). The fade-in, the −75 dBFS carrier, Bluetooth routes and the max-volume cap are all OpenTDI design choices with no Dormio source, consistent with owner requirements F.18 and F.19.
10. **Baseline while lying down.** This matches Dormio ("when the subject initially lies down", P20 §2.2) and supports owner requirement D.12. MIT gives no basis for a "prior-day resting HR" baseline; it would be a new OpenTDI feature.
11. **No experimenter.** The lab ended sessions by experimenter instruction, and an experimenter was present throughout (P20 §2.3). A home port needs an automatic end-of-session alarm (the code uses a gong ×3), and should show FS24's screening advice (psychosis history, narcolepsy, sleep paralysis, parasomnias) in onboarding, next to the wellness / not-a-medical-device note (owner requirement G.23).

---

## 9. Conflicts and gaps (summary)

| ID | Conflict / gap | Sources |
|---|---|---|
| C1 | Calibration: text "first 120 seconds" vs Figure 1 "calibration period (3:00 min)" | P20 §2.2 vs P20 Fig. 1 |
| C2 | Flex sensor finger: "middle finger" vs "index finger" | P20 §2.2 vs PhD22 p.61, SM19 |
| C3 | EDA threshold: "> 4 μSemen" in Methods vs "> 5 μSemen" in the §3.3 table. Units are μS/KΩ in the paper but "raw analog-to-digital units" in the theses | P20 §2.2, §3.3; PhD22 p.59 |
| C4 | Figure 4 caption vs text: HR Sleep "−2.89" vs "−+2.9"; Flex Wake "−3.42" vs "+3.4"; EDA Sleep "−4.88" vs "−4.0"; EDA Wake "±8.6%" (unit typo). The caption says deltas run "between Dormio-defined sleep onset and first awakening", the text says from session onset. The red lines are "20th percentile of 'Sleep'" in the caption vs "20th percentile for 'Awake'" in the text | P20 §3.3, Fig. 4 |
| C5 | Excluded subject: a wake participant who "failed to obey experimenter instructions" vs a sleep participant who "was unable to sleep" | P20 §2.3, SM19 p.78 vs SR23, PhD22 p.85 |
| C6 | Sleep−Tree reports with tree: 2 (3 %) vs 1 (1.4 %) | P20 Table 1 vs PhD22 p.92, SR23 |
| C7 | TDI-group size: FS24 cites P20 as "11 out of 12 (92%)", while SR23 gives SI n = 13 | FS24 vs SR23 |
| C8 | Wake reports: "at least 3 mentation" vs "at least 1 report" | P20 §3.1 vs PhD22 p.92 |
| C9 | Dormio Light incubation cue: "Remember to think of a Fork/Tree" vs "Think of a Fork/Tree" | FS24 vs PhD22 p.115 |
| C10 | Dormio Light loops: 4 in the study vs code default 3. The code at HEAD (2023) may not be the 2021–22 study build | FS24 vs CODE `:13` |
| C11 | The thesis says the timer mode was "used in Experiment 3", but Experiment 3 used the Dormio glove plus ZMax; the web timer was Experiment 2. Experiment 3 also lists both a 1–5 min Dormio timer and EEG "2 subsequent epochs" rules | PhD22 p.63 vs p.126–127 |
| C12 | REM pilot: "Sleep opportunities totalled 90 minutes" vs "additional 90 min" after 30 min (120 total, as FS26 says). FS26 "2–6 times (mean = 2.28)" sleep-onset awakenings while 31 reports / 11 participants = 2.8 (INFERENCE). FS26 Fig. 1 says "2–3 times from REM", text "0–3" | PhD22 p.142–143; FS26 |
| C13 | Wake control used "7 minutes ... approximating sleep onset time", while measured mean first onset was 10.3 min. Not explained | P20 §2.4 vs §3.3 |
| G1 | The Appendix A docx could not be downloaded (PMC proof-of-work page). Wording taken from SR23-SM / PhD22 | — |
| G2 | DSpace handles returned HTTP 429; dream.media.mit.edu did not resolve. PDFs taken from the Media Lab DAM URLs above | — |
| G3 | No source gives audio volume (dB), device (headphones vs speakers) for Experiment 1, fade-in, silence-detection threshold, threshold direction, hold or debounce time, or delay-vs-incorporation analysis | all |
| G4 | Thresholds were derived from n = 5 pilots plus an unpublished PSG pilot (Fig. S1), and P20 has no PSG. Sleep staging rests on self-report | P20 §2.2, §4.1; SR23 |
