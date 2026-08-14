# F38 - Écran de chargement du premier remplissage du catalogue

## Informations générales

Status:
RELEASED

Created:
2026-08-14

---

# 1. Description

À la première utilisation sur un appareil vierge, l'application affiche un
Accueil vide juste après la saisie des identifiants IPTV : la synchronisation du
catalogue démarre en arrière-plan, sans être attendue par l'interface, et les
écrans lisent uniquement le cache Room, encore vide.

Cette fiche introduit un écran de chargement dédié, affiché entre la connexion
et l'entrée dans l'application, qui rend visible la progression du premier
remplissage et n'autorise l'accès aux écrans qu'une fois le catalogue exploitable.

---

# 2. Contexte

Séquencement actuel observé sur appareil vierge :

1. `IptvApplication.onCreate()` planifie `database_sync_work`
   (`PeriodicWorkRequest`, quotidien, avec délai initial) : ce worker n'amorce
   donc pas le catalogue à l'installation.
2. `LoginViewModel.login()` authentifie, puis appelle
   `catalogSyncManager.syncIfStale()` dans un `runCatching`, **sans l'attendre** :
   l'état `LoginState.Success` est publié immédiatement.
3. `MainActivity` enchaîne sur le gate profil, puis compose l'Accueil.
4. `CatalogSyncManagerImpl.runSync()` remplit les six sections dans l'ordre
   (`LIVE_CATEGORIES`, `LIVE_STREAMS`, `VOD_CATEGORIES`, `VOD_STREAMS`,
   `SERIES_CATEGORIES`, `SERIES_STREAMS`), puis l'enrichissement.

Conséquence : entre l'étape 3 et la fin de l'étape 4, l'utilisateur regarde un
Accueil vide, sans indication de progression ni estimation de durée, alors que
l'application travaille. Rien ne distingue visuellement ce cas d'une panne
(identifiants acceptés mais panel muet, catalogue réellement vide).

Éléments déjà en place et réutilisables :

- `CatalogSyncManager.syncState` : `SyncState.Running(section, index, total)`,
  `Success`, `Failed(kind, at)`.
- `CatalogSyncManager.catalogStatus` : `isComplete`, `isStale`, `isOffline`,
  `isNetworkOnline`, `isSyncing`, `lastFailureKind`.
- `CatalogSyncStateDao` : `lastSuccessAt` par section, remis à zéro par la purge.

---

# 3. Objectif

- Aucun écran vide subi lors du premier remplissage : l'attente est expliquée et
  chiffrée.
- L'application n'expose ses écrans de catalogue qu'une fois le catalogue
  exploitable, jamais avant.
- Un premier remplissage qui échoue produit un message compréhensible et une
  action de sortie, jamais un Accueil vide silencieux.
- Aucune régression sur les démarrages ordinaires : un appareil dont le
  catalogue est déjà présent ne doit pas gagner d'attente supplémentaire.

---

# 4. Décisions produit prises à l'étape 1

| Sujet | Décision |
|---|---|
| Forme de l'attente | Écran de chargement dédié plein écran, affiché après la connexion, indiquant l'étape en cours (Chaînes, Films, Séries) et la progression (`1/6`), alimenté par `SyncState.Running(section, index, total)`. Ni squelettes sur l'Accueil, ni simple bandeau. |
| Seuil de déblocage | Les six sections catalogue (`catalogStatus.isComplete == true`). L'enrichissement des fiches, l'EPG et TMDB restent en tâche de fond, sans bloquer l'entrée. |
| Sortie anticipée | Aucune tant que la synchronisation progresse. En cas d'échec : message clair, action « Réessayer » et action « Se déconnecter ». Pas de « Continuer quand même ». |
| Déclenchement | Uniquement quand la base catalogue est vide : première installation, ou juste après une purge (`clearCatalogCacheUseCase()` déclenché par `onAccountAuthenticated` lors d'un changement de serveur/compte Xtream). Pas d'affichage pour un catalogue simplement périmé ou en cours de rafraîchissement. |
| Périmètre plateformes | Mobile **et** Android TV : le défaut est identique sur les deux, l'écran doit exister dans les deux habillages. |

# 4bis. Décisions produit prises à l'étape 2

| Sujet | Décision |
|---|---|
| Premier remplissage partiel | L'écran est rejoué à chaque démarrage tant que les six sections n'ont pas toutes réussi **au moins une fois**. Le déclencheur devient donc « premier remplissage jamais achevé » (`catalogStatus.isComplete == false`) et non « base strictement vide » — précision de la décision de l'étape 1, qui visait le même besoin. Un catalogue complet mais périmé n'affiche jamais l'écran. |
| Perte de réseau pendant le remplissage | L'écran reste affiché et bascule sur un état « hors ligne » avec reprise automatique au retour du réseau. Actions « Réessayer » et « Se déconnecter » disponibles, sans être obligatoires. |
| Attente longue | Aucune coupure tant que la synchronisation progresse. Au-delà de 30 s, un message d'attente prolongée est ajouté (« Gros catalogue, cela peut prendre quelques minutes »). |
| Granularité de progression | Progression par section : libellé de l'étape en cours + « n/6 » + barre déterminée, alimentés par `SyncState.Running(section, done, total)`. Aucun compteur d'éléments intra-section. |

# 4ter. Décisions techniques prises à l'étape 3

| Sujet | Décision |
|---|---|
| Pilotage | Le gate observe `catalogStatus` et `syncState` et déclenche lui-même `syncNow()` si nécessaire. `LoginViewModel` n'est pas modifié : son `syncIfStale()` non bloquant reste en place. Un seul chemin couvre login manuel, auto-login et purge après changement de compte. |
| Téléchargements hors-ligne pendant le blocage | Blocage total, aucune échappatoire. Cas pénalisé assumé : changement de compte Xtream (qui purge le catalogue) suivi d'une perte de réseau — les téléchargements existants ne sont pas supprimés mais restent inaccessibles jusqu'au remplissage. |
| Emplacement | Gate composable dans `MainActivity`, inséré dans la chaîne existante `CstvGate → Splash → Sélection de profil → AppNavGraph`, juste avant le `NavGraph`. Pas de nouvelle route. |

---

# 5. Hypothèses

- Le gate profil (`ensureInitializedAndNeedsSelection()`) et l'écran de
  chargement s'enchaînent sans conflit : la création du profil par défaut ne
  dépend pas du catalogue.
- Le déclencheur est observable **sans nouveau stockage** : `catalogStatus.isComplete`
  vaut déjà « les six sections ont chacune au moins une synchronisation
  réussie », et `ClearCatalogCacheUseCase` appelle `syncStateDao.clear()` dans la
  même transaction que la purge — une purge ramène donc exactement à l'état
  « premier remplissage jamais achevé ». Vérifié à l'étape 3.
- La progression par section suffit à l'utilisateur : `SyncState.Running` est
  émis une fois par section, donc six fois. Sur un très gros catalogue, la barre
  peut rester immobile plusieurs dizaines de secondes à l'intérieur d'une
  section — c'est ce que le message d'attente prolongée compense.
- Le mode hors-ligne existant n'est pas concerné : sans catalogue et sans
  réseau, il n'y a rien à afficher — l'écran doit dire lequel des deux manque.

---

# 6. Questions ouvertes

| Sujet | Étape de résolution |
|---|---|
| ~~Formulation définitive des libellés (sections, messages d'échec par `SyncFailureKind`, message d'attente prolongée)~~ — tranché à l'étape 6 : libellés livrés validés, message `PANEL` gardé générique et §7.5 à rectifier (voir §12). | 6 - Review (résolu) |
| Faut-il journaliser la durée du premier remplissage (`IptvLog` « PERF ») pour objectiver le ressenti ? Sans conséquence produit, tranché à l'implémentation. | 5 - Implémentation |

Questions de l'étape 1 tranchées : remplissage partiel, perte de réseau, garde-fou
de durée, granularité de progression (étape 2) ; point d'insertion du gate et
mode de pilotage (étape 3). Voir les tableaux 4bis et 4ter.

---

# 7. Spécification fonctionnelle

## 7.1 User stories

- **US1** — En tant qu'utilisateur qui vient de saisir ses identifiants IPTV sur
  un appareil neuf, je veux voir que l'application prépare mon catalogue et où
  elle en est, plutôt qu'un Accueil vide sans explication.
- **US2** — En tant qu'utilisateur, je veux que l'application ne me laisse pas
  entrer dans des écrans vides : quand elle m'ouvre l'Accueil, il y a du contenu.
- **US3** — En tant qu'utilisateur dont la préparation échoue (panel muet,
  identifiants refusés, réseau coupé), je veux un message compréhensible et une
  action, jamais une attente muette.
