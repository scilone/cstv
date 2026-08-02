package com.cstv.app.presentation.login

import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cstv.app.presentation.theme.appScreenBackground

@Composable
fun SplashScreen(
    isTv: Boolean,
    modifier: Modifier = Modifier
) {
    // Le fond est peint edge-to-edge sur la Box racine ; les insets sûrs ne
    // s'appliquent qu'au contenu, sinon la surface (claire) sous-jacente
    // transparaît sous les barres système (B19).
    Box(modifier = modifier.fillMaxSize().appScreenBackground(isTv)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(if (isTv) Modifier else Modifier.safeDrawingPadding()),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(24.dp)
            ) {
                Text(
                    text = "XTREAM CODES",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 2.sp,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "CONNEXION AUTOMATIQUE EN COURS...",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Light,
                    color = Color.Gray,
                    letterSpacing = 1.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp, bottom = 32.dp)
                )
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp)
                )
            }
        }
    }
}
