# Pub Discord — textes prêts à copier

Remplacez `{SERVER_NAME}` et `{DISCORD_URL}` (ex. https://discord.gg/xxxx). Le lien est aussi à mettre
dans `tools/generate_wiki.py` (`DISCORD_URL`) pour activer le bouton du wiki.

## 1. Annonce courte (chat en jeu, broadcast automatique, bio)
```
✦ Rejoins le Discord de {SERVER_NAME} ! Annonces, aide, événements et idées d'items : {DISCORD_URL}
```

## 2. Annonce complète (salon #annonces, listes de serveurs, réseaux)
```
🎮 **{SERVER_NAME} — rejoins la communauté !**

⚔️ 24 items custom : outils, armes légendaires et 3 armes mythiques uniques sur tout le serveur
📖 Un wiki complet avec toutes les recettes
🛠️ Aide et support : recettes, items, grades, problèmes en jeu
🗳️ Propose et vote les prochains items
📣 Annonces et événements en avant-première

👉 {DISCORD_URL}
```

## 3. MOTD de la liste des serveurs (2 lignes)
```
{SERVER_NAME} — 24 items custom & armes mythiques
Discord : {DISCORD_URL}
```

## 4. Message de bienvenue (à la connexion, via votre plugin de messages)
```
Bienvenue sur {SERVER_NAME} ! Tape /vortex pour découvrir les items et leurs recettes.
Besoin d'aide ? Le Discord t'attend : {DISCORD_URL}
```

## Conseils
- Ne promettez que ce qui existe : les textes ci-dessus ne parlent que des fonctions réellement en place.
- Évitez « gratuit » / « sans pay-to-win » si vous vendez des grades avec boosts (voir `BOUTIQUE_TEBEX.md`).
