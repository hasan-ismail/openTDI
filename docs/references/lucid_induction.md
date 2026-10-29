# Inducing lucid dreams: what the evidence says, and what it means for OpenTDI

Source audit 2026-10-28: non-academic sources replaced with primary papers or removed.

The verified sleep-stage, dreaming and safety reference is sleep_and_dreams.md (2026-10-28); where the two differ, sleep_and_dreams.md and this audit's corrections win.

Research notes, 2026-10-28. Lucid dreaming (knowing you are dreaming while you dream) is OpenTDI's main goal. MIT's
Dormio, which OpenTDI started from, cues at **sleep onset** to steer what a dream is *about*. The best evidence for
**lucidity** comes from cueing in **REM sleep late in the night**, after training the cue before sleep.

## 1. Targeted lucidity reactivation (TLR), Northwestern University (Paller lab)

- **Lab version** (Carr, Konkoly, Mallett, Edwards, Appel & Blagrove 2023, *Psychology of Consciousness* 10(4):413–430;
  https://doi.org/10.1037/cns0000227): a sound/light cue is paired with lucidity training before sleep, then replayed
  in REM under polysomnography. About 50% of participants had a signal-verified lucid dream in one nap (7/14 and 7/14
  cued vs 2/12 uncued), many with little prior experience **[moderate; one lab, n=40]**.
- **Home version, phone only** (Konkoly et al. 2024, *Consciousness and Cognition* 125:103759; PMC11542932):
  - *Training at bedtime*: one cue per person (three rising beeps 400/600/800 Hz, ~650 ms, or a 1 s violin note). The
    first 4 cues are each followed by spoken guidance to become lucid: notice your thoughts, body and breath, and
    "remain lucid, critically aware", then a 45 s pause. Then 12 more cues over ~15 min at 45–120 s intervals while
    falling asleep. Nights 2–7 used a shorter training.
  - *Cues at night*: start **6 h after training** (no sleep staging at all); one cue every **20–40 s**, starting at
    the person's hearing threshold and rising 0.16% per cue.
  - *Movement*: cueing pauses until **5 min** after movement stops (1 min in experiment 2), detected by the phone's
    accelerometer on the mattress. If a cue wakes the person, cueing pauses for **45 min** after they go back to sleep.
  - *Results*: experiment 1 (n=19) lucid dreams per week rose from 0.74 to 2.11 (p=.007); 14 lucid dreams were
    attributed to cues **[weak; pre/post, no control group]**. Experiment 2 (blinded controls, n=112 on nights 1–2)
    showed a smaller but significant effect of trained cues over untrained cues **[moderate; blinded controls]**. Cues
    caused more movement-pauses than no cues (0.19 vs 0.10 per cue): cues do wake people sometimes. Lucidity was
    self-reported, and only 50 of the 416 people who used the app at least once used it all 7 nights and met the
    dream-recall criterion **[weak overall; self-report, high drop-out]**.
- **Multi-centre replication with 2-channel EEG** (Picard-Deland C, Jafarzadeh Esfahani M, … Carr M, Dresler M 2026,
  v2; bioRxiv, preprint, not peer reviewed; https://doi.org/10.1101/2024.06.21.600133): an early version reported
  signal-verified lucid dreams in 65% (Netherlands) and 45% (Italy) of the first 40 participants. **The final version
  (21 Aug 2026, N=60) found no benefit of the REM cues**: 38.3% vs 28.3% of naps; the cues disrupted sleep in 23.7% of
  cued periods (see §5) **[preprint]**. The p=.219 and the REM cut from 16.2 to 12.7 min were not checked against the
  full text.

## 2. Cognitive techniques (International Lucid Dream Induction Study, Aspy 2020, *Front. Psychol.* 11:1746)

- 355 people, one week each. **WBTB**: alarm 5 h after going to bed, do the technique, go back to sleep.
- **MILD**: recall the last dream, then repeat "next time I'm dreaming, I will remember I'm dreaming" while picturing
  going back into the dream and noticing it is a dream. **SSILD**: cycle attention through sight, hearing and body
  (4 fast cycles, then 4–6 slow cycles of ~20 s per step).
- Lucid on 15.8% of nights in week 2 vs 5.3% in week 1; MILD+WBTB 18–20%, SSILD+WBTB 17%; differences between
  techniques were not significant.
- **Predictors**: good dream recall, and falling asleep **within 10 min** after the technique (18.3% vs 11.1%).
- **Daytime reality testing added nothing** (12.1% with it vs 19.4% without, p=.052): not worth building.

## 3. Can the watch find REM?

- Offline sleep staging against polysomnography, by source and type:
  - **Galaxy Watch 5** (the relevant figure for this watch): REM sensitivity 0.63, REM F1 0.60, in a peer-reviewed
    multicentre study (Lee et al. 2023, *JMIR mHealth and uHealth* 11:e50983) **[moderate]**.
  - Heart rate and HRV from a **chest ECG** (Firstbeat Bodyguard 2), not a wrist device: REM sensitivity 0.60
    (Kuula & Pesonen 2021, *JMIR mHealth and uHealth* 9(2):e24704) **[moderate]**.
  - An optical wrist device: REM sensitivity 0.89, in a conference paper by the device's own developers (Renevey et al.
    2017, *IFMBE Proceedings* 65) **[weak]**.
- No study found for **real-time** wrist REM detection driving cues. The home TLR study did not detect REM at all: it
  used time (6 h) plus movement pauses. Its benefit was self-reported only; a verified lab test (preprint) found none.
- So OpenTDI should do the same: time of night + the watch's stillness (REM comes with muscle atonia, so a still
  body is necessary, not sufficient) and pause on movement. HR and HRV are logged for later analysis, not used to gate.

## 4. What this means for OpenTDI

1. Keep the **sleep-onset (Dormio) mode**, but gate it on stillness (owner decision 2026-10-28): it is good for dream
   incubation and short naps.
2. Add a **"Lucid night" (TLR) mode**: training with a distinct cue at bedtime, then cues from ~6 h after training,
   every 20–40 s, quiet and slowly rising, paused for 5 min after any movement and 45 min after a wake-up. The watch's
   stillness signal is a better movement detector than a phone on the mattress.
3. Optionally a **WBTB + MILD** step: the watch vibrates at ~5 h, the phone or watch guides MILD (spoken), then the
   late-night TLR cues follow once the user is still again.
4. Keep cues soft and short: in the studies, cues that are too loud wake people, and waking costs 45 min of cueing.
5. Support **dream recall**: a quick morning dream report (recording + transcription, already planned) helps both
   lucidity and the user's own tracking.
6. Do not build daytime reality-check reminders.

Safety: lucid-dream induction disturbs sleep. The README's advice (talk to a doctor first with a history of psychosis,
narcolepsy, frequent sleep paralysis or other sleep disorders) applies to every mode. Galantamine and other
supplements mentioned in the literature are out of scope for this app.

## Sources

- Carr M, Konkoly K, Mallett R, Edwards C, Appel K, Blagrove M 2023. Combining presleep cognitive training and
  REM-sleep stimulation in a laboratory morning nap for lucid dream induction. *Psychology of Consciousness: Theory,
  Research, and Practice* 10(4):413–430. https://doi.org/10.1037/cns0000227
- Konkoly KR et al. 2024. Provoking lucid dreams at home with sensory cues paired with pre-sleep cognitive training.
  *Consciousness and Cognition* 125:103759. https://pmc.ncbi.nlm.nih.gov/articles/PMC11542932
- Picard-Deland C, Jafarzadeh Esfahani M, … Carr M, Dresler M 2026 (v2, 21 Aug 2026). Inducing lucid dreaming with
  multisensory stimulation: a preregistered multi-center study. bioRxiv, preprint, not peer reviewed.
  https://doi.org/10.1101/2024.06.21.600133
- Aspy DJ 2020. Findings from the International Lucid Dream Induction Study. *Frontiers in Psychology* 11:1746.
  https://doi.org/10.3389/fpsyg.2020.01746
- Mota-Rolim SA et al. 2019. Portable devices to induce lucid dreams: are they reliable? *Frontiers in Neuroscience*
  13:428. https://pmc.ncbi.nlm.nih.gov/articles/PMC6517539/
- Lee T et al. 2023. Accuracy of 11 wearable, nearable, and airable consumer sleep trackers: prospective multicenter
  validation study. *JMIR mHealth and uHealth* 11:e50983. https://pmc.ncbi.nlm.nih.gov/articles/PMC10654909/
- Kuula L, Pesonen AK 2021. Heart rate variability and Firstbeat method for detecting sleep stages in healthy young
  adults: feasibility study. *JMIR mHealth and uHealth* 9(2):e24704. https://pmc.ncbi.nlm.nih.gov/articles/PMC7889416/
- Renevey P, Delgado-Gonzalo R, Lemkaddem A, et al. 2017. Optical wrist-worn device for sleep monitoring. EMBEC & NBC
  2017, *IFMBE Proceedings* vol 65 (conference paper). https://doi.org/10.1007/978-981-10-5122-7_154 (CSEM copy:
  https://yoda.csem.ch/items/b20b1e4e-b8ba-4df6-8497-6c84ef608d95)

## 5. Second research round (2026-10-28): WBTB, cues, alarms, overall evidence

Four parallel literature searches and a synthesis that opened each surprising source; findings it could not verify are
listed in 5.5 and were not used. Where this section and §1–4 disagree, this section is newer.

