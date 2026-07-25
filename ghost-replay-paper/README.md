# GhostReplay (Paper plugin)

After you die, a translucent ghost wearing your head retraces your final 30 seconds, ending at your death point — follow it to recover your items.

- Automatic: on respawn, your ghost spawns at the start of your recorded path and walks it in real time, ending with a soul-fire burst at the exact death spot.
- `/ghost` — toggle replays for yourself (on by default).
- `/ghost last` — re-run your most recent replay (once per death).
- Ghosts never persist across restarts and are cleaned from loading chunks.

Install: drop the jar in `plugins/`, restart. Paper/Spigot/Purpur 1.21.4+, Java 21.
