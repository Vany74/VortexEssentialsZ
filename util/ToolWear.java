package fr.vortexsmp.vortexessentials.util;

import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

public final class ToolWear {
    private ToolWear() {}

    /** Use l'outil en main de {@code amount} points (ignore les outils incassables). */
    public static void apply(Player p, ItemStack tool, int amount) {
        if (amount <= 0) return;
        ItemMeta m = tool.getItemMeta();
        if (!(m instanceof Damageable dm) || m.isUnbreakable()) return;
        int max = tool.getType().getMaxDurability();
        int dmg = dm.getDamage() + amount;
        if (max > 0 && dmg >= max) {
            p.getInventory().setItemInMainHand(null);
            p.playSound(p.getLocation(), Sound.ENTITY_ITEM_BREAK, 1f, 1f);
            return;
        }
        dm.setDamage(dmg);
        tool.setItemMeta(m);
        p.getInventory().setItemInMainHand(tool);
    }
}
