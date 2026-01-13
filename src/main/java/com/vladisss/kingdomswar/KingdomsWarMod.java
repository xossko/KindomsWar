package com.vladisss.kingdomswar;

import com.vladisss.kingdomswar.block.ModBlocks;
import com.vladisss.kingdomswar.client.renderer.GuardRenderer;
import com.vladisss.kingdomswar.client.renderer.KnightRenderer;
import com.vladisss.kingdomswar.command.*;
import com.vladisss.kingdomswar.entity.GuardEntity;
import com.vladisss.kingdomswar.entity.KnightEntity;
import com.vladisss.kingdomswar.kingdom.KingdomManager;
import com.vladisss.kingdomswar.kingdom.KingdomTerritory;
import com.vladisss.kingdomswar.registry.ModEntities;
import com.vladisss.kingdomswar.registry.ModItems;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(KingdomsWarMod.MODID)
public class KingdomsWarMod {
    public static final String MODID = "kingdomswar";

    public KingdomsWarMod() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        ModEntities.register(modEventBus);
        ModItems.register(modEventBus);
        ModBlocks.register(modEventBus);

        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::clientSetup);
        modEventBus.addListener(this::entityAttributes);

        MinecraftForge.EVENT_BUS.register(this);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
    }

    // ✅ ВОТ СЮДА: Регистрация атрибутов для обоих энтити
    private void entityAttributes(EntityAttributeCreationEvent event) {
        event.put(ModEntities.GUARD.get(), GuardEntity.createAttributes().build());
        event.put(ModEntities.KNIGHT.get(), KnightEntity.createAttributes().build());
    }

    // ✅ ВОТ СЮДА: Регистрация рендереров для клиента
    private void clientSetup(final FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            EntityRenderers.register(ModEntities.GUARD.get(), GuardRenderer::new);
            EntityRenderers.register(ModEntities.KNIGHT.get(), KnightRenderer::new);
        });
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        for (ServerLevel level : event.getServer().getAllLevels()) {
            KingdomManager.loadKingdoms(level);
        }
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        SpawnGuardCommand.register(event.getDispatcher());
        SpawnCastleCommand.register(event.getDispatcher());
        KingdomStatusCommand.register(event.getDispatcher());
        KingdomExpandCommand.register(event.getDispatcher());
        KingdomAddPointsCommand.register(event.getDispatcher());
    }
    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            for (ServerLevel level : event.getServer().getAllLevels()) {
                KingdomTerritory kingdom = KingdomManager.getKingdom(level);
                if (kingdom != null) {
                    kingdom.tick(level);
                }
            }
        }
    }

}
