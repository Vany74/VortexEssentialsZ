# Résumé KingdomSMP — version 0.3.3

## Cible
Paper 1.21.11, Java 21, Maven. Auteur : ItsVanyy.

## Couronnes
- Normales : Nature, Wrath, Heaven, Gl1tched, Adaptation, Wealth, Fractionate, Void, Parade.
- Légendaires : King, Tyrannus, Hériclés.
- Une couronne par joueur; attribution normale aléatoire; doublons autorisés.
- Wrath : Force I. Hériclés : Force II, Vitesse II et Résistance au feu I.
- Progression active : 3/5/8 éliminations pour normales, 3/5/8/12/15 pour légendaires.

## Materials Left
- Départ/max : 10.
- À 3, 2 ou 1 : couronne fracturée; seuls les pouvoirs actifs sont désactivés, le passif reste actif.
- À 0 : couronne cassée; passif et pouvoirs sont désactivés jusqu'à réparation.
- Crown Material : craftable, obtenu sur les éliminations éligibles et utilisé pour restaurer des vies.
- Crown Upgrader : craftable, déverrouille une capacité et conserve le déblocage.
- Crown Repairer : craftable, consomme des matériaux et restaure la couronne cassée.

## GUI et commandes
Menus principaux, pouvoirs, langues, confiance et administration. Le GUI admin permet de sélectionner une couronne puis un joueur en ligne. Commandes : `/kingdom menu`, `me`, `powers`, `cast`, `language`, `trust`, `crown`, `ritual`, `remove`, `item`, `save`, `reload`, `status`.

## Pacte de restauration
- Après absorption par King, le joueur ne peut pas réparer sa couronne avant qu’un administrateur démarre et approuve un pacte.
- Objectifs par défaut configurables : 10 éliminations éligibles et 5 Crown Materials conservés dans l’inventaire. Les matériaux d’objectif sont consommés lors de l’approbation.
- Après approbation, le joueur doit encore utiliser un Crown Repairer avec les matériaux nécessaires à la réparation.
- Commandes : `/kingdom pact status`, `/kingdom pact start <joueur>`, `/kingdom pact approve <joueur>`, `/kingdom pact cancel <joueur>`. Le menu admin contient aussi la liste des joueurs en attente.

## État technique
Le code source et les ressources ont été mis à jour en 0.3.3. La compilation et les tests en jeu restent à faire. Aucun JAR n'est inclus, car Maven/dépendances ne sont pas disponibles dans cet environnement. Vault/BetterTeams sont seulement détectés, pas encore intégrés via leurs API. Le pack fournit des icônes de développement; l'apparence de l'armure portée doit être testée/complétée. Le pacte de restauration de base est implémenté; les quêtes multi-étapes restent à définir.
