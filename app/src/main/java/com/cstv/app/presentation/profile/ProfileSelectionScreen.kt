package com.cstv.app.presentation.profile
import com.cstv.app.R
import androidx.compose.ui.res.stringResource

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cstv.app.domain.model.Profile
import com.cstv.app.domain.model.ProfileAvatars
import com.cstv.app.presentation.theme.AccentLavande
import com.cstv.app.presentation.theme.BricolageGrotesque
import com.cstv.app.presentation.theme.HankenGrotesk
import com.cstv.app.presentation.theme.Surface1
import com.cstv.app.presentation.theme.Surface2
import com.cstv.app.presentation.theme.Surface3
import com.cstv.app.presentation.theme.mobileBackground
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape

/**
 * Écran de sélection de profil façon Netflix (Phase 27), affiché après login/
 * auto-login lorsqu'il existe plusieurs profils, et accessible à tout moment
 * depuis l'avatar de la Home. Partagé mobile/TV : le focus D-pad fonctionne
 * via la focusabilité standard des éléments cliquables.
 */
@Composable
fun ProfileSelectionScreen(
    profiles: List<Profile>,
    isTv: Boolean,
    onProfileSelected: (Profile) -> Unit,
    onManageProfiles: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .then(if (isTv) Modifier else Modifier.safeDrawingPadding())
            .mobileBackground(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth().padding(24.dp)
        ) {
            Text(
                text = stringResource(R.string.profile_selection_title),
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = BricolageGrotesque,
                modifier = Modifier.padding(bottom = 32.dp)
            )

            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 120.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(24.dp),
                modifier = Modifier.fillMaxWidth(0.9f).wrapContentHeight()
            ) {
                items(profiles, key = { it.id }) { profile ->
                    ProfileAvatarItem(
                        profile = profile,
                        onClick = { onProfileSelected(profile) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(40.dp))

            OutlinedButton(
                onClick = onManageProfiles,
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Color.White,
                    containerColor = Surface3
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(0.6f).height(48.dp)
            ) {
                Text(
                    text = stringResource(R.string.profile_manage_title),
                    fontWeight = FontWeight.Bold,
                    fontFamily = HankenGrotesk
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            TextButton(onClick = onLogout) {
                Text(stringResource(R.string.profile_logout), color = Color.Gray)
            }
        }
    }
}

@Composable
fun ProfileAvatar(avatarId: Int, name: String, size: Int, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(Color(ProfileAvatars.colorFor(avatarId))),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = name.take(1).uppercase(),
            color = Color.Black,
            fontSize = (size / 2.4f).sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun ProfileAvatarItem(profile: Profile, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(MaterialTheme.shapes.medium)
            .clickable { onClick() }
            .padding(8.dp)
    ) {
        ProfileAvatar(avatarId = profile.avatarId, name = profile.name, size = 96)
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = profile.name,
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}
