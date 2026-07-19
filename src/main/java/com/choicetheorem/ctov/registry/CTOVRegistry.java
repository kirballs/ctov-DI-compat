package com.choicetheorem.ctov.registry;

import com.choicetheorem.ctov.worldgen.PetshopCompatStructurePoolElement;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElementType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/**
 * Forge registry holder for CTOV's custom structure pool element types.
 *
 * <p>Currently exposes a single element type, {@code ctov:petshop_compat}, whose codec is
 * {@link PetshopCompatStructurePoolElement#CODEC}. When a village pool entry in a
 * {@code house.json} references {@code "element_type": "ctov:petshop_compat"}, the Forge
 * pool-element registry will deserialize that entry into a
 * {@link PetshopCompatStructurePoolElement} instance.
 *
 * <p>The {@link DeferredRegister} is registered on the mod event bus from
 * {@code ctov.java}'s constructor — see that class for the wiring.
 */
public final class CTOVRegistry {

    /** Namespace used for the {@link DeferredRegister}. Must match the mod id. */
    public static final String MOD_ID = "ctov";

    public static final DeferredRegister<StructurePoolElementType<?>> POOL_ELEMENTS =
            DeferredRegister.create(Registries.STRUCTURE_POOL_ELEMENT, MOD_ID);

    /**
     * The {@code ctov:petshop_compat} pool element type. Internally backed by
     * {@link PetshopCompatStructurePoolElement#CODEC}.
     */
    public static final RegistryObject<StructurePoolElementType<PetshopCompatStructurePoolElement>> PETSHOP_COMPAT_POOL_ELEMENT =
            POOL_ELEMENTS.register("petshop_compat",
                    () -> () -> PetshopCompatStructurePoolElement.CODEC);

    private CTOVRegistry() {}
}
