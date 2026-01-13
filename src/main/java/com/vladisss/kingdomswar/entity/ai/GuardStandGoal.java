package com.vladisss.kingdomswar.entity.ai;

import com.vladisss.kingdomswar.entity.GuardEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

public class GuardStandGoal extends Goal {
    private final GuardEntity guard;
    private final BlockPos guardPost;
    private final double maxDistance = 2.0;

    public GuardStandGoal(GuardEntity guard, BlockPos guardPost) {
        this.guard = guard;
        this.guardPost = guardPost;
        this.setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        // Активна, если нет врагов и далеко от поста
        return guard.getTarget() == null &&
                guard.blockPosition().distSqr(guardPost) > maxDistance * maxDistance;
    }

    @Override
    public boolean canContinueToUse() {
        return guard.getTarget() == null &&
                guard.blockPosition().distSqr(guardPost) > 1.0;
    }

    @Override
    public void start() {
        guard.getNavigation().moveTo(guardPost.getX(), guardPost.getY(), guardPost.getZ(), 1.0);
    }

    @Override
    public void tick() {
        if (guard.getNavigation().isDone()) {
            // Смотрим наружу (от замка)
            guard.getLookControl().setLookAt(
                    guardPost.getX() * 2 - guard.getX(),
                    guard.getY(),
                    guardPost.getZ() * 2 - guard.getZ()
            );
        }
    }
}
