#!/usr/bin/env bash
#
# Déploie le backend PHP sur alwaysdata : rsync du code, composer install
# distant, migrations, puis vérification santé + en-têtes de sécurité.
#
# Usage :
#   scripts/deploy-backend.sh              # rsync + composer install + migrate + healthcheck
#   scripts/deploy-backend.sh --dry-run    # affiche ce que rsync ferait, ne touche rien
#   scripts/deploy-backend.sh --skip-composer  # saute composer install distant (vendor/ inchangé)
#
# Prérequis distants :
#   - ~/.cstv-production.env sur le serveur (APP_ENV, secrets, DB_*, quotas T14/T16 --
#     voir backend/.env.example pour la liste complète des clés).
#   - composer disponible dans le shell SSH alwaysdata (sinon --skip-composer et
#     synchroniser vendor/ à la main, ou retirer vendor/ de EXCLUDES ci-dessous).
#
# N'exécute jamais bin/fixtures ici : le script refuse déjà de tourner en
# APP_ENV=production, mais autant ne pas le tenter depuis un outil de déploiement.

set -euo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")/.."

readonly REMOTE_HOST="cstv@ssh-cstv.alwaysdata.net"
readonly REMOTE_DIR="www"
readonly REMOTE_ENV_FILE="\$HOME/.cstv-production.env"
readonly HEALTH_URL="https://cstv.alwaysdata.net/health"

dry_run=false
skip_composer=false
for arg in "$@"; do
    case "$arg" in
        --dry-run) dry_run=true ;;
        --skip-composer) skip_composer=true ;;
        *) echo "❌ Option inconnue : $arg (attendu : --dry-run, --skip-composer)" >&2; exit 2 ;;
    esac
done

die() { echo "❌ $*" >&2; exit 1; }
step() { echo; echo "▶ $*"; }

command -v rsync >/dev/null || die "rsync introuvable."
command -v ssh >/dev/null || die "ssh introuvable."

# --- Sync du code ------------------------------------------------------------
# .env jamais écrasé (secrets prod hors dépôt). vendor/ exclu : reconstruit par
# composer install distant juste après, source de vérité = composer.lock, pas
# ce qui traîne localement.

step "Sync backend -> $REMOTE_HOST:$REMOTE_DIR"
rsync_args=(-avz --exclude='.env' --exclude='vendor/')
"$dry_run" && rsync_args+=(--dry-run)
rsync "${rsync_args[@]}" backend/ "$REMOTE_HOST:$REMOTE_DIR/"

if "$dry_run"; then
    echo "(dry-run : composer install, migrate et healthcheck non exécutés)"
    exit 0
fi

# --- Composer + migrations, en une seule connexion SSH -----------------------
# `set -a` avant `source` : sans lui, source garde les variables locales au
# shell distant et php ne les voit jamais (piège déjà rencontré en manuel).

remote_cmd="cd $REMOTE_DIR && set -a && source $REMOTE_ENV_FILE && set +a"
if ! "$skip_composer"; then
    remote_cmd+=" && composer install --no-dev --optimize-autoloader --no-interaction"
fi
remote_cmd+=" && php bin/migrate"

step "composer install (sauf --skip-composer) + bin/migrate sur le serveur"
ssh "$REMOTE_HOST" "$remote_cmd"

# --- Healthcheck ---------------------------------------------------------------
# Vérifie aussi les en-têtes T17 (HSTS, nosniff, referrer-policy) : un déploiement
# qui répond 200 mais sans ces en-têtes indique un souci de configuration front.

step "Healthcheck + en-têtes de sécurité ($HEALTH_URL)"
headers="$(curl -sf -D - -o /dev/null "$HEALTH_URL")" || die "Healthcheck a échoué : $HEALTH_URL injoignable ou non-200."
echo "$headers"
for header in "strict-transport-security" "x-content-type-options" "referrer-policy"; do
    echo "$headers" | grep -qi "^$header:" || echo "⚠️  En-tête absent : $header"
done

echo
echo "✅ Backend déployé."
