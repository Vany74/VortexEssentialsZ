# KingdomSMP — source project

**Version : 0.5.0 (développement)**  
Minecraft **Paper 1.21.11** · **Java 21** · **Maven** · Auteur : **ItsVanyy**

## Fonctions présentes dans le code

- 12 couronnes : Nature, Wrath, Heaven, Gl1tched, Adaptation, Wealth, Fractionate, Void, Parade, King, Tyrannus et Hériclés.
- Attribution aléatoire d'une couronne normale au premier join, une couronne par joueur.
- Passifs et pouvoirs actifs spécifiques, progression par éliminations : normales 3/5/8, légendaires 3/5/8/12/15.
- Couronnes avec **Materials Left** : départ et maximum 10; fracture à 3 ou moins (au-dessus de zéro), ce qui désactive seulement les pouvoirs actifs; à zéro la couronne est cassée et les passifs/pouvoirs sont désactivés jusqu'à réparation.
- Perte de matériaux par mort et au fil du temps configurable; matériaux craftables et récompense de kill soumise à l'anti-farm persistant.
- **Crown Upgrader** craftable : déverrouille une capacité, conservée dans les données du joueur.
- **Crown Repairer** craftable : consomme des Crown Materials et restaure une couronne cassée.
- Menus GUI : menu principal, pouvoirs, langue, confiance et administration; attribution de couronnes à un joueur en ligne depuis le GUI admin.
- Traductions individuelles FR/EN/ES, sauvegardées par UUID.
- Commandes avec suggestions Tab, profils, cooldowns persistants, confiance KingdomSMP, sauvegardes YAML.
- Pacte de restauration après absorption par King : objectifs configurables de kills éligibles + Crown Materials, validation par un administrateur puis réparation avec Crown Repairer.
- Blocage des pouvoirs dans la zone configurée et dans les régions WorldGuard où le PvP est refusé, si WorldGuard est installé.
- Paramètres pour méthodes d'activation et cooldowns individuels.
- Resource pack de développement avec textures pixel-art, modèles d'icônes et aperçu mis à jour.

## Compiler

1. Installer un JDK 21 et Maven.
2. Ouvrir le dossier `KingdomSMP` dans IntelliJ IDEA comme projet Maven.
3. Dans un terminal à la racine du projet, lancer `mvn clean package`.
4. Si la compilation réussit, le JAR sera créé dans `target/KingdomSMP-0.5.0.jar`.
5. Tester sur une copie Paper 1.21.11 avant de le déployer sur le serveur principal.

## Commandes

- `/kingdom` ou `/kingdom menu` — menu principal.
- `/kingdom me` — profil.
- `/kingdom powers` — GUI des pouvoirs.
- `/kingdom cast <1-5>` — activer un pouvoir débloqué.
- `/kingdom language <fr|en|es>` — langue individuelle.
- `/kingdom trust <joueur>` — bascule la confiance avec un joueur en ligne.
- `/kingdom crown <joueur> <couronne>` — administrateur, attribuer une couronne.
- `/kingdom ritual <joueur> <couronne légendaire>` — rituel administratif.
- `/kingdom item <material|upgrader|repairer> [quantité]` — donner un objet de test.
- `/kingdom pact status` — voir la progression; les admins peuvent utiliser `/kingdom pact start|approve|cancel <joueur>`.
- `/kingdom remove <joueur>` — retirer une couronne.
- `/kingdom save`, `/kingdom reload`, `/kingdom status` — gestion administrative.

## Permissions

- `kingdomsmp.use` — usage de base (par défaut : tous).
- `kingdomsmp.powers` — accès aux pouvoirs (par défaut : tous).
- `kingdomsmp.admin` — commandes et GUI d'administration (par défaut : opérateurs).
- `kingdomsmp.ritual` — rituel légendaire (par défaut : opérateurs).

## Limites connues — ne pas considérer comme prêt à publier

