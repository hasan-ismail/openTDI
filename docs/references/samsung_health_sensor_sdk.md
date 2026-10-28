# Samsung Health Sensor SDK: reference for OpenTDI (Galaxy Watch8 Classic, Wear OS 6 / API 36)

Researched 2026-10-27. Every Samsung page below was fetched on that date with `curl` and its HTML converted to text,
so the quotes come from the live pages, not from a summariser.

Labels: **FACT** = stated directly by the cited source. **INFERENCE** = my reasoning; not stated by a source.
**VERIFIED** (API signatures) = matches the official javadoc at the cited URL. **UNVERIFIED** = not confirmed by an official source.

The SDK AAR is behind a Samsung-account login. It was **not** downloaded, unpacked or inspected, and no third-party
copies of it or of its files were read. So anything that only the binary can answer (its `AndroidManifest.xml`,
consumer ProGuard rules, minSdk, transitive dependencies, internal constructors) is marked UNVERIFIED.

Base URL for Samsung pages: `https://developer.samsung.com/health/sensor/` (shortened below to `…/sensor/`).
Base URL for javadoc: `https://developer.samsung.com/health/sensor/api-reference/com/samsung/android/service/health/tracking/` (shortened to `…/tracking/`).

---

## 0. Key findings, and where they conflict with CLAUDE.md or the owner requirements

| # | Finding | Label / source | Impact |
|---|---|---|---|
| 1 | Latest SDK is **v1.4.1** (2025-08-20, a "patch version [that] improves operational stability"). **v1.4.0** (2025-07-31) added `EDA_CONTINUOUS` and the new Android 16 permission rules. | FACT, `…/sensor/release-note.html`; overview download button reads "Samsung Health Sensor SDK v1.4.1 (70.8 KB)" (`…/sensor/overview.html`) | Use 1.4.1. |
| 2 | SDK 1.4.0 needs **Health Sensor Service 1.6.5 or above**. Samsung renamed "Health Platform" to "Health Sensor Service"; older watch software may still show "Health Platform". | FACT, `…/sensor/api-reference/overview-summary.html`; `…/sensor/guide/developer-mode.html` | Requirement A4 is confirmed. Use both names in the UI and docs. |
| 3 | `SDK_POLICY_ERROR` is **not** a `HealthTrackerException` error code. It is the enum value `HealthTracker.TrackerError.SDK_POLICY_ERROR`, delivered to `TrackerEventListener.onError()` after `setEventListener()`. `HealthTrackerException` documents only `PACKAGE_NOT_INSTALLED = 0` and `OLD_PLATFORM_VERSION = 1`. | FACT, `…/tracking/HealthTracker.TrackerError.html`, `…/tracking/HealthTrackerException.html`, `…/sensor/api-reference/constant-values.html` | Requirement A2: handle it in the **tracker** listener, not in `onConnectionFailed`. |
| 4 | Permissions for targetSdk 36: `HEART_RATE_CONTINUOUS` needs `android.permission.health.READ_HEART_RATE`, `EDA_CONTINUOUS` needs `com.samsung.android.hardware.sensormanager.permission.READ_ADDITIONAL_HEALTH_DATA`, and **`ACCELEROMETER_CONTINUOUS` needs `ACTIVITY_RECOGNITION`**. | FACT, `…/sensor/guide/permission-request.html` | **Conflict with B6.** The owner wrote that heart rate needs ACTIVITY_RECOGNITION. Samsung ties ACTIVITY_RECOGNITION to the accelerometer, not to heart rate, and B6's list **omits `READ_HEART_RATE`**, which heart rate requires. OpenTDI needs all three. |
| 5 | `BODY_SENSORS` is only the permission for targetSdk ≤ 35. `BODY_SENSORS_BACKGROUND` is not mentioned anywhere in the Samsung SDK docs. | FACT, permission-request page; Android 16 behaviour changes | Drop both from the targetSdk-36 manifest (or cap them with `maxSdkVersion="35"`). The CLAUDE.md §2/§6 lists are outdated. |
| 6 | Accelerometer: **25 Hz** raw X/Y/Z as `ValueKey<Integer>`. Convert to m/s² with `9.81 / (16383.75 / 4.0) * value`. The javadoc says "**Accelerometer data doesn't include gravity.**" | FACT, `…/tracking/data/ValueKey.AccelerometerSet.html`, `…/sensor/guide/data-specifications.html` | Requirement C7's 25 Hz is confirmed. **Risk:** if gravity really is removed, CLAUDE.md's magnitude `M = √(x²+y²+z²)` is linear acceleration around 0, not around 1 g, and wrist orientation (for limp-wrist detection) cannot be read from gravity. Check on hardware (§9). |
| 7 | EDA: 1 Hz, **Galaxy Watch8 series and later only**. With the display on, 1 `EdaSet` point per callback. With the display off, a batch of `EdaSet` points. | FACT, data-specifications page | Requirement C9 is confirmed on paper ("keeps streaming" when the display is off, but **batched**). The owner still requires a hardware check. |
| 8 | **When the display is off, delivery is batched.** Samsung's example: heart rate "normally gives 600 samples every 10 minutes". `flush()` delivers the collected data at once and works for HR, EDA, accelerometer, PPG and skin-temperature continuous trackers. | FACT, `…/sensor/guide/tracking-data.html`, `…/tracking/HealthTracker.html#flush--` | **Biggest design impact.** With the screen off, the watch may hear about HR/EDA up to about 10 minutes late, which breaks real-time N1 detection unless OpenTDI calls `flush()` on a timer (a battery cost Samsung warns about). See §2.3. |
| 9 | Batched heart rate: "IBI values for the complete tracking times are stored in the first data point. The other data points contain NULL." | FACT, data-specifications page; `…/tracking/data/ValueKey.HeartRateSet.html#IBI_LIST` | The parser must accept a null `IBI_LIST` and must not assume per-second IBI. |
| 10 | Multiple continuous trackers may run at once. Only one on-demand tracker at a time. One listener per tracker type. | FACT, `…/sensor/faq.html`; `HealthTracker.setEventListener` javadoc | HR + EDA + accelerometer together is allowed. |
| 11 | Emulator not supported. | FACT, overview, introduction and FAQ pages | Requirement A3 is confirmed. |

