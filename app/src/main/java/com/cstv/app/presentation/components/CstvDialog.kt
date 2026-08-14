package com.cstv.app.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.cstv.app.presentation.theme.AccentLavande
import com.cstv.app.presentation.theme.AccentLavandeHover
import com.cstv.app.presentation.theme.Surface2
import com.cstv.app.presentation.theme.Surface4
import com.cstv.app.presentation.theme.TextPrimary
import com.cstv.app.presentation.theme.TextSecondary

/**
 * Dialogue de confirmation conforme à la charte (fond sombre `Surface2`, titre
 * blanc, corps gris, actions lavande) et lisible au D-pad.
 *
 * `AlertDialog` de Material 3 ne convient pas sur TV : il peint le conteneur
 * avec la couleur de son thème (lavande pleine) et ses boutons n'ont aucun état
 * de focus distinct du repos — à trois mètres, impossible de dire quelle action
 * est sélectionnée. Ici l'action focalisée change de fond **et** gagne un liseré
 * clair, deux signaux plutôt qu'un.
 */
@Composable
fun CstvDialog(
    title: String,
    message: String,
    isTv: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable () -> Unit
) {
    Dialog(onDismissRequest = onDismissRequest) {
        Box(
            modifier = modifier
                .widthIn(max = if (isTv) 560.dp else 340.dp)
                .clip(RoundedCornerShape(if (isTv) 16.dp else 12.dp))
                .background(Surface2)
                .padding(if (isTv) 28.dp else 20.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(if (isTv) 16.dp else 12.dp)) {
                Text(
                    text = title,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = if (isTv) 20.sp else 18.sp
                )
                Text(
                    text = message,
                    color = TextSecondary,
                    fontSize = if (isTv) 15.sp else 14.sp
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                    content = { actions() }
                )
            }
        }
    }
}

/**
 * Action de dialogue. [primary] porte l'aplat lavande, les autres un fond neutre
 * `Surface4` ; dans les deux cas le focus D-pad éclaircit le fond et ajoute un
 * liseré, pour que la sélection reste lisible de loin.
 */
@Composable
fun CstvDialogAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    enabled: Boolean = true,
    isTv: Boolean = false,
    content: (@Composable () -> Unit)? = null
) {
    var isFocused by remember { mutableStateOf(false) }
    val container = when {
        isFocused && primary -> AccentLavandeHover
        isFocused -> Surface4
        primary -> AccentLavande
        else -> Color(0x1FFFFFFF)
    }
    Box(
        modifier = modifier
            .heightIn(min = if (isTv) 48.dp else 44.dp)
            .onFocusChanged { isFocused = it.isFocused }
            .clip(RoundedCornerShape(8.dp))
            .then(if (enabled) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .then(if (enabled) Modifier.focusable() else Modifier)
            .background(container)
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) TextPrimary else Color(0x33FFFFFF),
                shape = RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .alpha(if (enabled) 1f else 0.5f),
        contentAlignment = Alignment.Center
    ) {
        if (content != null) {
            content()
        } else {
            Text(
                text = text,
                color = if (primary && !isFocused) Color.White else TextPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = if (isTv) 15.sp else 14.sp
            )
        }
    }
}
