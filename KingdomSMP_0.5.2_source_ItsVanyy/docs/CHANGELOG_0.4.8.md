# KingdomSMP 0.4.8

- Correction de l’exception `ArrayIndexOutOfBoundsException` du GUI des recettes : inventaire de détail porté à 36 slots et indexation défensive des lignes.
- Ajout des catalyseurs uniques de rituel : Sceau royal (King), Noyau du Tyran (Tyrannus), Cœur d’Hériclés (Hériclés), avec crafts dédiés.
- Un rituel légendaire ne démarre que si le joueur ciblé possède le catalyseur correspondant ; il est consommé à la fin du rituel.
- Lorsqu’un joueur portant Hériclés est tué en PvP, le tueur reçoit Hériclés et l’ancien porteur reçoit la couronne que possédait le tueur. Si le tueur n’avait pas de couronne, l’ancien porteur n’en reçoit pas.
- Événements plus difficiles : minimum de 12 monstres ou 24 minerais, 150 secondes, récompense minimale de 3 matériaux.
- Commande admin `/kingdom item king|tyrannus|hericles` pour obtenir les catalyseurs.
- Auteur : ItsVanyy. Cible : Paper 1.21.11, Java 21, Maven.

Compilation Maven et tests en jeu non exécutés dans cet environnement.
