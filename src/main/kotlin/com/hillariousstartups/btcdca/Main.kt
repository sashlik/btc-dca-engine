import com.google.cloud.firestore.FirestoreOptions
import com.hillariousstartups.btcdca.CoinGeckoPriceSource
import com.hillariousstartups.btcdca.FirestorePriceHistoryRepository
import com.hillariousstartups.btcdca.FirestoreStrategyStateRepository
import com.hillariousstartups.btcdca.PriceHistoryRepository
import com.hillariousstartups.btcdca.PriceSource
import com.hillariousstartups.btcdca.StrategyConfig
import com.hillariousstartups.btcdca.StrategyOrchestrator
import com.hillariousstartups.btcdca.StrategyStateRepository
import java.time.Instant

fun main() {
    val firestore = FirestoreOptions.getDefaultInstance().service

    val config = StrategyConfig()
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
    println("New state: ${result.state}")
}