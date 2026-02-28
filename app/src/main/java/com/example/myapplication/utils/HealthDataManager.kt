package com.example.myapplication.utils

import android.content.Context
import android.content.pm.PackageManager
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.HealthConnectClient.Companion.SDK_AVAILABLE
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.fitness.Fitness
import com.google.android.gms.fitness.FitnessOptions
import com.google.android.gms.fitness.data.DataType
import com.google.android.gms.fitness.request.DataReadRequest
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import com.google.android.gms.fitness.data.Field

data class HealthData(
    val steps: Long = 0,
    val heartRate: Int = 0,
    val sleepMinutes: Long = 0,
    val distance: Float = 0f,
    val caloriesBurned: Float = 0f,
    val isConnected: Boolean = false,
    val connectionError: String? = null,
    val isWearableBacked: Boolean = false
)

class HealthDataManager(private val context: Context) {

    private var healthConnectClient: HealthConnectClient? = null
    private var googleFitConnected = false

    private val fitnessOptions: FitnessOptions by lazy {
        FitnessOptions.builder()
            .addDataType(DataType.TYPE_STEP_COUNT_DELTA, FitnessOptions.ACCESS_READ)
            .addDataType(DataType.TYPE_HEART_RATE_BPM, FitnessOptions.ACCESS_READ)
            .addDataType(DataType.TYPE_DISTANCE_DELTA, FitnessOptions.ACCESS_READ)
            .addDataType(DataType.TYPE_CALORIES_EXPENDED, FitnessOptions.ACCESS_READ)
            .build()
    }

    private val wearablePackages = setOf(
        "com.samsung.android.shealth",
        "com.sec.android.app.shealth",
        "com.fitbit.FitbitMobile",
        "com.google.android.apps.fitness", // Wear OS / Fit services on watches
        "com.huawei.health",
        "com.xiaomi.hm.health",
        "com.garmin.android.apps.connectmobile",
        "com.apple.android.health" // Apple devices via bridge if ever exposed
    )

    private val healthConnectPackage = "com.google.android.apps.healthdata"
    private val samsungHealthConnectPackage = "com.samsung.android.service.health"
    private val samsungHealthApp = "com.samsung.android.shealth"

    private fun isWearablePackage(pkg: String?): Boolean {
        if (pkg.isNullOrBlank()) return false
        if (pkg == context.packageName) return false
        return wearablePackages.any { pkg.startsWith(it) }
    }

    private fun isHealthConnectInstalled(): Boolean {
        // Na Samsung-u: preuči Samsung Health (shealth)
        val hasSamsungHealth = try {
            context.packageManager.getPackageInfo(samsungHealthApp, 0)
            true
        } catch (_: Exception) {
            false
        }

        // Check for standard Health Connect
        val hasStandardHC = try {
            context.packageManager.getPackageInfo(healthConnectPackage, 0)
            true
        } catch (_: Exception) {
            false
        }

        // Check for Samsung Health Connect service
        val hasSamsungHC = try {
            context.packageManager.getPackageInfo(samsungHealthConnectPackage, 0)
            true
        } catch (_: Exception) {
            false
        }

        // Also check SDK availability
        val sdkAvailable = try {
            HealthConnectClient.getSdkStatus(context) == SDK_AVAILABLE
        } catch (_: Exception) {
            false
        }

        android.util.Log.d("HealthDataManager", "HC check: samsung=$hasSamsungHealth, standard=$hasStandardHC, samsungService=$hasSamsungHC, sdkAvailable=$sdkAvailable")

        return hasSamsungHealth || hasStandardHC || hasSamsungHC || sdkAvailable
    }

    // Preveri ali je Health Connect na voljo
    suspend fun isHealthConnectAvailable(): Boolean = withContext(Dispatchers.Default) {
        try {
            HealthConnectClient.getSdkStatus(context) == SDK_AVAILABLE
        } catch (e: Exception) {
            false
        }
    }

