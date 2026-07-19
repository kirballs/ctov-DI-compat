package net.ctov.fabric.petshop;

import net.ctov.petshop.PetshopCompatRegistries;
import net.ctov.petshop.PetshopCompatStructurePoolElement;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElementType;

/**
 * Fabric-side registration of the {@code ctov:petshop_compat} structure pool
 * element type.
 *
 * <p>Fabric doesn't have a {@code DeferredRegister} equivalent, so we register
 * the type directly via {@link BuiltInRegistries#register} during the mod's
 * {@code onInitialize} callback. The {@link Registries#STRUCTURE_POOL_ELEMENT}
 * registry is writable at that point because Fabric's registry freeze happens
 * after all mod init callbacks complete.</p>
 *
 * <p>If the registration fails (e.g. because the registry is already frozen —
 * shouldn't happen on Fabric, but defensive), we log a warning and leave the
 * supplier unset. The downstream effect is that the modified Lithostitched
 * modifier JSONs which reference {@code ctov:petshop_compat} won't resolve,
 * but because those modifiers are gated on {@code mod_loaded:
 * domesticationinnovation} (Forge-only mod), they'd never load on Fabric
 * anyway.</p>
 */
public final class PetshopCompatFabricRegistry {

    private PetshopCompatFabricRegistry() {
    }

    public static void init() {
        try {
            ResourceLocation id = new ResourceLocation("ctov", "petshop_compat");
            StructurePoolElementType<PetshopCompatStructurePoolElement> type =
                    () -> PetshopCompatStructurePoolElement.CODEC;
            StructurePoolElementType<?> registered = Registry.register(
                    BuiltInRegistries.STRUCTURE_POOL_ELEMENT,
                    id,
                    type);
            PetshopCompatRegistries.setTypeSupplier(() -> registered);
        } catch (Throwable t) {
            // Defensive: log and move on. The modifier JSONs referencing
            // ctov:petshop_compat are gated on mod_loaded:domesticationinnovation
            // which is Forge-only, so on Fabric this type is never actually
            // used — registering it is purely defensive so the modifier JSON
            // parses cleanly if Lithostitched ever tries.
            System.err.println("[CTOV] Failed to register ctov:petshop_compat StructurePoolElementType on Fabric: " + t);
        }
    }
}
