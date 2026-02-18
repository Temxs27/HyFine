package me.temxs27.hyfine.optimization;

import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.event.EventBus; 
import com.hypixel.hytale.server.core.universe.world.events.ecs.ChunkSaveEvent;
import com.hypixel.hytale.server.core.universe.world.events.ecs.ChunkUnloadEvent;

import com.hypixel.hytale.server.core.universe.world.WorldConfig;
import com.hypixel.hytale.server.core.universe.world.ClientEffectWorldSettings; 

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

// --- NEW: Imports for Proactive Despawn ---
import com.hypixel.hytale.server.spawning.world.component.WorldSpawnData;
import com.hypixel.hytale.server.spawning.world.WorldEnvironmentSpawnData;
import com.hypixel.hytale.server.spawning.world.WorldNPCSpawnStat;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap; // Import for Int2ObjectMap (used by WorldEnvironmentSpawnData)

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
    private static final boolean LOW_DISABLE_VISUAL_EFFECTS = false; // Keep effects enabled
    // NEW: Sun settings for LOW preset
    private static final float LOW_SUN_HEIGHT_PERCENT = 100.0f; // Default
    private static final float LOW_SUN_INTENSITY = 0.25f; // Default

    // BALANCED preset settings (default server behavior assumed)
    private static final int BALANCED_ITEM_DESPAWN_TICKS = 3600; // 3 minutes
    private static final int BALANCED_TARGET_TPS = 30; // Default TPS
    private static final boolean BALANCED_ALLOW_UNLOAD = true;
    private static final boolean BALANCED_ALLOW_SAVE = true;
    private static final int BALANCED_CLIENT_VIEW_RADIUS = 4; // Minimum view radius optimization
    private static final boolean BALANCED_DISABLE_VISUAL_EFFECTS = false; // Keep effects enabled
    // NEW: Sun settings for BALANCED preset
    private static final float BALANCED_SUN_HEIGHT_PERCENT = 100.0f; // Default
    private static final float BALANCED_SUN_INTENSITY = 0.25f; // Default

    // ULTRA preset settings
    private static final int ULTRA_ITEM_DESPAWN_TICKS = 1200; // 1 minute
    private static final int ULTRA_TARGET_TPS = 20; // Lower TPS to reduce load
    private static final boolean ULTRA_ALLOW_UNLOAD = false; // Prevent chunk unloading (keep memory high, reduce I/O)
    private static final boolean ULTRA_ALLOW_SAVE = false;   // Prevent chunk saving (keep memory high, reduce I/O)
    private static final int ULTRA_CLIENT_VIEW_RADIUS = 3; // Moderate view radius optimization
    private static final boolean ULTRA_DISABLE_VISUAL_EFFECTS = true; // Disable visual effects
    // NEW: Sun settings for ULTRA preset (more aggressive reduction)
    private static final float ULTRA_SUN_HEIGHT_PERCENT = 0.0f; // Minimize sun visibility
    private static final float ULTRA_SUN_INTENSITY = 0.0f; // Minimize sun intensity

    // --- NEW: Thresholds for proactive despawn based on preset ---
    private static final int HYFINE_LOW_NPC_THRESHOLD = 70;
    private static final int HYFINE_BALANCED_NPC_THRESHOLD = 50;
    private static final int HYFINE_ULTRA_NPC_THRESHOLD = 25;
    private static final int HYFINE_DESPAWN_BATCH_SIZE = 10;

    // --- State for Event-Based Policies ---
    // Map to store the current policy for each world
    private final Map<String, Boolean> savePolicyMap = new ConcurrentHashMap<>();
    private final Map<String, Boolean> unloadPolicyMap = new ConcurrentHashMap<>();


    public OptimizationEngine(HyFine plugin) {
        this.plugin = plugin;
    }

    // --- Conditional Logging Helpers ---
    // All log output is gated behind OptimizationManager.isLoggingEnabled()

    private void logInfo(String msg) {
        if (OptimizationManager.isLoggingEnabled()) {
            LOGGER.info(msg);
        }
    }

    private void logWarning(String msg) {
        if (OptimizationManager.isLoggingEnabled()) {
            LOGGER.warning(msg);
        }
    }

    private void logSevere(String msg) {
        if (OptimizationManager.isLoggingEnabled()) {
            LOGGER.severe(msg);
        }
    }

    private void logFine(String msg) {
        if (OptimizationManager.isLoggingEnabled()) {
            LOGGER.fine(msg);
        }
    }

    private void logPrint(String msg) {
        if (OptimizationManager.isLoggingEnabled()) {
            System.out.println(msg);
        }
    }

    private void logErr(String msg) {
        if (OptimizationManager.isLoggingEnabled()) {
            System.err.println(msg);
        }
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
                logErr("[HyFine] Critical error in optimization loop: " + e.getMessage());
                if (OptimizationManager.isLoggingEnabled()) e.printStackTrace();
            }
        }, "HyFine-Optimization");
        optimizationThread.setDaemon(true);
        optimizationThread.start();

        // --- MAGIC: Initialize additional components ---
        initializeEventListeners(); // Attempt to listen for server events
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
                logErr("[HyFine] Error in optimization loop: " + e.getMessage());
                if (OptimizationManager.isLoggingEnabled()) e.printStackTrace();
            }
        }
    }

    private void applyOptimizations() {
        Universe universe = Universe.get();
        if (universe == null) {
            logWarning("[HyFine] Universe is null, skipping optimization cycle.");
            return;
        }

        OptimizationPreset preset = OptimizationManager.getPreset();
        logInfo("[HyFine] Starting optimization cycle for preset: " + preset.name());

        // Determine base settings based on the preset
        int despawnThreshold;
        int despawnTicks;
        int targetTps;
        boolean allowUnload;
        boolean allowSave;
        // clientViewRadius: -1 means "do not modify" (used for LOW preset)
        int clientViewRadius;
        boolean disableVisualEffects;
        // Variables for sun settings
        float sunHeightPercent;
        float sunIntensity;

        switch (preset) {
            case LOW:
                despawnThreshold = HYFINE_LOW_NPC_THRESHOLD; 
                despawnTicks = LOW_ITEM_DESPAWN_TICKS;
                targetTps = LOW_TARGET_TPS;
                allowUnload = LOW_ALLOW_UNLOAD;
                allowSave = LOW_ALLOW_SAVE;
                clientViewRadius = -1; // LOW: do NOT modify view radius
                disableVisualEffects = LOW_DISABLE_VISUAL_EFFECTS;
                sunHeightPercent = LOW_SUN_HEIGHT_PERCENT;
                sunIntensity = LOW_SUN_INTENSITY;
                aggressiveMode = false;
                break;
            case BALANCED:
                despawnThreshold = HYFINE_BALANCED_NPC_THRESHOLD;
                despawnTicks = BALANCED_ITEM_DESPAWN_TICKS;
                targetTps = BALANCED_TARGET_TPS;
                allowUnload = BALANCED_ALLOW_UNLOAD;
                allowSave = BALANCED_ALLOW_SAVE;
                clientViewRadius = BALANCED_CLIENT_VIEW_RADIUS; // Minimum optimization (4 chunks)
                disableVisualEffects = BALANCED_DISABLE_VISUAL_EFFECTS;
                sunHeightPercent = BALANCED_SUN_HEIGHT_PERCENT;
                sunIntensity = BALANCED_SUN_INTENSITY;
                aggressiveMode = false;
                break;
            case ULTRA:
                despawnThreshold = HYFINE_ULTRA_NPC_THRESHOLD;
                despawnTicks = ULTRA_ITEM_DESPAWN_TICKS;
                targetTps = ULTRA_TARGET_TPS;
                allowUnload = ULTRA_ALLOW_UNLOAD;
                allowSave = ULTRA_ALLOW_SAVE;
                clientViewRadius = ULTRA_CLIENT_VIEW_RADIUS; // Moderate optimization (3 chunks)
                disableVisualEffects = ULTRA_DISABLE_VISUAL_EFFECTS;
                sunHeightPercent = ULTRA_SUN_HEIGHT_PERCENT;
                sunIntensity = ULTRA_SUN_INTENSITY;
                aggressiveMode = true;
                break;
            default:
                // Fallback to BALANCED if preset is unknown
                despawnThreshold = HYFINE_BALANCED_NPC_THRESHOLD;
                despawnTicks = BALANCED_ITEM_DESPAWN_TICKS;
                targetTps = BALANCED_TARGET_TPS;
                allowUnload = BALANCED_ALLOW_UNLOAD;
                allowSave = BALANCED_ALLOW_SAVE;
                clientViewRadius = BALANCED_CLIENT_VIEW_RADIUS;
                disableVisualEffects = BALANCED_DISABLE_VISUAL_EFFECTS;
                sunHeightPercent = BALANCED_SUN_HEIGHT_PERCENT;
                sunIntensity = BALANCED_SUN_INTENSITY;
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
            logInfo("[HyFine] Updated policy maps for world '" + worldName + "': Save=" + allowSave + ", Unload=" + allowUnload);

            // --- 1. Adjust Target TPS based on preset and current performance ---
            int currentTargetTps = world.getTps();
            if (currentTargetTps != targetTps) {
                try {
                    world.execute(() -> {
                        try {
                             world.setTps(targetTps);
                             logInfo("[HyFine] Successfully set TPS to " + targetTps + " for world '" + worldName + "' on ECS thread.");
                        } catch (Exception e) {
                             logSevere("[HyFine] Failed to set TPS for world '" + worldName + "' on ECS thread: " + e.getMessage());
                             if (OptimizationManager.isLoggingEnabled()) e.printStackTrace();
                        }
                    });
                    logInfo("[HyFine] Queued TPS change to " + targetTps + " for world '" + worldName + "' via world.execute().");
                } catch (Exception e) {
                     logErr("[HyFine] Failed to queue TPS change for world '" + worldName + "': " + e.getMessage());
                     logSevere("[HyFine] Failed to queue TPS change for world '" + worldName + "' via world.execute(): " + e.getMessage());
                     if (OptimizationManager.isLoggingEnabled()) e.printStackTrace();
                }
            } else {
                 logFine("[HyFine] TPS for world '" + worldName + "' is already " + targetTps + ", no change needed.");
            }

            // --- NEW: Apply WorldConfig Optimizations ---
            try {
                WorldConfig config = world.getWorldConfig();
                if (config != null) {
                    boolean currentBlockTicking = config.isBlockTicking();
                    boolean currentSpawningNPC = config.isSpawningNPC();
                    boolean currentIsAllNPCFrozen = config.isAllNPCFrozen();
                    boolean currentCanUnloadChunks = config.canUnloadChunks();

                    boolean newBlockTicking = shouldTickBlocks(preset, data.tps);
                    boolean newSpawningNPC = shouldSpawnNPCs(preset, data.tps);
                    boolean newIsAllNPCFrozen = shouldFreezeNPCs(preset, data.tps);
                    boolean newCanUnloadChunks = allowUnload;

                    if (currentBlockTicking != newBlockTicking) {
                        config.setBlockTicking(newBlockTicking);
                        logInfo("[HyFine] Changed blockTicking for world '" + worldName + "' from " + currentBlockTicking + " to " + newBlockTicking);
                    }
                    if (currentSpawningNPC != newSpawningNPC) {
                        config.setSpawningNPC(newSpawningNPC);
                        logInfo("[HyFine] Changed spawningNPC for world '" + worldName + "' from " + currentSpawningNPC + " to " + newSpawningNPC);
                    }
                    if (currentIsAllNPCFrozen != newIsAllNPCFrozen) {
                        config.setIsAllNPCFrozen(newIsAllNPCFrozen);
                        logInfo("[HyFine] Changed isAllNPCFrozen for world '" + worldName + "' from " + currentIsAllNPCFrozen + " to " + newIsAllNPCFrozen);
                    }
                    if (currentCanUnloadChunks != newCanUnloadChunks) {
                        config.setCanUnloadChunks(newCanUnloadChunks);
                        logInfo("[HyFine] Changed canUnloadChunks for world '" + worldName + "' from " + currentCanUnloadChunks + " to " + newCanUnloadChunks);
                    }

                    // --- Apply Client Effect Optimizations ---
                    ClientEffectWorldSettings clientEffects = config.getClientEffects();
                    if (clientEffects != null) {
                        float currentBloomIntensity = clientEffects.getBloomIntensity();
                        float currentBloomPower = clientEffects.getBloomPower();
                        float currentSunshaftIntensity = clientEffects.getSunshaftIntensity();
                        float currentSunHeightPercent = clientEffects.getSunHeightPercent();
                        float currentSunIntensity = clientEffects.getSunIntensity();

                        if (disableVisualEffects) {
                            if (currentBloomIntensity != 0.0f) {
                                clientEffects.setBloomIntensity(0.0f);
                                logInfo("[HyFine] Disabled Bloom Intensity for world '" + worldName + "'");
                            }
                            if (currentBloomPower != 0.0f) {
                                clientEffects.setBloomPower(0.0f);
                                logInfo("[HyFine] Disabled Bloom Power for world '" + worldName + "'");
                            }
                            if (currentSunshaftIntensity != 0.0f) {
                                clientEffects.setSunshaftIntensity(0.0f);
                                logInfo("[HyFine] Disabled Sunshaft Intensity for world '" + worldName + "'");
                            }
                            if (currentSunHeightPercent != sunHeightPercent) {
                                clientEffects.setSunHeightPercent(sunHeightPercent);
                                logInfo("[HyFine] Set SunHeightPercent to " + sunHeightPercent + " for world '" + worldName + "' (preset: " + preset.name() + ")");
                            }
                            if (currentSunIntensity != sunIntensity) {
                                clientEffects.setSunIntensity(sunIntensity);
                                logInfo("[HyFine] Set SunIntensity to " + sunIntensity + " for world '" + worldName + "' (preset: " + preset.name() + ")");
                            }
                        } else {
                            if (currentBloomIntensity == 0.0f) {
                                clientEffects.setBloomIntensity(0.3f);
                                logInfo("[HyFine] Re-enabled Bloom Intensity for world '" + worldName + "' to default");
                            }
                            if (currentBloomPower == 0.0f) {
                                clientEffects.setBloomPower(8.0f);
                                logInfo("[HyFine] Re-enabled Bloom Power for world '" + worldName + "' to default");
                            }
                            if (currentSunshaftIntensity == 0.0f) {
                                clientEffects.setSunshaftIntensity(0.3f);
                                logInfo("[HyFine] Re-enabled Sunshaft Intensity for world '" + worldName + "' to default");
                            }
                            if (currentSunHeightPercent != 100.0f) {
                                clientEffects.setSunHeightPercent(100.0f);
                                logInfo("[HyFine] Re-enabled SunHeightPercent for world '" + worldName + "' to default (100.0f)");
                            }
                            if (currentSunIntensity != 0.25f) {
                                clientEffects.setSunIntensity(0.25f);
                                logInfo("[HyFine] Re-enabled SunIntensity for world '" + worldName + "' to default (0.25f)");
                            }
                        }
                    } else {
                        logWarning("[HyFine] ClientEffectWorldSettings for world '" + worldName + "' is null, skipping visual effect optimization.");
                    }

                    // IMPORTANT: Call markChanged() to notify the server that the config has changed
                    config.markChanged();
                    logPrint("[HyFine] Applied WorldConfig optimizations for world: " + worldName);
                    logInfo("[HyFine] Applied WorldConfig optimizations and called markChanged() for world '" + worldName + "'");
                } else {
                    logWarning("[HyFine] WorldConfig for world '" + worldName + "' is null, skipping.");
                }
            } catch (Exception e) {
                logErr("[HyFine] Failed to modify WorldConfig for world '" + worldName + "': " + e.getMessage());
                logSevere("[HyFine] Failed to modify WorldConfig for world '" + worldName + "': " + e.getMessage());
                if (OptimizationManager.isLoggingEnabled()) e.printStackTrace();
            }

            // --- Apply Player View Radius Optimizations ---
            // Only apply if:
            //   1. clientViewRadius > 0 (LOW preset sets -1 to skip)
            //   2. OptimizationManager.isViewRadiusEnabled() is true (toggled by /hyvoff)
            if (clientViewRadius > 0 && OptimizationManager.isViewRadiusEnabled()) {
                try {
                    final int cvr = clientViewRadius;
                    world.execute(() -> {
                        try {
                            com.hypixel.hytale.component.Store<EntityStore> entityStore = world.getEntityStore().getStore();
                            ComponentType<EntityStore, PlayerRef> playerRefType = PlayerRef.getComponentType();
                            ComponentType<EntityStore, Player> playerType = Player.getComponentType();
                            ComponentType<EntityStore, EntityTrackerSystems.EntityViewer> viewerType = EntityTrackerSystems.EntityViewer.getComponentType();

                            entityStore.forEachEntityParallel(playerRefType, (index, archetypeChunk, commandBuffer) -> {
                                PlayerRef playerRefComponent = archetypeChunk.getComponent(index, playerRefType);
                                if (playerRefComponent != null) {
                                    com.hypixel.hytale.component.Ref<EntityStore> actualPlayerEntityRef = playerRefComponent.getReference();
                                    if (actualPlayerEntityRef != null && actualPlayerEntityRef.isValid()) {
                                        Player playerComponent = entityStore.getComponent(actualPlayerEntityRef, playerType);
                                        if (playerComponent != null) {
                                            EntityTrackerSystems.EntityViewer entityViewerComponent = entityStore.getComponent(actualPlayerEntityRef, viewerType);
                                            if (entityViewerComponent != null) {
                                                int currentRadiusChunks = playerComponent.getClientViewRadius();
                                                int targetRadiusBlocks = cvr * 32;

                                                if (currentRadiusChunks != cvr) {
                                                    playerComponent.setClientViewRadius(cvr);
                                                    entityViewerComponent.viewRadiusBlocks = targetRadiusBlocks;

                                                    ViewRadius viewRadiusPacket = new ViewRadius(targetRadiusBlocks);
                                                    playerRefComponent.getPacketHandler().writeNoCache(viewRadiusPacket);

                                                    logInfo("[HyFine] Changed client view radius for player in world '" + worldName + "' to " + cvr + " chunks (" + targetRadiusBlocks + " blocks)");
                                                }
                                            } else {
                                                logWarning("[HyFine] EntityViewer component not found for player entity ref " + actualPlayerEntityRef.getIndex() + " in world '" + worldName + "'");
                                            }
                                        } else {
                                            logWarning("[HyFine] Player component not found for player entity ref " + actualPlayerEntityRef.getIndex() + " in world '" + worldName + "'");
                                        }
                                    } else {
                                        logFine("[HyFine] Player entity ref is null or invalid for PlayerRef entity ref at index " + index + " in world '" + worldName + "'");
                                    }
                                } else {
                                    logWarning("[HyFine] PlayerRef component not found at index " + index + " in archetype chunk for world '" + worldName + "'");
                                }
                            });

                        } catch (Exception e) {
                             logSevere("[HyFine] Error applying player view radius optimization on ECS thread for world '" + worldName + "': " + e.getMessage());
                             if (OptimizationManager.isLoggingEnabled()) e.printStackTrace();
                        }
                    });
                    logInfo("[HyFine] Queued player view radius optimization for world '" + worldName + "' via world.execute().");
                } catch (Exception e) {
                     logErr("[HyFine] Failed to queue player view radius optimization for world '" + worldName + "': " + e.getMessage());
                     logSevere("[HyFine] Failed to queue player view radius optimization for world '" + worldName + "' via world.execute(): " + e.getMessage());
                     if (OptimizationManager.isLoggingEnabled()) e.printStackTrace();
                }
            }
            // If clientViewRadius <= 0 (LOW preset) or view radius is disabled via /hyvoff, skip view radius modification

            // --- Apply Proactive NPC Despawn Optimization ---
            switch (preset) {
                case LOW:
                    despawnThreshold = HYFINE_LOW_NPC_THRESHOLD;
                    break;
                case BALANCED:
                    despawnThreshold = HYFINE_BALANCED_NPC_THRESHOLD;
                    break;
                case ULTRA:
                    despawnThreshold = HYFINE_ULTRA_NPC_THRESHOLD;
                    break;
                default:
                    despawnThreshold = HYFINE_BALANCED_NPC_THRESHOLD;
                    break;
            }

            try {
                com.hypixel.hytale.component.Store<EntityStore> entityStore = world.getEntityStore().getStore();
                WorldSpawnData worldSpawnData = entityStore.getResource(WorldSpawnData.getResourceType());

                if (worldSpawnData != null) {
                    int currentActualNPCs = worldSpawnData.getActualNPCs();
                    logInfo("[HyFine] World '" + worldName + "' has " + currentActualNPCs + " actual NPCs (threshold: " + despawnThreshold + ").");

                    if (currentActualNPCs > despawnThreshold) {
                        int excessNPCs = currentActualNPCs - despawnThreshold;
                        int toDespawnCount = Math.min(excessNPCs, HYFINE_DESPAWN_BATCH_SIZE);
                        logInfo("[HyFine] Attempting proactive despawn of " + toDespawnCount + " NPCs in world '" + worldName + "' (preset: " + preset.name() + ").");

                        world.execute(() -> {
                            try {
                                int remainingToDespawn = toDespawnCount;
                                int actuallyDespawned = 0;

                                int[] environmentIndexes = worldSpawnData.getWorldEnvironmentSpawnDataIndexes();
                                for (int envIndex : environmentIndexes) {
                                    if (remainingToDespawn <= 0) break;

                                    WorldEnvironmentSpawnData envData = worldSpawnData.getWorldEnvironmentSpawnData(envIndex);
                                    if (envData == null) continue;

                                    Int2ObjectMap<WorldNPCSpawnStat> npcStatMap = envData.getNpcStatMap();
                                    if (npcStatMap != null) {
                                        for (Int2ObjectMap.Entry<WorldNPCSpawnStat> entry : npcStatMap.int2ObjectEntrySet()) {
                                            WorldNPCSpawnStat stat = entry.getValue();
                                            int roleIndex = stat.getRoleIndex();
                                            int actualCountForRole = stat.getActual();

                                            if (actualCountForRole > 0) {
                                                int countToDespawnFromThisRole = Math.min(actualCountForRole, remainingToDespawn);
                                                stat.adjustActual(-countToDespawnFromThisRole);
                                                worldSpawnData.untrackNPC(envIndex, roleIndex, countToDespawnFromThisRole);

                                                remainingToDespawn -= countToDespawnFromThisRole;
                                                actuallyDespawned += countToDespawnFromThisRole;
                                                logInfo("[HyFine] Adjusted actual count down by " + countToDespawnFromThisRole + " for role index " + roleIndex + " in environment " + envIndex + ".");
                                                if (remainingToDespawn <= 0) break;
                                            }
                                        }
                                    } else {
                                        logWarning("[HyFine] npcStatMap is null for environment index " + envIndex + " in world '" + worldName + "'. Cannot iterate roles for despawn.");
                                    }
                                }

                                logInfo("[HyFine] Adjusted global and role-specific NPC counts for " + actuallyDespawned + " NPCs in world '" + worldName + "'. The spawning system should now despawn NPCs based on the reduced counts.");

                            } catch (Exception e) {
                                logSevere("[HyFine] Error during proactive despawn execution on ECS thread for world '" + worldName + "': " + e.getMessage());
                                if (OptimizationManager.isLoggingEnabled()) e.printStackTrace();
                            }
                        });

                    } else {
                        logFine("[HyFine] World '" + worldName + "' actual NPC count (" + currentActualNPCs + ") is within threshold (" + despawnThreshold + "), no proactive despawn needed.");
                    }
                } else {
                    logWarning("[HyFine] WorldSpawnData resource is null for world '" + worldName + "', cannot perform proactive despawn check.");
                }
            } catch (Exception e) {
                 logErr("[HyFine] Failed to access WorldSpawnData for proactive despawn in world '" + worldName + "': " + e.getMessage());
                 logSevere("[HyFine] Failed to access WorldSpawnData for proactive despawn in world '" + worldName + "': " + e.getMessage());
                 if (OptimizationManager.isLoggingEnabled()) e.printStackTrace();
            }
            // --- END: Apply Proactive NPC Despawn Optimization ---


            // --- 2. Apply Emergency/Moderate TPS-based adjustments ---
            if (data.tps < 15) {
                 int emergencyTps = 15;
                 if (world.getTps() > emergencyTps) {
                     try {
                         world.execute(() -> {
                             try {
                                 world.setTps(emergencyTps);
                                 logWarning("[HyFine] EMERGENCY: Set TPS to " + emergencyTps + " for world '" + worldName + "' due to low TPS (" + data.tps + ") on ECS thread.");
                             } catch (Exception e) {
                                 logSevere("[HyFine] Failed to set emergency TPS for world '" + worldName + "' on ECS thread: " + e.getMessage());
                                 if (OptimizationManager.isLoggingEnabled()) e.printStackTrace();
                             }
                         });
                         logInfo("[HyFine] Queued emergency TPS change to " + emergencyTps + " for world '" + worldName + "' via world.execute().");
                     } catch (Exception e) {
                          logErr("[HyFine] Failed to queue emergency TPS change for world '" + worldName + "': " + e.getMessage());
                          logSevere("[HyFine] Failed to queue emergency TPS change for world '" + worldName + "' via world.execute(): " + e.getMessage());
                          if (OptimizationManager.isLoggingEnabled()) e.printStackTrace();
                     }
                 }
                 if (this.itemDespawnTicks > 1200) {
                      this.itemDespawnTicks = 1200;
                      logPrint("[HyFine] Emergency: Set itemDespawnTicks to 1200 due to low TPS (" + data.tps + ") in world: " + worldName);
                      logInfo("[HyFine] EMERGENCY: Set itemDespawnTicks to 1200 for world '" + worldName + "' due to low TPS (" + data.tps + ")");
                 }
                 savePolicyMap.put(worldName, false);
                 unloadPolicyMap.put(worldName, false);
                 logInfo("[HyFine] EMERGENCY: Updated policy maps for world '" + worldName + "': Save=false, Unload=false");
            } else if (data.tps < 18 && preset == OptimizationPreset.BALANCED) {
                 int moderateTps = 20;
                 if (world.getTps() > moderateTps) {
                     try {
                         world.execute(() -> {
                             try {
                                 world.setTps(moderateTps);
                                 logInfo("[HyFine] MODERATE: Set TPS to " + moderateTps + " for world '" + worldName + "' due to moderate low TPS (" + data.tps + ") on ECS thread.");
                             } catch (Exception e) {
                                 logSevere("[HyFine] Failed to set moderate TPS for world '" + worldName + "' on ECS thread: " + e.getMessage());
                                 if (OptimizationManager.isLoggingEnabled()) e.printStackTrace();
                             }
                         });
                         logInfo("[HyFine] Queued moderate TPS change to " + moderateTps + " for world '" + worldName + "' via world.execute().");
                     } catch (Exception e) {
                          logErr("[HyFine] Failed to queue moderate TPS change for world '" + worldName + "': " + e.getMessage());
                          logSevere("[HyFine] Failed to queue moderate TPS change for world '" + worldName + "' via world.execute(): " + e.getMessage());
                          if (OptimizationManager.isLoggingEnabled()) e.printStackTrace();
                     }
                 }
            }

            // Update internal state influenced by preset
            this.itemDespawnTicks = despawnTicks;
            this.estimatedEntityCount = data.entityCount;
        }
        logInfo("[HyFine] Completed optimization cycle for preset: " + preset.name());
    }

    // --- MAGIC: Placeholder Methods for Advanced Features ---

    /**
     * Initializes listeners for server events (e.g., ChunkUnloadEvent, ChunkSaveEvent).
     * This requires access to the EventBus.
     */
    private void initializeEventListeners() {
        try {
            this.eventBus = HytaleServer.get().getEventBus();
            if (this.eventBus != null) {
                // Register ChunkSaveEvent Listener
                this.eventBus.register(ChunkSaveEvent.class, event -> {
                    World eventWorld = event.getChunk().getWorld();
                    String worldName = eventWorld.getName();
                    Boolean allowSave = savePolicyMap.get(worldName);
                    if (allowSave != null && !allowSave) {
                        event.setCancelled(true);
                        logPrint("[HyFine] Cancelled chunk save in world '" + worldName + "' based on policy.");
                        logInfo("[HyFine] Cancelled chunk save event for chunk in world '" + worldName + "'");
                    }
                });

                // Register ChunkUnloadEvent Listener
                this.eventBus.register(ChunkUnloadEvent.class, event -> {
                    World eventWorld = event.getChunk().getWorld();
                    String worldName = eventWorld.getName();
                    Boolean allowUnload = unloadPolicyMap.get(worldName);
                    if (allowUnload != null && !allowUnload) {
                        event.setCancelled(true);
                        logPrint("[HyFine] Cancelled chunk unload in world '" + worldName + "' based on policy.");
                        logInfo("[HyFine] Cancelled chunk unload event for chunk in world '" + worldName + "'");
                    }
                });
                logPrint("[HyFine] Registered event listeners for chunk save/unload.");
                logInfo("[HyFine] Successfully registered event listeners for ChunkSaveEvent and ChunkUnloadEvent.");
            } else {
                logPrint("[HyFine] WARNING: Could not access EventBus. Chunk save/unload policies will not work.");
                logSevere("[HyFine] Could not access EventBus. Chunk save/unload policies will not work.");
            }
        } catch (Exception e) {
             logErr("[HyFine] Error accessing EventBus: " + e.getMessage());
             logSevere("[HyFine] Error accessing EventBus: " + e.getMessage());
             if (OptimizationManager.isLoggingEnabled()) e.printStackTrace();
        }
    }

    /**
     * Cleans up registered event listeners.
     */
    private void cleanupEventListeners() {
        if (this.eventBus != null) {
            logPrint("[HyFine] Event listeners are active. They may be automatically cleaned up by the EventBus upon shutdown.");
            logInfo("[HyFine] Event listeners cleanup requested. They may be automatically cleaned up by the EventBus.");
        }
        logPrint("[HyFine] Cleaning up event listeners... (Done)");
        logInfo("[HyFine] Cleaning up event listeners... (Done)");
    }


    /**
     * Determines if blocks should tick based on preset and TPS.
     */
    private boolean shouldTickBlocks(OptimizationPreset preset, int currentTps) {
        boolean result;
        if (preset == OptimizationPreset.ULTRA) {
            result = false;
            logFine("[shouldTickBlocks] Returning false: preset is ULTRA.");
        } else if (currentTps < 12) {
            result = false;
            logFine("[shouldTickBlocks] Returning false: current TPS (" + currentTps + ") is below emergency threshold (12).");
        } else {
            result = true;
            logFine("[shouldTickBlocks] Returning true: preset is not ULTRA and TPS (" + currentTps + ") is above emergency threshold.");
        }
        return result;
    }

    /**
     * Determines if NPCs should spawn based on preset and TPS.
     */
    private boolean shouldSpawnNPCs(OptimizationPreset preset, int currentTps) {
        boolean result;
        if (preset == OptimizationPreset.ULTRA) {
            result = false;
            logFine("[shouldSpawnNPCs] Returning false: preset is ULTRA.");
        } else if (currentTps < 15) {
            result = false;
            logFine("[shouldSpawnNPCs] Returning false: current TPS (" + currentTps + ") is below emergency threshold (15).");
        } else {
            result = true;
            logFine("[shouldSpawnNPCs] Returning true: preset is not ULTRA and TPS (" + currentTps + ") is above emergency threshold.");
        }
        return result;
    }

    /**
     * Determines if all NPCs should be frozen based on preset and TPS.
     */
    private boolean shouldFreezeNPCs(OptimizationPreset preset, int currentTps) {
        boolean result;
        if (preset == OptimizationPreset.ULTRA) {
            result = false;
            logFine("[shouldFreezeNPCs] Returning false: preset is ULTRA, but freezing is disabled in presets.");
        } else if (currentTps < 10) {
            result = false;
            logFine("[shouldFreezeNPCs] Returning false: current TPS (" + currentTps + ") is below emergency threshold (10), but freezing is disabled in presets.");
        } else {
            result = false;
            logFine("[shouldFreezeNPCs] Returning false: preset is not ULTRA and TPS (" + currentTps + ") is above emergency threshold.");
        }
        return result;
    }

    // --- Getters for State Influenced by Presets and Metrics ---

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
