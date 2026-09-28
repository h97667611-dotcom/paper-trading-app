# Ghosttrade 📈

A modern, dark-themed **Android paper-trading app for crypto**, built with
Kotlin and Jetpack Compose. It feels like a real trading platform, but every
trade uses **virtual money only** — no real cryptocurrency is ever bought,
sold, or held, no order is sent to any exchange, and no wallet or real funds
are touched.

> ⚠️ **Disclaimer:** This project is a simulation for educational and
> practice purposes only. All balances, trades, profits, and losses are
> virtual. Nothing in this app constitutes financial advice, and it must
> never be connected to a real exchange account, real wallet, or real funds.

---

## Table of Contents

- [Screenshots](#screenshots)
- [Features](#features)
- [Tech Stack](#tech-stack)
- [Architecture](#architecture)
- [Prerequisites](#prerequisites)
- [Installation](#installation)
- [Android Studio Setup](#android-studio-setup)
- [Build Instructions](#build-instructions)
  - [Debug Build](#debug-build)
  - [Release Build](#release-build)
- [API Configuration](#api-configuration)
- [Paper Trading System](#paper-trading-system)
- [Database](#database)
- [Testing](#testing)
- [CI/CD (GitHub Actions)](#cicd-github-actions)
- [Creating a GitHub Release](#creating-a-github-release)
- [Troubleshooting](#troubleshooting)
- [Project Structure](#project-structure)
- [License](#license)
- [Disclaimer](#disclaimer)

---

## Screenshots

Add screenshots/GIFs of the running app to `docs/screenshots/` and reference
them here, e.g.:

```md
![Dashboard](docs/screenshots/dashboard.png)
![Markets](docs/screenshots/markets.png)
![Coin Detail](docs/screenshots/coin_detail.png)
![Order Confirmation](docs/screenshots/order_confirmation.png)
```

*(Placeholders only — this repository ships without binary screenshots.)*

## Features

- **Dashboard** — total portfolio value, today's P&L, total P&L, available
  cash, invested value, and a portfolio value chart (1D/1W/1M/3M/1Y/ALL).
- **Markets** — search, Trending, Top Gainers, Top Losers, Popular Coins,
  and a dedicated DEX Pairs tab (CoinGecko + DexScreener).
- **Coin Detail** — interactive price chart (1H/4H/1D/1W/1M/1Y), market cap,
  volume, 24h high/low, all-time high/low, Buy/Sell actions.
- **Order screen** — Market and Limit order types, live order value and
  simulated-fee estimate, and an order confirmation dialog before any trade
  is placed.
- **Positions** — open positions with entry price, current price, position
  value, unrealized P&L (value + %), and a Close Position action.
- **Trade History** — every filled paper trade with All / Buys / Sells /
  Profitable / Losses filters.
- **Funds** — Add Funds / Withdraw Funds for the virtual cash balance, plus
  a running Funds History ledger.
- **Profile / Settings** — account info, funds shortcut, trading settings,
  currency, notifications, dark mode, API/data-source info, and **Reset
  Paper Account** (with a confirmation dialog).
- **Persistence** — cash balance, positions, orders, trades, funds history,
  and portfolio snapshots all survive an app restart (Room database).
- **Resilient networking** — in-memory response caching, retry-with-backoff,
  and a stale-cache offline fallback for both external APIs.
- **Demo account** — a fresh install starts with **10,000 USDT** virtual
  cash and no open positions.

## Tech Stack

| Layer            | Choice                                                        |
|------------------|----------------------------------------------------------------|
| Language         | Kotlin                                                         |
| UI               | Jetpack Compose + Material 3                                   |
| Navigation       | Navigation Compose                                              |
| Async            | Kotlin Coroutines + Flow                                        |
| Local storage    | Room                                                            |
| Networking       | Retrofit2 + OkHttp3 (+ logging interceptor)                     |
| Images           | Coil                                                            |
| Charts           | Custom Compose `Canvas` line chart (no external chart library)  |
| DI               | Lightweight manual service locator (`AppContainer`) — no Hilt   |
| Testing          | JUnit4, kotlinx-coroutines-test, Turbine, AndroidX Test/Espresso|
| Build            | Gradle (Kotlin DSL), Android Gradle Plugin, KSP                 |
| CI/CD            | GitHub Actions                                                  |

No external chart or DI library was pulled in on purpose, to keep the
dependency surface small and the project easy to open and build.

## Architecture

Clean, layered MVVM:

```
UI (Compose screens)
   ↓ observes
ViewModel (per screen, exposes StateFlow<UiState>)
   ↓ calls
Repository (PaperTradingRepository, MarketRepository)
   ↓ uses
Domain Engine (PaperTradingEngine — pure Kotlin, no Android deps)
   +
Local Database (Room)      Remote API (Retrofit: CoinGecko, DexScreener)
```

- **`domain/model`** — plain Kotlin data classes (`Coin`, `Position`,
  `Order`, `Trade`, `FundsTransaction`, `PortfolioState`), framework-free.
- **`domain/engine/PaperTradingEngine`** — the trading logic: buy/sell
  validation, weighted-average entry price, realized/unrealized P&L,
  add/withdraw funds. Pure functions, no I/O, so it's trivially unit
  testable (see `PaperTradingEngineTest`).
- **`data/local`** — Room entities, DAOs, and `AppDatabase`.
- **`data/remote`** — Retrofit interfaces + DTOs for CoinGecko and
  DexScreener, kept isolated behind repositories.
- **`data/repository`** — `MarketRepository` (external market data, with
  caching/retry/offline-fallback) and `PaperTradingRepository` (bridges the
  engine to Room persistence).
- **`di/AppContainer`** — a minimal manual DI container (no Hilt/Dagger) so
  the project builds with just the Android Gradle Plugin.
- **`ui/screens/*`** — one package per screen, each with its own
  `ViewModel` + `Composable` screen.
- **`ui/navigation`** — `NavGraph` + bottom navigation bar (Home, Markets,
  Portfolio, Orders, Profile).

## Prerequisites

- **Android Studio** Ladybug (2024.2) or newer
- **JDK 17**
- An internet connection for Gradle sync (dependencies) and for the app
  itself to reach the public CoinGecko/DexScreener APIs at runtime
- No API keys are required (see [API Configuration](#api-configuration))

## Installation

```bash
git clone https://github.com/<your-org>/paper-trading-app.git
cd paper-trading-app
```

## Android Studio Setup

1. Open Android Studio → **Open** → select the cloned `paper-trading-app`
   folder.
2. Let Gradle sync. Android Studio will download the Gradle wrapper jar and
   all dependencies automatically on first sync (this repository ships the
   wrapper *scripts* — `gradlew` / `gradlew.bat` — and
   `gradle-wrapper.properties`, but not the binary `gradle-wrapper.jar`, per
   standard practice for keeping binaries out of git history in this
   context; Android Studio fetches it transparently).
3. Select a device/emulator (minSdk 26 / Android 8.0+) and press **Run ▶**.

## Build Instructions

### Debug Build

```bash
./gradlew assembleDebug
# APK output: app/build/outputs/apk/debug/app-debug.apk
```

### Release Build

```bash
./gradlew assembleRelease   # signed APK
./gradlew bundleRelease     # signed AAB (for Play Store)
```

A release build is only **signed** when the following environment variables
are present (see [Release Workflow / GitHub Secrets](#creating-a-github-release)):

- `KEYSTORE_PATH`
- `KEYSTORE_PASSWORD`
- `KEY_ALIAS`
- `KEY_PASSWORD`

Without them, `app/build.gradle.kts` falls back to the debug signing config
so the release build still completes locally (useful for smoke-testing a
release build without a real signing key).

## API Configuration

This app uses two **public, key-free** APIs:

| API         | Base URL                              | Auth required? |
|-------------|----------------------------------------|-----------------|
| CoinGecko   | `https://api.coingecko.com/api/v3/`    | No (public tier)|
| DexScreener | `https://api.dexscreener.com/`         | No (public API) |

Both are wrapped behind clean interfaces (`CoinGeckoApi`, `DexScreenerApi`)
in `data/remote/`, and consumed only through `MarketRepository`, so either
can be swapped for a paid/authenticated tier later without touching any
other layer.

If you do add a key (e.g. to move to CoinGecko's Pro tier):

1. Copy `.env.example` to a local `local.properties` entry (or your own
   `.env`, never committed) — see the two keys below.
2. `app/build.gradle.kts` reads them into `BuildConfig` fields at build time.
3. `di/NetworkModule.kt` attaches the key as a header only when it's set.

```properties
# .env.example (documentation only — do NOT commit a real .env)
COINGECKO_API_KEY=
DEXSCREENER_API_KEY=
```

**No secret is ever hardcoded in Kotlin source or committed to git.**

## Paper Trading System

All trading logic lives in `domain/engine/PaperTradingEngine.kt` — a pure,
side-effect-free class:

- **Buy** — validates quantity/price/available cash, opens or adds to a
  position, computes the new **weighted-average entry price**.
- **Sell** — validates quantity/held amount, computes **realized P&L**
  against the average entry price, reduces or closes the position.
- **Close Position** — sells the full held quantity at the current market
  price.
- **Unrealized P&L** — `(currentPrice − avgEntryPrice) × quantity` for any
  open position.
- **Add/Withdraw Funds** — validates the amount and available balance.

`PaperTradingRepository` is the only class that both calls the engine *and*
persists its result to Room, so the engine itself stays 100% testable
without Android or a database (see [Testing](#testing)).

**No real order is ever sent to an exchange** — Market and Limit orders are
booked entirely against locally-cached CoinGecko prices.

## Database

Room database (`papertrader.db`) with these tables:

- `account` — the single virtual cash balance
- `positions` — open holdings (coin, quantity, average entry price)
- `orders` — every submitted order (filled, open/limit-pending, or handled)
- `trades` — every filled trade (buy/sell, price, value, realized P&L)
- `funds_history` — every deposit/withdrawal with the resulting balance
- `portfolio_snapshots` — periodic total-value snapshots that back the
  dashboard's performance chart
- `user_settings` — currency/notification/dark-mode preferences

All of this persists across app restarts by design.

## Testing

```bash
# Unit tests (paper-trading engine — no emulator required)
./gradlew testDebugUnitTest

# Instrumented tests (Room DAO round-trips — requires a connected device/emulator)
./gradlew connectedDebugAndroidTest

# Lint
./gradlew lint
```

- `app/src/test/.../PaperTradingEngineTest.kt` — 16 unit tests covering buy
  orders, sell orders, weighted-average entry price, realized/unrealized
  P&L, insufficient-funds/insufficient-position validation, add/withdraw
  funds, and portfolio valuation.
- `app/src/androidTest/.../AppDatabaseTest.kt` — instrumented Room tests
  verifying the account balance, positions, and funds history persist and
  round-trip correctly.

> **Note on this repository's provenance:** this project was generated in a
> sandboxed environment without an Android SDK, emulator, or network access,
> so the test suite above has **not** been executed against a real Gradle/
> Android toolchain as part of producing this repository. Please run the
> commands above yourself (locally or via the included GitHub Actions
> workflow) before treating this as verified — the CI workflow in
> `.github/workflows/android.yml` runs the full suite automatically on the
> first push.

## CI/CD (GitHub Actions)

### `.github/workflows/android.yml`
Runs on every push and pull request to `main`:
1. Checkout → JDK 17 → Gradle setup
2. `./gradlew lint`
3. `./gradlew testDebugUnitTest`
4. `./gradlew assembleDebug`
5. A second job runs `./gradlew connectedDebugAndroidTest` on an emulator.

Any failing step fails the workflow (red ❌), exactly as required.

### `.github/workflows/release.yml`
Triggered by pushing a tag matching `v*.*.*` (or manually via
`workflow_dispatch`):
1. Decodes a base64-encoded keystore from the `KEYSTORE_BASE64` secret
2. Builds a signed `app-release.apk` **and** `app-release.aab`
3. Uploads both as workflow artifacts
4. Attaches both to the matching GitHub Release, if the trigger was a tag

## Creating a GitHub Release

1. **Add these GitHub Secrets** (Settings → Secrets and variables →
   Actions) so `release.yml` can sign the build:

   | Secret name          | Contents                                          |
   |-----------------------|----------------------------------------------------|
   | `KEYSTORE_BASE64`    | `base64 -w0 your.keystore` output                 |
   | `KEYSTORE_PASSWORD`  | Keystore password                                  |
   | `KEY_ALIAS`          | Signing key alias                                  |
   | `KEY_PASSWORD`       | Signing key password                               |

   **Never commit the real keystore or these values to the repository.**

2. Bump `versionCode` / `versionName` in `app/build.gradle.kts` and add an
   entry to `CHANGELOG.md`.
3. Tag and push:
   ```bash
   git tag v1.0.0
   git push origin v1.0.0
   ```
4. GitHub Actions builds the signed APK/AAB and attaches them to the
   `v1.0.0` GitHub Release automatically.

## Troubleshooting

| Symptom                                             | Fix                                                                 |
|------------------------------------------------------|----------------------------------------------------------------------|
| Gradle sync fails / can't find `gradle-wrapper.jar`  | Open the project in Android Studio once (it fetches the jar), or run `gradle wrapper` if you have a system Gradle install. |
| Network errors loading markets                       | The app uses public CoinGecko/DexScreener endpoints; confirm your network/firewall allows outbound HTTPS to `api.coingecko.com` and `api.dexscreener.com`. |
| "Too many requests" / rate limited                    | The public CoinGecko tier has a modest rate limit; the app retries with backoff and falls back to its last cached data — wait a minute and refresh. |
| Release build isn't signed                            | Ensure `KEYSTORE_PATH`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` are set as environment variables (locally) or GitHub Secrets (CI). |
| App shows 10,000 USDT after I added funds             | Funds you add/withdraw are virtual and local only — check you're looking at the same install/device; a "Reset Paper Account" also restores 10,000 USDT. |

## Project Structure

```
paper-trading-app/
├── .github/
│   └── workflows/
│       ├── android.yml
│       └── release.yml
├── app/
│   ├── build.gradle.kts
│   ├── proguard-rules.pro
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── java/com/papertrader/app/
│       │   │   ├── data/          (local Room + remote API + repositories)
│       │   │   ├── di/            (AppContainer, NetworkModule)
│       │   │   ├── domain/        (models + PaperTradingEngine)
│       │   │   ├── ui/            (theme, components, navigation, screens)
│       │   │   ├── util/          (NetworkResult, error messages)
│       │   │   ├── MainActivity.kt
│       │   │   └── PaperTraderApplication.kt
│       │   └── res/               (colors, strings, themes, launcher icon)
│       ├── test/                  (JUnit — PaperTradingEngineTest)
│       └── androidTest/           (instrumented — AppDatabaseTest)
├── docs/
│   └── screenshots/
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── gradlew / gradlew.bat
├── gradle/wrapper/gradle-wrapper.properties
├── .gitignore
├── .env.example
├── LICENSE
├── README.md
└── CHANGELOG.md
```

## License

Released under the [MIT License](LICENSE).

## Disclaimer

**Ghosttrade is a simulation only.** It does not buy or sell real
cryptocurrency, does not place real exchange orders, does not touch any
real wallet, and does not process real deposits or withdrawals. All funds,
trades, gains, and losses shown in the app are entirely virtual and for
practice/educational purposes only.
