package fr.vortexsmp.vortexessentials;

import fr.vortexsmp.vortexessentials.boost.BoostListener;
import fr.vortexsmp.vortexessentials.boost.BoostManager;
import fr.vortexsmp.vortexessentials.command.LimitCommand;
import fr.vortexsmp.vortexessentials.consumable.ConsumableListener;
import fr.vortexsmp.vortexessentials.recipe.RecipeListener;
import fr.vortexsmp.vortexessentials.recipe.RecipeManager;
import fr.vortexsmp.vortexessentials.command.VortexCommand;
import fr.vortexsmp.vortexessentials.legendary.LegendaryListener;
import fr.vortexsmp.vortexessentials.legendary.WandListener;
import fr.vortexsmp.vortexessentials.limit.BypassManager;
import fr.vortexsmp.vortexessentials.limit.LimitListener;
import fr.vortexsmp.vortexessentials.limit.LimitManager;
import fr.vortexsmp.vortexessentials.limit.PotionManager;
import fr.vortexsmp.vortexessentials.menu.MenuManager;
import fr.vortexsmp.vortexessentials.mythic.MythicAbilities;
import fr.vortexsmp.vortexessentials.admin.AdminListener;
import fr.vortexsmp.vortexessentials.admin.AdminManager;
import fr.vortexsmp.vortexessentials.admin.AdminMenuManager;
import fr.vortexsmp.vortexessentials.admin.CustomItemListener;
import fr.vortexsmp.vortexessentials.admin.CustomItems;
import fr.vortexsmp.vortexessentials.admin.AdminDefaults;
import fr.vortexsmp.vortexessentials.admin.CustomCraftLimits;
import fr.vortexsmp.vortexessentials.bounty.BountyCommand;
import fr.vortexsmp.vortexessentials.bounty.BountyManager;
import fr.vortexsmp.vortexessentials.bounty.BountyMenus;
import fr.vortexsmp.vortexessentials.legendary.MaceLimits;
import fr.vortexsmp.vortexessentials.menu.ServicesMenu;
import fr.vortexsmp.vortexessentials.orders.OrderCommand;
import fr.vortexsmp.vortexessentials.orders.OrderManager;
import fr.vortexsmp.vortexessentials.orders.OrderMenus;
import fr.vortexsmp.vortexessentials.recipe.RecipeEditor;
import fr.vortexsmp.vortexessentials.shop.ShopEditor;
import fr.vortexsmp.vortexessentials.ritual.RitualCrafts;
import fr.vortexsmp.vortexessentials.ritual.RitualMenu;
import fr.vortexsmp.vortexessentials.serveradmin.DimensionManager;
import fr.vortexsmp.vortexessentials.serveradmin.ServerAdminMenu;
import fr.vortexsmp.vortexessentials.spawner.SpawnerCommand;
import fr.vortexsmp.vortexessentials.spawner.SpawnerManager;
import fr.vortexsmp.vortexessentials.spawner.SpawnerMenus;
import fr.vortexsmp.vortexessentials.staffmod.StaffMod;
import fr.vortexsmp.vortexessentials.staffmod.StaffModCommand;
import fr.vortexsmp.vortexessentials.staffmod.StaffModMenu;
import fr.vortexsmp.vortexessentials.admin.CustomRecipes;
import fr.vortexsmp.vortexessentials.admin.WaterBottleGuard;
import fr.vortexsmp.vortexessentials.command.AnnounceCommand;
import fr.vortexsmp.vortexessentials.maintenance.MaintenanceCommand;
import fr.vortexsmp.vortexessentials.maintenance.MaintenanceManager;
import fr.vortexsmp.vortexessentials.ritual.CraftRitual;
import fr.vortexsmp.vortexessentials.smp.SmpCommand;
import fr.vortexsmp.vortexessentials.smp.SmpManager;
import fr.vortexsmp.vortexessentials.staff.StaffCommands;
import fr.vortexsmp.vortexessentials.staff.StaffManager;
import fr.vortexsmp.vortexessentials.staff.StaffXray;
import fr.vortexsmp.vortexessentials.discord.DiscordListener;
import fr.vortexsmp.vortexessentials.discord.DiscordWebhook;
import fr.vortexsmp.vortexessentials.hud.HudTask;
import fr.vortexsmp.vortexessentials.item.UnmodifiableListener;
import fr.vortexsmp.vortexessentials.legendary.LegendaryLimits;
import fr.vortexsmp.vortexessentials.mythic.ArmorListener;
import fr.vortexsmp.vortexessentials.mythic.MythicRangedListener;
import fr.vortexsmp.vortexessentials.mythic.SecretWeaponListener;
import fr.vortexsmp.vortexessentials.playtime.PlaytimeCommand;
import fr.vortexsmp.vortexessentials.playtime.PlaytimeManager;
import fr.vortexsmp.vortexessentials.lang.Lang;
import fr.vortexsmp.vortexessentials.pack.PackManager;
import fr.vortexsmp.vortexessentials.shop.Money;
import fr.vortexsmp.vortexessentials.shop.PlayerShop;
import fr.vortexsmp.vortexessentials.shop.ShopCommand;
import fr.vortexsmp.vortexessentials.shop.ShopManager;
import fr.vortexsmp.vortexessentials.shop.ShopMenus;
import fr.vortexsmp.vortexessentials.tools.ToolSettings;
import fr.vortexsmp.vortexessentials.tools.ToolSettingsListener;
import fr.vortexsmp.vortexessentials.mythic.MythicEffectsTask;
import fr.vortexsmp.vortexessentials.mythic.MythicListener;
import fr.vortexsmp.vortexessentials.mythic.MythicToolListener;
import fr.vortexsmp.vortexessentials.tools.ToolListener;
import fr.vortexsmp.vortexessentials.mythic.MythicRegistry;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * VortexEssentials — fusion de VortexTools (items custom) et VortexLimit (limites).
 * Auteur : ItsVany
 */
