package me.temxs27.hyfine.command;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.EventTitleUtil; 
import me.temxs27.hyfine.HyFine;
import me.temxs27.hyfine.core.OptimizationManager;
import me.temxs27.hyfine.core.PerformanceMonitor;
import me.temxs27.hyfine.preset.OptimizationPreset; 

import javax.annotation.Nonnull;

/**
 * Command to display detailed server performance statistics including TPS, player count, and active preset.
 * Sends a title notification to the player with the gathered stats.
 */
public class HyFineStatsCommand extends AbstractPlayerCommand {

    /**
     * Constructs the command with its name, description, and confirmation requirement.
     *
     * @param name The name of the command (e.g., "hyfinestats").
     * @param description A brief description of what the command does.
     * @param requiresConfirmation Whether the command requires confirmation before execution.
     */
    public HyFineStatsCommand(@Nonnull String name, @Nonnull String description, boolean requiresConfirmation) {
        super(name, description, requiresConfirmation);
    }

    @Override
    protected void execute(@Nonnull CommandContext commandContext,
                           @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref,
                           @Nonnull PlayerRef playerRef,
                           @Nonnull World world) {
        // Get performance data for the current world
        PerformanceMonitor.PerformanceData data = HyFine.getInstance()
                .getPerformanceMonitor()
                .getData(world.getName());

        // Format the statistics string
        String stats = String.format(
                "TPS: %d | Status: %s | Players: %d | Preset: %s",
                data.tps,
                data.getStatus(), // Assumes getStatus() returns a string representation
                data.playerCount,
                OptimizationManager.getPreset().name() // Gets the name of the active preset
        );

        // Show a title notification to the player using the accessible EventTitleUtil
        // Displays the formatted stats string.
        // Note: The 'true' parameter likely indicates if the title should be centered.
        EventTitleUtil.showEventTitleToPlayer(
                playerRef,
                Message.raw("Server Performance"), // Title message
                Message.raw(stats),                // Subtitle message containing the stats
                true                             // Centered flag
        );

        // Optional: Send a chat message as well for confirmation or more details
        playerRef.sendMessage(Message.raw("HyFine Stats: " + stats));
    }
}
