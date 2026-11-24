# Construire KingdomSMP 0.5.0 sous Windows

1. Installe Java 21 et Apache Maven 3.9 ou plus récent.
2. Décompresse le ZIP source.
3. Double-clique sur `build.bat` (ou lance `mvn -U clean package` dans ce dossier).
4. Si la compilation réussit, le fichier sera `target/KingdomSMP-0.5.0.jar`.
5. Copie le JAR dans `plugins/` de ton serveur Paper 1.21.11, puis redémarre le serveur.

Le JAR doit être compilé avec Maven pour utiliser les vraies dépendances Paper et WorldGuard. Ne renomme pas le ZIP source en `.jar` : cela ne créerait pas un plugin exécutable.
