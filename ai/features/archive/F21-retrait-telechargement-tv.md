# F21 - Retrait et désactivation complète des fonctionnalités de téléchargement sur TV

## Informations générales

Status:
RELEASED

Created:
2026-08-02

Version:
v1.67.0

Date:
2026-08-02

---

# 1. Description

Les fonctionnalités de téléchargement hors-ligne (pour les Films et les épisodes de Séries) ont été conçues pour l'utilisation mobile en déplacement (mode avion, absence de réseau). Sur Android TV, ces fonctionnalités sont totalement hors sujet : les téléviseurs et box TV sont connectés de façon permanente à Internet, disposent d'un stockage interne extrêmement restreint et n'ont pas d'usage hors-ligne.

L'objectif de cette évolution est de **retirer et masquer complètement toute trace des fonctionnalités de téléchargement lorsque l'application s'exécute sur Android TV** (quand `isTv` est vrai).

Sur mobile, l'intégralité des fonctionnalités de téléchargement reste fonctionnelle et inchangée.

---

# 2. Contexte

Actuellement, les téléchargements apparaissent à plusieurs endroits de l'interface, y compris sur TV :
1. **Sur l'Accueil (`HomeScreen.kt`)** : Si des téléchargements existent dans la base (par exemple complétés depuis un profil mobile car la base Room est partagée), une ligne `"home_downloads"` (Téléchargements) s'affiche sous forme de rangée horizontale.
2. **Sur la fiche de détails d'un Film (`VodDetailsScreen.kt`)** : Un bouton d'action global de téléchargement (`DownloadActionButton`) est affiché en dessous des boutons de lecture.
3. **Sur la fiche de détails d'une Série (`SeriesDetailsScreen.kt`)** : Chaque épisode de la liste affiche un bouton d'action individuel de téléchargement (`EpisodeDownloadControl`) à côté de son titre et de sa description.

En masquant conditionnellement ces éléments grâce à l'indicateur `isTv`, nous épurerons l'interface de la TV, éviterons les clics accidentels et économiserons de l'espace d'affichage précieux pour les téléviseurs.

---

# 3. Spécification fonctionnelle

## Objectif

Masquer complètement les boutons, sections et contrôles liés aux téléchargements sur l'interface Android TV (`isTv == true`), tout en les conservant actifs et visibles sur l'interface mobile (`isTv == false`).

## User stories

* **En tant qu'utilisateur Android TV**, lorsque je consulte l'Accueil de l'application, je ne vois jamais la section "Téléchargements", même si d'autres profils ont téléchargé des médias sur d'autres appareils.
* **En tant qu'utilisateur Android TV**, lorsque je consulte la fiche de détails d'un Film ou d'une Série, je ne vois aucun bouton "Télécharger" ou icône de téléchargement d'épisode. L'interface se concentre uniquement sur la lecture en streaming et les favoris.
* **En tant qu'utilisateur mobile**, je conserve mon accès complet aux téléchargements (boutons de téléchargement sur les fiches de détails, section de téléchargement sur l'Accueil, et écran dédié de gestion des téléchargements).

## Règles métier et d'intégration

1. **Masquage de la section Accueil** :
   * Dans `HomeScreen.kt`, conditionner l'affichage de la section `"home_downloads"` pour qu'elle ne soit ajoutée à la `LazyColumn` que si `!isTv` (mobile uniquement).
2. **Masquage sur la fiche Film** :
   * Dans `VodDetailsScreen.kt`, envelopper le composant `DownloadActionButton` dans une condition `if (!isTv)` pour qu'il soit totalement masqué sur TV.
3. **Masquage sur la fiche Série** :
   * Dans `SeriesDetailsScreen.kt`, masquer l'icône de téléchargement d'épisode (`EpisodeDownloadControl`) si `isTv` est vrai. L'épisode dans la liste ou dans la grille ne présentera aucun bouton d'action de téléchargement sur TV.

## Critères d'acceptation (Fonctionnels)

- [ ] Sur Android TV, la section "Téléchargements" n'apparaît plus sur l'Accueil.
- [ ] Sur Android TV, le bouton "Télécharger" n'apparaît plus sur la fiche de détails d'un Film (VOD).
- [ ] Sur Android TV, les icônes de téléchargement en face de chaque épisode d'une Série n'apparaissent plus.
- [ ] Sur mobile, toutes les fonctionnalités, boutons et sections de téléchargement restent affichés et opérationnels à 100%.

