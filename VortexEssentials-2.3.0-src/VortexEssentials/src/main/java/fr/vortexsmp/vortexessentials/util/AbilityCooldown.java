package fr.vortexsmp.vortexessentials.util;

import fr.vortexsmp.vortexessentials.Msg;
import fr.vortexsmp.vortexessentials.VortexEssentials;
import org.bukkit.entity.Player;

/** Cooldown d'une capacité, avec réduction de boost de groupe et message dans l'action bar. */
public final class AbilityCooldown {
    private AbilityCooldown() {}

    public static boolean use(VortexEssentials plugin, Player p, String key, String label, double seconds) {
        long ms = plugin.boosts().cooldownMs(p, Math.round(seconds * 1000));
        if (plugin.cooldowns().tryUse(p.getUniqueId(), key, ms)) return true;
        long left = plugin.cooldowns().remainingMs(p.getUniqueId(), key);
        p.sendActionBar(Msg.c("§c" + label + " §7: §f" + (int) Math.ceil(left / 1000.0) + "s"));
        return false;
    }
}
