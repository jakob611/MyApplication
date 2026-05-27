// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    id("com.android.application") version "9.2.1" apply false
    id("org.jetbrains.kotlin.android") version "2.1.0" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.1.0" apply false
    id("com.google.gms.google-services") version "4.4.1" apply false
    // KSP 2.1.0-1.0.29 — uradno dostopen za Kotlin 2.1.0 (stable)
    id("com.google.devtools.ksp") version "2.1.0-1.0.29" apply false
    // ─── Detekt — statična analiza kode (Faza 45) ────────────────────────────
    id("io.gitlab.arturbosch.detekt") version "1.23.6" apply false
    // ─────────────────────────────────────────────────────────────────────────
}