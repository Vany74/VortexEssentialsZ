package fr.vortexsmp.vortexessentials.menu;

import fr.vortexsmp.vortexessentials.Msg;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/** Briques d'interface partagées par tous les menus. */
public final class Ui {
    private Ui() {}

    public static ItemStack button(Material m, String name, String... lore) {
        ItemStack i = new ItemStack(m);
        ItemMeta meta = i.getItemMeta();
        meta.displayName(Msg.plain(name));
        if (lore.length > 0) {
            List<Component> l = new ArrayList<>();
            for (String s : lore) l.add(Msg.plain(s));
            meta.lore(l);
        }
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        i.setItemMeta(meta);
        return i;
    }

    /** Fond sombre sur [from, to[. Si c'est tout l'inventaire, la première et la dernière ligne prennent la couleur d'accent. */
    public static void frame(Inventory inv, int from, int to, Material accent) {
        ItemStack dark = button(Material.BLACK_STAINED_GLASS_PANE, " ");
        ItemStack acc = button(accent, " ");
        for (int i = from; i < to; i++) inv.setItem(i, dark);
        if (from == 0 && to == inv.getSize() && inv.getSize() >= 18) {
            for (int i = 0; i < 9; i++) {
                inv.setItem(i, acc);
                inv.setItem(inv.getSize() - 1 - i, acc);
            }
        }
    }

    public static String bar() { return "§8§m                              "; }
}
