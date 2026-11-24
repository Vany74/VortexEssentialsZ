package fr.vortexsmp.vortexessentials.shop;

import fr.vortexsmp.vortexessentials.VortexEssentials;
import fr.vortexsmp.vortexessentials.YamlStore;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Annonces de la boutique joueurs (player-shop.yml). Les items sont retirés de l'inventaire du vendeur et gardés ici. */
public final class PlayerShop {
    public record Listing(long id, UUID seller, String sellerName, double price, ItemStack item, long created) {}

    private final VortexEssentials plugin;
    private final YamlStore store;
    private final Map<Long, Listing> listings = new LinkedHashMap<>();
    private long nextId = 1;

    public PlayerShop(VortexEssentials plugin) {
        this.plugin = plugin;
        this.store = new YamlStore(plugin, "player-shop.yml");
        load();
    }

    public void load() {
        store.reload();
        listings.clear();
        nextId = store.get().getLong("next-id", 1);
        ConfigurationSection ls = store.get().getConfigurationSection("listings");
        if (ls == null) return;
        for (String k : ls.getKeys(false)) {
            ConfigurationSection s = ls.getConfigurationSection(k);
            if (s == null) continue;
            try {
                ItemStack it = s.getItemStack("item");
                if (it == null) continue;
                long id = Long.parseLong(k);
                listings.put(id, new Listing(id, UUID.fromString(s.getString("seller", "")), s.getString("seller-name", "?"),
                        s.getDouble("price"), it, s.getLong("created")));
                nextId = Math.max(nextId, id + 1);
            } catch (Exception e) {
                plugin.getLogger().warning("player-shop.yml : annonce " + k + " ignorée (" + e.getMessage() + ")");
            }
        }
    }

    private void save() {
        store.get().set("next-id", nextId);
        store.get().set("listings", null);
        for (Listing l : listings.values()) {
            String p = "listings." + l.id();
            store.get().set(p + ".seller", l.seller().toString());
            store.get().set(p + ".seller-name", l.sellerName());
            store.get().set(p + ".price", l.price());
            store.get().set(p + ".created", l.created());
            store.get().set(p + ".item", l.item());
        }
        store.save();
    }

    public Listing get(long id) { return listings.get(id); }

    public int count(UUID seller) {
        int n = 0;
        for (Listing l : listings.values()) if (l.seller().equals(seller)) n++;
        return n;
    }

    public List<Listing> of(UUID seller) {
        List<Listing> out = new ArrayList<>();
        for (Listing l : listings.values()) if (l.seller().equals(seller)) out.add(l);
        return out;
    }

    public List<Listing> in(PlayerCategory c) {
        List<Listing> out = new ArrayList<>();
        for (Listing l : listings.values()) if (PlayerCategory.of(l.item()) == c) out.add(l);
        out.sort(Comparator.comparingDouble(Listing::price));
        return out;
    }

    public int countIn(PlayerCategory c) {
        int n = 0;
        for (Listing l : listings.values()) if (PlayerCategory.of(l.item()) == c) n++;
        return n;
    }

    public Listing add(Player seller, ItemStack item, double price) {
        Listing l = new Listing(nextId++, seller.getUniqueId(), seller.getName(), price, item.clone(), System.currentTimeMillis());
        listings.put(l.id(), l);
        save();
        return l;
    }

    /** Retire l'annonce (achat ou annulation). Renvoie null si elle n'existe plus (évite les doubles achats). */
    public Listing remove(long id) {
        Listing l = listings.remove(id);
        if (l != null) save();
        return l;
    }
}
