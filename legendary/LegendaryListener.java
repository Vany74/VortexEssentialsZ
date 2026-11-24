package fr.vortexsmp.vortexessentials.legendary;

import fr.vortexsmp.vortexessentials.Msg;
import fr.vortexsmp.vortexessentials.VortexEssentials;
import fr.vortexsmp.vortexessentials.item.ItemUtil;
import fr.vortexsmp.vortexessentials.item.VortexItem;
import fr.vortexsmp.vortexessentials.mythic.MythicEffectsTask;
import fr.vortexsmp.vortexessentials.util.AbilityCooldown;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Tameable;
import org.bukkit.entity.Villager;
import org.bukkit.entity.WanderingTrader;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** Blazing Crossbow, Reaper Scythe, Excalibur, Chrono Sword, Magma Club. */
public final class LegendaryListener implements Listener {
    private static final class Shield {
        int hits;
        long until;
    }

    private record Mark(Location loc, double health) {}

    private final VortexEssentials plugin;
    private final Set<UUID> casting = new HashSet<>();
    private final Map<UUID, Shield> shields = new HashMap<>();
    private final Map<UUID, Mark> marks = new HashMap<>();
    private final Map<UUID, Integer> crossbowShots = new HashMap<>();
    private final NamespacedKey explosiveKey;

    public LegendaryListener(VortexEssentials plugin) {
        this.plugin = plugin;
        this.explosiveKey = new NamespacedKey(plugin, "explosive_bolt");
    }

    private double d(String path, double def) {
        return plugin.getConfig().getDouble("legendary." + path, def);
    }

    private VortexItem held(Player p) {
        ItemStack s = p.getInventory().getItemInMainHand();
        if (!MythicEffectsTask.relevant(s.getType())) return null;
        return ItemUtil.get(s);
    }

    private boolean carries(Player p, VortexItem v) {
        for (ItemStack s : p.getInventory().getContents()) {
            if (s != null && MythicEffectsTask.relevant(s.getType()) && ItemUtil.is(s, v)) return true;
        }
        return false;
    }

    // ═════════════ Blazing Crossbow ═════════════

    @EventHandler(ignoreCancelled = true)
    public void shoot(EntityShootBowEvent e) {
        if (!ItemUtil.is(e.getBow(), VortexItem.BLAZING_CROSSBOW)) return;
        if (e.getProjectile() instanceof AbstractArrow a) {
            a.setFireTicks(1200);   // une flèche en feu enflamme sa cible
            a.setVisualFire(true);
            int every = (int) d("blazing-crossbow.explosive-every", 4);
            if (every > 0 && e.getEntity() instanceof Player sp) {
                int n = crossbowShots.merge(sp.getUniqueId(), 1, Integer::sum);
                if (n % every == 0) {
                    a.getPersistentDataContainer().set(explosiveKey, PersistentDataType.BYTE, (byte) 1);
                    sp.sendActionBar(Msg.c("§6Carreau explosif !"));
                }
            }
        }
    }

