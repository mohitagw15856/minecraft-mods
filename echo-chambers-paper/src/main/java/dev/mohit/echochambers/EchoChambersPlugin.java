package dev.mohit.echochambers;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class EchoChambersPlugin extends JavaPlugin implements Listener {

    private static final Component PREFIX = Component.text("[Echo] ", NamedTextColor.DARK_PURPLE);
    private static final int RECORD_SECONDS = 10;
    private static final int MAX_LINES = 10;
    private static final int MAX_PER_PLAYER = 5;
    private static final int RECORD_RADIUS = 16;
    private static final int TRIGGER_RADIUS = 5;
    private static final int COOLDOWN_TICKS = 1200; // 60s per player per echo

    private static class Echo {
        String world;
        int x, y, z;
        UUID owner;
        String ownerName;
        List<String> lines = new ArrayList<>();

        String key() {
            return world + ":" + x + ":" + y + ":" + z;
        }

        Location location() {
            World w = Bukkit.getWorld(world);
            return w == null ? null : new Location(w, x + 0.5, y + 0.5, z + 0.5);
        }
    }

    private static class Recording {
        Echo echo;
        long endTick;
    }

    private final Map<String, Echo> echoes = new HashMap<>();
    private final Map<UUID, Recording> recordings = new ConcurrentHashMap<>();
    private final Map<String, Long> cooldowns = new HashMap<>(); // playerUuid:echoKey -> tick
    private long tick;

    @Override
    public void onEnable() {
        load();
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getScheduler().runTaskTimer(this, this::poll, 40L, 40L);
        getServer().getScheduler().runTaskTimer(this, () -> tick += 20, 20L, 20L);
    }

    @Override
    public void onDisable() {
        save();
    }

    private Block targetLodestone(Player player) {
        Block block = player.getTargetBlockExact(5);
        return block != null && block.getType() == Material.LODESTONE ? block : null;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        String sub = args.length == 0 ? "help" : args[0].toLowerCase();
        switch (sub) {
            case "record" -> record(player);
            case "clear" -> clear(player);
            case "list" -> list(player);
            default -> player.sendMessage(PREFIX.append(Component.text(
                "Place a lodestone, look at it, then /echo record — everything said nearby for "
                    + RECORD_SECONDS + "s is bound to it and replays for passers-by. /echo clear, /echo list",
                NamedTextColor.YELLOW)));
        }
        return true;
    }

    private void record(Player player) {
        Block block = targetLodestone(player);
        if (block == null) {
            player.sendMessage(PREFIX.append(Component.text(
                "Look at a lodestone (within 5 blocks) to bind an echo.", NamedTextColor.RED)));
            return;
        }
        String key = block.getWorld().getName() + ":" + block.getX() + ":" + block.getY() + ":" + block.getZ();
        if (echoes.containsKey(key)) {
            player.sendMessage(PREFIX.append(Component.text(
                "This lodestone already holds an echo. /echo clear first.", NamedTextColor.RED)));
            return;
        }
        long owned = echoes.values().stream().filter(e -> e.owner.equals(player.getUniqueId())).count();
        if (owned >= MAX_PER_PLAYER) {
            player.sendMessage(PREFIX.append(Component.text(
                "You already keep " + MAX_PER_PLAYER + " echoes.", NamedTextColor.RED)));
            return;
        }
        Echo echo = new Echo();
        echo.world = block.getWorld().getName();
        echo.x = block.getX();
        echo.y = block.getY();
        echo.z = block.getZ();
        echo.owner = player.getUniqueId();
        echo.ownerName = player.getName();
        Recording rec = new Recording();
        rec.echo = echo;
        rec.endTick = tick + RECORD_SECONDS * 20L;
        recordings.put(player.getUniqueId(), rec);

        player.sendMessage(PREFIX.append(Component.text(
            "Recording... everything said within " + RECORD_RADIUS + " blocks of the lodestone for the next "
                + RECORD_SECONDS + " seconds will be bound to it.", NamedTextColor.GREEN)));
        player.playSound(block.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1f, 1.5f);

        getServer().getScheduler().runTaskLater(this, () -> {
            recordings.remove(player.getUniqueId());
            if (echo.lines.isEmpty()) {
                player.sendMessage(PREFIX.append(Component.text(
                    "Nothing was said — no echo bound.", NamedTextColor.GRAY)));
                return;
            }
            echoes.put(echo.key(), echo);
            save();
            player.sendMessage(PREFIX.append(Component.text(
                "Echo bound: " + echo.lines.size() + " line" + (echo.lines.size() == 1 ? "" : "s")
                    + ". It will whisper to anyone who comes within " + TRIGGER_RADIUS + " blocks.",
                NamedTextColor.GREEN)));
        }, RECORD_SECONDS * 20L);
    }

    @EventHandler
    public void onChat(AsyncChatEvent event) {
        if (recordings.isEmpty()) return;
        String text = PlainTextComponentSerializer.plainText().serialize(event.message());
        String speaker = event.getPlayer().getName();
        Location speakerLoc = event.getPlayer().getLocation();
        for (Recording rec : recordings.values()) {
            if (rec.echo.lines.size() >= MAX_LINES) continue;
            Location lodestone = rec.echo.location();
            if (lodestone == null || !lodestone.getWorld().equals(speakerLoc.getWorld())) continue;
            if (lodestone.distanceSquared(speakerLoc) <= RECORD_RADIUS * RECORD_RADIUS) {
                rec.echo.lines.add(speaker + ": " + text);
            }
        }
    }

    private void clear(Player player) {
        Block block = targetLodestone(player);
        if (block == null) {
            player.sendMessage(PREFIX.append(Component.text("Look at the lodestone to clear.", NamedTextColor.RED)));
            return;
        }
        String key = block.getWorld().getName() + ":" + block.getX() + ":" + block.getY() + ":" + block.getZ();
        Echo echo = echoes.get(key);
        if (echo == null) {
            player.sendMessage(PREFIX.append(Component.text("No echo here.", NamedTextColor.GRAY)));
            return;
        }
        if (!echo.owner.equals(player.getUniqueId()) && !player.isOp()) {
            player.sendMessage(PREFIX.append(Component.text(
                "Only " + echo.ownerName + " (or an op) can clear this echo.", NamedTextColor.RED)));
            return;
        }
        echoes.remove(key);
        save();
        player.sendMessage(PREFIX.append(Component.text("Echo released.", NamedTextColor.GRAY)));
    }

    private void list(Player player) {
        boolean any = false;
        for (Echo echo : echoes.values()) {
            if (!echo.owner.equals(player.getUniqueId())) continue;
            any = true;
            player.sendMessage(PREFIX.append(Component.text(
                echo.world + " (" + echo.x + ", " + echo.y + ", " + echo.z + ") — "
                    + echo.lines.size() + " lines", NamedTextColor.YELLOW)));
        }
        if (!any) player.sendMessage(PREFIX.append(Component.text("You keep no echoes.", NamedTextColor.GRAY)));
    }

    @EventHandler
    public void onBreak(BlockBreakEvent event) {
        if (event.getBlock().getType() != Material.LODESTONE) return;
        Block block = event.getBlock();
        String key = block.getWorld().getName() + ":" + block.getX() + ":" + block.getY() + ":" + block.getZ();
        if (echoes.remove(key) != null) {
            save();
            event.getPlayer().sendMessage(PREFIX.append(Component.text(
                "The echo bound here fades away...", NamedTextColor.GRAY)));
        }
    }

    private void poll() {
        for (Echo echo : echoes.values()) {
            Location loc = echo.location();
            if (loc == null || !loc.isChunkLoaded()) continue;
            for (Player player : loc.getWorld().getPlayers()) {
                if (player.getLocation().distanceSquared(loc) > TRIGGER_RADIUS * TRIGGER_RADIUS) continue;
                String cdKey = player.getUniqueId() + ":" + echo.key();
                Long last = cooldowns.get(cdKey);
                if (last != null && tick - last < COOLDOWN_TICKS) continue;
                cooldowns.put(cdKey, tick);
                replay(player, echo, loc);
            }
        }
    }

    private void replay(Player player, Echo echo, Location loc) {
        player.playSound(loc, Sound.BLOCK_BEACON_AMBIENT, 1f, 0.5f);
        loc.getWorld().spawnParticle(Particle.ENCHANT, loc.clone().add(0, 0.8, 0), 30, 0.4, 0.4, 0.4, 0.5);
        player.sendMessage(PREFIX.append(Component.text(
            "An echo bound by " + echo.ownerName + " stirs...", NamedTextColor.DARK_PURPLE)));
        for (int i = 0; i < echo.lines.size(); i++) {
            String line = echo.lines.get(i);
            getServer().getScheduler().runTaskLater(this, () -> {
                if (player.isOnline()) {
                    player.sendMessage(Component.text("  ~ " + line, NamedTextColor.GRAY)
                        .decorate(TextDecoration.ITALIC));
                    player.playSound(loc, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.6f, 0.8f);
                }
            }, 20L * (i + 1));
        }
    }

    // ---- persistence ----

    private File dataFile() {
        return new File(getDataFolder(), "echoes.yml");
    }

    private void load() {
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(dataFile());
        for (String key : yml.getKeys(false)) {
            ConfigurationSection s = yml.getConfigurationSection(key);
            if (s == null) continue;
            try {
                Echo echo = new Echo();
                echo.world = s.getString("world");
                echo.x = s.getInt("x");
                echo.y = s.getInt("y");
                echo.z = s.getInt("z");
                echo.owner = UUID.fromString(s.getString("owner", ""));
                echo.ownerName = s.getString("ownerName", "?");
                echo.lines = new ArrayList<>(s.getStringList("lines"));
                if (echo.world != null) echoes.put(echo.key(), echo);
            } catch (IllegalArgumentException ignored) {}
        }
    }

    private void save() {
        YamlConfiguration yml = new YamlConfiguration();
        int i = 0;
        for (Echo echo : echoes.values()) {
            String base = "e" + (i++);
            yml.set(base + ".world", echo.world);
            yml.set(base + ".x", echo.x);
            yml.set(base + ".y", echo.y);
            yml.set(base + ".z", echo.z);
            yml.set(base + ".owner", echo.owner.toString());
            yml.set(base + ".ownerName", echo.ownerName);
            yml.set(base + ".lines", echo.lines);
        }
        try {
            if (!getDataFolder().exists()) getDataFolder().mkdirs();
            yml.save(dataFile());
        } catch (IOException e) {
            getLogger().warning("Could not save echoes.yml: " + e.getMessage());
        }
    }
}
