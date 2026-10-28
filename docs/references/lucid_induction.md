# Inducing lucid dreams: what the evidence says, and what it means for OpenTDI

Research notes, 2026-10-28. Lucid dreaming (knowing you are dreaming while you dream) is OpenTDI's main goal. MIT's
Dormio, which OpenTDI started from, cues at **sleep onset** to steer what a dream is *about*. The best evidence for
**lucidity** comes from cueing in **REM sleep late in the night**, after training the cue before sleep.

## 1. Targeted lucidity reactivation (TLR), Northwestern University (Paller lab)

- **Lab version** (Carr, Konkoly et al. 2023, as reported by the papers below): a sound/light cue is paired with
  lucidity training before sleep, then replayed in REM under polysomnography. About 50% of participants had a lucid
  dream in one nap, many with little prior experience.
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
    attributed to cues. Experiment 2 (blinded controls, n=112 on nights 1–2) showed a smaller but significant effect of
    trained cues over untrained cues. Cues caused more movement-pauses than no cues (0.19 vs 0.10 per cue): cues do
    wake people sometimes. Lucidity was self-reported.
- **Multi-centre replication with 2-channel EEG** (preprint, bioRxiv 10.1101/2024.06.21.600133): an early version
  reported signal-verified lucid dreams in 65% (Netherlands) and 45% (Italy) of the first 40 participants. **The final
  version (21 Aug 2026, N=60) found no benefit of the REM cues**: 38.3% vs 28.3% of naps (p=.219); the cues woke
  people in 23.7% of cued REM periods and cut REM from 16.2 to 12.7 min (see §5).

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

- Offline wrist sleep staging from heart rate, HRV and movement finds REM with sensitivity 0.60–0.89 against
  polysomnography (Kuula & Pesonen 2021, JMIR mHealth 9(2):e24704; Renevey et al. 2017, CSEM). A 2025 review
  (medRxiv preprint) reports consumer devices under-count REM by 50–70%, and REM and light sleep look alike in HRV.
- No study found for **real-time** wrist REM detection driving cues. The home TLR study did not detect REM at all: it
  used time (6 h) plus movement pauses, and still worked.
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

- Konkoly et al. 2024, Provoking lucid dreams at home with sensory cues paired with pre-sleep cognitive training:
  https://pmc.ncbi.nlm.nih.gov/articles/PMC11542932 (PDF: https://faculty.wcas.northwestern.edu/~paller/C&C24.pdf)
- Northwestern news, 2024: https://news.northwestern.edu/stories/2024/10/study-shows-pre-sleep-training-coupled-with-sensory-cues-can-trigger-lucid-dreaming-2
- Multi-centre TLR with wearable EEG (preprint): https://www.biorxiv.org/content/10.1101/2024.06.21.600133.full.pdf
- Aspy 2020, Findings from the International Lucid Dream Induction Study: https://www.frontiersin.org/journals/psychology/articles/10.3389/fpsyg.2020.01746/full
- Portable devices to induce lucid dreams, are they reliable? (2019): https://www.ncbi.nlm.nih.gov/pmc/articles/PMC6517539/
- Kuula & Pesonen 2021, wrist HRV sleep staging: https://mhealth.jmir.org/2021/2/e24704/PDF
- Renevey et al. 2017, optical wrist-worn sleep monitoring: https://yoda.csem.ch/items/b20b1e4e-b8ba-4df6-8497-6c84ef608d95
- EDA and wearable sleep monitoring review (preprint, 2025): https://www.medrxiv.org/content/10.1101/2025.11.14.25340258.full.pdf

## 5. Second research round (2026-10-28): WBTB, cues, alarms, overall evidence

Four parallel literature searches and a synthesis that opened each surprising source; findings it could not verify are
listed in 5.5 and were not used. Where this section and §1–4 disagree, this section is newer.

### 5.1 Evidence-ranked interventions

