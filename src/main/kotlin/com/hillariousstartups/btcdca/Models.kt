package com.hillariousstartups.btcdca

import java.math.BigDecimal
import java.time.Instant

data class PricePoint(
    val time: Instant,
    val priceUsd: BigDecimal,
)

data class BuySignal(
    val time: Instant,
    val amountUsd: BigDecimal,
    val reason: String,
    val text: String
)

data class Tier(
    /** Inclusive lower bound for drawdown (e.g. 0.15 for -15%) */
    val drawdownFromInclusive: BigDecimal,
    /** Exclusive upper bound for drawdown. Null means +infinity. */
    val drawdownToExclusive: BigDecimal? = null,
    /** Target total buy amount for the current month/period when drawdown is in this tier. */
    val targetMonthlySpendUsd: BigDecimal,
)
