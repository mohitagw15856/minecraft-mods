# minecraft-mods

Original Minecraft mods by Mohit — one Bedrock add-on and eleven Paper/Spigot/Purpur server plugins. Every plugin is server-side only: players join and play, nothing to install client-side. All smoke-tested together on Paper 1.21.4.

| Plugin | One-liner | Command |
|---|---|---|
| [BountyBoard](bounty-board-paper) | Daily rotating hunt/mine/deliver bounties with tiers and streaks | `/bounty` |
| [TimeCapsule](time-capsule-paper) | Seal a chest (with a note) that only opens days later | `/capsule` |
| [GhostReplay](ghost-replay-paper) | A ghost retraces your last 30 seconds to your death point | `/ghost` |
| [SwornRivals](sworn-rivals-paper) | Secret weekly rival pairings — outscore yours to steal a buff | `/rivals` |
| [MobMoods](mob-moods-paper) | Mobs spawn cheerful/grumpy; feed berries to change their behavior | `/moods` |
| [CartographersContracts](cartographers-contracts-paper) | Buy expeditions to distant structures; arriving pays out | `/contract` |
| [SeasonPass](season-pass-paper) | Free cosmetic battle pass — 20 tiers of colors, trails, titles | `/pass` |
| [HeirloomItems](heirloom-items-paper) | Your designated item passes to an heir on death, with lineage | `/heirloom` |
| [WeatherInsurance](weather-insurance-paper) | Insure a chunk against creepers, fire and lightning | `/insure` |
| [EchoChambers](echo-chambers-paper) | Bind chat recordings to lodestones that whisper to passers-by | `/echo` |
| [TheCollector](the-collector-paper) | A daily absurd item request — first to deliver wins the pot | `/collector` |

Each folder has its own README with full mechanics. Install any plugin by dropping its jar from [Releases](../../releases) into your server's `plugins/` folder (Paper/Spigot/Purpur 1.21.4+, Java 21).

---

## Bounty Board (flagship)

A daily rotating quest system for Minecraft — available for **Bedrock Edition** (add-on) and **Java Edition servers** (Paper/Spigot/Purpur plugin).

Every in-game day the board offers 3 bounties — Common, Rare, and Epic — deterministically generated so every player on the world sees the same board. Complete them for emeralds, diamonds, and XP; complete one every day to build a **streak** worth up to +100% bonus emeralds.

| | Bedrock | Java servers |
|---|---|---|
| Folder | [`bounty-board/`](bounty-board) | [`bounty-board-paper/`](bounty-board-paper) |
| Artifact | `BountyBoard.mcpack` (behavior pack, Script API) | `bounty-board-x.y.z.jar` (Paper plugin) |
| Open the board | Use a **Book** | `/bounty` (aliases `/bb`, `/bounties`) |
| Requirements | Minecraft Bedrock 1.21+, no experiments | Paper/Spigot/Purpur 1.21.4+, Java 21 |
| Client install needed | Yes (or applied by the world/realm) | **No** — server-side only |

## Features

- **3 bounty types:** Hunt (kill mobs), Mine (break blocks — deepslate ore variants count), Deliver (turn in items, which are consumed).
- **Tiers & rewards:** Common (4 emeralds, 2 XP levels) · Rare (10 emeralds, 1 diamond, 5 levels) · Epic (16 emeralds, 3 diamonds, 10 levels).
- **Streaks:** +25% emeralds per consecutive day, capped at +100%.
- **One active bounty at a time**, expires at sunrise, abandon anytime.
- Live progress in the action bar; sounds and chat notifications; per-player persistence.

## Build from source

**Bedrock:** no build step — zip the pack folder:

```sh
cd bounty-board && zip -r ../BountyBoard.mcpack manifest.json pack_icon.png scripts -x '.*'
```

**Paper plugin** (needs JDK 21):

```sh
cd bounty-board-paper && ./gradlew build
# output: build/libs/bounty-board-1.0.0.jar
```

## Install

**Bedrock:** double-click `BountyBoard.mcpack`, then enable the behavior pack in your world settings.

**Java server:** drop the jar into your server's `plugins/` folder and restart. Players just run `/bounty` — nothing to install client-side.

## License

MIT — see [LICENSE](LICENSE).
