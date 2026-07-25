package dev.mohit.thecollector;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class CollectorPlugin extends JavaPlugin implements Listener {

    private static final Component PREFIX = Component.text("[The Collector] ", NamedTextColor.DARK_PURPLE);

    private record Request(Material item, int amount, String flavor, int reward) {}

    private static final List<Request> POOL = List.of(
        new Request(Material.POISONOUS_POTATO, 3, "Three poisonous potatoes. Do not ask why.", 12),
        new Request(Material.DEAD_BUSH, 5, "Five dead bushes. For my... garden.", 10),
        new Request(Material.ROTTEN_FLESH, 32, "A generous helping of rotten flesh. It's for a stew.", 8),
        new Request(Material.EGG, 16, "Sixteen eggs. Unbroken. UNBROKEN.", 8),
        new Request(Material.CACTUS, 7, "Seven cacti. I lost a bet.", 10),
        new Request(Material.SPIDER_EYE, 12, "A dozen spider eyes. They see things.", 10),
        new Request(Material.LILY_PAD, 9, "Nine lily pads, still damp.", 10),
        new Request(Material.BONE, 24, "Twenty-four bones. My dog is... large.", 8),
        new Request(Material.PUFFERFISH, 2, "Two pufferfish. Inflated, preferably.", 14),
        new Request(Material.OBSIDIAN, 10, "Ten obsidian. My mantelpiece is boring.", 16),
        new Request(Material.SNOWBALL, 16, "Sixteen snowballs, unmelted. Time is a factor.", 10),
        new Request(Material.FLINT, 20, "Twenty pieces of flint. Sharp ones.", 10),
        new Request(Material.INK_SAC, 8, "Eight ink sacs. My memoirs won't write themselves.", 10),
        new Request(Material.SLIME_BALL, 6, "Six slime balls. Do not squeeze them.", 14),
        new Request(Material.NAUTILUS_SHELL, 1, "A single nautilus shell. It hums, you know.", 20),
        new Request(Material.CAKE, 1, "A whole cake. Untouched. I will know.", 16),
        new Request(Material.BLUE_ORCHID, 4, "Four blue orchids for a melancholy bouquet.", 12),
        new Request(Material.GOAT_HORN, 1, "A goat horn. The louder, the better.", 24),
        new Request(Material.HONEY_BOTTLE, 3, "Three bottles of honey. Sticky business.", 14),
        new Request(Material.MUSIC_DISC_13, 1, "That disc. The unsettling one. Number thirteen.", 30)
    );

    private long fulfilledDay = -1;
    private long salt;

    @Override
    public void onEnable() {
        getConfig().addDefault("salt", new Random().nextInt(Integer.MAX_VALUE));
        getConfig().options().copyDefaults(true);
        saveConfig();
        salt = getConfig().getLong("salt");
        load();
        getServer().getPluginManager().registerEvents(this, this);

        // Announce the day's request when it changes
        getServer().getScheduler().runTaskTimer(this, new Runnable() {
            long announcedDay = -1;

            @Override
            public void run() {
                long today = currentDay();
                if (today != announcedDay) {
                    announcedDay = today;
                    if (fulfilledDay != today) {
                        Request request = requestFor(today);
                        getServer().broadcast(PREFIX.append(Component.text(
                            "\"" + request.flavor() + "\" — first to /collector deliver wins "
                                + request.reward() + " emeralds.", NamedTextColor.LIGHT_PURPLE)));
                    }
                }
            }
        }, 100L, 100L);
    }

    @Override
    public void onDisable() {
        save();
    }

    private long currentDay() {
        return Bukkit.getWorlds().get(0).getFullTime() / 24000L;
    }

    private Request requestFor(long day) {
        Random rand = new Random(day * 2654435761L + salt);
        return POOL.get(rand.nextInt(POOL.size()));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        String sub = args.length == 0 ? "show" : args[0].toLowerCase();
        switch (sub) {
            case "deliver" -> deliver(player);
            case "log" -> log(player);
            default -> show(player);
        }
        return true;
    }

    private void show(Player player) {
        long today = currentDay();
        Request request = requestFor(today);
        if (fulfilledDay == today) {
            player.sendMessage(PREFIX.append(Component.text(
                "Today's request has been fulfilled. The Collector is content... for now.",
                NamedTextColor.GRAY)));
            return;
        }
        player.sendMessage(PREFIX.append(Component.text("\"" + request.flavor() + "\"", NamedTextColor.LIGHT_PURPLE)));
        player.sendMessage(Component.text(
            "  Wanted: " + request.amount() + "x " + request.item().name().toLowerCase().replace('_', ' ')
                + " — reward " + request.reward() + " emeralds. Hold them and /collector deliver.",
            NamedTextColor.GRAY));
    }

    private void deliver(Player player) {
        long today = currentDay();
        if (fulfilledDay == today) {
            player.sendMessage(PREFIX.append(Component.text(
                "Too slow — today's request is already fulfilled.", NamedTextColor.RED)));
            return;
        }
        Request request = requestFor(today);
        if (!player.getInventory().containsAtLeast(new ItemStack(request.item()), request.amount())) {
            player.sendMessage(PREFIX.append(Component.text(
                "The Collector wants " + request.amount() + "x "
                    + request.item().name().toLowerCase().replace('_', ' ') + ". You don't have it all.",
                NamedTextColor.RED)));
            return;
        }
        player.getInventory().removeItem(new ItemStack(request.item(), request.amount()));
        fulfilledDay = today;
        appendLog(today, player.getName(), request);
        save();
        player.getInventory().addItem(new ItemStack(Material.EMERALD, request.reward())).values()
            .forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 0.9f);
        getServer().broadcast(PREFIX.append(Component.text(
            player.getName() + " delivered " + request.amount() + "x "
                + request.item().name().toLowerCase().replace('_', ' ')
                + "! \"Ah. Exquisite.\" (+" + request.reward() + " emeralds)", NamedTextColor.GOLD)));
    }

    private void log(Player player) {
        List<String> entries = YamlConfiguration.loadConfiguration(dataFile()).getStringList("log");
        if (entries.isEmpty()) {
            player.sendMessage(PREFIX.append(Component.text("The ledger is empty... so far.", NamedTextColor.GRAY)));
            return;
        }
        player.sendMessage(PREFIX.append(Component.text("The Collector's ledger:", NamedTextColor.LIGHT_PURPLE)));
        int from = Math.max(0, entries.size() - 10);
        for (int i = from; i < entries.size(); i++) {
            player.sendMessage(Component.text("  " + entries.get(i), NamedTextColor.GRAY));
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        getServer().getScheduler().runTaskLater(this, () -> {
            if (event.getPlayer().isOnline() && fulfilledDay != currentDay()) show(event.getPlayer());
        }, 60L);
    }

    // ---- persistence ----

    private File dataFile() {
        return new File(getDataFolder(), "data.yml");
    }

    private void appendLog(long day, String name, Request request) {
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(dataFile());
        List<String> entries = new ArrayList<>(yml.getStringList("log"));
        entries.add("Day " + day + ": " + name + " — " + request.amount() + "x "
            + request.item().name().toLowerCase().replace('_', ' '));
        yml.set("log", entries);
        yml.set("fulfilledDay", day);
        try {
            if (!getDataFolder().exists()) getDataFolder().mkdirs();
            yml.save(dataFile());
        } catch (IOException e) {
            getLogger().warning("Could not save data.yml: " + e.getMessage());
        }
    }

    private void load() {
        fulfilledDay = YamlConfiguration.loadConfiguration(dataFile()).getLong("fulfilledDay", -1);
    }

    private void save() {
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(dataFile());
        yml.set("fulfilledDay", fulfilledDay);
        try {
            if (!getDataFolder().exists()) getDataFolder().mkdirs();
            yml.save(dataFile());
        } catch (IOException e) {
            getLogger().warning("Could not save data.yml: " + e.getMessage());
        }
    }
}
