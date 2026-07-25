# HeirloomItems (Paper plugin)

Designate one precious item as your heirloom. When you die, it skips the item drop and passes to your chosen heir — gaining a generation number and a visible lineage.

- `/heirloom set` — tag the item in your main hand (one heirloom per player; setting a new one releases the old).
- `/heirloom heir <player>` — choose who inherits it (anyone who has ever joined).
- `/heirloom status` — your heirloom + heir.
- On death: the heirloom leaves your drops, gains "Inherited from you, Generation N" lineage lore, and is handed to your heir — instantly if online, or from the vault on their next join. No heir set? It drops normally and you're warned.
- Ownership transfers on inheritance: the item becomes the heir's heirloom.

Install: drop the jar in `plugins/`, restart. Paper/Spigot/Purpur 1.21.4+, Java 21.