public final class VortexEssentials extends JavaPlugin {

    private static VortexEssentials instance;

    private YamlStore limitStore;
    private CooldownManager cooldowns;
    private MythicRegistry mythic;
    private LimitManager limits;
    private PotionManager potions;
    private BypassManager bypass;
    private MenuManager menu;
    private BoostManager boosts;
    private RecipeManager recipes;
    private AdminManager admin;
    private AdminMenuManager adminMenus;
    private CustomRecipes customRecipes;
    private CustomItems customItems;
    private MythicEffectsTask effectsTask;
    private PlaytimeManager playtime;
    private MaintenanceManager maintenance;
    private SmpManager smp;
    private MaceLimits maceLimits;
    private RitualCrafts rituals;
    private RitualMenu ritualMenu;
    private RecipeEditor recipeEditor;
    private OrderManager orders;
    private OrderMenus orderMenus;
    private DimensionManager dimensions;
    private ServerAdminMenu serverAdminMenu;
    private SpawnerManager spawners;
    private SpawnerMenus spawnerMenus;
    private StaffMod staffMod;
    private StaffModMenu staffModMenu;
    private BountyManager bounties;
    private BountyMenus bountyMenus;
    private ServicesMenu servicesMenu;
    private CustomCraftLimits craftLimits;
    private ShopEditor shopEditor;
    private StaffManager staff;
    private StaffXray staffXray;
    private LegendaryLimits legendaryLimits;
    private DiscordWebhook discord;
    private PackManager pack;
    private Money money;
    private ShopManager shop;
    private PlayerShop playerShop;
    private ShopMenus shopMenus;
    private WandListener wands;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        Keys.init(this);
        ToolSettings.init(this);
        Lang.load(this);

        cooldowns = new CooldownManager();
        mythic = new MythicRegistry(this);

        limitStore = new YamlStore(this, "limits.yml");
        limits = new LimitManager(limitStore);
        potions = new PotionManager(limitStore);
        bypass = new BypassManager(this, limitStore);

        boosts = new BoostManager(this);
        recipes = new RecipeManager(this);
        recipes.load();
        admin = new AdminManager(this);
        customRecipes = new CustomRecipes(this);
        customItems = new CustomItems(this);
        adminMenus = new AdminMenuManager(this);
        discord = new DiscordWebhook(this);
        pack = new PackManager(this);
        money = new Money(this);
        shop = new ShopManager(this);
        playerShop = new PlayerShop(this);
        shopMenus = new ShopMenus(this);
        playtime = new PlaytimeManager(this);
        maintenance = new MaintenanceManager(this);
        smp = new SmpManager(this);
        maceLimits = new MaceLimits(this);
        rituals = new RitualCrafts(this);
        ritualMenu = new RitualMenu(this);
        recipeEditor = new RecipeEditor(this);
        orders = new OrderManager(this);
        orderMenus = new OrderMenus(this);
        dimensions = new DimensionManager(this);
        serverAdminMenu = new ServerAdminMenu(this);
        spawners = new SpawnerManager(this);
        spawnerMenus = new SpawnerMenus(this);
        staffMod = new StaffMod(this);
        staffModMenu = new StaffModMenu(this);
        bounties = new BountyManager(this);
        bountyMenus = new BountyMenus(this);
        servicesMenu = new ServicesMenu(this);
        craftLimits = new CustomCraftLimits(this);
        shopEditor = new ShopEditor(this);
        staff = new StaffManager(this);
        staffXray = new StaffXray(this);
        legendaryLimits = new LegendaryLimits(this);
        menu = new MenuManager(this);
        wands = new WandListener(this);

