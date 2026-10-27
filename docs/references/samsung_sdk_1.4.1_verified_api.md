# Samsung Health Sensor SDK 1.4.1 — public API used by OpenTDI

Source: Samsung's **public** API reference,
<https://developer.samsung.com/health/sensor/api-reference/overview-summary.html> and its class pages under
`…/api-reference/com/samsung/android/service/health/tracking/` plus `constant-values.html` — every signature
below is marked VERIFIED against those pages in `samsung_health_sensor_sdk.md` §2. The `:samsung-health-stub`
module is written from this public documentation only; the build itself (compiling `:wear` against the real AAR)

the AAR's classes. Where another note in `docs/references/` disagrees with this file, this file wins.

## SDK package facts

- SDK download: `samsung-health-sensor-sdk-v1.4.1.zip` → `1.4.1/libs/samsung-health-sensor-api-1.4.1.aar`
  (SHA-256 `893cd5d6564db0f304bf511a555c1d65ca6bccc8475fc979ff1d71d50680344c`), downloaded by the owner from
  developer.samsung.com on 2026-10-27.
- The library's manifest (merged into the app manifest at build time) adds `<queries>` for
  `com.samsung.android.service.health` — the Health Platform / Health Sensor Service app, confirmed installed on
  the watch as 1.7.00.05 (`device_audit.md`) — and for Galaxy Store (`com.sec.android.app.samsungapps`, used by
  `HealthTrackerException.resolve()`), sets `minSdkVersion 26`, and declares **no permissions**.
- The zip's `Announcement.txt` covers only the open-source components bundled inside the SDK (Apache-2.0, BSD,
  MPL-2.0). The SDK itself is under the Samsung Health Partner Service & SDK License Agreement (accepted at


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