- La compilation Maven **n'a pas été exécutée** dans l'environnement de création : Maven et les dépendances ne sont pas disponibles hors ligne. Ce ZIP n'inclut donc pas de JAR compilé.
- Le pack est un pack de développement : les icônes d'inventaire sont présentes, mais le rendu de la couronne portée comme armure doit être validé et complété en jeu.
- `WorldGuard` est appelé via son API lorsqu'il est présent. `Vault` et `BetterTeams` sont actuellement détectés comme dépendances optionnelles, mais leurs API ne sont pas encore intégrées; `/trust` est la liste propre à KingdomSMP et ne se synchronise pas avec BetterTeams.
- Le pacte après absorption par King a un objectif simple configurable (éliminations + Crown Materials) et validation admin. Des quêtes plus élaborées avec étapes/objectifs variés ne sont pas encore implémentées.
- Aucun test automatisé ni test réel sur un serveur Paper n'a pu être exécuté ici. Vérifier les recettes, le GUI, les pouvoirs, les permissions, les régions WorldGuard et les transitions 10 → 3 → 0 sur une instance de test.

## Version 0.3.6
- Chaque couronne utilise maintenant son trim décoratif vanilla dédié.
- Le menu principal contient « Couronnes & descriptions ».
- Le menu des recettes affiche les ingrédients dans de vraies grilles 3×3, avec les objets obtenus.
- Le Shift + clic droit reste désactivé par défaut.


## Version 0.4.6
- Les couronnes ne sont pas lâchées à la mort.
- La couronne du profil est automatiquement rééquipée dans l’emplacement du casque après le respawn.
- L’équipement normal est déplacé dans l’inventaire si possible.

## Version 0.4.6
- Passifs et capacités mieux décrits en FR/EN/ES.
- Nature est désormais utile en PvP pour ses capacités de repoussement et de zone.
- Wealth a une capacité de chance plus équilibrée.
- Adaptation conserve correctement ses améliorations temporaires de Résistance.
- Build non vérifié : Maven indisponible dans l’environnement de création.


## Version 0.4.6
- Heaven : aucun dégât de chute et Hâte III.
- Tyrannus : Force I, Vitesse II, Résistance II et Résistance au feu I.
- Hériclés : Force II, Vitesse I, Résistance II et Résistance au feu I.
- Menu des recettes : les objets sont sélectionnables pour afficher leur recette dans un écran séparé.
- Nouvel objet Crown Changer : consommé au clic droit pour obtenir une autre couronne basique aléatoire. Les couronnes légendaires ne sont pas attribuées par cet objet.
- Recette du Crown Changer : 4 perles de l'Ender, 4 éclats d'améthyste et 1 étoile du Nether.
- Cette version est fournie en code source ; la compilation réelle reste à effectuer.


## Version 0.5.0 — améliorations de fiabilité
- Un joueur ne peut recevoir qu'une seule récompense par événement actif, même s'il continue à miner ou tuer après avoir fini l'objectif.
- Les événements miniers ne progressent pas si la casse du bloc a été annulée par un autre plugin.
- Les annonces d'événement sont désormais localisées individuellement en français, anglais et espagnol.
- Les rituels sont interrompus si la cible meurt, se déconnecte, change de monde ou s'éloigne trop.
- Changer/attribuer une couronne réinitialise les améliorations manuelles de l'ancienne couronne sans effacer l'historique d'absorption de King.
- Le profil compte uniquement les améliorations de pouvoirs, pas les entrées internes d'absorption.
- Le projet reste une source non compilée : exécuter `mvn clean package` pour vérifier la compilation avant usage.

## Version 0.5.0 — rituels légendaires difficiles
- Catalyseur dédié + offrandes rares obligatoires pour chaque couronne légendaire.
- 120 secondes de canalisation par défaut (minimum 60 secondes), sans dégâts ni déplacement de plus de 3 blocs.
- Mort, déconnexion, changement de monde/de couronne ou dégâts interrompent le rituel ; les offrandes sont perdues en cas d'échec.
- Coûts configurables dans `config.yml`, sous `rituels.offrandes`.

**Rituels en 0.5.0 :** un joueur lance son propre rituel avec `/kingdom ritual King`, `/kingdom ritual Tyrannus` ou `/kingdom ritual Hériclés`. Les admins peuvent aussi cibler un joueur avec `/kingdom ritual <joueur> <couronne>`. Le joueur ciblé doit posséder le catalyseur et toutes les offrandes.


## Version 0.5.0 — événements difficiles

Les événements sont moins fréquents et durent 5 minutes. La chasse demande 40 monstres hostiles. L’extraction demande 48 minerais précieux (fer, or, redstone, lapis, diamant, émeraude, quartz du Nether, or du Nether ou débris antiques) ; charbon et cuivre ne comptent pas. La récompense par défaut est de 6 matériaux de couronne.
