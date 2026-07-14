package com.poc.iptvxtream.presentation.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.poc.iptvxtream.domain.model.Profile
import com.poc.iptvxtream.domain.model.ProfileAvatars

/**
 * Écran plein écran de gestion des profils (Phase 27, revu suite retour
 * utilisateur : accessible depuis l'avatar en haut à gauche de la Home,
 * plus depuis les Paramètres). Switch, création, renommage, changement de
 * couleur d'avatar, suppression (avec garde-fou sur le dernier profil).
 */
@Composable
fun ProfileManagementScreen(
    viewModel: ProfileViewModel,
    isTv: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsState()

    var editingProfile by remember { mutableStateOf<Profile?>(null) }
    var colorPickerProfile by remember { mutableStateOf<Profile?>(null) }
    var creatingProfile by remember { mutableStateOf(false) }
    var deleteError by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0F0F13))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .then(if (isTv) Modifier.fillMaxWidth(0.85f) else Modifier)
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour", tint = Color.White)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "GESTION DES PROFILS",
                    fontSize = if (isTv) 22.sp else 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Text(
                text = "Chaque profil a ses propres favoris, historique et reprises de lecture. Le catalogue reste commun.",
                color = Color.Gray,
                fontSize = 13.sp,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            deleteError?.let {
                Text(text = it, color = Color(0xFFCF6679), fontSize = 13.sp, modifier = Modifier.padding(bottom = 8.dp))
            }

            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(state.profiles, key = { it.id }) { profile ->
                    ProfileManagementRow(
                        profile = profile,
                        isActive = profile.id == state.activeProfileId,
                        onSelect = { viewModel.selectProfile(profile.id) },
                        onEdit = { editingProfile = profile },
                        onChangeColor = { colorPickerProfile = profile },
                        onDelete = {
                            viewModel.deleteProfile(profile.id) { success ->
                                deleteError = if (success) null else "Impossible de supprimer le dernier profil restant."
                            }
                        }
                    )
                }
                item {
                    Button(
                        onClick = { creatingProfile = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C2C35)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().height(44.dp).padding(top = 8.dp)
                    ) {
                        Text("+ Ajouter un profil", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    if (creatingProfile) {
        ProfileNameDialog(
            title = "Nouveau profil",
            initialName = "",
            onConfirm = { name ->
                viewModel.createProfile(name, state.profiles.size % ProfileAvatars.count)
                creatingProfile = false
            },
            onDismiss = { creatingProfile = false }
        )
    }

    editingProfile?.let { profile ->
        ProfileNameDialog(
            title = "Renommer le profil",
            initialName = profile.name,
            onConfirm = { name ->
                viewModel.renameProfile(profile.id, name)
                editingProfile = null
            },
            onDismiss = { editingProfile = null }
        )
    }

    colorPickerProfile?.let { profile ->
        ProfileColorDialog(
            currentAvatarId = profile.avatarId,
            onColorSelected = { avatarId ->
                viewModel.updateAvatar(profile.id, avatarId)
                colorPickerProfile = null
            },
            onDismiss = { colorPickerProfile = null }
        )
    }
}

@Composable
private fun ProfileManagementRow(
    profile: Profile,
    isActive: Boolean,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onChangeColor: () -> Unit,
    onDelete: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (isActive) Color(0xFF2C2C35) else Color(0xFF1E1E24))
            .clickable { onSelect() }
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ProfileAvatar(avatarId = profile.avatarId, name = profile.name, size = 44)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = profile.name,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (isActive) {
                    Text("Profil actif", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onChangeColor) { Text("Couleur", fontSize = 13.sp) }
            TextButton(onClick = onEdit) { Text("Renommer", fontSize = 13.sp) }
            TextButton(onClick = onDelete) {
                Text("Supprimer", fontSize = 13.sp, color = Color(0xFFCF6679))
            }
        }
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
                label = { Text("Nom du profil") }
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank()) onConfirm(name.trim()) },
                enabled = name.isNotBlank()
            ) { Text("Valider") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annuler") }
        }
    )
}

@Composable
private fun ProfileColorDialog(
    currentAvatarId: Int,
    onColorSelected: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Couleur du profil") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ProfileAvatars.colors.chunked(4).forEachIndexed { rowIndex, rowColors ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        rowColors.forEachIndexed { colIndex, colorLong ->
                            val index = rowIndex * 4 + colIndex
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(Color(colorLong))
                                    .border(
                                        width = if (index == currentAvatarId) 3.dp else 0.dp,
                                        color = Color.White,
                                        shape = CircleShape
                                    )
                                    .clickable { onColorSelected(index) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Fermer") }
        }
    )
}
