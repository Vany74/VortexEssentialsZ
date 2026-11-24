package fr.vortexsmp.vortexessentials.orders;

import fr.vortexsmp.vortexessentials.Msg;
import fr.vortexsmp.vortexessentials.VortexEssentials;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Menus des commandes : toutes les commandes ouvertes, et « mes commandes » (récupérer / annuler). */
public final class OrderMenus implements Listener {
    private static final class Holder implements InventoryHolder {
        final boolean mine;
        int page;
        final List<OrderManager.Order> shown = new ArrayList<>();
        Inventory inv;
        Holder(boolean mine, int page) { this.mine = mine; this.page = page; }
        @Override public Inventory getInventory() { return inv; }
    }

    private final VortexEssentials plugin;

    public OrderMenus(VortexEssentials plugin) {
        this.plugin = plugin;
    }

    private static String pretty(Material m) {
        String s = m.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private ItemStack button(Material m, String name, String... lore) {
        ItemStack s = new ItemStack(m);
        ItemMeta meta = s.getItemMeta();
        meta.displayName(Msg.c(name));
        List<Component> l = new ArrayList<>();
        for (String x : lore) l.add(Msg.c(x));
        meta.lore(l);
        s.setItemMeta(meta);
        return s;
    }

    public void open(Player p, boolean mine, int page) {
        Holder h = new Holder(mine, Math.max(0, page));
        List<OrderManager.Order> all = mine ? plugin.orders().mine(p.getUniqueId()) : plugin.orders().open();
        int from = h.page * 45;
        if (from >= all.size() && h.page > 0) { h.page = 0; from = 0; }
        Inventory inv = Bukkit.createInventory(h, 54, Msg.c(mine ? "§6Mes commandes" : "§6Commandes"));
        h.inv = inv;
        for (int i = 0; i < 45 && from + i < all.size(); i++) {
            OrderManager.Order o = all.get(from + i);
            h.shown.add(o);
            ItemStack icon = o.template();
            ItemMeta meta = icon.getItemMeta();
            if (!meta.hasDisplayName()) meta.displayName(Msg.c("§f" + pretty(o.mat)));
            List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
            if (!lore.isEmpty()) lore.add(Component.empty());
            lore.add(Msg.c("§7Demandé par : §f" + o.ownerName + (o.server ? " §8(serveur)" : "")));
            lore.add(Msg.c("§7Reste : §f" + o.remaining + "§7/§f" + o.total));
            lore.add(Msg.c("§7Prix unitaire : §a" + plugin.money().format(o.price)));
            lore.add(Msg.c("§7Valeur restante : §a" + plugin.money().format(o.price * o.remaining)));
            lore.add(Component.empty());
            if (mine) {
                lore.add(Msg.c("§7À récupérer : §f" + o.collected));
                lore.add(Msg.c("§eClic gauche §7: récupérer les items"));
                lore.add(Msg.c("§eClic droit §7: annuler (remboursement)"));
            } else {
                lore.add(Msg.c("§eClic gauche §7: livrer tout ce que vous avez"));
                lore.add(Msg.c("§eClic droit §7: livrer 1"));
            }
            meta.lore(lore);
            icon.setItemMeta(meta);
            inv.setItem(i, icon);
        }
        inv.setItem(45, h.page > 0 ? button(Material.ARROW, "§7« Page précédente") : null);
        inv.setItem(47, button(Material.PAPER, "§eCréer une commande", "§7Tenez l'item en main puis :", "§f/orders create <quantité> <prix>"));
        inv.setItem(49, mine ? button(Material.CHEST, "§6Toutes les commandes") : button(Material.WRITABLE_BOOK, "§6Mes commandes"));
        inv.setItem(53, from + 45 < all.size() ? button(Material.ARROW, "§7Page suivante »") : null);
        p.openInventory(inv);
    }

    @EventHandler
    public void click(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof Holder h)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p) || e.getClickedInventory() != e.getView().getTopInventory()) return;
        int slot = e.getRawSlot();
        if (slot == 45 && h.page > 0) { open(p, h.mine, h.page - 1); return; }
        if (slot == 53 && e.getCurrentItem() != null) { open(p, h.mine, h.page + 1); return; }
        if (slot == 49) { open(p, !h.mine, 0); return; }
        if (slot < 0 || slot >= h.shown.size()) return;
        OrderManager.Order o = h.shown.get(slot);
        OrderManager orders = plugin.orders();

        if (h.mine) {
            if (e.getClick() == ClickType.RIGHT || e.getClick() == ClickType.SHIFT_RIGHT) {
                String err = orders.cancel(p, o, false);
                Msg.prefixed(p, err != null ? "§c" + err : "§7Commande annulée : argent restant remboursé, items remis.");
            } else {
                int n = orders.collect(p, o);
                Msg.prefixed(p, n > 0 ? "§aVous récupérez §f" + n + " §aitem(s)." : "§7Rien à récupérer pour le moment.");
            }
        } else {
            if (!o.server && p.getUniqueId().equals(o.owner)) { Msg.prefixed(p, "§cVous ne pouvez pas livrer votre propre commande."); return; }
            int max = e.getClick() == ClickType.RIGHT ? 1 : Integer.MAX_VALUE;
            int n = orders.deliver(p, o, max);
            Msg.prefixed(p, n > 0 ? "§aLivré : §f" + n + " §aitem(s) pour §f" + plugin.money().format(n * o.price)
                    : "§cVous n'avez pas cet item (ou la commande ne peut plus payer).");
            Player owner = o.owner == null ? null : Bukkit.getPlayer(o.owner);
            if (n > 0 && owner != null) owner.sendActionBar(Msg.c("§6" + p.getName() + " a livré " + n + " " + pretty(o.mat) + " à votre commande."));
        }
        open(p, h.mine, h.page);
    }

    @EventHandler
    public void drag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Holder) e.setCancelled(true);
    }

    @EventHandler
    public void join(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            int n = plugin.orders().pendingFor(p.getUniqueId());
            if (p.isOnline() && n > 0) Msg.prefixed(p, "§6Des joueurs ont livré §f" + n + " §6item(s) à vos commandes : §f/orders mine");
        }, 60L);
    }
}
