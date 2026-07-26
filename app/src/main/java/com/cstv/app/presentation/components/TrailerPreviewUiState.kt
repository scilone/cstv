package com.cstv.app.presentation.components

import com.cstv.app.domain.model.TrailerPreview

sealed interface TrailerPreviewUiState {
    data object Poster : TrailerPreviewUiState
    data object Preparing : TrailerPreviewUiState
    data class Playing(val preview: TrailerPreview) : TrailerPreviewUiState
    data object Failed : TrailerPreviewUiState
}