## Cas limites et gestion des erreurs

- Une base partagée contenant des téléchargements créés sur mobile ne doit jamais faire réapparaître une rangée, un bouton ou une action de téléchargement sur TV.
- Si une ancienne route ou un deep link pointe vers `downloads` sur TV, l'application ne doit ni planter ni démarrer un téléchargement ; le comportement de repli précis sera défini à l'étape 3.
- Le masquage TV ne supprime ni les fichiers hors ligne ni leurs métadonnées : ils restent disponibles lors d'une utilisation mobile ultérieure du même profil.

## Hypothèses et Questions ouvertes

* *Espace libéré sur TV* : Sur la fiche de détails des Films sur TV, le retrait de `DownloadActionButton` libère de la place verticale, ce qui améliorera l'alignement visuel et facilitera la navigation directe vers les titres associés.
* *Navigation TV* : L'écran dédié `"downloads"` (auquel on accède via la route `"downloads"`) ne sera plus atteignable sur TV car toutes ses portes d'entrée (bouton Accueil, etc.) seront masquées. Il n'est donc pas nécessaire de modifier le `NavGraph` ou d'ajouter des règles de blocage de route complexes ; le simple masquage visuel suffit largement et s'avère ultra-robuste.

---

# 4. Spécification technique

## Points d'entrée relevés dans le code

| # | Emplacement | Code actuel | Action |
| --- | --- | --- | --- |
| 1 | `presentation/home/HomeScreen.kt` l. 678-711 | `if (state.downloadedItems.isNotEmpty()) { item(key = "home_downloads") { … } }` | Condition étendue à `if (!isTv && state.downloadedItems.isNotEmpty())`. |
| 2 | `presentation/vod/VodDetailsScreen.kt` l. 224-229 | `Spacer(height = 16.dp)` + `DownloadActionButton(item, onDownload, onRemove)` | Bloc (espaceur inclus) enveloppé dans `if (!isTv) { … }`. |
| 3 | `presentation/series/SeriesDetailsScreen.kt` l. 916-920 | `EpisodeDownloadControl(download, onDownload, onRemoveDownload)` dans la `Row` d'actions de l'épisode | Appel conditionné par `!isTv`, avec propagation du drapeau jusqu'à la ligne d'épisode. |

`isTv` est déjà un paramètre des deux écrans de détails
(`VodDetailsScreen.kt` l. 67, `SeriesDetailsScreen.kt` l. 72) et de `HomeScreen`
(l. 79) : aucun nouveau paramètre à faire descendre depuis `AppNavGraph` ou
`MainActivity`.

**Point d'attention n° 3 :** le composant privé qui rend une ligne d'épisode
(autour de `SeriesDetailsScreen.kt` l. 890-935) ne reçoit pas `isTv`
aujourd'hui — l'appel à `EpisodeDownloadControl` y est inconditionnel. Il faut
donc ajouter un paramètre `isTv: Boolean` à ce composable privé et le renseigner
depuis l'appelant, qui le possède déjà. C'est la seule modification de signature
du ticket.

## Composants impactés

* `app/src/main/java/com/cstv/app/presentation/home/HomeScreen.kt`
* `app/src/main/java/com/cstv/app/presentation/vod/VodDetailsScreen.kt`
* `app/src/main/java/com/cstv/app/presentation/series/SeriesDetailsScreen.kt`

## Composants explicitement NON impactés

Décision de périmètre, conforme aux hypothèses validées à l'étape 2 :

* `presentation/components/DownloadActionButton.kt` — composant partagé, reste
  intact et pleinement fonctionnel pour le mobile.
* `presentation/downloads/DownloadsScreen.kt` — écran conservé tel quel.
* `presentation/navigation/NavGraph.kt` — la route `"downloads"` n'est **pas**
  supprimée ni gardée. Sur TV toutes ses portes d'entrée disparaissent (la
  rangée d'accueil était la seule ; `TvNavigation.destinations` ne l'expose pas).
  Ajouter une redirection conditionnelle créerait un chemin d'erreur à maintenir
  pour un cas qui ne peut plus se produire par navigation normale. Si un
  deep-link forçait la route, l'écran s'afficherait — sans planter, et sans
  possibilité de lancer un téléchargement puisque les boutons d'ajout, eux, sont
  masqués. Repli retenu : **statu quo, aucune règle de blocage**.
