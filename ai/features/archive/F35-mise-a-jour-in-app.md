# F35 - Détection et installation des mises à jour in-app

## Informations générales

Status:
RELEASE

Created:
2026-08-12

---

# 1. Description

Permettre à l'application Android / Android TV de détecter qu'une version plus
récente est disponible, d'en informer l'utilisateur, puis de télécharger et de
lancer l'installation du nouvel APK sans quitter l'application.

La source de vérité des versions publiées est la page **GitHub Releases** du
dépôt `scilone/cstv`, désormais **public**. L'application interroge directement
l'API publique GitHub via une URL fixe compilée dans l'app — aucun secret,
aucun jeton, aucun passage par le backend CSTV.

Le périmètre couvre le cycle complet : vérification, notification, téléchargement,
vérification d'intégrité, déclenchement de l'installation système.

## Objectifs

- détecter automatiquement la disponibilité d'une version supérieure à celle
  installée ;
- proposer la mise à jour de façon non bloquante, sur mobile et sur Android TV ;
- télécharger l'APK signé attaché à la Release GitHub ;
- déclencher l'installation via le `PackageInstaller` système ;
- guider l'utilisateur vers l'autorisation « installer des applications
  inconnues » quand elle manque ;
- ne jamais casser l'usage de l'app si la vérification échoue (repli silencieux).

---

# 2. Contexte

## Pourquoi cet élément existe

L'application n'est **pas distribuée sur le Play Store** et ce choix est
définitif. La livraison passe par `scripts/release-local.sh`, qui compile un APK
signé, pose le tag SemVer et crée une Release GitHub avec l'APK attaché
(cf. `AGENTS.md`, section « Pour livrer une nouvelle version »).

Conséquence actuelle : **aucun canal de mise à jour**. L'utilisateur doit savoir
qu'une version existe, aller la chercher sur GitHub, la transférer sur
l'appareil et l'installer à la main. Sur Android TV, cette manipulation est
particulièrement pénible (pas de navigateur confortable, pas de gestionnaire de
fichiers standard, saisie à la télécommande).

Résultat : le parc d'appareils reste bloqué sur d'anciennes versions, les
correctifs livrés ne parviennent pas aux utilisateurs, et les retours de bug
portent sur du code déjà corrigé.

## Ce que le projet apporte déjà

- `versionCode` **dérivé mécaniquement** du SemVer
  (`major*10_000 + minor*100 + patch`, ex. `v1.77.6` → `17_706`) : une
  comparaison numérique entre le tag distant et `BuildConfig.VERSION_CODE`
  suffit, sans manifeste de version à maintenir.
- Tous les APK sont signés avec le **même keystore** (`app-release.jks`), donc
  la mise à jour par-dessus l'installation existante est acceptée par Android.
- Le script de release attache déjà l'APK et génère les notes de version : la
  Release GitHub contient tout ce dont l'app a besoin.
- Retrofit / OkHttp, Hilt, WorkManager et Compose (mobile + TV) sont en place.

## Décision d'architecture actée par le PO

Le dépôt a été rendu **public** pour cette fonctionnalité. L'URL de l'API
GitHub est donc **codée en dur dans l'application** :

```
https://api.github.com/repos/scilone/cstv/releases/latest
```

Motivation : supprimer la surface d'attaque. Un dépôt privé aurait imposé soit
un jeton GitHub embarqué dans l'APK (extractible par simple décompilation), soit
un proxy authentifié côté backend CSTV (nouvelle route, nouveau stockage,
nouvelle donnée en base à maintenir et à sécuriser). Avec un dépôt public et une
URL fixe, il n'y a **ni secret à protéger, ni donnée en base, ni dépendance au
backend CSTV** — la vérification de version fonctionne même si le backend est
indisponible.

Contrepartie assumée : le code source et les APK sont publiquement accessibles.
Cela reste sans impact sur les secrets du projet, qui n'ont jamais été versionnés
(`local.properties`, `keystore.properties`, `app-release.jks`).

---

# 3. Spécification fonctionnelle

## 3.1 User stories

- **US1** — En tant qu'utilisateur, au lancement de l'application, je suis
  informé qu'une version plus récente existe, sans avoir à la chercher.
- **US2** — En tant qu'utilisateur, je peux lancer la mise à jour depuis cette
  invitation et l'installer sans quitter l'application.
- **US3** — En tant qu'utilisateur, je peux repousser l'invitation à demain, ou
  décider de ne plus jamais être relancé pour cette version précise.
- **US4** — En tant qu'utilisateur, je ne suis relancé qu'une fois par jour au
  maximum, sur chaque appareil.
- **US5** — En tant qu'utilisateur, je peux vérifier manuellement la présence
  d'une mise à jour depuis les Paramètres, à tout moment.
- **US6** — En tant que PO, je peux imposer une mise à jour bloquante lorsque je
  publie une version majeure introduisant une rupture de compatibilité.

## 3.2 Parcours utilisateur

### A. Détection automatique au démarrage (cas nominal)

1. L'utilisateur lance l'application (démarrage à froid).
2. L'application atteint l'écran d'Accueil (session CSTV et connexion Xtream
   déjà résolues).
3. En tâche de fond, l'application interroge la Release GitHub la plus récente.
4. Une version supérieure existe, aucune règle de silence n'est active → une
   boîte de dialogue s'affiche par-dessus l'Accueil :

   > **Mise à jour disponible**
   > La version 1.78.0 est disponible. Vous utilisez la version 1.77.6.
   >
   > `[ Mettre à jour ]` `[ Plus tard ]` `[ Ignorer cette version ]`

5. L'utilisateur choisit **Mettre à jour** → le dialogue passe en état
   « téléchargement » avec une progression et un bouton **Annuler**.
6. Téléchargement terminé → la boîte de dialogue d'installation **du système**
   s'ouvre. L'application n'a plus la main.
7. L'utilisateur confirme → Android remplace l'application et la termine.
8. Au démarrage suivant, l'APK résiduel est supprimé du cache.

### B. Recherche manuelle depuis les Paramètres

1. L'utilisateur ouvre **Paramètres** → entrée **Rechercher une mise à jour**,
   avec en sous-titre la version installée (« Version actuelle 1.77.6 »).
2. Il valide → indicateur de chargement sur l'entrée.
3. Deux issues :
   - une version plus récente existe → même dialogue qu'en A, **y compris si la
     version avait été ignorée ou repoussée** ;
   - aucune version plus récente → message « Votre application est à jour. ».
4. En cas d'échec réseau, un message d'erreur explicite est affiché — la
   recherche manuelle, contrairement à l'automatique, **n'échoue jamais en
   silence**.

### C. Mise à jour obligatoire (version majeure)

1. La version distante est une **version majeure supérieure** à la version
   installée (ex. installée `1.77.6`, distante `2.0.0`).
2. Le dialogue s'affiche dans sa variante bloquante :

   > **Mise à jour requise**
   > Cette version de l'application n'est plus compatible. Installez la
   > version 2.0.0 pour continuer.
   >
   > `[ Mettre à jour ]` `[ Quitter ]`

3. Pas de **Plus tard**, pas d'**Ignorer cette version**, retour arrière
   désactivé, dialogue non annulable par clic extérieur. La seule sortie autre
   que la mise à jour est la fermeture de l'application.

### D. Autorisation « sources inconnues » manquante

1. L'utilisateur choisit **Mettre à jour**, mais l'application n'a pas
   l'autorisation d'installer des applications.
2. Message explicatif + bouton **Ouvrir les réglages**, qui envoie vers l'écran
   système d'autorisation.
3. Au retour dans l'application, si l'autorisation est accordée, le processus
   reprend automatiquement (téléchargement puis installation).
4. Si l'écran système est introuvable (certaines box Android TV), repli :
   message invitant à installer manuellement depuis
   `github.com/scilone/cstv/releases`.

## 3.3 Règles métier

| # | Règle |
|---|---|
| **RG1** | La vérification automatique a lieu **au démarrage à froid uniquement**, jamais sur simple retour au premier plan, jamais pendant la lecture vidéo. |
| **RG2** | La vérification automatique est limitée à **une fois par 24 h et par appareil**. L'horodatage est stocké localement, **non synchronisé** avec le compte CSTV (F34) : chaque appareil a son propre rythme. |
| **RG3** | Une version est « plus récente » si le `versionCode` dérivé de son tag est **strictement supérieur** à `BuildConfig.VERSION_CODE`. Les rétrogradations sont ignorées. |
| **RG4** | **Plus tard** masque l'invitation jusqu'au **lendemain** (24 h). Elle réapparaîtra ensuite pour la même version. |
| **RG5** | **Ignorer cette version** masque définitivement l'invitation **pour ce seul `versionCode`**. Une version ultérieure déclenchera de nouveau l'invitation. |
| **RG6** | La mise à jour est **obligatoire** — et seulement dans ce cas — lorsque le numéro **MAJEUR** distant est supérieur au numéro majeur installé (`versionCode / 10_000`). Par définition SemVer, un incrément majeur signale une rupture de compatibilité. Aucun marqueur supplémentaire n'est publié, aucune modification de `scripts/release-local.sh` n'est requise. |
| **RG7** | Une mise à jour obligatoire **ignore** RG2, RG4 et RG5 : elle s'affiche à chaque démarrage tant qu'elle n'est pas installée. |
| **RG8** | La **recherche manuelle** ignore RG2, RG4 et RG5, et ne modifie pas l'horodatage de la vérification automatique. |
| **RG9** | Seules les Releases **publiées** sont considérées : les pré-releases et les brouillons sont exclus (comportement natif de `releases/latest`). |
| **RG10** | Le téléchargement est **toujours autorisé**, y compris sur connexion mesurée. Aucune confirmation, aucun blocage lié au type de réseau. |
| **RG11** | **Aucune note de version n'est affichée.** Le dialogue annonce la disponibilité et invite à mettre à jour, rien de plus. |
| **RG12** | L'APK est téléchargé dans le **cache de l'application** (`cacheDir`), jamais dans un stockage partagé. |
| **RG13** | L'APK est **supprimé après installation réussie**. Le remplacement de l'application terminant le processus, la purge est effectuée **au démarrage suivant** : tout APK en cache dont le `versionCode` est inférieur ou égal à celui installé est supprimé. |
| **RG14** | L'intégrité repose sur le **transport HTTPS** et sur la **vérification de signature par Android** (même keystore de production). Aucun contrôle SHA-256 applicatif. |
| **RG15** | Une vérification automatique en échec (réseau, quota, réponse invalide) est **silencieuse** : aucun message, aucune trace visible, et l'horodatage n'est pas mis à jour afin de réessayer au démarrage suivant. |
| **RG16** | L'installation silencieuse n'existe pas : la confirmation système est toujours requise. Le parcours s'arrête donc systématiquement sur un écran hors du contrôle de l'application. |

