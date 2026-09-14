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
 * <p>A profile is a weighted list of entity entries. Each entry maps a
 * vanilla or modded {@link EntityType} (resolved by id) to a spawn weight
 * and an {@code is_baby} flag. When {@code is_baby} is {@code true} (the
 * default), the spawned mob gets {@link net.minecraft.world.entity.AgeableMob#setAge
 * setAge(-24000)} — the same age value vanilla applies to a baby mob bred
 * through the breeding system. That age value is what the user's external
 * growth-rate tuning mod reads to set per-species growth times.</p>
 *
 * <p>Set {@code is_baby: false} on a per-entry basis to spawn an adult
 * instead (e.g. for non-tameable mobs like {@code minecraft:parrot}
 * where spawning as a baby doesn't make sense, or for any case where
 * you want the adult variant).</p>
 *
 * <p>Loaded on-demand from {@code data/<namespace>/petshop_spawns/<path>.json}
 * by {@link PetshopCompatStructurePoolElement#loadProfile}. Datapacks at the
 * same path override the jar's built-in profiles — drop a JSON file at
 * {@code data/ctov/petshop_spawns/<profile>.json} in any active datapack
 * (e.g. the {@code rs_di_compat} datapack) to override the jar's version
 * without rebuilding the mod.</p>
 */
public record PetshopSpawnProfile(List<Entry> entries) {

    /**
     * Codec for a single spawn entry.
     *
     * <p>{@code entity} is a required entity-type id. {@code weight}
     * defaults to {@code 1} when omitted. {@code is_baby} defaults to
     * {@code true} — entries that should spawn as adults must explicitly
     * set {@code "is_baby": false}.</p>
     *
     * <p>The "baby" age is hardcoded to {@code -24000} ticks, the vanilla
     * breeding baby age. If a future use case requires overriding this
     * per-entry, add an {@code age} field — but for now we intentionally
     * don't expose it, because the user's external growth-rate tuning
     * mod reads {@code -24000} as "this was bred, apply the species
     * default growth time". Exposing {@code age} here would break that
     * contract.</p>
     */
    public record Entry(ResourceLocation entity, int weight, boolean isBaby) {
        public static final Codec<Entry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                ResourceLocation.CODEC.fieldOf("entity").forGetter(Entry::entity),
                Codec.intRange(1, 100).optionalFieldOf("weight", 1).forGetter(Entry::weight),
                Codec.BOOL.optionalFieldOf("is_baby", true).forGetter(Entry::isBaby)
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
     *
     * <p>Returns {@code -24000} for baby entries (the vanilla breeding baby
     * age — what an external growth-rate tuning mod reads to identify
     * "bred" mobs). Returns {@code 0} for adult entries.</p>
     */
    public int ageFor(Entry entry) {
        return entry.isBaby() ? -24000 : 0;
    }
}
