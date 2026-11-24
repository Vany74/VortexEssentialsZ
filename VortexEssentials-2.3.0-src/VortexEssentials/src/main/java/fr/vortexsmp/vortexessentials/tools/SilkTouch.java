package fr.vortexsmp.vortexessentials.tools;

import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Collection;

/** Module Silk Touch : les blocs sont récupérés tels quels, quel que soit l'enchantement réel de l'outil. */
public final class SilkTouch {
    private SilkTouch() {}

    public static Collection<ItemStack> drops(Block b, ItemStack tool, Player p) {
        ItemStack st = tool.clone();
        st.addUnsafeEnchantment(Enchantment.SILK_TOUCH, 1);
        st.removeEnchantment(Enchantment.FORTUNE);
        return b.getDrops(st, p);
    }
}
