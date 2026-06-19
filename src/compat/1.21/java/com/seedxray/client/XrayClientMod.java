package com.seedxray.client;

import com.seedxray.client.config.ConfigManager;
import com.seedxray.client.diagnostic.AntiXrayDiagnosticAnalyzer;
import com.seedxray.client.diagnostic.ClientSeenBlockScanner;
import com.seedxray.client.diagnostic.DiagnosticCache;
import com.seedxray.client.diagnostic.DiagnosticScheduler;
import com.seedxray.client.input.KeybindManager;
import com.seedxray.client.prediction.PredictionCache;
import com.seedxray.client.prediction.PredictionScheduler;
import com.seedxray.client.prediction.VanillaOrePredictionEngine;
import com.seedxray.client.render.HudRenderer;
import com.seedxray.client.render.XrayRenderer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;

public final class XrayClientMod implements ClientModInitializer {
    public static final ConfigManager CONFIG = new ConfigManager();
    public static final PredictionCache PREDICTION_CACHE = new PredictionCache();
    public static final DiagnosticCache DIAGNOSTIC_CACHE = new DiagnosticCache();

    private static final VanillaOrePredictionEngine PREDICTION_ENGINE = new VanillaOrePredictionEngine();
    private static final AntiXrayDiagnosticAnalyzer DIAGNOSTIC_ANALYZER = new AntiXrayDiagnosticAnalyzer();
    private static final ClientSeenBlockScanner CLIENT_SCANNER = new ClientSeenBlockScanner(DIAGNOSTIC_ANALYZER);
    private static final PredictionScheduler PREDICTION_SCHEDULER = new PredictionScheduler(PREDICTION_ENGINE, PREDICTION_CACHE);
    private static final DiagnosticScheduler DIAGNOSTIC_SCHEDULER = new DiagnosticScheduler(CLIENT_SCANNER, PREDICTION_CACHE, DIAGNOSTIC_CACHE);

    private static final XrayRenderer XRAY_RENDERER = new XrayRenderer(PREDICTION_CACHE, DIAGNOSTIC_CACHE);
    private static final HudRenderer HUD_RENDERER = new HudRenderer(PREDICTION_CACHE, DIAGNOSTIC_CACHE, PREDICTION_SCHEDULER, DIAGNOSTIC_SCHEDULER);
    private static final KeybindManager KEYBINDS = new KeybindManager(PREDICTION_CACHE, DIAGNOSTIC_CACHE, PREDICTION_SCHEDULER, DIAGNOSTIC_SCHEDULER);

    private static String lastDimensionId = "";

    @Override
    public void onInitializeClient() {
        CONFIG.load();
        KEYBINDS.register();

        ClientTickEvents.END_CLIENT_TICK.register(this::onEndClientTick);
        WorldRenderEvents.AFTER_ENTITIES.register(XRAY_RENDERER::render);
        HudRenderCallback.EVENT.register(HUD_RENDERER::render);
    }

    private void onEndClientTick(MinecraftClient client) {
        KEYBINDS.tick(client);

        if (client.world == null || client.player == null) {
            lastDimensionId = "";
            return;
        }

        String dimensionId = client.world.getRegistryKey().getValue().toString();
        if (!dimensionId.equals(lastDimensionId)) {
            if (CONFIG.get().clearCacheOnDimensionChange) {
                PREDICTION_CACHE.clearNonDimension(dimensionId);
                DIAGNOSTIC_CACHE.clearNonDimension(dimensionId);
            }
            PREDICTION_SCHEDULER.clearQueue();
            DIAGNOSTIC_SCHEDULER.clearQueue();
            lastDimensionId = dimensionId;
        }

        if (!CONFIG.get().enabled) {
            return;
        }

        PREDICTION_SCHEDULER.tick(client);
        DIAGNOSTIC_SCHEDULER.tick(client);
    }

    public static void clearPredictionState() {
        PREDICTION_CACHE.clear();
        DIAGNOSTIC_CACHE.clear();
        PREDICTION_SCHEDULER.clearQueue();
        DIAGNOSTIC_SCHEDULER.clearQueue();
        PREDICTION_ENGINE.clearCaches();
    }
}
