package com.vladisss.kingdomswar.kingdom;

import com.vladisss.kingdomswar.entity.GuardEntity;
import com.vladisss.kingdomswar.entity.KnightEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * УМНОЕ СКАНИРОВАНИЕ ТЕРРИТОРИИ
 *
 * Функции:
 * - Сканирует всю территорию на угрозы
 * - Распределяет рыцарей по секторам
 * - Направляет ближайших воинов к угрозам
 */
public class TerritoryScanner {
    private static final Logger LOGGER = LoggerFactory.getLogger("KingdomsWar");

    // Сектора территории (8 направлений)
    private static final int SECTOR_COUNT = 8;

    // Карта: какой рыцарь патрулирует какой сектор
    private final Map<UUID, Integer> knightSectorAssignments = new HashMap<>();

    /**
     * Сканировать всю территорию и распределить войска
     */
    public void scanAndAssignTroops(ServerLevel level, KingdomTerritory kingdom) {
        BlockPos center = kingdom.getCastleCenter();
        int radius = kingdom.getRadius();

        // 1. Найти все угрозы на территории
        List<LivingEntity> threats = findAllThreats(level, center, radius);

        if (threats.isEmpty()) {
            // Нет угроз - рыцари патрулируют сектора
            assignKnightsToSectors(level, center, radius);
            return;
        }

        // 2. Распределить ближайших воинов к угрозам
        List<GuardEntity> guards = getGuards(level, center, radius);
        List<KnightEntity> knights = getKnights(level, center, radius);

        assignTroopsToThreats(guards, knights, threats, center, radius);
    }

    /**
     * Найти все угрозы НА ПОВЕРХНОСТИ территории
     */
    private List<LivingEntity> findAllThreats(ServerLevel level, BlockPos center, int radius) {
        // ✅ Сканируем ТОЛЬКО на уровне поверхности ±10 блоков
        int centerY = center.getY();

        AABB searchArea = new AABB(
                center.offset(-radius, -10, -radius),  // Только ±10 блоков по вертикали
                center.offset(radius, 10, radius)
        );

        List<LivingEntity> threats = new ArrayList<>();

        // 1. Монстры (зомби, скелеты и т.д.)
        List<Monster> monsters = level.getEntitiesOfClass(Monster.class, searchArea);
        for (Monster monster : monsters) {
            if (isOnSurface(monster)) {  // ✅ ПРОВЕРКА ПОВЕРХНОСТИ
                threats.add(monster);
            }
        }

        // 2. Враждебные мобы из модов (некроманты, культисты и т.д.)
        List<Mob> mobs = level.getEntitiesOfClass(Mob.class, searchArea);
        for (Mob mob : mobs) {
            // Пропускаем своих воинов
            if (mob instanceof GuardEntity || mob instanceof KnightEntity) {
                continue;
            }

            // Пропускаем мирных мобов
            if (mob.getType().getCategory() == MobCategory.CREATURE ||
                    mob.getType().getCategory() == MobCategory.AMBIENT ||
                    mob.getType().getCategory() == MobCategory.WATER_CREATURE) {
                continue;
            }

            // ✅ ПРОВЕРКА ПОВЕРХНОСТИ
            if (isOnSurface(mob) && !threats.contains(mob)) {
                threats.add(mob);
            }
        }

        return threats;
    }

    /**
     * ✅ НОВЫЙ МЕТОД: Проверка что моб на поверхности
     */
    private boolean isOnSurface(LivingEntity entity) {
        if (!entity.isAlive()) {
            return false;
        }

        BlockPos entityPos = entity.blockPosition();

        // 1. Проверяем высоту поверхности
        int surfaceY = entity.level().getHeight(
                net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE,
                entityPos.getX(),
                entityPos.getZ()
        );

        // Моб должен быть НЕ НИЖЕ поверхности минус 3 блока
        if (entityPos.getY() < surfaceY - 3) {
            return false;
        }

        // 2. Проверяем что под мобом есть твердый блок (не летает)
        BlockPos below = entityPos.below();
        boolean hasGround = !entity.level().isEmptyBlock(below) ||
                !entity.level().isEmptyBlock(below.below());

        if (!hasGround && !entity.onGround()) {
            return false; // Летает в воздухе - игнорируем
        }

        // 3. Проверяем что над мобом есть небо (не в пещере)
        boolean canSeeSky = entity.level().canSeeSky(entityPos);
        if (!canSeeSky) {
            // Дополнительная проверка: если не видит небо, то должен быть близко к поверхности
            if (entityPos.getY() < surfaceY - 5) {
                return false; // Глубоко под землей
            }
        }

        return true;
    }



