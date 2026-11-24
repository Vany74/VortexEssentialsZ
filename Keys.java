package fr.vortexsmp.vortexessentials;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;

/**
 * Clés PDC. Les clés "legacy" permettent de reconnaître les items déjà créés par
 * VortexTools et VortexLimit : ils continuent de fonctionner sans migration.
 */
public final class Keys {
    public static NamespacedKey VORTEX_ID;
    public static NamespacedKey LIMIT_BYPASS;
    public static NamespacedKey OBJECT_ID;
    public static NamespacedKey AREA_MODE;
    public static NamespacedKey CHARGES;
    public static NamespacedKey HUD_OFF;
    public static NamespacedKey CUSTOM_ITEM;
    public static NamespacedKey LEGACY_CHARGES;

    public static NamespacedKey LEGACY_VORTEX_ID;    // vortextools:vortex_id
    public static NamespacedKey LEGACY_LIMIT_BYPASS; // vortexlimit:limit_bypass
    public static NamespacedKey LEGACY_OBJECT_ID;    // vortexlimit:object_id

    private Keys() {}

    public static void init(Plugin p) {
        VORTEX_ID = new NamespacedKey(p, "vortex_id");
        LIMIT_BYPASS = new NamespacedKey(p, "limit_bypass");
        OBJECT_ID = new NamespacedKey(p, "object_id");
        AREA_MODE = new NamespacedKey(p, "area_mode");
        CHARGES = new NamespacedKey(p, "charges");
        HUD_OFF = new NamespacedKey(p, "hud_off");
        CUSTOM_ITEM = new NamespacedKey(p, "custom_item");
        LEGACY_CHARGES = NamespacedKey.fromString("vortextools:charges");

        LEGACY_VORTEX_ID = NamespacedKey.fromString("vortextools:vortex_id");
        LEGACY_LIMIT_BYPASS = NamespacedKey.fromString("vortexlimit:limit_bypass");
        LEGACY_OBJECT_ID = NamespacedKey.fromString("vortexlimit:object_id");
    }
}
