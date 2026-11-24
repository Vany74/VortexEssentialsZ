package fr.vortexsmp.vortexessentials.bounty;

import fr.vortexsmp.vortexessentials.Msg;
import fr.vortexsmp.vortexessentials.VortexEssentials;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** /bounty [<joueur> <montant> | set <joueur> <montant> | list | check <joueur> | remove <joueur>] (alias /bounties, /prime). */
public final class BountyCommand implements CommandExecutor, TabCompleter {
    private final VortexEssentials plugin;

    public BountyCommand(VortexEssentials plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender s, Command c, String label, String[] a) {
        if (!s.hasPermission("vortexessentials.bounty")) { Msg.send(s, "§cPermission refusée."); return true; }
        if (a.length == 0) {
            if (!(s instanceof Player p)) { Msg.send(s, "§cCommande réservée aux joueurs."); return true; }
            plugin.bountyMenus().open(p);
            return true;
        }
        String sub = a[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "list" -> {
                List<BountyManager.Bounty> top = plugin.bounties().top();
                if (top.isEmpty()) { Msg.send(s, "§7Aucune prime pour le moment."); return true; }
                Msg.send(s, "§cPrimes en cours");
                int i = 1;
                for (BountyManager.Bounty b : top.subList(0, Math.min(10, top.size()))) {
                    s.sendMessage(Msg.c("§e#" + i++ + " §f" + b.name + " §7— §6" + plugin.money().format(b.total)));
                }
            }
            case "check" -> {
                if (a.length < 2) { Msg.send(s, "§e/bounty check <joueur>"); return true; }
                BountyManager.Bounty b = plugin.bounties().get(Bukkit.getOfflinePlayer(a[1]).getUniqueId());
                Msg.send(s, b == null ? "§7Aucune prime sur §f" + a[1] + "§7." : "§f" + b.name + " §7: prime de §6" + plugin.money().format(b.total));
            }
            case "remove" -> {
                if (!s.hasPermission("vortexessentials.bounty.admin")) { Msg.send(s, "§cPermission refusée."); return true; }
                if (a.length < 2) { Msg.send(s, "§e/bounty remove <joueur>"); return true; }
                Msg.prefixed(s, plugin.bounties().remove(Bukkit.getOfflinePlayer(a[1]).getUniqueId()) ? "§7Prime annulée, contributeurs remboursés." : "§cAucune prime sur ce joueur.");
            }
            default -> {
                if (!(s instanceof Player p)) { Msg.send(s, "§cCommande réservée aux joueurs."); return true; }
                int idx = sub.equals("set") ? 1 : 0;
                if (a.length < idx + 2) { Msg.send(p, "§e/bounty <joueur> <montant>"); return true; }
                OfflinePlayer t = Bukkit.getOfflinePlayer(a[idx]);
                try {
                    String err = plugin.bounties().place(p, t, Double.parseDouble(a[idx + 1].replace(',', '.')));
                    if (err != null) Msg.prefixed(p, "§c" + err);
                } catch (NumberFormatException e) {
                    Msg.send(p, "§cMontant invalide.");
                }
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String l, String[] a) {
        List<String> out = new ArrayList<>();
        if (a.length == 1) {
            out.addAll(List.of("list", "check", "set"));
            Bukkit.getOnlinePlayers().forEach(p -> out.add(p.getName()));
            if (s.hasPermission("vortexessentials.bounty.admin")) out.add("remove");
        } else if (a.length == 2 && List.of("check", "set", "remove").contains(a[0].toLowerCase(Locale.ROOT))) {
            Bukkit.getOnlinePlayers().forEach(p -> out.add(p.getName()));
        }
        String prefix = a[a.length - 1].toLowerCase(Locale.ROOT);
        return out.stream().filter(x -> x.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
    }
}
