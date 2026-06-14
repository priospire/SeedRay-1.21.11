package com.seedxray.client.prediction;

import com.seedxray.client.XrayClientMod;
import com.seedxray.client.config.XrayConfig;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.registry.BuiltinRegistries;
import net.minecraft.registry.RegistryEntryLookup;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.util.Identifier;
import net.minecraft.world.biome.source.BiomeCoords;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.ChunkRandom;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.math.random.RandomSeed;
import net.minecraft.util.math.random.Xoroshiro128PlusPlusRandom;
import net.minecraft.world.HeightLimitView;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.source.MultiNoiseBiomeSource;
import net.minecraft.world.biome.source.MultiNoiseBiomeSourceParameterList;
import net.minecraft.world.biome.source.MultiNoiseBiomeSourceParameterLists;
import net.minecraft.world.gen.chunk.ChunkGeneratorSettings;
import net.minecraft.world.gen.chunk.NoiseChunkGenerator;
import net.minecraft.world.gen.chunk.VerticalBlockSample;
import net.minecraft.world.gen.feature.PlacedFeature;
import net.minecraft.world.gen.feature.util.PlacedFeatureIndexer;
import net.minecraft.world.gen.noise.NoiseConfig;
import net.minecraft.util.math.noise.DoublePerlinNoiseSampler;

public final class VanillaOrePredictionEngine implements OrePredictionEngine {
    private static final String ACCURACY =
            "Vanilla 1.21.11 ore adapter: registry-resolved placed feature index, vanilla population/decorator seed math, "
                    + "vanilla placement distributions, seed-derived biome placement checks, local seed terrain replacement checks when available; server-sent ore blocks remain diagnostic only.";
    private static final int MAX_TERRAIN_COLUMN_CACHE_ENTRIES = 16_384;
    private static final List<OreFeatureSpec> SPECS = List.of(
            ore(OreTarget.DIAMOND, "ore_diamond", 7, heightTrapezoid(Offset.aboveBottom(-80), Offset.aboveBottom(80)), 4, 0.5F, TargetRule.OVERWORLD_STONE_DEEPSLATE),
            ore(OreTarget.DIAMOND, "ore_diamond_medium", 2, heightUniform(Offset.absolute(-64), Offset.absolute(-4)), 8, 0.5F, TargetRule.OVERWORLD_STONE_DEEPSLATE),
            oreRarity(OreTarget.DIAMOND, "ore_diamond_large", 9, heightTrapezoid(Offset.aboveBottom(-80), Offset.aboveBottom(80)), 12, 0.7F, TargetRule.OVERWORLD_STONE_DEEPSLATE),
            ore(OreTarget.DIAMOND, "ore_diamond_buried", 4, heightTrapezoid(Offset.aboveBottom(-80), Offset.aboveBottom(80)), 8, 1.0F, TargetRule.OVERWORLD_STONE_DEEPSLATE),
            ore(OreTarget.EMERALD, "ore_emerald", 100, heightTrapezoid(Offset.absolute(-16), Offset.absolute(480)), 3, 0.0F, TargetRule.OVERWORLD_STONE_DEEPSLATE),
            ore(OreTarget.GOLD, "ore_gold", 4, heightTrapezoid(Offset.absolute(-64), Offset.absolute(32)), 9, 0.5F, TargetRule.OVERWORLD_STONE_DEEPSLATE),
            oreUniformCount(OreTarget.GOLD, "ore_gold_lower", 0, 1, heightUniform(Offset.absolute(-64), Offset.absolute(-48)), 9, 0.5F, TargetRule.OVERWORLD_STONE_DEEPSLATE),
            ore(OreTarget.GOLD, "ore_gold_extra", 50, heightUniform(Offset.absolute(32), Offset.absolute(256)), 9, 0.0F, TargetRule.OVERWORLD_STONE_DEEPSLATE),
            ore(OreTarget.IRON, "ore_iron_upper", 90, heightTrapezoid(Offset.absolute(80), Offset.absolute(384)), 9, 0.0F, TargetRule.OVERWORLD_STONE_DEEPSLATE),
            ore(OreTarget.IRON, "ore_iron_middle", 10, heightTrapezoid(Offset.absolute(-24), Offset.absolute(56)), 9, 0.0F, TargetRule.OVERWORLD_STONE_DEEPSLATE),
            ore(OreTarget.IRON, "ore_iron_small", 10, heightUniform(Offset.aboveBottom(0), Offset.absolute(72)), 4, 0.0F, TargetRule.OVERWORLD_STONE_DEEPSLATE),
            ore(OreTarget.COAL, "ore_coal_upper", 30, heightUniform(Offset.absolute(136), Offset.belowTop(0)), 17, 0.0F, TargetRule.OVERWORLD_STONE_DEEPSLATE),
            ore(OreTarget.COAL, "ore_coal_lower", 20, heightTrapezoid(Offset.absolute(0), Offset.absolute(192)), 17, 0.5F, TargetRule.OVERWORLD_STONE_DEEPSLATE),
            ore(OreTarget.COPPER, "ore_copper", 16, heightTrapezoid(Offset.absolute(-16), Offset.absolute(112)), 10, 0.0F, TargetRule.OVERWORLD_STONE_DEEPSLATE),
            ore(OreTarget.COPPER, "ore_copper_large", 16, heightTrapezoid(Offset.absolute(-16), Offset.absolute(112)), 20, 0.0F, TargetRule.OVERWORLD_STONE_DEEPSLATE),
            ore(OreTarget.REDSTONE, "ore_redstone", 4, heightUniform(Offset.aboveBottom(0), Offset.absolute(15)), 8, 0.0F, TargetRule.OVERWORLD_STONE_DEEPSLATE),
            ore(OreTarget.REDSTONE, "ore_redstone_lower", 8, heightTrapezoid(Offset.aboveBottom(-32), Offset.aboveBottom(32)), 8, 0.0F, TargetRule.OVERWORLD_STONE_DEEPSLATE),
            ore(OreTarget.LAPIS, "ore_lapis", 2, heightTrapezoid(Offset.absolute(-32), Offset.absolute(32)), 7, 0.0F, TargetRule.OVERWORLD_STONE_DEEPSLATE),
            ore(OreTarget.LAPIS, "ore_lapis_buried", 4, heightUniform(Offset.aboveBottom(0), Offset.absolute(64)), 7, 1.0F, TargetRule.OVERWORLD_STONE_DEEPSLATE),
            scattered(OreTarget.ANCIENT_DEBRIS, "ore_ancient_debris_large", heightTrapezoid(Offset.absolute(8), Offset.absolute(24)), 3, 1.0F, TargetRule.NETHER_BASE_STONE),
            scattered(OreTarget.ANCIENT_DEBRIS, "ore_debris_small", heightUniform(Offset.aboveBottom(8), Offset.belowTop(8)), 2, 1.0F, TargetRule.NETHER_BASE_STONE),
            ore(OreTarget.NETHER_GOLD, "ore_gold_nether", 10, heightUniform(Offset.aboveBottom(10), Offset.belowTop(10)), 10, 0.0F, TargetRule.NETHERRACK_ONLY),
            ore(OreTarget.NETHER_GOLD, "ore_gold_deltas", 20, heightUniform(Offset.aboveBottom(10), Offset.belowTop(10)), 10, 0.0F, TargetRule.NETHERRACK_ONLY),
            ore(OreTarget.NETHER_QUARTZ, "ore_quartz_nether", 16, heightUniform(Offset.aboveBottom(10), Offset.belowTop(10)), 14, 0.0F, TargetRule.NETHERRACK_ONLY),
            ore(OreTarget.NETHER_QUARTZ, "ore_quartz_deltas", 32, heightUniform(Offset.aboveBottom(10), Offset.belowTop(10)), 14, 0.0F, TargetRule.NETHERRACK_ONLY)
    );
    private static final Map<Identifier, FallbackFeatureIndex> VANILLA_FALLBACK_INDICES = Map.ofEntries(
            fallback("ore_coal_upper", 6, 9),
            fallback("ore_coal_lower", 6, 10),
            fallback("ore_iron_upper", 6, 11),
            fallback("ore_iron_middle", 6, 12),
            fallback("ore_iron_small", 6, 13),
            fallback("ore_gold", 6, 14),
            fallback("ore_gold_lower", 6, 15),
            fallback("ore_redstone", 6, 16),
            fallback("ore_redstone_lower", 6, 17),
            fallback("ore_diamond", 6, 18),
            fallback("ore_diamond_medium", 6, 19),
            fallback("ore_diamond_large", 6, 20),
            fallback("ore_diamond_buried", 6, 21),
            fallback("ore_lapis", 6, 22),
            fallback("ore_lapis_buried", 6, 23),
            fallback("ore_copper", 6, 24),
            fallback("ore_gold_extra", 6, 26),
            fallback("ore_copper_large", 6, 27),
            fallback("ore_emerald", 6, 29),
            fallback("ore_dirt", 6, 0),
            fallback("ore_gravel", 6, 1),
            fallback("ore_granite_upper", 6, 2),
            fallback("ore_granite_lower", 6, 3),
            fallback("ore_diorite_upper", 6, 4),
            fallback("ore_diorite_lower", 6, 5),
            fallback("ore_andesite_upper", 6, 6),
            fallback("ore_andesite_lower", 6, 7),
            fallback("ore_tuff", 6, 8),
            fallback("ore_infested", 6, 28),
            fallback("ore_magma", 7, 0),
            fallback("ore_soul_sand", 7, 1),
            fallback("ore_gravel_nether", 7, 9),
            fallback("ore_blackstone", 7, 10),
            fallback("ore_gold_nether", 7, 11),
            fallback("ore_gold_deltas", 7, 11),
            fallback("ore_quartz_nether", 7, 12),
            fallback("ore_quartz_deltas", 7, 12),
            fallback("ore_ancient_debris_large", 7, 13),
            fallback("ore_debris_small", 7, 14)
    );

