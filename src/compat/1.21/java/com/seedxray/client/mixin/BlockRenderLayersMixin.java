package com.seedxray.client.mixin;

import com.seedxray.client.render.TerrainTransparencyHooks;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(RenderLayers.class)
public abstract class BlockRenderLayersMixin {
    @Inject(method = "getBlockLayer", at = @At("HEAD"), cancellable = true)
    private static void seedxray$useTranslucentLayer(BlockState state, CallbackInfoReturnable<RenderLayer> cir) {
        if (TerrainTransparencyHooks.shouldMakeBlockTransparent(state)) {
            cir.setReturnValue(RenderLayer.getTranslucent());
        }
    }
}