* `data/download/*`, `DownloadRepository`, `OfflineDownloadUtil`, WorkManager —
  inchangés. Les fichiers et métadonnées hors-ligne survivent au masquage TV et
  restent exploitables depuis le mobile sur le même profil.
* `HomeViewModel` — `downloadRepository.observeDownloads()` continue d'être
  collecté (l. 325-332). Voir « Performances » ci-dessous pour la justification.

## Modèles de données, API, services, stockage, cache

Néant. Aucune entité Room, aucune migration (base inchangée en version 21),
aucun DTO, aucun endpoint Xtream, aucun `UseCase`, aucun `Repository`, aucun
`ViewModel` touché. La feature est un masquage conditionnel de rendu.

## Performances

Le collecteur `observeDownloads()` de `HomeViewModel` (l. 325-332) est
**volontairement conservé sur TV**, bien que sa sortie ne soit plus affichée :

* il est déjà filtré (`status == COMPLETED`) et `distinctUntilChanged()`, donc
  il n'émet que sur un changement réel de la liste des téléchargements terminés,
  soit jamais sur TV en usage normal ;
* le rendre conditionnel à `isTv` obligerait à injecter la notion de plateforme
  dans un ViewModel qui l'ignore aujourd'hui — c'est-à-dire faire remonter une
  préoccupation `presentation` dans une couche qui n'en a pas besoin, pour un
  gain nul.

Gain réel côté TV : trois nœuds Compose de moins (une section `LazyColumn`
complète avec sa `LazyRow` et ses cartes, un bouton de la fiche film, une
`IconButton` par épisode listé), donc moins de nœuds focusables dans l'arbre —
ce qui améliore aussi la prévisibilité de la recherche de focus D-pad.

## Sécurité

Sans objet.

## Compatibilité

* **Mobile** : strictement aucun changement de comportement — toutes les
  conditions ajoutées sont de la forme `!isTv`, vraie sur mobile, donc le rendu
  actuel est conservé au nœud près.
* **Base partagée entre profils/appareils** : un `DownloadedItem` créé sur mobile
  reste en base ; sur TV il n'est simplement plus rendu. Aucune suppression,
  aucune migration, aucune perte.
* **Android TV** : le masquage étant purement conditionnel, aucune API
  spécifique n'est introduite ; min SDK 21 inchangé.

## Dépendances

Aucune dépendance Gradle ajoutée.

## Risques techniques

| Risque | Mitigation |
| --- | --- |
| Espaceur orphelin sur la fiche film TV (double marge ou trou) | Le `Spacer(height = 16.dp)` de `VodDetailsScreen.kt` l. 224 est inclus **dans** le bloc conditionnel, pas laissé à l'extérieur. |
| Ligne d'épisode TV déséquilibrée après retrait de l'`IconButton` | La `Row` d'actions (`SeriesDetailsScreen.kt` l. 916-924) conserve la durée et l'icône `PlayArrow` ; l'alignement est géré par la `Row` elle-même, aucune largeur fixe ne dépend du bouton retiré. |
| Régression de focus TV sur la fiche série | Le retrait d'un nœud focusable par épisode **simplifie** le parcours D-pad (durée → lecture) ; aucun `focusRequester` ne cible ce bouton dans le code actuel. |
| Un futur écran TV réintroduit un point d'entrée téléchargement | La règle est explicitée dans la review de l'étape 6 et les trois points sont listés ci-dessus ; aucun garde-fou runtime n'est ajouté (surdimensionné pour le besoin). |

## Contraintes de performance

Aucune. Le ticket ne touche ni le chargement de l'accueil, ni le catalogue, ni
la lecture.

---

# 5. Architecture

## Position dans la Clean Architecture

Feature intégralement `presentation`. Aucune descente vers `domain` ou `data` :
le fait qu'un téléchargement soit pertinent ou non relève de la plateforme
d'affichage, pas d'une règle métier. `DownloadRepository` et ses `UseCase` ne
connaissent donc pas `isTv` — et ne doivent pas l'apprendre.

```
presentation/
├── home/HomeScreen.kt              ← if (!isTv && downloadedItems.isNotEmpty())
├── vod/VodDetailsScreen.kt         ← if (!isTv) { Spacer + DownloadActionButton }
├── series/SeriesDetailsScreen.kt   ← ligne d'épisode : nouveau paramètre isTv
│                                      → if (!isTv) EpisodeDownloadControl(...)
└── components/DownloadActionButton.kt   ← INCHANGÉ (mobile)

data/download/, domain/repository/DownloadRepository   ← INCHANGÉS
presentation/downloads/DownloadsScreen.kt              ← INCHANGÉ
presentation/navigation/NavGraph.kt                    ← INCHANGÉ (route "downloads" conservée)
```

