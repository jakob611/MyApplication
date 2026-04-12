plugins {
    kotlin("multiplatform")
    id("com.android.library")
}

kotlin {
    androidTarget()

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("com.russhwolf:multiplatform-settings:1.1.1")
            }
        }
    }
}

android {
    namespace = "com.example.myapplication.shared"
    compileSdk = 35 // Just in case, match with usual

    defaultConfig {
        minSdk = 26
    }
}

