# VortexEssentials 2.3.0
**Auteur : ItsVany** — Paper 1.21.11 · Java 21 · Maven

Fusion de **VortexTools** (23 items custom) et **VortexLimit** (limites d'enchantements / potions), plus un système de **boosts par groupe LuckPerms**.

## Compilation
```
mvn clean package      ->  target/VortexEssentials-2.3.0.jar
```

## Contenu
| Module | État |
|---|---|
| Catalogue des 23 items, menu, `/vortex give` | ✅ |
| Limites enchantements / potions / bypass (+ items Vortex exemptés) | ✅ |
| Règles mythiques (forge unique, une par joueur, pas de rangement) | ✅ |
| Armes mythiques : effets permanents, passifs, capacités et ultimes | ✅ |
| Outils mythiques : Haste V, zone cubique activable (sneak + clic droit) | ✅ |
| Outils : Smelter (x3), Explosive (x2), Lumberjack (x3) | ✅ |
| Armes légendaires : Crossbow, Reaper, Excalibur, Evoker, Chrono, Arachne, Magma | ✅ |
| Boosts par groupe (XP, drops, dégâts, cooldowns, effets) | ✅ |
| Consommables : Panacea (5 charges), Crystallization Shard, Agony | ✅ |
| Recettes des 23 items (craft + forge) dans `recipes.yml`, aperçu dans le menu | ✅ |
| Resource pack optionnel | ⏳ |

Les recettes sont dans `plugins/VortexEssentials/recipes.yml` (modifiables, `/vortex reload`). `/vortex preview <item>` ou clic gauche dans le menu les affiche.

## Commandes
| Commande | Permission |
|---|---|
| `/vortex` (alias `/vt`) menu, `/vortex list`, `/vortex preview <item>` | `vortexessentials.use` |
| `/vortex boost [list \| info [joueur]]` | `use` (voir les boosts d'un autre joueur : `admin`) |
| `/vortex recipes` (diagnostic : recettes chargées, erreurs, items sans recette) | `vortexessentials.admin` |
| `/vortex give <item> [joueur] [qté]` | `vortexessentials.admin` |
| `/vortex mythic status \| reset <item>` | `vortexessentials.admin` |
| `/vortex reload` | `vortexessentials.admin` |
| `/vortex limit ...` = `/vortexlimit` (`/vl`) | `vortexessentials.limit.admin` |

Bypass total des limites : `vortexessentials.limit.bypass`.

## Boosts par groupe
Section `boosts:` de `config.yml`. Un joueur reçoit le boost s'il a la permission du groupe
(`group.<nom>` par défaut, que LuckPerms accorde automatiquement, héritage compris).
Boosts : `xp`, `drops` (mobs), `damage`, `potion-duration` (durée des potions bénéfiques), `cooldown-reduction` (capacités des items), `effects` (potions permanentes : speed, haste, luck, regeneration...).
`stacking` : `highest` (défaut), `multiply` ou `add` quand un joueur est dans plusieurs groupes.

## Capacités (résumé)
- **Mythiques** — capacité : sneak + clic gauche (Sealord, Mintus) / clic droit (Lightspeed) ; ultime : sneak + clic droit.
- **Outils mythiques** — sneak + clic droit : zone ON/OFF (`mythic-tools.size`, 3 par défaut).
- **Excalibur** clic droit · **Chrono Sword** sneak + clic droit (marque / rembobine, sauve de la mort) ·
  **Evoker Wand** sneak + clic gauche (crocs) / clic droit (renvoi) · **Sceptre of Arachne** sneak + clic gauche (empower) / clic droit (tir) ·
  **Reaper Scythe** et **Magma Club** : à l'attaque · **Blazing Crossbow** : flèches enflammées.

## Migration
- Items déjà créés : clés PDC de VortexTools / VortexLimit reconnues.
- Limites : copiez `enchantments:`, `potions:`, `bypass:` dans `limits.yml`.
- Permissions : `vortextools.*` / `vortexlimit.*` -> `vortexessentials.*`.
- Après mise à jour, l'ancien `config.yml` n'est pas réécrit : supprimez-le ou copiez les sections `mythic-*`, `tools`, `legendary`, `boosts`.

## Boutique et wiki
- `docs/BOUTIQUE_TEBEX.md` : mise en place d'une boutique Tebex (commandes LuckPerms / `vortex give`) et rappel des règles Mojang.
- `docs/wiki/index.html` : wiki statique (recherche, filtres, recettes, grades). Régénéré par `python3 tools/generate_wiki.py`
  (personnalisez `SERVER_NAME`, `SHOP_URL`, `DISCORD_URL` en haut du script).
- `docs/PUB_DISCORD.md` : textes de pub Discord prêts à copier.
- `tools/plugins.yml` : plugins présentés dans le wiki (les fiches marquées `verify: true` sont masquées tant que vous ne les complétez pas).

## Nouveautés 1.4.0
- **Jugas** (pelle mythique) : zone 3×3×3, minage instantané, autosmelt (sable → verre, argile → briques...).
- **Outils mythiques** (Fractured, Terraloop, Jugas) : cassent tout instantanément (plus de Hâte). Terraloop abat aussi les arbres.
- **Réglages d'outil** (sneak + clic droit, ou `/vortex tool`) : zone, autosmelt, abattage d'arbres, **vitesse de minage vanilla** — chacun activable/désactivable, sauvegardé sur l'item et affiché dans le tooltip.
- **Effets permanents configurables** (`mythic-weapons.<arme>.effects`) : Sealord ne garde que Résistance II (plus de Conduit Power ni Luck). Les enchantements d'un item se surchargent via `item-enchants`.
- **HUD des cooldowns** dans l'action bar (`/vortex hud`).
- **Menus** : nouveau style, profil du joueur, fonctionnalités, boosts, aperçu de recette, vraies potions colorées dans le menu des limites.
- **Tooltips** refaits (rareté, puces, réglages en direct).
- **Administration** (`/vortex admin`) : items/crafts/potions désactivés, limites d'objets, crafts personnalisés (éditeur graphique).
- **Items personnalisés façon ExecutableItems** (`/vortex citem`) : créés depuis l'item en main, actions au clic/coup, cooldown, consommable, permission.
- **Resource pack** : voir `resourcepack/README.md`.

## Nouveautés 1.5 et 1.6
- **Boutique** (`/shop`) : boutique du serveur (12 catégories, 241 items, **stock illimité**, prix fixes dans `shop.yml`) et boutique des joueurs
  (annonces, 10 catégories automatiques, taxe vendeur). Monnaie : **VaultUnlocked / Vault** + un plugin d'économie.
  Édition en jeu pour les admins (`/shop admin` > *Mode édition* : prix, activer/désactiver, ajouter avec le curseur, supprimer) ou par `/shop manage`.
  Les items Vortex (`vortex:<id>`) et personnalisés (`citem:<id>`) peuvent être vendus ; les armes mythiques sont exclues.
- **Boosts de rang pour la boutique** : `sell-bonus` (bonus de vente) et `buy-discount` (réduction d'achat) par grade.
  `/vortex boost create <id>`, `/vortex boost set <id> sell|discount <valeur>`, ou l'éditeur graphique (`/vortex boost edit`).
  Événements temporaires : `/vortex boost event start sell|buy|xp|... <multiplicateur> <minutes>`.
- **Création de boosts en jeu** (`boosts.yml`) : XP, drops, dégâts, durée des potions, cooldowns, vente, achat, permission, effets.
- **Discord** (webhook) : arrivées, départs, morts, succès, chat (sens unique), démarrage/arrêt, armes mythiques forgées, événements de boost,
  message de statut mis à jour. `config.yml` > `discord`, test : `/vortex discord test`.
- **Langues** : `language: fr|en|es` (fichiers `lang/<code>.txt`, `/vortex lang <code>`). Ajouter une langue = copier `en.txt` et le traduire.
- **Resource pack** : `resource-pack` (envoi par le plugin) et diagnostic `/vortex pack hash|status|check|send`.
- **Recettes difficiles** par défaut (`/vortex recipes preset hard|normal`, ancien fichier sauvegardé en `recipes.yml.bak`).

## Mise en route de la boutique
1. Installez **VaultUnlocked** (ou Vault) et un plugin d'économie.
2. Au premier démarrage, `shop.yml` est créé depuis le catalogue par défaut ; ajustez les prix (`/shop admin` > Mode édition).
3. Donnez vos grades (LuckPerms) puis créez leurs boosts : `/vortex boost create vip`, `/vortex boost set vip sell 0.10`, `/vortex boost set vip discount 0.05`.

## Nouveautés 1.7
- **Catalogue complet de la boutique** : les 1393 items obtenables du jeu (1.21.11) en 17 catégories, avec des prix selon la rareté et le coût de craft
  (outils/armures calculés d'après leurs matériaux, minerais, mobs, blocs, nature, couleurs, musique, modèles...). Régénérable : `python3 tools/gen_shop_defaults.py`.
  Sur une boutique existante : `/shop manage import` ajoute les nouveautés sans toucher à vos prix ; `/shop manage reset confirm` repart du catalogue.
- **Pas de revente des items de villageois** (et des émeraudes) : plus de boucle échanges → émeraudes → boutique. Items et armures de métier,
  laines, terracottas, teintures, bannières, lits, etc. n'ont plus de prix de vente (achat possible).
- **Prix PvP relevés** à l'achat : totems, pommes d'or, TNT, cristaux, ender pearls, netherite, élytres, trident, tir à l'arc, obsidienne, ingrédients de potions...
- **Menu de vente `/sell`** (alias `/vendre`, ou *Boutique > Menu de vente*) : on dépose les items, ils sont vendus à la fermeture
  (`shop.sell-menu.auto-sell-on-close`, sinon bouton « Vendre maintenant »). Les items non vendables sont rendus.
- **Menus de dépôt corrigés** (limites d'objets, items/crafts désactivés, ajout à la boutique) : on peut de nouveau prendre un item dans son
  inventaire avec le curseur (ou le tenir en main) avant de cliquer sur « Ajouter ».
- **Netherite Explosive Pickaxe : 3×3×3** (`tools.netherite-explosive-pickaxe-size`), l'Explosive Pickaxe reste en 2×2×2.
- **Sealord** : effets passifs dans l'eau en plus (Respiration aquatique, Grâce du dauphin III, Force, Régénération II) : `mythic-weapons.sealord.water-effects`.
- **Garde-fou** : un item ne se revend jamais plus cher qu'il ne s'achète, boosts de grade compris.

## Nouveautés 1.8
- **Temps de jeu** : `/playtime [joueur|top]` (alias `/pt`, `/temps`), temps actif (hors AFK), repris du temps vanilla, affiché dans le profil ; paliers de récompenses dans `playtime.rewards`.
- **Enchantements** des armes mythiques et légendaires mis à jour d'après vos captures ; elles restent hors limites. **Toutes les armes légendaires et mythiques sont incassables.**
- **Crafts durcis** pour les armes légendaires (étoile du Nether + netherite…) et mythiques (Lightspeed : 4 blocs de netherite, 2 étoiles, totem, élytre). Crafts des outils allégés :
  Smelter's Pickaxe et Lumberjack's Axe (fer) se font dès l'ouverture, sans Nether ; outils mythiques moins chers. Utilisez `/vortex recipes preset hard` pour recharger.
- **Armes légendaires : 3 exemplaires max sur le serveur, 2 par joueur** (`legendary-limits`, `/vortex legendary status|reset|resetplayer`).
- **Magma Club** : Éruption (sneak + clic gauche) et Vague de magma (sneak + clic droit). **Blazing Crossbow** : Barrage (sneak + clic gauche) et carreau explosif tous les 4 tirs.
- **Sealord** : dans l'eau, +2 dégâts d'attaque (attribut) à la place de la Force. **Mintus** : Rallying Cheer donne +4 dégâts d'attaque aux alliés au lieu de Force II.
- **Cristallisation** : invulnérable pendant l'effet. **Panacea** : 3,5 cœurs par charge (`panacea.heal-hearts`).
- **Boutique** : les items utiles au PvP (armes, armures, pommes d'or, perles, totems, seaux, rails, minecarts…) ne sont plus rachetés ; `/shop manage import` applique aussi ces règles à votre `shop.yml`.

## Correctifs 1.8.1
- **Reaper Scythe** : nouvelle capacité *Moisson d'âmes* (sneak + clic droit, cd 35s) : draine tous les ennemis à 6 blocs (dégâts + Wither) et vous soigne de 50 % des dégâts infligés. Réglages : `legendary.reaper-scythe.harvest`.
- **Sceptre of Arachne** : les toiles disparaissent avant que vous les touchiez (rayon `web-radius`, aussi devant vous), avec particules de fibres et aura de cendres blanches. Le Speed de compensation est retiré.
- **Excalibur** : annule maintenant **8** coups au lieu de 3 (`legendary.excalibur.hits`).
- Correction d'un import manquant (`LegendaryListener`) qui empêchait la compilation de la 1.8.0 de se terminer.

## Correctifs 1.8.2
- **Boutique** : retirés du catalogue les élytres, le netherite façonné (armures, outils, armes, modèle d'amélioration), la mace, le trident, la ficelle (dupe), les shulker shells/boxes, l'ender chest, les bundles,
  le breeze rod (remplacé par le wind charge, 14) et tout ce qu'on obtient auprès des villageois (glowstone, lapis, redstone, perles, laines, teintures, outils de métier…, émeraudes comprises).
  La catégorie « Items Vortex » n'existe plus. Totem : 4000. Pour nettoyer une boutique existante : `/shop manage purge confirm` (ou `/shop manage reset confirm`).
- **Crafts** : outils mythiques plus durs (4 blocs de netherite, 2 étoiles du Nether, 2 débris, l'outil) ; Explosive Pickaxe (diamant) bien plus facile (TNT + poudre à canon) ; Super Lumberjack's Axe plus dure (4 blocs de diamant + 4 blocs de fer + la hache). `/vortex recipes preset hard` pour recharger.

## Correctifs 1.8.3
- **Prix de la boutique selon la rareté** : 5 paliers (commun, peu commun, rare, épique, légendaire). Plus l'item est rare, plus il est cher à l'achat (×1,15 / ×1,3 / ×1,5 dès le palier rare)
  et mieux il est racheté (28 % du prix d'achat pour un commun, jusqu'à 40 % pour un légendaire). La revente ne dépasse jamais l'achat. `/shop manage reset confirm` pour repartir de ces prix.
- **Grades par défaut** : vip, mvp, donateur, media, veteran (valeurs de vos captures : XP, drops, dégâts, potions, cooldowns, vente/achat, Chance I à V).
  Si vous avez déjà créé des grades en jeu, ils priment : supprimez `plugins/VortexEssentials/boosts.yml` pour utiliser ces valeurs.

## Nouveautés 1.9
- **Nouvelles armes mythiques** : *Skyfall* (arc : flèches perforantes, Arrow Rain) et *Voidrender* (arbalète : Rail Shot, Blink Bolt). **Arme secrète** : *Umbral Scythe* (base mace, 12 dégâts, casse-bouclier, aucun dégât de chute, Eclipse Leap),
  inspirée de ManePear's Scythe ; elle n'apparaît ni dans les menus, ni dans le wiki, ni dans l'aperçu de recette. Ces armes sont non enchantables et non modifiables.
- **Armures mythiques** : *Colossus Plate*, *Phantom Treads*, *Radiant Crown* (effets portés, passif, capacité par double sneak). Une arme ET une armure mythique par joueur.
- **/smp start|stop|status|delay** : avant le lancement puis pendant 48 h (`smp.weapon-delay-hours`) les armes légendaires et mythiques ne sont pas craftables ; ensuite, **rituel** à chaque craft (foudres, titre pour tous).
- **Maintenance** (`/maintenance on|off|add|remove|list`) : seuls le staff, la liste de maintenance et (option) la whitelist entrent.
- **Staff** : `/jail` `/unjail` `/setjail` `/jails` `/freeze` `/staffxray` (x-ray staff avec halos), `/announce` (alias `/broadcast`, `/bc`).
- **Pioches 3×3** : zone carrée de 9 blocs face au bloc miné (plus de cube de 27). **Module Silk Touch** pour les Explosive Pickaxe et les outils mythiques (menu d'options de l'outil).
- **Outils normaux enchantés** : fer (Efficacité I, Solidité I), diamant (Efficacité II, Solidité I, Fortune I), netherite (Efficacité III, Solidité II, Fortune II).
- **Réglages par défaut** (une fois, `/vortex admin defaults` pour réappliquer) : limites d'objets, items désactivés (armures en netherite, respawn anchor) et crafts personnalisés (trident, pommes d'or, honey bottle, slime ball, poudre, enclume, toile, ominous bottle).
- **Boutique** : revente réduite pour tout ce qui est facile à obtenir (fermes, générateurs, crafts bon marché, lodestone…) et prix d'achat relevés pour les ingrédients des armes légendaires et mythiques.

## Nouveautés 2.0
- **Anti-triche intégré supprimé** (GrimAC et Orebfuscator sont déjà installés).
- **Secret Weapons** : nouvelle catégorie avec l'**Umbral Scythe** (base mace, densité V, brèche IV, rafale II, aura du feu II, tranchant V, plus de dégâts fixes, effets permanents Résistance au feu, Force II, Résistance II).
  Capacités : *Eclipse Leap* (sneak + clic droit), *Reaper Chains* (sneak + clic gauche : une chaîne vous tire vers la cible visée ou un bloc), *Shatterer* (touche d'échange de main : le sol éclate, 6 cœurs de dégâts). 
- **Armes légendaires** : Tranchant V ; **armes mythiques** : Tranchant VI. Nouveaux effets permanents : Skyfall, Voidrender, Sealord, Mintus, Colossus Plate, Phantom Treads, Radiant Crown. Plus de Protection contre les explosions sur les armures mythiques.
- **Ember Greaves** : jambières mythiques (immunité au feu, les attaquants brûlent, *Flame Nova* au double sneak).
- **Éditeur de recettes en jeu** : `/vortex recipes edit` (clic sur les items de votre inventaire pour composer la grille). Les modifications vont dans `recipes-custom.yml`, appliqué par-dessus `recipes.yml` : fini la suppression du fichier à chaque mise à jour. `/vortex recipes preset hard` ne touche plus à vos modifications.
- **Craft illimité / limité** affiché sur chaque item du menu et sur la page de recette.
- **Mace** : limite configurable (`mace-limits`, `/vortex mace status|reset|resetplayer`). **/smp newseason confirm** remet à zéro les compteurs pour une nouvelle saison.
- **/orders** (commandes façon DonutSMP, joueurs et serveur), **/bounty** (primes), **/us** (spawners : piles, mobs réels, auto-farm, améliorations, joueurs autorisés, limites, hologrammes, vente auto, journal), **crafts à rituel** (`/vortex ritual set <craft> <secondes>`, menu `/vortex rituals`).
- **/servermenu** : gamerules, Nether/End on-off, soigner/nourrir/give à tous... **/staffmod** : mode staff à paliers configurables en jeu (`/staffmod menu`).
- **Boutique** : prix d'achat qui montent avec la fortune, revente plafonnée pour les items faciles à crafter, `/shop add <achat> [vente]` avec l'item en main.
- **Crafts par défaut** mis à jour (trident, pommes d'or, totem, enclume, shulker box, toile, slime, miel, poudre, bouteille sinistre) : `/vortex admin defaults force` pour les réappliquer.
- **Boost « staff »** par défaut (6ᵉ groupe, lapis) : supprimez `boosts.yml` pour récupérer les groupes par défaut.
- **Menu « Systèmes du serveur »** (bouton dans `/vortex`, ou `/vortex services`) : boutique, vente, commandes (orders), primes, spawners, rituels, boosts, temps de jeu et, selon les permissions, mode staff, éditeur de recettes, admin du serveur, paliers du staff.
- **Nouveaux crafts par défaut** : bouteille d'expérience ×4 (magma cream, fruit de chorus, éclats d'améthyste, yeux d'Ender, fiole vide) et éclat d'écho ×2 (sculk, éclats d'améthyste, diamants).
- **Wiki** : liste complète des 56 plugins du serveur (7 Paper, 49 Bukkit) dans `tools/plugins.yml` (`all_plugins`).

## 2.0.2
- **Umbral Scythe** : plus de Tranchant, **Rafale III** ; 5 effets permanents (Résistance II, Force II, Résistance au feu, Vitesse II, Tissage). **Reaper Chains** : 3 chaînes en hélice (nouveaux effets visuels), aucune cible ni bloc nécessaire (elles filent droit devant si rien n'est visé). **Shatterer** : onde de choc (rayon 9, jusqu'à 10 cœurs de dégâts, projection, paralysie, faiblesse, obscurité, absorption pour le porteur).
- **Éditeur de recettes** : modifie aussi les crafts personnalisés (pommes d'or, trident, totem...), pas seulement les items Vortex.
- **Spawners** : ajout de tortue, nautile, armadillo, araignée bleue, slime, cube de magma, sorcière, zombie-villageois, villageois, breeze, évocateur, ghast, wither squelette et shulker ; améliorations (vitesse, portée, quantité) bien plus chères.
- **Traductions** anglais et espagnol des nouvelles fonctionnalités (`lang/en.txt`, `lang/es.txt`, `language: en` dans la config). Après mise à jour, supprimez les anciens fichiers `plugins/VortexEssentials/lang/*.txt` pour récupérer les nouvelles traductions.

## 2.0.3
- **Reaper Chains** : 3 charges enchaînées avec un seul cooldown (20 s, fenêtre de 6 s) — 1) vous hisse dans les airs, 2) vous projette sur l'ennemi, 3) vous fait fuir. Une chaîne en double hélice de couleur différente par charge.
- **Reaper Scythe** : uniquement dans la catégorie Légendaires (elle n'apparaît plus dans Secret Weapons).

## 2.0.4
- **Correction : les effets permanents de l'Umbral Scythe ne s'appliquaient pas** (la boucle des effets ignorait la catégorie Secret Weapons). Résistance II, Force II, Résistance au feu, Vitesse II et Tissage fonctionnent maintenant dans l'inventaire.
- **Umbral Scythe** : Wind Burst I et Looting V (plus de Wind Burst III).
- **Reaper Chains** : 3 charges **libres** (un cooldown de 20 s, fenêtre de 6 s) : chaque charge part là où vous visez (ciel, ennemi, bloc, ou droit devant pour fuir).
- **Shatterer** : rayon 10, jusqu'à 12 cœurs de dégâts, **les ennemis sont projetés très haut dans les airs** et le porteur s'élève aussi pour enchaîner un smash de mace.

## 2.1.0
- **Stormpiercer** : lance mythique (base netherite spear) — passif : brise les boucliers et foudre en chaîne tous les 4 coups ; *Thunder Javelin* (sneak + clic droit : javelot de foudre qui traverse tous les ennemis alignés) ; *Skewer Dash* (touche d'échange de main : charge fulgurante qui empale et projette). Effets permanents Force, Vitesse, Saut.
- **Potions spéciales** (catégorie Consommables, jetables) : **All in One** (Force I, Vitesse I, Résistance au feu I pendant 8 min) et **Holy Master** (Soin instantané II puis Résistance V et Lenteur V pendant 30 s). Recettes modifiables avec l'éditeur.
- **Nouveau resource pack** : textures peintes 32×32 pour tous les items custom, états de tension de l'arc Skyfall (voir `resourcepack/README.md`).

## 2.1.1
- **Limites de craft pour les crafts personnalisés** (pommes d'or, totem, enclume...) : nombre maximal de crafts sur le serveur et par joueur, 0 = illimité. Réglage dans l'éditeur de recettes (`/vortex recipes edit`, boutons de limite) ou avec `/vortex craftlimit <craft> <serveur> <joueur>` (`list`, `reset <craft>`). Les crafts en Shift + clic sont refusés pour les crafts limités.
- **Menu « Crafts spéciaux »** (`/vortex crafts`, ou le bouton du menu Systèmes du serveur) : tous les crafts personnalisés avec leur recette et la mention illimité / limité (nombre restant).

## 2.2.0
- **Vérifié par compilation** : le code des deux plugins compile sans aucune erreur contre les vraies sources de l'API Paper 1.21.11 ; noms de matériaux, effets, enchantements, entités, commandes, permissions et fichiers de langue contrôlés.
- **/orders** accepte maintenant **tous les items** (enchantés, nommés...) : la livraison exige le même item.
- **Éditeur de boutique en jeu** : `/shop editor` avec l'item en main (prix d'achat et de vente, catégorie, ajout, mise à jour, retrait), aussi dans le menu Systèmes du serveur.
- **Resource pack amélioré** : armures mythiques portées, arbalètes avec états de charge, arc avec corde visible, décors propres à chaque arme (voir `resourcepack/README.md`). Option `crossbow-models`.

## 2.3.0
- **Reaper Chains** : la chaîne **blesse l'entité accrochée** (10 PV) ; plantée dans un bloc, elle déclenche une **onde de choc** (rayon 5, dégâts, projection). Réglages : `chains.damage` et `chains.shockwave`.
- **Eclipse Leap** (très amélioré) : bond bien plus haut et plus loin ; au décollage, éclipse (dégâts, cécité 3 s, projection des ennemis proches) ; Résistance III et Vitesse III pendant le vol ; à l'atterrissage, **écrasement** (rayon 7, 14 PV, projection). Cooldown 16 s.
- **Stormpiercer** : Brèche II ajoutée.
- **Spawners** : chaque amélioration exige désormais des **matériaux du mob** en plus de l'argent (Zombie : chair putréfiée, Creeper : poudre à canon, Blaze : bâtons de blaze, Shulker : carapaces...). Pris dans le stock du spawner puis dans l'inventaire. Tout se règle dans `spawners.upgrade-materials`.
- **Édition directe des crafts dans les menus** : bouton « Éditer ce craft » (admin) sur la page de recette de `/vortex` et dans le menu « Crafts spéciaux » (ou Shift + clic). L'éditeur propose la grille, la **limite de craft par serveur et par joueur**, le compteur et la **durée du rituel**.
- **Limites de craft pour tous les items Vortex** (clé `item:<id>`) et plus seulement les crafts personnalisés : `/vortex craftlimit item:<id> <serveur> <joueur>` ; l'affichage « illimité / limité » les prend en compte.
- **Resource pack** : nouveau rendu pixel art net (fini les halos flous), voir `resourcepack/README.md`.
