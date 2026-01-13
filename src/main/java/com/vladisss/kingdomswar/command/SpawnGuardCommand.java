package com.vladisss.kingdomswar.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.vladisss.kingdomswar.registry.ModEntities;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MobSpawnType;

public class SpawnGuardCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("kwspawnguard")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 100))
                                .executes(context -> {
                                    int count = IntegerArgumentType.getInteger(context, "count");
                                    return spawnGuards(context.getSource(), count);
                                })
                        )
                        .executes(context -> spawnGuards(context.getSource(), 1))
        );
    }

    private static int spawnGuards(CommandSourceStack source, int count) {
        ServerLevel level = source.getLevel();
        BlockPos pos = BlockPos.containing(source.getPosition());

        int spawned = 0;
        for (int i = 0; i < count; i++) {
            var guard = ModEntities.GUARD.get().create(level);
            if (guard != null) {
                double offsetX = (level.random.nextDouble() - 0.5) * 10;
                double offsetZ = (level.random.nextDouble() - 0.5) * 10;

                guard.moveTo(pos.getX() + offsetX, pos.getY(), pos.getZ() + offsetZ, 0.0F, 0.0F);
                guard.finalizeSpawn(level, level.getCurrentDifficultyAt(guard.blockPosition()),
                        MobSpawnType.COMMAND, null, null);

                if (level.addFreshEntity(guard)) {
                    spawned++;
                }
            }
        }

        // Используем финальную переменную для лямбды
        final int finalSpawned = spawned;
        source.sendSuccess(() -> Component.literal("Spawned " + finalSpawned + " guard(s)"), true);
        return finalSpawned;
    }
}
