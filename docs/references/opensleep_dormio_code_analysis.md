# openSleep + dormio: code analysis for OpenTDI

Analysis date: 2026-10-27. Analyst: research agent for OpenTDI.

**Scope.** These are code-level facts from the two MIT Dormio code bases, so OpenTDI can port them, in particular as the sensor-free `TIMED_CUES` mode (owner requirement 15). Requirement 10 asks to mark every CLAUDE.md §4 default as sourced or guessed; this document does that for the code side. The paper and the thesis are covered in other `docs/references` files and are **not** quoted here.

**Paths.** All paths are relative to `reference_repos/`, which is read-only. `file:N` means line N and `file:N-M` means a range.

**Labels.** Every claim is tagged:
- **[FACT]**: read directly from the cited file, git metadata or API response.
- **[INFERENCE]**: my own reasoning from those facts.

Where a source could not be checked, the text says so.

| Repo | Remote | Commit analysed | Last commit date |
|---|---|---|---|
| `openSleep` | https://github.com/tomasero/openSleep | `efad39fd55ddbab9a8b915755279fbdd8c2b4324` ("clean up") | 2022-03-21 |
| `dormio` | https://github.com/christinatchen/dormio | `7c00cc39be113d90216a6d2ebab21adf1066cc91` ("Update style.css") | 2023-07-05 |

- [FACT] Both URLs resolve. The GitHub API (`api.github.com/repos/<owner>/<repo>`, queried 2026-10-27) returned `full_name` `tomasero/openSleep` (description "platform for sleep hacking and research", created 2017-11-26) and `christinatchen/dormio` (created 2019-06-28). The local `master` HEADs match `git ls-remote origin`.
- [FACT] On openSleep, the `pushed_at` of 2023-03-01 comes from dependabot branches only, not from `master`.

---

## 0. Key findings (read this first)

1. **The two web apps are near-identical.** [FACT] `dormio/` and `openSleep/serverless/` share byte-identical `hr.js`, `report.js`, `multithread.js`, `script_timer.js`, `script_wearable_bc.js`, `index.html`, `timer.html`, `wearablebc.html`, `audio.txt` and all 24 audio files (diffed with `--strip-trailing-cr`; audio compared by md5). Only `js/script_wearable.js`, `wearable.html` and `css/style.css` differ (section 2).
2. **Timer mode (`script_timer.js`) is the protocol to port as `TIMED_CUES`.**
   - [FACT] Defaults: 3 loops; first cue after a random whole number of minutes in [10, 15]; then 3 min "hypna latency"; then the wake prompt and a 60 s report; 7 min "time between sleep" between loops (`openSleep/serverless/js/script_timer.js:12-19, 213-235`).
   - [FACT] The "think of" cue plays at session start, again when hypna latency starts, and again after each report (`script_timer.js:196, 304, 366`).
   - [FACT] The session ends with three gong plays and a zip download (`script_timer.js:376-380, 429-436`).
   - [INFERENCE] A default session lasts T + 26 min, where T is the first-sleep delay: 36 to 41 min in total (section 3.2).
3. **The web "wearable" detector is a single-sample OR of three absolute deltas.**
   - [FACT] Once a second, it takes the mean of the last 100 raw samples (EDA, flex) and the last 10 BPM values (HR) and compares each with the calibration mean. If `|ΔEDA| ≥ 4` **or** `|Δflex| ≥ 5` **or** `|ΔHR| ≥ 6 BPM` (UI defaults), sleep onset is declared at once (`openSleep/serverless/js/script_wearable.js:690-731`, defaults `:221-230`).
   - [FACT] There is no standard deviation, no percentage, no debounce or hold time, no direction (it uses `Math.abs`) and no RMSSD.
4. **Biosignal detection only gates the first awakening.**
   - [FACT] In both web wearable modes (wearable and bc) and in the guided iOS flow, awakenings 2..N are purely timer-driven: `endWakeup → duringSleep → (timeBetween) → … → startWakeup` (`script_wearable.js:908-938, 826-847`; `FlowViewController.swift:479`, where detection requires `numOnsets == 0`).
   - [FACT] Only the iOS "experimental" `ViewController` lets the HR/EDA/flex deltas re-trigger later onsets (`ViewController.swift:512-534`, which has no `numOnsets` check there).
5. **openSleep `script_wearable.js` waits twice the hypna latency after detection.**
   - [FACT] `endDetectSleepOnset` plays the prompt and waits `hypnaLatency`, then calls `runHypnaLatency`, which plays the prompt **again** and waits `hypnaLatency` **again** before the wake-up. That makes detection-to-wake 6 min at the default of 3 (`script_wearable.js:781-792, 833-847`).
   - [FACT] This was introduced by dormio commit `15d22a6` (2020-05-19, "fix hypnalatency in wearable"). The `_bc` variant still waits a single `hypnaLatency` (`script_wearable_bc.js:791-805`).
   - [INFERENCE] The doubling is probably unintended.
6. **"bc" is never expanded in the code.**
   - [FACT] The home page button reads "bc tmr study" (`openSleep/serverless/index.html:58`) and the page title is "dormio | wearable | bc study" (`wearablebc.html:16`).
   - [FACT] The variant plays experimenter-chosen cue **words** (`audio/<word>.m4a`, chosen from `audio.txt`) after each "think of" prompt, with a different word list for each wake-up (`script_wearable_bc.js:921-947, 1429-1507`).
   - [INFERENCE] "tmr" most likely means targeted memory reactivation. What "bc" stands for is unknown.
7. **No fades, ramps or volume control anywhere.** [FACT] A search for `volume|fade|ramp|gain` over the web JS and the Swift sources finds only `player?.volume = 0` on the iOS background *video* (`FlowViewController.swift:203`). Every cue plays through `new Audio(url).play()` or `AVAudioPlayer.play()` at default volume.
8. **The iOS app is the only code with silence-detected dream recording.** [FACT] It records for at least 30 s and at most 120 s, and stops after 8 s below `min(-35 dB, ambient) + 5 dB` (`Recordings.swift:38-50, 415-440`). The web apps record for a fixed time (`recording-time`, 30 s in wearable mode or 60 s in timer mode) counted from the start of the wake prompt (`script_timer.js:344-346`).
9. **Licensing.** [FACT] Neither repo has a LICENSE file, and the GitHub API reports `license: null` for both. The Swift files carry "Copyright © 2017/2018/2019 Tomas Vega. All rights reserved." (for example `Dormio.swift:6`). See section 7.
10. **Sample rates.**
    - [FACT] All host code assumes **10 Hz** (`script_wearable.js:695` "last 100 entries ~approx 10 seconds"; `HeartQueue.swift:13` `frequency = 10`; `features.py:95` `/ 10.`).
    - [FACT] The current nRF52 firmware sends a 12-byte packet and then waits `delay(50)` (`embedded/sleepduino_nrf52/sleepduino_nrf52.ino:43-46`).
    - [INFERENCE] That gives up to about 20 Hz, so on that hardware every "10 s" window in the web code is really about 5 s, and the BPM scaling (`600/len`) is off by roughly ×2. Not verified on hardware.

---

## 1. Inventory and provenance

| Area | Files (status) | Notes |
|---|---|---|
| Web app, "serverless" (current) | `openSleep/serverless/{index,timer,wearable,wearablebc}.html`, `js/script_timer.js`, `js/script_wearable.js`, `js/script_wearable_bc.js`, `js/hr.js`, `js/report.js`, `js/multithread.js`, `audio.txt`, `audio/*.m4a`, `audio/gong.wav` | [FACT] jQuery and Web Bluetooth (Chrome only, `script_wearable.js:17-24`), plus WebAudioRecorder (MP3), JSZip and FileSaver. |
| Web app (dormio copy) | `dormio/` (same layout) | [FACT] Christina Chen is the author (`timer.html:8` `<meta name="author" content="Christina Chen">`). Commits run 2020-02-11 to 2023-07-05 (50 commits). |
| `dormio/lawinsider/` | 4 folders `batch1..4`: 2802 files (1401 `.txt` + 1401 `.metadata.json`), contract texts such as "ZipRealty Inc Manage.txt" | [FACT] Unrelated to Dormio. It was added in dormio commit `0542c57` ("play words", 2020-02-11). openSleep deleted its copy in `7c139bf` ("deleted lawinsider", 2022-03-21, 3442 files changed). Ignored here. |
| nRF52 firmware (current, "Dormio V3") | `openSleep/embedded/sleepduino_nrf52/sleepduino_nrf52.ino` | [FACT] Prints "sleepDuino v3.1" (`:19`). Added by commit `f5d07a4` (2022-02-02, "embedded sw for Dormio V3"). |
| RFduino firmware (old) | `openSleep/deprecated/sleepduino/sleepduino.ino` | [FACT] Uses `RFduinoBLE`. |
| iOS app (deprecated) | `openSleep/deprecated/mobile_app/openSleep/*.swift` | [FACT] Swift files created 2017-12 to 2019-01 (file headers). The app calls itself "DreamCatcher" (`ViewController.swift:766`). |
| Prediction server (deprecated) | `openSleep/deprecated/prediction_server/{app.py,features.py,classifiers.py,config.py}` | [FACT] A Flask service that wraps pyod `HBOS` (`app.py:19, 105`). |
| Node web app (deprecated) | `openSleep/deprecated/web_app/{app.js,assets/js/script.js,rfduino.js}` | [FACT] Uses noble and socket.io. It only plots and logs data; there is **no** detection logic (`script.js:1-209`). |
| `openSleep/serverless/js/script_wearable.js.zip` | Contains one `script_wearable.js` (32821 bytes, zip timestamp 2021-09-04) | [FACT] An intermediate version: it already uses the nRF52 name filter but differs from both current files. Not analysed further. |

---

## 2. `diff -u dormio/js/X openSleep/serverless/js/X`

[FACT] These files are identical: `hr.js`, `report.js`, `multithread.js`, `script_timer.js` and `script_wearable_bc.js`, plus `index.html`, `timer.html`, `wearablebc.html`, `audio.txt`, `css/style_timer.css`, `css/style_wearable.css` and the 24 audio files (md5-identical). The only differences are:

