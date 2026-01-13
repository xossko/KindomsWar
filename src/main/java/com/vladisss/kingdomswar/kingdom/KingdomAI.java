package com.vladisss.kingdomswar.kingdom;

import com.vladisss.kingdomswar.entity.GuardEntity;
import com.vladisss.kingdomswar.entity.KnightEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.phys.AABB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class KingdomAI {
    private static final Logger LOGGER = LoggerFactory.getLogger("KingdomsWar");

    public enum Strategy {
        PEACEFUL,      // Мирное развитие
        AGGRESSIVE,    // Агрессивное расширение
        DEFENSIVE,     // Оборона замка
        EXPANSIONIST   // Захват территории
    }

    private Strategy currentStrategy = Strategy.PEACEFUL;
    private int threatLevel = 0;
    private long lastThreatCheck = 0;
    private long lastOptimization = 0;
    private long lastStrategyChange = 0; // ✅ НОВОЕ: запоминаем когда меняли стратегию

    // ✅ Оценка ситуации + оптимизация рыцарей
    public void evaluateSituation(ServerLevel level, KingdomTerritory kingdom) {
        long currentTime = level.getGameTime();

        // ✅ Проверяем угрозу каждые 10 секунд (было 5)
        if (currentTime - lastThreatCheck >= 200) {
            lastThreatCheck = currentTime;
            updateThreatLevel(level, kingdom);
        }

        // Оптимизируем распределение рыцарей каждые 5 минут
        if (currentTime - lastOptimization >= 6000) {
            optimizeKnightDistribution(level, kingdom);
            lastOptimization = currentTime;
        }
    }

    // ✅ УЛУЧШЕННАЯ логика смены стратегии с гистерезисом
    private void updateThreatLevel(ServerLevel level, KingdomTerritory kingdom) {
        int nearbyMonsters = countNearbyMonsters(level, kingdom);
        int troops = kingdom.countLivingTroops(level);
        int required = kingdom.getRequiredTroopsCount();
        int points = kingdom.getPoints();
        int knights = kingdom.countKnights(level);

        // Расчет уровня угрозы (0-100)
        threatLevel = (nearbyMonsters * 3);
        if (troops < required) {
            threatLevel += (required - troops) * 2;
        }
        threatLevel = Math.min(100, threatLevel);

        Strategy oldStrategy = currentStrategy;
        long currentTime = level.getGameTime();

        // ✅ ГИСТЕРЕЗИС: не меняем стратегию чаще чем раз в 1 минуту (1200 тиков)
        boolean canChangeStrategy = (currentTime - lastStrategyChange) > 1200;

        // ✅ НОВАЯ ЛОГИКА: Приоритет на экономический рост
        if (threatLevel > 70) {
            // КРИТИЧЕСКАЯ УГРОЗА → немедленно защищаемся
            currentStrategy = Strategy.DEFENSIVE;
            canChangeStrategy = true; // Игнорируем гистерезис при угрозе
        } else if (threatLevel > 40) {
            // Средняя угроза → агрессивная оборона
            if (canChangeStrategy) {
                currentStrategy = Strategy.AGGRESSIVE;
            }
        } else if (threatLevel < 20) {
            // ✅ МИРНОЕ ВРЕМЯ → развиваемся экономически!
            if (points > 300 && knights >= required * 0.5) {
                // Есть ресурсы И хоть какая-то оборона → ЭКСПАНСИЯ!
                if (canChangeStrategy) {
                    currentStrategy = Strategy.EXPANSIONIST;
                }
            } else {
                // Мало ресурсов → мирное развитие
                if (canChangeStrategy) {
                    currentStrategy = Strategy.PEACEFUL;
                }
            }
        }

        if (oldStrategy != currentStrategy) {
            lastStrategyChange = currentTime;
            LOGGER.warn("[AI] {} изменила стратегию: {} -> {} (Угроза: {}, Войск: {}/{}, Очков: {})",
                    kingdom.getName(), oldStrategy, currentStrategy, threatLevel, troops, required, points);
        }
    }

    private int countNearbyMonsters(ServerLevel level, KingdomTerritory kingdom) {
        BlockPos center = kingdom.getCastleCenter();
        int radius = kingdom.getRadius();
        List<Monster> monsters = level.getEntitiesOfClass(Monster.class,
                new AABB(
                        center.offset(-radius, -50, -radius),
                        center.offset(radius, 100, radius)
                ),
                monster -> monster.isAlive()
        );
        return monsters.size();
    }

    // ✅ КАРДИНАЛЬНО ПЕРЕРАБОТАННАЯ ЛОГИКА НАЙМА С ЗАЩИТОЙ РЕЗЕРВА
    public void smartRecruit(ServerLevel level, KingdomTerritory kingdom) {
        int troops = kingdom.countLivingTroops(level);
        int required = kingdom.getRequiredTroopsCount();
        int points = kingdom.getPoints();
        int knights = kingdom.countKnights(level);
        int guards = kingdom.countGuards(level);

        // ========================================
        // 1. КРИТИЧЕСКАЯ ЭКСТРЕННАЯ ОБОРОНА (используем резерв!)
        // ========================================
        if (troops < required * 0.3 && points >= 70) {
            // Королевство в ОПАСНОСТИ! Тратим даже последние очки!
            LOGGER.error("[AI] ⚠️ {} КРИТИЧЕСКАЯ СИТУАЦИЯ! Войск: {}/{} - покупаем ЛЮБОЙ ценой!",
                    kingdom.getName(), troops, required);

            // Покупаем рыцарей (сильнее стражников)
            if (points >= 70) {
                kingdom.recruitKnight(level);
                LOGGER.error("[AI] {} экстренный найм РЫЦАРЯ из резерва!", kingdom.getName());
                return;
            }
        }

        // ========================================
        // 2. ЭКСТРЕННАЯ ОБОРОНА (нормальный режим)
        // ========================================
        if (troops < required * 0.5 && points >= 100) {
            LOGGER.error("[AI] {} ЭКСТРЕННЫЙ НАЙМ! Войск: {}/{}",
                    kingdom.getName(), troops, required);

            // При экстренной обороне покупаем стражников (дешевле)
            if (points >= 100) {
                kingdom.recruitGuard(level);
                return;
            }
        }

        // ========================================
        // 3. БАЗОВАЯ ОБОРОНА (до минимума)
        // ========================================
        if (troops < required) {
            if (currentStrategy == Strategy.DEFENSIVE) {
                // При обороне нужно больше стражников
                if (guards < required * 0.6 && points >= 150) {
                    kingdom.recruitGuard(level);
                    return;
                }
            }

            // Нанимаем рыцарей если есть очки
            if (points >= 250) {
                kingdom.recruitKnight(level);
                LOGGER.info("[AI] {} нанимает рыцаря для базовой обороны", kingdom.getName());
                return;
            } else if (points >= 150) {
                kingdom.recruitGuard(level);
                return;
            }
        }

        // ========================================
        // 4. ✅ ЭКОНОМИЧЕСКИЙ РОСТ (КЛЮЧЕВОЕ ИЗМЕНЕНИЕ!)
        // ========================================
        // Если есть базовая оборона → ИНВЕСТИРУЕМ В РЫЦАРЕЙ!
        if (troops >= required) {
            if (currentStrategy == Strategy.EXPANSIONIST || currentStrategy == Strategy.PEACEFUL) {
                // ✅ НОВАЯ ЛОГИКА: Покупаем рыцарей пока есть деньги!
                int optimalKnights = calculateOptimalKnightCount(kingdom, level);

                // ЗАЩИТА РЕЗЕРВА: оставляем минимум 100 очков на экстренный случай
                int reserveFund = 100;

                if (knights < optimalKnights && points >= 270) { // 200 (рыцарь) + 70 (резерв)
                    kingdom.recruitKnight(level);
                    LOGGER.info("[AI] {} ИНВЕСТИРУЕТ в рыцаря для территории ({}/{}, резерв: {})",
                            kingdom.getName(), knights, optimalKnights, points - 70);
                    return;
                }

                // Если рыцарей достаточно → создаем дополнительный резерв
                if (points > 500 && knights < optimalKnights * 1.2) {
                    kingdom.recruitKnight(level);
                    LOGGER.info("[AI] {} создает РЕЗЕРВ рыцарей (очки: {})", kingdom.getName(), points);
                    return;
                }
            }

            // При агрессивной стратегии баланс 60/40
            if (currentStrategy == Strategy.AGGRESSIVE) {
                if (knights < troops * 0.6 && points >= 270) {
                    kingdom.recruitKnight(level);
                    return;
                } else if (points >= 150 && guards < troops * 0.4) {
                    kingdom.recruitGuard(level);
                    return;
                }
            }
        }
    }


    // ✅ НОВЫЙ МЕТОД: Расчет оптимального количества рыцарей
    private int calculateOptimalKnightCount(KingdomTerritory kingdom, ServerLevel level) {
        int radius = kingdom.getRadius();

        // Формула: 1 рыцарь на 20 блоков радиуса (для покрытия территории)
        int baseKnights = radius / 20;

        // Минимум 5 рыцарей, максимум 20
        int optimal = Math.max(5, Math.min(20, baseKnights));

        // Бонус при экспансии: +50%
        if (currentStrategy == Strategy.EXPANSIONIST) {
            optimal = (int)(optimal * 1.5);
        }

        LOGGER.debug("[AI] Оптимальное количество рыцарей для радиуса {}: {}", radius, optimal);
        return optimal;
    }



    // ✅ Распределение рыцарей (без изменений)
    public void optimizeKnightDistribution(ServerLevel level, KingdomTerritory kingdom) {
        BlockPos center = kingdom.getCastleCenter();
        int radius = kingdom.getRadius();
        List<KnightEntity> knights = level.getEntitiesOfClass(
                KnightEntity.class,
                new AABB(
                        center.offset(-radius, -50, -radius),
                        center.offset(radius, 100, radius)
                )
        );

        if (knights.isEmpty()) return;

        int sectors = calculateOptimalSectors(knights.size());
        LOGGER.info("[AI] {} оптимизирует {} рыцарей в {} секторов (стратегия: {})",
                kingdom.getName(), knights.size(), sectors, currentStrategy);

        for (int i = 0; i < knights.size(); i++) {
            KnightEntity knight = knights.get(i);
            double angle = (2 * Math.PI * i) / sectors;
            double patrolDistance = calculatePatrolDistance(radius);
            int offsetX = (int) (Math.cos(angle) * patrolDistance);
            int offsetZ = (int) (Math.sin(angle) * patrolDistance);
            BlockPos patrolPos = center.offset(offsetX, 0, offsetZ);

            knight.setPatrolCenter(patrolPos);
            knight.setPatrolRadius(getPatrolRadiusForStrategy());

            LOGGER.debug("[AI] Рыцарь {} -> сектор {} ({})",
                    knight.getId(), i, patrolPos.toShortString());
        }
    }

    private int calculateOptimalSectors(int knightCount) {
        if (currentStrategy == Strategy.DEFENSIVE) {
            return Math.min(knightCount, 6);
        } else if (currentStrategy == Strategy.EXPANSIONIST) {
            return Math.min(knightCount, 12);
        } else {
            return Math.min(knightCount, 8);
        }
    }

    private double calculatePatrolDistance(int territoryRadius) {
        switch (currentStrategy) {
            case DEFENSIVE:
                return territoryRadius * 0.5;
            case EXPANSIONIST:
            case AGGRESSIVE:
                return territoryRadius * 0.8;
            case PEACEFUL:
            default:
                return territoryRadius * 0.7;
        }
    }

    private int getPatrolRadiusForStrategy() {
        switch (currentStrategy) {
            case DEFENSIVE:
                return 12;
            case EXPANSIONIST:
                return 20;
            default:
                return 15;
        }
    }

    public boolean shouldExpand(KingdomTerritory kingdom, ServerLevel level) {
        int troops = kingdom.countLivingTroops(level);
        int required = kingdom.getRequiredTroopsCount();
        return currentStrategy == Strategy.EXPANSIONIST
                && troops >= required * 1.2
                && threatLevel < 40
                && kingdom.getPoints() > kingdom.getExpansionCost() + 300;
    }

    public Strategy getCurrentStrategy() {
        return currentStrategy;
    }

    public int getThreatLevel() {
        return threatLevel;
    }
}
