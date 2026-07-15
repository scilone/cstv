# Référence design — Refonte UI/UX (maquette Claude Design)

Source de vérité pour les Phases 46-54 de [feuille-de-route-phases.md](../../feuille-de-route-phases.md).

Projet Claude Design : `https://claude.ai/design/p/f8a035f6-9af8-4bab-b41d-21412ee34d22`

## Contenu

- `mockup-source/Refonte-IPTV.dc.html` — export brut du document Claude Design.
  Contient TOUS les écrans (Profil, Home, TV, Films, Séries, Détail Film,
  Détail Série, Recherche, Grille "Voir tout", Paramètres, Nav bas, Category
  Sheet) dans un seul fichier, en HTML/CSS inline avec bindings `{{ mustache }}`.
  Les couleurs, `border-radius`, `padding`, `font-family` exacts sont dans ce
  fichier — grep dedans plutôt que deviner.
- `mockup-source/support.js` — runtime de rendu Claude Design (`dc-runtime`).
  Charge React/ReactDOM depuis unpkg au boot, parse le template `<x-dc>` et
  l'hydrate. Nécessaire uniquement pour un rendu live (voir "Prévisualiser"
  ci-dessous) ; pas nécessaire pour lire les valeurs CSS statiquement.
- `screenshots/` — captures d'écran pixel de chaque écran de la maquette
  rendue (référence visuelle humaine + IA multimodale).

## Comment lire les valeurs de design

Le fichier `.dc.html` a des sections marquées par des commentaires HTML :
`<!-- PROFILE -->`, `<!-- HOME -->`, `<!-- TV -->`, `<!-- FILMS -->`,
`<!-- SÉRIES -->`, `<!-- FILM DETAIL -->`, `<!-- SERIES DETAIL -->`,
`<!-- SEARCH -->`, `<!-- GRID VIEW (Voir tout) -->`, `<!-- SETTINGS -->`,
`<!-- BOTTOM NAV -->`, `<!-- CATEGORY SHEET -->`.

Chercher un écran : `grep -A 200 "<!-- HOME -->" Refonte-IPTV.dc.html`.

## Design tokens extraits (Phase 46, déjà validés)

**Polices** : `Bricolage Grotesque` (titres, 500/600/700), `Hanken Grotesk`
(corps, 400/500/600/700), `Material Symbols Rounded` (icônes maquette —
NON repris, l'app garde `androidx.compose.material.icons`).

**Couleurs** (hex) :
```
Fond global    : #060608 (base) + radial-gradient vers #1a1330 / #0b0b12
Surface 1      : #0F0F13  (déjà utilisé côté app)
Surface 2      : #16161D
Surface 3      : #1E1E24  (déjà utilisé côté app)
Accent         : #9C86FF (lavande, primary)
Accent hover   : #B3A3FF
Texte primaire : #F6F6FA
Texte secondaire: #9A9AA8
Accents alternatifs (Phase 54, optionnel) : #0070F3 (bleu), #2BB8A6 (sarcelle), #E5A13A (ambre)
```

**Radius** : 8, 10, 12, 13, 14, 16, 18, 22, 36, 46px, 999px (pills), 50% (cercles).

## Prévisualiser (rendu live, si besoin de revoir l'interactivité)

```bash
# Servir mockup-source/ en local (bind localhost obligatoire)
python3 -m http.server 8901 --bind 127.0.0.1 --directory docs/design-reference/mockup-source
# Renommer Refonte-IPTV.dc.html -> index.html au préalable, ou servir avec ce nom exact
```

Le runtime (`support.js`) charge React/ReactDOM via CDN (unpkg) — connexion
internet requise pour le rendu live. Pas requise pour juste lire le CSS.

## Écrans couverts par les screenshots

Voir `screenshots/` — un fichier par écran, nommé par section (`home.png`,
`tv.png`, `films.png`, `series.png`, `film-detail.png`, `series-detail.png`,
`search.png`, `grid-view.png`, `settings.png`, `profile.png`).