    private final Map<String, LocalTerrainSampler> terrainSamplers = new HashMap<>();
    private long terrainSeed = Long.MIN_VALUE;

    public static OrePredictionResult predictSeedCandidatesForChunk(String dimensionId, int chunkX, int chunkZ, long seed, Set<OreTarget> enabledTargets, long tick) {
        OrePredictionResult result = new OrePredictionResult(new ChunkKey(dimensionId, chunkX, chunkZ), false, ACCURACY);
        if ("minecraft:the_end".equals(dimensionId)) {
            return result;
        }

        ChunkPos chunkPos = new ChunkPos(chunkX, chunkZ);
        ChunkRandom random = new ChunkRandom(new Xoroshiro128PlusPlusRandom(RandomSeed.getSeed()));
        long populationSeed = random.setPopulationSeed(seed, chunkPos.getStartX(), chunkPos.getStartZ());
        LocalTerrainSampler terrainSampler = LocalTerrainSampler.createUnavailable();
        GeneratedStateTracker generatedStates = new GeneratedStateTracker();
        Set<Long> emitted = new HashSet<>();
        int bottomY = defaultBottomY(dimensionId);
        int height = defaultHeight(dimensionId);

        for (OreFeatureSpec spec : SPECS) {
            if (!spec.target().belongsInDimension(dimensionId) || !enabledTargets.contains(spec.target())) {
                continue;
            }
            ResolvedFeature resolvedFeature = fallbackFeature(spec.placedFeatureId());
            if (resolvedFeature == null) {
                continue;
            }
            generateSpec(result, terrainSampler, generatedStates, spec, random, populationSeed, resolvedFeature, chunkPos, dimensionId, bottomY, height, tick, emitted, true);
        }

        return result;
    }

    public static int countSeedPlacementOriginsForChunk(String dimensionId, int chunkX, int chunkZ, long seed, Set<OreTarget> enabledTargets) {
        if ("minecraft:the_end".equals(dimensionId)) {
            return 0;
        }

        int startX = chunkX << 4;
        int startZ = chunkZ << 4;
        ChunkRandom random = new ChunkRandom(new Xoroshiro128PlusPlusRandom(RandomSeed.getSeed()));
        long populationSeed = random.setPopulationSeed(seed, startX, startZ);
        int bottomY = defaultBottomY(dimensionId);
        int height = defaultHeight(dimensionId);
        int count = 0;

        for (OreFeatureSpec spec : SPECS) {
            if (!spec.target().belongsInDimension(dimensionId) || !enabledTargets.contains(spec.target())) {
                continue;
            }
            ResolvedFeature resolvedFeature = fallbackFeature(spec.placedFeatureId());
            if (resolvedFeature == null) {
                continue;
            }
            random.setDecoratorSeed(populationSeed, resolvedFeature.featureIndex(), resolvedFeature.generationStep());
            count += countPlacementOrigins(random, spec, bottomY, height);
        }

        return count;
    }

    public static OrePredictionResult predictLocalVanillaForChunk(String dimensionId, int chunkX, int chunkZ, long seed, Set<OreTarget> enabledTargets, long tick) {
        OrePredictionResult result = new OrePredictionResult(new ChunkKey(dimensionId, chunkX, chunkZ), false, ACCURACY);
        if ("minecraft:the_end".equals(dimensionId)) {
            return result;
        }
        LocalTerrainSampler terrainSampler = createLocalTerrainSampler(dimensionId, seed);
        if (terrainSampler.isUnavailable()) {
            return result;
        }

        ChunkPos chunkPos = new ChunkPos(chunkX, chunkZ);
        ChunkRandom random = new ChunkRandom(new Xoroshiro128PlusPlusRandom(RandomSeed.getSeed()));
        long populationSeed = random.setPopulationSeed(seed, chunkPos.getStartX(), chunkPos.getStartZ());
        GeneratedStateTracker generatedStates = new GeneratedStateTracker();
        Set<Long> emitted = new HashSet<>();
        Map<Identifier, ResolvedFeature> resolved = terrainSampler.resolveFeatureIndices();
        int bottomY = defaultBottomY(dimensionId);
        int height = defaultHeight(dimensionId);

        generateResolvedFeatures(result, terrainSampler, generatedStates, random, populationSeed, resolved, chunkPos, dimensionId, bottomY, height, tick, emitted, enabledTargets);

        return result;
    }

