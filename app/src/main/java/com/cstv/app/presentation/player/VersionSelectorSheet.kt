package com.cstv.app.presentation.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cstv.app.R
import com.cstv.app.presentation.theme.Surface1
import com.cstv.app.presentation.theme.Surface3

/**
 * Une entrée du sélecteur (F39 §8.5) — agnostique VOD/série : le ViewModel
 * appelant construit cette liste depuis `VodRepository.getVersionsByLinkKey`
 * ou `SeriesVersionResolver.resolve`, l'un et l'autre déjà nommés par leurs
 * attributs extraits T21 (ex. « VF · 4K », §8.1 — jamais le libellé brut).
 */
data class VersionOption(val id: Int, val label: String, val isActive: Boolean)

/**
 * Réutilise l'emplacement et le style de focus de [com.cstv.app.presentation.
 * vod.VodPlayerScreen]'s `TrackSelectionDialog` (§8.5) : mêmes rangées à
 * cases radio, même mise en surbrillance au focus D-pad — mobile comme
 * Android TV.
 *
 * Correction F39-R5 (étape 7) : la spécification (§7.2 pt. 2, §8.9) demande
 * une bottom sheet sur mobile et une modale sur TV, pas le même `AlertDialog`
 * sur les deux — un `AlertDialog` centré ne se pilote pas au D-pad de la même
 * façon qu'une feuille ancrée en bas ne se pilote au tactile, et l'un des deux
 * n'était donc pas le composant réellement livré pour sa plateforme.
 * `content()` factorise les lignes de version, seul le conteneur diffère.
 *
 * Fermé pendant le chargement d'une bascule ([isSwitching]) : §8.5 « pendant
 * le chargement, le sélecteur est fermé et les nouveaux changements sont
 * ignorés jusqu'au succès/rollback ».
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VersionSelectorSheet(
    options: List<VersionOption>,
    isSwitching: Boolean,
    onSelect: (VersionOption) -> Unit,
    onDismiss: () -> Unit,
    isTv: Boolean = false
) {
    if (isTv) {
        AlertDialog(
            onDismissRequest = { if (!isSwitching) onDismiss() },
            title = {
                Text(
                    stringResource(R.string.player_version_dialog_title),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            containerColor = Surface3,
            shape = RoundedCornerShape(16.dp),
            text = { VersionSelectorContent(options, isSwitching, onSelect) },
            confirmButton = {
                Button(
                    onClick = onDismiss,
                    enabled = !isSwitching,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text(stringResource(R.string.player_close), color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        )
    } else {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { if (!isSwitching) onDismiss() },
            sheetState = sheetState,
            containerColor = Surface3
        ) {
            Text(
                stringResource(R.string.player_version_dialog_title),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
            )
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                VersionSelectorContent(options, isSwitching, onSelect)
                Button(
                    onClick = onDismiss,
                    enabled = !isSwitching,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 24.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text(stringResource(R.string.player_close), color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun VersionSelectorContent(
    options: List<VersionOption>,
    isSwitching: Boolean,
    onSelect: (VersionOption) -> Unit
) {
    if (isSwitching) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
    } else {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            options.forEach { option ->
                var isFocused by remember { mutableStateOf(false) }
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .onFocusChanged { isFocused = it.isFocused }
                        .border(
                            width = 1.dp,
                            color = if (isFocused) MaterialTheme.colorScheme.primary else Color.DarkGray,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .background(if (isFocused) Color(0xFF2C2C35) else if (option.isActive) Color(0x33FFB300) else Surface1)
                        .clickable { onSelect(option) }
                        .padding(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = option.isActive,
                            onClick = { onSelect(option) },
                            colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = option.label,
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = if (option.isActive) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }
    }
}
