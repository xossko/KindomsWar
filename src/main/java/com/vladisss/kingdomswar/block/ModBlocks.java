package com.vladisss.kingdomswar.block;

import com.vladisss.kingdomswar.KingdomsWarMod;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, KingdomsWarMod.MODID);



    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
    }
}
