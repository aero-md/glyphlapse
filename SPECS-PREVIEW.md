# glyphlapse — Spécifications de la préview web

La préview reproduit le toy dans un navigateur, **posé sur une photo du dos
d'un Nothing Phone (3)** : la Glyph Matrix est rendue à sa position et à son
échelle réelles, et le bouton d'interaction est calé sur le Glyph Button
physique. Polices, décomposition calendaire et anneau des secondes sont ceux
du toy Kotlin — voir [SPECS.md](SPECS.md).

## Fichiers

| Fichier | Rôle |
|---|---|
| [`glyph-lapse-preview.html`](glyph-lapse-preview.html) | Source unique de vérité — page autonome, HTML + CSS + JS inline |
| [`docs/index.html`](docs/index.html) | Page GitHub Pages : charge le HTML depuis `main` et le réécrit via `document.write` |
| [`docs/phone3-back.webp`](docs/phone3-back.webp) | Photo du dos, détourée et rognée (704 × 913, ~52 Ko) |

Publication : GitHub Pages en mode *legacy*, source `main` + `/docs` → **le
push suffit**, aucun workflow à déclencher.

## Photo du dos

Source 2048 × 2048 avec canal alpha, détourée au corps du téléphone
(845 × 1768 px), puis rognée aux **62 % supérieurs** — les 40 % du bas ne sont
jamais rendus. Réduite à 704 px de large, encodée en WebP q0.78. Asset partagé
avec le repo glyphslot.

Toutes les positions sont exprimées en **pourcentage du cadre photo**, jamais
en pixels : c'est ce qui garde le calage quand la préview est redimensionnée.

| Élément | Position | Diamètre |
|---|---|---:|
| Disque de la Glyph Matrix (`.disc`) | 79,53 % / 15,36 % | 26,04 % |
| Glyph Button (`.glyphbtn`) | 84,53 % / 74,82 % | 15,86 % |
| Rappel « appui long » (`.glyphhint`) | 74,6 % / 74,82 % | — |

Le cadre `.phone` porte `aspect-ratio: 704/913`. Pour changer de photo : refaire
le relevé des centres par superposition de cercles à l'échelle native, puis
mettre à jour ces valeurs, rien d'autre ne bouge.

La coupe basse se fait par `mask-image` sur l'`<img>` (fondu 86 % → 99 %) et
non par un aplat superposé, sinon la trame de fond disparaîtrait sur la bande.

## Grille LED : pixels entiers

Un canvas de taille fixe redimensionné par le navigateur avec un ratio
fractionnaire produit une trame irrégulière — une colonne sur n gagne un pixel
de gap. La grille est donc calculée depuis le `devicePixelRatio` :

```js
const CELL = Math.max(3, Math.round(6 * DPR));      // px de canvas par LED
const LED  = Math.max(2, Math.round(CELL * 2 / 3)); // côté du carré allumé
const PAD  = (CELL - LED) / 2;                      // entier, donc net
cvs.width = cvs.height = SIZE * CELL;               // 150 @1x, 300 @2x
```

Le disque fait 26,04 % de 576 px, soit les **150 px CSS** visés. Mesuré :
`backing 150`, `cssW 149,98` → **ratio 1,0001**, pas de rééchantillonnage.
Exact pour dpr 1 / 1,5 / 2 / 3 ; approché pour les dpr en quarts. Sous 576 px
de large, la mise en page redevient proportionnelle et l'irrégularité peut
réapparaître.

Le téléphone mesure **576 × 747 px**, la matrice **150 px** de diamètre — soit
l'échelle réelle du composant sur un écran classique. `main` et `footer` sont
passés à `max-width: 1040px` pour que le rack de contrôles tienne à côté.

## Interaction

Le bouton est un `<button>` transparent superposé au Glyph Button de la photo,
avec un glow jaune Nothing (variable CSS `--yellow: 255,229,0`) :

| État | Rendu |
|---|---|
| repos | pulse `glyphPulse` 2,2 s |
| survol | même pulse à 1,1 s + halo radial |
| pressé (`.is-pressed`) | anneau plein, halo large, ombre interne |
| rappel actif (`.is-hint`) | pulse à 0,7 s |

Un appui de **450 ms** passe au format suivant. Relâché avant, ou pointeur
sorti du bouton en cours d'appui, le rappel `APPUI LONG →` apparaît à gauche du
bouton pendant 1,9 s. Les drapeaux `down` / `fired` distinguent les deux cas.

Au clavier (`Enter` / `Espace`) le format change directement : il n'y a pas de
notion d'appui long, et le bouton doit rester utilisable.

## Fond

Gris très sombre légèrement bleuté + trame de points, façon DA Nothing.

| Élément | Valeur |
|---|---|
| `--bg` | `#0C0D10` |
| point | carré 2 × 2 px, `#9AA3C8` à 22 % |
| pas | 132 px |

Les points sont un SVG en data-URI, pas un `radial-gradient` : à ce rayon un
gradient serait antialiasé et donnerait des ronds flous.
`background-attachment: fixed` — la trame ne défile pas avec le contenu.

## Contrainte de publication

`docs/index.html` charge la page depuis `raw.githubusercontent.com` : les
modifications ne sont visibles sur Pages qu'une fois **poussées sur `main`**.
Les chemins relatifs (`phone3-back.webp`) se résolvent contre l'URL de la page
Pages, pas contre celle du HTML source — l'asset doit donc vivre dans `docs/`.
