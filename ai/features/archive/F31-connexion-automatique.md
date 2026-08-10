# F31 - Gestion de compte : Connexion automatique au profil au démarrage

## Informations générales

Status:
RELEASED

Created:
2026-08-10

Version:
v1.77.5

Date:
2026-08-10

---

# 1. Description

Ajout d'une fonctionnalité permettant à l'utilisateur d'activer la connexion automatique sur un profil spécifique. Au démarrage de l'application (après login/auto-login sur le compte Xtream Codes), si un profil est désigné comme profil de connexion automatique, l'application doit s'y connecter directement et ignorer l'écran de sélection de profil ("Qui regarde ?").

---

# 2. Contexte

Actuellement, l'application propose une sélection de profils à chaque démarrage si plusieurs profils sont configurés (Netflix-style, introduit lors de la Phase 27). Cela oblige l'utilisateur qui n'a qu'un profil principal d'utilisation, ou qui souhaite un accès direct sans friction sur TV/mobile, à cliquer à chaque lancement. La possibilité d'associer un profil à une connexion automatique directe répond à ce besoin de fluidité, en supprimant l'étape de sélection intermédiaire.

---

# 3. Objectif

- Permettre à l'utilisateur de choisir un profil existant et d'activer/désactiver l'option de "Connexion automatique au démarrage" pour ce profil.
- Assurer qu'au démarrage de l'application, l'écran de sélection de profil soit totalement contourné si l'option est active pour un profil valide existant.
- Permettre d'éditer ou modifier ce choix depuis l'écran de gestion des profils.
- Maintenir la possibilité de changer manuellement de profil (depuis la Home ou les paramètres de profil) même si la connexion automatique est active pour l'un d'eux.

---

# 4. Spécification fonctionnelle

## User stories

- En tant qu'utilisateur ayant un profil principal, je peux activer « Connexion
  automatique au démarrage » depuis l'édition de ce profil afin d'arriver
  directement sur l'Accueil après l'authentification Xtream.
- En tant qu'utilisateur de plusieurs profils, je peux déplacer cette option
  vers un autre profil sans avoir à la désactiver manuellement sur le premier.
- En tant qu'utilisateur souhaitant ponctuellement changer de profil, je peux
  toujours ouvrir le sélecteur de profils et choisir un autre profil sans
  modifier mon choix de démarrage automatique.

## Périmètre

- L'option est disponible uniquement dans la boîte d'édition d'un profil local,
  sur mobile comme sur Android TV. Aucun doublon n'est ajouté dans les
  paramètres généraux.
- Elle concerne les profils locaux du compte Xtream actuellement authentifié ;
  elle ne remplace ni ne modifie l'authentification Xtream, ses identifiants ou
  son auto-login.
- Les écrans de sélection et de gestion des profils conservent leurs parcours
  actuels lorsqu'aucun profil valide n'est configuré pour le démarrage
  automatique.

## Parcours utilisateur

### Activer, déplacer ou désactiver l'option

1. L'utilisateur ouvre « Gérer les profils », puis l'édition du profil voulu.
2. Il active ou désactive « Connexion automatique au démarrage ».
3. L'activation est confirmée immédiatement dans l'édition. Si un autre profil
   portait déjà l'option, celui-ci cesse immédiatement de la porter : il ne
   peut jamais y avoir deux profils automatiques.
4. Désactiver l'option sur le profil qui la porte laisse l'application sans
   profil de démarrage automatique.
5. Le nom, l'avatar et les autres actions d'édition existantes restent
   indépendants de ce réglage.

### Démarrer l'application

1. L'application exécute d'abord le flux d'authentification Xtream existant.
2. Après une authentification réussie et une fois les profils locaux connus,
   elle vérifie le choix de démarrage automatique.
3. Si le profil désigné existe toujours, il devient le profil actif puis
   l'utilisateur arrive directement sur l'Accueil. L'écran « Qui regarde ? »
   n'est pas affiché, même si plusieurs profils existent.
