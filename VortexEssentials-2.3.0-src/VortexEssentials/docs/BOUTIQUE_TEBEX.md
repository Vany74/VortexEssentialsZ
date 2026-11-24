# Boutique web (Tebex) pour VortexEssentials

Tebex vend les paquets en ligne puis exécute des **commandes sur votre serveur** après paiement.
Variables utiles dans les commandes : `{username}` (pseudo utilisé à l'achat) et `{id}` (UUID du joueur).

## 1. Mise en place
1. Créez votre boutique sur Tebex et ajoutez votre serveur Minecraft. **Serveur ouvert aux comptes crack** : choisissez le type de boutique
   **« Minecraft: Offline »** à la création (voir §1 bis) ; sinon « Minecraft: Java Edition ».
2. Installez le plugin Tebex sur le serveur et liez-le avec la clé secrète affichée dans le panel
   (suivez l'écran « Game Servers » du panel : il donne la commande exacte).
3. Dans le panel, créez vos **Packages** (voir §3). Pour chaque commande de paquet, cochez
   **« exécuter uniquement si le joueur est en ligne »** : `/vortex give` ne fonctionne que sur un joueur connecté.
4. **Testez avec un paiement manuel** (fonction « manual payment » du panel), jamais avec un vrai achat.

## 1 bis. Serveur mixte premium + crack (online-mode=false)
La documentation de Tebex indique que, pour un serveur hors-ligne, le plugin s'installe pareil mais la boutique doit être créée avec le type
**« Minecraft: Offline »** : Tebex ne vérifie alors pas les pseudos (sinon les pseudos crack non achetés chez Mojang seraient refusés).
Conséquences à connaître :
- n'importe qui peut acheter pour **n'importe quel pseudo** (vous ne pouvez pas prouver que l'acheteur possède le compte) ;
  gardez AuthMe actif pour que personne ne prenne la place d'un autre joueur sur le serveur ;
- les UUID sont ceux du mode hors-ligne (calculés d'après le pseudo) : utilisez **`{username}`** dans vos commandes plutôt que `{id}` ;
- le type de boutique se choisit à la création : vérifiez-le avant de publier vos packages (changer après peut imposer de recréer la boutique) ;
- un joueur qui change de pseudo perd ses achats liés à l'ancien pseudo (le grade est attaché au nom) : prévoyez une procédure staff.

## 2. ⚠️ Avant de vendre : règles de Mojang
Les règles commerciales de Mojang/Microsoft interdisent en pratique de vendre des **avantages de gameplay**
(armes, outils, enchantements, boosts d'XP ou de drops, etc.). Les ranks cosmétiques ou de confort sont acceptés.
Dans ce plugin, **tout ce qui donne un avantage** — armes légendaires/mythiques, outils, boosts de grade
(XP, drops, dégâts, cooldowns, effets) — entre dans cette catégorie.

Approche prudente :
- vendez : grades **cosmétiques** (préfixe, couleur de chat, particules, tag, accès à des salons Discord), cosmétiques, soutien au serveur ;
- gagnez les items et les boosts **en jeu** (craft, vote, temps de jeu, événements) ;
- si vous vendez quand même des grades avec boosts, vérifiez le texte officiel (aka.ms/MinecraftEULA et
  « Commercial Usage Guidelines ») : les règles évoluent et ce guide ne remplace pas leur lecture.

Les sections ci-dessous décrivent la **partie technique**, utilisable quel que soit ce que vous décidez de vendre.

## 3. Packages et commandes

### Grade avec boosts (abonnement mensuel)
Les noms de groupes doivent correspondre aux ids de `boosts.groups` dans `config.yml` (`vip`, `premium`, `legend`),
ou à la `permission:` que vous y avez mise.

| Moment Tebex | Commande |
|---|---|
| Initial | `lp user {username} parent add vip` |
| Renewal | *(rien : le grade est déjà là)* |
| Expiry | `lp user {username} parent remove vip` |
| Chargeback / Refund | `lp user {username} parent remove vip` |

Achat unique pour 30 jours (sans abonnement) : `lp user {username} parent addtemp vip 30d`.

### Montée de grade (VIP → Premium)
Package « Upgrade Premium » :
`lp user {username} parent remove vip` puis `lp user {username} parent add premium`.
Proposez aussi la différence de prix à payer (un joueur VIP ne doit pas repayer le tarif plein).

### Item (si vous choisissez d'en vendre)
| Item | Commande |
|---|---|
| Panacea | `vortex give panacea {username} 1` |
| Crystallization Shard (×5) | `vortex give crystallization_shard {username} 5` |
| Agony (×3) | `vortex give agony {username} 3` |
| Outil / arme | `vortex give <id> {username} 1` |

`/vortex list` donne tous les ids. `give` **contourne aussi les limites de craft des armes légendaires** (3 par arme, 2 par joueur) :
ne les vendez pas non plus par ce moyen. **Ne vendez pas les armes mythiques par `give`** : elles sont conçues
pour n'exister qu'une fois par serveur ; `give` contourne cette règle et n'enregistre pas la forge.

### Chargeback / remboursement d'un item
Retirer un item déjà donné n'est pas automatique. Prévoyez un process manuel (sanction du compte, retrait
en jeu) ou évitez de vendre des items non récupérables.

## 4. Après la mise en place
- Mettez le lien de la boutique et les prix dans le wiki : éditez `SHOP_URL` en haut de `tools/generate_wiki.py`,
  relancez le script, puis hébergez `docs/wiki/index.html` (n'importe quel hébergement statique).
- Les boosts actifs d'un joueur : `/vortex boost info <joueur>`.
- Après un achat de grade, le joueur reçoit ses boosts immédiatement (aucun redémarrage nécessaire).
