package com.example.domain

import com.example.model.BotRiskConfig
import com.example.model.ForexPair
import com.example.model.FxCandle
import com.example.model.IndicatorVote
import com.example.model.PivotLevels
import com.example.model.TechnicalAnalysisResult
import com.example.model.TradeSide
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

object TechnicalAnalysisEngine {

    fun analyze(
        pair: ForexPair,
        candles: List<FxCandle>,
        bid: Double,
        ask: Double,
        config: BotRiskConfig
    ): TechnicalAnalysisResult {
        val closes = candles.map { it.close }
        val fastPeriod = config.emaFastPeriod.toInt().coerceIn(5, 25)
        val slowPeriod = config.emaSlowPeriod.toInt().coerceIn(fastPeriod + 2, 100)
        val rsiPeriod = config.rsiPeriod.toInt().coerceIn(7, 28)

        val emaFast = computeEma(closes, fastPeriod)
        val emaSlow = computeEma(closes, slowPeriod)
        val sma50 = computeSma(closes, 50)
        val (bbUpper, bbMid, bbLower) = computeBollingerBands(closes, 20, 2.0)
        val rsiSeries = computeRsiSeries(closes, rsiPeriod)
        val (macdLine, macdSignal, macdHistSeries) = computeMacd(closes, 12, 26, 9)
        val atrSeries = computeAtrSeries(candles, 14)
        val atrValue = atrSeries.lastOrNull() ?: computeAtr(candles, 14)
        val psarSeries = computeParabolicSar(candles)
        val (tenkanSeries, kijunSeries, spanASeries, spanBSeries) = computeIchimoku(candles)
        val (stochKSeries, stochDSeries) = computeStochastic(candles)
        val (adxSeries, diPlusSeries, diMinusSeries) = computeAdx(candles, 14)
        val pivotLevels = computePivotLevels(candles)

        val latestFast = emaFast.lastOrNull() ?: closes.lastOrNull() ?: 1.0
        val prevFast = emaFast.getOrNull(emaFast.lastIndex - 1) ?: latestFast
        val latestSlow = emaSlow.lastOrNull() ?: closes.lastOrNull() ?: 1.0
        val prevSlow = emaSlow.getOrNull(emaSlow.lastIndex - 1) ?: latestSlow

        val latestRsi = rsiSeries.lastOrNull() ?: 50.0
        val prevRsi = rsiSeries.getOrNull(rsiSeries.lastIndex - 1) ?: latestRsi

        val latestMacd = macdLine.lastOrNull() ?: 0.0
        val latestMacdSig = macdSignal.lastOrNull() ?: 0.0
        val latestHist = macdHistSeries.lastOrNull() ?: 0.0
        val prevHist = macdHistSeries.getOrNull(macdHistSeries.lastIndex - 1) ?: latestHist

        val latestUpper = bbUpper.lastOrNull() ?: latestSlow
        val latestLower = bbLower.lastOrNull() ?: latestSlow
        val latestClose = closes.lastOrNull() ?: bid
        val bbWidth = (latestUpper - latestLower).coerceAtLeast(pair.pipSize)
        val bbPercentB = ((latestClose - latestLower) / bbWidth).coerceIn(-0.2, 1.2)

        // 1. EMA Vote (Trend & Golden/Death Cross)
        val emaVote = when {
            latestFast > latestSlow && (prevFast <= prevSlow || (latestFast - latestSlow) > pair.pipSize * 0.5) ->
                IndicatorVote.BULLISH
            latestFast < latestSlow && (prevFast >= prevSlow || (latestSlow - latestFast) > pair.pipSize * 0.5) ->
                IndicatorVote.BEARISH
            else -> IndicatorVote.NEUTRAL
        }

        // 2. RSI Vote (Momentum Regime + Oversold/Overbought Reversal)
        val rsiVote = when {
            latestRsi < config.rsiOversold -> IndicatorVote.BULLISH
            latestRsi > config.rsiOverbought -> IndicatorVote.BEARISH
            latestRsi in 52.0..config.rsiOverbought -> IndicatorVote.BULLISH
            latestRsi in config.rsiOversold..48.0 -> IndicatorVote.BEARISH
            else -> IndicatorVote.NEUTRAL
        }

        // 3. MACD Vote (Signal Line & Histogram Alignment)
        val macdVote = when {
            latestMacd > latestMacdSig && latestHist > 0.0 -> IndicatorVote.BULLISH
            latestMacd < latestMacdSig && latestHist < 0.0 -> IndicatorVote.BEARISH
            else -> IndicatorVote.NEUTRAL
        }

        // 4. Bollinger Bands Vote (Mean Reversion + Trend Expansion Confirmation)
        val bbVote = when {
            bbPercentB < 0.08 -> IndicatorVote.BULLISH
            bbPercentB > 0.92 -> IndicatorVote.BEARISH
            bbPercentB <= 0.28 && emaVote != IndicatorVote.BEARISH -> IndicatorVote.BULLISH
            bbPercentB >= 0.72 && emaVote != IndicatorVote.BULLISH -> IndicatorVote.BEARISH
            bbPercentB in 0.52..0.88 && emaVote == IndicatorVote.BULLISH -> IndicatorVote.BULLISH
            bbPercentB in 0.12..0.48 && emaVote == IndicatorVote.BEARISH -> IndicatorVote.BEARISH
            else -> IndicatorVote.NEUTRAL
        }

        val votes = listOf(emaVote, rsiVote, macdVote, bbVote)
        val bullScore = votes.count { it == IndicatorVote.BULLISH }
        val bearScore = votes.count { it == IndicatorVote.BEARISH }

        val suggestedSide: TradeSide?
        val confluenceScore: Int
        when {
            bullScore > bearScore && bullScore >= 2 -> {
                suggestedSide = TradeSide.BUY
                confluenceScore = bullScore
            }
            bearScore > bullScore && bearScore >= 2 -> {
                suggestedSide = TradeSide.SELL
                confluenceScore = bearScore
            }
            else -> {
                suggestedSide = null
                confluenceScore = max(bullScore, bearScore)
            }
        }

        val minStopFloor = pair.pipSize * 12.0
        val effectiveAtr = max(atrValue, pair.pipSize * 8.0)
        val stopDistance = max(effectiveAtr * config.atrStopMultiplier, minStopFloor)
        val targetRR = config.minRiskRewardRatio.coerceAtLeast(1.2)
        val tpDistance = stopDistance * targetRR

        val entrySide = suggestedSide ?: if (bullScore >= bearScore) TradeSide.BUY else TradeSide.SELL
        val exactEntry = pair.roundToTick(if (entrySide == TradeSide.BUY) ask else bid)
        val exactSl = pair.roundToTick(
            if (entrySide == TradeSide.BUY) exactEntry - stopDistance else exactEntry + stopDistance
        )
        val exactTp = pair.roundToTick(
            if (entrySide == TradeSide.BUY) exactEntry + tpDistance else exactEntry - tpDistance
        )

        val summaryParts = buildList {
            add("EMA$fastPeriod/$slowPeriod ${emaVote.shortTag()}")
            add("RSI(${"%.1f".format(java.util.Locale.US, latestRsi)})")
            add("MACD ${macdVote.shortTag()}")
            add("BB %B=${"%.0f".format(java.util.Locale.US, bbPercentB * 100)}%")
        }
        val signalSummary = summaryParts.joinToString(" • ").take(155)

        return TechnicalAnalysisResult(
            pair = pair,
            emaFastSeries = emaFast,
            emaSlowSeries = emaSlow,
            bbUpperSeries = bbUpper,
            bbMiddleSeries = bbMid,
            bbLowerSeries = bbLower,
            rsiSeries = rsiSeries,
            macdHistogramSeries = macdHistSeries,
            latestEmaFast = latestFast,
            latestEmaSlow = latestSlow,
            latestRsi = latestRsi,
            macdLine = latestMacd,
            macdSignalLine = latestMacdSig,
            macdHistogram = latestHist,
            bbPercentB = bbPercentB,
            atrValue = effectiveAtr,
            atrPips = effectiveAtr / pair.pipSize,
            emaVote = emaVote,
            rsiVote = rsiVote,
            macdVote = macdVote,
            bbVote = bbVote,
            confluenceScore = confluenceScore,
            suggestedSide = suggestedSide,
            exactEntryPrice = exactEntry,
            exactStopLoss = exactSl,
            exactTakeProfit = exactTp,
            riskRewardRatio = targetRR,
            signalSummary = signalSummary,
            sma50Series = sma50,
            psarSeries = psarSeries,
            ichimokuTenkanSeries = tenkanSeries,
            ichimokuKijunSeries = kijunSeries,
            ichimokuSpanASeries = spanASeries,
            ichimokuSpanBSeries = spanBSeries,
            macdLineSeries = macdLine,
            macdSignalSeries = macdSignal,
            stochKSeries = stochKSeries,
            stochDSeries = stochDSeries,
            atrSeries = atrSeries,
            adxSeries = adxSeries,
            diPlusSeries = diPlusSeries,
            diMinusSeries = diMinusSeries,
            latestSma50 = sma50.lastOrNull() ?: latestClose,
            latestPsar = psarSeries.lastOrNull() ?: latestClose,
            latestStochK = stochKSeries.lastOrNull() ?: 50.0,
            latestStochD = stochDSeries.lastOrNull() ?: 50.0,
            latestAdx = adxSeries.lastOrNull() ?: 20.0,
            latestDiPlus = diPlusSeries.lastOrNull() ?: 20.0,
            latestDiMinus = diMinusSeries.lastOrNull() ?: 20.0,
            pivotLevels = pivotLevels
        )
    }

