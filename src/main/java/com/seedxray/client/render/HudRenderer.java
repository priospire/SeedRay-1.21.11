package com.seedxray.client.render;

import com.seedxray.client.XrayClientMod;
import com.seedxray.client.config.DisplayMode;
import com.seedxray.client.config.XrayConfig;
import com.seedxray.client.diagnostic.DiagnosticCache;
import com.seedxray.client.diagnostic.DiagnosticScheduler;
import com.seedxray.client.prediction.OrePredictionRecord;
import com.seedxray.client.prediction.OreTarget;
import com.seedxray.client.prediction.PredictionCache;
import com.seedxray.client.prediction.PredictionScheduler;
import com.seedxray.client.prediction.PredictionVisibilityStatus;
import com.seedxray.client.util.DimensionUtil;
import com.seedxray.client.util.RenderCompatibility;
import java.util.Collection;
import java.util.stream.Collectors;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;

public final class HudRenderer {
    private final PredictionCache predictionCache;
    private final DiagnosticCache diagnosticCache;
    private final PredictionScheduler predictionScheduler;
    private final DiagnosticScheduler diagnosticScheduler;

    public HudRenderer(
            PredictionCache predictionCache,
            DiagnosticCache diagnosticCache,
            PredictionScheduler predictionScheduler,
            DiagnosticScheduler diagnosticScheduler
    ) {
        this.predictionCache = predictionCache;
        this.diagnosticCache = diagnosticCache;
        this.predictionScheduler = predictionScheduler;
        this.diagnosticScheduler = diagnosticScheduler;
    }

    public void render(DrawContext context, RenderTickCounter tickCounter) {
        DimmingOverlayRenderer.render(context);

        MinecraftClient client = MinecraftClient.getInstance();
        XrayConfig config = XrayClientMod.CONFIG.get();
        if (!config.showHud || client.world == null || client.player == null) {
            return;
        }

        String dimensionId = client.world.getRegistryKey().getValue().toString();
        Collection<OrePredictionRecord> records = predictionCache.recordsNear(dimensionId, client.player.getChunkPos(), renderChunkRadius(config));
        int cachedRecords = predictionCache.recordCount(dimensionId);
        long masked = records.stream().filter(record -> record.visibilityStatus() == PredictionVisibilityStatus.PREDICTED_BUT_CLIENT_MASKED).count();
        long matching = records.stream().filter(record -> record.visibilityStatus() == PredictionVisibilityStatus.PREDICTED_AND_CLIENT_MATCHES).count();
        long obstructed = records.stream().filter(record -> record.visibilityStatus() == PredictionVisibilityStatus.PREDICTED_CLIENT_OBSTRUCTED).count();
        long seedCandidates = records.stream().filter(record -> record.ruleSource().contains("seed_candidate")).count();
        long terrainChecked = records.size() - seedCandidates;

        int x = 8;
        int y = 8;
        int line = 10;
        int color = config.enabled ? 0xE6FFFFFF : 0xAAFFFFFF;
        draw(context, "Seed X-Ray: " + (config.enabled ? "ON" : "OFF"), x, y, config.enabled ? 0xFF73FF8A : 0xFFFF7373);
        y += line;
        draw(context, "Mode: " + readableMode(config.displayMode) + " | Dim: " + DimensionUtil.shortName(dimensionId), x, y, color);
        y += line;
        draw(context, "Seed: " + config.worldSeed + " | Settings: F8", x, y, color);
        y += line;
        if (!config.seedConfigured) {
            draw(context, "Seed not saved yet: open F8 and save the target world seed.", x, y, 0xFFFFC266);
            y += line;
        }
        draw(context, "Radius: " + config.predictionRadiusChunks + " chunks | Near predicted: " + records.size() + " | Rendered: " + RenderStateUtil.lastRenderedPredicted(), x, y, color);
        y += line;
        draw(context, "Render cap: " + config.maxRenderedHighlights + " | Distance: " + config.distanceLimitBlocks + " blocks | Cached: " + cachedRecords, x, y, color);
        y += line;
        draw(context, "Masked: " + masked + " | Matching: " + matching + " | Obstructed: " + obstructed + " | Unpredicted: " + diagnosticCache.unpredictedCount(dimensionId), x, y, color);
        y += line;
        draw(context, "Prediction source: terrain " + terrainChecked + " | seed candidates " + seedCandidates, x, y, color);
        y += line;
        draw(context, "Filter: " + activeFilters(config), x, y, color);
        y += line;
        draw(context, "Queue: prediction " + predictionScheduler.queueSize() + " / diagnostic " + diagnosticScheduler.queueSize(), x, y, color);
        y += line;
        draw(context, "Worldgen: vanilla 1.21.11 seed adapter; custom datapacks may differ", x, y, 0xAEE8E8E8);
        y += line;
        if (RenderCompatibility.shouldDisableTerrainAlphaHooks()) {
            draw(context, "Vulkan renderer detected: terrain alpha hooks disabled; overlay rendering remains active", x, y, 0xFFFFC266);
        }
    }

    private static int renderChunkRadius(XrayConfig config) {
        int distanceChunks = Math.max(1, (config.distanceLimitBlocks + 31) / 16);
        return Math.min(config.predictionRadiusChunks, distanceChunks);
    }

    private static void draw(DrawContext context, String text, int x, int y, int color) {
        MinecraftClient client = MinecraftClient.getInstance();
        context.drawText(client.textRenderer, text, x, y, color, true);
    }

    private static String readableMode(DisplayMode mode) {
        return switch (mode) {
            case PREDICTION_ONLY -> "Prediction";
            case DIAGNOSTIC_ONLY -> "Diagnostic";
            case COMBINED -> "Combined";
        };
    }

    private static String activeFilters(XrayConfig config) {
        String filters = java.util.Arrays.stream(OreTarget.values())
                .filter(config::isOreEnabled)
                .map(OreTarget::displayName)
                .limit(3)
                .collect(Collectors.joining(", "));
        long count = java.util.Arrays.stream(OreTarget.values()).filter(config::isOreEnabled).count();
        if (filters.isEmpty()) {
            return "None";
        }
        if (count > 3) {
            return filters + " +" + (count - 3);
        }
        return filters;
    }
}
