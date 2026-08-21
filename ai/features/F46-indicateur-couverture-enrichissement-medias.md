# F46 - Indicateur de couverture de l’enrichissement des médias

## Informations générales

Status:
IMPLEMENTED

Created:
2026-08-21

Dépendances:
- F45 — Consolidation des données IPTV par métadonnées externes

---

# 1. Description

CSTV doit permettre de visualiser la progression de l’enrichissement du catalogue local introduit par F45.

Une section dédiée dans les **Paramètres** doit indiquer quelle proportion des films et séries présents sur l’installation a pu être reliée à une identité média externe CSTV.

L’indicateur ne doit pas se limiter au pourcentage de médias liés : il doit également permettre de distinguer la **couverture du matching** de la **progression du traitement**.

L’utilisateur doit ainsi pouvoir savoir si :

- une partie du catalogue reste encore à analyser ;
- le backfill est toujours en cours ;
- certains médias ont été analysés mais n’ont pas pu être associés de manière suffisamment fiable ;
- le catalogue a entièrement été parcouru même si le taux de liaison final est inférieur à 100 %.

Le suivi concerne uniquement les **films et séries actuellement présents dans le catalogue IPTV local**.

Les saisons et épisodes ne participent pas à ces indicateurs.

---

# 2. Contexte

F45 introduit un enrichissement progressif du catalogue IPTV.

Chaque film ou série peut progressivement être associé à une identité externe CSTV et bénéficier de métadonnées consolidées. Ce travail est volontairement effectué en arrière-plan, à faible priorité et sans bloquer l’utilisation normale de l’application.

Cette approche rend cependant la progression difficile à observer.

Un taux de liaison seul serait insuffisant.

Par exemple, un catalogue affichant **97 % de médias liés** peut correspondre à deux situations très différentes :

- le traitement est encore en cours et les 3 % restants n’ont pas encore été analysés ;
- 100 % du catalogue a été analysé mais 3 % des médias n’ont pas pu être associés de manière suffisamment fiable.

CSTV doit donc exposer deux notions distinctes :

- **taux de médias liés** : qualité/couverture effective de l’enrichissement ;
- **taux de médias traités** : progression du travail de matching sur le catalogue.

Un détail séparé pour les films et les séries doit également permettre d’identifier facilement une différence importante de couverture entre les deux types de médias.

Cette information est avant tout une information de suivi et de diagnostic. Elle ne doit donc pas encombrer les écrans de consultation du catalogue.

---

# 3. Objectif

- Donner une vision claire de la couverture de l’enrichissement du catalogue.
- Permettre de connaître le pourcentage de films et séries effectivement liés à une identité externe CSTV.
- Permettre de distinguer un catalogue encore en cours de traitement d’un catalogue entièrement analysé.
- Montrer séparément la couverture des films et celle des séries.
- Permettre d’identifier les médias analysés mais non résolus.
- Représenter uniquement l’état du catalogue IPTV actuellement présent sur l’installation.
- Fournir cette information dans les Paramètres sans modifier l’expérience normale de navigation dans le catalogue.

Le résultat attendu doit notamment permettre d’identifier sans ambiguïté une situation telle que :

> Catalogue traité à 100 %, médias liés à 97,8 %.

Cela signifie que l’enrichissement a terminé son passage sur le catalogue, mais que 2,2 % des médias n’ont pas obtenu de correspondance suffisamment fiable.

---

# 4. Décisions produit

## 4.1 Décisions produit prises à l’étape 1

| Sujet | Décision |
|---|---|
| Emplacement | L’état de l’enrichissement est présenté dans une section dédiée des **Paramètres**. Il n’est pas affiché en permanence dans les écrans principaux de CSTV. |
| Indicateur principal | Afficher à la fois le **pourcentage de médias liés** et le **pourcentage de médias traités**. |
| Informations complémentaires | Afficher les compteurs permettant de comprendre ces pourcentages ainsi qu’un détail séparé entre **films** et **séries**. |
| Média lié | Un média est considéré comme lié lorsqu’une correspondance fiable a été acceptée et qu’il possède une identité externe CSTV. |
| Média traité | Un média est considéré comme traité lorsqu’une tentative de résolution a abouti soit à un média lié, soit à un état `UNRESOLVED` pour la tentative courante. |
| Retry technique | Un média dont la tentative n’a pas produit de résultat métier à cause d’un échec technique et qui reste dans la file de retry n’est pas considéré comme traité. |
| Cooldown `UNRESOLVED` | Un `UNRESOLVED` déjà obtenu compte comme traité pour le passage courant même s’il possède un `retryAfter` de revalidation future. |
| Périmètre du catalogue | Les statistiques utilisent **tous les films et toutes les séries actuellement présents dans le catalogue IPTV local**, indépendamment des catégories affichées ou masquées. |
| Catégories masquées | Les catégories masquées par l’utilisateur restent incluses dans les statistiques puisqu’elles font toujours partie du catalogue local. |
| Films / séries | Les statistiques globales sont accompagnées d’un détail permettant de comparer séparément films et séries. |
| Saisons / épisodes | Hors calcul. Seuls les médias de niveau film et série participent aux indicateurs. |
| Nature de l’indicateur | Il s’agit d’un état informatif. Aucun enrichissement supplémentaire ne doit être déclenché uniquement parce que l’utilisateur consulte cet écran. |
| Objectif du 100 % traité | `100 % traité` signifie que tous les médias actuellement éligibles du catalogue ont obtenu un résultat de résolution pour le passage courant, même si certains restent `UNRESOLVED`. |
| Objectif du 100 % lié | `100 % lié` signifie que tous les films et séries actuellement présents dans le catalogue disposent d’une correspondance acceptée. |

