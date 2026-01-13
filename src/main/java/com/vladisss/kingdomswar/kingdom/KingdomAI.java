package com.vladisss.kingdomswar.kingdom;

import com.vladisss.kingdomswar.entity.GuardEntity;
import com.vladisss.kingdomswar.entity.KnightEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * ОПТИМИЗИРОВАННАЯ СИСТЕМА AI КОРОЛЕВСТВА
 * 
 * Приоритеты:
 * 1. ЗАЩИТА КОРОЛЯ (главная задача)
 * 2. РАСШИРЕНИЕ ТЕРРИТОРИИ (пассивный доход)
 * 3. АДАПТИВНАЯ ОБОРОНА (все войска при угрозе)
 */
public class KingdomAI {
    private static final Logger LOGGER = LoggerFactory.getLogger("KingdomsWar");
    
    // ==================== КОНСТАНТЫ ====================
    private static final int CASTLE_DETECTION_RADIUS = 16;  // Радиус видимости замка
    private static final int CRITICAL_THREAT_RADIUS = 32;   // Радиус критической угрозы
    private static final int GUARD_MAX_DISTANCE = 5;        // Стражники не убегают дальше 5 блоков от замка
    private static final int KNIGHT_MAX_DISTANCE = 5;       // Рыцари не уходят дальше 5 блоков от границы территории
    
    private static final int MAX_ATTACKERS_PER_TARGET = 3;  // Максимум атакующих на одну цель
    private static final int THREAT_CHECK_INTERVAL = 40;   // Проверка угроз каждые 5 секунд
    
    // ==================== СОСТОЯНИЕ ====================
    public enum ThreatLevel {
        NONE,           // Нет угроз - можно расширяться
        LOW,            // Низкая угроза - патруль
        MEDIUM,         // Средняя угроза - усиленная оборона
        CRITICAL        // Критическая угроза - ВСЕ ВОЙСКА НА ЗАЩИТУ!
    }
    
    private ThreatLevel currentThreatLevel = ThreatLevel.NONE;
    private long lastThreatCheck = 0;
    private long lastOptimization = 0;
    
    // Карта атакующих: цель -> список атакующих
    private Map<UUID, Set<UUID>> targetAssignments = new HashMap<>();
    
    // ==================== ГЛАВНЫЙ ЦИКЛ AI ====================
    
    /**
     * Главный метод AI - вызывается каждый тик
     */
    public void evaluateSituation(ServerLevel level, KingdomTerritory kingdom) {
        long currentTime = level.getGameTime();
        
        // 1. Обновляем оценку угрозы каждые 5 секунд
        if (currentTime - lastThreatCheck >= THREAT_CHECK_INTERVAL) {
            updateThreatLevel(level, kingdom);
            lastThreatCheck = currentTime;
        }
        
        // 2. Управляем войсками в зависимости от угрозы
        manageTroops(level, kingdom);
        
        // 3. Оптимизируем распределение войск каждые 10 секунд
        if (currentTime - lastOptimization >= 40) {
            optimizeTroopPositions(level, kingdom);
            lastOptimization = currentTime;
        }
    }
    
    // ==================== СИСТЕМА ОБНАРУЖЕНИЯ УГРОЗ ====================
    
    /**
     * Обновление уровня угрозы для королевства
     */
    private void updateThreatLevel(ServerLevel level, KingdomTerritory kingdom) {
        BlockPos castleCenter = kingdom.getCastleCenter();
        int territoryRadius = kingdom.getRadius();
        
        // Ищем всех потенциальных врагов в зоне видимости замка (16 блоков)
        List<LivingEntity> nearCastleThreats = findThreatsInRadius(level, castleCenter, CASTLE_DETECTION_RADIUS);
        
        // Ищем врагов в критической зоне (32 блока)
        List<LivingEntity> criticalThreats = findThreatsInRadius(level, castleCenter, CRITICAL_THREAT_RADIUS);
        
        // Ищем врагов на территории
        List<LivingEntity> territoryThreats = findThreatsInRadius(level, castleCenter, territoryRadius);
        
        // Определяем уровень угрозы
        ThreatLevel oldLevel = currentThreatLevel;
        
        if (!nearCastleThreats.isEmpty()) {
            // КРИТИЧЕСКАЯ УГРОЗА: враги у самого замка!
            currentThreatLevel = ThreatLevel.CRITICAL;
        } else if (!criticalThreats.isEmpty()) {
            // СРЕДНЯЯ УГРОЗА: враги близко к замку
            currentThreatLevel = ThreatLevel.MEDIUM;
        } else if (!territoryThreats.isEmpty()) {
            // НИЗКАЯ УГРОЗА: враги на территории
            currentThreatLevel = ThreatLevel.LOW;
        } else {
            // НЕТ УГРОЗ
            currentThreatLevel = ThreatLevel.NONE;
        }
        
        // Логируем изменение уровня угрозы
        if (oldLevel != currentThreatLevel) {
            LOGGER.warn("[AI] {} - Уровень угрозы: {} -> {} (Врагов: у замка={}, критич={}, террит={}) ",
                kingdom.getName(), oldLevel, currentThreatLevel,
                nearCastleThreats.size(), criticalThreats.size(), territoryThreats.size());
        }
    }
    