    private fun IndicatorVote.shortTag(): String = when (this) {
        IndicatorVote.BULLISH -> "Bull"
        IndicatorVote.BEARISH -> "Bear"
        IndicatorVote.NEUTRAL -> "Flat"
    }

    fun computeEma(values: List<Double>, period: Int): List<Double> {
        if (values.isEmpty()) return emptyList()
        val k = 2.0 / (period + 1)
        val result = ArrayList<Double>(values.size)
        var ema = values.first()
        result.add(ema)
        for (i in 1 until values.size) {
            ema = values[i] * k + ema * (1.0 - k)
            result.add(ema)
        }
        return result
    }

    fun computeBollingerBands(
        values: List<Double>,
        period: Int = 20,
        stdMultiplier: Double = 2.0
    ): Triple<List<Double>, List<Double>, List<Double>> {
        if (values.isEmpty()) return Triple(emptyList(), emptyList(), emptyList())
        val upper = ArrayList<Double>(values.size)
        val middle = ArrayList<Double>(values.size)
        val lower = ArrayList<Double>(values.size)
        for (i in values.indices) {
            val start = max(0, i - period + 1)
            val window = values.subList(start, i + 1)
            val mean = window.average()
            val variance = window.sumOf { (it - mean).pow(2) } / window.size
            val std = sqrt(variance)
            middle.add(mean)
            upper.add(mean + stdMultiplier * std)
            lower.add(mean - stdMultiplier * std)
        }
        return Triple(upper, middle, lower)
    }

