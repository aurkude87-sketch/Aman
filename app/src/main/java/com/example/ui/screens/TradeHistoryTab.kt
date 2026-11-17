package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ExitReason
import com.example.model.ForexPair
import com.example.model.ForexTrade
import com.example.model.TradeSide
import com.example.model.UiState
import com.example.ui.TradeHistorySort
import com.example.ui.theme.AmberContainer
import com.example.ui.theme.AmberOnContainer
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

private enum class HistoryOutcomeFilter(val label: String) {
    ALL("All"),
    WINNERS("Winners"),
    LOSSES("Losses")
}

@Composable
fun TradeHistoryTab(
    historyState: UiState<List<ForexTrade>>,
    sortOrder: TradeHistorySort,
    isRefreshing: Boolean,
    onSelectSort: (TradeHistorySort) -> Unit,
    onRefreshHistory: () -> Unit,
    onSelectPairForChart: (ForexPair) -> Unit,
    modifier: Modifier = Modifier
) {
    var outcomeFilter by remember { mutableStateOf(HistoryOutcomeFilter.ALL) }

    when (historyState) {
        is UiState.Loading -> {
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .testTag("trade_history_loading"),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = CyanIndicator)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Querying Firestore executed trades...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        is UiState.Error -> {
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .padding(24.dp)
                    .testTag("trade_history_error"),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, CrimsonBear.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ErrorOutline,
                            contentDescription = null,
                            tint = CrimsonBear,
                            modifier = Modifier.size(38.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Unable to Load Trade History",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = historyState.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = onRefreshHistory,
                            colors = ButtonDefaults.buttonColors(containerColor = CyanIndicator),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("trade_history_retry_button")
                        ) {
                            Text("Retry Query")
                        }
                    }
                }
            }
        }

        is UiState.Success -> {
            val allExecuted = historyState.data
            val filteredTrades = remember(allExecuted, outcomeFilter) {
                when (outcomeFilter) {
                    HistoryOutcomeFilter.ALL -> allExecuted
                    HistoryOutcomeFilter.WINNERS -> allExecuted.filter { it.realizedPnlUsd >= 0.0 }
                    HistoryOutcomeFilter.LOSSES -> allExecuted.filter { it.realizedPnlUsd < 0.0 }
                }
            }

            val totalNetPnl = remember(allExecuted) { allExecuted.sumOf { it.realizedPnlUsd } }
            val totalPips = remember(allExecuted) { allExecuted.sumOf { it.realizedPips } }
            val winRate = remember(allExecuted) {
                if (allExecuted.isEmpty()) 0.0
                else (allExecuted.count { it.realizedPnlUsd > 0.0 }.toDouble() / allExecuted.size) * 100.0
            }

            LazyColumn(
                modifier = modifier
                    .fillMaxSize()
                    .testTag("trade_history_list"),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 1. Firestore Query Summary Card
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
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
                                    Icon(
                                        imageVector = Icons.Filled.CloudDone,
                                        contentDescription = null,
                                        tint = CyanIndicator,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Firestore Executed Ledger",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }

                                IconButton(
                                    onClick = onRefreshHistory,
                                    enabled = !isRefreshing,
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("trade_history_refresh_button")
                                ) {
                                    if (isRefreshing) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(18.dp),
                                            strokeWidth = 2.dp,
                                            color = CyanIndicator
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Filled.Refresh,
                                            contentDescription = "Refresh trade history from Firestore",
                                            tint = CyanIndicator
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

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
                                    HistoryMetric(
                                        label = "Executed",
                                        value = "${allExecuted.size}",
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    HistoryMetric(
                                        label = "Win Rate",
                                        value = "${"%.1f".format(Locale.US, winRate)}%",
                                        color = CyanIndicator
                                    )
                                    HistoryMetric(
                                        label = "Net Pips",
                                        value = "${if (totalPips >= 0) "+" else ""}${"%.1f".format(Locale.US, totalPips)}p",
                                        color = if (totalPips >= 0) EmeraldBull else CrimsonBear
                                    )
                                    HistoryMetric(
                                        label = "Net P&L",
                                        value = "${if (totalNetPnl >= 0) "+" else "-"}$${"%,.2f".format(Locale.US, abs(totalNetPnl))}",
                                        color = if (totalNetPnl >= 0) EmeraldBull else CrimsonBear
                                    )
                                }
                            }
                        }
                    }
                }

                // 2. Sort Order & Outcome Filter Controls
                item {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Filled.SwapVert,
                                contentDescription = null,
                                tint = CyanIndicator,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Sort Order",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            TradeHistorySort.entries.forEach { option ->
                                FilterChip(
                                    selected = sortOrder == option,
                                    onClick = { onSelectSort(option) },
                                    label = { Text(option.label) },
                                    modifier = Modifier.testTag("history_sort_${option.name.lowercase()}")
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            HistoryOutcomeFilter.entries.forEach { filterOption ->
                                FilterChip(
                                    selected = outcomeFilter == filterOption,
                                    onClick = { outcomeFilter = filterOption },
                                    label = { Text(filterOption.label) },
                                    modifier = Modifier.testTag("history_filter_${filterOption.name.lowercase()}")
                                )
                            }
                        }
                    }
                }

                // 3. Sorted Trade History List or Empty State
                if (filteredTrades.isEmpty()) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("trade_history_empty")
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(28.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.History,
                                    contentDescription = null,
                                    tint = CyanIndicator,
                                    modifier = Modifier.size(40.dp)
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = if (allExecuted.isEmpty()) {
                                        "No Past Executed Trades Yet"
                                    } else {
                                        "No Trades Match Filter"
                                    },
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = if (allExecuted.isEmpty()) {
                                        "Closed positions settled by Take-Profit, Stop-Loss, Trailing Stop, or manual close are stored in Firestore and listed here in sorted order."
                                    } else {
                                        "Try switching back to All to inspect all settled trades."
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                } else {
                    items(filteredTrades, key = { it.id }) { trade ->
                        ExecutedTradeHistoryCard(
                            trade = trade,
                            onClick = { onSelectPairForChart(ForexPair.fromSymbol(trade.pair)) }
                        )
                    }
                }

                item { Spacer(modifier = Modifier.height(12.dp)) }
            }
        }
    }
}

