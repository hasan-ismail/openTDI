// Compile-only stand-in for the Samsung Health Sensor SDK, used when wear/libs/ has no AAR.
// Written from Samsung's public API reference; holds only the members OpenTDI calls. Android
// parameter types are declared as Object so this stays a plain JVM library: callers passing a
// Context/Activity compile against both this stub and the real AAR.
plugins {
    `java-library`
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