4. Sans choix automatique, le comportement existant est conservé : l'Accueil
   est atteint directement lorsqu'il y a au plus un profil, et le sélecteur est
   affiché lorsqu'il y en a plusieurs.

### Changer manuellement de profil

1. Depuis les accès existants, l'utilisateur ouvre le sélecteur de profils et
   choisit un autre profil.
2. Le changement prend effet immédiatement pour la session en cours.
3. Il ne désactive pas et ne déplace pas le choix de démarrage automatique :
   celui-ci ne s'applique qu'à un lancement ultérieur de l'application.

## Règles métier

- Un seul identifiant de profil peut être désigné pour le démarrage automatique
  à un instant donné.
- L'activation sur un nouveau profil remplace atomiquement le profil auparavant
  désigné ; aucun démarrage ne doit pouvoir sélectionner brièvement l'ancien
  profil.
- La suppression du profil désigné retire également son choix de démarrage
  automatique. Le lancement suivant applique alors le parcours standard de
  sélection.
- Le renommage ou le changement d'avatar d'un profil ne modifie pas son statut
  de démarrage automatique.
- La déconnexion Xtream, l'échec d'auto-login Xtream ou l'absence de compte
  authentifié conduisent toujours au flux de connexion existant : aucun profil
  local n'est activé avant la réussite de ce flux.
- Les données déjà séparées par profil (favoris, historique, reprise, notes et
  préférences) restent strictement associées au profil finalement actif.

## Cas limites et gestion des erreurs

- Si le profil mémorisé a été supprimé ou n'est plus présent dans la liste
  chargée, son choix est retiré et l'application affiche le sélecteur normal
  lorsque plusieurs profils restent disponibles.
- Si les profils ne peuvent pas être chargés au démarrage, l'application ne
  force aucun profil et suit le repli existant, sans afficher de message
  technique brut ni créer de profil fictif.
- Si une erreur temporaire survient pendant la vérification du profil
  automatique, le sélecteur standard est affiché pour ce démarrage ; l'utilisateur
  conserve la possibilité de choisir un profil manuellement.
- Si l'utilisateur active l'option puis annule l'édition avant sa validation,
  le choix précédemment enregistré reste inchangé.
- Si un seul profil reste après une suppression, il est utilisé selon le
  comportement existant ; l'option automatique n'introduit aucune étape ou
  confirmation supplémentaire.

## Critères d'acceptation

- [ ] L'édition d'un profil affiche une option activable et désactivable de
  connexion automatique au démarrage, sur mobile et Android TV.
- [ ] L'option n'apparaît pas dans les paramètres généraux.
- [ ] Activer l'option sur un profil retire automatiquement le marquage de tout
  autre profil.
- [ ] Après auto-login ou connexion Xtream réussie, un profil automatique
  existant est activé et l'écran « Qui regarde ? » est contourné.
- [ ] En l'absence de profil automatique valide, les règles actuelles de zéro,
  un ou plusieurs profils restent inchangées.
- [ ] Un changement manuel de profil ne modifie pas le choix automatique du
  prochain démarrage.
- [ ] Supprimer le profil automatique retire ce choix et ne provoque ni crash,
  ni boucle de navigation, ni activation d'un profil inexistant.
- [ ] L'authentification Xtream et les données isolées par profil ne régressent
  pas.

---

# 5. Décisions fonctionnelles actées

1. Le réglage est situé exclusivement dans l'édition d'un profil ; il n'est pas
   dupliqué dans les paramètres généraux.
2. Un seul profil automatique est autorisé. Activer le réglage sur un nouveau
   profil le retire du précédent.
3. Un changement manuel de profil ne désactive pas le réglage et ne change pas
   le profil mémorisé pour un démarrage futur.
4. Un profil automatique absent ou indisponible au lancement redonne la main au
   sélecteur standard, sans erreur technique affichée.

---

# 6. Spécification technique

## Choix de stockage (décision structurante)

Le profil de démarrage automatique est stocké comme **une clé scalaire unique**
`auto_start_profile_id` dans les `SharedPreferences` `profile_prefs` déjà
utilisées par `ProfileManagerImpl`, et **non** comme une colonne booléenne sur
`ProfileEntity`.

