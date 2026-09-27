package dev.chustatsu.mixin;

import dev.chustatsu.ChuStatsu;
import net.minecraft.client.entity.living.player.ClientPlayerEntity;
import net.minecraft.client.render.entity.PlayerRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerRenderer.class)
public abstract class PlayerRendererMixin {
    @ModifyVariable(method = "renderNameTag", at = @At("HEAD"), argsOnly = true)
    private String chustatsu$decorateNametag(String original, ClientPlayerEntity player) {
        return ChuStatsu.controller().decorateNametag(player.getName(), original);
    }

    @Inject(method = "renderNameTag(Lnet/minecraft/client/entity/living/player/ClientPlayerEntity;DDDLjava/lang/String;FD)V",
        at = @At("TAIL"))
    private void chustatsu$aboveNametag(ClientPlayerEntity player, double x, double y, double z,
                                         String original, float scale, double distanceSquared, CallbackInfo ci) {
        if (dev.chustatsu.ChuStatsuConfig.nametagDisplayMode != 0) return;
        String value = ChuStatsu.controller().nametagValue(player.getName());
        if (value == null) return;
        boolean belowName = distanceSquared < 100 && player.getScoreboard().getDisplayObjective(2) != null;
        double line = net.minecraft.client.Minecraft.getInstance().textRenderer.fontHeight * 1.15 * scale;
        ((EntityRendererLabelInvoker) this).chustatsu$drawLabel(player, value,
            x, y + line * (belowName ? 2 : 1), z, player.isSneaking() ? 32 : 64);
    }
}
