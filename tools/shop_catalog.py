# Moteur du catalogue complet (exécuté par gen_shop_defaults.py : C, pathlib et yaml sont déjà définis).
import re, sys
ROOT = pathlib.Path(__file__).resolve().parent.parent
ITEMS = [l.strip() for l in (ROOT / "tools/minecraft_items_1_21_11.txt").read_text().split("\n") if l.strip()]
BLOCKS = set((ROOT / "tools/minecraft_blocks_1_21_11.txt").read_text().split())

# ── Prix réglés à la main (table C) ──
H = {}
for cid, (cname, cicon, its) in C.items():
    for k, b, s in its:
        if ":" not in k:
            H[k] = (cid, float(b), float(s))

# ── Items absents de la boutique (non obtenables en survie, métadonnées, œufs...) ──
EXCLUDE_EXACT = {
    "AIR", "BEDROCK", "BARRIER", "COMMAND_BLOCK", "CHAIN_COMMAND_BLOCK", "REPEATING_COMMAND_BLOCK", "COMMAND_BLOCK_MINECART",
    "STRUCTURE_BLOCK", "STRUCTURE_VOID", "JIGSAW", "LIGHT", "DEBUG_STICK", "KNOWLEDGE_BOOK", "SPAWNER", "TRIAL_SPAWNER", "VAULT",
    "REINFORCED_DEEPSLATE", "END_PORTAL_FRAME", "BUDDING_AMETHYST", "FARMLAND", "DIRT_PATH", "PLAYER_HEAD", "WRITTEN_BOOK",
    "FILLED_MAP", "ENCHANTED_BOOK", "POTION", "SPLASH_POTION", "LINGERING_POTION", "TIPPED_ARROW", "SUSPICIOUS_STEW",
    "FIREWORK_STAR", "OMINOUS_BOTTLE", "FROGSPAWN", "CHORUS_PLANT", "PETRIFIED_OAK_SLAB", "SUSPICIOUS_SAND", "SUSPICIOUS_GRAVEL",
    "TEST_BLOCK", "TEST_INSTANCE_BLOCK", "ENDER_DRAGON_SPAWN_EGG", "WITHER_SPAWN_EGG", "BUNDLE_PLACEHOLDER",
}
def excluded(n):
    return n in EXCLUDE_EXACT or n.endswith("_SPAWN_EGG") or n.startswith("INFESTED_")

# ── Catégories (ordre d'affichage) ──
CATS = [
    ("blocks", "Blocs", "GRASS_BLOCK"), ("wood", "Bois", "OAK_LOG"), ("colored", "Couleurs & textiles", "WHITE_WOOL"),
    ("nature", "Nature & plantes", "OAK_SAPLING"), ("ores", "Minerais & cuivre", "DIAMOND"), ("food", "Nourriture", "COOKED_BEEF"),
    ("farming", "Agriculture", "WHEAT"), ("mobs", "Drops de mobs", "BONE"), ("tools", "Outils & armes", "DIAMOND_PICKAXE"),
    ("armor", "Armures", "DIAMOND_CHESTPLATE"), ("redstone", "Redstone & transport", "REDSTONE"),
    ("decoration", "Décoration", "PAINTING"), ("brewing", "Alchimie & XP", "BREWING_STAND"), ("discs", "Musique", "MUSIC_DISC_CAT"),
    ("templates", "Modèles & poteries", "NETHERITE_UPGRADE_SMITHING_TEMPLATE"), ("misc", "Divers", "CHEST"),
]

COLORS = ["LIGHT_BLUE", "LIGHT_GRAY", "WHITE", "ORANGE", "MAGENTA", "YELLOW", "LIME", "PINK", "GRAY", "CYAN", "PURPLE", "BLUE",
          "BROWN", "GREEN", "RED", "BLACK"]
CF = {"WOOL": 10, "CARPET": 3, "TERRACOTTA": 12, "GLAZED_TERRACOTTA": 20, "STAINED_GLASS": 8, "STAINED_GLASS_PANE": 2.5,
      "CONCRETE": 10, "CONCRETE_POWDER": 8, "SHULKER_BOX": 600, "BED": 30, "BANNER": 30, "CANDLE": 12, "DYE": 4,
      "HARNESS": 120, "BUNDLE": 45}
def color_split(n):
    for c in COLORS:
        if n.startswith(c + "_") and n[len(c) + 1:] in CF:
            return c, n[len(c) + 1:]
    return None

WOOD_TYPES = "OAK|SPRUCE|BIRCH|JUNGLE|ACACIA|DARK_OAK|MANGROVE|CHERRY|PALE_OAK|BAMBOO|CRIMSON|WARPED"
WOOD_RE = re.compile(r"^(STRIPPED_)?(" + WOOD_TYPES + r")_(LOG|WOOD|STEM|HYPHAE|BLOCK|PLANKS|MOSAIC_STAIRS|MOSAIC_SLAB|MOSAIC|STAIRS|SLAB|"
                     r"FENCE_GATE|FENCE|DOOR|TRAPDOOR|BUTTON|PRESSURE_PLATE|HANGING_SIGN|SIGN|CHEST_BOAT|BOAT|CHEST_RAFT|RAFT|SHELF)$")
