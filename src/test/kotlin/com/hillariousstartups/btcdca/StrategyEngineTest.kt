package com.hillariousstartups.btcdca

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.temporal.ChronoUnit

class StrategyEngineTest {

    private val config = StrategyConfig(
        baseDcaUsd = BigDecimal("250"),
        monthlyCapUsd = BigDecimal("1000"),
        monthStartDay = 2,
        lookbackDays = 90,
        peakUpdateHysteresis = BigDecimal("0.01"),
        tiers = listOf(
            Tier(BigDecimal("0.00"), BigDecimal("0.15"), BigDecimal("250")),
            Tier(BigDecimal("0.15"), BigDecimal("0.25"), BigDecimal("400")),
            Tier(BigDecimal("0.25"), BigDecimal("0.35"), BigDecimal("600")),
            Tier(BigDecimal("0.35"), BigDecimal("0.45"), BigDecimal("800")),
            Tier(BigDecimal("0.45"), null, BigDecimal("1000")),
        )
    )

    @Test
    fun `base DCA triggers at period start`() {
        val engine = StrategyEngine(config)
        val now = Instant.parse("2026-01-02T00:00:00Z") // start day=2 -> new period
        val history = historyWithPeakAndCurrent(now, peak = bd("100"), current = bd("100"))

        val result = engine.evaluate(now, history, StrategyState())
        assertEquals(1, result.signals.size)
        assertEquals(bd("250"), result.signals[0].amountUsd)
        assertTrue(result.signals[0].reason.contains("Base DCA"))
        assertEquals(bd("250"), result.state.spentThisPeriodUsd)
        assertEquals("2026-01", result.state.periodKey)
    }

    @Test
    fun `additional buy is recommended when drawdown enters deeper tier within same period`() {
        val engine = StrategyEngine(config)
        assertEquals(bd("1000"), config.monthlyCapUsd)      // must be > 250
        assertEquals(bd("250"), config.baseDcaUsd)
        // Start of period -> base 250
        val t0 = Instant.parse("2026-01-02T00:00:00Z")
        val h0 = historyWithPeakAndCurrent(t0, peak = bd("100"), current = bd("100"))
        val r0 = engine.evaluate(t0, h0, StrategyState())
        assertEquals(bd("250"), r0.state.spentThisPeriodUsd)

        // Later in same period price drops -20% (100 -> 80) => tier target 400 => additional 150
        val t1 = t0.plus(7, ChronoUnit.DAYS)
        val h1 = historyWithPeakAndCurrent(t1, peak = bd("100"), current = bd("80"))
        println(h1.maxOf { it.time })
        println(h1.filter { it.time <= t1 }.maxOf { it.time })
        val r1 = engine.evaluate(t1, h1, r0.state)
        println("monthlyCapUsd=${config.monthlyCapUsd}")
        assertEquals(1, r1.signals.size)
        assertEquals(bd("150"), r1.signals[0].amountUsd)
        assertTrue(r1.signals[0].reason.contains("Drawdown"))
        assertEquals(bd("400"), r1.state.spentThisPeriodUsd)

        // Drop deeper -30% => target 600 => additional 200
        val t2 = t1.plus(1, ChronoUnit.DAYS)
        val h2 = historyWithPeakAndCurrent(t2, peak = bd("100"), current = bd("70"))
        val r2 = engine.evaluate(t2, h2, r1.state)

        assertEquals(1, r2.signals.size)
        assertEquals(bd("200"), r2.signals[0].amountUsd)
        assertEquals(bd("600"), r2.state.spentThisPeriodUsd)

        // Same tier again should NOT spam (still -30%) => no signal
        val t3 = t2.plus(4, ChronoUnit.HOURS)
        val h3 = historyWithPeakAndCurrent(t3, peak = bd("100"), current = bd("72"))
        val r3 = engine.evaluate(t3, h3, r2.state)
        assertEquals(0, r3.signals.size)
        assertEquals(bd("600"), r3.state.spentThisPeriodUsd)
    }

    @Test
    fun `period boundary reset happens on configured start day`() {
        val engine = StrategyEngine(config)

        // Jan period
        val janStart = Instant.parse("2026-01-02T00:00:00Z")
        val jan = engine.evaluate(janStart, historyWithPeakAndCurrent(janStart, bd("100"), bd("100")), StrategyState())
        assertEquals("2026-01", jan.state.periodKey)
        assertEquals(bd("250"), jan.state.spentThisPeriodUsd)

        // Feb 1 is still January period because start day is 2
        val feb1 = Instant.parse("2026-02-01T12:00:00Z")
        val feb1Res = engine.evaluate(feb1, historyWithPeakAndCurrent(feb1, bd("120"), bd("120")), jan.state)
        assertEquals("2026-01", feb1Res.state.periodKey)
        assertEquals(0, feb1Res.signals.size) // no new base DCA

        // Feb 2 becomes new period and triggers base
        val feb2 = Instant.parse("2026-02-02T00:00:00Z")
        val feb2Res = engine.evaluate(feb2, historyWithPeakAndCurrent(feb2, bd("120"), bd("120")), feb1Res.state)
        assertEquals("2026-02", feb2Res.state.periodKey)
        assertEquals(1, feb2Res.signals.size)
        assertEquals(bd("250"), feb2Res.signals[0].amountUsd)
    }

    @Test
    fun `hysteresis prevents tiny peak updates`() {
        val engine = StrategyEngine(config)

        val t0 = Instant.parse("2026-01-02T00:00:00Z")
        val s0 = StrategyState(referencePeakUsd = bd("100")) // previous ref peak 100

        // Rolling peak increases to 100.5 (+0.5%) -> should NOT update ref peak due to 1% hysteresis
        val t1 = t0.plus(1, ChronoUnit.DAYS)
        val h1 = historyWithPeakAndCurrent(t1, peak = bd("100.5"), current = bd("100.5"))
        val r1 = engine.evaluate(t1, h1, s0)
        assertEquals(bd("100"), r1.state.referencePeakUsd)

        // Rolling peak increases to 101.0 (+1.0%) -> should update
        val t2 = t1.plus(1, ChronoUnit.DAYS)
        val h2 = historyWithPeakAndCurrent(t2, peak = bd("101.0"), current = bd("101.0"))
        val r2 = engine.evaluate(t2, h2, r1.state)
        assertEquals(bd("101.0"), r2.state.referencePeakUsd)
    }

    private fun bd(s: String) = BigDecimal(s)

    private fun historyWithPeakAndCurrent(now: Instant, peak: BigDecimal, current: BigDecimal): List<PricePoint> {
        // Create points within lookback window:
        // - a peak 10 days ago
        // - current now
        val peakTime = now.minus(10, ChronoUnit.DAYS)
        return listOf(
            PricePoint(peakTime, peak),
            PricePoint(now, current),
        )
    }
}
