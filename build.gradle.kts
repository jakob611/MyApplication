// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    id("com.android.application") version "9.2.1" apply false
    id("org.jetbrains.kotlin.android") version "2.2.10" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.2.10" apply false
    id("com.google.gms.google-services") version "4.4.1" apply false
    // KSP: Kotlin 2.2.10 nima uradne KSP verzije na Maven (preizkušeno: 1.0.29–1.0.35).
    // Ko Google objavi ksp za 2.2.10: id("com.google.devtools.ksp") version "2.2.10-1.0.XX" apply false
    // Preveri: https://github.com/google/ksp/releases
}