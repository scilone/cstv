Phase 18 [TERMINE] : fluidifier le lancement de l'app en supprimant l'affichage
inutile de l'écran de connexion.

Au démarrage de l'app, si la case "se souvenir de moi" a été cochée lors
d'une connexion précédente ET qu'une connexion a déjà été établie avec
succès (identifiants valides stockés), ne pas afficher l'écran de
connexion : aller directement à l'écran d'accueil (Home), comme si
l'utilisateur venait de se connecter.

Attendu :
- Vérification au lancement (avant tout affichage d'écran) de la
  présence d'identifiants stockés et de l'état "se souvenir de moi".
- Si les deux conditions sont réunies, saut direct vers la Home, sans
  passer par l'écran de connexion (pas de flash de l'écran de connexion
  avant redirection).
- Si les identifiants stockés sont invalides ou expirés (le serveur
  rejette la connexion silencieuse), rediriger proprement vers l'écran
  de connexion avec un message d'erreur explicite plutôt que de rester
  bloqué sur un écran vide ou un chargement infini.
- Si "se souvenir de moi" n'a pas été coché, ou qu'aucune connexion
  n'a jamais été établie, le comportement actuel (affichage de l'écran
  de connexion) reste inchangé.
- Prévoir un écran de chargement (splash) pendant cette vérification,
  pour éviter tout affichage transitoire de l'écran de connexion ou de
  la Home avant que l'état soit confirmé.

---

Phase 19 [TERMINE] : correction du bug de recherche par acteur/réalisateur (voir
Phase 15).

La recherche par acteur/réalisateur ne retourne que le film/série depuis
lequel on a cliqué sur l'acteur/réalisateur, alors que d'autres films/
séries du catalogue partagent ce même acteur/réalisateur.

Cause probable : le casting d'un film/série n'est indexé/stocké en cache
Room qu'au moment où l'utilisateur visite sa fiche détail
(get_vod_info/get_series_info), pas au moment du chargement de la
liste/catégorie (get_vod_streams/get_series, qui ne fournissent
généralement pas le casting).

Attendu :
- La recherche par acteur/réalisateur doit matcher sur l'ensemble du
  catalogue déjà en cache, indépendamment des fiches détail visitées ou
  non par l'utilisateur.
- Si le casting n'est réellement disponible qu'via l'appel détail et pas
  dans les endpoints de liste, revoir la stratégie de cache : récupérer/
  mettre à jour le casting en arrière-plan pour tout le catalogue (par
  exemple par lot, au premier chargement ou via un job dédié), pas
  seulement au moment de la visite d'une fiche. Ne te contente pas d'un
  correctif cosmétique qui masquerait le problème sans le résoudre.
- Vérifie que le dédoublonnage des résultats (Phase 15) reste correct
  une fois le casting indexé plus largement.

---

Phase 20 [TERMINE] : préservation de la position de défilement dans les listes
(Films/Séries/TV, voir Phase 14) au retour depuis une fiche détail.

Quand l'utilisateur visite une fiche détail film/série/chaîne puis fait
un retour arrière, il doit retrouver exactement sa position de
défilement dans la liste/grille d'où il vient.

Attendu :
- Restaurer la position de défilement vertical exacte de la liste/
  grille au retour (pas juste revenir en haut de la liste).
- Dans le mode filtre "Tout" (sections horizontales par catégorie,
  Phase 14), restaurer en plus la position de défilement horizontal de
  la section précise depuis laquelle l'utilisateur est parti.
- Ce comportement doit s'appliquer à tous les écrans de liste concernés :
  Films, Séries, TV, et Home (sections de la Phase 6).
- Ne conserve la position que pour un retour arrière réel vers la même
  liste ; un nouvel accès à l'écran (ex: changement d'onglet puis retour)
  peut repartir de zéro si c'est le comportement Compose Navigation
  standard déjà en place depuis la Phase 7.

---

Phase 21 : suggestions en autocomplétion sur la recherche globale.

Attendu :
- Pendant la saisie dans le champ de recherche globale (avant même de
  valider), afficher une liste de suggestions sous forme de dropdown.
- Suggestions basées uniquement sur le cache Room local (titres de
  films/séries/chaînes, éventuellement acteurs/réalisateurs comme pour
  la recherche enrichie de la Phase 15) — aucun appel réseau.
- Mise à jour des suggestions en temps réel à chaque frappe.
- Cliquer/sélectionner (D-pad + OK sur TV) une suggestion doit soit
  amener directement à sa fiche détail (si la suggestion est un élément
  précis), soit lancer la recherche complète sur ce terme.
- Limiter le nombre de suggestions affichées (ex: 5 à 10) pour rester
  lisible et performant, avec une requête Room adaptée plutôt qu'un
  filtre en mémoire sur tout le catalogue à chaque frappe (même
  contrainte de performance que la Phase 15).

---

Phase 22 [TERMINE] : rafraîchissement automatique en arrière-plan de la base de
données locale, avec fréquence réglable.

Attendu :
- Ajouter dans l'écran Paramètres (Phase 5) un réglage de fréquence de
  rafraîchissement automatique du cache Room (catégories/chaînes/films/
  séries) : quotidien / hebdomadaire / mensuel.
