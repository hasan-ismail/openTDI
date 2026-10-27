# Dormio Device Duo Wearable (ThingEngineer): firmware analysis for OpenTDI

- **Repo analysed:** `reference_repos/Dormio-Device-Duo-Wearable`, origin
  <https://github.com/ThingEngineer/Dormio-Device-Duo-Wearable>, HEAD `baeede4` (2022-12-07,
  "made lib ver fixed for testing stability"), 50 commits from 2018-11-01 to 2022-12-07.
- **Files read in full:** `src/main.cpp` (648 lines), `include/main.h` (169), `include/env.h.example` (14),
  `platformio.ini` (45), `README.md` (11), `test/README`, `.travis.yml`, `.gitignore`, `include/README`, `lib/README`,
  plus a search of the full git history (`git log --all -p`).
- **Written:** 2026-10-27, for OpenTDI owner requirement D.10 (extract Dormio's real numbers and mark each
  CLAUDE.md §4 default as sourced or guessed).
- **Citations:** a bare `file:line` is relative to the Duo repo root. Other repos are prefixed
  (`dormio/…`, `openSleep/…`, meaning `reference_repos/dormio/…` and so on). Third-party library lines are cited at the exact
  version pinned in `platformio.ini`, fetched from GitHub on 2026-10-27.
- **Labels:** **FACT** means read directly from the cited source. **INFERENCE** means my reasoning from facts, to be
  checked. **GUESSED** means a proposed number with no source behind it, to be tuned on recorded naps with the replay
  harness (requirement D.13).

---

## 0. Summary

1. **FACT: there is no sleep-onset detection in this firmware.** It has no HR, EDA or motion threshold, no
   baseline, no averaging window, no EMA, no relative-delta calculation, no hold time and no trigger. The firmware
   samples raw sensors, packs them into a 2,724-byte binary frame every 5 s (nominal) and sends each frame over a
   WebSocket (`src/main.cpp:70-96`, `:322-332`). A search of the whole git history for
   `thresh|baseline|delta|average|bpm|heart rate|sleep|onset|flex|spo2` finds only PPG-setup lines. The companion server
   repo, `ThingEngineer/Dormio-Device-Duo-Webapp`, contains only OTA firmware endpoints, and its data route names a
   controller that is not in the repo (§7).
2. **FACT: there is no flex sensor.** The sensors are a MAX30102 PPG (raw IR and Red), an ADS1115 ADC carrying GSR on A0
   and "ECG/EKG/EMG" on A1, an LSM6DS3 accelerometer and gyro, and an MLX90614 IR skin/ambient thermometer. The board
   also has a DRV2605 haptic driver (configured but never fired), a beeper and an OLED (`include/main.h:27-33`, `:85-88`).
3. **INFERENCE: this repo cannot source any number in CLAUDE.md §4.** The real Dormio numbers are in the 2020 paper:
   HR > 5 BPM, EDA > 4 μS (the table says > 5 μS), flex > 8 KΩ, a 120 s baseline, and OR logic. More defaults are in the
   `christinatchen/dormio` web app (§9). What this repo does offer is architecture:
   - multi-rate sampling;
   - sensing on the wearable with the logic off-device;
   - framed packets carrying a counter, a device ID and a checksum;
   - a heartbeat and command channel.

   It also has several defects that OpenTDI should not copy (§10.3).

---

## 1. Provenance and license

| Item | Value | Label / source |
|---|---|---|
| License grant | "The [MIT license](http://opensource.org/licenses/MIT)." | FACT, `README.md:9-11` |
| LICENSE file | None in the tree | FACT, file listing |
| GitHub license detection | `"license": null` | FACT, <https://api.github.com/repos/ThingEngineer/Dormio-Device-Duo-Wearable> (fetched 2026-10-27) |
| Author | "Author: Josh Campbell" | FACT, `include/main.h:4`. All commits are by `ThingEngineer` (`git log`) |
| GitHub description | "Dormio Device Duo - Wearable Device Sensor Platform" | FACT, GitHub API, as above |
| Created / last push | 2018-10-18 / 2022-12-07 | FACT, GitHub API |
| Usage docs | "wip" | FACT, `README.md:7` |
| Tests | None. `test/README` is the stock PlatformIO placeholder (`test/README:1-11`) | FACT |
| CI | `.travis.yml` is the stock PlatformIO template with every line commented out (`.travis.yml:27-67`) | FACT |
| Companion server | `ThingEngineer/Dormio-Device-Duo-Webapp` (Laravel/PHP; README also says MIT, `readme.md:16-18`; API `"license": null`; last push 2018-11-02) | FACT, <https://api.github.com/repos/ThingEngineer/Dormio-Device-Duo-Webapp> |

- **INFERENCE (licensing):** the grant is MIT, but it is only a README line, with no license text and no copyright
  holder line. If OpenTDI ever copied code from this repo, it should ship the MIT text with attribution to Josh
  Campbell / ThingEngineer. OpenTDI does not need to copy any code: nothing here is Kotlin and nothing implements
  detection. This is not legal advice.
- **INFERENCE (relationship to MIT):** apart from the name, nothing in the code or docs ties this repo to the MIT Media
  Lab Dormio team. The boot screen shows "Dormio Device Duo" and "--==Deep Dream==--" (`src/main.cpp:53-54`). Treat it
  as an independent 2018 hardware effort, not an MIT reference implementation. I could not verify any link to Haar
  Horowitz's group.

---

## 2. Hardware

