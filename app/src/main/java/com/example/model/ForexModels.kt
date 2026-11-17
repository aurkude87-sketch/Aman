package com.example.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import kotlin.math.pow
import kotlin.math.roundToInt

enum class ForexPair(
    val symbol: String,
    val base: String,
    val quote: String,
    val pipSize: Double,
    val decimals: Int,
    val typicalSpreadPips: Double,
    val sessionLabel: String
) {
    EUR_USD("EUR/USD", "EUR", "USD", 0.0001, 5, 0.8, "London • NY"),
    GBP_USD("GBP/USD", "GBP", "USD", 0.0001, 5, 1.1, "London • NY"),
    USD_JPY("USD/JPY", "USD", "JPY", 0.01, 3, 0.9, "Tokyo • NY"),
    AUD_USD("AUD/USD", "AUD", "USD", 0.0001, 5, 1.0, "Sydney • NY"),
    USD_CHF("USD/CHF", "USD", "CHF", 0.0001, 5, 1.2, "Zurich • NY"),
    USD_CAD("USD/CAD", "USD", "CAD", 0.0001, 5, 1.2, "Toronto • NY"),
    EUR_JPY("EUR/JPY", "EUR", "JPY", 0.01, 3, 1.3, "Frankfurt • Tokyo"),
    GBP_JPY("GBP/JPY", "GBP", "JPY", 0.01, 3, 1.6, "London • Tokyo");

    /**
     * Exact USD value of 1 pip for 1 standard lot (100,000 base units).
     */
    fun pipValueUsdPerStandardLot(currentSpotPrice: Double, usdJpySpot: Double = 154.0): Double {
        return when {
            quote == "USD" -> 10.0
            base == "USD" && currentSpotPrice > 0.0 -> (100_000.0 * pipSize) / currentSpotPrice
            quote == "JPY" && usdJpySpot > 0.0 -> (100_000.0 * pipSize) / usdJpySpot
            else -> 10.0
        }
    }

    fun formatPrice(price: Double): String {
        return "%.${decimals}f".format(java.util.Locale.US, price)
    }

    fun roundToTick(price: Double): Double {
        val factor = 10.0.pow(decimals)
        return (price * factor).roundToInt() / factor
    }

    companion object {
        fun fromSymbol(symbol: String): ForexPair {
            return entries.firstOrNull { it.symbol == symbol } ?: EUR_USD
        }
    }
}

enum class TradeSide {
    BUY,
    SELL;

    companion object {
        fun safeValueOf(raw: String): TradeSide =
            entries.firstOrNull { it.name == raw } ?: BUY
    }
}

enum class TradeStatus {
    OPEN,
    CLOSED;

    companion object {
        fun safeValueOf(raw: String): TradeStatus =
            entries.firstOrNull { it.name == raw } ?: OPEN
    }
}

enum class ExitReason(val label: String) {
    ACTIVE("Active"),
    TP_HIT("Take Profit Hit"),
    SL_HIT("Stop Loss Hit"),
    TRAILING_SL("Trailing Stop Hit"),
    SIGNAL_REVERSAL("Signal Reversal"),
    MANUAL_CLOSE("Manual Close"),
    CIRCUIT_BREAKER("Drawdown Kill-Switch");

    companion object {
        fun safeValueOf(raw: String): ExitReason =
            entries.firstOrNull { it.name == raw } ?: ACTIVE
    }
}

data class FxCandle(
    val timestampMillis: Long,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double
)

enum class FeedStatus {
    SYNCING,
    LIVE_ECB_SYNCED,
    CACHED_OFFLINE
}

data class PairQuote(
    val pair: ForexPair,
    val bid: Double,
    val ask: Double,
    val mid: Double,
    val changePercent: Double,
    val candles: List<FxCandle>,
    val lastUpdatedMillis: Long
)

enum class IndicatorVote {
    BULLISH,
    BEARISH,
    NEUTRAL
}

data class PivotLevels(
    val pivot: Double,
    val r1: Double,
    val r2: Double,
    val s1: Double,
    val s2: Double
)

enum class ChartOverlayIndicator(val label: String) {
    EMA("EMA"),
    SMA_50("SMA 50"),
    BOLLINGER("BB(20,2)"),
    ICHIMOKU("Ichimoku"),
    PSAR("PSAR"),
    PIVOTS("Pivots")
}

enum class OscillatorPane(val label: String) {
    RSI("RSI(14)"),
    MACD("MACD"),
    STOCHASTIC("Stoch"),
    ADX("ADX(14)"),
    ATR("ATR(14)")
}

enum class ChartMarkingTool(val label: String, val isTwoPointTool: Boolean) {
    BROWSE("Browse", false),
    CROSSHAIR("Crosshair", false),
    HORIZONTAL_LINE("H-Line", false),
    TREND_LINE("Trendline", true),
    FIB_RETRACEMENT("Fibonacci", true),
    RECTANGLE_ZONE("Zone Box", true),
    RR_PROJECTION("R:R Ruler", true),
    PRICE_PIN("Pin", false)
}

data class ChartAnchor(
    val candleTimestampMillis: Long,
    val fallbackIndex: Int,
    val price: Double
)

sealed interface ChartDrawing {
    val id: String

    data class HorizontalLine(
        override val id: String,
        val price: Double,
        val label: String = "S/R"
    ) : ChartDrawing

    data class TrendLine(
        override val id: String,
        val start: ChartAnchor,
        val end: ChartAnchor
    ) : ChartDrawing

    data class FibonacciRetracement(
        override val id: String,
        val start: ChartAnchor,
        val end: ChartAnchor
    ) : ChartDrawing