    @Override
    public OrePredictionResult predictChunk(ClientWorld world, ChunkKey chunkKey, long tick) {
        OrePredictionResult result = new OrePredictionResult(chunkKey, false, ACCURACY);
        if ("minecraft:the_end".equals(chunkKey.dimensionId())) {
            return result;
        }

        XrayConfig config = XrayClientMod.CONFIG.get();
        long seed = WorldSeedContext.serverSeed();
        if (terrainSeed != seed) {
            terrainSamplers.clear();
            terrainSeed = seed;
        }
        LocalTerrainSampler terrainSampler = resolveTerrainSampler(world, chunkKey.dimensionId());
        if (terrainSampler.isUnavailable()) {
            return result;
        }
        Map<Identifier, ResolvedFeature> resolved = terrainSampler.resolveFeatureIndices();
        ChunkPos chunkPos = chunkKey.chunkPos();
        ChunkRandom random = new ChunkRandom(new Xoroshiro128PlusPlusRandom(RandomSeed.getSeed()));
        long populationSeed = random.setPopulationSeed(seed, chunkPos.getStartX(), chunkPos.getStartZ());
        GeneratedStateTracker generatedStates = new GeneratedStateTracker();
        Set<Long> emitted = new HashSet<>();
        Set<OreTarget> enabledTargets = EnumSet.noneOf(OreTarget.class);
        for (OreTarget target : OreTarget.values()) {
            if (config.isOreEnabled(target)) {
                enabledTargets.add(target);
            }
        }

        generateResolvedFeatures(result, terrainSampler, generatedStates, random, populationSeed, resolved, chunkPos, chunkKey.dimensionId(), world.getBottomY(), world.getHeight(), tick, emitted, enabledTargets);

        return result;
    }

    public void clearCaches() {
        terrainSamplers.clear();
        terrainSeed = Long.MIN_VALUE;
    }

    private static void generateSpec(
            OrePredictionResult result,
            LocalTerrainSampler terrainSampler,
            GeneratedStateTracker generatedStates,
            OreFeatureSpec spec,
            ChunkRandom random,
            long populationSeed,
            ResolvedFeature resolvedFeature,
            ChunkPos chunkPos,
            String dimensionId,
            int bottomY,
            int height,
            long tick,
            Set<Long> emitted,
            boolean forceSeedCandidates
    ) {
        generateSpec(result, terrainSampler, generatedStates, spec, random, populationSeed, resolvedFeature, chunkPos, dimensionId, bottomY, height, tick, emitted, forceSeedCandidates, true);
    }

    private static void generateSpec(
            OrePredictionResult result,
            LocalTerrainSampler terrainSampler,
            GeneratedStateTracker generatedStates,
            OreFeatureSpec spec,
            ChunkRandom random,
            long populationSeed,
            ResolvedFeature resolvedFeature,
            ChunkPos chunkPos,
            String dimensionId,
            int bottomY,
            int height,
            long tick,
            Set<Long> emitted,
            boolean forceSeedCandidates,
            boolean emitRecord
    ) {
        random.setDecoratorSeed(populationSeed, resolvedFeature.featureIndex(), resolvedFeature.generationStep());
        int count = spec.count().sample(random);
        for (int index = 0; index < count; index++) {
            BlockPos origin = generateOrigin(random, chunkPos, spec, bottomY, height);
            if (!forceSeedCandidates && !terrainSampler.allowsFeature(spec.placedFeatureId(), origin)) {
                continue;
            }
            if (spec.kind() == FeatureKind.SCATTERED_ORE) {
                addScatteredOre(result, terrainSampler, generatedStates, spec, random, origin, chunkPos, dimensionId, tick, emitted, resolvedFeature, forceSeedCandidates, emitRecord);
            } else {
                addOreVein(result, terrainSampler, generatedStates, spec, random, origin, chunkPos, dimensionId, tick, emitted, resolvedFeature, forceSeedCandidates, emitRecord);
            }
        }
    }

    private LocalTerrainSampler resolveTerrainSampler(ClientWorld world, String dimensionId) {
        return terrainSamplers.computeIfAbsent(dimensionId, ignored -> createLocalTerrainSampler(dimensionId, WorldSeedContext.serverSeed()));
    }

    private static void generateResolvedFeatures(
            OrePredictionResult result,
            LocalTerrainSampler terrainSampler,
            GeneratedStateTracker generatedStates,
            ChunkRandom random,
            long populationSeed,
            Map<Identifier, ResolvedFeature> resolved,
            ChunkPos chunkPos,
            String dimensionId,
            int bottomY,
            int height,
            long tick,
            Set<Long> emitted,
            Set<OreTarget> enabledTargets
    ) {
        for (ResolvedGenerationSpec resolvedSpec : resolveGenerationSpecs(resolved, dimensionId, enabledTargets)) {
            if (resolvedSpec.oreSpec() != null) {
                generateSpec(result, terrainSampler, generatedStates, resolvedSpec.oreSpec(), random, populationSeed, resolvedSpec.resolvedFeature(), chunkPos, dimensionId, bottomY, height, tick, emitted, false, resolvedSpec.emitRecord());
            } else if (resolvedSpec.blockerSpec() != null) {
                generateBlockerSpec(terrainSampler, generatedStates, random, populationSeed, resolvedSpec.blockerSpec(), resolvedSpec.resolvedFeature(), chunkPos, bottomY, height);
            }
        }
    }

    private static List<ResolvedGenerationSpec> resolveGenerationSpecs(Map<Identifier, ResolvedFeature> resolved, String dimensionId, Set<OreTarget> enabledTargets) {
        List<ResolvedGenerationSpec> specs = new ArrayList<>();
        for (BlockerFeatureSpec spec : blockerSpecs()) {
            if (!spec.belongsInDimension(dimensionId)) {
                continue;
            }
            ResolvedFeature resolvedFeature = resolvedFeature(resolved, spec.placedFeatureId());
            if (resolvedFeature != null) {
                specs.add(ResolvedGenerationSpec.blocker(spec, resolvedFeature));
            }
        }
        for (OreFeatureSpec spec : SPECS) {
            if (!spec.target().belongsInDimension(dimensionId)) {
                continue;
            }
            ResolvedFeature resolvedFeature = resolvedFeature(resolved, spec.placedFeatureId());
            if (resolvedFeature != null) {
                specs.add(ResolvedGenerationSpec.ore(spec, resolvedFeature, enabledTargets.contains(spec.target())));
            }
        }
        specs.sort(Comparator
                .comparingInt((ResolvedGenerationSpec spec) -> spec.resolvedFeature().generationStep())
                .thenComparingInt(spec -> spec.resolvedFeature().featureIndex()));
        return specs;
    }

