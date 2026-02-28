package com.example.myapplication.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import com.example.myapplication.network.OpenFoodFactsAPI
import com.example.myapplication.network.OpenFoodFactsProduct
import com.google.common.util.concurrent.ListenableFuture
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BarcodeScannerScreen(
    onDismiss: () -> Unit,
    onProductScanned: (OpenFoodFactsProduct, String) -> Unit // dodal barcode parameter
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var scannedBarcode by remember { mutableStateOf<String?>(null) }
    var manualBarcode by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var scannedProduct by remember { mutableStateOf<OpenFoodFactsProduct?>(null) }
    var showManualInput by remember { mutableStateOf(false) }
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
    }

    val PrimaryBlue = Color(0xFF2563EB)

    // Funkcija za iskanje produkta
    fun searchProduct(barcode: String) {
        scope.launch {
            isLoading = true
            errorMessage = null
            scannedProduct = null

            try {
                val response = OpenFoodFactsAPI.getProductByBarcode(barcode)
                if (response?.product != null) {
                    scannedProduct = response.product
                } else {
                    errorMessage = "Product not found for barcode: $barcode"
                }
            } catch (e: Exception) {
                errorMessage = "Error: ${e.localizedMessage}"
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(scannedBarcode) {
        scannedBarcode?.let { barcode ->
            searchProduct(barcode)
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top Bar
            TopAppBar(
                title = { Text("Scan Barcode") },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = PrimaryBlue,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )

            if (scannedProduct != null) {
                // Prikaz produkta
                ProductDetailsCard(
                    product = scannedProduct!!,
                    barcode = scannedBarcode ?: "",
                    onAddToMeal = {
                        onProductScanned(scannedProduct!!, scannedBarcode ?: "")
                        onDismiss()
                    },
                    onScanAgain = {
                        scannedProduct = null
                        scannedBarcode = null
                        errorMessage = null
                    }
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (!hasCameraPermission) {
                        // Permission request
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                "Camera permission is required to scan barcodes",
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 32.dp)
                            )
                            Spacer(Modifier.height(16.dp))
                            Button(
                                onClick = { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) },
                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                            ) {
                                Text("Grant Permission")
                            }
                            Spacer(Modifier.height(16.dp))
                            TextButton(onClick = { showManualInput = true }) {
                                Text("Enter barcode manually")
                            }
                        }
                    } else if (showManualInput) {
                        // Manual input
                        ManualBarcodeInput(
                            barcode = manualBarcode,
                            onBarcodeChange = { manualBarcode = it },
                            onSearch = {
                                if (manualBarcode.isNotBlank()) {
                                    scannedBarcode = manualBarcode
                                }
                            },
                            onBackToCamera = { showManualInput = false },
                            isLoading = isLoading
                        )
                    } else {
                        // Camera preview
                        CameraPreview(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .padding(vertical = 16.dp),
                            onBarcodeDetected = { barcode ->
                                if (scannedBarcode == null && !isLoading) {
                                    scannedBarcode = barcode
                                }
                            }
                        )

                        TextButton(onClick = { showManualInput = true }) {
                            Text("Enter barcode manually", color = PrimaryBlue)
                        }
                    }

                    if (isLoading) {
                        Spacer(Modifier.height(16.dp))
                        CircularProgressIndicator(color = PrimaryBlue)
                        Text("Searching for product...", modifier = Modifier.padding(top = 8.dp))
                    }

                    errorMessage?.let { error ->
                        Spacer(Modifier.height(16.dp))
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                error,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CameraPreview(
    modifier: Modifier = Modifier,
    onBarcodeDetected: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraProviderFuture: ListenableFuture<ProcessCameraProvider> = remember {
        ProcessCameraProvider.getInstance(context)
    }
    val executor = remember { Executors.newSingleThreadExecutor() }

    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx)

            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also {
                        it.setAnalyzer(executor) { imageProxy ->
                            processImageProxy(imageProxy, onBarcodeDetected)
                        }
                    }

                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        imageAnalysis
                    )
                } catch (e: Exception) {
                    Log.e("CameraPreview", "Camera bind failed", e)
                }
            }, ContextCompat.getMainExecutor(ctx))

            previewView
        },
        modifier = modifier
            .background(Color.Black, RoundedCornerShape(16.dp))
    )
}

@androidx.annotation.OptIn(ExperimentalGetImage::class)
private fun processImageProxy(
    imageProxy: ImageProxy,
    onBarcodeDetected: (String) -> Unit
) {
    val mediaImage = imageProxy.image
    if (mediaImage != null) {
        val image = InputImage.fromMediaImage(
            mediaImage,
            imageProxy.imageInfo.rotationDegrees
        )

        val scanner = BarcodeScanning.getClient()
        scanner.process(image)
            .addOnSuccessListener { barcodes ->
                for (barcode in barcodes) {
                    when (barcode.valueType) {
                        Barcode.TYPE_PRODUCT -> {
                            barcode.rawValue?.let { value ->
                                Log.d("BarcodeScanner", "Detected barcode: $value")
                                onBarcodeDetected(value)
                            }
                        }
                        else -> {
                            barcode.rawValue?.let { value ->
                                Log.d("BarcodeScanner", "Detected barcode (other type): $value")
                                onBarcodeDetected(value)
                            }
                        }
                    }
                }
            }
            .addOnFailureListener { e ->
                Log.e("BarcodeScanner", "Barcode scanning failed", e)
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    } else {
        imageProxy.close()
    }
}

@Composable
private fun ManualBarcodeInput(
    barcode: String,
    onBarcodeChange: (String) -> Unit,
    onSearch: () -> Unit,
    onBackToCamera: () -> Unit,
    isLoading: Boolean
) {
    val PrimaryBlue = Color(0xFF2563EB)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "Enter Barcode Manually",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(24.dp))

        OutlinedTextField(
            value = barcode,
            onValueChange = onBarcodeChange,
            label = { Text("Barcode Number") },
            placeholder = { Text("e.g., 3017620422003") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = PrimaryBlue,
                focusedLabelColor = PrimaryBlue
            )
        )

        Spacer(Modifier.height(16.dp))

        Button(
            onClick = onSearch,
            enabled = barcode.isNotBlank() && !isLoading,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    color = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            } else {
                Text("Search Product")
            }
        }

        Spacer(Modifier.height(8.dp))

        TextButton(onClick = onBackToCamera) {
            Text("Back to Camera", color = PrimaryBlue)
        }
    }
}

