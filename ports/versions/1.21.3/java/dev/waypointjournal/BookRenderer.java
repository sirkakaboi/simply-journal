package dev.waypointjournal;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.ProjectionType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.model.BookModel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.Consumer;

public final class BookRenderer {
    private static final ResourceLocation WHITE = ResourceLocation.fromNamespaceAndPath("waypointjournal", "textures/white.png");
    private static final ResourceLocation BOOK_TEXTURE = ResourceLocation.fromNamespaceAndPath("waypointjournal", "textures/book.png");
    private static final BookModel BOOK = new BookModel(BookModel.createBodyLayer().bakeRoot());

    private BookRenderer() {}

    static void render(GuiGraphics graphics, State state) {
        var client = Minecraft.getInstance();
        var buffers = client.renderBuffers().bufferSource();
        graphics.flush();
        Matrix4f projection = new Matrix4f(RenderSystem.getProjectionMatrix());
        var sorting = RenderSystem.getProjectionType();
        var modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix().identity();
        RenderSystem.setProjectionMatrix(new Matrix4f().setPerspective((float) Math.toRadians(42), state.x1 / (float) state.y1, 1, 10000), ProjectionType.PERSPECTIVE);
        RenderSystem.clear(256);
        PoseStack pose = new PoseStack();
        pose.mulPose(model(state));
        float flip = state.turn < 0 ? 0 : state.turnDirection > 0 ? state.turn : 1 - state.turn;
        PoseStack modelPose = new PoseStack();
        modelPose.mulPose(pose.last().pose());
        modelPose.translate(0, 0, -34 * (float) Math.sin(1.25F * state.open));
        modelPose.scale(34 * 16, 32 * 16, 34 * 16);
        modelPose.mulPose(new Matrix4f().rotateY(-(float) Math.PI / 2 + coverRotation(state.open, state.closing)));
        BOOK.setupAnim(0, flip, 1, state.open);
        BOOK.renderToBuffer(modelPose, buffers.getBuffer(RenderType.entityCutoutNoCull(BOOK_TEXTURE)), 15728880, OverlayTexture.NO_OVERLAY);
        if (state.open > .03F) {
            if (state.turn < 0) {
                for (int side = 0; side < 2; side++) drawPage(state, side, side, hinge(side, state.open, state.closing), pose);
            } else {
                boolean forward = state.turnDirection > 0;
                drawPage(state, forward ? 0 : 2, 0, hinge(0, state.open, state.closing), pose);
                drawPage(state, forward ? 3 : 1, 1, hinge(1, state.open, state.closing), pose);
                float angle = 1.25F * state.open * (1 - 2 * flip);
                for (int side = 0; side < 2; side++) {
                    var transform = pageTransform(side, state.open, angle, state.closing).translate(0, 0, .85F);
                    drawPage(state, forward ? side == 1 ? 1 : 2 : side == 0 ? 0 : 3, side, transform, pose);
                }
            }
        }
        buffers.endBatch();
        RenderSystem.setProjectionMatrix(projection, sorting);
        modelView.popMatrix();
    }

    private static void drawPage(State state, int index, int side, Matrix4f transform, PoseStack book) {
        if (state.pages[index] == null || new Matrix4f(model(state)).mul(transform).transformDirection(0, 0, 1, new org.joml.Vector3f()).z <= .001F) return;
        PoseStack pose = new PoseStack();
        pose.mulPose(book.last().pose());
        pose.mulPose(transform);
        pose.translate(side == 0 ? -184 : 0, -128, 0);
        state.pages[index].draw.accept(new PageGraphics(pose, side));
    }

    static float cameraDistance(int height) { return height / (2F * (float) Math.tan(Math.toRadians(21))); }

    static Matrix4f model(State state) {
        return new Matrix4f().translate(state.centerX - state.x1 / 2F, state.y1 / 2F - state.centerY, -cameraDistance(state.y1))
            .scale(state.size, -state.size, state.size).mul(state.rotation);
    }

    static Matrix4f hinge(int side, float open, boolean closing) {
        return pageTransform(side, open, (side == 0 ? -1.25F : 1.25F) * open, closing);
    }