| Function | Part | Bus / pin | Configuration in code | Source |
|---|---|---|---|---|
| MCU | ESP8266 on a Heltec WiFi Kit 8 board. PlatformIO lists "ESP8266", "80MHz", "4MB" flash, "80KB" RAM | n/a | `platform = espressif8266`, `board = heltec_wifi_kit_8` in **both** envs. `default_envs = nodemcu`, but the `nodemcu` env also uses the Heltec board | FACT: `platformio.ini:13`, `:15-17`, `:23-25`; <https://docs.platformio.org/en/latest/boards/espressif8266/heltec_wifi_kit_8.html> |
| PPG (HR/SpO2 source) | MAX30102 (SparkFun MAX3010x lib 1.1.1) | I2C 0x57 (lib `MAX30105.h:22`) | `ledBrightness 32` ("0=Off to 255=50mA"), `sampleAverage 8`, `ledMode 2` (Red + IR), `sampleRate 800`, `pulseWidth 215`, `adcRange 2048` | FACT: `src/main.cpp:34-42`; `include/main.h:27`, `:43`; `platformio.ini:37` |
| EDA / GSR | Analog front end described as "LM324 GSR readings", read through the ADS1115 | ADS1115 A0 (`GSRpin 0`), I2C 0x48 (lib default) | `ads.setGain(GAIN_ONE)` (±4.096 V PGA). Raw ADC counts are sent; there is no μS conversion | FACT: `include/main.h:87`, `:115`; `src/main.cpp:31-32`, `:127`; lib `Adafruit_ADS1015.h:31`, `:131` |
| ECG / EMG | Front end not specified ("ECG/EKG/EMG Electrocardiography/Electromyography") | ADS1115 A1 (`ECGpin 1`) | Sampled at the full rate (nominal 50 Hz) | FACT: `include/main.h:88`; `src/main.cpp:108` |
| IMU | LSM6DS3 6-DoF (SparkFun lib 1.0.0) | I2C, lib default 0x6B | `imu.begin()` with library defaults: accel ±16 g, 416 Hz ODR, 100 Hz bandwidth; gyro 2000 dps, 416 Hz | FACT: `src/main.cpp:30`; lib `SparkFunLSM6DS3.h:146`, `SparkFunLSM6DS3.cpp:349-362` (tag `V_1.0.0`) |
| Skin temperature | MLX90614 IR thermometer, object and ambient | I2C 0x5A (lib default) | Read once per frame | FACT: `src/main.cpp:29`, `:137-138`; lib `Adafruit_MLX90614.h:25` |
| Haptic | DRV2605 | I2C 0x5A, behind mux channel 6 | `setMode(DRV2605_MODE_INTTRIG)`, `selectLibrary(1)`, `setWaveform(1, 1)` ("Strong click 100%"). **Never fired** in HEAD (§7) | FACT: `src/main.cpp:22-26` |
| I2C multiplexer | TCA9548A (named in a comment) at 0x70 | n/a | Channel 6 = haptic; channel 7 = MLX90614, IMU, ADS1115 (and whatever was initialised after it) | FACT: `src/main.cpp:22`, `:28`, `:430-441` |
| Display | SSD1306 128×32 OLED at 0x3C, reset on GPIO16 | I2C (`Wire.begin(4,5)`) | Shows the frame counter and checksum each frame | FACT: `include/main.h:48-52`; `src/main.cpp:20`, `:145-150` |
| Button | GPIO0 (D3) | n/a | Debounce 20 ms. A short press toggles the OLED; a long press over 3000 ms resets Wi-Fi | FACT: `include/main.h:85`, `:100-102`, `:161`; `src/main.cpp:599-630` |
| Beeper | GPIO15 | n/a | One 100 ms beep at boot only | FACT: `include/main.h:86`; `src/main.cpp:14-17` |
| Supply monitor | ESP8266 internal ADC in VCC mode | n/a | `ESP.getVcc()` once per frame | FACT: `include/main.h:167`; `src/main.cpp:142` |

Notes:

- **FACT: no flex sensor exists.** `include/main.h:85-88` lists every port alias, and `include/main.h:109-126` lists every
  frame field. Neither has a flex channel. The only muscle-related channel is the "ECG/EKG/EMG" input on A1.
- **INFERENCE: the A1 channel at a nominal 50 Hz is too slow for surface EMG.** Surface EMG energy sits far above the
  25 Hz Nyquist limit, so this input cannot stand in for a muscle-tone (flex) measurement, and nothing in the code tries.
- **INFERENCE: the mux exists because the MLX90614 and the DRV2605 share address 0x5A** (`Adafruit_MLX90614.h:25`
  `MLX90614_I2CADDR 0x5A`; `Adafruit_DRV2605.h:30` `DRV2605_ADDR 0x5A`, both at the pinned 1.1.1).
- **FACT: body placement is not documented anywhere** (the README says "wip"). Whether the GSR electrodes, PPG and IMU
  sat on a finger, the palm or the wrist is unknown. This is a gap (§12).
- **FACT: history.** GSR first used the ESP8266's internal A0 ADC and was moved to the ADS1115 on 2018-11-01 (commits
  `5fce9ca`, "remove GSR from A0 and add build in VCC value to data buffer", and `c3958cb`, "add ADS1115 support and
  change GSR to use that over internal A0 adc").

---

## 3. Sampling rates

The constants are FACT, from `include/main.h:104-107`: `ST 5` (frame length in s), `SF 50` (Hz), `HALF_SF (SF / 2)` = 25,
and `SAMPLE_COUNT (ST * SF)` = 250 samples per frame.

| Channel | Nominal rate | Samples per frame | Encoding | Source |
|---|---|---|---|---|
| PPG IR, PPG Red | 50 Hz | 250 each | 4 B uint32 LE (18-bit value) | FACT: `src/main.cpp:101-113`; `include/main.h:110`; lib `MAX30105.cpp:655` (`&= 0x3FFFF`) |
| ECG/EMG (ADS1115 A1) | 50 Hz | 250 | 2 B uint16 LE raw counts | FACT: `src/main.cpp:108`; `include/main.h:111` |
| Accel X/Y/Z, gyro X/Y/Z | 2 Hz | 10 each | 3 B custom float (§4) | FACT: `src/main.cpp:82`, `:118-130`; `include/main.h:116-121` |
| GSR (ADS1115 A0) | 2 Hz | 10 | 2 B uint16 LE raw counts | FACT: `src/main.cpp:127`; `include/main.h:115` |
| MAX30102 die temperature, MLX90614 skin (object) and ambient temperature | 0.2 Hz (one per frame) | 1 each | 3 B custom float | FACT: `src/main.cpp:135-138`; `include/main.h:112-114` |
| Epoch (s since boot), frame count, MAC, VCC, checksum | 0.2 Hz | 1 each | see §4 | FACT: `src/main.cpp:139-143` |

**Where 2 Hz comes from (FACT).** `sampleRateModHalfSF()` runs when `fullSampleCounter % HALF_SF == 0`
(`src/main.cpp:82`). That check comes after `sampleRateFull()` has incremented the counter (`src/main.cpp:111`), so it
fires at counts 25, 50, …, 250: ten calls per 250-sample frame. The field sizes agree: GSR is 20 B = 10 × 2 and each
accel axis is 30 B = 10 × 3 (`include/main.h:115-121`). The code comment at `src/main.cpp:82` says "(2 per second)".

**How the loop is actually paced (INFERENCE from library source).** `loop()` has no timer (`src/main.cpp:70-96`); the
rate comes from blocking reads.

- `ppg.getIR()` and `ppg.getRed()` each call `safeCheck(250)` (SparkFun `MAX30105.cpp:531-548` at `v1.1.1`).
  `safeCheck` polls `check()` every 1 ms until `check() == true` (`MAX30105.cpp:704-717`). `check()` returns the
  **number** of new FIFO samples (`MAX30105.cpp:592-699`), so `== true` holds only when exactly one new sample is
  found. Each loop iteration therefore waits for one fresh FIFO sample in `getIR()` and another in `getRed()`. The IR
  and Red values stored as one "sample" come from two consecutive FIFO records.
- The PPG settings are 800 sps with 8-sample averaging. That is about 100 FIFO records/s if the MAX30102 rate counts
  one Red + IR pair as a sample. I could not confirm that definition: the MAX30102 datasheet fetch from analog.com timed
  out. If it holds, two waits per iteration give about 50 iterations/s, matching the author's formula "sampleRate /
  sampleAverage / 2 (2 because there is one sample for each, ir & red)" (`include/main.h:105`).
