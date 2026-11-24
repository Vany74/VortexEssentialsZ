package fr.vortexsmp.vortexessentials.staff;

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
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** /jail, /unjail, /setjail, /deljail, /jails, /freeze, /staffxray (alias /sx). */
public final class StaffCommands implements CommandExecutor, TabCompleter {
    private final VortexEssentials plugin;

    public StaffCommands(VortexEssentials plugin) {
        this.plugin = plugin;
    }

    private StaffManager staff() { return plugin.staff(); }

    @Override
    public boolean onCommand(CommandSender s, Command c, String label, String[] a) {
        String name = c.getName().toLowerCase(Locale.ROOT);
        String perm = switch (name) {
            case "freeze" -> "vortexessentials.staff.freeze";
            case "staffxray" -> "vortexessentials.staff.xray";
            default -> "vortexessentials.staff.jail";
        };
        if (!s.hasPermission(perm)) { Msg.send(s, "§cPermission refusée."); return true; }
        switch (name) {
            case "jail" -> jail(s, a);
            case "unjail" -> unjail(s, a);
            case "setjail" -> setJail(s, a);
            case "deljail" -> {
                if (a.length < 1) { Msg.send(s, "§e/deljail <nom>"); return true; }
                Msg.prefixed(s, staff().delJail(a[0]) ? "§7Prison supprimée." : "§cPrison introuvable.");
            }
            case "jails" -> {
                Msg.send(s, "§6Prisons : §f" + (staff().jailNames().isEmpty() ? "aucune" : String.join(", ", staff().jailNames())));
                Msg.send(s, "§6Emprisonnés : §f" + (staff().jailedNames().isEmpty() ? "personne" : String.join(", ", staff().jailedNames())));
            }
            case "freeze" -> freeze(s, a);
            case "staffxray" -> xray(s, a);
            default -> { }
        }
        return true;
    }

    private void jail(CommandSender s, String[] a) {
        if (a.length < 1) { Msg.send(s, "§e/jail <joueur> [minutes] [raison...] §7(sans durée : jusqu'à /unjail)"); return; }
        Player t = Bukkit.getPlayerExact(a[0]);
        if (t == null) { Msg.send(s, "§cJoueur introuvable (il doit être en ligne)."); return; }
        long minutes = 0;
        int i = 1;
        if (a.length > 1) {
            try { minutes = Long.parseLong(a[1]); i = 2; } catch (NumberFormatException ignored) { }
        }
        String reason = a.length > i ? String.join(" ", Arrays.copyOfRange(a, i, a.length)) : "Décision du staff";
        String err = staff().jail(t, minutes, reason, s.getName());
        Msg.prefixed(s, err != null ? "§c" + err : "§a" + t.getName() + " est en prison" + (minutes > 0 ? " pour " + minutes + " min." : "."));
    }

    private void unjail(CommandSender s, String[] a) {
        if (a.length < 1) { Msg.send(s, "§e/unjail <joueur>"); return; }
        OfflinePlayer t = Bukkit.getOfflinePlayer(a[0]);
        Msg.prefixed(s, staff().unjail(t.getUniqueId(), true) ? "§a" + a[0] + " est libéré." : "§cCe joueur n'est pas en prison.");
    }

    private void setJail(CommandSender s, String[] a) {
        if (!(s instanceof Player p)) { Msg.send(s, "§cCommande réservée aux joueurs."); return; }
        String n = a.length > 0 ? a[0] : "default";
        staff().setJail(n, p.getLocation());
        Msg.prefixed(p, "§aPrison §f" + n.toLowerCase(Locale.ROOT) + " §adéfinie ici.");
    }

    private void freeze(CommandSender s, String[] a) {
        if (a.length < 1) { Msg.send(s, "§e/freeze <joueur>"); return; }
        Player t = Bukkit.getPlayerExact(a[0]);
        if (t == null) { Msg.send(s, "§cJoueur introuvable."); return; }
        boolean now = staff().toggleFreeze(t.getUniqueId());
        Msg.prefixed(s, "§f" + t.getName() + " §7: " + (now ? "§bgelé" : "§adégelé"));
        t.sendMessage(Msg.c(now ? "§bVous avez été gelé par le staff. Ne vous déconnectez pas." : "§aVous pouvez de nouveau bouger."));
    }

    private void xray(CommandSender s, String[] a) {
        if (!(s instanceof Player p)) { Msg.send(s, "§cCommande réservée aux joueurs."); return; }
        int radius = 12;
        try { if (a.length > 0) radius = Integer.parseInt(a[0]); } catch (NumberFormatException ignored) { }
        boolean on = plugin.staffXray().toggle(p, radius);
        Msg.prefixed(p, "§7X-ray staff : " + (on ? "§aactivé §7(minerais entourés d'un halo, visibles à travers les blocs)" : "§cdésactivé"));
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String l, String[] a) {
        List<String> out = new ArrayList<>();
        String name = c.getName().toLowerCase(Locale.ROOT);
        if (a.length == 1 && List.of("jail", "freeze").contains(name)) Bukkit.getOnlinePlayers().forEach(p -> out.add(p.getName()));
        else if (a.length == 1 && name.equals("unjail")) out.addAll(staff().jailedNames());
        else if (a.length == 1 && name.equals("deljail")) out.addAll(staff().jailNames());
        String prefix = a.length == 0 ? "" : a[a.length - 1].toLowerCase(Locale.ROOT);
        return out.stream().filter(x -> x.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
    }
}
