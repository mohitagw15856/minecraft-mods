# CartographersContracts (Paper plugin)

Pay emeralds for an expedition contract to a distant structure — reach it and the reward pays out with a server-wide broadcast.

- `/contract list` — six contract types (village, shipwreck, pillager outpost, ocean ruin, desert pyramid, woodland mansion). Prices escalate each repeat purchase, up to 4x.
- `/contract buy <type>` — charges emeralds from your inventory, locates the nearest structure (up to 3000 blocks), and hands you a signed contract paper with distance and direction.
- `/contract status` / `/contract cancel`.
- Arriving within 48 blocks auto-completes: emeralds, particles, broadcast.
- Note: `buy` runs a synchronous structure search which can briefly pause the server on slow disks — buy contracts while the area is generated or accept a short hitch.

Install: drop the jar in `plugins/`, restart. Paper/Spigot/Purpur 1.21.4+, Java 21.
