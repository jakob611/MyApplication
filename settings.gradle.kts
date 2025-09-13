pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    // Poravnaj verzijo ali odstrani celoten plugins blok; tukaj jo uskladimo na 2.0.0
    plugins {
        id("org.jetbrains.kotlin.android") version "2.0.0"
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "MyApplication"
include(":app")