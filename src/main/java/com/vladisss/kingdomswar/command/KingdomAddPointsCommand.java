package com.vladisss.kingdomswar.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.vladisss.kingdomswar.kingdom.KingdomManager;
import com.vladisss.kingdomswar.kingdom.KingdomTerritory;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

public class KingdomAddPointsCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("kingdomaddpoints")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("points", IntegerArgumentType.integer(1, 1000))
                        .executes(context -> {
                            CommandSourceStack source = context.getSource();
                            ServerLevel level = source.getLevel();
                            int points = IntegerArgumentType.getInteger(context, "points");

                            KingdomTerritory kingdom = KingdomManager.getKingdom(level);

                            if (kingdom == null) {
                                source.sendFailure(Component.literal("В этом измерении нет королевства"));
                                return 0;
                            }

                            kingdom.addPoints(points, "команда администратора");

                            source.sendSuccess(() -> Component.literal(
                                    "§a+" + points + " очков\n" +
                                            "§7Всего: §e" + kingdom.getPoints()
                            ), true);

                            return 1;
                        })
                )
        );
    }
}