| 13 | Samsung: "The developer mode is intended only for testing or debugging your app. It is not intended for app users. **Do not share the developer mode guide with app users.**" | FACT, developer-mode page; app-verification page; process page | **Tension with requirement A1**, which wants the dev-mode steps in the setup docs. Mitigation: write the setup doc for developers building from source and link to Samsung's page instead of copying it (§4). |
| 14 | `hasResolution()` / `resolve()` require `android.permission.INTERNET` and a network connection. | FACT, `…/tracking/HealthTrackerException.html` | Add `INTERNET` to the `:wear` manifest. |

---

## 1. Version and feature support

- **FACT**, release note (`…/sensor/release-note.html`):
  - "1.4.1 - August 20, 2025 — This patch version improves operational stability."
  - "1.4.0 - July 31, 2025 — [New] … `EDA_CONTINUOUS`, `MF_BIA_ON_DEMAND` … `EdaSet`, `MfBiaSet` … [Change] Required permissions to use each HealthTrackerType have been changed for targeting Android 16 … or higher."
  - "1.3.0 - August 22, 2024 — … `ACCELEROMETER_CONTINUOUS`, … `HEART_RATE_CONTINUOUS` …" (the `_CONTINUOUS` / `_ON_DEMAND` names; the old `ACCELEROMETER`, `HEART_RATE` etc. were deprecated).
  - "1.3.0 - September 22, 2024 — Rebranding the SDK name to 'Samsung Health Sensor SDK'."
  - 1.2.0 (2023-08-30) added `HEART_RATE_STATUS`, `IBI_LIST` and `IBI_STATUS_LIST`. 1.3.0 removed `HeartRateSet.HEART_RATE_IBI` and `HeartRateSet.STATUS`.
  - "Target device — Galaxy Watch4 series and later models."
- **FACT**, javadoc: `EDA_CONTINUOUS` "Since: 1.4.0", `ACCELEROMETER_CONTINUOUS` "Since: 1.3.0", `HEART_RATE_CONTINUOUS` "Since: 1.3.0" (`…/tracking/data/HealthTrackerType.html`).
- **INFERENCE:** the version that covers Wear OS 6 / API 36, Galaxy Watch8, `EDA_CONTINUOUS` and `ACCELEROMETER_CONTINUOUS` is **1.4.0 or newer; use 1.4.1**. The 2026-10-27 overview page still offers v1.4.1 as the current download, so nothing newer was published by that date.
- **FACT**, FAQ (`…/sensor/faq.html`): "The supported health tracker types can differ depending on the specific Galaxy Watch model and the watch software version", so check with `getTrackingCapability()` at runtime.

---

## 2. Java API surface for a compile-time stub

The packages are `com.samsung.android.service.health.tracking` and `com.samsung.android.service.health.tracking.data`
(FACT, `…/sensor/api-reference/overview-summary.html`).
Every signature below was read from the javadoc page cited above it.
Members marked **STUB-ONLY** are not in the public API; a stub needs them to be usable in unit tests, but production code must not call them.

### 2.1 `com.samsung.android.service.health.tracking`

```java
// VERIFIED …/tracking/HealthTrackingService.html
public class HealthTrackingService {
    public HealthTrackingService(ConnectionListener connectionListener, android.content.Context context); // since 1.0.0
    public void connectService();
    public void disconnectService();
    public HealthTrackerCapability getTrackingCapability();
    public HealthTracker getHealthTracker(HealthTrackerType healthTrackerType);
    public HealthTracker getHealthTracker(HealthTrackerType healthTrackerType, java.util.Set<PpgType> ppgTypeSet);          // since 1.3.0
    public HealthTracker getHealthTracker(HealthTrackerType healthTrackerType, TrackerUserProfile userProfile);           // BIA/MF-BIA
    public HealthTracker getHealthTracker(HealthTrackerType healthTrackerType, TrackerUserProfile userProfile,
                                          ExerciseType type);                                                             // SWEAT_LOSS
    // getHealthTracker(...) is documented to throw (all unchecked):
    //   IllegalArgumentException      - "if the given healthTrackerType is not proper"
    //   UnsupportedOperationException - "if the given healthTrackerType is not supported on the watch device"
    //   IllegalStateException         - "if the service connection is not available. Call connectService()"
}

// VERIFIED …/tracking/ConnectionListener.html
public interface ConnectionListener {
    void onConnectionSuccess();
    void onConnectionEnded();
    void onConnectionFailed(HealthTrackerException exception); // "It could be related to a permission or remote binding exception."
}

// VERIFIED …/tracking/HealthTrackerException.html and …/sensor/api-reference/constant-values.html
public class HealthTrackerException extends Exception {       // implements Serializable via Throwable
    public static final int PACKAGE_NOT_INSTALLED = 0;         // "Health Sensor Service is not installed."
    public static final int OLD_PLATFORM_VERSION  = 1;         // "Health Sensor Service version is old."
    public int getErrorCode();
    public boolean hasResolution();                            // precondition: network; needs android.permission.INTERNET
    public void resolve(android.app.Activity activity);        // throws IllegalArgumentException (null/invalid input),
                                                               //        IllegalStateException ("if unable to resolve")
    // STUB-ONLY: the javadoc and index list no public constructor. Add e.g. HealthTrackerException(int code) in the stub only.
}

// VERIFIED …/tracking/HealthTrackerCapability.html
public class HealthTrackerCapability {
    public java.util.List<HealthTrackerType> getSupportHealthTrackerTypes(); // throws IllegalStateException if not connected
    public String getVersion();                                               // "Get the SDK version."
}

// VERIFIED …/tracking/HealthTracker.html
public class HealthTracker {
    public void setEventListener(HealthTracker.TrackerEventListener listener); // one listener per HealthTrackerType
    public void unsetEventListener();
    public boolean flush();   // true = flush accepted; false = failed, no listener, or tracker type does not support flush
    public void setExerciseState(ExerciseState state);                        // SWEAT_LOSS only
    public void setExerciseData(DataType type, float[] data, long[] timestamp); // SWEAT_LOSS only

    // VERIFIED …/tracking/HealthTracker.TrackerEventListener.html
    public static interface TrackerEventListener {
        void onDataReceived(java.util.List<DataPoint> data);
        void onFlushCompleted();
        void onError(HealthTracker.TrackerError error);
    }

    // VERIFIED …/tracking/HealthTracker.TrackerError.html (exactly two constants in 1.4.1)
    public static enum TrackerError { PERMISSION_ERROR, SDK_POLICY_ERROR }
}
```

`TrackerError` history (FACT, release note "1.0.0 alpha2"): `LOW_SIGNAL`, `MOVEMENT_DETECTED`, `NOT_WEARING` and `TIME_OUT` were removed and `SDK_POLICY_ERROR` was added. A Kotlin `when` over the two current values is exhaustive.