Justification :

- La règle « un seul profil automatique » devient vraie *par construction* :
  une clé scalaire ne peut désigner qu'un identifiant. Une colonne booléenne
  imposerait une transaction de démarquage/remarquage et laisserait la porte
  ouverte à deux lignes marquées (crash entre deux écritures, migration
  bâclée) — exactement le « démarrage qui sélectionne brièvement l'ancien
  profil » que la spécification interdit.
- **Aucune migration Room** : `AppDatabase` reste en version 25. La règle
  impérative d'AGENTS.md (toute colonne nouvelle ⇒ `Migration` réelle dans
  `Migrations.kt`) est ainsi évitée, avec le risque de perte de cache/favoris
  qu'elle porte, pour une donnée qui n'est ni catalogue ni donnée de profil.
- La donnée suit exactement le cycle de vie de `active_profile_id`, déjà en
  `SharedPreferences`, et n'est pas sensible (pas de credentials) : le même
  support est cohérent.

Conséquence assumée : la valeur n'est pas jointe en SQL à la liste des profils,
la validité (« le profil désigné existe encore ») est donc vérifiée en mémoire
au démarrage et à la suppression d'un profil. Coût négligeable (la liste des
profils tient en quelques éléments et est déjà chargée à ce moment précis).

## Composants impactés

| Fichier | Nature du changement |
| --- | --- |
| `data/local/storage/ProfileManager.kt` | Interface + impl : nouvelle clé `auto_start_profile_id`, `StateFlow` associé |
| `domain/repository/ProfileRepository.kt` | Nouvelles opérations de lecture/écriture + résolution de démarrage |
| `data/repository/ProfileRepositoryImpl.kt` | Implémentation, nettoyage à la suppression, résolution au démarrage |
| `presentation/profile/ProfileViewModel.kt` | `ProfileUiState.autoStartProfileId`, `setAutoStartProfile()`, contournement du sélecteur |
| `presentation/profile/ProfileManagementScreen.kt` | Bascule dans `ProfileEditDialog` (mobile **et** TV, dialog partagé) |
| `app/src/main/res/values/strings.xml` | Libellé et description de l'option |
| `MainActivity.kt` | Aucun changement de structure : le gate existant consomme le nouveau retour du ViewModel |

Aucun nouveau fichier de production n'est nécessaire : la fonctionnalité se
loge dans les composants existants de la Phase 27.

## Modèles de données

- Aucun changement d'entité Room, aucun changement de DTO, aucun appel réseau.
- `Profile` (domain) **n'est pas** enrichi d'un champ `isAutoStart` : le
  marquage n'est pas une propriété du profil mais un choix applicatif unique.
  L'écran de gestion le compare à `ProfileUiState.autoStartProfileId`. Cela
  évite de recalculer/remapper toute la liste à chaque bascule et garde
  `ProfileEntity.toDomain()` inchangé.
- Valeur sentinelle : `ProfileManager.NO_PROFILE` (`-1`) signifie « aucun
  profil automatique », réutilisée telle quelle.

## API interne

`ProfileManager` (interface, mockable — la classe est déjà une interface pour
contourner le piège Mockito/unboxing documenté dans AGENTS.md) :

```kotlin
val autoStartProfileId: StateFlow<Int>
fun currentAutoStartProfileId(): Int     // nom distinct du getter JVM généré
fun setAutoStartProfileId(id: Int)       // NO_PROFILE pour désactiver
```

Le nom `currentAutoStartProfileId()` est imposé par la règle projet : une
fonction `getAutoStartProfileId()` entrerait en collision de signature avec le
getter généré par la propriété `autoStartProfileId`.

`ProfileRepository` :

```kotlin
val autoStartProfileId: Flow<Int>
fun currentAutoStartProfileId(): Int
suspend fun setAutoStartProfile(id: Int?)          // null = désactivation
suspend fun resolveStartupProfile(): StartupProfileResolution
```

`StartupProfileResolution` (domain, `domain/model/`) :

