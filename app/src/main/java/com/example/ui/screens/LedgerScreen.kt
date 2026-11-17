package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ExitReason
import com.example.model.ForexPair
import com.example.model.ForexTrade
import com.example.model.TradeSide
import com.example.ui.TerminalSnapshot
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

private enum class LedgerFilter(val label: String) {
    ALL("All"),
    WINNERS("Winners"),
    LOSSES("Losses")
}

@Composable
fun LedgerScreen(
    snapshot: TerminalSnapshot,
    modifier: Modifier = Modifier
) {
    var filter by remember { mutableStateOf(LedgerFilter.ALL) }
    val closed = snapshot.closedTrades

    val winCount = closed.count { it.realizedPnlUsd > 0.0 }
    val winRate = if (closed.isNotEmpty()) (winCount.toDouble() / closed.size) * 100.0 else 0.0
    val grossProfit = closed.filter { it.realizedPnlUsd > 0.0 }.sumOf { it.realizedPnlUsd }
    val grossLoss = abs(closed.filter { it.realizedPnlUsd < 0.0 }.sumOf { it.realizedPnlUsd })
    val profitFactor = when {
        grossLoss > 0.0 -> grossProfit / grossLoss
        grossProfit > 0.0 -> 9.99
        else -> 0.0
    }
    val netRealized = snapshot.account.realizedPnl

    val displayed = remember(closed, filter) {
        when (filter) {
            LedgerFilter.ALL -> closed
            LedgerFilter.WINNERS -> closed.filter { it.realizedPnlUsd >= 0.0 }
            LedgerFilter.LOSSES -> closed.filter { it.realizedPnlUsd < 0.0 }
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Summary Metrics Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Executed Trade Performance",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        MetricColumn(
                            label = "Net Realized",
                            value = "${if (netRealized >= 0) "+" else "-"}$${"%,.2f".format(Locale.US, abs(netRealized))}",
                            color = if (netRealized >= 0) EmeraldBull else CrimsonBear
                        )
                        MetricColumn(
                            label = "Win Rate",
                            value = "${"%.1f".format(Locale.US, winRate)}%",
                            color = CyanIndicator
                        )
                        MetricColumn(
                            label = "Profit Factor",
                            value = "%.2f".format(Locale.US, profitFactor),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        MetricColumn(
                            label = "Trades",
                            value = "${closed.size}",
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        // Filter Chips
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LedgerFilter.entries.forEach { option ->
                    FilterChip(
                        selected = filter == option,
                        onClick = { filter = option },
                        label = { Text(option.label) }
                    )
                }
            }
        }

        if (displayed.isEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ReceiptLong,
                            contentDescription = null,
                            tint = CyanIndicator,
                            modifier = Modifier.size(42.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "No Closed Trades Yet",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Every trade closed by Take-Profit, Stop-Loss, Trailing Stop, or Circuit Breaker is audited here with exact entry & exit prices.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(displayed, key = { it.id }) { trade ->
                ClosedTradeCard(trade = trade)
            }
        }

        item { Spacer(modifier = Modifier.height(12.dp)) }
    }
}

@Composable
private fun MetricColumn(
    label: String,
    value: String,
    color: androidx.compose.ui.graphics.Color
) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            style = TabularNumericStyle.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold),
            color = color
        )
    }
}

@Composable
private fun ClosedTradeCard(trade: ForexTrade) {
    val pair = ForexPair.fromSymbol(trade.pair)
    val isBuy = TradeSide.safeValueOf(trade.side) == TradeSide.BUY
    val isWin = trade.realizedPnlUsd >= 0.0
    val reason = ExitReason.safeValueOf(trade.exitReason)
    val timeFmt = remember { SimpleDateFormat("MMM dd, HH:mm:ss", Locale.US) }
    val closedTimeStr = if (trade.closedAtMillis > 0L) timeFmt.format(Date(trade.closedAtMillis)) else "Settled"

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = trade.pair,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(8.dp))
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
                    val reasonBg = when (reason) {
                        ExitReason.TP_HIT, ExitReason.TRAILING_SL -> EmeraldContainer
                        ExitReason.SL_HIT, ExitReason.CIRCUIT_BREAKER -> CrimsonContainer
                        else -> AmberContainer
                    }
                    val reasonFg = when (reason) {
                        ExitReason.TP_HIT, ExitReason.TRAILING_SL -> EmeraldOnContainer
                        ExitReason.SL_HIT, ExitReason.CIRCUIT_BREAKER -> CrimsonOnContainer
                        else -> AmberOnContainer
                    }
                    Surface(shape = RoundedCornerShape(5.dp), color = reasonBg) {
                        Text(
                            text = reason.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = reasonFg,
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
                    text = closedTimeStr,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (trade.signalSummary.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = trade.signalSummary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
