package com.vladisss.kingdomswar.kingdom;

import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * СИСТЕМА ПАССИВНОГО ДОХОДА ОТ ТЕРРИТОРИИ
 * 
 * Логика: Королевство получает доход от РАЗМЕРА территории
 * Формула: 50 блоков радиуса = 1 очко в минуту
 * 
 * Рыцари НЕ требуются для дохода!
 */
public class TerritoryIncomeSystem {
    private static final Logger LOGGER = LoggerFactory.getLogger("KingdomsWar");

    // ==================== КОНСТАНТЫ ====================
    private static final int BLOCKS_PER_POINT = 50;     // 50 блоков радиуса = 1 очко
    private static final int INCOME_INTERVAL = 1200;    // Доход каждые 60 секунд (1200 тиков)

    // ==================== СОСТОЯНИЕ ====================
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
     * Рассчитывает и начисляет пассивный доход от территории
     */
    private void calculateAndApplyIncome(ServerLevel level, KingdomTerritory kingdom) {
        // Размер территории = радиус
        int territoryRadius = kingdom.getRadius();
        
        // Доход = радиус / 50
        // Например: радиус 100 = 2 очка/мин, радиус 250 = 5 очков/мин
        int income = territoryRadius / BLOCKS_PER_POINT;

        if (income > 0) {
            kingdom.addPoints(income, "доход от территории");
            LOGGER.info("[Income] {} получил {} очков (территория: {} блоков, доход: {}/мин)",
                    kingdom.getName(), income, territoryRadius, income);
        } else {
            LOGGER.debug("[Income] {} - территория слишком мала для дохода (радиус: {})",
                    kingdom.getName(), territoryRadius);
        }
    }

    /**
     * Получить текущий доход в минуту (для отображения)
     */
    public int getIncomePerMinute(KingdomTerritory kingdom) {
        return kingdom.getRadius() / BLOCKS_PER_POINT;
    }

    /**
     * Получить сколько блоков нужно для следующего уровня дохода
     */
    public int getBlocksToNextIncome(KingdomTerritory kingdom) {
        int currentRadius = kingdom.getRadius();
        int remainder = currentRadius % BLOCKS_PER_POINT;
        return BLOCKS_PER_POINT - remainder;
    }

    /**
     * Сбросить счетчик (для загрузки)
     */
    public void resetTimer() {
        this.tickCounter = 0;
    }
}
