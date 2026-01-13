package com.vladisss.kingdomswar.command;

import com.mojang.brigadier.CommandDispatcher;
import com.vladisss.kingdomswar.kingdom.KingdomManager;
import com.vladisss.kingdomswar.kingdom.KingdomTerritory;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

public class KingdomExpandCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("kingdomexpand")
                .requires(source -> source.hasPermission(2))
                .executes(context -> {
                    CommandSourceStack source = context.getSource();
                    ServerLevel level = source.getLevel();

                    KingdomTerritory kingdom = KingdomManager.getKingdom(level);

                    if (kingdom == null) {
                        source.sendFailure(Component.literal("В этом измерении нет королевства"));
                        return 0;
                    }

                    if (kingdom.getPoints() < 50) {
                        source.sendFailure(Component.literal(
                                "§cНедостаточно очков! §7Есть: §e" + kingdom.getPoints() +
                                        " §7Нужно: §e50"
                        ));
                        return 0;
                    }

                    kingdom.expandTerritory(level);

                    source.sendSuccess(() -> Component.literal(
                            "§aТерритория расширена!\n" +
                                    "§7Новый радиус: §e" + kingdom.getRadius() + " §7блоков\n" +
                                    "§7Контроль: §e" + kingdom.getControlledChunksCount() + " §7чанков"
                    ), true);

                    return 1;
                })
        );
    }
}