        getServer().getPluginManager().registerEvents(menu, this);
        getServer().getPluginManager().registerEvents(new MythicListener(this), this);
        getServer().getPluginManager().registerEvents(new LimitListener(this), this);
        getServer().getPluginManager().registerEvents(new MythicAbilities(this), this);
        getServer().getPluginManager().registerEvents(new MythicToolListener(this), this);
        getServer().getPluginManager().registerEvents(new ToolListener(this), this);
        getServer().getPluginManager().registerEvents(new LegendaryListener(this), this);
        getServer().getPluginManager().registerEvents(wands, this);
        getServer().getPluginManager().registerEvents(new BoostListener(this), this);
        getServer().getPluginManager().registerEvents(new RecipeListener(this), this);
        getServer().getPluginManager().registerEvents(new ConsumableListener(this), this);
        getServer().getPluginManager().registerEvents(new ToolSettingsListener(this), this);
        getServer().getPluginManager().registerEvents(adminMenus, this);
        getServer().getPluginManager().registerEvents(new AdminListener(this), this);
        getServer().getPluginManager().registerEvents(new CustomItemListener(this), this);
        effectsTask = new MythicEffectsTask(this);
        effectsTask.runTaskTimer(this, 20L, 10L);
        new HudTask(this).runTaskTimer(this, 20L, 10L);
        getServer().getPluginManager().registerEvents(pack, this);
        getServer().getPluginManager().registerEvents(shopMenus, this);
        getServer().getPluginManager().registerEvents(playtime, this);
        getServer().getPluginManager().registerEvents(legendaryLimits, this);
        getServer().getPluginManager().registerEvents(new MythicRangedListener(this), this);
        getServer().getPluginManager().registerEvents(new SecretWeaponListener(this), this);
        getServer().getPluginManager().registerEvents(new fr.vortexsmp.vortexessentials.mythic.SpearListener(this), this);
        getServer().getPluginManager().registerEvents(new ArmorListener(this), this);
        getServer().getPluginManager().registerEvents(maintenance, this);
        getServer().getPluginManager().registerEvents(smp, this);
        for (org.bukkit.event.Listener l : new org.bukkit.event.Listener[]{maceLimits, rituals, ritualMenu, recipeEditor, orderMenus, dimensions,
                serverAdminMenu, spawners, spawnerMenus, staffMod, staffModMenu, bounties, bountyMenus, servicesMenu, craftLimits, shopEditor}) {
            getServer().getPluginManager().registerEvents(l, this);
        }
        getServer().getPluginManager().registerEvents(staff, this);
        getServer().getPluginManager().registerEvents(staffXray, this);
        getServer().getPluginManager().registerEvents(new CraftRitual(this), this);
        getServer().getPluginManager().registerEvents(new WaterBottleGuard(), this);
        getServer().getPluginManager().registerEvents(new UnmodifiableListener(), this);
        getServer().getPluginManager().registerEvents(new DiscordListener(this), this);
        if (discord.event("server-start")) {
            getServer().getScheduler().runTask(this, () ->
                    discord.announce("Serveur démarré", "Le serveur est en ligne.", 0x57F287));
        }

        LimitCommand limitCmd = new LimitCommand(this);
        VortexCommand vortexCmd = new VortexCommand(this, limitCmd);
        bind("vortex", vortexCmd, vortexCmd);
        bind("vortexlimit", limitCmd, limitCmd);
        ShopCommand shopCmd = new ShopCommand(this);
        bind("shop", shopCmd, shopCmd);
        bind("sell", shopCmd, shopCmd);
        PlaytimeCommand playtimeCmd = new PlaytimeCommand(this);
        bind("playtime", playtimeCmd, playtimeCmd);
        AnnounceCommand announceCmd = new AnnounceCommand(this);
        bind("announce", announceCmd, announceCmd);
        MaintenanceCommand maintenanceCmd = new MaintenanceCommand(this);
        bind("maintenance", maintenanceCmd, maintenanceCmd);
        SmpCommand smpCmd = new SmpCommand(this);
        bind("smp", smpCmd, smpCmd);
        OrderCommand orderCmd = new OrderCommand(this);
        bind("orders", orderCmd, orderCmd);
        bind("servermenu", serverAdminMenu, null);
        SpawnerCommand spawnerCmd = new SpawnerCommand(this);
        bind("uspawner", spawnerCmd, spawnerCmd);
        StaffModCommand staffModCmd = new StaffModCommand(this);
        bind("staffmod", staffModCmd, staffModCmd);
        BountyCommand bountyCmd = new BountyCommand(this);
        bind("bounty", bountyCmd, bountyCmd);
        StaffCommands staffCmds = new StaffCommands(this);
        for (String c : new String[]{"jail", "unjail", "setjail", "deljail", "jails", "freeze", "staffxray"}) bind(c, staffCmds, staffCmds);
        getServer().getScheduler().runTask(this, () -> AdminDefaults.installOnce(this)); // limites, items désactivés et crafts par défaut (une fois)

