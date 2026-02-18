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
 * Command to toggle HyFine logging output.
 * When enabled, all HyFine log messages will be printed to the console.
 * When disabled (default), logs are suppressed for a cleaner console.
 */
public class HyLogCommand extends AbstractPlayerCommand {

    public HyLogCommand(@Nonnull String name, @Nonnull String description, boolean requiresConfirmation) {
        super(name, description, requiresConfirmation);
    }

    @Override
    protected void execute(@Nonnull CommandContext commandContext,
                           @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref,
                           @Nonnull PlayerRef playerRef,
                           @Nonnull World world) {
        boolean newState = OptimizationManager.toggleLogging();
        String stateText = newState ? "ENABLED" : "DISABLED";

        EventTitleUtil.showEventTitleToPlayer(
                playerRef,
                Message.raw("HyFine Logging"),
                Message.raw("Logging " + stateText),
                true
        );

        playerRef.sendMessage(Message.raw("HyFine: Logging " + stateText));
    }
}