WOOD_P = {"LOG": 8, "WOOD": 10, "STEM": 12, "HYPHAE": 14, "BLOCK": 6, "PLANKS": 2, "STAIRS": 3, "SLAB": 1.5, "FENCE": 3.5,
          "FENCE_GATE": 6, "DOOR": 5, "TRAPDOOR": 5, "BUTTON": 2, "PRESSURE_PLATE": 4, "SIGN": 6, "HANGING_SIGN": 14,
          "BOAT": 10, "CHEST_BOAT": 20, "RAFT": 10, "CHEST_RAFT": 20, "SHELF": 12, "MOSAIC": 4, "MOSAIC_STAIRS": 5, "MOSAIC_SLAB": 2.5}
WOOD_RARITY = {"CHERRY": 1.2, "PALE_OAK": 1.2, "MANGROVE": 1.1, "CRIMSON": 1.5, "WARPED": 1.5}

NATURE_RE = re.compile(r"(SAPLING|LEAVES|CORAL|CORAL_FAN|CORAL_BLOCK)$|^(DANDELION|POPPY|BLUE_ORCHID|ALLIUM|AZURE_BLUET|RED_TULIP|"
    r"ORANGE_TULIP|WHITE_TULIP|PINK_TULIP|OXEYE_DAISY|CORNFLOWER|LILY_OF_THE_VALLEY|WITHER_ROSE|SUNFLOWER|LILAC|ROSE_BUSH|PEONY|"
    r"TORCHFLOWER|PITCHER_PLANT|PINK_PETALS|WILDFLOWERS|LEAF_LITTER|CLOSED_EYEBLOSSOM|OPEN_EYEBLOSSOM|CACTUS_FLOWER|FIREFLY_BUSH|BUSH|"
    r"SHORT_DRY_GRASS|TALL_DRY_GRASS|SHORT_GRASS|TALL_GRASS|FERN|LARGE_FERN|DEAD_BUSH|VINE|GLOW_LICHEN|MOSS_CARPET|AZALEA|"
    r"FLOWERING_AZALEA|SPORE_BLOSSOM|RED_MUSHROOM|BROWN_MUSHROOM|CRIMSON_FUNGUS|WARPED_FUNGUS|CRIMSON_ROOTS|WARPED_ROOTS|NETHER_SPROUTS|"
    r"WEEPING_VINES|TWISTING_VINES|BIG_DRIPLEAF|SMALL_DRIPLEAF|HANGING_ROOTS|MANGROVE_PROPAGULE|MANGROVE_ROOTS|MUDDY_MANGROVE_ROOTS|"
    r"CHORUS_FLOWER|SEA_PICKLE|LILY_PAD|SEAGRASS|SNIFFER_EGG|TURTLE_EGG|SCULK|SCULK_VEIN|SCULK_CATALYST|SCULK_SHRIEKER|"
    r"RED_MUSHROOM_BLOCK|BROWN_MUSHROOM_BLOCK|MUSHROOM_STEM|NETHER_WART_BLOCK|WARPED_WART_BLOCK|SHROOMLIGHT|MOSS_BLOCK|PALE_MOSS_BLOCK|"
    r"PALE_MOSS_CARPET|PALE_HANGING_MOSS|CREAKING_HEART|SPONGE|WET_SPONGE)$")

def special_cat(n):
    if color_split(n) or n in ("TERRACOTTA", "SHULKER_BOX"): return "colored"
    if WOOD_RE.match(n): return "wood"
    if n.startswith("MUSIC_DISC_") or n in ("DISC_FRAGMENT_5", "GOAT_HORN", "JUKEBOX"): return "discs"
    if n.endswith(("_SMITHING_TEMPLATE", "_POTTERY_SHERD", "_BANNER_PATTERN")): return "templates"
    if NATURE_RE.search(n): return "nature"
    return None

TOOL_END = ("_PICKAXE", "_AXE", "_SHOVEL", "_HOE", "_SWORD", "_SPEAR")
TOOL_SET = {"BOW", "CROSSBOW", "TRIDENT", "MACE", "ARROW", "SPECTRAL_ARROW", "FISHING_ROD", "SHEARS", "FLINT_AND_STEEL",
            "CARROT_ON_A_STICK", "WARPED_FUNGUS_ON_A_STICK", "BRUSH"}
