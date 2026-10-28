# MIT TDI / Dream Engineering lineage: what exists beyond the 2020 Dormio paper

Research note for OpenTDI, written 2026-10-27. It surveys the MIT Media Lab (Fluid Interfaces) Targeted
Dream Incubation (TDI) line, from the 2018 prototype to 2026, and the work outside MIT that copies it.
It then compares every version on one table.

**What this file does not repeat.** `docs/references/horowitz2020_dormio_paper.md` already covers the
2020 paper (P20), the SM and PhD theses (SM19, PhD22), the Sci Rep paper (SR23), the remote "Dormio Light"
study (FS24) and the REM pilot (FS26). It covers their protocol, thresholds, timings, cue wording and
headline results. `docs/references/opensleep_dormio_code_analysis.md` covers the openSleep and dormio code
line by line. Here I add only what those files lack, and I cite them by section where I rely on them.

**Labels.**
- **FACT**: stated in a source I read for this file. Each FACT gives the location in that source.
- **INFERENCE**: my own reasoning or arithmetic.
- **ANECDOTAL**: a claim in a README or hobby project, not a study.
- **ABSTRACT ONLY**: I could read only the abstract.

---

## 0. Key findings (read first)

1. **No peer-reviewed TDI study exists without MIT authors.** I checked the Europe PMC citation lists of
   P20 (21 citing papers) and SR23 (18) on 2026-10-27. Every TDI experiment in them has Haar Horowitz and
   Maes as authors. FS24 (Duke) and Youngren 2025 (Kansas / VA) are collaborations with MIT. MIT News
   (2020-07-21) said "Duke University, Boston College, Harvard University, the University of Rochester,
   and the University of Chicago" had begun Dormio studies. INFERENCE: apart from Duke, none of those has
   published one.
2. **Experiment 3 (nightmares / self-efficacy) is now published,** as Youngren, Haar Horowitz et al. 2025,
   *Sleep Advances* (§2.4). The published protocol differs from the thesis:
   - wake-ups came "immediately after two epochs of visually scored N1" on a ZMax EEG, not after a 1–5 min
     timer;
   - the session lasted 1 h, not 45 min;
   - 28 people enrolled, not 25;
   - the published paper reports **no nightmare effect** ("did not have any significant differences").
   - Its 40.6 % of awakenings with incorporation is the lowest of any TDI study.
3. **The first prototype (CHI EA 2018) was framed as detecting stage 2, not N1** (§2.1). It used:
   - a force-sensitive-resistor glove that sensed the closed fist releasing;
   - a fixed 3-minute timer;
   - a Jibo robot's text-to-speech voice;
   - a Muse EEG sleep-spindle detector, piloted only.

   Users complained about it: 2 of 6 said holding the fist made falling asleep harder, and 4 of 6 found
   the robot voice disruptive "at times".
4. **The MIT authors themselves name a failure mode: false onsets in the first minutes.** SM19: "These
   first minutes of lying down often involve movement, hand clenching and initial relaxation, which can
   trick the Dormio system into an early false detection of sleep onset ... safeguarded against by a
   human eye, which makes the system not entirely autonomous" (SM19 Limitations). The lab studies always
   had an experimenter in the room. A home app has none.
5. **Dream reports are very short.** Sleep+Incubation reports averaged **14.0 ± 9.9 words** (SR23
   Table 1); wake reports averaged 18.6–22.8 words. Reports were transcribed and scored by hand: a
   condition-blind rater counted "tree" words (P20 §3.1). That scoring rule is simple enough to automate
   with on-device speech-to-text (§7, recommendation 6).
6. **The effect sizes are large but come from small groups.**
   - INFERENCE: on SR23's Creativity Index, Sleep-Incubation vs Sleep-No-Incubation gives Cohen's
     d ≈ 1.42, and vs Wake-Incubation d ≈ 1.82 (n = 12–13 per group).
   - FACT: each extra direct "tree" reference per report added β = 0.260 to the Creativity Index
     (p < 0.001; SR23 Results, Table 4).
7. **The MIT code is unmaintained.** Make: magazine, updated 2025-09-24, says that "a critical bug
   prevents the record function from working in the Dormio local web app. Since the developers have
   confirmed they are no longer maintaining the GitHub repository, this issue will not be fixed."
   - The live timer site (`christinatchen.github.io/dormio/js/script_timer.js`) is byte-identical to the
     repo HEAD (md5 after CRLF normalisation, checked 2026-10-27).
   - `dream.media.mit.edu` returns NXDOMAIN on Google and Cloudflare DNS.
8. **The Fluid Interfaces group has since used a smartwatch for real-time sleep intervention.** Whitmore …
   Maes 2025/2026 (bioRxiv, ABSTRACT ONLY) ran "an automated on-watch sleep-staging model based on heart
   rate and motion" to deliver closed-loop audio and vibration in deep sleep at home (n = 93). It does not
   target N1, but it is the closest MIT precedent for OpenTDI's architecture.
9. **One independent open-source copy exists:** `idealemu/TDI` (Alexis Dumelié, 2023–24; no licence). It
   uses a single force/flex channel with a **hold rule**: trigger after a 3.5 % rise that stays at the new
   level for 10 s. It adds a 10 s grace period after the hand re-closes, a 90 s calibration, a fixed
   3 min delay and a fixed 60 s recording. Its README claims "a successful replication"; no data is
   published (ANECDOTAL).
10. **Edison's bottle drop (Lacaux et al. 2021) is the best physiological comparator for a wrist "limp
    hand" detector** (§2.9).
    - Measured against standard 30 s scoring, only 41 % of drops happened after N1.
    - Counting microsleeps, 78 % of drops came after about 1 min of accumulated microsleep, and frontal
      delta power rose for about 24 s before each drop.
    - The drop always woke the person, and only 15 % reached N2 before their first drop.
    - INFERENCE: a behavioural "release" detector fires at the right moment to *keep people in* N1. It
      does not mark the exact onset of N1.

---

## 1. Sources used for this file

