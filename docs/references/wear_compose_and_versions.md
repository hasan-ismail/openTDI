# Wear OS Compose UI and dependency versions for OpenTDI

Researched 2026-10-27 for the `:wear` (Galaxy Watch8 Classic SM-L500, Wear OS 6 / API 36) and
`:mobile` modules.

**Labels.** **FACT** means the claim comes straight from the cited source: `maven-metadata.xml`,
a `.pom` or `.module` file, an AAR's `AndroidManifest.xml` or `aar-metadata.properties`, a
published `-sources.jar`, or an official web page. **INFERENCE** means it is my reasoning and
has not been checked on hardware.

**How the sources were read.** Every version number below comes from `maven-metadata.xml` on
`dl.google.com/android/maven2` (Google) or `repo1.maven.org/maven2` (Central), fetched on
2026-10-27. Dependencies come from each artifact's Gradle `.module` file (runtime variant), or
from the `.pom` when there is no `.module`. `minSdk` comes from the AAR's `AndroidManifest.xml`.
`minCompileSdk` and `minAndroidGradlePluginVersion` come from the AAR's
`META-INF/com/android/build/gradle/aar-metadata.properties`. Source citations such as
`RotaryScrollable.kt:565` point into the published `-sources.jar` of the version named.

> **Note on `lastUpdated`.** The `lastUpdated` field in maven-metadata is the time of the *last
> publish of any version* of that artifact, not the date of the stable release.

---

## 0. Decisions

| # | Decision | Basis |
|---|---|---|
| 1 | Use **Wear Compose Material3 1.7.1** (`compose-material3` + `compose-foundation`). Do **not** use `compose-material` (M2.5) or `compose-navigation`. | FACT: 1.7.1 is the latest stable. The release page says compose-material "is superseded by" compose-material3. compose-navigation 1.7.1 pulls in compose-material 1.7.1 (§2.1). |
| 2 | **Drop Horologist** (CLAUDE.md §3 says "Jetpack Compose for Wear OS + Horologist libraries"). | FACT: the last non-alpha release is 0.7.15 (2025-07-31), built on Wear Compose 1.5.0-rc01 + M2.5. Every 0.8.x is `-alpha`. INFERENCE: everything OpenTDI needs is already in Wear Compose 1.7.1 + play-services-wearable (§3). |
| 3 | Bezel settings (incubation delay 60–300 s, sensitivity) use the **Material3 `Picker`**. It has rotary snap and haptics built in, so no custom rotary code is needed. | FACT: Picker.kt:102, :165, :501 (§1.7) |
| 4 | Keep the machine's known-good toolchain: AGP 9.4.0, Gradle 9.6.1, Kotlin 2.4.10, Compose BOM 2026.08.00, coroutines 1.11.0, serialization-json 1.11.0, core 1.19.0, activity-compose 1.13.0, lifecycle 2.11.0, datastore 1.2.1, compileSdk 37, build-tools 36.0.0. | Owner instruction. FACT: no chosen wear or Play-services library conflicts with it (§5). |
| 5 | Kotlin 2.4.10 has to be put on the build classpath explicitly. | FACT: the AGP 9.4.0 POM has a *runtime* dependency on `kotlin-gradle-plugin` **2.2.10** (§5.2). |
| 6 | During the nap, do **not** keep the watch screen on. Sensing runs in the health foreground service, and an **Ongoing Activity** keeps the app one tap away. | FACT: always-on page (§2.3). INFERENCE: the design choice itself. |

---

## 1. Rotary / bezel input

### 1.1 How a bezel click reaches Compose (FACT, Compose UI 1.12.0 = BOM 2026.08.00)

`androidx.compose.ui:ui-android:1.12.0` sources, `androidx/compose/ui/platform/AndroidComposeView.android.kt`:

- Line 2556: `ACTION_SCROLL` events are routed to rotary handling only when
  `motionEvent.isFromSource(SOURCE_ROTARY_ENCODER)`.
- Lines 2672–2685, `handleRotaryEvent`:
  ```kotlin
  val axisValue = -event.getAxisValue(AXIS_SCROLL)
  RotaryScrollEvent(
      verticalScrollPixels = axisValue * getScaledVerticalScrollFactor(config, context),
      horizontalScrollPixels = axisValue * getScaledHorizontalScrollFactor(config, context),
      uptimeMillis = event.eventTime,
      inputDeviceId = event.deviceId,
  )
  ```
  The event then goes to `focusOwner.dispatchRotaryEvent(...)`, which means it is delivered to the **focused** node.
- **FACT:** `verticalScrollPixels` is `−AXIS_SCROLL × ViewConfiguration.getScaledVerticalScrollFactor()`.
  ViewConfiguration describes that factor as the "Amount to scroll in response to a vertical
  MotionEvent.ACTION_SCROLL event. Multiply this by the event's axis value to obtain the number
  of pixels to be scrolled." (https://developer.android.com/reference/android/view/ViewConfiguration)
- **FACT:** `MotionEvent.AXIS_SCROLL` was added in API 26. It "Reports the relative movement of the
  generic scrolling device … A good example would be the rotation of a rotary encoder input
  device." (https://developer.android.com/reference/android/view/MotionEvent) The page does **not**
  say which rotation direction gives a positive value.

### 1.2 `RotaryScrollEvent` fields (FACT)

Source: `ui-android-1.12.0` `androidMain/androidx/compose/ui/input/rotary/RotaryScrollEvent.android.kt:25-46`

| Field | Type | KDoc (verbatim) |
|---|---|---|
| `verticalScrollPixels` | `Float` | "The amount to scroll (in pixels) in response to a RotaryScrollEvent in a container that can scroll vertically." |
| `horizontalScrollPixels` | `Float` | "…in a container that can scroll horizontally." |
| `uptimeMillis` | `Long` | "The time in milliseconds at which this even occurred. The start (`0`) time is platform-dependent." (it is `MotionEvent.eventTime`) |
| `inputDeviceId` | `Int` | "The id for the input device that this event came from" (Android-only field; it is `MotionEvent.deviceId`) |

