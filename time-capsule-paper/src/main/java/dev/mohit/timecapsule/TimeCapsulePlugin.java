package dev.mohit.timecapsule;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

public final class TimeCapsulePlugin extends JavaPlugin implements Listener {

    private static final Component PREFIX = Component.text("[TimeCapsule] ", NamedTextColor.GOLD);
    private static final int MAX_PER_PLAYER = 5;
    private static final int MAX_DAYS = 30;

    private final Map<String, Capsule> capsules = new HashMap<>();

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

    private Capsule capsuleAt(Block block) {
        if (block == null) return null;
        return capsules.get(block.getWorld().getName() + ":" + block.getX() + ":" + block.getY() + ":" + block.getZ());
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        if (args.length == 0) {
            player.sendMessage(PREFIX.append(Component.text(
                "/capsule seal <days> [message], /capsule check, /capsule list", NamedTextColor.YELLOW)));
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "seal" -> seal(player, args);
            case "check" -> check(player);
            case "list" -> list(player);
            default -> player.sendMessage(PREFIX.append(Component.text("Unknown subcommand.", NamedTextColor.RED)));
        }
        return true;
    }

    private Block targetChest(Player player) {
        Block block = player.getTargetBlockExact(5);
        if (block == null || !(block.getState() instanceof Chest)) return null;
        return block;
    }

