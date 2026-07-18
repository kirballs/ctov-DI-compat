package net.choicetheorem.ctov.registry.fabric.worldgen;

import net.choicetheorem.ctov.utils.TextUtils;
import net.choicetheorem.ctov.worldgen.processor.ModularCompatProcessor;
import net.choicetheorem.ctov.worldgen.processor.PetshopCompatStructurePoolElement;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElementType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;

public class WorldgenRegistry {
        public static void register() {
        }
        public static final StructureProcessorType<ModularCompatProcessor> MODULAR_COMPAT =
                Registry.register(
                        BuiltInRegistries.STRUCTURE_PROCESSOR,
                        TextUtils.res( "modular_compat"),
                        () -> ModularCompatProcessor.CODEC
                );
        // CTOV-side petshop compat element. Mirrors DI's `domesticationinnovation:petshop` element
        // but is registered under CTOV's namespace so it loads even when CTOV's lithostitched
        // modifier JSONs swap the element_type to `ctov:petshop_compat`. See
        // PetshopCompatStructurePoolElement javadoc for the resolution order.
        public static final StructurePoolElementType<PetshopCompatStructurePoolElement> PETSHOP_COMPAT =
                Registry.register(
                        BuiltInRegistries.STRUCTURE_POOL_ELEMENT,
                        TextUtils.res("petshop_compat"),
                        () -> PetshopCompatStructurePoolElement.CODEC
                );
}
