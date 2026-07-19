package net.ctov.petshop;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.BaseCoralPlantTypeBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.pools.LegacySinglePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElementType;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorList;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Optional;

/**
 * Custom {@link LegacySinglePoolElement} that handles the petshop data markers
 * ({@code petshop_water}, {@code petshop_chest}, {@code petshop_cage_0..3})
 * that ship inside CTOV's {@code village/<biome>/jobsite/petshop.nbt}
 * structures.
 *
 * <p>Without this element type, the markers are silently cleared to air by
 * vanilla's {@link LegacySinglePoolElement#handleDataMarker} (a no-op), so
 * petshop buildings generate with no animals, no chest loot, and no fish-tank
 * décor. DI's own {@code domesticationinnovation:petshop} element only handles
 * buildings that DI itself injects into <em>vanilla</em> villages, so it
 * doesn't help for CTOV-injected petshops.</p>
 *
 * <p>The element adds a {@code biome_profile} field to the codec. The profile
 * id is resolved at generation time against
 * {@code data/<namespace>/petshop_spawns/<path>.json}. If the profile is
 * missing or empty, the marker still gets cleared to air (no crash); only
 * spawning is skipped.</p>
 *
 * <p>Marker dispatch mirrors DI's {@code PetshopStructurePoolElement}:</p>
 * <ul>
 *   <li>{@code petshop_water} — spawn 2 from the {@code fishtank} profile,
 *       then place water / seagrass / waterlogged coral at the marker.</li>
 *   <li>{@code petshop_chest} — clear marker, bind chest-below to
 *       {@code domesticationinnovation:chests/petshop_chest}. Missing DI =
 *       empty chest, no crash.</li>
 *   <li>{@code petshop_cage_0} — spawn 1–2 from {@code biome_profile}.</li>
 *   <li>{@code petshop_cage_1} — spawn 2–3 from {@code biome_profile}.</li>
 *   <li>{@code petshop_cage_2} — spawn 1–2 from {@code biome_profile}.</li>
 *   <li>{@code petshop_cage_3} — spawn 1 from {@code biome_profile}.</li>
 * </ul>
 *
 * <p>Age is applied <em>after</em> {@link Mob#finalizeSpawn} to avoid being
 * overwritten.</p>
 */
public class PetshopCompatStructurePoolElement extends LegacySinglePoolElement {

    public static final ResourceLocation PETSHOP_CHEST_LOOT =
            new ResourceLocation("domesticationinnovation", "chests/petshop_chest");

    /**
     * Profile id used for the {@code petshop_water} marker. Shared across all
     * biomes — the fishtank entities are the same regardless of village type.
     */
    public static final ResourceLocation FISHTANK_PROFILE_ID =
            new ResourceLocation("ctov", "fishtank");