```kotlin
data class StartupProfileResolution(
    val profiles: List<Profile>,
    val needsSelection: Boolean
)
```

Elle remplace le `Boolean` nu renvoyé aujourd'hui par
`ensureInitializedAndNeedsSelection()` et évite un second aller-retour vers le
repository pour récupérer la liste normalisée.

## Stockage, cache, performances

- Écritures : une seule opération `SharedPreferences.edit()` par bascule.
- Lecture au démarrage : en mémoire (`StateFlow` initialisé depuis les prefs au
  premier accès au singleton), donc aucun coût mesurable ajouté au chemin
  critique déjà occupé par l'auto-login Xtream.
- Aucun impact sur la synchronisation catalogue, WorkManager ou Room.

## Sécurité

- Un identifiant de profil local n'est pas une donnée sensible : pas de
  chiffrement requis (les credentials Xtream restent dans leur stockage
  chiffré dédié, intouché).
- Le profil automatique n'est **jamais** appliqué avant la réussite du flux
  d'authentification : la résolution est appelée depuis le gate de profil, qui
  ne s'exécute que sur `loggedInUser != null`.

## Compatibilité

- Installations existantes : clé absente ⇒ `NO_PROFILE` ⇒ comportement actuel
  strictement conservé (0/1 profil → Accueil, 2+ → sélecteur).
- Downgrade : une version antérieure ignore simplement la clé inconnue.
- Mobile et TV partagent le même dialog d'édition et le même gate : une seule
  implémentation couvre les deux plateformes.

## Risques techniques

1. **Régression du gate de démarrage (`MainActivity`)** — le plus sérieux. Le
   gate actuel enchaîne `showSplash` / `showProfileSelection` sur quatre états
   (`profileGateChecked`, `profileGateResolved`, `profileSelectionNeeded`,
   `loggedInUser`) et son commentaire documente déjà un clignotement corrigé.
   Mitigation : ne pas ajouter d'état ; la résolution automatique est décidée
   **à l'intérieur** de la fonction suspendue déjà appelée, qui renvoie
   simplement `needsSelection = false` — aucune composition intermédiaire
   supplémentaire n'est possible.
2. **Profil automatique supprimé hors du chemin nominal** (suppression depuis
   le gate lui-même). Mitigation : le nettoyage est fait dans
   `ProfileRepositoryImpl.deleteProfile()`, point de passage unique.
3. **Échec de chargement des profils** au démarrage. Mitigation :
   `resolveStartupProfile()` encapsule sa lecture ; toute exception est
   convertie en `needsSelection = true` sans écrire en préférences (le choix
   mémorisé n'est pas détruit par un incident temporaire, conformément à la
   spécification).
4. **Ordre des écritures** : `setActiveProfile()` doit précéder le passage à
   l'Accueil, sinon les écrans scopés par profil (favoris, historique) se
   composent avec l'ancien profil. Mitigation : l'activation est faite dans la
   fonction suspendue, avant son retour.

## Dépendances

Aucune nouvelle dépendance Gradle. Aucune règle ProGuard supplémentaire (pas de
nouvelle interface Retrofit).

---

# 7. Architecture

## Flux de données au démarrage

```
MainActivity (gate profil, inchangé structurellement)
  └─ LaunchedEffect(loggedInUser)          [après auto-login/login Xtream OK]
       └─ ProfileViewModel.ensureInitializedAndNeedsSelection()
            └─ ProfileRepository.resolveStartupProfile()
                 ├─ profileDao.getAll()               (+ création "Profil 1" si vide)
                 ├─ normalisation du profil actif     (comportement Phase 27)
                 ├─ lecture ProfileManager.currentAutoStartProfileId()
                 ├─ si id ∈ profils   → setActiveProfileId(id) ; needsSelection = false
                 ├─ si id ∉ profils   → setAutoStartProfileId(NO_PROFILE) ; repli standard
                 └─ sinon (NO_PROFILE) → needsSelection = profiles.size > 1
```

Le contournement du sélecteur est donc décidé **dans la couche domain/data**,
pas dans l'UI : `MainActivity` continue de ne connaître qu'un booléen. C'est ce
qui garantit qu'aucune composition ne peut afficher « Qui regarde ? » avant que
la question soit tranchée.

