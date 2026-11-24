package fr.vortexsmp.vortexessentials.shop;

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

/**
 * Éditeur de boutique en jeu (/shop editor) : tenez l'item en main, réglez prix d'achat, prix de vente et catégorie avec des
 * boutons, puis ajoutez-le ou mettez-le à jour. Fonctionne aussi pour retirer un item de la boutique.
 */
public final class ShopEditor implements Listener {
    private static final class Holder implements InventoryHolder {
        ItemStack item;
        String key;
        double buy = 10, sell = 3;
        int catIdx;
        final List<String> catIds = new ArrayList<>();
        final List<String> catNames = new ArrayList<>();
        Inventory inv;
        @Override public Inventory getInventory() { return inv; }
    }

    private final VortexEssentials plugin;

    public ShopEditor(VortexEssentials plugin) {
        this.plugin = plugin;
    }

    private static ItemStack button(Material m, String name, String... lore) {
        ItemStack s = new ItemStack(m);
        ItemMeta meta = s.getItemMeta();
        meta.displayName(Msg.c(name));
        List<Component> l = new ArrayList<>();
        for (String x : lore) l.add(Msg.c(x));
        meta.lore(l);
        s.setItemMeta(meta);
        return s;
    }

    private static String price(double v) { return v < 0 ? "§cnon" : "§a" + (v == Math.floor(v) ? String.valueOf((long) v) : String.valueOf(v)); }

    public void open(Player p) {
        if (!p.hasPermission("vortexessentials.shop.admin")) { Msg.send(p, "§cPermission refusée."); return; }
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (hand.getType().isAir()) { Msg.send(p, "§cTenez en main l'item à ajouter, modifier ou retirer."); return; }
        ShopManager sh = plugin.shop();
        Holder h = new Holder();
        h.item = hand.clone();
        h.item.setAmount(1);
        h.key = sh.keyOf(hand);
        for (ShopManager.Category c : sh.categories()) { h.catIds.add(c.id); h.catNames.add(c.name); }
        if (h.catIds.isEmpty()) { Msg.send(p, "§cAucune catégorie dans la boutique."); return; }
        String existing = sh.categoryOf(h.key);
        String cat = existing != null ? existing : sh.autoCategory(hand.getType());
        h.catIdx = Math.max(0, h.catIds.indexOf(cat));
        if (existing != null) {
            ShopManager.Entry e = sh.entry(existing, h.key);
            h.buy = e.buy;
            h.sell = e.sell;
        }
        h.inv = Bukkit.createInventory(h, 54, Msg.c("§aÉditeur de boutique"));
        draw(h, existing != null);
        p.openInventory(h.inv);
    }

    private void draw(Holder h, boolean existing) {
        Inventory inv = h.inv;
        inv.clear();
        ItemStack shown = h.item.clone();
        ItemMeta sm = shown.getItemMeta();
        List<Component> lore = sm.lore() == null ? new ArrayList<>() : new ArrayList<>(sm.lore());
        lore.add(Component.empty());
        lore.add(Msg.c(existing ? "§eDéjà dans la boutique" : "§7Pas encore dans la boutique"));
        sm.lore(lore);
        shown.setItemMeta(sm);
        inv.setItem(4, shown);

        int[] steps = {-100, -10, -1, 1, 10, 100};
        int[] cols = {0, 1, 2, 4, 5, 6};
        for (int i = 0; i < 6; i++) {
            String name = (steps[i] > 0 ? "§a+" : "§c") + steps[i];
            inv.setItem(10 + cols[i], button(steps[i] > 0 ? Material.LIME_STAINED_GLASS_PANE : Material.RED_STAINED_GLASS_PANE, name, "§7Shift : ×10"));
            inv.setItem(19 + cols[i], button(steps[i] > 0 ? Material.LIME_STAINED_GLASS_PANE : Material.RED_STAINED_GLASS_PANE, name, "§7Shift : ×10"));
        }
        inv.setItem(13, button(Material.EMERALD, "§aPrix d'achat : " + price(h.buy), "§7Ce que paie le joueur.", "§eClic §7: achat possible / impossible"));
        inv.setItem(22, button(Material.GOLD_INGOT, "§6Prix de vente : " + price(h.sell), "§7Ce que reçoit le joueur.", "§eClic §7: vente possible / impossible"));
        inv.setItem(30, button(Material.COMPARATOR, "§7Vente = 30 % de l'achat", "§eClic §7: calculer automatiquement"));
        inv.setItem(31, button(Material.BOOKSHELF, "§bCatégorie : §f" + h.catNames.get(h.catIdx), "§eClic gauche §7: suivante  §eClic droit §7: précédente"));
        inv.setItem(37, button(Material.LIME_DYE, existing ? "§aMettre à jour" : "§aAjouter à la boutique"));
        inv.setItem(43, button(Material.RED_DYE, "§cRetirer de la boutique", "§cShift + clic §7pour confirmer."));
        inv.setItem(49, button(Material.BARRIER, "§7Fermer"));
    }

