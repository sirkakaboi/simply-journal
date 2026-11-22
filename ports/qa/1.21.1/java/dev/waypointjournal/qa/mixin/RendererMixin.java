package dev.waypointjournal.qa.mixin;

import dev.waypointjournal.PortCheck;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(net.minecraft.client.renderer.GameRenderer.class)
abstract class RendererMixin {
    @Inject(method = "render", at = @At("TAIL"))
    private void waypointjournal$check(CallbackInfo ci) { PortCheck.capture(); }
}
