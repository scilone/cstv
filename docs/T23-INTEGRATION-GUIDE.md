# T23 — Autoréparation du lecteur : Guide d'intégration F39/F40/F41

**Status:** VALIDATED (2026-08-17)  
**Commit:** 86c607c  
**Version:** v1.86.0

---

## Résumé T23

Quand la lecture d'un média échoue pour une cause de décodage (piste/codec), le lecteur tente automatiquement :
1. Bascule sur décodeur logiciel FFmpeg (NextLib)
2. Désactivation de la piste fautive
3. Sélection d'une autre piste audio

La configuration gagnante est mémorisée par `mediaUid` (appareil + fichier), réappliquée aux lectures suivantes.

Timeouts : 8s/essai, 24s séquence max. Succès confirmé après 3s stabilité sans erreur renderer.

---

## Points d'extension pour F39/F40/F41

Deux seams posés dans `PlaybackRecoveryCoordinator.kt`, sans logique réelle :

### 1. **F40 (Variante fallback)** — Notifiant d'épuisement

Interface : `PlaybackRecoveryExhaustionListener` (fun interface)

```kotlin
fun interface PlaybackRecoveryExhaustionListener {
    fun onRecoveryExhausted(kind: MediaKind, providerId: String)
}
```

**Branchement** : `PlaybackRecoveryCoordinator.recoverFromDecodingFailure(...)` prend paramètre optionnel `exhaustionListener: PlaybackRecoveryExhaustionListener? = null`

**Quand :** Notifié **exactement une fois** quand la séquence s'épuise sur une chaîne live (`kind == MediaKind.CHANNEL`), jamais sur VOD/série.

**Ne pas confondre :**
- `SourceFailure` (réseau, live-window, unknown) → **N'appelle jamais** exhaustionListener
- `DecoderExhausted` (3 essais de décodage échoués) → **Appelle** exhaustionListener (Live seulement)

**F39 rollback** : `PlaybackRecoveryCoordinator` expose `val outcome: StateFlow<RecoveryOutcome>` — F39 peut y écouter `FinalFailure` pour déclencher son rollback, mais celui-ci n'intervient qu'après épuisement T23, jamais au premier renderer error.

---

### 2. **F41 (Position tampon live)** — Fournisseur de position

Interface : `LiveBufferPositionProvider` (fun interface)

```kotlin
fun interface LiveBufferPositionProvider {
    fun bufferPositionMs(kind: MediaKind, providerId: String): Long?
}
```

**Branchement** : `PlaybackRecoveryCoordinator.liveRestorePositionMs(kind, providerId, provider)` accepte un fournisseur optionnel

**Quand :** F41 fournit une position de tampon pour une chaîne → utilisée en priorité sur le live edge lors d'une restauration pendant T23.

**Défaut** : Si provider omis ou retourne `null`, le direct reprend au live edge (comportement existant couvert par T23 seul).

---

## Fichiers clés pour l'intégration

| Fichier | Rôle |
|---------|------|
| `PlaybackRecoveryCoordinator.kt` | Machine d'états + interfaces extension |
| `PlaybackFailureClassifier.kt` | Qualification erreur (décodage vs réseau vs unknown) |
| `PlaybackRecoverySession.kt` | Gestion job/génération (évite races inter-médias) |
| `PlaybackRepairProfileEntity` + DAO | Persistance config par `mediaUid` |
| `ExoPlaybackRecoveryEngine.kt` | Adapter Media3 (non testable JVM, interface `PlaybackRecoveryEngine` JVM-testable) |

---

## Contrats invariants T23

1. **Pas de persévérance en réseau** : une erreur réseau/live-window/unknown pendant un essai **arrête la séquence immédiatement**, ne la remplace pas par un faux « appareil incompatible ».

2. **Profil mémorisé essayé en premier** : à chaque ouverture de média avec profil en base, `initialPlan()` le retourne ; s'il échoue, il est supprimé (mise à jour système, changement pistes) et la séquence repart de DEFAULT.

3. **Mémorisation = appareil + fichier** : `mediaUid` encode `(accountKey, kind, providerId)` — pas de `profileId`, pas de sync cloud, partagé tous profils locaux.

4. **Un seul ExoPlayer actif** : reconstruction toujours libère l'ancienne instance avant créer la nouvelle (§8.7 AGENTS.md).

5. **Aucun jargon utilisateur** : messages finaux sans « décodeur », « FFmpeg », « piste », « codec ».

---

## Points manuels non automatisés (validation device requis)

- **Désactivation piste étape 2** : Media3 n'expose pas d'exclusion pure. Implémentation actuelle traite étape 2 et 3 proche (sélection autre audio dans les deux cas). À vérifier sur device connu pour échouer en décodage audio.

- **Live edge après récupération** : `seekTo(0)` sur direct saute au début tampon, pas au live. T23 utilise `C.TIME_UNSET` en sentinelle pour ne pas seek sur live ; vérifier reprise au live edge correcte après essai.

---

## Tests JVM couverts (aucun device requis)

- Classification erreurs Media3 (AGENTS.md § Stratégie de tests)
- Machine états (3 étapes, timeouts 8s/essai et 24s séquence)
- Profil mémorisé appliqué → échoue → supprimé
- Changement média pendant récupération (annulation, génération monotonique)
- Retry repart de zéro (plan DEFAULT)
- Débits SourceFailure/LiveWindow vs DecoderExhausted (exhaustionListener appelé ou non)

Aucun test n'exige device/émulateur/Robolectric (AGENTS.md, tâche 7).

---

## Version, migration, dépendances

**Version DB :** 30 → 31 (création table `playback_repair_profiles`)

**Migration SQL :** `MIGRATION_30_31` dans `Migrations.kt` — lue via `addMigrations()` dans `AppModule.provideDatabase()`.

**Nouvelles dépendances :** aucune. Media3 et NextLib déjà présents suffisent.

**Gradle :** `./gradlew assembleDebug`, `./gradlew testDebugUnitTest`, `./gradlew lintDebug` — tous verts.

---

## Checklist intégration F39/F40/F41

- [ ] **F39** : lire `PlaybackRecoveryCoordinator.outcome` (StateFlow<RecoveryOutcome>) pour rollback après FinalFailure T23
- [ ] **F40** : branche `exhaustionListener` dans `PlayerScreen.kt` Live (§8.6), passer listener au coordinateur
- [ ] **F41** : branche `LiveBufferPositionProvider` dans `liveRestorePositionMs()` (défaut : nil, live edge utilisé)
- [ ] Tous tests JVM verts avant livraison F39/F40/F41
- [ ] Validation device : épisode/chaîne connus échouer en décodage audio → essais T23 visibles en logs → succès sans msg erreur
