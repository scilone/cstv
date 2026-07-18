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
