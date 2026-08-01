# B16 - Vidéo corrompue (lignes horizontales) sur TV pour certaines chaînes et certains médias

## Informations générales

Status:
RELEASED

Created:
2026-08-01

Version constatée:
v1.64.10 (note : `versionName` actuel du repo est `1.64.14` ; à vérifier si régression déjà introduite entre ces deux versions)

Appareil TV constaté:
Philips 2021/22 UHD Android TV — Android 11 (API 30) — 4 processeurs

---

# 1. Description

Lors du lancement d'une chaîne TV en direct sur le téléviseur (Android TV), l'image affichée est totalement corrompue : la vidéo apparaît sous forme de bandes horizontales déchirées, avec des aplats de couleurs saturées (bleu, vert, jaune) empilés verticalement. Aucune image reconnaissable n'est restituée.

Le phénomène est également observé sur certains médias (VOD / séries), mais pas sur tous.

Le même contenu, lu depuis l'application sur téléphone Android, s'affiche correctement.

Le son n'a pas encore été qualifié (voir questions ouvertes).

## Preuve visuelle

Capture fournie par l'utilisateur : écran TV affichant un empilement de lignes horizontales bleues, violettes, jaunes et vertes, sans structure d'image identifiable.

---

# 2. Contexte

## Pourquoi cet élément existe

Le lecteur vidéo est le cœur fonctionnel de l'application. Une image illisible sur le principal appareil cible du projet (le téléviseur) rend la fonctionnalité de lecture TV en direct inutilisable pour les flux concernés.

## Quel problème il résout

Restaurer une lecture vidéo correcte sur téléviseur pour l'ensemble des flux déjà lisibles sur téléphone.

## Éléments de contexte technique connus

- Lecteur basé sur `androidx.media3` (ExoPlayer) en version `1.4.0`, avec le module HLS.
- Décodeurs logiciels d'appoint fournis par `nextlib-media3ext` `v0.8.2` pour les codecs non pris en charge matériellement.
- Le cœur du lecteur se trouve dans `app/src/main/java/com/cstv/app/presentation/player/core/ExoPlayerCore.kt`.
- L'écart de comportement TV / téléphone oriente vers une différence de pipeline de décodage ou de rendu entre les deux appareils, et non vers un problème de flux amont.

---

# 3. Objectif

- Reproduire le défaut de manière déterministe sur téléviseur.
- Identifier la cause de la corruption d'image (décodeur, surface de rendu, format de flux, ou combinaison).
- Corriger le rendu pour que tout contenu lisible sur téléphone le soit aussi sur téléviseur.
- Éviter toute régression sur les contenus actuellement lus correctement sur les deux plateformes.

## Critère de succès attendu

Les chaînes et médias identifiés comme défaillants s'affichent avec une image correcte sur le téléviseur, sans dégradation de performance ni régression sur les autres contenus.

---

# 4. Hypothèses

Ces hypothèses sont à confirmer ou infirmer lors de l'étape d'analyse. Aucune n'est retenue à ce stade.

1. **Échec du décodeur matériel du téléviseur**
   Le décodeur matériel du téléviseur échoue silencieusement sur un profil de flux donné (par exemple H.264 entrelacé, profil élevé, ou MPEG-2), et produit des trames invalides au lieu de remonter une erreur. Le téléphone, disposant d'un décodeur différent, y parvient.

2. **Contenu entrelacé non désentrelacé**
   Les flux TV en direct sont fréquemment entrelacés. Un défaut de gestion de l'entrelacement côté rendu produirait des artefacts en bandes horizontales.

3. **Non-recours au repli logiciel**
   Le repli vers les décodeurs logiciels de `nextlib-media3ext` ne se déclenche pas, car le décodeur matériel ne signale pas d'erreur — il rend une image corrompue tout en se déclarant fonctionnel.

4. **Incompatibilité de surface de rendu**
   La surface de rendu utilisée sur téléviseur (`SurfaceView` / `TextureView`) ou le mode de sortie couleur (HDR / plage de couleurs / alignement de stride) diffère de celui du téléphone.

5. **Corrélation avec le codec plutôt qu'avec le type de contenu**
   La formulation « certains médias » suggère que le facteur discriminant est le codec ou le profil d'encodage du flux, et non sa catégorie (TV en direct / VOD / séries).

---

# 5. Questions ouvertes

Réponses apportées par l'utilisateur (2026-08-01) :

