package fr.vortexsmp.vortexessentials.admin;

import fr.vortexsmp.vortexessentials.YamlStore;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.Plugin;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Données d'administration (admin.yml) : items/crafts/potions désactivés, limites d'objets. */
public final class AdminManager {
    private final YamlStore store;
    private final Set<Material> items = new LinkedHashSet<>();
    private final Set<Material> crafts = new LinkedHashSet<>();
    private final Set<String> potions = new LinkedHashSet<>();
    private final Map<Material, Integer> limits = new LinkedHashMap<>();

    public AdminManager(Plugin plugin) {
        this.store = new YamlStore(plugin, "admin.yml");
        load();
    }

    public void load() {
        store.reload();
        items.clear();
        crafts.clear();
        potions.clear();
        limits.clear();
        for (String s : store.get().getStringList("disabled-items")) add(items, s);
        for (String s : store.get().getStringList("disabled-crafts")) add(crafts, s);
        potions.addAll(store.get().getStringList("disabled-potions"));
        ConfigurationSection ls = store.get().getConfigurationSection("item-limits");
        if (ls != null) {
            for (String k : ls.getKeys(false)) {
                Material m = Material.matchMaterial(k);
                if (m != null && ls.getInt(k) > 0) limits.put(m, ls.getInt(k));
            }
        }
    }

    private static void add(Set<Material> set, String name) {
        Material m = Material.matchMaterial(name);
        if (m != null) set.add(m);
    }

    public void save() {
        store.get().set("disabled-items", items.stream().map(Material::name).toList());
        store.get().set("disabled-crafts", crafts.stream().map(Material::name).toList());
        store.get().set("disabled-potions", potions.stream().toList());
        store.get().set("item-limits", null);
        for (Map.Entry<Material, Integer> e : limits.entrySet()) store.get().set("item-limits." + e.getKey().name(), e.getValue());
        store.save();
    }

    public Set<Material> items() { return items; }
    public Set<Material> crafts() { return crafts; }
    public Set<String> potions() { return potions; }
    public Map<Material, Integer> limits() { return limits; }

    public boolean itemDisabled(Material m) { return items.contains(m); }
    public boolean craftDisabled(Material m) { return crafts.contains(m); }
    public boolean potionDisabled(String id) { return potions.contains(id); }
    public int limitOf(Material m) { return limits.getOrDefault(m, 0); }

    /** Bascule et renvoie le nouvel état (true = désactivé). */
    public boolean toggle(Set<Material> set, Material m) {
        boolean now = set.add(m);
        if (!now) set.remove(m);
        save();
        return now;
    }

    public boolean togglePotion(String id) {
        boolean now = potions.add(id);
        if (!now) potions.remove(id);
        save();
        return now;
    }

    public void setLimit(Material m, int n) {
        if (n <= 0) limits.remove(m);
        else limits.put(m, n);
        save();
    }
}
