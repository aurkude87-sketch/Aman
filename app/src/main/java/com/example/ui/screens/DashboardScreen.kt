package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.RiskSeverity
import com.example.model.FeedStatus
import com.example.model.ForexPair
import com.example.model.ForexTrade
import com.example.model.PairQuote
import com.example.model.TechnicalAnalysisResult
import com.example.model.TradeSide
import com.example.model.UiState
import com.example.ui.OpenTradeLiveItem
import com.example.ui.TerminalSnapshot
import com.example.ui.TradeHistorySort
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

private enum class DashboardTab(val label: String) {
    MARKETS("Live Markets"),
    TRADE_HISTORY("Trade History")
}

private enum class PairListFilter(val label: String) {
    ALL("All Markets"),
    SIGNALS("Active Signals"),
    WATCHLIST("Watchlist")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    snapshot: TerminalSnapshot,
    tradeHistoryState: UiState<List<ForexTrade>>,
    historySort: TradeHistorySort,
    isRefreshingHistory: Boolean,
    isBusy: Boolean,
    onSelectPairForChart: (ForexPair) -> Unit,
    onToggleBotArmed: (Boolean) -> Unit,
    onResetCircuitBreaker: () -> Unit,
    onSyncLiveRates: () -> Unit,
    onViewAllPositions: () -> Unit,
    onClosePosition: (OpenTradeLiveItem) -> Unit,
    onSelectHistorySort: (TradeHistorySort) -> Unit,
    onRefreshHistory: () -> Unit,
    modifier: Modifier = Modifier
) {
    var activeTab by rememberSaveable { mutableStateOf(DashboardTab.MARKETS) }
    var pairFilter by remember { mutableStateOf(PairListFilter.ALL) }

    BackHandler(enabled = activeTab != DashboardTab.MARKETS) {
        activeTab = DashboardTab.MARKETS
    }

    LaunchedEffect(activeTab) {
        if (activeTab == DashboardTab.TRADE_HISTORY) {
            onRefreshHistory()
        }
    }

    val allPairs = remember(snapshot.config.watchedPairs, snapshot.analyses, pairFilter) {
        val baseList = ForexPair.entries
        when (pairFilter) {
            PairListFilter.ALL -> baseList
            PairListFilter.WATCHLIST -> baseList.filter { it.symbol in snapshot.config.watchedPairs }
                .ifEmpty { baseList }
            PairListFilter.SIGNALS -> baseList.filter { pair ->
                val analysis = snapshot.analyses[pair]
                analysis?.suggestedSide != null &&
                    analysis.confluenceScore >= snapshot.config.minConfluenceScore.toInt()
            }
        }
    }

    val historyCount = (tradeHistoryState as? UiState.Success)?.data?.size ?: snapshot.closedTrades.size

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("dashboard_screen")
    ) {
        PrimaryTabRow(
            selectedTabIndex = activeTab.ordinal,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = CyanIndicator,
            indicator = {
                TabRowDefaults.PrimaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(activeTab.ordinal),
                    color = CyanIndicator,
                    width = 64.dp
                )
            }
        ) {
            Tab(
                selected = activeTab == DashboardTab.MARKETS,
                onClick = { activeTab = DashboardTab.MARKETS },
                modifier = Modifier.testTag("dashboard_tab_markets"),
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.ShowChart,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(DashboardTab.MARKETS.label)
                    }
                },
                selectedContentColor = CyanIndicator,
                unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Tab(
                selected = activeTab == DashboardTab.TRADE_HISTORY,
                onClick = { activeTab = DashboardTab.TRADE_HISTORY },
                modifier = Modifier.testTag("dashboard_tab_trade_history"),
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.History,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            if (historyCount > 0) "${DashboardTab.TRADE_HISTORY.label} ($historyCount)"
                            else DashboardTab.TRADE_HISTORY.label
                        )
                    }
                },
                selectedContentColor = CyanIndicator,
                unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (activeTab == DashboardTab.TRADE_HISTORY) {
            TradeHistoryTab(
                historyState = tradeHistoryState,
                sortOrder = historySort,
                isRefreshing = isRefreshingHistory,
                onSelectSort = onSelectHistorySort,
                onRefreshHistory = onRefreshHistory,
                onSelectPairForChart = onSelectPairForChart,
                modifier = Modifier.weight(1f)
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .testTag("dashboard_markets_tab_content"),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
        // 1. Portfolio Equity, Bot Switch & Drawdown Protection Summary
        item {
            DashboardHeaderCard(
                snapshot = snapshot,
                onToggleBotArmed = onToggleBotArmed,
                onResetCircuitBreaker = onResetCircuitBreaker,
                onSyncLiveRates = onSyncLiveRates
            )
        }

        // 2. Summary of Current Open Positions
        item {
            OpenPositionsSummaryCard(
                snapshot = snapshot,
                isBusy = isBusy,
                onViewAllPositions = onViewAllPositions,
                onSelectPairForChart = onSelectPairForChart,
                onClosePosition = onClosePosition
            )
        }

        // 3. Live Currency Pairs Section Header + Filter Chips
        item {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Live Global Currency Pairs",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        val timeFmt = remember { SimpleDateFormat("HH:mm:ss", Locale.US) }
                        val latestTickMillis = snapshot.quotes.values.maxOfOrNull { it.lastUpdatedMillis } ?: 0L
                        val tickTimeText = if (latestTickMillis > 0L) {
                            "Real-time stream • ${timeFmt.format(Date(latestTickMillis))}"
                        } else {
                            "Connecting to live FX feed..."
                        }
                        Text(
                            text = tickTimeText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = "${allPairs.size} PAIRS",
                            style = MaterialTheme.typography.labelSmall,
                            color = CyanIndicator,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PairListFilter.entries.forEach { option ->
                        FilterChip(
                            selected = pairFilter == option,
                            onClick = { pairFilter = option },
                            label = { Text(option.label) },
                            modifier = Modifier.testTag("dashboard_filter_${option.name.lowercase()}")
                        )
                    }
                }
            }
        }

        // 4. Live Currency Pairs Vertical List with Real-Time Updates
        if (allPairs.isEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "No High-Confluence Signals Right Now",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Switch to All Markets to inspect live quotes and technical readings.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(allPairs, key = { it.symbol }) { pair ->
                val quote = snapshot.quotes[pair]
                val analysis = snapshot.analyses[pair]
                val openPosition = snapshot.openTrades.firstOrNull { it.trade.pair == pair.symbol }
                LiveCurrencyPairRow(
                    pair = pair,
                    quote = quote,
                    analysis = analysis,
                    openPosition = openPosition,
                    minConfluenceRequired = snapshot.config.minConfluenceScore.toInt(),
                    onClick = { onSelectPairForChart(pair) }
                )
            }
        }

                item { Spacer(modifier = Modifier.height(12.dp)) }
            }
        }
    }
}

