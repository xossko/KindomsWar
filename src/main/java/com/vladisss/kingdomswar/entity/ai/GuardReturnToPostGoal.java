package com.vladisss.kingdomswar.entity.ai;

import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.core.BlockPos;
import java.util.EnumSet;

public class GuardReturnToPostGoal extends Goal {
    private final PathfinderMob mob;
    private final double speedModifier;
    private BlockPos homePosition;
    private final int maxDistance; // ✅ Максимальная дистанция от поста

    public GuardReturnToPostGoal(PathfinderMob mob, double speedModifier, int maxDistance) {
        this.mob = mob;
        this.speedModifier = speedModifier;
        this.maxDistance = maxDistance;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    public void setHomePosition(BlockPos pos) {
        this.homePosition = pos;
    }

    @Override
    public boolean canUse() {
        if (this.mob.getTarget() != null) {
            return false; // В бою - не возвращается
        }

        if (this.homePosition == null) {
            return false;
        }

        // Возвращается если дальше maxDistance
        return this.mob.blockPosition().distSqr(this.homePosition) > (this.maxDistance * this.maxDistance);
    }

    @Override
    public boolean canContinueToUse() {
        if (this.mob.getTarget() != null) {
            return false;
        }

        if (this.homePosition == null) {
            return false;
        }

        return this.mob.blockPosition().distSqr(this.homePosition) > 9.0D; // 3 блока
    }

    @Override
    public void start() {
        if (this.homePosition != null) {
            this.mob.getNavigation().moveTo(
                    this.homePosition.getX() + 0.5,
                    this.homePosition.getY(),
                    this.homePosition.getZ() + 0.5,
                    this.speedModifier
            );
        }
    }

    @Override
    public void stop() {
        this.mob.getNavigation().stop();
    }

    @Override
    public void tick() {
        if (this.homePosition != null && this.mob.getNavigation().isDone()) {
            this.mob.getNavigation().moveTo(
                    this.homePosition.getX() + 0.5,
                    this.homePosition.getY(),
                    this.homePosition.getZ() + 0.5,
                    this.speedModifier
            );
        }
    }
}
