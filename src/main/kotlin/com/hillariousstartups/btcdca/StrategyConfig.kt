package com.hillariousstartups.btcdca

import java.math.BigDecimal
import java.time.Duration

data class StrategyConfig(
    val baseDcaUsd: BigDecimal = BigDecimal("250"),
    val monthlyCapUsd: BigDecimal = BigDecimal("1000"),

    /** Month/period starts on this day-of-month (1..28 recommended). Example: 2 means "every 2nd". */
    val monthStartDay: Int = 2,

    /** Lookback window for rolling peak. */
    val lookbackDays: Long = 90,

    /** Hysteresis for updating reference peak; 0.01 means +1%. */
    val peakUpdateHysteresis: BigDecimal = BigDecimal("0.01"),

    /** How often the scheduler runs (used mostly for docs; not required by the engine). */
    val checkInterval: Duration = Duration.ofHours(4),

    /** Tiers must be sorted by drawdownFromInclusive ascending. */
    val tiers: List<Tier> = listOf(
        Tier(drawdownFromInclusive = BigDecimal("0.00"), drawdownToExclusive = BigDecimal("0.15"), targetMonthlySpendUsd = BigDecimal("250")),
        Tier(drawdownFromInclusive = BigDecimal("0.15"), drawdownToExclusive = BigDecimal("0.25"), targetMonthlySpendUsd = BigDecimal("400")),
        Tier(drawdownFromInclusive = BigDecimal("0.25"), drawdownToExclusive = BigDecimal("0.35"), targetMonthlySpendUsd = BigDecimal("600")),
        Tier(drawdownFromInclusive = BigDecimal("0.35"), drawdownToExclusive = BigDecimal("0.45"), targetMonthlySpendUsd = BigDecimal("800")),
        Tier(drawdownFromInclusive = BigDecimal("0.45"), drawdownToExclusive = null,            targetMonthlySpendUsd = BigDecimal("1000")),
    ),
)