    fun computeRsiSeries(values: List<Double>, period: Int = 14): List<Double> {
        if (values.size < 2) return List(values.size) { 50.0 }
        val rsi = ArrayList<Double>(values.size)
        rsi.add(50.0)
        var avgGain = 0.0
        var avgLoss = 0.0
        for (i in 1 until values.size) {
            val delta = values[i] - values[i - 1]
            val gain = if (delta > 0) delta else 0.0
            val loss = if (delta < 0) -delta else 0.0
            if (i <= period) {
                avgGain += gain / period
                avgLoss += loss / period
            } else {
                avgGain = ((avgGain * (period - 1)) + gain) / period
                avgLoss = ((avgLoss * (period - 1)) + loss) / period
            }
            val value = if (i < period) {
                50.0
            } else if (avgLoss == 0.0) {
                100.0
            } else {
                val rs = avgGain / avgLoss
                100.0 - (100.0 / (1.0 + rs))
            }
            rsi.add(value.coerceIn(0.0, 100.0))
        }
        return rsi
    }

    fun computeMacd(
        values: List<Double>,
        fast: Int = 12,
        slow: Int = 26,
        signalPeriod: Int = 9
    ): Triple<List<Double>, List<Double>, List<Double>> {
        if (values.isEmpty()) return Triple(emptyList(), emptyList(), emptyList())
        val emaFast = computeEma(values, fast)
        val emaSlow = computeEma(values, slow)
        val macdLine = emaFast.zip(emaSlow) { f, s -> f - s }
        val signalLine = computeEma(macdLine, signalPeriod)
        val histogram = macdLine.zip(signalLine) { m, s -> m - s }
        return Triple(macdLine, signalLine, histogram)
    }

