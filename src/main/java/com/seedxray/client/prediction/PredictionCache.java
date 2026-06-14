package com.seedxray.client.prediction;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;

public final class PredictionCache {
    private final Map<ChunkKey, OrePredictionResult> byChunk = new HashMap<>();
    private final Map<String, Map<Long, OrePredictionRecord>> byDimensionAndPos = new HashMap<>();
    private final Map<ChunkKey, Map<Long, OrePredictionRecord>> byBlockChunk = new HashMap<>();

    public synchronized boolean hasChunk(ChunkKey key) {
        return byChunk.containsKey(key);
    }

    public synchronized void put(OrePredictionResult result) {
        byChunk.put(result.chunkKey(), result);
        Map<Long, OrePredictionRecord> posMap = byDimensionAndPos.computeIfAbsent(result.chunkKey().dimensionId(), ignored -> new HashMap<>());
        for (OrePredictionRecord record : result.records()) {
            posMap.put(record.pos().asLong(), record);
            ChunkKey blockChunkKey = new ChunkKey(record.dimensionId(), record.pos().getX() >> 4, record.pos().getZ() >> 4);
            byBlockChunk
                    .computeIfAbsent(blockChunkKey, ignored -> new HashMap<>())
                    .put(record.pos().asLong(), record);
        }
    }

    public synchronized OrePredictionRecord getAt(String dimensionId, BlockPos pos) {
        Map<Long, OrePredictionRecord> posMap = byDimensionAndPos.get(dimensionId);
        if (posMap == null) {
            return null;
        }
        return posMap.get(pos.asLong());
    }

    public synchronized List<OrePredictionRecord> recordsForChunk(ChunkKey key) {
        OrePredictionResult result = byChunk.get(key);
        if (result == null) {
            return List.of();
        }
        return new ArrayList<>(result.records());
    }

    public synchronized List<OrePredictionRecord> recordsIntersectingChunk(ChunkKey key) {
        Map<Long, OrePredictionRecord> records = byBlockChunk.get(key);
        if (records == null) {
            return List.of();
        }
        return new ArrayList<>(records.values());
    }

    public synchronized Collection<OrePredictionRecord> recordsForDimension(String dimensionId) {
        Map<Long, OrePredictionRecord> posMap = byDimensionAndPos.get(dimensionId);
        if (posMap == null) {
            return List.of();
        }
        return new ArrayList<>(posMap.values());
    }

    public synchronized List<OrePredictionRecord> recordsNear(String dimensionId, ChunkPos center, int radiusChunks) {
        List<OrePredictionRecord> records = new ArrayList<>();
        for (int dz = -radiusChunks; dz <= radiusChunks; dz++) {
            for (int dx = -radiusChunks; dx <= radiusChunks; dx++) {
                Map<Long, OrePredictionRecord> chunkRecords = byBlockChunk.get(new ChunkKey(dimensionId, center.x + dx, center.z + dz));
                if (chunkRecords != null) {
                    records.addAll(chunkRecords.values());
                }
            }
        }
        return records;
    }

    public synchronized int recordCount(String dimensionId) {
        Map<Long, OrePredictionRecord> posMap = byDimensionAndPos.get(dimensionId);
        return posMap == null ? 0 : posMap.size();
    }

    public synchronized int chunkCount() {
        return byChunk.size();
    }

    public synchronized void pruneFar(String dimensionId, ChunkPos center, int maxOriginRadiusChunks) {
        byChunk.keySet().removeIf(key -> !key.dimensionId().equals(dimensionId)
                || Math.max(Math.abs(key.chunkX() - center.x), Math.abs(key.chunkZ() - center.z)) > maxOriginRadiusChunks);
        rebuildIndexes();
    }

    public synchronized void clear() {
        byChunk.clear();
        byDimensionAndPos.clear();
        byBlockChunk.clear();
    }

    public synchronized void clearNonDimension(String dimensionId) {
        byChunk.keySet().removeIf(key -> !key.dimensionId().equals(dimensionId));
        byDimensionAndPos.keySet().removeIf(key -> !key.equals(dimensionId));
        byBlockChunk.keySet().removeIf(key -> !key.dimensionId().equals(dimensionId));
    }

    private void rebuildIndexes() {
        byDimensionAndPos.clear();
        byBlockChunk.clear();
        for (OrePredictionResult result : byChunk.values()) {
            Map<Long, OrePredictionRecord> posMap = byDimensionAndPos.computeIfAbsent(result.chunkKey().dimensionId(), ignored -> new HashMap<>());
            for (OrePredictionRecord record : result.records()) {
                posMap.put(record.pos().asLong(), record);
                ChunkKey blockChunkKey = new ChunkKey(record.dimensionId(), record.pos().getX() >> 4, record.pos().getZ() >> 4);
                byBlockChunk
                        .computeIfAbsent(blockChunkKey, ignored -> new HashMap<>())
                        .put(record.pos().asLong(), record);
            }
        }
    }
}