        getLogger().info("VortexEssentials " + getPluginMeta().getVersion() + " by ItsVany enabled.");
    }

    @Override
    public void onDisable() {
        if (wands != null) wands.cleanup();
        if (playtime != null) playtime.save();
        if (staffXray != null) staffXray.clearAll();
        if (spawners != null) spawners.shutdown();
        if (staffMod != null) staffMod.shutdown();
        if (discord != null) {
            if (discord.event("server-stop")) discord.announceBlocking("Serveur arrêté", "Le serveur s'est arrêté.", 0xED4245);
            discord.shutdown();
        }
        if (boosts != null) boosts.shutdown();
    }

    private void bind(String name, org.bukkit.command.CommandExecutor ex, org.bukkit.command.TabCompleter tc) {
        PluginCommand c = getCommand(name);
        if (c == null) {
            getLogger().severe("Commande '" + name + "' absente de plugin.yml");
            return;
        }
        c.setExecutor(ex);
        c.setTabCompleter(tc);
    }

    /** Recharge config.yml, limits.yml et mythic-data.yml. */
    public void reloadAll() {
        reloadConfig();
        Lang.load(this);
        discord.reload();
        limitStore.reload();
        limits.load();
        potions.load();
        bypass.load();
        mythic.reload();
        boosts.load();
        admin.load();
        shop.load();
        playerShop.load();
        customRecipes.load();
        customItems.reload();
        effectsTask.reload();
        playtime.reload();
        recipes.load();
    }

    public static VortexEssentials get() { return instance; }
    public CooldownManager cooldowns() { return cooldowns; }
    public MythicRegistry mythic() { return mythic; }
    public LimitManager limits() { return limits; }
    public PotionManager potions() { return potions; }
    public BypassManager bypass() { return bypass; }
    public MenuManager menu() { return menu; }
    public BoostManager boosts() { return boosts; }
    public RecipeManager recipes() { return recipes; }
    public AdminManager admin() { return admin; }
    public DiscordWebhook discord() { return discord; }
    public PackManager pack() { return pack; }
    public Money money() { return money; }
    public ShopManager shop() { return shop; }
    public PlayerShop playerShop() { return playerShop; }
    public ShopMenus shopMenus() { return shopMenus; }
    public PlaytimeManager playtime() { return playtime; }
    public MaintenanceManager maintenance() { return maintenance; }
    public SmpManager smp() { return smp; }
    public MaceLimits maceLimits() { return maceLimits; }
    public RitualCrafts rituals() { return rituals; }
    public RitualMenu ritualMenu() { return ritualMenu; }
    public RecipeEditor recipeEditor() { return recipeEditor; }
    public OrderManager orders() { return orders; }
    public OrderMenus orderMenus() { return orderMenus; }
    public DimensionManager dimensions() { return dimensions; }
    public SpawnerManager spawners() { return spawners; }
    public SpawnerMenus spawnerMenus() { return spawnerMenus; }
    public StaffMod staffMod() { return staffMod; }
    public StaffModMenu staffModMenu() { return staffModMenu; }
    public BountyManager bounties() { return bounties; }
    public BountyMenus bountyMenus() { return bountyMenus; }
    public ServicesMenu servicesMenu() { return servicesMenu; }
    public CustomCraftLimits craftLimits() { return craftLimits; }
    public ShopEditor shopEditor() { return shopEditor; }
    public StaffManager staff() { return staff; }
    public StaffXray staffXray() { return staffXray; }
    public LegendaryLimits legendaryLimits() { return legendaryLimits; }
    public AdminMenuManager adminMenus() { return adminMenus; }
    public CustomRecipes customRecipes() { return customRecipes; }
    public CustomItems customItems() { return customItems; }
}
