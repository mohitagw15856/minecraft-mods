package dev.mohit.weatherinsurance;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.weather.LightningStrikeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class WeatherInsurancePlugin extends JavaPlugin implements Listener {

    private static final Component PREFIX = Component.text("[Insurance] ", NamedTextColor.BLUE);
    private static final int PREMIUM = 10;
    private static final int DURATION_DAYS = 7;
    private static final int COVERAGE = 40;
    private static final int MAX_POLICIES = 3;

    private static class Policy {
        UUID owner;
        String ownerName;
        long expiresDay;
        int coverageLeft;
    }

    private final Map<String, Policy> policies = new HashMap<>();
    private final Map<UUID, Integer> pendingPayouts = new HashMap<>();

    @Override
    public void onEnable() {
        load();
        getServer().getPluginManager().registerEvents(this, this);
    }

    @Override
    public void onDisable() {
        save();
    }

    private long currentDay() {
        return Bukkit.getWorlds().get(0).getFullTime() / 24000L;
    }

    private String chunkKey(Chunk chunk) {
        return chunk.getWorld().getName() + ":" + chunk.getX() + ":" + chunk.getZ();
    }

    private Policy policyAt(Chunk chunk) {
        Policy policy = policies.get(chunkKey(chunk));
        if (policy != null && policy.expiresDay < currentDay()) {
            policies.remove(chunkKey(chunk));
            return null;
        }
        return policy;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        String sub = args.length == 0 ? "buy" : args[0].toLowerCase();
        switch (sub) {
            case "buy" -> buy(player);
            case "status" -> status(player);
            case "cancel" -> cancel(player);
            default -> player.sendMessage(PREFIX.append(Component.text(
                "/insure — insure this chunk (" + PREMIUM + " emeralds, " + DURATION_DAYS
                    + " days, " + COVERAGE + " coverage). /insure status, /insure cancel", NamedTextColor.YELLOW)));
        }
        return true;
    }

    private void buy(Player player) {
        Chunk chunk = player.getLocation().getChunk();
        if (policyAt(chunk) != null) {
            player.sendMessage(PREFIX.append(Component.text("This chunk is already insured.", NamedTextColor.RED)));
            return;
        }
        long owned = policies.values().stream().filter(p -> p.owner.equals(player.getUniqueId())).count();
        if (owned >= MAX_POLICIES) {
            player.sendMessage(PREFIX.append(Component.text(
                "You already hold " + MAX_POLICIES + " policies.", NamedTextColor.RED)));
            return;
        }
        if (!player.getInventory().containsAtLeast(new ItemStack(Material.EMERALD), PREMIUM)) {
            player.sendMessage(PREFIX.append(Component.text(
                "The premium is " + PREMIUM + " emeralds.", NamedTextColor.RED)));
            return;
        }
        player.getInventory().removeItem(new ItemStack(Material.EMERALD, PREMIUM));
        Policy policy = new Policy();
        policy.owner = player.getUniqueId();
        policy.ownerName = player.getName();
        policy.expiresDay = currentDay() + DURATION_DAYS;
        policy.coverageLeft = COVERAGE;
        policies.put(chunkKey(chunk), policy);
        save();
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1f, 1.4f);
        player.sendMessage(PREFIX.append(Component.text(
            "Chunk (" + chunk.getX() + ", " + chunk.getZ() + ") insured for " + DURATION_DAYS
                + " days, up to " + COVERAGE + " emeralds of damage. Sleep easy.", NamedTextColor.GREEN)));
    }

    private void status(Player player) {
        long today = currentDay();
        boolean any = false;
        for (Map.Entry<String, Policy> e : policies.entrySet()) {
            Policy p = e.getValue();
            if (!p.owner.equals(player.getUniqueId()) || p.expiresDay < today) continue;
            any = true;
            player.sendMessage(PREFIX.append(Component.text(
                e.getKey() + " — " + (p.expiresDay - today) + " days left, "
                    + p.coverageLeft + " coverage remaining", NamedTextColor.YELLOW)));
        }
        if (!any) {
            player.sendMessage(PREFIX.append(Component.text("You hold no active policies.", NamedTextColor.GRAY)));
        }
    }

    private void cancel(Player player) {
        Chunk chunk = player.getLocation().getChunk();
        Policy policy = policyAt(chunk);
        if (policy == null || !policy.owner.equals(player.getUniqueId())) {
            player.sendMessage(PREFIX.append(Component.text(
                "You hold no policy on this chunk.", NamedTextColor.RED)));
            return;
        }
        policies.remove(chunkKey(chunk));
        save();
        player.sendMessage(PREFIX.append(Component.text("Policy cancelled (no refund).", NamedTextColor.GRAY)));
    }

    private void payOut(Policy policy, String key, int amount, String cause) {
        amount = Math.min(amount, policy.coverageLeft);
        if (amount <= 0) return;
        policy.coverageLeft -= amount;
        Player owner = Bukkit.getPlayer(policy.owner);
        if (owner != null) {
            owner.getInventory().addItem(new ItemStack(Material.EMERALD, amount)).values()
                .forEach(left -> owner.getWorld().dropItemNaturally(owner.getLocation(), left));
            owner.playSound(owner.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1f, 1f);
            owner.sendMessage(PREFIX.append(Component.text(
                "Claim paid: +" + amount + " emeralds for " + cause + " damage in " + key
                    + " (" + policy.coverageLeft + " coverage left).", NamedTextColor.GREEN)));
        } else {
            pendingPayouts.merge(policy.owner, amount, Integer::sum);
        }
        if (policy.coverageLeft <= 0) policies.remove(key);
        save();
    }

    private void handleBlockDamage(List<Block> blocks, String cause) {
        Map<String, Integer> damaged = new HashMap<>();
        for (Block block : blocks) {
            if (block.getType().isAir()) continue;
            String key = chunkKey(block.getChunk());
            if (policies.containsKey(key)) damaged.merge(key, 1, Integer::sum);
        }
        for (Map.Entry<String, Integer> e : damaged.entrySet()) {
            Policy policy = policies.get(e.getKey());
            if (policy == null || policy.expiresDay < currentDay()) continue;
            payOut(policy, e.getKey(), Math.min(e.getValue(), 16), cause);
        }
    }

    @EventHandler
    public void onEntityExplode(EntityExplodeEvent event) {
        handleBlockDamage(event.blockList(), "explosion");
    }

    @EventHandler
    public void onBlockExplode(BlockExplodeEvent event) {
        handleBlockDamage(event.blockList(), "explosion");
    }

    @EventHandler
    public void onBurn(BlockBurnEvent event) {
        handleBlockDamage(List.of(event.getBlock()), "fire");
    }

    @EventHandler
    public void onLightning(LightningStrikeEvent event) {
        Policy policy = policyAt(event.getLightning().getChunk());
        if (policy != null) {
            payOut(policy, chunkKey(event.getLightning().getChunk()), 3, "lightning");
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Integer pending = pendingPayouts.remove(event.getPlayer().getUniqueId());
        if (pending == null || pending <= 0) return;
        int amount = pending;
        getServer().getScheduler().runTaskLater(this, () -> {
            Player player = event.getPlayer();
            if (!player.isOnline()) {
                pendingPayouts.merge(player.getUniqueId(), amount, Integer::sum);
                return;
            }
            player.getInventory().addItem(new ItemStack(Material.EMERALD, amount)).values()
                .forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
            player.sendMessage(PREFIX.append(Component.text(
                "While you were away, claims paid out " + amount + " emeralds.", NamedTextColor.GREEN)));
            save();
        }, 40L);
    }

    // ---- persistence ----

    private File dataFile() {
        return new File(getDataFolder(), "data.yml");
    }

    private void load() {
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(dataFile());
        ConfigurationSection ps = yml.getConfigurationSection("policies");
        if (ps != null) {
            for (String key : ps.getKeys(false)) {
                ConfigurationSection s = ps.getConfigurationSection(key);
                if (s == null) continue;
                try {
                    Policy policy = new Policy();
                    policy.owner = UUID.fromString(s.getString("owner", ""));
                    policy.ownerName = s.getString("ownerName", "?");
                    policy.expiresDay = s.getLong("expiresDay");
                    policy.coverageLeft = s.getInt("coverageLeft");
                    policies.put(key.replace('_', ':'), policy);
                } catch (IllegalArgumentException ignored) {}
            }
        }
        ConfigurationSection pp = yml.getConfigurationSection("pending");
        if (pp != null) {
            for (String key : pp.getKeys(false)) {
                try {
                    pendingPayouts.put(UUID.fromString(key), pp.getInt(key));
                } catch (IllegalArgumentException ignored) {}
            }
        }
    }

    private void save() {
        YamlConfiguration yml = new YamlConfiguration();
        for (Map.Entry<String, Policy> e : policies.entrySet()) {
            String base = "policies." + e.getKey().replace(':', '_');
            yml.set(base + ".owner", e.getValue().owner.toString());
            yml.set(base + ".ownerName", e.getValue().ownerName);
            yml.set(base + ".expiresDay", e.getValue().expiresDay);
            yml.set(base + ".coverageLeft", e.getValue().coverageLeft);
        }
        for (Map.Entry<UUID, Integer> e : pendingPayouts.entrySet()) {
            yml.set("pending." + e.getKey(), e.getValue());
        }
        try {
            if (!getDataFolder().exists()) getDataFolder().mkdirs();
            yml.save(dataFile());
        } catch (IOException ex) {
            getLogger().warning("Could not save data.yml: " + ex.getMessage());
        }
    }
}
