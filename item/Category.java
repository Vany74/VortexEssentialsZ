package fr.vortexsmp.vortexessentials.item;

import org.bukkit.Material;

public enum Category {
    TOOL("Outils", Material.IRON_PICKAXE, "§b"),
    MYTHIC_TOOL("Outils Mythiques", Material.NETHERITE_PICKAXE, "§5"),
    LEGENDARY_WEAPON("Armes Légendaires", Material.NETHERITE_SWORD, "§6"),
    MYTHIC_WEAPON("Armes & Armures Mythiques", Material.TRIDENT, "§c"),
    SECRET_WEAPON("Secret Weapons", Material.WITHER_SKELETON_SKULL, "§5"),
    CONSUMABLE("Consommables", Material.POTION, "§d");

    private final String label;
    private final Material icon;
    private final String color;

    Category(String label, Material icon, String color) {
        this.label = label;
        this.icon = icon;
        this.color = color;
    }

    public String label() { return label; }
    public Material icon() { return icon; }
    public String color() { return color; }
}
