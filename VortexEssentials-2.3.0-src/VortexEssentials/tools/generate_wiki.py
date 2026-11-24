#!/usr/bin/env python3
"""
Génère docs/wiki/index.html (page statique, aucun serveur requis) à partir de :
  - src/main/java/.../item/VortexItem.java   (items, lore, enchantements)
  - src/main/resources/recipes.yml           (recettes)
  - src/main/resources/config.yml            (grades / boosts)
Usage :  pip install pyyaml  &&  python3 tools/generate_wiki.py
Relancez-le après chaque modification d'un item, d'une recette ou d'un boost.
"""
import html, json, re, sys
from pathlib import Path

import yaml

# ── À PERSONNALISER ──
SERVER_NAME = "VortexSMP"
SHOP_URL = "https://votre-boutique.tebex.io"
DISCORD_URL = ""          # lien d’invitation (https://discord.gg/xxxx) ; vide = bouton désactivé
SHOW_UNVERIFIED = False   # True : affiche aussi les plugins marqués "verify: true"
# ─────────────────────

ROOT = Path(__file__).resolve().parent.parent
JAVA = next(ROOT.glob("src/main/java/**/item/VortexItem.java"))
RES = ROOT / "src/main/resources"
OUT = ROOT / "docs/wiki/index.html"

CATS = {
    "TOOL": ("Outils", "#2bb3d4"),
    "MYTHIC_TOOL": ("Outils mythiques", "#a259e6"),
    "LEGENDARY_WEAPON": ("Armes légendaires", "#e8a317"),
    "MYTHIC_WEAPON": ("Armes et armures mythiques", "#e5484d"),
    "SECRET_WEAPON": ("Secret Weapons", "#a855f7"),
    "CONSUMABLE": ("Consommables", "#e255a1"),
}


def pretty(mat: str) -> str:
    return mat.replace("_", " ").title()


def strip_codes(s: str) -> str:
    return re.sub(r"§.", "", s)


def split_top(s: str):
    """Découpe sur les virgules de premier niveau (en respectant chaînes et parenthèses)."""
    parts, depth, cur, in_str, esc = [], 0, [], False, False
    for ch in s:
        if in_str:
            cur.append(ch)
            if esc:
                esc = False
            elif ch == "\\":
                esc = True
            elif ch == '"':
                in_str = False
            continue
        if ch == '"':
            in_str = True
            cur.append(ch)
        elif ch == "(":
            depth += 1
            cur.append(ch)
        elif ch == ")":
            depth -= 1
            cur.append(ch)
        elif ch == "," and depth == 0:
            parts.append("".join(cur).strip())
            cur = []
        else:
            cur.append(ch)
    if "".join(cur).strip():
        parts.append("".join(cur).strip())
    return parts


def unq(s: str) -> str:
    return json.loads(s)  # chaîne Java simple -> str


SECRET_IDS = set()   # ids à ne jamais afficher dans le wiki


def parse_items():
    src = JAVA.read_text(encoding="utf-8")
    body = src[src.index("public enum VortexItem {") + len("public enum VortexItem {"):]
    items, i, n = [], 0, len(body)
    while i < n:
        m = re.compile(r"\s*(?://[^\n]*\n\s*)*([A-Z][A-Z_]+)\(").match(body, i)
        if not m:
            break
        name = m.group(1)
        j = m.end()
        depth, in_str, esc, k = 1, False, False, j
        while depth:
            ch = body[k]
            if in_str:
                if esc:
                    esc = False
                elif ch == "\\":
                    esc = True
                elif ch == '"':
                    in_str = False
            elif ch == '"':
                in_str = True
            elif ch == "(":
                depth += 1
            elif ch == ")":
                depth -= 1
            k += 1
        args = split_top(body[j:k - 1])
        cat = args[0].split(".")[1]
        mat = args[1].split(".")[1]
        display = unq(args[2])
        ench_raw = args[3][2:-1]  # contenu de e(...)
        ench = []
        if ench_raw.strip():
            kv = split_top(ench_raw)
            for a in range(0, len(kv) - 1, 2):
                ench.append((unq(kv[a]), int(kv[a + 1])))
        lore = [strip_codes(unq(x)) for x in args[4:]]
        if name.lower() not in SECRET_IDS:
            items.append(dict(id=name.lower(), cat=cat, mat=mat, name=display, ench=ench, lore=lore))
        i = k
        while i < n and body[i] in ", \n\r\t":
            i += 1
        if i < n and body[i] == ";":
            break
        # la virgule/point-virgule a été consommé(e) ci-dessus
    return items


