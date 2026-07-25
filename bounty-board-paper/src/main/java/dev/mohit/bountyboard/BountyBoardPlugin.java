package dev.mohit.bountyboard;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class BountyBoardPlugin extends JavaPlugin implements Listener {

    private BountyManager manager;

    @Override
    public void onEnable() {
        manager = new BountyManager(this);
        getServer().getPluginManager().registerEvents(this, this);

        // Deliver bounties: poll inventories so players get a "ready to turn in" nudge.
        getServer().getScheduler().runTaskTimer(this, () -> {
            for (Player player : getServer().getOnlinePlayers()) {
                BountyManager.PlayerData d = manager.data(player);
                Bounty b = d.active;
                if (b == null || b.type != Bounty.Type.DELIVER) continue;
                int have = manager.shownCount(player, b);
                if (have != b.count) {
                    boolean wasDone = b.count >= b.goal;
                    b.count = have;
                    if (have >= b.goal && !wasDone) {
                        player.sendMessage(BountyManager.PREFIX.append(Component.text(
                            "You have everything for: " + b.describe() + ". Run /bounty to turn it in!",
                            NamedTextColor.GREEN)));
                        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
                    }
                }
            }
        }, 100L, 100L);
    }

    @Override
    public void onDisable() {
        if (manager != null) manager.save();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        new BoardGui(manager, player).open(player);
        return true;
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null) return;
        manager.bump(killer, Bounty.Type.HUNT, event.getEntityType().name());
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Material type = event.getBlock().getType();
        // Deepslate variants count toward the ore bounty.
        String name = type.name().replace("DEEPSLATE_", "");
        manager.bump(event.getPlayer(), Bounty.Type.MINE, name);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof BoardGui gui)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getRawSlot() < 0 || event.getRawSlot() >= 27) return;
        gui.onClick(player, event.getRawSlot());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        BountyManager.PlayerData d = manager.data(player);
        if (d.welcomed) return;
        d.welcomed = true;
        manager.save();
        getServer().getScheduler().runTaskLater(this, () ->
            player.sendMessage(BountyManager.PREFIX.append(Component.text(
                "Welcome! Run /bounty to open the Bounty Board. Three new bounties every day — build a streak for bonus emeralds.",
                NamedTextColor.YELLOW))), 40L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        manager.save();
    }
}