## 4.2 Décisions produit prises à l’étape 2

Les questions fonctionnelles restantes ont été tranchées avec les hypothèses recommandées du workflow afin de permettre l’enchaînement demandé des étapes 2, 3 et 4.

| Sujet | Décision |
|---|---|
| Présentation | Une **carte “Enrichissement des médias”** est ajoutée dans les Paramètres, sur mobile et TV. |
| Information principale | La carte met en avant le **taux de médias liés** avec le compteur `liés / total`. |
| Progression du traitement | Une seconde information distincte affiche **“Traitement : X %”**. |
| Détail des états | Afficher les compteurs **Liés**, **Non résolus** et **À traiter**. |
| Détail par type | Afficher une ligne **Films** et une ligne **Séries**, chacune avec son taux de liaison et son compteur `liés / total`. |
| Interaction | La carte est informative et ne nécessite pas de navigation vers un nouvel écran. Elle est directement lisible dans les Paramètres. |
| Mise à jour | Les valeurs se mettent à jour automatiquement tant que l’écran des Paramètres est ouvert. Aucun bouton “Actualiser” n’est nécessaire. |
| Catalogue vide | Si aucun film ni aucune série n’est présent, afficher `Aucun média dans le catalogue` et ne pas afficher un pourcentage trompeur de 0 % ou 100 %. |
| Type absent | Si un seul type est absent, sa ligne affiche `0 média` sans pourcentage ; l’indicateur global repose uniquement sur les médias réellement présents. |
| Backfill actif | Aucun libellé artificiel “en cours” n’est nécessaire : un taux de traitement inférieur à 100 % et le compteur “À traiter” rendent l’état explicite. |
| `UNRESOLVED` | Le libellé utilisateur est **“Non résolus”** ; le terme technique `UNRESOLVED` n’est pas affiché. |
| Retry technique | Un item en retry technique reste inclus dans **“À traiter”** tant qu’aucun résultat métier (`linked` ou `UNRESOLVED`) n’a été persisté. |
| Arrondi | Les pourcentages sont affichés avec **une décimale** quand nécessaire ; un entier exact peut être affiché sans décimale (`100 %`). |
| Couleur / alerte | L’indicateur reste neutre : un taux de liaison faible n’est pas présenté comme une erreur. Aucun seuil rouge/orange/vert n’est introduit dans cette feature. |
| Action utilisateur | Aucun bouton de relance du matching/backfill n’est ajouté. F46 observe F45 sans modifier son fonctionnement. |

---

# 5. Hypothèses

- F45 reste la source de vérité concernant l’état de résolution externe d’un média.
- Chaque film et série du catalogue local peut être classé de manière non ambiguë dans les états nécessaires au calcul.
- Une ligne de `external_media_links` avec `externalId != null` représente un média lié.
- Une ligne de `external_media_links` avec `externalId == null` et `lastMatchAttemptAt != null` représente un résultat de résolution `UNRESOLVED` déjà obtenu pour le passage courant.
- Le `retryAfter` d’un `UNRESOLVED` correspond à un cooldown de revalidation future et ne remet pas le média dans l’état “non traité” du passage courant.
- Un échec technique du worker conserve la demande dans `external_hydration_queue` avec un backoff ; en l’absence de résultat persisté dans `external_media_links`, le média reste “à traiter”.
- Un média `UNRESOLVED` peut ultérieurement être revalidé et devenir lié ; les indicateurs représentent donc un état courant et non une valeur définitive.
- Les médias supprimés du catalogue IPTV lors d’une synchronisation cessent automatiquement de participer aux statistiques.
- Les nouveaux médias découverts lors d’une synchronisation augmentent immédiatement le périmètre total et peuvent donc temporairement faire diminuer les pourcentages.
- Les catégories visibles ou masquées sont une préférence de présentation et ne modifient pas la définition du catalogue couvert.
- Le volume du catalogue peut dépasser 50 000 médias ; le calcul doit rester agrégé côté SQLite et ne jamais matérialiser les lignes du catalogue en Kotlin.
- Cette fonctionnalité observe le fonctionnement de F45 mais ne modifie ni ses règles de matching ni ses priorités de backfill.

---

# 6. Questions ouvertes

Aucune question fonctionnelle ou architecturale bloquante ne reste ouverte.

Les choix purement visuels fins (espacements, taille exacte des textes et représentation graphique de la progression) restent à aligner sur les composants existants de `SettingsScreen` pendant l’implémentation et ne constituent pas des décisions produit structurantes.

---

# 7. Spécification fonctionnelle

## 7.1 User stories

- En tant qu’utilisateur, je veux savoir quelle proportion de mon catalogue a été enrichie afin de vérifier que le remplissage progresse.
- En tant qu’utilisateur, je veux distinguer les médias encore à traiter des médias déjà analysés mais non trouvés afin de comprendre pourquoi le taux de liaison peut rester inférieur à 100 %.
- En tant qu’utilisateur, je veux comparer films et séries afin de voir immédiatement si un type de média est moins bien couvert.
- En tant qu’utilisateur, je veux consulter cet état sans déclencher de nouvel appel réseau ni ralentir l’application.

## 7.2 Parcours utilisateur

