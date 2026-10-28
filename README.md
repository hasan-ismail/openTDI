# OpenTDI

**A lucid-dreaming companion for your Galaxy Watch and Android phone.**

As you drift off to sleep, there is a short, dreamy in-between stage where thoughts turn into images. OpenTDI uses
that moment. Your phone quietly reminds you, *"You are dreaming. You are in control."* It gently asks what you notice,
then lets you drift back, and repeats this a few times. The aim is simple: to help you **realise you are dreaming**,
and leave the rest of the dream up to you.

OpenTDI is free and open-source. It builds on **Dormio**, a research project from the MIT Media Lab that played spoken
cues to people as they fell asleep ([Horowitz et al., 2020](https://pmc.ncbi.nlm.nih.gov/articles/PMC7590944/)), and
on its sensor-free version that uses timed cues only
([Bellaiche, Haar Horowitz et al., 2024](https://doi.org/10.3389/frsle.2024.1258345)).

> **Please read: this is not a medical device.** OpenTDI is wellness and research software. It does not diagnose,
> treat, cure or prevent any illness, and it is not a sleep tracker you can rely on: its sleep estimates use untested
> settings and may be wrong. Samsung's sensor data is "for fitness and wellness information only, not for the
> diagnosis or treatment of any medical condition." **Talk to a doctor first** if you have ever had a psychotic
> disorder, narcolepsy, frequent sleep paralysis, sleepwalking or another sleep disorder. OpenTDI is not made by,
> or endorsed by, MIT or Samsung.

## How a session goes

1. **Open OpenTDI on the watch** (it waits for the phone), then **tap Start session** on the phone: the watch starts
   recording and you hear the first reminder: *"You are dreaming. You are in control."* The watch records the whole
   night; stop it in the morning from the phone or the watch.
2. **About 10–15 minutes later**, around when most people start drifting off, the reminder plays again, softly.
3. **Three minutes after that**, a gentle voice says *"You're falling asleep. You are dreaming now. What do you
   notice?"* Say whatever is on your mind, or nothing at all.
4. **A minute later:** *"You are dreaming. You are in control. You can fall back asleep now."*
5. **This repeats up to 4 times**, about 7 minutes apart. The session then ends, with a soft chime if you want one.

The phone speaks slowly, one phrase at a time, and every voice fades in over two seconds so it does not startle you.
You can change the words, the voice, the speaking speed and all the timings. If your phone is in another room,
turn on **Play cues on the watch** and the watch's speaker plays them.

While you sleep, the **watch records your body's signals**: heart rate and heartbeat timing, skin response (EDA),
movement, skin temperature, blood-oxygen spot checks, and the raw pulse signal, which can later show your breathing.
The phone shows these live.

## What you need

- A **Samsung Galaxy Watch** with Wear OS (Watch4 and later; only the Watch8 Classic has been tested) and an
  **Android phone** with Android 13 or newer, on the same Wi-Fi.
- That's all for the cues, the alarms and the dream report: they use the watch's heart rate and movement.
- **Optional: Samsung's sensor kit**, for skin response, skin temperature, blood oxygen and the raw pulse signal. OpenTDI
  contains no Samsung code: you download the kit yourself from Samsung and add it in the phone app, which passes it to
  your watch. Samsung's sensors then also need the watch's developer mode, which Samsung explains on its
  [developer-mode page](https://developer.samsung.com/health/sensor/guide/developer-mode.html).

**Installing:** download `OpenTDI-phone.apk` from the [Releases](../../releases) page and open it on the phone. Its set-up
guide walks you through the rest, including putting OpenTDI on the watch over Wi-Fi.

## What works today

| | Works now | Coming next |
|---|---|---|
| **Cues** | Timed cues (MIT's method) that wait until the watch shows you lying still; from the phone or the watch speaker; lucid-dream wording by default; your own words, or your own recorded voice; a natural voice that runs on the phone | |
| **Watch** | Records heart rate, heartbeat timing, skin response, movement, skin temperature, blood oxygen and the raw pulse signal; shows it live on the phone | Breathing rate worked out from the pulse signal |
| **Alarms** | On the watch: a wake-up alarm (five tones, a spoken one with your name, snooze) that logs how long you take to wake up, and a WBTB alarm that guides you through staying up and buzzes when to sleep again | |
| **Late-night cues** | After WBTB, a soft trained sound while you lie still; an experiment mode that secretly skips some nights so you can see whether it helps you | |
| **Your answers** | A dream report on the watch after each alarm: record the dream, then a few one-tap questions | Turning the recordings into text, and a dream journal on the phone |
| **Safety** | Volume cap set by you; warnings when the media volume is off or Do Not Disturb would mute the cues | |

Good to know: MIT's own studies used a calm voice and short reminders, like OpenTDI. In their at-home study, timed
cues alone got the chosen theme into at least one dream report for 73 of 80 people. Whether this setup helps you
become **lucid** has not been tested yet. Your own nights are the first test, so tell us how it goes.

## Your data stays with you

- Everything is stored **on your own phone and watch**, in the app's private storage. Nothing is uploaded anywhere.
- The phone app uses the network only to reach your watch on your own Wi-Fi when it installs the watch app; it never
  contacts the internet. The watch app only uses the network for Samsung's own sensor service.
- Each session is saved as a set of simple spreadsheet (CSV) files. You can look through your sessions and delete any
  of them in the app (**Sessions**).

## For developers

### Building

You need JDK 17 or newer and the Android SDK (platform 37, build-tools 36). Tell Gradle where the SDK is with
`sdk.dir` in a `local.properties` file, or with `ANDROID_HOME`. The Gradle wrapper fetches everything else.

```bash
./gradlew :shared:test :wear:testDebugUnitTest :mobile:testDebugUnitTest :wear:assembleDebug :mobile:assembleDebug
```

On Windows use Git Bash, or `gradlew.bat`. The apps land in `wear/build/outputs/apk/debug/` and
`mobile/build/outputs/apk/debug/`. Step-by-step setup, installing, permissions and copying session logs off the
devices are in [docs/SETUP.md](docs/SETUP.md).

**Samsung's sensor kit.** The watch uses Samsung's Health Sensor SDK, which you download yourself from Samsung (free
account; see [wear/libs/README.md](wear/libs/README.md)). It is never stored in this repository. Without it, the watch
app still builds and falls back to basic heart-rate and motion sensors. To try that fallback on purpose:

```bash
./gradlew :wear:assembleDebug -Popentdi.forceSamsungStub=true
```

### Putting OpenTDI on your own phone and watch

You install with `adb` (in the Android SDK's platform-tools) over Wi-Fi, so your computer and both devices should be
on the same network. Menu names are from a Samsung phone and a Galaxy Watch8 Classic and may differ slightly.

**Phone**

1. Turn on developer options: **Settings > About phone > Software information**, tap **Build number** 7 times.
2. **Settings > Developer options > Wireless debugging > On.** (A USB cable with USB debugging works too.)
3. Tap **Pair device with pairing code**, then on the computer: `adb pair <ip>:<port> <code>` using what the phone shows.
4. Connect with the address on the main Wireless debugging screen: `adb connect <ip>:<port>`.

**Watch**

1. Connect the watch to **Wi-Fi** (Settings > Connections > Wi-Fi).
2. Turn on developer options: **Settings > About watch > Software information**, tap **Software version** about 5–7
   times.
3. **Settings > Developer options > ADB debugging > On**, then **Wireless debugging > On > Pair new device**, and pair
   with `adb pair <ip>:<port> <code>` using exactly the address the watch shows. Avoid repeated failed attempts: one
   once restarted the watch's interface.
4. Connect, and later reconnect without pairing again: `adb mdns services` lists the current address, then
   `adb connect <ip>:<port>`. The watch drops the connection when its Wi-Fi sleeps, and the port changes each time.

**Turn on Samsung's sensor developer mode (on the watch)**

Samsung's sensors only talk to apps Samsung has approved, or to any app while **developer mode** is on. Without it the
watch says "Samsung blocked sensor access (SDK_POLICY_ERROR)".

1. **Settings > Apps > Health Platform** (newer watches call it **Health Sensor Service**).
2. Quickly tap the **title** at the top, "Health Platform", about 10 times. Tap the title, not the version number.
3. Tap the **Developer mode** row that appears below the version, accept Samsung's license, and switch it **On**.
4. Keep the watch online.

The watch needs Health Platform / Health Sensor Service **1.6.5 or newer**. Samsung's own
[developer-mode page](https://developer.samsung.com/health/sensor/guide/developer-mode.html) is the authority. Samsung
asks that this guide is not given to ordinary app users: it is a developer step for people building OpenTDI themselves.

### Where things are

| Folder | What is inside |
|---|---|
| `shared/` | Code both apps share: data formats, signal processing, the cue timetable, with tests |
| `wear/` | The watch app (`wear/libs/` is where Samsung's sensor kit goes) |
| `mobile/` | The phone app |
| `samsung-health-stub/` | A stand-in for Samsung's kit, so the project builds without it |
| `docs/` | The engineering spec, the research it is based on, and the setup guide |

- [docs/ARCHITECTURE_AND_THRESHOLDS.md](docs/ARCHITECTURE_AND_THRESHOLDS.md): the full engineering spec. Every
  threshold and timing is labelled SOURCED (from the MIT papers or code) or GUESSED (an untested default).
- [docs/references/](docs/references/): research notes, including the first on-device findings
  ([device_audit.md](docs/references/device_audit.md)).
- [CLAUDE.md](CLAUDE.md): the original project brief; where it and the spec disagree, the spec wins.

## License

OpenTDI is free software under the **GNU General Public License, version 3** ([LICENSE](LICENSE)). In short: you may
use, study, change and share it, and anything you share that is built from it must stay open under the same license.

[NOTICE](NOTICE) adds one exception so OpenTDI can work with Samsung's sensor kit. The kit is Samsung's own property
under Samsung's license, which each user accepts when downloading it from Samsung; it is never part of this
repository or of OpenTDI's apps. Read Samsung's license before you use the kit. (This is not legal advice.)

## Thanks

OpenTDI stands on the Dormio research of Adam Haar Horowitz and colleagues in the Fluid Interfaces group at the MIT
Media Lab. The public [openSleep](https://github.com/tomasero/openSleep), [dormio](https://github.com/christinatchen/dormio)
and [Dormio Device Duo Wearable](https://github.com/ThingEngineer/Dormio-Device-Duo-Wearable) projects were studied as
references; OpenTDI copies no code, cue wording or audio from them. OpenTDI is not affiliated with MIT or Samsung.
