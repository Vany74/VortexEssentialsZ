package fr.vortexsmp.vortexessentials.mythic;

import fr.vortexsmp.vortexessentials.Msg;
import fr.vortexsmp.vortexessentials.VortexEssentials;
import fr.vortexsmp.vortexessentials.item.ItemUtil;
import fr.vortexsmp.vortexessentials.item.VortexItem;
import fr.vortexsmp.vortexessentials.util.AbilityCooldown;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.scheduler.BukkitTask;
import java.util.HashMap;
import java.util.Map;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.entity.Villager;
import org.bukkit.entity.WanderingTrader;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.Team;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Capacités des armes mythiques.
 * Capacité = sneak + clic gauche (Sealord, Mintus) ou clic droit (Lightspeed : Dash).
 * Ultime   = sneak + clic droit.
 */
public final class MythicAbilities implements Listener {
    private final VortexEssentials plugin;
    /** Joueurs en train d'infliger des dégâts de capacité (évite de redéclencher la capacité). */
    private final Set<UUID> casting = new HashSet<>();
    private final NamespacedKey cheerKey;
    private final Map<UUID, BukkitTask> cheerTasks = new HashMap<>();

    public MythicAbilities(VortexEssentials plugin) {
        this.plugin = plugin;
        this.cheerKey = new NamespacedKey(plugin, "mintus_cheer_damage");
    }

    // ═════════════ Config ═════════════

    private double d(String path, double def) {
        return plugin.getConfig().getDouble("mythic-weapons." + path, def);
    }

    private int i(String path, int def) {
        return plugin.getConfig().getInt("mythic-weapons." + path, def);
    }

    // ═════════════ Déclencheurs ═════════════

    private VortexItem heldMythic(Player p) {
        ItemStack s = p.getInventory().getItemInMainHand();
        if (!MythicEffectsTask.relevant(s.getType())) return null;
        VortexItem v = ItemUtil.get(s);
        return v != null && v.isMythicWeapon() && !v.customAbilities() ? v : null;
    }

    @EventHandler
    public void interact(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        Player p = e.getPlayer();
        VortexItem v = heldMythic(p);
        if (v == null) return;

        Action a = e.getAction();
        boolean sneak = p.isSneaking();

        if (a == Action.LEFT_CLICK_AIR) {
            if (sneak && v != VortexItem.LIGHTSPEED) capacity(p, v);
            return;
        }
        if (a != Action.RIGHT_CLICK_AIR && a != Action.RIGHT_CLICK_BLOCK) return;

        if (sneak) {
            e.setCancelled(true);
            ultimate(p, v);
        } else if (v == VortexItem.LIGHTSPEED) {
            if (a == Action.RIGHT_CLICK_BLOCK && e.getClickedBlock() != null
                    && e.getClickedBlock().getType().isInteractable()) return;
            e.setCancelled(true);
            capacity(p, v);
        }
    }

