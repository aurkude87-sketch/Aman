package com.example.ui.components

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ChartAnchor
import com.example.model.ChartDrawing
import com.example.model.ChartMarkingTool
import com.example.model.ForexPair
import com.example.model.FxCandle
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.ChartTagDarkText
import com.example.ui.theme.CrimsonBear
import com.example.ui.theme.CyanIndicator
import com.example.ui.theme.EmeraldBull
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.RoseIndicator
import com.example.ui.theme.VioletIndicator
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

internal fun resolveAnchorX(
    anchor: ChartAnchor,
    displayCandles: List<FxCandle>,
    stepX: Float
): Float {
    val matchIdx = displayCandles.indexOfFirst { it.timestampMillis == anchor.candleTimestampMillis }
    val idx = if (matchIdx >= 0) matchIdx else anchor.fallbackIndex.coerceIn(0, (displayCandles.size - 1).coerceAtLeast(0))
    return idx * stepX + stepX / 2f
}

internal fun DrawScope.drawChartDrawings(
    drawings: List<ChartDrawing>,
    pendingStart: ChartAnchor?,
    livePreviewEnd: ChartAnchor?,
    activeTool: ChartMarkingTool,
    displayCandles: List<FxCandle>,
    pair: ForexPair,
    stepX: Float,
    plotWidth: Float,
    plotHeight: Float,
    rightAxisWidth: Float,
    yFor: (Double) -> Float,
    textMeasurer: TextMeasurer
) {
    val previewDrawing: ChartDrawing? = if (pendingStart != null && livePreviewEnd != null && activeTool.isTwoPointTool) {
        when (activeTool) {
            ChartMarkingTool.TREND_LINE -> ChartDrawing.TrendLine("preview", pendingStart, livePreviewEnd)
            ChartMarkingTool.FIB_RETRACEMENT -> ChartDrawing.FibonacciRetracement("preview", pendingStart, livePreviewEnd)
            ChartMarkingTool.RECTANGLE_ZONE -> ChartDrawing.ZoneBox("preview", pendingStart, livePreviewEnd)
            ChartMarkingTool.RR_PROJECTION -> ChartDrawing.RiskRewardBox("preview", pendingStart, livePreviewEnd)
            else -> null
        }
    } else null

    val allToDraw = if (previewDrawing != null) drawings + previewDrawing else drawings

    allToDraw.forEach { drawing ->
        val isPreview = drawing.id == "preview"
        val alphaScale = if (isPreview) 0.72f else 1f
        when (drawing) {
            is ChartDrawing.HorizontalLine -> {
                val y = yFor(drawing.price).coerceIn(2f, plotHeight - 2f)
                val color = AmberWarning.copy(alpha = alphaScale)
                drawLine(color = color, start = Offset(0f, y), end = Offset(plotWidth, y), strokeWidth = 2.2f)
                drawAxisPricePill(
                    text = "${drawing.label} ${pair.formatPrice(drawing.price)}",
                    y = y,
                    color = color,
                    plotWidth = plotWidth,
                    plotHeight = plotHeight,
                    rightAxisWidth = rightAxisWidth,
                    textMeasurer = textMeasurer
                )
            }

            is ChartDrawing.TrendLine -> {
                val x1 = resolveAnchorX(drawing.start, displayCandles, stepX)
                val y1 = yFor(drawing.start.price).coerceIn(2f, plotHeight - 2f)
                val x2 = resolveAnchorX(drawing.end, displayCandles, stepX)
                val y2 = yFor(drawing.end.price).coerceIn(2f, plotHeight - 2f)
                val color = CyanIndicator.copy(alpha = alphaScale)
                drawLine(
                    color = color,
                    start = Offset(x1, y1),
                    end = Offset(x2, y2),
                    strokeWidth = 2.5f,
                    cap = StrokeCap.Round
                )
                drawCircle(color = color, radius = 4.5.dp.toPx(), center = Offset(x1, y1))
                drawCircle(color = color, radius = 4.5.dp.toPx(), center = Offset(x2, y2))
            }

            is ChartDrawing.FibonacciRetracement -> {
                val x1 = resolveAnchorX(drawing.start, displayCandles, stepX)
                val x2 = resolveAnchorX(drawing.end, displayCandles, stepX)
                val leftX = min(x1, x2).coerceAtLeast(0f)
                val rightX = max(x1, x2).coerceAtLeast(leftX + 40.dp.toPx()).coerceAtMost(plotWidth)
                val startPrice = drawing.start.price
                val endPrice = drawing.end.price
                val diff = endPrice - startPrice

                val fibRatios = listOf(0.0, 0.236, 0.382, 0.5, 0.618, 0.786, 1.0)
                val labelStyle = TextStyle(
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 8.sp,
                    color = RoseIndicator.copy(alpha = alphaScale)
                )
                // Diagonal anchor connector
                drawLine(
                    color = RoseIndicator.copy(alpha = 0.45f * alphaScale),
                    start = Offset(x1, yFor(startPrice)),
                    end = Offset(x2, yFor(endPrice)),
                    strokeWidth = 1.4f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f))
                )
                fibRatios.forEach { ratio ->
                    val levelPrice = endPrice - diff * ratio
                    val y = yFor(levelPrice).coerceIn(2f, plotHeight - 2f)
                    val isGolden = ratio == 0.618 || ratio == 0.5
                    val lineColor = if (isGolden) AmberWarning.copy(alpha = 0.9f * alphaScale) else RoseIndicator.copy(alpha = 0.7f * alphaScale)
                    drawLine(
                        color = lineColor,
                        start = Offset(leftX, y),
                        end = Offset(rightX, y),
                        strokeWidth = if (isGolden) 1.8f else 1.2f
                    )
                    val pctText = "${"%.1f".format(Locale.US, ratio * 100)}% ${pair.formatPrice(levelPrice)}"
                    val measured = textMeasurer.measure(pctText, labelStyle.copy(color = lineColor))
                    val textY = (y - measured.size.height - 1.dp.toPx()).coerceIn(0f, plotHeight - measured.size.height)
                    drawText(measured, topLeft = Offset(leftX + 3.dp.toPx(), textY))
                }
            }

            is ChartDrawing.ZoneBox -> {
                val x1 = resolveAnchorX(drawing.start, displayCandles, stepX)
                val x2 = resolveAnchorX(drawing.end, displayCandles, stepX)
                val y1 = yFor(drawing.start.price)
                val y2 = yFor(drawing.end.price)
                val left = min(x1, x2).coerceIn(0f, plotWidth)
                val right = max(x1, x2).coerceIn(left + 12.dp.toPx(), plotWidth)
                val top = min(y1, y2).coerceIn(0f, plotHeight)
                val bottom = max(y1, y2).coerceIn(top + 8.dp.toPx(), plotHeight)
                val zoneColor = VioletIndicator.copy(alpha = alphaScale)
                drawRect(
                    color = zoneColor.copy(alpha = 0.16f * alphaScale),
                    topLeft = Offset(left, top),
                    size = Size(right - left, bottom - top)
                )
                drawRect(
                    color = zoneColor.copy(alpha = 0.85f * alphaScale),
                    topLeft = Offset(left, top),
                    size = Size(right - left, bottom - top),
                    style = Stroke(width = 1.6f)
                )
            }

            is ChartDrawing.RiskRewardBox -> {
                val x1 = resolveAnchorX(drawing.entry, displayCandles, stepX)
                val x2 = resolveAnchorX(drawing.stop, displayCandles, stepX)
                val left = min(x1, x2).coerceIn(0f, plotWidth)
                val right = max(x1, x2).coerceAtLeast(left + 44.dp.toPx()).coerceAtMost(plotWidth)
                val entryPrice = drawing.entry.price
                val stopPrice = drawing.stop.price
                val riskDist = (entryPrice - stopPrice)
                val targetPrice = entryPrice + riskDist * 2.0

                val entryY = yFor(entryPrice).coerceIn(2f, plotHeight - 2f)
                val stopY = yFor(stopPrice).coerceIn(2f, plotHeight - 2f)
                val targetY = yFor(targetPrice).coerceIn(2f, plotHeight - 2f)

                // Risk Zone
                drawRect(
                    color = CrimsonBear.copy(alpha = 0.18f * alphaScale),
                    topLeft = Offset(left, min(entryY, stopY)),
                    size = Size(right - left, abs(stopY - entryY).coerceAtLeast(4f))
                )
                // Reward Zone (2R)
                drawRect(
                    color = EmeraldBull.copy(alpha = 0.18f * alphaScale),
                    topLeft = Offset(left, min(entryY, targetY)),
                    size = Size(right - left, abs(targetY - entryY).coerceAtLeast(4f))
                )
                drawLine(CyanIndicator, Offset(left, entryY), Offset(right, entryY), 2f)
                drawLine(CrimsonBear, Offset(left, stopY), Offset(right, stopY), 1.6f)
                drawLine(EmeraldBull, Offset(left, targetY), Offset(right, targetY), 1.6f)

                val slPips = abs(entryPrice - stopPrice) / pair.pipSize
                val tpPips = slPips * 2.0
                val rrStyle = TextStyle(fontFamily = JetBrainsMonoFamily, fontSize = 8.5.sp, color = Color.White)
                val rrLabel = "1:2.0 R:R (+${"%.1f".format(Locale.US, tpPips)}p / -${"%.1f".format(Locale.US, slPips)}p)"
                val measured = textMeasurer.measure(rrLabel, rrStyle)
                drawText(measured, topLeft = Offset(left + 4.dp.toPx(), (min(entryY, targetY) + 3.dp.toPx()).coerceIn(0f, plotHeight - measured.size.height)))
            }

            is ChartDrawing.PricePin -> {
                val x = resolveAnchorX(drawing.anchor, displayCandles, stepX)
                val y = yFor(drawing.anchor.price).coerceIn(8f, plotHeight - 8f)
                val pinColor = if (drawing.isBullish) EmeraldBull else CrimsonBear
                val triPath = Path().apply {
                    if (drawing.isBullish) {
                        moveTo(x, y - 6.dp.toPx())
                        lineTo(x - 5.dp.toPx(), y + 4.dp.toPx())
                        lineTo(x + 5.dp.toPx(), y + 4.dp.toPx())
                    } else {
                        moveTo(x, y + 6.dp.toPx())
                        lineTo(x - 5.dp.toPx(), y - 4.dp.toPx())
                        lineTo(x + 5.dp.toPx(), y - 4.dp.toPx())
                    }
                    close()
                }
                drawPath(triPath, color = pinColor)
                val pinStyle = TextStyle(fontFamily = JetBrainsMonoFamily, fontSize = 8.sp, color = pinColor)
                val measured = textMeasurer.measure(pair.formatPrice(drawing.anchor.price), pinStyle)
                val labelX = (x + 7.dp.toPx()).coerceAtMost(plotWidth - measured.size.width - 2f)
                val labelY = (y - measured.size.height / 2f).coerceIn(0f, plotHeight - measured.size.height)
                drawText(measured, topLeft = Offset(labelX, labelY))
            }
        }
    }

    // Draw anchor dot if awaiting second tap of a 2-point drawing tool
    if (pendingStart != null && livePreviewEnd == null && activeTool.isTwoPointTool) {
        val x = resolveAnchorX(pendingStart, displayCandles, stepX)
        val y = yFor(pendingStart.price).coerceIn(4f, plotHeight - 4f)
        drawCircle(color = AmberWarning, radius = 6.dp.toPx(), center = Offset(x, y))
        drawCircle(color = Color.White, radius = 2.5.dp.toPx(), center = Offset(x, y))
    }
}

internal fun DrawScope.drawAxisPricePill(
    text: String,
    y: Float,
    color: Color,
    plotWidth: Float,
    plotHeight: Float,
    rightAxisWidth: Float,
    textMeasurer: TextMeasurer
) {
    val tagStyle = TextStyle(
        fontFamily = JetBrainsMonoFamily,
        fontSize = 8.5.sp,
        color = ChartTagDarkText
    )
    val measured = textMeasurer.measure(text, tagStyle)
    val boxW = (measured.size.width + 8.dp.toPx()).coerceAtMost(rightAxisWidth - 2f)
    val boxH = measured.size.height + 4.dp.toPx()
    val boxTop = (y - boxH / 2f).coerceIn(0f, plotHeight - boxH)
    drawRoundRect(
        color = color,
        topLeft = Offset(plotWidth + 2.dp.toPx(), boxTop),
        size = Size(boxW, boxH),
        cornerRadius = CornerRadius(4.dp.toPx())
    )
    drawText(
        textLayoutResult = measured,
        topLeft = Offset(plotWidth + 6.dp.toPx(), boxTop + 2.dp.toPx())
    )
}
