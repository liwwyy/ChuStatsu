package dev.chustatsu.mixin;

import dev.chustatsu.ChuStatsu;
import net.minecraft.client.gui.GameGui;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameGui.class)
public abstract class GameGuiMixin {
    @Inject(method = "render", at = @At("TAIL"))
    private void chustatsu$renderHud(float tickDelta, CallbackInfo ci) {
        ChuStatsu.controller().renderHud();
        ChuStatsu.controller().renderTab();
    }
}
