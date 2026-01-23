package me.temxs27.hyfine.optimization;

import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.event.EventBus; 
import com.hypixel.hytale.server.core.universe.world.events.ecs.ChunkSaveEvent;
import com.hypixel.hytale.server.core.universe.world.events.ecs.ChunkUnloadEvent;

import com.hypixel.hytale.component.system.CancellableEcsEvent; 
import com.hypixel.hytale.component.system.ICancellableEcsEvent; 
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import me.temxs27.hyfine.HyFine;
import me.temxs27.hyfine.core.OptimizationManager;
import me.temxs27.hyfine.core.PerformanceMonitor;
import me.temxs27.hyfine.preset.OptimizationPreset;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * The core optimization engine of the HyFine plugin.
 * It runs in a separate thread and applies optimizations based on the current preset and server performance.
 * Optimizations are applied by adjusting the world's target TPS and internal mod settings.
 * Note: Direct modification of WorldConfig from this thread is not possible due to API restrictions (requires CommandBuffer/ECS integration).
 * Alternative: Potential integration points could be EventBus, SpawningPlugin/NPCSpawningConfig, or a hypothetical ECS task scheduler.
 */
public class OptimizationEngine {

    private final HyFine plugin;
    private Thread optimizationThread;
    private volatile boolean running = false;

    // EventBus instance to register/unregister listeners
    private EventBus eventBus;

    // Internal state influenced by presets, can be used by other parts of the plugin
    private int itemDespawnTicks = 6000; // Configuration setting influenced by the preset
    private boolean aggressiveMode = false; // Flag influenced by the preset
    // Additional state for more complex optimizations
    private int estimatedEntityCount = 0; // Updated via EventBus or better estimation
    private int estimatedChunkCount = 0;  // Updated via EventBus or better estimation

    // --- Configuration Values Based on Presets ---
    // LOW preset settings
    private static final int LOW_ITEM_DESPAWN_TICKS = 6000; // 5 minutes
    private static final int LOW_TARGET_TPS = 30; // Default TPS
    private static final boolean LOW_ALLOW_UNLOAD = true; // Allow chunk unloading
    private static final boolean LOW_ALLOW_SAVE = true;   // Allow chunk saving

    // BALANCED preset settings (default server behavior assumed)
    private static final int BALANCED_ITEM_DESPAWN_TICKS = 3600; // 3 minutes
    private static final int BALANCED_TARGET_TPS = 30; // Default TPS
    private static final boolean BALANCED_ALLOW_UNLOAD = true;
    private static final boolean BALANCED_ALLOW_SAVE = true;

    // ULTRA preset settings
    private static final int ULTRA_ITEM_DESPAWN_TICKS = 1200; // 1 minute
    private static final int ULTRA_TARGET_TPS = 20; // Lower TPS to reduce load
    private static final boolean ULTRA_ALLOW_UNLOAD = false; // Prevent chunk unloading (keep memory high, reduce I/O)
    private static final boolean ULTRA_ALLOW_SAVE = false;   // Prevent chunk saving (keep memory high, reduce I/O)

    // --- State for Event-Based Policies ---
    // Map to store the current policy for each world
    private final Map<String, Boolean> savePolicyMap = new ConcurrentHashMap<>();
    private final Map<String, Boolean> unloadPolicyMap = new ConcurrentHashMap<>();


    public OptimizationEngine(HyFine plugin) {
        this.plugin = plugin;
    }

    public void start() {
        if (running) {
            return;
        }

        running = true;
        optimizationThread = new Thread(() -> {
            try {
                optimizationLoop();
            } catch (Exception e) {
                System.err.println("[HyFine] Critical error in optimization loop: " + e.getMessage());
                e.printStackTrace();
            }
        }, "HyFine-Optimization");
        optimizationThread.setDaemon(true);
        optimizationThread.start();

        // --- MAGIC: Initialize additional components ---
        initializeEventListeners(); // Attempt to listen for server events
        // initializeEcsScheduler(); // Attempt to get ECS task executor if available
    }

    public void stop() {
        running = false;
        if (optimizationThread != null) {
            optimizationThread.interrupt();
        }
        // --- MAGIC: Clean up additional components ---
        cleanupEventListeners(); // Remove event listeners
    }

