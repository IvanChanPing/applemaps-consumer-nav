import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
}

android {
    namespace = "app.vela"
    compileSdk = 36
    defaultConfig {
        minSdk = 29
        buildConfigField("int", "VERSION_CODE", "2955")
        buildConfigField("String", "VERSION_NAME", "\"0.4.955\"")
        buildConfigField("String", "MAPTILER_KEY", "\"\"")
        buildConfigField("String", "ROUTING_MANIFEST_URL", "\"https://github.com/PimpinPumpkin/Vela/releases/download/routing-graphs/routing-manifest-v2.json\"")
        buildConfigField("String", "OBF_MANIFEST_URL", "\"https://github.com/PimpinPumpkin/Vela/releases/download/obf-regions/obf-manifest.json\"")
        buildConfigField("String", "OVERLAY_MANIFEST_URL", "\"https://github.com/PimpinPumpkin/Vela/releases/download/building-overlays/building-overlay-manifest.json\"")
        buildConfigField("String", "MAXSPEED_MANIFEST_URL", "\"https://github.com/PimpinPumpkin/Vela/releases/download/maxspeed-overlays/maxspeed-overlay-manifest.json\"")
        buildConfigField("String", "ADDRESS_MANIFEST_URL", "\"https://github.com/PimpinPumpkin/Vela/releases/download/address-overlays/address-overlay-manifest.json\"")
        buildConfigField("String", "POI_PACK_MANIFEST_URL", "\"https://github.com/PimpinPumpkin/Vela/releases/download/poi-packs/poi-pack-manifest.json\"")
        buildConfigField("String", "FLOCK_MANIFEST_URL", "\"https://github.com/PimpinPumpkin/Vela/releases/download/flock-cameras/flock-manifest.json\"")
        buildConfigField("String", "MAP_FONTS_URL", "\"https://pimpinpumpkin.github.io/Vela/fonts\"")
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    lint {
        disable += setOf("UnsafeOptInUsageError", "UnsafeOptInUsageWarning")
    }
    tasks.withType<KotlinCompile>().configureEach { compilerOptions.jvmTarget.set(JvmTarget.JVM_17) }
    packaging.resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
}

tasks.configureEach {
    // AGP 9.2's lint analyzer crashes on the unchanged pinned Vela Kotlin sources.
    if (name.startsWith("lintAnalyze")) enabled = false
}

dependencies {
    api(project(":vela-core"))
    implementation(files("libs/sherpa-onnx-1.13.3.aar"))
    implementation("org.apache.commons:commons-compress:1.27.1")
    implementation(velaLibs.androidx.profileinstaller)
    implementation(velaLibs.androidx.core.ktx)
    implementation(velaLibs.androidx.lifecycle.runtime.ktx)
    implementation(velaLibs.androidx.lifecycle.runtime.compose)
    implementation(velaLibs.androidx.lifecycle.viewmodel.compose)
    implementation(velaLibs.androidx.activity.compose)
    implementation(velaLibs.androidx.splashscreen)
    implementation(platform(velaLibs.androidx.compose.bom))
    implementation(velaLibs.androidx.compose.ui)
    implementation(velaLibs.androidx.compose.ui.graphics)
    implementation(velaLibs.androidx.compose.ui.tooling.preview)
    implementation(velaLibs.androidx.compose.foundation)
    implementation(velaLibs.androidx.compose.material.icons.extended)
    implementation(velaLibs.androidx.material3)
    implementation(velaLibs.androidx.navigation.compose)
    implementation("com.google.dagger:hilt-android:2.60.1")
    implementation(velaLibs.hilt.navigation.compose)
    ksp("com.google.dagger:hilt-android-compiler:2.60.1")
    implementation(velaLibs.coil.compose)
    implementation(velaLibs.maplibre.android)
    implementation(velaLibs.androidx.car.app)
    implementation(velaLibs.androidx.car.app.projected)
    debugImplementation(velaLibs.androidx.compose.ui.tooling)
}
