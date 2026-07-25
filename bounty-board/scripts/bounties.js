// Bounty definitions and deterministic daily generation.
// Every player sees the same 3 bounties on a given world day.

export const TIERS = {
  common: { label: "§fCommon", emeralds: 4, diamonds: 0, xpLevels: 2 },
  rare: { label: "§bRare", emeralds: 10, diamonds: 1, xpLevels: 5 },
  epic: { label: "§dEpic", emeralds: 16, diamonds: 3, xpLevels: 10 }
};

const HUNT_POOL = [
  { target: "minecraft:zombie", name: "Zombies", goals: [8, 14, 22] },
  { target: "minecraft:skeleton", name: "Skeletons", goals: [6, 12, 20] },
  { target: "minecraft:creeper", name: "Creepers", goals: [4, 8, 14] },
  { target: "minecraft:spider", name: "Spiders", goals: [6, 12, 18] },
  { target: "minecraft:drowned", name: "Drowned", goals: [4, 8, 12] },
  { target: "minecraft:enderman", name: "Endermen", goals: [2, 4, 8] },
  { target: "minecraft:witch", name: "Witches", goals: [1, 2, 4] },
  { target: "minecraft:pillager", name: "Pillagers", goals: [3, 6, 10] }
];

const MINE_POOL = [
  { target: "minecraft:coal_ore", name: "Coal Ore", goals: [16, 28, 48] },
  { target: "minecraft:iron_ore", name: "Iron Ore", goals: [10, 20, 32] },
  { target: "minecraft:copper_ore", name: "Copper Ore", goals: [12, 24, 40] },
  { target: "minecraft:gold_ore", name: "Gold Ore", goals: [4, 8, 14] },
  { target: "minecraft:obsidian", name: "Obsidian", goals: [4, 8, 14] },
  { target: "minecraft:stone", name: "Stone", goals: [64, 128, 192] }
];

const DELIVER_POOL = [
  { target: "minecraft:wheat", name: "Wheat", goals: [24, 48, 64] },
  { target: "minecraft:cod", name: "Raw Cod", goals: [8, 16, 24] },
  { target: "minecraft:leather", name: "Leather", goals: [6, 12, 20] },
  { target: "minecraft:bone", name: "Bones", goals: [12, 24, 40] },
  { target: "minecraft:string", name: "String", goals: [10, 20, 32] },
  { target: "minecraft:pumpkin", name: "Pumpkins", goals: [6, 12, 20] },
  { target: "minecraft:iron_ingot", name: "Iron Ingots", goals: [8, 16, 24] }
];

const TYPES = [
  { type: "hunt", pool: HUNT_POOL, verb: "Slay", icon: "textures/items/iron_sword" },
  { type: "mine", pool: MINE_POOL, verb: "Mine", icon: "textures/items/iron_pickaxe" },
  { type: "deliver", pool: DELIVER_POOL, verb: "Deliver", icon: "textures/items/paper" }
];

// Small deterministic PRNG so the board is identical for everyone on the same day.
function mulberry32(seed) {
  let a = seed >>> 0;
  return function () {
    a |= 0;
    a = (a + 0x6d2b79f5) | 0;
    let t = Math.imul(a ^ (a >>> 15), 1 | a);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

const TIER_ORDER = ["common", "rare", "epic"];

/** Returns the 3 bounties for a given world day (one per tier). */
export function getDailyBounties(day, salt) {
  const rand = mulberry32(day * 2654435761 + salt);
  const bounties = [];
  for (let i = 0; i < 3; i++) {
    const tier = TIER_ORDER[i];
    const kind = TYPES[Math.floor(rand() * TYPES.length)];
    const entry = kind.pool[Math.floor(rand() * kind.pool.length)];
    bounties.push({
      id: `${day}:${i}`,
      day,
      tier,
      type: kind.type,
      verb: kind.verb,
      icon: kind.icon,
      target: entry.target,
      name: entry.name,
      goal: entry.goals[i]
    });
  }
  return bounties;
}

export function describeBounty(b) {
  return `${b.verb} ${b.goal} ${b.name}`;
}

/** Streak multiplier: +25% emeralds per consecutive day, capped at +100%. */
export function streakMultiplier(streak) {
  return 1 + 0.25 * Math.min(Math.max(streak - 1, 0), 4);
}
