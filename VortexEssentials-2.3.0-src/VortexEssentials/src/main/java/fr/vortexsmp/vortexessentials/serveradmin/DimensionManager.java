package fr.vortexsmp.vortexessentials.serveradmin;

import fr.vortexsmp.vortexessentials.Msg;
import fr.vortexsmp.vortexessentials.VortexEssentials;
import fr.vortexsmp.vortexessentials.YamlStore;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerTeleportEvent;

/** Active / désactive le Nether et l'End (dimensions.yml). Permission d'ignorer : vortexessentials.dimension.bypass. */
public final class DimensionManager implements Listener {
    private final VortexEssentials plugin;
    private final YamlStore store;

    public DimensionManager(VortexEssentials plugin) {
        this.plugin = plugin;
        this.store = new YamlStore(plugin, "dimensions.yml");
    }

    public boolean netherEnabled() { return store.get().getBoolean("nether", true); }
    public boolean endEnabled() { return store.get().getBoolean("end", true); }

    public void setNether(boolean on) { store.get().set("nether", on); store.save(); if (!on) eject(World.Environment.NETHER); }
    public void setEnd(boolean on) { store.get().set("end", on); store.save(); if (!on) eject(World.Environment.THE_END); }

    private boolean disabled(World.Environment env) {
        return (env == World.Environment.NETHER && !netherEnabled()) || (env == World.Environment.THE_END && !endEnabled());
    }

    private void eject(World.Environment env) {
        if (!plugin.getConfig().getBoolean("dimensions.eject-on-disable", true)) return;
        World main = Bukkit.getWorlds().get(0);
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getWorld().getEnvironment() == env && !p.hasPermission("vortexessentials.dimension.bypass")) {
                p.teleport(main.getSpawnLocation());
                p.sendMessage(Msg.c("§cCette dimension vient d'être désactivée."));
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void teleport(PlayerTeleportEvent e) {
        if (e.getTo() == null || e.getTo().getWorld() == null) return;
        World.Environment env = e.getTo().getWorld().getEnvironment();
        if (!disabled(env) || e.getPlayer().hasPermission("vortexessentials.dimension.bypass")) return;
        e.setCancelled(true);
        e.getPlayer().sendActionBar(Msg.c("§cCette dimension est désactivée pour le moment."));
    }
}
