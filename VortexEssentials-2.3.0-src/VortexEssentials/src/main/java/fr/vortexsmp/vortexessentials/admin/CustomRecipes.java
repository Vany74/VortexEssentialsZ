package fr.vortexsmp.vortexessentials.admin;

import fr.vortexsmp.vortexessentials.VortexEssentials;
import fr.vortexsmp.vortexessentials.YamlStore;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Crafts créés en jeu (custom-recipes.yml). Les ingrédients sont des matériaux ; le résultat peut être n'importe quel item. */
public final class CustomRecipes {
    private final VortexEssentials plugin;
    private final YamlStore store;
    private final List<NamespacedKey> keys = new ArrayList<>();

    public CustomRecipes(VortexEssentials plugin) {
        this.plugin = plugin;
        this.store = new YamlStore(plugin, "custom-recipes.yml");
        load();
    }

    public static String clean(String name) {
        return name.toLowerCase().replaceAll("[^a-z0-9_]", "_");
    }

    public void load() {
        for (NamespacedKey k : keys) Bukkit.removeRecipe(k);
        keys.clear();
        store.reload();
        ConfigurationSection rs = store.get().getConfigurationSection("recipes");
        if (rs == null) return;
        for (String name : rs.getKeys(false)) register(name);
    }

    public List<String> names() {
        ConfigurationSection rs = store.get().getConfigurationSection("recipes");
        return rs == null ? List.of() : new ArrayList<>(rs.getKeys(false));
    }

    /** Matériaux de la grille (9 cases, vide = case vide). */
    public List<String> grid(String name) { return store.get().getStringList("recipes." + name + ".grid"); }

    public boolean shapeless(String name) { return store.get().getBoolean("recipes." + name + ".shapeless"); }

    public ItemStack result(String name) {
        return store.get().getItemStack("recipes." + name + ".result");
    }

    /** Enregistre une recette. grid : 9 cases (null = vide). Renvoie un message d'erreur ou null si OK. */
    public String save(String rawName, Material[] grid, ItemStack result, boolean shapeless) {
        String name = clean(rawName);
        if (name.isBlank()) return "Nom invalide.";
        if (result == null || result.getType().isAir()) return "Placez le résultat dans la case de droite.";
        boolean any = false;
        for (Material m : grid) if (m != null) any = true;
        if (!any) return "Placez au moins un ingrédient.";

        String path = "recipes." + name;
        List<String> g = new ArrayList<>();
        for (Material m : grid) g.add(m == null ? "" : m.name());
        store.get().set(path + ".shapeless", shapeless);
        store.get().set(path + ".grid", g);
        store.get().set(path + ".result", result);
        store.save();

        NamespacedKey key = new NamespacedKey(plugin, "custom_" + name);
        Bukkit.removeRecipe(key);
        keys.remove(key);
        return register(name) ? null : "Recette invalide.";
    }

    public boolean delete(String rawName) {
        String name = clean(rawName);
        if (!store.get().contains("recipes." + name)) return false;
        store.get().set("recipes." + name, null);
        store.save();
        NamespacedKey key = new NamespacedKey(plugin, "custom_" + name);
        Bukkit.removeRecipe(key);
        keys.remove(key);
        return true;
    }

    private boolean register(String name) {
        String path = "recipes." + name;
        ItemStack result = store.get().getItemStack(path + ".result");
        List<String> g = store.get().getStringList(path + ".grid");
        if (result == null || g.size() != 9) return false;

        Material[] grid = new Material[9];
        for (int i = 0; i < 9; i++) grid[i] = g.get(i).isEmpty() ? null : Material.matchMaterial(g.get(i));

        NamespacedKey key = new NamespacedKey(plugin, "custom_" + name);
        Recipe recipe;
        if (store.get().getBoolean(path + ".shapeless")) {
            ShapelessRecipe r = new ShapelessRecipe(key, result.clone());
            for (Material m : grid) if (m != null) r.addIngredient(m);
            recipe = r;
        } else {
            // Rectangle englobant les cases remplies
            int minR = 3, maxR = -1, minC = 3, maxC = -1;
            for (int i = 0; i < 9; i++) {
                if (grid[i] == null) continue;
                minR = Math.min(minR, i / 3); maxR = Math.max(maxR, i / 3);
                minC = Math.min(minC, i % 3); maxC = Math.max(maxC, i % 3);
            }
            if (maxR < 0) return false;
            Map<Material, Character> letters = new LinkedHashMap<>();
            String[] rows = new String[maxR - minR + 1];
            for (int r = minR; r <= maxR; r++) {
                StringBuilder sb = new StringBuilder();
                for (int c = minC; c <= maxC; c++) {
                    Material m = grid[r * 3 + c];
                    if (m == null) { sb.append(' '); continue; }
                    letters.putIfAbsent(m, (char) ('A' + letters.size()));
                    sb.append(letters.get(m));
                }
                rows[r - minR] = sb.toString();
            }
            ShapedRecipe sr = new ShapedRecipe(key, result.clone());
            sr.shape(rows);
            for (Map.Entry<Material, Character> en : letters.entrySet()) sr.setIngredient(en.getValue(), en.getKey());
            recipe = sr;
        }
        try {
            Bukkit.addRecipe(recipe);
            keys.add(key);
            return true;
        } catch (Exception ex) {
            plugin.getLogger().warning("Craft personnalisé '" + name + "' : " + ex.getMessage());
            return false;
        }
    }
}
