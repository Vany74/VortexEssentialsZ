package fr.vortexsmp.vortexessentials.ritual;

import fr.vortexsmp.vortexessentials.Msg;
import fr.vortexsmp.vortexessentials.VortexEssentials;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/** Menu des rituels (/vortex rituals) : liste des crafts à rituel et aperçu de la recette (lecture seule). */
public final class RitualMenu implements Listener {
    private static final int[] GRID = {10, 11, 12, 19, 20, 21, 28, 29, 30};

    private static final class Holder implements InventoryHolder {
        final String craft;
        final List<String> shown = new ArrayList<>();
        Inventory inv;
        Holder(String craft) { this.craft = craft; }
        @Override public Inventory getInventory() { return inv; }
    }

    private final VortexEssentials plugin;

    public RitualMenu(VortexEssentials plugin) {
        this.plugin = plugin;
    }

    private static ItemStack named(Material m, String name, List<String> lore) {
        ItemStack s = new ItemStack(m);
        ItemMeta meta = s.getItemMeta();
        meta.displayName(Msg.c(name));
        List<Component> l = new ArrayList<>();
        for (String x : lore) l.add(Msg.c(x));
        meta.lore(l);
        s.setItemMeta(meta);
        return s;
    }

    public void openList(Player p) { openNames(p, plugin.rituals().names(), "§5Rituels", "Aucun rituel pour le moment"); }

    /** Tous les crafts spéciaux du serveur (pommes d'or, totem...), avec leur recette et leur limite éventuelle. */
    public void openCrafts(Player p) { openNames(p, plugin.customRecipes().names(), "§bCrafts spéciaux", "Aucun craft spécial pour le moment"); }

    private void openNames(Player p, List<String> names, String title, String empty) {
        Holder h = new Holder(null);
        Inventory inv = Bukkit.createInventory(h, 54, Msg.c(title));
        h.inv = inv;
        if (names.isEmpty()) inv.setItem(22, named(Material.BARRIER, "§7" + empty, List.of()));
        for (int i = 0; i < names.size() && i < 45; i++) {
            String n = names.get(i);
            h.shown.add(n);
            ItemStack res = plugin.customRecipes().result(n);
            ItemStack icon = res == null ? new ItemStack(Material.ENCHANTED_BOOK) : res.clone();
            ItemMeta meta = icon.getItemMeta();
            List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
            lore.add(Component.empty());
            int secs = plugin.rituals().seconds(n);
            if (secs > 0) lore.add(Msg.c("§5Durée du rituel : §f" + secs + "s"));
            for (String l : plugin.craftLimits().lines(n)) lore.add(Msg.c(l));
            lore.add(Msg.c("§eClic §7: voir la recette"));
            if (p.hasPermission("vortexessentials.admin")) lore.add(Msg.c("§bShift + clic §7: éditer §8(admin)"));
            meta.lore(lore);
            icon.setItemMeta(meta);
            inv.setItem(i, icon);
        }
        p.openInventory(inv);
    }

    private void openRecipe(Player p, String craft) {
        Holder h = new Holder(craft);
        Inventory inv = Bukkit.createInventory(h, 45, Msg.c("§5Rituel : " + craft));
        h.inv = inv;
        List<String> grid = plugin.customRecipes().grid(craft);
        boolean shapeless = plugin.customRecipes().shapeless(craft);
        int slot = 0;
        for (int i = 0; i < grid.size() && i < 9; i++) {
            Material m = grid.get(i) == null || grid.get(i).isBlank() ? null : Material.matchMaterial(grid.get(i));
            if (m == null || m.isAir()) { if (!shapeless) slot++; continue; }
            inv.setItem(GRID[Math.min(shapeless ? slot : i, 8)], new ItemStack(m));
            if (shapeless) slot++;
        }
        ItemStack res = plugin.customRecipes().result(craft);
        if (res != null) inv.setItem(24, res);
        if (plugin.rituals().seconds(craft) > 0) {
            inv.setItem(31, named(Material.CLOCK, "§5Rituel de §f" + plugin.rituals().seconds(craft) + "s",
                    List.of("§7Craftez à la table de craft, cliquez sur le résultat :", "§7l'item tombe à la fin du rituel.")));
        }
        inv.setItem(33, named(plugin.craftLimits().limited(craft) ? Material.CLOCK : Material.LIME_DYE, "§fLimite de craft", plugin.craftLimits().lines(craft)));
        inv.setItem(40, named(Material.ARROW, "§7« Retour", List.of()));
        if (p.hasPermission("vortexessentials.admin")) {
            inv.setItem(35, named(Material.ANVIL, "§b§lÉditer ce craft", List.of("§7Recette, limites de craft, rituel...", "§eClic §7: ouvrir l'éditeur §8(admin)")));
        }
        p.openInventory(inv);
    }

    @EventHandler
    public void click(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof Holder h)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p) || e.getClickedInventory() != e.getView().getTopInventory()) return;
        if (h.craft == null) {
            int slot = e.getRawSlot();
            if (slot >= 0 && slot < h.shown.size()) {
                if (e.isShiftClick() && p.hasPermission("vortexessentials.admin")) plugin.recipeEditor().openEditCustom(p, h.shown.get(slot), null);
                else openRecipe(p, h.shown.get(slot));
            }
        } else if (e.getRawSlot() == 35 && p.hasPermission("vortexessentials.admin")) {
            plugin.recipeEditor().openEditCustom(p, h.craft, null);
        } else if (e.getRawSlot() == 40) {
            p.closeInventory();
        }
    }

    @EventHandler
    public void drag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Holder) e.setCancelled(true);
    }
}
