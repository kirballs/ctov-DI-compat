package net.ctov.petshop;

import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElementType;

import java.util.function.Supplier;

/**
 * Common-side accessor for the platform-registered {@code ctov:petshop_compat}
 * {@link StructurePoolElementType}.
 *
 * <p>Because CTOV ships as a multi-loader mod (Architectury: Forge, NeoForge,
 * Fabric, Quilt), the actual registration of the structure pool element type
 * has to happen per-platform — Forge and NeoForge use a {@code DeferredRegister}
 * against {@link net.minecraft.core.registries.Registries#STRUCTURE_POOL_ELEMENT},
 * while Fabric and Quilt register directly via
 * {@code Registry.register(...)} in their respective entry points.</p>
 *
 * <p>This class is the bridge: each platform's registry glue calls
 * {@link #setTypeSupplier(Supplier)} during mod construction, and the common
 * {@link PetshopCompatStructurePoolElement#getType()} reads it via
 * {@link #PETSHOP_COMPAT_TYPE} at worldgen time.</p>
 *
 * <p>Implementation note: we use a {@code volatile} field rather than a final
 * field set in a static initializer because the platform init may run after
 * this class is loaded (Architectury's common module is loaded before the
 * platform modules). The supplier chain ensures the lookup is deferred until
 * the first call, by which point registration has completed.</p>
 */
public final class PetshopCompatRegistries {

    private PetshopCompatRegistries() {
    }

    private static volatile Supplier<StructurePoolElementType<?>> typeSupplier;

    /**
     * Called by each platform's mod entry point after registering the type.
     */
    public static void setTypeSupplier(Supplier<StructurePoolElementType<?>> supplier) {
        typeSupplier = supplier;
    }

    /**
     * Supplier used by {@link PetshopCompatStructurePoolElement#getType()}.
     *
     * <p>Wraps the volatile lookup so callers can use it as a regular
     * {@code Supplier<StructurePoolElementType<?>>}.</p>
     */
    public static final Supplier<StructurePoolElementType<?>> PETSHOP_COMPAT_TYPE = () -> {
        Supplier<StructurePoolElementType<?>> supplier = typeSupplier;
        if (supplier == null) {
            throw new IllegalStateException(
                    "PetshopCompatRegistries not initialized — platform mod entry point didn't run. " +
                    "This should never happen during normal worldgen; if it does, the ctov:petshop_compat " +
                    "element type was referenced before the platform registry glue executed.");
        }
        return supplier.get();
    };
}