@Composable
private fun DashboardHeaderCard(
    snapshot: TerminalSnapshot,
    onToggleBotArmed: (Boolean) -> Unit,
    onResetCircuitBreaker: () -> Unit,
    onSyncLiveRates: () -> Unit
) {
    val account = snapshot.account
    val maxDd = snapshot.config.maxDailyDrawdownPercent.coerceAtLeast(1.0)
    val ddRatio = (snapshot.dailyDrawdownPercent / maxDd).toFloat().coerceIn(0f, 1f)
    val isFloatingPos = snapshot.totalUnrealizedPnlUsd >= 0.0

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            1.dp,
            when {
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
                            account.circuitBreakerTripped -> "CIRCUIT BREAKER ACTIVE"
                            account.botArmed -> "BOT ARMED • AUTO-TRADING"
                            else -> "BOT STANDBY"
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
                        modifier = Modifier.clickable(onClick = onSyncLiveRates)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = "Sync live FX rates",
                                tint = CyanIndicator,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = when (snapshot.feedStatus) {
                                    FeedStatus.LIVE_ECB_SYNCED -> "LIVE ECB"
                                    FeedStatus.SYNCING -> "SYNC"
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
                        modifier = Modifier.testTag("dashboard_bot_armed_switch"),
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
                        text = "Total Equity",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "$${"%,.2f".format(Locale.US, snapshot.totalEquityUsd)}",
                        style = TabularNumericStyle.copy(fontSize = 25.sp, fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Realized / Floating",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    val realizedPos = account.realizedPnl >= 0.0
                    Text(
                        text = "${if (realizedPos) "+" else "-"}$${"%,.0f".format(Locale.US, abs(account.realizedPnl))}  /  ${if (isFloatingPos) "+" else "-"}$${"%,.2f".format(Locale.US, abs(snapshot.totalUnrealizedPnlUsd))}",
                        style = TabularNumericStyle.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
                        color = if (isFloatingPos) EmeraldBull else CrimsonBear
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

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
                        text = "Capital Protection Drawdown",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = "${"%.2f".format(Locale.US, snapshot.dailyDrawdownPercent)}% / ${"%.1f".format(Locale.US, maxDd)}%",
                    style = TabularNumericStyle.copy(fontSize = 11.sp),
                    color = if (ddRatio >= 0.75f) CrimsonBear else MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.height(5.dp))
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

            AnimatedVisibility(
                visible = snapshot.riskBreachCount > 0 ||
                    snapshot.totalLiveRiskPercent > snapshot.config.maxDailyDrawdownPercent
            ) {
                val hasCriticalBreach = snapshot.openTrades.any {
                    it.realTimeRisk.severity == RiskSeverity.BREACHED
                } || snapshot.totalLiveRiskPercent > snapshot.config.maxDailyDrawdownPercent
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (hasCriticalBreach) CrimsonContainer else AmberContainer,
                    border = BorderStroke(
                        1.dp,
                        if (hasCriticalBreach) CrimsonBear.copy(alpha = 0.6f) else AmberWarning.copy(alpha = 0.6f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                        .testTag("dashboard_header_risk_badge")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Warning,
                            contentDescription = "Capital risk threshold warning",
                            tint = if (hasCriticalBreach) CrimsonBear else AmberWarning,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (snapshot.riskBreachCount > 0) {
                                "${snapshot.riskBreachCount} open position(s) exceed capital risk thresholds (${
                                    "%.2f".format(Locale.US, snapshot.totalLiveRiskPercent)
                                }% total exposure)"
                            } else {
                                "Total open risk (${
                                    "%.2f".format(Locale.US, snapshot.totalLiveRiskPercent)
                                }%) exceeds ${"%.1f".format(Locale.US, maxDd)}% daily DD cap"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = if (hasCriticalBreach) CrimsonOnContainer else AmberOnContainer
                        )
                    }
                }
            }

            AnimatedVisibility(visible = account.circuitBreakerTripped) {
                OutlinedButton(
                    onClick = onResetCircuitBreaker,
                    border = BorderStroke(1.dp, CrimsonBear),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = CrimsonBear),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                ) {
                    Icon(Icons.Filled.LockOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Unlock Circuit Breaker")
                }
            }
        }
    }
}

