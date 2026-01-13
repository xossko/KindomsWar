package com.vladisss.kingdomswar.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.util.RandomSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;

/**
 * РЫЦАРЬ - ПАТРУЛЬНЫЙ ВОИН
 * 
 * Функции:
 * - Патрулирует у границы территории
 * - НЕ УХОДИТ ДАЛЬШЕ 5 БЛОКОВ от границы
 * - Атакует цели, назначенные KingdomAI
 * - Позиция патруля управляется KingdomAI
 */
public class KnightEntity extends PathfinderMob {
    private static final Logger LOGGER = LoggerFactory.getLogger("KingdomsWar");
    
    private static final EntityDataAccessor<Integer> VARIANT = 
        SynchedEntityData.defineId(KnightEntity.class, EntityDataSerializers.INT);

    // Позиция патруля (устанавливается KingdomAI)
    private BlockPos patrolCenter;
    private int patrolRadius = 15; // Радиус патруля
    
    // МАКСИМАЛЬНОЕ РАССТОЯНИЕ ОТ ПАТРУЛЯ
    private static final int MAX_DISTANCE_FROM_PATROL = 5;

    /**
     /**
     * ✅ СТРОГАЯ Проверка: цель на поверхности и видна
     */
    private boolean isValidTarget(LivingEntity target) {
        if (target == null || !target.isAlive()) {
            return false;
        }

        // Проверка: в зоне патруля
        if (patrolCenter != null) {
            double distanceToPatrol = target.distanceToSqr(
                    patrolCenter.getX(), patrolCenter.getY(), patrolCenter.getZ()
            );
            int maxDist = patrolRadius + 20;
            if (distanceToPatrol > maxDist * maxDist) {
                return false;
            }
        }

        // ✅ НОВАЯ ПРОВЕРКА: цель должна быть НА ПОВЕРХНОСТИ
        BlockPos targetPos = target.blockPosition();
        BlockPos belowTarget = targetPos.below();

        // Проверяем что под целью есть твердый блок
        boolean hasGroundBelow = !target.level().getBlockState(belowTarget).isAir() ||
                !target.level().getBlockState(belowTarget.below()).isAir();

        if (!hasGroundBelow) {
            return false;
        }

        // Проверяем что цель НЕ под землёй
        int surfaceY = target.level().getHeight(
                net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE,
                targetPos.getX(),
                targetPos.getZ()
        );

        if (targetPos.getY() < surfaceY - 5) {
            return false;
        }

        // ✅ Проверка видимости
        return this.hasLineOfSight(target);
    }