    private void optimizationLoop() {
        while (running) {
            try {
                // Sleep for 1 second between optimization cycles
                TimeUnit.SECONDS.sleep(1);

                // Update performance data
                plugin.getPerformanceMonitor().update();

                // Apply optimizations for all worlds
                applyOptimizations();

            } catch (InterruptedException e) {
                break; // Exit cleanly if interrupted
            } catch (Exception e) {
                System.err.println("[HyFine] Error in optimization loop: " + e.getMessage());
                e.printStackTrace(); // Log full stack trace for debugging
            }
        }
    }

    private void applyOptimizations() {
        Universe universe = Universe.get();
        if (universe == null) {
            return;
        }

        OptimizationPreset preset = OptimizationManager.getPreset();
        // PerformanceMonitor.PerformanceData globalData = plugin.getPerformanceMonitor().getGlobalData(); // Removed: Method does not exist

        // Determine base settings based on the preset
        int despawnTicks;
        int targetTps;
        boolean allowUnload;
        boolean allowSave;

        switch (preset) {
            case LOW:
                despawnTicks = LOW_ITEM_DESPAWN_TICKS;
                targetTps = LOW_TARGET_TPS;
                allowUnload = LOW_ALLOW_UNLOAD;
                allowSave = LOW_ALLOW_SAVE;
                aggressiveMode = false;
                break;
            case BALANCED:
                despawnTicks = BALANCED_ITEM_DESPAWN_TICKS;
                targetTps = BALANCED_TARGET_TPS;
                allowUnload = BALANCED_ALLOW_UNLOAD;
                allowSave = BALANCED_ALLOW_SAVE;
                aggressiveMode = false;
                break;
            case ULTRA:
                despawnTicks = ULTRA_ITEM_DESPAWN_TICKS;
                targetTps = ULTRA_TARGET_TPS; // Lower TPS for ultra preset
                allowUnload = ULTRA_ALLOW_UNLOAD; // Prevent unloading
                allowSave = ULTRA_ALLOW_SAVE;     // Prevent saving
                aggressiveMode = true;
                break;
            default:
                // Fallback to BALANCED if preset is unknown
                despawnTicks = BALANCED_ITEM_DESPAWN_TICKS;
                targetTps = BALANCED_TARGET_TPS;
                allowUnload = BALANCED_ALLOW_UNLOAD;
                allowSave = BALANCED_ALLOW_SAVE;
                aggressiveMode = false;
                break;
        }

        // Apply preset-based settings and TPS adjustments to all worlds
        for (World world : universe.getWorlds().values()) {
            PerformanceMonitor.PerformanceData data = plugin.getPerformanceMonitor().getData(world.getName());

            // --- MAGIC: Update Event-Based Policy Maps ---
            // Store the current policy for this world based on preset and performance
            savePolicyMap.put(world.getName(), allowSave);
            unloadPolicyMap.put(world.getName(), allowUnload);

            // --- 1. Adjust Target TPS based on preset and current performance ---
            // This affects how fast the world tries to run overall.
            int currentTargetTps = world.getTps(); // Get current target TPS
            if (currentTargetTps != targetTps) {
                try {
                    world.setTps(targetTps); // Attempt to set the new target TPS
                    System.out.println("[HyFine] Applied target TPS=" + targetTps + " for preset " + preset.name() + " in world: " + world.getName());
                } catch (Exception e) {
                    System.err.println("[HyFine] Failed to set TPS for world '" + world.getName() + "': " + e.getMessage());
                    e.printStackTrace();
                }
            }

            // --- MAGIC: Attempt to Modify WorldConfig via ECS Task (Hypothetical) ---
            // This is pseudocode. You'd need a way to submit tasks to the ECS main thread.
            // if (canSubmitEcsTasks()) { // Check if mechanism exists
            //     submitEcsTask(world, () -> {
            //         WorldConfig config = world.getWorldConfig();
            //         config.setBlockTicking(shouldTickBlocks(preset, data.tps));
            //         config.setSpawningNPC(shouldSpawnNPCs(preset, data.tps));
            //         config.setIsAllNPCFrozen(shouldFreezeNPCs(preset, data.tps));
            //         config.setCanUnloadChunks(allowUnload);
            //         config.markChanged(); // Mark for propagation if needed
            //     });
            // }

            // --- 2. Apply Emergency/Moderate TPS-based adjustments ---
            // Example: If TPS is critically low, force a lower TPS temporarily, regardless of preset (except maybe ULTRA which is already low)
            if (data.tps < 15) {
                 int emergencyTps = 15; // Or even lower like 10
                 if (world.getTps() > emergencyTps) { // Only decrease if current target is higher
                     try {
                         world.setTps(emergencyTps);
                         System.out.println("[HyFine] Emergency: Set target TPS to " + emergencyTps + " due to very low TPS (" + data.tps + ") in world: " + world.getName());
                     } catch (Exception e) {
                         System.err.println("[HyFine] Failed to set emergency TPS for world '" + world.getName() + "': " + e.getMessage());
                         e.printStackTrace();
                     }
                 }
                 // Example: Force faster item despawn in emergencies if the current preset allows longer times
                 if (this.itemDespawnTicks > 1200) { // Only if current preset allows longer times
                      this.itemDespawnTicks = 1200; // 1 minute
                      System.out.println("[HyFine] Emergency: Set itemDespawnTicks to 1200 due to low TPS (" + data.tps + ") in world: " + world.getName());
                 }
                 // Example: Force prevent unloading/saving in emergencies (if policy allows)
                 // Update policy maps again for emergency
                 savePolicyMap.put(world.getName(), false); // Prevent save in emergency
                 unloadPolicyMap.put(world.getName(), false); // Prevent unload in emergency
            } else if (data.tps < 18 && preset == OptimizationPreset.BALANCED) { // Moderate adjustment for BALANCED preset
                 int moderateTps = 20;
                 if (world.getTps() > moderateTps) {
                     try {
                         world.setTps(moderateTps);
                         System.out.println("[HyFine] Moderate: Set target TPS to " + moderateTps + " due to moderate low TPS (" + data.tps + ") in world: " + world.getName());
                     } catch (Exception e) {
                         System.err.println("[HyFine] Failed to set moderate TPS for world '" + world.getName() + "': " + e.getMessage());
                         e.printStackTrace();
                     }
                 }
            }

            // Update internal state influenced by preset
            this.itemDespawnTicks = despawnTicks;
            // Update estimated counts if available
            this.estimatedEntityCount = data.entityCount; // From PerformanceMonitor (even if estimated)
            // this.estimatedChunkCount = getEstimatedChunkCount(world); // Would need implementation
        }
    }

