package fr.vortexsmp.vortexessentials.tools;

import fr.vortexsmp.vortexessentials.VortexEssentials;
import fr.vortexsmp.vortexessentials.item.ItemUtil;
import fr.vortexsmp.vortexessentials.item.VortexItem;
import fr.vortexsmp.vortexessentials.util.BreakGuard;
import fr.vortexsmp.vortexessentials.util.Blocks;
import fr.vortexsmp.vortexessentials.util.Smelting;
import fr.vortexsmp.vortexessentials.util.ToolWear;
import fr.vortexsmp.vortexessentials.util.TreeFeller;
import org.bukkit.Bukkit;
import org.bukkit.Effect;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.Collection;
import java.util.List;

/** Outils : Smelter's Pickaxe (x3), Explosive Pickaxe (x2), Lumberjack's Axe (x3). Chaque comportement est désactivable. */
public final class ToolListener implements Listener {
    private final VortexEssentials plugin;

    public ToolListener(VortexEssentials plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        if (BreakGuard.BUSY.contains(p.getUniqueId())) return;

        ItemStack tool = p.getInventory().getItemInMainHand();
        String n = tool.getType().name();
        if (!n.endsWith("_PICKAXE") && !n.endsWith("_AXE")) return;
        VortexItem v = ItemUtil.get(tool);
        if (v == null) return;

        switch (v) {
            case SMELTER_PICKAXE, SUPER_SMELTER_PICKAXE, NETHERITE_SMELTER_PICKAXE -> {
                if (ToolSettings.get(tool, ToolOption.SMELT)) smelt(e, p, tool);
            }
            case EXPLOSIVE_PICKAXE, NETHERITE_EXPLOSIVE_PICKAXE -> {
                if (ToolSettings.get(tool, ToolOption.AREA)) explosive(e, p, tool);
            }
            case LUMBERJACK_AXE, SUPER_LUMBERJACK_AXE, NETHERITE_LUMBERJACK_AXE -> {
                if (ToolSettings.get(tool, ToolOption.TREE)) {
                    TreeFeller.fell(p, e.getBlock(), tool, plugin.getConfig().getInt("tools.lumberjack-max-blocks", 256));
                }
            }
            default -> { }
        }
    }

    private void smelt(BlockBreakEvent e, Player p, ItemStack tool) {
        if (p.getGameMode() == GameMode.CREATIVE) return;
        Block b = e.getBlock();
        if (!Smelting.isOre(b.getType())) return;
        e.setDropItems(false);
        for (ItemStack d : Smelting.smeltDrops(b.getDrops(tool, p))) {
            b.getWorld().dropItemNaturally(b.getLocation().add(0.5, 0.5, 0.5), d);
        }
    }

    /** Plage d'offsets sur un axe : centrée si n est impair, sinon étendue dans le sens du regard. */
    private static int[] range(int n, double dir) {
        if (n % 2 == 1) {
            int h = (n - 1) / 2;
            return new int[]{-h, h};
        }
        return dir >= 0 ? new int[]{0, n - 1} : new int[]{-(n - 1), 0};
    }

    private void explosive(BlockBreakEvent e, Player p, ItemStack tool) {
        VortexItem vi = ItemUtil.get(tool);
        int n = vi == VortexItem.NETHERITE_EXPLOSIVE_PICKAXE
                ? plugin.getConfig().getInt("tools.netherite-explosive-pickaxe-size", 3)
                : plugin.getConfig().getInt("tools.explosive-pickaxe-size", 2);
        n = Math.max(1, Math.min(7, n));
        if (n == 1) return;

        Vector dir = p.getLocation().getDirection();
        int[] o = AreaPlane.offsets(AreaPlane.axis(p, e.getBlock()), n, dir); // zone carrée face au bloc
        boolean creative = p.getGameMode() == GameMode.CREATIVE;
        Block center = e.getBlock();
        int broken = 0;
        boolean silk = ToolSettings.get(tool, ToolOption.SILK_TOUCH);
        if (silk && !creative) { // bloc central : récupéré tel quel
            e.setDropItems(false);
            for (ItemStack d : SilkTouch.drops(center, tool, p)) center.getWorld().dropItemNaturally(center.getLocation().add(0.5, 0.5, 0.5), d);
        }

        BreakGuard.BUSY.add(p.getUniqueId());
        try {
            for (int x = o[0]; x <= o[1]; x++) {
                for (int y = o[2]; y <= o[3]; y++) {
                    for (int z = o[4]; z <= o[5]; z++) {
                        if (x == 0 && y == 0 && z == 0) continue;
                        Block b = center.getRelative(x, y, z);
                        if (!Blocks.breakable(b, tool, false)) continue;

                        BlockBreakEvent ev = new BlockBreakEvent(b, p);
                        Bukkit.getPluginManager().callEvent(ev);
                        if (ev.isCancelled()) continue;

                        Collection<ItemStack> drops = (creative || !ev.isDropItems()) ? List.of() : (silk ? SilkTouch.drops(b, tool, p) : b.getDrops(tool, p));
                        b.getWorld().playEffect(b.getLocation(), Effect.STEP_SOUND, b.getBlockData());
                        b.setType(Material.AIR);
                        for (ItemStack d : drops) b.getWorld().dropItemNaturally(b.getLocation().add(0.5, 0.5, 0.5), d);
                        broken++;
                    }
                }
            }
        } finally {
            BreakGuard.BUSY.remove(p.getUniqueId());
        }

        if (broken > 0) {
            var loc = center.getLocation().add(0.5, 0.5, 0.5);
            center.getWorld().spawnParticle(Particle.EXPLOSION, loc, 1);
            center.getWorld().playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 0.5f, 1.4f);
            ToolWear.apply(p, p.getInventory().getItemInMainHand(), Math.max(1, broken / 3));
        }
    }
}