- **US4** — En tant qu'utilisateur qui change de compte Xtream, je veux la même
  préparation visible, puisque mon catalogue précédent a été purgé.

## 7.2 Parcours utilisateur

**Parcours nominal (première installation)**

1. Compte CSTV résolu, identifiants IPTV saisis, connexion acceptée.
2. Le profil par défaut est créé (aucune sélection demandée : un seul profil).
3. **Écran de préparation du catalogue** (nouveau) : titre, étape en cours,
   « n/6 », barre de progression déterminée.
4. Les étapes défilent : Chaînes → Films → Séries (deux sections par famille,
   catégories puis flux).
5. Dès que la sixième section est écrite, l'écran se retire et l'Accueil
   s'affiche, rempli.
6. L'enrichissement des fiches, l'EPG et TMDB continuent en tâche de fond, sans
   bloquer ni signaler quoi que ce soit.

**Parcours démarrage suivant, catalogue complet** : aucun changement, l'écran ne
s'affiche pas — y compris si le catalogue est périmé et qu'une synchronisation
tourne en fond.

**Parcours changement de compte Xtream** : la purge à la connexion remet le
catalogue à zéro, l'écran s'affiche comme en première installation.

**Parcours remplissage partiel** : une section échoue (hors `AUTH`), les
suivantes sont tout de même tentées. À la fin, le catalogue est incomplet :
l'écran passe en état d'échec (§7.5). Au démarrage suivant, il est réaffiché
tant que les six sections n'ont pas toutes réussi une fois.

## 7.3 Contenu de l'écran

| Zone | Contenu |
|---|---|
| Titre | « Préparation de votre catalogue » |
| Sous-titre | « Première synchronisation avec votre fournisseur. » |
| Étape | Libellé de la section en cours + compteur `n/6` |
| Progression | Barre déterminée, `n/6` |
| Attente prolongée (> 30 s) | « Gros catalogue, cela peut prendre quelques minutes. » |
| Actions | Aucune en marche nominale. En échec : « Réessayer » (primaire) et « Se déconnecter » (secondaire) |

Libellés des six étapes :

| Section | Libellé affiché |
|---|---|
| `LIVE_CATEGORIES` | Catégories de chaînes |
| `LIVE_STREAMS` | Chaînes TV |
| `VOD_CATEGORIES` | Catégories de films |
| `VOD_STREAMS` | Films |
| `SERIES_CATEGORIES` | Catégories de séries |
| `SERIES_STREAMS` | Séries |

La section `ENRICHMENT` n'est jamais affichée : l'écran s'est déjà retiré quand
elle démarre.

## 7.4 Règles métier

- **RG1** — L'écran s'affiche si et seulement si : l'utilisateur est connecté,
  le gate profil est résolu, et `catalogStatus.isComplete == false`.
- **RG2** — Il se retire dès que `isComplete` passe à `true`, sans action de
  l'utilisateur et sans attendre la fin de l'enrichissement.
- **RG3** — Tant qu'il est affiché, aucun écran de l'application n'est
  accessible : ni Accueil, ni Téléchargements, ni Paramètres, ni recherche. Le
  retour arrière ne le contourne pas.
- **RG4** — À l'affichage, si aucune synchronisation ne tourne
  (`isSyncing == false`) et que le réseau est disponible, l'écran déclenche
  `syncNow(STARTUP)`. Si une synchronisation tourne déjà, il se contente de
  l'observer.
- **RG5** — Un catalogue complet mais périmé n'affiche jamais l'écran : la
  fraîcheur reste traitée en tâche de fond, comme aujourd'hui.
- **RG6** — Après un échec, aucune relance automatique en boucle : la reprise
  vient de l'action « Réessayer » ou du retour du réseau (RG8).
- **RG7** — Un échec `AUTH` n'est jamais rejoué automatiquement, même au retour
  du réseau : les identifiants sont en cause, pas la connexion.
- **RG8** — Perte de réseau : l'écran bascule en état hors ligne et repart
  automatiquement au retour de la connexion, sans action de l'utilisateur.
