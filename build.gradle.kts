plugins {
    // AGP 9 compiles Kotlin for Android modules itself, so there is no org.jetbrains.kotlin.android here.
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}
