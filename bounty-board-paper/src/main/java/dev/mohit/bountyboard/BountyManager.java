package dev.mohit.bountyboard;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/** Daily board generation, per-player data, progress tracking and payouts. */
public class BountyManager {

    public static class PlayerData {
        public Bounty active;
        public int streak;
        public long lastDay = -10;
        public int completed;
        public boolean welcomed;
    }

    private record PoolEntry(String target, String name, int[] goals) {}

    private static final List<PoolEntry> HUNT = List.of(
        new PoolEntry("ZOMBIE", "Zombies", new int[]{8, 14, 22}),
        new PoolEntry("SKELETON", "Skeletons", new int[]{6, 12, 20}),
        new PoolEntry("CREEPER", "Creepers", new int[]{4, 8, 14}),
        new PoolEntry("SPIDER", "Spiders", new int[]{6, 12, 18}),
        new PoolEntry("DROWNED", "Drowned", new int[]{4, 8, 12}),
        new PoolEntry("ENDERMAN", "Endermen", new int[]{2, 4, 8}),
        new PoolEntry("WITCH", "Witches", new int[]{1, 2, 4}),
        new PoolEntry("PILLAGER", "Pillagers", new int[]{3, 6, 10})
    );

    private static final List<PoolEntry> MINE = List.of(
        new PoolEntry("COAL_ORE", "Coal Ore", new int[]{16, 28, 48}),
        new PoolEntry("IRON_ORE", "Iron Ore", new int[]{10, 20, 32}),
        new PoolEntry("COPPER_ORE", "Copper Ore", new int[]{12, 24, 40}),
        new PoolEntry("GOLD_ORE", "Gold Ore", new int[]{4, 8, 14}),
        new PoolEntry("OBSIDIAN", "Obsidian", new int[]{4, 8, 14}),
        new PoolEntry("STONE", "Stone", new int[]{64, 128, 192})
    );

    private static final List<PoolEntry> DELIVER = List.of(
        new PoolEntry("WHEAT", "Wheat", new int[]{24, 48, 64}),
        new PoolEntry("COD", "Raw Cod", new int[]{8, 16, 24}),
        new PoolEntry("LEATHER", "Leather", new int[]{6, 12, 20}),
        new PoolEntry("BONE", "Bones", new int[]{12, 24, 40}),
        new PoolEntry("STRING", "String", new int[]{10, 20, 32}),
        new PoolEntry("PUMPKIN", "Pumpkins", new int[]{6, 12, 20}),
        new PoolEntry("IRON_INGOT", "Iron Ingots", new int[]{8, 16, 24})
    );

    public static final Component PREFIX =
        Component.text("[Bounty Board] ", NamedTextColor.GOLD);

    private final BountyBoardPlugin plugin;
    private final Map<UUID, PlayerData> players = new HashMap<>();
    private final long salt;

    public BountyManager(BountyBoardPlugin plugin) {
        this.plugin = plugin;
        plugin.getConfig().addDefault("salt", new Random().nextInt(Integer.MAX_VALUE));
        plugin.getConfig().options().copyDefaults(true);
        plugin.saveConfig();
        this.salt = plugin.getConfig().getLong("salt");
        load();
    }

    public long currentDay() {
        return Bukkit.getWorlds().get(0).getFullTime() / 24000L;
    }

    /** Same 3 bounties (one per tier) for everyone on a given day. */
    public List<Bounty> dailyBounties(long day) {
        Random rand = new Random(day * 2654435761L + salt);
        Bounty.Tier[] tiers = Bounty.Tier.values();
        List<Bounty> out = new ArrayList<>(3);
        for (int i = 0; i < 3; i++) {
            int kind = rand.nextInt(3);
            List<PoolEntry> pool = kind == 0 ? HUNT : kind == 1 ? MINE : DELIVER;
            Bounty.Type type = kind == 0 ? Bounty.Type.HUNT : kind == 1 ? Bounty.Type.MINE : Bounty.Type.DELIVER;
            PoolEntry e = pool.get(rand.nextInt(pool.size()));
            out.add(new Bounty(day, tiers[i], type, e.target(), e.name(), e.goals()[i], 0));
        }
        return out;
    }

    public PlayerData data(Player player) {
        return players.computeIfAbsent(player.getUniqueId(), id -> new PlayerData());
    }

    /** Drops the active bounty if its day has passed. */
    public PlayerData refresh(Player player) {
        PlayerData d = data(player);
        if (d.active != null && d.active.day != currentDay()) {
            d.active = null;
            player.sendMessage(PREFIX.append(Component.text(
                "Your bounty expired with the sunrise. A new board awaits.", NamedTextColor.GRAY)));
        }
        return d;
    }

