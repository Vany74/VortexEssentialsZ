package fr.vortexsmp.vortexessentials.util;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.Set;

/** Abattage d'un arbre entier (rondins connectés + feuillage présent = arbre naturel). */
public final class TreeFeller {
    private TreeFeller() {}

    /** Abat l'arbre dont {@code origin} est un rondin déjà cassé. Renvoie le nombre de rondins supplémentaires. */
    public static int fell(Player p, Block origin, ItemStack tool, int max) {
        if (!Tag.LOGS.isTagged(origin.getType())) return 0;
        max = Math.max(2, max);

        Set<Block> logs = new LinkedHashSet<>();
        Deque<Block> queue = new ArrayDeque<>();
        logs.add(origin);
        queue.add(origin);
        boolean leaves = false;

        while (!queue.isEmpty()) {
            Block cur = queue.poll();
            for (int x = -1; x <= 1; x++) {
                for (int y = -1; y <= 1; y++) {
                    for (int z = -1; z <= 1; z++) {
                        if (x == 0 && y == 0 && z == 0) continue;
                        Block nb = cur.getRelative(x, y, z);
                        Material m = nb.getType();
                        if (Tag.LOGS.isTagged(m)) {
                            if (logs.size() < max && logs.add(nb)) queue.add(nb);
                        } else if (Tag.LEAVES.isTagged(m)) {
                            leaves = true;
                        }
                    }
                }
            }
        }
        if (!leaves || logs.size() <= 1) return 0; // sans feuillage : probablement une construction

        int broken = 0;
        BreakGuard.BUSY.add(p.getUniqueId());
        try {
            for (Block b : logs) {
                if (b.equals(origin)) continue;
                BlockBreakEvent ev = new BlockBreakEvent(b, p);
                Bukkit.getPluginManager().callEvent(ev);
                if (ev.isCancelled()) continue;
                if (p.getGameMode() == GameMode.CREATIVE) b.setType(Material.AIR);
                else b.breakNaturally(tool);
                broken++;
            }
        } finally {
            BreakGuard.BUSY.remove(p.getUniqueId());
        }
        if (broken > 0) ToolWear.apply(p, p.getInventory().getItemInMainHand(), Math.max(1, broken / 4));
        return broken;
    }
}
