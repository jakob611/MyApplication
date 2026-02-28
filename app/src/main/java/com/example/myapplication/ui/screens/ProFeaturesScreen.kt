package com.example.myapplication.ui.screens

import android.app.Activity
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.billingclient.api.*

@Composable
fun ProFeaturesScreen(
    onContinue: () -> Unit,
    onBack: () -> Unit,
    onFreeTrial: () -> Unit,
    modifier: Modifier = Modifier,
    errorMessage: String? = null
) {
    val context = LocalContext.current
    var selectedPlan by remember { mutableStateOf(Plan.Yearly) } // Default to yearly
    var isLoading by remember { mutableStateOf(false) }

    // Initialize BillingClient
    val billingClient = remember {
        BillingClient.newBuilder(context)
            .setListener { billingResult, purchases ->
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
                    for (purchase in purchases) {
                        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                            // Verify purchase, give content, etc.
                            // For now we just proceed
                            Toast.makeText(context, "Subscription successful!", Toast.LENGTH_LONG).show()
                            onContinue()
                        }
                    }
                } else if (billingResult.responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {
                    // User canceled
                } else {
                    // Error
                    Toast.makeText(context, "Error: ${billingResult.debugMessage}", Toast.LENGTH_SHORT).show()
                }
            }
            .enablePendingPurchases()
            .build()
    }

    val scrollState = rememberScrollState()

    // Define colors for dark/light theme
    val bgColor = MaterialTheme.colorScheme.background
    val textColor = MaterialTheme.colorScheme.onBackground
    val grayPillColor = if (isSystemInDarkTheme()) Color.DarkGray else Color(0xFFE0E0E0)
    val bluePillColor = Color(0xFF2196F3) // Bright blue
    val bluePillTextColor = Color.White

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(bgColor)
            .statusBarsPadding()
            .verticalScroll(scrollState)
    ) {
        // Back Button x
        IconButton(
            onClick = onBack,
            modifier = Modifier.padding(top = 16.dp, start = 8.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = textColor,
                modifier = Modifier.size(32.dp)
            )
        }

        // Title
        Text(
            text = "Speed up your glow-up\nwith GlowUpp Pro",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 28.sp,
                lineHeight = 36.sp
            ),
            color = textColor,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Feature Comparison Rows
        FeatureRow("No chats with AI", "24/7 AI Coach", grayPillColor, textColor, bluePillColor, bluePillTextColor)
        Spacer(modifier = Modifier.height(12.dp))
        FeatureRow("No updates", "Weekly updates", grayPillColor, textColor, bluePillColor, bluePillTextColor)
        Spacer(modifier = Modifier.height(12.dp))
        FeatureRow("Limited view of macros", "Full view of Macros", grayPillColor, textColor, bluePillColor, bluePillTextColor)
        Spacer(modifier = Modifier.height(12.dp))
        FeatureRow("Adds", "No adds", grayPillColor, textColor, bluePillColor, bluePillTextColor)

        Spacer(modifier = Modifier.height(32.dp))

        // Plan Selection
        Text(
            text = "Choose your plan",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = textColor,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Yearly Plan
            PlanCard(
                title = "Yearly",
                price = "31.41€",
                subtitle = "/ year",
                isSelected = selectedPlan == Plan.Yearly,
                modifier = Modifier.weight(1f),
                badgeText = "Best Value",
                onClick = { selectedPlan = Plan.Yearly }
            )

            // Monthly Plan
            PlanCard(
                title = "Monthly",
                price = "3.14€",
                subtitle = "/ month",
                isSelected = selectedPlan == Plan.Monthly,
                modifier = Modifier.weight(1f),
                onClick = { selectedPlan = Plan.Monthly }
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Upgrade Button
        val buttonText = when (selectedPlan) {
            Plan.Yearly -> "Upgrade for 31.41€ / year"
            Plan.Monthly -> "Upgrade for 3.14€ / month"
        }

        Button(
            enabled = !isLoading,
            onClick = {
                isLoading = true
                // Start connection to Google Play
                billingClient.startConnection(object : BillingClientStateListener {
                    override fun onBillingSetupFinished(billingResult: BillingResult) {
                        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                            val productId = if (selectedPlan == Plan.Yearly) "pro_yearly" else "pro_monthly" // Make sure these exist in Play Console

                            val productList = listOf(
                                QueryProductDetailsParams.Product.newBuilder()
                                    .setProductId(productId)
                                    .setProductType(BillingClient.ProductType.SUBS)
                                    .build()
                            )

                            val params = QueryProductDetailsParams.newBuilder()
                                .setProductList(productList)
                                .build()

                            billingClient.queryProductDetailsAsync(params) { result, productDetailsList ->
                                if (result.responseCode == BillingClient.BillingResponseCode.OK && productDetailsList.isNotEmpty()) {
                                    val productDetails = productDetailsList[0]
                                    val offerToken = productDetails.subscriptionOfferDetails?.firstOrNull()?.offerToken

                                    if (offerToken != null) {
                                        val billingFlowParams = BillingFlowParams.newBuilder()
                                            .setProductDetailsParamsList(
                                                listOf(
                                                    BillingFlowParams.ProductDetailsParams.newBuilder()
                                                        .setProductDetails(productDetails)
                                                        .setOfferToken(offerToken)
                                                        .build()
                                                )
                                            )
                                            .build()
                                        billingClient.launchBillingFlow(context as Activity, billingFlowParams)
                                    } else {
                                        // Handle missing offer token
                                        Toast.makeText(context, "Offer not available", Toast.LENGTH_SHORT).show()
                                    }
                                } else {
                                    // Handle missing product or error
                                    Toast.makeText(context, "Product not found: $productId", Toast.LENGTH_SHORT).show()
                                }
                                isLoading = false
                            }
                        } else {
                            isLoading = false
                            Toast.makeText(context, "Billing setup failed", Toast.LENGTH_SHORT).show()
                        }
                    }

                    override fun onBillingServiceDisconnected() {
                        isLoading = false
                        Toast.makeText(context, "Service disconnected", Toast.LENGTH_SHORT).show()
                    }
                })
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .height(56.dp)
                .shadow(elevation = 8.dp, shape = RoundedCornerShape(28.dp)),
            colors = ButtonDefaults.buttonColors(
                containerColor = bluePillColor
            ),
            shape = RoundedCornerShape(28.dp)
        ) {
            if (isLoading) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
            } else {
                Text(
                    text = buttonText,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }

        // Free Trial Link
        TextButton(
            onClick = onFreeTrial,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(top = 8.dp)
        ) {
            Text(
                text = "Start Free Trial",
                color = textColor.copy(alpha = 0.7f),
                fontWeight = FontWeight.SemiBold
            )
        }

        if (errorMessage != null) {
            Text(
                text = errorMessage,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .padding(16.dp)
                    .align(Alignment.CenterHorizontally)
            )
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
fun FeatureRow(
    leftText: String,
    rightText: String,
    leftBgColor: Color,
    leftTextColor: Color,
    rightBgColor: Color,
    rightTextColor: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left Pill (Free feature - usually negative)
        Box(
            modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .clip(RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp))
                .background(leftBgColor)
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = leftText,
                color = leftTextColor,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.width(4.dp))

        // Right Pill (Pro feature - positive)
        Box(
            modifier = Modifier
                .weight(1.2f) // Slightly larger to emphasize
                .height(56.dp)
                .clip(RoundedCornerShape(topEnd = 28.dp, bottomEnd = 28.dp))
                .background(
                    brush = Brush.horizontalGradient(
                        colors = listOf(rightBgColor, rightBgColor.copy(alpha = 0.8f))
                    )
                )
                .padding(horizontal = 12.dp)
                .shadow(8.dp, RoundedCornerShape(topEnd = 28.dp, bottomEnd = 28.dp), spotColor = rightBgColor), // Glow effect
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = rightText,
                color = rightTextColor,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun PlanCard(
    title: String,
    price: String,
    subtitle: String,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    badgeText: String? = null,
    onClick: () -> Unit
) {
    val borderColor = if (isSelected) Color(0xFF2196F3) else Color.Transparent
    val borderWidth = if (isSelected) 3.dp else 0.dp
    val backgroundColor = if (isSelected) Color(0xFF2196F3).copy(alpha = 0.1f) else MaterialTheme.colorScheme.surfaceVariant

    Box(
        modifier = modifier
            .height(160.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(backgroundColor)
            .border(borderWidth, borderColor, RoundedCornerShape(16.dp))
            .clickable { onClick() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = price,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.ExtraBold,
                color = if (isSelected) Color(0xFF2196F3) else MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (badgeText != null) {
            Surface(
                color = Color(0xFFFFC107), // Gold/Amber
                shape = RoundedCornerShape(bottomStart = 8.dp),
                modifier = Modifier.align(Alignment.TopEnd)
            ) {
                Text(
                    text = badgeText,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
            }
        }

        if (isSelected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Selected",
                tint = Color(0xFF2196F3),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
                    .size(24.dp)
            )
        }
    }
}

enum class Plan {
    Monthly, Yearly
}
