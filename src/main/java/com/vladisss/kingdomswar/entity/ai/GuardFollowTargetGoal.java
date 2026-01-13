package com.vladisss.kingdomswar.entity.ai;

import com.vladisss.kingdomswar.entity.GuardEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

public class GuardFollowTargetGoal extends Goal {
    private final GuardEntity guard;
    private LivingEntity target;

    public GuardFollowTargetGoal(GuardEntity guard) {
        this.guard = guard;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        this.target = this.guard.getTarget();
        return this.target != null && this.target.isAlive();
    }

    @Override
    public void tick() {
        if (this.target != null && this.target.isAlive()) {
            this.guard.getNavigation().moveTo(this.target, 1.0D);
        }
    }
}