    /**
     * Поиск угроз в указанном радиусе
     */
    private List<LivingEntity> findThreatsInRadius(ServerLevel level, BlockPos center, int radius) {
        AABB searchArea = new AABB(
            center.offset(-radius, -50, -radius),
            center.offset(radius, 100, radius)
        );
        
        List<LivingEntity> threats = new ArrayList<>();
        
        // Ищем монстров
        threats.addAll(level.getEntitiesOfClass(Monster.class, searchArea, 
            monster -> monster.isAlive()));
        
        // TODO: Когда добавите дипломатию, здесь будет проверка враждебности игроков
        // Пока считаем всех игроков нейтральными
        // threats.addAll(level.getEntitiesOfClass(Player.class, searchArea,
        //     player -> kingdom.isHostile(player)));
        
        return threats;
    }
    
    // ==================== УПРАВЛЕНИЕ ВОЙСКАМИ ====================
    
    /**
     * Управление войсками в зависимости от уровня угрозы
     */
    private void manageTroops(ServerLevel level, KingdomTerritory kingdom) {
        BlockPos castleCenter = kingdom.getCastleCenter();
        int territoryRadius = kingdom.getRadius();
        
        // Получаем всех войск
        List<GuardEntity> guards = getGuards(level, castleCenter, territoryRadius);
        List<KnightEntity> knights = getKnights(level, castleCenter, territoryRadius);
        
        switch (currentThreatLevel) {
            case CRITICAL:
                // ВСЕ ВОЙСКА К ЗАМКУ!
                defendCastleAllForces(level, kingdom, guards, knights);
                break;
                
            case MEDIUM:
                // Усиленная оборона - распределяем умно
                smartDefense(level, kingdom, guards, knights);
                break;
                
            case LOW:
                // Патрулирование территории
                patrolTerritory(level, kingdom, guards, knights);
                break;
                
            case NONE:
                // Мирное время - расширение и патруль
                peacefulExpansion(level, kingdom, guards, knights);
                break;
        }
    }
    
    /**
     * КРИТИЧЕСКАЯ ЗАЩИТА: Все войска защищают замок
     */
    private void defendCastleAllForces(ServerLevel level, KingdomTerritory kingdom, 
                                       List<GuardEntity> guards, List<KnightEntity> knights) {
        BlockPos castleCenter = kingdom.getCastleCenter();
        
        // Находим все угрозы в критической зоне
        List<LivingEntity> threats = findThreatsInRadius(level, castleCenter, CRITICAL_THREAT_RADIUS);
        
        if (threats.isEmpty()) return;
        
        LOGGER.info("[AI] {} - КРИТИЧЕСКАЯ ЗАЩИТА! Все {} войск атакуют {} врагов",
            kingdom.getName(), guards.size() + knights.size(), threats.size());
        
        // Приоритетные цели - ближайшие к замку
        threats.sort(Comparator.comparingDouble(e -> e.distanceToSqr(castleCenter.getX(), castleCenter.getY(), castleCenter.getZ())));
        
        // Отправляем всех на ближайшие цели
        int targetIndex = 0;
        for (GuardEntity guard : guards) {
            LivingEntity target = threats.get(targetIndex % threats.size());
            guard.setTarget(target);
            targetIndex++;
        }
        
        for (KnightEntity knight : knights) {
            LivingEntity target = threats.get(targetIndex % threats.size());
            knight.setTarget(target);
            targetIndex++;
        }
    }
    
