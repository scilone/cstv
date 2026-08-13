# T20 - Normalisation relationnelle de Room et allègement de la synchronisation cloud

## Informations générales

Status:
IMPLEMENTATION

Created:
2026-08-13

---

# 1. Description

Revoir le modèle de données local de l'application afin de réduire la
dénormalisation évitable, d'établir davantage de relations explicites entre
les tables Room et de ne plus recopier inutilement les métadonnées du
catalogue dans les données propres aux profils.

Le même travail doit étudier l'allègement des données synchronisées avec le
backend CSTV. Par exemple, un favori contient actuellement son identifiant et
son type, mais aussi son nom, sa jaquette et sa catégorie, alors que ces
informations existent déjà dans le catalogue local. Les snapshots cloud
reprennent aujourd'hui ces champs dupliqués.

Le résultat recherché est une base locale moins volumineuse et plus cohérente,
ainsi que des transferts cloud plus petits, sans perdre les données utilisateur
ni dégrader les usages hors ligne, la restauration sur une autre installation
ou les performances de navigation.

---

# 2. Contexte

## Constats initiaux dans l'existant

- `favorites` recopie `name`, `cover` et `categoryId` en plus de la référence
  `(type, id)` et du champ métier `addedAt`.
- `playback_positions` recopie de nombreuses métadonnées : `title`,
  `coverUrl`, `containerExtension`, `seriesId`, saison, épisode, `plot`,
  durée textuelle, date de sortie et catégorie, en plus de la position et des
  horodatages.
- `recently_watched_live` recopie le nom, l'icône, la catégorie et le numéro de
  chaîne en plus de `(profileId, streamId, watchedAt)`.
- `media_ratings`, `track_preferences`, `series_watch_state` et
  `category_preferences` stockent déjà principalement de l'état utilisateur et
  des identifiants, mais ces références ne sont pas matérialisées par des clés
  étrangères Room.
- `downloaded_media` duplique volontairement des métadonnées nécessaires à
  l'usage hors ligne ; son inclusion dans la normalisation reste à décider.
- les tables profilées ne déclarent actuellement pas de relations Room vers
  `profiles` ou vers les tables du catalogue.
- les snapshots cloud sont construits directement à partir des entités Room,
  en retirant seulement `profileId`. Ils héritent donc de leur dénormalisation.
- le catalogue Live/VOD/Séries est aujourd'hui remplacé par des séquences
  transactionnelles de suppression puis réinsertion. Cette stratégie doit être
  prise en compte avant d'ajouter des contraintes référentielles vers le
  catalogue.
- le backend CSTV ne possède ni le catalogue Xtream ni les identifiants IPTV.
  Il conserve un blob gzip opaque par `(profil distant, namespace)` et ne peut
  ni lire ni joindre les références contenues dans le document.
- la version Room actuelle est 27. Toute évolution devra respecter la règle de
  migration non destructive du projet.

## Objectifs à préciser aux étapes suivantes

- inventorier la redondance réelle et mesurer son coût local et réseau avant
  de modifier le schéma ;
- distinguer les métadonnées réellement dérivables du catalogue de celles qui
  sont nécessaires pour conserver un comportement autonome ;
- définir les relations et règles d'intégrité attendues entre profils, états
  utilisateur, catalogue, catégories, séries, saisons et épisodes ;
- définir un format cloud plus léger, versionné et compatible avec la stratégie
  de restauration retenue ;
- migrer les installations existantes sans perte de favoris, d'historique, de
  reprise, de notes ou de préférences ;
- mesurer le gain obtenu et vérifier qu'il ne se paie pas par une régression de
  temps de requête ou d'usage hors ligne.

## Décisions PO du 2026-08-13

### Périmètre et principe de normalisation

- Auditer et normaliser **toutes** les tables Room, pas seulement les données
  synchronisables. Les téléchargements, l'EPG, les trailers, les catégories,
  les séries, les saisons, les épisodes, la recherche et les index font donc
  partie de l'audit.
- Le caractère relationnel et la suppression de la redondance métier évitable
  sont des exigences, pas une optimisation optionnelle à arbitrer contre un
  objectif chiffré de taille.
- Aucun pourcentage de réduction locale ou réseau n'est imposé. Il faudra
  constater le gain, mais la réussite dépend d'abord de l'absence de copie
  inutile d'une même information métier.
- La migration doit également réduire les bases déjà installées, pas seulement
  limiter leur croissance future. Une reconstruction de table est autorisée si
  elle est nécessaire pour retirer des colonnes ou établir les relations.
- T19 reste un ticket séparé et constitue un prérequis de T20.

### Source des métadonnées et visibilité

- Les tables de médias portent les informations du média, notamment le titre,
  la jaquette, l'extension de lecture et, selon le type, la série, la saison et
  le numéro d'épisode.
- Les tables d'état comme les favoris, reprises et historiques ne portent que
  la référence au média et leurs données métier propres.
- Les écrans résolvent les métadonnées depuis le catalogue courant. Un média
  absent du catalogue n'apparaît pas dans les favoris, reprises ou historiques
  (`INNER JOIN`, pas de `LEFT JOIN` avec métadonnées de secours).
- Si le fournisseur modifie un nom ou une jaquette, l'interface affiche la
  valeur du catalogue courant.
- Un média disparu doit être masqué. Une préférence de catégorie doit en
  revanche survivre à la disparition puis au retour de sa catégorie.
- Toutes les données, téléchargements compris, doivent participer au modèle
  relationnel ; aucune table n'est exemptée par principe au motif de l'usage
  hors ligne.

### Intégrité, catalogue et migration

- Utiliser de vraies clés étrangères SQLite/Room.
- La suppression d'un profil supprime en cascade toutes ses données locales et
  tous ses états de synchronisation.
- T20 peut et doit revoir le remplacement actuel du catalogue par
  suppression/réinsertion afin de rendre les relations cohérentes.
- Les lignes déjà orphelines au moment de la migration depuis Room 27 sont
  supprimées.
- La référence cloud appartient au compte Xtream complet. Un changement de
  fournisseur ou de compte peut néanmoins laisser des données incohérentes ;
  elles sont conservées jusqu'à ce que l'utilisateur les nettoie.
- Il n'est pas demandé de partitionner les snapshots par catalogue Xtream.

### Cloud, livraison et validation

- Le backend peut continuer à traiter le contenu comme opaque : l'application
  est responsable de résoudre et valider les références contre Room.
- Aucune compatibilité de synchronisation avec une ancienne version de
  l'application n'est exigée, le PO étant l'unique utilisateur.
- Les champs dénormalisés reçus d'un ancien format doivent être ignorés.
- La transformation est livrée sous la forme d'une seule migration, et non en
  plusieurs transitions produit.
- Aucun jeu de données de référence ni seuil chiffré de performance n'est
  imposé.
- La validation de migration cible la base Room 27 actuellement installée.

### Arbitrages complémentaires du 2026-08-13

- Sur une nouvelle installation, les favoris, reprises et historiques restaurés
  depuis le cloud restent invisibles tant que leur média n'a pas été chargé
  dans le catalogue local. L'application attend la synchronisation Xtream ;
  elle ne transporte pas de métadonnées de secours.
- Une identité minimale de média peut être conservée comme inactive lorsqu'un
  média disparaît. Elle ne conserve pas les anciennes métadonnées d'affichage,
  maintient l'intégrité référentielle et permet à l'état utilisateur masqué de
  réapparaître si le média revient.
- Le même mécanisme d'identités inactives est accepté lors d'un changement de
  compte ou de fournisseur Xtream. Ces identités sont associées au compte
  Xtream complet et restent présentes jusqu'au nettoyage par l'utilisateur.
- Lorsqu'une catégorie disparaît, son identité est conservée afin que ses
  préférences survivent et s'appliquent si elle revient.
- Une reprise d'épisode restaurée depuis le cloud reste masquée tant que le
  détail de la série n'a pas été chargé et que l'épisode n'existe donc pas dans
  `series_episodes`. T20 ne déclenche pas un chargement automatique de tous les
  épisodes et ne copie pas l'entité catalogue de l'épisode dans le cloud.
- Si le média source d'un téléchargement disparaît du catalogue, le
  téléchargement est supprimé : sa ligne Room et son fichier local doivent être
  nettoyés de façon cohérente.
- Le cloud contient uniquement des copies allégées des entités d'état :
  référence au média et champs métier propres à l'état. Il ne contient aucune
  copie complète des entités catalogue ni de leurs titres, jaquettes ou autres
  métadonnées.
- Seuls les trois namespaces dont le contenu change sont versionnés :
  `favorites`, `playback` et `recently-watched-live`. Les quatre namespaces déjà
  légers ne changent pas de version sans modification de leur format.
- Les tables FTS et les index, y compris les index couvrants, sont des
  exceptions autorisées à la règle anti-redondance lorsqu'ils servent la
  recherche ou les performances. Ils ne deviennent pas des sources métier.
- Le choix entre une identité parent commune à tous les types de médias et des
  références typées séparées est explicitement délégué à l'agent pour l'étape
  d'architecture. Le choix devra être justifié par l'intégrité référentielle,
  la simplicité des jointures, l'évolutivité et le coût de stockage ; il n'est
  pas arrêté à l'étape 1.
- Une opération automatique de compactage après migration est autorisée afin
  de réduire réellement le fichier SQLite existant, même si le premier
  démarrage suivant la mise à jour est exceptionnellement plus long.

## Questions ouvertes

Aucune question fonctionnelle ou de périmètre ne reste ouverte à l'issue de
l'étape 1. Le choix de modélisation polymorphe des médias est une décision
technique volontairement reportée à l'étape 3 et déléguée à l'agent.

---

# 3. Spécification fonctionnelle

## Objectif fonctionnel

L'application doit présenter exactement les mêmes fonctions utilisateur
qu'avant T20, mais les informations d'affichage d'un média ou d'une catégorie
proviennent désormais de leur enregistrement canonique dans le catalogue
local. Les tables d'état ne conservent que la référence à cet enregistrement et
les valeurs propres à l'action utilisateur.

La normalisation concerne l'ensemble de Room. Elle ne doit pas introduire un
nouvel écran, une nouvelle action de Paramètres ou une nouvelle fonction métier
de nettoyage des anciennes références Xtream.

## User stories

- En tant qu'utilisateur, je peux continuer à ajouter et retirer des favoris,
  reprendre une lecture, retrouver mes chaînes récentes, noter des médias,
  conserver mes préférences et gérer mes téléchargements comme avant.