## Flux de données

```
DownloadRepository.observeDownloads()      (inchangé, TV comme mobile)
        │
        ▼
HomeViewModel : filter { status == COMPLETED }.distinctUntilChanged()
        │
        ▼
HomeState.downloadedItems                  (inchangé, alimenté sur TV aussi)
        │
        ▼
HomeScreen : if (!isTv && list.isNotEmpty())   ← SEUL point de décision
        │
        ├── mobile → item(key = "home_downloads") → HomeDownloadCard(...)
        └── TV     → aucun nœud composé
```

Le drapeau `isTv` est décidé une seule fois, dans `MainActivity.isTvDevice()`
(`UiModeManager.currentModeType == UI_MODE_TYPE_TELEVISION`), et se propage déjà
par paramètre jusqu'aux trois écrans. Aucune nouvelle source de vérité.

## Responsabilités des composants

* **`HomeScreen`** : décider quelles sections composer selon la plateforme. Elle
  le fait déjà pour l'en-tête (`if (!isTv) item(key = "home_header")`, l. 237) et
  pour les liens « Voir tout » (`HomeSectionRow`, l. 849) — la condition ajoutée
  suit exactement le même idiome.
* **`VodDetailsScreen` / `SeriesDetailsScreen`** : composer les actions
  pertinentes pour leur plateforme. Elles portent déjà `isTv` pour la mise en
  page (colonnes TV vs scroll vertical mobile).
* **`HomeViewModel`, `DownloadRepository`, `DownloadsScreen`** : inchangés, et
  volontairement ignorants de la plateforme.

## Décisions techniques

1. **Masquage à la composition, pas au niveau des données.** Ne pas vider
   `downloadedItems` dans le ViewModel : cela mélangerait plateforme et état
   métier, et rendrait le ViewModel dépendant de `isTv` sans bénéfice mesurable
   (le flux est déjà `distinctUntilChanged` et inerte sur TV).
2. **Aucun blocage de route.** La route `"downloads"` reste déclarée. Le
   masquage des portes d'entrée est suffisant et sans état à maintenir ; ajouter
   un garde conditionnel dans `AppNavGraph` créerait un chemin mort à tester.
   Repli retenu pour le cas limite « deep link vers `downloads` sur TV » :
   l'écran s'affiche, ne plante pas, et aucun nouveau téléchargement ne peut y
   être lancé puisque les boutons d'ajout sont masqués.
3. **Nouveau paramètre `isTv` sur la ligne d'épisode plutôt qu'un
   `CompositionLocal`.** Un `LocalIsTv` serait plus élégant à grande échelle mais
   introduirait une source de vérité implicite parallèle au paramètre `isTv`
   déjà propagé partout dans le projet. On reste sur l'idiome existant.
4. **`DownloadActionButton` n'est pas modifié.** Le composant partagé ne doit pas
   connaître la plateforme : c'est l'appelant qui décide de le composer ou non.
   Une variante `if (isTv) return` à l'intérieur du composant cacherait la
   décision à l'endroit où on la cherche.

## Stratégie de tests

Conformément à `AGENTS.md` (« Non prioritaire / pas sur-investir » : pas de test
de code de layout pur ; exclusion des vérifications nécessitant un device), aucun
test unitaire JVM n'est ajouté : le ticket n'introduit ni logique métier, ni
parsing, ni transformation d'état — uniquement trois conditions de composition.

