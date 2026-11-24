package fr.vortexsmp.vortexessentials.recipe;

import fr.vortexsmp.vortexessentials.Msg;
import fr.vortexsmp.vortexessentials.VortexEssentials;
import fr.vortexsmp.vortexessentials.item.ItemUtil;
import fr.vortexsmp.vortexessentials.item.VortexItem;
import org.bukkit.Bukkit;
import org.bukkit.Keyed;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Charge recipes.yml et enregistre les recettes de craft.
 * Les améliorations netherite (forge) ne sont pas des recettes Bukkit : elles réutilisent la recette
 * vanilla et le résultat est remplacé (voir RecipeListener).
 * Aucun RecipeChoice custom n'est utilisé (plante le serveur) : les items Vortex exigés sont vérifiés
 * dans PrepareItemCraftEvent.
 */
public final class RecipeManager {

    public record Def(VortexItem result, boolean shaped, List<String> shape, Map<Character, String> ingredients,
                      List<String> shapeless, Material transferFrom, Map<Material, VortexItem> requireCustom) {}

    private final VortexEssentials plugin;
    private final Map<VortexItem, Def> defs = new EnumMap<>(VortexItem.class);
    private final Map<VortexItem, VortexItem> upgrades = new EnumMap<>(VortexItem.class);
    private final List<NamespacedKey> keys = new ArrayList<>();
    private final List<String> problems = new ArrayList<>();

    public RecipeManager(VortexEssentials plugin) {
        this.plugin = plugin;
    }

    public Def def(VortexItem v) { return defs.get(v); }
    public List<String> problems() { return problems; }
    public int craftCount() { return defs.size(); }
    public int upgradeCount() { return upgrades.size(); }
    public VortexItem upgradeOf(VortexItem source) { return upgrades.get(source); }

    public VortexItem forgeSource(VortexItem target) {
        for (Map.Entry<VortexItem, VortexItem> e : upgrades.entrySet()) if (e.getValue() == target) return e.getKey();
        return null;
    }

