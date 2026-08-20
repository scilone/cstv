package com.cstv.app.presentation.profile
import com.cstv.app.R
import androidx.compose.ui.res.stringResource

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
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
import com.cstv.app.presentation.theme.appScreenBackground

/**
 * Écran de gestion des profils façon Netflix (Phase 27, revu suite retour
 * utilisateur) : grille d'avatars avec crayon pour modifier, tuile "+" pour
 * ajouter, "Terminé" pour revenir. La modification reste volontairement
 * simple : nom + couleur + suppression, rien de plus.
 */
@Composable
fun ProfileManagementScreen(
    viewModel: ProfileViewModel,
    isTv: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    var editingProfile by remember { mutableStateOf<Profile?>(null) }
    var creatingProfile by remember { mutableStateOf(false) }
    var deleteError by remember { mutableStateOf<String?>(null) }

    Box(modifier = modifier.fillMaxSize().appScreenBackground(isTv)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(if (isTv) Modifier else Modifier.safeDrawingPadding()),
            contentAlignment = Alignment.Center
        ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth(if (isTv) 0.7f else 1f)
                .padding(24.dp)
        ) {
            Text(
                text = stringResource(R.string.profile_manage_title),
                color = Color.White,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = BricolageGrotesque,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Text(
                text = stringResource(R.string.profile_manage_subtitle),
                color = Color.Gray,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            deleteError?.let {
                Text(text = it, color = Color(0xFFCF6679), fontSize = 13.sp, modifier = Modifier.padding(bottom = 16.dp))
            }
            state.profileActionErrorRes?.let { errorRes ->
                Text(
                    text = stringResource(errorRes),
                    color = Color(0xFFCF6679),
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }

            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 120.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                modifier = Modifier.fillMaxWidth().weight(1f, fill = false)
            ) {
                items(state.profiles, key = { it.id }) { profile ->
                    EditableProfileItem(
                        profile = profile,
                        enabled = state.cloudCrudEnabled,
                        onEditClick = { editingProfile = profile }
                    )
                }
                item {
                    AddProfileTile(
                        enabled = state.cloudCrudEnabled,
                        onClick = { creatingProfile = true }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onBack,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C2C35)),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth(if (isTv) 0.4f else 0.7f).height(48.dp)
            ) {
                Text(stringResource(R.string.profile_manage_done), color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
        }
    }

    if (creatingProfile) {
        ProfileNameDialog(
            title = stringResource(R.string.profile_create_title),
            initialName = "",
            onConfirm = { name ->
                viewModel.createProfile(name, state.profiles.size % ProfileAvatars.count)
                creatingProfile = false
            },
            onDismiss = { creatingProfile = false }
        )
    }

    val deleteErrorMessage = stringResource(R.string.profile_delete_error)
    editingProfile?.let { selectedProfile ->
        // Le profil conservé pour ouvrir le dialogue ne doit pas devenir la
        // source de vérité : l'observation Room peut avoir reçu le nouveau
        // niveau d'âge pendant que le dialogue reste ouvert.
        val profile = state.profiles.firstOrNull { it.id == selectedProfile.id } ?: selectedProfile
        ProfileEditDialog(
            profile = profile,
            isAutoStart = state.autoStartProfileId == profile.id,
            onSave = { name, avatarId, autoStart ->
                if (name != profile.name) viewModel.renameProfile(profile.id, name)
                if (avatarId != profile.avatarId) viewModel.updateAvatar(profile.id, avatarId)
                val wasAutoStart = state.autoStartProfileId == profile.id
                if (autoStart != wasAutoStart) {
                    viewModel.setAutoStartProfile(if (autoStart) profile.id else null)
                }
                editingProfile = null
            },
            onDelete = {
                viewModel.deleteProfile(profile.id) { success ->
                    deleteError = if (success) null else deleteErrorMessage
                }
                editingProfile = null
            },
            onDismiss = { editingProfile = null },
            onAgeRatingChange = { newLevel -> viewModel.requestMaxAgeRatingChange(profile.id, newLevel) }
        )
    }

    // F44 : le changement de niveau reste modal au-dessus de l'écran de
    // gestion (et de ProfileEditDialog, qui reste ouvert derrière) tant que le
    // PIN n'est pas résolu ou annulé.
    var showingPinReset by remember { mutableStateOf(false) }
    state.pendingAgeRatingChange?.let { pending ->
        if (pending.requiresPinCreation) {
            com.cstv.app.presentation.components.ParentalPinCreationDialog(
                onCreate = { pin -> viewModel.createDevicePinAndApplyPendingChange(pin) },
                onDismiss = { viewModel.cancelAgeRatingChange() }
            )
        } else {
            com.cstv.app.presentation.components.ParentalPinEntryDialog(
                reason = null,
                feedback = state.ageRatingChangeFeedback,
                onSubmit = { pin, _ -> viewModel.confirmAgeRatingChange(pin) },
                onDismiss = { viewModel.cancelAgeRatingChange() },
                onForgotPin = { showingPinReset = true },
                offerRememberChoice = false,
            )
        }
    }

    if (showingPinReset) {
        val resetViewModel: ParentalPinResetViewModel = androidx.hilt.navigation.compose.hiltViewModel()
        val resetState by resetViewModel.state.collectAsStateWithLifecycle()
        com.cstv.app.presentation.components.ParentalPinResetDialog(
            state = resetState,
            onStart = resetViewModel::start,
            onSubmitOtp = resetViewModel::submitOtp,
            onSubmitNewPin = resetViewModel::submitNewPin,
            onDismiss = {
                resetViewModel.cancel()
                showingPinReset = false
            }
        )
        if (resetState.completed) {
            resetViewModel.consumeCompleted()
            showingPinReset = false
        }
    }
}

@Composable
private fun EditableProfileItem(profile: Profile, enabled: Boolean, onEditClick: () -> Unit) {
    // Seule la pastille crayon est cliquable/focalisable ; le contour se
    // dessine autour de l'avatar entier, sinon le marquage de focus est trop
    // petit pour situer le profil visé au D-pad.
    var focused by remember { mutableStateOf(false) }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(120.dp)
    ) {
        Box(contentAlignment = Alignment.BottomEnd) {
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .border(
                        width = if (focused) 3.dp else 0.dp,
                        color = if (focused) AccentLavande else Color.Transparent,
                        shape = CircleShape
                    )
            ) {
                ProfileAvatar(avatarId = profile.avatarId, name = profile.name, size = 96)
            }
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(if (enabled) Color.White else Color.LightGray)
                    .onFocusChanged { focused = it.isFocused }
                    .clickable(enabled = enabled) { onEditClick() },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Edit, contentDescription = "Modifier ${profile.name}", tint = Color.Black, modifier = Modifier.size(16.dp))
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

@Composable
private fun AddProfileTile(enabled: Boolean, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(120.dp)
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(if (enabled) Surface3 else Surface2)
                .border(1.dp, Color.DarkGray, CircleShape)
                .clickable(enabled = enabled) { onClick() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Add,
                contentDescription = stringResource(R.string.profile_add_description),
                tint = if (enabled) Color.White else Color.Gray,
                modifier = Modifier.size(36.dp)
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = stringResource(R.string.profile_add_tile),
            color = Color.LightGray,
            fontSize = 15.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ProfileNameDialog(
    title: String,
    initialName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                label = { Text(stringResource(R.string.profile_name_label)) }
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank()) onConfirm(name.trim()) },
                enabled = name.isNotBlank()
            ) { Text(stringResource(R.string.profile_dialog_validate)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.profile_dialog_cancel)) }
        }
    )
}

