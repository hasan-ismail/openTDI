# Dream journal, dream signs and guided WBTB: research reference for OpenTDI (2026-10-28)

This file covers dream signs, what a dream journal can find in the sleeper's own reports, offline transcription of spoken reports, the privacy of dream content, and a guided wake-back-to-bed (WBTB) routine. It extends sleep_and_dreams.md and lucid_induction.md and does not repeat them: SD[n] is reference n in sleep_and_dreams.md §8, and LI §x is a section of lucid_induction.md. Research claims carry an evidence tag: **[strong]**, **[moderate]**, **[weak]**, **[contested]** or **[preprint]**. **ENG** marks an engineering fact from official docs or source code, with its source named. **INFERENCE** is reasoning from cited facts, and **GUESSED** is a design value with no source. Bracketed numbers point to A12. This is wellness information, not medical advice.

## A1. Dream signs in MILD

| Claim | Source | Tag |
|---|---|---|
| MILD came from a 3-year self-study by its author: 389 lucid dreams in total; under 1 a month with no technique, 5.4 a month with autosuggestion, 21.5 a month with MILD (range 18–26) | [1] (abstract) | [weak; n=1, the author]. 389 and 18–26 also match a secondary description; 5.4 and 21.5 could not be re-confirmed |
| The originator's description of MILD: recall a dream, ideally one just woken from; find a recurring oddity (a "dreamsign"); rehearse recognising that it only happens in dreams; recite the intention; picture going back in, noticing the sign, becoming lucid and doing a chosen action. MILD is framed as **prospective memory** (remembering to do something later) | SD[66] (introduction) | Technique description, not a test |
| The lab WBTB "dreamwork" had 3 steps: (1) write the report; (2) find dream signs, meaning "incongruous elements … an odd form, action, context"; (3) do MILD with that report and those signs. The study did not test whether the signs mattered | SD[12] (methods) | Method only |
| ILDIS MILD asks people to picture "noticing something unusual that causes them to realize they are dreaming". The article never uses the word "dreamsign". Lucid nights had higher dream recall: 2.8 vs 1.7 dreams per day (r=.23); dream-quantity score 10.2 vs 5.2 (r=.26) | SD[67] | [moderate] |
| 2012 systematic review: MILD was tested in 10 studies, 9 of them by LaBerge's group; quality score 5.9 and reporting 2.1 of 11. Weak but significant link between MILD practice and lucidity (r=0.124). MILD worked best after 30–120 min awake | SD[79], read in the copy inside Stumbrys's 2014 Heidelberg dissertation [2] | [weak–moderate] |
| Purcell 1988, as tabled in that review. Baseline (dream reports only): 12.5% of participants lucid (1.6% of dreams). Attention Control (reports, a report-skills questionnaire, weekly meetings): 10% (0.9%). Schema (Attention Control plus a dream-control questionnaire and a bracelet cue): 50% (13.1%). Every arm kept a journal; no arm tested journaling against no journaling | Purcell 1988 thesis, via the SD[79] table | [weak; thesis, second-hand] |

**Gap.**
- No study compares MILD with a dream-sign step against MILD without it.
- No study tests a cue or intention built from a person's own dream content.
- Every successful MILD protocol (SD[66], SD[67], SD[12]) rehearses the person's own recent dream, but that was never tested against generic material.
- The home TLR training (SD[70]) was generic.
- OpenTDI uses the peer-reviewed wording "odd form, action, context" (SD[12]). The four dreamsign categories come from a popular book and from LaBerge & DeGracia 2000, which was not opened, so they are not used.

## A2. Why a specific personal cue might help (waking-life evidence only)

- **If-then plans.** Across 94 tests, plans of the form "if situation Y, then I do X" improved goal attainment, d=.65. They made the cue situation easier to notice and the response more automatic [3] **[strong for waking goals]**.
  - INFERENCE: "When I see my old school, I'll know I'm dreaming" is an if-then plan. The generic MILD phrase is not.
