package fr.vortexsmp.vortexessentials.item;

import fr.vortexsmp.vortexessentials.VortexEssentials;
import fr.vortexsmp.vortexessentials.smp.SmpManager;

import java.util.ArrayList;
import java.util.List;

/** Lignes affichées dans les menus : le craft de l'item est-il illimité ou à quantité limitée ? */
public final class CraftInfo {
    private CraftInfo() {}

    public static List<String> lines(VortexEssentials plugin, VortexItem v) {
        List<String> out = new ArrayList<>();
        if (v.isMythicWeapon()) {
            boolean forged = plugin.mythic().isForged(v);
            String by = forged ? plugin.mythic().forgedBy(v) : null;
            out.add("§c✦ Craft limité : §f1 exemplaire §7sur le serveur");
            out.add(forged ? "§cDéjà forgé" + (by == null ? "" : " par §f" + by) : "§aEncore disponible");
            lock(plugin, out);
        } else if (v.category() == Category.LEGENDARY_WEAPON) {
            var lim = plugin.legendaryLimits();
            int max = lim.maxPerWeapon();
            int left = Math.max(0, max - lim.crafted(v));
            out.add("§6✦ Craft limité : §f" + left + "§7/§f" + max + " §7exemplaire(s) restant(s) sur le serveur");
            out.add("§7Maximum par joueur : §f" + lim.maxPerPlayer() + " §7arme(s) légendaire(s)");
            lock(plugin, out);
        } else if (!plugin.craftLimits().limited("item:" + v.id())) {
            out.add("§a✔ Craft illimité");
        }
        if (plugin.craftLimits().limited("item:" + v.id())) out.addAll(plugin.craftLimits().lines("item:" + v.id()));
        return out;
    }

    private static void lock(VortexEssentials plugin, List<String> out) {
        var smp = plugin.smp();
        if (smp == null || !smp.locking() || smp.weaponsUnlocked()) return;
        out.add(smp.started() ? "§8Craftable dans §f" + SmpManager.dur(smp.remainingMs()) : "§8Verrouillé jusqu'au lancement du SMP");
    }
}