The class KDoc (line 22) says: "Some Wear OS devices contain a physical rotating side button, or a
rotating bezel. When the user turns the button or rotates the bezel, a RotaryScrollEvent is sent
to the item in focus."

### 1.3 `Modifier.onRotaryScrollEvent` and its focus requirements (FACT)

- `commonMain/androidx/compose/ui/input/rotary/RotaryInputModifier.kt:42` declares
  `fun Modifier.onRotaryScrollEvent(onRotaryScrollEvent: (RotaryScrollEvent) -> Boolean): Modifier`.
  The KDoc (lines 23–31) says it intercepts events "if it (or one of its children) is focused". Return
  `true` to consume the event; return `false` and the event goes to the parent.
- Line 66, `Modifier.onPreRotaryScrollEvent`: ancestors see the event first, top-down, before
  it reaches the focused node.
- The official sample (https://developer.android.com/training/wearables/compose/rotary-input, last
  updated 2026-10-23) chains `.onRotaryScrollEvent { …; true }.focusRequester(focusRequester).focusable()`.
  The guide warns that the raw pixel values in the sample are "likely to be overly sensitive".
- Compose focus guide (https://developer.android.com/develop/ui/compose/touch-input/focus/change-focus-behavior,
  updated 2026-10-22): "Always invoke `focusRequester.requestFocus()` inside event callbacks,
  side-effects (`LaunchedEffect`), or coroutines—never directly within the body of a composable
  function". For initial focus: `LaunchedEffect(Unit) { focusRequester.requestFocus() }`.
- Wear's alternative, `Modifier.requestFocusOnHierarchyActive()`
  (`androidx.wear.compose:compose-foundation:1.7.1`, `HierarchicalFocus.kt:107`), "will request
  focus on the following focusable element when needed (i.e. this needs to be before that element
  in the Modifier chain)". The KDoc adds: "NOTE: This shouldn't be used together with
  FocusRequester.requestFocus calls in LaunchedEffect" (lines 100–101).
  `HierarchicalFocusCoordinator`, `rememberActiveFocusRequester` and `ActiveFocusListener` are
  `@Deprecated` and were replaced by it (`HierarchicalFocusCoordinator.kt:30-58`).
- Wear's own scrollables use this pattern. `ScalingLazyColumn.kt:692-698` applies
  `Modifier.requestFocusOnHierarchyActive().rotaryScrollable(...)`, and so do
  `TransformingLazyColumn.kt:227` and `Pager.kt:129`. `BasicSwipeToDismissBox.kt:181` marks only the
  foreground screen active with `hierarchicalFocusGroup(!isBackground)`.

### 1.4 Bezel and crown: detents, resolution, sign

- **FACT:** Wear Compose tells a bezel from a crown with a system feature, not with the event values:
  ```kotlin
  // compose-foundation 1.7.1, rotary/RotaryScrollable.kt:563-568
  /** Returns whether the input is Low-res (a bezel) or high-res (a crown/rsb). */
  private fun isLowResInput(): Boolean =
      LocalContext.current.packageManager.hasSystemFeature("android.hardware.rotaryencoder.lowres")
  ```
  KDoc lines 166–173: "Low-res devices: Have less granular control, registering larger rotations
  at a time … Examples include physical or virtual bezels". Low-res input gets snap behaviour
  `LowResSnapRotaryScrollableBehavior` and a fling timeframe of `LowResFlingTimeframe = 100L` ms,
  against `HighResFlingTimeframe = 30L` ms for a crown (lines 572–573).
