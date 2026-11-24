package fr.vortexsmp.vortexessentials.ritual;

import fr.vortexsmp.vortexessentials.Msg;
import fr.vortexsmp.vortexessentials.VortexEssentials;
import fr.vortexsmp.vortexessentials.YamlStore;
import org.bukkit.Bukkit;
import org.bukkit.Keyed;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Crafts à rituel : un craft personnalisé (/vortex admin craft) devient un craft à rituel avec /vortex ritual set <craft> <secondes>.
 * Quand le joueur clique sur le résultat, les ingrédients sont consommés, le rituel commence (particules, sons) puis,
 * après la durée prévue, l'item tombe par terre à l'endroit du rituel. Visible dans /vortex rituals.
 */
public final class RitualCrafts implements Listener {
    private final VortexEssentials plugin;
    private final YamlStore store;
    private final Map<UUID, Integer> running = new HashMap<>();

    public RitualCrafts(VortexEssentials plugin) {
        this.plugin = plugin;
        this.store = new YamlStore(plugin, "rituals.yml");
    }

    public int seconds(String craft) { return store.get().getInt("rituals." + craft, 0); }

    public void set(String craft, int seconds) {
        store.get().set("rituals." + craft, seconds <= 0 ? null : seconds);
        store.save();
    }

    public List<String> names() {
        ConfigurationSection cs = store.get().getConfigurationSection("rituals");
        return cs == null ? new ArrayList<>() : new ArrayList<>(cs.getKeys(false));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void craft(CraftItemEvent e) {
        Recipe r = e.getRecipe();
        if (!(r instanceof Keyed k) || !k.getKey().getNamespace().equals(plugin.getName().toLowerCase())) return;
        String key = k.getKey().getKey();
        if (!key.startsWith("custom_")) return;
        String craft = key.substring("custom_".length());
        int secs = seconds(craft);
        if (secs <= 0 || !(e.getWhoClicked() instanceof Player p)) return;

        e.setCancelled(true);
        if (e.isShiftClick()) {
            Msg.prefixed(p, "§cCliquez normalement sur le résultat pour lancer le rituel.");
            return;
        }
        int max = plugin.getConfig().getInt("rituals.max-concurrent", 1);
        if (running.getOrDefault(p.getUniqueId(), 0) >= max) {
            Msg.prefixed(p, "§cUn rituel est déjà en cours.");
            return;
        }
        CraftingInventory inv = e.getInventory();
        ItemStack[] matrix = inv.getMatrix();
        for (int i = 0; i < matrix.length; i++) {
            ItemStack s = matrix[i];
            if (s == null) continue;
            if (s.getAmount() <= 1) matrix[i] = null; else s.setAmount(s.getAmount() - 1);
        }
        inv.setMatrix(matrix);
        p.updateInventory();
        start(p, r.getResult().clone(), secs);
    }

    private void start(Player p, ItemStack result, int secs) {
        UUID id = p.getUniqueId();
        running.merge(id, 1, Integer::sum);
        Location at = p.getLocation().clone();
        World w = at.getWorld();
        Msg.prefixed(p, "§5Le rituel commence... §7restez à proximité (§f" + secs + "s§7).");
        w.playSound(at, Sound.BLOCK_BEACON_ACTIVATE, 1f, 0.6f);

        new BukkitRunnable() {
            int t = 0;
            final int total = secs * 20;

            @Override
            public void run() {
                if (t >= total) {
                    cancel();
                    running.merge(id, -1, Integer::sum);
                    Location drop = at.clone().add(0, 1, 0);
                    w.dropItemNaturally(drop, result);
                    w.strikeLightningEffect(at);
                    w.spawnParticle(Particle.END_ROD, drop, 60, 0.6, 0.8, 0.6, 0.15);
                    w.playSound(at, Sound.ENTITY_PLAYER_LEVELUP, 1f, 0.7f);
                    Player pl = Bukkit.getPlayer(id);
                    if (pl != null) Msg.prefixed(pl, "§aLe rituel est terminé !");
                    return;
                }
                double a = t * 0.35;
                for (int k = 0; k < 3; k++) {
                    double ang = a + k * Math.PI * 2 / 3;
                    double h = (t % 40) * 0.05;
                    w.spawnParticle(Particle.ENCHANT, at.getX() + Math.cos(ang) * 1.4, at.getY() + 0.2 + h, at.getZ() + Math.sin(ang) * 1.4, 2, 0, 0, 0, 0.3);
                }
                if (t % 20 == 0) {
                    w.spawnParticle(Particle.END_ROD, at.clone().add(0, 1, 0), 6, 0.4, 0.5, 0.4, 0.02);
                    Player pl = Bukkit.getPlayer(id);
                    if (pl != null) pl.sendActionBar(Msg.c("§5Rituel : §f" + Math.max(1, (total - t) / 20) + "s"));
                }
                if (t % 60 == 0) w.playSound(at, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 0.8f);
                t++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }
}
