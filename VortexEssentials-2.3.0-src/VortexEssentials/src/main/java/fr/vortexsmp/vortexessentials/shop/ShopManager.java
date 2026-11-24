package fr.vortexsmp.vortexessentials.shop;

import fr.vortexsmp.vortexessentials.VortexEssentials;
import fr.vortexsmp.vortexessentials.YamlStore;
import fr.vortexsmp.vortexessentials.item.ItemUtil;
import fr.vortexsmp.vortexessentials.item.VortexItem;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Boutique du serveur : catégories d'items à prix fixes, stock illimité (shop.yml).
 * Une entrée est un Material, un item Vortex (vortex:<id>) ou un item personnalisé (citem:<id>).
 * Les prix tiennent compte des boosts de grade : bonus de vente et réduction d'achat.
 */
public final class ShopManager {
    public static final class Entry {
        public final String key;
        public double buy;
        public double sell;
        public boolean enabled = true;

        Entry(String key, double buy, double sell) {
            this.key = key;
            this.buy = buy;
            this.sell = sell;
        }
    }

    public static final class Category {
        public final String id;
        public String name;
        public Material icon;
        public boolean enabled = true;
        public final Map<String, Entry> items = new LinkedHashMap<>();

        Category(String id, String name, Material icon) {
            this.id = id;
            this.name = name;
            this.icon = icon;
        }
    }

    private final VortexEssentials plugin;
    private YamlStore store;
    private final Map<String, Category> cats = new LinkedHashMap<>();

    public ShopManager(VortexEssentials plugin) {
        this.plugin = plugin;
        File f = new File(plugin.getDataFolder(), "shop.yml");
        if (!f.exists()) {
            plugin.getDataFolder().mkdirs();
            try (InputStream in = plugin.getResource("shop-default.yml")) {
                if (in != null) Files.copy(in, f.toPath());
            } catch (Exception e) {
                plugin.getLogger().warning("shop.yml : copie du catalogue par défaut impossible (" + e.getMessage() + ")");
            }
        }
        store = new YamlStore(plugin, "shop.yml");
        load();
    }

    // ═════════════ Persistance ═════════════

    public void load() {
        store.reload();
        cats.clear();
        List<String> unknown = new ArrayList<>();
        ConfigurationSection cs = store.get().getConfigurationSection("categories");
        if (cs == null) return;
        for (String id : cs.getKeys(false)) {
            ConfigurationSection s = cs.getConfigurationSection(id);
            if (s == null) continue;
            Material icon = Material.matchMaterial(s.getString("icon", "CHEST"));
            Category c = new Category(id, s.getString("name", id), icon == null ? Material.CHEST : icon);
            c.enabled = s.getBoolean("enabled", true);
            ConfigurationSection is = s.getConfigurationSection("items");
            if (is != null) {
                for (String key : is.getKeys(false)) {
                    if (stack(key, 1) == null) {
                        unknown.add(key);
                        continue;
                    }
                    ConfigurationSection e = is.getConfigurationSection(key);
                    if (e == null) continue;
                    Entry en = new Entry(key, e.getDouble("buy", -1), e.getDouble("sell", -1));
                    en.enabled = e.getBoolean("enabled", true);
                    c.items.put(key, en);
                }
            }
            cats.put(id, c);
        }
        if (!unknown.isEmpty()) {
            plugin.getLogger().warning("shop.yml : " + unknown.size() + " entrée(s) inconnue(s) ignorée(s) (ex. "
                    + String.join(", ", unknown.subList(0, Math.min(5, unknown.size()))) + ")");
        }
    }

