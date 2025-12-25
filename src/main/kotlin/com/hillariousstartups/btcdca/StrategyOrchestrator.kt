package com.hillariousstartups.btcdca

import com.hillariousstartups.btcdca.StrategyEngine.EvaluationResult
import java.time.Instant
import java.time.temporal.ChronoUnit

class StrategyOrchestrator(
    private val strategyId: String,
    private val config: StrategyConfig,
    private val priceSource: PriceSource,
    private val historyRepo: PriceHistoryRepository,
    private val stateRepo: StrategyStateRepository,
) {

    private val engine = StrategyEngine(config)

    /**
     * One end-to-end run:
     * - fetch price
     * - persist price point
     * - load window history
     * - load state
     * - evaluate
     * - persist new state
     */
    fun runOnce(now: Instant = Instant.now()): EvaluationResult {
        val price = priceSource.fetchNowUsd(now)

        // 1) persist current price point (idempotent per slot if repo is configured so)
        historyRepo.append(PricePoint(time = now, priceUsd = price))

        // 2) load rolling window history (inclusive)
        val since = now.minus(config.lookbackDays.toLong(), ChronoUnit.DAYS)
        val history = historyRepo.loadSince(since, now)

        // 3) load previous state
        val state = stateRepo.load(strategyId)

        // 4) evaluate strategy
        val result = engine.evaluate(now, history, state)

        // 5) persist updated state
        stateRepo.save(strategyId, result.state)

        return result
    }
}