## 3.4 États du dialogue de mise à jour

| État | Contenu | Actions |
|---|---|---|
| `Disponible` | Version distante + version installée | Mettre à jour / Plus tard / Ignorer cette version |
| `DisponibleObligatoire` | Message de rupture de compatibilité | Mettre à jour / Quitter |
| `AutorisationRequise` | Explication de l'autorisation d'installation | Ouvrir les réglages / Annuler |
| `AutorisationIndisponible` | Repli : installation manuelle + URL | Fermer |
| `Telechargement` | Progression en pourcentage | Annuler |
| `Erreur` | Cause de l'échec | Réessayer / Fermer |
| `PretAInstaller` | Transitoire, avant l'ouverture du dialogue système | — |

Sur une mise à jour obligatoire, **Annuler** et **Fermer** sont remplacés par
**Quitter**, qui ferme l'application.

## 3.5 Présentation mobile / TV

Composable **unique et partagé**, paramétré par `isTv: Boolean` — cohérent avec
le reste du projet (`SettingsScreen`, `CstvGateScreen`, `SplashScreen` suivent
déjà ce modèle). La logique, les états et les textes sont communs ; seules
divergent la mise en page et l'ergonomie :

- **TV** : composants `tv-material`, focus initial sur **Mettre à jour**,
  boutons en ligne, tailles et espacements agrandis, navigation directionnelle
  gauche/droite. `BackHandler` neutralisé sur la variante obligatoire.
- **Mobile** : dialogue Material standard, boutons empilés si le libellé
  déborde, fermeture par clic extérieur autorisée **sauf** en mode obligatoire.

Ce niveau de divergence tient dans une seule fonction. Si l'implémentation
révèle deux arbres de composition entièrement disjoints, la séparation en deux
composables reste autorisée — l'unification ne doit pas dégrader l'ergonomie
télécommande.

## 3.6 Textes (à ajouter dans `strings.xml`)

| Clé proposée | Valeur |
|---|---|
| `update_available_title` | Mise à jour disponible |
| `update_available_message` | La version %1$s est disponible. Vous utilisez la version %2$s. |
| `update_action_install` | Mettre à jour |
| `update_action_later` | Plus tard |
| `update_action_ignore` | Ignorer cette version |
| `update_required_title` | Mise à jour requise |
| `update_required_message` | Cette version de l'application n'est plus compatible. Installez la version %1$s pour continuer. |
| `update_action_quit` | Quitter |
| `update_downloading` | Téléchargement… %1$d %% |
| `update_action_cancel` | Annuler |
| `update_error_download` | Le téléchargement a échoué. Réessayez plus tard. |
| `update_error_check` | Impossible de vérifier les mises à jour. Vérifiez votre connexion. |
| `update_permission_message` | Autorisez CSTV à installer des applications pour poursuivre la mise à jour. |
| `update_permission_action` | Ouvrir les réglages |
| `update_permission_unavailable` | Cet appareil ne permet pas d'accorder cette autorisation. Installez la mise à jour manuellement depuis github.com/scilone/cstv/releases |
| `settings_check_update` | Rechercher une mise à jour |
| `settings_check_update_subtitle` | Version actuelle %1$s |
| `settings_check_update_up_to_date` | Votre application est à jour. |

## 3.7 Critères d'acceptation

Tous vérifiables par tests unitaires (`./gradlew testDebugUnitTest`) : la
décision d'affichage, la comparaison de versions et le parsing de la réponse
GitHub sont isolés dans des composants sans dépendance Android.

- [ ] **CA1** — Tag `v1.78.0` face à `versionCode` 17 706 → mise à jour proposée.
- [ ] **CA2** — Tag `v1.77.6` (identique) ou `v1.77.5` (inférieur) → aucune proposition.
- [ ] **CA3** — Tag `v2.0.0` face à `1.77.6` → proposition **obligatoire**.
- [ ] **CA4** — Tag `v1.78.0` face à `1.77.6` → proposition **non** obligatoire.
- [ ] **CA5** — Vérification réussie il y a moins de 24 h → aucune vérification automatique.
- [ ] **CA6** — Vérification réussie il y a plus de 24 h → vérification effectuée.
- [ ] **CA7** — « Plus tard » → aucune proposition avant 24 h, proposition rétablie après.
- [ ] **CA8** — « Ignorer cette version » sur 1.78.0 → plus jamais proposée ; 1.79.0 l'est.
- [ ] **CA9** — Recherche manuelle → propose même si repoussée, ignorée, ou déjà vérifiée dans les 24 h.
- [ ] **CA10** — Recherche manuelle sans version plus récente → message « à jour ».
- [ ] **CA11** — Recherche manuelle en échec réseau → message d'erreur affiché.
- [ ] **CA12** — Vérification automatique en échec → aucun message, horodatage inchangé.
- [ ] **CA13** — Mise à jour obligatoire → ni « Plus tard » ni « Ignorer », et réaffichage à chaque démarrage malgré les règles de silence.
- [ ] **CA14** — Réponse sans asset `.apk` → traitée comme « aucune mise à jour ».
- [ ] **CA15** — Tag non conforme à `vX.Y.Z` → traité comme « aucune mise à jour ».
- [ ] **CA16** — Purge au démarrage : un APK en cache de `versionCode` ≤ version installée est supprimé ; un APK de version supérieure est conservé.

## 3.8 Cas limites

- **Quota GitHub dépassé** (HTTP 403/429) → traité comme un échec silencieux
  (RG15). Avec une vérification quotidienne par appareil, le seuil de 60
  requêtes/heure par IP n'est pas atteignable en usage normal.
- **Aucun réseau** → échec silencieux, aucun impact sur le démarrage.
- **Réponse GitHub inattendue** (JSON modifié, champ absent, plusieurs assets)
  → premier asset dont le nom se termine par `.apk` ; à défaut, aucune mise à
  jour.
- **Téléchargement interrompu** (réseau coupé, application quittée) → aucune
  reprise ; nouvelle tentative au prochain déclenchement. Le fichier partiel est
  écrasé.
- **APK déjà présent en cache** pour la version cible, de taille identique à
  celle annoncée par l'API → réutilisé sans nouveau téléchargement.
- **Cache purgé par le système** entre le téléchargement et l'installation →
  échec d'installation → retour à l'état `Erreur`, avec **Réessayer**.
- **Utilisateur annulant le dialogue système d'installation** → retour dans
  l'application, aucune erreur affichée, APK conservé pour une nouvelle
  tentative.
- **Mise à jour installée par un autre moyen** entre-temps → la comparaison
  reposant toujours sur `BuildConfig.VERSION_CODE`, rien n'est proposé.
- **Signature incompatible** (APK d'un autre keystore) → Android refuse
  l'installation. Comportement attendu, non contourné ; l'utilisateur voit
  l'erreur système.
- **Appareil sous API 26** → pas d'autorisation par application ; si
  l'installation échoue, repli identique à `AutorisationIndisponible`.
- **Utilisateur non authentifié** (écran CSTV ou Xtream) → aucune vérification :
  l'invitation n'apparaît jamais par-dessus le splash, la connexion ou le
  lecteur vidéo.
- **Mise à jour obligatoire sur un appareil incapable d'installer** (box Android
  TV sans écran « sources inconnues ») → impasse assumée : l'application reste
  bloquée sur `AutorisationIndisponible`, avec l'URL d'installation manuelle. La
  sortie passe par une installation hors application, puis un relancement. Ce
  cas ne peut survenir que sur une version **majeure**, donc exceptionnellement.
- **Contenu hors-ligne et mise à jour obligatoire** → le blocage rend les
  téléchargements locaux temporairement inaccessibles. Conséquence acceptée :
  une version majeure signale une rupture de compatibilité, l'usage dégradé
  n'est pas souhaitable non plus.

## 3.9 Gestion des erreurs

| Situation | Vérification automatique | Recherche manuelle |
|---|---|---|
| Réseau indisponible | Silencieux | `update_error_check` |
| HTTP 403 / 429 / 5xx | Silencieux | `update_error_check` |
| Réponse illisible | Silencieux | `update_error_check` |
| Aucun asset APK | Silencieux | « Votre application est à jour. » |
| Échec du téléchargement | `update_error_download` + Réessayer | idem |
| Autorisation refusée | `AutorisationRequise` | idem |
| Écran d'autorisation introuvable | `AutorisationIndisponible` | idem |

Aucune erreur de ce ticket ne doit empêcher l'utilisation de l'application, à
la seule exception de la mise à jour obligatoire (RG6), qui est bloquante par
conception.

---

# 4. Spécification technique

## 4.1 Périmètre technique

