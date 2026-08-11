// Root build file. Toolchain: AGP 9.2.0 / Kotlin 2.4.0 / Gradle 9.4.1.
// Use the checked-in ./gradlew wrapper, pinned to the AGP-compatible Gradle version.
// Compose compiler plugin is required even under AGP 9's built-in Kotlin.
plugins {
    // AGP 9 has built-in Kotlin — do NOT apply org.jetbrains.kotlin.android. Only the Compose plugin.
    id("com.android.application") version "9.2.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.0" apply false
}
