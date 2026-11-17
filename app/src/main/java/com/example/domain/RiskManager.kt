package com.example.domain

import com.example.model.BotRiskConfig
import com.example.model.ExitReason
import com.example.model.ForexPair
import com.example.model.ForexTrade
import com.example.model.PairQuote
import com.example.model.PreTradeRiskCheck
import com.example.model.TechnicalAnalysisResult
import com.example.model.TradeSide
import com.example.model.TradingAccount
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

data class TradeExitEvaluation(
    val trade: ForexTrade,
    val exitPrice: Double,
    val realizedPnlUsd: Double,
    val realizedPips: Double,
    val exitReason: ExitReason
)

data class TrailingStopAdjustment(
    val trade: ForexTrade,
    val newStopLoss: Double,
    val newTrailingStopPrice: Double
)

enum class RiskSeverity {
    SAFE,
    ELEVATED,
    BREACHED
}

data class RealTimeTradeRisk(
    val liveRiskUsd: Double,
    val liveMarkToStopUsd: Double,
    val riskPercentOfEquity: Double,
    val maxAllowedRiskPercent: Double,
    val maxAllowedRiskUsd: Double,
    val projectedDailyDrawdownPercent: Double,
    val severity: RiskSeverity,
    val exceedsThreshold: Boolean,
    val badgeLabel: String?,
    val warningReasons: List<String>
)

object RiskManager {