1. L’utilisateur ouvre les **Paramètres**.
2. Une carte **“Enrichissement des médias”** est visible avec les autres sections de paramètres.
3. La carte affiche en premier :
   - le pourcentage global de médias liés ;
   - le compteur `médias liés / médias totaux`.
4. La carte affiche séparément :
   - `Traitement : X %` ;
   - `Liés : N` ;
   - `Non résolus : N` ;
   - `À traiter : N`.
5. Un détail présente ensuite :
   - `Films : X % — N / N liés` ;
   - `Séries : X % — N / N liées`.
6. Pendant que l’utilisateur reste sur l’écran, les valeurs évoluent automatiquement si F45 traite de nouveaux médias.
7. Quitter les Paramètres arrête l’observation dédiée à cet écran ; aucune tâche supplémentaire n’est maintenue uniquement pour F46.

## 7.3 Définitions métier

### Total

Le total correspond à :

```text
nombre de films actuellement présents dans le catalogue local
+
nombre de séries actuellement présentes dans le catalogue local
```

Les chaînes Live TV, saisons et épisodes sont exclus.

### Lié

Un film ou une série est **lié** lorsqu’il possède une association acceptée vers une identité externe CSTV.

Formule :

```text
linked = nombre de médias du catalogue local ayant externalId != null
```

### Non résolu

Un média est **non résolu** lorsqu’une tentative métier de matching a bien été menée à son terme mais n’a produit aucun match suffisamment fiable.

Formule conceptuelle :

```text
unresolved =
médias du catalogue local
avec externalId == null
et une tentative de matching persistée
```

Un cooldown de revalidation future ne change pas ce statut pour la progression courante.

### Traité

Un média est **traité** lorsqu’il est soit lié, soit non résolu.

```text
processed = linked + unresolved
```

Le fait qu’un `UNRESOLVED` puisse être retenté ultérieurement ne le retire pas de `processed` tant qu’un nouveau passage n’a pas modifié son état.

### À traiter

```text
pending = total - processed
```

Cela couvre notamment :

- les médias jamais encore passés par le matching ;
- les médias dont la tentative est encore dans la file ;
- les médias dont la tentative a échoué techniquement et reste en backoff sans résultat métier persisté.

### Taux de liaison

```text
linkedPercent = linked / total * 100
```

### Taux de traitement

```text
processedPercent = processed / total * 100
```

Les mêmes règles sont appliquées séparément aux films et aux séries.

## 7.4 Règles métier

- Seuls les médias encore présents dans `vod_streams` ou `series_streams` comptent dans les statistiques.
- Une ancienne association correspondant à un média IPTV supprimé ne doit pas gonfler les compteurs.
- Les catégories masquées restent comptabilisées.
- Une même œuvre externe peut être liée à plusieurs versions IPTV : chaque ligne IPTV présente dans le catalogue compte dans le dénominateur et dans le numérateur si elle est liée.
- La propagation F45 d’un même `externalId` aux variantes locales augmente donc normalement le nombre de médias liés.
- Le taux global est calculé à partir des volumes globaux, et non par moyenne des pourcentages films/séries.
- Aucun appel backend n’est effectué pour obtenir ces statistiques.
- Aucun matching n’est déclenché par la consultation des Paramètres.
- Aucun état n’est stocké spécifiquement pour F46 : l’affichage reflète les données courantes.
- Une synchronisation IPTV peut faire baisser temporairement les taux si de nouveaux médias sont ajoutés.
- La suppression de médias peut faire évoluer les taux dans les deux sens selon l’état des médias supprimés.

## 7.5 Présentation attendue

Exemple indicatif :

```text
ENRICHISSEMENT DES MÉDIAS

91,4 % liés
49 356 / 54 000 médias

Traitement : 94,6 %

Liés          49 356
Non résolus    1 728
À traiter      2 916

Films    93,8 %   37 500 / 40 000
Séries   84,7 %   11 856 / 14 000
```

Ce contenu est indicatif : l’implémentation doit reprendre le langage visuel existant des cartes de Paramètres mobile et TV.

## 7.6 Catalogue vide

Si :

```text
movieTotal + seriesTotal == 0
```

la carte affiche :

```text
Aucun média dans le catalogue
```

Aucun pourcentage global n’est affiché.

Si seulement les films ou seulement les séries sont absents :

- le calcul global utilise normalement le type présent ;
- le type absent affiche `0 média` sans taux.

## 7.7 Mise à jour en temps réel

Lorsque l’écran des Paramètres est ouvert :

- une nouvelle association persistée par F45 doit être reflétée automatiquement ;
- un nouvel `UNRESOLVED` doit mettre à jour `processed`, `unresolved` et `pending` ;
- une synchronisation qui ajoute ou retire des films/séries doit mettre à jour le total.

Aucune fréquence de polling n’est introduite.

## 7.8 Critères d’acceptation

