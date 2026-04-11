package com.example.myapplication.ui.screens

import androidx.compose.animation.core.AnimationEndReason
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.myapplication.data.WeekPlan

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlanPathVisualizer(
    currentDayGlobal: Int,
    isTodayDone: Boolean,
    weeklyGoal: Int,
    totalDays: Int,
    startWeek: Int,
    isDarkMode: Boolean,
    planWeeks: List<WeekPlan> = emptyList(),
    swapSourceDay: Int? = null,
    onNodeClick: (Int) -> Unit,
    onNodeLongClick: ((Int) -> Unit)? = null,
    onDragSwap: ((Int, Int) -> Unit)? = null,
    footerContent: @Composable () -> Unit
) {
    val totalNodes = totalDays
    val scrollState = rememberScrollState()
    val density = LocalDensity.current

    val currentWeek = ((currentDayGlobal - 1) / 7) + 1

    val dayPlanMap = remember(planWeeks) {
        buildMap { for (week in planWeeks) for (day in week.days) put(day.dayNumber, day) }
    }

    var draggedDay by remember { mutableIntStateOf(-1) }
    var dragOffsetX by remember { mutableFloatStateOf(0f) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    var dropTargetDay by remember { mutableIntStateOf(-1) }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(scrollState)) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth().wrapContentHeight()) {
            val containerWidth = this@BoxWithConstraints.maxWidth
            val containerWidthPx = with(density) { containerWidth.toPx() }

            val nodeSpacingDp = 80f
            val totalHeightDp = (totalNodes * nodeSpacingDp) + 200f
            val totalHeight = totalHeightDp.dp

            val points = remember(totalNodes) {
                (0 until totalNodes).map { i ->
                    val x = 0.5f + 0.35f * kotlin.math.sin(i * 0.8).toFloat()
                    val y = 50f + (i * nodeSpacingDp)
                    Pair(x, y)
                }
            }

            val nodePositionsPx = remember(points, containerWidthPx) {
                points.mapIndexed { idx, (x, y) ->
                    val px = x * containerWidthPx
                    val py = with(density) { y.dp.toPx() }
                    idx to Offset(px, py)
                }
            }

            Canvas(modifier = Modifier.fillMaxWidth().height(totalHeight)) {
                val canvasWidth = size.width
                val pathEffect = PathEffect.dashPathEffect(floatArrayOf(20f, 20f), 0f)

                for (i in 7 until totalNodes step 7) {
                    val sepY = (50f + (i * nodeSpacingDp) - 40f).dp.toPx()
                    drawLine(
                        color = if (isDarkMode) Color.DarkGray else Color.LightGray,
                        start = Offset(0f, sepY), end = Offset(canvasWidth, sepY),
                        strokeWidth = 2.dp.toPx(), pathEffect = pathEffect
                    )
                }

                if (points.size > 1) {
                    for (i in 0 until points.size - 1) {
                        val (sx, sy) = points[i]; val (ex, ey) = points[i + 1]
                        val lineColor = if ((i + 1) < currentDayGlobal) Color(0xFF4CAF50) else Color.Gray
                        drawLine(
                            color = lineColor,
                            start = Offset(sx * canvasWidth, sy.dp.toPx()),
                            end = Offset(ex * canvasWidth, ey.dp.toPx()),
                            strokeWidth = 4.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    }
                }
            }

            Box(modifier = Modifier.fillMaxWidth().height(totalHeight)) {
                points.forEachIndexed { index, (pointX, pointY) ->
                    val globalDay = index + 1
                    val weekNumOfNode = ((globalDay - 1) / 7) + 1
                    val isLastDayOfWeek = (globalDay % 7) == 0

                    val dayPlan = dayPlanMap[globalDay]
                    val isRestDay = dayPlan?.isRestDay ?: false
                    val focusLabel = dayPlan?.focusLabel ?: ""
                    val isFrozen = dayPlan?.isFrozen ?: false
                    val isSwapped = dayPlan?.isSwapped ?: false

                    val isPast = globalDay < currentDayGlobal
                    val isToday = globalDay == currentDayGlobal
                    val isLockedToday = isToday && isTodayDone
                    val isFuture = globalDay > currentDayGlobal
                    val isFutureWeek = weekNumOfNode > currentWeek
                    val isCompleted = isPast

                    val isDragSource = draggedDay == globalDay
                    val isDropTarget = dropTargetDay == globalDay && draggedDay != -1 && draggedDay != globalDay
                    val isDraggable = globalDay >= currentDayGlobal && onDragSwap != null && !isFrozen

                    val nodeColor = when {
                        isFrozen -> Color(0xFF00BCD4) // Ledena barva
                        isDropTarget -> Color(0xFFFF9800)
                        isDragSource -> Color(0xFF7C3AED)
                        swapSourceDay == globalDay -> Color(0xFF7C3AED)
                        swapSourceDay != null && !isRestDay && globalDay >= currentDayGlobal &&
                            ((swapSourceDay - 1) / 7) == ((globalDay - 1) / 7) -> Color(0xFFFF9800)
                        isSwapped && isCompleted -> Color(0xFF9C27B0) // Zamenjani opravljeni
                        isSwapped -> Color(0xFFBA68C8) // Zamenjani
                        isRestDay && isCompleted -> Color(0xFF546E7A)
                        isRestDay && isToday -> Color(0xFF78909C)
                        isRestDay -> if (isDarkMode) Color(0xFF37474F) else Color(0xFFB0BEC5)
                        isCompleted -> Color(0xFF4CAF50)
                        isLockedToday -> if (isDarkMode) Color(0xFF424242) else Color(0xFFEEEEEE)
                        isToday -> if (isDarkMode) Color.DarkGray else Color(0xFFE0E0E0)
                        isFutureWeek -> if (isDarkMode) Color.DarkGray else Color.LightGray
                        isFuture -> if (isDarkMode) Color(0xFF424242) else Color(0xFFEEEEEE)
                        else -> Color(0xFFE0E0E0)
                    }

                    val baseNodeSize = if (isRestDay) 48.dp else 56.dp
                    val nodeScaleTarget = if (isDragSource) 1.2f else if (isDropTarget) 1.1f else 1f
                    val nodeScale by animateFloatAsState(
                        targetValue = nodeScaleTarget,
                        animationSpec = tween(150),
                        label = "nodeScale_$globalDay"
                    )

                    val leftOffset = (containerWidth * pointX) - (baseNodeSize / 2)
                    val topOffset = pointY.dp - (baseNodeSize / 2)

                    val extraOffsetX = if (isDragSource) with(density) { dragOffsetX.toDp() } else 0.dp
                    val extraOffsetY = if (isDragSource) with(density) { dragOffsetY.toDp() } else 0.dp

                    Box(
                        modifier = Modifier
                            .absoluteOffset(x = leftOffset + extraOffsetX, y = topOffset + extraOffsetY)
                            .size(baseNodeSize * nodeScale)
                            .zIndex(if (isDragSource) 10f else 1f)
                            .clip(if (isRestDay) RoundedCornerShape(8.dp) else CircleShape)
                            .background(nodeColor)
                            .then(if (isDragSource) Modifier.shadow(8.dp, CircleShape) else Modifier)
                            .then(
                                if (isDraggable) {
                                    Modifier.pointerInput(globalDay) {
                                        detectDragGestures(
                                            onDragStart = {
                                                draggedDay = globalDay
                                                dragOffsetX = 0f
                                                dragOffsetY = 0f
                                                dropTargetDay = -1
                                            },
                                            onDrag = { change, dragAmount ->
                                                change.consume()
                                                dragOffsetX += dragAmount.x
                                                dragOffsetY += dragAmount.y
                                                val srcPx = nodePositionsPx.firstOrNull { it.first == index }?.second
                                                    ?: return@detectDragGestures
                                                val currentAbsX = srcPx.x + dragOffsetX
                                                val currentAbsY = srcPx.y + dragOffsetY
                                                val draggedWeek = (globalDay - 1) / 7
                                                var bestDist = Float.MAX_VALUE
                                                var bestDay = -1
                                                nodePositionsPx.forEach { (idx, pos) ->
                                                    val d = idx + 1
                                                    val dWeek = (d - 1) / 7
                                                    if (d != globalDay && d >= currentDayGlobal && dWeek == draggedWeek) {
                                                        val dist = kotlin.math.sqrt(
                                                            (pos.x - currentAbsX) * (pos.x - currentAbsX) +
                                                            (pos.y - currentAbsY) * (pos.y - currentAbsY)
                                                        )
                                                        if (dist < bestDist) { bestDist = dist; bestDay = d }
                                                    }
                                                }
                                                val snapThresholdPx = with(density) { 80.dp.toPx() }
                                                dropTargetDay = if (bestDist < snapThresholdPx) bestDay else -1
                                            },
                                            onDragEnd = {
                                                val from = draggedDay; val to = dropTargetDay
                                                if (from != -1 && to != -1 && from != to) onDragSwap?.invoke(from, to)
                                                draggedDay = -1; dragOffsetX = 0f; dragOffsetY = 0f; dropTargetDay = -1
                                            },
                                            onDragCancel = {
                                                draggedDay = -1; dragOffsetX = 0f; dragOffsetY = 0f; dropTargetDay = -1
                                            }
                                        )
                                    }
                                } else Modifier
                            )
                            .combinedClickable(
                                onClick = { if (draggedDay == -1 && !isFrozen) onNodeClick(globalDay) },
                                onLongClick = { if (!isFrozen) onNodeLongClick?.invoke(globalDay) }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            if (isFrozen) {
                                Text("❄️", fontSize = 20.sp)
                                Text("FROZEN", color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                            } else if (isRestDay) {
                                Text("💤", fontSize = if (isCompleted || isToday) 20.sp else 16.sp)
                                if (!isFuture || isToday) {
                                    Text(if (isSwapped) "SWAP" else "REST", color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                } else if (isSwapped && isFuture) {
                                    Text("SWAP", color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                }
                            } else if (isCompleted) {
                                Text("$globalDay", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp, textAlign = TextAlign.Center)
                                Text("✓", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                            } else if (isLockedToday || (isFuture && !isRestDay)) {
                                if (focusLabel.isNotBlank() && !isFutureWeek) {
                                    Text("$globalDay", color = if (isDarkMode) Color.White.copy(alpha = 0.5f) else Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    Text(focusLabel.take(4), color = if (isDarkMode) Color.White.copy(alpha = 0.4f) else Color.Gray.copy(alpha = 0.7f), fontSize = 7.sp)
                                } else {
                                    Icon(Icons.Filled.Lock, contentDescription = "Locked", tint = Color.Gray, modifier = Modifier.size(20.dp))
                                }
                            } else {
                                val txtColor = if (isDarkMode) Color.White else Color.Black
                                Text("$globalDay", color = txtColor, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                if (focusLabel.isNotBlank()) {
                                    Text(focusLabel.take(5), color = txtColor.copy(alpha = 0.7f), fontSize = 8.sp)
                                }
                            }
                        }
                    }

                    if (isLastDayOfWeek) {
                        val trophyCompleted = isCompleted
                        val trophyColor = if (trophyCompleted) Color(0xFFFFD700) else Color(0xFF4A5568)
                        val trophyLeft = if (pointX > 0.5f)
                            leftOffset + extraOffsetX + baseNodeSize + 4.dp
                        else
                            leftOffset + extraOffsetX - 28.dp
                        Box(
                            modifier = Modifier
                                .absoluteOffset(x = trophyLeft, y = topOffset + extraOffsetY + 4.dp)
                                .size(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("🏆", fontSize = if (trophyCompleted) 20.sp else 16.sp, color = trophyColor)
                        }
                    }

                    if (index % 7 == 0 && index > 0) {
                        val weekNum = (index / 7) + 1
                        val labelLeft = if (pointX > 0.5f)
                            (containerWidth * pointX) - 120.dp
                        else
                            (containerWidth * pointX) + 40.dp
                        val labelTop = topOffset + 25.dp
                        Column(modifier = Modifier.absoluteOffset(x = labelLeft, y = labelTop)) {
                            Text(
                                text = "WEEK $weekNum",
                                color = if (isDarkMode) Color.LightGray else Color.Gray,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                letterSpacing = 1.sp
                            )
                            if (weekNum > currentWeek) {
                                Text("Locked", color = if (isDarkMode) Color.DarkGray else Color.LightGray, fontSize = 10.sp)
                            }
                        }
                    }

                    if (isDropTarget) {
                        val indicatorLeft = leftOffset + extraOffsetX - 20.dp
                        val indicatorTop = topOffset + extraOffsetY - 28.dp
                        Box(
                            modifier = Modifier
                                .absoluteOffset(x = indicatorLeft, y = indicatorTop)
                                .background(Color(0xFFFF9800), RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text("↔ swap", fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            footerContent()
        }
    }
}

@Composable
fun EpicCounter(
    targetValue: Int,
    animate: Boolean,
    onAnimationEnd: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = Color.White,
    fontSize: androidx.compose.ui.unit.TextUnit = 34.sp,
    fontWeight: FontWeight = FontWeight.Bold
) {
    val count = remember { Animatable(targetValue.toFloat()) }

    LaunchedEffect(animate, targetValue) {
        if (animate) {
            count.snapTo(0f)
            val result = count.animateTo(
                targetValue = targetValue.toFloat(),
                animationSpec = tween(durationMillis = 1500, easing = FastOutSlowInEasing)
            )
            if (result.endReason == AnimationEndReason.Finished) {
                onAnimationEnd()
            }
        } else {
            count.snapTo(targetValue.toFloat())
        }
    }

    val scale = remember { Animatable(1f) }
    LaunchedEffect(count.value) {
        if (count.value == targetValue.toFloat() && animate) {
            scale.animateTo(1.2f, tween(150))
            scale.animateTo(1f, tween(150))
        }
    }

    Text(
        text = "DAY ${count.value.toInt()}",
        color = color,
        fontSize = fontSize,
        fontWeight = fontWeight,
        modifier = modifier.scale(scale.value)
    )
}

