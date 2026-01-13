package com.vladisss.kingdomswar.registry;

import com.vladisss.kingdomswar.KingdomsWarMod;
import com.vladisss.kingdomswar.block.ModBlocks;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, KingdomsWarMod.MODID);

    public static final RegistryObject<Item> GUARD_SPAWN_EGG = ITEMS.register("guard_spawn_egg",
            () -> new ForgeSpawnEggItem(ModEntities.GUARD, 0xFFD700, 0x8B4513,
                    new Item.Properties()));

    public static final RegistryObject<Item> KNIGHT_SPAWN_EGG = ITEMS.register("knight_spawn_egg",
            () -> new ForgeSpawnEggItem(ModEntities.KNIGHT, 0xC0C0C0, 0x4A4A4A,
                    new Item.Properties()));


    public static void register(IEventBus bus) {
        ITEMS.register(bus);
    }
}