| Intervention | Expected benefit | Evidence | Phone/watch feasibility |
|---|---|---|---|
| WBTB at about 6 h, then a guided dream report and MILD/SSILD, then back to bed | At home, 18% of WBTB nights lucid vs 6% of other nights (Schredl 2021, n=50). On WBTB+MILD placebo nights, 14% of participants were lucid (LaBerge 2018, n=121). In the lab with 60 min awake: 53–55% self-reported, 27% signal-verified (Erlacher & Stumbrys 2020) | One RCT arm plus small lab and home studies | High: a watch alarm and a spoken TTS script |
| WBTB with the technique left out | 4.3% of nights, no better than chance (Klofat 2024, n=138). Reading or games while awake: 9% and 0% (Erlacher 2020) | Online and lab | Do not offer a bare alarm |
| Cue trained before sleep, then replayed in later sleep (TLR) | Lab nap: 50% vs 17% verified (Carr 2023, 7/14, 7/14 vs 2/12). **Correction for lucid_induction.md:** the final multi-centre preprint (21 Aug 2026, N=60) found no benefit of REM cues: 38.3% vs 28.3% of naps (p=.219). Cues did wake people in 23.7% of cued REM periods and cut REM from 16.2 to 12.7 min | Mixed | High: watch speaker; the level must be calibrated |
| Falling back asleep quickly | Lucid on 18.3% of nights when asleep within 10 min vs 11.1% (Aspy 2020). The 5-min cutoff was not significant | Observational | High: log the gap from "back in bed" to stillness |
| Spoken report at every alarm | Dreams recalled per day correlate with lucid rate (rs=.31). Prior lucid frequency correlates too (r=.38) (Aspy 2020) | Observational | High (already built) |
| Gentle, name-calling wake | Being called by name gives +10.1% recall vs an alarm tone (p=.046, NREM awakenings only). Gentle waking gives +7% (Stucky 2025) | Review | High: TTS |
| "Lucid snooze" | Snooze use goes with ever having a lucid dream (OR 2.52, rho=.22, n=84) | Correlational | High; label it experimental |
| Wrist vibration as the only cue | 2 verified lucid dreams in 27 wrist/ankle vibrations (7.4%) (Paul 2014) | Pilot | Possible but weak; pair it with the tone |
| Do not build: reality-check cues, meditation course, smart wake window | Spoken "You are dreaming" cue with reality checks: 3/11 verified (Peters 2024). MBSR course: p=.92 (Baird 2019). Multimodal smart alarm: no effect on alertness, sleepiness or mood, and sleep efficiency fell to 83.97% from 86.65% (Campanella 2024) | | |

### 5.2 Recommended defaults

- **WBTB time:** 6.0 h after the watch detects sleep onset (range 4.5–6.5 h), with a fixed clock time as fallback. The 6 h lab study got 53–54% self-reported lucidity. The 4.5 h follow-up got 0–26%, and its authors say the earlier waking "seems to reduce" success. That comparison is across studies, so the evidence is thin.
- **Awake period:** 30 min by default (20–60). Waking for 30 min or more predicted success (β=.11). Every 10 min of MILD helped (β=.09), but extra minutes beyond that did not (p=.80) (LaBerge 2018). 60 min beat 30 min in the lab (54% vs 36%), but that was not tested statistically. The app should guide, in order:
  1. Spoken dream report.
  2. Review of dream signs and setting the intention.
  3. A few pairs of the night cue plus lucidity guidance (retraining the cue).
  4. MILD or SSILD lying down, lights off, as the last step.
- **Return to sleep:** keep the screens black and the guidance audio-only. Log the time from "back in bed" to stillness. After 60 min awake, people took 31.5 min on average to fall asleep (Erlacher 2020). If he usually takes more than 20 min, suggest a shorter awake period (judgment).
- **Late-night cues:**
  - Start only once he is still again, and no sooner than about 40 min after he is back in bed: REM latency after WBTB averaged 42 min.
  - Cue: the same short tone used in training (400/600/800 Hz, about 650 ms), optionally with one weak vibration pulse.
  - Spacing: one cue every 20–30 s, at most 10 per still bout. The Netherlands lab capped cues at 10 per REM period (Konkoly 2021). In Carr's study, people who became lucid had heard 5.7 cues on average.
  - Loudness: set in bed with the watch in his sleeping position, at "easily notice but won't wake me" (Carr). That is about whisper level, 40–45 dB SPL (Konkoly 2021).
  - Raise the level only in tiny steps. The multi-centre study's 5 dB steps woke people.
  - After each night, nudge the level down after a wake-up and up after a night with no cue in any dream. DreamLight needed a median of 6 nights to calibrate.
  - Keep the 5-min pause after movement and the 45-min pause after a wake-up.
