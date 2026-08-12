import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    // AGP 9 has built-in Kotlin — kotlin.android is removed; only the Compose compiler plugin is applied.
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.example.applemaps"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.applemaps.consumernav"
        minSdk = 26
        targetSdk = 36
        versionCode = 11
        versionName = "0.12-consumer-browse-and-directions"
        // On-device Google Routes API key (route planning). Supply via `-PROUTES_API_KEY=...` or gradle.properties.
        buildConfigField("String", "ROUTES_API_KEY", "\"${project.findProperty("ROUTES_API_KEY") ?: ""}\"")
        // Google Places API key (place details + photos). Falls back to ROUTES_API_KEY so one key with both APIs enabled works.
        buildConfigField("String", "PLACES_API_KEY", "\"${project.findProperty("PLACES_API_KEY") ?: project.findProperty("ROUTES_API_KEY") ?: ""}\"")
        // Google Maps SDK key → substituted into the manifest ${MAPS_API_KEY}. Supply via -PMAPS_API_KEY=... at build (never committed).
        manifestPlaceholders["MAPS_API_KEY"] = (project.findProperty("MAPS_API_KEY") ?: "") as String
    }

    buildTypes {
        getByName("debug") {
            // Physical-device diagnostics target ARM64; keep non-ARM native payloads out of the deliverable APK.
            ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
            }
        }
        release {
            isMinifyEnabled = false
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    lint {
        // Ferrostar packages notification code for its optional ForegroundServiceManager. NavEngine passes
        // that manager as null, so this app has no call path to the flagged notification operation.
        disable += "NotificationPermission"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true   // required by Ferrostar
    }

    tasks.withType<KotlinCompile>().configureEach {
        compilerOptions.jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation("io.coil-kt:coil-compose:2.7.0")
    val composeBom = platform("androidx.compose:compose-bom:2025.12.01")
    implementation(composeBom)

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.core:core-ktx:1.18.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")

    // Boxless rich place cards: Android-restricted Places API (New) calls run directly in the APK.
    implementation("com.google.android.libraries.places:places:5.3.0")

    // Ferrostar provides keyless turn-by-turn state/TTS; the consumer Apple WebView remains map owner through GO.
    val ferrostar = "0.53.0"
    implementation("com.stadiamaps.ferrostar:core:$ferrostar")
    implementation("com.stadiamaps.ferrostar:ui-compose:$ferrostar")
    implementation(platform("com.squareup.okhttp3:okhttp-bom:4.12.0"))
    implementation("com.squareup.okhttp3:okhttp")

    // Google Maps SDK (Compose) — Google's on-road "Road Level Details" (painted lanes/crosswalks) on a separate
    // Google-rendered map screen. Needs a Maps-SDK-enabled API key (manifest com.google.android.geo.API_KEY),
    // allowlisted for this package + signing SHA-1. It remains separate from the consumer Apple basemap.
    implementation("com.google.maps.android:maps-compose:6.6.0")

    // Apple Look Around coverage tiles use the checked-in GroundMetadataTile schema generated as lite Java.
    implementation("com.google.protobuf:protobuf-javalite:3.22.3")

    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