@Composable
private fun OpenPositionsSummaryCard(
    snapshot: TerminalSnapshot,
    isBusy: Boolean,
    onViewAllPositions: () -> Unit,
    onSelectPairForChart: (ForexPair) -> Unit,
    onClosePosition: (OpenTradeLiveItem) -> Unit
) {
    val openItems = snapshot.openTrades
    val totalRiskUsd = snapshot.totalLiveRiskUsd
    val aggregateOverCap = snapshot.totalLiveRiskPercent > snapshot.config.maxDailyDrawdownPercent
    val hasRiskAlerts = snapshot.riskBreachCount > 0 || aggregateOverCap
    val hasBreachSeverity = openItems.any { it.realTimeRisk.severity == RiskSeverity.BREACHED } || aggregateOverCap
    val floatingPos = snapshot.totalUnrealizedPnlUsd >= 0.0

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            1.dp,
            when {
                hasBreachSeverity -> CrimsonBear.copy(alpha = 0.6f)
                hasRiskAlerts -> AmberWarning.copy(alpha = 0.6f)
                else -> MaterialTheme.colorScheme.outlineVariant
            }
        ),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("open_positions_summary_card")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Open Positions Summary",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (hasRiskAlerts) {
                            Spacer(modifier = Modifier.width(8.dp))
                            RiskWarningBadge(
                                label = if (snapshot.riskBreachCount > 0) "${snapshot.riskBreachCount} RISK ALERT" else "DD EXPOSURE",
                                isCritical = hasBreachSeverity,
                                modifier = Modifier.testTag("open_positions_risk_warning_badge")
                            )
                        }
                    }
                    Text(
                        text = "${openItems.size} of ${snapshot.config.maxOpenPositions} max slots • Cap ${
                            "%.1f".format(Locale.US, snapshot.config.riskPerTradePercent)
                        }%/trade",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                TextButton(
                    onClick = onViewAllPositions,
                    modifier = Modifier.testTag("dashboard_view_all_positions")
                ) {
                    Text("Manage", style = MaterialTheme.typography.labelLarge)
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 3-Column Metrics Strip
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    SummaryStat("Active", "${openItems.size}/${snapshot.config.maxOpenPositions}", MaterialTheme.colorScheme.onSurface)
                    SummaryStat(
                        "Live Risk",
                        "$${"%,.0f".format(Locale.US, totalRiskUsd)} (${"%.2f".format(Locale.US, snapshot.totalLiveRiskPercent)}%)",
                        when {
                            hasBreachSeverity -> CrimsonBear
                            hasRiskAlerts -> AmberWarning
                            else -> CyanIndicator
                        }
                    )
                    SummaryStat(
                        "Unrealized P&L",
                        "${if (floatingPos) "+" else "-"}$${"%,.2f".format(Locale.US, abs(snapshot.totalUnrealizedPnlUsd))}",
                        if (floatingPos) EmeraldBull else CrimsonBear
                    )
                }
            }

            if (openItems.isEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "No active positions. Arm the bot above or tap any currency pair below to review exact Entry, Stop-Loss, and Take-Profit levels.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Spacer(modifier = Modifier.height(10.dp))
                openItems.take(3).forEachIndexed { index, item ->
                    if (index > 0) {
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                    CompactOpenPositionRow(
                        item = item,
                        isBusy = isBusy,
                        onInspect = { onSelectPairForChart(ForexPair.fromSymbol(item.trade.pair)) },
                        onClose = { onClosePosition(item) }
                    )
                }
                if (openItems.size > 3) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "+${openItems.size - 3} more open positions in Positions tab",
                        style = MaterialTheme.typography.labelSmall,
                        color = CyanIndicator
                    )
                }
            }
        }
    }
}

