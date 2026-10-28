# Audio cues, wake prompts and dream-report capture at sleep onset: evidence survey for OpenTDI

Research notes written 2026-10-27 for OpenTDI's prompt player and dream recorder (CLAUDE.md §3 `:mobile`,
§5). This file adds **new** material. It does not repeat the Dormio protocol itself. That is in
[`../horowitz2020_dormio_paper.md`](../horowitz2020_dormio_paper.md) (P20, SM19, PhD22, SR23, FS24, FS26),
[`../opensleep_dormio_code_analysis.md`](../opensleep_dormio_code_analysis.md) (MIT web and iOS code), and
[`../android_audio_stt_notes.md`](../android_audio_stt_notes.md) (Android routing, AOSP standby, silence
detector). The lineage of MIT TDI versions is in [`mit_tdi_lineage.md`](mit_tdi_lineage.md).

**Labels.** **FACT** means the source I actually read says it (quote or close paraphrase, with location).
**INFERENCE** is my own reasoning or arithmetic. **ABSTRACT ONLY** means I read only the abstract. **NOT
VERIFIED** means I saw the claim only in a secondary source, or only the title.

---

## 0. Key findings (read first)

1. **No Dormio source gives a cue level in dB, a fade time or an output device.** The only guidance is
   qualitative: "loud enough to rouse … but not so loud as to shock them awake" (SM19 p.71). Lab prompts
   came from "computer speakers" (FS26). Every OpenTDI loudness number must therefore come from other
   paradigms (FACT, existing notes §4–§6).
2. **Studies that cue sleepers without trying to wake them used cues of about 35–50 dB SPL at the
   head.** The cues sat at or a few dB above continuous white noise of about 37–45 dB. Examples: ~35 dB(A)
   tones over a similar-level background (ANT12), ~37 dB(A) (CRE15), 40–45 dB SPL beeps over ≤45 dBA
   noise (KON21), 45–50 dB peak spoken names over 43–44 dB noise (WHI22b). These are NREM/REM sleep
   cueing, not N1 wake prompts (FACT).
3. **At home, cue loudness is the main thing that decides benefit or harm.** Loud cues turned a memory
   benefit into an impairment (WHI22a, GOL19). The SleepStim authors' advice is "cues for home TMR be
   barely audible in a quiet room" (WHI22a Discussion). Every working home system calibrates the level to
   the user and then adapts it: user-set white noise as the anchor (WHI22a), a threshold test on night 1
   (KON24), and a pre-sleep "acceptable" setting of 30–50 dB (PAP17) (FACT).
4. **Report early, and the theme comes back more literally.** In Wamsley's sleep-onset study, 25 % of
   content reports had *direct* task imagery at ≤15 s of sleep. After 120 s this "was reduced by more than
   half", while *indirect* imagery rose (WAM10 Results, Fig. 3). OpenTDI scores incubation success with
   direct word matches, so this favours the short end of Dormio's 1–5 min delay (FACT + INFERENCE).
5. **Hypnagogic content is lost unless it is reported straight away.** No source I found measures a
   seconds-scale forgetting curve for hypnagogic reports; that gap is real. What exists:
   - reports taken at the moment of awakening caught hypnagogia 61.7 % of the time, against 22.5 % when
     asked at the end of a 20-min nap (LAC21);
   - after 10 min of sleep, material from the last ~4 min before sleep onset is lost; after 30 s of sleep
     it is not (WYA94, WYA97, ABSTRACT ONLY);
   - about 2 min of wakefulness is proposed as the minimum for a dream to be encoded at all (VAL17,
     ABSTRACT ONLY).

   Implication: start recording at prompt end, as Dormio does, and do not put speech or tasks between the
   prompt and the report (FACT + INFERENCE).
6. **Dormio's N1 reports are very short.** The Sleep-Incubation group averaged **14.0 ± 9.9 words per
   report** and Sleep No-Incubation 8.7 ± 4.2 (SR23 Table 1). Nightcap sleep-onset reports averaged 83 ±
   51 words under a "report all mental activity" instruction (WAM10, table notes). So a 45–120 s cap
   rarely truncates a report. The silence timeout matters more, because drowsy speech is halting (P20
   §3.1). MIT's own iOS app used 8 s of silence, at least 30 s and at most 120 s (CODE-iOS; existing notes
   §5) (FACT).
7. **Serial awakenings are tolerated, but they cost sleep.** Overnight serial awakenings (≈18 a night, every
   15–30 min) gave 73 % sleep efficiency and 110 min of wake after sleep onset. One participant quit nights
   early, and one had confusional arousals. The authors say the method is "best reserved for highly
   motivated individuals with a good sleep quality" (SIC13). In naps, awakening after only N1 left
   performance worse than before the nap (HAY05, ABSTRACT ONLY) (FACT).
8. **MIT scored incubation by counting theme words.** P20 used one condition-blind rater counting
   tree-part words. SR23 counted "direct references per report": tree, leaf, branch, root, forest.
   Counting words like this can be done on the device. Human scoring of *indirect* incorporation is
   unreliable: in FS24, two raters agreed only at κ = 0.17 (FACT).
9. **Consumer sleep earbuds often fight a TDI app.** Two examples:
   - Bose Sleepbuds II do not stream audio from other apps (NOT VERIFIED from Bose; several reviews say so).
   - Soundcore Sleep A20 switch themselves into a "Sleep mode" that "cannot play Bluetooth music", via
     "Smart Switch" when they detect sleep. Their FAQ also warns that a pillow can block one earbud and
     cause Bluetooth dropouts (FACT, official Anker page).

   A TDI cue fires *after* sleep onset, exactly when such features cut the stream (INFERENCE).

---

## 1. Sources

