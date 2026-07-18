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
import net.minecraft.world.level.block.Rotation;
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

public class PetshopCompatStructurePoolElement extends LegacySinglePoolElement {
	public static final StructurePoolElementType<PetshopCompatStructurePoolElement> TYPE = () -> CODEC;
	public static final Codec<PetshopCompatStructurePoolElement> CODEC = RecordCodecBuilder.create((instance) ->
		instance.group(templateCodec(), processorsCodec(), projectionCodec()).apply(instance, PetshopCompatStructurePoolElement::new)
	);
	private static final String PROFILE_NAMESPACE = "ctov_compat";
	private static final String PROFILE_BASE_PATH = "petshop_profiles";
	
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
		String variant = resolveVariant();
		SpawnResolution resolution = resolveSpawns(serverLevel, variant, marker);
		if (enablePetshopDebugLogging()) {
			CTOV.LOGGER.info("[CTOV-DI-Compat] marker={} variant={} ctovTag={} fallbackTag={} entries={} reason={}", marker, variant, resolution.ctovTag, resolution.fallbackTag, resolution.entries.size(), resolution.reason);
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
				CTOV.LOGGER.info("[CTOV-DI-Compat] spawned marker={} entity={} baby={} age={}", marker, selected.type, selected.baby, selected.age);
			}
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
			if (selected.age != null) {
				ageable.setAge(selected.age);
			} else if (selected.baby != null && selected.baby) {
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
		ResourceLocation ctovSemanticTag = null;
		ResourceLocation diFallbackTag = null;
		String reason = "no entries";
		SpawnProfile profile = getProfile(serverLevel.getLevel().getServer().getResourceManager(), variant, marker);
		if (enableCtovPetshopTagResolution()) {
			ctovSemanticTag = profile.ctovTag != null ? profile.ctovTag : defaultCtovTag(variant, marker);
			entries.addAll(readTagEntries(serverLevel, ctovSemanticTag));
			reason = entries.isEmpty() ? "ctov tag empty" : "ctov tag resolved";
		}
		if (entries.isEmpty() && !profile.entries.isEmpty()) {
			entries.addAll(resolveProfileEntries(serverLevel, profile.entries));
			reason = entries.isEmpty() ? "profile entries unresolved" : "profile entries";
		}
		if (entries.isEmpty() && enableDiPetshopFallbackTags()) {
			diFallbackTag = profile.diFallbackTag != null ? profile.diFallbackTag : defaultDiFallbackTag(marker);
			if (diFallbackTag != null) {
				entries.addAll(readTagEntries(serverLevel, diFallbackTag));
				reason = entries.isEmpty() ? "di fallback empty" : "di fallback";
			}
		}
		return new SpawnResolution(entries, ctovSemanticTag, diFallbackTag, reason);
	}
	
	private SpawnProfile getProfile(ResourceManager resourceManager, String variant, String marker) {
		ResourceLocation profileId = ResourceLocation.fromNamespaceAndPath(PROFILE_NAMESPACE, PROFILE_BASE_PATH + "/" + variant + "/" + marker + ".json");
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
			JsonArray array = root.has("entries") && root.get("entries").isJsonArray() ? root.getAsJsonArray("entries") : new JsonArray();
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
	
	private ResourceLocation defaultCtovTag(String variant, String marker) {
		String markerName = marker.replace("petshop_", "");
		return ResourceLocation.fromNamespaceAndPath(PROFILE_NAMESPACE, "petshop/ctov_" + variant + "/" + markerName);
	}
	
	private @Nullable ResourceLocation defaultDiFallbackTag(String marker) {
		if (marker.equals("petshop_water")) {
			return ResourceLocation.fromNamespaceAndPath("domesticationinnovation", "petstore_fishtank");
		}
		if (marker.startsWith("petshop_cage_")) {
			return ResourceLocation.fromNamespaceAndPath("domesticationinnovation", marker.replace("petshop_", "petstore_"));
		}
		return null;
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
	
	private record SpawnResolution(List<WeightedSpawn> entries, @Nullable ResourceLocation ctovTag, @Nullable ResourceLocation fallbackTag, String reason) {
	}
}
