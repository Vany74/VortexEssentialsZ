package fr.vortexsmp.vortexessentials.limit;

import fr.vortexsmp.vortexessentials.YamlStore;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Limites par effet de potion. Format de limits.yml identique à VortexLimit (potions.<id>). */
public final class PotionManager {
    public record PotionRule(boolean enabled, int maxAmplifier, int maxDurationSeconds) {}

    private static final PotionRule DEFAULT = new PotionRule(true, 1, 3600);

    private static final List<String> POTIONS = List.of(
            "speed", "slowness", "haste", "mining_fatigue", "strength", "instant_health", "instant_damage",
            "jump_boost", "nausea", "regeneration", "resistance", "fire_resistance", "water_breathing",
            "invisibility", "blindness", "night_vision", "hunger", "weakness", "poison", "wither",
            "health_boost", "absorption", "saturation", "glowing", "levitation", "luck", "unluck",
            "slow_falling", "conduit_power", "dolphins_grace", "bad_omen", "hero_of_the_village",
            "darkness", "trial_omen", "raid_omen", "wind_charged", "weaving", "oozing", "infested");

    private final YamlStore store;
    private final Map<String, PotionRule> rules = new LinkedHashMap<>();

    public PotionManager(YamlStore store) {
        this.store = store;
        load();
    }

    public void load() {
        rules.clear();
        FileConfiguration c = store.get();
        for (String id : POTIONS) {
            String p = "potions." + id;
            rules.put(id, new PotionRule(
                    c.getBoolean(p + ".enabled", true),
                    Math.max(0, c.getInt(p + ".max-amplifier", DEFAULT.maxAmplifier())),
                    Math.max(0, c.getInt(p + ".max-duration-seconds", DEFAULT.maxDurationSeconds()))));
        }
    }

    public void save() {
        FileConfiguration c = store.get();
        for (Map.Entry<String, PotionRule> x : rules.entrySet()) {
            String p = "potions." + x.getKey();
            c.set(p + ".enabled", x.getValue().enabled());
            c.set(p + ".max-amplifier", x.getValue().maxAmplifier());
            c.set(p + ".max-duration-seconds", x.getValue().maxDurationSeconds());
        }
        store.save();
    }

    public List<String> all() { return new ArrayList<>(rules.keySet()); }

    public PotionRule get(String id) { return rules.getOrDefault(id, DEFAULT); }

    public void toggle(String id) {
        PotionRule r = get(id);
        rules.put(id, new PotionRule(!r.enabled(), r.maxAmplifier(), r.maxDurationSeconds()));
    }

    public void setAmplifier(String id, int n) {
        PotionRule r = get(id);
        rules.put(id, new PotionRule(r.enabled(), Math.max(0, Math.min(255, n)), r.maxDurationSeconds()));
    }

    public void setDuration(String id, int n) {
        PotionRule r = get(id);
        rules.put(id, new PotionRule(r.enabled(), r.maxAmplifier(), Math.max(0, n)));
    }

    public void reset(String id) { rules.put(id, DEFAULT); }
}
