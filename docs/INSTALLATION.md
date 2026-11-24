# Installation, compilation et test

1. Installer Java Development Kit 21 et Maven.
2. Extraire l'archive puis ouvrir le dossier `KingdomSMP` dans IntelliJ IDEA.
3. Importer le `pom.xml` comme projet Maven.
4. Dans un terminal à la racine du dossier, exécuter `mvn clean package`.
5. Vérifier la présence de `target/KingdomSMP-0.3.3.jar`.
6. Sur une instance de test Paper 1.21.11, copier le JAR dans `plugins/`, puis démarrer.
7. Vérifier `plugins/KingdomSMP/config.yml`; régler le monde et les coordonnées du spawn.
8. Installer WorldGuard si tu veux que les régions avec `PVP: DENY` bloquent aussi les pouvoirs.
9. Tester le plugin sur une copie du serveur et conserver une sauvegarde avant chaque mise à jour.

## Langues
Chaque joueur choisit sa langue indépendamment avec `/kingdom language fr`, `/kingdom language en` ou `/kingdom language es`. Le choix est sauvegardé dans `plugins/KingdomSMP/players.yml` et ne change ni la langue Minecraft ni celle des autres joueurs.

## GUI et commandes
`/kingdom menu` ouvre le menu principal. `/kingdom powers` ouvre le menu des pouvoirs. En tenant une couronne, Maj + clic droit ouvre également le menu si `settings.activation.shift-clic-droit-menu` est activé. Le clic droit direct pour lancer un pouvoir est désactivé par défaut; il peut être activé dans `settings.activation.clic-droit-pouvoir`, avec le numéro configuré par `settings.activation.pouvoir-clic-droit`.

Les cooldowns globaux se règlent dans `settings.cooldown-pouvoir-secondes`. Les exceptions par couronne/pouvoir peuvent être définies dans `cooldowns.<NomCouronne>.<numéro>`.

## Materials Left
- Une couronne commence à 10 Materials Left, maximum 10.
- À 3 ou moins, tant que la valeur est supérieure à 0, elle est fracturée : les pouvoirs actifs sont désactivés, mais le passif reste actif.
- À 0, la couronne est cassée et ses passifs/pouvoirs sont désactivés jusqu'à utilisation du Crown Repairer avec suffisamment de Crown Materials.
- Crown Upgrader conserve les capacités débloquées dans les données du joueur.
- Le temps de perte de vie, la perte à la mort, les récompenses de kill et les recettes sont dans le code/configuration selon les paramètres disponibles.

## Pacte après absorption par King
Un joueur dont la couronne a été absorbée ne peut pas utiliser directement le Crown Repairer. Un administrateur lance `/kingdom pact start <joueur>`. Par défaut, le joueur doit obtenir 10 éliminations éligibles et avoir 5 Crown Materials dans son inventaire. L’administrateur valide avec `/kingdom pact approve <joueur>`; les 5 matériaux d’objectif sont consommés. Le joueur doit ensuite utiliser un Crown Repairer avec les 3 matériaux de réparation par défaut. Les objectifs se règlent dans `config.yml` sous `pacte`. Pour annuler : `/kingdom pact cancel <joueur>`. Le GUI d’administration propose également les joueurs dont la couronne a été absorbée.

## Commandes admin
`/kingdom crown <joueur> <couronne>`, `/kingdom remove <joueur>`, `/kingdom ritual <joueur> <couronne légendaire>`, `/kingdom item <material|upgrader|repairer> [quantité]`, `/kingdom save`, `/kingdom reload`, `/kingdom status`.

## Checklist de test
- [ ] Premier join : attribution d'une couronne normale.
- [ ] Vérifier les passifs Wrath (Force I) et Hériclés (Force II).
- [ ] Vérifier les pouvoirs à 3/5/8 et 3/5/8/12/15 éliminations.
- [ ] Vérifier que le GUI affiche aussi les pouvoirs débloqués avec Crown Upgrader.
- [ ] Tester Materials Left 10 → 3 (passif actif, capacités désactivées) → 0 (tout désactivé).
- [ ] Réparer une couronne à 0 avec Crown Repairer et les matériaux requis.
- [ ] Vérifier que les kills répétés sur le même joueur ne donnent pas de progression ni de matériaux pendant la période anti-farm.
- [ ] Vérifier `/trust`, le blocage au spawn et WorldGuard.
- [ ] Vérifier la persistance après mort, déconnexion et redémarrage.
- [ ] Vérifier les trois langues et les recettes des trois objets.

## Limites connues
Le code n'a pas été compilé dans l'environnement de création. Vault et BetterTeams ne sont pas encore intégrés à leurs API; la confiance est gérée par KingdomSMP. Le resource pack est un pack de développement et l'apparence de la couronne portée doit être testée. Le pacte de restauration de base existe; un système de quêtes multi-étapes reste à définir.