### 2.2 `com.samsung.android.service.health.tracking.data`

```java
// VERIFIED …/tracking/data/HealthTrackerType.html — all 21 constants, in declaration order (from the "Enum Constant Detail" section)
public enum HealthTrackerType {
    @Deprecated PPG_GREEN,              PPG_CONTINUOUS,            @Deprecated PPG_RED,   @Deprecated PPG_IR,
    PPG_ON_DEMAND,                      @Deprecated HEART_RATE,    HEART_RATE_CONTINUOUS, @Deprecated ECG,
    ECG_ON_DEMAND,                      @Deprecated ACCELEROMETER, ACCELEROMETER_CONTINUOUS,
    @Deprecated BIA,                    BIA_ON_DEMAND,             SWEAT_LOSS,            @Deprecated SPO2,
    SPO2_ON_DEMAND,                     @Deprecated SKIN_TEMPERATURE, SKIN_TEMPERATURE_ON_DEMAND,
    SKIN_TEMPERATURE_CONTINUOUS,        EDA_CONTINUOUS,            MF_BIA_ON_DEMAND
}

// VERIFIED …/tracking/data/DataPoint.html
public class DataPoint {
    public <T> T getValue(ValueKey<T> type);
    public long getTimestamp();   // "A timestamp in milliseconds of the DataPoint." (epoch not stated; see 2.3)
}

// VERIFIED …/tracking/data/ValueKey.html
public class ValueKey<T> {
    public ValueKey();            // the only documented constructor. STUB-ONLY: give the stub a private name field so keys are distinguishable.

    // VERIFIED …/tracking/data/ValueKey.HeartRateSet.html
    public static class HeartRateSet {
        public static final ValueKey<Integer>                 HEART_RATE;        // bpm
        public static final ValueKey<Integer>                 HEART_RATE_STATUS; // since 1.2.0
        public static final ValueKey<java.util.List<Integer>> IBI_LIST;          // ms, since 1.2.0
        public static final ValueKey<java.util.List<Integer>> IBI_STATUS_LIST;   // since 1.2.0
    }
    // VERIFIED …/tracking/data/ValueKey.EdaSet.html (since 1.4.0) — no other fields
    public static class EdaSet {
        public static final ValueKey<Float>   SKIN_CONDUCTANCE; // microsiemens (µS)
        public static final ValueKey<Integer> STATUS;
    }
    // VERIFIED …/tracking/data/ValueKey.AccelerometerSet.html — X/Y/Z only
    public static class AccelerometerSet {
        public static final ValueKey<Integer> ACCELEROMETER_X;
        public static final ValueKey<Integer> ACCELEROMETER_Y;
        public static final ValueKey<Integer> ACCELEROMETER_Z;
    }
    // Other nested sets exist (the stub does not need them): BiaSet, EcgSet, MfBiaSet, PpgSet, SkinTemperatureSet,
    // SpO2Set, SweatLossSet, and the deprecated PpgGreenSet/PpgIrSet/PpgRedSet (…/tracking/data/ValueKey.html).
}

// VERIFIED (only needed to make the other getHealthTracker overloads compile)
public enum PpgType { GREEN, RED, IR }                          // …/tracking/data/PpgType.html
public enum ExerciseType { RUNNING }                            // …/tracking/data/ExerciseType.html
public enum ExerciseState { START, PAUSE, RESUME, STOP }        // …/tracking/data/ExerciseState.html
public enum DataType { STEP_PER_MINUTE }                        // …/tracking/data/DataType.html
public final class TrackerUserProfile {                         // …/tracking/data/TrackerUserProfile.html
    public static class Builder {                               // …/tracking/data/TrackerUserProfile.Builder.html
        public Builder(); public Builder setAge(int age); public Builder setGender(int gender); // gender: 0 female, 1 male
        public Builder setHeight(float height); public Builder setWeight(float weight); public TrackerUserProfile build();
    }
}
```

**Conflict (FACT):** release note 1.2.0 says "The status interface has been added to … `ValueKey.AccelerometerSet.STATUS`", but the 1.4.1 javadoc for `AccelerometerSet` lists only `ACCELEROMETER_X/Y/Z`, and the index has no `AccelerometerSet.STATUS`. **Leave it out of the stub. UNVERIFIED** whether the AAR still contains it.

**Kotlin notes (INFERENCE, consistent with public Kotlin code):** the SDK is Java without nullability annotations, so Kotlin sees platform types. Kotlin overrides in public projects use `onDataReceived(data: MutableList<DataPoint>)` and `onConnectionFailed(e: HealthTrackerException?)`. Examples: Kaist-ICLab/EnPULSE@599cf37 `tracker-library/src/main/java/kaist/iclab/tracker/listener/SamsungHealthSensorInitializer.kt:43,120`, and Samsung's Apache-2.0 EDA sample as copied in M1chaelfmike/SamsungWatchHealthDataMonitorApplication@ef82e7b `…/edatracking/tracking/EDATrackingManager.kt:93,210-212`. Treat every `getValue` result as nullable (see `IBI_LIST` below).



### 2.3 Data semantics

| Tracker | Rate / delivery | Values and units | Status codes | Source |
|---|---|---|---|---|
| `HEART_RATE_CONTINUOUS` | 1 Hz. Display on: 1 `HeartRateSet` point per callback ("retrieved every 1 second"). Display off: batched points. | `HEART_RATE` bpm (`Integer`). `IBI_LIST` ms, "Between 0 and 4 data points are sent along with the heart rate". In a batch, all IBIs are in the **first** point and the rest are NULL. | `HEART_RATE_STATUS`: `1` successful measurement; `0` initial measuring state, or a higher-priority sensor (e.g. BIA) is running; `-2` wearable movement detected; `-3` wearable detached; `-8` PPG signal weak or user moved; `-10` PPG signal too weak or user moving too much; `-999` higher-priority sensor running. `IBI_STATUS_LIST`: `0` normal, `-1` error. | FACT, data-specifications; `ValueKey.HeartRateSet.html`; `HealthTrackerType.html#HEART_RATE_CONTINUOUS` |
| `EDA_CONTINUOUS` | 1 Hz. Display on: 1 `EdaSet` point per callback ("retrieved every 1 second"). Display off: batched `EdaSet` points. **Galaxy Watch8 series and later.** | `SKIN_CONDUCTANCE` µS (`Float`) | `STATUS`: `0` normal; `-5` watch detached from the wrist; `-10` low EDA signal quality | FACT, data-specifications; `ValueKey.EdaSet.html`; `HealthTrackerType.html#EDA_CONTINUOUS` |
| `ACCELEROMETER_CONTINUOUS` | 25 Hz. Per-callback batch size **not documented** for the public SDK. | Raw `Integer` per axis. m/s² = `9.81 / (16383.75 / 4.0) * value` ≈ `0.0023951 × value`; INFERENCE: 1 g ≈ 4095.94 counts, which suggests a ±8 g range on a 16-bit sensor. "Accelerometer data doesn't include gravity." | None documented in 1.4.1 (see the conflict above) | FACT, data-specifications; `ValueKey.AccelerometerSet.html`; FAQ: "records data samples with 25Hz resolution continuously" |