### 5.1 Evidence-ranked interventions

| Intervention | Expected benefit | Evidence | Phone/watch feasibility |
|---|---|---|---|
| WBTB at about 6 h, then a guided dream report and MILD/SSILD, then back to bed | At home, 18% of WBTB nights lucid vs 6% of other nights (Schredl 2020, n=50). On WBTB+MILD placebo nights, 14% of participants were lucid (LaBerge 2018, n=121). In the lab with 60 min awake: 53–55% self-reported, 27% signal-verified (Erlacher & Stumbrys 2020) | One RCT arm plus small lab and home studies | High: a watch alarm and a spoken TTS script |
| WBTB with the technique left out | 4.3% of nights, no better than chance (Klofat 2024, n=138). Reading or games while awake: 9% and 0% (Erlacher 2020) | Online and lab | Do not offer a bare alarm |
| Cue trained before sleep, then replayed in later sleep (TLR) | Lab nap: 50% vs 17% verified (Carr 2023, 7/14, 7/14 vs 2/12). **Correction for lucid_induction.md:** the final multi-centre preprint (21 Aug 2026, N=60) found no benefit of REM cues: 38.3% vs 28.3% of naps. Cues disrupted sleep in 23.7% of cued periods. Its p=.219 and the REM cut from 16.2 to 12.7 min were not checked against the full text | Mixed: Carr **[moderate; one lab, n=40]**; multi-centre **[preprint]** | High: watch speaker; the level must be calibrated |
| Falling back asleep quickly | Lucid on 18.3% of nights when asleep within 10 min vs 11.1% (Aspy 2020). The 5-min cutoff was not significant | Observational | High: log the gap from "back in bed" to stillness |
| Spoken report at every alarm | Dreams recalled per day correlate with lucid rate (rs=.31). Prior lucid frequency correlates too (r=.38) (Aspy 2020) | Observational | High (already built) |
| Gentle, name-calling wake | Being called by name gives +10.1% recall vs an alarm tone (p=.046, NREM awakenings only). Gentle waking gives +7% (via the Stucky 2025 review; primary studies not checked) | Review | High: TTS |
| "Lucid snooze" | Snooze use goes with ever having a lucid dream (OR 2.52, rho=.22, n=84) (Smith & Blagrove 2015) | **[weak; retrospective online survey, correlational]** | High; label it experimental |
| Wrist vibration as the only cue | 2 verified lucid dreams in 27 wrist/ankle vibrations (7.4%); finger vibration gave 0/21 (Paul 2014) | **[weak; pilot]** | Possible but weak; pair it with the tone |
| Do not build: reality-check cues, meditation course, smart wake window | Spoken "You are dreaming" cue with reality checks: 3/11 verified (Peters 2024) **[weak; n=11]**. MBSR course: p=.92 (Baird 2019). Multimodal smart alarm: no effect on alertness, sleepiness or mood, and sleep efficiency fell to 83.97% from 86.65% (Campanella 2024) | | |

### 5.2 Recommended defaults

- **WBTB time:** 6.0 h after the watch detects sleep onset (range 4.5–6.5 h), with a fixed clock time as fallback. The 6 h lab study got 53–54% self-reported lucidity. The 4.5 h follow-up got 0–26%, and its authors say the earlier waking "seems to reduce" success. That comparison is across studies, so the evidence is thin.
- **Awake period:** 30 min by default (20–60). Waking for 30 min or more predicted success (β=.11). Every 10 min of MILD helped (β=.09), but extra minutes beyond that did not (p=.80) (LaBerge 2018). 60 min beat 30 min in the lab (54% vs 36%), but that was not tested statistically. The app should guide, in order:
  1. Spoken dream report.
  2. Review of dream signs and setting the intention.
  3. A few pairs of the night cue plus lucidity guidance (retraining the cue).
  4. MILD or SSILD lying down, lights off, as the last step.