- **Sleep and remembering to act.** Sleep helped prospective memory, d=0.41 (24 samples). The benefit was larger when the cue was likely to bring the plan back by itself (d=0.94) than when it was not (d=0.45, not significant; CI −0.02 to 0.93). Sleep did not help active monitoring [4] **[moderate; waking tests after sleep, not in dreams]**.
- **Open intentions show up in dreams.** Tasks still to be done appeared in dreams more often than completed ones (p<.05; n=19, 86 German lab awakening reports, scored by GermanBERT text similarity). The effect was significant for only 2 of 3 task plans, and the whole-text comparison across the three conditions was not significant (p=.44) [5] **[weak]**.
- **Already in the other two files:** pre-sleep focus doubles dreams of the chosen thought (Wang 2022, LI §7.4), and Dormio theme incorporation is 67% vs 3% (SD[40]).
  - INFERENCE: rehearsing a sign at bedtime may make the sign itself more likely to appear. That helps for neutral signs and hurts for frightening ones (A7).

**Verdict:** tailoring is reasonable but **unproven**. Present it as a "practice aid", never as "makes lucidity more likely".

## A3. What will recur in the owner's reports

- Reports draw on recent waking life, but rarely replay an episode (SD[50]).
- The more time spent on an activity, the more it featured in dreams. Reading appeared less than sport despite more time spent on it [6] **[moderate]**. Six activities in 100 adolescents showed the same pattern [7] **[moderate]**.
- An automatic tool on about 24,000 DreamBank reports supported continuity with waking life at the group level [8] **[moderate]**.
- INFERENCE: a frequency list will be dominated by partner, home and work. Those recur but are not odd, so the owner, not the counter, decides what is a sign.

## A4. Automatic analysis of dream text: what simple methods can and cannot do

| Source | Result | Tag | Use in v1 |
|---|---|---|---|
| Fogli, Aiello & Quercia 2020 [8]: automatic Hall–Van de Castle scoring | Group-level average error 0.24. Element-set F1 against hand-coded sets: all characters 0.75; dead or imaginary characters 0.22; friendly 0.36; aggressive 0.36; sexual 0.17; emotions 0.27 | [moderate] | Aggregates only. No emotion, interaction or character labels |
| Bertolini, Elce et al. 2024 [9] (CLPsych, peer-reviewed workshop): BERT-large-cased fine-tuned on 922 English DreamBank reports | Weighted F1 0.85–0.87 for emotions. Authors: small dataset, English only, "no diagnostic valence" | [moderate] | Not in v1: needs labelled data, a 340M-parameter model, and is out of wellness scope |
| Elce, Handjaras & Bernardi 2021 [10], review | Dictionary methods can be applied to datasets of any size and may be the only viable option for small ones; embeddings need several million words. Dictionaries handle negation only partly: in the review's LIWC example, "not scared" still scored 40% fear. Oral reports average about 150 words vs about 80 written, with false starts. Automatic transcription "may lead to more frequent transcription errors" | [moderate; review] | Word counts only. INFERENCE: 100 nights × 150 words ≈ 15k words, far too small for embeddings |
| Fechner 2024 [5] | Similarity to a target, tested only with targets known in advance (German reports, GermanBERT) | [weak] | Possible v2: "did my sign appear?" against confirmed signs |

| Simple on-device counting can find | It cannot find |
|---|---|
| Words recurring across nights (counted by nights, not mentions) | Oddness (a dead relative alive, flying, the wrong house) |
| Words that appear together across nights | Negation ("I wasn't flying") |
| Names and places the owner uses | Synonyms, pronoun referents, metaphor |
| Words the owner marks as odd, and the owner's own form/action/context taps, counted across nights | Speech-recognition errors; empty or white reports |

## A5. Offline transcription: evidence

- **No validation of speech recognition on dream reports or sleepy speech was found.** The model choice must come from a comparison on the owner's own recordings.
- **Whisper invents text.**
  - About 1% of transcriptions (1.4% on average) contained hallucinated content.
  - Speakers with aphasia hallucinated more (1.7% vs 1.2%, p=0.019).
  - Hallucinating audio had more non-vocal time (29% vs 26%).
  - Source: [11] **[moderate; API Whisper 2023, one dataset]**.
  - INFERENCE: sleepy reports with long pauses fit this risk profile.
