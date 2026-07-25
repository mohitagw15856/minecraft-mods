package dev.mohit.contracts;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.generator.structure.Structure;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.StructureSearchResult;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ContractsPlugin extends JavaPlugin {

    private static final Component PREFIX = Component.text("[Contracts] ", NamedTextColor.DARK_AQUA);

    private record ContractType(String key, Structure structure, int basePrice, int reward) {}

    private static final List<ContractType> TYPES = List.of(
        new ContractType("VILLAGE", Structure.VILLAGE_PLAINS, 8, 12),
        new ContractType("SHIPWRECK", Structure.SHIPWRECK, 10, 16),
        new ContractType("PILLAGER_OUTPOST", Structure.PILLAGER_OUTPOST, 14, 24),
        new ContractType("OCEAN_RUIN", Structure.OCEAN_RUIN_COLD, 10, 16),
        new ContractType("DESERT_PYRAMID", Structure.DESERT_PYRAMID, 12, 20),
        new ContractType("WOODLAND_MANSION", Structure.MANSION, 32, 64)
    );

    private static class ActiveContract {
        String type;
        String world;
        int x, z;
        int reward;
    }

    private final Map<UUID, ActiveContract> active = new HashMap<>();
    private final Map<UUID, Map<String, Integer>> purchases = new HashMap<>();
    private NamespacedKey paperKey;

    @Override
    public void onEnable() {
        paperKey = new NamespacedKey(this, "contract-paper");
        load();
        getServer().getScheduler().runTaskTimer(this, this::checkArrivals, 100L, 100L);
    }

    @Override
    public void onDisable() {
        save();
    }

    private ContractType typeByKey(String key) {
        return TYPES.stream().filter(t -> t.key().equalsIgnoreCase(key)).findFirst().orElse(null);
    }

    private int priceFor(Player player, ContractType type) {
        int bought = purchases.getOrDefault(player.getUniqueId(), Map.of()).getOrDefault(type.key(), 0);
        return Math.min(type.basePrice() * (1 + bought), type.basePrice() * 4);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        String sub = args.length == 0 ? "list" : args[0].toLowerCase();
        switch (sub) {
            case "list" -> list(player);
            case "buy" -> {
                if (args.length < 2) {
                    player.sendMessage(PREFIX.append(Component.text("Usage: /contract buy <type>", NamedTextColor.RED)));
                } else {
                    buy(player, args[1]);
                }
            }
            case "status" -> status(player);
            case "cancel" -> cancel(player);
            default -> player.sendMessage(PREFIX.append(Component.text(
                "/contract <list|buy|status|cancel>", NamedTextColor.YELLOW)));
        }
        return true;
    }

    private void list(Player player) {
        player.sendMessage(PREFIX.append(Component.text("Available contracts (price → reward, in emeralds):",
            NamedTextColor.YELLOW)));
        for (ContractType type : TYPES) {
            player.sendMessage(Component.text("  " + type.key() + " — " + priceFor(player, type)
                + " → " + type.reward(), NamedTextColor.GRAY));
        }
        player.sendMessage(Component.text("Prices rise each time you buy a type (up to 4x).", NamedTextColor.DARK_GRAY));
    }

    private void buy(Player player, String key) {
        if (active.containsKey(player.getUniqueId())) {
            player.sendMessage(PREFIX.append(Component.text(
                "You already have an active contract. /contract status", NamedTextColor.RED)));
            return;
        }
        ContractType type = typeByKey(key);
        if (type == null) {
            player.sendMessage(PREFIX.append(Component.text("Unknown type. /contract list", NamedTextColor.RED)));
            return;
        }
        int price = priceFor(player, type);
        if (!player.getInventory().containsAtLeast(new ItemStack(Material.EMERALD), price)) {
            player.sendMessage(PREFIX.append(Component.text(
                "You need " + price + " emeralds in your inventory.", NamedTextColor.RED)));
            return;
        }
        player.sendMessage(PREFIX.append(Component.text("Consulting the charts...", NamedTextColor.GRAY)));
        StructureSearchResult result;
        try {
            result = player.getWorld().locateNearestStructure(player.getLocation(), type.structure(), 3000, true);
        } catch (Exception e) {
            result = null;
        }
        if (result == null) {
            player.sendMessage(PREFIX.append(Component.text(
                "No " + type.key() + " could be charted from here. No charge.", NamedTextColor.RED)));
            return;
        }
        player.getInventory().removeItem(new ItemStack(Material.EMERALD, price));

        Location target = result.getLocation();
        ActiveContract contract = new ActiveContract();
        contract.type = type.key();
        contract.world = player.getWorld().getName();
        contract.x = target.getBlockX();
        contract.z = target.getBlockZ();
        contract.reward = type.reward();
        active.put(player.getUniqueId(), contract);
        purchases.computeIfAbsent(player.getUniqueId(), id -> new HashMap<>())
            .merge(type.key(), 1, Integer::sum);
        save();

        ItemStack paper = new ItemStack(Material.PAPER);
        ItemMeta meta = paper.getItemMeta();
        meta.displayName(Component.text("Contract: " + type.key(), NamedTextColor.DARK_AQUA)
            .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
            Component.text(distanceLine(player, contract), NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false),
            Component.text("Reward: " + type.reward() + " emeralds", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false)));
        meta.getPersistentDataContainer().set(paperKey, PersistentDataType.BYTE, (byte) 1);
        paper.setItemMeta(meta);
        player.getInventory().addItem(paper).values()
            .forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));

        player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1f, 1f);
        player.sendMessage(PREFIX.append(Component.text(
            "Contract signed! Head to ~" + contract.x + ", ~" + contract.z + " ("
                + distanceLine(player, contract) + "). Get within 48 blocks to collect "
                + type.reward() + " emeralds.", NamedTextColor.GREEN)));
    }

    private String distanceLine(Player player, ActiveContract contract) {
        double dx = contract.x - player.getLocation().getX();
        double dz = contract.z - player.getLocation().getZ();
        int dist = (int) Math.sqrt(dx * dx + dz * dz);
        String ns = dz < 0 ? "north" : "south";
        String ew = dx < 0 ? "west" : "east";
        return dist + " blocks to the " + (Math.abs(dz) > Math.abs(dx) ? ns : ew);
    }

    private void status(Player player) {
        ActiveContract contract = active.get(player.getUniqueId());
        if (contract == null) {
            player.sendMessage(PREFIX.append(Component.text("No active contract. /contract list", NamedTextColor.GRAY)));
            return;
        }
        if (!player.getWorld().getName().equals(contract.world)) {
            player.sendMessage(PREFIX.append(Component.text(
                "Your " + contract.type + " contract is in " + contract.world + ".", NamedTextColor.YELLOW)));
            return;
        }
        player.sendMessage(PREFIX.append(Component.text(
            contract.type + ": " + distanceLine(player, contract) + " (~" + contract.x + ", ~" + contract.z + ")",
            NamedTextColor.YELLOW)));
    }

    private void cancel(Player player) {
        if (active.remove(player.getUniqueId()) != null) {
            save();
            player.sendMessage(PREFIX.append(Component.text("Contract abandoned (no refund).", NamedTextColor.GRAY)));
        } else {
            player.sendMessage(PREFIX.append(Component.text("No active contract.", NamedTextColor.GRAY)));
        }
    }

    private void checkArrivals() {
        for (Player player : getServer().getOnlinePlayers()) {
            ActiveContract contract = active.get(player.getUniqueId());
            if (contract == null || !player.getWorld().getName().equals(contract.world)) continue;
            double dx = contract.x - player.getLocation().getX();
            double dz = contract.z - player.getLocation().getZ();
            if (dx * dx + dz * dz > 48 * 48) continue;

            active.remove(player.getUniqueId());
            ItemStack reward = new ItemStack(Material.EMERALD, contract.reward);
            player.getInventory().addItem(reward).values()
                .forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
            // Remove one contract paper if present
            for (ItemStack item : player.getInventory().getContents()) {
                if (item != null && item.hasItemMeta()
                    && item.getItemMeta().getPersistentDataContainer().has(paperKey, PersistentDataType.BYTE)) {
                    item.setAmount(item.getAmount() - 1);
                    break;
                }
            }
            save();
            player.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING,
                player.getLocation().add(0, 1, 0), 40, 0.5, 0.8, 0.5, 0.1);
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
            getServer().broadcast(PREFIX.append(Component.text(
                player.getName() + " fulfilled a " + contract.type + " contract! (+"
                    + contract.reward + " emeralds)", NamedTextColor.GOLD)));
        }
    }

    // ---- persistence ----

    private File dataFile() {
        return new File(getDataFolder(), "data.yml");
    }

    private void load() {
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(dataFile());
        ConfigurationSection as = yml.getConfigurationSection("active");
        if (as != null) {
            for (String key : as.getKeys(false)) {
                ConfigurationSection s = as.getConfigurationSection(key);
                if (s == null) continue;
                try {
                    ActiveContract contract = new ActiveContract();
                    contract.type = s.getString("type");
                    contract.world = s.getString("world");
                    contract.x = s.getInt("x");
                    contract.z = s.getInt("z");
                    contract.reward = s.getInt("reward");
                    active.put(UUID.fromString(key), contract);
                } catch (IllegalArgumentException ignored) {}
            }
        }
        ConfigurationSection ps = yml.getConfigurationSection("purchases");
        if (ps != null) {
            for (String key : ps.getKeys(false)) {
                ConfigurationSection s = ps.getConfigurationSection(key);
                if (s == null) continue;
                try {
                    Map<String, Integer> counts = new LinkedHashMap<>();
                    for (String type : s.getKeys(false)) counts.put(type, s.getInt(type));
                    purchases.put(UUID.fromString(key), counts);
                } catch (IllegalArgumentException ignored) {}
            }
        }
    }

    private void save() {
        YamlConfiguration yml = new YamlConfiguration();
        for (Map.Entry<UUID, ActiveContract> e : active.entrySet()) {
            String base = "active." + e.getKey();
            yml.set(base + ".type", e.getValue().type);
            yml.set(base + ".world", e.getValue().world);
            yml.set(base + ".x", e.getValue().x);
            yml.set(base + ".z", e.getValue().z);
            yml.set(base + ".reward", e.getValue().reward);
        }
        for (Map.Entry<UUID, Map<String, Integer>> e : purchases.entrySet()) {
            for (Map.Entry<String, Integer> c : e.getValue().entrySet()) {
                yml.set("purchases." + e.getKey() + "." + c.getKey(), c.getValue());
            }
        }
        try {
            if (!getDataFolder().exists()) getDataFolder().mkdirs();
            yml.save(dataFile());
        } catch (IOException ex) {
            getLogger().warning("Could not save data.yml: " + ex.getMessage());
        }
    }
}
