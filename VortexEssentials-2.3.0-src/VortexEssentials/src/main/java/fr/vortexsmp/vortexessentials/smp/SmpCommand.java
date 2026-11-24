package fr.vortexsmp.vortexessentials.smp;

import fr.vortexsmp.vortexessentials.Msg;
import fr.vortexsmp.vortexessentials.VortexEssentials;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.List;
import java.util.Locale;

/** /smp start | stop | status | delay <heures> */
public final class SmpCommand implements CommandExecutor, TabCompleter {
    private final VortexEssentials plugin;

    public SmpCommand(VortexEssentials plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender s, Command c, String label, String[] a) {
        if (!s.hasPermission("vortexessentials.smp")) { Msg.send(s, "§cPermission refusée."); return true; }
        SmpManager smp = plugin.smp();
        String sub = a.length == 0 ? "status" : a[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "start" -> {
                if (smp.started()) { Msg.send(s, "§cLe SMP est déjà lancé (/smp stop pour l'arrêter)."); return true; }
                smp.start();
                if (plugin.getConfig().getBoolean("smp.disable-maintenance-on-start", true)) plugin.maintenance().setEnabled(false);
                Bukkit.broadcast(Msg.c("§8[§aSMP§8] §a§lLe SMP commence ! §7Les armes légendaires et mythiques seront craftables dans §f"
                        + SmpManager.dur(smp.remainingMs()) + "§7."));
                for (Player p : Bukkit.getOnlinePlayers()) {
                    p.showTitle(Title.title(Msg.c("§a§lLE SMP COMMENCE !"), Msg.c("§7Bonne chance à tous"),
                            Title.Times.times(Duration.ofMillis(400), Duration.ofSeconds(4), Duration.ofSeconds(1))));
                    p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.7f, 1f);
                }
                run("smp.start-commands");
                if (plugin.discord() != null) plugin.discord().announce("Le SMP commence !", "Les armes légendaires et mythiques seront craftables dans " + SmpManager.dur(smp.remainingMs()) + ".", 0x2ECC71);
            }
            case "stop" -> {
                if (!smp.started()) { Msg.send(s, "§cLe SMP n'est pas lancé."); return true; }
                smp.stop();
                if (plugin.getConfig().getBoolean("smp.maintenance-on-stop", false)) plugin.maintenance().setEnabled(true);
                Bukkit.broadcast(Msg.c("§8[§cSMP§8] §c§lLe SMP est arrêté."));
                run("smp.stop-commands");
            }
            case "newseason" -> {
                if (a.length < 2 || !a[1].equalsIgnoreCase("confirm")) {
                    Msg.send(s, "§cNouvelle saison : arrête le SMP et remet à zéro les armes mythiques forgées, les compteurs des armes légendaires et des maces. Refaites la commande avec §f/smp newseason confirm§c.");
                    return true;
                }
                if (smp.started()) smp.stop();
                for (fr.vortexsmp.vortexessentials.item.VortexItem v : fr.vortexsmp.vortexessentials.item.VortexItem.mythics()) {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "vortex mythic reset " + v.id());
                }
                plugin.legendaryLimits().resetAll();
                plugin.maceLimits().resetAll();
                Msg.prefixed(s, "§aNouvelle saison prête : armes mythiques, légendaires et maces remises à zéro. Lancez le SMP avec §f/smp start§a.");
            }
            case "delay" -> {
                if (a.length < 2) { Msg.send(s, "§e/smp delay <heures> §7(délai avant le craft des armes, actuellement §f" + smp.delayHours() + "h§7)"); return true; }
                try {
                    smp.setDelay(Double.parseDouble(a[1].replace(',', '.')));
                    Msg.prefixed(s, "§7Délai de craft des armes : §f" + smp.delayHours() + " h§7 après le lancement.");
                } catch (NumberFormatException e) {
                    Msg.send(s, "§cNombre invalide.");
                }
            }
            default -> {
                Msg.send(s, "§6§lSMP §7— " + (smp.started() ? "§alancé" : "§cnon lancé"));
                if (smp.started()) {
                    long ago = System.currentTimeMillis() - smp.startedAt();
                    s.sendMessage(Msg.c("§7Lancé il y a §f" + SmpManager.dur(ago)));
                    s.sendMessage(Msg.c(smp.weaponsUnlocked() ? "§7Armes légendaires et mythiques : §acraftables (avec rituel)"
                            : "§7Armes légendaires et mythiques : craftables dans §f" + SmpManager.dur(smp.remainingMs())));
                } else {
                    s.sendMessage(Msg.c("§7Armes légendaires et mythiques : §cverrouillées jusqu'au lancement + " + smp.delayHours() + " h"));
                }
            }
        }
        return true;
    }

    private void run(String path) {
        for (String cmd : plugin.getConfig().getStringList(path)) Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String l, String[] a) {
        if (a.length == 1) return List.of("start", "stop", "status", "delay", "newseason").stream().filter(x -> x.startsWith(a[0].toLowerCase(Locale.ROOT))).toList();
        return List.of();
    }
}
