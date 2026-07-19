package com.choicetheorem.ctov.worldgen;

import com.choicetheorem.ctov.registry.CTOVRegistry;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BaseCoralPlantTypeBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.pools.LegacySinglePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElementType;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorList;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.JigsawReplacementProcessor;
import net.minecraft.world.phys.Vec3;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * A {@link LegacySinglePoolElement} that processes the data markers embedded in CTOV's
 * petshop structure NBTs ({@code petshop_water}, {@code petshop_chest},
 * {@code petshop_cage_0..3}).
 *
 * <p>This is theForge-side equivalent of DI's {@code PetshopStructurePoolElement} — but
 * where DI relies on five global entity-type tags shared across every village biome, this
 * implementation reads per-biome spawn profiles from {@code data/ctov/petshop_spawns/<biome>.json}
 * so that a plains petshop can stock different pets than a snowy petshop.
 *
 * <p>Marker dispatch:
 * <ul>
 *   <li><b>petshop_water</b> — replaces the structure-block marker with a waterlogged
 *       decoration (seagrass or a random coral) and spawns 1–2 fish from the
 *       {@code fishtank} profile.</li>
 *   <li><b>petshop_chest</b> — clears the marker and binds the block below it to DI's
 *       {@code domesticationinnovation:chests/petshop_chest} loot table. If DI is not
 *       installed the chest is simply empty (no crash).</li>
 *   <li><b>petshop_cage_0..3</b> — clears the marker and spawns 1–2 entities from the
 *       configured biome profile. Cage index 3 spawns exactly 1 entity (parrot perch),
 *       mirroring DI's behavior.</li>
 * </ul>
 *
 * <p>The biome profile key is fixed at JSON-declaration time via the {@code biome_profile}
 * field on the pool element. This lets the same NBT be reused across biomes with different
 * spawn tables — only the pool entry's {@code biome_profile} field changes.
 *
 * <p>Spawn profiles are loaded lazily on first use and cached for the lifetime of the
 * resource manager. The cache is invalidated on datapack reload via
 * {@link #onReload(AddReloadListenerEvent)}.
 */
public class PetshopCompatStructurePoolElement extends LegacySinglePoolElement {

    private static final Logger LOGGER = LoggerFactory.getLogger("CTOV/DIPetshopCompat");

    /** Loot table provided by DI. Used as-is when DI is installed; chest is empty otherwise. */
    public static final ResourceLocation DI_PETSHOP_CHEST =
            new ResourceLocation("domesticationinnovation", "chests/petshop_chest");

    private final String biomeProfile;

    /** Codec used to (de)serialize this element from {@code house.json} pool files. */
    public static final Codec<PetshopCompatStructurePoolElement> CODEC =
            RecordCodecBuilder.create(inst -> inst.group(
                    templateCodec(),
                    processorsCodec(),
                    projectionCodec(),
                    Codec.STRING.fieldOf("biome_profile").forGetter(PetshopCompatStructurePoolElement::getBiomeProfile)
            ).apply(inst, PetshopCompatStructurePoolElement::new));

    protected PetshopCompatStructurePoolElement(
            Either<ResourceLocation, StructureTemplate> template,
            Holder<StructureProcessorList> processors,
            StructureTemplatePool.Projection projection,
            String biomeProfile) {
        super(template, processors, projection);
        this.biomeProfile = biomeProfile;
    }

    public String getBiomeProfile() {
        return biomeProfile;
    }

    @Override
    public StructurePoolElementType<?> getType() {
        return CTOVRegistry.PETSHOP_COMPAT_POOL_ELEMENT.get();
    }

    @Override
    public String toString() {
        return "CTOVPetshopCompat[" + this.template + ", biome=" + biomeProfile + "]";
    }

    // ──────────────────────────────────────────────────────────────────────
    //  Data marker dispatch
    // ──────────────────────────────────────────────────────────────────────

    @Override
    public void handleDataMarker(
            LevelAccessor level,
            StructureTemplate.StructureBlockInfo blockInfo,
            BlockPos pos,
            Rotation rotation,
            RandomSource random,
            BoundingBox box) {

        // blockInfo.nbt() carries the template-local metadata (the data-marker string);
        // it is independent of placement and is safe to read here. The `pos` parameter
        // is the world-space position of the marker — that's what we must use for any
        // block/entity placement, because blockInfo.pos() is template-local and would
        // land spawns and block writes at the wrong coordinates when the structure is
        // not placed at the world origin.
        String marker = blockInfo.nbt().getString("metadata");
        switch (marker) {
            case "petshop_water" -> handleWater(level, pos, random);
            case "petshop_chest" -> handleChest(level, pos, random);
            case "petshop_cage_0" -> handleCage(level, pos, random, 1 + random.nextInt(2));
            case "petshop_cage_1" -> handleCage(level, pos, random, 2 + random.nextInt(2));
            case "petshop_cage_2" -> handleCage(level, pos, random, 1 + random.nextInt(2));
            case "petshop_cage_3" -> handleCage(level, pos, random, 1);
            default -> { /* not our marker — leave the structure block in place */ }
        }
    }

    private void handleWater(LevelAccessor level, BlockPos pos, RandomSource random) {
        // Mirror DI's decoration behavior so the fishtank looks the same regardless of which
        // pool element type placed it.
        BlockState state = Blocks.WATER.defaultBlockState();
        float f = random.nextFloat();
        if (f < 0.5F) {
            state = Blocks.SEAGRASS.defaultBlockState();
        } else if (f < 0.75F) {
            Block coralBlock = switch (random.nextInt(5)) {
                case 1 -> Blocks.TUBE_CORAL;
                case 2 -> Blocks.BRAIN_CORAL;
                case 3 -> Blocks.BUBBLE_CORAL;
                case 4 -> Blocks.FIRE_CORAL;
                default -> Blocks.HORN_CORAL;
            };
            state = coralBlock.defaultBlockState().setValue(BaseCoralPlantTypeBlock.WATERLOGGED, true);
        }
        // Always spawn fishtank entities first (so they end up inside the water block).
        spawnFromProfile(level, pos, random, "fishtank", 2);
        level.setBlock(pos, state, 2);
    }

    private void handleChest(LevelAccessor level, BlockPos pos, RandomSource random) {
        // Clear the structure-block marker, then bind the block below (where the actual chest
        // was placed during NBT instantiation) to DI's petshop chest loot table.
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
        if (level instanceof ServerLevelAccessor serverLevel) {
            // RandomizableContainerBlockEntity.setLootTable gracefully no-ops if the loot table
            // ID is not resolvable, so missing DI does not crash worldgen.
            RandomizableContainerBlockEntity.setLootTable(level, random, pos.below(), DI_PETSHOP_CHEST);
        }
    }

    private void handleCage(LevelAccessor level, BlockPos pos,
                            RandomSource random, int count) {
        spawnFromProfile(level, pos, random, biomeProfile, count);
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
    }

    /**
     * Spawn up to {@code count} entities from {@code petshop_spawns/<profile>.json} at the
     * marker position. Each spawn independently rolls the weighted list, so a single cage
     * can mix entity types when the profile lists more than one.
     */
    private void spawnFromProfile(LevelAccessor level, BlockPos at,
                                  RandomSource random, String profile, int count) {
        if (!(level instanceof ServerLevelAccessor serverLevel)) {
            return;
        }
        if (level.getBlockState(at).getBlock() != Blocks.STRUCTURE_BLOCK) {
            // The marker block was already replaced (e.g. by another processor). Spawning
            // inside a non-air block would clip into geometry, so skip.
            return;
        }
        PetshopSpawnProfile spawns = ProfileLoader.get(profile);
        if (spawns == null || spawns.totalWeight() <= 0) {
            return;
        }
        for (int i = 0; i < count; i++) {
            PetshopSpawnProfile.Entry entry = spawns.pickEntry(random);
            if (entry == null) continue;
            EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(entry.entityId());
            if (type == null) {
                LOGGER.debug("Petshop profile '{}' references unknown entity '{}'; skipping spawn",
                        profile, entry.entityId());
                continue;
            }
            Entity entity = type.create(serverLevel.getLevel());
            if (entity == null) continue;
            entity.setPos(Vec3.atBottomCenterOf(at));
            entity.setYRot(random.nextInt(360) - 180);
            if (entity instanceof Mob mob) {
                mob.setPersistenceRequired();
                mob.finalizeSpawn(serverLevel, serverLevel.getCurrentDifficultyAt(mob.blockPosition()),
                        MobSpawnType.STRUCTURE, null, null);
            }
            // Apply age AFTER finalizeSpawn, otherwise AgeableMob.finalizeSpawn can overwrite it.
            if (entry.baby() && entity instanceof AgeableMob ageable) {
                ageable.setAge(entry.age());
            }
            serverLevel.addFreshEntityWithPassengers(entity);
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    //  Settings — copied from DI's PetshopStructurePoolElement so jigsaw
    //  replacement & entity finalization behave the same way.
    // ──────────────────────────────────────────────────────────────────────

    @Override
    protected StructurePlaceSettings getSettings(Rotation rotation, BoundingBox box, boolean knownShape) {
        StructurePlaceSettings settings = new StructurePlaceSettings();
        settings.setBoundingBox(box);
        settings.setRotation(rotation);
        settings.setKnownShape(true);
        settings.setIgnoreEntities(false);
        settings.setFinalizeEntities(true);
        if (!knownShape) {
            settings.addProcessor(JigsawReplacementProcessor.INSTANCE);
        }
        this.processors.value().list().forEach(settings::addProcessor);
        this.getProjection().getProcessors().forEach(settings::addProcessor);
        return settings;
    }

    // ──────────────────────────────────────────────────────────────────────
    //  Profile loading — cached per resource manager, invalidated on reload
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Lazily-loaded cache of parsed spawn profiles, keyed by profile name
     * (e.g. {@code "plains"}, {@code "fishtank"}).
     *
     * <p>Entries are loaded from {@code data/ctov/petshop_spawns/<name>.json} using whatever
     * {@link ResourceManager} is currently active. We hold the manager in a volatile field
     * so a datapack reload swaps the cache atomically.
     */
    static final class ProfileLoader {
        private static final Gson GSON = new Gson();
        private static volatile ResourceManager activeManager;
        private static final Cache<String, PetshopSpawnProfile> CACHE =
                CacheBuilder.newBuilder().expireAfterAccess(10, TimeUnit.MINUTES).build();

        static PetshopSpawnProfile get(String profile) {
            if (profile == null || profile.isBlank()) return null;
            try {
                return CACHE.get(profile, () -> load(profile));
            } catch (Exception e) {
                LOGGER.warn("Failed to load petshop spawn profile '{}': {}", profile, e.toString());
                return null;
            }
        }

        private static PetshopSpawnProfile load(String profile) throws Exception {
            ResourceManager rm = activeManager;
            if (rm == null) {
                LOGGER.debug("ResourceManager not yet available; petshop profile '{}' will be empty", profile);
                return null;
            }
            ResourceLocation id = new ResourceLocation("ctov", "petshop_spawns/" + profile + ".json");
            Optional<Resource> resource = rm.getResource(id);
            if (resource.isEmpty()) {
                LOGGER.debug("No petshop spawn profile at '{}' — cage spawns for this profile will be skipped", id);
                return null;
            }
            try (Reader reader = new InputStreamReader(resource.get().open(), StandardCharsets.UTF_8)) {
                JsonElement json = GSON.fromJson(reader, JsonElement.class);
                return PetshopSpawnProfile.CODEC.parse(JsonOps.INSTANCE, json)
                        .resultOrPartial(err -> LOGGER.warn("Malformed petshop profile '{}': {}", id, err))
                        .orElse(null);
            } catch (JsonParseException e) {
                LOGGER.warn("Petshop profile '{}' is not valid JSON: {}", id, e.toString());
                return null;
            }
        }

        static void invalidate(ResourceManager newManager) {
            activeManager = newManager;
            CACHE.invalidateAll();
        }
    }

    /**
     * Forge event subscriber that hooks the datapack reload to swap the cached
     * {@link ResourceManager}. Without this, profile edits made via datapack wouldn't
     * take effect until a full game restart.
     */
    @Mod.EventBusSubscriber(modid = "ctov", bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static class ReloadListener {
        @SubscribeEvent
        public static void onReload(AddReloadListenerEvent event) {
            event.addListener(new SimplePreparableReloadListener<Void>() {
                @Override
                protected Void prepare(ResourceManager resourceManager, ProfilerFiller profiler) {
                    return null;
                }

                @Override
                protected void apply(Void nil, ResourceManager resourceManager, ProfilerFiller profiler) {
                    ProfileLoader.invalidate(resourceManager);
                }
            });
        }
    }
}
