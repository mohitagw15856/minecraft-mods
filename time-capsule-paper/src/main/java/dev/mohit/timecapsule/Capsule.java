package dev.mohit.timecapsule;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** One sealed chest. */
public class Capsule {
    public final String world;
    public final int x, y, z;
    public final UUID owner;
    public final String ownerName;
    public final long sealedDay;
    public final long unlockDay;
    public final String message;

    public Capsule(String world, int x, int y, int z, UUID owner, String ownerName,
                   long sealedDay, long unlockDay, String message) {
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.owner = owner;
        this.ownerName = ownerName;
        this.sealedDay = sealedDay;
        this.unlockDay = unlockDay;
        this.message = message;
    }

    public String key() {
        return world + ":" + x + ":" + y + ":" + z;
    }

    public Location location() {
        World w = Bukkit.getWorld(world);
        return w == null ? null : new Location(w, x, y, z);
    }

    public Map<String, Object> serialize() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("world", world);
        m.put("x", x);
        m.put("y", y);
        m.put("z", z);
        m.put("owner", owner.toString());
        m.put("ownerName", ownerName);
        m.put("sealedDay", sealedDay);
        m.put("unlockDay", unlockDay);
        m.put("message", message);
        return m;
    }

    public static Capsule deserialize(Map<?, ?> m) {
        try {
            return new Capsule(
                (String) m.get("world"),
                ((Number) m.get("x")).intValue(),
                ((Number) m.get("y")).intValue(),
                ((Number) m.get("z")).intValue(),
                UUID.fromString((String) m.get("owner")),
                (String) m.get("ownerName"),
                ((Number) m.get("sealedDay")).longValue(),
                ((Number) m.get("unlockDay")).longValue(),
                m.get("message") == null ? "" : (String) m.get("message")
            );
        } catch (Exception e) {
            return null;
        }
    }
}
