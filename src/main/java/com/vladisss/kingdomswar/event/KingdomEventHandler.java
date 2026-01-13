package com.vladisss.kingdomswar.event;

import com.vladisss.kingdomswar.entity.GuardEntity;
import com.vladisss.kingdomswar.entity.KnightEntity;
import com.vladisss.kingdomswar.kingdom.KingdomManager;
import com.vladisss.kingdomswar.kingdom.KingdomTerritory;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

@Mod.EventBusSubscriber(modid = "kingdomswar", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class KingdomEventHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger("KingdomsWar");

    @SubscribeEvent
    public static void onEntityDeath(LivingDeathEvent event) {
        if (!(event.getEntity().level() instanceof ServerLevel level)) {
            return;
        }

        KingdomTerritory kingdom = KingdomManager.getKingdom(level);
        if (kingdom == null) {
            return;
        }

        LivingEntity killed = event.getEntity();

        // ============================================
        // 1. ВОИН УБИЛ МОБА → дать очки королевству
        // ============================================
        if (event.getSource().getEntity() instanceof GuardEntity ||
                event.getSource().getEntity() instanceof KnightEntity) {

            if (killed instanceof Mob) {
                int oldRadius = kingdom.getRadius();
                int points = Math.max(1, (int) (killed.getMaxHealth() / 10.0));
                String killedName = killed.getType().getDescription().getString();
                kingdom.addPoints(points, "убит " + killedName);
                kingdom.expandTerritory(level);

                if (kingdom.getRadius() > oldRadius) {
                    kingdom.placeBorderFlags(level);
                }

                LOGGER.debug("[Kingdom] {} убит → {} HP → {} очков",
                        killedName, killed.getMaxHealth(), points);
            }
        }

        // ============================================
        // 2. ВОИНА УБИЛИ → вызвать подкрепления!
        // ============================================
        if (killed instanceof GuardEntity || killed instanceof KnightEntity) {
            LivingEntity killer = null;

            if (event.getSource().getEntity() instanceof net.minecraft.world.entity.player.Player player) {
                // Активируем систему мести
                kingdom.getRevengeSystem().onWarriorKilled(level, (Mob) killed, player.getUUID());
            }

            // Определяем убийцу
            if (event.getSource().getDirectEntity() instanceof LivingEntity) {
                killer = (LivingEntity) event.getSource().getDirectEntity();
            } else if (event.getSource().getEntity() instanceof LivingEntity) {
                killer = (LivingEntity) event.getSource().getEntity();
            }

            if (killer != null) {
                // Проверяем расстояние до замка
                BlockPos deathPos = killed.blockPosition();
                BlockPos castlePos = kingdom.getCastleCenter();
                double distanceToCastle = deathPos.distSqr(castlePos);

                String warriorType = killed instanceof GuardEntity ? "Стражник" : "Рыцарь";
                LOGGER.warn("[Kingdom] 🚨 {} УБИТ в {}! Убийца: {}",
                        warriorType, deathPos.toShortString(),
                        killer.getType().getDescription().getString());

                // Вызываем подкрепления
                callReinforcements(level, kingdom, deathPos, castlePos, killer, distanceToCastle);
            }
        }
    }

    // ============================================
    // СИСТЕМА ПОДКРЕПЛЕНИЙ
    // ============================================
    private static void callReinforcements(ServerLevel level, KingdomTerritory kingdom,
                                           BlockPos deathPos, BlockPos castlePos,
                                           LivingEntity killer, double distanceToCastle) {

        int radius = kingdom.getRadius();

        // Находим всех СВОБОДНЫХ воинов (без цели)
        List<Mob> availableTroops = new ArrayList<>();

        // Стражники
        List<GuardEntity> guards = level.getEntitiesOfClass(GuardEntity.class,
                new AABB(
                        castlePos.offset(-radius, -50, -radius),
                        castlePos.offset(radius, 100, radius)
                ),
                guard -> guard.getTarget() == null && guard.isAlive()
        );
        availableTroops.addAll(guards);

        // Рыцари
        List<KnightEntity> knights = level.getEntitiesOfClass(KnightEntity.class,
                new AABB(
                        castlePos.offset(-radius, -50, -radius),
                        castlePos.offset(radius, 100, radius)
                ),
                knight -> knight.getTarget() == null && knight.isAlive()
        );
        availableTroops.addAll(knights);

        if (availableTroops.isEmpty()) {
            LOGGER.error("[Kingdom] ⚠️ Нет свободных войск для подкрепления!");
            return;
        }

        // Определяем сколько войск отправить
        int troopsToSend;
        if (distanceToCastle < 30 * 30) { // Близко к замку (< 30 блоков)
            // КРИТИЧЕСКАЯ УГРОЗА → 50% свободных войск
            troopsToSend = Math.max(3, availableTroops.size() / 2);
            LOGGER.error("[Kingdom] 🔴 КРИТИЧЕСКАЯ УГРОЗА У ЗАМКА! Отправляем {} войск (50%)", troopsToSend);
        } else if (distanceToCastle < 60 * 60) { // Средняя дистанция (30-60 блоков)
            // Средняя угроза → 5 войск
            troopsToSend = Math.min(5, availableTroops.size());
            LOGGER.warn("[Kingdom] 🟠 Угроза в территории! Отправляем {} войск", troopsToSend);
        } else {
            // Далеко → 3 война
            troopsToSend = Math.min(3, availableTroops.size());
            LOGGER.info("[Kingdom] 🟡 Дальняя угроза. Отправляем {} войск", troopsToSend);
        }

        // Отправляем подкрепления
        List<Mob> reinforcements = availableTroops.subList(0, Math.min(troopsToSend, availableTroops.size()));

        for (Mob troop : reinforcements) {
            // Устанавливаем цель = убийца
            if (killer.isAlive() && !killer.isRemoved()) {
                troop.setTarget(killer);
                troop.getNavigation().moveTo(killer, 1.5D); // Быстрая скорость!

                LOGGER.info("[Kingdom] → {} #{} бежит мстить!",
                        troop instanceof GuardEntity ? "Стражник" : "Рыцарь",
                        troop.getId());
            } else {
                // Убийца мертв/исчез → идем к месту смерти и патрулируем
                troop.getNavigation().moveTo(deathPos.getX(), deathPos.getY(), deathPos.getZ(), 1.2D);
                LOGGER.info("[Kingdom] → {} #{} патрулирует место смерти",
                        troop instanceof GuardEntity ? "Стражник" : "Рыцарь",
                        troop.getId());
            }
        }

        LOGGER.warn("[Kingdom] ✅ ПОДКРЕПЛЕНИЕ ОТПРАВЛЕНО: {} войск → {}",
                reinforcements.size(), deathPos.toShortString());
    }
}
