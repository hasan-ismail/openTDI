plugins {
    // AGP 9 compiles Kotlin itself: no org.jetbrains.kotlin.android here.
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Matches samsung-health-sensor-api-1.4.1.aar (the file in the SDK zip) and samsung-health-sensor-api.aar (the name on
// Samsung's pages). More than one match is a build error, so two SDK versions never mix (spec §11.5).
// -Popentdi.forceSamsungStub=true builds against the stub even when the AAR is present (contributors, CI).
val forceSamsungStub = providers.gradleProperty("opentdi.forceSamsungStub").orNull.toBoolean()
val samsungAars = fileTree("libs") { include("samsung-health-sensor-api*.aar") }.files
check(samsungAars.size <= 1) { "More than one Samsung AAR in wear/libs: $samsungAars" }
val samsungAar: File? = samsungAars.singleOrNull()?.takeUnless { forceSamsungStub }
logger.lifecycle("Samsung Health Sensor SDK: ${samsungAar?.name ?: "not bundled (stub)"}")

// Release key: the same four settings as :mobile (see there), so both apps carry one signature (spec §10.1).
fun releaseSigning(name: String): String? =
    providers.gradleProperty(name).orElse(providers.environmentVariable(name)).orNull

android {
    namespace = "com.opentdi.wear"
    compileSdk = libs.versions.compileSdk.get().toInt()
    buildToolsVersion = libs.versions.buildTools.get()

    defaultConfig {
        applicationId = "com.opentdi" // same as the phone: the Data Layer needs one application ID (spec §10.1)
        minSdk = libs.versions.wearMinSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 3
        versionName = "0.3.0"
        buildConfigField("boolean", "SAMSUNG_SDK_BUNDLED", (samsungAar != null).toString())
        buildConfigField("String", "SAMSUNG_SDK_AAR", "\"${samsungAar?.name ?: ""}\"")
    }

    buildFeatures {
        compose = true
        buildConfig = true // off by default since AGP 8.0
    }

    signingConfigs {
        releaseSigning("OPENTDI_KEYSTORE_FILE")?.let { keystore ->
            create("release") {
                storeFile = rootProject.file(keystore)
                storePassword = releaseSigning("OPENTDI_KEYSTORE_PASSWORD")
                keyAlias = releaseSigning("OPENTDI_KEY_ALIAS")
                keyPassword = releaseSigning("OPENTDI_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":shared"))
    if (samsungAar != null) {
        implementation(files(samsungAar))
    } else {
        compileOnly(project(":samsung-health-stub")) // classes absent at runtime → AAR_NOT_BUNDLED
    }

    implementation(libs.wear.compose.material3)
    implementation(libs.wear.compose.foundation)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.wear.ongoing)
    implementation(libs.androidx.wear)
    implementation(libs.play.services.wearable)
    implementation(libs.kotlinx.coroutines.play.services)

    testImplementation(libs.junit)
}
