package com.example.data.network

import android.util.Log
import com.example.model.FeedStatus
import com.example.model.ForexPair
import com.example.model.FxCandle
import com.example.model.PairQuote
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

data class MarketFeedState(
    val quotes: Map<ForexPair, PairQuote> = emptyMap(),
    val feedStatus: FeedStatus = FeedStatus.SYNCING,
    val lastEcbDate: String = "",
    val syncError: String? = null
)

class LiveMarketRepository(
    private val api: FrankfurterApiService = FrankfurterApiService.create()
) {
    private val _marketState = MutableStateFlow(MarketFeedState())
    val marketState: StateFlow<MarketFeedState> = _marketState.asStateFlow()

    private val ecbAnchorSpots = mutableMapOf<ForexPair, Double>()
    private val candlesByPair = mutableMapOf<ForexPair, MutableList<FxCandle>>()
    private val openAnchorByPair = mutableMapOf<ForexPair, Double>()
    private var tickCounter = 0L
    private var streamJob: Job? = null
    private val random = Random(System.currentTimeMillis())

    fun start(scope: CoroutineScope) {
        if (streamJob?.isActive == true) return
        streamJob = scope.launch {
            syncEcbGlobalRates()
            while (isActive) {
                delay(1800L)
                advanceIntradayTicks()
            }
        }
    }

    fun stop() {
        streamJob?.cancel()
        streamJob = null
    }

    suspend fun syncEcbGlobalRates() {
        withContext(Dispatchers.IO) {
            try {
                val cal = Calendar.getInstance()
                val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                val endDate = fmt.format(cal.time)
                cal.add(Calendar.DAY_OF_YEAR, -60)
                val startDate = fmt.format(cal.time)

                val latest = api.getLatestRates()
                val series = runCatching {
                    api.getTimeSeries("$startDate..$endDate")
                }.getOrNull()

                val sortedHistory = series?.rates
                    ?.toSortedMap()
                    ?.values
                    ?.toList()
                    .orEmpty()

                val now = System.currentTimeMillis()
                val newQuotes = LinkedHashMap<ForexPair, PairQuote>()

                ForexPair.entries.forEach { pair ->
                    val spot = computeSpotFromUsdRates(pair, latest.rates)
                        ?: defaultReferenceSpot(pair)
                    ecbAnchorSpots[pair] = spot

                    val historyRates = sortedHistory.mapNotNull { dayMap ->
                        computeSpotFromUsdRates(pair, dayMap)
                    }

                    val candles = buildIntradayCandlesFromHistory(pair, spot, historyRates, now)
                    candlesByPair[pair] = candles.toMutableList()
                    val sessionOpen = candles.firstOrNull()?.open ?: spot
                    openAnchorByPair[pair] = sessionOpen

                    val halfSpread = (pair.typicalSpreadPips * pair.pipSize) / 2.0
                    val bid = pair.roundToTick(spot - halfSpread)
                    val ask = pair.roundToTick(spot + halfSpread)
                    val changePct = if (sessionOpen > 0.0) ((spot - sessionOpen) / sessionOpen) * 100.0 else 0.0

                    newQuotes[pair] = PairQuote(
                        pair = pair,
                        bid = bid,
                        ask = ask,
                        mid = pair.roundToTick(spot),
                        changePercent = changePct,
                        candles = candles,
                        lastUpdatedMillis = now
                    )
                }

                _marketState.value = MarketFeedState(
                    quotes = newQuotes,
                    feedStatus = FeedStatus.LIVE_ECB_SYNCED,
                    lastEcbDate = latest.date,
                    syncError = null
                )
            } catch (e: Exception) {
                Log.w(TAG, "ECB API sync failed; using cached/reference global FX anchors", e)
                val now = System.currentTimeMillis()
                val fallbackQuotes = LinkedHashMap<ForexPair, PairQuote>()
                ForexPair.entries.forEach { pair ->
                    val spot = ecbAnchorSpots[pair] ?: defaultReferenceSpot(pair)
                    ecbAnchorSpots[pair] = spot
                    val candles = candlesByPair[pair] ?: buildIntradayCandlesFromHistory(pair, spot, emptyList(), now).also {
                        candlesByPair[pair] = it.toMutableList()
                    }
                    val sessionOpen = openAnchorByPair[pair] ?: candles.firstOrNull()?.open ?: spot
                    openAnchorByPair[pair] = sessionOpen
                    val halfSpread = (pair.typicalSpreadPips * pair.pipSize) / 2.0
                    val bid = pair.roundToTick(spot - halfSpread)
                    val ask = pair.roundToTick(spot + halfSpread)
                    val changePct = if (sessionOpen > 0.0) ((spot - sessionOpen) / sessionOpen) * 100.0 else 0.0
                    fallbackQuotes[pair] = PairQuote(
                        pair = pair,
                        bid = bid,
                        ask = ask,
                        mid = pair.roundToTick(spot),
                        changePercent = changePct,
                        candles = candles.toList(),
                        lastUpdatedMillis = now
                    )
                }
                _marketState.value = MarketFeedState(
                    quotes = fallbackQuotes,
                    feedStatus = FeedStatus.CACHED_OFFLINE,
                    lastEcbDate = _marketState.value.lastEcbDate,
                    syncError = e.localizedMessage ?: "Live ECB market feed unreachable"
                )
            }
        }
    }

    private fun advanceIntradayTicks() {
        val current = _marketState.value
        if (current.quotes.isEmpty()) return
        tickCounter++
        val now = System.currentTimeMillis()
        val updatedQuotes = LinkedHashMap<ForexPair, PairQuote>(current.quotes.size)

        ForexPair.entries.forEach { pair ->
            val existingQuote = current.quotes[pair] ?: return@forEach
            val anchor = ecbAnchorSpots[pair] ?: existingQuote.mid
            val candleList = candlesByPair.getOrPut(pair) { existingQuote.candles.toMutableList() }
            val lastCandle = candleList.lastOrNull() ?: FxCandle(
                now, existingQuote.mid, existingQuote.mid, existingQuote.mid, existingQuote.mid
            )

            // Mean-reverting intraday micro-structure tick around real ECB anchor
            val pip = pair.pipSize
            val meanReversionPull = (anchor - lastCandle.close) * 0.035
            val waveImpulse = sin((tickCounter + pair.ordinal * 7) * 0.28) * pip * 1.4
            val noise = (random.nextDouble() - 0.5) * pip * 3.2
            val rawNext = lastCandle.close + meanReversionPull + waveImpulse + noise
            // Keep intraday drift bounded within +/- 1.2% of ECB anchor
            val bounded = rawNext.coerceIn(anchor * 0.988, anchor * 1.012)
            val mid = pair.roundToTick(bounded)

            if (tickCounter % 5L == 0L) {
                val nextBar = FxCandle(
                    timestampMillis = now,
                    open = lastCandle.close,
                    high = max(lastCandle.close, mid),
                    low = min(lastCandle.close, mid),
                    close = mid
                )
                candleList.add(nextBar)
                if (candleList.size > 72) {
                    candleList.removeAt(0)
                }
            } else {
                val updatedBar = lastCandle.copy(
                    high = max(lastCandle.high, mid),
                    low = min(lastCandle.low, mid),
                    close = mid
                )
                candleList[candleList.lastIndex] = updatedBar
            }

            val halfSpread = (pair.typicalSpreadPips * pip) / 2.0
            val bid = pair.roundToTick(mid - halfSpread)
            val ask = pair.roundToTick(mid + halfSpread)
            val sessionOpen = openAnchorByPair[pair] ?: candleList.firstOrNull()?.open ?: mid
            val changePct = if (sessionOpen > 0.0) ((mid - sessionOpen) / sessionOpen) * 100.0 else 0.0

            updatedQuotes[pair] = PairQuote(
                pair = pair,
                bid = bid,
                ask = ask,
                mid = mid,
                changePercent = changePct,
                candles = candleList.toList(),
                lastUpdatedMillis = now
            )
        }

        _marketState.value = current.copy(quotes = updatedQuotes)
    }

    private fun computeSpotFromUsdRates(pair: ForexPair, usdRates: Map<String, Double>): Double? {
        return when (pair) {
            ForexPair.EUR_USD -> usdRates["EUR"]?.takeIf { it > 0 }?.let { 1.0 / it }
            ForexPair.GBP_USD -> usdRates["GBP"]?.takeIf { it > 0 }?.let { 1.0 / it }
            ForexPair.AUD_USD -> usdRates["AUD"]?.takeIf { it > 0 }?.let { 1.0 / it }
            ForexPair.USD_JPY -> usdRates["JPY"]?.takeIf { it > 0 }
            ForexPair.USD_CHF -> usdRates["CHF"]?.takeIf { it > 0 }
            ForexPair.USD_CAD -> usdRates["CAD"]?.takeIf { it > 0 }
            ForexPair.EUR_JPY -> {
                val eur = usdRates["EUR"]
                val jpy = usdRates["JPY"]
                if (eur != null && jpy != null && eur > 0) jpy / eur else null
            }
            ForexPair.GBP_JPY -> {
                val gbp = usdRates["GBP"]
                val jpy = usdRates["JPY"]
                if (gbp != null && jpy != null && gbp > 0) jpy / gbp else null
            }
        }
    }

    private fun buildIntradayCandlesFromHistory(
        pair: ForexPair,
        spot: Double,
        dailyHistory: List<Double>,
        nowMillis: Long
    ): List<FxCandle> {
        val barCount = 60
        val barMillis = 15 * 60 * 1000L
        val pip = pair.pipSize
        val avgDailyRange = if (dailyHistory.size >= 5) {
            dailyHistory.zipWithNext { a, b -> abs(b - a) }.average().coerceAtLeast(pip * 25.0)
        } else {
            pip * 45.0
        }
        val barStepVol = (avgDailyRange / 6.0).coerceIn(pip * 5.0, pip * 28.0)

        // Generate 60 realistic M15 bars ending cleanly at the real ECB spot price
        val closes = MutableList(barCount) { spot }
        var walk = spot
        for (i in barCount - 2 downTo 0) {
            val histAnchor = if (dailyHistory.isNotEmpty()) {
                val idx = ((i.toDouble() / barCount) * dailyHistory.lastIndex).toInt()
                    .coerceIn(0, dailyHistory.lastIndex)
                dailyHistory[idx]
            } else {
                spot
            }
            val drift = (histAnchor - walk) * 0.12
            val wave = sin((i + pair.ordinal * 5) * 0.35) * barStepVol * 0.55
            walk = (walk - drift - wave).coerceIn(spot * 0.96, spot * 1.04)
            closes[i] = pair.roundToTick(walk)
        }
        closes[barCount - 1] = pair.roundToTick(spot)

        return List(barCount) { idx ->
            val ts = nowMillis - (barCount - 1 - idx) * barMillis
            val open = if (idx == 0) pair.roundToTick(closes[0] - barStepVol * 0.2) else closes[idx - 1]
            val close = closes[idx]
            val wick = barStepVol * (0.35 + (idx % 3) * 0.15)
            val high = pair.roundToTick(max(open, close) + wick)
            val low = pair.roundToTick(min(open, close) - wick)
            FxCandle(ts, open, high, low, close)
        }
    }

    private fun defaultReferenceSpot(pair: ForexPair): Double = when (pair) {
        ForexPair.EUR_USD -> 1.06420
        ForexPair.GBP_USD -> 1.26840
        ForexPair.USD_JPY -> 154.320
        ForexPair.AUD_USD -> 0.65120
        ForexPair.USD_CHF -> 0.88460
        ForexPair.USD_CAD -> 1.40280
        ForexPair.EUR_JPY -> 164.220
        ForexPair.GBP_JPY -> 195.740
    }

    private companion object {
        const val TAG = "LiveMarketRepo"
    }
}