    public KnightEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        this.setCanPickUpLoot(false);
    }

    // Счетчик застревания
    private int stuckTimer = 0;
    private BlockPos lastPos = null;

    @Override
    public void tick() {
        super.tick();

        // ✅ ПРОВЕРКА ЗАСТРЕВАНИЯ
        if (!level().isClientSide && patrolCenter != null) {
            BlockPos currentPos = this.blockPosition();

            if (lastPos != null && lastPos.equals(currentPos)) {
                stuckTimer++;
            } else {
                stuckTimer = 0;
            }
            lastPos = currentPos;

            // ✅ ТЕЛЕПОРТ: если застряли больше 5 секунд
            if (stuckTimer > 100) {
                double distanceFromPatrol = this.distanceToSqr(
                        patrolCenter.getX(), patrolCenter.getY(), patrolCenter.getZ()
                );

                // Застряли далеко - телепортируем
                if (distanceFromPatrol > (patrolRadius + 10) * (patrolRadius + 10)) {
                    this.teleportTo(patrolCenter.getX() + 0.5, patrolCenter.getY(), patrolCenter.getZ() + 0.5);
                    this.setTarget(null);
                    this.getNavigation().stop();
                    LOGGER.info("[Knight] Застрял, телепортирован на патруль");
                }
                stuckTimer = 0;
            }

            LivingEntity target = this.getTarget();

            if (target != null && target.isAlive()) {
                double targetDistance = target.distanceToSqr(
                        patrolCenter.getX(), patrolCenter.getY(), patrolCenter.getZ()
                );

                if (targetDistance > 120 * 120) {
                    this.setTarget(null);
                    LOGGER.debug("[Knight] Цель слишком далеко, возвращаюсь");
                }
            } else {
                double distanceFromPatrol = this.distanceToSqr(
                        patrolCenter.getX(), patrolCenter.getY(), patrolCenter.getZ()
                );

                int maxDist = patrolRadius + 30;
                if (distanceFromPatrol > maxDist * maxDist) {
                    this.getNavigation().moveTo(
                            patrolCenter.getX(), patrolCenter.getY(), patrolCenter.getZ(), 1.0D
                    );
                }
            }
        }
    }



    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(VARIANT, 0);
    }

    public int getVariant() {
        return this.entityData.get(VARIANT);
    }

    public void setVariant(int variant) {
        this.entityData.set(VARIANT, variant);
    }

    /**
     * Установить центр патруля (вызывается из KingdomAI)
     */
    public void setPatrolCenter(BlockPos center) {
        this.patrolCenter = center;
    }
    
    /**
     * Установить радиус патруля (вызывается из KingdomAI)
     */
    public void setPatrolRadius(int radius) {
        this.patrolRadius = Math.min(radius, 30); // Максимум 30 блоков
    }
    
    public BlockPos getPatrolCenter() {
        return patrolCenter;
    }
    
    public int getPatrolRadius() {
        return patrolRadius;
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
                                        MobSpawnType spawnType, @Nullable SpawnGroupData spawnData,
                                        @Nullable CompoundTag dataTag) {
        this.setVariant(this.random.nextInt(2));
        this.populateDefaultEquipmentSlots(this.random, difficulty);
        return super.finalizeSpawn(level, difficulty, spawnType, spawnData, dataTag);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag compound) {
        super.addAdditionalSaveData(compound);
        compound.putInt("Variant", this.getVariant());
        if (patrolCenter != null) {
            compound.putInt("PatrolX", patrolCenter.getX());
            compound.putInt("PatrolY", patrolCenter.getY());
            compound.putInt("PatrolZ", patrolCenter.getZ());
        }
        compound.putInt("PatrolRadius", patrolRadius);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag compound) {
        super.readAdditionalSaveData(compound);
        this.setVariant(compound.getInt("Variant"));
        if (compound.contains("PatrolX")) {
            this.patrolCenter = new BlockPos(
                    compound.getInt("PatrolX"),
                    compound.getInt("PatrolY"),
                    compound.getInt("PatrolZ")
            );
        }
        this.patrolRadius = compound.getInt("PatrolRadius");
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 30.0D)
                .add(Attributes.ARMOR, 20.0D)
                .add(Attributes.ATTACK_DAMAGE, 12.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.32D)
                .add(Attributes.FOLLOW_RANGE, 48.0D);
    }

    @Override
    protected void registerGoals() {
        // ==================== ПРОСТАЯ СИСТЕМА AI ====================

        // 0. Плавать
        this.goalSelector.addGoal(0, new FloatGoal(this));

        // 1. Атаковать цель (назначенную KingdomAI)
        this.goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.3D, false));

        // 2. Патрулировать область
        this.goalSelector.addGoal(2, new KnightPatrolGoal(this));

        // 3. Смотреть на игроков
        this.goalSelector.addGoal(3, new LookAtPlayerGoal(this, Player.class, 8.0F));

        // 4. Случайно смотреть вокруг
        this.goalSelector.addGoal(4, new RandomLookAroundGoal(this));

        // ==================== ЦЕЛИ ДЛЯ АТАКИ ====================

        // ✅ ТОЛЬКО реагировать на атаку
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this, GuardEntity.class, KnightEntity.class)
                .setAlertOthers(GuardEntity.class, KnightEntity.class));

        // ❌ УБРАЛИ NearestAttackableTargetGoal - цели назначает ТОЛЬКО KingdomAI!
    }


    /**
     * AI Цель: Патрулировать область
     */
    private static class KnightPatrolGoal extends Goal {
        private final KnightEntity knight;
        private BlockPos targetPos;
        private int cooldown = 0;

        public KnightPatrolGoal(KnightEntity knight) {
            this.knight = knight;
        }

        @Override
        public boolean canUse() {
            // ✅ НЕ патрулировать если есть цель для атаки!
            if (knight.getTarget() != null && knight.getTarget().isAlive()) {
                return false;
            }

            if (knight.patrolCenter == null) {
                return false;
            }

            if (cooldown > 0) {
                cooldown--;
                return false;
            }

            // Генерируем случайную точку в радиусе патруля
            int radius = knight.patrolRadius;
            int offsetX = knight.random.nextInt(radius * 2) - radius;
            int offsetZ = knight.random.nextInt(radius * 2) - radius;

            targetPos = knight.patrolCenter.offset(offsetX, 0, offsetZ);
            return true;
        }

        @Override
        public void start() {
            knight.getNavigation().moveTo(targetPos.getX(), targetPos.getY(), targetPos.getZ(), 0.8D);
            cooldown = 100 + knight.random.nextInt(100); // 5-10 секунд перерыв
        }

        @Override
        public boolean canContinueToUse() {
            // ✅ ПРЕРВАТЬ патруль если появилась цель!
            if (knight.getTarget() != null && knight.getTarget().isAlive()) {
                knight.getNavigation().stop(); // Остановить движение
                return false;
            }

            // Продолжаем пока не дошли и нет цели
            return !knight.getNavigation().isDone();
        }

        @Override
        public void stop() {
            targetPos = null;
        }
    }


    @Override
    protected void populateDefaultEquipmentSlots(RandomSource random, DifficultyInstance difficulty) {
        // Пытаемся дать меч из Epic Fight
        ItemStack sword = new ItemStack(
                net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(
                        new net.minecraft.resources.ResourceLocation("epicfight", "iron_sword")
                )
        );
        
        // Если нет Epic Fight - даем железный меч
        if (sword.isEmpty()) {
            sword = new ItemStack(Items.IRON_SWORD);
        }
        
        this.setItemSlot(EquipmentSlot.MAINHAND, sword);
        this.setDropChance(EquipmentSlot.MAINHAND, 0.05F);
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false; // Не исчезают
    }
}
