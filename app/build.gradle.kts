import java.util.Properties
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.0"
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.gms.google-services")
    // ─── KSP SETUP (Room code generation) ────────────────────────────────────
    // KSP 2.1.0-1.0.29 je uradno dostopen za Kotlin 2.1.0 — Room generira kodo samodejno
    id("com.google.devtools.ksp")
    // ─────────────────────────────────────────────────────────────────────────
    // ─── Detekt — statična analiza kode (Faza 45) ────────────────────────────
    id("io.gitlab.arturbosch.detekt")
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
            // useLegacyPackaging = true: sekundarna varovalka za morebitne druge .so datoteke
            // v tranzitivnih odvisnostih, ki bi imele 4KB ELF alignment.
            // PRIMARNA rešitev za 16KB (Faza 27): ML Kit face-detection in barcode-scanning
            // sta zamenjana z play-services-mlkit-* variantami, ki NE bundlajo .so v APK
            // (native koda prihaja iz Play Services, ki je 16KB poravnan).
            useLegacyPackaging = true
        }
    }
    lint {
        // Opozorilo Aligned16KBPageSize je vidno (informativno) — NI suprimirano.
        // Prava rešitev: play-services-mlkit-* (Faza 27) ne bundla .so v APK.
        // useLegacyPackaging = true (spodaj) je sekundarna varovalka za tranzitivne odvisnosti.
    }
}
dependencies {
     implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.1")
    implementation("com.russhwolf:multiplatform-settings:1.1.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

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
    // Navigation (samo Compose) — 2.8.x: Type-Safe Navigation (stable)
    implementation("androidx.navigation:navigation-compose:2.8.9")
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
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")
    // CameraX — 1.4.1 = 16KB page size aligned (arm64 .so)
    implementation("androidx.camera:camera-camera2:1.4.1")
    implementation("androidx.camera:camera-lifecycle:1.4.1")
    implementation("androidx.camera:camera-view:1.4.1")
    // ── ML Kit via Play Services — FAZA 27: PRAVA rešitev za 16KB ELF LOAD alignment ──────────
    // com.google.mlkit:face-detection:16.1.7 in barcode-scanning:17.3.0 sta vsebovala
    // libface_detector_v2_jni.so in libbarhopper_v3.so z ELF p_align=0x1000 (4KB).
    // Na Android napravah z 16KB page granularity (Qualcomm Oryon, Pixel 9 Pro itd.) kernel
    // zavrne mmap teh .so datoteke — TUDI ko so izvlečene na disk z useLegacyPackaging=true.
    //
    // Play Services varianta NE bundla nobene .so datoteke v APK. Native kodo naloži iz
    // Google Play Services ob prvi uporabi. Play Services na Android 16 napravah je
    // VEDNO 16KB-ELF-poravnan (Google vzdržuje Play Services neodvisno od APK).
    // API vmesnik je popolnoma enak → nobenih sprememb v AndroidMLKitFaceDetector.kt
    // oz. AndroidMLKitBarcodeScanner.kt.
    //
    // Vir: https://developers.google.com/ml-kit/migration/android
    implementation("com.google.android.gms:play-services-mlkit-barcode-scanning:18.3.0")
    implementation("com.google.android.gms:play-services-mlkit-face-detection:17.1.0")
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
    // Faza 35 — ViewModel korutinski testi (runTest, UnconfinedTestDispatcher, advanceUntilIdle)
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")

    // Room — Offline-First baza podatkov (Faza 3)
    // KSP 2.1.0-1.0.29 samodejno generira AppDatabase_Impl — ročna implementacija je bila izbrisana
    // Room 2.7.1: popravljeno "unexpected jvm signature V" za suspend fun z Unit povratno vrednostjo
    val roomVersion = "2.7.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")

    // ─── Detekt — statična analiza kode (Faza 45) ────────────────────────────
    detektPlugins("io.gitlab.arturbosch.detekt:detekt-formatting:1.23.6")
    // ─────────────────────────────────────────────────────────────────────────
}

// ─── Detekt konfiguracija (Faza 45) ──────────────────────────────────────────
detekt {
    // Gradi na vrhu privzetih pravil (ne zamenja jih)
    buildUponDefaultConfig = true
    // allRules = false → ne vklopi eksperimentalnih/previewing pravil
    allRules = false
    // Pot do projektne konfiguracijske datoteke
    config.setFrom(files("$rootDir/config/detekt/detekt.yml"))
    // Baseline — ob prvem zagonu ustvari baseline.xml z obstoječimi kršitvami;
    // nadaljnji zagoni javljajo samo NOVE kršitve (postopna uvedba)
    baseline = file("$rootDir/config/detekt/baseline.xml")
    // Analiziraj tudi testne izvorne datoteke
    source.setFrom(
        "src/main/java",
        "src/test/java",
        "src/androidTest/java"
    )
}
// ─────────────────────────────────────────────────────────────────────────────