    /** Sneak + frapper une entité déclenche aussi la capacité (le clic sur une entité n'émet pas d'interact). */
    @EventHandler(ignoreCancelled = true)
    public void hit(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player p) || !p.isSneaking()) return;
        if (casting.contains(p.getUniqueId())) return;
        VortexItem v = heldMythic(p);
        if (v == null || v == VortexItem.LIGHTSPEED) return;
        capacity(p, v);
    }

    /** Lightspeed : immunité aux dégâts de chute (tant qu'elle est portée). */
    @EventHandler(ignoreCancelled = true)
    public void fall(EntityDamageEvent e) {
        if (e.getCause() != EntityDamageEvent.DamageCause.FALL) return;
        if (!(e.getEntity() instanceof Player p)) return;
        if (MythicEffectsTask.carried(p).contains(VortexItem.LIGHTSPEED)) e.setCancelled(true);
    }

    private void capacity(Player p, VortexItem v) {
        switch (v) {
            case SEALORD -> tidalSlam(p);
            case MINTUS -> rallyingCheer(p);
            case LIGHTSPEED -> dash(p);
            default -> { }
        }
    }

    private void ultimate(Player p, VortexItem v) {
        switch (v) {
            case SEALORD -> maelstrom(p);
            case LIGHTSPEED -> sonicSlash(p);
            case MINTUS -> villagersWrath(p);
            default -> { }
        }
    }

    // ═════════════ Utilitaires ═════════════

    private boolean use(Player p, String key, String label, int seconds) {
        return AbilityCooldown.use(plugin, p, key, label, seconds);
    }

    /** Entités vivantes dans le rayon, hors lanceur, armor stands, familiers du lanceur, joueurs créa/spec. */
    private List<LivingEntity> targets(Player p, double r, boolean sparePassive) {
        List<LivingEntity> out = new ArrayList<>();
        Location c = p.getLocation();
        for (Entity en : p.getNearbyEntities(r, r, r)) {
            if (!(en instanceof LivingEntity le) || le instanceof ArmorStand) continue;
            if (le.getLocation().distanceSquared(c) > r * r) continue;
            if (le instanceof Tameable t && t.isTamed() && p.equals(t.getOwner())) continue;
            if (le instanceof Player o
                    && (o.getGameMode() == GameMode.SPECTATOR || o.getGameMode() == GameMode.CREATIVE)) continue;
            if (sparePassive && (le instanceof Villager || le instanceof WanderingTrader)) continue;
            out.add(le);
        }
        return out;
    }

    private void hurt(LivingEntity target, double dmg, Player source) {
        casting.add(source.getUniqueId());
        try {
            target.damage(dmg, source);
        } finally {
            casting.remove(source.getUniqueId());
        }
    }

    private void ring(Location c, double r, Particle particle) {
        World w = c.getWorld();
        for (int k = 0; k < 36; k++) {
            double a = Math.PI * 2 * k / 36;
            w.spawnParticle(particle, c.getX() + Math.cos(a) * r, c.getY() + 0.2,
                    c.getZ() + Math.sin(a) * r, 1, 0, 0, 0, 0);
        }
    }

    private Vector away(Location from, Location to, double strength, double up) {
        Vector v = to.toVector().subtract(from.toVector());
        v.setY(0);
        if (v.lengthSquared() < 0.01) v = from.getDirection().setY(0);
        return v.normalize().multiply(strength).setY(up);
    }

    // ═════════════ Sealord ═════════════

    private void tidalSlam(Player p) {
        if (!use(p, "sealord_slam", "Tidal Slam", i("sealord.tidal-slam.cooldown", 20))) return;
        double r = d("sealord.tidal-slam.radius", 6);
        double kb = d("sealord.tidal-slam.knockback", 1.6);
        int slow = i("sealord.tidal-slam.slow-seconds", 5);

        Location c = p.getLocation();
        for (LivingEntity le : targets(p, r, true)) {
            le.setVelocity(away(c, le.getLocation(), kb, 0.45));
            le.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, slow * 20, 1));
        }
        ring(c, r * 0.5, Particle.SPLASH);
        ring(c, r, Particle.SPLASH);
        ring(c, r * 0.75, Particle.CLOUD);
        c.getWorld().playSound(c, Sound.ITEM_TRIDENT_THUNDER, 1f, 0.8f);
        p.sendActionBar(Msg.c("§bTidal Slam !"));
    }

    private void maelstrom(Player p) {
        if (!use(p, "sealord_maelstrom", "Maelstrom", i("sealord.maelstrom.cooldown", 60))) return;
        final double r = d("sealord.maelstrom.radius", 10);
        final int durationTicks = i("sealord.maelstrom.duration", 5) * 20;
        final double dps = d("sealord.maelstrom.damage-per-second", 4);
        final int fatigue = i("sealord.maelstrom.fatigue-seconds", 8);

        p.getWorld().playSound(p.getLocation(), Sound.ITEM_TRIDENT_RIPTIDE_3, 1.2f, 0.6f);
        p.sendActionBar(Msg.c("§3Maelstrom !"));

        new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                if (!p.isOnline() || p.isDead() || t >= durationTicks) {
                    cancel();
                    return;
                }
                Location c = p.getLocation();
                for (LivingEntity le : targets(p, r, true)) {
                    Vector pull = c.toVector().subtract(le.getLocation().toVector());
                    if (pull.lengthSquared() > 2.25) {
                        le.setVelocity(pull.normalize().multiply(0.5).setY(0.1));
                    }
                    if (t % 20 == 0) {
                        hurt(le, dps, p);
                        le.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, fatigue * 20, 2));
                    }
                }
                double a = t * 0.35;
                for (int k = 0; k < 3; k++) {
                    double ang = a + k * (Math.PI * 2 / 3);
                    double rr = 2 + (t % 40) / 40.0 * (r - 2);
                    c.getWorld().spawnParticle(Particle.BUBBLE, c.getX() + Math.cos(ang) * rr, c.getY() + 0.5,
                            c.getZ() + Math.sin(ang) * rr, 2, 0.1, 0.3, 0.1, 0);
                }
                t += 2;
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    // ═════════════ Lightspeed ═════════════

    private void dash(Player p) {
        if (!use(p, "lightspeed_dash", "Dash", i("lightspeed.dash.cooldown", 8))) return;
        double power = d("lightspeed.dash.power", 2.2);
        Vector v = p.getLocation().getDirection().multiply(power);
        if (v.getY() < 0.2) v.setY(0.2);
        p.setVelocity(v);
        p.setFallDistance(0);
        Location l = p.getLocation();
        l.getWorld().spawnParticle(Particle.CLOUD, l, 15, 0.3, 0.2, 0.3, 0.05);
        l.getWorld().playSound(l, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 1.4f);
    }

    private void sonicSlash(Player p) {
        if (!use(p, "lightspeed_slash", "Sonic Slash", i("lightspeed.sonic-slash.cooldown", 45))) return;
        double range = d("lightspeed.sonic-slash.range", 7);
        double dmg = d("lightspeed.sonic-slash.damage", 16);
        double angle = d("lightspeed.sonic-slash.angle", 45);
        int res = i("lightspeed.sonic-slash.resistance-seconds", 2);

        // Quasi-invincibilité brève : Résistance V
        p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, res * 20, 4, true, false, true));

        Location eye = p.getEyeLocation();
        Vector dir = eye.getDirection();
        for (LivingEntity le : targets(p, range, true)) {
            Vector to = le.getEyeLocation().toVector().subtract(eye.toVector());
            if (to.lengthSquared() < 0.0001) continue;
            if (Math.toDegrees(dir.angle(to)) > angle) continue;
            hurt(le, dmg, p);
        }

        World w = eye.getWorld();
        for (double k = 1; k <= range; k += 1) {
            Location l = eye.clone().add(dir.clone().multiply(k));
            w.spawnParticle(Particle.SWEEP_ATTACK, l, 1, 0, 0, 0, 0);
        }
        p.setVelocity(dir.clone().multiply(1.0).setY(Math.max(dir.getY(), 0.1)));
        w.playSound(eye, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.2f, 0.7f);
        p.sendActionBar(Msg.c("§eSonic Slash !"));
    }

    // ═════════════ Mintus ═════════════

    private void rallyingCheer(Player p) {
        if (!use(p, "mintus_cheer", "Rallying Cheer", i("mintus.rallying-cheer.cooldown", 30))) return;
        double r = d("mintus.rallying-cheer.radius", 12);
        int sec = i("mintus.rallying-cheer.seconds", 15);

        Team team = plugin.getServer().getScoreboardManager().getMainScoreboard().getEntryTeam(p.getName());
        Location c = p.getLocation();
        for (Player o : p.getWorld().getPlayers()) {
            if (o.getGameMode() == GameMode.SPECTATOR) continue;
            if (o.getLocation().distanceSquared(c) > r * r) continue;
            // Dans une équipe : alliés = même équipe. Sans équipe : tous les joueurs proches.
            if (!o.equals(p) && team != null && !team.hasEntry(o.getName())) continue;
            attackBuff(o, d("mintus.rallying-cheer.attack-damage", 4.0), sec * 20);
            o.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, sec * 20, 1));
            o.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, o.getLocation().add(0, 1, 0), 12, 0.4, 0.6, 0.4, 0);
        }
        ring(c, r * 0.5, Particle.HAPPY_VILLAGER);
        c.getWorld().playSound(c, Sound.BLOCK_BELL_USE, 1.5f, 1f);
        p.sendActionBar(Msg.c("§aRallying Cheer !"));
    }

    /** Bonus de dégâts d'attaque temporaire (attribut) : remplace l'ancien effet de Force. */
    private void attackBuff(Player o, double amount, int ticks) {
        AttributeInstance ai = o.getAttribute(Attribute.ATTACK_DAMAGE);
        if (ai == null) return;
        if (ai.getModifier(cheerKey) != null) ai.removeModifier(cheerKey);
        ai.addTransientModifier(new AttributeModifier(cheerKey, amount, AttributeModifier.Operation.ADD_NUMBER));
        BukkitTask old = cheerTasks.remove(o.getUniqueId());
        if (old != null) old.cancel();
        cheerTasks.put(o.getUniqueId(), plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            AttributeInstance a2 = o.getAttribute(Attribute.ATTACK_DAMAGE);
            if (a2 != null) a2.removeModifier(cheerKey);
            cheerTasks.remove(o.getUniqueId());
        }, ticks));
    }

    private void villagersWrath(Player p) {
        if (!use(p, "mintus_wrath", "Village's Wrath", i("mintus.villagers-wrath.cooldown", 60))) return;
        double r = d("mintus.villagers-wrath.radius", 10);
        double dmg = d("mintus.villagers-wrath.damage", 15);
        double kb = d("mintus.villagers-wrath.knockback", 1.5);

        Location c = p.getLocation();
        int bolts = 0;
        for (LivingEntity le : targets(p, r, true)) {
            if (!(le instanceof Enemy)) continue; // uniquement les monstres
            hurt(le, dmg, p);
            le.setVelocity(away(c, le.getLocation(), kb, 0.5));
            if (bolts++ < 8) le.getWorld().strikeLightningEffect(le.getLocation());
        }
        ring(c, r, Particle.ANGRY_VILLAGER);
        c.getWorld().playSound(c, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.8f, 1.2f);
        p.sendActionBar(Msg.c("§cVillage's Wrath !"));
    }
}
