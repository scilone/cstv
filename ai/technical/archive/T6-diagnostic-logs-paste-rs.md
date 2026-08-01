# T6 - Diagnostic Logs and Plantage Analyzer with Paste.rs

## Informations générales

Type:
Technical

Status:
RELEASED (v1.64.9 - 2026-07-28)

Created:
2026-07-28

---

# 1. Description

Certains utilisateurs rencontrent des crashs inopinés de l'application sur Android TV, suspectés d'être liés à des fuites de mémoire (Memory Leaks) ou à un épuisement du tas de la JVM (OutOfMemoryError). Sur Android TV, le débogage est complexe car l'accès classique aux fichiers locaux et aux outils de capture (ADB/USB) n'est pas possible pour l'utilisateur final.

Cette évolution technique vise à introduire un **système de diagnostic complet et autonome** qui :
1. Enregistre localement les logs de l'application.
2. Écoute et enregistre périodiquement l'état de la mémoire JVM.
3. Attrape et persiste les traces de pile (Stacktraces) des crashs au moment de l'extinction afin qu'ils soient disponibles au redémarrage suivant.
4. Permet l'envoi anonymisé de ce rapport de diagnostic vers le service d'hébergement sécurisé de texte brut `paste.rs` via une simple action utilisateur dans les paramètres.
5. Affiche un **QR Code** généré à la volée sur la TV afin que l'utilisateur puisse facilement scanner l'URL avec son smartphone pour la copier ou la transmettre pour analyse.

---

# 2. Contexte

* **Logging de l'app** : Centralisé dans l'objet pur `IptvLog` (dans `com.cstv.app.di`). Cet objet délègue aujourd'hui directement aux méthodes statiques de `android.util.Log` (ou imprime sur la console standard dans les tests JVM).
* **Paramètres de l'app** : Gérés par l'écran `SettingsScreen` (dans `com.cstv.app.presentation.settings`), son `SettingsViewModel` et persistés par le singleton `SettingsManager`.
* **Identifiants IPTV** : Stockés de manière sécurisée par le singleton `CredentialsManager`. Ces identifiants et URLs de serveurs d'origine doivent être strictement anonymisés pour préserver la vie privée et la sécurité des utilisateurs avant tout téléversement en ligne.

---

# 3. Spécification fonctionnelle et Objectifs

## Objectifs
* Permettre la capture continue des évènements de log (`IptvLog`) et des métriques de mémoire JVM de l'application.
* Permettre la conservation automatique des détails de crash d'un fil d'exécution (Thread) non intercepté à travers les redémarrages de l'application.
* Fournir une option d'extraction locale sécurisée, simple et ultra-robuste pour Android TV sans exiger d'application tierce (comme un client mail ou le partage Bluetooth qui font défaut sur TV).

## User stories
* En tant qu'utilisateur subissant des plantages de l'application sur ma TV, je veux pouvoir activer un "Mode Debug" pour enregistrer l'activité de l'application.
* En tant qu'utilisateur, je veux pouvoir exporter un diagnostic de manière anonyme et sécurisée en affichant simplement un QR code sur ma TV pour le flasher avec mon smartphone.

## Parcours utilisateur
1. L'utilisateur ouvre les **Paramètres** de l'application.
2. Une nouvelle section **"DIAGNOSTIC & LOGS"** est présente sous les réglages des sous-titres.
3. L'utilisateur y trouve un interrupteur (Toggle) pour activer/désactiver le **"Mode Debug"** (désactivé par défaut pour économiser l'espace disque et la mémoire).
4. Lorsque le Mode Debug est actif :
   - L'application écrit tous les logs dans un fichier local tampon circulaire `app_debug_log.txt` (limité à 2000 lignes ou ~1 Mo).
   - Les métriques de mémoire sont prélevées périodiquement.
   - En cas de crash, l'application écrit la stacktrace et l'état mémoire dans `crash_log.txt` avant de s'éteindre.
5. L'utilisateur clique sur **"Extraire les logs de diagnostic"** :
   - L'application compile les données : Informations techniques de l'appareil (modèle, version Android), état actuel de la mémoire, logs d'activité récents, et la trace du dernier crash si disponible.
   - L'application nettoie (anonymise) scrupuleusement les données (redact des identifiants et domaines IPTV).
   - L'application envoie ce rapport via une requête POST brute sécurisée vers `https://paste.rs`.
   - En cas de succès, un dialogue s'ouvre sur la TV avec l'adresse finale (`https://paste.rs/xxxx`) et un **QR Code** de 250x250 pixels pointant vers ce lien.
   - L'utilisateur scanne le QR code avec l'appareil photo de son téléphone et copie ou transmet le texte brut des logs pour analyse.