ARMOR_END = ("_HELMET", "_CHESTPLATE", "_LEGGINGS", "_BOOTS", "_HORSE_ARMOR", "WOLF_ARMOR", "_NAUTILUS_ARMOR")
ARMOR_SET = {"SHIELD", "ELYTRA", "TURTLE_HELMET"}
FOOD = set("""APPLE BAKED_POTATO BEEF BEETROOT BEETROOT_SOUP BREAD CAKE CARROT CHICKEN CHORUS_FRUIT COD COOKED_BEEF COOKED_CHICKEN COOKED_COD
COOKED_MUTTON COOKED_PORKCHOP COOKED_RABBIT COOKED_SALMON COOKIE DRIED_KELP ENCHANTED_GOLDEN_APPLE GLOW_BERRIES GOLDEN_APPLE GOLDEN_CARROT
HONEY_BOTTLE MELON_SLICE MILK_BUCKET MUSHROOM_STEW MUTTON POISONOUS_POTATO PORKCHOP POTATO PUMPKIN_PIE PUFFERFISH RABBIT RABBIT_STEW
ROTTEN_FLESH SALMON SPIDER_EYE SWEET_BERRIES TROPICAL_FISH""".split())
FARM = set("""WHEAT WHEAT_SEEDS BEETROOT_SEEDS MELON_SEEDS PUMPKIN_SEEDS TORCHFLOWER_SEEDS PITCHER_POD CARROT POTATO BEETROOT PUMPKIN MELON
CARVED_PUMPKIN SUGAR_CANE SUGAR BAMBOO COCOA_BEANS NETHER_WART BONE_MEAL KELP SWEET_BERRIES GLOW_BERRIES CACTUS EGG HAY_BLOCK
BROWN_EGG BLUE_EGG DRIED_KELP_BLOCK COMPOSTER""".split())
MOBS = set("""ROTTEN_FLESH BONE STRING SPIDER_EYE GUNPOWDER ENDER_PEARL ENDER_EYE BLAZE_ROD GHAST_TEAR SLIME_BALL LEATHER FEATHER
PHANTOM_MEMBRANE SHULKER_SHELL PRISMARINE_SHARD PRISMARINE_CRYSTALS INK_SAC GLOW_INK_SAC RABBIT_HIDE RABBIT_FOOT BREEZE_ROD WIND_CHARGE
TURTLE_SCUTE ARMADILLO_SCUTE ECHO_SHARD NAUTILUS_SHELL HEART_OF_THE_SEA DRAGON_BREATH WITHER_SKELETON_SKULL NETHER_STAR HEAVY_CORE
MAGMA_CREAM BLAZE_POWDER FIRE_CHARGE HONEYCOMB SLIME_BLOCK TRIAL_KEY OMINOUS_TRIAL_KEY COPPER_NUGGET""".split())
BREW = set("""BREWING_STAND GLASS_BOTTLE BLAZE_POWDER FERMENTED_SPIDER_EYE GLISTERING_MELON_SLICE MAGMA_CREAM PUFFERFISH EXPERIENCE_BOTTLE
ENCHANTING_TABLE ANVIL CHIPPED_ANVIL DAMAGED_ANVIL GRINDSTONE CAULDRON GLOWSTONE_DUST""".split())
DECO = set("""TORCH SOUL_TORCH LANTERN SOUL_LANTERN CHAIN END_ROD PAINTING ITEM_FRAME GLOW_ITEM_FRAME FLOWER_POT ARMOR_STAND BOOKSHELF
CHISELED_BOOKSHELF LECTERN CAMPFIRE SOUL_CAMPFIRE DECORATED_POT LADDER SCAFFOLDING IRON_BARS GLASS_PANE BELL JACK_O_LANTERN
CANDLE LIGHTNING_ROD_X""".split())
REDSTONE_SET = set("""REDSTONE REDSTONE_BLOCK REDSTONE_TORCH REDSTONE_LAMP REPEATER COMPARATOR PISTON STICKY_PISTON OBSERVER HOPPER DROPPER DISPENSER
TNT DAYLIGHT_DETECTOR LEVER TARGET NOTE_BLOCK STONE_BUTTON POLISHED_BLACKSTONE_BUTTON STONE_PRESSURE_PLATE POLISHED_BLACKSTONE_PRESSURE_PLATE
LIGHT_WEIGHTED_PRESSURE_PLATE HEAVY_WEIGHTED_PRESSURE_PLATE TRIPWIRE_HOOK TRAPPED_CHEST CRAFTER SLIME_BLOCK HONEY_BLOCK LIGHTNING_ROD
SCULK_SENSOR CALIBRATED_SCULK_SENSOR TNT_MINECART""".split())
ORE_SET = set("""COAL CHARCOAL DIAMOND EMERALD LAPIS_LAZULI REDSTONE QUARTZ AMETHYST_SHARD NETHERITE_SCRAP NETHERITE_INGOT IRON_INGOT GOLD_INGOT
COPPER_INGOT IRON_BLOCK GOLD_BLOCK DIAMOND_BLOCK EMERALD_BLOCK NETHERITE_BLOCK COAL_BLOCK LAPIS_BLOCK COPPER_BLOCK ANCIENT_DEBRIS
AMETHYST_BLOCK SMALL_AMETHYST_BUD MEDIUM_AMETHYST_BUD LARGE_AMETHYST_BUD AMETHYST_CLUSTER GILDED_BLACKSTONE RAW_IRON_BLOCK
RAW_GOLD_BLOCK RAW_COPPER_BLOCK""".split())

def is_copper_block_item(n):
    return "COPPER" in n and not n.endswith(TOOL_END + ARMOR_END) and n not in ("COPPER_INGOT", "COPPER_NUGGET", "RAW_COPPER", "COPPER_GOLEM_SPAWN_EGG")

def generic_cat(n):
    if n.endswith(TOOL_END) or n in TOOL_SET: return "tools"
    if n.endswith(ARMOR_END) or n in ARMOR_SET: return "armor"
    if n.endswith("RAIL") or n.endswith("MINECART") or n in REDSTONE_SET or n.startswith("REDSTONE") and not n.endswith("_ORE"): return "redstone"
    if is_copper_block_item(n) and any(x in n for x in ("LANTERN", "TORCH", "BARS", "CHAIN")): return "decoration"
    if n.endswith("_ORE") or n.startswith("RAW_") or n.endswith("_NUGGET") or n in ORE_SET or is_copper_block_item(n) and "GOLEM" not in n and "CHEST" not in n: return "ores"
    if n in BREW: return "brewing"
    if n in DECO or n.endswith(("_HEAD", "_SKULL")) or n.endswith("_BARS"): return "decoration"
    if n in FOOD or n.startswith("COOKED_"): return "food"
    if n in FARM: return "farming"
    if n in MOBS: return "mobs"
    return "blocks" if n in BLOCKS else "misc"

