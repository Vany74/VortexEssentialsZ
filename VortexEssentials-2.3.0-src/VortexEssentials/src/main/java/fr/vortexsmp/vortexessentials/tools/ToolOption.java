package fr.vortexsmp.vortexessentials.tools;

import org.bukkit.Material;

public enum ToolOption {
    AREA("Zone de minage", Material.TNT, "Casse une zone carrée face au bloc (3×3 = 9 blocs)."),
    SMELT("Autosmelt", Material.FURNACE, "Cuit automatiquement les drops."),
    TREE("Abattage d'arbres", Material.OAK_LOG, "Abat l'arbre entier en un coup."),
    SILK_TOUCH("Silk Touch", Material.GRASS_BLOCK, "Récupère les blocs tels quels (prime sur l'autosmelt)."),
    VANILLA_SPEED("Vitesse de minage vanilla", Material.CLOCK,
            "Activé : aucun bonus de vitesse (ni minage instantané, ni Hâte).");

    private final String label;
    private final Material icon;
    private final String description;

    ToolOption(String label, Material icon, String description) {
        this.label = label;
        this.icon = icon;
        this.description = description;
    }

    public String label() { return label; }
    public Material icon() { return icon; }
    public String description() { return description; }
}
