package fr.vortexsmp.vortexessentials.limit;

import fr.vortexsmp.vortexessentials.YamlStore;
import org.bukkit.Registry;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Limites par enchantement. Format de limits.yml identique à VortexLimit (enchantments.<clé>). */
public final class LimitManager {
    public record Limit(boolean enabled, int maxLevel) {}

    private final YamlStore store;
    private final Map<Enchantment, Limit> limits = new LinkedHashMap<>();

    public LimitManager(YamlStore store) {
        this.store = store;
        load();
    }

    public void load() {
        limits.clear();
        List<Enchantment> all = new ArrayList<>();
        Registry.ENCHANTMENT.forEach(all::add);
        all.sort(Comparator.comparing((Enchantment e) -> e.getKey().getKey()));

        FileConfiguration c = store.get();
        for (Enchantment e : all) {
            String p = "enchantments." + e.getKey().getKey();
            boolean enabled = c.getBoolean(p + ".enabled", true);
            int max = c.getInt(p + ".max-level", e.getMaxLevel());
            limits.put(e, new Limit(enabled, Math.max(0, max)));
        }
    }

    public void save() {
        FileConfiguration c = store.get();
        for (Map.Entry<Enchantment, Limit> x : limits.entrySet()) {
            String p = "enchantments." + x.getKey().getKey().getKey();
            c.set(p + ".enabled", x.getValue().enabled());
            c.set(p + ".max-level", x.getValue().maxLevel());
        }
        store.save();
    }

    public List<Enchantment> all() { return new ArrayList<>(limits.keySet()); }

    public Enchantment find(String key) {
        for (Enchantment e : limits.keySet()) if (e.getKey().getKey().equalsIgnoreCase(key)) return e;
        return null;
    }

    public Limit get(Enchantment e) {
        return limits.getOrDefault(e, new Limit(true, e.getMaxLevel()));
    }

    public void toggle(Enchantment e) {
        Limit l = get(e);
        limits.put(e, new Limit(!l.enabled(), l.maxLevel()));
    }

    public void setMax(Enchantment e, int n) {
        Limit l = get(e);
        limits.put(e, new Limit(l.enabled(), Math.max(0, Math.min(255, n))));
    }

    public void reset(Enchantment e) {
        limits.put(e, new Limit(true, e.getMaxLevel()));
    }

    /**
     * Niveau autorisé pour cet enchantement : 0 = à retirer (désactivé ou max à 0),
     * sinon le niveau plafonné au maximum configuré.
     */
    public int clamp(Enchantment e, int level) {
        Limit l = get(e);
        if (!l.enabled() || l.maxLevel() <= 0) return 0;
        return Math.min(level, l.maxLevel());
    }
}
