package com.hillariousstartups.btcdca

import com.google.cloud.Timestamp
import com.google.cloud.firestore.Firestore
import com.google.cloud.firestore.SetOptions
import java.math.BigDecimal
import java.time.Instant

interface StrategyStateRepository {
    fun load(strategyId: String): StrategyState
    fun save(strategyId: String, state: StrategyState)
}

class FirestoreStrategyStateRepository(
    private val firestore: Firestore,
) : StrategyStateRepository {

    private val col = firestore.collection("strategy_state")

    override fun load(strategyId: String): StrategyState {
        val docRef = col.document(strategyId)
        val snap = docRef.get().get()

        if (!snap.exists()) return StrategyState()

        val periodKey = snap.getString("periodKey")

        val spentStr = snap.getString("spentThisPeriodUsd")
        val spent = spentStr?.toBigDecimalOrNull() ?: BigDecimal.ZERO

        val peakStr = snap.getString("referencePeakUsd")
        val peak = peakStr?.toBigDecimalOrNull()

        val peakUpdatedAt = snap.getTimestamp("referencePeakUpdatedAt")
            ?.toDate()
            ?.toInstant()

        val lastEvaluatedAt = snap.getTimestamp("lastEvaluatedAt")
            ?.toDate()
            ?.toInstant()

        return StrategyState(
            periodKey = periodKey,
            spentThisPeriodUsd = spent,
            referencePeakUsd = peak,
            referencePeakUpdatedAt = peakUpdatedAt,
            lastEvaluatedAt = lastEvaluatedAt,
        )
    }

    override fun save(strategyId: String, state: StrategyState) {
        val docRef = col.document(strategyId)

        val data = mutableMapOf<String, Any?>(
            "periodKey" to state.periodKey,
            "spentThisPeriodUsd" to state.spentThisPeriodUsd.toPlainString(),
            "referencePeakUsd" to state.referencePeakUsd?.toPlainString(),
            "referencePeakUpdatedAt" to state.referencePeakUpdatedAt?.toTimestamp(),
            "lastEvaluatedAt" to state.lastEvaluatedAt?.toTimestamp(),
        )

        // Remove nulls so we don't overwrite existing fields with null during merge.
        data.entries.removeIf { it.value == null }

        docRef.set(data, SetOptions.merge()).get()
    }

    private fun Instant.toTimestamp(): Timestamp =
        Timestamp.ofTimeSecondsAndNanos(this.epochSecond, this.nano)
}