    data class ZoneBox(
        override val id: String,
        val start: ChartAnchor,
        val end: ChartAnchor
    ) : ChartDrawing

    data class RiskRewardBox(
        override val id: String,
        val entry: ChartAnchor,
        val stop: ChartAnchor
    ) : ChartDrawing

    data class PricePin(
        override val id: String,
        val anchor: ChartAnchor,
        val isBullish: Boolean
    ) : ChartDrawing
}

data class TechnicalAnalysisResult(
    val pair: ForexPair,
    val emaFastSeries: List<Double>,
    val emaSlowSeries: List<Double>,
    val bbUpperSeries: List<Double>,
    val bbMiddleSeries: List<Double>,
    val bbLowerSeries: List<Double>,
    val rsiSeries: List<Double>,
    val macdHistogramSeries: List<Double>,
    val latestEmaFast: Double,
    val latestEmaSlow: Double,
    val latestRsi: Double,
    val macdLine: Double,
    val macdSignalLine: Double,
    val macdHistogram: Double,
    val bbPercentB: Double,
    val atrValue: Double,
    val atrPips: Double,
    val emaVote: IndicatorVote,
    val rsiVote: IndicatorVote,
    val macdVote: IndicatorVote,
    val bbVote: IndicatorVote,
    val confluenceScore: Int,
    val suggestedSide: TradeSide?,
    val exactEntryPrice: Double,
    val exactStopLoss: Double,
    val exactTakeProfit: Double,
    val riskRewardRatio: Double,
    val signalSummary: String,
    val sma50Series: List<Double> = emptyList(),
    val psarSeries: List<Double> = emptyList(),
    val ichimokuTenkanSeries: List<Double> = emptyList(),
    val ichimokuKijunSeries: List<Double> = emptyList(),
    val ichimokuSpanASeries: List<Double> = emptyList(),
    val ichimokuSpanBSeries: List<Double> = emptyList(),
    val macdLineSeries: List<Double> = emptyList(),
    val macdSignalSeries: List<Double> = emptyList(),
    val stochKSeries: List<Double> = emptyList(),
    val stochDSeries: List<Double> = emptyList(),
    val atrSeries: List<Double> = emptyList(),
    val adxSeries: List<Double> = emptyList(),
    val diPlusSeries: List<Double> = emptyList(),
    val diMinusSeries: List<Double> = emptyList(),
    val latestSma50: Double = 0.0,
    val latestPsar: Double = 0.0,
    val latestStochK: Double = 50.0,
    val latestStochD: Double = 50.0,
    val latestAdx: Double = 20.0,
    val latestDiPlus: Double = 20.0,
    val latestDiMinus: Double = 20.0,
    val pivotLevels: PivotLevels? = null
)

data class PreTradeRiskCheck(
    val approved: Boolean,
    val rejectionReason: String?,
    val calculatedLotSize: Double,
    val calculatedUnits: Int,
    val riskAmountUsd: Double,
    val rewardAmountUsd: Double,
    val stopLossPips: Double,
    val takeProfitPips: Double
)

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

@Keep
@IgnoreExtraProperties
data class TradingAccount(
    val userId: String = "",
    val displayName: String = "FX Trader",
    val accountBalance: Double = 25000.0,
    val startingBalance: Double = 25000.0,
    val dailyStartEquity: Double = 25000.0,
    val dailyRealizedPnl: Double = 0.0,
    val realizedPnl: Double = 0.0,
    val totalTrades: Long = 0L,
    val winningTrades: Long = 0L,
    val botArmed: Boolean = false,
    val circuitBreakerTripped: Boolean = false,
    val executionMode: String = "PAPER",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class BotRiskConfig(
    @DocumentId val id: String = "",
    val userId: String = "",
    val riskPerTradePercent: Double = 1.0,
    val maxDailyDrawdownPercent: Double = 3.0,
    val maxOpenPositions: Long = 3L,
    val minRiskRewardRatio: Double = 2.0,
    val atrStopMultiplier: Double = 1.5,
    val trailingStopEnabled: Boolean = true,
    val breakEvenTriggerR: Double = 1.0,
    val emaFastPeriod: Long = 9L,
    val emaSlowPeriod: Long = 21L,
    val rsiPeriod: Long = 14L,
    val rsiOversold: Double = 30.0,
    val rsiOverbought: Double = 70.0,
    val minConfluenceScore: Long = 3L,
    val watchedPairs: List<String> = listOf(
        "EUR/USD",
        "GBP/USD",
        "USD/JPY",
        "AUD/USD",
        "USD/CHF",
        "USD/CAD"
    ),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class ForexTrade(
    @DocumentId val id: String = "",
    val userId: String = "",
    val pair: String = "EUR/USD",
    val side: String = TradeSide.BUY.name,
    val status: String = TradeStatus.OPEN.name,
    val lotSize: Double = 0.01,
    val units: Long = 1000L,
    val entryPrice: Double = 0.0,
    val stopLoss: Double = 0.0,
    val takeProfit: Double = 0.0,
    val trailingStopPrice: Double = 0.0,
    val exitPrice: Double = 0.0,
    val riskAmountUsd: Double = 0.0,
    val riskRewardRatio: Double = 2.0,
    val realizedPnlUsd: Double = 0.0,
    val realizedPips: Double = 0.0,
    val exitReason: String = ExitReason.ACTIVE.name,
    val signalSummary: String = "",
    val confluenceScore: Long = 3L,
    val timeframe: String = "M15",
    val openedAtMillis: Long = 0L,
    val closedAtMillis: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)
