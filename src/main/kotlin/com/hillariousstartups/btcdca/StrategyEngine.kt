package com.hillariousstartups.btcdca

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class StrategyEngine(
    private val config: StrategyConfig,
) {
    private val DIV_SCALE = 18

    fun evaluate(now: Instant, priceHistory: List<PricePoint>, state: StrategyState): EvaluationResult {
        require(config.monthStartDay in 1..28) { "monthStartDay should be 1..28" }
        require(priceHistory.isNotEmpty()) { "priceHistory must not be empty" }

        val currentPrice = currentPriceAtOrBefore(now, priceHistory)

        // Rolling peak is still computed, but reference peak will NOT move down.
        val rollingPeak = rollingPeak(now, priceHistory)

        val (refPeak, refPeakUpdated) = updateReferencePeakStickyDown(
            previous = state.referencePeakUsd,
            candidateRollingPeak = rollingPeak,
            hysteresis = config.peakUpdateHysteresis
        )

        val periodKey = periodKey(now, config.monthStartDay)
        val isNewPeriod = state.periodKey == null || state.periodKey != periodKey

        var spent = if (isNewPeriod) BigDecimal.ZERO else state.spentThisPeriodUsd
        val signals = mutableListOf<BuySignal>()

        // Base DCA once per period
        if (isNewPeriod) {
            val base = config.baseDcaUsd.min(config.monthlyCapUsd)
            if (base > BigDecimal.ZERO) {
                signals += BuySignal(
                    time = now,
                    amountUsd = base,
                    reason = "Base DCA for period $periodKey (start day=${config.monthStartDay})"
                )
                spent = spent.add(base)
            }
        }

        val drawdown = computeDrawdown(referencePeak = refPeak, currentPrice = currentPrice)
        val tierTarget = chooseTierTarget(drawdown).min(config.monthlyCapUsd)

        if (tierTarget > spent) {
            val additional = tierTarget.subtract(spent)
            signals += BuySignal(
                time = now,
                amountUsd = additional,
                reason = "Drawdown ${(drawdown * BigDecimal(100)).setScale(1, RoundingMode.HALF_UP)}% -> target $tierTarget USD for period $periodKey"
            )
            spent = tierTarget
        }

        val newState = state.copy(
            periodKey = periodKey,
            spentThisPeriodUsd = spent,
            referencePeakUsd = refPeak,
            referencePeakUpdatedAt = if (refPeakUpdated) now else state.referencePeakUpdatedAt,
            lastEvaluatedAt = now,
        )

        return EvaluationResult(signals = signals, state = newState)
    }

    data class EvaluationResult(
        val signals: List<BuySignal>,
        val state: StrategyState,
    )

    private fun currentPriceAtOrBefore(now: Instant, priceHistory: List<PricePoint>): BigDecimal {
        val candidates = priceHistory.filter { it.time <= now }
        require(candidates.isNotEmpty()) { "No price points with time <= now" }
        val maxTime = candidates.maxOf { it.time }
        return candidates.last { it.time == maxTime }.priceUsd
    }

    private fun rollingPeak(now: Instant, priceHistory: List<PricePoint>): BigDecimal {
        val upToNow = priceHistory.filter { it.time <= now }
        require(upToNow.isNotEmpty()) { "No price points with time <= now" }

        val from = now.minusSeconds(config.lookbackDays * 24L * 3600L)

        // If we don't have data spanning the full lookback window, use all available history up to `now`.
        // This makes unit tests with synthetic histories deterministic.
        val hasFullWindow = upToNow.any { it.time <= from }

        val window = if (hasFullWindow) upToNow.filter { it.time >= from } else upToNow
        return window.maxOf { it.priceUsd }
    }

    /**
     * Sticky-down reference peak:
     * - Initialize to rolling peak.
     * - Never decrease.
     * - Increase only if rollingPeak >= previous*(1+hysteresis).
     *
     * This matches your test helpers (peak & current points) and avoids needing full 90d history in tests.
     */
    private fun updateReferencePeakStickyDown(
        previous: BigDecimal?,
        candidateRollingPeak: BigDecimal,
        hysteresis: BigDecimal,
    ): Pair<BigDecimal, Boolean> {
        if (previous == null) return candidateRollingPeak to true

        val threshold = previous.multiply(BigDecimal.ONE.add(hysteresis))
        return if (candidateRollingPeak >= threshold) candidateRollingPeak to true else previous to false
    }

    private fun computeDrawdown(referencePeak: BigDecimal, currentPrice: BigDecimal): BigDecimal {
        if (referencePeak <= BigDecimal.ZERO) return BigDecimal.ZERO
        val dd = referencePeak.subtract(currentPrice).divide(referencePeak, DIV_SCALE, RoundingMode.HALF_UP)
        return dd.max(BigDecimal.ZERO)
    }

    private fun chooseTierTarget(drawdown: BigDecimal): BigDecimal {
        fun norm(x: BigDecimal) = if (x > BigDecimal.ONE) x.divide(BigDecimal(100)) else x

        val matching = config.tiers.filter { t ->
            val from = norm(t.drawdownFromInclusive)
            val to = t.drawdownToExclusive?.let(::norm)
            (drawdown >= from) && (to?.let { drawdown < it } ?: true)
        }

        // order-independent
        val chosen = matching.maxByOrNull { norm(it.drawdownFromInclusive) }
            ?: config.tiers.minBy { norm(it.drawdownFromInclusive) }

        return chosen.targetMonthlySpendUsd
    }

    companion object {
        fun periodKey(now: Instant, startDay: Int): String {
            val date = LocalDate.ofInstant(now, ZoneOffset.UTC)
            val adjusted = if (date.dayOfMonth < startDay) date.minusMonths(1) else date
            return "%04d-%02d".format(adjusted.year, adjusted.monthValue)
        }
    }
}