| File | dormio | openSleep/serverless | Meaning |
|---|---|---|---|
| `js/script_wearable.js`, BLE filter | `"services": [0x2220]` (`dormio/js/script_wearable.js:32`) | `"name": "sleepDuino"` plus `optionalServices` `0x2902, 0x2901, 6e400001-…, 6e400002-…, 6e400003-…` (`openSleep/serverless/js/script_wearable.js:31-40`) | [FACT] dormio targets the old RFduino board; openSleep targets the nRF52 "sleepDuino" board. |
| `js/script_wearable.js`, GATT | `getPrimaryService(0x2220)`, `getCharacteristic(0x2221)` (`dormio:67, 71`) | `getPrimaryService("6e400001-b5a3-f393-e0a9-e50e24dcca9e")`, `getCharacteristic("6e400003-…")` (`openSleep:78, 82`) | [FACT] Same reason. [INFERENCE] `6e4000xx` are the Nordic UART Service UUIDs (`BLEUart` in the firmware). This is external knowledge, not checked against Nordic's documentation here. |
| `js/script_wearable.js`, UX | Opens the form 10000 ms after connecting (`dormio:47-48`); shows an instruction `alert` 4000 ms after page load (`dormio:277-282`) | Both commented out (`openSleep:57-59, 291-296`); adds a "Start Session" button that opens the form (`openSleep:421-423, 511, 947`; `wearable.html:284`) | [FACT] openSleep commits `baaf177` and `3bae4d9` (2022-03-09, Eyal Perry). |
| `js/script_wearable.js`, errors | Fewer `.catch` handlers | Adds `.catch` with messages "Hmm!", "Argh1/2/3!" (`openSleep:47-49, 90-92, 185-187`) | [FACT] Cosmetic. |
| `wearable.html` | No Start Session button | `<button id="start_session">` (`openSleep/serverless/wearable.html:284`) | [FACT] Otherwise whitespace only. |
| `css/style.css` | | Adds `width: 10%` to `#icon` (`openSleep/serverless/css/style.css:168`) | [FACT] Cosmetic. |

- [FACT] The detection logic, defaults, timings and audio sequencing are **identical** between the two `script_wearable.js` files: the diff touches only BLE and UI lines.
- [FACT] `script_wearable_bc.js` still uses the RFduino service `0x2220`/`0x2221` in **both** repos (`script_wearable_bc.js:33, 68, 72`).
- [INFERENCE] So the bc page works only with the old RFduino hardware.

---

## 3. Session state machines and every timer

Labels such as `CALIBRATING` are mine unless the code names them. In the web code, state is implicit: a chain of `setTimeout` callbacks plus `calibrationStatus ∈ {null, "CALIBRATING", "CALIBRATED"}`.

### 3.1 Web: common machinery (all three pages)

**Two recordings are made before the session** [FACT] (`script_timer.js:73-123`):
- The "sleep"/prompt message, saved as `sleep.mp3`. Its tooltip says: *"a message to remind yourself what to dream about as you fall asleep. could be something like, 'remember to think about elephants.'"* (`timer.html:84`).
- The "wakeup" message, saved as `wakeup.mp3`. Its tooltip says: *"a message to wake yourself up and request a dream report. could be something like, 'tell me what you're thinking about.'"* (`timer.html:104`).

**Starting the session** [FACT]:
- Validation requires a dream subject, a numeric value in every `defaults` key, both recordings, and (in wearable mode) a connected device (`script_timer.js:138-173`; `script_wearable.js:430-471`).
- On start, the form fields are disabled and the form closes after 1000 ms (`script_timer.js:180-189`).

**Logging** [FACT]:
- Two text buffers are kept. `fileReadOutput` is human-readable; `fileParseOutput` is pipe-delimited, in the form `subject||||flex,hr,eda|…|EVENT,wakeup|…`.
- In wearable modes, every BLE sample is appended while `recording` is true (`script_wearable.js:124-128`).
- Key presses `a`, `b` and `c` are logged as events (`script_timer.js:451-466`; `script_wearable.js:1118-1149`).

