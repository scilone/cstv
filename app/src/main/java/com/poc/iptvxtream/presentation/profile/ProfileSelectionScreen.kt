package com.poc.iptvxtream.presentation.profile
import com.poc.iptvxtream.R
import androidx.compose.ui.res.stringResource

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.poc.iptvxtream.domain.model.Profile
import com.poc.iptvxtream.domain.model.ProfileAvatars
import com.poc.iptvxtream.presentation.theme.AccentLavande
import com.poc.iptvxtream.presentation.theme.BricolageGrotesque
import com.poc.iptvxtream.presentation.theme.HankenGrotesk
import com.poc.iptvxtream.presentation.theme.Surface1
import com.poc.iptvxtream.presentation.theme.Surface2
import com.poc.iptvxtream.presentation.theme.Surface3
import com.poc.iptvxtream.presentation.theme.mobileBackground
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
    onProfileSelected: (Profile) -> Unit,
    onManageProfiles: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Le chargement de la Home après sélection (catalogue, favoris, positions
    // de lecture) peut prendre un instant perceptible au premier lancement ou
    // sur un profil jamais ouvert. selectedProfile déclenche un overlay de
    // chargement immédiat au tap ; onProfileSelected n'est appelé qu'au frame
    // suivant (LaunchedEffect) pour garantir que l'overlay a le temps d'être
    // dessiné avant la navigation vers la Home.
    var selectedProfile by remember { mutableStateOf<Profile?>(null) }

    LaunchedEffect(selectedProfile) {
        selectedProfile?.let { onProfileSelected(it) }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
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
                        isLoading = selectedProfile?.id == profile.id,
                        enabled = selectedProfile == null,
                        onClick = { selectedProfile = profile }
                    )
                }
            }

            Spacer(modifier = Modifier.height(40.dp))

            OutlinedButton(
                onClick = onManageProfiles,
                enabled = selectedProfile == null,
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

            TextButton(onClick = onLogout, enabled = selectedProfile == null) {
                Text("Déconnexion", color = Color.Gray)
            }

            if (selectedProfile != null) {
                Spacer(modifier = Modifier.height(24.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator(
                        color = AccentLavande,
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                    Text(
                        text = stringResource(R.string.profile_selection_loading),
                        color = Color.White,
                        fontSize = 14.sp,
                        fontFamily = HankenGrotesk
                    )
                }
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
private fun ProfileAvatarItem(
    profile: Profile,
    onClick: () -> Unit,
    isLoading: Boolean = false,
    enabled: Boolean = true
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(MaterialTheme.shapes.medium)
            .clickable(enabled = enabled) { onClick() }
            .alpha(if (enabled || isLoading) 1f else 0.4f)
            .padding(8.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            ProfileAvatar(avatarId = profile.avatarId, name = profile.name, size = 96)
            if (isLoading) {
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(36.dp))
                }
            }
        }
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
