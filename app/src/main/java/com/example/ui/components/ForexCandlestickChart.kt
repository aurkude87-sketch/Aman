package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AddLocationAlt
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.HorizontalRule
import androidx.compose.material.icons.filled.LinearScale
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ChartAnchor
import com.example.model.ChartDrawing
import com.example.model.ChartMarkingTool
import com.example.model.ChartOverlayIndicator
import com.example.model.ForexPair
import com.example.model.ForexTrade
import com.example.model.FxCandle
import com.example.model.OscillatorPane
import com.example.model.TechnicalAnalysisResult
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CrimsonBear
import com.example.ui.theme.CyanContainer
import com.example.ui.theme.CyanIndicator
import com.example.ui.theme.CyanOnContainer
import com.example.ui.theme.EmeraldBull
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.RoseIndicator
import com.example.ui.theme.TabularNumericStyle
import com.example.ui.theme.VioletIndicator
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

@Composable
fun ForexCandlestickChart(
    pair: ForexPair,
    candles: List<FxCandle>,
    analysis: TechnicalAnalysisResult?,
    activeTrade: ForexTrade?,
    enabledOverlays: Set<ChartOverlayIndicator> = setOf(ChartOverlayIndicator.EMA, ChartOverlayIndicator.BOLLINGER),
    selectedOscillator: OscillatorPane = OscillatorPane.RSI,
    activeMarkingTool: ChartMarkingTool = ChartMarkingTool.BROWSE,
    drawings: List<ChartDrawing> = emptyList(),
    onToggleOverlay: (ChartOverlayIndicator) -> Unit = {},
    onSetAllOverlays: (Boolean) -> Unit = {},
    onSelectOscillator: (OscillatorPane) -> Unit = {},
    onSelectMarkingTool: (ChartMarkingTool) -> Unit = {},
    onAddDrawing: (ChartDrawing) -> Unit = {},
    onUndoDrawing: () -> Unit = {},
    onClearDrawings: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val visibleCount = 40
    val displayCandles = candles.takeLast(visibleCount)
    val startIdx = (candles.size - displayCandles.size).coerceAtLeast(0)

    val entryLine = activeTrade?.entryPrice ?: analysis?.exactEntryPrice
    val slLine = activeTrade?.stopLoss ?: analysis?.exactStopLoss
    val tpLine = activeTrade?.takeProfit ?: analysis?.exactTakeProfit

    val textMeasurer = rememberTextMeasurer()
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
    val labelTextColor = MaterialTheme.colorScheme.onSurfaceVariant

    var pendingStart by remember(pair, activeMarkingTool) { mutableStateOf<ChartAnchor?>(null) }
    var livePreviewEnd by remember(pair, activeMarkingTool) { mutableStateOf<ChartAnchor?>(null) }
    var crosshairOffset by remember(pair) { mutableStateOf<Offset?>(null) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    val density = LocalDensity.current
    val padPx = with(density) { 8.dp.toPx() }
    val padStartPx = with(density) { 6.dp.toPx() }
    val rightAxisWidthPx = with(density) { 68.dp.toPx() }

    val emaFastSlice = analysis?.emaFastSeries?.drop(startIdx).orEmpty()
    val emaSlowSlice = analysis?.emaSlowSeries?.drop(startIdx).orEmpty()
    val sma50Slice = analysis?.sma50Series?.drop(startIdx).orEmpty()
    val bbUpperSlice = analysis?.bbUpperSeries?.drop(startIdx).orEmpty()
    val bbLowerSlice = analysis?.bbLowerSeries?.drop(startIdx).orEmpty()
    val psarSlice = analysis?.psarSeries?.drop(startIdx).orEmpty()
    val tenkanSlice = analysis?.ichimokuTenkanSeries?.drop(startIdx).orEmpty()
    val kijunSlice = analysis?.ichimokuKijunSeries?.drop(startIdx).orEmpty()
    val spanASlice = analysis?.ichimokuSpanASeries?.drop(startIdx).orEmpty()
    val spanBSlice = analysis?.ichimokuSpanBSeries?.drop(startIdx).orEmpty()
    val pivots = analysis?.pivotLevels

    val priceCandidates = buildList {
        displayCandles.forEach { add(it.high); add(it.low) }
        if (ChartOverlayIndicator.BOLLINGER in enabledOverlays) {
            bbUpperSlice.forEach { add(it) }
            bbLowerSlice.forEach { add(it) }
        }
        if (ChartOverlayIndicator.SMA_50 in enabledOverlays) {
            sma50Slice.forEach { add(it) }
        }
        if (ChartOverlayIndicator.ICHIMOKU in enabledOverlays) {
            spanASlice.forEach { add(it) }
            spanBSlice.forEach { add(it) }
        }
        if (ChartOverlayIndicator.PIVOTS in enabledOverlays && pivots != null) {
            add(pivots.r1); add(pivots.s1)
        }
        if (entryLine != null) add(entryLine)
        if (slLine != null) add(slLine)
        if (tpLine != null) add(tpLine)
    }
    val rawMin = priceCandidates.minOrNull() ?: 0.0
    val rawMax = priceCandidates.maxOrNull() ?: 1.0
    val pad = ((rawMax - rawMin) * 0.08).coerceAtLeast(pair.pipSize * 4.0)
    val minPrice = rawMin - pad
    val maxPrice = rawMax + pad
    val priceRange = (maxPrice - minPrice).coerceAtLeast(pair.pipSize)

    fun mapOffsetToAnchor(offset: Offset): ChartAnchor? {
        if (displayCandles.isEmpty() || canvasSize.width <= 0 || canvasSize.height <= 0) return null
        val plotW = (canvasSize.width - padStartPx * 2f - rightAxisWidthPx).coerceAtLeast(40f)
        val plotH = (canvasSize.height - padPx * 2f).coerceAtLeast(40f)
        val localX = (offset.x - padStartPx).coerceIn(0f, plotW)
        val localY = (offset.y - padPx).coerceIn(0f, plotH)
        val stepX = plotW / displayCandles.size.coerceAtLeast(1)
        val idx = (localX / stepX).toInt().coerceIn(0, displayCandles.lastIndex)
        val normY = 1.0 - (localY / plotH).toDouble()
        val price = pair.roundToTick(minPrice + normY * priceRange)
        return ChartAnchor(
            candleTimestampMillis = displayCandles[idx].timestampMillis,
            fallbackIndex = idx,
            price = price
        )
    }

    fun buildTwoPointDrawing(start: ChartAnchor, end: ChartAnchor, tool: ChartMarkingTool): ChartDrawing {
        val id = "${tool.name}_${System.nanoTime()}"
        return when (tool) {
            ChartMarkingTool.TREND_LINE -> ChartDrawing.TrendLine(id, start, end)
            ChartMarkingTool.FIB_RETRACEMENT -> ChartDrawing.FibonacciRetracement(id, start, end)
            ChartMarkingTool.RECTANGLE_ZONE -> ChartDrawing.ZoneBox(id, start, end)
            ChartMarkingTool.RR_PROJECTION -> ChartDrawing.RiskRewardBox(id, start, end)
            else -> ChartDrawing.TrendLine(id, start, end)
        }
    }

    val allOverlaysActive = enabledOverlays.size == ChartOverlayIndicator.entries.size

    Column(modifier = modifier.fillMaxWidth()) {
        // 1. Price Overlay Indicators Selector Strip
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterChip(
                selected = allOverlaysActive,
                onClick = { onSetAllOverlays(!allOverlaysActive) },
                label = { Text(if (allOverlaysActive) "All On" else "All Overlays", style = MaterialTheme.typography.labelSmall) },
                modifier = Modifier.testTag("overlay_chip_all")
            )
            ChartOverlayIndicator.entries.forEach { indicator ->
                val isSelected = indicator in enabledOverlays
                FilterChip(
                    selected = isSelected,
                    onClick = { onToggleOverlay(indicator) },
                    label = { Text(indicator.label, style = MaterialTheme.typography.labelSmall) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = CyanContainer,
                        selectedLabelColor = CyanOnContainer
                    ),
                    modifier = Modifier.testTag("overlay_chip_${indicator.name.lowercase()}")
                )
            }
        }

        // 2. Marking & Drawing Tools Strip
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ChartMarkingTool.entries.forEach { tool ->
                    val toolIcon = when (tool) {
                        ChartMarkingTool.BROWSE -> Icons.Filled.OpenWith
                        ChartMarkingTool.CROSSHAIR -> Icons.Filled.CenterFocusStrong
                        ChartMarkingTool.HORIZONTAL_LINE -> Icons.Filled.HorizontalRule
                        ChartMarkingTool.TREND_LINE -> Icons.Filled.ShowChart
                        ChartMarkingTool.FIB_RETRACEMENT -> Icons.Filled.LinearScale
                        ChartMarkingTool.RECTANGLE_ZONE -> Icons.Filled.CropSquare
                        ChartMarkingTool.RR_PROJECTION -> Icons.Filled.Straighten
                        ChartMarkingTool.PRICE_PIN -> Icons.Filled.AddLocationAlt
                    }
                    FilterChip(
                        selected = activeMarkingTool == tool,
                        onClick = {
                            onSelectMarkingTool(tool)
                            if (tool != ChartMarkingTool.CROSSHAIR) crosshairOffset = null
                        },
                        leadingIcon = {
                            Icon(toolIcon, contentDescription = null, modifier = Modifier.size(14.dp))
                        },
                        label = { Text(tool.label, style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.testTag("marking_tool_${tool.name.lowercase()}")
                    )
                }
            }

            if (drawings.isNotEmpty()) {
                IconButton(
                    onClick = onUndoDrawing,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("marking_undo_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Undo,
                        contentDescription = "Undo last drawing",
                        tint = CyanIndicator,
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(
                    onClick = onClearDrawings,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("marking_clear_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.DeleteSweep,
                        contentDescription = "Clear all drawings",
                        tint = CrimsonBear,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // Active tool hint or Crosshair OHLC HUD
        val hoveredAnchor = crosshairOffset?.let { mapOffsetToAnchor(it) }
        val hoveredCandle = hoveredAnchor?.let { displayCandles.getOrNull(it.fallbackIndex) }
        if (hoveredCandle != null && hoveredAnchor != null) {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .testTag("crosshair_ohlc_hud")
            ) {
                Text(
                    text = "O ${pair.formatPrice(hoveredCandle.open)}  H ${pair.formatPrice(hoveredCandle.high)}  L ${pair.formatPrice(hoveredCandle.low)}  C ${pair.formatPrice(hoveredCandle.close)}  |  Cursor ${pair.formatPrice(hoveredAnchor.price)}",
                    style = TabularNumericStyle.copy(fontSize = 10.sp),
                    color = CyanIndicator,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        } else if (activeMarkingTool != ChartMarkingTool.BROWSE) {
            val hint = when {
                pendingStart != null -> "Tap or drag second point to complete ${activeMarkingTool.label}"
                activeMarkingTool.isTwoPointTool -> "Drag or tap 2 points on chart to place ${activeMarkingTool.label}"
                activeMarkingTool == ChartMarkingTool.CROSSHAIR -> "Tap or drag across chart to inspect OHLC & prices"
                else -> "Tap chart to place ${activeMarkingTool.label} (${drawings.size} marked)"
            }
            Text(
                text = hint,
                style = MaterialTheme.typography.labelSmall,
                color = AmberWarning,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 3.dp)
            )
        }

        // Legend strip
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 3.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.horizontalScroll(rememberScrollState())
            ) {
                if (ChartOverlayIndicator.EMA in enabledOverlays) {
                    LegendDot(color = CyanIndicator, label = "EMA")
                    Spacer(modifier = Modifier.width(8.dp))
                }
                if (ChartOverlayIndicator.SMA_50 in enabledOverlays) {
                    LegendDot(color = VioletIndicator, label = "SMA50")
                    Spacer(modifier = Modifier.width(8.dp))
                }
                if (ChartOverlayIndicator.BOLLINGER in enabledOverlays) {
                    LegendDot(color = CyanIndicator.copy(alpha = 0.55f), label = "BB")
                    Spacer(modifier = Modifier.width(8.dp))
                }
                if (ChartOverlayIndicator.ICHIMOKU in enabledOverlays) {
                    LegendDot(color = EmeraldBull.copy(alpha = 0.7f), label = "Kumo")
                    Spacer(modifier = Modifier.width(8.dp))
                }
                if (ChartOverlayIndicator.PSAR in enabledOverlays) {
                    LegendDot(color = RoseIndicator, label = "PSAR")
                    Spacer(modifier = Modifier.width(8.dp))
                }
                if (ChartOverlayIndicator.PIVOTS in enabledOverlays) {
                    LegendDot(color = AmberWarning, label = "Pivots")
                }
            }
            if (activeTrade != null) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = "LIVE ${activeTrade.side}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }

        // 3. Interactive Candlestick + Overlays + Markings Canvas
        val dragModifier = if (activeMarkingTool != ChartMarkingTool.BROWSE) {
            Modifier.pointerInput(activeMarkingTool, displayCandles.size, minPrice, maxPrice) {
                detectDragGestures(
                    onDragStart = { offset ->
                        if (activeMarkingTool == ChartMarkingTool.CROSSHAIR) {
                            crosshairOffset = offset
                        } else if (activeMarkingTool.isTwoPointTool) {
                            val anchor = mapOffsetToAnchor(offset)
                            if (anchor != null) {
                                pendingStart = anchor
                                livePreviewEnd = anchor
                            }
                        }
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        if (activeMarkingTool == ChartMarkingTool.CROSSHAIR) {
                            crosshairOffset = change.position
                        } else if (activeMarkingTool.isTwoPointTool && pendingStart != null) {
                            livePreviewEnd = mapOffsetToAnchor(change.position)
                        }
                    },
                    onDragEnd = {
                        val start = pendingStart
                        val end = livePreviewEnd
                        if (activeMarkingTool.isTwoPointTool && start != null && end != null) {
                            onAddDrawing(buildTwoPointDrawing(start, end, activeMarkingTool))
                            pendingStart = null
                            livePreviewEnd = null
                        }
                    },
                    onDragCancel = {
                        livePreviewEnd = null
                    }
                )
            }
        } else {
            Modifier
        }

        val tapModifier = Modifier.pointerInput(activeMarkingTool, displayCandles.size, minPrice, maxPrice) {
            detectTapGestures { offset ->
                val anchor = mapOffsetToAnchor(offset) ?: return@detectTapGestures
                when (activeMarkingTool) {
                    ChartMarkingTool.BROWSE, ChartMarkingTool.CROSSHAIR -> {
                        crosshairOffset = if (crosshairOffset != null && activeMarkingTool == ChartMarkingTool.BROWSE) null else offset
                    }
                    ChartMarkingTool.HORIZONTAL_LINE -> {
                        onAddDrawing(
                            ChartDrawing.HorizontalLine(
                                id = "HL_${System.nanoTime()}",
                                price = anchor.price
                            )
                        )
                    }
                    ChartMarkingTool.PRICE_PIN -> {
                        val candle = displayCandles.getOrNull(anchor.fallbackIndex)
                        val isBull = candle == null || anchor.price <= candle.close
                        onAddDrawing(
                            ChartDrawing.PricePin(
                                id = "PIN_${System.nanoTime()}",
                                anchor = anchor,
                                isBullish = isBull
                            )
                        )
                    }
                    ChartMarkingTool.TREND_LINE,
                    ChartMarkingTool.FIB_RETRACEMENT,
                    ChartMarkingTool.RECTANGLE_ZONE,
                    ChartMarkingTool.RR_PROJECTION -> {
                        val existingStart = pendingStart
                        if (existingStart == null) {
                            pendingStart = anchor
                            livePreviewEnd = null
                        } else {
                            onAddDrawing(buildTwoPointDrawing(existingStart, anchor, activeMarkingTool))
                            pendingStart = null
                            livePreviewEnd = null
                        }
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(250.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.background.copy(alpha = 0.65f))
                .onSizeChanged { canvasSize = it }
                .then(dragModifier)
                .then(tapModifier)
                .testTag("candlestick_chart_canvas")
        ) {
            if (displayCandles.isEmpty()) {
                Text(
                    text = "Syncing market candles...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 8.dp, bottom = 8.dp, start = 6.dp, end = 6.dp)
                ) {
                    val plotWidth = (size.width - rightAxisWidthPx).coerceAtLeast(40f)
                    val plotHeight = size.height

                    fun yFor(price: Double): Float {
                        val norm = ((price - minPrice) / priceRange).toFloat()
                        return plotHeight - (norm * plotHeight)
                    }

                    // Horizontal grid lines + price axis
                    val gridSteps = 4
                    val axisStyle = TextStyle(
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 9.sp,
                        color = labelTextColor
                    )
                    for (step in 0..gridSteps) {
                        val frac = step.toFloat() / gridSteps
                        val y = plotHeight * frac
                        val priceAtY = maxPrice - frac * priceRange
                        drawLine(
                            color = gridColor,
                            start = Offset(0f, y),
                            end = Offset(plotWidth, y),
                            strokeWidth = 1f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
                        )
                        val measured = textMeasurer.measure(pair.formatPrice(priceAtY), axisStyle)
                        drawText(
                            textLayoutResult = measured,
                            topLeft = Offset(
                                plotWidth + 6.dp.toPx(),
                                (y - measured.size.height / 2f).coerceIn(0f, plotHeight - measured.size.height)
                            )
                        )
                    }

                    val stepX = plotWidth / displayCandles.size.coerceAtLeast(1)
                    val bodyWidth = (stepX * 0.62f).coerceIn(3f, 14f)

                    fun drawSeriesLine(series: List<Double>, color: Color, width: Float = 2f) {
                        if (series.size != displayCandles.size) return
                        val path = Path()
                        series.forEachIndexed { idx, value ->
                            val x = idx * stepX + stepX / 2f
                            val y = yFor(value)
                            if (idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
                        }
                        drawPath(path, color = color, style = Stroke(width = width, cap = StrokeCap.Round))
                    }

                    // Overlay: Ichimoku Cloud
                    if (ChartOverlayIndicator.ICHIMOKU in enabledOverlays &&
                        spanASlice.size == displayCandles.size && spanBSlice.size == displayCandles.size
                    ) {
                        for (i in 0 until displayCandles.lastIndex) {
                            val x1 = i * stepX + stepX / 2f
                            val x2 = (i + 1) * stepX + stepX / 2f
                            val a1 = yFor(spanASlice[i])
                            val a2 = yFor(spanASlice[i + 1])
                            val b1 = yFor(spanBSlice[i])
                            val b2 = yFor(spanBSlice[i + 1])
                            val cloudColor = if (spanASlice[i] >= spanBSlice[i]) {
                                EmeraldBull.copy(alpha = 0.10f)
                            } else {
                                CrimsonBear.copy(alpha = 0.10f)
                            }
                            val segPath = Path().apply {
                                moveTo(x1, a1); lineTo(x2, a2); lineTo(x2, b2); lineTo(x1, b1); close()
                            }
                            drawPath(segPath, color = cloudColor)
                        }
                        drawSeriesLine(tenkanSlice, CyanIndicator.copy(alpha = 0.75f), 1.5f)
                        drawSeriesLine(kijunSlice, RoseIndicator.copy(alpha = 0.75f), 1.5f)
                    }

                    // Overlay: Bollinger Bands
                    if (ChartOverlayIndicator.BOLLINGER in enabledOverlays &&
                        bbUpperSlice.size == displayCandles.size && bbLowerSlice.size == displayCandles.size
                    ) {
                        val upperPath = Path()
                        val lowerPath = Path()
                        val bandFill = Path()
                        for (i in displayCandles.indices) {
                            val x = i * stepX + stepX / 2f
                            val yU = yFor(bbUpperSlice[i])
                            val yL = yFor(bbLowerSlice[i])
                            if (i == 0) {
                                upperPath.moveTo(x, yU)
                                lowerPath.moveTo(x, yL)
                                bandFill.moveTo(x, yU)
                            } else {
                                upperPath.lineTo(x, yU)
                                lowerPath.lineTo(x, yL)
                                bandFill.lineTo(x, yU)
                            }
                        }
                        for (i in displayCandles.indices.reversed()) {
                            val x = i * stepX + stepX / 2f
                            bandFill.lineTo(x, yFor(bbLowerSlice[i]))
                        }
                        bandFill.close()
                        drawPath(bandFill, color = CyanIndicator.copy(alpha = 0.06f))
                        drawPath(upperPath, color = CyanIndicator.copy(alpha = 0.32f), style = Stroke(width = 1.2f))
                        drawPath(lowerPath, color = CyanIndicator.copy(alpha = 0.32f), style = Stroke(width = 1.2f))
                    }

                    // Overlay: Classic Pivot Levels
                    if (ChartOverlayIndicator.PIVOTS in enabledOverlays && pivots != null) {
                        val pivotStyle = TextStyle(fontFamily = JetBrainsMonoFamily, fontSize = 7.5.sp, color = AmberWarning)
                        listOf("R1" to pivots.r1, "P" to pivots.pivot, "S1" to pivots.s1).forEach { (tag, lvl) ->
                            if (lvl in minPrice..maxPrice) {
                                val y = yFor(lvl)
                                drawLine(
                                    color = AmberWarning.copy(alpha = 0.45f),
                                    start = Offset(0f, y),
                                    end = Offset(plotWidth, y),
                                    strokeWidth = 1f,
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f, 4f))
                                )
                                val m = textMeasurer.measure(tag, pivotStyle)
                                drawText(m, topLeft = Offset(3.dp.toPx(), (y - m.size.height).coerceAtLeast(0f)))
                            }
                        }
                    }

                    // Candlesticks
                    displayCandles.forEachIndexed { idx, candle ->
                        val centerX = idx * stepX + stepX / 2f
                        val isBull = candle.close >= candle.open
                        val candleColor = if (isBull) EmeraldBull else CrimsonBear

                        drawLine(
                            color = candleColor.copy(alpha = 0.85f),
                            start = Offset(centerX, yFor(candle.high)),
                            end = Offset(centerX, yFor(candle.low)),
                            strokeWidth = 1.6f,
                            cap = StrokeCap.Round
                        )
                        val openY = yFor(candle.open)
                        val closeY = yFor(candle.close)
                        drawRect(
                            color = candleColor,
                            topLeft = Offset(centerX - bodyWidth / 2f, min(openY, closeY)),
                            size = Size(bodyWidth, max(abs(closeY - openY), 2.5f))
                        )
                    }

                    // Overlay: EMA Fast & Slow
                    if (ChartOverlayIndicator.EMA in enabledOverlays) {
                        drawSeriesLine(emaFastSlice, CyanIndicator, 2.2f)
                        drawSeriesLine(emaSlowSlice, AmberWarning, 2.0f)
                    }

                    // Overlay: SMA 50
                    if (ChartOverlayIndicator.SMA_50 in enabledOverlays) {
                        drawSeriesLine(sma50Slice, VioletIndicator, 2.0f)
                    }

                    // Overlay: Parabolic SAR Dots
                    if (ChartOverlayIndicator.PSAR in enabledOverlays && psarSlice.size == displayCandles.size) {
                        psarSlice.forEachIndexed { idx, sarVal ->
                            val x = idx * stepX + stepX / 2f
                            val y = yFor(sarVal).coerceIn(2f, plotHeight - 2f)
                            val candle = displayCandles[idx]
                            val dotColor = if (sarVal <= candle.close) EmeraldBull else RoseIndicator
                            drawCircle(color = dotColor, radius = 2.2.dp.toPx(), center = Offset(x, y))
                        }
                    }

                    // Exact Target Price Lines: TP, Entry, SL
                    fun drawTargetLine(price: Double?, tag: String, color: Color) {
                        if (price == null) return
                        val y = yFor(price).coerceIn(4f, plotHeight - 4f)
                        drawLine(
                            color = color,
                            start = Offset(0f, y),
                            end = Offset(plotWidth, y),
                            strokeWidth = 2f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f))
                        )
                        drawAxisPricePill(
                            text = "$tag ${pair.formatPrice(price)}",
                            y = y,
                            color = color,
                            plotWidth = plotWidth,
                            plotHeight = plotHeight,
                            rightAxisWidth = rightAxisWidthPx,
                            textMeasurer = textMeasurer
                        )
                    }

                    drawTargetLine(tpLine, "TP", EmeraldBull)
                    drawTargetLine(entryLine, "IN", CyanIndicator)
                    drawTargetLine(slLine, "SL", CrimsonBear)

                    // User-placed Chart Marking Drawings + live preview
                    drawChartDrawings(
                        drawings = drawings,
                        pendingStart = pendingStart,
                        livePreviewEnd = livePreviewEnd,
                        activeTool = activeMarkingTool,
                        displayCandles = displayCandles,
                        pair = pair,
                        stepX = stepX,
                        plotWidth = plotWidth,
                        plotHeight = plotHeight,
                        rightAxisWidth = rightAxisWidthPx,
                        yFor = ::yFor,
                        textMeasurer = textMeasurer
                    )

                    // Interactive Crosshair Lines
                    val cOff = crosshairOffset
                    val cAnchor = cOff?.let { mapOffsetToAnchor(it) }
                    if (cOff != null && cAnchor != null) {
                        val snapX = (cAnchor.fallbackIndex * stepX + stepX / 2f).coerceIn(0f, plotWidth)
                        val snapY = (cOff.y - padPx).coerceIn(0f, plotHeight)
                        drawLine(
                            color = Color.White.copy(alpha = 0.45f),
                            start = Offset(snapX, 0f),
                            end = Offset(snapX, plotHeight),
                            strokeWidth = 1f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f))
                        )
                        drawLine(
                            color = Color.White.copy(alpha = 0.45f),
                            start = Offset(0f, snapY),
                            end = Offset(plotWidth, snapY),
                            strokeWidth = 1f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f))
                        )
                        drawAxisPricePill(
                            text = pair.formatPrice(cAnchor.price),
                            y = snapY,
                            color = Color.White,
                            plotWidth = plotWidth,
                            plotHeight = plotHeight,
                            rightAxisWidth = rightAxisWidthPx,
                            textMeasurer = textMeasurer
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 4. Selectable Lower Oscillator Sub-Chart (RSI / MACD / Stochastic / ADX / ATR)
        if (analysis != null && displayCandles.isNotEmpty()) {
            OscillatorSubChart(
                pair = pair,
                visibleCount = visibleCount,
                candleCount = displayCandles.size,
                analysis = analysis,
                selectedOscillator = selectedOscillator,
                onSelectOscillator = onSelectOscillator
            )
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
