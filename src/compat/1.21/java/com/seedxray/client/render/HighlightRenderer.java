package com.seedxray.client.render;

import com.seedxray.client.XrayClientMod;
import com.seedxray.client.config.XrayConfig;
import com.seedxray.client.prediction.OrePredictionRecord;
import com.seedxray.client.prediction.OreTarget;
import com.seedxray.client.prediction.PredictionVisibilityStatus;
import com.seedxray.client.util.ColorUtil;
import com.seedxray.client.util.MathUtil;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.block.Block;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

public final class HighlightRenderer {
    private static final Identifier ANCIENT_DEBRIS_SIDE = Identifier.of("minecraft", "block/ancient_debris_side");
    private static final Identifier ANCIENT_DEBRIS_TOP = Identifier.of("minecraft", "block/ancient_debris_top");

    public int renderRecords(WorldRenderContext context, Collection<OrePredictionRecord> records, boolean diagnosticRecords) {
        XrayConfig config = XrayClientMod.CONFIG.get();
        boolean renderHighlights = config.showHighlights && (config.showFilledBoxes || config.showOutlines);
        if (!renderHighlights && !config.showOreTextures) {
            return 0;
        }
        if (context.matrixStack() == null || context.camera() == null || context.consumers() == null) {
            return 0;
        }

        MatrixStack matrices = context.matrixStack();
        Vec3d camera = context.camera().getPos();
        VertexConsumerProvider consumers = context.consumers();
        double maxDistanceSq = (double) config.distanceLimitBlocks * (double) config.distanceLimitBlocks;
        int renderLimit = Math.max(1, config.maxRenderedHighlights);
        PriorityQueue<CandidateRecord> nearest = new PriorityQueue<>(
                renderLimit,
                Comparator.comparingDouble(CandidateRecord::distanceSq).reversed()
        );

        for (OrePredictionRecord record : records) {
            if (!config.isOreEnabled(record.oreTarget())) {
                continue;
            }
            if (!config.showMaskedPredictions && record.visibilityStatus() == PredictionVisibilityStatus.PREDICTED_BUT_CLIENT_MASKED) {
                continue;
            }
            if (!config.showClientObstructedPredictions && record.visibilityStatus() == PredictionVisibilityStatus.PREDICTED_CLIENT_OBSTRUCTED) {
                continue;
            }
            double distanceSq = MathUtil.squaredDistanceToCenter(camera, record.pos());
            if (distanceSq > maxDistanceSq) {
                continue;
            }
            boolean wantsTexture = config.showOreTextures && config.isOreTextureEnabled(record.oreTarget());
            boolean wantsHighlight = renderHighlights && config.isOreHighlightEnabled(record.oreTarget());
            if (!wantsTexture && !wantsHighlight) {
                continue;
            }

            CandidateRecord candidate = new CandidateRecord(record, distanceSq, wantsTexture, wantsHighlight);
            if (nearest.size() < renderLimit) {
                nearest.add(candidate);
            } else if (nearest.peek() != null && distanceSq < nearest.peek().distanceSq()) {
                nearest.poll();
                nearest.add(candidate);
            }
        }

        if (nearest.isEmpty()) {
            return 0;
        }

        List<CandidateRecord> candidates = new ArrayList<>(nearest);
        candidates.sort(Comparator.comparingDouble(CandidateRecord::distanceSq));
        List<VisibleRecord> visibleRecords = new ArrayList<>(candidates.size());
        for (CandidateRecord candidate : candidates) {
            Box box = new Box(candidate.record().pos()).offset(-camera.x, -camera.y, -camera.z).expand(0.002D);
            visibleRecords.add(new VisibleRecord(candidate.record(), box, candidate.distanceSq(), candidate.wantsTexture(), candidate.wantsHighlight()));
        }
        int rendered = visibleRecords.size();

        if (config.showOreTextures) {
            VertexConsumer consumer = consumers.getBuffer(XrayRenderLayers.blockTextureNoDepth());
            for (VisibleRecord visibleRecord : visibleRecords) {
                OrePredictionRecord record = visibleRecord.record();
                if (visibleRecord.wantsTexture()) {
                    drawOreTexture(matrices.peek(), consumer, visibleRecord.box(), record, config.alphaFor(record.oreTarget()) / 255.0F);
                }
            }
        }
        if (config.showFilledBoxes) {
            VertexConsumer consumer = consumers.getBuffer(XrayRenderLayers.filledNoDepth());
            for (VisibleRecord visibleRecord : visibleRecords) {
                OrePredictionRecord record = visibleRecord.record();
                if (!visibleRecord.wantsHighlight()) {
                    continue;
                }
                int color = colorFor(record, diagnosticRecords);
                drawFilledBox(matrices.peek(), consumer, visibleRecord.box(), ColorUtil.red(color), ColorUtil.green(color), ColorUtil.blue(color), diagnosticRecords ? 0.35F : 0.72F);
            }
        }
        if (config.showOutlines) {
            VertexConsumer consumer = consumers.getBuffer(XrayRenderLayers.linesNoDepth());
            for (VisibleRecord visibleRecord : visibleRecords) {
                OrePredictionRecord record = visibleRecord.record();
                if (!visibleRecord.wantsHighlight()) {
                    continue;
                }
                int color = colorFor(record, diagnosticRecords);
                drawOutline(matrices.peek(), consumer, visibleRecord.box(), ColorUtil.red(color), ColorUtil.green(color), ColorUtil.blue(color), diagnosticRecords ? 0.95F : ColorUtil.alpha(color));
            }
        }
        return rendered;
    }