def category_of(n):
    sp = special_cat(n)
    if sp: return sp
    if n in H: return H[n][0]
    return generic_cat(n)

# ── Prix ──
def r1(x): return round(x, 1) if x < 10 else round(x)
def sell_of(b, ratio=0.25): return max(0.1, round(b * ratio, 1)) if b < 40 else round(b * ratio)

SB = {}
for line in """STONE 4|COBBLESTONE 2|MOSSY_COBBLESTONE 5|SMOOTH_STONE 6|STONE_BRICKS 6|MOSSY_STONE_BRICKS 8|CRACKED_STONE_BRICKS 7|CHISELED_STONE_BRICKS 8|
GRANITE 3|POLISHED_GRANITE 4|DIORITE 3|POLISHED_DIORITE 4|ANDESITE 3|POLISHED_ANDESITE 4|DEEPSLATE 5|COBBLED_DEEPSLATE 6|POLISHED_DEEPSLATE 8|
DEEPSLATE_BRICKS 10|CRACKED_DEEPSLATE_BRICKS 11|DEEPSLATE_TILES 10|CRACKED_DEEPSLATE_TILES 11|CHISELED_DEEPSLATE 12|TUFF 4|POLISHED_TUFF 6|
TUFF_BRICKS 8|CHISELED_TUFF 8|CHISELED_TUFF_BRICKS 9|CALCITE 6|DRIPSTONE_BLOCK 8|SMOOTH_BASALT 8|POLISHED_BASALT 8|BASALT 6|BLACKSTONE 6|
POLISHED_BLACKSTONE 8|POLISHED_BLACKSTONE_BRICKS 10|CRACKED_POLISHED_BLACKSTONE_BRICKS 11|CHISELED_POLISHED_BLACKSTONE 10|MUD 3|PACKED_MUD 6|
MUD_BRICKS 10|BRICKS 20|SANDSTONE 8|CHISELED_SANDSTONE 9|CUT_SANDSTONE 9|SMOOTH_SANDSTONE 9|RED_SANDSTONE 9|CHISELED_RED_SANDSTONE 10|
CUT_RED_SANDSTONE 10|SMOOTH_RED_SANDSTONE 10|NETHER_BRICKS 10|CRACKED_NETHER_BRICKS 11|CHISELED_NETHER_BRICKS 12|RED_NETHER_BRICKS 14|
END_STONE 12|END_STONE_BRICKS 15|PURPUR_BLOCK 20|PURPUR_PILLAR 22|QUARTZ_BLOCK 40|CHISELED_QUARTZ_BLOCK 42|QUARTZ_BRICKS 44|QUARTZ_PILLAR 42|
SMOOTH_QUARTZ 45|PRISMARINE 30|PRISMARINE_BRICKS 60|DARK_PRISMARINE 80|RESIN_BRICKS 12|CHISELED_RESIN_BRICKS 14|RESIN_BLOCK 20""".replace("\n", "").split("|"):
    k, v = line.split(); SB[k] = float(v)

def base_price(prefix):
    for cand in (prefix, prefix + "S", prefix + "_BLOCK", prefix.replace("_BRICK", "_BRICKS"), prefix + "_BRICKS"):
        if cand in SB: return SB[cand]
        if cand in H: return H[cand][1]
    return None

def p_stone(n):
    if n in SB: return SB[n], sell_of(SB[n])
    m = re.match(r"^(.*)_(STAIRS|SLAB|WALL|FENCE)$", n)
    if m:
        base = base_price(m.group(1))
        if base is None: return None
        b = r1(base * (0.5 if m.group(2) == "SLAB" else 1.0))
        return b, sell_of(b)
    return None

MAT = {"WOODEN": 2, "STONE": 2, "GOLDEN": 30, "IRON": 18, "COPPER": 8, "DIAMOND": 150, "LEATHER": 8, "CHAINMAIL": 26}
TOOL_U = {"PICKAXE": (3, 2), "AXE": (3, 2), "SHOVEL": (1, 2), "HOE": (2, 2), "SWORD": (2, 1), "SPEAR": (2, 2)}
ARMOR_U = {"HELMET": 5, "CHESTPLATE": 8, "LEGGINGS": 7, "BOOTS": 4}
HORSE = {"LEATHER": 60, "COPPER": 90, "IRON": 150, "GOLDEN": 250, "DIAMOND": 600, "NETHERITE": 2700}
def round_money(x): return round(x / 5) * 5 if x >= 100 else round(x)

def p_tool_armor(n):
    m = re.match(r"^(WOODEN|STONE|GOLDEN|IRON|COPPER|DIAMOND|NETHERITE|LEATHER|CHAINMAIL)_(.+)$", n)
    if not m: return None
    mat, kind = m.groups()
    extra = 2000 if mat == "NETHERITE" else 0
    mp = MAT["DIAMOND"] if mat == "NETHERITE" else MAT[mat]
    if kind in TOOL_U:
        u, sticks = TOOL_U[kind]; cost = u * mp + sticks
    elif kind in ARMOR_U:
        cost = ARMOR_U[kind] * mp
    elif kind == "HORSE_ARMOR":
        b = HORSE[mat]; return b, sell_of(b, 0.2)
    else:
        return None
    b = round_money((cost + extra) * 1.1)
    return b, sell_of(b, 0.2)

