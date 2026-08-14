# B29 - TV : le pad ne navigue pas dans les options du lecteur, touches média inertes

## Informations générales

Status:
RESOLVED (hotfix)

Created:
2026-08-14

---

# 1. Description

Sur Android TV, dans les trois lecteurs (Live, Film, Série), les flèches du pad
ne permettent jamais d'atteindre les options affichées par l'overlay : elles
déclenchent directement une action de lecture. En pause, l'utilisateur voit donc
des boutons qu'il ne peut pas atteindre. Les touches média de la télécommande
(play, pause, avance, retour, suivant, précédent, stop) ne font rien du tout.

# 2. Cause

Le `Box` racine de chaque lecteur capture les touches en phase *bubble*
(`onKeyEvent`) et **consomme** les flèches :

- `VodPlayerScreen.kt` / `SeriesPlayerScreen.kt` : gauche/droite = ±10 s,
  centre = lecture/pause ;
- `PlayerScreen.kt` (live) : haut/bas = zapping.

Comme ce `Box` est `focusable()` et détient le focus, l'événement ne redescend
jamais vers les boutons de l'overlay et le système de focus de Compose n'a
jamais l'occasion de déplacer la sélection. Aucun `KEYCODE_MEDIA_*` n'était par
ailleurs traité.

# 3. Décisions produit

| Sujet | Décision |
|---|---|
| Portée de la règle | Dès que l'overlay de contrôles est visible (pas seulement en pause), les flèches ne servent qu'à la navigation entre options. Règle identique Live / Film / Série. |
| Overlay masqué | Toute flèche se contente d'afficher l'overlay : plus de seek ni de zapping direct au pad. Règle appliquée strictement, y compris sur le live — le zapping passe désormais par les touches média suivant/précédent et par le tiroir « Chaînes ». |
| Touches média câblées | Play, Pause, Play/Pause, avance et retour rapides, suivant, précédent, stop. |
| Focus initial à l'ouverture de l'overlay | Bouton central Play/Pause sur Film et Série ; premier bouton de la barre (Retour) sur le live, qui n'a pas de transport. |

# 4. Correction

- Nouveau `presentation/player/core/PlayerRemoteKeys.kt` : traduction pure des
  touches en `PlayerKeyIntent`, partagée par les trois lecteurs.
  `resolveMediaKeyIntent()` vaut toujours ; `resolveTvDpadIntent()` rend `null`
  (« ne pas consommer ») dès que les contrôles sont visibles.
- Les trois lecteurs arbitrent désormais en phase *preview* (`onPreviewKeyEvent`)
  pour passer avant le bouton focalisé, et laissent les flèches au système de
  focus quand l'overlay est ouvert. Le comportement mobile historique est
  conservé tel quel (branche `!isTv`).
- Focus TV : la surface vidéo reprend le focus quand l'overlay se referme, le
  bouton central le reçoit quand il s'ouvre, via `rememberTvInitialFocus` déjà
  utilisé ailleurs (tentatives bornées, pas de boucle).
- `PlayerOverlayHost` accepte une `interactionKey` : chaque appui relance le
  compte à rebours d'auto-masquage, sinon l'overlay se refermait au bout de 5 s
  en pleine navigation.
- Live : `isPlaying` est désormais suivi, pour que l'overlay reste ouvert quand
  le flux est mis en pause par la télécommande. L'avance et le retour rapides
  sont consommés sans effet (flux non seekable) ; suivant/précédent zappent.
- Un film n'ayant ni suivant ni précédent, ces touches y sont consommées sans
  effet plutôt que de déplacer le focus.

**Fichiers :** `presentation/player/core/PlayerRemoteKeys.kt` (nouveau),
`presentation/player/core/PlayerOverlayCore.kt`,
`presentation/player/PlayerScreen.kt`,
`presentation/vod/VodPlayerScreen.kt`,
`presentation/series/SeriesPlayerScreen.kt`,
`test/.../presentation/player/core/PlayerRemoteKeysTest.kt` (nouveau).

# 5. Validation

- `PlayerRemoteKeysTest` : 7 contrats JVM sur la table de correspondance
  (touches média reconnues, flèches non consommées overlay ouvert, révélation
  overlay fermé, centre = activation du bouton focalisé overlay ouvert).
- `./gradlew testDebugUnitTest`, `./gradlew assembleDebug`, `./gradlew lintDebug`
  passent ; `git diff --check` propre.
- Limite assumée : le déplacement réel du focus D-pad et le rendu TV ne sont pas
  vérifiables hors device (`AGENTS.md`). Seule la table de décision est testée.

# 6. Release

Version : v1.83.1

Commit : voir tag

Date : 2026-08-14
