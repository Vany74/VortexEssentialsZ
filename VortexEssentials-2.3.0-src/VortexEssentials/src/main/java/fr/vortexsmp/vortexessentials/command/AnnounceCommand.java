package fr.vortexsmp.vortexessentials.command;

import fr.vortexsmp.vortexessentials.Msg;
import fr.vortexsmp.vortexessentials.VortexEssentials;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
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

/**
 * /announce [-t] [-b] [-s] <message> (alias /broadcast, /bc) : message dans le chat pour tout le monde.
 * Couleurs &amp; et &amp;#rrggbb. -t = aussi en titre, -b = aussi en barre d'action, -s = avec un son.
 */
public final class AnnounceCommand implements CommandExecutor, TabCompleter {
    private static final LegacyComponentSerializer AMP = LegacyComponentSerializer.builder().character('&').hexColors().build();
    private final VortexEssentials plugin;

    public AnnounceCommand(VortexEssentials plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender s, Command c, String label, String[] a) {
        if (!s.hasPermission("vortexessentials.announce")) { Msg.send(s, "§cPermission refusée."); return true; }
        boolean title = false, bar = false, sound = false;
        int i = 0;
        while (i < a.length && a[i].startsWith("-") && a[i].length() > 1) {
            switch (a[i].toLowerCase(Locale.ROOT)) {
                case "-t", "-title" -> title = true;
                case "-b", "-bar" -> bar = true;
                case "-s", "-sound" -> sound = true;
                default -> { Msg.send(s, "§cOption inconnue : " + a[i]); return true; }
            }
            i++;
        }
        if (i >= a.length) {
            Msg.send(s, "§e/announce [-t] [-b] [-s] <message> §7(-t titre, -b barre d'action, -s son ; couleurs &, &#rrggbb)");
            return true;
        }
        String text = String.join(" ", java.util.Arrays.copyOfRange(a, i, a.length));
        String format = plugin.getConfig().getString("announce.format", "&8[&6Annonce&8] &f{message}");
        Component chat = AMP.deserialize(format.replace("{message}", text));
        Component plain = AMP.deserialize(text);

        Bukkit.broadcast(chat);
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (title) {
                p.showTitle(Title.title(AMP.deserialize(plugin.getConfig().getString("announce.title", "&6&lAnnonce")), plain,
                        Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(4), Duration.ofMillis(600))));
            }
            if (bar) p.sendActionBar(plain);
            if (sound) p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1.2f);
        }
        if (plugin.getConfig().getBoolean("announce.discord", false)) {
            plugin.discord().announce("Annonce", text.replaceAll("&(#[0-9a-fA-F]{6}|[0-9a-fk-orA-FK-OR])", ""), 0xF1C40F);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String l, String[] a) {
        if (a.length == 1) return List.of("-t", "-b", "-s").stream().filter(x -> x.startsWith(a[0].toLowerCase(Locale.ROOT))).toList();
        return List.of();
    }
}