/** Pastille de couleur du sélecteur d'avatar : bordure blanche si sélectionnée, lavande au focus. */
@Composable
private fun ProfileColorSwatch(colorLong: Long, selected: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(Color(colorLong))
            .border(
                width = if (selected || focused) 3.dp else 0.dp,
                color = if (focused) AccentLavande else Color.White,
                shape = CircleShape
            )
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
    )
}

/**
 * Modification simple d'un profil existant : nom + couleur + suppression,
 * dans un seul dialog (retour utilisateur : l'ancien écran de modif façon
 * Netflix complet était superflu ici).
 */
@Composable
private fun ProfileEditDialog(
    profile: Profile,
    isAutoStart: Boolean,
    onSave: (name: String, avatarId: Int, autoStart: Boolean) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
    /** F44 : chaque changement de niveau passe par le PIN, indépendamment de "Enregistrer". */
    onAgeRatingChange: (Int?) -> Unit = {}
) {
    var name by remember { mutableStateOf(profile.name) }
    var avatarId by remember { mutableStateOf(profile.avatarId) }
    var autoStart by remember { mutableStateOf(isAutoStart) }
    var confirmingDelete by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.profile_dialog_edit_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.profile_name_label)) }
                )

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.profile_color_label), fontSize = 13.sp, color = Color.Gray)
                    ProfileAvatars.colors.chunked(4).forEachIndexed { rowIndex, rowColors ->
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            rowColors.forEachIndexed { colIndex, colorLong ->
                                val index = rowIndex * 4 + colIndex
                                ProfileColorSwatch(
                                    colorLong = colorLong,
                                    selected = index == avatarId,
                                    onClick = { avatarId = index }
                                )
                            }
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.profile_auto_start_label), fontSize = 13.sp, color = Color.White)
                        Text(
                            stringResource(R.string.profile_auto_start_description),
                            fontSize = 11.sp,
                            color = Color.Gray
                        )
                    }
                    Switch(
                        checked = autoStart,
                        onCheckedChange = { autoStart = it },
                        colors = SwitchDefaults.colors(checkedTrackColor = AccentLavande)
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.parental_age_rating_label), fontSize = 13.sp, color = Color.White)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                    ) {
                        // `null` = non bridé (aucune restriction) ; 0 = bridé sur "Tous publics"
                        // (bloque tout ce qui est classifié au-dessus). Deux options distinctes,
                        // jamais confondues (F44 §7.3/§8.1).
                        val options = listOf(
                            null to stringResource(R.string.parental_age_rating_none),
                            0 to stringResource(R.string.parental_age_rating_option_all),
                            10 to stringResource(R.string.parental_age_rating_option_10),
                            12 to stringResource(R.string.parental_age_rating_option_12),
                            16 to stringResource(R.string.parental_age_rating_option_16),
                            18 to stringResource(R.string.parental_age_rating_option_18),
                        )
                        options.forEach { (value, label) ->
                            FilterChip(
                                selected = value == profile.maxAgeRating,
                                onClick = { if (value != profile.maxAgeRating) onAgeRatingChange(value) },
                                label = { Text(label, fontSize = 12.sp) }
                            )
                        }
                    }
                }

                if (confirmingDelete) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.profile_delete_confirm_prompt), color = Color(0xFFCF6679), fontSize = 13.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { confirmingDelete = false }) { Text(stringResource(R.string.profile_confirm_delete_cancel)) }
                            TextButton(
                                onClick = onDelete,
                                colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFCF6679))
                            ) { Text(stringResource(R.string.profile_delete_confirm)) }
                        }
                    }
                } else {
                    // `colors` (pas seulement le texte) : sinon le focus TV se
                    // marque encore en lavande, la couleur par défaut du bouton.
                    TextButton(
                        onClick = { confirmingDelete = true },
                        colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFCF6679))
                    ) { Text(stringResource(R.string.profile_delete_btn)) }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank()) onSave(name.trim(), avatarId, autoStart) },
                enabled = name.isNotBlank()
            ) { Text(stringResource(R.string.profile_save_btn)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.profile_dialog_cancel)) }
        }
    )
}
