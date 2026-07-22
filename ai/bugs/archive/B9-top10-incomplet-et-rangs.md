# B9 - Top 10 incomplet et présentation des rangs

## Informations générales

Type: Bug

Status: RELEASED

Created: 2026-07-22

---

# 1. Description

Après la livraison de F9, les sections Top 10 Films et Top 10 Séries peuvent contenir moins de dix médias lorsque plusieurs titres de la première page TMDB ne correspondent pas au catalogue local. La présentation du rang ne correspond pas non plus à la référence visuelle : le chiffre actuel ressemble à un badge opaque au lieu d'un grand numéro détouré placé derrière l'affiche.

# 2. Contexte

F9 ne récupère que les 20 résultats de la page 1 de `/movie/popular` et `/tv/popular`. Le matching peut donc épuiser cette liste avant d'obtenir dix correspondances locales. La référence visuelle est conservée dans `docs/design-reference/screenshots/top-ten.jpeg`.

# 3. Spécification fonctionnelle

- Récupérer les 50 premiers médias populaires TMDB pour chaque type.
- Rechercher ces candidats dans l'ordre de popularité et conserver les dix premières correspondances locales.
- Conserver un résultat inférieur à dix si les 50 candidats ne suffisent réellement pas.
- Afficher le rang sous forme de grand chiffre noir détouré en blanc, à gauche et derrière le poster.
- Ne pas modifier les cartes hors sections Top 10, les clics, le tactile ou la navigation D-pad.

# 4. Spécification technique

- Charger les pages TMDB 1 à 3 en parallèle, concaténer dans l'ordre des pages, puis limiter la liste à 50 candidats avant mapping.
- Incrémenter la version des clés de cache Popular afin d'invalider les correspondances calculées sur l'ancienne fenêtre de 20 titres.
- Conserver la limite finale `take(10)` dans `GetPopularTop10InCatalogUseCase`.
- Réserver un espace à gauche des posters classés et superposer deux textes Compose : contour blanc puis remplissage noir.
- Adapter la largeur au rang 10 afin d'éviter la troncature du nombre à deux chiffres.

# 5. Architecture

Le flux F9 existant est conservé : `TmdbApiService` → `PopularRepositoryImpl` → `GetPopularTop10InCatalogUseCase` → `HomeScreen`. Le correctif porte uniquement sur la fenêtre de candidats dans le repository et le rendu des cartes Home classées.

# 6. Plan de développement

- [x] Étendre Popular aux trois premières pages et limiter à 50 candidats.
- [x] Invalider les anciennes entrées de cache Popular.
- [x] Ajouter un test de pagination, d'ordre et de limite à 50.
- [x] Recomposer les cartes Top 10 selon la référence visuelle.
- [x] Exécuter la validation globale.
- [x] Livrer la version patch et archiver le ticket.

# 7. Notes de développement

- 2026-07-22 — Les trois pages sont demandées concurremment pour ne pas tripler la latence réseau, puis consommées dans leur ordre numérique.
- 2026-07-22 — Les clés de cache passent de `v1` à `v2`; aucune donnée utilisateur n'est supprimée.
- 2026-07-22 — Le badge avec fond dégradé est remplacé par un chiffre détouré derrière un poster décalé à droite, conformément à la capture de référence.

# 8. Review

Status: RESOLVED

- Aucun défaut critique ou majeur restant après revue locale.
- La largeur et la taille typographique du rang 10 sont adaptées séparément pour éviter sa troncature.
- Les cartes sans rang conservent leurs dimensions et leur rendu précédents.

# 9. Validation

Status: VALIDATED (automated) / DEVICE PENDING

- 2026-07-22 — `./gradlew --no-daemon testDebugUnitTest assembleDebug lintDebug` : `BUILD SUCCESSFUL`.
- 2026-07-22 — Test ajouté : les pages 1 à 3 sont demandées, l'ordre est conservé et seuls les 50 premiers candidats sont retournés.
- 2026-07-22 — Aucun appareil ou émulateur ADB connecté : la comparaison visuelle sur matériel avec `top-ten.jpeg` reste à effectuer par le PO.

# 10. Release

Version: v1.50.1

Commit:

Date: 2026-07-22