    fun computeAtr(candles: List<FxCandle>, period: Int = 14): Double {
        if (candles.size < 2) return 0.0010
        val trs = ArrayList<Double>(candles.size - 1)
        for (i in 1 until candles.size) {
            val cur = candles[i]
            val prevClose = candles[i - 1].close
            val tr = max(
                cur.high - cur.low,
                max(abs(cur.high - prevClose), abs(cur.low - prevClose))
            )
            trs.add(tr)
        }
        val slice = trs.takeLast(period)
        return if (slice.isEmpty()) 0.0010 else slice.average()
    }

    fun computeSma(values: List<Double>, period: Int): List<Double> {
        if (values.isEmpty()) return emptyList()
        val p = period.coerceAtLeast(1)
        return List(values.size) { i ->
            val start = max(0, i - p + 1)
            values.subList(start, i + 1).average()
        }
    }

    fun computeAtrSeries(candles: List<FxCandle>, period: Int = 14): List<Double> {
        if (candles.isEmpty()) return emptyList()
        if (candles.size == 1) return listOf(max(candles[0].high - candles[0].low, 0.0001))
        val trs = ArrayList<Double>(candles.size)
        trs.add(max(candles[0].high - candles[0].low, 0.0001))
        for (i in 1 until candles.size) {
            val cur = candles[i]
            val prevClose = candles[i - 1].close
            val tr = max(
                cur.high - cur.low,
                max(abs(cur.high - prevClose), abs(cur.low - prevClose))
            )
            trs.add(tr)
        }
        val result = ArrayList<Double>(candles.size)
        var atr = trs.take(min(period, trs.size)).average()
        for (i in trs.indices) {
            atr = if (i < period) {
                trs.subList(0, i + 1).average()
            } else {
                ((atr * (period - 1)) + trs[i]) / period
            }
            result.add(atr)
        }
        return result
    }

    fun computeParabolicSar(
        candles: List<FxCandle>,
        step: Double = 0.02,
        maxAf: Double = 0.20
    ): List<Double> {
        if (candles.isEmpty()) return emptyList()
        if (candles.size == 1) return listOf(candles[0].low)

        val psar = MutableList(candles.size) { candles[0].low }
        var isBull = candles[1].close >= candles[0].close
        var af = step
        var ep = if (isBull) candles[0].high else candles[0].low
        var sar = if (isBull) candles[0].low else candles[0].high
        psar[0] = sar

        for (i in 1 until candles.size) {
            val cur = candles[i]
            val prev = candles[i - 1]
            var nextSar = sar + af * (ep - sar)

            if (isBull) {
                val floorLow = if (i >= 2) min(prev.low, candles[i - 2].low) else prev.low
                nextSar = min(nextSar, floorLow)
                if (cur.low < nextSar) {
                    isBull = false
                    nextSar = ep
                    ep = cur.low
                    af = step
                } else {
                    if (cur.high > ep) {
                        ep = cur.high
                        af = min(af + step, maxAf)
                    }
                }
            } else {
                val ceilHigh = if (i >= 2) max(prev.high, candles[i - 2].high) else prev.high
                nextSar = max(nextSar, ceilHigh)
                if (cur.high > nextSar) {
                    isBull = true
                    nextSar = ep
                    ep = cur.high
                    af = step
                } else {
                    if (cur.low < ep) {
                        ep = cur.low
                        af = min(af + step, maxAf)
                    }
                }
            }
            sar = nextSar
            psar[i] = sar
        }
        return psar
    }

    data class IchimokuResult(
        val tenkan: List<Double>,
        val kijun: List<Double>,
        val spanA: List<Double>,
        val spanB: List<Double>
    )

    fun computeIchimoku(candles: List<FxCandle>): IchimokuResult {
        if (candles.isEmpty()) return IchimokuResult(emptyList(), emptyList(), emptyList(), emptyList())
        fun midpointOver(idx: Int, lookback: Int): Double {
            val start = max(0, idx - lookback + 1)
            var hi = Double.NEGATIVE_INFINITY
            var lo = Double.POSITIVE_INFINITY
            for (j in start..idx) {
                if (candles[j].high > hi) hi = candles[j].high
                if (candles[j].low < lo) lo = candles[j].low
            }
            return (hi + lo) / 2.0
        }

        val tenkan = List(candles.size) { midpointOver(it, 9) }
        val kijun = List(candles.size) { midpointOver(it, 26) }
        val rawSpanA = List(candles.size) { (tenkan[it] + kijun[it]) / 2.0 }
        val rawSpanB = List(candles.size) { midpointOver(it, 52) }
        return IchimokuResult(tenkan, kijun, rawSpanA, rawSpanB)
    }

