package com.cstv.app.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cstv.app.domain.sync.CatalogStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Bandeau discret et non bloquant en tête des écrans catalogue.
 *
 * T7 (D2) : purement informatif, jamais un signal d'échec. En ligne, une
 * synchronisation silencieuse s'occupe déjà d'un catalogue périmé (voir
 * `CatalogSyncManagerImpl.syncIfStale`) : le bandeau resterait anxiogène pour
 * rien. Il n'apparaît donc que si l'appareil est réellement hors ligne, pour
 * indiquer que les données affichées sont locales et datées ; il ne remplace
 * jamais le contenu.
 *
 * T7-R1 : la garde se fonde sur [CatalogStatus.isNetworkOnline] (connectivité
 * réelle) et non [CatalogStatus.isOffline], qui reste vrai après un ancien
 * échec réseau même une fois la connexion revenue.
 */
@Composable
fun OfflineBanner(
    status: CatalogStatus,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (status.isNetworkOnline) return
    if (!status.isComplete) return

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0x22FFFFFF))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = bannerLabel(status),
            color = Color.White.copy(alpha = 0.85f),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )

        if (status.isSyncing) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = Color.White.copy(alpha = 0.85f)
            )
        } else {
            // Réessayer reste proposé hors ligne : le retour du réseau est le
            // cas le plus fréquent, et l'échec est de toute façon non bloquant.
            TextButton(onClick = onRetry) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = " Réessayer",
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 13.sp
                )
            }
        }
    }
}

// N'est appelée que hors ligne (voir garde en tête de OfflineBanner) : les
// libellés en ligne n'ont pas lieu d'être.
private fun bannerLabel(status: CatalogStatus): String {
    val date = formatSyncDate(status.lastFullSyncAt)
    return if (date != null) "Hors ligne — catalogue du $date" else "Hors ligne — catalogue local"
}

private fun formatSyncDate(timestamp: Long): String? {
    if (timestamp <= 0L) return null
    return try {
        SimpleDateFormat("dd/MM/yyyy à HH:mm", Locale.getDefault()).format(Date(timestamp))
    } catch (e: Exception) {
        null
    }
}
