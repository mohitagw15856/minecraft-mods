# EchoChambers (Paper plugin)

Bind a 10-second chat recording to a lodestone. Anyone who later walks within 5 blocks hears it whispered back — build haunted houses, museum plaques, base greetings, in-world tutorials.

- Place a lodestone, look at it, `/echo record` — everything said within 16 blocks over the next 10 seconds (up to 10 lines) is bound to it.
- Passers-by trigger the replay (chime sounds + enchant particles, lines whispered in gray italics, once per minute per player).
- `/echo clear` — release the echo you're looking at (owner or op). `/echo list` — your echoes (max 5).
- Breaking the lodestone releases its echo.

Install: drop the jar in `plugins/`, restart. Paper/Purpur 1.21.4+ (uses Paper chat events), Java 21.
