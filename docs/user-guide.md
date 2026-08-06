# Guide d'Utilisation de CSTV IPTV

Ce guide vous explique comment installer, configurer et utiliser l'application CSTV pour profiter au mieux de votre abonnement IPTV.

---

## 1. Prérequis & Installation

### 📋 Prérequis
* Un appareil sous **Android** (smartphone, tablette) ou **Android TV / Fire TV** fonctionnant sous Android 5.0 (API 21) ou version supérieure.
* Un abonnement IPTV actif compatible avec le protocole **Xtream Codes** (contenant une URL de serveur, un identifiant et un mot de passe).
* *Note* : Les playlists au format brut `.m3u` ou les fichiers locaux ne sont pas supportés.

### 📥 Installation
1. Téléchargez le fichier d'installation APK fourni pour votre plateforme.
2. Autorisez l'installation d'applications de sources inconnues dans les paramètres de sécurité de votre appareil Android si demandé.
3. Ouvrez le fichier APK et cliquez sur **Installer**.

---

## 2. Première Connexion & Profils

### 🔐 Authentification
Lors du premier lancement de l'application, vous arrivez sur l'écran de connexion :
1. **Nom du serveur / URL** : Saisissez l'adresse de votre serveur IPTV (ex: `http://mon-serveur.com:8000`).
2. **Utilisateur** : Saisissez votre identifiant d'abonné.
3. **Mot de passe** : Saisissez votre mot de passe associé.
4. Cliquez sur **Se connecter**. Les identifiants sont chiffrés et sauvegardés localement de manière sécurisée.

### 👥 Gestion des Profils (Type Netflix)
Une fois connecté, l'application vous propose l'écran de sélection de profil :
* Vous pouvez créer plusieurs profils locaux distincts pour chaque membre de la famille.
* Chaque profil dispose de son propre historique de visionnage, de ses favoris personnalisés, de ses positions de lecture de films/séries ainsi que de ses préférences de langue audio/sous-titres par défaut.
* Le catalogue de chaînes et de vidéos reste commun pour ne pas encombrer la mémoire de l'appareil.

---

## 3. Navigation dans l'application

L'application est entièrement optimisée pour deux types d'appareils :

* **📱 Version Mobile** : La navigation s'effectue de manière tactile en utilisant la barre d'onglets située au bas de l'écran.
* **📺 Version Android TV** : Conçue pour être pilotée entièrement à la télécommande (touches directionnelles, bouton central OK, touche retour). Une barre de navigation latérale rétractable est ancrée à gauche sur toute la hauteur de l'écran. 
  - Au repos (pliée), elle affiche uniquement les icônes de sections pour maximiser la visibilité de vos catalogues.
  - Dès que vous déplacez le focus au D-pad vers la gauche, elle se déplie en douceur pour dévoiler les libellés de sections, votre nom de profil et vos informations d'abonnement.
  - Pour fermer la barre sans changer de section, appuyez simplement sur la touche **Retour** ou naviguez vers la droite : le focus reviendra directement sur le contenu principal de votre écran sans aucune surprise.

---

## 4. Télévision en Direct (Live TV) & EPG

* **Sélection des chaînes** : Naviguez parmi vos catégories de chaînes préférées (Généralistes, Sport, Cinéma, Documentaires...).
* **Sélection des catégories (F24)** :
  - **Sur mobile** : Vous naviguez horizontalement à l'aide des puces de catégories défilantes en haut de l'écran.
  - **Sur TV (télécommande)** : L'interface est harmonisée avec les écrans de Films et Séries. Un sélecteur unique stable est disposé en tête de grille. Cliquez dessus pour ouvrir un dialogue plein écran et sélectionner verticalement et confortablement votre catégorie parmi la liste complète des bouquets IPTV (avec rappel dynamique des compteurs de chaînes).
* **EPG (Guide des programmes)** :
  * Sur l'écran de la liste des chaînes, l'application affiche directement le titre du programme actuellement en cours ainsi que celui à venir. La hauteur des cartes de chaînes TV a été accrue de 84 dp à 92 dp avec des hauteurs d'interlignes explicites pour que toutes les informations (nom de chaîne, titre de programme, barre de progression et plage horaire) s'affichent de façon parfaitement lisible sans aucune troncature verticale, même avec de forts réglages d'accessibilité.
  * En cliquant sur une chaîne, vous pouvez consulter le guide des programmes complet pour voir la grille horaire de la journée.
