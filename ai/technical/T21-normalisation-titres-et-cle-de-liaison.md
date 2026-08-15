# T21 - Normalisation des titres, extraction des attributs et clé de liaison entre médias

## Informations générales

Status:
SPECIFICATION

Created:
2026-08-15

Dépendances:
Aucune. Fondation de F39, F40 et de l'appariement TMDB (T22).

---

# 1. Description

Les libellés renvoyés par le panel Xtream mélangent le titre de l'œuvre et des
attributs techniques : langue et version (`VF`, `VOSTFR`, `MULTI`, `TRUEFRENCH`),
qualité (`4K`, `1080p`, `HD`, `SD`), codec, marqueurs divers entre crochets ou
barres verticales. La même œuvre y apparaît plusieurs fois sous des libellés
différents, sans qu'aucun lien ne soit matérialisé entre ces entrées.

Cette tâche fait de la décomposition de ces libellés une opération de première
classe, réalisée une seule fois pendant la synchronisation du catalogue et
persistée en base :

- un **titre nettoyé**, dépourvu de tout attribut technique ;
- les **attributs extraits** (langue/version, qualité) conservés à part ;
- une **clé de liaison** qui regroupe les entrées désignant la même œuvre ou la
  même chaîne.

La tâche ne produit **aucun changement visible** dans l'interface : elle ne
fabrique que la donnée. Son exploitation appartient à F39 (étiquettes et
sélecteur de versions VOD/séries) et F40 (qualité des chaînes).

---

# 2. Contexte

Trois besoins convergent vers la même donnée manquante.

1. **Appariement TMDB.** `TitleNormalizer` (`domain/model/TitleNormalizer.kt`)
   nettoie déjà les titres à la volée, mais avec une liste de tags courte, sans
   conserver ce qu'il retire, et en recalculant à chaque appel. `TmdbCatalogMatcher`
   et `ApproximateTitleMatcher` travaillent donc sur une base à la fois imprécise
   et coûteuse : un titre nettoyé stocké et indexé rendrait l'appariement plus
   rapide et plus juste.
2. **Versions d'un même média.** Aucun lien n'existe aujourd'hui entre
   « Film X VF 1080p » et « Film X MULTI 4K ». Sans clé de liaison, un sélecteur
   de version est impossible.
3. **Variantes d'une même chaîne.** Même problème pour « TF1 FHD » / « TF1 HD » /
   « TF1 SD », qui doivent être présentées comme des qualités d'une seule chaîne.

Faire ce calcul à l'affichage a été écarté : le catalogue dépasse plusieurs
dizaines de milliers de lignes, et l'écran « Tout » a déjà fait l'objet d'une
optimisation dédiée (T9, index couvrants sur `vod_streams` et `live_streams`).
Recalculer en permanence annulerait ce travail et interdirait toute requête SQL
par clé de liaison.

---

# 3. Objectif

- Chaque film, série et chaîne du cache Room porte un titre nettoyé, ses
  attributs extraits et une clé de liaison, calculés à la synchronisation.
- Deux entrées désignant la même œuvre ou la même chaîne partagent la même clé
  de liaison ; deux œuvres distinctes ne la partagent pas.
- L'appariement TMDB s'appuie sur le titre nettoyé stocké, sans recalcul.
- Le catalogue déjà en cache est mis à niveau sans resynchronisation réseau ni
  perte des données hors ligne.
- Aucun changement de comportement observable pour l'utilisateur.

---

# 4. Décisions produit prises à l'étape 1

| Sujet | Décision |
|---|---|
| Emplacement du calcul | Dans l'application, pendant la synchronisation du catalogue ; résultat persisté en base. Ni à la volée à l'affichage, ni côté backend (le catalogue IPTV ne quitte jamais l'appareil). |
| Portée V1 | Données uniquement. Aucune étiquette, aucun filtre, aucun regroupement de vignettes dans cette tâche. |
| Clé de liaison films/séries | Titre nettoyé seul ; deux entrées sont **séparées** si leurs années de sortie sont toutes deux connues et différentes. L'année absente n'empêche pas le regroupement. |
| Clé de liaison chaînes | Retrait des seuls marqueurs de qualité connus (`HD`, `FHD`, `UHD`, `4K`, `SD`, `1080p`…). « TF1 Séries Films » reste distincte de « TF1 ». Pas de regroupement agressif (préfixes pays, numéros, suffixes libres). |
| Reprise de l'existant | Recalcul en base pendant la migration Room, au premier lancement après mise à jour. Pas de colonnes vides en attente de synchronisation, pas de resynchronisation complète forcée. |
| Plateformes | Sans objet (aucune surface UI). |
| Ordre de livraison | Premier ticket du lot, avant F39 et F40. |

## Décisions produit prises à l'étape 2

| Sujet | Décision |
|---|---|
| Attribut brut | Le fragment brut d'origine de chaque attribut détecté est conservé en plus de sa valeur normalisée (coût de stockage négligeable, évite un retour au libellé source complet si un affichage futur veut un badge fidèle au marqueur d'origine). |
| Titre vide après nettoyage | Le libellé original complet est conservé comme titre nettoyé (comportement défensif, aucune perte de donnée ; l'entrée n'est simplement reliée à aucune autre par la clé de liaison). |

