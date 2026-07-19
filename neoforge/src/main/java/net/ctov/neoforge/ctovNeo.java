package net.ctov.neoforge;

import net.ctov.ctov;
import net.ctov.neoforge.petshop.PetshopCompatNeoRegistry;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(ctov.MOD_ID)
public class ctovNeo {
    public ctovNeo() {
        // Submit our event bus to let architectury register our content on the right time
            // Initialize logic here
        var modBus = FMLJavaModLoadingContext.get().getModEventBus();
        // Register the custom petshop_compat StructurePoolElementType. This is
        // what makes CTOV's petshop .nbt data markers (petshop_water,
        // petshop_chest, petshop_cage_0..3) actually do something instead of
        // being silently cleared to air by vanilla single_pool_element.
        PetshopCompatNeoRegistry.POOL_ELEMENTS.register(modBus);
        PetshopCompatNeoRegistry.init();
        ctov.init();
    }
}
