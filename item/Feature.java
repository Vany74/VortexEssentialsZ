package fr.vortexsmp.vortexessentials.item;

import org.bukkit.Material;

import java.util.List;

/** Fonctionnalités regroupées dans le menu : quels items en bénéficient. */
public enum Feature {
    INSTANT("Minage instantané", Material.NETHERITE_PICKAXE, "Casse tous les blocs en un instant.",
            VortexItem.FRACTURED, VortexItem.TERRALOOP, VortexItem.JUGAS),
    AREA("Zone de minage", Material.TNT, "Casse une zone cubique d'un coup.",
            VortexItem.EXPLOSIVE_PICKAXE, VortexItem.NETHERITE_EXPLOSIVE_PICKAXE,
            VortexItem.FRACTURED, VortexItem.TERRALOOP, VortexItem.JUGAS),
    AUTOSMELT("Autosmelt", Material.FURNACE, "Cuit automatiquement les drops.",
            VortexItem.SMELTER_PICKAXE, VortexItem.SUPER_SMELTER_PICKAXE, VortexItem.NETHERITE_SMELTER_PICKAXE,
            VortexItem.FRACTURED, VortexItem.JUGAS),
    TREE("Abattage d'arbres", Material.OAK_LOG, "Abat l'arbre entier en un coup.",
            VortexItem.LUMBERJACK_AXE, VortexItem.SUPER_LUMBERJACK_AXE, VortexItem.NETHERITE_LUMBERJACK_AXE,
            VortexItem.TERRALOOP),
    ACTIVE("Capacités actives", Material.BLAZE_POWDER, "Compétences à déclencher (cooldown affiché).",
            VortexItem.REAPER_SCYTHE, VortexItem.EXCALIBUR, VortexItem.EVOKER_WAND, VortexItem.CHRONO_SWORD,
            VortexItem.SCEPTRE_OF_ARACHNE, VortexItem.SEALORD, VortexItem.LIGHTSPEED, VortexItem.MINTUS),
    PASSIVE("Effets permanents", Material.BEACON, "Effets actifs en main ou dans l'inventaire.",
            VortexItem.SEALORD, VortexItem.LIGHTSPEED, VortexItem.MINTUS, VortexItem.MAGMA_CLUB,
            VortexItem.SCEPTRE_OF_ARACHNE),
    UNIQUE("Forge unique", Material.NETHER_STAR, "Un seul exemplaire par serveur.",
            VortexItem.SEALORD, VortexItem.LIGHTSPEED, VortexItem.MINTUS);

    private final String label;
    private final Material icon;
    private final String description;
    private final List<VortexItem> items;

    Feature(String label, Material icon, String description, VortexItem... items) {
        this.label = label;
        this.icon = icon;
        this.description = description;
        this.items = List.of(items);
    }

    public String label() { return label; }
    public Material icon() { return icon; }
    public String description() { return description; }
    public List<VortexItem> items() { return items; }
}