La fonctionnalité reste entièrement dans l'application Android. Elle ne crée
ni route dans le backend CSTV, ni table Room, ni `Worker`, ni notification
système, et ne modifie pas `scripts/release-local.sh`. Elle s'appuie sur les
dépendances déjà présentes (Retrofit, OkHttp, Gson, Hilt, coroutines et
Compose) ainsi que sur les API Android du framework.

La vérification et le téléchargement utilisent un client HTTP GitHub dédié.
Il est impératif de ne réutiliser ni le client Xtream — qui réécrit les URL et
peut contenir des identifiants — ni le client CSTV — qui ajoute un jeton
`Authorization`. Aucun secret ne doit être envoyé à GitHub.

Le cycle de mise à jour est porté par un état applicatif partagé : le contrôle
automatique depuis l'Accueil et le contrôle manuel depuis les Paramètres
doivent converger vers la même machine d'états, le même téléchargement et le
même dialogue. Deux téléchargements concurrents d'une même version sont
interdits.

## 4.2 Composants impactés

### Fichiers existants à modifier à l'étape 5

| Fichier | Évolution prévue |
|---|---|
| `app/src/main/AndroidManifest.xml` | Déclarer `REQUEST_INSTALL_PACKAGES` et le receiver privé du résultat `PackageInstaller`. |
| `app/src/main/java/com/cstv/app/IptvApplication.kt` | Lancer hors thread principal la purge ciblée de `cacheDir/app_updates` au démarrage. |
| `app/src/main/java/com/cstv/app/MainActivity.kt` | Créer le `AppUpdateViewModel` partagé, déclencher une seule vérification automatique après résolution des gates CSTV/Xtream/profil et héberger le dialogue global. |
| `app/src/main/java/com/cstv/app/presentation/navigation/NavGraph.kt` | Transmettre l'état et les actions de recherche manuelle à la destination Paramètres, sans créer un second ViewModel. |
| `app/src/main/java/com/cstv/app/presentation/settings/SettingsScreen.kt` | Ajouter l'entrée « Rechercher une mise à jour » sur mobile et TV, avec version installée et chargement. |
| `app/src/main/java/com/cstv/app/di/AppModule.kt` | Fournir le client/Retrofit GitHub nommés, l'API et le binding du repository. |
| `app/src/main/res/values/strings.xml` | Ajouter les textes définis en §3.6 et les libellés d'erreur/état complémentaires. |
| `app/proguard-rules.pro` | Conserver la nouvelle interface Retrofit GitHub en release, conformément à `AGENTS.md`. |

`app/build.gradle.kts` ne nécessite pas de nouvelle bibliothèque. Il ne sera
modifié que par le futur processus normal de release pour incrémenter la
version, hors implémentation fonctionnelle de F35.

### Nouveaux composants prévus

| Couche | Fichier proposé | Responsabilité |
|---|---|---|
| Domain | `domain/model/AppUpdate.kt` | `SemanticVersion`, `AppUpdateRelease`, origine du contrôle et décisions pures. |
| Domain | `domain/repository/AppUpdateRepository.kt` | Contrat de lecture de la dernière Release et de téléchargement en cache. |
| Domain | `domain/usecase/CheckForAppUpdateUseCase.kt` | Appliquer SemVer, délai 24 h, report, version ignorée et caractère obligatoire. |
| Data remote | `data/remote/api/GithubReleaseApiService.kt` | Appels Retrofit vers `releases/latest` et vers l'asset APK en streaming. |
| Data remote | `data/remote/dto/GithubReleaseDto.kt` | DTO minimaux `tag_name`, `draft`, `prerelease`, `assets.name`, `assets.browser_download_url`, `assets.size`. |
| Data repository | `data/repository/AppUpdateRepositoryImpl.kt` | Mapper la réponse GitHub, valider l'asset et écrire le téléchargement atomiquement. |
| Data local | `data/local/storage/AppUpdatePreferences.kt` | Stockage local non synchronisé des horodatages, report, version ignorée et dernière Release connue. |
| Data update | `data/update/UpdateApkStore.kt` | Nommage, inspection, réutilisation et purge des APK/partiels dans le cache. |
| Data update | `data/update/AndroidPackageInstaller.kt` | Autorisation sources inconnues, session `PackageInstaller` et remontée des résultats. |
| Data update | `data/update/UpdateInstallResultReceiver.kt` | Recevoir le statut de la session et ouvrir l'intent système de confirmation. |
| Presentation | `presentation/update/AppUpdateViewModel.kt` | Machine d'états partagée et sérialisation des actions utilisateur. |
| Presentation | `presentation/update/AppUpdateDialog.kt` | Dialogue commun mobile/TV et gestion du retour des réglages d'autorisation. |

Les noms définitifs pourront être ajustés à l'étape 4 si un fichier existant
offre un meilleur point d'intégration, sans changer les responsabilités
ci-dessus.

## 4.3 Modèles et contrats

### Version

`SemanticVersion` est un objet Kotlin pur :

```text
major: Int
minor: Int
patch: Int
displayName: String       // ex. 1.78.0
versionCode: Int          // major*10_000 + minor*100 + patch
```

Le parseur n'accepte que `vX.Y.Z`, avec trois composantes numériques et
`minor`/`patch` comprises entre 0 et 99. Le calcul est vérifié contre les
débordements. Toute autre valeur produit un résultat contrôlé, jamais une
exception jusque dans la présentation.

`AppUpdateRelease` contient uniquement : version distante, URL HTTPS de
l'asset, nom de l'asset et taille annoncée. Le corps Markdown de la Release
n'entre volontairement dans aucun modèle domain.

### Résultats de vérification

Le repository distingue :

- `ReleaseFound` : réponse exploitable avec tag et APK ;
- `NoUsableRelease` : JSON lisible mais tag non conforme ou aucun asset APK
  (CA14/CA15, rendu comme « à jour » en manuel) ;
- `Failure` : absence réseau, HTTP non réussi, timeout ou JSON illisible.

Le use case transforme ensuite ce résultat en `NoCheckNeeded`, `UpToDate`,
`OptionalUpdate`, `RequiredUpdate` ou `CheckFailed`. L'origine `AUTOMATIC` ou
`MANUAL` fait partie de l'entrée pour appliquer RG2/RG4/RG5/RG8 sans dupliquer
la logique dans le ViewModel.

### États de présentation

`AppUpdateUiState` est un état fini, sans booléens contradictoires :

```text
Idle
Checking(origin)
Available(release, required)
PermissionRequired(release, required)
PermissionUnavailable(release, required)
Downloading(release, required, percent)
ReadyToInstall(release, required)
Error(release?, required, kind)
UpToDate                         // retour manuel transitoire
```

Une mise à jour obligatoire conserve toujours `required = true` pendant les
états permission, téléchargement et erreur : le dialogue peut ainsi remplacer
Annuler/Fermer par Quitter sans déduire cette règle depuis le texte affiché.

## 4.4 API GitHub et téléchargement

Le Retrofit GitHub utilise la base fixe `https://api.github.com/` et l'appel :

```text
GET repos/scilone/cstv/releases/latest
Accept: application/vnd.github+json
```

L'endpoint officiel renvoie la dernière Release publiée, hors brouillons et
pré-releases, et accepte les lectures anonymes d'un dépôt public. Les champs
`draft` et `prerelease` restent néanmoins contrôlés défensivement.

Le premier asset dont le nom se termine par `.apk`, sans tenir compte de la
casse, est retenu. Son URL initiale doit respecter simultanément : schéma
`https`, hôte `github.com`, aucun `userinfo`, et chemin sous
`/scilone/cstv/releases/download/`. Les redirections HTTPS normales de GitHub
vers son hébergement d'assets restent autorisées par OkHttp. Le client n'ajoute
aucun cookie, token CSTV, identifiant Xtream ou logger de corps.

Le téléchargement Retrofit est annoté `@Streaming` afin de ne jamais charger
l'APK en mémoire. Il écrit par blocs dans
`cacheDir/app_updates/cstv-<versionCode>.apk.part`, publie au maximum une mise à
jour d'état par point de pourcentage, puis renomme atomiquement le fichier en
`.apk`. L'annulation de la coroutine annule l'appel OkHttp et supprime le
fichier partiel.

Un APK final existant n'est réutilisé que si :

1. sa taille correspond à `assets[].size` ;
2. `PackageManager.getPackageArchiveInfo()` le reconnaît ;
3. son package vaut `BuildConfig.APPLICATION_ID` ;
4. son `versionCode` vaut la version distante attendue.

La signature reste vérifiée par Android au moment de l'installation (RG14) ;
ce contrôle de métadonnées évite seulement de réutiliser un fichier incomplet
ou ne correspondant pas à la Release.

## 4.5 Persistance locale et règle des 24 heures

`AppUpdatePreferences` utilise un fichier `SharedPreferences`
`app_update_prefs`, séparé de `SettingsManager` et de toute donnée de profil.
Les valeurs ne sont ni secrètes ni synchronisées par F34 ; aucun chiffrement et
aucune migration Room ne sont nécessaires.

Clés logiques :

