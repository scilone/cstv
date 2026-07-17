package com.poc.iptvxtream.presentation.home

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.poc.iptvxtream.domain.model.SeriesStream
import com.poc.iptvxtream.domain.model.VodStream
import com.poc.iptvxtream.presentation.home.components.HomeSeriesShowCard
import com.poc.iptvxtream.presentation.home.components.HomeVodMovieCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecentlyAddedScreen(
    viewModel: RecentlyAddedViewModel,
    isTv: Boolean,
    isSeries: Boolean,
    onBack: () -> Unit,
    onSelectMovieDetail: (VodStream) -> Unit,
    onSelectSeriesDetail: (SeriesStream) -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(isSeries) {
        viewModel.loadRecentlyAdded(isSeries)
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (isSeries) "100 Dernières Séries Ajoutées" else "100 Derniers Films Ajoutés",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Retour",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF0F0F13),
                    titleContentColor = Color.White
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0F0F13))
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
        ) {
            when {
                state.isLoading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                }
                state.error != null -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = state.error ?: "Une erreur est survenue.",
                            color = Color.Red,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
                isSeries && state.seriesStreams.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(text = "Aucune série récemment ajoutée.", color = Color.Gray)
                    }
                }
                !isSeries && state.vodStreams.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(text = "Aucun film récemment ajouté.", color = Color.Gray)
                    }
                }
                else -> {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(if (isTv) 130.dp else 100.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier
                            .fillMaxSize()
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
