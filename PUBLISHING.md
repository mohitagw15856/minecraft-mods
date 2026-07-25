# Publishing guide — Bounty Board

Step-by-step for offering both versions to the public. Order matters: GitHub first (it's the canonical home every listing links back to), then the free marketplaces, then paid channels.

## 1. GitHub repo (canonical home)

```sh
cd /Users/mo/Desktop/projects/minecraft-mods
gh auth login                      # once, if not already
gh repo create bounty-board --public --source=. --push
```

Then on github.com:
1. Add topics: `minecraft`, `minecraft-plugin`, `paper-plugin`, `bedrock-addon`, `minecraft-bedrock`.
2. Create a release: `gh release create v1.0.0 BountyBoard.mcpack bounty-board-paper/build/libs/bounty-board-1.0.0.jar --title "Bounty Board 1.0.0" --notes "Initial release"` — the release assets are what listings link to.
3. Enable Issues (bug reports are your feedback channel and look good to Marketplace studios reviewing your portfolio).

## 2. Modrinth (Java plugin — best modern channel)

1. Create account at modrinth.com → **Create a project** → type **Plugin**.
2. Fill: name "Bounty Board", summary (1 line), description (paste the README feature list + 2–4 screenshots of the GUI — take them on a local Paper server).
3. Upload `bounty-board-1.0.0.jar` as version `1.0.0`, loaders **Paper, Purpur, Spigot**, game versions **1.21.4+** (tick each you've tested), channel **Release**.
4. Set license MIT, link the GitHub repo as Source, Issues link too.
5. Gallery: upload GUI screenshots — projects with images get dramatically more downloads.
6. Submit for review (usually approved in ~24–48h).
7. Enable **Creator monetization** in settings (Modrinth pays ad revenue share per download — small but real money, paid via PayPal at $0.01+ thresholds).

## 3. Hangar (hangar.papermc.io — Paper's official plugin repo)

Same drill as Modrinth: create project → paste description → upload jar → tag platform Paper 1.21. Server admins who run Paper browse here first. Free, quick review.

## 4. SpigotMC (spigotmc.org — largest legacy audience)

1. Register, then **Resources → Add Resource**.
2. Category "Spigot Plugins" → subcategory Mechanics/Fun; native version 1.21.
3. Paste description (BBCode — convert the markdown), upload jar, link GitHub.
4. SpigotMC also supports **premium resources** (paid plugins, min $1, they take no cut but require an established account history first — sell later versions here once you have reviews).

## 5. MCPEDL (Bedrock — biggest Bedrock audience)

1. Register at mcpedl.com → **Submit content** → Add-on.
2. Title, description with screenshots/GIF of the board UI in-game, feature list.
3. Upload `BountyBoard.mcpack` **or** (preferred by MCPEDL) link it via a GitHub release URL — no ad-link shorteners needed.
4. Manual review, typically a few days. MCPEDL supports partner monetization once you have traction.
5. Also list on **CurseForge → Minecraft Bedrock → Addons** — second-biggest Bedrock channel, and CurseForge pays creator points.

## 6. Paid: Minecraft Marketplace (Bedrock)

You can't publish directly without being a partner. The realistic 2026 path (matches your earlier research):
1. Ship Bounty Board free on MCPEDL/CurseForge → it becomes portfolio piece #1.
2. Build 2–3 more polished pieces (see ideas list).
3. Apply to partner studios as a Script API dev (Cyclone, Team Visionary, Gamemode One, Blockception) with the GitHub + MCPEDL links, or publish through an existing partner (~50/50 net split).

## 7. Ongoing

- Version bumps: update `version` in `manifest.json` / `build.gradle`, tag a GitHub release, upload the new file to each listing (Modrinth/Hangar have APIs — automatable later).
- Answer the first week of comments quickly on every platform; early ratings decide ranking.
- Post a short clip (30–60s screen recording of accepting → completing → claiming a bounty) — MCPEDL and Reddit r/MinecraftMod / r/admincraft posts drive the initial downloads.