    /**
     /**
     * Распределить воинов к угрозам
     */
    private void assignTroopsToThreats(
            List<GuardEntity> guards,
            List<KnightEntity> knights,
            List<LivingEntity> threats,
            BlockPos castleCenter,
            int territoryRadius
    ) {
        // Сортируем угрозы по близости к замку
        threats.sort(Comparator.comparingDouble(threat ->
                threat.distanceToSqr(castleCenter.getX(), castleCenter.getY(), castleCenter.getZ())
        ));

        Set<UUID> assignedTroops = new HashSet<>();

        for (LivingEntity threat : threats) {
            // ✅ ЗАМЕНИЛИ isValidThreat на isOnSurface
            if (!isOnSurface(threat)) {
                continue;
            }

            // Если угроза близко к замку (<20 блоков) - отправляем стражников
            double distToCastle = Math.sqrt(threat.distanceToSqr(
                    castleCenter.getX(), castleCenter.getY(), castleCenter.getZ()
            ));

            if (distToCastle < 20) {
                // Найти ближайшего свободного стражника
                GuardEntity nearestGuard = findNearestFreeTroop(guards, threat, assignedTroops);
                if (nearestGuard != null) {
                    nearestGuard.setTarget(threat);
                    assignedTroops.add(nearestGuard.getUUID());
                    LOGGER.debug("[Scanner] Стражник {} -> {} ({}м от замка)",
                            nearestGuard.getId(), threat.getName().getString(), (int)distToCastle);
                }
            }

            // Рыцари - для всех угроз на территории
            KnightEntity nearestKnight = findNearestFreeTroop(knights, threat, assignedTroops);
            if (nearestKnight != null) {
                nearestKnight.setTarget(threat);
                assignedTroops.add(nearestKnight.getUUID());
                LOGGER.debug("[Scanner] Рыцарь {} -> {}",
                        nearestKnight.getId(), threat.getName().getString());
            }
        }
    }



    /**
     * Найти ближайшего свободного воина
     */
    private <T extends Mob> T findNearestFreeTroop(
            List<T> troops,
            LivingEntity threat,
            Set<UUID> assignedTroops
    ) {
        return troops.stream()
                .filter(troop -> !assignedTroops.contains(troop.getUUID()))
                .filter(troop -> troop.getTarget() == null || !troop.getTarget().isAlive())
                .min(Comparator.comparingDouble(troop -> troop.distanceToSqr(threat)))
                .orElse(null);
    }

    /**
     * Распределить рыцарей по секторам для патрулирования
     */
    private void assignKnightsToSectors(ServerLevel level, BlockPos center, int radius) {
        List<KnightEntity> knights = getKnights(level, center, radius);

        // Очистить назначения у рыцарей без цели
        knights.stream()
                .filter(k -> k.getTarget() == null)
                .forEach(k -> k.setTarget(null));

        // Распределить по секторам
        for (int i = 0; i < knights.size(); i++) {
            KnightEntity knight = knights.get(i);

            // Если рыцарь занят боем - не трогаем
            if (knight.getTarget() != null && knight.getTarget().isAlive()) {
                continue;
            }

            int sectorIndex = i % SECTOR_COUNT;
            knightSectorAssignments.put(knight.getUUID(), sectorIndex);

            // Позиция патруля на границе территории
            double angle = (2 * Math.PI / SECTOR_COUNT) * sectorIndex;
            int patrolDist = (int) (radius * 0.7); // 70% от радиуса

            int x = center.getX() + (int) (Math.cos(angle) * patrolDist);
            int z = center.getZ() + (int) (Math.sin(angle) * patrolDist);
            BlockPos patrolPos = new BlockPos(x, center.getY(), z);

            knight.setPatrolCenter(patrolPos);
            knight.setPatrolRadius(20); // Радиус патруля 20 блоков
        }
    }

    private List<GuardEntity> getGuards(ServerLevel level, BlockPos center, int radius) {
        return level.getEntitiesOfClass(GuardEntity.class,
                new AABB(center.offset(-radius, -50, -radius), center.offset(radius, 100, radius)),
                guard -> guard.isAlive()
        );
    }

    private List<KnightEntity> getKnights(ServerLevel level, BlockPos center, int radius) {
        return level.getEntitiesOfClass(KnightEntity.class,
                new AABB(center.offset(-radius, -50, -radius), center.offset(radius, 100, radius)),
                knight -> knight.isAlive()
        );
    }


}