def ingredient_label(tok: str, require: dict, names: dict) -> str:
    if tok.startswith("#"):
        return {"#logs": "Rondin (au choix)", "#planks": "Planche (au choix)"}.get(tok.lower(), tok)
    if tok in require:
        return "★ " + names.get(require[tok], require[tok])
    return pretty(tok)


def build_recipes(items):
    names = {it["id"]: it["name"] for it in items}
    data = yaml.safe_load((RES / "recipes.yml").read_text(encoding="utf-8"))
    out = {}
    for rid, r in (data.get("recipes") or {}).items():
        require = {k.upper(): v for k, v in (r.get("require-custom") or {}).items()}
        cells = [""] * 9
        if "shapeless" in r:
            kind = "Sans forme"
            for idx, tok in enumerate(r["shapeless"][:9]):
                cells[idx] = ingredient_label(tok, require, names)
        else:
            kind = "Forme fixe"
            ing = {str(k): v for k, v in r["ingredients"].items()}
            for row, line in enumerate(r["shape"]):
                for col, ch in enumerate(line):
                    if ch != " ":
                        cells[row * 3 + col] = ingredient_label(ing[ch], require, names)
        note = "Conserve les enchantements et la durabilité de l'objet de base." if r.get("transfer-from") else ""
        out[rid] = dict(kind=kind, cells=cells, note=note)
    for src_id, dst_id in (data.get("upgrades") or {}).items():
        out[dst_id] = dict(
            kind="Table de forge",
            cells=["Modèle d'amélioration en netherite", "★ " + names.get(src_id, src_id), "Lingot de netherite"]
                  + [""] * 6,
            note="Conserve les enchantements et la durabilité.",
            forge=True,
        )
    return out


def build_boosts():
    cfg = yaml.safe_load((RES / "config.yml").read_text(encoding="utf-8"))
    b = (cfg.get("boosts") or {})
    rows = []
    for gid, g in (b.get("groups") or {}).items():
        fx = ", ".join(f"{k.replace('_', ' ').title()} {v}" for k, v in (g.get("effects") or {}).items()) or "—"
        rows.append(dict(
            id=gid, xp=g.get("xp", 1.0), drops=g.get("drops", 1.0), damage=g.get("damage", 1.0),
            potion=g.get("potion-duration", 1.0),
            sell=round(float(g.get("sell-bonus", 0)) * 100), disc=round(float(g.get("buy-discount", 0)) * 100),
            cd=round(float(g.get("cooldown-reduction", 0)) * 100), effects=fx))
    return rows


