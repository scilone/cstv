package com.poc.iptvxtream.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.poc.iptvxtream.presentation.theme.Surface1
import com.poc.iptvxtream.presentation.theme.Surface3

/**
 * Rangée horizontale « Titres associés » réutilisée par les écrans de détails
 * VOD et Séries. Chaque item est un poster cliquable (2:3), focusable au D-pad.
 * Ne s'affiche pas si [items] est vide (le caller ne l'appelle alors pas).
 */
@Composable
fun <T> RelatedTitlesRow(
    title: String,
    items: List<T>,
    poster: (T) -> String?,
    label: (T) -> String,
    onClick: (T) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = title,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(bottom = 12.dp, start = 4.dp)
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth().focusGroup()
        ) {
            items(items) { item ->
                RelatedTitleCard(
                    cover = poster(item),
                    name = label(item),
                    onClick = { onClick(item) }
                )
            }
        }
    }
}

@Composable
private fun RelatedTitleCard(
    cover: String?,
    name: String,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .width(110.dp)
            .onFocusChanged { isFocused = it.isFocused }
            .border(
                width = 2.dp,
                color = if (isFocused) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = RoundedCornerShape(12.dp)
            )
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .background(Surface3)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .background(Surface1),
            contentAlignment = Alignment.Center
        ) {
            if (!cover.isNullOrBlank()) {
                AsyncImage(
                    model = cover,
                    contentDescription = name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = Color.DarkGray,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    }
}