- Le total global est exactement égal au nombre de films + séries actuellement présents localement.
- Les chaînes Live TV, saisons et épisodes ne sont jamais comptés.
- Un média avec `externalId` est compté comme lié et traité.
- Un média avec tentative `UNRESOLVED` persistée est compté comme non résolu et traité.
- Un média jamais traité est compté dans “À traiter”.
- Un échec technique encore en backoff sans résultat métier reste dans “À traiter”.
- Un `UNRESOLVED` possédant un cooldown `retryAfter` reste compté comme traité.
- `processed = linked + unresolved`.
- `pending = total - processed`.
- `linked + unresolved + pending = total`.
- Les pourcentages globaux sont calculés depuis les compteurs globaux, sans moyenne des pourcentages par type.
- Les catégories masquées ne changent pas les statistiques.
- Supprimer un média du catalogue le retire immédiatement du calcul.
- Ajouter un média au catalogue l’ajoute immédiatement au total.
- Les valeurs se mettent à jour sans action manuelle pendant que les Paramètres sont ouverts.
- Ouvrir la carte ne provoque aucun appel réseau ni mise en file F45.
- Aucun pourcentage trompeur n’est affiché pour un catalogue vide.
- L’affichage est disponible sur mobile et Android TV.

## 7.9 Cas limites

### Association orpheline

Une ligne `external_media_links` peut survivre temporairement à une situation incohérente ou historique.

Elle ne compte jamais si le `providerId` correspondant n’est plus présent dans le catalogue local.

### Plusieurs versions IPTV d’une œuvre

Chaque média IPTV reste une unité du calcul.

Trois versions d’un même film liées au même `externalId` représentent donc trois médias liés sur trois.

### Revalidation d’un `UNRESOLVED`

Si une revalidation ultérieure trouve un match :

- `unresolved` diminue de 1 ;
- `linked` augmente de 1 ;
- `processed` reste inchangé ;
- le taux de liaison augmente ;
- le taux de traitement ne change pas.

### Nouveaux médias après 100 %

Si le catalogue était traité à 100 % puis qu’une synchronisation ajoute 100 médias :

- le total augmente immédiatement ;
- les nouveaux médias sont initialement “À traiter” ;
- le taux de traitement repasse temporairement sous 100 % ;
- F46 ne force pas leur traitement.

### Erreur technique fournisseur

Si une demande F45 échoue techniquement :

- elle reste/revient dans la file avec son backoff ;
- en l’absence de résultat métier persisté, le média reste “À traiter” ;
- F46 n’affiche pas d’erreur spécifique.

---

# 8. Spécification technique

## 8.1 Conclusion

F46 est une feature **entièrement locale à l’application**.

Aucune modification du backend CSTV n’est nécessaire.

Aucune migration Room n’est nécessaire.

F45 fournit déjà :

- `vod_streams` : catalogue local des films ;
- `series_streams` : catalogue local des séries ;
- `external_media_links` : résultat du matching par média IPTV ;
- `external_hydration_queue` : file des traitements et retries techniques.

F46 ajoute uniquement une lecture agrégée de cet état et sa présentation dans les Paramètres.

## 8.2 Modèle de domaine

Ajouter un modèle de domaine dédié, par exemple :

```kotlin
data class ExternalMetadataCoverage(
    val total: Int,
    val linked: Int,
    val unresolved: Int,
    val pending: Int,
    val movies: ExternalMetadataCoverageByKind,
    val series: ExternalMetadataCoverageByKind,
) {
    val processed: Int get() = linked + unresolved
}

data class ExternalMetadataCoverageByKind(
    val total: Int,
    val linked: Int,
    val unresolved: Int,
) {
    val processed: Int get() = linked + unresolved
    val pending: Int get() = (total - processed).coerceAtLeast(0)
}
```

Les pourcentages peuvent être calculés dans le domaine ou dans la présentation à partir des compteurs afin de ne pas stocker d’état dérivé.

Le modèle ne doit contenir aucun concept TMDB.

## 8.3 DAO Room

Étendre `ExternalMetadataDao` avec une projection agrégée unique observable.

Exemple conceptuel :

```kotlin
data class ExternalMetadataCoverageProjection(
    val movieTotal: Int,
    val movieLinked: Int,
    val movieProcessed: Int,
    val seriesTotal: Int,
    val seriesLinked: Int,
    val seriesProcessed: Int,
)
```

La requête doit :

1. compter le contenu réel de `vod_streams` et `series_streams` ;
2. joindre `external_media_links` uniquement aux médias encore présents ;
3. considérer lié :
   ```sql
   externalId IS NOT NULL
   ```
4. considérer traité :
   ```sql
   externalId IS NOT NULL OR lastMatchAttemptAt IS NOT NULL
   ```
5. ne jamais utiliser la présence dans `external_hydration_queue` comme preuve de traitement.

Une implémentation acceptable utilise des sous-requêtes scalaires ou des CTE et renvoie **une seule ligne**.

Exemple de logique SQL :

```sql
SELECT
    (SELECT COUNT(*) FROM vod_streams) AS movieTotal,

    (
        SELECT COUNT(*)
        FROM vod_streams v
        JOIN external_media_links l
          ON l.kind = 'movie'
         AND l.providerId = v.streamId
        WHERE l.externalId IS NOT NULL
    ) AS movieLinked,

    (
        SELECT COUNT(*)
        FROM vod_streams v
        JOIN external_media_links l
          ON l.kind = 'movie'
         AND l.providerId = v.streamId
        WHERE l.externalId IS NOT NULL
           OR l.lastMatchAttemptAt IS NOT NULL
    ) AS movieProcessed,

    (SELECT COUNT(*) FROM series_streams) AS seriesTotal,

    (
        SELECT COUNT(*)
        FROM series_streams s
        JOIN external_media_links l
          ON l.kind = 'series'
         AND l.providerId = s.seriesId
        WHERE l.externalId IS NOT NULL
    ) AS seriesLinked,

    (
        SELECT COUNT(*)
        FROM series_streams s
        JOIN external_media_links l
          ON l.kind = 'series'
         AND l.providerId = s.seriesId
        WHERE l.externalId IS NOT NULL
           OR l.lastMatchAttemptAt IS NOT NULL
    ) AS seriesProcessed
```

