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
 * Command to activate the LOW optimization preset.
 * Sends a title notification to the player confirming the change.
 */
public class HyFineLowCommand extends AbstractPlayerCommand {

    /**
     * Constructs the command with its name, description, and confirmation requirement.
     *
     * @param name The name of the command (e.g., "hyfinelow").
     * @param description A brief description of what the command does.
     * @param requiresConfirmation Whether the command requires confirmation before execution.
     */
    public HyFineLowCommand(@Nonnull String name, @Nonnull String description, boolean requiresConfirmation) {
        super(name, description, requiresConfirmation);
    }

    @Override
    protected void execute(@Nonnull CommandContext commandContext,
                           @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref,
                           @Nonnull PlayerRef playerRef,
                           @Nonnull World world) {
        // Apply the LOW optimization preset
        OptimizationManager.setPreset(OptimizationPreset.LOW);

        // Show a title notification to the player using the accessible EventTitleUtil
        // Note: The 'true' parameter likely indicates if the title should be centered.
        EventTitleUtil.showEventTitleToPlayer(
                playerRef,
                Message.raw("LOW Mode Activated"), // Title message
                Message.raw("Preset Changed"),     // Subtitle message
                true                             // Centered flag
        );

        // Optional: Send a chat message as well for confirmation
        playerRef.sendMessage(Message.raw("HyFine: LOW preset activated."));
    }
}