## Flux de données à l'édition

```
ProfileEditDialog (bascule locale, non persistée)
  └─ « Enregistrer »
       └─ ProfileViewModel.saveProfile(id, name, avatarId, autoStart)
            ├─ profileRepository.renameProfile / updateAvatar   (existant)
            └─ profileRepository.setAutoStartProfile(id ou null)
                 └─ ProfileManager.setAutoStartProfileId(...)   (écriture unique)
                      └─ StateFlow → ProfileUiState.autoStartProfileId → UI
```

L'état de la bascule vit dans le dialog jusqu'à validation : « Annuler » laisse
le choix mémorisé intact, sans traitement particulier. Le remplacement d'un
profil automatique par un autre est une **écriture unique** de la nouvelle
valeur — il n'existe aucun instant où deux profils sont marqués, ni aucun où
plus aucun ne l'est.

## Responsabilités

- `ProfileManagerImpl` — persistance brute et diffusion réactive des deux
  identifiants (actif, démarrage automatique). Aucune règle métier.
- `ProfileRepositoryImpl` — règles métier : validité du profil désigné,
  nettoyage à la suppression, décision de démarrage. Point unique de vérité.
- `ProfileViewModel` — exposition d'état UI et orchestration des actions
  d'édition. Aucune logique de décision de navigation.
- `MainActivity` — routage splash / sélecteur / Accueil, strictement inchangé
  dans sa structure.
- `ProfileManagementScreen` / `ProfileEditDialog` — présentation et état local
  du formulaire, partagés mobile/TV.

## Décisions techniques actées

1. Stockage en `SharedPreferences` scalaire, pas de colonne Room, pas de
   migration (base maintenue en version 25).
2. `Profile` (domain) reste inchangé ; le marquage est un identifiant unique
   porté par l'état applicatif.
3. La décision « afficher ou non le sélecteur » reste calculée côté repository
   et transite par une seule valeur de retour vers l'UI.
4. Le réglage n'est écrit qu'à la validation du dialog d'édition.
5. Un profil automatique introuvable est nettoyé silencieusement ; une erreur
   de chargement ne nettoie rien et retombe sur le sélecteur.

## Plan de tests (couverture obligatoire)

`ProfileRepositoryImplTest` (fichier existant, à étendre) :

- activer sur un profil B alors que A était désigné ⇒ une seule écriture,
  `currentAutoStartProfileId() == B` ;
- désactiver ⇒ `NO_PROFILE`, `resolveStartupProfile()` retombe sur la règle
  0/1/N profils ;
- profil automatique valide ⇒ profil actif mis à jour **et**
  `needsSelection == false` même avec plusieurs profils ;
- profil automatique supprimé via `deleteProfile()` ⇒ clé nettoyée ;
- identifiant orphelin (profil disparu hors app) ⇒ clé nettoyée,
  `needsSelection == true` s'il reste plusieurs profils ;
- renommage / changement d'avatar ⇒ marquage inchangé.

`ProfileViewModelTest` (à créer si absent) : `setAutoStartProfile()` propage
dans `ProfileUiState`, et un `selectProfile()` manuel ne modifie pas
`autoStartProfileId`.

Conformément à AGENTS.md, aucun test instrumenté ni manuel n'est requis pour
valider la tâche.

---

# 8. Plan de développement

- [x] F31-1 — Ajouter le contrat de profil de démarrage automatique

  Objectif : introduire l'identifiant unique de profil automatique dans le
  stockage de profils existant, les contrats domain et la résolution de
  démarrage, sans toucher aux entités Room ni aux données Xtream.

  Fichiers :
  - `data/local/storage/ProfileManager.kt`
  - `domain/model/StartupProfileResolution.kt` (nouveau)
  - `domain/repository/ProfileRepository.kt`

  Validation : la valeur `NO_PROFILE` désactive le réglage ; les nouveaux noms
  évitent toute collision de getter JVM ; aucun schéma Room, migration ou
  endpoint réseau n'est ajouté.