- Each `ads.readADC_SingleEnded()` blocks on `delay(m_conversionDelay)` = 9 ms for the ADS1115
  (`Adafruit_ADS1015.h:38` `ADS1115_CONVERSIONDELAY (9)`; `Adafruit_ADS1015.cpp:128`, `:207`, at `1.1.2`). That is one
  ECG read per iteration and one more GSR read every 25th iteration, along with 6 IMU reads.
- **Result:** "50 Hz" is approximate and jittery. The only timestamp is a per-frame `millis() / 1000` in whole seconds
  (`src/main.cpp:139`); there are no per-sample timestamps, so a receiver has to assume uniform spacing.
- **IMU aliasing:** the 2 Hz IMU value is a polled snapshot of an accelerometer running at a 416 Hz ODR with a 100 Hz
  bandwidth (library defaults), with no decimation filter, so motion above 1 Hz aliases. A hypnic jerk shorter than
  0.5 s usually falls between two snapshots 500 ms apart, so it is missed or shows up in a single sample. OpenTDI's
  25 Hz stream is far better suited (§10.7).

---

## 4. Wire format (one frame)

The offsets below are computed from the `#define`s at `include/main.h:109-148` (FACT; checked with a script).

| Field | Offset | Size (B) | Content |
|---|---|---|---|
| IR | 0 | 1000 | 250 × uint32 LE |
| RED | 1000 | 1000 | 250 × uint32 LE |
| ECG | 2000 | 500 | 250 × uint16 LE |
| DIE_TEMP | 2500 | 3 | 1 × float3 |
| SKIN_TEMP | 2503 | 3 | 1 × float3 |
| AMBIENT_TEMP | 2506 | 3 | 1 × float3 |
| GSR | 2509 | 20 | 10 × uint16 LE |
| ACCEL_X / Y / Z | 2529 / 2559 / 2589 | 30 each | 10 × float3 |
| GYRO_X / Y / Z | 2619 / 2649 / 2679 | 30 each | 10 × float3 |
| EPOCH | 2709 | 4 | uint32 LE, `millis()/1000` |
| FRAME_COUNT | 2713 | 2 | uint16 counter (see the overrun note below) |
| MAC | 2715 | 6 | bytes in reverse order |
| VCC | 2721 | 2 | uint16 LE |
| CHECKSUM | 2723 | 1 | XOR of every byte loaded |
| **Total** | | **2724** | **≈ 544.8 B/s** at the nominal 5 s frame (INFERENCE: arithmetic) |

The packing functions all live in `src/main.cpp`:

- **`load32Buffer` / `load16Buffer`** write little-endian bytes (`src/main.cpp:231-242`, `:251-262`). FACT.
- **`loadFloatBuffer` ("float3")** writes the integer part as an int16 LE in sign-magnitude form, followed by one byte
  holding `trunc(100 × |fraction|)`, which gives 0.01-unit resolution (`src/main.cpp:201-222`). FACT.
  - The sign-magnitude conversion is `(characteristic ^ 0x7FFF) + 1` (`src/main.cpp:203`), so −1 encodes as 0x8001.
    FACT, checked by script.
  - **Defect (FACT):** for −1 < v < 0 the integer part is 0, so the sign is lost: −0.35 and +0.35 both encode as
    `(0x0000, 35)`. The accelerometer reports in g (`calcAccel`: raw × 0.061 × (range >> 1) / 1000,
    `SparkFunLSM6DS3.cpp:630-633`).
  - **INFERENCE:** a resting limb has gravity split across axes, so components between −1 g and 0 g are common.
    The firmware corrupts exactly the orientation information that posture or "limp" analysis would need.
- **MAC bytes** are written in reverse order "to allow sequential retrieval on the server" (`src/main.cpp:269-275`).
  FACT.
- **Frame-count overrun:** `FRAME_COUNT_SIZE` is 2 (`include/main.h:123`), but the counter is written with
  `load32Buffer` (4 B) at `src/main.cpp:140`, which runs into the first 2 MAC bytes. The next line overwrites them
  (`src/main.cpp:141`). FACT. INFERENCE: this is harmless, because `frameCounter` is `uint16_t` (`include/main.h:158`),
  so the 2 extra bytes are 0 and do not change the XOR checksum.
- **Checksum:** a running XOR of every loaded byte (`src/main.cpp:282-284`), reset every frame (`src/main.cpp:92`) and
  stored at offset 2723 (`src/main.cpp:143`). FACT.
- **"Encryption"** XORs every byte with each key byte in turn (`src/main.cpp:422-428`), using `encryptionKey[3] =
  {47,7,191}` (`include/env.h.example:14`). FACT.
  - **INFERENCE:** sequential XOR collapses to a single-byte XOR with 47 ^ 7 ^ 191 = **151 (0x97)** (checked by
    script). This gives no confidentiality.
  - It runs after the checksum is stored (`src/main.cpp:85-88`), so the checksum byte is XORed too.

---

## 5. Networking