    private static void generateBlockerSpec(
            LocalTerrainSampler terrainSampler,
            GeneratedStateTracker generatedStates,
            ChunkRandom random,
            long populationSeed,
            BlockerFeatureSpec spec,
            ResolvedFeature resolvedFeature,
            ChunkPos chunkPos,
            int bottomY,
            int height
    ) {
        random.setDecoratorSeed(populationSeed, resolvedFeature.featureIndex(), resolvedFeature.generationStep());
        int count = spec.count().sample(random);
        for (int index = 0; index < count; index++) {
            BlockPos origin = generateOrigin(random, chunkPos, spec, bottomY, height);
            if (!terrainSampler.allowsFeature(spec.placedFeatureId(), origin)) {
                continue;
            }
            visitOreVeinPositions(terrainSampler, random, origin, spec.size(), false,
                    pos -> addBlockerIfReplaceable(terrainSampler, generatedStates, spec, random, pos));
        }
    }

    private static void addBlockerIfReplaceable(
            LocalTerrainSampler terrainSampler,
            GeneratedStateTracker generatedStates,
            BlockerFeatureSpec spec,
            Random random,
            BlockPos pos
    ) {
        if (!terrainSampler.isInHeightLimit(pos.getY())) {
            return;
        }
        BlockState baseState = terrainSampler.getState(pos, generatedStates);
        if (spec.rule().predictedState(spec.replacementState(), baseState, random) == null) {
            return;
        }
        generatedStates.put(pos, spec.replacementState());
    }

    private static ResolvedFeature resolvedFeature(Map<Identifier, ResolvedFeature> resolved, Identifier placedFeatureId) {
        ResolvedFeature resolvedFeature = resolved.get(placedFeatureId);
        return resolvedFeature == null ? fallbackFeature(placedFeatureId) : resolvedFeature;
    }

    private static LocalTerrainSampler createLocalTerrainSampler(String dimensionId, long seed) {
        try {
            var lookup = BuiltinRegistries.createWrapperLookup();
            RegistryEntryLookup<ChunkGeneratorSettings> settingsRegistry = lookup.getOrThrow(RegistryKeys.CHUNK_GENERATOR_SETTINGS);
            RegistryEntryLookup<DoublePerlinNoiseSampler.NoiseParameters> noiseRegistry = lookup.getOrThrow(RegistryKeys.NOISE_PARAMETERS);
            RegistryEntryLookup<MultiNoiseBiomeSourceParameterList> biomeParameterRegistry =
                    lookup.getOrThrow(RegistryKeys.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST);
            RegistryEntryLookup<PlacedFeature> placedFeatureRegistry = lookup.getOrThrow(RegistryKeys.PLACED_FEATURE);
            RegistryEntry.Reference<ChunkGeneratorSettings> settingsEntry;
            RegistryEntry.Reference<MultiNoiseBiomeSourceParameterList> biomeParameters;
            if ("minecraft:the_nether".equals(dimensionId)) {
                settingsEntry = settingsRegistry.getOrThrow(ChunkGeneratorSettings.NETHER);
                biomeParameters = biomeParameterRegistry.getOrThrow(MultiNoiseBiomeSourceParameterLists.NETHER);
            } else if ("minecraft:overworld".equals(dimensionId)) {
                settingsEntry = settingsRegistry.getOrThrow(ChunkGeneratorSettings.OVERWORLD);
                biomeParameters = biomeParameterRegistry.getOrThrow(MultiNoiseBiomeSourceParameterLists.OVERWORLD);
            } else {
                return LocalTerrainSampler.createUnavailable();
            }
            NoiseChunkGenerator generator = new NoiseChunkGenerator(MultiNoiseBiomeSource.create(biomeParameters), settingsEntry);
            NoiseConfig noiseConfig = NoiseConfig.create(settingsEntry.value(), noiseRegistry, seed);
            return new LocalTerrainSampler(generator, noiseConfig, placedFeatureRegistry, false);
        } catch (RuntimeException ignoredException) {
            return LocalTerrainSampler.createUnavailable();
        }
    }

    private static BlockPos generateOrigin(Random random, ChunkPos chunkPos, OreFeatureSpec spec, int bottomY, int height) {
        int x = chunkPos.getStartX() + random.nextInt(16);
        int z = chunkPos.getStartZ() + random.nextInt(16);
        int y = spec.height().sample(random, bottomY, height);
        return new BlockPos(x, y, z);
    }

    private static BlockPos generateOrigin(Random random, ChunkPos chunkPos, BlockerFeatureSpec spec, int bottomY, int height) {
        int x = chunkPos.getStartX() + random.nextInt(16);
        int z = chunkPos.getStartZ() + random.nextInt(16);
        int y = spec.height().sample(random, bottomY, height);
        return new BlockPos(x, y, z);
    }

    private static int countPlacementOrigins(Random random, OreFeatureSpec spec, int bottomY, int height) {
        int count = spec.count().sample(random);
        if (count <= 0) {
            return 0;
        }
        for (int i = 0; i < count; i++) {
            random.nextInt(16);
            random.nextInt(16);
            spec.height().sample(random, bottomY, height);
        }
        return count;
    }

    private static void addScatteredOre(
            OrePredictionResult result,
            LocalTerrainSampler terrainSampler,
            GeneratedStateTracker generatedStates,
            OreFeatureSpec spec,
            Random random,
            BlockPos origin,
            ChunkPos chunkPos,
            String dimensionId,
            long tick,
            Set<Long> emitted,
            ResolvedFeature resolvedFeature,
            boolean forceSeedCandidates,
            boolean emitRecord
    ) {
        int attempts = random.nextInt(spec.size() + 1);
        for (int index = 0; index < attempts; index++) {
            int spread = Math.min(index, 7);
            BlockPos pos = origin.add(getSpread(random, spread), getSpread(random, spread), getSpread(random, spread));
            addIfReplaceable(result, terrainSampler, generatedStates, spec, random, pos, chunkPos, dimensionId, tick, emitted, resolvedFeature, forceSeedCandidates, emitRecord);
        }
    }

