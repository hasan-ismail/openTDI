# Android audio routing, recording and on-device speech-to-text: reference notes for OpenTDI

Compiled 2026-10-27 for the `:mobile` audio loop (cue playback, keep-alive carrier, dream-report
recording, transcription). The watch speaker and watch mic routes are deferred to Phase 3 (owner
requirement 21), so these notes cover only the phone.

**Labels.** **FACT** means the claim is taken directly from the cited source. **INFERENCE** means it is
reasoning from facts, still to be confirmed on hardware. **GUESS** means a number with no source:
a configurable default only.

**Sources and versions.**
- developer.android.com reference pages were fetched on 2026-10-27. Their footer reads "Last updated
  2026-08-03 UTC", and they already list API 37 constants.
- AOSP sources were fetched from android.googlesource.com:
  - `platform/frameworks/av` at tag `android16-qpr2-release` (commit `22fd1c5d895e45a49df5582a4b345d8e3b75e46b`)
  - `platform/frameworks/base` at `android16-qpr2-release` (commit `45034f0663f960d9ee5fb0a101a4732b71f6e2f4`)
  - `platform/packages/modules/Bluetooth` at `android-16.0.0_r3` (commit `d3a858fb6bb581f21c4834c277ba056edf99b7c0`)
  - `platform/hardware/libhardware` at `android-16.0.0_r3` (commit `cf12e39157746b908b572df89352422474db4196`)
- AOSP line numbers below refer to those revisions. The AOSP `main` branch on googlesource was last
  updated in March 2025, so release tags were used instead.
- Samsung uses its own audio HAL and Bluetooth stack builds. **Every AOSP-HAL claim is the reference
  behaviour, not proof of what One UI does.** The checklist in section 10 covers that gap.

---

## 0. Decisions this research supports (summary)

| # | Decision | Basis |
|---|---|---|
| D1 | All cues and the carrier use `AudioTrack` with `USAGE_MEDIA`. Prompts use `CONTENT_TYPE_SPEECH` and `ENCODING_PCM_FLOAT`. Both tracks use `MODE_STREAM`, the same attributes and the same performance mode. | §2 (FACT: AudioMixer combines tracks; float range). INFERENCE: identical attributes and performance mode keep both tracks on one output thread. |
| D2 | Never use `USAGE_ALARM` for headphone routes. | FACT: an alarm may play "on headphones and speaker" at once (§2.4). |
| D3 | `setPreferredDevice` is a hint. After `play()`, check `getRoutedDevice()`. On disconnect, AOSP falls back to default routing (usually the speaker), so OpenTDI must stop the cues itself. | §2.4, §3.3 (FACT from AOSP) |
| D4 | Only the prompt requests focus, with `AUDIOFOCUS_GAIN_TRANSIENT`. The dream-report recording uses `AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE`. The carrier **never** requests focus. | §3.1 (FACT: a GAIN-focus media player is faded out and "remain[s] muted until the app requests audio focus again") |
| D5 | Detect DND before each session and warn. Total silence mutes media. Priority mode mutes media unless media is allowed. | §3.2 (FACT: AOSP `ZenModeHelper`) |
| D6 | Hard volume cap: a per-track gain ceiling, pre-normalised cue files, and no call that raises `STREAM_MUSIC`. Check the stream index before each prompt. | §3.4 |
| D7 | Keep-alive carrier: AOSP Android standby depends on whether tracks are active, not on sample values. The non-zero level is a hedge against standby **inside the headset**. Level is configurable from −90 to −50 dBFS (default −75, **GUESS**), and the manual test in §4.6 is required. | §4 |
| D8 | Record with `AudioRecord` (`VOICE_RECOGNITION`, or `UNPROCESSED` when supported), 16 kHz mono PCM16, `setPreferredDevice(TYPE_BUILTIN_MIC)`. Never call `setMode`, `setCommunicationDevice` or `startBluetoothSco`. | §5 |
| D9 | Transcribe afterwards from the saved PCM: `createOnDeviceSpeechRecognizer`, `EXTRA_AUDIO_SOURCE` (pipe), `EXTRA_SEGMENTED_SESSION`, and a `checkRecognitionSupport` gate. Never fall back to `createSpeechRecognizer`. | §6 |
| D10 | TTS cues go through `synthesizeToFile`. Read the real format from `onBeginSynthesis` or the header; never hard-code a sample rate. | §7 |
| D11 | The silence detector uses an adaptive noise floor plus a margin and a hangover, not a fixed dBFS threshold. The 4 s silence and 45 s maximum are **GUESSES**. Dormio itself used a fixed 60 s (timer page) or 30 s (wearable page) window. | §8 |

---

## 1. Output and input device discovery

### 1.1 `AudioDeviceInfo.TYPE_*` constants (FACT, developer.android.com `android/media/AudioDeviceInfo`)

| Constant | Value | Added in API | Doc text (short) |
|---|---|---|---|
| `TYPE_BUILTIN_SPEAKER` | 2 (0x2) | 23 | "the speaker system (i.e. a mono speaker or stereo speakers) built in a device" |
| `TYPE_WIRED_HEADSET` | 3 (0x3) | 23 | "headset, which is the combination of a headphones and microphone" |
| `TYPE_WIRED_HEADPHONES` | 4 (0x4) | 23 | "a pair of wired headphones" |
| `TYPE_BLUETOOTH_SCO` | 7 (0x7) | 23 | "Bluetooth device typically used for telephony" |
| `TYPE_BLUETOOTH_A2DP` | 8 (0x8) | 23 | "Bluetooth device supporting the A2DP profile" |
| `TYPE_USB_DEVICE` | 11 (0xb) | 23 | "a USB audio device" |
| `TYPE_BUILTIN_MIC` | 15 (0xf) | 23 | "the microphone(s) built in a device" |
| `TYPE_USB_HEADSET` | 22 (0x16) | 26 | "a USB audio headset" |
| `TYPE_BLE_HEADSET` | 26 (0x1a) | 31 | "Bluetooth Low Energy (BLE) audio headset or headphones" |
| `TYPE_BLE_SPEAKER` | 27 (0x1b) | 31 | "Bluetooth Low Energy (BLE) audio speaker" |
| `TYPE_BLE_BROADCAST` | 30 (0x1e) | 33 | "Bluetooth Low Energy (BLE) broadcast group" |
| `TYPE_HEARING_AID` (related) | 23 (0x17) | 28 | "a Hearing Aid" |
| `TYPE_BUILTIN_SPEAKER_SAFE` (related) | — | 30 | speaker "specifically tuned for outputting sounds like notifications and alarms" |

The same page also lists API 37 additions (`TYPE_BLE_CENTRAL`, `TYPE_BLE_HEARING_AID`). OpenTDI does not
need them.

**Route mapping (INFERENCE):**

| `AudioRouteMode` | Device types to accept |
|---|---|
| `BLUETOOTH_SLEEP_AUDIO` | `TYPE_BLUETOOTH_A2DP`, `TYPE_BLE_HEADSET`, `TYPE_BLE_SPEAKER` |
| `USB_WIRED_DAC` (Phase 3) | `TYPE_USB_HEADSET`, `TYPE_USB_DEVICE`, `TYPE_WIRED_HEADPHONES`, `TYPE_WIRED_HEADSET` |
| `NIGHTSTAND_SPEAKER` | `TYPE_BUILTIN_SPEAKER` |

Two types must never be picked:
- **`TYPE_BLUETOOTH_SCO`**: it is the telephony (HFP) path that CLAUDE.md §5.3 forbids.
- **`TYPE_BLE_BROADCAST`**: it is an Auracast group, not a personal sink.

### 1.2 `AudioManager.getDevices(int flags)` (FACT, API 23)

- It "Returns an array of AudioDeviceInfo objects corresponding to the audio devices currently connected
  to the system". Flags are `GET_DEVICES_INPUTS` and/or `GET_DEVICES_OUTPUTS`. The result may be
  zero-length.
- INFERENCE: a Bluetooth headset that supports both profiles usually appears twice, as A2DP and as SCO.
  Filter by type and match the pair by `getAddress()` when needed. Check this on the test earbuds.

### 1.3 `AudioDeviceCallback` (FACT, API 23)

- `AudioManager.registerAudioDeviceCallback(callback, handler)`: if `handler` is null, the callback runs
  on the main Looper.
- `onAudioDevicesAdded(AudioDeviceInfo[])`: "one or more audio devices have been connected".
- `onAudioDevicesRemoved(AudioDeviceInfo[])`: "one or more audio devices have been disconnected".
- INFERENCE: register it inside the session foreground service with a handler on the audio thread.
  The fallback logic is in §3.3.

---

## 2. Playback with `AudioTrack`

### 2.1 Builder and attributes (FACT)

- `AudioTrack.Builder` (API 23) provides `setAudioAttributes`, `setAudioFormat`, `setTransferMode`,
  `setBufferSizeInBytes` and `setPerformanceMode` (API 26). `build()` throws
  `UnsupportedOperationException` "if the parameters … were incompatible, or if they are not supported
  by the device, or if the device was not available".
