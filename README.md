# KaChat Chess

Chess on Kaspa: online, every move is a Kaspa transaction, so every game is on chain for good.
Public 1v1 rooms, private games by code, and eight-player knockout tournaments, five
minutes a side, with monthly and all-time leaderboards.

KaChat Chess is KaChat's Chess Online made into its own Android app, matching the iPhone
app (KaChat-Chess-iPhone) screen for screen. It plays the same games as KaChat, against
the same players: the protocol is [ONLINE_CHESS.md](ONLINE_CHESS.md) (the `#chess-arena`
broadcast channel, `kchat:1:bcast:chess-arena:` payloads, the leaderboard from the KaChat
indexer's `/chess/leaderboard`).

## The app

- **Play**: 1v1 (the public room taking players, or a friend by code) and Tournaments,
  each with Active and Finished games to watch; and Bots, a ladder of five bots (level 1
  to 5) whose games are stamped on chain once, when they end (ONLINE_CHESS.md §7). A win can be
  shared to KaPost as a challenge.
- **Leaderboard**: 1v1 games, tournaments and each bot (fewest moves to win), this month and
  all time.
- **Wallet**: the one address every move is signed by: balance, history, UTXOs, Receive
  and Send.
- **Settings**: appearance, language and currency, security, the Kaspa node and indexer,
  the seed phrase, Log Out, Remove Account.

Sign-in is KaChat's: create a new account or import a seed phrase.

## Building

Open the project in Android Studio, or from the command line:

```
./gradlew :app:assembleGithubDebug
```

One app module, `kaplay.kachat.app` (`kaplay.kachat.app.debug` for debug builds), minSdk 26. Two
distribution flavours as in KaChat: `play` (the Play Store bundle, `bundlePlayRelease`) and `github`
(the handed-out APK, `assembleGithubRelease`). Release signing reads a gitignored `keystore.properties`.

The version and build number live in `app/build.gradle.kts` (`versionName`, `versionCode`,
`kachatBuildNumber`).

## Where it came from

The starting point is KaChat-Android's app as it was at `9289fad8`, cut down to chess the
same way the iPhone app was cut down from KaChat-iPhone. Most of KaChat's service layer is
still there underneath (the wallet, the node pool, the broadcast repository every move is
sent through, and `ChatRepository`, whose contacts and bookkeeping the wallet's sends use,
with its chat syncing switched off: `ChatRepository.SYNCS_CHATS`).
