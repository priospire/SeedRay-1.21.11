package com.seedxray.client.diagnostic;

import com.seedxray.client.XrayClientMod;
import com.seedxray.client.prediction.ChunkKey;
import com.seedxray.client.prediction.OrePredictionRecord;
import com.seedxray.client.prediction.OreSource;
import com.seedxray.client.prediction.PredictionCache;
import com.seedxray.client.prediction.PredictionVisibilityStatus;
import com.seedxray.client.util.BlockUtil;
import java.util.List;
import java.util.Optional;
import net.minecraft.block.BlockState;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;

public final class ClientSeenBlockScanner {
    private final AntiXrayDiagnosticAnalyzer analyzer;

    public ClientSeenBlockScanner(AntiXrayDiagnosticAnalyzer analyzer) {
        this.analyzer = analyzer;
    }

    public void scanChunk(ClientWorld world, ChunkKey key, PredictionCache predictionCache, DiagnosticCache diagnosticCache, long tick) {
        boolean loaded = isChunkLoaded(world, key);
        List<OrePredictionRecord> predictedRecords = predictionCache.recordsIntersectingChunk(key);
        if (!loaded) {
            for (OrePredictionRecord record : predictedRecords) {
                analyzer.markUnloaded(record, tick);
            }
            return;
        }

        for (OrePredictionRecord record : predictedRecords) {
            analyzer.applyClientSeenState(record, world.getBlockState(record.pos()), tick);
        }

        if (XrayClientMod.CONFIG.get().displayMode.showsDiagnostics() && XrayClientMod.CONFIG.get().showClientVisibleUnpredictedOres) {
            scanUnpredictedClientOres(world, key, predictionCache, diagnosticCache, tick);
        }
    }

    private void scanUnpredictedClientOres(ClientWorld world, ChunkKey key, PredictionCache predictionCache, DiagnosticCache diagnosticCache, long tick) {
        ChunkPos chunkPos = key.chunkPos();
        BlockPos.Mutable pos = new BlockPos.Mutable();
        int minY = world.getBottomY();
        int maxY = world.getTopYInclusive();
        for (int x = chunkPos.getStartX(); x <= chunkPos.getEndX(); x++) {
            for (int z = chunkPos.getStartZ(); z <= chunkPos.getEndZ(); z++) {
                for (int y = minY; y <= maxY; y++) {
                    pos.set(x, y, z);
                    if (predictionCache.getAt(key.dimensionId(), pos) != null || diagnosticCache.hasUnpredicted(key.dimensionId(), pos)) {
                        continue;
                    }
                    BlockState state = world.getBlockState(pos);
                    Optional<com.seedxray.client.prediction.OreTarget> target = BlockUtil.oreTargetOf(state);
                    if (target.isEmpty() || !target.get().belongsInDimension(key.dimensionId())) {
                        continue;
                    }
                    diagnosticCache.putUnpredicted(new OrePredictionRecord(
                            pos.toImmutable(),
                            chunkPos,
                            key.dimensionId(),
                            target.get(),
                            OreSource.CLIENT_SEEN,
                            PredictionVisibilityStatus.CLIENT_ORE_NOT_PREDICTED,
                            state,
                            tick,
                            tick,
                            0.0F,
                            "client_seen_loaded_chunk_scan",
                            null
                    ));
                }
            }
        }
    }

    private boolean isChunkLoaded(ClientWorld world, ChunkKey key) {
        return world.getChunkManager().isChunkLoaded(key.chunkX(), key.chunkZ());
    }
}
