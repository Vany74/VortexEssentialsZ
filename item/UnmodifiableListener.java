package fr.vortexsmp.vortexessentials.item;

import fr.vortexsmp.vortexessentials.Msg;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/** Les items « non modifiables » (Skyfall, Voidrender, Umbral Scythe) ne peuvent entrer ni dans une enclume, une meule, une table d'enchantement ou une forge. */
public final class UnmodifiableListener implements Listener {

    private static boolean unmodifiable(ItemStack s) {
        if (s == null || s.getType().isAir()) return false;
        VortexItem v = ItemUtil.get(s);
        return v != null && v.isUnmodifiable();
    }

    private static boolean workstation(Inventory top) {
        InventoryType t = top.getType();
        return t == InventoryType.ANVIL || t == InventoryType.GRINDSTONE || t == InventoryType.ENCHANTING || t == InventoryType.SMITHING;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void click(InventoryClickEvent e) {
        Inventory top = e.getView().getTopInventory();
        if (!workstation(top)) return;
        ItemStack moving = null;
        if (e.getClickedInventory() == top) {
            if (unmodifiable(e.getCursor())) {
                moving = e.getCursor();
            } else if (e.getClick() == ClickType.NUMBER_KEY && e.getWhoClicked() instanceof Player p) {
                ItemStack hot = p.getInventory().getItem(e.getHotbarButton());
                if (unmodifiable(hot)) moving = hot;
            } else if (e.getClick() == ClickType.SWAP_OFFHAND && e.getWhoClicked() instanceof Player p
                    && unmodifiable(p.getInventory().getItemInOffHand())) {
                moving = p.getInventory().getItemInOffHand();
            }
        } else if (e.isShiftClick() && unmodifiable(e.getCurrentItem())) {
            moving = e.getCurrentItem();
        }
        if (moving == null) return;
        e.setCancelled(true);
        if (e.getWhoClicked() instanceof Player p) Msg.prefixed(p, "§cCet objet ne peut être ni enchanté ni modifié.");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void drag(InventoryDragEvent e) {
        Inventory top = e.getView().getTopInventory();
        if (!workstation(top) || !unmodifiable(e.getOldCursor())) return;
        for (int raw : e.getRawSlots()) {
            if (raw < top.getSize()) { e.setCancelled(true); return; }
        }
    }
}
