@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.myapplication.ui.screens

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.SystemClock
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.View
import androidx.annotation.ColorInt
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Brush
import coil.compose.rememberAsyncImagePainter
import com.example.myapplication.data.HealthStorage
import com.example.myapplication.health.HealthConnectManager
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import com.example.myapplication.network.FatSecretApi
import com.example.myapplication.network.FoodDetail
import com.example.myapplication.network.FoodSummary
import com.example.myapplication.network.OpenFoodFactsProduct
import com.example.myapplication.network.RecipeSummary
import com.example.myapplication.network.RecipeDetail
import com.example.myapplication.ui.theme.DrawerBlue
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

// Donut view (custom) - Dual ring with segmented outer ring
@SuppressLint("ViewConstructor")
class DonutProgressView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val outerThicknessPx = dp(20f)
    private val innerThicknessPx = dp(16f)

    // Simple mode - single blue color, no segments, no click interaction
    var simpleMode: Boolean = true
        set(value) { field = value; invalidate() }

    @ColorInt var outerBaseColor: Int = 0xFFE5E7EB.toInt()
        set(value) { field = value; outerBasePaint.color = value; invalidate() }

    // Proportions for 3 segments (fat, protein, carbs)
    var fatProportion: Float = 0f
        set(value) { field = value; invalidate() }
    var proteinProportion: Float = 0f
        set(value) { field = value; invalidate() }
    var carbsProportion: Float = 0f
        set(value) { field = value; invalidate() }

    var innerProgress: Float = 0f
        set(value) { field = value; invalidate() }

    @ColorInt var innerBaseColor: Int = 0xFFE5E7EB.toInt()
        set(value) { field = value; innerBasePaint.color = value; invalidate() }
    @ColorInt var innerProgressColor: Int = 0xFF2563EB.toInt()
        set(value) { field = value; innerProgressPaint.color = value; invalidate() }

    var startAngle: Float = 135f
    var sweepAngle: Float = 270f

    @ColorInt var textColor: Int = 0xFF000000.toInt()
        set(value) { field = value; invalidate() }

    @ColorInt var waterColor: Int = 0xFF3B82F6.toInt()
        set(value) { field = value; invalidate() }

    var weightUnit: String = "kg"
        set(value) { field = value; invalidate() }

    var centerValue: String = "0"
        set(value) { field = value; invalidate() }
    var centerLabel: String = "kcal"
        set(value) { field = value; invalidate() }
    var innerValue: String = "0"
        set(value) { field = value; invalidate() }
    var innerLabel: String = "ml"
        set(value) { field = value; invalidate() }
    var consumedCalories: Int = 0
        set(value) { field = value; invalidate() }
    var targetCalories: Int = 1
        set(value) { field = if (value <= 0) 1 else value; invalidate() }

    // Click handling
    var onSegmentClick: ((segment: String) -> Unit)? = null
    internal var clickedSegment: String? = null

    // Segment data for tooltip
    var fatCalories: Int = 0
    var proteinCalories: Int = 0
    var carbsCalories: Int = 0

    private val outerBasePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = outerThicknessPx
        color = outerBaseColor
    }

    private val outerFatPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = outerThicknessPx
        color = 0xFFEF4444.toInt() // Red for fat
    }

    private val outerProteinPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = outerThicknessPx
        color = 0xFF10B981.toInt() // Green for protein
    }

    private val outerCarbsPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = outerThicknessPx
        color = 0xFF3B82F6.toInt() // Blue for carbs
    }

    private val innerBasePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = innerThicknessPx
        color = innerBaseColor
    }

    private val innerProgressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = innerThicknessPx
        color = innerProgressColor
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        color = 0xFF000000.toInt()
    }

    // Optimization: Reusable paints to avoid allocation in onDraw
    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = outerThicknessPx * 1.2f
    }

    private val overPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = outerThicknessPx * 0.35f
        color = 0xFFDC2626.toInt()
    }

    private val outerArcRect = RectF()
    private val innerArcRect = RectF()

    init {
        isClickable = true
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = MeasureSpec.getSize(heightMeasureSpec)
        val size = if (w == 0 || h == 0) maxOf(w, h) else minOf(w, h)
        val finalSize = if (size == 0) dp(240f).toInt() else size
        setMeasuredDimension(finalSize, finalSize)
        updateRects()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateRects()
    }

    private fun updateRects() {
        val outerHalf = outerThicknessPx / 2f
        outerArcRect.set(
            paddingLeft + outerHalf,
            paddingTop + outerHalf,
            width - paddingRight - outerHalf,
            height - paddingBottom - outerHalf
        )

        val innerMargin = outerThicknessPx + dp(4f)
        val innerHalf = innerThicknessPx / 2f
        innerArcRect.set(
            paddingLeft + innerMargin + innerHalf,
            paddingTop + innerMargin + innerHalf,
            width - paddingRight - innerMargin - innerHalf,
            height - paddingBottom - innerMargin - innerHalf
        )
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // In simple mode, don't handle segment clicks
        if (simpleMode) {
            return super.onTouchEvent(event)
        }

        if (event.action == MotionEvent.ACTION_DOWN) {
            val x = event.x
            val y = event.y
            val centerX = width / 2f
            val centerY = height / 2f
            val dx = x - centerX
            val dy = y - centerY
            val distance = kotlin.math.sqrt(dx * dx + dy * dy)
            val outerRadius = outerArcRect.width() / 2f
            val innerRadius = outerRadius - outerThicknessPx
            val touchTolerance = dp(24f)
            val expandedOuterRadius = outerRadius + touchTolerance
            val expandedInnerRadius = (innerRadius - touchTolerance).coerceAtLeast(0f)
            if (distance >= expandedInnerRadius && distance <= expandedOuterRadius) {
                var touchAngle = Math.toDegrees(kotlin.math.atan2(dy.toDouble(), dx.toDouble())).toFloat()
                touchAngle = (touchAngle + 360) % 360
                val adjustedAngle = (touchAngle - startAngle + 360) % 360
                val consumedFraction = (consumedCalories / targetCalories.toFloat()).coerceIn(0f, 1f)
                val availableSweep = sweepAngle * consumedFraction
                if (adjustedAngle <= availableSweep) {
                    val totalMacroCalories = fatCalories + proteinCalories + carbsCalories
                    if (totalMacroCalories > 0) {
                        val fatSweep = sweepAngle * (fatCalories.toFloat() / targetCalories.toFloat())
                        val proteinSweep = sweepAngle * (proteinCalories.toFloat() / targetCalories.toFloat())
                        val fatEnd = fatSweep
                        val proteinEnd = fatEnd + proteinSweep
                        clickedSegment = when {
                            adjustedAngle <= fatEnd -> "fat"
                            adjustedAngle <= proteinEnd -> "protein"
                            else -> "carbs"
                        }
                        performClick()
                        return true
                    }
                }
                clickedSegment = null
                performClick()
                return true
            }
            clickedSegment = null
            performClick()
            return true
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        // Accessibility event
        onSegmentClick?.invoke(clickedSegment ?: "")
        invalidate()
        return super.performClick()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val consumedFraction = (consumedCalories / targetCalories.toFloat()).coerceIn(0f, 1f)
        val availableSweep = sweepAngle * consumedFraction

        // Background for consumed portion
        if (availableSweep > 0.5f) {
            canvas.drawArc(outerArcRect, startAngle, availableSweep, false, outerBasePaint)
        }

        // SIMPLE MODE: Draw single blue arc without segments
        if (simpleMode) {
            if (availableSweep > 0.5f) {
                canvas.drawArc(outerArcRect, startAngle, availableSweep, false, outerCarbsPaint) // Blue color
            }
        } else {
            // DETAILED MODE: Draw segmented arc (fat/protein/carbs)
            var remainingSweep = availableSweep
            var currentAngle = startAngle

            fun drawSegment(segmentCalories: Int, paint: Paint, isClicked: Boolean = false) {
                if (remainingSweep <= 0f || segmentCalories <= 0) return
                val segSweepFull = sweepAngle * (segmentCalories / targetCalories.toFloat())
                val segSweep = segSweepFull.coerceAtMost(remainingSweep)
                if (segSweep > 0.5f) {
                    // Optimization: reuse highlightPaint instead of creating new Paint
                    val drawPaint = if (isClicked) {
                        highlightPaint.apply {
                            color = paint.color
                            // strokeWidth is already set in init
                        }
                    } else {
                        paint
                    }
                    canvas.drawArc(outerArcRect, currentAngle, segSweep, false, drawPaint)
                    currentAngle += segSweep
                    remainingSweep -= segSweep
                }
            }

            drawSegment(fatCalories, outerFatPaint, clickedSegment == "fat")
            drawSegment(proteinCalories, outerProteinPaint, clickedSegment == "protein")
            drawSegment(carbsCalories, outerCarbsPaint, clickedSegment == "carbs")

            // Over-target warning
            if (consumedFraction >= 1f && (fatCalories + proteinCalories + carbsCalories) > targetCalories) {
                canvas.drawArc(outerArcRect, startAngle, sweepAngle, false, overPaint)
            }
        }

        // Inner ring (water)
        canvas.drawArc(innerArcRect, startAngle, sweepAngle, false, innerBasePaint)
        canvas.drawArc(innerArcRect, startAngle, sweepAngle * innerProgress, false, innerProgressPaint)

        val centerX = width / 2f
        val centerY = height / 2f

        // Show segment details on click (only in detailed mode)
        if (!simpleMode && clickedSegment != null) {
            val isImperial = weightUnit == "lbs" || weightUnit == "lb"
            val unitLabel = if (isImperial) "oz" else "g"

            val details = when (clickedSegment) {
                "fat" -> {
                    val g = fatCalories / 9.0
                    val value = if (isImperial) (g / 28.3495).roundToInt() else g.roundToInt()
                    SegmentDetails("$value $unitLabel", "Fat", 0xFFEF4444.toInt(), fatCalories)
                }
                "protein" -> {
                    val g = proteinCalories / 4.0
                    val value = if (isImperial) (g / 28.3495).roundToInt() else g.roundToInt()
                    SegmentDetails("$value $unitLabel", "Protein", 0xFF10B981.toInt(), proteinCalories)
                }
                "carbs" -> {
                    val g = carbsCalories / 4.0
                    val value = if (isImperial) (g / 28.3495).roundToInt() else g.roundToInt()
                    SegmentDetails("$value $unitLabel", "Carbs", 0xFF3B82F6.toInt(), carbsCalories)
                }
                else -> SegmentDetails(centerValue, centerLabel, textColor, 0)
            }

            // Grams (large, top)
            textPaint.color = details.color
            textPaint.textSize = dp(48f)
            textPaint.isFakeBoldText = true
            canvas.drawText(details.value, centerX, centerY - dp(10f), textPaint)

            // Label
            textPaint.textSize = dp(18f)
            textPaint.isFakeBoldText = false
            textPaint.color = 0xFF6B7280.toInt()
            canvas.drawText(details.label, centerX, centerY + dp(15f), textPaint)

            // Calories
            textPaint.textSize = dp(22f)
            textPaint.isFakeBoldText = true
            textPaint.color = details.color
            canvas.drawText("${details.calories} kcal", centerX, centerY + dp(45f), textPaint)

        } else {
            // Water (top, themed)
            textPaint.color = waterColor
            textPaint.textSize = dp(40f)
            textPaint.isFakeBoldText = true
            canvas.drawText(innerValue, centerX, centerY - dp(10f), textPaint)

            textPaint.textSize = dp(16f)
            textPaint.isFakeBoldText = false
            textPaint.color = 0xFF6B7280.toInt() // Label color (gray)
            canvas.drawText(innerLabel, centerX, centerY + dp(10f), textPaint)

            // Calories (bottom)
            textPaint.color = textColor
            textPaint.textSize = dp(28f)
            textPaint.isFakeBoldText = true
            canvas.drawText(centerValue, centerX, centerY + dp(45f), textPaint)

            textPaint.textSize = dp(14f)
            textPaint.isFakeBoldText = false
            textPaint.color = 0xFF6B7280.toInt() // Subtitle color (gray)
            canvas.drawText(centerLabel, centerX, centerY + dp(62f), textPaint)
        }
    }

    private data class SegmentDetails(val value: String, val label: String, val color: Int, val calories: Int)

    private fun dp(v: Float) = v * resources.displayMetrics.density
}

