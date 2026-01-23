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
import me.temxs27.hyfine.core.OptimizationManager;
import me.temxs27.hyfine.preset.OptimizationPreset; 

import javax.annotation.Nonnull;

/**
 * Command to display the current TPS status and active optimization preset to the player.
 * Sends a title notification showing the TPS and preset.
 */
public class HyFineCommand extends AbstractPlayerCommand {

    /**
     * Constructs the command with its name, description, and confirmation requirement.
     *
     * @param name The name of the command (e.g., "hyfine").
     * @param description A brief description of what the command does.
     * @param requiresConfirmation Whether the command requires confirmation before execution.
     */
    public HyFineCommand(@Nonnull String name, @Nonnull String description, boolean requiresConfirmation) {
        super(name, description, requiresConfirmation);
    }

    @Override
    protected void execute(@Nonnull CommandContext commandContext,
                           @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref,
                           @Nonnull PlayerRef playerRef,
                           @Nonnull World world) {
        // Get the current TPS of the world
        int tps = world.getTps();
        String status;

        // Determine a human-readable status based on the TPS value
        if (tps >= 19) {
            status = "Excellent";
        } else if (tps >= 17) {
            status = "Good";
        } else if (tps >= 15) {
            status = "Fair";
        } else {
            status = "Poor";
        }

        // Show a title notification to the player using the accessible EventTitleUtil
        // Displays the current TPS, its status, and the active optimization preset.
        // Note: The 'true' parameter likely indicates if the title should be centered.
        EventTitleUtil.showEventTitleToPlayer(
                playerRef,
                Message.raw("HyFine Active"), // Title message
                Message.raw("TPS: " + tps + " (" + status + ") | Preset: " + OptimizationManager.getPreset().name()), // Subtitle message
                true // Centered flag
        );

        // Optional: Send a chat message as well for confirmation or more details
        playerRef.sendMessage(Message.raw("HyFine Status: TPS=" + tps + ", Status=" + status + ", Preset=" + OptimizationManager.getPreset().name()));
    }
}