- dernière vérification automatique réussie ;
- `versionCode` reporté et échéance du report ;
- `versionCode` ignoré ;
- dernière Release supérieure connue (nom, code, URL, nom d'asset, taille).

Une réponse HTTP/JSON exploitable, y compris `NoUsableRelease`, met à jour
l'horodatage automatique. Un échec réseau/HTTP/parsing ne le met jamais à jour.
La recherche manuelle ne le modifie dans aucun cas.

La dernière Release connue permet de réafficher une mise à jour obligatoire à
chaque démarrage sans nouvel appel réseau. RG7 s'applique donc dès que cette
majeure a été découverte. Une majeure publiée juste après une vérification ne
peut matériellement être connue avant le prochain appel autorisé par RG2 : le
délai maximal de découverte reste 24 h. Refaire un appel à chaque démarrage
pour l'éviter violerait la décision PO « 1× par jour et par appareil ».

Un horodatage futur incohérent (horloge appareil reculée) est considéré comme
expiré, afin de ne pas désactiver indéfiniment les contrôles. Une Release connue
dont le code devient inférieur ou égal à `BuildConfig.VERSION_CODE` est
effacée.

Fermer le dialogue non obligatoire par Retour ou clic extérieur est traité
comme **Plus tard** : toutes les sorties implicites respectent ainsi RG4 au lieu
de créer un quatrième comportement non spécifié.

## 4.6 Installation Android

Le manifeste ajoute :

```xml
<uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES" />
```

À partir d'API 26, `AndroidPackageInstaller` appelle
`PackageManager.canRequestPackageInstalls()` avant le téléchargement. Si le
résultat est faux, le dialogue passe à `PermissionRequired` et ouvre
`Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES` avec
`package:com.cstv.app`. Au retour, l'autorisation est relue ; si elle est
accordée, le téléchargement reprend automatiquement. L'intent est résolu avant
lancement ; son absence produit `PermissionUnavailable`.

Sous API 21–25, il n'existe pas d'autorisation par application : le flux tente
directement l'installation. Un refus lié au réglage global « sources inconnues »
est converti en repli manuel, conformément au cas limite de §3.8, sans dépendre
d'une API sécurisée obsolète pour lire ce réglage.

Après téléchargement, `AndroidPackageInstaller` :

1. crée une `PackageInstaller.Session` en `MODE_FULL_INSTALL` pour
   `BuildConfig.APPLICATION_ID` ;
2. copie l'APK en streaming via `Session.openWrite()`, puis appelle `fsync()` ;
3. exige explicitement une action utilisateur sur API 31+ ;
4. commite la session avec un `IntentSender` mutable (requis avec target 35)
   ciblant `UpdateInstallResultReceiver` ;
5. traite `STATUS_PENDING_USER_ACTION` en ouvrant l'intent de confirmation
   fourni par Android ; `STATUS_SUCCESS` termine le flux, les statuts d'échec
   deviennent un état contrôlé.

Le receiver est `android:exported="false"` et n'accepte que le résultat du
`PendingIntent` créé par l'application. Aucune installation silencieuse n'est
demandée. L'annulation du dialogue système (`STATUS_FAILURE_ABORTED`) ne montre
pas d'erreur : elle revient à l'état disponible et conserve l'APK. Les erreurs
de signature, stockage, conflit ou incompatibilité sont affichées sous une
forme générique, sans exposer les messages système bruts.

Cette approche n'utilise ni `Intent.ACTION_INSTALL_PACKAGE` (déprécié depuis
API 29), ni `FileProvider`, ni stockage partagé. Le `PackageInstaller` moderne
est disponible dès le minSdk 21 du projet.

## 4.7 Purge du cache

Au démarrage, `IptvApplication` lance sur `Dispatchers.IO` une purge strictement
limitée à `cacheDir/app_updates` :

- supprimer tous les `.part` d'une tentative interrompue ;
- supprimer tout APK illisible ou d'un autre package ;
- supprimer tout APK dont le `versionCode` est inférieur ou égal à la version
  installée ;
- conserver un APK valide de version supérieure.

La purge ne bloque jamais l'affichage du splash et n'accède à aucun autre cache
(images Coil, téléchargements Media3). Elle nettoie aussi les métadonnées de
Release devenues obsolètes. Cela réalise RG13 au premier lancement de la
nouvelle version, puisque le processus de l'ancienne application est terminé
par son remplacement.

## 4.8 Intégration au démarrage et aux Paramètres

`AppUpdateViewModel` est créé une seule fois dans la composition racine de
`MainActivity`. Un `LaunchedEffect` appelle `checkAutomatically()` uniquement
quand :

- la session CSTV est `Active` ou `Offline` ;
- l'auto-login Xtream et le gate profil sont résolus ;
- la route courante est `home` ;
- aucune lecture vidéo n'est active ;
- le ViewModel n'a pas déjà tenté le contrôle pendant ce démarrage à froid.

Le garde en mémoire du ViewModel évite qu'une rotation/recomposition réessaie
après un échec, tout en laissant un nouveau processus réessayer au prochain
démarrage à froid comme l'exige RG15.

`AppUpdateDialog` est rendu au-dessus du `NavHost`, mais jamais au-dessus des
gates CSTV/Xtream/profil ni du lecteur. Une mise à jour obligatoire connue
reste bloquante sur toutes les routes non-player après son affichage. Le
`BackHandler` et le clic extérieur sont neutralisés dans cette variante.

La destination Paramètres reçoit un état immuable et des callbacks depuis ce
même ViewModel. L'entrée manuelle est ajoutée comme une carte `Surface2`, dans
le langage visuel de `docs/design-reference/screenshots/settings.png` : titre,
sous-titre de version et action lavande. Sur TV, elle réutilise les conventions
de focus de `TvSettingsActionButton`; sur mobile, elle suit les cartes existantes.
`Checking(MANUAL)` affiche le spinner sur cette entrée. `UpToDate` et
`CheckFailed(MANUAL)` sont présentés dans les Paramètres ; une Release trouvée
ouvre le dialogue global.

## 4.9 Concurrence et cycle de vie

- Un `Mutex` ou une unique `Job` dans le ViewModel sérialise vérification et
  téléchargement. Un contrôle manuel pendant un contrôle automatique rejoint
  le résultat courant au lieu de lancer un second appel.
- **Annuler** pendant `Downloading` annule la `Job`, ferme les flux et supprime
  le `.part`. Il n'existe plus d'annulation applicative après le `commit()` de
  la session système.
- La progression et les états durables passent par `StateFlow`. L'ouverture des
  réglages et de l'intent système passe par des effets à identifiant consommable
  afin qu'une recomposition ne les relance pas.
- Une mort de processus interrompt le téléchargement. Le `.part` est supprimé
  au prochain démarrage et la tentative repart de zéro, conformément au §3.8.
- Aucun polling, aucune boucle périodique et aucun travail permanent n'est lancé
  dans `init` du ViewModel.

## 4.10 Performance

- un petit JSON au maximum par 24 h et par appareil, hors action manuelle ;
- aucun travail réseau dans `Application.onCreate()` ou sur le thread principal ;
- APK écrit en streaming avec mémoire bornée ;
- recomposition limitée aux changements entiers de pourcentage ;
- cache réutilisé quand taille, package et version correspondent ;
- pas de `WorkManager`, service au premier plan ou réveil périodique.

Le téléchargement reste volontairement lié au processus : cette solution est
plus simple et conforme au choix « dialogue in-app, aucune notification ». Une
garantie de reprise après mort du processus imposerait un service/Worker et un
contrat produit différent.

## 4.11 Sécurité

- URL d'API et dépôt fixes ; URL d'asset validée avant tout téléchargement ;
- HTTPS uniquement et aucun downgrade HTTP ;
- client GitHub sans secrets ni logs de corps ;
- fichiers privés dans `cacheDir`, jamais exposés sur stockage partagé ;
- validation package/version avant installation ;
- signature finale et continuité de clé déléguées à Android ;
- receiver privé et `PendingIntent` explicite ;
- aucune stack trace, URL arbitraire ou erreur brute affichée à l'utilisateur.

La publication publique ne remplace pas la sécurité du keystore : un tiers
peut lire ou recopier l'APK, mais ne peut pas produire une mise à jour acceptée
par Android sans la clé de signature historique.

## 4.12 Compatibilité et dépendances

- minSdk 21 : `PackageInstaller.Session` est disponible ;
- API 26+ : autorisation par source avec
  `canRequestPackageInstalls()`/`ACTION_MANAGE_UNKNOWN_APP_SOURCES` ;
- API 31+ : `USER_ACTION_REQUIRED` explicite ;
- targetSdk 35 : `PendingIntent` mutable obligatoire pour recevoir les extras
  ajoutés au résultat de `Session.commit()` ;
- aucune dépendance Gradle supplémentaire ;
- nouvelle règle R8 obligatoire pour `GithubReleaseApiService`.

Risque opérationnel externe : Android déploie une vérification des développeurs
et des noms de package pour les applications distribuées hors Play. Le
sideloading direct n'est pas bloqué lors de la première vague du 30 septembre
2026, mais un déploiement mondial est annoncé pour 2027 sur les appareils
Android certifiés. Avant cette échéance, le propriétaire doit enregistrer
`com.cstv.app` et l'empreinte SHA-256 du keystore dans l'Android Developer
Console. Ce prérequis de distribution ne nécessite aucun code F35, mais la
perte du keystore empêcherait aussi cette preuve de propriété.

Références techniques consultées le 2026-08-12 :

