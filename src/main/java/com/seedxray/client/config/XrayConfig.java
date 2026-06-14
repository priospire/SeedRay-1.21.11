package com.seedxray.client.config;

import com.seedxray.client.XrayConstants;
import com.seedxray.client.prediction.OreTarget;
import java.util.HashMap;
import java.util.Map;

public final class XrayConfig {
    public int configVersion = 13;
    public boolean enabled = false;
    public long worldSeed = 0L;
    public boolean seedConfigured = false;
    public DisplayMode displayMode = DisplayMode.COMBINED;
    public int transparencyPercent = 96;
    public boolean useTerrainTransparency = true;
    public boolean useFallbackDimmingOverlay = false;
    public int predictionRadiusChunks = XrayConstants.DEFAULT_PREDICTION_RADIUS_CHUNKS;
    public int diagnosticScanRadiusChunks = XrayConstants.DEFAULT_DIAGNOSTIC_RADIUS_CHUNKS;
    public int maxPredictedChunksPerTick = XrayConstants.DEFAULT_MAX_PREDICTED_CHUNKS_PER_TICK;
    public int maxDiagnosticChunksScannedPerTick = XrayConstants.DEFAULT_MAX_DIAGNOSTIC_CHUNKS_PER_TICK;
    public int maxRenderedHighlights = XrayConstants.DEFAULT_MAX_RENDERED_HIGHLIGHTS;
    public int distanceLimitBlocks = XrayConstants.DEFAULT_DISTANCE_LIMIT_BLOCKS;
    public boolean showHud = true;
    public boolean showLabels = false;
    public boolean showDiagnosticStatus = true;
    public boolean showHighlights = true;
    public boolean showOreTextures = true;
    public boolean showFilledBoxes = true;
    public boolean showOutlines = true;
    public boolean showClientVisibleUnpredictedOres = false;
    public boolean showMaskedPredictions = true;
    public boolean showClientObstructedPredictions = false;
    public Map<String, Boolean> oreFilters = new HashMap<>();
    public Map<String, Boolean> oreHighlightEnabled = new HashMap<>();
    public Map<String, Boolean> oreTextureEnabled = new HashMap<>();
    public Map<String, Integer> oreColors = new HashMap<>();
    public Map<String, Integer> oreAlphas = new HashMap<>();
    public boolean debugLogging = false;
    public boolean clampExtremeRadius = true;
    public boolean clearCacheOnDimensionChange = true;

    public XrayConfig() {
        applyDefaults();
    }

