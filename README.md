# ShoreCrash

Crash mini-game plugin targeting Paper 1.21.8 (works on 1.21.x): runs a round every 30s by default, shows a hologram chart, lets players bet with `/crash <amount>` and cash out with `/crashcashout`, and drops a TNT marker where the multiplier crashes. All timing, limits, lines, and messages are configurable.

## Features
- Automatic crash rounds on a configurable interval.
- Exponential multiplier curve with random crash point; hologram chart is a multi-line tilted graph (slash line by default) that curves up until crash.
- Configurable hologram content and positioning, spawnable with `/crashholo set`.
- TNT marker hologram at the crash point with the final multiplier in its name.
- Bets via `/crash <amount>` (supports suffixes like `4k`, `2m`); cash out live with `/crashcashout`.
- Participant list, pot total, and countdown rendered in the hologram lines.
- Optional Vault economy integration; configurable min/max bets.
- Reloadable config via `/crashreload`.

## Commands
- `/crash <amount>` — join the next round with that bet. Aliases: `/crashbet`, `/bet`.
- `/crashcashout` — cash out your active bet at the current multiplier. Alias: `/cashout`.
- `/crashholo set` — save the hologram anchor at your current position.
- `/crashholo clear` — remove hologram data and despawn it.
- `/crashreload` — reload config and restart the scheduler.

## Permissions
- `shorecrash.bet` (default true)
- `shorecrash.cashout` (default true)
- `shorecrash.admin` (required for hologram and reload)

## Configuration
See `src/main/resources/config.yml` for all options:
- `game.*` — intervals, growth rate, crash variance, bet limits, economy toggle.
- `hologram.*` — enable flag, location, line spacing, chart symbols/width, and line templates (placeholders: `{state}`, `{timer}`, `{multiplier}`, `{chart}`, `{pot}`, `{players}`).
- `tnt-hologram.*` — enable flag, lifespan, and text (placeholder `{multiplier}`).
- `messages.*` — all player-facing text and prefixes.

Placeholders are replaced on every hologram update and when broadcasting round events.

## Building
Requires Java 17+. Package with:

```bash
mvn clean package
```

The shaded jar will be in `target/ShoreCrash-0.1.0-SNAPSHOT-shaded.jar`. Drop it into your Paper 1.21.x `plugins` folder. If using economy, install the Vault plugin plus an economy provider (e.g., EssentialsX). Reload the config after setting the hologram location with `/crashholo set`.