    @EventHandler
    public void click(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof Holder h)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p) || e.getClickedInventory() != e.getView().getTopInventory()) return;
        if (!p.hasPermission("vortexessentials.shop.admin")) return;
        int slot = e.getRawSlot();
        ShopManager sh = plugin.shop();
        boolean existing = sh.categoryOf(h.key) != null;
        int mult = e.isShiftClick() ? 10 : 1;
        int[] steps = {-100, -10, -1, 1, 10, 100};
        int[] cols = {0, 1, 2, 4, 5, 6};
        for (int i = 0; i < 6; i++) {
            if (slot == 10 + cols[i]) { h.buy = Math.max(0, (h.buy < 0 ? 0 : h.buy) + steps[i] * mult); draw(h, existing); return; }
            if (slot == 19 + cols[i]) { h.sell = Math.max(0, (h.sell < 0 ? 0 : h.sell) + steps[i] * mult); draw(h, existing); return; }
        }
        switch (slot) {
            case 13 -> h.buy = h.buy < 0 ? 10 : -1;
            case 22 -> h.sell = h.sell < 0 ? Math.max(1, Math.round(Math.max(0, h.buy) * 0.3 * 100.0) / 100.0) : -1;
            case 30 -> h.sell = h.buy <= 0 ? -1 : Math.round(h.buy * 0.3 * 100.0) / 100.0;
            case 31 -> h.catIdx = (h.catIdx + (e.getClick().isRightClick() ? h.catIds.size() - 1 : 1)) % h.catIds.size();
            case 37 -> { save(p, h); return; }
            case 43 -> {
                if (!e.isShiftClick()) { Msg.prefixed(p, "§cShift + clic pour confirmer le retrait."); return; }
                String cat = sh.categoryOf(h.key);
                Msg.prefixed(p, cat != null && sh.removeEntry(cat, h.key) ? "§7Item retiré de la boutique." : "§cCet item n'est pas dans la boutique.");
                sh.save();
                p.closeInventory();
                return;
            }
            case 49 -> { p.closeInventory(); return; }
            default -> { return; }
        }
        draw(h, existing);
    }

    private void save(Player p, Holder h) {
        ShopManager sh = plugin.shop();
        double buy = h.buy <= 0 ? -1 : h.buy, sell = h.sell <= 0 ? -1 : h.sell;
        if (buy < 0 && sell < 0) { Msg.prefixed(p, "§cDéfinissez au moins un prix (achat ou vente)."); return; }
        String chosen = h.catIds.get(h.catIdx);
        String existing = sh.categoryOf(h.key);
        String err = null;
        if (existing != null && existing.equals(chosen)) {
            ShopManager.Entry en = sh.entry(existing, h.key);
            en.buy = buy;
            en.sell = sell;
        } else {
            if (existing != null) sh.removeEntry(existing, h.key);
            err = sh.addEntry(chosen, h.key, buy, sell);
        }
        sh.save();
        Msg.prefixed(p, err != null ? "§c" + err : "§aEnregistré : §f" + sh.displayName(h.key) + " §7(achat " + price(buy) + "§7 / vente " + price(sell) + "§7) dans §f" + h.catNames.get(h.catIdx));
        p.closeInventory();
    }

    @EventHandler
    public void drag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Holder) e.setCancelled(true);
    }
}
