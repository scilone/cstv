package com.poc.iptvxtream.presentation.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
 * Carte de gestion des profils dans les Paramètres (Phase 27) : bascule de
 * profil actif, création, renommage, changement d'avatar, suppression (avec
 * garde-fou sur le dernier profil). Utilisable en mobile et TV (material3).
 */
@Composable
fun ProfileManagementCard(viewModel: ProfileViewModel) {
    val state by viewModel.state.collectAsState()

    var editingProfile by remember { mutableStateOf<Profile?>(null) }
    var creatingProfile by remember { mutableStateOf(false) }
    var deleteError by remember { mutableStateOf<String?>(null) }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E24)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Profils",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
            Text(
                text = "Chaque profil a ses propres favoris, historique et reprises de lecture. Le catalogue reste commun.",
                color = Color.Gray,
                fontSize = 12.sp
            )

            deleteError?.let {
                Text(text = it, color = Color(0xFFCF6679), fontSize = 12.sp)
            }

            state.profiles.forEach { profile ->
                ProfileRow(
                    profile = profile,
                    isActive = profile.id == state.activeProfileId,
                    onSelect = { viewModel.selectProfile(profile.id) },
                    onEdit = { editingProfile = profile },
                    onCycleAvatar = {
                        viewModel.updateAvatar(profile.id, (profile.avatarId + 1) % ProfileAvatars.count)
                    },
                    onDelete = {
                        viewModel.deleteProfile(profile.id) { success ->
                            deleteError = if (success) null else "Impossible de supprimer le dernier profil."
                        }
                    }
                )
            }

            Button(
                onClick = { creatingProfile = true },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C2C35)),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth().height(40.dp)
            ) {
                Text("Ajouter un profil", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
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
}

@Composable
private fun ProfileRow(
    profile: Profile,
    isActive: Boolean,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onCycleAvatar: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (isActive) Color(0xFF2C2C35) else Color.Transparent)
            .clickable { onSelect() }
            .padding(8.dp)
    ) {
        ProfileAvatar(avatarId = profile.avatarId, name = profile.name, size = 40)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = profile.name,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (isActive) {
                Text("Profil actif", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp)
            }
        }
        TextButton(onClick = onCycleAvatar) { Text("Avatar", fontSize = 12.sp) }
        TextButton(onClick = onEdit) { Text("Renommer", fontSize = 12.sp) }
        TextButton(onClick = onDelete) {
            Text("Suppr.", fontSize = 12.sp, color = Color(0xFFCF6679))
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