- **Non-speech sounds alone trigger recurring Whisper phrases** [12] **[moderate; abstract only, no numbers used]**.
- **Whisper's own long-form workarounds** (beam search, temperature fallback, a no-speech threshold, conditioning on the previous text, and an initial-timestamp constraint) together cut average long-form WER from 11.0 to 10.0. The authors call them "a workaround for the noisy predictions" [13] **[moderate; read- and long-form benchmarks]**.
  - Greedy WER, for reference: tiny.en LibriSpeech clean 5.6 / other 14.6; base.en 4.2 / 10.2 [13] **[moderate; read-speech benchmarks]**.
- **External VAD helps.** Cutting audio with a voice-activity detector (VAD) and merging the segments cut insertions and repetition:
  - TED-LIUM: insertion error rate 7.7 → 6.7; five-gram duplicates 189 → 129.
  - Kincaid46: insertion error rate 3.2 → 2.2; duplicates 131 → 75.
  - A CTC model, wav2vec2.0, repeated less than Whisper on Kincaid46 (29 vs 131 duplicates) but more on TED-LIUM (221 vs 189, with insertion error rate 8.5 vs 7.7). Its WER was worse on both.
  - The authors call wav2vec2.0 "far less prone to repetition errors", but their own TED-LIUM figures say the opposite. So [14] gives mixed support for the idea that CTC models repeat less.
  - Source: [14] **[moderate; one paper, a different CTC model]**.
- **Moonshine.**
  - Tiny vs Whisper tiny.en average WER: 12.66 vs 12.81.
  - Hallucinations rise on clips longer than its 4–30 s training clips; clips under 1 s tend to repeat tokens.
  - Source: [15] **[preprint]**.
- **TDT decoding method**, which the Parakeet model family uses: [16] (method only; no accuracy claim for the checkpoints in A9).

## A6. Privacy of dream content

- 33 of 36 top depression and smoking-cessation apps sent data to third parties. 29 of 36 sent it to Google or Facebook, often undisclosed [17] **[moderate; abstract]**. Dream reports name third parties and can be intimate (INFERENCE).
- In a survey of 281 respondents, mostly from an online dreaming community, dream tracking was "universally constrained by the ephemeral dreamset", the short window before a dream fades [18] **[weak; abstract only]**. This supports capture first, analysis later.
- ENG (developer.android.com, Auto Backup [E14]):
  - For apps targeting API 31+, on some manufacturers' devices `android:allowBackup="false"` "doesn't disable device-to-device transfers".
  - A `dataExtractionRules` file with `<device-transfer>` and `<cloud-backup>` sections controls this.
  - Checked in the repo on 2026-10-28: both manifests have `allowBackup="false"` and no `dataExtractionRules`.
- ENG (developer.android.com, Data Layer overview [E15]; android_platform_notes.md §4):
  - The phone and watch apps exchange cues, commands, night files and dream recordings through Google Play services' Wear Data Layer (`play-services-wearable`, used in `CuePlayer.kt`, `SessionFilesService.kt` and `WristSpeakerService.kt`).
  - It uses Bluetooth when the devices are connected. Otherwise it can go over an available network, "using a network node on Google's servers as an intermediary". That cloud traffic is end-to-end encrypted.
  - The docs say: "Assume that data transmitted using Data Layer may at some point use Google-owned servers."
  - So recordings and transcripts can be stored only on the phone and watch, but anything sent between them may be relayed through Google's servers, end-to-end encrypted. A privacy statement must not say that recordings never leave the two devices.

## A7. Risks: rumination, nightmares, mood

- Nightmares explained the links between lucid-dream frequency and poor sleep, stress and anxiety (SD[97]).
- The AASM recommends imagery rehearsal therapy, which rewrites the nightmare and rehearses the new version. It lists lucid dreaming therapy only as "may be used" (SD[96]).
  - INFERENCE: MILD re-entry rehearsal of a nightmare is the opposite of rewriting it. Never offer nightmare-tagged reports as MILD material by default.
