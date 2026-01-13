package com.vladisss.kingdomswar.registry;

import com.vladisss.kingdomswar.KingdomsWarMod;
import com.vladisss.kingdomswar.entity.GuardEntity;
import com.vladisss.kingdomswar.entity.KnightEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, KingdomsWarMod.MODID);

    public static final RegistryObject<EntityType<GuardEntity>> GUARD = ENTITIES.register("guard",
            () -> EntityType.Builder.of(GuardEntity::new, MobCategory.CREATURE)
                    .sized(0.6F, 1.95F)
                    .clientTrackingRange(8)
                    .build(null));

    public static final RegistryObject<EntityType<KnightEntity>> KNIGHT = ENTITIES.register("knight",
            () -> EntityType.Builder.of(KnightEntity::new, MobCategory.CREATURE)
                    .sized(0.6F, 1.95F)
                    .clientTrackingRange(8)
                    .build(null));

    public static void register(IEventBus bus) {
        ENTITIES.register(bus);
    }
}
