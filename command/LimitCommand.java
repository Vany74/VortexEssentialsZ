package fr.vortexsmp.vortexessentials.command;

import fr.vortexsmp.vortexessentials.Msg;
import fr.vortexsmp.vortexessentials.VortexEssentials;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** /vortexlimit (et /vortex limit ...) — gestion des limites et des bypass. */
public final class LimitCommand implements CommandExecutor, TabCompleter {
    private static final List<String> SUBS =
            List.of("menu", "search", "potions", "objects", "bypass", "type", "id", "reload");

    private final VortexEssentials plugin;

    public LimitCommand(VortexEssentials plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender s, Command c, String label, String[] a) {
        run(s, a);
        return true;
    }

    /** Point d'entrée commun à /vortexlimit et /vortex limit. */
    public void run(CommandSender s, String[] a) {
        if (!s.hasPermission("vortexessentials.limit.admin")) {
            Msg.send(s, "§cPermission refusée.");
            return;
        }
        String sub = a.length == 0 ? "menu" : a[0].toLowerCase(Locale.ROOT);

        switch (sub) {
            case "menu" -> player(s, p -> plugin.menu().openLimits(p));
            case "search" -> player(s, p -> plugin.menu().openEnchants(p,
                    a.length > 1 ? String.join(" ", Arrays.copyOfRange(a, 1, a.length)) : ""));
            case "potions" -> player(s, p -> plugin.menu().openPotions(p));
            case "objects" -> player(s, p -> plugin.menu().openObjects(p));
            case "bypass" -> player(s, p -> {
                ItemStack i = p.getInventory().getItemInMainHand();
                if (i.getType() == Material.AIR) { Msg.send(p, "§cTiens un item en main."); return; }
                boolean on = !plugin.bypass().individual(i);
                plugin.bypass().mark(i, on);
                p.getInventory().setItemInMainHand(i);
                Msg.send(p, on ? "§aBypass individuel activé." : "§cBypass individuel retiré.");
            });
            case "type" -> player(s, p -> {
                ItemStack i = p.getInventory().getItemInMainHand();
                if (i.getType() == Material.AIR) { Msg.send(p, "§cTiens un item en main."); return; }
                boolean on = !plugin.bypass().permanentMaterial(i);
                plugin.bypass().setMaterial(i.getType(), on);
                Msg.send(p, on ? "§aBypass permanent du type activé." : "§cBypass permanent du type retiré.");
            });
            case "id" -> player(s, p -> {
                if (a.length < 2) { Msg.send(p, "§e/vortexlimit id <nom>"); return; }
                ItemStack i = p.getInventory().getItemInMainHand();
                if (i.getType() == Material.AIR) { Msg.send(p, "§cTiens un item en main."); return; }
                plugin.bypass().setObjectId(i, a[1]);
                p.getInventory().setItemInMainHand(i);
                plugin.bypass().addObjectId(a[1]);
                Msg.send(p, "§aID objet défini : §f" + a[1]);
            });
            case "reload" -> {
                plugin.reloadAll();
                Msg.send(s, "§aVortexEssentials rechargé.");
            }
            default -> Msg.send(s, "§e/vortexlimit §7| menu | search <texte> | potions | objects | bypass | type | id <nom> | reload");
        }
    }

    private void player(CommandSender s, java.util.function.Consumer<Player> action) {
        if (s instanceof Player p) action.accept(p);
        else Msg.send(s, "§cCommande réservée aux joueurs.");
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String l, String[] a) {
        if (!s.hasPermission("vortexessentials.limit.admin")) return List.of();
        return complete(a);
    }

    /** Complétion partagée ; "a" est la liste d'arguments à partir du sous-commande de limites. */
    public List<String> complete(String[] a) {
        if (a.length == 1) {
            String p = a[0].toLowerCase(Locale.ROOT);
            return SUBS.stream().filter(x -> x.startsWith(p)).toList();
        }
        return List.of();
    }
}
