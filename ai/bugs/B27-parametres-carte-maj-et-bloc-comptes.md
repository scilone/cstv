# B27 — Paramètres : carte « Mise à jour » désalignée et bloc comptes hors charte

- **Statut** : VALIDATION (build + tests + lint OK ; reste la vérification
  visuelle PO sur TV et mobile)
- **Type** : Bug UI (hotfix)
- **Écrans** : Paramètres (TV + Mobile)
- **Fichiers** : `presentation/settings/SettingsScreen.kt`, `SettingsState.kt`,
  `SettingsViewModel.kt`, `res/values/strings.xml`

## 1. Constat (PO)

1. La carte « Rechercher une mise à jour » n'a pas le même fond que les autres
   cartes de l'écran (`Surface2` au lieu de `Surface3`), sur TV comme sur mobile.
2. Sur TV, le focus de son action principale est illisible : le bouton est en
   `AccentLavande` et son liseré de focus par défaut est `AccentLavandeHover`
   — lavande sur lavande, donc pas de retour visuel comparable aux autres
   actions de l'écran.
3. La zone de déconnexion CSTV ne respecte pas la charte : texte de statut nu +
   `OutlinedButton` (mobile) / action isolée (TV), sans carte porteuse. Sur
   mobile, le libellé `cstv_logout` (« Se déconnecter du compte CSTV (email) »)
   déborde d'un bouton de 44 dp de haut : la seconde ligne est tronquée,
   illisible.
4. Demande PO : regrouper la connexion IPTV et la connexion CSTV dans un seul
   bloc.

## 2. Cause

- `TvCheckUpdateCard` / `MobileCheckUpdateCard` appliquent la déviation décidée
  en F35-R5 (`Surface2` + action `AccentLavande`) alors que le reste de l'écran
  suit la convention F32 (`Surface3` + `TvSettingsActionButton` par défaut).
  La déviation, isolée, se lit comme un défaut de rendu et non comme une
  hiérarchie.
- La sortie de compte CSTV avait été greffée en fin de colonne (F34/T10) sans
  carte, et le bouton de déconnexion IPTV vivait encore plus bas, séparé : deux
  actions de même nature à deux endroits, dans deux langages visuels.

## 3. Correctif

1. `TvCheckUpdateCard` / `MobileCheckUpdateCard` repassent en `Surface3`.
   L'action TV reprend le conteneur par défaut de `TvSettingsActionButton`
   (`Surface3` au repos, liseré `AccentLavandeHover` au focus), donc le même
   focus que « Extraire les logs de diagnostic ». Le bouton mobile reste en
   `colorScheme.primary`, comme les autres actions de carte mobile.
2. Nouvelle carte « Comptes » (`TvAccountsCard` / `MobileAccountsCard`), dernière
   de l'écran, qui regroupe :
   - ligne **IPTV** : identifiant du compte Xtream + action destructive rouge
     (`TvSettingsDestructiveButton` / bouton `RatingDislike` mobile) ;
   - séparateur ;
   - ligne **CSTV** : e-mail, état de synchronisation cloud, action
     « Se déconnecter » ; si aucun compte CSTV n'est lié, la ligne affiche
     seulement un libellé « Aucun compte lié » et aucune action.
3. `SettingsState.iptvUsername` alimenté par `CredentialsManager` (identifiants
   courants, repli sur le dernier `UserInfo` connu pour une session hors ligne).
4. Le libellé `cstv_logout` (email inséré dans le bouton) est remplacé par des
   libellés courts (`settings_account_*`) : l'e-mail devient une ligne de texte
   dédiée, ce qui supprime le débordement mobile.

## 4. Validation

- `./gradlew assembleDebug` + `./gradlew testDebugUnitTest` + `./gradlew lintDebug`.
- `SettingsViewModelTest` mis à jour (nouvelle dépendance `CredentialsManager`)
  et couverture de `iptvUsername` (identifiants présents / absents / repli
  hors ligne).
- Vérification visuelle PO sur TV et mobile (hors périmètre automatisable).
