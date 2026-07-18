package com.cstv.app.presentation.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit

@Composable
fun ExpandableText(
    text: String,
    modifier: Modifier = Modifier,
    maxLinesCollapsed: Int = 3,
    fontSize: TextUnit,
    lineHeight: TextUnit,
    color: Color = Color.LightGray,
    textAlign: TextAlign = TextAlign.Start
) {
    var isExpanded by remember { mutableStateOf(false) }
    var isOverflowing by remember { mutableStateOf(false) }

    Column(modifier = modifier) {
        Text(
            text = text,
            color = color,
            fontSize = fontSize,
            lineHeight = lineHeight,
            textAlign = textAlign,
            maxLines = if (isExpanded) Int.MAX_VALUE else maxLinesCollapsed,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { result ->
                isOverflowing = result.hasVisualOverflow
            }
        )

        if (isOverflowing) {
            Text(
                text = if (isExpanded) "Voir moins" else "Voir plus",
                color = MaterialTheme.colorScheme.primary,
                fontSize = fontSize * 0.9f,
                modifier = Modifier.clickable { isExpanded = !isExpanded }
            )
        }
    }
}
