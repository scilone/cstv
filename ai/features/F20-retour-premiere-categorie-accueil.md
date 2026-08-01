# F20 - Retour à l'affichage de la première catégorie pour les Films et Séries sur l'Accueil

## Informations générales

Status:
SPECIFICATION

Created:
2026-08-02

---

# 1. Description

Actuellement, sur l'Accueil, les sections dédiées aux **Films (VOD)** et aux **Séries** affichent les 20 derniers ajouts (les nouveautés de l'ensemble du catalogue, triées par date d'ajout décroissante).

L'objectif de cette évolution est de revenir à la version antérieure de l'Accueil : les rangées de Films et de Séries doivent à nouveau présenter le contenu de leur **première catégorie** respective (au lieu des derniers ajouts). La logique d'affichage de la TV en direct (qui présente déjà sa première catégorie) reste inchangée.

---

# 2. Contexte

La modification vers les "derniers ajouts" avait été introduite par le commit `f691c80` (Feature #2 / v1.25.0) pour remplacer le chargement de la première catégorie.

Depuis lors, l'application a grandement évolué (notamment avec la Phase 58 et les préférences de profil) :
* L'ordre des catégories et les catégories masquées sont désormais gérés de façon dynamique et persistés par profil via `CategoryPreferenceRepository`.
* Des Use Cases dédiés (`GetVodCategoriesUseCase` et `GetSeriesCategoriesUseCase`) existent aujourd'hui pour récupérer et trier les catégories d'un profil de façon réactive.
* Utiliser ces Use Cases pour charger le contenu de la première catégorie de Films et Séries permettra de respecter automatiquement les préférences de tri et d'affichage (catégories masquées) du profil actif.

---

# 3. Spécification fonctionnelle

## Objectif

Sur l'Accueil (mobile et TV), remplacer la rangée "Derniers ajouts" des Films et des Séries par une rangée affichant les médias de leur toute première catégorie disponible (non masquée et triée selon les préférences de l'utilisateur).

## User stories

* **En tant qu'utilisateur (mobile et TV)**, lorsque j'ouvre l'Accueil, la section "Films" affiche les films appartenant à ma première catégorie préférée de VOD (ex: "Action" si c'est la première catégorie active de mon profil), plutôt que les nouveautés globales.
* **En tant qu'utilisateur (mobile et TV)**, lorsque j'ouvre l'Accueil, la section "Séries" affiche les séries appartenant à ma première catégorie préférée de Séries, au lieu des nouveautés globales.
* **En tant qu'utilisateur**, si je modifie l'ordre de mes catégories ou que je masque certaines catégories dans les paramètres de mon profil, la Home s'actualise pour présenter le contenu de la nouvelle "première" catégorie active.

## Règles métier et d'interaction

1. **Identification de la première catégorie** :
   * Pour les Films (VOD), récupérer les catégories via `GetVodCategoriesUseCase.once()`. Prendre la première catégorie de la liste obtenue (qui exclut déjà les catégories masquées et respecte l'ordre personnalisé du profil).
   * Pour les Séries, faire de même via `GetSeriesCategoriesUseCase.once()`.
2. **Chargement du contenu** :
   * Si une première catégorie est identifiée, charger ses flux associés depuis le cache local (`vodRepository.getCachedVodStreams(categoryId)` / `seriesRepository.getCachedSeriesStreams(categoryId)`).
   * Si aucune catégorie n'est disponible (ex: toutes masquées ou catalogue vide), la section correspondante sur la Home reste vide ou masquée.
3. **Limite d'éléments** :
   * Conserver la limite d'affichage standard de la rangée sur l'Accueil (ex: 20 éléments).

## Critères d'acceptation (Fonctionnels)

- [ ] Sur l'Accueil, la section Films affiche les films de la première catégorie triée et visible du profil.
- [ ] Sur l'Accueil, la section Séries affiche les séries de la première catégorie triée et visible du profil.
- [ ] Modifier l'ordre des catégories ou masquer la première catégorie actuelle dans les Paramètres met bien à jour la Home avec la nouvelle première catégorie lors du retour sur l'Accueil.
- [ ] Le lien "Voir tout" à côté de ces sections redirige correctement vers la catégorie concernée.

## Décisions de conception et choix utilisateur

* **Format du titre de la section** :
  * **Décision (Validée par l'utilisateur)** : **Option B (Titres génériques)**. Conserver les titres de section génériques actuels (`"Films"`, `"Séries"`), même si le contenu affiché est celui de la première catégorie (ce qui est parfaitement cohérent avec la TV en direct qui affiche sa première catégorie sous le titre générique `"TV en direct"`). Les fichiers de ressources `strings.xml` ne seront donc pas altérés pour cette partie.

---

# 4. Spécification technique

*(À compléter à l'Étape 3)*

---

# 5. Architecture

*(À compléter à l'Étape 3)*

---

# 6. Plan de développement

*(À compléter à l'Étape 4)*

---

# 7. Notes de développement

*(À remplir au fil du développement)*

---

# 8. Review

*(À remplir à l'Étape 6)*

---

# 9. Release

*(À remplir à l'Étape 10)*