Non-régression assurée par la suite existante : `./gradlew testDebugUnitTest`
(dont `HomeViewModelTest`, qui vérifie que `downloadedItems` reste alimenté et
doit continuer de passer sans modification — c'est précisément la preuve que la
couche données n'a pas bougé), puis `assembleDebug` et `lintDebug`.

---

# 6. Plan de développement

## Ordre d'exécution

Les portes d'entrée TV sont retirées dans les écrans appelants ; le domaine et
la route existante ne sont pas modifiés.

### Tâche 1 — Retirer la rangée de téléchargements de l'Accueil TV

- [x] Conditionner la composition de `home_downloads` à `!isTv`.

Objectif : ne plus exposer les téléchargements terminés dans l'Accueil Android
TV tout en conservant les données et le comportement mobile.

Fichiers : `presentation/home/HomeScreen.kt`.

Validation : aucune rangée ni carte de téléchargement n'est composée sur TV ;
`HomeViewModel.downloadedItems` reste inchangé et mobile conserve la rangée.

### Tâche 2 — Masquer les actions de téléchargement des fiches TV

- [x] Garder les actions film/épisode pour mobile uniquement, en propageant
  `isTv` jusqu'à la ligne d'épisode concernée.

Objectif : supprimer tout point de création de téléchargement sur TV sans
modifier `DownloadActionButton` ni le dépôt.

Fichiers : `presentation/vod/VodDetailsScreen.kt`,
`presentation/series/SeriesDetailsScreen.kt` et composables d'épisode appelés.

Validation : les fiches TV ne composent aucun bouton de téléchargement ; les
fiches mobiles conservent exactement les actions existantes.

### Tâche 3 — Vérifier les limites de périmètre et la non-régression

- [x] Contrôler que la route, l'écran et la couche données ne sont pas touchés.

Fichiers : tests existants et ce ticket.

Validation : `testDebugUnitTest`, `assembleDebug` et `lintDebug` passent ; la
route `downloads` reste tolérante sur TV et aucune validation device n'est
requise par le ticket.

---

# 7. Notes de développement

Implémentation conforme à la spécification, sans écart :
- `HomeScreen.kt` : condition `!isTv && state.downloadedItems.isNotEmpty()`.
- `VodDetailsScreen.kt` : `Spacer` + `DownloadActionButton` enveloppés dans `if (!isTv) { ... }`.
- `SeriesDetailsScreen.kt` : ajout du paramètre `isTv: Boolean` à `EpisodeCardItem` (seule modification de signature du ticket), `isTv = true` côté `TvLayout`, `isTv = false` côté `MobileLayout`, `EpisodeDownloadControl` conditionné par `!isTv`.
- Aucun test unitaire ajouté (conforme à la stratégie de tests du projet : layout pur, pas de logique métier).
- Validation : `./gradlew testDebugUnitTest assembleDebug lintDebug` passent tous (voir aussi F12/B19, livrés dans la même session).

---

# 8. Review

## Critique

Aucun problème critique identifié.

## Majeur

Aucun problème majeur identifié.

## Mineur

Aucun problème mineur identifié.

## Conclusion

Les trois portes d'entrée prévues sont correctement conditionnées à la
plateforme : rangée Accueil, action Film (espaceur inclus) et contrôle de chaque
épisode. Le comportement mobile reste inchangé, aucun composant de données ni
la route `downloads` n'a été modifié, et la propagation explicite de `isTv`
reste cohérente avec l'architecture existante.

Aucun correctif de code demandé à l'issue de cette review.

---

# Validation finale (étape 8)

**Comportement attendu / règles métier :** les trois portes d'entrée
téléchargement (rangée `"home_downloads"` sur l'Accueil, `DownloadActionButton`
sur la fiche Film, `EpisodeDownloadControl` sur la fiche Série) sont
conditionnées à `!isTv`, conformément à la section 3.

**Critères d'acceptation :**
- [x] TV : section "Téléchargements" absente de l'Accueil.
- [x] TV : bouton "Télécharger" absent de la fiche Film.
- [x] TV : icônes de téléchargement absentes de la liste des épisodes.
- [x] Mobile : fonctionnalités de téléchargement inchangées à 100 % (aucun
  composant de données ni la route `downloads` n'a été modifié).

**Cas limites :** base de données partagée entre profils/appareils — le
masquage est purement visuel côté TV et ne touche ni les fichiers hors ligne
ni leurs métadonnées, qui restent disponibles pour un usage mobile ultérieur
du même profil (conforme à la section 3, "Cas limites").

**Qualité technique / absence de régression :** propagation explicite de
`isTv` cohérente avec l'architecture existante ; `./gradlew testDebugUnitTest`
et `./gradlew assembleDebug` verts (suite complète, aucune régression).

**Validation visuelle sur appareil (TV/mobile) :** hors périmètre de cette
validation automatisée — nécessite un device/émulateur physique, explicitement
exclu des critères de validation finale par `AGENTS.md`.

**Status: VALIDATED**

---

# 9. Release

Version : v1.67.0

Commit : :sparkles: feat(tv): remove download features on Android TV interface (F21)

Date : 2026-08-02
