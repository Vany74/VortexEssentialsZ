package fr.vortexsmp.vortexessentials.admin;

import fr.vortexsmp.vortexessentials.Keys;
import fr.vortexsmp.vortexessentials.Msg;
import fr.vortexsmp.vortexessentials.YamlStore;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Items créés en jeu avec des actions déclenchables (custom-items.yml).
 * Un item = apparence (nom, lore, enchantements...) + options (cooldown, consommable, permission)
 * + actions par activateur (clic droit, clic gauche, sneak, coup).
 */
public final class CustomItems {
    public static final List<String> ACTIVATORS =
            List.of("RIGHT_CLICK", "LEFT_CLICK", "SNEAK_RIGHT_CLICK", "SNEAK_LEFT_CLICK", "HIT");
    public static final List<String> OPTIONS =
            List.of("cooldown", "consume", "permission", "glow", "material", "model", "unbreakable", "hide-flags");
    private static final LegacyComponentSerializer AMP = LegacyComponentSerializer.legacyAmpersand();

    private final YamlStore store;

    public CustomItems(Plugin plugin) {
        this.store = new YamlStore(plugin, "custom-items.yml");
    }

    public void reload() { store.reload(); }

    public static String clean(String id) { return id.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "_"); }

    public List<String> ids() {
        ConfigurationSection s = store.get().getConfigurationSection("items");
        return s == null ? List.of() : new ArrayList<>(s.getKeys(false));
    }

    public boolean exists(String id) { return store.get().contains("items." + clean(id)); }

    public ConfigurationSection sec(String id) { return store.get().getConfigurationSection("items." + clean(id)); }

    public String idOf(ItemStack s) {
        if (s == null || !s.hasItemMeta()) return null;
        return s.getItemMeta().getPersistentDataContainer().get(Keys.CUSTOM_ITEM, PersistentDataType.STRING);
    }

    // ───────── Création / édition ─────────

    /** Crée l'item d'après celui tenu en main. Renvoie un message d'erreur ou null. */
    public String createFromHand(String rawId, ItemStack hand) {
        String id = clean(rawId);
        if (id.isBlank()) return "Identifiant invalide.";
        if (hand == null || hand.getType().isAir()) return "Tenez un item en main.";
        if (exists(id)) return "Cet identifiant existe déjà.";

        String p = "items." + id;
        store.get().set(p + ".material", hand.getType().name());
        ItemMeta m = hand.getItemMeta();
        if (m != null) {
            if (m.hasDisplayName() && m.displayName() != null) store.get().set(p + ".name", AMP.serialize(m.displayName()));
            if (m.lore() != null) store.get().set(p + ".lore", m.lore().stream().map(AMP::serialize).toList());
            for (Map.Entry<Enchantment, Integer> e : m.getEnchants().entrySet()) {
                store.get().set(p + ".enchants." + e.getKey().getKey().getKey(), e.getValue());
            }
            if (m.isUnbreakable()) store.get().set(p + ".unbreakable", true);
        }
        store.save();
        return null;
    }

    public boolean delete(String id) {
        if (!exists(id)) return false;
        store.get().set("items." + clean(id), null);
        store.save();
        return true;
    }

    public void setName(String id, String text) { store.get().set("items." + clean(id) + ".name", text); store.save(); }

    public void addLore(String id, String text) {
        List<String> l = new ArrayList<>(store.get().getStringList("items." + clean(id) + ".lore"));
        l.add(text);
        store.get().set("items." + clean(id) + ".lore", l);
        store.save();
    }

    public void clearLore(String id) { store.get().set("items." + clean(id) + ".lore", null); store.save(); }

    /** Renvoie un message d'erreur ou null. */
    public String setOption(String id, String key, String value) {
        String p = "items." + clean(id) + ".";
        switch (key.toLowerCase(Locale.ROOT)) {
            case "cooldown" -> {
                try { store.get().set(p + "cooldown", Math.max(0, Integer.parseInt(value))); }
                catch (NumberFormatException e) { return "Nombre invalide."; }
            }
            case "consume", "glow", "unbreakable", "hide-flags" -> store.get().set(p + key.toLowerCase(Locale.ROOT), Boolean.parseBoolean(value));
            case "permission", "model" -> store.get().set(p + key.toLowerCase(Locale.ROOT), value.equalsIgnoreCase("none") ? null : value);
            case "material" -> {
                Material m = Material.matchMaterial(value);
                if (m == null || !m.isItem()) return "Matériau inconnu.";
                store.get().set(p + "material", m.name());
            }
            default -> { return "Option inconnue (" + String.join(", ", OPTIONS) + ")."; }
        }
        store.save();
        return null;
    }

    public List<String> actions(String id, String activator) {
        return store.get().getStringList("items." + clean(id) + ".activators." + activator);
    }

    public void addAction(String id, String activator, String action) {
        List<String> l = new ArrayList<>(actions(id, activator));
        l.add(action);
        store.get().set("items." + clean(id) + ".activators." + activator, l);
        store.save();
    }

    public void clearActions(String id, String activator) {
        store.get().set("items." + clean(id) + ".activators." + activator, null);
        store.save();
    }

    // ───────── Construction de l'item ─────────

    public ItemStack build(String rawId) {
        String id = clean(rawId);
        ConfigurationSection s = sec(id);
        if (s == null) return null;
        Material mat = Material.matchMaterial(s.getString("material", "STONE"));
        if (mat == null || !mat.isItem()) mat = Material.STONE;

        ItemStack it = new ItemStack(mat);
        ItemMeta m = it.getItemMeta();
        if (s.getString("name") != null) {
            m.displayName(AMP.deserialize(s.getString("name")).decoration(TextDecoration.ITALIC, false));
        }
        List<Component> lore = new ArrayList<>();
        for (String l : s.getStringList("lore")) lore.add(AMP.deserialize(l).decoration(TextDecoration.ITALIC, false));
        if (!lore.isEmpty()) m.lore(lore);

        ConfigurationSection en = s.getConfigurationSection("enchants");
        if (en != null) {
            for (String k : en.getKeys(false)) {
                Enchantment e = Registry.ENCHANTMENT.get(NamespacedKey.minecraft(k));
                if (e != null) m.addEnchant(e, en.getInt(k, 1), true);
            }
        }
        if (s.getBoolean("unbreakable")) m.setUnbreakable(true);
        if (s.getBoolean("glow")) m.setEnchantmentGlintOverride(true);
        if (s.getBoolean("hide-flags")) m.addItemFlags(ItemFlag.values());
        if (s.getString("model") != null) m.setItemModel(NamespacedKey.fromString(s.getString("model")));
        m.getPersistentDataContainer().set(Keys.CUSTOM_ITEM, PersistentDataType.STRING, id);
        it.setItemMeta(m);
        return it;
    }

    public static Component color(String amp) { return Msg.raw(amp.replace('&', '§')); }
}