// Lokalni modeli
internal enum class MealType(val title: String) { Breakfast("Breakfast"), Lunch("Lunch"), Dinner("Dinner"), Snacks("Snacks") }

internal data class TrackedFood(
    val id: String,
    val name: String,
    val meal: MealType,
    val amount: Double,
    val unit: String,
    val caloriesKcal: Double,
    val proteinG: Double?,
    val carbsG: Double?,
    val fatG: Double?,
    val fiberG: Double? = null,
    val sugarG: Double? = null,
    val saturatedFatG: Double? = null,
    val sodiumMg: Double? = null,
    val potassiumMg: Double? = null,
    val cholesterolMg: Double? = null,
    val barcode: String? = null
)

// Tarče iz plana (parser)
private data class ParsedTargets(val calories: Int?, val proteinG: Int?, val carbsG: Int?, val fatG: Int?)
private fun parseMacroBreakdown(text: String?): ParsedTargets {
    if (text.isNullOrBlank()) return ParsedTargets(null, null, null, null)
    // Fixed: Regex redundant escape
    val proteinTotalRe = Regex("""Protein:\s*[\d.]+g/kg\s*\(([\d.]+)g total\)""", RegexOption.IGNORE_CASE)
    val proteinSimpleRe = Regex("""Protein:\s*([\d.]+)g(?:\b|,)""", RegexOption.IGNORE_CASE)
    val carbsRe = Regex("""Carbs:\s*([\d.]+)g""", RegexOption.IGNORE_CASE)
    val fatRe = Regex("""Fat:\s*([\d.]+)g""", RegexOption.IGNORE_CASE)
    val caloriesRe = Regex("""Calories:\s*([\d.]+)\s*kcal""", RegexOption.IGNORE_CASE)

    val protein = proteinTotalRe.find(text)?.groupValues?.getOrNull(1)?.toDoubleOrNull()?.toInt()
        ?: proteinSimpleRe.find(text)?.groupValues?.getOrNull(1)?.toDoubleOrNull()?.toInt()
    val carbs = carbsRe.find(text)?.groupValues?.getOrNull(1)?.toDoubleOrNull()?.toInt()
    val fat = fatRe.find(text)?.groupValues?.getOrNull(1)?.toDoubleOrNull()?.toInt()
    val calories = caloriesRe.find(text)?.groupValues?.getOrNull(1)?.toDoubleOrNull()?.toInt()
    return ParsedTargets(calories, protein, carbs, fat)
}

private fun formatMacroWeight(grams: Double?, unitPreference: String): String {
    val g = grams ?: 0.0
    return if (unitPreference == "lbs" || unitPreference == "lb") {
        val oz = g / 28.3495
        "${oz.roundToInt()} oz"
    } else {
        "${g.roundToInt()} g"
    }
}

private fun macroLabel(label: String, consumed: Double, target: Int, unitPreference: String): String {
    val emoji = when(label) {
        "Protein" -> "🥩"
        "Fat" -> "🥑"
        "Carbs" -> "🍞"
        else -> ""
    }

    val consumedStr = formatMacroWeight(consumed, unitPreference).replace(Regex("[a-zA-Z ]+"), "")
    val targetGrams = target.toDouble()
    val targetStr = formatMacroWeight(targetGrams, unitPreference)

    // formatMacroWeight returns "XX g" or "YY oz". We want "XX/YY g" or "XX/YY oz"
    // So let's split the unit out

    val unit = if (unitPreference == "lbs" || unitPreference == "lb") "oz" else "g"
    val cVal = if (unit == "oz") (consumed / 28.3495).roundToInt() else consumed.roundToInt()
    val tVal = if (unit == "oz") (targetGrams / 28.3495).roundToInt() else targetGrams.roundToInt()

    return if (target > 0) "$emoji $label: $cVal/$tVal $unit" else "$emoji $label: $cVal $unit"
}

// ===== RECIPES SECTION =====

@Composable
fun RecipesSearchSection(onScanBarcode: () -> Unit = {}, onOpenEAdditives: () -> Unit = {}, userProfile: com.example.myapplication.data.UserProfile) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf<List<RecipeSummary>>(emptyList()) }
    var selectedRecipe by remember { mutableStateOf<RecipeDetail?>(null) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var hasSearched by remember { mutableStateOf(false) }
    val view = androidx.compose.ui.platform.LocalView.current

    val textColor = MaterialTheme.colorScheme.onBackground
    val surfaceColor = MaterialTheme.colorScheme.surface

    fun doSearch() {
        scope.launch {
            searching = true
            errorMsg = null
            hasSearched = true
            runCatching { FatSecretApi.searchRecipes(query, 1, 20) }
                .onSuccess {
                    results = it
                    Log.d("RecipesSearch", "Found ${it.size} recipes")

                    // Scroll down to show results
                    kotlinx.coroutines.delay(300) // Počakaj da se UI posodobi
                    try {
                        var parent = view.parent
                        while (parent != null) {
                            if (parent is androidx.core.widget.NestedScrollView) {
                                val scrollView = parent // No cast needed
                                scrollView.post {
                                    // Namesto fullScroll, scroll do view pozicije
                                    // Izračunaj pozicijo view-a relativno na scrollView
                                    val location = IntArray(2)
                                    view.getLocationInWindow(location)
                                    val scrollViewLocation = IntArray(2)
                                    scrollView.getLocationInWindow(scrollViewLocation)

                                    // Scroll tako da je view viden, z malo prostora na vrhu
                                    val targetY = location[1] - scrollViewLocation[1] - 100
                                    scrollView.smoothScrollTo(0, scrollView.scrollY + targetY)
                                }
                                break
                            }
                            parent = parent.parent
                        }
                    } catch (e: Exception) {
                        Log.e("RecipesSearch", "Scroll failed: ${e.message}")
                    }
                }
                .onFailure { e ->
                    errorMsg = e.message ?: "Search failed"
                    Log.e("RecipesSearch", "Search failed: ${e.message}", e)
                }
            searching = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        // Row sa dva gumba
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Scan Barcode gumb - leva polovica
            Button(
                onClick = onScanBarcode,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        painter = painterResource(id = android.R.drawable.ic_menu_camera),
                        contentDescription = "Scan",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Scan Barcode",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }

            // E-Additives Info gumb - desna polovica
            Button(
                onClick = onOpenEAdditives,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        "🧪",
                        fontSize = 24.sp
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "E-Additives",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        }

        Text(
            "Recommended Recipes",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = textColor,
            modifier = Modifier.padding(vertical = 8.dp)
        )

        // Search bar - bolj zaobljen in nižji
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                placeholder = { Text("Search recipes (e.g., Chicken Salad)",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp) },
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp),
                keyboardActions = KeyboardActions(onSearch = { doSearch() }),
                shape = RoundedCornerShape(20.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = textColor,
                    unfocusedTextColor = textColor,
                    disabledTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    cursorColor = DrawerBlue,
                    focusedContainerColor = surfaceColor,
                    unfocusedContainerColor = surfaceColor,
                    disabledContainerColor = surfaceColor,
                    focusedBorderColor = DrawerBlue,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    disabledBorderColor = MaterialTheme.colorScheme.outlineVariant
                ),
                textStyle = TextStyle(fontSize = 14.sp)
            )
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = { doSearch() },
                colors = ButtonDefaults.buttonColors(containerColor = DrawerBlue),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.height(50.dp)
            ) {
                Text("Search", fontSize = 14.sp)
            }
        }

        if (searching) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                color = DrawerBlue
            )
        }

        errorMsg?.let { msg ->
            Text(
                text = "Error: $msg",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        // Show "No items found" if searched but no results
        if (hasSearched && !searching && results.isEmpty() && errorMsg == null) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "🔍",
                        fontSize = 32.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Text(
                        "No items found",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = textColor
                    )
                    Text(
                        "Try searching with different keywords",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }

        if (results.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                results.forEach { recipe ->
                    RecipeCard(
                        recipe = recipe,
                        onClick = {
                            scope.launch {
                                runCatching { FatSecretApi.getRecipeDetail(recipe.id) }
                                    .onSuccess { selectedRecipe = it }
                            }
                        },
                        userProfile = userProfile
                    )
                }
            }
        }
    }

    selectedRecipe?.let { recipe ->
        RecipeDetailDialog(
            recipe = recipe,
            onDismiss = { selectedRecipe = null },
            userProfile = userProfile
        )
    }
}

@Composable
private fun RecipeCard(
    recipe: RecipeSummary,
    onClick: () -> Unit,
    userProfile: com.example.myapplication.data.UserProfile
) {
    val textColor = MaterialTheme.colorScheme.onSurface
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant

    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = surfaceVariant),
        shape = MaterialTheme.shapes.large,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Recipe image if available
            recipe.imageUrl?.let { imageUrl ->
                Image(
                    painter = rememberAsyncImagePainter(imageUrl),
                    contentDescription = recipe.name,
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop
                )
                Spacer(Modifier.width(12.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    recipe.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = textColor,
                    maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                recipe.description?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    recipe.caloriesKcal?.let {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("🔥", fontSize = 14.sp)
                            Spacer(Modifier.width(3.dp))
                            Text("$it", style = MaterialTheme.typography.labelMedium, color = DrawerBlue, fontWeight = FontWeight.Bold)
                        }
                    }
                    recipe.proteinG?.let {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("🥩", fontSize = 13.sp)
                            Spacer(Modifier.width(3.dp))
                            Text(formatMacroWeight(it, userProfile.weightUnit), style = MaterialTheme.typography.labelSmall, color = textColor)
                        }
                    }
                    recipe.carbsG?.let {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("🍞", fontSize = 13.sp)
                            Spacer(Modifier.width(3.dp))
                            Text(formatMacroWeight(it, userProfile.weightUnit), style = MaterialTheme.typography.labelSmall, color = textColor)
                        }
                    }
                    recipe.fatG?.let {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("🥑", fontSize = 13.sp)
                            Spacer(Modifier.width(3.dp))
                            Text(formatMacroWeight(it, userProfile.weightUnit), style = MaterialTheme.typography.labelSmall, color = textColor)
                        }
                    }
                }
            }

            Spacer(Modifier.width(12.dp))

            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = "View details",
                tint = DrawerBlue,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

