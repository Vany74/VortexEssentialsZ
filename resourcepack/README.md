# Resource pack VortexSMP

`VortexEssentials-Pack.zip` habille **tous les items custom** (34 modèles) en **pixel art net 32×32** (ombrage par paliers, biseau lumineux, contour d'un pixel,
aura de rareté, étincelles) et décors (éclairs de Lightspeed et Stormpiercer, horloge de la Chrono Sword,
âmes de la Reaper Scythe, vagues de Sealord...). Aperçus : `preview.png` (icônes) et `preview_armor.png` (armures portées).
Format de pack : 75 (Minecraft 1.21.11).

Contenu : 11 outils, armes légendaires et mythiques, Umbral Scythe, Stormpiercer, potions (Panacea, Agony, **All in One**,
**Holy Master**), Crystallization Shard, et :
- **l'arc Skyfall** : l'image change pendant que la corde se tend ;
- **les arbalètes** (Blazing Crossbow, Voidrender) : états de tension, puis flèche ou feu d'artifice chargés ;
- **les 4 armures mythiques portées** (Radiant Crown, Colossus Plate, Ember Greaves, Phantom Treads) : texture dédiée sur le joueur
  (équipement `humanoid` / `humanoid_leggings`), en plus de l'icône.

## Installation
1. Hébergez le zip sur un lien de téléchargement **direct** (le fichier doit se télécharger sans page intermédiaire).
2. Dans `server.properties` :
```
resource-pack=<lien direct vers le zip>
resource-pack-sha1=8dc06c0a7061c0f640c4e296c13a0cd919880740
require-resource-pack=true
```
   (le SHA-1 correspond au zip fourni ; recalculez-le si vous régénérez le pack)
3. Dans `plugins/VortexEssentials/config.yml` : `use-custom-models: true`, puis `/vortex reload`.
   Si une arbalète s'affichait mal, mettez `crossbow-models: false` : elles reprennent l'apparence vanilla.
4. **Re-donnez les items** : le modèle (et la texture d'armure) est inscrit dans l'item à sa création.

## À savoir
- Sans le pack chez le joueur, les items activés affichent une texture manquante : n'activez `use-custom-models`
  qu'avec `require-resource-pack=true`.
- Les clients plus anciens que la 1.21.4 (via ViaBackwards) ne connaissent pas ces modèles.
- Les textures sont générées par code : pour les retoucher, éditez `resourcepack-source/generate_pack.py`
  puis `python3 resourcepack-source/generate_pack.py` (Pillow et numpy requis), ou retouchez les PNG du zip.
