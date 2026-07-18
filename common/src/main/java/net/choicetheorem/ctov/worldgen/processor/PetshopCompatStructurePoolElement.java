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
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.tags.TagKey;
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
 * <p>This element mirrors DI's marker handling for every CTOV variant, with a per-village tag
 * resolution scheme:</p>
 * <ul>
 *   <li><b>Vanilla-aligned variants</b> (plains, plains_fortified, taiga, taiga_fortified,
 *       halloween, desert, desert_oasis, snowy_igloo, christmas, savanna, savanna_na) use DI's
 *       original numbered tags {@code petstore_cage_0..3}. All cage markers in a given variant
 *       use the SAME tag (e.g. plains → all cages use {@code petstore_cage_0}). This differs
 *       from DI's per-marker behavior but matches the per-village theming the user requested.</li>
 *   <li><b>Non-vanilla variants</b> (beach, jungle, jungle_tree, mesa, mesa_fortified, mountain,
 *       mountain_alpine, mushroom, swamp, swamp_fortified) use new flat tags
 *       {@code domesticationinnovation:petshop_cage_<biome>} (e.g. {@code petshop_cage_jungle}).
 *       All cage markers in a given variant use the same biome tag.</li>
 *   <li><b>Fishtank marker</b> always uses {@code domesticationinnovation:petstore_fishtank}
 *       for every variant.</li>
 *   <li><b>Chest marker</b> binds {@code domesticationinnovation:chests/petshop_chest} loot
 *       table to the block below the marker (mirrors DI).</li>
 * </ul>
 *
 * <p>Optional data-driven spawn profiles at {@code ctov:petshop_profiles/<variant>/<marker>.json}
 * allow per-(variant, marker) overrides of weight/baby/age fields (age may be negative, e.g.
 * {@code -60000} for a permanent baby). Profiles are consulted between the primary tag and the
 * DI fallback tag.</p>
 */
public class PetshopCompatStructurePoolElement extends LegacySinglePoolElement {
	public static final StructurePoolElementType<PetshopCompatStructurePoolElement> TYPE = () -> CODEC;
	public static final Codec<PetshopCompatStructurePoolElement> CODEC = RecordCodecBuilder.create((instance) ->
		instance.group(templateCodec(), processorsCodec(), projectionCodec()).apply(instance, PetshopCompatStructurePoolElement::new)
	);