    // --- MAGIC: Placeholder Methods for Advanced Features ---

    /**
     * Initializes listeners for server events (e.g., ChunkUnloadEvent, ChunkSaveEvent).
     * This requires access to the EventBus.
     */
    private void initializeEventListeners() {
        // Attempt to get the EventBus - We know from directory.txt and previous analysis that HytaleServer exists and has getEventBus().
        try {
            // Import added: import com.hypixel.hytale.server.core.HytaleServer;
            this.eventBus = HytaleServer.get().getEventBus();
            if (this.eventBus != null) {
                // Register listeners for ChunkSaveEvent and ChunkUnloadEvent
                // Register ChunkSaveEvent Listener
                this.eventBus.register(ChunkSaveEvent.class, event -> {
                    World eventWorld = event.getChunk().getWorld(); // Get world from the chunk in the event (Based on descompiled code)
                    String worldName = eventWorld.getName();
                    Boolean allowSave = savePolicyMap.get(worldName);
                    if (allowSave != null && !allowSave) {
                        // event.cancel(); // REMOVED: CancellableEcsEvent doesn't have cancel() method
                        event.setCancelled(true); // CORRECT: Use setCancelled(true) instead
                        System.out.println("[HyFine] Cancelled chunk save in world '" + worldName + "' based on policy.");
                    }
                });

                // Register ChunkUnloadEvent Listener
                this.eventBus.register(ChunkUnloadEvent.class, event -> {
                    World eventWorld = event.getChunk().getWorld(); // Get world from the chunk in the event (Based on descompiled code)
                    String worldName = eventWorld.getName();
                    Boolean allowUnload = unloadPolicyMap.get(worldName);
                    if (allowUnload != null && !allowUnload) {
                        // event.cancel(); // REMOVED: CancellableEcsEvent doesn't have cancel() method
                        event.setCancelled(true); // CORRECT: Use setCancelled(true) instead
                        System.out.println("[HyFine] Cancelled chunk unload in world '" + worldName + "' based on policy.");
                    }
                });
                System.out.println("[HyFine] Registered event listeners for chunk save/unload.");
            } else {
                System.out.println("[HyFine] WARNING: Could not access EventBus. Chunk save/unload policies will not work.");
            }
        } catch (Exception e) {
             System.err.println("[HyFine] Error accessing EventBus: " + e.getMessage());
             e.printStackTrace();
        }
    }