    private static void drawOreTexture(MatrixStack.Entry entry, VertexConsumer consumer, Box box, OrePredictionRecord record, float alpha) {
        Sprite side = getBlockSprite(textureIdFor(record));
        Sprite top = record.oreTarget() == OreTarget.ANCIENT_DEBRIS ? getBlockSprite(ANCIENT_DEBRIS_TOP) : side;
        if (side == null) {
            return;
        }
        if (top == null) {
            top = side;
        }

        float minX = (float) box.minX;
        float minY = (float) box.minY;
        float minZ = (float) box.minZ;
        float maxX = (float) box.maxX;
        float maxY = (float) box.maxY;
        float maxZ = (float) box.maxZ;
        texturedFace(entry, consumer, side, minX, minY, minZ, maxX, minY, minZ, maxX, maxY, minZ, minX, maxY, minZ, alpha);
        texturedFace(entry, consumer, side, maxX, minY, maxZ, minX, minY, maxZ, minX, maxY, maxZ, maxX, maxY, maxZ, alpha);
        texturedFace(entry, consumer, side, minX, minY, maxZ, minX, minY, minZ, minX, maxY, minZ, minX, maxY, maxZ, alpha);
        texturedFace(entry, consumer, side, maxX, minY, minZ, maxX, minY, maxZ, maxX, maxY, maxZ, maxX, maxY, minZ, alpha);
        texturedFace(entry, consumer, top, minX, maxY, minZ, maxX, maxY, minZ, maxX, maxY, maxZ, minX, maxY, maxZ, alpha);
        texturedFace(entry, consumer, top, minX, minY, maxZ, maxX, minY, maxZ, maxX, minY, minZ, minX, minY, minZ, alpha);
    }

    private static Identifier textureIdFor(OrePredictionRecord record) {
        if (record.oreTarget() == OreTarget.ANCIENT_DEBRIS) {
            return ANCIENT_DEBRIS_SIDE;
        }
        Block block = record.predictedBlockState() == null ? record.oreTarget().blocks()[0] : record.predictedBlockState().getBlock();
        Identifier blockId = Registries.BLOCK.getId(block);
        return Identifier.of(blockId.getNamespace(), "block/" + blockId.getPath());
    }

