package fr.vortexsmp.vortexessentials.boost;

import fr.vortexsmp.vortexessentials.VortexEssentials;
import fr.vortexsmp.vortexessentials.YamlStore;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Boosts par groupe + événements temporaires.
 * Les groupes viennent de config.yml (boosts.groups) et de boosts.yml (créés/modifiés en jeu ; ce fichier prime).
 * Un joueur est dans un groupe s'il a la permission du groupe ("group.<nom>" par défaut, donnée par LuckPerms).
 * Les événements (ex. XP x2 pendant 1 h) s'appliquent à tous les joueurs et se multiplient aux boosts de groupe.
 */
public final class BoostManager {
    public enum Stat { XP, DROPS, DAMAGE, POTION }

    public record Group(String id, String permission, double xp, double drops, double damage,
                        double potionDuration, double cooldownReduction, Map<PotionEffectType, Integer> effects,
                        double sellBonus, double buyDiscount) {}

    public record Event(String stat, double multiplier, long until) {}

    public static final List<String> STAT_KEYS = List.of("xp", "drops", "damage", "potion", "cooldown", "sell", "discount");
    public static final List<String> EVENT_KEYS = List.of("xp", "drops", "damage", "potion", "cooldown", "sell", "buy");

    private final VortexEssentials plugin;
    private final YamlStore store;
    private final Map<String, Group> groups = new LinkedHashMap<>();
    private final Map<String, Event> events = new LinkedHashMap<>();
    private String stacking = "highest";
    private boolean enabled = true;
    private BukkitRunnable task;

    public BoostManager(VortexEssentials plugin) {
        this.plugin = plugin;
        this.store = new YamlStore(plugin, "boosts.yml");
        load();
    }

    // ═════════════ Chargement / sauvegarde ═════════════

    public void load() {
        store.reload();
        groups.clear();
        enabled = plugin.getConfig().getBoolean("boosts.enabled", true);
        stacking = plugin.getConfig().getString("boosts.stacking", "highest").toLowerCase(Locale.ROOT);

        ConfigurationSection root = plugin.getConfig().getConfigurationSection("boosts.groups");
        if (root != null) {
            for (String id : root.getKeys(false)) {
                ConfigurationSection s = root.getConfigurationSection(id);
                if (s != null) groups.put(id, parse(id, s));
            }
        }
        ConfigurationSection mine = store.get().getConfigurationSection("groups");
        if (mine != null) {
            for (String id : mine.getKeys(false)) {
                ConfigurationSection s = mine.getConfigurationSection(id);
                if (s != null) groups.put(id, parse(id, s));
            }
        }
        for (String id : store.get().getStringList("deleted")) groups.remove(id);
        restartTask();
    }

    private Group parse(String id, ConfigurationSection s) {
        Map<PotionEffectType, Integer> fx = new LinkedHashMap<>();
        ConfigurationSection es = s.getConfigurationSection("effects");
        if (es != null) {
            for (String name : es.getKeys(false)) {
                PotionEffectType t = PotionEffectType.getByKey(NamespacedKey.minecraft(name.toLowerCase(Locale.ROOT)));
                if (t == null) {
                    plugin.getLogger().warning("Boost '" + id + "' : effet inconnu '" + name + "'");
                    continue;
                }
                fx.put(t, Math.max(1, es.getInt(name, 1)) - 1);
            }
        }
        return new Group(id,
                s.getString("permission", "group." + id),
                Math.max(0, s.getDouble("xp", 1.0)),
                Math.max(0, s.getDouble("drops", 1.0)),
                Math.max(0, s.getDouble("damage", 1.0)),
                Math.max(0, s.getDouble("potion-duration", 1.0)),
                Math.max(0, Math.min(0.9, s.getDouble("cooldown-reduction", 0.0))),
                fx,
                Math.max(0, s.getDouble("sell-bonus", 0.0)),
                Math.max(0, Math.min(0.9, s.getDouble("buy-discount", 0.0))));
    }

    /** Écrit le groupe dans boosts.yml (il prime sur config.yml) et le met en mémoire. */
    private void put(Group g) {
        String p = "groups." + g.id();
        store.get().set(p + ".permission", g.permission());
        store.get().set(p + ".xp", round(g.xp()));
        store.get().set(p + ".drops", round(g.drops()));
        store.get().set(p + ".damage", round(g.damage()));
        store.get().set(p + ".potion-duration", round(g.potionDuration()));
        store.get().set(p + ".cooldown-reduction", round(g.cooldownReduction()));
        store.get().set(p + ".sell-bonus", round(g.sellBonus()));
        store.get().set(p + ".buy-discount", round(g.buyDiscount()));
        store.get().set(p + ".effects", null);
        for (Map.Entry<PotionEffectType, Integer> e : g.effects().entrySet()) {
            store.get().set(p + ".effects." + e.getKey().getKey().getKey(), e.getValue() + 1);
        }
        List<String> deleted = new ArrayList<>(store.get().getStringList("deleted"));
        if (deleted.remove(g.id())) store.get().set("deleted", deleted);
        store.save();
        groups.put(g.id(), g);
        restartTask();
    }

