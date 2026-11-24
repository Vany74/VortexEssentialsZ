package fr.vortexsmp.vortexessentials.mythic;

import fr.vortexsmp.vortexessentials.YamlStore;
import fr.vortexsmp.vortexessentials.item.VortexItem;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/** Registre persistant (mythic-data.yml) des armes mythiques déjà forgées. */
public final class MythicRegistry {
    private final YamlStore store;

    public MythicRegistry(Plugin plugin) {
        this.store = new YamlStore(plugin, "mythic-data.yml");
    }

    public void reload() { store.reload(); }

    public boolean isForged(VortexItem v) {
        return store.get().contains("forged." + v.id());
    }

    public void markForged(VortexItem v, Player by) {
        String p = "forged." + v.id();
        store.get().set(p + ".by", by.getUniqueId().toString());
        store.get().set(p + ".name", by.getName());
        store.get().set(p + ".at", System.currentTimeMillis());
        store.save();
    }

    /** Libère une arme mythique (ex: perdue dans la lave). Renvoie false si elle n'était pas forgée. */
    public boolean reset(VortexItem v) {
        if (!isForged(v)) return false;
        store.get().set("forged." + v.id(), null);
        store.save();
        return true;
    }

    /** Nom du forgeron, ou null. */
    public String forgedBy(VortexItem v) {
        return store.get().getString("forged." + v.id() + ".name");
    }
}