    private static Sprite getBlockSprite(Identifier textureId) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getTextureManager() == null) {
            return null;
        }
        AbstractTexture texture = client.getTextureManager().getTexture(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE);
        if (texture instanceof SpriteAtlasTexture atlas) {
            return atlas.getSprite(textureId);
        }
        return null;
    }

    private static void texturedFace(
            MatrixStack.Entry entry,
            VertexConsumer consumer,
            Sprite sprite,
            float x1, float y1, float z1,
            float x2, float y2, float z2,
            float x3, float y3, float z3,
            float x4, float y4, float z4,
            float alpha
    ) {
        texturedVertex(entry, consumer, x1, y1, z1, sprite.getMinU(), sprite.getMaxV(), alpha);
        texturedVertex(entry, consumer, x2, y2, z2, sprite.getMaxU(), sprite.getMaxV(), alpha);
        texturedVertex(entry, consumer, x3, y3, z3, sprite.getMaxU(), sprite.getMinV(), alpha);
        texturedVertex(entry, consumer, x4, y4, z4, sprite.getMinU(), sprite.getMinV(), alpha);
    }

    private static void texturedVertex(MatrixStack.Entry entry, VertexConsumer consumer, float x, float y, float z, float u, float v, float alpha) {
        consumer.vertex(entry, x, y, z).texture(u, v).color(1.0F, 1.0F, 1.0F, alpha);
    }

    private static void drawFilledBox(MatrixStack.Entry entry, VertexConsumer consumer, Box box, float red, float green, float blue, float alpha) {
        float minX = (float) box.minX;
        float minY = (float) box.minY;
        float minZ = (float) box.minZ;
        float maxX = (float) box.maxX;
        float maxY = (float) box.maxY;
        float maxZ = (float) box.maxZ;
        face(entry, consumer, minX, minY, minZ, maxX, minY, minZ, maxX, maxY, minZ, minX, maxY, minZ, red, green, blue, alpha);
        face(entry, consumer, maxX, minY, maxZ, minX, minY, maxZ, minX, maxY, maxZ, maxX, maxY, maxZ, red, green, blue, alpha);
        face(entry, consumer, minX, minY, maxZ, minX, minY, minZ, minX, maxY, minZ, minX, maxY, maxZ, red, green, blue, alpha);
        face(entry, consumer, maxX, minY, minZ, maxX, minY, maxZ, maxX, maxY, maxZ, maxX, maxY, minZ, red, green, blue, alpha);
        face(entry, consumer, minX, maxY, minZ, maxX, maxY, minZ, maxX, maxY, maxZ, minX, maxY, maxZ, red, green, blue, alpha);
        face(entry, consumer, minX, minY, maxZ, maxX, minY, maxZ, maxX, minY, minZ, minX, minY, minZ, red, green, blue, alpha);
    }

    private static void face(
            MatrixStack.Entry entry,
            VertexConsumer consumer,
            float x1, float y1, float z1,
            float x2, float y2, float z2,
            float x3, float y3, float z3,
            float x4, float y4, float z4,
            float red, float green, float blue, float alpha
    ) {
        vertex(entry, consumer, x1, y1, z1, red, green, blue, alpha);
        vertex(entry, consumer, x2, y2, z2, red, green, blue, alpha);
        vertex(entry, consumer, x3, y3, z3, red, green, blue, alpha);
        vertex(entry, consumer, x4, y4, z4, red, green, blue, alpha);
    }

    private static void drawOutline(MatrixStack.Entry entry, VertexConsumer consumer, Box box, float red, float green, float blue, float alpha) {
        float minX = (float) box.minX;
        float minY = (float) box.minY;
        float minZ = (float) box.minZ;
        float maxX = (float) box.maxX;
        float maxY = (float) box.maxY;
        float maxZ = (float) box.maxZ;
        line(entry, consumer, minX, minY, minZ, maxX, minY, minZ, red, green, blue, alpha);
        line(entry, consumer, maxX, minY, minZ, maxX, minY, maxZ, red, green, blue, alpha);
        line(entry, consumer, maxX, minY, maxZ, minX, minY, maxZ, red, green, blue, alpha);
        line(entry, consumer, minX, minY, maxZ, minX, minY, minZ, red, green, blue, alpha);
        line(entry, consumer, minX, maxY, minZ, maxX, maxY, minZ, red, green, blue, alpha);
        line(entry, consumer, maxX, maxY, minZ, maxX, maxY, maxZ, red, green, blue, alpha);
        line(entry, consumer, maxX, maxY, maxZ, minX, maxY, maxZ, red, green, blue, alpha);
        line(entry, consumer, minX, maxY, maxZ, minX, maxY, minZ, red, green, blue, alpha);
        line(entry, consumer, minX, minY, minZ, minX, maxY, minZ, red, green, blue, alpha);
        line(entry, consumer, maxX, minY, minZ, maxX, maxY, minZ, red, green, blue, alpha);
        line(entry, consumer, maxX, minY, maxZ, maxX, maxY, maxZ, red, green, blue, alpha);
        line(entry, consumer, minX, minY, maxZ, minX, maxY, maxZ, red, green, blue, alpha);
    }

    private static void line(
            MatrixStack.Entry entry,
            VertexConsumer consumer,
            float x1, float y1, float z1,
            float x2, float y2, float z2,
            float red, float green, float blue, float alpha
    ) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        float dz = z2 - z1;
        float length = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        float nx = length == 0.0F ? 0.0F : dx / length;
        float ny = length == 0.0F ? 1.0F : dy / length;
        float nz = length == 0.0F ? 0.0F : dz / length;
        consumer.vertex(entry, x1, y1, z1).color(red, green, blue, alpha).normal(entry, nx, ny, nz);
        consumer.vertex(entry, x2, y2, z2).color(red, green, blue, alpha).normal(entry, nx, ny, nz);
    }

    private static void vertex(MatrixStack.Entry entry, VertexConsumer consumer, float x, float y, float z, float red, float green, float blue, float alpha) {
        consumer.vertex(entry, x, y, z).color(red, green, blue, alpha);
    }

    private int colorFor(OrePredictionRecord record, boolean diagnosticRecords) {
        int base = XrayClientMod.CONFIG.get().colorFor(record.oreTarget());
        if (diagnosticRecords || record.visibilityStatus() == PredictionVisibilityStatus.CLIENT_ORE_NOT_PREDICTED) {
            return ColorUtil.withAlpha(base, 210);
        }
        return switch (record.visibilityStatus()) {
            case PREDICTED_AND_CLIENT_MATCHES -> ColorUtil.withAlpha(base, 255);
            case PREDICTED_CLIENT_OBSTRUCTED -> ColorUtil.withAlpha(base, 90);
            case PREDICTED_BUT_CLIENT_MASKED -> ColorUtil.withAlpha(base, 235);
            case UNKNOWN_UNLOADED -> ColorUtil.withAlpha(base, 170);
            default -> ColorUtil.withAlpha(base, 230);
        };
    }

    private record CandidateRecord(OrePredictionRecord record, double distanceSq, boolean wantsTexture, boolean wantsHighlight) {
    }

    private record VisibleRecord(OrePredictionRecord record, Box box, double distanceSq, boolean wantsTexture, boolean wantsHighlight) {
    }
}