| Key | Citation | DOI / URL | What I read |
|---|---|---|---|
| WAM10 | Wamsley EJ, Perry K, Djonlagic I, Reaven LB, Stickgold R (2010). Cognitive replay of visuomotor learning at sleep onset: temporal dynamics and relationship to task performance. *Sleep* 33(1):59–68 | 10.1093/sleep/33.1.59; PMC2802248 | Full text (PMC HTML) |
| SIC13 | Siclari F, LaRocque JJ, Postle BR, Tononi G (2013). Assessing sleep consciousness within subjects using a serial awakening paradigm. *Front Psychol* 4:542 | 10.3389/fpsyg.2013.00542; PMC3747360 | Full text |
| SIC17 | Siclari F, Baird B, Perogamvros L, et al. (2017). The neural correlates of dreaming. *Nat Neurosci* | 10.1038/nn.4545; PMC5462120 | Full text (author manuscript) |
| NOR09 | Noreika V, Valli K, Lahtela H, Revonsuo A (2009). Early-night serial awakenings as a new paradigm for studies on NREM dreaming. *Int J Psychophysiol* 74:14–18 | 10.1016/j.ijpsycho.2009.06.002 | ABSTRACT ONLY |
| ROW98 | Rowley JT, Stickgold R, Hobson JA (1998). Eyelid movements and mental activity at sleep onset. *Conscious Cogn* 7:67–84 | 10.1006/ccog.1998.0333 | ABSTRACT ONLY |
| LAC21 | Lacaux C, Andrillon T, Bastoul C, Idir Y, Fonteix-Galet A, Arnulf I, Oudiette D (2021). Sleep onset is a creative sweet spot. *Sci Adv* 7(50):eabj5866 | 10.1126/sciadv.abj5866; PMC8654287 | Full text |
| NIE17 | Nielsen T (2017). Microdream neurophenomenology. *Neurosci Conscious* nix001 | 10.1093/nc/nix001; PMC6007184 | Full text |
| WYA94 | Wyatt JK, Bootzin RR, Anthony J, Bazant S (1994). Sleep onset is associated with retrograde and anterograde amnesia. *Sleep* 17(6):502–511 | 10.1093/sleep/17.6.502 | ABSTRACT ONLY |
| WYA97 | Wyatt JK, Bootzin RR, Allen JJ, Anthony JL (1997). Mesograde amnesia during the sleep onset transition: replication and electrophysiological correlates. *Sleep* 20(7):512–522 | 10.1093/sleep/20.7.512 | ABSTRACT ONLY |
| VAL17 | Vallat R, Lajnef T, Eichenlaub JB, et al. (2017). Increased evoked potentials to arousing auditory stimuli during sleep: implication for the understanding of dream recall. *Front Hum Neurosci* 11:132 | 10.3389/fnhum.2017.00132; PMC5360011 | ABSTRACT ONLY |
| GOL19 | Göldi M, Rasch B (2019). Effects of targeted memory reactivation during sleep at home depend on sleep disturbances and habituation. *npj Sci Learn* 4:5 | 10.1038/s41539-019-0044-2; PMC6497651 | Full text |
| WHI22a | Whitmore NW, Harris JC, Kovach T, Paller KA (2022). Improving memory via automated targeted memory reactivation during sleep. *J Sleep Res* e13731 | 10.1111/jsr.13731; PMC9649863 | Full text (author manuscript) |
| WHI22b | Whitmore NW, Bassard AM, Paller KA (2022). Targeted memory reactivation of face-name learning depends on ample and undisturbed slow-wave sleep. *npj Sci Learn* 7:1 | 10.1038/s41539-021-00119-2; PMC8755782 | Full text |
| KON21 | Konkoly KR, Appel K, Chabani E, et al. (2021). Real-time dialogue between experimenters and dreamers during REM sleep. *Curr Biol* 31:1417–1427.e6 | 10.1016/j.cub.2021.01.026; PMC8162929 | Full text (methods) |
| KON24 | Konkoly KR, Whitmore NW, Mallett R, Mazurek CY, Paller KA (2024). Provoking lucid dreams at home with sensory cues paired with pre-sleep cognitive training. *Conscious Cogn* 103759 | 10.1016/j.concog.2024.103759; PMC11542932 | Full text (author manuscript) |
| CRE15 | Creery JD, Oudiette D, Antony JW, Paller KA (2015). Targeted memory reactivation during sleep depends on prior learning. *Sleep* 38(5):755–763 | 10.5665/sleep.4670; PMC4402655 | Full text (methods) |
| ANT12 | Antony JW, Gobel EW, O'Hare JK, Reber PJ, Paller KA (2012). Cued memory reactivation during sleep influences skill learning. *Nat Neurosci* 15:1114–1116 | 10.1038/nn.3152; PMC3498459 | Full text (methods) |
| RUD09 | Rudoy JD, Voss JL, Westerberg CE, Paller KA (2009). Strengthening individual memories by reactivating them during sleep. *Science* 326:1079 | 10.1126/science.1179013; PMC2990343 | Full text (author manuscript, supplement methods) |
| PAP17 | Papalambros NA, Santostasi G, Malkani RG, et al. (2017). Acoustic enhancement of sleep slow oscillations and concomitant memory improvement in older adults. *Front Hum Neurosci* 11:109 | 10.3389/fnhum.2017.00109; PMC5340797 | Full text (methods) |
| WHO09 | WHO Regional Office for Europe (2009). *Night Noise Guidelines for Europe*. ISBN 978 92 890 4173 7 | PDF copy at https://en.unav.edu/documents/16089811/16216616/E92845.pdf (iris.who.int served only a JS app) | Table 1 (p. XIII) and "Effects on sleep" (p. 47) read |
| ZEP84 | Zepelin H, McDonald CS, Zammit GK (1984). Effects of age on auditory awakening thresholds. *J Gerontol* 39:294–300 | 10.1093/geronj/39.3.294 | ABSTRACT ONLY |
| BUS94 | Busby KA, Mercier L, Pivik RT (1994). Ontogenetic variations in auditory arousal threshold during sleep. *Psychophysiology* 31:182–188 | 10.1111/j.1469-8986.1994.tb01038.x | ABSTRACT ONLY |
| ERM10 | Ermis U, Krakow K, Voss U (2010). Arousal thresholds during human tonic and phasic REM sleep. *J Sleep Res* 19:400–406 | 10.1111/j.1365-2869.2010.00831.x | ABSTRACT ONLY |
| PUT90 | Putnam LE, Roth WT (1990). Effects of stimulus repetition, duration, and rise time on startle blink and automatically elicited P300. *Psychophysiology* 27:275–297 | 10.1111/j.1469-8986.1990.tb00383.x | ABSTRACT ONLY |
| BLU18 | Blume C, Del Giudice R, Wislowska M, Heib DPJ, Schabus M (2018). Standing sentinel during human sleep: continued evaluation of environmental stimuli in the absence of consciousness. *NeuroImage* 178:638–648 | 10.1016/j.neuroimage.2018.05.056 | ABSTRACT ONLY |
| KOU14 | Kouider S, Andrillon T, Barbosa LS, Goupil L, Bekinschtein TA (2014). Inducing task-relevant responses to speech in the sleeping brain. *Curr Biol* 24:2208–2214 | 10.1016/j.cub.2014.08.016; PMC4175175 | Abstract plus a skim of the main text |
| TAK92 | Takeuchi T, Miyasita A, Sasaki Y, Inugami M, Fukuda K (1992). Isolated sleep paralysis elicited by sleep interruption. *Sleep* 15(3):217–225 | 10.1093/sleep/15.3.217 | ABSTRACT ONLY |
| HAY05 | Hayashi M, Motoyoshi N, Hori T (2005). Recuperative power of a short daytime nap with or without stage 2 sleep. *Sleep* 28(7):829–836 | 10.1093/sleep/28.7.829 | ABSTRACT ONLY |
| TAS00 | Tassi P, Muzet A (2000). Sleep inertia. *Sleep Med Rev* 4:341–353 | 10.1053/smrv.2000.0098 | ABSTRACT ONLY |
| ELC21 | Elce V, Handjaras G, Bernardi G (2021). The language of dreams: application of linguistics-based approaches for the automated analysis of dream experiences. *Clocks & Sleep* 3:495–514 | 10.3390/clockssleep3030035; PMC8482230 | Full text (skimmed §1–3) |
| FOG20 | Fogli A, Aiello LM, Quercia D (2020). Our dreams, our selves: automatic analysis of dream reports. *R Soc Open Sci* 7:192080 | 10.1098/rsos.192080; PMC7481704 | ABSTRACT ONLY |
| ALT17 | Altszyler E, Ribeiro S, Sigman M, Fernández Slezak D (2017). The interpretation of dream meaning: resolving ambiguity using latent semantic analysis in a small corpus of text. *Conscious Cogn* 56:178–187 | 10.1016/j.concog.2017.09.004 | ABSTRACT ONLY |
| SPE16 | Speth J, Schloerscheidt AM, Speth C (2016). As we fall asleep we forget about the future: a quantitative linguistic analysis of mentation reports from hypnagogia. *Conscious Cogn* 45:235–244 | 10.1016/j.concog.2016.08.014 | ABSTRACT ONLY |
| KOE24 | Koenecke A, Choi ASG, Mei KX, Schellmann H, Sloane M (2024). Careless Whisper: speech-to-text hallucination harms. arXiv:2402.08021 | https://arxiv.org/abs/2402.08021 | ABSTRACT ONLY (arXiv API). Conference venue NOT VERIFIED here |
| CAR20 | Carr M, Haar A, Amores J, et al. (2020). Dream engineering: simulating worlds through sensory stimulation. *Conscious Cogn* 83:102955 | 10.1016/j.concog.2020.102955; PMC7415562 | Full text (auditory section) |
| HTMR | `nathanww/home-tmr` (SleepStim Android app for WHI22a), MIT licence, commit `c4d9230` | https://github.com/nathanww/home-tmr | `MainActivity.java` read via GitHub API |
| IDE24 | `idealemu/TDI`, "A successful replication of Dormio TDI" (author in code: Alexis Dumelié). **No licence file** (GitHub API 404), commit `e4c49d3` | https://github.com/idealemu/TDI | `TDI.py`, `recorder.py`, `makePrompts.py`, `AnalysisScripts/*` read. The "successful replication" claim is unpublished and NOT VERIFIED |
| TLRA | `rmallela26/TLR` (iOS/watchOS TLR app), MIT licence, commit `9cc30e7` | https://github.com/rmallela26/TLR | `SoundPlayer.swift`, `StimulusActivation.swift`. The linked IJoDR paper sat behind a bot check and was not read |
| SC-A20 | Anker/Soundcore, Sleep A20 product page and FAQ | https://www.anker.com/ca/products/sleep-a20-sleeping-earbuds | Fetched (WebFetch summary with quotes) |
| P20, SM19, PhD22, SR23, SR23-SM, FS24, FS26, CODE-web, CODE-iOS | As in `../horowitz2020_dormio_paper.md` §0 and `../opensleep_dormio_code_analysis.md` | — | I re-read SR23 and FS24 full text for scoring and report-length details not in those notes |

