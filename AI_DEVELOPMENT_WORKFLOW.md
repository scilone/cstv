# AI Development Workflow

## Objectif

Ce document définit le workflow standard de création, analyse, développement, validation et livraison des éléments du projet.

Ce workflow doit être utilisé pour toute évolution du projet :

- nouvelle fonctionnalité ;
- correction de bug ;
- amélioration technique ;
- refactoring ;
- évolution d'architecture.

Chaque élément est représenté par un fichier Markdown unique qui constitue sa source de vérité.

---

# Organisation des éléments

Les éléments sont organisés dans le dossier :

```
ai/

├── features/
├── bugs/
├── technical/
```

Chaque catégorie possède un dossier `archive/` contenant les éléments terminés.

Structure :

```
ai/

├── features/
│   ├── F1-recommendations.md
│   └── archive/
│
├── bugs/
│   ├── B1-player-freeze.md
│   └── archive/
│
└── technical/
    ├── T3-cache-refactor.md
    └── archive/
```

---

# Identifiants

Chaque élément possède un identifiant unique et permanent, sans zéros de remplissage.

Formats :

```
Fx = Feature (ex: F1, F2, F12...)

Bx = Bug (ex: B1, B2, B12...)

Tx = Technical (ex: T1, T2, T12...)
```

Exemples :

```
F1-recommendations.md

B1-player-freeze.md

T3-cache-refactor.md
```

L'identifiant ne doit jamais changer.

Le nom descriptif peut évoluer, mais l'identifiant reste identique.

---

# Source de vérité

Le fichier Markdown associé à l'élément contient toutes les informations nécessaires :

- contexte ;
- objectif ;
- spécifications fonctionnelles ;
- spécifications techniques ;
- architecture ;
- décisions prises ;
- tâches ;
- notes de développement ;
- review ;
- informations de livraison.

Aucune information importante ne doit exister uniquement dans une conversation avec une IA.

---

# Questions interactives (règle transverse)

Un ticket ne se devine pas : il se négocie. Avant de rédiger ou de compléter une
étape, l'agent **doit** poser ses questions à l'utilisateur plutôt que de choisir
seul et de présenter le résultat comme acquis.

## Principe

L'agent ne remplit une section qu'avec des décisions soit **explicitement
validées** par l'utilisateur, soit **triviales et réversibles**. Toute décision
qui engage le produit, le périmètre, l'expérience utilisateur ou une contrainte
technique structurante passe par une question.

## Format attendu

Les questions doivent être posées **de manière interactive**, sous forme de
choix fermés, dès que l'outillage le permet (outil de questions à choix
multiples de l'agent). À défaut, une liste numérotée de questions avec options
explicites dans la réponse.

Règles de formulation :

- **Par lots**, jamais une par une : regrouper jusqu'à 4 questions par salve
  pour éviter les allers-retours.
- **2 à 4 options par question**, mutuellement exclusives, jamais de question
  ouverte quand un choix fermé est possible.
- **L'option recommandée en premier**, suffixée `(Recommandé)`, avec la
  justification du choix.
- **Chaque option décrit sa conséquence**, y compris ses inconvénients : le rôle
  de l'agent est d'éclairer un arbitrage, pas de vendre sa préférence.
- **Vocabulaire du produit**, pas du code : les questions d'étape 1 et 2 doivent
  être compréhensibles sans lire le dépôt.

## Quand poser les questions

| Étape | Objet des questions |
|---|---|
| 1 - Création | Comportement attendu, périmètre, cas de conflit, mode dégradé, ce qui est explicitement hors sujet. |
| 2 - Spécification fonctionnelle | Parcours utilisateur, règles métier ambiguës, formulation des messages, cas limites. |
| 3 - Technique et architecture | Arbitrages structurants uniquement (dépendance nouvelle, migration de schéma, compromis performance/complexité). Les choix internes relèvent de l'agent. |
| 4 - Découpage | Ordre de livraison et découpage en lots seulement si plusieurs stratégies sont défendables. |
| 6 à 8 - Review, correction, validation | Arbitrage sur les problèmes classés `Majeur` ou `Mineur` dont la correction élargirait le périmètre. |

## Traçabilité

Les réponses obtenues sont **immédiatement reportées dans le fichier de
l'élément**, dans une section dédiée :

```markdown
# Décisions produit prises à l'étape X

| Sujet | Décision |
|---|---|
| ... | ... |
```

Une décision qui reste uniquement dans la conversation est considérée comme
perdue. Les points laissés en suspens vont dans `Questions ouvertes`, avec
l'étape à laquelle ils devront être tranchés.

## Limite

