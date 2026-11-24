package fr.vortexsmp.vortexessentials.maintenance;

import fr.vortexsmp.vortexessentials.Msg;
import fr.vortexsmp.vortexessentials.VortexEssentials;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** /maintenance on|off|toggle|status|add|remove|list|kick (alias /maint). */
public final class MaintenanceCommand implements CommandExecutor, TabCompleter {
    private final VortexEssentials plugin;

    public MaintenanceCommand(VortexEssentials plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender s, Command c, String label, String[] a) {
        if (!s.hasPermission("vortexessentials.maintenance")) { Msg.send(s, "§cPermission refusée."); return true; }
        MaintenanceManager m = plugin.maintenance();
        String sub = a.length == 0 ? "status" : a[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "on" -> { m.setEnabled(true); Msg.prefixed(s, "§cMaintenance activée §7: seuls le staff et la liste de maintenance peuvent rejoindre."); }
            case "off" -> { m.setEnabled(false); Msg.prefixed(s, "§aMaintenance désactivée §7: tout le monde peut rejoindre."); }
            case "toggle" -> {
                m.setEnabled(!m.enabled());
                Msg.prefixed(s, "§7Maintenance : " + (m.enabled() ? "§cactivée" : "§adésactivée"));
            }
            case "add" -> {
                if (a.length < 2) { Msg.send(s, "§e/maintenance add <joueur>"); return true; }
                OfflinePlayer t = Bukkit.getOfflinePlayer(a[1]);
                m.add(t);
                Msg.prefixed(s, "§7" + a[1] + " peut rejoindre pendant la maintenance.");
            }
            case "remove" -> {
                if (a.length < 2) { Msg.send(s, "§e/maintenance remove <joueur>"); return true; }
                Msg.prefixed(s, m.remove(Bukkit.getOfflinePlayer(a[1]).getUniqueId()) ? "§7Joueur retiré de la liste." : "§cJoueur absent de la liste.");
            }
            case "list" -> {
                Msg.send(s, "§6Liste de maintenance §7(" + m.allowed().size() + ")");
                for (Map.Entry<UUID, String> e : m.allowed().entrySet()) s.sendMessage(Msg.c("§8 - §f" + e.getValue()));
            }
            case "kick" -> { m.kickOnline(); Msg.prefixed(s, "§7Joueurs sans accès expulsés."); }
            default -> Msg.send(s, "§6Maintenance : " + (m.enabled() ? "§cactivée" : "§adésactivée")
                    + " §8| §e/maintenance on|off|toggle|add|remove|list|kick");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String l, String[] a) {
        List<String> out = new ArrayList<>();
        if (a.length == 1) out.addAll(List.of("on", "off", "toggle", "status", "add", "remove", "list", "kick"));
        else if (a.length == 2 && (a[0].equalsIgnoreCase("add") || a[0].equalsIgnoreCase("remove"))) Bukkit.getOnlinePlayers().forEach(p -> out.add(p.getName()));
        String prefix = a[a.length - 1].toLowerCase(Locale.ROOT);
        return out.stream().filter(x -> x.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
    }
}
