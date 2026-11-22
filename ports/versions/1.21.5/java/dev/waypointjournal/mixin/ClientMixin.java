package dev.waypointjournal.mixin;

import dev.waypointjournal.LoaderClient;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
abstract class ClientMixin {
    @Inject(method = "tick", at = @At("TAIL"))
    private void waypointjournal$tick(CallbackInfo ci) { LoaderClient.tick((Minecraft) (Object) this); }
}
