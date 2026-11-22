package dev.waypointjournal;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import org.joml.Vector2f;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;

public final class ReleaseCheck {
    private static final long STARTED = System.nanoTime();
    private static boolean started;
    private static long welcomeAt;
    private static int step;
    private static long since = System.nanoTime();
    private static String screenshot;
    private static boolean ready;

    private ReleaseCheck() {}

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (System.getenv("WAYPOINT_JOURNAL_PLAYTEST") != null) { playtest(mc); return; }
        mc.options.pauseOnLostFocus = false;
        if (System.nanoTime() - STARTED > 180_000_000_000L) throw new IllegalStateException("Release check timed out at step " + step);
        if (mc.gui.screen() instanceof WelcomeScreen) {
            if (welcomeAt == 0) { welcomeAt = System.nanoTime(); screenshot = "welcome"; }
            if (System.nanoTime() - welcomeAt > 750_000_000L) {
                require(button("Okay", 0) != null && button("Alr", 0) != null, "Both welcome choices exist");
                click(System.getenv("SJ_WELCOME_BUTTON") == null ? "Okay" : System.getenv("SJ_WELCOME_BUTTON"), 0);
                require(mc.gui.screen() instanceof TitleScreen, "Welcome choice opens main menu");
                require(Files.exists(mc.gameDirectory.toPath().resolve("config").resolve("simply-journal.json")), "Welcome choice is saved");
            }
            return;
        }
        if (!started && mc.isGameLoadFinished() && mc.gui.overlay() == null && mc.gui.screen() instanceof TitleScreen) {
            started = true;
            mc.options.guiScale().set(3);
            mc.options.renderDistance().set(5);
            mc.resizeGui();
            var settings = new LevelSettings("Simply Journal release check", GameType.CREATIVE,
                new LevelSettings.DifficultySettings(Difficulty.PEACEFUL, false, false), true, WorldDataConfiguration.DEFAULT);
            mc.createWorldOpenFlows().createFreshLevel("release-check-" + System.currentTimeMillis(), settings,
                new WorldOptions(2026L, false, false), WorldPresets::createTestWorldDimensions, mc.gui.screen());
            advance();
            return;
        }
        if (mc.player == null || mc.level == null) return;
        if (step == 1 && mc.gui.screen() != null && mc.gui.screen().getClass().getName().startsWith("xaero.")) mc.gui.setScreen(null);
        switch (step) {
            case 1 -> {
                if (!after(2000) || mc.gui.screen() != null) return;
                mc.player.getAbilities().flying = true;
                command("gamerule send_command_feedback false");
                command("time set noon");
                command("weather clear");
                command("tp @a 0.5 4 0.5 0 0");
                advance();
            }
            case 2 -> {
                if (!after(600) || Math.abs(mc.player.getX() - .5) > .1) return;
                KeyMapping key = KeyMapping.get("key.waypointjournal.open");
                require(key.getDefaultKey().getValue() == InputConstants.KEY_SEMICOLON, "Semicolon binding");
                KeyMapping.click(key.getDefaultKey());
                advance();
            }
            case 3 -> {
                if (!after(1800)) return;
                require(mc.gui.screen() instanceof JournalScreen, "Key opens journal");
                click("New", 0);
                title(0).setValue("Home camp");
                description(0).setValue("Bring wood for the roof.");
                coordinate("X").setValue("1,842");
                require(Journal.waypoints().getFirst().x == 1842, "Coordinate editing accepts grouped numbers");
                click("Copy", 0);
                require(mc.keyboardHandler.getClipboard().equals("1842"), "Copy uses the coordinate value");
                click("Reset", 0);
                require(Journal.waypoints().getFirst().x == 0, "Reset restores creation position");
                mc.gui.screen().setFocused(coordinate("X"));
                coordinate("X").setValue("invalid");
                click("Copy", 0);
                require(Journal.waypoints().getFirst().x == 0 && mc.keyboardHandler.getClipboard().equals("0"), "Invalid coordinate preserves saved value");
                command("tp @a 30.5 4 20.5 0 0");
                advance();
            }
            case 4 -> {
                if (!after(600) || Math.abs(mc.player.getX() - 30.5) > .1) return;
                click("New", 1);
                title(1).setValue("River crossing");
                description(1).setValue("Follow the river.");
                require(title(0).getValue().equals("Home camp"), "Saving stays on spread");
                screenshot = "coordinate-controls";
                click("Go!", 0);
                advance();
            }
            case 5 -> {
                if (!after(2400)) return;
                require(mc.gui.screen() == null && Journal.tracked() == Journal.waypoints().getFirst(), "Go starts navigation and closes book");
                float target = (float) Math.toDegrees(Math.atan2(30, -20));
                require(Math.abs(net.minecraft.util.Mth.wrapDegrees(mc.player.getYRot() - target)) < 2, "Camera guide turns toward arrow");
                checkMarker(true);
                screenshot = "navigation";
                mc.player.setYRot(25);
                advance();
            }
            case 6 -> {
                if (!after(1000)) return;
                require(Math.abs(net.minecraft.util.Mth.wrapDegrees(mc.player.getYRot() - 25)) < 1, "Camera guide releases control");
                mc.gui.setScreen(new JournalScreen());
                advance();
            }
            case 7 -> {
                if (!after(1800)) return;
                clickAt(0, -84, -18);
                require(!Journal.waypoints().getFirst().zEnabled, "Z checkbox disables Z");
                mc.gui.setScreen(null);
                command("tp @a 0.5 4 200.5");
                advance();
            }
            case 8 -> {
                if (!after(1500)) return;
                require(Journal.tracked() != null, "Arrived holds before fading");
                screenshot = "arrived";
                advance();
            }
            case 9 -> {
                if (!after(4200)) return;
                require(Journal.tracked() == null, "Ignored Z permits arrival at any Z and stops navigation");
                checkMarker(false);
                mc.gui.setScreen(new JournalScreen());
                advance();
            }
            case 10 -> {
                if (!after(1800)) return;
                clickAt(0, -84, -18);
                clickAt(0, -84, -33);
                require(!Journal.waypoints().getFirst().yEnabled && Journal.waypoints().getFirst().zEnabled, "Y checkbox ignores height");
                mc.gui.setScreen(null);
                command("tp @a 0.5 100 0.5");
                advance();
            }
            case 11 -> {
                if (!after(500) || Math.abs(mc.player.getY() - 100) > .1) return;
                Navigation.start(Journal.waypoints().getFirst());
                advance();
            }
            case 12 -> {
                if (!after(5700)) return;
                require(Journal.tracked() == null, "Ignored Y permits arrival at any height");
                mc.gui.setScreen(new JournalScreen());
                advance();
            }
            case 13 -> {
                if (!after(1800)) return;
                click(">", 0);
                advance();
            }
            case 14 -> {
                if (!after(1000)) return;
                require(mc.gui.screen().children().stream().noneMatch(c -> c instanceof EditBox), "Can turn to blank pages");
                click("New", 0);
                title(0).setValue("Hill lookout");
                require(Journal.waypoints().get(2).name.equals("Hill lookout"), "New uses selected page");
                click(">", 0);
                advance();
            }
            case 15 -> {
                if (!after(1000)) return;
                click("New", 1);
                title(0).setValue("Forest edge");
                require(Journal.waypoints().get(3) == null && Journal.waypoints().get(4) == null, "Blank slots remain empty");
                swipe(0, 190);
                advance();
            }
            case 16 -> {
                if (!after(1000)) return;
                require(title(0).getValue().equals("Hill lookout"), "Drag goes to previous spread");
                swipe(1, -190);
                advance();
            }
            case 17 -> {
                if (!after(1000)) return;
                require(title(0).getValue().equals("Forest edge"), "Drag goes to next spread");
                swipe(0, 25);
                advance();
            }
            case 18 -> {
                if (!after(600)) return;
                require(title(0).getValue().equals("Forest edge"), "Short drag cancels");
                click("<", 0);
                advance();
            }
            case 19 -> {
                if (!after(1000)) return;
                click("Delete", 0);
                advance();
            }
            case 20 -> {
                if (!after(3250)) return;
                require(button("Delete", 0) != null && button("Delete?", 0) == null, "Delete confirmation expires after three seconds");
                click("Delete", 0);
                require(Journal.waypoints().get(2) != null, "Expired confirmation cannot delete waypoint");
                click("Delete?", 0);
                require(Journal.waypoints().get(2) == null && Journal.waypoints().get(5).name.equals("Forest edge"), "Delete leaves page slots in place");
                Journal.save();
                Journal.disconnect();
                Journal.connect(mc);
                require(Journal.waypoints().get(3) == null && Journal.waypoints().get(5).name.equals("Forest edge"), "Sparse pages persist");
                require(Journal.waypoints().getFirst().originalX == 0 && !Journal.waypoints().getFirst().yEnabled, "Reset position and axes persist");
                String homeId = Journal.waypoints().getFirst().id;
                Journal.selectWorld("server:localhost:25565");
                require(Journal.waypoints().isEmpty() && Journal.tracked() == null, "Server journal starts separate from singleplayer");
                Journal.selectWorld("server:localhost:25566");
                require(Journal.waypoints().isEmpty(), "Different server addresses have separate journals");
                Journal.connect(mc);
                require(Journal.waypoints().getFirst().id.equals(homeId), "Returning to world restores its journal");
                mc.gui.setScreen(null);
                Navigation.start(Journal.waypoints().get(1));
                command("tp @a 0.5 4 0.5");
                command("gamemode survival @a");
                command("kill @a");
                advance();
            }
            case 21 -> {
                if (!after(1200)) return;
                require(Journal.waypoints().get(2) != null && Journal.waypoints().get(2).death, "Death saves an empty page");
                require(Journal.tracked() == Journal.waypoints().get(1), "Death keeps active waypoint");
                mc.player.respawn();
                advance();
            }
            case 22 -> {
                if (!after(1200) || mc.player.isDeadOrDying()) return;
                mc.gui.setScreen(new JournalScreen());
                advance();
            }
            case 23 -> {
                if (!after(1800)) return;
                click(">", 0);
                advance();
            }
            case 24 -> {
                if (!after(1000)) return;
                screenshot = "death-page";
                mc.options.guiScale().set(5);
                mc.resizeGui();
                advance();
            }
            case 25 -> {
                if (!after(750)) return;
                screenshot = "small-viewport";
                advance();
            }
            case 26 -> {
                if (!after(250)) return;
                mc.gui.setScreen(null);
                var point = Journal.waypoints().getFirst();
                point.yEnabled = true;
                point.x = mc.player.blockPosition().getX();
                point.z = mc.player.blockPosition().getZ();
                point.y = 300;
                Navigation.start(point);
                advance();
            }
            case 27 -> {
                if (!after(1400)) return;
                screenshot = "arrow-above";
                Journal.tracked().y = -60;
                Journal.changed();
                advance();
            }
            case 28 -> {
                if (!after(800)) return;
                screenshot = "arrow-below";
                mc.player.setYRot(mc.player.getYRot() + 90);
                mc.player.setXRot(35);
                advance();
            }
            case 29 -> {
                if (!after(800)) return;
                screenshot = "arrow-side";
                advance();
            }
            case 30 -> {
                if (!after(250)) return;
                LoggerFactory.getLogger("Simply Journal").info("SIMPLY_JOURNAL_RELEASE_QA_COMPLETE");
                mc.stop();
                advance();
            }
        }
    }

    private static void advance() { step++; since = System.nanoTime(); }
    private static boolean after(long milliseconds) { return System.nanoTime() - since >= milliseconds * 1_000_000L; }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message + " (step " + step + ")"); }

    private static EditBox title(int index) { return edit("Waypoint name", index); }
    private static EditBox coordinate(String axis) { return edit(axis + " coordinate", 0); }
    private static EditBox edit(String label, int index) {
        return Minecraft.getInstance().gui.screen().children().stream().filter(c -> c instanceof EditBox box && box.getMessage().getString().equals(label))
            .map(c -> (EditBox) c).skip(index).findFirst().orElseThrow();
    }
    private static MultiLineEditBox description(int index) {
        return Minecraft.getInstance().gui.screen().children().stream().filter(c -> c instanceof MultiLineEditBox)
            .map(c -> (MultiLineEditBox) c).skip(index).findFirst().orElseThrow();
    }
    private static Button button(String label, int index) {
        return Minecraft.getInstance().gui.screen().children().stream().filter(c -> c instanceof Button b && b.getMessage().getString().equals(label))
            .map(c -> (Button) c).skip(index).findFirst().orElse(null);
    }
    private static void click(String label, int index) {
        Screen screen = Minecraft.getInstance().gui.screen();
        Button button = button(label, index);
        require(button != null, "Missing button " + label);
        double x = button.getX() + button.getWidth() / 2F, y = button.getY() + button.getHeight() / 2F;
        if (screen instanceof JournalScreen journal) {
            Vector2f p = journal.screenPosition(button.getX() < 0 ? 0 : 1, (float) x, (float) y);
            x = p.x; y = p.y;
        }
        var event = new MouseButtonEvent(x, y, new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0));
        screen.mouseClicked(event, false);
        screen.mouseReleased(event);
    }
    private static void clickAt(int side, float x, float y) {
        var screen = (JournalScreen) Minecraft.getInstance().gui.screen();
        Vector2f p = screen.screenPosition(side, x, y);
        var event = new MouseButtonEvent(p.x, p.y, new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0));
        screen.mouseClicked(event, false);
        screen.mouseReleased(event);
    }
    private static void swipe(int side, float distance) {
        var screen = (JournalScreen) Minecraft.getInstance().gui.screen();
        Vector2f start = screen.screenPosition(side, side == 0 ? -145 : 145, -56);
        Vector2f end = screen.screenPosition(side, (side == 0 ? -145 : 145) + distance, -56);
        var info = new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0);
        screen.mouseClicked(new MouseButtonEvent(start.x, start.y, info), false);
        screen.mouseDragged(new MouseButtonEvent(end.x, end.y, info), end.x - start.x, end.y - start.y);
        screen.mouseReleased(new MouseButtonEvent(end.x, end.y, info));
    }
    private static void command(String command) {
        var server = Minecraft.getInstance().getSingleplayerServer();
        server.execute(() -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), command));
    }
    private static void checkMarker(boolean active) {
    }

    private static void playtest(Minecraft mc) {
        if (!started && mc.isGameLoadFinished() && mc.gui.overlay() == null && mc.gui.screen() instanceof TitleScreen) {
            started = true;
            mc.options.pauseOnLostFocus = true;
            mc.options.guiScale().set(3);
            mc.options.renderDistance().set(8);
            mc.resizeGui();
            String world = System.getenv("WAYPOINT_JOURNAL_WORLD");
            if (world != null && !world.isBlank()) {
                mc.createWorldOpenFlows().openWorld(world, () -> mc.gui.setScreen(new TitleScreen()));
                return;
            }
            var settings = new LevelSettings("Simply Journal Playtest", GameType.CREATIVE,
                new LevelSettings.DifficultySettings(Difficulty.NORMAL, false, false), true, WorldDataConfiguration.DEFAULT);
            long seed = System.currentTimeMillis();
            mc.createWorldOpenFlows().createFreshLevel("simply-journal-playtest-" + seed, settings,
                new WorldOptions(seed, true, false), WorldPresets::createNormalWorldDimensions, mc.gui.screen());
        }
        if (!ready && mc.player != null && mc.level != null && mc.gui.screen() == null) {
            ready = true;
            LoggerFactory.getLogger("Simply Journal").info("WAYPOINT_JOURNAL_PLAYTEST_READY");
        }
    }

    public static void render() {
        String name = screenshot;
        if (name == null) return;
        screenshot = null;
        var mc = Minecraft.getInstance();
        Screenshot.takeScreenshot(mc.gameRenderer.mainRenderTarget(), image -> {
            try (image) {
                var directory = mc.gameDirectory.toPath().resolve("render-checks");
                Files.createDirectories(directory);
                image.writeToFile(directory.resolve(name + ".png"));
            } catch (IOException error) { throw new UncheckedIOException(error); }
        });
    }
}
