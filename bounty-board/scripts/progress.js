// Per-player persistence + live progress tracking (kills / mining / deliveries).
import { world, system, ItemStack } from "@minecraft/server";
import { TIERS, streakMultiplier, describeBounty } from "./bounties.js";

const PROP = "bb:data";
const SALT_PROP = "bb:salt";

export function getSalt() {
  let salt = world.getDynamicProperty(SALT_PROP);
  if (typeof salt !== "number") {
    salt = Math.floor(Math.random() * 2147483647);
    world.setDynamicProperty(SALT_PROP, salt);
  }
  return salt;
}

export function getData(player) {
  const raw = player.getDynamicProperty(PROP);
  if (typeof raw === "string") {
    try {
      return JSON.parse(raw);
    } catch {}
  }
  return { active: null, streak: 0, lastDay: -10, completed: 0 };
}

export function saveData(player, data) {
  player.setDynamicProperty(PROP, JSON.stringify(data));
}

/** Expire an active bounty if its day has passed. Returns fresh data. */
export function refreshData(player, today) {
  const data = getData(player);
  if (data.active && data.active.day !== today) {
    data.active = null;
    saveData(player, data);
    player.sendMessage("§6[Bounty Board]§r §7Your bounty expired with the sunrise. A new board awaits.");
  }
  return data;
}

function bump(player, matchType, targetId) {
  const data = getData(player);
  const b = data.active;
  if (!b || b.type !== matchType || b.target !== targetId) return;
  if (b.count >= b.goal) return;
  b.count++;
  saveData(player, data);
  if (b.count >= b.goal) {
    player.sendMessage(`§6[Bounty Board]§r §aBounty complete: ${describeBounty(b)}! Open the board (use a Book) to claim your reward.`);
    try {
      player.playSound("random.levelup");
    } catch {}
  } else {
    try {
      player.onScreenDisplay.setActionBar(`§6Bounty:§r ${describeBounty(b)} §e${b.count}/${b.goal}`);
    } catch {}
  }
}

export function countInInventory(player, itemId) {
  const inv = player.getComponent("minecraft:inventory");
  const container = inv && inv.container;
  if (!container) return 0;
  let total = 0;
  for (let i = 0; i < container.size; i++) {
    const item = container.getItem(i);
    if (item && item.typeId === itemId) total += item.amount;
  }
  return total;
}

function removeFromInventory(player, itemId, amount) {
  const inv = player.getComponent("minecraft:inventory");
  const container = inv && inv.container;
  if (!container) return;
  let remaining = amount;
  for (let i = 0; i < container.size && remaining > 0; i++) {
    const item = container.getItem(i);
    if (!item || item.typeId !== itemId) continue;
    if (item.amount <= remaining) {
      remaining -= item.amount;
      container.setItem(i, undefined);
    } else {
      item.amount -= remaining;
      remaining = 0;
      container.setItem(i, item);
    }
  }
}

function give(player, itemId, amount) {
  const inv = player.getComponent("minecraft:inventory");
  const container = inv && inv.container;
  if (!container) return;
  let left = amount;
  while (left > 0) {
    const n = Math.min(left, 64);
    const leftover = container.addItem(new ItemStack(itemId, n));
    left -= n;
    if (leftover) {
      // Inventory full — drop at the player's feet instead of losing it.
      player.dimension.spawnItem(leftover, player.location);
    }
  }
}

/** Pays out the active bounty. Returns a message string, or null if not claimable. */
export function claimReward(player, today) {
  const data = refreshData(player, today);
  const b = data.active;
  if (!b) return null;
  if (b.type === "deliver") {
    b.count = Math.min(countInInventory(player, b.target), b.goal);
  }
  if (b.count < b.goal) return null;
  if (b.type === "deliver") removeFromInventory(player, b.target, b.goal);

  // Streak: consecutive days completed.
  data.streak = data.lastDay === today - 1 ? data.streak + 1 : 1;
  data.lastDay = today;
  data.completed++;

  const tier = TIERS[b.tier];
  const mult = streakMultiplier(data.streak);
  const emeralds = Math.round(tier.emeralds * mult);
  give(player, "minecraft:emerald", emeralds);
  if (tier.diamonds > 0) give(player, "minecraft:diamond", tier.diamonds);
  try {
    player.addLevels(tier.xpLevels);
  } catch {}
  try {
    player.playSound("random.levelup");
  } catch {}

  data.active = null;
  saveData(player, data);

  let msg = `§aClaimed! §r+${emeralds} emeralds`;
  if (tier.diamonds > 0) msg += `, +${tier.diamonds} diamonds`;
  msg += `, +${tier.xpLevels} XP levels`;
  if (data.streak > 1) msg += ` §6(streak x${data.streak} — ${Math.round((mult - 1) * 100)}% bonus)`;
  return msg;
}

export function initTracking() {
  world.afterEvents.entityDie.subscribe((ev) => {
    const killer = ev.damageSource && ev.damageSource.damagingEntity;
    if (!killer || killer.typeId !== "minecraft:player") return;
    bump(killer, "hunt", ev.deadEntity.typeId);
  });

  world.afterEvents.playerBreakBlock.subscribe((ev) => {
    bump(ev.player, "mine", ev.brokenBlockPermutation.type.id);
  });

  // Deliver bounties: periodically nudge progress display from inventory counts.
  system.runInterval(() => {
    for (const player of world.getPlayers()) {
      const data = getData(player);
      const b = data.active;
      if (!b || b.type !== "deliver") continue;
      const have = Math.min(countInInventory(player, b.target), b.goal);
      if (have !== b.count) {
        b.count = have;
        saveData(player, data);
        if (have >= b.goal) {
          player.sendMessage(`§6[Bounty Board]§r §aYou have everything for: ${describeBounty(b)}. Open the board (use a Book) to turn it in!`);
          try {
            player.playSound("random.orb");
          } catch {}
        }
      }
    }
  }, 100); // every 5 seconds
}
