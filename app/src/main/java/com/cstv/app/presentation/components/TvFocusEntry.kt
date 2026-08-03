package com.cstv.app.presentation.components

/**
 * Cible à re-focaliser quand le focus rentre dans un écran catalogue TV depuis
 * l'extérieur (retour de la barre latérale, arrivée sur l'écran).
 */
enum class TvFocusEntryTarget {
    /** Vignette exacte quittée, mémorisée par l'écran. */
    RESTORED_MEDIA,

    /** Premier média de la section par défaut, faute de vignette mémorisée. */
    INITIAL_MEDIA,

    /** Ne rien forcer : laisser le focus là où la recherche par défaut l'a posé. */
    NONE
}

/**
 * Règle de repositionnement du focus à l'entrée d'un écran catalogue TV.
 *
 * MainActivity ramène le focus au contenu par `requestFocus()` sur un
 * conteneur non focalisable. Compose 1.6 traite ce cas par
 * `findChildCorrespondingToFocusEnter`, qui ne consulte ni `focusRestorer` ni
 * `focusProperties.enter` : le focus se pose sur le premier focusable de
 * l'écran, c'est-à-dire son chrome. Aucun média n'étant alors focalisé, la
 * rangée quittée est perdue et le sélecteur pivot n'est jamais republié. Les
 * écrans détectent donc eux-mêmes le front montant de leur propre `hasFocus`
 * et appliquent cette règle.
 *
 * @param alreadyFocused l'écran avait déjà le focus : déplacement interne, rien à forcer.
 * @param modalHandoverPending une surface modale de l'écran vient de se fermer ;
 *   c'est à elle de décider où retourne le focus (cf. [MODAL_FOCUS_HANDOVER_MS]).
 */
fun tvFocusEntryTarget(
    alreadyFocused: Boolean,
    modalHandoverPending: Boolean,
    hasRestoreTarget: Boolean,
    hasInitialTarget: Boolean
): TvFocusEntryTarget = when {
    alreadyFocused || modalHandoverPending -> TvFocusEntryTarget.NONE
    hasRestoreTarget -> TvFocusEntryTarget.RESTORED_MEDIA
    hasInitialTarget -> TvFocusEntryTarget.INITIAL_MEDIA
    else -> TvFocusEntryTarget.NONE
}