---

## 2. Cue loudness and delivery

### 2.1 What Dormio did (recap with pointers; nothing new)
- FACT: pre-recorded human voice, from the experimenter (Exp 1) or the participant (Exp 2), "without a
  significant change in efficacy" (PhD22 p.56). Delivered by "the computer" or "computer speakers" (P20
  §2.4; FS26 Methods). An experimenter stayed in the room (P20 §2.3).
- FACT: the volume guidance is qualitative only (SM19 p.71, quoted in §0). No dB value, fade, device or
  silence threshold appears in any MIT source (existing notes gap G3).
- FACT: Dormio Light (remote) used recordings in the participant's own voice. FS24 justifies this by
  citing Castaldo & Holzman (1967) on own-voice incubation; I did not read that paper.
- FACT: the MIT iOS code sets no output level (CODE-iOS). MIT's `FlowViewController.swift:203` sets
  `player?.volume = 0` only for a background *video*, not for the cues. I checked this in the local clone.

### 2.2 Cue levels in sleep-cueing labs (TMR, TLR): measured numbers

| Study | Goal | Cue | Cue level | Background | Device | Source location |
|---|---|---|---|---|---|---|
| ANT12 | NREM TMR, no waking | melodies | "similar to that of the background noise (approximately 35 dB sound pressure level [A])", with the noise "lowered slightly" during each tone | white noise ~35 dB(A) | speaker, assumed (not stated in the passage read) | Online Methods |
| CRE15 | SWS TMR | object sounds, "quietly presented over white noise" | ~37 dB(A) SPL measured at the head | white noise | speaker | Methods: "Sound intensity, measured from where participants' heads were located… approximately 37 dB(A)" |
| RUD09 | NREM TMR | object sounds | reported "~62 dB SPL" white noise, cues mixed in at constant overall level | white noise | "a speaker" | Supplement methods. **CRE15 says this dB value was reported incorrectly** and that the real level was about the same as CRE15's 37 dB(A) |
| WHI22b | N2/N3 TMR in a nap | spoken names plus background music | "highest volume possible without causing arousal (45–50 dB peak)", controlled by hand | white noise "at a low level (43–44 dB)" | speakers | Methods |
| KON21 (USA) | REM TLR, no waking | three-tone beeps (400/600/800 Hz, ~650 ms) | "approximately 40–45 dB SPL" | — | — | STAR Methods, USA group |
| KON21 (NL) | REM cueing | auditory cues | — | "white noise, set at a maximum of 45 dBA (whisper-like)" | — | STAR Methods, Netherlands group |
| PAP17 | closed-loop pink-noise pulses (SWS) | 50 ms pink noise | "adjusted to a level that the participant considered acceptable and would not disturb them during sleep (30–50 dB)" | — | "soft headphones specifically designed for sleep (Acoustic Sheep Sleep Phones, SP4BM)" | Methods |

- FACT: in all these studies the cue was meant **not** to wake. Cues stopped or paused on arousal: a 30 s
  pause on alpha/beta arousal (PAP17); manual control (WHI22b); stimulation stopped when arousals appear
  "in most previous TMR studies in the lab" (GOL19 Discussion).
- INFERENCE: for a cue meant to be heard without waking, the evidence clusters at **≈35–50 dB SPL at the
  ear, at or ≤ ~5 dB above a ~37–45 dB noise bed**. This applies to OpenTDI's *theme* cue when it plays
  during descent. It does **not** apply to the *wake* prompt, which is supposed to rouse.
- FACT, a caution on dB values: RUD09's published level was later called incorrect by the same lab
  (CRE15). Quote dB figures only together with how and where they were measured.

### 2.3 Home and automated cueing systems (closest analogues to OpenTDI)

**GOL19: unsupervised home TMR (iPod shuffle plus in-ear earphones).**
- Setup (FACT): 66 participants, Philips SHQ3200 "in-ear sports ear phones", 30 min of silence, then about
  60 min of words with 5 s gaps. "The first block was presented with linearly increasing volume to
  minimize waking through sudden sound onset." Participants woken by the words were told to turn the volume
  down and restart (Methods).
- Results, night 1 (FACT):
  - 19 of 66 were woken by the words, 32 had other disturbances ("discomfort with headphones"), and 15
    slept undisturbed. By night 3, 31 slept undisturbed.
  - 20 participants turned the volume down on night 1.
  - The word-disturbed group showed an **impairment** for cued words of about 17 % (Results).
- The authors' call (FACT): future work should use "automatic stopping of stimulation when sign of
  awakenings are detected as well as brain-signal based volume control" (Discussion).

**WHI22a: SleepStim, a Fitbit watch plus an Android phone. Architecturally the nearest system to OpenTDI.**
- Calibration and adaptation (FACT):
  - The phone plays continuous white noise all night. The user sets it "to a comfortable level", and
    "this intensity setting was used as the initial setpoint for sounds".
  - The cue level drops if the classifier's N3 probability falls below 0.85 during cueing.
  - Fitbit signal loss for 10 s turns sounds off.
  - An adaptation night with a neutral "ding" comes before the real cues (Methods, Fig. 3).
- Results (FACT): in Exp 1 (n = 61) the benefit "varied systematically with stimulus intensity;
  low-intensity but not high-intensity stimuli produced memory benefits". Exp 2 capped the initial
  intensity at 0.02 and gave a reliable benefit (Abstract; Exp 2 Methods). "Very few participants reported
  hearing cues", so the authors attribute the reversal to micro-arousals. Their recommendation: "cues for
  home TMR be barely audible in a quiet room" (Discussion).
- Code details (FACT, HTMR `MainActivity.java`):
  - `maximizeSystemVolume()` sets `STREAM_MUSIC` to maximum (`:229-232`). Loudness is then set with
    `MediaPlayer.setVolume` scalars: white noise from `MIN_NOISE = 0.005` plus a slider times
    `maxNoise = 0.015` (`:82, :108, :560`).
  - The cue starts at the noise level (`CUE_NOISE_OFFSET = 0.0`, `CUE_NOISE_MAX = 0.0`, `:86-87, :549`),
    rises by `VOLUME_INC = 0.05/200` per step (`:100, :860-867`), and on an arousal drops and pauses for
    `BACKOFF_TIME = 5 min` (`:84, :793-834`).
  - The inter-stimulus interval is 10 s (`:91`).
- INFERENCE: the "0.02" cap is therefore a `MediaPlayer` gain at maximum system volume, not dB SPL. Its
  acoustic level depends on the phone. Forcing system volume to maximum is also a safety risk if an
  app-gain bug ever plays a file at 1.0. OpenTDI should not copy that part (see §8).

**KON24: lucid-dream cues from an Android phone placed face down beside the pillow.**
- Setup (FACT): users set phone volume to maximum. "On the first night … the app tested participants'
  auditory perceptual threshold and discrimination threshold". Cues started at the discrimination
  threshold, played every 20–40 s, and "each time a cue was presented the intensity increased by 0.16%".
- Pauses (FACT): movement detected by the phone accelerometer paused cues for 5 min (Exp 1) or 1 min
  (Exp 2). A user report of being woken paused cues for 45 min and reset the level to threshold.
