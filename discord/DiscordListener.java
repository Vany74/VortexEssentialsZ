package fr.vortexsmp.vortexessentials.discord;

import fr.vortexsmp.vortexessentials.VortexEssentials;
import io.papermc.paper.advancement.AdvancementDisplay;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.translation.GlobalTranslator;
import org.bukkit.Bukkit;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Locale;

/** Événements du serveur relayés vers Discord (chaque type se règle dans discord.events). */
public final class DiscordListener implements Listener {
    private final VortexEssentials plugin;

    public DiscordListener(VortexEssentials plugin) {
        this.plugin = plugin;
    }

    private DiscordWebhook d() { return plugin.discord(); }

    private static String plain(Component c) {
        if (c == null) return "";
        return PlainTextComponentSerializer.plainText().serialize(GlobalTranslator.render(c, Locale.US));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void join(PlayerJoinEvent e) {
        if (!d().event("join")) return;
        Player p = e.getPlayer();
        d().player("Arrivée", "**" + p.getName() + "** a rejoint le serveur (" + Bukkit.getOnlinePlayers().size() + " en ligne).", 0x57F287, p);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void quit(PlayerQuitEvent e) {
        if (!d().event("quit")) return;
        Player p = e.getPlayer();
        d().player("Départ", "**" + p.getName() + "** a quitté le serveur (" + Math.max(0, Bukkit.getOnlinePlayers().size() - 1) + " en ligne).", 0xED4245, p);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void death(PlayerDeathEvent e) {
        if (!d().event("death")) return;
        String msg = plain(e.deathMessage());
        d().player("Mort", msg.isBlank() ? "**" + e.getPlayer().getName() + "** est mort." : msg, 0x2C2F33, e.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void advancement(PlayerAdvancementDoneEvent e) {
        if (!d().event("advancement")) return;
        AdvancementDisplay disp = e.getAdvancement().getDisplay();
        if (disp == null || !disp.doesAnnounceToChat() || e.getAdvancement().getKey().getKey().startsWith("recipes/")) return;
        String msg = plain(e.message());
        if (!msg.isBlank()) d().player("Succès", msg, 0xF1C40F, e.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void chat(AsyncChatEvent e) {
        if (!d().event("chat")) return;
        d().chat(e.getPlayer().getName(), PlainTextComponentSerializer.plainText().serialize(e.message()));
    }
}
