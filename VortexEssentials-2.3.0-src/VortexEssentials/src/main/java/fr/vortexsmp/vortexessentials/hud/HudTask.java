package fr.vortexsmp.vortexessentials.hud;

import fr.vortexsmp.vortexessentials.Keys;
import fr.vortexsmp.vortexessentials.Msg;
import fr.vortexsmp.vortexessentials.VortexEssentials;
import fr.vortexsmp.vortexessentials.item.ItemUtil;
import fr.vortexsmp.vortexessentials.item.VortexItem;
import fr.vortexsmp.vortexessentials.mythic.MythicEffectsTask;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;

/** Affiche dans l'action bar les cooldowns de l'arme tenue en main (désactivable : /vortex hud). */
public final class HudTask extends BukkitRunnable {
    private final VortexEssentials plugin;

    public HudTask(VortexEssentials plugin) {
        this.plugin = plugin;
    }

    public static boolean enabledFor(Player p) {
        Byte off = p.getPersistentDataContainer().get(Keys.HUD_OFF, PersistentDataType.BYTE);
        return off == null || off == 0;
    }

    public static void setEnabled(Player p, boolean on) {
        p.getPersistentDataContainer().set(Keys.HUD_OFF, PersistentDataType.BYTE, (byte) (on ? 0 : 1));
    }

    @Override
    public void run() {
        if (!plugin.getConfig().getBoolean("hud.cooldowns", true)) return;
        for (Player p : Bukkit.getOnlinePlayers()) {
            ItemStack main = p.getInventory().getItemInMainHand();
            if (!MythicEffectsTask.relevant(main.getType())) continue;
            VortexItem v = ItemUtil.get(main);
            List<AbilityInfo.Ability> abilities = AbilityInfo.of(v);
            if (abilities.isEmpty() || !enabledFor(p)) continue;

            List<String> parts = new ArrayList<>();
            for (AbilityInfo.Ability a : abilities) {
                long ms = plugin.cooldowns().remainingMs(p.getUniqueId(), a.key());
                parts.add(ms <= 0 ? "§a" + a.label() + " §2✔" : "§c" + a.label() + " §f" + (int) Math.ceil(ms / 1000.0) + "s");
            }
            p.sendActionBar(Msg.c(String.join(" §8| ", parts)));
        }
    }
}