- What went wrong (FACT): with ever-rising levels, "several participants noted that the cue sometimes woke
  them up like an alarm clock". Exp 2 therefore capped the level once a cue had been incorporated into a
  dream, and switched to "two distinct harp melodies, with intensity gradually fading in and out, each
  lasting approximately 3 seconds" (Exp 2 Methods). Limitation: "the difficulty in setting the ideal cue
  intensity, given the unknown background sound levels in participants' homes" (Discussion).

**PAP17**: a pre-sleep user setting of 30–50 dB over sleep headphones (see table above).

- INFERENCE: four independent home or near-home systems converged on the same pattern: (1) a user
  calibration step while lying in bed, (2) a level anchored to either a noise bed or a measured threshold,
  (3) automatic back-off when the wearable or phone senses arousal, and (4) a cap. None used an absolute
  dB target, because phone output in SPL is unknown.

### 2.4 How loud a sound wakes someone from light sleep

- FACT (WHO09 Table 1, p. XIII): the observed-effect threshold for "EEG awakening" is LAmax,inside 35 dB,
  and for "Motility, onset of motility" 32 dB.
- FACT (WHO09 p.47): Basner et al. (2004) found "the LAmax threshold for noise-induced awakenings … about
  35 dB(A)", assuming a background of 27 dB(A). "The noise threshold for awakening is particularly high in
  deep SWS … while it is much lower in shallower sleep stages (stages 1 and 2). In REM sleep the awakening
  threshold is variable and depends on the significance of the stimulus."
- ABSTRACT ONLY:
  - Auditory awakening thresholds fall with age, most steeply in stage 4 (ZEP84).
  - Children and preadolescents are much harder to wake (BUS94).
  - Tonic REM thresholds match stage 2, and phasic REM thresholds match slow-wave sleep (ERM10).
- NOT VERIFIED: Rechtschaffen, Hauri & Zeitlin (1966), "Auditory awakening thresholds in REM and NREM sleep
  stages", *Percept Mot Skills* 22:927–942 (DOI 10.2466/pms.1966.22.3.927). It is the classic stage-wise
  threshold study, but I saw only the title, so I cite no numbers from it.
- INFERENCE for the wake prompt:
  - N1 has the lowest arousal threshold of any sleep stage, and the wake prompt is a meaningful sentence
    rather than traffic noise.
  - So a prompt clearly above the room's noise floor, around the level of quiet speech, is probably
    enough. There is no TDI study measuring it.
  - Because thresholds differ by person (ZEP84: "individual differences" outweighed age), a per-user
    calibration beats any fixed number.

### 2.5 Fade-in, onset ramps and startle

- ABSTRACT ONLY (PUT90): the startle blink is sensitive to rise time from 3 to 45 ms, but those tests used
  110 dBA tones. INFERENCE: at sleep-cue levels (35–55 dB) a reflex startle is not the concern. The concern
  is arousal from an abrupt onset, so any ramp longer than a few tens of milliseconds already removes the
  click component.
- FACT: two home systems used ramps to avoid sudden onsets. GOL19 ramped the first minutes linearly "to
  minimize waking through sudden sound onset". KON24 faded each ~3 s melody in and out. MIT has no fade
  (CODE-web plays files directly; existing notes).
- FACT (NIE17, "Phasic vs. tonic stimuli"): at sleep onset, "abrupt, short-lasting (phasic) stimuli … result
  in equally abrupt changes in image content, most notably, in people or objects falling, appearing quickly,
  or being hit brusquely". External sounds are incorporated "very quickly, possibly within a few hundred
  milliseconds to, at most, a second or two".
- INFERENCE (design consequence):
  - CLAUDE.md's 2,000 ms logarithmic fade, if applied to a spoken prompt, attenuates its first word. "You're
    falling asleep" would start about 20–40 dB down.
  - Better: put the fade on a short pre-roll, such as a soft chime or the noise bed swelling over 1.5–2 s,
    and then speak at the target level.
  - That keeps the anti-abrupt-onset benefit, and the words stay intelligible.

### 2.6 Voice versus tone; own versus familiar voice

- FACT (MIT): voice prompts, in the experimenter's or the participant's own voice, with no efficacy
  difference (PhD22 p.56). The v1 robot TTS voice was listed as a usability problem, an "unpleasant voice
  interface" (SM19 p.77; existing notes §6.3).
- ABSTRACT ONLY (BLU18): "evaluation of voice familiarity continues during all NREM sleep stages and even
  REM sleep". "Especially UFV [unfamiliar voice] stimuli elicit larger responses … suggesting they continue
  being salient." INFERENCE: an unfamiliar or synthetic voice may be more arousing per dB than the user's
  own voice. That suits the wake prompt and is a mild argument for own-voice theme cues. It is untested in
  TDI.
- ABSTRACT ONLY (KOU14): sleepers in light sleep still categorise spoken words semantically. The paradigm
  ran from wake into sleep, with stimuli "every 6 to 9 s" (main text Fig. 1). This supports a *semantic*
  theme cue at sleep onset.
- FACT (SIC13, SIC17): the serial-awakening labs wake people with a non-verbal "computerized sound
  lasting 1.5 s", then interview them over an intercom. MIT combines the alert and the question in one
  spoken prompt.
- FACT (SM19 p.71): the v1 prompt began with the user's name ("[Name(x)] you are falling asleep"). So did
  the independent replication IDE24 (`makePrompts.py:38`, "[Your NAME], You are falling asleep"). P20,
  SR23 and Dormio Light dropped the name (existing notes §5). Own-name salience in sleep appears in BLU18's
  design, but the abstract reports no own-name result.

### 2.7 Speaker versus earphones
- FACT: all MIT lab studies and the NREM TMR labs above used room speakers. Home systems used in-ear
  sports earphones (GOL19), sleep headband headphones (PAP17), or the phone speaker by the pillow (KON24,
  WHI22a).
- FACT (GOL19): "discomfort with headphones" was part of the most common disturbance class (32 of 66 on
  night 1). Sleep quality reached baseline only by night 3 (Discussion: habituation).
- INFERENCE: for a ≤45-min nap, the phone on the nightstand or by the pillow is the evidence-backed default.
  That is MIT-like and SleepStim-like, and it avoids earphone discomfort and Bluetooth sleep-mode
  conflicts (§6). Headband speakers are the comfortable option for side sleepers who need privacy.

---

## 3. Wake prompts: wording, timing and recall

### 3.1 Probe wording across paradigms (FACT)

| Paradigm | Alert | Question |
|---|---|---|
| Dormio lab (P20 §2.4; SR23-SM §1.2) | "You're falling asleep" | "Please tell me, what's going through your mind", then "And were you asleep?" (Awake / Halfway / Asleep) |
| Dormio Light (FS24) | own-voice recording | "Tell me what is going through your mind" |
| Siclari serial awakening (SIC13 "Questionnaire") | 1.5 s computerized sound | "What was the last thing going through your mind prior to the alarm sound?" Subjects report "only the most recent conscious experience", then answer duration and rating questions |
| Wamsley Nightcap (WAM10 Methods) | computer prompt | "upon hearing a prompt, they were to report all mental activity experienced just prior to the prompt" |
| Lacaux "Edison" bottle (LAC21 Methods, Break) | sound of the dropped bottle | "describe out loud what was going through your mind before it fell … their mental content could be of any kind: thoughts, images, reveries, and dreams" |
| Dormio v1 (SM19 p.71) | "[Name] you are falling asleep" | "tell me, what are you thinking about", then "can you tell me more?" |

- FACT (P20 §2.4, citing Rechtschaffen 1973): asking for "anything that was going through your mind"
  rather than "a dream" raises NREM report rates (existing notes §5).
- INFERENCE: every lab uses the same two parts, an anchor to *just before* the alert and a broad
  definition of content. The broad definition (thoughts, images, feelings) is given in the **pre-session
  instructions**, not in the prompt, which stays short. OpenTDI's wording matches. The open choice is
  whether to add "the last thing" (Siclari) to anchor the report in time.

### 3.2 How fast hypnagogic content is lost: what is and is not known

