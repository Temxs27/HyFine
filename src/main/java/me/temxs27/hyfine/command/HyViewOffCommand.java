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

import javax.annotation.Nonnull;

/**
 * Command to toggle the client view radius optimization.
 * When disabled, HyFine will not modify player view radius.
 * When enabled (default), view radius is adjusted based on the active preset.
 */
public class HyViewOffCommand extends AbstractPlayerCommand {

    public HyViewOffCommand(@Nonnull String name, @Nonnull String description, boolean requiresConfirmation) {
        super(name, description, requiresConfirmation);
    }

    @Override
    protected void execute(@Nonnull CommandContext commandContext,
                           @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref,
                           @Nonnull PlayerRef playerRef,
                           @Nonnull World world) {
        boolean newState = OptimizationManager.toggleViewRadius();
        String stateText = newState ? "ENABLED" : "DISABLED";

        EventTitleUtil.showEventTitleToPlayer(
                playerRef,
                Message.raw("View Radius Optimization"),
                Message.raw("View Radius " + stateText),
                true
        );

        playerRef.sendMessage(Message.raw("HyFine: View Radius Optimization " + stateText));
    }
}
