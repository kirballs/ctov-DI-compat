package net.ctov.quilt.petshop;

import net.ctov.petshop.PetshopCompatRegistries;
import net.ctov.petshop.PetshopCompatStructurePoolElement;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElementType;

/**
 * Quilt-side registration of the {@code ctov:petshop_compat} structure pool
 * element type.
 *
 * <p>Quilt uses the same registry API as Fabric (Quilt is Fabric-compatible),
 * so this is functionally identical to
 * {@link net.ctov.fabric.petshop.PetshopCompatFabricRegistry} — just packaged
 * under the quilt module so it loads under the Quilt entry point.</p>
 */
public final class PetshopCompatQuiltRegistry {

    private PetshopCompatQuiltRegistry() {
    }

    public static void init() {
        try {
            ResourceLocation id = new ResourceLocation("ctov", "petshop_compat");
            StructurePoolElementType<PetshopCompatStructurePoolElement> type =
                    () -> PetshopCompatStructurePoolElement.CODEC;
            StructurePoolElementType<?> registered = BuiltInRegistries.register(
                    BuiltInRegistries.STRUCTURE_POOL_ELEMENT,
                    id,
                    type);
            PetshopCompatRegistries.setTypeSupplier(() -> registered);
        } catch (Throwable t) {
            System.err.println("[CTOV] Failed to register ctov:petshop_compat StructurePoolElementType on Quilt: " + t);
        }
    }
}
