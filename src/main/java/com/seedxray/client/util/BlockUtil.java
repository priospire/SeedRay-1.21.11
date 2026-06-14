package com.seedxray.client.util;

import com.seedxray.client.prediction.OreTarget;
import java.util.Optional;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.Registries;

public final class BlockUtil {
    private BlockUtil() {
    }

    public static Optional<OreTarget> oreTargetOf(BlockState state) {
        Block block = state.getBlock();
        for (OreTarget target : OreTarget.values()) {
            for (Block targetBlock : target.blocks()) {
                if (block == targetBlock) {
                    return Optional.of(target);
                }
            }
        }
        return Optional.empty();
    }

    public static boolean matchesOreFamily(BlockState state, OreTarget target) {
        return oreTargetOf(state).filter(target::equals).isPresent();
    }

    public static boolean isMaskingTerrain(BlockState state) {
        Block block = state.getBlock();
        return block == Blocks.STONE
                || block == Blocks.DEEPSLATE
                || block == Blocks.NETHERRACK
                || block == Blocks.BASALT
                || block == Blocks.BLACKSTONE
                || block == Blocks.TUFF
                || block == Blocks.GRANITE
                || block == Blocks.DIORITE
                || block == Blocks.ANDESITE;
    }

    public static boolean isTrustedClientObstruction(BlockState state) {
        return state.isAir()
                || !state.getFluidState().isEmpty()
                || state.isOf(Blocks.BEDROCK);
    }

    public static boolean isNetherBaseStone(BlockState state) {
        Block block = state.getBlock();
        return block == Blocks.NETHERRACK
                || block == Blocks.BASALT
                || block == Blocks.BLACKSTONE;
    }

    public static String blockName(BlockState state) {
        return Registries.BLOCK.getId(state.getBlock()).toString();
    }
}
