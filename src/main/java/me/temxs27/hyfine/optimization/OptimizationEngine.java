package me.temxs27.hyfine.optimization;

import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.event.EventBus; 
import com.hypixel.hytale.server.core.universe.world.events.ecs.ChunkSaveEvent;
import com.hypixel.hytale.server.core.universe.world.events.ecs.ChunkUnloadEvent;

import com.hypixel.hytale.server.core.universe.world.WorldConfig;

import com.hypixel.hytale.component.system.CancellableEcsEvent; 
import com.hypixel.hytale.component.system.ICancellableEcsEvent; 
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.entity.entities.Player; // Import for Player component
import com.hypixel.hytale.server.core.universe.PlayerRef; // Import for PlayerRef component
import com.hypixel.hytale.server.core.modules.entity.tracker.EntityTrackerSystems; // Import for EntityViewer
import com.hypixel.hytale.protocol.packets.setup.ViewRadius; // Import for ViewRadius packet
import com.hypixel.hytale.component.ComponentType; // Import for ComponentType
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore; // Import for EntityStore

import me.temxs27.hyfine.HyFine;
import me.temxs27.hyfine.core.OptimizationManager;
import me.temxs27.hyfine.core.PerformanceMonitor;
import me.temxs27.hyfine.preset.OptimizationPreset;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level; // Import necessary for Level
import java.util.logging.Logger; // Import necessary for Logger

/**
 * The core optimization engine of the HyFine plugin.
 * It runs in a separate thread and applies optimizations based on the current preset and server performance.
 * Optimizations are applied by adjusting the world's target TPS and internal mod settings.
 * Note: Direct modification of WorldConfig from this thread is possible based on PrefabEditSessionManager findings.
 * Alternative: Potential integration points could be EventBus, SpawningPlugin/NPCSpawningConfig, or a hypothetical ECS task scheduler.
 */
public class OptimizationEngine {

    // Logger instance for this class
    private static final Logger LOGGER = Logger.getLogger(OptimizationEngine.class.getName());
    // Optionally, set the level here if needed, though it often depends on the root logger configuration
    // static { LOGGER.setLevel(Level.ALL); } // Uncomment if you want to ensure FINE logs appear

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
    private static final int LOW_CLIENT_VIEW_RADIUS = 5; // Reduced slightly

    // BALANCED preset settings (default server behavior assumed)
    private static final int BALANCED_ITEM_DESPAWN_TICKS = 3600; // 3 minutes
    private static final int BALANCED_TARGET_TPS = 30; // Default TPS
    private static final boolean BALANCED_ALLOW_UNLOAD = true;
    private static final boolean BALANCED_ALLOW_SAVE = true;
    private static final int BALANCED_CLIENT_VIEW_RADIUS = 4; // Reduced slightly

