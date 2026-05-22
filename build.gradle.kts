// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    id("com.android.application") version "9.2.1" apply false
    id("org.jetbrains.kotlin.android") version "2.2.10" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.2.10" apply false
    id("com.google.gms.google-services") version "4.4.1" apply false
    // KSP 2.2.10-1.0.X — ⚠️ ZAKOMENTIRANO: 2.2.10-1.0.29 ni v Maven repos
    // Ko bo verzija objavljena: odkomentiraj in zbriši AppDatabase_Impl.kt ročno
    // id("com.google.devtools.ksp") version "2.2.10-1.0.29" apply false
}