    /**
     * УМНАЯ ЗАЩИТА: Распределяем войска группами по 2-3 человека
     */
    private void smartDefense(ServerLevel level, KingdomTerritory kingdom,
                              List<GuardEntity> guards, List<KnightEntity> knights) {
        BlockPos castleCenter = kingdom.getCastleCenter();
        int territoryRadius = kingdom.getRadius();
        
        // Находим все угрозы на территории
        List<LivingEntity> threats = findThreatsInRadius(level, castleCenter, territoryRadius);
        
        if (threats.isEmpty()) {
            // Если нет угроз, возвращаемся к патрулю
            patrolTerritory(level, kingdom, guards, knights);
            return;
        }
        
        // Очищаем старые назначения
        targetAssignments.clear();
        
        // Приоритет: ближайшие к замку враги
        threats.sort(Comparator.comparingDouble(e -> 
            e.distanceToSqr(castleCenter.getX(), castleCenter.getY(), castleCenter.getZ())));
        
        // Распределяем стражников (они ближе к замку)
        assignTroopsToTargets(guards, threats, castleCenter);
        
        // Распределяем рыцарей (они могут уходить дальше)
        assignTroopsToTargets(knights, threats, castleCenter);
    }
    
    /**
     * Умное распределение войск по целям (макс 2-3 на цель)
     */
    private <T extends Mob> void assignTroopsToTargets(
            List<T> troops,
            List<LivingEntity> enemies,
            BlockPos castlePos) {  // ✅ Круглая скобка!

        for (T troop : troops) {
            LivingEntity bestTarget = null;
            double bestScore = Double.MAX_VALUE;

            for (LivingEntity threat : enemies) {
                int currentAttackers = targetAssignments
                        .getOrDefault(threat.getUUID(), new HashSet<>()).size();

                if (currentAttackers >= MAX_ATTACKERS_PER_TARGET) {
                    continue;
                }

                double distanceToTroop = troop.distanceToSqr(threat);
                double distanceToCastle = threat.distanceToSqr(
                        castlePos.getX(),
                        castlePos.getY(),
                        castlePos.getZ()
                );
                double score = distanceToTroop + (distanceToCastle * 0.5);

                if (score < bestScore) {
                    bestScore = score;
                    bestTarget = threat;
                }
            }

            if (bestTarget != null) {
                troop.setTarget(bestTarget);
                targetAssignments.computeIfAbsent(bestTarget.getUUID(),
                        k -> new HashSet<>()).add(troop.getUUID());
            }
        }
    }


    /**
     * ПАТРУЛЬ ТЕРРИТОРИИ: Войска патрулируют свои зоны
     */
    private void patrolTerritory(ServerLevel level, KingdomTerritory kingdom,
                                 List<GuardEntity> guards, List<KnightEntity> knights) {
        // Рыцари патрулируют границы территории (уже управляется через optimizeTroopPositions)
        // Стражники стоят у замка (уже управляется через optimizeTroopPositions)
    }
    
    /**
     * МИРНОЕ РАСШИРЕНИЕ: Фокус на росте территории
     */
    private void peacefulExpansion(ServerLevel level, KingdomTerritory kingdom,
                                   List<GuardEntity> guards, List<KnightEntity> knights) {
        // В мирное время просто патрулируем
        patrolTerritory(level, kingdom, guards, knights);
        
        // Рыцари могут исследовать дальше для расширения
        for (KnightEntity knight : knights) {
            knight.setPatrolRadius(20); // Больший радиус патруля
        }
    }
    
    // ==================== ОПТИМИЗАЦИЯ ПОЗИЦИЙ ====================
    
    /**
     * Оптимизация распределения войск
     */
    private void optimizeTroopPositions(ServerLevel level, KingdomTerritory kingdom) {
        BlockPos castleCenter = kingdom.getCastleCenter();
        int territoryRadius = kingdom.getRadius();
        
        List<GuardEntity> guards = getGuards(level, castleCenter, territoryRadius);
        List<KnightEntity> knights = getKnights(level, castleCenter, territoryRadius);
        
        // Стражники - вокруг замка
        distributeGuardsAroundCastle(guards, castleCenter);
        
        // Рыцари - по секторам территории
        distributeKnightsInSectors(knights, castleCenter, territoryRadius);
    }
    
    private void distributeGuardsAroundCastle(List<GuardEntity> guards, BlockPos castleCenter) {
        int guardCount = guards.size();
        if (guardCount == 0) return;
        
        for (int i = 0; i < guardCount; i++) {
            GuardEntity guard = guards.get(i);
            double angle = (2 * Math.PI * i) / guardCount;
            int x = castleCenter.getX() + (int)(Math.cos(angle) * 8);
            int z = castleCenter.getZ() + (int)(Math.sin(angle) * 8);
            BlockPos guardPost = new BlockPos(x, castleCenter.getY(), z);
            
            // Устанавливаем пост стражника
            guard.setGuardPos(guardPost);
        }
    }
    
    private void distributeKnightsInSectors(List<KnightEntity> knights, BlockPos castleCenter, int territoryRadius) {
        int knightCount = knights.size();
        if (knightCount == 0) return;
        
        int sectors = Math.min(knightCount, 8); // Максимум 8 секторов
        
        for (int i = 0; i < knightCount; i++) {
            KnightEntity knight = knights.get(i);
            double angle = (2 * Math.PI * i) / sectors;
            int patrolDistance = territoryRadius - 15; // Патрулируют у границы
            int x = castleCenter.getX() + (int)(Math.cos(angle) * patrolDistance);
            int z = castleCenter.getZ() + (int)(Math.sin(angle) * patrolDistance);
            BlockPos patrolPos = new BlockPos(x, castleCenter.getY(), z);
            
            knight.setPatrolCenter(patrolPos);
            knight.setPatrolRadius(15);
        }
    }
    
    // ==================== НАЙМ ВОЙСК ====================
    
    /**
     * Умный найм войск
     */
    public void smartRecruit(ServerLevel level, KingdomTerritory kingdom) {
        int points = kingdom.getPoints();
        int guards = kingdom.countGuards(level);
        int knights = kingdom.countKnights(level);
        int totalTroops = guards + knights;
        int territoryRadius = kingdom.getRadius();
        
        // Минимальное количество войск для защиты
        int minGuards = 4; // Минимум 4 стражника у замка
        int optimalKnights = Math.max(3, territoryRadius / 30); // 1 рыцарь на 30 блоков радиуса
        
        // КРИТИЧЕСКАЯ СИТУАЦИЯ: нанимаем любой ценой
        if (currentThreatLevel == ThreatLevel.CRITICAL && totalTroops < 5) {
            if (points >= 50) { // Минимальная цена
                kingdom.recruitGuard(level);
                LOGGER.warn("[AI] {} - ЭКСТРЕННЫЙ НАЙМ! Критическая угроза!", kingdom.getName());
                return;
            }
        }
        
        // ПРИОРИТЕТ 1: Минимальная защита замка
        if (guards < minGuards && points >= 100) {
            kingdom.recruitGuard(level);
            LOGGER.info("[AI] {} - нанимает стражника для защиты замка ({}/{})", 
                kingdom.getName(), guards + 1, minGuards);
            return;
        }
        
        // ПРИОРИТЕТ 2: Рыцари для патруля территории
        if (knights < optimalKnights && points >= 200) {
            kingdom.recruitKnight(level);
            LOGGER.info("[AI] {} - нанимает рыцаря для территории ({}/{})", 
                kingdom.getName(), knights + 1, optimalKnights);
            return;
        }
        
        // ПРИОРИТЕТ 3: Дополнительные войска при достатке очков
        if (points >= 500) {
            // Баланс 50/50 между стражниками и рыцарями
            if (guards < knights && points >= 100) {
                kingdom.recruitGuard(level);
            } else if (points >= 200) {
                kingdom.recruitKnight(level);
            }
        }
    }
    
    // ==================== ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ ====================
    
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
    
    /**
     * Проверка, должно ли королевство расширяться
     */
    public boolean shouldExpand(KingdomTerritory kingdom, ServerLevel level) {
        // Расширяемся только в мирное время и при достатке ресурсов
        return currentThreatLevel == ThreatLevel.NONE 
            && kingdom.getPoints() > 300
            && kingdom.countKnights(level) >= 3; // Минимум 3 рыцаря для патруля
    }
    
    // ==================== ГЕТТЕРЫ ====================
    
    public ThreatLevel getCurrentThreatLevel() {
        return currentThreatLevel;
    }
    
    // Для совместимости со старым кодом
    public void optimizeKnightDistribution(ServerLevel level, KingdomTerritory kingdom) {
        optimizeTroopPositions(level, kingdom);
    }
}
