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

