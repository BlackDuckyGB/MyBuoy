# scripts/

Outils utilitaires du projet MyBuoy.

## mkico.sh

Génère l'icône (et un splash optionnel) d'une app à partir d'une source **PNG**
ou **SVG** : produit un `.iconset` complet (toutes tailles), un `.icns` (macOS),
un `.ico` (Windows), l'export 1024 px et les copies de tailles / splash.

```bash
mkico.sh <source.svg|png> [prefixe-sortie]
```

- **Entrée SVG** : rasterisée proprement via `rsvg-convert` (ImageMagick rend mal
  le SVG en interne). Si `rsvg-convert` est absent, on retombe sur le rendu
  ImageMagick (qualité dégradée) avec un avertissement.
- **prefixe-sortie** (2e arg, optionnel) : préfixe des fichiers produits, p. ex.
  `mybuoy` → `mybuoy.png`, `mybuoy-192.png`, `mybuoy.iconset/`… Par défaut, le nom
  de l'entrée sans extension.

Toutes les sorties sont écrites **à côté du préfixe** ; pour MyBuoy elles tombent
dans `design/app-icon/` et sont ignorées par git (cf. `.gitignore`), sauf
`mybuoy.png` que l'on conserve.

### Splash paramétrable

Le bloc splash incruste deux lignes de texte, paramétrables par variables
d'environnement (défauts MyBuoy) :

| Variable     | Défaut          | Rôle                         |
|--------------|-----------------|------------------------------|
| `MKICO_TXT1` | `MyBuoy`        | Ligne principale             |
| `MKICO_TXT2` | `by BlackDucky`  | Ligne secondaire (crédit)    |
| `MKICO_FONT` | `fonts/BuoyRound.ttf` | Police (embarquée ci-dessous) |

```bash
MKICO_TXT1="MyBuoy" MKICO_TXT2="by BlackDucky" \
  ./mkico.sh ../design/app-icon/mybuoy.svg mybuoy
```

> Historique : ce script est un outil générique réutilisé sur plusieurs projets.
> Les splash d'origine importés portaient par erreur la marque d'un autre projet ;
> ils n'ont donc **pas** été versionnés et doivent être régénérés avec les valeurs
> ci-dessus.

**Dépendances** : `imagemagick` (`convert` / `magick`), `iconutil` (macOS, pour le
`.icns`), et `rsvg-convert` (librsvg) si l'entrée est un SVG.

## fonts/

- `BuoyRound.ttf` — police utilisée par le splash de `mkico.sh` (embarquée pour que
  la régénération soit autonome, sans dépendre de `~/Library/Fonts`). Police maison
  du projet, même licence (AGPL-3.0 + licence commerciale).
- `make_buoyround.py` — génère `BuoyRound.ttf` : chaque lettre est un squelette
  (traits et arcs) tracé au stylo rond puis penché, rien n'est repris d'une autre
  police. Nécessite `fontTools` : `python3 make_buoyround.py [sortie.ttf]`.
