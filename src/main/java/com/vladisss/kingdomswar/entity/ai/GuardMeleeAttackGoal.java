package com.vladisss.kingdomswar.entity.ai;

import com.vladisss.kingdomswar.entity.GuardEntity;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;

public class GuardMeleeAttackGoal extends MeleeAttackGoal {
    private final GuardEntity guard;

    public GuardMeleeAttackGoal(GuardEntity pMob, double pSpeedModifier, boolean pFollowingTargetEvenIfNotSeen) {
        super(pMob, pSpeedModifier, pFollowingTargetEvenIfNotSeen);
        this.guard = pMob;
    }

    @Override
    protected void checkAndPerformAttack(net.minecraft.world.entity.LivingEntity pEnemy, double pDistToEnemySqr) {
        double d0 = this.getAttackReachSqr(pEnemy);
        if (pDistToEnemySqr <= d0 && this.getTicksUntilNextAttack() <= 0) {
            this.resetAttackCooldown();
            this.mob.doHurtTarget(pEnemy);
        }
    }
}