- **RG9** — Le message d'attente prolongée apparaît après 30 s d'affichage
  continu et ne masque ni ne remplace la progression.
- **RG10** — « Se déconnecter » efface la session comme la déconnexion existante
  et ramène à l'écran de connexion ; le catalogue partiel déjà téléchargé n'est
  pas purgé (la purge reste réservée au changement de compte).

## 7.5 Gestion des erreurs

| `SyncFailureKind` | Message | Actions |
|---|---|---|
| `NETWORK` | « Connexion perdue. La préparation reprendra automatiquement. » | Réessayer, Se déconnecter (reprise auto au retour du réseau) |
| `AUTH` | « Vos identifiants ont été refusés par le fournisseur. » | Se déconnecter (primaire), Réessayer |
| `PANEL` | « Votre fournisseur n'a rien renvoyé pour cette étape. » | Réessayer, Se déconnecter |
| `PARSE` | « Réponse illisible de votre fournisseur. » | Réessayer, Se déconnecter |
| `STORAGE` | « Espace de stockage insuffisant pour préparer le catalogue. » | Réessayer, Se déconnecter |
| `UNKNOWN` | « La préparation du catalogue a échoué. » | Réessayer, Se déconnecter |

Aucune trace technique n'est montrée (règle cahier des charges §5).

## 7.6 Cas limites