- **FACT (not found):** No source I could reach documents the pixels per bezel detent, the raw
  `AXIS_SCROLL` value per detent, or whether clockwise is positive for the Galaxy Watch bezel. I
  checked the MotionEvent, ViewConfiguration and Wear rotary-input pages and the Compose sources. A
  Samsung developer-forum thread that a search snippet attributed "1 clockwise / −1
  counter-clockwise" to returned **HTTP 404** when fetched
  (https://forum.developer.samsung.com/t/using-the-buzel-in-unity/28773), so that claim is
  **unverified**.
- **FACT:** Samsung lists a rotating bezel for SM-L500
  (https://www.samsung.com/ie/business/watches/galaxy-watch/galaxy-watch8-classic-46mm-black-bluetooth-sm-l500nzkaeua/),
  and so does GSMArena (https://m.gsmarena.com/samsung_galaxy_watch8_classic-ampp-13998.php). Neither
  page gives encoder details.
- **INFERENCE:** Each detent most likely arrives as one `ACTION_SCROLL` event with a fixed
  `|AXIS_SCROLL|`. Because of the minus sign in §1.1, the sign of `verticalScrollPixels` is the
  opposite of the raw axis. Do not hard-code either value. Measure them once on the SM-L500:
  ```bash
  adb -s <WATCH> shell pm list features | grep -i rotary     # expect android.hardware.rotaryencoder.lowres
  adb -s <WATCH> shell pm list libraries | grep -i wear-sdk  # needed by LocalAmbientModeManager (§2.3)
  adb -s <WATCH> shell dumpsys input | grep -i -A12 rotary   # encoder device, sources, AXIS_SCROLL range/resolution
  ```
  Then log `verticalScrollPixels`, `uptimeMillis` and `inputDeviceId` from a debug
  `onRotaryScrollEvent` while turning the bezel 10 detents clockwise and 10 anticlockwise. Write
  the per-detent pixel value and the sign into this file.

### 1.5 Haptics for detents

**FACT: what Wear Compose 1.7.1 does** (`compose-foundation`, `rotary/Haptics.kt`):

- Lines 60–71: on **SDK > 35** (VANILLA_ICE_CREAM), which includes **API 36 on the SM-L500**, rotary haptics use
  the **platform `ScrollFeedbackProvider`** (`PlatformRotaryHapticHandler`, line 305). It calls
  `onScrollProgress(inputDeviceId, InputDevice.SOURCE_ROTARY_ENCODER, axis, deltaPx)`,
  `onSnapToItem(...)` and `onScrollLimit(...)`. Only on SDK ≤ 35 does it fall back to custom
  `View.performHapticFeedback` constants.
- Lines 131–140 and 161: in that fallback, Galaxy watches get the hidden Samsung constants
  `GalaxyWatchConstants(scrollFocus = 102, scrollTick = 101, scrollLimit = 50107)`. The fallback
  detects a Galaxy watch with `Build.MANUFACTURER` "Samsung" and `Build.MODEL` matching
  `^SM-R.*` (line 367). **SM-L500 does not match `SM-R`**, but this does not matter on API 36,
  where the platform path is used.
- Rotary haptics are enabled by default. Each `RotaryScrollableDefaults.behavior/snapBehavior` takes
  `hapticFeedbackEnabled: Boolean = true` (`RotaryScrollable.kt:289-297`).

**FACT: platform API** (https://developer.android.com/reference/android/view/ScrollFeedbackProvider, updated 2026-08-03):
`ScrollFeedbackProvider` was added in **API 35**. It has `createProvider(View)`,
`onScrollProgress(inputDeviceId, source, axis, deltaInPixels)`,
`onSnapToItem(inputDeviceId, source, axis)` and `onScrollLimit(inputDeviceId, source, axis, isStart)`. The page says:
"for vertical scrolling on MotionEvent.AXIS_SCROLL, convert the raw axis value to the equivalent
pixels by using ViewConfiguration.getScaledVerticalScrollFactor()". So `RotaryScrollEvent.verticalScrollPixels`
is already the value it expects.

**FACT: public `HapticFeedbackConstants`** (https://developer.android.com/reference/android/view/HapticFeedbackConstants).
There is no public `ROTARY_*` constant. The ones relevant to detents are:

| Constant | Value | Added | Doc (abridged, verbatim) |
|---|---|---|---|
| `CLOCK_TICK` | 4 | API 21 | "The user has pressed either an hour or minute tick of a Clock." |
| `SEGMENT_TICK` | 26 | API 34 | "switching between a series of potential choices, for example items in a list or discrete points on a slider." |
| `SEGMENT_FREQUENT_TICK` | — | API 34 | "many potential choices … expected to be very soft … If the device can't make a suitably soft vibration, then it may not make any vibration." |
| `CONFIRM` / `REJECT` | 16 / 17 | API 30 | confirmation or success / rejection or failure |
| `TOGGLE_ON` | 21 | API 34 | toggled into the on position |
| `NO_HAPTICS` | −1 | API 34 | skip haptics |

**INFERENCE, for OpenTDI:** use Picker (§1.7) and the haptics come for free. In a custom handler, call
`ScrollFeedbackProvider.createProvider(view).onSnapToItem(e.inputDeviceId, InputDevice.SOURCE_ROTARY_ENCODER, MotionEvent.AXIS_SCROLL)`
once per step on API ≥ 35. Below that, use `view.performHapticFeedback(SEGMENT_TICK)` on API ≥ 34, or `CLOCK_TICK`
below 34 (wear minSdk is 30). Haptics fire only while the user is adjusting settings awake. Never
vibrate during the nap except for a deliberate cue.

### 1.6 The newer rotary API: `rotaryScrollable` (FACT, compose-foundation 1.7.1)

- `RotaryScrollable.kt:147`:
  `fun Modifier.rotaryScrollable(behavior: RotaryScrollableBehavior, focusRequester: FocusRequester, reverseDirection: Boolean = false, overscrollEffect: OverscrollEffect? = null)`.
  It wires in `.focusRequester(focusRequester).focusTargetWithSemantics()` itself (lines 153–159), so no separate
  `focusable()` is needed. The overload without `overscrollEffect` is `@Deprecated(level = HIDDEN)` (line 194).
- `RotaryScrollableDefaults` (line 263):
  - `behavior(scrollableState, flingBehavior = ScrollableDefaults.flingBehavior(), hapticFeedbackEnabled = true)` (line 294) for scrolling with or without fling.
  - `snapBehavior(scrollableState, layoutInfoProvider, snapOffset, hapticFeedbackEnabled, snapSensitivity)` (line 328). It uses
    `LowSnapSensitivity = 0.4f` by default, or `HighSnapSensitivity = 0.8f` (lines 269, 276).
- Direction: "aligned with the scroll direction of the `Modifier.verticalScroll` … (please be aware
  that `Modifier.scrollable` has the opposite direction by default)" (lines 105–107).
- Rotary is **built in** (FACT) to `ScalingLazyColumn` (`ScalingLazyColumn.kt:489`, `rotaryScrollableBehavior = RotaryScrollableDefaults.behavior(state)`),
  `TransformingLazyColumn` (`TransformingLazyColumn.kt:148`), the pagers (`Pager.kt:129`), and
  Material3 `Picker` (below). Material3 `Stepper.kt` and `Slider.kt` in 1.7.1 contain **no** rotary
  handling: a grep for `rotary`/`Rotary` found nothing in either file.

### 1.7 Recommendation for the wrist settings (CLAUDE.md §3, "Physical Rotating Bezel UI")

- **FACT:** `Picker(state: PickerState, contentDescription, …, rotaryScrollableBehavior: RotaryScrollableBehavior? = PickerDefaults.rotarySnapBehavior(state), option: @Composable PickerScope.(Int) -> Unit)`
  (`compose-material3` 1.7.1, `Picker.kt:154-166`). "Picker supports rotary input by default" (line 102).
  `rotarySnapBehavior` is `RotaryScrollableDefaults.snapBehavior(state, …)` (line 501). Picker hands it to
  its inner ScalingLazyColumn (line 293), which requests focus with `requestFocusOnHierarchyActive()`.
- **INFERENCE:** A Picker over a fixed list such as `listOf(60, 75, …, 300)` seconds, plus a 3-option Picker
  for sensitivity, covers the requirement with no hand-written rotary, focus or haptic code. The
  low-res bezel snap is already handled. Label the middle sensitivity "Default", not "MIT
  Standard": owner requirement 10 says these numbers are unvalidated, not MIT values.
- Only if a free-form dial is needed (INFERENCE, untested):
  ```kotlin
  // shortcut: PX_PER_STEP must be measured on SM-L500 (see 1.4); until then, one step per event.
  Modifier
      .onRotaryScrollEvent { e -> acc += e.verticalScrollPixels; /* emit whole steps, haptic per step */ true }
      .requestFocusOnHierarchyActive()   // or focusRequester + LaunchedEffect { requestFocus() }, not both
      .focusable()
  ```

---

## 2. Wear Compose Material3 (`androidx.wear.compose:compose-material3`)

### 2.1 Versions and status (FACT)

- Metadata (`https://dl.google.com/android/maven2/androidx/wear/compose/compose-material3/maven-metadata.xml`):
  latest **stable 1.7.1**. Newest overall is 1.8.0-alpha02. The `compose-foundation`, `compose-material`,
  `compose-navigation`, `compose-navigation3` and `compose-ui-tooling` lines are identical.
- Release notes (https://developer.android.com/jetpack/androidx/releases/wear-compose): 1.7.1 on
  **October 07, 2026** (bug fixes for one-handed-gesture indicators), 1.7.0 on **September 23, 2026**, 1.6.0 on
  March 25, 2026. The notes say: "The `androidx.wear.compose:compose-material` library is superseded by the
  `androidx.wear.compose:compose-material3` library." 1.8.0-alpha01 says "Libraries now target
  Kotlin 2.2 and require kotlinc 2.2" (that is alpha only).
- AAR `aar-metadata.properties` for `compose-material3`, `compose-foundation` and `compose-navigation` 1.7.1:
  **`minCompileSdk=37`, `minAndroidGradlePluginVersion=9.1.0`**, manifest `minSdkVersion=25`.
- `compose-material3:1.7.1.module` runtime deps: `androidx.compose.{foundation,ui,runtime,animation,material-ripple,ui-text,ui-util}:1.10.0`,
  `compose.material:material-icons-core:1.7.8`, `androidx.core:core:1.15.0`, `lifecycle-runtime:2.10.0`,
  `androidx.wear:wear-core:1.1.0`, `wear.compose:compose-foundation:1.7.1`, `compose-material-core:1.7.1`,
  `graphics-shapes:1.0.1`, `kotlin-stdlib:2.1.20`, `kotlinx-coroutines-core:1.9.0`.
- `compose-foundation:1.7.1.module`: `activity-compose:1.10.0`, compose `1.10.0`, `core:1.12.0`,
  `lifecycle-runtime-compose:2.10.0`, `profileinstaller:1.4.0`, `kotlin-stdlib:2.1.20`. Its manifest declares
  `<uses-library android:name="wear-sdk" android:required="false"/>`.
- `compose-navigation:1.7.1.module` depends on **`androidx.wear.compose:compose-material:1.7.1`** and
  `androidx.navigation:navigation-compose:2.6.0`.
  **INFERENCE:** OpenTDI's watch app needs only a few screens (session, settings, status), so plain
  state-driven screen switching inside `AppScaffold` avoids pulling in M2.5. Re-evaluate
  `compose-navigation3` only if navigation grows.
- The Compose BOM does **not** manage Wear Compose. FACT: `compose-bom-2026.08.00.pom` has no `androidx.wear*`
  entry, so Wear Compose needs its own `version.ref`.

### 2.2 Key APIs in 1.7.1 (FACT, `compose-material3-1.7.1-sources.jar` unless noted)

| API | Where | Notes |
|---|---|---|
| `AppScaffold(modifier, timeText = { TimeText() }, containerColor, contentColor, content)` | `AppScaffold.kt:58` | Top-level. Keeps `TimeText` across in-app transitions. |
| `ScreenScaffold(scrollState, …)` | `ScreenScaffold.kt:124…735` | Overloads for `ScalingLazyListState`, `TransformingLazyColumnState`, `LazyListState`, `ScrollState`, and a `ScrollInfoProvider`, each with or without an `edgeButton` slot. Coordinates TimeText scroll-away and the `ScrollIndicator`. |
| `TimeText(modifier, curvedModifier, maxSweepAngle, backgroundColor, timeSource, contentPadding, content)` | `TimeText.kt:109` | The always-on page says "`TimeText` (included by default in `ScreenScaffold`) is ambient-aware and automatically updates once per minute in ambient mode." |
| `Button(...)` (4 overloads), `EdgeButton(...)` | `Button.kt:125/218/604/724`, `EdgeButton.kt:148` | |
| `Text(...)` | `Text.kt:93/187` | |
| `MaterialTheme(...)` | `MaterialTheme.kt:55` | Use Wear's `MaterialTheme`, not `androidx.compose.material3`'s. |
| `AlertDialog(visible, onDismissRequest, confirmButton, title, modifier, dismissButton, icon, text, …)` | `AlertDialog.kt:127` (6 overloads) | Fits the `SDK_POLICY_ERROR` and Health-Platform-update messages (owner requirements 2 and 4). |
| `Picker`, `PickerGroup`, `TimePicker`, `Stepper`, `Slider` | `Picker.kt:154`, `Stepper.kt:104`, `Slider.kt:122` | Only Picker handles rotary input (§1.6). |
| `TransformingLazyColumn(modifier, state, contentPadding, …, rotaryScrollableBehavior = RotaryScrollableDefaults.behavior(state), overscrollEffect, content)` | foundation `lazy/TransformingLazyColumn.kt:135` | The current list for M3. 1.7.0 added `PinnableContainer` support, slide-off animations and `firstLayoutItemProvider` (release notes). |
| `ScalingLazyColumn(...)` | foundation `lazy/ScalingLazyColumn.kt:346/473/600` | Still supported and still has rotary. |

### 2.3 Screen on vs ambient (FACT unless marked)

From the always-on page (https://developer.android.com/training/wearables/views/always-on, updated 2026-10-23):

- "On Wear OS 6 and higher, if an app targets SDK 36 or newer, it is considered always-on. The
  display is dimmed, but the application continues running and remains visible. (Updates may be as
  infrequent as once per minute.)"
- Use `LocalAmbientModeManager` / `rememberAmbientModeManager()` to observe `AmbientMode.Interactive` and
  `AmbientMode.Ambient`. "AmbientTickEffect … is invoked only periodically -- typically once per minute".
- "On Wear OS 5 and higher, you can prevent this [return to the watch face] by implementing an **Ongoing
  Activity** … to keep your app visible until the task ends." The Ongoing Activity's touch intent has to point at the
  always-on activity. Live Updates replace this on Wear OS 7+; Wear OS 6 still needs Ongoing Activity.
- The `keepScreenOn` modifier "functions as a wake lock, keeping the device in the Interactive state.
  Use this with extreme caution as it severely impacts battery life." It is
  `fun Modifier.keepScreenOn()` in `androidx.compose.ui` (`ui-android-1.12.0`, `commonMain/androidx/compose/ui/KeepScreenOn.kt:30`).
  Material3 has its own `KeepScreenOn()`, but it is `internal`, used only by ConfirmationDialog and OpenOnPhoneDialog (`KeepScreenOn.kt:28`).

APIs:

- **Compose way:** `androidx.wear.compose.foundation.rememberAmbientModeManager()`, `LocalAmbientModeManager`,
  `AmbientModeManager.currentAmbientMode`, `withAmbientTick` and `AmbientTickEffect`
  (`compose-foundation 1.7.1`, `LocalAmbientModeManager.kt:57/81/95/147`). These were added in 1.6.0. **It depends on the
  Wear SDK**: `import com.google.wear.Sdk` (line 33) and `Sdk.getWearManager(…, AmbientManager::class.java)` (line 237),
  with no fallback when the library is missing. The AAR declares `wear-sdk` as `required="false"`.
  **INFERENCE:** if SM-L500's One UI 8 Watch does not ship `wear-sdk`, calling it would fail at runtime. Check with
  `pm list libraries` (§1.4) before relying on it.
- **Fallback that needs no Wear SDK:** `androidx.wear:wear:1.4.0`'s `AmbientLifecycleObserver(activity, callbacks)` or
  `AmbientLifecycleObserver(activity, executor, callbacks)` (`AmbientLifecycleObserver.kt:62/99`), with
  `AmbientLifecycleCallback.onEnterAmbient(AmbientDetails) / onUpdateAmbient() / onExitAmbient()` and `isAmbient`
  (lines 137–163). The KDoc says "the app needs to declare that it uses the android.Manifest.permission.WAKE_LOCK permission".
- **Ongoing Activity:** `androidx.wear:wear-ongoing:1.1.0`, `OngoingActivity.Builder(context, notificationId, NotificationCompat.Builder)`
  (`OngoingActivity.java:137`), then `.setTouchIntent(...)`, `.build().apply(context)` (sample on the always-on page).

**INFERENCE, for OpenTDI:**
- Target SDK 36 on the watch, so Wear OS 6 "considered always-on" applies. The session screen should therefore
  draw a near-black ambient layout (dim HR and state text only). A lit face by the pillow is a sleep disruptor.
- Never use `keepScreenOn` during a nap. Attach an Ongoing Activity to the health foreground
  service's notification, so the session survives the return to the watch face and stays one tap away.
- Sensor streaming must not depend on the activity being visible. Owner requirement 9 still asks to verify EDA
  with the screen off for 10+ minutes.

---

## 3. Horologist (`com.google.android.horologist`)

### 3.1 Status (FACT)

- Repo: https://github.com/google/horologist. `archived: false`, last push 2026-10-27, read through the GitHub API.
  README "Maintained Versions":

  | Version | Branch | Min SDK (Wear) | Kotlin/JDK | Description |
  |---|---|---|---|---|
  | 0.7.x | release-0.7.x | 26 | 2.2.0 / 17 | "Wear Compose 1.5.x, Compose 1.8.x and generally latest stable Androidx." |
  | 0.8.x | main | 26 | 2.2 / 17 | "Wear Compose 1.6.x, Compose 1.9.x and generally latest relevant alphas of Androidx." |

  "the main branch will actively update to incorporate new API guidance, removing or changing APIs."
- Releases (GitHub API): **v0.7.15** on 2025-07-31 ("Dependency bumps to stable and Wear Compose 1.5.0-rc01"),
  v0.8.0-alpha on 2025-07-14 … **v0.8.4-alpha** on 2026-08-10 ("Long overdue accumulation of features.").
  There has been no release in the 0.8 line without the `-alpha` suffix.
- Central metadata, e.g. `…/horologist-compose-layout/maven-metadata.xml`: `latest = release = 0.8.4-alpha`,
  lastUpdated 2026-08-10. The last version without a pre-release suffix is **0.7.15**. The same holds for
  `horologist-datalayer`, `-datalayer-phone`, `-datalayer-watch`, `-compose-tools`, `-compose-material`,
  `-composables`, `-tiles`, `-audio-ui`, `-media-ui`, `-health-composables`, `-health-service`, `-auth-ui`,
  `-images-base` and `-annotations`. `horologist-media-ui-material3` only starts at 0.7.13-beta.
  **`horologist-compose-material3` does not exist** (HTTP 404).

### 3.2 Dependencies of the candidate modules (FACT, `.module` runtime variants and AAR metadata)

| Module @ version | Wear Compose | Compose BOM | Other notable | Kotlin stdlib | AAR minSdk / minCompileSdk |
|---|---|---|---|---|---|
| compose-layout 0.7.15 | **compose-material / -foundation / -navigation / -ui-tooling 1.5.0-rc01** | 2025.07.00 | wear 1.3.0, lifecycle-runtime-compose 2.9.2, paging-compose 3.3.6, navigation-runtime 2.9.2 | 2.2.0 | 26 / 1 |
| compose-layout 0.8.4-alpha | **compose-material3 / -material / -foundation / -navigation 1.6.2** | 2026.04.01 | lifecycle-runtime-compose 2.10.0, paging-compose 3.5.0, navigation-runtime 2.9.8 | 2.4.10 | 26 / 36 |
| datalayer 0.7.15 | — | — | play-services-wearable 19.0.0, coroutines(-play-services) 1.10.2, datastore(-preferences, -proto) 1.1.7, protobuf-kotlin-lite 4.26.1, wear-remote-interactions 1.1.0, lifecycle-service 2.9.2 | 2.2.0 | 21 / 1 |
| datalayer 0.8.4-alpha | — | — | play-services-wearable 20.0.1, coroutines 1.11.0, datastore 1.2.1, protobuf-kotlin-lite 4.34.1, wear-remote-interactions 1.2.0, lifecycle-service 2.10.0 | 2.4.10 | 23 / 36 |
| compose-tools 0.7.15 | compose-material / -foundation / -navigation / -ui-tooling 1.5.0-rc01 | 2025.07.00 | horologist-tiles 0.7.15, tiles-tooling-preview 1.5.0, kotlin-reflect 2.2.0 | 2.2.0 | 26 / 1 |
| compose-material 0.7.15 | compose-material / -foundation 1.5.0-rc01 (M2.5) | 2025.07.00 | | 2.2.0 | 26 / 1 |

Deprecations (FACT, `horologist-compose-layout` sources): every public helper in `compose/rotaryinput/Haptics.kt` is
`@Deprecated("Replaced by wear compose")`. `ScalingLazyColumnDefaults` and `rememberColumnState` are
`@Deprecated("Use rememberResponsiveColumnState")`. The `compose/ambient/AmbientAware*` files exist in 0.7.15 but are
gone from 0.8.4-alpha.

What horologist-datalayer 0.8.4-alpha contains (FACT, sources): `WearDataLayerRegistry`, `WearLocalDataStore`,
`ProtoDataStoreHelper` (proto DataStore over the Data Layer), and `DataLayerAppHelper` (app-installed checks,
launching the app on the other device).

### 3.3 Recommendation (INFERENCE)

**Depend on no Horologist module.**
- `0.7.15` is the only stable option. It was compiled against Wear Compose **1.5.0-rc01** and M2.5 `compose-material`. With
  Material3 1.7.1 on the classpath, Gradle would upgrade its transitive Wear Compose to 1.7.1 under a library
  that was never tested against it, and it would drag in M2.5, paging and navigation.
- `0.8.4-alpha` (Wear Compose 1.6.2) is alpha, and its own README says the main branch removes or changes APIs.
- What OpenTDI would have used Horologist for already exists: scaffolds, rotary, TransformingLazyColumn and
  ambient in Wear Compose 1.7.1; previews in `wear.compose:compose-ui-tooling` + `androidx.wear:wear-tooling-preview`;
  messaging in `play-services-wearable` + `kotlinx-coroutines-play-services` `await()`.
  `horologist-datalayer` is built around DataItems and proto DataStore, which is the opposite of owner
  requirement 17 (MessageClient for commands, ChannelClient or batched messages for telemetry, **not** DataClient).
- If an app-installed or "open on phone" helper is wanted later, `horologist-datalayer:0.8.4-alpha` is
  dependency-compatible with the catalog below: its play-services-wearable 20.0.1, coroutines 1.11.0, datastore 1.2.1 and
  stdlib 2.4.10 all match. It is still alpha.

---

## 4. Version resolution (FACT: metadata fetched 2026-10-27)

| Artifact | Latest stable | Newest overall | Recommend | AAR minSdk | minCompileSdk / minAGP | Key runtime deps |
|---|---|---|---|---|---|---|
| `androidx.wear.compose:compose-material3` | **1.7.1** | 1.8.0-alpha02 | **1.7.1** | 25 | **37 / 9.1.0** | compose 1.10.0, core 1.15.0, lifecycle-runtime 2.10.0 |
| `androidx.wear.compose:compose-foundation` | **1.7.1** | 1.8.0-alpha02 | **1.7.1** | 25 | **37 / 9.1.0** | activity-compose 1.10.0, compose 1.10.0, lifecycle-runtime-compose 2.10.0 |
| `androidx.wear.compose:compose-ui-tooling` | 1.7.1 | 1.8.0-alpha02 | 1.7.1 (debug/previews) | 24 | 34 / 8.1.1 | ui-tooling-preview 1.9.0, wear-tooling-preview 1.0.0 |
| `androidx.wear:wear-tooling-preview` | 1.0.0 | 1.0.0 | 1.0.0 | — | — | (lastUpdated 2023-11-27) |
| `androidx.wear:wear` | **1.4.0** | 1.4.0 | 1.4.0 (only if the wear-sdk check fails, §2.3) | 23 | 34 / 8.1.1 | core 1.6.0, fragment 1.2.4, recyclerview 1.1.0, stdlib 2.0.21 |
| `androidx.wear:wear-ongoing` | **1.1.0** | 1.1.0 | **1.1.0** | 25 | 34 / 8.1.1 | core 1.6.0 (lastUpdated 2025-09-10) |
| `com.google.android.gms:play-services-wearable` | **20.0.1** | 20.0.1 | **20.0.1** | 23 | (none declared) | play-services-base 18.5.0, -basement 18.9.0, -tasks 18.2.0 (POM) |
| `org.jetbrains.kotlinx:kotlinx-coroutines-play-services` | **1.11.0** | 1.11.0 | **1.11.0** | JVM jar | — | coroutines-core 1.11.0, play-services-tasks 16.0.1, stdlib 2.2.20 |
| `org.jetbrains.kotlinx:kotlinx-coroutines-core` / `-android` / `-test` | 1.11.0 | 1.11.0 | 1.11.0 (known-good) | — | — | `-android`: stdlib 2.2.20 |
| `org.jetbrains.kotlinx:kotlinx-serialization-json` | 1.12.0 | 1.12.0 | **1.11.0** (known-good) | — | — | 1.11.0 → stdlib 2.3.20. 1.12.0 → stdlib **2.4.10**. GitHub: 1.12.0 (2026-10-19) "is based on Kotlin 2.4.10", 1.11.0 (2026-04-09) "is based on Kotlin 2.3.20" |
| `androidx.datastore:datastore-preferences` | 1.3.0 | 1.3.0 | **1.2.1** (known-good) | 24 (1.3.0) | 34 / 8.1.1 (1.3.0) | 1.3.0 → datastore 1.3.0, stdlib 2.2.20 (published 2026-10-21) |
| `androidx.lifecycle:lifecycle-service` | **2.11.0** | 2.12.0-alpha05 | **2.11.0** | 23 | 34 / 8.1.1 | lifecycle-runtime 2.11.0, stdlib 2.1.20 |
| `androidx.lifecycle:lifecycle-runtime-compose` | 2.11.0 | 2.12.0-alpha05 | 2.11.0 | — | — | |
| `androidx.compose:compose-bom` | 2026.09.00 | 2026.09.00 | **2026.08.00** (known-good) | — | — | 2026.08.00 → ui/foundation/runtime/animation 1.12.0, compose material3 1.4.0. 2026.09.00 → 1.12.1, material3 1.4.0 |
| `androidx.core:core` | 1.19.1 | 1.19.1 | **1.19.0** (known-good) | 23 | 37 / 9.1.0 | lifecycle 2.6.2 (1.19.0) / 2.10.0 (1.19.1). FACT: `core-ktx:1.19.0` `classes.jar` holds **0 classes** (an empty shim) |
| `androidx.activity:activity-compose` | **1.13.0** | 1.14.0-alpha03 | **1.13.0** | 23 | 36 / 8.9.1 | core-ktx 1.18.0, lifecycle 2.9.4, navigationevent-compose 1.0.0 |
| `org.jetbrains.kotlin:kotlin-gradle-plugin` (and `plugin.compose`, `plugin.serialization`, `jvm` markers) | 2.4.21 | 2.5.0-Beta2 | **2.4.10** (known-good) | — | — | 2.4.10 markers exist on plugins.gradle.org and Central |
| `com.android.tools.build:gradle` | 9.4.1 | 9.6.0-alpha01 | **9.4.0** (known-good) | — | — | POM: runtime `kotlin-gradle-plugin:2.2.10` |
| Gradle wrapper | 9.8.2 (services.gradle.org "current") | — | **9.6.1** (known-good) | — | — | AGP 9.4 needs ≥ 9.6.0 |
| `junit:junit` | **4.13.2** | 4.13.2 | **4.13.2** | — | — | (lastUpdated 2021-02-13) |
| Horologist (any module) | 0.7.15 | 0.8.4-alpha | **none** (§3.3) | 21–26 | 1 / 36 | §3.2 |

Release notes for play-services-wearable 20.0.1 (FACT, https://developers.google.com/android/guides/releases, entry dated
**April 28, 2026**): "Vulnerability fix. Apps targeting API level 37 or later are recommended to use this SDK version at a
minimum." 19.0.0 (Nov 2024) "Removed an unsupported sendMessage() API variant that included a MessageOptions parameter."
There is no 20.0.0 in the metadata: it goes 19.0.0 → 20.0.1.

---

## 5. Compatibility check against the known-good toolchain

### 5.1 Flags (FACT unless marked)

| Check | Result |
|---|---|
| compileSdk 37 vs `minCompileSdk` | Highest required is **37** (wear compose 1.7.1, core 1.19.x). **OK.** INFERENCE: Wear Compose 1.7.x cannot be used if compileSdk is ever lowered to 36. |
| AGP 9.4.0 vs `minAndroidGradlePluginVersion` | Highest required is **9.1.0** (wear compose 1.7.1, core 1.19.x). **OK.** |
| AGP 9.4 requirements (https://developer.android.com/build/releases/gradle-plugin) | "The maximum API level that Android Gradle plugin 9.4 supports is API level 37." Gradle min **9.6.0**, SDK Build Tools min **36.0.0**, JDK **17**. Known-good (Gradle 9.6.1, build-tools 36.0.0, compileSdk 37) **OK.** |
| Wear minSdk 30 vs library minSdk | Highest is 26 (Horologist). Chosen libs are ≤ 25. **OK.** |
| Compose BOM 2026.08.00 vs Wear Compose 1.7.1 | Wear needs compose foundation/ui/runtime ≥ **1.10.0**. The BOM gives **1.12.0**, so Gradle resolves up. **OK.** INFERENCE: this is the normal direction for androidx (newer Compose under an older Wear Compose). |
| Lifecycle 2.11.0 vs Wear Compose | Wear needs ≥ 2.10.0 ("required for bundled lifecycle runtime lint checks to run with AGP version 9.5.0-alpha04 or later", 1.7.0 notes). **OK.** |
| Activity-compose 1.13.0 vs Wear foundation (needs ≥ 1.10.0) | **OK.** |
| Kotlin 2.4.10 vs library stdlib | The newest stdlib any recommended or optional lib needs is **2.4.10** (serialization-json 1.12.0, Horologist 0.8.4-alpha). The recommended set peaks at **2.3.20** (serialization-json-jvm 1.11.0 POM). Wear Compose is 2.1.20, coroutines 2.2.20, datastore-preferences-android 1.2.1 is 2.0.21. All ≤ 2.4.10, so **OK.** |
| play-services-wearable 20.0.1 | Recommended for targetSdk ≥ 37. minSdk 23. **OK.** |
| Wear Compose and `wear-sdk` | `uses-library wear-sdk required=false`. `LocalAmbientModeManager` needs it at runtime. **Verify on SM-L500** (§2.3). |
| Emulator | Not tested, and not relevant here. Per owner requirement 3, rotary and haptic checks are done on the real watch. |

### 5.2 Pinning Kotlin 2.4.10 under AGP 9 built-in Kotlin

- FACT (https://developer.android.com/build/releases/agp-9-0-0-release-notes): "Android Gradle plugin 9.0 now has a
  runtime dependency on Kotlin Gradle plugin (KGP) 2.2.10 … To use a higher version of KGP … add
  `classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:KGP_VERSION")`" to the top-level `buildscript { dependencies { … } }`.
  FACT: `gradle-9.4.0.pom` still declares `kotlin-gradle-plugin` **2.2.10**, scope `runtime`.
- FACT (https://developer.android.com/build/migrate-to-built-in-kotlin): do not apply `org.jetbrains.kotlin.android`. With built-in
  Kotlin, `jvmTarget` defaults to `android.compileOptions.targetCompatibility`.
- INFERENCE: two things put KGP 2.4.10 on the root classpath: declaring `alias(libs.plugins.kotlin.jvm) apply false`
  (needed anyway for the pure-Kotlin `:shared` module) together with `kotlin-compose` / `kotlin-serialization` at 2.4.10, or the explicit classpath
  line. Gradle then picks the highest version. Confirm with `./gradlew buildEnvironment | grep kotlin-gradle-plugin`
  (expect `2.2.10 -> 2.4.10`). If it does not show that, add the `buildscript` classpath line with `libs.kotlin.gradle.plugin`.

### 5.3 Things in CLAUDE.md this research overrides

- §3 `:wear` "Jetpack Compose for Wear OS + Horologist libraries" → Wear Compose Material3 1.7.1, **no Horologist** (§3.3).
- §3 sensitivity label "MIT Standard" → "Default" (owner requirement 10).
- §2 says `onRotaryScrollEvent` / `MotionEvent.ACTION_SCROLL`. Prefer a Picker with built-in `rotaryScrollable`, and use `onRotaryScrollEvent` only for a free-form dial (§1.7).

---

## 6. Open items to measure on the SM-L500 (none are done yet)

1. `pm list features | grep rotary`: is `android.hardware.rotaryencoder.lowres` present?
2. Pixels per bezel detent, and the sign of `verticalScrollPixels` for clockwise rotation (§1.4).
3. `pm list libraries | grep wear-sdk`: present? If not, use `AmbientLifecycleObserver` from `androidx.wear:wear:1.4.0`.
4. Do Picker rotary haptics fire on One UI 8 Watch, through the platform `ScrollFeedbackProvider` path?
5. With targetSdk 36: how bright is the "dimmed" always-on session screen in a dark room? Does the user's AOD setting change it?

---

## Recommended version catalog

`gradle/libs.versions.toml`. Values are the known-good toolchain plus the newest compatible stable wear and Play-services
libraries. `targetSdk` and the mobile `minSdk` are **INFERENCE** (Wear OS 6 = API 36; the phone is API 35/36 per CLAUDE.md §1).

```toml
[versions]
# SDK levels (read via libs.versions.<name>.get().toInt())
compileSdk = "37"
targetSdk = "36"          # INFERENCE: Wear OS 6 = API 36, so the "considered always-on" rules apply (section 2.3)
wearMinSdk = "30"
mobileMinSdk = "30"       # INFERENCE/guess: phone is API 35/36; raise freely
buildTools = "36.0.0"

# Toolchain: known-good on this machine (2026-09-01); Gradle wrapper 9.6.1
agp = "9.4.0"             # latest stable 9.4.1
kotlin = "2.4.10"         # latest stable 2.4.21

# AndroidX / Compose: known-good
composeBom = "2026.08.00" # latest 2026.09.00
core = "1.19.0"           # core-ktx is an empty shim; use androidx.core:core
activityCompose = "1.13.0"
lifecycle = "2.11.0"
datastore = "1.2.1"       # latest stable 1.3.0

# Wear (not managed by the Compose BOM)
wearCompose = "1.7.1"     # needs compileSdk >= 37, AGP >= 9.1.0
wearToolingPreview = "1.0.0"
wear = "1.4.0"            # AmbientLifecycleObserver fallback only (if wear-sdk is absent)
wearOngoing = "1.1.0"

# Data Layer
playServicesWearable = "20.0.1"

# KotlinX: known-good
coroutines = "1.11.0"
serializationJson = "1.11.0"  # 1.12.0 (built on Kotlin 2.4.10) is an optional bump

junit = "4.13.2"

[libraries]
# Build classpath, only needed if `buildEnvironment` does not already show KGP 2.4.10 (section 5.2)
kotlin-gradle-plugin = { module = "org.jetbrains.kotlin:kotlin-gradle-plugin", version.ref = "kotlin" }

androidx-core = { module = "androidx.core:core", version.ref = "core" }
androidx-activity-compose = { module = "androidx.activity:activity-compose", version.ref = "activityCompose" }
androidx-lifecycle-service = { module = "androidx.lifecycle:lifecycle-service", version.ref = "lifecycle" }
androidx-lifecycle-runtime-compose = { module = "androidx.lifecycle:lifecycle-runtime-compose", version.ref = "lifecycle" }
androidx-datastore-preferences = { module = "androidx.datastore:datastore-preferences", version.ref = "datastore" }

# Phone Compose (BOM-managed)
androidx-compose-bom = { module = "androidx.compose:compose-bom", version.ref = "composeBom" }
androidx-compose-ui = { module = "androidx.compose.ui:ui" }
androidx-compose-ui-tooling = { module = "androidx.compose.ui:ui-tooling" }
androidx-compose-ui-tooling-preview = { module = "androidx.compose.ui:ui-tooling-preview" }
androidx-compose-material3 = { module = "androidx.compose.material3:material3" }

# Watch Compose
wear-compose-material3 = { module = "androidx.wear.compose:compose-material3", version.ref = "wearCompose" }
wear-compose-foundation = { module = "androidx.wear.compose:compose-foundation", version.ref = "wearCompose" }
wear-compose-ui-tooling = { module = "androidx.wear.compose:compose-ui-tooling", version.ref = "wearCompose" }
wear-tooling-preview = { module = "androidx.wear:wear-tooling-preview", version.ref = "wearToolingPreview" }
androidx-wear = { module = "androidx.wear:wear", version.ref = "wear" }
androidx-wear-ongoing = { module = "androidx.wear:wear-ongoing", version.ref = "wearOngoing" }

# Data Layer (MessageClient / ChannelClient; no DataClient for the stream)
play-services-wearable = { module = "com.google.android.gms:play-services-wearable", version.ref = "playServicesWearable" }
kotlinx-coroutines-play-services = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-play-services", version.ref = "coroutines" }

kotlinx-coroutines-core = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-core", version.ref = "coroutines" }
kotlinx-coroutines-android = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-android", version.ref = "coroutines" }
kotlinx-coroutines-test = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-test", version.ref = "coroutines" }
kotlinx-serialization-json = { module = "org.jetbrains.kotlinx:kotlinx-serialization-json", version.ref = "serializationJson" }

junit = { module = "junit:junit", version.ref = "junit" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
# AGP 9 compiles Kotlin itself: no org.jetbrains.kotlin.android plugin
kotlin-jvm = { id = "org.jetbrains.kotlin.jvm", version.ref = "kotlin" }                    # :shared (pure Kotlin)
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
```