    /** Remplace recipes.yml par un préréglage fourni (normal ou hard), sauvegarde l'ancien en .bak et recharge. */
    public String applyPreset(String name) {
        String n = name.toLowerCase(java.util.Locale.ROOT);
        if (!n.equals("hard") && !n.equals("normal")) return "Préréglage inconnu (hard, normal).";
        try (java.io.InputStream in = plugin.getResource("recipes-" + n + ".yml")) {
            if (in == null) return "Fichier de préréglage introuvable.";
            File f = new File(plugin.getDataFolder(), "recipes.yml");
            if (f.exists()) {
                java.nio.file.Files.copy(f.toPath(), new File(plugin.getDataFolder(), "recipes.yml.bak").toPath(),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            java.nio.file.Files.copy(in, f.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            return "Erreur : " + e.getMessage();
        }
        load();
        return null;
    }

    // ═════════════ Modifications en jeu (recipes-custom.yml) ═════════════

    private File overlayFile() { return new File(plugin.getDataFolder(), "recipes-custom.yml"); }

    public boolean isOverridden(VortexItem v) {
        return YamlConfiguration.loadConfiguration(overlayFile()).contains("recipes." + v.id());
    }

    public void writeOverride(VortexItem v, List<String> shape, Map<Character, String> ingredients, Map<String, String> requireCustom,
                              String transferFrom) throws java.io.IOException {
        YamlConfiguration y = YamlConfiguration.loadConfiguration(overlayFile());
        String p = "recipes." + v.id();
        y.set(p, null);
        y.set(p + ".shape", shape);
        for (Map.Entry<Character, String> e : ingredients.entrySet()) y.set(p + ".ingredients." + e.getKey(), e.getValue());
        for (Map.Entry<String, String> e : requireCustom.entrySet()) y.set(p + ".require-custom." + e.getKey(), e.getValue());
        if (transferFrom != null) y.set(p + ".transfer-from", transferFrom);
        y.save(overlayFile());
    }

    public void disableRecipe(VortexItem v) throws java.io.IOException {
        YamlConfiguration y = YamlConfiguration.loadConfiguration(overlayFile());
        y.set("recipes." + v.id(), null);
        y.set("recipes." + v.id() + ".disabled", true);
        y.save(overlayFile());
    }

    public void clearOverride(VortexItem v) throws java.io.IOException {
        YamlConfiguration y = YamlConfiguration.loadConfiguration(overlayFile());
        y.set("recipes." + v.id(), null);
        y.save(overlayFile());
    }

    // ═════════════ Chargement ═════════════

    public void load() {
        for (NamespacedKey k : keys) Bukkit.removeRecipe(k);
        keys.clear();
        problems.clear();
        defs.clear();
        upgrades.clear();

        File f = new File(plugin.getDataFolder(), "recipes.yml");
        if (!f.exists()) plugin.saveResource("recipes.yml", false);
        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);

        // modifications faites en jeu (/vortex recipes edit) : appliquées par-dessus recipes.yml, jamais écrasées
        ConfigurationSection os = YamlConfiguration.loadConfiguration(overlayFile()).getConfigurationSection("recipes");
        if (os != null) {
            for (String oid : os.getKeys(false)) {
                ConfigurationSection sec = os.getConfigurationSection(oid);
                y.set("recipes." + oid, null);
                if (sec == null || sec.getBoolean("disabled")) continue;
                for (Map.Entry<String, Object> en : sec.getValues(true).entrySet()) {
                    if (!(en.getValue() instanceof ConfigurationSection)) y.set("recipes." + oid + "." + en.getKey(), en.getValue());
                }
            }
        }

        ConfigurationSection rs = y.getConfigurationSection("recipes");
        if (rs != null) {
            for (String id : rs.getKeys(false)) {
                VortexItem v = VortexItem.byId(id);
                ConfigurationSection s = rs.getConfigurationSection(id);
                if (v == null || s == null) {
                    warn("item inconnu '" + id + "'");
                    continue;
                }
                Def d = parse(v, s);
                if (d == null) continue;
                Recipe r = build(d);
                if (r == null) continue;
                try {
                    Bukkit.addRecipe(r);
                } catch (Exception ex) {
                    warn(id + " : " + ex.getMessage());
                    continue;
                }
                defs.put(v, d);
                keys.add(((Keyed) r).getKey());
            }
        }

        ConfigurationSection us = y.getConfigurationSection("upgrades");
        if (us != null) {
            for (String src : us.getKeys(false)) {
                VortexItem a = VortexItem.byId(src);
                VortexItem b = VortexItem.byId(us.getString(src));
                if (a == null || b == null) warn("amélioration invalide '" + src + "'");
                else upgrades.put(a, b);
            }
        }
        plugin.getLogger().info(defs.size() + " recettes et " + upgrades.size() + " améliorations chargées.");
    }

    private void warn(String msg) {
        problems.add(msg);
        plugin.getLogger().warning("recipes.yml : " + msg);
    }

    private Def parse(VortexItem v, ConfigurationSection s) {
        Material transfer = null;
        String t = s.getString("transfer-from");
        if (t != null) {
            transfer = Material.matchMaterial(t);
            if (transfer == null) {
                warn(v.id() + " : transfer-from invalide '" + t + "'");
                return null;
            }
        }

        Map<Material, VortexItem> req = new EnumMap<>(Material.class);
        ConfigurationSection rc = s.getConfigurationSection("require-custom");
        if (rc != null) {
            for (String mk : rc.getKeys(false)) {
                Material m = Material.matchMaterial(mk);
                VortexItem ci = VortexItem.byId(rc.getString(mk));
                if (m == null || ci == null) {
                    warn(v.id() + " : require-custom invalide '" + mk + "'");
                    return null;
                }
                req.put(m, ci);
            }
        }

        if (s.isList("shapeless")) {
            List<String> list = s.getStringList("shapeless");
            if (list.isEmpty() || list.size() > 9) {
                warn(v.id() + " : shapeless doit contenir 1 à 9 ingrédients");
                return null;
            }
            for (String tok : list) {
                if (choice(tok) == null) {
                    warn(v.id() + " : ingrédient inconnu '" + tok + "'");
                    return null;
                }
            }
            return new Def(v, false, List.of(), Map.of(), list, transfer, req);
        }

        List<String> shape = s.getStringList("shape");
        ConfigurationSection is = s.getConfigurationSection("ingredients");
        if (shape.isEmpty() || shape.size() > 3 || is == null) {
            warn(v.id() + " : shape (1 à 3 lignes) et ingredients requis");
            return null;
        }
        Map<Character, String> ing = new LinkedHashMap<>();
        for (String k : is.getKeys(false)) {
            String tok = is.getString(k);
            if (k.length() != 1 || choice(tok) == null) {
                warn(v.id() + " : ingrédient invalide '" + k + "'");
                return null;
            }
            ing.put(k.charAt(0), tok);
        }
        for (String row : shape) {
            if (row.isEmpty() || row.length() > 3) {
                warn(v.id() + " : chaque ligne doit faire 1 à 3 caractères");
                return null;
            }
            for (char c : row.toCharArray()) {
                if (c != ' ' && !ing.containsKey(c)) {
                    warn(v.id() + " : la lettre '" + c + "' n'a pas d'ingrédient");
                    return null;
                }
            }
        }
        return new Def(v, true, shape, ing, List.of(), transfer, req);
    }

    private static RecipeChoice choice(String token) {
        if (token == null) return null;
        if (token.startsWith("#")) {
            Tag<Material> tag = switch (token.substring(1).toLowerCase()) {
                case "logs" -> Tag.LOGS;
                case "planks" -> Tag.PLANKS;
                default -> null;
            };
            return tag == null ? null : new RecipeChoice.MaterialChoice(tag);
        }
        Material m = Material.matchMaterial(token);
        return m == null ? null : new RecipeChoice.MaterialChoice(m);
    }

    private Recipe build(Def d) {
        NamespacedKey key = new NamespacedKey(plugin, "craft_" + d.result().id());
        ItemStack result = ItemUtil.create(d.result());

        if (d.shaped()) {
            ShapedRecipe r = new ShapedRecipe(key, result);
            r.shape(d.shape().toArray(new String[0]));
            Set<Character> done = new HashSet<>();
            for (String row : d.shape()) {
                for (char c : row.toCharArray()) {
                    if (c != ' ' && done.add(c)) r.setIngredient(c, choice(d.ingredients().get(c)));
                }
            }
            return r;
        }
        ShapelessRecipe r = new ShapelessRecipe(key, result);
        for (String tok : d.shapeless()) r.addIngredient(choice(tok));
        return r;
    }

    // ═════════════ Affichage (aperçu dans le menu) ═════════════

    /** Icône d'un ingrédient ("MATERIAL" ou "#tag"). */
    public static ItemStack display(String token) {
        if (token.startsWith("#")) {
            boolean logs = token.equalsIgnoreCase("#logs");
            ItemStack s = new ItemStack(logs ? Material.OAK_LOG : Material.OAK_PLANKS);
            ItemMeta m = s.getItemMeta();
            m.displayName(Msg.plain(logs ? "§fRondin §7(au choix)" : "§fPlanche §7(au choix)"));
            s.setItemMeta(m);
            return s;
        }
        Material m = Material.matchMaterial(token);
        return new ItemStack(m == null ? Material.BARRIER : m);
    }
}
