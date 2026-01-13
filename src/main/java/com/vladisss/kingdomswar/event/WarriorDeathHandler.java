package com.vladisss.kingdomswar.event;

import com.vladisss.kingdomswar.entity.GuardEntity;
import com.vladisss.kingdomswar.entity.KnightEntity;
import com.vladisss.kingdomswar.kingdom.KingdomManager;
import com.vladisss.kingdomswar.kingdom.KingdomTerritory;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ✅ Обработчик событий для системы мести
 */
@Mod.EventBusSubscriber
public class WarriorDeathHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger("KingdomsWar");

    @SubscribeEvent
    public static void onWarriorDeath(LivingDeathEvent event) {
        LivingEntity victim = event.getEntity();

        // Проверяем что умер воин королевства
        if (!(victim instanceof GuardEntity || victim instanceof KnightEntity)) {
            return;
        }

        if (!(victim.level() instanceof ServerLevel level)) {
            return;
        }

        // Получаем королевство
        KingdomTerritory kingdom = KingdomManager.getKingdom(level);
        if (kingdom == null) {
            return;
        }

        // Получаем убийцу
        if (event.getSource().getEntity() instanceof Player killer) {
            LOGGER.error("[Death] Игрок {} убил воина {}!",
                    killer.getName().getString(), kingdom.getName());

            // ✅ Активируем систему мести
            kingdom.getRevengeSystem().onWarriorKilled(level, (Mob) victim, killer.getUUID());
        }
    }
}