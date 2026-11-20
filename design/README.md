# design/ — sources graphiques de MyBuoy

Ce dossier regroupe les **sources** des visuels de l'app (fichiers GIMP `.xcf`,
SVG, exports PNG). Ce ne sont **pas** des entrées du build Gradle : les assets
réellement consommés par l'app vivent dans `app/src/main/res/`. On garde ici de
quoi **régénérer** ces assets sans repartir de zéro.

Règle de versionnement : on garde les **sources** (`.xcf`, `.svg`) et les
**exports utiles** (`.png`), on ignore tout ce qui est **régénérable en masse**
par un script (voir `.gitignore` à la racine).

## app-icon/ — icône du lanceur et déclinaisons

- `mybuoy.xcf` — master GIMP de l'icône.
- `mybuoy-foreground.svg` / `mybuoy-background.svg` — les deux calques de l'icône
  adaptative Android (source du launcher, régénéré côté Android Studio vers
  `app/src/main/res/mipmap-*/ic_launcher*.png`).
- `mybuoy.svg` — icône complète (fusion background + foreground), entrée de `mkico.sh`.
- `mybuoy.png` — export 1024 px de l'icône (dépannage sans GIMP).

Non versionnés (régénérés par `scripts/mkico.sh`, cf. `.gitignore`) :
`mybuoy-*.png` (toutes tailles + splash), `mybuoy.iconset/`, `mybuoy.icns`,
`mybuoy.ico`. Les `.icns` / `.ico` sont des livrables desktop ; le launcher
Android, lui, se construit à partir des SVG foreground/background.

### Régénérer icône + splash

```bash
scripts/mkico.sh design/app-icon/mybuoy.svg mybuoy
```

Le 2e argument fixe le préfixe de sortie (`mybuoy`). Textes du splash
paramétrables (défauts : « MyBuoy » / « by BlackDucky ») :

```bash
MKICO_TXT1="MyBuoy" MKICO_TXT2="by BlackDucky" \
  scripts/mkico.sh design/app-icon/mybuoy.svg mybuoy
```

## marks/ — repères dessinés sur la carte

- `src/` — masters GIMP (`.xcf`) : `seamarks`, `marks`, `modele`,
  `pm_00x`…
- `export/` — PNG exportés à la main depuis GIMP. Ce sont ces PNG qui sont
  ensuite **recopiés dans `app/src/main/res/drawable*`** (p. ex.
  `seamarks_96x96.png`, `personalmarks_72x72.png`, `marks_96x96.png`).
- `reference/josm/` — jeu d'icônes **tiers** JOSM + fichiers `.mapcss`, gardé
  comme référence. **Non versionné** (rechargeable), présent localement.

Workflow d'un mark : éditer le `.xcf` → exporter le PNG à la bonne taille dans
`export/` → le copier dans le `drawable*` correspondant de l'app.

## db-model/

- `MyBuoy.db` — gabarit / modèle de base de données de référence.
