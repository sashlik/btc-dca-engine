package com.hillariousstartups.btcdca

import com.google.cloud.NoCredentials
import com.google.cloud.firestore.FirestoreOptions
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant
import kotlin.test.Ignore

class E2ETest {

    @Ignore
    @Test
    fun `e2e test`() {
        // Kotlin
        System.setProperty("FIRESTORE_EMULATOR_HOST", "localhost:8200")
        System.setProperty("GOOGLE_CLOUD_PROJECT", "demo-btc")

        val config = StrategyConfig()
        val strategyId = "btc-dca-sim"
        val firestore = FirestoreOptions.newBuilder()
            .setProjectId("demo-btc")
            .setHost("localhost:8200") // or "http://localhost:8200" depending on SDK; both generally work
            .setCredentials(NoCredentials.getInstance())
            .build()
            .service

        val historyRepo = FirestorePriceHistoryRepository(
            firestore = firestore,
            strategyId = strategyId,
            idempotentBySlot = true,
            slotSeconds = 10
        )

        val stateRepo = FirestoreStrategyStateRepository(firestore)

        val prices = listOf(
            bd("100000"),
            bd("80000"),
            bd("70000"),
            bd("69500")
        )
        var now = Instant.parse("2025-12-25T00:00:00Z")

        for ((i, price) in prices.withIndex()) {
            val orchestrator = StrategyOrchestrator(
                strategyId = strategyId,
                config = config,
                priceSource = FixedPriceSource(price),
                historyRepo = historyRepo,
                stateRepo = stateRepo
            )

            val result = orchestrator.runOnce(now)

            println("=== RUN ${i + 1} ===")
            println("now=$now price=$price")
            println("signals=${result.signals.size}")
            result.signals.forEach { println("  $it") }
            println("state=${result.state}")
            println()

            now = now.plusSeconds(15)
        }
    }

    private fun bd(v: String) = BigDecimal(v)

}