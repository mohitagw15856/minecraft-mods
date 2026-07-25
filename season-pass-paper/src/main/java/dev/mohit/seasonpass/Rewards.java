package dev.mohit.seasonpass;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.Particle;

/** The 20-tier reward track. All cosmetic. */
public final class Rewards {

    public enum Kind { COLOR, TRAIL, TITLE }

    public record Reward(int tier, Kind kind, String key, String label, Material icon) {}

    public static final Reward[] TRACK = {
        new Reward(1, Kind.COLOR, "yellow", "Yellow name", Material.YELLOW_DYE),
        new Reward(2, Kind.TITLE, "Wanderer", "[Wanderer]", Material.LEATHER_BOOTS),
        new Reward(3, Kind.TRAIL, "villager", "Sparkle trail", Material.EMERALD),
        new Reward(4, Kind.TITLE, "Pathfinder", "[Pathfinder]", Material.COMPASS),
        new Reward(5, Kind.COLOR, "aqua", "Aqua name", Material.LIGHT_BLUE_DYE),
        new Reward(6, Kind.TITLE, "Explorer", "[Explorer]", Material.MAP),
        new Reward(7, Kind.TRAIL, "note", "Music trail", Material.NOTE_BLOCK),
        new Reward(8, Kind.TITLE, "Adventurer", "[Adventurer]", Material.IRON_SWORD),
        new Reward(9, Kind.COLOR, "green", "Green name", Material.GREEN_DYE),
        new Reward(10, Kind.TITLE, "Trailblazer", "[Trailblazer]", Material.CAMPFIRE),
        new Reward(11, Kind.TRAIL, "flame", "Flame trail", Material.BLAZE_POWDER),
        new Reward(12, Kind.TITLE, "Voyager", "[Voyager]", Material.OAK_BOAT),
        new Reward(13, Kind.COLOR, "light_purple", "Purple name", Material.PURPLE_DYE),
        new Reward(14, Kind.TITLE, "Conqueror", "[Conqueror]", Material.DIAMOND_SWORD),
        new Reward(15, Kind.TRAIL, "soul", "Soul trail", Material.SOUL_TORCH),
        new Reward(16, Kind.TITLE, "Veteran", "[Veteran]", Material.IRON_HELMET),
        new Reward(17, Kind.COLOR, "gold", "Gold name", Material.GOLD_INGOT),
        new Reward(18, Kind.TITLE, "Legend", "[Legend]", Material.TOTEM_OF_UNDYING),
        new Reward(19, Kind.TRAIL, "heart", "Heart trail", Material.POPPY),
        new Reward(20, Kind.TITLE, "Mythic", "[Mythic]", Material.NETHER_STAR)
    };

    public static NamedTextColor color(String key) {
        return switch (key == null ? "" : key) {
            case "yellow" -> NamedTextColor.YELLOW;
            case "aqua" -> NamedTextColor.AQUA;
            case "green" -> NamedTextColor.GREEN;
            case "light_purple" -> NamedTextColor.LIGHT_PURPLE;
            case "gold" -> NamedTextColor.GOLD;
            default -> null;
        };
    }

    public static Particle particle(String key) {
        return switch (key == null ? "" : key) {
            case "villager" -> Particle.HAPPY_VILLAGER;
            case "note" -> Particle.NOTE;
            case "flame" -> Particle.FLAME;
            case "soul" -> Particle.SOUL_FIRE_FLAME;
            case "heart" -> Particle.HEART;
            default -> null;
        };
    }

    private Rewards() {}
}
