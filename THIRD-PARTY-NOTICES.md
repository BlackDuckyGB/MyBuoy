# Third-Party Notices

MyBuoy is distributed under the GNU Affero General Public License v3.0
(see `LICENSE`). It also incorporates third-party material listed below.
Each item is compatible with both the AGPL distribution and the separate
commercial licensing of MyBuoy.

---

## Sourcing policy for third-party assets

MyBuoy is dual-licensed: it is published under the AGPL-3.0, and it is also
offered under a separate commercial license. That combination is stricter
than it looks, and it drives every decision recorded in this file.

Two obligations apply at once. Because the AGPL distribution ships the
sources, any icon in this repository can be extracted, modified and
redistributed by its recipients. And because a commercial license grants a
customer rights over the whole application, MyBuoy must be able to
**sublicense** every asset it contains. The second point is the decisive
one: most "free with attribution" icon libraries (Flaticon, Freepik,
Icons8, the free tier of The Noun Project) are explicitly *non-transferable
and non-sublicensable*, and forbid redistributing the icon as a reusable
file. They are perfectly usable in a closed-source app — and unusable here.

Copyleft-for-artwork is excluded for the mirror-image reason: a CC-BY-SA or
GPL asset would force the share-alike terms onto derivatives, which
collides head-on with the commercial track.

### Approved sources

