package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.BotRiskConfig
import com.example.model.ForexPair
import com.example.ui.TerminalSnapshot
import com.example.ui.theme.CrimsonBear
import com.example.ui.theme.CyanIndicator
import com.example.ui.theme.EmeraldBull
import com.example.ui.theme.TabularNumericStyle
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RiskSettingsScreen(
    snapshot: TerminalSnapshot,
    userEmail: String?,
    isBusy: Boolean,
    onSaveConfig: (BotRiskConfig) -> Unit,
    onResetAccount: (Double) -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cfg = snapshot.config
    var riskPct by remember(cfg) { mutableDoubleStateOf(cfg.riskPerTradePercent) }
    var maxDrawdownPct by remember(cfg) { mutableDoubleStateOf(cfg.maxDailyDrawdownPercent) }
    var minRR by remember(cfg) { mutableDoubleStateOf(cfg.minRiskRewardRatio) }
    var atrMult by remember(cfg) { mutableDoubleStateOf(cfg.atrStopMultiplier) }
    var maxPositions by remember(cfg) { mutableIntStateOf(cfg.maxOpenPositions.toInt()) }
    var trailingEnabled by remember(cfg) { mutableStateOf(cfg.trailingStopEnabled) }
    var breakEvenR by remember(cfg) { mutableDoubleStateOf(cfg.breakEvenTriggerR) }
    var emaFast by remember(cfg) { mutableIntStateOf(cfg.emaFastPeriod.toInt()) }
    var emaSlow by remember(cfg) { mutableIntStateOf(cfg.emaSlowPeriod.toInt()) }
    var minConfluence by remember(cfg) { mutableIntStateOf(cfg.minConfluenceScore.toInt()) }
    var watchedPairs by remember(cfg) { mutableStateOf(cfg.watchedPairs.toSet()) }

    var pendingResetCapital by remember { mutableDoubleStateOf(0.0) }
    var showResetDialog by remember { mutableStateOf(false) }

    LaunchedEffect(emaFast) {
        if (emaSlow <= emaFast + 2) {
            emaSlow = (emaFast + 4).coerceAtMost(100)
        }
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Reset Paper Trading Account?") },
            text = {
                Text(
                    "This clears all open/closed trade history and resets account equity to $${"%,.0f".format(Locale.US, pendingResetCapital)}."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showResetDialog = false
                        onResetAccount(pendingResetCapital)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonBear)
                ) {
                    Text("Reset Account")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Capital Protection Rules
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Shield,
                            contentDescription = null,
                            tint = EmeraldBull,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Capital Protection Rules",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    val riskUsdPreview = snapshot.account.accountBalance * (riskPct / 100.0)
                    SliderControl(
                        label = "Risk Per Trade",
                        valueLabel = "${"%.2f".format(Locale.US, riskPct)}% ($${"%,.2f".format(Locale.US, riskUsdPreview)})",
                        value = riskPct.toFloat(),
                        range = 0.25f..5.0f,
                        onValueChange = { riskPct = ((it * 4f).roundToInt() / 4.0) }
                    )

                    val ddUsdPreview = snapshot.account.dailyStartEquity * (maxDrawdownPct / 100.0)
                    SliderControl(
                        label = "Max Daily Drawdown Kill-Switch",
                        valueLabel = "${"%.1f".format(Locale.US, maxDrawdownPct)}% (-$${"%,.0f".format(Locale.US, ddUsdPreview)})",
                        value = maxDrawdownPct.toFloat(),
                        range = 1.0f..10.0f,
                        onValueChange = { maxDrawdownPct = ((it * 2f).roundToInt() / 2.0) }
                    )

                    SliderControl(
                        label = "Minimum Risk:Reward Ratio",
                        valueLabel = "1 : ${"%.1f".format(Locale.US, minRR)}",
                        value = minRR.toFloat(),
                        range = 1.2f..4.0f,
                        onValueChange = { minRR = ((it * 10f).roundToInt() / 10.0) }
                    )

                    SliderControl(
                        label = "ATR(14) Stop-Loss Multiplier",
                        valueLabel = "${"%.1f".format(Locale.US, atrMult)}x ATR",
                        value = atrMult.toFloat(),
                        range = 1.0f..3.5f,
                        onValueChange = { atrMult = ((it * 10f).roundToInt() / 10.0) }
                    )

                    SliderControl(
                        label = "Max Concurrent Open Positions",
                        valueLabel = "$maxPositions trades",
                        value = maxPositions.toFloat(),
                        range = 1f..6f,
                        onValueChange = { maxPositions = it.roundToInt().coerceIn(1, 6) }
                    )

                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Trailing Stop & Auto-Breakeven",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Ratchets SL to exact entry + 1 pip once profit reaches +${"%.1f".format(Locale.US, breakEvenR)}R.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = trailingEnabled,
                            onCheckedChange = { trailingEnabled = it }
                        )
                    }

                    if (trailingEnabled) {
                        Spacer(modifier = Modifier.height(6.dp))
                        SliderControl(
                            label = "Breakeven Trigger Threshold",
                            valueLabel = "+${"%.1f".format(Locale.US, breakEvenR)}R",
                            value = breakEvenR.toFloat(),
                            range = 0.5f..2.0f,
                            onValueChange = { breakEvenR = ((it * 10f).roundToInt() / 10.0) }
                        )
                    }
                }
            }
        }

        // 2. Technical Indicator Parameters & Watchlist
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Tune,
                            contentDescription = null,
                            tint = CyanIndicator,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Indicator Engine & Markets",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    SliderControl(
                        label = "Fast EMA Period",
                        valueLabel = "$emaFast bars",
                        value = emaFast.toFloat(),
                        range = 5f..20f,
                        onValueChange = { emaFast = it.roundToInt().coerceIn(5, 20) }
                    )

                    SliderControl(
                        label = "Slow EMA Period",
                        valueLabel = "$emaSlow bars",
                        value = emaSlow.toFloat(),
                        range = (emaFast + 3).toFloat()..60f,
                        onValueChange = { emaSlow = it.roundToInt().coerceIn(emaFast + 3, 60) }
                    )

                    SliderControl(
                        label = "Required Indicator Confluence",
                        valueLabel = "$minConfluence / 4 aligned",
                        value = minConfluence.toFloat(),
                        range = 2f..4f,
                        onValueChange = { minConfluence = it.roundToInt().coerceIn(2, 4) }
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Active Global FX Pairs",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        ForexPair.entries.forEach { pair ->
                            val selected = pair.symbol in watchedPairs
                            FilterChip(
                                selected = selected,
                                onClick = {
                                    watchedPairs = if (selected && watchedPairs.size > 1) {
                                        watchedPairs - pair.symbol
                                    } else {
                                        watchedPairs + pair.symbol
                                    }
                                },
                                label = { Text(pair.symbol) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = {
                            onSaveConfig(
                                cfg.copy(
                                    riskPerTradePercent = riskPct,
                                    maxDailyDrawdownPercent = maxDrawdownPct,
                                    minRiskRewardRatio = minRR,
                                    atrStopMultiplier = atrMult,
                                    maxOpenPositions = maxPositions.toLong(),
                                    trailingStopEnabled = trailingEnabled,
                                    breakEvenTriggerR = breakEvenR,
                                    emaFastPeriod = emaFast.toLong(),
                                    emaSlowPeriod = emaSlow.toLong(),
                                    minConfluenceScore = minConfluence.toLong(),
                                    watchedPairs = watchedPairs.toList()
                                )
                            )
                        },
                        enabled = !isBusy,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("save_risk_config_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Save Risk & Strategy Rules")
                    }
                }
            }
        }

        // 3. Paper Capital & Account Session
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Paper Trading Capital",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (!userEmail.isNullOrBlank()) {
                        Text(
                            text = "Signed in as $userEmail",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(10_000.0 to "$10K", 25_000.0 to "$25K", 50_000.0 to "$50K", 100_000.0 to "$100K").forEach { (cap, label) ->
                            OutlinedButton(
                                onClick = {
                                    pendingResetCapital = cap
                                    showResetDialog = true
                                },
                                enabled = !isBusy,
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                            ) {
                                Icon(Icons.Filled.RestartAlt, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(2.dp))
                                Text(label, style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedButton(
                        onClick = onSignOut,
                        border = BorderStroke(1.dp, CrimsonBear.copy(alpha = 0.6f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = CrimsonBear),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("sign_out_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Sign Out")
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(12.dp)) }
    }
}

@Composable
private fun SliderControl(
    label: String,
    valueLabel: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = valueLabel,
                style = TabularNumericStyle.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold),
                color = CyanIndicator
            )
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onValueChange,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = EmeraldBull,
                activeTrackColor = EmeraldBull
            )
        )
    }
}
