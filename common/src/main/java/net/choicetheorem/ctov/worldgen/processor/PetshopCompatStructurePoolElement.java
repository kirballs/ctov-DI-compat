package net.choicetheorem.ctov.worldgen.processor;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.choicetheorem.ctov.CTOV;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.BaseCoralPlantTypeBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.pools.LegacySinglePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElementType;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.JigsawReplacementProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorList;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static net.choicetheorem.ctov.platform.CTOVConfigHelper.*;

/**
 * CTOV-side compatibility layer that reproduces Domestication Innovation's petshop marker-driven
 * spawning for CTOV petshop structure pieces.
 *
 * <p>DI registers its own {@code domesticationinnovation:petshop} pool element type which handles
 * {@code petshop_water}, {@code petshop_chest}, {@code petshop_cage_0..3} data markers. DI only
 * auto-injects into the 5 vanilla village pools (plains/desert/savanna/snowy/taiga); CTOV's 16
 * non-vanilla biome variants would never receive petshop spawns through DI alone.</p>
 *
 * <p>This element mirrors DI's marker handling for every CTOV variant. Spawn lists are loaded from
 * a single set of unified JSON files at {@code ctov:petshop_spawns/<biome>.json}, one per biome
 * family. Each file contains a list of entries with inline {@code weight}, {@code baby}, and
 * {@code age} fields, so users have full control over spawn rates and baby/adult state without
 * having to maintain separate tag files and profile overrides.</p>
 *
 * <p>Resolution is a single lookup:</p>
 * <ol>
 *   <li>Resolve the variant's biome name (e.g. {@code "plains"}, {@code "jungle"}, {@code "swamp"}).</li>
 *   <li>For the {@code petshop_water} marker, use {@code "fishtank"} as the biome name.</li>
 *   <li>Load {@code ctov:petshop_spawns/<biome>.json} and parse its {@code entries} array.</li>
 *   <li>Pick {@code resolveSpawnCount(marker)} entities via weighted random selection.</li>
 *   <li>If the file is missing or empty, no entities spawn (safe failure — marker cleared to AIR,
 *       debug log records the reason). No fallback to DI's own tags.</li>
 * </ol>
 *
 * <p>The {@code petshop_chest} marker is handled separately and binds DI's
 * {@code domesticationinnovation:chests/petshop_chest} loot table to the block below.</p>
 *
 * <p>The {@code age} field follows vanilla {@link AgeableMob#setAge(int)} semantics: any negative
 * value spawns the mob as a baby; the mob grows up when its age timer reaches 0; more negative
 * values mean longer baby state (e.g. {@code -60000} ≈ 50 in-game hours / ~3 real-time hours at
 * normal tick speed). Use {@link Integer#MIN_VALUE} for an effectively permanent baby.</p>
 */
public class PetshopCompatStructurePoolElement extends LegacySinglePoolElement {
        public static final StructurePoolElementType<PetshopCompatStructurePoolElement> TYPE = () -> CODEC;
        public static final Codec<PetshopCompatStructurePoolElement> CODEC = RecordCodecBuilder.create((instance) ->
                instance.group(templateCodec(), processorsCodec(), projectionCodec()).apply(instance, PetshopCompatStructurePoolElement::new)
        );

        // Spawn-list JSON resources live under CTOV's own namespace.
        private static final String SPAWN_LIST_NAMESPACE = "ctov";
        private static final String SPAWN_LIST_BASE_PATH = "petshop_spawns";
        // Loot table used by the petshop chest marker when DI is loaded. Owned by DI; we only reference it.
        private static final ResourceLocation DI_PETSHOP_CHEST_LOOT =
                ResourceLocation.fromNamespaceAndPath("domesticationinnovation", "chests/petshop_chest");

        protected PetshopCompatStructurePoolElement(Either<ResourceLocation, StructureTemplate> template, Holder<StructureProcessorList> processors, StructureTemplatePool.Projection projection) {
                super(template, processors, projection);
        }