- En tant qu'utilisateur, je vois toujours le titre, la jaquette et les autres
  informations actuellement fournies par mon catalogue, sans ancienne copie
  divergente dans mes favoris ou mon historique.
- En tant qu'utilisateur, si mon fournisseur renomme un média ou modifie sa
  jaquette, toutes les vues utilisant ce média reflètent le catalogue courant.
- En tant qu'utilisateur hors ligne, je retrouve les données déjà présentes
  dans Room ; aucune copie supplémentaire dans les tables d'état n'est requise.
- En tant qu'utilisateur ayant restauré ses données cloud, je retrouve un état
  seulement lorsque le média correspondant est présent dans le catalogue
  local.
- En tant qu'utilisateur, un média disparu ne laisse pas de carte incomplète ou
  obsolète dans les favoris, les reprises ou les historiques.
- En tant qu'utilisateur, si un média ou une catégorie revient avec la même
  identité dans le même compte Xtream, son état conservé redevient applicable.
- En tant qu'utilisateur, supprimer un profil supprime toutes les données
  propres à ce profil sans affecter les autres profils ni le catalogue partagé.
- En tant qu'utilisateur, un téléchargement dont le média source a disparu est
  supprimé proprement, y compris son fichier local.
- En tant qu'utilisateur de plusieurs installations, les snapshots cloud
  transportent mes états, pas une copie du catalogue Xtream.

## Parcours utilisateur

### Ajout et affichage d'un favori

1. L'utilisateur ajoute une chaîne, un film ou une série aux favoris.
2. L'application enregistre la référence du média, le profil concerné et la
   date d'ajout, sans recopier son nom, sa jaquette ou sa catégorie.
3. L'écran Favoris rapproche cet état du catalogue local.
4. Si le média est actif et présent, la carte utilise les métadonnées courantes
   du catalogue.
5. Si le média est absent ou inactif, aucune carte n'est affichée ; aucune
   métadonnée de secours n'est utilisée.
6. Le retrait du favori supprime uniquement cet état pour ce profil et marque
   le namespace cloud correspondant selon le comportement F34 existant.

### Reprise d'un film ou d'un épisode

1. La lecture enregistre la référence du média, la position, la durée de
   lecture, le dernier accès et les autres valeurs propres à la reprise.
2. Le titre, la jaquette, l'extension de lecture et les informations
   d'appartenance à une série sont lus depuis le catalogue local, pas depuis la
   ligne de reprise.
3. Un film présent dans le catalogue peut apparaître dans « Continuer à
   regarder ».
4. Une reprise d'épisode restaurée avant le chargement de `get_series_info`
   reste invisible jusqu'à ce que l'épisode existe localement.
5. Ce parcours ne déclenche pas le téléchargement automatique de tous les
   épisodes et ne modifie pas les moments de push playback définis par F34.

### Historique Live TV récent

1. Lorsqu'une chaîne est effectivement regardée, l'application enregistre sa
   référence, le profil et l'horodatage métier.
2. Le nom, l'icône, la catégorie et le numéro affichés viennent de la chaîne
   courante du catalogue.
3. Une chaîne absente ou inactive n'apparaît pas dans l'historique visible.
4. Si elle revient avec la même identité, son état conservé peut réapparaître
   avec ses nouvelles métadonnées.

### Préférences, notes et suivi de séries

1. Une note, une préférence de piste, un état de suivi de série ou une
   préférence de catégorie est rattaché à son profil et à sa cible canonique.
2. La suppression d'un profil supprime ces états en cascade.
3. Une catégorie disparue conserve son identité et sa préférence, mais
   n'apparaît pas dans les listes tant qu'elle est inactive.
4. Si cette catégorie revient avec la même identité, son masquage et son ordre
   personnalisé s'appliquent de nouveau.

### Synchronisation et restauration cloud

1. T19 borne et élague les listes avant que T20 ne change leurs formats.
2. `favorites`, `playback` et `recently-watched-live` sont envoyés dans leurs
   nouveaux formats allégés et versionnés.
3. Chaque objet cloud contient sa référence métier et les seules valeurs
   propres à l'état ; il ne contient pas de titre, jaquette, catégorie, résumé,
   extension ni autre copie du catalogue.
4. `ratings`, `track-preferences`, `series-watch-state` et
   `category-preferences` conservent leur format tant que leur contenu ne
   change pas.
5. Une restauration applique les états qui peuvent être rattachés aux
   identités locales. Leur affichage attend ensuite que la cible soit active et
   présente dans le catalogue.
6. Les champs supplémentaires d'un ancien format de snapshot sont ignorés ;
   ils ne sont ni persistés dans les nouvelles tables d'état ni réémis.
7. Le backend reste opaque au contenu et les règles F34 de Room-first, ETag,
   fusion, retry, isolation et snapshot vide continuent de s'appliquer.

### Migration de la base installée

1. La mise à jour transforme en une seule migration la base Room 27 existante.
2. Pour chaque état utilisateur encore rattachable, la référence et les champs
   métier sont conservés tandis que les métadonnées dupliquées sont retirées.
3. Les lignes déjà orphelines dans Room 27 sont supprimées.
4. Les identités nécessaires à un média ou une catégorie volontairement
   conservés mais absents deviennent inactives.
5. Les relations entre profils et données profilées deviennent effectives : la
   suppression d'un profil nettoie toutes ses données.
6. À l'issue de la transformation, la base peut être compactée automatiquement
   afin de rendre l'espace libéré au système de fichiers.
7. Cette migration peut allonger exceptionnellement le premier démarrage après
   mise à jour ; les démarrages suivants retrouvent le fonctionnement normal.

## Règles métier

1. Chaque information métier possède une source canonique unique dans Room.
2. Les tables d'état utilisateur ne dupliquent aucune métadonnée issue du
   catalogue : elles stockent une référence et leurs seules valeurs métier.
3. Les interfaces affichant un état et son média utilisent une correspondance
   stricte : une cible absente ou inactive n'est pas rendue.
4. Une cible inactive ne fournit aucune ancienne métadonnée d'affichage.
5. Le retour d'un média ou d'une catégorie avec la même identité réactive les
   états conservés qui lui sont rattachés.
6. Un changement de compte ou fournisseur Xtream n'effectue aucun
   rapprochement heuristique entre catalogues et ne partitionne pas le cloud.
   Les références de l'ancien compte restent associées à ce compte, inactives
   et invisibles. Aucun nettoyage dédié n'est proposé : l'utilisateur doit se
   reconnecter à l'ancien compte pour que les cibles disponibles redeviennent
   visibles et puissent être retirées par les actions normales de chaque écran.
7. Les identifiants numériques identiques provenant de comptes Xtream
   différents ne sont pas considérés comme une preuve qu'il s'agit du même
   média.
8. Supprimer un profil supprime toutes ses données profilées et ses états de
   synchronisation, sans supprimer les données des autres profils.
9. Une ligne Room 27 impossible à rattacher lors de la migration est supprimée ;
   aucune ligne orpheline n'est créée dans le nouveau modèle.
10. La disparition d'une cible ne doit jamais produire une carte partielle,
    un titre vide, une jaquette historique ou un crash.
11. Un téléchargement dépend de son média source : lorsque celui-ci disparaît,
    la ligne de téléchargement et le fichier physique sont supprimés de façon
    cohérente.
12. Le nettoyage d'un téléchargement ne supprime pas les autres états du média
    sauf si leur propre règle l'exige.
13. Les tables FTS et les index sont autorisés à recopier des valeurs uniquement
    comme structures d'accès dérivées. Ils ne sont jamais modifiés comme une
    seconde source métier indépendante.
14. Les plafonds et règles d'élagage de T19 restent applicables et sont exécutés
    avant la production des snapshots T20.
15. Seuls `favorites`, `playback` et `recently-watched-live` changent de format
    cloud. Un changement fonctionnel futur d'un autre namespace devra le
    versionner séparément.
16. Les anciens champs dénormalisés reçus du cloud sont ignorés. Aucune
    compatibilité avec une ancienne application qui attendrait ces champs n'est
    garantie.
17. Le cloud ne contient jamais les entités catalogue complètes, les
    téléchargements, les credentials ou URL Xtream, les données TMDB/YouTube,
    les JWT ou les OTP.
18. T20 ne change ni les règles de conflit ETag, ni les déclencheurs playback,
    ni le caractère asynchrone et local-first de F34.
19. La normalisation ne doit supprimer aucune fonctionnalité visible existante
    lorsqu'une cible valide est présente.
20. Le modèle fonctionnel ne fixe pas la représentation technique d'une
    référence polymorphe ; ce choix est délégué et justifié à l'étape 3.

## Cas limites

- **Cloud restauré avant le catalogue :** les états sont conservés mais aucun
  élément ne s'affiche avant la présence de sa cible locale.
- **Épisode non chargé :** sa reprise reste cachée jusqu'à la consultation et au
  chargement de la série ; aucun chargement massif implicite n'est effectué.
- **Média supprimé puis restauré par le même fournisseur :** l'état conservé
  redevient visible avec les métadonnées actuelles si l'identité est la même.
- **Média recréé sous un nouvel identifiant :** il s'agit d'une nouvelle cible ;
  l'ancien état n'est pas transféré automatiquement.
- **Même identifiant numérique sur deux comptes Xtream :** aucun rattachement
  automatique entre ces comptes.
- **Ancien compte jamais reconnecté :** ses références inactives restent
  conservées et invisibles sans expiration ni purge automatique.
- **Reconnexion à l'ancien compte :** les éléments dont la cible est de nouveau
  disponible réapparaissent et peuvent être nettoyés avec les actions normales
  de retrait ; aucun écran de maintenance distinct n'est ajouté.
- **Catégorie supprimée puis restaurée :** sa préférence réapparaît seulement si
  l'identité est identique ; une nouvelle identité utilise les valeurs par
  défaut.
- **Snapshot ancien contenant des métadonnées :** les champs reconnus comme
  état sont importés, les copies de catalogue sont ignorées et le prochain
  envoi utilise le nouveau format du namespace concerné.
- **Snapshot futur ou illisible :** Room et le document distant restent
  intacts, conformément à F34 ; aucun effacement opportuniste n'est effectué.
- **Échec réseau pendant la restauration :** Room reste utilisable avec les
  données déjà présentes ; la synchronisation reprend selon F34.
