package fr.vortexsmp.vortexessentials.util;

import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;
import org.bukkit.inventory.ItemStack;

public final class Blocks {
    private Blocks() {}

    /** Bloc cassable "en zone" : pas d'air/liquide/incassable/tile entity, et outil adapté. */
    public static boolean breakable(Block b, ItemStack tool, boolean allowLeaves) {
        Material m = b.getType();
        if (m.isAir() || b.isLiquid() || !m.isBlock() || m.getHardness() < 0) return false;
        if (b.getState(false) instanceof TileState) return false; // coffres, spawners, panneaux...
        return b.isPreferredTool(tool) || (allowLeaves && Tag.LEAVES.isTagged(m));
    }
}