**Valid IBI rule (FACT, Samsung sample code, Apache-2.0):** `isIBIValid(ibiStatus, ibiValue) = ibiStatus == 0 && ibiValue != 0`. Source: r-doz/HRDataTransfer@aa14222 `wear/src/main/java/com/samsung/health/hrdatatransfer/data/IBIDataParsing.kt:25-27`, a Samsung © 2023 file; the same code appears in the Samsung blog `https://developer.samsung.com/health/blog/en/transfer-heart-rate-from-galaxy-watch-to-a-phone` ("If the IBI reading is valid, the value of the status parameter is 0").

**Batching and `flush()`:**
- FACT (`…/sensor/guide/tracking-data.html`): "If a watch's display is off, Health Platform provides batched tracking data. flush() gives the collected data instantly. … tracking continuous heart rate normally gives 600 samples every 10 minutes. If flush() is called when 25 samples have been gathered, a DataPoint list in onDataReceived() gives only 25 samples. After getting the flush result, onDataReceived() again provides another 600 samples after a 10 minites period."
- FACT (`…/tracking/HealthTracker.html#flush--`): `flush()` is supported only for `PPG_CONTINUOUS`, `HEART_RATE_CONTINUOUS`, `ACCELEROMETER_CONTINUOUS`, `SKIN_TEMPERATURE_CONTINUOUS` and `EDA_CONTINUOUS`; any other type returns false. After a flush, Health Sensor Service calls `onFlushCompleted` and delivers the gathered data in `onDataReceived`. "If no data has been collected, TrackerEventListener.onFlushCompleted is called without triggering TrackerEventListener.onDataReceived." Remark: "Calling this API too frequently can lead to increased watch battery consumption."
- FACT (`…/sensor/guide/getting-started.html`): "The flush also resets the counter for sampling before data is automatically pushed. For example, if data is normally sent every 300 samples and flush is used after collecting 200 samples, next push happens 300 samples after calling the flush."
- FACT (tracking-data page): "Although frequency of data is fixed (for example, for the accelerometer, it's 25 Hz), interval time between each DataPoint's timestamp may vary. The order of received data points is not changed."
- FACT (overview): the continuous tracker "gathers sensor data in an application processor without waking up the CPU and sends an event at specific periods".
- **INFERENCE (design):** with the screen off, HR/EDA can reach the app up to about 10 minutes late. For C7/D11 detection on the watch, the `HealthTrackingForegroundService` will need to call `flush()` on all three trackers on a fixed period (for example every 5–10 s; tune it on hardware), and keep the CPU awake for that timer (partial wake lock or alarm). Log the battery cost per nap. Read samples by their `DataPoint` timestamps, not by arrival time.
- **UNVERIFIED lead:** a search-result snippet of the Samsung forum thread "IBI Data Collection Stops When Screen is Off" (`https://forum.developer.samsung.com/t/ibi-data-collection-stops-when-screen-is-off/38687`) said screen-off data is collected at 1 Hz and delivered "in batches every few minutes". The thread itself returned HTTP 404 when fetched. Another snippet, attributed to the *privileged* SDK FAQ, mentioned 300-sample / 12-second accelerometer batches; that may not apply to this SDK.
- **Timestamp base (UNVERIFIED):** the javadoc only says "milliseconds". `setExerciseData` asks for epoch timestamps, and public code logs `DataPoint.timestamp` next to `System.currentTimeMillis()` (Kaist-ICLab/EnPULSE@599cf37 `…/sensor/galaxywatch/SamsungHealthSensor.kt:76-78`), which suggests epoch ms. Log both values in the CSV (requirement D13) and check on hardware.

**Simultaneous trackers:**
- FACT (FAQ): "Only one on-demand tracker type can be used at a time. Multiple continuous tracker types can be used simultaneously."
- FACT (`HealthTracker.setEventListener` javadoc): "Only one HealthTracker.TrackerEventListener should be set per HealthTrackerType."
- FACT (data-specifications page): "During on-demand tracker type data measurement, tracking a continuous tracker type can give invalid values." On-demand trackers should run in the foreground and for no longer than 30 s.
- **INFERENCE:** OpenTDI uses three continuous trackers and no on-demand tracker, so it is within the documented limits.

**Wear detection (FACT, FAQ):** 'a Heart Rate STATUS value of "-3" means that the wearable is not being worn'; `Sensor.TYPE_LOW_LATENCY_OFFBODY_DETECT` is an alternative (`…/sensor/sample/hr-tracker.html`).

---

## 3. Permissions (Wear OS 6, targetSdk 36)

### 3.1 Samsung's per-tracker table

FACT, `…/sensor/guide/permission-request.html`. The raw HTML was checked for merged (`rowspan`) cells.

| HealthTrackerType | targetSdk ≥ 36 (Android 16) | targetSdk ≤ 35 |
|---|---|---|
| `ACCELEROMETER_CONTINUOUS` | `Manifest.permission.ACTIVITY_RECOGNITION` | `ACTIVITY_RECOGNITION` |
| `EDA_CONTINUOUS` (also BIA, ECG, MF-BIA, PPG on-demand/continuous) | `com.samsung.android.hardware.sensormanager.permission.READ_ADDITIONAL_HEALTH_DATA` | `BODY_SENSORS` |
| `HEART_RATE_CONTINUOUS` | `HealthPermissions.READ_HEART_RATE` (= `"android.permission.health.READ_HEART_RATE"`) | `BODY_SENSORS` |
| `SPO2_ON_DEMAND` | `HealthPermissions.READ_OXYGEN_SATURATION` | `BODY_SENSORS` |
| `SKIN_TEMPERATURE_*` | `HealthPermissions.READ_SKIN_TEMPERATURE` | `BODY_SENSORS` |
| `SWEAT_LOSS` | `ACTIVITY_RECOGNITION` **and** `READ_ADDITIONAL_HEALTH_DATA` | `ACTIVITY_RECOGNITION` + `BODY_SENSORS` |