ICONS = {
    "lock": '<rect x="12" y="22" width="24" height="18" rx="3"/><path d="M17 22v-6a7 7 0 0 1 14 0v6"/>',
    "home": '<path d="M7 24 24 9l17 15"/><path d="M12 21v19h24V21"/><path d="M20 40V30h8v10"/>',
    "shop": '<circle cx="24" cy="24" r="15"/><path d="M24 14v20M19 19h8a3.5 3.5 0 0 1 0 7h-6a3.5 3.5 0 0 0 0 7h9"/>',
    "crate": '<rect x="7" y="15" width="34" height="23" rx="2"/><path d="M7 24h34M24 24v7"/><path d="M7 15l4-6h26l4 6"/>',
    "grave": '<path d="M13 40V20a11 11 0 0 1 22 0v20z"/><path d="M24 17v12M19 22h10"/><path d="M8 40h32"/>',
    "combat": '<path d="M9 9l22 22M39 9 17 31M9 39l7-7M39 39l-7-7"/><path d="M9 9h7M39 9h-7"/>',
    "mic": '<rect x="18" y="7" width="12" height="21" rx="6"/><path d="M12 23a12 12 0 0 0 24 0M24 35v7M18 42h12"/>',
    "skin": '<circle cx="24" cy="15" r="7"/><path d="M9 41c2-10 8-14 15-14s13 4 15 14"/>',
    "anvil": '<path d="M8 15h32c0 5-4 8-9 8H17c0 6 4 6 4 11H13c0-5 4-5 4-11-4 0-9-3-9-8z"/><path d="M13 40h22"/>',
    "vortex": '<circle cx="24" cy="24" r="16"/><path d="M24 8a16 16 0 0 1 16 16M24 16a8 8 0 0 1 8 8M24 24l-8 0"/>',
    "generic": '<rect x="9" y="9" width="30" height="30" rx="7"/><path d="M18 24h12M24 18v12"/>',
}


def icon(name):
    return ('<svg viewBox="0 0 48 48" width="48" height="48" fill="none" stroke="currentColor" stroke-width="2.6" '
            'stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">' + ICONS.get(name, ICONS["generic"]) + "</svg>")


def all_plugins_html():
    import html as _h
    data = yaml.safe_load((ROOT / "tools/plugins.yml").read_text(encoding="utf-8")) or {}
    items = data.get("all_plugins") or []
    out = []
    for kind, title in (("paper", "Plugins Paper"), ("bukkit", "Plugins Bukkit")):
        rows = [p for p in items if p.get("type") == kind]
        out.append('<h3>%s (%d)</h3><div class="tw"><table><thead><tr><th>Plugin</th><th>À quoi ça sert</th></tr></thead><tbody>' % (title, len(rows)))
        for p in rows:
            out.append("<tr><td><b>%s</b></td><td>%s</td></tr>" % (_h.escape(p["name"]), _h.escape(p.get("role", ""))))
        out.append("</tbody></table></div>")
    return "\n".join(out)


def load_plugins():
    data = yaml.safe_load((ROOT / "tools/plugins.yml").read_text(encoding="utf-8")) or {}
    return [p for p in (data.get("plugins") or []) if SHOW_UNVERIFIED or not p.get("verify")]


def plugin_card(p):
    steps = "".join(f"<li>{html.escape(x)}</li>" for x in p.get("how") or [])
    cmds = " ".join(f"<code>{html.escape(c)}</code>" for c in p.get("commands") or [])
    return (f'<article class="pl"><div class="ico">{icon(p.get("icon", "generic"))}</div><div>'
            f'<h3>{html.escape(p["name"])} <small>{html.escape(p.get("role", ""))}</small></h3>'
            f'<ol>{steps}</ol>' + (f'<p class="cmds">{cmds}</p>' if cmds else "") + "</div></article>")


