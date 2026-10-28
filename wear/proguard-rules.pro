# Samsung Health Sensor SDK (spec §11.5). Minify is off in Phase 1; test a minified build on the watch before relying
# on R8 with these rules.
-keep class com.samsung.android.service.health.tracking.** { *; }
-dontwarn com.samsung.android.service.health.tracking.**