| Purpose | Source | License |
|---|---|---|
| Seamarks / nautical symbols | [OpenNauticalChart/josm](https://github.com/OpenNauticalChart/josm) | Public Domain (per-file dedication) |
| Android UI icons | [Material Symbols](https://fonts.google.com/icons) | Apache-2.0 |
| General UI icons | [Lucide](https://lucide.dev), [Tabler](https://tabler.io/icons), [Phosphor](https://phosphoricons.com) | ISC / MIT |
| Illustrations | [Openclipart](https://openclipart.org), [unDraw](https://undraw.co) | CC0 / permissive |

CC-BY sources (such as Font Awesome Free's SVGs) are technically usable,
but they carry a perpetual attribution duty that must then be surfaced in
the application's About screen — not merely in this file. Prefer a
permissive source and avoid the obligation entirely.

### Checklist before adding any asset

Ask, in this order:

1. **Is the license sublicensable?** MIT, ISC, Apache-2.0, CC0 and Public
   Domain qualify. CC-BY qualifies but adds a visible-attribution duty.
   Anything share-alike, GPL, or "free with attribution" does not.
2. **Where is that license actually stated?** A root `LICENSE` file is
   ideal. Failing that, check the asset itself — a per-file dedication in
   the SVG metadata governs, and is what made the seamark set usable:
   `grep -l "cc:license\|dc:rights" *.svg`
3. **Does the format change anything?** No. Rasterising an SVG to PNG and
   compositing it into an atlas creates a derivative work; the obligations
   follow the pixels, not the file extension.
4. **Is it recorded?** Add the source, license, URL and date to this file
   at the time of import. Note that `design/marks/reference/` is
   `.gitignore`d, so provenance that is not written down here is lost on
   the next clone.

One AGPL consequence worth restating: for a composited raster atlas, the
"preferred form for making modifications" is the GIMP `.xcf`, not the
flattened PNG. The `.xcf` sources under `design/marks/src/` are therefore
kept in version control deliberately.

---

## Seamark symbols (IHO INT-1 / IALA)

The on-screen seamark icons shipped in MyBuoy are raster atlases
(`app/src/main/res/**/seamarks*.png`) that were composited in GIMP
(`design/marks/src/seamarks.xcf`, `seamarks2.xcf`) from source SVG symbols.

- **Source project:** OpenNauticalChart / JOSM presets and icons
  <https://github.com/OpenNauticalChart/josm> (styles authored by
  Aun "Skippern" Johnsen and contributors; upstream of the OpenSeaMap
  JOSM tooling <https://github.com/OpenSeaMap/josm>).
- **License of the source symbols:** Public Domain. Every source SVG used
  carries an embedded Creative Commons Public Domain Dedication in its RDF
  metadata:
  `<cc:license rdf:resource="http://creativecommons.org/licenses/publicdomain/"/>`.
  The corresponding MapCSS rendering styles are titled "PD"
  (e.g. *"INT 1 Rendering (IHO standardized symbols) PD"*).
- **Verification:** the specific source SVGs composited into the shipped
  atlases were each confirmed to carry the Public Domain dedication. The
  underlying symbology (IHO INT-1 / IALA buoyage) is an international
  standard, not a copyrightable original design.
- **Note:** the raw reference set lives under `design/marks/reference/`,
  which is excluded from version control (`.gitignore`). A small number of
  symbols in that reference set omit an explicit dedication; none of those
  are used in MyBuoy's atlases.

No attribution is required by the Public Domain dedication. This notice is
provided for provenance and transparency.

---

## Map data (OpenStreetMap)

Every map MyBuoy draws — coastlines, roads, buildings, seamarks — is built
from **OpenStreetMap** data.

- **Source:** <https://www.openstreetmap.org>
- **License:** Open Database License (ODbL) v1.0
  <https://opendatacommons.org/licenses/odbl/1-0/>
- **Required attribution:** *© OpenStreetMap contributors*. MyBuoy displays
  it permanently in the bottom corner of the map view, as the OSMF
  attribution guidelines require for a browsable map
  <https://osmfoundation.org/wiki/Licence/Attribution_Guidelines>.

Unlike the assets listed above, this obligation is **not** discharged by
this file: the credit has to remain visible in the running application.

### Derived database — `land_polygons`

The offline coastline database shipped to users (`land_polygons`, an
RTREE-indexed SQLite build) is a **Derivative Database** in the sense of
the ODbL. Two consequences follow, and they travel with every copy:

1. The database is itself published under the **ODbL**, not under the
   AGPL-3.0 or the commercial license of the application. The dual-license
   scheme covers MyBuoy's own code and artwork; it does not and cannot
   cover OpenStreetMap data.
2. Wherever that database is distributed — including the archives served
   from the `mybuoy-maps` repository — the ODbL notice and the
   *© OpenStreetMap contributors* credit must accompany it.

The commercial license of MyBuoy therefore grants rights over the
application, never over the map data a customer receives with it.

---

## Overpass API

MyBuoy's online mode queries the **Overpass API** to fetch map elements for
the area in view.

- **Software:** Overpass API by Roland Olbricht and contributors,
  <https://github.com/drolbr/Overpass-API> — AGPL-3.0.
- **Public instances:** operated by volunteers and funded by donations
  (`overpass-api.de` and the FOSSGIS e.V. instances). MyBuoy currently
  queries `overpass-api.de`.
- **Attribution:** not legally required merely to query the service. The
  credit shown on the Support screen is given because these instances cost
  their operators real money and MyBuoy would have no online mode without
  them.

**Fair use is the real obligation here.** The public instances are sized
for occasional use, not for backing a distributed application. MyBuoy
identifies itself on every request with a `User-Agent` carrying its name,
version and a contact address (`Chunk.java`), which is what the fair-use
policy asks for and what lets an operator write before blocking. Caching,
backoff on 429/504, and keeping the offline database as the default path
matter more than any notice in this file.

---

## SQLite (RTREE build)

The Android system SQLite is compiled without the RTREE module, which the
`land_polygons` spatial index requires. MyBuoy therefore vendors the
official SQLite Android bindings as a local AAR (`org.sqlite.database`,
`libsqliteX.so`).

- **Source:** the official SQLite project, <https://www.sqlite.org>
- **License:** **Public Domain.** SQLite carries an explicit dedication:
  *"The author disclaims copyright to this source code."*
  <https://www.sqlite.org/copyright.html>
- **Version:** 3.53.04, aligned to 16 KB memory pages (2026-08-10).
- **Note:** this is the *official* `org.sqlite.database` package, not the
  `io.requery` fork (Apache-2.0). The distinction matters: only the former
  is public domain and therefore free of any obligation under either the
  AGPL or the commercial license.

No attribution is required. This notice is provided for provenance.

---

## EOT20 tide model (downloaded data)

Tide prediction uses **EOT20**, a global empirical ocean tide model built
from the residual analysis of eleven satellite altimetry missions.

- **Source:** Hart-Davis, Piccioni, Dettmering, Schwatke, Passaro and Seitz
  (DGFI-TUM), published through SEANOE —
  <https://doi.org/10.17882/79489>
- **License:** **CC BY 4.0** — attribution required, commercial use allowed.
- **Not bundled:** the grid is never shipped inside the APK. It is
  downloaded on request alongside the offline maps.
- **Conversion:** the packing of the published NetCDF atlas into MyBuoy's
  format, and the full list of changes made to the data, live in the
  separate `mybuoy-tides` repository.

Attribution is given on the Support screen, which is where the user sees it.

---

## ERFA (astronomy routines)

Sun and Moon positions, sidereal time and the related coordinate
transformations are ported to Java from **ERFA**, the freely licensed
edition of the IAU **Standards of Fundamental Astronomy** (SOFA).

- **Source:** <https://github.com/liberfa/erfa>
- **License:** **BSD 3-Clause**, Copyright (C) NumFOCUS Foundation,
  derived with permission from the IAU SOFA library.
- **Conditions:** the copyright notice, the list of conditions and the
  disclaimer are retained in each ported file and reproduced here; the
  names of the SOFA Board, the IAU and the contributors are not used to
  endorse MyBuoy; the SOFA heritage is acknowledged by describing this as
  a library derived from SOFA, not as SOFA itself.
- **Why ERFA and not SOFA:** the SOFA licence forbids redistributing a
  modified version under the same name, which a Java port necessarily is.
  The SOFA Board authorised the BSD re-release precisely for that case.

BSD-3 imposes no reciprocity: this is compatible with both the AGPL
distribution and the commercial licensing of MyBuoy.

---

## Original assets

The following are original works created for MyBuoy (not third-party):

- Application icon — `design/app-icon/mybuoy.{svg,xcf,png}`
- Personal / user marks — `design/marks/src/marks.xcf`, and the atlas
  layers `1_magenta.svg`, `1_no.svg`
- Font — `scripts/fonts/BuoyRound.ttf`, generated from stroke skeletons by
  `scripts/fonts/make_buoyround.py` (not derived from any existing font)
- Sounds — all files in `app/src/main/res/raw/`:
  - alarms, jingles and sound effects are synthesized from scratch by the
    author's own scripts (no samples, no sound bank);
  - `alm_melody_beethoven5`, `alm_melody_boudin` and `alm_melody_charge`
    are new renditions, synthesized the same way, of melodies in the public
    domain (Beethoven's Fifth Symphony, the French Foreign Legion march
    *Le Boudin*, the traditional military *Charge* call);
  - voices (`eg_*`) are original recordings made for the project, used with
    the performers' permission.

These are covered by the project's AGPL-3.0 license and the commercial
license, at the copyright holder's discretion.
