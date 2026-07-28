package com.cstv.app.presentation.profile
import com.cstv.app.R
import androidx.compose.ui.res.stringResource

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
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
    val firstProfileFocusRequester = remember { FocusRequester() }
    LaunchedEffect(isTv, profiles.firstOrNull()?.id) {
        if (isTv && profiles.isNotEmpty()) {
            runCatching { firstProfileFocusRequester.requestFocus() }
        }
    }
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

            // Rangées de largeur intrinsèque, centrées : `GridCells.Adaptive`
            // répartissait les colonnes sur toute la largeur et collait les
            // avatars à gauche. Le nombre de profils par rangée est déduit de la
            // largeur réellement disponible, sinon trois avatars débordent de
            // l'écran d'un téléphone.
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val itemWidth = if (isTv) TV_ITEM_WIDTH else MOBILE_ITEM_WIDTH
                val perRow = ((maxWidth + ITEM_SPACING) / (itemWidth + ITEM_SPACING))
                    .toInt()
                    .coerceIn(1, MAX_PROFILES_PER_ROW)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    profiles.chunked(perRow).forEach { row ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(ITEM_SPACING, Alignment.CenterHorizontally),
                            verticalAlignment = Alignment.Top,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            row.forEach { profile ->
                                ProfileAvatarItem(
                                    profile = profile,
                                    width = itemWidth,
                                    avatarSize = if (isTv) 96 else 78,
                                    // Le premier profil porte le focus initial :
                                    // sur TV, l'écran arrivait sans aucun élément
                                    // focalisé.
                                    focusRequester = firstProfileFocusRequester
                                        .takeIf { isTv && profile.id == profiles.firstOrNull()?.id },
                                    onClick = { onProfileSelected(profile) }
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(40.dp))

            var manageFocused by remember { mutableStateOf(false) }
            OutlinedButton(
                onClick = onManageProfiles,
                border = BorderStroke(
                    if (manageFocused) 2.dp else 1.dp,
                    if (manageFocused) AccentLavande else Color.White.copy(alpha = 0.15f)
                ),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = if (manageFocused) Color.White else Color.White,
                    containerColor = if (manageFocused) AccentLavande.copy(alpha = 0.55f) else Surface3
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth(0.6f)
                    .height(48.dp)
                    .onFocusChanged { manageFocused = it.isFocused }
            ) {
                Text(
                    text = stringResource(R.string.profile_manage_title),
                    fontWeight = FontWeight.Bold,
                    fontFamily = HankenGrotesk
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Les états de focus par défaut de Material sont invisibles à
            // distance : ces deux commandes portent donc le même marquage
            // lavande que le reste de l'interface TV.
            var logoutFocused by remember { mutableStateOf(false) }
            TextButton(
                onClick = onLogout,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.textButtonColors(
                    containerColor = if (logoutFocused) AccentLavande.copy(alpha = 0.55f) else Color.Transparent
                ),
                modifier = Modifier.onFocusChanged { logoutFocused = it.isFocused }
            ) {
                Text(
                    text = stringResource(R.string.profile_logout),
                    color = if (logoutFocused) Color.White else Color.Gray,
                    fontWeight = if (logoutFocused) FontWeight.Bold else FontWeight.Normal
                )
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
    width: Dp,
    avatarSize: Int,
    focusRequester: FocusRequester?,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    // Marquage volontairement sobre : un simple contour lavande autour de
    // l'avatar suffit à situer le focus, sans agrandir ni colorer la cellule.
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(width)
            .clip(MaterialTheme.shapes.medium)
            .onFocusChanged { focused = it.isFocused }
            .then(focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier)
            .clickable { onClick() }
            .padding(vertical = 8.dp, horizontal = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .clip(CircleShape)
                .border(
                    width = if (focused) 3.dp else 0.dp,
                    color = if (focused) AccentLavande else Color.Transparent,
                    shape = CircleShape
                )
                .padding(4.dp)
        ) {
            ProfileAvatar(avatarId = profile.avatarId, name = profile.name, size = avatarSize)
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

/** Au-delà, les avatars deviennent trop petits pour être lus à distance sur TV. */
private const val MAX_PROFILES_PER_ROW = 5
private val TV_ITEM_WIDTH = 140.dp
private val MOBILE_ITEM_WIDTH = 110.dp
private val ITEM_SPACING = 16.dp
