package com.cstv.app.presentation.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.cstv.app.R
import com.cstv.app.domain.model.MediaRatingValue
import com.cstv.app.presentation.theme.RatingDislike
import com.cstv.app.presentation.theme.RatingLike

@Composable
fun MediaRatingControls(
    value: MediaRatingValue?,
    isTv: Boolean,
    onLike: () -> Unit,
    onDislike: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (isTv) {
        Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            RatingButton(MediaRatingValue.LIKE, value == MediaRatingValue.LIKE, isTv, onLike)
            RatingButton(MediaRatingValue.DISLIKE, value == MediaRatingValue.DISLIKE, isTv, onDislike)
        }
    } else {
        Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.weight(1f)) { RatingButton(MediaRatingValue.LIKE, value == MediaRatingValue.LIKE, isTv, onLike) }
            Box(Modifier.weight(1f)) { RatingButton(MediaRatingValue.DISLIKE, value == MediaRatingValue.DISLIKE, isTv, onDislike) }
        }
    }
}

@Composable
private fun RatingButton(value: MediaRatingValue, selected: Boolean, isTv: Boolean, onClick: () -> Unit) {
    val selectedColor = if (value == MediaRatingValue.LIKE) RatingLike else RatingDislike
    val color by animateColorAsState(if (selected) selectedColor else Color.Transparent, label = "ratingColor")
    val scale by animateFloatAsState(if (selected) 1.02f else 1f, label = "ratingScale")
    val label = stringResource(if (value == MediaRatingValue.LIKE) R.string.media_rating_like else R.string.media_rating_dislike)
    val description = stringResource(if (selected) R.string.media_rating_selected_description else R.string.media_rating_action_description, label)
    val modifier = Modifier
        .fillMaxWidth()
        .height(if (isTv) 40.dp else 48.dp)
        .scale(scale)
        .semantics { contentDescription = description }
    val content: @Composable () -> Unit = {
        Icon(if (value == MediaRatingValue.LIKE) Icons.Default.ThumbUp else Icons.Default.ThumbDown, contentDescription = null)
        Text(label, modifier = Modifier.padding(start = 8.dp))
    }
    // Instant, silent toggle: no loader, no disabled state while the rating persists in the background.
    if (selected) {
        Button(onClick = onClick, modifier = modifier, colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = Color.White)) { content() }
    } else {
        OutlinedButton(onClick = onClick, modifier = modifier, border = BorderStroke(1.dp, Color.White), colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)) { content() }
    }
}
