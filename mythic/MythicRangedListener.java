package fr.vortexsmp.vortexessentials.mythic;

import fr.vortexsmp.vortexessentials.Msg;
import fr.vortexsmp.vortexessentials.VortexEssentials;
import fr.vortexsmp.vortexessentials.item.ItemUtil;
import fr.vortexsmp.vortexessentials.item.VortexItem;
import fr.vortexsmp.vortexessentials.util.AbilityCooldown;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Arrow;
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
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Armes mythiques à distance.
 *  - Skyfall (arc) : flèches pleine puissance perforantes et plus rapides ; Arrow Rain (sneak + clic gauche).
 *  - Voidrender (arbalète) : carreaux perforants ; Rail Shot (sneak + clic gauche) ; Blink Bolt (tirer en sneak).
 * Réglages : mythic-weapons.skyfall / mythic-weapons.voidrender dans config.yml.
 */
public final class MythicRangedListener implements Listener {
    private final VortexEssentials plugin;
    private final NamespacedKey blinkKey;
    private final NamespacedKey rainKey;

    public MythicRangedListener(VortexEssentials plugin) {
        this.plugin = plugin;
        this.blinkKey = new NamespacedKey(plugin, "blink_bolt");
        this.rainKey = new NamespacedKey(plugin, "arrow_rain");
    }

    private double d(String path, double def) { return plugin.getConfig().getDouble("mythic-weapons." + path, def); }

    private VortexItem held(Player p) {
        ItemStack s = p.getInventory().getItemInMainHand();
        if (!MythicEffectsTask.relevant(s.getType())) return null;
        return ItemUtil.get(s);
    }

    private boolean validTarget(Player p, LivingEntity t) {
        if (t.equals(p) || t instanceof ArmorStand || t instanceof Villager || t instanceof WanderingTrader) return false;
        if (t instanceof Tameable ta && ta.isTamed() && p.equals(ta.getOwner())) return false;
        return !(t instanceof Player o && (o.getGameMode() == GameMode.CREATIVE || o.getGameMode() == GameMode.SPECTATOR));
    }

    // ═════════════ Tirs ═════════════

    @EventHandler(ignoreCancelled = true)
    public void shoot(EntityShootBowEvent e) {
        if (!(e.getEntity() instanceof Player p) || !(e.getProjectile() instanceof AbstractArrow a)) return;
        VortexItem v = ItemUtil.get(e.getBow());
        if (v == VortexItem.SKYFALL) {
            if (e.getForce() >= 0.95f) { // arc tendu à fond
                a.setPierceLevel((int) d("skyfall.pierce", 3));
                a.setCritical(true);
                a.setVelocity(a.getVelocity().multiply(d("skyfall.speed-multiplier", 1.25)));
            }
        } else if (v == VortexItem.VOIDRENDER) {
            a.setPierceLevel((int) d("voidrender.pierce", 5));
            a.setVelocity(a.getVelocity().multiply(d("voidrender.speed-multiplier", 1.15)));
            if (p.isSneaking() && AbilityCooldown.use(plugin, p, "voidrender_blink", "Blink Bolt", d("voidrender.blink.cooldown", 20))) {
                a.getPersistentDataContainer().set(blinkKey, PersistentDataType.BYTE, (byte) 1);
                p.sendActionBar(Msg.c("§5Blink Bolt prêt !"));
            }
        }
    }

    /** Blink Bolt : le tireur se téléporte à l'impact. */
    @EventHandler(ignoreCancelled = true)
    public void blink(ProjectileHitEvent e) {
        if (!(e.getEntity() instanceof AbstractArrow a)) return;
        Byte b = a.getPersistentDataContainer().get(blinkKey, PersistentDataType.BYTE);
        if (b == null || b != 1) return;
        a.getPersistentDataContainer().remove(blinkKey);
        if (!(a.getShooter() instanceof Player p) || !p.isOnline()) return;

        Location dest;
        if (e.getHitBlock() != null) {
            BlockFace face = e.getHitBlockFace() == null ? BlockFace.UP : e.getHitBlockFace();
            dest = e.getHitBlock().getRelative(face).getLocation().add(0.5, 0, 0.5);
        } else if (e.getHitEntity() != null) {
            dest = e.getHitEntity().getLocation();
        } else {
            dest = a.getLocation();
        }
        for (int i = 0; i < 4; i++) { // cherche 2 blocs d'air libres
            if (dest.getBlock().isPassable() && dest.clone().add(0, 1, 0).getBlock().isPassable()) break;
            dest.add(0, 1, 0);
        }
        dest.setYaw(p.getLocation().getYaw());
        dest.setPitch(p.getLocation().getPitch());
        p.getWorld().spawnParticle(Particle.PORTAL, p.getLocation().add(0, 1, 0), 30, 0.3, 0.5, 0.3, 0.5);
        p.teleport(dest);
        p.setFallDistance(0);
        dest.getWorld().spawnParticle(Particle.REVERSE_PORTAL, dest.clone().add(0, 1, 0), 30, 0.3, 0.5, 0.3, 0.1);
        dest.getWorld().playSound(dest, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1f);
        a.remove();
    }

    /** Les flèches de l'Arrow Rain ne blessent pas leur propriétaire. */
    @EventHandler(ignoreCancelled = true)
    public void rainSafe(EntityDamageByEntityEvent e) {
        if (e.getDamager() instanceof AbstractArrow a && a.getPersistentDataContainer().has(rainKey, PersistentDataType.BYTE)
                && e.getEntity().equals(a.getShooter())) {
            e.setCancelled(true);
        }
    }

