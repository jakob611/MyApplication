import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.10"
    id("com.google.gms.google-services")
}

val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val fatsecretBaseUrl = (localProps.getProperty("FATSECRET_BASE_URL") ?: "").removeSuffix("/")
val fitnessApiBaseUrl = (localProps.getProperty("FITNESS_API_BASE_URL") ?: "").removeSuffix("/")
val backendKey = localProps.getProperty("BACKEND_API_KEY") ?: ""

android {
    buildFeatures {
        viewBinding = true
    }
    namespace = "com.example.myapplication"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.example.myapplication"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        buildConfigField("String", "FATSECRET_BASE_URL", "\"$fatsecretBaseUrl\"")
        buildConfigField("String", "FITNESS_API_BASE_URL", "\"$fitnessApiBaseUrl\"")
        buildConfigField("String", "BACKEND_API_KEY", "\"$backendKey\"")

        // Debug logging za konfiguracijo
        println("BuildConfig values:")
        println("FATSECRET_BASE_URL: $fatsecretBaseUrl")
        println("FITNESS_API_BASE_URL: $fitnessApiBaseUrl")
        println("BACKEND_API_KEY: ${if (backendKey.isNotBlank()) "***SET***" else "EMPTY"}")
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {

    // Za ConstraintLayout (če še ni dodan)
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")

        // Compose BOM (uporabi svojo verzijo; če že uporabljaš BOM, pusti kot je)
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))

    // Compose <-> ViewBinding most (za AndroidViewBinding)
    implementation("androidx.compose.ui:ui-viewbinding")

    // Material Components (za MaterialCardView v XML)
    implementation("com.google.android.material:material:1.12.0")

    // KTX (zaradi isVisible razširitve)
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("com.google.ai.edge.litert:litert:1.0.1")
    implementation("com.google.ai.edge.litert:litert-support:1.0.1")

    implementation("androidx.compose.ui:ui:1.6.7")
    implementation("androidx.compose.ui:ui-text:1.6.7")
    implementation("androidx.compose.material:material:1.6.7")
    implementation("androidx.compose.material3:material3:1.2.1")
    implementation("androidx.compose.material:material-icons-extended:1.6.7")
    implementation("androidx.compose.ui:ui-tooling-preview:1.6.7")
    implementation(libs.androidx.material3)
    debugImplementation("androidx.compose.ui:ui-tooling:1.6.7")
    debugImplementation("androidx.compose.ui:ui-test-manifest:1.6.7")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4:1.6.7")

    // Firebase prek BoM (brez verzij na artefaktih!)
    implementation(platform("com.google.firebase:firebase-bom:33.1.0"))
    implementation("com.google.firebase:firebase-auth-ktx")
    implementation("com.google.firebase:firebase-firestore-ktx")
    implementation("com.google.firebase:firebase-storage-ktx")

    implementation("com.google.android.gms:play-services-auth:21.4.0")

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.0")

    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")
    implementation("androidx.activity:activity-compose:1.9.0")

    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation("androidx.navigation:navigation-fragment-ktx:2.7.7")
    implementation("androidx.navigation:navigation-ui-ktx:2.7.7")

    implementation("androidx.datastore:datastore-preferences:1.0.0")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("io.coil-kt:coil-compose:2.5.0")
    implementation("org.json:json:20210307")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")

    implementation("com.android.billingclient:billing:6.2.1")

    // CameraX za barcode scanning
    implementation("androidx.camera:camera-camera2:1.3.1")
    implementation("androidx.camera:camera-lifecycle:1.3.1")
    implementation("androidx.camera:camera-view:1.3.1")

    // Guava za CameraX
    implementation("com.google.guava:guava:31.1-android")

    // ML Kit Barcode Scanning
    implementation("com.google.mlkit:barcode-scanning:17.2.0")

    // Coil za prikaz slik
    implementation("io.coil-kt:coil-compose:2.5.0")

    // Activity result API za permissions
    implementation("androidx.activity:activity-compose:1.9.0")

    // MapLibre GL Android (disabled for now to unblock build)
    // implementation("org.maplibre.gl:android-sdk:11.4.4")
    // implementation("org.maplibre.gl:android-sdk-turf:6.10.1")
    // implementation("com.mapbox.geojson:geojson:1.5.0")

    // Location services
    implementation("com.google.android.gms:play-services-location:21.3.0")

    // OpenStreetMap for run tracking (osmdroid)
    implementation("org.osmdroid:osmdroid-android:6.1.18")

    // Health Connect API (Android 14+) - verzija ki deluje z Samsung Health
    implementation("androidx.health.connect:connect-client:1.1.0-alpha08")


    // Google Fit API (za starejše naprave)
    implementation("com.google.android.gms:play-services-fitness:21.2.0")

    // ExoPlayer za video predvajanje vaj
    implementation("androidx.media3:media3-exoplayer:1.2.1")
    implementation("androidx.media3:media3-ui:1.2.1")
    implementation("androidx.media3:media3-common:1.2.1")

    // WorkManager za background streak check
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}