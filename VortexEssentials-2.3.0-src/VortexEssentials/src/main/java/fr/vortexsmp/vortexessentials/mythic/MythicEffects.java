package fr.vortexsmp.vortexessentials.mythic;

import fr.vortexsmp.vortexessentials.VortexEssentials;
import fr.vortexsmp.vortexessentials.item.VortexItem;
import fr.vortexsmp.vortexessentials.util.Effects;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Effets permanents des armes mythiques (dans l'inventaire).
 * Config : mythic-weapons.<id>.effects : { <effet>: <niveau> }  (section vide = aucun effet).
 * Sans section, les valeurs par défaut ci-dessous s'appliquent.
 */
public final class MythicEffects {
    private MythicEffects() {}

    /** Section mythic-weapons.<id>.<name>, avec l'id tel quel ou avec des tirets (umbral-scythe). */
    private static ConfigurationSection section(VortexEssentials pl, VortexItem v, String name) {
        ConfigurationSection s = pl.getConfig().getConfigurationSection("mythic-weapons." + v.id() + "." + name);
        if (s == null) s = pl.getConfig().getConfigurationSection("mythic-weapons." + v.id().replace('_', '-') + "." + name);
        return s;
    }

    private static Map<String, Integer> defaults(VortexItem v) {
        Map<String, Integer> m = new LinkedHashMap<>();
        switch (v) {
            case SEALORD -> m.put("resistance", 2);
            case LIGHTSPEED -> { m.put("speed", 3); m.put("weaving", 3); m.put("resistance", 1); }
            case SKYFALL -> { m.put("speed", 2); m.put("regeneration", 1); m.put("resistance", 1); }
            case VOIDRENDER -> { m.put("fire_resistance", 1); m.put("strength", 1); m.put("weaving", 1); }
            case STORMPIERCER -> { m.put("strength", 1); m.put("speed", 1); m.put("jump_boost", 1); }
            case UMBRAL_SCYTHE -> { m.put("resistance", 2); m.put("strength", 2); m.put("fire_resistance", 1); m.put("speed", 2); m.put("weaving", 1); }
            case MINTUS -> { m.put("hero_of_the_village", 1); m.put("resistance", 3); m.put("fire_resistance", 1); }
            default -> { }
        }
        return m;
    }

    /** effet -> niveau (1 = I). */
    public static Map<String, Integer> levels(VortexItem v) {
        VortexEssentials pl = VortexEssentials.get();
        if (pl != null) {
            ConfigurationSection s = section(pl, v, "effects");
            if (s != null) {
                Map<String, Integer> m = new LinkedHashMap<>();
                for (String k : s.getKeys(false)) {
                    int lvl = s.getInt(k, 0);
                    if (lvl > 0) m.put(k.toLowerCase(Locale.ROOT), lvl);
                }
                return m;
            }
        }
        return defaults(v);
    }

    /** Effets supplémentaires tant que le porteur est dans l'eau (mythic-weapons.<id>.water-effects). */
    public static Map<String, Integer> waterLevels(VortexItem v) {
        VortexEssentials pl = VortexEssentials.get();
        if (pl != null) {
            ConfigurationSection s = section(pl, v, "water-effects");
            if (s != null) {
                Map<String, Integer> m = new LinkedHashMap<>();
                for (String k : s.getKeys(false)) {
                    int lvl = s.getInt(k, 0);
                    if (lvl > 0) m.put(k.toLowerCase(Locale.ROOT), lvl);
                }
                return m;
            }
        }
        Map<String, Integer> m = new LinkedHashMap<>();
        if (v == VortexItem.SEALORD) {
            m.put("water_breathing", 1);
            m.put("dolphins_grace", 3);
            m.put("regeneration", 2);
        }
        return m;
    }

    public static Map<PotionEffectType, Integer> waterAmplifiers(VortexItem v) {
        Map<PotionEffectType, Integer> out = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> e : waterLevels(v).entrySet()) {
            PotionEffectType t = PotionEffectType.getByKey(NamespacedKey.minecraft(e.getKey()));
            if (t != null) out.put(t, e.getValue() - 1);
        }
        return out;
    }

    public static String describeWater(VortexItem v) {
        List<String> parts = new ArrayList<>();
        for (Map.Entry<String, Integer> e : waterLevels(v).entrySet()) {
            parts.add(Effects.label(e.getKey()) + " " + Effects.roman(e.getValue()));
        }
        return String.join("§7, §f", parts);
    }

    /** type -> amplificateur (niveau - 1). */
    public static Map<PotionEffectType, Integer> amplifiers(VortexItem v) {
        Map<PotionEffectType, Integer> out = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> e : levels(v).entrySet()) {
            PotionEffectType t = PotionEffectType.getByKey(NamespacedKey.minecraft(e.getKey()));
            if (t != null) out.put(t, e.getValue() - 1);
        }
        return out;
    }

    /** Texte pour le tooltip, ex. "Résistance II, Vitesse III". */
    public static String describe(VortexItem v) {
        List<String> parts = new ArrayList<>();
        for (Map.Entry<String, Integer> e : levels(v).entrySet()) {
            parts.add(Effects.label(e.getKey()) + " " + Effects.roman(e.getValue()));
        }
        return String.join("§7, §f", parts);
    }
}
