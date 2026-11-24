package fr.vortexsmp.vortexessentials.admin;

import fr.vortexsmp.vortexessentials.VortexEssentials;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Réglages par défaut du serveur : limites d'objets, items désactivés (craft et usage) et crafts personnalisés.
 * Appliqués une seule fois au premier démarrage, sans rien écraser ; /vortex admin defaults les réapplique.
 */
public final class AdminDefaults {
    private AdminDefaults() {}

    private static final Map<Material, Integer> LIMITS = new LinkedHashMap<>();
    private static final List<Material> DISABLED = List.of(Material.NETHERITE_HELMET, Material.NETHERITE_CHESTPLATE,
            Material.NETHERITE_LEGGINGS, Material.NETHERITE_BOOTS, Material.RESPAWN_ANCHOR);

    static {
        LIMITS.put(Material.GOLDEN_APPLE, 128);
        LIMITS.put(Material.COBWEB, 128);
        LIMITS.put(Material.ENCHANTED_GOLDEN_APPLE, 3);
        LIMITS.put(Material.TOTEM_OF_UNDYING, 2);
        LIMITS.put(Material.ENDER_PEARL, 32);
        LIMITS.put(Material.BREEZE_ROD, 64);
        LIMITS.put(Material.EXPERIENCE_BOTTLE, 128);
        LIMITS.put(Material.WATER_BUCKET, 3);
        LIMITS.put(Material.WIND_CHARGE, 192);
    }

    private record Def(String name, Material result, int amount, boolean shapeless, Material... grid) {}

    private static Material[] g(Material... m) { return m; }

    private static List<Def> recipes() {
        Material n = null;
        List<Def> l = new ArrayList<>();
        l.add(new Def("trident", Material.TRIDENT, 1, false, g(
                Material.IRON_SPEAR, Material.DIAMOND_SPEAR, Material.IRON_SPEAR,
                Material.PRISMARINE_SHARD, Material.DIAMOND_BLOCK, Material.PRISMARINE_SHARD,
                Material.PRISMARINE_SHARD, Material.BREEZE_ROD, Material.PRISMARINE_SHARD)));
        l.add(new Def("enchanted_golden_apple", Material.ENCHANTED_GOLDEN_APPLE, 1, false, g(
                Material.GOLD_BLOCK, Material.NETHERITE_INGOT, Material.GOLD_BLOCK,
                Material.DIAMOND_BLOCK, Material.GOLDEN_APPLE, Material.DIAMOND_BLOCK,
                Material.GOLD_BLOCK, Material.NETHERITE_INGOT, Material.GOLD_BLOCK)));
        l.add(new Def("golden_apple", Material.GOLDEN_APPLE, 1, false, g(
                n, n, n,
                Material.GOLD_INGOT, Material.APPLE, Material.GOLD_INGOT,
                n, n, n)));
        l.add(new Def("honey_bottle", Material.HONEY_BOTTLE, 2, true, g(
                Material.SUGAR, Material.MELON_SLICE, Material.POTION, Material.SWEET_BERRIES)));
        l.add(new Def("slime_ball", Material.SLIME_BALL, 1, true, g(Material.SNOWBALL, Material.AZALEA)));
        l.add(new Def("gunpowder", Material.GUNPOWDER, 2, true, g(Material.COAL, Material.REDSTONE)));
        l.add(new Def("anvil", Material.ANVIL, 1, false, g(
                Material.IRON_INGOT, Material.IRON_INGOT, Material.IRON_INGOT,
                n, Material.IRON_INGOT, n,
                Material.IRON_INGOT, Material.IRON_INGOT, Material.IRON_INGOT)));
        l.add(new Def("cobweb", Material.COBWEB, 1, false, g(
                Material.STRING, n, Material.STRING,
                n, Material.STRING, n,
                Material.STRING, n, Material.STRING)));
        l.add(new Def("ominous_bottle", Material.OMINOUS_BOTTLE, 1, false, g(
                Material.ENDER_EYE, Material.NETHER_WART, Material.ENDER_EYE,
                Material.NETHER_WART, Material.POTION, Material.NETHER_WART,
                Material.ENDER_EYE, Material.NETHER_WART, Material.ENDER_EYE)));
        l.add(new Def("experience_bottle", Material.EXPERIENCE_BOTTLE, 4, false, g(
                Material.MAGMA_CREAM, Material.CHORUS_FRUIT, Material.AMETHYST_SHARD,
                Material.ENDER_EYE, Material.GLASS_BOTTLE, Material.ENDER_EYE,
                Material.AMETHYST_SHARD, Material.CHORUS_FRUIT, Material.MAGMA_CREAM)));
        l.add(new Def("echo_shard", Material.ECHO_SHARD, 2, false, g(
                Material.SCULK, Material.AMETHYST_SHARD, Material.SCULK,
                Material.DIAMOND, n, Material.DIAMOND,
                Material.SCULK, Material.AMETHYST_SHARD, Material.SCULK)));
        l.add(new Def("shulker_box", Material.SHULKER_BOX, 1, false, g(
                Material.DIAMOND_BLOCK, Material.NETHERITE_INGOT, Material.DIAMOND_BLOCK,
                Material.IRON_BLOCK, Material.SHULKER_SHELL, Material.IRON_BLOCK,
                Material.DIAMOND_BLOCK, Material.NETHERITE_INGOT, Material.DIAMOND_BLOCK)));
        l.add(new Def("totem_of_undying", Material.TOTEM_OF_UNDYING, 1, false, g(
                Material.GOLD_BLOCK, Material.ENDER_EYE, Material.GOLD_BLOCK,
                Material.ECHO_SHARD, Material.ENCHANTED_GOLDEN_APPLE, Material.ECHO_SHARD,
                Material.GOLD_BLOCK, Material.ENDER_EYE, Material.GOLD_BLOCK)));
        return l;
    }

