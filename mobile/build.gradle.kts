import java.net.URI
import java.security.MessageDigest
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Release key (owner, 2026-10-28: "use github actions to publish builds"; docs/SETUP.md §12). The release workflow sets
// these from repository secrets; a maintainer can set them as Gradle properties instead. :wear reads the same four, so
// both apps carry one signature. Without them a release build stays unsigned; debug builds keep the debug key.
fun releaseSigning(name: String): String? =
    providers.gradleProperty(name).orElse(providers.environmentVariable(name)).orNull
val releaseKeystore: String? = releaseSigning("OPENTDI_KEYSTORE_FILE")

android {
    namespace = "com.opentdi.mobile"
    compileSdk = libs.versions.compileSdk.get().toInt()
    buildToolsVersion = libs.versions.buildTools.get()

    defaultConfig {
        // The same applicationId (and signing key) as :wear: the Data Layer only connects matching apps (spec §10.1).
        applicationId = "com.opentdi"
        minSdk = libs.versions.mobileMinSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 3
        versionName = "0.3.0"
        // The owner's phone is arm64-v8a; the other three ABIs would add about 90 MB of sherpa-onnx native code.
        ndk { abiFilters += "arm64-v8a" }
    }

    signingConfigs {
        releaseKeystore?.let { keystore ->
            create("release") {
                storeFile = rootProject.file(keystore)
                storePassword = releaseSigning("OPENTDI_KEYSTORE_PASSWORD")
                keyAlias = releaseSigning("OPENTDI_KEY_ALIAS")
                keyPassword = releaseSigning("OPENTDI_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release { signingConfig = signingConfigs.findByName("release") }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    packaging {
        // Bouncy Castle (Kadb's pairing) ships the same license file in three jars: one copy is enough (MIT, NOTICE).
        resources { pickFirsts += "META-INF/LICENSE.md" }
    }
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

// sherpa-onnx (Apache-2.0) renders the neural cue voice (§7.3). Its AAR is not on Maven Central, so it is fetched from
// the release and checked against a pinned digest instead of being committed (.gitignore).
val sherpaOnnxAar: File = layout.projectDirectory.file("libs/sherpa-onnx-1.13.7.aar").asFile
val fetchSherpaOnnx = tasks.register("fetchSherpaOnnx") {
    description = "Downloads the pinned sherpa-onnx AAR and verifies its digest."
    // Locals, so the task body does not reach back into the script at execution time.
    val target = sherpaOnnxAar
    val url = "https://github.com/k2-fsa/sherpa-onnx/releases/download/v1.13.7/sherpa-onnx-1.13.7.aar"
    val expected = "c4ef49e309f24fcee5c106b8a279481aaecaabb078cd37b2cd6e9a62cc8a73c8"
    outputs.file(target)
    inputs.property("pin", "$url@$expected")

    doLast {
        fun digest(file: File): String =
            MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }

        if (target.exists() && digest(target) == expected) return@doLast
        target.parentFile.mkdirs()
        logger.lifecycle("Fetching $url")
        val partial = File(target.parentFile, target.name + ".part")
        URI(url).toURL().openStream().use { input -> partial.outputStream().use { input.copyTo(it) } }
        val actual = digest(partial)
        if (actual != expected) {
            partial.delete()
            error("${target.name} digest mismatch: expected $expected, got $actual")
        }
        target.delete()
        if (!partial.renameTo(target)) error("Could not move ${partial.name} to ${target.name}")
    }
}

// The phone carries the watch app of its own build type as assets/watch.apk, so it can install it on the watch itself
// (WatchInstaller); a release phone carries the release watch app, signed with the same key (spec §10.1).
abstract class CopyWatchApk : DefaultTask() {
    @get:InputFile
    abstract val apk: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun copy() {
        val dir = outputDir.get().asFile.apply { mkdirs() }
        apk.get().asFile.copyTo(File(dir, "watch.apk"), overwrite = true)
    }
}

androidComponents {
    onVariants { variant ->
        val type = variant.name // debug or release: neither app has product flavors
        val title = type.replaceFirstChar(Char::uppercase)
        // AGP names an APK built without a signing config "-unsigned"; :wear signs exactly when this module does.
        val watchApk = if (type == "release" && releaseKeystore == null) "wear-release-unsigned.apk" else "wear-$type.apk"
        val copyWatchApk = tasks.register<CopyWatchApk>("copy${title}WatchApk") {
            dependsOn(":wear:assemble$title")
            apk.set(rootProject.layout.projectDirectory.file("wear/build/outputs/apk/$type/$watchApk"))
        }
        variant.sources.assets?.addGeneratedSourceDirectory(copyWatchApk, CopyWatchApk::outputDir)
    }
}

dependencies {
    implementation(project(":shared"))
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.play.services.wearable)
    implementation(libs.androidx.wear.remote.interactions)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(files(sherpaOnnxAar).builtBy(fetchSherpaOnnx))
    implementation(libs.kadb)
    implementation(libs.r8)
    testImplementation(libs.junit)
}
