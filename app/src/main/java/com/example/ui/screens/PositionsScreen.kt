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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.CandlestickChart
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.RiskSeverity
import com.example.model.ForexPair
import com.example.model.TradeSide
import com.example.ui.OpenTradeLiveItem
import com.example.ui.TerminalSnapshot
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
fun PositionsScreen(
    snapshot: TerminalSnapshot,
    isBusy: Boolean,
    onClosePosition: (OpenTradeLiveItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val openItems = snapshot.openTrades
    val totalRiskAtStake = openItems.sumOf { it.trade.riskAmountUsd }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "Open Slots",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${openItems.size} / ${snapshot.config.maxOpenPositions}",
                            style = TabularNumericStyle.copy(fontSize = 18.sp, fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Capital at Risk",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "$${"%,.2f".format(Locale.US, totalRiskAtStake)}",
                            style = TabularNumericStyle.copy(fontSize = 18.sp, fontWeight = FontWeight.Bold),
                            color = CyanIndicator
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "Floating P&L",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        val isPos = snapshot.totalUnrealizedPnlUsd >= 0.0
                        Text(
                            text = "${if (isPos) "+" else "-"}$${"%,.2f".format(Locale.US, abs(snapshot.totalUnrealizedPnlUsd))}",
                            style = TabularNumericStyle.copy(fontSize = 18.sp, fontWeight = FontWeight.Bold),
                            color = if (isPos) EmeraldBull else CrimsonBear
                        )
                    }
                }
            }
        }

        if (openItems.isEmpty()) {
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
                            imageVector = Icons.Outlined.CandlestickChart,
                            contentDescription = null,
                            tint = CyanIndicator,
                            modifier = Modifier.size(44.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No Active Positions",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Arm the automated bot in Terminal to enter high-confluence setups automatically, or execute a calibrated setup manually.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(openItems, key = { it.trade.id }) { item ->
                OpenPositionCard(
                    item = item,
                    isBusy = isBusy,
                    onClosePosition = { onClosePosition(item) }
                )
            }
        }

        item { Spacer(modifier = Modifier.height(12.dp)) }
    }
}

@Composable
private fun OpenPositionCard(
    item: OpenTradeLiveItem,
    isBusy: Boolean,
    onClosePosition: () -> Unit
) {
    val trade = item.trade
    val pair = ForexPair.fromSymbol(trade.pair)
    val isBuy = TradeSide.safeValueOf(trade.side) == TradeSide.BUY
    val isProfit = item.unrealizedPnlUsd >= 0.0
    val hasTrailingLock = trade.trailingStopPrice > 0.0

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, if (isProfit) EmeraldBull.copy(alpha = 0.35f) else CrimsonBear.copy(alpha = 0.35f)),
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
                    Text(
                        text = trade.pair,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (isBuy) EmeraldContainer else CrimsonContainer
                    ) {
                        Text(
                            text = "${trade.side} ${"%.2f".format(Locale.US, trade.lotSize)}L",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isBuy) EmeraldOnContainer else CrimsonOnContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                    if (hasTrailingLock) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Shield,
                                    contentDescription = null,
                                    tint = CyanIndicator,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = "TRAIL",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = CyanIndicator
                                )
                            }
                        }
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    val sign = if (isProfit) "+" else "-"
                    Text(
                        text = "$sign$${"%,.2f".format(Locale.US, abs(item.unrealizedPnlUsd))}",
                        style = TabularNumericStyle.copy(fontSize = 18.sp, fontWeight = FontWeight.Bold),
                        color = if (isProfit) EmeraldBull else CrimsonBear
                    )
                    Text(
                        text = "${if (item.unrealizedPips >= 0) "+" else ""}${"%.1f".format(Locale.US, item.unrealizedPips)} pips",
                        style = TabularNumericStyle.copy(fontSize = 11.sp),
                        color = if (isProfit) EmeraldBull else CrimsonBear
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // SL -> TP Progress Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "SL ${pair.formatPrice(trade.stopLoss)}",
                    style = TabularNumericStyle.copy(fontSize = 11.sp),
                    color = CrimsonBear
                )
                Text(
                    text = "NOW ${pair.formatPrice(item.currentPrice)}",
                    style = TabularNumericStyle.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold),
                    color = CyanIndicator
                )
                Text(
                    text = "TP ${pair.formatPrice(trade.takeProfit)}",
                    style = TabularNumericStyle.copy(fontSize = 11.sp),
                    color = EmeraldBull
                )
            }
            Spacer(modifier = Modifier.height(5.dp))
            LinearProgressIndicator(
                progress = { item.progressToTp },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(7.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = if (isProfit) EmeraldBull else CrimsonBear,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            Spacer(modifier = Modifier.height(10.dp))

            val risk = item.realTimeRisk
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Entry: ${pair.formatPrice(trade.entryPrice)}",
                    style = TabularNumericStyle.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Live Risk: $${"%.2f".format(Locale.US, risk.liveRiskUsd)} (${
                        "%.2f".format(Locale.US, risk.riskPercentOfEquity)
                    }% / ${"%.1f".format(Locale.US, risk.maxAllowedRiskPercent)}%)",
                    style = TabularNumericStyle.copy(fontSize = 11.sp),
                    color = when (risk.severity) {
                        RiskSeverity.BREACHED -> CrimsonBear
                        RiskSeverity.ELEVATED -> AmberWarning
                        RiskSeverity.SAFE -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }

            if (risk.exceedsThreshold && risk.badgeLabel != null) {
                val isCritical = risk.severity == RiskSeverity.BREACHED
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (isCritical) CrimsonContainer else AmberContainer,
                    border = BorderStroke(
                        0.75.dp,
                        if (isCritical) CrimsonBear.copy(alpha = 0.5f) else AmberWarning.copy(alpha = 0.5f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Warning,
                            contentDescription = "Risk warning",
                            tint = if (isCritical) CrimsonBear else AmberWarning,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "${risk.badgeLabel} • ${risk.warningReasons.joinToString(" • ")}",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isCritical) CrimsonOnContainer else AmberOnContainer
                        )
                    }
                }
            }

            if (trade.signalSummary.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = trade.signalSummary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = onClosePosition,
                enabled = !isBusy,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .testTag("close_trade_${trade.id}")
            ) {
                Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Close Position at Market")
            }
        }
    }
}
