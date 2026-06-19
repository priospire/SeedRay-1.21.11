package com.seedxray.client.render;

import com.seedxray.client.XrayClientMod;
import com.seedxray.client.config.XrayConfig;
import com.seedxray.client.diagnostic.DiagnosticCache;
import com.seedxray.client.prediction.OrePredictionRecord;
import com.seedxray.client.prediction.PredictionCache;
import java.util.Collection;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.ChunkPos;

public final class XrayRenderer {
    private final PredictionCache predictionCache;
    private final DiagnosticCache diagnosticCache;
    private final HighlightRenderer highlightRenderer = new HighlightRenderer();

    public XrayRenderer(PredictionCache predictionCache, DiagnosticCache diagnosticCache) {
        this.predictionCache = predictionCache;
        this.diagnosticCache = diagnosticCache;
    }

    public void render(WorldRenderContext context) {
        XrayConfig config = XrayClientMod.CONFIG.get();
        MinecraftClient client = MinecraftClient.getInstance();
        if (!config.enabled || client.world == null || client.player == null) {
            RenderStateUtil.setLastRenderedCounts(0, 0);
            return;
        }

        String dimensionId = client.world.getRegistryKey().getValue().toString();
        ChunkPos center = client.player.getChunkPos();
        int renderChunkRadius = renderChunkRadius(config);
        int predicted = 0;
        int diagnostic = 0;
        if (config.displayMode.showsPredictions()) {
            Collection<OrePredictionRecord> records = predictionCache.recordsNear(dimensionId, center, renderChunkRadius);
            predicted = highlightRenderer.renderRecords(context, records, false);
        }
        if (config.displayMode.showsDiagnostics() && config.showClientVisibleUnpredictedOres) {
            Collection<OrePredictionRecord> records = diagnosticCache.unpredictedNear(dimensionId, center, renderChunkRadius);
            diagnostic = highlightRenderer.renderRecords(context, records, true);
        }
        RenderStateUtil.setLastRenderedCounts(predicted, diagnostic);
    }

    private static int renderChunkRadius(XrayConfig config) {
        int distanceChunks = Math.max(1, (config.distanceLimitBlocks + 31) / 16);
        return Math.min(config.predictionRadiusChunks, distanceChunks);
    }
}
