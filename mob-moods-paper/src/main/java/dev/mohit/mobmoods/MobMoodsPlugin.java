package dev.mohit.mobmoods;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ExplosionPrimeEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.EnumMap;
import java.util.Map;
import java.util.Random;
import java.util.Set;

public final class MobMoodsPlugin extends JavaPlugin implements Listener {

    private enum Mood { CHEERFUL, NEUTRAL, GRUMPY }

    private static final Component PREFIX = Component.text("[MobMoods] ", NamedTextColor.GREEN);
    private static final Set<EntityType> MOODY_TYPES =
        Set.of(EntityType.ZOMBIE, EntityType.SKELETON, EntityType.CREEPER, EntityType.SPIDER);

    private final Random random = new Random();
    private NamespacedKey moodKey;
    private boolean enabled;

    @Override
    public void onEnable() {
        moodKey = new NamespacedKey(this, "mood");
        getConfig().addDefault("enabled", true);
        getConfig().options().copyDefaults(true);
        saveConfig();
        enabled = getConfig().getBoolean("enabled");
        getServer().getPluginManager().registerEvents(this, this);

        // Mood particles near players + grumpy speed refresh
        getServer().getScheduler().runTaskTimer(this, () -> {
            if (!enabled) return;
            for (Player player : getServer().getOnlinePlayers()) {
                for (Entity entity : player.getNearbyEntities(24, 12, 24)) {
                    Mood mood = moodOf(entity);
                    if (mood == null || mood == Mood.NEUTRAL) continue;
                    entity.getWorld().spawnParticle(
                        mood == Mood.CHEERFUL ? Particle.HAPPY_VILLAGER : Particle.SMOKE,
                        entity.getLocation().add(0, entity.getHeight() + 0.3, 0), 3, 0.2, 0.1, 0.2, 0.01);
                    if (mood == Mood.GRUMPY && entity instanceof LivingEntity living
                        && (entity.getType() == EntityType.ZOMBIE || entity.getType() == EntityType.SPIDER)) {
                        living.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 260, 0, true, false, false));
                    }
                }
            }
        }, 60L, 60L);
    }

    private Mood moodOf(Entity entity) {
        String value = entity.getPersistentDataContainer().get(moodKey, PersistentDataType.STRING);
        if (value == null) return null;
        try {
            return Mood.valueOf(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private void setMood(Entity entity, Mood mood) {
        entity.getPersistentDataContainer().set(moodKey, PersistentDataType.STRING, mood.name());
    }

    @EventHandler
    public void onSpawn(CreatureSpawnEvent event) {
        if (!enabled || !MOODY_TYPES.contains(event.getEntityType())) return;
        int roll = random.nextInt(100);
        Mood mood = roll < 25 ? Mood.CHEERFUL : roll < 75 ? Mood.NEUTRAL : Mood.GRUMPY;
        setMood(event.getEntity(), mood);
        if (mood == Mood.GRUMPY && (event.getEntityType() == EntityType.ZOMBIE
            || event.getEntityType() == EntityType.SPIDER)) {
            event.getEntity().addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 260, 0, true, false, false));
        }
    }

    @EventHandler
    public void onPrime(ExplosionPrimeEvent event) {
        if (enabled && event.getEntity() instanceof Creeper && moodOf(event.getEntity()) == Mood.CHEERFUL) {
            event.setCancelled(true);
            event.getEntity().getWorld().spawnParticle(Particle.HEART,
                event.getEntity().getLocation().add(0, 1.2, 0), 5, 0.3, 0.3, 0.3);
        }
    }

    @EventHandler
    public void onExplode(EntityExplodeEvent event) {
        if (enabled && event.getEntity() instanceof Creeper && moodOf(event.getEntity()) == Mood.CHEERFUL) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onShoot(EntityShootBowEvent event) {
        if (!enabled || event.getEntity().getType() != EntityType.SKELETON) return;
        if (moodOf(event.getEntity()) == Mood.CHEERFUL && random.nextBoolean()) {
            event.setCancelled(true);
            event.getEntity().getWorld().spawnParticle(Particle.NOTE,
                event.getEntity().getLocation().add(0, 2.2, 0), 3, 0.2, 0.2, 0.2);
        }
    }

    @EventHandler
    public void onDamage(EntityDamageByEntityEvent event) {
        if (enabled && moodOf(event.getDamager()) == Mood.GRUMPY) {
            event.setDamage(event.getDamage() + 1.0);
        }
    }

    @EventHandler
    public void onFeed(PlayerInteractEntityEvent event) {
        if (!enabled || event.getHand() != EquipmentSlot.HAND) return;
        Entity entity = event.getRightClicked();
        Mood mood = moodOf(entity);
        if (mood == null) return;
        Player player = event.getPlayer();
        ItemStack held = player.getInventory().getItemInMainHand();
        if (held.getType() != Material.SWEET_BERRIES) return;
        event.setCancelled(true);
        if (mood == Mood.CHEERFUL) {
            player.sendMessage(PREFIX.append(Component.text("It's already as happy as can be.", NamedTextColor.GRAY)));
            return;
        }
        held.setAmount(held.getAmount() - 1);
        Mood next = mood == Mood.GRUMPY ? Mood.NEUTRAL : Mood.CHEERFUL;
        setMood(entity, next);
        if (entity instanceof LivingEntity living) {
            living.removePotionEffect(PotionEffectType.SPEED);
        }
        entity.getWorld().spawnParticle(Particle.HEART, entity.getLocation().add(0, 1.2, 0), 8, 0.3, 0.3, 0.3);
        entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_GENERIC_EAT, 1f, 1.2f);
        player.sendMessage(PREFIX.append(Component.text(
            "The " + entity.getType().name().toLowerCase().replace('_', ' ') + " is now "
                + next.name().toLowerCase() + ".", NamedTextColor.GREEN)));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0 && (args[0].equalsIgnoreCase("on") || args[0].equalsIgnoreCase("off"))) {
            if (!sender.isOp()) {
                sender.sendMessage(PREFIX.append(Component.text("Op only.", NamedTextColor.RED)));
                return true;
            }
            enabled = args[0].equalsIgnoreCase("on");
            getConfig().set("enabled", enabled);
            saveConfig();
            sender.sendMessage(PREFIX.append(Component.text("Mob moods " + (enabled ? "enabled." : "disabled."),
                NamedTextColor.YELLOW)));
            return true;
        }
        Map<Mood, Integer> counts = new EnumMap<>(Mood.class);
        for (World world : getServer().getWorlds()) {
            for (LivingEntity entity : world.getLivingEntities()) {
                Mood mood = moodOf(entity);
                if (mood != null) counts.merge(mood, 1, Integer::sum);
            }
        }
        sender.sendMessage(PREFIX.append(Component.text(
            "Cheerful creepers don't explode, grumpy mobs are fast and hit harder, cheerful skeletons often hold fire. "
                + "Feed sweet berries to cheer a mob up. Loaded moody mobs — cheerful: "
                + counts.getOrDefault(Mood.CHEERFUL, 0) + ", neutral: " + counts.getOrDefault(Mood.NEUTRAL, 0)
                + ", grumpy: " + counts.getOrDefault(Mood.GRUMPY, 0)
                + (enabled ? "" : " (system OFF)"), NamedTextColor.YELLOW)));
        return true;
    }
}
