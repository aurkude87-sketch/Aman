package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ChartDrawing
import com.example.model.ChartMarkingTool
import com.example.model.ChartOverlayIndicator
import com.example.model.FeedStatus
import com.example.model.ForexPair
import com.example.model.IndicatorVote
import com.example.model.OscillatorPane
import com.example.model.TradeSide
import com.example.ui.TerminalSnapshot
import com.example.ui.components.ForexCandlestickChart
import com.example.ui.theme.AmberContainer
import com.example.ui.theme.AmberOnContainer
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CrimsonBear
import com.example.ui.theme.CrimsonContainer
import com.example.ui.theme.CrimsonOnContainer
import com.example.ui.theme.CyanIndicator
import com.example.ui.theme.EmeraldBull
import com.example.ui.theme.EmeraldContainer
import com.example.ui.theme.EmeraldOnContainer
import com.example.ui.theme.TabularNumericStyle
import java.util.Locale
import kotlin.math.abs

@Composable
fun TerminalScreen(
    snapshot: TerminalSnapshot,
    isBusy: Boolean,
    enabledOverlays: Set<ChartOverlayIndicator> = setOf(ChartOverlayIndicator.EMA, ChartOverlayIndicator.BOLLINGER),
    selectedOscillator: OscillatorPane = OscillatorPane.RSI,
    activeMarkingTool: ChartMarkingTool = ChartMarkingTool.BROWSE,
    drawings: List<ChartDrawing> = emptyList(),
    onSelectPair: (ForexPair) -> Unit,
    onToggleBotArmed: (Boolean) -> Unit,
    onExecuteSetup: (TradeSide?) -> Unit,
    onResetCircuitBreaker: () -> Unit,
    onSyncLiveRates: () -> Unit,
    onToggleOverlay: (ChartOverlayIndicator) -> Unit = {},
    onSetAllOverlays: (Boolean) -> Unit = {},
    onSelectOscillator: (OscillatorPane) -> Unit = {},
    onSelectMarkingTool: (ChartMarkingTool) -> Unit = {},
    onAddDrawing: (ChartDrawing) -> Unit = {},
    onUndoDrawing: () -> Unit = {},
    onClearDrawings: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val selectedPair = snapshot.selectedPair
    val selectedQuote = snapshot.quotes[selectedPair]
    val selectedAnalysis = snapshot.selectedAnalysis
    val activeTradeOnSelectedPair = snapshot.openTrades
        .firstOrNull { it.trade.pair == selectedPair.symbol }
        ?.trade

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Institutional Portfolio & Capital Protection Header Card
        item {
            CapitalStatusCard(
                snapshot = snapshot,
                onToggleBotArmed = onToggleBotArmed,
                onResetCircuitBreaker = onResetCircuitBreaker,
                onSyncLiveRates = onSyncLiveRates
            )
        }

        // 2. Global Markets Pair Strip
        item {
            val watchedEnums = snapshot.config.watchedPairs
                .map { ForexPair.fromSymbol(it) }
                .distinct()
                .ifEmpty { ForexPair.entries }

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(vertical = 2.dp)
            ) {
                items(watchedEnums, key = { it.symbol }) { pair ->
                    val quote = snapshot.quotes[pair]
                    val analysis = snapshot.analyses[pair]
                    val isSelected = pair == selectedPair
                    val hasOpen = snapshot.openTrades.any { it.trade.pair == pair.symbol }

                    MarketPairChip(
                        pair = pair,
                        midPrice = quote?.let { pair.formatPrice(it.mid) } ?: "—",
                        changePct = quote?.changePercent ?: 0.0,
                        suggestedSide = analysis?.suggestedSide,
                        confluence = analysis?.confluenceScore ?: 0,
                        isSelected = isSelected,
                        hasOpenPosition = hasOpen,
                        onClick = { onSelectPair(pair) }
                    )
                }
            }
        }

        // 3. Candlestick Chart + Indicators + Entry/SL/TP Lines
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = selectedPair.symbol,
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant
                                ) {
                                    Text(
                                        text = "M15 • ${selectedPair.sessionLabel}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            if (selectedQuote != null) {
                                Text(
                                    text = "BID ${selectedPair.formatPrice(selectedQuote.bid)}  •  ASK ${selectedPair.formatPrice(selectedQuote.ask)}",
                                    style = TabularNumericStyle.copy(fontSize = 12.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        if (selectedQuote != null) {
                            val isUp = selectedQuote.changePercent >= 0
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = selectedPair.formatPrice(selectedQuote.mid),
                                    style = TabularNumericStyle.copy(
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = if (isUp) EmeraldBull else CrimsonBear
                                )
                                Text(
                                    text = "${if (isUp) "+" else ""}${"%.2f".format(Locale.US, selectedQuote.changePercent)}%",
                                    style = TabularNumericStyle.copy(fontSize = 11.sp),
                                    color = if (isUp) EmeraldBull else CrimsonBear
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    ForexCandlestickChart(
                        pair = selectedPair,
                        candles = selectedQuote?.candles.orEmpty(),
                        analysis = selectedAnalysis,
                        activeTrade = activeTradeOnSelectedPair,
                        enabledOverlays = enabledOverlays,
                        selectedOscillator = selectedOscillator,
                        activeMarkingTool = activeMarkingTool,
                        drawings = drawings,
                        onToggleOverlay = onToggleOverlay,
                        onSetAllOverlays = onSetAllOverlays,
                        onSelectOscillator = onSelectOscillator,
                        onSelectMarkingTool = onSelectMarkingTool,
                        onAddDrawing = onAddDrawing,
                        onUndoDrawing = onUndoDrawing,
                        onClearDrawings = onClearDrawings
                    )
                }
            }
        }

        // 4. Quantitative Indicators & Exact Entry / Exit Blueprint
        if (selectedAnalysis != null) {
            item {
                TechnicalSignalAndExecutionCard(
                    snapshot = snapshot,
                    isBusy = isBusy,
                    onExecuteSetup = onExecuteSetup
                )
            }
        }

        item { Spacer(modifier = Modifier.height(12.dp)) }
    }
}

@Composable
private fun CapitalStatusCard(
    snapshot: TerminalSnapshot,
    onToggleBotArmed: (Boolean) -> Unit,
    onResetCircuitBreaker: () -> Unit,
    onSyncLiveRates: () -> Unit
) {
    val account = snapshot.account
    val maxDd = snapshot.config.maxDailyDrawdownPercent.coerceAtLeast(1.0)
    val ddRatio = (snapshot.dailyDrawdownPercent / maxDd).toFloat().coerceIn(0f, 1f)
    val isPnlPositive = snapshot.totalUnrealizedPnlUsd >= 0.0

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            width = 1.dp,
            color = when {
                account.circuitBreakerTripped -> CrimsonBear.copy(alpha = 0.7f)
                account.botArmed -> EmeraldBull.copy(alpha = 0.45f)
                else -> MaterialTheme.colorScheme.outlineVariant
            }
        ),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    account.circuitBreakerTripped -> CrimsonBear
                                    account.botArmed -> EmeraldBull
                                    else -> AmberWarning
                                }
                            )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = when {
                            account.circuitBreakerTripped -> "CIRCUIT BREAKER TRIPPED"
                            account.botArmed -> "AUTO-BOT ARMED"
                            else -> "BOT ON STANDBY"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = when {
                            account.circuitBreakerTripped -> CrimsonBear
                            account.botArmed -> EmeraldBull
                            else -> AmberWarning
                        }
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.clickable { onSyncLiveRates() }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = "Sync Live FX Rates",
                                tint = CyanIndicator,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = when (snapshot.feedStatus) {
                                    FeedStatus.LIVE_ECB_SYNCED -> "LIVE ECB"
                                    FeedStatus.SYNCING -> "SYNCING"
                                    FeedStatus.CACHED_OFFLINE -> "CACHED"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = if (snapshot.feedStatus == FeedStatus.CACHED_OFFLINE) AmberWarning else CyanIndicator
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Switch(
                        checked = account.botArmed,
                        onCheckedChange = onToggleBotArmed,
                        enabled = !account.circuitBreakerTripped,
                        modifier = Modifier.testTag("bot_armed_switch"),
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                            checkedTrackColor = EmeraldBull
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column {
                    Text(
                        text = "Account Equity (Paper)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "$${"%,.2f".format(Locale.US, snapshot.totalEquityUsd)}",
                        style = TabularNumericStyle.copy(
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Floating P&L",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    val sign = if (isPnlPositive) "+" else "-"
                    Text(
                        text = "$sign$${"%,.2f".format(Locale.US, abs(snapshot.totalUnrealizedPnlUsd))}",
                        style = TabularNumericStyle.copy(
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = if (isPnlPositive) EmeraldBull else CrimsonBear
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Daily Drawdown Capital Protection Meter
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Shield,
                        contentDescription = null,
                        tint = CyanIndicator,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Daily Drawdown Guard",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = "${"%.2f".format(Locale.US, snapshot.dailyDrawdownPercent)}% / ${"%.1f".format(Locale.US, maxDd)}% Max",
                    style = TabularNumericStyle.copy(fontSize = 11.sp),
                    color = if (ddRatio >= 0.75f) CrimsonBear else MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { ddRatio },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = when {
                    ddRatio >= 0.8f -> CrimsonBear
                    ddRatio >= 0.5f -> AmberWarning
                    else -> EmeraldBull
                },
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            AnimatedVisibility(visible = account.circuitBreakerTripped) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    OutlinedButton(
                        onClick = onResetCircuitBreaker,
                        border = BorderStroke(1.dp, CrimsonBear),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = CrimsonBear),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("unlock_circuit_breaker_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.LockOpen,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Unlock Circuit Breaker & Reset Daily Baseline")
                    }
                }
            }
        }
    }
}

@Composable
private fun MarketPairChip(
    pair: ForexPair,
    midPrice: String,
    changePct: Double,
    suggestedSide: TradeSide?,
    confluence: Int,
    isSelected: Boolean,
    hasOpenPosition: Boolean,
    onClick: () -> Unit
) {
    val bgColor = if (isSelected) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface
    val borderColor = when {
        isSelected -> CyanIndicator
        hasOpenPosition -> EmeraldBull.copy(alpha = 0.55f)
        else -> MaterialTheme.colorScheme.outlineVariant
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = bgColor),
        border = BorderStroke(if (isSelected) 1.5.dp else 1.dp, borderColor),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .width(136.dp)
            .clickable(onClick = onClick)
            .testTag("pair_chip_${pair.symbol}")
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = pair.symbol,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (suggestedSide != null) {
                    val isBuy = suggestedSide == TradeSide.BUY
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = if (isBuy) EmeraldContainer else CrimsonContainer
                    ) {
                        Text(
                            text = "${if (isBuy) "B" else "S"} $confluence/4",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isBuy) EmeraldOnContainer else CrimsonOnContainer,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = midPrice,
                style = TabularNumericStyle.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
            val isUp = changePct >= 0
            Text(
                text = "${if (isUp) "+" else ""}${"%.2f".format(Locale.US, changePct)}%",
                style = TabularNumericStyle.copy(fontSize = 11.sp),
                color = if (isUp) EmeraldBull else CrimsonBear
            )
        }
    }
}

@Composable
private fun TechnicalSignalAndExecutionCard(
    snapshot: TerminalSnapshot,
    isBusy: Boolean,
    onExecuteSetup: (TradeSide?) -> Unit
) {
    val analysis = snapshot.selectedAnalysis ?: return
    val riskCheck = snapshot.selectedPreTradeCheck
    val pair = analysis.pair

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Confluence Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Technical Confluence",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "ATR(14): ${"%.1f".format(Locale.US, analysis.atrPips)} pips • Min Gate: ${snapshot.config.minConfluenceScore}/4",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                val sideBadgeColor = when (analysis.suggestedSide) {
                    TradeSide.BUY -> EmeraldContainer
                    TradeSide.SELL -> CrimsonContainer
                    null -> AmberContainer
                }
                val sideTextColor = when (analysis.suggestedSide) {
                    TradeSide.BUY -> EmeraldOnContainer
                    TradeSide.SELL -> CrimsonOnContainer
                    null -> AmberOnContainer
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = sideBadgeColor
                ) {
                    Text(
                        text = when (analysis.suggestedSide) {
                            TradeSide.BUY -> "BUY ${analysis.confluenceScore}/4"
                            TradeSide.SELL -> "SELL ${analysis.confluenceScore}/4"
                            null -> "NEUTRAL"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = sideTextColor,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 2x2 Indicator Readings Grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IndicatorTile(
                    name = "EMA ${snapshot.config.emaFastPeriod}/${snapshot.config.emaSlowPeriod}",
                    reading = "${pair.formatPrice(analysis.latestEmaFast)} / ${pair.formatPrice(analysis.latestEmaSlow)}",
                    vote = analysis.emaVote,
                    modifier = Modifier.weight(1f)
                )
                IndicatorTile(
                    name = "RSI (${snapshot.config.rsiPeriod})",
                    reading = "%.1f".format(Locale.US, analysis.latestRsi),
                    vote = analysis.rsiVote,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IndicatorTile(
                    name = "MACD (12,26,9)",
                    reading = "${if (analysis.macdHistogram >= 0) "+" else ""}${"%.5f".format(Locale.US, analysis.macdHistogram)}",
                    vote = analysis.macdVote,
                    modifier = Modifier.weight(1f)
                )
                IndicatorTile(
                    name = "Bollinger %B",
                    reading = "${"%.0f".format(Locale.US, analysis.bbPercentB * 100)}%",
                    vote = analysis.bbVote,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val spotClose = snapshot.quotes[pair]?.mid ?: analysis.exactEntryPrice
                val smaVote = when {
                    spotClose > analysis.latestSma50 -> IndicatorVote.BULLISH
                    spotClose < analysis.latestSma50 -> IndicatorVote.BEARISH
                    else -> IndicatorVote.NEUTRAL
                }
                val stochVote = when {
                    analysis.latestStochK < 20.0 -> IndicatorVote.BULLISH
                    analysis.latestStochK > 80.0 -> IndicatorVote.BEARISH
                    analysis.latestStochK > analysis.latestStochD -> IndicatorVote.BULLISH
                    analysis.latestStochK < analysis.latestStochD -> IndicatorVote.BEARISH
                    else -> IndicatorVote.NEUTRAL
                }
                IndicatorTile(
                    name = "SMA(50)",
                    reading = pair.formatPrice(analysis.latestSma50),
                    vote = smaVote,
                    modifier = Modifier.weight(1f)
                )
                IndicatorTile(
                    name = "Stoch (14,3,3)",
                    reading = "${"%.0f".format(Locale.US, analysis.latestStochK)} / ${"%.0f".format(Locale.US, analysis.latestStochD)}",
                    vote = stochVote,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val adxVote = when {
                    analysis.latestAdx < 20.0 -> IndicatorVote.NEUTRAL
                    analysis.latestDiPlus > analysis.latestDiMinus -> IndicatorVote.BULLISH
                    else -> IndicatorVote.BEARISH
                }
                val spotClose = snapshot.quotes[pair]?.mid ?: analysis.exactEntryPrice
                val psarVote = if (analysis.latestPsar <= spotClose) IndicatorVote.BULLISH else IndicatorVote.BEARISH
                IndicatorTile(
                    name = "ADX(14) Trend",
                    reading = "${"%.1f".format(Locale.US, analysis.latestAdx)} (${if (analysis.latestAdx >= 25.0) "Strong" else "Weak"})",
                    vote = adxVote,
                    modifier = Modifier.weight(1f)
                )
                IndicatorTile(
                    name = "Parabolic SAR",
                    reading = pair.formatPrice(analysis.latestPsar),
                    vote = psarVote,
                    modifier = Modifier.weight(1f)
                )
            }

            analysis.pivotLevels?.let { piv ->
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "S1 ${pair.formatPrice(piv.s1)}",
                            style = TabularNumericStyle.copy(fontSize = 11.sp),
                            color = EmeraldBull
                        )
                        Text(
                            text = "Pivot ${pair.formatPrice(piv.pivot)}",
                            style = TabularNumericStyle.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold),
                            color = AmberWarning
                        )
                        Text(
                            text = "R1 ${pair.formatPrice(piv.r1)}",
                            style = TabularNumericStyle.copy(fontSize = 11.sp),
                            color = CrimsonBear
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(modifier = Modifier.height(14.dp))

            // Exact Entry & Exit Target Blueprint
            Text(
                text = "Exact Entry & Exit Blueprint",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PriceLevelBox(
                    label = "EXACT ENTRY",
                    price = pair.formatPrice(analysis.exactEntryPrice),
                    subLabel = analysis.suggestedSide?.name ?: "STANDBY",
                    accentColor = CyanIndicator,
                    modifier = Modifier.weight(1f)
                )
                PriceLevelBox(
                    label = "STOP LOSS",
                    price = pair.formatPrice(analysis.exactStopLoss),
                    subLabel = riskCheck?.let { "-${"%.1f".format(Locale.US, it.stopLossPips)} pips" } ?: "—",
                    accentColor = CrimsonBear,
                    modifier = Modifier.weight(1f)
                )
                PriceLevelBox(
                    label = "TAKE PROFIT",
                    price = pair.formatPrice(analysis.exactTakeProfit),
                    subLabel = riskCheck?.let { "+${"%.1f".format(Locale.US, it.takeProfitPips)} pips" } ?: "—",
                    accentColor = EmeraldBull,
                    modifier = Modifier.weight(1f)
                )
            }

            if (riskCheck != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Size: ${"%.2f".format(Locale.US, riskCheck.calculatedLotSize)} lots",
                            style = TabularNumericStyle.copy(fontSize = 12.sp),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Risk: $${"%.2f".format(Locale.US, riskCheck.riskAmountUsd)}",
                            style = TabularNumericStyle.copy(fontSize = 12.sp),
                            color = CrimsonBear
                        )
                        Text(
                            text = "R:R 1:${"%.1f".format(Locale.US, analysis.riskRewardRatio)}",
                            style = TabularNumericStyle.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold),
                            color = EmeraldBull
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Capital Protection Pre-Trade Gate Status
                val gateApproved = riskCheck.approved
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (gateApproved) EmeraldContainer.copy(alpha = 0.7f) else AmberContainer.copy(alpha = 0.7f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (gateApproved) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                            contentDescription = null,
                            tint = if (gateApproved) EmeraldBull else AmberWarning,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (gateApproved) {
                                "Capital Protection Gate: APPROVED (${analysis.suggestedSide?.name} Setup Ready)"
                            } else {
                                riskCheck.rejectionReason ?: "Gate pending confluence"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (gateApproved) EmeraldOnContainer else AmberOnContainer
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            val canManuallyExecute = !isBusy &&
                !snapshot.account.circuitBreakerTripped &&
                snapshot.openTrades.none { it.trade.pair == pair.symbol } &&
                snapshot.openTrades.size < snapshot.config.maxOpenPositions.toInt()

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = { onExecuteSetup(TradeSide.BUY) },
                    enabled = canManuallyExecute,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("execute_buy_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = EmeraldBull,
                        contentColor = Color(0xFF002110)
                    )
                ) {
                    Icon(Icons.Filled.Bolt, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("BUY", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                }

                Button(
                    onClick = { onExecuteSetup(TradeSide.SELL) },
                    enabled = canManuallyExecute,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("execute_sell_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CrimsonBear,
                        contentColor = Color.White
                    )
                ) {
                    Icon(Icons.Filled.Bolt, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("SELL", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                }
            }
        }
    }
}

@Composable
private fun IndicatorTile(
    name: String,
    reading: String,
    vote: IndicatorVote,
    modifier: Modifier = Modifier
) {
    val voteColor = when (vote) {
        IndicatorVote.BULLISH -> EmeraldBull
        IndicatorVote.BEARISH -> CrimsonBear
        IndicatorVote.NEUTRAL -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        border = BorderStroke(1.dp, voteColor.copy(alpha = 0.28f)),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = when (vote) {
                        IndicatorVote.BULLISH -> "BULL"
                        IndicatorVote.BEARISH -> "BEAR"
                        IndicatorVote.NEUTRAL -> "FLAT"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = voteColor
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = reading,
                style = TabularNumericStyle.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun PriceLevelBox(
    label: String,
    price: String,
    subLabel: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.background.copy(alpha = 0.7f),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.35f)),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = accentColor
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = price,
                style = TabularNumericStyle.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
            Text(
                text = subLabel,
                style = TabularNumericStyle.copy(fontSize = 10.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
