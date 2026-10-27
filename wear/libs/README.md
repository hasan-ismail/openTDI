# Samsung Health Sensor SDK (not committed)

The watch app reads heart rate, IBI, EDA and the accelerometer through Samsung's Health Sensor SDK.
Samsung distributes it only from its developer site behind a Samsung account login, under Samsung's own
license, which does not grant redistribution — so the AAR is gitignored and every developer downloads it.

1. Sign in at <https://developer.samsung.com/health/sensor/overview.html> → **Download** and accept the license.
2. Unzip `samsung-health-sensor-sdk-v1.4.1.zip` and copy `1.4.1/libs/samsung-health-sensor-api-1.4.1.aar` here
   (`wear/libs/`). SHA-256 of the copy this project was built against:
   `893cd5d6564db0f304bf511a555c1d65ca6bccc8475fc979ff1d71d50680344c`.
3. Build normally. Without the AAR, `:wear` compiles against `:samsung-health-stub` and falls back to Android's
   `SensorManager` heart rate + accelerometer (no EDA, no IBI); the watch shows "Samsung SDK not bundled".

On the watch, the SDK only serves apps registered with Samsung unless **Health Platform developer mode** is on:
Settings → Apps → Health Platform → tap the title ~10 times until "[Dev mode]" appears. SDK 1.4.x needs
Health Platform (`com.samsung.android.service.health`) 1.6.5 or newer. The emulator is not supported.
