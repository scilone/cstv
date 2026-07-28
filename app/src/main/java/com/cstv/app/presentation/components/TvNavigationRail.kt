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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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
        com.cstv.app.presentation.profile.ProfileAvatar(profileAvatarId, profileName, 42)
        if (expanded) {
            Spacer(Modifier.height(10.dp))
            Text(profileName, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1)
            username?.takeIf { it.isNotBlank() }?.let { Text(it, color = Color.LightGray, fontSize = 12.sp, maxLines = 1) }
            expiryLabel?.let { Text(it, color = Color.LightGray, fontSize = 12.sp, maxLines = 1) }
        }
        Spacer(Modifier.height(18.dp))
        HorizontalDivider(color = Color.White.copy(alpha = 0.12f))
        Spacer(Modifier.height(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            destinations.forEach { destination ->
                val isSelected = destination == selected
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .background(if (isSelected) AccentLavande.copy(alpha = 0.22f) else Color.Transparent, RoundedCornerShape(10.dp))
                        .clickable { onDestinationClick(destination) }
                        .padding(horizontal = 10.dp)
                ) {
                    Icon(destination.icon.imageVector(), stringResource(destination.labelRes), tint = if (isSelected) AccentLavande else Color.White, modifier = Modifier.size(24.dp))
                    if (expanded) {
                        Spacer(Modifier.width(14.dp))
                        Text(stringResource(destination.labelRes), color = if (isSelected) AccentLavande else Color.White, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
        if (expanded) Text("CSTV", color = AccentLavande, fontWeight = FontWeight.Bold)
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