| Aspect | Behaviour | Source |
|---|---|---|
| Transport | Wi-Fi only. The includes are `ESP8266WiFi`, `ESP8266HTTPClient`, `ESP8266httpUpdate`, `WebSocketsClient`, `DNSServer`, `ESP8266WebServer` and `WiFiManager`. **No BLE, MQTT or UDP** | FACT: `include/main.h:13-33` |
| Wi-Fi provisioning | WiFiManager `autoConnect("Dormio Setup")` captive portal. On failure: `delay(3000)`, then `ESP.reset()`. `setBreakAfterConfig(true)` | FACT: `src/main.cpp:519-537`; lib `tzapu/WiFiManager @ 0.16.0` (`platformio.ini:43`) |
| Wi-Fi reset | A long press (> 3000 ms) clears the credentials and reopens the portal | FACT: `src/main.cpp:607-619`; `include/main.h:161` |
| WebSocket client | `links2004/WebSockets @ 2.3.5`; `webSocket.begin(wsHost, wsPort, wsAccessToken, "")`; reconnect every **10000 ms** | FACT: `platformio.ini:44`; `src/main.cpp:591-593` |
| Endpoint example | `wsHost "app.yourdomain.com"`, `wsPort 80`, path and token `"/ws/?t=aBc123$!*"` | FACT: `include/env.h.example:1-3`. INFERENCE: plain `ws://` on port 80, with the token in cleartext in the URL |
| On connect | Sends text `{"c":"sub","chan":"hb"}` and `{"c":"sub","chan":"dormio"}` (subscribe to a heartbeat channel and a data channel) | FACT: `src/main.cpp:341-346` |
| Uplink | One **binary** message (`sendBIN`) per frame, 2724 B, so 0.2 messages/s nominal | FACT: `src/main.cpp:84-88`, `:322-332` |
| Downlink commands | A single-character text payload parsed with `strtol`. `"1"`: disconnect, Wi-Fi off, `delay(100)` then `delay(3000)`, then `ESP.restart()`. `"2"`: reply text `"2"` (heartbeat). `"9"`: `checkForUpdates()`. Anything else logs "Invalid command". Binary downlink is only hexdumped | FACT: `src/main.cpp:348-383` |
| Connection gate | Sampling **and** sending run only while `isConnected` (`src/main.cpp:75`). The flag is set on `WStype_CONNECTED` and cleared on `WStype_DISCONNECTED` (`src/main.cpp:338`, `:345`). **Nothing is buffered while disconnected**, so data is lost | FACT |
| Send-condition bug | `if ( (WiFi.status() == WL_CONNECTED) && (isConnected = true) )` is an assignment, which is always true | FACT: `src/main.cpp:323`. INFERENCE: `==` was intended |
| OTA update | HTTP GET `fwUrlBase + "ver/" + MAC`. On HTTP 200, if the integer body > `firmwareVersion` (= 1 in the example), it calls `ESPhttpUpdate.update(client, updateHost, updatePort, "/bin/" + MAC)`. Runs at boot and on command `"9"` | FACT: `src/main.cpp:62`, `:446-499`; `include/env.h.example:5-8` |
| OTA server | `ver/{mac}` returns `1001`. `bin/{mac}` requires `User-Agent: ESP8266-http-Update` and sends `x-MD5` | FACT: Webapp `app/Http/Controllers/FirmwareController.php:15-18`, `:26-48`; `routes/web.php:21-22` (<https://github.com/ThingEngineer/Dormio-Device-Duo-Webapp>) |
| HTTP POST (disabled) | Commented out. It sent the binary buffer with `Content-Type: application/json` | FACT: `src/main.cpp:289-320` (`:295`, `:298`); the define is commented out in `include/env.h.example:10` |
| Serial debug | 921600 baud | FACT: `src/main.cpp:8`; `platformio.ini:21`, `:29` |

INFERENCE: the OTA path is plain HTTP and checks only an MD5 the server itself supplies, with no signature, so anyone
on the network path could replace the firmware.

---

## 6. Timing constants (complete list)

| Constant | Value | Purpose | Source |
|---|---|---|---|
| `ST` | 5 s | Frame length | `include/main.h:104` |
| `SF` | 50 Hz | Full sampling rate (nominal) | `include/main.h:105` |
| `HALF_SF` | 25 samples | Divider for the 2 Hz channels | `include/main.h:106`; `src/main.cpp:82` |
| `SAMPLE_COUNT` | 250 | Samples per frame | `include/main.h:107` |
| `DEBOUNCE_MS` | 20 ms | Button debounce | `include/main.h:102` |
| `longPressInterval` | 3000 ms | Long press = Wi-Fi reset | `include/main.h:161` |
| Boot beep | 100 ms | Beeper on, then off | `src/main.cpp:15-17` |
| Boot delays | 1000 ms, 2000 ms, 2000 ms | Splash screen, Wi-Fi info screen, update-check screen | `src/main.cpp:57`, `:61`, `:63` |
| WS reconnect | 10000 ms | `setReconnectInterval` | `src/main.cpp:593` |
| Restart command | 100 ms + 3000 ms | Delays before `ESP.restart()` | `src/main.cpp:357`, `:359` |
| Wi-Fi failure reset | 3000 ms | Delay before `ESP.reset()` | `src/main.cpp:534` |
| Wi-Fi reset screen | 1000 ms | Inverted-display notice | `src/main.cpp:617` |
| MAX30102 read timeout | 250 ms | `safeCheck(250)` inside `getIR`/`getRed` (library) | SparkFun `MAX30105.cpp:534`, `:544` |
| ADS1115 conversion wait | 9 ms | `ADS1115_CONVERSIONDELAY` (library) | Adafruit `Adafruit_ADS1015.h:38` |

FACT: none of these is a physiological hold time or detection window.

---

## 7. Thresholds, baselines, smoothing, triggers: what exists

| Asked for | Present? | Evidence |
|---|---|---|
| HR threshold / relative delta | **No.** HR is never computed; only raw IR and Red PPG are sent | FACT: `src/main.cpp:101-113`. There is no `checkForBeat` or BPM code in HEAD or history (`git log --all -p` search) |
| EDA threshold / delta | **No.** Raw ADS1115 counts are sent; no conductance conversion | FACT: `src/main.cpp:127` |
| Motion / flex threshold | **No** | FACT: `src/main.cpp:118-125` only loads values into the buffer |
| Baseline / calibration window | **No** | FACT: whole-file read |
| Averaging / EMA / filters | **Only** the 50-sample mean in `normalizePPG()`, which is **disabled** (call commented out at `src/main.cpp:79`; commit `bbb6894`, 2018-12-28, "Disable PPG normalization (seems to interfere with SpO2 algorithms)") | FACT: `src/main.cpp:162-192` |
| Hold times | **No** (the only duration rule is the 3000 ms button long press) | FACT |
| Trigger / actuation | **No.** The DRV2605 is configured (`src/main.cpp:22-26`) but `haptic.go()` is never called in HEAD. The only call was removed in `d2530d0` (2018-11-10, "remove haptic test code"); it used to buzz after every successful HTTP POST. The beeper only beeps at boot | FACT: `git show d2530d0` |

The only threshold-like constants in the firmware (FACT, `src/main.cpp:155-191`):

- `normalizePPG()` nudges the red LED amplitude by ±1 step when the IR mean differs from the red mean by more than
  ±8000 counts.
- The comment says "Each redPulseAmplitude step results in a change of about 8000".

**Where detection might have lived (FACT, checked):** the companion webapp. Its whole tree was listed through the GitHub
API on 2026-10-27, and it is a stock Laravel 5.7 skeleton plus `FirmwareController.php`. `routes/web.php:19` declares
`Route::resource('dp', 'WearableDataPointsController')`, but that controller does not exist in the tree, and the repo
has no WebSocket server, decoder or detection code. **Conclusion:** no public code implements Dormio Device Duo
sleep-onset detection. A private server may have existed; that is a gap (§12).

---

## 8. Algorithms in pseudo-code (everything the firmware does)

### 8.1 Multi-rate frame scheduler (`src/main.cpp:70-96`, `:101-153`)

```
loop forever:
  poll wifi-reset button
  webSocket.loop()                       # service the socket, dispatch events
  if not isConnected: continue           # no sampling, no buffering offline
  # --- full rate (nominal 50 Hz, paced by blocking sensor reads) ---
  ir  = ppg.getIR()                      # waits for a fresh FIFO record (<=250 ms)
  red = ppg.getRed()                     # waits for another fresh record
  frame.IR[n] = ir; frame.RED[n] = red
  frame.ECG[n] = ads.read(A1)            # ~9 ms blocking conversion
  n += 1
  # --- every 25th sample (2 Hz) ---
  if n % 25 == 0:
     frame.ACC[k]  = imu.accel(x,y,z)    # float3, sign lost for -1<v<0
     frame.GYR[k]  = imu.gyro(x,y,z)
     frame.GSR[k]  = ads.read(A0)        # raw counts
     k += 1
  # --- end of frame (250 samples, ~5 s) ---
  if n == 250:
     frame.temps  = (ppg.dieTemp, mlx.object, mlx.ambient)
     frame.epoch  = millis()/1000; frame.count = frameCounter++
     frame.mac, frame.vcc = ...
     frame.checksum = XOR(all bytes loaded)
     frame ^= 0x97 (bytewise)            # "encryption" (47^7^191)
     webSocket.sendBIN(frame, 2724)
     n = k = 0; checksum = 0
```

### 8.2 PPG LED normalisation, disabled (`src/main.cpp:162-192`)

```
every 50 samples:
  redMean = mean(redBuffer[0..49]); irMean = mean(irBuffer[0..49])
  if irMean > redMean + 8000: redAmp += 1
  if irMean < redMean - 8000: redAmp -= 1
  if redAmp changed: ppg.setPulseAmplitudeRed(redAmp); show on OLED
```

This is a bang-bang controller with a ±8000-count dead band; it is not a physiological algorithm. The author disabled it
because it "seems to interfere with SpO2 algorithms" (commit `bbb6894`). INFERENCE: those SpO2/HR algorithms were meant
to run server-side, and they are not in either public repo.

### 8.3 float3 packing (`src/main.cpp:201-222`)

```
c = trunc_toward_zero(v) as int16
if c < 0: c = (c XOR 0x7FFF) + 1        # sign-magnitude
write c little-endian (2 bytes); write uint8(trunc(100*|v - trunc(v)|))
# decode: s = (c & 0x8000) ? -1 : +1; v = s*((c & 0x7FFF) + frac/100)
# BUG: v in (-1,0) -> c == 0 -> decodes as +|v|
```

### 8.4 WebSocket command handler (`src/main.cpp:334-417`)

```
on CONNECTED:    send {"c":"sub","chan":"hb"}; send {"c":"sub","chan":"dormio"}; isConnected = true
on DISCONNECTED: isConnected = false
on TEXT len==1:  "1" -> restart | "2" -> reply "2" | "9" -> OTA check | else log
on BIN:          hexdump only
```

### 8.5 OTA (`src/main.cpp:446-499`)

```
GET fwUrlBase + "ver/" + mac
if 200 and int(body) > firmwareVersion:
   ESPhttpUpdate.update(updateHost, updatePort, "/bin/" + mac)   # reboots on success
```

---

## 9. Cross-reference: where Dormio's real numbers live (input for requirement D.10)

The Duo repo has no detection numbers, so here are the sources that do, quoted exactly. Full extraction belongs in the
paper and `dormio` analyses; these are the points needed to judge transfer.

**Haar Horowitz et al. 2020, *Consciousness and Cognition*.** <https://pmc.ncbi.nlm.nih.gov/articles/PMC7590944/>.
The raw HTML was fetched and converted to text on 2026-10-27. All quotes are FACT.

- **§2.2 "Dormio Sleep-Onset Detection Algorithm":**
  - "a flex sensor monitors the slow opening of the hand, with its parallel loss of muscle tone"
  - "muscle tone is tracked using a voltage divider composed of a resistor and a 4.5” Sparkfun flex sensor wrapped
    around the middle finger"
  - "EDA is monitored using a constant voltage source, a voltage divider and a low-pass filter to measure conductance
    between two electrodes placed on the bottom of the wrist"
  - HR uses "Adafruit’s Pulse Sensor Amped on their middle finger"
  - "Each of the three Dormio sensors is sampled at 100hz."
- **§2.2, baseline:** "These three measures are averaged over the first 120 seconds when the subject initially lies
  down, and predefined deviations from these mean values are interpreted as Dormio-defined sleep onset."
- **§2.2, thresholds:** "Heart Rate (BPM) deltas of > 5 BPM, Electrodermal Activity sensor deltas > 4 μSemen or
  flexor muscle sensor deltas > 8 KΩ were taken as indications of sleep onset. Awakenings were performed whenever one
  of these thresholds was passed." This is **OR** logic. The thresholds came from "pilot data from 5 participants".
- **§2.1, behavioural task:** "they are asked to close their hand as they descend into sleep … finger flexion begins
  to decrease as they forget or fail at this passive behavioral task."
- **§2.4 "Instructions", timings:**
  - "This timer instigated wakeups from 1:00 to 5:00 minutes after Dormio-detected sleep onset"
  - "This loop of events was repeated for 45 minutes"
  - In the awake control group: "Once 7 minutes had passed, approximating sleep onset time, a variable timer was
    triggered from 1 to 5 minutes later." This is a sensor-free timed-cue precedent inside the paper itself
    (relevant to requirement E.15).
- **§3.3 "Sleep Physiology", table:** HR "> 5 BPM", Flexion "> 8 KΩ", Electrodermal activity "> 5 μSemen".
  **Conflict:** §2.2 says EDA "> 4 μSemen" but the table says "> 5 μSemen".
- **§3.3, distributions (Figure 4 caption):**
  - "HR Mean Sleep: −2.89 ± 22.94. HR Mean Wake: 10.01 ± 18.7"
  - "Flex Mean Sleep: −6.74 ± 9.05. Flex Mean Wake: −3.42 ± 6.32"
  - "EDA Mean Sleep: −4.88 ± 13.44. EDA Mean Wake: 3.36 ± 8.64"
  - The body text gives slightly different values: flex Wake "+3.4 ± 6.3 KΩ" and EDA Sleep "−4.0 ± 13.4 μSemen". This
    is an internal inconsistency in the paper.
  - **INFERENCE:** the standard deviations are 2 to 8 times the mean differences, so each single-channel threshold is a
    weak separator. That supports requirement D.11 (combine signals) and D.14 (output a confidence score, not a
    boolean).
- §3.3: "Across all participants, sleep onset averaged 10.3 ± 6.4 (S.D.) minutes."

**`christinatchen/dormio` web app (`reference_repos/dormio/js/script_wearable.js`).** All FACT.

- **Defaults:** `"loops" : 3`, `"hypna-latency" : 3`, `"time-between-sleep" : 7`, `"calibration-time" : 3`,
  `"recording-time" : 30`, `"delta-eda" : 4`, `"delta-flex": 5`, `"delta-hr": 6` (`dormio/js/script_wearable.js:208-215`).
  - `hypna-latency` and `calibration-time` are in minutes and multiplied by 60 (`:484`, `:531-532`).
  - `recording-time` is used in seconds (`:485`, `:885`).
- **Detection rule** (`:671-760`):
  - It compares the mean of the last 100 flex/EDA entries ("~approx 10 seconds of data", `:676-685`) and the mean of
    the last 10 BPM values (`:689-696`) against the calibration means.
  - It uses `Math.abs` deltas, so a change in either direction counts (`:699-701`).
  - It fires if **any one** delta ≥ its threshold (`:709`), re-checking every 1000 ms (`:731-733`).
- **Data ingest:** flex, raw HR and EDA arrive as three little-endian uint32 values per BLE notification
  (`:89-91`). The UI threshold values are raw device units, not KΩ or μS.

**`tomasero/openSleep` (`openSleep/deprecated/mobile_app/openSleep/FlexAnalyzer.swift`).** All FACT.

- A flex state machine (OPEN, CLOSING, CLOSED, OPENING) with `openThresh 650` and `closedThresh 430` (raw ADC)
  (`:20-21`).
- Two quick flexes, each closed within `CloseTimeThresh 1.75` s and with the pair less than `maxTimeBetweenFlexes 5` s
  apart, are flagged as a **false positive**: the user signalling "I'm still awake" (`:25-26`, `:62-105`).

---

## 10. Transfer evaluation: Galaxy Watch8 (PPG HR + IBI, wrist EDA in μS, 25 Hz accelerometer)

### 10.1 Channel mapping

| Duo channel | OpenTDI equivalent (Samsung Health Sensor SDK) | Verdict |
|---|---|---|
| Raw PPG IR/Red, nominal 50 Hz | HR plus an IBI list from `HEART_RATE_CONTINUOUS` (CLAUDE.md §2), with a status flag | **Replace.** Samsung does the beat detection. Nothing from the Duo PPG path (LED tuning, raw streaming) transfers |
| GSR, raw counts at 2 Hz, placement unknown | `EDA_CONTINUOUS` skin conductance in μS with a status flag; owner requirement C.9 says it streams with the display off on Watch8 and later | **Replace.** Duo thresholds don't exist, and Dormio's μS thresholds were set on a different wrist site with different electrodes (§10.5) |
| Accel/gyro snapshots at 2 Hz (sign-corrupted) | `ACCELEROMETER_CONTINUOUS` at 25 Hz (owner requirement C.7). Units and scale must be checked on-device | **Replace and improve:** process on the watch at 25 Hz (§10.6-10.7) |
| ECG/EMG at 50 Hz | None used | Drop |
| Skin / ambient temperature at 0.2 Hz | Not in OpenTDI's sensor list | Drop. Possible later logging only (INFERENCE: skin temperature rises at sleep onset, but no source was fetched here) |
| Haptic (DRV2605, never used) | Watch vibration motor | Optional gentle cue. CLAUDE.md §3 standalone fallback is deferred (requirement G.21) |
| Wi-Fi WebSocket, 5 s binary frames | Wear Data Layer: `MessageClient` for triggers and commands, `ChannelClient` or batched messages for 1 Hz telemetry (requirement F.17) | Replace |
| HTTP OTA | Play / ADB install | Drop |

### 10.2 What transfers, as design ideas (INFERENCE)

1. **Multi-rate pipeline with summaries.** The Duo sends high-rate PPG, low-rate IMU/GSR and once-per-frame metadata.
   OpenTDI should do the same shape, one level higher: process 25 Hz accel on the watch and send per-second summaries
   plus event flags (requirement C.8).
2. **A frame counter plus a session-relative timestamp in every packet** (`src/main.cpp:139-140`). Keep this, but put a
   real monotonic timestamp on each 1 Hz record, not one whole-second epoch per frame, so the phone can find gaps and
   the replay harness can line up streams.
3. **Device identity in every packet** (MAC, `src/main.cpp:141`). With the Data Layer, the node ID already does this.
4. **Heartbeat and command channel** (`"2"` → `"2"`, `src/main.cpp:363-367`). Map it to a `MessageClient` ping on
   `/opentdi/state_command`, so the phone can tell "watch alive, no trigger" apart from a dead link mid-nap.
5. **Reconnect with a fixed back-off** (10 s, `src/main.cpp:593`). Use a similar bounded retry for Data Layer node
   loss, and surface it on the dashboard.
6. **Decision logic lives off the sensor.** In the Duo, the firmware was deliberately dumb and the analysis was meant
   for a server. OpenTDI's `HypnagogiaDetectionEngine` is pure Kotlin in `:shared`, so the same engine can run on the
   watch, the phone or in unit tests with replay (requirement D.13).

### 10.3 What must not be copied (defects found, all FACT with code cites)

| Defect | Cite | OpenTDI rule |
|---|---|---|
| No sampling or buffering while disconnected; data silently lost | `src/main.cpp:75` | Always log to CSV on the watch, and mark gaps when the link is down (requirement D.13) |
| `isConnected = true` assignment inside the send condition | `src/main.cpp:323` | Unit-test the link-state logic |
| float3 loses the sign for −1 < v < 0 | `src/main.cpp:201-222` | Keep the SDK's numeric types; never hand-pack floats |
| IR and Red taken from different FIFO samples; no per-sample timing | `src/main.cpp:103-104`; `MAX30105.cpp:704-717` | Timestamp each SDK callback batch |
| IMU sampled as 2 Hz snapshots with no anti-alias decimation | `src/main.cpp:82`, `:118-125` | Use every 25 Hz sample on-watch |
| XOR "encryption" collapses to one byte (0x97) | `src/main.cpp:422-428`; `include/env.h.example:14` | Data Layer traffic is already scoped to the paired node. Add no fake crypto |
| Unsigned HTTP OTA, plain `ws://` with a token in the URL | `src/main.cpp:446-499`, `:591`; `include/env.h.example:1-3` | Not applicable (no OTA) |
| Raw ADC counts for EDA with no unit conversion | `src/main.cpp:127` | Use the SDK's μS value and log its status flag |

### 10.4 Heart rate and IBI

- **FACT:** the Duo computes nothing (§7). Dormio's rule is an absolute **"> 5 BPM"** delta from a 120 s mean (paper
  §2.2), applied in either direction in the web app (`dormio/js/script_wearable.js:701`, `:709`).