    public static final Codec<PetshopCompatStructurePoolElement> CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    templateCodec(),
                    processorsCodec(),
                    projectionCodec(),
                    ResourceLocation.CODEC.fieldOf("biome_profile").forGetter(e -> e.biomeProfile)
            ).apply(instance, PetshopCompatStructurePoolElement::new));

    private final ResourceLocation biomeProfile;

    public PetshopCompatStructurePoolElement(
            Either<ResourceLocation, StructureTemplate> template,
            Holder<StructureProcessorList> processors,
            StructureTemplatePool.Projection projection,
            ResourceLocation biomeProfile) {
        super(template, processors, projection);
        this.biomeProfile = biomeProfile;
    }

    @Override
    public StructurePoolElementType<?> getType() {
        return PetshopCompatRegistries.PETSHOP_COMPAT_TYPE.get();
    }

    @Override
    public void handleDataMarker(
            LevelAccessor levelAccessor,
            StructureTemplate.StructureBlockInfo blockInfo,
            BlockPos pos,
            Rotation rotation,
            RandomSource random,
            BoundingBox box) {
        String marker = blockInfo.nbt().getString("metadata");
        switch (marker) {
            case "petshop_water" -> handleWater(levelAccessor, blockInfo, random);
            case "petshop_chest" -> handleChest(levelAccessor, blockInfo, random);
            case "petshop_cage_0" -> handleCage(levelAccessor, blockInfo, random, 1, 2);
            case "petshop_cage_1" -> handleCage(levelAccessor, blockInfo, random, 2, 3);
            case "petshop_cage_2" -> handleCage(levelAccessor, blockInfo, random, 1, 2);
            case "petshop_cage_3" -> handleCage(levelAccessor, blockInfo, random, 1, 1);
            default -> {
                // Unknown marker — fall through to vanilla behaviour (clear to air).
                levelAccessor.setBlock(blockInfo.pos(), Blocks.AIR.defaultBlockState(), 2);
            }
        }
    }

    private void handleWater(LevelAccessor levelAccessor, StructureTemplate.StructureBlockInfo blockInfo, RandomSource random) {
        PetshopSpawnProfile profile = loadProfile(FISHTANK_PROFILE_ID, levelAccessor);
        if (profile != null) {
            spawnFromProfile(levelAccessor, blockInfo.pos(), random, profile, 2, null);
        }
        BlockState state;
        float f = random.nextFloat();
        if (f < 0.5F) {
            state = Blocks.SEAGRASS.defaultBlockState();
        } else if (f < 0.75F) {
            BlockState coral = switch (random.nextInt(5)) {
                case 1 -> Blocks.TUBE_CORAL.defaultBlockState();
                case 2 -> Blocks.BRAIN_CORAL.defaultBlockState();
                case 3 -> Blocks.BUBBLE_CORAL.defaultBlockState();
                case 4 -> Blocks.FIRE_CORAL.defaultBlockState();
                default -> Blocks.HORN_CORAL.defaultBlockState();
            };
            state = coral.setValue(BaseCoralPlantTypeBlock.WATERLOGGED, true);
        } else {
            state = Blocks.WATER.defaultBlockState();
        }
        levelAccessor.setBlock(blockInfo.pos(), state, 2);
    }

    private void handleChest(LevelAccessor levelAccessor, StructureTemplate.StructureBlockInfo blockInfo, RandomSource random) {
        // Clear the marker block (vanilla would have replaced it with air anyway).
        levelAccessor.setBlock(blockInfo.pos(), Blocks.AIR.defaultBlockState(), 2);
        // Bind the chest below to DI's petshop loot table.
        //
        // NOTE: per maintainer observation, the chest in CTOV's petshops already
        // works in the stock CTOV+DI setup without any custom element type —
        // the .nbt files reference DI's loot table id and DI resolves it when
        // loaded. This call here is defensive: if for any reason the chest
        // below the marker doesn't already have the loot table bound (e.g.
        // when the .nbt uses a structure-block marker rather than a baked-in
        // LootTable NBT tag), we bind it ourselves. If DI's loot table is
        // unresolved (DI not loaded), the call is a no-op — the chest stays
        // empty, no crash.
        RandomizableContainerBlockEntity.setLootTable(
                levelAccessor,
                random,
                blockInfo.pos().below(),
                PETSHOP_CHEST_LOOT);
    }

    private void handleCage(LevelAccessor levelAccessor, StructureTemplate.StructureBlockInfo blockInfo, RandomSource random, int min, int max) {
        PetshopSpawnProfile profile = loadProfile(biomeProfile, levelAccessor);
        if (profile != null) {
            int count = min + (max > min ? random.nextInt(max - min + 1) : 0);
            spawnFromProfile(levelAccessor, blockInfo.pos(), random, profile, count, biomeProfile);
        }
        // Clear the marker regardless — vanilla does this and we want consistent behaviour.
        levelAccessor.setBlock(blockInfo.pos(), Blocks.AIR.defaultBlockState(), 2);
    }

    private void spawnFromProfile(
            LevelAccessor levelAccessor,
            BlockPos pos,
            RandomSource random,
            PetshopSpawnProfile profile,
            int count,
            ResourceLocation profileId) {
        if (!(levelAccessor instanceof ServerLevelAccessor serverLevelAccessor)) {
            return;
        }
        for (int i = 0; i < count; i++) {
            Optional<EntityType<?>> picked = profile.pickEntity(random);
            if (picked.isEmpty()) {
                continue;
            }
            EntityType<?> type = picked.get();
            Entity entity = type.create(serverLevelAccessor.getLevel());
            if (entity == null) {
                continue;
            }
            entity.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
            entity.setYRot(random.nextFloat() * 360.0F - 180.0F);
            if (entity instanceof Mob mob) {
                mob.setPersistenceRequired();
                DifficultyInstance difficulty = serverLevelAccessor.getCurrentDifficultyAt(mob.blockPosition());
                mob.finalizeSpawn(serverLevelAccessor, difficulty, MobSpawnType.STRUCTURE, null, null);
                // Apply age AFTER finalizeSpawn — finalizeSpawn resets it.
                Optional<PetshopSpawnProfile.Entry> matchedEntry = matchEntry(profile, type);
                matchedEntry.ifPresent(entry -> {
                    int age = profile.ageFor(entry);
                    if (age != 0) {
                        mob.setAge(age);
                    }
                });
            }
            serverLevelAccessor.addFreshEntityWithPassengers(entity);
        }
    }

    private Optional<PetshopSpawnProfile.Entry> matchEntry(PetshopSpawnProfile profile, EntityType<?> type) {
        ResourceLocation actualId = BuiltInRegistries.ENTITY_TYPES.getKey(type);
        if (actualId == null) {
            return Optional.empty();
        }
        return profile.entries().stream()
                .filter(e -> e.entity().equals(actualId))
                .findFirst();
    }

    /**
     * Load a {@link PetshopSpawnProfile} from
     * {@code data/<namespace>/petshop_spawns/<path>.json} via the server's
     * resource manager. Called on-demand during worldgen; no caching because
     * village generation is rare and the JSON files are tiny.
     *
     * <p>Returns {@code null} if the profile can't be resolved (missing file,
     * malformed JSON, or not running on a server level). Callers must
     * null-check and skip spawning accordingly.</p>
     */
    private PetshopSpawnProfile loadProfile(ResourceLocation id, LevelAccessor levelAccessor) {
        if (!(levelAccessor instanceof ServerLevelAccessor serverLevelAccessor)) {
            return null;
        }
        ResourceManager rm = serverLevelAccessor.getLevel().getServer().getResourceManager();
        ResourceLocation path = new ResourceLocation(id.getNamespace(), "petshop_spawns/" + id.getPath() + ".json");
        Optional<Resource> resource = rm.getResource(path);
        if (resource.isEmpty()) {
            return null;
        }
        try (InputStreamReader reader = new InputStreamReader(resource.get().open())) {
            JsonElement json = JsonParser.parseReader(reader);
            return PetshopSpawnProfile.CODEC.parse(JsonOps.INSTANCE, json)
                    .result()
                    .orElse(null);
        } catch (IOException e) {
            return null;
        }
    }

    private boolean isLootTablePresent(LevelAccessor levelAccessor, ResourceLocation lootTableId) {
        if (!(levelAccessor instanceof ServerLevelAccessor serverLevelAccessor)) {
            return false;
        }
        return serverLevelAccessor.getLevel().getServer().getLootData().getId(
                net.minecraft.world.level.storage.loot.LootTable.TABLE_KEY.context()
        ) != null || lootTableId != null;
    }

    public String toString() {
        return "PetshopCompat[" + this.template + ", profile=" + this.biomeProfile + "]";
    }
}
