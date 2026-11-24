package fr.vortexsmp.vortexessentials.staffmod;

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

/** /staffmod [toggle | status | list | menu | assign <joueur> <palier> | unassign <joueur> | tier create|delete|list]. */
public final class StaffModCommand implements CommandExecutor, TabCompleter {
    private final VortexEssentials plugin;

    public StaffModCommand(VortexEssentials plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender s, Command c, String label, String[] a) {
        String sub = a.length == 0 ? "toggle" : a[0].toLowerCase(Locale.ROOT);
        StaffMod sm = plugin.staffMod();
        boolean admin = s.hasPermission("vortexessentials.staffmod.admin");
        switch (sub) {
            case "toggle", "on", "off" -> {
                if (!(s instanceof Player p)) { Msg.send(s, "§cCommande réservée aux joueurs."); return true; }
                if (sm.tierOf(p) == null && !sm.isActive(p.getUniqueId())) { Msg.send(p, "§cAucun palier de staff ne vous est attribué."); return true; }
                if (sub.equals("on") && sm.isActive(p.getUniqueId())) { Msg.send(p, "§7Mode staff déjà actif."); return true; }
                if (sub.equals("off") && !sm.isActive(p.getUniqueId())) { Msg.send(p, "§7Mode staff déjà désactivé."); return true; }
                String err = sm.toggle(p);
                if (err != null) Msg.send(p, "§c" + err);
            }
            case "status" -> {
                if (!(s instanceof Player p)) { Msg.send(s, "§cCommande réservée aux joueurs."); return true; }
                StaffMod.Tier t = sm.tierOf(p);
                Msg.send(p, "§bPalier : §f" + (t == null ? "aucun" : t.name) + " §8| §7Mode staff : " + (sm.isActive(p.getUniqueId()) ? "§aactif" : "§cinactif"));
            }
            case "list" -> {
                if (!s.hasPermission("vortexessentials.staffmod")) { Msg.send(s, "§cPermission refusée."); return true; }
                Msg.send(s, "§bStaff en mode staff : §f" + (sm.activeNames().isEmpty() ? "personne" : String.join(", ", sm.activeNames())));
            }
            case "menu" -> {
                if (!admin) { Msg.send(s, "§cPermission refusée."); return true; }
                if (!(s instanceof Player p)) { Msg.send(s, "§cCommande réservée aux joueurs."); return true; }
                plugin.staffModMenu().openList(p);
            }
            case "assign" -> {
                if (!admin) { Msg.send(s, "§cPermission refusée."); return true; }
                if (a.length < 3 || sm.tier(a[2]) == null) { Msg.send(s, "§e/staffmod assign <joueur> <palier> §7(paliers : /staffmod tier list)"); return true; }
                OfflinePlayer t = Bukkit.getOfflinePlayer(a[1]);
                sm.assign(t.getUniqueId(), a[2].toLowerCase(Locale.ROOT));
                Msg.prefixed(s, "§f" + a[1] + " §7a maintenant le palier §b" + a[2].toLowerCase(Locale.ROOT));
            }
            case "unassign" -> {
                if (!admin) { Msg.send(s, "§cPermission refusée."); return true; }
                if (a.length < 2) { Msg.send(s, "§e/staffmod unassign <joueur>"); return true; }
                Msg.prefixed(s, sm.unassign(Bukkit.getOfflinePlayer(a[1]).getUniqueId()) ? "§7Palier retiré." : "§cCe joueur n'a pas de palier assigné.");
            }
            case "tier" -> {
                if (!admin) { Msg.send(s, "§cPermission refusée."); return true; }
                String ts = a.length > 1 ? a[1].toLowerCase(Locale.ROOT) : "list";
                switch (ts) {
                    case "create" -> {
                        if (a.length < 3) { Msg.send(s, "§e/staffmod tier create <nom>"); return true; }
                        String err = sm.create(a[2]);
                        Msg.prefixed(s, err != null ? "§c" + err : "§aPalier créé : modifiez-le avec §f/staffmod menu");
                    }
                    case "delete" -> Msg.prefixed(s, a.length > 2 && sm.delete(a[2].toLowerCase(Locale.ROOT)) ? "§7Palier supprimé." : "§cPalier introuvable.");
                    default -> {
                        Msg.send(s, "§bPaliers du mode staff");
                        for (StaffMod.Tier t : sm.tiers()) s.sendMessage(Msg.c("§8 - §b" + t.name + " §7(poids " + t.weight + ", " + t.features.size() + " groupe(s) : §f" + String.join(", ", t.features) + "§7)"));
                    }
                }
            }
            default -> {
                Msg.send(s, "§b§lMode staff §7— /staffmod");
                s.sendMessage(Msg.c("§e/staffmod §7: activer / désactiver · §e/staffmod status §7· §e/staffmod list"));
                if (admin) s.sendMessage(Msg.c("§e/staffmod menu §7: paliers · §eassign <joueur> <palier> §7· §eunassign <joueur> §7· §etier create|delete|list"));
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String l, String[] a) {
        List<String> out = new ArrayList<>();
        boolean admin = s.hasPermission("vortexessentials.staffmod.admin");
        if (a.length == 1) {
            out.addAll(List.of("toggle", "on", "off", "status", "list"));
            if (admin) out.addAll(List.of("menu", "assign", "unassign", "tier"));
        } else if (admin && a.length == 2 && (a[0].equalsIgnoreCase("assign") || a[0].equalsIgnoreCase("unassign"))) {
            Bukkit.getOnlinePlayers().forEach(p -> out.add(p.getName()));
        } else if (admin && a.length == 3 && a[0].equalsIgnoreCase("assign")) {
            plugin.staffMod().tiers().forEach(t -> out.add(t.name));
        } else if (admin && a.length == 2 && a[0].equalsIgnoreCase("tier")) {
            out.addAll(List.of("create", "delete", "list"));
        } else if (admin && a.length == 3 && a[0].equalsIgnoreCase("tier") && a[1].equalsIgnoreCase("delete")) {
            plugin.staffMod().tiers().forEach(t -> out.add(t.name));
        }
        String prefix = a[a.length - 1].toLowerCase(Locale.ROOT);
        return out.stream().filter(x -> x.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
    }
}
