package com.vladisss.kingdomswar.kingdom;

import com.vladisss.kingdomswar.entity.KnightEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class TerritoryIncomeSystem {
    private static final Logger LOGGER = LoggerFactory.getLogger("KingdomsWar");

    // Константы
    private static final int KNIGHT_CONTROL_RADIUS = 20; // 1 рыцарь контролирует 20 блоков
    private static final int BLOCKS_PER_POINT = 50; // 50 блоков = 1 очко
    private static final int INCOME_INTERVAL = 1200; // Доход каждые 60 секунд (1200 тиков)

    private int tickCounter = 0;

    /**
     * Вызывается каждый тик из KingdomTerritory.tick()
     */
    public void tick(ServerLevel level, KingdomTerritory kingdom) {
        tickCounter++;

        if (tickCounter >= INCOME_INTERVAL) {
            calculateAndApplyIncome(level, kingdom);
            tickCounter = 0;
        }
    }

    /**
     * Рассчитывает и начисляет пассивный доход
     */
    private void calculateAndApplyIncome(ServerLevel level, KingdomTerritory kingdom) {
        // 1. Находим всех рыцарей королевства
        List<KnightEntity> knights = findKnightsInTerritory(level, kingdom);

        // 2. Рассчитываем контролируемые блоки
        int controlledBlocks = calculateControlledBlocks(knights);

        // 3. Рассчитываем доход
        int income = controlledBlocks / BLOCKS_PER_POINT;

        if (income > 0) {
            kingdom.addPoints(income, "доход от территории");
            LOGGER.info("[Income] {} получил {} очков (рыцарей: {}, блоков: {})",
                    kingdom.getName(), income, knights.size(), controlledBlocks);
        }
    }

    /**
     * Находит всех рыцарей в радиусе королевства
     */
    private List<KnightEntity> findKnightsInTerritory(ServerLevel level, KingdomTerritory kingdom) {
        BlockPos center = kingdom.getCastleCenter();
        int radius = kingdom.getRadius();

        AABB searchArea = new AABB(
                center.getX() - radius, center.getY() - 50, center.getZ() - radius,
                center.getX() + radius, center.getY() + 50, center.getZ() + radius
        );

        return level.getEntitiesOfClass(
                KnightEntity.class,
                searchArea,
                knight -> knight != null && !knight.isDeadOrDying()
        );
    }

    /**
     * Рассчитывает контролируемые блоки (каждый рыцарь контролирует 20 блоков)
     */
    private int calculateControlledBlocks(List<KnightEntity> knights) {
        return knights.size() * KNIGHT_CONTROL_RADIUS;
    }

    /**
     * Получить количество контролируемых блоков (для отображения)
     */
    public int getControlledBlocks(ServerLevel level, KingdomTerritory kingdom) {
        List<KnightEntity> knights = findKnightsInTerritory(level, kingdom);
        return calculateControlledBlocks(knights);
    }

    /**
     * Сбросить счетчик (для загрузки)
     */
    public void resetTimer() {
        this.tickCounter = 0;
    }
}
