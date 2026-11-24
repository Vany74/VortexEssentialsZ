package fr.vortexsmp.vortexessentials.tools;

import fr.vortexsmp.vortexessentials.VortexEssentials;
import fr.vortexsmp.vortexessentials.item.ItemUtil;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/** Sneak + clic droit avec un outil réglable : ouvre son menu de réglages. */
public final class ToolSettingsListener implements Listener {
    private final VortexEssentials plugin;

    public ToolSettingsListener(VortexEssentials plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        Action a = e.getAction();
        if (a != Action.RIGHT_CLICK_AIR && a != Action.RIGHT_CLICK_BLOCK) return;
        Player p = e.getPlayer();
        if (!p.isSneaking()) return;

        ItemStack hand = p.getInventory().getItemInMainHand();
        String n = hand.getType().name();
        if (!n.endsWith("_PICKAXE") && !n.endsWith("_AXE") && !n.endsWith("_SHOVEL")) return;
        if (ToolSettings.available(ItemUtil.get(hand)).isEmpty()) return;

        e.setCancelled(true);
        plugin.menu().openToolSettings(p);
    }
}
