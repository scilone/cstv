package com.cstv.app.presentation.profile
import com.cstv.app.R
import androidx.compose.ui.res.stringResource

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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

    Box(
        modifier = modifier
            .fillMaxSize()
            .then(if (isTv) Modifier.background(Surface1) else Modifier.mobileBackground()),
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

            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 120.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                modifier = Modifier.fillMaxWidth().weight(1f, fill = false)
            ) {
                items(state.profiles, key = { it.id }) { profile ->
                    EditableProfileItem(
                        profile = profile,
                        onEditClick = { editingProfile = profile }
                    )
                }
                item {
                    AddProfileTile(onClick = { creatingProfile = true })
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
    editingProfile?.let { profile ->
        ProfileEditDialog(
            profile = profile,
            onSave = { name, avatarId ->
                if (name != profile.name) viewModel.renameProfile(profile.id, name)
                if (avatarId != profile.avatarId) viewModel.updateAvatar(profile.id, avatarId)
                editingProfile = null
            },
            onDelete = {
                viewModel.deleteProfile(profile.id) { success ->
                    deleteError = if (success) null else deleteErrorMessage
                }
                editingProfile = null
            },
            onDismiss = { editingProfile = null }
        )
    }
}

@Composable
private fun EditableProfileItem(profile: Profile, onEditClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(120.dp)
    ) {
        Box(contentAlignment = Alignment.BottomEnd) {
            ProfileAvatar(avatarId = profile.avatarId, name = profile.name, size = 96)
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .clickable { onEditClick() },
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
private fun AddProfileTile(onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(120.dp)
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(Surface3)
                .border(1.dp, Color.DarkGray, CircleShape)
                .clickable { onClick() },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.profile_add_description), tint = Color.White, modifier = Modifier.size(36.dp))
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

/**
 * Modification simple d'un profil existant : nom + couleur + suppression,
 * dans un seul dialog (retour utilisateur : l'ancien écran de modif façon
 * Netflix complet était superflu ici).
 */
@Composable
private fun ProfileEditDialog(
    profile: Profile,
    onSave: (name: String, avatarId: Int) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(profile.name) }
    var avatarId by remember { mutableStateOf(profile.avatarId) }
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
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(Color(colorLong))
                                        .border(
                                            width = if (index == avatarId) 3.dp else 0.dp,
                                            color = Color.White,
                                            shape = CircleShape
                                        )
                                        .clickable { avatarId = index }
                                )
                            }
                        }
                    }
                }

                if (confirmingDelete) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.profile_delete_confirm_prompt), color = Color(0xFFCF6679), fontSize = 13.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { confirmingDelete = false }) { Text("Annuler") }
                            TextButton(onClick = onDelete) {
                                Text(stringResource(R.string.profile_delete_confirm), color = Color(0xFFCF6679))
                            }
                        }
                    }
                } else {
                    TextButton(onClick = { confirmingDelete = true }) {
                        Text(stringResource(R.string.profile_delete_btn), color = Color(0xFFCF6679))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank()) onSave(name.trim(), avatarId) },
                enabled = name.isNotBlank()
            ) { Text(stringResource(R.string.profile_save_btn)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.profile_dialog_cancel)) }
        }
    )
}