    /** Ajoute ce qui manque. Renvoie {limites ajoutées, items désactivés ajoutés, crafts ajoutés}. */
    public static int[] apply(VortexEssentials plugin) { return apply(plugin, false); }

    /** force = true : recrée aussi les crafts par défaut déjà présents (met leur recette à jour). */
    public static int[] apply(VortexEssentials plugin, boolean force) {
        AdminManager adm = plugin.admin();
        int lim = 0, dis = 0, rec = 0;
        for (Map.Entry<Material, Integer> e : LIMITS.entrySet()) {
            if (adm.limitOf(e.getKey()) <= 0) { adm.setLimit(e.getKey(), e.getValue()); lim++; }
        }
        for (Material m : DISABLED) if (adm.items().add(m)) dis++;
        adm.save();

        CustomRecipes cr = plugin.customRecipes();
        List<String> existing = cr.names();
        for (Def d : recipes()) {
            if (existing.contains(d.name())) {
                if (!force) continue;
                cr.delete(d.name());
            }
            Material[] grid = new Material[9];
            if (d.shapeless()) {
                System.arraycopy(d.grid(), 0, grid, 0, d.grid().length);
            } else {
                grid = d.grid();
            }
            if (cr.save(d.name(), grid, new ItemStack(d.result(), d.amount()), d.shapeless()) == null) rec++;
        }
        return new int[]{lim, dis, rec};
    }

    /** Premier démarrage uniquement. */
    public static void installOnce(VortexEssentials plugin) {
        File marker = new File(plugin.getDataFolder(), "defaults-applied.txt");
        if (marker.exists()) return;
        int[] r = apply(plugin);
        plugin.getLogger().info("Réglages par défaut appliqués : " + r[0] + " limite(s), " + r[1] + " item(s) désactivé(s), " + r[2] + " craft(s).");
        try {
            plugin.getDataFolder().mkdirs();
            marker.createNewFile();
        } catch (IOException ignored) { }
    }
}
