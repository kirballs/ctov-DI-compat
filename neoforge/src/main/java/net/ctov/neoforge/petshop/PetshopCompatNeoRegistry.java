package net.ctov.neoforge.petshop;

import net.ctov.petshop.PetshopCompatRegistries;
import net.ctov.petshop.PetshopCompatStructurePoolElement;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElementType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/**
 * NeoForge-side registration of the {@code ctov:petshop_compat} structure
 * pool element type.
 *
 * <p>NeoForge 20.2.x (MC 1.20.x) still ships the {@code DeferredRegister}
 * API under {@code net.minecraftforge.registries.*} — the package rename to
 * {@code net.neoforged.neoforge.registries.*} happened in NeoForge 20.4+.
 * This class uses the legacy package so it compiles against the same
 * dependency set as the Forge module.</p>
 *
 * <p>Functionally identical to
 * {@link net.ctov.forge.petshop.PetshopCompatForgeRegistry} — the only
 * difference is which platform's mod entry point wires it up.</p>
 */
public final class PetshopCompatNeoRegistry {

    private PetshopCompatNeoRegistry() {
    }

    public static final DeferredRegister<StructurePoolElementType<?>> POOL_ELEMENTS =
            DeferredRegister.create(Registries.STRUCTURE_POOL_ELEMENT, "ctov");

    public static final RegistryObject<StructurePoolElementType<PetshopCompatStructurePoolElement>> PETSHOP_COMPAT =
            POOL_ELEMENTS.register("petshop_compat", () -> () -> PetshopCompatStructurePoolElement.CODEC);

    /**
     * Publish the registry object to the common accessor. Must be called from
     * the NeoForge mod constructor AFTER {@link #POOL_ELEMENTS} has been
     * registered on the mod event bus.
     */
    public static void init() {
        // Wrap with a lambda because RegistryObject<StructurePoolElementType<...>>
        // is not directly convertible to Supplier<StructurePoolElementType<?>>
        // due to Java generics invariance.
        PetshopCompatRegistries.setTypeSupplier(() -> PETSHOP_COMPAT.get());
    }
}