    // Initializiraj Health Connect
    suspend fun initializeHealthConnect(): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            val sdkStatus = HealthConnectClient.getSdkStatus(context)
            android.util.Log.d("HealthDataManager", "Health Connect SDK status: $sdkStatus")

            if (sdkStatus == SDK_AVAILABLE) {
                healthConnectClient = HealthConnectClient.getOrCreate(context)
                android.util.Log.d("HealthDataManager", "Health Connect client created successfully")
                true
            } else {
                android.util.Log.d("HealthDataManager", "Health Connect SDK not available")
                false
            }
        } catch (e: Exception) {
            android.util.Log.e("HealthDataManager", "Error initializing Health Connect", e)
            false
        }
    }

    // Zahtevaj permisije za Health Connect
    fun getHealthConnectPermissions(): Set<String> {
        return setOf(
            HealthPermission.getReadPermission(StepsRecord::class),
            HealthPermission.getReadPermission(HeartRateRecord::class),
            HealthPermission.getReadPermission(SleepSessionRecord::class),
            HealthPermission.getReadPermission(DistanceRecord::class),
            HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
        )
    }

    // Preberi podatke iz Health Connect
    suspend fun readHealthDataFromHealthConnect(): HealthData = withContext(Dispatchers.IO) {
        return@withContext try {
            android.util.Log.d("HealthDataManager", "=== Starting Health Connect read ===")

            if (!hasAnyWearableAppInstalled()) {
                android.util.Log.d("HealthDataManager", "No wearable app installed")
                return@withContext HealthData(connectionError = "Nobene fitness aplikacije niso nameščene. Namesti Samsung Health ali drugo fitness aplikacijo.")
            }

            // Initialize if not already done (SDK check is inside)
            if (healthConnectClient == null) {
                android.util.Log.d("HealthDataManager", "Initializing Health Connect client")
                if (!initializeHealthConnect()) {
                    android.util.Log.d("HealthDataManager", "Failed to initialize Health Connect - SDK not available")
                    // For Samsung devices, Health Connect might not be available but Samsung Health might be installed
                    val hasSamsungHealth = isWearablePackageInstalled("com.samsung.android.shealth", listOf("com.sec.android.app.shealth"))
                    if (hasSamsungHealth) {
                        return@withContext HealthData(
                            connectionError = "Dovolil moraš dostop do telesne dejavnosti:\\n\\n1. Odpri Samsung Health\\n2. Pojdi v Nastavitve\\n3. Tappe na Podatke/Varnost/Dovoljenja\\n4. Iskalnik > Dovoli dostop"
                        )
                    }
                    return@withContext HealthData(connectionError = "Health Connect ni nameščen. Namesti Health Connect iz Play Store in omogoči sinhronizacijo v Samsung Health.")
                }
            }

            val client = healthConnectClient ?: run {
                android.util.Log.d("HealthDataManager", "Health Connect client is null")
                return@withContext HealthData(connectionError = "Health Connect client null")
            }

            val requiredPermissions = getHealthConnectPermissions()
            android.util.Log.d("HealthDataManager", "Required permissions: ${requiredPermissions.size}")

            val granted = try {
                val grantedPerms = client.permissionController.getGrantedPermissions()
                android.util.Log.d("HealthDataManager", "Granted permissions: ${grantedPerms.size}")
                grantedPerms.containsAll(requiredPermissions)
            } catch (e: Exception) {
                android.util.Log.e("HealthDataManager", "Error checking permissions", e)
                false
            }

            if (!granted) {
                android.util.Log.d("HealthDataManager", "Permissions not granted")
                return@withContext HealthData(connectionError = "Health Connect permissions not granted. Please approve permissions.")
            }

            val now = LocalDateTime.now()
            val startOfDay = now.withHour(0).withMinute(0).withSecond(0)
            val timeRangeFilter = TimeRangeFilter.between(
                startOfDay.atZone(ZoneId.systemDefault()).toInstant(),
                now.atZone(ZoneId.systemDefault()).toInstant()
            )

            var steps = 0L
            var heartRate = 0
            var sleepMinutes = 0L
            var distance = 0f
            var calories = 0f
            var wearableBacked = false

            try {
                val stepsRequest = ReadRecordsRequest(
                    recordType = StepsRecord::class,
                    timeRangeFilter = timeRangeFilter
                )
                val stepsResponse = client.readRecords(stepsRequest)

                android.util.Log.d("HealthDataManager", "Total step records: ${stepsResponse.records.size}")

                // FILTRIRAJ SAMO SAMSUNG HEALTH podatke - ignorij Fitbit, Google Fit, itd.
                val samsungHealthPackages = setOf(
                    "com.samsung.android.shealth",
                    "com.sec.android.app.shealth",
                    "com.samsung.android.service.health"
                )

                val filteredRecords = stepsResponse.records.filter { record ->
                    val origin = record.metadata.dataOrigin?.packageName
                    val isSamsung = origin != null && samsungHealthPackages.any { origin.startsWith(it) }
                    android.util.Log.d("HealthDataManager", "  Step record: origin=$origin, count=${record.count}, isSamsung=$isSamsung")
                    isSamsung
                }

                steps = filteredRecords.sumOf { it.count }
                android.util.Log.d("HealthDataManager", "Total steps from Samsung Health ONLY: $steps from ${filteredRecords.size} records (filtered from ${stepsResponse.records.size} total)")

                // Če ni podatkov v Health Connect, preveri če je kakšna fitness app nameščena
                if (steps == 0L && stepsResponse.records.isEmpty()) {
                    val hasSamsungHealth = try {
                        context.packageManager.getPackageInfo("com.samsung.android.shealth", 0)
                        true
                    } catch (_: Exception) {
                        false
                    }

                    if (hasSamsungHealth) {
                        android.util.Log.d("HealthDataManager", "Samsung Health installed but no data in Health Connect")
                        return@withContext HealthData(
                            connectionError = "Samsung Health ne sinhronizira podatkov:\n\n1. Odpri Samsung Health\n2. Profil → Nastavitve (⚙️)\n3. Povezane storitve\n4. Health Connect\n5. Vklopi sinhronizacijo\n\nAli pa\n\n1. Odpri Samsung Health\n2. Dodaj korake ročno\n3. Počakaj 1-2 minuti\n4. Osveži tukaj"
                        )
                    } else {
                        android.util.Log.d("HealthDataManager", "No fitness app data in Health Connect")
                    }
                }

                wearableBacked = wearableBacked || stepsResponse.records.any { record ->
                    isWearablePackage(record.metadata.dataOrigin?.packageName)
                }
            } catch (e: Exception) {
                android.util.Log.e("HealthDataManager", "Error reading steps", e)
            }

            try {
                val heartRateRequest = ReadRecordsRequest(
                    recordType = HeartRateRecord::class,
                    timeRangeFilter = timeRangeFilter
                )
                val heartRateResponse = client.readRecords(heartRateRequest)

                // FILTRIRAJ SAMO SAMSUNG HEALTH
                val samsungHealthPackages = setOf(
                    "com.samsung.android.shealth",
                    "com.sec.android.app.shealth",
                    "com.samsung.android.service.health"
                )

                val filteredHeartRate = heartRateResponse.records.filter { record ->
                    val origin = record.metadata.dataOrigin?.packageName
                    origin != null && samsungHealthPackages.any { origin.startsWith(it) }
                }

                if (filteredHeartRate.isNotEmpty()) {
                    heartRate = filteredHeartRate.lastOrNull()?.samples?.lastOrNull()?.beatsPerMinute?.toInt() ?: 0
                }
                android.util.Log.d("HealthDataManager", "Heart rate from Samsung Health: $heartRate from ${filteredHeartRate.size} records (filtered from ${heartRateResponse.records.size} total)")
                wearableBacked = wearableBacked || filteredHeartRate.any { record ->
                    isWearablePackage(record.metadata.dataOrigin?.packageName)
                }
            } catch (e: Exception) {
                android.util.Log.e("HealthDataManager", "Error reading heart rate", e)
            }

            try {
                val sleepRequest = ReadRecordsRequest(
                    recordType = SleepSessionRecord::class,
                    timeRangeFilter = timeRangeFilter
                )
                val sleepResponse = client.readRecords(sleepRequest)

                // FILTRIRAJ SAMO SAMSUNG HEALTH
                val samsungHealthPackages = setOf(
                    "com.samsung.android.shealth",
                    "com.sec.android.app.shealth",
                    "com.samsung.android.service.health"
                )

                val filteredSleep = sleepResponse.records.filter { record ->
                    val origin = record.metadata.dataOrigin?.packageName
                    origin != null && samsungHealthPackages.any { origin.startsWith(it) }
                }

                sleepMinutes = filteredSleep.sumOf { record ->
                    val startTime = record.startTime
                    val endTime = record.endTime
                    java.time.Duration.between(startTime, endTime).toMinutes()
                }
                android.util.Log.d("HealthDataManager", "Sleep from Samsung Health: $sleepMinutes min from ${filteredSleep.size} records (filtered from ${sleepResponse.records.size} total)")
                wearableBacked = wearableBacked || filteredSleep.any { record ->
                    isWearablePackage(record.metadata.dataOrigin?.packageName)
                }
            } catch (e: Exception) {
                android.util.Log.e("HealthDataManager", "Error reading sleep", e)
            }

            try {
                val distanceRequest = ReadRecordsRequest(
                    recordType = DistanceRecord::class,
                    timeRangeFilter = timeRangeFilter
                )
                val distanceResponse = client.readRecords(distanceRequest)

                // FILTRIRAJ SAMO SAMSUNG HEALTH
                val samsungHealthPackages = setOf(
                    "com.samsung.android.shealth",
                    "com.sec.android.app.shealth",
                    "com.samsung.android.service.health"
                )

                val filteredDistance = distanceResponse.records.filter { record ->
                    val origin = record.metadata.dataOrigin?.packageName
                    origin != null && samsungHealthPackages.any { origin.startsWith(it) }
                }

                distance = filteredDistance.sumOf { it.distance.inMeters }.toFloat() / 1000
                android.util.Log.d("HealthDataManager", "Distance from Samsung Health: $distance km from ${filteredDistance.size} records (filtered from ${distanceResponse.records.size} total)")
                wearableBacked = wearableBacked || filteredDistance.any { record ->
                    isWearablePackage(record.metadata.dataOrigin?.packageName)
                }
            } catch (e: Exception) {
                android.util.Log.e("HealthDataManager", "Error reading distance", e)
            }

            try {
                val caloriesRequest = ReadRecordsRequest(
                    recordType = ActiveCaloriesBurnedRecord::class,
                    timeRangeFilter = timeRangeFilter
                )
                val caloriesResponse = client.readRecords(caloriesRequest)

                // FILTRIRAJ SAMO SAMSUNG HEALTH
                val samsungHealthPackages = setOf(
                    "com.samsung.android.shealth",
                    "com.sec.android.app.shealth",
                    "com.samsung.android.service.health"
                )

                val filteredCalories = caloriesResponse.records.filter { record ->
                    val origin = record.metadata.dataOrigin?.packageName
                    origin != null && samsungHealthPackages.any { origin.startsWith(it) }
                }

                calories = filteredCalories.sumOf { it.energy.inKilocalories }.toFloat()
                android.util.Log.d("HealthDataManager", "Calories from Samsung Health: $calories kcal from ${filteredCalories.size} records (filtered from ${caloriesResponse.records.size} total)")
                wearableBacked = wearableBacked || filteredCalories.any { record ->
                    isWearablePackage(record.metadata.dataOrigin?.packageName)
                }
            } catch (e: Exception) {
                android.util.Log.e("HealthDataManager", "Error reading calories", e)
            }

            val hasAnyData = steps > 0 || heartRate > 0 || sleepMinutes > 0 || distance > 0f || calories > 0f
            android.util.Log.d("HealthDataManager", "hasAnyData: $hasAnyData, steps: $steps, heartRate: $heartRate, sleep: $sleepMinutes, distance: $distance, calories: $calories")

            // If we have data from Health Connect and Samsung Health is installed, it's valid
            val hasSamsungHealth = isWearablePackageInstalled(
                "com.samsung.android.shealth",
                listOf("com.sec.android.app.shealth")
            )
            android.util.Log.d("HealthDataManager", "hasSamsungHealth: $hasSamsungHealth, wearableBacked: $wearableBacked")

            if (!hasAnyData) {
                android.util.Log.d("HealthDataManager", "No Samsung Health data available")
                // Če je Samsung Health nameščen a ni podatkov, verjetno ni sinhroniziran
                if (hasSamsungHealth) {
                    HealthData(connectionError = "Samsung Health ni sinhroniziran s Health Connect.\n\n1. Odpri Samsung Health\n2. Pojdi v Nastavitve → Povezane storitve → Health Connect\n3. Omogoči sinhronizacijo")
                } else {
                    HealthData(connectionError = "No data available. Make sure your smartwatch is synced and has collected data today.")
                }
            } else {
                android.util.Log.d("HealthDataManager", "Returning Samsung Health data successfully")
                HealthData(
                    steps = steps,
                    heartRate = heartRate,
                    sleepMinutes = sleepMinutes,
                    distance = distance,
                    caloriesBurned = calories,
                    isConnected = true,
                    isWearableBacked = wearableBacked || hasSamsungHealth
                )
            }
        } catch (e: Exception) {
            android.util.Log.e("HealthDataManager", "Error in readHealthDataFromHealthConnect", e)
            HealthData(connectionError = "Error reading Health Connect data: ${e.message}")
        }
    }

    // Inicijaliziraj Google Fit
    suspend fun initializeGoogleFit(): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            val account = GoogleSignIn.getLastSignedInAccount(context)
            googleFitConnected = account != null && GoogleSignIn.hasPermissions(account, fitnessOptions)
            googleFitConnected
        } catch (e: Exception) {
            false
        }
    }

    fun hasGoogleFitPermissions(): Boolean {
        val account = GoogleSignIn.getLastSignedInAccount(context) ?: return false
        return GoogleSignIn.hasPermissions(account, fitnessOptions)
    }

    // Zahtevaj Google Fit permisije
    fun getGoogleSignInClient(): GoogleSignInClient {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN).build()
        return GoogleSignIn.getClient(context, gso)
    }

    // Preberi podatke iz Google Fit
    suspend fun readHealthDataFromGoogleFit(): HealthData = withContext(Dispatchers.IO) {
        return@withContext try {
            if (!hasAnyWearableAppInstalled()) {
                return@withContext HealthData(connectionError = "No wearable companion app installed")
            }
            val account = GoogleSignIn.getLastSignedInAccount(context)
            if (account == null || !GoogleSignIn.hasPermissions(account, fitnessOptions)) {
                return@withContext HealthData(connectionError = "Google Fit not authorized")
            }

            val now = System.currentTimeMillis()
            val startTime = now - TimeUnit.DAYS.toMillis(1)

            var steps = 0L
            var distance = 0f
            var calories = 0f
            var wearableBacked = false

            fun isWearableDevice(device: com.google.android.gms.fitness.data.Device?): Boolean {
                device ?: return false
                return device.type != com.google.android.gms.fitness.data.Device.TYPE_PHONE &&
                    device.type != com.google.android.gms.fitness.data.Device.TYPE_TABLET &&
                    device.type != com.google.android.gms.fitness.data.Device.TYPE_UNKNOWN
            }

            try {
                // Preberi korake
                val stepsRequest = DataReadRequest.Builder()
                    .aggregate(DataType.TYPE_STEP_COUNT_DELTA)
                    .bucketByTime(1, TimeUnit.DAYS)
                    .setTimeRange(startTime, now, TimeUnit.MILLISECONDS)
                    .build()

                val result = Fitness.getHistoryClient(context, account).readData(stepsRequest).await()
                steps = result.buckets.sumOf { bucket ->
                    bucket.dataSets.sumOf { dataSet ->
                        dataSet.dataPoints.sumOf { point ->
                            point.getValue(Field.FIELD_STEPS).asInt().toLong()
                        }
                    }
                }
                wearableBacked = wearableBacked || result.buckets.any { bucket ->
                    bucket.dataSets.any { dataSet -> isWearableDevice(dataSet.dataSource.device) }
                }
            } catch (e: Exception) {
                // Ignoriraj napako pri branju korakov
            }

            try {
                // Preberi razdaljo
                val distanceRequest = DataReadRequest.Builder()
                    .aggregate(DataType.TYPE_DISTANCE_DELTA)
                    .bucketByTime(1, TimeUnit.DAYS)
                    .setTimeRange(startTime, now, TimeUnit.MILLISECONDS)
                    .build()

                val result = Fitness.getHistoryClient(context, account).readData(distanceRequest).await()
                distance = result.buckets
                    .flatMap { bucket -> bucket.dataSets }
                    .flatMap { dataSet -> dataSet.dataPoints }
                    .sumOf { point ->
                        point.getValue(Field.FIELD_DISTANCE).asFloat().toDouble()
                    }
                    .toFloat() / 1000f // km
                wearableBacked = wearableBacked || result.buckets.any { bucket ->
                    bucket.dataSets.any { dataSet -> isWearableDevice(dataSet.dataSource.device) }
                }
            } catch (e: Exception) {
                // Ignoriraj napako pri branju razdalje
            }

            try {
                // Preberi porabljene kalorije
                val caloriesRequest = DataReadRequest.Builder()
                    .aggregate(DataType.TYPE_CALORIES_EXPENDED)
                    .bucketByTime(1, TimeUnit.DAYS)
                    .setTimeRange(startTime, now, TimeUnit.MILLISECONDS)
                    .build()

                val result = Fitness.getHistoryClient(context, account).readData(caloriesRequest).await()
                calories = result.buckets
                    .flatMap { bucket -> bucket.dataSets }
                    .flatMap { dataSet -> dataSet.dataPoints }
                    .sumOf { point ->
                        point.getValue(Field.FIELD_CALORIES).asFloat().toDouble()
                    }
                    .toFloat()
                wearableBacked = wearableBacked || result.buckets.any { bucket ->
                    bucket.dataSets.any { dataSet -> isWearableDevice(dataSet.dataSource.device) }
                }
            } catch (e: Exception) {
                // Ignoriraj napako pri branju kalorij
            }

            val hasAnyData = steps > 0 || distance > 0f || calories > 0f

            // Accept data from Google Fit if wearable app is installed
            val hasFitbitOrWearable = isWearablePackageInstalled(
                "com.fitbit.FitbitMobile",
                listOf("com.google.android.apps.fitness", "com.huawei.health", "com.xiaomi.hm.health")
            )

            if (!hasAnyData) {
                HealthData(connectionError = "No data available. Make sure your smartwatch is synced and has collected data today.")
            } else {
                // Accept data if any wearable app is installed
                HealthData(
                    steps = steps,
                    distance = distance,
                    caloriesBurned = calories,
                    isConnected = true,
                    isWearableBacked = wearableBacked || hasFitbitOrWearable
                )
            }
        } catch (e: Exception) {
            HealthData(connectionError = "Error reading Google Fit data: ${e.message}")
        }
    }

    // Preberi podatke direktno iz Samsung Health
    suspend fun readHealthDataFromSamsungHealth(): HealthData = withContext(Dispatchers.IO) {
        return@withContext try {
            // Check if Samsung Health is installed
            if (!isWearablePackageInstalled("com.samsung.android.shealth", listOf("com.sec.android.app.shealth"))) {
                return@withContext HealthData(connectionError = "Samsung Health not installed. Please install and sync with your smartwatch.")
            }

            // Samsung Health is installed - attempt to read data
            try {
                readFromSamsungHealthSDK()
            } catch (e: Exception) {
                HealthData(connectionError = "Samsung Health is installed but permissions might be needed. Please grant access in app settings.")
            }
        } catch (e: Exception) {
            HealthData(connectionError = "Samsung Health read error: ${e.message}")
        }
    }

    private suspend fun readFromSamsungHealthSDK(): HealthData = withContext(Dispatchers.IO) {
        return@withContext try {
            // Samsung Health is connected but we need explicit permissions to read data
            // For now, report that it's ready to sync
            HealthData(
                steps = 0,
                heartRate = 0,
                sleepMinutes = 0,
                distance = 0f,
                caloriesBurned = 0f,
                isConnected = true,
                isWearableBacked = true,
                connectionError = "Samsung Health permissions needed. Check app settings and grant data access."
            )
        } catch (e: Exception) {
            HealthData(connectionError = "Samsung Health SDK error: ${e.message}")
        }
    }

    // Osvežitev podatkov - osvežite podatke iz vseh virov
    suspend fun refreshHealthConnectData(): HealthData = withContext(Dispatchers.IO) {
        return@withContext try {
            android.util.Log.d("HealthDataManager", "=== REFRESH DATA STARTED ===")

            // Osvežite iz vseh virov - ne zanašajte se samo na keš

            // Najprej poskusite s Health Connect
            val hcResult = try {
                if (isHealthConnectAvailable()) {
                    android.util.Log.d("HealthDataManager", "HC available - reinitializing client")
                    // Força reinitializacijo
                    healthConnectClient = null
                    if (!initializeHealthConnect()) {
                        android.util.Log.d("HealthDataManager", "HC init failed")
                        null
                    } else {
                        android.util.Log.d("HealthDataManager", "Reading from HC")
                        val data = readHealthDataFromHealthConnect()
                        android.util.Log.d("HealthDataManager", "HC data: connected=${data.isConnected}, steps=${data.steps}, error=${data.connectionError}")
                        if (data.isConnected) data else null
                    }
                } else {
                    android.util.Log.d("HealthDataManager", "HC not available")
                    null
                }
            } catch (e: Exception) {
                android.util.Log.e("HealthDataManager", "Error with Health Connect refresh", e)
                null
            }

            if (hcResult != null && hcResult.isConnected) {
                android.util.Log.d("HealthDataManager", "REFRESH SUCCESS via HC")
                return@withContext hcResult
            }

            // Če Health Connect ni delovalo, poskusite z Google Fit
            val fitResult = try {
                android.util.Log.d("HealthDataManager", "Trying Google Fit")
                if (initializeGoogleFit() && hasGoogleFitPermissions()) {
                    readHealthDataFromGoogleFit()
                } else {
                    null
                }
            } catch (e: Exception) {
                android.util.Log.e("HealthDataManager", "Error with Google Fit refresh", e)
                null
            }

            if (fitResult != null && fitResult.isConnected) {
                android.util.Log.d("HealthDataManager", "REFRESH SUCCESS via Fit")
                return@withContext fitResult
            }

            // Če tudi to ni delovalo, poskusite Samsung Health
            val samsungResult = try {
                android.util.Log.d("HealthDataManager", "Trying Samsung Health")
                readHealthDataFromSamsungHealth()
            } catch (e: Exception) {
                android.util.Log.e("HealthDataManager", "Error with Samsung Health refresh", e)
                null
            }

            if (samsungResult != null && samsungResult.isConnected) {
                android.util.Log.d("HealthDataManager", "REFRESH SUCCESS via Samsung")
                return@withContext samsungResult
            }

            // Če nič ni delovalo
            val result = hcResult ?: fitResult ?: samsungResult ?: HealthData(connectionError = "Failed to refresh data. Make sure your device is synced.")
            android.util.Log.d("HealthDataManager", "REFRESH FALLBACK: ${result.connectionError}")
            result
        } catch (e: Exception) {
            android.util.Log.e("HealthDataManager", "Error refreshing data", e)
            getHealthData()
        }
    }

    // Glavni metod - preberi podatke od boljšega vira
    suspend fun getHealthData(): HealthData = withContext(Dispatchers.IO) {
        return@withContext try {
            if (!hasAnyWearableAppInstalled()) {
                return@withContext HealthData(connectionError = "No fitness app installed. Please install Samsung Health, Fitbit, or Google Fit.")
            }

            // Try Samsung Health first if installed
            val samsungData = try {
                if (isWearablePackageInstalled("com.samsung.android.shealth", listOf("com.sec.android.app.shealth"))) {
                    readHealthDataFromSamsungHealth()
                } else {
                    null
                }
            } catch (e: Exception) {
                null
            }

            // Try Health Connect (Android 14+)
            val healthConnectData = try {
                if (isHealthConnectAvailable()) {
                    if (healthConnectClient == null) {
                        initializeHealthConnect()
                    }
                    readHealthDataFromHealthConnect()
                } else {
                    null
                }
            } catch (e: Exception) {
                null
            }

            if (healthConnectData != null && healthConnectData.isConnected) {
                return@withContext healthConnectData
            }

            // Try Google Fit
            val fitData = try {
                if (initializeGoogleFit() && hasGoogleFitPermissions()) {
                    readHealthDataFromGoogleFit()
                } else {
                    null
                }
            } catch (e: Exception) {
                null
            }

            if (fitData != null && fitData.isConnected) {
                return@withContext fitData
            }

            // If all failed, return appropriate error message
            return@withContext when {
                samsungData != null -> samsungData
                healthConnectData != null -> healthConnectData
                fitData != null -> fitData
                else -> HealthData(connectionError = "No smartwatch data found. Make sure your watch is paired, synced, and has collected data.")
            }
        } catch (e: Exception) {
            HealthData(connectionError = "Error: ${e.message}")
        }
    }

    // Ensure Health Connect client exists so we can build permission intent in UI
    suspend fun ensureHealthConnectClient(): Boolean = withContext(Dispatchers.IO) {
        if (healthConnectClient != null) return@withContext true
        initializeHealthConnect()
    }

    // Get permission controller for requesting permissions
    fun getPermissionController() = healthConnectClient?.permissionController


    // Previously private; expose so UI can validate real apps before reporting connection.
    fun hasAnyWearableAppInstalled(): Boolean {
        val pm: PackageManager = context.packageManager
        return wearablePackages.any { pkg ->
            try {
                pm.getPackageInfo(pkg, 0)
                true
            } catch (_: Exception) {
                false
            }
        }
    }

    fun isWearablePackageInstalled(packageName: String, alternates: List<String> = emptyList()): Boolean {
        val candidates = buildList {
            if (packageName.isNotBlank()) add(packageName)
            alternates.filter { it.isNotBlank() }.forEach { add(it) }
        }
        val pm = context.packageManager
        return candidates.any { pkg ->
            try {
                pm.getPackageInfo(pkg, 0)
                true
            } catch (_: Exception) {
                false
            }
        }
    }
}