---

# 5. Hypothèses

- Les attributs utiles se déduisent du seul libellé : le panel n'expose aucun
  champ structuré de langue ou de qualité (à confirmer sur `get_live_streams`,
  `get_vod_streams`, `get_series`).
- Le vocabulaire des marqueurs est fini et énumérable pour ce panel ; une liste
  fermée, enrichie au fil des observations, suffit. Un modèle probabiliste n'est
  pas nécessaire.
- Le nombre de versions par œuvre reste faible (quelques unités), donc une
  requête par clé de liaison reste peu coûteuse.
- Le recalcul complet en migration reste dans une durée acceptable au démarrage
  sur box Android TV. À mesurer à l'étape 3 ; si ce n'est pas le cas, un
  traitement en tâche de fond avec indicateur de progression sera arbitré.
- Le catalogue conserve des libellés stables entre deux synchronisations : la
  clé de liaison d'une entrée ne change pas d'une synchro à l'autre.

---

# 6. Questions ouvertes

| Question | À trancher à l'étape |
|---|---|
| Les colonnes ajoutées doivent-elles être indexées dès la V1, ou seulement quand F39/F40 les interrogeront ? | 3 |
| Le recalcul en migration se fait-il en SQL pur ou en Kotlin (lecture/écriture par lots) ? | 3 |
| `TitleNormalizer` est-il étendu ou remplacé par un nouveau composant, et que devient l'appariement TMDB existant ? | 3 |
| La clé de liaison films/séries est-elle une valeur unique par entrée (titre seul, transitive, avec le filtrage par année appliqué uniquement à la lecture par F39/F40), ou une valeur qui encode l'année quand elle est connue (groupes strictement séparés en base, mais alors une entrée sans année ne rejoint plus naturellement les deux) ? La règle produit « année absente n'empêche pas le regroupement » n'est pas transitive quand une entrée sans année sert de pont entre deux années différentes — impacte le modèle de données (voir 7.4). | 3 |
| Comment départager deux marqueurs de la même catégorie présents simultanément dans un libellé (ex. un flux étiqueté à la fois « HD » et « 4K ») : le plus qualitatif prime-t-il toujours, ou est-ce un signe de libellé mal formé à traiter autrement ? | 3 |

---

# 7. Spécification fonctionnelle

## 7.1 Résultat attendu

T21 ne modifie aucun écran : le résultat se constate uniquement en base et dans
le comportement interne de la synchronisation et de l'appariement TMDB. Le
« parcours utilisateur » de cette tâche est le pipeline de synchronisation du
catalogue lui-même, pas une interaction visible.

## 7.2 Pipeline de traitement

Le calcul a lieu à deux moments :

1. **Synchronisation courante** : pour chaque entrée reçue via
   `get_live_streams`, `get_vod_streams`, `get_series`, avant persistance en
   base Room.
2. **Reprise de l'existant** : pendant la migration Room qui suit la mise à
   jour de l'application, pour chaque entrée déjà en cache — sans appel
   réseau (décision étape 1).

Pour chaque libellé source, dans cet ordre :

1. Détection des marqueurs connus (langue/version, qualité — voir 7.3), quelle
   que soit leur position dans le libellé et leur casse, délimités par
   crochets, parenthèses, barres verticales, tirets ou simples espaces.
2. Retrait de ces marqueurs pour obtenir le titre nettoyé ; normalisation des
   espaces multiples et de la ponctuation résiduelle laissée par le retrait
   (crochets ou tirets orphelins, espaces en double).
3. Stockage du titre nettoyé, de la valeur normalisée de chaque attribut
   détecté, du fragment brut d'origine de chaque attribut (décision étape 2),
   et de la clé de liaison calculée.

## 7.3 Règles métier — extraction des attributs

- **Langue/version** (liste fermée, insensible à la casse) : `VF`, `VFQ`,
  `VFF`, `VOSTFR`, `VOST`, `VO`, `MULTI`, `TRUEFRENCH`, `SUBFRENCH`. Liste
  enrichie au fil des observations sur le catalogue réel (hypothèse étape 1).
