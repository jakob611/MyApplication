// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    id("com.android.application") version "9.2.0" apply false
    id("org.jetbrains.kotlin.android") version "2.2.10" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.2.10" apply false
    id("com.google.gms.google-services") version "4.4.1" apply false
    // KSP ni na voljo za Kotlin 2.2.10; AppDatabase_Impl.kt je ročno napisan
}