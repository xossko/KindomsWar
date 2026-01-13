package com.vladisss.kingdomswar.entity;

import com.vladisss.kingdomswar.entity.ai.AvoidCrowdingGoal;
import com.vladisss.kingdomswar.entity.ai.GuardReturnToPostGoal;
import com.vladisss.kingdomswar.entity.ai.SmartTargetGoal; // ✅ ДОБАВЛЕНО
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
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

public class GuardEntity extends PathfinderMob {
    private static final Logger LOGGER = LoggerFactory.getLogger("KingdomsWar");
    private static final EntityDataAccessor<Integer> VARIANT =
            SynchedEntityData.defineId(GuardEntity.class, EntityDataSerializers.INT);

    private BlockPos homePosition;
    private GuardReturnToPostGoal returnToPostGoal;

    // ✅ Ломание блоков при застревании
    private int stuckBreakTimer = 0;
    private BlockPos lastTickPos = null;

    public GuardEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        this.setCanPickUpLoot(false);
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && level() instanceof ServerLevel) {
            tryBreakBlockIfStuck(); // ✅ Система ломания блоков
        }
    }

    // ✅ Ломает блоки если застрял
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
                            LOGGER.info("Guard #{} пробил {} на пути",
                                    this.getId(), frontState.getBlock().getName().getString());
                        }

                        // Проверяем верхний блок (для 2-блочного прохода)
                        BlockState upperState = level.getBlockState(upperPos);
                        if (canBreakBlock(level, upperPos, upperState)) {
                            level.destroyBlock(upperPos, false);
                            brokeBlock = true;
                            LOGGER.info("Guard #{} пробил {} сверху",
                                    this.getId(), upperState.getBlock().getName().getString());
                        }

                        if (brokeBlock) {
                            // Сбрасываем таймер после успешного ломания
                            stuckBreakTimer = 0;
                            // Обновляем навигацию
                            if (this.getTarget() != null) {
                                this.getNavigation().moveTo(this.getTarget(), 1.2D);
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

    // ✅ Проверка можно ли ломать блок
    private boolean canBreakBlock(ServerLevel level, BlockPos pos, BlockState state) {
        if (state.isAir()) {
            return false;
        }

        // Получаем твердость блока
        float destroySpeed = state.getDestroySpeed(level, pos);
        // Ломаем только мягкие блоки (твердость < 1.0)
        if (destroySpeed < 0 || destroySpeed > 1.0F) {
            return false;
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
        if (this.returnToPostGoal != null) {
            this.returnToPostGoal.setHomePosition(pos);
        }
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
    }

    @Override
    public void readAdditionalSaveData(CompoundTag compound) {
        super.readAdditionalSaveData(compound);
        this.setVariant(compound.getInt("Variant"));
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 25.0D)
                .add(Attributes.ARMOR, 15.0D)        // ✅ Улучшено с 10.0
                .add(Attributes.ATTACK_DAMAGE, 5.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.35D) // ✅ Улучшено с 0.30D
                .add(Attributes.FOLLOW_RANGE, 48.0D);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.2D, false));
        this.returnToPostGoal = new GuardReturnToPostGoal(this, 1.0D, 15);
        this.goalSelector.addGoal(2, this.returnToPostGoal);
        this.goalSelector.addGoal(3, new AvoidCrowdingGoal(this, 3));
        this.goalSelector.addGoal(4, new WaterAvoidingRandomStrollGoal(this, 0.6D, 5)); // ✅ Улучшено с 0.3D
        this.goalSelector.addGoal(5, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(6, new RandomLookAroundGoal(this));

        // ПРИОРИТЕТ 1: Реагировать на атаку союзников МОМЕНТАЛЬНО!
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this, GuardEntity.class, KnightEntity.class)
                .setAlertOthers(GuardEntity.class, KnightEntity.class));

        // ✅ ПРИОРИТЕТ 2: УМНАЯ АТАКА (вместо NearestAttackableTargetGoal)
        this.targetSelector.addGoal(2, new SmartTargetGoal<>(
                this,
                Mob.class,
                20, // Проверка каждые 20 тиков (1 сек)
                true,
                false,
                (mob) -> {
                    if (mob instanceof GuardEntity || mob instanceof KnightEntity) {
                        return false;
                    }

                    if (mob.getType().getCategory() == net.minecraft.world.entity.MobCategory.MONSTER) {
                        return true;
                    }

                    if (mob instanceof net.minecraft.world.entity.NeutralMob) {
                        return ((net.minecraft.world.entity.NeutralMob) mob).isAngry();
                    }

                    return false;
                }
        ));
    }

    @Override
    protected void populateDefaultEquipmentSlots(RandomSource random, DifficultyInstance difficulty) {
        ItemStack spear = new ItemStack(
                net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(
                        new net.minecraft.resources.ResourceLocation("epicfight", "iron_spear")
                )
        );

        if (spear.isEmpty()) {
            spear = new ItemStack(Items.TRIDENT);
        }

        this.setItemSlot(EquipmentSlot.MAINHAND, spear);
        this.setDropChance(EquipmentSlot.MAINHAND, 0.05F);
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }
}
