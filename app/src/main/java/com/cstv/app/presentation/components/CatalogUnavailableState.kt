package com.cstv.app.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * État qualifié du premier démarrage sans cache et sans réseau.
 *
 * À n'afficher que lorsque le catalogue est réellement absent **et**
 * l'appareil hors ligne : il ne doit jamais se substituer à une liste
 * simplement filtrée à vide, ce qui ferait passer un filtre pour une panne.
 */
@Composable
fun CatalogUnavailableState(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    isRetrying: Boolean = false
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Filled.Refresh,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.5f),
            modifier = Modifier.size(48.dp)
        )
        Text(
            text = "Catalogue indisponible",
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 16.dp)
        )
        Text(
            text = "La première synchronisation du catalogue nécessite une connexion Internet. " +
                "Vos profils, favoris et téléchargements restent disponibles.",
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp)
        )
        Button(
            onClick = onRetry,
            enabled = !isRetrying,
            modifier = Modifier.padding(top = 24.dp)
        ) {
            Text(if (isRetrying) "Synchronisation…" else "Réessayer")
        }
    }
}
