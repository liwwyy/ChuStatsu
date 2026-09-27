package dev.chustatsu.mixin;

import dev.chustatsu.ChuStatsu;
import net.minecraft.client.network.handler.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.TeamS2CPacket;
import net.minecraft.network.packet.s2c.play.TabListS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerInfoS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayNetworkHandler.class)
public abstract class ClientPlayNetworkHandlerMixin {
    @Inject(method = "handleTeam", at = @At("TAIL"))
    private void chustatsu$observeTeam(TeamS2CPacket packet, CallbackInfo ci) {
        ChuStatsu.controller().observeTeam(packet);
    }

    @Inject(method = "handleTabList", at = @At("TAIL"))
    private void chustatsu$observeTabList(TabListS2CPacket packet, CallbackInfo ci) {
        ChuStatsu.controller().observeTabList(packet);
    }

    @Inject(method = "handlePlayerInfo", at = @At("TAIL"))
    private void chustatsu$observePlayerInfo(PlayerInfoS2CPacket packet, CallbackInfo ci) {
        ChuStatsu.controller().observePlayerInfo(packet);
    }
}
