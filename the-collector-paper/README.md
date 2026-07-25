# TheCollector (Paper plugin)

A mysterious collector broadcasts one odd request per in-game day ("Three poisonous potatoes. Do not ask why."). The first player to deliver wins the emerald reward — and a place in the ledger.

- `/collector` — today's request. `/collector deliver` — hand over the goods (must have the full amount; items are consumed).
- `/collector log` — the ledger of past fulfilments.
- 20 handwritten requests rotate deterministically per world; one winner per day, announced server-wide.

Install: drop the jar in `plugins/`, restart. Paper/Spigot/Purpur 1.21.4+, Java 21.
