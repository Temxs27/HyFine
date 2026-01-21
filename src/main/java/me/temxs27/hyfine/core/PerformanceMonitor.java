package me.temxs27.hyfine.core;

import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

public class PerformanceMonitor {

    private final Map<String, PerformanceData> worldData = new ConcurrentHashMap<>();

    public void update() {
        Universe universe = Universe.get();
        if (universe == null) {
            return;
        }

        for (World world : universe.getWorlds().values()) {
            String worldName = world.getName();
            PerformanceData data = worldData.computeIfAbsent(worldName, k -> new PerformanceData());

            data.tps = world.getTps();
            data.tick = world.getTick();
            data.playerCount = world.getPlayerCount();

            try {
                data.entityCount = estimateEntityCount(world);
            } catch (Exception e) {
                data.entityCount = -1;
            }
        }
    }

    private int estimateEntityCount(World world) {
        return world.getPlayerCount() * 50;
    }

    public PerformanceData getData(String worldName) {
        return worldData.getOrDefault(worldName, new PerformanceData());
    }

    public static class PerformanceData {
        public int tps = 20;
        public long tick = 0;
        public int playerCount = 0;
        public int entityCount = 0;

        public boolean isLagging() {
            return tps < 18;
        }

        public String getStatus() {
            if (tps >= 19) return "Excellent";
            if (tps >= 17) return "Good";
            if (tps >= 15) return "Fair";
            return "Poor";
        }
    }
}