package com.vladisss.kingdomswar.command;

import com.mojang.brigadier.CommandDispatcher;
import com.vladisss.kingdomswar.entity.GuardEntity;
import com.vladisss.kingdomswar.entity.KnightEntity;
import com.vladisss.kingdomswar.kingdom.KingdomManager;
import com.vladisss.kingdomswar.kingdom.KingdomTerritory;
import com.vladisss.kingdomswar.registry.ModEntities;
import com.vladisss.kingdomswar.structure.CastleStructure;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class SpawnCastleCommand {
    private static final Logger LOGGER = LoggerFactory.getLogger("KingdomsWar");

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("spawncastle")
                .requires(source -> source.hasPermission(2))
                .executes(context -> {
                    CommandSourceStack source = context.getSource();
                    ServerLevel level = source.getLevel();

                    if (KingdomManager.getKingdom(level) != null) {
                        source.sendFailure(Component.literal("В этом измерении уже есть королевство!"));
                        return 0;
                    }

                    BlockPos pos = BlockPos.containing(source.getPosition());
                    BlockPos castlePos = new BlockPos(pos.getX(),
                            level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE_WG,
                                    pos.getX(), pos.getZ()),
                            pos.getZ());

                    source.sendSuccess(() -> Component.literal("§eСтроим замок..."), true);
                    LOGGER.info("[Kingdom] Начинается строительство замка в {}", castlePos.toShortString());

                    // Генерируем замок
                    CastleStructure.generate(level, castlePos);

                    // Создаем территорию
                    KingdomTerritory kingdom = new KingdomTerritory(castlePos, "Yellow Kingdom");

                    // ✅ СТАРТОВЫЙ КАПИТАЛ 300 ОЧКОВ
                    kingdom.addPoints(300, "Стартовый капитал (резерв на ЧС)");

                    KingdomManager.registerKingdom(level, kingdom);

                    // ✅ СПАВНИМ НАЧАЛЬНУЮ ОХРАНУ
                    spawnInitialGuards(level, castlePos);

                    source.sendSuccess(() -> Component.literal(
                            "§6═══════════════════════════\n" +
                                    "§e§lЖелтое Королевство основано!\n" +
                                    "§6═══════════════════════════\n" +
                                    "§7Замок: §f" + castlePos.toShortString() + "\n" +
                                    "§7Территория: §e" + kingdom.getRadius() + " §7блоков\n" +
                                    "§7Контроль: §e" + kingdom.getControlledChunksCount() + " §7чанков\n" +
                                    "§7Начальная охрана: §a13 воинов\n" +
                                    "§8 - 2 у ворот\n" +
                                    "§8 - 3 внутри замка\n" +
                                    "§8 - 4 патрульных\n" +
                                    "§8 - 4 рыцаря\n" +
                                    "§6Стартовый капитал: §e300 очков §7(резерв на ЧС)"
                    ), true);

                    // ✅ Устанавливаем начальные флаги на границе
                    kingdom.placeBorderFlags(level);

                    return 1;
                })
        );
    }

    // ✅ ЕДИНСТВЕННЫЙ правильный метод spawnInitialGuards
    private static void spawnInitialGuards(ServerLevel level, BlockPos castleCenter) {
        LOGGER.info("[Kingdom] Спавн начальных стражников...");
        int castleSize = 15;

        // ✅ Ворота - 2 стражника
        BlockPos gatePos = castleCenter.offset(0, 0, -castleSize - 2);
        int gateY = castleCenter.getY() + 1;
        spawnGateGuard(level, new BlockPos(gatePos.getX() - 2, gateY, gatePos.getZ()));
        spawnGateGuard(level, new BlockPos(gatePos.getX() + 2, gateY, gatePos.getZ()));
        LOGGER.info("[Kingdom] Спавн 2 стражников у ворот");

        // ✅ 3 стражника ВНУТРИ замка (разбросанно)
        BlockPos[] castleGuards = {
                castleCenter.offset(8, 1, 5),    // Северо-восток
                castleCenter.offset(-7, 1, -6),  // Юго-запад
                castleCenter.offset(3, 1, -9)    // Юг
        };

        for (BlockPos pos : castleGuards) {
            GuardEntity guard = ModEntities.GUARD.get().create(level);
            if (guard != null) {
                guard.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0, 0);
                guard.setGuardPos(pos);
                guard.finalizeSpawn(level, level.getCurrentDifficultyAt(pos),
                        net.minecraft.world.entity.MobSpawnType.COMMAND, null, null);
                guard.setPersistenceRequired();
                level.addFreshEntity(guard);
            }
        }
        LOGGER.info("[Kingdom] Спавн 3 стражников внутри замка");

        // ✅ Патрульные стражники (4 штуки вокруг замка)
        List<BlockPos> guardPatrolPoints = calculatePatrolRoute(level, castleCenter, castleSize + 10);
        for (int i = 0; i < 4; i++) {
            int startIndex = (i * guardPatrolPoints.size()) / 4;
            BlockPos spawnPos = guardPatrolPoints.get(startIndex);
            BlockPos adjustedPos = new BlockPos(
                    spawnPos.getX(),
                    castleCenter.getY() + 1,
                    spawnPos.getZ()
            );
            spawnPatrolGuard(level, adjustedPos, guardPatrolPoints);
        }
        LOGGER.info("[Kingdom] Спавн 4 патрульных стражников");

        // ✅ Рыцари (4 штуки дальше от замка)
        List<BlockPos> knightPatrolPoints = calculatePatrolRoute(level, castleCenter, castleSize + 25);
        for (int i = 0; i < 4; i++) {
            int startIndex = (i * knightPatrolPoints.size()) / 4;
            BlockPos spawnPos = knightPatrolPoints.get(startIndex);
            int groundY = level.getHeight(
                    net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE_WG,
                    spawnPos.getX(),
                    spawnPos.getZ()
            );
            BlockPos adjustedPos = new BlockPos(spawnPos.getX(), groundY, spawnPos.getZ());
            spawnPatrolKnight(level, adjustedPos, knightPatrolPoints);
        }
        LOGGER.info("[Kingdom] Спавн 4 рыцарей");
        LOGGER.info("[Kingdom] ✅ Всего создано 13 воинов!");
    }

    private static void spawnGateGuard(ServerLevel level, BlockPos pos) {
        GuardEntity guard = ModEntities.GUARD.get().create(level);
        if (guard != null) {
            guard.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 180.0F, 0.0F);
            guard.setVariant(0);
            guard.setGuardPos(pos);
            guard.finalizeSpawn(level, level.getCurrentDifficultyAt(pos),
                    net.minecraft.world.entity.MobSpawnType.COMMAND, null, null);
            guard.setPersistenceRequired();
            level.addFreshEntity(guard);
        }
    }

    private static void spawnPatrolGuard(ServerLevel level, BlockPos pos, List<BlockPos> patrolRoute) {
        GuardEntity guard = ModEntities.GUARD.get().create(level);
        if (guard != null) {
            guard.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0.0F, 0.0F);
            guard.setVariant(0);
            guard.setGuardPos(pos);
            guard.finalizeSpawn(level, level.getCurrentDifficultyAt(pos),
                    net.minecraft.world.entity.MobSpawnType.COMMAND, null, null);
            guard.setPersistenceRequired();
            level.addFreshEntity(guard);
        }
    }

    private static void spawnPatrolKnight(ServerLevel level, BlockPos pos, List<BlockPos> patrolRoute) {
        KnightEntity knight = ModEntities.KNIGHT.get().create(level);
        if (knight != null) {
            knight.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0.0F, 0.0F);
            knight.setVariant(0);
            knight.setPatrolCenter(pos);
            knight.finalizeSpawn(level, level.getCurrentDifficultyAt(pos),
                    net.minecraft.world.entity.MobSpawnType.COMMAND, null, null);
            knight.setPersistenceRequired();
            level.addFreshEntity(knight);
        }
    }

    private static List<BlockPos> calculatePatrolRoute(ServerLevel level, BlockPos center, int radius) {
        List<BlockPos> points = new ArrayList<>();
        int numPoints = 12;

        for (int i = 0; i < numPoints; i++) {
            double angle = (2 * Math.PI * i) / numPoints;
            int x = center.getX() + (int) (radius * Math.cos(angle));
            int z = center.getZ() + (int) (radius * Math.sin(angle));
            int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE_WG, x, z);
            points.add(new BlockPos(x, y, z));
        }

        LOGGER.info("[Kingdom] Маршрут патруля: {} точек, радиус {} блоков", points.size(), radius);
        return points;
    }
}