    private static void addOreVein(
            OrePredictionResult result,
            LocalTerrainSampler terrainSampler,
            GeneratedStateTracker generatedStates,
            OreFeatureSpec spec,
            Random random,
            BlockPos origin,
            ChunkPos chunkPos,
            String dimensionId,
            long tick,
            Set<Long> emitted,
            ResolvedFeature resolvedFeature,
            boolean forceSeedCandidates,
            boolean emitRecord
    ) {
        visitOreVeinPositions(terrainSampler, random, origin, spec.size(), forceSeedCandidates,
                pos -> addIfReplaceable(result, terrainSampler, generatedStates, spec, random, pos, chunkPos, dimensionId, tick, emitted, resolvedFeature, forceSeedCandidates, emitRecord));
    }

    private static void visitOreVeinPositions(
            LocalTerrainSampler terrainSampler,
            Random random,
            BlockPos origin,
            int size,
            boolean forceSeedCandidates,
            PositionConsumer consumer
    ) {
        float angle = random.nextFloat() * (float) Math.PI;
        float horizontalRadius = (float) size / 8.0F;
        int radius = MathHelper.ceil(((float) size / 16.0F * 2.0F + 1.0F) / 2.0F);
        double startX = (double) origin.getX() + Math.sin(angle) * horizontalRadius;
        double endX = (double) origin.getX() - Math.sin(angle) * horizontalRadius;
        double startZ = (double) origin.getZ() + Math.cos(angle) * horizontalRadius;
        double endZ = (double) origin.getZ() - Math.cos(angle) * horizontalRadius;
        double startY = origin.getY() + random.nextInt(3) - 2;
        double endY = origin.getY() + random.nextInt(3) - 2;
        int minX = origin.getX() - MathHelper.ceil(horizontalRadius) - radius;
        int minY = origin.getY() - 2 - radius;
        int minZ = origin.getZ() - MathHelper.ceil(horizontalRadius) - radius;
        int horizontalSize = 2 * (MathHelper.ceil(horizontalRadius) + radius);
        int verticalSize = 2 * (2 + radius);

        boolean canStart = false;
        for (int x = minX; x <= minX + horizontalSize && !canStart; x++) {
            for (int z = minZ; z <= minZ + horizontalSize; z++) {
                if (forceSeedCandidates || minY <= terrainSampler.topY(x, z)) {
                    canStart = true;
                    break;
                }
            }
        }
        if (!canStart) {
            return;
        }

        double[] spheres = new double[size * 4];
        for (int index = 0; index < size; index++) {
            float progress = (float) index / (float) size;
            double centerX = MathHelper.lerp(progress, startX, endX);
            double centerY = MathHelper.lerp(progress, startY, endY);
            double centerZ = MathHelper.lerp(progress, startZ, endZ);
            double randomRadius = random.nextDouble() * size / 16.0D;
            double sphereRadius = ((MathHelper.sin((float) Math.PI * progress) + 1.0D) * randomRadius + 1.0D) / 2.0D;
            spheres[index * 4] = centerX;
            spheres[index * 4 + 1] = centerY;
            spheres[index * 4 + 2] = centerZ;
            spheres[index * 4 + 3] = sphereRadius;
        }

        for (int first = 0; first < size - 1; first++) {
            if (spheres[first * 4 + 3] <= 0.0D) {
                continue;
            }
            for (int second = first + 1; second < size; second++) {
                if (spheres[second * 4 + 3] <= 0.0D) {
                    continue;
                }
                double dx = spheres[first * 4] - spheres[second * 4];
                double dy = spheres[first * 4 + 1] - spheres[second * 4 + 1];
                double dz = spheres[first * 4 + 2] - spheres[second * 4 + 2];
                double dr = spheres[first * 4 + 3] - spheres[second * 4 + 3];
                if (dr * dr > dx * dx + dy * dy + dz * dz) {
                    if (dr > 0.0D) {
                        spheres[second * 4 + 3] = -1.0D;
                    } else {
                        spheres[first * 4 + 3] = -1.0D;
                    }
                }
            }
        }

        BitSet visited = new BitSet(horizontalSize * verticalSize * horizontalSize);
        for (int index = 0; index < size; index++) {
            double sphereRadius = spheres[index * 4 + 3];
            if (sphereRadius < 0.0D) {
                continue;
            }
            double centerX = spheres[index * 4];
            double centerY = spheres[index * 4 + 1];
            double centerZ = spheres[index * 4 + 2];
            int startBlockX = Math.max(MathHelper.floor(centerX - sphereRadius), minX);
            int startBlockY = Math.max(MathHelper.floor(centerY - sphereRadius), minY);
            int startBlockZ = Math.max(MathHelper.floor(centerZ - sphereRadius), minZ);
            int endBlockX = Math.max(MathHelper.floor(centerX + sphereRadius), startBlockX);
            int endBlockY = Math.max(MathHelper.floor(centerY + sphereRadius), startBlockY);
            int endBlockZ = Math.max(MathHelper.floor(centerZ + sphereRadius), startBlockZ);
            for (int x = startBlockX; x <= endBlockX; x++) {
                double normalizedX = ((double) x + 0.5D - centerX) / sphereRadius;
                if (normalizedX * normalizedX >= 1.0D) {
                    continue;
                }
                for (int y = startBlockY; y <= endBlockY; y++) {
                    double normalizedY = ((double) y + 0.5D - centerY) / sphereRadius;
                    if (normalizedX * normalizedX + normalizedY * normalizedY >= 1.0D) {
                        continue;
                    }
                    if (!forceSeedCandidates && !terrainSampler.isInHeightLimit(y)) {
                        continue;
                    }
                    for (int z = startBlockZ; z <= endBlockZ; z++) {
                        double normalizedZ = ((double) z + 0.5D - centerZ) / sphereRadius;
                        if (normalizedX * normalizedX + normalizedY * normalizedY + normalizedZ * normalizedZ >= 1.0D) {
                            continue;
                        }
                        int bitIndex = x - minX + (y - minY) * horizontalSize + (z - minZ) * horizontalSize * verticalSize;
                        if (bitIndex < 0 || visited.get(bitIndex)) {
                            continue;
                        }
                        visited.set(bitIndex);
                        consumer.accept(new BlockPos(x, y, z));
                    }
                }
            }
        }
    }

