package dev.mohit.swornrivals;

import java.util.UUID;

/** Per-player rivalry state. */
public class RivalData {
    public String name = "?";
    public int weekScore;
    public int lifetimeWins;
    public UUID rival;       // null = no rival this week
    public boolean hasEdge;  // won last week: Speed I + 10% bonus points
    public boolean seenThisWeek;
}
