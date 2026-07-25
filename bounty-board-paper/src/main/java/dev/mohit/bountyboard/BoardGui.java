package dev.mohit.bountyboard;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/** The 27-slot Bounty Board menu. */
public class BoardGui implements InventoryHolder {

    private static final int[] BOUNTY_SLOTS = {11, 13, 15};
    private static final int SLOT_CLAIM = 11;
    private static final int SLOT_ACTIVE = 13;
    private static final int SLOT_ABANDON = 15;

    private final BountyManager manager;
    private final Inventory inv;
    private List<Bounty> board = List.of();
    private boolean hasActive;

    public BoardGui(BountyManager manager, Player player) {
        this.manager = manager;
        long day = manager.currentDay();
        this.inv = Bukkit.createInventory(this, 27,
            Component.text("Bounty Board — Day " + day, NamedTextColor.DARK_GREEN));
        render(player);
    }

    @Override
    public Inventory getInventory() {
        return inv;
    }

    public void open(Player player) {
        player.openInventory(inv);
    }

    private void render(Player player) {
        inv.clear();
        ItemStack filler = named(new ItemStack(Material.GRAY_STAINED_GLASS_PANE), Component.text(" "), List.of());
        for (int i = 0; i < 27; i++) inv.setItem(i, filler);

        long day = manager.currentDay();
        BountyManager.PlayerData d = manager.refresh(player);
        hasActive = d.active != null;

        List<Component> footer = new ArrayList<>();
        footer.add(line("Completed: " + d.completed, NamedTextColor.GRAY));
        if (d.streak > 1) {
            footer.add(line("Streak: " + d.streak + " days (+"
                + Math.round((BountyManager.streakMultiplier(d.streak) - 1) * 100) + "% emeralds)", NamedTextColor.GOLD));
        }
        inv.setItem(22, named(new ItemStack(Material.BOOK),
            Component.text("Your stats", NamedTextColor.YELLOW), footer));

        if (hasActive) {
            Bounty b = d.active;
            int shown = manager.shownCount(player, b);
            boolean done = shown >= b.goal;

            List<Component> lore = new ArrayList<>();
            lore.add(line(b.describe(), NamedTextColor.WHITE));
            lore.add(line("Progress: " + shown + "/" + b.goal, done ? NamedTextColor.GREEN : NamedTextColor.YELLOW));
            lore.add(line(done ? "Ready to claim!" : "Expires at the next sunrise.", NamedTextColor.GRAY));
            inv.setItem(SLOT_ACTIVE, named(icon(b), title(b), lore));

            if (done) {
                inv.setItem(SLOT_CLAIM, named(new ItemStack(Material.EMERALD_BLOCK),
                    Component.text("Claim Reward", NamedTextColor.GREEN, TextDecoration.BOLD),
                    List.of(line(rewardText(b), NamedTextColor.GRAY))));
            }
            inv.setItem(SLOT_ABANDON, named(new ItemStack(Material.BARRIER),
                Component.text("Abandon Bounty", NamedTextColor.RED),
                List.of(line("Progress will be lost.", NamedTextColor.GRAY))));
        } else {
            board = manager.dailyBounties(day);
            for (int i = 0; i < board.size(); i++) {
                Bounty b = board.get(i);
                List<Component> lore = new ArrayList<>();
                lore.add(line(b.describe(), NamedTextColor.WHITE));
                lore.add(line("Reward: " + rewardText(b), NamedTextColor.GRAY));
                lore.add(line("Click to accept.", NamedTextColor.YELLOW));
                inv.setItem(BOUNTY_SLOTS[i], named(icon(b), title(b), lore));
            }
        }
    }

    public void onClick(Player player, int slot) {
        BountyManager.PlayerData d = manager.data(player);
        if (hasActive) {
            if (slot == SLOT_CLAIM) {
                if (manager.claim(player)) player.closeInventory();
            } else if (slot == SLOT_ABANDON) {
                d.active = null;
                manager.save();
                player.sendMessage(BountyManager.PREFIX
                    .append(Component.text("Bounty abandoned.", NamedTextColor.GRAY)));
                player.closeInventory();
            }
        } else {
            for (int i = 0; i < board.size(); i++) {
                if (BOUNTY_SLOTS[i] == slot) {
                    Bounty b = board.get(i);
                    b.count = 0;
                    d.active = b;
                    manager.save();
                    player.sendMessage(BountyManager.PREFIX
                        .append(Component.text("Accepted: " + b.describe() + " (" + b.tier.label
                            + " — " + rewardText(b) + ")", NamedTextColor.WHITE)));
                    player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1f, 1f);
                    player.closeInventory();
                    return;
                }
            }
        }
    }

    private static String rewardText(Bounty b) {
        String s = b.tier.emeralds + " emeralds";
        if (b.tier.diamonds > 0) s += ", " + b.tier.diamonds + " diamonds";
        return s + ", " + b.tier.xpLevels + " XP levels";
    }

    private static Component title(Bounty b) {
        NamedTextColor color = switch (b.tier) {
            case COMMON -> NamedTextColor.WHITE;
            case RARE -> NamedTextColor.AQUA;
            case EPIC -> NamedTextColor.LIGHT_PURPLE;
        };
        return Component.text(b.tier.label + " Bounty", color, TextDecoration.BOLD)
            .decoration(TextDecoration.ITALIC, false);
    }

    private static ItemStack icon(Bounty b) {
        Material mat = switch (b.type) {
            case HUNT -> {
                Material egg = Material.matchMaterial(b.target + "_SPAWN_EGG");
                yield egg != null ? egg : Material.IRON_SWORD;
            }
            case MINE, DELIVER -> {
                Material m = Material.matchMaterial(b.target);
                yield m != null ? m : Material.PAPER;
            }
        };
        return new ItemStack(mat);
    }

    private static Component line(String text, NamedTextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }

    private static ItemStack named(ItemStack item, Component name, List<Component> lore) {
        ItemMeta meta = item.getItemMeta();
        meta.displayName(name.decoration(TextDecoration.ITALIC, false));
        if (!lore.isEmpty()) meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }
}
