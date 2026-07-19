package net.ctov.fabric;

import net.ctov.ctov;
import net.ctov.fabric.petshop.PetshopCompatFabricRegistry;
import net.fabricmc.api.ModInitializer;


public class ctovFabric implements ModInitializer {

    @Override
    public void onInitialize() {
        // Register the custom petshop_compat StructurePoolElementType. On
        // Fabric, DI/Rats/SimplyCats aren't loaded (they're Forge-only), so
        // the Lithostitched modifier JSONs that reference this type never
        // fire — but we register it anyway so the modifier JSONs parse
        // cleanly if Lithostitched ever tries to validate them.
        PetshopCompatFabricRegistry.init();
        ctov.init();
    }
}
