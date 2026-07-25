# WeatherInsurance (Paper plugin)

Pay a premium, insure the chunk you're standing in, and get automatically compensated when disaster strikes your build.

- `/insure` — insure the current chunk: 10 emeralds for 7 in-game days, up to 40 emeralds of coverage. Max 3 policies per player.
- Covered perils: explosions (creepers/TNT — 1 emerald per destroyed block, capped 16 per blast), fire spread (1 per burned block), lightning strikes (3 flat).
- Claims pay instantly to your inventory if online, or accumulate and pay on your next join.
- `/insure status` — your policies, days and coverage left. `/insure cancel` — cancel the policy where you stand.

Install: drop the jar in `plugins/`, restart. Paper/Spigot/Purpur 1.21.4+, Java 21.