    private static float coverRotation(float open, boolean closing) { return (closing ? 1 : -1) * (1.25F - 1.25F * open); }

    private static Matrix4f pageTransform(int side, float open, float rotation, boolean closing) {
        float angle = 1.25F * open;
        float sine = (float) Math.sin(angle);
        return new Matrix4f().translate(0, 0, -34 * sine).scale(34, 32, 34).rotateY(-(float) Math.PI / 2 + coverRotation(open, closing))
            .translate(sine, 0, 0).rotateY(rotation)
            .translate(-5 / 34F, 0, side == 0 ? -.025F : .025F)
            .scale(side == 0 ? -1 / 34F : 1 / 34F, 1 / 32F, side == 0 ? -1 / 34F : 1 / 34F);
    }

    private static void submit(Captured captured, ScreenRectangle clip, PoseStack pose,
                               RenderType type, float depth, boolean text) {
        List<Vertex> vertices = captured.vertices;
        if (vertices.isEmpty()) return;
        List<Vertex> clipped = new ArrayList<>();
        for (int i = 0; i + 3 < vertices.size(); i += 4) {
            List<Vertex> quad = vertices.subList(i, i + 4);
            float minX = quad.stream().map(v -> v.x).min(Float::compare).orElse(0F);
            float maxX = quad.stream().map(v -> v.x).max(Float::compare).orElse(0F);
            float minY = quad.stream().map(v -> v.y).min(Float::compare).orElse(0F);
            float maxY = quad.stream().map(v -> v.y).max(Float::compare).orElse(0F);
            if (clip != null && (maxX <= clip.left() || minX >= clip.right() || maxY <= clip.top() || minY >= clip.bottom())) continue;
            for (Vertex vertex : quad) {
                Vertex v = vertex.copy();
                if (clip != null) {
                    float nx = Math.max(clip.left(), Math.min(clip.right(), v.x));
                    float ny = Math.max(clip.top(), Math.min(clip.bottom(), v.y));
                    Vertex oppositeX = quad.stream().filter(p -> p.x != vertex.x && p.y == vertex.y).findFirst().orElse(vertex);
                    Vertex oppositeY = quad.stream().filter(p -> p.y != vertex.y && p.x == vertex.x).findFirst().orElse(vertex);
                    if (oppositeX.x != v.x) v.u += (oppositeX.u - v.u) * (nx - v.x) / (oppositeX.x - v.x);
                    if (oppositeY.y != v.y) v.v += (oppositeY.v - v.v) * (ny - v.y) / (oppositeY.y - v.y);
                    v.x = nx; v.y = ny;
                }
                clipped.add(v);
            }
        }
        var matrix = pose.last();
        var buffer = Minecraft.getInstance().renderBuffers().bufferSource().getBuffer(type);
        {
            for (Vertex v : clipped) {
                buffer.addVertex(matrix, v.x, v.y, depth).setColor(v.color)
                    .setUv(text ? v.u : .5F, text ? v.v : .5F).setOverlay(OverlayTexture.NO_OVERLAY).setLight(15728880).setNormal(matrix, 0, 0, 1);
            }
        }
    }

    private static ScreenRectangle bounded(ScreenRectangle clip, int side) {
        int left = side == 0 ? 9 : 5, top = 0, right = left + 170, bottom = 256;
        if (clip != null) {
            left = Math.max(left, clip.left());
            top = Math.max(top, clip.top());
            right = Math.min(right, clip.right());
            bottom = Math.min(bottom, clip.bottom());
        }
        return new ScreenRectangle(left, top, Math.max(0, right - left), Math.max(0, bottom - top));
    }

    public record Page(Consumer<GuiGraphics> draw) {}
    public record State(int x1, int y1, float size, float centerX, float centerY, Matrix4f rotation,
                        float open, boolean closing, float turn, int turnDirection, Page[] pages) {}

    private static final class PageGraphics extends GuiGraphics {
        private final PoseStack page;
        private final int side;
        private final ArrayDeque<ScreenRectangle> clips = new ArrayDeque<>();
        private float depth = .12F;

