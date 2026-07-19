package com.choicetheorem.ctov.worldgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Parsed form of {@code data/ctov/petshop_spawns/<biome>.json}.
 *
 * <p>Schema:
 * <pre>{@code
 * {
 *   "entries": [
 *     {
 *       "entity": "minecraft:wolf",   // required — vanilla or modded entity ID
 *       "weight": 10,                  // required — positive int
 *       "baby": false,                 // optional — default false
 *       "age": -24000                  // optional — only used when "baby" is true;
 *                                       //   negative = baby; more negative = longer baby duration.
 *                                       //   Integer.MIN_VALUE ≈ permanent baby.
 *                                       //   Default -24000 (= one in-game day).
 *     }
 *   ]
 * }
 * }</pre>
 *
 * <p>The {@code age} semantics intentionally mirror {@link net.minecraft.world.entity.AgeableMob#setAge(int)}:
 * negative values are the baby phase, with duration proportional to the magnitude of negativity.
 * This is NOT a boolean "always baby" toggle — pick a finite negative number for a timed baby,
 * or {@link Integer#MIN_VALUE} for an effectively permanent baby.
 */
public final class PetshopSpawnProfile {

    /** Codec for a single spawn entry inside the {@code entries} array. */
    public static final Codec<Entry> ENTRY_CODEC = RecordCodecBuilder.create(inst -> inst.group(
            ResourceLocation.CODEC.fieldOf("entity").forGetter(Entry::entityId),
            Codec.intRange(1, Integer.MAX_VALUE).fieldOf("weight").forGetter(Entry::weight),
            Codec.BOOL.optionalFieldOf("baby").forGetter(e -> e.baby() ? Optional.of(true) : Optional.empty()),
            Codec.INT.optionalFieldOf("age").forGetter(e -> e.age() != Entry.DEFAULT_AGE
                    ? Optional.of(e.age()) : Optional.empty())
    ).apply(inst, Entry::new));

    /** Codec for the whole profile document. */
    public static final Codec<PetshopSpawnProfile> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            ENTRY_CODEC.listOf().optionalFieldOf("entries").forGetter(p -> p.entries.isEmpty()
                    ? Optional.empty() : Optional.of(p.entries))
    ).apply(inst, PetshopSpawnProfile::new));

    /** A single entity spawn entry. */
    public record Entry(ResourceLocation entityId, int weight, boolean baby, int age) {
        /** Default {@code age} when "baby": true is set without an explicit age. One in-game day = 24000 ticks. */
        public static final int DEFAULT_AGE = -24000;

        Entry(ResourceLocation entityId, int weight, Optional<Boolean> baby, Optional<Integer> age) {
            this(entityId, weight, baby.orElse(false), age.orElse(DEFAULT_AGE));
        }
    }

    private final List<Entry> entries;
    private final int totalWeight;

    public PetshopSpawnProfile(Optional<List<Entry>> entries) {
        this.entries = entries.orElse(Collections.emptyList());
        this.totalWeight = this.entries.stream().mapToInt(Entry::weight).sum();
    }

    public List<Entry> entries() {
        return entries;
    }

    public int totalWeight() {
        return totalWeight;
    }

    /**
     * Pick a random entry by weight, resolving the {@link EntityType} via the Forge registry.
     * Returns {@code null} if the profile is empty or the chosen entity ID is not registered
     * (e.g. the providing mod is not installed). In that case the caller should silently skip
     * spawning — never crash world generation.
     */
    public EntityType<?> pickEntity(net.minecraft.util.RandomSource random) {
        Entry e = pickEntry(random);
        return e == null ? null : ForgeRegistries.ENTITY_TYPES.getValue(e.entityId());
    }

    /** Look up the original entry (for {@code baby}/{@code age} access) given a resolved entity type. */
    public Entry pickEntry(net.minecraft.util.RandomSource random) {
        if (totalWeight <= 0 || entries.isEmpty()) {
            return null;
        }
        int roll = random.nextInt(totalWeight);
        int cursor = 0;
        for (Entry e : entries) {
            cursor += e.weight;
            if (roll < cursor) {
                return e;
            }
        }
        return null;
    }
}