@Composable
private fun ProductDetailsCard(
    product: OpenFoodFactsProduct,
    barcode: String,
    onAddToMeal: () -> Unit,
    onScanAgain: () -> Unit
) {
    val PrimaryBlue = Color(0xFF2563EB)
    val countryOfOrigin = OpenFoodFactsAPI.getCountryFromBarcode(barcode)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Product image
        product.imageUrl?.let { imageUrl ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = "Product image",
                    modifier = Modifier.fillMaxSize()
                )
            }
            Spacer(Modifier.height(16.dp))
        }

        // Product name
        Text(
            product.productName ?: "Unknown Product",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        product.brands?.let { brands ->
            Text(
                brands,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        // Barcode in država izvora
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Barcode badge
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    "📊 $barcode",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            // Country badge
            countryOfOrigin?.let { country ->
                Surface(
                    color = PrimaryBlue.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        "🌍 $country",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = PrimaryBlue
                    )
                }
            }
        }

        // Nutri-Score, Nova Group, Eco-Score
        product.nutriscoreGrade?.let { nutriscore ->
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    color = getNutriscoreColor(nutriscore),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "Nutri-Score",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White
                        )
                        Text(
                            nutriscore.uppercase(),
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }

                product.novaGroup?.let { nova ->
                    Surface(
                        color = getNovaColor(nova),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                "NOVA",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color.White
                            )
                            Text(
                                "Group $nova",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }

                product.ecoscoreGrade?.let { ecoscore ->
                    Surface(
                        color = getEcoscoreColor(ecoscore),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                "Eco-Score",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color.White
                            )
                            Text(
                                ecoscore.uppercase(),
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Nutrition info
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "Nutrition Facts (per 100g)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(12.dp))

                product.nutriments?.let { nutriments ->
                    nutriments.energyKcal100g?.let {
                        NutritionRow("Calories", "${it.toInt()} kcal")
                    }
                    nutriments.proteins100g?.let {
                        NutritionRow("Protein", "${it}g")
                    }
                    nutriments.carbohydrates100g?.let {
                        NutritionRow("Carbohydrates", "${it}g")
                    }
                    nutriments.sugars100g?.let {
                        NutritionRow("  Sugars", "${it}g")
                    }
                    nutriments.fat100g?.let {
                        NutritionRow("Fat", "${it}g")
                    }
                    nutriments.fiber100g?.let {
                        NutritionRow("Fiber", "${it}g")
                    }
                    nutriments.salt100g?.let {
                        NutritionRow("Salt", "${it}g")
                    }
                }
            }
        }

        // Alergeni
        if (!product.allergens.isNullOrBlank() || !product.allergensTags.isNullOrEmpty()) {
            Spacer(Modifier.height(12.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "⚠️ Allergens",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        product.allergens ?: product.allergensTags?.joinToString(", ") {
                            it.replace("en:", "").replace("-", " ").replaceFirstChar { c -> c.uppercase() }
                        } ?: "None",
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }

        // Ingredients
        product.ingredientsText?.let { ingredients ->
            if (ingredients.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "📝 Ingredients",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(ingredients, fontSize = 14.sp)
                    }
                }
            }
        }

        // Labels (Bio, Vegan, itd.)
        if (!product.labels.isNullOrBlank()) {
            Spacer(Modifier.height(12.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF10B981).copy(alpha = 0.1f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "🏷️ Labels & Certifications",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF10B981)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        product.labels,
                        color = Color(0xFF10B981),
                        fontSize = 14.sp
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Buttons
        Button(
            onClick = onAddToMeal,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
        ) {
            Text("Add to Meal", fontSize = 16.sp)
        }

        Spacer(Modifier.height(8.dp))

        OutlinedButton(
            onClick = onScanAgain,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Scan Another Product")
        }
    }
}

// Helper funkcije za barve
private fun getNutriscoreColor(grade: String): Color {
    return when (grade.lowercase()) {
        "a" -> Color(0xFF038C3E)
        "b" -> Color(0xFF85BB2F)
        "c" -> Color(0xFFFECB02)
        "d" -> Color(0xFFEE8100)
        "e" -> Color(0xFFE63E11)
        else -> Color.Gray
    }
}

private fun getNovaColor(group: Int): Color {
    return when (group) {
        1 -> Color(0xFF038C3E)
        2 -> Color(0xFF85BB2F)
        3 -> Color(0xFFEE8100)
        4 -> Color(0xFFE63E11)
        else -> Color.Gray
    }
}

private fun getEcoscoreColor(grade: String): Color {
    return when (grade.lowercase()) {
        "a" -> Color(0xFF038C3E)
        "b" -> Color(0xFF85BB2F)
        "c" -> Color(0xFFFECB02)
        "d" -> Color(0xFFEE8100)
        "e" -> Color(0xFFE63E11)
        else -> Color.Gray
    }
}

@Composable
private fun NutritionRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label)
        Text(value, fontWeight = FontWeight.SemiBold)
    }
}

