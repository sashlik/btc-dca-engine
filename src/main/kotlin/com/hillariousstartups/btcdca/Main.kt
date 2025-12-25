package com.hillariousstartups.btcdca

import java.math.BigDecimal
import java.nio.file.Paths
import java.time.Instant

/**
 * Minimal entrypoint just to show how the engine can be wired with a state file.
 * No Telegram integration yet.
 */
fun main() {
    val env = System.getenv("TEST") ?: "not set"
    println("It runs ok. TEST=$env")
    if (true) {
        return
    }
    val config = StrategyConfig(
        // You can tweak config here for local experiments.
        monthStartDay = 2,
    )

    val repo = FileStateRepository(Paths.get("data/state.json"))
    val state = repo.load()

    // Placeholder: replace with real market price feed later.
    val now = Instant.now()
    val history = listOf(
        PricePoint(now.minusSeconds(3600 * 24), BigDecimal("95000")),
        PricePoint(now, BigDecimal("93000")),
    )

    val engine = StrategyEngine(config)
    val result = engine.evaluate(now, history, state)

    result.signals.forEach { println("SIGNAL: buy ${it.amountUsd} USD | ${it.reason}") }

    repo.save(result.state)
}
