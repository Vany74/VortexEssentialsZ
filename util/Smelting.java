package fr.vortexsmp.vortexessentials.util;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.FurnaceRecipe;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;

import java.util.Collection;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;

/** Cuisson automatique des minerais (recettes de four vanilla). */
public final class Smelting {
    private static final Map<Material, ItemStack> CACHE = new EnumMap<>(Material.class);
    private static boolean loaded;

    private Smelting() {}

    public static boolean isOre(Material m) {
        return m.name().endsWith("_ORE") || m == Material.ANCIENT_DEBRIS;
    }

    /** Résultat de cuisson d'un item, ou null s'il n'y en a pas. */
    public static ItemStack smelted(Material in) {
        if (!loaded) {
            Iterator<Recipe> it = Bukkit.recipeIterator();
            while (it.hasNext()) {
                Recipe r = it.next();
                if (r instanceof FurnaceRecipe fr && fr.getInputChoice() instanceof RecipeChoice.MaterialChoice mc) {
                    for (Material m : mc.getChoices()) CACHE.putIfAbsent(m, fr.getResult());
                }
            }
            loaded = true;
        }
        ItemStack r = CACHE.get(in);
        return r == null ? null : r.clone();
    }

    /** Remplace chaque drop par sa version cuite quand elle existe. */
    public static List<ItemStack> smeltDrops(Collection<ItemStack> drops) {
        List<ItemStack> out = new ArrayList<>();
        for (ItemStack d : drops) {
            ItemStack s = smelted(d.getType());
            if (s == null) {
                out.add(d);
            } else {
                s.setAmount(Math.min(64, s.getAmount() * d.getAmount()));
                out.add(s);
            }
        }
        return out;
    }
}
