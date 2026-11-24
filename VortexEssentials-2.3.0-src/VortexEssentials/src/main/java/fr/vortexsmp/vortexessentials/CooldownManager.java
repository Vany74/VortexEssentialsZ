package fr.vortexsmp.vortexessentials;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Cooldowns par joueur et par clé (ex: "excalibur", "sealord_ultimate"). */
public final class CooldownManager {
    private final Map<UUID, Map<String, Long>> map = new ConcurrentHashMap<>();

    public boolean ready(UUID id, String key) {
        return remainingMs(id, key) <= 0;
    }

    public long remainingMs(UUID id, String key) {
        Map<String, Long> m = map.get(id);
        if (m == null) return 0;
        Long until = m.get(key);
        return until == null ? 0 : Math.max(0, until - System.currentTimeMillis());
    }

    public void start(UUID id, String key, long ms) {
        map.computeIfAbsent(id, k -> new ConcurrentHashMap<>()).put(key, System.currentTimeMillis() + ms);
    }

    /** Si le cooldown est écoulé, le relance et renvoie true. Sinon renvoie false. */
    public boolean tryUse(UUID id, String key, long ms) {
        if (!ready(id, key)) return false;
        start(id, key, ms);
        return true;
    }

    public void clear(UUID id) {
        map.remove(id);
    }
}
