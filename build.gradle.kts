// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    id("com.android.application") version "9.2.0" apply false
    id("org.jetbrains.kotlin.android") version "2.2.10" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.2.10" apply false
    id("com.google.gms.google-services") version "4.4.1" apply false
    // KSP za Room — če KSP verzija ni razpoložljiva, se uporablja kapt (v app/build.gradle.kts)
    // id("com.google.devtools.ksp") version "2.2.10-1.0.27" apply false
}