package fr.vortexsmp.vortexessentials.mythic;

import fr.vortexsmp.vortexessentials.Msg;
import fr.vortexsmp.vortexessentials.VortexEssentials;
import fr.vortexsmp.vortexessentials.item.ItemUtil;
import fr.vortexsmp.vortexessentials.item.VortexItem;
import fr.vortexsmp.vortexessentials.util.AbilityCooldown;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
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
import org.bukkit.event.entity.EntityDamageEvent;
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
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Umbral Scythe (catégorie Secret Weapons, base mace) — inspirée de « ManePear's Scythe » : casse-bouclier, aucun dégât
 * de chute en la tenant, smash attacks de mace natifs, impossible à enchanter ou modifier.
 * Capacités : Eclipse Leap (sneak + clic droit), Reaper Chains (sneak + clic gauche : 3 charges libres, visez où vous voulez),
 * Shatterer (touche d'échange de main : onde de choc dévastatrice).
 * Effets permanents (inventaire) : mythic-weapons.umbral-scythe.effects.
 */
public final class SecretWeaponListener implements Listener {
    private final VortexEssentials plugin;
    /** Séquence Reaper Chains en cours : {prochaine charge (0 à 2), expiration en ms}. */
    private final Map<UUID, long[]> seq = new HashMap<>();

    public SecretWeaponListener(VortexEssentials plugin) {
        this.plugin = plugin;
    }

    private double d(String path, double def) { return plugin.getConfig().getDouble("mythic-weapons.umbral-scythe." + path, def); }

    private static boolean isScythe(ItemStack s) {
        return s != null && s.getType() == Material.MACE && ItemUtil.get(s) == VortexItem.UMBRAL_SCYTHE;
    }

    private static boolean holds(Player p) {
        return isScythe(p.getInventory().getItemInMainHand()) || isScythe(p.getInventory().getItemInOffHand());
    }

    private boolean validTarget(Player p, LivingEntity t) {
        if (t.equals(p) || t instanceof ArmorStand || t instanceof Villager || t instanceof WanderingTrader) return false;
        if (t instanceof Tameable ta && ta.isTamed() && p.equals(ta.getOwner())) return false;
        return !(t instanceof Player o && (o.getGameMode() == GameMode.CREATIVE || o.getGameMode() == GameMode.SPECTATOR));
    }

    @EventHandler(ignoreCancelled = true)
    public void fall(EntityDamageEvent e) {
        if (e.getCause() == EntityDamageEvent.DamageCause.FALL && e.getEntity() instanceof Player p && holds(p)) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void hit(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player p) || !isScythe(p.getInventory().getItemInMainHand())) return;
        if (e.getEntity() instanceof Player victim && victim.isBlocking()) {
            victim.setCooldown(Material.SHIELD, 100);
            victim.clearActiveItem();
            victim.getWorld().playSound(victim.getLocation(), Sound.ITEM_SHIELD_BREAK, 1f, 0.9f);
            victim.getWorld().spawnParticle(Particle.CRIT, victim.getLocation().add(0, 1, 0), 15, 0.3, 0.4, 0.3, 0.2);
        }
        if (p.isSneaking() && e.getEntity() instanceof LivingEntity t && validTarget(p, t)) {
            chains(p, t);
        }
    }

    @EventHandler
    public void interact(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        Player p = e.getPlayer();
        if (!p.isSneaking() || !isScythe(p.getInventory().getItemInMainHand())) return;
        Action a = e.getAction();
        if (a == Action.RIGHT_CLICK_AIR || a == Action.RIGHT_CLICK_BLOCK) {
            e.setCancelled(true);
            leap(p);
        } else if (a == Action.LEFT_CLICK_AIR || a == Action.LEFT_CLICK_BLOCK) {
            chains(p, null);
        }
    }

    @EventHandler
    public void quit(PlayerQuitEvent e) {
        seq.remove(e.getPlayer().getUniqueId());
    }

    @EventHandler
    public void swap(PlayerSwapHandItemsEvent e) {
        Player p = e.getPlayer();
        if (!isScythe(p.getInventory().getItemInMainHand()) && !isScythe(p.getInventory().getItemInOffHand())) return;
        e.setCancelled(true);
        shatterer(p);
    }

    private void leap(Player p) {
        if (!AbilityCooldown.use(plugin, p, "umbral_leap", "Eclipse Leap", d("leap.cooldown", 16))) return;
        World w = p.getWorld();
        Location c = p.getLocation().clone();
        // 1) éclipse au décollage : les ennemis proches sont blessés, aveuglés et repoussés
        double tr = d("leap.takeoff-radius", 5), td = d("leap.takeoff-damage", 8);
        int blind = (int) (d("leap.blind-seconds", 3) * 20);
        PotionEffectType blindness = PotionEffectType.getByKey(NamespacedKey.minecraft("blindness"));
        for (Entity en : p.getNearbyEntities(tr, tr, tr)) {
            if (!(en instanceof LivingEntity t) || !validTarget(p, t) || t.getLocation().distanceSquared(c) > tr * tr) continue;
            t.damage(td, p);
            if (blindness != null) t.addPotionEffect(new PotionEffect(blindness, blind, 0));
            Vector away = t.getLocation().toVector().subtract(c.toVector()).setY(0);
            if (away.lengthSquared() < 0.01) away = new Vector(0, 0, 1);
            t.setVelocity(away.normalize().multiply(0.7).setY(0.5));
        }
        // 2) bond : très haut et très loin, avec résistance et vitesse le temps du saut
        Vector look = p.getLocation().getDirection().setY(0);
        if (look.lengthSquared() > 0.001) look.normalize().multiply(d("leap.forward", 1.1)); else look = new Vector(0, 0, 0);
        p.setVelocity(look.setY(d("leap.height", 2.2)));
        PotionEffectType res = PotionEffectType.getByKey(NamespacedKey.minecraft("resistance"));
        PotionEffectType spd = PotionEffectType.getByKey(NamespacedKey.minecraft("speed"));
        if (res != null) p.addPotionEffect(new PotionEffect(res, 80, 2));
        if (spd != null) p.addPotionEffect(new PotionEffect(spd, 80, 2));
        w.spawnParticle(Particle.SQUID_INK, c.clone().add(0, 0.2, 0), 60, 0.8, 0.2, 0.8, 0.08);
        w.spawnParticle(Particle.SMOKE, c.clone().add(0, 0.3, 0), 40, 1.2, 0.2, 1.2, 0.03);
        for (int k = 0; k < 40; k++) {
            double ang = Math.PI * 2 * k / 40;
            w.spawnParticle(Particle.DUST, c.getX() + Math.cos(ang) * tr, c.getY() + 0.2, c.getZ() + Math.sin(ang) * tr, 1, 0, 0, 0, 0,
                    new Particle.DustOptions(Color.fromRGB(30, 10, 55), 1.6f));
        }
        w.playSound(c, Sound.ENTITY_ENDER_DRAGON_FLAP, 1.2f, 0.6f);
        w.playSound(c, Sound.ENTITY_WARDEN_SONIC_BOOM, 0.8f, 1.3f);
        p.sendActionBar(Msg.c("§5Eclipse Leap !"));
        // 3) traînée d'ombre pendant le vol, puis écrasement à l'atterrissage
        new BukkitRunnable() {
            int t = 0;
            boolean airborne = false;

            @Override
            public void run() {
                t++;
                if (!p.isOnline() || p.isDead()) { cancel(); return; }
                if (!p.isOnGround()) airborne = true;
                if ((airborne && p.isOnGround() && t > 4) || t > 100) {
                    cancel();
                    if (airborne) slam(p);
                    return;
                }
                Location l = p.getLocation().add(0, 0.9, 0);
                p.getWorld().spawnParticle(Particle.SQUID_INK, l, 3, 0.25, 0.3, 0.25, 0.02);
                p.getWorld().spawnParticle(Particle.DUST, l, 2, 0.2, 0.3, 0.2, 0, new Particle.DustOptions(Color.fromRGB(120, 60, 190), 1.1f));
            }
        }.runTaskTimer(plugin, 4L, 1L);
    }

    /** Atterrissage de l'Eclipse Leap : onde de choc qui écrase et projette les ennemis. */
    private void slam(Player p) {
        double r = d("leap.slam-radius", 7), dmg = d("leap.slam-damage", 14), launch = d("leap.slam-launch", 1.1);
        Location c = p.getLocation().clone();
        World w = c.getWorld();
        for (Entity en : p.getNearbyEntities(r, r, r)) {
            if (!(en instanceof LivingEntity t) || !validTarget(p, t) || t.getLocation().distanceSquared(c) > r * r) continue;
            double f = 1.0 - 0.5 * (t.getLocation().distance(c) / r);
            t.damage(dmg * f, p);
            Vector away = t.getLocation().toVector().subtract(c.toVector()).setY(0);
            if (away.lengthSquared() < 0.01) away = new Vector(0, 0, 1);
            t.setVelocity(away.normalize().multiply(0.7).setY(launch));
        }
        final Material below = c.clone().add(0, -1, 0).getBlock().getType().isAir() ? Material.STONE : c.clone().add(0, -1, 0).getBlock().getType();
        w.playSound(c, Sound.ENTITY_GENERIC_EXPLODE, 1.2f, 0.7f);
        w.playSound(c, Sound.ITEM_MACE_SMASH_GROUND_HEAVY, 1f, 1f);
        new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                if (t > 8) { cancel(); return; }
                double rho = 0.8 + (r - 0.8) * (t / 8.0);
                int pts = (int) Math.max(14, rho * 8);
                for (int k = 0; k < pts; k++) {
                    double ang = Math.PI * 2 * k / pts;
                    Location pt = c.clone().add(Math.cos(ang) * rho, 0.15, Math.sin(ang) * rho);
                    w.spawnParticle(Particle.BLOCK, pt, 2, 0.15, 0.3, 0.15, 0.05, below.createBlockData());
                    if (k % 3 == 0) w.spawnParticle(Particle.SQUID_INK, pt, 1, 0.05, 0.1, 0.05, 0.01);
                }
                t++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    // ═════════════ Reaper Chains : 3 charges libres ═════════════

    private static final Color[] DARK = {Color.fromRGB(40, 20, 90), Color.fromRGB(90, 10, 20), Color.fromRGB(15, 70, 50)};
    private static final Color[] GLOW = {Color.fromRGB(120, 180, 255), Color.fromRGB(255, 90, 70), Color.fromRGB(120, 255, 170)};

    /**
     * Un cooldown, trois chaînes enchaînées dans une fenêtre de temps (chains.window). Chaque charge part là où vous visez :
     * vers le ciel, sur un ennemi, sur un bloc, ou droit devant pour fuir : à vous d'en faire ce que vous voulez.
     */
    private void chains(Player p, LivingEntity hit) {
        long now = System.currentTimeMillis();
        long[] st = seq.get(p.getUniqueId());
        int stage;
        if (st != null && now < st[1] && st[0] < 3) {
            stage = (int) st[0];
        } else {
            if (!AbilityCooldown.use(plugin, p, "umbral_chains", "Reaper Chains", d("chains.cooldown", 20))) return;
            stage = 0;
        }
        freeChain(p, hit, stage);
        if (stage >= 2) seq.remove(p.getUniqueId());
        else seq.put(p.getUniqueId(), new long[]{stage + 1, now + (long) (d("chains.window", 6) * 1000)});
        p.sendActionBar(Msg.c("§5Reaper Chains §d" + (stage + 1) + "/3"));
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 1f, 0.5f);
        p.getWorld().playSound(p.getLocation(), Sound.BLOCK_CHAIN_PLACE, 1f, 0.6f);
    }

    /** La chaîne s'accroche à l'entité ou au bloc visé ; sans cible elle file droit devant. Vous êtes tiré vers ce point. */
    private void freeChain(Player p, LivingEntity hit, int stage) {
        double range = d("chains.range", 25);
        Location eye = p.getEyeLocation();
        Location target = null;
        LivingEntity ent = hit != null && validTarget(p, hit) ? hit : null;
        Block hitBlock = null;
        if (ent == null) {
            RayTraceResult rt = p.getWorld().rayTrace(eye, eye.getDirection(), range, FluidCollisionMode.NEVER, true, 0.8,
                    en -> en instanceof LivingEntity le && !en.equals(p) && validTarget(p, le));
            if (rt != null) {
                if (rt.getHitEntity() instanceof LivingEntity le) ent = le;
                else { target = rt.getHitPosition().toLocation(p.getWorld()); hitBlock = rt.getHitBlock(); }
            }
        }
        if (ent != null) target = ent.getLocation().add(0, ent.getHeight() / 2, 0);
        double flight = d("chains.free-distance", 14);
        if (target == null) target = eye.clone().add(eye.getDirection().multiply(flight));
        Vector line = target.toVector().subtract(p.getLocation().add(0, 1.1, 0).toVector());
        double dist = line.length();
        if (dist > 0.5) {
            double power = Math.max(d("chains.min-speed", 1.4), Math.min(d("chains.max-speed", 2.8), 0.6 + dist * 0.12));
            Vector v = line.clone().normalize().multiply(power);
            v.setY(v.getY() + 0.15);
            p.setVelocity(v);
        }
        if (ent != null) { // la chaîne blesse l'ennemi accroché
            ent.damage(d("chains.damage", 10), p);
            PotionEffectType slow = PotionEffectType.getByKey(NamespacedKey.minecraft("slowness"));
            if (slow != null) ent.addPotionEffect(new PotionEffect(slow, 40, 1));
            p.getWorld().spawnParticle(Particle.CRIT, ent.getLocation().add(0, ent.getHeight() / 2, 0), 18, 0.3, 0.4, 0.3, 0.3);
            p.getWorld().playSound(ent.getLocation(), Sound.ITEM_TRIDENT_HIT, 1f, 0.6f);
        }
        animateChains(p, target, DARK[stage % 3], GLOW[stage % 3]);
        if (hitBlock != null) shockwave(p, target, hitBlock.getType());
    }

    /** Chaîne plantée dans le sol : onde de choc qui blesse et projette les ennemis alentour. */
    private void shockwave(Player p, Location at, Material surface) {
        double r = d("chains.shockwave.radius", 5), dmg = d("chains.shockwave.damage", 10), launch = d("chains.shockwave.launch", 0.8);
        World w = at.getWorld();
        for (Entity en : w.getNearbyEntities(at, r, r, r)) {
            if (!(en instanceof LivingEntity t) || !validTarget(p, t) || t.getLocation().distanceSquared(at) > r * r) continue;
            double f = 1.0 - 0.5 * (t.getLocation().distance(at) / r);
            t.damage(dmg * f, p);
            Vector away = t.getLocation().toVector().subtract(at.toVector()).setY(0);
            if (away.lengthSquared() < 0.01) away = new Vector(0, 0, 1);
            t.setVelocity(away.normalize().multiply(0.6).setY(launch));
        }
        Material mat = surface.isAir() || !surface.isBlock() ? Material.STONE : surface;
        w.playSound(at, Sound.ENTITY_GENERIC_EXPLODE, 1f, 0.8f);
        w.playSound(at, Sound.BLOCK_CHAIN_BREAK, 1f, 0.5f);
        new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                if (t > 7) { cancel(); return; }
                double rho = 0.6 + (r - 0.6) * (t / 7.0);
                int pts = (int) Math.max(12, rho * 8);
                for (int k = 0; k < pts; k++) {
                    double ang = Math.PI * 2 * k / pts;
                    Location pt = at.clone().add(Math.cos(ang) * rho, 0.15, Math.sin(ang) * rho);
                    w.spawnParticle(Particle.BLOCK, pt, 2, 0.15, 0.3, 0.15, 0.05, mat.createBlockData());
                    if (k % 3 == 0) w.spawnParticle(Particle.SOUL_FIRE_FLAME, pt, 1, 0.05, 0.1, 0.05, 0.01);
                }
                t++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /** Chaîne en double hélice : elle jaillit en 6 ticks, reste tendue puis se dissipe dans une gerbe d'âmes. */
    private void animateChains(Player p, Location target, Color darkC, Color glowC) {
        final World w = p.getWorld();
        final Particle.DustOptions dark = new Particle.DustOptions(darkC, 1.3f);
        final Particle.DustOptions glow = new Particle.DustOptions(glowC, 1.0f);
        new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                if (!p.isOnline() || p.isDead() || t > 16) {
                    cancel();
                    if (t > 16) {
                        w.spawnParticle(Particle.SOUL, target, 24, 0.5, 0.5, 0.5, 0.05);
                        w.spawnParticle(Particle.SWEEP_ATTACK, target, 3, 0.4, 0.4, 0.4, 0);
                        w.playSound(target, Sound.BLOCK_CHAIN_BREAK, 1f, 0.7f);
                    }
                    return;
                }
                Location from = p.getLocation().add(0, 1.0, 0);
                Vector line = target.toVector().subtract(from.toVector());
                double len = line.length();
                if (len < 0.3) { t = 99; return; }
                Vector dir = line.clone().normalize();
                Vector up = Math.abs(dir.getY()) > 0.97 ? new Vector(1, 0, 0) : new Vector(0, 1, 0);
                Vector u = dir.clone().crossProduct(up).normalize();
                Vector vv = dir.clone().crossProduct(u).normalize();
                double reach = len * Math.min(1.0, (t + 1) / 6.0);
                double wobble = t < 6 ? 0.45 : Math.max(0.05, 0.3 - (t - 6) * 0.03);
                for (int c = 0; c < 2; c++) {
                    double phase = c * Math.PI + t * 0.9;
                    for (double s = 0.3; s <= reach; s += 0.3) {
                        double th = phase + s * 2.4;
                        Vector off = u.clone().multiply(Math.cos(th) * wobble).add(vv.clone().multiply(Math.sin(th) * wobble));
                        Location pt = from.clone().add(dir.clone().multiply(s)).add(off);
                        int k = (int) (s / 0.3);
                        if (k % 3 == 0) w.spawnParticle(Particle.DUST, pt, 1, 0, 0, 0, 0, glow);
                        else w.spawnParticle(Particle.DUST, pt, 1, 0.02, 0.02, 0.02, 0, dark);
                        if (k % 5 == 0) w.spawnParticle(Particle.SOUL_FIRE_FLAME, pt, 1, 0.02, 0.02, 0.02, 0.005);
                    }
                }
                if (t < 6) w.spawnParticle(Particle.CRIT, from.clone().add(dir.clone().multiply(reach)), 4, 0.1, 0.1, 0.1, 0.1);
                if (t == 6) {
                    w.spawnParticle(Particle.SWEEP_ATTACK, target, 2, 0.3, 0.3, 0.3, 0);
                    w.spawnParticle(Particle.SOUL, target, 14, 0.4, 0.4, 0.4, 0.04);
                    w.playSound(target, Sound.BLOCK_CHAIN_HIT, 1f, 0.6f);
                }
                t++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    // ═════════════ Shatterer : onde de choc ═════════════

    private void shatterer(Player p) {
        if (!AbilityCooldown.use(plugin, p, "umbral_shatter", "Shatterer", d("shatterer.cooldown", 25))) return;
        final double r = d("shatterer.radius", 10);
        final double dmg = d("shatterer.damage", 24);               // 20 PV = 10 cœurs au centre (moitié au bord)
        final int stun = (int) (d("shatterer.stun-seconds", 2.5) * 20);
        final double launch = d("shatterer.launch", 2.0);
        final Location c = p.getLocation().clone();
        final World w = c.getWorld();
        PotionEffectType slow = PotionEffectType.getByKey(NamespacedKey.minecraft("slowness"));
        PotionEffectType weak = PotionEffectType.getByKey(NamespacedKey.minecraft("weakness"));
        PotionEffectType dark = PotionEffectType.getByKey(NamespacedKey.minecraft("darkness"));
        PotionEffectType abs = PotionEffectType.getByKey(NamespacedKey.minecraft("absorption"));

        for (Entity en : p.getNearbyEntities(r, r, r)) {
            if (!(en instanceof LivingEntity t) || !validTarget(p, t)) continue;
            double dist = t.getLocation().distance(c);
            if (dist > r) continue;
            double factor = 1.0 - 0.5 * (dist / r);
            t.damage(dmg * factor, p);
            Vector away = t.getLocation().toVector().subtract(c.toVector()).setY(0);
            if (away.lengthSquared() < 0.01) away = new Vector(0, 0, 1);
            t.setVelocity(away.normalize().multiply(0.5 + 0.4 * factor).setY(launch * (0.8 + 0.5 * factor))); // projeté dans les airs
            if (slow != null) t.addPotionEffect(new PotionEffect(slow, stun, 4));   // paralysie
            if (weak != null) t.addPotionEffect(new PotionEffect(weak, 120, 1));
            if (dark != null) t.addPotionEffect(new PotionEffect(dark, 60, 0));
        }
        if (abs != null) p.addPotionEffect(new PotionEffect(abs, 160, 2)); // le porteur est fortifié
        p.setVelocity(new Vector(0, d("shatterer.self-launch", 0.9), 0));   // et s'élève pour enchaîner un smash de mace

        w.strikeLightningEffect(c);
        w.playSound(c, Sound.ENTITY_GENERIC_EXPLODE, 1.5f, 0.5f);
        w.playSound(c, Sound.ENTITY_WARDEN_SONIC_BOOM, 1f, 0.8f);
        w.playSound(c, Sound.BLOCK_STONE_BREAK, 1.5f, 0.4f);
        p.sendActionBar(Msg.c("§5Shatterer !"));

        final Material below = c.clone().add(0, -1, 0).getBlock().getType().isAir() ? Material.STONE : c.clone().add(0, -1, 0).getBlock().getType();
        new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                if (t > 12) { cancel(); return; }
                double rho = 1 + (r - 1) * (t / 12.0);
                int pts = (int) Math.max(14, rho * 9);
                for (int k = 0; k < pts; k++) { // anneau de roche qui explose
                    double ang = Math.PI * 2 * k / pts;
                    Location at = c.clone().add(Math.cos(ang) * rho, 0.15, Math.sin(ang) * rho);
                    w.spawnParticle(Particle.BLOCK, at, 3, 0.2, 0.5, 0.2, 0.08, below.createBlockData());
                    if (k % 4 == 0) w.spawnParticle(Particle.CLOUD, at, 1, 0.1, 0.2, 0.1, 0.02);
                }
                for (int k = 0; k < 8; k++) { // fissures rayonnantes
                    double ang = Math.PI * 2 * k / 8 + 0.4;
                    for (double s = 0.5; s <= rho; s += 0.8) {
                        w.spawnParticle(Particle.SMOKE, c.clone().add(Math.cos(ang) * s, 0.1, Math.sin(ang) * s), 1, 0.05, 0.02, 0.05, 0);
                    }
                }
                if (t % 3 == 0) w.playSound(c, Sound.BLOCK_DEEPSLATE_BREAK, 1f, 0.5f);
                t++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }
}