        @Override
        public void handleDataMarker(LevelAccessor accessor, StructureTemplate.StructureBlockInfo structureBlockInfo, BlockPos pos, Rotation rotation, RandomSource random, BoundingBox box) {
                String marker = structureBlockInfo.nbt() == null ? "" : structureBlockInfo.nbt().getString("metadata");
                if (marker.isEmpty() || !marker.startsWith("petshop_")) {
                        return;
                }
                if (!(accessor instanceof ServerLevelAccessor serverLevel)) {
                        return;
                }
                if (!enableDiPetshopCompat()) {
                        accessor.setBlock(structureBlockInfo.pos(), Blocks.AIR.defaultBlockState(), 2);
                        return;
                }
                // Chest marker has no entity-spawn semantics — handle it first and short-circuit.
                if (marker.equals("petshop_chest")) {
                        handleChestMarker(accessor, structureBlockInfo, random);
                        return;
                }
                String variant = resolveVariant();
                String biome = markerToBiome(variant, marker);
                List<WeightedSpawn> entries = resolveSpawns(serverLevel, biome);
                if (enablePetshopDebugLogging()) {
                        CTOV.LOGGER.info("[CTOV-DI-Compat] marker={} variant={} biome={} entries={} file=ctov:{}/{}.json",
                                marker, variant, biome, entries.size(), SPAWN_LIST_BASE_PATH, biome);
                }
                if (marker.equals("petshop_water")) {
                        accessor.setBlock(structureBlockInfo.pos(), resolveWaterDecoration(random), 2);
                } else {
                        accessor.setBlock(structureBlockInfo.pos(), Blocks.AIR.defaultBlockState(), 2);
                }
                if (entries.isEmpty()) {
                        return;
                }
                int count = resolveSpawnCount(marker, random);
                for (int i = 0; i < count; i++) {
                        WeightedSpawn selected = pickWeighted(random, entries);
                        if (selected == null) {
                                continue;
                        }
                        spawnEntity(serverLevel, structureBlockInfo.pos(), random, selected);
                        if (enablePetshopDebugLogging()) {
                                CTOV.LOGGER.info("[CTOV-DI-Compat] spawned marker={} entity={} baby={} age={}",
                                        marker, selected.type, selected.baby, selected.age);
                        }
                }
        }

        /**
         * Mirrors DI's chest-marker handling: clear the structure-block marker, then bind DI's
         * petshop_chest loot table to the block below (where the chest actually sits in every
         * petshop template). If DI is not actually loaded the loot table won't resolve and the
         * chest stays empty — but we only register this element type when DI is loaded anyway
         * (per the lithostitched load-conditions in the modifier JSONs), so this is a defensive
         * no-op rather than a likely failure mode.
         */
        private void handleChestMarker(LevelAccessor accessor, StructureTemplate.StructureBlockInfo info, RandomSource random) {
                accessor.setBlock(info.pos(), Blocks.AIR.defaultBlockState(), 2);
                BlockPos chestPos = info.pos().below();
                BlockEntity existing = accessor.getBlockEntity(chestPos);
                if (existing instanceof RandomizableContainerBlockEntity container) {
                        container.setLootTable(DI_PETSHOP_CHEST_LOOT, random.nextLong());
                        container.setChanged();
                } else {
                        RandomizableContainerBlockEntity.setLootTable(accessor, random, chestPos, DI_PETSHOP_CHEST_LOOT);
                }
                if (enablePetshopDebugLogging()) {
                        CTOV.LOGGER.info("[CTOV-DI-Compat] chest marker bound lootTable={} at={}", DI_PETSHOP_CHEST_LOOT, chestPos);
                }
        }

