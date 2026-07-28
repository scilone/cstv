package com.cstv.app.presentation.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cstv.app.presentation.navigation.TvRailDestination
import com.cstv.app.presentation.navigation.TvRailIcon
import com.cstv.app.presentation.theme.AccentLavande
import com.cstv.app.presentation.theme.Surface1
import com.cstv.app.presentation.theme.Surface3

const val TV_RAIL_COLLAPSED_WIDTH_DP = 68

@Composable
fun TvNavigationRail(
    expanded: Boolean,
    selected: TvRailDestination?,
    profileAvatarId: Int,
    profileName: String,
    username: String?,
    expiryLabel: String?,
    destinations: List<TvRailDestination>,
    onExpandedChange: (Boolean) -> Unit,
    onDestinationClick: (TvRailDestination) -> Unit,
    modifier: Modifier = Modifier
) {
    val width = animateDpAsState(if (expanded) 260.dp else TV_RAIL_COLLAPSED_WIDTH_DP.dp, tween(200), label = "tvRailWidth")
    Column(
        modifier = modifier
            .width(width.value)
            .fillMaxHeight()
            .background(Surface1)
            .border(1.dp, Color.White.copy(alpha = 0.08f))
            .focusGroup()
            .onFocusChanged { onExpandedChange(it.hasFocus) }
            .padding(vertical = 18.dp, horizontal = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // En-tête profil : avatar à gauche, informations de session à sa droite.
        // Empilées sous l'avatar, elles écrasaient la hiérarchie de la barre.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            com.cstv.app.presentation.profile.ProfileAvatar(profileAvatarId, profileName, 42)
            if (expanded) {
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(profileName, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    username?.takeIf { it.isNotBlank() }?.let {
                        Text(it, color = Color.LightGray, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    expiryLabel?.let {
                        Text(it, color = Color.LightGray, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        // Séparation par l'espace uniquement : le trait alourdissait la barre.
        Spacer(Modifier.height(36.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            destinations.forEach { destination ->
                TvRailDestinationRow(
                    destination = destination,
                    isSelected = destination == selected,
                    expanded = expanded,
                    onClick = { onDestinationClick(destination) }
                )
            }
        }
        Spacer(Modifier.weight(1f))
        if (expanded) {
            Text(
                "CSTV",
                color = AccentLavande,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 24.dp)
            )
        }
    }
}

@Composable
private fun TvRailDestinationRow(
    destination: TvRailDestination,
    isSelected: Boolean,
    expanded: Boolean,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    // Trois niveaux distincts, le focus primant toujours sur la sélection :
    // à distance, savoir où est le pad importe plus que relire l'onglet courant.
    val background = when {
        focused -> AccentLavande.copy(alpha = 0.45f)
        isSelected -> AccentLavande.copy(alpha = 0.18f)
        else -> Color.Transparent
    }
    val contentColor = when {
        focused -> Color.White
        isSelected -> AccentLavande
        else -> Color(0xFFB9B9C6)
    }
    val shape = RoundedCornerShape(10.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .background(background, shape)
            .border(if (focused) 2.dp else 0.dp, if (focused) AccentLavande else Color.Transparent, shape)
            .onFocusChanged { focused = it.isFocused }
            .clickable { onClick() }
            .padding(horizontal = 10.dp)
    ) {
        Icon(
            destination.icon.imageVector(),
            stringResource(destination.labelRes),
            tint = contentColor,
            modifier = Modifier.size(24.dp)
        )
        if (expanded) {
            Spacer(Modifier.width(14.dp))
            Text(
                stringResource(destination.labelRes),
                color = contentColor,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun TvRailIcon.imageVector(): ImageVector = when (this) {
    TvRailIcon.HOME -> Icons.Default.Home
    TvRailIcon.LIVE_TV -> Icons.Default.LiveTv
    TvRailIcon.MOVIES -> Icons.Default.Movie
    TvRailIcon.SERIES -> Icons.Default.Tv
    TvRailIcon.SEARCH -> Icons.Default.Search
    TvRailIcon.SETTINGS -> Icons.Default.Settings
}
