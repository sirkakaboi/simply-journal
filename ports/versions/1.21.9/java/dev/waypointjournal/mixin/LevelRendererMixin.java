package dev.waypointjournal.mixin;

import dev.waypointjournal.Navigation;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.LevelRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
abstract class LevelRendererMixin {
    @Inject(method = "submitEntities", at = @At("TAIL"))
    private void waypointjournal$navigation(com.mojang.blaze3d.vertex.PoseStack pose, LevelRenderState state, SubmitNodeCollector collector, CallbackInfo ci) {
        Navigation.render(state, collector);
    }
}