- The same mapping appears per constant in the javadoc, labelled "[For targeting Android 16 (API level 36) or higher]" (`…/tracking/data/HealthTrackerType.html`). The javadoc code samples branch on the device's `Build.VERSION.SDK_INT >= BAKLAVA`, not on targetSdk. The HR Tracker sample adds: "if your application targetSdk=35 then BODY_SENSORS permissions should always be used" (`…/sensor/sample/hr-tracker.html`).
- The constant value `"android.permission.health.READ_HEART_RATE"` ("Protection level: dangerous", added in API 34) is FACT from `https://developer.android.com/reference/android/health/connect/HealthPermissions`.
- The permission-request page says requesting these "displays a pop-up to get the user's consent", so `READ_ADDITIONAL_HEALTH_DATA` is requested at runtime with `requestPermissions` (FACT).

### 3.2 Answers to B6

- **READ_ADDITIONAL_HEALTH_DATA on API 36:** required for EDA. FACT.
- **"Heart rate also needs ACTIVITY_RECOGNITION":** **not supported by Samsung's docs.** Heart rate needs `READ_HEART_RATE`; ACTIVITY_RECOGNITION is the accelerometer permission. OpenTDI still needs ACTIVITY_RECOGNITION because it uses the accelerometer (C7). It also satisfies the `health` foreground-service prerequisite (below).
- **BODY_SENSORS / BODY_SENSORS_BACKGROUND:** FACT, Android 16 behaviour changes (`https://developer.android.com/about/versions/16/behavior-changes-16`): "For apps targeting Android 16 (API level 36) or higher, `BODY_SENSORS` permissions use more granular permissions under `android.permissions.health` … any API previously requiring `BODY_SENSORS` or `BODY_SENSORS_BACKGROUND` requires the corresponding `android.permissions.health` permission instead." The Wear OS 6 changes page maps `BODY_SENSORS` to `READ_HEART_RATE`/`READ_OXYGEN_SATURATION`/`READ_SKIN_TEMPERATURE`, and `BODY_SENSORS_BACKGROUND` to `READ_HEALTH_DATA_IN_BACKGROUND` (`https://developer.android.com/training/wearables/versions/6/changes`). Google's migration snippet declares the legacy permissions with `android:maxSdkVersion="35"` (`https://developer.android.com/health-and-fitness/health-services/permissions`).
  **INFERENCE:** on the SM-L500 (API 36, targetSdk 36), neither BODY_SENSORS permission is needed on the Samsung SDK path. Keep `BODY_SENSORS` with `maxSdkVersion="35"` only if older watches must keep working.
- **READ_HEALTH_DATA_IN_BACKGROUND:** not mentioned in the Samsung docs. FACT (Android FGS docs): a `health` foreground service "that uses body sensors" cannot be **created** from the background without `READ_HEALTH_DATA_IN_BACKGROUND` (API 36). **INFERENCE:** if OpenTDI starts the watch service while its activity is visible, it is not needed for the service itself. **UNVERIFIED** whether Health Sensor Service checks it for screen-off delivery. KAIST's code requests it on BAKLAVA for HR (Kaist-ICLab/EnPULSE@599cf37 `…/sensor/galaxywatch/HeartRateSensor.kt:35-40`). Test: run a session with the screen off for 10+ minutes; if `PERMISSION_ERROR` appears, add it.
- **`android.permission.health.READ_HEART_RATE`** is needed for the Samsung path (FACT, Samsung table). It is the same permission Health Services and SensorManager now use on API 36 (FACT, Android 16 page), so "only for Health Services" is wrong.

### 3.3 Foreground service (watch)

FACT, `https://developer.android.com/develop/background-work/services/fgs/service-types` ("Health"):
- `android:foregroundServiceType="health"`, permission `FOREGROUND_SERVICE_HEALTH`, constant `FOREGROUND_SERVICE_TYPE_HEALTH`.
- Runtime prerequisite: "Declare the HIGH_SAMPLING_RATE_SENSORS permission … [or] Request and be granted at least one of … `BODY_SENSORS` on API 35 and lower, `READ_HEART_RATE`, `READ_SKIN_TEMPERATURE`, `READ_OXYGEN_SATURATION`, `ACTIVITY_RECOGNITION`."
- **INFERENCE:** OpenTDI meets this with READ_HEART_RATE or ACTIVITY_RECOGNITION. `READ_ADDITIONAL_HEALTH_DATA` is **not** on the list.
- **UNVERIFIED lead:** a KAIST code comment says "Unable to call health foreground service using activity recognition after BAKLAVA" (`…/AccelerometerSensor.kt:36`), which contradicts the Android doc. Holding READ_HEART_RATE avoids the question.
- The phone's `mediaPlayback|microphone` service (requirement F16) is outside the Samsung SDK. FACT from the same page: `mediaPlayback` has no runtime prerequisite; `microphone` needs `RECORD_AUDIO` and cannot be created from the background.

### 3.4 `<queries>` and other manifest entries

- The Samsung docs do **not** mention `<queries>` (FACT: none of the guide, sample or codelab pages contain it).
- FACT (`https://developer.android.com/training/package-visibility`): for apps targeting API 30+, `getPackageInfo()` results are filtered, and visibility also "affects explicit interactions with other apps, such as starting another app's service".
- **INFERENCE:** reading the Health Sensor Service version in code (requirement A4) needs a `<queries>` entry. The binding probably already works through the AAR's own merged manifest (UNVERIFIED, because the AAR was not inspected), so the extra entry is harmless:
  ```xml
  <queries><package android:name="com.samsung.android.service.health" /></queries>
  ```
  **Corrected 2026-10-27 (FACT, `device_audit.md`; `samsung_sdk_1.4.1_verified_api.md`, which overrides this note):** the installed Health Platform / Health Sensor Service package is `com.samsung.android.service.health` (versionName 1.7.00.05 on the SM-L500), and the AAR's merged manifest already declares `<queries>` for it. `com.samsung.android.service.health.tracking` is only the SDK's Java package, not an installed app, so a `getPackageInfo` or `<queries>` lookup of it fails. (The earlier text here guessed `.tracking` from public apps such as StanleyMasinde/HrmApp `app/src/main/AndroidManifest.xml:20-22`.)
- Also add `android.permission.INTERNET`: `hasResolution()`/`resolve()` need it (FACT, HealthTrackerException javadoc), and SDK_POLICY_ERROR can be caused by "A network is not connected" (FACT, TrackerError javadoc).

