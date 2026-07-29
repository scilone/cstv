package com.cstv.app.presentation

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow

@Composable
fun rememberForeverLazyListState(
    key: String,
    getScroll: (String) -> Pair<Int, Int>,
    saveScroll: (String, Int, Int) -> Unit
): LazyListState {
    val position = remember { getScroll(key) }
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = position.first,
        initialFirstVisibleItemScrollOffset = position.second
    )

    LaunchedEffect(listState) {
        snapshotFlow {
            Pair(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset)
        }.collect { (index, offset) ->
            saveScroll(key, index, offset)
        }
    }

    return listState
}

/**
 * État de défilement d'une rangée horizontale.
 *
 * Sur TV, la position n'est délibérément pas mémorisée d'une session à
 * l'autre : le pad descend depuis la vignette de gauche de la rangée du
 * dessus, mais une rangée restaurée au milieu de son contenu n'expose que des
 * vignettes éloignées, et le focus atterrit sur celle qui se trouve être la
 * plus proche — d'où une entrée qui semblait arbitraire, souvent tout à
 * droite. Une rangée qui commence à son début rend ce point d'entrée
 * prévisible. Sur mobile, le doigt navigue librement : la position reprise
 * garde tout son sens.
 */
@Composable
fun rememberRowScrollState(
    isTv: Boolean,
    key: String,
    getScroll: (String) -> Pair<Int, Int>,
    saveScroll: (String, Int, Int) -> Unit
): LazyListState =
    if (isTv) rememberLazyListState() else rememberForeverLazyListState(key, getScroll, saveScroll)

@Composable
fun rememberForeverLazyGridState(
    key: String,
    getScroll: (String) -> Pair<Int, Int>,
    saveScroll: (String, Int, Int) -> Unit
): LazyGridState {
    val position = remember { getScroll(key) }
    val gridState = rememberLazyGridState(
        initialFirstVisibleItemIndex = position.first,
        initialFirstVisibleItemScrollOffset = position.second
    )

    LaunchedEffect(gridState) {
        snapshotFlow {
            Pair(gridState.firstVisibleItemIndex, gridState.firstVisibleItemScrollOffset)
        }.collect { (index, offset) ->
            saveScroll(key, index, offset)
        }
    }

    return gridState
}
