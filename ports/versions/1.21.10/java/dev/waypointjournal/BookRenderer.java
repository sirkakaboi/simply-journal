package dev.waypointjournal;

import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.platform.Lighting;
import net.minecraft.client.model.BookModel;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.font.TextRenderable;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.CachedPerspectiveProjectionMatrixBuffer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.gui.render.state.GuiRenderState;
import net.minecraft.client.gui.render.state.pip.PictureInPictureRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

public final class BookRenderer extends PictureInPictureRenderer<BookRenderer.State> {
    private static final ResourceLocation WHITE = ResourceLocation.fromNamespaceAndPath("waypointjournal", "textures/white.png");
    private static final ResourceLocation BOOK_TEXTURE = ResourceLocation.fromNamespaceAndPath("waypointjournal", "textures/book.png");
    private final BookModel book = new BookModel(BookModel.createBodyLayer().bakeRoot());
    private final CachedPerspectiveProjectionMatrixBuffer projectionBuffer = new CachedPerspectiveProjectionMatrixBuffer("Simply Journal perspective", 1, 10000);

    public BookRenderer() { super(Minecraft.getInstance().renderBuffers().bufferSource()); }

    @Override public Class<State> getRenderStateClass() { return State.class; }
    @Override protected String getTextureLabel() { return "Simply Journal"; }

    @Override
    protected void renderToTexture(State state, PoseStack ignored) {
        RenderSystem.setProjectionMatrix(projectionBuffer.getBuffer(state.x1, state.y1, 42), ProjectionType.PERSPECTIVE);
        Minecraft.getInstance().gameRenderer.getLighting().setupFor(Lighting.Entry.ENTITY_IN_UI);
        PoseStack pose = new PoseStack();
        pose.mulPose(model(state));
        float flip = state.turn < 0 ? 0 : state.turnDirection > 0 ? state.turn : 1 - state.turn;
        float openness = 1.25F * state.open;
        PoseStack modelPose = new PoseStack();
        modelPose.mulPose(pose.last().pose());
        modelPose.translate(0, 0, -34 * (float) Math.sin(openness));
        modelPose.scale(34 * 16, 32 * 16, 34 * 16);
        modelPose.mulPose(new Matrix4f().rotateY(-(float) Math.PI / 2 + coverRotation(state.open, state.closing)));
        book.setupAnim(new BookModel.State(0, flip, 1, state.open));
        book.renderToBuffer(modelPose, bufferSource.getBuffer(RenderType.entityCutout(BOOK_TEXTURE)), 15728880, OverlayTexture.NO_OVERLAY);
        if (state.open > .03F) {
            if (state.turn < 0) {
                for (int side = 0; side < 2; side++) drawPage(state, side, side, hinge(side, state.open, state.closing), pose);
            } else {
                boolean forward = state.turnDirection > 0;
                drawPage(state, forward ? 0 : 2, 0, hinge(0, state.open, state.closing), pose);
                drawPage(state, forward ? 3 : 1, 1, hinge(1, state.open, state.closing), pose);
                float angle = openness * (1 - 2 * flip);
                for (int side = 0; side < 2; side++) {
                    Matrix4f transform = pageTransform(side, state.open, angle, state.closing).translate(0, 0, .85F);
                    if (new Matrix4f(model(state)).mul(transform).transformDirection(0, 0, 1, new org.joml.Vector3f()).z <= 0) continue;
                    int index = forward ? side == 1 ? 1 : 2 : side == 0 ? 0 : 3;
                    drawPage(state, index, side, transform, pose);
                }
            }
        }
    }

    private void drawPage(State state, int index, int side, Matrix4f transform, PoseStack pose) {
        if (new Matrix4f(model(state)).mul(transform).transformDirection(0, 0, 1, new org.joml.Vector3f()).z <= .001F) return;
        if (state.pages[index] != null) content(state.pages[index], transform, pose, side);
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

    private void content(GuiRenderState state, Matrix4f hinge, PoseStack book, int side) {
        PoseStack pose = new PoseStack();
        pose.mulPose(book.last().pose());
        pose.mulPose(hinge);
        pose.translate(side == 0 ? -184 : 0, -128, 0);
        final float[] depth = {.12F};
        state.forEachElement(element -> {
            Captured vertices = new Captured();
            element.buildVertices(vertices);
            submit(vertices, bounded(element.scissorArea(), side), pose, RenderType.text(WHITE), depth[0], false);
            depth[0] += .003F;
        }, GuiRenderState.TraverseRange.ALL);
        state.forEachText(text -> text.ensurePrepared().visit(new Font.GlyphVisitor() {
            @Override public void acceptGlyph(TextRenderable glyph) { draw(glyph); }
            @Override public void acceptEffect(TextRenderable effect) { draw(effect); }
            private void draw(TextRenderable glyph) {
                Captured vertices = new Captured();
                Matrix4f transform = new Matrix4f().m00(text.pose.m00()).m01(text.pose.m01())
                    .m10(text.pose.m10()).m11(text.pose.m11()).m30(text.pose.m20()).m31(text.pose.m21());
                glyph.render(transform, vertices, 15728880, false);
                submit(vertices, bounded(text.scissor, side), pose, glyph.renderType(Font.DisplayMode.NORMAL), .55F, true);
            }
        }));
    }

    private void submit(Captured captured, ScreenRectangle clip, PoseStack pose,
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
        var buffer = bufferSource.getBuffer(type);
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

    @Override public void close() { super.close(); projectionBuffer.close(); }

    public record State(int x1, int y1, float size, float centerX, float centerY, Matrix4f rotation,
                        float open, boolean closing, float turn, int turnDirection, GuiRenderState[] pages) implements PictureInPictureRenderState {
        @Override public int x0() { return 0; }
        @Override public int y0() { return 0; }
        @Override public float scale() { return 1; }
        @Override public ScreenRectangle scissorArea() { return null; }
        @Override public ScreenRectangle bounds() { return new ScreenRectangle(0, 0, x1, y1); }
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
