package fr.vortexsmp.vortexessentials.serveradmin;

import org.bukkit.Bukkit;
import org.bukkit.World;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Accès aux gamerules par réflexion (les noms et l'API ont changé selon les versions) : liste, lecture, écriture.
 * L'écriture passe par la commande vanilla /gamerule, valable quelle que soit la version.
 */
final class GameRuleAccess {
    private GameRuleAccess() {}

    static List<String> names(World w) {
        Set<String> out = new LinkedHashSet<>();
        try {
            Method m = World.class.getMethod("getGameRules");
            Object r = m.invoke(w);
            if (r instanceof String[] arr) for (String s : arr) out.add(s);
        } catch (Throwable ignored) { }
        if (out.isEmpty()) {
            try {
                Class<?> gr = Class.forName("org.bukkit.GameRule");
                for (Field f : gr.getFields()) {
                    if (!Modifier.isStatic(f.getModifiers()) || f.getType() != gr) continue;
                    Object rule = f.get(null);
                    out.add(String.valueOf(rule.getClass().getMethod("getName").invoke(rule)));
                }
            } catch (Throwable ignored) { }
        }
        return new ArrayList<>(out);
    }

    static String value(World w, String name) {
        try {
            Method m = World.class.getMethod("getGameRuleValue", String.class);
            Object v = m.invoke(w, name);
            if (v != null) return String.valueOf(v);
        } catch (Throwable ignored) { }
        try {
            Class<?> gr = Class.forName("org.bukkit.GameRule");
            for (Field f : gr.getFields()) {
                if (!Modifier.isStatic(f.getModifiers()) || f.getType() != gr) continue;
                Object rule = f.get(null);
                if (!name.equals(String.valueOf(rule.getClass().getMethod("getName").invoke(rule)))) continue;
                Method g = World.class.getMethod("getGameRuleValue", gr);
                Object v = g.invoke(w, rule);
                return v == null ? null : String.valueOf(v);
            }
        } catch (Throwable ignored) { }
        return null;
    }

    static void set(World w, String name, String value) {
        String dim = switch (w.getEnvironment()) {
            case NETHER -> "minecraft:the_nether";
            case THE_END -> "minecraft:the_end";
            default -> w.getName().equals(Bukkit.getWorlds().get(0).getName()) ? "minecraft:overworld" : w.getName();
        };
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "execute in " + dim + " run gamerule " + name + " " + value);
    }
}