    public void applyDefaults() {
        int originalVersion = configVersion;
        boolean migrateV5 = configVersion < 5;
        if (configVersion < 2) {
            useTerrainTransparency = true;
            useFallbackDimmingOverlay = false;
            showClientVisibleUnpredictedOres = false;
            configVersion = 2;
        }
        if (configVersion < 3) {
            useTerrainTransparency = true;
            useFallbackDimmingOverlay = false;
            showClientVisibleUnpredictedOres = false;
            configVersion = 3;
        }
        if (configVersion < 4) {
            transparencyPercent = 92;
            useTerrainTransparency = true;
            useFallbackDimmingOverlay = false;
            showClientVisibleUnpredictedOres = false;
            configVersion = 4;
        }
        if (configVersion < 5) {
            transparencyPercent = 92;
            useTerrainTransparency = true;
            useFallbackDimmingOverlay = false;
            showClientVisibleUnpredictedOres = false;
            maxPredictedChunksPerTick = XrayConstants.DEFAULT_MAX_PREDICTED_CHUNKS_PER_TICK;
            maxRenderedHighlights = XrayConstants.DEFAULT_MAX_RENDERED_HIGHLIGHTS;
            distanceLimitBlocks = XrayConstants.DEFAULT_DISTANCE_LIMIT_BLOCKS;
            showHud = true;
            configVersion = 5;
        }
        if (configVersion < 6) {
            transparencyPercent = 96;
            showHighlights = true;
            showOreTextures = true;
            configVersion = 6;
        }
        if (displayMode == null) {
            displayMode = DisplayMode.COMBINED;
        }
        if (oreFilters == null) {
            oreFilters = new HashMap<>();
        }
        if (oreColors == null) {
            oreColors = new HashMap<>();
        }
        if (oreHighlightEnabled == null) {
            oreHighlightEnabled = new HashMap<>();
        }
        if (oreTextureEnabled == null) {
            oreTextureEnabled = new HashMap<>();
        }
        if (oreAlphas == null) {
            oreAlphas = new HashMap<>();
        }
        if (configVersion < 7) {
            for (OreTarget target : OreTarget.values()) {
                oreFilters.put(target.name(), true);
            }
            configVersion = 7;
        }
        if (configVersion < 8) {
            configVersion = 8;
        }
        if (configVersion < 9) {
            seedConfigured = seedConfigured || worldSeed != 0L;
            configVersion = 9;
        }
        if (configVersion < 10) {
            if (distanceLimitBlocks < 64) {
                distanceLimitBlocks = XrayConstants.DEFAULT_DISTANCE_LIMIT_BLOCKS;
            }
            if (maxPredictedChunksPerTick > 2) {
                maxPredictedChunksPerTick = XrayConstants.DEFAULT_MAX_PREDICTED_CHUNKS_PER_TICK;
            }
            if (maxRenderedHighlights > XrayConstants.DEFAULT_MAX_RENDERED_HIGHLIGHTS) {
                maxRenderedHighlights = XrayConstants.DEFAULT_MAX_RENDERED_HIGHLIGHTS;
            }
            if (isOreEnabled(OreTarget.ANCIENT_DEBRIS) && !isOreHighlightEnabled(OreTarget.ANCIENT_DEBRIS)) {
                oreHighlightEnabled.put(OreTarget.ANCIENT_DEBRIS.name(), true);
            }
            configVersion = 10;
        }
        if (configVersion < 11) {
            configVersion = 11;
        }
        if (configVersion < 12) {
            if (maxRenderedHighlights > XrayConstants.DEFAULT_MAX_RENDERED_HIGHLIGHTS) {
                maxRenderedHighlights = XrayConstants.DEFAULT_MAX_RENDERED_HIGHLIGHTS;
            }
            configVersion = 12;
        }
        if (configVersion < 13) {
            showClientObstructedPredictions = false;
            configVersion = 13;
        }
        for (OreTarget target : OreTarget.values()) {
            oreFilters.putIfAbsent(target.name(), true);
            oreHighlightEnabled.putIfAbsent(target.name(), true);
            oreTextureEnabled.putIfAbsent(target.name(), target == OreTarget.ANCIENT_DEBRIS);
            oreColors.putIfAbsent(target.name(), target.defaultColor());
            oreAlphas.putIfAbsent(target.name(), 230);
        }
        if (migrateV5) {
            oreColors.put(OreTarget.ANCIENT_DEBRIS.name(), OreTarget.ANCIENT_DEBRIS.defaultColor());
        }
        if (originalVersion < 9) {
            boolean anyOreEnabled = false;
            boolean anyOreHighlightable = false;
            for (OreTarget target : OreTarget.values()) {
                anyOreEnabled |= isOreEnabled(target);
                anyOreHighlightable |= isOreHighlightEnabled(target) || isOreTextureEnabled(target);
            }
            if (!anyOreEnabled) {
                for (OreTarget target : OreTarget.values()) {
                    oreFilters.put(target.name(), true);
                }
            }
            if (!anyOreHighlightable) {
                for (OreTarget target : OreTarget.values()) {
                    oreHighlightEnabled.put(target.name(), true);
                }
            }
            if (!showHighlights && !showOreTextures) {
                showHighlights = true;
            }
        }
        if (showHighlights && !showFilledBoxes && !showOutlines) {
            showOutlines = true;
        }
        transparencyPercent = clamp(transparencyPercent, 0, 99);
        predictionRadiusChunks = clampRadius(predictionRadiusChunks, XrayConstants.DEFAULT_PREDICTION_RADIUS_CHUNKS);
        diagnosticScanRadiusChunks = clampRadius(diagnosticScanRadiusChunks, XrayConstants.DEFAULT_DIAGNOSTIC_RADIUS_CHUNKS);
        maxPredictedChunksPerTick = clamp(maxPredictedChunksPerTick, 1, 4);
        maxDiagnosticChunksScannedPerTick = clamp(maxDiagnosticChunksScannedPerTick, 1, 4);
        maxRenderedHighlights = clamp(maxRenderedHighlights, 100, 10000);
        distanceLimitBlocks = clamp(distanceLimitBlocks, 16, 512);
        for (OreTarget target : OreTarget.values()) {
            oreAlphas.put(target.name(), clamp(oreAlphas.getOrDefault(target.name(), 230), 20, 255));
        }
    }

    public boolean isOreEnabled(OreTarget target) {
        return oreFilters.getOrDefault(target.name(), false);
    }

    public int colorFor(OreTarget target) {
        return oreColors.getOrDefault(target.name(), target.defaultColor());
    }

    public boolean isOreHighlightEnabled(OreTarget target) {
        return oreHighlightEnabled.getOrDefault(target.name(), true);
    }

    public boolean isOreTextureEnabled(OreTarget target) {
        return oreTextureEnabled.getOrDefault(target.name(), target == OreTarget.ANCIENT_DEBRIS);
    }

    public int alphaFor(OreTarget target) {
        return oreAlphas.getOrDefault(target.name(), 230);
    }

    private int clampRadius(int value, int fallback) {
        if (!clampExtremeRadius) {
            return Math.max(1, value);
        }
        return clamp(value <= 0 ? fallback : value, 1, 32);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