**Ending the session** [FACT]:
- `endSession` zips `<subject>.raw.read.txt`, `<subject>.raw.txt` and `audioRecordings/` (sleep.mp3, wakeup.mp3 and `dream_<n>_<ISO>.mp3`), then downloads the zip with FileSaver (`script_timer.js:386-427`).
- The end-of-session alarm is `audio/gong.wav`, replayed until it has played 3 times through its `ended` listener (`script_timer.js:429-436`). [FACT] `gong.wav` is mono, 11111 Hz, 4.009 s (read from the WAV header with Python's `wave`), so 3 plays take about 12 s.

**Dream recording** [FACT] (`script_timer.js:479-547`):
- `startRecording()` uses `getUserMedia({audio:true})` → `WebAudioRecorder`, encoding mp3, `timeLimit: 480` s, `mp3.bitRate: 160`, `encodeAfterRecord: true`.
- The dream recording starts on the wake message's `onended` event (`script_timer.js:335-337`) and is stopped by the `endWakeup` timer.
- There is **no silence detection**.

### 3.2 Web TIMER mode (`timer.html` + `js/script_timer.js`): sensor-free protocol

**States and transitions** [FACT]:

```
FORM ──start_button──► INCUBATE_0  (playPrompt; show "go to sleep... dormio will wake you soon")
   t+60 s: show countdown "time until sleep" (display only)                 script_timer.js:238-245
INCUBATE_0 ──after timeUntilSleep (random int min∈[min,max] ×60 s)──► HYPNA  script_timer.js:213-221, 247-249
HYPNA (playPrompt; countdown "hypna latency") ──after hypnaLatency──► WAKE   script_timer.js:296-310
WAKE (wakeups++; playWakeup; record from end of wake msg; countdown "recording time")
   ──after recordingTime (counted from WAKE start)──► END_WAKE              script_timer.js:313-347
END_WAKE: stopRecording; if wakeups < loops: playPrompt → BETWEEN           script_timer.js:350-371
          else: gong ×3; after 4000 ms → END                               script_timer.js:373-381
BETWEEN (countdown "time between sleep") ──after timeBetweenSleep──► HYPNA  script_timer.js:282-294
END: hide UI, write "Session End", zip + download                           script_timer.js:386-427
Manual: "stop session" → END (stop_session, script_timer.js:439-441); the "wakeup" button exists in JS
(:444-446) but is commented out in HTML (timer.html:339).
```

**Defaults and UI values.** Every input is a free-text `<input list=…>` with a `<datalist>`. [FACT] Datalist options are suggestions only; any integer is accepted, and the only check is `isNaN(parseInt(...))` (`script_timer.js:146-159`). Defaults are filled from `defaults` (`script_timer.js:12-19, 67-69`).

| Parameter (UI wording) | Element id | Unit | Default (`script_timer.js`) | UI suggestions (`timer.html`) | Used as |
|---|---|---|---|---|---|
| "you want to catch _ dreams" | `loops` | count | 3 (`:13`) | 1, 2, 3, 4, 5 (`:127-131`) | `loops` (`:235`) |
| "you want to stay in hypnagogia for _ minutes" | `hypna-latency` | min | 3 (`:14`) | 1, 2, 3 (`:144-146`) | ×60 s (`:233`) |
| "it will take _ to _ minutes for you to fall asleep" (min) | `time-until-sleep-min` | min | 10 (`:15`) | 5, 10, 15 (`:159-161`)* | random lower bound (`:213`) |
| (max) | `time-until-sleep-max` | min | 15 (`:16`) | 10, 15, 25 (`:166-168`) | random upper bound (`:214`) |
| "afterwards, it'll take _ minutes to fall asleep again" | `time-between-sleep` | min | 7 (`:17`) | 3, 5, 7 (`:181-183`) | ×60 s (`:229-230`) |
| "you'll record your dream report for _ seconds" | `recording-time` | s | 60 (`:18`) | 30, 60, 90 (`:196-198`) | ms timer (`:234, 344-346`) |
| dream subject | `dream-subject` | text | (required) | | file prefix only; never spoken or synthesised (`:204, 403`) |
| prompt recording / wake recording | | audio | (required) | | `sleep_msg_recording` / `wakeup_msg_recording` |

\* [FACT] The min input uses `list="time-until-sleep-min-list"` but its datalist has `id="time-until-sleep-list-min"` (`timer.html:157-158`). [INFERENCE] So those suggestions never appear.

**Time until first sleep** [FACT]: `getRandomInt(min, max)` returns `Math.floor(Math.random()*(max-min+1))+min`, an integer number of minutes with both ends inclusive (`script_timer.js:680-684`). The default draw is one of {10, 11, 12, 13, 14, 15} min.

**Cue order** [FACT]:
1. Prompt at start (`:196`).
2. Prompt at each HYPNA start (`:304`).
3. Wake message at WAKE (`:332`).
4. Prompt at END_WAKE when loops remain (`:366`).
5. Gong after the last loop (`:376`).

So each inter-wake cycle has **two** "think of" plays: one right after the report, and one 7 min later at the start of hypna latency.

**Minute-by-minute timeline of one default session**, with T = 12 min drawn (T can be 10 to 15). [FACT] for each step; the clock times are [INFERENCE] arithmetic from the defaults.

| Clock (mm:ss) | Event | Source |
|---|---|---|
| 00:00 | Start pressed → **prompt #1** ("think of"); "go to sleep..." screen; session log starts | `script_timer.js:186-210` |
| 00:01 | Form slides away | `:189` |
| 01:00 | Countdown appears, label "time until sleep", starting at 12:00 (display lags the real timer by 60 s) | `:238-245` |
| 12:00 | **Prompt #2**; countdown "hypna latency" 3:00 | `:247-249, 296-310` |
| 15:00 | **Wake #1**: wake message plays; dream recording starts when it ends; 60 s timer counted from 15:00 | `:313-347` |
| 16:00 | Recording stops; **prompt #3** (re-incubation); "time between sleep" 7:00 | `:350-371, 282-294` |
| 23:00 | **Prompt #4**; hypna latency 3:00 | `:296-310` |
| 26:00 | **Wake #2** | |
| 27:00 | Recording stops; **prompt #5**; 7:00 | |
| 34:00 | **Prompt #6**; 3:00 | |
| 37:00 | **Wake #3** | |
| 38:00 | Recording stops; loops done → gong ×3 (about 12 s) | `:373-377` |
| 38:04 | `endSession()` → zip download (the gong is still playing) | `:378-380, 386-427` |

- [INFERENCE] Total time = T + loops×(hypna + rec) + (loops−1)×between = T + 3×(3 + 1) + 2×7 = **T + 26 min**, so 36 to 41 min.
- [INFERENCE] Number of prompts = 2×loops = 6; number of wake-ups = loops = 3.

**Timer-mode quirks to fix rather than copy**:
- [FACT] `duringSleep` and `runHypnaLatency` store their timers in *local* `var nextWakeupTimer` variables (`:291, 306`), and `endSession` never calls `clearTimeout` (`:386-427`). [INFERENCE] So "stop session" does not cancel future cues: prompts and wake-ups keep playing after Stop.
- [FACT] The recording timer starts at WAKE, but the recording only starts when the wake message ends (`:335-346`). [INFERENCE] The real report window is `recordingTime − duration(wake message)`. If the wake message is longer than `recordingTime`, `stopRecording` runs first, and the recording that starts afterwards is never stopped.
- [FACT] `initTimer` starts a new self-rescheduling countdown on every call and never cancels the previous one (`:566-650`). [INFERENCE] Several countdowns fight over the display; the display is cosmetic only.
- [FACT] The `gongs` counter is never reset in timer mode (`:429`). The wearable mode does reset it (`script_wearable.js:931`).

### 3.3 Web WEARABLE mode (`wearable.html` + `js/script_wearable.js`)

**States** [FACT]:

```
IDLE ──connect (Web Bluetooth)──► CONNECTED ──Start Session→form→start_button──► CALIBRATING   :425-515
CALIBRATING (calibration-time min; means recomputed every 1 s)                               :522-601
   (no prompt at session start)
   ──timer or "Calibrating..." button──► CALIBRATED: playPrompt                               :604-638
CALIBRATED ──if time-until-sleep min/max both numeric: wait min×60 s──► DETECTING            :640-658
           ──else immediately──► DETECTING
DETECTING (every 1000 ms): if |Δ| ≥ δ on ANY channel → ONSET                                  :690-731
           else if window set and elapsed ≥ (max−min)×60 s → ONSET (forced)                  :755-766
ONSET: playPrompt; after hypnaLatency → HYPNA                                                 :781-792
HYPNA: playPrompt; after hypnaLatency → WAKE                                                  :833-847
WAKE: wakeups++; wake msg; record after it ends; after recording-time → END_WAKE              :851-905
END_WAKE: stopRecording; if wakeups<loops: playPrompt → BETWEEN; else gong, 4000 ms → END     :908-938
BETWEEN: after time-between-sleep → HYPNA   (no biosignal detection after the first onset)    :826-831
END: zip/download; re-enable form; clear calibrate/countdown/nextWakeup timers                :942-997
Manual: "Calibrated" button → restart calibration (startCalibrating) ; "stop session" → END   :409-415, 517-519
```

**Defaults and UI values** (`script_wearable.js:221-230`; `wearable.html`):

| Parameter | id | Unit | Default | UI suggestions | Note |
|---|---|---|---|---|---|
| loops | `loops` | count | 3 | 1-5 (`wearable.html:122-128`) | |
| hypna latency | `hypna-latency` | min | 3 | 1, 2, 3 (`:140-144`) | [FACT] applied twice after detection (`:781-792, 833-847`) |
| time until sleep, min / max | `time-until-sleep-min/max` | min | **empty** (not in `defaults`) | 5, 10, 15 / 10, 15, 25 (`:155-166`) | [FACT] Tooltip: "this step is optional, and wards against too-early and too-late sleep detection" (`:170`). When either field is empty, `NaN` → detection starts right after calibration (`:643-646`). |
| time between sleep | `time-between-sleep` | min | 7 | 3, 5, 7 (`:178-182`) | label: "after your first wakeup, it'll take..." (`:176`) |
| calibration time | `calibration-time` | min | 3 | 3, 5, 7 (`:193-197`) | |
| recording time | `recording-time` | s | **30** | 30, 60, 90 (`:208-212`) | [FACT] The default is 30 here versus 60 in timer mode. |
| δEDA | `delta-eda` | raw ADC counts | 4 | free text (`:271-274`) | |
| δflex | `delta-flex` | raw ADC counts | 5 | free text (`:266-269`) | |
| δHR | `delta-hr` | BPM | 6 | free text (`:261-264`) | |

**Window semantics** [FACT] (`:640-658, 744-777`): with min and max set, detection begins `min` minutes after calibration ends and runs for `(max−min)` minutes. If no threshold is crossed in that time, onset is forced ("window elapsed", `:763-766`). [INFERENCE] In wall-clock terms the window is [calEnd + min, calEnd + max], so it acts as a timer fallback.

**Default timeline** [INFERENCE from the facts above]. Let D be the first threshold crossing, at or after 3:00:
- 0:00 calibration (no audio)
- 3:00 prompt, detection on
- D prompt
- D+3:00 prompt
- D+6:00 wake #1
- D+6:30 prompt
- D+13:30 prompt
- D+16:30 wake #2
- D+17:00 prompt
- D+24:00 prompt
- D+27:00 wake #3
- D+27:30 gong, end about 4 s later

The total is D + 27.5 min.

### 3.4 Web WEARABLE_BC variant (`wearablebc.html` + `js/script_wearable_bc.js`): "bc tmr study"

What "bc" is: see finding 6. Differences from `script_wearable.js`, all [FACT]:

| Aspect | `script_wearable_bc.js` | Line |
|---|---|---|
| BLE | RFduino `0x2220`/`0x2221` (old board) | `:33, 68, 72` |
| Form | "subject name" (`#subject-name`) instead of dream subject | `wearablebc.html:70-71`; js `:450` |
| Prompt tooltip | "a message to tell the subject to go to sleep. could be something like, 'remember to think about the following words.'" | `wearablebc.html:80` |
| Wake-up count | "you want to perform _ wakeups": suggestions 3, 4, 5; "The maximum number of wakeups allowed is 10" | `wearablebc.html:119-129` |
| Word lists | "confirm wakeups" shows N word pickers (`sellect` widget), one per wake-up (`#wakeup-1..10`), whose options come from `audio.txt` split on `;` | js `:410-428, 968-996, 1000-1140`; `wearablebc.html:137-184` |
| Cue words | After **every** prompt ends, `playSelected(currentSelected)` plays `audio/<word>.m4a` one after another | js `:936-942, 1429-1451` |
| Word list per phase | List 1 at session start (`:519-520`). At each `startWakeup(n)`, `changeCurrentSelected(n)` switches to list n+1, so prompts after wake-up n play list n+1 | `:833, 1453-1507` |
| Detection to wake | **1 × hypnaLatency** (prompt + words, then wake) | `:791-805` |
| Between loops | prompt+words at END_WAKE; prompt+words again after `time-between-sleep`; wake at `time-between + hypna-latency` | `:902-919` |
| Parameters | Read from the form at use time instead of cached at start | `:795, 865, 883, 904, 911` |
| Defaults | Same `defaults` object as wearable (loops 3, hypna 3, between 7, calibration 3, recording 30, δ 4/5/6) | `:228-236` |
| Detection | Identical code (100-sample / 10-BPM means, `Math.abs`, OR) | `:700-789` |

[INFERENCE] bc default timeline after detection D:
- D prompt+words
- D+3:00 wake #1
- D+3:30 prompt+words
- D+10:30 prompt+words
- D+13:30 wake #2
- D+14:00 prompt+words
- D+21:00 prompt+words
- D+24:00 wake #3
- D+24:30 gong

Bugs [FACT]:
- `playSelected` loops while `i < list.length + 1`, so it tries `audio/undefined.m4a` after the last word (`:1439`).
- `wakeupSelect1.getSelected()` is called at start (`:519`). [INFERENCE] It throws if "confirm wakeups" was not pressed.
- `$("#bluetooth_help")` (`:22`) has no matching element in `wearablebc.html`.

### 3.5 iOS app (deprecated): `FlowViewController` (guided) and `ViewController` (experimental "DreamCatcher")

**Stored defaults**, written once in `AppDelegate.swift:19-64` [FACT]:
- calibrationTime 300 s
- promptTime 15 s
- numOnsets 3
- waitForOnsetTime 240 s
- deltaHBOSS 7
- deltaFlex 50
- deltaHR 15
- deltaEDA 10
- minTimeToFirstOnset 300 s
- falsePosFlexOpen 750
- falsePosFlexClosed 430
- minRecordingTime 30 s
- maxRecordingTime 120 s
- maxTimeToFirstOnset 600 s
- phoneUUIDPrefix "subject"

The experimental view edits these values (`ViewController.swift:788-877`). [FACT] It forces `maxTimeToFirstOnset > calibrationTime` (`:867-873`).

**Guided flow screens** (storyboard ids `step1..7`, `stepName`, `timerStep`, `thinkOfTable`) [FACT]: connect → name → "What would you like to dream about?" → record WAKEUP message → record one or more SLEEP ("Remember To Think Of") messages → "How deep would you like to dream?" → "How many dreams would you like to catch?" → Dream screen.

The depth choice and the onset count [FACT]:

| Control | Options | Default | Source |
|---|---|---|---|
| Dream depth | Light / Medium / Deep | Light (`selectedSegmentIndex="0"`) | `Main.storyboard:1238` |
| Delay for each depth | 30 / 60 / 90 s | | `Flow.swift:39-48` (`promptTimeDelay()`) |
| Onsets to catch | 1-5 | 3 (index 2) | `Main.storyboard:1292` |

A "Timers" entry instead leads to `timerStep`, "Time Until Sleep (Seconds)" (`FlowViewController.swift:230-236, 284-292`; `Main.storyboard:928`).

**FlowViewController, biosignal path**: `dreamPressed` (`FlowViewController.swift:375-435`) [FACT]:

```
IDLE ──Dream──► CALIBRATING: calibrateSilenceThreshold (10 s ambient), GET /init, calibrateStart
  t=30 s: play "think of" recording #numOnsets (round-robin)                         :398-400
  t=calibrationTime (300 s): RUNNING; calibrateEnd (means); GET /train; startTime=now;
       detectSleep every 3 s                                                         :402-410
       maxTimeToFirstOnsetTimer fires at calibrationTime + maxTimeToFirstOnset − 30
       (= 870 s with defaults) → sleepDetected(TIMER)                                :412-416
detectSleep: only if numOnsets==0 AND (now−startTime) > minTimeToFirstOnset (300 s), inside
  the /predict success callback; ONE detector chosen by dreamDetectorControl:
  0 HBOSS score ≥ deltaHBOSS | 1 |lastHR−meanHR| ≥ deltaHR | 2 |lastEDA−meanEDA| ≥ deltaEDA |
  3 |lastFlex−meanFlex| ≥ deltaFlex                                                 :473-499
sleepDetected(trigger): start falsePositiveTimer (0.5 s poll of FlexAnalyzer) [biosignal only];
  after promptTimeDelay (30/60/90 s): play wake prompt (mode 1); calibrateStart();
  on playback end: record dream with silence detection → on silence: numOnsets++,
  POST /reportTrigger legitimate=true; if numOnsets < target → transitionOnsetToSleep
  else alarm after waitTimeForAlarm (10 s)                                           :501-588
falsePositive (double fist squeeze): stop + delete recording, report legitimate=false,
  transitionOnsetToSleep                                                             :529-547
transitionOnsetToSleep: maxWaitOnsetTimer = max(waitForOnsetTime, 45) s → sleepDetected(TIMER);
  after 30 s: play next "think of", calibrateEnd, unpause detection                  :594-611
wakeupAlarm: Alarm.mp3 looping; "Continue (+1 onset(s))" or "OK" → reset           :616-635
```

- [FACT] The detection window for the first onset runs from 600 s (300 + 300) to 870 s after Dream is pressed. Onsets 2..N are TIMER-only, because detection requires `numOnsets == 0` (`:479`).
- [FACT] The baseline is re-collected from the moment the wake prompt plays until 30 s after the report ends (`:585, 608`). [INFERENCE] So later baselines are taken while the user is awake and talking. In this controller they are unused, because detectors only run for onset 1.
- [FACT] The detector icons and the code disagree. The segments are mind2, heart, fist, skin (`Main.storyboard:1435-1438`), but code index 2 is EDA and index 3 is flex (`FlowViewController.swift:488-494`). The same storyboard also uses `skin.png` and `fist.png` as standalone channel icons (`Main.storyboard:1370, 1384`). [INFERENCE] So the fist icon selects EDA and the skin icon selects flex: a UI bug.
- [FACT] Every detector, including HR/EDA/flex, runs inside the `/predict` HTTP success callback (`:476-498`). [INFERENCE] With no server reachable, the guided flow can only trigger on TIMER.

**FlowViewController, timer path** [FACT]:
- Dream plays "think of" #0 immediately, then waits `timeUntilSleep` seconds (user-entered) before `sleepDetected(TIMER)` (`:421-430`).
- An "I'm not asleep" button appears and adds 60 s (`timerFalsePositiveAdditionalTime = 60.0`, `:57, 338-351`).
- `timeUntilSleep` can be measured with **phone-drop calibration**: the user holds the phone, falls asleep, the phone drops, and the elapsed seconds are recorded (`DropDetector.swift`; `FlowViewController.swift:315-373`).
- The FlowManager default `timeUntilSleep = 600` s (`Flow.swift:17`). [INFERENCE] It is effectively unused, because the Continue button stays disabled until the field is filled (`:467-472`).

**ViewController (experimental)**: `startButtonPressed` (`ViewController.swift:268-335`) [FACT]:
- The "think of" message plays at 30 s into calibration (`:301-303`).
- Calibration ends at `calibrationTimeText`, then detectSleep runs every 3 s (`:305-318`).
- `maxTimeToFirstOnsetTimer` is scheduled from the 30 s callback, so it fires at `maxTimeToFirstOnset` after start (600 s) (`:325-329`).
- Detection is allowed once `now − startTime > minTimeToFirstOnset`, where `startTime` is the end of calibration (`:309, 498`).
- [INFERENCE] With the stored defaults (300 s + 300 s = 600 s, versus a max of 600 s), the detection window collapses to about 0 s. The first onset is effectively timer-triggered at 10 min unless the defaults are changed.
- detectSleep combines inputs [FACT]:
  - HBOS score ≥ deltaHBOSS triggers for the **first onset only** (`:503`).
  - **OR** across `|ΔHR| ≥ 15`, `|ΔEDA| ≥ 10` and `|Δflex| ≥ 50`, for **every** onset (`:512-534`).
- After a trigger, the wake prompt plays after `promptTime` (15 s) (`:589-595`).
- The report is recorded with silence detection; then `transitionOnsetToSleep` replays "think of" **immediately** and arms a TIMER for `waitForOnsetTime` (240 s) (`:633-649`).
- The alarm then offers "Continue (+3 onset(s))" (`:675-678`).
- An optional "speaking detection" switch records spontaneous speech between onsets, and defers a trigger while the user is speaking (`:320-322, 538-549, 651-657`).
- A simulation switch replays `simulatedData.csv` at 0.1 s per row (`:277-280, 480-493`). [FACT] That file has 1427 rows × 3 columns. At index 845 the code prints "Sending SLEEP data!" (`:490-492`). My summary of the file: flex mean goes from 232.4 to 311.2, EDA mean from 41.4 to 28.0, and raw PPG spans about 1-957 throughout.

---

## 4. Audio sequencing (all modes)

| Event | Web timer | Web wearable | Web bc | iOS Flow (biosignal) | iOS Flow (timer) | iOS experimental |
|---|---|---|---|---|---|---|
| Session start | "think of" prompt (`script_timer.js:196`) | none (calibrating) | none | none, then "think of" #0 at +30 s (`FlowViewController.swift:398-400`) | "think of" #0 immediately (`:423`) | "think of" at +30 s (`ViewController.swift:301-303`) |
| End of calibration | n/a | prompt (`script_wearable.js:638`) | prompt + words list 1 (`_bc.js:648`, 936-942) | silent | n/a | silent |
| Onset detected | n/a (timer) | prompt (`:783`) | prompt + words (`:793`) | silent; wait 30/60/90 s | silent; "not asleep" button | silent; wait 15 s |
| Start of hypna latency | prompt (`:304`) | prompt again (`:841`) | n/a | n/a | n/a | n/a |
| Wake | wake message, then mic (`:332-337`) | same (`:886-898`) | same (`:847-854`) | wake prompt, mode 1 (`:553-559`) | same | same (`:595-601`) |
| After the report | prompt (`:366`) | prompt (`:925`) | prompt + words (`:887`) | "think of" #n at +30 s (`:602-604`) | same | "think of" #n immediately (`:635`) |
| Between loops | prompt after 7 min (`:296-304`) | prompt after 7 min (`:826-841`) | prompt + words after 7 min (`:907-909`) | none | none | none |
| End | gong ×3, then zip (`:376-380`) | gong ×3, then zip | gong ×3, then zip | Alarm.mp3 looped after 10 s (`Recordings.swift:557-564`; `Flow.swift:18`) | same | same |

**Wording found in the code or UI.** The web apps have no fixed scripts: the user records the cues. These are the hints and labels [FACT]:
- Web prompt hint: "remember to think about elephants." (`timer.html:84`).
- Web wake hint: "tell me what you're thinking about." (`timer.html:104`).
- bc prompt hint: "remember to think about the following words." (`wearablebc.html:80`).
- iOS sleep message label: "You can fall asleep now, Remember to think of..." (`Main.storyboard:1110`), shown with the theme filled in as `"You can fall asleep now,\nRemember to think of " + dreamTitle` (`FlowViewController.swift:218`).
- iOS wake label: "You're falling asleep... Tell me what you're dreaming about" (`Main.storyboard:1023`).
- iOS experimental button: "You're falling asleep...\nTell me what you're thinking" (`ViewController.swift:262`).
- iOS instruction: "Close your hand, and relax for 30 seconds. When your bio-signals stabilize, press Dream" (`Main.storyboard:1350`).

**"Think of" versus prompt recordings** [FACT]:
- Web: exactly one "sleep/prompt" recording and one "wakeup" recording.
- iOS: mode 0 means **several** "Remember To Think Of (n)" recordings, which can be reordered and deleted (`Recordings.swift:24-26, 173-199, 268-289`). They are played round-robin as `urls[numOnset % count]` (`Recordings.swift:542-552`). Mode 1 is the single wake prompt (`Recordings.swift:241-263, 527-537`).

**Volume, fades and ramps** [FACT]: none. No code sets audio volume, gain or envelope (finding 7). iOS uses `AVAudioSessionCategoryPlayAndRecord` with `.defaultToSpeaker` (`Recordings.swift:80`).

**Recording start and stop**:
- Web [FACT]: recording starts on the wake message's `ended` event. It stops at a fixed time, `recording-time` seconds after the wake message *started*. There is no silence detection. The encoder hard limit is 480 s.
- iOS [FACT] (`Recordings.swift:390-444`):
  - The meter is polled every 0.1 s.
  - The threshold is `dbThreshold = min(-35 dB, calibrationSilenceThresh)`. The ambient level is calibrated over 10 s (0.1 s steps) when Dream/Start is pressed (`:478-512`).
  - A poll counts as "silent" when `averagePower < dbThreshold + 5`, and only after `minRecordingTime` (30 s).
  - Recording stops when silence lasts more than 8 s (`silenceTimeThreshold`), or when elapsed time exceeds `maxRecordingTime` (120 s).
  - Format: AAC, 44100 Hz, mono, high quality (`:96-101`).
  - Speaking detection uses `> dbThreshold + 3 dB` for more than 1.0 s cumulative, resets after 5.0 s of silence, and restarts the probe recording every 20 s (`:59-62, 328-360`).

**`audio.txt` and `audio/`** [FACT]:
- `audio.txt` is one line: `age;arm;bag;ball;bank;coach;crown;fish;glove;glue;grass;hair;hand;hat;hip;hook;mouth;nail;nose;pen;rock;ship;tent` (23 words).
- `audio/` holds one `<word>.m4a` for each word plus `gong.wav`.
- ffprobe: every `.m4a` is 48 kHz stereo, 1.22-1.88 s long (`ship` 1.216 s, `hook` 1.877 s).
- They are used **only** by the bc page: `readAudioTxt` fills the word pickers, and `playSelected` plays `audio/<word>.m4a` (`_bc.js:968-996, 1429-1451`).
- [INFERENCE] Each file is a single spoken word, used as a TMR cue. I did not listen to them.
- `gong.wav` is the end-of-session alarm in all web modes.

---

## 5. Biosignal processing and detection

### 5.1 Web HR (`js/hr.js`, `script_wearable.js:100-211`)

- [FACT] The BLE `hr` value is a **raw analog PPG sample** (firmware `analogRead(hr)`), not BPM.
- [FACT] Buffers:
  - `buffer`: the last 600 raw HR samples (`:113-116`).
  - `bigBuffer`: the last 1800 `[flex, hr, eda]` samples (`:119-122`).
  - `bpmBuffer`: the last 180 BPM values (`:207-210`).
- [FACT] `processBPM(buffer, 50)` counts rising edges where `sample[i] − sample[i−1] > 50` and `i − lastBeat > 4` samples, and returns `parseInt(beats × 600 / buffer.length)` (`hr.js:1-15`). It runs in a Web Worker through `Multithread(2)`.
- [FACT] It first runs once 20 s have passed since page load (`bpmInit`), then every 1 s (`:154-166`).
- [INFERENCE] At 10 Hz, 600 samples are 60 s; `>4` samples gives a 0.5 s refractory period, so the maximum is about 120 BPM; the ×600/len factor converts the count to beats per minute.
- [FACT] The display flashes a heart on `hr − oldHr > 50` and `now − lastBeat > 0.4 s` (`:145-152`). This is cosmetic.

### 5.2 Web calibration and detection

- [FACT] `startCalibrating` clears `bigBuffer`, `bpmBuffer` and the means; calibration lasts `calibration-time × 60` s; `updateMeans()` runs every 1 s (`:522-570`).
- [FACT] The means are **arithmetic means only**, rounded: `meanEDA` and `meanFlex` over `bigBuffer` (up to 1800 samples), `meanHR` over `bpmBuffer` (up to 180 values) (`:572-601`). No standard deviation is computed anywhere.
- [INFERENCE] With a 5 or 7 min calibration, only the last 3 min count, because of the 1800/180 caps (at 10 Hz).
- [FACT] `detectSleepOnset` (`:690-779`):

| Step | What it does | Source |
|---|---|---|
| recentMean EDA/flex | Mean of the last 100 `bigBuffer` samples (comment: "~approx 10 seconds") | `:695-704` |
| recentMean HR | Mean of the last 10 `bpmBuffer` values ("~approx 10 seconds") | `:708-715` |
| deltas | `abs(mean − recentMean)` for each channel | `:718-720` |
| trigger | `ΔEDA ≥ δEDA OR Δflex ≥ δflex OR ΔHR ≥ δHR` | `:728` |
| re-check | `setTimeout(detectSleepOnset, 1000)` | `:750-752, 772-774` |
| forced | Elapsed time ≥ (max−min)×60 when the window is set | `:757-766` |

- [FACT] There is no hold or debounce: one 1-s evaluation over a 100-sample (about 10 s) moving average fires immediately.
- [FACT] Thresholds are absolute values in raw units (ADC counts or BPM); there are no percentages.
- [INFERENCE] Because of `abs`, a rise (for example an arousal) triggers "sleep" just as a drop does.
- [FACT] `report.js` is an offline viewer and is **not loaded** by any `serverless/*.html` (only `hr.js` is included: `index.html:30`, `timer.html:33`, `wearable.html:30`, `wearablebc.html:32`). It:
  - re-derives BPM with `bufferLen = 200` and `maxBufferLen = 600`;
  - falls back to a calibration over samples 0-1800 ("add one at 3:00") when none is logged;
  - marks the first sample after calibration where `|x − mean| > δ` on each channel separately;
  - scales the plot by HR×8 and EDA×25 on a 0-1023 axis, with x-ticks of 600 samples per minute.

  (`report.js:47-73, 98-142, 200-244`)

### 5.3 iOS HR, EDA and flex

- [FACT] `HeartQueue(windowTime: 60)` keeps 60 × 10 = 600 samples (`HeartQueue.swift:12-21`; `FlowViewController.swift:77`).
- [FACT] `bpm()` returns 0 when it holds fewer than 100 samples. It finds peaks where `a[i+1] − a[i] > 50`, skipping 4 samples after each, needs at least 2 peaks, and returns `60 / (mean peak interval in samples / frequency)` (`HeartQueue.swift:30-55`).
- [INFERENCE] Each pair of peaks gives an inter-beat interval in samples, so this is an IBI-based BPM. There is no RMSSD or any HRV measure.
- [FACT] Every 30 samples, `sendData` uploads the batch to `/upload` and sets `lastEDA` and `lastFlex` to the means of those 30 samples, and `lastHR = hrQueue.bpm()` (`FlowViewController.swift:678-701`; `ViewController.swift:693-715`).
- [INFERENCE] That is a 3 s window at 10 Hz.
- [FACT] During calibration, every sample appends `(flex, hrQueue.bpm(), eda)` (`:668-670, 703-707`). The means are plain arithmetic means (`:716-723`).
- [INFERENCE] The first 10 s of `bpm()` return 0, which biases `meanHR` low.
- [FACT] Thresholds are absolute: HR 15 BPM, EDA 10 counts, flex 50 counts; HBOS score 7 (`AppDelegate.swift:32-43`).

### 5.4 `DropDetector.swift` (full detail)

- [FACT] It uses the phone's CoreMotion accelerometer at 480 Hz (`accelerometerUpdateInterval = 1/480`, and a polling timer at the same 1/480 s) (`:46-50`).
- [FACT] It computes the magnitude `sqrt(x²+y²+z²)` in g (`:54-57`).
- [FACT] State machine (`:72-100`):

```
HELD     --mag > 5      --> DROPPING  (lastNonZero = now)
DROPPING --mag < 3      --> STOPPED
STOPPED  --mag > 1.1    --> (stay; lastNonZero = now)
STOPPED  --mag <= 1.1 for > 0.3 s since lastNonZero --> DROPPED
DROPPED  --> numDrops++, call dropCB(), state = HELD
```

- [FACT] It is used only for "Phone Drop Calibration" of time-until-sleep in the iOS timer path (`FlowViewController.swift:315-362`).
- [INFERENCE] It detects a held phone falling, an Edison-style sleep-onset marker, not body movement.
- [INFERENCE] The thresholds read as impact (> 5 g), then settling (< 3 g), then lying still near 1 g for at least 0.3 s.

### 5.5 `FlexAnalyzer.swift` (full detail): false-positive gesture, not sleep detection

- [FACT] Code defaults: `openThresh = 650`, `closedThresh = 430`, `CloseTimeThresh = 1.75` s, `maxTimeBetweenFlexes = 5` s (`:20-26`). Lower values mean a closed hand.
- [FACT] `configureFalsePositiveParams` overrides the thresholds from UserDefaults (open 750, closed 430) (`:52-60`; `AppDelegate.swift:47-52`). It is called only from `ViewController.swift:284, 419, 838, 846`.
- [INFERENCE] In the guided flow the code default of 650 applies, unless the experimental view was opened earlier (the class is a singleton).
- [FACT] State machine (`:62-106`):

```
OPEN    --flex <= open   --> CLOSING (t0 = now)
CLOSING --flex >  open   --> OPEN
CLOSING --flex <= closed --> CLOSED
CLOSED  --flex >  closed --> OPENING          (no count)
CLOSED  --(now − t0) <= 1.75 s --> count a flex, state OPENING:
          1st flex: t1 = now
          2nd flex: if now − t1 < 5 s → falsePositive = true, numFlexes = 0
                    else numFlexes = 1, t1 = now
OPENING --flex >= open   --> OPEN
```

- [FACT] The comment "at least one second in closed" (`:25`) contradicts the code, which counts *fast* closures of 1.75 s or less.
- [FACT] It is polled every 0.5 s after a detection (`FlowViewController.swift:72, 529-547`; `ViewController.swift:572-587`). A positive deletes the current dream recording and returns to waiting for sleep.
- [INFERENCE] The protocol is: squeeze your fist twice within 5 s to say "I'm not asleep".

### 5.6 Prediction server (HBOS "HBOSS" score)

- [FACT] `/init` creates the session files. `/upload` appends `flex, ecg(hr), eda` rows. `/train` runs at the end of calibration and needs at least 300 rows (`config.py:2`; `app.py:92-94`). `/predict` is polled every 3 s.
- [FACT] Features use 150-sample windows with a 10-sample step (`config.py:17-19`). They are computed after normalising: flex `(x−256)/320`, ecg `/960`, eda `/64` (`features.py:6-11`).
- [FACT] The six features (`features.py:69-156`):
  - flex mean;
  - flex-diff-clipped (`(mean diff × 5e3)²` when mean diff > 1e-4);
  - EDA mean;
  - HR normalised `(min(120,hr)−40)/60`, from peaks rising more than 0.05 with a 4-sample skip, and RR = Δpeaks/10;
  - RR-diff-clipped (`(mean ΔRR × 10)²` when > 0.02);
  - `log(LF/HF)` from `scipy.signal.periodogram`, with bands 0.04-0.15 and 0.15-0.4.
- [INFERENCE] The periodogram runs on the raw PPG with the default sampling frequency. Assuming the default `fs = 1.0` (scipy API knowledge, not checked here), those bands are cycles per sample (0.4-1.5 Hz and 1.5-4 Hz at 10 Hz). They are pulse-waveform bands, not true HRV LF/HF.
- [FACT] Baseline: feature windows 60-120 of the session (`features.py:166-173`). [INFERENCE] At 10 Hz that is roughly 60-134 s into calibration.
- [FACT] Calibration subtracts the baseline means, except for features 1 and 4. The flex-mean delta is clipped to ≤ 0, and the HR delta becomes `4·min(Δ,0)²` (`features.py:175-185`). [INFERENCE] So the model only reacts to flex and HR **decreases**.
- [FACT] Training: `HBOS(contamination=0.05)` is fit on population awake features (`awake.pkl`) plus the session features; feature 1 is replaced by `|N(0, 0.01)|` noise (`app.py:90-106`).
- [FACT] Prediction: on the last 200 rows, the score is `decision_function − min(train scores)`; `max_sleep` is the maximum over the windows (`app.py:141-171`). The trigger is `max_sleep ≥ deltaHBOSS` (7).
- [FACT] `SimpleClassifier` (Mahalanobis, capped at 50) is defined but its call is commented out (`classifiers.py:4-22`; `app.py:152-167`).
- [FACT] The iOS base URL is the plain-HTTP `http://68.183.114.149:5000/` (`SleepAPI.swift:12`).

---

## 6. Sensor sampling rates and packet formats

| Device | Transport | UUIDs / name | Packet | Rate | Source |
|---|---|---|---|---|---|
| nRF52 "sleepDuino" (V3) | BLE `BLEUart` | Name `sleepDuino`; manufacturer "MIT Media Lab", model "V1.2" | 12 bytes = 3 × `int` `[flex(A4), hr(A5), eda(A6)]`, `memcpy` per value | `delay(50)` then `waitForEvent()` in each loop | `sleepduino_nrf52.ino:12, 30-46, 63-68` |
| nRF52 advertising | | | | `setTxPower(4)`, `Advertising.setInterval(32, 244)`, `setFastTimeout(30)`, `start(0)`; Serial at 115200 | `:18, 61-79` |
| Web client for nRF52 | Web Bluetooth | Service `6e400001-b5a3-f393-e0a9-e50e24dcca9e`, notify characteristic `6e400003-…` | `getUint32(0/4/8, littleEndian=true)` → flex, hr, eda | Every notification | `openSleep/serverless/js/script_wearable.js:78-88, 103-105` |
| RFduino (old) | `RFduinoBLE.send` | Service `2220`, receive `2221`, send `2222`, disconnect `2223` | 12 bytes `[flex(pin2), hrv(pin6), eda(pin4)]` | `delay(1)` ×3 plus `delay(100)` per loop | `deprecated/sleepduino/sleepduino.ino:3-5, 32-61`; `deprecated/web_app/rfduino.js:2-5` |
| iOS client | CoreBluetooth | Name contains "RFduino"; service 2220, read 2221, write 2222 | `getBytes` at 0-4 flex, 4-8 HR, 8-12 EDA (UInt32) | Every notification | `Dormio.swift:29-32, 145-162` |

- [FACT] The ADC range shown by the plots is 0-1023 (`script_wearable.js:1029`; `report.js:99`). [INFERENCE] That implies 10-bit ADC values.
- [FACT] The README hardware list gives HR-SENSOR as Adafruit 1093, FLEX-SENSOR as Adafruit 1070, and OpenBCI EDA electrodes (`openSleep/README.md`, "Components" table). [INFERENCE] Adafruit 1093 is a PPG pulse sensor; the product name was not checked online.
- [INFERENCE] Sample rates:
  - RFduino: about 9.7 Hz (a ~103 ms loop), which matches the 10 Hz assumed by the host code.
  - nRF52: at most about 20 Hz, but the real rate depends on `waitForEvent()` and BLE connection timing, and was not measured.
  - Advertising: the interval units are probably 0.625 ms (Adafruit library knowledge, not checked), giving 20 ms fast and 152.5 ms slow.
- [FACT] No accelerometer exists on any Dormio wearable. The only accelerometer use is the phone in `DropDetector` (480 Hz).

---

## 7. Licenses

| Item | Finding | Type |
|---|---|---|
| `openSleep` repo | No `LICENSE`, `COPYING` or `NOTICE` file anywhere outside vendored folders (`find -iname '*licen*'` found only `deprecated/noble/LICENSE` and `deprecated/data_webapp/src/canvas/license.txt`). The README has no license statement. The GitHub API reports `"license": null` and `/license` returns 404 (2026-10-27). | FACT |
| `dormio` repo | No LICENSE file; GitHub `"license": null`, `/license` 404; no README. The HTML meta author is Christina Chen. | FACT |
| iOS sources | Headers say "Copyright © 2017/2018/2019 Tomas Vega. All rights reserved." (`AppDelegate.swift:6`, `Dormio.swift:6`, `DropDetector.swift:6`, …) | FACT |
| Vendored third-party code | noble: MIT (Sandeep Mistry, `deprecated/noble/LICENSE`). CanvasJS 2.3.1: commercial ("requires purchase of license", `canvas/license.txt`). jQuery 3.4.1 (header points to jquery.org/license). JSZip 3.2.1: "Dual licenced under the MIT license or GPLv3". d3 4.12.0: header "Copyright 2017 Mike Bostock". sellect.js: author header only (Lidia Freitas). WebAudioRecorder, Mp3Lame and OggVorbis: minified with no header. | FACT |
| Implication for OpenTDI | With no license grant, default copyright applies: do not copy code, UI text or the 23 word recordings and `gong.wav` into OpenTDI. Re-implement the behaviour described here in Kotlin and record our own audio. If reuse is wanted, ask the authors for permission. This is not legal advice. | INFERENCE |

---

## 8. Pseudo-code

### 8(a) Detection algorithms

```text
# Web wearable / wearable_bc (identical)   script_wearable.js:100-211, 522-779
on BLE notify (≈10 Hz assumed): flex, hr_raw, eda = u32le[0], u32le[4], u32le[8]
  raw_hr_buf.push(hr_raw) (cap 600); big_buf.push([flex,hr_raw,eda]) (cap 1800)
  every 1 s (first after 20 s): bpm = count_rises(raw_hr_buf, step>50, gap>4) * 600/len(raw_hr_buf)
                                bpm_buf.push(bpm) (cap 180)
calibrate(T_cal = calibration-time min):
  clear big_buf, bpm_buf; after T_cal: μEDA, μflex = mean(big_buf cols); μHR = mean(bpm_buf)
detect (every 1 s):
  eda10  = mean(last 100 eda);  flex10 = mean(last 100 flex);  hr10 = mean(last 10 bpm)
  if |μEDA−eda10| ≥ δEDA(4) or |μflex−flex10| ≥ δflex(5) or |μHR−hr10| ≥ δHR(6 BPM): ONSET
  elif window_set and t_since_detect_start ≥ (max−min)·60: ONSET (forced)

# iOS experimental ViewController   ViewController.swift:495-535
every 30 samples: lastEDA = mean(30 eda); lastFlex = mean(30 flex); lastHR = HeartQueue.bpm()
every 3 s, if t_since_cal_end > minTimeToFirstOnset (300 s):
  async: score = GET /predict .max_sleep;  if onsets==0 and score ≥ 7 → ONSET(HBOSS)
  if |lastHR−μHR| ≥ 15 or |lastEDA−μEDA| ≥ 10 or |lastFlex−μFlex| ≥ 50 → ONSET(first matching)
first onset forced (TIMER) at maxTimeToFirstOnset (600 s) after start

# iOS guided FlowViewController   FlowViewController.swift:473-499
same inputs, ONE user-selected detector, only for onset #1, only inside /predict success

# HeartQueue.bpm()   HeartQueue.swift:30-55
if n < 100: return 0
peaks = indices i where a[i+1]−a[i] > 50, skipping 4 samples after each
return 60 / (mean(diff(peaks)) / 10 Hz)

# Server HBOS   app.py:124-171, features.py
X = last 200 rows → normalize → windows(150, step 10) → 6 features → minus baseline mean
    (flex-mean delta clipped ≤0; HR delta → 4·min(Δ,0)²)
max_sleep = max(HBOS.decision_function(X)) − min(train scores)

# FlexAnalyzer (false positive) and DropDetector: see 5.4 / 5.5 state machines
```

### 8(b) Session loops

```text
# WEB TIMER  (script_timer.js)  -- the TIMED_CUES reference
play(PROMPT)
T = randint_inclusive(min=10, max=15) minutes
wait T
for k in 1..loops(3):
    play(PROMPT); wait hypna(3 min)
    play(WAKE); on WAKE.ended: start_mic()        # timer runs from WAKE start
    wait rec(60 s); stop_mic()
    if k < loops: play(PROMPT); wait between(7 min)
play(GONG)×3; after 4 s: save zip

# WEB WEARABLE  (script_wearable.js)
calibrate(3 min); play(PROMPT)
if min,max set: wait min; detect until ONSET or (max−min) elapsed
else: detect until ONSET
play(PROMPT); wait hypna; play(PROMPT); wait hypna        # double wait (bug?)
for k in 1..loops:
    play(WAKE); record after it ends; wait rec(30 s); stop
    if k < loops: play(PROMPT); wait between(7); play(PROMPT); wait hypna(3)
gong×3; save zip

# WEB WEARABLE_BC  (script_wearable_bc.js)
words = list[1]; calibrate(3 min); play(PROMPT+words)
detect as above → play(PROMPT+words); wait hypna(3)
for k in 1..loops:
    words = list[k+1]; play(WAKE); record; wait rec(30 s); stop
    if k < loops: play(PROMPT+words); wait between(7); play(PROMPT+words); wait hypna(3)
gong×3; save zip

# iOS GUIDED (FlowViewController)
ambient_silence_cal(10 s); baseline 0..300 s; at 30 s play(THINK_OF[0])
first onset: detector in [600 s, 870 s] else TIMER at 870 s     (timer path: TIMER at timeUntilSleep)
loop: wait promptDelay(30/60/90 s); play(WAKE); record until 8 s silence (30..120 s)
      [flex double-squeeze → discard, back to waiting]
      onsets++; if onsets < N(3): after 30 s play(THINK_OF[onsets % n]); TIMER at max(240,45) s
      else after 10 s: ALARM (looping) → Continue(+1) | OK
```

---

## 9. Every number found

Abbreviations:
- Kinds: **CC** = code constant; **UD** = UI default (pre-filled or initial value); **UR** = UI suggestion or range (datalist or segment options; free text is still accepted on the web); **SD** = stored default (iOS UserDefaults, editable in the experimental view).
- Modes: **T** = web timer; **W** = web wearable; **BC** = web bc; **iF** = iOS guided; **iE** = iOS experimental; **S** = server; **FW** = firmware.

All rows are [FACT] at the cited lines.

| Value | Meaning | Kind | Mode | Source |
|---|---|---|---|---|
| 3 | loops (dreams to catch) | UD | T, W, BC | `script_timer.js:13`; `script_wearable.js:222`; `_bc.js:229` |
| 1-5 | loops suggestions | UR | T, W | `timer.html:127-131`; `wearable.html:122-128` |
| 3, 4, 5 / max 10 | bc wake-up suggestions / stated maximum (10 pickers) | UR | BC | `wearablebc.html:122-124, 129`; `_bc.js:413` |
| 3 min | hypna latency | UD | T, W, BC | `script_timer.js:14`; `script_wearable.js:223`; `_bc.js:230` |
| 1, 2, 3 min | hypna latency suggestions | UR | T, W, BC | `timer.html:144-146`; `wearable.html:140-144`; `wearablebc.html:193-195` |
| 10 / 15 min | time until sleep min / max (random integer, inclusive) | UD | T | `script_timer.js:15-16, 680-684` |
| (empty) | time until sleep min / max in wearable modes | UD | W, BC | not in `defaults` (`script_wearable.js:221-230`) |
| 5, 10, 15 / 10, 15, 25 min | time until sleep suggestions min / max | UR | T, W, BC | `timer.html:159-168`; `wearable.html:156-166` |
| 7 min | time between sleep | UD | T, W, BC | `script_timer.js:17`; `script_wearable.js:224` |
| 3, 5, 7 min | time between sleep suggestions | UR | all web | `timer.html:181-183`; `wearable.html:179-181` |
| 60 s | recording time | UD | T | `script_timer.js:18` |
| 30 s | recording time | UD | W, BC | `script_wearable.js:226`; `_bc.js:233` |
| 30, 60, 90 s | recording time suggestions | UR | all web | `timer.html:196-198`; `wearable.html:209-211` |
| 3 min | calibration time | UD | W, BC | `script_wearable.js:225` |
| 3, 5, 7 min | calibration suggestions | UR | W, BC | `wearable.html:194-196` |
| 4 | δEDA (raw counts, absolute) | UD | W, BC | `script_wearable.js:227` |
| 5 | δflex (raw counts, absolute) | UD | W, BC | `script_wearable.js:228` |
| 6 | δHR (BPM, absolute) | UD | W, BC | `script_wearable.js:229` |
| 60 s | delay before the countdown display appears | CC | T | `script_timer.js:238-245` |
| 1000 ms | form opens after load / closes after start | CC | T, W | `script_timer.js:64, 189`; `script_wearable.js:486` |
| 500 / 300 ms (T); 500 / 500 ms (W) | form open/close animation delays | CC | T, W | `script_timer.js:659-677`; `script_wearable.js:1244-1262` |
| 10000 ms | auto-open form after connect | CC | BC, dormio-W | `_bc.js:48-49`; `dormio/js/script_wearable.js:47-48` |
| 4000 ms | instruction alert after page load | CC | BC, dormio-W | `_bc.js:287-292`; `dormio/js/script_wearable.js:277-282` |
| 4000 ms | end of session after the gong starts | CC | all web | `script_timer.js:378-380`; `script_wearable.js:934-936` |
| 3 | gong plays | CC | all web | `script_timer.js:433` |
| 4.009 s | gong.wav duration (mono, 11111 Hz) | measured | all web | `audio/gong.wav` header |
| 1.22-1.88 s | cue-word m4a durations (48 kHz, stereo) | measured | BC | ffprobe on `audio/*.m4a` |
| 23 | cue words in `audio.txt` | CC | BC | `audio.txt` |
| 480 s | WebAudioRecorder `timeLimit` | CC | all web | `script_timer.js:501` |
| 160 kbps / 0.5 | MP3 bit rate / Ogg quality | CC | all web | `script_timer.js:504-508` |
| 2 | Multithread worker threads | CC | all web | `script_timer.js:21`; `script_wearable.js:232` |
| 50 | PPG rising-edge threshold (raw counts) | CC | W, BC, report | `script_wearable.js:256`; `report.js:2` |
| > 4 samples | refractory period between beats | CC | W, BC | `hr.js:9` |
| 600 | raw HR buffer length; BPM scale numerator | CC | W, BC | `script_wearable.js:114`; `hr.js:14` |
| 20 s / 1 s | first BPM calculation / update period | CC | W, BC | `script_wearable.js:156, 162` |
| 0.4 s | beat-flash refractory (display) | CC | W, BC | `script_wearable.js:145` |
| 1800 | bigBuffer samples (calibration EDA/flex) | CC | W, BC | `script_wearable.js:120` |
| 180 | bpmBuffer values (calibration HR) | CC | W, BC | `script_wearable.js:208` |
| 1 s | calibration mean refresh | CC | W, BC | `script_wearable.js:559-569` |
| 100 samples | recent EDA/flex mean window | CC | W, BC | `script_wearable.js:696-704` |
| 10 values | recent HR (BPM) mean window | CC | W, BC | `script_wearable.js:709-715` |
| 1000 ms | detection re-check period | CC | W, BC | `script_wearable.js:750-752, 772-774` |
| 1000 pts / 0-1023 / 20 ms / 6650 ms | live plot length / y range / tick / marker slide | CC | W, BC | `script_wearable.js:1011, 1029, 262, 866` |
| 200 / 600 / 1800 / ×8 / ×25 | report BPM buffer / max / fallback calibration samples / HR and EDA plot scale | CC | report | `report.js:47-48, 59-62, 111, 115` |
| 300 s | calibrationTime | SD | iE, iF | `AppDelegate.swift:21` |
| 15 s | promptTime (detection → wake prompt) | SD | iE | `AppDelegate.swift:24`; `ViewController.swift:589` |
| 30 / 60 / 90 s | promptTimeDelay for Light / Medium / Deep | CC | iF | `Flow.swift:39-48` |
| Light | default depth | UD | iF | `Main.storyboard:1238` |
| 3 | numOnsets | SD / UD | iE / iF | `AppDelegate.swift:27`; `Flow.swift:16`; `Main.storyboard:1292` |
| 1-5 | onsets segments | UR | iF | `Main.storyboard:1299-1303` |
| 240 s | waitForOnsetTime (TIMER for the next onset) | SD | iE, iF | `AppDelegate.swift:30` |
| 45 s | floor on waitForOnsetTime | CC | iF | `FlowViewController.swift:596` |
| 30 s | delay to the first "think of"; delay to "think of" after a report | CC | iE, iF | `ViewController.swift:301`; `FlowViewController.swift:398, 602` |
| 300 s | minTimeToFirstOnset (after calibration end) | SD | iE, iF | `AppDelegate.swift:45` |
| 600 s | maxTimeToFirstOnset | SD | iE, iF | `AppDelegate.swift:60` |
| 7 | deltaHBOSS | SD | iE, iF | `AppDelegate.swift:33` |
| 15 BPM / 10 / 50 | deltaHR / deltaEDA / deltaFlex | SD | iE, iF | `AppDelegate.swift:36-42` |
| 3 s | detectSleep poll | CC | iE, iF | `ViewController.swift:318`; `FlowViewController.swift:410` |
| 30 samples | upload batch and lastX window | CC | iE, iF | `ViewController.swift:698` |
| 60 s × 10 Hz | HeartQueue window × frequency (code default 15 s) | CC | iE, iF | `HeartQueue.swift:12-13`; `FlowViewController.swift:77` |
| 100 / 50 / 4 / 2 | HeartQueue minimum samples / peak threshold / skip / minimum peaks | CC | iE, iF | `HeartQueue.swift:31, 36, 41, 46` |
| 0.5 s | false-positive poll | CC | iE, iF | `ViewController.swift:89` |
| 650 / 430 | FlexAnalyzer open / closed thresholds (code) | CC | iF | `FlexAnalyzer.swift:20-21` |
| 750 / 430 | falsePosFlexOpen / Closed | SD | iE | `AppDelegate.swift:48, 51` |
| 1.75 s / 5 s | flex closure time / maximum time between two flexes | CC | iE, iF | `FlexAnalyzer.swift:25-26` |
| 60 s | timer "not asleep" extension | CC | iF | `FlowViewController.swift:57` |
| 600 s | FlowManager timeUntilSleep (overwritten by user entry) | CC | iF timer | `Flow.swift:17` |
| 10 s | wait before the alarm after the last onset | CC | iE, iF | `ViewController.swift:127`; `Flow.swift:18` |
| +3 / +1 | onsets added by "Continue" | CC | iE / iF | `ViewController.swift:675-678`; `FlowViewController.swift:620-622` |
| 30 s / 120 s | min / max recording time | SD + CC | iE, iF | `AppDelegate.swift:54, 57`; `Recordings.swift:44-45` |
| 0.1 s / −35 dB / +5 dB / 8 s | silence poll / base threshold / margin / silence to stop | CC | iE, iF | `Recordings.swift:38-41, 426, 434` |
| 10 s / 0.1 s | ambient silence calibration length / step | CC | iE, iF | `Recordings.swift:49-50` |
| +3 dB / 1.0 s / 5.0 s / 20 s / 0.1 s | speaking detection margin / speech time / reset / restart / poll | CC | iE | `Recordings.swift:61-62, 344-353` |
| 44100 Hz / 1 ch / AAC | iOS recording format | CC | iE, iF | `Recordings.swift:96-101` |
| 480 Hz / 5 g / 3 g / 1.1 g / 0.3 s | DropDetector rate and thresholds | CC | iF timer | `DropDetector.swift:46, 75, 80, 84, 88` |
| 0.1 s / index 845 | simulator period / "SLEEP data" marker | CC | iE | `ViewController.swift:279, 490` |
| 300 / 200 / 150 / 10 | server minimum train rows / prediction rows / window / step | CC | S | `config.py:2-19` |
| 0.05 | HBOS contamination | CC | S | `app.py:105` |
| 256, 320, 960, 64 | normalisation constants | CC | S | `features.py:7-9` |
| 0.05 / 4 / 10 | peak threshold / skip / samples per second | CC | S | `features.py:37-44, 95` |
| 120, 40, 60 | HR normalisation | CC | S | `features.py:100` |
| 5e3, 1e-4 / 10, 0.02 | flex-diff and RR-diff clipping | CC | S | `features.py:79, 109` |
| 0.04-0.15 / 0.15-0.4 | "LF" / "HF" bands (cycles per sample, see 5.6) | CC | S | `features.py:115-116` |
| 60-120 | baseline feature windows | CC | S | `features.py:169-172` |
| 4 | HR-drop weight `4·min(Δ,0)²` | CC | S | `features.py:182` |
| 0.01 / 50 | training noise SD / Mahalanobis cap (unused) | CC | S | `app.py:103`; `classifiers.py:13, 21` |
| 0.3 / 0.3 / 0.4 | featureImportance flex / eda / ecg (declared, unused) | CC | iE | `ViewController.swift:112-114` |
| A4 / A5 / A6, LED 12 | nRF52 flex / hr / eda pins | CC | FW | `sleepduino_nrf52.ino:12-14` |
| 50 ms | nRF52 loop delay | CC | FW | `sleepduino_nrf52.ino:44` |
| 12 bytes (3 × 4) | packet size | CC | FW | `sleepduino_nrf52.ino:30-43`; `sleepduino.ino:56-59` |
| 115200 / 9600 | serial baud nRF52 / RFduino | CC | FW | `sleepduino_nrf52.ino:18`; `sleepduino.ino:12` |
| 5 × (250 + 250 ms) | boot LED blink | CC | FW | `sleepduino_nrf52.ino:21, 49-56` |
| 4 / 32, 244 / 30 | TX power / advertising interval / fast timeout | CC | FW | `sleepduino_nrf52.ino:63, 77-78` |
| pins 2 / 4 / 6; 1 ms ×3; 100 ms | RFduino flex / eda / hrv pins; ADC settle delays; loop delay | CC | FW | `sleepduino.ino:3-5, 35-41, 61` |
| 0x2220 / 0x2221 / 0x2222 / 0x2223 | RFduino service / receive / send / disconnect | CC | BC, iOS, node | `rfduino.js:2-5`; `Dormio.swift:30-32` |

---

## 10. CLAUDE.md §4 defaults versus the code (for owner requirement 10)

"Sourced" here means a code counterpart exists in these two repos. It does **not** check the paper. Every row is [FACT] about what the code contains or lacks; the verdict column is [INFERENCE].

| CLAUDE.md §4 default | Code counterpart | Verdict |
|---|---|---|
| Baseline `CALIBRATING_BASELINE (180s)` | Web calibration-time default 3 min (`script_wearable.js:225`), options 3/5/7. iOS 300 s (`AppDelegate.swift:21`). | **Sourced** from web code. Configurable per requirement 12; iOS used 5 min. |
| Mean and **σ** baseline per channel | Only arithmetic means; no standard deviation anywhere (sections 5.2, 5.3). The server uses HBOS on feature means. | **σ guessed** |
| Discard motion-artifact frames | None | **Guessed** |
| 60 s rolling accelerometer SD stillness gate | No accelerometer on the wearable. The flex sensor is used as a plain delta: any `|Δflex| ≥ 5` alone triggers. | **Guessed** (flex to wrist stillness is our adaptation) |
| Hypnic-jerk bonus (< 0.5 s spike then stillness) | None. DropDetector (phone, 5 g → < 3 g → ≤ 1.1 g for 0.3 s) is the closest analogue but measures a different thing. | **Guessed** |
| HR 15 s moving average | Web: mean of the last 10 one-per-second BPM values (about 10 s). iOS: `bpm()` over 60 s, sampled every 30 samples. | **Guessed** (15 s); the code uses about 10 s |
| HR drop 7 % (4-12 %) | Web: **absolute 6 BPM, either direction**. iOS: 15 BPM. The number 7 appears only as `deltaHBOSS` (an HBOS score, not a percentage). | **Guessed**. No percentage exists in the code. |
| RMSSD +15 % | No RMSSD. The server has mean-ΔRR (clipped) and `log(LF/HF)` features, but no percentage threshold. | **Guessed** |
| EDA 30 s slope ≤ 0; phasic spikes < 1.5σ | EDA is a plain absolute mean delta (4 counts web, 10 iOS) in either direction. No slope and no σ. | **Guessed**; requirement 11 already demotes EDA |
| Gate plus at least 1 signal held 20 s / all 3 held 10 s | Web and iOS: **OR of single channels, no hold**: one evaluation per 1 s (web) or 3 s (iOS) fires. | **Guessed**; requirement 11's AND rule is stricter than MIT's code |
| Countdown after detection 180 s (60-300 s) | Web hypna latency 3 min, options 1/2/3 min; **effectively 6 min** in openSleep and dormio `script_wearable.js` (double wait); 3 min in bc. iOS 15 s (experimental) or 30/60/90 s (guided). | **180 s sourced** from web UI defaults. The 60-300 s range is guessed; web suggestions are 60-180 s. |
| Abort the countdown on movement | Only the iOS flex "double squeeze" false-positive gesture (section 5.5). | **Guessed** |
| Up to N cycles | Loops/onsets default 3 everywhere (web 1-5 suggested; bc up to 10; iOS 1-5). | **Sourced** (default 3) |
| Recording 4 s silence or 45 s max | iOS: 8 s silence, 30 s minimum, 120 s maximum. Web: fixed 30 s (wearable) or 60 s (timer). | **Guessed**; code values differ |
| 2000 ms log fade-in | No fades at all | **Guessed** |
| Cue wording "Remember to think of [THEME]..." | iOS label "You can fall asleep now, Remember to think of …" (`FlowViewController.swift:218`). Web hint "remember to think about elephants". | **Sourced** (iOS) |
| Wake prompt "You're falling asleep... what's going through your mind?" | iOS "You're falling asleep... Tell me what you're dreaming about" (`Main.storyboard:1023`) and "…Tell me what you're thinking" (`ViewController.swift:262`) | **Close match** (wording differs) |
| Re-incubation "…You can fall back asleep now." | Web and iOS replay the *same* "think of" recording after the report; there is no separate re-incubation text. | **Partly guessed** (behaviour sourced, wording not) |
| Detection on every cycle | Only onset #1 uses sensors (web, iOS guided); later cycles are timer-based, with between 7 min + hypna 3 min on the web and 240 s on iOS. | **Divergence**: MIT code is timer-driven after the first wake-up. Decide deliberately. |

---

## 11. Implications for OpenTDI (INFERENCE unless marked)

- **TIMED_CUES (requirement 15).** Port `script_timer.js` faithfully:
  - Configurable values: loops, `firstSleepMinMin`/`firstSleepMaxMin` (random inclusive integer minutes), `hypnaLatencyMin`, `betweenSleepMin`, `reportSec`.
  - Cue order: PROMPT, [T], PROMPT, [hypna], WAKE+record, [rec], PROMPT, [between], … then end alarm.
  - Fix the timer-mode quirks in section 3.2: cancellable timers, a report window counted from the end of the wake prompt, and a single countdown.
- **Detection (requirements 11 and 14).** MIT's code gives no σ, %, hold or confidence values to inherit. The HBOS score is the only continuous "sleepiness" output, and its feature calibration counts only HR and flex **decreases**. That supports requirement 11's direction-aware HR/IBI-drop rule plus a continuous confidence score.
- **Logging and replay (requirement 13).** [FACT] The web apps already log every raw sample plus `EVENT` markers (`calibrate_start`, `calibrate_end` with means, `wakeup`, the a/b/c tags) to text. [FACT] `simulatedData.csv` (1427 × 3 rows at 10 Hz, awake then "sleep" at row 845) is an example of a replay fixture. Do not copy it (license); record our own fixtures.
- **Telemetry (requirement 17).** [FACT] iOS batched 30 samples per upload. That matches "batched messages for telemetry, not per-sample".
- **Licensing (requirement 5 spirit).** Re-implement; do not vendor any file from these repos.

---

## 12. Gaps and items not verified

- What "bc" stands for: not stated anywhere in either repo or the git messages I read. "tmr" as targeted memory reactivation is an inference.
- Real BLE sample rate of the nRF52 firmware (`delay(50)` + `waitForEvent()`): not measured. The 10 Hz assumption in the host code may not match V3 hardware.
- Units and direction of EDA and flex raw counts: hardware-dependent; not documented in the code.
- Nordic UART Service UUID meaning, Bluefruit advertising-interval units, scipy `periodogram` default `fs`, pyod HBOS score semantics and Adafruit product names: stated from library or product knowledge, not checked against their documentation in this session.
- Whether the double hypna-latency wait in `script_wearable.js` was intentional: only the commit message "fix hypnalatency in wearable" (dormio `15d22a6`) is available.
- The paper and the thesis (including the non-contact web TDI protocol and its exact timings) are outside this file's scope and were not consulted here.
- Audio content of the 23 `.m4a` files was not listened to; "spoken single words" is an inference from file names, durations and how the files are used.
- Runtime behaviour of the JS and Swift (race conditions, browser autoplay policies) was not executed; everything above comes from reading the code.
