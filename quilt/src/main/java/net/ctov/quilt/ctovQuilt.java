package net.ctov.quilt;

import net.ctov.ctov;
import net.ctov.quilt.petshop.PetshopCompatQuiltRegistry;
import org.quiltmc.loader.api.ModContainer;
import org.quiltmc.qsl.base.api.entrypoint.ModInitializer;

public class ctovQuilt implements ModInitializer {
    @Override
    public void onInitialize(ModContainer mod) {
        // Register the custom petshop_compat StructurePoolElementType. Same
        // rationale as the Fabric module: DI/Rats/SimplyCats aren't loaded
        // on Quilt, but we register the type defensively so the modified
        // Lithostitched modifier JSONs parse cleanly if ever validated.
        PetshopCompatQuiltRegistry.init();
        ctov.init();
    }
}
