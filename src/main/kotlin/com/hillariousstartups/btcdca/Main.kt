import com.google.cloud.firestore.FirestoreOptions
import com.hillariousstartups.btcdca.CoinGeckoPriceSource
import com.hillariousstartups.btcdca.FirestorePriceHistoryRepository
import com.hillariousstartups.btcdca.FirestoreStrategyStateRepository
import com.hillariousstartups.btcdca.PriceHistoryRepository
import com.hillariousstartups.btcdca.PriceSource
import com.hillariousstartups.btcdca.StrategyConfig
import com.hillariousstartups.btcdca.StrategyOrchestrator
import com.hillariousstartups.btcdca.StrategyStateRepository
import com.hillariousstartups.btcdca.TgBot
import com.hillariousstartups.btcdca.Tier
import com.hillariousstartups.btcdca.getEnv
import java.math.BigDecimal
import java.time.Instant

fun main() {
    val firestore = FirestoreOptions.getDefaultInstance().service

    val config = StrategyConfig(
        monthStartDay = getEnv("MONTH_START_DAY", 2),
        baseDcaUsd = getEnv("TIER1", BigDecimal("250")),
        monthlyCapUsd = getEnv("TIER5", BigDecimal("1000")),
        tiers = listOf(
            Tier(
                drawdownFromInclusive = BigDecimal("0.00"),
                drawdownToExclusive = BigDecimal("0.15"),
                targetMonthlySpendUsd = getEnv("TIER1", BigDecimal("250"))
            ),
            Tier(
                drawdownFromInclusive = BigDecimal("0.15"),
                drawdownToExclusive = BigDecimal("0.25"),
                targetMonthlySpendUsd = getEnv("TIER2", BigDecimal("400"))
            ),
            Tier(
                drawdownFromInclusive = BigDecimal("0.25"),
                drawdownToExclusive = BigDecimal("0.35"),
                targetMonthlySpendUsd = getEnv("TIER3", BigDecimal("600"))
            ),
            Tier(
                drawdownFromInclusive = BigDecimal("0.35"),
                drawdownToExclusive = BigDecimal("0.45"),
                targetMonthlySpendUsd = getEnv("TIER4", BigDecimal("800"))
            ),
            Tier(
                drawdownFromInclusive = BigDecimal("0.45"),
                drawdownToExclusive = null,
                targetMonthlySpendUsd = getEnv("TIER5", BigDecimal("1000"))
            )
        )

    )
    val priceSource: PriceSource = CoinGeckoPriceSource()

    val historyRepo: PriceHistoryRepository = FirestorePriceHistoryRepository(
        firestore = firestore,
        strategyId = "btc-dca",
        idempotentBySlot = true
    )

    val stateRepo: StrategyStateRepository = FirestoreStrategyStateRepository(firestore)

    val orchestrator = StrategyOrchestrator(
        strategyId = "btc-dca",
        config = config,
        priceSource = priceSource,
        historyRepo = historyRepo,
        stateRepo = stateRepo
    )

    val result = orchestrator.runOnce(Instant.now())

    println("Signals: ${result.signals.size}")
    result.signals.forEach { println(it) }

    val tgBot = TgBot(
        botToken = getEnv("TG_BOT_TOKEN", ""),
        chatId = getEnv("TG_CHAT_ID", 0L)
    )
    tgBot.send("test")
}

