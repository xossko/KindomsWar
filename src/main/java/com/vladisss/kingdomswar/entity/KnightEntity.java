package com.vladisss.kingdomswar.entity;

import com.vladisss.kingdomswar.entity.ai.AvoidCrowdingGoal;
import com.vladisss.kingdomswar.entity.ai.SmartTargetGoal;
import com.vladisss.kingdomswar.kingdom.KingdomManager;
import com.vladisss.kingdomswar.kingdom.KingdomTerritory;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.util.RandomSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;

public class KnightEntity extends PathfinderMob {
    private static final Logger LOGGER = LoggerFactory.getLogger("KingdomsWar");
    private static final EntityDataAccessor<Integer> VARIANT = SynchedEntityData.defineId(KnightEntity.class, EntityDataSerializers.INT);

    private BlockPos homePosition;
    private BlockPos patrolCenter;
    private int patrolRadius = 25;

    // Убираем телепорты - только плавное перемещение
    private int returningTimer = 0;
    private static final int MAX_DISTANCE_FROM_HOME = 80;

    // ✅ НОВОЕ: Ломание блоков при застревании
    private int stuckBreakTimer = 0;
    private BlockPos lastTickPos = null;

    public KnightEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        this.setCanPickUpLoot(false);
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && level() instanceof ServerLevel serverLevel) {
            checkDistanceFromHome();
            tryBreakBlockIfStuck();
            checkTerritoryBounds(serverLevel); // ✅ ИСПРАВЛЕНО
        }
    }


    private void checkTerritoryBounds(ServerLevel level) {
        KingdomTerritory kingdom = KingdomManager.getKingdom(level);
        if (kingdom != null) {
            kingdom.getRevengeSystem().checkWarriorBounds(this);
        }
    }


    // ✅ НОВОЕ: Ломает блоки если застрял
    private void tryBreakBlockIfStuck() {
        // Проверяем только если активно двигаемся куда-то
        if (!this.getNavigation().isInProgress()) {
            stuckBreakTimer = 0;
            lastTickPos = null;
            return;
        }

        BlockPos currentPos = this.blockPosition();

        // Проверяем застревание - если не двигаемся
        if (lastTickPos != null && currentPos.equals(lastTickPos)) {
            // Двигаемся? (скорость > 0.01)
            double movementSpeed = this.getDeltaMovement().horizontalDistance();

            if (movementSpeed < 0.01) {
                stuckBreakTimer++;

                // Застряли на 2+ секунды (40 тиков)
                if (stuckBreakTimer > 40) {
                    // Определяем направление движения
                    Direction direction = this.getDirection();
                    BlockPos frontPos = currentPos.relative(direction);
                    BlockPos upperPos = frontPos.above();

                    if (level() instanceof ServerLevel level) {
                        boolean brokeBlock = false;

                        // Проверяем нижний блок
                        BlockState frontState = level.getBlockState(frontPos);
                        if (canBreakBlock(level, frontPos, frontState)) {
                            level.destroyBlock(frontPos, false);
                            brokeBlock = true;
                            LOGGER.info("Knight #{} пробил {} на пути",
                                    this.getId(), frontState.getBlock().getName().getString());
                        }

                        // Проверяем верхний блок (для 2-блочного прохода)
                        BlockState upperState = level.getBlockState(upperPos);
                        if (canBreakBlock(level, upperPos, upperState)) {
                            level.destroyBlock(upperPos, false);
                            brokeBlock = true;
                            LOGGER.info("Knight #{} пробил {} сверху",
                                    this.getId(), upperState.getBlock().getName().getString());
                        }

                        if (brokeBlock) {
                            // Сбрасываем таймер после успешного ломания
                            stuckBreakTimer = 0;
                            // Обновляем навигацию
                            if (this.getTarget() != null) {
                                this.getNavigation().moveTo(this.getTarget(), 1.3D);
                            }
                        }
                    }
                }
            } else {
                // Двигаемся - сбрасываем таймер
                stuckBreakTimer = 0;
            }
        } else {
            // Позиция изменилась - не застряли
            stuckBreakTimer = 0;
        }

        lastTickPos = currentPos;
    }

    // ✅ НОВОЕ: Проверка можно ли ломать блок
    private boolean canBreakBlock(ServerLevel level, BlockPos pos, BlockState state) {
        if (state.isAir()) {
            return false;
        }

        // Получаем твердость блока
        float destroySpeed = state.getDestroySpeed(level, pos);

        // Ломаем только мягкие блоки (твердость < 2.0)
        // Листва = 0.2, Земля = 0.5, Песок = 0.5, Дерево = 2.0
        // Камень = 1.5, НО мы ставим порог 1.0 чтобы не трогать камень
        if (destroySpeed < 0 || destroySpeed > 1.0F) {
            return false; // Неразрушимый или слишком твердый
        }

        // Не ломаем ценные блоки (сундуки, печки и т.д.)
        if (state.hasBlockEntity()) {
            return false;
        }

        // Не ломаем двери и ворота
        String blockName = state.getBlock().getName().getString().toLowerCase();
        if (blockName.contains("door") || blockName.contains("gate") ||
                blockName.contains("fence") || blockName.contains("wall")) {
            return false;
        }

        return true;
    }

    // Плавное возвращение вместо телепорта
    private void checkDistanceFromHome() {
        if (homePosition == null) return;

        double distSqr = this.blockPosition().distSqr(homePosition);

        if (distSqr > MAX_DISTANCE_FROM_HOME * MAX_DISTANCE_FROM_HOME) {
            if (this.getTarget() == null) {
                this.getNavigation().moveTo(homePosition.getX(), homePosition.getY(), homePosition.getZ(), 1.2D);
                returningTimer++;

                if (returningTimer > 100) {
                    LOGGER.info("Knight {} returning home (distance: {})", this.getId(), Math.sqrt(distSqr));
                    returningTimer = 0;
                }
            } else {
                if (this.getTarget().blockPosition().distSqr(homePosition) > MAX_DISTANCE_FROM_HOME * MAX_DISTANCE_FROM_HOME) {
                    this.setTarget(null);
                    this.getNavigation().moveTo(homePosition.getX(), homePosition.getY(), homePosition.getZ(), 1.2D);
                }
            }
        } else {
            returningTimer = 0;
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

    public void setHomePosition(BlockPos pos) {
        this.homePosition = pos;
        this.patrolCenter = pos;
    }

    public void setPatrolCenter(BlockPos center) {
        this.patrolCenter = center;
    }

    public void setPatrolRadius(int radius) {
        this.patrolRadius = Math.min(radius, 40);
    }

    public BlockPos getHomePosition() {
        return homePosition;
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
        if (homePosition != null) {
            compound.putInt("HomeX", homePosition.getX());
            compound.putInt("HomeY", homePosition.getY());
            compound.putInt("HomeZ", homePosition.getZ());
        }
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
        if (compound.contains("HomeX")) {
            this.homePosition = new BlockPos(
                    compound.getInt("HomeX"),
                    compound.getInt("HomeY"),
                    compound.getInt("HomeZ")
            );
        }
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
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.3D, false));
        this.goalSelector.addGoal(2, new RestrictedPatrolGoal(this));
        this.goalSelector.addGoal(3, new AvoidCrowdingGoal(this, 3));
        this.goalSelector.addGoal(4, new WaterAvoidingRandomStrollGoal(this, 0.6D, 20));
        this.goalSelector.addGoal(5, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(6, new RandomLookAroundGoal(this));

        // Агрессия
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this, GuardEntity.class, KnightEntity.class)
                .setAlertOthers(GuardEntity.class, KnightEntity.class));

        // ✅ УМНАЯ АТАКА (вместо стандартной)
        this.targetSelector.addGoal(2, new SmartTargetGoal<>(
                this,
                Mob.class,
                10,
                true,
                false,
                (mob) -> {
                    if (mob instanceof GuardEntity || mob instanceof KnightEntity) return false;
                    if (mob.getType().getCategory() == net.minecraft.world.entity.MobCategory.MONSTER) {
                        // Атакуем монстров только если они близко к дому
                        return mob.blockPosition().distSqr(homePosition) < 60 * 60;
                    }
                    return false;
                }
        ));
    }


    private static class RestrictedPatrolGoal extends Goal {
        private final KnightEntity knight;
        private BlockPos targetPos;
        private int cooldown = 0;

        public RestrictedPatrolGoal(KnightEntity knight) {
            this.knight = knight;
        }

        @Override
        public boolean canUse() {
            if (cooldown > 0) {
                cooldown--;
                return false;
            }

            if (knight.patrolCenter == null) return false;
            if (knight.getTarget() != null) return false;

            targetPos = knight.patrolCenter.offset(
                    knight.random.nextInt(knight.patrolRadius * 2) - knight.patrolRadius,
                    0,
                    knight.random.nextInt(knight.patrolRadius * 2) - knight.patrolRadius
            );

            return true;
        }

        @Override
        public void start() {
            knight.getNavigation().moveTo(targetPos.getX(), targetPos.getY(), targetPos.getZ(), 0.7D);
            cooldown = 100 + knight.random.nextInt(100);
        }

        @Override
        public boolean canContinueToUse() {
            return !knight.getNavigation().isDone() && knight.getTarget() == null;
        }

        @Override
        public void stop() {
            knight.getNavigation().stop();
        }
    }

    @Override
    protected void populateDefaultEquipmentSlots(RandomSource random, DifficultyInstance difficulty) {
        ItemStack sword = new ItemStack(
                net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(
                        new net.minecraft.resources.ResourceLocation("epicfight", "iron_sword")
                )
        );
        if (sword.isEmpty()) {
            sword = new ItemStack(Items.IRON_SWORD);
        }
        this.setItemSlot(EquipmentSlot.MAINHAND, sword);
        this.setDropChance(EquipmentSlot.MAINHAND, 0.05F);
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }
}
