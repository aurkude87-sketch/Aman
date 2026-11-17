package com.example.data.firebase

import android.content.Context
import com.example.R
import com.example.model.BotRiskConfig
import com.example.model.ExitReason
import com.example.model.ForexTrade
import com.example.model.TradeStatus
import com.example.model.TradingAccount
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class TradingRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    fun observeAccount(userId: String): Flow<TradingAccount?> = flow {
        val path = "users/$userId"
        emitAll(
            db.collection("users").document(userId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObject(
                        TradingAccount::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    fun observeRiskConfig(userId: String): Flow<BotRiskConfig?> = flow {
        val path = "users/$userId/configs/default"
        emitAll(
            db.collection("users").document(userId)
                .collection("configs").document("default")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObject(
                        BotRiskConfig::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    fun observeTrades(userId: String): Flow<List<ForexTrade>> = flow {
        val path = "users/$userId/trades"
        emitAll(
            db.collection("users").document(userId)
                .collection("trades")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        ForexTrade::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.openedAtMillis }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeExecutedTradeHistory(
        userId: String,
        ascending: Boolean = false
    ): Flow<List<ForexTrade>> = flow {
        val uid = requireUserId()
        require(uid == userId) { "Authenticated user mismatch for trade history query." }
        val path = "users/$uid/trades"
        val direction = if (ascending) Query.Direction.ASCENDING else Query.Direction.DESCENDING
        emitAll(
            db.collection("users").document(uid)
                .collection("trades")
                .whereGreaterThan("closedAtMillis", 0L)
                .orderBy("closedAtMillis", direction)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        ForexTrade::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).filter { TradeStatus.safeValueOf(it.status) == TradeStatus.CLOSED }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun fetchExecutedTradeHistory(ascending: Boolean = false): Result<List<ForexTrade>> {
        var colPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/trades"
        return try {
            val uid = requireUserId()
            val colRef = db.collection("users").document(uid).collection("trades")
            colPath = colRef.path
            val direction = if (ascending) Query.Direction.ASCENDING else Query.Direction.DESCENDING
            val snap = colRef
                .whereGreaterThan("closedAtMillis", 0L)
                .orderBy("closedAtMillis", direction)
                .get()
                .await()
            val executed = snap.toObjects(
                ForexTrade::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            ).filter { TradeStatus.safeValueOf(it.status) == TradeStatus.CLOSED }
            Result.success(executed)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, colPath)
            Result.failure(e)
        }
    }

    suspend fun ensureAccountAndConfigInitialized(displayName: String): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}"
        return try {
            val uid = requireUserId()
            val safeName = displayName.trim().ifBlank { "FX Trader" }.take(80)
            val userDoc = db.collection("users").document(uid)
            docPath = userDoc.path
            val existingAccount = userDoc.get().await()
            if (!existingAccount.exists()) {
                val accountPayload = mapOf(
                    "userId" to uid,
                    "displayName" to safeName,
                    "accountBalance" to 25000.0,
                    "startingBalance" to 25000.0,
                    "dailyStartEquity" to 25000.0,
                    "dailyRealizedPnl" to 0.0,
                    "realizedPnl" to 0.0,
                    "totalTrades" to 0L,
                    "winningTrades" to 0L,
                    "botArmed" to false,
                    "circuitBreakerTripped" to false,
                    "executionMode" to "PAPER",
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                userDoc.set(accountPayload).await()
            }

            val configDoc = userDoc.collection("configs").document("default")
            docPath = configDoc.path
            val existingConfig = configDoc.get().await()
            if (!existingConfig.exists()) {
                val defaultConfig = BotRiskConfig(userId = uid)
                val configPayload = mapOf(
                    "userId" to uid,
                    "riskPerTradePercent" to defaultConfig.riskPerTradePercent,
                    "maxDailyDrawdownPercent" to defaultConfig.maxDailyDrawdownPercent,
                    "maxOpenPositions" to defaultConfig.maxOpenPositions,
                    "minRiskRewardRatio" to defaultConfig.minRiskRewardRatio,
                    "atrStopMultiplier" to defaultConfig.atrStopMultiplier,
                    "trailingStopEnabled" to defaultConfig.trailingStopEnabled,
                    "breakEvenTriggerR" to defaultConfig.breakEvenTriggerR,
                    "emaFastPeriod" to defaultConfig.emaFastPeriod,
                    "emaSlowPeriod" to defaultConfig.emaSlowPeriod,
                    "rsiPeriod" to defaultConfig.rsiPeriod,
                    "rsiOversold" to defaultConfig.rsiOversold,
                    "rsiOverbought" to defaultConfig.rsiOverbought,
                    "minConfluenceScore" to defaultConfig.minConfluenceScore,
                    "watchedPairs" to defaultConfig.watchedPairs,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                configDoc.set(configPayload).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun toggleBotArmed(armed: Boolean): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}"
        return try {
            val uid = requireUserId()
            val ref = db.collection("users").document(uid)
            docPath = ref.path
            ref.update(
                mapOf(
                    "botArmed" to armed,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun resetCircuitBreaker(currentBalance: Double): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}"
        return try {
            val uid = requireUserId()
            val ref = db.collection("users").document(uid)
            docPath = ref.path
            ref.update(
                mapOf(
                    "circuitBreakerTripped" to false,
                    "botArmed" to false,
                    "dailyStartEquity" to currentBalance.coerceAtLeast(0.0),
                    "dailyRealizedPnl" to 0.0,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun resetPaperAccount(startingCapital: Double): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}"
        return try {
            val uid = requireUserId()
            val boundedCapital = startingCapital.coerceIn(100.0, 10_000_000.0)
            val userRef = db.collection("users").document(uid)
            docPath = userRef.path
            val tradesSnap = userRef.collection("trades").get().await()

            val batch = db.batch()
            tradesSnap.documents.forEach { doc ->
                batch.delete(doc.reference)
            }
            batch.update(
                userRef,
                mapOf(
                    "accountBalance" to boundedCapital,
                    "startingBalance" to boundedCapital,
                    "dailyStartEquity" to boundedCapital,
                    "dailyRealizedPnl" to 0.0,
                    "realizedPnl" to 0.0,
                    "totalTrades" to 0L,
                    "winningTrades" to 0L,
                    "botArmed" to false,
                    "circuitBreakerTripped" to false,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateRiskConfig(config: BotRiskConfig): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/configs/default"
        return try {
            val uid = requireUserId()
            val safeFast = config.emaFastPeriod.coerceIn(5L, 25L)
            val safeSlow = config.emaSlowPeriod.coerceIn(safeFast + 2L, 100L)
            val safePairs = config.watchedPairs.ifEmpty { listOf("EUR/USD") }.take(8)
            val ref = db.collection("users").document(uid).collection("configs").document("default")
            docPath = ref.path
            ref.update(
                mapOf(
                    "riskPerTradePercent" to config.riskPerTradePercent.coerceIn(0.25, 5.0),
                    "maxDailyDrawdownPercent" to config.maxDailyDrawdownPercent.coerceIn(1.0, 15.0),
                    "maxOpenPositions" to config.maxOpenPositions.coerceIn(1L, 10L),
                    "minRiskRewardRatio" to config.minRiskRewardRatio.coerceIn(1.2, 5.0),
                    "atrStopMultiplier" to config.atrStopMultiplier.coerceIn(1.0, 4.0),
                    "trailingStopEnabled" to config.trailingStopEnabled,
                    "breakEvenTriggerR" to config.breakEvenTriggerR.coerceIn(0.5, 2.0),
                    "emaFastPeriod" to safeFast,
                    "emaSlowPeriod" to safeSlow,
                    "rsiPeriod" to config.rsiPeriod.coerceIn(7L, 28L),
                    "rsiOversold" to config.rsiOversold.coerceIn(15.0, 40.0),
                    "rsiOverbought" to config.rsiOverbought.coerceIn(60.0, 85.0),
                    "minConfluenceScore" to config.minConfluenceScore.coerceIn(2L, 4L),
                    "watchedPairs" to safePairs,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun openTrade(trade: ForexTrade): Result<String> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/trades"
        return try {
            val uid = requireUserId()
            val docRef = db.collection("users").document(uid).collection("trades").document()
            docPath = docRef.path
            val payload = mapOf(
                "userId" to uid,
                "pair" to trade.pair.take(12),
                "side" to trade.side,
                "status" to TradeStatus.OPEN.name,
                "lotSize" to trade.lotSize.coerceIn(0.01, 100.0),
                "units" to trade.units.coerceAtLeast(1L),
                "entryPrice" to trade.entryPrice,
                "stopLoss" to trade.stopLoss,
                "takeProfit" to trade.takeProfit,
                "trailingStopPrice" to 0.0,
                "exitPrice" to 0.0,
                "riskAmountUsd" to trade.riskAmountUsd.coerceAtLeast(0.01),
                "riskRewardRatio" to trade.riskRewardRatio.coerceAtLeast(1.0),
                "realizedPnlUsd" to 0.0,
                "realizedPips" to 0.0,
                "exitReason" to ExitReason.ACTIVE.name,
                "signalSummary" to trade.signalSummary.ifBlank { "Manual Setup" }.take(160),
                "confluenceScore" to trade.confluenceScore.coerceIn(1L, 4L),
                "timeframe" to trade.timeframe.ifBlank { "M15" }.take(10),
                "openedAtMillis" to trade.openedAtMillis.coerceAtLeast(1L),
                "closedAtMillis" to 0L,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateTrailingStop(
        tradeId: String,
        newStopLoss: Double,
        trailingStopPrice: Double
    ): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/trades/$tradeId"
        return try {
            val uid = requireUserId()
            require(tradeId.isNotBlank()) { "Trade ID cannot be blank" }
            val docRef = db.collection("users").document(uid).collection("trades").document(tradeId)
            docPath = docRef.path
            docRef.update(
                mapOf(
                    "stopLoss" to newStopLoss,
                    "trailingStopPrice" to trailingStopPrice,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun closeTradeAndSettleAccount(
        trade: ForexTrade,
        exitPrice: Double,
        realizedPnlUsd: Double,
        realizedPips: Double,
        exitReason: ExitReason,
        account: TradingAccount,
        maxDailyDrawdownPercent: Double
    ): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/trades/${trade.id}"
        return try {
            val uid = requireUserId()
            require(trade.id.isNotBlank()) { "Trade ID cannot be blank" }
            val userRef = db.collection("users").document(uid)
            val tradeRef = userRef.collection("trades").document(trade.id)
            docPath = tradeRef.path

            val newBalance = (account.accountBalance + realizedPnlUsd).coerceAtLeast(0.0)
            val newDailyPnl = account.dailyRealizedPnl + realizedPnlUsd
            val newTotalPnl = account.realizedPnl + realizedPnlUsd
            val newTotalTrades = account.totalTrades + 1L
            val newWinningTrades = account.winningTrades + if (realizedPnlUsd > 0.0) 1L else 0L
            val dailyLimitUsd = account.dailyStartEquity * (maxDailyDrawdownPercent / 100.0)
            val shouldTripBreaker = account.circuitBreakerTripped || (newDailyPnl <= -dailyLimitUsd)
            val updatedArmed = if (shouldTripBreaker) false else account.botArmed

            val batch = db.batch()
            batch.update(
                tradeRef,
                mapOf(
                    "status" to TradeStatus.CLOSED.name,
                    "exitPrice" to exitPrice,
                    "realizedPnlUsd" to realizedPnlUsd,
                    "realizedPips" to realizedPips,
                    "exitReason" to exitReason.name,
                    "closedAtMillis" to System.currentTimeMillis().coerceAtLeast(1L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.update(
                userRef,
                mapOf(
                    "accountBalance" to newBalance,
                    "dailyRealizedPnl" to newDailyPnl,
                    "realizedPnl" to newTotalPnl,
                    "totalTrades" to newTotalTrades,
                    "winningTrades" to newWinningTrades,
                    "circuitBreakerTripped" to shouldTripBreaker,
                    "botArmed" to updatedArmed,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }
}
