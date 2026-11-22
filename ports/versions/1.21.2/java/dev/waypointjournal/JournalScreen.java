package dev.waypointjournal;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class JournalScreen extends Screen {
    private static final int INK = 0xFF332619;
    private static final int MUTED = 0xFF715A3E;
    private final long opened = System.nanoTime();
    private final List<PageWidget> widgets = new ArrayList<>();
    private final Matrix4f[] planes = {new Matrix4f(), new Matrix4f()};
    private float tiltX;
    private float tiltY;
    private long lastFrame;
    private int spread;
    private long turnStarted;
    private int turnDirection;
    private float turnFrom;
    private float turnTo = 1;
    private long turnDuration = 560_000_000L;
    private int dragSide = -1;
    private float dragStartX;
    private float dragStartY;
    private float dragProgress;
    private float dragTarget;
    private boolean draggingPage;
    private boolean closing;
    private long closeStarted;
    private float closeOpening;
    private String confirmDelete;
    private long confirmDeleteUntil;

    public JournalScreen() { super(Component.literal("Simply Journal")); }

    @Override
    protected void init() {
        Journal.connect(minecraft);
        widgets.clear();
        for (int side = 0; side < 2; side++) {
            for (AbstractWidget widget : buildPage(side, spread)) widget(side, widget);
        }
    }

    private List<AbstractWidget> buildPage(int side, int pageSpread) {
        List<AbstractWidget> result = new ArrayList<>();
        int left = side == 0 ? -169 : 17;
        int outer = side == 0 ? left : left + 110;
        var point = point(side, pageSpread);
        PaperButton create = new PaperButton(outer, -108, 42, "New", () -> create(side));
        create.active = point == null;
        if (!create.active) create.setTooltip(Tooltip.create(Component.literal("Turn to a blank page to add a waypoint.")));
        result.add(create);
        if (point != null) {
            EditBox title = new EditBox(font, left, -79, 150, 16, Component.literal("Waypoint name"));
            title.setBordered(false);
            title.setTextColor(INK);
            title.setMaxLength(48);
            title.setValue(point.name);
            title.setResponder(value -> { point.name = value; Journal.changed(); });
            result.add(title);
            for (int axis = 0; axis < 3; axis++) {
                int index = axis;
                int row = -52 + axis * 15;
                String name = new String[]{"X", "Y", "Z"}[axis];
                EditBox coordinate = new EditBox(font, left + 14, row - 1, 62, 12, Component.literal(name + " coordinate")) {
                    @Override public void setFocused(boolean focused) {
                        super.setFocused(focused);
                        if (!focused) {
                            setTextColor(INK);
                            setValue(formatCoordinate(point.coordinate(index)));
                        }
                    }
                };
                coordinate.setBordered(false);
                coordinate.setTextColor(INK);
                coordinate.setMaxLength(15);
                coordinate.setValue(formatCoordinate(point.coordinate(index)));
                coordinate.setResponder(value -> {
                    try {
                        int next = Integer.parseInt(value.replace(",", "").trim());
                        coordinate.setTextColor(INK);
                        if (next != point.coordinate(index)) { point.setCoordinate(index, next); Journal.changed(); }
                    } catch (NumberFormatException ignored) {
                        coordinate.setTextColor(0xFF9A3E32);
                    }
                });
                result.add(coordinate);
                PaperButton enabled = new PaperButton(left + 79, row - 2, 12, 13, point.enabled(axis) ? "x" : "", () -> {
                    point.setEnabled(index, !point.enabled(index));
                    Journal.changed();
                    rebuildWidgets();
                });
                enabled.setTooltip(Tooltip.create(Component.literal("Include " + name + " in navigation")));
                result.add(enabled);
                PaperButton copy = new PaperButton(left + 94, row - 2, 27, 13, "Copy", () ->
                    minecraft.keyboardHandler.setClipboard(Integer.toString(point.coordinate(index))));
                copy.setTooltip(Tooltip.create(Component.literal("Copy " + name)));
                result.add(copy);
                PaperButton reset = new PaperButton(left + 123, row - 2, 31, 13, "Reset", () ->
                    coordinate.setValue(formatCoordinate(point.originalCoordinate(index))));
                reset.setTooltip(Tooltip.create(Component.literal("Restore the saved " + name + " position")));
                result.add(reset);
            }
            MultiLineEditBox description = new MultiLineEditBox(font, left - 3, 6, 156, 75,
                Component.literal("Write a description..."), Component.literal("Waypoint description")) {
                @Override protected void renderBackground(GuiGraphics graphics) {}
                @Override protected void renderDecorations(GuiGraphics graphics) {}
            };
            description.setCharacterLimit(768);
            description.setValue(point.description);
            description.setValueListener(value -> { point.description = value; Journal.changed(); });
            result.add(description);
            result.add(new PaperButton(left, 89, 48, point == Journal.tracked() ? "Stop" : "Go!", () -> {
                if (point == Journal.tracked()) Journal.track(null);
                else Navigation.start(point);
                rebuildWidgets();
                if (point == Journal.tracked()) onClose();
            }));
            result.add(new PaperButton(left + 96, 89, 56, point.id.equals(confirmDelete) ? "Delete?" : "Delete", () -> {
                if (point.id.equals(confirmDelete) && System.nanoTime() < confirmDeleteUntil) {
                    Journal.remove(point);
                    confirmDelete = null;
                } else {
                    confirmDelete = point.id;
                    confirmDeleteUntil = System.nanoTime() + 3_000_000_000L;
                }
                rebuildWidgets();
            }));
        }
        if (side == 0 && pageSpread > 0) result.add(new PaperButton(left, 108, 32, "<", () -> turn(-1)));
        if (side == 1) {
            result.add(new PaperButton(left + 120, 108, 32, ">", () -> turn(1)));
        }
        return result;
    }

    private void widget(int side, AbstractWidget widget) {
        addWidget(widget);
        widgets.add(new PageWidget(side, widget));
    }

    private Journal.Waypoint point(int side, int pageSpread) {
        int index = pageSpread * 2 + side;
        return index < Journal.waypoints().size() ? Journal.waypoints().get(index) : null;
    }

    private void create(int side) {
        Journal.Waypoint point = Journal.addAt(spread * 2 + side, false);
        if (point == null) return;
        confirmDelete = null;
        rebuildWidgets();
        widgets.stream().filter(w -> w.side == side && w.widget instanceof EditBox).findFirst().ifPresent(w -> {
            setFocused(w.widget);
            EditBox title = (EditBox) w.widget;
            title.moveCursorToEnd(false);
            title.setHighlightPos(0);
        });
    }

    private void turn(int direction) {
        if (!interactive() || !canTurn(direction)) return;
        settleTurn(direction, 0, 1);
    }

    private boolean canTurn(int direction) { return direction > 0 || spread > 0; }

    private void settleTurn(int direction, float from, float to) {
        Journal.save();
        setFocused(null);
        turnStarted = System.nanoTime();
        turnDirection = direction;
        turnFrom = from;
        turnTo = to;
        turnDuration = Math.max(240_000_000L, (long) (Math.abs(to - from) * 800_000_000L));
        draggingPage = false;
        dragSide = -1;
        confirmDelete = null;
        if (to == 1) minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1));
    }

    private float age() { return (System.nanoTime() - opened) / 1_000_000_000F; }

    @Override
    public void tick() {
        if (confirmDelete != null && System.nanoTime() >= confirmDeleteUntil) {
            confirmDelete = null;
            for (PageWidget item : widgets) {
                if (item.widget instanceof PaperButton && item.widget.getMessage().getString().equals("Delete?")) {
                    item.widget.setMessage(Component.literal("Delete"));
                }
            }
        }
    }

    private boolean interactive() { return age() > 1.5F && !closing && turnStarted == 0 && !draggingPage; }

    @Override
    public void renderBackground(GuiGraphics graphics, int mx, int my, float delta) {}

    @Override
    public void render(GuiGraphics graphics, int mx, int my, float delta) {
        long now = System.nanoTime();
        float dt = lastFrame == 0 ? 0 : Math.min(.05F, (now - lastFrame) / 1_000_000_000F);
        lastFrame = now;
        float elapsed = age();
        float t = Mth.clamp(elapsed / 1.5F, 0, 1);
        float flight = softer(t);
        float opening = softer(Mth.clamp((t - .32F) / .62F, 0, 1));
        float exit = closing ? Mth.clamp((now - closeStarted) / 1_150_000_000F, 0, 1) : 0;
        if (exit >= 1) { minecraft.setScreen(null); return; }
        float departure = softer(exit);
        if (closing) opening = closeOpening * (1 - softer(Mth.clamp(exit / .78F, 0, 1)));
        float size = Math.min(width * .88F / 470F, height * .79F / 360F);
        float x = width * .5F - (1 - flight) * width * 1.05F + departure * width * 1.05F;
        float arc = (float) Math.pow(Math.sin(t * Math.PI), 2);
        float exitArc = (float) Math.pow(Math.sin(exit * Math.PI), 2);
        float y = height * .5F + (1 - flight) * height * .45F - arc * height * .1F
            - exitArc * height * .08F + departure * height * .14F;
        float response = 1 - (float) Math.exp(-dt * 5);
        if (dragSide < 0 && turnStarted == 0) {
            tiltX += (Mth.clamp((my - height / 2F) / height, -.5F, .5F) * .045F - tiltX) * response;
            tiltY += (Mth.clamp((mx - width / 2F) / width, -.5F, .5F) * .055F - tiltY) * response;
        }
        Matrix4f rotation = new Matrix4f().rotateZ(-.025F - (1 - flight) * .48F + arc * .045F + departure * .22F)
            .rotateY(-.12F + tiltY - (1 - flight) * .8F + departure * .4F)
            .rotateX(.23F + tiltX - (1 - flight) * .4F - departure * .12F);
        float animation = turnStarted == 0 ? 0 : Mth.clamp((now - turnStarted) / (float) turnDuration, 0, 1);
        if (draggingPage) dragProgress = Mth.approach(dragProgress, dragTarget, dt * 2);
        float turnProgress = draggingPage ? dragProgress : turnStarted == 0 ? -1 : Mth.lerp(smooth(animation), turnFrom, turnTo);
        BookRenderer.Page[] pages = new BookRenderer.Page[4];
        BookRenderer.State state = new BookRenderer.State(width, height, size * (.82F + .18F * flight) * (1 - departure * .1F), x, y,
            rotation, opening, closing, turnProgress, turnDirection, pages);
        for (int side = 0; side < 2; side++) {
            planes[side].setPerspective((float) Math.toRadians(42), width / (float) height, 1, 10000)
                .mul(BookRenderer.model(state)).mul(BookRenderer.hinge(side, opening, closing));
            if (opening <= .03F) continue;
            pages[side] = capturePage(side, spread, mx, my, delta, true);
            if (turnProgress >= 0) pages[side + 2] = capturePage(side, spread + turnDirection, mx, my, delta, false);
        }
        graphics.fill(0, 0, width, height, ((int) (116 * flight * (1 - departure)) << 24) | 0x11100C);
        BookRenderer.render(graphics, state);
        if (turnStarted != 0 && animation >= 1) {
            if (turnTo == 1) spread += turnDirection;
            turnStarted = 0;
            rebuildWidgets();
        }
        if (interactive()) {
            graphics.drawCenteredString(font, "Simply Journal", width / 2, Math.max(6, (int) (height / 2F - 195 * size)), 0xFFF0DFC0);
            graphics.drawCenteredString(font, "Drag pages to turn | Esc to close", width / 2, Math.min(height - 12, (int) (height / 2F + 188 * size)), 0xFFC6BBA6);
        }
    }

    private BookRenderer.Page capturePage(int side, int pageSpread, int mx, int my, float delta, boolean current) {
        Vector2f mouse = local(side, mx, my);
        List<AbstractWidget> pageWidgets = current ? widgets.stream().filter(w -> w.side == side).map(PageWidget::widget).toList() : buildPage(side, pageSpread);
        return new BookRenderer.Page(graphics -> {
            page(graphics, side, pageSpread);
            for (AbstractWidget widget : pageWidgets) widget.render(graphics, current && interactive() ? (int) mouse.x : -10000,
                current && interactive() ? (int) mouse.y : -10000, delta);
        });
    }

    private void page(GuiGraphics graphics, int side, int pageSpread) {
        int left = side == 0 ? -169 : 17;
        var point = point(side, pageSpread);
        graphics.fill(side == 0 ? -175 : 5, -128, side == 0 ? -5 : 175, 128, 0xFFF4E7CD);
        graphics.drawString(font, point != null && point.death ? "DEATH LOCATION" : "WAYPOINT", side == 0 ? left + 50 : left, -102, MUTED, false);
        if (point == null) {
            graphics.drawString(font, "An unwritten page", left + 6, -40, INK, false);
            graphics.drawWordWrap(font, Component.literal("Press New to save the place where you're standing."), left + 6, -17, 139, MUTED);
        } else {
            graphics.fill(left, -61, left + 150, -60, 0xFFB49A72);
            for (int axis = 0; axis < 3; axis++) {
                graphics.drawString(font, new String[]{"X:", "Y:", "Z:"}[axis], left, -51 + axis * 15, INK, false);
            }
            graphics.drawString(font, font.plainSubstrByWidth(Journal.dimensionName(point.dimension), 150), left, -8, MUTED, false);
        }
        graphics.drawString(font, Integer.toString(pageSpread * 2 + side + 1), left + 72, 114, MUTED, false);
    }

    Vector2f screenPosition(int side, float x, float y) {
        Vector3f p = planes[side].transformProject(new Vector3f(x, y, .6F));
        return new Vector2f((p.x + 1) * width / 2F, (1 - p.y) * height / 2F);
    }

    private Vector2f local(int side, double x, double y) {
        float px = (float) (x - width / 2F), py = (float) (y - height / 2F);
        for (int i = 0; i < 8; i++) {
            Vector2f p = screenPosition(side, px, py);
            Vector2f right = screenPosition(side, px + 1, py).sub(p);
            Vector2f down = screenPosition(side, px, py + 1).sub(p);
            float determinant = right.x * down.y - down.x * right.y;
            if (Math.abs(determinant) < .0001F) break;
            float dx = (float) x - p.x, dy = (float) y - p.y;
            px += (dx * down.y - down.x * dy) / determinant;
            py += (right.x * dy - dx * right.y) / determinant;
        }
        return new Vector2f(px, py);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (!interactive()) return true;
        dragSide = -1;
        for (PageWidget item : widgets) {
            Vector2f p = local(item.side, mx, my);
            if (item.widget.isMouseOver(p.x, p.y)) {
                boolean editing = item.widget == getFocused();
                setFocused(item.widget);
                item.widget.mouseClicked(p.x, p.y, button);
                setDragging(true);
                if (!editing && !(item.widget instanceof Button) && !(item.widget instanceof EditBox)) startDrag(item.side, mx, my, button);
                return true;
            }
        }
        setFocused(null);
        for (int side = 0; side < 2; side++) {
            Vector2f p = local(side, mx, my);
            if (p.x >= (side == 0 ? -175 : 5) && p.x <= (side == 0 ? -5 : 175) && p.y >= -128 && p.y <= 128) {
                startDrag(side, mx, my, button);
                break;
            }
        }
        return true;
    }

    private void startDrag(int side, double mx, double my, int button) {
        if (button != InputConstants.MOUSE_BUTTON_LEFT || !canTurn(side == 1 ? 1 : -1)) return;
        Vector2f p = local(side, mx, my);
        dragSide = side;
        dragStartX = p.x;
        dragStartY = p.y;
        dragProgress = 0;
        dragTarget = 0;
        setDragging(true);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (dragSide >= 0 && button == InputConstants.MOUSE_BUTTON_LEFT) {
            Vector2f p = local(dragSide, mx, my);
            int direction = dragSide == 1 ? 1 : -1;
            float travel = (dragStartX - p.x) * direction;
            if (!draggingPage && travel > 8 && travel > Math.abs(p.y - dragStartY) * 1.2F) {
                draggingPage = true;
                turnDirection = direction;
                confirmDelete = null;
                Journal.save();
                setFocused(null);
            }
            if (draggingPage) {
                dragTarget = Mth.clamp(travel / 175, 0, 1);
                return true;
            }
        }
        if (!interactive()) return true;
        for (PageWidget item : widgets) if (item.widget == getFocused()) {
            Vector2f p = local(item.side, mx, my);
            Vector2f before = local(item.side, mx - dx, my - dy);
            return item.widget.mouseDragged(p.x, p.y, button, p.x - before.x, p.y - before.y);
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        setDragging(false);
        if (draggingPage) {
            settleTurn(turnDirection, dragProgress, dragTarget >= .35F ? 1 : 0);
            return true;
        }
        dragSide = -1;
        for (PageWidget item : widgets) if (item.widget == getFocused()) {
            Vector2f p = local(item.side, mx, my);
            return item.widget.mouseReleased(p.x, p.y, button);
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double x, double y, double dx, double dy) {
        if (!interactive()) return true;
        for (PageWidget item : widgets) {
            Vector2f p = local(item.side, x, y);
            if (item.widget.isMouseOver(p.x, p.y) && item.widget.mouseScrolled(p.x, p.y, dx, dy)) return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (key == InputConstants.KEY_ESCAPE) { onClose(); return true; }
        if (!interactive()) return true;
        if (getFocused() == null && key == InputConstants.KEY_SEMICOLON) { onClose(); return true; }
        return super.keyPressed(key, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char character, int modifiers) {
        return interactive() && super.charTyped(character, modifiers);
    }

    @Override
    public void onClose() {
        if (closing) return;
        Journal.save();
        closing = true;
        closeOpening = softer(Mth.clamp((age() / 1.5F - .32F) / .62F, 0, 1));
        draggingPage = false;
        dragSide = -1;
        turnStarted = 0;
        closeStarted = System.nanoTime();
        setFocused(null);
    }

    @Override public void removed() { Journal.save(); }
    @Override public boolean isPauseScreen() { return false; }

    private static float smooth(float value) { return value * value * (3 - 2 * value); }

    private static float softer(float value) { return value * value * value * (value * (value * 6 - 15) + 10); }

    private static String formatCoordinate(int value) { return String.format(Locale.US, "%,d", value); }

    private record PageWidget(int side, AbstractWidget widget) {}

    private final class PaperButton extends Button {
        private float hover;
        private long frame;

        PaperButton(int x, int y, int width, String text, Runnable action) {
            this(x, y, width, 18, text, action);
        }

        PaperButton(int x, int y, int width, int height, String text, Runnable action) {
            super(x, y, width, height, Component.literal(text), button -> action.run(), DEFAULT_NARRATION);
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mx, int my, float delta) {
            long now = System.nanoTime();
            float dt = frame == 0 ? 0 : Math.min(.05F, (now - frame) / 1_000_000_000F);
            frame = now;
            hover += ((active && isHoveredOrFocused() ? 1 : 0) - hover) * (1 - (float) Math.exp(-dt * 18));
            int x = getX(), y = getY(), height = getHeight();
            graphics.fill(x, y + 1, x + getWidth(), y + height - 1, 0xFFDCC6A1);
            graphics.fill(x + 1, y, x + getWidth() - 1, y + height, 0xFFDCC6A1);
            graphics.fill(x + 1, y + 1, x + getWidth() - 1, y + height - 2, net.minecraft.util.ARGB.lerp(hover, 0xFFE8D5B2, 0xFFF8E4BE));
            graphics.fill(x + 1, y + height - 1, x + getWidth() - 1, y + height, 0xFFAA8B61);
            if (isFocused()) {
                graphics.hLine(x, x + getWidth(), y, MUTED);
                graphics.hLine(x, x + getWidth(), y + height, MUTED);
                graphics.vLine(x, y, y + height, MUTED);
                graphics.vLine(x + getWidth(), y, y + height, MUTED);
            }
            graphics.drawString(font, getMessage(), x + (getWidth() - font.width(getMessage())) / 2, y + (height - 8) / 2, active ? INK : MUTED, false);
        }
    }
}
