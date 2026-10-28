# Samsung Health Sensor SDK (not committed)

The watch app reads heart rate, IBI, EDA and the accelerometer through Samsung's Health Sensor SDK. OpenTDI contains
no Samsung code. Samsung's Health Sensor SDK is optional; each user downloads it from Samsung, under Samsung's own
terms, and adds it in the app. For a build of your own you can instead put the AAR here; `.gitignore` keeps it out of
the repository.

1. Sign in at <https://developer.samsung.com/health/sensor/overview.html> and choose **Download**.
2. Unzip `samsung-health-sensor-sdk-v1.4.1.zip` and copy `1.4.1/libs/samsung-health-sensor-api-1.4.1.aar` here
   (`wear/libs/`). SHA-256 of the copy this project was built against:
   `893cd5d6564db0f304bf511a555c1d65ca6bccc8475fc979ff1d71d50680344c`.
3. Build normally. Without the AAR, `:wear` compiles against `:samsung-health-stub` and falls back to Android's
   `SensorManager` heart rate + accelerometer (no EDA, no IBI); the watch shows "This build has no Samsung sensor
   SDK. Using basic watch sensors (no EDA or beat-to-beat data)." `-Popentdi.forceSamsungStub=true` builds that stub
   path even when the AAR is present.

A build with the AAR here contains Samsung's code; OpenTDI's published builds, made with the stub, contain none
(`tools/release/dexcheck.py` checks them).

On the watch, the SDK only serves apps registered with Samsung unless Health Sensor Service developer mode is on:
see [docs/SETUP.md](../../docs/SETUP.md) sections 3–4, which also give the 1.6.5 minimum version. The emulator is not
supported.
