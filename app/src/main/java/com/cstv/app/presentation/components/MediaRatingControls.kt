package com.cstv.app.presentation.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.cstv.app.R
import com.cstv.app.domain.model.MediaRatingValue
import com.cstv.app.presentation.theme.AccentLavande
import com.cstv.app.presentation.theme.Surface3

@Composable
fun MediaRatingControls(
    value: MediaRatingValue?,
    isSaving: Boolean,
    isTv: Boolean,
    onLike: () -> Unit,
    onDislike: () -> Unit,
    modifier: Modifier = Modifier
) {
    val content: @Composable () -> Unit = {
        RatingButton(MediaRatingValue.LIKE, value == MediaRatingValue.LIKE, isSaving, isTv, onLike)
        RatingButton(MediaRatingValue.DISLIKE, value == MediaRatingValue.DISLIKE, isSaving, isTv, onDislike)
    }
    if (isTv) {
        Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
    } else {
        Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            androidx.compose.foundation.layout.Box(Modifier.weight(1f)) { RatingButton(MediaRatingValue.LIKE, value == MediaRatingValue.LIKE, isSaving, isTv, onLike) }
            androidx.compose.foundation.layout.Box(Modifier.weight(1f)) { RatingButton(MediaRatingValue.DISLIKE, value == MediaRatingValue.DISLIKE, isSaving, isTv, onDislike) }
        }
    }
}

@Composable
private fun RatingButton(value: MediaRatingValue, selected: Boolean, isSaving: Boolean, isTv: Boolean, onClick: () -> Unit) {
    val color by animateColorAsState(if (selected) AccentLavande else Surface3, label = "ratingColor")
    val scale by animateFloatAsState(if (selected) 1.02f else 1f, label = "ratingScale")
    val label = stringResource(if (value == MediaRatingValue.LIKE) R.string.media_rating_like else R.string.media_rating_dislike)
    val description = stringResource(if (selected) R.string.media_rating_selected_description else R.string.media_rating_action_description, label)
    val modifier = Modifier
        .fillMaxWidth()
        .height(if (isTv) 40.dp else 48.dp)
        .scale(scale)
        .semantics { contentDescription = description }
    val content: @Composable () -> Unit = {
        if (isSaving) CircularProgressIndicator(modifier = Modifier.width(18.dp).height(18.dp), color = Color.White, strokeWidth = 2.dp)
        else Icon(if (value == MediaRatingValue.LIKE) Icons.Default.ThumbUp else Icons.Default.ThumbDown, contentDescription = null)
        Text(label, modifier = Modifier.padding(start = 8.dp))
    }
    if (selected) Button(onClick = onClick, enabled = !isSaving, modifier = modifier, colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = Color.White)) { content() }
    else OutlinedButton(onClick = onClick, enabled = !isSaving, modifier = modifier.border(1.dp, Color.White.copy(alpha = 0.25f)), colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)) { content() }
}
