package me.temxs27.hyfine.optimization;

import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import me.temxs27.hyfine.HyFine;
import me.temxs27.hyfine.core.OptimizationManager;
import me.temxs27.hyfine.core.PerformanceMonitor;
import me.temxs27.hyfine.preset.OptimizationPreset;

public class OptimizationEngine {

    private final HyFine plugin;
    private Thread optimizationThread;
    private volatile boolean running = false;

    private int itemDespawnTicks = 6000;
    private boolean aggressiveMode = false;

    public OptimizationEngine(HyFine plugin) {
        this.plugin = plugin;
    }

    public void start() {
        if (running) {
            return;
        }

        running = true;
        optimizationThread = new Thread(new Runnable() {
            @Override
            public void run() {
                optimizationLoop();
            }
        }, "HyFine-Optimization");
        optimizationThread.setDaemon(true);
        optimizationThread.start();
    }

    public void stop() {
        running = false;
        if (optimizationThread != null) {
            optimizationThread.interrupt();
        }
    }

    private void optimizationLoop() {
        while (running) {
            try {
                Thread.sleep(1000);

                plugin.getPerformanceMonitor().update();
                applyOptimizations();

            } catch (InterruptedException e) {
                break;
            } catch (Exception e) {
                System.err.println("[HyFine] Error in optimization loop: " + e.getMessage());
            }
        }
    }

    private void applyOptimizations() {
        Universe universe = Universe.get();
        if (universe == null) {
            return;
        }

        OptimizationPreset preset = OptimizationManager.getPreset();

        for (World world : universe.getWorlds().values()) {
            PerformanceMonitor.PerformanceData data = plugin.getPerformanceMonitor().getData(world.getName());

            switch (preset) {
                case LOW:
                    itemDespawnTicks = 6000;
                    aggressiveMode = false;
                    if (data.tps < 15) {
                        applyEmergencyOptimizations(world);
                    }
                    break;

                case BALANCED:
                    itemDespawnTicks = 3600;
                    aggressiveMode = false;
                    if (data.tps < 18) {
                        applyModerateOptimizations(world);
                    }
                    break;

                case ULTRA:
                    itemDespawnTicks = 1200;
                    aggressiveMode = true;
                    applyAggressiveOptimizations(world);
                    break;
            }
        }
    }

    private void applyEmergencyOptimizations(World world) {
        optimizeChunkTicking(world, true);
    }

    private void applyModerateOptimizations(World world) {
        optimizeChunkTicking(world, false);
    }

    private void applyAggressiveOptimizations(World world) {
        optimizeChunkTicking(world, true);
        reduceViewDistance(world);
    }

    private void optimizeChunkTicking(World world, boolean aggressive) {
        try {
            if (aggressive) {
                int currentTick = (int) (world.getTick() % 20);
                if (currentTick == 0) {
                    optimizeDistantChunks(world);
                }
            }
        } catch (Exception e) {
        }
    }

    private void optimizeDistantChunks(World world) {
        try {
            long tick = world.getTick();

            if (tick % 20 == 0) {
            }
        } catch (Exception ignored) {
        }
    }

    private void reduceViewDistance(World world) {
        PerformanceMonitor.PerformanceData data = plugin.getPerformanceMonitor().getData(world.getName());

        if (data.tps < 16 && !world.isPaused()) {
            // View distance optimization
        }
    }

    public int getItemDespawnTicks() {
        return itemDespawnTicks;
    }

    public boolean isAggressiveMode() {
        return aggressiveMode;
    }
}