// Root build file. Toolchain: AGP 9.2.0 / Kotlin 2.4.0 / Gradle 9.4.1.
// Use the checked-in ./gradlew wrapper, pinned to the AGP-compatible Gradle version.
// Compose compiler plugin is required even under AGP 9's built-in Kotlin.
plugins {
    // AGP 9 has built-in Kotlin — do NOT apply org.jetbrains.kotlin.android. Only the Compose plugin.
    id("com.android.application") version "9.2.0" apply false
    id("com.android.library") version "9.2.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.0" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.4.0" apply false
    id("com.google.devtools.ksp") version "2.3.6" apply false
    id("com.google.dagger.hilt.android") version "2.60.1" apply false
}
