package fr.vortexsmp.vortexessentials.util;

import java.util.Map;
import java.util.Set;

/** Effets de potion : nuisibles et libellés français. */
public final class Effects {
    public static final Set<String> HARMFUL = Set.of(
            "slowness", "mining_fatigue", "instant_damage", "nausea", "blindness", "hunger", "weakness",
            "poison", "wither", "levitation", "unluck", "darkness", "bad_omen", "raid_omen", "trial_omen",
            "infested", "oozing", "weaving", "wind_charged");

    private static final Map<String, String> FR = Map.ofEntries(
            Map.entry("speed", "Vitesse"), Map.entry("slowness", "Lenteur"), Map.entry("haste", "Hâte"),
            Map.entry("mining_fatigue", "Fatigue de minage"), Map.entry("strength", "Force"),
            Map.entry("instant_health", "Soin instantané"), Map.entry("instant_damage", "Dégâts instantanés"),
            Map.entry("jump_boost", "Saut amélioré"), Map.entry("nausea", "Nausée"),
            Map.entry("regeneration", "Régénération"), Map.entry("resistance", "Résistance"),
            Map.entry("fire_resistance", "Résistance au feu"), Map.entry("water_breathing", "Respiration aquatique"),
            Map.entry("invisibility", "Invisibilité"), Map.entry("blindness", "Cécité"),
            Map.entry("night_vision", "Vision nocturne"), Map.entry("hunger", "Faim"),
            Map.entry("weakness", "Faiblesse"), Map.entry("poison", "Poison"), Map.entry("wither", "Wither"),
            Map.entry("health_boost", "Bonus de santé"), Map.entry("absorption", "Absorption"),
            Map.entry("saturation", "Saturation"), Map.entry("glowing", "Lueur"),
            Map.entry("levitation", "Lévitation"), Map.entry("luck", "Chance"), Map.entry("unluck", "Malchance"),
            Map.entry("slow_falling", "Chute lente"), Map.entry("conduit_power", "Puissance de conduit"),
            Map.entry("dolphins_grace", "Grâce du dauphin"), Map.entry("bad_omen", "Mauvais présage"),
            Map.entry("hero_of_the_village", "Héros du village"), Map.entry("darkness", "Obscurité"),
            Map.entry("trial_omen", "Présage d'épreuve"), Map.entry("raid_omen", "Présage de raid"),
            Map.entry("wind_charged", "Chargé de vent"), Map.entry("weaving", "Tissage"),
            Map.entry("oozing", "Suintement"), Map.entry("infested", "Infesté"));

    private Effects() {}

    public static String label(String key) {
        return FR.getOrDefault(key, key.replace('_', ' '));
    }

    public static String roman(int n) {
        return switch (n) {
            case 1 -> "I"; case 2 -> "II"; case 3 -> "III"; case 4 -> "IV"; case 5 -> "V";
            case 6 -> "VI"; case 7 -> "VII"; case 8 -> "VIII"; case 9 -> "IX"; case 10 -> "X";
            default -> String.valueOf(n);
        };
    }
}
