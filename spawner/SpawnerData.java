package fr.vortexsmp.vortexessentials.spawner;

import org.bukkit.Material;
import org.bukkit.entity.EntityType;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Un spawner posé : pile, propriétaire, joueurs autorisés, améliorations, stockage. */
public final class SpawnerData {
    public String key;
    public String world;
    public int x, y, z;
    public EntityType type;
    public int stack = 1;
    public UUID owner;
    public String ownerName = "?";
    public final Set<UUID> trusted = new HashSet<>();
    public int speed, range, qty;
    public boolean autoSell;
    public long exp;
    public final Map<Material, Long> drops = new LinkedHashMap<>();
    public long nextSpawn;
    public UUID holo;
    public long lastHolo;

    public long storedItems() {
        long n = 0;
        for (long v : drops.values()) n += v;
        return n;
    }

    public boolean allowed(UUID id) { return id.equals(owner) || trusted.contains(id); }
}
