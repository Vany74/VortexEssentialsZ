package fr.vortexsmp.vortexessentials.mythic;

import fr.vortexsmp.vortexessentials.Msg;
import fr.vortexsmp.vortexessentials.VortexEssentials;
import fr.vortexsmp.vortexessentials.item.ItemUtil;
import fr.vortexsmp.vortexessentials.item.VortexItem;
import fr.vortexsmp.vortexessentials.util.AbilityCooldown;
import fr.vortexsmp.vortexessentials.util.Effects;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
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
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.ArrayList;

/**
 * Armures mythiques (une pièce chacune) : effets portés, passif, capacité activée par un double sneak.
 *  - Colossus Plate (plastron) : Résistance I, Santé boostée II ; renvoie 20 % des dégâts de mêlée ; Fortress.
 *  - Phantom Treads (bottes) : Speed II, Jump Boost I ; aucun dégât de chute ; Shadow Step.
 *  - Radiant Crown (casque) : vision nocturne, respiration aquatique ; immunité cécité/obscurité/nausée ; Solar Flare.
 * Réglages : mythic-armors.<id> dans config.yml.
 */
public final class ArmorListener implements Listener {
    private static final Set<String> BLOCKED_BY_CROWN = Set.of("blindness", "darkness", "nausea");

    private final VortexEssentials plugin;
    private final NamespacedKey kbKey;
    private final Map<UUID, Long> lastSneak = new HashMap<>();
    private final Set<UUID> reflecting = new HashSet<>();

    public ArmorListener(VortexEssentials plugin) {
        this.plugin = plugin;
        this.kbKey = new NamespacedKey(plugin, "fortress_knockback");
        Bukkit.getScheduler().runTaskTimer(plugin, this::applyEffects, 20L, 20L);
        Bukkit.getScheduler().runTaskTimer(plugin, this::crownImmunity, 10L, 10L);
    }

    private double d(String path, double def) { return plugin.getConfig().getDouble("mythic-armors." + path, def); }

    private static VortexItem item(ItemStack s) {
        return s == null || s.getType().isAir() ? null : ItemUtil.get(s);
    }

    private static boolean wears(Player p, VortexItem v) {
        return item(p.getInventory().getHelmet()) == v || item(p.getInventory().getChestplate()) == v
                || item(p.getInventory().getLeggings()) == v || item(p.getInventory().getBoots()) == v;
    }

    private static PotionEffectType fx(String key) { return PotionEffectType.getByKey(NamespacedKey.minecraft(key)); }

    // ═════════════ Effets portés ═════════════

    /** niveau d'effet par nom (config mythic-armors.<id>.effects, sinon valeurs par défaut). */
    public static Map<String, Integer> levels(VortexItem v) {
        VortexEssentials pl = VortexEssentials.get();
        if (pl != null) {
            var s = pl.getConfig().getConfigurationSection("mythic-armors." + v.id().replace('_', '-') + ".effects");
            if (s != null) {
                Map<String, Integer> m = new LinkedHashMap<>();
                for (String k : s.getKeys(false)) if (s.getInt(k, 0) > 0) m.put(k.toLowerCase(), s.getInt(k));
                return m;
            }
        }
        Map<String, Integer> m = new LinkedHashMap<>();
        switch (v) {
            case COLOSSUS_PLATE -> { m.put("resistance", 1); m.put("health_boost", 4); m.put("haste", 2); }
            case PHANTOM_TREADS -> { m.put("speed", 3); m.put("fire_resistance", 1); m.put("haste", 3); }
            case RADIANT_CROWN -> { m.put("night_vision", 1); m.put("conduit_power", 1); m.put("haste", 5); }
            case EMBER_GREAVES -> { m.put("fire_resistance", 1); m.put("regeneration", 1); }
            default -> { }
        }
        return m;
    }

    /** Texte des effets portés pour le lore. */
    public static String describe(VortexItem v) {
        if (!v.isMythicArmor()) return "";
        List<String> parts = new ArrayList<>();
        for (Map.Entry<String, Integer> e : levels(v).entrySet()) {
            parts.add(Effects.label(e.getKey()) + " " + Effects.roman(e.getValue()));
        }
        return String.join("§7, §f", parts);
    }