### 3.5 Can adb `pm grant` grant each permission?

- FACT (`https://developer.android.com/tools/adb`, pm table): `pm grant package_name permission` — "On devices running Android 6.0 … the permission can be any permission declared in the app manifest". `adb install -g` — "Grant all permissions listed in the app manifest."
- **INFERENCE:** in practice `pm grant` only works for runtime ("dangerous") permissions. `ACTIVITY_RECOGNITION`, `POST_NOTIFICATIONS` and `READ_HEART_RATE` ("Protection level: dangerous", FACT) should work. `READ_ADDITIONAL_HEALTH_DATA` is requested at runtime, so it is probably dangerous too (UNVERIFIED). `FOREGROUND_SERVICE*`, `INTERNET` and `WAKE_LOCK` are install-time and need no grant.
- Replacement for the CLAUDE.md §6 grant list (watch). **UNVERIFIED until it runs on the SM-L500:**
  ```bash
  W=<WATCH_SERIAL>; P=com.opentdi.wear
  adb -s $W shell pm grant $P android.permission.health.READ_HEART_RATE
  adb -s $W shell pm grant $P com.samsung.android.hardware.sensormanager.permission.READ_ADDITIONAL_HEALTH_DATA
  adb -s $W shell pm grant $P android.permission.ACTIVITY_RECOGNITION
  adb -s $W shell pm grant $P android.permission.POST_NOTIFICATIONS
  # check the protection level and grant state:
  adb -s $W shell pm list permissions -f | grep -A6 READ_ADDITIONAL_HEALTH_DATA
  adb -s $W shell dumpsys package $P | grep -E "READ_HEART_RATE|READ_ADDITIONAL|ACTIVITY_RECOGNITION"
  ```

---

## 4. Access policy: SDK_POLICY_ERROR, developer mode, partner registration

