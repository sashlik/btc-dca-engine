package com.hillariousstartups.btcdca

import com.fasterxml.jackson.databind.ObjectMapper
import com.google.cloud.firestore.Firestore
import com.google.cloud.firestore.FirestoreOptions
import java.math.BigDecimal
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Paths
import java.time.Duration
import java.time.Instant

private val http = HttpClient.newBuilder()
    .connectTimeout(Duration.ofSeconds(5))
    .build()

private val json = ObjectMapper()

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



    fun btcUsdFromCoinGecko(): BigDecimal {
        val request = HttpRequest.newBuilder()
            .uri(URI.create(
                "https://api.coingecko.com/api/v3/simple/price?ids=bitcoin&vs_currencies=usd"
            ))
            .timeout(Duration.ofSeconds(5))
            .GET()
            .build()

        val response = http.send(request, HttpResponse.BodyHandlers.ofString())
        require(response.statusCode() == 200) { "HTTP ${response.statusCode()}" }

        val root = json.readTree(response.body())
        return root["bitcoin"]["usd"].decimalValue()
    }
    println("BTC/USD = ${btcUsdFromCoinGecko()}")

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