def p_wood(n):
    m = WOOD_RE.match(n)
    if not m: return None
    stripped, wt, suf = m.groups()
    b = WOOD_P[suf] * WOOD_RARITY.get(wt, 1.0) * (1.1 if stripped else 1.0)
    b = r1(b)
    return b, sell_of(b)

def p_color(n):
    cs = color_split(n)
    base = CF[cs[1]] if cs else {"TERRACOTTA": 12, "SHULKER_BOX": 600}.get(n)
    return (base, sell_of(base)) if base else None

CP = {"COPPER_BLOCK": 72, "CUT_COPPER": 20, "CUT_COPPER_STAIRS": 20, "CUT_COPPER_SLAB": 10, "CHISELED_COPPER": 24, "COPPER_GRATE": 24,
      "COPPER_BULB": 110, "COPPER_DOOR": 48, "COPPER_TRAPDOOR": 32, "COPPER_CHEST": 120, "COPPER_GOLEM_STATUE": 250,
      "COPPER_LANTERN": 30, "COPPER_TORCH": 12, "COPPER_BARS": 14, "COPPER_CHAIN": 12}
def p_copper(n):
    if "COPPER" not in n: return None
    base = n
    waxed = base.startswith("WAXED_")
    base = base.removeprefix("WAXED_")
    for pre in ("EXPOSED_", "WEATHERED_", "OXIDIZED_"):
        base = base.removeprefix(pre)
    if base == "COPPER": base = "COPPER_BLOCK"
    if base in CP:
        b = CP[base] + (6 if waxed else 0)
        return b, sell_of(b)
    return None

RARE = {}
def load_rare(text):
    for line in text.strip().split("\n"):
        line = line.split("#")[0].strip()
        if not line: continue
        parts = line.split()
        RARE[parts[0]] = (float(parts[1]), float(parts[2]))
exec(open(pathlib.Path(__file__).with_name("shop_rare.py"), encoding="utf-8").read())

def price_of(n):
    if n in H: return H[n][1], H[n][2]
    for fn in (p_tool_armor, p_wood, p_color, p_copper, p_stone):
        r = fn(n)
        if r: return r
    return RARE.get(n)

FALLBACK = {"blocks": (6, 1.5), "wood": (6, 1.5), "colored": (10, 2.5), "nature": (5, 1.2), "ores": (20, 5), "food": (12, 3),
            "farming": (5, 1.2), "mobs": (15, 4), "tools": (100, 20), "armor": (100, 20), "redstone": (30, 8), "decoration": (15, 4),
            "brewing": (20, 5), "discs": (600, 150), "templates": (120, 30), "misc": (25, 6)}

# ── Vortex (vente à sec : -1 = non rachetable) ──
VORTEX = [
    ("vortex:lumberjack_axe", 900), ("vortex:smelter_pickaxe", 1200), ("vortex:explosive_pickaxe", 5500),
    ("vortex:super_lumberjack_axe", 5000), ("vortex:super_smelter_pickaxe", 6000), ("vortex:netherite_lumberjack_axe", 12500),
    ("vortex:netherite_explosive_pickaxe", 13500), ("vortex:netherite_smelter_pickaxe", 14000),
    ("vortex:panacea", 1500), ("vortex:crystallization_shard", 800), ("vortex:agony", 600),
    ("vortex:blazing_crossbow", 18000), ("vortex:magma_club", 22000), ("vortex:reaper_scythe", 25000),
    ("vortex:evoker_wand", 30000), ("vortex:sceptre_of_arachne", 30000), ("vortex:chrono_sword", 35000), ("vortex:excalibur", 40000),
    ("vortex:fractured", 120000), ("vortex:terraloop", 120000), ("vortex:jugas", 120000),
]

# ── Construction ──
cat_items = {cid: {} for cid, _, _ in CATS}
fallbacks = []
for n in ITEMS:
    if excluded(n): continue
    cid = category_of(n)
    pr = price_of(n)
    if pr is None:
        fb = FALLBACK[cid]
        pr = fb
        fallbacks.append((n, cid))
    cat_items[cid][n] = pr
# (catégorie « Items Vortex » supprimée de la boutique : on les obtient par craft)

# ── Prix selon la RARETÉ : plus un item est rare, plus il est cher à l'achat ET mieux il est racheté ──
#   (seuil de prix de base, multiplicateur d'achat, part de revente)
TIERS = [
    (3000, 1.50, 0.40),   # légendaire
    (600, 1.30, 0.37),    # épique
    (100, 1.15, 0.33),    # rare
    (15, 1.00, 0.30),     # peu commun
    (0, 1.00, 0.28),      # commun
]
def tier(base):
    for i, (th, m, r) in enumerate(TIERS):
        if base >= th:
            return m, r, len(TIERS) - 1 - i
def money(x):
    if x < 10: return round(x, 1)
    if x < 100: return float(round(x))
    if x < 1000: return float(round(x / 5) * 5)
    return float(round(x / 10) * 10)
for cid, its in cat_items.items():
    for n, (b, sv) in list(its.items()):
        if b >= 0:
            m, ratio, lvl = tier(b)
            nb = money(b * m)
            ns = sv if sv < 0 else min(nb, max(0.1, money(nb * ratio)))
            its[n] = (nb, ns)
        elif sv >= 0:                      # vendable seulement (non achetable) : bonus de rareté sur le rachat
            m, ratio, lvl = tier(sv / 0.25)
            its[n] = (b, money(sv * (1 + 0.1 * lvl)))