        PageGraphics(PoseStack page, int side) {
            super(Minecraft.getInstance(), Minecraft.getInstance().renderBuffers().bufferSource());
            this.page = page;
            this.side = side;
            pose().translate(side == 0 ? 184 : 0, 128, 0);
        }

        @Override public void enableScissor(int x0, int y0, int x1, int y1) {
            int shift = side == 0 ? 184 : 0;
            ScreenRectangle next = new ScreenRectangle(x0 + shift, y0 + 128, Math.max(0, x1 - x0), Math.max(0, y1 - y0));
            if (!clips.isEmpty()) {
                var previous = clips.peek();
                int left = Math.max(previous.left(), next.left()), top = Math.max(previous.top(), next.top());
                next = new ScreenRectangle(left, top, Math.max(0, Math.min(previous.right(), next.right()) - left), Math.max(0, Math.min(previous.bottom(), next.bottom()) - top));
            }
            clips.push(next);
        }
        @Override public void disableScissor() { clips.pop(); }

        @Override public void fill(int x0, int y0, int x1, int y1, int color) {
            Captured vertices = new Captured();
            int ink = color == -1 ? 0xFF332619 : color;
            vertices.addVertex(pose().last(), x0, y0, 0).setColor(ink);
            vertices.addVertex(pose().last(), x0, y1, 0).setColor(ink);
            vertices.addVertex(pose().last(), x1, y1, 0).setColor(ink);
            vertices.addVertex(pose().last(), x1, y0, 0).setColor(ink);
            submit(vertices, bounded(clips.peek(), side), page, RenderType.text(WHITE), depth, false);
            depth += .003F;
        }
        @Override public void fill(int x0, int y0, int x1, int y1, int z, int color) { fill(x0, y0, x1, y1, color); }
        @Override public void fill(RenderType type, int x0, int y0, int x1, int y1, int color) { fill(x0, y0, x1, y1, color); }
        @Override public void fill(RenderType type, int x0, int y0, int x1, int y1, int z, int color) { fill(x0, y0, x1, y1, color); }

        @Override public int drawString(Font font, String text, int x, int y, int color, boolean shadow) {
            return drawString(font, Component.literal(text).getVisualOrderText(), x, y, color, shadow);
        }
        @Override public int drawString(Font font, Component text, int x, int y, int color, boolean shadow) {
            return drawString(font, text.getVisualOrderText(), x, y, color, shadow);
        }
        @Override public int drawString(Font font, FormattedCharSequence text, int x, int y, int color, boolean shadow) {
            var glyphs = new LinkedHashMap<RenderType, Captured>();
            int ink = ((color & 0xFFFFFF) == 0xFFFFFF || (color & 0xFFFFFF) == 0xE0E0E0) ? 0xFF332619 : color;
            font.drawInBatch(text, x, y, ink, false, pose().last().pose(), type -> glyphs.computeIfAbsent(type, key -> new Captured()), Font.DisplayMode.NORMAL, 0, 15728880);
            glyphs.forEach((type, vertices) -> submit(vertices, bounded(clips.peek(), side), page, type, .55F, true));
            return font.width(text);
        }
    }

    private static final class Vertex {
        float x, y, u, v;
        int color = -1;
        Vertex copy() { Vertex v = new Vertex(); v.x = x; v.y = y; v.u = u; v.v = this.v; v.color = color; return v; }
    }

    private static final class Captured implements VertexConsumer {
        private final List<Vertex> vertices = new ArrayList<>();
        private Vertex current;
        @Override public VertexConsumer addVertex(float x, float y, float z) { current = new Vertex(); current.x = x; current.y = y; vertices.add(current); return this; }
        @Override public VertexConsumer setColor(int color) { current.color = color; return this; }
        @Override public VertexConsumer setColor(int r, int g, int b, int a) { return setColor(a << 24 | r << 16 | g << 8 | b); }
        @Override public VertexConsumer setUv(float u, float v) { current.u = u; current.v = v; return this; }
        @Override public VertexConsumer setUv1(int u, int v) { return this; }
        @Override public VertexConsumer setUv2(int u, int v) { return this; }
        @Override public VertexConsumer setNormal(float x, float y, float z) { return this; }
    }
}
