package fr.vortexsmp.vortexessentials.boost;

import fr.vortexsmp.vortexessentials.VortexEssentials;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.player.PlayerExpChangeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;

import java.util.Set;

import java.util.concurrent.ThreadLocalRandom;

/** Applique les boosts de groupe : XP, drops de mobs, dégâts. (Cooldowns : voir AbilityCooldown.) */
public final class BoostListener implements Listener {
    /** Effets nuisibles : jamais prolongés par un boost. */
    private static final Set<String> HARMFUL = Set.of(
            "slowness", "mining_fatigue", "instant_damage", "nausea", "blindness", "hunger", "weakness",
            "poison", "wither", "levitation", "unluck", "darkness", "bad_omen", "raid_omen", "trial_omen",
            "infested", "oozing", "weaving", "wind_charged");

    private final VortexEssentials plugin;

    public BoostListener(VortexEssentials plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void xp(PlayerExpChangeEvent e) {
        if (e.getAmount() <= 0) return;
        double f = plugin.boosts().multiplier(e.getPlayer(), BoostManager.Stat.XP);
        if (f != 1.0) e.setAmount((int) Math.round(e.getAmount() * f));
    }

    @EventHandler(ignoreCancelled = true)
    public void drops(EntityDeathEvent e) {
        if (e.getEntity() instanceof Player) return;
        Player killer = e.getEntity().getKiller();
        if (killer == null) return;
        double f = plugin.boosts().multiplier(killer, BoostManager.Stat.DROPS);
        if (f <= 1.0) return;

        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        for (ItemStack s : e.getDrops()) {
            double target = s.getAmount() * f;
            int n = (int) Math.floor(target);
            if (rnd.nextDouble() < target - n) n++; // arrondi aléatoire : x1.25 sur 1 item = 25% d'en avoir 2
            s.setAmount(Math.max(1, Math.min(s.getMaxStackSize(), n)));
        }
    }

    /**
     * Prolonge les effets bénéfiques des potions bues, lancées ou des nuages.
     * (Les effets de groupe, Panacea, armes mythiques... sont posés par le plugin et ne sont pas concernés.)
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void potionDuration(EntityPotionEffectEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        switch (e.getCause()) {
            case POTION_DRINK, POTION_SPLASH, AREA_EFFECT_CLOUD -> { }
            default -> { return; }
        }
        PotionEffect ne = e.getNewEffect();
        if (ne == null || ne.getDuration() <= 1) return; // retrait, instantané ou infini
        if (HARMFUL.contains(ne.getType().getKey().getKey())) return;

        double f = plugin.boosts().multiplier(p, BoostManager.Stat.POTION);
        if (f <= 1.0) return;

        e.setCancelled(true);
        int duration = (int) Math.min(Integer.MAX_VALUE / 4L, Math.round(ne.getDuration() * f));
        // Cause PLUGIN : l'événement n'est plus traité (pas de boucle)
        p.addPotionEffect(new PotionEffect(ne.getType(), duration, ne.getAmplifier(),
                ne.isAmbient(), ne.hasParticles(), ne.hasIcon()));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void damage(EntityDamageByEntityEvent e) {
        Player p = null;
        if (e.getDamager() instanceof Player d) p = d;
        else if (e.getDamager() instanceof Projectile pr && pr.getShooter() instanceof Player s) p = s;
        if (p == null) return;

        double f = plugin.boosts().multiplier(p, BoostManager.Stat.DAMAGE);
        if (f != 1.0) e.setDamage(e.getDamage() * f);
    }
}