def discord_section():
    if DISCORD_URL:
        btn = f'<a class="btn" href="{html.escape(DISCORD_URL)}">Rejoindre le Discord</a>'
    else:
        btn = '<span class="btn off">Lien d’invitation bientôt disponible</span>'
    font = "system-ui,Segoe UI,sans-serif"
    banner = (
        '<svg class="banner" viewBox="0 0 800 220" role="img" aria-label="Bannière Discord">'
        '<defs><linearGradient id="g" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#5865f2"/>'
        '<stop offset="1" stop-color="#9b59f5"/></linearGradient></defs>'
        '<rect width="800" height="220" rx="18" fill="url(#g)"/>'
        '<g fill="#fff" fill-opacity=".18"><circle cx="720" cy="40" r="90"/><circle cx="90" cy="200" r="70"/></g>'
        '<g fill="#fff"><rect x="520" y="46" width="200" height="54" rx="16"/><path d="M552 100l-4 20 22-20z"/>'
        '<rect x="560" y="116" width="190" height="54" rx="16" fill-opacity=".85"/></g>'
        '<g fill="#5865f2"><rect x="540" y="66" width="120" height="8" rx="4"/><rect x="540" y="82" width="80" height="8" rx="4"/>'
        '<rect x="580" y="136" width="130" height="8" rx="4"/><rect x="580" y="152" width="90" height="8" rx="4"/></g>'
        f'<text x="40" y="96" fill="#fff" font-size="40" font-weight="700" font-family="{font}">Rejoins {html.escape(SERVER_NAME)}</text>'
        f'<text x="40" y="140" fill="#fff" fill-opacity=".92" font-size="24" font-family="{font}">sur Discord</text></svg>')
    return ('<section id="discord"><h2>Rejoins le Discord</h2>' + banner +
            '<ul class="perks"><li><b>Annonces et événements</b> en avant-première</li>'
            '<li><b>Aide et support</b> : recettes, items, grades, problèmes en jeu</li>'
            '<li><b>Tes idées comptent</b> : propose et vote les prochains items</li>'
            '<li><b>Trouve des partenaires</b> de jeu et d’aventure</li></ul>' + btn + "</section>")


def shop_rows():
    data = yaml.safe_load((RES / "shop-default.yml").read_text(encoding="utf-8")) or {}
    rows = []
    for cid, c in (data.get("categories") or {}).items():
        rows.append(f'<tr><td><b>{html.escape(c["name"])}</b></td><td>{len(c.get("items") or {})}</td></tr>')
    return "".join(rows)


def card(it, rec):
    label, color = CATS[it["cat"]]
    h = [f'<article class="card" data-cat="{it["cat"]}" data-search="{html.escape((it["name"] + " " + it["id"] + " " + label + " " + " ".join(it["lore"])).lower())}" style="--c:{color}">']
    h.append(f'<header><h3>{html.escape(it["name"])}</h3><span class="badge">{label}</span></header>')
    h.append(f'<p class="base">Base : {pretty(it["mat"])} · <code>/vortex give {it["id"]}</code></p>')
    h.append("<ul>" + "".join(f"<li>{html.escape(x)}</li>" for x in it["lore"] if not x.startswith("Cooldown") or True) + "</ul>")
    if it["ench"]:
        h.append('<p class="ench">' + " · ".join(f"{pretty(k)} {v}" for k, v in it["ench"]) + " · Incassable</p>")
    if it["cat"] in ("MYTHIC_WEAPON", "SECRET_WEAPON"):
        h.append('<p class="warn">Craft limité : un seul exemplaire sur le serveur · une arme et une armure mythiques par joueur · impossible à ranger.</p>')
    elif it["cat"] == "LEGENDARY_WEAPON":
        h.append('<p class="warn">Craft limité : 3 exemplaires sur le serveur · 2 armes légendaires maximum par joueur.</p>')
    else:
        h.append('<p class="note">Craft illimité.</p>')
    if rec:
        h.append(f'<details><summary>Recette — {rec["kind"]}</summary><div class="grid">')
        for c in rec["cells"]:
            h.append(f'<div class="cell{" on" if c else ""}">{html.escape(c)}</div>')
        h.append("</div>")
        if rec["note"]:
            h.append(f'<p class="note">{html.escape(rec["note"])}</p>')
        h.append("</details>")
    else:
        h.append('<p class="note">Aucune recette : obtenu auprès de l\'équipe.</p>')
    h.append("</article>")
    return "\n".join(h)


