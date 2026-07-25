package dev.mohit.seasonpass;

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

/** 54-slot pass GUI: tiers 1-20 in slots 0-19, stats at 49. */
public class PassGui implements InventoryHolder {

    private final SeasonPassPlugin plugin;
    private final Inventory inv;

    public PassGui(SeasonPassPlugin plugin, Player player) {
        this.plugin = plugin;
        this.inv = Bukkit.createInventory(this, 54,
            Component.text("Season Pass", NamedTextColor.DARK_PURPLE));
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
        PassData d = plugin.data(player);
        int tier = d.tier();

        for (Rewards.Reward reward : Rewards.TRACK) {
            boolean reached = tier >= reward.tier();
            boolean equipped = reached && reward.key().equals(switch (reward.kind()) {
                case COLOR -> d.color;
                case TRAIL -> d.trail;
                case TITLE -> d.title;
            });
            ItemStack item = new ItemStack(reached ? reward.icon() : Material.GRAY_STAINED_GLASS_PANE);
            ItemMeta meta = item.getItemMeta();
            meta.displayName(Component.text("Tier " + reward.tier() + " — " + reward.label(),
                    reached ? (equipped ? NamedTextColor.GREEN : NamedTextColor.YELLOW) : NamedTextColor.DARK_GRAY)
                .decoration(TextDecoration.ITALIC, false));
            List<Component> lore = new ArrayList<>();
            if (reached) {
                lore.add(Component.text(equipped ? "Equipped — click to unequip" : "Click to equip",
                    NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
            } else {
                lore.add(Component.text("Unlocks at " + PassData.cumulativeFor(reward.tier()) + " XP",
                    NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
            }
            meta.lore(lore);
            item.setItemMeta(meta);
            inv.setItem(reward.tier() - 1, item);
        }

        ItemStack stats = new ItemStack(Material.EXPERIENCE_BOTTLE);
        ItemMeta meta = stats.getItemMeta();
        meta.displayName(Component.text("Your pass", NamedTextColor.AQUA)
            .decoration(TextDecoration.ITALIC, false));
        int next = tier < 20 ? PassData.cumulativeFor(tier + 1) : 0;
        meta.lore(List.of(
            Component.text("Tier " + tier + " / 20 — " + d.xp + " XP", NamedTextColor.WHITE)
                .decoration(TextDecoration.ITALIC, false),
            Component.text(tier < 20 ? (next - d.xp) + " XP to next tier" : "Track complete!",
                NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
            Component.text("XP: kills +5, mining +1, fishing +10,", NamedTextColor.DARK_GRAY)
                .decoration(TextDecoration.ITALIC, false),
            Component.text("advancements +25, playtime +2/min", NamedTextColor.DARK_GRAY)
                .decoration(TextDecoration.ITALIC, false)));
        stats.setItemMeta(meta);
        inv.setItem(49, stats);
    }

    public void onClick(Player player, int slot) {
        if (slot < 0 || slot >= 20) return;
        PassData d = plugin.data(player);
        Rewards.Reward reward = Rewards.TRACK[slot];
        if (d.tier() < reward.tier()) return;

        switch (reward.kind()) {
            case COLOR -> d.color = reward.key().equals(d.color) ? null : reward.key();
            case TRAIL -> d.trail = reward.key().equals(d.trail) ? null : reward.key();
            case TITLE -> d.title = reward.key().equals(d.title) ? null : reward.key();
        }
        plugin.applyNameCosmetics(player);
        plugin.saveData();
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.2f);
        render(player);
    }
}
