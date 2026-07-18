pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // NextLib : décodeurs FFmpeg prebuilt pour media3 (EAC3/AC3/DTS...).
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "cstv"
include(":app")