@Composable
private fun HistoryMetric(label: String, value: String, color: Color) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            style = TabularNumericStyle.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold),
            color = color
        )
    }
}

@Composable
private fun ExecutedTradeHistoryCard(
    trade: ForexTrade,
    onClick: () -> Unit
) {
    val pair = ForexPair.fromSymbol(trade.pair)
    val isBuy = TradeSide.safeValueOf(trade.side) == TradeSide.BUY
    val isWin = trade.realizedPnlUsd >= 0.0
    val exitReason = ExitReason.safeValueOf(trade.exitReason)
    val dateFmt = remember { SimpleDateFormat("MMM dd, HH:mm:ss", Locale.US) }
    val closedLabel = if (trade.closedAtMillis > 0L) dateFmt.format(Date(trade.closedAtMillis)) else "Settled"
    val openedLabel = if (trade.openedAtMillis > 0L) dateFmt.format(Date(trade.openedAtMillis)) else "—"

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            1.dp,
            if (isWin) EmeraldBull.copy(alpha = 0.35f) else CrimsonBear.copy(alpha = 0.35f)
        ),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("trade_history_item_${trade.id}")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = trade.pair,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        shape = RoundedCornerShape(5.dp),
                        color = if (isBuy) EmeraldContainer else CrimsonContainer
                    ) {
                        Text(
                            text = "${trade.side} ${"%.2f".format(Locale.US, trade.lotSize)}L",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isBuy) EmeraldOnContainer else CrimsonOnContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    val reasonBg = when (exitReason) {
                        ExitReason.TP_HIT, ExitReason.TRAILING_SL -> EmeraldContainer
                        ExitReason.SL_HIT, ExitReason.CIRCUIT_BREAKER -> CrimsonContainer
                        else -> AmberContainer
                    }
                    val reasonFg = when (exitReason) {
                        ExitReason.TP_HIT, ExitReason.TRAILING_SL -> EmeraldOnContainer
                        ExitReason.SL_HIT, ExitReason.CIRCUIT_BREAKER -> CrimsonOnContainer
                        else -> AmberOnContainer
                    }
                    Surface(shape = RoundedCornerShape(5.dp), color = reasonBg) {
                        Text(
                            text = exitReason.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = reasonFg,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    val sign = if (isWin) "+" else "-"
                    Text(
                        text = "$sign$${"%,.2f".format(Locale.US, abs(trade.realizedPnlUsd))}",
                        style = TabularNumericStyle.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold),
                        color = if (isWin) EmeraldBull else CrimsonBear
                    )
                    Text(
                        text = "${if (trade.realizedPips >= 0) "+" else ""}${"%.1f".format(Locale.US, trade.realizedPips)} pips",
                        style = TabularNumericStyle.copy(fontSize = 11.sp),
                        color = if (isWin) EmeraldBull else CrimsonBear
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Entry ${pair.formatPrice(trade.entryPrice)} → Exit ${pair.formatPrice(trade.exitPrice)}",
                    style = TabularNumericStyle.copy(fontSize = 12.sp),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Confluence ${trade.confluenceScore}/4 • ${trade.timeframe}",
                    style = MaterialTheme.typography.labelSmall,
                    color = CyanIndicator
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "SL ${pair.formatPrice(trade.stopLoss)} • TP ${pair.formatPrice(trade.takeProfit)}",
                    style = TabularNumericStyle.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Closed $closedLabel",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (trade.signalSummary.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.background.copy(alpha = 0.55f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Opened $openedLabel • ${trade.signalSummary}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                    )
                }
            }
        }
    }
}
