package com.hillariousstartups.btcdca

import com.google.cloud.firestore.Firestore
import com.google.cloud.firestore.FirestoreOptions
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

    // Инициализация Firestore
    val firestore: Firestore = FirestoreOptions.getDefaultInstance().service

    // Получаем первую запись из коллекции
    val querySnapshot = firestore
        .collection("test-collection")
        .limit(1)
        .get()
        .get()   // blocking, для batch job это нормально

    if (querySnapshot.isEmpty) {
        println("Collection is empty")
        return
    }

    val doc = querySnapshot.documents.first()
    val firstName = doc.getString("firstName")

    println("firstName = $firstName")

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
