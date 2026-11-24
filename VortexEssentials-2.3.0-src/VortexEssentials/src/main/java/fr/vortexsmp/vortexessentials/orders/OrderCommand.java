package fr.vortexsmp.vortexessentials.orders;

import fr.vortexsmp.vortexessentials.Msg;
import fr.vortexsmp.vortexessentials.VortexEssentials;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** /orders [mine | create <quantité> <prix> | collect | cancel <id> | server create|remove|list] (alias /order, /commandes). */
public final class OrderCommand implements CommandExecutor, TabCompleter {
    private final VortexEssentials plugin;

    public OrderCommand(VortexEssentials plugin) {
        this.plugin = plugin;
    }

    private static double num(String s) throws NumberFormatException { return Double.parseDouble(s.replace(',', '.')); }

    @Override
    public boolean onCommand(CommandSender s, Command c, String label, String[] a) {
        if (!s.hasPermission("vortexessentials.orders")) { Msg.send(s, "§cPermission refusée."); return true; }
        if (!plugin.getConfig().getBoolean("orders.enabled", true)) { Msg.send(s, "§cLes commandes sont désactivées."); return true; }
        String sub = a.length == 0 ? "menu" : a[0].toLowerCase(Locale.ROOT);
        if (sub.equals("server")) { server(s, a); return true; }
        if (!(s instanceof Player p)) { Msg.send(s, "§cCommande réservée aux joueurs."); return true; }
        if (!plugin.money().available()) { Msg.send(p, "§cAucune économie détectée : installez VaultUnlocked et un plugin d'économie."); return true; }
        try {
            switch (sub) {
                case "mine", "mes" -> plugin.orderMenus().open(p, true, 0);
                case "create", "creer", "créer" -> {
                    if (a.length < 3) { Msg.send(p, "§e/orders create <quantité> <prix unitaire> §7(item en main ; l'argent est mis de côté)"); return true; }
                    String err = plugin.orders().create(p, p.getInventory().getItemInMainHand(), (int) num(a[1]), num(a[2]));
                    Msg.prefixed(p, err != null ? "§c" + err : "§aCommande publiée : les autres joueurs peuvent maintenant vous livrer. §7(/orders mine)");
                }
                case "collect" -> {
                    int total = 0;
                    for (OrderManager.Order o : plugin.orders().mine(p.getUniqueId())) total += plugin.orders().collect(p, o);
                    Msg.prefixed(p, total > 0 ? "§aVous récupérez §f" + total + " §aitem(s)." : "§7Rien à récupérer.");
                }
                case "cancel" -> {
                    OrderManager.Order o = a.length > 1 ? plugin.orders().get((int) num(a[1])) : null;
                    if (o == null) { Msg.send(p, "§e/orders cancel <id>"); return true; }
                    String err = plugin.orders().cancel(p, o, p.hasPermission("vortexessentials.orders.admin"));
                    Msg.prefixed(p, err != null ? "§c" + err : "§7Commande annulée.");
                }
                default -> plugin.orderMenus().open(p, false, 0);
            }
        } catch (NumberFormatException e) {
            Msg.send(p, "§cNombre invalide.");
        }
        return true;
    }

    private void server(CommandSender s, String[] a) {
        if (!s.hasPermission("vortexessentials.orders.admin")) { Msg.send(s, "§cPermission refusée."); return; }
        String sub = a.length > 1 ? a[1].toLowerCase(Locale.ROOT) : "list";
        try {
            switch (sub) {
                case "create" -> {
                    if (!(s instanceof Player p)) { Msg.send(s, "§cCommande réservée aux joueurs."); return; }
                    if (a.length < 4) { Msg.send(s, "§e/orders server create <quantité> <prix> [minutes de reconstitution] §7(item en main)"); return; }
                    String err = plugin.orders().createServer(p.getInventory().getItemInMainHand(), (int) num(a[2]), num(a[3]), a.length > 4 ? (long) num(a[4]) : 0);
                    Msg.prefixed(s, err != null ? "§c" + err : "§aCommande du serveur publiée.");
                }
                case "remove" -> {
                    OrderManager.Order o = a.length > 2 ? plugin.orders().get((int) num(a[2])) : null;
                    if (o == null || !o.server) { Msg.send(s, "§cCommande du serveur introuvable."); return; }
                    plugin.orders().cancel(s instanceof Player pl ? pl : null, o, true);
                    Msg.prefixed(s, "§7Commande du serveur supprimée.");
                }
                default -> {
                    Msg.send(s, "§6Commandes du serveur §7(" + plugin.orders().servers().size() + ")");
                    for (OrderManager.Order o : plugin.orders().servers()) {
                        s.sendMessage(Msg.c("§8#" + o.id + " §f" + o.mat.name() + " §7" + o.remaining + "/" + o.total + " à §a" + plugin.money().format(o.price)
                                + (o.refillMs > 0 ? " §8(reconstitué toutes les " + o.refillMs / 60_000L + " min)" : "")));
                    }
                }
            }
        } catch (NumberFormatException e) {
            Msg.send(s, "§cNombre invalide.");
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String l, String[] a) {
        List<String> out = new ArrayList<>();
        if (a.length == 1) {
            out.addAll(List.of("mine", "create", "collect", "cancel"));
            if (s.hasPermission("vortexessentials.orders.admin")) out.add("server");
        } else if (a.length == 2 && a[0].equalsIgnoreCase("server")) {
            out.addAll(List.of("create", "remove", "list"));
        }
        String prefix = a[a.length - 1].toLowerCase(Locale.ROOT);
        return out.stream().filter(x -> x.startsWith(prefix)).toList();
    }
}
