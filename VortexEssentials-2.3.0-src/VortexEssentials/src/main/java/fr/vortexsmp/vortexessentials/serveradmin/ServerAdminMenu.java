package fr.vortexsmp.vortexessentials.serveradmin;

import fr.vortexsmp.vortexessentials.Msg;
import fr.vortexsmp.vortexessentials.VortexEssentials;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;

import java.util.ArrayList;
import java.util.List;

/**
 * /servermenu (alias /smenu) : gamerules du monde (booléens et nombres), activation du Nether / de l'End et actions
 * globales (soigner, nourrir, give à tous, rassembler, jour/nuit, météo, expulser).
 */
public final class ServerAdminMenu implements Listener, CommandExecutor {
    private enum Kind { HUB, RULES, DIMS, ACTIONS }

    private static final class Holder implements InventoryHolder {
        final Kind kind;
        final int page;
        final List<String> rules = new ArrayList<>();
        Inventory inv;
        Holder(Kind kind, int page) { this.kind = kind; this.page = page; }
        @Override public Inventory getInventory() { return inv; }
    }

    private final VortexEssentials plugin;

    public ServerAdminMenu(VortexEssentials plugin) {
        this.plugin = plugin;
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

    @Override
    public boolean onCommand(CommandSender s, Command c, String label, String[] a) {
        if (!s.hasPermission("vortexessentials.servermenu")) { Msg.send(s, "§cPermission refusée."); return true; }
        if (!(s instanceof Player p)) { Msg.send(s, "§cCommande réservée aux joueurs."); return true; }
        hub(p);
        return true;
    }

    public void hub(Player p) {
        Holder h = new Holder(Kind.HUB, 0);
        Inventory inv = Bukkit.createInventory(h, 27, Msg.c("§cAdmin serveur"));
        h.inv = inv;
        inv.setItem(11, button(Material.COMMAND_BLOCK, "§6Gamerules", "§7Toutes les règles du monde où vous êtes."));
        inv.setItem(13, button(Material.OBSIDIAN, "§5Dimensions", "§7Activer / désactiver le Nether et l'End."));
        inv.setItem(15, button(Material.NETHER_STAR, "§bActions globales", "§7Soigner, nourrir, give à tous, jour / nuit..."));
        p.openInventory(inv);
    }

    private void rules(Player p, int page) {
        Holder h = new Holder(Kind.RULES, Math.max(0, page));
        World w = p.getWorld();
        Inventory inv = Bukkit.createInventory(h, 54, Msg.c("§6Gamerules : " + w.getName()));
        h.inv = inv;
        List<String> names = GameRuleAccess.names(w);
        if (names.isEmpty()) inv.setItem(22, button(Material.BARRIER, "§cAucune règle détectée", "§7L'API des gamerules n'est pas lisible sur cette version."));
        int from = h.page * 45;
        for (int i = 0; i < 45 && from + i < names.size(); i++) {
            String n = names.get(from + i);
            h.rules.add(n);
            String v = GameRuleAccess.value(w, n);
            boolean bool = "true".equals(v) || "false".equals(v);
            if (bool) {
                boolean on = Boolean.parseBoolean(v);
                inv.setItem(i, button(on ? Material.LIME_DYE : Material.GRAY_DYE, (on ? "§a" : "§c") + n, "§7Valeur : §f" + v, "§eClic §7: inverser"));
            } else {
                inv.setItem(i, button(Material.PAPER, "§e" + n, "§7Valeur : §f" + v, "§eClic gauche §7: +1  §eClic droit §7: -1", "§eShift §7: ±10"));
            }
        }
        inv.setItem(45, h.page > 0 ? button(Material.ARROW, "§7« Précédent") : null);
        inv.setItem(49, button(Material.BARRIER, "§7« Retour"));
        inv.setItem(53, from + 45 < names.size() ? button(Material.ARROW, "§7Suivant »") : null);
        p.openInventory(inv);
    }

    private void dims(Player p) {
        Holder h = new Holder(Kind.DIMS, 0);
        Inventory inv = Bukkit.createInventory(h, 27, Msg.c("§5Dimensions"));
        h.inv = inv;
        boolean n = plugin.dimensions().netherEnabled(), e = plugin.dimensions().endEnabled();
        inv.setItem(11, button(n ? Material.NETHERRACK : Material.BARRIER, (n ? "§aNether activé" : "§cNether désactivé"), "§eClic §7: " + (n ? "désactiver" : "activer"),
                "§8Désactiver renvoie les joueurs présents dans le Nether."));
        inv.setItem(15, button(e ? Material.END_STONE : Material.BARRIER, (e ? "§aEnd activé" : "§cEnd désactivé"), "§eClic §7: " + (e ? "désactiver" : "activer"),
                "§8Désactiver renvoie les joueurs présents dans l'End."));
        inv.setItem(22, button(Material.BARRIER, "§7« Retour"));
        p.openInventory(inv);
    }

    private void actions(Player p) {
        Holder h = new Holder(Kind.ACTIONS, 0);
        Inventory inv = Bukkit.createInventory(h, 36, Msg.c("§bActions globales"));
        h.inv = inv;
        inv.setItem(10, button(Material.GOLDEN_APPLE, "§aSoigner tout le monde"));
        inv.setItem(11, button(Material.COOKED_BEEF, "§aNourrir tout le monde"));
        inv.setItem(12, button(Material.MILK_BUCKET, "§aRetirer les effets de tous"));
        inv.setItem(13, button(Material.CHEST, "§6Give à tous", "§7Donne l'item de votre main principale à tous les joueurs."));
        inv.setItem(14, button(Material.ENDER_PEARL, "§dRassembler tout le monde sur vous"));
        inv.setItem(15, button(Material.SUNFLOWER, "§eJour"));
        inv.setItem(16, button(Material.BLACK_WOOL, "§8Nuit"));
        inv.setItem(19, button(Material.WATER_BUCKET, "§bTemps clair"));
        inv.setItem(21, button(Material.TNT, "§cExpulser tous les joueurs", "§7Sauf le staff. §cShift + clic §7pour confirmer."));
        inv.setItem(31, button(Material.BARRIER, "§7« Retour"));
        p.openInventory(inv);
    }

    @EventHandler
    public void click(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof Holder h)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p) || e.getClickedInventory() != e.getView().getTopInventory()) return;
        if (!p.hasPermission("vortexessentials.servermenu")) return;
        int slot = e.getRawSlot();
        switch (h.kind) {
            case HUB -> { if (slot == 11) rules(p, 0); else if (slot == 13) dims(p); else if (slot == 15) actions(p); }
            case RULES -> {
                if (slot == 49) { hub(p); return; }
                if (slot == 45 && h.page > 0) { rules(p, h.page - 1); return; }
                if (slot == 53 && e.getCurrentItem() != null) { rules(p, h.page + 1); return; }
                if (slot < 0 || slot >= h.rules.size()) return;
                String name = h.rules.get(slot);
                String v = GameRuleAccess.value(p.getWorld(), name);
                if ("true".equals(v) || "false".equals(v)) {
                    GameRuleAccess.set(p.getWorld(), name, String.valueOf(!Boolean.parseBoolean(v)));
                } else if (v != null) {
                    try {
                        int step = e.isShiftClick() ? 10 : 1;
                        int cur = Integer.parseInt(v);
                        GameRuleAccess.set(p.getWorld(), name, String.valueOf(e.getClick().isRightClick() ? cur - step : cur + step));
                    } catch (NumberFormatException ignored) { }
                }
                Bukkit.getScheduler().runTaskLater(plugin, () -> { if (p.isOnline()) rules(p, h.page); }, 2L);
            }
            case DIMS -> {
                if (slot == 11) plugin.dimensions().setNether(!plugin.dimensions().netherEnabled());
                else if (slot == 15) plugin.dimensions().setEnd(!plugin.dimensions().endEnabled());
                else if (slot == 22) { hub(p); return; }
                dims(p);
            }
            case ACTIONS -> action(p, slot, e.getClick());
        }
    }

    private void action(Player p, int slot, ClickType click) {
        int n = 0;
        switch (slot) {
            case 10 -> {
                for (Player t : Bukkit.getOnlinePlayers()) {
                    AttributeInstance mh = t.getAttribute(Attribute.MAX_HEALTH);
                    t.setHealth(mh == null ? 20 : mh.getValue());
                    t.setFireTicks(0);
                    t.setFoodLevel(20);
                    n++;
                }
                Msg.prefixed(p, "§a" + n + " joueur(s) soigné(s).");
            }
            case 11 -> {
                for (Player t : Bukkit.getOnlinePlayers()) { t.setFoodLevel(20); t.setSaturation(20f); n++; }
                Msg.prefixed(p, "§a" + n + " joueur(s) nourri(s).");
            }
            case 12 -> {
                for (Player t : Bukkit.getOnlinePlayers()) {
                    for (PotionEffect fx : new ArrayList<>(t.getActivePotionEffects())) t.removePotionEffect(fx.getType());
                    n++;
                }
                Msg.prefixed(p, "§aEffets retirés pour " + n + " joueur(s).");
            }
            case 13 -> {
                ItemStack hand = p.getInventory().getItemInMainHand();
                if (hand.getType().isAir()) { Msg.prefixed(p, "§cTenez l'item à donner en main."); return; }
                for (Player t : Bukkit.getOnlinePlayers()) {
                    t.getInventory().addItem(hand.clone()).values().forEach(r -> t.getWorld().dropItemNaturally(t.getLocation(), r));
                    n++;
                }
                Msg.prefixed(p, "§aItem donné à " + n + " joueur(s).");
            }
            case 14 -> {
                for (Player t : Bukkit.getOnlinePlayers()) if (!t.equals(p)) { t.teleport(p.getLocation()); n++; }
                Msg.prefixed(p, "§a" + n + " joueur(s) téléporté(s) sur vous.");
            }
            case 15 -> { p.getWorld().setTime(1000); Msg.prefixed(p, "§aIl fait jour."); }
            case 16 -> { p.getWorld().setTime(13000); Msg.prefixed(p, "§7Il fait nuit."); }
            case 19 -> { p.getWorld().setStorm(false); p.getWorld().setThundering(false); Msg.prefixed(p, "§bTemps clair."); }
            case 21 -> {
                if (!click.isShiftClick()) { Msg.prefixed(p, "§cShift + clic pour confirmer l'expulsion."); return; }
                for (Player t : Bukkit.getOnlinePlayers()) {
                    if (t.equals(p) || t.isOp() || t.hasPermission("vortexessentials.maintenance.bypass")) continue;
                    t.kick(Msg.c("§cLe serveur est en cours de gestion par le staff."));
                    n++;
                }
                Msg.prefixed(p, "§a" + n + " joueur(s) expulsé(s).");
            }
            case 31 -> hub(p);
            default -> { }
        }
    }

    @EventHandler
    public void drag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Holder) e.setCancelled(true);
    }
}