        private void spawnEntity(ServerLevelAccessor level, BlockPos pos, RandomSource random, WeightedSpawn selected) {
                Entity entity = selected.type.create(level.getLevel());
                if (entity == null) {
                        return;
                }
                entity.setPos(Vec3.atBottomCenterOf(pos));
                entity.setYRot(random.nextInt(360) - 180);
                entity.setXRot(random.nextInt(360) - 180);
                if (entity instanceof Mob mob) {
                        mob.setPersistenceRequired();
                        mob.finalizeSpawn(level, level.getCurrentDifficultyAt(mob.blockPosition()), MobSpawnType.STRUCTURE, null, null);
                }
                if (entity instanceof AgeableMob ageable) {
                        // Apply explicit age first (allows age=-60000 for a long-lasting baby).
                        // Only fall back to the baby flag if age was not specified. Non-ageable entities ignore both.
                        if (selected.age != null) {
                                ageable.setAge(selected.age);
                        } else if (Boolean.TRUE.equals(selected.baby)) {
                                ageable.setBaby(true);
                        }
                        if (forcePetshopBabySpawns() && selected.age == null) {
                                ageable.setBaby(true);
                        }
                }
                level.addFreshEntityWithPassengers(entity);
        }

        /**
         * Loads {@code ctov:petshop_spawns/<biome>.json} and returns the parsed weighted spawn
         * list. Returns an empty list if the file is missing, malformed, or contains no resolvable
         * entries. There is no fallback — if the file is missing, no entities spawn.
         */
        private List<WeightedSpawn> resolveSpawns(ServerLevelAccessor serverLevel, String biome) {
                ResourceLocation resourceId = ResourceLocation.fromNamespaceAndPath(
                        SPAWN_LIST_NAMESPACE, SPAWN_LIST_BASE_PATH + "/" + biome + ".json");
                ResourceManager resourceManager = serverLevel.getLevel().getServer().getResourceManager();
                return resourceManager.getResource(resourceId)
                        .map(resource -> parseSpawnList(resource, resourceId, serverLevel))
                        .orElseGet(() -> {
                                if (enablePetshopDebugLogging()) {
                                        CTOV.LOGGER.warn("[CTOV-DI-Compat] spawn list not found: {}", resourceId);
                                }
                                return List.of();
                        });
        }

        private List<WeightedSpawn> parseSpawnList(Resource resource, ResourceLocation resourceId, ServerLevelAccessor serverLevel) {
                Registry<EntityType<?>> registry = serverLevel.registryAccess().registryOrThrow(Registries.ENTITY_TYPE);
                List<WeightedSpawn> entries = new ArrayList<>();
                try (Reader reader = resource.openAsReader()) {
                        JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
                        if (!root.has("entries") || !root.get("entries").isJsonArray()) {
                                return List.of();
                        }
                        JsonArray array = root.getAsJsonArray("entries");
                        for (JsonElement element : array) {
                                if (!element.isJsonObject()) {
                                        continue;
                                }
                                JsonObject entryObject = element.getAsJsonObject();
                                ResourceLocation entity = readResourceLocation(entryObject, "entity");
                                if (entity == null) {
                                        continue;
                                }
                                EntityType<?> type = registry.getOptional(ResourceKey.create(Registries.ENTITY_TYPE, entity)).orElse(null);
                                if (type == null) {
                                        if (enablePetshopDebugLogging()) {
                                                CTOV.LOGGER.warn("[CTOV-DI-Compat] unknown entity in {}: {}", resourceId, entity);
                                        }
                                        continue;
                                }
                                int weight = entryObject.has("weight") ? entryObject.get("weight").getAsInt() : 1;
                                if (weight <= 0) {
                                        continue;
                                }
                                Boolean baby = entryObject.has("baby") ? entryObject.get("baby").getAsBoolean() : null;
                                Integer age = entryObject.has("age") ? entryObject.get("age").getAsInt() : null;
                                entries.add(new WeightedSpawn(type, weight, baby, age));
                        }
                } catch (Exception exception) {
                        CTOV.LOGGER.warn("[CTOV-DI-Compat] Failed to parse spawn list {}: {}", resourceId, exception.getMessage());
                        return List.of();
                }
                return entries;
        }

        private static @Nullable ResourceLocation readResourceLocation(JsonObject object, String key) {
                if (!object.has(key)) {
                        return null;
                }
                return ResourceLocation.tryParse(object.get(key).getAsString());
        }

        /**
         * Returns the biome name used to look up the spawn list for a (variant, marker) pair.
         * For the fishtank marker, always returns {@code "fishtank"} (shared list across variants).
         * For cage markers, returns the variant's biome name.
         */
        private static String markerToBiome(String variant, String marker) {
                if (marker.equals("petshop_water")) {
                        return "fishtank";
                }
                return variantToBiome(variant);
        }