	// Per task §3: CTOV-specific entity tags live under the `domesticationinnovation` namespace
	// so users can edit them with the same datapack tooling they already use for DI's own tags.
	private static final String TAG_NAMESPACE = "domesticationinnovation";
	// Profile JSON resources are a CTOV-internal concept and live under CTOV's own namespace.
	private static final String PROFILE_NAMESPACE = "ctov";
	private static final String PROFILE_BASE_PATH = "petshop_profiles";
	// Loot table used by the petshop chest marker when DI is loaded. Owned by DI; we only reference it.
	private static final ResourceLocation DI_PETSHOP_CHEST_LOOT =
		ResourceLocation.fromNamespaceAndPath("domesticationinnovation", "chests/petshop_chest");
	// Fishtank tag (shared across all variants) — DI-owned, unchanged.
	private static final ResourceLocation FISHTANK_TAG =
		ResourceLocation.fromNamespaceAndPath("domesticationinnovation", "petstore_fishtank");

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
		SpawnResolution resolution = resolveSpawns(serverLevel, variant, marker);
		if (enablePetshopDebugLogging()) {
			CTOV.LOGGER.info("[CTOV-DI-Compat] marker={} variant={} primaryTag={} fallbackTag={} entries={} reason={}",
				marker, variant, resolution.primaryTag, resolution.fallbackTag, resolution.entries.size(), resolution.reason);
		}
		if (marker.equals("petshop_water")) {
			accessor.setBlock(structureBlockInfo.pos(), resolveWaterDecoration(random), 2);
		} else {
			accessor.setBlock(structureBlockInfo.pos(), Blocks.AIR.defaultBlockState(), 2);
		}
		if (resolution.entries.isEmpty()) {
			return;
		}
		int count = resolveSpawnCount(marker, random);
		for (int i = 0; i < count; i++) {
			WeightedSpawn selected = pickWeighted(random, resolution.entries);
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
			// Apply explicit age first (allows age=-60000 for a permanent baby that will never grow up).
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

	private SpawnResolution resolveSpawns(ServerLevelAccessor serverLevel, String variant, String marker) {
		List<WeightedSpawn> entries = new ArrayList<>();
		ResourceLocation primaryTag = null;
		ResourceLocation fallbackTag = null;
		String reason = "no entries";
		SpawnProfile profile = getProfile(serverLevel.getLevel().getServer().getResourceManager(), variant, marker);
		// Layer 1: Primary tag (per-village biome tag).
		//   For vanilla-aligned variants: petstore_cage_N (N matches the biome family).
		//   For non-vanilla variants: petshop_cage_<biome> (new CTOV tag).
		//   For fishtank marker: petstore_fishtank (shared across all variants).
		if (enableCtovPetshopTagResolution()) {
			primaryTag = profile.ctovTag != null ? profile.ctovTag : defaultCtovTag(variant, marker);
			if (primaryTag != null) {
				entries.addAll(readTagEntries(serverLevel, primaryTag));
				reason = entries.isEmpty() ? "primary tag empty" : "primary tag resolved";
			}
		}
		// Layer 2: Profile entries (per-(variant, marker) for fine-tuning weight/baby/age).
		if (entries.isEmpty() && !profile.entries.isEmpty()) {
			entries.addAll(resolveProfileEntries(serverLevel, profile.entries));
			reason = entries.isEmpty() ? "profile entries unresolved" : "profile entries";
		}
		// Layer 3: DI fallback tag — only consulted for cage markers in non-vanilla variants
		// when the CTOV biome tag yielded no entries. Falls back to petstore_cage_0 (plains/taiga
		// pets) as a safe default so petshops always spawn SOMETHING even if the user deletes
		// the CTOV tag without providing a replacement. For vanilla-aligned variants, layer 1
		// already used the DI tag, so no fallback is needed.
		if (entries.isEmpty() && enableDiPetshopFallbackTags()) {
			fallbackTag = profile.diFallbackTag != null ? profile.diFallbackTag : defaultDiFallbackTag(variant, marker);
			if (fallbackTag != null) {
				entries.addAll(readTagEntries(serverLevel, fallbackTag));
				reason = entries.isEmpty() ? "di fallback empty" : "di fallback";
			}
		}
		return new SpawnResolution(entries, primaryTag, fallbackTag, reason);
	}

	private SpawnProfile getProfile(ResourceManager resourceManager, String variant, String marker) {
		ResourceLocation profileId = ResourceLocation.fromNamespaceAndPath(
			PROFILE_NAMESPACE, PROFILE_BASE_PATH + "/" + variant + "/" + marker + ".json");
		return resourceManager.getResource(profileId)
			.map(resource -> parseProfile(resource, profileId))
			.orElse(SpawnProfile.EMPTY);
	}

	private SpawnProfile parseProfile(Resource resource, ResourceLocation profileId) {
		try (Reader reader = resource.openAsReader()) {
			JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
			ResourceLocation ctovTag = readResourceLocation(root, "ctov_tag");
			ResourceLocation diTag = readResourceLocation(root, "di_fallback_tag");
			List<SpawnEntry> entries = new ArrayList<>();
			JsonArray array = root.has("entries") && root.get("entries").isJsonArray()
				? root.getAsJsonArray("entries") : new JsonArray();
			for (JsonElement element : array) {
				if (!element.isJsonObject()) {
					continue;
				}
				JsonObject entryObject = element.getAsJsonObject();
				ResourceLocation entity = readResourceLocation(entryObject, "entity");
				if (entity == null) {
					continue;
				}
				int weight = entryObject.has("weight") ? entryObject.get("weight").getAsInt() : 1;
				Boolean baby = entryObject.has("baby") ? entryObject.get("baby").getAsBoolean() : null;
				Integer age = entryObject.has("age") ? entryObject.get("age").getAsInt() : null;
				entries.add(new SpawnEntry(entity, weight, baby, age));
			}
			return new SpawnProfile(ctovTag, diTag, entries);
		} catch (Exception exception) {
			CTOV.LOGGER.warn("[CTOV-DI-Compat] Failed to parse profile {}: {}", profileId, exception.getMessage());
			return SpawnProfile.EMPTY;
		}
	}

	private List<WeightedSpawn> resolveProfileEntries(ServerLevelAccessor serverLevel, List<SpawnEntry> profileEntries) {
		Registry<EntityType<?>> registry = serverLevel.registryAccess().registryOrThrow(Registries.ENTITY_TYPE);
		List<WeightedSpawn> resolved = new ArrayList<>();
		for (SpawnEntry entry : profileEntries) {
			EntityType<?> type = registry.getOptional(ResourceKey.create(Registries.ENTITY_TYPE, entry.entity)).orElse(null);
			if (type != null && entry.weight > 0) {
				resolved.add(new WeightedSpawn(type, entry.weight, entry.baby, entry.age));
			}
		}
		return resolved;
	}

	private List<WeightedSpawn> readTagEntries(ServerLevelAccessor serverLevel, @Nullable ResourceLocation tagId) {
		if (tagId == null) {
			return List.of();
		}
		Registry<EntityType<?>> registry = serverLevel.registryAccess().registryOrThrow(Registries.ENTITY_TYPE);
		HolderSet.Named<EntityType<?>> named = registry.getTag(TagKey.create(Registries.ENTITY_TYPE, tagId)).orElse(null);
		if (named == null || named.size() == 0) {
			return List.of();
		}
		List<WeightedSpawn> entries = new ArrayList<>();
		for (Holder<EntityType<?>> holder : named) {
			entries.add(new WeightedSpawn(holder.value(), 1, null, null));
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
	 * Returns the primary tag for a (variant, marker) pair. This is the per-village biome tag.
	 * For the fishtank marker, returns the shared petstore_fishtank tag regardless of variant.
	 * For cage markers, returns the variant's biome tag (petstore_cage_N or petshop_cage_<biome>).
	 */
	private @Nullable ResourceLocation defaultCtovTag(String variant, String marker) {
		// Fishtank marker always uses the shared petstore_fishtank tag, regardless of variant.
		if (marker.equals("petshop_water")) {
			return FISHTANK_TAG;
		}
		// Cage markers: resolve to ONE biome tag per village variant (per user spec).
		String cageBiome = variantToCageBiome(variant);
		return cageBiomeToTag(cageBiome);
	}

	/**
	 * Maps a CTOV village variant to its cage biome name. Vanilla-aligned variants return
	 * a numbered biome ("0".."3") that maps to the original DI tag. Non-vanilla variants
	 * return a named biome ("jungle", "swamp", etc.) that maps to a new CTOV tag.
	 */
	private static String variantToCageBiome(String variant) {
		return switch (variant) {
			// Vanilla-aligned — use original DI numbered tags (petstore_cage_0..3)
			case "plains", "plains_fortified", "taiga", "taiga_fortified", "halloween" -> "0";
			case "desert", "desert_oasis" -> "1";
			case "snowy_igloo", "christmas" -> "2";
			case "savanna", "savanna_na" -> "3";
			// Non-vanilla — use new CTOV biome tags (petshop_cage_<biome>)
			case "beach" -> "beach";
			case "jungle" -> "jungle";
			case "jungle_tree" -> "bamboo";
			case "mesa", "mesa_fortified" -> "badlands";
			case "mountain", "mountain_alpine" -> "mountains";
			case "mushroom" -> "mushroom";
			case "swamp", "swamp_fortified" -> "swamp";
			// Default: plains/taiga tag (safe fallback for unknown variants)
			default -> "0";
		};
	}

	/**
	 * Maps a cage biome name to its tag ResourceLocation. Numbered biomes ("0".."3") map
	 * to the original DI tags (petstore_cage_N). Named biomes map to the new CTOV tags
	 * (petshop_cage_<biome>).
	 */
	private static @Nullable ResourceLocation cageBiomeToTag(String biome) {
		return switch (biome) {
			case "0" -> ResourceLocation.fromNamespaceAndPath(TAG_NAMESPACE, "petstore_cage_0");
			case "1" -> ResourceLocation.fromNamespaceAndPath(TAG_NAMESPACE, "petstore_cage_1");
			case "2" -> ResourceLocation.fromNamespaceAndPath(TAG_NAMESPACE, "petstore_cage_2");
			case "3" -> ResourceLocation.fromNamespaceAndPath(TAG_NAMESPACE, "petstore_cage_3");
			// New CTOV biome tags — flat names, no path nesting
			case "beach", "bamboo", "cherry", "jungle", "mountains", "mushroom", "swamp", "badlands"
				-> ResourceLocation.fromNamespaceAndPath(TAG_NAMESPACE, "petshop_cage_" + biome);
			default -> null;
		};
	}

	/**
	 * Returns the DI fallback tag for a (variant, marker) pair. Only non-null for cage markers
	 * in non-vanilla variants — falls back to petstore_cage_0 (plains/taiga pets) as a safe
	 * default when the CTOV biome tag is empty. For vanilla-aligned variants and the fishtank
	 * marker, returns null (layer 1 already covered it).
	 */
	private @Nullable ResourceLocation defaultDiFallbackTag(String variant, String marker) {
		// Fishtank marker has no DI fallback (it always uses petstore_fishtank via the primary tag).
		if (marker.equals("petshop_water")) {
			return null;
		}
		String biome = variantToCageBiome(variant);
		return switch (biome) {
			// Vanilla-aligned variants already use the DI tag as primary; no fallback needed
			case "0", "1", "2", "3" -> null;
			// Non-vanilla variants fall back to petstore_cage_0 (plains/taiga pets) as a safe default.
			// This ensures petshops always spawn SOMETHING even if the user deletes the CTOV tag.
			default -> ResourceLocation.fromNamespaceAndPath(TAG_NAMESPACE, "petstore_cage_0");
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

	private record SpawnEntry(ResourceLocation entity, int weight, @Nullable Boolean baby, @Nullable Integer age) {
	}

	private record SpawnProfile(@Nullable ResourceLocation ctovTag, @Nullable ResourceLocation diFallbackTag, List<SpawnEntry> entries) {
		private static final SpawnProfile EMPTY = new SpawnProfile(null, null, List.of());
	}

	private record WeightedSpawn(EntityType<?> type, int weight, @Nullable Boolean baby, @Nullable Integer age) {
	}

	private record SpawnResolution(List<WeightedSpawn> entries, @Nullable ResourceLocation primaryTag, @Nullable ResourceLocation fallbackTag, String reason) {
	}
}
