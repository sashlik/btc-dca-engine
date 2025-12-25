# BTC DCA Engine (Kotlin + Maven)

A small, test-driven core of the strategy we discussed:

- **Base DCA**: $250 per period (a “month” starting on a configurable day-of-month, e.g. every 2nd).
- **Monthly cap**: $1,000 maximum recommended buys per period.
- **Drawdown tiers**: when drawdown deepens, the engine recommends buying the *difference* up to the tier's **target monthly spend**.
- **Rolling peak**: max price over the last **90 days**.
- **Hysteresis**: reference peak only updates if rolling peak exceeds it by **+1%** (prevents noisy “micro reset” near highs).
- **No Telegram yet**: this module only outputs `BuySignal`s + updates persistent state.
- **State storage**: JSON file (later we can replace with DB).

## Project layout

- `StrategyConfig` – all parameters (you can keep it in Kotlin for now).
- `StrategyEngine` – pure logic: `evaluate(now, priceHistory, state) -> signals + newState`.
- `FileStateRepository` – JSON state in `data/state.json` (demo usage in `Main.kt`).
- Tests in `StrategyEngineTest` show reproducible signals.

## How the engine decides buy signals

1. Determine the **period key** (month-like period) using `monthStartDay`.
2. If this is a **new period**, emit base DCA ($250) and count it into spent amount.
3. Compute **rolling peak** over the last `lookbackDays` (default 90).
4. Update **reference peak** with hysteresis (+1%).
5. Compute drawdown from reference peak and choose tier's `targetMonthlySpendUsd`.
6. If `targetMonthlySpendUsd > alreadySpentThisPeriod`, emit an additional buy signal for the difference (bounded by `monthlyCapUsd`).

## Run tests

```bash
mvn test
```

## Run locally (demo)

```bash
mvn -q -DskipTests package
java -cp target/btc-dca-engine-0.1.0-SNAPSHOT.jar com.example.btcdca.MainKt
```

> Note: `Main.kt` uses placeholder prices. Replace them later with a real price feed.

## Next steps (planned)

- Replace placeholder price feed with Kraken/CoinGecko client
- Telegram integration (`sendMessage`)
- Move state from file to Firestore/DynamoDB
- Add “every 4 hours” scheduler adapter (Cloud Run Job / Lambda / cron)
