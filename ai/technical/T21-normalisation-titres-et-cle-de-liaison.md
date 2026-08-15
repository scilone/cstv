# T21 - Normalisation des titres, extraction des attributs et clé de liaison entre médias

## Informations générales

Status:
ANALYSIS

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
| Faut-il conserver l'attribut brut extrait (chaîne de caractères d'origine) en plus de sa valeur normalisée, pour affichage ultérieur ? | 2 |
| Comment traiter un titre dont le retrait des attributs laisse une chaîne vide ou d'un seul caractère ? | 2 |
| Les colonnes ajoutées doivent-elles être indexées dès la V1, ou seulement quand F39/F40 les interrogeront ? | 3 |
| Le recalcul en migration se fait-il en SQL pur ou en Kotlin (lecture/écriture par lots) ? | 3 |
| `TitleNormalizer` est-il étendu ou remplacé par un nouveau composant, et que devient l'appariement TMDB existant ? | 3 |

---

# 7. Spécification fonctionnelle

_À compléter — étape 2._

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
