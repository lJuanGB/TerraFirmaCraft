/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */

package net.dries007.tfc.world.chunkdata;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import org.jetbrains.annotations.Nullable;

import net.dries007.tfc.util.IArtist;
import net.dries007.tfc.world.Seed;
import net.dries007.tfc.world.layer.TFCLayers;
import net.dries007.tfc.world.layer.framework.Area;
import net.dries007.tfc.world.layer.framework.ConcurrentArea;
import net.dries007.tfc.world.noise.Noise2D;
import net.dries007.tfc.world.noise.OpenSimplex2D;
import net.dries007.tfc.world.region.ChooseRocks;
import net.dries007.tfc.world.region.Region;
import net.dries007.tfc.world.region.RegionGenerator;
import net.dries007.tfc.world.region.RiverEdge;
import net.dries007.tfc.world.region.Units;
import net.dries007.tfc.world.river.MidpointFractal;
import net.dries007.tfc.world.settings.RockLayerSettings;
import net.dries007.tfc.world.settings.RockSettings;

public final class RegionChunkDataGenerator implements ChunkDataGenerator
{
    private static final int LAYER_OFFSET_BITS = 3;
    private static final int LAYER_OFFSET_MASK = (1 << LAYER_OFFSET_BITS) - 1;
    private static final int[] LAYER_OFFSETS = new int[1 << (LAYER_OFFSET_BITS + 1)];

    private static final float DELTA_Y_OFFSET = 12;

    static
    {
        final RandomSource random = new XoroshiroRandomSource(1923874192341L);
        for (int i = 0; i < LAYER_OFFSETS.length; i++)
        {
            LAYER_OFFSETS[i] = random.nextInt(0, 100_000);
        }
    }

    private static int getOffsetX(int layer)
    {
        return LAYER_OFFSETS[(layer & LAYER_OFFSET_MASK) << 1];
    }

    private static int getOffsetZ(int layer)
    {
        return LAYER_OFFSETS[((layer & LAYER_OFFSET_MASK) << 1) | 0b1];
    }

    private static final int MIN_RIVER_WIDTH = 12; // Rivers must be this wide to influence rainfall
    private static final float RIVER_INFLUENCE = (float) Units.blockToGridExact(40);
    private static final float RIVER_INFLUENCE_SQ = RIVER_INFLUENCE * RIVER_INFLUENCE;

    private final RegionGenerator regionGenerator;
    private final RockLayerSettings rockLayerSettings;
    private final ConcurrentArea<ForestType> forestTypeLayer;
    private final ThreadLocal<Area> rockLayerArea;
    private final Noise2D layerHeightNoise;
    private final Noise2D layerSkewXNoise;
    private final Noise2D layerSkewZNoise;

    public RegionChunkDataGenerator(RegionGenerator regionGenerator, RockLayerSettings rockLayerSettings, Seed seed)
    {
        this.regionGenerator = regionGenerator;
        this.rockLayerSettings = rockLayerSettings;

        this.rockLayerArea = ThreadLocal.withInitial(TFCLayers.createOverworldRockLayer(regionGenerator, seed.next()));
        this.layerHeightNoise = new OpenSimplex2D(seed.next()).octaves(3).scaled(43, 63).spread(0.014f);
        this.layerSkewXNoise = new OpenSimplex2D(seed.next()).octaves(2).scaled(-1.8f, 1.8f).spread(0.01f);
        this.layerSkewZNoise = new OpenSimplex2D(seed.next()).octaves(2).scaled(-1.8f, 1.8f).spread(0.01f);

        this.forestTypeLayer = new ConcurrentArea<>(TFCLayers.createOverworldForestLayer(seed.next(), IArtist.nope()), ForestType::valueOf);
    }

