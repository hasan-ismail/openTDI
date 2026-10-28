# Samsung Health Sensor SDK 1.4.1 — public API used by OpenTDI

Source: Samsung's **public** API reference,
<https://developer.samsung.com/health/sensor/api-reference/overview-summary.html> and its class pages under
`…/api-reference/com/samsung/android/service/health/tracking/` plus `constant-values.html` — every signature
below is marked VERIFIED against those pages in `samsung_health_sensor_sdk.md` §2. The `:samsung-health-stub`
module is written from this public documentation only; the build itself (compiling `:wear` against the real AAR)
is the check that the two agree. Do not decompile or inspect the AAR's classes: the stub follows the public
documentation only. Where another note in `docs/references/` disagrees with this file, this file wins.

## SDK package facts

- SDK download: `samsung-health-sensor-sdk-v1.4.1.zip` → `1.4.1/libs/samsung-health-sensor-api-1.4.1.aar`
  (SHA-256 `893cd5d6564db0f304bf511a555c1d65ca6bccc8475fc979ff1d71d50680344c`), downloaded by the owner from
  developer.samsung.com on 2026-10-27.
- The library's manifest (merged into the app manifest at build time) adds `<queries>` for
  `com.samsung.android.service.health` — the Health Platform / Health Sensor Service app, confirmed installed on
  the watch as 1.7.00.05 (`device_audit.md`) — and for Galaxy Store (`com.sec.android.app.samsungapps`, used by
  `HealthTrackerException.resolve()`), sets `minSdkVersion 26`, and declares **no permissions**.
- The zip's `Announcement.txt` covers only the open-source components bundled inside the SDK (Apache-2.0, BSD,
  MPL-2.0). The SDK itself comes from Samsung under Samsung's own terms, accepted at download, so the AAR is
  never committed (it is gitignored).

## Public API (package `com.samsung.android.service.health.tracking`)

```java
public class HealthTrackingService {
  public HealthTrackingService(ConnectionListener listener, android.content.Context context);
  public void connectService();
  public void disconnectService();
  public HealthTracker getHealthTracker(data.HealthTrackerType type);
  public HealthTracker getHealthTracker(data.HealthTrackerType, data.TrackerUserProfile);
  public HealthTracker getHealthTracker(data.HealthTrackerType, data.TrackerUserProfile, data.ExerciseType);
  public HealthTracker getHealthTracker(data.HealthTrackerType, java.util.Set<data.PpgType>);
  public HealthTracker getHealthTracker(data.HealthTrackerType, data.TrackerUserProfile, data.ExerciseType, int);
  public HealthTrackerCapability getTrackingCapability();
}

public interface ConnectionListener {
  void onConnectionSuccess();
  void onConnectionEnded();
  void onConnectionFailed(HealthTrackerException e);
}

public class HealthTrackerException extends Exception {
  public static final int PACKAGE_NOT_INSTALLED = 0;
  public static final int OLD_PLATFORM_VERSION  = 1;
  public int getErrorCode();
  public boolean hasResolution();
  public void resolve(android.app.Activity activity);   // opens Galaxy Store to install/update; needs INTERNET
}

public class HealthTrackerCapability {
  public java.util.List<data.HealthTrackerType> getSupportHealthTrackerTypes();
  public String getVersion();
}

public class HealthTracker {
  public void setEventListener(HealthTracker.TrackerEventListener listener);
  public void unsetEventListener();
  public boolean flush();

  public interface TrackerEventListener {
    void onDataReceived(java.util.List<data.DataPoint> dataPoints);
    void onFlushCompleted();
    void onError(HealthTracker.TrackerError error);
  }
  public enum TrackerError { PERMISSION_ERROR, SDK_POLICY_ERROR }
}
```

## Data (package `com.samsung.android.service.health.tracking.data`)

```java
public class DataPoint {
  public <T> T getValue(ValueKey<T> key);
  public long getTimestamp();
}
public class ValueKey<T> { }

public enum HealthTrackerType {
  PPG_GREEN, PPG_CONTINUOUS, PPG_RED, PPG_IR, PPG_ON_DEMAND, HEART_RATE, HEART_RATE_CONTINUOUS, ECG,
  ECG_ON_DEMAND, ACCELEROMETER, ACCELEROMETER_CONTINUOUS, BIA, BIA_ON_DEMAND, SWEAT_LOSS, SPO2,
  SPO2_ON_DEMAND, SKIN_TEMPERATURE, SKIN_TEMPERATURE_ON_DEMAND, SKIN_TEMPERATURE_CONTINUOUS, EDA_CONTINUOUS,
  MF_BIA_ON_DEMAND
}

public class ValueKey.HeartRateSet {
  ValueKey<Integer>        HEART_RATE;
  ValueKey<Integer>        HEART_RATE_STATUS;
  ValueKey<List<Integer>>  IBI_LIST;
  ValueKey<List<Integer>>  IBI_STATUS_LIST;
}
public class ValueKey.EdaSet {           // since 1.4.0
  ValueKey<Float>   SKIN_CONDUCTANCE;
  ValueKey<Integer> STATUS;
}
public class ValueKey.AccelerometerSet {
  ValueKey<Integer> ACCELEROMETER_X;     // raw counts; Samsung: m/s^2 = 9.81 / (16383.75 / 4.0) * value
  ValueKey<Integer> ACCELEROMETER_Y;
  ValueKey<Integer> ACCELEROMETER_Z;
}
```