@Composable
private fun RecipeDetailDialog(
    recipe: RecipeDetail,
    onDismiss: () -> Unit,
    userProfile: com.example.myapplication.data.UserProfile
) {
    val textColor = MaterialTheme.colorScheme.onSurface
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant

    // Debug logging
    LaunchedEffect(recipe) {
        Log.d("RecipeDetail", "Recipe: ${recipe.name}")
        Log.d("RecipeDetail", "Ingredients count: ${recipe.ingredients?.size ?: 0}")
        Log.d("RecipeDetail", "Directions count: ${recipe.directions?.size ?: 0}")
        recipe.directions?.forEachIndexed { i, dir ->
            Log.d("RecipeDetail", "Direction $i: $dir")
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(contentColor = DrawerBlue)
            ) {
                Text("Close", fontWeight = FontWeight.Bold)
            }
        },
        title = {
            Column {
                Text(
                    recipe.name,
                    color = textColor,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                recipe.description?.let {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    // Info Section with icon badges
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            recipe.caloriesKcal?.let {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("🔥", fontSize = 24.sp)
                                    Text("$it", color = DrawerBlue, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    Text("kcal", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            recipe.servings?.let {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("🍽️", fontSize = 24.sp)
                                    Text("$it", color = textColor, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    Text("servings", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            recipe.prepTimeMin?.let {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("⏱️", fontSize = 24.sp)
                                    Text("$it", color = textColor, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    Text("prep min", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            recipe.cookTimeMin?.let {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("🔥", fontSize = 24.sp)
                                    Text("$it", color = textColor, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    Text("cook min", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }

                // Macros if available
                if (recipe.proteinG != null || recipe.carbsG != null || recipe.fatG != null) {
                    item {
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = surfaceVariant,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                recipe.proteinG?.let {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("🥩", fontSize = 20.sp)
                                        Text(formatMacroWeight(it, userProfile.weightUnit), fontWeight = FontWeight.Bold, color = textColor)
                                        Text("Protein", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                recipe.carbsG?.let {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("🍞", fontSize = 20.sp)
                                        Text(formatMacroWeight(it, userProfile.weightUnit), fontWeight = FontWeight.Bold, color = textColor)
                                        Text("Carbs", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                recipe.fatG?.let {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("🥑", fontSize = 20.sp)
                                        Text(formatMacroWeight(it, userProfile.weightUnit), fontWeight = FontWeight.Bold, color = textColor)
                                        Text("Fat", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }

                item { Spacer(Modifier.height(8.dp)) }

                // Ingredients Section
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("📝", fontSize = 20.sp)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "Ingredients",
                            fontWeight = FontWeight.Bold,
                            color = textColor,
                            fontSize = 18.sp
                        )
                    }
                }

                item {
                    if (recipe.ingredients.isNullOrEmpty()) {
                        Text(
                            "No ingredients available",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 14.sp,
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    } else {
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = surfaceVariant,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                recipe.ingredients.forEach { ingredient ->
                                    Row(
                                        modifier = Modifier.padding(vertical = 2.dp),
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        Text("•", color = DrawerBlue, fontSize = 16.sp, modifier = Modifier.padding(end = 6.dp))
                                        Text(ingredient, color = textColor, fontSize = 14.sp, lineHeight = 20.sp)
                                    }
                                }
                            }
                        }
                    }
                }

                item { Spacer(Modifier.height(8.dp)) }

                // Directions Section
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("👨‍🍳", fontSize = 20.sp)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "Directions",
                            fontWeight = FontWeight.Bold,
                            color = textColor,
                            fontSize = 18.sp
                        )
                    }
                }

                item {
                    if (recipe.directions.isNullOrEmpty()) {
                        Text(
                            "No directions available",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 14.sp,
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            recipe.directions.forEachIndexed { index, direction ->
                                Surface(
                                    shape = MaterialTheme.shapes.medium,
                                    color = surfaceVariant,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        Surface(
                                            shape = MaterialTheme.shapes.small,
                                            color = DrawerBlue,
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(
                                                    "${index + 1}",
                                                    color = Color.White,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                        Spacer(Modifier.width(10.dp))
                                        Text(
                                            direction.removePrefix("${index + 1}. "),
                                            color = textColor,
                                            fontSize = 14.sp,
                                            lineHeight = 20.sp,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.extraLarge
    )
}


@Composable
fun ActiveCaloriesBar(
    currentCalories: Int,
    goal: Int = 800
) {
    val progress = (currentCalories.toFloat() / goal).coerceIn(0f, 1f)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.height(240.dp)
    ) {
        // Icon on top
        Text("🔥", fontSize = 16.sp)
        Spacer(Modifier.height(4.dp))

        // Bar
        Box(
            modifier = Modifier
                .width(16.dp)
                .weight(1f)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFFE5E7EB)) // Track color
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(progress)
                    .align(Alignment.BottomCenter)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(Color(0xFFFF5722), Color(0xFFFF9800))
                        )
                    )
            )
        }

        Spacer(Modifier.height(4.dp))
        // Value at bottom
        Text(
            "$currentCalories",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFFF5722)
        )
    }
}

// Glavni ekran
@Composable
fun NutritionScreen(
    plan: PlanResult?,
    onScanBarcode: () -> Unit = {},
    onOpenEAdditives: () -> Unit = {},
    scannedProduct: Pair<OpenFoodFactsProduct, String>? = null,
    onProductConsumed: () -> Unit = {},
    openBarcodeScan: Boolean = false,
    openFoodSearch: Boolean = false,
    onXPAdded: () -> Unit = {},
    snackbarHostState: SnackbarHostState,
    userProfile: com.example.myapplication.data.UserProfile = com.example.myapplication.data.UserProfile()
) {
    // Snackbar feedback state
    var showAddedMessage by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(showAddedMessage) {
        showAddedMessage?.let { msg ->
            snackbarHostState.showSnackbar(message = msg, withDismissAction = true)
            showAddedMessage = null
        }
    }

    // Context for widget updates
    val context = LocalContext.current

    // Active Calories (Health Connect) - load from SharedPreferences INSTANTLY
    val healthManager = remember { HealthConnectManager.getInstance(context) }
    val burnedPrefs = remember { context.getSharedPreferences("burned_cache", android.content.Context.MODE_PRIVATE) }
    val todayBurnedKey = remember { "burned_${java.time.LocalDate.now()}" }
    var activeCaloriesBurned by remember { mutableStateOf(burnedPrefs.getInt(todayBurnedKey, 0)) }

    LaunchedEffect(Unit) {
        // Check permissions and load data
        if (healthManager.isAvailable() && healthManager.hasAllPermissions()) {
            while (true) {
                val now = Instant.now()
                val startOfDay = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant()

                // 1. Health Connect (Active)
                val healthConnectCalories = healthManager.readCalories(startOfDay, now)

                // 2. App Exercises (Workouts + Logs)
                val appExercisesCalories = HealthStorage.getTodayAppExercisesCalories()

                // Sum -> Total Active Calories
                val newBurned = healthConnectCalories + appExercisesCalories
                activeCaloriesBurned = newBurned

                // Save to SharedPreferences immediately
                burnedPrefs.edit().putInt(todayBurnedKey, newBurned).apply()

                kotlinx.coroutines.delay(10000) // Refresh every 10s
            }
        }
    }

    // Današnji vnosi (lokalni state) — takoj naloži iz lokalnega cache-a
    val initialFoods = remember {
        val cacheDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        try {
            val json = com.example.myapplication.persistence.DailySyncManager.loadFoodsJson(context, cacheDate)
            if (!json.isNullOrBlank()) {
                val arr = org.json.JSONArray(json)
                (0 until arr.length()).mapNotNull { i ->
                    try {
                        val obj = arr.getJSONObject(i)
                        val mealStr = obj.optString("meal", "Breakfast")
                        val meal = runCatching { MealType.valueOf(mealStr) }.getOrNull() ?: MealType.Breakfast
                        TrackedFood(
                            id = obj.optString("id", java.util.UUID.randomUUID().toString()),
                            name = obj.optString("name", ""),
                            meal = meal,
                            amount = obj.optDouble("amount", 1.0),
                            unit = obj.optString("unit", "servings"),
                            caloriesKcal = obj.optDouble("caloriesKcal", 0.0),
                            proteinG = obj.optDouble("proteinG", 0.0).takeIf { it > 0 },
                            carbsG = obj.optDouble("carbsG", 0.0).takeIf { it > 0 },
                            fatG = obj.optDouble("fatG", 0.0).takeIf { it > 0 },
                            fiberG = obj.optDouble("fiberG", 0.0).takeIf { it > 0 },
                            sugarG = obj.optDouble("sugarG", 0.0).takeIf { it > 0 },
                            saturatedFatG = obj.optDouble("saturatedFatG", 0.0).takeIf { it > 0 },
                            sodiumMg = obj.optDouble("sodiumMg", 0.0).takeIf { it > 0 },
                            potassiumMg = obj.optDouble("potassiumMg", 0.0).takeIf { it > 0 },
                            cholesterolMg = obj.optDouble("cholesterolMg", 0.0).takeIf { it > 0 },
                            barcode = obj.optString("barcode", "").takeIf { it.isNotBlank() }
                        )
                    } catch (e: Exception) { null }
                }
            } else emptyList()
        } catch (e: Exception) { emptyList() }
    }
    var trackedFoods by remember { mutableStateOf<List<TrackedFood>>(initialFoods) }

    // Water tracking - load from SharedPreferences INSTANTLY for no flicker
    val waterPrefs = remember { context.getSharedPreferences("water_cache", android.content.Context.MODE_PRIVATE) }
    val todayWaterKey = remember { "water_${java.time.LocalDate.now()}" }
    val cachedWater = remember { waterPrefs.getInt(todayWaterKey, 0) }
    var waterConsumedMl by remember { mutableStateOf(cachedWater) }
    // waterLoaded = true means Firestore initial read happened (ne overwritamo lokalnih sprememb)
    var waterLoaded by remember { mutableStateOf(false) }
    // UI debounce za vode gumbe (preprečuje double-tap)
    val lastWaterClickState = remember { mutableStateOf(0L) }

    // Poraba (izračuni)
    val consumedKcal = trackedFoods.sumOf { it.caloriesKcal.roundToInt() }
    val consumedProtein = trackedFoods.sumOf { it.proteinG ?: 0.0 }
    val consumedCarbs = trackedFoods.sumOf { it.carbsG ?: 0.0 }
    val consumedFat = trackedFoods.sumOf { it.fatG ?: 0.0 }
    val consumedFiber = trackedFoods.sumOf { it.fiberG ?: 0.0 }
    val consumedSugar = trackedFoods.sumOf { it.sugarG ?: 0.0 }
    val consumedSodium = trackedFoods.sumOf { it.sodiumMg ?: 0.0 }
    val consumedPotassium = trackedFoods.sumOf { it.potassiumMg ?: 0.0 }
    val consumedCholesterol = trackedFoods.sumOf { it.cholesterolMg ?: 0.0 }
    val consumedSatFat = trackedFoods.sumOf { it.saturatedFatG ?: 0.0 }

    var showOtherMacros by remember { mutableStateOf(false) }

    // ?? KRITI�NO: Preberi nutrition plan iz NutritionPlanStore (posodobljiv plan)
    var nutritionPlan by remember { mutableStateOf<com.example.myapplication.data.NutritionPlan?>(null) }
    LaunchedEffect(Unit) {
        val uid = com.example.myapplication.persistence.FirestoreHelper.getCurrentUserDocId()
        if (uid != null) {
            nutritionPlan = com.example.myapplication.persistence.NutritionPlanStore.loadNutritionPlan(uid)
        }
    }

    // Tarče
    val parsed = remember(plan?.algorithmData?.macroBreakdown) { parseMacroBreakdown(plan?.algorithmData?.macroBreakdown) }
    // Round target calories down to nearest 100
    val rawTargetCalories = nutritionPlan?.calories ?: parsed.calories ?: plan?.calories ?: 2000
    val targetCalories = (rawTargetCalories / 100) * 100

    val targetProtein  = nutritionPlan?.protein ?: parsed.proteinG ?: plan?.protein ?: 100
    val targetCarbs    = nutritionPlan?.carbs ?: parsed.carbsG ?: plan?.carbs ?: 200
    val targetFat      = nutritionPlan?.fat ?: parsed.fatG ?: plan?.fat ?: 60

    // Modali/sheets
    var sheetMeal by remember { mutableStateOf<MealType?>(null) }
    val addSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var showMakeCustom by remember { mutableStateOf(false) }
    var pendingCustomMeal by remember { mutableStateOf<SavedCustomMeal?>(null) }
    var askWhereToAdd by remember { mutableStateOf(false) }
    var chooseMealForCustom by remember { mutableStateOf(MealType.Breakfast) }
    var showFoodDetailDialog by remember { mutableStateOf<TrackedFood?>(null) }

    // Avtomatsko odpri Add Food Sheet, ko je produkt skeniran
    LaunchedEffect(scannedProduct) {
        if (scannedProduct != null) {
            sheetMeal = MealType.Snacks // Default v Snacks, lahko spremenimo
        }
    }

    // Auto-open barcode scan from widget
    LaunchedEffect(openBarcodeScan) {
        if (openBarcodeScan) {
            Log.d("NutritionScreen", "Auto-opening barcode scanner (flag=$openBarcodeScan)")
            kotlinx.coroutines.delay(100) // Small delay for UI to be ready
            onScanBarcode()
        }
    }

    // Auto-open food search from widget
    LaunchedEffect(openFoodSearch) {
        if (openFoodSearch) {
            // Determine current meal type based on time
            val calendar = java.util.Calendar.getInstance()
            val hour = calendar.get(java.util.Calendar.HOUR_OF_DAY)
            val minute = calendar.get(java.util.Calendar.MINUTE)
            val timeInMinutes = hour * 60 + minute

            val mealType = when {
                timeInMinutes in 300..599 -> MealType.Breakfast
                timeInMinutes in 690..899 -> MealType.Lunch
                timeInMinutes in 1080..1199 -> MealType.Dinner
                else -> MealType.Snacks
            }

            Log.d("NutritionScreen", "Auto-opening food search for $mealType (flag=$openFoodSearch)")
            kotlinx.coroutines.delay(100) // Small delay for UI to be ready
            sheetMeal = mealType // This triggers the ModalBottomSheet to open
        }
    }

    // Real-time shranjeni custom meals (čipi)
    var savedMeals by remember { mutableStateOf<List<SavedCustomMeal>>(emptyList()) }
    var confirmDelete by remember { mutableStateOf<SavedCustomMeal?>(null) }

    // Snapshot listener za customMeals
    DisposableEffect(Unit) {
        val uid = com.example.myapplication.persistence.FirestoreHelper.getCurrentUserDocId()
        var reg: ListenerRegistration? = null
        if (uid != null) {
            reg = Firebase.firestore.collection("users").document(uid)
                .collection("customMeals")
                .addSnapshotListener { snaps, _ ->
                    val list = snaps?.documents?.mapNotNull { doc ->
                        val name = doc.getString("name") ?: "Custom meal"
                        @Suppress("UNCHECKED_CAST")
                        val itemsAny = doc.get("items") as? List<Map<String, Any?>>
                        val items: List<Map<String, Any>> = itemsAny?.mapNotNull { m ->
                            try {
                                // Create mutable map with Any type
                                val map = mutableMapOf<String, Any>()
                                map["id"] = m["id"] as? String ?: ""
                                map["name"] = m["name"] as? String ?: ""
                                map["amt"] = (m["amt"] as? String) ?: (m["amt"]?.toString() ?: "")
                                map["unit"] = m["unit"] as? String ?: ""
                                map // Return as immutable
                            } catch (_: Exception) { // Param e unused
                                null
                            }
                        } ?: emptyList()
                        SavedCustomMeal(id = doc.id, name = name, items = items)
                    } ?: emptyList()
                    savedMeals = list
                }
        }
        onDispose { reg?.remove() }
    }

    // Persist današnji jedilnik — todayId je skupni ključ za vse lokalne cache-e
    val todayId = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()) }
    val uid = com.example.myapplication.persistence.FirestoreHelper.getCurrentUserDocId()
    var lastSyncedSignature by remember { mutableStateOf("") }
    fun foodsSignature(list: List<TrackedFood>) = list.joinToString("|") { tf ->
        listOf(
            tf.name,
            tf.meal.name,
            tf.amount,
            tf.unit,
            tf.caloriesKcal,
            tf.proteinG ?: 0.0,
            tf.carbsG ?: 0.0,
            tf.fatG ?: 0.0,
            tf.fiberG ?: 0.0,
            tf.sugarG ?: 0.0,
            tf.saturatedFatG ?: 0.0,
            tf.sodiumMg ?: 0.0,
            tf.potassiumMg ?: 0.0,
            tf.cholesterolMg ?: 0.0
        ).joinToString(";")
    }

    // Restore ob zagonu — enkratno branje iz Firestore (samo če je lokalni cache prazen)
    var firestoreFoodsLoaded by remember { mutableStateOf(false) }
    DisposableEffect(uid, todayId) {
        var reg: ListenerRegistration? = null
        if (uid != null) {
            reg = Firebase.firestore.collection("users").document(uid)
                .collection("dailyLogs").document(todayId)
                .addSnapshotListener { doc, _ ->
                    // ── HRANA: Firestore prevzame samo ob prvem nalaganju in SAMO če je lokalni cache prazen ──
                    if (!firestoreFoodsLoaded) {
                        firestoreFoodsLoaded = true
                        val items = doc?.get("items") as? List<*>
                        if (items != null && trackedFoods.isEmpty()) {
                            val parsedFoods = items.mapNotNull { any ->
                                val m = any as? Map<*, *> ?: return@mapNotNull null
                                val name = m["name"] as? String ?: return@mapNotNull null
                                val mealStr = m["meal"] as? String ?: "Breakfast"
                                val meal = runCatching { MealType.valueOf(mealStr) }.getOrNull() ?: MealType.Breakfast
                                val amount = (m["amount"] as? Number)?.toDouble()
                                    ?: (m["amount"] as? String)?.toDoubleOrNull() ?: 1.0
                                val unit = m["unit"] as? String ?: "servings"
                                val kcal = (m["caloriesKcal"] as? Number)?.toDouble()
                                    ?: (m["caloriesKcal"] as? String)?.toDoubleOrNull() ?: 0.0
                                val p = (m["proteinG"] as? Number)?.toDouble()
                                    ?: (m["proteinG"] as? String)?.toDoubleOrNull()
                                val c = (m["carbsG"] as? Number)?.toDouble()
                                    ?: (m["carbsG"] as? String)?.toDoubleOrNull()
                                val f = (m["fatG"] as? Number)?.toDouble()
                                    ?: (m["fatG"] as? String)?.toDoubleOrNull()
                                val barcode = m["barcode"] as? String
                                TrackedFood(
                                    id = (m["id"] as? String) ?: java.util.UUID.randomUUID().toString(),
                                    name = name,
                                    meal = meal,
                                    amount = amount,
                                    unit = unit,
                                    caloriesKcal = kcal,
                                    proteinG = p,
                                    carbsG = c,
                                    fatG = f,
                                    fiberG = (m["fiberG"] as? Number)?.toDouble()
                                        ?: (m["fiberG"] as? String)?.toDoubleOrNull(),
                                    sugarG = (m["sugarG"] as? Number)?.toDouble()
                                        ?: (m["sugarG"] as? String)?.toDoubleOrNull(),
                                    saturatedFatG = (m["saturatedFatG"] as? Number)?.toDouble()
                                        ?: (m["saturatedFatG"] as? String)?.toDoubleOrNull(),
                                    sodiumMg = (m["sodiumMg"] as? Number)?.toDouble()
                                        ?: (m["sodiumMg"] as? String)?.toDoubleOrNull(),
                                    potassiumMg = (m["potassiumMg"] as? Number)?.toDouble()
                                        ?: (m["potassiumMg"] as? String)?.toDoubleOrNull(),
                                    cholesterolMg = (m["cholesterolMg"] as? Number)?.toDouble()
                                        ?: (m["cholesterolMg"] as? String)?.toDoubleOrNull(),
                                    barcode = barcode
                                )
                            }
                            if (parsedFoods.isNotEmpty()) {
                                trackedFoods = parsedFoods
                                lastSyncedSignature = foodsSignature(parsedFoods)
                                Log.d("NutritionLocal", "Loaded ${parsedFoods.size} foods from Firestore (local was empty)")
                            }
                        }
                    }
                    // ── VODA: samo ob prvem nalaganju, prevzame Firestore vrednost če je višja ──
                    val serverWater = (doc?.get("waterMl") as? Number)?.toInt() ?: 0
                    if (!waterLoaded) {
                        if (serverWater > waterConsumedMl) {
                            waterConsumedMl = serverWater
                            waterPrefs.edit().putInt(todayWaterKey, serverWater).apply()
                        }
                        waterLoaded = true
                    }
                    // ── BURNED: samo ob prvem nalaganju, prevzame Firestore vrednost če je višja ──
                    val serverBurned = (doc?.get("burnedCalories") as? Number)?.toInt()
                    if (serverBurned != null && serverBurned > activeCaloriesBurned) {
                        activeCaloriesBurned = serverBurned
                        burnedPrefs.edit().putInt(todayBurnedKey, serverBurned).apply()
                    }
                }
        }
        onDispose { reg?.remove() }
    }

    // Lokalni zapis hrane — TAKOJ ob vsaki spremembi, brez čakanja na Firestore
    LaunchedEffect(trackedFoods, todayId) {
        val sig = foodsSignature(trackedFoods)
        if (sig == lastSyncedSignature) return@LaunchedEffect
        lastSyncedSignature = sig

        // Serializiraj v JSON in shrani lokalno
        try {
            val arr = org.json.JSONArray()
            trackedFoods.forEach { tf ->
                val obj = org.json.JSONObject().apply {
                    put("id", tf.id)
                    put("name", tf.name)
                    put("meal", tf.meal.name)
                    put("amount", tf.amount)
                    put("unit", tf.unit)
                    put("caloriesKcal", tf.caloriesKcal)
                    put("proteinG", tf.proteinG ?: 0.0)
                    put("carbsG", tf.carbsG ?: 0.0)
                    put("fatG", tf.fatG ?: 0.0)
                    put("fiberG", tf.fiberG ?: 0.0)
                    put("sugarG", tf.sugarG ?: 0.0)
                    put("saturatedFatG", tf.saturatedFatG ?: 0.0)
                    put("sodiumMg", tf.sodiumMg ?: 0.0)
                    put("potassiumMg", tf.potassiumMg ?: 0.0)
                    put("cholesterolMg", tf.cholesterolMg ?: 0.0)
                    if (tf.barcode != null) put("barcode", tf.barcode)
                }
                arr.put(obj)
            }
            com.example.myapplication.persistence.DailySyncManager.saveFoodsLocally(context, arr.toString(), todayId)
        } catch (e: Exception) {
            Log.e("NutritionLocal", "Failed to save foods locally", e)
        }

        // Preverimo XP nagrado lokalno (ne čakamo na Firestore)
        val targetCal = targetCalories
        val consumedCal = consumedKcal
        val difference = kotlin.math.abs(targetCal - consumedCal)
        val percentageDiff = if (targetCal > 0) (difference.toDouble() / targetCal.toDouble()) else 1.0
        if (percentageDiff <= 0.20) {
            val xpKey = "nutrition_xp_$todayId"
            val prefs = context.getSharedPreferences("nutrition_xp", Context.MODE_PRIVATE)
            if (!prefs.getBoolean(xpKey, false)) {
                val userEmail = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.email
                if (userEmail != null) {
                    com.example.myapplication.data.UserPreferences.addXPWithCallback(context, userEmail, 100) { _ ->
                        onXPAdded()
                    }
                    prefs.edit().putBoolean(xpKey, true).apply()
                }
            }
        }
    }

    // Lokalni zapis vode — TAKOJ ob vsaki spremembi, brez Firestore
    LaunchedEffect(waterConsumedMl, todayId) {
        // Shrani lokalno takoj
        waterPrefs.edit().putInt(todayWaterKey, waterConsumedMl).apply()
        com.example.myapplication.persistence.DailySyncManager.saveWaterLocally(context, waterConsumedMl, todayId)
        // Posodobi water widget takoj
        com.example.myapplication.widget.WaterWidgetProvider.updateWidgetFromApp(context, waterConsumedMl)
    }

    // Lokalni zapis porabljenih kalorij — TAKOJ, brez Firestore
    LaunchedEffect(activeCaloriesBurned, todayId) {
        burnedPrefs.edit().putInt(todayBurnedKey, activeCaloriesBurned).apply()
        com.example.myapplication.persistence.DailySyncManager.saveBurnedLocally(context, activeCaloriesBurned, todayId)
    }

    // Read theme colors in Composable context
    val backgroundColor = MaterialTheme.colorScheme.background
    val surfaceVariantColor = MaterialTheme.colorScheme.surfaceVariant
    val textPrimary = MaterialTheme.colorScheme.onBackground
    val textSecondary = MaterialTheme.colorScheme.onSurfaceVariant

    // Water tracking (2000ml target)
    val waterTarget = 2000f
    val waterProgress = (waterConsumedMl.toFloat() / waterTarget).coerceIn(0f, 1f)

    // Calculate macro calories
    val fatCals = (consumedFat * 9).roundToInt()
    val proteinCals = (consumedProtein * 4).roundToInt()
    val carbsCals = (consumedCarbs * 4).roundToInt()
    val totalMacroCalories = fatCals + proteinCals + carbsCals

    // Calculate proportions for each segment
    val fatProp = if (totalMacroCalories > 0) (fatCals.toFloat() / totalMacroCalories.toFloat()).coerceIn(0f, 1f) else 0f
    val proteinProp = if (totalMacroCalories > 0) (proteinCals.toFloat() / totalMacroCalories.toFloat()).coerceIn(0f, 1f) else 0f
    val carbsProp = if (totalMacroCalories > 0) (carbsCals.toFloat() / totalMacroCalories.toFloat()).coerceIn(0f, 1f) else 0f

    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundColor)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Donut Progress View
            // Donut Progress View + Active Calories Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                AndroidView(
                    factory = { ctx ->
                        DonutProgressView(ctx).apply {
                            // Simple mode = !detailedCalories (privzeto enostaven)
                            simpleMode = !userProfile.detailedCalories
                            fatProportion = fatProp
                            proteinProportion = proteinProp
                            carbsProportion = carbsProp
                            this.fatCalories = fatCals
                            this.proteinCalories = proteinCals
                            this.carbsCalories = carbsCals
                            this.consumedCalories = consumedKcal
                            this.targetCalories = targetCalories
                            innerProgress = waterProgress
                            textColor = textPrimary.toArgb()
                            waterColor = textPrimary.toArgb()
                            innerValue = waterConsumedMl.toString()
                            innerLabel = "ml"
                            weightUnit = userProfile.weightUnit // Pass unit
                            centerValue = "$consumedKcal/$targetCalories"
                            centerLabel = "kcal"
                            startAngle = 135f
                            sweepAngle = 270f
                            onSegmentClick = { segment -> Log.d("DonutRing", "Clicked: $segment") }
                        }
                    },
                    modifier = Modifier.size(240.dp),
                    update = { view ->
                        // Update simpleMode when setting changes
                        view.simpleMode = !userProfile.detailedCalories
                        view.fatProportion = fatProp
                        view.proteinProportion = proteinProp
                        view.carbsProportion = carbsProp
                        view.fatCalories = fatCals
                        view.proteinCalories = proteinCals
                        view.carbsCalories = carbsCals
                        view.consumedCalories = consumedKcal
                        view.targetCalories = targetCalories
                        view.innerProgress = waterProgress
                        view.textColor = textPrimary.toArgb()
                        view.waterColor = textPrimary.toArgb()
                        view.innerValue = waterConsumedMl.toString()
                        view.weightUnit = userProfile.weightUnit // Update unit
                        view.centerValue = "$consumedKcal/$targetCalories"

                        // Update click listener to use correct units for tooltip
                        view.onSegmentClick = { clicked ->
                            view.clickedSegment = clicked
                            view.invalidate()
                        }
                    }
                )

                Spacer(modifier = Modifier.width(16.dp))

                // Active Calories Bar (Goal: 800 kcal)
                ActiveCaloriesBar(currentCalories = activeCaloriesBurned, goal = 800)
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Other Macros button
            Button(
                onClick = {
                    com.example.myapplication.utils.HapticFeedback.performHapticFeedback(
                        context,
                        com.example.myapplication.utils.HapticFeedback.FeedbackType.CLICK
                    )
                    showOtherMacros = true
                },
                modifier = Modifier.align(Alignment.CenterHorizontally),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F172A)),
                shape = RoundedCornerShape(20.dp)
            ) {
                Icon(
                    painter = painterResource(android.R.drawable.ic_menu_info_details),
                    contentDescription = null,
                    tint = Color.White
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("OTHER MACROS", color = Color.White, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Water controls
            Row(
                modifier = Modifier.align(Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                FloatingActionButton(
                    onClick = {
                        val now = SystemClock.elapsedRealtime()
                        if (now - lastWaterClickState.value < 80) return@FloatingActionButton
                        lastWaterClickState.value = now
                        com.example.myapplication.utils.HapticFeedback.performHapticFeedback(
                            context,
                            com.example.myapplication.utils.HapticFeedback.FeedbackType.LIGHT_CLICK
                        )
                        waterConsumedMl = maxOf(0, waterConsumedMl - 50)
                    },
                    containerColor = Color(0xFF2563EB),
                    contentColor = Color.White,
                    modifier = Modifier.size(48.dp)
                ) {
                    Text("-", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }

                Text(
                    "💧 50ml",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = textPrimary // Uporablja dinamično barvo
                )

                FloatingActionButton(
                    onClick = {
                        val now = SystemClock.elapsedRealtime()
                        if (now - lastWaterClickState.value < 80) return@FloatingActionButton
                        lastWaterClickState.value = now
                        com.example.myapplication.utils.HapticFeedback.performHapticFeedback(
                            context,
                            com.example.myapplication.utils.HapticFeedback.FeedbackType.LIGHT_CLICK
                        )
                        waterConsumedMl += 50
                    },
                    containerColor = Color(0xFF2563EB),
                    contentColor = Color.White,
                    modifier = Modifier.size(48.dp)
                ) {
                    Text("+", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Macro numbers row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                fun getMacroColor(consumed: Double, target: Int): Color {
                    if (target <= 0) return textPrimary
                    val deviation = kotlin.math.abs((consumed - target) / target)
                    return when {
                        deviation <= 0.10 -> Color(0xFF10B981) // Green
                        deviation <= 0.20 -> Color(0xFFF59E0B) // Yellow
                        else -> Color(0xFFEF4444) // Red
                    }
                }

                Text(
                    text = macroLabel("Protein", consumedProtein, targetProtein, userProfile.weightUnit),
                    color = getMacroColor(consumedProtein, targetProtein),
                    fontSize = 13.sp,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )

                Text(
                    text = macroLabel("Fat", consumedFat, targetFat, userProfile.weightUnit),
                    color = getMacroColor(consumedFat, targetFat),
                    fontSize = 13.sp,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )

                Text(
                    text = macroLabel("Carbs", consumedCarbs, targetCarbs, userProfile.weightUnit),
                    color = getMacroColor(consumedCarbs, targetCarbs),
                    fontSize = 13.sp,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Tap colored segments for details",
                color = Color(0xFF6B7280),
                fontSize = 12.sp,
                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Make custom meals button
            Button(
                onClick = {
                    com.example.myapplication.utils.HapticFeedback.performHapticFeedback(
                        context,
                        com.example.myapplication.utils.HapticFeedback.FeedbackType.CLICK
                    )
                    showMakeCustom = true
                },
                modifier = Modifier.align(Alignment.CenterHorizontally),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976F6)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("MAKE CUSTOM MEALS", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Saved custom meals
            savedMeals.forEach { meal ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                        .clickable {
                            pendingCustomMeal = meal
                            askWhereToAdd = true
                        },
                    colors = CardDefaults.cardColors(containerColor = surfaceVariantColor),
                    shape = RoundedCornerShape(18.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, DrawerBlue)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp, 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = meal.name,
                            fontSize = 14.sp,
                            color = textPrimary,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { confirmDelete = meal }) {
                            Icon(
                                painter = painterResource(android.R.drawable.ic_menu_delete),
                                contentDescription = "Delete",
                                tint = textPrimary
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Meal cards (Breakfast, Lunch, Dinner, Snacks)
            listOf(
                MealType.Breakfast to "Breakfast",
                MealType.Lunch to "Lunch",
                MealType.Dinner to "Dinner",
                MealType.Snacks to "Snacks"
            ).forEach { (mealType, title) ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    colors = CardDefaults.cardColors(containerColor = surfaceVariantColor),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = title,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = textPrimary
                            )
                            SmallFloatingActionButton(
                                onClick = {
                                    com.example.myapplication.utils.HapticFeedback.performHapticFeedback(
                                        context,
                                        com.example.myapplication.utils.HapticFeedback.FeedbackType.CLICK
                                    )
                                    sheetMeal = mealType
                                },
                                containerColor = Color(0xFF1976F6),
                                contentColor = Color.White
                            ) {
                                Icon(
                                    painter = painterResource(android.R.drawable.ic_input_add),
                                    contentDescription = "Add"
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Meal line separator
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(Color(0xFF1976F6), shape = androidx.compose.foundation.shape.CircleShape)
                            )
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(4.dp)
                                    .padding(start = 8.dp)
                                    .background(Color(0xFFCCCCCC), shape = RoundedCornerShape(2.dp))
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Food items for this meal
                        trackedFoods.filter { it.meal == mealType }.forEach { itx ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clickable { showFoodDetailDialog = itx },
                                colors = CardDefaults.cardColors(containerColor = surfaceVariantColor),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = itx.name,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = textPrimary
                                        )
                                        Text(
                                            text = "${itx.amount.toInt()} ${itx.unit} • ${itx.caloriesKcal.roundToInt()} kcal",
                                            fontSize = 12.sp,
                                            color = Color(0xFF6B7280)
                                        )
                                    }
                                    IconButton(onClick = {
                                        trackedFoods = trackedFoods.filterNot { t -> t.id == itx.id }
                                    }) {
                                        Icon(
                                            painter = painterResource(android.R.drawable.ic_menu_delete),
                                            contentDescription = "Delete",
                                            tint = Color(0xFFEF4444)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Recipes search section
            RecipesSearchSection(
                onScanBarcode = onScanBarcode,
                onOpenEAdditives = onOpenEAdditives,
                userProfile = userProfile
            )

            Spacer(modifier = Modifier.height(80.dp)) // Bottom padding for snackbar
        }
    }

    // Sheet: iskanje hrane + dodajanje
    if (sheetMeal != null) {
        ModalBottomSheet(
            onDismissRequest = {
                sheetMeal = null
                onProductConsumed() // Počisti scanned product
            },
            sheetState = addSheetState,
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            AddFoodSheet(
                meal = sheetMeal!!,
                onClose = {
                    sheetMeal = null
                    onProductConsumed() // Počisti scanned product
                },
                onAddTracked = { tf ->
                    trackedFoods = trackedFoods + tf
                    showAddedMessage = "Added ${tf.name} to ${tf.meal.title}" // immediate feedback
                    onProductConsumed() // Počisti scanned product po dodajanju
                },
                scannedProduct = scannedProduct,
                onProductConsumed = onProductConsumed,
                isImperial = userProfile.weightUnit == "lb" || userProfile.speedUnit == "mph" // Simplified check
            )
        }
    }

    // Dialog: ustvarjanje custom meals
    if (showMakeCustom) {
        MakeCustomMealsDialog(
            onDismiss = { showMakeCustom = false },
            onSaved = { saved ->
                // Po shranjevanju takoj vprašamo kam dodaš; čipi se sami posodobijo prek snapshot listenerja
                pendingCustomMeal = saved
                showMakeCustom = false
                askWhereToAdd = true
            }
        )
    }

    // Food Detail Dialog - prikaže podrobnosti o tracked food item
    showFoodDetailDialog?.let { trackedFood ->
        TrackedFoodDetailDialog(
            trackedFood = trackedFood,
            onDismiss = { showFoodDetailDialog = null },
            userProfile = userProfile
        )
    }

    // Dialog: izberi obrok in dodaj custom meal
    if (askWhereToAdd && pendingCustomMeal != null) {
        ChooseMealDialog(
            selected = chooseMealForCustom,
            onCancel = {
                askWhereToAdd = false
                pendingCustomMeal = null
            },
            onConfirmAsync = { mealChosen ->
                val cm = pendingCustomMeal!!
                val currentUid = com.example.myapplication.persistence.FirestoreHelper.getCurrentUserDocId()

                if (currentUid == null) {
                    // Removed redundant qualifier
                    android.widget.Toast.makeText(context, "Not logged in", android.widget.Toast.LENGTH_SHORT).show()
                    pendingCustomMeal = null
                    askWhereToAdd = false
                    return@ChooseMealDialog true
                }

                // CRITICAL FIX: Re-fetch from Firestore to get ALL nutritional data
                Firebase.firestore.collection("users").document(currentUid)
                    .collection("customMeals").document(cm.id)
                    .get()
                    .addOnSuccessListener { doc ->
                        if (!doc.exists()) {
                            android.widget.Toast.makeText(context, "Meal not found", android.widget.Toast.LENGTH_SHORT).show()
                            pendingCustomMeal = null
                            askWhereToAdd = false
                            return@addOnSuccessListener
                        }

                        val items = doc.get("items") as? List<*> ?: emptyList<Any>()

                        val newItems = items.mapNotNull { any ->
                            val m = any as? Map<*, *> ?: return@mapNotNull null
                            val name = m["name"] as? String ?: return@mapNotNull null
                            val amtStr = m["amt"] as? String ?: return@mapNotNull null
                            val amt = amtStr.toDoubleOrNull() ?: 1.0
                            val unit = m["unit"] as? String ?: "servings"

                            // Get macros from Firestore data
                            val caloriesKcal = (m["caloriesKcal"] as? Number)?.toDouble() ?: 0.0
                            val proteinG = (m["proteinG"] as? Number)?.toDouble() ?: 0.0
                            val carbsG = (m["carbsG"] as? Number)?.toDouble() ?: 0.0
                            val fatG = (m["fatG"] as? Number)?.toDouble() ?: 0.0
                            val fiberG = (m["fiberG"] as? Number)?.toDouble()
                            val sugarG = (m["sugarG"] as? Number)?.toDouble()
                            val saturatedFatG = (m["saturatedFatG"] as? Number)?.toDouble()
                            val sodiumMg = (m["sodiumMg"] as? Number)?.toDouble()
                            val potassiumMg = (m["potassiumMg"] as? Number)?.toDouble()
                            val cholesterolMg = (m["cholesterolMg"] as? Number)?.toDouble()

                            TrackedFood(
                                id = java.util.UUID.randomUUID().toString(),
                                name = name,
                                meal = mealChosen,
                                amount = amt,
                                unit = unit,
                                caloriesKcal = caloriesKcal,
                                proteinG = proteinG,
                                carbsG = carbsG,
                                fatG = fatG,
                                fiberG = fiberG,
                                sugarG = sugarG,
                                saturatedFatG = saturatedFatG,
                                sodiumMg = sodiumMg,
                                potassiumMg = potassiumMg,
                                cholesterolMg = cholesterolMg
                            )
                        }

                        if (newItems.isNotEmpty()) {
                            trackedFoods = trackedFoods + newItems
                            showAddedMessage = "Added custom meal: ${cm.name}"
                        } else {
                            android.widget.Toast.makeText(context, "No items found in custom meal.", android.widget.Toast.LENGTH_LONG).show()
                        }
                        pendingCustomMeal = null
                        askWhereToAdd = false
                    }
                    .addOnFailureListener { e ->
                        android.widget.Toast.makeText(context, "Failed to load meal: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                        pendingCustomMeal = null
                        askWhereToAdd = false
                    }

                true // Close dialog immediately, Firestore callback will handle the rest
            }
        )
    }

    // Dialog: potrdi brisanje custom meal
    confirmDelete?.let { mealToDelete ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            confirmButton = {
                Button(onClick = {
                    val uidDel = com.example.myapplication.persistence.FirestoreHelper.getCurrentUserDocId()
                    if (uidDel != null) {
                        Firebase.firestore.collection("users").document(uidDel)
                            .collection("customMeals").document(mealToDelete.id)
                            .delete()
                            .addOnCompleteListener {
                                confirmDelete = null
                                // Refresh quick meal widget after deletion
                                com.example.myapplication.widget.QuickMealWidgetProvider.forceRefresh(context)
                            }
                    } else confirmDelete = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancel") } },
            title = { Text("Delete custom meal?") },
            text = { Text("This will remove '${mealToDelete.name}' from saved custom meals.") }
        )
    }

    // Dialog: prikaži druge makre
    if (showOtherMacros) {
        val textColor = MaterialTheme.colorScheme.onSurface // Define textColor here
        AlertDialog(
            onDismissRequest = { showOtherMacros = false },
            confirmButton = {
                TextButton(onClick = { showOtherMacros = false }) {
                    Text("Close", color = DrawerBlue)
                }
            },
            title = {
                Text(
                    "Other Macros",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 8.dp)
                ) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            // Fiber
                            NutritionDetailRow(
                                "🌾 Fiber",
                                formatMacroWeight(consumedFiber, userProfile.weightUnit),
                                Color(0xFF10B981),
                                textColor // Pass textColor here
                            )
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                            // Sugar
                            NutritionDetailRow(
                                "🍬 Sugar",
                                formatMacroWeight(consumedSugar, userProfile.weightUnit),
                                Color(0xFFEC4899),
                                textColor // Pass textColor here
                            )
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                            // Saturated Fat
                            NutritionDetailRow(
                                "🥓 Saturated Fat",
                                formatMacroWeight(consumedSatFat, userProfile.weightUnit),
                                Color(0xFFEF4444),
                                textColor // Pass textColor here
                            )
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                            // Sodium
                            NutritionDetailRow(
                                "🧂 Sodium",
                                String.format(Locale.US, "%.0f mg", consumedSodium),
                                Color(0xFFF59E0B),
                                textColor // Pass textColor here
                            )
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                            // Potassium
                            NutritionDetailRow(
                                "🍌 Potassium",
                                String.format(Locale.US, "%.0f mg", consumedPotassium),
                                Color(0xFF3B82F6),
                                textColor // Pass textColor here
                            )
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                            // Cholesterol
                            NutritionDetailRow(
                                "🥚 Cholesterol",
                                String.format(Locale.US, "%.0f mg", consumedCholesterol),
                                Color(0xFF8B5CF6),
                                textColor // Pass textColor here
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    Text(
                        "These values are calculated from all foods consumed today.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = MaterialTheme.shapes.extraLarge
        )
    }
} // End NutritionScreen

// Helper composable functions below

// Dialog: izberi obrok
@Composable
private fun ChooseMealDialog(
    selected: MealType,
    onCancel: () -> Unit,
    onConfirmAsync: suspend (MealType) -> Boolean
) {
    var pick by remember { mutableStateOf(selected) }
    var loading by remember { mutableStateOf(false) }
    var showSpinner by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = { if (!loading) onCancel() },
        confirmButton = {
            Button(
                onClick = {
                    if (loading) return@Button
                    loading = true
                    showSpinner = false
                    // Start async; show spinner if it runs > 500ms
                    scope.launch {
                        // Delay trigger
                        val trigger = launch { kotlinx.coroutines.delay(500); showSpinner = true }
                        try {
                            val shouldDismiss = onConfirmAsync(pick)
                            // Removed empty if
                        } finally {
                            loading = false
                            showSpinner = false
                        }
                        // cancel trigger if finished sooner
                        trigger.cancel()
                    }
                },
                enabled = !loading
            ) {
                if (showSpinner) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = DrawerBlue
                    )
                } else {
                    Text("Add")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = { if (!loading) onCancel() }, enabled = !loading) { Text("Cancel") }
        },
        title = { Text("Add created meal to") },
        text = {
            Column {
                listOf(MealType.Breakfast, MealType.Lunch, MealType.Dinner, MealType.Snacks).forEach { m ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = pick == m, onClick = { if (!loading) pick = m })
                        Spacer(Modifier.width(8.dp))
                        Text(m.title)
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.extraLarge
    )
}

// Bottom sheet: dodajanje hrane iz iskanja
@Composable
private fun AddFoodSheet(
    meal: MealType,
    onClose: () -> Unit,
    onAddTracked: (TrackedFood) -> Unit,
    scannedProduct: Pair<com.example.myapplication.network.OpenFoodFactsProduct, String>? = null,
    onProductConsumed: () -> Unit = {},
    isImperial: Boolean = false // Passed parameter
) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    var searchError by remember { mutableStateOf<String?>(null) }
    var results by remember { mutableStateOf<List<FoodSummary>>(emptyList()) }
    var showAmountDialogFor by remember { mutableStateOf<FoodDetail?>(null) }
    var hasSearched by remember { mutableStateOf(false) }

    // Avtomatsko prikaži scanned product, če obstaja
    LaunchedEffect(scannedProduct) {
        if (scannedProduct != null) {
            val (product, barcode) = scannedProduct
            // Pretvori OpenFoodFacts produkt v FoodDetail za prikaz
            val foodDetail = FoodDetail(
                id = barcode,
                name = product.productName ?: "Scanned Product",
                caloriesKcal = product.nutriments?.energyKcal100g,
                proteinG = product.nutriments?.proteins100g,
                carbsG = product.nutriments?.carbohydrates100g,
                fatG = product.nutriments?.fat100g,
                servingDescription = "100 g",
                numberOfUnits = 100.0,
                measurementDescription = "g",
                metricServingAmount = 100.0,
                metricServingUnit = "g",
                fiberG = product.nutriments?.fiber100g,
                sugarG = product.nutriments?.sugars100g,
                saturatedFatG = product.nutriments?.saturatedFat100g,
                sodiumMg = product.nutriments?.sodium100g?.times(1000),
                potassiumMg = product.nutriments?.potassium100g?.times(1000),
                cholesterolMg = product.nutriments?.cholesterol100g?.times(1000)
            )
            showAmountDialogFor = foodDetail
        }
    }

    var autocompleteSuggestions by remember { mutableStateOf<List<String>>(emptyList()) }

    fun doSearch() {
        autocompleteSuggestions = emptyList() // Clear suggestions when searching
        scope.launch {
            searching = true
            searchError = null
            hasSearched = true
            runCatching { FatSecretApi.searchFoods(query, 1, 20) }
                .onSuccess { results = it }
                .onFailure { e -> searchError = e.message ?: "Search failed" }
            searching = false
        }
    }

    val textColor = MaterialTheme.colorScheme.onSurface
    val surfaceColor = MaterialTheme.colorScheme.surface

    Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
        Row(Modifier, verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Add food to ${meal.title}",
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp),
                color = textColor
            )
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = "Close", tint = textColor) }
        }

        LaunchedEffect(query) {
            if (query.length >= 2 && !hasSearched) {
                Log.d("FoodSearch", "Autocomplete query: '$query'")
                runCatching {
                    val suggestions = FatSecretApi.getFoodAutocomplete(query, 5)
                    Log.d("FoodSearch", "Autocomplete suggestions: ${suggestions.size}")
                    autocompleteSuggestions = suggestions.map { it.suggestion }
                }.onFailure { e ->
                    Log.e("FoodSearch", "Autocomplete failed: ${e.message}", e)
                    autocompleteSuggestions = emptyList()
                }
            } else {
                autocompleteSuggestions = emptyList()
            }
        }

        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    placeholder = { Text("Search foods (e.g., chicken breast)",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp) },
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp),
                    keyboardActions = KeyboardActions(onSearch = { doSearch() }),
                    shape = RoundedCornerShape(20.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = textColor,
                        unfocusedTextColor = textColor,
                        disabledTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        cursorColor = DrawerBlue,
                        focusedContainerColor = surfaceColor,
                        unfocusedContainerColor = surfaceColor,
                        disabledContainerColor = surfaceColor,
                        focusedBorderColor = DrawerBlue,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        disabledBorderColor = MaterialTheme.colorScheme.outlineVariant
                    ),
                    textStyle = TextStyle(fontSize = 14.sp)
                )
                Spacer(Modifier.width(6.dp))
                Button(
                    onClick = { doSearch() },
                    colors = ButtonDefaults.buttonColors(containerColor = DrawerBlue),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.height(50.dp)
                ) {
                    Text("Search", fontSize = 14.sp)
                }
            }

            // Autocomplete suggestions
            if (autocompleteSuggestions.isNotEmpty()) {
                Surface(
                    modifier = Modifier.padding(top = 4.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(Modifier.padding(8.dp)) {
                        autocompleteSuggestions.forEach { suggestion ->
                            TextButton(
                                onClick = {
                                    query = suggestion
                                    doSearch()
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    suggestion,
                                    modifier = Modifier.fillMaxWidth(),
                                    color = textColor
                                )
                            }
                        }
                    }
                }
            }
        }

        if (searching) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                color = DrawerBlue
            )
        }
        if (searchError != null) {
            Text(
                text = "Error: $searchError",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        // Show "No items found" if searched but no results
        if (hasSearched && !searching && results.isEmpty() && searchError == null && scannedProduct == null) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "🔍",
                        fontSize = 32.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Text(
                        "No items found",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = textColor
                    )
                    Text(
                        "Try searching with different keywords",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }

        LazyColumn(
            modifier = Modifier.heightIn(min = 100.dp, max = 420.dp).padding(top = 8.dp)
        ) {
            items(results) { item ->
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Row(
                        Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Food image if available
                        item.imageUrl?.let { imageUrl ->
                            Image(
                                painter = rememberAsyncImagePainter(imageUrl),
                                contentDescription = item.name,
                                modifier = Modifier
                                    .size(60.dp)
                                    .clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(Modifier.width(12.dp))
                        }

                        Column(Modifier.weight(1f)) {
                            Text(item.name, style = MaterialTheme.typography.titleSmall, color = textColor)
                            Text(
                                item.description ?: "",
                                maxLines = 2,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Button(onClick = {
                            scope.launch {
                                runCatching { FatSecretApi.getFoodDetail(item.id) }
                                    .onSuccess { d -> showAmountDialogFor = d }
                                    .onFailure { e -> searchError = e.message ?: "Failed to load food detail" }
                            }
                        }) { Text("Add") }
                    }
                }
            }
        }
    }

    showAmountDialogFor?.let { detail ->
        // Če je detail iz skeniranega produkta (id = barcode), pošlji barcode
        val barcodeToPass = if (scannedProduct != null && detail.id == scannedProduct.second) {
            scannedProduct.second
        } else {
            null
        }
        AmountDialog(
            detail = detail,
            meal = meal,
            barcode = barcodeToPass,
            onCancel = { showAmountDialogFor = null },
            isImperial = isImperial
        ) { tracked ->
            onAddTracked(tracked)
            showAmountDialogFor = null
        }
    }
}

// Dialog: izbira količine/eneote pri ročnem dodajanju
@Composable
private fun AmountDialog(
    detail: FoodDetail,
    meal: MealType,
    barcode: String? = null,  // Dodano za OpenFoodFacts fallback
    onCancel: () -> Unit,
    isImperial: Boolean,
    onConfirm: (TrackedFood) -> Unit
) {
    val metricUnit = detail.metricServingUnit?.lowercase(Locale.US)?.let { if (it == "g" || it == "ml") it else null }

    // Determine default unit based on preference
    var unit by remember {
        mutableStateOf(
            if (isImperial && (detail.servingDescription == "g" || detail.servingDescription == "ml")) "oz"
            else detail.servingDescription
        )
    }

    var amount by remember { mutableStateOf(if (unit == "servings") "1" else "100") }
    val amountDouble = amount.toDoubleOrNull()

    // Calculate scale factor based on API serving size
    val baseServingSize = detail.metricServingAmount ?: 100.0

    val scaleFactor = remember(detail, unit, amountDouble, baseServingSize) {
        when {
            amountDouble == null -> 1.0
            unit == "g" || unit == "ml" -> amountDouble / baseServingSize
            unit == "oz" -> (amountDouble * 28.3495) / baseServingSize // Convert oz to g
            else -> amountDouble // servings
        }
    }
    val preview = remember(detail, scaleFactor) {
        detail.copy(
            caloriesKcal = (detail.caloriesKcal ?: 0.0) * scaleFactor,
            proteinG = (detail.proteinG ?: 0.0) * scaleFactor,
            carbsG = (detail.carbsG ?: 0.0) * scaleFactor,
            fatG = (detail.fatG ?: 0.0) * scaleFactor,
            fiberG = detail.fiberG?.times(scaleFactor),
            sugarG = detail.sugarG?.times(scaleFactor),
            saturatedFatG = detail.saturatedFatG?.times(scaleFactor),
            sodiumMg = detail.sodiumMg?.times(scaleFactor),
            potassiumMg = detail.potassiumMg?.times(scaleFactor),
            cholesterolMg = detail.cholesterolMg?.times(scaleFactor)
        )
    }

    val textColor = MaterialTheme.colorScheme.onSurface
    val surfaceColor = MaterialTheme.colorScheme.surface

    val availableUnits = remember(metricUnit, detail, isImperial) {
        val list = mutableListOf<String>()
        // Prioritize units based on preference
        if (isImperial) {
            list.add("oz")
            list.add("servings")
            list.add("g")
        } else {
            list.add("g")
            list.add("servings")
            list.add("oz")
        }

        if (metricUnit != null && metricUnit != "g" && metricUnit != "ml" && !list.contains(metricUnit)) {
            list.add(metricUnit)
        }
        list
    }

    AlertDialog(
        onDismissRequest = onCancel,
        confirmButton = {
            Button(
                onClick = {
                    if (amountDouble != null && amountDouble > 0) {
                        onConfirm(
                            TrackedFood(
                                id = java.util.UUID.randomUUID().toString(),
                                name = detail.name,
                                meal = meal,
                                amount = amountDouble,
                                unit = unit ?: "servings",
                                caloriesKcal = preview.caloriesKcal ?: 0.0,
                                proteinG = preview.proteinG,
                                carbsG = preview.carbsG,
                                fatG = preview.fatG,
                                fiberG = preview.fiberG,
                                sugarG = preview.sugarG,
                                saturatedFatG = preview.saturatedFatG,
                                sodiumMg = preview.sodiumMg,
                                potassiumMg = preview.potassiumMg,
                                cholesterolMg = preview.cholesterolMg,
                                barcode = barcode
                            )
                        )
                    }
                },
                enabled = amountDouble != null && amountDouble > 0,
                colors = ButtonDefaults.buttonColors(containerColor = DrawerBlue, contentColor = Color.White)
            ) { Text("Add to ${meal.title}") }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } },
        title = { Text(detail.name, style = MaterialTheme.typography.titleLarge, color = textColor) },
        text = {
            Column {
                if (metricUnit != null) {
                    Row {
                        AssistChip(onClick = { unit = metricUnit }, label = { Text(metricUnit.uppercase()) })
                        Spacer(Modifier.width(8.dp))
                        AssistChip(onClick = { unit = "servings" }, label = { Text("Servings") })
                    }
                } else {
                    Text("No metric weight/volume available. Using servings.", fontSize = 12.sp, color = textColor)
                }
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = {
                        val labelUnit = if (unit == "g") "g" else if (unit == "ml") "ml" else if (unit == "oz") "oz" else "servings"
                        Text("Amount ($labelUnit)")
                    },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                // Unit Selection
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    availableUnits.forEach { u ->
                        FilterChip(
                            selected = unit == u,
                            onClick = {
                                // Conversion logic when switching units?
                                val currentVal = amount.toDoubleOrNull() ?: 0.0
                                if (currentVal > 0) {
                                    // g <-> oz
                                    if (unit == "g" && u == "oz") {
                                        amount = String.format(Locale.US, "%.1f", currentVal / 28.3495)
                                    } else if (unit == "oz" && u == "g") {
                                        amount = String.format(Locale.US, "%.0f", currentVal * 28.3495)
                                    }
                                }
                                unit = u
                            },
                            label = { Text(u) }
                        )
                    }
                }

                // Macros Summary based on preview
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Cal: ${preview.caloriesKcal?.roundToInt() ?: 0}")
                    Text("P: ${String.format(Locale.US, "%.1f", preview.proteinG ?: 0.0)}g")
                    Text("C: ${String.format(Locale.US, "%.1f", preview.carbsG ?: 0.0)}g")
                    Text("F: ${String.format(Locale.US, "%.1f", preview.fatG ?: 0.0)}g")
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.surface
    )
}

// ===== TRACKED FOOD DETAIL DIALOG =====

@Composable
internal fun TrackedFoodDetailDialog(
    trackedFood: TrackedFood,
    onDismiss: () -> Unit,
    userProfile: com.example.myapplication.data.UserProfile
) {
    val scope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(true) }
    var foodDetail by remember { mutableStateOf<FoodDetail?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val PrimaryBlue = Color(0xFF2563EB)

    // Naloži podatke iz pravega vira (če imamo barcode, uporabi OpenFoodFacts direktno)
    LaunchedEffect(trackedFood) {
        isLoading = true
        errorMessage = null

        try {
            if (trackedFood.barcode != null) {
                // 1. ČE IMAMO BARCODE: uporabi OpenFoodFacts direktno (to so originalni podatki!)
                Log.d("TrackedFoodDetail", "Using OpenFoodFacts with barcode: ${trackedFood.barcode}")
                val offResponse = com.example.myapplication.network.OpenFoodFactsAPI.getProductByBarcode(trackedFood.barcode)
                val offProduct = offResponse?.product

                if (offProduct != null && offResponse.status == 1) {
                    // Pretvori OpenFoodFacts produkt v FoodDetail
                    foodDetail = FoodDetail(
                        id = trackedFood.barcode,
                        name = offProduct.productName ?: trackedFood.name,
                        caloriesKcal = offProduct.nutriments?.energyKcal100g,
                        proteinG = offProduct.nutriments?.proteins100g,
                        carbsG = offProduct.nutriments?.carbohydrates100g,
                        fatG = offProduct.nutriments?.fat100g,
                        servingDescription = offProduct.servingSize,
                        metricServingAmount = 100.0,
                        metricServingUnit = "g",
                        fiberG = offProduct.nutriments?.fiber100g,
                        sugarG = offProduct.nutriments?.sugars100g,
                        saturatedFatG = offProduct.nutriments?.saturatedFat100g,
                        sodiumMg = offProduct.nutriments?.sodium100g?.times(1000), // g to mg
                        potassiumMg = offProduct.nutriments?.potassium100g?.times(1000),
                        cholesterolMg = offProduct.nutriments?.cholesterol100g?.times(1000)
                    )
                } else {
                    errorMessage = "Product not found on OpenFoodFacts database."
                }
            } else {
                // 2. ČE NIMAMO BARCODE: poskusi FatSecret search
                Log.d("TrackedFoodDetail", "No barcode, searching FatSecret: ${trackedFood.name}")
                val searchResults = FatSecretApi.searchFoods(trackedFood.name, 1, 5)
                if (searchResults.isNotEmpty()) {
                    val firstResult = searchResults.first()
                    foodDetail = FatSecretApi.getFoodDetail(firstResult.id)
                } else {
                    errorMessage = "Product not found on FatSecret database."
                }
            }
        } catch (e: Exception) {
            Log.e("TrackedFoodDetail", "Error loading food details", e)
            errorMessage = "Error: ${e.localizedMessage}"
        } finally {
            isLoading = false
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                trackedFood.name,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                if (isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = PrimaryBlue)
                    }
                } else if (errorMessage != null) {
                    Text(
                        errorMessage!!,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                } else if (foodDetail != null) {
                    // Prikaži osnovne informacije
                    Text(
                        "Nutrition for your serving:",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    // Prikaži prispevek za vnešeno količino
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                "${trackedFood.amount.toInt()} ${trackedFood.unit}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = PrimaryBlue
                            )
                            Spacer(Modifier.height(12.dp))

                            // Main nutrition
                            NutritionDetailRow(
                                "Calories",
                                "${(trackedFood.caloriesKcal).toInt()} kcal",
                                PrimaryBlue,
                                MaterialTheme.colorScheme.onSurface // Pass textColor
                            )
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                            trackedFood.proteinG?.let {
                                NutritionDetailRow(
                                    "🥩 Protein",
                                    formatMacroWeight(it, userProfile.weightUnit),
                                    Color(0xFF10B981),
                                    MaterialTheme.colorScheme.onSurface // Pass textColor
                                )
                            }

                            trackedFood.carbsG?.let {
                                NutritionDetailRow(
                                    "🍞 Carbohydrates",
                                    formatMacroWeight(it, userProfile.weightUnit),
                                    Color(0xFFF59E0B),
                                    MaterialTheme.colorScheme.onSurface // Pass textColor
                                )
                            }

                            trackedFood.fatG?.let {
                                NutritionDetailRow(
                                    "🥑 Fat",
                                    formatMacroWeight(it, userProfile.weightUnit),
                                    Color(0xFFEF4444),
                                    MaterialTheme.colorScheme.onSurface // Pass textColor
                                )
                            }
                        }
                    }

                    // Additional nutrition if available
                    if (trackedFood.fiberG != null || trackedFood.sugarG != null ||
                        trackedFood.saturatedFatG != null || trackedFood.sodiumMg != null ||
                        trackedFood.potassiumMg != null || trackedFood.cholesterolMg != null) {

                        Spacer(Modifier.height(12.dp))

                        Text(
                            "Additional Nutrition:",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                trackedFood.fiberG?.let {
                                    if (it > 0) {
                                        NutritionDetailRow(
                                            "🌾 Fiber",
                                            formatMacroWeight(it, userProfile.weightUnit),
                                            Color(0xFF10B981),
                                            MaterialTheme.colorScheme.onSurface // Pass textColor
                                        )
                                        HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                                    }
                                }

                                trackedFood.sugarG?.let {
                                    if (it > 0) {
                                        NutritionDetailRow(
                                            "🍬 Sugar",
                                            formatMacroWeight(it, userProfile.weightUnit),
                                            Color(0xFFEC4899),
                                            MaterialTheme.colorScheme.onSurface // Pass textColor
                                        )
                                        HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                                    }
                                }

                                trackedFood.saturatedFatG?.let {
                                    if (it > 0) {
                                        NutritionDetailRow(
                                            "🥓 Saturated Fat",
                                            formatMacroWeight(it, userProfile.weightUnit),
                                            Color(0xFFEF4444),
                                            MaterialTheme.colorScheme.onSurface // Pass textColor
                                        )
                                        HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                                    }
                                }

                                trackedFood.sodiumMg?.let {
                                    if (it > 0) {
                                        NutritionDetailRow(
                                            "🧂 Sodium",
                                            String.format(Locale.US, "%.0f mg", it),
                                            Color(0xFFF59E0B),
                                            MaterialTheme.colorScheme.onSurface // Pass textColor
                                        )
                                        HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                                    }
                                }

                                trackedFood.potassiumMg?.let {
                                    if (it > 0) {
                                        NutritionDetailRow(
                                            "🍌 Potassium",
                                            String.format(Locale.US, "%.0f mg", it),
                                            Color(0xFF3B82F6),
                                            MaterialTheme.colorScheme.onSurface // Pass textColor
                                        )
                                        HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                                    }
                                }

                                trackedFood.cholesterolMg?.let {
                                    if (it > 0) {
                                        NutritionDetailRow(
                                            "🥚 Cholesterol",
                                            String.format(Locale.US, "%.0f mg", it),
                                            Color(0xFF8B5CF6),
                                            MaterialTheme.colorScheme.onSurface // Pass textColor
                                        )
                                    }
                                }
                            }
                        }
                    }


                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = PrimaryBlue)
            }
        }
    )
}

@Composable
fun NutritionDetailRow(label: String, value: String, valueColor: Color, textColor: Color = MaterialTheme.colorScheme.onSurface) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = textColor
        )
        Text(
            value,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = valueColor
        )
    }
}

data class SavedCustomMeal(
    val id: String,
    val name: String,
    val items: List<Map<String, Any>>
)

@Composable
private fun MakeCustomMealsDialog(
    onDismiss: () -> Unit,
    onSaved: (SavedCustomMeal) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var ingredients by remember { mutableStateOf<List<TrackedFood>>(emptyList()) }
    var showFoodSearch by remember { mutableStateOf(false) }

    // Za izračun skupnih makrov
    val totalKcal = ingredients.sumOf { it.caloriesKcal }
    val totalProtein = ingredients.sumOf { it.proteinG ?: 0.0 }
    val totalCarbs = ingredients.sumOf { it.carbsG ?: 0.0 }
    val totalFat = ingredients.sumOf { it.fatG ?: 0.0 }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Custom Meal") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Meal Name") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(16.dp))

                Text("Ingredients:", style = MaterialTheme.typography.titleMedium)

                ingredients.forEach { item ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    ) {
                        Text(
                            "${item.name} (${item.amount.toInt()} ${item.unit})",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        IconButton(onClick = { ingredients = ingredients - item }) {
                            Icon(Icons.Filled.Close, "Remove")
                        }
                    }
                }

                Button(
                    onClick = { showFoodSearch = true },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                ) {
                    Text("Add Ingredient")
                }

                Spacer(Modifier.height(16.dp))

                if (ingredients.isNotEmpty()) {
                    Text("Total: ${totalKcal.roundToInt()} kcal", fontWeight = FontWeight.Bold)
                    Text("P: ${totalProtein.roundToInt()}g, C: ${totalCarbs.roundToInt()}g, F: ${totalFat.roundToInt()}g", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank() && ingredients.isNotEmpty()) {
                        val uid = com.example.myapplication.persistence.FirestoreHelper.getCurrentUserDocId()
                        if (uid != null) {
                            val itemsList = ingredients.map { tf ->
                                mapOf(
                                    "id" to tf.id,
                                    "name" to tf.name,
                                    "amt" to tf.amount.toString(),
                                    "unit" to tf.unit,
                                    "caloriesKcal" to tf.caloriesKcal,
                                    "proteinG" to (tf.proteinG ?: 0.0),
                                    "carbsG" to (tf.carbsG ?: 0.0),
                                    "fatG" to (tf.fatG ?: 0.0),
                                    "fiberG" to (tf.fiberG ?: 0.0),
                                    "sugarG" to (tf.sugarG ?: 0.0),
                                    "saturatedFatG" to (tf.saturatedFatG ?: 0.0),
                                    "sodiumMg" to (tf.sodiumMg ?: 0.0),
                                    "potassiumMg" to (tf.potassiumMg ?: 0.0),
                                    "cholesterolMg" to (tf.cholesterolMg ?: 0.0)
                                )
                            }

                            val mealData = mapOf(
                                "name" to name,
                                "items" to itemsList,
                                "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                            )

                            com.google.firebase.ktx.Firebase.firestore.collection("users").document(uid)
                                .collection("customMeals").add(mealData)
                                .addOnSuccessListener { ref ->
                                    val saved = SavedCustomMeal(ref.id, name, itemsList)
                                    onSaved(saved)
                                }
                        }
                    }
                },
                enabled = name.isNotBlank() && ingredients.isNotEmpty()
            ) {
                Text("Save Meal")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )

    if (showFoodSearch) {
        ModalBottomSheet(
            onDismissRequest = { showFoodSearch = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            AddFoodSheet(
                meal = MealType.Snacks, // Placeholder
                onClose = { showFoodSearch = false },
                onAddTracked = { tf ->
                    ingredients = ingredients + tf
                    showFoodSearch = false
                }
            )
        }
    }
}