    // ═════════════ Reaper Scythe & Magma Club (attaque) ═════════════

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void melee(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player p) || !(e.getEntity() instanceof LivingEntity target)) return;
        if (casting.contains(p.getUniqueId())) return;

        VortexItem v = held(p);
        if (v == VortexItem.REAPER_SCYTHE) { if (p.isSneaking()) reaper(p, target); } // Drain : activation manuelle
        else if (v == VortexItem.BLAZING_CROSSBOW && p.isSneaking()) barrage(p);
        else if (v == VortexItem.MAGMA_CLUB) {
            if (p.isSneaking()) eruption(p);
            if (ThreadLocalRandom.current().nextDouble() * 100 < d("magma-club.ignite-chance", 35)) {
                target.setFireTicks((int) (d("magma-club.fire-seconds", 5) * 20));
            }
        }
    }

    private void reaper(Player p, LivingEntity target) {
        if (!AbilityCooldown.use(plugin, p, "reaper", "Drain", d("reaper-scythe.cooldown", 45))) return;

        double drain = target.getHealth() * d("reaper-scythe.drain-percent", 25) / 100.0;
        casting.add(p.getUniqueId());
        try {
            target.damage(drain, p);
        } finally {
            casting.remove(p.getUniqueId());
        }

        AttributeInstance mh = p.getAttribute(Attribute.MAX_HEALTH);
        double max = mh == null ? 20 : mh.getValue();
        p.setHealth(Math.min(max, p.getHealth() + drain));

        for (PotionEffect pe : target.getActivePotionEffects()) p.addPotionEffect(pe);

        target.getWorld().spawnParticle(Particle.SOUL, target.getLocation().add(0, 1, 0), 20, 0.3, 0.5, 0.3, 0.02);
        p.getWorld().playSound(p.getLocation(), Sound.PARTICLE_SOUL_ESCAPE, 1f, 0.8f);
        p.sendActionBar(Msg.c("§8Reaper Scythe §7: §f+" + String.format("%.1f", drain / 2) + " ❤"));
    }

    /** Drain en visant : cible la plus proche dans la ligne de mire (6 blocs). */
    private void reaperAim(Player p) {
        Location eye = p.getEyeLocation();
        org.bukkit.util.RayTraceResult rt = p.getWorld().rayTraceEntities(eye, eye.getDirection(), d("reaper-scythe.range", 6), 0.5,
                en -> en instanceof LivingEntity && !en.equals(p));
        if (rt == null || !(rt.getHitEntity() instanceof LivingEntity t) || !validTarget(p, t)) {
            p.sendActionBar(Msg.c("§7Aucune cible en vue."));
            return;
        }
        reaper(p, t);
    }

    // ═════════════ Clic droit : Excalibur / Chrono Sword ═════════════

    @EventHandler
    public void interact(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        Action a = e.getAction();
        if (a == Action.LEFT_CLICK_AIR && e.getPlayer().isSneaking()) {
            VortexItem lv = held(e.getPlayer());
            if (lv == VortexItem.REAPER_SCYTHE) { reaperAim(e.getPlayer()); return; }
            if (lv == VortexItem.MAGMA_CLUB) { eruption(e.getPlayer()); return; }
            if (lv == VortexItem.BLAZING_CROSSBOW) { barrage(e.getPlayer()); return; }
        }
        if (a != Action.RIGHT_CLICK_AIR && a != Action.RIGHT_CLICK_BLOCK) return;

        Player p = e.getPlayer();
        VortexItem v = held(p);
        if (v == VortexItem.EXCALIBUR && !p.isSneaking()) {
            // Ne pas bloquer les portes, coffres, etc.
            if (a == Action.RIGHT_CLICK_BLOCK && e.getClickedBlock() != null
                    && e.getClickedBlock().getType().isInteractable()) return;
            e.setCancelled(true);
            excalibur(p);
        } else if (v == VortexItem.CHRONO_SWORD && p.isSneaking()) {
            e.setCancelled(true);
            chrono(p);
        } else if (v == VortexItem.MAGMA_CLUB && p.isSneaking()) {
            e.setCancelled(true);
            magmaWave(p);
        } else if (v == VortexItem.REAPER_SCYTHE && p.isSneaking()) {
            e.setCancelled(true);
            harvest(p);
        }
    }

    // ───────── Excalibur ─────────

    private void excalibur(Player p) {
        if (!AbilityCooldown.use(plugin, p, "excalibur", "Excalibur", d("excalibur.cooldown", 45))) return;
        Shield s = new Shield();
        s.hits = (int) d("excalibur.hits", 3);
        s.until = System.currentTimeMillis() + (long) (d("excalibur.seconds", 10) * 1000);
        shields.put(p.getUniqueId(), s);

        Location c = p.getLocation();
        for (int k = 0; k < 24; k++) {
            double a = Math.PI * 2 * k / 24;
            c.getWorld().spawnParticle(Particle.END_ROD, c.getX() + Math.cos(a) * 1.2, c.getY() + 1,
                    c.getZ() + Math.sin(a) * 1.2, 1, 0, 0, 0, 0);
        }
        c.getWorld().playSound(c, Sound.BLOCK_BEACON_ACTIVATE, 1f, 1.5f);
        p.sendActionBar(Msg.c("§6Excalibur §7: §f" + s.hits + " coups annulés"));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void shield(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        Shield s = shields.get(p.getUniqueId());
        if (s == null) return;
        if (System.currentTimeMillis() > s.until) {
            shields.remove(p.getUniqueId());
            return;
        }
        e.setCancelled(true);
        p.getWorld().spawnParticle(Particle.CRIT, p.getLocation().add(0, 1, 0), 15, 0.4, 0.5, 0.4, 0.1);
        p.getWorld().playSound(p.getLocation(), Sound.ITEM_SHIELD_BLOCK, 1f, 1.2f);
        if (--s.hits <= 0) {
            shields.remove(p.getUniqueId());
            p.sendActionBar(Msg.c("§6Excalibur §7: bouclier épuisé"));
        } else {
            p.sendActionBar(Msg.c("§6Excalibur §7: §f" + s.hits + " coup(s) restant(s)"));
        }
    }

    // ───────── Chrono Sword ─────────

    private void chrono(Player p) {
        UUID id = p.getUniqueId();
        Mark m = marks.get(id);
        if (m == null) {
            if (!plugin.cooldowns().ready(id, "chrono")) {
                long left = plugin.cooldowns().remainingMs(id, "chrono");
                p.sendActionBar(Msg.c("§cChrono Sword §7: §f" + (int) Math.ceil(left / 1000.0) + "s"));
                return;
            }
            marks.put(id, new Mark(p.getLocation().clone(), p.getHealth()));
            p.getWorld().spawnParticle(Particle.PORTAL, p.getLocation().add(0, 1, 0), 40, 0.4, 0.8, 0.4, 0.5);
            p.getWorld().playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 0.7f);
            p.sendActionBar(Msg.c("§bPoint temporel marqué §7— sneak + clic droit pour rembobiner"));
        } else {
            rewind(p, m, false);
        }
    }

    private void rewind(Player p, Mark m, boolean death) {
        UUID id = p.getUniqueId();
        marks.remove(id);

        p.getWorld().spawnParticle(Particle.PORTAL, p.getLocation().add(0, 1, 0), 40, 0.4, 0.8, 0.4, 0.5);
        p.teleport(m.loc());
        p.setFallDistance(0);
        p.setFireTicks(0);

        AttributeInstance mh = p.getAttribute(Attribute.MAX_HEALTH);
        double max = mh == null ? 20 : mh.getValue();
        double hp = death ? Math.max(m.health(), 2.0) : m.health();
        p.setHealth(Math.max(1.0, Math.min(max, hp)));

        double cd = death ? d("chrono-sword.death-cooldown", 100) : d("chrono-sword.cooldown", 50);
        plugin.cooldowns().start(id, "chrono", plugin.boosts().cooldownMs(p, Math.round(cd * 1000)));

        p.getWorld().spawnParticle(Particle.PORTAL, p.getLocation().add(0, 1, 0), 40, 0.4, 0.8, 0.4, 0.5);
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 0.6f);
        p.sendActionBar(Msg.c(death ? "§bLe temps vous a sauvé la vie §7!" : "§bTemps rembobiné §7!"));
    }

    /** Mort évitée : si un point est marqué et la Chrono Sword est dans l'inventaire, on rembobine. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void lethal(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        Mark m = marks.get(p.getUniqueId());
        if (m == null) return;
        if (p.getHealth() - e.getFinalDamage() > 0) return;
        if (!plugin.cooldowns().ready(p.getUniqueId(), "chrono")) return;
        if (!carries(p, VortexItem.CHRONO_SWORD)) return;

        e.setCancelled(true);
        rewind(p, m, true);
    }

    // ───────── Reaper Scythe : Moisson d'âmes ─────────

    /** Draine tous les ennemis proches : dégâts + Wither, et soigne le porteur d'une part des dégâts infligés. */
    private void harvest(Player p) {
        double r = d("reaper-scythe.harvest.radius", 6);
        List<LivingEntity> targets = new ArrayList<>();
        for (Entity en : p.getNearbyEntities(r, r, r)) {
            if (en instanceof LivingEntity t && validTarget(p, t) && t.getLocation().distanceSquared(p.getLocation()) <= r * r) targets.add(t);
        }
        if (targets.isEmpty()) {
            p.sendActionBar(Msg.c("§7Aucun ennemi à portée."));
            return;
        }
        if (!AbilityCooldown.use(plugin, p, "reaper_harvest", "Moisson d'âmes", d("reaper-scythe.harvest.cooldown", 35))) return;

        double dmg = d("reaper-scythe.harvest.damage", 4);
        double ratio = d("reaper-scythe.harvest.heal-percent", 50) / 100.0;
        int wither = (int) (d("reaper-scythe.harvest.wither-seconds", 4) * 20);
        double total = 0;
        for (LivingEntity t : targets) {
            double before = t.getHealth();
            hurt(p, t, dmg);
            total += Math.max(0, before - Math.max(0, t.getHealth()));
            t.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, wither, 1));
            Location from = t.getLocation().add(0, 1, 0), to = p.getLocation().add(0, 1, 0);
            Vector line = to.toVector().subtract(from.toVector());
            for (int k = 0; k <= 12; k++) { // traînée d'âmes de la cible vers le porteur
                Location pt = from.clone().add(line.clone().multiply(k / 12.0));
                pt.getWorld().spawnParticle(Particle.SOUL, pt, 1, 0.05, 0.05, 0.05, 0.01);
            }
        }
        AttributeInstance mh = p.getAttribute(Attribute.MAX_HEALTH);
        double max = mh == null ? 20 : mh.getValue();
        double healed = Math.min(max - p.getHealth(), total * ratio);
        if (healed > 0) p.setHealth(p.getHealth() + healed);

        Location c = p.getLocation();
        for (int k = 0; k < 60; k++) { // spirale d'âmes
            double ang = k * 0.5, rad = r * k / 60.0;
            c.getWorld().spawnParticle(Particle.SOUL, c.getX() + Math.cos(ang) * rad, c.getY() + 0.2 + k * 0.03,
                    c.getZ() + Math.sin(ang) * rad, 1, 0, 0, 0, 0.01);
        }
        c.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, c.clone().add(0, 1, 0), 30, 1.2, 0.6, 1.2, 0.02);
        c.getWorld().playSound(c, Sound.PARTICLE_SOUL_ESCAPE, 1f, 0.6f);
        p.sendActionBar(Msg.c("§8Moisson d'âmes §7: §f+" + String.format("%.1f", Math.max(0, healed) / 2) + " ❤ §7(" + targets.size() + " cibles)"));
    }

    // ───────── Magma Club : Éruption et Vague de magma ─────────

    private boolean validTarget(Player p, LivingEntity t) {
        if (t.equals(p) || t instanceof ArmorStand || t instanceof Villager || t instanceof WanderingTrader) return false;
        if (t instanceof Tameable ta && ta.isTamed() && p.equals(ta.getOwner())) return false;
        return !(t instanceof Player o && (o.getGameMode() == GameMode.CREATIVE || o.getGameMode() == GameMode.SPECTATOR));
    }

    private void hurt(Player src, LivingEntity t, double dmg) {
        casting.add(src.getUniqueId());
        try {
            t.damage(dmg, src);
        } finally {
            casting.remove(src.getUniqueId());
        }
    }

    private void eruption(Player p) {
        if (!AbilityCooldown.use(plugin, p, "magma_eruption", "Éruption", d("magma-club.eruption.cooldown", 20))) return;
        double r = d("magma-club.eruption.radius", 5);
        double dmg = d("magma-club.eruption.damage", 6);
        int fire = (int) (d("magma-club.eruption.fire-seconds", 5) * 20);
        Location c = p.getLocation();
        for (Entity en : p.getNearbyEntities(r, r, r)) {
            if (!(en instanceof LivingEntity t) || !validTarget(p, t) || t.getLocation().distanceSquared(c) > r * r) continue;
            hurt(p, t, dmg);
            t.setFireTicks(fire);
            Vector away = t.getLocation().toVector().subtract(c.toVector()).setY(0);
            if (away.lengthSquared() < 0.01) away = new Vector(0, 0, 1);
            t.setVelocity(away.normalize().multiply(0.9).setY(0.6));
        }
        for (int k = 0; k < 48; k++) {
            double ang = Math.PI * 2 * k / 48;
            c.getWorld().spawnParticle(Particle.FLAME, c.getX() + Math.cos(ang) * r, c.getY() + 0.3, c.getZ() + Math.sin(ang) * r, 1, 0, 0.1, 0, 0.02);
        }
        c.getWorld().spawnParticle(Particle.LAVA, c.clone().add(0, 0.5, 0), 25, 0.8, 0.3, 0.8, 0);
        c.getWorld().playSound(c, Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 0.7f);
        p.sendActionBar(Msg.c("§6Éruption !"));
    }

    private void magmaWave(Player p) {
        if (!AbilityCooldown.use(plugin, p, "magma_wave", "Vague de magma", d("magma-club.wave.cooldown", 35))) return;
        final double range = d("magma-club.wave.range", 10);
        final double dmg = d("magma-club.wave.damage", 8);
        final int fire = (int) (d("magma-club.wave.fire-seconds", 6) * 20);
        final Location eye = p.getEyeLocation();
        final Vector dir = eye.getDirection().normalize();
        final Set<UUID> hit = new HashSet<>();
        p.getWorld().playSound(eye, Sound.ITEM_FIRECHARGE_USE, 1f, 0.6f);
        p.sendActionBar(Msg.c("§6Vague de magma !"));

        new BukkitRunnable() {
            int step = 1;

            @Override
            public void run() {
                if (step > range || !p.isOnline()) { cancel(); return; }
                Location l = eye.clone().add(dir.clone().multiply(step));
                if (l.getBlock().getType().isSolid()) { cancel(); return; }
                l.getWorld().spawnParticle(Particle.FLAME, l, 6, 0.3, 0.3, 0.3, 0.02);
                l.getWorld().spawnParticle(Particle.LAVA, l, 1, 0.2, 0.2, 0.2, 0);
                for (Entity en : l.getWorld().getNearbyEntities(l, 1.2, 1.2, 1.2)) {
                    if (en instanceof LivingEntity t && validTarget(p, t) && hit.add(t.getUniqueId())) {
                        hurt(p, t, dmg);
                        t.setFireTicks(fire);
                        t.setVelocity(dir.clone().multiply(0.8).setY(0.3));
                    }
                }
                step++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    // ───────── Blazing Crossbow : Barrage et carreau explosif ─────────

    private void barrage(Player p) {
        if (!AbilityCooldown.use(plugin, p, "crossbow_barrage", "Barrage", d("blazing-crossbow.barrage.cooldown", 25))) return;
        int n = Math.max(1, (int) d("blazing-crossbow.barrage.arrows", 5));
        double spread = d("blazing-crossbow.barrage.spread", 6);
        Vector dir = p.getEyeLocation().getDirection().normalize();
        for (int i = 0; i < n; i++) {
            double ang = Math.toRadians((i - (n - 1) / 2.0) * spread);
            Arrow ar = p.launchProjectile(Arrow.class, dir.clone().rotateAroundY(ang).multiply(3.0));
            ar.setFireTicks(1200);
            ar.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
        }
        p.getWorld().playSound(p.getLocation(), Sound.ITEM_CROSSBOW_SHOOT, 1f, 0.8f);
        p.sendActionBar(Msg.c("§6Barrage !"));
    }

    /** Carreau explosif : explosion à l'impact, sans feu ni dégâts aux blocs. */
    @EventHandler(ignoreCancelled = true)
    public void bolt(ProjectileHitEvent e) {
        if (!(e.getEntity() instanceof AbstractArrow a)) return;
        Byte b = a.getPersistentDataContainer().get(explosiveKey, PersistentDataType.BYTE);
        if (b == null || b != 1) return;
        a.getPersistentDataContainer().remove(explosiveKey);
        Location l = e.getHitEntity() != null ? e.getHitEntity().getLocation() : a.getLocation();
        Entity src = a.getShooter() instanceof Entity en ? en : null;
        l.getWorld().createExplosion(l, (float) d("blazing-crossbow.explosion-power", 2.0), false, false, src);
        a.remove();
    }

    @EventHandler
    public void quit(PlayerQuitEvent e) {
        marks.remove(e.getPlayer().getUniqueId());
        shields.remove(e.getPlayer().getUniqueId());
    }
}
