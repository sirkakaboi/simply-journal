package dev.waypointjournal;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import org.slf4j.LoggerFactory;

public final class PortCheck {
    private static int step;
    private static long since = System.nanoTime();
    private static String screenshot;

    public static void tick() {
        var mc = Minecraft.getInstance();
        mc.options.pauseOnLostFocus = false;
        if (mc.screen instanceof WelcomeScreen) mc.screen.onClose();
        if (step == 0 && mc.screen instanceof TitleScreen && mc.getOverlay() == null && mc.isGameLoadFinished()) {
            mc.options.guiScale().set(3);
            mc.options.renderDistance().set(4);
            mc.resizeDisplay();
            var settings = new LevelSettings("Simply Journal port check", GameType.CREATIVE, false, Difficulty.PEACEFUL, true, new GameRules(), WorldDataConfiguration.DEFAULT);
            mc.createWorldOpenFlows().createFreshLevel("journal-check-" + System.currentTimeMillis(), settings,
                new WorldOptions(2026L, false, false), registries -> registries.registryOrThrow(net.minecraft.core.registries.Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(), mc.screen);
            next();
        }
        if (mc.player == null || mc.level == null) return;
        switch (step) {
            case 1 -> {
                if (!after(1000) || mc.screen != null) return;
                mc.player.getAbilities().flying = true;
                mc.setScreen(new JournalScreen());
                next();
            }
            case 2 -> {
                if (!after(1800)) return;
                click("New", 0);
                title(0).setValue("Home camp");
                click("New", 1);
                title(1).setValue("River crossing");
                Journal.addAt(2, false).name = "Hill lookout";
                Journal.addAt(3, false).name = "Forest edge";
                screenshot = "port-book.png";
                next();
            }
            case 3 -> { if (after(300)) { click(">", 0); next(); } }
            case 4 -> { if (after(240)) { screenshot = "port-turn.png"; next(); } }
            case 5 -> {
                if (!after(750)) return;
                if (!title(0).getValue().equals("Hill lookout")) throw new IllegalStateException("Page turn lost content");
                var home = Journal.waypoints().getFirst();
                Journal.disconnect();
                Journal.connect(mc);
                if (!Journal.waypoints().getFirst().id.equals(home.id)) throw new IllegalStateException("Journal did not persist");
                Journal.selectWorld("server:localhost:25565");
                if (!Journal.waypoints().isEmpty()) throw new IllegalStateException("Worlds shared waypoints");
                Journal.connect(mc);
                mc.setScreen(null);
                home = Journal.waypoints().getFirst();
                home.y = 300;
                Navigation.start(home);
                next();
            }
            case 6 -> { if (after(1400)) { screenshot = "port-above.png"; next(); } }
            case 7 -> { if (after(300)) { Journal.tracked().y = -200; next(); } }
            case 8 -> { if (after(1000)) { screenshot = "port-below.png"; next(); } }
            case 9 -> {
                if (!after(400)) return;
                LoggerFactory.getLogger("Simply Journal").info("SIMPLY_JOURNAL_PORT_QA_COMPLETE");
                mc.stop();
                next();
            }
        }
    }

    private static boolean after(long ms) { return System.nanoTime() - since > ms * 1_000_000L; }
    private static void next() { step++; since = System.nanoTime(); }
    private static EditBox title(int index) {
        return Minecraft.getInstance().screen.children().stream().filter(c -> c instanceof EditBox box && box.getMessage().getString().equals("Waypoint name"))
            .map(c -> (EditBox) c).skip(index).findFirst().orElseThrow();
    }
    private static void click(String name, int index) {
        var screen = (JournalScreen) Minecraft.getInstance().screen;
        var button = screen.children().stream().filter(c -> c instanceof Button b && b.getMessage().getString().equals(name))
            .map(c -> (Button) c).skip(index).findFirst().orElseThrow();
        var position = screen.screenPosition(button.getX() < 0 ? 0 : 1, button.getX() + button.getWidth() / 2F, button.getY() + button.getHeight() / 2F);
        screen.mouseClicked(position.x, position.y, 0);
        screen.mouseReleased(position.x, position.y, 0);
    }
    public static void capture() {
        if (screenshot == null) return;
        var mc = Minecraft.getInstance();
        Screenshot.grab(mc.gameDirectory, screenshot, mc.getMainRenderTarget(), message -> {});
        screenshot = null;
    }
}