- Dream mood carried into morning mood; dreams about death or the body went with a worse morning mood [19] **[moderate; home diaries, correlational]**.
  - INFERENCE: do not surface charged words on the morning screen unprompted.
- **No study was found on rumination caused by dream journaling or recurrence lists.** So OpenTDI's planned safeguards are GUESSED:
  - nightmare-tagged reports are never offered as MILD material;
  - after 2 or more nightmares in 7 days, the app shows its "consider pausing induction" note (SD §6);
  - nightmare reports stay out of recurring-word counts unless the owner opts in;
  - no interpretation text appears anywhere.
- Interpretations ("this means anxiety") move toward health claims. Name no conditions and give no thresholds (SD[100]).

## A8. Guided WBTB routine: evidence not already in LI §2, §5 or SD §5

**MILD at WBTB (ILDIS details, SD[67])**
- Instructions were **written** documents kept by the bed.
- There was no fixed number of repetitions: people continued "until they felt their intention was set" and logged 13.6 ± 11.6.
- Technique length: 8.7 ± 9.2 min, and it did not predict lucidity (9.4 vs 8.6 min, p=.398).
- People fell asleep during the technique on 70.7% of occasions (1,162), unrelated to lucidity (p=.966).
- Time to sleep after stopping: 19.5 ± 52.0 min.
- The authors' reason for the 10-min effect (numbers in LI §2): a quick return to sleep "makes it more likely that the MILD intention will persist into REM sleep" **[moderate; observational]**.
- NALDIS, as reported in SD[67]: lucid on 45.8% of occasions when asleep within 5 min of finishing MILD, "86.2% more often" than otherwise **[weak; second-hand, one week, self-report]**.

**SSILD procedure (academic descriptions only)**
- ILDIS (SD[67]):
  - 4 fast cycles of sight, sound and body at 2–3 s per step, then 4–6 slow cycles at about 20 s per step, then "fall asleep as normal".
  - Participants were "told not to count the number of seconds".
  - Logged: 4.0 ± 2.2 fast and 5.1 ± 4.1 slow cycles.
- Salvesen thesis, ch. 3 and Supplementary Text II.1 (SD[69]): a **pre-recorded ~30-min voice track**, done lying in bed.
  - Wording, quoted: *"focus all your attention on the darkness behind your closed eyelids… don't try to see anything"*; *"shift all of your attention to your ears…"*; *"Feel the weight of your blanket, your heartbeat, the temperature of the air… tingling, heaviness, lightness, spinning sensations… simply relax, observe them passively and try not to get excited"*; intrusive thoughts: *"intentionally let them go"*.
  - Block 1: 1 min of fast cycles, then "four to six slower cycles… approximately 20 seconds", then 4 min of silence. The script says "you should complete at least 4 cycles" and the thesis summary says "4 uncued slower cycles", so 4 min fits (20 s × 3 steps × 4).
  - Block 2: 6 cues 1 min apart, each followed by the Carr prompt. The cues alternated light, sound and touch, marking the change from one sense to the next.
  - Block 3: 6 cues without the prompt, then "you can fall asleep normally".
  - One inconsistency: the thesis describes Block 3 as "8 minutes of cueing without vocal prompts", but the script says "over the next 6 minutes" (6 cues 1 min apart).
  - **[weak; thesis]**

**Spoken TLR training (Carr 2023, SD[63])**
- Training lasted 20 min, lying in bed.
- Cue level was set lying in bed so they "could easily notice the cue but felt it would not wake them up".
- Carr's cue was 500/700/900 Hz beeps plus a red LED. **The app's 400/600/800 Hz tone is Konkoly's (SD[70]).**
- Read-aloud instruction: *"Whenever you hear or see one of the cues, you should remain in the same position with your eyes closed, but you will become lucid by attending to where your mind has been, attending to your body, and attending to your surroundings. Focus on how aspects of your experience might be in any way different from your normal waking experience."*
- Minutes 0–5: a cue about every minute, each followed by: *"As you notice the cue, you become lucid. Bring your attention to your thoughts, notice how your mind has wandered… Now observe your body, sensations, and feelings… Observe your breathing… remain critically aware, lucid, and notice how aspects of this experience are in any way different from your normal waking experience."*
- Minutes 5–13: cues with no prompt. Minutes 13–20: cues spaced further apart, and people were told they "could begin to fall asleep" **[moderate]**.

