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
 * СТРАЖНИК - ЗАЩИТНИК ЗАМКА
 * 
 * Функции:
 * - Стоит на посту вокруг замка
 * - НЕ УХОДИТ ДАЛЬШЕ 5 БЛОКОВ от замка
 * - Атакует цели, назначенные KingdomAI
 * - Возвращается на пост после боя
 */
public class GuardEntity extends PathfinderMob {
    private static final Logger LOGGER = LoggerFactory.getLogger("KingdomsWar");
    
    private static final EntityDataAccessor<Integer> VARIANT =
            SynchedEntityData.defineId(GuardEntity.class, EntityDataSerializers.INT);

    // Позиция поста стражника
    private BlockPos guardPost;
    
    // МАКСИМАЛЬНОЕ РАССТОЯНИЕ ОТ ПОСТА
    private static final int MAX_DISTANCE_FROM_POST = 5;

    /**
     * ✅ Проверка что цель валидна (не под землёй, в зоне поста)
     /**
     * ✅ СТРОГАЯ Проверка: цель на поверхности и видна
     */
    private boolean isValidTarget(LivingEntity target) {
        if (target == null || !target.isAlive()) {
            return false;
        }

        // Проверка: в зоне охраны поста
        if (guardPost != null) {
            double distanceToPost = target.distanceToSqr(
                    guardPost.getX(), guardPost.getY(), guardPost.getZ()
            );
            if (distanceToPost > 20 * 20) {
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
            // Цель летает или под землёй - игнорируем
            return false;
        }

        // Проверяем что цель НЕ под землёй (есть небо над головой)
        int surfaceY = target.level().getHeight(
                net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE,
                targetPos.getX(),
                targetPos.getZ()
        );

        // Цель должна быть НЕ НИЖЕ поверхности минус 5 блоков
        if (targetPos.getY() < surfaceY - 5) {
            return false;
        }

        // ✅ Дополнительно: проверка видимости (raycast)
        return this.hasLineOfSight(target);
    }




    public GuardEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        this.setCanPickUpLoot(false);
    }

    // Счетчик "застревания"
    private int stuckTimer = 0;
    private BlockPos lastPos = null;

    @Override
    public void tick() {
        super.tick();

        // ✅ ПРОВЕРКА ЗАСТРЕВАНИЯ: если не двигаемся 100 тиков (5 секунд)
        if (!level().isClientSide && guardPost != null) {
            BlockPos currentPos = this.blockPosition();

            // Проверяем двигаемся ли мы
            if (lastPos != null && lastPos.equals(currentPos)) {
                stuckTimer++;
            } else {
                stuckTimer = 0;
            }
            lastPos = currentPos;

            // ✅ ТЕЛЕПОРТ НАЗАД: если застряли больше 5 секунд
            if (stuckTimer > 100) {
                double distanceFromPost = this.distanceToSqr(
                        guardPost.getX(), guardPost.getY(), guardPost.getZ()
                );

                // Застряли далеко от поста - телепортируем
                if (distanceFromPost > 15 * 15) {
                    this.teleportTo(guardPost.getX() + 0.5, guardPost.getY(), guardPost.getZ() + 0.5);
                    this.setTarget(null);
                    this.getNavigation().stop();
                    LOGGER.info("[Guard] Застрял, телепортирован на пост");
                }
                stuckTimer = 0;
            }

            LivingEntity target = this.getTarget();

            // Если есть цель - проверяем она в территории или нет
            if (target != null && target.isAlive()) {
                double targetDistance = target.distanceToSqr(
                        guardPost.getX(), guardPost.getY(), guardPost.getZ()
                );

                // Если цель дальше 50 блоков - отменяем
                if (targetDistance > 50 * 50) {
                    this.setTarget(null);
                    this.getNavigation().moveTo(guardPost.getX(), guardPost.getY(), guardPost.getZ(), 1.0D);
                }
            } else {
                // Нет цели - возвращаемся на пост только если далеко
                double distanceFromPost = this.distanceToSqr(
                        guardPost.getX(), guardPost.getY(), guardPost.getZ()
                );

                if (distanceFromPost > 10 * 10) {
                    this.getNavigation().moveTo(guardPost.getX(), guardPost.getY(), guardPost.getZ(), 1.0D);
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
     * Установить пост стражника (вызывается из KingdomAI)
     */
    public void setGuardPos(BlockPos pos) {
        this.guardPost = pos;
    }
    
    public BlockPos getGuardPos() {
        return guardPost;
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
        if (guardPost != null) {
            compound.putInt("PostX", guardPost.getX());
            compound.putInt("PostY", guardPost.getY());
            compound.putInt("PostZ", guardPost.getZ());
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag compound) {
        super.readAdditionalSaveData(compound);
        this.setVariant(compound.getInt("Variant"));
        if (compound.contains("PostX")) {
            this.guardPost = new BlockPos(
                compound.getInt("PostX"),
                compound.getInt("PostY"),
                compound.getInt("PostZ")
            );
        }
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 25.0D)
                .add(Attributes.ARMOR, 15.0D)
                .add(Attributes.ATTACK_DAMAGE, 5.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.35D)
                .add(Attributes.FOLLOW_RANGE, 48.0D);
    }

    @Override
    protected void registerGoals() {
        // ==================== ПРОСТАЯ СИСТЕМА AI ====================
        // 0. Плавать
        this.goalSelector.addGoal(0, new FloatGoal(this));

        // 1. Атаковать цель (назначенную KingdomAI)
        this.goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.2D, false));

        // 2. Стоять на посту
        this.goalSelector.addGoal(2, new GuardStandAtPostGoal(this));

        // 3. Смотреть на игроков
        this.goalSelector.addGoal(3, new LookAtPlayerGoal(this, Player.class, 8.0F));

        // 4. Случайно смотреть вокруг
        this.goalSelector.addGoal(4, new RandomLookAroundGoal(this));

        // ==================== ЦЕЛИ ДЛЯ АТАКИ ====================

        // ✅ ТОЛЬКО реагировать на атаку + отвечать ударом
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this, GuardEntity.class, KnightEntity.class)
                .setAlertOthers(GuardEntity.class, KnightEntity.class));

        // ❌ УБРАЛИ NearestAttackableTargetGoal - теперь цели назначает ТОЛЬКО KingdomAI!
    }