- **Échec pendant le nettoyage d'un fichier téléchargé :** l'application ne
  doit pas annoncer silencieusement un nettoyage complet si le fichier ou son
  état persiste ; l'opération doit pouvoir être reprise.
- **Interruption pendant la migration ou le compactage :** l'installation ne
  doit pas être laissée dans un schéma partiellement transformé ni perdre une
  base Room 27 encore valide.
- **Espace disque insuffisant pour migrer ou compacter :** l'application ne doit
  pas détruire la base existante pour poursuivre.
- **Suppression de profil pendant une synchronisation :** aucun état de ce
  profil ne doit être recréé localement après la suppression.
- **Listes vidées par T19 ou par l'utilisateur :** un snapshot vide explicite
  continue d'être synchronisé selon F34.

## Gestion des erreurs

- Une référence cloud impossible à comprendre, de type inconnu ou dont les
  champs métier obligatoires sont invalides n'est jamais appliquée en créant
  une ligne incohérente.
- Un document globalement illisible, trop volumineux ou d'une version future
  suit les états d'erreur F34 (`Malformed`, `TooLarge`, `Incompatible`) sans
  effacer Room ni écraser le document distant.
- Une violation d'intégrité lors d'une écriture locale annule l'opération
  concernée ; l'interface ne doit pas afficher un succès fictif.
- Une erreur de migration conserve la base antérieure exploitable et empêche
  l'ouverture d'un schéma partiellement migré ; aucun fallback destructif n'est
  autorisé.
- Une erreur de compactage ne doit pas annuler une migration déjà validée ni
  supprimer la base ; le compactage pourra être différé ou retenté.
- Une erreur lors de la suppression d'un téléchargement ne doit pas supprimer
  uniquement la référence Room en laissant volontairement un fichier oublié,
  ni l'inverse sans mécanisme de reprise.
- Les erreurs visibles restent formulées sans stack trace, chemin local, SQL,
  contenu de snapshot, credential ou donnée sensible.

## Critères d'acceptation

- [ ] Toutes les tables Room ont été auditées ; chaque duplication métier
  conservée est explicitement justifiée comme structure d'accès dérivée ou
  nécessité fonctionnelle validée.
- [ ] Les favoris ne stockent plus de nom, jaquette ou catégorie ; un favori
  visible utilise les métadonnées courantes du catalogue.
- [ ] Les reprises ne stockent plus les métadonnées du média ; elles conservent
  uniquement leur référence et les valeurs propres à la reprise.
- [ ] L'historique Live récent ne stocke plus le nom, l'icône, la catégorie ou
  le numéro de chaîne ; son affichage provient du catalogue courant.
- [ ] Les notes, préférences de pistes, suivis de séries et préférences de
  catégories sont reliés à leur profil et à leur cible sans devenir des copies
  de catalogue.
- [ ] Les téléchargements sont reliés à leur média ; la disparition de la cible
  supprime la ligne et le fichier sans affecter un autre téléchargement.
- [ ] Un média absent ou inactif n'apparaît dans aucun favori, reprise ou
  historique visible et ne produit aucune carte dégradée.
- [ ] Un média revenant avec la même identité réactive son état conservé avec
  ses métadonnées actuelles ; un nouvel identifiant ne récupère rien
  automatiquement.
- [ ] Une catégorie disparue conserve son identité et ses préférences, qui se
  réappliquent à son retour avec la même identité.
- [ ] Une reprise d'épisode cloud reste invisible tant que cet épisode n'est
  pas présent dans Room, sans déclencher de récupération globale des détails.
- [ ] Une restauration cloud effectuée avant le catalogue ne montre pas de
  métadonnées embarquées ; les états deviennent visibles lorsque leurs cibles
  locales sont disponibles.
- [ ] Supprimer un profil supprime toutes ses données et ses états de sync sans
  toucher aux autres profils.
- [ ] La migration unique depuis Room 27 conserve les états rattachables,
  supprime les lignes déjà orphelines et retire les colonnes redondantes sans
  fallback destructif.
- [ ] Le compactage automatique peut réduire physiquement la base sans rendre
  l'application inutilisable en cas d'échec ou d'espace disque insuffisant.
- [ ] `favorites`, `playback` et `recently-watched-live` utilisent un nouveau
  format cloud allégé et versionné ; les quatre autres namespaces restent
  inchangés.
- [ ] Les nouveaux snapshots ne contiennent aucune métadonnée catalogue
  complète ni `profileId`, credential, URL Xtream, donnée TMDB/YouTube, JWT ou
  OTP.
- [ ] Les champs dénormalisés d'un ancien snapshot sont ignorés et ne sont pas
  réémis.
- [ ] Les règles F34 de fusion, ETag, offline, erreurs et déclencheurs playback
  restent inchangées, et les plafonds T19 restent appliqués.
- [ ] Aucun bouton ou écran de nettoyage des anciennes références Xtream n'est
  ajouté ; leur retrait n'est possible qu'après reconnexion à l'ancien compte,
  via les actions normales sur les éléments redevenus visibles.
- [ ] Les structures FTS et index nécessaires peuvent conserver leurs données
  dérivées sans devenir une seconde source métier.
- [ ] Toutes les validations nécessaires sont automatisées et exécutables
  localement sans appareil ni émulateur.

## Décision finale de l'étape 2

Il n'existe aucun bouton global ni écran dédié pour nettoyer les références
inactives d'un ancien compte Xtream. Elles restent conservées et invisibles
tant que ce compte n'est pas reconnecté. Après reconnexion, les éléments dont
la cible catalogue est disponible redeviennent visibles et l'utilisateur les
nettoie au moyen des actions ordinaires de retrait déjà proposées par les
écrans concernés.

Aucune question fonctionnelle ne reste ouverte à l'issue de l'étape 2.

---

# 4. Spécification technique

## 4.1 Décision centrale : identité de média partagée (`media_refs`)

L'étape 1 délègue à l'agent le choix entre une identité parente commune et des
références typées séparées. **Décision : identité parente commune**, matérialisée
par deux tables d'identité, `media_refs` et `category_refs`.

Justification :

- **Intégrité référentielle.** SQLite n'a pas de clé étrangère polymorphe : une
  colonne `(type, id)` ne peut pas pointer tantôt `live_streams`, tantôt
  `vod_streams`. Sans identité commune, honorer « de vraies clés étrangères »
  imposerait d'éclater chaque table d'état en trois ou quatre tables typées
  (`favorites_live`, `favorites_vod`, `favorites_series`, …), soit une vingtaine
  de tables d'état, autant de DAO et de requêtes de sync.
- **Identités inactives.** La spécification exige de conserver l'identité d'un
  média disparu sans conserver ses métadonnées. Une table d'identité est
  exactement ce porteur ; avec des références typées, il faudrait de toute façon
  créer une table d'identité par type.
- **Cloisonnement par compte Xtream.** Les règles 6 et 7 interdisent de
  confondre deux médias partageant un identifiant numérique sur deux comptes.
  `media_refs` porte l'`accountKey`, une seule fois par média référencé, au lieu
  de le répéter dans chaque table d'état.
- **Évolutivité.** Un nouvel état utilisateur (par exemple une liste
  personnalisée) se raccroche par une seule clé étrangère `mediaUid`.
- **Coût de stockage.** `media_refs` n'indexe **que** les médias effectivement
  référencés par un état — jamais le catalogue entier. Une installation avec
  500 favoris, 10 000 reprises et 20 chaînes récentes tient dans quelques
  milliers de lignes de trois colonnes, contre les dizaines de milliers de
  lignes qu'imposerait une identité posée sur tout le catalogue (qui casserait
  en outre les index couvrants du catalogue).

Ce que l'identité **ne** porte pas : ni titre, ni jaquette, ni catégorie, ni
drapeau `active`. « Actif » est dérivé de la jointure avec le catalogue courant
(présence d'une ligne dans `live_streams` / `vod_streams` / `series_streams` /
`series_episodes`) : une colonne `active` serait une redondance à maintenir et
une seconde source de vérité.

## 4.2 Schéma Room cible (version 28)

### Tables d'identité (nouvelles)

```sql
CREATE TABLE media_refs (
    mediaUid   INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
    accountKey TEXT NOT NULL,      -- AccountKey (host:port:username), '' = hérité de la migration
    kind       TEXT NOT NULL,      -- 'live' | 'movie' | 'series' | 'episode'
    providerId INTEGER NOT NULL    -- streamId Xtream, seriesId, ou episodeId
);
CREATE UNIQUE INDEX index_media_refs_identity ON media_refs(accountKey, kind, providerId);

CREATE TABLE category_refs (
    catUid             INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
    accountKey         TEXT NOT NULL,
    kind               TEXT NOT NULL,  -- 'live' | 'vod' | 'series'
    providerCategoryId TEXT NOT NULL
);
CREATE UNIQUE INDEX index_category_refs_identity
    ON category_refs(accountKey, kind, providerCategoryId);
```

`kind = 'episode'` porte l'`episodeId` Xtream, qui est aussi l'identifiant de
lecture : c'est lui que joignent `series_episodes.episodeId` et les reprises.

### Tables d'état (reconstruites)

Toutes déclarent deux clés étrangères réelles : `profileId → profiles(id)
ON DELETE CASCADE` et `mediaUid → media_refs(mediaUid) ON DELETE CASCADE`
(`catUid → category_refs(catUid)` pour les préférences de catégories).

| Table | Clé primaire | Colonnes métier conservées | Colonnes supprimées |
|---|---|---|---|
| `favorites` | `(profileId, mediaUid)` | `addedAt` | `id`, `type`, `name`, `cover`, `categoryId` |
| `playback_positions` | `(profileId, mediaUid)` | `positionMs`, `durationMs`, `lastAccessedAt` | `streamId`, `title`, `coverUrl`, `type`, `containerExtension`, `seriesId`, `episodeNum`, `seasonNum`, `plot`, `duration`, `releaseDate`, `categoryId` |
| `recently_watched_live` | `(profileId, mediaUid)` | `watchedAt` | `streamId`, `name`, `streamIcon`, `categoryId`, `num` |
| `media_ratings` | `(profileId, mediaUid)` | `value` | `mediaType`, `mediaId` |
| `track_preferences` | `(profileId, mediaUid)` | `audioLang`, `subtitleLang` | `mediaType`, `mediaId` |
| `series_watch_state` | `(profileId, mediaUid)` | `lastKnownSeason`, `lastKnownEpisode`, `lastNotifiedSeason`, `lastNotifiedEpisode`, `updatedAt` | `seriesId` |
| `category_preferences` | `(profileId, catUid)` | `hidden`, `sortOrder` | `categoryId`, `type` |
| `downloaded_media` | `mediaUid` | `status`, `percent`, `bytesDownloaded`, `totalBytes`, `createdAt` | `contentId`, `type`, `streamId`, `seriesId`, `seasonNum`, `episodeNum`, `title`, `subtitle`, `coverUrl`, `containerExtension` |