- **INFERENCE: what transfers is the idea of a delta against an in-session baseline mean.** The Samsung HR value is
  already in BPM, so it can be compared with Dormio's 5 BPM directly. For reference, 5 BPM is 8.3 % of a 60 BPM
  baseline and 6.7 % of 75 BPM, so CLAUDE.md's 7 % falls inside that range by arithmetic, not from a source.
  - Use a **one-directional drop** (HR falling) as the sleep signal.
  - Treat a rise as an arousal veto, not as a trigger. Dormio's `Math.abs` counts a rise as "sleep", which is
    physiologically backwards for onset (INFERENCE).
  - Report the drop both as BPM and as a z-score against the baseline σ, and feed the confidence score (requirement
    D.14).
- **IBI / RMSSD:** no source in the Duo, paper or web app uses IBI or RMSSD, so CLAUDE.md's "15 % RMSSD" is GUESSED.
  - Wrist PPG IBIs need artifact rejection before RMSSD: drop IBIs outside physiological bounds and successive
    differences that are too large. Only use samples whose HR status flag is good.
  - Compute RMSSD over ≥ 30 s of clean IBIs. A shorter window is too noisy (INFERENCE, GUESSED window).
- **Smoothing:** Dormio averages HR over about 10 s (10 BPM values, `dormio/js/script_wearable.js:689-696`).
  CLAUDE.md's 15 s window is GUESSED; make it a config value.

