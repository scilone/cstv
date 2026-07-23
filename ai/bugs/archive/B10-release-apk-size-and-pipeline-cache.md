# B10 - Régression taille APK release et cache pipeline

## Informations générales

Type:
Bug

Status:
RELEASED

Created:
2026-07-23

---

# 1. Description

Entre `v1.50.1` et `v1.51.0`, l'APK release universel est passé d'environ
8,32 Mo à 13,4 Mo. Les pipelines de release ont également montré une
dégradation importante, notamment entre `v1.47.19` et `v1.47.20`.

---

# 2. Contexte

`v1.51.0` a ajouté les ABI `x86` et `x86_64` à la configuration commune.
L'APK release embarque donc quatre variantes des bibliothèques natives
FFmpeg/NextLib au lieu des deux variantes ARM nécessaires aux appareils
Android ciblés.

La CI autorisait uniquement l'écriture du cache Gradle sur `main`, alors que
la compilation release est conditionnée aux tags. Les builds qui produisent
réellement l'APK ne pouvaient donc pas sauvegarder leur cache.

Le tag `v1.51.0` a par ailleurs été créé alors que `versionName` et
`versionCode` correspondaient encore à `v1.50.1`.

---

# 3. Spécification fonctionnelle

- L'APK release doit cibler `armeabi-v7a` et `arm64-v8a`.
- Les builds debug doivent conserver `x86` et `x86_64` pour les émulateurs.
- Les builds de tags doivent pouvoir lire et écrire le cache Gradle.
- Une release doit échouer avant compilation si le tag, `versionName` et
  `versionCode` ne correspondent pas.

---

# 4. Spécification technique

- Déplacer les filtres ABI dans les types de build `debug` et `release`.
- Laisser `gradle/actions/setup-gradle` gérer son cache en lecture/écriture.
- Ajouter une étape Bash de validation SemVer avant le décodage du keystore.

---

# 5. Architecture

Aucun changement applicatif. La correction reste limitée à la configuration
Android et au workflow de release.

---

# 6. Plan de développement

- [x] Limiter les ABI release aux architectures ARM.
- [x] Conserver les quatre ABI pour les builds debug.
- [x] Réactiver l'écriture du cache sur les builds de tags.
- [x] Ajouter le contrôle tag/version.
- [x] Valider les builds, tests et lint.
- [x] Vérifier la composition et la taille de l'APK release.

---

# 7. Notes de développement

Les fichiers `log-1-47-19.txt` et `log-1-47-20.txt` restent des fichiers
locaux non suivis et ne font pas partie de la livraison.

Validation locale :

- `testDebugUnitTest`, `assembleDebug` et `lintDebug` réussis ;
- `assembleRelease` réussi avec un keystore temporaire de validation ;
- APK release obtenu : 8,4 Mo ;
- seules les ABI `armeabi-v7a` et `arm64-v8a` sont présentes.

---

# 8. Review

## Critique

Aucun.

## Majeur

Aucun.

## Mineur

Aucun.

## Corrections demandées

Aucune après contrôle du diff et de l'APK généré.

---

# 9. Release

Version : v1.53.2

Commit : 58a7078

Date : 2026-07-23