**Home TLR app (Konkoly 2024, SD[70]; adds to LI §1)**
- Guidance after each of the first 4 cues, then 12 wordless cues at gaps of 45, 70, 55, 65, 70, 80, 65, 60, 75, 75, 90 and 120 s.
- Nights 2–7: the guidance was given once.
- The phone lay face down by the pillow, with volume at maximum for training.
- The paper does not say whether the voice was human or synthetic.

**Awake period and activity (adds to LI §5.1)**
- Erlacher & Stumbrys 2020 (SD[12]): 30-min arm 35.7% self-reported, 14% signal-verified, sleep latency 35.6 min. Reading arm: latency 19.3 min. Wii arm: 37.0 min. Authors: "the activity does matter" **[weak; groups of 11–15]**.
- Erlacher 2022 (LI §5.6): woken at about 4.5 h, about 1 h awake (report, dream signs, MILD sheet), then about 20 min of MILD in bed.
  - Lucid by the loose criterion 4/15, 0/20 and 3/15 (strict: 2/15, 0/20, 3/15); sleep latency 12.0 ± 7.4 min **[weak]**.
- Klofat 2024 (LI §5.6): **30% had trouble falling back asleep** after a 30-min WBTB with no technique; latency 27 min on average (range 1 min to 2 h) **[weak; online, one night]**.

**Audio guidance vs written instructions**
- **No study compares them.** Non-TLR home studies used text; the TLR protocols used spoken guidance with confounds (lab, PSG, naps, cues).
- Autogenic training and guided imagery for insomnia (7 trials within a 13-trial hypnotherapy review): sleep latency shorter than waitlist (SMD −1.16, p=.003) but not shorter than sham (SMD −0.50, p=.15). 11 of the 13 trials were low quality [20] **[weak]**.
- An engaging imagery task shortened sleep latency vs no instructions (n=41, insomnia) [21] **[weak; abstract]**.
- That SSILD/MILD as imagery tasks help people fall back asleep is GUESSED.

**Safety (applies SD §6)**
- Back-lying is 3–4× more common during sleep paralysis (SD[83]). The advice to lie on the side is expert opinion (SD[84]).
- Sleep interruption elicited sleep paralysis in 9.4% of interruptions (SD[82]).
- The SSILD body step invites tingling, heaviness and spinning sensations. Whether it raises sleep paralysis: **no data**.

## A9. Engineering facts (ENG)