- [GitHub — Get the latest release](https://docs.github.com/en/rest/releases/releases#get-the-latest-release) ;
- [Android — PackageInstaller](https://developer.android.com/reference/android/content/pm/PackageInstaller) ;
- [Android — PackageManager.canRequestPackageInstalls](<https://developer.android.com/reference/android/content/pm/PackageManager#canRequestPackageInstalls()>) ;
- [Android — ACTION_MANAGE_UNKNOWN_APP_SOURCES](https://developer.android.com/reference/android/provider/Settings#ACTION_MANAGE_UNKNOWN_APP_SOURCES) ;
- [Android — vérification des développeurs hors Play](https://developer.android.com/developer-verification/guides/android-developer-console).

## 4.13 Stratégie de tests prévue

Toutes les décisions sont isolées derrière des contrats Kotlin testables sur la
JVM, sans appareil ni réseau réel :

- parse SemVer strict, bornes 0–99, comparaison et débordement ;
- mapping DTO avec champs absents/null, tag invalide, plusieurs assets, asset
  absent et URL rejetée ;
- règles automatique/manuelle, 24 h, horloge incohérente, Plus tard, Ignorer,
  majeure obligatoire et dernière Release connue ;
- transitions du ViewModel, contrôle concurrent, annulation et distinction
  erreur silencieuse/manuelle ;
- écriture `.part`/renommage, progression bornée, réutilisation et purge via un
  répertoire temporaire et un inspecteur d'APK fake ;
- reprise après autorisation et mapping de tous les résultats d'installation
  via un `PackageInstallerGateway` fake.

L'adaptateur Android très mince (`PackageInstaller`, réglages système) est
couvert par compilation/lint ; les critères F35 ne dépendent pas d'un appareil
ou d'un émulateur, conformément à la stratégie de tests du projet.

---

# 5. Architecture

## 5.1 Vue d'ensemble

```text
MainActivity (démarrage) ─┐
                          ├─> AppUpdateViewModel ─> CheckForAppUpdateUseCase
SettingsScreen (manuel) ──┘             │                    │
                                        │                    ├─> AppUpdatePreferences
                                        │                    └─> AppUpdateRepository
                                        │                              │
                                        │                              ├─> GithubReleaseApiService
                                        │                              └─> UpdateApkStore
                                        │
                                        └─> AndroidPackageInstaller
                                                   │
                                                   ├─> réglages sources inconnues
                                                   └─> PackageInstaller.Session
                                                              │
                                                    UpdateInstallResultReceiver
                                                              │
                                                   confirmation système Android
```

Les couches domain ne connaissent ni `Context`, ni Retrofit, ni
`PackageInstaller`. La présentation ne manipule ni DTO, ni fichier, ni URL :
elle exprime des intentions utilisateur et affiche `AppUpdateUiState`.

## 5.2 Flux automatique

1. Les gates CSTV, Xtream et profil sont résolus et l'Accueil est composé.
2. `MainActivity` appelle une fois `checkAutomatically()`.
3. Le use case élimine d'abord une Release connue devenue obsolète.
4. Une majeure supérieure déjà connue est rendue immédiatement obligatoire.
5. Sinon, le use case applique délai 24 h, report et version ignorée.
6. Si un appel est dû, le repository lit `releases/latest` et mappe la réponse.
7. Une réussite mémorise l'heure ; une Release supérieure est mémorisée et
   comparée à la version installée.
8. La variante facultative ou obligatoire du dialogue est affichée. Toute
   erreur s'arrête à `Idle`, sans feedback utilisateur.

## 5.3 Flux manuel

1. L'utilisateur valide l'entrée des Paramètres.
2. Le ViewModel lance `checkManually()` sans consulter ni écrire l'horodatage,
   le report ou la version ignorée.
3. `NoUsableRelease` ou une version non supérieure produit `UpToDate`.
4. Une erreur produit le message manuel `update_error_check`.
5. Une Release supérieure alimente le même état `Available` et le même dialogue
   que le flux automatique.

## 5.4 Flux téléchargement et installation

1. **Mettre à jour** vérifie l'autorisation par source sur API 26+.
2. Si nécessaire, `AppUpdateDialog` ouvre les réglages et le ViewModel conserve
   la Release cible.
3. Au retour autorisé, le repository réutilise ou télécharge l'APK en cache.
4. Le fichier final est inspecté, puis copié dans une session
   `PackageInstaller`.
5. Le receiver reçoit `STATUS_PENDING_USER_ACTION` et ouvre l'écran de
   confirmation Android.
6. Un abandon revient à `Available`; un échec contrôlé revient à `Error`; un
   succès remplace l'application et termine l'ancien processus.
7. Au premier lancement de la nouvelle version, `IptvApplication` purge l'APK
   devenu obsolète et les métadonnées associées.

## 5.5 Responsabilités

| Composant | Fait | Ne fait pas |
|---|---|---|
| `CheckForAppUpdateUseCase` | Décide si/quand proposer et si la mise à jour est obligatoire. | Réseau, UI, fichiers, API Android. |
| `AppUpdateRepositoryImpl` | Lit GitHub, valide/mappe la Release, télécharge en streaming. | Règles de silence ou affichage Compose. |
| `AppUpdatePreferences` | Persiste uniquement les décisions locales appareil. | Profil, Room, synchronisation F34. |
| `UpdateApkStore` | Manipule uniquement `cacheDir/app_updates` et inspecte les APK. | Lancer l'installation. |
| `AndroidPackageInstaller` | Adapte autorisation et session Android. | Choisir la version ou afficher du texte. |
| `AppUpdateViewModel` | Orchestre une opération, expose l'état, applique les actions UI. | Connaître Retrofit ou `SharedPreferences`. |
| `AppUpdateDialog` | Rend les états mobile/TV et émet des actions. | Décision métier, téléchargement direct. |
| `MainActivity` | Déclenche le contrôle au bon moment et héberge l'overlay global. | Implémenter la logique de version. |
| `SettingsScreen` | Offre le point d'entrée manuel et son feedback local. | Posséder un deuxième cycle de mise à jour. |

## 5.6 Décisions techniques justifiées

1. **ViewModel racine partagé plutôt qu'intégration dans
   `SettingsViewModel`** : le dialogue automatique doit survivre à la navigation
   et le contrôle manuel doit rejoindre la même opération. Un ViewModel propre à
   la route Paramètres serait détruit en quittant l'écran et dupliquerait l'état.
2. **Client GitHub dédié** : l'isolation empêche toute fuite de credentials et
   évite les réécritures d'URL Xtream. Une simple qualification Hilt est moins
   coûteuse qu'un nouveau module réseau.
3. **Pas de WorkManager** : le PO demande un contrôle au démarrage, pas une
   tâche périodique. Le téléchargement interactif a besoin du dialogue et de
   l'action utilisateur ; un Worker ne garantit pas ce contexte.
4. **`PackageInstaller.Session` plutôt qu'intent APK historique** : API moderne
   disponible dès 21, résultat structuré, confirmation système explicite et
   absence de `FileProvider`.
5. **`SharedPreferences` séparées plutôt que Room/DataStore chiffré** : quatre
   petits groupes de scalaires locaux, sans secret, sans besoin réactif ni
   migration de base. La séparation empêche une inclusion accidentelle dans les
   snapshots F34.
6. **Dernière Release connue persistée** : indispensable pour réafficher une
   majeure déjà détectée à chaque démarrage tout en respectant la limite réseau
   de 24 h.
7. **Cache privé et renommage atomique** : évite les permissions de stockage,
   les APK partiellement installables et les fuites vers d'autres applications.
8. **Pas de SHA-256 applicatif** : décision PO RG14. Le contrôle package/version
   sert à la cohérence ; la confiance cryptographique reste celle de la
   signature Android.

## 5.7 Risques et mitigations

| Risque | Impact | Mitigation / limite acceptée |
|---|---|---|
| Majeure publiée juste après le contrôle quotidien | Découverte retardée de 24 h maximum. | Limite inhérente à RG2 ; une fois connue, cache local et réaffichage à chaque démarrage. |
| Quota GitHub/NAT partagé ou indisponibilité GitHub | Contrôle impossible. | Une requête/jour, échec automatique silencieux sans écrire l'heure, contrôle manuel explicite. |
| Asset très volumineux ou stockage insuffisant | Téléchargement/installation échoue. | Streaming, taille annoncée, `.part` supprimé, erreur réessayable. |
| Processus tué pendant le téléchargement | Téléchargement perdu. | `.part` purgé au prochain démarrage ; pas de reprise, conformément au périmètre. |
| Box TV sans activité de réglage ou installateur exploitable | Impasse pour une majeure. | `resolveActivity`, état `PermissionUnavailable`, URL manuelle et Quitter. |
| Variations OEM du `PackageInstaller` | Statut ou écran système différent. | Adapter tous les statuts publics, conserver un fallback générique, aucune dépendance à l'UI système. |
| APK GitHub compromis ou mauvais keystore | Refus d'installation. | URL contrainte, package/version inspectés, signature Android obligatoire. |
| Horloge appareil modifiée | Report ou throttle anormalement long. | Horodatage futur considéré expiré ; tests avec horloge injectée. |
| Recomposition/rotation | Double appel ou double intent système. | garde ViewModel, opération unique, effets consommables. |
| R8 release | Crash Retrofit invisible en debug. | règle `-keep GithubReleaseApiService`, puis validation release au stade prévu. |
| Vérification développeur Android 2027 | Installation sideload potentiellement dégradée sur appareils certifiés. | Enregistrer `com.cstv.app` et le certificat du keystore hors code avant le déploiement mondial. |

## 5.8 Frontières de cette étape

Cette architecture n'ajoute aucun découpage exécutable au §6 : celui-ci reste
réservé à l'étape 4. Aucun fichier Kotlin, manifeste, ressource, dépendance,
test, build, commit ou release n'est modifié/exécuté pendant l'étape 3.

---

# 6. Plan de développement

- [x] **T1 — Modèles domain (version + release)**

  Objectif : `SemanticVersion` (parse strict `vX.Y.Z`, bornes 0–99, `versionCode`,
  comparaison, anti-débordement), `AppUpdateRelease`, `AppUpdateCheckOrigin`
  (`AUTOMATIC`/`MANUAL`), `AppUpdateCheckResult` (résultat repository :
  `ReleaseFound`/`NoUsableRelease`/`Failure`), `AppUpdateDecision` (résultat use
  case) et `AppUpdateUiState` (§4.3).

  Fichiers : `domain/model/AppUpdate.kt`.

  Validation : tests unitaires SemVer (CA1–CA4, CA15) et égalité/comparaison de
  versionCode.

- [x] **T2 — Persistance locale (`AppUpdatePreferences`)**

  Objectif : `SharedPreferences` dédiées (`app_update_prefs`), horodatage
  dernière vérification automatique réussie, version reportée + échéance,
  version ignorée, dernière Release majeure connue. Horloge incohérente
  traitée comme expirée (§4.5).

  Fichiers : `data/local/storage/AppUpdatePreferences.kt`.

  Validation : tests unitaires (mock `Context`/`SharedPreferences`, pattern
  `SettingsManagerDebugModeTest`) couvrant lecture/écriture de chaque clé et le
  cas horloge future.

- [x] **T3 — Use case de décision (`CheckForAppUpdateUseCase`)**

  Objectif : contrat `domain/repository/AppUpdateRepository.kt` (lecture
  dernière Release + téléchargement en cache) et application de RG1–RG9 à
  partir d'un `AppUpdateCheckResult`, de `AppUpdatePreferences` et du
  `versionCode` installé (`TimeProvider` injecté).

  Fichiers : `domain/repository/AppUpdateRepository.kt`,
  `domain/usecase/CheckForAppUpdateUseCase.kt`.

  Validation : tests CA1–CA13 (délai 24 h, report, version ignorée, majeure
  obligatoire, origine automatique/manuelle, horloge incohérente).

- [x] **T4 — Client GitHub (DTO + Retrofit)**

  Objectif : DTO minimal (`tag_name`, `draft`, `prerelease`, `assets[].name`,
  `assets[].browser_download_url`, `assets[].size`) et interface Retrofit
  `GET repos/scilone/cstv/releases/latest`, streaming pour l'asset.

  Fichiers : `data/remote/dto/GithubReleaseDto.kt`,
  `data/remote/api/GithubReleaseApiService.kt`.

  Validation : tests de mapping DTO → `AppUpdateCheckResult` (asset absent, tag
  non conforme, plusieurs assets, URL hors `github.com/scilone/cstv`, draft/
  prerelease) = CA14/CA15.

- [x] **T5 — Gestion du cache APK (`UpdateApkStore`)**

  Objectif : nommage `cstv-<versionCode>.apk[.part]`, renommage atomique,
  critères de réutilisation (§4.4), purge ciblée (`.part`, package/version
  invalides, version ≤ installée) — RG12/RG13/RG16.

  Fichiers : `data/update/UpdateApkStore.kt`.

  Validation : tests JVM sur répertoire temporaire avec un inspecteur d'APK
  fake, couvrant CA16 et les cas limites §3.8 (cache purgé, APK déjà présent).

- [x] **T6 — Repository (`AppUpdateRepositoryImpl`)**

  Objectif : orchestrer lecture GitHub, mapping, validation d'URL d'asset
  (schéma/hôte/chemin), téléchargement `@Streaming` par blocs et délégation à
  `UpdateApkStore`. Client OkHttp/Retrofit GitHub dédié, sans cookie ni jeton.

  Fichiers : `data/repository/AppUpdateRepositoryImpl.kt`.

  Validation : tests avec `GithubReleaseApiService` et `UpdateApkStore` fakes/
  mocks (succès, échec réseau/HTTP, JSON illisible, annulation).

- [x] **T7 — Installation Android (adaptateurs minces)**

  Objectif : `AndroidPackageInstaller` (autorisation source inconnue,
  `PackageInstaller.Session`), `UpdateInstallResultReceiver` (receiver privé),
  déclaration manifeste (`REQUEST_INSTALL_PACKAGES`, receiver
  `exported="false"`), règle ProGuard pour `GithubReleaseApiService`.

  Fichiers : `data/update/AndroidPackageInstaller.kt`,
  `data/update/UpdateInstallResultReceiver.kt`, `AndroidManifest.xml`,
  `app/proguard-rules.pro`.

  Validation : compilation + lint uniquement — adaptateur Android mince sans
  logique testable sur JVM (AGENTS.md, exclusion des vérifications nécessitant
  un appareil).

- [x] **T8 — ViewModel partagé (`AppUpdateViewModel`)**

  Objectif : machine d'états `AppUpdateUiState`, sérialisation vérification/
  téléchargement (`Mutex`/`Job` unique), actions utilisateur (`checkAutomatically`,
  `checkManually`, `install`, `postpone`, `ignore`, `cancelDownload`,
  `onPermissionResult`), garde anti-double-appel par démarrage à froid (§4.8/4.9).

  Fichiers : `presentation/update/AppUpdateViewModel.kt`.

  Validation : tests de transitions (use case, repository et installeur mockés)
  couvrant CA9–CA13 côté présentation et l'annulation §4.9.

- [x] **T9 — Dialogue partagé mobile/TV + textes**

  Objectif : `AppUpdateDialog` composable unique paramétré `isTv`, tous les
  états §3.4, variante obligatoire (pas de Plus tard/Ignorer/retour, `Quitter`
  remplace Annuler/Fermer). Ajout des clés `strings.xml` (§3.6).

  Fichiers : `presentation/update/AppUpdateDialog.kt`,
  `app/src/main/res/values/strings.xml`.

  Validation : compilation. Pas de test Compose UI (AGENTS.md, tests
  nécessitant un appareil exclus des critères).

- [x] **T10 — Intégration (démarrage, Paramètres, DI, purge)**

  Objectif : `AppUpdateViewModel` créé une fois dans `MainActivity`,
  déclenchement automatique après résolution des gates (§4.8), transmission de
  l'état/callbacks à la destination `settings` via `NavGraph`, entrée
  « Rechercher une mise à jour » dans `SettingsScreen` (mobile + TV), purge du
  cache au démarrage dans `IptvApplication`, bindings Hilt dans `AppModule`.

  Fichiers : `MainActivity.kt`, `presentation/navigation/NavGraph.kt`,
  `presentation/settings/SettingsScreen.kt`, `IptvApplication.kt`,
  `di/AppModule.kt`.

  Validation : `./gradlew assembleDebug` + `./gradlew testDebugUnitTest` (non-
  régression complète) — pas de vérification manuelle sur appareil requise.

---

# 7. Notes de développement

## Vérifications effectuées (2026-08-12)

Appel réel de `https://api.github.com/repos/scilone/cstv/releases/latest`, sans
authentification, depuis un poste quelconque :

```
HTTP 200
x-ratelimit-limit: 60   (quota horaire par IP, non authentifié)
tag_name: v1.77.6       (= version installée, versionCode 17_706)
prerelease: false | draft: false
asset: app-release.apk | 8 994 232 octets
  https://github.com/scilone/cstv/releases/download/v1.77.6/app-release.apk
```

L'API publique, le nom d'asset stable (`app-release.apk`) et l'URL de
téléchargement directe sont donc confirmés. Le corps de la Release ne fait que
**77 caractères** : les notes générées par `--generate-notes` sont aujourd'hui
trop pauvres pour être affichées telles quelles à l'utilisateur (cf. questions
ouvertes).

## Hypothèses de départ

1. **Source de version** : le champ `tag_name` de la Release GitHub (format
   `vX.Y.Z`) est parsé et converti en `versionCode` avec la formule du projet,
   puis comparé à `BuildConfig.VERSION_CODE`. Aucun fichier de manifeste
   supplémentaire n'est publié.
2. **Binaire** : l'APK est le premier asset de la Release dont le nom se termine
   par `.apk` (`browser_download_url`).
3. **Notes de version** : le champ `body` de la Release n'est ni mappé vers le
   domain, ni affiché à l'utilisateur (décision PO RG11).
4. **Quota** : l'API GitHub non authentifiée est limitée à 60 requêtes/heure par
   adresse IP. Une vérification automatique quotidienne reste très en dessous
   de ce seuil en usage normal. Un HTTP 403 / 429 est un échec : silencieux en
   automatique, explicite en manuel, sans mise à jour de l'horodatage.
5. **Installation** : l'installation silencieuse est **impossible** sans être
   device owner ou application système. L'utilisateur confirmera toujours dans
   la boîte de dialogue système. C'est une contrainte de la plateforme, pas un
   choix de conception.
6. **Autorisation** : `REQUEST_INSTALL_PACKAGES` est requise à partir d'API 26,
   avec en plus une autorisation par application accordée par l'utilisateur
   (`canRequestPackageInstalls()` / `ACTION_MANAGE_UNKNOWN_APP_SOURCES`). Sous
   API 26 (minSdk du projet = 21), c'est le réglage global « sources inconnues »
   qui s'applique : chemin distinct à prévoir.
7. **Android TV** : certaines box masquent ou rendent difficilement accessible
   l'écran « sources inconnues ». Un repli explicatif est nécessaire quand
   l'intent système n'est pas résolvable.
8. **Signature** : la mise à jour ne s'installera que si l'APK téléchargé est
   signé avec le keystore de production. Un APK issu d'un autre keystore sera
   rejeté par Android — comportement attendu, pas à contourner.
9. **Non-régression release** : toute nouvelle interface Retrofit ajoutée pour
   ce ticket devra recevoir sa règle `-keep` dans `proguard-rules.pro`, sous
   peine de crash en build release invisible en debug (règle `AGENTS.md`).

## Décisions PO — 2026-08-12 (étape 2)

Toutes les questions ouvertes de l'étape 1 ont été tranchées :

| Question | Décision |
|---|---|
| Notes de version | **Aucune note affichée.** Le dialogue annonce la disponibilité et invite à mettre à jour. `scripts/release-local.sh` reste inchangé. |
| Fréquence | Vérification **au démarrage**, **1× par jour et par appareil**. Pas de `Worker` périodique, pas de notification système. |
| Mise à jour obligatoire | **Uniquement** pour une version **majeure** (rupture de compatibilité). Détectée sur le numéro majeur du tag, sans marqueur additionnel. |
| Report | **Plus tard** = rappel **le lendemain**. |
| Silence durable | **Ignorer cette version** = plus jamais pour ce `versionCode`. |
| Point d'entrée manuel | **Oui**, entrée « Rechercher une mise à jour » dans les Paramètres (mobile + TV). |
| Connexion mesurée | **Aucun blocage**, téléchargement toujours autorisé. |
| Pré-releases / brouillons | **Ignorés**. |
| Intégrité | **HTTPS + signature Android suffisent.** Pas de contrôle SHA-256. |
| Emplacement | **`cacheDir`**, avec **purge après installation réussie**. |
| UI mobile / TV | **Composable partagé** paramétré par `isTv`, comme le reste du projet. Séparation autorisée uniquement si l'unification dégrade l'ergonomie télécommande. |

## Implémentation — étapes 4 et 5 (2026-08-12)

Découpage en 10 tâches (§6) puis implémentation complète, dans l'ordre domain
→ data → adaptateurs Android → présentation → intégration. Décisions prises
pendant le code, non explicitées jusque-là dans les étapes 1–3 :

- **Report (RG4) et ignore (RG5) sont tous les deux scopés au `versionCode`**,
  symétriquement : un report posé sur `1.78.0` n'empêche pas la proposition de
  `1.79.0` (même logique que CA8 pour l'ignore, qui ne le précisait que pour
  ce cas). Sans ce scope, une version ultérieure resterait masquée par un
  report obsolète.
- **`PackageInstallerGateway` (nouveau, `domain/update`)** : le retour de
  `install()` (`AwaitingUserConfirmation`) ne préjuge pas de l'issue finale —
  un succès remplace le processus avant que l'appelant ne puisse réagir, un
  abandon/échec après confirmation système arrive bien après le retour de
  l'appel suspendu. Les issues tardives remontent via un `Flow` séparé
  (`installResults`), observé en continu par `AppUpdateViewModel`.
- **Retour de l'autorisation « sources inconnues » traité par `ON_RESUME`**,
  pas par `ActivityResultLauncher` : l'intent système et l'intent de
  confirmation d'installation sont ouverts avec `FLAG_ACTIVITY_NEW_TASK`
  depuis le contexte applicatif (aucune dépendance à l'Activity courante),
  et `AppUpdateViewModel.onAppResumed()` relit l'autorisation au retour dans
  l'app — plus simple que capturer un code de résultat, et suffisant au
  regard du parcours D (§3.2).
- **Entrée « Rechercher une mise à jour »** ajoutée dans les Paramètres
  (mobile + TV) sans dupliquer de cycle : elle lit et pilote la même instance
  d'`AppUpdateViewModel` que le contrôle automatique, transmise depuis
  `MainActivity` via `AppNavGraph`.

Vérifications exécutées : `./gradlew compileDebugKotlin`,
`./gradlew testDebugUnitTest` (totalité de la suite, y compris les nouveaux
tests F35, sans régression), `./gradlew assembleDebug`, `./gradlew lintDebug`
— toutes réussies. Aucune vérification manuelle sur appareil n'était requise
pour ce ticket (AGENTS.md) ; l'installation réelle (`PackageInstaller`,
confirmation système) reste un adaptateur mince non couvert par des tests JVM,
conformément à la stratégie de tests du projet.

## Conséquences relevées pendant la spécification

- La purge de l'APK (`RG13`) ne peut pas s'exécuter après l'installation : le
  remplacement de l'application termine le processus. Elle est donc déplacée
  **au démarrage suivant**, sur critère de `versionCode`.
- La règle « majeure = obligatoire » se déduit entièrement du tag SemVer déjà
  produit par le workflow de release. Aucune convention nouvelle, aucun champ
  supplémentaire, aucun risque d'oubli au moment de publier.
- Les clés de persistance de ce ticket (dernière vérification, report, version
  ignorée) sont **strictement locales à l'appareil** et doivent être exclues de
  la synchronisation cloud F34, sans quoi la règle « 1× par jour **par
  appareil** » serait violée.

---

# 8. Review

Décision : **CHANGES REQUESTED** (2026-08-12).

## Critique

### F35-R1 — Le streaming de l'APK bloque le thread principal

**Description :** `AppUpdateViewModel.downloadAndInstall()` appelle
`repository.downloadApk()` depuis `viewModelScope`. Après la suspension
Retrofit, `AppUpdateRepositoryImpl.writeStreamed()` lit `ResponseBody.byteStream()`
et écrit le fichier avec des API bloquantes, sans `withContext(Dispatchers.IO)`
ni dispatcher IO injecté. La continuation reprend donc sur le dispatcher Main
du ViewModel et y exécute toute la copie de l'APK.

**Impact :** pendant un téléchargement réel, l'interface peut rester figée
plusieurs secondes, voire déclencher un ANR sur une connexion lente. La
progression ne peut pas être rendue normalement et le bouton **Annuler** ne peut
pas traiter son clic tant que le thread principal est occupé. Le comportement
contredit directement les contrats de progression/annulation et le §4.10
« aucun travail réseau sur le thread principal ».

**Correction attendue :** exécuter la lecture réseau, l'écriture du `.part`,
la promotion et les inspections de fichiers sur un dispatcher IO injecté ou
dans un `withContext(Dispatchers.IO)`, tout en conservant l'annulation
structurée de la coroutine et une progression bornée. Ajouter un test qui
prouve que le chemin bloquant n'utilise pas le dispatcher Main.

## Majeur

### F35-R2 — Un APK fraîchement téléchargé n'est jamais validé avant installation

**Description :** `downloadApk()` applique taille/package/version uniquement à
un APK final déjà présent via `findReusableApk()`. Après un téléchargement, il
appelle directement `promotePart()` puis retourne le fichier. Ni le nombre
d'octets effectivement reçus, ni `ApkInspector`, ni le package, ni le
`versionCode` ne sont contrôlés sur ce nouveau fichier avant que le ViewModel
le transmette à `PackageInstaller`.

**Impact :** une réponse tronquée, une taille GitHub incohérente ou un asset
portant le mauvais package/version est promu comme cache final et envoyé au
système. L'erreur est repoussée tardivement vers l'installateur Android au lieu
d'être traitée comme un échec de téléchargement contrôlé ; le cache ne respecte
pas les invariants annoncés aux §4.4, §4.11 et dans T5/T6.

**Correction attendue :** avant toute promotion/installation, vérifier la
taille exacte puis inspecter package et `versionCode`; supprimer le `.part` et
tout final invalide et remonter une erreur de téléchargement générique. Couvrir
par tests les tailles courte/longue, l'APK illisible, le mauvais package et la
mauvaise version.

### F35-R3 — Une mise à jour obligatoire reste annulable pendant le téléchargement

**Description :** `AppUpdateDialog` transmet tous les états `Downloading` à
`DownloadingContent`, qui affiche systématiquement **Annuler** et appelle
`cancelDownload()`. Le champ `required` n'est pas transmis à ce composable.

**Impact :** pour une majeure obligatoire, l'utilisateur peut annuler le
téléchargement et revenir à l'état `Available`. Cela viole le parcours C, le
tableau des états et CA13, qui exigent l'absence d'**Annuler/Fermer** et leur
remplacement par **Quitter** dans tous les états obligatoires.

**Correction attendue :** rendre `DownloadingContent` conscient du caractère
obligatoire : **Quitter** doit fermer l'application et aucune action ne doit
annuler la `Job` dans cette variante. Ajouter une couverture JVM de la politique
d'actions de chaque état obligatoire.

### F35-R4 — Le client autorise encore une redirection HTTPS vers HTTP

**Description :** seule l'URL initiale de l'asset est validée. Le client OkHttp
4.12.0 dédié conserve ses valeurs par défaut `followRedirects = true` et
`followSslRedirects = true`; une réponse de redirection peut donc faire suivre
le téléchargement vers HTTP. Aucun contrôle de l'URL finale de la chaîne de
redirections n'est réalisé.

**Impact :** le transport n'est pas « HTTPS uniquement » et un downgrade reste
possible, contrairement à RG14 et au §4.11. La signature Android empêcherait
l'installation silencieuse d'un APK forgé, mais elle n'empêche ni le
téléchargement de données non fiables, ni un déni de mise à jour, ni les erreurs
système qui en découlent.

**Correction attendue :** interdire les redirections inter-protocoles
(`followSslRedirects(false)`) et vérifier que toute URL finale acceptée reste en
HTTPS sur un hébergement explicitement autorisé pour les assets GitHub. Ajouter
des tests de redirection HTTPS normale, de downgrade HTTP et d'hôte final non
autorisé.

### F35-R5 — La branche TV ne respecte pas le contrat de composants et de focus

**Description :** `AppUpdateDialog` emploie les `Button`/`OutlinedButton`
Material3 mobile dans les deux variantes, alors que §3.5 impose les composants
`tv-material` sur TV. Seul l'état `Available` demande explicitement le focus;
les états permission, téléchargement et erreur n'ont aucune entrée de focus
déterministe. Dans les Paramètres, la carte est câblée sur `Surface3` et son
action TV conserve elle aussi le fond `Surface3`, au lieu de la carte `Surface2`
et de l'action lavande prévues au §4.8.

**Impact :** la navigation D-pad et le focus initial ne sont pas garantis sur
plusieurs étapes critiques du parcours, et l'action de mise à jour perd la
hiérarchie visuelle explicitement demandée. La fonctionnalité TV, qui motive en
grande partie F35, n'est donc pas conforme à son contrat statique même si elle
compile.

**Correction attendue :** fournir une vraie branche TV avec composants
`androidx.tv.material3` (ou deux composables si nécessaire), focus initial
déterministe sur l'action primaire de chaque état et actions horizontales;
aligner la carte Paramètres sur `Surface2` et son action principale sur
`AccentLavande`, sans dégrader la variante mobile.

### F35-R6 — La couverture annoncée ne protège pas plusieurs contrats livrés

**Description :** les tests présents ne couvrent ni l'exécution IO de la copie,
ni la validation d'un APK nouvellement téléchargé, ni les redirections, ni les
actions de la variante obligatoire. Le test de téléchargement utilise même un
inspecteur qui renvoie toujours `null` tout en acceptant le fichier promu. Les
cas annoncés dans T6/T8/§4.13 — annulation réelle du repository, reprise après
autorisation, échecs d'installation, horloge future — ne sont pas exercés non
plus.

**Impact :** la suite complète reste verte malgré F35-R1 à F35-R4 et ne fournit
pas la preuve JVM promise par le ticket pour les critères fonctionnels et les
edge cases isolables sans appareil. Une régression des chemins essentiels peut
donc être publiée sans signal.

**Correction attendue :** compléter les tests JVM autour d'un dispatcher
contrôlé, d'un `ApkInspector` fake et d'un serveur HTTP fake; couvrir la
politique d'actions obligatoire, la reprise d'autorisation, les résultats
d'installation et les horodatages futurs. Aucun test instrumenté/device n'est
demandé.

## Mineur

### F35-R7 — Les fermetures implicites après la demande d'autorisation ne reportent pas la version

**Description :** le même `onDismiss` sert au Retour/clic extérieur et aux
actions de fermeture. Dans `AppUpdateViewModel.dismiss()`, un état
`PermissionRequired`, `PermissionUnavailable` ou `Error` facultatif repasse à
`Idle` sans appeler `postpone()`; le report RG4 n'est appliqué qu'à `Available`.

**Impact :** après une recherche manuelle (qui ne met pas à jour l'horodatage
automatique), fermer implicitement le dialogue à l'étape d'autorisation permet
à la même version de réapparaître dès le prochain démarrage, au lieu d'être
silencée 24 h comme l'exige le §4.5.

**Correction attendue :** distinguer une fermeture implicite d'une action
explicite si leurs effets doivent diverger, puis appliquer le report de 24 h à
toute fermeture implicite d'un dialogue facultatif possédant une Release;
nettoyer aussi `pendingPermissionRelease` sur toutes les sorties terminales.

### F35-R8 — L'adaptateur d'installation ne ferme pas complètement son cycle de session

**Description :** sur API 31+, `SessionParams` ne fixe pas explicitement
`USER_ACTION_REQUIRED` comme le prévoit le §4.6. De plus, si l'écriture ou le
commit lève après `createSession()`, l'exception est convertie en `Failed` mais
la session créée n'est jamais abandonnée.

**Impact :** la confirmation repose sur un comportement implicite de plateforme
au lieu du contrat déclaré, et des échecs répétés peuvent laisser des sessions
staging orphelines consommant du stockage jusqu'au nettoyage du système.

**Correction attendue :** appeler `setRequireUserAction(USER_ACTION_REQUIRED)`
sur API 31+ et abandonner explicitement toute session créée qui n'a pas été
commitée, sans masquer l'annulation de coroutine.

## Vérifications effectuées

- Architecture, intégration racine/Paramètres, sécurité réseau, stockage cache,
  machine d'états, UI mobile/TV et tests F35 relus statiquement.
- Comparaison avec `docs/design-reference/screenshots/settings.png` et les
  couleurs/composants de `SettingsScreen`.
- Valeurs par défaut du binaire OkHttp 4.12.0 local vérifiées : redirections
  générales et inter-protocoles activées.
- `./gradlew --no-daemon --max-workers=1 testDebugUnitTest assembleDebug lintDebug`
  : succès (`BUILD SUCCESSFUL`; tâches majoritairement `UP-TO-DATE`).
- `git diff --check` : succès.

## Limites de la review

- Aucun appareil ni émulateur n'est requis ou utilisé, conformément à la
  stratégie de tests du projet. Le focus réel OEM et l'écran système
  `PackageInstaller` ne sont donc pas présentés comme validés.
- L'arbre de travail contient aussi les changements F33/F34 et des changements
  backend préexistants. Ils ont été préservés et ne font pas partie des constats
  F35 ci-dessus.

## Corrections demandées

- Corriger F35-R1 à F35-R8 à l'étape 7 avant toute validation finale.

## Corrections appliquées — Étape 7 (2026-08-12)

- **F35-R1** (Critique) : `AppUpdateRepositoryImpl.downloadApk` s'exécute
  désormais entièrement dans `withContext(ioDispatcher)` (constructeur
  secondaire injectable, même motif que `VodRepositoryImpl`/
  `SeriesRepositoryImpl`, `Dispatchers.IO` par défaut) — la copie
  réseau → disque ne tourne plus sur le dispatcher de l'appelant
  (`viewModelScope`, donc `Main`). Test `F35-R1 downloadApk completes when
  run on an injected dispatcher` : preuve JVM que le dispatcher est
  effectivement pris en compte ; l'absence de blocage du thread Main réel
  reste hors périmètre JVM (test instrumenté), comme documenté par la review.
- **F35-R2** (Majeur) : `UpdateApkStore.validatePromoted()` (nouveau) rejoue
  les mêmes critères que `findReusableApk` (taille exacte, package,
  `versionCode`) sur le fichier **fraîchement promu**, et le supprime s'il
  échoue. `AppUpdateRepositoryImpl.downloadApk` lève désormais une
  `IOException` si la validation échoue, avant de jamais transmettre le
  fichier à `PackageInstaller`. Tests : `F35-R2 a freshly downloaded apk that
  fails inspection is rejected...` et deux cas dans `UpdateApkStoreTest`
  (taille tronquée, mauvais package) ; le test de succès existant utilise
  maintenant un inspecteur qui reconnaît réellement le fichier au lieu d'un
  `NoopApkInspector` qui acceptait tout par construction.
- **F35-R3** (Majeur) : `AppUpdateDialog.DownloadingContent` reçoit
  maintenant `required` et affiche **Quitter** (ferme l'application) au lieu
  d'**Annuler** dans la variante obligatoire ; `onCancelDownload` n'est plus
  atteignable pendant un téléchargement obligatoire.
- **F35-R4** (Majeur) : le client OkHttp GitHub pose `followSslRedirects(false)`
  (refuse le suivi d'une redirection HTTPS → HTTP) et un `addNetworkInterceptor`
  qui rejette explicitement toute requête réseau, y compris un hop de
  redirection, dont l'URL n'est pas HTTPS. Défense en profondeur non couverte
  par un test JVM (couche réseau réelle), conformément aux limites déjà
  actées par la review.
- **F35-R5** (Majeur) : `AppUpdateDialog` utilise désormais de vrais
  composants `androidx.tv.material3.Button`/`OutlinedButton` sur TV (focus/
  scale D-pad natifs) au lieu des composants Material3 mobiles ; **chaque**
  état (pas seulement `Available`) pose un focus initial déterministe sur son
  action primaire via `rememberInitialFocus`. Dans les Paramètres,
  `TvCheckUpdateCard`/`MobileCheckUpdateCard` passent de `Surface3` à
  `Surface2`, et l'action TV reçoit explicitement `containerColor =
  AccentLavande` (le bouton mobile utilisait déjà `colorScheme.primary`, qui
  vaut `AccentLavande` dans le thème du projet — aucun changement nécessaire
  côté mobile).
- **F35-R6** (Majeur) : couverture complétée en même temps que R1/R2/R7 —
  voir les tests cités pour chaque point. `ConflictResolversTest`-style
  d'annulation réelle, reprise d'autorisation et résultats d'installation
  restent couverts par les tests déjà en place
  (`AppUpdateViewModelTest.cancelDownload...`,
  `...an install abort received asynchronously...`) ; aucune régression sur
  ce périmètre.
- **F35-R7** (Mineur) : `AppUpdateViewModel.dismiss()` applique désormais le
  report 24 h (`postpone`) à toute fermeture implicite d'un dialogue optionnel
  possédant une Release (`PermissionRequired`, `PermissionUnavailable`,
  `Error`), pas seulement `Available` ; `pendingPermissionRelease` est
  nettoyée sur toutes les sorties terminales via `clearToIdle()`. Trois
  nouveaux tests `F35-R7 dismissing...postpones the release`, plus un test de
  non-régression confirmant qu'une variante **obligatoire** n'est jamais
  reportée par `dismiss()`.
- **F35-R8** (Mineur) : `AndroidPackageInstaller` pose
  `setRequireUserAction(USER_ACTION_REQUIRED)` sur API 31+ (au lieu de
  dépendre du comportement implicite de la plateforme) et abandonne
  explicitement (`installer.abandonSession`) toute session créée qui échoue
  avant ou pendant le `commit()`, aux deux points où l'échec peut survenir
  (écriture et commit). Adaptateur Android mince, non testable sur JVM
  (`PackageInstaller` réel), conformément à la stratégie de tests du projet —
  vérifié par relecture et par `assembleDebug`/`assembleRelease`.

Vérifications exécutées après corrections :
`./gradlew testDebugUnitTest lintDebug assembleDebug` — succès, **879 tests,
0 échec** (869 F33/F34 + 10 nouveaux/modifiés F35) ; `./gradlew
:app:assembleRelease` — succès, règle R8 `GithubReleaseApiService` confirmée
tenir face à R8 après les changements (nouvel intercepteur réseau,
composants `tv.material3`).

Décision PO (2026-08-12, alignée sur F33/F34) : le parcours réel
téléchargement + installation d'un APK sur un appareil physique n'est pas
exécuté avant livraison — hors périmètre des critères de validation du projet
(AGENTS.md exclut explicitement les vérifications nécessitant un appareil ou
un émulateur) et jamais exigé par la review F35 elle-même. Accepté sur la
base des 879 tests JVM verts et de la relecture statique des trois
adaptateurs Android minces (`AndroidPackageInstaller`,
`UpdateInstallResultReceiver`, réglages système). Statut → `VALIDATION`.

---

# 9. Release

Version :
v1.78.0

Commit :
e8a7406ab05bfb78c04305f7fc910f8a6a1fb965

Date :
2026-08-12
