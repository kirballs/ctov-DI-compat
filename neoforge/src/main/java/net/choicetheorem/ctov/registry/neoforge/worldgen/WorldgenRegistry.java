package net.choicetheorem.ctov.registry.neoforge.worldgen;

import net.choicetheorem.ctov.worldgen.processor.ModularCompatProcessor;
import net.choicetheorem.ctov.worldgen.processor.PetshopCompatStructurePoolElement;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElementType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class WorldgenRegistry {
	public static final DeferredRegister<StructureProcessorType<?>> PROCESSORS =
		DeferredRegister.create(Registries.STRUCTURE_PROCESSOR, "ctov");

	public static final DeferredHolder<StructureProcessorType<?>, StructureProcessorType<ModularCompatProcessor>> MODULAR_COMPAT =
		PROCESSORS.register("modular_compat", () -> () -> ModularCompatProcessor.CODEC);

	// CTOV-side petshop compat element. Mirrors DI's `domesticationinnovation:petshop` element
	// but is registered under CTOV's namespace so it loads even when CTOV's lithostitched
	// modifier JSONs swap the element_type to `ctov:petshop_compat`. The forge subproject
	// picks this registration up via architectury's `transformProductionNeoForge` transform
	// (see forge/build.gradle), so this single registration covers both neoforge and forge.
	public static final DeferredRegister<StructurePoolElementType<?>> POOL_ELEMENTS =
		DeferredRegister.create(Registries.STRUCTURE_POOL_ELEMENT, "ctov");

	public static final DeferredHolder<StructurePoolElementType<?>, StructurePoolElementType<PetshopCompatStructurePoolElement>> PETSHOP_COMPAT =
		POOL_ELEMENTS.register("petshop_compat", () -> () -> PetshopCompatStructurePoolElement.CODEC);
}
