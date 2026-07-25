# Bounty Board (Minecraft Bedrock add-on)

A daily rotating quest system for Minecraft Bedrock Edition, built with the Script API. No resource pack needed — behavior pack only.

## Features

- **3 bounties per in-game day** (Common / Rare / Epic), deterministically generated — every player on the world sees the same board.
- **Three bounty types:** Hunt (kill mobs), Mine (break blocks), Deliver (turn in items from your inventory).
- **Rewards:** emeralds, diamonds (rare/epic), and XP levels, paid on claim. Delivered items are consumed.
- **Streak system:** complete a bounty on consecutive days for up to +100% bonus emeralds.
- **Bounties expire at sunrise** — one active bounty at a time, abandon anytime.
- New players are handed a Book and a welcome message on first join.

## How to play

Use (right-click / long-press) a **Book** to open the Bounty Board. Pick a bounty, complete it before the next sunrise, then open the board again to claim.

## Install

1. Double-click `BountyBoard.mcpack` (in the parent folder) — Minecraft imports it.
2. In your world settings, enable the **Bounty Board** behavior pack.
3. No experimental toggles required (uses stable `@minecraft/server` 1.17.0 / `@minecraft/server-ui` 1.2.0; min engine 1.21.0).

## Rebuild the .mcpack

```sh
cd bounty-board
zip -r ../BountyBoard.mcpack manifest.json pack_icon.png scripts -x '.*'
```

## Code map

- `scripts/main.js` — entry point: book-use handler, first-join welcome.
- `scripts/bounties.js` — bounty pools, tiers, deterministic daily generation (seeded PRNG on world day + per-world salt).
- `scripts/progress.js` — persistence (player dynamic properties), kill/mine event tracking, deliver polling, reward payout.
- `scripts/ui.js` — ActionForm menus (board, claim, abandon).