- FACT (LAC21 Results, Fig. 4C):
  - Reports taken right after the bottle dropped contained hypnagogia 61.70 % of the time.
  - Reports taken at the end of the 20-min break, from those who did not drop it, did so 22.50 % of the
    time (χ² = 13.50, P < 0.001).
  - In a control with sound awakenings after microsleeps, the rate was 36.49 % (50 of 137).
- ABSTRACT ONLY (WYA94): "subjects allowed 10 minutes of sleep demonstrate deficient explicit memory for
  auditory stimuli presented immediately prior to sleep onset … free recall deficits for word pairs
  presented 3 minutes prior … In contrast … subjects allowed 30 seconds of sleep demonstrated no equivalent
  explicit memory deficits". WYA97 replicated this, with "profound amnesia … for stimuli presented in the
  4-minute window prior to sleep onset" after 10 min but not 30 s of sleep.
- ABSTRACT ONLY (VAL17): "the minimum necessary duration of an awakening during sleep for a successful
  encoding of dreams into long-term memory is approximately 2 min".
- FACT (WAM10 Results): at ≤15 s of Nightcap-defined sleep, "a full 25% of content-filled reports contained
  imagery unambiguously representing either the game itself, or skiing experiences in general". "After 120
  sec of sleep, however, this directly related game imagery was reduced by more than half", while indirect
  imagery rose. Collapsed across direct and indirect, task content "did not vary as a function of Nightcap
  latency".
- ABSTRACT ONLY (ROW98): awakenings after 15 s to 5 min of sleep. Longer sleep went with "a decrease in the
  length of mentation reports" and more dream-like features.
- FACT (SIC17 Discussion): "to avoid interfering with dream recall … subjects were always interviewed just
  after being awakened from a dream (short temporal lag) and in identical experimental conditions (lying on
  their back, with eyes closed, without the examiner entering the room)".
- NOT VERIFIED (secondary only): Cohen & Wolfe (1973, *J Consult Clin Psychol*) is described in later
  papers as showing that a task done before reporting raises "contentless" reports (a phone call to a
  weather line: 43 % against 18 %). The IJoDR replication (Parke & Horton 2009) sat behind a bot check and
  was not read.
- NOT VERIFIED (title only): Dement & Kleitman (1957, *J Exp Psychol* 53:339–346) is commonly cited for REM
  recall fading within minutes of REM ending. I did not read it.
- **Gap.** I found no study that measures how hypnagogic report content decays over *seconds* after an
  awakening. The seconds-scale claim in the task brief is plausible but unsourced.
- INFERENCE (design):
  1. Start capture the moment the prompt ends, as Dormio does (`onended`, CODE-web). Better, open the mic
     before the prompt ends so speech that overlaps the prompt tail is kept.
  2. Put nothing between prompt and report: no question about sleep state, no beep, no screen.
  3. Ask "were you asleep?" *after* the report, as MIT does.

### 3.3 The prompt itself can enter the report, and it can bias the answer
- FACT (NIE17): sleep-onset images absorb external sounds within about a second.
- FACT (FS26 Limitations): "the verbal prompt 'You are falling asleep' delivered prior to dream reports may
  have influenced participants' subsequent self-reports of their sleep state".
