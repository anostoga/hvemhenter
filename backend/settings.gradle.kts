// Lar Gradle auto-laste ned manglende JDK-toolchains (f.eks. JDK 25 for kompilering,
// se jvmToolchain(25) i build.gradle.kts) uten at den som kjører bygget må ha den
// installert fra før — nyttig både lokalt og i CI.
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

rootProject.name = "barnehage-backend"
