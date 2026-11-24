package fr.vortexsmp.vortexessentials.bounty;

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
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;

/** Menu des primes : les têtes mises à prix, de la plus élevée à la plus basse. */
public final class BountyMenus implements Listener {
    private static final class Holder implements InventoryHolder {
        Inventory inv;
        @Override public Inventory getInventory() { return inv; }
    }

    private final VortexEssentials plugin;

    public BountyMenus(VortexEssentials plugin) {
        this.plugin = plugin;
    }

    public void open(Player p) {
        Holder h = new Holder();
        Inventory inv = Bukkit.createInventory(h, 54, Msg.c("§cPrimes"));
        h.inv = inv;
        List<BountyManager.Bounty> top = plugin.bounties().top();
        for (int i = 0; i < 45 && i < top.size(); i++) {
            BountyManager.Bounty b = top.get(i);
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            meta.setOwningPlayer(Bukkit.getOfflinePlayer(b.target));
            meta.displayName(Msg.c("§c" + b.name));
            List<Component> lore = new ArrayList<>();
            lore.add(Msg.c("§7Prime : §6" + plugin.money().format(b.total)));
            lore.add(Msg.c("§7Contributeurs : §f" + b.contributors.size()));
            lore.add(Component.empty());
            lore.add(Msg.c("§7Tuez-le pour encaisser la prime."));
            meta.lore(lore);
            head.setItemMeta(meta);
            inv.setItem(i, head);
        }
        if (top.isEmpty()) inv.setItem(22, named(Material.BARRIER, "§7Aucune prime pour le moment"));
        inv.setItem(49, named(Material.PAPER, "§eMettre une prime", "§f/bounty <joueur> <montant>", "§7Les primes se cumulent."));
        p.openInventory(inv);
    }

    private static ItemStack named(Material m, String name, String... lore) {
        ItemStack s = new ItemStack(m);
        ItemMeta meta = s.getItemMeta();
        meta.displayName(Msg.c(name));
        List<Component> l = new ArrayList<>();
        for (String x : lore) l.add(Msg.c(x));
        meta.lore(l);
        s.setItemMeta(meta);
        return s;
    }

    @EventHandler
    public void click(InventoryClickEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Holder) e.setCancelled(true);
    }

    @EventHandler
    public void drag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Holder) e.setCancelled(true);
    }
}
