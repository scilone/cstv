package com.cstv.app.presentation.player.core

import androidx.compose.ui.input.key.Key

/**
 * Traduction des touches de télécommande en intentions de lecture, partagée par
 * les trois lecteurs (Live TV / VOD / Séries).
 *
 * Deux règles distinctes, volontairement séparées :
 *
 * - les **touches média** (play, pause, avance, retour, suivant, précédent,
 *   stop) valent toujours, quel que soit l'habillage et l'état de l'overlay :
 *   elles ne dépendent pas du focus et sont donc consommées en phase *preview*,
 *   avant que le bouton focalisé ne les voie ;
 * - le **pad directionnel sur TV** ne sert qu'à une chose à la fois : naviguer
 *   dans les options quand l'overlay est visible, révéler l'overlay quand il ne
 *   l'est pas. Aucun raccourci de lecture n'est câblé sur les flèches, sinon la
 *   navigation D-pad devient impossible dès que les contrôles s'affichent.
 *
 * La résolution est une fonction pure pour rester vérifiable en test JVM :
 * `FocusRequester`, `onPreviewKeyEvent` et la composition ne le sont pas dans ce
 * projet (voir `AGENTS.md`, pas de test instrumenté ni de Robolectric).
 */
enum class PlayerKeyIntent {
    /** Bascule lecture/pause. */
    PlayPause,

    /** Reprise explicite (touche dédiée de la télécommande). */
    Play,

    /** Mise en pause explicite (touche dédiée de la télécommande). */
    Pause,

    /** Avance de 10 s (sans effet sur un flux live). */
    FastForward,

    /** Retour de 10 s (sans effet sur un flux live). */
    Rewind,

    /** Élément suivant : épisode suivant en série, chaîne suivante en live. */
    Next,

    /** Élément précédent : épisode précédent en série, chaîne précédente en live. */
    Previous,

    /** Ferme le lecteur, comme le retour arrière. */
    Stop,

    /** Affiche les contrôles sans rien déclencher d'autre. */
    RevealControls
}

/**
 * Touches média de la télécommande. `null` quand la touche n'en est pas une :
 * l'appelant poursuit alors son propre traitement.
 */
fun resolveMediaKeyIntent(key: Key): PlayerKeyIntent? = when (key) {
    Key.MediaPlayPause, Key.Spacebar -> PlayerKeyIntent.PlayPause
    Key.MediaPlay -> PlayerKeyIntent.Play
    Key.MediaPause -> PlayerKeyIntent.Pause
    Key.MediaFastForward -> PlayerKeyIntent.FastForward
    Key.MediaRewind -> PlayerKeyIntent.Rewind
    Key.MediaNext, Key.MediaSkipForward -> PlayerKeyIntent.Next
    Key.MediaPrevious, Key.MediaSkipBackward -> PlayerKeyIntent.Previous
    Key.MediaStop -> PlayerKeyIntent.Stop
    else -> null
}

/**
 * Pad directionnel sur TV. `null` signifie « ne pas consommer » : l'événement
 * poursuit sa route, soit vers le bouton focalisé (validation), soit vers le
 * système de focus de Compose (déplacement).
 *
 * @param controlsVisible overlay de contrôles affiché ou non.
 */
fun resolveTvDpadIntent(key: Key, controlsVisible: Boolean): PlayerKeyIntent? = when (key) {
    Key.DirectionUp, Key.DirectionDown, Key.DirectionLeft, Key.DirectionRight ->
        // Overlay visible : les flèches n'appartiennent qu'à la navigation.
        if (controlsVisible) null else PlayerKeyIntent.RevealControls
    Key.DirectionCenter, Key.Enter, Key.NumPadEnter ->
        // Overlay visible : c'est le bouton focalisé qui doit s'activer.
        if (controlsVisible) null else PlayerKeyIntent.PlayPause
    else -> null
}
