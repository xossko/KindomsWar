package com.vladisss.kingdomswar.entity.ai;

import com.vladisss.kingdomswar.entity.GuardEntity;
import com.vladisss.kingdomswar.entity.KnightEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.List;

public class AvoidCrowdingGoal extends Goal {
    private final PathfinderMob mob;
    private final double maxPerGroup;
    private Vec3 escapePos;

    public AvoidCrowdingGoal(PathfinderMob mob, double maxPerGroup) {
        this.mob = mob;
        this.maxPerGroup = maxPerGroup;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        // Не работает в бою
        if (this.mob.getTarget() != null) {
            return false;
        }

        // Считаем соседей в радиусе 5 блоков
        List<PathfinderMob> nearby = this.mob.level().getEntitiesOfClass(
                PathfinderMob.class,
                this.mob.getBoundingBox().inflate(5.0),
                entity -> (entity instanceof GuardEntity || entity instanceof KnightEntity) && entity != this.mob
        );

        if (nearby.size() >= this.maxPerGroup) {
            // Слишком много рядом - ищем куда отойти
            this.escapePos = findEscapePosition();
            return this.escapePos != null;
        }

        return false;
    }

    @Override
    public void start() {
        if (this.escapePos != null) {
            this.mob.getNavigation().moveTo(this.escapePos.x, this.escapePos.y, this.escapePos.z, 1.0);
        }
    }

    @Override
    public boolean canContinueToUse() {
        return !this.mob.getNavigation().isDone() && this.mob.getTarget() == null;
    }

    private Vec3 findEscapePosition() {
        BlockPos currentPos = this.mob.blockPosition();

        for (int i = 0; i < 10; i++) {
            int dx = this.mob.getRandom().nextInt(16) - 8;
            int dz = this.mob.getRandom().nextInt(16) - 8;
            BlockPos testPos = currentPos.offset(dx, 0, dz);

            // Проверяем что там мало народу
            List<PathfinderMob> nearTarget = this.mob.level().getEntitiesOfClass(
                    PathfinderMob.class,
                    new net.minecraft.world.phys.AABB(testPos).inflate(5.0),
                    entity -> entity instanceof GuardEntity || entity instanceof KnightEntity
            );

            if (nearTarget.size() < 2) {
                return Vec3.atCenterOf(testPos);
            }
        }

        return null;
    }
}
