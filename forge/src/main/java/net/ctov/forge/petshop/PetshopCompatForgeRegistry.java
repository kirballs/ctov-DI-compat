package net.ctov.forge.petshop;

import net.ctov.petshop.PetshopCompatRegistries;
import net.ctov.petshop.PetshopCompatStructurePoolElement;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElementType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/**
 * Forge-side registration of the {@code ctov:petshop_compat} structure pool
 * element type.
 *
 * <p>Mirrors the pattern used by DI's own {@code DIVillagePieceRegistry}:
 * a {@link DeferredRegister} against
 * {@link net.minecraft.core.registries.Registries#STRUCTURE_POOL_ELEMENT}
 * with one entry, {@code petshop_compat}, bound to
 * {@link PetshopCompatStructurePoolElement#CODEC}.</p>
 *
 * <p>The {@link DeferredRegister} itself is wired onto the Forge mod event bus
 * by {@code net.ctov.forge.ctovForge}'s constructor; the {@link #init()} call
 * (also from the constructor) publishes the resulting {@link RegistryObject}
 * to {@link PetshopCompatRegistries} so the common code can resolve it at
 * worldgen time.</p>
 */
public final class PetshopCompatForgeRegistry {

    private PetshopCompatForgeRegistry() {
    }

    public static final DeferredRegister<StructurePoolElementType<?>> POOL_ELEMENTS =
            DeferredRegister.create(Registries.STRUCTURE_POOL_ELEMENT, "ctov");

    public static final RegistryObject<StructurePoolElementType<PetshopCompatStructurePoolElement>> PETSHOP_COMPAT =
            POOL_ELEMENTS.register("petshop_compat", () -> () -> PetshopCompatStructurePoolElement.CODEC);

    /**
     * Publish the registry object to the common accessor. Must be called from
     * the Forge mod constructor AFTER {@link #POOL_ELEMENTS} has been
     * registered on the mod event bus.
     */
    public static void init() {
        // RegistryObject is itself a Supplier<StructurePoolElementType<...>>,
        // so we can pass it directly.
        PetshopCompatRegistries.setTypeSupplier(PETSHOP_COMPAT);
    }
}
