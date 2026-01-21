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

public class HyFineCommand extends AbstractPlayerCommand {

    public HyFineCommand(@Nonnull String name, @Nonnull String description, boolean requiresConfirmation) {
        super(name, description, requiresConfirmation);
    }

    @Override
    protected void execute(@Nonnull CommandContext commandContext,
                           @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref,
                           @Nonnull PlayerRef playerRef,
                           @Nonnull World world)
    {
        int tps = world.getTps();
        String status;

        if (tps >= 19) {
            status = "Excellent";
        } else if (tps >= 17) {
            status = "Good";
        } else if (tps >= 15) {
            status = "Fair";
        } else {
            status = "Poor";
        }

        EventTitleUtil.showEventTitleToPlayer(
                playerRef,
                Message.raw("HyFine Active"),
                Message.raw("TPS: " + tps + " (" + status + ") | Preset: " + OptimizationManager.getPreset().name()),
                true
        );
    }
}