        /**
         * Maps a CTOV village variant to its biome name (used to look up
         * {@code ctov:petshop_spawns/<biome>.json}).
         */
        private static String variantToBiome(String variant) {
                return switch (variant) {
                        case "plains", "plains_fortified", "taiga", "taiga_fortified", "halloween" -> "plains";
                        case "desert", "desert_oasis" -> "desert";
                        case "snowy_igloo", "christmas" -> "snowy";
                        case "savanna", "savanna_na" -> "savanna";
                        case "beach" -> "beach";
                        case "jungle" -> "jungle";
                        case "jungle_tree" -> "bamboo";
                        case "mesa", "mesa_fortified" -> "badlands";
                        case "mountain", "mountain_alpine" -> "mountains";
                        case "mushroom" -> "mushroom";
                        case "swamp", "swamp_fortified" -> "swamp";
                        // Default: plains (safe fallback for unknown variants)
                        default -> "plains";
                };
        }

        private String resolveVariant() {
                return this.template.left()
                        .map(location -> {
                                String path = location.getPath();
                                String[] split = path.split("/");
                                for (int i = 0; i < split.length - 1; i++) {
                                        if (split[i].equals("village")) {
                                                return split[i + 1].toLowerCase(Locale.ROOT);
                                        }
                                }
                                return "plains";
                        })
                        .orElse("plains");
        }

        private int resolveSpawnCount(String marker, RandomSource random) {
                return switch (marker) {
                        case "petshop_water" -> 2;
                        case "petshop_cage_0", "petshop_cage_2" -> 1 + random.nextInt(2);
                        case "petshop_cage_1" -> 2 + random.nextInt(2);
                        case "petshop_cage_3" -> 1;
                        default -> 0;
                };
        }

        private @Nullable WeightedSpawn pickWeighted(RandomSource random, List<WeightedSpawn> entries) {
                if (entries.isEmpty()) {
                        return null;
                }
                int total = 0;
                for (WeightedSpawn entry : entries) {
                        total += Math.max(entry.weight, 0);
                }
                if (total <= 0) {
                        return null;
                }
                int value = random.nextInt(total);
                for (WeightedSpawn entry : entries) {
                        value -= Math.max(entry.weight, 0);
                        if (value < 0) {
                                return entry;
                        }
                }
                return entries.get(entries.size() - 1);
        }

        private BlockState resolveWaterDecoration(RandomSource random) {
                float chance = random.nextFloat();
                if (chance < 0.5F) {
                        return Blocks.SEAGRASS.defaultBlockState();
                }
                if (chance < 0.75F) {
                        Block coral = switch (random.nextInt(5)) {
                                case 1 -> Blocks.TUBE_CORAL;
                                case 2 -> Blocks.BRAIN_CORAL;
                                case 3 -> Blocks.BUBBLE_CORAL;
                                case 4 -> Blocks.FIRE_CORAL;
                                default -> Blocks.HORN_CORAL;
                        };
                        return coral.defaultBlockState().setValue(BaseCoralPlantTypeBlock.WATERLOGGED, true);
                }
                return Blocks.WATER.defaultBlockState();
        }

        @Override
        protected StructurePlaceSettings getSettings(Rotation rotation, BoundingBox box, boolean keepJigsaws) {
                StructurePlaceSettings settings = new StructurePlaceSettings();
                settings.setBoundingBox(box);
                settings.setRotation(rotation);
                settings.setKnownShape(true);
                settings.setIgnoreEntities(false);
                settings.setFinalizeEntities(true);
                if (!keepJigsaws) {
                        settings.addProcessor(JigsawReplacementProcessor.INSTANCE);
                }
                this.processors.value().list().forEach(settings::addProcessor);
                this.getProjection().getProcessors().forEach(settings::addProcessor);
                return settings;
        }

        @Override
        public StructurePoolElementType<?> getType() {
                return TYPE;
        }

        private record WeightedSpawn(EntityType<?> type, int weight, @Nullable Boolean baby, @Nullable Integer age) {
        }
}
