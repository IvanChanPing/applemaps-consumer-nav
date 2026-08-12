rootProject.name = "AppleMapsConsumerNav"

pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { setUrl("https://www.jitpack.io") }
    }
    versionCatalogs {
        create("velaLibs") { from(files("vendor/Vela/gradle/libs.versions.toml")) }
    }
}

include(":app", ":vela-core", ":vela-app-runtime")
