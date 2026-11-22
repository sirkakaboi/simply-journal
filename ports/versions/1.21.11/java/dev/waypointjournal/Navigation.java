package dev.waypointjournal;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.LevelRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

public final class Navigation {
    private static final Identifier WHITE = Identifier.fromNamespaceAndPath("waypointjournal", "textures/white.png");
    private static long lastFrame;
    private static String destination;
    private static float angle;
    private static float visibility;
    private static float arrivalTime = -1;
    private static int axes;
    private static int targetX, targetY, targetZ;
    private static String guide;
    private static long guideStarted;
    private static float guideYaw;
    private static float guidePitch;

    private Navigation() {}

    public static void start(Journal.Waypoint point) {
        Journal.track(point);
        destination = null;
        guide = point.id;
        guideStarted = 0;
    }

    public static void tick(Minecraft client) {
        if (guide == null) return;
        var point = Journal.tracked();
        if (client.player == null || point == null || !guide.equals(point.id)) { guide = null; return; }
        if (client.screen != null) {
            if (guideStarted != 0) guide = null;
            return;
        }
        var player = client.player;
        long now = System.nanoTime();
        if (guideStarted == 0) {
            guideStarted = now;
            guideYaw = player.getYRot();
            guidePitch = player.getXRot();
        }
        double dx = point.xEnabled ? point.x + .5 - player.getX() : 0;
        double dz = point.zEnabled ? point.z + .5 - player.getZ() : 0;
        float targetYaw = Math.hypot(dx, dz) < .001 ? guideYaw : (float) Math.toDegrees(Math.atan2(-dx, dz));
        float targetPitch = (float) Math.toDegrees(Math.atan2(player.getEyeY() - player.getY() - 1.1, 1.8));
        if (client.options.getCameraType().isMirrored()) { targetYaw += 180; targetPitch = -targetPitch; }
        float t = Mth.clamp((now - guideStarted) / 850_000_000F, 0, 1);
        float ease = t * t * t * (t * (t * 6 - 15) + 10);
        player.setYRot(guideYaw + Mth.wrapDegrees(targetYaw - guideYaw) * ease);
        player.setXRot(Mth.lerp(ease, guidePitch, targetPitch));
        if (t >= 1) guide = null;
    }