## Règles métier
1. **Désactivé par défaut** : Le logging de diagnostic sur fichier est désactivé par défaut. Son activation est persistée dans `SettingsManager`.
2. **Anonymisation absolue** : Toute URL, mot de passe (`password`), nom d'utilisateur (`username`) ou hôte (`host`) d'origine IPTV doit être masqué dans le rapport final (remplacé par `[REDACTED_USER]`, `[REDACTED_PASS]`, `[REDACTED_HOST]`).
3. **Limite de taille** : Le fichier de logs debug ne doit jamais saturer le stockage de l'appareil. Le tampon de logs en mémoire ou sur fichier doit être limité à un historique circulaire de 1000 lignes maximum.
4. **Gestion du cycle de vie** : L'activation du Mode Debug à chaud commence immédiatement à intercepter les logs sans nécessiter de redémarrage. Sa désactivation purge immédiatement le fichier temporaire local et libère la mémoire.
5. **Formatage du QR Code** : Le QR code est généré dynamiquement via une URL de l'API de graphiques de Google (`https://chart.googleapis.com/chart?chs=250x250&cht=qr&chl=<URL>`) affichée de manière asynchrone par `Coil`.

## Cas limites
* **Absence de réseau** : Si la TV n'a pas d'accès Internet lors de la demande de partage, un message d'erreur clair "Pas de connexion réseau pour téléverser les logs" est affiché.
* **Service paste.rs indisponible ou en échec (Rate-limiting)** : Si le service renvoie une erreur ou est inaccessible, l'application affiche un message d'erreur clair à l'utilisateur : "Échec de l'envoi des logs à paste.rs. Veuillez réessayer ultérieurement."
* **Fichier de logs inexistant** : Si l'utilisateur clique sur "Extraire les logs" alors qu'aucun log n'a été enregistré (ou juste après l'activation), le rapport n'inclut que les informations système de l'appareil et l'état RAM.

---

# 4. Spécification technique

## 4.1 Décisions techniques

| # | Décision | Justification |
|---|----------|---------------|
| D1 | **`DiagnosticManager` sous forme de Singleton thread-safe** | Centralise l'enregistrement circulaire en mémoire, la détection de la RAM, la lecture/écriture des fichiers `app_debug_log.txt` et `crash_log.txt`, et le call d'envoi OkHttp vers `paste.rs`. |
| D2 | **Écouteur de logs dans `IptvLog`** | Ajout d'une propriété de callback mutable `var logListener: ((level: String, tag: String, message: String, throwable: Throwable?) -> Unit)?` dans l'object `IptvLog`. `DiagnosticManager` s'y enregistre s'il est actif. Cela évite d'introduire des dépendances de la couche présentation/data vers le fichier global `IptvLog`. |
| D3 | **Interception automatique des crashs au démarrage** | Armement d'un `UncaughtExceptionHandler` personnalisé au démarrage de `IptvApplication`. Si le Mode Debug est actif, il sérialise l'exception dans `crash_log.txt` et appelle l'ancien handler système. |
| D4 | **Sanitization via `CredentialsManager`** | Récupération à la volée des credentials actifs via `CredentialsManager` pour nettoyer le texte final du rapport. Le traitement utilise des regex de filtrage d'URL robustes en plus des remplacements exacts des chaînes. |
| D5 | **OkHttpClient réutilisé** | L'envoi vers `paste.rs` utilise directement l'instance standard d'OkHttp de l'application ou un client direct pour ne pas perturber l'instance de Retrofit Xtream. |
| D6 | **Rapport auto-complet et structuré** | Le rapport généré en texte brut contient des sections claires délimitées par des séparateurs markdown (`---`) : Infos App/Appareil, Métriques de mémoire, Logs récents, Trace du dernier crash (si disponible). |

## 4.2 Structure du rapport de diagnostic final

```
======================================================================
IPTV APPLICATION DIAGNOSTIC REPORT
======================================================================
Generated at: 2026-07-28 12:40:00

--- SYSTEM INFO ---
App VersionName: 1.64.8 (16408)
Android Version: 11 (API 30)
Device Brand: Xiaomi
Device Model: MiTV-MOS0
Available Processors: 4

--- JVM MEMORY STATE ---
Max Memory (Heap Limit): 192.00 MB
Total Memory (Allocated): 42.50 MB
Free Memory in Allocated: 12.30 MB
Used Memory: 30.20 MB (22.1% of Heap Limit)

--- LAST CRASH (PREVIOUS RUN) ---
[Si présent dans crash_log.txt]
Crash Time: 2026-07-28 12:35:12
Uncaught Exception: java.lang.NullPointerException: Attempt to invoke virtual method ...
    at com.cstv.app.presentation.player.PlayerViewModel.leakMethod(...)
    ...

--- ACTIVITY LOGS ---
[D 12:38:00] [IptvApplication] scheduleDefaultBackgroundSync
[I 12:38:05] [AuthRepository] Attempting automatic login...
[D 12:38:08] [OkHttp] --> POST http://[REDACTED_HOST]/player_api.php?username=[REDACTED_USER]&password=[REDACTED_PASS]
[D 12:38:09] [OkHttp] <-- 200 OK (1100ms)
[D 12:38:10] [LiveTvViewModel] Loading 25 channels for category 12
...
======================================================================
```

---

# 5. Architecture

## 5.1 Position dans le flux