| Key | Source | Access |
|---|---|---|
| CHI18 | Haar Horowitz A, Grover I, Reynolds-Cuéllar P, Breazeal C, Maes P (2018). "Dormio: Interfacing with Dreams." *CHI'18 Extended Abstracts* (alt.chi), Montréal. DOI 10.1145/3170427.3188403 | Full text, author PDF `https://dam-prod.media.mit.edu/x/2018/04/20/alt_chi_submitted_version.pdf` (8 pages) |
| C20 | Carr M, Haar A, Amores J, Lopes P, Bernal G, Vega T, Rosello O, Jain A, Maes P (2020). "Dream engineering: Simulating worlds through sensory stimulation." *Conscious Cogn* 83:102955. DOI 10.1016/j.concog.2020.102955, PMC7415562 | Full text (Europe PMC XML) |
| SM19, PhD22, SR23, P20, FS24, FS26 | as listed in `horowitz2020_dormio_paper.md` §0 | Full text. For this file I re-read SM19 Limitations, PhD22 Ch. 5 (Experiment 3), Ch. 7 (community) and Ch. 9 (conclusion), and SR23 Table 1–2 |
| Y25 | Youngren WA, Haar Horowitz A, West Staples V, Carr M, Stickgold R, Maes P (2025). "Targeted dream incubation and dream self-efficacy." *Sleep Advances* 6:zpaf013. DOI 10.1093/sleepadvances/zpaf013, PMC12022959 | Full text (Europe PMC XML) |
| MAKE22 | Vega T, Perry E, Haar A, Rosello O, Jain A (2022). "Dormio Dream Incubator." *Make:* Vol. 81. `https://makezine.com/projects/dormio-dream-incubator/` (published 2022-05-18, modified 2025-09-24 per page metadata) | Full web page |
| MLP-TDI | MIT Media Lab project page "Targeted Dream Incubation" (FAQ). `https://www.media.mit.edu/projects/targeted-dream-incubation/overview/` | Full page, fetched 2026-10-27. No date on the page |
| MLP-DOR | MIT Media Lab project page "Dormio: Interfacing with Dreams" (FAQ). `https://www.media.mit.edu/projects/sleep-creativity/overview/` | Full page, fetched 2026-10-27 |
| MLP-MASCA | MIT Media Lab project page "Masca" ("active from April 2018 to July 2021"). `https://www.media.mit.edu/projects/masca/overview/` | Full page |
| NEWS20 | MIT News, 2020-07-21, `https://news.mit.edu/2020/targeted-dream-incubation-dormio-mit-media-lab-0721` | Full page (press, not primary) |
| L21 | Lacaux C, Andrillon T, Bastoul C, Idir Y, Fonteix-Galet A, Arnulf I, Oudiette D (2021). "Sleep onset is a creative sweet spot." *Sci Adv* 7:eabj5866. DOI 10.1126/sciadv.abj5866, PMC8654287 | Full text |
| S24 | Salvesen L, Capriglia E, Dresler M, Bernardi G (2024). "Influencing dreams through sensory stimulation: A systematic review." *Sleep Med Rev* 74:101908. DOI 10.1016/j.smrv.2024.101908, PMC11009489 | Full text |
| G23 | Ghibellini R, Meier B (2023). "The hypnagogic state: A brief update." *J Sleep Res* 32:e13719. DOI 10.1111/jsr.13719, PMC10078162 | Full text (Dormio passages only) |
| E22 | Amores J, Dotan M, Maes P (2022). "Development and Study of Ezzence: A Modular Scent Wearable to Improve Wellbeing in Home Sleep Environments." *Front Psychol* 13:791768. DOI 10.3389/fpsyg.2022.791768, PMC8970317 | Abstract and skim |
| W25 | Whitmore NW, Chan SW, Dulski A, Podrug A, Hidalgo N, Tubbs EF, Obi N, Viswanath VK, Freedman MS, Nathan V, Maes P. "Slow Wave Entrainment Using a Smartwatch: A Randomized Crossover Study." bioRxiv 10.1101/2025.07.28.666762 (v1 2025-07-29, v2 2026-02-03) | **ABSTRACT ONLY** (bioRxiv rate-limited; no Europe PMC full text) |
| B23 | Bernal G, Chhibber M, Bhatnagar M, Jivani U, Hidalgo N, Levasseur A, Maes P (2023). "Fascia Ecosystem: A Step Forward in Sleep Engineering and Research." *IEEE EMBC 2023*. DOI 10.1109/embc40787.2023.10341064 | **ABSTRACT ONLY** |
| ED26 | Michalak A, Picard-Deland C, Bernardi G, Ameen M (2026). "Editorial: Understanding conscious experiences during sleep." *Front Sleep* 5:1985086. DOI 10.3389/frsle.2026.1985086, PMC13696182 | Full text |
| FEST26 | Cunningham TJ, Wamsley EJ, Payne JD (2026). "Festschrift in honor of Dr. Robert Stickgold." *Sleep Adv*. DOI 10.1093/sleepadvances/zpag041, PMC13173424 | Full text (TDI passages only) |
| Other, abstract only | Mallett R et al. 2024 *Trends Cogn Sci* DOI 10.1016/j.tics.2024.10.004; Konkoly KR et al. 2026 *Neurosci Conscious* DOI 10.1093/nc/niaf067; Lacaux C et al. 2024 *Trends Neurosci* DOI 10.1016/j.tins.2024.02.002; Decat N et al. 2026 *Cell Rep* DOI 10.1016/j.celrep.2026.117237; Sayk C et al. 2026 *J Sleep Res* DOI 10.1111/jsr.70443; Schaich A et al. 2026 medRxiv DOI 10.64898/2026.09.14.26362979; Chin S et al. 2025 *PLoS One* DOI 10.1371/journal.pone.0329711 | **ABSTRACT ONLY** |
| GH | GitHub repos `idealemu/TDI` (HEAD 2024-01-29), `jabituyaben/SteelBall` (2020-12-14), `KairosHive/hypnagogia` (2025-06-27), via the GitHub API on 2026-10-27 | Source files read: `TDI.py`, `recorder.py`, `makePrompts.py`, `Board/code.py`, `README.md`; `SteelBall.js`; head of `hori_predict.py` |

**Not obtained.** These sources could not be fetched:
- `dream.media.mit.edu` returned NXDOMAIN (Status 3) from both `dns.google` and `cloudflare-dns.com`. The
  Wayback Machine was offline; its availability API shows only a 2008 capture, so INFERENCE: probably an
  unrelated older site.
- The editorial "Towards engineering dreams" (Carr, Haar Horowitz, Amores, Maes 2020, *Conscious Cogn*
  85:103006) has no abstract in Europe PMC, and I did not read it.
- The ACM *Interactions* "Demo hour" item (DOI 10.1145/3279993) was not read.

---

## 2. New material, source by source

### 2.1 Dormio v1 at CHI 2018 (CHI18): the prototype paper
- **FACT, sensing.** "an Arduino-based glove ... With an embedded force sensitive resistor in the palm,
  users can comfortably touch the sensor during their wakeful state and send a signal of 'no-touch' back
  to the system upon sleep onset when their hand opens and force ceases" (CHI18, System Implementation >
  Muscle Control Detection).
- **FACT, it was framed as stage 2 detection.** "Given that people lose muscle control when entering stage
  2 sleep ... Dormio alerts users whenever they enter unconscious stage 2 sleep, so they do not go deeper
  into sleep stages 3, 4 or REM" (CHI18, same section). INFERENCE: later papers relabelled the same event
  as N1 onset (P20, SR23). The meaning of "sleep onset" shifted between versions.
- **FACT, timing.** "Once a loss of muscle control was picked up by the system, a 3-minute timer was
  triggered ... chosen to ensure full sleep onset transition, and because previous work with serial
  awakenings has shown hypnagogic imagery can continue into early stage 2 sleep" (CHI18, Method: Field
  Experiment). The prompts were "remember to think of [dream cue (a)]", then "tell me, what are you thinking
  about", then "can you tell me more?", "with a 2-minute delay between prompts" (CHI18, Multisensory
  Inception and Conversation). After the report, "the robot instructed them to hold the glove again". The
  loop ran "three total times".
- **FACT, EEG spindle detector (piloted, not used to trigger).** It used Muse channel TP10:
  - band-pass 9–12 Hz, RMS over "a short overlapping moving window of 100 milliseconds";
  - band-pass 0.5–40 Hz, RMS over a 1 s window;
  - a spindle is the ratio crossing a threshold for "at least another 1 second", discarding detections
    longer than 2 s.

  "we are able to algorithmically track EEG spindles in Subject 1 without false positives, we have not
  shown this detection algorithm generalizes across subjects" (CHI18, Sleep Detection Via EEG; Limitations).