Ne pas transformer chaque étape en interrogatoire. Ne pas poser de question dont
la réponse est déjà écrite dans le dépôt (`AGENTS.md`, fiche de l'élément, code
existant), ni sur un point sans conséquence observable. En cas d'exécution non
interactive (aucune réponse possible), l'agent avance avec l'option qu'il aurait
recommandée, l'inscrit comme **hypothèse** explicite dans le fichier et la
signale dans sa réponse.

---

# Structure d'un fichier élément

Chaque élément doit suivre cette structure :

```markdown
# F1 - Nom de l'élément

## Informations générales

Status:
IDEA

Created:
YYYY-MM-DD

---

# 1. Description

Description générale du besoin.

---

# 2. Contexte

Pourquoi cet élément existe.

Quel problème il résout.

---

# 3. Objectif

Ce que l'élément doit obtenir, exprimé en résultats, pas en solutions.

---

# 4. Décisions produit prises à l'étape X

| Sujet | Décision |
|---|---|
| ... | ... |

Réponses obtenues auprès de l'utilisateur (voir *Questions interactives*).
Une ligne par arbitrage tranché, complétée au fil des étapes.

---

# 5. Hypothèses

Ce que l'élément tient pour acquis, et qui invaliderait la conception si c'était faux.

---

# 6. Questions ouvertes

Points non tranchés, avec l'étape à laquelle ils devront l'être.

---

# 7. Spécification fonctionnelle

Décrire :

- comportement attendu ;
- parcours utilisateur ;
- règles métier ;
- critères d'acceptation ;
- cas limites ;
- gestion des erreurs.

---

# 8. Spécification technique

Décrire :

- composants impactés ;
- nouveaux composants ;
- modèles de données ;
- API ;
- services ;
- stockage ;
- cache ;
- performances ;
- sécurité ;
- compatibilité.

---

# 9. Architecture

Décrire :

- architecture proposée ;
- flux de données ;
- responsabilités des composants ;
- décisions techniques.

---

# 10. Plan de développement

Liste des tâches :

- [ ] Task 1
- [ ] Task 2
- [ ] Task 3

---

# 11. Notes de développement

Historique des décisions prises pendant l'implémentation.

---

# 12. Review

Résultats des revues.

## Critique

## Majeur

## Mineur

## Corrections demandées

---

# 13. Release

Version :

Commit :

Date :
```

---

# Cycle de vie

Chaque élément suit le cycle suivant :

```
IDEA
 ↓
ANALYSIS
 ↓
SPECIFICATION
 ↓
ARCHITECTURE
 ↓
TASK BREAKDOWN
 ↓
IMPLEMENTATION
 ↓
REVIEW
 ↓
FIXES
 ↓
VALIDATION
 ↓
DOCUMENTATION
 ↓
RELEASE
 ↓
ARCHIVE
```

---

<details>
<summary><b>Étape 1 - Création et structuration</b></summary>

## Objectif

Transformer une idée ou un problème brut en élément exploitable.

## Actions

**Commencer par interroger l'utilisateur**, de manière interactive, avant
d'écrire la moindre ligne du ticket (voir *Questions interactives*). Une idée
brute contient toujours des arbitrages implicites : comportement en cas de
conflit, périmètre exact, mode dégradé, ce qui est volontairement exclu.
L'agent explore d'abord le dépôt pour ne poser que des questions utiles, puis
pose ses questions par lots de choix fermés.

Rédiger le fichier seulement une fois les réponses obtenues, et y reporter les
décisions dans une section `Décisions produit prises à l'étape 1`.

Créer un fichier :

```
ai/{category}/{id}-{name}.md
```

Exemples :

```
ai/features/F1-recommendations.md

ai/bugs/B1-player-freeze.md

ai/technical/T3-cache-refactor.md
```

Compléter :

- description ;
- contexte ;
- objectif ;
- décisions produit prises à l'étape 1 ;
- hypothèses ;
- questions ouvertes.

Ne pas définir d'architecture technique.

## Modèle recommandé

Gemini 3.5 Flash / Haiku 4.5

</details>

---

<details>
<summary><b>Étape 2 - Spécification fonctionnelle</b></summary>

## Objectif

Décrire précisément le comportement attendu.

## Actions

Compléter :

```
Spécification fonctionnelle
```

Ajouter :

- user stories ;
- parcours utilisateur ;
- règles métier ;
- critères d'acceptation ;
- cas limites ;
- gestion des erreurs.

La spécification doit permettre à une personne externe au projet de comprendre exactement le résultat attendu.

Traiter d'abord les `Questions ouvertes` laissées par l'étape 1 : chacune doit
être posée à l'utilisateur de manière interactive (voir *Questions
interactives*), puis soit tranchée dans les `Décisions produit`, soit reportée
explicitement à l'étape 3 avec sa raison. Toute nouvelle ambiguïté découverte en
rédigeant la spécification suit le même chemin.

