package com.example.myapplication.ui.screens

// =====================================================================
// DonutProgressView.kt
// Vsebuje: custom Android View za dvojni krog (kalorije + voda).
// Uporablja se v NutritionScreen z AndroidView { }.
// =====================================================================

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.annotation.ColorInt
import kotlin.math.roundToInt

// UPP Design System colors (Figma spec)
private object DonutUppColors {
    val orange     = 0xFFFF6411.toInt()  // Fat segment / primary
    val blue       = 0xFF648DE5.toInt()  // Carbs / water inner
    val lightGray  = 0xFFE0E2DB.toInt()  // Protein / track
    val error      = 0xFFFF4444.toInt()  // Exceeded kalorij
    val mutedText  = 0xFF9E9E9E.toInt()  // Labels
    val divider    = 0xFF2C2C2C.toInt()  // Base track
    val darkBg     = 0xFF181818.toInt()  // Background
    val white      = 0xFFFCFCFC.toInt()  // Primary text
}

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

    @ColorInt var outerBaseColor: Int = DonutUppColors.divider
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

    @ColorInt var innerBaseColor: Int = DonutUppColors.divider
        set(value) { field = value; innerBasePaint.color = value; invalidate() }
    @ColorInt var innerProgressColor: Int = DonutUppColors.blue
        set(value) { field = value; innerProgressPaint.color = value; invalidate() }

    var startAngle: Float = 135f
    var sweepAngle: Float = 270f

    @ColorInt var textColor: Int = DonutUppColors.white
        set(value) { field = value; invalidate() }

    @ColorInt var waterColor: Int = DonutUppColors.blue
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
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND
        strokeWidth = outerThicknessPx; color = outerBaseColor
    }
    private val outerFatPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND
        strokeWidth = outerThicknessPx; color = DonutUppColors.orange   // Fat → oranžna (Figma)
    }
    private val outerProteinPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND
        strokeWidth = outerThicknessPx; color = DonutUppColors.lightGray // Protein → svetlo siva (Figma)
    }
    private val outerCarbsPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND
        strokeWidth = outerThicknessPx; color = DonutUppColors.blue      // Carbs → modra (Figma)
    }
    private val innerBasePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND
        strokeWidth = innerThicknessPx; color = innerBaseColor
    }
    private val innerProgressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND
        strokeWidth = innerThicknessPx; color = innerProgressColor
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER; color = DonutUppColors.white
    }
    // Reusable paints — ne ustvarjamo novih v onDraw
    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND
        strokeWidth = outerThicknessPx * 1.2f
    }
    private val overPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND
        strokeWidth = outerThicknessPx * 0.35f; color = 0xFFDC2626.toInt()
    }

    private val outerArcRect = RectF()
    private val innerArcRect = RectF()

    init { isClickable = true }

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
            paddingLeft + outerHalf, paddingTop + outerHalf,
            width - paddingRight - outerHalf, height - paddingBottom - outerHalf
        )
        val innerMargin = outerThicknessPx + dp(4f)
        val innerHalf = innerThicknessPx / 2f
        innerArcRect.set(
            paddingLeft + innerMargin + innerHalf, paddingTop + innerMargin + innerHalf,
            width - paddingRight - innerMargin - innerHalf, height - paddingBottom - innerMargin - innerHalf
        )
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (simpleMode) return super.onTouchEvent(event)
        if (event.action == MotionEvent.ACTION_DOWN) {
            val dx = event.x - width / 2f
            val dy = event.y - height / 2f
            val distance = kotlin.math.sqrt(dx * dx + dy * dy)
            val outerRadius = outerArcRect.width() / 2f
            val innerRadius = outerRadius - outerThicknessPx
            val touchTolerance = dp(24f)
            if (distance >= (innerRadius - touchTolerance).coerceAtLeast(0f) &&
                distance <= outerRadius + touchTolerance) {
                var touchAngle = Math.toDegrees(kotlin.math.atan2(dy.toDouble(), dx.toDouble())).toFloat()
                touchAngle = (touchAngle + 360) % 360
                val adjustedAngle = (touchAngle - startAngle + 360) % 360
                val consumedFraction = (consumedCalories / targetCalories.toFloat()).coerceIn(0f, 1f)
                val availableSweep   = sweepAngle * consumedFraction
                if (adjustedAngle <= availableSweep) {
                    val totalMacroCalories = fatCalories + proteinCalories + carbsCalories
                    if (totalMacroCalories > 0) {
                        // Faza 13b FIX: koti segmentov kot delež ZAUŽITIH kalorij (availableSweep),
                        // ne targetCalories. Segmenti so zdaj vizualno vsebovani znotraj consumed loka.
                        val fatSweep     = availableSweep * (fatCalories.toFloat() / totalMacroCalories)
                        val proteinSweep = availableSweep * (proteinCalories.toFloat() / totalMacroCalories)
                        clickedSegment = when {
                            adjustedAngle <= fatSweep                      -> "fat"
                            adjustedAngle <= fatSweep + proteinSweep       -> "protein"
                            else                                           -> "carbs"
                        }
                        performClick(); return true
                    }
                }
                clickedSegment = null; performClick(); return true
            }
            clickedSegment = null; performClick(); return true
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        onSegmentClick?.invoke(clickedSegment ?: "")
        invalidate()
        return super.performClick()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val consumedFraction = (consumedCalories / targetCalories.toFloat()).coerceIn(0f, 1f)

        // Draw outer background track
        canvas.drawArc(outerArcRect, startAngle, sweepAngle, false, outerBasePaint)

        // Calculate active sweep
        val availableSweep = sweepAngle * consumedFraction

        // Zaokroževanje in barve (če ni doseženo -> SIVO, če je preseženo -> RDEČE)
        val isExceeded = consumedCalories >= targetCalories

        // Dinamična barva za navadni progres - sivo ali rdeče (po navodilih "če dnevna norma še ni zaužita naj bo sivo...")
        val activeColor = if (isExceeded) DonutUppColors.error else DonutUppColors.orange // Red if exceeded, Orange if not

        if (simpleMode) {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND
                strokeWidth = outerThicknessPx; color = activeColor
            }
            if (availableSweep > 0.5f) {
                canvas.drawArc(outerArcRect, startAngle, availableSweep, false, paint)
            }
        } else {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND
                strokeWidth = outerThicknessPx; color = activeColor
            }
            if (availableSweep > 0.5f) {
                canvas.drawArc(outerArcRect, startAngle, availableSweep, false, paint)
            }
        }

        // Notranji krog (voda)
        canvas.drawArc(innerArcRect, startAngle, sweepAngle, false, innerBasePaint)
        canvas.drawArc(innerArcRect, startAngle, sweepAngle * innerProgress, false, innerProgressPaint)

        val centerX = width / 2f
        val centerY = height / 2f

        if (!simpleMode && clickedSegment != null) {
            val isImperial = weightUnit == "lbs" || weightUnit == "lb"
                    val unitLabel = if (isImperial) "oz" else "g"
                    val details = when (clickedSegment) {
                        "fat" -> {
                            val g = fatCalories / 9.0
                            val value = if (isImperial) (g / 28.3495).roundToInt() else g.roundToInt()
                            SegmentDetails("$value $unitLabel", "Fat", DonutUppColors.orange, fatCalories)
                        }
                        "protein" -> {
                            val g = proteinCalories / 4.0
                            val value = if (isImperial) (g / 28.3495).roundToInt() else g.roundToInt()
                            SegmentDetails("$value $unitLabel", "Protein", DonutUppColors.lightGray, proteinCalories)
                        }
                        "carbs" -> {
                            val g = carbsCalories / 4.0
                            val value = if (isImperial) (g / 28.3495).roundToInt() else g.roundToInt()
                            SegmentDetails("$value $unitLabel", "Carbs", DonutUppColors.blue, carbsCalories)
                        }
                        else -> SegmentDetails(centerValue, centerLabel, textColor, 0)
                    }
            textPaint.color = details.color; textPaint.textSize = dp(48f); textPaint.isFakeBoldText = true
            canvas.drawText(details.value, centerX, centerY - dp(10f), textPaint)
            textPaint.textSize = dp(18f); textPaint.isFakeBoldText = false; textPaint.color = DonutUppColors.mutedText
            canvas.drawText(details.label, centerX, centerY + dp(15f), textPaint)
            textPaint.textSize = dp(22f); textPaint.isFakeBoldText = true; textPaint.color = details.color
            canvas.drawText("${details.calories} kcal", centerX, centerY + dp(45f), textPaint)
        } else {
            textPaint.color = waterColor; textPaint.textSize = dp(40f); textPaint.isFakeBoldText = true
            canvas.drawText(innerValue, centerX, centerY - dp(10f), textPaint)
            textPaint.textSize = dp(16f); textPaint.isFakeBoldText = false; textPaint.color = DonutUppColors.mutedText
            canvas.drawText(innerLabel, centerX, centerY + dp(10f), textPaint)
            textPaint.color = textColor; textPaint.textSize = dp(28f); textPaint.isFakeBoldText = true
            canvas.drawText(centerValue, centerX, centerY + dp(45f), textPaint)
            textPaint.textSize = dp(14f); textPaint.isFakeBoldText = false; textPaint.color = DonutUppColors.mutedText
            canvas.drawText(centerLabel, centerX, centerY + dp(62f), textPaint)
        }
    }

    private data class SegmentDetails(val value: String, val label: String, val color: Int, val calories: Int)
    private fun dp(v: Float) = v * resources.displayMetrics.density
}