    fun computeStochastic(
        candles: List<FxCandle>,
        kPeriod: Int = 14,
        smoothK: Int = 3,
        dPeriod: Int = 3
    ): Pair<List<Double>, List<Double>> {
        if (candles.isEmpty()) return emptyList<Double>() to emptyList()
        val rawK = List(candles.size) { i ->
            val start = max(0, i - kPeriod + 1)
            var hi = Double.NEGATIVE_INFINITY
            var lo = Double.POSITIVE_INFINITY
            for (j in start..i) {
                if (candles[j].high > hi) hi = candles[j].high
                if (candles[j].low < lo) lo = candles[j].low
            }
            val span = hi - lo
            if (span <= 1e-9) 50.0 else ((candles[i].close - lo) / span * 100.0).coerceIn(0.0, 100.0)
        }
        val kSeries = computeSma(rawK, smoothK)
        val dSeries = computeSma(kSeries, dPeriod)
        return kSeries to dSeries
    }

    fun computeAdx(
        candles: List<FxCandle>,
        period: Int = 14
    ): Triple<List<Double>, List<Double>, List<Double>> {
        if (candles.isEmpty()) return Triple(emptyList(), emptyList(), emptyList())
        if (candles.size < 2) {
            return Triple(listOf(20.0), listOf(20.0), listOf(20.0))
        }
        val tr = MutableList(candles.size) { 0.0 }
        val plusDm = MutableList(candles.size) { 0.0 }
        val minusDm = MutableList(candles.size) { 0.0 }
        for (i in 1 until candles.size) {
            val cur = candles[i]
            val prev = candles[i - 1]
            tr[i] = max(cur.high - cur.low, max(abs(cur.high - prev.close), abs(cur.low - prev.close)))
            val upMove = cur.high - prev.high
            val downMove = prev.low - cur.low
            plusDm[i] = if (upMove > downMove && upMove > 0.0) upMove else 0.0
            minusDm[i] = if (downMove > upMove && downMove > 0.0) downMove else 0.0
        }

        val diPlus = MutableList(candles.size) { 20.0 }
        val diMinus = MutableList(candles.size) { 20.0 }
        val dx = MutableList(candles.size) { 20.0 }
        val adx = MutableList(candles.size) { 20.0 }

        var smoothTr = 0.0
        var smoothPlus = 0.0
        var smoothMinus = 0.0
        var smoothAdx = 20.0

        for (i in 1 until candles.size) {
            if (i <= period) {
                smoothTr += tr[i]
                smoothPlus += plusDm[i]
                smoothMinus += minusDm[i]
            } else {
                smoothTr = smoothTr - (smoothTr / period) + tr[i]
                smoothPlus = smoothPlus - (smoothPlus / period) + plusDm[i]
                smoothMinus = smoothMinus - (smoothMinus / period) + minusDm[i]
            }
            if (smoothTr > 1e-12) {
                val pdi = (100.0 * smoothPlus / smoothTr).coerceIn(0.0, 100.0)
                val mdi = (100.0 * smoothMinus / smoothTr).coerceIn(0.0, 100.0)
                diPlus[i] = pdi
                diMinus[i] = mdi
                val sumDi = pdi + mdi
                val curDx = if (sumDi > 1e-9) (100.0 * abs(pdi - mdi) / sumDi).coerceIn(0.0, 100.0) else 0.0
                dx[i] = curDx
                smoothAdx = if (i <= period) {
                    dx.subList(1, i + 1).average()
                } else {
                    ((smoothAdx * (period - 1)) + curDx) / period
                }
                adx[i] = smoothAdx.coerceIn(0.0, 100.0)
            }
        }
        return Triple(adx, diPlus, diMinus)
    }

    fun computePivotLevels(candles: List<FxCandle>): PivotLevels? {
        if (candles.size < 4) return null
        val sessionWindow = candles.dropLast(1).takeLast(24).ifEmpty { candles }
        val hi = sessionWindow.maxOf { it.high }
        val lo = sessionWindow.minOf { it.low }
        val close = sessionWindow.last().close
        val p = (hi + lo + close) / 3.0
        val range = hi - lo
        return PivotLevels(
            pivot = p,
            r1 = 2.0 * p - lo,
            r2 = p + range,
            s1 = 2.0 * p - hi,
            s2 = p - range
        )
    }
}
