package dev.mohit.swornrivals;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
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
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class SwornRivalsPlugin extends JavaPlugin implements Listener {

    private static final Component PREFIX = Component.text("[Rivals] ", NamedTextColor.RED);

    private final Map<UUID, RivalData> players = new HashMap<>();
    private long currentWeek = -1;

    @Override
    public void onEnable() {
        load();
        getServer().getPluginManager().registerEvents(this, this);
        if (currentWeek < 0) currentWeek = week();
        getServer().getScheduler().runTaskTimer(this, this::tick, 100L, 100L);
    }

    @Override
    public void onDisable() {
        save();
    }

    private long week() {
        return Bukkit.getWorlds().get(0).getFullTime() / 24000L / 7L;
    }

    private RivalData data(UUID id) {
        return players.computeIfAbsent(id, k -> new RivalData());
    }

    private void tick() {
        long now = week();
        if (now != currentWeek) {
            settleWeek();
            currentWeek = now;
            shufflePairs(true);
            save();
        }
        // Refresh the winners' buff
        for (Player player : getServer().getOnlinePlayers()) {
            if (data(player.getUniqueId()).hasEdge) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 160, 0, true, false, true));
            }
        }
    }

    private void addPoints(Player player, int points) {
        RivalData d = data(player.getUniqueId());
        d.name = player.getName();
        d.seenThisWeek = true;
        if (d.hasEdge) points = (int) Math.ceil(points * 1.10);
        d.weekScore += points;
    }

    @EventHandler
    public void onKill(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null) return;
        if (event.getEntity() instanceof Player) return; // handled below
        if (event.getEntity() instanceof Monster) addPoints(killer, 1);
    }

    @EventHandler
    public void onPlayerKill(PlayerDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer != null && !killer.equals(event.getEntity())) addPoints(killer, 5);
    }

    @EventHandler
    public void onMine(BlockBreakEvent event) {
        Material type = event.getBlock().getType();
        if (type.name().endsWith("_ORE") || type == Material.ANCIENT_DEBRIS) {
            addPoints(event.getPlayer(), 2);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        RivalData d = data(player.getUniqueId());
        d.name = player.getName();
        d.seenThisWeek = true;
        if (d.rival != null) {
            RivalData rd = players.get(d.rival);
            if (rd != null) {
                player.sendMessage(PREFIX.append(Component.text(
                    "Your sworn rival this week: " + rd.name + " (" + rd.weekScore + " pts vs your "
                        + d.weekScore + "). Outscore them.", NamedTextColor.YELLOW)));
            }
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("shuffle")) {
            if (!sender.isOp()) {
                sender.sendMessage(PREFIX.append(Component.text("Op only.", NamedTextColor.RED)));
                return true;
            }
            shufflePairs(false);
            sender.sendMessage(PREFIX.append(Component.text("Rival pairs reshuffled.", NamedTextColor.GREEN)));
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        RivalData d = data(player.getUniqueId());
        if (d.rival == null) {
            player.sendMessage(PREFIX.append(Component.text(
                "No rival assigned this week" + (d.hasEdge ? " — you carry the Rival's Edge." : ".")
                    + " Score so far: " + d.weekScore, NamedTextColor.GRAY)));
            return true;
        }
        RivalData rd = players.get(d.rival);
        String rivalName = rd == null ? "?" : rd.name;
        int rivalScore = rd == null ? 0 : rd.weekScore;
        NamedTextColor color = d.weekScore >= rivalScore ? NamedTextColor.GREEN : NamedTextColor.RED;
        player.sendMessage(PREFIX.append(Component.text("Rival: " + rivalName + " — ", NamedTextColor.YELLOW))
            .append(Component.text("you " + d.weekScore + " : " + rivalScore + " them", color))
            .append(Component.text("  (lifetime wins: " + d.lifetimeWins + ")"
                + (d.hasEdge ? "  [Rival's Edge active]" : ""), NamedTextColor.GRAY)));
        return true;
    }

    private void settleWeek() {
        List<UUID> settled = new ArrayList<>();
        for (Map.Entry<UUID, RivalData> e : players.entrySet()) {
            RivalData d = e.getValue();
            d.hasEdge = false;
            if (d.rival == null || settled.contains(e.getKey())) continue;
            RivalData rd = players.get(d.rival);
            if (rd == null) continue;
            settled.add(e.getKey());
            settled.add(d.rival);
            RivalData winner = d.weekScore >= rd.weekScore ? d : rd;
            RivalData loser = winner == d ? rd : d;
            winner.hasEdge = true;
            winner.lifetimeWins++;
            notifyIfOnline(winner == d ? e.getKey() : d.rival,
                "You OUTSCORED your rival " + loser.name + " (" + winner.weekScore + ":" + loser.weekScore
                    + ")! Rival's Edge is yours this week: Speed I + 10% bonus points.", NamedTextColor.GREEN);
            notifyIfOnline(winner == d ? d.rival : e.getKey(),
                winner.name + " outscored you " + winner.weekScore + ":" + loser.weekScore
                    + ". They stole the Rival's Edge. Take it back.", NamedTextColor.RED);
        }
        // Weekly top 3
        List<RivalData> top = new ArrayList<>(players.values());
        top.sort(Comparator.comparingInt((RivalData d) -> d.weekScore).reversed());
        StringBuilder sb = new StringBuilder("Weekly top scorers: ");
        for (int i = 0; i < Math.min(3, top.size()); i++) {
            if (top.get(i).weekScore == 0) break;
            if (i > 0) sb.append(", ");
            sb.append(top.get(i).name).append(" (").append(top.get(i).weekScore).append(")");
        }
        getServer().broadcast(PREFIX.append(Component.text(sb.toString(), NamedTextColor.GOLD)));
        for (RivalData d : players.values()) {
            d.weekScore = 0;
            d.rival = null;
            d.seenThisWeek = false;
        }
    }

    private void notifyIfOnline(UUID id, String message, NamedTextColor color) {
        Player player = Bukkit.getPlayer(id);
        if (player != null) player.sendMessage(PREFIX.append(Component.text(message, color)));
    }

    private void shufflePairs(boolean newWeek) {
        List<UUID> pool = new ArrayList<>();
        for (Map.Entry<UUID, RivalData> e : players.entrySet()) {
            e.getValue().rival = null;
            if (newWeek ? Bukkit.getPlayer(e.getKey()) != null : e.getValue().seenThisWeek
                || Bukkit.getPlayer(e.getKey()) != null) {
                pool.add(e.getKey());
            }
        }
        for (Player p : getServer().getOnlinePlayers()) {
            if (!pool.contains(p.getUniqueId())) pool.add(p.getUniqueId());
            data(p.getUniqueId()).name = p.getName();
        }
        Collections.shuffle(pool);
        for (int i = 0; i + 1 < pool.size(); i += 2) {
            UUID a = pool.get(i), b = pool.get(i + 1);
            data(a).rival = b;
            data(b).rival = a;
            notifyIfOnline(a, "Your sworn rival this week is " + data(b).name + ". Outscore them.", NamedTextColor.YELLOW);
            notifyIfOnline(b, "Your sworn rival this week is " + data(a).name + ". Outscore them.", NamedTextColor.YELLOW);
        }
        if (pool.size() % 2 == 1) {
            notifyIfOnline(pool.get(pool.size() - 1),
                "Odd player out — no rival this week. Rest up.", NamedTextColor.GRAY);
        }
        save();
    }

    // ---- persistence ----

    private File dataFile() {
        return new File(getDataFolder(), "data.yml");
    }

    private void load() {
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(dataFile());
        currentWeek = yml.getLong("currentWeek", -1);
        ConfigurationSection ps = yml.getConfigurationSection("players");
        if (ps == null) return;
        for (String key : ps.getKeys(false)) {
            ConfigurationSection s = ps.getConfigurationSection(key);
            if (s == null) continue;
            try {
                RivalData d = new RivalData();
                d.name = s.getString("name", "?");
                d.weekScore = s.getInt("weekScore");
                d.lifetimeWins = s.getInt("lifetimeWins");
                d.hasEdge = s.getBoolean("hasEdge");
                d.seenThisWeek = s.getBoolean("seenThisWeek");
                String rival = s.getString("rival");
                if (rival != null) d.rival = UUID.fromString(rival);
                players.put(UUID.fromString(key), d);
            } catch (IllegalArgumentException ignored) {}
        }
    }

    private void save() {
        YamlConfiguration yml = new YamlConfiguration();
        yml.set("currentWeek", currentWeek);
        for (Map.Entry<UUID, RivalData> e : players.entrySet()) {
            String base = "players." + e.getKey();
            RivalData d = e.getValue();
            yml.set(base + ".name", d.name);
            yml.set(base + ".weekScore", d.weekScore);
            yml.set(base + ".lifetimeWins", d.lifetimeWins);
            yml.set(base + ".hasEdge", d.hasEdge);
            yml.set(base + ".seenThisWeek", d.seenThisWeek);
            if (d.rival != null) yml.set(base + ".rival", d.rival.toString());
        }
        try {
            if (!getDataFolder().exists()) getDataFolder().mkdirs();
            yml.save(dataFile());
        } catch (IOException ex) {
            getLogger().warning("Could not save data.yml: " + ex.getMessage());
        }
    }
}
