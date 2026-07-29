package com.cstv.app.presentation.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
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

/** Hauteur constante de l'en-tête profil, plié comme déplié. */
private val TV_RAIL_HEADER_HEIGHT = 56.dp

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
    onProfileClick: () -> Unit,
    onCloseToContent: () -> Unit,
    modifier: Modifier = Modifier
) {
    val selectedFocusRequester = remember { FocusRequester() }
    LaunchedEffect(expanded, selected) {
        if (expanded && selected != null) {
            runCatching { selectedFocusRequester.requestFocus() }
        }
    }
    val width = animateDpAsState(if (expanded) 260.dp else TV_RAIL_COLLAPSED_WIDTH_DP.dp, tween(200), label = "tvRailWidth")
    Column(
        modifier = modifier
            .width(width.value)
            .fillMaxHeight()
            // Sans bordure : le liseré tranchait franchement sur le contenu.
            .background(Surface1)
            .focusGroup()
            .onFocusChanged { onExpandedChange(it.hasFocus) }
            .onKeyEvent { event ->
                // La barre est superposée au contenu : la recherche de focus
                // vers la droite ne trouve rien à côté d'elle. Droite doit donc
                // explicitement la refermer et rendre la main au contenu.
                if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionRight) {
                    onCloseToContent()
                    true
                } else {
                    false
                }
            }
            .padding(vertical = 18.dp, horizontal = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // En-tête profil : avatar à gauche, informations de session à sa droite.
        // Hauteur fixe : sans elle, le bloc de texte révélé à l'ouverture
        // agrandissait l'en-tête et décalait verticalement toutes les icônes.
        var profileFocused by remember { mutableStateOf(false) }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .height(TV_RAIL_HEADER_HEIGHT)
        ) {
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .border(
                        if (profileFocused) 3.dp else 0.dp,
                        if (profileFocused) AccentLavande else Color.Transparent,
                        CircleShape
                    )
                    .onFocusChanged { profileFocused = it.isFocused }
                    .clickable { onProfileClick() }
                    .padding(3.dp)
            ) {
                com.cstv.app.presentation.profile.ProfileAvatar(profileAvatarId, profileName, 42)
            }
            if (expanded) {
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        profileName,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        lineHeight = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    username?.takeIf { it.isNotBlank() }?.let {
                        Text(it, color = Color.LightGray, fontSize = 11.sp, lineHeight = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    expiryLabel?.let {
                        Text(it, color = Color.LightGray, fontSize = 11.sp, lineHeight = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        // Séparation par l'espace uniquement : le trait alourdissait la barre.
        Spacer(Modifier.height(36.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            destinations.forEach { destination ->
                val isSelected = destination == selected
                TvRailDestinationRow(
                    destination = destination,
                    isSelected = isSelected,
                    expanded = expanded,
                    // À l'ouverture, le focus doit se poser sur la section
                    // courante plutôt que sur la première destination.
                    focusRequester = selectedFocusRequester.takeIf { isSelected },
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
    focusRequester: FocusRequester?,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    // Ni aplat ni bordure : le focus se lit au libellé, blanc et gras, la
    // destination courante à son icône violette. La sélection prime sur le
    // focus pour la teinte de l'icône — survoler la section où l'on se trouve
    // ne doit pas lui faire perdre sa couleur, sans quoi on ne sait plus où
    // l'on est.
    val iconColor = when {
        isSelected -> AccentLavande
        focused -> Color.White
        else -> Color(0xFFB9B9C6)
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .onFocusChanged { focused = it.isFocused }
            .then(focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier)
            .clickable { onClick() }
            .padding(horizontal = 10.dp)
    ) {
        Icon(
            destination.icon.imageVector(),
            stringResource(destination.labelRes),
            tint = iconColor,
            modifier = Modifier.size(24.dp)
        )
        if (expanded) {
            Spacer(Modifier.width(14.dp))
            Text(
                stringResource(destination.labelRes),
                color = if (focused) Color.White else Color(0xFFB9B9C6),
                fontWeight = if (focused || isSelected) FontWeight.Bold else FontWeight.Normal,
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