- Mettre en place un rafraîchissement automatique en arrière-plan
  respectant cette fréquence (WorkManager ou équivalent), sans bloquer
  ni interrompre l'utilisateur en cours d'usage de l'app.
- Ce rafraîchissement automatique est indépendant du rafraîchissement
  manuel (pull-to-refresh) déjà existant depuis la Phase 2 : les deux
  mécanismes doivent cohabiter sans conflit.
- Si l'app n'est pas lancée à l'échéance prévue, le rafraîchissement doit
  se déclencher au prochain lancement si la fréquence choisie est
  dépassée (comportement standard de WorkManager avec contrainte de
  réseau disponible).

---

Phase 23 [TERMINE] : revoir les icônes TV/Films/Séries de la barre de navigation
mobile (Phase 7).

Les icônes actuelles de ces trois onglets ne sont pas satisfaisantes
visuellement. Les icônes Accueil et Recherche restent inchangées (déjà
correctes) : ne retoucher que TV/Films/Séries, en gardant une cohérence
de style (même famille d'icônes, même poids visuel) avec les deux
icônes conservées.

---

Phase 24 [TERMINE] : correction d'accessibilité sur les boutons "voir tout" de la
Home (Phase 6).

Le contraste actuel de ces chips les rend peu lisibles.

Attendu (au choix, selon ce qui rend le mieux) :
- Soit donner à la chip un fond avec un contraste suffisant par rapport
  au texte (respecter les ratios de contraste d'accessibilité standard,
  WCAG AA minimum).
- Soit remplacer la chip par un simple lien texte stylé de façon à
  rester lisible sur toutes les Home (attention aux thèmes clair/sombre
  si l'app en gère) et clairement identifiable comme cliquable/focusable
  au D-pad sur TV.
- Dans tous les cas, éviter tout texte clair sur fond clair ou texte
  sombre sur fond sombre.

---

Phase 25 [TERMINE] : correction d'un chevauchement visuel dans le player film/
série (Phase 16) en orientation portrait/verticale.

Quand le titre du média est long, les boutons "paramètres" (audio/
sous-titres) et "fermer" de l'overlay se chevauchent.

Attendu :
- Corriger la mise en page de l'overlay pour que le titre soit tronqué
  (ellipsis) ou passe sur plusieurs lignes limitées, sans jamais
  recouvrir ces deux boutons, quelle que soit la longueur du titre.
- Vérifie aussi le comportement en mode paysage/TV pour t'assurer de ne
  rien casser sur ces affichages qui fonctionnaient déjà correctement.

---

Phase 26 [TERMINE] : personnalisation de l'apparence des sous-titres du player
film/série (Phase 16), réglable depuis les Paramètres.

Attendu :
- Ajouter dans l'écran Paramètres (Phase 5) une section permettant de
  régler l'apparence des sous-titres : au minimum taille du texte et
  couleur du texte, idéalement aussi couleur/opacité du fond.
- Persister ce réglage (DataStore ou équivalent) et l'appliquer à toutes
  les lectures suivantes de films/épisodes.
- S'appuyer sur les capacités de style de sous-titres de Media3
  (CaptionStyleCompat ou équivalent) plutôt que sur un rendu de
  sous-titres personnalisé fait à la main.
- Prévoir un aperçu en direct du rendu (texte d'exemple) dans l'écran de
  réglage, pour que l'utilisateur voie l'effet avant de valider.

---

Phase 27 [TERMINE] : profils multiples sur un même compte Xtream (type Netflix).

Note périmètre : AGENTS.md exclut "multi-comptes/profils utilisateurs" du
périmètre initial du projet. Cette phase reste bien un seul compte Xtream
(un seul identifiant/mot de passe stocké, une seule base de médias) avec
plusieurs profils LOCAUX dessus — pas des comptes Xtream distincts. À
confirmer explicitement que c'est bien ça qui est visé avant de démarrer
le développement.

Le catalogue de médias (chaînes, films, séries, catégories, EPG) reste
commun à tous les profils : un seul cache Room partagé, pas de
duplication ni de re-téléchargement par profil.

Ce qui doit devenir spécifique à chaque profil :
- Favoris (chaînes, films, séries).
- Historique "Récemment regardées" (chaînes Live TV, Phase 13).
- État d'avancement de lecture : position sauvegardée sur les films et
  les épisodes de séries (reprise de lecture), y compris "Continuer à
  regarder" sur la Home (Phase 6).

Attendu :
- Écran de sélection de profil (grille de profils avec nom + avatar,
  façon Netflix) affiché après connexion (ou après auto-login de la
  Phase 18) si plusieurs profils existent, avant d'accéder à la Home.
- Si un seul profil existe, sauter directement cet écran (comportement
  actuel inchangé pour un usage mono-profil).
- Gestion des profils depuis les Paramètres (Phase 5) : création,
  renommage, suppression, changement d'avatar. Prévoir un minimum de
  garde-fou (ex: ne pas supprimer le dernier profil restant).
- Revoir le schéma Room pour associer un `profileId` aux entités
  Favoris, Historique et Position de lecture, sans dupliquer les
  entités de catalogue (chaînes/films/séries/catégories) qui restent
  non liées à un profil.
- Un changement de profil en cours d'usage (depuis les Paramètres ou un
  bouton dédié) doit rafraîchir immédiatement Favoris/Historique/
  Continuer à regarder sur tous les écrans concernés, sans nécessiter de
  redémarrage de l'app.
- Pas de code PIN ni de restriction parentale par profil à ce stade
  (hors périmètre sauf demande explicite ultérieure) : uniquement la
  séparation des données listées ci-dessus.

---

Phase 28 [TERMINE] : correction de l'accessibilité et de l'ergonomie de l'écran de
connexion.

1. Bug de contraste : les champs de saisie remplis (host, username,
   password) utilisent une couleur de fond gris foncé qui se confond
   avec le fond noir de l'écran, rendant le texte saisi illisible.
   Corriger les couleurs du champ de texte (fond, bordure, texte) pour
   garantir un contraste suffisant dans tous les états (vide, rempli,
   focus) sur fond sombre — respecter les ratios de contraste
   d'accessibilité standard, WCAG AA minimum, cohérent avec la
   correction de contraste déjà faite en Phase 24.

2. Fusion des champs host et port : actuellement deux champs séparés
   (host, port). Remplacer par un seul champ "adresse du serveur" où
   l'utilisateur saisit l'URL complète avec le port inclus
   (ex: http://mondns.com:8080 ou http://192.168.1.1:25461).
   Attendu :
   - Parser ce champ unique pour en extraire host/port/scheme avant
     l'appel à player_api.php (réutilise la même logique réseau
     existante, ne change que la saisie utilisateur).
   - Être défensif sur le format saisi : accepter avec ou sans
     "http://"/"https://" en préfixe (ajouter "http://" par défaut si
     absent), signaler une erreur de saisie claire si le port est
     manquant ou non numérique plutôt que de laisser échouer la requête
     silencieusement.
   - Migration : les identifiants déjà stockés (host + port séparés,
     Phase 1) doivent continuer à fonctionner après cette phase
     (reconstituer l'URL complète à l'affichage/pré-remplissage du champ
     fusionné à partir des valeurs existantes), sans forcer l'utilisateur
     à ressaisir ses identifiants.

---

Phase 29 [TERMINE] : mémorisation de la langue audio et des sous-titres choisis, par
film et par série (voir Phase 3/16 pour la sélection des pistes en cours de
lecture).

Actuellement la préférence de langue audio/sous-titres choisie dans le
player est stockée de façon globale (un seul réglage partagé par toute
l'app) : changer de piste sur un film écrase le choix fait sur un autre
film ou une série, et réciproquement.

Attendu :
- Le choix de langue audio et de sous-titres fait pendant la lecture doit
  être mémorisé individuellement par fiche :
  - Par film (clé = streamId du film).
  - Par série (clé = seriesId de la série) — le choix est commun à tous
    les épisodes de cette série, pas mémorisé épisode par épisode. Si
    l'utilisateur change de piste en regardant un épisode, ce choix
    s'applique aussi aux autres épisodes de la même série.
- Au lancement de la lecture d'un film ou d'un épisode, appliquer en
  priorité la préférence déjà mémorisée pour ce film/cette série si elle
  existe.
- Si aucune préférence n'a encore été mémorisée pour ce film/cette série,
  revenir au comportement par défaut actuel (dernière langue utilisée
  globalement, ou langue par défaut du flux).
- Revoir le schéma de stockage (Room ou DataStore selon ce qui est le plus
  adapté) pour associer la préférence audio/sous-titres à un streamId
  (film) ou un seriesId (série), en remplacement du réglage global actuel
  dans SettingsManager — sans perdre les autres réglages existants qui,
  eux, restent globaux (fréquence de sync, apparence des sous-titres de
  la Phase 26, tri des catégories, etc.).
- Si des profils multiples sont déjà implémentés (Phase 27) au moment de
  développer cette phase, cette préférence doit elle aussi être spécifique
  au profil actif, comme les Favoris/Historique/Position de lecture.

---

Phase 30 [TERMINE] : regrouper "Continuer à regarder" par série sur la Home (Phase 6).

Actuellement, si plusieurs épisodes d'une même série ont une position de
lecture sauvegardée, chacun apparaît comme une entrée séparée dans
"Continuer à regarder". Aucun intérêt à afficher plusieurs épisodes de la
même série : une seule entrée par série, représentant le dernier épisode vu.

Attendu :
- Regrouper les entrées "Continuer à regarder" par série (clé = seriesId) :
  une seule carte par série, correspondant à l'épisode le plus récemment
  regardé (le plus grand `lastAccessedAt`/timestamp d'accès), pas au premier
  épisode trouvé.
- Les films (sans seriesId) ne sont pas concernés par ce regroupement :
  chaque film garde sa propre entrée comme aujourd'hui.
- Cliquer sur la carte groupée reprend la lecture du dernier épisode vu
  (même comportement qu'aujourd'hui pour une entrée individuelle), pas un
  écran de sélection d'épisode.
- Vérifie que ce regroupement reste correct une fois les positions de
  lecture scopées par profil (Phase 27) : le regroupement se fait au sein
  des positions du profil actif uniquement.

---

Phase 31 [TERMINE] : uniformiser la couleur du texte des boutons sur fond violet
(couleur primaire de l'app).

Le texte de certains boutons à fond violet (couleur primaire du thème) est
blanc, d'autres fois noir, de façon incohérente selon l'écran.

Attendu :
- Repérer tous les boutons dont le fond utilise la couleur primaire
  (violet) de l'app, sur tous les écrans (mobile et TV).
- Uniformiser : texte (et icônes le cas échéant) systématiquement blanc
  quand le fond du bouton est la couleur primaire violette.
- Vérifier que le contraste texte blanc / fond violet reste conforme aux
  ratios WCAG AA (cohérent avec les corrections de contraste des Phases 24
  et 28).
- Ne pas modifier les boutons dont le fond n'est pas la couleur primaire
  (ex: boutons de déconnexion en rouge, boutons neutres gris) : uniquement
  ceux à fond violet.

---

Phase 32 [TERMINE] : afficher l'heure de début/fin et la progression du programme en
cours sur les tuiles de chaînes Live TV.

Attendu :
- Sur chaque tuile de chaîne affichant le programme EPG en cours, ajouter
  l'heure de début et l'heure de fin du programme (déjà disponibles dans
  les données EPG récupérées, voir cahier des charges / Phase EPG).
- Ajouter une jauge de progression (barre horizontale) représentant
  l'avancement du programme en cours entre son heure de début et son heure
  de fin, mise à jour en temps réel (ou au moins à chaque rafraîchissement
  de l'écran).
- Cohérent visuellement avec les jauges de progression déjà existantes
  ailleurs dans l'app (ex: "Continuer à regarder", Phase 6).

---

Phase 33 [TERMINE] : uniformiser la taille des tuiles de chaînes Live TV, avec ou sans
programme en cours affiché.

Certaines chaînes n'affichent pas leur programme en cours (EPG absente ou
non résolue) et leur tuile devient de fait plus petite que celles qui
affichent un programme, cassant l'alignement de la grille/liste.

Attendu :
- Toutes les tuiles de chaînes Live TV doivent avoir la même taille (même
  hauteur en liste, mêmes dimensions en grille), qu'un programme EPG soit
  affiché ou non.
- Pour une chaîne sans programme en cours résolu, réserver l'espace
  normalement occupé par le texte du programme (ex: état vide silencieux,
  placeholder discret, ou simplement un espace vide de la même hauteur)
  plutôt que de laisser la tuile se contracter.
- Vérifie que ce correctif reste cohérent avec l'ajout de la jauge de
  progression et des heures de la Phase 32 (une chaîne sans EPG ne doit pas
  non plus afficher une jauge vide ou incohérente).

---

Phase 34 [TERMINE] : corriger le cadrage de l'image des chaînes favorites dans la
section Favoris de la Home (Phase 6).

Les chaînes Live TV ont des logos au format carré. La section Favoris de la
Home affiche ses cartes au format 2:3 (portrait, cohérent avec les films/
séries) : quand la carte est une chaîne, son logo carré est actuellement
recadré (cropped) pour remplir le 2:3, ce qui coupe une partie du logo.

Attendu :
- Conserver le format de carte 2:3 pour toutes les entrées de la section
  Favoris (uniformité avec films/séries), y compris pour les chaînes.
- Pour les chaînes uniquement, adapter le mode d'affichage de l'image afin
  que le logo carré tienne entièrement dans la carte sans être rogné (ex:
  `ContentScale.Fit`/letterboxing avec un fond neutre autour, plutôt que
  `ContentScale.Crop`), au lieu de déborder du cadre.
- Ne pas modifier l'affichage des films/séries dans cette même section,
  qui reste en `Crop` comme aujourd'hui (leurs affiches sont déjà au format
  portrait proche de 2:3).

---

Phase 35 [TERMINE] : ajouter une section Favoris sur l'écran Live TV en mode filtre
"Tout", sous "Récemment regardées".

Sur les écrans Films et Séries, en mode filtre "Tout", la section Favoris
apparaît déjà en premier. Sur l'écran Live TV en mode "Tout", cette section
est absente.

Attendu :
- Ajouter une section "Favoris" sur l'écran Live TV en mode filtre "Tout",
  positionnée juste en dessous de la section "Récemment regardées".
- Cette section liste les chaînes favorites de l'utilisateur (Favoris,
  scopés par profil actif depuis la Phase 27), dans le même style de
  section horizontale que les autres catégories du mode "Tout".
- Si aucune chaîne favorite n'existe, ne pas afficher de section vide
  (comportement cohérent avec "Récemment regardées" et avec les sections
  Favoris de Films/Séries).
- Ne modifie pas l'ordre/le contenu des sections Favoris déjà existantes
  sur Films et Séries : uniquement l'ajout côté Live TV.

---

Phase 36 [TERMINE] : désactiver le logging HTTP verbeux en dehors du debug.

L'`HttpLoggingInterceptor` est configuré en `Level.BODY` en dur : chaque
réponse API est intégralement écrite dans logcat (les catalogues "Tout"
pèsent plusieurs mégaoctets), et les URLs Xtream contenant username et
password apparaissent en clair dans les logs. Coût CPU/mémoire réel et
fuite d'identifiants sur build release.

Attendu :
- `Level.BODY` uniquement quand `BuildConfig.DEBUG` est vrai, `Level.NONE`
  sinon.
- Aucun identifiant ne doit apparaître dans logcat sur un build release.

---

Phase 37 [TERMINE] : debounce + annulation de la recherche globale.

`FavoritesViewModel.onSearchQueryChanged` lance une coroutine de recherche
à chaque frappe, sans annuler la précédente ni attendre de pause de
saisie : plusieurs requêtes SQL concurrentes, et un résultat obsolète
peut écraser un résultat plus récent (course).

Attendu :
- Mémoriser le `Job` de recherche en cours et l'annuler avant d'en lancer
  un nouveau.
- Debounce (~300 ms) avant d'exécuter la requête, pour ne chercher qu'à
  la pause de saisie.
- Le spinner `isSearching` reste cohérent (pas de spinner fantôme après
  annulation).

---

Phase 38 [TERMINE] : activer la minification R8 sur le build release.

`isMinifyEnabled = false` et `proguard-rules.pro` absent : APK ~27 Mo,
code non obfusqué.

Attendu :
- `isMinifyEnabled = true` + `isShrinkResources = true` sur le buildType
  release uniquement (debug inchangé).
- Créer `proguard-rules.pro` avec les règles keep nécessaires : DTOs Gson
  (désérialisation par réflexion), Retrofit, et toute classe touchée par
  la réflexion. Vérifier que login + navigation + lecture fonctionnent
  sur un build release minifié avant de conclure.

---

Phase 39 [TERMINE] : synchroniser versionCode/versionName avec les tags git.

`versionCode = 1` / `versionName = "1.0"` figés depuis le début alors que
les tags git sont en v1.x.y : deux installations successives ne se
mettent pas à jour proprement.

Attendu :
- `versionName` aligné sur le dernier tag git (ex: "1.15.1") et
  `versionCode` monotone croissant (dérivé du tag ou compteur manuel).
- Documenter dans AGENTS.md que le bump fait partie de la checklist de
  tag.

---

Phase 40 [TERMINE] : recherche plein texte via table FTS Room.

La recherche globale utilise `LIKE '%query%'` sur 4 colonnes × 3 tables :
non-indexable par SQLite (wildcard en préfixe), full scan à chaque
recherche. Sensible sur les gros catalogues (10k+ films).

Attendu :
- Table(s) FTS4 Room (`@Fts4`) sur name/actors/director/genre pour
  vod/series (et name pour live), synchronisées avec les tables sources.
- Migration Room réelle (pas de fallback destructif, voir AGENTS.md).
- `searchUnified` bascule sur MATCH ; résultats identiques ou meilleurs
  qu'avec LIKE (préfixes de mots).
- Tests unitaires de non-régression sur le mapping des résultats.

---

Phase 41 [TERMINE] : réactivité Room via Flow sur favoris et positions de lecture.

Tous les DAOs exposent des `List` ponctuelles : un favori ajouté/retiré
sur un écran n'actualise pas les autres écrans sans re-fetch manuel
(`loadHomeData()` à chaque entrée sur la Home).

Attendu :
- Exposer en `Flow` les lectures observées par l'UI : favoris, positions
  de lecture ("Continuer à regarder"), profils.
- Les ViewModels collectent ces Flows (scopés par profil actif) au lieu
  de recharger à chaque navigation.
- Les caches catalogues (live/vod/series) restent en suspend/List : leur
  cycle de vie est géré par la logique de fraîcheur existante.

---

Phase 42 [TERMINE] : centraliser le polling EPG de la Home dans le ViewModel.

Chaque `HomeLiveTvCard` visible lance sa propre boucle `while(true)` +
`delay(60s)` d'appels EPG : N cartes = N boucles réseau indépendantes.

Attendu :
- Un seul ticker dans `HomeViewModel` qui rafraîchit l'EPG de toutes les
  chaînes de la rangée en batch (délai 60 s conservé).
- Les cartes deviennent passives (elles lisent `state.epgPrograms`).
- Le ticker s'arrête quand le ViewModel est cleared ; pas de fuite de
  coroutine.

---

Phase 43 [TERMINE] : migrer kapt vers KSP (Hilt + Room).

kapt génère des stubs Java pour chaque compilation : build ~2× plus lent
que KSP sur ces processeurs. Migration mécanique supportée par Hilt
(2.48+) et Room (2.x).

Attendu :
- Remplacer `kotlin-kapt` par le plugin KSP, `kapt(...)` par `ksp(...)`
  pour hilt-compiler et room-compiler.
- Build, lint et tests passent à l'identique.

---

Phase 44 [TERMINE] : durcissements divers (lifecycle, cancellation, targetSdk).

Regroupe trois petits durcissements indépendants :

1. `collectAsStateWithLifecycle()` au lieu de `collectAsState()` dans les
   écrans (ajouter la dépendance `lifecycle-runtime-compose`) : stoppe la
   collecte des StateFlows quand l'app est en arrière-plan.
2. Les `catch (e: Exception)` génériques dans les coroutines doivent
   re-lancer `CancellationException` (sinon l'annulation structurée est
   cassée) : `if (e is CancellationException) throw e` en tête de catch,
   sur les catch situés dans du code suspend/coroutine uniquement.
3. Bump `targetSdk`/`compileSdk` à 35 (exigence Play Store pour les mises
   à jour) et corriger les éventuels avertissements de compat.

---

Phase 45 [TERMINE] : dette structurelle optionnelle (i18n, découpage UI, cleartext).

Améliorations de fond, non bloquantes pour un POC mono-langue :

1. Externaliser les ~80 chaînes FR hardcodées vers `strings.xml` +
   `stringResource()` (prérequis à toute i18n future).
2. Découper les fichiers UI massifs : navigation de MainActivity
   (~1100 lignes) vers un `NavGraph.kt` dédié ; extraire les cards de
   HomeScreen/LiveTvScreen dans des fichiers par composant.
3. Restreindre `usesCleartextTraffic` via un
   `network_security_config.xml` (autoriser HTTP uniquement là où les
   panels Xtream l'exigent) plutôt que le flag global.

---

## Refonte UI/UX (maquette Claude Design "Refonte IPTV")

Les phases 46 à 53 déclinent la refonte visuelle validée sur maquette
(projet Claude Design, direction "cinéma"). Cadrage commun à toutes ces
phases, à respecter sans le répéter à chaque fois :

- **Référence visuelle** : `docs/design-reference/` contient le HTML/CSS brut
  de la maquette (`mockup-source/Refonte-IPTV.dc.html` — couleurs, radius,
  typographie exacts, grep par section via les commentaires `<!-- HOME -->`,
  `<!-- TV -->`, etc.) et, quand disponibles, des captures d'écran par écran
  (`screenshots/`). Consulter ce dossier avant de commencer chaque phase
  46-54 plutôt que d'extrapoler les valeurs de design.

- **Périmètre mobile uniquement** (`isTv == false`). La branche TV
  (D-pad, focus, `androidx.tv.material3`, navigations dédiées) n'est PAS
  concernée : ne rien casser sur les layouts `TvXxx`/`isTv` existants.
- **Refonte purement visuelle** : toutes les fonctionnalités de la
  maquette existent déjà côté logique (profils, reprise de lecture,
  favoris, EPG, recherche FTS, fréquence de sync, apparence sous-titres,
  "Voir tout"). Aucune feature métier à créer — on ré-habille des écrans
  qui fonctionnent, on ne réécrit pas les ViewModels/repositories.
- **Polices** : Bricolage Grotesque (titres) et Hanken Grotesk (corps),
  bundlées en statique dans `res/font/` (fichiers `.ttf` téléchargés,
  pas de Downloadable Fonts — l'app doit rester fonctionnelle hors
  ligne).
- **Icônes** : conserver `androidx.compose.material.icons` (vecteurs,
  hors ligne). Ne PAS migrer vers la police Material Symbols de la
  maquette ; le rendu rounded/filled existant est visuellement proche.
- **Palette de référence** (hex maquette) : fond `#060608`, surfaces
  `#0F0F13` / `#16161D` / `#1E1E24`, accent lavande `#9C86FF`, texte
  `#F6F6FA` / secondaire `#9A9AA8`. L'app utilise déjà `#0F0F13` /
  `#1E1E24` : la direction est proche, la migration reste faisable
  incrémentalement.
- **Méthode** : livrer écran par écran, build + lint + test verts et
  commit à chaque phase (règle projet). Aucun tag intermédiaire jusqu'à
  la fin de la refonte.

---

Phase 46 [TERMINE] : fondation du design system (thème centralisé + polices).

Aucun `Theme.kt` / `Color.kt` / `Type.kt` n'existe : chaque composable
code ses couleurs en dur (`Color(0xFF1E1E24)`…) et l'app tourne sur le
`colorScheme` Material3 par défaut. Prérequis à tout le reste : sans
socle de tokens, chaque phase suivante recopierait des littéraux.

Attendu :
- Créer `presentation/theme/` : `Color.kt` (palette de référence en
  tokens nommés), `Type.kt` (Typography Material3 mappée sur les deux
  polices : Bricolage Grotesque en display/headline/title, Hanken
  Grotesk en body/label), `Theme.kt` (un `MaterialTheme` mobile
  appliqué à la racine de la branche `isTv == false`).
- Télécharger et bundler les `.ttf` des deux familles dans `res/font/`
  (poids réellement utilisés : 500/600/700), déclarer les `FontFamily`.
- Définir un `colorScheme` sombre unique (primary = accent lavande) et
  y raccorder les usages existants de `MaterialTheme.colorScheme.primary`
  (déjà violet, donc pas de rupture visuelle brutale).
- Étape d'infrastructure : à la fin de cette phase, l'app doit compiler
  et se lancer avec un rendu quasi identique à l'actuel (le socle est en
  place, le reskin écran par écran vient ensuite). Pas de régression de
  navigation ni de focus.

---

Phase 47 [TERMINE] : chrome global mobile (fond dégradé + barre de navigation).

Attendu :
- Remplacer le fond plat `#0F0F13` des écrans mobile par le dégradé
  radial de la maquette (`radial-gradient` violet sombre en haut → noir
  en bas), factorisé dans un conteneur/`Modifier` réutilisable du thème
  plutôt que recopié par écran.
- Restyler la `NavigationBar` mobile (actuellement `containerColor
  #16161D`) selon la maquette : fond, item actif en accent, labels,
  poids d'icônes cohérents (Accueil / TV / Films / Séries / Recherche
  inchangés fonctionnellement).
- Vérifier le rendu sur les cinq onglets et le contraste de l'item
  actif/inactif (cohérent avec les Phases 24/28/31 sur l'accessibilité).

---

Phase 48 [TERMINE] : refonte de l'écran d'accueil (Home).

Écran vitrine, le plus visible et le plus proche de la maquette. Contient
le seul composant réellement nouveau de la refonte : le hero "Reprendre".

Attendu :
- En-tête : "Bonsoir {profil}", code/identifiant du compte, date
  d'expiration, accès Paramètres (icône). Restyle, données déjà en state.
- Hero "Reprendre" (NOUVEAU) : grande carte (~282 dp, coins 22 dp, ombre
  portée) affichant la dernière entrée "Continuer à regarder" du profil
  actif, avec image de fond + dégradé, badge accent "REPRENDRE", badge
  verre "4K · SÉRIE" (métadonnée qualité/type), titre en Bricolage
  Grotesque, boutons "Reprendre" (lecture directe) et un bouton
  secondaire. S'appuie sur `resumeWatchingList` déjà exposé par
  `HomeViewModel` ; masquer proprement le hero si la liste est vide.
- Restyler les rangées horizontales (Continuer à regarder, Favoris, TV
  en direct, Films, Séries) et leurs chips "Voir tout" (déjà
  fonctionnels depuis les phases "Voir tout") selon la maquette.
- Réutiliser les cartes du thème (voir Phase 46) : ne pas réintroduire de
  couleurs en dur dans `home/components/HomeCards.kt`.

---

Phase 49 [TERMINE] : refonte de l'écran TV en direct (Live TV).

Attendu :
- Sélecteur de catégorie : aligner sur la bottom sheet de la maquette
  (déclencheur "Tout ⌄" → feuille modale avec champ de recherche, liste
  des catégories + compteurs, coche sur la sélection courante) plutôt que
  le dropdown actuel. Le contenu (catégories fournisseur) est déjà
  disponible.
- Restyler les tuiles de chaîne (logo, numéro, programme EPG avec heures
  + jauge de progression des Phases 32/33) et les sections "Récemment
  regardées" et "Favoris" (Phase 35), en conservant la hauteur uniforme
  des tuiles (Phase 33).
- Conserver le cadrage `Fit` des logos carrés (Phase 34).

---

Phase 50 [TERMINE] : refonte des écrans Films et Séries (navigation catalogue).

`VodScreen` et `SeriesScreen` partagent la même structure (rangées par
catégorie fournisseur + mode filtre) : à traiter ensemble pour rester
cohérent.

Attendu :
- Restyler les rangées par catégorie (Reprendre, Nouveautés, Nouveautés
  Jeunesse, Films 4K, Tendances, Jeunesse… — libellés fournisseur
  intacts) et les cartes affiches au format 2:3 selon la maquette.
- Aligner le sélecteur de catégorie sur la même bottom sheet que la
  Phase 49 (composant partagé).
- Préserver la restauration de position de défilement (Phase 20) et les
  grilles "Voir tout" (traitées visuellement en Phase 52).

---

Phase 51 [TERMINE] : refonte des fiches détail Film et Série.

Attendu :
- Fiche Film : image/backdrop, titre en Bricolage Grotesque, métadonnées
  (date, genre, durée, note), résumé, réalisateur, acteurs, CTA "Lire le
  film" en accent. Restyle, données déjà fournies par
  `getVodDetails`/`getSeriesDetails`.
- Fiche Série : mêmes principes + sélecteur de saisons et liste des
  épisodes avec badge "en cours de lecture / reprendre" et bouton
  "Reprendre : S{n} E{n}" (position de lecture déjà gérée).
- Corriger au passage tout chevauchement titre long / actions (cohérent
  avec la Phase 25 sur le player).

---

Phase 52 [TERMINE] : refonte de la recherche et de la grille "Voir tout".

Attendu :
- Écran Recherche : champ restylé, résultats groupés par type (Chaînes /
  Films / Séries) avec compteurs et chips "Voir tout" (FTS de la Phase 40
  et regroupement déjà en place).
- Vue grille "Voir tout" (partagée par Recherche/Films/Séries/Home) :
  en-tête avec bouton retour restylé (carré 40 dp, coins 13 dp, surface
  `#1E1E24`), titre de section, grille verticale d'affiches au format de
  la maquette.
- Vérifier que la grille reste performante sur gros catalogue (pas de
  régression vs. l'implémentation actuelle).

---

Phase 53 [TERMINE] : refonte des écrans secondaires (Profil, Paramètres, Connexion).

Applique le design system aux écrans restants. Fonctionnalités déjà
complètes : uniquement du reskin.

Attendu :
- Sélection de profil ("Qui regarde ?") et gestion des profils (création,
  renommage, couleur, suppression) : avatars, boutons, dialogues au thème.
- Paramètres : cartes (fréquence de sync, apparence des sous-titres, tri
  des catégories, déconnexion) restylées ; contenu et logique inchangés.
- Connexion : la maquette ne couvre pas cet écran ; étendre la direction
  visuelle (champs lisibles — cohérent Phase 28, accent, typographie) par
  extrapolation, sans introduire de comportement nouveau.
- Passe finale de cohérence : traquer les derniers `Color(0xFF…)` en dur
  sur les écrans mobile et les basculer vers les tokens du thème.

---

Phase 54 (optionnelle) [TERMINE] : accent réglable par l'utilisateur.

La maquette expose l'accent comme variable de thème (lavande `#9C86FF`
par défaut, + bleu `#0070F3`, sarcelle `#2BB8A6`, ambre `#E5A13A`). Une
fois le design system en place (Phase 46), permettre à l'utilisateur de
choisir l'accent depuis les Paramètres.

Attendu :
- Réglage "Couleur d'accent" dans les Paramètres (4 teintes proposées),
  persisté (DataStore/SettingsManager, global ou par profil selon
  cohérence avec l'existant).
- L'accent choisi pilote le `primary` du thème et se propage à tous les
  écrans mobile sans redémarrage.
- Hors périmètre si jugé superflu pour un POC : à confirmer avant de
  démarrer. Ne pas bloquer la clôture de la refonte (Phases 46-53) sur
  cette phase.

---

Phase 55 [TERMINE] : corrections et peaufinages de la refonte UI/UX (retours utilisateur).

Attendu :
- Harmonisation des icônes : s'assurer que toutes les icônes de l'application (paramètres, accueil, TV, etc.) correspondent exactement aux visuels de la maquette.
- Liens "Voir tout" de la Home : restyler les liens/boutons "Voir tout" de l'écran d'accueil pour qu'ils soient identiques à ceux de la maquette (liens textuels discrets, etc.).
- Temps restant sur "Continuer à regarder" : dans la section "Continuer à regarder" de l'accueil, afficher clairement le temps de lecture restant pour chaque vignette, conformément aux captures d'écran de référence.
- Titre des médias dans les listes : déplacer le titre des médias pour qu'il soit affiché à l'intérieur de la vignette (en overlay) et non plus en dessous, conformément à la maquette.
- Tuiles TV de l'accueil : réviser le design des tuiles de la rangée TV en direct sur l'écran d'accueil pour qu'elles soient rigoureusement identiques à celles de la maquette.

---

Phase 56 [TERMINE] : retours liés à l'onglet TV et affichage catalogue.

Attendu :
- Titres des catégories plus gris : les titres de catégories doivent être affichés avec une couleur de texte plus grisée (cohérente avec le texte secondaire `#9A9AA8` de la maquette).
- Vignettes TV récemment regardées : corriger le design des vignettes TV récemment regardées pour être 100 % iso-maquette (couleur du texte du programme gris et non violet, affichage de l'heure/horaire du programme, etc.).
- Filtre de catégorie unifié (TV/Films/Séries) :
  - Supprimer le bouton "Rafraîchir" (Refresh) à droite du sélecteur sur les écrans TV, Films et Séries. Le rafraîchissement manuel se fera désormais uniquement via les Paramètres.
  - Élargir le bouton déclencheur (dropdown) de catégorie pour occuper toute la largeur disponible.
  - Espacer le sélecteur du haut de l'écran pour éviter qu'il soit collé au bord supérieur.
  - Donner au sélecteur un fond de la même couleur neutre et transparente que le reste du layout.
- Champ de recherche de catégorie filtrée : restyler le champ de recherche textuel affiché lors d'un filtrage de catégorie spécifique pour être iso-maquette (plus arrondi, plus aéré en haut/bas avec des paddings, fond légèrement grisé identique au dropdown de catégorie).
- Boutons "Voir tout" et "Favoris" en mode "Tout" :
  - Quand on est sur la catégorie virtuelle "Tout", ajouter un bouton "Voir tout" à droite de chaque section horizontale (comme sur l'écran d'accueil).
  - Ajouter également la section "Favoris" sur cet écran de TV en direct.
- Grille de chaînes à 2 colonnes par ligne : lorsqu'on clique sur une catégorie spécifique, les chaînes doivent s'afficher en défilement vertical, mais sous forme d'une grille à 2 colonnes par ligne (2 médias par ligne) au lieu d'une seule colonne.

---

Phase 57 [TERMINE] : retours sur les onglets Films/Séries et fiches détails.

Attendu :
- Bouton "Voir tout" sur les Favoris : dans l'onglet "Tout" des écrans Films et Séries, s'assurer de rajouter le bouton "Voir tout" à droite de la section "Favoris" (comme pour les autres sections et cohérent avec la Phase 56).
- Grille à 3 colonnes pour les catégories filtrées : lorsqu'un utilisateur sélectionne une catégorie spécifique dans le catalogue Films ou Séries, l'affichage vertical en grille doit s'organiser en lignes de 3 médias par ligne au lieu de 2 (identique au design des grilles "Voir tout" de la recherche).
- Unification de la taille des vignettes Films/Séries : la taille des vignettes de films et séries entre les sliders de la Home et ceux de l'onglet "Tout" des catalogues doit être harmonisée. Conserver et généraliser le format de la Home car il permet d'intégrer élégamment la note de notation que l'utilisateur apprécie.
- Année uniquement sur les fiches détails : sur la fiche détail de film (`VodDetailsScreen.kt`) et de série (`SeriesDetailsScreen.kt`), afficher uniquement l'année de sortie à la place de la date de sortie complète.
- Épurage de l'en-tête retour arrière : sur les fiches détails, tout en haut à gauche à côté de la flèche de retour arrière, supprimer définitivement le texte d'en-tête "Détail..." pour avoir une navigation épurée.