```
SettingsScreen (Presentation)
  └─ SettingsViewModel
       ├─ Toggle Mode Debug ➔ SettingsManager (Local storage)
       │                         └─ DiagnosticManager.setLoggingEnabled(true/false)
       └─ Clic "Extraire les logs" ➔ SettingsViewModel (State: Loading)
            └─ DiagnosticManager.exportAndUploadLogs(context) ➔ returns String (URL)
                 ├─ Compilation des infos (System, Memory, Logs, Last Crash)
                 ├─ Nettoyage avec CredentialsManager (Anonymisation)
                 ├─ Requête OkHttp POST brute vers https://paste.rs
                 └─ Retourne URL ou lance une Exception
```

## 5.2 Composants impactés et à créer

**Nouveaux composants**
1. `com.cstv.app.data.util.DiagnosticManager` :
   - Singleton chargé de l'enregistrement des lignes de log et de l'envoi vers `paste.rs`.
   - Gère le fichier `app_debug_log.txt` et `crash_log.txt` dans le cache de l'app.
   - Enregistre périodiquement les métriques mémoires et le matériel.

**Modifiés**
1. `com.cstv.app.di.IptvLog` :
   - Ajout d'une propriété callback mutable pour notifier `DiagnosticManager` de chaque log écrit.
2. `com.cstv.app.IptvApplication` :
   - Armement de l'intercepteur de crashs non capturés au démarrage.
   - Initialisation du mode diagnostic si persisté à "vrai" dans les préférences.
3. `com.cstv.app.data.local.storage.SettingsManager` :
   - Ajout des fonctions `getDebugModeEnabled(): Boolean` et `setDebugModeEnabled(Boolean)`.
4. `com.cstv.app.presentation.settings.SettingsState` :
   - Ajout de `debugModeEnabled: Boolean`, `isUploadingLogs: Boolean`, `uploadedLogsUrl: String?`, et `uploadLogsError: String?`.
5. `com.cstv.app.presentation.settings.SettingsViewModel` :
   - Méthodes pour toggler le mode debug et lancer l'upload des logs de manière asynchrone sur le `viewModelScope`.
6. `com.cstv.app.presentation.settings.SettingsScreen` :
   - Ajout visuel d'une carte "Diagnostic & Logs" dans la mise en page TV et Mobile.
   - Affichage d'un dialogue d'upload en cours, de succès avec l'URL + le QR Code (Coil) et d'erreur.

---

# 6. Plan de développement

- [x] **Tâche 1 : Ajout de la clé de persistance dans `SettingsManager`**
  - Ajouter `KEY_DEBUG_MODE_ENABLED` dans les préférences.
  - Implémenter les accesseurs correspondants dans `SettingsManager`.
  - Écrire un test unitaire unitaire pour vérifier la persistance de cette clé.

- [x] **Tâche 2 : Extension de `IptvLog` avec écouteur d'évènements**
  - Ajouter un callback thread-safe `logListener` dans `IptvLog`.
  - Appeler ce listener dans `d()`, `w()`, `e()`.

- [x] **Tâche 3 : Création de `DiagnosticManager`**
  - Gérer un tampon de log circulaire thread-safe.
  - Gérer l'écriture dans `app_debug_log.txt` de manière asynchrone pour ne pas ralentir le thread principal.
  - Implémenter la capture de l'état mémoire JVM.
  - Implémenter l'anonymisation rigoureuse via `CredentialsManager` (username, password, serveurs IPTV).
  - Implémenter la méthode d'envoi asynchrone vers `https://paste.rs` via `OkHttpClient`.

- [x] **Tâche 4 : Interception globale des crashs**
  - Configurer `Thread.setDefaultUncaughtExceptionHandler` au sein de `IptvApplication`.
  - Écrire les traces dans `crash_log.txt` si le mode débug est activé.
  - Initialiser `DiagnosticManager` à l'allumage de l'app si configuré.

- [x] **Tâche 5 : Mise à jour du ViewModel de Settings**
  - Ajouter les champs nécessaires dans `SettingsState`.
  - Gérer la bascule à chaud du mode debug et l'upload asynchrone.
  - Écrire des tests unitaires locaux pour valider le comportement du ViewModel et l'interaction avec `DiagnosticManager` (mocké).

- [x] **Tâche 6 : Intégration de l'UI dans `SettingsScreen`**
  - Ajouter un panneau dédié dans `TvSettingsLayout` et `MobileSettingsLayout`.
  - Présenter l'option sous forme de carte interactive avec un bouton d'envoi.
  - Gérer l'affichage d'un dialogue modal contenant le QR Code généré via Coil (`https://chart.googleapis.com/chart?chs=250x250&cht=qr&chl=URL_PASTE`) et un bouton de fermeture.

- [x] **Tâche 7 : Validation technique globale**
  - Vérifier la compilation de l'application via `./gradlew assembleDebug`.
  - Exécuter les tests unitaires via `./gradlew testDebugUnitTest`.
  - S'assurer que le linting est vierge d'erreurs via `./gradlew lintDebug`.
