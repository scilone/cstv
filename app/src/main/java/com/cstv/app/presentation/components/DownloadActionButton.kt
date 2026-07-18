package com.cstv.app.presentation.components

import com.cstv.app.R
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cstv.app.domain.model.DownloadStatus
import com.cstv.app.domain.model.DownloadedItem
import com.cstv.app.presentation.theme.Surface3

/**
 * Bouton d'action de téléchargement (feature #15), partagé entre les détails
 * VOD et Séries. Reflète l'état courant : télécharger / en cours (%) / terminé
 * (→ supprimer) / échec (→ réessayer).
 */
@Composable
fun DownloadActionButton(
    item: DownloadedItem?,
    onDownload: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }

    val status = item?.status
    val label: String = when (status) {
        null -> stringResource(R.string.download_action_download)
        DownloadStatus.QUEUED, DownloadStatus.REMOVING -> stringResource(R.string.download_action_queued)
        DownloadStatus.DOWNLOADING -> stringResource(R.string.download_action_downloading, item.percent)
        DownloadStatus.PAUSED -> stringResource(R.string.download_action_paused)
        DownloadStatus.COMPLETED -> stringResource(R.string.download_action_downloaded)
        DownloadStatus.FAILED -> stringResource(R.string.download_action_failed)
    }

    val icon = when (status) {
        DownloadStatus.COMPLETED -> Icons.Default.CheckCircle
        DownloadStatus.FAILED -> Icons.Default.ErrorOutline
        else -> Icons.Default.Download
    }

    val container = when (status) {
        DownloadStatus.COMPLETED -> Color(0xFF2E7D32)
        DownloadStatus.FAILED -> Color(0xFFCF6679)
        else -> Surface3
    }

    val onClick: () -> Unit = when (status) {
        null, DownloadStatus.FAILED -> onDownload
        DownloadStatus.COMPLETED -> onRemove
        // En cours / en attente / pause : un tap annule/supprime le téléchargement.
        else -> onRemove
    }

    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isFocused) MaterialTheme.colorScheme.primary else container,
            contentColor = Color.White
        ),
        shape = RoundedCornerShape(8.dp),
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp)
            .onFocusChanged { isFocused = it.isFocused }
            .border(
                width = 2.dp,
                color = if (isFocused) Color.Yellow else Color.Transparent,
                shape = RoundedCornerShape(8.dp)
            )
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = label, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
    }
}
