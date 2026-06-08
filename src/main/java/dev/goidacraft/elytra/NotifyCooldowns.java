package dev.goidacraft.elytra;

import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Транзиентное состояние per-player: анти-спам сообщений и детект начала планирования.
 * Заменяет хранение кулдаунов в persistentData (NBT) из старого KubeJS-скрипта.
 */
public final class NotifyCooldowns {

    private static final Map<UUID, Map<String, Long>> LAST_NOTIFY = new ConcurrentHashMap<>();
    private static final Set<UUID> WAS_FALL_FLYING = ConcurrentHashMap.newKeySet();

    private NotifyCooldowns() {
    }

    /**
     * @return true, если с момента последнего сообщения с этим ключом прошло >= cooldownTicks
     *         (и тогда таймер обновляется).
     */
    public static boolean canNotify(ServerPlayer player, String key, int cooldownTicks) {
        long now = player.serverLevel().getGameTime();
        Map<String, Long> m = LAST_NOTIFY.computeIfAbsent(player.getUUID(), u -> new HashMap<>());
        Long last = m.get(key);
        if (last == null || now - last >= cooldownTicks) {
            m.put(key, now);
            return true;
        }
        return false;
    }

    /**
     * Обновляет флаг полёта и сообщает, был ли это переход «не летел -> начал планировать».
     *
     * @return true только в тот тик, когда игрок начал планирование.
     */
    public static boolean startedFlying(ServerPlayer player, boolean isFlyingNow) {
        UUID id = player.getUUID();
        boolean was = WAS_FALL_FLYING.contains(id);
        if (isFlyingNow) {
            WAS_FALL_FLYING.add(id);
        } else {
            WAS_FALL_FLYING.remove(id);
        }
        return isFlyingNow && !was;
    }

    /** Очистка состояния при выходе игрока. */
    public static void clear(UUID id) {
        LAST_NOTIFY.remove(id);
        WAS_FALL_FLYING.remove(id);
    }
}
