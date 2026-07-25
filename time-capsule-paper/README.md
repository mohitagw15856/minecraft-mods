# TimeCapsule (Paper plugin)

Seal a chest as a time capsule that only opens after a chosen number of in-game days.

- `/capsule seal <days> [message]` — look at a chest and seal it (1-30 days, max 5 per player). Optional note shown to whoever opens it.
- `/capsule check` — status of the capsule you're looking at.
- `/capsule list` — your capsules and time remaining.
- Sealed capsules can't be opened, broken, or blown up. Opening after the unlock day broadcasts it to the server.
- Limitation: seals the single chest block you target (double chests: seal both halves).

Install: drop the jar in `plugins/`, restart. Paper/Spigot/Purpur 1.21.4+, Java 21.
