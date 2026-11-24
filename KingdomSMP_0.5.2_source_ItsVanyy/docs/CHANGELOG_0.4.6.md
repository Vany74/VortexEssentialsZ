# KingdomSMP 0.4.6

## Nouveautés
- Profil avancé avec couronne/état, éliminations, morts, ratio K/D et nombre de pouvoirs débloqués.
- Statistique des morts sauvegardée dans `players.yml` (migration automatique avec valeur par défaut 0).
- Événement aléatoire « Résonance des couronnes » : tuer 3 monstres en 3 minutes avec une couronne valide récompense 2 matériaux.
- Crown Changer réinitialise les déblocages liés à l’ancienne couronne tout en conservant les statistiques générales.
- Protection contre deux rituels simultanés identiques et nettoyage de l’état du rituel si la cible se déconnecte.
- Version et documentation mises à jour.

## Vérification
- Archive source et structure ZIP vérifiées.
- Compilation Maven non exécutée dans cet environnement : compatibilité de compilation non confirmée.


## Correctifs de finalisation
- Ajout d’un second événement aléatoire : extraction de minerais, en plus de la chasse aux monstres.
- Progression, durée, chance de déclenchement et récompenses configurables.
- Messages de défi traduits selon la langue du joueur et récompense sonore à la réussite.
- Import de `EntityDeathEvent` explicite pour le gestionnaire d’événement.
- Le statut admin affiche désormais la version 0.4.6.
- Crown Changer : ajout d’un GUI de confirmation/annulation avant consommation.