    /**
     * Ajoute les catégories et items du catalogue par défaut qui manquent, sans toucher aux prix existants.
     * Renvoie {catégories ajoutées, items ajoutés}.
     */
    public int[] mergeDefaults() {
        try (InputStream in = plugin.getResource("shop-default.yml")) {
            if (in == null) return new int[]{0, 0, 0};
            org.bukkit.configuration.file.YamlConfiguration def = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(
                    new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8));
            ConfigurationSection cs = def.getConfigurationSection("categories");
            if (cs == null) return new int[]{0, 0, 0};
            java.util.Set<String> present = new java.util.HashSet<>();
            for (Category c : cats.values()) present.addAll(c.items.keySet());
            java.util.Map<String, Entry> existing = new java.util.HashMap<>();
            for (Category c : cats.values()) existing.putAll(c.items);
            int newCats = 0, newItems = 0, locked = 0;
            for (String id : cs.getKeys(false)) {
                ConfigurationSection s = cs.getConfigurationSection(id);
                if (s == null) continue;
                Category c = cats.get(id);
                if (c == null) {
                    Material icon = Material.matchMaterial(s.getString("icon", "CHEST"));
                    c = new Category(id, s.getString("name", id), icon == null ? Material.CHEST : icon);
                    cats.put(id, c);
                    newCats++;
                }
                ConfigurationSection is = s.getConfigurationSection("items");
                if (is == null) continue;
                for (String key : is.getKeys(false)) {
                    ConfigurationSection e = is.getConfigurationSection(key);
                    if (present.contains(key)) {
                        // règles du catalogue : un item déclaré non revendable le devient aussi dans votre boutique
                        Entry ex = existing.get(key);
                        if (ex != null && e != null && e.getDouble("sell", 0) < 0 && ex.sell >= 0) { ex.sell = -1; locked++; }
                        continue;
                    }
                    if (stack(key, 1) == null) continue;
                    if (e == null) continue;
                    Entry en = new Entry(key, e.getDouble("buy", -1), e.getDouble("sell", -1));
                    en.enabled = e.getBoolean("enabled", true);
                    c.items.put(key, en);
                    present.add(key);
                    newItems++;
                }
            }
            save();
            return new int[]{newCats, newItems, locked};
        } catch (Exception ex) {
            plugin.getLogger().warning("shop : import impossible (" + ex.getMessage() + ")");
            return new int[]{0, 0, 0};
        }
    }

    /**
     * Retire de votre boutique tout item absent du catalogue par défaut (items de villageois, élytres, netherite façonné,
     * ficelle, shulker boxes...), ainsi que les catégories devenues vides (dont « Items Vortex »). Les items citem: sont conservés.
     * Renvoie {items retirés, catégories supprimées}.
     */
    public int[] purgeToDefaults() {
        try (InputStream in = plugin.getResource("shop-default.yml")) {
            if (in == null) return new int[]{0, 0};
            org.bukkit.configuration.file.YamlConfiguration def = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(
                    new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8));
            ConfigurationSection dcs = def.getConfigurationSection("categories");
            if (dcs == null) return new int[]{0, 0};
            java.util.Set<String> keep = new java.util.HashSet<>();
            java.util.Set<String> defaultCats = new java.util.HashSet<>(dcs.getKeys(false));
            for (String id : dcs.getKeys(false)) {
                ConfigurationSection is = dcs.getConfigurationSection(id + ".items");
                if (is != null) keep.addAll(is.getKeys(false));
            }
            int removed = 0, emptied = 0;
            for (java.util.Iterator<java.util.Map.Entry<String, Category>> ci = cats.entrySet().iterator(); ci.hasNext(); ) {
                java.util.Map.Entry<String, Category> ce = ci.next();
                Category c = ce.getValue();
                for (java.util.Iterator<String> it = c.items.keySet().iterator(); it.hasNext(); ) {
                    String key = it.next();
                    if (key.startsWith("citem:") || keep.contains(key)) continue;
                    it.remove();
                    removed++;
                }
                if (c.items.isEmpty() && !defaultCats.contains(ce.getKey())) {
                    ci.remove();
                    emptied++;
                }
            }
            save();
            return new int[]{removed, emptied};
        } catch (Exception ex) {
            plugin.getLogger().warning("shop : purge impossible (" + ex.getMessage() + ")");
            return new int[]{0, 0};
        }
    }

    /** Remplace shop.yml par le catalogue par défaut (ancien fichier sauvegardé en shop.yml.bak). Renvoie une erreur ou null. */
    public String resetToDefault() {
        File f = new File(plugin.getDataFolder(), "shop.yml");
        try (InputStream in = plugin.getResource("shop-default.yml")) {
            if (in == null) return "Catalogue par défaut introuvable.";
            if (f.exists()) {
                Files.copy(f.toPath(), new File(plugin.getDataFolder(), "shop.yml.bak").toPath(),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            Files.copy(in, f.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            return "Erreur : " + e.getMessage();
        }
        load();
        return null;
    }

    public void save() {
        store.get().set("categories", null);
        for (Category c : cats.values()) {
            String p = "categories." + c.id;
            store.get().set(p + ".name", c.name);
            store.get().set(p + ".icon", c.icon.name());
            store.get().set(p + ".enabled", c.enabled);
            for (Entry e : c.items.values()) {
                store.get().set(p + ".items." + e.key + ".buy", e.buy);
                store.get().set(p + ".items." + e.key + ".sell", e.sell);
                store.get().set(p + ".items." + e.key + ".enabled", e.enabled);
            }
        }
        store.save();
    }

    // ═════════════ Catégories ═════════════

    public List<Category> categories() { return new ArrayList<>(cats.values()); }
    public Category category(String id) { return cats.get(id == null ? "" : id.toLowerCase(Locale.ROOT)); }

    public static String cleanId(String raw) { return raw.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "_"); }

    public String createCategory(String rawId, Material icon, String name) {
        String id = cleanId(rawId);
        if (id.isBlank()) return "Identifiant invalide.";
        if (cats.containsKey(id)) return "Cette catégorie existe déjà.";
        cats.put(id, new Category(id, name, icon));
        save();
        return null;
    }

    public boolean deleteCategory(String id) {
        if (cats.remove(cleanId(id)) == null) return false;
        save();
        return true;
    }

    // ═════════════ Entrées ═════════════

    /** Clé d'entrée pour un item (Vortex, personnalisé ou matériau). */
    public String keyOf(ItemStack s) {
        String vid = ItemUtil.idOf(s);
        if (vid != null) return "vortex:" + vid;
        String cid = plugin.customItems().idOf(s);
        if (cid != null) return "citem:" + cid;
        return s.getType().name();
    }

    /** Normalise une clé saisie ; null si elle ne correspond à aucun item. */
    public String normalizeKey(String raw) {
        if (raw == null) return null;
        String k = raw.trim();
        String low = k.toLowerCase(Locale.ROOT);
        if (low.startsWith("vortex:")) return VortexItem.byId(low.substring(7)) == null ? null : "vortex:" + VortexItem.byId(low.substring(7)).id();
        if (low.startsWith("citem:")) return plugin.customItems().exists(low.substring(6)) ? "citem:" + low.substring(6) : null;
        Material m = Material.matchMaterial(k);
        return m != null && m.isItem() && !m.isAir() ? m.name() : null;
    }

    /** Item correspondant à une clé (quantité donnée), ou null. */
    public ItemStack stack(String key, int amount) {
        ItemStack s = null;
        if (key.startsWith("vortex:")) {
            VortexItem v = VortexItem.byId(key.substring(7));
            if (v != null) s = ItemUtil.create(v);
        } else if (key.startsWith("citem:")) {
            s = plugin.customItems().build(key.substring(6));
        } else {
            Material m = Material.matchMaterial(key);
            if (m != null && m.isItem() && !m.isAir()) s = new ItemStack(m);
        }
        if (s != null) s.setAmount(Math.max(1, Math.min(amount, s.getMaxStackSize())));
        return s;
    }

    /** Nom lisible d'une entrée. */
    public String displayName(String key) {
        if (key.startsWith("vortex:")) {
            VortexItem v = VortexItem.byId(key.substring(7));
            return v == null ? key : v.displayName();
        }
        if (key.startsWith("citem:")) return key.substring(6);
        return key.toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    public String addEntry(String catId, String key, double buy, double sell) {
        Category c = category(catId);
        if (c == null) return "Catégorie introuvable.";
        String k = normalizeKey(key);
        if (k == null) return "Item inconnu.";
        if (k.startsWith("vortex:") && VortexItem.byId(k.substring(7)).isMythicWeapon()) {
            return "Les armes mythiques ne peuvent pas être vendues en boutique (forge unique).";
        }
        c.items.put(k, new Entry(k, buy <= 0 ? -1 : buy, sell <= 0 ? -1 : sell));
        save();
        return null;
    }

    public boolean removeEntry(String catId, String key) {
        Category c = category(catId);
        String k = normalizeKey(key);
        if (c == null || k == null || c.items.remove(k) == null) return false;
        save();
        return true;
    }

    public Entry entry(String catId, String key) {
        Category c = category(catId);
        String k = normalizeKey(key);
        return c == null || k == null ? null : c.items.get(k);
    }

    // ═════════════ Prix (avec boosts) ═════════════

    private static double round2(double v) { return Math.round(v * 100.0) / 100.0; }

    /** Prix d'achat unitaire pour ce joueur (réduction de grade appliquée), -1 si non achetable. */
    public double buyUnit(Player p, Entry e) {
        return e.buy < 0 ? -1 : round2(e.buy * plugin.boosts().buyMultiplier(p) * wealthMultiplier(p));
    }

    /**
     * Plus un joueur est riche, plus les prix d'achat montent (shop.wealth-scaling) :
     * +per-doubling à chaque doublement de la fortune au-delà de base-balance, plafonné à max.
     */
    public double wealthMultiplier(Player p) {
        var c = plugin.getConfig();
        if (!c.getBoolean("shop.wealth-scaling.enabled", true) || !plugin.money().available()) return 1.0;
        double base = Math.max(1, c.getDouble("shop.wealth-scaling.base-balance", 10000));
        double bal = plugin.money().balance(p);
        if (bal <= base) return 1.0;
        double doublings = Math.log(bal / base) / Math.log(2);
        return Math.min(c.getDouble("shop.wealth-scaling.max", 3.0), 1.0 + c.getDouble("shop.wealth-scaling.per-doubling", 0.06) * doublings);
    }

    /** Catégorie qui contient déjà cette clé, ou null. */
    public String categoryOf(String key) {
        for (Category c : cats.values()) if (c.items.containsKey(key)) return c.id;
        return null;
    }

    /** Catégorie choisie automatiquement pour un matériau absent de la boutique. */
    public String autoCategory(Material m) {
        String n = m.name(), id;
        if (n.endsWith("_PICKAXE") || n.endsWith("_AXE") || n.endsWith("_SHOVEL") || n.endsWith("_HOE") || n.endsWith("_SWORD")
                || n.equals("BOW") || n.equals("CROSSBOW") || n.equals("TRIDENT") || n.equals("MACE") || n.equals("SHEARS")) id = "tools";
        else if (n.endsWith("_HELMET") || n.endsWith("_CHESTPLATE") || n.endsWith("_LEGGINGS") || n.endsWith("_BOOTS") || n.equals("SHIELD")) id = "armor";
        else if (n.startsWith("MUSIC_DISC")) id = "discs";
        else if (m.isEdible()) id = "food";
        else if (n.contains("PLANKS") || n.contains("_LOG") || n.contains("_WOOD")) id = "wood";
        else if (n.endsWith("_ORE") || n.contains("INGOT") || n.contains("RAW_") || n.contains("NUGGET")) id = "ores";
        else if (m.isBlock()) id = "blocks";
        else id = "misc";
        if (cats.containsKey(id)) return id;
        return cats.isEmpty() ? null : cats.keySet().iterator().next();
    }


    /** Prix de vente unitaire pour ce joueur (bonus de grade appliqué), -1 si non vendable. */
    public double sellUnit(Player p, Entry e) {
        if (e.sell < 0) return -1;
        double v = round2(e.sell * plugin.boosts().sellMultiplier(p));
        // Garde-fou anti-arbitrage : la vente (bonus compris) ne dépasse jamais l'achat (réduction comprise)
        if (e.buy >= 0) v = Math.min(v, buyUnit(p, e));
        return v;
    }

    // ═════════════ Reconnaissance des items à la vente ═════════════

    /** Item "propre" : sans nom, lore, enchantement, usure ni donnée plugin (on ne vend pas un item modifié au prix du vanilla). */
    public static boolean plain(ItemStack s) {
        if (!s.hasItemMeta()) return true;
        ItemMeta m = s.getItemMeta();
        return !m.hasDisplayName() && !m.hasLore() && !m.hasEnchants() && m.getPersistentDataContainer().isEmpty()
                && (!(m instanceof Damageable d) || d.getDamage() == 0);
    }

    public boolean matches(Entry e, ItemStack s) {
        if (s == null || s.getType().isAir()) return false;
        if (e.key.startsWith("vortex:")) return e.key.substring(7).equals(ItemUtil.idOf(s));
        if (e.key.startsWith("citem:")) return e.key.substring(6).equals(plugin.customItems().idOf(s));
        return s.getType().name().equals(e.key) && plain(s);
    }

    /** Index matériau -> entrée vendable (pour « tout vendre »). */
    public Map<String, Entry> sellIndex() {
        Map<String, Entry> idx = new HashMap<>();
        for (Category c : cats.values()) {
            if (!c.enabled) continue;
            for (Entry e : c.items.values()) if (e.enabled && e.sell >= 0) idx.putIfAbsent(e.key, e);
        }
        return idx;
    }
}