* **Ajout aux favoris** : 
  * **Sur mobile** : Appuyez simplement sur l'icône d'étoile en haut à droite du logo de la chaîne directement depuis la rangée (dans l'écran « Tout ») ou depuis la grille d'une catégorie. L'étoile devient or/jaune pour confirmer l'état de favori. Vous pouvez également rester appuyé longuement sur une chaîne pour faire basculer son statut.
  * **Sur TV (télécommande)** : Appuyez longuement sur le bouton central OK d'une carte de chaîne pour l'ajouter ou la retirer de votre liste de favoris rapides. L'étoile jaune décorative s'affiche alors pour confirmer le statut sans perturber le focus de votre télécommande (aucun focus secondaire sur l'étoile). Cette action est disponible sur toutes les cartes sauf dans la rangée historique « Récemment regardées ».
* **Zapping direct** : Lors de la lecture d'une chaîne, vous pouvez zapper instantanément à la chaîne suivante ou précédente via les flèches Haut/Bas de votre télécommande ou en glissant verticalement sur votre écran de téléphone.

---

## 5. Vidéos à la Demande (VOD) & Séries

* **Fiches détaillées** : En ouvrant un film ou une série, vous accédez à son résumé, à son année de sortie, à sa note et à des suggestions de films similaires recommandés pour vous. Vous pouvez également cliquer directement sur le nom d'un acteur ou d'un réalisateur dans la fiche pour lancer une recherche instantanée de ses œuvres associées.
* **Reprise de lecture** : Si vous quittez la lecture d'un film ou d'un épisode de série avant la fin, l'application mémorise précisément votre position. Lors du prochain lancement, cliquez simplement sur "Reprendre la lecture" pour continuer là où vous vous étiez arrêté. Les étiquettes (badges) indiquant le type de média (FILM, SÉRIE, DIRECT) sur l'écran d'accueil bénéficient d'un contraste renforcé (fond noir semi-opaque à 50% avec fine bordure blanche transparente) pour être immédiatement lisibles sur n'importe quelle pochette, claire ou foncée.
* **Carrousels Top 10 Mondiaux & Badges de Rang** : Les sections "Top 10 Films" et "Top 10 Séries" sur la page d'accueil de l'application affichent désormais les 10 contenus les plus populaires du moment au niveau mondial d'après les données de la plateforme TMDB (croisées intelligemment avec votre catalogue IPTV). Chaque affiche de ces classements intègre un grand chiffre de rang stylisé de 1 à 10 (inspiré du style de Netflix), superposé sur le flanc gauche de l'affiche et conçu avec des contours clairs et un fond transparent pour rester lisible en toutes circonstances. Si la clé TMDB est manquante ou en cas de coupure réseau, l'application continue de vous proposer des carrousels Top 10 fonctionnels en calculant silencieusement un classement de repli basé sur les meilleures notes locales (supérieures ou égales à 8/10).
* **Saisons & Épisodes** : L'interface des séries regroupe proprement les épisodes par saisons. À la fin d'un épisode, l'application vous propose de lancer automatiquement l'épisode suivant.
* **Notification de nouveaux épisodes (F12 - Mobile uniquement)** : Lorsque vous terminez de visionner le dernier épisode d'une série que vous suivez (présente dans vos favoris ou dans votre historique « Continuer à regarder »), l'application surveille silencieusement en arrière-plan (lors de la synchronisation de catalogue) si de nouveaux épisodes sont ajoutés par votre fournisseur IPTV. Si c'est le cas, vous recevez une notification système sur votre téléphone. Toucher cette alerte ouvre directement la fiche détaillée de la série pour que vous puissiez reprendre votre visionnage en un instant. Ce suivi est entièrement isolé par profil local afin de préserver votre confidentialité.

---

## 6. Téléchargements Hors-Ligne

* **Disponibilité exclusive sur Mobile** : Les fonctionnalités de téléchargement hors-ligne sont **entièrement désactivées et masquées sur la version Android TV** (les téléviseurs étant connectés de manière permanente, disposant d'un espace de stockage très restreint et n'ayant aucun usage hors-ligne). L'interface TV est ainsi épurée de toute trace de téléchargement (pas de section sur l'Accueil, pas de bouton sur les fiches de Films, pas d'icônes d'épisodes de Séries). Sur mobile, la fonctionnalité reste disponible à 100 %.
* **Lancer un téléchargement** : Sur mobile, ouvrez la fiche détaillée d'un film ou d'un épisode de série et cliquez sur l'icône de téléchargement. Le téléchargement se lance en arrière-plan.
* **Consulter ses vidéos hors-ligne** : Sur mobile, rendez-vous dans la section **Téléchargements** de l'application. Vous y retrouverez tous vos fichiers téléchargés localement.
* **Lecture sans connexion** : Les vidéos de cette section peuvent être lues à tout moment, même si votre appareil n'est absolument pas connecté à internet.
* **Raccourci sur l'écran d'Accueil (F15)** : En plus de l'écran dédié sur mobile, une nouvelle rangée horizontale « Téléchargements » apparaît automatiquement tout en bas de votre écran d'Accueil dès que vous possédez des téléchargements terminés (`COMPLETED`). Vous pouvez y voir d'un coup d'œil vos 20 derniers téléchargements par ordre de fraîcheur, voir leur titre et repère saison/épisode pour les séries, lancer directement leur lecture locale sans réseau, et utiliser le bouton « Voir tout » pour basculer vers l'écran complet de gestion.

## Catalogue sans connexion

Après avoir ouvert l'application en ligne et laissé le catalogue se synchroniser, vous pouvez continuer à parcourir les chaînes, films, séries, favoris, recherches et fiches déjà consultées sans connexion. Une bannière indique que les données sont locales et la date de leur dernière synchronisation. Les téléchargements restent lisibles ; pour une chaîne Live ou une vidéo non téléchargée, l'application affiche qu'une connexion est nécessaire.

---

## 7. Recherche Locale de Contenus (F17)

L'application dispose d'un moteur de recherche locale extrêmement flexible pour retrouver instantanément vos chaînes, films et séries :

1. **Saisie par sous-chaînes (fragments de mots)** : Vous n'avez pas besoin de connaître le début exact d'un mot. La saisie d'une portion de mot, qu'elle soit située au début, au milieu ou à la fin (ex: chercher `pilami` ou `lami`), remontera correctement les médias associés (ex: `Le Marsupilami`).
2. **Tolérance aux accents et à la casse** : La recherche est totalement insensible à la casse (majuscules/minuscules) et aux accents. Par exemple, saisir `odysee`, `ODYSEE` ou `odysée` affichera le programme `Odysée`. De même, les ligatures et caractères spéciaux courants sont gérés de manière transparente (ex: chercher `coeur` trouvera `Cœur`).
3. **Mots multiples dans le désordre** : Si vous saisissez plusieurs mots séparés par des espaces, l'application s'assure que chaque mot est présent dans le média, peu importe leur ordre de saisie ou les champs dans lesquels ils se trouvent (ex: chercher `reno jean` affichera les films avec l'acteur `Jean Reno`).
4. **Recherche étendue sur crédits** : Pour les films et séries, la recherche examine le titre, la catégorie thématique, mais également les acteurs, le réalisateur et le genre littéraire. Vous pouvez d'ailleurs cliquer directement sur le nom d'un acteur ou d'un réalisateur depuis une fiche de détails pour lancer une recherche instantanée de ses œuvres associées (ce qui réinitialise automatiquement vos anciens filtres de recherche pour éviter les résultats vides inattendus).
5. **Filtres de recherche avancée** : En cliquant sur le bouton de filtre, vous pouvez restreindre les résultats par type de média (uniquement la TV en direct, uniquement les films, uniquement les séries), par catégorie d'abonnements, ou encore trier les résultats par date d'ajout, note ou nom. Ces filtres s'appliquent de manière cumulative avec votre saisie textuelle.

### 📺 Optimisation de l'Interface de Recherche sur Android TV
Pour une meilleure ergonomie à la télécommande (D-pad), plusieurs simplifications ont été apportées :
* **Masquage de la croix d'effacement (B21)** : Dans le champ de recherche de l'écran Recherche globale, le bouton d'effacement rapide (croix) a été retiré en mode TV car il est physiquement inatteignable à la télécommande, évitant ainsi un élément inerte confus (le mobile conserve ce bouton d'effacement rapide).
* **Retrait des barres de recherche locales de catégories (F27)** : Les champs de recherche locale qui apparaissaient auparavant dans l'en-tête de catégorie spécifique sur Films (VOD) et Séries ont été supprimés sur TV. La saisie de texte à la télécommande étant fastidieuse, cette suppression épure le bandeau supérieur pour ne conserver que les sélecteurs de catégories et le bouton de filtres avancés (le mobile conserve la recherche par catégorie et la recherche globale reste disponible pour tous sur TV).

---

## 8. Paramètres de l'application

* **Langues par défaut** : Définissez vos préférences de pistes audio et de sous-titres (ex: Français par défaut). Le lecteur ExoPlayer tentera systématiquement de sélectionner ces pistes en priorité lors du démarrage d'une vidéo.
* **Filtrage des catégories** : Si certaines catégories de chaînes ou de VOD ne vous intéressent pas (ex : langues étrangères, chaînes thématiques inutilisées), vous pouvez les masquer complètement dans les paramètres afin d'épurer l'interface de l'application.
* **Synchronisation** : Vous pouvez forcer manuellement la mise à jour du catalogue de votre serveur IPTV ou régler la fréquence de mise à jour automatique en arrière-plan.

---

## 9. Gestion de l'historique de visionnage local (F8)

Vous pouvez à tout moment nettoyer et contrôler vos listes de reprise et vos chaînes récentes. Chaque suppression est entièrement privée et isolée sur votre profil de visionnage local.

### 📱 Sur smartphone et tablette (Tactile)
1. **Geste** : Effectuez un **appui long** sur la carte du contenu que vous souhaitez retirer de la liste :
   - *Continuer à regarder* sur les écrans Accueil, Films et Séries (y compris la vue étendue).
   - *Chaînes récentes (Récemment vus)* sur l'écran TV en direct.
2. **Dialogue de confirmation** : Une boîte de dialogue apparaît. Cliquez sur **Retirer de la liste** pour confirmer, ou sur **Annuler** pour laisser l'élément inchangé.
3. Le contenu disparaît instantanément de l'interface graphique.

### 📺 Sur Android TV / Fire TV (Télécommande)
1. **Geste** : Positionnez le focus de votre télécommande sur la carte du média à retirer, puis **maintenez enfoncé le bouton central de validation (OK/Entrée)** pendant environ 1 seconde.
2. **Dialogue de confirmation** : La boîte de dialogue s'affiche avec le focus par défaut sur le bouton **Annuler** afin de prévenir tout clic accidentel. Naviguez vers la droite pour sélectionner **Retirer de la liste** et validez.
3. Lorsque vous validez la suppression, l'interface graphique se rafraîchit immédiatement, et l'application s'assure qu'au relâchement de la touche, le lecteur vidéo ne soit pas démarré par erreur.

### 💡 Comportements spécifiques
* **Gestion des séries** : Lorsque vous retirez une série de la liste « Continuer à regarder », vous indiquez que l'épisode représenté (affiché sur la carte) n'est pas vu. Seule la position de lecture de cet épisode spécifique est effacée. Si vous avez d'autres épisodes en cours de lecture pour cette même série, la carte s'actualisera automatiquement pour afficher le prochain épisode à reprendre. Elle disparaîtra définitivement s'il n'en reste aucun autre.
* **Recommandations** : La suppression d'un film ou d'un épisode de série invalide automatiquement le cache des recommandations personnalisées sur la page d'Accueil afin d'adapter instantanément les suggestions à vos goûts mis à jour.

---

## 10. Évaluation des films et séries (F7)

Pour vous aider à personnaliser l'application et à sculpter vos recommandations, vous pouvez émettre un avis explicite sur n'importe quel film (VOD) ou série de votre catalogue. Tout comme l'historique et les favoris, les votes sont strictement privés et isolés par profil.

### 📝 Exprimer ses préférences
Sur la fiche détaillée d'un film ou d'une série, vous disposez de deux boutons d'action : **J'aime** et **Je n'aime pas** :
* **Aucun vote (neutre)** : Les deux boutons sont affichés dans un état inactif (contours fins).
* **Voter J'aime** : Le bouton « J'aime » s'illumine en violet (`AccentLavande`) tandis que « Je n'aime pas » reste neutre. Un média aimé contribue fortement (poids multiplié par 3) à vos goûts pour vous proposer des contenus similaires, mais il est masqué des recommandations pour privilégier la découverte de nouveaux contenus.
* **Voter Je n'aime pas** : Le bouton « Je n'aime pas » s'illumine en violet. Le média rejeté est définitivement banni de toutes vos recommandations et de vos calculs de goûts.
* **Annuler son vote** : Appuyez à nouveau sur le bouton actuellement actif pour le faire revenir à l'état neutre.
* **Changer son vote** : Cliquez directement sur l'autre bouton pour intervertir instantanément votre avis.

### ⚠️ Effets collatéraux d'un rejet (DISLIKE)
Marquer un contenu comme **Je n'aime pas** entraîne immédiatement les actions de nettoyage suivantes pour votre profil actif :
1. Le média est **retiré de vos Favoris** s'il y figurait.
2. Le média est **retiré de la liste « Continuer à regarder »** (votre progression de lecture ou celle de tous les épisodes de la série est supprimée).
*Note : Ces suppressions sont définitives. Si vous annulez ultérieurement votre vote négatif, le favori et la progression ne seront pas restaurés automatiquement.*

### 💡 Rafraîchissement en temps réel
Dès que vous validez un vote (positif, négatif ou neutre), l'application recalcule instantanément vos suggestions personnalisées. Si vous retournez sur la page d'Accueil, les carrousels de recommandations reflètent immédiatement vos préférences fraîchement exprimées.

---

## 11. Visibilité de la barre de statut sur Mobile (F11)

L'application améliore votre confort de navigation sur les smartphones et tablettes en affichant l'heure, l'état de la batterie et vos notifications tout en protégeant les contrôles d'interface.

### 📱 Affichage de la barre de statut
* **En navigation** : Lorsque vous parcourez les catalogues de films, de séries ou d'accueil, la barre d'état Android reste visible en haut de l'écran avec une couleur de texte claire parfaitement lisible sur le thème sombre de l'application.
* **Sécurité visuelle (Pas d'obstruction)** : Tous les écrans et en-têtes de l'application s'adaptent automatiquement aux dimensions physiques de votre appareil. Les boutons de retour, les titres d'écrans et les menus ne passent jamais sous l'encoche (notch) ou le poinçon de la caméra frontale de votre téléphone.
* **En lecture vidéo (Mode Immersif)** : Dès que vous lancez un film, une série ou un flux de télévision en direct (Live TV), l'application masque automatiquement toutes les barres système. Vous bénéficiez ainsi d'une expérience plein écran totale sans distraction visuelle. Les barres réapparaissent instantanément dès que vous quittez la lecture.

### 📺 Version Android TV
* Sur Android TV, le concept de barre de statut ou de poinçon n'existe pas. L'affichage en plein écran constant et la navigation à la télécommande restent parfaitement préservés sans aucun changement visuel.

---

## 12. Lecture automatique du trailer sur l'accueil (F10)

L'écran d'Accueil mobile propose une lecture automatique dynamique et interactive des bandes-annonces YouTube (si disponibles) pour le média actuellement à l'affiche dans le carrousel des **Tendances du moment**.

### 📱 Fonctionnement sur smartphone et tablette
1. **Délai de démarrage** : Lorsque vous ouvrez l'écran d'Accueil ou faites défiler le carrousel des tendances, le poster statique s'affiche normalement. Si vous restez immobile sur la même page pendant **5 secondes consécutives**, la lecture de la bande-annonce commence automatiquement en arrière-plan de la carte.
2. **Autoplay silencieux** : Afin d'éviter de perturber votre navigation, la vidéo démarre systématiquement en mode **muet**.
3. **Contrôle sonore (Sourdine)** : Une icône de haut-parleur s'affiche discrètement en bas à droite de la carte. Cliquez dessus pour activer ou couper le son du trailer. Ce choix est temporaire et propre à l'aperçu en cours (tout nouveau trailer démarrera muet).
4. **Navigation conservée** : Cliquer n'importe où ailleurs sur la carte de tendance conserve son comportement habituel et ouvre instantanément la fiche détaillée du film ou de la série, en stoppant proprement le trailer en cours.

### 🔄 Interruption immédiate du trailer
Pour ne pas gaspiller vos données mobiles ni consommer de ressources CPU, la vidéo s'interrompt de force et le poster réapparaît instantanément dans les cas suivants :
* Vous faites défiler le carrousel vers un autre média.
* Vous quittez l'écran d'Accueil (changement d'onglet dans la barre de navigation).
* Vous ouvrez une fiche de détails.
* Vous quittez l'application ou la mettez en arrière-plan.

### 💡 Résilience automatique
Si aucun trailer n'est configuré par votre fournisseur IPTV ou trouvé sur TMDB, ou si une erreur réseau survient lors du chargement du lecteur, l'application reste sur l'affiche statique (poster) d'origine de manière transparente, sans aucun message d'erreur gênant.

### 📺 Version Android TV
Afin de préserver la navigation par focus au bouton (D-pad) caractéristique des téléviseurs, l'autoplay des trailers est **désactivé** sur Android TV. Le carrousel y reste statique et fidèle à l'expérience d'origine.

---

## 13. Navigation vers la fiche détails depuis le clic sur la cover du player (F16)

Vous pouvez à tout moment basculer instantanément du lecteur vidéo (Films ou Séries) vers la fiche détaillée du média en lecture en sélectionnant simplement l'affiche (cover) située dans les contrôles.

### 📱 Sur smartphone et tablette (Tactile)
1. **Geste** : Ouvrez les contrôles du lecteur vidéo, puis touchez simplement la jaquette (cover) du média située en bas à gauche de l'écran (à côté du titre).
2. Le lecteur s'arrête proprement, mémorise votre progression de lecture et vous redirige directement vers la fiche détaillée du film ou de la série correspondante.

### 📺 Sur Android TV / Fire TV (Télécommande)
1. **Geste** : Affichez les contrôles du lecteur. Naviguez vers le bas pour amener le focus sur la jaquette (cover) en bas à gauche. Son focus est clairement signalé par une bordure d'accentuation violette.
2. **Validation** : Appuyez sur le bouton central **OK/Sélectionner** de votre télécommande pour valider l'action.
3. Le lecteur s'arrête et vous amène directement sur la fiche détaillée.

### 🔄 Comportement intelligent du retour arrière (Backstack)
* **Pas de doublon** : Si vous aviez lancé la lecture depuis la fiche détaillée, cliquer sur la cover effectue un simple retour arrière pour revenir sur cette même fiche. L'application évite d'empiler des fenêtres en double dans votre historique.
* **Fermeture puis ouverture** : Si la lecture a été lancée directement depuis l'Accueil (ex: « Continuer la lecture ») ou depuis vos Téléchargements sans passer par la fiche, l'application ferme d'abord proprement le lecteur vidéo puis vous ouvre la fiche détaillée. Ainsi, un retour arrière depuis cette fiche vous ramène directement à l'écran d'accueil d'origine, sans repasser par le lecteur vidéo.

### 💡 Cas particuliers
* **Mode hors-ligne** : Si vous lisez un contenu téléchargé sans connexion internet, cliquer sur la cover ouvrira la fiche détaillée en utilisant uniquement les données et jaquettes stockées localement sur votre appareil.
* **Erreur ou absence de métadonnées** : Si pour une raison exceptionnelle (vieux téléchargement orphelin, etc.) l'application ne parvient pas à identifier la fiche correspondante, la lecture de votre vidéo n'est pas interrompue. Un message transitoire s'affiche brièvement pour vous informer qu'aucune fiche n'est disponible.

---

## 14. Lecture automatique du trailer YouTube sur les fiches de détail (Films/Séries) (F13)

Pour rendre l'exploration de vos fiches de détail encore plus vivante, l'application lance automatiquement la bande-annonce (trailer) du film ou de la série en arrière-plan après quelques secondes.

### 📱 Fonctionnement général (Mobile & Android TV)
1. **Délai d'estompage** : Lorsque vous ouvrez une fiche de détails, celle-ci s'affiche immédiatement avec son affiche de fond statique. Si vous restez stable sur cette même fiche pendant **5 secondes**, l'affiche de fond s'estompe délicatement pour laisser place à la bande-annonce YouTube lue en arrière-plan.
2. **Autoplay silencieux** : Afin d'éviter toute gêne sonore, la bande-annonce démarre systématiquement en mode **muet**.
3. **Activation du son (Bouton d'action)** : Un bouton haut-parleur (icône son actif/couper) apparaît en haut de l'écran à côté des autres boutons d'actions (Retour, Favori, etc.) dès que la vidéo démarre. Cliquez dessus ou sélectionnez-le au D-Pad pour activer ou couper le son. 
4. **Coupure immédiate** : La bande-annonce s'arrête instantanément dès que :
   - Vous quittez la fiche de détails (retour arrière).
   - Vous lancez la lecture plein écran de la vidéo principale.
   - Vous mettez l'application en arrière-plan.

### 📺 Version Android TV
Sur Android TV, la vidéo se lance de la même manière sans jamais capturer le focus de votre télécommande. Vous pouvez naviguer en toute liberté au D-pad sur les différents boutons d'actions (Lecture, Favori, etc.) sans être interrompu ou bloqué par la vidéo.

### 💡 Optimisations & Mode hors-ligne
* **Économie de données** : Si vous revenez sur une fiche détaillée déjà consultée, l'application réutilise instantanément les métadonnées de bande-annonce déjà mémorisées dans sa base de données locale (Room) pour éviter des requêtes réseau inutiles.
* **Aucun trailer disponible** : Si le média n'a pas de bande-annonce trouvée (ou si vous êtes déconnecté sans données locales), la fiche reste parfaitement fonctionnelle avec son affiche de fond statique d'origine de manière invisible et fluide.

---

## 15. Nouvelle interface de l'Accueil Android TV (F18)

L'accueil d'Android TV propose une expérience modernisée, plus fluide et immersive grâce à une mise en avant éditoriale dynamique.

### 🌟 Hero Card (Tendance en vedette)
En haut de votre écran d'Accueil sur Android TV, vous découvrirez une grande **Hero Card** mettant en valeur la principale tendance du moment issue des flux TMDB mondiaux.
* **Informations claires** : Elle affiche en grand l'affiche du média, son titre, son année de sortie, et un badge indiquant s'il s'agit d'un film ou d'une série.
* **Lecture automatique de bande-annonce** : Si vous placez le focus de votre télécommande sur cette carte et que vous y restez stable pendant **1,5 seconde**, l'image fixe se transforme doucement pour laisser place à la bande-annonce YouTube du film ou de la série en cours, lue silencieusement en boucle.
* **Contrôle sonore direct** : Pendant la lecture de l'aperçu, un bouton de contrôle du son (haut-parleur) s'affiche directement sur la carte. Vous pouvez l'atteindre à la télécommande pour activer ou couper le son à tout moment.
* **Coupure de sécurité** : Dès que vous naviguez vers un autre élément, que vous changez d'onglet, que vous ouvrez une fiche ou que l'application passe en arrière-plan, la lecture s'arrête instantanément pour économiser vos ressources et garantir une fluidité impeccable.
* **Accès d'un clic** : Appuyez sur le bouton central de sélection de votre télécommande sur la carte pour ouvrir instantanément la fiche détaillée du média.

### 🔗 Liens « VOIR TOUT » revisités
Afin de ne pas surcharger l'écran, les rangées de médias (Films, Séries) proposent à leur extrémité un lien textuel discret **« VOIR TOUT »**.
* Au repos, il est de couleur lavande et s'intègre harmonieusement à l'écran.
* Lorsque vous l'atteignez avec le focus de votre télécommande, il se pare d'un fond lavande translucide à coins arrondis très visible, vous confirmant sans ambiguïté que vous pouvez cliquer dessus pour parcourir le catalogue complet associé.

---

## 16. Gestion dynamique de la synchronisation & Bandeau hors-ligne (T7)

L'application gère intelligemment la fraîcheur de son catalogue local et s'adapte à vos préférences pour rendre l'utilisation agréable et discrète.

### ⚙️ Fréquence de synchronisation respectée
L'application n'applique plus de délai de validité fixe de 24 heures pour tout le monde. Elle calcule la fraîcheur du catalogue local selon l'option que vous choisissez dans les paramètres de synchronisation :
* **Quotidienne (DAILY)** : Votre catalogue est considéré comme périmé après 24 heures.
* **Hebdomadaire (WEEKLY)** : Les données restent considérées comme fraîches pendant 7 jours.
* **Mensuelle (MONTHLY)** : Le catalogue reste valide pendant 30 jours.
* **Désactivée (DISABLED)** : Aucune synchronisation automatique liée à l'ancienneté n'est déclenchée. Vous continuez de consulter vos données locales sans aucune contrainte de temps ou relance automatique.

### 🚀 Synchronisation silencieuse et invisible
Lorsque vous ouvrez les sections Live TV, Films ou Séries :
* Si votre catalogue local est expiré et que votre appareil est **connecté à Internet**, l'application lance automatiquement une mise à jour en tâche de fond. C'est entièrement invisible : vous ne subissez aucun écran de chargement, aucun bandeau d'attente ni bouton "Réessayer". Vous naviguez normalement, et les données se mettent à jour d'elles-mêmes.
* Les échecs réseau transitoires (micro-coupures, serveur temporairement injoignable) sont interceptés en silence pour vous éviter d'avoir des alertes ou des bannières d'erreur intempestives.

### 📶 Rôle informatif du bandeau hors-ligne (OfflineBanner)
* Le bandeau d'avertissement n'apparaît **que si votre appareil est réellement hors ligne** (pas de connexion Internet) et que vous consultez des données stockées dans le cache. Il indique alors clairement la date de votre dernière synchronisation réussie à titre purement indicatif.
* Dès que votre connexion Internet est rétablie, le bandeau disparaît automatiquement pour vous laisser profiter pleinement de votre expérience en ligne.

---

## 17. Stabilité visuelle absolue de l'Accueil (T8)

Pour vous éviter des sauts visuels désagréables (saut de cartes, modification de focus) pendant que vous parcourez l'écran d'Accueil, les carrousels **"Top 10" (Films et Séries)** utilisent désormais une stratégie de stabilité stricte par session.

* **Figeage pour la session active** : Lorsque vous ouvrez l'application, l'Accueil charge immédiatement le dernier Top 10 persistant en cache (qu'il soit récent ou périmé de plus de 24h). Ces éléments sont alors figés et ne bougeront pas d'un millimètre pendant tout le temps où vous parcourez l'Accueil ou changez de catégories.
* **Rafraîchissement silencieux en tâche de fond** : Si le cache affiché a dépassé 24 heures, l'application lance une mise à jour réseau discrète en arrière-plan pour récupérer et sauvegarder le nouveau classement populaire TMDB. Cette actualisation n'applique **jamais** de changement sur l'écran en cours d'utilisation afin d'éviter tout décalage visuel brusque sous vos yeux.
* **Prise en compte au prochain démarrage** : C'est lors de votre prochaine ouverture de l'application que ces nouvelles données populaires seront lues depuis le cache et affichées comme nouveau point de départ stable.
* **Premier démarrage à froid** : Si l'application est lancée pour la toute première fois et qu'aucun cache n'est disponible, la mise à jour réseau applique directement les résultats dès réception afin d'éviter de laisser une rangée vide.

---

## 18. Défilement à sélecteur fixe sur Android TV (F19)

Afin d'offrir une navigation TV haut de gamme, cinématique et reposante pour les yeux, l'application CSTV propose un défilement à "sélecteur fixe" (Fixed Focus / Pivot Scrolling) sur Android TV.

### 🎯 Qu'est-ce que le sélecteur fixe ?
* **Sur d'autres applications classiques** : Lorsque vous appuyez sur les flèches de la télécommande, c'est le cadre du focus qui traverse l'écran d'élément en élément. La liste ne défile que lorsque vous atteignez les bords gauche ou droit. Vos yeux doivent sans cesse suivre le déplacement du cadre à l'écran.
* **Avec le sélecteur fixe (Fixed Focus)** : Le cadre visuel du focus reste **fixe et immobile** à un endroit stratégique de l'écran. Lorsque vous appuyez sur les flèches, ce sont les cartes de la liste qui glissent de manière fluide en arrière-plan sous votre cadre de focus. Vos yeux restent ainsi ancrés confortablement au même endroit de l'écran pour lire le catalogue.

### 📺 Fonctionnement sur Android TV
Le sélecteur fixe s'active automatiquement dès que l'application est lancée sur un téléviseur Android TV ou un boîtier TV, et s'applique aux écrans d'Accueil, de TV en Direct, de VOD, de Séries, de Favoris et aux Résultats de recherche.
* **Défilement Horizontal (15 % de l'écran)** : Lorsque vous naviguez dans une rangée horizontale, la carte active s'aligne et reste idéalement ancrée à environ **15 % de la largeur utilisable** depuis le bord gauche de l'écran. Vous visualisez ainsi parfaitement la suite des contenus à droite.
* **Défilement Vertical (50 % de l'écran)** : Lorsque vous vous déplacez vers le haut ou vers le bas entre les différentes rangées, la rangée focalisée est automatiquement recentrée à **50 % de la hauteur de l'écran**, vous garantissant un équilibre visuel parfait.
* **Butée naturelle aux extrémités** : Le pivot de 15 % ou 50 % est maintenu tant que la liste contient des éléments. Lorsque vous atteignez le tout début ou la toute fin d'une liste, la liste s'arrête naturellement à ses bords réels (sans laisser de grand espace vide inutile) et le focus se déplace alors vers le bord pour atteindre la première ou la dernière carte, sans jamais risquer de perdre le focus ou de naviguer dans le vide.
* **Résistance aux chargements** : Lors de la navigation dans les grilles à plusieurs colonnes (Films, Séries) ou lors de l'apparition progressive de sections de l'Accueil, le défilement s'adapte de manière asynchrone pour recentrer instantanément la ligne active dès qu'elle est affichée.

### 📱 Préservation de la version Mobile
* Cette fonctionnalité est strictement réservée à l'expérience TV à la télécommande.
* Sur smartphone ou tablette, le défilement tactile classique, le comportement des cartes et la mémorisation de position restent parfaitement inchangés.



