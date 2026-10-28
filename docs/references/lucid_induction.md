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
- **Multi-centre replication with 2-channel EEG** (preprint, bioRxiv 10.1101/2024.06.21.600133): signal-verified
  lucid dreams in 65% (Netherlands) and 45% (Italy) of the first 40 participants. Not yet peer reviewed.

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
