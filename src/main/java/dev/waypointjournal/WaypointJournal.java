package dev.waypointjournal;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

public final class WaypointJournal implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        KeyMapping open = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.waypointjournal.open", InputConstants.Type.KEYBOARD, InputConstants.KEY_SEMICOLON,
            KeyMapping.Category.register(Identifier.fromNamespaceAndPath("waypointjournal", "journal"))));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            WelcomeScreen.showIfNeeded(client);
            if (client.level == null || client.player == null) {
                Journal.disconnect();
            } else {
                Journal.connect(client);
                Journal.tick();
            }
            while (open.consumeClick()) {
                if (client.gui.screen() == null && client.player != null && !client.player.isDeadOrDying()) {
                    client.gui.setScreen(new JournalScreen());
                }
            }
            Navigation.tick(client);
        });
        var xaero = FabricLoader.getInstance().getModContainer("xaerominimap");
        if (xaero.isPresent() && xaero.get().getMetadata().getVersion().getFriendlyString().equals("26.6.2")) {
            ClientTickEvents.END_CLIENT_TICK.register(XaeroCompat::tick);
        }
    }
}