- INFERENCE: log the state answer as a *biased* label. When OpenTDI uses it as ground truth for the
  detector (existing notes recommendation 5), weight it accordingly. A neutral alert ("Tell me what's going
  through your mind") is worth testing as an option for validation sessions.

### 3.4 Report length and the response window (FACT)

| Source | Recording rule | Observed report size |
|---|---|---|
| SR23 lab (Methods; Table 1) | "Verbal reports were not subject to a time limit" | **14.00 ± 9.86 words** (Sleep-Incubation), 8.71 ± 4.17 (Sleep No-Incubation), 22.78 ± 23.67 and 18.58 ± 7.75 in the wake groups |
| FS24 Dormio Light | 60 s fixed. Audio **could not be analysed**: "unexpected issues (including technological and participant errors) precluded us from analyzing these verbal reports". Typed post-session reports were used instead | — |
| MIT web code | 60 s (timer) or 30 s (wearable) fixed, from the start of the prompt | — |
| MIT iOS code | stop after 8 s below `min(−35 dB, ambient) + 5 dB`; minimum 30 s, maximum 120 s (existing notes, opensleep §0 item 8) | — |
| IDE24 replication | 60 s fixed (`recorder.py:38`) | — |
| WAM10 Nightcap | free dictation | 83.1 ± 51.1 words when content was present. 79.4 % of 781 prompts had content. 98 % of prompts were answered |
| SIC13 serial awakening | structured intercom interview | the interview took "between 20 s and 3.5 min" |

- FACT (P20 §3.1): "One subject reported difficulty speaking while in hypnagogia, but uttered short phrases
  and elaborated on each after their final awakening."
- INFERENCE: at a slow 1–2 words/s, a 14-word report lasts roughly 7–15 s plus pauses. A 45 s cap rarely
  truncates. A 4 s silence cut-off is short for halting, sleepy speech, and MIT's own iOS app chose 8 s. A
  hard cap mainly protects against a TV or a partner talking; it is not about long reports.

---

## 4. Serial-awakening paradigms: rates, re-sleep intervals, burden, safety

| Study | Setting | Awakenings | Interval or trigger | Sleep or burden findings |
|---|---|---|---|---|
| Dormio lab (P20; SR23 Table 1) | 45-min afternoon nap | 5.15 ± 1.41 (SI), 5.75 ± 2.13 (SN) per session | detection, then 1–5 min | "positive and relaxing" (P20 §2.1, citing Hayashi 2005 and Horowitz 2018) |
| Dormio Light (FS24) | home, one hour | 4 | 3 min dwell, then 7 min re-sleep | no adverse events, with screening |
| MIT iOS defaults (CODE-iOS) | home | `numOnsets` 3 | after each report: "think of" plus a 240 s timer (`waitForOnsetTime`) | — |
| WAM10 Nightcap | home, first hour of the night | "a maximum of 10 prompts" or 1 h after sleep onset | prompts after 15, 45, 75, 120, 180 or 300 s of sleep (pseudorandom) | 98 % compliance |
| ROW98 Nightcap | home | — | after 15 s to 5 min of sleep | ABSTRACT ONLY |
| NOR09 | lab, early night | 164 in 20 nights (about 8 per session; INFERENCE) | about 24 min, in N2/N3 | sessions averaged 193 min. Recall and complexity "remained stable" (ABSTRACT ONLY) |
| SIC13 | lab, whole night, 256-ch EEG | 18.2 ± 3.4 per night | pseudorandom 15–30 min (mean 23.1 ± 8.3) | TST 305 ± 37 min, sleep efficiency 73 ± 8 %, WASO 110 ± 36 min (Table 1). Recall in one subject was the same at ~20 and ~35 min intervals |
| SIC17 Exp 3 | lab, 2 experimental nights | 84 in total, 12.0 ± 3.4 per participant | EEG-triggered, "a minimum of 120 seconds" in NREM before an awakening | 16 of 836 awakenings (Exp 2) excluded as "too somnolent" |
| LAC21 control | 30-min break | regular sound awakenings | after microsleeps (mean cumulated microsleep 1.10 ± 1.08 min) | — |

Burden and safety (FACT unless marked):
- SIC13 Discussion: "the sleep fragmentation can be uncomfortable and one subject prematurely ended the
  protocol for this reason. The serial awakening paradigm is thus best reserved for highly motivated
  individuals with a good sleep quality."
- SIC13 Results: one subject had "two spontaneous confusional arousals out of slow wave sleep". These
  came from N3 awakenings overnight, which a ≤45-min N1-targeted nap should rarely produce (INFERENCE).
- SIC13: recall fell on the 2nd and 3rd consecutive nights in 2 of 7 subjects, attributed to accumulating
  sleep restriction. INFERENCE: repeated daily TDI sessions may give diminishing returns. Track per-session
  recall rate.
- ABSTRACT ONLY (HAY05): naps ended after 5 min of stage 1 improved subjective sleepiness and fatigue, but
  "performance deteriorated and slow eye movements increased in the S1-nap condition". A 3-min-of-stage-2
  nap was recuperative. INFERENCE: TDI sessions deliberately hold users in N1. Expect no alertness
  benefit, and warn against driving or safety-critical tasks straight afterwards.
- ABSTRACT ONLY (TAS00): inertia after stage 1/2 awakenings is smaller than after SWS. "In the absence of
  major sleep deprivation, the duration of sleep inertia rarely exceeds 30 min."
- ABSTRACT ONLY (TAK92): interrupting nocturnal sleep produced sleep-onset REM periods in 71.9 % of cases,
  and isolated sleep paralysis in 9.4 % of interruptions, almost all from those REM periods. INFERENCE:
  repeated awakenings on a sleep-deprived or early-morning nap (high REM pressure) carry some risk of
  sleep paralysis. This matches FS24's screening list (existing notes recommendation 11).
- FACT (GOL19): in an unsupervised home setting, sound that wakes people can impair the targeted content,
  here memory for cued words. INFERENCE: in TDI the wake prompt is *meant* to wake. The theme cue that
  follows should not jolt the user into full alertness, which makes re-entry harder (SM19 p.71).

---

## 5. Dream-report collection and analysis

### 5.1 How MIT and the Stickgold lineage scored incorporation (FACT)
- **P20 (Methods):** "All dream reports were collected via audio, transcribed into typed text, and were not
  edited prior to scoring … a direct reference to 'Tree' is … the word 'tree' or the parts of a tree …
  including 'branch', 'leaf', or 'root'. An indirect reference … 'paper', 'plant', or 'wood'. One
  condition-blind rater counted instances of these specific words … Methods adapted from the Wamsley (2010)
  Alpine Racer study".
- **SR23 ("Assessing theme incorporation"):** "the average number of direct references to the incubated
  theme ('tree') made in each verbal report … an unambiguous mention of 'tree' or part of a tree (including
  leaf, branch, root, or forest)". Example: "thinking about how leaves are the eyes, roots are like lips,
  and the different senses trees must have" counts as 3. Table 1 reports "% Reports w/ 'tree' reference"
  (SI 70.29 ± 33.66) and "# 'Tree' references per report" (SI 1.25 ± 0.79).
- **FS24 (Results):** two raters scored direct ("I dreamed of becoming a tree") and indirect ("I imagined
  watering big plants") incorporation in *typed* reports. Counts correlated at r = 0.97, but "inter-rater
  reliability was relatively low (κ = 0.17)" because Rater 2 scored more. The main results held with
  either rater. FS24 also used the **uncued object as a control** (Fork against Tree).
- **FS26:** "Dream reports were scored by one rater … unambiguous mention of 'tree' or part of a tree
  (including leaf, branch, root, or forest)".
- **WAM10 (Scoring):** reports were "transcribed and then scored by 3 raters, blind to report set, awakening
  condition, and performance data". Two raters agreed 89 % (± 4 %), and a third resolved disagreements.
  200 **control reports** from an unrelated study gave a 2 % false-positive rate for "Alpine Racer"
  content.

### 5.2 Transcription
- FACT: the labs used human transcription (P20; SIC13: "answers were audiotaped and later transcribed";
  WAM10). The IDE24 replication shipped no ASR, only text-analysis scripts. Its `NOTE.md` says they "were
  prototyped and boilerplated with the assistance of ChatGPT".
- ABSTRACT ONLY (KOE24): for Whisper, "roughly 1% of audio transcriptions contained entire hallucinated
  phrases or sentences". Hallucinations "disproportionately occur for individuals who speak with longer
  shares of non-vocal durations". INFERENCE: hypnagogic reports are short, halting and full of pauses (P20
  §3.1), which is the high-risk profile for language-model ASR. Android's on-device recogniser is a
  different model, but the same caution applies.
- FACT (FS24): the remote study **lost its audio reports** to technical and participant errors and fell
  back on typed recall. INFERENCE: on-device storage must be robust, and a morning or post-session
  written summary is a useful fallback measure.
- I found no study validating any ASR on dream reports (WebSearch, 2026-10-27).

### 5.3 Automatic metrics that can run on the device
- FACT (ELC21 §1): "The most typical examples of structural information are the total word count … and its
  variants, such as the number of sentences". They "only provide a very rough estimate … and are likely to
  be greatly affected by inter-individual differences in verbosity". "Dictionary-based methods allow for a
  word-by-word classification of lexical items by comparing textual data with predefined lists … their
  implementation is simple and associated with low computational and time costs" (§3).
- ABSTRACT ONLY: distributional semantics on small corpora (ALT17: LSA beat Skip-gram on small dream
  corpora). Automatic Hall–Van de Castle scoring (FOG20). Blind-judge linguistic coding of hypnagogia
  reports (SPE16).
- FACT (IDE24 `AnalysisScripts/wordCount.py:10`): the replication counted theme words with the regex
  `\b[Tt]rees?\b` (case- and plural-insensitive) and ran TF-IDF of pre- against post-session text
  (`tfIDF.py`).
- INFERENCE: SR23's metric, direct references per report and % of reports with ≥1, is a lexicon count. It
  can be reproduced on the device *exactly as MIT defined it*. Indirect incorporation is where humans
  disagree (κ = 0.17), so an automatic "indirect" score should be labelled experimental.

---

## 6. Bluetooth and sleep earbuds (mostly marketing or anecdotal)

| Product | What it does that matters to TDI | Evidence |
|---|---|---|
| Soundcore Sleep A20 | "In Sleep mode, the earbuds can only play sleep sounds, but cannot play Bluetooth music." "Smart Switch" moves to Sleep mode once you fall asleep, and sound "automatically pause[s]". The FAQ warns that on your side "one earphone may be blocked due to pillow obstruction," which can cause Bluetooth dropouts or low sound. Bluetooth mode 8–10 h, Sleep mode 14 h, both at 50 % volume | FACT: official Anker/Soundcore page and FAQ (SC-A20) |
| Bose Sleepbuds II | Reported not to stream any third-party audio. Sounds come only from the Bose Sleep app and are stored on the buds | NOT VERIFIED: the Bose page redirected or returned 404. Several reviews (TechRadar, Tom's Guide, iMore) say so |
| Ozlo Sleepbuds (founded by ex-Bose engineers) | Reported to stream any audio, plus a "Bluetooth Disabled" mode that plays stored sounds | NOT VERIFIED: the official page returned HTTP 429. Secondary reviews only |
| Kokoon Nightbuds | Reported to sense sleep (PPG + accelerometer) and "Auto Pause / Fade Out" audio when you fall asleep | NOT VERIFIED: marketing copy and reviews (New Atlas, iMore) |
| Acoustic Sheep SleepPhones (headband) | Used for overnight closed-loop pink noise | FACT: research use (PAP17); no product claims checked |

- FACT (existing notes, android_audio_stt_notes §4): AOSP puts the output thread on standby after about
  3 s with no active track; on A2DP the delay is forced to at least 3 s. A third-party NVDA add-on and a
  blog report headsets that drop the start of audio after idle (anecdotal).
- INFERENCE:
  1. Any earbud with on-bud sleep detection or a "sleep mode" will silence a cue that fires *after* sleep
     onset, which is exactly when TDI cues fire. Warn users, and test with the device first.
  2. Side-sleeping can block one bud (SC-A20 FAQ). Play the cue mono on both channels.
  3. An audible noise bed (§2.2) is itself an active track, so it also keeps A2DP out of standby. That
     makes the −75 dBFS carrier unnecessary whenever the user opts into a noise bed.

---

## 7. Comparison: cue and recording parameters across implementations

| | MIT lab (P20/SR23) | MIT web timer (Dormio Light) | MIT iOS app | IDE24 replication | SleepStim (WHI22a, HTMR) | KON24 TLR app | CLAUDE.md (OpenTDI today) |
|---|---|---|---|---|---|---|---|
| Output | computer speakers | user's computer | phone | PC (`pydub.play`) | phone speaker | phone face down by the pillow | 4 routes, speaker default |
| Level | qualitative | unset | unset | unset | user-set noise gain; cue = noise gain; cap 0.02 (Exp 2) | starts at discrimination threshold, +0.16 %/cue | "calibrated target", cap |
| Fade | none | none | none | none | none per cue (gain ramps up over the night) | Exp 2: ~3 s fade in/out | 2 s log ramp |
| Background | room | room | room | room | continuous white noise | none | −75 dBFS pink carrier (Bluetooth/USB) |
| Arousal back-off | experimenter | none | none | none | p(N3) < 0.85 → lower gain, 5 min pause | movement → pause 5 min / 1 min; reported waking → 45 min | abort countdown on movement |
| Wake prompt | "You're falling asleep. Please tell me, what's going through your mind" | own voice | recorded | "[Name] You are falling asleep" + question | n/a | n/a | MIT wording |
| Post-onset delay | 1–5 min | 3 min dwell | 15 s `promptTime` after trigger | 3 min fixed (`recorder.py:27`) | n/a | n/a | 180 s default |
| Recording end | none (speech ends) | 60 s fixed | 8 s silence, 30–120 s | 60 s fixed | n/a | n/a | 4 s silence / 45 s |
| Loops | time box 45 min (~5) | 4 | 3 | "wake up fully" prompt on the 3rd cycle (`MAX_CYCLE = 2`, `recorder.py:22, 53`); no hard stop in the code | n/a | n/a | N |
| Re-sleep before next trigger | detection re-arms | 7 min | 240 s timer | 10 s grace (`TDI.py:41`) | n/a | n/a | not specified |
| Licence (for GPL-3.0 reuse) | — | see lineage file | see lineage file | **none: read only, do not copy code** | MIT (GPL-compatible with attribution) | — | GPL-3.0 |

(IDE24 detection: 90 s calibration, `TDI.py:33`; a 10 s stable-state window, `:37`. Prompts are recorded
in the user's voice with `noisereduce` at `prop_decrease = 0.95`, `makePrompts.py:21-30`. TLRA plays one
bundled file at system volume with no level or fade logic, `SoundPlayer.swift`; MIT licence.)

---

## 8. Implications for OpenTDI

Defaults first, then the reasoning. "Strength" grades the evidence *for the specific value in an N1 TDI
context*. Most values come from neighbouring paradigms (TMR, serial awakening), so few reach "strong".

### 8.1 Recommended defaults

| Parameter | Recommended default | Allowed range | Strength | Basis |
|---|---|---|---|---|
| Output route | phone on nightstand or by pillow (`NIGHTSTAND_SPEAKER`) | 4 routes | moderate | MIT speakers; GOL19 earphone discomfort; WHI22a / KON24 phone speaker |
| Level calibration | bedtime threshold test lying down: an ascending soft tone or the user's own cue, tap when first heard → threshold **T** (app gain, logged with the system volume index) | — | moderate (method) / weak (exact offsets) | KON24 threshold test; WHI22a user setpoint; PAP17 pre-sleep adjustment |
| Theme cue level (during descent) | **T + 10 dB**, or equal to the noise bed + 0–5 dB when a bed is on | T+5 … T+15 dB | weak–moderate | TMR cues at or slightly above a 37–45 dB bed (ANT12, CRE15, WHI22b, KON21); "barely audible" (WHI22a) |
| Theme cue level (right after the report, user awake) | same as above | — | weak | the user is awake; avoid re-alerting (SM19 p.71) |
| Wake prompt level | **T + 20 dB** | T+12 … T+30 dB | weak | N1 has the lowest arousal threshold (WHO09 p.47); individual differences (ZEP84); no TDI dB data |
| Wake escalation | if no speech within 10 s: replay once at **+6 dB**; never more than 2 escalations | 0–2 | weak | KON24 escalating design (and its "alarm clock" side effect) |
| Absolute ceiling | hard cap on app gain, and never force the system volume to max | — | moderate (safety) | HTMR forces max system volume (`:229-232`), which OpenTDI should not copy; existing notes §3.4 |
| Optional noise bed | off by default (MIT-like). When on: pink or white noise at a user-set "comfortable" level, also the Bluetooth keep-alive | — | moderate | CRE15, WHI22a/b, KON21 used continuous white noise; §6 inference |
| Onset shaping | **1.5–2 s fade on a pre-roll** (chime or noise swell), then the voice at the target level; 0.5 s fade-out. Conflicts with CLAUDE.md §5.2: owner decision; O6 option 3 (chime under the kept fade) avoids the conflict | pre-roll 0.5–3 s | weak–moderate | GOL19 ramp, KON24 ~3 s fade, NIE17 phasic effects; startle reflex not at issue at these levels (PUT90) |
| Prompt wording | keep MIT verbatim. Pre-session instructions define content broadly ("thoughts, images, feelings, anything"). Optional "the last thing" variant | — | strong (MIT replication) | P20, SR23-SM, SIC13, LAC21 |
| State question | "And were you asleep? Awake, halfway or asleep" **after** the report; logged as a biased label | — | moderate | P20; FS26 demand-effect caveat |
| Recording start | mic open ~0.5 s before prompt end (ring buffer); report window starts at prompt end | — | moderate | CODE-web `onended`; LAC21, SIC17 immediacy |
| No-speech timeout | **20 s**, then one soft re-prompt ("Anything at all?"), then 10 s more; log "no report" | 10–30 s | weak | MIT iOS minimum 30 s; SM19 "can you tell me more?" |
| End-of-speech silence | **8 s** | 4–15 s | moderate | MIT iOS 8 s; halting speech (P20 §3.1; KOE24 pause profile). CLAUDE.md's 4 s is GUESSED |
| Max report length | **120 s** | 45–300 s, or unlimited | moderate | MIT iOS 120 s; SR23 unlimited; reports average 9–14 words (SR23 Table 1) |
| Post-onset delay | keep MIT's variable 1–5 min (P20 does not report the distribution; uniform is OpenTDI's choice) as "MIT standard". Add a **"direct" preset of 60–120 s** | 15 s – 5 min | moderate | WAM10: direct incorporation halves after 120 s; P20 / SR23 1–5 min |
| Re-arm refractory after the re-incubation cue | keep **120 s** (GUESSED) before detection may fire again | 60–420 s | weak | No source gives a refractory. MIT iOS `waitForOnsetTime` 240 s is the opposite, a maximum wait that forces the next onset by timer (`FlowViewController.swift:596-600`), with sensor detection only on the first loop (line 479); it supports a no-onset fallback, not a refractory. Dormio Light 7 min re-sleep; SIC17 120 s minimum in stage |
| Session shape | **45 min time box**, stop at **6 awakenings**; hard cap 10 per hour | 30–60 min; 3–8 | moderate–strong | SR23 ~5 per 45 min; Dormio Light 4; WAM10 max 10/h |
| End of session | gong or alarm, then a written or voice summary prompt | — | moderate | CODE-web gong ×3; FS24 typed-report fallback |

### 8.2 Numbered recommendations

1. **Treat the theme cue and the wake prompt as two calibrated levels, not one "target volume".** The theme
   cue should be heard without waking the user, as in TMR (35–50 dB SPL, at or ≤ 5 dB over the bed). The
   wake prompt must rouse from N1. Store both as offsets from a per-user threshold T measured at bedtime.
   *Strength: moderate for the split (ANT12, CRE15, WHI22a/b, KON21, KON24, SM19 p.71); weak for the exact
   offsets.*
2. **Calibrate in the app at bedtime, and do not force system volume.** Take a 30-second ascending-tone or
   own-voice threshold test while the user lies in their sleep position. Log the system volume index,
   route and app gain. If the volume index or route changes before the session, re-run the test.
   SleepStim's forced max volume (`HTMR :229-232`) shows the control idea but is a blast risk. Keep the
   existing hard cap. *Strength: moderate (KON24 / WHI22a methods; safety).*
3. **Add automatic back-off on arousal for theme cues.** If the watch reports a movement burst or an HR
   jump within ~10 s of a theme cue, lower the next theme cue by 3–6 dB. Do not replay it in that loop.
   This is SleepStim's and KON24's logic, using the sensors OpenTDI already has. *Strength: moderate (WHI22a,
   GOL19 harm without it, KON24).*
4. **Move the 2 s fade off the words.** Put it on a 1.5–2 s pre-roll (a soft chime or the noise bed
   swelling), then speak at full target level, with a 0.5 s fade-out. A fade across "You're falling
   asleep" lowers its first syllable by tens of dB. *Strength: weak–moderate (GOL19, KON24, NIE17; PUT90
   for why reflex startle is not the issue).*
   Conflicts with CLAUDE.md §5.2 ("All voice prompts must fade in over a 2,000ms logarithmic volume
   curve"): owner decision. The option that keeps the owner's fade is [SPEC] O6 option 3, a soft chime
   under the fade.
5. **Offer an optional noise bed.** Use pink or white noise at a user-set comfortable level. It anchors
   cue loudness (WHI22a), masks household noise (WHO09: the awakening threshold is about 35 dB(A) LAmax),
   and keeps Bluetooth and USB paths active, so the −75 dBFS carrier question disappears for those users.
   It stays off by default, because MIT used a quiet room. *Strength: moderate (TMR practice); the Bluetooth
   point is inference.*
6. **Keep MIT's wording and put the content definition in onboarding.** Instructions should say "anything
   at all: thoughts, images, sounds, feelings, even fragments" (SR23-SM §1.1; LAC21; Rechtschaffen 1973 via
   P20). Keep the "And were you asleep?" step after the report, and mark it as a demand-biased label
   (FS26). *Strength: strong for the wording; moderate for the bias note.*
