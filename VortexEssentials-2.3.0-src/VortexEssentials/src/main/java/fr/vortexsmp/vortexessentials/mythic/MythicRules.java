package fr.vortexsmp.vortexessentials.mythic;

import fr.vortexsmp.vortexessentials.item.ItemUtil;
import fr.vortexsmp.vortexessentials.item.VortexItem;
import fr.vortexsmp.vortexessentials.menu.MenuHolder;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.EnumSet;
import java.util.Set;

/** Règles partagées des armes mythiques (Sealord, Lightspeed, Mintus). */
public final class MythicRules {
    /** Seuls ces inventaires "du haut" ne sont pas considérés comme du stockage. */
    private static final Set<InventoryType> SAFE =
            EnumSet.of(InventoryType.CRAFTING, InventoryType.PLAYER, InventoryType.CREATIVE);

    private MythicRules() {}

    public static boolean isMythicWeapon(ItemStack s) {
        VortexItem v = ItemUtil.get(s);
        return v != null && v.isMythicWeapon();
    }

    /** Nombre d'armes mythiques portées (inventaire, armure, main secondaire). */
    /** Nombre d'objets mythiques de ce genre (armure ou arme) portés. */
    public static int carried(Player p, boolean armor) {
        PlayerInventory inv = p.getInventory();
        int n = 0;
        for (ItemStack s : inv.getStorageContents()) if (isKind(s, armor)) n++;
        for (ItemStack s : inv.getArmorContents()) if (isKind(s, armor)) n++;
        for (ItemStack s : inv.getExtraContents()) if (isKind(s, armor)) n++;
        return n;
    }

    private static boolean isKind(ItemStack s, boolean armor) {
        VortexItem v = ItemUtil.get(s);
        return v != null && v.isMythicWeapon() && v.isMythicArmor() == armor;
    }

    public static int carried(Player p) {
        PlayerInventory inv = p.getInventory();
        int n = 0;
        for (ItemStack s : inv.getStorageContents()) if (isMythicWeapon(s)) n++;
        for (ItemStack s : inv.getArmorContents()) if (isMythicWeapon(s)) n++;
        for (ItemStack s : inv.getExtraContents()) if (isMythicWeapon(s)) n++;
        return n;
    }

    /**
     * Vrai si l'inventaire du haut est un stockage où une arme mythique ne doit pas entrer
     * (coffre, ender chest, baril, shulker, four, entonnoir, dropper, dispenser, alambic,
     * marchand, coffre de monture/bateau...). Liste blanche : tout ce qui n'est pas explicitement
     * sûr est interdit. Les menus du plugin sont exclus (leurs clics sont annulés ailleurs).
     */
    public static boolean restricted(Inventory top) {
        if (top == null) return false;
        if (top.getHolder() instanceof MenuHolder) return false;
        return !SAFE.contains(top.getType());
    }

    public static boolean isBundle(ItemStack s) {
        return s != null && s.getType().name().endsWith("BUNDLE");
    }
}
