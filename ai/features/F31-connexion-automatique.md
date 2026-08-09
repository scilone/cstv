# F31 - Gestion de compte : Connexion automatique au profil au démarrage

## Informations générales

Status:
IDEA

Created:
2026-08-10

---

# 1. Description

Ajout d'une fonctionnalité permettant à l'utilisateur d'activer la connexion automatique sur un profil spécifique. Au démarrage de l'application (après login/auto-login sur le compte Xtream Codes), si un profil est désigné comme profil de connexion automatique, l'application doit s'y connecter directement et ignorer l'écran de sélection de profil ("Qui regarde ?").

---

# 2. Contexte

Actuellement, l'application propose une sélection de profils à chaque démarrage si plusieurs profils sont configurés (Netflix-style, introduit lors de la Phase 27). Cela oblige l'utilisateur qui n'a qu'un profil principal d'utilisation, ou qui souhaite un accès direct sans friction sur TV/mobile, à cliquer à chaque lancement. La possibilité d'associer un profil à une connexion automatique directe répond à ce besoin de fluidité, en supprimant l'étape de sélection intermédiaire.

---

# 3. Objectif

- Permettre à l'utilisateur de choisir un profil existant et d'activer/désactiver l'option de "Connexion automatique au démarrage" pour ce profil.
- Assurer qu'au démarrage de l'application, l'écran de sélection de profil soit totalement contourné si l'option est active pour un profil valide existant.
- Permettre d'éditer ou modifier ce choix depuis l'écran de gestion des profils.
- Maintenir la possibilité de changer manuellement de profil (depuis la Home ou les paramètres de profil) même si la connexion automatique est active pour l'un d'eux.

---

# 4. Hypothèses

- L'état de la connexion automatique par profil sera sauvegardé de manière persistante (ex: via `SettingsManager` avec le stockage de l'identifiant du profil de connexion auto).
- Un seul profil à la fois peut être marqué pour la connexion automatique au démarrage. Si un nouveau profil l'active, l'ancien est désactivé.
- Si le profil configuré pour la connexion automatique est supprimé, la connexion automatique est automatiquement désactivée (ou réinitialisée).
- L'auto-login Xtream Codes (compte global de l'application) reste indépendant et s'exécute toujours avant d'appliquer la connexion automatique au profil local.

---

# 5. Questions ouvertes

- Faut-il également intégrer l'option d'activation/désactivation de cette connexion automatique dans les paramètres généraux (`SettingsScreen`), en plus de la boîte de dialogue d'édition du profil ?
- Quel comportement adopter en cas d'erreur de chargement du profil lors de l'auto-connexion ? (Se replier de manière transparente sur l'écran de sélection de profil standard).
- Devrait-on désactiver temporairement l'auto-connexion au profil si l'utilisateur vient de se déconnecter d'un profil manuellement, ou seulement au redémarrage complet de l'application ?