7. **Start capture immediately, and protect the first seconds.** Arm the recorder (ring buffer) before the
   prompt ends. Start the report window at prompt end. Insert no beeps, haptics or screens between prompt
   and report. *Strength: moderate (LAC21 61.7 % vs 22.5 %; SIC17 "short temporal lag"; WYA94/97;
   CODE-web).* The seconds-scale decay rate itself is **unsourced**.
8. **Replace "4 s silence / 45 s max" with MIT iOS values: 8 s silence, 120 s cap.** Add a 20 s no-speech
   timeout with one soft re-prompt. Reports average 9–14 words (SR23 Table 1), so the cap almost never
   binds. The silence timeout is the parameter that loses data. Keep the floor-relative detector from
   android_audio_stt_notes §8.2, with `hangoverMs = 8000`, `maxMs = 120000`, `noSpeechTimeoutMs =
   20000`. *Strength: moderate.*
9. **Add a "direct incorporation" delay preset of 60–120 s next to MIT's 1–5 min.** WAM10 found direct task
   imagery highest at ≤15 s and "reduced by more than half" after 120 s. OpenTDI's success metric is a
   direct-word count (rec. 11), so shorter delays should score better. Caveat: WAM10 used a pre-sleep game,
   not an audio theme cue. Too short a delay risks catching wake. WAM10 notes that Nightcap staging is imprecise and
   that many Nightcap "Wake" reports came from early stage 1 (Results, Fig. 6). *Strength: moderate. A
   good A/B test for OpenTDI's own data.*
