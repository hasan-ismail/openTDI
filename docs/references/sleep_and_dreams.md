# Sleep, dreams and lucid dreaming: a research reference for OpenTDI (2026-10-28)

Every source here is a peer-reviewed paper, a systematic review, a professional guideline, a university thesis or a government regulator's guidance. Each one was opened through its DOI, PubMed/PMC or Europe PMC record before its numbers were used. Each claim carries an evidence tag: **[strong]**, **[moderate]**, **[weak]**, **[contested]** or **[preprint]**. Bracketed numbers point to §8. This file builds on lucid_induction.md and does not repeat it. It is for wellness only and is not medical advice.

## 1. Sleep stages and the night's architecture

Experts score sleep stages in 30-second pages using scalp EEG, eye leads and a chin muscle lead [1]. The watch records none of these. OpenTDI's states are therefore proxies ("still", "heart rate falling", "late-night window") and must never be labelled N1 or REM. Even with full EEG, trained scorers agree 82.6% of the time overall but only 63.0% of the time for N1, the stage Dormio aims at [2] **[strong]**. The sleep-onset trigger should be judged by what the sleeper reports ("was I drifting off? did the prompt wake me?"), not by a claimed accuracy for N1.

| Stage | Share of the night (healthy adults aged 18–34) [3] | When in the night | What a wrist can see |
|---|---|---|---|
| Wake | 32 min awake after sleep onset; asleep in 14.3 min | Start of the night, brief wakings | Movement; the highest heart rate |
| N1 (drowsy) | 6.0% | Sleep onset and after each waking | Stillness begins, heart rate drifts down; 4% of skin-conductance (EDA) peaks [7] |
| N2 (light) | 51.3% | All night | Still; 55% of EDA peaks [7] |
| N3 (deep) | 21.4% | Mostly the first two cycles [4] | The stillest stage [6]; heart rate only ~5 bpm below wake [5]; 25% of EDA peaks and most EDA "storms" [7] |
| REM | 19.8%; first REM about 96 min after sleep onset [3] | Episodes run 5–10 min early in the night and up to 30–40 min late [4] | Muscle atonia with small twitches; heart rate and blood pressure at waking levels [5]; 12% of EDA peaks [7] |

- **A cycle is not a clock.** Across 6,064 lab-recorded cycles, the median cycle lasted 96 min (mean 99.5) and 95% of cycles ran 60–150 min. Cycle length varied by 29.3% from one cycle to the next and by 18.5% from night to night. Each person's typical REM episode lasted 11–41 min [8] **[moderate; single centre, retrospective]**. Timing cues by "multiples of 90 min" can be off by an hour by morning.
- **REM follows the body clock as well as time asleep.** The daily REM peak falls shortly after the minimum of core body temperature. REM is highest when sleep ends at the usual wake time [9][10] **[strong; small samples, several labs]**. On average the temperature minimum falls at 03:50 for morning types, 05:02 for in-between types and 06:01 for evening types [11]. *Inference:* anchor the late-night window on the habitual wake time as well as hours since sleep onset. Skin temperature on the watch cannot locate the core-temperature minimum.
- **After a WBTB break at 6 h (lab):** people took 31.5 ± 26.0 min to fall back asleep. REM came back in 53 of 62 returns (85.5%), 42.1 ± 24.7 min after sleep onset. Deep sleep made up only 2.5–8.1% across the condition means [12] **[weak; groups of 11–15]**. Healthy people woken from REM between 01:30 and 03:30 took 80.6 ± 24.9 min to reach REM again [13]. Comparing across these studies suggests REM returns faster after a later WBTB (inference).
- **Lost sleep costs REM.** Four-hour nights cut stages 1–2 and REM but largely spared deep sleep, and REM rebounded on recovery nights [14]. When REM was blocked selectively, it fell to 9.2% of baseline and then rebounded to 140.1% [15] **[moderate]**. Cues that wake the sleeper take REM, and the body pays it back on later nights, so cue nights should be spaced out.
- **Alcohol:** about 2 drinks (≤0.50 g/kg) already delays and shortens REM, and the effect grows with dose (27 studies) [16] **[strong]**. **Medicines:** many antidepressants (SSRIs, SNRIs, MAOIs, activating tricyclics) lengthen REM latency and reduce REM [17]. The app should say "never change a medicine for this app".
- **Age is contested.** Under the older scoring rules, deep sleep in men fell from 18.9% at ages 16–25 to 3.4% at 36–50 [18]. Under modern rules, a meta-analysis found no significant change per decade in N3 or REM [3] **[contested]**. Compare each night with the sleeper's own baseline only.
- **First-night effect:** first lab nights averaged 371.6 min asleep and a REM latency of 99.5 min, against 419.7 min and 87.3 min on later nights [3]. Leave the first night out after any big change to the setup (inference).
- Adults should regularly sleep **at least 7 h** [19] **[guideline]**. This gives a concrete floor for the WBTB sleep guard.

## 2. Each stage from a watch, and how well a watch can stage sleep

