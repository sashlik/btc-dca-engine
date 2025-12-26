# BTC DCA Engine (Kotlin)

A production-ready **DCA + drawdown-tiers engine** written in Kotlin and designed to run as a **Cloud Run Job**.

The engine periodically:
1. Fetches the current BTC price
2. Persists it as a time-bucketed price point
3. Evaluates a DCA strategy with drawdown tiers
4. Updates persistent strategy state
5. Sends Telegram notifications when buy signals are generated

> ⚠️ This project **does not place orders**. It only produces buy recommendations.

---

## Core strategy rules

- **Custom monthly period**
    - Month starts at configurable `monthStartDay` (e.g. day 2)
- **Base DCA**
    - Once per period (default: `$250`)
- **Monthly cap**
    - Hard upper limit per period (default: `$1000`)
- **Drawdown tiers**
    - When drawdown deepens into a higher tier, the engine buys **up to the tier target**, not a fixed amount
- **Rolling peak**
    - Peak price is calculated over a rolling lookback window (e.g. 90 days)
- **Anti-spam logic**
    - Re-entering the same tier does not trigger repeated buys

---

## Architecture overview

PriceSource (CoinGecko)
↓
PriceHistoryRepository (Firestore)
↓
StrategyEngine (pure logic)
↓
StrategyStateRepository (Firestore)
↓
TelegramNotifier

### Key design principles
- **Pure strategy logic** (easy to test, reason about, and evolve)
- **Idempotent execution** (safe retries / multiple runs)
- **Explicit state transitions**
- **Fail-fast behavior**

## Running locally (Firestore emulator)

### Start Firestore emulator
```bash
docker compose down
docker compose up
```
(No volume attached → clean database on each restart.)

Cloud Run Job

The project is designed to run as a Cloud Run Job:
•	one execution = one evaluation
•	safe to run via cron or manually
•	Firestore used for persistence
•	environment variables for configuration

Required environment variables
TELEGRAM_BOT_TOKEN
TELEGRAM_CHAT_ID
GOOGLE_CLOUD_PROJECT

What this project intentionally does NOT do
•	No order execution
•	No exchange integration
•	No portfolio tracking
•	No predictions

This is a decision engine, not a trading bot.

