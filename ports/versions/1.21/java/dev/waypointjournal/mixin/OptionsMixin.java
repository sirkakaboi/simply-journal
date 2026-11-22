package dev.waypointjournal.mixin;

import dev.waypointjournal.LoaderClient;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Arrays;

@Mixin(Options.class)
abstract class OptionsMixin {
    @Shadow @Final @Mutable public KeyMapping[] keyMappings;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void waypointjournal$key(CallbackInfo ci) {
        keyMappings = Arrays.copyOf(keyMappings, keyMappings.length + 1);
        keyMappings[keyMappings.length - 1] = LoaderClient.OPEN;
    }
}