La méthode DAO doit retourner un `Flow<ExternalMetadataCoverageProjection>` afin que Room invalide automatiquement l’observation lors des modifications des tables concernées.

Exemple :

```kotlin
@Query(COVERAGE_QUERY)
fun observeCoverage(): Flow<ExternalMetadataCoverageProjection>
```

## 8.4 Pourquoi `lastMatchAttemptAt`

F45 persiste :

- un lien résolu avec `externalId != null` et `lastMatchAttemptAt` renseigné ;
- un résultat `UNRESOLVED` avec `externalId == null`, `lastMatchAttemptAt` renseigné et un `retryAfter` de cooldown ;
- un échec technique du worker en reprogrammant la ligne de `external_hydration_queue`, sans transformer cet échec en résultat `UNRESOLVED`.

Ainsi :

```text
externalId != null
    => linked + processed

externalId == null && lastMatchAttemptAt != null
    => unresolved + processed

aucun résultat de lien/tentative persisté
    => pending
```

`retryAfter` ne doit donc pas être utilisé pour décider si un média est traité.

## 8.5 Repository

Étendre `ExternalMetadataRepository` avec une lecture observable :

```kotlin
fun observeCoverage(): Flow<ExternalMetadataCoverage>
```

`ExternalMetadataRepositoryImpl` :

- observe la projection Room ;
- transforme `movieProcessed - movieLinked` en `movieUnresolved` ;
- fait de même pour les séries ;
- agrège les compteurs globaux ;
- borne défensivement les différences à `>= 0`.

Aucun appel à `CstvCatalogApiService` ne doit être effectué dans ce flux.

## 8.6 SettingsViewModel

`SettingsViewModel` injecte déjà `ExternalMetadataRepository` via Hilt une fois ajouté à son constructeur.

Au démarrage du ViewModel :

```kotlin
viewModelScope.launch {
    externalMetadataRepository.observeCoverage().collect { coverage ->
        _state.update { it.copy(externalMetadataCoverage = coverage) }
    }
}
```

La collecte vit uniquement avec le `SettingsViewModel`.

Aucun WorkManager ou timer supplémentaire n’est créé.

## 8.7 SettingsState

Ajouter :

```kotlin
val externalMetadataCoverage: ExternalMetadataCoverage? = null
```

`null` représente uniquement l’état initial avant la première émission Room.

Un catalogue réellement vide est représenté par un objet de couverture dont `total == 0`.

## 8.8 UI Paramètres

Modifier `SettingsScreen.kt` sur les deux layouts :

- `TvSettingsLayout`;
- `MobileSettingsLayout`.

Ajouter une carte cohérente avec F32 :

```text
Enrichissement des médias
```

La carte reste purement déclarative et reçoit `ExternalMetadataCoverage?`.

Aucune logique SQL ou de matching ne doit apparaître dans Compose.

### TV

Créer un composable dédié, par exemple :

```kotlin
TvExternalMetadataCoverageCard(...)
```

Il reprend :

- `Card`;
- `Surface3`;
- typographies TV existantes ;
- `MaterialTheme.colorScheme.primary` pour le titre ;
- textes secondaires gris/blanc selon les conventions existantes.

Aucun élément focalisable n’est requis puisque la carte ne contient aucune action.

### Mobile

Créer l’équivalent :

```kotlin
MobileExternalMetadataCoverageCard(...)
```

en reprenant les composants et espacements des cartes existantes du layout mobile.

## 8.9 Format des pourcentages

Ajouter une fonction pure testable :

```kotlin
fun coveragePercent(part: Int, total: Int): Double?
```

Règles :

- `total <= 0` → `null`;
- résultat borné entre `0.0` et `100.0`;
- format UI avec une décimale maximum ;
- `100.0` peut être rendu `100 %`.

Éviter de persister les pourcentages dans `SettingsState` : les compteurs sont la source de vérité.

## 8.10 Performances

Contraintes :

- aucun `SELECT *` pour F46 ;
- aucune liste de dizaines de milliers d’IDs chargée en mémoire ;
- une seule projection agrégée Room ;
- aucune boucle Kotlin sur le catalogue ;
- aucun polling ;
- aucun réseau ;
- aucun worker F46.

Les jointures utilisent les identifiants naturels déjà présents :

- `vod_streams.streamId`;
- `series_streams.seriesId`;
- clé primaire `(kind, providerId)` de `external_media_links`.

Aucun nouvel index n’est attendu.

La requête n’est active que lorsque le `SettingsViewModel` existe.

## 8.11 Synchronisation et invalidation Room

Le `Flow` doit dépendre directement des tables :

- `vod_streams`;
- `series_streams`;
- `external_media_links`.

Room réémet donc automatiquement lorsque :

- F45 lie un média ;
- F45 persiste un `UNRESOLVED`;
- une synchronisation IPTV ajoute ou supprime un média.

Les modifications de `external_hydration_queue` seules n’ont pas besoin de déclencher un recalcul si elles ne changent pas l’état métier persisté. Le compteur “À traiter” est dérivé de `total - processed`.

## 8.12 Backend

Aucun changement.

Le backend ne connaît pas le catalogue IPTV complet d’une installation et ne doit pas le connaître pour F46.