- **Heart:** in NREM the nervous system shifts toward rest-and-digest (parasympathetic) control, and REM resembles wake. Heart rate also follows a circadian rhythm regardless of stage [20][21]. A baseline taken at bedtime will drift by 3–6 a.m., so use a running baseline for each night.
- **Movement:** movement rate falls in the order wake > N1 > REM > N2 > N3, with little overlap between stages, and it is a stable trait for each person (r=.69 between adjacent nights) [6]. Adults change position 3.6 times an hour at ages 18–24 and 2.7 times at 35–45 [22]. Because N3 is stiller than REM, a stillness gate picks deep and light sleep at least as often as REM.
- **EDA:** more than 80% of wrist EDA peaks fall in NREM, and long "storms" cluster in the first half of the night. In 13 of 15 people the wrist was warmer in deep sleep than in REM [7] **[moderate; inner-wrist electrodes, not the watch's back-of-wrist sensor]**. An EDA-only rule told sleep from wake with 97% sensitivity and 75% specificity [23]. Use a storm as a reason **not** to cue (probably N2/N3). A quiet EDA trace does not mean REM.
- **Breathing:** breathing is least regular in wake, more regular in REM, and most regular in N3 [24]. No one has tested whether the watch can measure this.
- **Watch staging against polysomnography (PSG):**
  - Consumer devices in healthy adults found 50–69% of REM epochs. One Fitbit's REM-minute bias was −11.4 min, but single nights ranged from −60 to +37 min [25] **[moderate]**.
  - The **Galaxy Watch 5** (22 sleep-clinic patients) found 63% of REM epochs, with kappa 0.42. Some authors hold stock in a competing company [26]. No peer-reviewed validation of the Watch 6, 7 or 8 was found.
  - Offline models: raw Apple Watch data classified 65% of REM correctly, kappa 0.28 [27]. On the Oura ring, REM sensitivity rose from 32% with motion and temperature to 78% with HRV and time-of-night features (the authors work for Oura) [28]. The best wrist-PPG model reached kappa 0.65 [29].
  - **Real time:** a model using only ECG beat-to-beat intervals reached 3-class kappa 0.52 in healthy people and 0.24 in REM behaviour disorder [30]. A real-time smartwatch study classified REM correctly 62% of the time, but in only 6 people [31] **[weak]**.
  - Beat-to-beat HRV from PPG agrees with ECG worse for RMSSD than for SDNN, and that evidence does not extend to sleep [32]. Prefer slower heart-rate features.
- **Correction for lucid_induction.md §3:** Kuula & Pesonen used a **chest ECG** (Firstbeat Bodyguard 2), not a wrist device; REM sensitivity was 0.60 [33]. The preprint's "under-counts REM by 50–70%" conflicts with the peer-reviewed biases of about ±10 min [25].
- The AASM says consumer sleep technology cannot diagnose sleep disorders [34] **[guideline]**. Watches are weakest outside the main sleep bout, which includes WBTB returns [35].
- **Bottom line (inference):** a soft "REM-likely" score could combine late night, a still bout (twitches allowed), a heart rate higher and more variable than the night's running NREM baseline, and no EDA storm. Use it only to schedule or hold back cues, never to claim REM.
- **Built as "REM-likely v1"** (`shared/.../stats/RemLikely.kt`, 2026-10-28), shown on the Stats night timeline only and not yet used to time cues. A minute counts when it is estimated asleep, at least 60 min after sleep onset [3], outside an EDA storm, and the 5 min around it have a heart rate (Samsung status "good" only) at least 3 bpm above the lower quartile of the minute means in the 90 min around it [8] and a larger spread than the night's median 5 min asleep [5][20][21]. Runs under 5 min are dropped [4]. Storm, after [7]: 30-s epochs with a peak (a rise above 0.01 µS/s, their wrist threshold), two or more in a row; the watch's 1-Hz trace stands in for their 32-Hz one. Only the storm rule comes from a paper; every other value is GUESSED and untested. It needs no calibration night, because every comparison is within the same night. To check it: dream recall at alarm wakings inside vs outside REM-likely minutes and, optionally, Samsung Health's own stages for the same nights.

## 3. Dreaming and dream recall

- **Recall by stage:**
  - Waking from REM gives a report about 80% of the time; waking from NREM, about 50% [36].
  - Within the same people, contentful reports came from 77% of N1, 42% of N2, 23% of N3 and 82% of REM awakenings [37].
  - Early-night N2/N3 awakenings gave 30% dreams, 39% "white dreams" (sure they dreamed, no content) and 31% nothing [38] **[weak, n=5]**.
  - The report should offer Yes / White / No.
- **Sleep onset:** some imagery is recalled from 90–98% of N1/N2 awakenings. Images can last about a second, and outside sounds get woven into them [39]. That is why spoken cues shape content.
- **Dormio (MIT):**
  - The theme appeared in 45 of 67 cued sleep-onset reports (67%) against 2 of 69 uncued (3%). A cue given while awake did nearly as well (29/56, p=.129). No PSG was used [40].
  - In the follow-up, 70.3% of cued reports contained the theme against 1.4% uncued, and creativity scores rose [41] **[weak, 12–13 per group]**.
  - In a pilot, the theme carried into the first REM dream for 4 of 8 people [42] **[weak]**.
- **N1 as a creative sweet spot:** 83% (20/24) found a hidden rule after N1, against 31% who stayed awake and 14% who reached N2 [43] **[moderate]**. Keep the Dormio countdown short so the sleeper does not drift into N2.
- Sleep-onset content is thought-like and REM is the most hallucinatory [44]. Lucidity needs immersive, REM-like dreams, so late-night effort fits better.
- **No "dream detector":**
  - Posterior EEG activity predicted dreaming from NREM [45], but a blinded replication attempt was at chance [46] **[contested]**. Both used dense scalp EEG. A watch cannot detect dreaming.
- **What helps recall:**
  - Dreams are kept only if waking follows while the memory trace lasts (the arousal-retrieval model, as described in [47]).
  - People who recall many dreams wake more from NREM, while their REM awakenings are similar to low recallers' [48]. Their brains also react more to sounds (18 vs 18 people) [47]. *Inference:* for someone who recalls many dreams, start cue levels low.
  - At home, 58% of mornings brought a dream with content, 14% a white dream and 28% nothing (2,900 reports; recall was lower in winter) [49]. Compare protocols within the same season.
- **What dreams contain:** 65% of reports drew on recent waking life, but at most 1–2% replayed an episode [50]. Dream signs will turn up as fragments. Without a cue, sleep-onset dreams incorporated a just-practised task only 7% of the time, and the link between dreaming of it and getting better vanished once baseline skill was controlled [51] **[contested]**. Do not claim that dreams improve skills.

## 4. Lucid dreaming: prevalence, timing, brain, two-way communication

- **Prevalence:** 55% of people have had at least one lucid dream and 23% have one monthly or more (meta-analysis) [52] **[strong]**. In a representative sample, lucid-dream frequency correlated r=.57 with dream recall [53]. Recall is the best early indicator to track.
- **Verification:** in the lab, lucidity is confirmed by a pre-agreed eye signal (left-right-left-right) during PSG-scored REM [54][55]. In OpenTDI every lucid dream is self-reported. A pre-agreed in-dream action can make the self-report stricter, but it is still not verification.
- **Timing and body:** lucid dreams occur in REM periods later in the night, with higher heart rate, breathing and skin potential (LaBerge 1986, as summarised in [55]) **[weak; old, small]**. Lucid REM is "activated" REM with denser eye movements. The reported 40 Hz signature disappears once eye-movement artefact is removed [56]. A multi-lab study found only minimal scalp-level differences [57] **[contested]**. Make no "40 Hz" or "gamma" claims.
- People with narcolepsy are lucid far more often: 77.4% vs 49.1% have had a lucid dream, 7.6 vs 0.3 a month [58].
- **Two-way communication** with lucid dreamers worked, but rarely: 29 of 158 questions (18.4%) got a correct answer, against 1 of 379 in non-lucid REM. Cue tones were about 40–45 dB SPL [59] **[moderate]**. Even non-lucid sleepers respond briefly when their brain state allows [60]. In tonic REM it is about as easy to wake someone as in N2; in phasic REM, about as hard as in deep sleep [61]. This explains why some cues wake the sleeper and others go unnoticed.
- **Lucid dreams are short.** People estimate about 14 min [62], but lab-verified lucid dreams averaged 1 min 53 s (range 25 s to 6 min 25 s) [63]. Choose one simple goal per night.
- **Rating:** the LuCiD scale separates insight (knowing it is a dream) from control [64]. Replace the Lucid Yes/No tap with separate insight and control ratings from 0 to 5.
- Sleep paralysis and false awakenings show EEG between wake and REM [65] **[weak, n=5]**. This supports a reality check at every waking prompt.

## 5. Induction methods ranked by evidence

| Method | Best verified result | Evidence | Needs |
|---|---|---|---|
| WBTB + MILD | 14% of participants lucid on placebo nights (WBTB at 4.54 h, 36 min awake) [66]. 15.8% of week-2 nights (n=355) [67]. Lab, 6 h: 3/11 and 4/15 eye-signal verified [12] | Moderate | Alarm and spoken guide |
| WBTB + SSILD | 16.9% of nights [67]. Lab SSILD with sham cues: 28.3% of naps verified [68] | Moderate / preprint | Audio guide |
| Trained cue replayed in REM (TLR), lab | 7/14 and 7/14 verified vs 2/12 without cues [63]. Italian site: 7/20 vs 3/20 (p=.021) [69] | Moderate; small, not randomised | PSG to time REM |
| TLR, multi-centre | 38.3% vs 28.3% of naps, no difference; cues disrupted sleep in 23.7% of cued periods [68] | **Preprint**, not peer reviewed | — |
| TLR at home, phone | 14/19 vs 8/19 had at least one lucid dream (app week vs the week before). Night 2 OR 3.61, only in people who recalled dreams. 50 of 416 used it all week [70] | Moderate; self-report | Timed cues |
| Asleep within 10 min of the technique | 18.3% vs 11.1% of nights [67] | Observational | Log the latency |
| Daytime reality checks | 12.1% vs 19.4% of nights (p=.052) [67]. Classic checks did not beat no training (p=.177) [71] | Moderate (no effect) | Don't build |
| Light cues | DreamLight: 11 of 14 people reported 32 lucid dreams, 22 of them on cue nights [72] | Weak, old | Don't build |
| Wrist vibration | Touch felt in the dream 14.3% vs 4.2% with sham (n.s.). 45 of 165 reports were excluded because the stimulation woke people [73] | Weak | Only paired with the tone |
| Vestibular current (GVS) | Externally rated lucidity 66.7% vs 13.3% (n=11) [74] | Weak, not randomised | Electrodes: not feasible |
| 40 Hz tACS | The "77%" used a lenient definition; insight averaged 0.5–0.6 out of 5 [55][75]. A replication found sham did as well [76] | **Contested** | Not feasible |
| tDCS | Small self-rated effect, only in frequent lucid dreamers [77] | Weak | Not feasible |
| 8-week mindfulness (MBSR) course | No increase [78] | Weak (no effect) | Don't build |
| Galantamine (background only) | 27% (4 mg) and 42% (8 mg) vs 14% [66] | Moderate | Drug; out of scope |

- **Reviews:** in 2012 (35 studies, low quality) no technique was shown to work reliably [79]. In 2023 (19 studies), MILD was the most effective technique, and SSILD and galantamine still need replication [80] **[abstract only]**.
- **Nights to the first lucid dream:** in the lab, about half of people succeed in the first one or two naps [63][68], and success fell in the second session (35% to 15%, p=.064) [69]. No study measures this at home. *My calculation:* at 15% per WBTB night, treating nights as independent, the chance of at least one lucid dream is 68% after 7 WBTB nights and 90% after 14.

## 6. Safety, risks and evidence-based uses

- **Sleep paralysis (SP):** 7.6% of the general population, 28.3% of students, 31.9% of psychiatric patients and 34.6% of people with panic disorder have had it (35 studies, n=36,533) [81] **[strong]**.
  - Interrupting sleep for 60 min in the lab brought on sleep-onset REM in 71.9% of interruptions and SP in 9.4% (6 episodes) [82]. WBTB is therefore a plausible trigger.
  - Lying on the back was 3–4 times more common during SP than when normally falling asleep [83]. A clinician's review advises against sleeping on the back or front [84] (expert opinion). After WBTB, suggest lying on the side.
- **What goes with SP:** poor or fragmented sleep, stress, trauma and anxiety (42 studies) [85]. SP and lucid dreaming are positively related, and dissociation predicts both (n=1,928) [86].
- **Possible harms (thin evidence):** deliberate induction predicted a rise in dissociation and schizotypy over 2 months (78 students) [87] **[weak, unreplicated]**. Two opinion papers urge caution about fragmenting sleep [88][89]. Breaking sleep up with forced awakenings lowered positive mood more than the same sleep loss from a later bedtime [90] **[moderate]**. Avoid induction on consecutive nights.
- **Who should check with a clinician first:**
  - Bipolar disorder: 20% of 3,140 patients said sleep loss had triggered mania or hypomania [91].
  - Epilepsy: each extra hour of sleep lowered the odds of a next-day seizure (OR 0.91) [92].
  - Psychosis: lucid dreamers with psychosis reported more dream control than healthy lucid dreamers, with no clinical benefit [93].
  - Suggested wording: "if you have a diagnosed sleep, mood, seizure or mental-health condition, ask your clinician first".
- **Nightmares:**
  - Lucid dreaming treatment reduced nightmares in a pilot of 23, but lucidity was not needed for the improvement [94]. A review (4 RCTs) found low internal validity [95].
  - The AASM **recommends** imagery rehearsal therapy. It lists lucid dreaming therapy only as "may be used", and only for nightmare disorder [96] **[guideline]**.
  - The links between lucid-dream frequency and poor sleep, stress and anxiety were explained entirely by nightmares (n=1,332) [97].
  - Add a nightmare tag to the report. If nightmares are frequent, suggest pausing.
- **Benefits:**
  - Nights with a lucid dream were followed by feeling more refreshed than nights with a recalled non-lucid dream (149 people) [98].
  - Creativity improves at sleep onset [41][43].
  - Practising finger tapping in a lucid dream helped about as much as practising awake (online, n=68) [99] **[weak]**.
- **Regulation (US):** the FDA's 2026 general wellness guidance counts "track sleep trends" as a wellness claim. Naming diseases, showing diagnostic thresholds or sending clinical-action alerts takes an app out of that category. A note that "evaluation by a healthcare professional may be helpful" is allowed only if it names no condition, does not call an output abnormal and gives no thresholds [100] **[regulatory]**.

## 7. Open questions

1. No study has tested real-time REM detection from the wrist, cueing in phasic vs tonic REM, or detecting lucidity from heart rate or EDA.
2. Should WBTB be anchored on hours since sleep onset or on habitual wake time? No within-person test exists.
3. The quality of the SM-L500's beat-to-beat intervals during sleep has not been checked against ECG. Do inner-wrist EDA storm thresholds carry over to its back-of-wrist sensor?
4. Do REM cues add anything beyond pre-sleep training? The multi-centre result is a null preprint. The app's blinded cue/no-cue nights are the test.
5. There is no safe maximum number of induction nights, and no study compares consecutive with spaced nights.
6. Do repeated sleep-onset (Dormio) awakenings raise SP?
7. How often does the wrist trigger land in N1 rather than in wake or N2?

## 8. References

1. Silber MH et al. 2007. The visual scoring of sleep in adults. J Clin Sleep Med 3:121–131. https://doi.org/10.5664/jcsm.26814
2. Rosenberg RS, Van Hout S. 2013. AASM inter-scorer reliability program: sleep stage scoring. J Clin Sleep Med 9:81–87. https://pmc.ncbi.nlm.nih.gov/articles/PMC3525994/
3. Boulos MI et al. 2019. Normal polysomnography parameters in healthy adults: systematic review and meta-analysis. Lancet Respir Med 7:533–543. https://doi.org/10.1016/S2213-2600(19)30057-8 (Tables 2–3 via omc.ohri.ca/psgnorms)
4. Roehrs T, Roth T. 2001. Sleep, sleepiness, and alcohol use. Alcohol Res Health 25:101–109. https://pmc.ncbi.nlm.nih.gov/articles/PMC6707127/
5. Somers VK et al. 1993. Sympathetic-nerve activity during sleep in normal subjects. N Engl J Med 328:303–307. https://doi.org/10.1056/NEJM199302043280502
6. Wilde-Frenz J, Schulz H. 1983. Rate and distribution of body movements during sleep in humans. Percept Mot Skills 56:275–283. https://doi.org/10.2466/pms.1983.56.1.275
7. Sano A, Picard RW, Stickgold R. 2014. Quantitative analysis of wrist electrodermal activity during sleep. Int J Psychophysiol 94:382–389. https://pmc.ncbi.nlm.nih.gov/articles/PMC4335672/
8. Cajochen C et al. 2024. Ultradian sleep cycles: frequency, duration, and associations… Sleep Health 10:S52–S62. https://doi.org/10.1016/j.sleh.2023.09.002
9. Dijk DJ, Czeisler CA. 1995. Contribution of the circadian pacemaker and the sleep homeostat… J Neurosci 15:3526–3538. https://pmc.ncbi.nlm.nih.gov/articles/PMC6578184/
10. Czeisler CA et al. 1980. Timing of REM sleep is coupled to the circadian rhythm of body temperature in man. Sleep 2:329–346. https://doi.org/10.1093/sleep/2.3.329
11. Baehr EK, Revelle W, Eastman CI. 2000. Individual differences in the phase and amplitude of the human circadian temperature rhythm. J Sleep Res 9:117–127. https://doi.org/10.1046/j.1365-2869.2000.00196.x
12. Erlacher D, Stumbrys T. 2020. Wake Up, Work on Dreams, Back to Bed and Lucid Dream. Front Psychol 11:1383. https://pmc.ncbi.nlm.nih.gov/articles/PMC7332853/
13. Thase ME et al. 1992. Early return to REM sleep after nocturnal awakening in depression. Biol Psychiatry 31:337–344. https://doi.org/10.1016/0006-3223(92)90203-c
14. Brunner DP, Dijk DJ, Borbély AA. 1993. Repeated partial sleep deprivation progressively changes in EEG… Sleep 16:100–113. https://doi.org/10.1093/sleep/16.2.100
15. Endo T et al. 1998. Selective REM sleep deprivation in humans. Am J Physiol 274:R1186–R1194. https://doi.org/10.1152/ajpregu.1998.274.4.R1186
16. Gardiner C et al. 2025. The effect of alcohol on subsequent sleep in healthy adults: systematic review and meta-analysis. Sleep Med Rev 80:102030. https://doi.org/10.1016/j.smrv.2024.102030
17. Wichniak A et al. 2017. Effects of antidepressants on sleep. Curr Psychiatry Rep 19:63. https://pmc.ncbi.nlm.nih.gov/articles/PMC5548844/
18. Van Cauter E, Leproult R, Plat L. 2000. Age-related changes in slow wave sleep and REM sleep… JAMA 284:861–868. https://doi.org/10.1001/jama.284.7.861
19. Watson NF et al. 2015. Recommended amount of sleep for a healthy adult (AASM/SRS). J Clin Sleep Med 11:591–592. https://pmc.ncbi.nlm.nih.gov/articles/PMC4442216/
20. Trinder J et al. 2001. Autonomic activity during human sleep as a function of time and sleep stage. J Sleep Res 10:253–264. https://doi.org/10.1046/j.1365-2869.2001.00263.x
21. Boudreau P et al. 2013. Circadian variation of heart rate variability across sleep stages. Sleep 36:1919–1928. https://doi.org/10.5665/sleep.3230
22. De Koninck J, Lorrain D, Gagnon P. 1992. Sleep positions and position shifts in five age groups. Sleep 15:143–149. https://doi.org/10.1093/sleep/15.2.143
23. Herlan A et al. 2019. Electrodermal activity patterns in sleep stages… J Sleep Res 28:e12694. https://doi.org/10.1111/jsr.12694
24. Gutierrez G et al. 2016. Respiratory rate variability in sleeping adults without obstructive sleep apnea. Physiol Rep 4:e12949. https://pmc.ncbi.nlm.nih.gov/articles/PMC5027356/
25. Chinoy ED et al. 2021. Performance of seven consumer sleep-tracking devices compared with polysomnography. Sleep 44:zsaa291. https://pmc.ncbi.nlm.nih.gov/articles/PMC8120339/
26. Lee T et al. 2023. Accuracy of 11 wearable, nearable, and airable consumer sleep trackers. JMIR mHealth uHealth 11:e50983. https://pmc.ncbi.nlm.nih.gov/articles/PMC10654909/
27. Walch O et al. 2019. Sleep stage prediction with raw acceleration and PPG heart rate data… Sleep 42:zsz180. https://pmc.ncbi.nlm.nih.gov/articles/PMC6930135/
28. Altini M, Kinnunen H. 2021. The promise of sleep: a multi-sensor approach… Oura ring. Sensors 21:4302. https://pmc.ncbi.nlm.nih.gov/articles/PMC8271886/
29. Radha M et al. 2021. A deep transfer learning approach for wearable sleep stage classification with PPG. NPJ Digit Med 4:135. https://doi.org/10.1038/s41746-021-00510-8
30. Garcia-Molina G, Jiang J. 2022. Interbeat interval-based sleep staging: toward real-time implementation. Physiol Meas 43:045002. https://doi.org/10.1088/1361-6579/ac5a78
31. Guagnano M et al. 2025. On-the-fly sleep scoring algorithm with heart rate, RR intervals and accelerometer. Sensors 25:2141. https://pmc.ncbi.nlm.nih.gov/articles/PMC11991269/
32. Xu S et al. 2026. Accuracy of PPG-derived pulse rate variability vs ECG-derived HRV: systematic review and meta-analysis. Sensors 26:5192. https://pmc.ncbi.nlm.nih.gov/articles/PMC13517957/
33. Kuula L, Pesonen AK. 2021. Heart rate variability and Firstbeat method for detecting sleep stages. JMIR mHealth uHealth 9:e24704. https://pmc.ncbi.nlm.nih.gov/articles/PMC7889416/
34. Khosla S et al. 2018. Consumer sleep technology: an AASM position statement. J Clin Sleep Med 14:877–880. https://pmc.ncbi.nlm.nih.gov/articles/PMC5940440/
35. de Zambotti M et al. 2024. State of the science and recommendations for using wearable technology in sleep and circadian research. Sleep 47:zsad325. https://doi.org/10.1093/sleep/zsad325
36. Nielsen TA. 2000. A review of mentation in REM and NREM sleep. Behav Brain Sci 23:851–866. https://doi.org/10.1017/S0140525X0000399X
37. Siclari F et al. 2013. Assessing sleep consciousness within subjects using a serial awakening paradigm. Front Psychol 4:542. https://pmc.ncbi.nlm.nih.gov/articles/PMC3747360/
38. Noreika V et al. 2009. Early-night serial awakenings as a new paradigm for studies on NREM dreaming. Int J Psychophysiol 74:14–18. https://doi.org/10.1016/j.ijpsycho.2009.06.002
39. Nielsen T. 2017. Microdream neurophenomenology. Neurosci Conscious 2017:nix001. https://pmc.ncbi.nlm.nih.gov/articles/PMC6007184/
40. Haar Horowitz A et al. 2020. Dormio: a targeted dream incubation device. Conscious Cogn 83:102938. https://pmc.ncbi.nlm.nih.gov/articles/PMC7590944/
41. Haar Horowitz A et al. 2023. Targeted dream incubation at sleep onset increases post-sleep creative performance. Sci Rep 13:7319. https://pmc.ncbi.nlm.nih.gov/articles/PMC10185495/
42. Haar Horowitz A et al. 2026. Targeted dream incubation at sleep onset can influence later dream content in REM sleep: a pilot. Front Sleep 5:1812535. https://pmc.ncbi.nlm.nih.gov/articles/PMC13341471/
43. Lacaux C et al. 2021. Sleep onset is a creative sweet spot. Sci Adv 7:eabj5866. https://pmc.ncbi.nlm.nih.gov/articles/PMC8654287/
44. Fosse R, Stickgold R, Hobson JA. 2001. Brain-mind states: reciprocal variation in thoughts and hallucinations. Psychol Sci 12:30–36. https://doi.org/10.1111/1467-9280.00306
45. Siclari F et al. 2017. The neural correlates of dreaming. Nat Neurosci 20:872–878. https://pmc.ncbi.nlm.nih.gov/articles/PMC5462120/
46. Wong W et al. 2020. The Dream Catcher experiment: blinded analyses failed to detect markers of dreaming consciousness. Neurosci Conscious 2020:niaa006. https://pmc.ncbi.nlm.nih.gov/articles/PMC7362719/
47. Eichenlaub JB et al. 2014. Brain reactivity differentiates subjects with high and low dream recall frequencies. Cereb Cortex 24:1206–1215. https://doi.org/10.1093/cercor/bhs388
48. van Wyk M, Solms M, Lipinska G. 2019. Increased awakenings from NREM sleep explain differences in dream recall frequency. Front Hum Neurosci 13:370. https://pmc.ncbi.nlm.nih.gov/articles/PMC6803546/
49. Elce V et al. 2025. The individual determinants of morning dream recall. Commun Psychol 3:25. https://pmc.ncbi.nlm.nih.gov/articles/PMC11836467/
50. Fosse MJ et al. 2003. Dreaming and episodic memory: a functional dissociation? J Cogn Neurosci 15:1–9. https://doi.org/10.1162/089892903321107774
51. Wamsley EJ, Stickgold R. 2019. Dreaming of a learning task is associated with enhanced memory consolidation: replication. J Sleep Res 28:e12749. https://pmc.ncbi.nlm.nih.gov/articles/PMC6338510/
52. Saunders DT et al. 2016. Lucid dreaming incidence: a quality effects meta-analysis of 50 years of research. Conscious Cogn 43:197–215. https://doi.org/10.1016/j.concog.2016.06.002
53. Schredl M, Erlacher D. 2011. Frequency of lucid dreaming in a representative German sample. Percept Mot Skills 112:104–108. https://doi.org/10.2466/09.PMS.112.1.104-108
54. LaBerge SP et al. 1981. Lucid dreaming verified by volitional communication during REM sleep. Percept Mot Skills 52:727–732. https://doi.org/10.2466/pms.1981.52.3.727
55. Baird B, Mota-Rolim SA, Dresler M. 2019. The cognitive neuroscience of lucid dreaming. Neurosci Biobehav Rev 100:305–323. https://pmc.ncbi.nlm.nih.gov/articles/PMC6451677/
56. Baird B, Tononi G, LaBerge S. 2022. Lucid dreaming occurs in activated REM sleep, not a mixture of sleep and wakefulness. Sleep 45:zsab294. https://doi.org/10.1093/sleep/zsab294
57. Demirel Ç et al. 2025. Electrophysiological correlates of lucid dreaming: sensor and source level signatures. J Neurosci 45:e2237242025. https://pmc.ncbi.nlm.nih.gov/articles/PMC12079745/
58. Dodet P et al. 2015. Lucid dreaming in narcolepsy. Sleep 38:487–497. https://pmc.ncbi.nlm.nih.gov/articles/PMC4335518/
59. Konkoly KR et al. 2021. Real-time dialogue between experimenters and dreamers during REM sleep. Curr Biol 31:1417–1427. https://pmc.ncbi.nlm.nih.gov/articles/PMC8162929/
60. Türker B et al. 2023. Behavioral and brain responses to verbal stimuli reveal transient periods of cognitive integration during sleep. Nat Neurosci 26:1981–1993. https://pmc.ncbi.nlm.nih.gov/articles/PMC10620087/
61. Ermis U, Krakow K, Voss U. 2010. Arousal thresholds during human tonic and phasic REM sleep. J Sleep Res 19:400–406. https://doi.org/10.1111/j.1365-2869.2010.00831.x
62. Stumbrys T et al. 2014. The phenomenology of lucid dreaming: an online survey. Am J Psychol 127:191–204. https://doi.org/10.5406/amerjpsyc.127.2.0191
63. Carr M et al. 2023. Combining presleep cognitive training and REM-sleep stimulation in a laboratory morning nap. Psychol Conscious 10:413–430. https://doi.org/10.1037/cns0000227
64. Voss U et al. 2013. Measuring consciousness in dreams: the LuCiD scale. Conscious Cogn 22:8–21. https://doi.org/10.1016/j.concog.2012.11.001
65. Mainieri G et al. 2021. Are sleep paralysis and false awakenings different from REM sleep and lucid REM sleep? J Clin Sleep Med 17:719–727. https://pmc.ncbi.nlm.nih.gov/articles/PMC8020694/
66. LaBerge S, LaMarca K, Baird B. 2018. Pre-sleep treatment with galantamine stimulates lucid dreaming. PLoS One 13:e0201246. https://pmc.ncbi.nlm.nih.gov/articles/PMC6082533/
67. Aspy DJ (now Adventure-Heart). 2020. Findings from the International Lucid Dream Induction Study. Front Psychol 11:1746. https://pmc.ncbi.nlm.nih.gov/articles/PMC7379166/
68. Picard-Deland C et al. 2026 (v2). Inducing lucid dreaming with multisensory stimulation: a preregistered multi-center study. bioRxiv, **preprint, not peer reviewed**. https://doi.org/10.1101/2024.06.21.600133
69. Salvesen LAC. PhD thesis, IMT School for Advanced Studies Lucca, ch. 3. https://iris.imtlucca.it/retrieve/6adaf3c5-dae8-4b78-9588-fd2da265b77f/Thesis_SALVESEN_final%20version..pdf
70. Konkoly KR et al. 2024. Provoking lucid dreams at home with sensory cues paired with pre-sleep cognitive training. Conscious Cogn 125:103759. https://pmc.ncbi.nlm.nih.gov/articles/PMC11542932/
71. Gott J et al. 2021. Virtual reality training of lucid dreaming. Phil Trans R Soc B 376:20190697. https://pmc.ncbi.nlm.nih.gov/articles/PMC7741087/
72. Mota-Rolim SA et al. 2019. Portable devices to induce lucid dreams: are they reliable? Front Neurosci 13:428. https://pmc.ncbi.nlm.nih.gov/articles/PMC6517539/
73. Peters E et al. 2026. Investigating the transfer of physical stimuli to the dreamed body. PLoS One 21. https://pmc.ncbi.nlm.nih.gov/articles/PMC13667795/
74. Peters E et al. 2026. Bodily stimulation during REM sleep: muscle and vestibular cueing on lucid dreaming. Sleep Adv 7:zpag074. https://pmc.ncbi.nlm.nih.gov/articles/PMC13464720/
75. Voss U et al. 2014. Induction of self awareness in dreams through frontal low current stimulation of gamma activity. Nat Neurosci 17:810–812. https://doi.org/10.1038/nn.3719
76. Blanchette-Carrière C et al. 2020. Attempted induction of signalled lucid dreaming by tACS. Conscious Cogn 83:102957. https://doi.org/10.1016/j.concog.2020.102957
77. Stumbrys T, Erlacher D, Schredl M. 2013. Testing the involvement of the prefrontal cortex in lucid dreaming: a tDCS study. Conscious Cogn 22:1214–1222. https://doi.org/10.1016/j.concog.2013.08.005
78. Baird B et al. 2019. Increased lucid dream frequency in long-term meditators but not following MBSR training. Psychol Conscious 6:40–54. https://pmc.ncbi.nlm.nih.gov/articles/PMC6490164/
79. Stumbrys T et al. 2012. Induction of lucid dreams: a systematic review of evidence. Conscious Cogn 21:1456–1475. https://doi.org/10.1016/j.concog.2012.07.003
80. Tan S, Fan J. 2023. A systematic review of new empirical data on lucid dream induction techniques. J Sleep Res 32:e13786. https://doi.org/10.1111/jsr.13786
81. Sharpless BA, Barber JP. 2011. Lifetime prevalence rates of sleep paralysis: a systematic review. Sleep Med Rev 15:311–315. https://pmc.ncbi.nlm.nih.gov/articles/PMC3156892/
82. Takeuchi T et al. 1992. Isolated sleep paralysis elicited by sleep interruption. Sleep 15:217–225. https://doi.org/10.1093/sleep/15.3.217
83. Cheyne JA. 2002. Situational factors affecting sleep paralysis: position and timing effects. J Sleep Res 11:169–177. https://doi.org/10.1046/j.1365-2869.2002.00297.x
84. Sharpless BA. 2016. A clinician's guide to recurrent isolated sleep paralysis. Neuropsychiatr Dis Treat 12:1761–1767. https://pmc.ncbi.nlm.nih.gov/articles/PMC4958367/
85. Denis D, French CC, Gregory AM. 2018. A systematic review of variables associated with sleep paralysis. Sleep Med Rev 38:141–157. https://doi.org/10.1016/j.smrv.2017.05.005
86. Denis D, Poerio GL. 2017. Terror and bliss? Sleep paralysis, lucid dreaming, and waking life experiences. J Sleep Res 26:38–47. https://pmc.ncbi.nlm.nih.gov/articles/PMC5245115/
87. Aviram L, Soffer-Dudek N. 2018. Lucid dreaming: intensity, but not frequency, is inversely related to psychopathology. Front Psychol 9:384. https://pmc.ncbi.nlm.nih.gov/articles/PMC5875414/
88. Soffer-Dudek N. 2020. Are lucid dreams good for us? A call for caution. Front Neurosci 13:1423. https://pmc.ncbi.nlm.nih.gov/articles/PMC6993576/
89. Vallat R, Ruby PM. 2019. Is it a good idea to cultivate lucid dreaming? Front Psychol 10:2585. https://pmc.ncbi.nlm.nih.gov/articles/PMC6874013/
90. Finan PH, Quartana PJ, Smith MT. 2015. Effects of sleep continuity disruption on positive mood and sleep architecture. Sleep 38:1735–1742. https://pmc.ncbi.nlm.nih.gov/articles/PMC4813351/
91. Lewis KS et al. 2017. Sleep loss as a trigger of mood episodes in bipolar disorder. Br J Psychiatry 211:169–174. https://pmc.ncbi.nlm.nih.gov/articles/PMC5579327/
92. Haut SR et al. 2007. Seizure occurrence: precipitants and prediction. Neurology 69:1905–1910. https://doi.org/10.1212/01.wnl.0000278112.48285.84
93. Mota NB et al. 2016. Psychosis and the control of lucid dreaming. Front Psychol 7:294. https://pmc.ncbi.nlm.nih.gov/articles/PMC4783408/
94. Spoormaker VI, van den Bout J. 2006. Lucid dreaming treatment for nightmares: a pilot study. Psychother Psychosom 75:389–394. https://doi.org/10.1159/000095446
95. Ouchene R et al. 2023. Effectiveness of lucid dreaming therapy in patients with nightmares: a systematic review. Encephale 49:525–531. https://doi.org/10.1016/j.encep.2023.01.008
96. Morgenthaler TI et al. 2018. Position paper for the treatment of nightmare disorder in adults (AASM). J Clin Sleep Med 14:1041–1055. https://pmc.ncbi.nlm.nih.gov/articles/PMC5991964/
97. Carr M et al. 2025. Effects of lucid dreaming and nightmares on sleep quality and mental health outcomes. Behav Sleep Med 23:133–140. https://doi.org/10.1080/15402002.2024.2423297
98. Schredl M, Dyck S, Kühnel A. 2020. Lucid dreaming and the feeling of being refreshed in the morning. Clocks Sleep 2:54–60. https://pmc.ncbi.nlm.nih.gov/articles/PMC7445824/
99. Stumbrys T, Erlacher D, Schredl M. 2016. Effectiveness of motor practice in lucid dreams. J Sports Sci 34:27–34. https://doi.org/10.1080/02640414.2015.1030342
100. US FDA, CDRH. 2026 (issued 6 Jan 2026; replaces the 2019 version). General Wellness: Policy for Low Risk Devices. https://www.fda.gov/media/90652/download

### Dropped after checking

- **No DOI or PubMed record:**
  - LaBerge, Levitan & Dement 1986 (J Mind Behav). Only an author upload exists, so its exact figures (r=.98, lucidity rare in the first ~5 min of REM, 115 s of REM after the eye signal) are dropped. Only what review [55] reports is kept.
- **Preprint, superseded or too small:**
  - The medRxiv "50–70% REM under-count" preprint.
  - Wolk 2023 (PsyArXiv preprint, n=5).
- **Not opened, or blocked:**
  - Schredl 2025 (IJoDR), seen only through a search summary.
  - Schadow 2018 and Buzzi 2011 (IJoDR DOIs did not resolve).
  - Peters 2023 (Somnologie).
  - Stumbrys 2023 (adverse-effects survey), Stumbrys 2018 and Konkoly & Burke 2019: no abstract.
  - Schredl 2002 (diary study) and Cohen & Wolfe 1973: abstracts not read.
  - Ohayon 2004: numbers behind a paywall.
  - Koulack & Goodenough 1976 is used only as Eichenlaub 2014 [47] describes it.
- **Corrected against the primary source:**
  - Elce 2025 is volume 3, article 25 (not 29).
  - Erlacher 2020's stage-3 "2.5–8.1%" is a range of condition means, not of individual people.
  - Peters 2026 (PLOS ONE): the vibration effect was not significant (p=.214).
  - Konkoly 2021: the Germany WBTB "5 of 40" could not be matched; the paper reports 60 sessions with communication attempts, 5 of them with verified lucidity. Left out.
  - Demirel 2025: participant counts not verified; numbers left out.
- **Verified but left out for length** (no change for the app): Voss 2009, Dresler 2012, Baird 2018, Filevich 2015, Gackenbach 2006, Picard-Deland 2022, Gerhardt 2024, Wang 2026, De Pisapia 2026, Tallal 2026, Miller 2022, Schyvens 2024, Birrer 2024, Fonseca 2020, Beattie 2017, Long 2015, Douglas 1982, Peever 2017, Hobson 1978, Sarabia 2008, Cuellar 2015, Singh 2006, Ogilvie 2001, Borbély 2016, Moser 2009, Barbato 1998, Wehr 1992, Stickgold 2000/2001, Siclari 2018, Vallat 2020, Marzano 2011, the dream-lag papers, Wamsley 2010, Picard-Deland 2023, Malinowski 2014, Rak 2015, Gott 2020, Mallett 2022, the IRT meta-analyses, Holzinger 2015, Lancee 2010, Pilcher 1996, Jalal 2020, Stocks 2020, Schädlich 2017.
