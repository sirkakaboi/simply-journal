package dev.waypointjournal;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

public final class LoaderClient {
    public static final KeyMapping OPEN = new KeyMapping("key.waypointjournal.open", InputConstants.Type.KEYSYM,
        InputConstants.KEY_SEMICOLON, KeyMapping.Category.register(Identifier.fromNamespaceAndPath("waypointjournal", "journal")));

    private LoaderClient() {}

    public static void tick(Minecraft client) {
        WelcomeScreen.showIfNeeded(client);
        if (client.level == null || client.player == null) Journal.disconnect();
        else {
            Journal.connect(client);
            Journal.tick();
        }
        while (OPEN.consumeClick()) {
            if (client.gui.screen() == null && client.player != null && !client.player.isDeadOrDying()) {
                client.gui.setScreen(new JournalScreen());
            }
        }
        Navigation.tick(client);
    }
}