# ── Items obtenables auprès des villageois : aucun argent à la vente (évite la boucle échanges -> émeraudes -> boutique) ──
# Les émeraudes (et leur minerai/bloc) ne se revendent pas non plus : ce sont la monnaie des villageois.
VILLAGER_SOLD = set("""IRON_HELMET IRON_CHESTPLATE IRON_LEGGINGS IRON_BOOTS CHAINMAIL_HELMET CHAINMAIL_CHESTPLATE CHAINMAIL_LEGGINGS CHAINMAIL_BOOTS
DIAMOND_HELMET DIAMOND_CHESTPLATE DIAMOND_LEGGINGS DIAMOND_BOOTS SHIELD BELL COOKED_CHICKEN COOKED_PORKCHOP MAP ITEM_FRAME GLASS_PANE COMPASS
REDSTONE LAPIS_LAZULI GLOWSTONE ENDER_PEARL EXPERIENCE_BOTTLE BREAD PUMPKIN_PIE APPLE COOKIE CAKE GOLDEN_CARROT GLISTERING_MELON_SLICE
FISHING_ROD CAMPFIRE COOKED_COD COOKED_SALMON ARROW BOW CROSSBOW TRIPWIRE_HOOK LEATHER_HELMET LEATHER_CHESTPLATE LEATHER_LEGGINGS LEATHER_BOOTS
LEATHER_HORSE_ARMOR SADDLE BOOKSHELF NAME_TAG LANTERN GLASS CLOCK BRICKS QUARTZ_PILLAR QUARTZ_BLOCK POLISHED_ANDESITE POLISHED_DIORITE
POLISHED_GRANITE CHISELED_STONE_BRICKS DRIPSTONE_BLOCK SHEARS PAINTING STONE_AXE STONE_SHOVEL STONE_PICKAXE STONE_HOE IRON_AXE IRON_SHOVEL
IRON_PICKAXE IRON_HOE IRON_SWORD DIAMOND_AXE DIAMOND_SHOVEL DIAMOND_PICKAXE DIAMOND_HOE DIAMOND_SWORD GRINDSTONE
EMERALD EMERALD_BLOCK EMERALD_ORE DEEPSLATE_EMERALD_ORE TERRACOTTA""".split())
VILLAGER_COLOR_FAMILIES = ("WOOL", "CARPET", "BED", "BANNER", "DYE", "TERRACOTTA", "GLAZED_TERRACOTTA")

def villager_item(n):
    cs = color_split(n)
    return n in VILLAGER_SOLD or bool(cs and cs[1] in VILLAGER_COLOR_FAMILIES)

no_sell = 0
for cid, its in cat_items.items():
    if cid == "vortex": continue
    for n, (b, sv) in list(its.items()):
        if sv >= 0 and villager_item(n):
            its[n] = (b, -1.0)
            no_sell += 1

# ── Items utiles au PvP : jamais rachetés (armes, armures, projectiles, pommes d'or, perles, totems, seaux, rails, minecarts...) ──
PVP_NO_SELL = set("""BOW CROSSBOW ARROW SPECTRAL_ARROW TRIDENT MACE SHIELD ELYTRA TOTEM_OF_UNDYING GOLDEN_APPLE ENCHANTED_GOLDEN_APPLE GOLDEN_CARROT
ENDER_PEARL ENDER_EYE TNT END_CRYSTAL RESPAWN_ANCHOR OBSIDIAN CRYING_OBSIDIAN COBWEB WATER_BUCKET LAVA_BUCKET POWDER_SNOW_BUCKET
FLINT_AND_STEEL FIRE_CHARGE FIREWORK_ROCKET EXPERIENCE_BOTTLE WIND_CHARGE WOLF_ARMOR TURTLE_HELMET""".split())
def pvp_item(n):
    return n in PVP_NO_SELL or n.endswith(("RAIL", "MINECART", "_SWORD", "_AXE", "_HELMET", "_CHESTPLATE", "_LEGGINGS", "_BOOTS",
                                           "_SPEAR", "_HORSE_ARMOR", "_NAUTILUS_ARMOR"))
pvp_locked = 0
for cid, its in cat_items.items():
    if cid == "vortex": continue
    for n, (b, sv) in list(its.items()):
        if sv >= 0 and pvp_item(n):
            its[n] = (b, -1.0)
            pvp_locked += 1
print("items PvP sans revente :", pvp_locked)