PAGE = """<!doctype html>
<html lang="fr"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Wiki — __SERVER__</title>
<style>
:root{--bg:#f6f7f9;--fg:#15171a;--mut:#5d6570;--card:#fff;--line:#dfe3e8;--cell:#eef0f3}
@media (prefers-color-scheme:dark){:root{--bg:#101215;--fg:#e9ebee;--mut:#9aa3ad;--card:#181b20;--line:#2a2f36;--cell:#222730}}
*{box-sizing:border-box}body{margin:0;font:16px/1.5 system-ui,Segoe UI,Roboto,sans-serif;background:var(--bg);color:var(--fg)}
.wrap{max-width:1100px;margin:0 auto;padding:24px 16px 64px}
h1{margin:0 0 4px;font-size:1.9rem}.sub{color:var(--mut);margin:0 0 20px}
nav a{margin-right:14px;color:inherit}
.bar{display:flex;flex-wrap:wrap;gap:8px;margin:16px 0}
input{flex:1;min-width:220px;padding:10px 12px;border:1px solid var(--line);border-radius:8px;background:var(--card);color:var(--fg);font:inherit}
.chip{padding:8px 12px;border:1px solid var(--line);border-radius:999px;background:var(--card);color:var(--fg);cursor:pointer;font:inherit}
.chip.on{background:var(--fg);color:var(--bg)}
.cards{display:grid;grid-template-columns:repeat(auto-fill,minmax(300px,1fr));gap:14px}
.card{background:var(--card);border:1px solid var(--line);border-top:4px solid var(--c);border-radius:10px;padding:14px}
.card header{display:flex;justify-content:space-between;gap:8px;align-items:start}
.card h3{margin:0;font-size:1.1rem}.badge{font-size:.72rem;color:var(--c);border:1px solid var(--c);border-radius:99px;padding:1px 8px;white-space:nowrap}
.base{color:var(--mut);font-size:.85rem;margin:4px 0}code{background:var(--cell);padding:1px 5px;border-radius:4px;font-size:.85em}
.card ul{margin:8px 0;padding-left:18px;font-size:.92rem}.ench{font-size:.82rem;color:var(--mut)}
.warn{font-size:.82rem;color:#e5484d;margin:6px 0}.note{font-size:.82rem;color:var(--mut);margin:6px 0 0}
details{margin-top:8px}summary{cursor:pointer;font-weight:600;font-size:.92rem}
.grid{display:grid;grid-template-columns:repeat(3,1fr);gap:4px;margin-top:8px}
.cell{min-height:52px;background:var(--cell);border-radius:6px;padding:4px;font-size:.72rem;text-align:center;display:flex;align-items:center;justify-content:center;opacity:.45}
.cell.on{opacity:1;border:1px solid var(--line)}
section{margin-top:36px}h2{font-size:1.3rem;border-bottom:1px solid var(--line);padding-bottom:6px}
.tw{overflow-x:auto}table{border-collapse:collapse;width:100%;min-width:520px;font-size:.92rem}
th,td{text-align:left;padding:8px 10px;border-bottom:1px solid var(--line)}th{color:var(--mut);font-weight:600}
.pls{display:grid;grid-template-columns:repeat(auto-fill,minmax(330px,1fr));gap:14px}
.pl{display:flex;gap:14px;background:var(--card);border:1px solid var(--line);border-radius:10px;padding:14px}
.pl .ico{flex:0 0 48px;color:#5865f2}.pl h3{margin:0 0 4px;font-size:1.05rem}.pl small{color:var(--mut);font-weight:400;font-size:.8rem}
.pl ol{margin:6px 0;padding-left:18px;font-size:.9rem}.cmds{margin:6px 0 0;display:flex;flex-wrap:wrap;gap:4px}
.promo{display:flex;flex-wrap:wrap;gap:8px;align-items:center;justify-content:space-between;background:linear-gradient(90deg,#5865f2,#9b59f5);color:#fff;border-radius:10px;padding:10px 14px;margin:0 0 16px}
.promo a{color:#fff;font-weight:700}
.banner{width:100%;height:auto;border-radius:18px;margin:8px 0}.perks{padding-left:18px}
.btn{display:inline-block;background:#5865f2;color:#fff;text-decoration:none;font-weight:700;padding:12px 20px;border-radius:10px}
.btn.off{background:var(--cell);color:var(--mut)}
.empty{display:none;color:var(--mut)}footer{margin-top:40px;color:var(--mut);font-size:.85rem}
</style></head><body><div class="wrap">
<h1>__SERVER__ — Wiki</h1>
<p class="sub">Items custom, recettes, capacités et grades. <a href="__SHOP__">Boutique</a>__DISCORD__</p>
__PROMO__
<nav><a href="#serveur">Serveur</a><a href="#fonctions">Fonctionnalités</a><a href="#items">Items</a><a href="#legendaires">Armes légendaires</a><a href="#mythic">Règles mythiques</a><a href="#plugins">Plugins</a><a href="#tousplugins">Tous les plugins</a><a href="#shop">Boutique</a><a href="#grades">Grades &amp; boosts</a><a href="#commandes">Commandes</a><a href="#discord">Discord</a></nav>
<section id="serveur"><h2>Le serveur</h2><ul>
<li><b>Version</b> : Minecraft Java <b>1.21.11</b> (Paper).</li>
<li><b>Comptes</b> : <b>premium et crack acceptés</b>. À la première connexion, créez votre mot de passe avec <code>/register</code>, puis utilisez <code>/login</code>.</li>
<li><b>Temps de jeu</b> : <code>/playtime</code> (ou <code>/pt</code>) affiche votre temps de jeu actif ; <code>/playtime top</code> donne le classement. Le temps ne compte plus quand vous êtes inactif.</li></ul></section>
<section id="fonctions"><h2>Fonctionnalités de VortexSMP</h2><ul>
<li><b>Lancement du SMP</b> : les armes légendaires et mythiques se craftent <b>2 jours après le lancement</b>. Chaque craft déclenche un rituel visible par tous.</li>
<li><b>Commandes</b> (<code>/orders</code>) : demandez des items à un prix unitaire (l'argent est mis de côté) ou livrez les commandes des autres joueurs et celles du serveur pour gagner de l'argent.</li>
<li><b>Primes</b> (<code>/bounty</code>) : mettez une prime sur un joueur, les primes se cumulent. Celui qui le tue l'encaisse (une petite taxe est retenue).</li>
<li><b>Spawners</b> (<code>/us shop</code>) : spawners empilables, mobs réels, auto-farm, stockage des drops, améliorations (vitesse, portée, quantité), joueurs autorisés et vente automatique.</li>
<li><b>Rituels de craft</b> (<code>/vortex rituals</code>) : certains items se craftent avec un rituel : après avoir cliqué sur le résultat, patientez, l'item tombe par terre à la fin.</li>
<li><b>Boutique</b> : plus votre fortune est grande, plus les prix d'achat augmentent. Les items faciles à fabriquer se revendent à très bas prix.</li>
<li><b>Crafts spéciaux</b> : trident, pommes d'or, totem, enclume, shulker box, toile d'araignée, slime, miel… ont des recettes modifiées (regardez-les dans <code>/vortex</code> ou en jeu).</li></ul></section>
<section id="items"><h2>Items (__COUNT__)</h2>
<div class="bar"><input id="q" type="search" placeholder="Rechercher un item, une capacité, un ingrédient…" aria-label="Recherche">__CHIPS__</div>
<div class="cards" id="cards">
__CARDS__
</div><p class="empty" id="empty">Aucun résultat.</p></section>
<section id="mythic"><h2>Règles des armes mythiques</h2><ul>
<li>Chaque arme mythique, armure mythique et Secret Weapon ne peut être forgée <b>qu'une seule fois par serveur</b>, définitivement.</li>
<li>Un joueur ne peut porter <b>qu'une seule arme mythique et qu'une seule armure mythique</b> à la fois.</li>
<li>Elles ne se rangent dans <b>aucun conteneur</b> (coffre, ender chest, baril, shulker, entonnoir, bundle…).</li>
<li>Capacité : sneak + clic gauche (Lightspeed : clic droit). Ultime : sneak + clic droit.</li>
<li>Leurs effets permanents restent actifs tant qu'elles sont dans l'inventaire.</li></ul></section>
<section id="legendaires"><h2>Armes légendaires</h2><ul>
<li>Chaque arme légendaire ne peut être <b>craftée que 3 fois sur tout le serveur</b>, et <b>2 armes légendaires maximum par joueur</b>.</li>
<li>Toutes les armes légendaires et mythiques sont <b>incassables</b>, et leurs enchantements ne sont pas limités.</li>
<li>Leurs recettes demandent des ressources rares (étoile du Nether, lingots et blocs de netherite, totems…) : regardez la recette de chaque arme dans la liste d’items.</li></ul></section>
<section id="plugins"><h2>Plugins du serveur</h2><p class="sub">Ce que fait chaque plugin et comment l’utiliser.</p><div class="pls">
__PLUGINS__
</div></section>
<section id="tousplugins"><h2>Tous les plugins de VortexSMP</h2><p class="sub">La liste complète des plugins installés sur le serveur.</p>__ALLPLUGINS__</section>
<section id="shop"><h2>Boutique</h2>
<p class="sub">Commande <code>/shop</code> : boutique du serveur (stock illimité, prix fixes) et boutique des joueurs.</p>
<div class="tw"><table><tr><th>Catégorie</th><th>Items</th></tr>
__SHOPCATS__</table></div>
<ul><li><b>Vendre</b> : <code>/shop sellhand</code> (item en main), <code>/shop sellall</code> (tout l'inventaire hors barre rapide) ou clic droit dans le menu.</li>
<li><b>Boutique des joueurs</b> : <code>/shop sell &lt;prix&gt;</code> met en vente l'item tenu ; une taxe est prélevée sur le vendeur.</li>
<li><b>Menu de vente</b> : <code>/sell</code> ouvre un menu où vous déposez les items à vendre ; ils sont vendus quand vous le fermez.</li>
<li><b>Non vendus</b> : les items qu’on obtient auprès des villageois (glowstone, lapis, laines…), les élytres, le netherite façonné (armures, outils, armes, modèle d’amélioration), la mace, le trident, la ficelle, les shulker boxes et l’ender chest ne sont pas dans la boutique.</li>
<li><b>Non rachetés</b> : les items qui servent au PvP (armes, armures, pommes d’or, totems, seaux, rails, minecarts…) ne rapportent pas d’argent à la vente.</li>
<li><b>Grades</b> : chaque grade peut donner un bonus sur les prix de vente et une réduction sur les prix d'achat (tableau ci-dessous).</li></ul></section>
<section id="grades"><h2>Grades &amp; boosts</h2><div class="tw"><table>
<tr><th>Grade</th><th>XP</th><th>Drops mobs</th><th>Dégâts</th><th>Durée potions</th><th>Cooldowns</th><th>Vente</th><th>Achat</th><th>Effets</th></tr>
__BOOSTS__</table></div></section>
<section id="commandes"><h2>Commandes</h2><div class="tw"><table>
<tr><th>Commande</th><th>Rôle</th></tr>
<tr><td><code>/vortex</code></td><td>Ouvre le menu des items et des recettes</td></tr>
<tr><td><code>/vortex list</code></td><td>Liste les items</td></tr>
<tr><td><code>/vortex preview &lt;item&gt;</code></td><td>Affiche la recette d'un item</td></tr>
<tr><td><code>/vortex boost</code></td><td>Affiche vos boosts de grade</td></tr>
<tr><td><code>/shop</code></td><td>Ouvre la boutique (serveur et joueurs)</td></tr>
<tr><td><code>/shop sellhand</code> · <code>/shop sellall</code></td><td>Vend l\'item en main · tout ce qui est vendable</td></tr>
<tr><td><code>/sell</code></td><td>Menu de vente : déposez des items, ils sont vendus à la fermeture</td></tr>
<tr><td><code>/orders</code> · <code>/orders create &lt;quantité&gt; &lt;prix&gt;</code></td><td>Commandes : livrer des items pour gagner de l\'argent · demander des items</td></tr>
<tr><td><code>/bounty &lt;joueur&gt; &lt;montant&gt;</code> · <code>/bounty</code></td><td>Met une prime sur un joueur · liste des primes</td></tr>
<tr><td><code>/us shop</code> · <code>/us trust &lt;joueur&gt;</code></td><td>Boutique de spawners · autorise un joueur sur le spawner visé</td></tr>
<tr><td><code>/vortex rituals</code></td><td>Liste des crafts à rituel et de leurs recettes</td></tr>
<tr><td><code>/playtime</code> · <code>/playtime top</code></td><td>Votre temps de jeu · classement</td></tr>
<tr><td><code>/shop sell &lt;prix&gt;</code></td><td>Met l\'item tenu en vente aux joueurs</td></tr></table></div></section>
__DISCORD__
<footer>Page générée depuis le plugin VortexEssentials (par ItsVany).</footer>
</div>
<script>
const cards=[...document.querySelectorAll('.card')],q=document.getElementById('q'),empty=document.getElementById('empty');
let cat='ALL';
function apply(){const t=q.value.trim().toLowerCase();let n=0;
cards.forEach(c=>{const ok=(cat==='ALL'||c.dataset.cat===cat)&&(!t||c.dataset.search.includes(t)||c.textContent.toLowerCase().includes(t));c.style.display=ok?'':'none';if(ok)n++});
empty.style.display=n?'none':'block'}
q.addEventListener('input',apply);
document.querySelectorAll('.chip').forEach(b=>b.addEventListener('click',()=>{document.querySelectorAll('.chip').forEach(x=>x.classList.remove('on'));b.classList.add('on');cat=b.dataset.cat;apply()}));
</script></body></html>
"""