    /**
     * Cleans up registered event listeners.
     */
    private void cleanupEventListeners() {
        // Deregister listeners if eventBus was successfully obtained and listeners were registered
        if (this.eventBus != null) {
            // The EventBus doesn't have a direct deregister method for Consumer listeners.
            // A common pattern is to keep the EventRegistration object returned by register() and call its unregister() method.
            // Since we didn't capture the registration objects, we'll rely on the fact that the listeners are weakly referenced or that the EventBus handles cleanup when the plugin stops.
            // For now, just log.
            System.out.println("[HyFine] Event listeners are active. They may be automatically cleaned up by the EventBus upon shutdown.");
            // In a real scenario, you might need to store the EventRegistration objects and call .unregister() on them.
        }
        System.out.println("[HyFine] Cleaning up event listeners... (Done)");
    }


    /**
     * Attempts to submit a task to the ECS main thread for safe modifications.
     * This is a hypothetical method requiring a server-provided mechanism.
     *
     * @param world The world whose ECS context is used.
     * @param task  The runnable task to execute on the ECS thread.
     */
    // private void submitEcsTask(World world, Runnable task) {
    //     // This would require a method like world.submitEcsTask(task) or similar
    //     // which may not exist in the public API.
    // }

    /**
     * Checks if a mechanism to submit ECS tasks exists.
     * This is a hypothetical check.
     *
     * @return True if ECS task submission is possible, false otherwise.
     */
    // private boolean canSubmitEcsTasks() {
    //     // Check for existence of methods like world.submitEcsTask
    //     return false; // Placeholder
    // }

    /**
     * Determines if blocks should tick based on preset and TPS.
     * Used in hypothetical ECS task.
     *
     * @param preset The current optimization preset.
     * @param currentTps The current TPS of the world.
     * @return True if block ticking should be enabled, false otherwise.
     */
    // private boolean shouldTickBlocks(OptimizationPreset preset, int currentTps) {
    //     // Logic based on preset and TPS
    //     if (preset == OptimizationPreset.ULTRA) return false;
    //     if (currentTps < 12) return false; // Emergency
    //     return true;
    // }

    /**
     * Determines if NPCs should spawn based on preset and TPS.
     * Used in hypothetical ECS task.
     *
     * @param preset The current optimization preset.
     * @param currentTps The current TPS of the world.
     * @return True if NPC spawning should be enabled, false otherwise.
     */
    // private boolean shouldSpawnNPCs(OptimizationPreset preset, int currentTps) {
    //     // Logic based on preset and TPS
    //     if (preset == OptimizationPreset.ULTRA) return false;
    //     if (currentTps < 15) return false; // Emergency
    //     return true;
    // }

    /**
     * Determines if all NPCs should be frozen based on preset and TPS.
     * Used in hypothetical ECS task.
     *
     * @param preset The current optimization preset.
     * @param currentTps The current TPS of the world.
     * @return True if all NPCs should be frozen, false otherwise.
     */
    // private boolean shouldFreezeNPCs(OptimizationPreset preset, int currentTps) {
    //     // Logic based on preset and TPS
    //     if (preset == OptimizationPreset.ULTRA) return true;
    //     if (currentTps < 10) return true; // Emergency
    //     return false;
    // }

    // --- Getters for State Influenced by Presets and Metrics ---
    // These can be used by other parts of the plugin (e.g., spawning systems, item despawn logic).

    public int getItemDespawnTicks() {
        return itemDespawnTicks;
    }

    public boolean isAggressiveMode() {
        return aggressiveMode;
    }

    public int getEstimatedEntityCount() {
        return estimatedEntityCount;
    }

    public int getEstimatedChunkCount() {
        return estimatedChunkCount;
    }
}