    public static void render(LevelRenderState state, SubmitNodeCollector collector) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null || client.options.hideGui || client.screen != null) {
            lastFrame = 0;
            return;
        }
        Journal.Waypoint point = Journal.tracked();
        if (point == null) {
            destination = null;
            visibility = 0;
            arrivalTime = -1;
            lastFrame = 0;
            return;
        }
        long now = System.nanoTime();
        float elapsed = lastFrame == 0 ? 1 / 60F : (now - lastFrame) / 1_000_000_000F;
        float dt = Math.min(.05F, elapsed);
        lastFrame = now;
        int mask = (point.xEnabled ? 1 : 0) | (point.yEnabled ? 2 : 0) | (point.zEnabled ? 4 : 0);
        boolean changed = !point.id.equals(destination) || axes != mask || targetX != point.x || targetY != point.y || targetZ != point.z;
        if (changed) {
            destination = point.id;
            axes = mask;
            targetX = point.x;
            targetY = point.y;
            targetZ = point.z;
            visibility = 0;
            arrivalTime = -1;
        }
        float partial = client.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        var player = client.player;
        double x = Mth.lerp(partial, player.xo, player.getX());
        double y = Mth.lerp(partial, player.yo, player.getY());
        double z = Mth.lerp(partial, player.zo, player.getZ());
        double dx = point.xEnabled ? point.x + .5 - x : 0;
        double dy = point.yEnabled ? point.y - y : 0;
        double dz = point.zEnabled ? point.z + .5 - z : 0;
        double horizontal = Math.hypot(dx, dz);
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        boolean sameDimension = point.dimension.equals(client.level.dimension().identifier().toString());
        if (arrivalTime < 0) {
            float target = horizontal < .001 ? (changed ? player.getYRot() : angle) : (float) Math.toDegrees(Math.atan2(-dx, dz));
            if (changed) angle = target;
            else {
                float response = 1 - (float) Math.exp(-dt * 14);
                angle += Mth.wrapDegrees(target - angle) * response;
            }
        }
        if (sameDimension && distance <= 2 && arrivalTime < 0) arrivalTime = 0;
        if (arrivalTime >= 0) {
            arrivalTime += elapsed;
            float fade = Mth.clamp((arrivalTime - 3) / 2, 0, 1);
            visibility = 1 - fade * fade * (3 - 2 * fade);
            if (fade >= 1) {
                Journal.track(null);
                destination = null;
                arrivalTime = -1;
                visibility = 0;
                lastFrame = 0;
                return;
            }
        } else visibility += (1 - visibility) * (1 - (float) Math.exp(-dt * 8));
        if (visibility < .01F) return;
        int alpha = Math.max(4, Math.round(visibility * 255)) << 24;
        float yaw = -angle * Mth.DEG_TO_RAD;
        var camera = state.cameraRenderState;
        double anchorX = x + Math.sin(yaw) * 1.8;
        double anchorY = y + 1.1;
        double anchorZ = z + Math.cos(yaw) * 1.8;
        if (sameDimension) {
            PoseStack arrow = new PoseStack();
            arrow.translate(anchorX - camera.pos.x, anchorY - camera.pos.y, anchorZ - camera.pos.z);
            var direction = new org.joml.Vector3f((float) dx, (float) dy, (float) dz)
                .rotate(new org.joml.Quaternionf(camera.orientation).conjugate());
            float rotation = direction.x * direction.x + direction.y * direction.y < .0001F ? 0
                : (float) Math.atan2(-direction.x, direction.y);
            arrow.mulPose(camera.orientation);
            arrow.mulPose(new org.joml.Quaternionf().rotationZ(rotation));
            arrow.scale(.46F, .46F, .46F);
            collector.submitCustomGeometry(arrow, RenderTypes.text(WHITE), (pose, buffer) -> {
                int color = alpha | 0xFFFFFF;
                vertex(buffer, pose, -.15F, -.09F, color);
                vertex(buffer, pose, 0, .165F, color);
                vertex(buffer, pose, 0, .115F, color);
                vertex(buffer, pose, -.13F, -.11F, color);
                vertex(buffer, pose, 0, .165F, color);
                vertex(buffer, pose, .15F, -.09F, color);
                vertex(buffer, pose, .13F, -.11F, color);
                vertex(buffer, pose, 0, .115F, color);
                vertex(buffer, pose, -.13F, -.11F, color);
                vertex(buffer, pose, 0, .115F, color);
                vertex(buffer, pose, 0, .165F, color);
                vertex(buffer, pose, -.15F, -.09F, color);
                vertex(buffer, pose, 0, .115F, color);
                vertex(buffer, pose, .13F, -.11F, color);
                vertex(buffer, pose, .15F, -.09F, color);
                vertex(buffer, pose, 0, .165F, color);
            });
        }
        String label = arrivalTime >= 0 ? "Arrived" : sameDimension ? Math.round(distance) + " blocks"
            : "In " + Journal.dimensionName(point.dimension);
        PoseStack text = new PoseStack();
        text.translate(anchorX - camera.pos.x, anchorY - .12 - camera.pos.y, anchorZ - camera.pos.z);
        text.mulPose(camera.orientation);
        text.scale(.009F, -.009F, .009F);
        collector.submitText(text, -client.font.width(label) / 2F, 0, Component.literal(label).getVisualOrderText(),
            true, Font.DisplayMode.NORMAL, 15728880, alpha | 0xFFFFFF, 0, 0);
    }

    private static void vertex(VertexConsumer buffer, PoseStack.Pose pose, float x, float y, int color) {
        buffer.addVertex(pose, x, y, 0).setColor(color).setUv(.5F, .5F).setLight(15728880);
    }
}
