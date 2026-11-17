package com.example

import com.example.domain.RiskManager
import com.example.domain.RiskSeverity
import com.example.domain.TechnicalAnalysisEngine
import com.example.model.BotRiskConfig
import com.example.model.ExitReason
import com.example.model.ForexPair
import com.example.model.ForexTrade
import com.example.model.FxCandle
import com.example.model.PairQuote
import com.example.model.TradeSide
import com.example.model.TradingAccount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TradingEngineUnitTest {

    private fun buildTrendingCandles(count: Int = 60, bullish: Boolean = true): List<FxCandle> {
        var price = 1.08000
        return List(count) { idx ->
            val step = when {
                !bullish -> -0.00045
                idx < 35 -> -0.00015
                idx < 48 -> if (idx % 2 == 0) 0.00012 else -0.00014
                else -> if (idx % 3 == 0) -0.00008 else 0.00022
            }
            val open = price
            val close = price + step
            val high = maxOf(open, close) + 0.00025
            val low = minOf(open, close) - 0.00025
            price = close
            FxCandle(timestampMillis = 1_700_000_000_000L + idx * 900_000L, open, high, low, close)
        }
    }

    @Test
    fun technicalAnalysisComputesExactEntryStopLossTakeProfitWithMinRR() {
        val candles = buildTrendingCandles(60, bullish = true)
        val spot = candles.last().close
        val config = BotRiskConfig(
            minRiskRewardRatio = 2.5,
            atrStopMultiplier = 1.5,
            emaFastPeriod = 9L,
            emaSlowPeriod = 21L
        )
        val result = TechnicalAnalysisEngine.analyze(
            pair = ForexPair.EUR_USD,
            candles = candles,
            bid = spot - 0.00004,
            ask = spot + 0.00004,
            config = config
        )

        assertTrue(result.latestRsi in 0.0..100.0)
        assertTrue("Expected EMA9 > EMA21, got ${result.latestEmaFast} vs ${result.latestEmaSlow}", result.latestEmaFast > result.latestEmaSlow)
        assertEquals(
            "Expected BUY, got ${result.suggestedSide} (${result.signalSummary}, ema=${result.emaVote}, rsi=${result.rsiVote}, macd=${result.macdVote}, bb=${result.bbVote})",
            TradeSide.BUY,
            result.suggestedSide
        )
        assertTrue(result.exactStopLoss < result.exactEntryPrice)
        assertTrue(result.exactTakeProfit > result.exactEntryPrice)

        val slDist = result.exactEntryPrice - result.exactStopLoss
        val tpDist = result.exactTakeProfit - result.exactEntryPrice
        assertEquals(2.5, tpDist / slDist, 0.05)
    }

    @Test
    fun riskManagerSizesLotsAccuratelyAndBlocksCircuitBreaker() {
        val candles = buildTrendingCandles(60, bullish = true)
        val spot = candles.last().close
        val config = BotRiskConfig(
            riskPerTradePercent = 1.0,
            maxDailyDrawdownPercent = 3.0,
            maxOpenPositions = 2L,
            minRiskRewardRatio = 2.0,
            minConfluenceScore = 2L
        )
        val analysis = TechnicalAnalysisEngine.analyze(
            pair = ForexPair.EUR_USD,
            candles = candles,
            bid = spot - 0.00004,
            ask = spot + 0.00004,
            config = config
        )

        val healthyAccount = TradingAccount(
            userId = "u1",
            accountBalance = 25_000.0,
            dailyStartEquity = 25_000.0,
            dailyRealizedPnl = 0.0,
            circuitBreakerTripped = false
        )
        val approvedCheck = RiskManager.evaluatePreTrade(
            account = healthyAccount,
            config = config,
            analysis = analysis,
            openTrades = emptyList(),
            usdJpySpot = 154.0,
            requireConfluenceGate = true
        )
        assertTrue("Expected approved, got reason=${approvedCheck.rejectionReason}, side=${analysis.suggestedSide}", approvedCheck.approved)
        // 1% of $25,000 = $250 target risk
        assertEquals(250.0, approvedCheck.riskAmountUsd, 20.0)
        assertTrue(approvedCheck.calculatedLotSize >= 0.01)

        val trippedAccount = healthyAccount.copy(circuitBreakerTripped = true)
        val blockedCheck = RiskManager.evaluatePreTrade(
            account = trippedAccount,
            config = config,
            analysis = analysis,
            openTrades = emptyList(),
            usdJpySpot = 154.0,
            requireConfluenceGate = true
        )
        assertFalse(blockedCheck.approved)
        assertNotNull(blockedCheck.rejectionReason)
    }

    @Test
    fun riskManagerDetectsTakeProfitAndTrailingStopRatchet() {
        val trade = ForexTrade(
            id = "t1",
            userId = "u1",
            pair = "EUR/USD",
            side = TradeSide.BUY.name,
            lotSize = 1.0,
            entryPrice = 1.08000,
            stopLoss = 1.07800,
            takeProfit = 1.08400
        )
        val config = BotRiskConfig(trailingStopEnabled = true, breakEvenTriggerR = 1.0)

        // Price moves +25 pips (> +1R = +20 pips) -> trailing stop should move above entry
        val midQuote = PairQuote(
            pair = ForexPair.EUR_USD,
            bid = 1.08250,
            ask = 1.08258,
            mid = 1.08254,
            changePercent = 0.2,
            candles = emptyList(),
            lastUpdatedMillis = 1L
        )
        val trail = RiskManager.evaluateTrailingStop(trade, midQuote, config)
        assertNotNull(trail)
        assertTrue(trail!!.newStopLoss > trade.entryPrice)

        // Price hits Take Profit 1.08400 -> exact TP_HIT exit
        val tpQuote = midQuote.copy(bid = 1.08405, ask = 1.08413, mid = 1.08409)
        val exitEval = RiskManager.evaluateTradeExit(
            trade = trade,
            quote = tpQuote,
            analysis = null,
            usdJpySpot = 154.0,
            forceCircuitBreaker = false
        )
        assertNotNull(exitEval)
        assertEquals(ExitReason.TP_HIT, exitEval!!.exitReason)
        assertEquals(1.08400, exitEval.exitPrice, 0.00001)
        assertEquals(400.0, exitEval.realizedPnlUsd, 1.0)

        // Price within bounds -> remains open
        val openQuote = midQuote.copy(bid = 1.08100, ask = 1.08108, mid = 1.08104)
        assertNull(
            RiskManager.evaluateTradeExit(
                trade = trade,
                quote = openQuote,
                analysis = null,
                usdJpySpot = 154.0,
                forceCircuitBreaker = false
            )
        )
    }

    @Test
    fun calculateRealTimeTradeRiskFlagsBreachesElevatedSafeAndTrailingLock() {
        val account = TradingAccount(
            userId = "u1",
            accountBalance = 10_000.0,
            dailyStartEquity = 10_000.0,
            dailyRealizedPnl = 0.0
        )
        val config = BotRiskConfig(
            riskPerTradePercent = 1.0,
            maxDailyDrawdownPercent = 3.0,
            maxOpenPositions = 3L
        )
        val quote = PairQuote(
            pair = ForexPair.EUR_USD,
            bid = 1.08020,
            ask = 1.08028,
            mid = 1.08024,
            changePercent = 0.1,
            candles = emptyList(),
            lastUpdatedMillis = 1L
        )

        // 1. Safe trade: 0.4 lots * 20 pips * $10/pip = $80 risk = 0.80% (< 85% of 1.0% cap)
        val safeTrade = ForexTrade(
            id = "safe_1",
            userId = "u1",
            pair = "EUR/USD",
            side = TradeSide.BUY.name,
            lotSize = 0.4,
            entryPrice = 1.08000,
            stopLoss = 1.07800,
            takeProfit = 1.08400
        )
        val safeRisk = RiskManager.calculateRealTimeTradeRisk(safeTrade, quote, account, config, 154.0, 1)
        assertEquals(RiskSeverity.SAFE, safeRisk.severity)
        assertFalse(safeRisk.exceedsThreshold)
        assertNull(safeRisk.badgeLabel)
        assertEquals(0.80, safeRisk.riskPercentOfEquity, 0.02)

        // 2. Breached per-trade capital limit: 1.0 lot * 20 pips = $200 = 2.00% (> 1.0% cap)
        val breachedTrade = safeTrade.copy(id = "breach_1", lotSize = 1.0)
        val breachedRisk = RiskManager.calculateRealTimeTradeRisk(breachedTrade, quote, account, config, 154.0, 1)
        assertEquals(RiskSeverity.BREACHED, breachedRisk.severity)
        assertTrue(breachedRisk.exceedsThreshold)
        assertNotNull(breachedRisk.badgeLabel)
        assertEquals(2.00, breachedRisk.riskPercentOfEquity, 0.02)

        // 3. Elevated warning: underwater close to SL (> 75% of risk budget lost)
        val nearSlQuote = quote.copy(bid = 1.07840, ask = 1.07848, mid = 1.07844)
        val elevatedRisk = RiskManager.calculateRealTimeTradeRisk(safeTrade, nearSlQuote, account, config, 154.0, 1)
        assertEquals(RiskSeverity.ELEVATED, elevatedRisk.severity)
        assertTrue(elevatedRisk.exceedsThreshold)
        assertEquals("NEAR STOP-LOSS", elevatedRisk.badgeLabel)

        // 4. Trailing stop locked above entry while in profit -> 0% capital loss risk
        val trailedTrade = breachedTrade.copy(stopLoss = 1.08010, trailingStopPrice = 1.08010)
        val profitQuote = quote.copy(bid = 1.08150, ask = 1.08158, mid = 1.08154)
        val lockedRisk = RiskManager.calculateRealTimeTradeRisk(trailedTrade, profitQuote, account, config, 154.0, 1)
        assertEquals(RiskSeverity.SAFE, lockedRisk.severity)
        assertFalse(lockedRisk.exceedsThreshold)
        assertEquals(0.0, lockedRisk.liveRiskUsd, 0.01)
    }

    @Test
    fun allTechnicalIndicatorsAndPivotsPopulateValidSeries() {
        val candles = buildTrendingCandles(64, bullish = true)
        val spot = candles.last().close
        val result = TechnicalAnalysisEngine.analyze(
            pair = ForexPair.EUR_USD,
            candles = candles,
            bid = spot - 0.00004,
            ask = spot + 0.00004,
            config = BotRiskConfig()
        )

        assertEquals(candles.size, result.sma50Series.size)
        assertEquals(candles.size, result.psarSeries.size)
        assertEquals(candles.size, result.ichimokuTenkanSeries.size)
        assertEquals(candles.size, result.ichimokuKijunSeries.size)
        assertEquals(candles.size, result.ichimokuSpanASeries.size)
        assertEquals(candles.size, result.ichimokuSpanBSeries.size)
        assertEquals(candles.size, result.stochKSeries.size)
        assertEquals(candles.size, result.stochDSeries.size)
        assertEquals(candles.size, result.atrSeries.size)
        assertEquals(candles.size, result.adxSeries.size)
        assertEquals(candles.size, result.diPlusSeries.size)
        assertEquals(candles.size, result.diMinusSeries.size)

        assertTrue(result.latestSma50 > 0.0)
        assertTrue(result.latestPsar > 0.0)
        assertTrue(result.latestStochK in 0.0..100.0)
        assertTrue(result.latestStochD in 0.0..100.0)
        assertTrue(result.latestAdx in 0.0..100.0)
        assertTrue(result.latestDiPlus in 0.0..100.0)
        assertTrue(result.latestDiMinus in 0.0..100.0)
        assertTrue(result.atrValue > 0.0)
        assertTrue(result.atrSeries.last() > 0.0)

        assertNotNull(result.pivotLevels)
        val pivots = result.pivotLevels!!
        assertTrue(pivots.s2 < pivots.s1)
        assertTrue(pivots.s1 < pivots.pivot)
        assertTrue(pivots.pivot < pivots.r1)
        assertTrue(pivots.r1 < pivots.r2)
    }
}

