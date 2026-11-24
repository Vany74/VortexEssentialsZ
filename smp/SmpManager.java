package fr.vortexsmp.vortexessentials.smp;

import fr.vortexsmp.vortexessentials.Msg;
import fr.vortexsmp.vortexessentials.VortexEssentials;
import fr.vortexsmp.vortexessentials.YamlStore;
import fr.vortexsmp.vortexessentials.item.Category;
import fr.vortexsmp.vortexessentials.item.ItemUtil;
import fr.vortexsmp.vortexessentials.item.VortexItem;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Lancement du SMP (/smp start|stop). Tant que le SMP n'a pas commencé, puis pendant le délai suivant son lancement
 * (smp.weapon-delay-hours, 48 h par défaut), les armes légendaires et mythiques ne sont pas craftables : elles le
 * deviennent ensuite, avec leur rituel. L'état est conservé dans smp.yml.
 */
public final class SmpManager implements Listener {
    private final VortexEssentials plugin;
    private final YamlStore store;

    public SmpManager(VortexEssentials plugin) {
        this.plugin = plugin;
        this.store = new YamlStore(plugin, "smp.yml");
    }

    public boolean started() { return store.get().getBoolean("started", false); }
    public long startedAt() { return store.get().getLong("started-at", 0L); }

    public double delayHours() {
        return store.get().contains("delay-hours") ? store.get().getDouble("delay-hours") : plugin.getConfig().getDouble("smp.weapon-delay-hours", 48);
    }

    public long unlockAt() { return startedAt() + (long) (delayHours() * 3_600_000L); }

    public boolean locking() { return plugin.getConfig().getBoolean("smp.lock-weapons", true); }

    public boolean weaponsUnlocked() {
        if (!locking()) return true;
        return started() && System.currentTimeMillis() >= unlockAt();
    }

    public long remainingMs() { return Math.max(0, unlockAt() - System.currentTimeMillis()); }

    public void start() {
        store.get().set("started", true);
        store.get().set("started-at", System.currentTimeMillis());
        store.save();
    }

    public void stop() {
        store.get().set("started", false);
        store.save();
    }

    public void setDelay(double hours) {
        store.get().set("delay-hours", Math.max(0, hours));
        store.save();
    }

    public static String dur(long ms) {
        long s = ms / 1000, d = s / 86400, h = (s % 86400) / 3600, m = (s % 3600) / 60;
        if (d > 0) return d + "j " + h + "h " + m + "min";
        if (h > 0) return h + "h " + m + "min";
        return Math.max(1, m) + "min";
    }

    private static boolean restricted(ItemStack s) {
        VortexItem v = ItemUtil.get(s);
        return v != null && (v.isMythicWeapon() || v.category() == Category.LEGENDARY_WEAPON);
    }

    private String lockedMessage() {
        return started()
                ? "§cLes armes légendaires et mythiques seront craftables dans §f" + dur(remainingMs()) + "§c."
                : "§cLe SMP n'a pas encore commencé : les armes légendaires et mythiques ne sont pas craftables.";
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void prepare(PrepareItemCraftEvent e) {
        if (weaponsUnlocked() || !restricted(e.getInventory().getResult())) return;
        if (!(e.getView().getPlayer() instanceof Player p) || p.hasPermission("vortexessentials.admin.bypass")) return;
        e.getInventory().setResult(null);
        if (plugin.cooldowns().tryUse(p.getUniqueId(), "smp_lock", 2500)) p.sendActionBar(Msg.c(lockedMessage()));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void craft(CraftItemEvent e) {
        if (weaponsUnlocked() || !restricted(e.getCurrentItem())) return;
        if (!(e.getWhoClicked() instanceof Player p) || p.hasPermission("vortexessentials.admin.bypass")) return;
        e.setCancelled(true);
        Msg.prefixed(p, lockedMessage());
    }
}