### Logging-only trackers (skin temperature, raw PPG, SpO2 spot checks)

Read from Samsung's public reference on 2026-10-27; the `:samsung-health-stub` holds exactly these members, and
compiling `:wear` against the AAR confirmed every name and type. Base URL:
`https://developer.samsung.com/health/sensor/api-reference/com/samsung/android/service/health/tracking/`.

```java
// data/ValueKey.SkinTemperatureSet.html (since 1.2.0): for SKIN_TEMPERATURE_CONTINUOUS and _ON_DEMAND
public static class ValueKey.SkinTemperatureSet {
  public static final ValueKey<Float> OBJECT_TEMPERATURE;   // °C, "Temperature of an object contacted with the Galaxy Watch"
  public static final ValueKey<Float> AMBIENT_TEMPERATURE;  // °C, ambient temperature around the watch
  public static ValueKey<Integer> STATUS;                   // 0 normal, -1 error (the page shows no `final` here)
}
// data/ValueKey.SpO2Set.html (since 1.1.0): for SPO2_ON_DEMAND
public static class ValueKey.SpO2Set {
  public static final ValueKey<Integer> STATUS;      // 0 calculating, 2 completed, -4 device moved, -5 low signal, -6 time out
  public static final ValueKey<Integer> SPO2;        // %
  public static final ValueKey<Integer> HEART_RATE;  // bpm
}
// data/ValueKey.PpgSet.html (since 1.3.0): for PPG_CONTINUOUS and PPG_ON_DEMAND
public static class ValueKey.PpgSet {
  public static final ValueKey<Integer> PPG_GREEN, PPG_IR, PPG_RED;           // green LED ADC value; IR / red raw values
  public static final ValueKey<Integer> GREEN_STATUS, IR_STATUS, RED_STATUS;  // PPG_CONTINUOUS: 0 normal, -1 a
                                                                              // higher-priority sensor (e.g. BIA) runs
}
// data/PpgType.html (since 1.3.0); constants in declaration order. Request only the channels needed: fewer channels
// cost less battery ("Setting only the required PpgType can reduce the battery consumption").
public enum PpgType { GREEN, RED, IR }
// HealthTrackingService.html (since 1.3.0): the overload PPG_CONTINUOUS and PPG_ON_DEMAND must use
public HealthTracker getHealthTracker(HealthTrackerType healthTrackerType, java.util.Set<PpgType> ppgTypeSet);
```

- `HealthTrackerType` (data/HealthTrackerType.html): `PPG_CONTINUOUS` is 25 Hz and, at targetSdk 36, needs
  `READ_ADDITIONAL_HEALTH_DATA`; `SKIN_TEMPERATURE_CONTINUOUS` gives one point per measurement with the display on and
  needs `HealthPermissions.READ_SKIN_TEMPERATURE`; `SPO2_ON_DEMAND` gives one data point per measurement and needs
  `HealthPermissions.READ_OXYGEN_SATURATION` (`BODY_SENSORS` for all three at targetSdk ≤ 35; the same table is in
  `https://developer.samsung.com/health/sensor/guide/permission-request.html`).
- `HealthTracker.flush()` (HealthTracker.html#flush--) works for `PPG_CONTINUOUS` and `SKIN_TEMPERATURE_CONTINUOUS`
  (and HR, accelerometer, EDA); for any other type it returns false.
- On-demand rules (`https://developer.samsung.com/health/sensor/guide/data-specifications.html`, "Using On-demand
  Tracker Type"): use it "in a foreground application, not in the background"; one on-demand tracker at a time;
  "Track on-demand type sensors in 30 seconds"; "During on-demand tracker type data measurement, tracking a continuous
  tracker type can give invalid values." The SPO2_ON_DEMAND remark repeats the 30 s limit; a SpO2 measurement
  "usually takes 30 seconds".
- The SM-L500 lists `SKIN_TEMPERATURE_CONTINUOUS`, `PPG_CONTINUOUS` and `SPO2_ON_DEMAND` in
  `getSupportHealthTrackerTypes()` (owner's on-device log, 2026-10-27).

## Consequences for OpenTDI

1. **`SDK_POLICY_ERROR` is a `TrackerError`**, delivered to `TrackerEventListener.onError()` *after* a tracker
   listener is set — not a `HealthTrackerException` from `connectService()`. The status checker watches both:
   - `onConnectionFailed(e)`: `PACKAGE_NOT_INSTALLED` / `OLD_PLATFORM_VERSION` → if `e.hasResolution()`, offer
     `e.resolve(activity)` ("Install/update Health Sensor Service").
   - `onError(SDK_POLICY_ERROR)` → "Enable developer mode in Settings > Apps > Health Sensor Service (Health
     Platform), and check the watch is online".
   - `onError(PERMISSION_ERROR)` → missing runtime permission.
2. Connection can succeed while data is still refused, so "connected" is not "working" until data arrives.
3. `HealthTrackerCapability.getVersion()` can be shown on the status screen.
4. Skin temperature, raw PPG and SpO2 are logging-only: a missing permission, an unsupported type or a tracker error
   turns off only that tracker. SpO2 spot checks run from a foreground service, often with the screen off, and the
   owner's default timeout is 60 s: both go beyond Samsung's on-demand guidance above, so check on the watch that
   measurements complete and how heart rate behaves meanwhile (`w_spo2.csv` counts the valid HR points per check).