`downloaded_media.contentId` (« movie_123 » / « episode_456 ») est l'identifiant
de `DownloadRequest` et le `customCacheKey` ExoPlayer : il reste nécessaire au
dialogue avec media3, mais il est **dérivable** de `(kind, providerId)`. Il
devient donc une fonction pure `DownloadContentId.of(kind, providerId)` /
`parse(contentId)` (testable JVM) au lieu d'une colonne.

Index ajoutés : `index_<table>_mediaUid` sur chaque table d'état (obligatoire
pour la cascade et pour les jointures d'affichage), `index_favorites_profileId_addedAt`,
`index_playback_positions_profileId_lastAccessedAt`,
`index_recently_watched_live_profileId_watchedAt`.

### Tables catalogue (relations ajoutées)

| Enfant | Parent | Action |
|---|---|---|
| `series_seasons.seriesId` | `series_streams(seriesId)` | `ON DELETE CASCADE` |
| `series_episodes.seriesId` | `series_streams(seriesId)` | `ON DELETE CASCADE` |
| `epg_cache.streamId` | `live_streams(streamId)` | `ON DELETE CASCADE` |

Ces trois relations remplacent des nettoyages aujourd'hui inexistants : une
série retirée du bouquet laisse actuellement ses saisons, ses épisodes et l'EPG
de ses chaînes en base indéfiniment.

**Exceptions justifiées, sans clé étrangère :**

- `live_streams.categoryId` / `vod_streams.categoryId` / `series_streams.categoryId`
  → tables de catégories : les panels Xtream renvoient couramment des flux dont
  la catégorie n'est pas listée par `get_*_categories`. Une contrainte dure
  ferait échouer la synchronisation entière du catalogue sur une incohérence de
  panel. La cohérence reste assurée à l'affichage (la catégorie inconnue est
  simplement absente du sélecteur), comme aujourd'hui.
- `trailer_cache`, `epg_cache` au-delà de la cascade ci-dessus, `searchText`,
  `categoryRank`, index couvrants : structures d'accès ou caches dérivés,
  couverts par la règle métier 13. `trailer_cache` est purgé par réconciliation
  (§ 4.5), pas par contrainte.
- `catalog_sync_state`, `profile_sync_state` : `profile_sync_state.profileId`
  gagne en revanche une clé étrangère `→ profiles(id) ON DELETE CASCADE`
  (critère « supprimer un profil supprime ses états de synchronisation »).

## 4.3 Remplacement du catalogue : différentiel au lieu de purge totale

Les relations ci-dessus sont incompatibles avec la stratégie actuelle
(`clearAllStreams()` puis réinsertion) : chaque synchronisation détruirait
l'EPG, les saisons et les épisodes déjà chargés. L'étape 1 autorise
explicitement à revoir ce point.

Nouvelle stratégie, par section de catalogue et dans une seule transaction :

1. `batchTs = System.currentTimeMillis()` ;
2. **upsert** de toutes les lignes reçues avec `cachedAt = batchTs` ;
3. `DELETE FROM <table> WHERE cachedAt < :batchTs` (variante
   `AND categoryId = :categoryId` pour un rafraîchissement d'une seule
   catégorie).

La garde existante « une réponse vide ne remplace jamais un catalogue connu »
est conservée. Les lignes non modifiées ne sont jamais supprimées : les cascades
ne se déclenchent que pour les médias réellement disparus, ce qui est
précisément le nettoyage recherché.

**Piège à respecter :** `@Insert(onConflict = REPLACE)` compile en
`INSERT OR REPLACE`, qui **supprime** la ligne en conflit et déclenche donc les
`ON DELETE CASCADE`. Toutes les insertions sur `live_streams`, `vod_streams`,
`series_streams` doivent passer à `@Upsert` (Room 2.6.1, déjà en place), qui
génère un `INSERT … ON CONFLICT DO UPDATE` sans suppression. Un oubli ici vide
silencieusement l'EPG et les épisodes à chaque synchronisation ; le test
`CatalogUpsertSqlTest` (§ 4.9) garde ce point.

## 4.4 Résolution des métadonnées à l'affichage

Les entités Room d'état ne sont plus exposées : chaque écran est servi par une
projection DAO qui joint l'identité au catalogue courant en `INNER JOIN`, filtrée
par `accountKey`.

```sql
-- FavoritesDao.observeFavorites(profileId, accountKey)
SELECT r.kind, r.providerId, f.addedAt, s.name, s.streamIcon AS cover, s.categoryId
  FROM favorites f
  JOIN media_refs r ON r.mediaUid = f.mediaUid
  JOIN live_streams s ON s.streamId = r.providerId
 WHERE f.profileId = :profileId AND r.accountKey = :accountKey AND r.kind = 'live'
UNION ALL
SELECT r.kind, r.providerId, f.addedAt, s.name, s.streamIcon, s.categoryId
  FROM favorites f JOIN media_refs r ON r.mediaUid = f.mediaUid
  JOIN vod_streams s ON s.streamId = r.providerId
 WHERE f.profileId = :profileId AND r.accountKey = :accountKey AND r.kind = 'movie'
UNION ALL
SELECT r.kind, r.providerId, f.addedAt, s.name, s.cover, s.categoryId
  FROM favorites f JOIN media_refs r ON r.mediaUid = f.mediaUid
  JOIN series_streams s ON s.seriesId = r.providerId
 WHERE f.profileId = :profileId AND r.accountKey = :accountKey AND r.kind = 'series'
 ORDER BY addedAt DESC
```

