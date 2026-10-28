pluginManagement {
    repositories {
        google {
            content {
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
                includeGroupAndSubgroups("androidx")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google {
            content {
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
                includeGroupAndSubgroups("androidx")
            }
        }
        mavenCentral()
        // Only Kadb's pairing dependency (spake2-java, GPL-3.0) comes from JitPack; nothing else may.
        maven("https://jitpack.io") { content { includeGroup("com.github.Flyfish233") } }
    }
}

rootProject.name = "OpenTDI"

// :shared              pure Kotlin: data contracts, HypnagogiaDetectionEngine, signal math, simulation curves
// :wear                Galaxy Watch8 Classic app (sensing, detection, bezel UI)
// :mobile              phone companion (session service, timed cues, audio, journal)
// :samsung-health-stub compile-only stand-in for the Samsung Health Sensor SDK AAR (see wear/libs/README.md)
include(":shared")
include(":wear")
include(":mobile")
include(":samsung-health-stub")
