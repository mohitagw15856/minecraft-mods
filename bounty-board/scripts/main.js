// Bounty Board — daily rotating quests for Minecraft Bedrock.
// Open the board by using (right-click / long-press) a Book.
import { world, system, ItemStack } from "@minecraft/server";
import { openBoard } from "./ui.js";
import { initTracking } from "./progress.js";

initTracking();

world.afterEvents.itemUse.subscribe((ev) => {
  if (ev.itemStack.typeId !== "minecraft:book") return;
  const player = ev.source;
  if (!player || player.typeId !== "minecraft:player") return;
  // Defer one tick so the form opens reliably after the use gesture.
  system.run(() => openBoard(player));
});

// First-join welcome: hand new players a Book and explain the board.
world.afterEvents.playerSpawn.subscribe((ev) => {
  if (!ev.initialSpawn) return;
  const player = ev.player;
  if (player.getDynamicProperty("bb:welcomed")) return;
  player.setDynamicProperty("bb:welcomed", true);
  system.runTimeout(() => {
    try {
      const inv = player.getComponent("minecraft:inventory");
      if (inv && inv.container) inv.container.addItem(new ItemStack("minecraft:book", 1));
      player.sendMessage("§6[Bounty Board]§r Welcome! §eUse the Book§r to open the Bounty Board. Three new bounties every day — build a streak for bonus emeralds.");
    } catch {}
  }, 40);
});