- **Return to sleep:** keep the screens black and the guidance audio-only. Log the time from "back in bed" to stillness. After 60 min awake, people took 31.5 min on average to fall asleep (Erlacher 2020). If the sleeper usually takes more than 20 min, suggest a shorter awake period (judgment).
- **Late-night cues:**
  - Start only once the sleeper is still again, and no sooner than about 40 min after falling back asleep (the watch's proxy: the start of that still bout), not 40 min after getting back into bed. In the lab, REM came back 42.1 ± 24.7 min after sleep onset, and falling back asleep had taken 31.5 ± 26.0 min (Erlacher 2020). Counted from getting back into bed, that is roughly 70 min on average (inference from the two means).
  - Cue: the same short tone used in training (400/600/800 Hz, about 650 ms), optionally with one weak vibration pulse.
  - Spacing: one cue every 20–30 s, at most 10 per still bout. The Netherlands lab capped cues at 10 per REM period (Konkoly 2021). In Carr's study, people who became lucid had heard 5.7 cues on average.
  - Loudness: set in bed with the watch in the usual sleeping position, at "easily notice but won't wake me" (Carr). That is about whisper level, 40–45 dB SPL (Konkoly 2021).
  - Raise the level only in tiny steps. The multi-centre study (preprint) used 5 dB steps, and its cues disrupted sleep in 23.7% of cued periods.
  - After each night, nudge the level down after a wake-up and up after a night with no cue in any dream. DreamLight needed a median of 6 nights to calibrate (LaBerge & Levitan 1995) **[weak; n=14, tested by the device's own maker]**.
  - Keep the 5-min pause after movement and the 45-min pause after a wake-up.
- **Sleep-onset spoken cues:** recommend the user's own recorded voice. Unfamiliar voices caused more K-complexes and microarousals (relative effect 0.76 vs 0.24, n=17, NREM) (Ameen 2022). Applying that to lucidity is an inference.
- **Wake-up tones:** keep all of them distinct from the lucidity cue.
  1. **Melodic (default):** a melodic sound went with less self-reported grogginess (V=.37, n=50, correlational).
  2. **5-min gentle swell from -30 to 0 dB, then the tone:** better morning mood (p=.007, n=18), but the gradual alarm also took longer to stop (p=.007). Sleepiness and snooze use did not change (Yang, Cera & Pauletto 2026, conference paper) **[weak; n=18, single study]**.
  3. **Spoken wake ("[Name], keep your eyes closed, replay your dream"):** best for dream recall (+10.1%, via review).
  4. **Low 520 Hz square-wave beeps (escalation):** 400 Hz and 520 Hz square waves both had the lowest waking thresholds (n=39) (Bruck, Ball, Thomas & Rouillard 2009). That high-pitched alarms of 3 kHz or more do worse comes from earlier work the paper cites, not from its own data.
  5. **Vibration only (WBTB default):** woke healthy sleepers on 94% of alarms, with a median of 52 s (IQR 37–80). Only 13 of 18 woke to every alarm, so it must escalate to sound.
- **Volume ramp:** the 5-min swell is an option. Plain tones fade in over 20–30 s (judgment; no study has tested ramp length).
- **Ring and snooze limits:**
  - The alarm escalates until a deliberate dismiss. A vibration-only alarm adds sound after about 2 min.
  - Snooze: 9 min each, at most 3. A 30-min snooze did no harm in habitual snoozers: cognition improved or did not change, and snoozing cost about 6 min of sleep (Sundelin 2023, n=31). In real-world app data, a snoozed morning averaged 10.8 min of total snooze time over 2.4 presses, not one 10.8-min interval (Robbins 2025; the app maker Sleep Cycle co-authored the paper and paid its publication fee).
- **Smart wake window:** no. The one controlled study found no benefit. Wrist sensors cannot stage sleep in real time (see lucid_induction.md §3).
- **Time to wake up:**
  - Main measure: seconds from alarm start to the first deliberate tap.
  - Also log: ramp start, the time it reached full volume, first movement above the stillness threshold, final dismiss, snooze count, and whether the user woke before the alarm.
  - Keep morning and WBTB alarms separate. Compare each against the user's own rolling median, because no norm exists.

### 5.3 Morning protocol (also used at WBTB)

1. Dismiss with one deliberate gesture (bezel or hold), with no puzzles.
2. Voice prompt: "Stay still, eyes closed, replay the dream." Allow 60–120 s. High recallers stay awake about 1.9 min after waking vs 0.95 min for low recallers. That a dream needs about 2 min of waking to be stored is the authors' inference, not a test (Vallat 2017).
3. "What were you dreaming?" records from the watch mic and is transcribed later.
4. One tap each for:
   - Dream recalled (Yes / Vague / No).
   - Lucid (Yes / No / Unsure).
   - Cue heard in the dream.
   - Cue woke me.
   - Refreshed (1–5).
   - Sleep paralysis.
5. The cue-level nudge and the lucid-rate tracking come from these answers. Keep 1–2 weeks of baseline before switching protocols on.

### 5.4 Limits and safety

- **WBTB:** 1–3 nights a week by default. Once a week tripled the per-night rate (Schredl 2020). Seven nights in a row did not lower sleep quality (3.5 to 3.4, p=.15), but success varied by day with a slight decline (p=.001) (Aspy 2020). No study sets a safe maximum. On WBTB nights, suggest a wake alarm at least 30 min later.
- **Cue nights:** cues cost REM. Offer a blinded cue/no-cue night option so users can test whether cues help them. In the DreamLight study, cue nights averaged 0.30 lucid dreams per night vs 0.09 (LaBerge & Levitan 1995) **[weak; n=14, tested by the device's own maker]**.
- **Adherence is the real bottleneck:** of 416 people who used the home TLR app at least once, 50 used it all 7 nights and met the dream-recall criterion (Konkoly 2024). In ILDIS, 1,618 started and 355 finished. Setup has to take one tap.
- **Cautions** (correlational evidence, not clinical contraindications): psychosis, dissociative disorders, frequent sleep paralysis, narcolepsy, insomnia.
  - Sleep paralysis and lucid dreaming share dissociation as a predictor (N=1,928) (Denis & Poerio 2017) **[weak; correlational survey]**.
  - People who deliberately tried to induce lucid dreams rose in dissociation and schizotypy over 2 months (Aviram & Soffer-Dudek 2018, n=78 undergraduates) **[weak; correlational]**.
  - Show a caution if the morning report logs sleep paralysis.
- **Do not claim:**
  - that REM cues are proven;
  - REM or "cycle-aligned" timing;
  - that the tones reduce grogginess;
  - smart waking;
  - that the lucid snooze works;
  - lighting advice (untested).
- **Realistic expectation:** about 18% of WBTB+MILD nights lucid at home (Schredl 2020, n=50). On WBTB+MILD placebo nights, 14% of participants (not nights) had a lucid dream (LaBerge 2018).

### 5.5 Unverified, dropped

I could not open these, so they are not used above:
- Korres 2018 (vibration patterns)
- McFarlane 105 BPM melody pilot
- Cajochen 2024 (cycle length)
- Barbato (REM density)
- Scheer 2008
- Smith 2018 (voice alarm)
- Matsangas (wrist reaction-time test)
- Kaida / UVA (heart-rate surge at waking)
- Sano (EDA during sleep)
- Walch 2019
- Gooley 2011 (light)
- Wolk 2024
- Tan & Fan 2023
- Stumbrys 2012
- Saunders 2016
- Vallat & Ruby 2019
- Schredl 2019 (voice vs written reports)
- Peters (haptic/GVS)

Schredl 2020 (cited earlier as 2021) was first read only through a news summary; the 2026-10-28 source audit matched its numbers to the paper.

Removed in the source audit: the medRxiv "consumer devices under-count REM by 50–70%" preprint (Suraparaju & Udhayakumar 2025). It is unpublished, its abstract contradicts itself, and its full text could not be opened. §3 now uses Lee 2023.

### 5.6 Sources

- Erlacher & Stumbrys 2020, Wake Up, Work on Dreams, Back to Bed: https://pmc.ncbi.nlm.nih.gov/articles/PMC7332853/
- Erlacher et al. 2022, Earlier sleep interruption: https://pmc.ncbi.nlm.nih.gov/articles/PMC9149976/
- LaBerge S, LaMarca K, Baird B 2018. Pre-sleep treatment with galantamine stimulates lucid dreaming. *PLoS One* 13:e0201246. https://doi.org/10.1371/journal.pone.0201246
- Schredl M, Dyck S, Kühnel A 2020. Inducing lucid dreams: the wake-up-back-to-bed technique in the home setting. *Dreaming* 30(4):287–296. https://doi.org/10.1037/drm0000152
- Klofat B, Steingen U, Henriques Abrantes V, Metz C, Pabst KL, Palme JJ 2024. Luzide Trauminduktion mittels Wake-up-back-to-bed stand-alone im Home-Setting: ein Onlineversuch (in German). *Somnologie* 28:3–12. https://doi.org/10.1007/s11818-024-00453-8 (Düsseldorf copy: https://docserv.uni-duesseldorf.de/servlets/DocumentServlet?id=67705)
- Aspy DJ 2020. Findings from the International Lucid Dream Induction Study. *Frontiers in Psychology* 11:1746. https://doi.org/10.3389/fpsyg.2020.01746
- Picard-Deland C, Jafarzadeh Esfahani M, … Carr M, Dresler M 2026 (v2, 21 Aug 2026). Inducing lucid dreaming with multisensory stimulation: a preregistered multi-center study. bioRxiv, preprint, not peer reviewed. https://doi.org/10.1101/2024.06.21.600133
- Carr M, Konkoly K, Mallett R, Edwards C, Appel K, Blagrove M 2023. Combining presleep cognitive training and REM-sleep stimulation in a laboratory morning nap for lucid dream induction. *Psychology of Consciousness: Theory, Research, and Practice* 10(4):413–430. https://doi.org/10.1037/cns0000227 (Swansea copy: https://cronfa.swan.ac.uk/Record/cronfa53997)
- Konkoly KR et al. 2021. Real-time dialogue between experimenters and dreamers during REM sleep. *Current Biology* 31:1417–1427. https://pmc.ncbi.nlm.nih.gov/articles/PMC8162929/
- Konkoly KR et al. 2024. Provoking lucid dreams at home with sensory cues paired with pre-sleep cognitive training. *Consciousness and Cognition* 125:103759. https://pmc.ncbi.nlm.nih.gov/articles/PMC11542932
- LaBerge S, Levitan L 1995. Validity established of DreamLight cues for eliciting lucid dreaming. *Dreaming* 5(3):159–168. https://doi.org/10.1037/h0094432
- Paul F, Schädlich M, Erlacher D 2014. Lucid dream induction by visual and tactile stimulation: an exploratory sleep laboratory study. *International Journal of Dream Research* 7(1):61–66. https://doi.org/10.11588/ijodr.2014.1.13044
- Peters E, Erlacher D, Rühl M, Schädlich M 2024 (online; 2025 issue). Sounds of lucidity: auditory stimulation for triggering reality checks in lucid dream induction. *Dreaming* 35(Suppl 1):S41–S57. https://doi.org/10.1037/drm0000282 (Bern copy: https://boris-portal.unibe.ch/entities/publication/4390b1b9-e40c-4b8c-9f15-b0c4121d223d/full)
- Ameen et al. 2022, Unfamiliar voices in sleep: https://pmc.ncbi.nlm.nih.gov/articles/PMC8896625/
- Baird B, Riedner BA, Boly M, Davidson RJ, Tononi G 2019. Increased lucid dream frequency in long-term meditators but not following MBSR training. *Psychology of Consciousness* 6:40–54. https://pmc.ncbi.nlm.nih.gov/articles/PMC6490164
- Attali et al. 2020, Vibrating bracelet: https://pmc.ncbi.nlm.nih.gov/articles/PMC7783873/
- McFarlane et al. 2020, Melodic alarm survey: https://pmc.ncbi.nlm.nih.gov/articles/PMC6986749
- Yang K, Cera A, Pauletto S 2026. The SoundRise alarm. *Forum Acusticum 2026* proceedings (conference paper; peer-reviewed by at least 2 reviewers, per the paper). https://dael.euracoustics.org/confs/fa2026/data/articles/000156.pdf
- Bruck D, Ball M, Thomas I, Rouillard V 2009. How does the pitch and pattern of a signal affect auditory arousal thresholds? *Journal of Sleep Research* 18(2):196–203. https://doi.org/10.1111/j.1365-2869.2008.00710.x (Victoria University copy: https://vuir.vu.edu.au/2059/)
- Campanella et al. 2024, Smart alarm: https://pmc.ncbi.nlm.nih.gov/articles/PMC10969141/
- Stucky 2025, Awakenings and dream recall (review): https://pmc.ncbi.nlm.nih.gov/articles/PMC12053782/
- Vallat R, Lajnef T, Eichenlaub JB, et al. 2017. Increased evoked potentials to arousing auditory stimuli during sleep: implication for the understanding of dream recall. *Frontiers in Human Neuroscience* 11:132. https://doi.org/10.3389/fnhum.2017.00132
- Sundelin T, Landry S, Axelsson J 2023 (online; 2024 issue). Is snoozing losing? *Journal of Sleep Research* 33(3):e14054. https://doi.org/10.1111/jsr.14054
- Robbins R, Sääf D, Weaver MD, Gradisar M, Quan SF, Czeisler CA 2025. Snooze alarm use in a global population of smartphone users. *Scientific Reports* 15:16942. https://pmc.ncbi.nlm.nih.gov/articles/PMC12089427/ (Sleep Cycle co-authored it and paid its publication fee)
- Smith BV, Blagrove M 2015. Lucid dreaming frequency and alarm clock snooze button use. *Dreaming* 25(4):291–299. https://doi.org/10.1037/drm0000012 (Swansea copy: https://cronfa.swansea.ac.uk/Record/cronfa25493)
- Aviram L, Soffer-Dudek N 2018. Lucid dreaming: intensity, but not frequency, is inversely related to psychopathology. *Frontiers in Psychology* 9:384. https://pmc.ncbi.nlm.nih.gov/articles/PMC5875414/
- Soffer-Dudek N 2020. Are lucid dreams good for us? A call for caution (background review). *Frontiers in Neuroscience* 13:1423. https://pmc.ncbi.nlm.nih.gov/articles/PMC6993576/
- Denis D, Poerio GL 2017. Terror and bliss? Sleep paralysis, lucid dreaming, and waking life experiences. *Journal of Sleep Research* 26(1):38–47. https://doi.org/10.1111/jsr.12441 (White Rose copy: https://eprints.whiterose.ac.uk/103552)

Dropped in the source audit: Aspy et al. 2017 (NALDIS) and Gott et al. 2020 (sleep fragmentation), because nothing above cites them, and every news release and summary link. The source audit (2026-10-28) matched the Robbins, Paul 2014 and Peters 2024 numbers against the papers' records. No repo files were edited.

## 6. Practitioner reports from Reddit (2026-10-28)

**Anecdotal practitioner reports (r/LucidDreaming), not research evidence. Used only to raise questions to test, never to justify defaults or claims; §1–5 and peer-reviewed sources override.** **[anecdote]**

Three reads of r/LucidDreaming (techniques, alarms/devices, pitfalls): ~1,000 posts and 43 comment threads, via Reddit RSS and the Arctic Shift archive (Reddit's JSON API returned 403). Counts are approximate. All of it is anecdote; §1–5 outrank it.

### 6.1 Lessons

| Lesson | How widely reported | OpenTDI should |
|---|---|---|
| Nearly all lucid dreams follow a night waking plus a technique; bedtime techniques rarely work | Strong: 6 long logs put 70–100% after a waking (1lf4bhp: 99 of 101); 1 dissent | Centre Lucid nights on WBTB + one technique |
| WBTB ~5 h after *bed* (4–6 h); lucid dreams cluster around 5–6 AM | 20 of 51 posts naming a time | Keep 6 h from onset; timestamp lucid reports |
| Awake 5–20 min, tuned to how fast sleep returns; under ~10 min also fails | ~19 of 25 posts ≤20 min; 3 too-short failures | Adapt awake time to measured latency |
| 2–4 WBTB nights a week; WBTB insomnia can snowball into sleep anxiety | Consistent among regulars | Active and light nights; guard sleep |
| MILD = replaying a recent dream and its dream sign just before sleep, not a mantra. SSILD: "less is more" | ~6 guides each; 2 MILD dissents | MILD from tonight's report; SSILD audio |
| Natural wakings as "micro-WBTBs": don't move, eyes closed | 22 posts; a 250-dream log credits most success to it | Opt-in guidance at natural late wakings |
| Touchless alarms that stop themselves; dismissing "wakes me up more" | ≥6 threads + ~12 of 100 recent alarm posts | Experimental only, blinded |
| Watch vibration often fails; people dismiss it and fall back asleep | 8 of 100 alarm posts | Proof of waking at WBTB |
| Untrained cues (masks) are woven into the dream (lights → police car) or wake you | ~12 of 15 first-hand mask reports negative | No cues without training; ask if the cue appeared disguised |
| Fear of sleep paralysis (SP) stops many; SP often features one's name called; a name in a dream cue woke one user | 25 of 100 SP posts; 4 name reports; 1 anecdote | Name only on real wake alarms; no WILD by default |
| False awakenings are common after WBTB/SSILD, even dreamed journaling | 41 of 100 first-lucid posts | Nose-pinch check at every waking prompt |
| Quitters stop after 3–10 nights; regulars say ≥30 days per technique; dry spells are normal | ~half of 24 "give up" posts | 30-night blocks; recall as the main chart |

### 6.2 Versus the literature

**Agrees:** WBTB needs a technique (Schredl 2020, Klofat 2024); falling asleep fast predicts success (ILDIS); untrained cues fail (TLR); vibration alone is unreliable (Attali 2020); recall tracks lucidity; no daytime reality-check reminders; 1–3 WBTB nights a week.

**Differs:**
- **Awake time:** Reddit's 5–20 min is shorter than LaBerge's ≥30 min. Start at 30; let latency move it.
- **WBTB time:** Reddit's ~5 h counts from bedtime; Erlacher 2022 found 4.5 h worse. Keep 6 h from onset.
- **SSILD counts:** the author's tutorial (4–6 fast, 3–4 slow, ≥30 s per sense) differs from §2. Use the tutorial.
- **"Lucid snooze"** on Reddit is a few-second self-stopping alarm, not a 9-min snooze. Unstudied, as is natural-waking WBTB.
- **Spoken name:** Stucky's recall gain is for waking; on Reddit a name in an in-dream cue woke the dreamer. Use the name only to wake.

### 6.3 App changes, ranked by expected value

1. **Cheap:** Lucid night = WBTB + one technique; bedtime only a short intention and cue training.
2. MILD from tonight's report: read back a line, name its odd element, ≤5 min re-entering it lucid, then silence.
3. **Cheap:** WBTB proof of waking: no report or movement within 60 s → ring again in 2 min. Distinct WBTB and morning patterns.
4. **Cheap:** Adaptive awake time: start 30 min; −5–10 after 2 nights with back-in-bed→still >20 min; +5–10 if asleep <5 min with no recall; floor 10.
5. **Cheap:** Sleep guard: skip WBTB <4.5 h after onset; warn if bed-to-alarm <8.5 h; suggest rest after 2 low "refreshed" scores or rising latency.
6. **Cheap:** The user's name only on morning and WBTB alarms, never in night cues (code comment says why).
7. **Cheap:** Waking prompts open "Before you move: pinch your nose and try to breathe."
8. **Cheap:** Report one-taps: false awakening; almost lucid; cue in dream, even disguised; WBTB natural/alarm/none; technique; unusual night (excluded from cue/no-cue analysis).
9. Progress view: recall first; lucid rate by WBTB vs not and per 30-night block; odds of the current blank run (at 15%, 5 blank nights happen 44% of the time).
10. Natural-waking WBTB (opt-in): from 4.5 h, movement after long stillness gets one soft tap; tapping runs WBTB and cancels the alarm.
11. SSILD audio guide (tutorial counts plus a 5 × 10 s version), then silence.
12. **Cheap:** SP explainer (stay calm, move one finger); after logged SP: side sleeping, WBTB at most weekly, tones-only cues.
13. Experimental still-wake: ≤8 s own-voice "stay still" + faint haptic, self-stopping, late at night when still, ≤3 a night, blinded with the tones. May just wake the sleeper.

**Don't build:** binaural beats, "lucid music", subliminals (the subreddit removes them as pseudoscience), supplements, reality-check reminders, default WILD.

### 6.4 Caveats

Lucidity is self-reported and unverified. Survivorship: success logs and top posts come from people it worked for; quitters rarely post logs. Self-selection: motivated posters stacking techniques. Samples are not random (top RSS, archive date windows). 1rujsir is flagged as promotion (only its comments used); Luzide, Lucid Waker and Totem posts are by their developers. Wiki and r/AstralProjection not reached.

### 6.5 Threads used

- **WBTB and timing:** [1lf4bhp](https://www.reddit.com/r/LucidDreaming/comments/1lf4bhp/), [1mz61ff](https://www.reddit.com/r/LucidDreaming/comments/1mz61ff/), [1vf4sfk](https://www.reddit.com/r/LucidDreaming/comments/1vf4sfk/), [1l308ob](https://www.reddit.com/r/LucidDreaming/comments/1l308ob/), [3j317d](https://www.reddit.com/r/LucidDreaming/comments/3j317d/), [phoj9l](https://www.reddit.com/r/LucidDreaming/comments/phoj9l/), [1rujsir](https://www.reddit.com/r/LucidDreaming/comments/1rujsir/), [94ou02](https://www.reddit.com/r/LucidDreaming/comments/94ou02/), [28x5fj](https://www.reddit.com/r/LucidDreaming/comments/28x5fj/), [1iep6ak](https://www.reddit.com/r/LucidDreaming/comments/1iep6ak/)
- **Techniques:** [f43h14](https://www.reddit.com/r/LucidDreaming/comments/f43h14/), [4cpb6o](https://www.reddit.com/r/LucidDreaming/comments/4cpb6o/), [18h2bi](https://www.reddit.com/r/LucidDreaming/comments/18h2bi/), [o8wc4k](https://www.reddit.com/r/LucidDreaming/comments/o8wc4k/), [58uecj](https://www.reddit.com/r/LucidDreaming/comments/58uecj/), [dm55ck](https://www.reddit.com/r/LucidDreaming/comments/dm55ck/)
- **Alarms and devices:** [fnvmk4](https://www.reddit.com/r/LucidDreaming/comments/fnvmk4/), [dm03so](https://www.reddit.com/r/LucidDreaming/comments/dm03so/), [1sq21xf](https://www.reddit.com/r/LucidDreaming/comments/1sq21xf/), [1jop4lc](https://www.reddit.com/r/LucidDreaming/comments/1jop4lc/), [1jus48p](https://www.reddit.com/r/LucidDreaming/comments/1jus48p/), [xdrjoa](https://www.reddit.com/r/LucidDreaming/comments/xdrjoa/), [15h489j](https://www.reddit.com/r/LucidDreaming/comments/15h489j/), [1fur5bl](https://www.reddit.com/r/LucidDreaming/comments/1fur5bl/), [1lwhcdz](https://www.reddit.com/r/LucidDreaming/comments/1lwhcdz/), [ecknz4](https://www.reddit.com/r/LucidDreaming/comments/ecknz4/), [1x2j1m5](https://www.reddit.com/r/LucidDreaming/comments/1x2j1m5/)
- **Pitfalls:** [1gjl0tg](https://www.reddit.com/r/LucidDreaming/comments/1gjl0tg/), [1copt2o](https://www.reddit.com/r/LucidDreaming/comments/1copt2o/), [1jj2y1v](https://www.reddit.com/r/LucidDreaming/comments/1jj2y1v/), [f5jups](https://www.reddit.com/r/LucidDreaming/comments/f5jups/), [1dtxj8c](https://www.reddit.com/r/LucidDreaming/comments/1dtxj8c/), [1dvpqyc](https://www.reddit.com/r/LucidDreaming/comments/1dvpqyc/), [1iltwas](https://www.reddit.com/r/LucidDreaming/comments/1iltwas/)

## 7. Bedtime routine evidence (2026-10-28)

Four literature searches and a synthesis that opened each surprising source; dropped findings are listed at the end. It is about falling asleep and REM, not lucidity itself. The app's checklist (mobile RoutineStore.kt) follows 7.1.

### 7.1 Bedtime routine checklist (23:30 lights-out)

| When | Item | Why (one number) | Evidence | Lucid-specific? |
|---|---|---|---|---|
| By 14:30 (energy drinks by 10:00) | Have your last coffee by 14:30. | Caffeine cost about 45 min of sleep across 24 studies. A 107 mg coffee needs at least 8.8 h before bed, a 217.5 mg pre-workout at least 13.2 h. | Meta-analysis | No, general sleep |
| All evening | Skip alcohol tonight. | Across 27 studies, about 2 drinks (≤0.5 g/kg) was enough to delay and shorten REM. | Meta-analysis | Yes (REM) |
| At least 4 h before (by 19:30) | Finish any hard workout by 19:30. | Compared with light exercise, a maximal workout ending 2 h before bed delayed sleep by 36 min and raised night HR by 3.9 bpm (14,689 people; WHOOP co-authored the study). | Observational | Partly. In one review, hard evening exercise slightly reduced REM |
| 3–4 h before (by 20:30) | Eat a normal, not greasy dinner by 20:30. | Eating within 3 h of bed went with more night waking (OR 1.61, n=793). More fat at night went with later, shorter REM (n=52). | Observational, preliminary | Partly (REM) |
| From 3 h before (20:30) | Switch to dim, warm lamps. No bright ceiling light. | Experts say at most 10 melanopic lux at the eye from 3 h before bed. Warm lamps at about 30 lux usually meet that (inference; Brown 2022 does not say this). | Expert consensus | No |
| 1–2 h before (21:30–22:30) | Take a warm bath or shower, 40–42.5 °C, for at least 10 min. | A meta-analysis (17 studies included, 13 meta-analysed) found a warm bath or shower at 40–42.5 °C, 1–2 h before bed, for at least 10 min, shortened the time to fall asleep. A 10-min, 40 °C shower right before bed also helped: 7 min faster, but only 11 people. | Meta-analysis; small RCT | No |
| By 23:00 | Take the watch off the charger at 100% and wear it snug. | Manufacturer instruction, not evidence: Samsung says wait 30 min after charging before wearing, because a warm watch skews skin readings. A full charge is for the whole night's sensor streaming (practical advice, not tested). | Manufacturer instruction, not evidence | App-specific |
| 30 min before (23:00) | Put the phone down. Screens off. | Small RCT (n=38): time to fall asleep went from 31 to 18 min over 4 weeks. Reading 4 h on an iPad cut REM by 12 min (n=12). | Small RCTs | Partly (one REM study) |
| Before lights-out | Set the bedroom cool (about 17–19 °C) and fully dark. | At 17 °C vs 22 °C, REM was 23.9% vs 22.1% of sleep (n=17), a side comparison in a sleepwear-fabric study (Shin 2016) **[weak]**. One night under room light cut the REM share (n=20). | Small RCTs | Yes (REM) |
| Last 15–30 min | Wind down with slow breathing or muscle relaxation. | The AASM insomnia guideline supports relaxation (conditional) but advises against sleep-hygiene tips alone. | Guideline (people with insomnia) | No |
| 5 min before (optional) | Write tomorrow's to-do list on paper. | Asleep in 16 vs 25 min (n=57, d=0.63). | RCT | No |
| In bed | Set tonight's dream intention, or do cue training on cue nights. | Writing about one chosen thought for 5 min doubled dreams about it: 40% vs 20% (n=90). That result is about dream content. For lucidity, use MILD/TLR (lucid_induction.md). | Weak for lucidity | Yes |
| In bed | Open the journal, ready to record, screen black. | People who recall more dreams have more lucid dreams (rs=0.38, n=178). | Observational | Yes |

**Who should skip what:**
- **Hot baths:** skip with heart disease, low blood pressure or fainting, in pregnancy, after alcohol, or when very sleepy, because of the drowning risk. A shower is the safer choice. **[general caution, no source]**
- **Persistent insomnia:** see a clinician for CBT-I. A checklist is not a treatment.
- **Alcohol item:** anyone dependent on alcohol must not stop abruptly without medical help. **[general caution, no source]** Hide this item for people who don't drink.

### 7.2 Morning and WBTB items

- **WBTB light (05:30):** use a dim lamp or a black screen, at most 10 melanopic lux, the expert limit for night tasks. Using it at WBTB is an untested inference.
- **WBTB drinks:** water only, no coffee or tea. This is an inference from the caffeine data; no WBTB study exists.
- **WBTB bathroom:** use the toilet before lying back down. This is my judgment.
- **Morning:** go outdoors for daylight within a few hours of waking. Experts say at least 250 melanopic lux in the daytime.
- **Weekends:** keep the wake time within about 1 h of weekdays. The same wake time every day is common advice, but no source for it was checked **[general caution, no source]**; the 1 h figure is my judgment.
- **Morning log tags:** alcohol, late meal, late hard workout, coffee time. A maximal late workout (vs light exercise) went with night HR +3.9 bpm and HRV −8.3 (Leota 2025, observational), so the app should leave those nights out of detector tuning.

### 7.3 Do not say

- That a shower, cool room, diet or dim light causes lucid dreams. The evidence covers falling asleep and REM, not lucidity.
- That alcohol, cannabis, nicotine, B6, tryptophan or any supplement helps dreaming.
- That Night Shift or blue-light glasses make screen time fine.
- That the watch measures body or room temperature, or detects REM.
- Any guarantee of lucid dreams. Realistic rates are in lucid_induction.md §5.4.
- Anything that diagnoses or treats a condition. This is wellness only.

### 7.4 Sources

Each claim in 7.1–7.2, with its source:

- **Caffeine** (about 45 min lost, 24 studies; 8.8 h and 13.2 h cut-offs): Gardiner C, Weakley J, Burke LM, et al. 2023. The effect of caffeine on subsequent sleep: a systematic review and meta-analysis. *Sleep Medicine Reviews* 69:101764. https://doi.org/10.1016/j.smrv.2023.101764 (Leeds Beckett copy: https://eprints.leedsbeckett.ac.uk/id/eprint/9625/1/TheEffectOfCaffeineOnSubsequentSleepASystematicReviewAndMetaanalysisPV-WEAKLEY.pdf)
- **Alcohol** (about 2 drinks, ≤0.5 g/kg, delays and shortens REM; 27 studies): Gardiner C, Weakley J, Burke LM, et al. 2025. The effect of alcohol on subsequent sleep in healthy adults: a systematic review and meta-analysis. *Sleep Medicine Reviews* 80:102030. https://doi.org/10.1016/j.smrv.2024.102030 (Monash copy: https://research.monash.edu/en/publications/the-effect-of-alcohol-on-subsequent-sleep-in-healthy-adults-a-sys/)
- **Evening exercise** (36 min later, HR +3.9 bpm, HRV −8.3; maximal vs light exercise, 14,689 people; observational, WHOOP co-authored): Leota J, Presby DM, Le F, … Facer-Childs ER 2025. Dose-response relationship between evening exercise and sleep. *Nature Communications* 16:3297. https://pmc.ncbi.nlm.nih.gov/articles/PMC12000559/
- **Hard evening exercise and REM** ("slightly reduced"): Frimpong E, Mograss M, Zvionow T, Dang-Vu TT 2021. The effects of evening high-intensity exercise on sleep in healthy adults: a systematic review and meta-analysis. *Sleep Medicine Reviews* 60:101535. https://doi.org/10.1016/j.smrv.2021.101535
- **Late meals** (OR 1.61, n=793): Chung N, Bin YS, Cistulli PA, Chow CM 2020. Does the proximity of meals to bedtime influence the sleep of young adults? A cross-sectional survey of university students. *Int J Environ Res Public Health* 17:2677. https://pmc.ncbi.nlm.nih.gov/articles/PMC7215804/
- **Fat at night and REM** (n=52): Crispim CA, Zimberg IZ, dos Reis BG, et al. 2011. Relationship between food intake and sleep pattern in healthy individuals. *J Clin Sleep Med* 7:659–664. https://pmc.ncbi.nlm.nih.gov/articles/PMC3227713/
- **Light** (≤10 melanopic lux from 3 h before bed and for night tasks; ≥250 in the daytime): Brown TM, Brainard GC, Cajochen C, et al. 2022. Recommendations for daytime, evening, and nighttime indoor light exposure to best support physiology, sleep, and wakefulness in healthy adults. *PLoS Biology* 20:e3001571. https://pmc.ncbi.nlm.nih.gov/articles/PMC8929548/ The 30-lux lamp figure is an inference, not from this paper.
- **Warm bath** (40–42.5 °C, 1–2 h before bed, ≥10 min; 17 studies included, 13 meta-analysed): Haghayegh S, Khoshnevis S, Smolensky MH, Diller KR, Castriotta RJ 2019. Before-bedtime passive body heating by warm shower or bath to improve sleep: a systematic review and meta-analysis. *Sleep Medicine Reviews* 46:124–135. https://doi.org/10.1016/j.smrv.2019.04.008
- **Warm shower** (7 min faster, n=11): Whitworth-Turner C, Di Michele R, Muir I, Gregson W, Drust B 2017. A shower before bedtime may improve the sleep onset latency of youth soccer players. *European Journal of Sport Science* 17(9):1119–1128. https://doi.org/10.1080/17461391.2017.1346147 (LJMU copy: https://researchonline.ljmu.ac.uk/id/eprint/7494/)
- **Phone before bed** (31 to 18 min, n=38): He JW, Tu ZH, Xiao L, Su T, Tang YX 2020. Effect of restricting bedtime mobile phone use on sleep, arousal, mood, and working memory: a randomized pilot trial. *PLoS One* 15:e0228756. https://pmc.ncbi.nlm.nih.gov/articles/PMC7010281
- **iPad and REM** (−12 min, n=12): Chang AM, Aeschbach D, Duffy JF, Czeisler CA 2015. Evening use of light-emitting eReaders negatively affects sleep, circadian timing, and next-morning alertness. *PNAS* 112:1232–1237. https://pmc.ncbi.nlm.nih.gov/articles/PMC4313820
- **Room temperature** (17 °C vs 22 °C, n=17) **[weak]**: Shin M, Halaki M, Swan P, Ireland AH, Chow CM 2016. The effects of fabric for sleepwear and bedding on sleep at ambient temperatures of 17°C and 22°C. *Nature and Science of Sleep* 8:121–131. https://doi.org/10.2147/nss.s100271 A sleepwear study; temperature is a side comparison.
- **Light during sleep** (REM share, n=20): Mason IC, Grimaldi D, Reid KJ, et al. 2022. Light exposure during sleep impairs cardiometabolic function. *PNAS* 119:e2113290119. https://pmc.ncbi.nlm.nih.gov/articles/PMC8944904
- **Relaxation, sleep hygiene and CBT-I** (insomnia): Edinger JD, Arnedt JT, Bertisch SM, et al. 2021. Behavioral and psychological treatments for chronic insomnia disorder in adults: an AASM clinical practice guideline. *J Clin Sleep Med* 17:255–262. https://pmc.ncbi.nlm.nih.gov/articles/PMC7853203/
- **To-do list** (16 vs 25 min, n=57): Scullin MK, Krueger ML, Ballard HK, Pruett N, Bliwise DL 2018. The effects of bedtime writing on difficulty falling asleep: a polysomnographic study comparing to-do lists and completed activity lists. *J Exp Psychol Gen* 147:139–146. https://pmc.ncbi.nlm.nih.gov/articles/PMC5758411/
- **Dream intention** (40% vs 20%, n=90): Wang et al. 2022. Presleep focusing on positive spontaneous thoughts enhanced the possibility of dreaming of them. *Frontiers in Psychology* 13:1042857. https://doi.org/10.3389/fpsyg.2022.1042857
- **Recall and lucidity** (rs=0.38, n=178): Baird B, Riedner BA, Boly M, Davidson RJ, Tononi G 2019. Increased lucid dream frequency in long-term meditators but not following MBSR training. *Psychology of Consciousness* 6:40–54. https://pmc.ncbi.nlm.nih.gov/articles/PMC6490164 The figure is not in the abstract and was not re-checked in the source audit.
- **Watch charging:** manufacturer instruction, not evidence. No research source.
- **Hot-bath and alcohol-withdrawal warnings, same wake time every day:** general caution, no source.

Removed in the source audit: the technologynetworks, AJMC and sleepreviewmag news links and the Samsung support page.

**Dropped or corrected after checking:**
- Haghayegh "a bath 15 min before bed is too soon": not in the release.
- Haghayegh "about 10 min faster" and "about 90 min before bed works best": these came only from a press release, and "5,322" was the number of candidate records, not studies. Dropped in the source audit.
- Bunnell 1988 (heating right before sleep cuts REM), and with it the "no hot WBTB shower" item: abstract not found.
- Frimpong's exact −2.34% REM: not found. Kept only as "slight".
- Gardiner 2025's exact 18.0 and 11.3 min: the PDF could not be read. Kept the verified dose threshold instead.
- Samsung battery: the page says at least 50%, not 30%.
- Ko & Lee bed socks: the table shows feet 0.8 °C warmer, not 1.3 °C. Item left out.
- Whitworth-Turner's +2% sleep efficiency: settled by the source audit; the journal abstract gives +2%.
- Schredl 2002 diary study: URL returns 404.
- Not opened, so left out: ICS 2-h fluid taper, Mograss naps, Pietilä alcohol and HRV, B6 and EFSA numbers, Drake 2013, Duraccio, the Cochrane blue-light review, Burns 2021.

Not checked: the AASM habits page and Watson 2015 were not reopened. The caffeine REM null was not verified, so caffeine is shown as general-sleep evidence only.

## 8. Third research round (2026-10-29)

How this was done: searches of PubMed, Europe PMC (which also lists preprints), bioRxiv, PsyArXiv, and journal and university copies. Every number below was read in the paper's full text unless it is marked "abstract only". Where this section disagrees with §1–7, this section is newer. 8.6 gives the status of each open question in sleep_and_dreams.md §7.

### 8.1 Can the wrist detect REM live? (Q1)

- **One live REM detector for a watch exists. It has never been tested live.**
  - Mallela & Mallett (2024) built "Dream Sage", an Apple Watch and iPhone app for targeted lucidity reactivation (TLR). Every 30 s it estimates the sleep stage from past data only:
    - heart rate, sampled at 0.2 Hz, averaged per 30 s and smoothed with an exponential moving average;
    - activity counts from the accelerometer, also smoothed;
    - hours since the night started.
  - A random-forest model calls an epoch REM when its probability is 0.24 or more. The authors chose that low threshold to catch more REM at the cost of false alarms.
  - They tested it on the Walch dataset (31 adults, one lab polysomnography (PSG) night each), leaving out one person at a time. **It found 57% of REM epochs (recall), only 34% of its REM calls were right (precision), and overall accuracy was 70%.** Most false REM calls were N2 or N3.
  - The app plays a cue on every REM epoch, but after 4 REM epochs in a row it stops until that REM period ends, so as not to wake the sleeper.
  - The authors say they did not test it live [1] **[weak; replay of past-only features, n=31 healthy adults, lab PSG; numbers read from the preprint because the journal PDF was blocked]**.
- **The nearest live test targeted deep sleep, not REM** (SleepStim, Whitmore 2022).
  - A Fitbit sent heart rate, accelerometer and gyroscope data at 1 Hz to the phone. A neural network judged the last 240 s. On held-out people it separated N3 from other stages with an AUC of 0.77.
  - At home, a Dreem 2 headband's automatic staging was the reference (n=45 with usable headband data):
    - N3 was 21.1% of sleep but received 34.7% of cues;
    - 65.5% of cues fell in N2 or N3, about 14% in REM and about 16% in wake.
  - 16 of 120 people (13%) heard any cue, and none said the sounds woke them [2] **[moderate; home study, reference is a headband algorithm, not PSG]**.
  - Lesson: cues triggered from the wrist often land in the wrong stage, and about 1 in 6 land in wake.
- **Samsung's own staging, offline.**
  - Galaxy Watch 3 against lab PSG (n=32, 16 of them with sleep apnoea): REM sensitivity 0.598 pooled.
  - Per person: sensitivity 0.58, precision (PPV) 0.47, kappa 0.39. The authors say REM duration was "not reliably estimated".
  - Samsung Electronics supplied the watches [3] **[moderate]**.
  - Third-party apps cannot read these stages live anyway.
- **Which signals carry REM best.**
  - **Heart rate level beats every other wrist signal.** In DREAMT (Empatica E4 wristband, 100 people with suspected sleep disorders, lab PSG), the REM F1 score for each signal on its own was: heart rate 0.43; pulse-rate variability 0.26; accelerometer 0.08; skin temperature 0.05; EDA 0.02 (3-class task, handcrafted features). The authors write that "EDA and temperature showed smaller and less consistent contributions" [4] **[moderate; offline model that also looks ahead in time]**.
  - **Heart rate rises quickly at the switch from NREM to REM**, then declines slowly within REM, and it also drifts down across the whole night [5] **[moderate; healthy young men; abstract only]**. So a live detector should compare against the most recent NREM stretch, not against the bedtime baseline.
  - **Time of night** is an input in every model that works ([1]; Walch and Altini in sleep_and_dreams §2).
- **Bottom line for Q1.** No study has checked a live wrist REM detector's cues against PSG one by one. If a past-only wrist model performs like [1], only about a third of its REM calls will be real REM (inference). Guagnano 2025 (n=6) and Garcia-Molina 2022 (chest ECG) are already in sleep_and_dreams §2.

### 8.2 Cue timing, spacing, loudness and type (Q2)

- **Cueing on detected REM has never been compared directly with cueing by time of night.** In the lab, REM cueing helped in one lab (Carr) and showed no benefit in the multi-centre preprint. The home study (Konkoly 2024) cued by time and never staged sleep. No study has tested both against each other. **[gap]**
- **The multi-centre preprint, now checked against its full text** [6] **[preprint; lab, n=60, PSG, cued and sham naps in balanced order, participants blinded]**:
  - **Lucidity:** signal-verified lucid dreams in 23/60 cued naps (38.3%) vs 17/60 sham naps (28.3%), p=.219. This settles the §1 check.
  - **Cost:** REM 12.7 vs 16.2 min (p=.040); sleep efficiency 53.4% vs 57.6% (p=.025).
  - **Cues:** light, sound (500-700-900 Hz) and forehead vibration took turns, about every 20 s, starting about 20 s after REM was detected. Sound started at 40 dBA (lower if that was the person's threshold) and rose in 5-dBA steps, over about 35 dB of background white noise. On signs of arousal, cues paused, then resumed at the level used before the arousal.
  - **Perception:** people perceived cues in 71.1% of cued REM periods; 39.1% of the lucid dreams in cued naps began with a cue; from the first to the last eye signal, lucid dreams lasted longer with cues: 6.08 vs 3.24 min (p=.027).
  - **Wake-ups:** cues woke people in 23.7% of cued periods (5 by light, 9 by sound, 9 by vibration). Even the headband's lowest light and vibration settings were too strong for some people. The authors think starting 20 s into REM may be too early.
- **How far into REM to start: untested.** **[gap]** Carr cued from the first eye movement, about every 15 s, and suggests early cues helped [7]. The multi-centre authors suspect early cues hurt [6]. A wrist-vibration study waited 5 min into REM [8].
- **Spacing and number of cues.** Lab protocols use 15–30 s between cues: Carr about 15 s [7], multi-centre about 20 s [6], Konkoly 2026 15–30 s [9], wrist vibration 30 s [8]. In Carr, people who became lucid had heard 1–14 cues (mean 5.71). Cued REM periods averaged 0.62–0.71 brief awakenings [7] **[moderate; n=40]**.
- **Loudness.**
  - One protocol started at the lowest level the person could detect while awake, raised it only while sleep stayed sound, and lowered it at any sign of arousal [9] **[weak; n=20 frequent lucid dreamers]**.
  - In the home deep-sleep study [2] **[moderate]**: the cue level started at the user's chosen white-noise level; it stepped down automatically when the classifier's deep-sleep probability fell while sounds played; people who set loud levels lost the memory benefit (it reversed), probably through micro-arousals rather than full wakings; the authors recommend cues "barely audible in a quiet room".
- **Sound or vibration.**
  - **Wrist vibration in REM:** 2-s pulses on the back of the wrist every 30 s, from 5 min into REM (n=44, each person tested with and without). Signal-verified lucidity was 16.0% with vibration vs 32.1% with the same training and no vibration. Condition had no significant effect overall (p=.289) [8] **[preprint; lab PSG]**.
  - **Forehead vibration** was perceived as often as sound and got the fastest eye-signal response (1.50 s, vs 1.99 s for sound and 2.9 s for light). It also woke people as often as sound did [6] **[preprint]**.
  - Together with Paul 2014 (§5.1) and Peters 2026 in PLoS One (sleep_and_dreams §5), there is still no evidence that vibration alone brings on lucidity.
- **How often cues wake people.** The studies measure this differently, so the figures are not comparable: 23.7% of cued REM periods, by self-report [6]; 0.62–0.71 PSG arousals per cued REM period [7]; at home, 0.19 movement pauses per cue vs 0.10 with no cue [20]; 0 of 120 people reported being woken by quiet cues aimed at deep sleep [2].
- **Training the cue during the WBTB waking.** Two Northwestern lab studies trained the cue during a waking around 4 a.m. or after about 5 h of sleep, then cued REM after the return to sleep: 2 of 5 had a verified lucid dream, with no control group [10] **[weak]**; [9]. This supports step 3 of §5.2.
- **On names:** no TLR paper by Kathleen Esfahany was found; M. Jafarzadeh Esfahani is the multi-centre preprint's co-first author [6]. No separate Carr 2020 TLR study was found; the Carr TLR nap study used here is [7].

### 8.3 When to wake for WBTB, and for how long (Q3)

- **No study from 2022–2026 compares, within the same people, timing by hours since sleep onset with timing by clock.** sleep_and_dreams §7 Q2 stays open.
- **Older within-person lab studies, new to these files (abstracts only):**
  - Interrupting sleep in the 4th sleep cycle instead of the 2nd made the next sleep onset start straight in REM (a SOREMP) 87.5% of the time vs 58.1% (n=16) [11] **[weak]**.
  - 13 people, 364 long lab nights, 1,189 wakings [12] **[moderate]**: after any waking, REM came back soon; REM after wakings longer than 10 min lasted longer than after shorter wakings; long wakings mostly came out of REM.
  - A single waking of 10–90 min resets the REM rhythm. The time to REM after going back to sleep falls into two groups, split at 25–30 min (n=44 men) [13] **[moderate]**.
  - What this means for OpenTDI (inference): the later the break comes in the sleep period, the faster REM returns. That favours counting hours since sleep onset, but it is no argument against clock time, because on a normal night time asleep and body-clock time move together.
- **Waking out of REM vs at a fixed time.** In Erlacher 2022 [14], morning awakenings after WBTB timed to REM gave 4 lucid dreams in 15 people; awakenings at a fixed time (e.g. 08:00) gave 0 in 20. These were separate experiments **[weak]**. Appel 2020: people were woken from REM after about 6 h and did tasks before going back to bed; 10 of 20 reported a lucid dream, 8 verified by eye signal [15] **[weak; lab, no control group, abstract only]**.
- **Dream recall.** Within the same people at home (n=124 high recallers; EEG headband or Fitbit): nights with more short or medium REM awakenings than usual brought more morning dream recall; nights with more long (2 min or more) REM awakenings brought more recalled content [16] **[preprint]**.
- **How long to stay awake: nothing new.** The 10-min finding in [12] fits the app's 10-min minimum (§6.3 item 4).

### 8.4 Repeated sleep-onset wakings (Dormio/TDI) (Q4)

- **No study has measured later REM, sleep quality that night, or sleep paralysis (SP) after Dormio/TDI.** Every TDI study was an afternoon nap (45–120 min, between 12:00 and 16:00) in screened healthy adults.
  - **Pilot:** after 2–6 sleep-onset wakings (mean 2.28 across all 11), 8 of 11 people still reached REM within a 120-min nap. Sleep stages were not recorded in enough detail to analyse [19] **[weak]**.
  - **Remote Dormio Light** (n=80; 4 hypnagogic reports in about 1 h): "no adverse events were reported". The authors advise screening out psychosis, narcolepsy, recurrent sleep paralysis and frequent hypnagogic hallucinations [18] **[weak; sleep not measured]**.
- **Indirect evidence on SP from repeated wakings:**
  - A schedule of many short sleeps, with a waking after every 5 min of REM, in 13 students prone to SP: 8 SP episodes in 184 wakings. Those with SP had more stage 1 sleep and more fatigue throughout ("low tolerance for disrupted sleep-wake rhythms") [17] **[weak; abstract only]**. Compare Takeuchi 1992: 9.4% (sleep_and_dreams §6).
  - Morning lab TLR naps: SP in 8 of 120 naps (6.7%), the same with and without cues; false awakenings in 46.7% of naps [6] **[preprint]**.
- **Inference, untested:** SP needs REM at sleep onset. Early in the night, sleep onset is rarely REM; late in the night it usually is [11]. So Dormio loops at bedtime should rarely cause SP. Loops after WBTB or near morning may catch the start of REM rather than N1, which raises the risk of SP and hypnagogic hallucinations. Each waking also resets the REM rhythm [13], so bedtime loops probably push the night's first REM later.

### 8.5 What this means for OpenTDI (ranked; **cheap** = a few lines or one setting)

1. **Cheap: make REM-likely use past data only before it times anything.** `RemLikely.kt` judges each minute on the 5 min and 90 min *around* it (`WINDOW_HALF_MIN`, `NEAR_HALF_MIN`) and on medians over the whole night; live, that would need data from the future. For live use, compute the same signs over trailing windows, using medians of the night so far, and log this score every minute next to v2. Expect live precision to be much lower: about 34% in the only past-only wrist model [1].
2. **Cheap: base REM on heart rate, not on EDA or temperature.** Give the most weight to a heart-rate rise above the last NREM stretch [4][5]. Keep time of night and stillness. Use EDA only to veto cues during a storm; give wrist temperature little or no weight.
3. **Cheap: REM-likely can hold cues back, but must not start them on its own.** Keep the §5.2 gate (time window, stillness, pauses after movement) and add: no cues while live REM-likely is low; one burst per candidate bout (up to 10 cues, 20–30 s apart), then silence for the rest of that bout ([1] stops after 2 min of REM); the first cue about 60 s into the bout (judgment between [7], [6] and [8]).
4. **Cheap: after any sign of waking, lower the cue level for the rest of the night.** Trigger: movement or a heart-rate jump within 30 s of a cue. Response: keep the existing 5-min pause, and also drop one level until morning [2][6]. Start each night at the lowest level the user could detect awake in bed [9]; never raise it automatically above the bedtime "notice but won't wake me" level.
5. **Cheap: vibration off by default for lucidity cues.** Change §5.2's "optionally one weak vibration pulse" to opt-in only, always paired with the tone [6][8]. (The late cues play no vibration today; only the back-to-sleep buzz vibrates.)
6. **Cheap: a time-of-night guard for Dormio.** Offer sleep-onset loops at bedtime and in naps. After WBTB or in the last third of the night, show "Sleep onset may go straight into REM; sleep paralysis is more likely" and keep the mode off by default. Turn it off after a logged SP. Cap a session at about 6 sleep-onset wakings, the most any TDI study used [19].
7. **Cheap: an optional quiet noise bed on cue nights.** About 35 dB of pink or white noise from the phone, with the cue level set relative to it. Two cueing studies used one [2][6], but neither tested whether it helps (judgment). This is separate from the −75 dBFS keep-alive carrier.
8. **Cheap: keep WBTB at 6 h after sleep onset, and log both anchors.** For each WBTB, store the clock time, hours since onset and the habitual wake time, plus the time from back-in-bed to stillness and the first REM-likely bout after the return, so the user's own nights can compare the two anchors, which no study has done. Keep the 10-min minimum awake time [12].
9. **Validation (not cheap):** before live REM-likely times any cue, replay the owner's past nights. Compare its minutes with Samsung Health's stages (offline) and with the "cue heard in dream" and "cue woke me" reports. Report precision, not only recall [1][2].
10. **Experimental, later: a "recall alarm"** that rings during a late REM-likely bout [16][14]. The smart-alarm caveat in §5.2 still applies: claim no benefit for alertness.

**Do not claim** that the watch detects REM live, that REM-timed cues beat timed cues, that vibration cues help lucidity, or that Dormio is safe with respect to sleep paralysis.

### 8.6 Status of the open questions (sleep_and_dreams.md §7)

1. **Live REM detection from the wrist:** built once, on Apple Watch (offline: recall 57%, precision 34% [1]), and never tested live. Cueing in phasic vs tonic REM, and detecting lucidity from heart rate or EDA, are still untested.
2. **What to anchor WBTB on:** still open. Within-person lab data show REM returns faster after later breaks [11][12].
3. **SM-L500 beat-to-beat quality:** not addressed. On another wrist device, EDA added little to REM detection [4].
4. **Do REM cues add anything beyond training?** The multi-centre null result is now checked: p=.219, and REM fell from 16.2 to 12.7 min (p=.040) [6]. It is still a preprint.
5. **Safe maximum number of induction nights:** nothing new.
6. **Dormio and SP:** no direct study. Indirect evidence says the risk depends on time of night (8.4).
7. **Where the wrist trigger lands:** no new data for N1. A wrist-triggered deep-sleep system put about 16% of its cues in wake [2].

### 8.7 Seen but not used

- **Djanian et al. 2026**, *Physiol Meas* 47(4): PPG staging on DREAMT, described as "towards real-time". Abstract only, no REM numbers, paywalled.
- **Quino et al. 2024**, arXiv 2410.00693: shorter PPG windows lowered REM sensitivity. Abstract only, preprint, no past-only test.
- **NCT07891806**: Apple Watch vibration timed to REM, as a depression treatment; n=208, completed 2025. A company-sponsored registry entry with no results posted.
- **Mundt et al. 2025**, *J Sleep Res*: CBT plus TLR for nightmares in narcolepsy (TLR n=3). Not about cue timing.
- **LuciEntry** (ACM DIS 2025; EEG and EOG headset) and **Genc 2025** (Geneva master's thesis on the Oniri app): not opened.
- **Salvesen et al. 2024**, *Sleep Med Rev* review: abstract only. Across 51 publications, stimulus incorporation into dreams ranged from 0 to about 80%.
- **Morris et al. 2026**, *Brain Sci* (n=5), and **Konkoly et al. 2025**, *Neuropsychologia*: REM cueing to shape dream content, with no new timing data.

### 8.8 Sources

1. Mallela R, Mallett R 2024. Targeted lucidity reactivation implemented in an open-source watchOS app. *International Journal of Dream Research* 17(2):143–147. https://doi.org/10.11588/ijodr.2024.2.100797. Numbers read from the PsyArXiv preprint: https://doi.org/10.31234/osf.io/e9u8v
2. Whitmore NW, Harris JC, Kovach T, Paller KA 2022. Improving memory via automated targeted memory reactivation during sleep. *Journal of Sleep Research* 31(6):e13731. https://doi.org/10.1111/jsr.13731
3. Kim D, Joo EY, Choi SJ 2023. Validation of the Samsung smartwatch for sleep–wake determination and sleep stage estimation. *Journal of Sleep Medicine* 20(1):28–34. https://doi.org/10.13078/jsm.230004 (Samsung Electronics supplied the watches.)
4. Tallal U, Agrawal R, Kshirsagar S 2026. Wearable sleep-stage classification: a systematic analysis of signal modalities, representations, and multimodal fusion. *Sensors* 26(18):5746. https://pmc.ncbi.nlm.nih.gov/articles/PMC13611820/
5. Cajochen C, Pischke J, Aeschbach D, Borbély AA 1994. Heart rate dynamics during human sleep. *Physiology & Behavior* 55(4):769–774. https://doi.org/10.1016/0031-9384(94)90058-2 (abstract only)
6. Picard-Deland C, Jafarzadeh Esfahani M, … Carr M, Dresler M 2026 (v2, 21 Aug 2026). Inducing lucid dreaming with multisensory stimulation: a preregistered multi-center study. bioRxiv, preprint, not peer reviewed. https://doi.org/10.1101/2024.06.21.600133
7. Carr M, Konkoly K, Mallett R, Edwards C, Appel K, Blagrove M 2023. Combining presleep cognitive training and REM-sleep stimulation in a laboratory morning nap for lucid dream induction. *Psychology of Consciousness* 10(4):413–430. https://doi.org/10.1037/cns0000227
8. Peters E, Heitmann J, Morath N, … Dresler M, Erlacher D 2026. Waking up in the dream lab: a lab-based lucid dream induction paradigm using virtual reality and sensory stimulation. bioRxiv, preprint, not peer reviewed. https://doi.org/10.64898/2026.03.11.711049
9. Konkoly KR, Morris DJ, Hurka K, Martinez AM, Sanders KEG, Paller KA 2026. Creative problem-solving after experimentally provoking dreams of unsolved puzzles during REM sleep. *Neuroscience of Consciousness* niaf067. https://pmc.ncbi.nlm.nih.gov/articles/PMC12875123/
10. Wolk N, Morris D, Nahas Y, Konkoly KR, Carr M, Paller KA, Mallett R 2024. Lucid dreams from reactivating mindfulness during REM sleep: a pilot study. *International Journal of Dream Research* 17(2):224–227. https://doi.org/10.11588/ijodr.2024.2.98233
11. Sasaki Y, Fukuda K, Takeuchi T, Inugami M, Miyasita A 2000. Sleep onset REM period appearance rate is affected by REM propensity in circadian rhythm in normal nocturnal sleep. *Clinical Neurophysiology* 111(3):428–433. https://doi.org/10.1016/s1388-2457(99)00254-0 (abstract only)
12. Barbato G, Barker C, Bender C, Wehr TA 2002. Spontaneous sleep interruptions during extended nights. *Clinical Neurophysiology* 113(6):892–900. https://doi.org/10.1016/s1388-2457(02)00081-0 (abstract only)
13. Miyasita A, Fukuda K, Inugami M 1989. Effects of sleep interruption on REM-NREM cycle in nocturnal human sleep. *Electroencephalography and Clinical Neurophysiology* 73(2):107–116. https://doi.org/10.1016/0013-4694(89)90189-2 (abstract only)
14. Erlacher D, Schmid D, Bischof F, Hammer J, Stumbrys T 2022. Combining wake-up-back-to-bed with cognitive induction techniques: does earlier sleep interruption reduce lucid dream induction rate? *Clocks & Sleep* 4(2). https://pmc.ncbi.nlm.nih.gov/articles/PMC9149976/
15. Appel K, Füllhase S, Kern S, et al. 2020. Inducing signal-verified lucid dreams in 40% of untrained novice lucid dreamers within two nights in a sleep laboratory setting. *Consciousness and Cognition* 83:102960. https://doi.org/10.1016/j.concog.2020.102960 (abstract only)
16. Ataei S, Esfahani MJ, Axmacher N, Dresler M, Schoch SF 2026. How awakenings shape dream recall: a multilevel study. bioRxiv, preprint, not peer reviewed. https://doi.org/10.64898/2026.05.20.722678
17. Takeuchi T, Fukuda K, Sasaki Y, Inugami M, Murphy TI 2002. Factors related to the occurrence of isolated sleep paralysis elicited during a multi-phasic sleep-wake schedule. *Sleep* 25(1):89–96. https://doi.org/10.1093/sleep/25.1.89 (abstract only)
18. Bellaiche L, Haar Horowitz A, McClay M, Bottary R, Denis D, Chen C, Maes P, Seli P 2024. Targeted dream incubation at a distance: the development of a remote and sensor-free tool for incubating hypnagogic dreams and mind-wandering. *Frontiers in Sleep*, 1258345. https://pmc.ncbi.nlm.nih.gov/articles/PMC12713799/
19. Haar Horowitz A, Konkoly KR, Carr M, Stickgold R, Maes P 2026. Targeted dream incubation at sleep onset can influence later dream content in REM sleep: a pilot study. *Frontiers in Sleep* 5:1812535. https://doi.org/10.3389/frsle.2026.1812535
20. Konkoly KR et al. 2024. Provoking lucid dreams at home with sensory cues paired with pre-sleep cognitive training. *Consciousness and Cognition* 125:103759. https://pmc.ncbi.nlm.nih.gov/articles/PMC11542932/

Not checked: the IJoDR journal PDF of [1] (blocked), so its numbers come from the PsyArXiv preprint; [5], [11]–[13], [15] and [17] were read as abstracts only; Erlacher 2022 [14] was read through a summarising fetch of its PMC page. Spot-checked again before adding: [4]'s per-signal REM F1 values (Table 9) and [19]'s 8 of 11.
