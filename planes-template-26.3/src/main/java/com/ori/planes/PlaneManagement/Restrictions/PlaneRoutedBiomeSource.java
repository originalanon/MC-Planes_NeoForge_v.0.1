package com.ori.planes.PlaneManagement.Restrictions;

import java.util.List;
import java.util.stream.Stream;

import com.mojang.serialization.MapCodec;
import com.ori.planes.Planes;

import net.minecraft.core.Holder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class PlaneRoutedBiomeSource extends BiomeSource {
    public static final MapCodec<PlaneRoutedBiomeSource> CODEC = MultiNoiseBiomeSource.CODEC.fieldOf("source")
            .xmap(PlaneRoutedBiomeSource::new, source -> source.delegate);
    private static final DeferredRegister<MapCodec<? extends BiomeSource>> SOURCES =
            DeferredRegister.create(Registries.BIOME_SOURCE, Planes.MODID);
    private final MultiNoiseBiomeSource delegate;

    private PlaneRoutedBiomeSource(MultiNoiseBiomeSource delegate) {
        this.delegate = delegate;
    }

    public static void register(IEventBus eventBus) {
        SOURCES.register("routed_multi_noise", () -> CODEC);
        SOURCES.register(eventBus);
    }

    @Override
    protected MapCodec<? extends BiomeSource> codec() {
        return CODEC;
    }

    @Override
    protected Stream<Holder<Biome>> collectPossibleBiomes() {
        return delegate.possibleBiomes().stream();
    }

    @Override
    public BiomeResolver createResolver(Climate.Sampler sampler) {
        return delegate.createResolver(sampler);
    }

    @Override
    public BiomeResolver createResolverForChunk(Climate.Sampler sampler, int minQuartX, int minQuartY, int minQuartZ,
            int quartSizeX, int quartSizeY, int quartSizeZ) {
        return delegate.createResolverForChunk(sampler, minQuartX, minQuartY, minQuartZ,
                quartSizeX, quartSizeY, quartSizeZ);
    }

    @Override
    public void addDebugInfo(List<String> result, BlockPos position, Climate.Sampler sampler) {
        delegate.addDebugInfo(result, position, sampler);
    }
}