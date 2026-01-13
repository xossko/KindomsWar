package com.vladisss.kingdomswar.registry;

import com.mojang.brigadier.CommandDispatcher;
import com.vladisss.kingdomswar.command.SpawnGuardCommand;
import net.minecraft.commands.CommandSourceStack;

public class ModCommands {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        SpawnGuardCommand.register(dispatcher);
    }
}
