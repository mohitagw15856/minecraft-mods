package dev.mohit.seasonpass;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class SeasonPassPlugin extends JavaPlugin implements Listener {

    private static final Component PREFIX = Component.text("[SeasonPass] ", NamedTextColor.LIGHT_PURPLE);
    private static final int MINING_XP_DAILY_CAP = 200;

    private final Map<UUID, PassData> players = new HashMap<>();

    @Override
    public void onEnable() {
        loadData();
        getServer().getPluginManager().registerEvents(this, this);

        // Playtime XP: +2 per minute online
        getServer().getScheduler().runTaskTimer(this, () -> {
            for (Player player : getServer().getOnlinePlayers()) addXp(player, 2);
        }, 1200L, 1200L);

        // Particle trails
        getServer().getScheduler().runTaskTimer(this, () -> {
            for (Player player : getServer().getOnlinePlayers()) {
                Particle particle = Rewards.particle(data(player).trail);
                if (particle != null && player.getVelocity().lengthSquared() > 0.01) {
                    player.getWorld().spawnParticle(particle, player.getLocation().add(0, 0.2, 0),
                        2, 0.15, 0.05, 0.15, 0.0);
                }
            }
        }, 10L, 10L);
    }

    @Override
    public void onDisable() {
        saveData();
    }

    public PassData data(Player player) {
        return players.computeIfAbsent(player.getUniqueId(), id -> new PassData());
    }

    private long currentDay() {
        return Bukkit.getWorlds().get(0).getFullTime() / 24000L;
    }

    public void addXp(Player player, int amount) {
        PassData d = data(player);
        int before = d.tier();
        d.xp += amount;
        int after = d.tier();
        if (after > before) {
            Rewards.Reward reward = Rewards.TRACK[after - 1];
            player.sendMessage(PREFIX.append(Component.text(
                "Tier " + after + " reached! Unlocked: " + reward.label() + " — open /pass to equip.",
                NamedTextColor.GREEN)));
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.3f);
        }
    }

    public void applyNameCosmetics(Player player) {
        PassData d = data(player);
        NamedTextColor color = Rewards.color(d.color);
        Component name = Component.text(player.getName(), color == null ? NamedTextColor.WHITE : color);
        if (d.title != null) {
            name = Component.text("[" + d.title + "] ", NamedTextColor.GOLD).append(name);
        }
        player.displayName(name);
        player.playerListName(name);
    }

    @EventHandler
    public void onKill(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer != null && event.getEntity() instanceof Monster) addXp(killer, 5);
    }

    @EventHandler
    public void onMine(BlockBreakEvent event) {
        PassData d = data(event.getPlayer());
        long today = currentDay();
        if (d.lastDay != today) {
            d.lastDay = today;
            d.miningXpToday = 0;
        }
        if (d.miningXpToday < MINING_XP_DAILY_CAP) {
            d.miningXpToday++;
            addXp(event.getPlayer(), 1);
        }
    }

    @EventHandler
    public void onFish(PlayerFishEvent event) {
        if (event.getState() == PlayerFishEvent.State.CAUGHT_FISH) addXp(event.getPlayer(), 10);
    }

    @EventHandler
    public void onAdvancement(PlayerAdvancementDoneEvent event) {
        if (event.getAdvancement().getKey().getKey().startsWith("recipes/")) return;
        addXp(event.getPlayer(), 25);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        applyNameCosmetics(event.getPlayer());
    }

    @EventHandler
    public void onChat(AsyncChatEvent event) {
        PassData d = data(event.getPlayer());
        if (d.title == null && Rewards.color(d.color) == null) return;
        event.renderer((source, displayName, message, viewer) -> {
            NamedTextColor color = Rewards.color(d.color);
            Component name = Component.text(source.getName(), color == null ? NamedTextColor.WHITE : color);
            Component prefix = d.title == null ? Component.empty()
                : Component.text("[" + d.title + "] ", NamedTextColor.GOLD);
            return prefix.append(name)
                .append(Component.text(": ", NamedTextColor.WHITE))
                .append(message);
        });
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof PassGui gui)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getRawSlot() < 0 || event.getRawSlot() >= 54) return;
        gui.onClick(player, event.getRawSlot());
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        new PassGui(this, player).open(player);
        return true;
    }

    // ---- persistence ----

    private File dataFile() {
        return new File(getDataFolder(), "data.yml");
    }

    private void loadData() {
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(dataFile());
        for (String key : yml.getKeys(false)) {
            ConfigurationSection s = yml.getConfigurationSection(key);
            if (s == null) continue;
            try {
                PassData d = new PassData();
                d.xp = s.getInt("xp");
                d.color = s.getString("color");
                d.trail = s.getString("trail");
                d.title = s.getString("title");
                d.miningXpToday = s.getInt("miningXpToday");
                d.lastDay = s.getLong("lastDay", -1);
                players.put(UUID.fromString(key), d);
            } catch (IllegalArgumentException ignored) {}
        }
    }

    public void saveData() {
        YamlConfiguration yml = new YamlConfiguration();
        for (Map.Entry<UUID, PassData> e : players.entrySet()) {
            String base = e.getKey().toString();
            PassData d = e.getValue();
            yml.set(base + ".xp", d.xp);
            yml.set(base + ".color", d.color);
            yml.set(base + ".trail", d.trail);
            yml.set(base + ".title", d.title);
            yml.set(base + ".miningXpToday", d.miningXpToday);
            yml.set(base + ".lastDay", d.lastDay);
        }
        try {
            if (!getDataFolder().exists()) getDataFolder().mkdirs();
            yml.save(dataFile());
        } catch (IOException ex) {
            getLogger().warning("Could not save data.yml: " + ex.getMessage());
        }
    }
}