def main():
    items = parse_items()
    if not items:
        sys.exit("Aucun item trouvé — vérifiez le parseur.")
    recipes = build_recipes(items)

    order = list(CATS)
    items.sort(key=lambda it: (order.index(it["cat"]), 0))
    cards = "\n".join(card(it, recipes.get(it["id"])) for it in items)

    chips = '<button class="chip on" data-cat="ALL">Tous</button>' + "".join(
        f'<button class="chip" data-cat="{k}">{v[0]}</button>' for k, v in CATS.items())
    boosts = "".join(
        f'<tr><td><b>{html.escape(b["id"])}</b></td><td>×{b["xp"]}</td><td>×{b["drops"]}</td><td>×{b["damage"]}</td><td>×{b["potion"]}</td>'
        f'<td>−{b["cd"]}%</td><td>+{b["sell"]}%</td><td>−{b["disc"]}%</td><td>{html.escape(b["effects"])}</td></tr>' for b in build_boosts()
    ) or '<tr><td colspan="9">Aucun grade configuré.</td></tr>'

    plugins_html = "\n".join(plugin_card(p) for p in load_plugins())
    promo = ('<div class="promo"><span>Rejoins la communauté de <b>' + html.escape(SERVER_NAME)
             + '</b> sur Discord : annonces, aide, événements.</span>'
             + (f'<a href="{html.escape(DISCORD_URL)}">Rejoindre →</a>' if DISCORD_URL
                else '<a href="#discord">En savoir plus →</a>') + '</div>')
    page = (PAGE.replace("__SERVER__", html.escape(SERVER_NAME)).replace("__SHOP__", html.escape(SHOP_URL))
            .replace("__DISCORD__", f' · <a href="{html.escape(DISCORD_URL)}">Discord</a>' if DISCORD_URL else "")
            .replace("__COUNT__", str(len(items))).replace("__CHIPS__", chips)
            .replace("__CARDS__", cards).replace("__SHOPCATS__", shop_rows())
            .replace("__PROMO__", promo).replace("__PLUGINS__", plugins_html).replace("__ALLPLUGINS__", all_plugins_html()).replace("__DISCORD__", discord_section()).replace("__BOOSTS__", boosts))
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(page, encoding="utf-8")
    print(f"OK : {OUT} ({len(items)} items, {len(recipes)} recettes)")


if __name__ == "__main__":
    main()
