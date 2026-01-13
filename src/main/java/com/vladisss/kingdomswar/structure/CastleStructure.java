package com.vladisss.kingdomswar.structure;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

public class CastleStructure {
    private static final Logger LOGGER = LoggerFactory.getLogger("KingdomsWar");

    private void placeFromNBT(ServerLevel level, BlockPos center) {
        // Загружаем NBT структуру
        ResourceLocation structureLocation = new ResourceLocation("kingdomswar", "my_castle");

        StructureTemplateManager structureManager = level.getStructureManager();
        Optional<StructureTemplate> template = structureManager.get(structureLocation);

        if (template.isPresent()) {
            StructurePlaceSettings settings = new StructurePlaceSettings()
                    .setRotation(Rotation.NONE)
                    .setMirror(Mirror.NONE)
                    .setIgnoreEntities(false);

            template.get().placeInWorld(
                    level,
                    center,
                    center,
                    settings,
                    level.random,
                    Block.UPDATE_ALL
            );

            LOGGER.info("Castle placed from NBT at {}", center);
        } else {
            LOGGER.error("Castle structure not found!");
        }
    }


    public static void generate(ServerLevel level, BlockPos center) {
        int size = 15;
        int wallHeight = 8;
        int towerHeight = 12;

        LOGGER.info("[Castle] Начало генерации замка в {}", center.toShortString());

        // ✅ 1. ЗАПОЛНЕНИЕ ПУСТОТ под замком (от bedrock до поверхности)
        fillUnderground(level, center, size + 5, 20);

        // ✅ 2. РОВНАЯ ПЛАТФОРМА под замком
        createFlatPlatform(level, center, size + 3);

        // ✅ 3. ПЛАВНЫЙ СКЛОН вместо террас
        createSmoothSlope(level, center, size + 3, size + 12);

        // ✅ 4. ПОЛНАЯ ОЧИСТКА воздуха внутри
        clearCastleArea(level, center, size + 2, wallHeight + 5);

        // 5. ПОЛ внутри замка
        buildFloor(level, center, size);

        // 6. Постройка замка
        buildWalls(level, center, size, wallHeight);
        buildTower(level, center.offset(-size, 0, -size), towerHeight);
        buildTower(level, center.offset(size, 0, -size), towerHeight);
        buildTower(level, center.offset(-size, 0, size), towerHeight);
        buildTower(level, center.offset(size, 0, size), towerHeight);
        buildGate(level, center.offset(0, 0, -size), wallHeight);
        clearCourtyard(level, center, size - 2, wallHeight);
        buildKeep(level, center, towerHeight + 4);

        LOGGER.info("[Castle] ✅ Замок построен!");
    }