- **Sleep-onset spoken cues:** recommend his own recorded voice. Unfamiliar voices caused more K-complexes and microarousals (relative effect 0.76 vs 0.24, n=17, NREM) (Ameen 2022). Applying that to lucidity is an inference.
- **Wake-up tones:** keep all of them distinct from the lucidity cue.
  1. **Melodic (default):** a melodic sound went with less self-reported grogginess (V=.37, n=50, correlational).
  2. **5-min gentle swell from -30 to 0 dB, then the tone:** better morning mood (p=.007, n=18). Sleepiness and snooze use did not change.
  3. **Spoken wake ("Hasan, keep your eyes closed, replay your dream"):** best for dream recall (+10.1%).
  4. **Low 520 Hz square-wave beeps (escalation):** this tone had the lowest waking thresholds (n=39). High-pitched alarms of 3 kHz or more did worse.
  5. **Vibration only (WBTB default):** woke healthy sleepers on 94% of alarms, with a median of 52 s (IQR 37–80). Only 13 of 18 woke to every alarm, so it must escalate to sound.
- **Volume ramp:** the 5-min swell is an option. Plain tones fade in over 20–30 s (judgment; no study has tested ramp length).
- **Ring and snooze limits:**
  - The alarm escalates until a deliberate dismiss. A vibration-only alarm adds sound after about 2 min.
  - Snooze: 9 min each, at most 3. A 30-min snooze did no harm in habitual snoozers (Sundelin 2023, n=31). The real-world average is 10.8 min (Robbins 2025).
- **Smart wake window:** no. The one controlled study found no benefit. Wrist sensors cannot stage sleep in real time (see lucid_induction.md §3).
- **Time to wake up:**
  - Main measure: seconds from alarm start to the first deliberate tap.
  - Also log: ramp start, the time it reached full volume, first movement above the stillness threshold, final dismiss, snooze count, and whether he woke before the alarm.
  - Keep morning and WBTB alarms separate. Compare each against his own rolling median, because no norm exists.

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

- **WBTB:** 1–3 nights a week by default. Once a week tripled the per-night rate (Schredl). Seven nights in a row did not lower sleep quality (3.5 to 3.4, p=.15), but success varied by day with a slight decline (p=.001) (Aspy 2020). No study sets a safe maximum. On WBTB nights, suggest a wake alarm at least 30 min later.
- **Cue nights:** cues cost REM. Offer a blinded cue/no-cue night option so he can test whether cues help him. In the DreamLight study, cue nights averaged 0.30 lucid dreams per night vs 0.09.
- **Adherence is the real bottleneck:** of 416 users in the home TLR study, 50 finished all 7 nights. In ILDIS, 1,618 started and 355 finished. Setup has to take one tap.
- **Contraindications:** psychosis, dissociative disorders, frequent sleep paralysis, narcolepsy, insomnia.
  - Sleep paralysis and lucid dreaming share dissociation as a predictor (N=1,928).
  - People who deliberately tried to induce lucid dreams rose in dissociation and schizotypy over 2 months (correlational).
  - Show a caution if the morning report logs sleep paralysis.
- **Do not claim:**
  - that REM cues are proven;
  - REM or "cycle-aligned" timing;
  - that the tones reduce grogginess;
  - smart waking;
  - that the lucid snooze works;
  - lighting advice (untested).
- **Realistic expectation:** about 14–18% of WBTB nights lucid at home.

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

Schredl 2021 was read only through the PsyPost summary.

### 5.6 Sources

