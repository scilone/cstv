#!/usr/bin/env bash
#
# Fabrique l'APK de release signé sur ce poste, puis publie la Release GitHub
# avec l'APK attaché. Remplace l'ancienne pipeline GitHub Actions.
#
# Usage :
#   scripts/release-local.sh              # vérifie, compile, tague, pousse, publie
#   scripts/release-local.sh --no-publish # s'arrête après la compilation
#
# La version publiée est celle de app/build.gradle.kts : le tag en est déduit,
# jamais l'inverse. Bumper versionName/versionCode avant de lancer ce script.

set -euo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")/.."

readonly GRADLE_MODULE_DIR="app"
readonly BUILD_FILE="app/build.gradle.kts"
readonly APK_PATH="app/build/outputs/apk/release/app-release.apk"
readonly KEYSTORE_PROPERTIES="keystore.properties"

publish=true
case "${1:-}" in
    --no-publish) publish=false ;;
    "") ;;
    *) echo "Option inconnue : $1 (attendu : --no-publish)" >&2; exit 2 ;;
esac

die() { echo "❌ $*" >&2; exit 1; }
step() { echo; echo "▶ $*"; }

# --- Signature -------------------------------------------------------------
# Vérifiée avant toute compilation : `assembleRelease` échouerait de toute
# façon, mais bien plus tard et sur un message autrement moins clair.

[[ -f "$KEYSTORE_PROPERTIES" ]] || die \
    "$KEYSTORE_PROPERTIES absent. Copier keystore.properties.example et le renseigner."

read_property() {
    sed -nE "s/^[[:space:]]*$1[[:space:]]*=[[:space:]]*(.*[^[:space:]])[[:space:]]*$/\1/p" \
        "$KEYSTORE_PROPERTIES" | head -n1
}

store_file="$(read_property storeFile)"
[[ -n "$store_file" ]] || die "storeFile non renseigné dans $KEYSTORE_PROPERTIES."
for property in storePassword keyAlias keyPassword; do
    [[ -n "$(read_property "$property")" ]] || die "$property non renseigné dans $KEYSTORE_PROPERTIES."
done

# storeFile est relatif au module `app` (c'est ainsi que Gradle le résout).
[[ -f "$GRADLE_MODULE_DIR/$store_file" ]] || die \
    "Keystore introuvable : $GRADLE_MODULE_DIR/$store_file (chemin relatif au module app)."

command -v gh >/dev/null || die "GitHub CLI (gh) introuvable."
$publish && { gh auth status >/dev/null 2>&1 || die "gh non authentifié. Lancer : gh auth login"; }

# --- État du dépôt ---------------------------------------------------------

branch="$(git rev-parse --abbrev-ref HEAD)"
[[ "$branch" == "main" ]] || die "Branche courante : $branch. Une release se coupe depuis main."

[[ -z "$(git status --porcelain)" ]] || die \
    "Arbre de travail non propre. Committer ou remiser avant de publier — l'APK doit correspondre exactement au tag."

# --- Version ---------------------------------------------------------------
# Mêmes règles que l'ancienne étape « Validate Release Version » du workflow :
# le tag, versionName et versionCode ne peuvent pas diverger.

version_name="$(sed -nE 's/^[[:space:]]*versionName = "([^"]+)".*/\1/p' "$BUILD_FILE" | head -n1)"
version_code="$(sed -nE 's/^[[:space:]]*versionCode = ([0-9_]+).*/\1/p' "$BUILD_FILE" | head -n1 | tr -d '_')"

[[ "$version_name" =~ ^([0-9]+)\.([0-9]+)\.([0-9]+)$ ]] || die \
    "versionName « $version_name » n'est pas au format MAJEUR.MINEUR.CORRECTIF."

expected_code=$(( 10#${BASH_REMATCH[1]} * 10000 + 10#${BASH_REMATCH[2]} * 100 + 10#${BASH_REMATCH[3]} ))
[[ "$version_code" == "$expected_code" ]] || die \
    "versionCode $version_code incohérent avec versionName $version_name (attendu : $expected_code)."

tag="v$version_name"

git rev-parse -q --verify "refs/tags/$tag" >/dev/null && die \
    "Le tag $tag existe déjà en local. Bumper la version dans $BUILD_FILE."
if git ls-remote --exit-code --tags origin "refs/tags/$tag" >/dev/null 2>&1; then
    die "Le tag $tag existe déjà sur origin. Bumper la version dans $BUILD_FILE."
fi

echo "Version : $version_name (code $version_code) → tag $tag"

# --- Vérifications et compilation ------------------------------------------

step "Tests unitaires et lint"
./gradlew testDebugUnitTest --tests "com.cstv.app.presentation.components.TvFocusSelectorStateTest" --tests "com.cstv.app.domain.model.EpisodeLabelTest" lintDebug

step "Compilation de l'APK signé"
./gradlew :app:assembleRelease

[[ -f "$APK_PATH" ]] || die "APK introuvable après compilation : $APK_PATH"

# Une signature absente ne casse pas le build : elle produit un APK non signé,
# que les appareils refuseront à l'installation. Autant le voir maintenant.
if command -v apksigner >/dev/null; then
    apksigner verify "$APK_PATH" >/dev/null || die "APK non signé ou signature invalide."
    echo "Signature vérifiée (apksigner)."
else
    unzip -l "$APK_PATH" | grep -q "META-INF/.*\.RSA" || die "APK sans bloc de signature."
    echo "Signature vérifiée (présence du bloc META-INF)."
fi

echo "APK prêt : $APK_PATH ($(du -h "$APK_PATH" | cut -f1))"

# Mise à jour de l'APK officiel à la racine dans le dossier releases/
mkdir -p releases
cp "$APK_PATH" releases/app-release.apk
echo "Copie effectuée vers releases/app-release.apk"

# On amende le dernier commit pour y inclure l'APK à jour (requis pour que l'arbre reste propre)
git add releases/app-release.apk
git commit --amend --no-edit
echo "Dernier commit amendé avec releases/app-release.apk"

if ! $publish; then
    echo
    echo "✅ --no-publish : ni tag ni release. L'APK est dans $APK_PATH et copié dans releases/app-release.apk."
    exit 0
fi

# --- Publication -----------------------------------------------------------
# Le tag n'est posé qu'après une compilation réussie : un tag qui ne compile
# pas ne doit jamais atteindre le dépôt distant.

step "Tag et publication"
git tag -a "$tag" -m "Release $tag"
git push origin main
git push origin "$tag"

# --- Déploiement automatique du backend si des modifications y sont présentes ---
if git diff --name-only HEAD~1 HEAD | grep -q "^backend/"; then
    step "Déploiement automatique du backend (modifications détectées dans backend/)"
    ./scripts/deploy-backend.sh
fi

gh release create "$tag" \
    --title "Release $tag" \
    --generate-notes \
    "$APK_PATH"

echo
echo "✅ Release $tag publiée."
gh release view "$tag" --json url -q .url
