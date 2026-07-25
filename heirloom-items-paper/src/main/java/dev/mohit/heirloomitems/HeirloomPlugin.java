package dev.mohit.heirloomitems;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class HeirloomPlugin extends JavaPlugin implements Listener {

    private static final Component PREFIX = Component.text("[Heirloom] ", NamedTextColor.GOLD);

    private NamespacedKey ownerKey, generationKey, lineageKey;
    private final Map<UUID, UUID> heirs = new HashMap<>();
    private final Map<UUID, String> heirNames = new HashMap<>();
    private final Set<UUID> warnNoHeir = new HashSet<>();
    private File vaultFile;

    @Override
    public void onEnable() {
        ownerKey = new NamespacedKey(this, "owner");
        generationKey = new NamespacedKey(this, "generation");
        lineageKey = new NamespacedKey(this, "lineage");
        vaultFile = new File(getDataFolder(), "vault.yml");
        load();
        getServer().getPluginManager().registerEvents(this, this);
    }

    @Override
    public void onDisable() {
        save();
    }

    private boolean isHeirloom(ItemStack item) {
        return item != null && item.hasItemMeta()
            && item.getItemMeta().getPersistentDataContainer().has(ownerKey, PersistentDataType.STRING);
    }

    private void writeTag(ItemStack item, UUID owner, int generation, String lineage) {
        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(ownerKey, PersistentDataType.STRING, owner.toString());
        pdc.set(generationKey, PersistentDataType.INTEGER, generation);
        pdc.set(lineageKey, PersistentDataType.STRING, lineage);
        List<Component> lore = new ArrayList<>();
        String[] names = lineage.split(",");
        lore.add(Component.text("Heirloom of " + names[names.length - 1], NamedTextColor.GOLD)
            .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("Generation " + generation, NamedTextColor.YELLOW)
            .decoration(TextDecoration.ITALIC, false));
        if (names.length > 1) {
            lore.add(Component.text("Lineage: " + String.join(" → ", names), NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        }
        meta.lore(lore);
        item.setItemMeta(meta);
    }

    private void stripTag(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.remove(ownerKey);
        pdc.remove(generationKey);
        pdc.remove(lineageKey);
        meta.lore(null);
        item.setItemMeta(meta);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        String sub = args.length == 0 ? "status" : args[0].toLowerCase();
        switch (sub) {
            case "set" -> set(player);
            case "heir" -> {
                if (args.length < 2) {
                    player.sendMessage(PREFIX.append(Component.text("Usage: /heirloom heir <player>", NamedTextColor.RED)));
                } else {
                    heir(player, args[1]);
                }
            }
            case "status" -> status(player);
            default -> player.sendMessage(PREFIX.append(Component.text(
                "/heirloom <set|heir|status>", NamedTextColor.YELLOW)));
        }
        return true;
    }

    private void set(Player player) {
        ItemStack held = player.getInventory().getItemInMainHand();
        if (held.getType().isAir()) {
            player.sendMessage(PREFIX.append(Component.text("Hold the item you want to make your heirloom.", NamedTextColor.RED)));
            return;
        }
        // Untag any previous heirloom of this player found in their inventory
        boolean replaced = false;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item != held && isHeirloom(item)
                && player.getUniqueId().toString().equals(
                    item.getItemMeta().getPersistentDataContainer().get(ownerKey, PersistentDataType.STRING))) {
                stripTag(item);
                replaced = true;
            }
        }
        writeTag(held, player.getUniqueId(), 1, player.getName());
        player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1f, 1.2f);
        player.sendMessage(PREFIX.append(Component.text(
            "This " + held.getType().name().toLowerCase().replace('_', ' ') + " is now your heirloom"
                + (replaced ? " (your previous heirloom was released)" : "")
                + ". Set an heir with /heirloom heir <player>.", NamedTextColor.GREEN)));
    }

    private void heir(Player player, String name) {
        OfflinePlayer target = null;
        for (OfflinePlayer offline : getServer().getOfflinePlayers()) {
            if (name.equalsIgnoreCase(offline.getName())) {
                target = offline;
                break;
            }
        }
        Player online = getServer().getPlayerExact(name);
        if (online != null) target = online;
        if (target == null) {
            player.sendMessage(PREFIX.append(Component.text(
                "No player named '" + name + "' has ever joined this server.", NamedTextColor.RED)));
            return;
        }
        if (target.getUniqueId().equals(player.getUniqueId())) {
            player.sendMessage(PREFIX.append(Component.text("You cannot be your own heir.", NamedTextColor.RED)));
            return;
        }
        heirs.put(player.getUniqueId(), target.getUniqueId());
        heirNames.put(target.getUniqueId(), target.getName() == null ? name : target.getName());
        save();
        player.sendMessage(PREFIX.append(Component.text(
            (target.getName() == null ? name : target.getName()) + " is now your heir.", NamedTextColor.GREEN)));
    }

    private void status(Player player) {
        ItemStack found = null;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && isHeirloom(item)) {
                found = item;
                break;
            }
        }
        UUID heir = heirs.get(player.getUniqueId());
        String heirName = heir == null ? null : heirNames.getOrDefault(heir, "?");
        player.sendMessage(PREFIX.append(Component.text(
            (found == null ? "No heirloom in your inventory."
                : "Heirloom: " + found.getType().name().toLowerCase().replace('_', ' ')
                    + " (Generation " + found.getItemMeta().getPersistentDataContainer()
                        .getOrDefault(generationKey, PersistentDataType.INTEGER, 1) + ").")
                + (heirName == null ? " No heir set — /heirloom heir <player>." : " Heir: " + heirName + "."),
            NamedTextColor.YELLOW)));
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player dead = event.getPlayer();
        ItemStack heirloom = null;
        for (ItemStack item : event.getDrops()) {
            if (isHeirloom(item)) {
                heirloom = item;
                break;
            }
        }
        if (heirloom == null) return;
        UUID heirId = heirs.get(dead.getUniqueId());
        if (heirId == null) {
            warnNoHeir.add(dead.getUniqueId());
            return; // drops normally
        }
        event.getDrops().remove(heirloom);

        ItemMeta meta = heirloom.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        int generation = pdc.getOrDefault(generationKey, PersistentDataType.INTEGER, 1) + 1;
        String lineage = pdc.getOrDefault(lineageKey, PersistentDataType.STRING, dead.getName());
        String heirName = heirNames.getOrDefault(heirId, "?");
        heirloom.setItemMeta(meta);
        writeTag(heirloom, heirId, generation, lineage + "," + heirName);

        Player heir = Bukkit.getPlayer(heirId);
        if (heir != null) {
            deliver(heir, heirloom, dead.getName(), generation);
        } else {
            storePending(heirId, heirloom, dead.getName(), generation);
            save();
        }
    }

    private void deliver(Player heir, ItemStack heirloom, String fromName, int generation) {
        heir.getInventory().addItem(heirloom).values()
            .forEach(left -> heir.getWorld().dropItemNaturally(heir.getLocation(), left));
        heir.playSound(heir.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 0.8f);
        heir.sendMessage(PREFIX.append(Component.text(
            "You have inherited " + heirloom.getType().name().toLowerCase().replace('_', ' ')
                + " from " + fromName + " (Generation " + generation + "). Guard it well.",
            NamedTextColor.GOLD)));
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        if (warnNoHeir.remove(event.getPlayer().getUniqueId())) {
            event.getPlayer().sendMessage(PREFIX.append(Component.text(
                "Your heirloom dropped with your death — you had no heir set. /heirloom heir <player>",
                NamedTextColor.RED)));
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        heirNames.put(player.getUniqueId(), player.getName());
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(vaultFile);
        String base = player.getUniqueId().toString();
        ItemStack pending = yml.getItemStack(base + ".item");
        if (pending == null) return;
        String from = yml.getString(base + ".from", "?");
        int generation = yml.getInt(base + ".generation", 1);
        yml.set(base, null);
        try {
            yml.save(vaultFile);
        } catch (IOException ignored) {}
        getServer().getScheduler().runTaskLater(this, () -> {
            if (player.isOnline()) deliver(player, pending, from, generation);
        }, 40L);
    }

    private void storePending(UUID heirId, ItemStack heirloom, String fromName, int generation) {
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(vaultFile);
        String base = heirId.toString();
        yml.set(base + ".item", heirloom);
        yml.set(base + ".from", fromName);
        yml.set(base + ".generation", generation);
        try {
            if (!getDataFolder().exists()) getDataFolder().mkdirs();
            yml.save(vaultFile);
        } catch (IOException e) {
            getLogger().warning("Could not save vault.yml: " + e.getMessage());
        }
    }

    // ---- persistence (heirs) ----

    private File dataFile() {
        return new File(getDataFolder(), "data.yml");
    }

    private void load() {
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(dataFile());
        ConfigurationSection hs = yml.getConfigurationSection("heirs");
        if (hs != null) {
            for (String key : hs.getKeys(false)) {
                try {
                    heirs.put(UUID.fromString(key), UUID.fromString(hs.getString(key, "")));
                } catch (IllegalArgumentException ignored) {}
            }
        }
        ConfigurationSection ns = yml.getConfigurationSection("names");
        if (ns != null) {
            for (String key : ns.getKeys(false)) {
                try {
                    heirNames.put(UUID.fromString(key), ns.getString(key));
                } catch (IllegalArgumentException ignored) {}
            }
        }
    }

    private void save() {
        YamlConfiguration yml = new YamlConfiguration();
        for (Map.Entry<UUID, UUID> e : heirs.entrySet()) {
            yml.set("heirs." + e.getKey(), e.getValue().toString());
        }
        for (Map.Entry<UUID, String> e : heirNames.entrySet()) {
            yml.set("names." + e.getKey(), e.getValue());
        }
        try {
            if (!getDataFolder().exists()) getDataFolder().mkdirs();
            yml.save(dataFile());
        } catch (IOException ex) {
            getLogger().warning("Could not save data.yml: " + ex.getMessage());
        }
    }
}