Aucune nouvelle route API n’est créée.

## 8.13 Stockage

Aucune nouvelle table.

Aucune nouvelle colonne.

Aucune migration Room.

Aucune donnée F46 n’est incluse dans les snapshots cloud.

## 8.14 Sécurité et confidentialité

Aucune donnée nouvelle n’est envoyée hors de l’appareil.

Les compteurs concernent uniquement le catalogue local déjà stocké.

F46 ne transmet ni titres, ni IDs IPTV, ni statistiques au backend.

## 8.15 Compatibilité

- Mobile : supporté.
- Android TV : supporté.
- Anciennes données F45 : si un lien résolu existe, il est compté.
- Lignes sans `lastMatchAttemptAt` et sans `externalId` : considérées non traitées, comportement conservateur.
- Aucune modification du contrat backend.
- Aucune migration de schéma.

---

# 9. Architecture

## 9.1 Flux de données

```text
vod_streams ───────────────┐
                           │
series_streams ────────────┼─> ExternalMetadataDao.observeCoverage()
                           │        │
external_media_links ──────┘        │
                                    v
                      ExternalMetadataCoverageProjection
                                    │
                                    v
                      ExternalMetadataRepositoryImpl
                                    │
                                    v
                        ExternalMetadataCoverage
                                    │
                                    v
                           SettingsViewModel
                                    │
                                    v
                             SettingsState
                                    │
                      ┌─────────────┴─────────────┐
                      v                           v
          MobileSettingsLayout           TvSettingsLayout
                      │                           │
                      v                           v
       CoverageCard mobile             CoverageCard TV
```

Aucun flux ne traverse le backend.

## 9.2 Responsabilités

### `ExternalMetadataDao`

Responsable uniquement de calculer les compteurs bruts à partir de Room.

Il ne calcule pas les textes ni le rendu.

### `ExternalMetadataRepositoryImpl`

Responsable de transformer la projection de persistance en modèle métier provider-neutral.

Il dérive :

- `unresolved`;
- `pending`;
- totaux globaux.

### `SettingsViewModel`

Responsable d’exposer la couverture courante dans l’état UI.

Il ne déclenche aucun enrichissement.

### `SettingsScreen`

Responsable uniquement du rendu.

Il dérive éventuellement la chaîne formatée du pourcentage à partir du modèle reçu.

## 9.3 Choix architecturaux

### Réutiliser `ExternalMetadataRepository`

Choix retenu plutôt que d’injecter directement `ExternalMetadataDao` dans `SettingsViewModel`.

Motifs :

- préserver la séparation présentation / data ;
- exposer un modèle provider-neutral au ViewModel ;
- éviter que l’écran connaisse le schéma F45 ;
- garder la possibilité de faire évoluer le stockage sans modifier la présentation.

### Pas de repository F46 dédié

Un nouveau `MetadataCoverageRepository` serait disproportionné pour une seule projection directement liée au domaine déjà porté par `ExternalMetadataRepository`.

### Pas de cache applicatif

Les agrégats sont simples et Room fournit déjà l’invalidation.

Un cache introduirait un risque de valeurs obsolètes sans bénéfice mesurable.

### Pas de table de statistiques

Les statistiques doivent représenter l’état courant du catalogue.

Les matérialiser créerait des problèmes de cohérence lors des syncs, suppressions et nouveaux matchs.

## 9.4 Arbitrages structurants ratifiés à l’étape 3

| Sujet | Décision |
|---|---|
| Source des statistiques | **Room local uniquement**. Le backend n’est pas sollicité et ne reçoit aucune connaissance du catalogue IPTV complet. |
| Migration | **Aucune migration** : les tables F45 existantes suffisent. |
| Calcul | Agrégation SQL directe sur les tables courantes, jamais de matérialisation du catalogue en mémoire. |
| Réactivité | `Flow` Room, sans polling. |
| Couche d’accès | Extension de `ExternalMetadataRepository`, pas d’accès DAO direct depuis `SettingsViewModel`. |
| Retry / processed | `lastMatchAttemptAt` matérialise un résultat métier déjà obtenu ; le backoff technique dans la queue sans résultat persistant reste non traité. |
| Dépendances | Aucune nouvelle bibliothèque. |
| Backend | Aucun changement de route, modèle ou stockage. |
| Cloud | Aucun snapshot ou synchronisation de cette statistique ; elle est toujours recalculée localement. |

## 9.5 Risques techniques

### Requête réémise fréquemment pendant le backfill

F45 peut insérer de nombreuses lignes dans `external_media_links`.

Room peut donc invalider le Flow fréquemment lorsque les Paramètres restent ouverts.

Mitigation :

- requête purement agrégée ;
- résultats d’une seule ligne ;
- écran rarement ouvert pendant de longues périodes ;
- `distinctUntilChanged()` possible côté repository si nécessaire.

Ne pas introduire de debounce avant mesure : la requête doit rester simple et exacte.

### Incohérence historique de données

Des données antérieures pourraient contenir une ligne partielle.

Le calcul reste conservateur :

- `externalId != null` gagne toujours : linked + processed ;
- sinon `lastMatchAttemptAt != null` : unresolved + processed ;
- sinon : pending.

### Coût du `COUNT(*)`

Les tables de référence sont de l’ordre de quelques dizaines de milliers de lignes, ce qui est adapté à une agrégation SQLite ponctuelle.

