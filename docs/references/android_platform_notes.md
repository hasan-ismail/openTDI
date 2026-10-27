# Android / Wear OS platform notes for OpenTDI (API 34-36)

Retrieved 2026-10-27. Scope: the platform rules OpenTDI needs on a Galaxy Watch8 Classic
(SM-L500, Wear OS 6 = Android 16 / API 36) and an Android 15/16 phone. Samsung Health Sensor
SDK specifics (its own permission, `SDK_POLICY_ERROR`, Health Platform versions) are out of
scope here and live in the Samsung SDK notes.

**Labels.** **FACT** = stated directly by the cited source (quotes are verbatim). **INFERENCE** =
my reasoning from the facts; verify before relying on it. AOSP citations are to the tag
`android-16.0.0_r1` unless marked otherwise, path:line, fetched from
`https://android.googlesource.com/platform/<project>/+/refs/tags/android-16.0.0_r1/<path>`.
OEM builds (One UI 8 / One UI 8 Watch) can differ from AOSP; every AOSP-derived claim is
therefore something to confirm on the real devices.

---

## 0. Decisions this research supports

| # | Decision for OpenTDI | Basis |
|---|---|---|
| D1 | Phone and watch APKs use the **same `applicationId`** (e.g. `com.opentdi`) and the **same signing key**. Kotlin `namespace`s may stay `com.opentdi.mobile` / `com.opentdi.wear`. | FACT §4.1, FACT §4.1 (namespace vs applicationId) |
| D2 | Watch manifest: `READ_HEART_RATE` (+ `BODY_SENSORS` with `maxSdkVersion="35"`), `ACTIVITY_RECOGNITION`, `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_HEALTH`, `POST_NOTIFICATIONS`, `WAKE_LOCK`. Add Samsung's permission per the Samsung notes. | FACT §1.2, §1.5, §2.6 |
| D3 | Watch FGS `foregroundServiceType="health"`, started from the watch UI. `ACTIVITY_RECOGNITION` (granted) or `HIGH_SAMPLING_RATE_SENSORS` (declared) satisfies the type check without depending on while-in-use. | FACT §1.5 (AOSP) |
| D4 | Phone: one FGS `mediaPlayback\|microphone`, started by `startForegroundService()` while the session screen is visible, never `stopForeground()`-ed until the nap ends. | FACT + AOSP §2.2 |
| D5 | ADB sim receiver: send with `-p <applicationId>` (or `-n`), receiver `exported="true"` + `android:permission="android.permission.DUMP"`, ideally only in the debug manifest. A bare `am broadcast -a ...` will **not** reach a manifest receiver. | FACT §3 |
| D6 | Triggers/commands: `MessageClient` (`sendRequest` gives an app-level ACK). Telemetry: batched `MessageClient` (simplest) or one `ChannelClient` stream per session. No `DataClient` for the stream. End-of-session CSV: `ChannelClient.sendFile`. | FACT §4.3-4.8, INFERENCE for the choice |
| D7 | Countdown/recording timers on the phone run under a `PARTIAL_WAKE_LOCK` held by the FGS (or use elapsed-realtime alarms), because `Handler`/`Dispatchers.Main` delays stop during deep sleep. | FACT §2.5 |

---

## 1. Health permissions on Android 16 / Wear OS 6 (API 36)

### 1.1 What changed (FACT)

- Android 16 behavior changes, "Health and fitness permissions"
  (https://developer.android.com/about/versions/16/behavior-changes-16, updated 2026-10-23):
  > "For apps targeting Android 16 (API level 36) or higher, `BODY_SENSORS` permissions use more
  > granular permissions under `android.permissions.health`, which Health Connect also uses. As of
  > Android 16, any API previously requiring `BODY_SENSORS` or `BODY_SENSORS_BACKGROUND` requires
  > the corresponding `android.permissions.health` permission instead."

  The affected list includes "`HEART_RATE_BPM` from Health Services on Wear OS",
  "`Sensor.TYPE_HEART_RATE` from Android Sensor Manager" and
  "`FOREGROUND_SERVICE_TYPE_HEALTH` where the respective `android.permission.health` permission is
  needed in place of `BODY_SENSORS`". It then says: while-in-use → request `READ_HEART_RATE`
  instead of `BODY_SENSORS`; background → request `READ_HEALTH_DATA_IN_BACKGROUND` instead of
  `BODY_SENSORS_BACKGROUND`.
- Same page, "Mobile apps": "Mobile apps migrating to use the `READ_HEART_RATE` and other granular
  permissions must also declare an activity to display the app's privacy policy." (Our phone app
  does not need any health permission, so this does not apply.)
- Wear OS 6 changes (https://developer.android.com/training/wearables/versions/6/changes, updated
  2026-03-05), table: `BODY_SENSORS` → `READ_HEART_RATE`, `READ_OXYGEN_SATURATION`,
  `READ_SKIN_TEMPERATURE`; `BODY_SENSORS_BACKGROUND` → `READ_HEALTH_DATA_IN_BACKGROUND`. The change
  "affects your app only if you update your target SDK version to Android 16". For apps targeting
  35 or lower: "the system automatically requests the `READ_HEART_RATE` permission on your app's
  behalf", and previously granted `BODY_SENSORS`/`BODY_SENSORS_BACKGROUND` are kept after an OS
  update.
- The mechanism is a platform split permission, gated by an aconfig flag. AOSP
  `frameworks/base/data/etc/platform.xml:283-292`: `BODY_SENSORS` splits to
  `android.permission.health.READ_HEART_RATE` and `BODY_SENSORS_BACKGROUND` splits to
  `READ_HEALTH_DATA_IN_BACKGROUND`, both `targetSdk="36"` and
  `featureFlag="android.permission.flags.replace_body_sensor_permission_enabled"`. Flag definition:
  `frameworks/base/core/java/android/permission/flags.aconfig:297-304` (`is_fixed_read_only: true`,
  namespace `android_health_services`). Whether the flag is on in a given OEM build is not visible
  in source. INFERENCE: the Wear OS 6 doc describes the migration as live, so assume it is on for
  SM-L500, and confirm on the device.

### 1.2 How to declare and request on the watch (FACT)

From "Declare appropriate permissions", Health Services
(https://developer.android.com/health-and-fitness/guides/health-services/permissions, updated
2026-01-19), section "Migrate to support API level 36":

```xml
<uses-permission android:name="android.permission.BODY_SENSORS"
    android:maxSdkVersion="35" />
<uses-permission android:name="android.permission.health.READ_HEART_RATE" />
<!-- only if body-sensor data is needed while the app is in the background: -->
<uses-permission android:name="android.permission.BODY_SENSORS_BACKGROUND"
    android:maxSdkVersion="35" />
<uses-permission android:name="android.permission.health.READ_HEALTH_DATA_IN_BACKGROUND" />
```

and the runtime request is gated by version:
`if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) { this.add(HealthPermissions.READ_HEART_RATE) }`.

The same page also says Health Services uses `BODY_SENSORS` "on Wear OS 5.1 (API level 35) and
lower" and `BODY_SENSORS_BACKGROUND` "between Wear OS 4 (API level 33) and Wear OS 5.1 (API level
35), inclusive".

**On owner item 6 (BODY_SENSORS needed at all?).** FACT: on API 36 no AOSP API documented above
still requires `BODY_SENSORS` for an app targeting 36. FACT (§1.5): the health FGS type no
longer lists `BODY_SENSORS` when the flag is on. INFERENCE: `BODY_SENSORS(_BACKGROUND)` matter
only for the Health Services / `SensorManager` path on API ≤35. Whether Samsung's
`HealthTrackingService` checks either of them is vendor code and cannot be checked in AOSP.
Verify on the watch: install without them, start the trackers, and look for permission
errors.

### 1.3 Protection levels (FACT)

