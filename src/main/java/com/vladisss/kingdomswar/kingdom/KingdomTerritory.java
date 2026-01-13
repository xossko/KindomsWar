package com.vladisss.kingdomswar.kingdom;

import com.vladisss.kingdomswar.entity.GuardEntity;
import com.vladisss.kingdomswar.entity.KnightEntity;
import com.vladisss.kingdomswar.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ChunkPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class KingdomTerritory {

    private TerritoryScanner territoryScanner = new TerritoryScanner();


    private TerritoryIncomeSystem incomeSystem = new TerritoryIncomeSystem();

    private RevengeSystem revengeSystem; // НОВОЕ ПОЛЕ


    private static final Logger LOGGER = LoggerFactory.getLogger("KingdomsWar");
    private final KingdomAI ai = new KingdomAI();


    private long lastAIUpdate = 0;
    private int tickCounter = 0; // ✅ ДОБАВЛЕНО

    private final BlockPos centerPos;
    private final String name;
    private int radius;
    private int points;
    private final Set<ChunkPos> controlledChunks;
    private final List<String> activityLog;

    // ✅ Лимиты армии
    private static final int MAX_ACTIVE_TROOPS = 50;
    private int reserveTroops = 0;

    // ✅ Стоимость найма
    private static final int GUARD_COST = 50;
    private static final int KNIGHT_COST = 70;
    private static final int AUTO_RECRUIT_THRESHOLD = 150;
    private static final int RESERVE_FUND = 250;

    // ✅ Минимальное соотношение: 1 воин на 10 блоков радиуса
    private static final double MIN_TROOPS_PER_RADIUS = 0.1;

    // ✅ Соотношение армии: 80% рыцари, 20% стражники
    private static final double KNIGHT_RATIO = 0.8;

    private long lastRecruitmentCheck = 0;
    private long lastReinforcementCheck = 0;

    public KingdomTerritory(BlockPos centerPos, String name) {
        this.centerPos = centerPos;
        this.name = name;
        this.radius = 50;
        this.points = 0;
        this.controlledChunks = new HashSet<>();
        this.activityLog = new ArrayList<>();
        updateControlledChunks();
        this.revengeSystem = new RevengeSystem(this);

    }
    public void addPoints(int amount, String reason) {
        this.points += amount;
        addLog("+" + amount + " очков: " + reason);
        LOGGER.info("[Kingdom] {} получил {} очков ({}). Всего: {}", this.name, amount, reason, this.points);
    }


    private void addLog(String message) {
        this.activityLog.add(message);
        if (this.activityLog.size() > 50) {
            this.activityLog.remove(0);
        }
    }

    // ✅ Обновленная логика расширения с удалением старых флагов
    public void expandTerritory(ServerLevel level) {
        int troopCount = countLivingTroops(level);
        int requiredTroops = getRequiredTroopsCount();

        if (troopCount < requiredTroops) {
            LOGGER.debug("[Kingdom] Недостаточно войск для расширения");
            return;
        }

        // ✅ Проверка с учетом резерва!
        if (this.points >= getExpansionCost() + RESERVE_FUND) {
            int oldRadius = this.radius;
            this.radius += 10;
            this.points -= getExpansionCost();

            removeOldFlags(level, oldRadius);
            updateControlledChunks();

            addLog("Территория расширена с " + oldRadius + " до " + this.radius);
            LOGGER.info("[Kingdom] {} расширил территорию до {}", this.name, this.radius);
        }
    }

    // ✅ Удаление старых флагов-столбиков
    private void removeOldFlags(ServerLevel level, int oldRadius) {
        for (int angle = 0; angle < 360; angle += 45) {
            double rad = Math.toRadians(angle);
            int x = centerPos.getX() + (int) (Math.cos(rad) * oldRadius);
            int z = centerPos.getZ() + (int) (Math.sin(rad) * oldRadius);

            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    BlockPos checkPos = new BlockPos(
                            x + dx,
                            level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE_WG,
                                    x + dx, z + dz),
                            z + dz
                    );

                    for (int dy = -3; dy <= 7; dy++) {
                        BlockPos flagPos = checkPos.offset(0, dy, 0);

                        if (level.getBlockState(flagPos).is(net.minecraft.world.level.block.Blocks.OAK_FENCE) ||
                                level.getBlockState(flagPos).is(net.minecraft.world.level.block.Blocks.YELLOW_WOOL)) {
                            level.removeBlock(flagPos, false);
                        }
                    }
                }
            }
        }
    }

    // ✅ Установка флагов-столбиков на границе территории
    public void placeBorderFlags(ServerLevel level) {
        for (int angle = 0; angle < 360; angle += 45) {
            double rad = Math.toRadians(angle);
            int x = centerPos.getX() + (int) (Math.cos(rad) * radius);
            int z = centerPos.getZ() + (int) (Math.sin(rad) * radius);

            int y = level.getHeight(
                    net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE_WG,
                    x, z
            );

            BlockPos flagPos = new BlockPos(x, y, z);

            if (level.getBlockState(flagPos.below()).isSolid()) {
                for (int i = 0; i < 3; i++) {
                    level.setBlock(flagPos.above(i),
                            net.minecraft.world.level.block.Blocks.OAK_FENCE.defaultBlockState(), 3);
                }

                level.setBlock(flagPos.above(3),
                        net.minecraft.world.level.block.Blocks.YELLOW_WOOL.defaultBlockState(), 3);
            }
        }
    }

    public int getExpansionCost() {
        return 100 + (this.radius * 2);
    }

    // ✅ Подсчет живых войск
    public int countLivingTroops(ServerLevel level) {
        List<Mob> troops = level.getEntitiesOfClass(Mob.class,
                new net.minecraft.world.phys.AABB(
                        centerPos.offset(-radius, -50, -radius),
                        centerPos.offset(radius, 100, radius)
                ),
                mob -> mob instanceof GuardEntity || mob instanceof KnightEntity
        );
        return troops.size();
    }

    // ✅ Требуемое количество войск
    public int getRequiredTroopsCount() {
        return Math.max(10, (int) (radius * MIN_TROOPS_PER_RADIUS));
    }

    public void tick(ServerLevel level) {
        tickCounter++;
        if (revengeSystem != null) {
            revengeSystem.checkForRememberedPlayers(level);
        }
        // Пассивный доход от патрулирующих рыцарей
        incomeSystem.tick(level, this);

        long currentTime = level.getGameTime();

        // ✅ УМНОЕ СКАНИРОВАНИЕ каждые 2 секунды
        if (currentTime - lastAIUpdate >= 40) {
            lastAIUpdate = currentTime;

            // Старый AI для стратегии (найм, расширение)
            ai.evaluateSituation(level, this);

            // ✅ НОВЫЙ: Умное сканирование территории
            territoryScanner.scanAndAssignTroops(level, this);
        }


        // Найм каждые 5 секунд
        if (currentTime - lastRecruitmentCheck >= 100) {
            lastRecruitmentCheck = currentTime;
            ai.smartRecruit(level, this);

            // Проверка расширения
            if (ai.shouldExpand(this, level)) {
                expandTerritory(level);
            }
        }

        // Подкрепления каждые 2 секунды
        if (currentTime - lastReinforcementCheck >= 40) {
            lastReinforcementCheck = currentTime;
            checkReinforcements(level);
        }
    } // ✅ ИСПРАВЛЕНО - закрыта скобка метода tick()

    public int getControlledBlocksByKnights(ServerLevel level) {
        return (int) (this.radius * 2);
    }

    // ✅ Автоматический найм войск (с лимитом 50 активных)
    public void checkRecruitment(ServerLevel level) {
        int activeTroops = countLivingTroops(level);
        int requiredTroops = getRequiredTroopsCount();

        int knightCount = countKnights(level);
        int guardCount = countGuards(level);

        int targetKnights = (int) (requiredTroops * KNIGHT_RATIO);
        int targetGuards = requiredTroops - targetKnights;

        // ✅ ЛИМИТ: Если 50 активных - покупаем в резерв
        if (activeTroops >= MAX_ACTIVE_TROOPS) {
            if (activeTroops + reserveTroops < requiredTroops) {
                if (this.points >= KNIGHT_COST + RESERVE_FUND) {
                    this.points -= KNIGHT_COST;
                    this.reserveTroops++;
                    addLog("Нанят рыцарь в РЕЗЕРВ (-" + KNIGHT_COST + " очков). Резерв: " + reserveTroops);
                    LOGGER.info("[Kingdom] Нанят рыцарь в резерв. Всего резерва: {}", reserveTroops);
                }
            }
            return;
        }

        // Если не хватает войск и есть очки - нанимаем
        if (activeTroops < requiredTroops && activeTroops < MAX_ACTIVE_TROOPS) {
            if (knightCount < targetKnights && this.points >= KNIGHT_COST + RESERVE_FUND) {
                recruitKnight(level);
                return;
            }

            if (guardCount < targetGuards && this.points >= GUARD_COST + RESERVE_FUND) {
                recruitGuard(level);
                return;
            }
        }

        // Автонайм при избытке очков
        if (this.points >= AUTO_RECRUIT_THRESHOLD + RESERVE_FUND && activeTroops < MAX_ACTIVE_TROOPS) {
            if (knightCount < requiredTroops * 1.2 && this.points >= KNIGHT_COST + RESERVE_FUND) {
                recruitKnight(level);
            }
        }
    }

    // ✅ Проверка: нужно ли спавнить войска из резерва
    private void checkReinforcements(ServerLevel level) {
        if (reserveTroops == 0) return;

        int activeTroops = countLivingTroops(level);

        if (activeTroops < MAX_ACTIVE_TROOPS && activeTroops < getRequiredTroopsCount()) {
            recruitKnight(level);
            reserveTroops--;
            addLog("Рыцарь из резерва вступил в бой! Осталось резерва: " + reserveTroops);
            LOGGER.info("[Kingdom] Спавн рыцаря из резерва. Осталось: {}", reserveTroops);
        }
    }

    // ✅ Подсчет рыцарей
    public int countKnights(ServerLevel level) {
        List<KnightEntity> knights = level.getEntitiesOfClass(KnightEntity.class,
                new net.minecraft.world.phys.AABB(
                        centerPos.offset(-radius, -50, -radius),
                        centerPos.offset(radius, 100, radius)
                )
        );
        return knights.size();
    }

    // ✅ Подсчет стражников
    public int countGuards(ServerLevel level) {
        List<GuardEntity> guards = level.getEntitiesOfClass(GuardEntity.class,
                new net.minecraft.world.phys.AABB(
                        centerPos.offset(-radius, -50, -radius),
                        centerPos.offset(radius, 100, radius)
                )
        );
        return guards.size();
    }

    // ✅ Найм стражника (ВНУТРИ ЗАМКА!)
    public void recruitGuard(ServerLevel level) {
        BlockPos spawnPos = findSafeSpawnNearCastle(level, 20);

        if (spawnPos == null) {
            LOGGER.warn("[Kingdom] Не найдена позиция для спавна стражника!");
            return;
        }

        GuardEntity guard = ModEntities.GUARD.get().create(level);
        if (guard != null) {
            guard.moveTo(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5, 0, 0);
            guard.setGuardPos(spawnPos);
            guard.finalizeSpawn(level, level.getCurrentDifficultyAt(spawnPos),
                    net.minecraft.world.entity.MobSpawnType.COMMAND, null, null);
            guard.setPersistenceRequired();
            level.addFreshEntity(guard);

            this.points -= GUARD_COST;
            addLog("Нанят стражник в замке (-" + GUARD_COST + " очков)");
            LOGGER.info("[Kingdom] Нанят стражник в замке {}", spawnPos.toShortString());
        }
    }

    // ✅ Найм рыцаря (ПО ВСЕЙ ТЕРРИТОРИИ)
    public void recruitKnight(ServerLevel level) {
        BlockPos spawnPos = findSafeSpawnInTerritory(level);

        if (spawnPos == null) {
            LOGGER.warn("[Kingdom] Не найдена позиция для спавна рыцаря!");
            return;
        }

        KnightEntity knight = ModEntities.KNIGHT.get().create(level);
        if (knight != null) {
            knight.moveTo(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5, 0, 0);
            knight.setPatrolCenter(centerPos);
            knight.setPatrolRadius(15); // Добавьте эту строку тоже
            knight.finalizeSpawn(level, level.getCurrentDifficultyAt(spawnPos),
                    net.minecraft.world.entity.MobSpawnType.COMMAND, null, null);
            knight.setPersistenceRequired();
            level.addFreshEntity(knight);

            this.points -= KNIGHT_COST;
            addLog("Нанят рыцарь (-" + KNIGHT_COST + " очков)");
            LOGGER.info("[Kingdom] Нанят рыцарь на территории {}", spawnPos.toShortString());
        }
    }

    // ✅ Поиск позиции ВНУТРИ замка (для стражников)
    private BlockPos findSafeSpawnNearCastle(ServerLevel level, int maxRadius) {
        for (int attempt = 0; attempt < 30; attempt++) {
            double angle = level.random.nextDouble() * Math.PI * 2;
            int distance = 5 + level.random.nextInt(maxRadius - 5);

            int offsetX = (int) (Math.cos(angle) * distance);
            int offsetZ = (int) (Math.sin(angle) * distance);

            BlockPos testPos = centerPos.offset(offsetX, 0, offsetZ);
            int surfaceY = level.getHeight(
                    net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE_WG,
                    testPos.getX(),
                    testPos.getZ()
            );

            if (Math.abs(surfaceY - centerPos.getY()) > 5) {
                continue;
            }

            BlockPos spawnPos = new BlockPos(testPos.getX(), surfaceY, testPos.getZ());
            if (isValidSpawnPosition(level, spawnPos)) {
                return spawnPos;
            }
        }
        return null;
    }

    // ✅ Поиск позиции ПО ВСЕЙ ТЕРРИТОРИИ (для рыцарей)
    private BlockPos findSafeSpawnInTerritory(ServerLevel level) {
        for (int attempt = 0; attempt < 30; attempt++) {
            double angle = level.random.nextDouble() * Math.PI * 2;
            int distance = 20 + level.random.nextInt((int) (radius * 0.8));

            int offsetX = (int) (Math.cos(angle) * distance);
            int offsetZ = (int) (Math.sin(angle) * distance);

            BlockPos testPos = centerPos.offset(offsetX, 0, offsetZ);
            int surfaceY = level.getHeight(
                    net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE_WG,
                    testPos.getX(),
                    testPos.getZ()
            );

            BlockPos spawnPos = new BlockPos(testPos.getX(), surfaceY, testPos.getZ());
            if (isValidSpawnPosition(level, spawnPos)) {
                return spawnPos;
            }
        }
        return null;
    }

    // ✅ Проверка валидности позиции спавна
    private boolean isValidSpawnPosition(ServerLevel level, BlockPos pos) {
        if (!level.getBlockState(pos.below()).isSolid()) {
            return false;
        }

        if (!level.getBlockState(pos).isAir() || !level.getBlockState(pos.above()).isAir()) {
            return false;
        }

        List<Mob> nearby = level.getEntitiesOfClass(
                net.minecraft.world.entity.Mob.class,
                new net.minecraft.world.phys.AABB(pos).inflate(3.0),
                entity -> entity instanceof GuardEntity || entity instanceof KnightEntity
        );

        return nearby.size() < 2;
    }

    private void updateControlledChunks() {
        this.controlledChunks.clear();
        int chunkRadius = this.radius / 16;
        ChunkPos centerChunk = new ChunkPos(this.centerPos);

        for (int x = -chunkRadius; x <= chunkRadius; x++) {
            for (int z = -chunkRadius; z <= chunkRadius; z++) {
                this.controlledChunks.add(new ChunkPos(centerChunk.x + x, centerChunk.z + z));
            }
        }
    }

    public boolean isInTerritory(BlockPos pos) {
        return pos.distSqr(this.centerPos) <= (this.radius * this.radius);
    }

    // Getters
    public BlockPos getCastleCenter() { return centerPos; } // ✅ ИСПРАВЛЕНО имя метода
    public String getName() { return name; }
    public int getRadius() { return radius; }
    public int getPoints() { return points; }
    public int getControlledChunksCount() { return controlledChunks.size(); }
    public List<String> getActivityLog() { return new ArrayList<>(activityLog); }
    public RevengeSystem getRevengeSystem() {
        return revengeSystem;
    }



    // NBT сохранение
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("name", this.name);
        tag.putInt("centerX", this.centerPos.getX());
        tag.putInt("centerY", this.centerPos.getY());
        tag.putInt("centerZ", this.centerPos.getZ());
        tag.putInt("radius", this.radius);
        tag.putInt("points", this.points);
        tag.putInt("reserveTroops", this.reserveTroops);

        ListTag logTag = new ListTag();
        for (String log : this.activityLog) {
            CompoundTag entry = new CompoundTag();
            entry.putString("message", log);
            logTag.add(entry);
        }

        tag.put("log", logTag);
        return tag;
    }

    public static KingdomTerritory load(CompoundTag tag) {
        BlockPos center = new BlockPos(
                tag.getInt("centerX"),
                tag.getInt("centerY"),
                tag.getInt("centerZ")
        );

        KingdomTerritory kingdom = new KingdomTerritory(center, tag.getString("name"));
        kingdom.radius = tag.getInt("radius");
        kingdom.points = tag.getInt("points");
        kingdom.reserveTroops = tag.getInt("reserveTroops");

        ListTag logTag = tag.getList("log", Tag.TAG_COMPOUND);
        for (int i = 0; i < logTag.size(); i++) {
            CompoundTag entry = logTag.getCompound(i);
            kingdom.activityLog.add(entry.getString("message"));
        }

        kingdom.updateControlledChunks();
        return kingdom;
    }
}
