package dev.waypointjournal.mixin;

import dev.waypointjournal.Journal;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundPlayerCombatKillPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
abstract class DeathMixin {
    @Inject(method = "handlePlayerCombatKill", at = @At("TAIL"))
    private void waypointjournal$saveDeath(ClientboundPlayerCombatKillPacket packet, CallbackInfo ci) {
        var player = Minecraft.getInstance().player;
        if (player != null && player.getId() == packet.playerId()) Journal.add(true);
    }
}
