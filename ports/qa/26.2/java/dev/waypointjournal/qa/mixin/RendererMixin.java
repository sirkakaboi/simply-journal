package dev.waypointjournal.qa.mixin;

import dev.waypointjournal.ReleaseCheck;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
abstract class RendererMixin {
    @Inject(method = "render", at = @At("TAIL"))
    private void waypointjournal$capture(CallbackInfo ci) { ReleaseCheck.render(); }
}