- [x] F31-2 — Résoudre et nettoyer le profil automatique dans le repository

  Objectif : faire appliquer le profil automatique valide avant l'arrivée sur
  l'Accueil, conserver le parcours 0/1/N profils en l'absence de réglage et
  nettoyer l'identifiant lorsqu'un profil est effectivement supprimé.

  Fichiers :
  - `data/repository/ProfileRepositoryImpl.kt`
  - `test/.../data/repository/ProfileRepositoryImplTest.kt`

  Validation : les tests couvrent le remplacement A → B, la désactivation, le
  profil valide avec plusieurs profils, l'identifiant orphelin, la suppression,
  ainsi que la préservation du choix lors d'une erreur temporaire de lecture.

- [x] F31-3 — Raccorder l'état de profils au gate de démarrage existant

  Objectif : exposer le profil automatique dans l'état UI et faire retourner
  au gate existant la décision finale d'afficher ou non le sélecteur, sans
  créer d'état de navigation intermédiaire ni modifier le flux Xtream.

  Fichiers :
  - `presentation/profile/ProfileViewModel.kt`
  - `test/.../presentation/profile/ProfileViewModelTest.kt` (nouveau)
  - `MainActivity.kt` (vérification du contrat existant ; modification non
    attendue sauf nécessité avérée de compilation)

  Validation : les tests prouvent qu'un profil automatique valide contourne le
  sélecteur, qu'un changement manuel de profil ne modifie pas le réglage et
  que l'état UI se met à jour sans sélectionner de profil avant une
  authentification Xtream réussie.

- [x] F31-4 — Ajouter l'option à l'édition de profil partagée

  Objectif : rendre la bascule visible et modifiable uniquement dans le dialog
  d'édition commun mobile/TV, avec persistance au seul enregistrement et
  annulation sans effet.

  Fichiers :
  - `presentation/profile/ProfileManagementScreen.kt`
  - `app/src/main/res/values/strings.xml`

  Validation : l'option reflète le profil actuellement désigné, l'activation
  passe par le ViewModel, « Annuler » ne change rien, et aucun contrôle n'est
  ajouté à `SettingsScreen`.

- [x] F31-5 — Exécuter la non-régression automatisée des profils

  Objectif : vérifier ensemble les contrats repository/ViewModel et les
  chemins de compilation impactés par le nouveau gate de démarrage.

  Fichiers :
  - `test/.../data/repository/ProfileRepositoryImplTest.kt`
  - `test/.../presentation/profile/ProfileViewModelTest.kt`

  Validation : `./gradlew testDebugUnitTest`, `./gradlew assembleDebug` et
  `./gradlew lintDebug` réussissent ; aucun appareil ni émulateur n'est requis
  pour ce critère automatisé.

---

# 9. Review

Date : 2026-08-10

Status : RESOLVED

## Périmètre relu

- `data/local/storage/ProfileManager.kt`
- `domain/model/StartupProfileResolution.kt`
- `domain/repository/ProfileRepository.kt`
- `data/repository/ProfileRepositoryImpl.kt`
- `presentation/profile/ProfileViewModel.kt`
- `presentation/profile/ProfileManagementScreen.kt`
- gate de profils existant dans `MainActivity.kt`
- `app/src/main/res/values/strings.xml`
- `app/src/test/java/com/cstv/app/data/repository/ProfileRepositoryImplTest.kt`
- `app/src/test/java/com/cstv/app/presentation/profile/ProfileViewModelTest.kt`

## Critique

Aucun constat.

## Majeur

### F31-R1 — La couverture annoncée comme obligatoire n'exerce pas plusieurs contrats réactifs et métier