    private static double round(double v) { return Math.round(v * 100.0) / 100.0; }

    private void restartTask() {
        if (task != null) task.cancel();
        task = null;
        if (!enabled || groups.values().stream().noneMatch(g -> !g.effects().isEmpty())) return;
        task = new BukkitRunnable() {
            @Override
            public void run() {
                for (Player p : Bukkit.getOnlinePlayers()) applyEffects(p);
            }
        };
        task.runTaskTimer(plugin, 40L, 40L);
    }

    public void shutdown() {
        if (task != null) task.cancel();
    }

    // ═════════════ Création / édition en jeu ═════════════

    public static String cleanId(String raw) { return raw.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "_"); }

    /** Renvoie un message d'erreur ou null. */
    public String create(String raw) {
        String id = cleanId(raw);
        if (id.isBlank()) return "Identifiant invalide.";
        if (groups.containsKey(id)) return "Ce boost existe déjà.";
        put(new Group(id, "group." + id, 1.0, 1.0, 1.0, 1.0, 0.0, new LinkedHashMap<>(), 0.0, 0.0));
        return null;
    }

    public boolean delete(String raw) {
        String id = cleanId(raw);
        if (!groups.containsKey(id)) return false;
        groups.remove(id);
        store.get().set("groups." + id, null);
        List<String> deleted = new ArrayList<>(store.get().getStringList("deleted"));
        if (!deleted.contains(id)) deleted.add(id);
        store.get().set("deleted", deleted);
        store.save();
        restartTask();
        return true;
    }

    public Group group(String raw) { return groups.get(cleanId(raw)); }

    /** stat : xp, drops, damage, potion ou cooldown (réduction 0 à 0.9). Renvoie un message d'erreur ou null. */
    public String setStat(String raw, String stat, double value) {
        Group g = group(raw);
        if (g == null) return "Boost introuvable.";
        double v = Math.max(0, value);
        Group n = switch (stat.toLowerCase(Locale.ROOT)) {
            case "xp" -> new Group(g.id(), g.permission(), v, g.drops(), g.damage(), g.potionDuration(), g.cooldownReduction(), g.effects(), g.sellBonus(), g.buyDiscount());
            case "drops" -> new Group(g.id(), g.permission(), g.xp(), v, g.damage(), g.potionDuration(), g.cooldownReduction(), g.effects(), g.sellBonus(), g.buyDiscount());
            case "damage" -> new Group(g.id(), g.permission(), g.xp(), g.drops(), v, g.potionDuration(), g.cooldownReduction(), g.effects(), g.sellBonus(), g.buyDiscount());
            case "potion" -> new Group(g.id(), g.permission(), g.xp(), g.drops(), g.damage(), v, g.cooldownReduction(), g.effects(), g.sellBonus(), g.buyDiscount());
            case "cooldown" -> new Group(g.id(), g.permission(), g.xp(), g.drops(), g.damage(), g.potionDuration(), Math.min(0.9, v), g.effects(), g.sellBonus(), g.buyDiscount());
            case "sell" -> new Group(g.id(), g.permission(), g.xp(), g.drops(), g.damage(), g.potionDuration(), g.cooldownReduction(), g.effects(), v, g.buyDiscount());
            case "discount" -> new Group(g.id(), g.permission(), g.xp(), g.drops(), g.damage(), g.potionDuration(), g.cooldownReduction(), g.effects(), g.sellBonus(), Math.min(0.9, v));
            default -> null;
        };
        if (n == null) return "Statistique inconnue (" + String.join(", ", STAT_KEYS) + ").";
        put(n);
        return null;
    }

    public String setPermission(String raw, String perm) {
        Group g = group(raw);
        if (g == null) return "Boost introuvable.";
        put(new Group(g.id(), perm, g.xp(), g.drops(), g.damage(), g.potionDuration(), g.cooldownReduction(), g.effects(), g.sellBonus(), g.buyDiscount()));
        return null;
    }

    /** level 1 = I. level 0 retire l'effet. */
    public String setEffect(String raw, String effect, int level) {
        Group g = group(raw);
        if (g == null) return "Boost introuvable.";
        PotionEffectType t = PotionEffectType.getByKey(NamespacedKey.minecraft(effect.toLowerCase(Locale.ROOT)));
        if (t == null) return "Effet inconnu.";
        Map<PotionEffectType, Integer> fx = new LinkedHashMap<>(g.effects());
        if (level <= 0) fx.remove(t);
        else fx.put(t, Math.min(level, 255) - 1);
        put(new Group(g.id(), g.permission(), g.xp(), g.drops(), g.damage(), g.potionDuration(), g.cooldownReduction(), fx, g.sellBonus(), g.buyDiscount()));
        return null;
    }

    // ═════════════ Événements temporaires ═════════════

    public Event startEvent(String stat, double multiplier, int minutes) {
        Event e = new Event(stat.toLowerCase(Locale.ROOT), multiplier, System.currentTimeMillis() + minutes * 60_000L);
        events.put(e.stat(), e);
        return e;
    }

    public boolean stopEvent(String stat) { return events.remove(stat.toLowerCase(Locale.ROOT)) != null; }

    public void stopAllEvents() { events.clear(); }

    public List<Event> events() {
        long now = System.currentTimeMillis();
        events.values().removeIf(e -> e.until() <= now);
        return new ArrayList<>(events.values());
    }

    private double eventMult(String key) {
        Event e = events.get(key);
        if (e == null) return 1.0;
        if (e.until() <= System.currentTimeMillis()) {
            events.remove(key);
            return 1.0;
        }
        return e.multiplier();
    }

    // ═════════════ Requêtes ═════════════

    public boolean enabled() { return enabled; }
    public String stacking() { return stacking; }
    public List<Group> all() { return new ArrayList<>(groups.values()); }

    public List<Group> active(Player p) {
        List<Group> out = new ArrayList<>();
        if (!enabled) return out;
        for (Group g : groups.values()) if (p.hasPermission(g.permission())) out.add(g);
        return out;
    }

    /** Multiplicateur total (boosts de groupe x événement), 1.0 = aucun. */
    public double multiplier(Player p, Stat stat) {
        String key = switch (stat) {
            case XP -> "xp";
            case DROPS -> "drops";
            case DAMAGE -> "damage";
            case POTION -> "potion";
        };
        double ev = eventMult(key);
        List<Group> act = active(p);
        if (act.isEmpty()) return Math.max(0, ev);

        double result = 1.0;
        for (Group g : act) {
            double m = switch (stat) {
                case XP -> g.xp();
                case DROPS -> g.drops();
                case DAMAGE -> g.damage();
                case POTION -> g.potionDuration();
            };
            switch (stacking) {
                case "multiply" -> result *= m;
                case "add" -> result += (m - 1.0);
                default -> result = Math.max(result, m);
            }
        }
        return Math.max(0, result * ev);
    }

    /** Multiplicateur appliqué aux prix de VENTE (1.15 = +15%) : boosts de grade x événement « sell ». */
    public double sellMultiplier(Player p) {
        double m = 1.0;
        List<Group> act = active(p);
        if (!act.isEmpty()) {
            if (stacking.equals("multiply")) { for (Group g : act) m *= 1.0 + g.sellBonus(); }
            else if (stacking.equals("add")) { double sum = 0; for (Group g : act) sum += g.sellBonus(); m = 1.0 + sum; }
            else { double b = 0; for (Group g : act) b = Math.max(b, g.sellBonus()); m = 1.0 + b; }
        }
        return Math.max(0, m * eventMult("sell"));
    }

    /** Multiplicateur appliqué aux prix d'ACHAT (0.9 = -10%) : réductions de grade x événement « buy ». */
    public double buyMultiplier(Player p) {
        double m = 1.0;
        List<Group> act = active(p);
        if (!act.isEmpty()) {
            if (stacking.equals("multiply")) { for (Group g : act) m *= 1.0 - g.buyDiscount(); }
            else if (stacking.equals("add")) { double sum = 0; for (Group g : act) sum += g.buyDiscount(); m = 1.0 - Math.min(0.9, sum); }
            else { double d = 0; for (Group g : act) d = Math.max(d, g.buyDiscount()); m = 1.0 - d; }
        }
        return Math.max(0.1, m * eventMult("buy"));
    }

    /** Facteur appliqué aux cooldowns (1.0 = aucun, 0.8 = -20%). */
    public double cooldownFactor(Player p) {
        double ev = eventMult("cooldown");
        List<Group> act = active(p);
        double f = 1.0;
        if (!act.isEmpty()) {
            double reduction = 0;
            double product = 1.0;
            for (Group g : act) {
                switch (stacking) {
                    case "multiply" -> product *= (1.0 - g.cooldownReduction());
                    case "add" -> reduction += g.cooldownReduction();
                    default -> reduction = Math.max(reduction, g.cooldownReduction());
                }
            }
            f = stacking.equals("multiply") ? product : 1.0 - Math.min(0.9, reduction);
        }
        return Math.max(0.1, f * ev);
    }

    public long cooldownMs(Player p, long baseMs) {
        return Math.max(1000L, Math.round(baseMs * cooldownFactor(p)));
    }

    // ═════════════ Effets de groupe ═════════════

    private void applyEffects(Player p) {
        if (p.isDead()) return;
        for (Group g : active(p)) {
            for (Map.Entry<PotionEffectType, Integer> fx : g.effects().entrySet()) {
                PotionEffect cur = p.getPotionEffect(fx.getKey());
                if (cur != null && (cur.getAmplifier() > fx.getValue()
                        || (cur.getAmplifier() == fx.getValue() && cur.getDuration() > 60))) continue;
                p.addPotionEffect(new PotionEffect(fx.getKey(), 120, fx.getValue(), true, false, true));
            }
        }
    }
}
