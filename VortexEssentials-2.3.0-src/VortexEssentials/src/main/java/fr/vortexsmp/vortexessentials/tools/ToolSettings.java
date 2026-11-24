package fr.vortexsmp.vortexessentials.tools;

import fr.vortexsmp.vortexessentials.Keys;
import fr.vortexsmp.vortexessentials.VortexEssentials;
import fr.vortexsmp.vortexessentials.item.ItemUtil;
import fr.vortexsmp.vortexessentials.item.VortexItem;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Réglages activables par outil, stockés sur l'item (PDC). */
public final class ToolSettings {
    private static final Map<ToolOption, NamespacedKey> KEYS = new EnumMap<>(ToolOption.class);

    private ToolSettings() {}

    public static void init(Plugin p) {
        KEYS.put(ToolOption.AREA, Keys.AREA_MODE);
        KEYS.put(ToolOption.SMELT, new NamespacedKey(p, "opt_smelt"));
        KEYS.put(ToolOption.TREE, new NamespacedKey(p, "opt_tree"));
        KEYS.put(ToolOption.VANILLA_SPEED, new NamespacedKey(p, "opt_vanilla"));
        KEYS.put(ToolOption.SILK_TOUCH, new NamespacedKey(p, "opt_silk"));
    }

    /** Options proposées par un item (liste vide = pas un outil réglable). */
    public static List<ToolOption> available(VortexItem v) {
        if (v == null) return List.of();
        return switch (v) {
            case SMELTER_PICKAXE -> List.of(ToolOption.SMELT);
            case SUPER_SMELTER_PICKAXE, NETHERITE_SMELTER_PICKAXE -> List.of(ToolOption.SMELT, ToolOption.VANILLA_SPEED);
            case EXPLOSIVE_PICKAXE, NETHERITE_EXPLOSIVE_PICKAXE -> List.of(ToolOption.AREA, ToolOption.SILK_TOUCH);
            case LUMBERJACK_AXE, SUPER_LUMBERJACK_AXE, NETHERITE_LUMBERJACK_AXE -> List.of(ToolOption.TREE);
            case FRACTURED -> List.of(ToolOption.AREA, ToolOption.SMELT, ToolOption.SILK_TOUCH, ToolOption.VANILLA_SPEED);
            case TERRALOOP -> List.of(ToolOption.AREA, ToolOption.TREE, ToolOption.SILK_TOUCH, ToolOption.VANILLA_SPEED);
            case JUGAS -> List.of(ToolOption.AREA, ToolOption.SMELT, ToolOption.SILK_TOUCH, ToolOption.VANILLA_SPEED);
            default -> List.of();
        };
    }

    private static boolean defaultOf(ToolOption o) {
        if (o == ToolOption.SILK_TOUCH) return false;
        if (o != ToolOption.VANILLA_SPEED) return true;
        VortexEssentials pl = VortexEssentials.get();
        return pl != null && pl.getConfig().getBoolean("tools.vanilla-speed-default", false);
    }

    /** Lit une option dans un PDC (null = valeurs par défaut). */
    public static boolean read(PersistentDataContainer pdc, ToolOption o) {
        if (pdc == null || KEYS.get(o) == null) return defaultOf(o);
        Byte b = pdc.get(KEYS.get(o), PersistentDataType.BYTE);
        return b == null ? defaultOf(o) : b == 1;
    }

    public static boolean get(ItemStack tool, ToolOption o) {
        if (tool == null || !tool.hasItemMeta()) return defaultOf(o);
        return read(tool.getItemMeta().getPersistentDataContainer(), o);
    }

    /** Modifie l'option et met à jour le lore. */
    public static void set(ItemStack tool, ToolOption o, boolean on) {
        ItemMeta m = tool.getItemMeta();
        m.getPersistentDataContainer().set(KEYS.get(o), PersistentDataType.BYTE, (byte) (on ? 1 : 0));
        tool.setItemMeta(m);
        ItemUtil.refreshLore(tool);
    }
}
