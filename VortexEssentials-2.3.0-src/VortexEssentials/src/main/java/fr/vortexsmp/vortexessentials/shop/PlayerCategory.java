package fr.vortexsmp.vortexessentials.shop;

import fr.vortexsmp.vortexessentials.item.ItemUtil;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.Set;

/** Catégories (automatiques) des annonces de la boutique joueurs. */
public enum PlayerCategory {
    BLOCKS("Blocs", Material.GRASS_BLOCK),
    ORES("Minerais", Material.DIAMOND),
    FOOD("Nourriture", Material.COOKED_BEEF),
    TOOLS("Outils & armes", Material.DIAMOND_PICKAXE),
    ARMOR("Armures", Material.DIAMOND_CHESTPLATE),
    POTIONS("Potions", Material.POTION),
    REDSTONE("Redstone", Material.REDSTONE),
    ENCHANTED("Enchantés", Material.ENCHANTED_BOOK),
    VORTEX("Items Vortex", Material.NETHER_STAR),
    MISC("Divers", Material.CHEST);

    private static final Set<Material> REDSTONE_ITEMS = Set.of(Material.REDSTONE, Material.REPEATER, Material.COMPARATOR,
            Material.PISTON, Material.STICKY_PISTON, Material.OBSERVER, Material.HOPPER, Material.DROPPER, Material.DISPENSER,
            Material.TNT, Material.DAYLIGHT_DETECTOR, Material.LEVER, Material.REDSTONE_LAMP, Material.REDSTONE_TORCH,
            Material.REDSTONE_BLOCK, Material.TARGET, Material.NOTE_BLOCK, Material.SLIME_BLOCK, Material.HONEY_BLOCK);

    private final String label;
    private final Material icon;

    PlayerCategory(String label, Material icon) {
        this.label = label;
        this.icon = icon;
    }

    public String label() { return label; }
    public Material icon() { return icon; }

    public static PlayerCategory of(ItemStack s) {
        if (ItemUtil.idOf(s) != null) return VORTEX;
        Material m = s.getType();
        String n = m.name();
        if (m == Material.ENCHANTED_BOOK || (s.hasItemMeta() && s.getItemMeta().hasEnchants())) return ENCHANTED;
        if (m.isEdible()) return FOOD;
        if (n.contains("POTION") || n.equals("TIPPED_ARROW")) return POTIONS;
        if (n.endsWith("_HELMET") || n.endsWith("_CHESTPLATE") || n.endsWith("_LEGGINGS") || n.endsWith("_BOOTS")
                || m == Material.SHIELD || m == Material.ELYTRA) return ARMOR;
        if (n.endsWith("_SWORD") || n.endsWith("_AXE") || n.endsWith("_PICKAXE") || n.endsWith("_SHOVEL") || n.endsWith("_HOE")
                || m == Material.BOW || m == Material.CROSSBOW || m == Material.TRIDENT || m == Material.FISHING_ROD
                || m == Material.SHEARS || m == Material.MACE) return TOOLS;
        if (REDSTONE_ITEMS.contains(m)) return REDSTONE;
        if (n.endsWith("_ORE") || n.endsWith("_INGOT") || n.startsWith("RAW_") || n.endsWith("_NUGGET")
                || m == Material.DIAMOND || m == Material.EMERALD || m == Material.COAL || m == Material.CHARCOAL
                || m == Material.LAPIS_LAZULI || m == Material.QUARTZ || m == Material.AMETHYST_SHARD
                || m == Material.NETHERITE_SCRAP || m == Material.ANCIENT_DEBRIS
                || n.equals("IRON_BLOCK") || n.equals("GOLD_BLOCK") || n.equals("DIAMOND_BLOCK") || n.equals("EMERALD_BLOCK")
                || n.equals("NETHERITE_BLOCK") || n.equals("COPPER_BLOCK") || n.equals("COAL_BLOCK") || n.equals("LAPIS_BLOCK")) return ORES;
        if (m.isBlock()) return BLOCKS;
        return MISC;
    }
}