## Modèle recommandé

Sonnet 5 / GPT 5.6-Terra

</details>

---

<details>
<summary><b>Étape 3 - Spécification technique et architecture</b></summary>

## Objectif

Définir la solution technique.

## Actions

Compléter :

- spécification technique ;
- architecture ;
- composants impactés ;
- nouveaux composants ;
- choix techniques.

Les décisions doivent être justifiées.

Cette étape doit identifier :

- les fichiers qui seront modifiés ;
- les dépendances nécessaires ;
- les risques techniques ;
- les contraintes de performance.

Les arbitrages **structurants** se posent à l'utilisateur de manière interactive
avant d'être actés : nouvelle dépendance, migration de schéma Room ou backend,
nouvelle surface d'API, compromis entre performance et complexité, tout choix
difficile à revenir en arrière. Les choix internes sans conséquence observable
restent à la main de l'agent : ils n'ont pas à être soumis.

## Modèle recommandé

GPT 5.6-Sol / Opus 4.8

</details>

---

<details>
<summary><b>Étape 4 - Découpage des tâches</b></summary>

## Objectif

Transformer la conception en tâches exécutables.

## Actions

Créer une liste de tâches.

Chaque tâche doit :

- avoir un objectif clair ;
- être indépendante ;
- être réalisable en une session ;
- préciser les fichiers concernés ;
- préciser les critères de validation.

Exemple :

```markdown
- [ ] Créer RecommendationRepository

Objectif:
Créer la couche d'accès aux recommandations.

Fichiers:
- RecommendationRepository.kt

Validation:
Tests unitaires présents.
```

## Modèle recommandé

Sonnet 5 / GPT 5.6-Terra

</details>

---

<details>
<summary><b>Étape 5 - Implémentation</b></summary>

## Objectif

Développer l'élément.

## Actions

Pour chaque tâche :

1. Lire le fichier complet de l'élément.
2. Comprendre les décisions existantes.
3. Implémenter uniquement la tâche demandée.
4. Respecter l'architecture définie.
5. Ajouter les tests nécessaires.
6. Vérifier les tests existants.
7. Mettre à jour le statut des tâches.

Format :

```
- [x] Tâche terminée
```

Ne pas modifier le périmètre sans validation.

## Modèle recommandé

GPT 5.6-Terra / Sonnet 5

</details>

---

<details>
<summary><b>Étape 6 - Review technique</b></summary>

## Objectif

Analyser la qualité de l'implémentation.

## Actions

Effectuer une revue complète :

- architecture ;
- qualité du code ;
- performances ;
- sécurité ;
- tests ;
- maintenabilité ;
- dette technique ;
- edge cases ;
- cohérence avec les spécifications.

Ne pas modifier le code.

Ajouter les résultats dans :

```
Review
```

Classer les problèmes :

```
Critique

Majeur

Mineur
```

Chaque problème doit contenir :

- description ;
- impact ;
- correction attendue.

## Modèle recommandé

Opus 4.8 / GPT 5.6-Sol

</details>

---

<details>
<summary><b>Étape 7 - Correction</b></summary>

## Objectif

Appliquer les corrections issues de la review.

## Actions

Il est impératif de traiter et corriger l'ensemble des retours listés dans la review de l'étape 6, y compris ceux classés en **Mineur**.

Pour chaque problème :

- corriger ;
- ajouter ou modifier les tests ;
- vérifier la non-régression.

Mettre à jour la review :

```
Status: RESOLVED
```

## Modèle recommandé

GPT 5.6-Terra / Sonnet 5

</details>

---

<details>
<summary><b>Étape 8 - Validation finale</b></summary>

## Objectif

Vérifier que l'élément répond au besoin initial.

Vérifier :

- comportement attendu ;
- règles métier ;
- expérience utilisateur ;
- qualité technique ;
- absence de régression ;
- tests validés.

Mettre à jour :

```
Status: VALIDATED
```

## Modèle recommandé

Sonnet 5 / GPT 5.6-Sol

</details>

---

<details>
<summary><b>Étape 9 - Documentation</b></summary>

## Objectif

Mettre à jour la documentation globale.

Mettre à jour selon le besoin :

```
docs/

├── features.md
├── architecture.md
├── user-guide.md
└── changelog.md
```

Ajouter :

- nouvelle fonctionnalité ;
- correction utilisateur visible ;
- changement architectural ;
- migration ;
- impact technique.

## Modèle recommandé

Haiku 4.5 / Gemini 3.5 Flash

</details>

---

<details>
<summary><b>Étape 10 - Livraison Git et Compilation</b></summary>