| Fact | Source |
|---|---|
| The shipped `mobile/libs/sherpa-onnx-1.13.7.aar` contains `OfflineRecognizer` (configs for transducer, Whisper, Moonshine, NeMo CTC, SenseVoice and more), `OfflineStream`, `Vad` and `SileroVadModelConfig`. `OfflineRecognizer(null, config)` (first parameter `AssetManager?`) loads from file paths, like `NeuralVoice.kt` | The AAR's `classes.jar`; sherpa-onnx v1.13.7 Kotlin API [E1] |
| `OfflineStream.acceptWaveform` resamples internally (`LinearResample`) when the rate differs from the 16 kHz feature config. sherpa's exported `silero_vad.onnx` (and `.int8.onnx`) accepts only 16 kHz, and the C++ code checks for 16000. Upstream Silero v4/v5 also take 8 kHz | `offline-stream.cc` and the VAD C++ code [E1]; sherpa VAD docs [E2] |
| Silero defaults: threshold 0.5, minSilence 0.25 s, minSpeech 0.25 s, window 512, maxSpeech 5 s. Past maxSpeech the detector forces a cut (threshold 0.90, silence 0.1 s) | `SileroVadModelConfig`, `voice-activity-detector.cc` [E1] |
| The v1.13.7 Kotlin source predefines `sherpa-onnx-nemo-parakeet_tdt_ctc_110m-en-36000-int8` (type 33: `nemo = OfflineNemoEncDecCtcModelConfig("model.int8.onnx")`, `tokens.txt`) and the Moonshine `*-en-quantized-2026-02-27` models as types 53 (tiny-en) and 59 (base-en) | `getOfflineModelConfig` in `OfflineRecognizer.kt` [E1] |
| Parakeet TDT-CTC 110M int8: `model.int8.onnx` 131,652,171 B, `tokens.txt` 9,953 B. Archive sha256 `17f945007b52ccd8b7200ffc7c5652e9e8e961dfdf479cefcabd06cf5703630b`. File sha256: model `9177a9146cf32ee0cc8152276ef95116f312018d316be37ccf57f7efea81fc1a`, tokens `450e56bd2f036fe5b6aa821865838cc5aa9d8b0106134ce9a9ba0664abe6cd10`. Outputs punctuation and capitals | asr-models release [E3]; sherpa NeMo CTC docs [E4] |
| Parakeet licence CC-BY-4.0. The archive ships no LICENSE file | NVIDIA model card [E5] (licence only); [E3] |
| Moonshine tiny-en quantized 2026-02-27: `encoder_model.ort` 13,281,600 B, `decoder_model_merged.ort` 30,412,256 B, `tokens.txt` 549,350 B (44.2 MB). English models MIT; other languages non-commercial | asr-models release [E3]; LICENSE inside the archive; [E6] |
| `silero_vad.onnx` 643,854 B (629 KB), sha256 `9e2449e1087496d8d4caba907f23e0bd3f78d91fa552479bb9c23ac09cbb1fd6`, MIT | [E3]; [E7] |
| Whisper tiny.en int8 about 117 MB (12 MB encoder + 105 MB decoder); base.en int8 160.6 MB. MIT | tiny.en: sherpa tiny.en docs [E8]; base.en: [E3]; licence: [E8] |
| Parakeet TDT 0.6B v2 int8 (`parakeet-tdt-0.6b-v2-int8`) on RK3588 (Cortex-A76): RTF 0.220 / 0.142 / 0.118 / 0.088 at 1–4 threads. No official phone figures | sherpa NeMo transducer docs [E9] |
| The watch records MPEG-4/AAC at 16,000 Hz, 32 kbps. Channel count is not set | `wear/.../DreamReportScreen.kt:304-307` (checked 2026-10-28) |
| AAC LC in MPEG-4 is a platform-supported decode format. `csd-0` comes with the extractor's format and must not be resubmitted. Output is 16-bit PCM unless float is confirmed | [E10]; [E11] |
| `java.util.zip` has zip and gzip, no bzip2 or tar | [E12] |
| `RecognizerIntent.EXTRA_AUDIO_SOURCE` (API 33) passes PCM to `SpeechRecognizer` through a ParcelFileDescriptor; if unsupported, "the recognizer will open the mic". On-device mode makes no documented promise that audio stays on the device. Samsung's on-device component is undocumented | [E13]; android_audio_stt_notes.md §6 |
| `dataExtractionRules` with `<device-transfer>` and `<cloud-backup>` (API 31+) | [E14] |

## A10. Corrections and conflicts for the existing docs

1. **Schredl, Dyck & Kühnel 2020.** LI §5.1 and §5.4 say n=50, and LI §5.5 says the source audit matched the paper. Klofat 2024 describes the study as 43 psychology students, 46 lucid dreams in 256 WBTB+MILD nights (18%) vs 6%. It may be enrolled vs analysed. **Re-open the paper and state both if both are true.** Still open: the abstract could not be opened.
2. **LI §6.2** says "SSILD counts: … Use the tutorial.", and LI §6.3 item 11 plans the audio guide with the same tutorial counts. The tutorial is not an allowed source. **Use the ILDIS and Salvesen counts (A8).**
3. **LI §5.2 awake-period order** is report, signs, cue retraining, then technique last. The planned guided back-to-sleep routine instead runs MILD recap, SSILD, cue training, then the MILD intention last, then wordless cues while falling asleep. That follows Salvesen and Konkoly. Both orders are GUESSED. For the in-bed part, the guided routine replaces the §5.2 order.
4. **`android_audio_stt_notes.md` D9** (transcription with the on-device `SpeechRecognizer`) is superseded for watch recordings by offline transcription on the phone with sherpa-onnx, whose library the app already ships (A9). Reasons, from A9: Samsung's on-device recognizer is undocumented, on-device mode makes no documented promise that audio stays on the device, and if the audio-source extra is unsupported the recognizer opens the mic.
5. **LI §5.5** lists Stumbrys 2012 as not opened. It has since been read through the author's dissertation copy [2], and SD[79] cites it.

