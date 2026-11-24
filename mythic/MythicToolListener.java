package fr.vortexsmp.vortexessentials.mythic;

import fr.vortexsmp.vortexessentials.VortexEssentials;
import fr.vortexsmp.vortexessentials.item.ItemUtil;
import fr.vortexsmp.vortexessentials.item.VortexItem;
import fr.vortexsmp.vortexessentials.tools.AreaPlane;
import fr.vortexsmp.vortexessentials.tools.SilkTouch;
import fr.vortexsmp.vortexessentials.tools.ToolOption;
import fr.vortexsmp.vortexessentials.tools.ToolSettings;
import fr.vortexsmp.vortexessentials.util.Blocks;
import fr.vortexsmp.vortexessentials.util.BreakGuard;
import fr.vortexsmp.vortexessentials.util.Smelting;
import fr.vortexsmp.vortexessentials.util.TreeFeller;
import org.bukkit.Bukkit;
import org.bukkit.Effect;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Collection;
import java.util.List;

/**
 * Outils mythiques : Fractured (pioche), Terraloop (hache), Jugas (pelle).
 *  - cassent TOUS les blocs instantanément (désactivable : réglage « vitesse vanilla ») ;
 *  - zone cubique (mythic-tools.size, 3 = 3×3×3) ;
 *  - autosmelt (Fractured : minerais ; Jugas : sable, argile, etc.) ;
 *  - Terraloop : abat l'arbre entier.
 * Chaque comportement se règle dans le menu de l'outil (sneak + clic droit).
 */
public final class MythicToolListener implements Listener {
    private final VortexEssentials plugin;

    public MythicToolListener(VortexEssentials plugin) {
        this.plugin = plugin;
    }

    private static boolean isMythicTool(VortexItem v) {
        return v == VortexItem.FRACTURED || v == VortexItem.TERRALOOP || v == VortexItem.JUGAS;
    }

    private int size() {
        int s = Math.max(1, Math.min(9, plugin.getConfig().getInt("mythic-tools.size", 3)));
        return s % 2 == 0 ? s + 1 : s; // toujours impair
    }

    private VortexItem mythicIn(ItemStack tool) {
        if (!MythicEffectsTask.relevant(tool.getType())) return null;
        VortexItem v = ItemUtil.get(tool);
        return isMythicTool(v) ? v : null;
    }

    // ═════════════ Minage instantané ═════════════

    @EventHandler(ignoreCancelled = true)
    public void damage(BlockDamageEvent e) {
        if (!plugin.getConfig().getBoolean("mythic-tools.instant-break", true)) return;
        ItemStack tool = e.getPlayer().getInventory().getItemInMainHand();
        if (mythicIn(tool) == null) return;
        if (ToolSettings.get(tool, ToolOption.VANILLA_SPEED)) return;
        if (e.getBlock().getType().getHardness() < 0) return; // bedrock, barrières...
        e.setInstaBreak(true);
    }

    // ═════════════ Casse ═════════════

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        if (BreakGuard.BUSY.contains(p.getUniqueId())) return;

        ItemStack tool = p.getInventory().getItemInMainHand();
        VortexItem v = mythicIn(tool);
        if (v == null) return;

        boolean creative = p.getGameMode() == GameMode.CREATIVE;
        Block center = e.getBlock();

        // Terraloop : arbre entier (à la place de la zone)
        if (v == VortexItem.TERRALOOP && ToolSettings.get(tool, ToolOption.TREE)
                && Tag.LOGS.isTagged(center.getType())) {
            int max = plugin.getConfig().getInt("tools.lumberjack-max-blocks", 256);
            if (TreeFeller.fell(p, center, tool, max) > 0) return;
        }

        boolean silk = ToolSettings.get(tool, ToolOption.SILK_TOUCH);
        boolean smelt = v != VortexItem.TERRALOOP && !silk && ToolSettings.get(tool, ToolOption.SMELT);

        // Silk Touch : le bloc central est récupéré tel quel
        if (silk && !creative) {
            e.setDropItems(false);
            drop(center, SilkTouch.drops(center, tool, p), false);
        }

        // Bloc central : cuisson
        if (smelt && !creative && smeltAllowed(v, center.getType())) {
            e.setDropItems(false);
            drop(center, center.getDrops(tool, p), true);
        }

        if (!ToolSettings.get(tool, ToolOption.AREA)) return;
        int n = size();
        if (n <= 1) return;
        int[] o = AreaPlane.offsets(AreaPlane.axis(p, center), n, p.getLocation().getDirection()); // zone carrée face au bloc

        BreakGuard.BUSY.add(p.getUniqueId());
        try {
            for (int x = o[0]; x <= o[1]; x++) {
                for (int y = o[2]; y <= o[3]; y++) {
                    for (int z = o[4]; z <= o[5]; z++) {
                        if (x == 0 && y == 0 && z == 0) continue;
                        Block b = center.getRelative(x, y, z);
                        if (!Blocks.breakable(b, tool, v == VortexItem.TERRALOOP)) continue;

                        // Respecte WorldGuard & co : on simule une vraie casse par le joueur
                        BlockBreakEvent ev = new BlockBreakEvent(b, p);
                        Bukkit.getPluginManager().callEvent(ev);
                        if (ev.isCancelled()) continue;

                        Collection<ItemStack> drops = (creative || !ev.isDropItems()) ? List.of() : (silk ? SilkTouch.drops(b, tool, p) : b.getDrops(tool, p));
                        boolean doSmelt = smelt && smeltAllowed(v, b.getType());
                        b.getWorld().playEffect(b.getLocation(), Effect.STEP_SOUND, b.getBlockData());
                        b.setType(Material.AIR);
                        drop(b, drops, doSmelt);
                    }
                }
            }
        } finally {
            BreakGuard.BUSY.remove(p.getUniqueId());
        }
    }

    /** Fractured cuit les minerais ; Jugas cuit tout ce qui a une recette de four (sable, argile...). */
    private static boolean smeltAllowed(VortexItem v, Material block) {
        return v == VortexItem.JUGAS || Smelting.isOre(block);
    }

    private void drop(Block b, Collection<ItemStack> drops, boolean smelt) {
        Collection<ItemStack> out = smelt ? Smelting.smeltDrops(drops) : drops;
        for (ItemStack d : out) b.getWorld().dropItemNaturally(b.getLocation().add(0.5, 0.5, 0.5), d);
    }
}