- `AudioAttributes.USAGE_MEDIA` = 1 (API 21): "music, or movie soundtracks".
  `CONTENT_TYPE_SPEECH` = 1 (API 21). `USAGE_ALARM` = 4.
- `setPerformanceMode` "is an advisory request … the framework is free to ignore". About
  `PERFORMANCE_MODE_POWER_SAVING`: "this lower power path typically will have deeper internal buffers
  … with a tradeoff of higher latency".

### 2.2 `ENCODING_PCM_FLOAT` (FACT, `AudioFormat`)

- Added in API 21, constant value 4.
- "The nominal range of ENCODING_PCM_FLOAT audio data is [-1.0, 1.0] … Values outside of the nominal range
  are clamped before sending to the endpoint device." NaN handling is undefined, and subnormals "may be
  treated as zero".
- `write(float[], int, int, int writeMode)` (API 21): "sample values +3 dB beyond the nominal range are
  permitted. However such values may eventually be limited or clipped".
- `ENCODING_PCM_16BIT`: "Guaranteed to be supported by devices", range [-32768, 32767].

### 2.3 `MODE_STREAM` and `MODE_STATIC` (FACT, `AudioTrack` class overview)

- **Streaming mode:** "the application writes a continuous stream of data to the AudioTrack". It is meant
  for audio that is too big for memory or is "received or generated while previously queued audio is
  playing".
- **Static mode:** "should be chosen when dealing with short sounds that fit in memory and that need to
  be played with the smallest latency possible". The buffer size "is the maximum size of the sound that
  can be played from it".
- Constants: `MODE_STATIC` = 0, `MODE_STREAM` = 1. `WRITE_BLOCKING` = 0, `WRITE_NON_BLOCKING` = 1.
- `stop()` in `MODE_STREAM`: "audio will stop playing after the last buffer that was written has been
  played. For an immediate stop, use pause(), followed by flush()".
- **Recommendation (INFERENCE):** use `MODE_STREAM` for both tracks, so there is one code path. Latency
  does not matter for a cue that fades in over 2 s. The carrier is generated in a loop, so it must stream.
  Use `pause()` then `flush()` for the emergency stop in §3.3.

### 2.4 `setPreferredDevice`: a hint, not a guarantee

- **FACT (API docs):** `AudioTrack.setPreferredDevice(AudioDeviceInfo)` (API 23) "Specifies an audio device
  … to route the output". It returns false only "if the specified AudioDeviceInfo is non-null and does not
  correspond to a valid audio output device". Passing null restores default routing.
- **FACT (API docs):** `getRoutedDevice()` (API 23): "The query is only valid if the AudioTrack is currently
  playing". It adds: "Audio may play on multiple devices simultaneously (e.g. an alarm playing on
  headphones and speaker on a phone)". `getRoutedDevices()` was added in API 36.
  `addOnRoutingChangedListener` was added in API 24.
- **FACT (AOSP):**
  - `Engine.cpp:824-825` (audio policy engine): "Honor explicit routing requests only if all active
    clients have a preferred route in which case the last active client route is used".
  - `AudioPolicyManager.cpp:7986-7987`: "a specific app can not force routing for other apps by setting
    a preferred device".
- **FACT (AOSP, disconnect):** `AudioPolicyManager.cpp:1318-1322` looks up the requested device with
  `mAvailableOutputDevices.getDeviceFromId(requestedPortId)`. A device that has disconnected is therefore
  not found, and `getOutputForAttr` falls back to the engine's default routing (comment at 1341-1342:
  "The primary output is the explicit routing (eg. setPreferredDevice) if specified, otherwise, fallback
  to the dynamic policies…").
- **INFERENCE:** when the earbuds drop mid-nap, a track still playing will most likely move to the built-in
  speaker, which is the startle risk. So:
  1. Call `setPreferredDevice(chosen)` on both tracks before `play()`.
  2. Shortly after `play()`, check that `getRoutedDevice()?.id == chosen.id`. If not, fail the route,
     log it, and do not play the cue.
  3. Keep an `OnRoutingChangedListener` on the prompt track. A change to a device outside the route's
     allowed set triggers the emergency stop in §3.3.

### 2.5 `setVolume` (FACT)

- `setVolume(float gain)` (API 21) sets gain "on all channels of this track. Gain values are clamped to the
  closed interval [0.0, max] where max is the value of getMaxVolume() … The word 'volume' in the API name is
  historical; this is actually a linear gain."
- `getMaxVolume()` "is greater than or equal to 1.0".
- For the 2 s fade-in, `VolumeShaper` (`createVolumeShaper`, API 26) has presets `LINEAR_RAMP`,
  `CUBIC_RAMP`, `SINE_RAMP` and `SCURVE_RAMP`, each "A one second … ramp from silence to full volume".
  `Builder.setCurve` and `setDuration` give custom curves. INFERENCE: use a `VolumeShaper` with a
  2000 ms curve instead of writing a gain ramp by hand.

### 2.6 Two concurrent tracks: carrier and prompt

- **FACT:** AOSP audio terminology (https://source.android.com/docs/core/audio/terminology) defines
  AudioMixer as the "Module in AudioFlinger responsible for combining multiple tracks and applying
  attenuation (volume) and effects".
- **INFERENCE:**
  - Two `AudioTrack`s with the same attributes are mixed by the framework, so no app-side mixing is needed.
  - The carrier peak (about 1.8e-4, see §4.4) costs no measurable headroom.
  - Use identical `AudioAttributes`, the same performance mode (`PERFORMANCE_MODE_NONE`) and the same
    preferred device, so both tracks land on the same output thread. Only then does the carrier keep the
    prompt's output thread out of standby (§4.1).
  - The two tracks may use different sample rates (for example a TTS file at its native rate, carrier at
    48 kHz): AudioFlinger resamples each track.
  - Verify with `adb shell dumpsys media.audio_flinger`: both tracks must be listed under the same output
    thread (§4.6).

---

## 3. Focus, Do Not Disturb, disconnects and volume cap

### 3.1 Audio focus for short prompts (FACT, https://developer.android.com/media/optimize/audio-focus and `AudioManager`)

**Constants:**
- Gain: `AUDIOFOCUS_GAIN_TRANSIENT` = 2, `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK` = 3,
  `AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE` = 4 (API 19).
- Loss: `AUDIOFOCUS_LOSS` = -1, `AUDIOFOCUS_LOSS_TRANSIENT` = -2,
  `AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK` = -3.
- Request result: `AUDIOFOCUS_REQUEST_FAILED` = 0, `AUDIOFOCUS_REQUEST_DELAYED` = 2.

**What each gain means:**
- `GAIN_TRANSIENT`: "temporary gain … anticipated to last a short amount of time".
- `GAIN_TRANSIENT_MAY_DUCK`: "acceptable for other audio applications to keep playing after having lowered
  their output level".
- `GAIN_TRANSIENT_EXCLUSIVE`: "no other applications, or system components, should play anything.
  Examples … are voice memo recording and speech recognition, during which the system shouldn't play any
  notifications".

**Requesting focus:**
- `requestAudioFocus(AudioFocusRequest)` (API 26): "If an app targets Android 15 (API level 35) or higher,
  it cannot request audio focus unless it's the top app or running a foreground service … the method
  returns AUDIOFOCUS_REQUEST_FAILED."
- Owner requirement 16 (the foreground service started while the UI is visible) satisfies this.
- `abandonAudioFocusRequest` must be passed the same `AudioFocusRequest` instance.

**System behaviour on Android 12 and later (guide):**
- **Fade-out:** an app that holds `AUDIOFOCUS_GAIN` with `USAGE_MEDIA` and is "not playing audio with
  content type AudioAttributes.CONTENT_TYPE_SPEECH" is faded out when another app requests `GAIN`.
  "The app's players remain muted until the app requests audio focus again."
- **Automatic ducking:** "is not performed when the user is listening to speech content".
- **Incoming calls:** the system can mute a `USAGE_MEDIA` app that requested focus and is playing.

**Handling loss (guide):**
- On `LOSS_TRANSIENT` or `LOSS_TRANSIENT_CAN_DUCK`: "duck … or pause playing but otherwise maintain the
  same state".
- On `AUDIOFOCUS_LOSS`: "pause playback immediately, as it won't ever receive an AUDIOFOCUS_GAIN callback".
- A failed request (for example during a phone call) means the app "should not proceed with audio
  playback".

**OpenTDI policy (INFERENCE):**
- **Carrier:** never requests focus. Under the fade-out rule above, a carrier holding `GAIN` would be muted
  by any other media app, which defeats the keep-alive.
- **Prompt:** request `AUDIOFOCUS_GAIN_TRANSIENT` right before the fade-in, and abandon it after the cue
  ends.
  - On `REQUEST_FAILED`: skip the cue, log `focus_denied`, and retry on the next detection.
  - On any loss during the cue: stop and flush the prompt (a cut-off cue is better than a cue talking over
    a phone call), and log it.
  - `MAY_DUCK` is acceptable for users who play a white-noise app during the nap. Make it a setting;
    default `GAIN_TRANSIENT`.
- **Recording:** hold `AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE` during `RECORDING_DREAM`. That is the
  documented use case for keeping notifications quiet.

### 3.2 Do Not Disturb and media versus alarm

**AOSP behaviour (FACT, `ZenModeHelper.java:2281-2321`, `applyRestrictions()`):**

```java
final boolean zenPriorityOnly = mZenMode == Global.ZEN_MODE_IMPORTANT_INTERRUPTIONS;
final boolean zenSilence      = mZenMode == Global.ZEN_MODE_NO_INTERRUPTIONS;
final boolean muteAlarms = zenPriorityOnly && !mConsolidatedPolicy.allowAlarms();
final boolean muteMedia  = zenPriorityOnly && !mConsolidatedPolicy.allowMedia();
final boolean muteEverything = zenSilence || (zenPriorityOnly
        && ZenModeConfig.areAllZenBehaviorSoundsMuted(mConsolidatedPolicy));
... case AudioAttributes.SUPPRESSIBLE_MEDIA -> muteMedia || muteEverything;
... case AudioAttributes.SUPPRESSIBLE_ALARM -> muteAlarms || muteEverything;
```

- Muting is applied through app-ops `OP_PLAY_AUDIO` = `MODE_IGNORED` per usage (`ZenModeHelper.java`,
  `applyRestrictions(boolean, IntPredicate, int)`).
- INFERENCE: a muted app gets no error; its track simply plays silence.

**Usage mapping (FACT, `AudioAttributes.java:326-344`):**
- `USAGE_MEDIA`, `USAGE_GAME`, `USAGE_ASSISTANT` and `USAGE_UNKNOWN` map to `SUPPRESSIBLE_MEDIA`.
- `USAGE_ALARM` maps to `SUPPRESSIBLE_ALARM`.
- `USAGE_ASSISTANCE_SONIFICATION` maps to `SUPPRESSIBLE_SYSTEM`.
- `USAGE_ASSISTANCE_ACCESSIBILITY` maps to `SUPPRESSIBLE_NEVER`.

**AOSP defaults (FACT, `ZenModeConfig.java:213-215`):** `DEFAULT_ALLOW_ALARMS = true`,
`DEFAULT_ALLOW_MEDIA = true`, `DEFAULT_ALLOW_SYSTEM = false`.

**Resulting matrix (FACT from the code above):**

| DND state | `USAGE_MEDIA` | `USAGE_ALARM` |
|---|---|---|
| Off | plays | plays |
| Priority only (`INTERRUPTION_FILTER_PRIORITY`), media allowed (AOSP default) | plays | plays if alarms allowed |
| Priority only, media **not** allowed | **muted** | per alarms setting |
| Alarms only (`ZEN_MODE_ALARMS`) | plays (muteMedia requires priority-only) | plays |
| Total silence (`INTERRUPTION_FILTER_NONE`) | **muted** | **muted** |

The API docs agree: `INTERRUPTION_FILTER_NONE` means "all audio streams (except those used for phone
calls) and vibrations are muted". `INTERRUPTION_FILTER_ALARMS` and `INTERRUPTION_FILTER_PRIORITY` say
"Some audio streams are muted".

