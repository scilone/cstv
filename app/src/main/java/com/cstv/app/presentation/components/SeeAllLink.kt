package com.cstv.app.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cstv.app.R
import com.cstv.app.presentation.theme.AccentLavande
import com.cstv.app.presentation.theme.HankenGrotesk

@Composable
fun SeeAllLink(isTv: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(if (isTv) 8.dp else 6.dp)
    Text(
        text = if (isTv) stringResource(R.string.home_see_all).uppercase() else stringResource(R.string.home_see_all),
        fontSize = if (isTv) 14.sp else 12.5.sp,
        fontWeight = FontWeight.SemiBold,
        color = AccentLavande,
        fontFamily = HankenGrotesk,
        modifier = modifier
            .onFocusChanged { focused = it.isFocused }
            .clip(shape)
            .background(if (isTv && focused) AccentLavande.copy(alpha = 0.22f) else Color.Transparent)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(horizontal = if (isTv) 10.dp else 4.dp, vertical = if (isTv) 6.dp else 2.dp)
    )
}