    private void applyEffects() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            for (ItemStack piece : p.getInventory().getArmorContents()) {
                VortexItem v = item(piece);
                if (v == null || !v.isMythicArmor()) continue;
                for (Map.Entry<String, Integer> e : levels(v).entrySet()) {
                    PotionEffectType t = fx(e.getKey());
                    if (t == null) continue;
                    boolean night = e.getKey().equals("night_vision"); // durée longue : pas de clignotement
                    int dur = night ? 400 : 100;
                    PotionEffect cur = p.getPotionEffect(t);
                    if (cur != null && cur.getAmplifier() >= e.getValue() - 1 && cur.getDuration() > (night ? 260 : 60)) continue;
                    p.addPotionEffect(new PotionEffect(t, dur, e.getValue() - 1, true, false, false));
                }
            }
        }
    }

    /** Radiant Crown : retire cécité, obscurité et nausée. */
    private void crownImmunity() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (item(p.getInventory().getHelmet()) != VortexItem.RADIANT_CROWN) continue;
            for (String k : BLOCKED_BY_CROWN) {
                PotionEffectType t = fx(k);
                if (t != null && p.hasPotionEffect(t)) p.removePotionEffect(t);
            }
        }
    }

    // ═════════════ Passifs ═════════════

    /** Colossus Plate : renvoie une part des dégâts de mêlée. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void reflect(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player victim) || !(e.getDamager() instanceof LivingEntity attacker)) return;
        if (e.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK || attacker.equals(victim)) return;
        if (item(victim.getInventory().getChestplate()) != VortexItem.COLOSSUS_PLATE || reflecting.contains(victim.getUniqueId())) return;
        double pct = d("colossus-plate.reflect-percent", 20) / 100.0;
        double back = e.getFinalDamage() * pct;
        if (back <= 0) return;
        reflecting.add(victim.getUniqueId());
        try {
            attacker.damage(back, victim);
        } finally {
            reflecting.remove(victim.getUniqueId());
        }
    }

    /** Ember Greaves : immunité au feu et à la lave ; les attaquants prennent feu. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void fireImmune(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p) || item(p.getInventory().getLeggings()) != VortexItem.EMBER_GREAVES) return;
        EntityDamageEvent.DamageCause c = e.getCause();
        if (c == EntityDamageEvent.DamageCause.FIRE || c == EntityDamageEvent.DamageCause.FIRE_TICK
                || c == EntityDamageEvent.DamageCause.LAVA || c == EntityDamageEvent.DamageCause.HOT_FLOOR) {
            e.setCancelled(true);
            p.setFireTicks(0);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void emberHit(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player victim) || !(e.getDamager() instanceof LivingEntity attacker) || attacker.equals(victim)) return;
        if (e.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK) return;
        if (item(victim.getInventory().getLeggings()) == VortexItem.EMBER_GREAVES) attacker.setFireTicks((int) (d("ember-greaves.burn-seconds", 4) * 20));
    }

    /** Phantom Treads : aucun dégât de chute. */
    @EventHandler(ignoreCancelled = true)
    public void fall(EntityDamageEvent e) {
        if (e.getCause() == EntityDamageEvent.DamageCause.FALL && e.getEntity() instanceof Player p
                && item(p.getInventory().getBoots()) == VortexItem.PHANTOM_TREADS) {
            e.setCancelled(true);
        }
    }

    // ═════════════ Capacités : double sneak ═════════════

    @EventHandler
    public void sneak(PlayerToggleSneakEvent e) {
        if (!e.isSneaking()) return;
        Player p = e.getPlayer();
        long now = System.currentTimeMillis();
        Long last = lastSneak.put(p.getUniqueId(), now);
        if (last == null || now - last > 450) return;
        lastSneak.remove(p.getUniqueId());
        if (wears(p, VortexItem.COLOSSUS_PLATE)) fortress(p);
        if (wears(p, VortexItem.PHANTOM_TREADS)) shadowStep(p);
        if (wears(p, VortexItem.RADIANT_CROWN)) solarFlare(p);
        if (wears(p, VortexItem.EMBER_GREAVES)) flameNova(p);
    }

    @EventHandler
    public void quit(PlayerQuitEvent e) { lastSneak.remove(e.getPlayer().getUniqueId()); }

    private boolean validTarget(Player p, LivingEntity t) {
        if (t.equals(p) || t instanceof ArmorStand || t instanceof Villager || t instanceof WanderingTrader) return false;
        if (t instanceof Tameable ta && ta.isTamed() && p.equals(ta.getOwner())) return false;
        return !(t instanceof Player o && (o.getGameMode() == GameMode.CREATIVE || o.getGameMode() == GameMode.SPECTATOR));
    }

    /** Fortress : Résistance IV, aucun recul, onde de choc qui repousse les ennemis. */
    private void fortress(Player p) {
        if (!AbilityCooldown.use(plugin, p, "colossus_fortress", "Fortress", d("colossus-plate.fortress.cooldown", 60))) return;
        int ticks = (int) (d("colossus-plate.fortress.seconds", 6) * 20);
        PotionEffectType res = fx("resistance"), slow = fx("slowness");
        if (res != null) p.addPotionEffect(new PotionEffect(res, ticks, 3, true, true, true));
        if (slow != null) p.addPotionEffect(new PotionEffect(slow, ticks, 0, true, false, false));
        AttributeInstance kb = p.getAttribute(Attribute.KNOCKBACK_RESISTANCE);
        if (kb != null) {
            if (kb.getModifier(kbKey) != null) kb.removeModifier(kbKey);
            kb.addTransientModifier(new AttributeModifier(kbKey, 1.0, AttributeModifier.Operation.ADD_NUMBER));
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                AttributeInstance a2 = p.getAttribute(Attribute.KNOCKBACK_RESISTANCE);
                if (a2 != null) a2.removeModifier(kbKey);
            }, ticks);
        }
        double r = d("colossus-plate.fortress.radius", 5), dmg = d("colossus-plate.fortress.damage", 4);
        Location c = p.getLocation();
        for (Entity en : p.getNearbyEntities(r, r, r)) {
            if (!(en instanceof LivingEntity t) || !validTarget(p, t)) continue;
            t.damage(dmg, p);
            Vector away = t.getLocation().toVector().subtract(c.toVector()).setY(0);
            if (away.lengthSquared() < 0.01) away = new Vector(0, 0, 1);
            t.setVelocity(away.normalize().multiply(1.1).setY(0.5));
        }
        for (int k = 0; k < 40; k++) {
            double ang = Math.PI * 2 * k / 40;
            c.getWorld().spawnParticle(Particle.CLOUD, c.getX() + Math.cos(ang) * r, c.getY() + 0.2, c.getZ() + Math.sin(ang) * r, 1, 0, 0.05, 0, 0.02);
        }
        c.getWorld().playSound(c, Sound.BLOCK_ANVIL_LAND, 1f, 0.6f);
        p.sendActionBar(Msg.c("§7Fortress !"));
    }

    /** Shadow Step : dash en avant et invisibilité. */
    private void shadowStep(Player p) {
        Vector dir = p.getLocation().getDirection().setY(0);
        if (dir.lengthSquared() < 0.001) return;
        if (!AbilityCooldown.use(plugin, p, "phantom_step", "Shadow Step", d("phantom-treads.shadow-step.cooldown", 25))) return;
        Location from = p.getLocation().add(0, 0.5, 0);
        p.setVelocity(dir.normalize().multiply(d("phantom-treads.shadow-step.power", 2.2)).setY(0.25));
        PotionEffectType inv = fx("invisibility");
        if (inv != null) p.addPotionEffect(new PotionEffect(inv, (int) (d("phantom-treads.shadow-step.invisibility-seconds", 3) * 20), 0, true, false, false));
        from.getWorld().spawnParticle(Particle.SQUID_INK, from, 30, 0.3, 0.5, 0.3, 0.05);
        from.getWorld().playSound(from, Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 0.6f);
        p.sendActionBar(Msg.c("§8Shadow Step !"));
    }

    /** Flame Nova : enflamme, blesse et repousse les ennemis proches. */
    private void flameNova(Player p) {
        if (!AbilityCooldown.use(plugin, p, "ember_nova", "Flame Nova", d("ember-greaves.flame-nova.cooldown", 40))) return;
        double r = d("ember-greaves.flame-nova.radius", 6), dmg = d("ember-greaves.flame-nova.damage", 5);
        int fire = (int) (d("ember-greaves.flame-nova.fire-seconds", 6) * 20);
        Location c = p.getLocation();
        for (Entity en : p.getNearbyEntities(r, r, r)) {
            if (!(en instanceof LivingEntity t) || !validTarget(p, t) || t.getLocation().distanceSquared(c) > r * r) continue;
            t.damage(dmg, p);
            t.setFireTicks(fire);
            Vector away = t.getLocation().toVector().subtract(c.toVector()).setY(0);
            if (away.lengthSquared() < 0.01) away = new Vector(0, 0, 1);
            t.setVelocity(away.normalize().multiply(1.0).setY(0.5));
        }
        for (int k = 0; k < 48; k++) {
            double ang = Math.PI * 2 * k / 48;
            c.getWorld().spawnParticle(Particle.FLAME, c.getX() + Math.cos(ang) * r, c.getY() + 0.3, c.getZ() + Math.sin(ang) * r, 2, 0, 0.1, 0, 0.02);
        }
        c.getWorld().spawnParticle(Particle.LAVA, c.clone().add(0, 0.5, 0), 25, r / 4, 0.3, r / 4, 0);
        c.getWorld().playSound(c, Sound.ITEM_FIRECHARGE_USE, 1f, 0.6f);
        p.sendActionBar(Msg.c("§6Flame Nova !"));
    }

    /** Solar Flare : aveugle et révèle les ennemis proches, soigne le porteur. */
    private void solarFlare(Player p) {
        if (!AbilityCooldown.use(plugin, p, "radiant_flare", "Solar Flare", d("radiant-crown.solar-flare.cooldown", 45))) return;
        double r = d("radiant-crown.solar-flare.radius", 8);
        int blind = (int) (d("radiant-crown.solar-flare.blind-seconds", 3) * 20);
        int glow = (int) (d("radiant-crown.solar-flare.glow-seconds", 8) * 20);
        PotionEffectType blindness = fx("blindness"), glowing = fx("glowing");
        for (Entity en : p.getNearbyEntities(r, r, r)) {
            if (!(en instanceof LivingEntity t) || !validTarget(p, t)) continue;
            if (blindness != null) t.addPotionEffect(new PotionEffect(blindness, blind, 0));
            if (glowing != null) t.addPotionEffect(new PotionEffect(glowing, glow, 0));
        }
        AttributeInstance mh = p.getAttribute(Attribute.MAX_HEALTH);
        double max = mh == null ? 20 : mh.getValue();
        p.setHealth(Math.min(max, p.getHealth() + d("radiant-crown.solar-flare.heal", 6)));
        Location c = p.getLocation().add(0, 1, 0);
        for (int k = 0; k < 48; k++) {
            double ang = Math.PI * 2 * k / 48;
            c.getWorld().spawnParticle(Particle.END_ROD, c.getX() + Math.cos(ang) * r * 0.6, c.getY(), c.getZ() + Math.sin(ang) * r * 0.6, 1, 0, 0.1, 0, 0.02);
        }
        c.getWorld().spawnParticle(Particle.END_ROD, c, 40, 0.5, 0.8, 0.5, 0.15);
        c.getWorld().playSound(c, Sound.BLOCK_BEACON_ACTIVATE, 1f, 1.4f);
        p.sendActionBar(Msg.c("§eSolar Flare !"));
    }
}
