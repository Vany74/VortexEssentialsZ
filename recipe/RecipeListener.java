package fr.vortexsmp.vortexessentials.recipe;

import fr.vortexsmp.vortexessentials.Msg;
import fr.vortexsmp.vortexessentials.VortexEssentials;
import fr.vortexsmp.vortexessentials.item.ItemUtil;
import fr.vortexsmp.vortexessentials.item.VortexItem;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.inventory.PrepareSmithingEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.SmithingInventory;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Map;

/**
 * Garde-fous des recettes :
 *  - un item Vortex ne peut jamais servir d'ingrédient vanilla (sinon il redeviendrait un objet vanilla) ;
 *  - require-custom : vérifie que l'ingrédient exigé est bien l'item Vortex ;
 *  - transfer-from : copie enchantements et durabilité ;
 *  - forge : améliorations netherite des items Vortex.
 */
public final class RecipeListener implements Listener {
    private final VortexEssentials plugin;

    public RecipeListener(VortexEssentials plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void prepare(PrepareItemCraftEvent e) {
        if (e.getRecipe() == null) return;
        CraftingInventory inv = e.getInventory();
        ItemStack[] matrix = inv.getMatrix();

        VortexItem res = ItemUtil.get(inv.getResult());
        RecipeManager.Def def = res == null ? null : plugin.recipes().def(res);

        // 1. Un item Vortex n'est accepté que là où la recette l'exige explicitement
        for (ItemStack s : matrix) {
            VortexItem ci = ItemUtil.get(s);
            if (ci == null) continue;
            if (def != null && def.requireCustom().get(s.getType()) == ci) continue;
            inv.setResult(null);
            return;
        }
        if (def == null) return;

        // 2. Ingrédient Vortex exigé
        for (Map.Entry<org.bukkit.Material, VortexItem> req : def.requireCustom().entrySet()) {
            boolean found = false;
            for (ItemStack s : matrix) {
                if (s != null && s.getType() == req.getKey()) {
                    if (ItemUtil.get(s) != req.getValue()) {
                        inv.setResult(null);
                        return;
                    }
                    found = true;
                }
            }
            if (!found) {
                inv.setResult(null);
                return;
            }
        }

        // 3. Transfert d'enchantements / durabilité
        if (def.transferFrom() != null) {
            for (ItemStack s : matrix) {
                if (s != null && s.getType() == def.transferFrom()) {
                    if (inv.getResult() == null) return;
                    ItemStack out = inv.getResult().clone();
                    transfer(s, out);
                    inv.setResult(out);
                    break;
                }
            }
        }
    }

    /** Pas de shift-clic / touche numérique quand le résultat dépend d'un ingrédient. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void craft(CraftItemEvent e) {
        VortexItem res = ItemUtil.get(e.getCurrentItem());
        if (res == null) return;
        RecipeManager.Def def = plugin.recipes().def(res);
        if (def == null || def.transferFrom() == null) return;

        ClickType ct = e.getClick();
        if (e.isShiftClick() || ct == ClickType.NUMBER_KEY || ct == ClickType.SWAP_OFFHAND) {
            e.setCancelled(true);
            if (e.getWhoClicked() instanceof Player p) {
                Msg.prefixed(p, "§cCliquez normalement sur le résultat pour conserver les enchantements.");
            }
        }
    }

    // ═════════════ Table de forge ═════════════

    @EventHandler(priority = EventPriority.HIGH)
    public void smithing(PrepareSmithingEvent e) {
        SmithingInventory inv = e.getInventory();
        ItemStack base = inv.getInputEquipment();
        VortexItem bi = ItemUtil.get(base);
        if (bi == null) return;

        ItemStack res = e.getResult();
        if (res == null || res.getType().isAir()) return;

        VortexItem target = plugin.recipes().upgradeOf(bi);
        if (target == null || res.getType() != target.base()) {
            e.setResult(null); // jamais de transformation silencieuse en objet vanilla
            return;
        }
        ItemStack out = ItemUtil.create(target);
        transfer(base, out);
        e.setResult(out);
    }

    // ═════════════ Transfert ═════════════

    /** Copie les enchantements et la durabilité (proportionnelle) de {@code from} vers {@code to}. */
    public static void transfer(ItemStack from, ItemStack to) {
        ItemMeta fm = from.getItemMeta();
        ItemMeta tm = to.getItemMeta();
        if (fm == null || tm == null) return;

        for (Map.Entry<Enchantment, Integer> en : fm.getEnchants().entrySet()) {
            tm.addEnchant(en.getKey(), en.getValue(), true);
        }
        if (fm instanceof Damageable fd && tm instanceof Damageable td) {
            int fmax = from.getType().getMaxDurability();
            int tmax = to.getType().getMaxDurability();
            if (fmax > 0 && tmax > 0) td.setDamage((int) Math.round(fd.getDamage() * (double) tmax / fmax));
        }
        to.setItemMeta(tm);
    }
}