1. **Modèle TV / version Android** — Philips 2021/22 UHD Android TV, Android 11 (API 30), 4 processeurs.
2. **Son** — correct pendant que l'image est corrompue. Le défaut est donc isolé à la piste vidéo (décodage ou rendu), pas au démuxage ni à l'audio.
3. **Apparition dès la première trame ?** — non tranché.
4. **Médias VOD/séries concernés** — non identifiés précisément ; l'utilisateur confirme avoir déjà rencontré le cas « de rares fois », sans pouvoir désigner un titre ou un point commun.
5. **Reproductible à 100 % ?** — non tranché pour les chaînes TV (semble systématique sur les chaînes concernées d'après la capture) ; rare/intermittent sur VOD.
6. **Régression ?** — inconnu, l'utilisateur n'a jamais testé de version antérieure.
7. **Comparaison avec une autre application** — **déterminant** : l'utilisateur a testé une **autre application IPTV sur le même téléviseur**, avec les **mêmes flux**, et l'affichage est correct. Cela **écarte l'hypothèse d'une limitation matérielle du décodeur du téléviseur** (Hypothèse 1 de la section 4 largement affaiblie) et oriente fortement vers un défaut côté configuration/pipeline de rendu propre à cette application (ExoPlayer/media3, sélection de piste, surface de rendu, ou repli logiciel mal déclenché).
8. **Changement de piste/qualité** — non testé.

## Conséquence sur les hypothèses

- Hypothèse 1 (échec intrinsèque du décodeur matériel du TV) : **affaiblie**, le même matériel décode correctement les mêmes flux via une autre application.
- Hypothèse 3 (non-recours au repli logiciel `nextlib-media3ext`) et Hypothèse 4 (incompatibilité de surface de rendu / configuration du renderer vidéo dans `ExoPlayerCore.kt`) : **renforcées**, car elles expliquent un défaut spécifique à l'intégration media3 de cette application plutôt qu'au matériel.
- Hypothèse 2 (entrelacement non géré) reste possible si l'autre application applique un désentrelacement que celle-ci n'applique pas.

---

# 6. Spécification fonctionnelle

## User story

En tant qu'utilisateur regardant l'application sur téléviseur Android TV, je veux que toute chaîne en direct ou tout média VOD/série se lance avec une image correcte, afin de pouvoir regarder mon contenu comme sur mobile.

## Parcours utilisateur

1. L'utilisateur sélectionne une chaîne TV (ou un média VOD/série) depuis l'application sur téléviseur.
2. Le lecteur se lance, l'audio démarre.
3. **Attendu** : l'image vidéo s'affiche correctement, sans artefact, avec une latence de démarrage comparable à celle observée sur mobile pour le même flux.
4. **Constaté (défaut)** : pour certaines chaînes et certains médias, l'image est totalement corrompue (bandes horizontales, aplats de couleurs saturées), sans structure reconnaissable, alors que le son reste correct.

## Règles métier

- Un flux qui se lit correctement sur mobile doit se lire correctement sur téléviseur : le device cible ne doit jamais dégrader le rendu par rapport au mobile pour un même contenu.
- Le défaut d'image ne doit jamais être silencieux : si un profil de flux/codec n'est pas décodable proprement par le pipeline choisi (matériel ou logiciel), l'application doit soit rendre une image correcte via un mécanisme de repli, soit signaler une erreur exploitable à l'utilisateur — jamais afficher une image corrompue en laissant croire que la lecture fonctionne.
- Le comportement doit être cohérent avec ce qu'obtient une application tierce sur le même appareil et les mêmes flux (référence de comportement correct déjà observée par l'utilisateur).

## Critères d'acceptation

