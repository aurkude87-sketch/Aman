package com.example.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.firebase.TradingRepository
import com.example.data.network.LiveMarketRepository
import com.example.data.network.MarketFeedState
import com.example.domain.RealTimeTradeRisk
import com.example.domain.RiskManager
import com.example.domain.RiskSeverity
import com.example.domain.TechnicalAnalysisEngine
import com.example.model.BotRiskConfig
import com.example.model.ChartDrawing
import com.example.model.ChartMarkingTool
import com.example.model.ChartOverlayIndicator
import com.example.model.ExitReason
import com.example.model.FeedStatus
import com.example.model.ForexPair
import com.example.model.ForexTrade
import com.example.model.OscillatorPane
import com.example.model.PairQuote
import com.example.model.PreTradeRiskCheck
import com.example.model.TechnicalAnalysisResult
import com.example.model.TradeSide
import com.example.model.TradeStatus
import com.example.model.TradingAccount
import com.example.model.UiState
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class TradeHistorySort(val label: String) {
    NEWEST_FIRST("Newest"),
    OLDEST_FIRST("Oldest"),
    HIGHEST_PNL("Top Profit"),
    LOWEST_PNL("Top Loss")
}

data class BotExecutionEvent(
    val message: String,
    val isPositive: Boolean,
    val timestampMillis: Long = System.currentTimeMillis()
)

data class OpenTradeLiveItem(
    val trade: ForexTrade,
    val currentPrice: Double,
    val unrealizedPnlUsd: Double,
    val unrealizedPips: Double,
    val progressToTp: Float,
    val realTimeRisk: RealTimeTradeRisk = RealTimeTradeRisk(
        liveRiskUsd = 0.0,
        liveMarkToStopUsd = 0.0,
        riskPercentOfEquity = 0.0,
        maxAllowedRiskPercent = 1.0,
        maxAllowedRiskUsd = 250.0,
        projectedDailyDrawdownPercent = 0.0,
        severity = RiskSeverity.SAFE,
        exceedsThreshold = false,
        badgeLabel = null,
        warningReasons = emptyList()
    )
)

data class TerminalSnapshot(
    val account: TradingAccount = TradingAccount(),
    val config: BotRiskConfig = BotRiskConfig(),
    val selectedPair: ForexPair = ForexPair.EUR_USD,
    val quotes: Map<ForexPair, PairQuote> = emptyMap(),
    val analyses: Map<ForexPair, TechnicalAnalysisResult> = emptyMap(),
    val selectedAnalysis: TechnicalAnalysisResult? = null,
    val selectedPreTradeCheck: PreTradeRiskCheck? = null,
    val openTrades: List<OpenTradeLiveItem> = emptyList(),
    val closedTrades: List<ForexTrade> = emptyList(),
    val totalUnrealizedPnlUsd: Double = 0.0,
    val totalEquityUsd: Double = 25000.0,
    val dailyDrawdownPercent: Double = 0.0,
    val totalLiveRiskUsd: Double = 0.0,
    val totalLiveRiskPercent: Double = 0.0,
    val riskBreachCount: Int = 0,
    val feedStatus: FeedStatus = FeedStatus.SYNCING,
    val lastEcbDate: String = "",
    val syncError: String? = null
)

