# KingdomSMP 0.4.9 — Rituels légendaires difficiles

- Catalyseur dédié ET offrandes rares propres à chaque couronne légendaire.
- Les offrandes sont consommées au démarrage ; le catalyseur n'est consommé qu'à la réussite.
- Durée par défaut de 120 secondes, avec un minimum de 60 secondes.
- Dégâts reçus, mort, déconnexion, changement de couronne/de monde ou déplacement au-delà de 3 blocs interrompent le rituel.
- Les offrandes sont perdues en cas d'échec : le rituel est un engagement risqué.
- Barre d'action, particules et sons pendant le rituel.
- Coûts modifiables dans `config.yml` sous `rituels.offrandes`.

Coûts par défaut :
- King : 4 Totems of Undying, 16 Echo Shards, 8 blocs d'or, 1 Dragon Head.
- Tyrannus : 1 bloc de Netherite, 8 Wither Skeleton Skulls, 32 Blaze Rods, 16 Ancient Debris.
- Hériclés : 1 Heavy Core, 4 Enchanted Golden Apples, 4 Hearts of the Sea, 2 Totems of Undying.

Compilation à vérifier avec `mvn clean package` sur Java 21.

- Les joueurs peuvent lancer leur propre rituel via `/kingdom ritual <couronne>` ; les admins gardent la commande de ciblage d’un joueur.