- **FACT, participants.** 8 graduate students enrolled (mean age 23.8, SD 1.76); 2 men "were not tired
  enough to fall asleep" and were excluded; evening sessions from 5 to 9 pm. The control was 8 min of awake
  rest with three prompts (CHI18, Method).
- **FACT, outcomes.**
  - 6/6 "remembered and reported seeing the prompt word".
  - AUT was rated more creative after hypnagogia for 5/6.
  - Stories were longer by 158.8 s (SD 87.7).
  - Comfort was 4.5/5 (SD 0.5).
  - "2 subjects commented that keeping their hand closed for force sensing made falling asleep more
    difficult, and that they found the robotic voice had a jarring tone".
  - "4/6 subjects reported perceiving the voice of the robot as disruptive at times".
  - "it is fully working on a cell phone" (CHI18, Results; Limitations and Future Work).
- **FACT, how others summarise this paper.** "a specific word was presented immediately after sleep onset
  during a daytime nap. All six participants reported 'seeing' the prompted word" (S24 §3.1.1, citing
  CHI18).

### 2.2 SM19: lessons and failure modes not in the protocol tables
- **FACT, early false positives need a human.** See Key finding 4 (SM19 Discussion > Limitations, PDF
  p. ~151).
- **FACT, no PSG validation.** "the device has not been properly validated with polysomnography ... it
  would be beneficial to see that the Dormio system at least is able to keep participants in classically
  defined N1 sleep, beyond our pilot validation" (SM19 Limitations).
- **FACT, demand bias.** "we are concerned that participants were over-reporting to please investigators".
  The suggested test was to change the wording "from `Remember to dream of X' to simply `X' and see if
  incubation is reduced" (SM19 Limitations). INFERENCE: FS24 later used "Think of a Fork/Tree" in one
  document and "Remember to think of ..." in another (conflict C9 in the existing file), so this test was
  never cleanly run.
- **FACT, v1 → v2 changes.** The board went from a 68.6 × 53.3 mm Arduino Uno to a custom 30 × 15 mm board,
  "8x smaller", moved "on the wrist instead of the dorsal side of the hand", and became wireless. The
  complaints that drove this were "(1) unpleasant voice interface, (2) unnecessary use of a conversational
  robot, (3) a bulky, wired glove that limited mobility and required a fully closed fist" (SM19, Thesis
  Experiment > System > Device Updates from Dormio V1, PDF p. ~76–77).
- **FACT, the author's own conclusion.** "It seems to work better if you remind them multiple times while
  they're half asleep, instead of reminding them during wake and giving no reminders in half-sleep"
  (SM19 Conclusion, PDF p. ~155).

### 2.3 PhD22: content not already extracted
- **Analysis platform (FACT).** "a custom data analysis platform ... It will parse and plot these, along
  with markers for wakeup and report times, so users can see biosignal changes and corresponding changes
  in phenomenology. The next time they sleep with their device or timer, they can adjust their incubation
  protocol according to this data" (PhD22 p.64). INFERENCE: MIT intended users to retune their own
  thresholds from their session plots. That is the same loop as OpenTDI's replay harness.
- **Experiment 3 in the thesis (FACT, PhD22 Ch. 5, p.123–138).**
  - Collaborators: Kathleen Esfahany, Westley Youngren (University of Kansas / VA Finger Lakes), Michelle
    Carr, Pattie Maes, Robert Stickgold.
  - Participants: 25 enrolled, mean age 31.55; "11 of 25 participants qualified for diagnosis of a moderate
    nightmare disorder".
  - Hardware: Dormio glove **plus** Hypnodyne ZMax (F7, F8 referenced to Fpz, 256 Hz, 0.1–128 Hz).
    "Epochs of 30s were used and awakenings were done after 2 subsequent epochs of N1 sleep were scored"
    (p.126). The Condition 1 text on p.127 still describes the 1–5 min Dormio timer (conflict C11 in the
    existing file).
  - Thesis results:
    - 92 % had ≥ 1 direct "Tree" incorporation; 42 % in the following week's journal.
    - The MHLOC composite rose by 0.52 (p = 0.027) after TDI and stayed 0.50 above baseline at one week
      (p = 0.042).
    - The DSE item "To what extent do you feel able to control the content of your dreams?" rose 1.33
      (p = 0.03) and then fell back by 0.96 at one week (p = 0.03).
    - Nightmare frequency predicted a decrease on the Nightmare Distress Questionnaire (β = −0.656,
      p = 0.002, R² = 0.329); incubation success did not (p = 0.408).
    - **Pre-session belief in TDI predicted incubation success** (β = 0.39, p = 0.0005, R² = 0.15),
      p.135–136.
  - Author's conclusion: "education regarding TDI could form a crucial part of the TDI protocol in future
    work" (p.137).