    @Override
    public ChunkData generate(ChunkData data)
    {
        switch (data.status())
        {
            case EMPTY: break; // Allow generation
            case PARTIAL, FULL: return data; // Skip generation, this data is already generated
            case CLIENT, INVALID: throw new IllegalStateException("generate() called on chunk data with status " + data.status());
        }


        final ChunkPos pos = data.getPos();
        final int blockX = pos.getMinBlockX(), blockZ = pos.getMinBlockZ();

        final int gridX = Units.blockToGrid(blockX);
        final int gridZ = Units.blockToGrid(blockZ);

        // In this formulation, the value represented by a grid point's rainfall / temperature is interpreted to be at the 0,0 exact grid position,
        // meaning if we want smooth interpolation, we need to sample a 2x2 grid of points and compute the local values within the grid square
        final Region.Point point00 = regionGenerator.getOrCreateRegionPoint(gridX, gridZ);
        final Region.Point point01 = regionGenerator.getOrCreateRegionPoint(gridX, gridZ + 1);
        final Region.Point point10 = regionGenerator.getOrCreateRegionPoint(gridX + 1, gridZ);
        final Region.Point point11 = regionGenerator.getOrCreateRegionPoint(gridX + 1, gridZ + 1);

        final LerpFloatLayer rainfallGridLayer = new LerpFloatLayer(point00.rainfall, point01.rainfall, point10.rainfall, point11.rainfall);
        final LerpFloatLayer rainfallVarianceGridLayer = new LerpFloatLayer(point00.rainfallVariance, point01.rainfallVariance, point10.rainfallVariance, point11.rainfallVariance);
        final LerpFloatLayer temperatureGridLayer = new LerpFloatLayer(point00.temperature, point01.temperature, point10.temperature, point11.temperature);

        // The exact grid coordinates of the bottom (00) value of this chunk
        final double exactGridX = Units.blockToGridExact(blockX);
        final double exactGridZ = Units.blockToGridExact(blockZ);

        // Distance within the grid of this chunk - so a value between [0, 1] representing the top left of this chunk
        // The interpolator will add 16 / <grid width> to obtain the other side of this chunk, and interpolate from the bounding boxes of the grid points.
        final float deltaX = (float) (exactGridX - gridX);
        final float deltaZ = (float) (exactGridZ - gridZ);

        // The width of the chunk, in grid coordinates
        final float dG = (float) Units.blockToGridExact(16);

        // The base rainfall and temperature layers, scaled down to chunk resolution
        final LerpFloatLayer rainfallLayer = rainfallGridLayer.scaled(deltaX, deltaZ, dG);
        final LerpFloatLayer rainfallVarianceLayer = rainfallVarianceGridLayer.scaled(deltaX, deltaZ, dG);
        final LerpFloatLayer temperatureLayer = temperatureGridLayer.scaled(deltaX, deltaZ, dG);

        // Calculate local influence of rivers - when they are wide enough (which should happen only with large rivers near shores),
        // rivers will influence the groundwater of the nearby area, thus creating a bit of a humid area in the immediate vicinity of the river
        // groundwater is used instead of rainfall in order to avoid strange rain effects
        //
        // The radius that the partition point will find rivers is approx ~5 grid distance (~500 blocks), so we should be fine to influence
        // on a smaller scale of ~100 blocks around the river
        float groundwater00 = 0;
        float groundwater01 = 0;
        float groundwater10 = 0;
        float groundwater11 = 0;

        for (RiverEdge edge : regionGenerator.getOrCreatePartitionPoint(gridX, gridZ).rivers())
        {
            final MidpointFractal fractal = edge.fractal();
            if (edge.width >= MIN_RIVER_WIDTH && // Note that all downstream segments will always be the same or larger width
                fractal.maybeIntersect(exactGridX, exactGridZ, RIVER_INFLUENCE))
            {
                final float widthInfluence = Mth.map(edge.width, MIN_RIVER_WIDTH, RiverEdge.MAX_WIDTH, 0f, 1.0f);

                groundwater00 = adjustGroundwaterNearRiver(groundwater00, widthInfluence, fractal, exactGridX, exactGridZ);
                groundwater01 = adjustGroundwaterNearRiver(groundwater01, widthInfluence, fractal, exactGridX, exactGridZ + dG);
                groundwater10 = adjustGroundwaterNearRiver(groundwater10, widthInfluence, fractal, exactGridX + dG, exactGridZ);
                groundwater11 = adjustGroundwaterNearRiver(groundwater11, widthInfluence, fractal, exactGridX + dG, exactGridZ + dG);
            }
        }

        // Update the groundwater layer with the new influenced values, and then clamp to within the target range
        final LerpFloatLayer baseGroundwaterLayer = new LerpFloatLayer(groundwater00, groundwater01, groundwater10, groundwater11)
            .apply(value -> Mth.clamp(value, 0, 500));

        // This layer is sampled per-chunk, to avoid the waste of two additional zoom layers
        final ForestType forestType = forestTypeLayer.get(blockX >> 4, blockZ >> 4);

        data.generatePartial(
            rainfallLayer,
            rainfallVarianceLayer,
            baseGroundwaterLayer,
            temperatureLayer,
            forestType
        );

        return data;
    }

    // River effects on groundwater are more pronounced at low rainfalls
    private float adjustGroundwaterNearRiver(float currentValue, float widthInfluence, MidpointFractal fractal, double gridX, double gridZ)
    {
        final float distance = (float) fractal.intersectDistance(gridX, gridZ);
        final float distanceInfluence = Mth.clampedMap(distance, 0f, RIVER_INFLUENCE_SQ, 1f, 0f);

        // Take the max of any influence with adjacent rivers
        return Math.max(currentValue, distanceInfluence * widthInfluence * 300f);
    }

    @Override
    public RockSettings generateRock(int x, int y, int z, int surfaceY, @Nullable ChunkRockDataCache cache)
    {
        return generateRock(x, y, z, surfaceY, cache, null);
    }

