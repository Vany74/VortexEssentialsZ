# KingdomSMP 0.4.7 — ItsVanyy

## Fiabilité et équilibrage
- Limite à une récompense par joueur et par événement actif pour empêcher le farm infini après validation de l'objectif.
- Les blocs dont la casse est annulée par un autre plugin ne comptent plus dans l'événement minier.
- Messages de lancement des événements localisés en FR/EN/ES.
- Un rituel s'interrompt à la mort ou à la déconnexion de sa cible, ainsi qu'en cas de déplacement/changement de monde ou de couronne.
- Les améliorations manuelles sont réinitialisées lors d'un changement/attribution de couronne; les marqueurs d'absorption de King sont conservés.
- Le GUI de profil affiche le nombre d'améliorations manuelles, et non la taille complète de l'ensemble interne.

## Vérification
- Contrôle structurel et intégrité de l'archive effectués.
- Compilation Maven et tests en serveur Paper non exécutés dans cet environnement; ne pas considérer le plugin comme validé avant `mvn clean package` et des essais sur un serveur de test.
