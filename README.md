# OpenTDI — Open Targeted Dream Incubation

**A lucid-dreaming companion for your Galaxy Watch and Android phone.** TDI stands for **Targeted Dream Incubation**:
guiding what a dream is about with a soft voice, on a timer, as you fall asleep.

As you drift off to sleep, there is a short, dreamy in-between stage where thoughts turn into images. OpenTDI uses
that moment. A soft voice from your phone or watch says, *"You are safe in bed. If you hear this voice in a dream,
you'll know it's only a dream."* It gently asks what was going through your mind, then lets you drift back, and
repeats this a few times. The aim is simple: to help you **realise when you are dreaming**, and leave the rest of the
dream up to you.

OpenTDI is free and open-source. It builds on **Dormio**, a research project from the MIT Media Lab that spoke to
people as they fell asleep ([Horowitz et al., 2020](https://pmc.ncbi.nlm.nih.gov/articles/PMC7590944/)), and
on its sensor-free version that speaks on a timer only
([Bellaiche, Haar Horowitz et al., 2024](https://doi.org/10.3389/frsle.2024.1258345)).

> **Please read: this is not a medical device.** OpenTDI is wellness and research software. It does not diagnose,
> treat, cure or prevent any illness, and it is not a sleep tracker you can rely on: its sleep estimates use untested
> settings and may be wrong. Its sensor readings are for general wellness information only. **Talk to a doctor first**
> if you have ever had a psychotic or dissociative disorder, narcolepsy, frequent sleep paralysis, hallucinations when
> falling asleep or waking up, sleepwalking or another sleep disorder. If everyday life ever starts to feel unreal or
> dreamlike, stop using OpenTDI and talk to a doctor. OpenTDI is not made by, or endorsed by, MIT or Samsung.

## How a night goes

1. **Tap Start the night** on the phone. It opens OpenTDI on the watch (if the watch doesn't open it, open it there
   yourself and the night still starts by itself). If anything needs
   fixing first, such as getting the voice ready, a popup lists just that, each with a button to fix it. The watch
   starts tracking your sleep and you hear the voice: *"You are safe in bed. If you hear this voice in a dream,
   you'll know it's only a dream."*
2. **About 10–15 minutes later**, around when most people start drifting off, the voice says it again, softly. If you
   are still moving around, it waits until the watch shows you lying still.
3. **Three minutes after that**, a question wakes you lightly: *"You were falling asleep. What was going through your
   mind? Was it a dream?"* Say whatever is on your mind, or nothing at all. Nothing is recorded.
4. **A minute later:** *"You can fall back asleep now. When you dream, you'll know it's only a dream."*
5. **This round repeats up to 4 times**, about 7 minutes apart. The voice then ends, with a soft goodnight chime if you
   want one. The watch keeps tracking your sleep and runs your alarms all night.
6. **In the morning** the wake-up alarm rings on the watch (it is on unless you turn it off). Tap **I'm awake** and the
   watch asks the dream questions: record your dream, then a few one-tap questions. Tap **Save** and the night ends by
   itself. Remember a dream later? Tap **Save my dream** on the watch, up to 14 hours after the night,
   and it is saved with that night.

**The early wake-up** (off unless you turn it on in Settings) wakes you in the middle of the night, keeps you up for a
short while, then buzzes when to go back to bed. While the watch tracks your sleep, it shows two buttons for it:

- **Skip the early wake-up** turns it off for tonight only (it asks first). Your wake-up alarm still rings, and the
  watch keeps tracking your sleep.
- **I woke up**, then **Do my early wake-up now** (from 4½ hours into the night) starts it straight away, without
  ringing, if you are already awake. **I woke up** also offers **Save my dream**: say the dream, and the night goes on.

In Settings › More, **Time it from when you fall asleep** makes the early wake-up ring a set time after the watch sees
you fall asleep: 6 hours unless you change it (4½ to 7 hours). The time you set is then called **No later than**: it
never rings after that, and if the watch can't tell when you fell asleep, it rings at that time. This needs Samsung's
sensor kit (see "What you need").

**The wake-up alarm has a backup.** The watch also sets it in its own alarm clock, so it still rings if tracking stops,
the app closes, the watch restarts or the app updates during the night, and it still rings only once. If it can't
start ringing, the watch tries again a minute later, and if that fails too, it shows a **Good morning** alarm
notification instead of staying silent. If tracking stopped, the watch's main screen shows when the alarm will ring,
with a **Turn it off for this morning** button. Turning the wake-up alarm off on the phone, or ending the night, turns
the backup off too. The backup is for the wake-up alarm only, not the early wake-up.

**The phone checks the watch app.** If the watch runs a different version of OpenTDI from the phone, Start's popup
says **The watch app is out of date**, with an **Update the watch app** button that opens Settings › More › **Install
the watch app**. You can still start the night, but your sleep tracking and alarms might not work. The warning is hidden
while the watch is tracking your sleep, because updating would end that night's tracking and alarms.

To try the voice in about 5 minutes, tap **Quick test (5 min)** in Settings › More.

The voice speaks slowly, one phrase at a time, and fades in over two seconds so it does not startle you. It never
tells you that you are dreaming right now; it only says how to recognise a dream if you hear it in one. You can change
the words (**What the voice says**), and, under **More**, the voice, the speaking speed and all the timings. If your
phone is in another room, turn on **Play the voice on the watch** in Settings, and the watch plays it.

While you sleep, the **watch tracks your body's signals**: heart rate and heartbeat timing, skin response (EDA),
movement, skin temperature, blood-oxygen spot checks, and the raw pulse signal, which can later show your breathing.
The phone shows these live.

## What you need

- A **Samsung Galaxy Watch** with Wear OS (Watch4 and later; only the Watch8 Classic has been tested) and an
  **Android phone** with Android 13 or newer, on the same Wi-Fi.
- That's all for the voice, the alarms and the dream questions: they use the watch's heart rate and movement.
- **Optional: Samsung's sensor kit**, for skin response, skin temperature, blood oxygen and the raw pulse signal. OpenTDI
  contains no Samsung code: you download the kit yourself from Samsung, under Samsung's own terms, and add it in the
  phone app, which passes it to your watch. Samsung's sensors then also need the watch's developer mode, which Samsung
  explains on its [developer-mode page](https://developer.samsung.com/health/sensor/guide/developer-mode.html).

## Download

OpenTDI is published as **source code only**: there are no ready-made app files to download. The apps combine
third-party GPL code (the espeak-ng speech engine inside sherpa-onnx, and spake2-java) with Google Play services, and
the GPL does not allow distributing that combination as one app. Building it for your own devices is fine: follow
[docs/SETUP.md](docs/SETUP.md).

**Builds:** GitHub Actions tests and builds every push to `main` and every pull request
([ci.yml](.github/workflows/ci.yml)) and checks that no Samsung code is inside the apps. It publishes no app files.

## What works today

| | Works now | Coming next |
|---|---|---|
| **The voice** | A voice on a timer (MIT's method) that waits until the watch shows you lying still; from the phone, or from the watch's speaker or earbuds paired with the watch; lucid-dream words by default; your own words, or your own recorded voice; a natural-sounding voice that runs on the phone | |
| **Watch** | Tracks your sleep all night: heart rate, heartbeat timing, skin response, movement, skin temperature, blood oxygen and the raw pulse signal; the phone shows them live | Breathing rate worked out from the pulse signal |
| **Alarms** | On the watch: a wake-up alarm, on by default (five tones, a spoken one with your name, snooze), that logs how long you take to wake up, with a backup in the watch's own alarm clock; and an early wake-up (often called WBTB, "wake back to bed") that guides you through staying up and buzzes when to go back to bed. You can skip it for one night, start it yourself with **I woke up**, or time it from when you fall asleep | |
| **Soft sounds** | After the early wake-up, the watch teaches you a soft three-note sound, then plays it quietly once you are asleep again; a test mode secretly skips some nights so Stats can show whether it helps you | |
| **Your answers** | Dream questions on the watch after each alarm: record your dream, then a few one-tap questions; **Save my dream** for a dream you remember in the night or after it | Showing your recordings as text (the phone already transcribes them offline, see [docs/SETUP.md](docs/SETUP.md) §13), and a dream journal on the phone |
| **Safety** | The voice never gets louder than the volume you test it at. When the phone plays it, Start stops first if the phone's volume is off, its sound goes to headphones, or Do Not Disturb would mute it. Start warns when the watch app is out of date | |

Good to know: MIT's own studies used a calm voice and short lines, like OpenTDI. In their at-home study, a timed
voice alone got the chosen theme into at least one dream report for 73 of 80 people. Whether this setup helps you
become **lucid** has not been tested yet. Your own nights are the first test, so tell us how it goes.

## Your data stays with you

- Everything is stored **on your own phone and watch**, in the app's private storage. OpenTDI has no server, no
  account and no analytics.
- The phone and the watch talk through Google Play services' Wear Data Layer (the voice, commands, the night files and
  your dream recordings). It normally uses Bluetooth or your Wi-Fi; when neither connects them, Google may relay the data
  through its servers, end-to-end encrypted ([Android's Data Layer overview](https://developer.android.com/training/wearables/data/overview)).
- Apart from that, the phone app uses the network only to reach your watch on your own Wi-Fi when it installs the
  watch app. The watch app only uses the network for Samsung's own sensor service.
- Each night is saved as a set of simple spreadsheet (CSV) files. **Stats** on the phone shows your nights. **Saved
  nights** (turn on **More** at the bottom of the phone's Settings) deletes what the voice did on those
  nights, and the watch has its own **Saved nights** under **Details**. The sleep data and dream recordings already
  copied from the watch stay on the phone, in Stats. To remove them, clear OpenTDI's storage in the phone's Android
  settings (Apps > OpenTDI > Storage > Clear data). This also resets your settings and voice.

## For developers

### Building

You need JDK 17 or newer and the Android SDK (platform 37, build-tools 36). Tell Gradle where the SDK is with
`sdk.dir` in a `local.properties` file, or with `ANDROID_HOME`. The Gradle wrapper fetches everything else.

```bash
./gradlew :shared:test :wear:testDebugUnitTest :mobile:testDebugUnitTest :wear:assembleDebug :mobile:assembleDebug
```

On Windows use Git Bash, or `gradlew.bat`. The apps land in `wear/build/outputs/apk/debug/` and
`mobile/build/outputs/apk/debug/`. Step-by-step setup, installing, permissions and copying the night logs off the
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

Samsung's sensors only talk to apps registered with Samsung, or to any app while **developer mode** is on. Without it
the watch says "Samsung's extra sensors are locked for this app" and offers **Use basic sensors**.

1. **Settings > Apps > Health Platform** (newer watches call it **Health Sensor Service**).
2. Quickly tap the **title** at the top, "Health Platform", about 10 times. Tap the title, not the version number.
3. Tap the **Developer mode** row that appears below the version, accept Samsung's terms when asked, and switch it
   **On**.
4. Keep the watch online.

The watch needs Health Platform / Health Sensor Service **1.6.5 or newer**. Samsung's own
[developer-mode page](https://developer.samsung.com/health/sensor/guide/developer-mode.html) is the authority.

### Where things are

| Folder | What is inside |
|---|---|
| `shared/` | Code both apps share: data formats, signal processing, the voice's timetable, with tests |
| `wear/` | The watch app (`wear/libs/` is where Samsung's sensor kit goes) |
| `mobile/` | The phone app |
| `samsung-health-stub/` | A stand-in for Samsung's kit, so the project builds without it |
| `docs/` | The engineering spec, the research it is based on, and the setup guide |

- [docs/ARCHITECTURE_AND_THRESHOLDS.md](docs/ARCHITECTURE_AND_THRESHOLDS.md): the engineering spec, written before
  the build; a note at its top says which parts exist. Every threshold and timing is labelled SOURCED (from the MIT
  papers or code) or GUESSED (an untested default).
- [docs/references/](docs/references/): research notes, including the first on-device findings
  ([device_audit.md](docs/references/device_audit.md)).
- [CLAUDE.md](CLAUDE.md): the original project brief; where it and the spec disagree, the spec wins.

## License

OpenTDI is free software under the **GNU General Public License, version 3** ([LICENSE](LICENSE)). In short: you may
use, study, change and share it, and anything you share that is built from it must stay open under the same license.

[NOTICE](NOTICE) adds one exception so OpenTDI can work with Samsung's sensor kit, and lists the other projects the
apps include. OpenTDI contains no Samsung code. Samsung's Health Sensor SDK is optional; each user downloads it from
Samsung, under Samsung's own terms, and adds it in the app.

## Thanks

OpenTDI stands on the Dormio research of Adam Haar Horowitz and colleagues in the Fluid Interfaces group at the MIT
Media Lab. The public [openSleep](https://github.com/tomasero/openSleep), [dormio](https://github.com/christinatchen/dormio)
and [Dormio Device Duo Wearable](https://github.com/ThingEngineer/Dormio-Device-Duo-Wearable) projects were studied as
references; OpenTDI copies no code, spoken wording or audio from them. OpenTDI is not affiliated with MIT or Samsung.
