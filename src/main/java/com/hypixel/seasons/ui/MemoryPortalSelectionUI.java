package com.hypixel.seasons.ui;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.seasons.echo.Echo;
import com.hypixel.seasons.echo.EchoManager;

public class MemoryPortalSelectionUI extends InteractiveCustomUIPage<MemoryPortalSelectionUI.SelectionData> {

    private final World world;

    public MemoryPortalSelectionUI(PlayerRef playerRef, CustomPageLifetime lifetime, World world) {
        super(playerRef, lifetime, SelectionData.CODEC);
        this.world = world;
    }

    @Override
    public void build(Ref<EntityStore> ref, UICommandBuilder uiCommandBuilder,
                      UIEventBuilder uiEventBuilder, Store<EntityStore> store) {
        uiCommandBuilder.append("Pages/MemoryPortalSelection.ui");

        registerEventBindings(uiEventBuilder);
    }

    private void registerEventBindings(UIEventBuilder uiEventBuilder) {
        for (Echo echo : Echo.values()) {
            String echoName = echo.getId().replace("_echo", "");
            String buttonId = "#" + capitalize(echoName) + "EchoButton";
            uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, buttonId,
                new EventData().append("Action", "SelectEcho").append("EchoId", echo.getId()));
        }
    }

    private String capitalize(String str) {
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }

    @Override
    public void handleDataEvent(Ref<EntityStore> ref, Store<EntityStore> store,
                                SelectionData data) {
        super.handleDataEvent(ref, store, data);

        if ("SelectEcho".equals(data.action) && data.echoId != null) {
            Echo selectedEcho = Echo.getById(data.echoId);
            if (selectedEcho != null) {
                Player playerComponent = store.getComponent(ref, Player.getComponentType());
                if (playerComponent != null) {
                    EchoManager.get().teleportToEcho(selectedEcho, ref, store, world);
                    this.close();
                }
            }
            return;
        }

        this.sendUpdate();
    }

    public static class SelectionData {
        static final String KEY_ACTION = "Action";
        static final String KEY_ECHO_ID = "EchoId";

        public static final BuilderCodec<SelectionData> CODEC =
            BuilderCodec.<SelectionData>builder(SelectionData.class, SelectionData::new)
                .addField(new KeyedCodec<>(KEY_ACTION, Codec.STRING),
                    (data, s) -> data.action = s, data -> data.action)
                .addField(new KeyedCodec<>(KEY_ECHO_ID, Codec.STRING),
                    (data, s) -> data.echoId = s, data -> data.echoId)
                .build();

        public String action;
        public String echoId;
    }
}