- **Clinical form factor (FACT).** "we see the same proportion of subjects report direct incubation of the
  theme `Tree' in N1 dreams in Experiment 1, 3 and 4 ... the EEG data ... added little to improve
  incorporation rates. Thus the smaller, cheaper Dormio, or perhaps even the hardware-free Dormio Web
  Interface would be an appropriate choice for clinical use of TDI" (p.138).
- **Openness and demand (FACT).** "Many researchers at the Dream Engineering seminar series asked for a
  Dormio device of their own ... we have made our circuit board design Open Source, as well as the software
  enabling the Dormio Web App and iOS App, and published an in-depth tutorial in Make Magazine" (p.152).
  There were seven Dream Engineering gatherings, with people from "more than 20 countries" (p.151). There
  was a course, MAS.S63 "Engineering Sleep and Dreams" (p.152).
- **Conclusion (FACT).** "We demonstrate the efficacy of our TDI technique at varied levels of complexity
  and cost (with Dormio, with EEG and without devices) and offer our software and hardware open source, free
  of charge" (p.165). INFERENCE: "open source" here means published. The GitHub repos carry no licence
  (opensleep_dormio_code_analysis.md §7).

### 2.4 Youngren et al. 2025 (Y25): Experiment 3 as published
- **Protocol (FACT, Y25 Methods > Protocol and TDI methods).**
  - Participants: "We enrolled N = 28 participants" (mean age 31.55, SD 16.37). Inclusion was ≥ 18 years;
    exclusion was a current psychotic episode or current sleep medication (the source's wording is garbled:
    "exclusion: not experiencing a current psychotic episode ...").
  - Timing: afternoon, 12:00–16:00. Wear: "a sleep mask, the Dormio headworn system [sic], and the Hypnodyne
    ZMax EEG".
  - "Subjects were given a 1-h sleep opportunity. Once sleep onset occurred, participants were awakened
    after 2 epochs of visually scored N1 sleep."
  - Cue sequence: "think of a tree" → (2 × 30 s N1 epochs) → "You're falling asleep" → "Please tell me,
    what's going through your mind" → record → "And were you asleep?" → "Remember to think of a tree" →
    "You can fall back asleep now", "repeated for a total experiment time of 1 h".
- **Staging validity (FACT, Y25 Measures).** For ZMax vs PSG it cites "sensitivity (68.3 per cent) and
  inter-rater reliability (Cohen's Kappa = 57.3 ...)".
- **Scoring (FACT).** A direct reference is "an unambiguous mention of any part of a tree (including tree,
  forest, branch, or root)". Indirect references are "plant, bush, or other tree-like objects".
- **Results (FACT, Y25 Results).**
  - 3 people were excluded "for failure to fall asleep".
  - 23/25 (92 %) had ≥ 1 direct incorporation. "On average, participants reported 1.96 incorporations in
    4.83 N1 episodes during the 1-h nap; 40.6 per cent of awakenings were accompanied by direct
    incorporations."
  - DSE composite rose by 1.02 (p = .003) after TDI. The one-week vs baseline difference was 0.50
    (p = .042).
  - "Subjects reporting a nightmare frequency of ≥1 nightmare/week at recruitment (n = 11) did not have any
    significant differences across pre, post, and 1-week post-TDI analyses".
- **Stated limitations (FACT).** No control group; small, single-site sample; one-week follow-up; no
  blinding. Also "sleeper sometimes perceive N1 sleep as being awake" (Y25 Limitations).
- **Thesis vs paper (INFERENCE from the two texts).**
  - The published paper drops the NDQ nightmare-complaint regression and the credibility regression that
    PhD22 reported.
  - It changes the primary scale from MHLOC to DSE.
  - It describes EEG-triggered immediate wake-ups, not the 1–5 min timer.
  - Treat the PhD22 nightmare claims as superseded.

### 2.5 SR23: effect sizes and what was automated
- **Automated (FACT).** "The Dormio system was used to automatically track participants' sleep onset,
  communicate auditory cues to participants, and record participants' verbal reports" (SR23 Methods).
  "It automatically collects verbal dream reports before they are lost to amnesia and avoids the more
  complete awakening that a written report would require" (SR23 Discussion).
- **Manual (FACT).**
  - Instructions were read aloud by an experimenter (SR23-SM §1.1).
  - "Throughout the 45-min experimental period, experimenters remained in the room".
  - A written post-experiment report was collected (SR23 Methods).
  - Creativity tasks were scored by three condition-blind raters (CST, AUT) and 39 Amazon MTurk "Master
    Workers" (VGT). Semantic distance was computed with GloVe (SR23 Methods).
  - Dream reports were "transcribed into typed text, and were not edited prior to scoring"; "One
    condition-blind rater counted" references (P20 §3.1).
- **Group sizes and report metrics (FACT, SR23 Table 1).**

| | SI | SN | WI | WN |
|---|---|---|---|---|
| N | 13 | 12 | 12 | 12 |
| Reports per participant | 5.15 ± 1.41 | 5.75 ± 2.13 | 4.66 ± 0.62 | 4.00 ± 0.37 |
| % reports with "tree" | 70.29 ± 33.66 | 1.39 ± 4.61 | 52.36 ± 34.92 | 0 |
| Report length (words) | **14.00 ± 9.86** | 8.71 ± 4.17 | 22.78 ± 23.67 | 18.58 ± 7.75 |
| Creativity Index (z) | 0.79 ± 0.50 | −0.07 ± 0.70 | −0.32 ± 0.71 | −0.47 ± 0.77 |

- **Statistics (FACT, SR23 Table 2).** On the Creativity Index (Aligned Rank Transform ANOVA): state
  F = 16.39, p = 0.0002; condition F = 6.33, p = 0.015; interaction F = 4.08, p = 0.049. Post hoc (Holm):
  SI–SN t = 3.23, p = 0.009; SI–WI t = 4.17, p = 0.0007.
- **Effect sizes (INFERENCE, pooled-SD Cohen's d from Table 1).** SI vs SN d ≈ 1.42; SI vs WI d ≈ 1.82.
  For % reports with tree, SI vs WI d ≈ 0.52. Small groups make these estimates imprecise.
- **Dose-response (FACT).** The regression coefficient on the degree of theme incorporation (direct "tree"
  references per report) was β = 0.260, p < 0.001, controlling for semantic distance, state and condition
  (SR23 Results).
- **Scope caveat (FACT).** "the term 'N1 sleep' throughout our study refers to the period defined as 1–5
  min after Dormio-detected sleep-onset" (SR23 Methods).

### 2.6 Carr et al. 2020, "Dream engineering" (C20)
- **Dormio as described in 2020 (FACT, C20 §2.6.1).** "a silence detection feature stops the recording
  once the report is finished". The heart rate sensor is on the middle finger, flex on the index finger,
  and EDA "between two electrodes placed on the bottom of the wrist".
- **Masca (FACT, C20 §2.7.1).** Masca modernises Stickgold's Nightcap with "piezoresistive fabric sensors
  to detect eye movement ... placed in an eye mask". MLP-MASCA says it was "active from April 2018 to July
  2021" and is a prototype "only tested ... on a few people so far". MLP-TDI says "we have also used the
  Masca, the Hypnodyne, and even typical polysomnography to enact TDI".
- **Fascia (FACT, C20 §2.7.2).** A sleep mask that "simultaneously collects EEG, EOG, EMG, heart rate, head
  movement and skin temperature". B23 (ABSTRACT ONLY) describes the 2023 "Fascia Ecosystem" (mask, portal,
  hub) for "home-based sleep studies with real-time intervention".
- **Real-time EEG staging (FACT, C20 §2.7.3).** "a Deep Convolutional Neural Network that automatically
  computes sleep stages in real-time from single channel EEG ... overall accuracy of 83.5% for 5-stage
  classification ... on 4 test nights ... (Koushik et al., 2019)". The app scores 30 s epochs on the
  phone and is "open-sourced". INFERENCE: this is the group's route to EEG-gated TDI, which OpenTDI cannot
  use without an EEG headband.
- **Essence / Ezzence olfaction (FACT, C20 §2.5.2; E22 abstract).**
  - Essence released scent on physiological rules, for example "if HR greater than 80, release pleasant
    scent".
  - A PTSD pilot used one participant (C20).
  - Ezzence (2022) is "the first smartphone-controlled olfactometer". In a home study (N = 40) with scent,
    sleep quality improved (p = 0.003), as did morning mood (p = 0.038) and time to sleep onset
    (p = 0.008), versus control.
  - It is integrated with "a wearable EEG and real-time sleep staging algorithm to release scent during
    specific sleep stages".
- **Ethics (FACT, C20 §3.2).** The authors call the sleeper "particularly vulnerable": "individuals by
  design are unaware of and have no control over the application of dream engineering as it is occurring".
  They "suggest that a minimalist approach to dream engineering using simple sense stimulation techniques
  ... carries promise".

### 2.7 MIT web pages and Make: magazine
- **Make: magazine, Dormio V3 (FACT, MAKE22).**
  - The hardware is "a hand-worn device that tracks changes in three biosignals" built on an nRF52832.
    Signals are broadcast "via Bluetooth to a web interface which detects sleep onset". You open
    `serverless/wearable.html` locally in Chrome.
  - Wear: "the heart sensor stays on your fingertip, the electrodes stay on the inside of your wrist, and
    the flex sensor stays attached to your finger. An earlier version of the Dormio used a glove, but then
    people complained their hand was too hot while they slept. A newer version uses simple velcro straps".
  - Data: "your data and audio are downloaded to a local .zip file, not uploaded to any server".
  - Advice: "You may have to tweak things to get this whole hypnagogic incubation practice right — from
    your bedtime to the volume of your audio — because this is not an exact science".
  - The UI plays "the dream incubation audio when you slip into the beginning of hypnagogia" and "the dream
    report audio when you near the end of hypnagogia". INFERENCE: this matches the code's two-stage
    hypna-latency wait (opensleep_dormio_code_analysis.md §0 item 5). The doubling may be by design rather
    than a bug.
  - Status: the note on the unmaintained repo and broken recording is quoted in Key finding 7.
- **MLP-DOR (FACT).**
  - It claims "detecting sleep onset based on electrodermal activity, **heart-rate variability**, and muscle
    tension". INFERENCE: no paper and no code uses HRV; the code uses BPM deltas
    (opensleep_dormio_code_analysis.md §10). Treat this as loose wording, not a source for an RMSSD rule.
  - "designed to prompt users for a dream report such that they are unlikely to slip into stage 2 sleep,
    while also avoiding startling users into full wakefulness".
  - Ethics: "we have had people wake themselves up when they had a weird enough dream that they did not
    want us experimenters to hear about it".
  - On REM lucid dreaming: "it often requires waking you up in the middle of the night ... It also involves
    full atonia unlike hypnagogia, so people would have much more difficulty describing their dreams".
- **MLP-TDI (FACT).** "a timer of a few minutes is started"; "we have mainly explored using auditory and
  olfactory stimuli". At the time of writing, the REM and nightmare studies were "not yet published", and
  "TDI is being used in an experiment on PTSD related nightmares at University of Kansas and in an
  experiment on mind-wandering and dreams at Duke".

### 2.8 Fluid Interfaces follow-ups, 2022–2026 (not TDI, but the same toolchain)
- **Smartwatch closed loop (W25, ABSTRACT ONLY).**
  - Design: randomised crossover at home, two nights, "a consumer smartwatch for stimulation delivery and a
    portable EEG headband for neural recording". Stimulation was "auditory, vibrotactile, or combined",
    "guided by an automated on-watch sleep-staging model based on heart rate and motion".
  - Results: stimulation raised frontal delta power, and "Stimulation blocks exhibited lower disruption
    rates". There was no group-level difference in next-day measures. "68/93 participants were stimulated
    overnight."
  - Not established: the watch model, the staging accuracy and the latency are not in the abstract.
- **EEG sonification (ABSTRACT ONLY).** Chin, Whitmore, Perry, Paradiso, Maes 2025 (*PLoS One*, n = 40
  online): sonified EEG did not help overall, but improved accuracy for the least experienced stagers.
- **Nightmares and PTSD with an MIT alumna (ABSTRACT ONLY).** These are not TDI. Both use targeted memory
  reactivation (TMR) with scent, with Judith Amores as co-author.
  - Sayk et al. 2026 (*J Sleep Res*, n = 25, frequent nightmares): odour-cued relaxation reactivation
    changed neither cortical hyperarousal nor nightmare symptoms.
  - Schaich et al. 2026 (medRxiv): odour cueing during 12 weeks of trauma-focused therapy "improved
    clinician-rated and self-reported PTSD symptoms".
  - INFERENCE: the clinical dream-engineering evidence after 2024 sits in TMR, not in sleep-onset TDI.

### 2.9 Work outside MIT, 2021–2026
- **Lacaux et al. 2021, Edison's bottle (FACT, L21 Results; Methods).**
  - Setup: N = 103 (87 analysed); a 20-min break, semi-reclined, holding a 55 g, 14.5 cm plastic
    bottle. The object had to make "a noise when falling", be "light weight to avoid cramped arms", be
    "slippery", and be "large enough so that the fist cannot close on it".
  - Insight: "at least 15 s in N1" gave 83 % insight vs 30.61 % for wake and 14.29 % for N2.
  - Drop timing: "Of the 63 drops, 26 (41.27%) occurred after N1 ... this proportion rises to 77.78%
    (N = 49 of 63), with the bottle dropping after approximately 1 min of accumulated MSEs". "A clear power
    increase in the delta band 24 s before the drops."
  - "the bottle always woke the subjects up (100%)"; "only 5 subjects of the 33 (15%) who fell asleep and
    dropped the bottle showed evidence of N2 before the first bottle drop".
  - Hypnagogia was reported after 61.7 % of drops, vs 22.5 % for people who never dropped it.
  - The authors cite Dormio as a device that could "act as a creativity booster".
- **Reviews.**
  - G23: Dormio "successfully induced the target stimulus ... However, as admitted by the authors, it
    remains questionable whether reports from subjects can be trusted due to influence from demand
    characteristics".
  - S24: across 51 stimulation studies, "gradually increasing the stimulus volume until awakening led to
    more thought-like reports than abrupt awakenings" (Shapiro, Goodenough et al. 1963–65, REM/NREM night
    awakenings). Also: "The impact of [stimulation-to-awakening interval] is likely to be significant but
    has never been systematically addressed".
  - ED26: "progress in dream engineering will require improving success rates, determining the extent and
    limits of control over dream content, and more directly assessing its possible applications and
    potential side effects".
- **Related, ABSTRACT ONLY.**
  - Konkoly et al. 2026: REM sound cues linked to unsolved puzzles "reliably increased dreaming about the
    associated puzzles" in frequent lucid dreamers, and boosted solving post hoc. This is the REM analogue
    of TDI for creativity.
  - Decat et al. 2026 (n = 92, EEG): "all these mental states emerge across wakefulness, N1 sleep, and N2
    sleep". INFERENCE: this supports P20's finding that Wake+Tree also incorporates (52 %). A detector that
    says "N1" is not needed to get dream-like content, but sleep still beat wake on creativity in SR23.

### 2.10 Community implementations on GitHub
- **`idealemu/TDI` (FACT from code; claims ANECDOTAL).**
  - README: "A successful replication of Dormio TDI (Horowitz et al. 2018), software, hardware and user
    study". Author Alexis Dumelié (`TDI.py:3`). No licence (API `license: null`). Last push 2024-01-29.
    English and French prompts (`makePrompts.py`).
  - Sensor: one FSR/flex voltage on an Adafruit ItsyBitsy over serial at 9600 baud (`Board/code.py`), with
    a OneEuro filter at 100 Hz (`TDI.py:46-52`).
  - Calibration is 90 s (`TDI.py:33`).
  - Trigger (`TDI.py:255-275`): the 1 s mean exceeds baseline × (1 + 0.035), where 0.035 =
    "sensor_repeatability 0.02 + state_change_range 0.015". Then the signal must "stabilize" at the new
    level for `NEW_STABLE_STATE_TIME_WINDOW = 10` s (`TDI.py:37, 267-269`). Detection is direction-specific
    (rise only).
  - A grace period of `GRACE_WINDOW = 10` s follows each loop, "After closing hand again" (`TDI.py:41,
    251-253`).
  - Protocol (`recorder.py:22-57`): `time.sleep(60*3)`, then "[Name] You are falling asleep", "Please tell
    me what is going on through your mind ?", a 60 s recording, "Remember to think of [PROMPT]", and "You
    can fall back asleep now, hold on to glove again". `MAX_CYCLE = 2` gives three cycles (0–2), then "You
    can wake up fully".
  - INFERENCE: this independent build added the hold time and the post-loop refractory period that MIT's
    code lacks.
- **`jabituyaben/SteelBall` (Bangle.js watch, ANECDOTAL).**
  - Wrist PPG: every 5 valid readings it computes the mean and SD, discarding BPM < 59 or > 100. It
    vibrates when "average_HR < target_heartrate && stdev_HR < 3", where the target is set by hand
    (`SteelBall.js:69-90`). It stops after 2 triggers.
  - README: "a slight delay to the alarm before it is triggered ... seems to work better for me".
  - INFERENCE: this is the only wrist-only hypnagogia alarm I found. It is a single HR gate with a
    stability (SD) check.
- **`KairosHive/hypnagogia`.** A Muse EEG rule-based Hori stage 4/5 detector (`hori_predict.py` header:
  5 s windows, θ/α ratio, accelerometer RMS gate of 0.01 g, 10-epoch hysteresis). EEG only, so out of
  OpenTDI's sensor set; noted for completeness.

---

## 3. Comparison table: every TDI version

Sources are cited inside each cell; P20, SR23, SM19, PhD22, FS24 and FS26 values come from
`horowitz2020_dormio_paper.md` unless marked otherwise.

### 3.1 MIT studies

| Study / version | Year | Sensors | Onset rule | Baseline | Onset→wake | Loops / session | Recording | Cue wording | N | Outcome |
|---|---|---|---|---|---|---|---|---|---|---|
| **Dormio v1** (CHI18 = SM19 pilot) | 2017–18 | FSR in palm of wired Arduino glove; Muse EEG recorded only | Loss of fist pressure ("no-touch") | None described | **3 min fixed** | 3 loops | Audio recorded; length not stated | Jibo TTS: "[Name(x)] you are falling asleep", "remember to think of [a rabbit / a fork]", "tell me, what are you thinking about", "can you tell me more?" (2 min between prompts) | 8 enrolled, **6** analysed | 6/6 saw the prompt word; AUT better in 5/6; stories +158.8 s |
| **Dormio v2 glove**, Exp 1 (P20, SR23) | 2018–19 | Finger PPG, index-finger flex, volar-wrist EDA, 100 Hz, nRF51 BLE | OR, no hold: \|ΔHR\| > 5 BPM, ΔEDA > 4, Δflex > 8 (raw ADC units) | 120 s mean (P20 Fig. 1 says 3:00) | **Uniform 1–5 min** | Time-boxed **45 min** (SI mean 5.15 reports) | No time limit; ends when the speaker stops | "Think of a tree" → "You're falling asleep" / "Please tell me, what's going through your mind" → "And were you asleep?" → "Remember to think of a tree" / "You can fall back asleep now" | 50 enrolled, **49** (SI 13, SN 12, WI 12, WN 12) | 67 % of SI reports had "tree" (P20); Creativity Index SI > SN, p = 0.009, d ≈ 1.42 (INFERENCE) |
| **Dormio Light**, Exp 2 (FS24) | run before 2022, published 2024 | **None** (Chrome timer website; own-voice cues) | Timer: random whole minute in a user window (e.g. 10–15) | n/a | **3 min "hypnagogia" dwell** | **4 rounds**, about 1 h, 7 min re-sleep | **60 s fixed** | "Remember to think of a Fork/Tree" (PhD22: "Think of ...") / "Tell me what is going through your mind" | 132 enrolled, **80** analysed | 91 % had ≥ 1 incorporation (typed reports, not audio) |
| **Exp 3 thesis → Y25** | run before 2022; published 2025 | Dormio glove + **ZMax EEG** (F7, F8–Fpz, 256 Hz) | **Visual EEG: 2 consecutive 30 s N1 epochs** (Y25); thesis also says 1–5 min Dormio timer | n/a | "Immediately" after 2 N1 epochs (≈ 60 s of N1, INFERENCE) | **1 h** (Y25; thesis: 45 min); 4.83 N1 episodes on average | Until the speaker finishes (INFERENCE from "Once subjects finished speaking") | As Exp 1 | 28 enrolled, **25** (Y25); thesis says 25 enrolled | 92 % ≥ 1 direct; **40.6 % of awakenings**; DSE +1.02, p = .003; no nightmare-subgroup effect |
| **Exp 4 / REM pilot** (FS26) | run before 2022; published 2026 | Dormio (N1 part) + ZMax; REM awakenings triggered by the experimenter | N1: Dormio muscle/HR/EDA rule; REM: 2 contiguous 30 s REM epochs | as Dormio | N1: **1–5 min**; REM: at detection | 30 min N1 TDI (2–6 awakenings) + 90 min (REM 0–3; conflict C12 in the existing file) | Until the speaker finishes | As Exp 1; REM: "Please tell me, what's going through your mind?" via computer speakers | 11 analysed (5 excluded, 1 for a **Bluetooth failure**) | 11/11 incubated at N1; 57 % of N1 reports; REM 10/21 reports (50 %) |

### 3.2 Released code, community builds and comparators

| Version | Year | Sensors | Onset rule | Baseline | Onset→wake | Loops | Recording | Cues | N | Outcome / status |
|---|---|---|---|---|---|---|---|---|---|---|
| **Web "wearable"** (openSleep/dormio `script_wearable.js`; Make V3) | 2020–23 | Fingertip PPG, finger flex, inner-wrist EDA, nRF52832 | OR, no hold: \|ΔEDA\| ≥ 4, \|Δflex\| ≥ 5, \|ΔHR\| ≥ 6 BPM, using **abs** (both directions); optional latency window that forces onset at max | **3 min** (options 3/5/7) | hypna-latency 3 min applied **twice = 6 min** | 3; after the first onset, wake-ups are **timer-only** (7 min + 3 min) | **30 s fixed** | User-recorded prompt and wake message | — | MAKE22 (2025): recording broken, repo unmaintained |
| **Web "timer"** (`script_timer.js`; live site = repo HEAD) | 2020–23 | None | Random integer minutes in [10, 15] | — | 3 min | **3** (the study used 4) | 60 s | User-recorded | — | Live at `christinatchen.github.io/dormio/timer.html` |
| **iOS app "DreamCatcher"** (deprecated) | 2017–19 | Dormio glove via BLE; phone drop detection | Deltas HR 15 BPM, EDA 10 (iOS defaults) | 300 s | Experimental 15 s; guided 30/60/90 s | 1–5 | **Silence stop: ≥ 30 s, ≤ 120 s, 8 s quiet** | "You're falling asleep... Tell me what you're dreaming about" | — | Code only (opensleep_dormio_code_analysis.md §0, §10) |
| **`idealemu/TDI`** (independent) | 2023–24 | One FSR/flex channel | **Rise > 3.5 % above baseline, then a stable new level for 10 s**; 10 s grace after each loop | 90 s | **3 min fixed** | 3 | **60 s fixed** | "[Name], You are falling asleep", "Please tell me what is going on through your mind ?", "Remember to think of Trees", "You can fall back asleep now, close your hand on the glove again" | not published | ANECDOTAL "successful replication"; no licence |
| **Edison bottle** (L21; comparator, not TDI) | 2021 | Hand-held 55 g bottle + PSG | Object drop (behavioural) | — | 0 (the drop itself wakes) | Free, within a 20 min rest | Spoken report after the drop | none | 103 | N1 ≥ 15 s → 83 % insight; drops follow about 1 min of microsleep |
| **SteelBall** (Bangle.js) | 2020 | Wrist PPG | Mean of 5 HR samples < user target **and** SD < 3 | User sets the target by hand | ~0 (README: "a slight delay ... seems to work better") | ≤ 2 triggers | none | Vibration | 1 (author) | ANECDOTAL |
| **Smartwatch SWE** (W25; MIT, not TDI) | 2025–26 | Consumer smartwatch HR + motion; EEG headband for verification | On-watch HR+motion staging model (deep sleep) | — | Block-wise stimulation | All night | — | Auditory / vibrotactile | 93 (68 stimulated) | Delta power up; no group-level next-day effect (ABSTRACT ONLY) |

### 3.3 Per-awakening incorporation across MIT studies (one view of the table above)

| Study | Wake-up rule | % of reports with direct theme | % participants ≥ 1 |
|---|---|---|---|
| P20 / SR23 Sleep+Tree | Dormio onset + 1–5 min | 67 % (P20, 45/67); 70.3 % per-participant mean (SR23) | — (25/25 sleepers recalled ≥ 1 dream) |
| FS26 N1 part | Dormio onset + 1–5 min | 57 % (15/31) | 100 % (11/11) |
| Y25 | ZMax 2 × 30 s N1, immediate | **40.6 %** | 92 % (23/25) |
| P20 Wake+Tree (control) | 7 min + 1–5 min, awake | 52 % (29/56) | — |

INFERENCE, weak: per-awakening incorporation was lower when wake-ups came right after EEG-scored N1 than
after Dormio's 1–5 min delay. The populations, session lengths and staging methods also differ, so this
is not a controlled comparison. S24 notes the interval has "never been systematically addressed".

---

## 4. What the released code does differently from the papers

Details and line numbers are in `opensleep_dormio_code_analysis.md` §0, §3 and §10. This is a summary,
with this file's new evidence added.

| Topic | Papers | Released code | New evidence here |
|---|---|---|---|
| Thresholds | 5 BPM / 4 / 8 (P20, SR23) | Web UI defaults 6 BPM / 4 / 5; iOS 15 BPM / EDA 10 | `idealemu/TDI` uses a relative 3.5 % rise + 10 s hold |
| Direction | "drops in BPM ... shifts either up or down in EDA" (PhD22 p.59) | `Math.abs`, both directions | `idealemu` uses rise only; L21 shows a behavioural drop |
| Hold / debounce | None described | None | `idealemu` 10 s stable; SteelBall SD < 3 over 5 samples |
| Baseline | 120 s text, 3:00 figure | Web 3 min; iOS 300 s | `idealemu` 90 s |
| Onset → wake | Uniform 1–5 min (lab); 3 min (v1, Dormio Light) | Web wearable 2 × 3 min = 6 min; bc variant 3 min | MAKE22 describes two prompts ("beginning" and "end" of hypnagogia), so the double wait may be intended |
| Later loops | Re-detect each loop (P20 "This loop of events was repeated") | Timer-only after the first onset (web, guided iOS) | FS26 measured 8.23 min between onsets; the web re-sleep default is 7 + 3 = 10 min |
| Recording | Unlimited, ends when speech ends (SR23); silence detection (C20, PhD22 p.63) | Web fixed 30 / 60 s; iOS 30–120 s with 8 s silence | SI reports average 14 words (SR23) |
| Sleep-state question | "And were you asleep?" Awake / Halfway / Asleep | Not in the web code (grep for "asleep" finds only UI tooltips) | Y25 still used it in 2025 |
| Early false-positive guard | Human experimenter (SM19) | Optional latency window that "wards against too-early and too-late sleep detection" (`wearable.html:170`) | `idealemu` grace window |
| Licence | MLP pages show a CC BY 4.0 notice; paper licences not checked here | **No licence** on any repo | Same for `idealemu/TDI` and SteelBall |

---

## 5. Conflicts and gaps (only those new to this file)

| ID | Conflict / gap | Sources |
|---|---|---|
| N1 | Exp 3 wake rule: 1–5 min Dormio timer (PhD22 p.127 Condition 1) vs "immediately after two epochs of visually scored N1" (Y25). Session 45 min (PhD22) vs 1 h (Y25). Enrolled 25 (PhD22 p.126) vs 28 (Y25) | PhD22 Ch. 5; Y25 Methods |
| N2 | Exp 3 nightmare outcome: thesis reports a frequency-dependent NDQ reduction (β = −0.656, p = 0.002); the published paper reports no nightmare-subgroup differences and omits the NDQ regression | PhD22 p.135; Y25 Results |
| N3 | Y25 calls the device the "Dormio headworn system"; every other source says hand-worn or wrist | Y25 Protocol vs P20, C20 |
| N4 | CHI18 calls loss of grip the entry to "stage 2"; P20 and SR23 call the same family of signals "sleep onset (N1)" | CHI18; SR23 Methods |
| N5 | MLP-DOR lists "heart-rate variability" as a Dormio signal; no paper or code uses HRV | MLP-DOR; opensleep_dormio_code_analysis.md §10 |
| N6 | Volume, fade, speaker distance and earbud use are still unreported in every MIT source, including MAKE22, which says to "tweak ... the volume of your audio" | all |
| G1 | W25's watch model, staging accuracy and latency: not verified (abstract only) | W25 |
| G2 | The `idealemu/TDI` "user study" results are unpublished; the replication claim is unverifiable | GH |
| G3 | The MIT News 2020 list of other universities running Dormio: only Duke is published (FS24). No TDI paper from Boston College, Harvard (other than Stickgold co-authorship), Rochester or Chicago turned up in Europe PMC | NEWS20; Europe PMC |

---

## 6. Implications for OpenTDI

Evidence strength: **strong** = several primary sources agree or the fact is directly observable;
**moderate** = one primary study, or consistent but confounded data; **weak** = inference across studies
or a thesis-only result; **anecdotal** = README or hobby report.

1. **Keep the post-onset delay variable at 60–300 s (uniform, redrawn each loop) as the default. Do not
   wake immediately on detection.** The 1–5 min delay is the only rule used in the studies with the highest
   per-report incorporation (57–70 %). The one study that woke right after about 60 s of EEG-scored N1
   got 40.6 % (Y25), and v1 used a fixed 3 min. Offer 60 / 90 / 180 s presets as "shallow / medium / deep"
   (PhD22 p.56). Strength: **moderate** for the range; **weak** for "immediate is worse" (§3.3 caveat).
2. **Add an early-onset guard: no onset accepted for the first N minutes after the baseline ends, plus an
   optional user "I usually fall asleep in X–Y min" window.** MIT's own failure mode is false detections in
   the first minutes, caught only by an experimenter (SM19 Limitations). MIT's code already offers the
   latency window "wards against too-early and too-late sleep detection" (`wearable.html:170`).
   Suggested default, GUESSED: ignore onsets in the first 3 min after calibration; the observed first-onset
   latency was 10.3 ± 6.4 min (P20). Strength: **moderate** for the need; the value is **GUESSED**.
3. **Use a hold or debounce and a post-loop refractory period, and label both as OpenTDI additions, not
   MIT.** MIT used single-sample OR logic. The independent build added a 10 s stable-state hold and a 10 s
   grace after re-closing the hand (`idealemu/TDI`). L21 shows the physiological lead-up to a behavioural
   drop is about 24 s of rising delta. A 10–20 s hold is consistent with these, but no study tested a value.
   Strength: **weak / anecdotal**. Values are **GUESSED**.
4. **Decide re-detection per loop on purpose. Offer "detect each loop" (as in the papers) with a timer
   fallback (the synthesis in `landscape_comparison.md` proposes 900 s on the first loop, 600 s later; GUESSED). FS26 measured 8.23 ± 2.32 min between successive sleep-onset reports, which includes the 1–5 min delay and the report.** The released web code is
   timer-only after the first onset; the papers describe repeated detection. Strength: **moderate**.
5. **Treat TIMED_CUES as a first-class mode, not a degraded fallback.** FS24 (n = 80, Duke collaboration)
   reproduced in-lab incorporation rates with no sensor. PhD22 p.138 says EEG "added little" and recommends
   the hardware-free web interface for clinical use. Ship FS24's study values (4 rounds, 3 min dwell, 60 s
   report, 7 min re-sleep), not the code's default of 3 loops. Strength: **moderate**: one study, self-report
   state, typed reports.
6. **Automate the MIT incubation score on the phone.**
   - After speech-to-text, count direct hits: the theme word plus user-listed parts, following P20's
     "tree ... branch, leaf, root" and Y25's "tree, forest, branch, root". Count indirect hits from a
     separate user list.
   - Store direct hits per report next to the "And were you asleep?" answer, which keeps the full MIT cue
     set, including Y25's 2025 protocol.
   - This turns OpenTDI's journal into the same outcome measure MIT reported, by hand, in every paper.
   - Strength: **strong** for the method (it is MIT's published scoring rule). STT accuracy on drowsy
     speech is **unverified**.
7. **Design recording for short, soft reports.** SI reports averaged 14 words (SR23 Table 1). The lab used
   no time cap. MIT's only silence-detection code used a 30 s minimum, a 120 s maximum and 8 s of silence
   (iOS). Suggested defaults: a minimum window of 20–30 s before silence can end recording, 8 s trailing
   silence, a 90–120 s cap, and "unlimited" as an option. Strength: **moderate** (code values + SR23 data).
   The exact values are **GUESSED** within that envelope.
8. **Make the wake prompt gentle and human-voiced.** CHI18 found a TTS robot voice "jarring" (2/6) and
   "disruptive" (4/6). MIT then switched to recorded human voices; own voice and experimenter voice worked
   equally well (PhD22 p.56). S24 summarises 1960s work: gradual arousal gave more thought-like reports
   than abrupt arousal. This supports OpenTDI's fade-in, but those studies were night REM/NREM, not N1.
   Default to recorded voice; offer TTS as a fallback only. Strength: **moderate** (voice); **weak** (fade
   evidence transfers from other sleep stages).
9. **The watch-only form factor is an advantage. Keep any "hold" behavioural task optional.** Users
   complained about the closed-fist FSR (CHI18: 2/6 said it made sleep harder), the "too hot" glove
   (MAKE22), and wires (SM19). If OpenTDI adds a wrist-raise or fist task to imitate the flex signal, make
   it opt-in and log it as an experimental feature. Strength: **moderate** for comfort; the wrist task
   itself is **untested**.
10. **Plan for sessions where nobody falls asleep, and for dropped links.** The MIT studies show both:
    - CHI18: 2 of 8 people "not tired enough".
    - Y25: 3 of 28 "failure to fall asleep".
    - FS26: 3 could not sleep, and 1 was lost to a "bluetooth connectivity failure resulting in missing
      physiological data". INFERENCE: 16 enrolled, from 11 analysed plus 5 excluded.
    - FS24: 52 of 132 excluded on state self-report.

    So OpenTDI needs:
    - a session time cap with an end alarm;
    - a logged "no onset" outcome;
    - an automatic switch to TIMED_CUES when no onset is found by the end of the user window (as MIT's
      code forces onset at the window max);
    - watch-side buffering so a Bluetooth drop does not lose the session.

    Strength: **strong** (repeated in four studies).
11. **Default session: a 45-minute time box, afternoon nap, about 5 awakenings expected.** Exp 1 used
    45 min (5.15 reports in SI); Y25 used 1 h (4.83 N1 episodes); FS26 used 30 min (2–6). Strength:
    **moderate**.
12. **Validate against MIT's benchmarks.** In an OpenTDI pilot, report:
    - % participants with ≥ 1 direct incorporation (MIT: 91–100 %);
    - % reports with a direct theme (57–70 % with the 1–5 min delay);
    - reports per 45 min (about 5);
    - the Awake / Halfway / Asleep distribution;
    - first-onset latency (10.3 ± 6.4 min).

    For detector validation, record a ZMax- or Muse-class EEG headband alongside, and score 30 s epochs
    with MIT's rule of 2 consecutive N1 epochs (Y25, FS26). Y25 cites ZMax vs PSG sensitivity of 68.3 % and
    kappa of 57.3, so the headband is itself an imperfect reference. Strength: **moderate**.
13. **Educate users before the first session.** Pre-session belief in TDI predicted incubation success
    (β = 0.39, p = 0.0005; PhD22 p.135–136). This result is from the thesis only and is not in Y25.
    Suggested: a short onboarding screen explaining what hypnagogic dreams feel like ("This period ...
    feels to some people like sleep, to others just like relaxation", SR23-SM §1.1). Strength: **weak**.
14. **Make no clinical claims.**
    - The only TDI nightmare study (Y25) had no control group, no blinding, a one-week follow-up, and no
      nightmare-subgroup effect in its published form.
    - Post-2024 clinical dream-engineering results come from TMR during deeper sleep (Sayk 2026: null;
      Schaich 2026: positive, preprint), not from sleep-onset TDI.
    - Keep FS24's screening advice, and state plainly that OpenTDI is a wellness and research tool.

    Strength: **strong** that the evidence is insufficient for claims.
15. **Privacy by default.** Participants sometimes woke themselves to avoid disclosing a strange dream
    (MLP-DOR). MIT's DIY build kept data in a local zip "not uploaded to any server" (MAKE22). Store
    reports locally and let the user delete or skip any single report, with no cloud default. Strength:
    **anecdotal** (the observation) / **strong** (the design norm).
16. **Licensing (relevant to the GPL-3.0 choice).**
    - Facts: no Dormio repo has a licence, and neither has `idealemu/TDI` or SteelBall. None of their code
      can be copied into a GPL-3.0 (or any) project. Re-implement from the papers, which is what this
      research already supports.
    - The MIT Media Lab pages carry a CC BY 4.0 notice. Short quotations of cue wording with attribution
      are fine (INFERENCE; not legal advice).
    - Strength: **strong** (licence facts checked through the GitHub API and page text).
17. **The smartwatch path has a direct MIT precedent: use it as design support, not as a validated
    detector.** W25 ran HR+motion sleep staging on the watch to drive closed-loop audio and vibration at
    home with n = 93. That shows the architecture is feasible, but it targeted deep sleep, and I saw only
    the abstract. It provides no N1 thresholds. Strength: **weak** for N1 relevance.
18. **Do not cite the Dormio project page's "heart-rate variability" mention as a source for an RMSSD
    rule.** No MIT paper or code computes HRV (§5 N5). If OpenTDI keeps an RMSSD signal, mark it as an
    OpenTDI hypothesis to test with the replay harness. Strength: **strong** (absence checked in papers and
    code).
