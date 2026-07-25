package dev.mohit.seasonpass;

/** Per-player season pass state. */
public class PassData {
    public int xp;
    public String color;   // equipped name color reward key, or null
    public String trail;   // equipped particle trail key, or null
    public String title;   // equipped chat title, or null
    public int miningXpToday;
    public long lastDay = -1;

    /** Tier N needs N*100 XP on top of tier N-1: cumulative 50*N*(N+1). */
    public int tier() {
        int n = 0;
        while (n < 20 && 50L * (n + 1) * (n + 2) <= xp) n++;
        return n;
    }

    public static int cumulativeFor(int tier) {
        return 50 * tier * (tier + 1);
    }
}