Le point de vigilance est d’éviter toute projection des colonnes complètes ou tout transfert des IDs vers Kotlin.

---

# 10. Plan de développement

## Tâche 1 — Ajouter le modèle métier de couverture

- [x] Tâche terminée

Objectif:
Créer la représentation provider-neutral des statistiques F46 et les fonctions pures de dérivation.

Fichiers:
- `app/src/main/java/com/cstv/app/domain/model/ExternalMetadataCoverage.kt` — nouveau
- `app/src/test/java/com/cstv/app/domain/model/ExternalMetadataCoverageTest.kt` — nouveau

À implémenter:
- `ExternalMetadataCoverage`;
- `ExternalMetadataCoverageByKind`;
- calcul de `processed`;
- calcul de `pending`;
- calcul/format logique du pourcentage, sans dépendance Compose.

Validation:
- `processed = linked + unresolved`;
- `pending` ne devient jamais négatif ;
- catalogue vide correctement représenté ;
- taux 0 %, intermédiaire et 100 % testés ;
- absence de vocabulaire/provider TMDB.

---

## Tâche 2 — Ajouter la projection agrégée Room

- [x] Tâche terminée

Objectif:
Calculer les compteurs films/séries en une seule lecture SQL observable.

Fichiers:
- `app/src/main/java/com/cstv/app/data/local/dao/ExternalMetadataDao.kt`
- `app/src/test/java/com/cstv/app/data/local/db/ExternalMetadataCoverageSqlTest.kt` — nouveau ou test Room équivalent selon conventions existantes

À implémenter:
- `ExternalMetadataCoverageProjection`;
- `observeCoverage(): Flow<ExternalMetadataCoverageProjection>`;
- requête jointe uniquement aux médias présents dans `vod_streams` / `series_streams`.

Validation:
Construire un jeu de données couvrant au minimum :

1. film sans lien → pending ;
2. film lié → linked + processed ;
3. film `UNRESOLVED` → unresolved + processed ;
4. série liée ;
5. série non résolue ;
6. lien orphelin vers un `providerId` absent du catalogue → ignoré ;
7. suppression d’un média du catalogue → retiré du total ;
8. `externalId == null` sans `lastMatchAttemptAt` → pending ;
9. `UNRESOLVED` avec `retryAfter` futur → processed ;
10. totaux films/séries séparés exacts.

---

## Tâche 3 — Exposer la couverture via `ExternalMetadataRepository`

- [x] Tâche terminée

Objectif:
Préserver la séparation domaine/data et convertir la projection Room en modèle F46.

Fichiers:
- `app/src/main/java/com/cstv/app/domain/repository/ExternalMetadataRepository.kt`
- `app/src/main/java/com/cstv/app/data/repository/ExternalMetadataRepositoryImpl.kt`
- `app/src/test/java/com/cstv/app/data/repository/ExternalMetadataRepositoryImplTest.kt`

À implémenter:
- `fun observeCoverage(): Flow<ExternalMetadataCoverage>`;
- mapping movie/series ;
- calcul `unresolved = processed - linked`;
- agrégation globale ;
- `distinctUntilChanged()` si utile.

Validation:
- aucun appel API dans `observeCoverage`;
- mapping correct pour tous les compteurs ;
- données incohérentes bornées défensivement ;
- émission mise à jour lorsque la projection DAO change.

---

## Tâche 4 — Intégrer la couverture au `SettingsViewModel`

- [x] Tâche terminée

Objectif:
Exposer les données F46 à l’écran des Paramètres.

Fichiers:
- `app/src/main/java/com/cstv/app/presentation/settings/SettingsViewModel.kt`
- `app/src/main/java/com/cstv/app/presentation/settings/SettingsState.kt`
- `app/src/test/java/com/cstv/app/presentation/settings/SettingsViewModelTest.kt`

À implémenter:
- injection de `ExternalMetadataRepository`;
- champ `externalMetadataCoverage` dans `SettingsState`;
- collecte du Flow dans `viewModelScope`.

Validation:
- état initial sans crash avant première émission ;
- première couverture reçue exposée dans le state ;
- mises à jour suivantes propagées ;
- aucune action F45 déclenchée par le ViewModel ;
- tests existants de `SettingsViewModel` adaptés à la nouvelle dépendance.

---

## Tâche 5 — Ajouter la carte mobile

- [x] Tâche terminée

Objectif:
Afficher F46 dans les Paramètres mobile.

Fichiers:
- `app/src/main/java/com/cstv/app/presentation/settings/SettingsScreen.kt`
- `app/src/main/res/values/strings.xml`

À implémenter:
- composable mobile dédié ;
- titre `Enrichissement des médias`;
- taux lié global ;
- compteur liés / total ;
- taux traité ;
- compteurs liés / non résolus / à traiter ;
- lignes Films / Séries ;
- état catalogue vide ;
- état de chargement initial discret.

Validation:
- aucune action utilisateur nécessaire ;
- pas de navigation additionnelle ;
- pas d’affichage `NaN`, division par zéro ou `0 %` trompeur pour catalogue vide ;
- textes issus des ressources Android ;
- cohérence visuelle avec les cartes Paramètres existantes.

---

## Tâche 6 — Ajouter la carte Android TV

- [x] Tâche terminée

Objectif:
Afficher les mêmes informations sur Android TV en respectant F32.

Fichiers:
- `app/src/main/java/com/cstv/app/presentation/settings/SettingsScreen.kt`
- `app/src/main/res/values/strings.xml`

