package dev.waypointjournal.mixin;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import dev.waypointjournal.Navigation;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.LevelRenderer;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
abstract class LevelRendererMixin {
    @Inject(method = "renderLevel", at = @At("TAIL"))
    private void waypointjournal$navigation(GraphicsResourceAllocator allocator, DeltaTracker delta, boolean outline, Camera camera,
                                           Matrix4f view, Matrix4f projection, GpuBufferSlice fog, Vector4f color, boolean sky, CallbackInfo ci) {
        Navigation.render(camera, view);
    }
}
