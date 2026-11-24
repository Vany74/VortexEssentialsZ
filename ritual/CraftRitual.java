package fr.vortexsmp.vortexessentials.ritual;

import fr.vortexsmp.vortexessentials.Msg;
import fr.vortexsmp.vortexessentials.VortexEssentials;
import fr.vortexsmp.vortexessentials.item.Category;
import fr.vortexsmp.vortexessentials.item.ItemUtil;
import fr.vortexsmp.vortexessentials.item.VortexItem;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.time.Duration;

/**
 * Rituel déclenché au craft d'une arme légendaire (or) ou mythique (rouge) : foudres, spirale de particules,
 * titre et son pour tous les joueurs, lueur du forgeron. Réglages : rituals.* dans config.yml.
 */
public final class CraftRitual implements Listener {
    private final VortexEssentials plugin;

    public CraftRitual(VortexEssentials plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void craft(CraftItemEvent e) {
        if (!plugin.getConfig().getBoolean("rituals.enabled", true)) return;
        VortexItem v = ItemUtil.get(e.getCurrentItem());
        if (v == null || !(v.isMythicWeapon() || v.category() == Category.LEGENDARY_WEAPON)) return;
        if (!(e.getWhoClicked() instanceof Player p)) return;
        boolean mythic = v.isMythicWeapon();
        Bukkit.getScheduler().runTask(plugin, () -> ritual(p, v, mythic));
    }

    private void ritual(Player p, VortexItem v, boolean mythic) {
        if (!p.isOnline()) return;
        World w = p.getWorld();
        Location c = p.getLocation();
        String color = mythic ? "§4" : "§6";
        String kind = v.isMythicArmor() ? "ARMURE MYTHIQUE" : mythic ? "ARME MYTHIQUE" : "ARME LÉGENDAIRE";

        if (plugin.getConfig().getBoolean("rituals.broadcast-title", true)) {
            Title title = Title.title(Msg.c(color + "§l✦ " + kind + " ✦"), Msg.c("§f" + v.displayName() + " §7forgé par §f" + p.getName()),
                    Title.Times.times(Duration.ofMillis(400), Duration.ofSeconds(4), Duration.ofSeconds(1)));
            for (Player o : Bukkit.getOnlinePlayers()) {
                o.showTitle(title);
                o.playSound(o.getLocation(), mythic ? Sound.ENTITY_WITHER_SPAWN : Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, mythic ? 0.8f : 1f);
            }
        }

        if (plugin.getConfig().getBoolean("rituals.lightning-effect", true)) {
            int strikes = mythic ? (int) plugin.getConfig().getDouble("rituals.mythic-strikes", 3) : 1;
            for (int i = 0; i < strikes; i++) {
                Bukkit.getScheduler().runTaskLater(plugin, () -> w.strikeLightningEffect(c), i * 12L);
            }
        }

        new BukkitRunnable() { // spirale ascendante autour du forgeron
            int t = 0;

            @Override
            public void run() {
                if (t >= 60 || !p.isOnline()) { cancel(); return; }
                Location base = p.getLocation();
                for (int k = 0; k < 2; k++) {
                    double a = t * 0.5 + k * Math.PI;
                    Location l = base.clone().add(Math.cos(a) * 1.6, t * 0.05, Math.sin(a) * 1.6);
                    w.spawnParticle(mythic ? Particle.SOUL_FIRE_FLAME : Particle.FLAME, l, 2, 0, 0, 0, 0.01);
                    w.spawnParticle(Particle.END_ROD, l, 1, 0, 0, 0, 0);
                }
                t++;
            }
        }.runTaskTimer(plugin, 0L, 1L);

        int glow = (int) plugin.getConfig().getDouble("rituals.glow-seconds", 10);
        if (glow > 0) p.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, glow * 20, 0, true, false, false));
    }
}
