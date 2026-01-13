package com.vladisss.kingdomswar.kingdom;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class KingdomManager {
    private static final Map<String, KingdomTerritory> kingdoms = new HashMap<>();

    public static void registerKingdom(ServerLevel level, KingdomTerritory kingdom) {
        String dimensionKey = level.dimension().location().toString();
        kingdoms.put(dimensionKey, kingdom);

        // Сохраняем в SavedData
        KingdomData data = KingdomData.get(level);
        data.setKingdom(kingdom);
        data.setDirty();
    }

    @Nullable
    public static KingdomTerritory getKingdom(Level level) {
        String dimensionKey = level.dimension().location().toString();
        return kingdoms.get(dimensionKey);
    }

    @Nullable
    public static KingdomTerritory getKingdomAt(Level level, BlockPos pos) {
        KingdomTerritory kingdom = getKingdom(level);
        if (kingdom != null && kingdom.isInTerritory(pos)) {
            return kingdom;
        }
        return null;
    }

    public static void loadKingdoms(ServerLevel level) {
        KingdomData data = KingdomData.get(level);
        KingdomTerritory kingdom = data.getKingdom();

        if (kingdom != null) {
            String dimensionKey = level.dimension().location().toString();
            kingdoms.put(dimensionKey, kingdom);
        }
    }

    public static void clearKingdoms() {
        kingdoms.clear();
    }
}
