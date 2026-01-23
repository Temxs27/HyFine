package me.temxs27.hyfine.core;

import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * Monitors server performance metrics such as TPS, tick count, player count, and estimated entity count for each world.
 * Updates the metrics periodically.
 */
public class PerformanceMonitor {

    // Stores performance data for each world, keyed by world name.
    private final Map<String, PerformanceData> worldData = new ConcurrentHashMap<>();

    /**
     * Updates the performance data for all loaded worlds.
     * Retrieves TPS, tick count, player count, and estimates entity count.
     */
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
                // If estimation fails, set count to -1 to indicate an error
                data.entityCount = -1;
            }
        }
    }

    /**
     * Estimates the number of entities in a world.
     * This is a placeholder implementation that multiplies player count by 50.
     * A more accurate method would iterate through the EntityStore.
     *
     * @param world The world to estimate entities for.
     * @return An estimated count of entities.
     */
    private int estimateEntityCount(World world) {
        // Placeholder: This is a very rough estimation.
        // A real implementation might need to access the EntityStore directly.
        return world.getPlayerCount() * 50;
    }

    /**
     * Retrieves the performance data for a specific world.
     *
     * @param worldName The name of the world.
     * @return The PerformanceData object for the world, or a default one if not found.
     */
    public PerformanceData getData(String worldName) {
        return worldData.getOrDefault(worldName, new PerformanceData());
    }

    /**
     * Inner class to hold performance metrics for a single world.
     */
    public static class PerformanceData {
        public int tps = 20;        // Current TPS (Ticks Per Second)
        public long tick = 0;       // Current tick number
        public int playerCount = 0; // Number of players in the world
        public int entityCount = 0; // Estimated number of entities in the world

        /**
         * Checks if the world is considered lagging based on TPS.
         *
         * @return true if TPS is below 18, false otherwise.
         */
        public boolean isLagging() {
            return tps < 18;
        }

        /**
         * Gets a human-readable status based on the current TPS.
         *
         * @return "Excellent", "Good", "Fair", or "Poor".
         */
        public String getStatus() {
            if (tps >= 19) return "Excellent";
            if (tps >= 17) return "Good";
            if (tps >= 15) return "Fair";
            return "Poor";
        }
    }
}
