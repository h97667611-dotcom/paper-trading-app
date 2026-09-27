# Changelog

All notable changes to this project are documented in this file.
The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project uses simple `versionCode`/`versionName` semantic-style
versioning (`MAJOR.MINOR.PATCH`).

## [1.0.0] - 2026-09-27

### Added
- Initial release of Paper Trader: a paper-trading crypto app for Android.
- Dark FinTech-style UI (Jetpack Compose, Material 3) with Home/Dashboard,
  Markets, Positions (Portfolio), Trade History (Orders), and Profile tabs.
- Demo paper-trading account created on first launch with 10,000 USDT
  virtual starting capital and no open positions.
- Market data via the public CoinGecko API: prices, 24h change, market cap,
  volume, 24h high/low, all-time high/low, coin logos, and historical price
  charts (1H/4H/1D/1W/1M/1Y).
- DEX pair data via the public DexScreener API, browsable from a dedicated
  Markets tab (chain, DEX, liquidity, volume, price change).
- Coin search, Trending / Top Gainers / Top Losers / Popular Coins sections.
- Coin detail screen with an interactive price chart and Buy/Sell actions.
- Order screen supporting simulated Market and Limit orders, with an order
  confirmation dialog before any paper trade is placed.
- Local, framework-free `PaperTradingEngine` implementing: buy/sell
  validation, weighted-average entry price, realized P&L on sell, unrealized
  P&L on open positions, and add/withdraw funds validation.
- Positions screen with live unrealized P&L and a "Close Position" action.
- Trade History screen with All / Buys / Sells / Profitable / Losses filters.
- Funds management (Add Funds / Withdraw Funds) with a running Funds History
  ledger (type, amount, resulting balance, timestamp).
- Portfolio dashboard: total value, today's P&L, total P&L, available cash,
  invested value, and a portfolio value chart across 1D/1W/1M/3M/1Y/ALL.
- Local persistence via Room: cash balance, positions, orders, trades, funds
  history, portfolio snapshots, and settings all survive an app restart.
- Reset Paper Account flow with an explicit confirmation dialog.
- Networking layer with in-memory caching, retry-with-backoff, and an
  offline/stale-cache fallback for both CoinGecko and DexScreener calls.
- Unit tests for the paper-trading engine (buy/sell/funds/P&L) and
  instrumented tests for Room persistence.
- GitHub Actions CI (`android.yml`): lint, unit tests, instrumented tests,
  and a debug APK build on every push/PR.
- GitHub Actions release workflow (`release.yml`): signed release APK/AAB
  build from a `v*.*.*` tag, using GitHub Secrets for signing.

### Disclaimer
This is a simulation only. No real cryptocurrency is bought or sold, no
order is ever sent to a real exchange, no wallet is touched, and no real
money is deposited or withdrawn. All funds, trades, gains, and losses in
this app are entirely virtual.