**Samsung One UI (FACT, https://www.samsung.com/us/support/answer/ANS10002889):** under "Allowed during
Do not disturb", "Alarms and sounds: Select from Alarms, Media sound, Touch sounds, Calendar events, and
Reminders." The **default state of "Media sound" on One UI 8 is not documented** there: verify on the
phone.

**Android 15+ (FACT, `NotificationManager.getNotificationPolicy()`):** "For apps targeting …
VANILLA_ICE_CREAM and above … this method will return the policy associated to their implicit
AutomaticZenRule". To read what is actually in force, use
`getConsolidatedNotificationPolicy()` (API 30, "Returns the currently applied notification policy") and
`getCurrentInterruptionFilter()` (API 23). `PRIORITY_CATEGORY_MEDIA` = 64 and
`PRIORITY_CATEGORY_ALARMS` = 32 (API 28).

**OpenTDI policy (INFERENCE):**
- Stay on `USAGE_MEDIA`. `USAGE_ALARM` can also sound on the speaker while earbuds are connected (§2.4),
  and it moves volume control to the alarm stream.
- At session start and before each cue:
  - If the filter is `NONE`, block the session with "DND Total silence will mute cues".
  - If it is `PRIORITY` and `(consolidatedPolicy.priorityCategories & PRIORITY_CATEGORY_MEDIA) == 0`,
    warn with "Allow Media sound in DND".
  - Log the filter and categories to the session CSV.
- `setStreamVolume` "volume adjustments that would toggle Do Not Disturb are not allowed unless the app
  has been granted Notification Policy Access … Throws SecurityException". This is one more reason never
  to touch stream volume (§3.4).

### 3.3 Bluetooth or USB disconnect mid-session

**FACT:**
- `ACTION_AUDIO_BECOMING_NOISY` (API 3) is "a hint … that audio is about to become 'noisy' … when a wired
  headset is unplugged, or when an A2DP audio sink is disconnected, and the audio system is about to
  automatically switch audio route to the speaker".
- The media guide (https://developer.android.com/media/platform/output) says: "Register the receiver when
  you begin playback, and unregister it when you stop."
- Android 14: for a receiver "only for system broadcasts … it shouldn't specify a flag"
  (https://developer.android.com/about/versions/14/behavior-changes-14).

**INFERENCE:** BECOMING_NOISY is documented only for wired and A2DP cases. Do not rely on it alone for
LE Audio or USB; also use `onAudioDevicesRemoved` (§1.3) and the routing listener (§2.4).

**Emergency stop and fallback (INFERENCE, implements owner requirement 18):**
1. On whichever trigger arrives first (BECOMING_NOISY, `onAudioDevicesRemoved` containing the chosen
   device, or the routed device leaving the allowed set):
   - `prompt.pause(); prompt.flush()`
   - `carrier.pause(); carrier.flush()`
   - abandon focus
   - log `route_lost` with a timestamp and the device type.
2. The fallback is a setting with default `PAUSE_CUES`:
   - Keep sensing and logging, play nothing, and post a silent notification.
   - When a device of the route's allowed types is re-added, re-verify it (§2.4) and resume at the next
     detection, not mid-cue.
   - Optional `SPEAKER_FALLBACK` switches to the `NIGHTSTAND_SPEAKER` cap (§3.4), never the earbud gain.
3. Never let the default routing play a cue: the AOSP fallback goes to default routing (§2.4).

### 3.4 Hard maximum volume cap

**FACT:**
- `getStreamVolume(int)` (API 1) returns the current index.
- `getStreamMaxVolume(int)` (API 1) and `getStreamMinVolume(int)` (API 28) return the index range.
- `getStreamVolumeDb(streamType, index, deviceType)` (API 28) returns "the volume in dB … at the given
  volume index, on the given type of audio output device".
- `isVolumeFixed()` (API 21) means `setStreamVolume` and similar calls "have no effect".

**FACT (AOSP, absolute volume):** `AudioPolicyManager.cpp:8602-8626`
(`adjustDeviceAttenuationForAbsVolume`): for a device with absolute volume whose driving stream is this
volume source, "attenuation is applied by the abs volume controller". The digital gain is then the
curve's maximum (`volumeDbMax`) unless the index is 0. `AudioService.java:16138`
`isAbsoluteVolumeDevice()` covers A2DP absolute-volume devices, LE Audio types and hearing aids.

**INFERENCE:** the level reaching the ear is sample × track gain × (stream attenuation, which
absolute-volume Bluetooth skips) × the headset's own gain.

**Cap design (INFERENCE):**
1. **Pre-normalise** every cue (TTS or recorded) to a fixed peak, for example −3 dBFS, when it is created.
   The loudness of the source file then cannot vary.
2. **Track-gain ceiling:** `prompt.setVolume(min(userGain, capGain[route]))`. `capGain` is set by the
   user once per route in a calibration screen ("this is the loudest a cue will ever be") and stored.
   The app never sets a gain above 1.0 (`getMaxVolume()` may be larger: do not use it).
3. **Never raise system volume:** OpenTDI never calls `setStreamVolume` or `adjustStreamVolume` upward.
4. **Index guard:** before each cue, read `getStreamVolume(STREAM_MUSIC)` and compare it with the index
   saved at calibration. If it has gone up, lower the track gain by
   `getStreamVolumeDb(now) - getStreamVolumeDb(calibrated)` (API 28+). On absolute-volume Bluetooth this
   difference may not reflect the earbud level, so on Bluetooth the safe action is to **skip the cue and
   log `volume_raised`**.
5. **Belt and braces:** One UI has a user-level "Media volume limiter" that "prevents the volume from
   rising above a set level … only applied when you use headphones or Bluetooth speakers" (FACT,
   https://www.samsung.com/us/support/troubleshoot/TSG10004902/). Path: Settings > Sounds and vibration >
   Volume > ⋮ > Media volume limit. Mention it in the setup docs.

---

## 4. Standby pops, clipped first syllables and the keep-alive carrier

### 4.1 What Android does on the phone (FACT, AOSP)

**AudioFlinger standby** (`frameworks/av/services/audioflinger/Threads.cpp`):
- `Threads.cpp:252`: `static const nsecs_t kDefaultStandbyTimeInNsecs = seconds(3);`.
  `Threads.cpp:254-262` lets the system property `ro.audio.flinger_standbytime_ms` override it.
- `Threads.cpp:3706-3712`: "make sure standby delay is not too short when connected to an A2DP sink to
  avoid truncating audio when going to standby". On A2DP the delay is forced to at least 3 s.
- `Threads.cpp:4109-4111`: the playback thread enters standby when
  `(mActiveTracks.empty() && systemTime() > mStandbyTimeNs) || isSuspended()`.
- `dumpsys` prints `Standby: yes/no` (`Threads.cpp:1086`) and `Standby delay ns=` (`Threads.cpp:2457`).

**Bluetooth audio HAL** (`packages/modules/Bluetooth/system/audio_bluetooth_hw/stream_apis.cc`):
- `out_standby()` (lines 257-282) calls `bluetooth_output_->Suspend()` when the stream is `STARTED`.
- The next `out_write()` (lines 505-525) calls `stream->resume()`, which goes to `Start()` (`out_resume`,
  line 626).
- If the resume fails while `DISABLED`, the data is dropped: "drop data for cases of A2dpSuspended=true /
  closing=true" (line 518).
- `device_port_proxy.cc:115`: `kMaxWaitingTimeMs = 4500`. `Start()` (line 460 onward) waits for
  `STARTED`.

**A2DP suspend path:**
- `btif_a2dp_source.cc:373-385`: `SuspendStream()` calls `btif_av_stream_suspend()`.
- `btif_av.cc:2509-2541`: on `BTIF_AV_SUSPEND_STREAM_REQ_EVT` it calls
  `BTA_AvStop(peer_.BtaHandle(), true)`, a stop with suspend=true.

**LE Audio:** `bta/le_audio/client.cc:693-716` starts a suspend timer. When it fires ("No resume request
received. Stop the group"), the group is stopped. `client.cc:7084`:
`kAudioSuspentKeepIsoAliveTimeoutMs = 500`, which the property
`persist.bluetooth.leaudio.audio.suspend.timeoutms` can override (`client.cc:7087-7088`).

**USB audio HAL (AOSP generic):** `hardware/libhardware/modules/usbaudio/audio_hal.c:712`
`out_standby()` calls `stream_standby_l()`, which calls `proxy_close()` (line 573 onward): the ALSA PCM
is closed.

**INFERENCE:**
- On the AOSP path, the chain is: no active track for 3 s or more → AudioFlinger standby → HAL standby →
  A2DP AVDTP suspend, LE Audio ISO teardown after about 0.5 s, or USB PCM close.
- Restart then needs a Bluetooth start round trip, and the sink's amplifier or DAC has to wake. That is the
  mechanism behind the pop or the lost first syllable.
- **A continuously playing `AudioTrack` keeps `mActiveTracks` non-empty, so the phone never enters
  standby.** This is true whether the samples are zero or not.

### 4.2 Do zero-valued samples count as silence?

- **On the phone side (AOSP): no.**
  - The playback standby decision depends on active tracks, not on sample values (`Threads.cpp:4109`).
  - A search of `Threads.cpp` for "silen" found only capture-side silencing (for example lines 8384-8604
    and 9582-9593), nothing on playback. This is a FACT about the code searched, an INFERENCE about the
    behaviour.
  - The A2DP AAC encoder pads underflows with zeros: "Fill the unfilled part of the read buffer with
    silence (0)" (`a2dp_aac_encoder.cc:632`). The stack therefore treats zeros as ordinary payload.
  - Samsung's vendor HAL and Bluetooth build are **not verified**.
- **On the headset side: possibly yes, depending on the device.** Third-party evidence (not Android
  documentation):
  - The NVDA "Bluetooth Audio" add-on README (https://github.com/mltony/nvda-bluetooth-audio, README.md
    lines 3 and 8): "Most bluetooth devices enter standby mode after a few seconds of inactivity … the
    first split second of sound will be lost." In 2025, "instead of playing silence or white noise it
    plays a high frequency (20 KHz) sine wave … it tricks some bluetooth devices to not enter standby mode
    prematurely."
  - https://lee.org/blog/?p=9856: "the Bluetooth connection times-out after about 10 seconds of silence
    … you'll lose the first few seconds or so of audio … play a very low volume sound in the background at
    all times."
- **INFERENCE:** some sinks seem to detect digital silence, or near-silence, inside the earbud and power
  down. The NVDA author's move away from silence and white noise suggests that even low-level noise may
  not always be enough. That is why a **non-zero** carrier is the hedge, and why its level and waveform
  must be testable.

### 4.3 Two reasons a −75 dBFS carrier might still reach the earbud as digital zero (INFERENCE, to test)

1. **Non-absolute-volume routes attenuate digitally.** Wired, USB, the speaker, and Bluetooth without
   AVRCP absolute volume all apply the stream-volume curve in the mixer (§3.4). At a low media index
   (−30 dB, for example), a −75 dBFS carrier becomes about −105 dBFS. That is below one 16-bit LSB
   (−90.3 dBFS), so it rounds to zero if the path is 16-bit. Absolute-volume Bluetooth keeps the digital
   level at its maximum (`AudioPolicyManager.cpp:8621-8626`), so the carrier reaches the codec at its set
   level.
2. **Perceptual codecs may discard it.** SBC and AAC bit allocation can give zero bits to very quiet
   subbands, so the decoded carrier at the earbud may be zero. This is not verified: there is no source.

Consequences: the carrier level must be configurable, and the test in §4.6 must run at the real
nap-time volume index and with the real codec (Developer options shows the active Bluetooth codec).

### 4.4 Level arithmetic (FACT: arithmetic; amplitude A = 10^(dBFS/20), 16-bit full scale = 32768)

| dBFS | float amplitude | 16-bit LSB | 24-bit LSB |
|---|---|---|---|
| −50 | 3.16e-3 | 103.6 | 26 527 |
| −60 | 1.00e-3 | 32.8 | 8 389 |
| −65 | 5.62e-4 | 18.4 | 4 717 |
| −70 | 3.16e-4 | 10.4 | 2 653 |
| **−75** | **1.778e-4** | **5.83** | 1 492 |
| −80 | 1.00e-4 | 3.28 | 839 |
| −85 | 5.62e-5 | 1.84 | 472 |
| −90 | 3.16e-5 | 1.04 | 265 |

- One 16-bit LSB is −90.31 dBFS.
- "−75 dBFS" is ambiguous for noise. In the table it means RMS relative to a full-scale constant
  (A = 1.778e-4 RMS, about 5.8 LSB RMS). Under the AES17 sine-referenced convention (full-scale sine =
  0 dBFS RMS) it is 1.257e-4 RMS, about 4.1 LSB RMS.
- **OpenTDI should define the carrier level as RMS relative to 1.0 float** and log that definition with
  the value.
- At about 6 LSB RMS a 16-bit path quantises the noise coarsely. INFERENCE: generate it in float. Doing so
  avoids app-side quantisation, but the mixer and codec path may still be 16-bit.

### 4.5 Carrier settings (INFERENCE; owner requirement 19)

**Settings:**
- `carrierMode`: `OFF | NOISE | ZERO`. `ZERO` is a control condition for the test only.
- `carrierLevelDbfs`: −90 to −50 in 5 dB steps. Default −75 is a **GUESS**, unvalidated.
- `carrierStartLeadSec`: how long the carrier must have run before the first cue may fire. The proposed
  default of 10 s is a **GUESS**.

**Waveform:** pink-ish noise generated in float (white noise through a one-pole low-pass is enough for a
keep-alive; the exact spectrum does not matter, the level does). A 20 kHz tone is not proposed: perceptual
codecs low-pass high frequencies (INFERENCE), and some people hear it.

**Battery:** NVDA README line 10 warns that a keep-alive "might reduce battery life of your bluetooth
device". Log earbud battery at the start and end of the session if it is readable (Phase 3).

**Off-carrier routes:** `NIGHTSTAND_SPEAKER` uses carrier `OFF` by default. The speaker path has no
Bluetooth link to keep alive. AudioFlinger standby still applies, but the 2 s fade-in hides the restart.
This is INFERENCE: test it too.

### 4.6 Manual test procedure (real earbuds; required before relying on the carrier)

**Record per device:** earbud model and firmware, Bluetooth codec (Developer options), AVRCP absolute
volume on or off, media index and maximum, and the phone build.

1. **Pre-check:** `adb shell getprop ro.audio.flinger_standbytime_ms`. Empty means the AOSP default of 3 s.
2. **Test screen:** play the carrier at level L. After a silence gap G, play a test cue. Use a cue with a
   hard onset, for example the word "Papa" twice, or a 1 kHz burst with a 5 ms ramp, at the calibrated
   cue volume with **no** fade-in. The fade-in would hide clipping.
3. **Grid:**
   - L ∈ {OFF, ZERO, −90, −85, −80, −75, −70, −65, −60}
   - G ∈ {5 s, 30 s, 120 s, 600 s}
   - 5 trials per cell, in random order
   - screen off, phone in the nap position, earbuds worn
4. **During each gap:** run `adb shell dumpsys media.audio_flinger` and record `Standby:` and
   `Standby delay ns=` for the Bluetooth output thread. Both tracks must appear under the same thread.
   Optional: `adb logcat | grep -iE "out_standby|Suspend|resum"` (AOSP log text from `stream_apis.cc`;
   Samsung tags may differ).
5. **Listener scores per trial:**
   - (a) onset pop or click, yes/no
   - (b) first syllable or onset clipped, yes/no
   - (c) carrier audible during the gap in a quiet room, yes/no, scored by 2 listeners
6. **Optional objective check:** hold one earbud against a second phone recording at 48 kHz. Measure the
   delay from the cue trigger log to the acoustic onset, and look for a missing onset.
7. **Pass rule:**
   - The chosen default is the **lowest** L with 0/5 clipped onsets and 0/5 pops at G = 600 s that is
     inaudible to both listeners.
   - If no level passes, the carrier is ineffective for that earbud. Fall back to a longer fade-in
     (for example 3-4 s) and document the earbud as "clips first syllable".
   - If `ZERO` also passes, that earbud does not need a non-zero carrier.
8. **Repeat** at the nap-time media index (low) and at the calibrated maximum. Absolute versus
   non-absolute volume changes the result (§4.3).
9. **Store the results** in the session CSV format (owner requirement 13), so the replay harness can
   carry the per-device default.

---

## 5. Recording the dream report without SCO

### 5.1 Audio sources (FACT, `MediaRecorder.AudioSource` and the Android 16 CDD)

**API values:**
- `MIC` = 1 (API 1): "Microphone audio source".
- `VOICE_RECOGNITION` = 6 (API 7): "tuned for voice recognition".
- `VOICE_COMMUNICATION` = 7 (API 11): "tuned for voice communications such as VoIP … echo cancellation or
  automatic gain control".
- `UNPROCESSED` = 9 (API 24): "tuned for unprocessed (raw) sound if available, behaves like DEFAULT
  otherwise".
- `VOICE_PERFORMANCE` = 10 (API 29).

**Android 16 CDD** (https://source.android.com/docs/compatibility/16/android-16-cdd, "Last updated:
December 2, 2025"):
- **§5.4.1 [C-1-1]:** every opened stream must support "Linear PCM, 16-bit; Sampling rates: 8000, 11025,
  16000, 44100, 48000 Hz; Mono" for DEFAULT, MIC, CAMCORDER, VOICE_RECOGNITION, VOICE_COMMUNICATION,
  UNPROCESSED and VOICE_PERFORMANCE. [C-1-2] adds "MUST capture at above sample rates without
  up-sampling".
- **§5.4.2 `VOICE_RECOGNITION`:**
  - "[C-1-2] MUST, by default, disable any noise reduction"
  - "[C-1-3] MUST, by default, disable any automatic gain control"
  - SHOULD give a 1 kHz tone at 90 dB SPL "RMS 2500 … for 16 bit-samples (or -22.35 db ±3dB Full Scale…)"
  - "[C-1-1] MUST capture … at one of the sampling rates, 44100 and 48000"
- **§5.11 `UNPROCESSED`:**
  - "[C-1-1] MUST report the support through … PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED"
  - "[C-1-5] … 94 dB SPL yields a response with RMS of 520 for 16 bit-samples (or -36 dB Full Scale…)"
  - "[C-1-8] MUST not have any other signal processing (e.g. Automatic Gain Control, High Pass Filter, or
    Echo cancellation)"
- **`AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED`** (API 24) is a key for `getProperty()`,
  value `"android.media.property.SUPPORT_AUDIO_SOURCE_UNPROCESSED"`.

**INFERENCE:**
- The CDD puts no processing or level requirement on `MIC`, so its AGC and noise reduction are
  OEM-tuned and unpredictable.
- **Use `VOICE_RECOGNITION` by default.** It has no AGC or noise reduction, a defined sensitivity, and it
  suits STT.
- Offer `UNPROCESSED` only when `getProperty(...) == "true"`. Its level is about 14 dB lower than
  `VOICE_RECOGNITION`, which matters for §8.
- Record **16 kHz mono PCM16**. It is CDD-guaranteed (down-sampling uses an anti-alias filter, §5.4.1
  [C-1-3]), matches the recognizer's defaults (§6.3), and keeps files small.

### 5.2 Pinning the built-in microphone

**FACT:**
- `AudioRecord.setPreferredDevice(AudioDeviceInfo)` (API 23) "Specifies an audio device … to route the
  input to this AudioRecord". `getRoutedDevice()` "is only valid if the AudioRecord is currently
  recording".
- AOSP default input selection (`Engine.cpp:566` onward, `getDeviceForInputSource`):
  - For `AUDIO_SOURCE_MIC` (lines 614-627): Bluetooth A2DP-in first, then SCO **only if** the
    communication device is SCO, then `getFirstExistingDevice({WIRED_HEADSET, USB_HEADSET, USB_DEVICE,
    BLUETOOTH_BLE, BUILTIN_MIC})`.
  - `VOICE_RECOGNITION` and `UNPROCESSED` (lines 675-689) follow the same order after an SCO check.
  - During a call, most sources, MIC/VOICE_RECOGNITION/UNPROCESSED included, are remapped to
    `VOICE_COMMUNICATION` (lines 579-593).
- `AudioPolicyManager.cpp:8058-8060`: an input's explicit routing is honoured "only if no client using
  default routing is active on this input or if all active clients are from the same app".

**INFERENCE:** a wired or USB headset mic would win over the phone mic by default (the
`USB_WIRED_DAC` route with an in-line mic). So call `setPreferredDevice(builtinMic)` and check
`getRoutedDevice().type == TYPE_BUILTIN_MIC` after `startRecording()`. Log any other value.
`BLUETOOTH_BLE` there is the BLE remote-control mic type, not an LE Audio headset (`AUDIO_DEVICE_IN_BLE_HEADSET`
is chosen only in the `VOICE_COMMUNICATION` branch).

### 5.3 Does recording switch Bluetooth to HFP/SCO?

**FACT:**
- From the engine code above, `MIC`, `VOICE_RECOGNITION` and `UNPROCESSED` select the SCO input only when
  `audio_is_bluetooth_out_sco_device(commDeviceType)`. `commDeviceType` is the preferred device for
  `STRATEGY_PHONE` (`Engine.cpp` `getPreferredDeviceTypeForLegacyStrategy`), set through the
  communication-device APIs.
- `setCommunicationDevice()` (API 31) "Selects the audio device that should be used for communication use
  cases … This API replaces … startBluetoothSco() / stopBluetoothSco() / setSpeakerphoneOn(boolean)".
- `startBluetoothSco()` is deprecated in API 34. Its docs say that with SCO, output must be
  "STREAM_VOICE_CALL … mono … 16kHz or 8kHz" and input "mono … 8kHz".
- `setMode()`: "should only be used by applications that replace the platform-wide management of audio
  settings or the main telephony application".

**INFERENCE (AOSP):** plain `AudioRecord(MIC | VOICE_RECOGNITION | UNPROCESSED)` while A2DP playback
continues does **not** start SCO, as long as OpenTDI never calls `setMode(MODE_IN_COMMUNICATION)`,
`setCommunicationDevice(SCO)` or `startBluetoothSco()`, and no other app holds an SCO communication
device or a call. Guard:
- Before recording, if `getMode() != MODE_NORMAL`, or `getCommunicationDevice()?.type` is
  `TYPE_BLUETOOTH_SCO` or `TYPE_BLE_HEADSET`, skip the recording and log `comm_device_busy`.
- Verify on Samsung with the test earbuds: listen for the HFP switch beep and check that the A2DP codec
  stays the same in Developer options during a recording.

### 5.4 Concurrent capture (FACT, https://developer.android.com/media/platform/sharing-audio-input and CDD §5.4.5)

- From Android 10 a priority scheme applies:
  - "Two ordinary apps can never capture audio at the same time."
  - "If neither app has a UI, the one that started capture the most recently receives audio."
  - A privacy-sensitive source (VOICE_COMMUNICATION, CAMCORDER) wins.
  - The Assistant may share input.
- "Apps … without a foreground service or foreground UI component" receive silence (since Android 9).
- Detection: `AudioRecord.registerAudioRecordingCallback` (API 29). Call it "before the capture is
  started". `AudioRecordingConfiguration.isClientSilenced()` returns "true if the audio returned to the
  client is currently being silenced due to the capture policy".
- `AudioRecord.Builder.setPrivacySensitive(true)` (API 30) means "any concurrent capture is not permitted",
  and "even a privileged Assistant cannot capture concurrently". It is allowed with MIC, VOICE_RECOGNITION
  and UNPROCESSED.
- Microphone foreground services (https://developer.android.com/develop/background-work/services/fgs/service-types):
  "you cannot create a microphone foreground service while your app is in the background". On Android 14+
  you "must call Context.startForegroundService() or Context.bindService() while your app has a visible
  activity". This supports owner requirement 16.

**INFERENCE:** for dream-report privacy, use `setPrivacySensitive(true)`. Register the recording callback
and, if silenced, mark the report `capture_silenced` in the CSV instead of saving an empty file as a
dream.

---

## 6. On-device speech recognition from the recorded file

### 6.1 API surface (FACT, `SpeechRecognizer`, `RecognizerIntent`, `RecognitionListener`, `RecognitionSupport`)

**Class rules:**
- "This class's methods must be invoked only from the main application thread."
- "the caller MUST invoke destroy()".
- "the application must have Manifest.permission.RECORD_AUDIO permission to use this class".
- The overview also says "The implementation of this API is likely to stream audio to remote servers"
  (about the default recognizer).

**On-device recognizer:**
- `createOnDeviceSpeechRecognizer(Context)` (API 31): "This method must be called from the main thread".
  It throws `UnsupportedOperationException` "iff isOnDeviceRecognitionAvailable(Context) is false".
- `isOnDeviceRecognitionAvailable(Context)` (API 31). The AOSP implementation (`SpeechRecognizer.java:311-316`)
  returns true when the OEM resource `config_defaultOnDeviceSpeechRecognitionService` parses as a
  component name. **It does not check that a language model is installed.**

**Support check and model download:**
- `checkRecognitionSupport(Intent, Executor, RecognitionSupportCallback)` (API 33). `RecognitionSupport`
  (API 33) lists:
  - `getInstalledOnDeviceLanguages()`: "ready for use"
  - `getPendingOnDeviceLanguages()`: "scheduled for download"
  - `getSupportedOnDeviceLanguages()`: "need to be downloaded before use"
  - `getOnlineLanguages()`: "available via a remote implementation. SpeechRecognizer created via
    createOnDeviceSpeechRecognizer … are expected to return an empty list".
- `triggerModelDownload(Intent)` (API 33): "This might trigger user interaction". An overload with
  `ModelDownloadListener` (API 34) reports `onProgress` and `onSuccess`.

**Listener and listening calls:**
- `setRecognitionListener` and `startListening` are main thread only, and the listener must be set first.
- `stopListening()`: "Speech captured so far will be recognized as if the user had stopped speaking".
- `onResults(Bundle)`: get results with `getStringArrayList(RESULTS_RECOGNITION)`; `CONFIDENCE_SCORES` is
  optional.
- `onSegmentResults(Bundle)` and `onEndOfSegmentedSession()` (API 33).
- `onBufferReceived` is not guaranteed.

**Error codes:** `ERROR_CLIENT` = 5, `ERROR_SPEECH_TIMEOUT` = 6, `ERROR_NO_MATCH` = 7,
`ERROR_RECOGNIZER_BUSY` = 8, `ERROR_INSUFFICIENT_PERMISSIONS` = 9, `ERROR_LANGUAGE_NOT_SUPPORTED` = 12,
`ERROR_LANGUAGE_UNAVAILABLE` = 13 ("supported, but not available currently (e.g. not downloaded yet)"),
`ERROR_CANNOT_CHECK_SUPPORT` = 14.

**Manifest:** apps targeting API 30+ need
`<queries><intent><action android:name="android.speech.RecognitionService"/></intent></queries>`. The docs
show it under `createSpeechRecognizer`; INFERENCE: add it anyway, it costs nothing.

### 6.2 `EXTRA_AUDIO_SOURCE` semantics (FACT, API 33)

- **`EXTRA_AUDIO_SOURCE`:** "Optional ParcelFileDescriptor pointing to an already opened audio source for
  the recognizer to use. The caller of the recognizer is responsible for closing the audio. If this extra
  is not set or the recognizer does not support this feature, the recognizer will open the mic".
  "Along with this extra, please send EXTRA_AUDIO_SOURCE_CHANNEL_COUNT, EXTRA_AUDIO_SOURCE_ENCODING, and
  EXTRA_AUDIO_SOURCE_SAMPLING_RATE extras, otherwise the default values … will be used." When used as the
  value of `EXTRA_SEGMENTED_SESSION`: "The recognition session will end when and only when the audio is
  closed."
- **Defaults:** `EXTRA_AUDIO_SOURCE_CHANNEL_COUNT` = 1, `EXTRA_AUDIO_SOURCE_ENCODING` =
  `AudioFormat.ENCODING_PCM_16BIT`, `EXTRA_AUDIO_SOURCE_SAMPLING_RATE` = 16000.
- **`EXTRA_SEGMENTED_SESSION`:** its value is the *name* of another extra (`EXTRA_AUDIO_SOURCE`,
  `EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS` or `EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS`).
  Results arrive through `onSegmentResults`, and the session ends with `onEndOfSegmentedSession()`.
  "Depending on the recognizer implementation, this value may have no effect."
- **`EXTRA_PREFER_OFFLINE`** (API 23): "only use an offline speech recognition engine … may have no
  effect".
- **`EXTRA_LANGUAGE`:** a BCP 47 tag.
- **API 33-34 additions:** `EXTRA_ENABLE_FORMATTING` (33), `EXTRA_REQUEST_WORD_TIMING` (34),
  `EXTRA_ENABLE_LANGUAGE_DETECTION` (34).

**Not documented (INFERENCE, test on the phone):**
- **Container:** the docs give no format. The channel, encoding and rate extras imply **headerless raw
  PCM**. Send PCM16 mono 16 kHz samples only, without the 44-byte WAV header.
- **End of stream:** with segmented mode, closing the audio ends the session (documented). With a pipe
  (`ParcelFileDescriptor.createPipe()`), closing the write end gives EOF, so pass the **read end** in the
  intent, write the PCM from a worker thread, and close the write end.
- **Speed:** whether the recognizer accepts audio faster than real time is undocumented. Write as fast as
  the pipe accepts first. If results are empty or truncated, pace writes at real time
  (20 ms chunks every 20 ms).
- **Why segmented mode:** without it, the endpointer may end the session at the first long pause.
  Dream reports have pauses.

### 6.3 Transcription flow (INFERENCE)

1. After the recording stops, save `dream_<cycle>.wav` (PCM16 mono 16 kHz) first. The audio is the
   primary record; the transcript is derived from it.
2. On the main thread:
   - `if (!SpeechRecognizer.isOnDeviceRecognitionAvailable(ctx))`: mark the transcript
     `unavailable_no_on_device_service` and stop.
   - `rec = createOnDeviceSpeechRecognizer(ctx)`, then `rec.checkRecognitionSupport(intent, mainExecutor, cb)`.
   - If the language is in `getInstalledOnDeviceLanguages()`, continue.
   - If it is in `supported` or `pending`, mark the transcript `pending_model` and offer
     `triggerModelDownload` from the UI, **not during the nap**.
   - If `ERROR_CANNOT_CHECK_SUPPORT`, try once and handle `ERROR_LANGUAGE_UNAVAILABLE` or
     `ERROR_LANGUAGE_NOT_SUPPORTED`.
3. Build the intent with:
   - `EXTRA_LANGUAGE_MODEL = LANGUAGE_MODEL_FREE_FORM` ("This extra is required" for
     `ACTION_RECOGNIZE_SPEECH`)
   - `EXTRA_LANGUAGE`
   - `EXTRA_AUDIO_SOURCE = readPfd`
   - `EXTRA_AUDIO_SOURCE_ENCODING = ENCODING_PCM_16BIT`, `EXTRA_AUDIO_SOURCE_SAMPLING_RATE = 16000`,
     `EXTRA_AUDIO_SOURCE_CHANNEL_COUNT = 1`
   - `EXTRA_SEGMENTED_SESSION = RecognizerIntent.EXTRA_AUDIO_SOURCE`
   - `EXTRA_PREFER_OFFLINE = true`
4. Call `setRecognitionListener`, then `startListening(intent)`. A worker thread streams the data chunk
   to the write end, then closes it.
5. Concatenate the `onSegmentResults` texts. On `onEndOfSegmentedSession` (or `onResults` if the
   implementation ignores segmentation), save `transcript` and `confidence` if present, then call
   `destroy()`.
6. On any error, keep the WAV, save the transcript status with the error code, and allow a manual retry
   from the journal.

**"No cloud fallback" (INFERENCE):**
- OpenTDI must never call `createSpeechRecognizer()` (the default recognizer "is likely to stream audio to
  remote servers").
- The on-device path binds the OEM's configured component. In AOSP `SpeechRecognitionManagerServiceImpl`
  (`createSessionLocked`, lines 104-117; `getOnDeviceComponentNameLocked`, lines 236-249) the framework
  only resolves and binds that component. There is no network sandbox in this code.
- "No network" is therefore guaranteed only by the API contract and the OEM implementation, not enforced
  by the framework.
- Checking `getOnlineLanguages().isEmpty()` (expected for on-device) and setting `EXTRA_PREFER_OFFLINE`
  are the available safeguards. Document this honestly in the privacy notes.

**Samsung availability: NOT VERIFIED.** No Samsung document was found that names the on-device
recognizer component on Galaxy phones (Google's or Samsung's), or confirms that it supports
`EXTRA_AUDIO_SOURCE` or segmented sessions. Check on the phone (§10).

---

## 7. TTS cue rendering: `TextToSpeech.synthesizeToFile`

**FACT (API docs):**
- `synthesizeToFile(CharSequence text, Bundle params, File file, String utteranceId)` (API 21) and the
  `ParcelFileDescriptor` overload (API 30) are asynchronous: "the method just adds the request to the
  queue … The synthesis might not have finished (or even started!) … we recommend setting an utterance
  progress listener".
- The text must be no longer than `getMaxSpeechInputLength()`.

**`UtteranceProgressListener` (FACT):**
- `onStart` (API 15): fired "before the first bytes of a file are written".
- `onBeginSynthesis(utteranceId, sampleRateInHz, audioFormat, channelCount)` (API 24) "provides
  information about the format … Should be one of ENCODING_PCM_8BIT, ENCODING_PCM_16BIT or
  ENCODING_PCM_FLOAT".
- `onAudioAvailable` (API 24).
- `onDone` (API 15): "all output will have been written to disk for file synthesis requests".
- `onError(String, int)` (API 21): "there will never be a call to both onDone(String) and
  onError(String,int) for the same utterance".
- `onStop(String, boolean interrupted)` (API 23).

**FACT (AOSP): the WAV file is written by the framework, not by the engine.**
- `TextToSpeechService.java:1093-1110`: `SynthesisToFileOutputStreamSpeechItem` uses
  `new FileSynthesisCallback(mFileOutputStream.getChannel(), this, false)`.
- `FileSynthesisCallback.java`:
  - `WAV_HEADER_LENGTH = 44` and `WAV_FORMAT_PCM = 0x0001` (lines 39-40).
  - `start()` accepts only `ENCODING_PCM_8BIT`, `ENCODING_PCM_16BIT` or `ENCODING_PCM_FLOAT`
    (lines 108-113).
  - It writes a 44-byte placeholder, streams the data, and in `done()` seeks to 0 and writes the real
    header (lines 236-238).
  - `makeWavHeader` (lines 285-312) writes `RIFF`, `WAVE`, `fmt ` with size 16, **format tag
    `WAV_FORMAT_PCM` (1) for every encoding**, `bitsPerSample = bytesPerSample*8`, then `data`.
- **Consequence (INFERENCE):** an engine that outputs float produces a non-standard file: tag 1 (PCM) with
  32 bits per sample, which really holds IEEE float. Section 8.1's reader must handle this. Use the
  encoding reported by `onBeginSynthesis` to decide, not the header alone.

**Google and Samsung TTS sample rate or encoding: NOT VERIFIED.** Neither engine documents it, so do not
hard-code a rate. Read `onBeginSynthesis` (log it) or the header, create the prompt `AudioTrack` at that
rate, and let AudioFlinger resample (§2.6).

**INFERENCE:**
- Pick a voice with `Voice.isNetworkConnectionRequired() == false`, so cue rendering is offline.
- Render all three cues when the theme is created or edited, never during the nap.
- Peak-normalise them as required by §3.4.

---

## 8. WAV I/O and end-of-report silence detection

### 8.1 RIFF/WAVE facts (FACT, McGill MMSP WAVE spec page, https://www.mmsp.ece.mcgill.ca/Documents/AudioFormats/WAVE/WAVE.html, last update 2022-09-27)

**Structure:**
- Little-endian. The file is `RIFF` + size + `WAVE`, followed by chunks.
- `fmt ` "Chunk size: 16, 18 or 40". Its fields are `wFormatTag`, `nChannels`, `nSamplesPerSec`,
  `nAvgBytesPerSec`, `nBlockAlign`, `wBitsPerSample`, plus `cbSize` and the extensible fields.

**Format codes:** `0x0001 WAVE_FORMAT_PCM`, `0x0003 WAVE_FORMAT_IEEE_FLOAT`, and
`0xFFFE WAVE_FORMAT_EXTENSIBLE` (data format given by the first 2 bytes of the SubFormat GUID).

**Encoding rules:**
- "PCM data is two's-complement except for resolutions of 1-8 bits, which are represented as offset
  binary."
- "For float data, full scale is 1."
- "For all formats other than PCM, the Format chunk must have an extended portion … (with value 0) must
  be present." "The non-PCM formats must have a fact chunk."
- `WAVE_FORMAT_EXTENSIBLE` "should be used whenever: PCM data has more than 16 bits/sample. The number of
  channels is more than 2…".

**Padding:** the RIFF size formula includes "(0 or 1)" pad bytes. A chunk with an odd length is
followed by one pad byte.

**Minimal, robust implementation (INFERENCE; ponytail scope: only what OpenTDI writes and reads):**
- **Write PCM16 mono only** for recordings and normalised cues. Header layout: 44 bytes, tag 1, fmt size
  16 (the same layout as `FileSynthesisCallback`).
  - Open the file, write a placeholder header, stream frames, and patch the RIFF and `data` sizes on stop.
  - For crash safety, also patch the sizes every ~5 s. Otherwise a killed process leaves size 0.
- **Read:** walk the chunks generically.
  - Skip unknown chunks such as `LIST`, honouring the pad byte.
  - Accept tag 1 with 16 bits; tag 3 with 32 bits (float); tag 0xFFFE with the GUID's first 2 bytes equal
    to 1 or 3; and tag 1 with 32 bits as float **only if** the caller says the file came from TTS with
    `ENCODING_PCM_FLOAT`.
  - If the `data` size is 0 or larger than the rest of the file, read to EOF. This covers crash recovery.
- **Float on disk:** not needed for OpenTDI. If it ever is, write tag 3, fmt size 18 with `cbSize` = 0,
  and a `fact` chunk.
- **Test:** a round-trip unit test (write PCM16, read back, compare samples), and a reader test against a
  hand-made header with an odd-sized `LIST` chunk and a header truncated to size 0.

### 8.2 Silence detection for the end of a spoken report

**Measured facts that drive the design:**
- **CDD levels:** `VOICE_RECOGNITION` gives about −22.35 dBFS at 90 dB SPL. `UNPROCESSED` gives
  −36 dBFS at 94 dB SPL (CDD §5.4.2, §5.11, see §5.1).
- **INFERENCE:** soft, sleepy speech at the nightstand might be roughly 50-60 dB SPL at the phone. That
  figure is a **GUESS**, not sourced. It would land around −52 to −62 dBFS on `VOICE_RECOGNITION` and
  −66 to −76 dBFS on `UNPROCESSED`. **A fixed threshold such as −40 dBFS would treat a real whispered
  report as silence.** The threshold must therefore be relative to the measured noise floor.
- **What Dormio did (FACT, `reference_repos/dormio/js`):** there was no silence detection.
  - `script_timer.js:18`: `"recording-time" : 60`.
  - `script_wearable.js:212`: `"recording-time" : 30`.
  - Recording starts when the wakeup prompt ends (`script_timer.js:335-336`,
    `wakeup_msg_player.onended = () => { startRecording(...) }`).
  - The wakeup is ended by a timer `recordingTime * 1000` ms after the wakeup begins
    (`script_timer.js:344-346`).
  - Capture used WebAudioRecorder MP3 at 160 kbps (`script_timer.js:497-507`).
  - So CLAUDE.md's "4 s silence or 45 s max" is an OpenTDI **GUESS**, not an MIT value.

**Algorithm (INFERENCE; every number is a configurable GUESS, logged per session):**

```kotlin
// 16 kHz mono PCM16, 20 ms frames (320 samples). All thresholds in dBFS (20*log10(rms/32768)).
class EndOfReportDetector(
    val marginDb: Double = 10.0,        // GUESS: speech must exceed floor by this much
    val hangoverMs: Int = 4000,         // GUESS (CLAUDE.md): silence after speech that ends the report
    val noSpeechTimeoutMs: Int = 15000, // GUESS: nothing said at all -> stop
    val maxMs: Int = 45000,             // GUESS (CLAUDE.md); Dormio used a fixed 60 s / 30 s
    val minSpeechFrames: Int = 5        // 100 ms above threshold counts as speech onset
) {
    private var floorDb = Double.NaN; private var t = 0; private var lastSpeech = -1
    private var run = 0; var spoke = false; private set
    /** returns true when recording should stop */
    fun onFrame(rmsDb: Double): Boolean {
        t += 20
        // floor: fast to fall, slow to rise (about 1 dB/s), so speech never drags it up
        floorDb = if (floorDb.isNaN()) rmsDb else if (rmsDb < floorDb) rmsDb else floorDb + 0.02
        val speech = rmsDb > floorDb + marginDb
        run = if (speech) run + 1 else 0
        if (run >= minSpeechFrames) { spoke = true; lastSpeech = t }
        return t >= maxMs ||
            (!spoke && t >= noSpeechTimeoutMs) ||
            (spoke && t - lastSpeech >= hangoverMs)
    }
}
```

**Notes:**
- Seed the floor from the first 300-500 ms after the wakeup prompt's playback has fully ended. The
  prompt's tail would raise the floor otherwise. Starting the recorder on prompt end mirrors Dormio's
  `onended`.
- Because the floor only rises slowly, a fan that starts mid-report raises it only gradually.
- Keep the detector a pure function of a sequence of frame dB values. The same code then runs in the
  replay harness (owner requirement 13). Unit-test it with synthetic sequences: noise only, speech then
  silence, speech with a 3 s pause, and continuous speech to the maximum.
- Log `floorDb`, `spoke`, the stop reason and the duration per report.

---

## 9. Owner requirements covered by these notes

| Req | Where | Status |
|---|---|---|
| 16 FGS started while visible, alive all nap | §3.1 (focus needs FGS on API 35+), §5.4 (mic FGS while-in-use rule) | FACT-backed |
| 18 Audio focus, DND, BT disconnect, hard volume cap | §3.1-§3.4 | design is INFERENCE; DND matrix FACT (AOSP); Samsung default unverified |
| 19 −75 dBFS carrier untested, configurable, manual test | §4.3-§4.6 | level is a **GUESS**; test procedure given |
| 20 Phase 2 recording + transcript | §5, §6, §8 | Samsung STT unverified |
| 21 Defer watch speaker/mic, USB DAC | USB facts kept for Phase 3 (§1.1, §4.1) | — |
| 13 CSV logging / replay | §4.6 step 9, §8.2 notes | — |

CLAUDE.md items these notes contradict or qualify:
- §5.1 lists only `TYPE_BUILTIN_SPEAKER` for the watch. That route is deferred.
- §5.2 says the "−75 dBFS carrier prevents standby". That is unverified (§4.2-§4.3).
- §5.3 says to record with `MIC` or `UNPROCESSED`. `VOICE_RECOGNITION` is preferred (§5.1).
- §3 gives "4 s silence / 45 s max". These are guesses (§8.2).

---

## 10. Hardware verification checklist (cannot be answered from documentation)

1. **The Samsung phone's on-device recognizer:**
   - Log `isOnDeviceRecognitionAvailable`, the `checkRecognitionSupport` lists for `en-US`, and whether a
     16 kHz PCM16 pipe with `EXTRA_SEGMENTED_SESSION = EXTRA_AUDIO_SOURCE` returns segment results.
   - Check whether feeding faster than real time works.
   - Check whether `getOnlineLanguages()` is empty.
2. **One UI 8 DND:** the default state of the "Media sound" exception, and whether `USAGE_MEDIA` cues are
   heard with DND in Priority mode at that default.
3. **The keep-alive carrier:** run the §4.6 grid per earbud, at the low nap-time index and the calibrated
   maximum, and record the codec and absolute-volume state.
4. **Phone standby time:** read `ro.audio.flinger_standbytime_ms` from `getprop` on the phone, and the
   `dumpsys media.audio_flinger` "Standby delay ns" value for the Bluetooth output thread.
5. **Disconnect:** power off the earbuds mid-carrier and mid-cue.
   - Confirm that the emergency stop runs before any speaker output. Record a sound-level reading near the
     phone.
   - Confirm which of BECOMING_NOISY, `onAudioDevicesRemoved` and the routing listener fires first on
     A2DP and on LE Audio.
6. **Recording during A2DP playback:** no HFP switch beep, the codec unchanged in Developer options,
   `getRoutedDevice()` = `TYPE_BUILTIN_MIC`, and `isClientSilenced` false with the screen off inside the
   foreground service.
7. **`PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED`** on the phone, and the measured noise-floor dBFS for
   `VOICE_RECOGNITION` versus `UNPROCESSED` in the bedroom. This calibrates `marginDb`.
8. **TTS:** log the `onBeginSynthesis` rate and encoding for each installed engine (Google, Samsung), and
   whether float output appears with tag 1 and 32 bits.
9. **Two tracks:** in `dumpsys media.audio_flinger`, the carrier and prompt appear on the same output
   thread for each route.

---

## Sources

**developer.android.com reference pages** (all fetched 2026-10-27, under
https://developer.android.com/reference/android/):
- `media/AudioDeviceInfo`, `media/AudioDeviceCallback`, `media/AudioManager`
- `media/AudioTrack`, `media/AudioTrack.Builder`, `media/AudioAttributes`, `media/AudioFormat`,
  `media/AudioFocusRequest`
- `media/AudioRecord`, `media/AudioRecord.Builder`, `media/MediaRecorder.AudioSource`
- `media/VolumeShaper.Configuration`, `media/VolumeShaper.Configuration.Builder` (`setDuration`
  default 1 s, `setCurve`), `media/AudioPlaybackConfiguration` (no public mute-reason API was found
  there)
- `speech/tts/Voice` (`isNetworkConnectionRequired()`, API 21)
- `speech/SpeechRecognizer`, `speech/RecognizerIntent`, `speech/RecognitionListener`,
  `speech/RecognitionSupport`, `speech/RecognitionSupportCallback`
- `speech/tts/TextToSpeech`, `speech/tts/UtteranceProgressListener`
- `app/NotificationManager`, `app/NotificationManager.Policy`, `service/notification/ZenPolicy`

**developer.android.com guides:**
- https://developer.android.com/media/optimize/audio-focus
- https://developer.android.com/media/platform/sharing-audio-input
- https://developer.android.com/media/platform/output
- https://developer.android.com/develop/background-work/services/fgs/service-types
- https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start
- https://developer.android.com/about/versions/14/behavior-changes-14
- `https://developer.android.com/media/platform/audio-routing` returned **404**, so it was not used.

**source.android.com:**
- https://source.android.com/docs/compatibility/16/android-16-cdd (§5.4.1, §5.4.2, §5.4.5, §5.11)
- https://source.android.com/docs/core/audio/terminology

**AOSP files** (revisions in the header):
- `frameworks/av`: `services/audioflinger/Threads.cpp`, `services/audiopolicy/enginedefault/src/Engine.cpp`,
  `services/audiopolicy/managerdefault/AudioPolicyManager.cpp`
- `frameworks/base`:
  - `services/core/java/com/android/server/notification/ZenModeHelper.java`
  - `core/java/android/service/notification/ZenModeConfig.java`
  - `media/java/android/media/AudioAttributes.java`
  - `services/core/java/com/android/server/audio/AudioService.java`
  - `core/java/android/speech/{SpeechRecognizer,SpeechRecognizerImpl}.java`
  - `core/java/android/speech/tts/{FileSynthesisCallback,TextToSpeechService}.java`
  - `services/core/java/com/android/server/speech/SpeechRecognitionManagerServiceImpl.java`
- `packages/modules/Bluetooth`:
  - `system/audio_bluetooth_hw/{stream_apis.cc,device_port_proxy.cc}`
  - `system/btif/src/{btif_av.cc,btif_a2dp_source.cc}`
  - `system/stack/a2dp/a2dp_aac_encoder.cc`
  - `system/bta/le_audio/client.cc`
- `hardware/libhardware`: `modules/usbaudio/audio_hal.c`

**Samsung:**
- https://www.samsung.com/us/support/answer/ANS10002889 (DND exceptions)
- https://www.samsung.com/us/support/troubleshoot/TSG10004902/ (Media volume limiter)

**Third party (headset standby evidence, not Android documentation):**
- https://github.com/mltony/nvda-bluetooth-audio (README.md)
- https://lee.org/blog/?p=9856

**WAV:** https://www.mmsp.ece.mcgill.ca/Documents/AudioFormats/WAVE/WAVE.html

**Reference repo:** `reference_repos/dormio/js/script_timer.js`, `script_wearable.js` (dormio commit
`7c00cc39be113d90216a6d2ebab21adf1066cc91`).