    private static void addIfReplaceable(
            OrePredictionResult result,
            LocalTerrainSampler terrainSampler,
            GeneratedStateTracker generatedStates,
            OreFeatureSpec spec,
            Random random,
            BlockPos pos,
            ChunkPos chunkPos,
            String dimensionId,
            long tick,
            Set<Long> emitted,
            ResolvedFeature resolvedFeature,
            boolean forceSeedCandidate,
            boolean emitRecord
    ) {
        long posKey = pos.asLong();
        if ((!forceSeedCandidate && !terrainSampler.isInHeightLimit(pos.getY())) || (emitRecord && emitted.contains(posKey))) {
            return;
        }
        if (!forceSeedCandidate) {
            BlockState baseState = terrainSampler.getState(pos, generatedStates);
            BlockState predictedState = spec.rule().predictedState(spec.target(), baseState);
            if (predictedState == null) {
                return;
            }
            if (!shouldNotDiscard(random, spec.discardOnAirChance()) && terrainSampler.isExposedToAir(pos, generatedStates)) {
                return;
            }
            generatedStates.put(pos, predictedState);
        }
        if (!emitRecord) {
            return;
        }
        emitted.add(posKey);
        BlockState predictedState = forceSeedCandidate ? spec.target().blocks()[0].getDefaultState() : generatedStates.get(pos);
        result.add(new OrePredictionRecord(
                pos,
                new ChunkPos(pos),
                dimensionId,
                spec.target(),
                predictedState,
                OreSource.SEED_PREDICTION,
                PredictionVisibilityStatus.PREDICTED_ONLY,
                null,
                tick,
                0L,
                forceSeedCandidate ? 0.62F : resolvedFeature.registryResolved() ? 0.92F : 0.78F,
                spec.placedFeatureId() + "_" + (forceSeedCandidate ? "seed_candidate" : "terrain_checked") + "_feature_index_" + resolvedFeature.featureIndex(),
                null
        ));
    }

    private static boolean shouldNotDiscard(Random random, float discardChance) {
        if (discardChance <= 0.0F) {
            return true;
        }
        if (discardChance >= 1.0F) {
            return false;
        }
        return random.nextFloat() >= discardChance;
    }

    private static int getSpread(Random random, int spread) {
        return Math.round((random.nextFloat() - random.nextFloat()) * (float) spread);
    }

    private static OreFeatureSpec ore(OreTarget target, String placedId, int count, HeightSpec height, int size, float discardChance, TargetRule rule) {
        return new OreFeatureSpec(target, Identifier.of("minecraft", placedId), FeatureKind.ORE, CountSpec.fixed(count), height, size, discardChance, rule);
    }

    private static OreFeatureSpec oreUniformCount(OreTarget target, String placedId, int minCount, int maxCount, HeightSpec height, int size, float discardChance, TargetRule rule) {
        return new OreFeatureSpec(target, Identifier.of("minecraft", placedId), FeatureKind.ORE, CountSpec.uniform(minCount, maxCount), height, size, discardChance, rule);
    }

    private static OreFeatureSpec oreRarity(OreTarget target, String placedId, int chance, HeightSpec height, int size, float discardChance, TargetRule rule) {
        return new OreFeatureSpec(target, Identifier.of("minecraft", placedId), FeatureKind.ORE, CountSpec.rarity(chance), height, size, discardChance, rule);
    }

    private static OreFeatureSpec scattered(OreTarget target, String placedId, HeightSpec height, int size, float discardChance, TargetRule rule) {
        return new OreFeatureSpec(target, Identifier.of("minecraft", placedId), FeatureKind.SCATTERED_ORE, CountSpec.fixed(1), height, size, discardChance, rule);
    }

    private static List<BlockerFeatureSpec> blockerSpecs() {
        return BlockerSpecHolder.SPECS;
    }

    private static BlockerFeatureSpec blocker(String placedId, int count, HeightSpec height, int size, TargetRule rule, BlockState replacementState) {
        return new BlockerFeatureSpec(Identifier.of("minecraft", placedId), CountSpec.fixed(count), height, size, rule, replacementState);
    }

    private static BlockerFeatureSpec blockerRarity(String placedId, int chance, HeightSpec height, int size, TargetRule rule, BlockState replacementState) {
        return new BlockerFeatureSpec(Identifier.of("minecraft", placedId), CountSpec.rarity(chance), height, size, rule, replacementState);
    }

    private static Map.Entry<Identifier, FallbackFeatureIndex> fallback(String placedId, int generationStep, int featureIndex) {
        return Map.entry(Identifier.of("minecraft", placedId), new FallbackFeatureIndex(generationStep, featureIndex));
    }

    private static ResolvedFeature fallbackFeature(Identifier placedFeatureId) {
        FallbackFeatureIndex fallback = VANILLA_FALLBACK_INDICES.get(placedFeatureId);
        if (fallback == null) {
            return null;
        }
        return ResolvedFeature.fallback(fallback.generationStep(), fallback.featureIndex());
    }

    private static int defaultBottomY(String dimensionId) {
        return "minecraft:overworld".equals(dimensionId) ? -64 : 0;
    }

    private static int defaultHeight(String dimensionId) {
        return "minecraft:overworld".equals(dimensionId) ? 384 : 256;
    }

    private static HeightSpec heightUniform(Offset minInclusive, Offset maxInclusive) {
        return new HeightSpec(HeightMode.UNIFORM, minInclusive, maxInclusive);
    }

    private static HeightSpec heightTrapezoid(Offset minInclusive, Offset maxInclusive) {
        return new HeightSpec(HeightMode.TRAPEZOID, minInclusive, maxInclusive);
    }

    private enum FeatureKind {
        ORE,
        SCATTERED_ORE
    }

    private enum HeightMode {
        UNIFORM,
        TRAPEZOID
    }

    private enum OffsetMode {
        ABSOLUTE,
        ABOVE_BOTTOM,
        BELOW_TOP
    }

    private enum CountMode {
        FIXED,
        UNIFORM,
        RARITY
    }

    private enum TargetRule {
        OVERWORLD_STONE_DEEPSLATE,
        NETHER_BASE_STONE,
        NETHERRACK_ONLY;

        BlockState predictedState(OreTarget target, BlockState baseState) {
            return predictedState(target.blocks()[0].getDefaultState(), target.blocks().length > 1 ? target.blocks()[1].getDefaultState() : null, baseState);
        }

        BlockState predictedState(BlockState replacementState, BlockState baseState, Random random) {
            return predictedState(replacementState, null, baseState);
        }

        private BlockState predictedState(BlockState primaryState, BlockState deepslateState, BlockState baseState) {
            if (this == NETHERRACK_ONLY) {
                return baseState.isOf(Blocks.NETHERRACK) ? primaryState : null;
            }
            if (this == NETHER_BASE_STONE) {
                return baseState.isIn(BlockTags.BASE_STONE_NETHER) ? primaryState : null;
            }
            if (baseState.isIn(BlockTags.STONE_ORE_REPLACEABLES)) {
                return primaryState;
            }
            if (baseState.isIn(BlockTags.DEEPSLATE_ORE_REPLACEABLES)) {
                return deepslateState == null ? primaryState : deepslateState;
            }
            return null;
        }
    }

    private record OreFeatureSpec(
            OreTarget target,
            Identifier placedFeatureId,
            FeatureKind kind,
            CountSpec count,
            HeightSpec height,
            int size,
            float discardOnAirChance,
            TargetRule rule
    ) {
    }

