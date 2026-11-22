package dev.waypointjournal.qa.mixin;

import dev.waypointjournal.PortCheck;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(net.minecraft.client.Minecraft.class)
abstract class MinecraftMixin {
    @Inject(method = "tick", at = @At("TAIL"))
    private void waypointjournal$check(CallbackInfo ci) { PortCheck.tick(); }
}
