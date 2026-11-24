package fr.vortexsmp.vortexessentials.limit;

import fr.vortexsmp.vortexessentials.VortexEssentials;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;

import java.util.Iterator;
import java.util.Map;

/** Applique les limites d'enchantements et de potions. */
public final class LimitListener implements Listener {
    private final VortexEssentials plugin;

    public LimitListener(VortexEssentials plugin) {
        this.plugin = plugin;
    }

    private boolean setting(String key) {
        return plugin.getConfig().getBoolean("limit." + key, true);
    }

    private boolean exempt(Player pl, ItemStack i) {
        return (pl != null && pl.hasPermission("vortexessentials.limit.bypass")) || plugin.bypass().isBypass(i);
    }

    /** Plafonne / retire les enchantements hors limites. Renvoie true si l'item a changé. */
    private boolean clean(Player pl, ItemStack i) {
        if (i == null || i.getType().isAir() || i.getEnchantments().isEmpty() || exempt(pl, i)) return false;
        boolean changed = false;
        for (Map.Entry<Enchantment, Integer> e : i.getEnchantments().entrySet()) {
            int allowed = plugin.limits().clamp(e.getKey(), e.getValue());
            if (allowed == e.getValue()) continue;
            i.removeEnchantment(e.getKey());
            if (allowed > 0) i.addUnsafeEnchantment(e.getKey(), allowed);
            changed = true;
        }
        return changed;
    }

    private void cleanPlayer(Player pl) {
        PlayerInventory inv = pl.getInventory();
        ItemStack[] c = inv.getContents();
        for (int s = 0; s < c.length; s++) {
            if (clean(pl, c[s])) inv.setItem(s, c[s]);
        }
    }

    private void cleanPlayerNextTick(Player pl) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (pl.isOnline()) cleanPlayer(pl);
        });
    }

    // ───────── Table d'enchantement ─────────

    @EventHandler(ignoreCancelled = true)
    public void enchant(EnchantItemEvent e) {
        if (!setting("enforce-on-enchanting") || exempt(e.getEnchanter(), e.getItem())) return;
        Iterator<Map.Entry<Enchantment, Integer>> it = e.getEnchantsToAdd().entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Enchantment, Integer> x = it.next();
            int allowed = plugin.limits().clamp(x.getKey(), x.getValue());
            if (allowed == 0) it.remove();
            else if (allowed != x.getValue()) x.setValue(allowed);
        }
    }

    // ───────── Enclume ─────────

    @EventHandler(ignoreCancelled = true)
    public void anvil(PrepareAnvilEvent e) {
        if (!setting("enforce-on-anvil")) return;
        ItemStack r = e.getResult();
        if (r == null || r.getType().isAir()) return;
        Player pl = e.getView().getPlayer() instanceof Player p ? p : null;
        if (clean(pl, r)) e.setResult(r);
    }

    // ───────── Inventaire ─────────

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void click(InventoryClickEvent e) {
        if (!setting("enforce-on-inventory")) return;
        if (e.getWhoClicked() instanceof Player pl) cleanPlayerNextTick(pl);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void drag(InventoryDragEvent e) {
        if (!setting("enforce-on-inventory")) return;
        if (e.getWhoClicked() instanceof Player pl) cleanPlayerNextTick(pl);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void pickup(EntityPickupItemEvent e) {
        if (!setting("enforce-on-inventory")) return;
        if (e.getEntity() instanceof Player pl) cleanPlayerNextTick(pl);
    }

    @EventHandler
    public void join(PlayerJoinEvent e) {
        if (setting("enforce-on-inventory")) cleanPlayerNextTick(e.getPlayer());
    }

    // ───────── Potions ─────────

    /**
     * Ne touche que les effets issus de potions bues/lancées, nuages et flèches teintées.
     * Les effets posés par des plugins (cause PLUGIN : Panacea, armes mythiques...) et les effets
     * naturels (balise, raid, warden...) ne sont jamais modifiés.
     */
    @EventHandler(ignoreCancelled = true)
    public void potion(EntityPotionEffectEvent e) {
        if (!setting("enforce-potions")) return;
        if (!(e.getEntity() instanceof Player pl)) return;
        if (pl.hasPermission("vortexessentials.limit.bypass")) return;

        switch (e.getCause()) {
            case POTION_DRINK, POTION_SPLASH, AREA_EFFECT_CLOUD, ARROW -> { }
            default -> { return; }
        }

        PotionEffect ne = e.getNewEffect();
        if (ne == null) return;

        PotionManager.PotionRule rule = plugin.potions().get(ne.getType().getKey().getKey());
        if (!rule.enabled()) {
            e.setCancelled(true);
            return;
        }

        int maxTicks = rule.maxDurationSeconds() * 20;
        int duration = ne.getDuration() < 0 ? maxTicks : Math.min(ne.getDuration(), maxTicks);
        int amplifier = Math.min(ne.getAmplifier(), rule.maxAmplifier());

        if (duration != ne.getDuration() || amplifier != ne.getAmplifier()) {
            e.setCancelled(true);
            if (duration > 0) {
                // Cause PLUGIN : pas de boucle, cet événement sera ignoré par ce listener
                pl.addPotionEffect(new PotionEffect(ne.getType(), duration, amplifier,
                        ne.isAmbient(), ne.hasParticles(), ne.hasIcon()));
            }
        }
    }
}
