package dev.waypointjournal.mixin;

import dev.waypointjournal.Navigation;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.LevelRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
abstract class LevelRendererMixin {
    @Inject(method = "renderLevel", at = @At("TAIL"))
    private void waypointjournal$navigation(com.mojang.blaze3d.resource.GraphicsResourceAllocator allocator, DeltaTracker delta, boolean outline, Camera camera,
                                           net.minecraft.client.renderer.GameRenderer renderer,
                                           Matrix4f view, Matrix4f projection, CallbackInfo ci) {
        Navigation.render(camera, view, delta);
    }
}