    private void seal(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(PREFIX.append(Component.text("Usage: /capsule seal <days> [message]", NamedTextColor.RED)));
            return;
        }
        int days;
        try {
            days = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            player.sendMessage(PREFIX.append(Component.text("Days must be a number.", NamedTextColor.RED)));
            return;
        }
        if (days < 1 || days > MAX_DAYS) {
            player.sendMessage(PREFIX.append(Component.text("Days must be 1-" + MAX_DAYS + ".", NamedTextColor.RED)));
            return;
        }
        long owned = capsules.values().stream().filter(c -> c.owner.equals(player.getUniqueId())).count();
        if (owned >= MAX_PER_PLAYER) {
            player.sendMessage(PREFIX.append(Component.text("You already have " + MAX_PER_PLAYER + " sealed capsules.", NamedTextColor.RED)));
            return;
        }
        Block block = targetChest(player);
        if (block == null) {
            player.sendMessage(PREFIX.append(Component.text("Look at a chest (within 5 blocks) to seal it.", NamedTextColor.RED)));
            return;
        }
        if (capsuleAt(block) != null) {
            player.sendMessage(PREFIX.append(Component.text("That chest is already sealed.", NamedTextColor.RED)));
            return;
        }
        StringJoiner joiner = new StringJoiner(" ");
        for (int i = 2; i < args.length; i++) joiner.add(args[i]);
        long today = currentDay();
        Capsule capsule = new Capsule(block.getWorld().getName(), block.getX(), block.getY(), block.getZ(),
            player.getUniqueId(), player.getName(), today, today + days, joiner.toString());
        capsules.put(capsule.key(), capsule);
        save();
        player.playSound(block.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1f, 0.7f);
        player.sendMessage(PREFIX.append(Component.text(
            "Sealed! This chest will open on day " + capsule.unlockDay + " (" + days + " days from now).",
            NamedTextColor.GREEN)));
    }

    private void check(Player player) {
        Block block = targetChest(player);
        Capsule capsule = block == null ? null : capsuleAt(block);
        if (capsule == null) {
            player.sendMessage(PREFIX.append(Component.text("Look at a sealed capsule to check it.", NamedTextColor.RED)));
            return;
        }
        long left = capsule.unlockDay - currentDay();
        player.sendMessage(PREFIX.append(Component.text(
            capsule.ownerName + "'s capsule, sealed on day " + capsule.sealedDay
                + (left > 0 ? " — opens in " + left + " day" + (left == 1 ? "" : "s") + "." : " — ready to open!"),
            NamedTextColor.YELLOW)));
    }

    private void list(Player player) {
        List<Capsule> own = capsules.values().stream()
            .filter(c -> c.owner.equals(player.getUniqueId())).toList();
        if (own.isEmpty()) {
            player.sendMessage(PREFIX.append(Component.text("You have no sealed capsules.", NamedTextColor.GRAY)));
            return;
        }
        long today = currentDay();
        player.sendMessage(PREFIX.append(Component.text("Your capsules:", NamedTextColor.YELLOW)));
        for (Capsule c : own) {
            long left = c.unlockDay - today;
            player.sendMessage(Component.text(
                "  " + c.world + " (" + c.x + ", " + c.y + ", " + c.z + ") — "
                    + (left > 0 ? left + " day" + (left == 1 ? "" : "s") + " left" : "ready to open"),
                left > 0 ? NamedTextColor.GRAY : NamedTextColor.GREEN));
        }
    }

    @EventHandler
    public void onOpen(InventoryOpenEvent event) {
        Location loc = event.getInventory().getLocation();
        if (loc == null) return;
        Capsule capsule = capsuleAt(loc.getBlock());
        if (capsule == null) return;
        if (!(event.getPlayer() instanceof Player player)) return;

        long left = capsule.unlockDay - currentDay();
        if (left > 0) {
            event.setCancelled(true);
            player.sendMessage(PREFIX.append(Component.text(
                "Sealed for " + left + " more day" + (left == 1 ? "" : "s") + ".", NamedTextColor.RED)));
            player.playSound(loc, Sound.BLOCK_CHEST_LOCKED, 1f, 1f);
            return;
        }
        // Unlock!
        long age = currentDay() - capsule.sealedDay;
        capsules.remove(capsule.key());
        save();
        getServer().broadcast(PREFIX.append(Component.text(
            capsule.ownerName + "'s time capsule from day " + capsule.sealedDay
                + " has been opened after " + age + " days!", NamedTextColor.GOLD)));
        if (!capsule.message.isEmpty()) {
            player.sendMessage(Component.text("The note inside reads: ", NamedTextColor.YELLOW)
                .append(Component.text("\"" + capsule.message + "\"", NamedTextColor.WHITE)));
        }
        player.playSound(loc, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
    }

    @EventHandler
    public void onBreak(BlockBreakEvent event) {
        Capsule capsule = capsuleAt(event.getBlock());
        if (capsule == null) return;
        if (capsule.unlockDay - currentDay() <= 0) return; // unlocked capsules break normally
        event.setCancelled(true);
        boolean own = capsule.owner.equals(event.getPlayer().getUniqueId());
        event.getPlayer().sendMessage(PREFIX.append(Component.text(
            own ? "Your capsule is sealed — it cannot be broken until day " + capsule.unlockDay + "."
                : "This is " + capsule.ownerName + "'s sealed time capsule. It cannot be broken.",
            NamedTextColor.RED)));
    }

    @EventHandler
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(b -> {
            Capsule c = capsuleAt(b);
            return c != null && c.unlockDay - currentDay() > 0;
        });
    }

    @EventHandler
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(b -> {
            Capsule c = capsuleAt(b);
            return c != null && c.unlockDay - currentDay() > 0;
        });
    }

    // ---- persistence ----

    private File dataFile() {
        return new File(getDataFolder(), "capsules.yml");
    }

    private void load() {
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(dataFile());
        for (String key : yml.getKeys(false)) {
            ConfigurationSection s = yml.getConfigurationSection(key);
            if (s == null) continue;
            Capsule c = Capsule.deserialize(s.getValues(false));
            if (c != null) capsules.put(c.key(), c);
        }
    }

    private void save() {
        YamlConfiguration yml = new YamlConfiguration();
        int i = 0;
        for (Capsule c : capsules.values()) yml.set("c" + (i++), c.serialize());
        try {
            if (!getDataFolder().exists()) getDataFolder().mkdirs();
            yml.save(dataFile());
        } catch (IOException e) {
            getLogger().warning("Could not save capsules.yml: " + e.getMessage());
        }
    }
}
