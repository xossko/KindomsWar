package com.vladisss.kingdomswar.kingdom;

import com.vladisss.kingdomswar.entity.GuardEntity;
import com.vladisss.kingdomswar.entity.KnightEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * ✅ Система мести за убитых воинов:
 * - Преследует убийцу до границы территории
 * - Запоминает игроков-убийц на 10 минут
 * - Отправляет отряд 3-5 воинов при повторном входе
 */
public class RevengeSystem {
    private static final Logger LOGGER = LoggerFactory.getLogger("KingdomsWar");

    // UUID игрока -> время когда его забудут (в game time)
    private final Map<UUID, Long> rememberedPlayers = new HashMap<>();
    private static final long MEMORY_DURATION = 12000; // 10 минут в тиках (600 сек * 20 тиков)

    private final KingdomTerritory kingdom;

    public RevengeSystem(KingdomTerritory kingdom) {
        this.kingdom = kingdom;
    }

    /**
     * ✅ Вызывается когда воин умирает
     */
    public void onWarriorKilled(ServerLevel level, Mob killedWarrior, UUID killerUUID) {
        if (killerUUID == null) return;

        Player killer = level.getServer().getPlayerList().getPlayer(killerUUID);
        if (killer == null) return;

        // ✅ Запоминаем игрока на 10 минут
        long currentTime = level.getGameTime();
        long forgetTime = currentTime + MEMORY_DURATION;
        rememberedPlayers.put(killerUUID, forgetTime);

        LOGGER.warn("[Revenge] {} убил воина {}! Запомнен до {}",
                killer.getName().getString(), kingdom.getName(), forgetTime);

        // ✅ Поднимаем всех союзников на месть
        alertAlliesForRevenge(level, killedWarrior.blockPosition(), killer);
    }

    /**
     * ✅ Поднимает всех воинов на месть
     */
    private void alertAlliesForRevenge(ServerLevel level, BlockPos deathPos, Player killer) {
        BlockPos center = kingdom.getCastleCenter();
        int radius = kingdom.getRadius();

        AABB searchBox = new AABB(
                center.offset(-radius, -50, -radius),
                center.offset(radius, 100, radius)
        );

        List<Mob> allWarriors = level.getEntitiesOfClass(Mob.class, searchBox, mob ->
                (mob instanceof GuardEntity || mob instanceof KnightEntity) && mob.isAlive()
        );

        int alerted = 0;
        for (Mob warrior : allWarriors) {
            // Устанавливаем цель на убийцу
            warrior.setTarget(killer);
            alerted++;
        }

        LOGGER.error("[Revenge] {} воинов {} отправлены мстить за товарища!",
                alerted, kingdom.getName());
    }

    /**
     * ✅ Проверяет вошел ли запомненный игрок на территорию
     */
    public void checkForRememberedPlayers(ServerLevel level) {
        long currentTime = level.getGameTime();

        // Удаляем забытых игроков
        rememberedPlayers.entrySet().removeIf(entry -> {
            if (entry.getValue() <= currentTime) {
                LOGGER.info("[Revenge] {} забыт игрок с UUID {}",
                        kingdom.getName(), entry.getKey());
                return true;
            }
            return false;
        });

        // Проверяем запомненных игроков
        for (UUID playerUUID : new HashSet<>(rememberedPlayers.keySet())) {
            Player player = level.getServer().getPlayerList().getPlayer(playerUUID);
            if (player == null) continue;

            // Проверяем вошел ли на территорию
            if (kingdom.isInTerritory(player.blockPosition())) {
                LOGGER.error("[Revenge] {} ВРАГ {} ВЕРНУЛСЯ! Отправляем отряд!",
                        kingdom.getName(), player.getName().getString());

                // ✅ Отправляем отряд мести 3-5 воинов
                sendRevengeSquad(level, player);
            }
        }
    }

    /**
     * ✅ Отправляет отряд 3-5 ближайших воинов
     */
    private void sendRevengeSquad(ServerLevel level, Player target) {
        BlockPos center = kingdom.getCastleCenter();
        int radius = kingdom.getRadius();

        AABB searchBox = new AABB(
                center.offset(-radius, -50, -radius),
                center.offset(radius, 100, radius)
        );

        List<Mob> allWarriors = level.getEntitiesOfClass(Mob.class, searchBox, mob ->
                (mob instanceof GuardEntity || mob instanceof KnightEntity) &&
                        mob.isAlive() &&
                        mob.getTarget() == null // Только свободные воины
        );

        if (allWarriors.isEmpty()) {
            LOGGER.warn("[Revenge] Нет свободных воинов для отряда мести!");
            return;
        }

        // Сортируем по расстоянию до игрока
        allWarriors.sort(Comparator.comparingDouble(warrior ->
                warrior.distanceToSqr(target)
        ));

        // Берем 3-5 ближайших
        int squadSize = Math.min(5, Math.max(3, allWarriors.size()));
        for (int i = 0; i < squadSize; i++) {
            Mob warrior = allWarriors.get(i);
            warrior.setTarget(target);
        }

        LOGGER.error("[Revenge] Отряд мести из {} воинов отправлен за {}!",
                squadSize, target.getName().getString());
    }

    /**
     * ✅ Проверяет не вышел ли воин за границу территории при преследовании
     */
    public void checkWarriorBounds(Mob warrior) {
        BlockPos warriorPos = warrior.blockPosition();

        // Если вышел за границу И преследует цель
        if (!kingdom.isInTerritory(warriorPos) && warrior.getTarget() != null) {
            LOGGER.info("[Revenge] Воин #{} достиг границы, возвращается", warrior.getId());

            // Сбрасываем цель
            warrior.setTarget(null);

            // Возвращаемся к замку
            BlockPos castleCenter = kingdom.getCastleCenter();
            warrior.getNavigation().moveTo(castleCenter.getX(), castleCenter.getY(), castleCenter.getZ(), 1.0D);
        }
    }

    public boolean isPlayerRemembered(UUID playerUUID) {
        return rememberedPlayers.containsKey(playerUUID);
    }
}