    public static double streakMultiplier(int streak) {
        return 1 + 0.25 * Math.min(Math.max(streak - 1, 0), 4);
    }

    public void bump(Player player, Bounty.Type type, String target) {
        PlayerData d = data(player);
        Bounty b = d.active;
        if (b == null || b.type != type || !b.target.equals(target)) return;
        if (b.day != currentDay()) { refresh(player); return; }
        if (b.count >= b.goal) return;
        b.count++;
        if (b.count >= b.goal) {
            player.sendMessage(PREFIX.append(Component.text(
                "Bounty complete: " + b.describe() + "! Run /bounty to claim your reward.", NamedTextColor.GREEN)));
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
        } else {
            player.sendActionBar(Component.text("Bounty: ", NamedTextColor.GOLD)
                .append(Component.text(b.describe() + " ", NamedTextColor.WHITE))
                .append(Component.text(b.count + "/" + b.goal, NamedTextColor.YELLOW)));
        }
    }

    public int countInInventory(Player player, Material mat) {
        int total = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == mat) total += item.getAmount();
        }
        return total;
    }

    /** Current progress, reading live inventory for DELIVER bounties. */
    public int shownCount(Player player, Bounty b) {
        if (b.type == Bounty.Type.DELIVER) {
            Material mat = Material.matchMaterial(b.target);
            if (mat == null) return b.count;
            return Math.min(countInInventory(player, mat), b.goal);
        }
        return b.count;
    }

    /** Pays out the active bounty. Returns true if claimed. */
    public boolean claim(Player player) {
        PlayerData d = refresh(player);
        Bounty b = d.active;
        if (b == null || shownCount(player, b) < b.goal) return false;

        if (b.type == Bounty.Type.DELIVER) {
            Material mat = Material.matchMaterial(b.target);
            if (mat != null) player.getInventory().removeItem(new ItemStack(mat, b.goal));
        }

        long today = currentDay();
        d.streak = (d.lastDay == today - 1) ? d.streak + 1 : 1;
        d.lastDay = today;
        d.completed++;

        double mult = streakMultiplier(d.streak);
        int emeralds = (int) Math.round(b.tier.emeralds * mult);
        give(player, new ItemStack(Material.EMERALD, emeralds));
        if (b.tier.diamonds > 0) give(player, new ItemStack(Material.DIAMOND, b.tier.diamonds));
        player.giveExpLevels(b.tier.xpLevels);
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);

        StringBuilder msg = new StringBuilder("Claimed! +" + emeralds + " emeralds");
        if (b.tier.diamonds > 0) msg.append(", +").append(b.tier.diamonds).append(" diamonds");
        msg.append(", +").append(b.tier.xpLevels).append(" XP levels");
        if (d.streak > 1) {
            msg.append(" (streak x").append(d.streak)
               .append(" — ").append(Math.round((mult - 1) * 100)).append("% bonus)");
        }
        player.sendMessage(PREFIX.append(Component.text(msg.toString(), NamedTextColor.GREEN)));

        d.active = null;
        save();
        return true;
    }

    private void give(Player player, ItemStack stack) {
        player.getInventory().addItem(stack).values()
            .forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
    }

    // ---- persistence ----

    private File dataFile() {
        return new File(plugin.getDataFolder(), "playerdata.yml");
    }

    public void load() {
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(dataFile());
        for (String key : yml.getKeys(false)) {
            ConfigurationSection s = yml.getConfigurationSection(key);
            if (s == null) continue;
            PlayerData d = new PlayerData();
            d.streak = s.getInt("streak");
            d.lastDay = s.getLong("lastDay", -10);
            d.completed = s.getInt("completed");
            d.welcomed = s.getBoolean("welcomed");
            ConfigurationSection a = s.getConfigurationSection("active");
            if (a != null) d.active = Bounty.deserialize(a.getValues(false));
            try {
                players.put(UUID.fromString(key), d);
            } catch (IllegalArgumentException ignored) {}
        }
    }

    public void save() {
        YamlConfiguration yml = new YamlConfiguration();
        for (Map.Entry<UUID, PlayerData> e : players.entrySet()) {
            PlayerData d = e.getValue();
            String key = e.getKey().toString();
            yml.set(key + ".streak", d.streak);
            yml.set(key + ".lastDay", d.lastDay);
            yml.set(key + ".completed", d.completed);
            yml.set(key + ".welcomed", d.welcomed);
            if (d.active != null) yml.set(key + ".active", d.active.serialize());
        }
        try {
            yml.save(dataFile());
        } catch (IOException ex) {
            plugin.getLogger().warning("Could not save playerdata.yml: " + ex.getMessage());
        }
    }
}
