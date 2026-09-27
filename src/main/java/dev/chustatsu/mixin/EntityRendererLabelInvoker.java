package dev.chustatsu.mixin;

import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(EntityRenderer.class)
public interface EntityRendererLabelInvoker {
    @Invoker("renderNameTag")
    void chustatsu$drawLabel(Entity entity, String label, double x, double y, double z, int maxDistance);
}
