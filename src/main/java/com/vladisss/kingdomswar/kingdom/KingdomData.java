package com.vladisss.kingdomswar.kingdom;

import com.vladisss.kingdomswar.KingdomsWarMod;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nullable;

public class KingdomData extends SavedData {
    private static final String DATA_NAME = KingdomsWarMod.MODID + "_kingdom_data";

    @Nullable
    private KingdomTerritory kingdom;

    public KingdomData() {
    }

    public static KingdomData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                KingdomData::load,
                KingdomData::new,
                DATA_NAME
        );
    }

    public static KingdomData load(CompoundTag tag) {
        KingdomData data = new KingdomData();

        if (tag.contains("Kingdom")) {
            data.kingdom = KingdomTerritory.load(tag.getCompound("Kingdom"));
        }

        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        if (kingdom != null) {
            tag.put("Kingdom", kingdom.save());
        }
        return tag;
    }

    public void setKingdom(KingdomTerritory kingdom) {
        this.kingdom = kingdom;
        setDirty();
    }

    @Nullable
    public KingdomTerritory getKingdom() {
        return kingdom;
    }
}