@Composable
private fun SummaryStat(label: String, value: String, valueColor: Color) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            style = TabularNumericStyle.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold),
            color = valueColor
        )
    }
}

@Composable
private fun CompactOpenPositionRow(
    item: OpenTradeLiveItem,
    isBusy: Boolean,
    onInspect: () -> Unit,
    onClose: () -> Unit
) {
    val trade = item.trade
    val pair = ForexPair.fromSymbol(trade.pair)
    val isBuy = TradeSide.safeValueOf(trade.side) == TradeSide.BUY
    val isProfit = item.unrealizedPnlUsd >= 0.0
    val risk = item.realTimeRisk

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onInspect)
            .padding(vertical = 2.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = trade.pair,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = if (isBuy) EmeraldContainer else CrimsonContainer
                    ) {
                        Text(
                            text = "${trade.side} ${"%.2f".format(Locale.US, trade.lotSize)}L",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isBuy) EmeraldOnContainer else CrimsonOnContainer,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                        )
                    }
                    if (trade.trailingStopPrice > 0.0) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "TRAIL",
                            style = MaterialTheme.typography.labelSmall,
                            color = CyanIndicator
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "In ${pair.formatPrice(trade.entryPrice)} • SL ${pair.formatPrice(trade.stopLoss)} • Risk ${
                        "%.2f".format(Locale.US, risk.riskPercentOfEquity)
                    }%",
                    style = TabularNumericStyle.copy(fontSize = 10.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(horizontalAlignment = Alignment.End) {
                    val sign = if (isProfit) "+" else "-"
                    Text(
                        text = "$sign$${"%.2f".format(Locale.US, abs(item.unrealizedPnlUsd))}",
                        style = TabularNumericStyle.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold),
                        color = if (isProfit) EmeraldBull else CrimsonBear
                    )
                    Text(
                        text = "${if (item.unrealizedPips >= 0) "+" else ""}${"%.1f".format(Locale.US, item.unrealizedPips)}p",
                        style = TabularNumericStyle.copy(fontSize = 10.sp),
                        color = if (isProfit) EmeraldBull else CrimsonBear
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(enabled = !isBusy, onClick = onClose)
                ) {
                    Text(
                        text = "Close",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                    )
                }
            }
        }

        if (risk.exceedsThreshold && risk.badgeLabel != null) {
            Spacer(modifier = Modifier.height(5.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                RiskWarningBadge(
                    label = risk.badgeLabel,
                    isCritical = risk.severity == RiskSeverity.BREACHED,
                    modifier = Modifier.testTag("position_risk_badge_${trade.id}")
                )
                if (risk.warningReasons.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = risk.warningReasons.first(),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (risk.severity == RiskSeverity.BREACHED) CrimsonBear else AmberWarning,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun RiskWarningBadge(
    label: String,
    isCritical: Boolean,
    modifier: Modifier = Modifier
) {
    val bg = if (isCritical) CrimsonContainer else AmberContainer
    val fg = if (isCritical) CrimsonOnContainer else AmberOnContainer
    val iconTint = if (isCritical) CrimsonBear else AmberWarning
    Surface(
        shape = RoundedCornerShape(5.dp),
        color = bg,
        border = BorderStroke(0.75.dp, iconTint.copy(alpha = 0.5f)),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = "Risk warning",
                tint = iconTint,
                modifier = Modifier.size(11.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = fg,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun LiveCurrencyPairRow(
    pair: ForexPair,
    quote: PairQuote?,
    analysis: TechnicalAnalysisResult?,
    openPosition: OpenTradeLiveItem?,
    minConfluenceRequired: Int,
    onClick: () -> Unit
) {
    val hasOpenPosition = openPosition != null
    val openRisk = openPosition?.realTimeRisk
    val sparkValues = remember(quote) {
        quote?.candles?.takeLast(20)?.map { it.close }.orEmpty()
    }
    val isUp = (quote?.changePercent ?: 0.0) >= 0.0
    val tickUp = remember(quote) {
        val candles = quote?.candles.orEmpty()
        if (candles.size >= 2) candles.last().close >= candles[candles.lastIndex - 1].close else isUp
    }
    val priceColor = if (tickUp) EmeraldBull else CrimsonBear
    val spreadPips = if (quote != null) (quote.ask - quote.bid) / pair.pipSize else pair.typicalSpreadPips

    val suggestedSide = analysis?.suggestedSide
    val confluence = analysis?.confluenceScore ?: 0
    val isActionable = suggestedSide != null && confluence >= minConfluenceRequired

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            1.dp,
            when {
                openRisk?.severity == RiskSeverity.BREACHED -> CrimsonBear.copy(alpha = 0.65f)
                openRisk?.severity == RiskSeverity.ELEVATED -> AmberWarning.copy(alpha = 0.6f)
                hasOpenPosition -> EmeraldBull.copy(alpha = 0.45f)
                isActionable -> CyanIndicator.copy(alpha = 0.45f)
                else -> MaterialTheme.colorScheme.outlineVariant
            }
        ),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("dashboard_pair_row_${pair.symbol}")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left: Symbol, session, confluence badge
                Column(modifier = Modifier.weight(1.1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = pair.symbol,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (hasOpenPosition) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = EmeraldContainer
                            ) {
                                Text(
                                    text = "OPEN",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = EmeraldOnContainer,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                )
                            }
                        }
                        if (openRisk != null && openRisk.exceedsThreshold) {
                            Spacer(modifier = Modifier.width(5.dp))
                            RiskWarningBadge(
                                label = if (openRisk.severity == RiskSeverity.BREACHED) "RISK BREACH" else "HIGH RISK",
                                isCritical = openRisk.severity == RiskSeverity.BREACHED,
                                modifier = Modifier.testTag("pair_risk_warning_${pair.symbol}")
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${pair.sessionLabel} • Spd ${"%.1f".format(Locale.US, spreadPips)}p",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (analysis != null) {
                        Spacer(modifier = Modifier.height(5.dp))
                        val badgeBg = when (suggestedSide) {
                            TradeSide.BUY -> EmeraldContainer
                            TradeSide.SELL -> CrimsonContainer
                            null -> MaterialTheme.colorScheme.surfaceVariant
                        }
                        val badgeFg = when (suggestedSide) {
                            TradeSide.BUY -> EmeraldOnContainer
                            TradeSide.SELL -> CrimsonOnContainer
                            null -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                        Surface(shape = RoundedCornerShape(5.dp), color = badgeBg) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (isActionable) {
                                    Icon(
                                        imageVector = Icons.Filled.Bolt,
                                        contentDescription = null,
                                        tint = badgeFg,
                                        modifier = Modifier.size(11.dp)
                                    )
                                    Spacer(modifier = Modifier.width(2.dp))
                                }
                                Text(
                                    text = when (suggestedSide) {
                                        TradeSide.BUY -> "BUY $confluence/4"
                                        TradeSide.SELL -> "SELL $confluence/4"
                                        null -> "NEUTRAL"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = badgeFg
                                )
                            }
                        }
                    }
                }

                // Middle: Live Mini Sparkline
                MiniSparkline(
                    values = sparkValues,
                    lineColor = priceColor,
                    modifier = Modifier
                        .width(74.dp)
                        .height(38.dp)
                        .padding(horizontal = 4.dp)
                )

                // Right: Real-time Bid/Ask, Mid Price, % Change
                Column(
                    modifier = Modifier.weight(1.0f),
                    horizontalAlignment = Alignment.End
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (tickUp) Icons.Filled.ArrowUpward else Icons.Filled.ArrowDownward,
                            contentDescription = null,
                            tint = priceColor,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = quote?.let { pair.formatPrice(it.mid) } ?: "—",
                            style = TabularNumericStyle.copy(
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = priceColor
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    if (quote != null) {
                        Text(
                            text = "${pair.formatPrice(quote.bid)} / ${pair.formatPrice(quote.ask)}",
                            style = TabularNumericStyle.copy(fontSize = 10.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${if (isUp) "+" else ""}${"%.2f".format(Locale.US, quote.changePercent)}%  •  RSI ${analysis?.let { "%.0f".format(Locale.US, it.latestRsi) } ?: "—"}",
                            style = TabularNumericStyle.copy(fontSize = 11.sp),
                            color = if (isUp) EmeraldBull else CrimsonBear
                        )
                    }
                }
            }

            // Bottom subtle exact Entry/SL/TP preview for actionable setups
            if (isActionable && analysis != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.background.copy(alpha = 0.6f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Entry ${pair.formatPrice(analysis.exactEntryPrice)}",
                            style = TabularNumericStyle.copy(fontSize = 10.sp),
                            color = CyanIndicator
                        )
                        Text(
                            text = "SL ${pair.formatPrice(analysis.exactStopLoss)}",
                            style = TabularNumericStyle.copy(fontSize = 10.sp),
                            color = CrimsonBear
                        )
                        Text(
                            text = "TP ${pair.formatPrice(analysis.exactTakeProfit)}",
                            style = TabularNumericStyle.copy(fontSize = 10.sp),
                            color = EmeraldBull
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniSparkline(
    values: List<Double>,
    lineColor: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        if (values.size < 2) return@Canvas
        val minVal = values.minOrNull() ?: 0.0
        val maxVal = values.maxOrNull() ?: 1.0
        val span = (maxVal - minVal).coerceAtLeast(1e-6)
        val w = size.width
        val h = size.height
        val stepX = w / (values.size - 1).coerceAtLeast(1)

        val linePath = Path()
        val fillPath = Path()
        values.forEachIndexed { idx, v ->
            val x = idx * stepX
            val y = h - (((v - minVal) / span).toFloat() * (h * 0.84f) + h * 0.08f)
            if (idx == 0) {
                linePath.moveTo(x, y)
                fillPath.moveTo(x, h)
                fillPath.lineTo(x, y)
            } else {
                linePath.lineTo(x, y)
                fillPath.lineTo(x, y)
            }
        }
        fillPath.lineTo(w, h)
        fillPath.close()

        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(lineColor.copy(alpha = 0.25f), Color.Transparent)
            )
        )
        drawPath(
            path = linePath,
            color = lineColor,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
        )
        val lastX = (values.size - 1) * stepX
        val lastY = h - (((values.last() - minVal) / span).toFloat() * (h * 0.84f) + h * 0.08f)
        drawCircle(color = lineColor, radius = 2.8.dp.toPx(), center = Offset(lastX, lastY))
    }
}
