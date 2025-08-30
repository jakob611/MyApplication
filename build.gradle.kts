// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    id("com.android.application") version "8.12.2" apply false
    id("org.jetbrains.kotlin.android") version "2.0.0" apply false
    // Zgornje verzije posodobi, če je treba (8.1.1 in 2.0.0 sta stabilni za Firebase Compose)
}

buildscript {
    dependencies {
        classpath("com.google.gms:google-services:4.4.1")
    }
}