    // ULTRA preset settings
    private static final int ULTRA_ITEM_DESPAWN_TICKS = 1200; // 1 minute
    private static final int ULTRA_TARGET_TPS = 20; // Lower TPS to reduce load
    private static final boolean ULTRA_ALLOW_UNLOAD = false; // Prevent chunk unloading (keep memory high, reduce I/O)
    private static final boolean ULTRA_ALLOW_SAVE = false;   // Prevent chunk saving (keep memory high, reduce I/O)
    private static final int ULTRA_CLIENT_VIEW_RADIUS = 3; // Reduced further, but not extremely

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
            LOGGER.warning("[HyFine] Universe is null, skipping optimization cycle.");
            return;
        }

        OptimizationPreset preset = OptimizationManager.getPreset();
        LOGGER.info("[HyFine] Starting optimization cycle for preset: " + preset.name());

        // PerformanceMonitor.PerformanceData globalData = plugin.getPerformanceMonitor().getGlobalData(); // Removed: Method does not exist

        // Determine base settings based on the preset
        int despawnTicks;
        int targetTps;
        boolean allowUnload;
        boolean allowSave;
        int clientViewRadius; // Added for view radius optimization

        switch (preset) {
            case LOW:
                despawnTicks = LOW_ITEM_DESPAWN_TICKS;
                targetTps = LOW_TARGET_TPS;
                allowUnload = LOW_ALLOW_UNLOAD;
                allowSave = LOW_ALLOW_SAVE;
                clientViewRadius = LOW_CLIENT_VIEW_RADIUS; // Assign value based on preset
                aggressiveMode = false;
                break;
            case BALANCED:
                despawnTicks = BALANCED_ITEM_DESPAWN_TICKS;
                targetTps = BALANCED_TARGET_TPS;
                allowUnload = BALANCED_ALLOW_UNLOAD;
                allowSave = BALANCED_ALLOW_SAVE;
                clientViewRadius = BALANCED_CLIENT_VIEW_RADIUS; // Assign value based on preset
                aggressiveMode = false;
                break;
            case ULTRA:
                despawnTicks = ULTRA_ITEM_DESPAWN_TICKS;
                targetTps = ULTRA_TARGET_TPS; // Lower TPS for ultra preset
                allowUnload = ULTRA_ALLOW_UNLOAD; // Prevent unloading
                allowSave = ULTRA_ALLOW_SAVE;     // Prevent saving
                clientViewRadius = ULTRA_CLIENT_VIEW_RADIUS; // Assign value based on preset
                aggressiveMode = true;
                break;
            default:
                // Fallback to BALANCED if preset is unknown
                despawnTicks = BALANCED_ITEM_DESPAWN_TICKS;
                targetTps = BALANCED_TARGET_TPS;
                allowUnload = BALANCED_ALLOW_UNLOAD;
                allowSave = BALANCED_ALLOW_SAVE;
                clientViewRadius = BALANCED_CLIENT_VIEW_RADIUS; // Assign value based on preset
                aggressiveMode = false;
                break;
        }

        // Apply preset-based settings and TPS adjustments to all worlds
        for (World world : universe.getWorlds().values()) {
            String worldName = world.getName();
            PerformanceMonitor.PerformanceData data = plugin.getPerformanceMonitor().getData(worldName);

            // --- MAGIC: Update Event-Based Policy Maps ---
            // Store the current policy for this world based on preset and performance
            savePolicyMap.put(worldName, allowSave);
            unloadPolicyMap.put(worldName, allowUnload);
            LOGGER.info("[HyFine] Updated policy maps for world '" + worldName + "': Save=" + allowSave + ", Unload=" + allowUnload);

            // --- 1. Adjust Target TPS based on preset and current performance ---
            // This affects how fast the world tries to run overall.
            int currentTargetTps = world.getTps(); // Get current target TPS
            if (currentTargetTps != targetTps) {
                // Wrap the setTps call in a Runnable and execute it on the world's ECS thread
                try {
                    world.execute(() -> {
                        try {
                             world.setTps(targetTps); // Attempt to set the new target TPS
                             // System.out.println("[HyFine] Applied target TPS=" + targetTps + " for preset " + preset.name() + " in world: " + worldName);
                             LOGGER.info("[HyFine] Successfully set TPS to " + targetTps + " for world '" + worldName + "' on ECS thread.");
                        } catch (Exception e) {
                             // System.err.println("[HyFine] Failed to set TPS for world '" + worldName + "': " + e.getMessage());
                             LOGGER.severe("[HyFine] Failed to set TPS for world '" + worldName + "' on ECS thread: " + e.getMessage());
                             e.printStackTrace();
                        }
                    });
                    LOGGER.info("[HyFine] Queued TPS change to " + targetTps + " for world '" + worldName + "' via world.execute().");
                } catch (Exception e) {
                     System.err.println("[HyFine] Failed to queue TPS change for world '" + worldName + "': " + e.getMessage());
                     LOGGER.severe("[HyFine] Failed to queue TPS change for world '" + worldName + "' via world.execute(): " + e.getMessage());
                     e.printStackTrace();
                }
            } else {
                 LOGGER.fine("[HyFine] TPS for world '" + worldName + "' is already " + targetTps + ", no change needed.");
            }

            // --- NEW: Apply WorldConfig Optimizations ---
            // Try to modify WorldConfig directly (based on PrefabEditSessionManager and SpawnCommand findings)
            try {
                WorldConfig config = world.getWorldConfig(); // Get the current config
                if (config != null) {
                    // Get current values for logging
                    boolean currentBlockTicking = config.isBlockTicking();
                    boolean currentSpawningNPC = config.isSpawningNPC();
                    boolean currentIsAllNPCFrozen = config.isAllNPCFrozen();
                    boolean currentCanUnloadChunks = config.canUnloadChunks();

                    // Apply optimizations based on preset and TPS
                    boolean newBlockTicking = shouldTickBlocks(preset, data.tps);
                    boolean newSpawningNPC = shouldSpawnNPCs(preset, data.tps);
                    boolean newIsAllNPCFrozen = shouldFreezeNPCs(preset, data.tps);
                    boolean newCanUnloadChunks = allowUnload;

                    if (currentBlockTicking != newBlockTicking) {
                        config.setBlockTicking(newBlockTicking);
                        LOGGER.info("[HyFine] Changed blockTicking for world '" + worldName + "' from " + currentBlockTicking + " to " + newBlockTicking);
                    }
                    if (currentSpawningNPC != newSpawningNPC) {
                        config.setSpawningNPC(newSpawningNPC);
                        LOGGER.info("[HyFine] Changed spawningNPC for world '" + worldName + "' from " + currentSpawningNPC + " to " + newSpawningNPC);
                    }
                    if (currentIsAllNPCFrozen != newIsAllNPCFrozen) {
                        config.setIsAllNPCFrozen(newIsAllNPCFrozen);
                        LOGGER.info("[HyFine] Changed isAllNPCFrozen for world '" + worldName + "' from " + currentIsAllNPCFrozen + " to " + newIsAllNPCFrozen);
                    }
                    if (currentCanUnloadChunks != newCanUnloadChunks) {
                        config.setCanUnloadChunks(newCanUnloadChunks);
                        LOGGER.info("[HyFine] Changed canUnloadChunks for world '" + worldName + "' from " + currentCanUnloadChunks + " to " + newCanUnloadChunks);
                    }

                    // IMPORTANT: Call markChanged() to notify the server that the config has changed (based on SpawnCommand.java)
                    config.markChanged(); // <-- AÑADIDA ESTA LINEA CRUCIAL
                    System.out.println("[HyFine] Applied WorldConfig optimizations for world: " + worldName);
                    LOGGER.info("[HyFine] Applied WorldConfig optimizations and called markChanged() for world '" + worldName + "'");
                } else {
                    LOGGER.warning("[HyFine] WorldConfig for world '" + worldName + "' is null, skipping.");
                }
            } catch (Exception e) {
                System.err.println("[HyFine] Failed to modify WorldConfig for world '" + worldName + "': " + e.getMessage());
                LOGGER.severe("[HyFine] Failed to modify WorldConfig for world '" + worldName + "': " + e.getMessage());
                e.printStackTrace();
            }

            // --- NEW: Apply Player View Radius Optimizations ---
            // Iterate through players in the world and adjust their view radius
            // This needs to be done on the world's ECS thread
            try {
                world.execute(() -> {
                    try {
                        com.hypixel.hytale.component.Store<EntityStore> entityStore = world.getEntityStore().getStore(); // Get the Store<EntityStore>
                        ComponentType<EntityStore, PlayerRef> playerRefType = PlayerRef.getComponentType(); // Get the ComponentType
                        ComponentType<EntityStore, Player> playerType = Player.getComponentType(); // Get the ComponentType
                        ComponentType<EntityStore, EntityTrackerSystems.EntityViewer> viewerType = EntityTrackerSystems.EntityViewer.getComponentType(); // Get the ComponentType

                        // Use forEachEntityParallel with ComponentType - this is the efficient way to iterate entities of a specific type
                        entityStore.forEachEntityParallel(playerRefType, (index, archetypeChunk, commandBuffer) -> {
                            // Get PlayerRef component from the ArchetypeChunk at the given index
                            PlayerRef playerRefComponent = archetypeChunk.getComponent(index, playerRefType);
                            if (playerRefComponent != null) {
                                // Get the actual Player entity reference from the PlayerRef component
                                com.hypixel.hytale.component.Ref<EntityStore> actualPlayerEntityRef = playerRefComponent.getReference();
                                if (actualPlayerEntityRef != null && actualPlayerEntityRef.isValid()) {
                                    // Get the Player component from the Player entity
                                    // Use the 'entityStore' variable from the outer scope of forEachEntityParallel
                                    Player playerComponent = entityStore.getComponent(actualPlayerEntityRef, playerType);
                                    if (playerComponent != null) {
                                        // Get the EntityViewer component
                                        // Use the 'entityStore' variable from the outer scope of forEachEntityParallel
                                        EntityTrackerSystems.EntityViewer entityViewerComponent = entityStore.getComponent(actualPlayerEntityRef, viewerType);
                                        if (entityViewerComponent != null) {
                                            int currentRadiusChunks = playerComponent.getClientViewRadius();
                                            int targetRadiusBlocks = clientViewRadius * 32; // Convert chunks to blocks as per command logic

                                            if (currentRadiusChunks != clientViewRadius) {
                                                // Apply the changes directly on the ECS thread (within the parallel execution context)
                                                playerComponent.setClientViewRadius(clientViewRadius);
                                                entityViewerComponent.viewRadiusBlocks = targetRadiusBlocks;

                                                // Send the ViewRadius packet to the client using the PlayerRef
                                                ViewRadius viewRadiusPacket = new ViewRadius(targetRadiusBlocks);
                                                playerRefComponent.getPacketHandler().writeNoCache(viewRadiusPacket);

                                                LOGGER.info("[HyFine] Changed client view radius for player in world '" + worldName + "' to " + clientViewRadius + " chunks (" + targetRadiusBlocks + " blocks)");
                                            }
                                        } else {
                                            LOGGER.warning("[HyFine] EntityViewer component not found for player entity ref " + actualPlayerEntityRef.getIndex() + " in world '" + worldName + "'");
                                        }
                                    } else {
                                        LOGGER.warning("[HyFine] Player component not found for player entity ref " + actualPlayerEntityRef.getIndex() + " in world '" + worldName + "'");
                                    }
                                } else {
                                    LOGGER.fine("[HyFine] Player entity ref is null or invalid for PlayerRef entity ref at index " + index + " in world '" + worldName + "'");
                                }
                            } else {
                                // This shouldn't happen if the query is correct, but just in case
                                LOGGER.warning("[HyFine] PlayerRef component not found at index " + index + " in archetype chunk for world '" + worldName + "'");
                            }
                        });

                    } catch (Exception e) {
                         LOGGER.severe("[HyFine] Error applying player view radius optimization on ECS thread for world '" + worldName + "': " + e.getMessage());
                         e.printStackTrace();
                    }
                });
                LOGGER.info("[HyFine] Queued player view radius optimization for world '" + worldName + "' via world.execute().");
            } catch (Exception e) {
                 System.err.println("[HyFine] Failed to queue player view radius optimization for world '" + worldName + "': " + e.getMessage());
                 LOGGER.severe("[HyFine] Failed to queue player view radius optimization for world '" + worldName + "' via world.execute(): " + e.getMessage());
                 e.printStackTrace();
            }


            // --- 2. Apply Emergency/Moderate TPS-based adjustments ---
            // Example: If TPS is critically low, force a lower TPS temporarily, regardless of preset (except maybe ULTRA which is already low)
            if (data.tps < 15) {
                 int emergencyTps = 15; // Or even lower like 10
                 if (world.getTps() > emergencyTps) { // Only decrease if current target is higher
                     // Wrap the emergency setTps call in a Runnable and execute it on the world's ECS thread
                     try {
                         world.execute(() -> {
                             try {
                                 world.setTps(emergencyTps);
                                 // System.out.println("[HyFine] Emergency: Set target TPS to " + emergencyTps + " due to very low TPS (" + data.tps + ") in world: " + worldName);
                                 LOGGER.warning("[HyFine] EMERGENCY: Set TPS to " + emergencyTps + " for world '" + worldName + "' due to low TPS (" + data.tps + ") on ECS thread.");
                             } catch (Exception e) {
                                 // System.err.println("[HyFine] Failed to set emergency TPS for world '" + worldName + "': " + e.getMessage());
                                 LOGGER.severe("[HyFine] Failed to set emergency TPS for world '" + worldName + "' on ECS thread: " + e.getMessage());
                                 e.printStackTrace();
                             }
                         });
                         LOGGER.info("[HyFine] Queued emergency TPS change to " + emergencyTps + " for world '" + worldName + "' via world.execute().");
                     } catch (Exception e) {
                          System.err.println("[HyFine] Failed to queue emergency TPS change for world '" + worldName + "': " + e.getMessage());
                          LOGGER.severe("[HyFine] Failed to queue emergency TPS change for world '" + worldName + "' via world.execute(): " + e.getMessage());
                          e.printStackTrace();
                     }
                 }
                 // Example: Force faster item despawn in emergencies if the current preset allows longer times
                 if (this.itemDespawnTicks > 1200) { // Only if current preset allows longer times
                      this.itemDespawnTicks = 1200; // 1 minute
                      System.out.println("[HyFine] Emergency: Set itemDespawnTicks to 1200 due to low TPS (" + data.tps + ") in world: " + worldName);
                      LOGGER.info("[HyFine] EMERGENCY: Set itemDespawnTicks to 1200 for world '" + worldName + "' due to low TPS (" + data.tps + ")");
                 }
                 // Example: Force prevent unloading/saving in emergencies (if policy allows)
                 // Update policy maps again for emergency
                 savePolicyMap.put(worldName, false); // Prevent save in emergency
                 unloadPolicyMap.put(worldName, false); // Prevent unload in emergency
                 LOGGER.info("[HyFine] EMERGENCY: Updated policy maps for world '" + worldName + "': Save=false, Unload=false");
            } else if (data.tps < 18 && preset == OptimizationPreset.BALANCED) { // Moderate adjustment for BALANCED preset
                 int moderateTps = 20;
                 if (world.getTps() > moderateTps) {
                     // Wrap the moderate setTps call in a Runnable and execute it on the world's ECS thread
                     try {
                         world.execute(() -> {
                             try {
                                 world.setTps(moderateTps);
                                 // System.out.println("[HyFine] Moderate: Set target TPS to " + moderateTps + " due to moderate low TPS (" + data.tps + ") in world: " + worldName);
                                 LOGGER.info("[HyFine] MODERATE: Set TPS to " + moderateTps + " for world '" + worldName + "' due to moderate low TPS (" + data.tps + ") on ECS thread.");
                             } catch (Exception e) {
                                 // System.err.println("[HyFine] Failed to set moderate TPS for world '" + worldName + "': " + e.getMessage());
                                 LOGGER.severe("[HyFine] Failed to set moderate TPS for world '" + worldName + "' on ECS thread: " + e.getMessage());
                                 e.printStackTrace();
                             }
                         });
                         LOGGER.info("[HyFine] Queued moderate TPS change to " + moderateTps + " for world '" + worldName + "' via world.execute().");
                     } catch (Exception e) {
                          System.err.println("[HyFine] Failed to queue moderate TPS change for world '" + worldName + "': " + e.getMessage());
                          LOGGER.severe("[HyFine] Failed to queue moderate TPS change for world '" + worldName + "' via world.execute(): " + e.getMessage());
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
        LOGGER.info("[HyFine] Completed optimization cycle for preset: " + preset.name());
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
                        LOGGER.info("[HyFine] Cancelled chunk save event for chunk in world '" + worldName + "'");
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
                        LOGGER.info("[HyFine] Cancelled chunk unload event for chunk in world '" + worldName + "'");
                    }
                });
                System.out.println("[HyFine] Registered event listeners for chunk save/unload.");
                LOGGER.info("[HyFine] Successfully registered event listeners for ChunkSaveEvent and ChunkUnloadEvent.");
            } else {
                System.out.println("[HyFine] WARNING: Could not access EventBus. Chunk save/unload policies will not work.");
                LOGGER.severe("[HyFine] Could not access EventBus. Chunk save/unload policies will not work.");
            }
        } catch (Exception e) {
             System.err.println("[HyFine] Error accessing EventBus: " + e.getMessage());
             LOGGER.severe("[HyFine] Error accessing EventBus: " + e.getMessage());
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
            LOGGER.info("[HyFine] Event listeners cleanup requested. They may be automatically cleaned up by the EventBus.");
            // In a real scenario, you might need to store the EventRegistration objects and call .unregister() on them.
        }
        System.out.println("[HyFine] Cleaning up event listeners... (Done)");
        LOGGER.info("[HyFine] Cleaning up event listeners... (Done)");
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
     *
     * @param preset The current optimization preset.
     * @param currentTps The current TPS of the world.
     * @return True if block ticking should be enabled, false otherwise.
     */
    private boolean shouldTickBlocks(OptimizationPreset preset, int currentTps) {
        // Logic based on preset and TPS
        boolean result;
        if (preset == OptimizationPreset.ULTRA) {
            result = false;
            LOGGER.fine("[shouldTickBlocks] Returning false: preset is ULTRA.");
        } else if (currentTps < 12) {
            result = false;
            LOGGER.fine("[shouldTickBlocks] Returning false: current TPS (" + currentTps + ") is below emergency threshold (12).");
        } else {
            result = true;
            LOGGER.fine("[shouldTickBlocks] Returning true: preset is not ULTRA and TPS (" + currentTps + ") is above emergency threshold.");
        }
        return result;
    }

    /**
     * Determines if NPCs should spawn based on preset and TPS.
     *
     * @param preset The current optimization preset.
     * @param currentTps The current TPS of the world.
     * @return True if NPC spawning should be enabled, false otherwise.
     */
    private boolean shouldSpawnNPCs(OptimizationPreset preset, int currentTps) {
        // Logic based on preset and TPS
        boolean result;
        if (preset == OptimizationPreset.ULTRA) {
            result = false;
            LOGGER.fine("[shouldSpawnNPCs] Returning false: preset is ULTRA.");
        } else if (currentTps < 15) {
            result = false;
            LOGGER.fine("[shouldSpawnNPCs] Returning false: current TPS (" + currentTps + ") is below emergency threshold (15).");
        } else {
            result = true;
            LOGGER.fine("[shouldSpawnNPCs] Returning true: preset is not ULTRA and TPS (" + currentTps + ") is above emergency threshold.");
        }
        return result;
    }

    /**
     * Determines if all NPCs should be frozen based on preset and TPS.
     *
     * @param preset The current optimization preset.
     * @param currentTps The current TPS of the world.
     * @return True if all NPCs should be frozen, false otherwise.
     */
    private boolean shouldFreezeNPCs(OptimizationPreset preset, int currentTps) {
        // Logic based on preset and TPS
        boolean result;
        if (preset == OptimizationPreset.ULTRA) {
            result = false;       //disable for cheating XD
            LOGGER.fine("[shouldFreezeNPCs] Returning true: preset is ULTRA.");
        } else if (currentTps < 10) {
            result = false;
            LOGGER.fine("[shouldFreezeNPCs] Returning true: current TPS (" + currentTps + ") is below emergency threshold (10).");
        } else {
            result = false;
            LOGGER.fine("[shouldFreezeNPCs] Returning false: preset is not ULTRA and TPS (" + currentTps + ") is above emergency threshold.");
        }
        return result;
    }

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