    // ═════════════ Capacités (sneak + clic gauche) ═════════════

    @EventHandler(priority = EventPriority.NORMAL)
    public void interact(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        Action a = e.getAction();
        if (a != Action.LEFT_CLICK_AIR && a != Action.LEFT_CLICK_BLOCK) return;
        Player p = e.getPlayer();
        if (!p.isSneaking()) return;
        VortexItem v = held(p);
        if (v == VortexItem.SKYFALL) arrowRain(p);
        else if (v == VortexItem.VOIDRENDER) railShot(p);
    }

    /** Pluie de flèches sur la cible visée (bloc ou entité). */
    private void arrowRain(Player p) {
        double range = d("skyfall.rain.range", 45);
        Location eye = p.getEyeLocation();
        RayTraceResult rt = p.getWorld().rayTrace(eye, eye.getDirection(), range, FluidCollisionMode.NEVER, true, 0.4,
                en -> en instanceof LivingEntity && !en.equals(p));
        if (rt == null) {
            p.sendActionBar(Msg.c("§7Aucune cible en vue."));
            return;
        }
        if (!AbilityCooldown.use(plugin, p, "skyfall_rain", "Arrow Rain", d("skyfall.rain.cooldown", 40))) return;

        final Location center = rt.getHitPosition().toLocation(p.getWorld());
        final double radius = d("skyfall.rain.radius", 5);
        final double dmg = d("skyfall.rain.damage", 6);
        final int total = (int) d("skyfall.rain.arrows", 40);
        final int duration = Math.max(10, (int) d("skyfall.rain.duration-ticks", 60));
        int free = 0; // hauteur libre au-dessus de la cible (plafond de grotte)
        while (free < 18 && center.clone().add(0, free + 1, 0).getBlock().isPassable()) free++;
        final double top = center.getY() + Math.max(3, free) - 0.5;
        center.getWorld().playSound(center, Sound.ENTITY_ARROW_SHOOT, 1f, 0.6f);

        new BukkitRunnable() {
            int tick = 0, spawned = 0;

            @Override
            public void run() {
                if (!p.isOnline() || tick >= duration) { cancel(); return; }
                int should = (int) Math.ceil((double) total * (tick + 1) / duration) - spawned;
                ThreadLocalRandom r = ThreadLocalRandom.current();
                for (int k = 0; k < should; k++) {
                    double ang = r.nextDouble() * Math.PI * 2, rad = Math.sqrt(r.nextDouble()) * radius;
                    Location at = new Location(center.getWorld(), center.getX() + Math.cos(ang) * rad, top, center.getZ() + Math.sin(ang) * rad);
                    Arrow ar = center.getWorld().spawnArrow(at, new Vector(0, -1, 0), 2.6f, 0f);
                    ar.setShooter(p);
                    ar.setDamage(dmg);
                    ar.setCritical(true);
                    ar.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
                    ar.getPersistentDataContainer().set(rainKey, PersistentDataType.BYTE, (byte) 1);
                    Bukkit.getScheduler().runTaskLater(plugin, () -> { if (ar.isValid()) ar.remove(); }, 100L);
                }
                spawned += should;
                if (tick % 5 == 0) { // cercle de la zone
                    for (int k = 0; k < 24; k++) {
                        double ang = Math.PI * 2 * k / 24;
                        center.getWorld().spawnParticle(Particle.CRIT, center.getX() + Math.cos(ang) * radius, center.getY() + 0.2,
                                center.getZ() + Math.sin(ang) * radius, 1, 0, 0, 0, 0);
                    }
                }
                tick++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
        p.sendActionBar(Msg.c("§bArrow Rain !"));
    }

    /** Rayon perforant : dégâts à toutes les entités sur la ligne et bouclier désactivé. */
    private void railShot(Player p) {
        if (!AbilityCooldown.use(plugin, p, "voidrender_rail", "Rail Shot", d("voidrender.rail.cooldown", 30))) return;
        double range = d("voidrender.rail.range", 45);
        double dmg = d("voidrender.rail.damage", 18);
        Location eye = p.getEyeLocation();
        Vector dir = eye.getDirection().normalize();
        World w = p.getWorld();
        Set<UUID> hit = new HashSet<>();

        for (double s = 1.0; s <= range; s += 0.5) {
            Location l = eye.clone().add(dir.clone().multiply(s));
            if (!l.getBlock().isPassable()) break;
            w.spawnParticle(Particle.END_ROD, l, 1, 0, 0, 0, 0);
            if (((int) (s * 2)) % 3 == 0) w.spawnParticle(Particle.ELECTRIC_SPARK, l, 2, 0.1, 0.1, 0.1, 0.02);
            for (Entity en : w.getNearbyEntities(l, 0.9, 0.9, 0.9)) {
                if (en instanceof LivingEntity t && validTarget(p, t) && hit.add(t.getUniqueId())) {
                    t.damage(dmg, p);
                    if (t instanceof Player tp) { // brise le bouclier
                        tp.setCooldown(Material.SHIELD, 100);
                        tp.clearActiveItem();
                    }
                    t.setVelocity(dir.clone().multiply(0.6).setY(0.2));
                }
            }
        }
        w.playSound(p.getLocation(), Sound.ENTITY_WARDEN_SONIC_BOOM, 0.7f, 1.4f);
        p.sendActionBar(Msg.c("§5Rail Shot !"));
    }
}
