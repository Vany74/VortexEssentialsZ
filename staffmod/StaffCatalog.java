package fr.vortexsmp.vortexessentials.staffmod;

import org.bukkit.Material;

import java.util.List;

/** Catalogue des commandes de gestion du serveur qu'un palier de staff peut débloquer. */
public final class StaffCatalog {
    private StaffCatalog() {}

    public record Entry(String key, String name, Material icon, List<String> nodes) {}

    private static Entry e(String key, String name, Material icon, String... nodes) { return new Entry(key, name, icon, List.of(nodes)); }

    public static final List<Entry> ENTRIES = List.of(
            e("jail", "Prison (/jail, /unjail, /setjail)", Material.IRON_BARS, "vortexessentials.staff.jail"),
            e("freeze", "Gel (/freeze)", Material.PACKED_ICE, "vortexessentials.staff.freeze"),
            e("xray", "X-ray staff (/staffxray)", Material.DIAMOND_ORE, "vortexessentials.staff.xray"),
            e("announce", "Annonces (/announce)", Material.BELL, "vortexessentials.announce"),
            e("maintenance", "Maintenance (/maintenance)", Material.BARRIER, "vortexessentials.maintenance", "vortexessentials.maintenance.bypass"),
            e("smp", "SMP (/smp start|stop)", Material.CLOCK, "vortexessentials.smp"),
            e("servermenu", "Menu admin du serveur (/servermenu)", Material.COMMAND_BLOCK, "vortexessentials.servermenu", "vortexessentials.dimension.bypass"),
            e("orders", "Commandes du serveur (/orders server)", Material.WRITABLE_BOOK, "vortexessentials.orders.admin"),
            e("shop", "Édition de la boutique (/shop add...)", Material.EMERALD, "vortexessentials.shop.admin"),
            e("spawners", "Spawners (/us give, /us log)", Material.SPAWNER, "ultimatespawners.admin"),
            e("bounty", "Primes (administration)", Material.SKELETON_SKULL, "vortexessentials.bounty.admin"),
            e("admin", "Administration Vortex (/vortex admin...)", Material.NETHER_STAR, "vortexessentials.admin", "vortexessentials.admin.bypass"),
            e("bypass", "Ignorer prison et gel", Material.SHIELD, "vortexessentials.staff.bypass"));

    public static Entry get(String key) {
        for (Entry e : ENTRIES) if (e.key().equals(key)) return e;
        return null;
    }
}