    private record BlockerFeatureSpec(
            Identifier placedFeatureId,
            CountSpec count,
            HeightSpec height,
            int size,
            TargetRule rule,
            BlockState replacementState
    ) {
        boolean belongsInDimension(String dimensionId) {
            return switch (rule) {
                case NETHER_BASE_STONE, NETHERRACK_ONLY -> "minecraft:the_nether".equals(dimensionId);
                case OVERWORLD_STONE_DEEPSLATE -> "minecraft:overworld".equals(dimensionId);
            };
        }
    }

    private record CountSpec(CountMode mode, int min, int max) {
        static CountSpec fixed(int count) {
            return new CountSpec(CountMode.FIXED, count, count);
        }

        static CountSpec uniform(int min, int max) {
            return new CountSpec(CountMode.UNIFORM, min, max);
        }

        static CountSpec rarity(int chance) {
            return new CountSpec(CountMode.RARITY, chance, chance);
        }

        int sample(Random random) {
            return switch (mode) {
                case FIXED -> min;
                case UNIFORM -> min + random.nextInt(max - min + 1);
                case RARITY -> random.nextFloat() < 1.0F / (float) min ? 1 : 0;
            };
        }
    }

    private record HeightSpec(HeightMode mode, Offset minInclusive, Offset maxInclusive) {
        int sample(Random random, int bottomY, int height) {
            int minY = minInclusive.resolve(bottomY, height);
            int maxY = maxInclusive.resolve(bottomY, height);
            int range = maxY - minY;
            if (range <= 0) {
                return minY;
            }
            return switch (mode) {
                case UNIFORM -> minY + random.nextInt(range + 1);
                case TRAPEZOID -> {
                    int lowerSlope = range / 2;
                    int upperSlope = range - lowerSlope;
                    yield minY + random.nextInt(upperSlope + 1) + random.nextInt(lowerSlope + 1);
                }
            };
        }
    }

    private record Offset(OffsetMode mode, int value) {
        static Offset absolute(int value) {
            return new Offset(OffsetMode.ABSOLUTE, value);
        }

        static Offset aboveBottom(int value) {
            return new Offset(OffsetMode.ABOVE_BOTTOM, value);
        }

        static Offset belowTop(int value) {
            return new Offset(OffsetMode.BELOW_TOP, value);
        }

        int resolve(int bottomY, int height) {
            return switch (mode) {
                case ABSOLUTE -> value;
                case ABOVE_BOTTOM -> bottomY + value;
                case BELOW_TOP -> bottomY + height - value;
            };
        }
    }

    private record FallbackFeatureIndex(int generationStep, int featureIndex) {
    }

    private record ResolvedFeature(int generationStep, int featureIndex, boolean registryResolved) {
        static ResolvedFeature builtin(int generationStep, int featureIndex) {
            return new ResolvedFeature(generationStep, featureIndex, true);
        }

        static ResolvedFeature fallback(int generationStep, int featureIndex) {
            return new ResolvedFeature(generationStep, featureIndex, false);
        }
    }

    private record ResolvedGenerationSpec(OreFeatureSpec oreSpec, BlockerFeatureSpec blockerSpec, ResolvedFeature resolvedFeature, boolean emitRecord) {
        static ResolvedGenerationSpec ore(OreFeatureSpec spec, ResolvedFeature resolvedFeature, boolean emitRecord) {
            return new ResolvedGenerationSpec(spec, null, resolvedFeature, emitRecord);
        }

        static ResolvedGenerationSpec blocker(BlockerFeatureSpec spec, ResolvedFeature resolvedFeature) {
            return new ResolvedGenerationSpec(null, spec, resolvedFeature, false);
        }
    }

    private interface PositionConsumer {
        void accept(BlockPos pos);
    }

    private static final class GeneratedStateTracker {
        private final Map<Long, BlockState> states = new HashMap<>();

        void put(BlockPos pos, BlockState state) {
            states.put(pos.asLong(), state);
        }

        BlockState get(BlockPos pos) {
            return states.get(pos.asLong());
        }
    }

    private static final class BlockerSpecHolder {
        private static final List<BlockerFeatureSpec> SPECS = List.of(
                blocker("ore_magma", 4, heightUniform(Offset.absolute(27), Offset.absolute(36)), 33, TargetRule.NETHER_BASE_STONE, Blocks.MAGMA_BLOCK.getDefaultState()),
                blocker("ore_soul_sand", 12, heightUniform(Offset.aboveBottom(0), Offset.absolute(31)), 12, TargetRule.NETHER_BASE_STONE, Blocks.SOUL_SAND.getDefaultState()),
                blocker("ore_gravel_nether", 2, heightUniform(Offset.absolute(5), Offset.absolute(41)), 33, TargetRule.NETHER_BASE_STONE, Blocks.GRAVEL.getDefaultState()),
                blocker("ore_blackstone", 2, heightUniform(Offset.absolute(5), Offset.absolute(31)), 33, TargetRule.NETHER_BASE_STONE, Blocks.BLACKSTONE.getDefaultState()),
                blocker("ore_dirt", 7, heightUniform(Offset.absolute(0), Offset.absolute(160)), 33, TargetRule.OVERWORLD_STONE_DEEPSLATE, Blocks.DIRT.getDefaultState()),
                blocker("ore_gravel", 14, heightUniform(Offset.aboveBottom(0), Offset.belowTop(0)), 33, TargetRule.OVERWORLD_STONE_DEEPSLATE, Blocks.GRAVEL.getDefaultState()),
                blockerRarity("ore_granite_upper", 6, heightUniform(Offset.absolute(64), Offset.absolute(128)), 64, TargetRule.OVERWORLD_STONE_DEEPSLATE, Blocks.GRANITE.getDefaultState()),
                blocker("ore_granite_lower", 2, heightUniform(Offset.absolute(0), Offset.absolute(60)), 64, TargetRule.OVERWORLD_STONE_DEEPSLATE, Blocks.GRANITE.getDefaultState()),
                blockerRarity("ore_diorite_upper", 6, heightUniform(Offset.absolute(64), Offset.absolute(128)), 64, TargetRule.OVERWORLD_STONE_DEEPSLATE, Blocks.DIORITE.getDefaultState()),
                blocker("ore_diorite_lower", 2, heightUniform(Offset.absolute(0), Offset.absolute(60)), 64, TargetRule.OVERWORLD_STONE_DEEPSLATE, Blocks.DIORITE.getDefaultState()),
                blockerRarity("ore_andesite_upper", 6, heightUniform(Offset.absolute(64), Offset.absolute(128)), 64, TargetRule.OVERWORLD_STONE_DEEPSLATE, Blocks.ANDESITE.getDefaultState()),
                blocker("ore_andesite_lower", 2, heightUniform(Offset.absolute(0), Offset.absolute(60)), 64, TargetRule.OVERWORLD_STONE_DEEPSLATE, Blocks.ANDESITE.getDefaultState()),
                blocker("ore_tuff", 2, heightUniform(Offset.aboveBottom(0), Offset.absolute(0)), 64, TargetRule.OVERWORLD_STONE_DEEPSLATE, Blocks.TUFF.getDefaultState()),
                blocker("ore_infested", 14, heightUniform(Offset.aboveBottom(0), Offset.absolute(63)), 9, TargetRule.OVERWORLD_STONE_DEEPSLATE, Blocks.INFESTED_STONE.getDefaultState())
        );
    }

