package fr.vortexsmp.vortexessentials.legendary;

import fr.vortexsmp.vortexessentials.Msg;
import fr.vortexsmp.vortexessentials.VortexEssentials;
import fr.vortexsmp.vortexessentials.item.ItemUtil;
import fr.vortexsmp.vortexessentials.item.VortexItem;
import fr.vortexsmp.vortexessentials.mythic.MythicEffectsTask;
import fr.vortexsmp.vortexessentials.util.AbilityCooldown;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.CaveSpider;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EvokerFangs;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Tameable;
import org.bukkit.entity.Villager;
import org.bukkit.entity.WanderingTrader;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Evoker Wand : sneak + clic gauche = crocs ; clic droit = renvoi des projectiles.
 * Sceptre of Arachne : sneak + clic gauche = empower ; clic droit = tir.
 */
public final class WandListener implements Listener {
    private final VortexEssentials plugin;
    private final Set<UUID> casting = new HashSet<>();
    private final Map<UUID, Long> empowered = new HashMap<>();
    /** Cave Spiders invoquées -> propriétaire. */
    private final Map<UUID, UUID> babies = new HashMap<>();
    private final List<Block> webs = new ArrayList<>();

    private BukkitTask webTask;

    public WandListener(VortexEssentials plugin) {
        this.plugin = plugin;
        // Sceptre en main : les toiles se dissipent avant même qu'on les touche
        webTask = Bukkit.getScheduler().runTaskTimer(plugin, this::dissolveWebsAround, 2L, 2L);
    }