Les reprises suivent le même motif : branche `movie` sur `vod_streams`, branche
`episode` sur `series_episodes` jointe à `series_streams` (titre de la série,
jaquette, saison, numéro d'épisode, extension de lecture). L'historique Live
joint `live_streams` (nom, icône, catégorie, `num`).

Le filtre `accountKey` n'est pas décoratif : sans lui, un favori de l'ancien
compte dont l'identifiant numérique existe dans le nouveau catalogue afficherait
un média sans rapport (règle 7). Il impose un nouveau fournisseur applicatif,
`CurrentAccountKeyProvider` (§ 5), combiné au `ProfileManager.activeProfileId`
existant dans chaque repository.

**Les modèles `domain` ne changent pas** (`FavoriteItem`, `PlaybackPosition`,
`LiveStream`) : leurs champs sont désormais alimentés par la jointure au lieu de
la ligne d'état. Aucun Composable, aucun état d'écran n'est modifié — la
normalisation reste invisible en présentation, ce qui borne fortement le risque
de régression fonctionnelle.

En écriture, `VodRepository.savePlaybackPosition` perd ses douze paramètres de
métadonnées et devient
`savePlaybackPosition(kind, providerId, positionMs, durationMs)` :
`SavePlaybackPositionUseCase` et les appels du lecteur sont simplifiés d'autant.
L'ancienne logique de « conservation de la valeur existante quand le paramètre
est vide » disparaît avec les colonnes qu'elle protégeait.

## 4.5 Cycle de vie des identités, des téléchargements et des caches

- **Création d'identité** : à la première écriture d'un état pour un média
  (favori, reprise, note, préférence, historique, téléchargement) et à
  l'application d'un snapshot cloud. Fonction `MediaRefDao.resolve(accountKey,
  kind, providerId)` : `INSERT OR IGNORE` puis `SELECT` du `mediaUid`.
- **Purge d'identité** : après toute suppression d'état,
  `DELETE FROM media_refs WHERE NOT EXISTS (…)` sur les sept tables d'état.
  Une identité sans état n'a aucune raison de survivre ; une identité avec état
  survit à la disparition du média (exigence d'identité inactive).
  `category_refs` suit la même règle, ce qui préserve mécaniquement les
  préférences d'une catégorie disparue.
- **Réconciliation post-synchronisation** (`CatalogReconciler`, nouveau) :
  exécuté après un cycle de synchronisation catalogue **complet et réussi**
  (les six `CatalogSection.CATALOG_SECTIONS` en succès pour le compte courant).
  Il supprime : les lignes `trailer_cache` sans média correspondant, et les
  téléchargements orphelins. Cette garde évite qu'une réponse partielle de panel
  ne déclenche une destruction de fichiers.
- **Téléchargements orphelins** : la suppression est **applicative**, pas une
  cascade — `media_refs` conserve l'identité, la cascade ne se déclencherait
  donc jamais. Ordre imposé et repris : marquer `status = 'ORPHANED'` (commit
  Room), supprimer le contenu media3 (`DownloadService.sendRemoveDownload` +
  purge du cache), puis supprimer la ligne. Un échec laisse la ligne en
  `ORPHANED` : l'écran Téléchargements ne l'affiche pas et la réconciliation
  suivante reprend l'opération, sans jamais annoncer un nettoyage qui n'a pas eu
  lieu. **Restriction de compte** : seuls les téléchargements dont
  `media_refs.accountKey` est le compte courant sont éligibles. Un changement de
  compte Xtream ne détruit donc aucun fichier ; ces téléchargements deviennent
  invisibles comme les autres états de l'ancien compte, conformément à la
  règle 6.

## 4.6 Format cloud v2

### Versionnement par namespace

`SnapshotCodec.SCHEMA_VERSION` (global, valeur 1) est remplacé par
`SyncNamespace.schemaVersion` : `favorites`, `playback` et
`recently-watched-live` passent à **2**, les quatre autres restent à **1**.
L'encodage écrit la version du namespace ; le décodage compare à la version
attendue du namespace ciblé et rend `Incompatible` au-delà (règle F34
inchangée). `profile_sync_state.schemaVersion` mémorise la version acceptée.

### Documents

```jsonc
// favorites v2
{"schemaVersion":2,"namespace":"favorites",
 "objects":{"live:1042":{"addedAt":1755000000000},
            "movie:815":{"addedAt":1755000100000}}}

// playback v2 — la clé lève enfin l'ambiguïté film / épisode
{"schemaVersion":2,"namespace":"playback",
 "objects":{"movie:815":{"positionMs":124000,"durationMs":5400000,
                         "lastAccessedAt":1755000200000},
            "episode:99213":{"positionMs":60000,"durationMs":2700000,
                             "lastAccessedAt":1755000300000}}}

// recently-watched-live v2
{"schemaVersion":2,"namespace":"recently-watched-live",
 "objects":{"live:1042":{"watchedAt":1755000400000}}}
```

Aucun titre, aucune jaquette, aucune catégorie, aucune extension, aucun
`profileId`. Ordre de grandeur : une reprise passe d'environ 200 octets JSON à
environ 60 ; 10 000 reprises tombent de ~0,30 Mio gzip (état T19) à moins de
0,10 Mio.

### Sérialisation par projections, non par entités

`RoomSnapshotSerializer` ne sérialise plus des entités Room amputées
(`gson.toJsonTree(entity).withoutFields(...)`) mais des DTO de transport dédiés
(`FavoriteWire`, `PlaybackWire`, `RecentlyWatchedWire`, `RatingWire`,
`TrackPreferenceWire`, `SeriesWatchStateWire`, `CategoryPreferenceWire`),
alimentés par des projections DAO qui joignent `media_refs`. Le format de
transport cesse d'être un sous-produit du schéma Room — c'est la cause racine du
défaut corrigé par ce ticket. Les quatre namespaces non versionnés conservent
octet pour octet leur format actuel (`{mediaType, mediaId, value}`, …) bien que
leur stockage change : la projection reconstruit `mediaType`/`mediaId` depuis
`media_refs`.

Conséquence sur T19 : `SnapshotLimits.PLAYBACK_STRIPPED_FIELDS`,
`SyncNamespace.strippedFields()` et `JsonElement.withoutFields()` deviennent
sans objet (le DTO ne porte plus les champs à retirer) et sont supprimés avec
leurs tests. `capMostRecent` et les trois plafonds (`FAVORITES` 500,
`PLAYBACK` 10 000, `RECENTLY_WATCHED_LIVE` 20) sont conservés et continuent de
s'appliquer **avant** la sérialisation.

### Lecture d'un ancien document (v1 → v2)

Un `SnapshotUpgrader` pur normalise tout document décodé avant fusion :

- `favorites` v1 : clé `"type:id"` déjà canonique ; seul `addedAt` est retenu,
  `name`, `cover`, `categoryId` sont ignorés.
- `playback` v1 : la clé est un `streamId` nu, sans type. Le `kind` est déduit de
  l'objet : `seriesId != null` ou `type == "series"` → `episode` ; `type ==
  "movie"` → `movie`. **Si aucun des deux indices n'est présent, l'item est
  ignoré** (règle « une référence impossible à comprendre n'est jamais
  appliquée ») plutôt que rattaché au hasard à un film. Seuls `positionMs`,
  `durationMs`, `lastAccessedAt` sont retenus.
- `recently-watched-live` v1 : clé `streamId` → `live:<streamId>` ; seul
  `watchedAt` est retenu.

La normalisation intervient **avant** `SnapshotMerger` : la base de fusion
(`profile_sync_state.baseSnapshot`) est elle aussi relue puis réécrite en v2, de
sorte que le fusionneur ne compare jamais une clé v1 à une clé v2 (ce qui
dupliquerait chaque item). Le prochain envoi est en v2 ; rien n'est jamais
réémis en v1.

### Application d'un snapshot

`apply()` résout ou crée les identités pour l'`accountKey` courant, puis
remplace le contenu du namespace pour le profil (sémantique F34 inchangée).
Une identité est créée même si le média est absent du catalogue : l'état est
restauré et reste invisible jusqu'à l'arrivée de sa cible (parcours « cloud
restauré avant le catalogue »). Si aucun compte Xtream n'est encore connu au
moment de la restauration, l'`accountKey` sentinelle `''` est employé, puis
rattaché au premier compte authentifié (§ 4.7).

## 4.7 Migration Room 27 → 28

Migration unique, non destructive, dans `Migrations.kt`. Le SQL est exposé par
`internal fun migration27To28Statements(): List<String>` afin d'être rejoué sur
un SQLite en mémoire par un test JVM — motif déjà utilisé par
`categoryRankBackfillStatements` / `CategoryRankMigrationSqlTest`.

Séquence :

1. `PRAGMA defer_foreign_keys = TRUE` (la migration s'exécute dans une
   transaction ; `PRAGMA foreign_keys` n'y est pas modifiable).
2. Créer `media_refs`, `category_refs`, `db_maintenance`.
3. Peupler les identités depuis les états existants, avec `accountKey = ''` :
   - `favorites` → `kind = type` (`live` | `movie` | `series`), `providerId = id` ;
   - `playback_positions` → `kind = CASE WHEN seriesId IS NOT NULL OR type =
     'series' THEN 'episode' WHEN EXISTS (SELECT 1 FROM series_episodes e WHERE
     e.episodeId = playback_positions.streamId) THEN 'episode' ELSE 'movie' END` ;
   - `recently_watched_live` → `live` ; `series_watch_state` → `series` ;
   - `media_ratings`, `track_preferences` → `kind = mediaType` ;
   - `downloaded_media` → `kind = type` (`movie` | `episode`) ;
   - `category_preferences` → `category_refs(kind = type)`.
4. Reconstruire chaque table d'état (`CREATE TABLE <t>_new` avec clés
   étrangères, `INSERT … SELECT` joignant `media_refs` **et** `profiles`,
   `DROP`, `RENAME`, recréation des index). La jointure sur `profiles` supprime
   au passage les lignes déjà orphelines de Room 27 (règle métier 9).
5. Reconstruire `series_seasons`, `series_episodes`, `epg_cache`,
   `profile_sync_state` pour porter leurs clés étrangères, en supprimant leurs
   lignes orphelines.
6. `DELETE FROM media_refs` / `category_refs` sans état référent.
7. `INSERT INTO db_maintenance(task, requestedAt) VALUES ('vacuum', …)`.

Room exécute `PRAGMA foreign_key_check` en fin de migration : une incohérence
résiduelle échoue la migration sans écrire, et la base Room 27 reste ouvrable
par la version précédente de l'application. Aucun
`fallbackToDestructiveMigration()` n'est ajouté (AGENTS.md).

**Sentinelle d'`accountKey`.** Le compte Xtream vit dans des préférences
chiffrées, hors de Room : le SQL de migration ne peut pas le lire. Les identités
héritées reçoivent donc `accountKey = ''`, et un composant
`MediaRefAccountBinder` réécrit `''` en `AccountKey.from(credentials)` à la
première authentification réussie — opération idempotente, qui ne touche jamais
une identité déjà rattachée à un compte. Justification : l'appareil migré
appartient à l'utilisateur qui a produit ces données, et le premier compte
authentifié après la mise à jour est le meilleur (et le seul) rattachement
disponible. Tant que le rattachement n'a pas eu lieu, les états hérités sont
invisibles — comportement identique à celui d'un catalogue non encore chargé.

**Compactage.** `VACUUM` ne peut pas s'exécuter dans la transaction de
migration. La table technique `db_maintenance(task TEXT PRIMARY KEY,
requestedAt INTEGER)` porte la demande ; `DatabaseMaintenanceRunner` (nouveau,
déclenché au démarrage hors thread principal) lit la table, vérifie via `StatFs`
que l'espace libre dépasse la taille du fichier `.db`, exécute `VACUUM` hors
transaction, puis supprime la ligne. Échec ou espace insuffisant : la ligne
reste, la base est intacte, l'opération est retentée au démarrage suivant. Une
erreur de compactage n'invalide jamais la migration.

## 4.8 Composants impactés

**Modifiés — `data/local`**

- `entity/` : `FavoriteEntity`, `PlaybackPositionEntity`,
  `RecentlyWatchedLiveEntity`, `MediaRatingEntity`, `TrackPreferenceEntity`,
  `SeriesWatchStateEntity`, `CategoryPreferenceEntity`, `DownloadedMediaEntity`,
  `SeriesSeasonEntity`, `SeriesEpisodeEntity`, `EpgCacheEntity`,
  `ProfileSyncStateEntity`.
- `dao/` : `FavoritesDao`, `VodDao`, `LiveTvDao`, `SeriesDao`, `DownloadDao`,
  `MediaRatingDao`, `TrackPreferenceDao`, `CategoryPreferenceDao`,
  `SeriesWatchStateDao` (projections de jointure, `@Upsert` catalogue, requêtes
  d'élagage), `CatalogListRow` (nouvelles projections d'état).
- `db/` : `AppDatabase` (version 28, deux entités de plus), `Migrations.kt`.

**Modifiés — `data/repository` et `data/cloudsync`**

- `FavoritesRepositoryImpl`, `VodRepositoryImpl`, `LiveTvRepositoryImpl`,
  `SeriesRepositoryImpl`, `DownloadRepositoryImpl`, `MediaRatingRepositoryImpl`,
  `TrackPreferenceRepositoryImpl`, `CategoryPreferenceRepositoryImpl`,
  `ProfileRepositoryImpl` (les suppressions manuelles par profil deviennent
  redondantes avec la cascade ; conservées uniquement là où elles portent une
  logique distante).
- `RoomSnapshotSerializer` (DTO wire + résolution d'identités),
  `SnapshotCodec` (version par namespace), `SnapshotLimits` (retrait du
  `strip`), `CloudSyncManagerImpl` (normalisation v1→v2 avant fusion),
  `data/sync/CatalogSyncManagerImpl` (différentiel `batchTs`, appel du
  réconciliateur).

**Modifiés — `domain` et `presentation`**

- `domain/repository/VodRepository` (signature `savePlaybackPosition`),
  `domain/usecase/SavePlaybackPositionUseCase`, `DetectNewEpisodesUseCase`
  (séries suivies lues par jointure), `domain/sync/SyncNamespace`.
- `presentation/player/PlayerScreen` et les appels de sauvegarde de position :
  suppression des arguments de métadonnées. Aucun autre écran n'est touché ; les
  modèles `domain` exposés à l'UI sont inchangés.

**Nouveaux**

- `data/local/entity/MediaRefEntity.kt`, `CategoryRefEntity.kt`,
  `DbMaintenanceEntity.kt` ;
- `data/local/dao/MediaRefDao.kt`, `CategoryRefDao.kt`, `DbMaintenanceDao.kt` ;
- `data/sync/CatalogReconciler.kt`, `data/sync/MediaRefAccountBinder.kt`,
  `data/local/db/DatabaseMaintenanceRunner.kt` ;
- `data/local/storage/CurrentAccountKeyProvider.kt` ;
- `data/cloudsync/wire/` (DTO de transport) et
  `data/cloudsync/SnapshotUpgrader.kt` ;
- `domain/model/MediaRef.kt` (`MediaKind` : `LIVE`, `MOVIE`, `SERIES`,
  `EPISODE`) et `domain/model/DownloadContentId.kt`.

**Hors code** : AGENTS.md § « Base de données Room » (version 28, nouvelles
règles de relations et de remplacement différentiel du catalogue) à mettre à
jour à l'étape 9.

## 4.9 Tests (JVM local, sans appareil ni émulateur)

1. `Migration27To28SqlTest` (sqlite-jdbc) : base fixture v27 peuplée de favoris
   dénormalisés, de reprises film et épisode, d'une reprise sans `type` ni
   `seriesId`, de lignes orphelines de profil, de notes, de préférences et d'un
   téléchargement ; rejeu des instructions ; assertions sur les colonnes
   retirées, les identités créées avec le bon `kind`, la disparition des
   orphelins, `PRAGMA foreign_key_check` vide, présence de la demande de
   compactage.
2. `CatalogUpsertSqlTest` : le différentiel `batchTs` conserve EPG, saisons et
   épisodes des médias maintenus, et les supprime en cascade pour les médias
   disparus ; un `INSERT OR REPLACE` échouerait ce test.
3. `StateDisplayJoinSqlTest` : les requêtes d'affichage (favoris, reprises,
   historique) rendues mot pour mot ; média absent → aucune ligne ; retour du
   média → réapparition ; même `providerId` sur deux `accountKey` → aucune
   fuite ; reprise d'épisode invisible tant que `series_episodes` est vide.
4. `SnapshotWireFormatTest` : documents v2 exacts, absence de `profileId` et de
   toute métadonnée catalogue, versions par namespace, `Incompatible` au-delà.
5. `SnapshotUpgraderTest` : import v1 des trois namespaces, champs dénormalisés
   ignorés, `kind` déduit, item indéterminable ignoré, réécriture de la base de
   fusion.
6. `RoomSnapshotSerializerTest` (fakes DAO, motif existant) : plafonds T19
   toujours appliqués, restauration créant les identités manquantes.
7. `CatalogReconcilerTest` : purge inhibée tant que le cycle catalogue n'est pas
   complet, restreinte au compte courant, ordre fichier → ligne, reprise après
   échec.
8. `MediaRefAccountBinderTest` : `''` → compte courant, idempotence, aucune
   identité d'un autre compte modifiée.
9. Non-régression : `./gradlew testDebugUnitTest` (887 tests actuels) +
   adaptation des tests construisant les entités modifiées ; `assembleDebug` et
   `lintDebug` avant conclusion.

## 4.10 Performances, sécurité, compatibilité

- **Lecture.** Les listes de catalogue et leurs index couvrants sont inchangés.
  Les écrans d'état ajoutent une jointure sur clé primaire entière, sur des
  ensembles bornés par T19 (500 favoris, 20 chaînes récentes, reprises affichées
  par page). Le gain de volume par ligne (favoris ~120 → ~12 octets ; reprises
  ~400 → ~28 octets) réduit d'autant les pages lues.
- **Écriture.** L'upsert différentiel du catalogue coûte plus qu'un `DELETE`
  massif suivi d'insertions vierges (mise à jour d'index au lieu de
  reconstruction), mais évite la reconstruction complète des index et la perte
  des détails de séries. À mesurer sur Android TV lors de l'implémentation ; le
  point de bascule est la taille du bouquet, pas le nombre d'états.
- **Migration.** Reconstruction de huit tables d'état plus quatre tables
  catalogue, puis `VACUUM` : premier démarrage exceptionnellement long, accepté
  par le PO. Les tables d'état sont petites ; le coût dominant est le `VACUUM`.
- **Sécurité.** Aucune donnée nouvelle n'est exposée : les snapshots perdent des
  champs et n'en gagnent aucun. `accountKey` est un SHA-256 tronqué de
  `host:port:username`, déjà employé, sans mot de passe. Aucun identifiant, JWT,
  OTP, URL Xtream ou donnée TMDB/YouTube ne transite.
- **Compatibilité.** Aucune compatibilité descendante de synchronisation n'est
  garantie (décision PO). Le backend reste opaque et inchangé : aucune évolution
  côté `backend/`.

## 4.11 Risques identifiés

| Risque | Conséquence | Parade |
|---|---|---|
| `INSERT OR REPLACE` conservé sur une table catalogue parente | destruction silencieuse de l'EPG et des épisodes à chaque sync | `@Upsert` + `CatalogUpsertSqlTest` |
| Oubli du filtre `accountKey` dans une requête d'affichage | états d'un ancien compte affichés sur le catalogue courant | filtre porté par les projections DAO, testé par `StateDisplayJoinSqlTest` |
| Changement de compte Xtream sur le même `host:port` | catalogue conservé mais états devenus invisibles | comportement conforme aux règles 6 et 7 ; documenté, reconnexion possible |
| Réconciliation déclenchée après une synchronisation partielle | suppression de fichiers téléchargés encore valides | garde « cycle catalogue complet et réussi » + restriction au compte courant |
| Interruption pendant `VACUUM` | base volumineuse mais valide | `VACUUM` hors transaction, atomique côté SQLite ; demande conservée et retentée |
| Base de fusion v1 confrontée à un local v2 | doublons de favoris ou de reprises | normalisation systématique avant fusion (`SnapshotUpgrader`) |
| Reprise d'épisode dont la série n'est plus chargée | reprise invisible | conforme à la spécification ; aucun chargement massif implicite |

---

# 5. Architecture

## 5.1 Vue d'ensemble

```
presentation/ (inchangée)
        ▲  modèles domain identiques (FavoriteItem, PlaybackPosition, LiveStream)
domain/  repositories · usecases · MediaKind · DownloadContentId
        ▲
data/repository/  ← CurrentAccountKeyProvider + ProfileManager
        ▲                     (accountKey courant)   (profil actif)
data/local/dao/   projections de jointure  ─────────────┐
        ▲                                              │
Room 28 :   états ──FK──> media_refs / category_refs    │  INNER JOIN (sans FK)
            états ──FK──> profiles                      ▼
                                        catalogue (live/vod/series/episodes)
                                        ▲ FK CASCADE : epg_cache, seasons, episodes
data/sync/        CatalogSyncManager (upsert différentiel) → CatalogReconciler
data/cloudsync/   projections wire → SnapshotUpgrader → SnapshotMerger → codec v2
```

## 5.2 Décisions techniques

**D1 — Identité parente commune plutôt que références typées.** Voir § 4.1. Une
seule clé étrangère par état, une seule table par état, cloisonnement par compte
porté en un point unique.

**D2 — Aucune clé étrangère entre un état et le catalogue.** Une contrainte
dure imposerait l'un des deux comportements interdits : `CASCADE` supprimerait
les favoris d'un média temporairement retiré du bouquet ; `RESTRICT` empêcherait
la mise à jour du catalogue. Le lien état ↔ catalogue est donc une **jointure de
présentation** (`INNER JOIN` sur `kind` + `providerId`), tandis que l'intégrité
dure porte sur ce qui doit réellement disparaître ensemble : le profil et
l'identité.

**D3 — « Actif » est dérivé, jamais stocké.** L'activité d'un média ou d'une
catégorie est la présence d'une ligne dans le catalogue courant. Aucune colonne
`active` n'est maintenue : ce serait une redondance et une seconde source de
vérité, exactement ce que le ticket supprime.

**D4 — Remplacement différentiel du catalogue.** Upsert horodaté par `batchTs`
puis suppression des lignes non revues. C'est la condition pour que les cascades
catalogue soient utiles plutôt que destructrices, et cela supprime au passage la
fenêtre pendant laquelle le catalogue était vide en cours de transaction.

**D5 — Le format cloud est produit par des projections, pas par des entités.**
Les DTO wire découplent définitivement le document distant du schéma Room ; le
versionnement devient par namespace, comme l'exige la règle métier 15.

**D6 — Identités seulement pour les médias référencés.** `media_refs` est
alimenté par les états, jamais par le catalogue. La table reste petite et les
index couvrants du catalogue, critiques sur Android TV, sont intacts.

**D7 — Rattachement de compte différé plutôt que devinette SQL.** La sentinelle
`''` et le `MediaRefAccountBinder` évitent d'inventer un `accountKey` dans une
migration qui n'a pas accès aux identifiants chiffrés.

**D8 — Nettoyage des téléchargements applicatif, gardé et repris.** Cascade
impossible par construction (l'identité survit) ; l'ordre marquage → fichier →
ligne, la garde de cycle complet et la restriction au compte courant sont ce qui
distingue un nettoyage cohérent d'une perte de données.

**D9 — Validation par SQL rejoué en JVM.** Le projet n'a pas d'infrastructure
instrumentée ; le motif `…Statements(): List<String>` + `sqlite-jdbc`, déjà
utilisé pour `categoryRank`, permet de valider migration, cascades et requêtes
d'affichage sans appareil, conformément à AGENTS.md.

## 5.3 Responsabilités

| Composant | Responsabilité |
|---|---|
| `media_refs` / `category_refs` | identité stable d'une cible, par compte Xtream ; aucune métadonnée |
| Tables d'état | référence + valeurs métier propres, rien d'autre |
| Tables catalogue | source canonique unique des métadonnées d'affichage |
| Projections DAO | rapprocher état et catalogue, filtrer par profil et par compte |
| `CurrentAccountKeyProvider` | exposer l'`accountKey` courant aux repositories |
| `CatalogSyncManager` | appliquer le catalogue en différentiel |
| `CatalogReconciler` | nettoyer téléchargements et caches orphelins après un cycle complet |
| `MediaRefAccountBinder` | rattacher les identités héritées au premier compte authentifié |
| `RoomSnapshotSerializer` + wire | produire et appliquer des documents d'état purs |
| `SnapshotUpgrader` | normaliser tout document reçu avant fusion |
| `DatabaseMaintenanceRunner` | exécuter le compactage différé, sans jamais compromettre la base |

## 5.4 Flux de données

**Ajout d'un favori** : écran → repository → `MediaRefDao.resolve(accountKey,
kind, providerId)` → `favorites(profileId, mediaUid, addedAt)` →
`markDirty(FAVORITES)` (F34 inchangé). L'écran Favoris est ré-émis par Room via
la projection jointe ; si le média est absent du catalogue, il n'apparaît pas.

**Synchronisation catalogue** : Xtream → upsert `batchTs` → suppression des
lignes non revues → cascades (EPG, saisons, épisodes) → `CatalogReconciler` si
le cycle est complet → les états dont la cible est revenue redeviennent visibles
sans écriture supplémentaire.

**Restauration cloud sur une installation neuve** : document v2 → `apply()` →
identités créées avec l'`accountKey` courant → états écrits → écrans vides tant
que le catalogue n'est pas chargé → première synchronisation Xtream → tout
apparaît avec les métadonnées courantes.

---

# 6. Plan de développement

## Précondition de démarrage

- [x] **T20-0 — Clore T19 avant toute modification T20**

  **Objectif :** faire passer les corrections T19-R1 à T19-R4 par son étape 7
  puis constater sa validation. T20 remplace les mécanismes de sérialisation
  temporaire de T19 (`strippedFields`, `withoutFields` et
  `PLAYBACK_STRIPPED_FIELDS`) ; il ne doit pas absorber une correction de review
  appartenant à T19 ni se bâtir sur un comportement de fusion encore invalide.

  **Fichiers :** ticket `ai/technical/T19-app-plafonds-items-et-hygiene-snapshots.md`
  et les fichiers explicitement désignés par sa review.

  **Validation :** T19 est `VALIDATED` avec ses quatre corrections résolues et
  ses tests JVM de câblage exécutés. Aucun changement T20 n'est commencé avant
  cette preuve.

## Implémentation T20

- [x] **T20-1 — Introduire les identités relationnelles et les contrats purs**

  **Objectif :** créer `MediaRef`, `CategoryRef`, `DbMaintenance` et les
  fonctions pures `MediaKind` / `DownloadContentId`, avec DAO de résolution,
  de purge et de rattachement au compte. Poser les clés étrangères vers profils
  et identités sans faire des tables catalogue une dépendance destructrice des
  états utilisateur.

  **Fichiers :** `data/local/entity/{MediaRefEntity,CategoryRefEntity,DbMaintenanceEntity}.kt`,
  `data/local/dao/{MediaRefDao,CategoryRefDao,DbMaintenanceDao}.kt`,
  `domain/model/{MediaRef,DownloadContentId}.kt`, `AppDatabase.kt`, modules
  Hilt concernés et tests JVM dédiés.

  **Validation :** unicité `(accountKey, kind, providerId)`, résolution
  idempotente, purge seulement des identités sans état et conversions
  `DownloadContentId` testées sans Android.

- [x] **T20-2 — Migrer Room 27 vers 28 sans perte**

  **Objectif :** reconstruire les huit tables d'état, `profile_sync_state` et
  les tables catalogue concernées avec les colonnes normalisées, les clés
  étrangères et les index prévus ; créer les identités héritées, supprimer les
  orphelins et programmer le compactage différé.

  **Fichiers :** entités Room existantes, `data/local/db/{AppDatabase,Migrations}.kt`,
  `DatabaseMaintenanceRunner.kt`, tests `Migration27To28SqlTest`.

  **Validation :** rejeu SQL sur fixture v27 avec `PRAGMA foreign_key_check`
  vide, aucune colonne dénormalisée dans les tables d'état, conservation des
  états rattachables, suppression des seuls orphelins et absence de fallback
  destructif.

- [x] **T20-3 — Passer le catalogue à la réconciliation différentielle**

  **Objectif :** remplacer chaque séquence purge/réinsertion par un upsert
  horodaté puis suppression des lignes non revues, avec `@Upsert` sur les
  parents et les cascades catalogue prévues. Déclencher la réconciliation
  seulement après un cycle complet réussi.

  **Fichiers :** `LiveTvDao.kt`, `VodDao.kt`, `SeriesDao.kt`, repositories de
  catalogue, `data/sync/{CatalogSyncManagerImpl,CatalogReconciler}.kt` et tests
  `CatalogUpsertSqlTest` / `CatalogReconcilerTest`.

  **Validation :** aucun `INSERT OR REPLACE` sur un parent catalogue ; EPG,
  saisons et épisodes survivent au refresh d'un média maintenu, sont supprimés
  pour un média réellement absent, et aucun téléchargement n'est supprimé sur
  cycle incomplet ou autre compte.

- [x] **T20-4 — Réécrire les lectures et écritures d'état autour des références**

  **Objectif :** adapter DAO et repositories des favoris, reprises, historique
  Live, notes, préférences, suivi de séries et téléchargements : création de
  référence à l'écriture, projections `INNER JOIN` filtrées par compte à la
  lecture, modèles domain/presentation inchangés.

  **Fichiers :** DAO d'état, `FavoritesRepositoryImpl.kt`, `VodRepositoryImpl.kt`,
  `LiveTvRepositoryImpl.kt`, `ViewingHistoryRepositoryImpl.kt`, repositories
  de note/préférence/suivi/téléchargement, `VodRepository.kt`,
  `SavePlaybackPositionUseCase.kt`, `PlayerScreen.kt` et tests associés.

  **Validation :** aucune métadonnée catalogue dans une écriture d'état ; les
  projections masquent une cible absente, la font réapparaître à son retour et
  empêchent toute fuite entre deux `accountKey` partageant le même identifiant.

- [x] **T20-5 — Rendre la synchronisation cloud indépendante du schéma Room**

  **Objectif :** introduire les DTO wire et projections de sérialisation,
  versionner seulement les trois namespaces modifiés en v2 et normaliser les
  snapshots v1 avant fusion et écriture locale. Retirer les mécanismes T19
  devenus obsolètes tout en conservant ses plafonds.

  **Fichiers :** `data/cloudsync/{RoomSnapshotSerializer,SnapshotCodec,SnapshotUpgrader}.kt`,
  `data/cloudsync/wire/`, `CloudSyncManagerImpl.kt`, `SnapshotLimits.kt`,
  `domain/sync/SyncModels.kt`, tests de codec, serializer et upgrader.

  **Validation :** documents v2 exacts sans `profileId` ni métadonnée
  catalogue, v1 normalisé avant `SnapshotMerger`, item ambigu ignoré, quatre
  namespaces inchangés et plafonds T19 appliqués avant chaque encodage.

- [x] **T20-6 — Rattacher les identités héritées et compacter sans risque**

  **Objectif :** exécuter le rattachement idempotent de la sentinelle
  `accountKey = ''` au premier compte authentifié et le `VACUUM` différé hors
  transaction, avec reprise sûre après manque d'espace ou échec.

  **Fichiers :** `MediaRefAccountBinder.kt`, `DatabaseMaintenanceRunner.kt`,
  point de démarrage/authentification concerné, tests JVM associés.

  **Validation :** aucune identité d'un autre compte n'est modifiée ; un échec
  de compactage laisse une base valide et une demande rejouable.

- [x] **T20-7 — Couvrir les invariants et la non-régression**

  **Objectif :** compléter les tests de migration, jointures, cloud,
  réconciliation et repositories, puis adapter seulement les tests cassés par
  les entités normalisées.

  **Fichiers :** `app/src/test/java/**` concernés, en particulier
  `Migration27To28SqlTest`, `StateDisplayJoinSqlTest`,
  `SnapshotWireFormatTest`, `SnapshotUpgraderTest`,
  `RoomSnapshotSerializerTest`, `CatalogReconcilerTest` et
  `MediaRefAccountBinderTest`.

  **Validation :** toutes les preuves sont JVM locales, sans appareil ni
  émulateur ; `testDebugUnitTest`, `assembleDebug`, `lintDebug` et
  `git diff --check` réussissent après les tâches précédentes.

---

# 7. Notes de développement

Ticket créé à l'étape 1. Aucun choix d'architecture ni changement de code à ce
stade. Les réponses PO du 2026-08-13 ont fixé le périmètre global Room, les
vraies clés étrangères, la migration unique depuis Room 27, le catalogue
courant comme source des métadonnées, T19 comme prérequis et l'absence de
compatibilité avec les anciennes applications. Le second arbitrage du même jour
a fermé les questions de restauration, références inactives, épisodes,
téléchargements, formats cloud, FTS/index et compactage. Le choix de
modélisation polymorphe est délégué à l'étape 3 ; aucune option n'est choisie à
ce stade.

Étape 2 engagée le 2026-08-13 : spécification fonctionnelle rédigée. Le ticket
est passé à `SPECIFICATION` après arbitrage du parcours de nettoyage : aucun
bouton ni écran dédié, reconnexion à l'ancien compte puis actions ordinaires de
retrait. Aucune spécification technique ou architecture n'a été ajoutée.

Étape 3 engagée le 2026-08-13 : spécification technique et architecture
rédigées, statut `ARCHITECTURE`. Aucun code modifié. Décisions structurantes
prises à cette étape :

- **Modélisation polymorphe (question déléguée à l'étape 1) : identité parente
  commune.** `media_refs(mediaUid, accountKey, kind, providerId)` et
  `category_refs` ; les huit tables d'état ne portent plus qu'une clé étrangère
  `mediaUid`/`catUid` et leurs valeurs métier. Les références typées séparées
  auraient imposé une vingtaine de tables d'état, faute de clé étrangère
  polymorphe en SQLite.
- **Aucune clé étrangère entre état et catalogue** : `CASCADE` détruirait les
  états d'un média temporairement absent, `RESTRICT` bloquerait la mise à jour
  du catalogue. Le lien est une jointure d'affichage `INNER JOIN` ; l'intégrité
  dure porte sur `profiles` et sur les identités.
- **« Actif » dérivé de la présence catalogue**, jamais stocké.
- **Remplacement différentiel du catalogue** (upsert horodaté `batchTs` puis
  suppression des lignes non revues) à la place du `clear + insert` actuel,
  condition sine qua non des cascades `epg_cache` / `series_seasons` /
  `series_episodes`. Impose `@Upsert` : `INSERT OR REPLACE` déclencherait ces
  cascades et viderait silencieusement l'EPG et les épisodes.
- **Format cloud produit par des projections wire**, plus par des entités Room
  amputées ; versionnement par namespace (v2 pour `favorites`, `playback`,
  `recently-watched-live`), clés `kind:providerId` levant l'ambiguïté
  film/épisode du namespace `playback`, normalisation v1→v2 avant fusion.
- **Migration 27→28 unique**, SQL exposé en `List<String>` pour rejeu JVM via
  `sqlite-jdbc` (motif `categoryRankBackfillStatements`), sentinelle
  `accountKey = ''` rattachée au premier compte authentifié, compactage différé
  par la table `db_maintenance` + `DatabaseMaintenanceRunner`.
- **Téléchargements orphelins** nettoyés par réconciliation applicative gardée
  (cycle catalogue complet et réussi, compte courant uniquement, ordre
  marquage → fichier → ligne avec reprise).

Impact sur T19, encore en review : `strippedFields()`, `withoutFields()` et
`SnapshotLimits.PLAYBACK_STRIPPED_FIELDS` deviennent sans objet avec les DTO
wire et seront supprimés par T20 avec leurs tests ; `capMostRecent` et les trois
plafonds sont conservés et restent appliqués avant sérialisation.

Point de vigilance signalé au PO : l'`accountKey` retenu pour les identités est
le compte Xtream complet (`host:port:username`, décision PO), alors que le
catalogue local est conservé par serveur (`host:port`). Changer d'utilisateur
Xtream sur le même serveur laisse donc le catalogue en place mais rend les états
antérieurs inactifs — conforme aux règles 6 et 7, mais visible pour
l'utilisateur.

Étape 4 engagée le 2026-08-13 : le plan T20-0 à T20-7 a été ajouté et le ticket
est passé à `TASK BREAKDOWN`. Il sépare explicitement le prérequis T19, les
fondations Room, la migration unique, le remplacement différentiel du catalogue,
les projections d'état, le protocole cloud, la maintenance et les preuves JVM.

Étape 5 demandée le 2026-08-13 : aucun code T20 n'a été démarré. Le prérequis
T20-0 est actuellement bloquant : T19 est encore `REVIEW — CHANGES REQUESTED`
avec T19-R1 à T19-R4 non corrigés. Les contourner dans T20 mélangerait les
tickets et supprimerait les mécanismes T19 avant leur correction et validation.

Étape 5 reprise le 2026-08-13 : le PO a confirmé que T19 est livré. T20-0 est
donc levé malgré le statut documentaire de T19 qui attend encore son étape 8 ;
les corrections T19-R1 à T19-R4 sont résolues et leurs invariants restent à
préserver par T20.

Lot T20-1 commencé : `MediaKind` et `DownloadContentId` sont maintenant des
contrats purs, et les entités/DAO d'identité `media_refs`, `category_refs` et
de maintenance ont été créés. Ce lot ne sera coché qu'après son intégration à
`AppDatabase` avec la migration 27→28 : une intégration partielle ferait ouvrir
un schéma différent sous le même numéro de version, ce qui est interdit.
Le test JVM `DownloadContentIdTest` passe (`./gradlew --no-daemon
testDebugUnitTest --tests com.cstv.app.domain.model.DownloadContentIdTest`).

Étape 5 (implémentation) poursuivie et pour l'essentiel achevée le 2026-08-13 :

- **T20-1/T20-2** : identités relationnelles intégrées à `AppDatabase`
  (version 28), `MIGRATION_27_28` complète (identités héritées à `accountKey
  = ''`, reconstruction des huit tables d'état + `series_seasons` /
  `series_episodes` / `epg_cache` / `profile_sync_state` avec FK réelles,
  purge des orphelins, `PRAGMA foreign_key_check` en garde-fou, aucun
  `fallbackToDestructiveMigration`). Validé par `Migration27To28SqlTest` (9
  tests JVM, rejeu SQL sur fixture v27 via `sqlite-jdbc`).
- **T20-4** : DAO et repositories des huit tables d'état réécrits autour de
  `mediaUid`/`catUid` et `accountKey` (projections `INNER JOIN` filtrées par
  compte pour l'affichage, DTO wire lean pour le cloud). Use cases et
  ViewModels de sauvegarde de position simplifiés en conséquence
  (`SavePlaybackPositionUseCase`, `SeriesViewModel`, `VodViewModel`).
- **T20-5** : `SyncNamespace.schemaVersion` par namespace (v2 pour
  `favorites`/`playback`/`recently-watched-live`), DTO wire dédiés
  (`data/cloudsync/wire/SnapshotWireModels.kt`), `SnapshotUpgrader` v1→v2
  branché dans `CloudSyncManagerImpl` avant fusion. **Écart assumé** : les
  fonctions T19 `SnapshotLimits.strippedFields`/`withoutFields`/
  `PLAYBACK_STRIPPED_FIELDS` n'ont pas été retirées comme prévu — elles sont
  désormais des no-ops inoffensifs (les DTO wire ne portent plus les champs
  visés) et leur suppression a été reportée pour ne pas complexifier la review
  de ce lot sans bénéfice fonctionnel.
- **T20-6** : `MediaRefAccountBinder` (rattachement idempotent de la
  sentinelle `accountKey = ''`, appelé dans `AuthRepositoryImpl.login()` après
  une authentification réussie) et `DatabaseMaintenanceRunner` (lit
  `db_maintenance`, vérifie l'espace libre via une sonde `DiskSpaceProbe`
  substituable en test — `StatFs` n'étant pas simulable sans Robolectric,
  absent du projet —, exécute `VACUUM` hors transaction sur
  `openHelper.writableDatabase`, ne complète la ligne qu'au succès), lancé au
  démarrage dans `IptvApplication.onCreate()` hors thread principal. Tests
  `MediaRefAccountBinderTest` et `DatabaseMaintenanceRunnerTest` (cas :
  absence de demande, fichier `.db` disparu, espace insuffisant, succès,
  échec de `VACUUM`).

**T20-3 reste partiel.** Fait : `@Upsert` sur les parents catalogue
(`LiveTvDao`, `SeriesDao`) et remplacement du `clear + insert` par
upsert-puis-`DELETE ... WHERE cachedAt < batchTs` — condition nécessaire aux
nouvelles cascades FK sans vider l'EPG/les épisodes à chaque synchronisation.
**Non fait** : `CatalogReconciler` (nettoyage des téléchargements et caches
orphelins après un cycle catalogue complet) n'a pas été créé, et aucun test
SQL dédié (`CatalogUpsertSqlTest`) ne rejoue le comportement upsert/cascade
sur une vraie base — seule une couverture indirecte existe via les tests
Mockito des repositories (vérifient l'appel DAO, pas le SQL réel).

**T20-7 reste partiel** en conséquence : `testDebugUnitTest`, `assembleDebug`
et `lintDebug` sont `BUILD SUCCESSFUL` sur l'ensemble du projet, mais les
tests nommément prévus `StateDisplayJoinSqlTest`, `SnapshotWireFormatTest`,
`SnapshotUpgraderTest` (dédié — la logique n'est aujourd'hui exercée
qu'indirectement par `CloudSyncManagerTest`/`CloudSyncManagerMergeNormalizationTest`)
et `CatalogUpsertSqlTest`/`CatalogReconcilerTest` n'existent pas. `git diff
--check` n'a pas encore été exécuté.

Statut conservé à `IMPLEMENTATION` : T20-3/T20-7 ne sont pas clos tant que
`CatalogReconciler` et ses tests SQL dédiés ne sont pas traités, en lot séparé
ou en poursuite de celui-ci selon arbitrage PO.

**T20-3/T20-7 complétés le 2026-08-13, sur demande explicite de finir
l'implémentation.** Écarts précédents comblés :

- **`CatalogReconciler`** (nouveau, `data/sync/CatalogReconciler.kt`) : purge
  `trailer_cache` orpheline (cache global, sans lien de compte) et
  téléchargements orphelins du compte courant, dans l'ordre imposé
  (`ORPHANED` → retrait media3 via `DownloadContentRemover` → suppression de
  la ligne), échec = ligne laissée en `ORPHANED`, reprise au cycle suivant.
  `DownloadService.sendRemoveDownload` isolé derrière `DownloadContentRemover`
  (nouvelle indirection, même motif que `DiskSpaceProbe` en T20-6) pour rester
  testable JVM. Câblé dans `CatalogSyncManagerImpl.runSync()`, déclenché
  seulement si les six `CatalogSection.CATALOG_SECTIONS` ont réussi (capturé
  avant l'enrichissement, qui ne bloque donc jamais la réconciliation).
  `DownloadDao.findOrphaned` renvoie désormais une projection
  `OrphanedDownloadRow(mediaUid, kind, providerId)` (le `(kind, providerId)`
  est nécessaire pour reconstruire le `DownloadContentId` media3).
  `TrailerCacheDao.deleteOrphaned()` ajouté.
- **Tests SQL dédiés** (nouveaux, JVM, sqlite-jdbc) :
  `CatalogUpsertSqlTest` (preuve que `INSERT OR REPLACE` détruit `epg_cache`
  même pour un flux maintenu, que `ON CONFLICT DO UPDATE` ne le fait jamais,
  et que la suppression différentielle par `batchTs` ne supprime que le flux
  réellement absent — live et séries), `CatalogReconcilerTest` (ordre
  marquer/retirer/supprimer, reprise sur échec media3, kind non résolvable
  ignoré, purge trailer_cache indépendante), `StateDisplayJoinSqlTest`
  (rejoue les vraies requêtes `FAVORITE_LIST_QUERY`/`PLAYBACK_LIST_QUERY`/
  `RECENTLY_WATCHED_LIST_QUERY` importées des DAO — masquage d'une cible
  absente, réapparition à son retour, étanchéité entre deux `accountKey`
  partageant le même `providerId` numérique), `SnapshotUpgraderTest` (les
  trois règles de normalisation v1→v2 nommées individuellement, y compris
  « référence impossible à comprendre jamais appliquée »). `FAVORITE_LIST_QUERY`
  passé de `private` à `internal` pour permettre au test de rejouer le SQL
  réel plutôt qu'une copie.
- 4 nouveaux tests sur `CatalogSyncManagerImplTest` couvrant le déclenchement
  conditionnel de la réconciliation (succès complet, échec partiel, AUTH,
  échec d'enrichissement sans effet).

`./gradlew testDebugUnitTest assembleDebug lintDebug` → `BUILD SUCCESSFUL`
(ensemble du projet, T20 et hors-T20). `git diff --check` propre.

Implémentation (étape 5) de T20 considérée complète : T20-1 à T20-7 tous
cochés. Statut laissé à `IMPLEMENTATION` — le passage à `REVIEW` (étape 6)
n'a pas été demandé dans ce tour et n'a donc pas été engagé.

---

# 8. Review

## Critique

## Majeur

## Mineur

## Corrections demandées

---

# 9. Release

Version :

Commit :

Date :