    private static final class LocalTerrainSampler {
        private final NoiseChunkGenerator generator;
        private final NoiseConfig noiseConfig;
        private final RegistryEntryLookup<PlacedFeature> placedFeatureLookup;
        private final boolean unavailable;
        private final HeightLimitView heightLimitView;
        private final Map<Long, VerticalBlockSample> columns = boundedColumnCache();
        private final Map<Long, Integer> topY = boundedColumnCache();
        private final Map<Identifier, PlacedFeature> placedFeatures = new HashMap<>();
        private Map<Identifier, ResolvedFeature> resolvedFeatures;

        private LocalTerrainSampler(
                NoiseChunkGenerator generator,
                NoiseConfig noiseConfig,
                RegistryEntryLookup<PlacedFeature> placedFeatureLookup,
                boolean unavailable
        ) {
            this.generator = generator;
            this.noiseConfig = noiseConfig;
            this.placedFeatureLookup = placedFeatureLookup;
            this.unavailable = unavailable;
            this.heightLimitView = generator == null ? HeightLimitView.create(0, 0) : HeightLimitView.create(generator.getMinimumY(), generator.getWorldHeight());
        }

        static LocalTerrainSampler createUnavailable() {
            return new LocalTerrainSampler(null, null, null, true);
        }

        boolean isUnavailable() {
            return unavailable;
        }

        boolean isInHeightLimit(int y) {
            return !unavailable && heightLimitView.isInHeightLimit(y);
        }

        Map<Identifier, ResolvedFeature> resolveFeatureIndices() {
            if (unavailable || placedFeatureLookup == null) {
                return Map.of();
            }
            if (resolvedFeatures != null) {
                return resolvedFeatures;
            }

            Map<PlacedFeature, Identifier> idsByFeature = new HashMap<>();
            for (OreFeatureSpec spec : SPECS) {
                PlacedFeature feature = placedFeature(spec.placedFeatureId());
                if (feature != null) {
                    idsByFeature.put(feature, spec.placedFeatureId());
                }
            }
            for (BlockerFeatureSpec spec : blockerSpecs()) {
                PlacedFeature feature = placedFeature(spec.placedFeatureId());
                if (feature != null) {
                    idsByFeature.put(feature, spec.placedFeatureId());
                }
            }

            try {
                List<RegistryEntry<Biome>> biomes = new ArrayList<>(generator.getBiomeSource().getBiomes());
                List<PlacedFeatureIndexer.IndexedFeatures> indexedFeatures = PlacedFeatureIndexer.collectIndexedFeatures(
                        biomes,
                        biomeEntry -> generator.getGenerationSettings(biomeEntry).getFeatures(),
                        true
                );
                Map<Identifier, ResolvedFeature> resolved = new HashMap<>();
                for (int step = 0; step < indexedFeatures.size(); step++) {
                    PlacedFeatureIndexer.IndexedFeatures features = indexedFeatures.get(step);
                    for (PlacedFeature feature : features.features()) {
                        Identifier id = idsByFeature.get(feature);
                        if (id != null) {
                            resolved.put(id, ResolvedFeature.builtin(step, features.indexMapping().applyAsInt(feature)));
                        }
                    }
                }
                resolvedFeatures = Map.copyOf(resolved);
                return resolvedFeatures;
            } catch (RuntimeException ignored) {
                resolvedFeatures = Map.of();
                return resolvedFeatures;
            }
        }

        boolean allowsFeature(Identifier placedFeatureId, BlockPos origin) {
            if (unavailable || noiseConfig == null) {
                return false;
            }
            PlacedFeature feature = placedFeature(placedFeatureId);
            if (feature == null) {
                return false;
            }
            RegistryEntry<Biome> biome = generator.getBiomeSource().getBiome(
                    BiomeCoords.fromBlock(origin.getX()),
                    BiomeCoords.fromBlock(origin.getY()),
                    BiomeCoords.fromBlock(origin.getZ()),
                    noiseConfig.getMultiNoiseSampler()
            );
            return generator.getGenerationSettings(biome).isFeatureAllowed(feature);
        }

        BlockState getState(BlockPos pos) {
            if (unavailable || !isInHeightLimit(pos.getY())) {
                return Blocks.AIR.getDefaultState();
            }
            return getColumn(pos.getX(), pos.getZ()).getState(pos.getY());
        }

        BlockState getState(BlockPos pos, GeneratedStateTracker generatedStates) {
            BlockState generatedState = generatedStates.get(pos);
            return generatedState == null ? getState(pos) : generatedState;
        }

        boolean isExposedToAir(BlockPos pos, GeneratedStateTracker generatedStates) {
            for (Direction direction : Direction.values()) {
                if (getState(pos.offset(direction), generatedStates).isAir()) {
                    return true;
                }
            }
            return false;
        }

        int topY(int x, int z) {
            if (unavailable) {
                return Integer.MIN_VALUE;
            }
            return topY.computeIfAbsent(columnKey(x, z), ignored -> {
                for (int y = heightLimitView.getTopYInclusive(); y >= heightLimitView.getBottomY(); y--) {
                    if (!getColumn(x, z).getState(y).isAir()) {
                        return y;
                    }
                }
                return heightLimitView.getBottomY();
            });
        }

        private VerticalBlockSample getColumn(int x, int z) {
            return columns.computeIfAbsent(columnKey(x, z), ignored -> generator.getColumnSample(x, z, heightLimitView, noiseConfig));
        }

        private PlacedFeature placedFeature(Identifier placedFeatureId) {
            if (placedFeatureLookup == null) {
                return null;
            }
            if (placedFeatures.containsKey(placedFeatureId)) {
                return placedFeatures.get(placedFeatureId);
            }
            try {
                RegistryKey<PlacedFeature> key = RegistryKey.of(RegistryKeys.PLACED_FEATURE, placedFeatureId);
                PlacedFeature feature = placedFeatureLookup.getOrThrow(key).value();
                placedFeatures.put(placedFeatureId, feature);
                return feature;
            } catch (RuntimeException ignored) {
                placedFeatures.put(placedFeatureId, null);
                return null;
            }
        }

        private static long columnKey(int x, int z) {
            return ((long) x << 32) ^ (z & 0xFFFFFFFFL);
        }

        private static <T> Map<Long, T> boundedColumnCache() {
            return new LinkedHashMap<>(1024, 0.75F, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<Long, T> eldest) {
                    return size() > MAX_TERRAIN_COLUMN_CACHE_ENTRIES;
                }
            };
        }
    }
}
