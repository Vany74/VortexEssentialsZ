package fr.vortexsmp.vortexessentials.mythic;

import fr.vortexsmp.vortexessentials.Msg;
import fr.vortexsmp.vortexessentials.VortexEssentials;
import fr.vortexsmp.vortexessentials.item.ItemUtil;
import fr.vortexsmp.vortexessentials.item.VortexItem;
import fr.vortexsmp.vortexessentials.util.AbilityCooldown;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.entity.Villager;
import org.bukkit.entity.WanderingTrader;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Stormpiercer (lance mythique, base netherite spear).
 * Passif : brise les boucliers ; tous les N coups, la foudre jaillit en chaîne autour de la cible.
 * Thunder Javelin (sneak + clic droit) : javelot de foudre qui traverse tous les ennemis alignés.
 * Skewer Dash (touche d'échange de main) : charge fulgurante qui empale et projette tout sur le passage.
 * Réglages : mythic-weapons.stormpiercer dans config.yml.
 */
public final class SpearListener implements Listener {
    private final VortexEssentials plugin;
    private final Map<UUID, Integer> hits = new HashMap<>();

    public SpearListener(VortexEssentials plugin) {
        this.plugin = plugin;
    }

    private double d(String path, double def) { return plugin.getConfig().getDouble("mythic-weapons.stormpiercer." + path, def); }

    private static boolean isSpear(ItemStack s) {
        return s != null && s.getType() == Material.NETHERITE_SPEAR && ItemUtil.get(s) == VortexItem.STORMPIERCER;
    }

    private boolean validTarget(Player p, LivingEntity t) {
        if (t.equals(p) || t instanceof ArmorStand || t instanceof Villager || t instanceof WanderingTrader) return false;
        if (t instanceof Tameable ta && ta.isTamed() && p.equals(ta.getOwner())) return false;
        return !(t instanceof Player o && (o.getGameMode() == GameMode.CREATIVE || o.getGameMode() == GameMode.SPECTATOR));
    }

    private static PotionEffectType eff(String key) { return PotionEffectType.getByKey(NamespacedKey.minecraft(key)); }

    // ═════════════ Passif : brise-bouclier et foudre en chaîne ═════════════

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void hit(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player p) || !isSpear(p.getInventory().getItemInMainHand())) return;
        if (!(e.getEntity() instanceof LivingEntity victim)) return;
        if (victim instanceof Player vp && vp.isBlocking()) {
            vp.setCooldown(Material.SHIELD, 100);
            vp.clearActiveItem();
            vp.getWorld().playSound(vp.getLocation(), Sound.ITEM_SHIELD_BREAK, 1f, 0.9f);
        }
        int every = (int) d("static-charge.every", 4);
        if (every <= 0) return;
        int n = hits.merge(p.getUniqueId(), 1, Integer::sum);
        if (n % every != 0) return;
        double radius = d("static-charge.radius", 5), dmg = d("static-charge.damage", 8);
        World w = victim.getWorld();
        w.strikeLightningEffect(victim.getLocation());
        for (Entity en : victim.getNearbyEntities(radius, radius, radius)) {
            if (!(en instanceof LivingEntity t) || !validTarget(p, t) || t.equals(victim)) continue;
            t.damage(dmg, p);
            Location a = victim.getLocation().add(0, 1, 0), b = t.getLocation().add(0, 1, 0);
            Vector line = b.toVector().subtract(a.toVector());
            double len = line.length();
            for (double s = 0; s < len; s += 0.4) w.spawnParticle(Particle.ELECTRIC_SPARK, a.clone().add(line.clone().normalize().multiply(s)), 1, 0.05, 0.05, 0.05, 0);
        }
        w.spawnParticle(Particle.ELECTRIC_SPARK, victim.getLocation().add(0, 1, 0), 30, 0.5, 0.7, 0.5, 0.3);
        p.sendActionBar(Msg.c("§bFoudre en chaîne !"));
    }

    // ═════════════ Thunder Javelin ═════════════

    @EventHandler
    public void interact(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        Player p = e.getPlayer();
        if (!p.isSneaking() || !isSpear(p.getInventory().getItemInMainHand())) return;
        Action a = e.getAction();
        if (a != Action.RIGHT_CLICK_AIR && a != Action.RIGHT_CLICK_BLOCK) return;
        e.setCancelled(true);
        javelin(p);
    }

    private void javelin(Player p) {
        if (!AbilityCooldown.use(plugin, p, "storm_javelin", "Thunder Javelin", d("javelin.cooldown", 14))) return;
        double range = d("javelin.range", 40), dmg = d("javelin.damage", 14), bolt = d("javelin.lightning-damage", 6);
        World w = p.getWorld();
        Location eye = p.getEyeLocation();
        Vector dir = eye.getDirection().normalize();
        RayTraceResult blocks = w.rayTraceBlocks(eye, dir, range, FluidCollisionMode.NEVER, true);
        double reach = blocks == null ? range : Math.max(1, blocks.getHitPosition().distance(eye.toVector()));

        Set<LivingEntity> targets = new LinkedHashSet<>();
        for (double s = 1; s <= reach; s += 1.0) {
            Location pt = eye.clone().add(dir.clone().multiply(s));
            for (Entity en : w.getNearbyEntities(pt, 1.4, 1.4, 1.4)) {
                if (en instanceof LivingEntity t && validTarget(p, t)) targets.add(t);
            }
        }
        // visuel : lance de foudre en hélice
        Vector up = Math.abs(dir.getY()) > 0.97 ? new Vector(1, 0, 0) : new Vector(0, 1, 0);
        Vector u = dir.clone().crossProduct(up).normalize();
        Vector v = dir.clone().crossProduct(u).normalize();
        for (double s = 0.6; s <= reach; s += 0.35) {
            Location pt = eye.clone().add(dir.clone().multiply(s)).add(0, -0.25, 0);
            double th = s * 2.6;
            Location hl = pt.clone().add(u.clone().multiply(Math.cos(th) * 0.25)).add(v.clone().multiply(Math.sin(th) * 0.25));
            w.spawnParticle(Particle.ELECTRIC_SPARK, hl, 1, 0, 0, 0, 0);
            w.spawnParticle(Particle.END_ROD, pt, 1, 0.02, 0.02, 0.02, 0);
        }
        w.playSound(p.getLocation(), Sound.ITEM_TRIDENT_THROW, 1.2f, 0.7f);
        w.playSound(p.getLocation(), Sound.ITEM_TRIDENT_THUNDER, 1.2f, 1.2f);
        PotionEffectType slow = eff("slowness");
        for (LivingEntity t : targets) {
            w.strikeLightningEffect(t.getLocation());
            t.damage(dmg, p);
            t.damage(bolt, p);
            t.setFireTicks(60);
            t.setVelocity(new Vector(0, 0.6, 0).add(dir.clone().multiply(0.4)));
            if (slow != null) t.addPotionEffect(new PotionEffect(slow, 40, 2));
        }
        p.sendActionBar(Msg.c("§bThunder Javelin ! §7(" + targets.size() + " cible(s))"));
    }

    // ═════════════ Skewer Dash ═════════════

    @EventHandler
    public void swap(PlayerSwapHandItemsEvent e) {
        Player p = e.getPlayer();
        if (!isSpear(p.getInventory().getItemInMainHand()) && !isSpear(p.getInventory().getItemInOffHand())) return;
        e.setCancelled(true);
        dash(p);
    }

    private void dash(Player p) {
        if (!AbilityCooldown.use(plugin, p, "storm_dash", "Skewer Dash", d("dash.cooldown", 18))) return;
        double speed = d("dash.speed", 2.4), dmg = d("dash.damage", 12);
        Vector dir = p.getLocation().getDirection().normalize();
        p.setVelocity(dir.clone().multiply(speed).setY(Math.max(0.1, dir.getY() * speed)));
        PotionEffectType res = eff("resistance");
        if (res != null) p.addPotionEffect(new PotionEffect(res, 30, 2));
        World w = p.getWorld();
        w.playSound(p.getLocation(), Sound.ITEM_TRIDENT_RIPTIDE_3, 1.2f, 1f);
        p.sendActionBar(Msg.c("§bSkewer Dash !"));
        final Set<UUID> struck = new HashSet<>();
        new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                if (!p.isOnline() || p.isDead() || t > 10) {
                    cancel();
                    if (p.isOnline()) {
                        Location c = p.getLocation();
                        w.spawnParticle(Particle.ELECTRIC_SPARK, c.clone().add(0, 1, 0), 40, 1, 0.8, 1, 0.3);
                        w.playSound(c, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 0.8f, 1.4f);
                    }
                    return;
                }
                Location loc = p.getLocation().add(0, 1, 0);
                w.spawnParticle(Particle.ELECTRIC_SPARK, loc, 6, 0.3, 0.4, 0.3, 0.1);
                w.spawnParticle(Particle.CRIT, loc, 4, 0.3, 0.4, 0.3, 0.2);
                for (Entity en : p.getNearbyEntities(2.2, 2, 2.2)) {
                    if (!(en instanceof LivingEntity target) || !validTarget(p, target) || !struck.add(target.getUniqueId())) continue;
                    target.damage(dmg, p);
                    target.setVelocity(new Vector(0, 0.9, 0).add(dir.clone().multiply(0.5)));
                    PotionEffectType glow = eff("glowing");
                    if (glow != null) target.addPotionEffect(new PotionEffect(glow, 60, 0));
                    w.playSound(target.getLocation(), Sound.ITEM_TRIDENT_HIT, 1f, 0.8f);
                    w.strikeLightningEffect(target.getLocation());
                }
                t++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    @EventHandler
    public void quit(PlayerQuitEvent e) {
        hits.remove(e.getPlayer().getUniqueId());
    }
}