À implémenter:
- `TvExternalMetadataCoverageCard`;
- `Card` utilisant les surfaces/typographies existantes ;
- aucun composant focalisable inutile ;
- même information fonctionnelle que mobile.

Validation:
- navigation D-pad existante inchangée ;
- la carte informative ne capture jamais le focus ;
- contenu lisible avec les dimensions TV existantes ;
- pas de régression du scroll de `TvSettingsLayout`.

---

## Tâche 7 — Tests de présentation et non-régression

- [x] Tâche terminée

Objectif:
Sécuriser les règles F46 et l’intégration aux Paramètres.

Fichiers:
- tests existants de `presentation/settings`;
- éventuels tests Compose déjà utilisés par le projet pour `SettingsScreen`.

Scénarios:
- catalogue vide ;
- 100 % linked / 100 % processed ;
- 80 % linked / 100 % processed ;
- 80 % linked / 90 % processed ;
- films à 100 %, séries à 50 % ;
- type séries absent ;
- mise à jour de la couverture pendant que l’écran est actif.

Validation:
- valeurs et libellés conformes à la spécification ;
- mobile et TV couverts au niveau adapté aux conventions de tests existantes ;
- suite de tests existante verte.

---

## Tâche 8 — Validation performance / requête

- [x] Tâche terminée

Objectif:
Vérifier que l’observation F46 n’introduit aucune lecture massive du catalogue.

Fichiers:
- aucun fichier obligatoire supplémentaire ;
- documentation dans les notes de développement si une adaptation est nécessaire.

Validation:
- aucune méthode F46 ne retourne une `List` de médias ;
- aucune boucle sur 40k/50k entrées côté Kotlin ;
- une seule ligne de projection par émission ;
- aucun appel réseau lors de l’ouverture des Paramètres ;
- aucune migration et aucun index supplémentaire sans mesure démontrant son besoin.

---

# 11. Notes de développement

Points à surveiller :

- garder la définition de “traité” alignée sur la sémantique réelle F45 ;
- ne jamais assimiler le `retryAfter` d’un `UNRESOLVED` à un échec technique encore non traité ;
- ne pas faire dépendre les statistiques des catégories masquées ;
- ne pas introduire de backend/API pour une donnée calculable localement ;
- mesurer avant toute optimisation SQL supplémentaire.

## Implémentation (étape 5)

Toutes les tâches 1 à 8 du plan de développement (§10) sont livrées :

- `ExternalMetadataCoverage`/`ExternalMetadataCoverageByKind`/`coveragePercent` dans
  `domain/model/ExternalMetadataCoverage.kt`, testés par `ExternalMetadataCoverageTest`.
- `ExternalMetadataDao.observeCoverage(): Flow<ExternalMetadataCoverageProjection>` — projection
  agrégée en une seule requête SQL (sous-requêtes scalaires, `INNER JOIN` qui exclut naturellement
  les liens orphelins). Preuve SQL indépendante de Room dans
  `data/local/db/ExternalMetadataCoverageSqlTest.kt` (10 scénarios repris de §10 Tâche 2).
- `ExternalMetadataRepositoryImpl.observeCoverage()` : mapping projection → modèle métier,
  `unresolved = processed - linked` borné à `>= 0`, `distinctUntilChanged()`. Aucun appel
  `CstvCatalogApiService`. Tests dans `ExternalMetadataRepositoryImplTest`.
- `SettingsViewModel` collecte `externalMetadataRepository.observeCoverage()` dans
  `viewModelScope` dès l'`init` ; `SettingsState.externalMetadataCoverage` (`null` = avant première
  émission Room). Tests dans `SettingsViewModelTest`.
- Cartes `TvExternalMetadataCoverageCard`/`MobileExternalMetadataCoverageCard` dans
  `SettingsScreen.kt` (langage visuel `Surface3` existant, §9.2) + chaînes dans `strings.xml`.
  Catalogue vide → `settings_external_coverage_empty`, sans pourcentage trompeur ; type absent →
  `settings_external_coverage_by_kind_row_empty` (`0 média`, sans taux).
- Formatage des pourcentages : `formatCoveragePercent` (privé, `SettingsScreen.kt`) — une décimale
  max, `100 %` sans décimale, virgule française (`Locale.FRANCE`).

Écart mineur assumé sur §10 Tâche 7 : le projet ne porte aucune infrastructure de test Compose
pour `SettingsScreen` (aucun `SettingsScreen*Test` existant avant F46) — les scénarios demandés
(catalogue vide, 100 %/100 %, 80 %/100 %, 80 %/90 %, films 100 % / séries 50 %, type absent, mise
à jour pendant que l’écran est actif) sont donc couverts au niveau domaine
(`ExternalMetadataCoverageTest`) et ViewModel (`SettingsViewModelTest`), conformément aux
conventions de tests déjà en place dans le projet plutôt qu'en introduisant un nouveau harnais de
test Compose pour cette seule feature.

Validation §10 Tâche 8 : aucune méthode F46 ne retourne de `List` de médias, aucune boucle Kotlin
sur le catalogue, une seule ligne de projection par émission, aucun appel réseau, aucune migration
Room ni index supplémentaire ajoutés.

`./gradlew testDebugUnitTest` : suite complète verte (build local du 2026-08-21).

---

# 12. Review

À compléter à l’étape 6.

## Critique

## Majeur

## Mineur

## Corrections demandées

---

# 13. Release

Version:

Commit:

Date:
