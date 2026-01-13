package com.vladisss.kingdomswar.entity.ai;

import com.vladisss.kingdomswar.entity.GuardEntity;
import com.vladisss.kingdomswar.entity.KnightEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.phys.AABB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

/**
 * ✅ Умная система атаки v2:
 * - ПРИОРИТЕТ на БЛИЖАЙШУЮ цель
 * - Распределение воинов по силе врага (HP)
 * - Помощь союзникам если нет своих целей
 */
public class SmartTargetGoal<T extends LivingEntity> extends NearestAttackableTargetGoal<T> {
    private static final Logger LOGGER = LoggerFactory.getLogger("KingdomsWar");
    private final Mob attacker;

    public SmartTargetGoal(Mob attacker, Class<T> targetClass, int randomInterval,
                           boolean mustSee, boolean mustReach, Predicate<LivingEntity> selector) {
        super(attacker, targetClass, randomInterval, mustSee, mustReach, selector);
        this.attacker = attacker;
    }

    @Override
    public boolean canUse() {
        // ✅ 1. Ищем БЛИЖАЙШУЮ подходящую цель
        LivingEntity closestTarget = findClosestValidTarget();

        if (closestTarget == null) {
            // ✅ 2. Нет своих целей → помогаем союзникам
            closestTarget = findAllyTargetToHelp();
        }

        if (closestTarget == null) {
            return false;
        }

        this.target = (T) closestTarget;
        return true;
    }

    /**
     * ✅ Ищет БЛИЖАЙШУЮ валидную цель с учетом распределения
     */
    private LivingEntity findClosestValidTarget() {
        AABB searchBox = attacker.getBoundingBox().inflate(48.0);

        List<Mob> potentialTargets = attacker.level().getEntitiesOfClass(Mob.class, searchBox, mob -> {
            if (mob == attacker) return false;
            if (mob instanceof GuardEntity || mob instanceof KnightEntity) return false;
            if (!mob.isAlive()) return false;

            // Проверяем фильтр (монстры, агрессивные нейтралы)
            if (mob.getType().getCategory() != net.minecraft.world.entity.MobCategory.MONSTER) {
                if (!(mob instanceof net.minecraft.world.entity.NeutralMob)) {
                    return false;
                }
                if (!((net.minecraft.world.entity.NeutralMob) mob).isAngry()) {
                    return false;
                }
            }

            return true;
        });

        if (potentialTargets.isEmpty()) {
            return null;
        }

        // ✅ Сортируем по ДИСТАНЦИИ (ближайший первый)
        potentialTargets.sort(Comparator.comparingDouble(mob ->
                mob.distanceToSqr(attacker)
        ));

        // ✅ Проверяем каждую цель (от ближайшей к дальней)
        for (Mob target : potentialTargets) {
            if (shouldAttackTarget(target)) {
                return target;
            }
        }

        return null;
    }

    /**
     * ✅ Ищет союзника которому нужна помощь
     */
    private LivingEntity findAllyTargetToHelp() {
        AABB searchBox = attacker.getBoundingBox().inflate(32.0);

        List<Mob> allies = attacker.level().getEntitiesOfClass(Mob.class, searchBox, mob -> {
            if (!(mob instanceof GuardEntity || mob instanceof KnightEntity)) {
                return false;
            }
            if (!mob.isAlive() || mob == attacker) {
                return false;
            }
            // У союзника должна быть цель
            return mob.getTarget() != null && mob.getTarget().isAlive();
        });

        if (allies.isEmpty()) {
            return null;
        }

        // Сортируем союзников по дистанции
        allies.sort(Comparator.comparingDouble(ally -> ally.distanceToSqr(attacker)));

        // Берем цель ближайшего союзника
        for (Mob ally : allies) {
            LivingEntity allyTarget = ally.getTarget();
            if (allyTarget != null && allyTarget.isAlive()) {
                // Проверяем нужна ли помощь
                List<Mob> attackersOnTarget = countAlliesAttackingTarget(allyTarget);
                int requiredWarriors = calculateRequiredWarriors(
                        allyTarget.getHealth(),
                        attacker.getHealth()
                );

                if (attackersOnTarget.size() < requiredWarriors) {
                    LOGGER.debug("[SmartAI] {} помогает союзнику с целью (HP: {})",
                            attacker.getId(), allyTarget.getHealth());
                    return allyTarget;
                }
            }
        }

        return null;
    }

    /**
     * ✅ Проверяет нужно ли атаковать эту цель (или её уже достаточно атакуют)
     */
    private boolean shouldAttackTarget(LivingEntity target) {
        float targetHP = target.getHealth();
        float myHP = attacker.getHealth();

        // Подсчитываем сколько союзников уже атакуют эту цель
        List<Mob> attackingAllies = countAlliesAttackingTarget(target);
        int allyCount = attackingAllies.size();

        // Рассчитываем суммарное HP атакующих
        float totalAllyHP = myHP;
        for (Mob ally : attackingAllies) {
            totalAllyHP += ally.getHealth();
        }

        // ========================================
        // ЛОГИКА УМНОЙ АТАКИ
        // ========================================

        // 1. Если моб СЛАБЕЕ нас → атакует ТОЛЬКО 1 воин
        if (targetHP <= myHP * 1.2f) {
            if (allyCount >= 1) {
                return false;
            }
            return true;
        }

        // 2. Если моб СИЛЬНЕЕ нас → атакуют 2-3 воина
        if (targetHP > myHP * 1.2f) {
            int requiredWarriors = calculateRequiredWarriors(targetHP, myHP);

            if (allyCount >= requiredWarriors) {
                return false;
            }

            // Проверяем не превысим ли суммарное HP
            if (totalAllyHP >= targetHP * 1.5) {
                return false;
            }

            LOGGER.debug("[SmartAI] {} атакует сильного врага (HP: {}, союзники: {}/{})",
                    attacker.getId(), targetHP, allyCount, requiredWarriors);
            return true;
        }

        return true;
    }

    /**
     * ✅ Рассчитывает сколько воинов нужно для победы
     */
    private int calculateRequiredWarriors(float targetHP, float warriorHP) {
        float requiredTotalHP = targetHP * 1.3f;
        int warriors = (int) Math.ceil(requiredTotalHP / warriorHP);
        return Math.max(2, Math.min(3, warriors));
    }

    /**
     * ✅ Подсчитывает сколько союзников уже атакуют эту цель
     */
    private List<Mob> countAlliesAttackingTarget(LivingEntity target) {
        AABB searchBox = attacker.getBoundingBox().inflate(48.0);

        List<Mob> allies = attacker.level().getEntitiesOfClass(Mob.class, searchBox, mob -> {
            if (!(mob instanceof GuardEntity || mob instanceof KnightEntity)) {
                return false;
            }
            if (!mob.isAlive() || mob == attacker) {
                return false;
            }
            return mob.getTarget() == target;
        });

        return allies;
    }
}
