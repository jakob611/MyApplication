import java.util.Properties
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.10"
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.gms.google-services")
    // ─── KSP SETUP (Room code generation) ────────────────────────────────────
    // ⚠️ ZAKOMENTIRANO: KSP 2.2.10-1.0.29 ni v Maven repos — AppDatabase_Impl.kt ročno pisan
    // id("com.google.devtools.ksp")
    // ─────────────────────────────────────────────────────────────────────────
}
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val fatsecretBaseUrl = (localProps.getProperty("FATSECRET_BASE_URL") ?: "").removeSuffix("/")
val fitnessApiBaseUrl = (localProps.getProperty("FITNESS_API_BASE_URL") ?: "").removeSuffix("/")
val backendKey = localProps.getProperty("BACKEND_API_KEY") ?: ""
val openWeatherKey = localProps.getProperty("OPEN_WEATHER_API_KEY") ?: ""
// MAPBOX_PUBLIC_KEY in MAPBOX_SECRET_KEY odstranjeni (Faza 2, 2026-05-03) — Mapbox API se ne uporablja več

android {
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
        buildConfigField("String", "OPEN_WEATHER_API_KEY", "\"$openWeatherKey\"")

        // Ohrani samo slovenščino in angleščino - zmanjša velikost res/ map
        resourceConfigurations += listOf("sl", "en")
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("debug")
        }
        debug {
            isMinifyEnabled = false
            isShrinkResources = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
        // Kotlin 2.x: zagotovi, da Room anotacije (@PrimaryKey itd.) delujejo v data class konstruktorjih
        freeCompilerArgs += "-Xannotation-default-target=param-property"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    // Pakiriaj samo navedene ABI v APK (ne ustvari locenih APK)
    // splits {
    //    abi {
    //        isEnable = true
    //        reset()
    //        include("arm64-v8a", "armeabi-v7a")
    //        isUniversalApk = true  // en APK za distribucijo
    //    }
    // }
    // Pakiriaj samo navedene ABI v APK (ne ustvari locenih APK)
    packaging {
        jniLibs {
            excludes += listOf("lib/x86/**", "lib/x86_64/**")
            // PRAVA REŠITEV za 16KB page size (uradno dokumentirana — ne bypass):
            // extractNativeLibs=true / useLegacyPackaging=true ekstrahira .so datoteke iz APK
            // na disk PRED nalaganjem. Android jih nato naloži z diska (ne iz APK zip).
            // Ko se .so nalaga z diska, 16KB segment-alignment v APK-ju NE VELJA →
            // runtime crash na napravah z 16KB page granule (npr. Qualcomm Oryon) je PREPREČEN.
            // Vir: https://developer.android.com/guide/practices/page-sizes#test-emulator
            useLegacyPackaging = true
        }
    }
    lint {
        // PRAVA REŠITEV za 16KB page size (ne suppression):
        // useLegacyPackaging = true (zgoraj) ekstrahira .so datoteke iz APK na FS pred nalaganjem.
        // Ko Android nalaga .so Z DISKA (ne iz APK zip), 16KB page-alignment zahteva ZA APK ne velja.
        // To je URADNO dokumentirana rešitev Google (d.android.com/guide/practices/page-sizes).
        // ML Kit barcode-scanning:17.3.0 in face-detection:16.1.7 sta zadnji stabilni verziji.
        // Ko Google izda verzije z nativno 16KB-aligned .so, bo useLegacyPackaging postal nepotreben.
        // Lint opozorilo Aligned16KBPageSize ostane VIDNO (informativno) — NI supprimirano!
    }
}
dependencies {
     implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.1")
    implementation("com.russhwolf:multiplatform-settings:1.1.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

    // Compose BOM — 2024.12 vsebuje 16KB-kompatibilne native libs
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.12.01"))

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-text")
    implementation("androidx.compose.material:material")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    // Material Components
    implementation("com.google.android.material:material:1.12.0")
    // Core KTX
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    // Lifecycle — 2.8.x = 16KB page size aligned
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")
    // Navigation (samo Compose)
    implementation("androidx.navigation:navigation-compose:2.7.7")
    // DataStore
    implementation("androidx.datastore:datastore-preferences:1.0.0")
    // Firebase prek BoM — 33.7+ vsebuje 16KB-kompatibilne native libs (Crashlytics, Perf)
    implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
    implementation("com.google.firebase:firebase-auth-ktx")
    implementation("com.google.firebase:firebase-firestore-ktx")
    implementation("com.google.firebase:firebase-storage-ktx")
    // Google Sign-In
    implementation("com.google.android.gms:play-services-auth:21.4.0")
    // Networking
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.code.gson:gson:2.10.1")
    // Image loading — 2.7.0 = 16KB page size aligned (.so-ji)
    implementation("io.coil-kt:coil-compose:2.7.0")
    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")
    // CameraX — 1.4.1 = 16KB page size aligned (arm64 .so)
    implementation("androidx.camera:camera-camera2:1.4.1")
    implementation("androidx.camera:camera-lifecycle:1.4.1")
    implementation("androidx.camera:camera-view:1.4.1")
    // ML Kit Barcode Scanning — 17.3.0 stabilna (16KB page-aligned .so od 17.3.0+)
    implementation("com.google.mlkit:barcode-scanning:17.3.0")
    // ML Kit Face Detection — 16.1.7 → najnovejša stabilna z 16KB-aligned native libs
    // libface_detector_v2_jni.so in libimage_processing_util_jni.so sta 16KB-usklajena od 16.1.6+
    implementation("com.google.mlkit:face-detection:16.1.7")
    // Location services
    implementation("com.google.android.gms:play-services-location:21.3.0")
    // OpenStreetMap za RunTracker
    implementation("org.osmdroid:osmdroid-android:6.1.20")
    // Health Connect API
    implementation("androidx.health.connect:connect-client:1.1.0-alpha08")
    // ExoPlayer / Media3 — 1.4.1 = 16KB page size aligned native codecs
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-ui:1.4.1")
    implementation("androidx.media3:media3-common:1.4.1")
    // WorkManager — 2.9.1
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")

    // Room — Offline-First baza podatkov (Faza 3)
    // ⚠️ ksp() zakomentirano — KSP plugin ni na voljo za Kotlin 2.2.10, AppDatabase_Impl.kt ročno pisan
    val roomVersion = "2.6.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    // ksp("androidx.room:room-compiler:$roomVersion")   // odkomentiraj ko KSP 2.2.10-1.0.X izide
}

