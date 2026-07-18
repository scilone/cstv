package com.cstv.app.presentation.home

import com.cstv.app.R
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cstv.app.domain.model.SeriesStream
import com.cstv.app.domain.model.VodStream
import com.cstv.app.presentation.home.components.HomeSeriesShowCard
import com.cstv.app.presentation.home.components.HomeVodMovieCard
import com.cstv.app.presentation.theme.Surface1

@Composable
fun RecentlyAddedScreen(
    viewModel: RecentlyAddedViewModel,
    isTv: Boolean,
    isSeries: Boolean,
    onBack: () -> Unit,
    onSelectMovieDetail: (VodStream) -> Unit,
    onSelectSeriesDetail: (SeriesStream) -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(isSeries) {
        viewModel.loadRecentlyAdded(isSeries)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Surface1)
            .padding(16.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header cohérent avec les autres écrans (SearchScreen, détails VOD/Séries) :
            // pas de Scaffold/TopAppBar Material3 dans le reste de l'app.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.background(Color(0x33FFFFFF), shape = RoundedCornerShape(12.dp))
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back), tint = Color.White)
                }
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = if (isSeries) stringResource(R.string.recently_added_title_series) else stringResource(R.string.recently_added_title_movies),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }

            when {
                state.isLoading -> {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                }
                state.error != null -> {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(
                            text = state.error ?: stringResource(R.string.recently_added_error_fallback),
                            color = Color.Red,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
                isSeries && state.seriesStreams.isEmpty() -> {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(text = stringResource(R.string.recently_added_empty_series), color = Color.Gray)
                    }
                }
                !isSeries && state.vodStreams.isEmpty() -> {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(text = stringResource(R.string.recently_added_empty_movies), color = Color.Gray)
                    }
                }
                else -> {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(if (isTv) 130.dp else 100.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .focusGroup()
                    ) {
                        if (isSeries) {
                            items(state.seriesStreams) { stream ->
                                HomeSeriesShowCard(
                                    stream = stream,
                                    onClick = { onSelectSeriesDetail(stream) }
                                )
                            }
                        } else {
                            items(state.vodStreams) { stream ->
                                HomeVodMovieCard(
                                    stream = stream,
                                    onClick = { onSelectMovieDetail(stream) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