    @Override
    public void displayDebugInfo(List<String> tooltip, BlockPos pos, int surfaceY)
    {
        generateRock(pos.getX(), pos.getY(), pos.getZ(), surfaceY, null, tooltip);
    }

    private RockSettings generateRock(int x, int y, int z, int surfaceY, @Nullable ChunkRockDataCache cache, @Nullable List<String> tooltip)
    {
        // Adjust surface Y so that really high mountains don't pull up the rock layers too much
        float adjustedSurfaceY = surfaceY;
        if (adjustedSurfaceY > 125)
        {
            adjustedSurfaceY = 125 + 0.3f * (surfaceY - 125);
        }

        // Iterate downwards to find the nth layer
        int layer = 0;
        float deltaY = adjustedSurfaceY - y;
        float layerHeight;
        do
        {
            if (cache != null)
            {
                populateLayerInCache(cache, layer);
                layerHeight = cache.getLayerHeight(layer, x, z);
            }
            else
            {
                final int layerX = x + getOffsetX(layer);
                final int layerZ = z + getOffsetZ(layer);

                layerHeight = (float) layerHeightNoise.noise(layerX, layerZ);
            }
            if (deltaY <= layerHeight)
            {
                break;
            }
            deltaY -= layerHeight;
            layer++;
        } while (deltaY > 0);

        final int offsetX, offsetZ;
        final float skewNoiseX, skewNoiseZ;

        if (cache != null)
        {
            // Unused, since we query cached skews directly
            offsetX = offsetZ = 0;

            // The cache is required to be populated as before as we iterated layers, we also populate the skew noise there
            skewNoiseX = cache.getLayerSkewX(layer, x, z);
            skewNoiseZ = cache.getLayerSkewZ(layer, x, z);
        }
        else
        {
            // Layer count (from surface) is now known
            // Sample (lateral) offset
            offsetX = x + getOffsetX(layer);
            offsetZ = z + getOffsetZ(layer);

            // Skew position after calculating the correct layer offset, and then skewing by deltaY
            skewNoiseX = (float) layerSkewXNoise.noise(offsetX, offsetZ);
            skewNoiseZ = (float) layerSkewZNoise.noise(offsetX, offsetZ);
        }

        final int skewX = x + (int) (skewNoiseX * (deltaY + DELTA_Y_OFFSET));
        final int skewZ = z + (int) (skewNoiseZ * (deltaY + DELTA_Y_OFFSET));

        // Rock seed (including type and seed) at this point in the layer
        final int point = rockLayerArea.get().get(skewX, skewZ);

        // Sample the rock at this layer, progressing downwards according to the possible layered rocks
        final RockSettings rock = rockLayerSettings.sampleAtLayer(point, layer);

        if (tooltip != null)
        {
            tooltip.add("Pos: %d, %d, %d S: %d dY: %.1f Layer: %d LayerH: %.1f".formatted(x, y, z, surfaceY, deltaY, layer, layerHeight));
            tooltip.add("Offset: %d, %d Skew: %.1f, %.1f / %d, %d Seed: %d Type: %d".formatted(offsetX, offsetZ, skewNoiseX, skewNoiseZ, skewX, skewZ, point >> ChooseRocks.TYPE_BITS, point & ChooseRocks.TYPE_MASK));
            tooltip.add("Rock: %s".formatted(BuiltInRegistries.BLOCK.getKey(rock.raw())));
        }

        return rock;
    }

    private void populateLayerInCache(ChunkRockDataCache cache, int layer)
    {
        if (cache.layers() <= layer)
        {
            // Populate layers of layer height, and skew noise here
            final int chunkX = cache.pos().getMinBlockX(), chunkZ = cache.pos().getMinBlockZ();
            for (int populateLayer = cache.layers(); populateLayer <= layer; populateLayer++)
            {
                final float[] populatedLayerHeight = new float[16 * 16];
                final float[] populatedLayerSkew = new float[16 * 16 * 2];
                final int layerX = chunkX + getOffsetX(layer);
                final int layerZ = chunkZ + getOffsetZ(layer);
                for (int dx = 0; dx < 16; dx++)
                {
                    for (int dz = 0; dz < 16; dz++)
                    {
                        final int offsetX = layerX + dx, offsetZ = layerZ + dz;
                        final int i = Units.index(dx, dz);

                        populatedLayerHeight[i] = (float) layerHeightNoise.noise(offsetX, offsetZ);
                        populatedLayerSkew[i << 1] = (float) layerSkewXNoise.noise(offsetX, offsetZ);
                        populatedLayerSkew[(i << 1) | 0b1] = (float) layerSkewZNoise.noise(offsetX, offsetZ);
                    }
                }
                cache.addLayer(populatedLayerHeight, populatedLayerSkew);
            }
        }
    }
}