| Permission | Level | Source |
|---|---|---|
| `android.permission.health.READ_HEART_RATE` | `dangerous`, `backgroundPermission=READ_HEALTH_DATA_IN_BACKGROUND`, group `HEALTH` | AOSP `packages/modules/HealthFitness/apk/HealthPermissionsManifest.xml:240-246`; ref "Added in API level 34" (https://developer.android.com/reference/android/health/connect/HealthPermissions) |
| `android.permission.health.READ_HEALTH_DATA_IN_BACKGROUND` | `dangerous` (not hard-restricted) | same manifest `:21-25`; ref "Added in API level 35" |
| `android.permission.BODY_SENSORS` | `dangerous`, `backgroundPermission=BODY_SENSORS_BACKGROUND` | AOSP `frameworks/base/core/res/AndroidManifest.xml:1858-1863` |
| `android.permission.BODY_SENSORS_BACKGROUND` | `dangerous`, `permissionFlags="hardRestricted"` | same file `:1875-1879`; ref: "This is a hard restricted permission which cannot be held by an app until the installer on record allowlists the permission." |
| `android.permission.ACTIVITY_RECOGNITION` | `dangerous\|instant` | same file `:1716-1720` |
| `android.permission.HIGH_SAMPLING_RATE_SENSORS` | `normal` ("access sensor data with a sampling rate greater than 200 Hz") | same file `:1849-1853`; ref (https://developer.android.com/reference/android/Manifest.permission) |
| `FOREGROUND_SERVICE` | `normal` | Manifest.permission ref |
| `FOREGROUND_SERVICE_HEALTH` / `_MICROPHONE` / `_MEDIA_PLAYBACK` | `normal\|instant` | ref; AOSP manifest `:7993`, `:7975`, `:7957` |
| `RECORD_AUDIO`, `POST_NOTIFICATIONS`, `BLUETOOTH_CONNECT` | `dangerous` | Manifest.permission ref |
| `WAKE_LOCK` | `normal` | Manifest.permission ref |
| `android.permission.DUMP` | `signature\|privileged\|development` | AOSP manifest `:5168-5169` |

Health Connect "rationale activity" rule on watches. FACT: AOSP
`packages/modules/HealthFitness/service/java/com/android/server/healthconnect/permission/HealthConnectPermissionHelper.java:358-366`
has `shouldEnforcePermissionUsageIntent()` return false on `FEATURE_WATCH`, with the comment
"The rationale intent is not currently required on Wear devices." So the watch app does not
need the privacy-policy / `VIEW_PERMISSION_USAGE` activity that phones need.

### 1.4 `adb shell pm grant`: what works (FACT from AOSP, per-permission results are INFERENCE until run)

- Help text, `PackageManagerShellCommand.java:4933-4937`: "These commands either grant or revoke
  permissions to apps. The permissions must be declared as used in the app's manifest, be runtime
  permissions (protection level dangerous), and the app targeting SDK greater than Lollipop MR1."
- `PermissionManagerServiceImpl.java:1411-1420`: grant proceeds only for `isRuntime()` or
  `isDevelopment()` permissions, otherwise `SecurityException("... is not a changeable permission
  type")`. `:1429-1433`: throws `"has not requested permission"` if the manifest does not request
  it. `:1456-1460`: a hard-restricted permission without an exemption flag is refused, with the log
  line "Cannot grant hard restricted non-exempt permission".
- `adb install` allowlists restricted permissions by default: `PackageManagerShellCommand.java:3417`
  sets `INSTALL_ALL_WHITELIST_RESTRICTED_PERMISSIONS`; `--restrict-permissions` clears it
  (`:3460-3463`, help `:4780`). `-g` = "grant all runtime permissions" (`:4776`, `:3456-3458`).

| Permission (watch unless noted) | `pm grant` on API 36? |
|---|---|
| `android.permission.health.READ_HEART_RATE` | INFERENCE: yes, it is a runtime `dangerous` permission. Confirm on the device. |
| `android.permission.health.READ_HEALTH_DATA_IN_BACKGROUND` | INFERENCE: yes, if requested. Grant `READ_HEART_RATE` first, since it is that permission's background permission. |
| `BODY_SENSORS` declared with `maxSdkVersion="35"` | FACT-derived: **no** on API 36. It is not requested there, so you get `"has not requested permission"`. |
| `BODY_SENSORS_BACKGROUND` | Hard-restricted. Works only if the installer allowlisted it, which plain `adb install` does. Irrelevant on API 36 with `maxSdkVersion="35"`. |
| `ACTIVITY_RECOGNITION`, `RECORD_AUDIO` (phone), `POST_NOTIFICATIONS`, `BLUETOOTH_CONNECT` (phone) | yes (dangerous). |
| `FOREGROUND_SERVICE*`, `WAKE_LOCK`, `HIGH_SAMPLING_RATE_SENSORS` | **no**, and not needed: normal permissions are granted at install. `pm grant` throws "not a changeable permission type". |
| Samsung `com.samsung.android.hardware.sensormanager.permission.READ_ADDITIONAL_HEALTH_DATA` | Unknown: its protection level is defined by Samsung. Check with `adb shell pm list permissions -f` on the watch. |

INFERENCE for the test script: use `adb install -r -g <apk>`, or `pm grant` the permissions
above one by one. Drop the CLAUDE.md §6 entries for `BODY_SENSORS` and `BODY_SENSORS_BACKGROUND`,
since they will fail on API 36.

### 1.5 Exactly which permission satisfies `foregroundServiceType="health"` (FACT)

Docs, "Foreground service types", Health
(https://developer.android.com/develop/background-work/services/fgs/service-types, updated
2026-10-23): manifest permission `FOREGROUND_SERVICE_HEALTH`; runtime prerequisite, "At least one of
the following conditions must be true":
- "Declare the `HIGH_SAMPLING_RATE_SENSORS` permission in your manifest."
- "Request and be granted at least one of": `BODY_SENSORS` on API 35 and lower,
  `READ_HEART_RATE`, `READ_SKIN_TEMPERATURE`, `READ_OXYGEN_SATURATION`, `ACTIVITY_RECOGNITION`.

Note on that page: "The `BODY_SENSORS` and sensor-based READ runtime permissions are subject to
while-in-use restrictions. For this reason, you cannot create a `health` foreground service that
uses body sensors while your app is in the background, unless you've been granted the
`BODY_SENSORS_BACKGROUND` (between API level 33 and API level 35) or
`READ_HEALTH_DATA_IN_BACKGROUND` (API level 36) permissions."

AOSP code (the exact rule):
- API 36: `frameworks/base/core/java/android/app/ForegroundServiceTypePolicy.java:490-522`.
  All-of `FOREGROUND_SERVICE_HEALTH`, plus any-of `ACTIVITY_RECOGNITION`,
  `HIGH_SAMPLING_RATE_SENSORS`, and either (flag on) `READ_HEART_RATE`, `READ_SKIN_TEMPERATURE`,
  `READ_OXYGEN_SATURATION` or (flag off) `BODY_SENSORS`. `foregroundOnlyPermission=false`.
- API 34 (`refs/tags/android-14.0.0_r1`, same file `:483-499`): all-of `FOREGROUND_SERVICE_HEALTH`,
  any-of `ACTIVITY_RECOGNITION`, `BODY_SENSORS`, `HIGH_SAMPLING_RATE_SENSORS`.
- How each candidate is checked, `ForegroundServiceTypePolicy.java` `RegularPermission.checkPermission`
  (`:1171-1250`): normal permissions with no app-op count as granted. An app-op in
  `MODE_FOREGROUND` (a while-in-use grant, e.g. `READ_HEART_RATE` without its background
  permission) counts only when the FGS start was while-in-use-allowed (`case MODE_FOREGROUND`,
  `:1243`). `ACTIVITY_RECOGNITION` has no background permission, so once granted it is not
  while-in-use-gated. The bg-start doc says the same: "(This doesn't apply if it's a health service
  that needs different permissions, like `ACTIVITY_RECOGNITION`.)"

INFERENCE for OpenTDI: start the watch FGS from the visible watch UI, so all variants pass. If
it ever has to restart from the background, a granted `ACTIVITY_RECOGNITION` still satisfies the
type check, but the background-start (BFSL) rules in §2.7 still apply.

### 1.6 `ACTIVITY_RECOGNITION` and `HIGH_SAMPLING_RATE_SENSORS` (FACT)

- `ACTIVITY_RECOGNITION`: "Allows an application to recognize physical activity." Dangerous
  (Manifest.permission ref). Health Services maps step and calorie data types to it (Health
  Services permissions table). It is also one of the health-FGS any-of permissions (§1.5).
- Sensor rate limiting, "Sensors overview"
  (https://developer.android.com/develop/sensors-and-location/sensors/sensors_overview, updated
  2026-10-23): for apps targeting 31+, "If you call the `registerListener()` method ... the sensor
  sampling rate is limited to 200 Hz"; with `SensorDirectChannel` "limited to `RATE_NORMAL`, which
  is usually about 50 Hz". Higher rates need `HIGH_SAMPLING_RATE_SENSORS`.
- INFERENCE: 25 Hz accelerometer (owner item 7) is far below 200 Hz, so `HIGH_SAMPLING_RATE_SENSORS`
  is not needed for the rate. Its only use for us would be as an always-granted way to satisfy
  the health FGS type check.

---

## 2. Foreground services and keeping both apps alive for a nap

### 2.1 Rules for API 34-36 (FACT)

- Android 14 (https://developer.android.com/about/versions/14/behavior-changes-14): "If your app
  targets Android 14 (API level 34) or higher, it must specify at least one foreground service
  type for each foreground service within your app."
- Declare: "If multiple types apply to your service, separate them with the `|` operator"; an
  undeclared type gives `MissingForegroundServiceTypeException` on `startForeground()`
  (https://developer.android.com/develop/background-work/services/fgs/declare). Passing a type not
  declared in the manifest throws `IllegalArgumentException`
  (https://developer.android.com/develop/background-work/services/fgs/launch).
- Launch: "Apps that target Android 14 (API level 34) or higher must request the appropriate
  permissions for the foreground service type ... throws `SecurityException` if the app is
  missing any." (fgs/launch)
- Background start (https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start,
  updated 2026-10-23): apps targeting 31+ "can't start foreground services while the app is
  running in the background, except for a few special cases"
  (`ForegroundServiceStartNotAllowedException`). Separately, for while-in-use types: "if your
  foreground service needs a while-in-use permission, you must call
  `Context.startForegroundService()` or `Context.bindService()` while your app has a visible
  activity, unless the service falls into one of the defined exemptions."
- Microphone type (fgs/service-types): manifest `FOREGROUND_SERVICE_MICROPHONE`, runtime
  prerequisite "Request and be granted the `RECORD_AUDIO` runtime permission"; "you cannot create a
  `microphone` foreground service while your app is in the background".
- Media type: `mediaPlayback`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, runtime prerequisites
  "None". Apps targeting 15+ may not launch it from `BOOT_COMPLETED`.
- `POST_NOTIFICATIONS`: "Apps don't need to request the `POST_NOTIFICATIONS` permission in order to
  launch a foreground service. However, apps must include a notification when they start a
  foreground service". If it is denied, users "still see notices related to foreground services
  in the Task Manager but don't see them in the notification drawer"
  (https://developer.android.com/develop/ui/views/notifications/notification-permission).
- Time limits: only `dataSync` and `mediaProcessing` are time-limited. AOSP
  `ActiveServices.java` `getTimeLimitedFgsType()` checks only those two types. Android 15 docs:
  "new restrictions on how long a `dataSync` foreground service can run ... Similar restrictions
  apply to ... `mediaProcessing`"
  (https://developer.android.com/develop/background-work/services/fgs/changes).
  INFERENCE: `mediaPlayback`, `microphone` and `health` have no system time limit.

### 2.2 Phone: one `mediaPlayback|microphone` FGS started while the UI is visible. Does it keep the mic for 10-60 min with the screen off?

Answer: **yes, per AOSP, as long as the service stays foreground the whole time.**

- FACT (docs, §2.1): starting while a visible activity exists is the documented way to get
  while-in-use access.
- FACT (AOSP `ActiveServices.java:993-994`): `startServiceLocked()` calls
  `setFgsRestrictionLocked()` with the *caller's* state when `startForegroundService()` runs.
  `:8415-8437` (`shouldAllowFgsWhileInUsePermissionLocked`): the caller being at
  `PROCESS_STATE_TOP` or having a visible UID (`REASON_UID_VISIBLE`) makes it allowed.
- FACT (AOSP `ActiveServices.java:8324-8336`): the while-in-use reason is recomputed only
  `if ((allowWiu == REASON_DENIED) || ...)`, and existing non-denied values are kept
  (`:8345-8346`). So a later `startService()` from the background (for example the Data Layer
  listener poking the service) does not downgrade it.
- FACT (AOSP `OomAdjuster.java:2435-2461`): while `s.isForeground` and
  `s.isFgsAllowedWiu_forCapabilities()`, the process gets
  `PROCESS_CAPABILITY_FOREGROUND_MICROPHONE` when the FGS type includes `MICROPHONE`. This is a
  standing capability, not a one-time check.
- FACT (AOSP reset points): `resetFgsRestrictionLocked()` (`:8377-8384`) runs when the service
  leaves the foreground (`:2821`, in the stop-foreground path), when FGS state is torn down
  (`:6464`), and in the `startService()`-without-`startForegroundService()` timeout path
  (`:2494-2496`).
- FACT (AOSP `ActiveServices.java:2543-2548`): an FGS without while-in-use logs "Foreground service
  started from background can not have location/camera/microphone access". The docs give the
  same Logcat line as the way to detect the problem.
- FACT: `adb shell dumpsys activity services <pkg>` prints `mAllowWiu_noBinding`
  (`ServiceRecord.java:970`), so the state can be checked on the device mid-nap.

Pitfalls (INFERENCE from the above):
1. Call `startForegroundService()` from the session screen's Start button, and call
   `startForeground(id, notif, MEDIA_PLAYBACK or MICROPHONE)` with **both** types at once in
   `onStartCommand()`. `RECORD_AUDIO` must already be granted, or the microphone type throws
   `SecurityException`.
2. Never call `stopForeground()` mid-nap. It clears while-in-use, and you cannot get it back
   from the background. To update the notification, `notify()` with the same id.
3. If the process dies and the system restarts the service, assume the mic is gone. Detect it
   (the Logcat line above, `mAllowWiu_noBinding`, or silent `AudioRecord` RMS) and tell the user.
   Whether a sticky restart keeps while-in-use was not verified.
4. Don't move CSV/transcription work into WorkManager/JobScheduler started from the FGS. Android
   16: "Background jobs started from a foreground service now must adhere to their respective
   runtime quotas" (fgs/changes). Run it in the service's own coroutines.
5. User "Stop" in Task Manager (Android 13+) "removes your app from memory", and "The system
   doesn't send your app any callbacks"
   (https://developer.android.com/develop/background-work/services/fgs/handle-user-stopping). On
   next launch, read `ApplicationExitInfo` `REASON_USER_REQUESTED` and mark the session aborted.
   Test with `adb shell cmd activity stop-app PACKAGE_NAME`.

Phone manifest (FACT for each element per §2.1; the combination is INFERENCE):

```xml
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MICROPHONE" />
<uses-permission android:name="android.permission.RECORD_AUDIO" />        <!-- runtime -->
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />  <!-- runtime, 33+ -->
<uses-permission android:name="android.permission.WAKE_LOCK" />
<application>
  <service android:name=".session.NapSessionService"
      android:exported="false"
      android:foregroundServiceType="mediaPlayback|microphone" />
</application>
```

### 2.3 Audio focus (FACT, relevant to owner item 18)

Android 15 (https://developer.android.com/about/versions/15/behavior-changes-15): "Apps that target
Android 15 (API level 35) must be the top app or running a foreground service in order to request
audio focus. If an app attempts to request focus when it does not meet one of these requirements,
the call returns `AUDIOFOCUS_REQUEST_FAILED`." INFERENCE: the nap FGS satisfies this, but handle
`AUDIOFOCUS_REQUEST_FAILED` anyway.

### 2.4 Doze and wake locks (both devices)

- FACT (docs, https://developer.android.com/training/monitoring-device-state/doze-standby, updated
  2026-10-22): Doze "Suspends network access", "Ignores wake locks", defers standard alarms, and
  doesn't run jobs or syncs. App Standby does not treat an app as idle while "The app has a process
  currently in the foreground, either as an activity or foreground service". Test commands:
  `adb shell dumpsys deviceidle force-idle` / `unforce`.
- FACT (AOSP, finer than the doc): `services/core/java/com/android/server/power/PowerManagerService.java:4409-4418`.
  In idle mode, an app's `PARTIAL_WAKE_LOCK` is disabled only if the UID is not on the idle
  allowlists **and** `mProcState > PROCESS_STATE_BOUND_FOREGROUND_SERVICE`. `ProcessStateEnum.aidl:42-45`:
  `FOREGROUND_SERVICE = 4`, `BOUND_FOREGROUND_SERVICE = 5`. So a process running an FGS keeps its
  partial wake lock during Doze. Conflict: the doc's blanket "Ignores wake locks" vs this code. I
  rely on the code, and this must be checked on the device with `force-idle`. A separate
  `mLowPowerStandbyActive` branch (`:4421-4427`) disables wake locks above `BOUND_TOP`, FGS
  included. INFERENCE: Low Power Standby is not expected on phones or watches. Check it on the
  devices.
- FACT: `Handler.postDelayed`: "The time-base is `SystemClock.uptimeMillis()`. Time spent in deep
  sleep will add an additional delay to execution."
  (https://developer.android.com/reference/android/os/Handler). `kotlinx-coroutines-android`
  `HandlerDispatcher.kt:139-151` implements `delay` on `Dispatchers.Main` with
  `handler.postDelayed`.
  INFERENCE: a 180 s countdown on `Dispatchers.Main` can stretch if the phone CPU suspends. So
  hold a `PARTIAL_WAKE_LOCK` (with a timeout) from countdown start through re-incubation, or for
  the whole session, or schedule with `setExactAndAllowWhileIdle()`. That method cannot fire
  "more than once per nine minutes, per app" (Doze doc).
- Wear guidance, "Conserve power and battery"
  (https://developer.android.com/training/wearables/apps/power, updated 2026-10-15): "in health &
  fitness apps, long-running workouts don't need a wakelock. Use the processor's awake time when
  receiving callbacks from the sensor API". INFERENCE: on the watch, keep the CLAUDE.md
  `PARTIAL_WAKE_LOCK` behind a config flag. Measure with `adb shell dumpsys batterystats` and
  `dumpsys sensorservice` (the latter is named on that page) whether the Samsung trackers keep
  delivering with the screen off without it. The 445 mAh battery makes an all-night wake lock
  costly.

### 2.5 Watch: `health` FGS started from the watch UI, plus Ongoing Activity

Manifest (FACT per §1.2, §1.5; Ongoing Activity per below):

```xml
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_HEALTH" />
<uses-permission android:name="android.permission.health.READ_HEART_RATE" />
<uses-permission android:name="android.permission.BODY_SENSORS" android:maxSdkVersion="35" />
<uses-permission android:name="android.permission.ACTIVITY_RECOGNITION" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.WAKE_LOCK" />
<!-- + Samsung READ_ADDITIONAL_HEALTH_DATA, see Samsung SDK notes -->
<service android:name=".sensing.HealthTrackingForegroundService"
    android:exported="false"
    android:foregroundServiceType="health" />
```

"What else" for later phases (INFERENCE from §2.1): add `mediaPlayback` if the watch plays
prompts (WATCH_WRIST_SPEAKER) and `microphone` + `RECORD_AUDIO` for wrist-mic streaming. Both are
deferred by owner item 21. Data Layer use itself needs no FGS type.

Ongoing Activity (FACT, https://developer.android.com/training/wearables/notifications/ongoing-activity,
updated 2026-10-23):
- Dependency: `androidx.wear:wear-ongoing:1.1.0` (also the latest on Google Maven,
  `maven-metadata.xml` `<release>1.1.0</release>`, lastUpdated 2025-09-10).
- Steps: `NotificationCompat.Builder(...).setOngoing(true)` →
  `OngoingActivity.Builder(applicationContext, NOTIFICATION_ID, notificationBuilder)
  .setAnimatedIcon(...).setStaticIcon(...).setTouchIntent(pendingIntent).build()` →
  `ongoingActivity.apply(applicationContext)` → `startForeground(NOTIFICATION_ID, notificationBuilder.build())`.
- "Static icon ... If neither is set, an exception is thrown."
- "Using an `OngoingActivity` or Live Update in these scenarios is a requirement under Wear OS App
  Quality guidelines WO-V4." The page also says Live Update is the Wear OS 7+ alternative. From
  "Always-on apps" (https://developer.android.com/training/wearables/always-on): "For backward
  compatibility, continue to support Ongoing Activity on devices that run Wear OS 6 or lower."
- Always-on, same page: "On Wear OS 6 and higher, if an app targets SDK 36 or newer, it is
  considered always-on. The display is dimmed, but the application continues running and remains
  visible. (Updates may be as infrequent as once per minute.)" Ongoing Activity also prevents the
  return to the watch face. INFERENCE: give the nap screen a near-black ambient layout, since it
  stays lit in a dark bedroom.

### 2.6 Starting an FGS from a BroadcastReceiver or the background

- FACT (restrictions-bg-start): the exemption list. Relevant entries: "Your app transitions from a
  user-visible state, such as an activity"; "The user performs an action on a UI element related
  to your app ... notification, widget, or activity"; "Your app invokes an exact alarm to complete
  an action that the user requests"; "The user turns off battery optimizations for your app";
  `BOOT_COMPLETED` (with type limits). A plain broadcast receiver, and a Data Layer message, are
  **not** on the list. Even when BFSL is exempt, while-in-use types (microphone, body-sensor
  READ_* permissions) still need one of the narrower while-in-use exemptions: "A system component
  starts the service", "interacting with a notification", a `PendingIntent` "sent from a
  different, visible app", and so on.
- FACT (AOSP `ActiveServices.java:8447-8466`): callers with app id `ROOT_UID`, `SYSTEM_UID`,
  `NFC_UID` or `SHELL_UID` get `REASON_SYSTEM_UID` for while-in-use. `:8743-8748`: the BFSL check
  starts from that while-in-use result (`int ret = allowWhileInUse;`). So
  `adb shell am start-foreground-service -n <pkg>/<service>` (help text
  `ActivityManagerShellCommand.java:4414`) is allowed from the background with while-in-use.
  INFERENCE: useful for HIL scripts. It does **not** extend to a receiver inside our app, because
  there the caller of `startForegroundService()` is our own UID.
- INFERENCE for the design (matches owner item 16): neither device should start an FGS from a
  receiver or from Data Layer callbacks. The user taps Start on the watch and on the phone, both
  UIs visible. Receivers and listeners only signal the already-running service. The background
  service-start limits do not bite then: an app "is considered to be in the foreground if ... It
  has a foreground service" (https://developer.android.com/about/versions/oreo/background).

---

## 3. Broadcasts and the ADB `SimulationBroadcastReceiver`

### 3.1 Does `adb shell am broadcast -a SOME_ACTION` (no `-p`/`-n`) reach a manifest receiver? **No** (FACT)

- Docs (Background Execution Limits, https://developer.android.com/about/versions/oreo/background,
  updated 2026-03-03): "Apps that target Android 8.0 or higher can no longer register broadcast
  receivers for implicit broadcasts in their manifest unless the broadcast is restricted to that
  app specifically. An implicit broadcast is a broadcast that does not target a specific component
  within an app." Also: "Apps can continue to register for explicit broadcasts in their
  manifests." "Apps can use `Context.registerReceiver()` at runtime to register a receiver for any
  broadcast, whether implicit or explicit." "Broadcasts that require a signature permission are
  exempted from this restriction".
- AOSP `services/core/java/com/android/server/am/BroadcastSkipPolicy.java:347-354`:
  `disallowBackgroundStart()` is true when `getComponent() == null && getPackage() == null`,
  `FLAG_RECEIVER_INCLUDE_BACKGROUND` is not set, and the required permission is not a signature
  permission. `:229-247` then skips with "Background execution not allowed: receiving ...". The
  start mode comes from `ActivityManagerService.java:6360-6370` / `:6279-6286`. It is called with
  `alwaysRestrict=true`, and "Apps that target O+ are always subject to background check". So this
  happens **even when our app is running**.
- AOSP `BroadcastController.java:921-922`: "By default broadcasts do not go to stopped apps"
  (`FLAG_EXCLUDE_STOPPED_PACKAGES`).

Ways to make it reach the receiver (FACT, `frameworks/base/core/java/android/content/Intent.java`
`parseCommandArgs`):
- `-p <package>` → `intent.setPackage()` (`:8877-8884`), which is no longer implicit.
- `-n <package>/<.Receiver>` → `setComponent()` (`:8866-8875`).
- `--receiver-include-background` → `FLAG_RECEIVER_INCLUDE_BACKGROUND` (`:8970-8971`; the flag is
  `@hide @SystemApi`, `0x01000000`, `:7649-7660`).
- `--include-stopped-packages` → `FLAG_INCLUDE_STOPPED_PACKAGES` (`:8904-8905`), for a freshly
  installed app that was never launched. INFERENCE: easier to just launch the app once.

Corrected CLAUDE.md §6 command (INFERENCE, assumes applicationId `com.opentdi`):

```
adb -s <WATCH_SERIAL> shell am broadcast -a com.opentdi.ACTION_SIMULATE_BIOSIGNALS -p com.opentdi --es mode FAST_HYPNAGOGIA
```

### 3.2 Context-registered receivers and `RECEIVER_EXPORTED` (FACT)

- Android 14 behavior changes: "Apps and services that target Android 14 (API level 34) or higher
  and use context-registered receivers are required to specify a flag to indicate whether or not
  the receiver should be exported to all other apps on the device: either `RECEIVER_EXPORTED` or
  `RECEIVER_NOT_EXPORTED`, respectively. This requirement helps protect apps from security
  vulnerabilities by leveraging the features for these receivers introduced in Android 13." The
  exception: a receiver registered "only for system broadcasts ... shouldn't specify a flag".
- Broadcasts overview (https://developer.android.com/develop/background-work/background-tasks/broadcasts,
  updated 2026-10-24): "If this receiver is listening for broadcasts sent from the system or from
  other apps—even other apps that you own—use the `RECEIVER_EXPORTED` flag." Use
  `ContextCompat.registerReceiver(context, receiver, filter, permission, scheduler, receiverFlags)`
  (androidx.core 1.9.0+).
- AOSP `BroadcastSkipPolicy.java:580-590`: a non-exported registered receiver is skipped with
  "Exported Denial" unless `checkComponentPermission()` passes. `ActivityManager.java:5212-5215`:
  only `ROOT_UID`/`SYSTEM_UID` "can access unexported components"; shell (uid 2000) cannot. So an
  `adb shell am broadcast` to a context-registered receiver needs `RECEIVER_EXPORTED`.
- Android 16: broadcast `priority` ordering "across different processes won't be guaranteed"
  (broadcasts overview). This does not affect a single receiver.

### 3.3 Restrict the receiver to adb with `android:permission="android.permission.DUMP"` (FACT)

- Shell holds DUMP: AOSP `frameworks/base/packages/Shell/AndroidManifest.xml:164`
  `<uses-permission android:name="android.permission.DUMP" />` (same line on `refs/heads/main`).
  The package is `android:sharedUserId="android.uid.shell"` (`:23`). Shell itself protects its own
  exported `.HeapDumpReceiver` with `android:permission="android.permission.DUMP"` (`:1085-1088`),
  the same pattern we would use.
- DUMP is `signature|privileged|development` (`core/res/AndroidManifest.xml:5168-5169`); the ref
  says "Not for use by third-party applications."
- Enforcement for manifest receivers: `BroadcastSkipPolicy.java:133-145` checks
  `info.activityInfo.permission` against the sender UID (`checkComponentPermission`). For
  registered receivers: `:387-396` (`filter.requiredPermission`).
- Who else could send (INFERENCE from the protection level and §1.4): root/system, privileged
  apps allowlisted for DUMP, and any app that declares DUMP **and** was granted it through
  `adb shell pm grant` (the `development` flag; `PermissionManagerServiceImpl.java:1411` accepts
  `isDevelopment()`). All of these need adb or system access, which is acceptable for a test hook.

Recommended receiver (INFERENCE; put it in `wear/src/debug/AndroidManifest.xml` so release builds
don't ship it):

```xml
<receiver android:name=".sim.SimulationBroadcastReceiver"
    android:exported="true"
    android:permission="android.permission.DUMP">
  <intent-filter>
    <action android:name="com.opentdi.ACTION_SIMULATE_BIOSIGNALS" />
  </intent-filter>
</receiver>
```

Alternative: register it from inside the running watch FGS with
`ContextCompat.registerReceiver(ctx, r, filter, "android.permission.DUMP", null, ContextCompat.RECEIVER_EXPORTED)`.
Implicit broadcasts reach it without `-p`, and it only exists while a session runs, which is
when injection makes sense. Per owner item 3, it only exercises the algorithm and audio loop, not
the real sensors.

---

## 4. Wearable Data Layer API (`play-services-wearable`)

### 4.1 Which apps can talk to each other: CRITICAL (FACT, verbatim)

"Overview of Data Layer API" (https://developer.android.com/training/wearables/data/overview,
updated 2026-10-23), section "Security of communications":

> "Google Play services enforces the following restrictions to provide more secure communication
> between the app installed on a Wear OS device and the same app installed on a nearby handheld
> device:
> - The package name must match across devices.
> - The signature of the package must match across devices.
>
> No other apps have access to the data regardless of connection type."

Supporting facts:
- MessageClient ref: "A message is private to the application that created it and accessible only
  by that application on other nodes."
- "Package and distribute Wear OS apps" (https://developer.android.com/training/wearables/packaging):
  "If you have an existing mobile app, verify that you have used the same package name for your
  Wear OS app."
- "Package name" means the application ID. "Configure the app module"
  (https://developer.android.com/build/configure-app-module): "some Android APIs use the term
  'package name' in their method names and parameter names. This is actually your application
  ID." Also "Safely adjust the namespace independent of the application ID", and "If the
  application ID is not explicitly defined ... it automatically takes on the same value as the
  namespace."
- Debug signing: "Android Studio automatically signs your app with a debug certificate ... creates
  the debug keystore and certificate in `$HOME/.android/debug.keystore`"
  (https://developer.android.com/studio/publish/app-signing).

INFERENCE / action items:
- Set `applicationId = "com.opentdi"` explicitly in **both** `:wear` and `:mobile`. Keep
  `namespace = "com.opentdi.wear"` / `"com.opentdi.mobile"`. If CLAUDE.md's module packages
  become application IDs, the apps will not see each other's messages.
- Any `applicationIdSuffix` (e.g. `.debug`) must be identical on both modules, or left out.
- Both APKs must be signed with the same key. Locally, one machine's debug keystore covers both.
  On CI, or on a second developer machine, use a shared keystore for both modules.

### 4.2 Setup and versions (FACT)

- Dependency on the overview page: `implementation("com.google.android.gms:play-services-wearable:20.0.1")`.
  Google Maven metadata: `<release>20.0.1</release>`, lastUpdated 2026-04-28. Release notes
  (https://developers.google.com/android/guides/releases), April 28, 2026: "Vulnerability fix. Apps
  targeting API level 37 or later are recommended to use this SDK version at a minimum." The
  Nov 21, 2024 notes for v19.0.0: "Removed an unsupported `sendMessage()` API variant that included a
  `MessageOptions` parameter." So there is no message priority option, and you should not copy old
  samples that use one.
- Availability check before use (overview): `GoogleApiAvailability.getInstance().checkApiAvailability(client).await()`,
  catching `AvailabilityException`.
- "Wearable API clients, such as `DataClient` and `MessageClient`, are inexpensive to create ...
  recreate them as you need them"; "By default, callbacks to listeners are made on the app's main UI
  thread" unless a `Looper` is set via `WearableOptions` (overview).
- Transport: Bluetooth when connected, otherwise "Over an available network ... using a network
  node on Google's servers as an intermediary"; "Assume that data transmitted using Data Layer may
  at some point use Google-owned servers." Cloud traffic "is end-to-end encrypted" (overview).
  INFERENCE: say this in the privacy note of the README.
- `kotlinx-coroutines-play-services`: Maven Central `<release>1.11.0</release>` (lastUpdated
  2026-05-08). API in `integration/kotlinx-coroutines-play-services/src/Tasks.kt`:
  `public suspend fun <T> Task<T>.await(): T` (`:103`), "Awaits the completion of the task without
  blocking a thread. This suspending function is cancellable."; `await(cancellationTokenSource)`
  (`:117`); `asDeferred()` (`:42`); `Deferred<T>.asTask()` (`:15`). Package `kotlinx.coroutines.tasks`.
  The events doc's examples use `.await()` from coroutines, and `runBlocking { ... await() }` only
  off the main thread inside a `WearableListenerService`. INFERENCE: pin its version to the
  project's `kotlinx-coroutines-core` version.

### 4.3 MessageClient (FACT; ref updated 2024-11-21)

- "A message is considered successful if it has been queued for delivery to the specified node.
  A message will only be queued if the specified node is connected. A `DataClient` should be used
  for messages to nodes which are not currently connected" (MessageClient ref,
  https://developers.google.com/android/reference/com/google/android/gms/wearable/MessageClient).
- `Task<Integer> sendMessage(String nodeId, String path, byte[] data)`: `data` is a "small array of
  information to pass to the target node. Generally not larger than 100k"; "A successful result
  doesn't guarantee delivery."
- `Task<byte[]> sendRequest(String nodeId, String path, byte[] data)`: returns "the byte[] payload
  received from the remote node ... If a response is not received within 1 minute, the returned Task
  completes with a failure." The receiver implements `MessageClient.RpcService` /
  `WearableListenerService.onRequest(nodeId, path, request): Task<byte[]>`; manifest action
  `com.google.android.gms.wearable.REQUEST_RECEIVED`.
- "Choose a client type" (https://developer.android.com/training/wearables/data/client-types,
  where `/data/messages` now redirects): "The `sendMessage()` method is a best effort to deliver to
  the remote node, and it doesn't contain any built-in retry mechanism. If the target device
  disconnects before the network transfer starts, the method returns `TARGET_NODE_NOT_CONNECTED`."
  `WearableStatusCodes.TARGET_NODE_NOT_CONNECTED` = 4000; `DUPLICATE_LISTENER` = 4001.
- Live listeners: `addListener(listener, uri, filterType)`. "The `uri` follows the rules of the
  <data> element ... The path is ignored if a URI host is not specified. The host should be an
  Android Wear node ID, or * to match any node", e.g. `wear://*/<path>`. `FILTER_LITERAL` = 0,
  `FILTER_PREFIX` = 1. "Listeners will be called on the main thread, or the looper set in
  `Wearable.WearableOptions`." "Calls to this method should be balanced with `removeListener`".
- Manifest action constant: `ACTION_MESSAGE_RECEIVED` = `"com.google.android.gms.wearable.MESSAGE_RECEIVED"`.

INFERENCE for OpenTDI triggers (owner item 17): send `/opentdi/hypnagogia_trigger` with
`sendRequest` and treat the reply as the ACK. Retry with backoff on failure or timeout, and log
send and ACK times to CSV on both sides. Messages are not persisted (client table: "No persistence
and no retry"), so the watch must also log the trigger locally in case the phone is unreachable.

### 4.4 `WearableListenerService` manifest and filters (FACT)

From the WearableListenerService ref
(https://developers.google.com/android/reference/com/google/android/gms/wearable/WearableListenerService,
updated 2026-04-28), "Example manifest entry", where "android:pathPrefix is optional":

```xml
<service android:name=".MyWearableListenerService" android:exported="true" >
  <intent-filter>
      <!-- listeners receive events that match the action and data filters -->
      <action android:name="com.google.android.gms.wearable.DATA_CHANGED" />
      <action android:name="com.google.android.gms.wearable.MESSAGE_RECEIVED" />
      <action android:name="com.google.android.gms.wearable.REQUEST_RECEIVED" />
      <action android:name="com.google.android.gms.wearable.CAPABILITY_CHANGED" />
      <action android:name="com.google.android.gms.wearable.CHANNEL_EVENT" />
      <data android:scheme="wear" android:host="*" android:pathPrefix="/prefix" />
  </intent-filter>
</service>
```

- "The life-cycle of this service is managed by Android Wear. This service will be bound to and
  events delivered as a result of various Android Wear events" (ref). "All the preceding events are
  executed in a background thread, not on the main thread." "Declare an intent filter ... This
  declaration lets the system bind your service as needed."
  (https://developer.android.com/training/wearables/data/events, updated 2026-10-23)
- Filter rules (events doc): "You can also match a literal path or path prefix. To do this, you
  must specify a wildcard or specific host. Otherwise, the system ignores the path you specify."
  "If no scheme is specified for the intent filter, the system ignores all the other URI
  attributes." "If no host is specified for the filter, the system ignores all the path
  attributes."
- `CapabilityClient.ACTION_CAPABILITY_CHANGED`: "Capability events do not support filtering by
  host, but can be filtered by path." (CapabilityClient ref)
- Battery (events doc): "A `WearableListenerService` is registered in the app's manifest and can
  launch the app if it is not already running. If you only need to listen for events when your app
  is already running ... don't use a `WearableListenerService`. Instead, register a live listener."
  Wear power doc: "Wait until your app is active before you set up a listener using
  `WearableListenerService`."

INFERENCE for OpenTDI: during a nap both apps already run an FGS, so register live listeners
(`MessageClient.addListener`, `addRpcService`, `ChannelClient.registerChannelCallback`) inside the
FGS on a background `Looper` from `WearableOptions`. Keep a manifest `WearableListenerService`,
filtered by `pathPrefix="/opentdi/"`, only for a phone→watch "session start requested" message,
which the watch can only show as a notification (§2.6, no background FGS start).

### 4.5 NodeClient and reconnection (FACT)

- `NodeClient.getConnectedNodes()`: "Gets a list of nodes to which this device is currently
  connected, either directly or indirectly via a directly connected node. The returned list will
  not include the `local node`." (NodeClient ref)
- Reachable vs nearby: "A device is considered nearby if it can be connected directly through
  Bluetooth, without using the cloud." "Under certain circumstances, devices may require up to 4
  minutes to re-establish a connection", for example after Wear OS device inactivity or the
  handheld's Doze (https://developer.android.com/training/wearables/data/discover-devices, updated
  2026-09-01). `Node.isNearby()` is named in the events doc.
- INFERENCE: a mid-nap Bluetooth drop can mean minutes without delivery. That is one more
  reason for watch-side CSV logging and the ACK/retry in §4.3. The phone should show "watch link
  lost" after a few missed telemetry batches.

### 4.6 CapabilityClient and `res/values/wear.xml` (FACT)

From "Standalone versus non-standalone" (https://developer.android.com/training/wearables/apps/standalone-apps,
updated 2026-10-23): "For the app corresponding to each device type, either watch or phone, specify
a unique string for the capability name in the `res/values/wear.xml` file."

```xml
<!-- mobile/src/main/res/values/wear.xml -->
<resources xmlns:tools="http://schemas.android.com/tools"
    tools:keep="@array/android_wear_capabilities">
  <string-array name="android_wear_capabilities">
    <item>verify_remote_example_phone_app</item>
  </string-array>
</resources>
```

The Wear module uses a different item (the doc's example is `verify_remote_example_wear_app`).
INFERENCE: use `opentdi_phone` / `opentdi_watch`.

CapabilityClient ref: `getCapability(String capability, int nodeFilter)` with `FILTER_ALL` = 0 or
`FILTER_REACHABLE` = 1; "The local node will never be returned in the set of nodes";
`addListener(listener, capability)`; `addLocalCapability` / `removeLocalCapability` at runtime.
Wear power doc: "Don't use the Data Layer API to periodically ping the watch ... Instead, use
`CapabilityClient` to detect device availability and capabilities, or only send messages when
state changes."

### 4.7 ChannelClient (FACT; refs updated 2024-11-21)

- "Channels are bidirectional ... Once a channel is established, the API for the initiator and
  receiver are identical." "When the remote node disconnects, all existing channels will be
  closed." (ChannelClient ref)
- `openChannel(nodeId, path): Task<Channel>`: "This call involves a network round trip, so may be
  long running"; returns "the newly created channel, or `null`, if the connection couldn't be
  opened."
- `getOutputStream(channel)`: "Data written to this stream is buffered. If you wish to send the
  current data without waiting for the buffer to fill up, `flush` the stream." `getInputStream`:
  "reads may block for a long time." Each may be used once per channel, and they are mutually
  exclusive with `sendFile`/`receiveFile` respectively.
- `sendFile(channel, uri[, startOffset, length])` / `receiveFile(channel, uri, append)`:
  "implemented more efficiently ... done in a background process owned by Google Play services";
  completion is reported by `ChannelCallback.onOutputClosed` / `onInputClosed`. "The channel should
  not be immediately closed after calling this method."
- `close(channel[, errorCode])`: close the streams first, or "both streams will throw
  `ChannelIOException`".
- `registerChannelCallback(callback)`: "Callbacks will be made on the main thread, or the looper set
  in `Wearable.WearableOptions`." Manifest action `ACTION_CHANNEL_EVENT`
  (`com.google.android.gms.wearable.CHANNEL_EVENT`).
- `ChannelClient.ChannelCallback`: `onChannelOpened(channel)`,
  `onChannelClosed(channel, closeReason, appSpecificErrorCode)`, `onInputClosed(...)`,
  `onOutputClosed(...)`. Close reasons: `CLOSE_REASON_NORMAL` = 0, `CLOSE_REASON_DISCONNECTED` = 1,
  `CLOSE_REASON_REMOTE_CLOSE` = 2, `CLOSE_REASON_LOCAL_CLOSE` = 3, `CLOSE_REASON_CONNECTION_TIMEOUT` = 4
  (ChannelCallback ref).

### 4.8 Which client for the 1 Hz telemetry stream

Official guidance (FACT):

| | Persistence | >100 KB | Offline | Source |
|---|---|---|---|---|
| DataClient | "Data is persisted indefinitely" and "backed up to the cloud" | Yes (Assets) | Yes | client-types table |
| MessageClient | "No persistence and no retry" | No | No | same |
| ChannelClient | "No persistence (connection-oriented)" | Yes | No | same |

- Channel use cases include "Transfer streamed data, such as voice data from the microphone", and
  "You're responsible for managing the data flow and keeping data consistent." (client-types)
- Messages "should generally contain ephemeral, small payloads" (MessageClient ref).
- Power (Wear power doc): "each transmission uses some power. In particular, if you use this API
  to send data, your app must wake up to receive the data. For these reasons, be conservative with
  your usage of this API." "Transmit state changes instead of configuring rapid updates." "Batch
  any related operations, to maximize the time that your app's process is idle." Diagnose with
  `adb shell dumpsys activity service WearableService` (RpcService = MessageClient paths and
  frequency).
- DataClient for the stream (forbidden by owner item 17) would be the worst fit: every 1 Hz
  sample would become a persisted, cloud-backed data item.

Latency and battery numbers: **none are published** in the pages above, so I cannot quote any.
What follows is INFERENCE:
- Wake-ups and radio activity scale with the number of transmissions, not bytes, at these sizes.
  A per-second summary (`BiosignalPacket` + `jerkEvent`/`limpEvent`/`stillnessScore`, owner item
  8) is on the order of 100-300 bytes. A 5 s batch is about 1-1.5 KB, far below the "generally not
  larger than 100k" guideline. A 60 s batch still fits.
- **Batched MessageClient** (recommended for Phase 2, the smallest working option): send every N
  seconds (configurable, default about 5 s) to the capability node; drop on
  `TARGET_NODE_NOT_CONNECTED`. Nothing is lost scientifically, because owner item 13 already logs
  everything to CSV on the watch. Dashboard latency is about N s plus transport. There is no stream
  lifecycle to manage.
- **ChannelClient stream**: one channel per session (`/opentdi/sensor_stream`), length-prefixed
  frames, `flush()` per batch. Writes are buffered, so wake-ups follow the flush cadence; the
  battery profile at the same cadence is likely similar to batched messages. It costs reconnect
  logic, since channels close on every disconnect (`CLOSE_REASON_DISCONNECTED`), and an
  `openChannel` round trip. Use it when per-second latency matters or for audio (wrist mic,
  deferred).
- Detection runs on the watch (owner items 7-8), so telemetry latency never delays the trigger.
  Trigger latency depends only on the single `sendRequest`.
- End of session: copy the watch CSV to the phone with `ChannelClient.sendFile` /
  `receiveFile`, a documented efficient path that runs in the Play services process.
- Measure before tuning: log the watch send time and phone receive time per batch. Estimate the
  clock offset from `sendRequest` round trips. Compare `dumpsys batterystats` for N = 1 s, 5 s
  and 30 s on a 30-minute screen-off run.

---

## 5. Corrections to CLAUDE.md found here

1. §2 "Wear OS 6 Foreground Service health permissions (`FOREGROUND_SERVICE_HEALTH`,
   `BODY_SENSORS`, `BODY_SENSORS_BACKGROUND`)". Wrong for targetSdk 36: use `READ_HEART_RATE`
   (and `READ_HEALTH_DATA_IN_BACKGROUND` only if needed), with the legacy pair at
   `maxSdkVersion="35"` (§1.1-1.2). For the FGS type, any of `ACTIVITY_RECOGNITION` /
   `HIGH_SAMPLING_RATE_SENSORS` / `READ_HEART_RATE` satisfies it (§1.5).
2. §6 `pm grant` list: `BODY_SENSORS` and `BODY_SENSORS_BACKGROUND` fail on API 36 when declared
   with `maxSdkVersion="35"` ("has not requested permission"). Normal permissions cannot be
   granted and need no grant. Use `adb install -g` (§1.4).
3. §6 sim command: without `-p com.opentdi` (or `-n`), the broadcast is dropped for a manifest
   receiver (§3.1).
4. §1 and §3 package names: `com.opentdi.wear` / `com.opentdi.mobile` may be namespaces but must
   not be different application IDs, or the Data Layer link fails (§4.1).
5. §3 `HealthTrackingForegroundService` "Acquires `PARTIAL_WAKE_LOCK`". Make it configurable and
   measure; Wear guidance says workouts don't need one (§2.4).

## 6. To verify on the real devices (not checkable from docs or AOSP)

- [ ] `replace_body_sensor_permission_enabled` is in effect on SM-L500. Check
  `adb shell dumpsys package com.opentdi | grep -i -E "heart|body_sensors"` after install, and
  that Samsung trackers work with only `READ_HEART_RATE` + Samsung's permission (owner item 6).
- [ ] `pm grant com.opentdi android.permission.health.READ_HEART_RATE` succeeds on the watch.
- [ ] Phone: after 30-60 min screen-off, `adb shell dumpsys activity services com.opentdi` still
  shows `mAllowWiu_noBinding` not DENIED, and a test recording has non-zero RMS. Also check with
  `adb shell dumpsys deviceidle force-idle`.
- [ ] Phone and watch: the FGS's `PARTIAL_WAKE_LOCK` stays active in forced Doze
  (`adb shell dumpsys power`), and the 180 s countdown fires on time with the screen off.
- [ ] Watch: Samsung trackers keep streaming with the screen off for 10+ min with and without a
  wake lock (owner item 9), and the Ongoing Activity icon shows on the watch face.
- [ ] One UI-specific battery managers ("Sleeping apps" and similar) do not kill either FGS.
  Not documented by Google; if needed, ask the user to set battery to Unrestricted. That is also a
  BFSL exemption (§2.6).
- [ ] Data Layer: messages flow between the two debug builds (same applicationId + keystore), and
  record reconnect time after a forced Bluetooth off/on mid-session (docs: up to 4 min).

## 7. Sources (all fetched 2026-10-27)

Android developer docs (page "Last updated" date in brackets):
- Behavior changes, Android 16 targeting: https://developer.android.com/about/versions/16/behavior-changes-16 [2026-10-23]
- Behavior changes, Android 16 all apps: https://developer.android.com/about/versions/16/behavior-changes-all [2026-10-23]
- Wear OS 6 changes: https://developer.android.com/training/wearables/versions/6/changes [2026-03-05]
- Health Services permissions: https://developer.android.com/health-and-fitness/guides/health-services/permissions [2026-01-19]
- Health Services active data: https://developer.android.com/health-and-fitness/guides/health-services/active-data [2026-10-23]
- Manifest.permission: https://developer.android.com/reference/android/Manifest.permission [2026-10-09]
- HealthPermissions: https://developer.android.com/reference/android/health/connect/HealthPermissions [2026-08-03]
- Sensors overview: https://developer.android.com/develop/sensors-and-location/sensors/sensors_overview [2026-10-23]
- FGS types: https://developer.android.com/develop/background-work/services/fgs/service-types [2026-10-23]
- FGS background start restrictions: https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start [2026-10-23]
- FGS declare / launch / changes / user stopping: .../fgs/declare, .../fgs/launch, .../fgs/changes, .../fgs/handle-user-stopping [all 2026-10-23]
- Behavior changes Android 14: https://developer.android.com/about/versions/14/behavior-changes-14 [2026-10-23]
- Behavior changes Android 15 targeting / all: https://developer.android.com/about/versions/15/behavior-changes-15, .../behavior-changes-all [2026-10-23]
- Notification permission: https://developer.android.com/develop/ui/views/notifications/notification-permission [2026-10-23]
- Background execution limits: https://developer.android.com/about/versions/oreo/background [2026-03-03]
- Broadcasts overview: https://developer.android.com/develop/background-work/background-tasks/broadcasts [2026-10-24]
- Doze and App Standby: https://developer.android.com/training/monitoring-device-state/doze-standby [2026-10-22]
- Set a wake lock: https://developer.android.com/develop/background-work/background-tasks/awake/wakelock/set [2026-09-15]
- Handler: https://developer.android.com/reference/android/os/Handler [2026-08-03]
- Wear: Conserve power: https://developer.android.com/training/wearables/apps/power [2026-10-15]
- Wear: Ongoing activity: https://developer.android.com/training/wearables/notifications/ongoing-activity [2026-10-23]
- Wear: Always-on apps: https://developer.android.com/training/wearables/always-on [2026-10-23]
- Wear: Standalone apps (wear.xml): https://developer.android.com/training/wearables/apps/standalone-apps [2026-10-23]
- Wear: Packaging: https://developer.android.com/training/wearables/packaging [2026-10-22]
- Data Layer overview / client types / events / discover devices / sync:
  https://developer.android.com/training/wearables/data/overview, .../client-types, .../events, .../discover-devices, .../sync [2026-10-23; discover 2026-09-01]
- Configure the app module: https://developer.android.com/build/configure-app-module [2026-02-26]
- App signing: https://developer.android.com/studio/publish/app-signing [2026-03-06]

Google Play services references (https://developers.google.com/android/reference/com/google/android/gms/wearable/...):
MessageClient, ChannelClient, ChannelClient.ChannelCallback, CapabilityClient, NodeClient [2024-11-21];
WearableListenerService [2026-04-28]; WearableStatusCodes [2024-10-31].
Release notes: https://developers.google.com/android/guides/releases [2026-10-27].

Library versions: https://dl.google.com/dl/android/maven2/com/google/android/gms/play-services-wearable/maven-metadata.xml,
https://dl.google.com/dl/android/maven2/androidx/wear/wear-ongoing/maven-metadata.xml,
https://repo1.maven.org/maven2/org/jetbrains/kotlinx/kotlinx-coroutines-play-services/maven-metadata.xml.
kotlinx-coroutines sources: https://github.com/Kotlin/kotlinx.coroutines (`integration/kotlinx-coroutines-play-services/src/Tasks.kt`,
`ui/kotlinx-coroutines-android/src/HandlerDispatcher.kt`, branch `master`).

AOSP (`refs/tags/android-16.0.0_r1` unless noted):
- platform/frameworks/base: `packages/Shell/AndroidManifest.xml`, `core/res/AndroidManifest.xml`,
  `data/etc/platform.xml`, `core/java/android/permission/flags.aconfig`,
  `core/java/android/app/ForegroundServiceTypePolicy.java` (+ `refs/tags/android-14.0.0_r1`),
  `core/java/android/app/ActivityManager.java`, `core/java/android/app/ProcessStateEnum.aidl`,
  `core/java/android/content/Intent.java`,
  `services/core/java/com/android/server/am/{ActiveServices,ServiceRecord,OomAdjuster,BroadcastSkipPolicy,BroadcastController,ActivityManagerService,ActivityManagerShellCommand}.java`,
  `services/core/java/com/android/server/pm/PackageManagerShellCommand.java`,
  `services/core/java/com/android/server/pm/permission/PermissionManagerServiceImpl.java`,
  `services/core/java/com/android/server/power/PowerManagerService.java`.
- platform/packages/modules/HealthFitness: `apk/HealthPermissionsManifest.xml`,
  `service/java/com/android/server/healthconnect/permission/HealthConnectPermissionHelper.java`.
- Not fetched: `refs/tags/android-16.0.0_r1` of `frameworks/proto_logging` (404 at the path tried);
  process-state values were taken from `ProcessStateEnum.aidl` instead. The URL
  `https://developer.android.com/training/wearables/apps/visible` returned 404; the "Keep your app
  visible" content is the `always-on` page.
