package fr.vortexsmp.vortexessentials.playtime;

import fr.vortexsmp.vortexessentials.Msg;
import fr.vortexsmp.vortexessentials.VortexEssentials;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** /playtime [joueur | top] (alias /pt, /temps). */
public final class PlaytimeCommand implements CommandExecutor, TabCompleter {
    private final VortexEssentials plugin;

    public PlaytimeCommand(VortexEssentials plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender s, Command c, String label, String[] a) {
        if (!s.hasPermission("vortexessentials.playtime")) { Msg.send(s, "§cPermission refusée."); return true; }
        PlaytimeManager pm = plugin.playtime();

        if (a.length > 0 && a[0].equalsIgnoreCase("top")) {
            List<Map.Entry<UUID, Long>> top = pm.top(10);
            if (top.isEmpty()) { Msg.send(s, "§7Aucune donnée de temps de jeu."); return true; }
            Msg.send(s, "§6§lTop temps de jeu");
            int i = 1;
            for (Map.Entry<UUID, Long> e : top) {
                Msg.send(s, "§e#" + i++ + " §f" + pm.name(e.getKey()) + " §7— §f" + PlaytimeManager.format(e.getValue()));
            }
            return true;
        }

        if (a.length > 0) {
            UUID id = pm.find(a[0]);
            if (id == null) {
                Player online = Bukkit.getPlayerExact(a[0]);
                if (online == null) { Msg.send(s, "§cJoueur introuvable."); return true; }
                id = online.getUniqueId();
            }
            Msg.prefixed(s, "§7Temps de jeu de §f" + pm.name(id) + " §7: §f" + PlaytimeManager.format(pm.seconds(id)));
            return true;
        }
        if (!(s instanceof Player p)) { Msg.send(s, "§cCommande réservée aux joueurs."); return true; }
        Msg.prefixed(p, "§7Temps de jeu : §f" + PlaytimeManager.format(pm.seconds(p.getUniqueId()))
                + " §8(classement : #" + pm.rank(p.getUniqueId()) + ")");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String l, String[] a) {
        List<String> out = new ArrayList<>();
        if (a.length == 1) {
            out.add("top");
            Bukkit.getOnlinePlayers().forEach(p -> out.add(p.getName()));
        }
        String prefix = a.length == 0 ? "" : a[a.length - 1].toLowerCase(Locale.ROOT);
        return out.stream().filter(x -> x.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
    }
}