    /**
     * AI Цель: Стоять на посту
     */
    private static class GuardStandAtPostGoal extends Goal {
        private final GuardEntity guard;
        private int standTimer = 0;

        public GuardStandAtPostGoal(GuardEntity guard) {
            this.guard = guard;
        }

        @Override
        public boolean canUse() {
            // ✅ НЕ стоять на посту если есть цель для атаки!
            if (guard.getTarget() != null && guard.getTarget().isAlive()) {
                return false;
            }

            // Стоим на посту только если нет цели
            return guard.guardPost != null;
        }

        @Override
        public boolean canContinueToUse() {
            // ✅ ПРЕРВАТЬ стояние если появилась цель!
            if (guard.getTarget() != null && guard.getTarget().isAlive()) {
                return false;
            }

            return guard.guardPost != null;
        }

        @Override
        public void start() {
            standTimer = 0;
        }

        @Override
        public void tick() {
            standTimer++;

            // Если далеко от поста - идем к нему
            double distance = guard.distanceToSqr(guard.guardPost.getX(), guard.guardPost.getY(), guard.guardPost.getZ());
            if (distance > 4.0) { // Дальше 2 блоков
                guard.getNavigation().moveTo(guard.guardPost.getX(), guard.guardPost.getY(), guard.guardPost.getZ(), 1.0D);
            } else {
                // На посту - стоим
                guard.getNavigation().stop();

                // Смотрим в сторону замка (от замка наружу)
                if (standTimer % 100 == 0) {
                    // Каждые 5 секунд поворачиваемся
                    double angle = Math.atan2(
                            guard.guardPost.getZ() - guard.getZ(),
                            guard.guardPost.getX() - guard.getX()
                    );
                    guard.setYRot((float) Math.toDegrees(angle) - 90); // Смотрим от замка
                }
            }
        }
    }


    @Override
    protected void populateDefaultEquipmentSlots(RandomSource random, DifficultyInstance difficulty) {
        // Пытаемся дать копье из Epic Fight
        ItemStack spear = new ItemStack(
                net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(
                        new net.minecraft.resources.ResourceLocation("epicfight", "iron_spear")
                )
        );

        // Если нет Epic Fight - даем трезубец
        if (spear.isEmpty()) {
            spear = new ItemStack(Items.TRIDENT);
        }

        this.setItemSlot(EquipmentSlot.MAINHAND, spear);
        this.setDropChance(EquipmentSlot.MAINHAND, 0.05F);
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false; // Не исчезают
    }
}