## A11. Do not claim

- That dream signs, personalised MILD or the guided routine raise lucid-dream rates. None was tested in isolation, and no audio-vs-text or routine-vs-none study exists.
- That REM cues add anything. The final multi-centre version is a null result **[preprint]** (SD[68]).
- That the app detects dream signs, emotions, themes or meanings, or that recurring words say anything about mental state.
- That transcripts are accurate for dream reports. They are unvalidated. A transcript is "a written copy of your dream report".
- That recordings never leave the phone and watch. Transfers between them may be relayed through Google's servers (A6).
- For a realistic expectation, see LI §5.4.

## A12. References

New in this file; SD and LI references are cited in place.

1. LaBerge SP 1980. Lucid dreaming as a learnable skill: a case study. *Percept Mot Skills* 51:1039–1042. https://doi.org/10.2466/pms.1980.51.3f.1039 (abstract)
2. Stumbrys T 2014. Dissertation, Heidelberg University (contains the author's copy of SD[79]). http://archiv.ub.uni-heidelberg.de/volltextserver/17209/1/Dissertation_Stumbrys_2014.pdf
3. Gollwitzer PM, Sheeran P 2006. Implementation intentions and goal achievement: a meta-analysis. *Adv Exp Soc Psychol* 38:69–119. https://doi.org/10.1016/S0065-2601(06)38002-1 (abstract)
4. Leong RLF, Cheng GH-L, Chee MWL, Lo JC 2019. The effects of sleep on prospective memory: a systematic review and meta-analysis. *Sleep Med Rev* 47:18–27. https://doi.org/10.1016/j.smrv.2019.05.006 (abstract)
5. Fechner J et al. 2024. The influence of intentions on dream content. *Sleep Adv* 5:zpae088. https://doi.org/10.1093/sleepadvances/zpae088
6. Schredl M, Erlacher D 2008. Relation between waking sport activities, reading, and dream content. *J Psychol* 142:267–276. https://doi.org/10.3200/JRLP.142.3.267-276 (abstract)
7. Schredl M 2024. *Imagination, Cognition and Personality*. https://doi.org/10.1177/02762366241254818 (abstract)
8. Fogli A, Aiello LM, Quercia D 2020. Our dreams, our selves: automatic analysis of dream reports. *R Soc Open Sci* 7:192080. https://pmc.ncbi.nlm.nih.gov/articles/PMC7481704/
9. Bertolini L, Elce V, Michalak A, Widhoelzl H-S, Bernardi G, Weeds J 2024. Automatic annotation of dream report's emotional content with large language models. CLPsych 2024 (ACL). https://aclanthology.org/2024.clpsych-1.7.pdf
10. Elce V, Handjaras G, Bernardi G 2021. The language of dreams: application of linguistics-based approaches for the automated analysis of dream experiences. *Clocks & Sleep* 3:495–514. https://pmc.ncbi.nlm.nih.gov/articles/PMC8482230/
11. Koenecke A et al. 2024. Careless Whisper: speech-to-text hallucination harms. ACM FAccT 2024. https://doi.org/10.1145/3630106.3658996 (read via https://arxiv.org/html/2402.08021v2)
12. Barański M et al. 2025. Investigation of Whisper ASR hallucinations induced by non-speech audio. ICASSP 2025. https://doi.org/10.1109/ICASSP49660.2025.10890105 (abstract)
13. Radford A et al. 2023. Robust speech recognition via large-scale weak supervision. ICML, PMLR 202:28492–28518. https://proceedings.mlr.press/v202/radford23a.html
14. Bain M, Huh J, Han T, Zisserman A 2023. WhisperX: time-accurate speech transcription of long-form audio. Interspeech 2023:4489–4493. https://www.isca-archive.org/interspeech_2023/bain23_interspeech.html
15. Jeffries N et al. 2024. Moonshine: speech recognition for live transcription and voice commands. arXiv 2410.15608, **preprint**. https://arxiv.org/html/2410.15608v2
16. Xu H et al. 2023. Efficient sequence transduction by jointly predicting tokens and durations. ICML, PMLR 202:38462–38484. https://proceedings.mlr.press/v202/xu23g.html (method only)
17. Huckvale K, Torous J, Larsen ME 2019. Assessment of the data sharing and privacy practices of smartphone apps for depression and smoking cessation. *JAMA Netw Open* 2:e192542. https://doi.org/10.1001/jamanetworkopen.2019.2542 (abstract)
18. Hoefer MJD, Schumacher BE, Voida S 2022. Personal dream informatics. CHI '22. https://doi.org/10.1145/3491102.3517669 (abstract)
19. Mallett R et al. 2022. The relationship between dreams and subsequent morning mood using self-reports and text analysis. *Affective Science* 3:400–405. https://doi.org/10.1007/s42761-021-00080-8 (abstract)
20. Lam TH et al. 2015. Hypnotherapy for insomnia: a systematic review and meta-analysis of randomized controlled trials. *Complement Ther Med* 23(5):719–732. https://doi.org/10.1016/j.ctim.2015.07.011
21. Harvey AG, Payne S 2002. The management of unwanted pre-sleep thoughts in insomnia: distraction with imagery versus general distraction. *Behav Res Ther* 40:267–277. https://doi.org/10.1016/s0005-7967(01)00012-2 (abstract)

**Engineering sources**
- E1. sherpa-onnx v1.13.7: `sherpa-onnx/kotlin-api/{OfflineRecognizer,OfflineStream,Vad,FeatureConfig}.kt`; `csrc/{offline-stream,offline-whisper-model-config,voice-activity-detector,silero-vad-model}.cc`. https://github.com/k2-fsa/sherpa-onnx/tree/v1.13.7
- E2. https://k2-fsa.github.io/sherpa/onnx/vad/silero-vad.html and https://k2-fsa.github.io/sherpa/onnx/vad/index.html (official VAD + ASR Android example: `android/SherpaOnnxVadAsr`)
- E3. https://github.com/k2-fsa/sherpa-onnx/releases/tag/asr-models (sizes and SHA-256 digests from the release listing; the Parakeet CTC, Moonshine tiny and Silero files re-checked by download)
- E4. https://k2-fsa.github.io/sherpa/onnx/pretrained_models/offline-ctc/nemo/english.html
- E5. https://huggingface.co/nvidia/parakeet-tdt_ctc-110m
- E6. https://github.com/moonshine-ai/moonshine and https://k2-fsa.github.io/sherpa/onnx/moonshine/models-v2.html
- E7. https://github.com/snakers4/silero-vad/blob/master/LICENSE
- E8. https://github.com/openai/whisper and https://k2-fsa.github.io/sherpa/onnx/pretrained_models/whisper/tiny.en.html
- E9. https://k2-fsa.github.io/sherpa/onnx/pretrained_models/offline-transducer/nemo-transducer-models.html
- E10. https://developer.android.com/media/platform/supported-formats; Android 16 CDD https://source.android.com/docs/compatibility/16/android-16-cdd
- E11. https://developer.android.com/reference/android/media/MediaCodec
- E12. https://developer.android.com/reference/java/util/zip/package-summary
- E13. https://developer.android.com/reference/android/speech/RecognizerIntent (`EXTRA_AUDIO_SOURCE`) and https://developer.android.com/reference/android/speech/SpeechRecognizer (on-device mode)
- E14. https://developer.android.com/guide/topics/data/autobackup
- E15. https://developer.android.com/training/wearables/data/overview (Data Layer transport and Google-server relay)
