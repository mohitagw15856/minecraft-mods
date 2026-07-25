package dev.mohit.bountyboard;

import java.util.LinkedHashMap;
import java.util.Map;

/** One bounty: what to do, how much, and which day's board it belongs to. */
public class Bounty {

    public enum Type { HUNT, MINE, DELIVER }

    public enum Tier {
        COMMON("Common", 4, 0, 2),
        RARE("Rare", 10, 1, 5),
        EPIC("Epic", 16, 3, 10);

        public final String label;
        public final int emeralds;
        public final int diamonds;
        public final int xpLevels;

        Tier(String label, int emeralds, int diamonds, int xpLevels) {
            this.label = label;
            this.emeralds = emeralds;
            this.diamonds = diamonds;
            this.xpLevels = xpLevels;
        }
    }

    public final long day;
    public final Tier tier;
    public final Type type;
    public final String target;      // EntityType name (HUNT) or Material name (MINE/DELIVER)
    public final String displayName; // e.g. "Zombies"
    public final int goal;
    public int count;

    public Bounty(long day, Tier tier, Type type, String target, String displayName, int goal, int count) {
        this.day = day;
        this.tier = tier;
        this.type = type;
        this.target = target;
        this.displayName = displayName;
        this.goal = goal;
        this.count = count;
    }

    public String verb() {
        return switch (type) {
            case HUNT -> "Slay";
            case MINE -> "Mine";
            case DELIVER -> "Deliver";
        };
    }

    public String describe() {
        return verb() + " " + goal + " " + displayName;
    }

    public Map<String, Object> serialize() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("day", day);
        m.put("tier", tier.name());
        m.put("type", type.name());
        m.put("target", target);
        m.put("displayName", displayName);
        m.put("goal", goal);
        m.put("count", count);
        return m;
    }

    public static Bounty deserialize(Map<?, ?> m) {
        try {
            return new Bounty(
                ((Number) m.get("day")).longValue(),
                Tier.valueOf((String) m.get("tier")),
                Type.valueOf((String) m.get("type")),
                (String) m.get("target"),
                (String) m.get("displayName"),
                ((Number) m.get("goal")).intValue(),
                ((Number) m.get("count")).intValue()
            );
        } catch (Exception e) {
            return null;
        }
    }
}