    /**
     * Evaluates all 6 strict capital protection gates before allowing automated or manual execution.
     */
    fun evaluatePreTrade(
        account: TradingAccount,
        config: BotRiskConfig,
        analysis: TechnicalAnalysisResult,
        openTrades: List<ForexTrade>,
        usdJpySpot: Double,
        requireConfluenceGate: Boolean = true
    ): PreTradeRiskCheck {
        val pair = analysis.pair
        val stopDist = abs(analysis.exactEntryPrice - analysis.exactStopLoss)
        val tpDist = abs(analysis.exactTakeProfit - analysis.exactEntryPrice)
        val slPips = (stopDist / pair.pipSize).coerceAtLeast(1.0)
        val tpPips = (tpDist / pair.pipSize).coerceAtLeast(1.0)

        val currentEquity = account.accountBalance
        val pipValuePerLot = pair.pipValueUsdPerStandardLot(analysis.exactEntryPrice, usdJpySpot)
        val targetRiskUsd = currentEquity * (config.riskPerTradePercent / 100.0)
        val rawLots = targetRiskUsd / (slPips * pipValuePerLot)
        val lotSize = ((rawLots * 100.0).roundToInt() / 100.0).coerceIn(0.01, 50.0)
        val units = (lotSize * 100_000.0).roundToInt().coerceAtLeast(1000)
        val actualRiskUsd = lotSize * slPips * pipValuePerLot
        val rewardUsd = lotSize * tpPips * pipValuePerLot

        // Gate 1: Circuit breaker
        if (account.circuitBreakerTripped) {
            return PreTradeRiskCheck(
                approved = false,
                rejectionReason = "Daily Drawdown Circuit Breaker is active. Trading halted to protect capital.",
                calculatedLotSize = lotSize,
                calculatedUnits = units,
                riskAmountUsd = actualRiskUsd,
                rewardAmountUsd = rewardUsd,
                stopLossPips = slPips,
                takeProfitPips = tpPips
            )
        }

        // Gate 2: Max daily drawdown check
        val dailyLossLimit = account.dailyStartEquity * (config.maxDailyDrawdownPercent / 100.0)
        if (account.dailyRealizedPnl <= -dailyLossLimit) {
            return PreTradeRiskCheck(
                approved = false,
                rejectionReason = "Max daily drawdown limit (${config.maxDailyDrawdownPercent}%) reached.",
                calculatedLotSize = lotSize,
                calculatedUnits = units,
                riskAmountUsd = actualRiskUsd,
                rewardAmountUsd = rewardUsd,
                stopLossPips = slPips,
                takeProfitPips = tpPips
            )
        }

        // Gate 3: Sufficient equity
        if (currentEquity < 100.0) {
            return PreTradeRiskCheck(
                approved = false,
                rejectionReason = "Insufficient account equity ($${"%.2f".format(currentEquity)}) for margin.",
                calculatedLotSize = lotSize,
                calculatedUnits = units,
                riskAmountUsd = actualRiskUsd,
                rewardAmountUsd = rewardUsd,
                stopLossPips = slPips,
                takeProfitPips = tpPips
            )
        }

        // Gate 4: Max open concurrent positions
        if (openTrades.size >= config.maxOpenPositions.toInt()) {
            return PreTradeRiskCheck(
                approved = false,
                rejectionReason = "Max concurrent positions (${config.maxOpenPositions}) reached.",
                calculatedLotSize = lotSize,
                calculatedUnits = units,
                riskAmountUsd = actualRiskUsd,
                rewardAmountUsd = rewardUsd,
                stopLossPips = slPips,
                takeProfitPips = tpPips
            )
        }

        // Gate 5: Duplicate pair exposure guard
        if (openTrades.any { it.pair == pair.symbol }) {
            return PreTradeRiskCheck(
                approved = false,
                rejectionReason = "Active position already open on ${pair.symbol}.",
                calculatedLotSize = lotSize,
                calculatedUnits = units,
                riskAmountUsd = actualRiskUsd,
                rewardAmountUsd = rewardUsd,
                stopLossPips = slPips,
                takeProfitPips = tpPips
            )
        }

        // Gate 6: Minimum Risk:Reward & Technical Confluence
        val rr = tpDist / stopDist.coerceAtLeast(pair.pipSize)
        if (rr + 0.01 < config.minRiskRewardRatio) {
            return PreTradeRiskCheck(
                approved = false,
                rejectionReason = "Risk:Reward (${"%.2f".format(rr)}) below minimum ${config.minRiskRewardRatio}:1.",
                calculatedLotSize = lotSize,
                calculatedUnits = units,
                riskAmountUsd = actualRiskUsd,
                rewardAmountUsd = rewardUsd,
                stopLossPips = slPips,
                takeProfitPips = tpPips
            )
        }

        if (requireConfluenceGate && (analysis.suggestedSide == null || analysis.confluenceScore < config.minConfluenceScore.toInt())) {
            return PreTradeRiskCheck(
                approved = false,
                rejectionReason = "Confluence ${analysis.confluenceScore}/4 below required ${config.minConfluenceScore}/4.",
                calculatedLotSize = lotSize,
                calculatedUnits = units,
                riskAmountUsd = actualRiskUsd,
                rewardAmountUsd = rewardUsd,
                stopLossPips = slPips,
                takeProfitPips = tpPips
            )
        }

        return PreTradeRiskCheck(
            approved = true,
            rejectionReason = null,
            calculatedLotSize = lotSize,
            calculatedUnits = units,
            riskAmountUsd = actualRiskUsd,
            rewardAmountUsd = rewardUsd,
            stopLossPips = slPips,
            takeProfitPips = tpPips
        )
    }

    fun calculateUnrealizedPnl(
        trade: ForexTrade,
        quote: PairQuote,
        usdJpySpot: Double
    ): Pair<Double, Double> {
        val pair = ForexPair.fromSymbol(trade.pair)
        val side = TradeSide.safeValueOf(trade.side)
        val markPrice = if (side == TradeSide.BUY) quote.bid else quote.ask
        val priceDiff = if (side == TradeSide.BUY) {
            markPrice - trade.entryPrice
        } else {
            trade.entryPrice - markPrice
        }
        val pips = priceDiff / pair.pipSize
        val pipValuePerLot = pair.pipValueUsdPerStandardLot(markPrice, usdJpySpot)
        val pnlUsd = pips * pipValuePerLot * trade.lotSize
        return pnlUsd to pips
    }

