package com.seedxray.client.diagnostic;

import com.seedxray.client.XrayClientMod;
import com.seedxray.client.config.XrayConfig;
import com.seedxray.client.prediction.ChunkKey;
import com.seedxray.client.prediction.PredictionCache;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.ChunkPos;

public final class DiagnosticScheduler {
    private final ClientSeenBlockScanner scanner;
    private final PredictionCache predictionCache;
    private final DiagnosticCache diagnosticCache;
    private final Queue<ChunkKey> queue = new ArrayDeque<>();
    private final Set<ChunkKey> queued = new HashSet<>();
    private final Map<ChunkKey, Long> lastScannedTick = new HashMap<>();

    public DiagnosticScheduler(ClientSeenBlockScanner scanner, PredictionCache predictionCache, DiagnosticCache diagnosticCache) {
        this.scanner = scanner;
        this.predictionCache = predictionCache;
        this.diagnosticCache = diagnosticCache;
    }

    public void tick(MinecraftClient client) {
        XrayConfig config = XrayClientMod.CONFIG.get();
        if ((!config.displayMode.showsPredictions() && !config.displayMode.showsDiagnostics()) || client.world == null || client.player == null) {
            return;
        }

        String dimensionId = client.world.getRegistryKey().getValue().toString();
        ChunkPos center = client.player.getChunkPos();
        int radius = config.diagnosticScanRadiusChunks;
        boolean fullAnomalyScan = config.displayMode.showsDiagnostics() && config.showClientVisibleUnpredictedOres;
        pruneScanState(dimensionId, center, radius + 2);
        for (int dz = -radius; dz <= radius; dz++) {
            for (int dx = -radius; dx <= radius; dx++) {
                ChunkKey key = new ChunkKey(dimensionId, center.x + dx, center.z + dz);
                if (!fullAnomalyScan && predictionCache.recordsIntersectingChunk(key).isEmpty()) {
                    continue;
                }
                long lastScan = lastScannedTick.getOrDefault(key, Long.MIN_VALUE);
                if (client.world.getTime() - lastScan >= 40L && queued.add(key)) {
                    queue.add(key);
                }
            }
        }

        int budget = config.maxDiagnosticChunksScannedPerTick;
        while (budget-- > 0 && !queue.isEmpty()) {
            ChunkKey key = queue.poll();
            queued.remove(key);
            scanner.scanChunk(client.world, key, predictionCache, diagnosticCache, client.world.getTime());
            lastScannedTick.put(key, client.world.getTime());
        }
    }

    private void pruneScanState(String dimensionId, ChunkPos center, int retainedRadiusChunks) {
        queued.removeIf(key -> isOutsideRetainedRadius(key, dimensionId, center, retainedRadiusChunks));
        queue.removeIf(key -> isOutsideRetainedRadius(key, dimensionId, center, retainedRadiusChunks));
        lastScannedTick.keySet().removeIf(key -> isOutsideRetainedRadius(key, dimensionId, center, retainedRadiusChunks));
    }

    private static boolean isOutsideRetainedRadius(ChunkKey key, String dimensionId, ChunkPos center, int retainedRadiusChunks) {
        return !key.dimensionId().equals(dimensionId)
                || Math.max(Math.abs(key.chunkX() - center.x), Math.abs(key.chunkZ() - center.z)) > retainedRadiusChunks;
    }

    public int queueSize() {
        return queue.size();
    }

    public void clearQueue() {
        queue.clear();
        queued.clear();
        lastScannedTick.clear();
    }
}