    private void dissolveWebsAround() {
        double r = d("sceptre-of-arachne.web-radius", 2.0);
        int ri = (int) Math.ceil(r);
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (held(p) != VortexItem.SCEPTRE_OF_ARACHNE) continue;
            Location feet = p.getLocation();
            Vector ahead = feet.getDirection().setY(0);
            Location front = ahead.lengthSquared() > 0.001 ? feet.clone().add(ahead.normalize().multiply(1.5)) : feet;
            boolean any = false;
            for (Location centre : new Location[]{feet, front}) {
                World w = centre.getWorld();
                for (int dx = -ri; dx <= ri; dx++) {
                    for (int dy = -1; dy <= 2; dy++) {
                        for (int dz = -ri; dz <= ri; dz++) {
                            Block b = w.getBlockAt(centre.getBlockX() + dx, centre.getBlockY() + dy, centre.getBlockZ() + dz);
                            if (b.getType() != Material.COBWEB) continue;
                            if (b.getLocation().add(0.5, 0.5, 0.5).distanceSquared(centre.clone().add(0, 1, 0)) > (r + 1) * (r + 1)) continue;
                            dissolve(b);
                            any = true;
                        }
                    }
                }
            }
            if (any) p.getWorld().playSound(p.getLocation(), Sound.BLOCK_COBWEB_BREAK, 0.4f, 1.5f);
            // aura discrète autour du porteur
            p.getWorld().spawnParticle(Particle.WHITE_ASH, p.getLocation().add(0, 1, 0), 4, 0.4, 0.6, 0.4, 0.01);
        }
    }

    /** Fait disparaître une toile avec des particules (fibres + nuage). */
    private void dissolve(Block b) {
        b.setType(Material.AIR, false);
        webs.remove(b);
        Location l = b.getLocation().add(0.5, 0.5, 0.5);
        b.getWorld().spawnParticle(Particle.BLOCK, l, 12, 0.25, 0.25, 0.25, 0, Material.COBWEB.createBlockData());
        b.getWorld().spawnParticle(Particle.CLOUD, l, 3, 0.2, 0.2, 0.2, 0.01);
    }

    private double d(String path, double def) {
        return plugin.getConfig().getDouble("legendary." + path, def);
    }

    private VortexItem held(Player p) {
        ItemStack s = p.getInventory().getItemInMainHand();
        if (!MythicEffectsTask.relevant(s.getType())) return null;
        return ItemUtil.get(s);
    }

    // ═════════════ Déclencheurs ═════════════

    @EventHandler
    public void interact(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        Player p = e.getPlayer();
        VortexItem v = held(p);
        if (v != VortexItem.EVOKER_WAND && v != VortexItem.SCEPTRE_OF_ARACHNE) return;

        Action a = e.getAction();
        if (a == Action.LEFT_CLICK_AIR) {
            if (p.isSneaking()) leftAbility(p, v);
        } else if ((a == Action.RIGHT_CLICK_AIR || a == Action.RIGHT_CLICK_BLOCK) && !p.isSneaking()) {
            if (a == Action.RIGHT_CLICK_BLOCK && e.getClickedBlock() != null
                    && e.getClickedBlock().getType().isInteractable()) return;
            e.setCancelled(true);
            if (v == VortexItem.EVOKER_WAND) reflect(p);
            else shot(p);
        }
    }

    /** Sneak + frapper une entité déclenche aussi la capacité de gauche. */
    @EventHandler(ignoreCancelled = true)
    public void hit(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player p) || !p.isSneaking()) return;
        if (casting.contains(p.getUniqueId())) return;
        VortexItem v = held(p);
        if (v == VortexItem.EVOKER_WAND || v == VortexItem.SCEPTRE_OF_ARACHNE) leftAbility(p, v);
    }

    private void leftAbility(Player p, VortexItem v) {
        if (v == VortexItem.EVOKER_WAND) fangs(p);
        else empower(p);
    }

    // ═════════════ Evoker Wand ═════════════

    private void fangs(Player p) {
        if (!AbilityCooldown.use(plugin, p, "evoker_fangs", "Evoker Wand", d("evoker-wand.fangs-cooldown", 12))) return;

        final int length = (int) d("evoker-wand.fang-length", 8);
        final Location origin = p.getLocation().clone();
        Vector base = origin.getDirection().setY(0);
        if (base.lengthSquared() < 0.01) base = new Vector(0, 0, 1);
        base.normalize();

        final List<Vector> lines = new ArrayList<>();
        for (double deg : new double[]{-25, 0, 25}) lines.add(base.clone().rotateAroundY(Math.toRadians(deg)));

        p.getWorld().playSound(origin, Sound.ENTITY_EVOKER_CAST_SPELL, 1f, 1f);

        new BukkitRunnable() {
            int i = 1;

            @Override
            public void run() {
                if (i > length || !p.isOnline()) {
                    cancel();
                    return;
                }
                for (Vector dir : lines) {
                    Location l = ground(origin.clone().add(dir.clone().multiply(1.5 * i)));
                    l.getWorld().spawn(l, EvokerFangs.class, f -> f.setOwner(p));
                }
                i++;
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    /** Ajuste la hauteur pour que les crocs sortent du sol. */
    private Location ground(Location l) {
        World w = l.getWorld();
        int x = l.getBlockX(), z = l.getBlockZ(), y = l.getBlockY();
        for (int k = 0; k < 3 && w.getBlockAt(x, y, z).getType().isSolid(); k++) y++;
        for (int k = 0; k < 4 && !w.getBlockAt(x, y - 1, z).getType().isSolid(); k++) y--;
        Location g = l.clone();
        g.setY(y);
        return g;
    }

    private void reflect(Player p) {
        if (!AbilityCooldown.use(plugin, p, "evoker_reflect", "Evoker Wand", d("evoker-wand.reflect-cooldown", 25))) return;

        final double r = d("evoker-wand.reflect-radius", 5);
        final int ticks = (int) (d("evoker-wand.reflect-seconds", 8) * 20);
        final Set<UUID> done = new HashSet<>();

        p.getWorld().playSound(p.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 1f, 1.4f);
        p.sendActionBar(Msg.c("§5Barrière active §7(" + (ticks / 20) + "s)"));

        new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                if (!p.isOnline() || p.isDead() || t >= ticks) {
                    cancel();
                    return;
                }
                for (Entity en : p.getNearbyEntities(r, r, r)) {
                    if (!(en instanceof Projectile pr) || done.contains(pr.getUniqueId())) continue;
                    if (p.equals(pr.getShooter())) continue;
                    if (pr instanceof AbstractArrow aa && aa.isInBlock()) continue;
                    if (pr.getLocation().distanceSquared(p.getLocation()) > r * r) continue;

                    pr.setVelocity(pr.getVelocity().multiply(-1));
                    if (pr instanceof Fireball f) f.setDirection(f.getDirection().multiply(-1));
                    pr.setShooter(p);
                    done.add(pr.getUniqueId());
                    pr.getWorld().spawnParticle(Particle.END_ROD, pr.getLocation(), 8, 0.1, 0.1, 0.1, 0.05);
                }
                if (t % 10 == 0) {
                    Location c = p.getLocation();
                    for (int k = 0; k < 24; k++) {
                        double a = Math.PI * 2 * k / 24;
                        c.getWorld().spawnParticle(Particle.WITCH, c.getX() + Math.cos(a) * r, c.getY() + 1,
                                c.getZ() + Math.sin(a) * r, 1, 0, 0, 0, 0);
                    }
                }
                t += 2;
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    // ═════════════ Sceptre of Arachne ═════════════

    private void empower(Player p) {
        if (!AbilityCooldown.use(plugin, p, "arachne_empower", "Sceptre of Arachne",
                d("sceptre-of-arachne.empower-cooldown", 20))) return;
        empowered.put(p.getUniqueId(), System.currentTimeMillis() + 20_000L);
        p.getWorld().spawnParticle(Particle.WITCH, p.getLocation().add(0, 1, 0), 30, 0.5, 0.8, 0.5, 0.05);
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_SPIDER_AMBIENT, 1f, 0.6f);
        p.sendActionBar(Msg.c("§5Prochain tir empowered §7(20s) — clic droit"));
    }

    private void shot(Player p) {
        UUID id = p.getUniqueId();
        Long until = empowered.get(id);
        boolean power = until != null && System.currentTimeMillis() <= until;
        if (until != null && !power) empowered.remove(id);

        if (!power && !AbilityCooldown.use(plugin, p, "arachne_shot", "Sceptre of Arachne",
                d("sceptre-of-arachne.shot-cooldown", 2))) return;
        if (power) empowered.remove(id);

        double range = d("sceptre-of-arachne.range", 20);
        Location eye = p.getEyeLocation();
        Vector dir = eye.getDirection();
        World w = p.getWorld();

        RayTraceResult r = w.rayTrace(eye, dir, range, FluidCollisionMode.NEVER, true, 0.4,
                en -> !en.equals(p) && en instanceof LivingEntity && !(en instanceof ArmorStand));
        Location point = r != null ? r.getHitPosition().toLocation(w) : eye.clone().add(dir.clone().multiply(range));
        LivingEntity direct = r != null && r.getHitEntity() instanceof LivingEntity le ? le : null;

        double dist = eye.distance(point);
        for (double k = 1; k < dist; k += 0.8) {
            w.spawnParticle(Particle.WITCH, eye.clone().add(dir.clone().multiply(k)), 1, 0, 0, 0, 0);
        }
        w.playSound(eye, Sound.ENTITY_SPIDER_HURT, 1f, 1.4f);

        if (power) {
            placeWebs(point, (int) d("sceptre-of-arachne.web-radius", 2), (int) d("sceptre-of-arachne.web-seconds", 10));
            summonSpiders(p, point, direct);
            p.sendActionBar(Msg.c("§5Arachne vous répond..."));
        } else {
            if (direct != null) {
                casting.add(id);
                try {
                    direct.damage(d("sceptre-of-arachne.shot-damage", 4), p);
                } finally {
                    casting.remove(id);
                }
            }
            if (ThreadLocalRandom.current().nextDouble() * 100 < d("sceptre-of-arachne.web-chance", 25)) {
                placeWebs(direct != null ? direct.getLocation() : point, 0,
                        (int) d("sceptre-of-arachne.web-seconds", 10));
            }
        }
    }

    private void placeWebs(Location c, int radius, int seconds) {
        List<Block> placed = new ArrayList<>();
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    if (x * x + y * y + z * z > radius * radius + 1) continue;
                    Block b = c.getBlock().getRelative(x, y, z);
                    if (b.getType() == Material.AIR) {
                        b.setType(Material.COBWEB);
                        placed.add(b);
                    }
                }
            }
        }
        webs.addAll(placed);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (Block b : placed) if (b.getType() == Material.COBWEB) b.setType(Material.AIR);
            webs.removeAll(placed);
        }, seconds * 20L);
    }

    private void summonSpiders(Player owner, Location point, LivingEntity preferred) {
        LivingEntity target = preferred != null && validEnemy(owner, preferred) ? preferred : nearestEnemy(owner, point);
        int count = (int) d("sceptre-of-arachne.spiders", 6);
        long life = (long) (d("sceptre-of-arachne.spider-seconds", 15) * 20);

        for (int k = 0; k < count; k++) {
            Location l = point.clone().add(ThreadLocalRandom.current().nextDouble(-1.5, 1.5), 0.2,
                    ThreadLocalRandom.current().nextDouble(-1.5, 1.5));
            CaveSpider s = l.getWorld().spawn(l, CaveSpider.class, sp -> {
                sp.setLootTable(null);
                sp.customName(Msg.c("§5Bébé d'Arachne"));
                sp.setCustomNameVisible(false);
                sp.setRemoveWhenFarAway(true);
                if (target != null) sp.setTarget(target);
            });
            babies.put(s.getUniqueId(), owner.getUniqueId());
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                Entity en = Bukkit.getEntity(s.getUniqueId());
                if (en != null) en.remove();
                babies.remove(s.getUniqueId());
            }, life);
        }
    }

    private boolean validEnemy(Player owner, LivingEntity le) {
        if (le.equals(owner) || le instanceof ArmorStand || babies.containsKey(le.getUniqueId())) return false;
        if (le instanceof Villager || le instanceof WanderingTrader) return false;
        if (le instanceof Tameable t && t.isTamed() && owner.equals(t.getOwner())) return false;
        if (le instanceof Player o && (o.getGameMode() == GameMode.CREATIVE || o.getGameMode() == GameMode.SPECTATOR)) {
            return false;
        }
        return true;
    }

    private LivingEntity nearestEnemy(Player owner, Location point) {
        LivingEntity best = null;
        double bestD = Double.MAX_VALUE;
        for (LivingEntity le : point.getWorld().getNearbyLivingEntities(point, 10)) {
            if (!validEnemy(owner, le)) continue;
            double dd = le.getLocation().distanceSquared(point);
            if (dd < bestD) {
                bestD = dd;
                best = le;
            }
        }
        return best;
    }

    /** Les bébés ne ciblent jamais leur maître ni ses familiers. */
    @EventHandler(ignoreCancelled = true)
    public void retarget(EntityTargetLivingEntityEvent e) {
        UUID owner = babies.get(e.getEntity().getUniqueId());
        if (owner == null || e.getTarget() == null) return;
        LivingEntity t = e.getTarget();
        if (t.getUniqueId().equals(owner) || babies.containsKey(t.getUniqueId())
                || (t instanceof Tameable ta && ta.isTamed() && ta.getOwner() != null
                    && ta.getOwner().getUniqueId().equals(owner))) {
            e.setCancelled(true);
        }
    }

    /** Morsure d'un bébé : poison garanti (même en difficulté Facile) ; jamais sur le maître. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void bite(EntityDamageByEntityEvent e) {
        UUID owner = babies.get(e.getDamager().getUniqueId());
        if (owner == null || !(e.getEntity() instanceof LivingEntity victim)) return;
        if (victim.getUniqueId().equals(owner)) {
            e.setCancelled(true);
            return;
        }
        victim.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 120, 0));
    }

    /** Immunité aux toiles tant que le sceptre est en main (compensée par Speed I via MythicEffectsTask). */
    @EventHandler(ignoreCancelled = true)
    public void move(PlayerMoveEvent e) {
        if (!e.hasChangedBlock()) return;
        Block feet = e.getTo().getBlock();
        Block head = feet.getRelative(0, 1, 0);
        if (feet.getType() != Material.COBWEB && head.getType() != Material.COBWEB) return;
        if (held(e.getPlayer()) == VortexItem.SCEPTRE_OF_ARACHNE) MythicEffectsTask.stripWebs(e.getPlayer());
    }

    /** Appelé à l'arrêt du plugin : retire les toiles et les araignées restantes. */
    public void cleanup() {
        if (webTask != null) webTask.cancel();
        for (Block b : webs) if (b.getType() == Material.COBWEB) b.setType(Material.AIR);
        webs.clear();
        for (UUID id : new ArrayList<>(babies.keySet())) {
            Entity en = Bukkit.getEntity(id);
            if (en != null) en.remove();
        }
        babies.clear();
    }
}
