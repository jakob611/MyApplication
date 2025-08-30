package com.example.myapplication.ui.screens

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.billingclient.api.*

@Composable
fun ProSubscriptionScreen(
    onBack: () -> Unit = {},
    onSubscribed: () -> Unit = {}
) {
    val context = LocalContext.current
    var isLoading by remember { mutableStateOf(false) }
    var billingResultText by remember { mutableStateOf<String?>(null) }

    // Billing setup
    val billingClient = remember {
        BillingClient.newBuilder(context)
            .setListener { billingResult, purchases ->
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
                    for (purchase in purchases) {
                        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                            // TODO: Zabeleži PRO status (shrani v DB/server)
                            billingResultText = "Uspešno si nadgradil na PRO! 🎉"
                            onSubscribed()
                        }
                    }
                } else if (billingResult.responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {
                    billingResultText = "Nakup preklican."
                } else {
                    billingResultText = "Napaka pri nakupu: ${billingResult.debugMessage}"
                }
            }
            .enablePendingPurchases()
            .build()
    }

    // Prikaz UI
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFFF1F6FF)),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .padding(16.dp),
            elevation = CardDefaults.cardElevation(8.dp)
        ) {
            Column(
                Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    repeat(3) {
                        Icon(
                            Icons.Filled.Star,
                            contentDescription = null,
                            tint = Color(0xFFFFD600),
                            modifier = Modifier
                                .size(32.dp)
                                .padding(horizontal = 6.dp)
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Nadgradi na PRO",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "Odkleni napredne AI funkcije:\n• Personalizirani AI trener\n• Premium workout generator\n• Napredno sledenje napredku in še več!",
                    style = MaterialTheme.typography.bodyLarge,
                    fontSize = 18.sp
                )
                Spacer(Modifier.height(20.dp))
                Text(
                    "Samo 4,99 € / mesec",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.secondary
                )
                Spacer(Modifier.height(24.dp))

                Button(
                    enabled = !isLoading,
                    onClick = {
                        isLoading = true
                        billingResultText = null

                        // 1. Poveži se na Google Play
                        billingClient.startConnection(object : BillingClientStateListener {
                            override fun onBillingSetupFinished(billingResult: BillingResult) {
                                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                                    // 2. Najdi produkt iz Play Console (ID: "pro_subscription")
                                    val params = QueryProductDetailsParams.newBuilder()
                                        .setProductList(
                                            listOf(
                                                QueryProductDetailsParams.Product.newBuilder()
                                                    .setProductId("pro_subscription")
                                                    .setProductType(BillingClient.ProductType.SUBS)
                                                    .build()
                                            )
                                        )
                                        .build()
                                    billingClient.queryProductDetailsAsync(params) { result, productDetailsList ->
                                        if (result.responseCode == BillingClient.BillingResponseCode.OK && productDetailsList.isNotEmpty()) {
                                            val productDetails = productDetailsList[0]
                                            val offerToken = productDetails.subscriptionOfferDetails?.firstOrNull()?.offerToken
                                            if (offerToken != null) {
                                                // 3. Prikaži Google Billing flow
                                                val billingParams = BillingFlowParams.newBuilder()
                                                    .setProductDetailsParamsList(
                                                        listOf(
                                                            BillingFlowParams.ProductDetailsParams.newBuilder()
                                                                .setProductDetails(productDetails)
                                                                .setOfferToken(offerToken)
                                                                .build()
                                                        )
                                                    )
                                                    .build()
                                                billingClient.launchBillingFlow(context as Activity, billingParams)
                                                isLoading = false
                                            } else {
                                                billingResultText = "PRO ponudba ni na voljo."
                                                isLoading = false
                                            }
                                        } else {
                                            billingResultText = "Napaka pri iskanju produkta: ${result.debugMessage}"
                                            isLoading = false
                                        }
                                    }
                                } else {
                                    billingResultText = "Napaka pri povezavi na Google Play: ${billingResult.debugMessage}"
                                    isLoading = false
                                }
                            }

                            override fun onBillingServiceDisconnected() {
                                billingResultText = "Povezava na Google Play prekinjena."
                                isLoading = false
                            }
                        })
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                ) {
                    Text(if (isLoading) "Nalagam..." else "NADGRADI NA PRO")
                }
                TextButton(
                    onClick = onBack,
                    modifier = Modifier.padding(top = 10.dp)
                ) { Text("Nazaj") }

                if (billingResultText != null) {
                    Spacer(Modifier.height(16.dp))
                    Text(billingResultText!!, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}