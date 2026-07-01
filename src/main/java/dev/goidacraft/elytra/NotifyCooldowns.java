package dev.goidacraft.elytra;

import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Transient per-player state: notification anti-spam and glide-start detection.
 * Replaces storing cooldowns in persistentData (NBT) from the old KubeJS script.
 */
public final class NotifyCooldowns {

    private static final Map<UUID, Map<String, Long>> LAST_NOTIFY = new ConcurrentHashMap<>();
    private static final Set<UUID> WAS_FALL_FLYING = ConcurrentHashMap.newKeySet();

    private NotifyCooldowns() {
    }

    /**
     * @return true if at least cooldownTicks have passed since the last notification with this key
     *         (in which case the timer is reset).
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
     * Updates the flight flag and reports whether this was a "not flying -> started gliding" transition.
     *
     * @return true only on the tick when the player started gliding.
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

    /** Clears state when a player logs out. */
    public static void clear(UUID id) {
        LAST_NOTIFY.remove(id);
        WAS_FALL_FLYING.remove(id);
    }
}