class TradingViewModel(
    private val repository: TradingRepository,
    private val marketRepository: LiveMarketRepository,
    private val currentUserId: String,
    private val initialDisplayName: String
) : ViewModel() {

    private val _selectedPair = MutableStateFlow(ForexPair.EUR_USD)
    val selectedPair: StateFlow<ForexPair> = _selectedPair.asStateFlow()

    private val _actionError = MutableStateFlow<String?>(null)
    val actionError: StateFlow<String?> = _actionError.asStateFlow()

    private val _latestEvent = MutableStateFlow<BotExecutionEvent?>(null)
    val latestEvent: StateFlow<BotExecutionEvent?> = _latestEvent.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _historySort = MutableStateFlow(TradeHistorySort.NEWEST_FIRST)
    val historySort: StateFlow<TradeHistorySort> = _historySort.asStateFlow()

    private val _isRefreshingHistory = MutableStateFlow(false)
    val isRefreshingHistory: StateFlow<Boolean> = _isRefreshingHistory.asStateFlow()

    private val _historyRetryTrigger = MutableStateFlow(0)

    private val _enabledOverlays = MutableStateFlow(
        setOf(
            ChartOverlayIndicator.EMA,
            ChartOverlayIndicator.BOLLINGER
        )
    )
    val enabledOverlays: StateFlow<Set<ChartOverlayIndicator>> = _enabledOverlays.asStateFlow()

    private val _selectedOscillator = MutableStateFlow(OscillatorPane.RSI)
    val selectedOscillator: StateFlow<OscillatorPane> = _selectedOscillator.asStateFlow()

    private val _activeMarkingTool = MutableStateFlow(ChartMarkingTool.BROWSE)
    val activeMarkingTool: StateFlow<ChartMarkingTool> = _activeMarkingTool.asStateFlow()

    private val _drawingsByPair = MutableStateFlow<Map<ForexPair, List<ChartDrawing>>>(emptyMap())
    val drawingsByPair: StateFlow<Map<ForexPair, List<ChartDrawing>>> = _drawingsByPair.asStateFlow()

    private val executionMutex = Mutex()
    private val pairCooldownMillis = mutableMapOf<String, Long>()

    val accountState: StateFlow<UiState<TradingAccount>> = repository.observeAccount(currentUserId)
        .map<TradingAccount?, UiState<TradingAccount>> { account ->
            UiState.Success(account ?: TradingAccount(userId = currentUserId, displayName = initialDisplayName))
        }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing trading account", rootError)
            emit(UiState.Error(rootError.message ?: "Failed to load trading account"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = UiState.Loading
        )

    val configState: StateFlow<UiState<BotRiskConfig>> = repository.observeRiskConfig(currentUserId)
        .map<BotRiskConfig?, UiState<BotRiskConfig>> { config ->
            UiState.Success(config ?: BotRiskConfig(userId = currentUserId))
        }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing risk config", rootError)
            emit(UiState.Error(rootError.message ?: "Failed to load risk rules"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = UiState.Loading
        )

    val tradesState: StateFlow<UiState<List<ForexTrade>>> = repository.observeTrades(currentUserId)
        .map<List<ForexTrade>, UiState<List<ForexTrade>>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing trades", rootError)
            emit(UiState.Error(rootError.message ?: "Failed to load trades"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = UiState.Loading
        )

    @OptIn(ExperimentalCoroutinesApi::class)
    val tradeHistoryState: StateFlow<UiState<List<ForexTrade>>> = combine(
        _historySort,
        _historyRetryTrigger
    ) { sort, _ -> sort }
        .flatMapLatest { sort ->
            val ascending = sort == TradeHistorySort.OLDEST_FIRST
            repository.observeExecutedTradeHistory(currentUserId, ascending = ascending)
                .map<List<ForexTrade>, UiState<List<ForexTrade>>> { trades ->
                    val sorted = when (sort) {
                        TradeHistorySort.NEWEST_FIRST -> trades.sortedByDescending { it.closedAtMillis }
                        TradeHistorySort.OLDEST_FIRST -> trades.sortedBy { it.closedAtMillis }
                        TradeHistorySort.HIGHEST_PNL -> trades.sortedByDescending { it.realizedPnlUsd }
                        TradeHistorySort.LOWEST_PNL -> trades.sortedBy { it.realizedPnlUsd }
                    }
                    UiState.Success(sorted)
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val rootError = firestoreError ?: error
                    Log.w(TAG, "Error querying Firestore trade history", rootError)
                    emit(UiState.Error(rootError.message ?: "Failed to query trade history"))
                }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = UiState.Loading
        )

    val terminalSnapshot: StateFlow<TerminalSnapshot> = combine(
        marketRepository.marketState,
        accountState,
        configState,
        tradesState,
        _selectedPair
    ) { market, accState, cfgState, trState, pair ->
        buildSnapshot(market, accState, cfgState, trState, pair)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = TerminalSnapshot()
    )

    init {
        viewModelScope.launch {
            val initResult = repository.ensureAccountAndConfigInitialized(initialDisplayName)
            initResult.onFailure { e ->
                _actionError.value = e.localizedMessage ?: "Failed to initialize trading account"
            }
        }
        marketRepository.start(viewModelScope)
        observeMarketForBotAutomation()
    }

    private fun buildSnapshot(
        market: MarketFeedState,
        accState: UiState<TradingAccount>,
        cfgState: UiState<BotRiskConfig>,
        trState: UiState<List<ForexTrade>>,
        selectedPair: ForexPair
    ): TerminalSnapshot {
        val account = (accState as? UiState.Success)?.data ?: TradingAccount(userId = currentUserId)
        val config = (cfgState as? UiState.Success)?.data ?: BotRiskConfig(userId = currentUserId)
        val allTrades = (trState as? UiState.Success)?.data.orEmpty()
        val openRaw = allTrades.filter { TradeStatus.safeValueOf(it.status) == TradeStatus.OPEN }
        val closedRaw = allTrades.filter { TradeStatus.safeValueOf(it.status) == TradeStatus.CLOSED }

        val usdJpySpot = market.quotes[ForexPair.USD_JPY]?.mid ?: 154.0
        val analyses = LinkedHashMap<ForexPair, TechnicalAnalysisResult>(market.quotes.size)
        market.quotes.forEach { (fxPair, quote) ->
            analyses[fxPair] = TechnicalAnalysisEngine.analyze(
                pair = fxPair,
                candles = quote.candles,
                bid = quote.bid,
                ask = quote.ask,
                config = config
            )
        }

        val liveOpenItems = openRaw.map { trade ->
            val fxPair = ForexPair.fromSymbol(trade.pair)
            val quote = market.quotes[fxPair]
            val side = TradeSide.safeValueOf(trade.side)
            val currentPrice = if (quote != null) {
                if (side == TradeSide.BUY) quote.bid else quote.ask
            } else {
                trade.entryPrice
            }
            val (unrealizedUsd, unrealizedPips) = if (quote != null) {
                RiskManager.calculateUnrealizedPnl(trade, quote, usdJpySpot)
            } else {
                0.0 to 0.0
            }
            val riskPerUnit = kotlin.math.abs(trade.entryPrice - trade.stopLoss).coerceAtLeast(fxPair.pipSize)
            val rewardDist = kotlin.math.abs(trade.takeProfit - trade.entryPrice).coerceAtLeast(fxPair.pipSize)
            val progress = if (side == TradeSide.BUY) {
                ((currentPrice - trade.stopLoss) / (trade.takeProfit - trade.stopLoss).coerceAtLeast(fxPair.pipSize)).toFloat()
            } else {
                ((trade.stopLoss - currentPrice) / (trade.stopLoss - trade.takeProfit).coerceAtLeast(fxPair.pipSize)).toFloat()
            }.coerceIn(0f, 1f)

            val realTimeRisk = RiskManager.calculateRealTimeTradeRisk(
                trade = trade,
                quote = quote,
                account = account,
                config = config,
                usdJpySpot = usdJpySpot,
                totalOpenPositionsCount = openRaw.size
            )

            OpenTradeLiveItem(
                trade = trade,
                currentPrice = currentPrice,
                unrealizedPnlUsd = unrealizedUsd,
                unrealizedPips = unrealizedPips,
                progressToTp = if (riskPerUnit > 0 && rewardDist > 0) progress else 0.5f,
                realTimeRisk = realTimeRisk
            )
        }

        val totalUnrealized = liveOpenItems.sumOf { it.unrealizedPnlUsd }
        val totalEquity = (account.accountBalance + totalUnrealized).coerceAtLeast(0.0)
        val dailyDrawdown = RiskManager.computeDailyDrawdownPercent(account, totalUnrealized)
        val totalLiveRiskUsd = liveOpenItems.sumOf { it.realTimeRisk.liveRiskUsd }
        val totalLiveRiskPercent = if (account.accountBalance > 0.0) {
            (totalLiveRiskUsd / account.accountBalance) * 100.0
        } else {
            0.0
        }
        val riskBreachCount = liveOpenItems.count { it.realTimeRisk.exceedsThreshold }

        val selectedAnalysis = analyses[selectedPair]
        val preTradeCheck = selectedAnalysis?.let { analysis ->
            RiskManager.evaluatePreTrade(
                account = account,
                config = config,
                analysis = analysis,
                openTrades = openRaw,
                usdJpySpot = usdJpySpot,
                requireConfluenceGate = true
            )
        }

        return TerminalSnapshot(
            account = account,
            config = config,
            selectedPair = selectedPair,
            quotes = market.quotes,
            analyses = analyses,
            selectedAnalysis = selectedAnalysis,
            selectedPreTradeCheck = preTradeCheck,
            openTrades = liveOpenItems,
            closedTrades = closedRaw,
            totalUnrealizedPnlUsd = totalUnrealized,
            totalEquityUsd = totalEquity,
            dailyDrawdownPercent = dailyDrawdown,
            totalLiveRiskUsd = totalLiveRiskUsd,
            totalLiveRiskPercent = totalLiveRiskPercent,
            riskBreachCount = riskBreachCount,
            feedStatus = market.feedStatus,
            lastEcbDate = market.lastEcbDate,
            syncError = market.syncError
        )
    }

    private fun observeMarketForBotAutomation() {
        viewModelScope.launch {
            marketRepository.marketState.collect { market ->
                if (market.quotes.isEmpty()) return@collect
                val account = (accountState.value as? UiState.Success)?.data ?: return@collect
                val config = (configState.value as? UiState.Success)?.data ?: return@collect
                val allTrades = (tradesState.value as? UiState.Success)?.data ?: return@collect
                val openTrades = allTrades.filter { TradeStatus.safeValueOf(it.status) == TradeStatus.OPEN }

                if (executionMutex.isLocked) return@collect
                executionMutex.withLock {
                    runAutomatedRiskAndExecutionCycle(market, account, config, openTrades)
                }
            }
        }
    }

    private suspend fun runAutomatedRiskAndExecutionCycle(
        market: MarketFeedState,
        account: TradingAccount,
        config: BotRiskConfig,
        openTrades: List<ForexTrade>
    ) {
        val usdJpySpot = market.quotes[ForexPair.USD_JPY]?.mid ?: 154.0
        var totalUnrealized = 0.0
        openTrades.forEach { trade ->
            val quote = market.quotes[ForexPair.fromSymbol(trade.pair)] ?: return@forEach
            totalUnrealized += RiskManager.calculateUnrealizedPnl(trade, quote, usdJpySpot).first
        }

        val currentDrawdownPct = RiskManager.computeDailyDrawdownPercent(account, totalUnrealized)
        val forceCircuitBreaker = !account.circuitBreakerTripped &&
            currentDrawdownPct >= config.maxDailyDrawdownPercent

        var mutableAccount = account
        val remainingOpenTrades = openTrades.toMutableList()

        // 1. Evaluate active open trades for exact SL/TP/Trailing/CircuitBreaker exits
        for (trade in openTrades) {
            val fxPair = ForexPair.fromSymbol(trade.pair)
            val quote = market.quotes[fxPair] ?: continue
            val analysis = TechnicalAnalysisEngine.analyze(fxPair, quote.candles, quote.bid, quote.ask, config)

            val exitEval = RiskManager.evaluateTradeExit(
                trade = trade,
                quote = quote,
                analysis = analysis,
                usdJpySpot = usdJpySpot,
                forceCircuitBreaker = forceCircuitBreaker
            )
            if (exitEval != null) {
                val res = repository.closeTradeAndSettleAccount(
                    trade = trade,
                    exitPrice = exitEval.exitPrice,
                    realizedPnlUsd = exitEval.realizedPnlUsd,
                    realizedPips = exitEval.realizedPips,
                    exitReason = exitEval.exitReason,
                    account = mutableAccount,
                    maxDailyDrawdownPercent = config.maxDailyDrawdownPercent
                )
                if (res.isSuccess) {
                    remainingOpenTrades.removeAll { it.id == trade.id }
                    pairCooldownMillis[trade.pair] = System.currentTimeMillis()
                    mutableAccount = mutableAccount.copy(
                        accountBalance = (mutableAccount.accountBalance + exitEval.realizedPnlUsd).coerceAtLeast(0.0),
                        dailyRealizedPnl = mutableAccount.dailyRealizedPnl + exitEval.realizedPnlUsd,
                        realizedPnl = mutableAccount.realizedPnl + exitEval.realizedPnlUsd,
                        totalTrades = mutableAccount.totalTrades + 1L,
                        winningTrades = mutableAccount.winningTrades + if (exitEval.realizedPnlUsd > 0.0) 1L else 0L
                    )
                    val sign = if (exitEval.realizedPnlUsd >= 0) "+" else "-"
                    val absUsd = "%.2f".format(java.util.Locale.US, kotlin.math.abs(exitEval.realizedPnlUsd))
                    _latestEvent.value = BotExecutionEvent(
                        message = "${trade.pair} ${trade.side} closed (${exitEval.exitReason.label}): $sign$$absUsd",
                        isPositive = exitEval.realizedPnlUsd >= 0.0
                    )
                }
                continue
            }

            // Evaluate trailing stop ratchet while open
            val trailAdj = RiskManager.evaluateTrailingStop(trade, quote, config)
            if (trailAdj != null) {
                repository.updateTrailingStop(
                    tradeId = trade.id,
                    newStopLoss = trailAdj.newStopLoss,
                    trailingStopPrice = trailAdj.newTrailingStopPrice
                )
            }
        }

        // 2. If Bot Armed & Circuit Breaker clear, scan watched pairs for high-confluence entry
        if (!mutableAccount.botArmed || mutableAccount.circuitBreakerTripped || forceCircuitBreaker) {
            return
        }

        val now = System.currentTimeMillis()
        for (pairSymbol in config.watchedPairs) {
            if (remainingOpenTrades.size >= config.maxOpenPositions.toInt()) break
            val lastClosed = pairCooldownMillis[pairSymbol] ?: 0L
            if (now - lastClosed < 12_000L) continue

            val fxPair = ForexPair.fromSymbol(pairSymbol)
            val quote = market.quotes[fxPair] ?: continue
            val analysis = TechnicalAnalysisEngine.analyze(fxPair, quote.candles, quote.bid, quote.ask, config)
            val side = analysis.suggestedSide ?: continue

            val riskCheck = RiskManager.evaluatePreTrade(
                account = mutableAccount,
                config = config,
                analysis = analysis,
                openTrades = remainingOpenTrades,
                usdJpySpot = usdJpySpot,
                requireConfluenceGate = true
            )
            if (!riskCheck.approved) continue

            val newTrade = ForexTrade(
                userId = currentUserId,
                pair = fxPair.symbol,
                side = side.name,
                status = TradeStatus.OPEN.name,
                lotSize = riskCheck.calculatedLotSize,
                units = riskCheck.calculatedUnits.toLong(),
                entryPrice = analysis.exactEntryPrice,
                stopLoss = analysis.exactStopLoss,
                takeProfit = analysis.exactTakeProfit,
                trailingStopPrice = 0.0,
                exitPrice = 0.0,
                riskAmountUsd = riskCheck.riskAmountUsd,
                riskRewardRatio = analysis.riskRewardRatio,
                realizedPnlUsd = 0.0,
                realizedPips = 0.0,
                exitReason = ExitReason.ACTIVE.name,
                signalSummary = analysis.signalSummary,
                confluenceScore = analysis.confluenceScore.toLong(),
                timeframe = "M15",
                openedAtMillis = now,
                closedAtMillis = 0L
            )
            val openRes = repository.openTrade(newTrade)
            openRes.onSuccess { createdId ->
                remainingOpenTrades.add(newTrade.copy(id = createdId))
                _latestEvent.value = BotExecutionEvent(
                    message = "Auto-Executed ${fxPair.symbol} ${side.name} ${riskCheck.calculatedLotSize} lots @ ${fxPair.formatPrice(analysis.exactEntryPrice)}",
                    isPositive = true
                )
            }
        }
    }

    fun selectPair(pair: ForexPair) {
        _selectedPair.value = pair
    }

    fun toggleBotArmed(armed: Boolean) {
        val snap = terminalSnapshot.value
        if (armed && snap.account.circuitBreakerTripped) {
            _actionError.value = "Cannot arm bot while Daily Drawdown Circuit Breaker is active."
            return
        }
        viewModelScope.launch {
            repository.toggleBotArmed(armed)
                .onFailure { _actionError.value = it.localizedMessage ?: "Failed to update bot status" }
                .onSuccess {
                    _latestEvent.value = BotExecutionEvent(
                        message = if (armed) "Automated Bot ARMED on ${snap.config.watchedPairs.size} pairs" else "Bot placed on STANDBY",
                        isPositive = armed
                    )
                }
        }
    }

    fun executeManualSetup(sideOverride: TradeSide? = null) {
        val snap = terminalSnapshot.value
        val analysis = snap.selectedAnalysis ?: return
        val quote = snap.quotes[snap.selectedPair] ?: return
        val usdJpySpot = snap.quotes[ForexPair.USD_JPY]?.mid ?: 154.0

        val effectiveAnalysis = if (sideOverride != null && sideOverride != analysis.suggestedSide) {
            val stopDist = kotlin.math.abs(analysis.exactEntryPrice - analysis.exactStopLoss)
            val tpDist = stopDist * snap.config.minRiskRewardRatio.coerceAtLeast(1.2)
            val entry = snap.selectedPair.roundToTick(if (sideOverride == TradeSide.BUY) quote.ask else quote.bid)
            val sl = snap.selectedPair.roundToTick(if (sideOverride == TradeSide.BUY) entry - stopDist else entry + stopDist)
            val tp = snap.selectedPair.roundToTick(if (sideOverride == TradeSide.BUY) entry + tpDist else entry - tpDist)
            analysis.copy(
                suggestedSide = sideOverride,
                exactEntryPrice = entry,
                exactStopLoss = sl,
                exactTakeProfit = tp
            )
        } else {
            val resolvedSide = analysis.suggestedSide ?: TradeSide.BUY
            analysis.copy(suggestedSide = resolvedSide)
        }

        val openRaw = snap.openTrades.map { it.trade }
        val riskCheck = RiskManager.evaluatePreTrade(
            account = snap.account,
            config = snap.config,
            analysis = effectiveAnalysis,
            openTrades = openRaw,
            usdJpySpot = usdJpySpot,
            requireConfluenceGate = false
        )
        if (!riskCheck.approved) {
            _actionError.value = riskCheck.rejectionReason ?: "Trade blocked by risk manager."
            return
        }

        val side = effectiveAnalysis.suggestedSide ?: TradeSide.BUY
        viewModelScope.launch {
            _isBusy.value = true
            val trade = ForexTrade(
                userId = currentUserId,
                pair = snap.selectedPair.symbol,
                side = side.name,
                status = TradeStatus.OPEN.name,
                lotSize = riskCheck.calculatedLotSize,
                units = riskCheck.calculatedUnits.toLong(),
                entryPrice = effectiveAnalysis.exactEntryPrice,
                stopLoss = effectiveAnalysis.exactStopLoss,
                takeProfit = effectiveAnalysis.exactTakeProfit,
                riskAmountUsd = riskCheck.riskAmountUsd,
                riskRewardRatio = effectiveAnalysis.riskRewardRatio,
                exitReason = ExitReason.ACTIVE.name,
                signalSummary = effectiveAnalysis.signalSummary,
                confluenceScore = effectiveAnalysis.confluenceScore.coerceAtLeast(1).toLong(),
                timeframe = "M15",
                openedAtMillis = System.currentTimeMillis()
            )
            repository.openTrade(trade)
                .onSuccess {
                    _latestEvent.value = BotExecutionEvent(
                        message = "Executed ${snap.selectedPair.symbol} ${side.name} ${riskCheck.calculatedLotSize} lots @ ${snap.selectedPair.formatPrice(effectiveAnalysis.exactEntryPrice)}",
                        isPositive = true
                    )
                }
                .onFailure { e ->
                    _actionError.value = e.localizedMessage ?: "Order execution failed"
                }
            _isBusy.value = false
        }
    }

    fun closePositionManually(item: OpenTradeLiveItem) {
        val snap = terminalSnapshot.value
        viewModelScope.launch {
            _isBusy.value = true
            repository.closeTradeAndSettleAccount(
                trade = item.trade,
                exitPrice = item.currentPrice,
                realizedPnlUsd = item.unrealizedPnlUsd,
                realizedPips = item.unrealizedPips,
                exitReason = ExitReason.MANUAL_CLOSE,
                account = snap.account,
                maxDailyDrawdownPercent = snap.config.maxDailyDrawdownPercent
            ).onSuccess {
                val sign = if (item.unrealizedPnlUsd >= 0) "+" else "-"
                val absUsd = "%.2f".format(java.util.Locale.US, kotlin.math.abs(item.unrealizedPnlUsd))
                _latestEvent.value = BotExecutionEvent(
                    message = "Closed ${item.trade.pair} manually: $sign$$absUsd",
                    isPositive = item.unrealizedPnlUsd >= 0
                )
            }.onFailure { e ->
                _actionError.value = e.localizedMessage ?: "Failed to close position"
            }
            _isBusy.value = false
        }
    }

    fun updateRiskConfig(updated: BotRiskConfig) {
        viewModelScope.launch {
            _isBusy.value = true
            repository.updateRiskConfig(updated)
                .onSuccess {
                    _latestEvent.value = BotExecutionEvent(
                        message = "Risk rules & indicator thresholds saved",
                        isPositive = true
                    )
                }
                .onFailure { e ->
                    _actionError.value = e.localizedMessage ?: "Failed to save risk settings"
                }
            _isBusy.value = false
        }
    }

    fun resetCircuitBreaker() {
        val balance = terminalSnapshot.value.account.accountBalance
        viewModelScope.launch {
            _isBusy.value = true
            repository.resetCircuitBreaker(balance)
                .onSuccess {
                    _latestEvent.value = BotExecutionEvent(
                        message = "Circuit Breaker unlocked. Daily equity baseline reset.",
                        isPositive = true
                    )
                }
                .onFailure { e ->
                    _actionError.value = e.localizedMessage ?: "Failed to reset circuit breaker"
                }
            _isBusy.value = false
        }
    }

    fun resetPaperAccount(startingCapital: Double) {
        viewModelScope.launch {
            _isBusy.value = true
            repository.resetPaperAccount(startingCapital)
                .onSuccess {
                    _latestEvent.value = BotExecutionEvent(
                        message = "Paper account reset to $${"%,.0f".format(java.util.Locale.US, startingCapital)}",
                        isPositive = true
                    )
                }
                .onFailure { e ->
                    _actionError.value = e.localizedMessage ?: "Failed to reset account"
                }
            _isBusy.value = false
        }
    }

    fun syncLiveRates() {
        viewModelScope.launch {
            marketRepository.syncEcbGlobalRates()
        }
    }

    fun setHistorySort(sort: TradeHistorySort) {
        _historySort.value = sort
    }

    fun refreshTradeHistory() {
        if (_isRefreshingHistory.value) return
        viewModelScope.launch {
            _isRefreshingHistory.value = true
            val ascending = _historySort.value == TradeHistorySort.OLDEST_FIRST
            repository.fetchExecutedTradeHistory(ascending = ascending)
                .onFailure { e ->
                    _actionError.value = e.localizedMessage ?: "Failed to query trade history"
                }
            _historyRetryTrigger.value += 1
            _isRefreshingHistory.value = false
        }
    }

    fun toggleOverlay(indicator: ChartOverlayIndicator) {
        val current = _enabledOverlays.value
        _enabledOverlays.value = if (indicator in current) current - indicator else current + indicator
    }

    fun setAllOverlays(enableAll: Boolean) {
        _enabledOverlays.value = if (enableAll) ChartOverlayIndicator.entries.toSet() else emptySet()
    }

    fun selectOscillator(pane: OscillatorPane) {
        _selectedOscillator.value = pane
    }

    fun selectMarkingTool(tool: ChartMarkingTool) {
        _activeMarkingTool.value = tool
    }

    fun addDrawing(pair: ForexPair, drawing: ChartDrawing) {
        val currentMap = _drawingsByPair.value.toMutableMap()
        val list = currentMap[pair].orEmpty() + drawing
        currentMap[pair] = list
        _drawingsByPair.value = currentMap
    }

    fun undoDrawing(pair: ForexPair) {
        val currentMap = _drawingsByPair.value.toMutableMap()
        val list = currentMap[pair].orEmpty()
        if (list.isNotEmpty()) {
            currentMap[pair] = list.dropLast(1)
            _drawingsByPair.value = currentMap
        }
    }

    fun clearDrawings(pair: ForexPair) {
        val currentMap = _drawingsByPair.value.toMutableMap()
        currentMap.remove(pair)
        _drawingsByPair.value = currentMap
    }

    fun dismissError() {
        _actionError.value = null
    }

    fun dismissEvent() {
        _latestEvent.value = null
    }

    override fun onCleared() {
        super.onCleared()
        marketRepository.stop()
    }

    private companion object {
        const val TAG = "TradingViewModel"
    }
}
