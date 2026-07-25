// Bounty Board UI (ActionForm menus).
import { world } from "@minecraft/server";
import { ActionFormData, MessageFormData } from "@minecraft/server-ui";
import { getDailyBounties, describeBounty, TIERS, streakMultiplier } from "./bounties.js";
import { refreshData, saveData, claimReward, countInInventory, getSalt } from "./progress.js";

export function openBoard(player) {
  const today = world.getDay();
  const data = refreshData(player, today);
  const board = getDailyBounties(today, getSalt());

  const form = new ActionFormData().title(`§l§6Bounty Board§r — Day ${today}`);

  let body = "";
  if (data.streak > 1) {
    body += `§6Streak: ${data.streak} days §7(+${Math.round((streakMultiplier(data.streak) - 1) * 100)}% emeralds)§r\n`;
  }
  body += `§7Bounties completed: ${data.completed}§r\n\n`;

  const active = data.active;
  if (active) {
    const shownCount =
      active.type === "deliver" ? Math.min(countInInventory(player, active.target), active.goal) : active.count;
    const done = shownCount >= active.goal;
    body += `§eActive:§r ${describeBounty(active)} — §${done ? "a" : "e"}${shownCount}/${active.goal}§r\n`;
    body += done ? "§aReady to claim!§r\n" : "§7The board resets each dawn — finish it today.§r\n";
  } else {
    body += "§7Pick one bounty. It expires at the next sunrise.§r\n";
  }
  form.body(body);

  const buttons = [];
  if (active) {
    const shownCount =
      active.type === "deliver" ? Math.min(countInInventory(player, active.target), active.goal) : active.count;
    if (shownCount >= active.goal) {
      form.button("§a§lClaim Reward", "textures/items/emerald");
      buttons.push({ kind: "claim" });
    }
    form.button("§cAbandon Bounty", "textures/ui/cancel");
    buttons.push({ kind: "abandon" });
  } else {
    for (const b of board) {
      const tier = TIERS[b.tier];
      form.button(`${tier.label}§r\n${describeBounty(b)}`, b.icon);
      buttons.push({ kind: "accept", bounty: b });
    }
  }

  form.show(player).then((res) => {
    if (res.canceled || res.selection === undefined) return;
    const choice = buttons[res.selection];
    if (!choice) return;

    if (choice.kind === "accept") {
      const b = choice.bounty;
      const tier = TIERS[b.tier];
      data.active = { ...b, count: 0 };
      saveData(player, data);
      player.sendMessage(
        `§6[Bounty Board]§r Accepted: ${describeBounty(b)} §7(${tier.label}§r§7 — ${tier.emeralds} emeralds${
          tier.diamonds ? `, ${tier.diamonds} diamonds` : ""
        }, ${tier.xpLevels} XP levels)§r`
      );
      try {
        player.playSound("random.pop");
      } catch {}
    } else if (choice.kind === "claim") {
      const msg = claimReward(player, today);
      if (msg) player.sendMessage(`§6[Bounty Board]§r ${msg}`);
    } else if (choice.kind === "abandon") {
      new MessageFormData()
        .title("Abandon bounty?")
        .body("Your progress on this bounty will be lost.")
        .button2("§cAbandon")
        .button1("Keep it")
        .show(player)
        .then((r) => {
          if (r.canceled || r.selection !== 1) return;
          const d = refreshData(player, world.getDay());
          d.active = null;
          saveData(player, d);
          player.sendMessage("§6[Bounty Board]§r §7Bounty abandoned.");
        });
    }
  });
}