- **Rotation d'écran / retour depuis l'arrière-plan** : la synchronisation
  continue (portée par un singleton, pas par l'écran) ; l'écran se recompose sur
  l'étape réelle en cours. Le compteur des 30 s repart, sans conséquence.
- **Application tuée pendant le remplissage** : les sections déjà écrites sont
  committées ; au relancement, l'écran reprend au premier manque.
- **Panel renvoyant une section vide** : traité comme un échec `PANEL` par le
  manager (règle existante), donc catalogue incomplet, donc écran maintenu.
- **Hors ligne au moment de l'affichage** : l'écran s'ouvre directement en état
  hors ligne, sans déclencher de synchronisation (RG4).
- **Deep link « nouveaux épisodes »** : la cible est conservée et honorée après
  le retrait de l'écran, jamais ouverte pendant.
- **Second appareil / verrou de lecture (F37)** : sans objet, aucune lecture
  n'est possible depuis cet écran.

## 7.7 Critères d'acceptation

- **CA1** — Installation neuve + identifiants valides : aucun Accueil vide n'est
  visible à aucun moment ; l'écran de préparation est affiché de la connexion
  jusqu'à l'affichage d'un Accueil rempli.
- **CA2** — L'étape affichée et le compteur `n/6` correspondent à la section
  réellement en cours.
- **CA3** — Catalogue complet : l'écran ne s'affiche pas, y compris quand une
  synchronisation périodique tourne.
- **CA4** — Catalogue complet mais périmé : l'écran ne s'affiche pas.
- **CA5** — Échec sur une section : message conforme à §7.5, avec « Réessayer »
  et « Se déconnecter » ; « Réessayer » relance et remet l'écran en progression.
- **CA6** — Perte puis retour du réseau : reprise sans action de l'utilisateur.
- **CA7** — Échec `AUTH` : pas de relance automatique au retour du réseau.
- **CA8** — Redémarrage après un remplissage partiel : l'écran est réaffiché.
- **CA9** — Le retour arrière ne permet jamais d'atteindre un écran de catalogue
  depuis l'écran de préparation.
- **CA10** — Mobile et Android TV présentent le même parcours ; sur TV, le focus
  initial est sur « Réessayer » quand les actions sont visibles.

---

# 8. Spécification technique

## 8.1 Composants impactés

| Fichier | Nature |
|---|---|
| `presentation/bootstrap/CatalogBootstrapViewModel.kt` | **Nouveau** — état du gate |
| `presentation/bootstrap/CatalogBootstrapScreen.kt` | **Nouveau** — écran mobile + TV |
| `presentation/bootstrap/CatalogBootstrapUiState.kt` | **Nouveau** — modèle d'état exposé |
| `MainActivity.kt` | **Modifié** — insertion du gate dans la chaîne existante |
| `res/values/strings.xml` | **Modifié** — libellés (titre, six sections, six messages d'échec, attente prolongée, actions) |
| `app/src/test/java/.../presentation/bootstrap/CatalogBootstrapViewModelTest.kt` | **Nouveau** — tests d'état |

Aucune modification de `LoginViewModel`, `AuthRepositoryImpl`, `CatalogSyncManagerImpl`
ni des repositories : tout ce dont le gate a besoin est déjà exposé.

Le dossier `presentation/bootstrap/` s'ajoute à l'arborescence documentée dans
`AGENTS.md` (« Structure de dossiers attendue »), à mettre à jour à l'étape
Documentation.

## 8.2 Modèles de données

**Aucune migration Room, aucune nouvelle table, aucune nouvelle colonne.** Tout
l'état nécessaire est dérivé de l'existant :

- `catalog_sync_state` (version 27 en place) fournit `lastSuccessAt` par
  section ; `CatalogStatus.isComplete` en dérive déjà.
- `ClearCatalogCacheUseCase` appelle `syncStateDao.clear()` dans la même
  transaction que la purge du catalogue : après un changement de compte,
  `isComplete` retombe donc à `false` sans traitement supplémentaire.
- Aucune persistance d'un « bootstrap déjà vu » : la condition est un état de
  fait, pas un marqueur.

## 8.3 API, services, stockage

- Aucun nouvel appel réseau, aucun nouvel endpoint Xtream : le gate ne fait que
  déclencher `CatalogSyncManager.syncNow(SyncTrigger.STARTUP)`, qui emprunte le
  chemin existant (`XtreamRequestGate`, priorité, cooldown 403/429).
- Aucune nouvelle dépendance Gradle, donc aucune règle `-keep` supplémentaire.
- Aucun accès direct à Room depuis la couche présentation : le gate consomme les
  flux du `CatalogSyncManager` (interface `domain`).

## 8.4 Performances

- Coût ajouté au démarrage nominal (catalogue complet) : une lecture du flux
  `catalogStatus`, déjà collecté ailleurs dans l'application. L'écran n'est pas
  composé.
- L'écran ne se compose que dans le cas qu'il traite, et se retire par
  recomposition dès la bascule de `isComplete`.
- Aucune boucle de sondage : la progression est poussée par `syncState`. Le
  délai des 30 s est un `delay` unique côté Composable, pas un ticker (voir
  §9.5, règle « boucles infinies de tests » d'`AGENTS.md`).

## 8.5 Sécurité

Aucun identifiant, aucune URL de panel, aucune clé n'est affichée ni journalisée
par l'écran. Les messages d'échec sont typés par `SyncFailureKind`, jamais
construits depuis un message d'exception.

## 8.6 Compatibilité

- Min SDK 21 inchangé, mobile et TV couverts par le même gate (paramètre `isTv`,
  comme `SplashScreen` et `ProfileSelectionScreen`).
- Aucune migration : une installation existante avec catalogue complet ne voit
  jamais l'écran après mise à jour.
- Une installation existante dont le catalogue est incomplet (sync partielle
  historique) verra l'écran une fois, au premier démarrage après mise à jour.
  Comportement voulu, à signaler dans les notes de version.

## 8.7 Risques techniques

| Risque | Traitement |
|---|---|
| Double déclenchement : `LoginViewModel.login()` appelle déjà `syncIfStale()` et le gate peut appeler `syncNow()` dans la même fenêtre. | Sans effet : `syncNow()` prend le `Mutex` par `tryLock()` et repart en `AlreadyRunning`. Aucune requête panel supplémentaire. |
| Boucle de relance après échec. | RG6 : la relance n'est jamais automatique côté gate ; seuls « Réessayer » et l'observation de reconnexion du manager (débounce 5 s, plafond 15 min) relancent. |
| Blocage perpétuel si le panel ne renvoie jamais une des six sections. | L'écran reste, mais toujours avec message typé et deux actions de sortie (RG10). Aucun état muet. |
| Gel des tests unitaires par tâche périodique. | Aucun ticker dans le ViewModel ; le délai d'attente prolongée vit dans un `LaunchedEffect`. Règle `Timeout.seconds(60)` ajoutée au test du ViewModel. |
| Régression du gate profil ou du deep link « nouveaux épisodes ». | Le gate s'insère **après** le gate profil et **avant** le `NavGraph` : `pendingSeriesId` est déjà mémorisé dans `MainActivity` et consommé après résolution. |

---

# 9. Architecture

## 9.1 Architecture proposée

Un gate de plus dans la chaîne déjà en place de `MainActivity`, sans toucher à
la navigation :

```
CstvGateScreen            (compte CSTV, F33)
  ↓ résolu
SplashScreen              (auto-login + gate profil en cours)
  ↓ résolu
ProfileSelectionScreen    (si plusieurs profils, Phase 27)
  ↓ résolu
CatalogBootstrapScreen    ← NOUVEAU : tant que catalogStatus.isComplete == false
  ↓ isComplete == true
AppNavGraph               (Accueil, Live, VOD, Séries, ...)
```

Condition d'affichage, alignée sur les gates existants :

```kotlin
val showCatalogBootstrap = loggedInUser != null &&
    profileGateResolved &&
    bootstrapState.blocking
```

## 9.2 Flux de données

```
CatalogSyncManagerImpl (singleton)
  ├── catalogStatus : Flow<CatalogStatus>   (isComplete, isSyncing, isNetworkOnline, lastFailureKind)
  └── syncState     : StateFlow<SyncState>  (Running(section, done, total) | Failed | Success | Idle)
                        │
                        ▼
        CatalogBootstrapViewModel
          combine(catalogStatus, syncState) → CatalogBootstrapUiState
          + retry() → syncNow(SyncTrigger.STARTUP)
                        │
                        ▼
        CatalogBootstrapScreen (stateless, isTv)
```

`CatalogBootstrapUiState` (proposition) :

```kotlin
data class CatalogBootstrapUiState(
    val blocking: Boolean = false,        // catalogue incomplet → gate actif
    val stepLabelRes: Int? = null,        // libellé de la section en cours
    val stepIndex: Int = 0,               // 1..6
    val stepCount: Int = 6,
    val failure: SyncFailureKind? = null,
    val offline: Boolean = false
)
```

## 9.3 Responsabilités

| Composant | Responsabilité | Ne fait pas |
|---|---|---|
| `CatalogSyncManagerImpl` | Ordonnancer, écrire, classer les échecs, exposer progression et complétude | Ne connaît pas l'écran ; **non modifié** |
| `CatalogBootstrapViewModel` | Décider si le gate bloque, mapper `SyncState`/`CatalogStatus` en état d'UI, déclencher `syncNow()` et « Réessayer » | N'accède ni à Room ni au réseau |
| `CatalogBootstrapScreen` | Afficher progression, message, actions ; gérer focus TV et délai des 30 s | Aucune décision métier |
| `MainActivity` | Placer le gate dans la chaîne, câbler « Se déconnecter » sur le chemin de déconnexion existant | Ne calcule pas la condition de blocage |

## 9.4 Décisions techniques et justifications

- **Gate composable plutôt que route de navigation** : les trois gates existants
  (CSTV, splash, profil) fonctionnent ainsi ; une route imposerait de neutraliser
  le retour arrière et les deep links vers des écrans encore vides, pour un
  bénéfice nul.
- **Observation plutôt qu'attente dans le login** : un seul chemin couvre login
  manuel, auto-login et purge après changement de compte, et l'état de connexion
  ne dépend pas de la durée d'une synchronisation. `LoginViewModel` reste intact,
  donc ses tests aussi.
- **Aucun nouveau stockage** : `isComplete` est déjà la propriété recherchée, et
  la purge remet `catalog_sync_state` à zéro dans la même transaction que le
  catalogue. Un marqueur séparé pourrait diverger de l'état réel.
- **Déblocage aux six sections, pas à l'enrichissement** : l'enrichissement est
  borné et long (lots de 50, 500 ms d'espacement) ; les écrans sont pleinement
  utilisables sans lui.
- **`syncNow()` et non `syncIfStale()` depuis le gate** : le gate ne s'affiche
  que sur catalogue incomplet, cas où la fraîcheur n'a pas de sens ; `syncNow`
  respecte quand même l'unicité par `Mutex`.

## 9.5 Contraintes de test

- Le ViewModel est testé en JVM avec un faux `CatalogSyncManager` (interface
  `domain`, donc doublable sans Mockito sur classe) : bascule de `blocking`,
  mapping des six sections, mapping des `SyncFailureKind`, état hors ligne,
  absence de relance automatique après échec, non-déclenchement quand
  `isSyncing == true`.
- Règle `@get:Rule val globalTimeout = Timeout.seconds(60)` obligatoire dans le
  test du ViewModel (`AGENTS.md`).
- Aucune tâche périodique dans `init` : le délai d'attente prolongée est un
  `LaunchedEffect` côté Composable, hors du champ des tests JVM.

---

# 10. Plan de développement

Les tâches sont ordonnées par dépendance. Chacune reste réalisable dans une
session et ne déborde pas vers une route de navigation, une migration Room ou
une modification du moteur de synchronisation.

- [x] **F38-1 — Exposer l'état du gate de premier remplissage**

  **Objectif :** créer l'état de présentation et le `CatalogBootstrapViewModel`
  qui observe `CatalogSyncManager.catalogStatus` et `syncState`, décide du
  blocage, mappe progression/échec/hors-ligne, et offre une relance explicite.
  Le ViewModel ne déclenche `syncNow(STARTUP)` que si le catalogue est
  incomplet, le réseau disponible et aucune synchronisation n'est déjà en
  cours ; il ne crée aucune boucle périodique ni relance automatique après un
  échec.

  **Fichiers :**
  - `app/src/main/java/com/cstv/app/presentation/bootstrap/CatalogBootstrapUiState.kt` (nouveau)
  - `app/src/main/java/com/cstv/app/presentation/bootstrap/CatalogBootstrapViewModel.kt` (nouveau)
  - `app/src/test/java/com/cstv/app/presentation/bootstrap/CatalogBootstrapViewModelTest.kt` (nouveau)

  **Validation :** tests JVM avec un faux `CatalogSyncManager` couvrant le
  blocage/déblocage sur `isComplete`, les six sections et leur compteur, les
  échecs typés, l'état hors ligne, l'absence de second déclenchement pendant une
  synchronisation, l'absence de relance après échec et la relance par action.
  Le test porte `Timeout.seconds(60)` et `./gradlew testDebugUnitTest` passe.

- [x] **F38-2 — Réaliser l'écran de préparation partagé mobile/TV**

  **Objectif :** créer un `CatalogBootstrapScreen` stateless qui rend l'état de
  F38-1 : titre, sous-titre, section, barre et compteur `n/6`, message après
  30 secondes, erreurs typées et actions. Il respecte les fonds, typographies,
  insets et couleurs des gates existants ; sur TV, « Réessayer » reçoit le focus
  initial dès que les actions deviennent visibles.

  **Fichiers :**
  - `app/src/main/java/com/cstv/app/presentation/bootstrap/CatalogBootstrapScreen.kt` (nouveau)
  - `app/src/main/res/values/strings.xml`
  - `docs/design-reference/mockup-source/Refonte-IPTV.dc.html` (consultation seulement : aucune maquette dédiée n'existe à l'étape 4)

  **Validation :** inspection Compose des états progression, attente prolongée,
  hors ligne et chaque échec ; les actions ne sont présentes qu'en état
  d'échec/hors-ligne, et le `LaunchedEffect` des 30 secondes est annulé quand
  l'écran quitte la composition. `./gradlew assembleDebug` et
  `./gradlew lintDebug` passent.

- [x] **F38-3 — Insérer le gate dans le démarrage et câbler les sorties**

  **Objectif :** dans `MainActivity`, instancier le ViewModel Hilt et composer
  le nouveau gate après la résolution du profil, avant toute composition du
  `AppNavGraph`. Câbler « Se déconnecter » sur le chemin de déconnexion déjà
  utilisé, sans créer de route ni modifier `LoginViewModel`,
  `CatalogSyncManagerImpl` ou la navigation. Garantir que les deep links restent
  mémorisés jusqu'au déblocage et que le retour arrière ne contourne pas le gate.

  **Fichiers :**
  - `app/src/main/java/com/cstv/app/MainActivity.kt`
  - `app/src/main/java/com/cstv/app/presentation/bootstrap/CatalogBootstrapScreen.kt`
  - `app/src/main/java/com/cstv/app/presentation/bootstrap/CatalogBootstrapViewModel.kt`

  **Validation :** revue du chemin connexion manuelle, auto-login, sélection de
  profil, changement de compte après purge et démarrage avec catalogue complet
  ou seulement périmé. Vérifier statiquement qu'aucun `AppNavGraph` ne peut être
  composé tant que `blocking` est vrai, puis exécuter
  `./gradlew testDebugUnitTest assembleDebug lintDebug`.

- [x] **F38-4 — Consolider les contrats de non-régression et documenter les résultats d'implémentation**

  **Objectif :** compléter les tests automatisés manquants révélés par
  l'intégration et consigner les choix réellement appliqués dans les notes de
  développement, sans élargir le périmètre aux données de synchronisation ou à
  une nouvelle navigation.

  **Fichiers :**
  - `app/src/test/java/com/cstv/app/presentation/bootstrap/CatalogBootstrapViewModelTest.kt`
  - `ai/features/F38-ecran-chargement-premier-remplissage-catalogue.md`
  - fichiers de F38-1 à F38-3 uniquement si nécessaires à une correction de
    non-régression

  **Validation :** les CA1 à CA10 sont tracés vers des tests JVM ou, lorsqu'ils
  portent uniquement sur le rendu/focus Android TV, explicitement signalés
  comme limites de validation hors device. Exécuter
  `./gradlew testDebugUnitTest`, `./gradlew assembleDebug`,
  `./gradlew lintDebug` et `git diff --check`.

---

# 11. Notes de développement

## Étape 5 — 2026-08-14

- Ajout de `CatalogBootstrapViewModel` et de son état dédié. Il combine
  exclusivement `CatalogSyncManager.catalogStatus` et `syncState`, sans accès
  direct à Room ou au réseau. La demande initiale est unique pour un catalogue
  incomplet ; après un échec, seule l'action « Réessayer » ou le mécanisme de
  reconnexion déjà porté par le manager peut relancer une synchronisation.
- Ajout de `CatalogBootstrapScreen`, commun mobile/TV : les six sections,
  `n/6`, barre, délai unique de 30 s, messages typés et actions sont
  localisés. Sur TV, le bouton primaire reçoit le focus lorsque les actions
  apparaissent. Le délai est un `LaunchedEffect` annulé à la sortie, sans
  polling ni tâche périodique.
- Insertion du gate dans `MainActivity` après le gate profil et avant le
  `AppNavGraph`. Un état initial non résolu garde ce gate affiché jusqu'à la
  première lecture de `catalogStatus`, ce qui interdit même une composition
  transitoire de l'Accueil vide. La déconnexion réemploie exactement
  `LoginViewModel.logout()`.
- Décision d'implémentation : aucune journalisation `IptvLog` de durée n'est
  ajoutée. Elle n'est pas nécessaire au comportement demandé et éviter de
  multiplier les traces de démarrage sans usage opérationnel établi.
- Tests JVM ajoutés : sept contrats couvrent blocage/déblocage, les six
  sections, état hors ligne, tous les échecs typés, absence de relance
  automatique, relance explicite et absence de double déclenchement.
- Traçabilité des CA : CA1/CA8 sont garantis par le gate sur tout catalogue
  incomplet, CA2 par le mapping des six sections, CA3/CA4 par le déblocage sur
  `isComplete`, CA5/CA7 par les échecs typés et la relance explicite, CA6 par
  l'état hors ligne et le mécanisme de reconnexion existant du manager, CA9 par
  l'absence de `AppNavGraph` pendant le blocage. CA10 est couvert
  structurellement (même écran et focus primaire TV) ; son rendu D-pad reste
  volontairement hors validation automatisée, conformément à `AGENTS.md`.
- Validation : `testDebugUnitTest` complet, `assembleDebug` et `lintDebug`
  sont passés. Le test F38 ciblé compte 8 tests sans échec. Le lint a produit
  ses avertissements historiques (dépendances/API) sans finding F38. `git diff
  --check` est propre.

## Étape 7 — 2026-08-14

- C1 : la condition pure `shouldShowCatalogBootstrap(...)` exige maintenant
  simultanément une session IPTV et un gate profil résolu. `MainActivity` ne
  déclenche la synchronisation initiale que si ce gate est réellement affiché ;
  le test `CatalogBootstrapGateTest` couvre explicitement connexion et profil.
- C2/m1 : `CatalogSection` est déplacé dans `domain/sync/`. Le manager, le
  gate et leurs tests consomment les mêmes identifiants bas de casse ; aucun
  littéral de section ne reste recopié en présentation.
- M1/M3 : l'état hors-ligne dépend exclusivement de la connectivité actuelle.
  Un échec réseau durable ne bloque plus RG4 ; les phases hors six sections,
  dont `ENRICHMENT`, affichent un indicateur indéterminé. Les indicateurs et le
  compteur portent une sémantique de progression annoncée poliment.
- M2 : `CatalogSyncManagerImpl.observeReconnections()` lit le dernier échec
  catalogue et n'appelle plus `syncIfStale()` après un `AUTH`. Le contrat est
  testé sur les états persistés du manager.
- M4/m7 : les tests couvrent aussi le rebloquage après purge, la reprise après
  échec réseau persistant, l'enrichissement et le retour en progression après
  « Réessayer ». `retry()` réarme le cycle de démarrage avant sa demande
  explicite.
- m2/m3/m4/m6 : le libellé `PANEL` de §7.5 rejoint la chaîne livrée ; la barre
  conserve sa sémantique « section actuellement tentée » (`done + 1`), donc
  peut afficher `6/6` au début de la dernière section. Le choix gate/navigation
  est isolé dans `CatalogBootstrapContent` et le focus TV utilise les tentatives
  bornées partagées `rememberTvInitialFocus`.
- Traçabilité revue : CA1/CA8 (condition de gate et rebloquage), CA2 (constantes
  partagées), CA3/CA4 (catalogue complet), CA5 (retry vers `Running`), CA6
  (reprise réseau), CA7 (garde reconnexion `AUTH`) et CA9 (gate hors NavGraph)
  sont couverts par tests JVM. CA10 est structurellement couvert par le focus
  TV borné ; le rendu D-pad réel reste hors validation device conformément à
  `AGENTS.md`.

## Étape 8 — 2026-08-14

- Validation fonctionnelle des CA1 à CA9 : le gate n'est composé qu'après une
  session IPTV et un gate profil résolu ; il bloque tout `AppNavGraph` tant que
  les six sections ne sont pas complètes, se retire pour un catalogue complet
  ou seulement périmé, et se rebloque après purge. Les six constantes réelles
  de `CatalogSection` portent libellé et compteur. Les erreurs typées,
  l'hors-ligne, la relance explicite et l'indicateur indéterminé
  d'enrichissement sont couverts par les contrats JVM. Pour CA6/CA7, le chemin
  de reconnexion est vérifié dans le manager : il relance le catalogue périmé
  après retour du réseau et le contrat dédié interdit ce chemin après le dernier
  échec `AUTH`.
- CA10 est validé au niveau automatisable : le même composable est utilisé sur
  mobile et TV et le focus initial TV emploie les tentatives bornées partagées.
  Le rendu D-pad réel n'est pas un critère de validation de cette tâche : les
  vérifications sur appareil ou émulateur sont explicitement exclues par
  `AGENTS.md`.
- Référence visuelle consultée : la maquette ne comporte pas d'écran bootstrap
  dédié ; le composant conserve donc les tokens et le fond des gates existants,
  sans nouvelle décision de design.
- Validation automatisée : `./gradlew --no-daemon testDebugUnitTest assembleDebug
  lintDebug` passe (build, tests JVM et lint). Les rapports dédiés confirment
  12 tests `CatalogBootstrapViewModelTest`, 2 tests `CatalogBootstrapGateTest`
  et 26 tests `CatalogSyncManagerImplTest`, tous sans échec. `git diff --check`
  est propre, y compris pour les nouveaux fichiers non suivis.

---

# 12. Review

Revue technique du 2026-08-14, portant sur `CatalogBootstrapUiState.kt`,
`CatalogBootstrapViewModel.kt`, `CatalogBootstrapScreen.kt`,
`CatalogBootstrapViewModelTest.kt`, l'insertion dans `MainActivity.kt` et les
chaînes ajoutées. Aucun code modifié.

Status: RESOLVED

Toutes les corrections C1–M4 et m1–m7 ont été appliquées à l'étape 7 ; leur
traçabilité et les validations associées sont consignées dans les notes
d'étape ci-dessous.

## Décisions produit prises à l'étape 6

| Sujet | Décision |
|---|---|
| RG7 / CA7 (relance automatique après `AUTH`) | La garde est ajoutée dans `CatalogSyncManagerImpl.observeReconnections()` : pas de resynchronisation au retour du réseau lorsque le dernier échec est `AUTH`. Le périmètre de F38 est étendu à ce fichier, par exception au §8.1, parce que la règle est invérifiable ailleurs. |
| Libellé `PANEL` | La formulation générique « Votre fournisseur n'a rien renvoyé pour cette étape. » est retenue ; le §7.5 est corrigé pour s'aligner. Aucune remontée de la section fautive. |
| Phase sans progression lisible (`ENRICHMENT`, `SyncOutcome.Skipped`) | Repli sur l'indicateur indéterminé déjà utilisé pour l'état non résolu, sans texte d'étape. Aucun état sans retour visuel. |
| Retour arrière sur l'écran | Comportement actuel conservé : le retour quitte l'application, comme sur le splash et le gate profil. Aucun `BackHandler`. |

## Critique

**C1 — L'écran de connexion devient inaccessible sur une installation neuve**

- *Description* : dans `MainActivity.kt:310-329`, le gate est composé sans la
  condition `loggedInUser != null` pourtant écrite au §9.1. L'écran de connexion
  vit dans `AppNavGraph` (`NavGraph.kt:207`, `startDestination = if (loggedInUser
  == null) "login"`), c'est-à-dire dans la branche `else` désormais court-circuitée.
  Sur une installation neuve, le catalogue est vide, donc `blocking == true` et
  l'application affiche l'écran de préparation à la place du formulaire Xtream.
- *Impact* : bloquant. Aucun nouvel utilisateur ne peut se connecter. Sans
  identifiants, `runSync()` retourne `SyncOutcome.Skipped` sans toucher
  `syncState` : l'écran reste figé sans progression, sans message et sans action
  (`showActions == false`). « Se déconnecter » remet `loggedInUser = null`, donc
  ramène au même écran : l'impasse est totale. Même effet après une déconnexion
  dont le catalogue avait été purgé.
- *Correction attendue* : n'entrer dans le gate que si `loggedInUser != null`
  (et, conformément au §9.1, `profileGateResolved`), sinon composer `AppNavGraph`
  comme avant. Ajouter un test de non-régression sur cette condition d'affichage
  (extraction de la condition dans une fonction pure testable en JVM).

**C2 — Les clés de section ne correspondent à aucune valeur réelle**

- *Description* : `CatalogBootstrapViewModel.kt:88-105` compare `SyncState.Running.section`
  à `"LIVE_CATEGORIES"`, `"LIVE_STREAMS"`, etc. Les valeurs réellement émises par
  `CatalogSyncManagerImpl` viennent de `CatalogSection`
  (`data/local/entity/CatalogSyncStateEntity.kt`) et valent `"live_categories"`,
  `"live_streams"`, `"vod_categories"`, `"vod_streams"`, `"series_categories"`,
  `"series_streams"`. Aucune correspondance n'est donc jamais trouvée.
- *Impact* : bloquant sur la raison d'être de la fiche. En production,
  `stepLabelRes` reste `null` et `stepIndex` reste `0` pendant tout le premier
  remplissage : l'écran n'affiche que le titre et le sous-titre, sans étape, sans
  barre, sans `n/6`. CA2 est faux, US1 n'est pas rendu. Les tests ne le voient pas
  parce qu'ils injectent eux-mêmes les littéraux en majuscules
  (`CatalogBootstrapViewModelTest.kt:82-87`, `157`) : ils valident un contrat qui
  n'existe pas.
- *Correction attendue* : utiliser une constante partagée plutôt qu'un littéral
  recopié (voir m1), et faire porter les tests sur cette même constante, de sorte
  qu'un renommage casse la compilation et non le rendu.

## Majeur

**M1 — Un échec réseau mémorisé bloque le déclenchement prévu par RG4**

- *Description* : `toBootstrapState` calcule `offline = !isNetworkOnline ||
  failure == NETWORK`, et `failure` retombe sur `lastFailureKind`
  (`CatalogBootstrapViewModel.kt:69-83`) dès que `syncState` n'est ni `Running`
  ni `Failed`. Or `lastFailureKind` est lu dans `catalog_sync_state` et persiste
  jusqu'à la prochaine réussite de la section. Au redémarrage suivant, réseau
  disponible, `syncState == Idle` : l'état est `offline == true`, donc
  `startIfNeeded()` retourne immédiatement (`:51-53`).
- *Impact* : RG4 n'est pas appliquée dans le cas le plus fréquent de reprise
  (échec réseau la veille), et l'écran affiche « Connexion perdue. La préparation
  reprendra automatiquement. » alors que la connexion fonctionne. La reprise ne
  vient que de l'observateur de reconnexion du manager (débounce 5 s, un
  déclenchement par fenêtre de 15 min) : elle est réelle au démarrage à froid,
  absente si une reconnexion a déjà eu lieu dans les 15 minutes.
- *Correction attendue* : distinguer « hors ligne maintenant »
  (`!isNetworkOnline`) de « dernier échec de type réseau ». Ne bloquer
  `startIfNeeded()` que sur le premier ; afficher le message hors ligne sur le
  premier également, et le message d'échec réseau seulement quand l'échec est
  celui de la tentative en cours (`SyncState.Failed`). Test JVM associé :
  statut incomplet + `isNetworkOnline == true` + `lastFailureKind == NETWORK` +
  `syncState == Idle` doit déclencher exactement un `syncNow(STARTUP)`.

**M2 — RG7 n'est garantie nulle part**

- *Description* : le gate ne relance pas après un `AUTH`, mais
  `CatalogSyncManagerImpl.observeReconnections()`
  (`data/sync/CatalogSyncManagerImpl.kt:128-144`) appelle `syncIfStale()` à
  chaque retour de réseau, sans consulter le dernier échec. Un catalogue
  incomplet est toujours périmé (`isCatalogStale`, `lastSuccessAt <= 0`), donc la
  synchronisation repart et rejoue les appels refusés.
- *Impact* : CA7 n'est pas tenu, et le §11 le présente pourtant comme couvert. Du
  trafic authentifié refusé est réémis à chaque reconnexion, exactement ce que le
  commentaire de `runSync()` cherche à éviter (rapprochement du bannissement).
- *Correction attendue* : conformément à la décision d'étape 6, court-circuiter
  la reconnexion quand le dernier échec connu est `AUTH`, avec un test JVM sur le
  manager. À défaut, retirer CA7 — mais la décision prise est la garde.

**M3 — Phases sans progression lisible : écran totalement muet**

- *Description* : quand `SyncState.Running` porte `ENRICHMENT`, `isCatalogStep`
  est faux, donc `stepLabelRes == null` et `stepIndex == 0`
  (`CatalogBootstrapViewModel.kt:74-75`) ; `isResolved` étant vrai, aucun
  indicateur n'est composé (`CatalogBootstrapScreen.kt:86-105`). Le cas se
  produit à chaque remplissage partiel non `AUTH` : les six sections sont
  tentées, l'enrichissement s'exécute ensuite (lots de 50, 500 ms d'espacement)
  alors que le catalogue est incomplet, donc que le gate bloque. Même effet quand
  `runSync()` retourne `Skipped` sans modifier `syncState`.
- *Impact* : l'écran affiche titre et sous-titre, rien d'autre — ni progression,
  ni message, ni action — pendant une durée qui peut atteindre plusieurs minutes.
  C'est précisément l'« état muet » que le §8.7 déclare impossible.
- *Correction attendue* : repli sur l'indicateur indéterminé pour toute phase non
  mappée (décision d'étape 6), et test JVM couvrant `Running(ENRICHMENT, …)` sur
  catalogue incomplet.

**M4 — Les tests ne couvrent pas les critères d'acceptation qu'ils prétendent tracer**

- *Description* : les huit tests portent sur des états injectés à la main. Il
  manque : le re-blocage après purge (`isComplete` vrai → faux, CA8/US4), la
  reprise avec échec persistant en base (M1), la phase `ENRICHMENT` (M3), l'état
  après `retry()` (CA5 : retour effectif en progression), et surtout tout ancrage
  sur les valeurs réelles de section (C2). Le §11 affirme « CA2 par le mapping des
  six sections » et « CA7 par … la relance explicite », deux affirmations
  contredites par C2 et M2.
- *Impact* : la couverture donne une fausse assurance ; deux défauts bloquants
  passent au vert.
- *Correction attendue* : compléter les tests ci-dessus, puis reprendre le
  tableau de traçabilité du §11 pour qu'il décrive l'état réel après correction.

## Mineur

- **m1 — Clés de section recopiées dans la couche présentation.**
  `CatalogBootstrapViewModel.kt:88-105` duplique des littéraux au lieu de
  référencer `CatalogSection`, qui vit dans `data/local/entity/`. La présentation
  ne doit pas dépendre de `data` (AGENTS.md, Clean Architecture) : déplacer ces
  identifiants de section dans `domain/sync/` (par exemple à côté de `SyncState`),
  les faire consommer par `CatalogSyncManagerImpl` et par le gate. Cause racine de
  C2. Impact : toute évolution du nommage casse le rendu en silence.
- **m2 — §7.5 à corriger.** La spec annonce « Votre fournisseur n'a rien renvoyé
  pour : {section} », les chaînes livrées disent « pour cette étape ». Décision
  d'étape 6 : garder le générique et rectifier le §7.5.
- **m3 — Sémantique de la barre de progression.** `progressFraction =
  stepIndex / stepCount` avec `stepIndex = done + 1` : une section à peine
  commencée est comptée comme terminée, la barre atteint 100 % au début de la
  sixième. Cohérent avec le compteur affiché, mais optimiste ; à défaut de
  changement, le documenter.
- **m4 — Imbrication de `MainActivity`.** Le `} else {` de `MainActivity.kt:329`
  et son accolade fermante `:572` laissent tout le bloc `AppNavGraph` à
  l'indentation précédente. Extraire la branche dans un composable privé
  (`AppContent(...)`) ou réindenter ; en l'état, la lecture du fichier le plus
  chargé du projet se dégrade.
- **m5 — Accessibilité.** `LinearProgressIndicator` et le compteur `n/6` n'ont
  aucune sémantique associée (pas de `contentDescription`, pas de
  `liveRegion`) ; un lecteur d'écran n'annonce pas la progression.
- **m6 — Focus TV.** `CatalogBootstrapActions` demande le focus une seule fois
  (`LaunchedEffect(isTv)`, `CatalogBootstrapScreen.kt:145-147`) alors que les
  boutons apparaissent au même instant que la composition ; le reste du projet
  retente (`FOCUS_REQUEST_ATTEMPTS` dans `MainActivity`). Si la demande échoue,
  aucune seconde tentative — CA10 repose sur un coup unique.
- **m7 — `retry()` ne réarme pas `startupRequestedForCurrentIncompleteCatalog`.**
  Sans conséquence aujourd'hui (le drapeau n'est relu qu'après déblocage), mais
  il rend le raisonnement sur l'état interne plus fragile qu'il n'en a l'air.

## Corrections demandées

Toutes les entrées ci-dessus, y compris les mineures (étape 7), dans cet ordre :

1. C1 — restaurer la condition `loggedInUser != null && profileGateResolved`
   autour du gate dans `MainActivity`, avec test de la condition d'affichage.
2. C2 + m1 — déplacer les identifiants de section dans `domain/sync/`, les
   consommer côté manager et côté gate, aligner les tests sur ces constantes.
3. M3 — indicateur indéterminé pour toute phase non mappée, test associé.
4. M1 — séparer « hors ligne maintenant » de « dernier échec réseau », test associé.
5. M2 — garde `AUTH` dans `observeReconnections()` de `CatalogSyncManagerImpl`,
   test associé (extension de périmètre décidée à l'étape 6).
6. M4 — compléter les tests manquants (purge, échec persistant, enrichissement,
   état après `retry()`), puis corriger la traçabilité des CA du §11.
7. m2 — corriger le §7.5 (message `PANEL` générique).
8. m3 à m7 — sémantique de la barre documentée, `MainActivity` réindenté ou
   extrait, sémantique d'accessibilité ajoutée, focus TV retenté comme ailleurs
   dans le projet, drapeau de démarrage réarmé dans `retry()`.

Validation attendue après corrections : `./gradlew testDebugUnitTest`,
`./gradlew assembleDebug`, `./gradlew lintDebug`, `git diff --check`.

---

# 13. Release

Version : v1.83.0

Commit : :sparkles: feat(bootstrap): écran de chargement du premier remplissage du catalogue (F38)

Date : 2026-08-14
