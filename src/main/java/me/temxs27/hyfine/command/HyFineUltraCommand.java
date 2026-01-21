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

public class HyFineUltraCommand extends AbstractPlayerCommand {

    public HyFineUltraCommand(@Nonnull String name, @Nonnull String description, boolean requiresConfirmation) {
        super(name, description, requiresConfirmation);
    }

    @Override
    protected void execute(@Nonnull CommandContext commandContext,
                           @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref,
                           @Nonnull PlayerRef playerRef,
                           @Nonnull World world)
    {
        OptimizationManager.setPreset(OptimizationPreset.ULTRA);
        EventTitleUtil.showEventTitleToPlayer(
                playerRef,
                Message.raw("ULTRA Mode Activated"),
                Message.raw("Preset Changed"),
                true
        );
    }
}