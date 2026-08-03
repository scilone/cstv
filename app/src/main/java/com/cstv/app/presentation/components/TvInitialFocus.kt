package com.cstv.app.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

internal const val INITIAL_FOCUS_ATTEMPTS = 10
internal const val INITIAL_FOCUS_RETRY_MS = 80L

@Stable
class TvInitialFocusState internal constructor() {
    val requester = FocusRequester()
    internal var acquired by mutableStateOf(false)
    fun markAcquired() { acquired = true }
}

/**
 * Requests focus only after a concrete TV target has been composed.
 *
 * [targetKey] is the only re-arm trigger (a genuinely new target — new tab,
 * new category, manual reload). [ready] is read live from inside the same
 * coroutine instead of being a `LaunchedEffect` key: callers whose readiness
 * flag flickers transiently for the *same* target (e.g. `LazyPagingItems`
 * briefly reporting `itemCount == 0` while its flow is recreated by an
 * unrelated text-search recomposition) must not restart the attempt window
 * and steal focus back from wherever the user already is.
 */
@Composable
fun rememberTvInitialFocus(
    isTv: Boolean,
    ready: Boolean,
    targetKey: Any?
): TvInitialFocusState {
    val state = remember(targetKey) { TvInitialFocusState() }
    val readyState = rememberUpdatedState(ready)
    LaunchedEffect(isTv, targetKey) {
        if (!isTv) return@LaunchedEffect
        runInitialFocusAttempts(
            awaitReady = { snapshotFlow { readyState.value }.first { it } },
            isAcquired = { state.acquired },
            requestFocus = { runCatching { state.requester.requestFocus() } }
        )
    }
    return state
}

/**
 * Compose-free core of [rememberTvInitialFocus] : wait for [awaitReady], then
 * call [requestFocus] up to [attempts] times (spaced by [retryMs]), stopping
 * as soon as [isAcquired] reports true. Extracted so the bounded-attempt
 * contract is verifiable in a plain JVM test — `LaunchedEffect`/`FocusRequester`
 * require a live composition and aren't unit-testable in this project (no
 * Compose UI test / Robolectric dependency, see `AGENTS.md`).
 */
internal suspend fun runInitialFocusAttempts(
    awaitReady: suspend () -> Unit,
    isAcquired: () -> Boolean,
    requestFocus: () -> Unit,
    attempts: Int = INITIAL_FOCUS_ATTEMPTS,
    retryMs: Long = INITIAL_FOCUS_RETRY_MS
) {
    awaitReady()
    repeat(attempts) {
        if (isAcquired()) return
        requestFocus()
        delay(retryMs)
    }
}

fun Modifier.tvInitialFocusTarget(state: TvInitialFocusState): Modifier =
    focusRequester(state.requester).onFocusChanged { if (it.isFocused) state.markAcquired() }

/** No-op variant for call sites that only conditionally own the initial-focus target (F19/B17 grids). */
fun Modifier.tvInitialFocusTarget(state: TvInitialFocusState?, active: Boolean): Modifier =
    if (active && state != null) tvInitialFocusTarget(state) else this