# ── Prix d'ACHAT relevés pour les items utiles au PvP (la vente reste inchangée) ──
PVP = {
    2.0: "ENCHANTED_GOLDEN_APPLE TOTEM_OF_UNDYING END_CRYSTAL RESPAWN_ANCHOR TNT TNT_MINECART ELYTRA TRIDENT MACE HEAVY_CORE EXPERIENCE_BOTTLE "
         "ENDER_PEARL FIREWORK_ROCKET NETHERITE_INGOT NETHERITE_SCRAP NETHERITE_BLOCK ANCIENT_DEBRIS".split(),
    1.8: "GOLDEN_APPLE GOLDEN_CARROT".split(),
    1.5: "SHIELD BOW CROSSBOW ARROW SPECTRAL_ARROW OBSIDIAN CRYING_OBSIDIAN COBWEB LAVA_BUCKET WATER_BUCKET POWDER_SNOW_BUCKET FLINT_AND_STEEL "
         "FIRE_CHARGE GUNPOWDER BLAZE_POWDER BLAZE_ROD GHAST_TEAR MAGMA_CREAM GLISTERING_MELON_SLICE NETHER_WART SUGAR FERMENTED_SPIDER_EYE "
         "RABBIT_FOOT PHANTOM_MEMBRANE DRAGON_BREATH ENDER_EYE ANVIL ENCHANTING_TABLE BREWING_STAND WIND_CHARGE BREEZE_ROD ENDER_CHEST".split(),
}
PVP_LOOKUP = {n: m for m, names in PVP.items() for n in names}

def pvp_mult(n):
    if n in PVP_LOOKUP: return PVP_LOOKUP[n]
    gear = n.endswith(("_SWORD", "_AXE", "_HELMET", "_CHESTPLATE", "_LEGGINGS", "_BOOTS", "_SPEAR", "_PICKAXE"))
    if n.startswith("NETHERITE_") and gear: return 2.0
    if n.startswith("DIAMOND_") and gear and not n.endswith("_PICKAXE"): return 1.5
    if n.startswith("IRON_") and gear and not n.endswith(("_PICKAXE", "_AXE")): return 1.3
    return 1.0

boosted = []
for cid, its in cat_items.items():
    if cid == "vortex": continue
    for n, (b, sv) in list(its.items()):
        m = pvp_mult(n)
        if m > 1.0 and b >= 0:
            nb = round(b * m, 1) if b * m < 10 else round(b * m)
            ns = sv if sv < 0 else min(float(nb), money(sv * (1 + (m - 1) * 0.5)))   # la revente suit à moitié le relèvement PvP
            its[n] = (float(nb), ns)
            boosted.append(n)
print("sans revente (villageois / émeraudes) :", no_sell, "| prix PvP relevés :", len(boosted))

# ── Retraits : ces items ne sont plus du tout dans la boutique (ni achat ni vente) ──
#  - élytres, mace, trident, tout le netherite façonné (armures, outils, armes, modèle d'amélioration) ;
#    les lingots/débris/blocs de netherite restent ;
#  - ficelle (dupe de string sur le serveur) ;
#  - stockage portatif : shulker shell, shulker boxes, ender chest, bundles ;
#  - breeze rod (remplacé par le wind charge) ;
#  - tout ce qu'on obtient auprès des villageois (glowstone, lapis, redstone, perles, pain, outils/armures de métier,
#    laines, teintures, terracottas...) et les émeraudes (monnaie des villageois).
REMOVED_ITEMS = set("""ELYTRA MACE TRIDENT STRING SHULKER_SHELL SHULKER_BOX ENDER_CHEST BUNDLE BREEZE_ROD""".split())
NETHERITE_KEPT = {"NETHERITE_INGOT", "NETHERITE_SCRAP", "NETHERITE_BLOCK"}
def removed_item(n):
    return (n in REMOVED_ITEMS or n.endswith(("_SHULKER_BOX", "_BUNDLE"))
            or (n.startswith("NETHERITE_") and n not in NETHERITE_KEPT) or villager_item(n))
removed_count = 0
for cid, its in cat_items.items():
    for n in list(its):
        if removed_item(n):
            del its[n]
            removed_count += 1

def set_price(name, buy, sell):
    for cid, its in cat_items.items():
        if name in its:
            its[name] = (float(buy), float(sell))
            return True
    return False
set_price("TOTEM_OF_UNDYING", 4000, -1)     # totem : prix relevé (toujours non revendable)
set_price("WIND_CHARGE", 14, -1)            # remplace le breeze rod (1 breeze rod = 4 wind charges)
print("items retirés de la boutique :", removed_count)

# ── Prix d'ACHAT relevés pour tout ce qui sert aux crafts d'armes et d'armures légendaires / mythiques ──
CRAFT_MULT = {"NETHERITE_INGOT": 2.5, "NETHERITE_BLOCK": 2.5, "NETHERITE_SCRAP": 2.5, "ANCIENT_DEBRIS": 2.5,
              "ENCHANTED_GOLDEN_APPLE": 2.0, "ECHO_SHARD": 3.0, "HEART_OF_THE_SEA": 3.0, "CONDUIT": 2.0, "GHAST_TEAR": 2.5,
              "WITHER_SKELETON_SKULL": 3.0, "DIAMOND_BLOCK": 1.8, "DIAMOND": 1.5, "BLAZE_ROD": 2.0, "BLAZE_POWDER": 2.0,
              "MAGMA_BLOCK": 2.0, "COBWEB": 2.0, "FERMENTED_SPIDER_EYE": 2.0, "HEAVY_CORE": 3.0, "TNT": 1.5, "GUNPOWDER": 1.5,
              "PRISMARINE_SHARD": 2.0, "TOTEM_OF_UNDYING": 2.0, "BREEZE_ROD": 3.0, "GOLD_BLOCK": 1.5, "ENDER_EYE": 1.5,
              "NETHER_WART": 1.5, "IRON_BLOCK": 1.3, "ENDER_PEARL": 1.5, "CLOCK": 2.0}