    /**
     * Evaluates whether an open position has hit exact Take Profit, Stop Loss, Trailing Stop,
     * or Signal Reversal exit points.
     */
    fun evaluateTradeExit(
        trade: ForexTrade,
        quote: PairQuote,
        analysis: TechnicalAnalysisResult?,
        usdJpySpot: Double,
        forceCircuitBreaker: Boolean
    ): TradeExitEvaluation? {
        val pair = ForexPair.fromSymbol(trade.pair)
        val side = TradeSide.safeValueOf(trade.side)
        val markPrice = if (side == TradeSide.BUY) quote.bid else quote.ask

        if (forceCircuitBreaker) {
            val (pnl, pips) = calculateUnrealizedPnl(trade, quote, usdJpySpot)
            return TradeExitEvaluation(trade, pair.roundToTick(markPrice), pnl, pips, ExitReason.CIRCUIT_BREAKER)
        }

        val hitTakeProfit = if (side == TradeSide.BUY) {
            markPrice >= trade.takeProfit
        } else {
            markPrice <= trade.takeProfit
        }
        if (hitTakeProfit) {
            val exitPx = trade.takeProfit
            val pips = abs(exitPx - trade.entryPrice) / pair.pipSize
            val pnl = pips * pair.pipValueUsdPerStandardLot(exitPx, usdJpySpot) * trade.lotSize
            return TradeExitEvaluation(trade, exitPx, pnl, pips, ExitReason.TP_HIT)
        }

        val hitStopLoss = if (side == TradeSide.BUY) {
            markPrice <= trade.stopLoss
        } else {
            markPrice >= trade.stopLoss
        }
        if (hitStopLoss) {
            val exitPx = trade.stopLoss
            val isTrailing = trade.trailingStopPrice > 0.0
            val reason = if (isTrailing) ExitReason.TRAILING_SL else ExitReason.SL_HIT
            val signedDiff = if (side == TradeSide.BUY) exitPx - trade.entryPrice else trade.entryPrice - exitPx
            val pips = signedDiff / pair.pipSize
            val pnl = pips * pair.pipValueUsdPerStandardLot(exitPx, usdJpySpot) * trade.lotSize
            return TradeExitEvaluation(trade, exitPx, pnl, pips, reason)
        }

        if (analysis != null && analysis.suggestedSide != null &&
            analysis.suggestedSide != side && analysis.confluenceScore >= 3
        ) {
            val (pnl, pips) = calculateUnrealizedPnl(trade, quote, usdJpySpot)
            return TradeExitEvaluation(trade, pair.roundToTick(markPrice), pnl, pips, ExitReason.SIGNAL_REVERSAL)
        }

        return null
    }

    /**
     * Moves Stop Loss to breakeven at +breakEvenTriggerR and trails behind favorable price movement.
     */
    fun evaluateTrailingStop(
        trade: ForexTrade,
        quote: PairQuote,
        config: BotRiskConfig
    ): TrailingStopAdjustment? {
        if (!config.trailingStopEnabled) return null
        val pair = ForexPair.fromSymbol(trade.pair)
        val side = TradeSide.safeValueOf(trade.side)
        val markPrice = if (side == TradeSide.BUY) quote.bid else quote.ask
        val initialRiskDist = max(abs(trade.entryPrice - trade.stopLoss), pair.pipSize * 10.0)

        return if (side == TradeSide.BUY) {
            val favorableMove = markPrice - trade.entryPrice
            val triggerDist = initialRiskDist * config.breakEvenTriggerR
            if (favorableMove >= triggerDist) {
                val trailCandidate = pair.roundToTick(
                    max(trade.entryPrice + pair.pipSize * 1.0, markPrice - initialRiskDist * 0.85)
                )
                if (trailCandidate > trade.stopLoss + pair.pipSize * 1.5 && trailCandidate < markPrice) {
                    TrailingStopAdjustment(trade, trailCandidate, trailCandidate)
                } else null
            } else null
        } else {
            val favorableMove = trade.entryPrice - markPrice
            val triggerDist = initialRiskDist * config.breakEvenTriggerR
            if (favorableMove >= triggerDist) {
                val trailCandidate = pair.roundToTick(
                    minOf(trade.entryPrice - pair.pipSize * 1.0, markPrice + initialRiskDist * 0.85)
                )
                if (trailCandidate < trade.stopLoss - pair.pipSize * 1.5 && trailCandidate > markPrice) {
                    TrailingStopAdjustment(trade, trailCandidate, trailCandidate)
                } else null
            } else null
        }
    }