### 10.5 EDA (wrist skin conductance in μS)

FACTS that matter:

- **The original Dormio EDA was already wrist EDA:** "two electrodes placed on the bottom of the wrist" (paper §2.2).
  The "> 4 μSemen" (or "> 5 μSemen") threshold is therefore a ventral-wrist number from a custom constant-voltage
  divider, not a finger number.
- **The literature on wrist versus palm/finger EDA conflicts:**
  - In sleep at home, "EDA levels were higher and the skin conductance peaks were larger and more frequent when
    measured on the wrist than when measured on the palm" (Sano, Picard & Stickgold 2014, *Int J Psychophysiol*
    94(3):382-389, abstract; <https://www.media.mit.edu/publications/quantitative-analysis-of-wrist-electrodermal-activity-during-sleep-1/>,
    PMC4335672).
  - The same abstract: "more than 80% of the EDA peaks occurred in non-REM sleep … specifically during slow-wave sleep
    (SWS) and non-REM stage 2 sleep (NREM2)."
  - In an awake lab study of 112 adults (van der Mee et al. 2021, *Int J Psychophysiol* 168:52-64), wrist absolute SCL
    and ns.SCR frequency were lower than at the palm, with modest within-subject correlations (r = 0.31 for SCL,
    r = 0.42 for ns.SCR). This comes from the abstract as indexed at
    <https://vufind.lboro.ac.uk/PrimoRecord/cdi_proquest_miscellaneous_2563420626>, seen only through a search summary;
    I did not read the full text.
  - A 2022 review says "contradictory results were reported by the researchers for this measurement site" and
    "Typically, EDA electrodes are placed on the fingers" (Hossain et al. 2022, *Sensors* 22(9):3177,
    <https://pmc.ncbi.nlm.nih.gov/articles/PMC9104297>).

INFERENCES for OpenTDI:

1. **The owner's premise needs care.** "Wrist EDA is far lower amplitude and noisier than finger EDA" holds for awake,
   lab-type measurement (van der Mee 2021), but not universally during sleep (Sano 2014).
   - A watch's electrodes sit on the **dorsal** wrist, under the case. That is a different site from Dormio's
     "bottom of the wrist", with different electrodes and contact pressure (INFERENCE: not verified for SM-L500).
   - So **no absolute μS threshold transfers.** Dormio's 4-5 μS delta could be bigger than the entire tonic range a
     watch reports for some users.
2. **Express EDA relative to the session baseline.** Use the percentage change from the baseline median and a z-score
   against the baseline robust σ (MAD), never an absolute μS delta.
3. **EDA is a bonus and a veto, never a trigger** (requirement D.11).
   - A falling or flat tonic level is true at rest almost constantly, so it carries little information.
   - The useful signal is **phasic**: an SCR (a fast rise above a minimum amplitude) during a countdown suggests
     arousal and should lower confidence or abort.
   - Sano 2014 places most sleep EDA peaks in NREM2 and SWS, so a burst of peaks during a long nap may mean the user
     has gone past N1. Log it as a "too deep" flag; do not treat it as onset.
4. **Handle data quality explicitly.**
   - Use the SDK `STATUS` flag, and treat zero, saturated or flatlined values as missing.
   - Add a contact-loss rule (for example, a conductance step to near zero).
   - Never interpolate across gaps when computing slopes.
5. **Verify on hardware first** (requirement C.9). Record a 10-minute screen-off session and check the rate, the
   dropout pattern and the resolution of the μS values before choosing any EDA parameter.

### 10.6 Flex sensor → "limp wrist" from a 25 Hz accelerometer

**What the flex sensor measured (FACT, paper §2.1-2.2).** The user closes the hand when lying down; as sleep approaches,
"finger flexion begins to decrease" and "a flex sensor monitors the slow opening of the hand, with its parallel loss of
muscle tone". It is an active behavioural task that **fails** at sleep onset, read as a resistance change in KΩ.
openSleep adds a deliberate double-flex "still awake" gesture (§9).

**Why a wrist accelerometer is not a flex sensor (INFERENCE):**

- Finger flexion barely moves the wrist. Opening a relaxed fist changes the forearm's orientation by a few degrees at
  most, and only if the forearm is not supported. An accelerometer at rest sees mostly gravity, so it can detect:
  - a change in orientation (gravity-vector tilt);
  - the presence or absence of small dynamic motion;
  - discrete events (jerks, drops).
- It cannot see tone in a fully supported, motionless limb.
- So "limp wrist" has to be built from **orientation drift plus loss of micro-motion**, or the behavioural task has to
  change into one whose failure moves the watch a lot.

**Three ways to approximate it, from most to least reliable (INFERENCE; every number is GUESSED):**

1. **Arm-drop task (recommended as an opt-in protocol).**
   - The user lies supine with the elbow on the mattress and the forearm raised towards vertical (watch hand up).
   - Losing tone makes the forearm fall: the gravity vector rotates by tens of degrees within about 0.5 to 2 s, often
     followed by a settling bounce. That is unmistakable on an accelerometer, so the signal is large.
   - It is the direct analogue of Dormio's "close your hand" task, and so it is also a **protocol change**. It needs
     the owner's approval and should be a selectable task (`LimpTask.ARM_RAISED`). The default stays a passive
     posture.
2. **Passive posture drift.** With the arm resting, track the angle between the current low-passed gravity vector and a
   reference captured after the baseline. A slow, monotonic drift of a few degrees over 10 to 60 s with no dynamic
   energy (no voluntary movement), followed by stillness, is a weak "relaxing" cue. Use it only as a confidence bonus.
3. **Loss of micro-motion.** The per-second dynamic energy (variance of |a| after removing gravity) falls towards the
   sensor noise floor as tone and fidgeting stop. This is the **stillness score**. It is necessary but not sufficient:
   requirement D.11 says stillness **plus** an HR/IBI drop.
   - Physiological tremor (around 8 to 12 Hz) lies close to the 12.5 Hz Nyquist limit of a 25 Hz stream.
   - Its amplitude in a supported arm is probably below the watch's noise floor.
   - Do not rely on it until a recording shows it is visible.

**Pseudo-code, run on the watch at 25 Hz** (requirement C.7). Every constant is **GUESSED** and must be a config value.

```
# per sample (25 Hz), a = (ax, ay, az) in g after SDK unit conversion (verify scale on device)
g_lp   = g_lp + ALPHA_G * (a - g_lp)          # gravity estimate, ALPHA_G ~ 0.04 (tau ~1 s at 25 Hz)  GUESSED
dyn    = |a - g_lp|                            # dynamic (non-gravity) magnitude
jerkHP = |a[t] - a[t-1]| * 25                  # first difference, g/s

# per second (25 samples)
dynRms        = rms(dyn over last 25)
stillnessScore= clamp(1 - (dynRms - NOISE_FLOOR) / (MOVE_RMS - NOISE_FLOOR), 0, 1)
                # NOISE_FLOOR measured during baseline (e.g. 95th pct of dynRms), MOVE_RMS ~ 0.05 g  GUESSED
tiltDeg       = angle(g_lp, g_ref)             # g_ref = mean g_lp over the last still baseline minute
limpEvent:
  ARM_RAISED task:  tiltDeg rises by >= DROP_DEG (40 deg) within <= 2 s and the new posture is then still >= 5 s  GUESSED
  passive posture:  tiltDeg drifts monotonically >= DRIFT_DEG (3 deg) over 10-60 s while stillnessScore >= 0.8    GUESSED
  after a limpEvent: g_ref = new settled posture (so drift is relative to the latest rest)
```

### 10.7 Hypnic-jerk detector, on the watch at 25 Hz (INFERENCE; numbers GUESSED)

The Duo's 2 Hz snapshots could not resolve a sub-second jerk (§3). At 25 Hz, a 0.5 s event spans about 12 samples.

```
state: stillRun = consecutive seconds with stillnessScore >= 0.8
on each sample:
  if dyn > JERK_G (0.3 g)  and  burst not open and stillRun >= PRE_STILL_S (10 s):
       open burst, burstStart = t
  if burst open and dyn < JERK_G for >= 3 samples:
       burstLen = t - burstStart
       if burstLen <= 0.5 s: wait POST_STILL_S (3 s); if stillnessScore stays >= 0.8 -> jerkEvent = true
       else: voluntary movement -> reset stillRun, no jerk
```

INFERENCE: a hypnic jerk is a commonly described sleep-onset phenomenon, but none of the three reference repos or the
Dormio paper uses one. In OpenTDI it is a **bonus** signal only, and its weight is GUESSED. A jerk followed by
arousal (HR up, an SCR) should abort rather than confirm.

### 10.8 Packet fields this implies (requirement C.8)

The proposal is to add to the 1 Hz `BiosignalPacket`, keeping `accelRms`:

| Field | Type | Meaning |
|---|---|---|
| `seq` | Int | Monotonic packet counter (Duo `frameCounter` idea, `src/main.cpp:140`) |
| `stillnessScore` | Float 0..1 | §10.6 |
| `tiltDeg` | Float | Angle from the current rest reference |
| `jerkEvent` | Boolean | Set in the second the jerk is confirmed |
| `limpEvent` | Boolean | Set in the second the drop or drift is confirmed |
| `edaScrCount` | Int | Phasic peaks this second, for the arousal veto (§10.5) |

INFERENCE: this is roughly a 50 to 100 B JSON or protobuf record per second, compared with the Duo's 545 B/s of raw
data, and it suits `ChannelClient` or batched messages (requirement F.17). The raw 25 Hz event flags and 1 Hz values
also go to CSV on the watch (requirement D.13).

### 10.9 What must be measured before any parameter is trusted

1. Accelerometer scale, units and noise floor at rest on the SM-L500. This sets `NOISE_FLOOR` and `JERK_G`.
2. Tilt-angle noise of `g_lp` for a still, supported arm. This sets `DRIFT_DEG`.
3. Arm-drop recordings (n ≥ 10 deliberate drops and ≥ 3 real naps). This sets `DROP_DEG` and the time window.
4. EDA at screen-off for 10 min or more: rate, dropouts, μS range on the dorsal wrist (requirement C.9).
5. Replay those recordings through `HypnagogiaDetectionEngine` in unit tests (requirement D.13) and tune against
   subjective "asleep / half / awake" reports, as Dormio did with pilot data from 5 participants (paper §2.2).

---

## 11. Audit of CLAUDE.md §4 numbers against this repo (requirement D.10)

| CLAUDE.md §4 value | In the Duo repo? | Source found elsewhere during this analysis | Status |
|---|---|---|---|
| 180 s baseline | No | Paper: 120 s (§2.2). Dormio web app default `calibration-time` 3 min = 180 s (`dormio/js/script_wearable.js:211`, `:531-532`) | **SOURCED** (web-app default). The paper used 120 s. Make it configurable (requirement D.12) |
| 7 % HR drop (4-12 %) | No | Paper: absolute "> 5 BPM" (§2.2). Web app default `delta-hr` 6 (`:215`) | **GUESSED** as a percentage. Dormio used absolute BPM |
| 15-s HR moving average | No | Web app: mean of the last 10 BPM values, "~approx 10 seconds" (`:689-696`) | **GUESSED** |
| RMSSD +15 % | No | None | **GUESSED** |
| 60 s accel SD window | No | None (Dormio used a finger flex sensor, > 8 KΩ, §2.2) | **GUESSED** |
| Hypnic jerk spike < 0.5 s | No | None | **GUESSED** |
| EDA 30 s slope ≤ 0, \|ΔEDA\| < 1.5σ | No | Paper: absolute delta "> 4 μSemen" (§2.2) / "> 5 μSemen" (table §3.3); web app `delta-eda` 4 (`:213`) | **GUESSED.** It also breaks requirement D.11 (it is true at rest) |
| 20 s hold (10 s if all three) | No | Paper: fire "whenever one of these thresholds was passed" (no hold). Web app re-checks every 1 s on about 10 s means (`:676-733`) | **GUESSED** |
| Composite AND logic | No | Paper and web app use **OR** across the three channels (§2.2; `:709`) | **GUESSED** (deliberately stricter, per requirement D.11) |
| 180 s N1 countdown | No | Paper: wake-ups "from 1:00 to 5:00 minutes after Dormio-detected sleep onset" (§2.4). Web app `hypna-latency` default 3 min (`:209`, `:484`) | **SOURCED** (web-app default, within the paper's 1-5 min range) |

---

## 12. Gaps, conflicts and things I could not verify

1. **No public detection code for the Duo exists.** Neither public repo has it, and `WearableDataPointsController` is
   referenced at Webapp `routes/web.php:19` but missing. Any server-side algorithm was private or never written.
2. **Body placement of the Duo sensors is undocumented** (README "wip"), so it is unknown whether its GSR was finger or
   wrist.
3. **The MAX30102 sample-rate definition is unconfirmed.** The analog.com datasheet fetch timed out, so the claim that
   the loop runs at about 50 Hz stays INFERENCE. It is consistent with `include/main.h:105` and the library code.
4. **The Dormio paper contradicts itself:**
   - the EDA threshold is "> 4 μSemen" in §2.2 but "> 5 μSemen" in the §3.3 table;
   - flex Wake is −3.42 KΩ in the Figure 4 caption but +3.4 KΩ in the text;
   - EDA Sleep is −4.88 in the caption but −4.0 in the text;
   - one EDA value is given in "%".
5. **van der Mee 2021 was seen only through a search-index abstract**, not the full text.
6. **The Samsung SDK accelerometer units, scale and true rate, and the EDA rate, resolution and screen-off behaviour on
   SM-L500,** are taken from the owner requirements (C.7, C.9) and CLAUDE.md, not verified here.
7. **Every limp-wrist, stillness and jerk parameter in §10.6-10.7 is GUESSED**, as is the arm-drop protocol. That
   protocol is a design proposal that changes the user task, and the owner must approve it.
8. **Licensing:** "MIT" appears only as a README line, with no license text. OpenTDI copies no code, so there is no
   practical impact.
