package fr.vortexsmp.vortexessentials.menu;

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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Menu « Systèmes du serveur » (/vortex services, ou le bouton du menu /vortex) : un seul endroit pour ouvrir la boutique,
 * les commandes (orders), les primes, les spawners, les rituels, les boosts, le temps de jeu, et, selon les permissions,
 * les outils du staff (mode staff, éditeur de recettes, menu admin du serveur, paliers).
 */
public final class ServicesMenu implements Listener {
    private static final class Holder implements InventoryHolder {
        final Map<Integer, Consumer<Player>> actions = new HashMap<>();
        Inventory inv;
        @Override public Inventory getInventory() { return inv; }
    }

    private final VortexEssentials plugin;

    public ServicesMenu(VortexEssentials plugin) {
        this.plugin = plugin;
    }

    private void add(Holder h, int slot, Material m, String name, List<String> lore, Consumer<Player> action) {
        ItemStack s = new ItemStack(m);
        ItemMeta meta = s.getItemMeta();
        meta.displayName(Msg.c(name));
        List<Component> l = new ArrayList<>();
        for (String x : lore) l.add(Msg.c(x));
        l.add(Msg.c("§eClic §7: ouvrir"));
        meta.lore(l);
        s.setItemMeta(meta);
        h.inv.setItem(slot, s);
        h.actions.put(slot, action);
    }

    public void open(Player p) {
        Holder h = new Holder();
        h.inv = Bukkit.createInventory(h, 45, Msg.c("§d§lSystèmes du serveur"));
        add(h, 10, Material.EMERALD, "§a§lBoutique", List.of("§7Acheter et vendre des items."), x -> plugin.shopMenus().openHub(x));
        add(h, 11, Material.CHEST, "§6§lVendre", List.of("§7Menu de revente (/sell)."), x -> x.performCommand("sell"));
        add(h, 12, Material.WRITABLE_BOOK, "§6§lCommandes", List.of("§7Livrez des items demandés par les joueurs", "§7et par le serveur pour gagner de l'argent."),
                x -> plugin.orderMenus().open(x, false, 0));
        add(h, 13, Material.BOOK, "§6§lMes commandes", List.of("§7Vos commandes ouvertes, récupération des items."), x -> plugin.orderMenus().open(x, true, 0));
        if (p.hasPermission("vortexessentials.bounty")) {
            add(h, 14, Material.SKELETON_SKULL, "§c§lPrimes", List.of("§7Les têtes mises à prix.", "§8/bounty <joueur> <montant> pour en mettre une."), x -> plugin.bountyMenus().open(x));
        }
        if (p.hasPermission("ultimatespawners.shop") && plugin.getConfig().getBoolean("spawners.shop.enabled", true)) {
            add(h, 15, Material.SPAWNER, "§e§lSpawners", List.of("§7Spawners empilables : mobs réels, auto-farm."), x -> plugin.spawnerMenus().shop(x));
        }
        add(h, 16, Material.ENCHANTING_TABLE, "§5§lRituels", List.of("§7Les crafts à rituel et leurs recettes."), x -> plugin.ritualMenu().openList(x));
        add(h, 21, Material.CRAFTING_TABLE, "§b§lCrafts spéciaux", List.of("§7Pommes d'or, totem, enclume...", "§7recettes et limites de craft."), x -> plugin.ritualMenu().openCrafts(x));
        add(h, 19, Material.EXPERIENCE_BOTTLE, "§a§lBoosts", List.of("§7Les bonus de votre grade."), x -> plugin.menu().openBoosts(x));
        add(h, 20, Material.CLOCK, "§b§lTemps de jeu", List.of("§7Votre temps de jeu et le classement."), x -> x.performCommand("playtime"));

        boolean staff = plugin.staffMod().tierOf(p) != null || plugin.staffMod().isActive(p.getUniqueId());
        if (staff || p.hasPermission("vortexessentials.admin") || p.hasPermission("vortexessentials.servermenu") || p.hasPermission("vortexessentials.staffmod.admin")) {
            h.inv.setItem(27, named(Material.IRON_BARS, "§8— Staff —"));
        }
        if (staff) {
            boolean on = plugin.staffMod().isActive(p.getUniqueId());
            add(h, 28, Material.SHIELD, "§b§lMode staff", List.of("§7État : " + (on ? "§aactif" : "§cinactif"), "§7Active les commandes de votre palier."), x -> x.performCommand("staffmod"));
        }
        if (p.hasPermission("vortexessentials.admin")) {
            add(h, 29, Material.CRAFTING_TABLE, "§b§lÉditeur de recettes", List.of("§7Modifier les recettes des items Vortex."), x -> plugin.recipeEditor().openList(x, 0));
        }
        if (p.hasPermission("vortexessentials.servermenu")) {
            add(h, 30, Material.COMMAND_BLOCK, "§c§lAdmin du serveur", List.of("§7Gamerules, Nether / End, actions globales."), x -> x.performCommand("servermenu"));
        }
        if (p.hasPermission("vortexessentials.staffmod.admin")) {
            add(h, 31, Material.NETHERITE_HELMET, "§b§lPaliers du staff", List.of("§7Créer et modifier les paliers du mode staff."), x -> plugin.staffModMenu().openList(x));
        }
        if (p.hasPermission("vortexessentials.shop.admin")) {
            add(h, 33, Material.EMERALD_BLOCK, "§a§lÉditeur de boutique", List.of("§7Ajouter ou modifier l'item en main."), x -> plugin.shopEditor().open(x));
        }
        if (p.hasPermission("vortexessentials.orders.admin")) {
            add(h, 32, Material.PAPER, "§6§lCommandes du serveur", List.of("§7Liste des commandes publiées par le serveur."), x -> x.performCommand("orders server list"));
        }
        add(h, 40, Material.ARROW, "§7« Accueil", List.of(), x -> plugin.menu().openHub(x));
        p.openInventory(h.inv);
    }

    private static ItemStack named(Material m, String name) {
        ItemStack s = new ItemStack(m);
        ItemMeta meta = s.getItemMeta();
        meta.displayName(Msg.c(name));
        s.setItemMeta(meta);
        return s;
    }

    @EventHandler
    public void click(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof Holder h)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p) || e.getClickedInventory() != e.getView().getTopInventory()) return;
        Consumer<Player> action = h.actions.get(e.getRawSlot());
        if (action == null) return;
        Bukkit.getScheduler().runTask(plugin, () -> { if (p.isOnline()) action.accept(p); });
    }

    @EventHandler
    public void drag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Holder) e.setCancelled(true);
    }
}