    fun computeDailyDrawdownPercent(account: TradingAccount, totalUnrealizedPnl: Double): Double {
        if (account.dailyStartEquity <= 0.0) return 0.0
        val netDailyPnl = account.dailyRealizedPnl + totalUnrealizedPnl
        return if (netDailyPnl < 0.0) {
            (abs(netDailyPnl) / account.dailyStartEquity) * 100.0
        } else {
            0.0
        }
    }

    /**
     * Calculates real-time capital risk for an active position against user-defined capital limits
     * (per-trade risk %, max daily drawdown %, and max concurrent positions) and flags threshold breaches.
     */
    fun calculateRealTimeTradeRisk(
        trade: ForexTrade,
        quote: PairQuote?,
        account: TradingAccount,
        config: BotRiskConfig,
        usdJpySpot: Double,
        totalOpenPositionsCount: Int = 1
    ): RealTimeTradeRisk {
        val pair = ForexPair.fromSymbol(trade.pair)
        val side = TradeSide.safeValueOf(trade.side)
        val markPrice = when {
            quote != null && side == TradeSide.BUY -> quote.bid
            quote != null && side == TradeSide.SELL -> quote.ask
            else -> trade.entryPrice
        }

        val (unrealizedUsd, _) = if (quote != null) {
            calculateUnrealizedPnl(trade, quote, usdJpySpot)
        } else {
            0.0 to 0.0
        }

        val pipValuePerLot = pair.pipValueUsdPerStandardLot(markPrice, usdJpySpot)

        // Distance from current live mark price to active Stop-Loss (in USD)
        val adverseMarkToStopPips = if (side == TradeSide.BUY) {
            max(0.0, (markPrice - trade.stopLoss) / pair.pipSize)
        } else {
            max(0.0, (trade.stopLoss - markPrice) / pair.pipSize)
        }
        val liveMarkToStopUsd = adverseMarkToStopPips * pipValuePerLot * trade.lotSize

        // Check if trailing/breakeven stop has locked in profit above/below entry
        val protectedByTrailingLock = if (side == TradeSide.BUY) {
            trade.stopLoss >= trade.entryPrice
        } else {
            trade.stopLoss <= trade.entryPrice
        }

        // Worst-case capital loss from entry if Stop-Loss hits right now (or current drawdown if gapped past SL)
        val entryToStopPips = if (side == TradeSide.BUY) {
            max(0.0, (trade.entryPrice - trade.stopLoss) / pair.pipSize)
        } else {
            max(0.0, (trade.stopLoss - trade.entryPrice) / pair.pipSize)
        }
        val entryStopRiskUsd = entryToStopPips * pipValuePerLot * trade.lotSize
        val currentLossUsd = if (unrealizedUsd < 0.0) abs(unrealizedUsd) else 0.0

        val worstCaseCapitalRiskUsd = if (protectedByTrailingLock && currentLossUsd == 0.0) {
            0.0
        } else {
            max(entryStopRiskUsd, currentLossUsd)
        }

        val equity = account.accountBalance.coerceAtLeast(1.0)
        val maxAllowedRiskUsd = equity * (config.riskPerTradePercent / 100.0)
        val riskPercentOfEquity = (worstCaseCapitalRiskUsd / equity) * 100.0

        val dailyBaseline = account.dailyStartEquity.coerceAtLeast(equity)
        val projectedDailyNet = account.dailyRealizedPnl - worstCaseCapitalRiskUsd
        val projectedDailyDrawdownPercent = if (projectedDailyNet < 0.0) {
            (abs(projectedDailyNet) / dailyBaseline) * 100.0
        } else {
            0.0
        }

        val warnings = mutableListOf<String>()
        var severity = RiskSeverity.SAFE

        // 1. Per-trade capital risk limit check (user-defined riskPerTradePercent)
        val perTradeLimitExceeded = riskPercentOfEquity > config.riskPerTradePercent + 0.05
        val perTradeNearLimit = !perTradeLimitExceeded &&
            riskPercentOfEquity >= config.riskPerTradePercent * 0.85

        if (perTradeLimitExceeded) {
            severity = RiskSeverity.BREACHED
            warnings.add(
                "Risk ${"%.2f".format(java.util.Locale.US, riskPercentOfEquity)}% exceeds ${
                    "%.1f".format(java.util.Locale.US, config.riskPerTradePercent)
                }% cap"
            )
        } else if (perTradeNearLimit) {
            severity = RiskSeverity.ELEVATED
            warnings.add(
                "Risk ${"%.2f".format(java.util.Locale.US, riskPercentOfEquity)}% near ${
                    "%.1f".format(java.util.Locale.US, config.riskPerTradePercent)
                }% cap"
            )
        }

        // 2. Daily drawdown limit projection check (user-defined maxDailyDrawdownPercent)
        val drawdownLimitExceeded = projectedDailyDrawdownPercent >= config.maxDailyDrawdownPercent
        val drawdownNearLimit = !drawdownLimitExceeded &&
            projectedDailyDrawdownPercent >= config.maxDailyDrawdownPercent * 0.80

        if (drawdownLimitExceeded) {
            severity = RiskSeverity.BREACHED
            warnings.add(
                "SL hit projects ${"%.2f".format(java.util.Locale.US, projectedDailyDrawdownPercent)}% daily DD (>= ${
                    "%.1f".format(java.util.Locale.US, config.maxDailyDrawdownPercent)
                }%)"
            )
        } else if (drawdownNearLimit) {
            if (severity == RiskSeverity.SAFE) severity = RiskSeverity.ELEVATED
            warnings.add(
                "SL hit reaches ${"%.2f".format(java.util.Locale.US, projectedDailyDrawdownPercent)}% daily DD"
            )
        }

        // 3. Underwater close-to-stop warning (> 75% of risk budget already lost)
        if (!protectedByTrailingLock && worstCaseCapitalRiskUsd > 0.0 && currentLossUsd >= worstCaseCapitalRiskUsd * 0.75) {
            if (severity == RiskSeverity.SAFE) severity = RiskSeverity.ELEVATED
            warnings.add("Near Stop-Loss (${((currentLossUsd / worstCaseCapitalRiskUsd) * 100.0).roundToInt()}% risk used)")
        }

        // 4. Max open positions limit breached
        if (totalOpenPositionsCount > config.maxOpenPositions.toInt()) {
            severity = RiskSeverity.BREACHED
            warnings.add("Open slots ($totalOpenPositionsCount) exceed max ${config.maxOpenPositions}")
        }

        val exceedsThreshold = severity != RiskSeverity.SAFE
        val badgeLabel = when {
            perTradeLimitExceeded -> "RISK ${"%.2f".format(java.util.Locale.US, riskPercentOfEquity)}% > ${
                "%.1f".format(java.util.Locale.US, config.riskPerTradePercent)
            }%"
            drawdownLimitExceeded -> "DD LIMIT BREACH"
            totalOpenPositionsCount > config.maxOpenPositions.toInt() -> "SLOT CAP EXCEEDED"
            severity == RiskSeverity.ELEVATED && currentLossUsd >= worstCaseCapitalRiskUsd * 0.75 && worstCaseCapitalRiskUsd > 0.0 -> "NEAR STOP-LOSS"
            severity == RiskSeverity.ELEVATED -> "HIGH RISK ${"%.2f".format(java.util.Locale.US, riskPercentOfEquity)}%"
            else -> null
        }

        return RealTimeTradeRisk(
            liveRiskUsd = worstCaseCapitalRiskUsd,
            liveMarkToStopUsd = liveMarkToStopUsd,
            riskPercentOfEquity = riskPercentOfEquity,
            maxAllowedRiskPercent = config.riskPerTradePercent,
            maxAllowedRiskUsd = maxAllowedRiskUsd,
            projectedDailyDrawdownPercent = projectedDailyDrawdownPercent,
            severity = severity,
            exceedsThreshold = exceedsThreshold,
            badgeLabel = badgeLabel,
            warningReasons = warnings
        )
    }
}
