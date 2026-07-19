package net.ctov.petshop;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;

import java.util.List;
import java.util.Optional;

/**
 * Data-only definition of a petshop spawn profile.
 *
 * <p>A profile is a weighted list of entity entries. Each entry maps a vanilla
 * or modded {@link EntityType} (resolved by id) to a spawn weight, an optional
 * {@code baby} flag, and an optional {@code age} value. The semantics of
 * {@code age} mirror {@link net.minecraft.world.entity.AgeableMob#setAge(int)}:
 * negative values are babies, more-negative values extend the baby phase, and
 * {@link Integer#MIN_VALUE} approximates a permanent baby.</p>
 *
 * <p><b>Footgun warning:</b> {@code baby:true} combined with {@code age:0}
 * produces an adult mob that <em>looks</em> like it was supposed to be a baby.
 * Either omit {@code age} (defaults to {@code -24000}, the vanilla baby age)
 * or set it to a clearly negative value when {@code baby} is true.</p>
 *
 * <p>Loaded on-demand from {@code data/<namespace>/petshop_spawns/<path>.json}
 * by {@link PetshopCompatStructurePoolElement#loadProfile}.</p>
 */
public record PetshopSpawnProfile(List<Entry> entries) {

    /**
     * Codec for a single spawn entry.
     *
     * <p>{@code entity} is a required entity-type id. {@code weight} defaults
     * to 1 when omitted. {@code baby} defaults to false. {@code age} defaults
     * to {@code -24000} when {@code baby} is true and to {@code 0} otherwise;
     * providing an explicit {@code age} always wins.</p>
     */
    public record Entry(ResourceLocation entity, int weight, boolean baby, int age) {
        public static final Codec<Entry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                ResourceLocation.CODEC.fieldOf("entity").forGetter(Entry::entity),
                Codec.intRange(1, 100).optionalFieldOf("weight", 1).forGetter(Entry::weight),
                Codec.BOOL.optionalFieldOf("baby", false).forGetter(Entry::baby),
                Codec.INT.optionalFieldOf("age", -24000).forGetter(Entry::age)
        ).apply(instance, Entry::new));
    }

    public static final Codec<PetshopSpawnProfile> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Entry.CODEC.listOf().fieldOf("entries").forGetter(PetshopSpawnProfile::entries)
    ).apply(instance, PetshopSpawnProfile::new));

    /**
     * Pick a random entity type from the profile, respecting weights.
     *
     * <p>Returns {@link Optional#empty()} if the profile has no resolvable
     * entries (e.g. the mod providing the entities isn't loaded) — in that
     * case the caller skips spawning for that cage.</p>
     */
    public Optional<EntityType<?>> pickEntity(RandomSource random) {
        if (entries.isEmpty()) {
            return Optional.empty();
        }
        int totalWeight = entries.stream().mapToInt(Entry::weight).sum();
        if (totalWeight <= 0) {
            return Optional.empty();
        }
        int roll = random.nextInt(totalWeight);
        int cumulative = 0;
        for (Entry entry : entries) {
            cumulative += entry.weight;
            if (roll < cumulative) {
                EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(entry.entity);
                if (type != null) {
                    return Optional.of(type);
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Resolve the desired age for a chosen entry. Used by
     * {@link PetshopCompatStructurePoolElement} after {@link Mob#finalizeSpawn}
     * to avoid being overwritten.
     */
    public int ageFor(Entry entry) {
        return entry.baby() ? (entry.age() < 0 ? entry.age() : -24000) : entry.age();
    }
}
