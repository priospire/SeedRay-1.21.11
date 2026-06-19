package com.seedxray.client.render;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.texture.SpriteAtlasTexture;

public final class XrayRenderLayers {
    private XrayRenderLayers() {
    }

    public static RenderLayer filledNoDepth() {
        return RenderLayer.getDebugFilledBox();
    }

    public static RenderLayer linesNoDepth() {
        return RenderLayer.getLines();
    }

    public static RenderLayer blockTextureNoDepth() {
        return RenderLayer.getEntityTranslucent(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE);
    }
}
