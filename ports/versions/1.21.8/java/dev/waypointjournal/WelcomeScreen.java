package dev.waypointjournal;

import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class WelcomeScreen extends Screen {
    private static final Path FILE = Minecraft.getInstance().gameDirectory.toPath().resolve("config/simply-journal.json");
    private static final String GUIDE = "Thanks for trying Simply Journal. It's still very early, so you may find bugs. Thanks for supporting it!\n\n"
        + "Press ; to open your journal in a world. Esc closes it. You can change the key in Controls. New saves your position. Click a title, note, or coordinate to edit it.\n\n"
        + "Go! shows the arrow and distance. Stop ends navigation.\n\n"
        + "Uncheck an axis to ignore it. With Y off, height doesn't matter. Copy copies that number. Reset restores its saved position.\n\n"
        + "Use < and > or drag to turn pages. Click Delete twice within 3 seconds to remove a waypoint.";
    private static boolean checked;
    private static boolean seen;
    private int textWidth;
    private int top;

    public WelcomeScreen() { super(Component.literal("Welcome to Simply Journal")); }

    public static void showIfNeeded(Minecraft client) {
        if (!(client.screen instanceof TitleScreen) || client.getOverlay() != null || !client.isGameLoadFinished()) return;
        if (!checked) {
            checked = true;
            if (Files.exists(FILE)) {
                try {
                    seen = JsonParser.parseString(Files.readString(FILE)).getAsJsonObject().get("welcomeSeen").getAsBoolean();
                } catch (IOException | RuntimeException error) {
                    LoggerFactory.getLogger("Simply Journal").warn("Couldn't read the welcome preference.", error);
                }
            }
        }
        if (!seen) client.setScreen(new WelcomeScreen());
    }

    @Override
    protected void init() {
        textWidth = Math.min(420, width - 32);
        int textHeight = font.split(Component.literal(GUIDE), textWidth).size() * 9;
        top = Math.max(12, (height - textHeight - 66) / 2);
        int buttonY = Math.min(height - 30, top + textHeight + 42);
        addRenderableWidget(Button.builder(Component.literal("Okay"), button -> onClose()).bounds(width / 2 - 105, buttonY, 100, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Alr"), button -> onClose()).bounds(width / 2 + 5, buttonY, 100, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mx, int my, float delta) {
        super.render(graphics, mx, my, delta);
        graphics.drawCenteredString(font, title, width / 2, top, 0xFFFFFFFF);
        graphics.drawWordWrap(font, Component.literal(GUIDE), (width - textWidth) / 2, top + 24, textWidth, 0xFFD8D8D8, false);
    }

    @Override
    public void onClose() {
        seen = true;
        try {
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, "{\"welcomeSeen\":true}\n");
        } catch (IOException error) {
            LoggerFactory.getLogger("Simply Journal").warn("Couldn't save the welcome preference.", error);
        }
        minecraft.setScreen(new TitleScreen());
    }
}
