package com.cstv.app.presentation.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp

@Composable
fun HomeMediaTypeBadge(
    label: String,
    fontSize: TextUnit,
    fontWeight: FontWeight,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(4.dp)
    Text(
        text = label,
        color = Color.White,
        fontSize = fontSize,
        fontWeight = fontWeight,
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.5f), shape)
            .border(1.dp, Color.White.copy(alpha = 0.2f), shape)
            .padding(contentPadding)
    )
}
