Phase 18 : fluidifier le lancement de l'app en supprimant l'affichage
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

Phase 19 : correction du bug de recherche par acteur/réalisateur (voir
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

Phase 20 : préservation de la position de défilement dans les listes
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

Phase 22 : rafraîchissement automatique en arrière-plan de la base de
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

Phase 23 : revoir les icônes TV/Films/Séries de la barre de navigation
mobile (Phase 7).

Les icônes actuelles de ces trois onglets ne sont pas satisfaisantes
visuellement. Les icônes Accueil et Recherche restent inchangées (déjà
correctes) : ne retoucher que TV/Films/Séries, en gardant une cohérence
de style (même famille d'icônes, même poids visuel) avec les deux
icônes conservées.

---

Phase 24 : correction d'accessibilité sur les boutons "voir tout" de la
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

Phase 25 : correction d'un chevauchement visuel dans le player film/
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

Phase 26 : personnalisation de l'apparence des sous-titres du player
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

Phase 27 : profils multiples sur un même compte Xtream (type Netflix).

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

Phase 28 : correction de l'accessibilité et de l'ergonomie de l'écran de
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

Phase 30 : regrouper "Continuer à regarder" par série sur la Home (Phase 6).

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

Phase 31 : uniformiser la couleur du texte des boutons sur fond violet
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

Phase 32 : afficher l'heure de début/fin et la progression du programme en
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

Phase 33 : uniformiser la taille des tuiles de chaînes Live TV, avec ou sans
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

Phase 34 : corriger le cadrage de l'image des chaînes favorites dans la
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

Phase 35 : ajouter une section Favoris sur l'écran Live TV en mode filtre
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