- **Qualité** (liste fermée, insensible à la casse) : `4K`, `UHD`, `2160p`,
  `FHD`, `1080p`, `HD`, `720p`, `SD`.
- Chaque catégorie retient au plus une valeur par entrée. En cas de plusieurs
  marqueurs de la même catégorie dans un même libellé, le plus qualitatif
  prime par défaut (`4K` avant `HD`) — comportement exact à confirmer étape 3
  (voir Questions ouvertes).

## 7.4 Règles métier — clé de liaison films/séries

- Base : titre nettoyé normalisé pour la comparaison (minuscules, accents
  supprimés, espaces multiples réduits), afin de tolérer les variations de
  casse et d'accentuation entre panels.
- Séparation par année : deux entrées de même titre nettoyé partagent la clé
  de liaison sauf si elles ont toutes les deux une année connue et que ces
  années diffèrent. Une entrée sans année connue ne bloque jamais le
  regroupement avec une entrée qui en a une.
- Cette règle n'est pas nécessairement transitive (une entrée sans année peut
  être compatible avec deux entrées d'années différentes qui, elles, sont
  incompatibles entre elles) : accepté comme hypothèse, modèle de stockage
  exact renvoyé à l'étape 3 (voir Questions ouvertes).

## 7.5 Règles métier — clé de liaison chaînes

- Retrait des seuls marqueurs de qualité (`HD`, `FHD`, `UHD`, `4K`, `SD`,
  `1080p`…) du nom de la chaîne pour obtenir la clé ; aucun autre retrait
  (pas de préfixe pays, numérotation, suffixes libres) — décision étape 1
  explicite : « TF1 Séries Films » reste distinct de « TF1 ».
- Comparaison insensible à la casse et aux espaces superflus.

## 7.6 Cas limites

- **Titre vide après nettoyage** : le libellé original complet est conservé
  comme titre nettoyé (décision étape 2) ; la clé de liaison qui en découle
  peut ne rassembler que cette seule entrée, ce qui est le comportement
  attendu, pas une erreur.
- **Aucun attribut détecté** : titre nettoyé identique au libellé source,
  attributs vides, entrée fonctionnellement inchangée par rapport à
  aujourd'hui.
- **Doublons stricts** (même libellé exact présent deux fois dans le
  catalogue) : même titre nettoyé, même clé — comportement attendu, pas un
  cas particulier à gérer.
- **Marqueur faisant partie du titre légitime d'une œuvre** (ex. un titre
  contenant littéralement « HD » ou « 4K ») : risque accepté comme hypothèse
  étape 1 (liste fermée plutôt que modèle probabiliste) ; aucune correction
  algorithmique prévue en V1, à surveiller en usage réel.

## 7.7 Critères d'acceptation

- Pour un échantillon de libellés représentatif du catalogue réel (tests
  unitaires avec cas volontairement « sales », conformément à AGENTS.md
  § Stratégie de tests), le titre nettoyé ne contient plus aucun marqueur de
  la liste fermée.
- Deux entrées désignant la même œuvre sous des libellés différents (ex.
  « Film X VF 1080p » et « Film X MULTI 4K ») partagent la même clé de
  liaison.
- Deux œuvres distinctes de titres différents ne partagent jamais la même
  clé.
- Deux entrées de même titre nettoyé mais années connues différentes ne sont
  jamais proposées comme versions l'une de l'autre par les fonctionnalités
  consommatrices (F39, F40).
- Le catalogue déjà en cache est recalculé pendant la migration Room sans
  appel réseau ni perte des favoris, de l'historique, des positions de
  lecture ou des téléchargements existants.
- L'appariement TMDB (`TmdbCatalogMatcher`, `ApproximateTitleMatcher`)
  consomme le titre nettoyé stocké sans le recalculer à chaque appel.

## 7.8 Gestion des erreurs

- Libellé source `null` ou vide : traité comme les autres champs manquants du
  parsing Xtream (AGENTS.md § Conventions de code) — titre nettoyé vide, pas
  de crash.
- Échec du calcul sur une entrée pendant la migration : n'interrompt pas la
  migration des autres entrées ; l'entrée en échec se comporte comme le cas
  « titre vide » (libellé source conservé tel quel, clé de liaison qui lui
  est propre).

---

# 8. Spécification technique

_À compléter — étape 3._

---

# 9. Architecture

_À compléter — étape 3._

---

# 10. Plan de développement

_À compléter — étape 4._

---

# 11. Notes de développement

---

# 12. Review

## Critique

## Majeur

## Mineur

## Corrections demandées

---

# 13. Release

Version :

Commit :

Date :