    // ✅ НОВОЕ: Заполнение всех пустот под замком (пещеры, равнины)
    private static void fillUnderground(ServerLevel level, BlockPos center, int radius, int depth) {
        LOGGER.info("[Castle] Заполнение пустот под замком (глубина: {})", depth);

        BlockState stone = Blocks.STONE.defaultBlockState();
        BlockState deepslate = Blocks.DEEPSLATE.defaultBlockState();
        int filled = 0;

        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                // Заполняем от центра вниз на 20 блоков
                for (int y = -1; y >= -depth; y--) {
                    BlockPos pos = center.offset(x, y, z);
                    BlockState current = level.getBlockState(pos);

                    // Заполняем только воздух и пустоты
                    if (current.isAir() ||
                            current.is(Blocks.CAVE_AIR) ||
                            current.is(Blocks.WATER) ||
                            current.is(Blocks.LAVA)) {

                        // Глубже -10 блоков - deepslate, выше - stone
                        if (y < -10) {
                            level.setBlock(pos, deepslate, 3);
                        } else {
                            level.setBlock(pos, stone, 3);
                        }
                        filled++;
                    }
                }
            }
        }

        LOGGER.info("[Castle] ✅ Пустоты заполнены ({} блоков)", filled);
    }

    // ✅ НОВОЕ: Ровная платформа (БЕЗ террас)
    private static void createFlatPlatform(ServerLevel level, BlockPos center, int radius) {
        LOGGER.info("[Castle] Создание ровной платформы (радиус: {})", radius);

        BlockState grass = Blocks.GRASS_BLOCK.defaultBlockState();
        BlockState dirt = Blocks.DIRT.defaultBlockState();
        int cleared = 0;
        int filled = 0;

        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                int surfaceY = level.getHeight(
                        net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE_WG,
                        center.getX() + x,
                        center.getZ() + z
                );

                // ВЫШЕ центра - удаляем до уровня
                if (surfaceY > center.getY()) {
                    for (int y = surfaceY; y >= center.getY(); y--) {
                        level.setBlock(new BlockPos(center.getX() + x, y, center.getZ() + z),
                                Blocks.AIR.defaultBlockState(), 3);
                        cleared++;
                    }
                }

                // НИЖЕ центра - заполняем
                if (surfaceY < center.getY()) {
                    for (int y = surfaceY; y < center.getY(); y++) {
                        BlockState block = (y == center.getY() - 1) ? grass : dirt;
                        level.setBlock(new BlockPos(center.getX() + x, y, center.getZ() + z),
                                block, 3);
                        filled++;
                    }
                }

                // Поверхность - трава
                level.setBlock(new BlockPos(center.getX() + x, center.getY() - 1, center.getZ() + z),
                        grass, 3);
            }
        }

        LOGGER.info("[Castle] Платформа готова (удалено: {}, заполнено: {})", cleared, filled);
    }

    // ✅ НОВОЕ: Плавный склон (вместо резких террас)
    private static void createSmoothSlope(ServerLevel level, BlockPos center, int innerRadius, int outerRadius) {
        LOGGER.info("[Castle] Создание плавного склона");

        BlockState grass = Blocks.GRASS_BLOCK.defaultBlockState();
        BlockState dirt = Blocks.DIRT.defaultBlockState();
        int smoothed = 0;

        for (int x = -outerRadius; x <= outerRadius; x++) {
            for (int z = -outerRadius; z <= outerRadius; z++) {
                double distance = Math.sqrt(x * x + z * z);

                // Только в зоне склона
                if (distance < innerRadius || distance > outerRadius) {
                    continue;
                }

                // Плавное снижение (1 блок на каждые 3 блока расстояния)
                double ratio = (distance - innerRadius) / (outerRadius - innerRadius);
                int targetY = center.getY() - (int) (ratio * 3); // Спуск на 3 блока

                int surfaceY = level.getHeight(
                        net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE_WG,
                        center.getX() + x,
                        center.getZ() + z
                );

                // Выравнивание до целевой высоты
                if (Math.abs(surfaceY - targetY) > 1) {
                    // Удаляем лишнее
                    if (surfaceY > targetY) {
                        for (int y = surfaceY; y > targetY; y--) {
                            level.setBlock(new BlockPos(center.getX() + x, y, center.getZ() + z),
                                    Blocks.AIR.defaultBlockState(), 3);
                        }
                    }

                    // Заполняем недостающее
                    if (surfaceY < targetY) {
                        for (int y = surfaceY; y <= targetY; y++) {
                            BlockState block = (y == targetY) ? grass : dirt;
                            level.setBlock(new BlockPos(center.getX() + x, y, center.getZ() + z),
                                    block, 3);
                        }
                    }

                    // Трава на поверхности
                    level.setBlock(new BlockPos(center.getX() + x, targetY, center.getZ() + z),
                            grass, 3);
                    smoothed++;
                }
            }
        }

        LOGGER.info("[Castle] Склон создан ({} блоков)", smoothed);
    }

    // ✅ Полная очистка территории
    private static void clearCastleArea(ServerLevel level, BlockPos center, int radius, int height) {
        LOGGER.info("[Castle] Очистка территории замка");

        int cleared = 0;

        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                for (int y = 0; y <= height; y++) {
                    BlockPos pos = center.offset(x, y, z);
                    BlockState state = level.getBlockState(pos);

                    // Удаляем всё кроме камня замка
                    if (!state.isAir() &&
                            !state.is(Blocks.STONE_BRICKS) &&
                            !state.is(Blocks.STONE_BRICK_STAIRS) &&
                            !state.is(Blocks.CHISELED_STONE_BRICKS)) {

                        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                        cleared++;
                    }
                }
            }
        }

        LOGGER.info("[Castle] Очищено {} блоков", cleared);
    }

    // ✅ Пол внутри замка
    private static void buildFloor(ServerLevel level, BlockPos center, int size) {
        BlockState floor = Blocks.STONE_BRICKS.defaultBlockState();

        for (int x = -size; x <= size; x++) {
            for (int z = -size; z <= size; z++) {
                level.setBlock(center.offset(x, -1, z), floor, 3);
                level.setBlock(center.offset(x, 0, z), floor, 3);
            }
        }
    }

    private static void buildWalls(ServerLevel level, BlockPos center, int size, int height) {
        BlockState wall = Blocks.STONE_BRICKS.defaultBlockState();
        BlockState battlement = Blocks.STONE_BRICK_STAIRS.defaultBlockState();

        for (int y = 0; y < height; y++) {
            for (int x = -size; x <= size; x++) {
                level.setBlock(center.offset(x, y, -size), wall, 3);
                level.setBlock(center.offset(x, y, size), wall, 3);
            }

            for (int z = -size; z <= size; z++) {
                level.setBlock(center.offset(-size, y, z), wall, 3);
                level.setBlock(center.offset(size, y, z), wall, 3);
            }
        }

        for (int x = -size; x <= size; x += 2) {
            level.setBlock(center.offset(x, height, -size), battlement, 3);
            level.setBlock(center.offset(x, height, size), battlement, 3);
        }

        for (int z = -size; z <= size; z += 2) {
            level.setBlock(center.offset(-size, height, z), battlement, 3);
            level.setBlock(center.offset(size, height, z), battlement, 3);
        }
    }

    private static void buildTower(ServerLevel level, BlockPos base, int height) {
        BlockState tower = Blocks.STONE_BRICKS.defaultBlockState();
        int radius = 3;

        for (int y = 0; y < height; y++) {
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    if (x * x + z * z <= radius * radius) {
                        level.setBlock(base.offset(x, y, z), tower, 3);
                    }
                }
            }
        }

        level.setBlock(base.offset(0, height, 0), Blocks.STONE_BRICK_SLAB.defaultBlockState(), 3);
        level.setBlock(base.offset(0, height - 2, 0), Blocks.TORCH.defaultBlockState(), 3);
    }

    private static void buildGate(ServerLevel level, BlockPos gatePos, int height) {
        for (int x = -1; x <= 1; x++) {
            for (int y = 0; y < height - 2; y++) {
                level.setBlock(gatePos.offset(x, y, 0), Blocks.AIR.defaultBlockState(), 3);
            }
        }

        level.setBlock(gatePos.offset(0, 0, 0), Blocks.IRON_BARS.defaultBlockState(), 3);
    }

    private static void clearCourtyard(ServerLevel level, BlockPos center, int size, int height) {
        for (int x = -size; x <= size; x++) {
            for (int z = -size; z <= size; z++) {
                for (int y = 1; y < height; y++) {
                    level.setBlock(center.offset(x, y, z), Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
    }

    private static void buildKeep(ServerLevel level, BlockPos center, int height) {
        BlockState keep = Blocks.CHISELED_STONE_BRICKS.defaultBlockState();
        int size = 4;

        for (int y = 0; y < height; y++) {
            for (int x = -size; x <= size; x++) {
                for (int z = -size; z <= size; z++) {
                    if (Math.abs(x) == size || Math.abs(z) == size) {
                        level.setBlock(center.offset(x, y, z), keep, 3);
                    }
                }
            }
        }

        for (int x = -size; x <= size; x++) {
            for (int z = -size; z <= size; z++) {
                level.setBlock(center.offset(x, height, z), Blocks.STONE_BRICK_SLAB.defaultBlockState(), 3);
            }
        }
    }
}
