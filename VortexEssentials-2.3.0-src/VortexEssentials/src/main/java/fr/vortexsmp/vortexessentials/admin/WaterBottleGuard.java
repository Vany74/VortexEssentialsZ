package fr.vortexsmp.vortexessentials.admin;

import org.bukkit.Keyed;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;

/** Les crafts « honey bottle » et « ominous bottle » n'acceptent que des fioles d'EAU (pas n'importe quelle potion). */
public final class WaterBottleGuard implements Listener {
    @EventHandler(priority = EventPriority.HIGH)
    public void prepare(PrepareItemCraftEvent e) {
        if (!(e.getRecipe() instanceof Keyed k)) return;
        String key = k.getKey().getKey();
        if (!key.equals("custom_honey_bottle") && !key.equals("custom_ominous_bottle")) return;
        for (ItemStack s : e.getInventory().getMatrix()) {
            if (s == null || s.getType() != Material.POTION) continue;
            if (!(s.getItemMeta() instanceof PotionMeta pm) || pm.getBasePotionType() != PotionType.WATER) {
                e.getInventory().setResult(null);
                return;
            }
        }
    }
}