- **What it means (FACT, `…/tracking/HealthTracker.TrackerError.html#SDK_POLICY_ERROR`):** "A partner application that uses Samsung Health Sensor SDK requires you to register application information in Samsung's system, including an application package name, signature (SHA256), and available tracker types. SDK_POLICY_ERROR occurs in the following cases: If a running application's package name is not matched with a SDK policy. The running application's package name is matched, but its signature (SHA256) is not. To test the application, activate Health Sensor Service's developer mode. … The health tracker type is not registered. A network is not connected or the connection quality is not good."
- Also FACT: "If you get SDK_POLICY_ERROR, it indicates that your app's SDK policy was not downloaded yet" (`…/sensor/guide/tracking-data.html`). The FAQ says: "This error occurs when your app's package name or SHA-256 signature does not match the information registered with Samsung … During development, you can use the SDK without registration by enabling the developer mode." (`…/sensor/faq.html`)
- **Delivered through:** `HealthTracker.TrackerEventListener.onError(TrackerError.SDK_POLICY_ERROR)` (FACT, Getting Started sample `…/sensor/guide/getting-started.html`; Samsung multisensor sample as copied in dcuevasa/galaxy-watch-health-sensors-example@742b0c6 `…/multisensortracking/HeartRateListener.java:53-55`). INFERENCE: it can arrive once per tracker.
- **Developer mode steps (FACT, `…/sensor/guide/developer-mode.html`, checked against the page's screenshots):**
  1. Turn on the screen and select **Settings**.
  2. Tap **Apps**, swipe down, and tap **Health Sensor Service**. ("Depending on your Galaxy Watch software version, you may still see Health Platform.")
  3. "Tap the Health Sensor Service title area about 10 times."
  4. "The **Developer mode** button appears below Health Sensor Service version." (The screenshot shows "Version 1.8.00.03", then a "Developer mode" row.)
  5. Tap **Developer mode**.
  6. "After agreeing to Samsung Health Partner Service & SDK License, turn on the developer mode." (The screenshot shows an Agree prompt, then On/Off radio buttons.)
  - To disable it: "return to Developer Mode and turn it off."
  - The owner wrote that "[Dev mode]" appears. The current Samsung page and screenshots show a **"Developer mode" button** instead. UNVERIFIED whether older Health Platform builds showed "[Dev mode]". Write both into the setup doc.
- **Partner registration (FACT):** "In order to utilize Samsung Health Sensor SDK in your **publicly distributed** app, a prior registration in Samsung's systems is required" (`…/sensor/guide/app-verification.html`). "Otherwise, the app using the Samsung Health Sensor SDK works only with the developer mode turned on" (`…/sensor/process.html`). Registration takes the package name plus the signing-key SHA-256; for Play-distributed apps that is the Play **app signing key**, not the upload key (app-verification page). Check an installed APK with `adb shell pm path <pkg>`, `adb pull …`, `apksigner verify --print-certs` (same page).
- **Tension with requirement A1 (FACT, developer-mode, process and app-verification pages):** "The developer mode is intended only for testing or debugging your app. It is not intended for app users. Do not share the developer mode guide with app users."
  **INFERENCE:** an open-source project whose users build it from source are developers. So put the dev-mode step in a *developer* setup doc, link to Samsung's page instead of copying it, and do not describe it as an end-user step in any distributed app or store listing.
- **Emulator (FACT):** "Samsung Health Sensor SDK does not support an emulator." (overview); "Development with the SDK requires the Galaxy Watch device." (FAQ)
- **Phones (FACT, FAQ):** the SDK "does not support other mobile devices, such as smartphones". The `:mobile` app needs no Samsung SDK permission.

---

## 5. Version compatibility and connection errors

- **Compatibility table (FACT, `…/sensor/api-reference/overview-summary.html`):**

  | SDK library | Health Sensor Service required |
  |---|---|
  | 1.0.0 | 1.2.0 or above |
  | 1.1.0 | 1.3.0 or above |
  | 1.2.0 | 1.5.0 or above |
  | 1.3.0 | 1.5.4 or above |
  | 1.4.0 | **1.6.5 or above** |

  "If the SDK and Health Sensor Service's version is not matched, the SDK gives an error. OLD_PLATFORM_VERSION. You can resolve the error simply and induce the users to update Health Sensor Service app to the latest version with … HealthTrackerException.hasResolution() … HealthTrackerException.resolve()."
  The table has **no row for 1.4.1**. INFERENCE: as a stability patch, it is likely to need the same 1.6.5+ (UNVERIFIED).
- **On mismatch (FACT):** `ConnectionListener.onConnectionFailed(e)` with `e.getErrorCode() == HealthTrackerException.OLD_PLATFORM_VERSION` (1). If the service is missing, the code is `PACKAGE_NOT_INSTALLED` (0).
- **`resolve(Activity)` (FACT, HealthTrackerException javadoc):** both error codes → "Jump to the application installation page". "Show a proper message for each error case in your application before calling this method." The Getting Started page says it "will redirect the user to Health Sensor Service download or update page"; the class sample says "Jumping to an application market to install or update Health Sensor Service." Preconditions: network, `INTERNET` permission. It throws `IllegalStateException` "if unable to resolve".
  **INFERENCE:** `resolve()` needs an `Activity`, so the foreground service must start or notify an Activity to call it. Handle any other `getErrorCode()` value as a generic failure and show `getMessage()`; the javadoc says failures "could be related to a permission or remote binding exception".
- **Samsung's handling pattern (FACT, `…/sensor/guide/getting-started.html` and dcuevasa/galaxy-watch-health-sensors-example@742b0c6 `…/MainActivity.java:186-196`):**
  `if (code == OLD_PLATFORM_VERSION || code == PACKAGE_NOT_INSTALLED) toast("…outdated or not installed"); if (e.hasResolution()) e.resolve(activity);`
- **Health Sensor Service version format (FACT, developer-mode screenshot):** "Version 1.8.00.03". INFERENCE: compare numerically per component, so `1.6.05` equals `1.6.5`; do not compare the strings.
- **Read the installed version (package name corrected per `device_audit.md`, see §3.4):**
  ```bash
  adb -s $W shell dumpsys package com.samsung.android.service.health | grep -E "versionName|versionCode"   # package per device_audit.md
  ```
  In code, with the `<queries>` entry from §3.4: `context.packageManager.getPackageInfo("com.samsung.android.service.health", 0).versionName` (catch `PackageManager.NameNotFoundException`). Log `getTrackingCapability().getVersion()` ("Get the SDK version", FACT) as well.

---

## 6. License (requirement A5) and the medical-use statement

The license text is embedded in the download button on `…/sensor/overview.html` (attribute `data-terms="terms/sdk-license-partner-service-agreement.txt"`). It is titled "SAMSUNG HEALTH PARTNER SERVICE & SDK LICENSE AGREEMENT" and dated "UPDATED ON October 7, 2021". The watch's developer-mode screen also requires agreeing to "Samsung Health Partner Service & SDK License". Relevant clauses, quoted verbatim (FACT):

- **Definition of SDK:** "…SDK is Samsung's proprietary information and is not open source nor available in the public domain."








**Answers (INFERENCE from the clauses above; not legal advice):**
1. *May the AAR be committed to the public repo?* **No.** That would "redistribute … part of the SDK" (3.4). Gitignore `/wear/libs/*.aar` (the repo's `.gitignore` already does) and have each developer download it themselves, which also makes each of them accept the license. Some public repos commit it anyway (e.g. Tecdaz/remote-monitor `watch/app/libs/README.md` says the AAR is "committed for reproducibility"). That is not a precedent to follow.

3. *The stub:* keep it independent of the AAR (§2.2 note).
4. *OpenTDI's own license:* a permissive license (Apache-2.0/MIT) with a NOTICE saying the Samsung SDK is not covered avoids the copyleft conflict in 4.1(g). A GPL-family license could conflict.


**Fitness and wellness only (FACT, for requirement G23):**
- Release note, introduction and data-specifications pages: "Data measured by the Samsung Health Sensor SDK is for fitness and wellness information only, not for the diagnosis or treatment of any medical condition."


- §8.2: "the SDK is not intended to be used in the development of any Service where death, personal injury, or severe physical or environmental damage could result from errors…"

---

## 7. AAR file, Gradle, dependencies, ProGuard

- **Name (FACT):** `samsung-health-sensor-api.aar` (introduction and release-note "SDK content"). The download is "Samsung Health Sensor SDK v1.4.1 (70.8 KB)" (overview and process pages).
  UNVERIFIED third-party report: the zip is `samsung-health-sensor-sdk-v1.4.1.zip` and contains `samsung-health-sensor-api-1.4.1.aar` (61,063 bytes, SHA-256 `893cd5d6564db0f304bf511a555c1d65ca6bccc8475fc979ff1d71d50680344c`), per Tecdaz/remote-monitor `watch/app/libs/README.md`. Record the real file name and hash in `wear/libs/README.md` after downloading.
- **Gradle (FACT):** the app-module guide (`…/sensor/guide/app-module.html`) says "Import the library into your app project's libs folder" with `implementation fileTree(dir: 'libs', include: '*.aar')`, and sets `sourceCompatibility/targetCompatibility JavaVersion.VERSION_1_8`. The Getting Started page uses `implementation files('path/to/library/samsung-health-sensor-api.aar')`. The EDA codelab requires "JDK 17 or later" and "Android API level 36 or later" (`https://developer.samsung.com/codelab/health/electrodermal-activity.html`).
  Kotlin DSL for OpenTDI (`:wear` app module):
  ```kotlin
  dependencies { implementation(files("libs/samsung-health-sensor-api.aar")) }
  ```
  **INFERENCE:** put the AAR in the `:wear` *application* module. AGP refuses direct local `.aar` dependencies when it bundles an Android *library* AAR. Public projects work around that with a wrapper module (`configurations.maybeCreate("default"); artifacts.add("default", file("samsung-health-sensor-api.aar"))`, Kaist-ICLab/EnPULSE@599cf37 `samsung-health-sensor-api/build.gradle.kts:1-3`), which is extra moving parts OpenTDI does not need.
- **Transitive dependencies:** none documented by Samsung. INFERENCE: a `files(...)` dependency has no POM, so Gradle resolves no transitive dependencies. Whether the AAR needs any runtime library is UNVERIFIED, because the AAR was not inspected.
- **ProGuard/R8:** nothing on any Samsung page (FACT: searched all fetched pages). Whether the AAR ships consumer rules is UNVERIFIED. Public apps add `-keep class com.samsung.android.service.health.tracking.** { *; }` (e.g. aegorsuch/weartak-tracker `app/proguard-rules.pro:3-5`, with `-dontwarn` for the same package). INFERENCE: add that rule to `:wear` and test a minified release build on the watch before relying on R8.

---

## 8. Error-handling summary for `HealthTrackingForegroundService`

All INFERENCE, built from the FACTs above.

| Event | Where it arrives | On-watch message (requirements A2/A4) | Action |
|---|---|---|---|
| `onConnectionFailed`, `OLD_PLATFORM_VERSION` (1) | ConnectionListener | "Health Sensor Service (Health Platform) is too old for this app (needs ≥ 1.6.5). Tap to update." | Bring an Activity up, then `if (e.hasResolution()) e.resolve(activity)` |
| `onConnectionFailed`, `PACKAGE_NOT_INSTALLED` (0) | ConnectionListener | "Health Sensor Service is not installed. Tap to install." | Same as above |
| `onConnectionFailed`, any other code | ConnectionListener | "Cannot connect to Health Sensor Service: `e.message`" | Log it; retry with backoff (the KAIST initializer uses 1–30 s exponential backoff, `SamsungHealthSensorInitializer.kt:18-19,68-80`) |
| `onConnectionEnded` | ConnectionListener | "Sensor service disconnected — reconnecting" | Reconnect and re-register the listeners |
| `onError(SDK_POLICY_ERROR)` | TrackerEventListener | "Samsung blocked sensor access (SDK_POLICY_ERROR). Developer build: enable Developer mode in Settings > Apps > Health Sensor Service (or Health Platform). Make sure the watch has a network connection." | Stop the session; offer the sensor-free TIMED_CUES mode (requirement E15) |
| `onError(PERMISSION_ERROR)` | TrackerEventListener | "Permission missing: heart rate / additional health data / physical activity" | Request permissions again from an Activity |
| `getHealthTracker` throws `UnsupportedOperationException` | call site | "This watch does not support EDA" (EDA = Watch8+) | Mark EDA unavailable; detection keeps running without it (requirement D11: EDA is a bonus signal) |
| `getHealthTracker` throws `IllegalStateException` | call site | n/a | Not connected yet: wait for `onConnectionSuccess` |

---

## 9. Hardware checks to run on the SM-L500 (open questions)

1. **Gravity:** with the watch flat and still, log raw `ACCELEROMETER_Z`. About ±4096 means gravity is included; about 0 means the javadoc is right and it is excluded. This decides how stillness and limp-wrist detection must be designed (C7).
2. **Batch timing with the screen off** for HR, EDA and accelerometer, over at least 10 minutes with the screen off (C9): log list size, the first and last `getTimestamp()` per callback, and the arrival time. Then repeat with `flush()` every 5 s and every 10 s, and compare battery drain per hour.
3. **EDA screen-off continuity:** at least 10 minutes with the screen off, no gaps in `EdaSet` timestamps, status distribution (0 / −5 / −10).
4. **Timestamp base:** compare `getTimestamp()` with `System.currentTimeMillis()` at arrival.
5. **Permissions:** run each `pm grant` in §3.5; check the protection level of `READ_ADDITIONAL_HEALTH_DATA`; check whether `PERMISSION_ERROR` occurs with the screen off without `READ_HEALTH_DATA_IN_BACKGROUND`.
6. **Package name and version** of Health Sensor Service (§5), and whether the dev-mode label reads "Developer mode" or "[Dev mode]".
7. **Capability list:** log `getSupportHealthTrackerTypes()` and `getVersion()` once on the SM-L500 and save the result to `docs/references`.

---

## 10. Sources

Samsung pages, all fetched 2026-10-27 (HTML saved in the session scratchpad):

- Overview and download, including the embedded license: https://developer.samsung.com/health/sensor/overview.html
- Release note: https://developer.samsung.com/health/sensor/release-note.html
- Process: https://developer.samsung.com/health/sensor/process.html
- FAQ: https://developer.samsung.com/health/sensor/faq.html
- Developer support: https://developer.samsung.com/health/sensor/developer-support.html
- Guide pages: https://developer.samsung.com/health/sensor/guide/{introduction, data-specifications, connect-watch, developer-mode, app-module, connect-health-sensor-service, capability-check, permission-request, tracking-data, measurement-guide, app-verification, getting-started}.html
- HR Tracker sample: https://developer.samsung.com/health/sensor/sample/hr-tracker.html
- API reference: https://developer.samsung.com/health/sensor/api-reference/overview-summary.html, plus every class page under `…/api-reference/com/samsung/android/service/health/tracking/`, `constant-values.html`, `deprecated-list.html` and `index-all.html`
- Code labs: https://developer.samsung.com/codelab/health/{electrodermal-activity, skin-temperature, blood-oxygen, blood-oxygen-heart-rate, heart-rate-data-transfer}.html
- Blogs: https://developer.samsung.com/health/blog/en/how-the-galaxy-watchs-eda-sensor-enhances-your-health-monitoring (2026-01-20) and https://developer.samsung.com/health/blog/en/transfer-heart-rate-from-galaxy-watch-to-a-phone (2025-07-31)
- Developer-mode screenshots: https://d3unf4s5rp9dfh.cloudfront.net/SHealth_doc/developer-mode-button.png and `developer-mode-menu.png`

Android pages:

- https://developer.android.com/about/versions/16/behavior-changes-16
- https://developer.android.com/training/wearables/versions/6/changes
- https://developer.android.com/health-and-fitness/health-services/permissions
- https://developer.android.com/develop/background-work/services/fgs/service-types
- https://developer.android.com/training/package-visibility
- https://developer.android.com/tools/adb
- https://developer.android.com/reference/android/health/connect/HealthPermissions

GitHub, read through `gh api`; source files only, no AARs or files extracted from AARs:

- Kaist-ICLab/EnPULSE@599cf37 (`tracker-library/.../galaxywatch/*.kt`, `listener/SamsungHealthSensorInitializer.kt`, `samsung-health-sensor-api/{build.gradle.kts,NOTES.txt}`, `.gitignore:21-23`)
- M1chaelfmike/SamsungWatchHealthDataMonitorApplication@ef82e7b (Samsung © 2025 Apache-2.0 EDA sample)
- r-doz/HRDataTransfer@aa14222 (Samsung © 2023 Apache-2.0 sample)
- dcuevasa/galaxy-watch-health-sensors-example@742b0c6 (Samsung © 2022 Apache-2.0 sample)
- aegorsuch/weartak-tracker `app/proguard-rules.pro`
- StanleyMasinde/HrmApp `app/src/main/AndroidManifest.xml`
- Tecdaz/remote-monitor `watch/app/libs/README.md`

Could not fetch:

- Samsung forum threads 38687, 42258, 27419 and 27968 (HTTP 404; only search snippets were seen)
- APKMirror Health Platform listing (HTTP 403)
- Google Play listing for `com.samsung.android.service.health.tracking` (HTTP 404)
- The codelab sample zips and the SDK zip (they need a Samsung login; deliberately not downloaded)
