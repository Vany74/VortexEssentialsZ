package fr.vortexsmp.vortexessentials.limit;

import fr.vortexsmp.vortexessentials.Keys;
import fr.vortexsmp.vortexessentials.YamlStore;
import fr.vortexsmp.vortexessentials.item.ItemUtil;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

/**
 * Bypass des limites : individuel (PDC), par matériau, par ID d'objet.
 * Tout item Vortex custom est automatiquement exempté (sinon Sharpness 6 sur Sealord serait retiré).
 */
public final class BypassManager {
    private final Plugin plugin;
    private final YamlStore store;
    private final Set<String> materials = new HashSet<>();
    private final Set<String> objectIds = new HashSet<>();

    public BypassManager(Plugin plugin, YamlStore store) {
        this.plugin = plugin;
        this.store = store;
        load();
    }

    public void load() {
        materials.clear();
        objectIds.clear();
        materials.addAll(store.get().getStringList("bypass.materials"));
        objectIds.addAll(store.get().getStringList("bypass.object-ids"));
    }

    public void save() {
        store.get().set("bypass.materials", new ArrayList<>(materials));
        store.get().set("bypass.object-ids", new ArrayList<>(objectIds));
        store.save();
    }

    private boolean flag(String key) {
        return plugin.getConfig().getBoolean("limit.bypass." + key, true);
    }

    private static boolean valid(ItemStack i) {
        return i != null && !i.getType().isAir() && i.hasItemMeta();
    }

    // ───────── Bypass individuel ─────────

    public boolean individual(ItemStack i) {
        if (!valid(i)) return false;
        PersistentDataContainer c = i.getItemMeta().getPersistentDataContainer();
        return isOn(c, Keys.LIMIT_BYPASS) || isOn(c, Keys.LEGACY_LIMIT_BYPASS);
    }

    private static boolean isOn(PersistentDataContainer c, NamespacedKey k) {
        if (k == null) return false;
        Byte b = c.get(k, PersistentDataType.BYTE);
        return b != null && b == 1;
    }

    public void mark(ItemStack i, boolean on) {
        if (i == null || i.getType().isAir()) return;
        ItemMeta m = i.getItemMeta();
        if (m == null) return;
        if (on) {
            m.getPersistentDataContainer().set(Keys.LIMIT_BYPASS, PersistentDataType.BYTE, (byte) 1);
        } else {
            m.getPersistentDataContainer().remove(Keys.LIMIT_BYPASS);
            if (Keys.LEGACY_LIMIT_BYPASS != null) m.getPersistentDataContainer().remove(Keys.LEGACY_LIMIT_BYPASS);
        }
        i.setItemMeta(m);
    }

    // ───────── ID d'objet ─────────

    public String objectId(ItemStack i) {
        if (!valid(i)) return null;
        PersistentDataContainer c = i.getItemMeta().getPersistentDataContainer();
        String id = c.get(Keys.OBJECT_ID, PersistentDataType.STRING);
        if (id == null && Keys.LEGACY_OBJECT_ID != null) id = c.get(Keys.LEGACY_OBJECT_ID, PersistentDataType.STRING);
        return id;
    }

    public void setObjectId(ItemStack i, String id) {
        if (i == null || i.getType().isAir()) return;
        ItemMeta m = i.getItemMeta();
        if (m == null) return;
        if (id == null || id.isBlank()) {
            m.getPersistentDataContainer().remove(Keys.OBJECT_ID);
        } else {
            m.getPersistentDataContainer().set(Keys.OBJECT_ID, PersistentDataType.STRING, id);
        }
        i.setItemMeta(m);
    }

    public boolean objectBypass(ItemStack i) {
        String id = objectId(i);
        return id != null && objectIds.contains(id);
    }

    public void addObjectId(String id) { objectIds.add(id); save(); }
    public void removeObjectId(String id) { objectIds.remove(id); save(); }
    public boolean hasObjectId(String id) { return objectIds.contains(id); }

    // ───────── Matériau ─────────

    public boolean permanentMaterial(ItemStack i) {
        return i != null && materials.contains(i.getType().getKey().toString());
    }

    public void setMaterial(Material m, boolean on) {
        if (on) materials.add(m.getKey().toString());
        else materials.remove(m.getKey().toString());
        save();
    }

    public int materialCount() { return materials.size(); }
    public int objectIdCount() { return objectIds.size(); }

    // ───────── Verdict ─────────

    public boolean isBypass(ItemStack i) {
        if (i == null || i.getType().isAir()) return false;
        if (flag("vortex-items") && ItemUtil.idOf(i) != null) return true; // items Vortex (mythiques, légendaires, outils...)
        return (flag("individual-items") && individual(i))
                || (flag("custom-object-ids") && objectBypass(i))
                || (flag("permanent-material-types") && permanentMaterial(i));
    }
}
