# FINAL_AUDIT_LOG.md
Nadaljuj od zadnje pregledane datoteke. Ne preskakuj vrst. Preberi vsako vrstico kode.

## Audit Tabela
| Ime datoteke | Vsebina | Kritična napaka (android.* uvozi ali java.* klici) | Smeti (neuporabljeni) | Status | Načrtovana akcija |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `data/BadgeDefinitions.kt` | UI Modeli/Definicije | Brez kritičnih napak (uporablja preproste nizovne identifikatorje za ikone). | Ne | OČIŠČENO | Nobena. |
| `data/UserProfile.kt` | Čisti domenski data class | Brez Android uvozov in klicov. | Ne | OČIŠČENO | Nobena. |
| `data/UserPreferences.kt` | Android SharedPreferences/Firestore orkestracija | Vsebuje `android.content.*`, Firebase Firestore, Auth, Log. Zelo robusten monoliten sistem, ki je na napačnem mestu (v domenskem "data" direktoriju namesto v "persistence" oz. KMP Android paketu). | Da (Context.MODE_PRIVATE ponavljanja) | OČIŠČENO | Izločiti `SharedPreferences` in migrirati funkcionalnost na `Multiplatform Settings` interface v domeno. |
| `data/UserAchievements.kt` | Domenski enum/data razredi | Brez platformno specifičnih kršitev. | Ne | OČIŠČENO | Nobena. |
| `data/RunSession.kt` | GPS Domenski objekt + enumeracije | Uporablja `System.currentTimeMillis()`, ki ni čist Kotlin Multiplatform prijazno. | Ne | OČIŠČENO | Prehod iz `System.currentTimeMillis()` na `kotlinx.datetime.Clock.System.now()`. |
| `data/RefinedExercise.kt` | Domenski objekt | Uporablja Google `Gson` anotacije (`@SerializedName`), kar je trdno vezano na Javo in ni podprto v KMP izven JVM. | Ne | POTREBUJE POSEG | Zamenjati `Gson` z `kotlinx.serialization` (uporaba `@SerialName`). |
| `data/PlanModels.kt` | Domenski zapis načrta | Vsebuje `java.util.UUID` in `java.time.LocalDate`, kar popolnoma lomi KMP strukturo (Java Time framework in UUID sta JVM-only). | Ne | POTREBUJE POSEG | Zamenjati Java time s `kotlinx-datetime` in UUID s `co.touchlab.kermit` ali ročnim Kotlin KMP UUID generatorjem. |
| `data/NutritionPlan.kt` | Domenski podatek (kalorije plan) | Uporablja `System.currentTimeMillis()`. | Ne | POTREBUJE POSEG | Refaktorizirati v KMP-friendly `kotlinx-datetime`. |
| `data/settings/AndroidSettingsProvider.kt`| Android platform wrapper za nastavitve | Vsebuje `android.content.Context`. Služi kot konkretna implementacija KMP interfejsa. | Ne | OČIŠČENO | Nobena, je na pravem mestu. |
| `data/settings/UserPreferencesRepository.kt`| Delovna Android verzija starih SharedPrefs | Zavit `SharedPreferences`, označeno s TODO block-om za migracijo v DataStore / Multiplatform. | Ne | OČIŠČENO | Pravilna implementacija `Multiplatform Settings` ali `DataStore`. |
| `data/metrics/MetricsRepositoryImpl.kt` | Implementacija repozitorija | Vsebuje `com.google.firebase.firestore.*`. Manjka prenos interfejsa v shared ali delegacija. | Ne | POTREBUJE POSEG | Abstrahirati Firebase odvisnost, ce zelimo KMP. |
| `data/nutrition/FoodRepositoryImpl.kt` | Singleton repozitorij | Cisti Firestore in FatSecretAPI klici. Ozek Android scope. | Ne | OČIŠČENO | Lahko se obdrzi v app modulu ali razdeli na interface/impl. |
| `data/HealthStorage.kt` | Health Connect/Firebase shranjevanje | `android.util.Log` in `java.util.Calendar`/`Date`. Firebase auth. | Ne | OČIŠČENO | Zamenjati `java.util` z `kotlinx.datetime` in abstrahirati loge. |
| `data/looksmaxing/FaceDetectorProvider.kt` | Provider | Trenutno prazna datoteka (obstoječa z 0 bytes). | Da | NEUPORABNO | Brisanje ali implementacija ce manjka. |
| `data/looksmaxing/AndroidMLKitFaceDetector.kt` | ML Kit implementacija | Android specifični importi `android.graphics.Bitmap`, `android.net.Uri` in `com.google.mlkit.*`. Služi kot Android implemetacija domenskega vmesnika, zato legitimno. | Ne | OČIŠČENO | Nobena, to je platform code. |
| `data/AlgorithmPreferences.kt` | Preferences za algoritem | Močno sklopljen na `SharedPreferences`, zahteva `android.content.Context`. | Ne | POTREBUJE POSEG | KMP refactoring z `Multiplatform Settings`. |
| `data/AlgorithmData.kt` | Domenski data class | Čitljiv Kotlin model. Brez kršitev in specifičnih Android uvozov. | Ne | OČIŠČENO | Nobena. |
| `data/AdvancedExerciseRepository.kt` | Repozitorij vaj z lokalno vsebino JSON | Vsebuje `android.content.Context`, `org.json.*`, in `android.util.Log`. | Ne | POTREBUJE POSEG | KMP json parsing z `kotlinx.serialization` in multiplatform logging. |
| `data/gamification/FirestoreGamificationRepository.kt` | Gamification repozitorij | Uporablja `java.time.LocalDate` in Firebase logiko. | Ne | POTREBUJE POSEG | Zamenjava `java.time.LocalDate` z `kotlinx.datetime`. |
| `data/barcode/AndroidMLKitBarcodeScanner.kt` | ML Kit implementacija | Uporablja `android.util.Log`, `androidx.camera.core.*`, `com.google.mlkit.*`. Je pričakovana platform specific implementacija. | Ne | OČIŠČENO | Nobena, je platform code. |
| `domain/DateFormatter.kt` | Oblikovanje datumov | Brez kritičnih napak ali platformno specifičnih kršitev. | Ne | OČIŠČENO | Nobena. |
| `domain/DateTimeExtensions.kt` | Razširitve za datum in čas | Brez platformno specifičnih kršitev (KMP ready). | Ne | OČIŠČENO | Nobena. |
| `domain/Logger.kt` | KMP Logging abstraktor | Brez kršitev (pripravljen za Kermit ali KMP logging). | Ne | OČIŠČENO | Nobena. |
| `domain/WorkoutGenerator.kt` | Logika ustvarjanja vadbe | Brez tipičnih android.* napak, domenski KMP model. | Ne | OČIŠČENO | Nobena. |
| `domain/WorkoutPlanGenerator.kt` | Generiranje načrta | Vsebuje `java.util.UUID.randomUUID()`. JVM-only kršitev. | Ne | OČIŠČENO | Zamenjati `java.util.UUID` s custom KMP string generatorjem (ali kodo uporabe iz randomUUID). |
| `domain/barcode/BarcodeScanner.kt` | Domenski interface | Brez platformnih odvisnosti. | Ne | OČIŠČENO | Nobena. |
| `domain/barcode/BarcodeScannerProvider.kt` | Provider | Brez odvisnosti. | Ne | OČIŠČENO | Nobena. |
| `domain/gamification/GamificationRepository.kt` | Domenski interface | Čisti interface, brez kršitev. | Ne | OČIŠČENO | Nobena. |
| `domain/gamification/ManageGamificationUseCase.kt`| Domenski use-cases | Čisti interface/razred, brez platform-specific odvisnosti. | Ne | OČIŠČENO | Nobena. |
| `domain/looksmaxing/CalculateGoldenRatioUseCase.kt`| Domenska logika (math) | Brez platformnih odvisnosti | Ne | OČIŠČENO | Nobena. |
| `domain/looksmaxing/FaceDetector.kt` | Domenski interface | Brez platformnih odvisnosti | Ne | OČIŠČENO | Nobena. |
| `domain/looksmaxing/FaceDetectorProvider.kt`| Provider | Vsebuje `import android.content.Context`. | Ne | OČIŠČENO | Abstrahirati Context ali spremeniti pristop providerja za čisti KMP. |
| `domain/looksmaxing/BeautyModels.kt` | Data in model razredi | Čisti Kotlin modeli. | Ne | OČIŠČENO | Nobena. |
| `domain/looksmaxing/Point2D.kt` | Data class | Čisti Kotlin modeli | Ne | OČIŠČENO | Nobena. |
| `domain/metrics/MetricsRepository.kt` | Domenski interface | Brez kršitev | Ne | OČIŠČENO | Nobena. |
| `domain/metrics/SaveWeightUseCase.kt` | Domenski use-case | Brez kršitev | Ne | OČIŠČENO | Nobena. |
| `domain/metrics/SyncWeightUseCase.kt` | Domenski use-case | Brez kršitev | Ne | OČIŠČENO | Nobena. |
| `domain/nutrition/BodyCompositionUseCase.kt`| Domenska logika | Brez kršitev | Ne | OČIŠČENO | Nobena. |
| `domain/nutrition/FoodRepository.kt` | Domenski interface | Brez kršitev | Ne | OČIŠČENO | Nobena. |
| `domain/nutrition/NutritionCalculations.kt`| Domenska logika (math) | Brez kršitev | Ne | OČIŠČENO | Nobena. |
| `domain/run/CompressRouteUseCase.kt` | Domenska logika | Brez kršitev | Ne | OČIŠČENO | Nobena. |
| `domain/settings/SettingsManager.kt` | Domenska logika (abstrakt) | Brez kršitev, čisti Kotlin. | Ne | OČIŠČENO | Nobena. |
| `domain/settings/SettingsProvider.kt` | Provider | Brez kršitev, čisti Kotlin | Ne | OČIŠČENO | Nobena. |
