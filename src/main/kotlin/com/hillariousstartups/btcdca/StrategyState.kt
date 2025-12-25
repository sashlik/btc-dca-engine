package com.hillariousstartups.btcdca

import java.math.BigDecimal
import java.time.Instant

/**
 * Persisted state. For now it's stored in a JSON file; later we can replace the repository with DynamoDB/Firestore/etc.
 */
data class StrategyState(
    /** Period key like "2026-01" that depends on [StrategyConfig.monthStartDay]. */
    val periodKey: String? = null,

    /** How much we have already "recommended to buy" within the current period. */
    val spentThisPeriodUsd: BigDecimal = BigDecimal.ZERO,

    /** Reference peak price (USD) used for drawdown calculation. */
    val referencePeakUsd: BigDecimal? = null,

    /** Last time the reference peak was updated. */
    val referencePeakUpdatedAt: Instant? = null,

    /** For debugging / observability. */
    val lastEvaluatedAt: Instant? = null,
)
