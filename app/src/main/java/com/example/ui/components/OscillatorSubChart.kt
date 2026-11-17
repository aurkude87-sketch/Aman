package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.model.ForexPair
import com.example.model.OscillatorPane
import com.example.model.TechnicalAnalysisResult
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CrimsonBear
import com.example.ui.theme.CyanIndicator
import com.example.ui.theme.EmeraldBull
import com.example.ui.theme.RoseIndicator
import com.example.ui.theme.VioletIndicator
import java.util.Locale
import kotlin.math.abs

@Composable
fun OscillatorSubChart(
    pair: ForexPair,
    visibleCount: Int,
    candleCount: Int,
    analysis: TechnicalAnalysisResult,
    selectedOscillator: OscillatorPane,
    onSelectOscillator: (OscillatorPane) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OscillatorPane.entries.forEach { pane ->
                FilterChip(
                    selected = selectedOscillator == pane,
                    onClick = { onSelectOscillator(pane) },
                    label = { Text(pane.label, style = MaterialTheme.typography.labelSmall) },
                    modifier = Modifier.testTag("oscillator_chip_${pane.name.lowercase()}")
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(86.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.background.copy(alpha = 0.55f))
                .padding(horizontal = 8.dp, vertical = 6.dp)
                .testTag("oscillator_canvas_${selectedOscillator.name.lowercase()}")
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                when (selectedOscillator) {
                    OscillatorPane.RSI -> {
                        Text(
                            text = "RSI(14): ${"%.1f".format(Locale.US, analysis.latestRsi)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = CyanIndicator
                        )
                        Text(
                            text = "OB 70 / OS 30",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    OscillatorPane.MACD -> {
                        Text(
                            text = "MACD ${"%.5f".format(Locale.US, analysis.macdLine)} • SIG ${"%.5f".format(Locale.US, analysis.macdSignalLine)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = CyanIndicator
                        )
                        Text(
                            text = "HIST ${if (analysis.macdHistogram >= 0) "+" else ""}${"%.5f".format(Locale.US, analysis.macdHistogram)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (analysis.macdHistogram >= 0) EmeraldBull else CrimsonBear
                        )
                    }
                    OscillatorPane.STOCHASTIC -> {
                        Text(
                            text = "Stoch %K ${"%.1f".format(Locale.US, analysis.latestStochK)} • %D ${"%.1f".format(Locale.US, analysis.latestStochD)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = VioletIndicator
                        )
                        Text(
                            text = "80 / 20",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    OscillatorPane.ADX -> {
                        Text(
                            text = "ADX ${"%.1f".format(Locale.US, analysis.latestAdx)} • +DI ${"%.1f".format(Locale.US, analysis.latestDiPlus)} • -DI ${"%.1f".format(Locale.US, analysis.latestDiMinus)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = AmberWarning
                        )
                        Text(
                            text = if (analysis.latestAdx >= 25.0) "TREND" else "RANGE",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (analysis.latestAdx >= 25.0) EmeraldBull else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    OscillatorPane.ATR -> {
                        Text(
                            text = "ATR(14): ${"%.1f".format(Locale.US, analysis.atrPips)} pips",
                            style = MaterialTheme.typography.labelSmall,
                            color = RoseIndicator
                        )
                        Text(
                            text = "VOLATILITY",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 16.dp)
            ) {
                val w = size.width
                val h = size.height
                val count = candleCount.coerceAtLeast(1)
                val stepX = w / count

                fun drawLineSeries(series: List<Double>, minVal: Double, maxVal: Double, color: Color, strokeWidth: Float = 2f) {
                    if (series.size < 2) return
                    val span = (maxVal - minVal).coerceAtLeast(1e-9)
                    val path = Path()
                    val offset = (count - series.size).coerceAtLeast(0)
                    series.forEachIndexed { idx, v ->
                        val x = (idx + offset) * stepX + stepX / 2f
                        val y = h * (1f - ((v - minVal) / span).toFloat().coerceIn(0f, 1f))
                        if (idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    drawPath(path, color = color, style = Stroke(width = strokeWidth, cap = StrokeCap.Round))
                }

                when (selectedOscillator) {
                    OscillatorPane.RSI -> {
                        val y70 = h * 0.30f
                        val y30 = h * 0.70f
                        val dash = PathEffect.dashPathEffect(floatArrayOf(5f, 5f))
                        drawLine(CrimsonBear.copy(alpha = 0.4f), Offset(0f, y70), Offset(w, y70), strokeWidth = 1f, pathEffect = dash)
                        drawLine(EmeraldBull.copy(alpha = 0.4f), Offset(0f, y30), Offset(w, y30), strokeWidth = 1f, pathEffect = dash)
                        drawLineSeries(analysis.rsiSeries.takeLast(visibleCount), 0.0, 100.0, CyanIndicator, 2.2f)
                    }

                    OscillatorPane.MACD -> {
                        val hist = analysis.macdHistogramSeries.takeLast(visibleCount)
                        val macd = analysis.macdLineSeries.takeLast(visibleCount)
                        val sig = analysis.macdSignalSeries.takeLast(visibleCount)
                        val maxAbs = (hist + macd + sig).maxOfOrNull { abs(it) }?.coerceAtLeast(pair.pipSize * 0.1) ?: 0.0001
                        val midY = h / 2f
                        drawLine(Color.White.copy(alpha = 0.18f), Offset(0f, midY), Offset(w, midY), strokeWidth = 1f)
                        val barW = (stepX * 0.5f).coerceIn(2f, 10f)
                        hist.forEachIndexed { idx, v ->
                            val x = idx * stepX + stepX / 2f
                            val barH = ((abs(v) / maxAbs).toFloat() * (h * 0.44f)).coerceAtLeast(1.5f)
                            val barColor = if (v >= 0) EmeraldBull.copy(alpha = 0.5f) else CrimsonBear.copy(alpha = 0.5f)
                            drawRect(barColor, Offset(x - barW / 2f, if (v >= 0) midY - barH else midY), Size(barW, barH))
                        }
                        drawLineSeries(macd, -maxAbs, maxAbs, CyanIndicator, 1.8f)
                        drawLineSeries(sig, -maxAbs, maxAbs, AmberWarning, 1.6f)
                    }

                    OscillatorPane.STOCHASTIC -> {
                        val y80 = h * 0.20f
                        val y20 = h * 0.80f
                        val dash = PathEffect.dashPathEffect(floatArrayOf(5f, 5f))
                        drawLine(CrimsonBear.copy(alpha = 0.4f), Offset(0f, y80), Offset(w, y80), strokeWidth = 1f, pathEffect = dash)
                        drawLine(EmeraldBull.copy(alpha = 0.4f), Offset(0f, y20), Offset(w, y20), strokeWidth = 1f, pathEffect = dash)
                        drawLineSeries(analysis.stochKSeries.takeLast(visibleCount), 0.0, 100.0, VioletIndicator, 2f)
                        drawLineSeries(analysis.stochDSeries.takeLast(visibleCount), 0.0, 100.0, AmberWarning, 1.7f)
                    }

                    OscillatorPane.ADX -> {
                        val y25 = h * (1f - 0.25f)
                        drawLine(
                            CyanIndicator.copy(alpha = 0.4f),
                            Offset(0f, y25),
                            Offset(w, y25),
                            strokeWidth = 1f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 5f))
                        )
                        drawLineSeries(analysis.diPlusSeries.takeLast(visibleCount), 0.0, 80.0, EmeraldBull, 1.6f)
                        drawLineSeries(analysis.diMinusSeries.takeLast(visibleCount), 0.0, 80.0, CrimsonBear, 1.6f)
                        drawLineSeries(analysis.adxSeries.takeLast(visibleCount), 0.0, 80.0, AmberWarning, 2.2f)
                    }

                    OscillatorPane.ATR -> {
                        val atrPipsSeries = analysis.atrSeries.takeLast(visibleCount).map { it / pair.pipSize }
                        val minAtr = (atrPipsSeries.minOrNull() ?: 0.0) * 0.85
                        val maxAtr = (atrPipsSeries.maxOrNull() ?: 10.0).coerceAtLeast(minAtr + 1.0) * 1.1
                        drawLineSeries(atrPipsSeries, minAtr, maxAtr, RoseIndicator, 2.2f)
                    }
                }
            }
        }
    }
}
