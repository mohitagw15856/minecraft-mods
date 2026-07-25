package dev.mohit.ghostreplay;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class GhostReplayPlugin extends JavaPlugin implements Listener {

    private static final Component PREFIX = Component.text("[GhostReplay] ", NamedTextColor.AQUA);
    private static final int SAMPLE_TICKS = 2;
    private static final int MAX_SAMPLES = 300; // 30 seconds

    private NamespacedKey ghostKey;
    private final Map<UUID, Deque<Location>> buffers = new HashMap<>();
    private final Map<UUID, List<Location>> lastDeathPath = new HashMap<>();
    private final Set<UUID> disabled = new HashSet<>();
    private final Set<UUID> replayUsed = new HashSet<>();

    @Override
    public void onEnable() {
        ghostKey = new NamespacedKey(this, "ghost");
        loadToggles();
        getServer().getPluginManager().registerEvents(this, this);
        for (World world : getServer().getWorlds()) {
            world.getEntities().forEach(this::removeIfGhost);
        }
        getServer().getScheduler().runTaskTimer(this, () -> {
            for (Player player : getServer().getOnlinePlayers()) {
                if (disabled.contains(player.getUniqueId()) || player.isDead()) continue;
                Deque<Location> buf = buffers.computeIfAbsent(player.getUniqueId(), id -> new ArrayDeque<>());
                buf.addLast(player.getLocation().clone());
                if (buf.size() > MAX_SAMPLES) buf.removeFirst();
            }
        }, SAMPLE_TICKS, SAMPLE_TICKS);
    }

    @Override
    public void onDisable() {
        for (World world : getServer().getWorlds()) {
            world.getEntities().forEach(this::removeIfGhost);
        }
        saveToggles();
    }

    private void removeIfGhost(Entity entity) {
        if (entity.getPersistentDataContainer().has(ghostKey, PersistentDataType.BYTE)) entity.remove();
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        for (Entity entity : event.getChunk().getEntities()) removeIfGhost(entity);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        UUID id = player.getUniqueId();
        if (args.length > 0 && args[0].equalsIgnoreCase("last")) {
            List<Location> path = lastDeathPath.get(id);
            if (path == null || replayUsed.contains(id)) {
                player.sendMessage(PREFIX.append(Component.text(
                    path == null ? "No recorded death yet." : "You already replayed this death.", NamedTextColor.RED)));
                return true;
            }
            replayUsed.add(id);
            runReplay(player, path);
            return true;
        }
        if (disabled.remove(id)) {
            player.sendMessage(PREFIX.append(Component.text("Ghost replays enabled.", NamedTextColor.GREEN)));
        } else {
            disabled.add(id);
            buffers.remove(id);
            player.sendMessage(PREFIX.append(Component.text("Ghost replays disabled.", NamedTextColor.GRAY)));
        }
        saveToggles();
        return true;
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getPlayer();
        UUID id = player.getUniqueId();
        if (disabled.contains(id)) return;
        Deque<Location> buf = buffers.remove(id);
        if (buf == null || buf.size() < 10) return;
        lastDeathPath.put(id, new ArrayList<>(buf));
        replayUsed.remove(id);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        List<Location> path = lastDeathPath.get(player.getUniqueId());
        if (path == null || disabled.contains(player.getUniqueId())) return;
        Location death = path.get(path.size() - 1);
        player.sendMessage(PREFIX.append(Component.text(
            "You died at " + death.getBlockX() + ", " + death.getBlockY() + ", " + death.getBlockZ()
                + " in " + death.getWorld().getName() + ". Follow your ghost!", NamedTextColor.YELLOW)));
        getServer().getScheduler().runTaskLater(this, () -> {
            if (player.isOnline() && !replayUsed.contains(player.getUniqueId())) {
                replayUsed.add(player.getUniqueId());
                runReplay(player, path);
            }
        }, 40L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        buffers.remove(event.getPlayer().getUniqueId());
    }

    private void runReplay(Player player, List<Location> path) {
        Location start = path.get(0);
        if (!start.isChunkLoaded()) start.getChunk().load();

        ArmorStand ghost = start.getWorld().spawn(start, ArmorStand.class, stand -> {
            stand.setInvisible(true);
            stand.setGravity(false);
            stand.setInvulnerable(true);
            stand.setCanPickupItems(false);
            stand.setCollidable(false);
            stand.customName(Component.text(player.getName() + "'s ghost", NamedTextColor.AQUA));
            stand.setCustomNameVisible(true);
            ItemStack head = new ItemStack(org.bukkit.Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            meta.setOwningPlayer(Bukkit.getOfflinePlayer(player.getUniqueId()));
            head.setItemMeta(meta);
            stand.getEquipment().setHelmet(head);
            stand.getPersistentDataContainer().set(ghostKey, PersistentDataType.BYTE, (byte) 1);
        });

        new BukkitRunnable() {
            int index = 0;
            int linger = 0;

            @Override
            public void run() {
                if (!ghost.isValid()) {
                    cancel();
                    return;
                }
                if (index < path.size()) {
                    Location loc = path.get(index++);
                    ghost.teleport(loc);
                    loc.getWorld().spawnParticle(Particle.SOUL, loc.clone().add(0, 1, 0), 2, 0.1, 0.2, 0.1, 0.01);
                } else {
                    if (linger == 0) {
                        Location end = ghost.getLocation();
                        end.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, end.clone().add(0, 1, 0), 40, 0.4, 0.6, 0.4, 0.02);
                        end.getWorld().playSound(end, Sound.ENTITY_VEX_AMBIENT, 1f, 0.6f);
                    }
                    if (++linger >= 100) { // stand at the death point 10 seconds
                        ghost.remove();
                        cancel();
                    }
                }
            }
        }.runTaskTimer(this, 1L, SAMPLE_TICKS);
    }

    // ---- persistence (toggle only) ----

    private File togglesFile() {
        return new File(getDataFolder(), "toggles.yml");
    }

    private void loadToggles() {
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(togglesFile());
        for (String key : yml.getStringList("disabled")) {
            try {
                disabled.add(UUID.fromString(key));
            } catch (IllegalArgumentException ignored) {}
        }
    }

    private void saveToggles() {
        YamlConfiguration yml = new YamlConfiguration();
        yml.set("disabled", disabled.stream().map(UUID::toString).toList());
        try {
            if (!getDataFolder().exists()) getDataFolder().mkdirs();
            yml.save(togglesFile());
        } catch (IOException e) {
            getLogger().warning("Could not save toggles.yml: " + e.getMessage());
        }
    }
}
