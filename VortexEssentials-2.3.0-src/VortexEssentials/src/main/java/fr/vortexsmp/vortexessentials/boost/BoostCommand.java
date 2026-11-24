package fr.vortexsmp.vortexessentials.boost;

import fr.vortexsmp.vortexessentials.Msg;
import fr.vortexsmp.vortexessentials.VortexEssentials;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Sous-commandes d'administration de /vortex boost : create, delete, set, perm, effect, event, edit. */
public final class BoostCommand {
    public static final List<String> ADMIN_SUBS = List.of("create", "delete", "set", "perm", "effect", "event", "edit");

    private final VortexEssentials plugin;

    public BoostCommand(VortexEssentials plugin) {
        this.plugin = plugin;
    }

    /** a[0] = "boost". Renvoie true si la sous-commande a été traitée ici. */
    public boolean run(CommandSender s, String[] a) {
        String sub = a.length > 1 ? a[1].toLowerCase(Locale.ROOT) : "";
        if (!ADMIN_SUBS.contains(sub)) return false;
        if (!s.hasPermission("vortexessentials.admin")) {
            Msg.send(s, "§cPermission refusée.");
            return true;
        }
        BoostManager bm = plugin.boosts();

        switch (sub) {
            case "create" -> {
                if (a.length < 3) { Msg.send(s, "§e/vortex boost create <id>"); return true; }
                String err = bm.create(a[2]);
                Msg.prefixed(s, err != null ? "§c" + err
                        : "§aBoost §f" + BoostManager.cleanId(a[2]) + " §acréé. Réglez-le avec §f/vortex boost set §aou §f/vortex boost edit§a.");
            }
            case "delete" -> {
                if (a.length < 3) { Msg.send(s, "§e/vortex boost delete <id>"); return true; }
                Msg.prefixed(s, bm.delete(a[2]) ? "§7Boost supprimé." : "§cBoost introuvable.");
            }
            case "set" -> {
                if (a.length < 5) { Msg.send(s, "§e/vortex boost set <id> <" + String.join("|", BoostManager.STAT_KEYS) + "> <valeur>"); return true; }
                try {
                    String err = bm.setStat(a[2], a[3], Double.parseDouble(a[4].replace(',', '.')));
                    Msg.prefixed(s, err != null ? "§c" + err : "§7" + a[3].toLowerCase(Locale.ROOT) + " de §f" + a[2] + " §7: §f" + a[4]);
                } catch (NumberFormatException e) {
                    Msg.send(s, "§cNombre invalide.");
                }
            }
            case "perm" -> {
                if (a.length < 4) { Msg.send(s, "§e/vortex boost perm <id> <permission>"); return true; }
                String err = bm.setPermission(a[2], a[3]);
                Msg.prefixed(s, err != null ? "§c" + err : "§7Permission de §f" + a[2] + " §7: §f" + a[3]);
            }
            case "effect" -> {
                if (a.length < 5) { Msg.send(s, "§e/vortex boost effect <id> add <effet> <niveau> | remove <effet>"); return true; }
                String err;
                if (a[3].equalsIgnoreCase("remove")) err = bm.setEffect(a[2], a[4], 0);
                else if (a[3].equalsIgnoreCase("add") && a.length >= 6) {
                    try { err = bm.setEffect(a[2], a[4], Integer.parseInt(a[5])); }
                    catch (NumberFormatException e) { err = "Nombre invalide."; }
                } else { Msg.send(s, "§e/vortex boost effect <id> add <effet> <niveau> | remove <effet>"); return true; }
                Msg.prefixed(s, err != null ? "§c" + err : "§7Effets de §f" + a[2] + " §7mis à jour.");
            }
            case "event" -> event(s, a);
            case "edit" -> {
                if (!(s instanceof Player p)) { Msg.send(s, "§cCommande réservée aux joueurs."); return true; }
                if (a.length >= 3 && bm.group(a[2]) != null) plugin.adminMenus().openBoostEditor(p, a[2]);
                else plugin.adminMenus().openList(p, "boosts");
            }
            default -> { }
        }
        return true;
    }

    private void event(CommandSender s, String[] a) {
        BoostManager bm = plugin.boosts();
        String act = a.length > 2 ? a[2].toLowerCase(Locale.ROOT) : "list";
        switch (act) {
            case "start" -> {
                if (a.length < 6 || !BoostManager.EVENT_KEYS.contains(a[3].toLowerCase(Locale.ROOT))) {
                    Msg.send(s, "§e/vortex boost event start <" + String.join("|", BoostManager.EVENT_KEYS) + "> <multiplicateur> <minutes>");
                    Msg.send(s, "§8Pour cooldown, le multiplicateur est un facteur (0.5 = cooldowns divisés par 2).");
                    return;
                }
                try {
                    double mult = Double.parseDouble(a[4].replace(',', '.'));
                    int minutes = Integer.parseInt(a[5]);
                    if (mult <= 0 || minutes <= 0) { Msg.send(s, "§cValeurs invalides."); return; }
                    BoostManager.Event e = bm.startEvent(a[3], mult, minutes);
                    String text = "⚡ Événement : " + a[3].toLowerCase(Locale.ROOT) + " x" + mult + " pendant " + minutes + " min !";
                    Bukkit.broadcast(Msg.c("§a" + text));
                    plugin.discord().announce("Événement de boost", text, 0xF1C40F);
                } catch (NumberFormatException ex) {
                    Msg.send(s, "§cNombre invalide.");
                }
            }
            case "stop" -> {
                if (a.length >= 4) Msg.prefixed(s, bm.stopEvent(a[3]) ? "§7Événement arrêté." : "§cAucun événement pour cette statistique.");
                else { bm.stopAllEvents(); Msg.prefixed(s, "§7Tous les événements sont arrêtés."); }
            }
            default -> {
                List<BoostManager.Event> list = bm.events();
                if (list.isEmpty()) { Msg.send(s, "§7Aucun événement de boost en cours."); return; }
                for (BoostManager.Event e : list) {
                    long left = Math.max(0, (e.until() - System.currentTimeMillis()) / 60_000L);
                    Msg.send(s, "§a" + e.stat() + " §7x§f" + e.multiplier() + " §7— reste §f" + left + " min");
                }
            }
        }
    }

    public List<String> complete(String[] a) {
        List<String> out = new ArrayList<>();
        if (a.length == 2) {
            out.addAll(List.of("list", "info"));
            out.addAll(ADMIN_SUBS);
        } else if (a.length == 3) {
            switch (a[1].toLowerCase(Locale.ROOT)) {
                case "event" -> out.addAll(List.of("start", "stop", "list"));
                case "delete", "set", "perm", "effect", "edit" -> plugin.boosts().all().forEach(g -> out.add(g.id()));
                case "info" -> Bukkit.getOnlinePlayers().forEach(p -> out.add(p.getName()));
                default -> { }
            }
        } else if (a.length == 4) {
            switch (a[1].toLowerCase(Locale.ROOT)) {
                case "set" -> out.addAll(BoostManager.STAT_KEYS);
                case "effect" -> out.addAll(List.of("add", "remove"));
                case "event" -> out.addAll(BoostManager.EVENT_KEYS);
                default -> { }
            }
        }
        String prefix = a[a.length - 1].toLowerCase(Locale.ROOT);
        return out.stream().filter(x -> x.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
    }
}