## Objectif

Publier officiellement l'élément et compiler l'APK signé final de production.

Avant livraison :

- toutes les tâches sont terminées ;
- tous les tests passent ;
- documentation à jour.

## Actions

1. Stager les fichiers modifiés, créés et documentés :
   ```bash
   git add <fichiers>
   ```
2. Créer le commit en respectant les conventions Gitmoji :
   ```bash
   git commit -m ":emoji: type(scope): description (ID)"
   ```
3. Exécuter le script de release locale pour compiler l'APK signé de production, créer le tag Git et pousser automatiquement vers le dépôt distant :
   ```bash
   ./scripts/release-local.sh
   ```
   *Note : Le script compile l'APK, vérifie sa signature, met à jour automatiquement l'APK à la racine (releases/app-release.apk), amende votre dernier commit pour y inclure cet APK de façon transparente, pose le tag localement, effectue les pushs et crée la Release GitHub avec l'APK attaché.*
4. Mettre à jour le statut dans la fiche de l'élément à `RELEASED`, avec la version, le nom du commit/tag, et la date du jour.
5. Archiver la fiche de l'élément en la déplaçant vers son sous-dossier `archive/`.

</details>

---

# Convention Git

Les commits utilisent Gitmoji.

Format :

```
:emoji: description
```

Exemples :

Nouvelle fonctionnalité :

```
✨ Add movie recommendations
```

Bug :

```
🐛 Fix player freeze
```

Refactoring :

```
♻️ Refactor recommendation architecture
```

Documentation :

```
📝 Update documentation
```

Tests :

```
✅ Add recommendation tests
```

Performance :

```
⚡ Improve cache performance
```

Dépendances :

```
⬆️ Update dependencies
```

---

# Versioning SemVer

Les versions utilisent :

```
MAJOR.MINOR.PATCH
```

Format du tag :

```
v1.5.0
```

---

## MAJOR

Modification incompatible.

Exemple :

```
v1.5.0 → v2.0.0
```

---

## MINOR

Nouvelle fonctionnalité compatible.

Exemple :

```
v1.4.0 → v1.5.0
```

---

## PATCH

Correction ou amélioration mineure.

Exemple :

```
v1.5.0 → v1.5.1
```

---

# Finalisation

Créer le commit :

```
git commit
```

Créer le tag :

```
git tag vX.Y.Z
```

Publier :

```
git push
git push --tags
```

Mettre à jour le fichier :

```markdown
Status:
RELEASED

Version:
vX.Y.Z

Date:
YYYY-MM-DD
```

Déplacer ensuite le fichier dans :

```
ai/{category}/archive/
```

---

# Commandes d'utilisation

Les demandes doivent suivre ce format :

```
Exécute l'étape X de {ID}
```

Exemples :

```
Exécute l'étape 1 de F1.

Exécute l'étape 3 de B2.

Exécute l'étape 5 de T3.
```

---

# Règles générales

L'agent doit toujours :

1. Lire le fichier correspondant à l'identifiant demandé.
2. Identifier l'état actuel.
3. Exécuter uniquement l'étape demandée.
4. Mettre à jour le fichier concerné.
5. Respecter les décisions déjà prises.
6. Signaler les incohérences avant modification.
7. Ne jamais modifier le périmètre sans validation.
8. Poser directement des questions à l'utilisateur en cas de doute, d'ambiguïté ou de questionnement sur les choix fonctionnels ou techniques, afin de valider l'alignement avant d'agir. Ces questions se posent **de manière interactive et par lots de choix fermés**, selon la section *Questions interactives* : c'est le mode de fonctionnement par défaut, pas un recours exceptionnel. Une décision produit ne s'invente jamais en silence.
9. Veiller à ce que tous les tests requis pour valider une tâche soient entièrement automatisés (tests unitaires s'exécutant localement via `./gradlew testDebugUnitTest`). Si une tâche ou une vérification requiert un appareil physique connecté (device), un émulateur en cours d'exécution, ou un test utilisateur manuel, elle ne doit pas être prise en compte et doit être ignorée des critères de validation de l'agent.
10. Si un ticket fait l'objet de retours (feedback ou corrections demandées par l'utilisateur), traiter et implémenter ces retours directement dans le cadre du ticket d'origine sans passer par la création d'un nouveau ticket, à moins que l'agent n'estime que l'ampleur des retours soit trop importante et qu'il soit préférable de créer un nouveau ticket dédié.
11. Dans le cas d'un hotfix (correction d'anomalie urgente en production), il est impératif d'effectuer directement les opérations de commit, push et création du tag Git correspondant (SemVer PATCH vX.Y.PATCH) pour assurer la livraison et le déploiement immédiats de la correction.
