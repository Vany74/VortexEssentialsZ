package fr.vortexsmp.vortexessentials.spawner;

import fr.vortexsmp.vortexessentials.Msg;
import fr.vortexsmp.vortexessentials.VortexEssentials;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** /uspawner help | shop | give <joueur> <mob> [quantité] | reload | trust <joueur> | untrust <joueur> | info | log [n] (alias /us, /spawners). */
public final class SpawnerCommand implements CommandExecutor, TabCompleter {
    private final VortexEssentials plugin;

    public SpawnerCommand(VortexEssentials plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender s, Command c, String label, String[] a) {
        String sub = a.length == 0 ? "help" : a[0].toLowerCase(Locale.ROOT);
        boolean admin = s.hasPermission("ultimatespawners.admin");
        switch (sub) {
            case "shop" -> {
                if (!(s instanceof Player p)) { Msg.send(s, "§cCommande réservée aux joueurs."); return true; }
                if (!p.hasPermission("ultimatespawners.shop") || !plugin.getConfig().getBoolean("spawners.shop.enabled", true)) { Msg.send(s, "§cBoutique indisponible."); return true; }
                plugin.spawnerMenus().shop(p);
            }
            case "give" -> {
                if (!admin) { Msg.send(s, "§cPermission refusée."); return true; }
                if (a.length < 3) { Msg.send(s, "§e/uspawner give <joueur> <MOB> [quantité]"); return true; }
                Player t = Bukkit.getPlayerExact(a[1]);
                if (t == null) { Msg.send(s, "§cJoueur introuvable."); return true; }
                try {
                    EntityType type = EntityType.valueOf(a[2].toUpperCase(Locale.ROOT));
                    int qty = a.length > 3 ? Math.max(1, Math.min(2304, Integer.parseInt(a[3]))) : 1;
                    t.getInventory().addItem(plugin.spawners().item(type, qty)).values().forEach(r -> t.getWorld().dropItemNaturally(t.getLocation(), r));
                    plugin.spawners().audit(s.getName() + " donne " + qty + " " + type.name() + " à " + t.getName());
                    Msg.prefixed(s, "§a" + qty + "× " + SpawnerManager.pretty(type) + " donné(s) à §f" + t.getName());
                } catch (IllegalArgumentException e) {
                    Msg.send(s, "§cMob ou quantité invalide (ex. ZOMBIE).");
                }
            }
            case "reload" -> {
                if (!admin) { Msg.send(s, "§cPermission refusée."); return true; }
                plugin.reloadConfig();
                Msg.prefixed(s, "§aConfiguration des spawners rechargée.");
            }
            case "trust", "untrust" -> trust(s, a, sub.equals("trust"));
            case "info" -> info(s);
            case "log" -> {
                if (!admin) { Msg.send(s, "§cPermission refusée."); return true; }
                int n = 15;
                try { if (a.length > 1) n = Math.max(1, Math.min(50, Integer.parseInt(a[1]))); } catch (NumberFormatException ignored) { }
                List<String> lines = plugin.spawners().tail(n);
                if (lines.isEmpty()) Msg.send(s, "§7Aucune action enregistrée aujourd'hui.");
                for (String l : lines) s.sendMessage(Msg.c("§8" + l));
            }
            default -> {
                Msg.send(s, "§e§lUltimateSpawners §7— /us");
                s.sendMessage(Msg.c("§e/us shop §7: boutique · §e/us info §7: spawner visé · §e/us trust|untrust <joueur> §7: joueurs autorisés"));
                if (admin) s.sendMessage(Msg.c("§e/us give <joueur> <MOB> [quantité] §7· §e/us reload §7· §e/us log [n]"));
            }
        }
        return true;
    }

    private SpawnerData target(Player p) {
        Block b = p.getTargetBlockExact(6);
        return b == null ? null : plugin.spawners().at(b);
    }

    private void trust(CommandSender s, String[] a, boolean add) {
        if (!(s instanceof Player p)) { Msg.send(s, "§cCommande réservée aux joueurs."); return; }
        SpawnerData d = target(p);
        if (d == null) { Msg.send(p, "§cRegardez un de vos spawners (à moins de 6 blocs)."); return; }
        if (!p.getUniqueId().equals(d.owner) && !p.hasPermission("ultimatespawners.admin")) { Msg.send(p, "§cSeul le propriétaire peut gérer les joueurs autorisés."); return; }
        if (a.length < 2) { Msg.send(p, "§e/us " + (add ? "trust" : "untrust") + " <joueur>"); return; }
        OfflinePlayer t = Bukkit.getOfflinePlayer(a[1]);
        if (add) d.trusted.add(t.getUniqueId()); else d.trusted.remove(t.getUniqueId());
        plugin.spawners().markDirty();
        plugin.spawners().audit(p.getName() + (add ? " autorise " : " retire ") + a[1] + " sur " + d.key);
        Msg.prefixed(p, add ? "§a" + a[1] + " peut maintenant utiliser ce spawner." : "§7" + a[1] + " n'a plus accès à ce spawner.");
    }

    private void info(CommandSender s) {
        if (!(s instanceof Player p)) { Msg.send(s, "§cCommande réservée aux joueurs."); return; }
        SpawnerData d = target(p);
        if (d == null) { Msg.send(p, "§cRegardez un spawner (à moins de 6 blocs)."); return; }
        Msg.send(p, "§e" + SpawnerManager.pretty(d.type) + " Spawner §7— pile §f" + d.stack + " §7— propriétaire §f" + d.ownerName);
        p.sendMessage(Msg.c("§7Niveaux : vitesse §f" + d.speed + " §7portée §f" + d.range + " §7quantité §f" + d.qty + " §7— stocké §f" + d.storedItems() + " §7items, §f" + d.exp + " §7xp"));
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String l, String[] a) {
        List<String> out = new ArrayList<>();
        if (a.length == 1) {
            out.addAll(List.of("shop", "info", "trust", "untrust", "help"));
            if (s.hasPermission("ultimatespawners.admin")) out.addAll(List.of("give", "reload", "log"));
        } else if (a.length == 2 && List.of("give", "trust", "untrust").contains(a[0].toLowerCase(Locale.ROOT))) {
            Bukkit.getOnlinePlayers().forEach(p -> out.add(p.getName()));
        } else if (a.length == 3 && a[0].equalsIgnoreCase("give")) {
            for (EntityType t : EntityType.values()) if (t.isSpawnable() && t.isAlive()) out.add(t.name());
        }
        String prefix = a[a.length - 1].toLowerCase(Locale.ROOT);
        return out.stream().filter(x -> x.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
    }
}
