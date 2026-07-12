Phase 0 : mise en place du projet, rien de fonctionnel pour l'instant.

Attendu :
- Projet Android Studio complet (Gradle Kotlin DSL) avec le module app.
- Arborescence Clean Architecture : data / domain / presentation, packages
  clairs et cohérents.
- Hilt configuré (Application class annotée, module de base).
- Dépendances de base posées dans build.gradle.kts : Compose, Compose for TV,
  Retrofit + OkHttp, Room, Coil, Media3/ExoPlayer, DataStore ou
  EncryptedSharedPreferences.
- AndroidManifest.xml de base avec les permissions et les uses-feature TV
  décrits dans le cahier des charges.
- Un écran vide (placeholder) qui s'affiche au lancement, juste pour vérifier
  que le build et le run fonctionnent sur mobile ET sur Android TV (émulateur
  ou device).
- README.md avec les instructions de build.

Ne code aucun écran métier (pas de login, pas de Live TV, etc.) à ce stade.

---

Phase 0 validée, le projet compile et se lance. Passe à la Phase 1 :
authentification + couche réseau Xtream Codes (voir cahier des charges,
section 2).

Attendu :
- Écran de connexion (host, port, username, password, case "se souvenir de
  moi") en Compose, adapté mobile ET TV (D-pad fonctionnel).
- Interface Retrofit pour player_api.php avec l'action de login/vérification
  des identifiants.
- Stockage chiffré des identifiants après validation réussie.
- Gestion des erreurs explicite : identifiants invalides, compte expiré,
  serveur injoignable, timeout (voir section 5 du cahier des charges).
- Écran d'accueil minimal après connexion réussie, affichant au moins les
  infos du compte (statut, date d'expiration) pour prouver que
  l'authentification fonctionne bout en bout.

Je vais tester cette phase avec mon vrai serveur Xtream avant qu'on continue,
donc assure-toi que le parsing JSON est défensif (champs parfois en string
au lieu d'int selon les panels).

---

Phase 1 validée et testée avec mon serveur réel. Passe à la Phase 2 : Live TV
(voir cahier des charges, sections 2.2 et 3.3).

Attendu :
- Récupération et affichage des catégories de chaînes (get_live_categories).
- Liste des chaînes par catégorie (get_live_streams) avec logo, nom, numéro.
- Cache Room des catégories/chaînes avec rafraîchissement manuel
  (pull-to-refresh) et logique de cache expiré.
- Lecteur ExoPlayer plein écran (support HLS), construction correcte de
  l'URL de lecture d'une chaîne.
- Zapping chaîne suivante/précédente pendant la lecture (D-pad haut/bas sur
  TV, boutons sur mobile).
- Overlay de lecture : nom/logo de la chaîne, indicateur de buffering,
  gestion des erreurs de flux avec retry (voir section 5).
- Navigation D-pad complète et états de focus visibles sur TV.

---

Phase 2 validée. Passe à la Phase 3 : VOD Films (voir cahier des charges,
sections 2.2 et 3.4).

Attendu :
- Catégories de films (get_vod_categories) puis grille des films
  (get_vod_streams) avec poster, titre, note.
- Écran détail film (get_vod_info) : synopsis, année, genre, casting.
- Lecture ExoPlayer avec reprise de lecture (mémorisation de la position en
  Room, proposition "reprendre" ou "recommencer").
- Cache Room + rafraîchissement comme pour le Live TV.
- Gestion d'erreurs identique (flux non chargé, etc.).

---

Phase 3 validée. Passe à la Phase 4 : Séries (voir cahier des charges,
sections 2.2 et 3.5). C'est la partie avec le parsing JSON le plus
complexe (saisons/épisodes), sois particulièrement défensif.

Attendu :
- Catégories de séries (get_series_categories) puis grille des séries
  (get_series).
- Écran détail série avec liste des saisons disponibles.
- get_series_info pour récupérer les épisodes groupés par saison (titre,
  résumé, durée).
- Lecture d'un épisode avec reprise de lecture + bouton "épisode suivant"
  en fin de lecture.
- Cache Room pour les séries/saisons/épisodes.

---

Phase 4 validée. Passe à la Phase 5, la dernière (voir cahier des charges,
sections 3.6, 3.7, 3.8).

Attendu :
- Recherche unifiée locale (sur le cache Room) sur chaînes, films et séries
  par nom.
- Favoris : ajout/retrait pour chaînes, films et séries (stockage Room),
  écran dédié listant les favoris.
- Écran Paramètres : déconnexion/changement de compte, fréquence de
  rafraîchissement du cache, effacement du cache local, infos du compte
  (user_info : statut, expiration, connexions max).

Une fois cette phase livrée, fais une passe de relecture globale du projet
et signale-moi tout écart par rapport au cahier des charges initial, ou tout
point que tu n'as pas pu traiter correctement.

---

Phase 6 : refonte complète de l'écran d'accueil (Home).

L'écran doit maintenant afficher, dans cet ordre, sous forme de sections
avec défilement horizontal :

1. "Continuer à regarder" : tous les films/épisodes de séries avec une
   position de lecture sauvegardée et non terminée, triés du plus récent
   au plus ancien.
2. "Favoris" : l'ensemble des favoris de l'utilisateur (chaînes, films,
   séries confondus).
3. "TV" : les chaînes de la PREMIÈRE catégorie live (celle retournée en
   premier par get_live_categories), avec un lien "voir tout" vers l'écran
   Live TV complet.
4. "Films" : les films de la PREMIÈRE catégorie VOD, avec un lien
   "voir tout" vers l'écran Films complet.
5. "Séries" : les séries de la PREMIÈRE catégorie séries, avec un lien
   "voir tout" vers l'écran Séries complet.

Si une section est vide (ex: aucun favori), ne l'affiche pas du tout
(pas de section vide avec message "rien ici").
Navigation D-pad complète sur TV entre les sections et à l'intérieur de
chaque section (défilement horizontal).
Chaque carte doit permettre d'accéder directement à la lecture (pour
"Continuer à regarder") ou à la fiche détail (pour Favoris/TV/Films/Séries).

---

Phase 7 : ajout d'une barre de navigation en bas de l'écran, UNIQUEMENT
sur mobile (ne pas l'afficher sur Android TV, qui garde sa navigation
actuelle par D-pad/menu latéral).

5 onglets : Home / TV / Films / Séries / Recherche.
Icônes + labels, onglet actif visuellement distinct.
Cette barre doit rester visible sur les écrans de liste et de fiche détail,
mais doit disparaître pendant la lecture plein écran (player).
Navigation via Compose Navigation (NavHost) avec état préservé par onglet
(si je navigue de TV vers Films puis reviens sur TV, je retrouve ma position
de scroll / dernière catégorie consultée).

---

Phase 8 : ajout d'une recherche LOCALE À LA CATÉGORIE, en plus de la
recherche globale existante.

Quand l'utilisateur est sur la liste des chaînes/films/séries d'UNE
catégorie précise (pas sur "Tout"), afficher un champ de recherche qui
filtre uniquement les éléments de cette catégorie par nom, en temps réel
(pas besoin de valider).
Cette recherche contextuelle est différente de la recherche globale (qui
reste accessible depuis l'onglet Recherche et cherche sur tout le
catalogue). Ne mélange pas les deux implémentations : la recherche
contextuelle est un simple filtre local sur la liste déjà chargée/cachée,
pas un nouvel appel réseau.

---

Phase 9 : refonte du player Live TV.

Retirer de l'interface du lecteur les boutons visibles : play/pause,
précédent, suivant. Le direct n'a pas vocation à être mis en pause.

IMPORTANT : le zapping doit rester fonctionnel, juste sans boutons visibles :
- Sur Android TV : zapping chaîne suivante/précédente via D-pad haut/bas
  pendant la lecture.
- Sur mobile : zapping via swipe vertical (haut/bas) pendant la lecture.

Garde l'overlay d'information (nom/logo de la chaîne, indicateur de
buffering) et la gestion des erreurs de flux avec retry, décrits dans le
cahier des charges initial section 3.3 — seuls les boutons de contrôle de
lecture disparaissent, pas les infos ni la gestion d'erreur.

---

Phase 10 : corrections sur la fiche détail d'un film.

1. Bug : les acteurs ne sont pas correctement extraits de la réponse
   get_vod_info. Inspecte la structure réelle du champ concerné (souvent
   "cast" ou "actors" dans l'objet "info", parfois une string séparée par
   des virgules, parfois un tableau selon les panels) et corrige le
   parsing pour extraire correctement chaque acteur individuellement.
2. Ajoute l'affichage de la durée du film (champ "duration" ou
   équivalent dans "info", à formater en heures/minutes, ex: "1h 42min").
3. La note doit être arrondie à 1 chiffre après la virgule (ex: 7.85 →
   7.9, 8 → 8.0).

Sois défensif sur le parsing (voir section 2.3 du cahier des charges) :
si un champ est absent ou dans un format inattendu, ne fais pas planter
l'écran, masque simplement l'information concernée.

---

Phase 11 : refonte de la fiche détail d'une série pour reprendre le même
style visuel que la fiche film (Phase 10).

Ces informations sont bien fournies par mon serveur Xtream (une autre
application IPTV classique les affiche correctement) : il s'agit donc
d'un bug de parsing côté app à corriger, pas de données manquantes côté
serveur. Inspecte la réponse brute JSON de get_series_info sur mon
serveur pour identifier la structure exacte avant de coder le mapping.

Compléter les informations affichées, à extraire de get_series_info
(objet "info") :
- Réalisateur ("director" ou équivalent)
- Date de sortie ("releaseDate" ou équivalent)
- Genre ("genre")
- Description/synopsis ("plot")
- Liste des acteurs (même bug de parsing que pour les films en Phase 10
  — vérifie si get_series_info a la même structure ou une structure
  différente de get_vod_info sur ce point, ne suppose pas qu'elle est
  identique)

Si après vérification de la réponse brute un champ précis s'avère
réellement absent pour certaines séries de mon catalogue (ex: séries
sans réalisateur renseigné), masque l'information pour cette série
uniquement — mais ne généralise pas cette hypothèse à l'ensemble de la
fonctionnalité.

Ajoute un bouton "Reprendre" qui lance directement la lecture du dernier
épisode regardé et non terminé (s'appuie sur la même logique de reprise
de lecture que pour les films). S'il n'y a aucun historique de lecture,
ce bouton doit plutôt lancer le premier épisode de la première saison
(libellé du bouton adapté en conséquence, ex: "Lire" au lieu de
"Reprendre").

Dans la liste des épisodes (par saison), à côté du nom et de la durée de
chaque épisode, affiche également la vignette/image associée à l'épisode
(champ "movie_image" ou équivalent dans les données de l'épisode).

---

Phase 12 : correction du format des images dans les listes/grilles de
films et séries.

Actuellement les affiches sont affichées en carré (crop). Les affiches
Xtream (movies/séries) sont au format portrait rectangulaire standard
(ratio environ 2:3, comme une affiche de cinéma). Corrige l'affichage
dans toutes les grilles (Home, listes par catégorie, résultats de
recherche) pour respecter ce ratio 2:3 sans crop qui coupe l'image
(utilise ContentScale.Fit ou un composant Coil dimensionné en 2:3, pas
ContentScale.Crop en carré).

Les logos de chaînes Live TV restent inchangés (souvent déjà carrés/
rectangulaires selon les chaînes, ne pas forcer de ratio dessus).

---

Phase 13 : sur l'écran Live TV, ajoute une section "Récemment regardées"
listant les 10 dernières chaînes regardées par l'utilisateur (stockage
Room, mise à jour à chaque changement de chaîne dans le player).

Cette section est FIXE en haut de l'écran Live TV : elle reste affichée
quelle que soit la catégorie sélectionnée en dessous (elle ne fait pas
partie du contenu qui change avec le filtre catégorie).
Si aucune chaîne n'a encore été regardée, ne pas afficher la section.
Défilement horizontal pour cette section, comme les autres.

---

Phase 14 : refonte des écrans de liste Films / Séries / TV pour ajouter
un système de filtre par catégorie en haut de l'écran, avec un
comportement différent selon le filtre choisi :

- Par défaut, le filtre est sur "Tout" : dans ce mode, affiche une section
  par catégorie (comme sur la Home), chacune avec défilement horizontal
  et un titre = nom de la catégorie. L'ordre des sections suit l'ordre
  des catégories retourné par l'API.
- Si l'utilisateur sélectionne une catégorie précise dans le filtre :
  on quitte le mode sections horizontales, et on affiche une grille
  verticale classique (comme l'écran actuel) contenant uniquement les
  éléments de cette catégorie.

Le filtre catégorie doit être navigable au D-pad sur TV (rangée
horizontale de chips/boutons en haut, "Tout" en premier).
Réutilise la logique de cache Room existante, ne fais pas de nouvel appel
réseau si les données sont déjà en cache.

---

Phase 15 : enrichissement de la recherche globale et ajout de liens
cliquables sur les fiches détail.

1. La recherche globale (écran Recherche) doit maintenant chercher aussi
   dans les champs acteur, genre et réalisateur des films/séries en
   cache (pas seulement le titre). Un résultat correspond si le terme
   recherché matche le titre OU un acteur OU le genre OU le réalisateur.
   Affiche un résultat dédoublonné (un film ne doit pas apparaître deux
   fois même s'il matche sur plusieurs critères).

2. Sur les fiches détail film et série, rends cliquables :
   - le nom du réalisateur
   - chaque acteur de la liste

   Au clic (ou sélection D-pad + OK sur TV), lance une recherche sur ce
   nom exact et affiche les résultats (films + séries confondus) sur
   l'écran Recherche, comme si l'utilisateur avait tapé ce nom lui-même.

Assure-toi que cette recherche enrichie reste performante (recherche sur
le cache Room local, pas d'appel réseau supplémentaire) même avec un
catalogue de plusieurs milliers d'éléments — ajoute un index/une
requête Room adaptée si nécessaire plutôt qu'un filtre en mémoire sur
toute la liste à chaque frappe.

---

Phase 16 : enrichir le lecteur ExoPlayer utilisé pour les films et les
épisodes de séries (pas le player Live TV) avec la possibilité de
changer :
- la piste audio (si le flux en propose plusieurs — langues différentes)
- les sous-titres (activer/désactiver, choisir la langue si plusieurs
  pistes sont disponibles, avec une option "Aucun")

Attendu :
- Un bouton/icône dédié dans l'overlay du player VOD (ex: icône
  "audio/sous-titres") ouvrant un menu de sélection, navigable au D-pad
  sur TV.
- Utilise les capacités de Media3 (TrackSelector /
  TrackSelectionParameters) pour lister DYNAMIQUEMENT les pistes audio et
  de sous-titres réellement disponibles dans le flux en cours de lecture
  — ne code pas une liste de langues en dur, elle doit venir de ce que le
  flux expose réellement.
- Si le flux ne propose qu'une seule piste audio et aucun sous-titre,
  n'affiche pas le bouton (ou affiche-le désactivé avec un message
  clair) plutôt que d'ouvrir un menu vide.
- Mémorise la langue audio/sous-titres préférée de l'utilisateur
  (DataStore ou Room) et applique-la par défaut à la lecture suivante si
  elle est disponible sur le nouveau flux, sinon reviens à la piste par
  défaut du flux.
- Les sous-titres Xtream Codes sont presque toujours embarqués dans le
  flux lui-même (pas de fichier séparé) : concentre-toi sur les pistes
  embarquées. Si tu détectes malgré tout un champ de sous-titres externe
  dans get_vod_info/get_series_info, prends-le en compte en complément.
  
---

Phase 17 : afficher le programme actuellement diffusé sur les vignettes
de chaînes, partout où une liste de chaînes apparaît (liste Live TV par
catégorie, section "Récemment regardées" de la Phase 13, section "TV" de
la Home de la Phase 6).

Utilise l'action Xtream Codes get_short_epg (voir cahier des charges
section 2.2, à compléter si besoin avec le paramètre stream_id) pour
récupérer, pour chaque chaîne visible à l'écran, le programme en cours.

Attendu :
- Afficher sous/sur la vignette de la chaîne : le titre du programme en
  cours (tronqué si trop long), et si possible une barre de progression
  indiquant l'avancement du programme dans sa plage horaire (début/fin).
- Les titres de programme EPG Xtream Codes sont souvent encodés en
  base64 : décode-les avant affichage, en restant défensif si un panel
  ne les encode pas (essaie le décodage, si le résultat n'est pas du
  texte lisible affiche la valeur brute plutôt que de planter).
- Ne récupère l'EPG QUE pour les chaînes réellement visibles à l'écran
  (pas tout le catalogue d'un coup) pour ne pas surcharger le serveur ni
  ralentir l'app, surtout dans les sections à défilement horizontal.
- Cache ces données en Room avec une expiration courte (quelques
  minutes) car le programme change avec le temps ; prévois un
  rafraîchissement automatique tant que l'écran concerné est affiché.
- Si l'EPG est indisponible pour une chaîne (erreur, pas de données),
  n'affiche simplement aucune info de programme pour cette chaîne, sans
  erreur visible ni crash.
  

