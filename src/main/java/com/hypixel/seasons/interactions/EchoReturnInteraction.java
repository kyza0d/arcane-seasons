package com.hypixel.seasons.interactions;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.WaitForDataFrom;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.teleport.PendingTeleport;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.seasons.echo.EchoManager;

public class EchoReturnInteraction extends SimpleInstantInteraction {

    public static final BuilderCodec<EchoReturnInteraction> CODEC = BuilderCodec.builder(
            EchoReturnInteraction.class,
            EchoReturnInteraction::new,
            SimpleInstantInteraction.CODEC
    ).build();

    @Override
    public WaitForDataFrom getWaitForDataFrom() {
        return WaitForDataFrom.Server;
    }

    @Override
    protected void firstRun(InteractionType type, InteractionContext context,
                           CooldownHandler cooldownHandler) {
        Ref<EntityStore> ref = context.getEntity();
        CommandBuffer<EntityStore> commandBuffer = context.getCommandBuffer();

        Player playerComponent = commandBuffer.getComponent(ref, Player.getComponentType());
        if (playerComponent == null || playerComponent.isWaitingForClientReady()) {
            return;
        }

        Archetype<EntityStore> archetype = commandBuffer.getArchetype(ref);
        if (archetype.contains(Teleport.getComponentType()) ||
            archetype.contains(PendingTeleport.getComponentType())) {
            return;
        }

        World currentWorld = commandBuffer.getExternalData().getWorld();
        Store<EntityStore> store = ref.getStore();

        commandBuffer.run(s -> {
            EchoManager.get().returnFromEcho(ref, s, currentWorld);
        });
    }
}