- CA1 : Les chaînes TV actuellement affectées se lancent sur le Philips 2021/22 UHD Android TV (Android 11) avec une image correcte, identique en structure à ce qui est vu sur mobile pour le même flux.
- CA2 : Les médias VOD/séries précédemment touchés (une fois identifiés lors de l'implémentation, via logs/reproduction) se lisent avec une image correcte sur ce même appareil.
- CA3 : Aucune régression sur les contenus déjà lus correctement sur téléviseur (pas de dégradation de performance, pas de nouvelle latence de démarrage significative, pas de nouveau flux affecté).
- CA4 : Si un flux ne peut définitivement pas être rendu correctement (limite matérielle réelle malgré repli logiciel), l'application affiche un message d'erreur clair au lieu d'une image corrompue.

## Cas limites

- Changement de piste vidéo/qualité en cours de lecture sur un flux affecté : le nouveau flux sélectionné doit être soumis à la même logique corrective.
- Reprise de lecture (retour en arrière dans l'app, mise en veille/réveil du téléviseur) sur un flux précédemment affecté.
- Flux dont le profil vidéo change en cours de diffusion (ex. bascule de profil sur une chaîne live).

## Gestion des erreurs

- Si un repli logiciel est nécessaire pour restaurer une image correcte et qu'il induit un impact de performance notable, ce compromis doit être documenté en étape 3 (spécification technique) plutôt que laissé implicite.
- Toute erreur de décodage réelle (flux corrompu à la source, format non supporté) doit produire un message d'erreur utilisateur, jamais un rendu visuel corrompu silencieux.

---

# 7. Spécification technique

## 7.1 Cause racine identifiée

Le lecteur est construit en un point unique : `rememberManagedExoPlayer()` dans
`app/src/main/java/com/cstv/app/presentation/player/core/ExoPlayerCore.kt:30`. Les trois écrans de
lecture (`PlayerScreen`, `VodPlayerScreen`, `SeriesPlayerScreen`) consomment tous cette fonction.

La fabrique de renderers y est configurée ainsi (`ExoPlayerCore.kt:36-38`) :

```kotlin
// Décodeurs FFmpeg (NextLib) préférés pour l'audio : lit EAC3/AC3/DTS
// même sur les appareils sans décodeur matériel de ces codecs.
val renderersFactory = NextRenderersFactory(context)
    .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
    .setEnableDecoderFallback(true)
```

Le commentaire indique clairement une intention **audio uniquement** (EAC3/AC3/DTS). Or
`setExtensionRendererMode` est un réglage **global** de `DefaultRenderersFactory` : il s'applique
aussi bien aux renderers audio qu'aux renderers **vidéo** construits par `NextRenderersFactory`.

En mode `EXTENSION_RENDERER_MODE_PREFER`, les renderers d'extension sont insérés **avant** les
renderers `MediaCodec` dans la liste. La sélection de piste d'ExoPlayer retenant le premier renderer
capable de gérer le format, le décodeur **logiciel FFmpeg de NextLib est donc préféré au décodeur
matériel du téléviseur pour la vidéo**, dès lors que le codec du flux fait partie de ceux compilés
dans `nextlib-media3ext v0.8.2`.

Le symptôme observé — bandes horizontales, aplats de couleurs saturées, audio intact — est la
signature typique d'un chemin de rendu logiciel dont les trames YUV sont mal restituées vers la
`Surface` (alignement/stride, format de couleur ou conversion non gérés par la couche graphique de
l'appareil). L'audio est intact parce que le chemin audio, lui, fonctionne correctement avec FFmpeg :
c'était l'objectif initial du réglage.

## 7.2 Cohérence avec les faits constatés

| Fait constaté | Explication par la cause racine |
| --- | --- |
| Son correct, image corrompue | Renderer audio FFmpeg OK ; renderer **vidéo** FFmpeg défaillant sur cet appareil |
| Une autre application IPTV lit les mêmes flux correctement sur le même téléviseur | Elle utilise le décodeur **matériel** ; le matériel n'est donc pas en cause |
| Défaut sur « certaines » chaînes et de « rares » médias VOD | `FfmpegVideoRenderer` ne prend en charge qu'un sous-ensemble de codecs. Pour les autres, MediaCodec reste utilisé et l'image est correcte. La sélectivité observée est attendue |
| Aucun défaut sur téléphone | Chemin de rendu logiciel → `Surface` différent (SoC, version d'Android, pilote graphique). Le même code produit un rendu correct sur ce matériel |
| Aucune erreur de lecture remontée | `setEnableDecoderFallback(true)` régit le repli **entre décodeurs `MediaCodec`** en cas d'échec d'initialisation/de décodage matériel ; il ne fait pas basculer d'un renderer `MediaCodec` vers un renderer FFmpeg en cours de lecture (correction m1). Ici le décodeur logiciel FFmpeg, sélectionné dès le départ par le mode `PREFER`, se déclare fonctionnel et produit des trames invalides : aucun mécanisme de repli n'est sollicité. Cela explique le caractère silencieux du défaut, contraire à la règle métier de la section 6 |

## 7.3 Composants impactés

| Fichier | Nature de la modification |
| --- | --- |
| `presentation/player/core/ExoPlayerCore.kt` | Ajout de `VideoHardwarePreferredRenderersFactory` (sous-classe de `NextRenderersFactory` forçant le mode vidéo) et application de la politique dissociée audio/vidéo |
| `presentation/player/core/PlayerDecoderPolicy.kt` | **Nouveau** — politique de décodage isolée en Kotlin pur, testable en JVM (modes audio et vidéo dissociés — correction M1) |
| `presentation/player/PlayerScreen.kt` | Nettoyage : imports `NextRenderersFactory` et `DefaultRenderersFactory` inutilisés (une seule occurrence = l'import) |
| `presentation/vod/VodPlayerScreen.kt` | Idem |
| `presentation/series/SeriesPlayerScreen.kt` | Idem |
| `test/.../player/core/PlayerDecoderPolicyTest.kt` | **Nouveau** — tests unitaires JVM de la politique de décodage |

## 7.4 Dépendances

Aucune nouvelle dépendance. `androidx.media3 1.4.0` et `nextlib-media3ext v0.8.2` sont conservés :
l'extension FFmpeg reste indispensable pour l'audio EAC3/AC3/DTS sur les appareils dépourvus de
décodeur matériel correspondant. C'est précisément ce besoin qui interdit de simplement remplacer
`NextRenderersFactory` par `DefaultRenderersFactory`.

## 7.5 Performances

Le passage au décodage matériel pour la vidéo est un **gain** sur cet appareil : le téléviseur Philips
ne dispose que de 4 cœurs, et le décodage logiciel d'un flux HD y est coûteux en CPU. Aucune
régression de performance n'est attendue ; une amélioration de la fluidité et de la consommation est
probable sur les flux actuellement décodés en logiciel.

## 7.6 Compatibilité

- Aucun changement de `minSdk`/`targetSdk`, aucune migration de données, aucun impact sur le cache
  ou le stockage.
- Le comportement audio EAC3/AC3/DTS est préservé sur les appareils sans décodeur matériel (voir
  section 8.2, justification du mode retenu).
- Aucun impact sur la sécurité.

## 7.7 Risques techniques

| Risque | Probabilité | Mitigation |
| --- | --- | --- |
| Un appareil possède un décodeur matériel `MediaCodec` déclaré mais défaillant pour un codec donné | Faible | `setEnableDecoderFallback(true)` est conservé : en cas d'échec réel d'initialisation/de décodage d'un décodeur `MediaCodec`, ExoPlayer essaie un autre décodeur `MediaCodec` (le repli documenté par cette option ne couvre pas un basculement vers FFmpeg en cours de lecture, cf. m1) |
| Un codec vidéo n'est décodable que par FFmpeg (pas de décodeur matériel du tout) | Faible | Le mode vidéo retenu (`ON`, cf. 8.2) **conserve** les renderers vidéo FFmpeg en dernier recours plutôt que de les supprimer : si aucun `MediaCodecVideoRenderer` ne déclare supporter le format, FFmpeg est sélectionné |
| Un décodeur audio `MediaCodec` annonce un codec mais le restitue mal | Faible — comportement inchangé par rapport à l'origine | Le mode audio reste `PREFER` (correction M1) : FFmpeg audio prime sur `MediaCodec` exactement comme avant B16, aucune régression introduite sur ce point |
| Le correctif ne résout pas le défaut (décodeur matériel réellement en cause) | Faible — contredit par le test utilisateur sur une autre application | Étape de diagnostic prévue (8.4) pour tracer le décodeur effectivement retenu |
| Validation impossible en automatique sur le matériel affecté | Certaine | Voir 7.8 |

## 7.8 Contrainte de validation

Conformément à `AGENTS.md` (« Exclusion des tests manuels ou sur device connecté ») et à la règle 9
du workflow, la confirmation visuelle sur le téléviseur Philips **ne fait pas partie des critères de
validation de l'agent**. Elle relève d'une vérification utilisateur post-livraison.

Pour rester validable automatiquement, les deux modes retenus (`AUDIO_EXTENSION_RENDERER_MODE`,
`VIDEO_EXTENSION_RENDERER_MODE`) et l'activation du repli sont extraits dans un objet Kotlin pur
(`PlayerDecoderPolicy`), sans dépendance à `Context` ni au runtime Android, testable via
`./gradlew testDebugUnitTest`. Le projet ne dispose pas de Robolectric (dépendances de test :
JUnit 4, coroutines-test, Mockito, sqlite-jdbc) ; cette extraction est donc la seule voie pour
verrouiller les valeurs de la politique par un test automatisé.

Le câblage réel (sous-classe `VideoHardwarePreferredRenderersFactory` de `NextRenderersFactory`
dans `ExoPlayerCore.kt`, qui force le mode vidéo dans `buildVideoRenderers`) reste, comme le reste
de `rememberManagedExoPlayer`, dépendant du runtime Android et donc hors du périmètre testable en
JVM pur — au même titre que le code qu'il remplace, qui n'était pas davantage couvert avant B16.

---

# 8. Architecture

## 8.1 Flux de décodage — avant / après

**Avant (défaut) — un seul mode global `PREFER` pour audio et vidéo**

```
Flux vidéo ─► ExoPlayer
               └─ NextRenderersFactory (mode global PREFER)
                    ├─ [1] FfmpegVideoRenderer  ◄── sélectionné (logiciel)  ► image corrompue sur TV
                    └─ [2] MediaCodecVideoRenderer (matériel, jamais atteint)
Flux audio ─► NextRenderersFactory (mode global PREFER)
               └─ [1] FfmpegAudioRenderer ◄── sélectionné ► son correct
```

**Après (corrigé) — modes dissociés par type de piste (correction M1)**

```
Flux vidéo ─► ExoPlayer
               └─ VideoHardwarePreferredRenderersFactory.buildVideoRenderers()
                    force le mode ON pour la vidéo, quel que soit le mode global configuré
                    ├─ [1] MediaCodecVideoRenderer ◄── sélectionné (matériel) ► image correcte
                    └─ [2] FfmpegVideoRenderer (dernier recours si codec non supporté par MediaCodec)
Flux audio ─► NextRenderersFactory.buildAudioRenderers() (mode global PREFER, inchangé)
               └─ [1] FfmpegAudioRenderer ◄── sélectionné, comme avant B16 ► EAC3/AC3/DTS couverts
```

Le mode global de la factory (`setExtensionRendererMode`) reste réglé sur `PREFER` — c'est lui que
`buildAudioRenderers` (non surchargé) utilise. Seul `buildVideoRenderers` est surchargé pour
recevoir `ON` au lieu du mode global, sans jamais toucher au champ partagé ni au comportement audio.

## 8.2 Décision technique — solution retenue

**Solution A retenue, révisée après review (M1) : dissocier le mode de renderer d'extension par
type de piste — `PREFER` pour l'audio (inchangé), `ON` pour la vidéo.**

Une première version appliquait `EXTENSION_RENDERER_MODE_ON` globalement via
`setExtensionRendererMode`, ce qui corrigeait la vidéo mais rétrogradait aussi FFmpeg après
`MediaCodec` côté **audio** — régression relevée en review (M1), puisque l'objectif initial du
réglage (préférer FFmpeg pour EAC3/AC3/DTS) n'était plus respecté à l'identique dans tous les cas
(un `MediaCodecAudioRenderer` qui déclarerait à tort supporter le format aurait pu être retenu
avant FFmpeg).

`DefaultRenderersFactory.setExtensionRendererMode` étant un champ global unique, dissocier les deux
modes impose de surcharger la construction des renderers vidéo : `VideoHardwarePreferredRenderersFactory`
(sous-classe de `NextRenderersFactory`, dans `ExoPlayerCore.kt`) surcharge `buildVideoRenderers` et
délègue à `super.buildVideoRenderers(...)` avec `PlayerDecoderPolicy.VIDEO_EXTENSION_RENDERER_MODE`
(`ON`) au lieu du mode reçu en paramètre. `buildAudioRenderers` n'est pas surchargé : il continue
d'utiliser le mode global de la factory, réglé sur `PlayerDecoderPolicy.AUDIO_EXTENSION_RENDERER_MODE`
(`PREFER`) — comportement audio strictement identique à l'origine.

Justification :

- La vidéo repasse par le décodeur matériel, ce qui restaure le comportement de référence déjà
  observé par l'utilisateur avec une autre application sur le même téléviseur.
- L'audio n'est plus affecté par le correctif : aucune régression possible sur EAC3/AC3/DTS.
- Les deux renderers FFmpeg (audio et vidéo) restent présents dans les deux cas en dernier recours ;
  seul l'ordre change, jamais leur disponibilité.

**Solutions écartées :**

| Solution | Raison du rejet |
| --- | --- |
| Mode global unique `ON` (première version, avant review) | Corrige la vidéo mais modifie aussi la priorité audio (M1) : hors du périmètre du défaut B16 |
| Remplacer `NextRenderersFactory` par `DefaultRenderersFactory` | Supprime le décodage audio EAC3/AC3/DTS logiciel — régression fonctionnelle sur les appareils sans décodeur matériel |
| `EXTENSION_RENDERER_MODE_OFF` (global) | Même conséquence : désactive toutes les extensions, dont l'audio |
| Désactiver l'extension uniquement sur Android TV (détection `UI_MODE_TYPE_TELEVISION`) | Traite le symptôme sur une seule famille d'appareils, laisse le défaut latent ailleurs. La préférence du décodage logiciel vidéo n'est de toute façon souhaitable sur aucun appareil |
| Forcer un `TextureView` au lieu du `SurfaceView` par défaut de `PlayerView` | Contourne le rendu sans traiter la préférence de décodeur ; dégrade les performances et la consommation |

**Solution B, conditionnelle (non appliquée par défaut) :** si le défaut persistait après la
solution A — cas où le décodeur matériel serait réellement en cause pour ces flux — filtrer
explicitement les renderers vidéo logiciels via une sous-classe de `NextRenderersFactory` surchargeant
`buildVideoRenderers` et retirant les renderers FFmpeg de la liste produite. Cette solution est
documentée ici mais **ne sera implémentée que sur constat d'échec de la solution A**, afin de ne pas
supprimer un chemin de repli utile.

## 8.3 Responsabilités des composants

- **`PlayerDecoderPolicy` (nouveau, Kotlin pur)** — porte les deux modes de renderer d'extension
  (audio `PREFER`, vidéo `ON`) et l'activation du repli de décodeur. Aucune dépendance Android →
  testable en JVM ; verrouille par test que les deux modes restent distincts (M1).
- **`VideoHardwarePreferredRenderersFactory` (nouveau, dans `ExoPlayerCore.kt`)** — sous-classe de
  `NextRenderersFactory` dont la seule responsabilité est de forcer le mode vidéo dans
  `buildVideoRenderers`, sans toucher à `buildAudioRenderers`.
- **`ExoPlayerCore.rememberManagedExoPlayer`** — construit `VideoHardwarePreferredRenderersFactory`
  et lui applique le mode audio de `PlayerDecoderPolicy` via `setExtensionRendererMode`. Reste le
  point de construction **unique** du lecteur : le correctif profite automatiquement au Live, à la
  VOD et aux séries.
- **`PlayerScreen` / `VodPlayerScreen` / `SeriesPlayerScreen`** — consommateurs ; aucune logique de
  décodage. Leurs imports `NextRenderersFactory` / `DefaultRenderersFactory` étaient des reliquats
  non utilisés, retirés pour éviter qu'une configuration divergente y soit réintroduite.

## 8.4 Diagnostic complémentaire (facultatif)

Pour lever tout doute résiduel sur le décodeur effectivement retenu, un `AnalyticsListener` peut
journaliser `onVideoDecoderInitialized` (nom du décodeur) en build debug. Cette trace se lit via
logcat sur appareil : elle est donc **hors critères de validation automatisée** et ne conditionne pas
la livraison du correctif.

## 8.5 Impact sur les critères d'acceptation

- CA1 / CA2 — adressés par le retour au décodage matériel vidéo.
- CA3 — non-régression garantie par le maintien du repli FFmpeg (mode `ON`) et de
  `setEnableDecoderFallback(true)`.
- CA4 — un échec réel de décodage remonte désormais par le chemin d'erreur normal d'ExoPlayer,
  puisque le décodeur silencieusement défaillant n'est plus préféré.

---

# 9. Plan de développement

- [x] Tâche 1 — Créer `PlayerDecoderPolicy`

Objectif :
Extraire la politique de décodage (mode de renderer d'extension, activation du repli) dans un objet
Kotlin pur, sans dépendance Android, pour la rendre testable en JVM.

Fichiers :
- `app/src/main/java/com/cstv/app/presentation/player/core/PlayerDecoderPolicy.kt` (nouveau)

Validation :
Compile, exposé par une fonction pure appliquée à un `DefaultRenderersFactory`/`NextRenderersFactory`.

---

- [x] Tâche 2 — Corriger `ExoPlayerCore.kt` : `PREFER` → `ON`

Objectif :
Appliquer `PlayerDecoderPolicy` dans `rememberManagedExoPlayer` pour que la vidéo repasse par le
décodeur matériel tout en conservant le repli logiciel pour l'audio EAC3/AC3/DTS.

Fichiers :
- `app/src/main/java/com/cstv/app/presentation/player/core/ExoPlayerCore.kt`

Validation :
Le code utilise `EXTENSION_RENDERER_MODE_ON` via `PlayerDecoderPolicy`, `setEnableDecoderFallback(true)`
conservé.

---

- [x] Tâche 3 — Nettoyer les imports reliquats dans les 3 écrans de lecture

Objectif :
Retirer les imports inutilisés `NextRenderersFactory` / `DefaultRenderersFactory` dans les écrans
consommateurs, pour éviter qu'une configuration divergente du renderer y soit un jour réintroduite
hors de `ExoPlayerCore.kt`.

Fichiers :
- `app/src/main/java/com/cstv/app/presentation/player/PlayerScreen.kt`
- `app/src/main/java/com/cstv/app/presentation/vod/VodPlayerScreen.kt`
- `app/src/main/java/com/cstv/app/presentation/series/SeriesPlayerScreen.kt`

Validation :
Compile sans avertissement d'import inutilisé sur ces symboles.

---

- [x] Tâche 4 — Tests unitaires `PlayerDecoderPolicy`

Objectif :
Couvrir la politique de décodage : mode retenu (`ON`, pas `PREFER`/`OFF`), repli de décodeur activé.

Fichiers :
- `app/src/test/java/com/cstv/app/presentation/player/core/PlayerDecoderPolicyTest.kt` (nouveau)

Validation :
`./gradlew testDebugUnitTest` passe, tests non-régression existants passent.

---

# 10. Notes de développement

## Implémentation initiale (avant review)

- `PlayerDecoderPolicy` créé en Kotlin pur (`PlayerDecoderPolicy.kt`), sans dépendance à `Context` :
  expose `EXTENSION_RENDERER_MODE` (`ON`), `ENABLE_DECODER_FALLBACK` (`true`) et une fonction
  `apply(factory)` générique appliquée dans `ExoPlayerCore.rememberManagedExoPlayer`.
- `ExoPlayerCore.kt` : remplacement de la construction inline `.setExtensionRendererMode(PREFER)` par
  `PlayerDecoderPolicy.apply(NextRenderersFactory(context))`. L'import `DefaultRenderersFactory`
  devenu inutile a été retiré. Commentaire mis à jour pour référencer B16 et clarifier que le
  repli logiciel reste réservé à l'audio.
- Imports reliquats `NextRenderersFactory` / `DefaultRenderersFactory` retirés dans `PlayerScreen.kt`,
  `VodPlayerScreen.kt`, `SeriesPlayerScreen.kt` — ces écrans n'ont jamais construit leur propre
  `RenderersFactory`, seul `ExoPlayerCore.kt` le fait, donc aucun changement fonctionnel ici.
- `PlayerDecoderPolicyTest.kt` ajouté : vérifie le mode retenu (`ON`, pas `PREFER`), le repli de
  décodeur activé, et que `apply()` configure bien la factory passée.
- `./gradlew testDebugUnitTest` : succès, aucune régression sur les tests existants.
- **Hors périmètre signalé** : lors du premier lancement de la suite complète, `compileDebugKotlin` a
  échoué de façon intermittente sur `HomeViewModel.kt` (`Unresolved reference: CoroutineScope` ligne
  674 et erreurs de suspension liées, malgré l'import présent). Un second run à froid (`--rerun`) a
  recompilé sans erreur — comportement typique d'un cache incrémental/KSP périmé, sans lien avec les
  fichiers touchés par B16. `HomeViewModel.kt` était déjà modifié de façon non commitée avant le début
  de ce ticket (travaux T7/T8 en cours). Aucune modification apportée à ce fichier dans le cadre de
  B16 ; à surveiller si le souci se reproduit.

## Corrections apportées suite à la review (étape 7)

Review effectuée par l'utilisateur via ChatGPT (voir section 11) : mode global unique (M1, majeur) et
libellé imprécis du repli de décodeur (m1, mineur).

- **M1** — `PlayerDecoderPolicy` réécrit : `AUDIO_EXTENSION_RENDERER_MODE` (`PREFER`, inchangé) et
  `VIDEO_EXTENSION_RENDERER_MODE` (`ON`) remplacent l'unique `EXTENSION_RENDERER_MODE`. La fonction
  générique `apply()` est supprimée : `DefaultRenderersFactory.setExtensionRendererMode` étant un
  champ global partagé, elle ne pouvait pas exprimer deux modes différents par type de piste.
- **M1** — `ExoPlayerCore.kt` : ajout de `VideoHardwarePreferredRenderersFactory`, sous-classe privée
  de `NextRenderersFactory` qui surcharge `buildVideoRenderers(context, extensionRendererMode, ...)`
  et délègue à `super.buildVideoRenderers(context, PlayerDecoderPolicy.VIDEO_EXTENSION_RENDERER_MODE, ...)`
  — ignorant le mode reçu en paramètre pour forcer `ON` côté vidéo uniquement.
  `buildAudioRenderers` n'est pas surchargé : il continue d'utiliser le mode global de la factory,
  réglé sur `PlayerDecoderPolicy.AUDIO_EXTENSION_RENDERER_MODE` (`PREFER`) via
  `.setExtensionRendererMode(...)` sur l'instance. Vérifié par lecture des signatures `javap` de
  `androidx.media3.exoplayer.DefaultRenderersFactory` (jar `media3-exoplayer-1.4.0-api.jar` du cache
  Gradle) pour garantir la signature exacte de la méthode protégée à surcharger.
- **M1** — `PlayerDecoderPolicyTest.kt` mis à jour : les tests verrouillent désormais
  `AUDIO_EXTENSION_RENDERER_MODE == PREFER`, `VIDEO_EXTENSION_RENDERER_MODE == ON`, et que les deux
  modes sont distincts. Le test de câblage (`apply()` sur un mock) est retiré : la logique de câblage
  réelle (`VideoHardwarePreferredRenderersFactory`) est désormais dépendante du runtime Android
  (surcharge de méthode `NextRenderersFactory`), donc hors périmètre JVM pur — seule la politique
  (les constantes) reste testée, conformément à la contrainte de validation de la section 7.8.
- **m1** — Sections 7.2 et 7.7 corrigées : `setEnableDecoderFallback(true)` est désormais décrit comme
  gouvernant le repli **entre décodeurs `MediaCodec`**, pas un basculement `MediaCodec` → FFmpeg en
  cours de lecture.
- `./gradlew testDebugUnitTest` : succès après corrections, aucune régression.

## Validation finale (étape 8)

Vérification par rapport à la spécification fonctionnelle (section 6) et à la review corrigée
(section 11) :

- **CA1 / CA2** — la vidéo est décodée en priorité par le décodeur matériel
  (`VideoHardwarePreferredRenderersFactory` force `ON`), ce qui restaure le comportement de
  référence déjà observé par l'utilisateur avec une autre application sur le même téléviseur.
  Confirmation visuelle sur l'appareil Philips **non exécutée par l'agent** — hors périmètre de
  validation automatisée (AGENTS.md, règle 9 du workflow) ; à confirmer par l'utilisateur.
- **CA3 (non-régression)** — le mode audio reste `PREFER`, strictement identique à l'origine ;
  aucun autre comportement du lecteur n'est modifié. `./gradlew testDebugUnitTest` : succès complet,
  aucun test existant cassé (`26 fichiers modifiés au total dans l'arbre de travail, dont 4
  seulement liés à B16` — le reste est du travail T7/T8 déjà en cours, non touché par ce ticket).
- **CA4 (erreur non silencieuse)** — aucun changement du chemin d'erreur d'ExoPlayer ; un échec réel
  de décodage continue de remonter normalement. Le scénario qui rendait le défaut silencieux
  (décodeur logiciel se déclarant fonctionnel) disparaît puisque ce n'est plus lui qui est
  sélectionné en premier pour la vidéo.
- **Cas limites (section 6)** — changement de piste/qualité, reprise de lecture, changement de
  profil en cours de diffusion : tous passent par la même `VideoHardwarePreferredRenderersFactory`,
  construite une seule fois par session de lecture (`remember`) ; aucun cas particulier de code n'a
  besoin d'être traité séparément.
- **Règles métier** — respectées : comportement TV aligné sur mobile pour le chemin vidéo, defaut
  jamais silencieux (voir CA4), cohérence avec l'application tierce de référence.
- **Qualité technique** — cause racine identifiée avec certitude raisonnable (croisement de tous les
  faits constatés, section 7.2), correctif minimal et ciblé, signature de méthode surchargée
  vérifiée via `javap` sur le jar réel plutôt que supposée, régression détectée en review corrigée
  avant validation.
- **Tests** — `PlayerDecoderPolicyTest` verrouille la politique (modes distincts, valeurs figées) ;
  `./gradlew testDebugUnitTest` général au vert.

**Limite assumée de cette validation** : la confirmation visuelle sur le téléviseur Philips
2021/22 (device réel) n'est pas un critère de validation de l'agent et reste à faire par
l'utilisateur avant la livraison finale (étape 10), conformément à la contrainte documentée en
section 7.8.

---

# 11. Review

Status: RESOLVED

## Critique

Aucun.

## Majeur

### M1 — Le correctif change aussi la priorité du décodage audio

**Description :** `PlayerDecoderPolicy.apply()` applique un unique
`EXTENSION_RENDERER_MODE_ON` à `NextRenderersFactory`. Ce mode global place donc aussi
`FfmpegAudioRenderer` après `MediaCodecAudioRenderer`, alors que la configuration remplacée et son
commentaire indiquaient explicitement que FFmpeg devait être **préféré pour l'audio**
EAC3/AC3/DTS. Le changement corrige l'ordre vidéo, mais modifie simultanément une politique audio
qui n'appartient pas au défaut B16. Le test actuel entérine seulement ce mode global ; il ne couvre
pas la priorité audio historique.

**Impact :** sur un appareil dont le décodeur audio matériel annonce la prise en charge d'un codec
mais le restitue mal (ou ne produit pas de son dans la configuration de sortie réelle), MediaCodec
sera désormais retenu avant FFmpeg. Cela peut introduire une régression audio sur EAC3/AC3/DTS et ne
permet pas de garantir CA3. `setEnableDecoderFallback(true)` ne compense pas ce changement de
priorité entre renderers : il autorise le repli entre décodeurs MediaCodec dans le renderer
MediaCodec.

**Correction attendue :** dissocier les politiques par type de piste dans une fabrique dédiée :
conserver `PREFER` lors de la construction des renderers audio et utiliser `ON` lors de la
construction des renderers vidéo. Ajouter des tests de politique qui verrouillent explicitement les
deux ordres (`audio = PREFER`, `vidéo = ON`) ainsi que l'activation du repli MediaCodec.

## Mineur

### m1 — La documentation attribue au repli MediaCodec un basculement vers FFmpeg

**Description :** les sections 7.2, 7.7 et 8.2 présentent
`setEnableDecoderFallback(true)` comme capable de faire passer la lecture du renderer MediaCodec au
renderer FFmpeg après un échec d'initialisation ou de décodage. Cette option configure le repli entre
décodeurs MediaCodec ; l'ordre `ON` permet à FFmpeg d'être sélectionné quand le renderer matériel ne
déclare pas le format pris en charge, mais ne garantit pas un changement de renderer après une
erreur d'exécution.

**Impact :** le ticket surestime le comportement de secours et pourrait conduire à une mauvaise
analyse d'un futur flux dont MediaCodec annonce le support puis échoue pendant la lecture.

**Correction attendue :** corriger ces passages pour distinguer la sélection initiale du renderer
du repli entre décodeurs MediaCodec. Ne revendiquer un basculement MediaCodec vers FFmpeg en cours de
lecture que s'il est implémenté et couvert explicitement.

## Corrections demandées

- [x] M1 — Séparer la priorité des renderers audio et vidéo, puis couvrir les deux politiques.
      Corrigé via `PlayerDecoderPolicy.AUDIO_EXTENSION_RENDERER_MODE` / `VIDEO_EXTENSION_RENDERER_MODE`
      + `VideoHardwarePreferredRenderersFactory` (voir section 10). Tests mis à jour pour verrouiller
      les deux modes.
- [x] m1 — Rectifier la portée documentée de `setEnableDecoderFallback(true)`.
      Sections 7.2 et 7.7 corrigées : repli décrit comme circonscrit aux décodeurs `MediaCodec`,
      pas un basculement vers FFmpeg en cours de lecture.

---

# 12. Release

Version :
v1.65.0

Commit :
:sparkles: :technologist: :bug: release(catalog-popular-player): deliver dynamic sync, silent popular trends, and TV video rendering fix (v1.65.0)

Date :
2026-08-01