crafted_up = 0
for cid, its in cat_items.items():
    for n, (b, sv) in list(its.items()):
        m = CRAFT_MULT.get(n)
        if m and b >= 0:
            nb = money(b * m)
            its[n] = (nb, sv if sv < 0 else min(sv, nb))
            crafted_up += 1

# ── Revente « nerfée » pour tout ce qui est facile à obtenir : fermes, générateurs, crafts bon marché ──
SELL_MULT_CAT = {"blocks": 0.4, "wood": 0.5, "colored": 0.5, "nature": 0.5, "food": 0.5, "farming": 0.5,
                 "decoration": 0.6, "redstone": 0.6, "misc": 0.6}
FARM_DROPS = set("""BONE ROTTEN_FLESH GUNPOWDER SPIDER_EYE SLIME_BALL BLAZE_ROD BLAZE_POWDER GHAST_TEAR MAGMA_CREAM LEATHER FEATHER INK_SAC
GLOW_INK_SAC PHANTOM_MEMBRANE PRISMARINE_SHARD PRISMARINE_CRYSTALS HONEYCOMB RABBIT_HIDE TURTLE_SCUTE ARMADILLO_SCUTE EGG
SNOWBALL CLAY_BALL SAND GRAVEL QUARTZ""".split())
SELL_MULT_ITEM = {"IRON_INGOT": 0.5, "GOLD_INGOT": 0.6, "COPPER_INGOT": 0.6, "COAL": 0.7, "CHARCOAL": 0.6, "IRON_BLOCK": 0.5,
                  "GOLD_BLOCK": 0.6, "COPPER_BLOCK": 0.6, "COAL_BLOCK": 0.7, "RAW_IRON_BLOCK": 0.5, "RAW_COPPER_BLOCK": 0.6,
                  "LODESTONE": 0.25, "RESPAWN_ANCHOR": 0.3, "BEACON": 0.4, "CONDUIT": 0.4, "ENCHANTING_TABLE": 0.4,
                  "BREWING_STAND": 0.5, "ANVIL": 0.4, "CAULDRON": 0.5, "JUKEBOX": 0.5, "OBSIDIAN": 0.5, "CRYING_OBSIDIAN": 0.5}
nerfed = 0
for cid, its in cat_items.items():
    for n, (b, sv) in list(its.items()):
        if sv < 0:
            continue
        m = SELL_MULT_ITEM.get(n)
        if m is None:
            m = SELL_MULT_CAT.get(cid, 1.0)
            if n in FARM_DROPS:
                m = min(m, 0.6)
        if m < 1.0:
            ns = max(0.1, money(sv * m))
            its[n] = (b, min(ns, b) if b >= 0 else ns)
            nerfed += 1
print("achats relevés (crafts) :", crafted_up, "| reventes réduites (faciles à obtenir) :", nerfed)

# ── Items craftables facilement (laisse, etc.) : la revente ne dépasse jamais une petite part du coût de leurs ingrédients ──
import json
RECIPES = json.load(open(ROOT / "tools/minecraft_recipes_1_21_11.json"))
def unit_cost(name):
    best = None
    for r in RECIPES.get(name, []):
        total, ok = 0.0, True
        for ing, c in r["in"].items():
            p = price_of(ing)
            if p is None or p[0] < 0:
                ok = False
                break
            total += p[0] * c
        if ok and total > 0:
            u = total / max(1, r["out"])
            best = u if best is None else min(best, u)
    return best
crafted_capped = 0
for cid, its in cat_items.items():
    for n, (b, sv) in list(its.items()):
        if sv < 0 or b < 0:
            continue
        cost = unit_cost(n)
        if cost is None:
            continue
        cap = (0.15 if b < 100 else 0.25) * cost      # peu rare : encore plus bas
        if sv > cap:
            its[n] = (b, min(b, max(0.1, money(cap))))
            crafted_capped += 1
print("reventes plafonnées (craft facile) :", crafted_capped)

# items à la main : ordre « table C » conservé en tête de leur catégorie
out = {"categories": {}}
names = {cid: (nm, ic) for cid, nm, ic in CATS}
for cid, (nm, ic) in names.items():
    its = cat_items[cid]
    if not its: continue
    out["categories"][cid] = {"name": nm, "icon": ic, "enabled": True,
                              "items": {k: {"buy": float(b), "sell": float(s), "enabled": True} for k, (b, s) in its.items()}}
total = sum(len(v["items"]) for v in out["categories"].values())
path = ROOT / "src/main/resources/shop-default.yml"
path.write_text("# Catalogue par défaut de la boutique (copié en shop.yml au premier démarrage).\n"
                "# buy = prix d'achat par unité, sell = prix de vente par unité, -1 = non achetable / non vendable.\n"
                "# Clés d'items : nom de Material, vortex:<id> (item Vortex) ou citem:<id> (item personnalisé).\n"
                "# Ajouter les nouveautés à une boutique existante sans toucher à vos prix : /shop manage import\n"
                + yaml.safe_dump(out, allow_unicode=True, sort_keys=False), encoding="utf-8")
print("OK", total, "items,", len(out["categories"]), "catégories")
for cid in out["categories"]: print(f"  {cid:11} {len(out['categories'][cid]['items'])}")
print("prix par défaut (à compléter dans shop_rare.py) :", len(fallbacks))
if "--fallbacks" in sys.argv or True:
    open("/tmp/fallbacks.txt", "w").write("\n".join(f"{n} {c}" for n, c in fallbacks))
