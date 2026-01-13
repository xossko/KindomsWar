package com.vladisss.kingdomswar.command;

import com.mojang.brigadier.CommandDispatcher;
import com.vladisss.kingdomswar.kingdom.KingdomManager;
import com.vladisss.kingdomswar.kingdom.KingdomTerritory;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

public class KingdomStatusCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("kingdom_status")
                .requires(source -> source.hasPermission(2))
                .executes(context -> {
                    CommandSourceStack source = context.getSource();
                    ServerLevel level = source.getLevel();
                    KingdomTerritory kingdom = KingdomManager.getKingdom(level);

                    if (kingdom == null) {
                        source.sendFailure(Component.literal("§c[Королевство] Не найдено!"));
                        return 0;
                    }

                    int controlledBlocks = kingdom.getControlledBlocksByKnights(level);
                    int expectedIncome = controlledBlocks / 50; // 50 блоков = 1 очко

                    source.sendSuccess(() -> Component.literal(
                            "§6=== Статус королевства ===\n" +
                                    "§e" + kingdom.getName() + "\n" +
                                    "§7Замок: §f" + kingdom.getCastleCenter().toShortString() + "\n" +
                                    "§7Радиус: §f" + kingdom.getRadius() + " блоков\n" +
                                    "§7Очки: §f" + kingdom.getPoints() + "\n" +
                                    "§7Контролируемые блоки (рыцарями): §a" + controlledBlocks + "\n" +
                                    "§7Доход в минуту: §a+" + expectedIncome + " очков\n" +
                                    "§7Контролируемых чанков: §f" + kingdom.getControlledChunksCount()
                    ), false);

                    return 1;
                })
        );
    }
}

