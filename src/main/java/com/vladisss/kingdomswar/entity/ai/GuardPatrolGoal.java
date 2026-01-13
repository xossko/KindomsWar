package com.vladisss.kingdomswar.entity.ai;

import com.vladisss.kingdomswar.entity.GuardEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.pathfinder.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.EnumSet;
import java.util.List;

public class GuardPatrolGoal extends Goal {
    private static final Logger LOGGER = LoggerFactory.getLogger("KingdomsWar");
    private final GuardEntity guard;
    private final List<BlockPos> patrolPoints;
    private int currentPointIndex = 0;
    private int stuckCounter = 0;
    private BlockPos lastTargetPos = null;

    public GuardPatrolGoal(GuardEntity guard, List<BlockPos> patrolPoints) {
        this.guard = guard;
        this.patrolPoints = patrolPoints;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        return guard.getTarget() == null && !patrolPoints.isEmpty();
    }

    @Override
    public boolean canContinueToUse() {
        return guard.getTarget() == null && !patrolPoints.isEmpty();
    }

    @Override
    public void tick() {
        if (patrolPoints.isEmpty()) return;

        BlockPos targetPoint = patrolPoints.get(currentPointIndex);

        // ✅ Проверка застревания
        if (lastTargetPos != null && lastTargetPos.equals(targetPoint)) {
            stuckCounter++;

            // Застрял больше 5 секунд - пропускаем точку
            if (stuckCounter > 100) {
                LOGGER.warn("[Patrol] Стражник застрял на пути к {}. Переход к следующей точке",
                        targetPoint.toShortString());
                currentPointIndex = (currentPointIndex + 1) % patrolPoints.size();
                stuckCounter = 0;
                lastTargetPos = null;
                return;
            }
        } else {
            lastTargetPos = targetPoint;
            stuckCounter = 0;
        }

        double distance = guard.position().distanceTo(
                new net.minecraft.world.phys.Vec3(
                        targetPoint.getX() + 0.5,
                        targetPoint.getY(),
                        targetPoint.getZ() + 0.5
                )
        );

        // ✅ Достигли точки патруля
        if (distance < 3.0) {
            currentPointIndex = (currentPointIndex + 1) % patrolPoints.size();
            stuckCounter = 0;
            lastTargetPos = null;
        } else {
            // ✅ УЛУЧШЕННАЯ навигация - проверка пути
            Path path = guard.getNavigation().getPath();

            if (path == null || path.isDone()) {
                // Пробуем построить путь заново
                boolean success = guard.getNavigation().moveTo(
                        targetPoint.getX() + 0.5,
                        targetPoint.getY(),
                        targetPoint.getZ() + 0.5,
                        1.0D
                );

                if (!success) {
                    // Не можем построить путь - пропускаем точку
                    LOGGER.warn("[Patrol] Не удалось построить путь к {}. Пропуск точки",
                            targetPoint.toShortString());
                    currentPointIndex = (currentPointIndex + 1) % patrolPoints.size();
                    stuckCounter = 0;
                }
            }
        }
    }

    @Override
    public void stop() {
        guard.getNavigation().stop();
        stuckCounter = 0;
        lastTargetPos = null;
    }
}
