package dev.chustatsu.mixin;

import dev.chustatsu.ChuStatsu;
import net.minecraft.client.gui.overlay.PlayerTabOverlay;
import net.minecraft.client.network.PlayerInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardObjective;

@Mixin(PlayerTabOverlay.class)
public abstract class PlayerTabOverlayMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void chustatsu$replaceTab(int width, Scoreboard scoreboard, ScoreboardObjective objective, CallbackInfo ci) {
        if (ChuStatsu.controller().shouldRenderTab()) ci.cancel();
    }

    @Inject(method = "getDisplayName", at = @At("RETURN"), cancellable = true)
    private void chustatsu$decorateName(PlayerInfo info, CallbackInfoReturnable<String> cir) {
        String name = info.getProfile().getName();
        cir.setReturnValue(ChuStatsu.controller().decorateTabName(name, cir.getReturnValue()));
    }
}
