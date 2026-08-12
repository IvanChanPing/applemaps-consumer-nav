import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
}

android {
    namespace = "app.vela.core"
    compileSdk = 36
    defaultConfig { minSdk = 29 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    lint {
        disable += setOf("UnsafeOptInUsageError", "UnsafeOptInUsageWarning")
    }
    tasks.withType<KotlinCompile>().configureEach { compilerOptions.jvmTarget.set(JvmTarget.JVM_17) }
}

val verifyVelaSource by tasks.registering(Exec::class) {
    inputs.file("../vela-upstream.sha256")
    inputs.dir("../vendor/Vela")
    workingDir("../vendor/Vela")
    commandLine("sha256sum", "--check", "../../vela-upstream.sha256")
}
tasks.named("preBuild").configure { dependsOn(verifyVelaSource) }
tasks.configureEach {
    // AGP 9.2's lint analyzer crashes on the unchanged pinned Vela Kotlin sources.
    if (name.startsWith("lintAnalyze")) enabled = false
}

val relocateOsmandProtobuf by tasks.registering(RelocateOsmandProtobuf::class) {
    inputJar.set(layout.projectDirectory.file("libs/osmand-java.jar"))
    outputJar.set(layout.buildDirectory.file("generated/osmand/osmand-java-relocated.jar"))
}
val relocatedOsmandJar = files(relocateOsmandProtobuf.flatMap { it.outputJar }).builtBy(relocateOsmandProtobuf)

dependencies {
    implementation(velaLibs.androidx.core.ktx)
    implementation(velaLibs.kotlinx.coroutines.android)
    implementation(velaLibs.kotlinx.serialization.json)
    implementation(velaLibs.androidx.datastore.preferences)
    implementation(velaLibs.okhttp)
    implementation(velaLibs.rhino.runtime)
    implementation(velaLibs.graphhopper.mapmatching) {
        exclude(group = "org.openstreetmap.osmosis")
        exclude(group = "com.google.protobuf")
        exclude(group = "com.fasterxml.jackson.dataformat", module = "jackson-dataformat-xml")
        exclude(group = "com.fasterxml.woodstox")
        exclude(group = "org.codehaus.woodstox")
        exclude(group = "org.apache.xmlgraphics")
    }
    implementation(relocatedOsmandJar)
    implementation(files("libs/osmand-shared-jvm.jar", "libs/gnu-trove-osmand.jar", "libs/kxml2-vela.jar"))
    implementation("commons-logging:commons-logging:1.2")
    implementation("com.google.dagger:hilt-android:2.60.1")
    ksp("com.google.dagger:hilt-android-compiler:2.60.1")
    testImplementation(velaLibs.junit)
}