- Erlacher & Stumbrys 2020, Wake Up, Work on Dreams, Back to Bed: https://pmc.ncbi.nlm.nih.gov/articles/PMC7332853/
- Erlacher et al. 2022, Earlier sleep interruption: https://pmc.ncbi.nlm.nih.gov/articles/PMC9149976/
- LaBerge, LaMarca, Baird 2018, Galantamine: https://journals.plos.org/plosone/article?id=10.1371/journal.pone.0201246
- Schredl, Dyck, Kühnel 2021, WBTB at home (summary): https://www.psypost.org/more-evidence-the-wake-up-back-to-bed-technique-can-help-to-induce-lucid-dreams/
- Klofat et al. 2024, WBTB stand-alone: https://docserv.uni-duesseldorf.de/servlets/DocumentServlet?id=67705
- Aspy 2020, ILDIS: https://www.frontiersin.org/journals/psychology/articles/10.3389/fpsyg.2020.01746/full
- Aspy et al. 2017, NALDIS (release): https://www.sciencedaily.com/releases/2017/10/171019100812.htm
- Picard-Deland et al. 2026, Multisensory stimulation multi-centre (preprint v. 21 Aug 2026): https://www.biorxiv.org/content/10.1101/2024.06.21.600133.full.pdf
- Carr et al. 2023, Morning-nap TLR: https://cronfa.swan.ac.uk/Record/cronfa53997
- Konkoly et al. 2021, Real-time dialogue: https://pmc.ncbi.nlm.nih.gov/articles/PMC8162929/
- LaBerge & Levitan 1995, DreamLight validity: https://www2.cs.uh.edu/~gnawali/courses/cosc6321-f14/DreamLight_Validity.txt
- Paul, Schädlich, Erlacher 2014, Visual and tactile stimulation: https://journals.ub.uni-heidelberg.de/index.php/IJoDR/article/view/13044
- Peters et al. 2024, Sounds of lucidity: https://boris-portal.unibe.ch/entities/publication/4390b1b9-e40c-4b8c-9f15-b0c4121d223d/full
- Ameen et al. 2022, Unfamiliar voices in sleep: https://pmc.ncbi.nlm.nih.gov/articles/PMC8896625/
- Gott et al. 2020, Sleep fragmentation and lucid dreaming: https://pure.uva.nl/ws/files/118129944/1_s2.0_S1053810019305227_main.pdf
- Baird et al. 2019, Meditators and MBSR: https://pmc.ncbi.nlm.nih.gov/articles/PMC6490164
- Attali et al. 2020, Vibrating bracelet: https://pmc.ncbi.nlm.nih.gov/articles/PMC7783873/
- McFarlane et al. 2020, Melodic alarm survey: https://pmc.ncbi.nlm.nih.gov/articles/PMC6986749
- Yang, Cera, Pauletto 2026, SoundRise: https://dael.euracoustics.org/confs/fa2026/data/articles/000156.pdf
- Bruck, Thomas, Rouillard 2009, Pitch and pattern: https://vuir.vu.edu.au/2059/
- Campanella et al. 2024, Smart alarm: https://pmc.ncbi.nlm.nih.gov/articles/PMC10969141/
- Stucky 2025, Awakenings and dream recall: https://pmc.ncbi.nlm.nih.gov/articles/PMC12053782/
- Vallat et al. 2017, Dream recall and awakenings: https://www.frontiersin.org/journals/human-neuroscience/articles/10.3389/fnhum.2017.00132/full
- Sundelin et al. 2023, Is snoozing losing (release): https://www.su.se/english/news/articles/2023-10-23-you-dont-lose-if-you-snooze
- Robbins et al. 2025, Snooze in smartphone users (release): https://news.massgeneralbrigham.org/en/dont-hit-snooze-on-new-research-about-waking-up-each-morning
- Smith & Blagrove 2015, Snooze and lucid dreaming: https://cronfa.swansea.ac.uk/Record/cronfa25493
- Soffer-Dudek 2020, Are lucid dreams good for us?: https://pmc.ncbi.nlm.nih.gov/articles/PMC6993576/
- Denis & Poerio 2017, Sleep paralysis and lucid dreaming: https://eprints.whiterose.ac.uk/103552
- Konkoly et al. 2024, home app attrition (summary): https://www.psypost.org/lucid-dreaming-app-triples-users-awareness-in-dreams-study-finds/

The Robbins snooze numbers come from press coverage, and the Paul 2014 and Peters 2024 numbers from abstracts or search summaries; the publishers' pages were blocked. No repo files were edited.
