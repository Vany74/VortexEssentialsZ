package fr.vortexsmp.vortexessentials.menu;

import fr.vortexsmp.vortexessentials.item.Category;
import fr.vortexsmp.vortexessentials.item.Feature;
import fr.vortexsmp.vortexessentials.item.VortexItem;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.List;

/** Identifie un inventaire du plugin et porte son état (page, recherche, catégorie...). */
public final class MenuHolder implements InventoryHolder {
    public enum Type {
        HUB, CATEGORY, LIMITS, ENCHANTS, POTIONS, OBJECTS, RECIPE, FEATURES, FEATURE, BOOSTS, TOOL, PROFILE,
        ADMIN, ADMIN_LIST, ADMIN_POTIONS, CRAFT_EDIT, CITEMS, BOOST_EDIT,
        SHOP_HUB, SHOP_CATS, SHOP_ITEMS, SHOP_EDIT, PSHOP_CATS, PSHOP_LIST, PSHOP_MINE, SELL_BOX;

        /** Menus gérés par ShopMenus. */
        public boolean shop() {
            return this == SHOP_HUB || this == SHOP_CATS || this == SHOP_ITEMS || this == SHOP_EDIT
                    || this == PSHOP_CATS || this == PSHOP_LIST || this == PSHOP_MINE || this == SELL_BOX;
        }

        /** Menus gérés par AdminMenuManager. */
        public boolean admin() {
            return this == ADMIN || this == ADMIN_LIST || this == ADMIN_POTIONS || this == CRAFT_EDIT || this == CITEMS || this == BOOST_EDIT;
        }
    }

    private final Type type;
    private Inventory inventory;
    private Category category;
    private Feature feature;
    private VortexItem item;
    private int page;
    private String query = "";
    private String kind = "";
    private String name = "";
    private boolean shapeless;
    private List<String> entries = List.of();

    public MenuHolder(Type type) { this.type = type; }

    public void bind(Inventory inventory) { this.inventory = inventory; }

    @Override
    public Inventory getInventory() { return inventory; }

    public Type type() { return type; }
    public Category category() { return category; }
    public void category(Category c) { this.category = c; }
    public Feature feature() { return feature; }
    public void feature(Feature f) { this.feature = f; }
    public VortexItem item() { return item; }
    public void item(VortexItem i) { this.item = i; }
    public int page() { return page; }
    public void page(int p) { this.page = p; }
    public String query() { return query; }
    public void query(String q) { this.query = q == null ? "" : q; }
    public String kind() { return kind; }
    public void kind(String k) { this.kind = k; }
    public String name() { return name; }
    public void name(String n) { this.name = n; }
    public boolean shapeless() { return shapeless; }
    public void shapeless(boolean s) { this.shapeless = s; }
    public List<String> entries() { return entries; }
    public void entries(List<String> e) { this.entries = e; }
}