**Description :** le plan de tests exige notamment que `setAutoStartProfile()`
se propage dans `ProfileUiState`, que le remplacement A vers B soit observable
comme un état unique, et que renommage/changement d'avatar préservent le choix.
Or `ProfileViewModelTest.test_setAutoStartProfile_delegatesToRepository` vérifie
seulement l'appel au repository : son `autoStartProfileId` est un `flowOf(-1)`
immuable et aucune assertion ne prouve la mise à jour réactive de l'état UI.
`ProfileRepositoryImplTest` vérifie une écriture vers B sur un mock sans état,
mais jamais la valeur courante après cette écriture ; il ne couvre pas non plus
la préservation lors d'un renommage/changement d'avatar. Enfin, le test nommé
`followsExisting0or1orNRule` n'exerce que le cas N (deux profils).

**Impact :** une régression supprimant le collecteur de
`autoStartProfileId`, altérant l'état après une édition, ou changeant le repli à
un profil pourrait laisser toute la suite verte alors que l'option affichée ou
le gate de démarrage ne respecte plus le contrat F31. Le succès Gradle ne
constitue donc pas la preuve de la couverture obligatoire revendiquée par le
ticket.

**Correction attendue :** ajouter un fake ou un `MutableStateFlow` contrôlé qui
permet de vérifier l'état courant après A vers B et la propagation jusqu'à
`ProfileUiState`; couvrir explicitement le repli sans profil automatique avec
un seul profil et plusieurs profils, ainsi que l'absence d'écriture de la clé
lors d'un renommage ou d'un changement d'avatar. Conserver les cas déjà présents
pour la suppression, l'identifiant orphelin et l'erreur temporaire.

## Mineur

Aucun constat supplémentaire.

## Vérifications effectuées

- Le stockage est bien une clé scalaire unique `auto_start_profile_id` dans
  `profile_prefs`; aucune entité Room, migration ou API réseau n'est ajoutée.
- La résolution applique un identifiant valide avant de rendre le gate et
  nettoie silencieusement un identifiant orphelin; une exception de chargement
  conserve la préférence et retombe sur le sélecteur.
- `MainActivity` garde sa structure de gate et n'exécute la résolution qu'après
  une authentification Xtream réussie.
- Le réglage est local au dialog partagé mobile/TV, n'est écrit qu'au clic sur
  « Enregistrer » et n'apparaît pas dans `SettingsScreen`.
- `./gradlew --no-daemon --max-workers=1 testDebugUnitTest assembleDebug lintDebug`
  : succès (`BUILD SUCCESSFUL`).
- Réexécution forcée des deux classes de tests F31 : succès (`BUILD SUCCESSFUL`).
- `git diff --check` : aucun défaut d'espaces.

## Limites de la review

La présence et la manipulation réelle du `Switch` dans le dialog Compose ne
sont pas exercées par une infrastructure UI instrumentée. Cette limite ne
remplace pas F31-R1, qui concerne des contrats repository/ViewModel entièrement
testables sur la JVM.

## Corrections demandées

- Corriger F31-R1 avant l'étape 8.

## Corrections appliquées à l'étape 7

### F31-R1 — Résolu

- `ProfileRepositoryImplTest` : ajout d'un `FakeProfileManager` (état réel via
  `MutableStateFlow`, pas un mock d'interactions) prouvant que le remplacement
  A → B laisse `currentAutoStartProfileId() == B` comme unique valeur
  observable, et que la désactivation retombe sur `NO_PROFILE`. Ajout des cas
  0 et 1 profil manquants à la règle 0/1/N (seul le cas N était couvert).
  Ajout de deux tests prouvant qu'un renommage ou un changement d'avatar
  n'appelle jamais `setAutoStartProfileId`.
- `ProfileViewModelTest` : remplacement du `flowOf(-1)` immuable par un
  `MutableStateFlow` contrôlé pour `autoStartProfileId`. Deux nouveaux tests
  prouvent la propagation réactive jusqu'à `ProfileUiState` lors d'un
  remplacement A(5) → B(6) et d'une désactivation. Deux tests supplémentaires
  prouvent qu'un renommage ou un changement d'avatar laissent
  `autoStartProfileId` inchangé dans l'état UI.
- `./gradlew --no-daemon --max-workers=1 testDebugUnitTest assembleDebug lintDebug`
  : succès (`BUILD SUCCESSFUL`).
- `git diff --check` : aucun défaut d'espaces.
