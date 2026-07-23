# glyphlapse — Spécifications techniques

Glyph Toy « compteur temporel » : affiche le temps écoulé **depuis** une date
de référence, ou restant **jusqu'à** elle. La direction est déduite
automatiquement : référence passée → depuis, future → jusqu'à.

## Constantes

| Nom | Valeur | Description |
|-----|-------:|-------------|
| `SIZE` | 25 | Matrice 25×25 |
| `RADIUS` | 12,5 | Masque circulaire centré (12,12) — ~489 LEDs |
| `FONTS` | 5×7 / 3×5 / 3×4 | Police selon le nombre de lignes (≤2 / 3-4 / 5) |
| `MAX_LINES` | 5 | Lignes max au centre (5×4 + 4 interlignes = 24 px) |
| `RING_BAND` | 11,3–12,5 | Cellules de l'anneau des secondes (bord du disque) |
| `TICK` | 1 Hz | Rendu au repos, aligné sur la seconde |
| FPS anim | 30 | Pendant transitions et animation d'arrivée |
| `CYCLE_PERIOD` | 2 s | Durée par unité en format Cycle |
| `SLIDE` | 0,3 s | Transition de slide (cycle & changement de format) |
| `ARRIVAL` | ~4 s | Animation d'arrivée (compte à rebours atteint) |

## Décomposition calendaire

- Calcul via `java.time` (`ZonedDateTime`, `Period` + `Duration`), fuseau local :
  années/mois exacts (bissextiles, fins de mois), puis jours/heures/minutes/secondes.
- Unités : `A` (années), `M` (mois), `J` (jours), `H` (heures), `′` (minutes,
  notation prime — étroit, passe dans les bandes basses du disque), secondes
  portées par **l'anneau périphérique** (pas de ligne dédiée).
- **Pertinence** : les unités de tête à zéro sont masquées (diff de 5 jours →
  pas de « 0A 0M », on commence à `J`). En dessous de la première unité non
  nulle, **granularité complète** : jusqu'à 5 lignes (`A M J H ′`).
- **Centrage disc-aware** : chaque ligne est centrée puis nudgée de ±1-3 px
  si des pixels sortiraient du disque (bandes haut/bas plus étroites).
- Diff < 1 min : la valeur des secondes s'affiche au centre (5×7), l'anneau
  continue en parallèle.

## Formats d'affichage

Sélectionnés dans l'app, cyclés par appui long sur le Glyph Button :

1. **Détail** *(défaut)* — granularité complète : une ligne par unité
   pertinente (valeur + lettre), jusqu'à 5 lignes. Police 5×7 si ≤ 2 lignes,
   3×5 si 3-4, **3×4** si 5 lignes (années → minutes toutes visibles).
   *Variante à l'essai* **Détail 2** : même granularité mais 2 unités par
   ligne quand nécessaire — `[A]` / `[M J]` / `[H ′]` (calendrier au centre,
   horloge en bas, séparateur 2 px) — police 3×5 partout, jamais de 3×4.
   Une des deux variantes sera retenue après essai sur la préview.
2. **Compact** — les 2 unités les plus significatives, police 5×7.
3. **Cycle** — une unité à la fois plein écran (valeur 5×7, lettre en dessous),
   slide horizontal toutes les `CYCLE_PERIOD`.
4. **Jours** — total de jours uniquement : `J-n` en compte à rebours
   (classique « J-42 »), `nJ` en écoulé. 5×7 si ≤ 4 caractères, sinon 3×5.

Tous les formats conservent l'anneau des secondes.

## Anneau des secondes

- Cellules du bord (distance ∈ `RING_BAND`) triées par angle depuis 12 h.
- Arc allumé proportionnel à `s/60` : tête à 100 %, traînée dégradée (~35 %).
- Sens **horaire** en mode depuis (le temps s'accumule), **anti-horaire**
  en mode jusqu'à (le temps s'épuise).
- Masqué en AOD (rendu statique, cf. Interaction).

## Machine à états

```
DISPLAY ──appui long──▶ FORMAT_SLIDE (0,3 s) ──▶ DISPLAY
DISPLAY ──diff atteint 0 (jusqu'à)──▶ ARRIVAL (~4 s) ──▶ DISPLAY (bascule depuis)
```

## Animation d'arrivée (jusqu'à → 0)

1. *0–0,5 s* : double flash plein disque (décroissant).
2. *0,3–2,2 s* : 3 ondes concentriques (r = 14·t, largeur 1 px).
3. *0–4 s* : anneau complet pulsé (sin 3 Hz).
4. Fade-out, puis affichage « depuis » (compteur repart de 0).
- Haptique : pattern long à l'arrivée, tick au changement de format.

## Architecture

```
engine/  TimeBreakdown.kt, LapseEngine.kt — logique pure (java.time), testable JUnit
render/  Fonts.kt, LapseRenderer.kt, Ring.kt, Arrival.kt — IntArray(625)/frame
toy/     LapseToyService.kt, GlyphMatrixService.kt — seul module dépendant du SDK
ui/      MainActivity.kt — configuration Compose + préview live 25×25
```

Le temps est injecté (`nowEpochMillis` + zone) : décomposition et machine à
états testables en JVM pure.

## Configuration (app, avec raccourci launcher)

Contrairement à GlyphSlot, l'app a une **icône launcher en release** : c'est
l'interface de configuration.

- **Sélecteur de date + heure de référence** (Material 3 DatePicker/TimePicker).
- **Sélecteur de format** (Détail / Compact / Cycle / Jours).
- Libellé auto : « Depuis le … » / « Jusqu'au … » selon la date choisie.
- **Préview live 25×25** : même moteur et même renderer que le toy.
- Persistance : `SharedPreferences` (`ref_epoch_millis`, `format`) ;
  le service écoute `OnSharedPreferenceChangeListener` → mise à jour immédiate
  sans re-bind. Défaut : 1er janvier de l'année courante, 00:00 (mode depuis).

## Interaction Glyph Button

| Event | Action |
|-------|--------|
| Short press | Système : cycle entre les toys |
| Long press (« change ») | Format suivant (slide + tick haptique) |
| `EVENT_AOD` | Rendu statique sans anneau, 1 update/min |
| `onUnbind` | Stop boucle + extinction matrice |

## Boucle de rendu

- Repos : 1 tick/s **aligné sur la seconde** (`postDelayed` jusqu'à la
  prochaine frontière) — l'anneau avance pile au tic.
- 30 fps uniquement pendant `FORMAT_SLIDE`, le slide du format Cycle et
  `ARRIVAL`, retour à 1 Hz ensuite (batterie).

## Icône

`glyphlapse-icon.svg` : sablier pixel-art sur la matrice circulaire (grains
en cours de chute, halo sur LEDs allumées), fond adaptive icon sombre.