10. **Bound the session: a 45-min time box, at most 6 awakenings, at least 240 s between a re-incubation cue
    and the next trigger, never more than 10 per hour.** Show post-session advice: no driving or
    safety-critical tasks for about 30 min (HAY05, TAS00). Show the screening list in onboarding (FS24),
    with an explicit sleep-paralysis note (TAK92). *Strength: moderate–strong for the counts (SR23, FS24,
    WAM10, CODE-iOS); moderate for the safety notes (abstract-level sources).*
11. **Score incubation on the device exactly as SR23 did.**
    - Each theme gets a user-editable *direct lexicon*: the theme word, its plural, and its parts. For
      "tree" that is tree, leaf, branch, root and forest.
    - Per report, compute `directRefs` (count of lexicon tokens, case- and plural-insensitive, as IDE24's
      regex) and `hasDirectRef`. Per session, compute `% reports with ≥1 ref` and `refs per report`. Add
      `wordCount` (ELC21) and `reportDurationS`.
    - Also compute the same counts for a **control lexicon** (a non-incubated object, as FS24's Fork
      against Tree and WAM10's control reports). That gives a per-user false-positive baseline.
    - *Strength: strong for matching MIT's metric (P20, SR23, FS26); moderate for the control lexicon
      (FS24, WAM10).*
12. **Do not auto-score indirect incorporation as a headline number.** Human raters reached κ = 0.17
    (FS24). If added, run a small on-device embedding or LSA similarity (ALT17 suggests LSA copes with small
    corpora) and label it "experimental, unvalidated". *Strength: moderate.*
13. **The audio is the record; the transcript is derived.** Keep the WAV/M4A always. Trim leading and
    trailing silence before STT, and never send prompt audio to STT. Store recogniser confidence, and let
    the user correct transcripts in the morning. Flag a transcript when STT text appears over segments the
    silence detector called non-speech, because that is the hallucination pattern (KOE24). Write audio
    atomically, so files are never lost as in FS24. *Strength: moderate (KOE24, FS24); no dream-specific
    ASR validation exists.*
14. **Validation method for OpenTDI's own pilot data.** Treat each of these as a small experiment:
    - Blind-score 20–30 reports by hand with SR23's rule and compare them against the on-device counts.
    - Compare incorporation for the 60–120 s and 1–5 min delays.
    - Log cue level against arousal (movement or HR within 10 s of a cue) to tune the back-off.
    - Record the state self-rating for each awakening.

    *Strength: method recommendation (WAM10, FS24, WHI22a analyses).*
15. **Bluetooth earbud guidance in the app.**
    - Recommend the phone speaker or a headband speaker.
    - If earbuds are chosen, show a one-time test: lie down for 10 min, then play a cue. That catches buds
      with sleep modes or auto-pause (SC-A20 "Smart Switch"; Kokoon "Auto Pause", NOT VERIFIED) and buds
      that cannot stream at all (Bose Sleepbuds II, NOT VERIFIED).
    - Play mono to both ears, because a pillow can block one bud (SC-A20 FAQ).
    - *Strength: anecdotal / marketing.*
16. **Licensing note for GPL-3.0.** HTMR (SleepStim) and TLRA are MIT-licensed. Their logic can be adapted
    with attribution. IDE24 has **no licence**, so read it for ideas only and copy no code. *Strength: FACT
    (GitHub licence API).*

---

## 9. What I could not verify or access

- No MIT source gives cue dB, a fade or a device. That gap is real, not a search failure (existing notes G3).
- Seconds-scale forgetting of hypnagogic reports after awakening: no study found.
- Abstract-only sources, with no numbers taken beyond their abstracts: WYA94, WYA97, VAL17, ROW98, NOR09,
  HAY05, TAS00, TAK92, ZEP84, BUS94, ERM10, PUT90, BLU18, FOG20, ALT17, SPE16, KOE24.
- Title only, content NOT VERIFIED: Rechtschaffen, Hauri & Zeitlin 1966; Dement & Kleitman 1957;
  Goodenough et al. 1965 (abrupt against gradual awakening); Cohen & Wolfe 1973 (seen only via secondary
  descriptions).
- Blocked: the IJoDR (Heidelberg) papers, Parke & Horton 2009 and the TLRA paper, sat behind an Anubis
  bot check. I did not try to get around it.
- Official product pages: Bose (redirect or 404) and Ozlo (HTTP 429) could not be fetched. Their claims
  above are secondary.
- WHO09 came from a third-party mirror of the PDF (University of Navarra), because iris.who.int returned
  only a JavaScript app. Page numbers are the printed numbers.
- SleepStim's "0.02" intensity is a `MediaPlayer` gain at maximum system volume, inferred from HTMR code.
  The paper text I read does not state its units.
- Ngo et al. 2013 (*Neuron*) pink-noise levels were not read (not open access), so they are not cited.
