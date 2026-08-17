package com.cstv.app.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cstv.app.R
import com.cstv.app.presentation.player.VersionOption

/**
 * Bouton de lecture principal accolé à un chevron ouvrant la liste des
 * versions disponibles — un seul contrôle scindé en deux zones partageant la
 * même forme, plutôt qu'un bouton « Versions » séparé (retour PO).
 *
 * Mobile uniquement : la liste est ancrée juste sous le chevron
 * (`DropdownMenu`), pilotage tactile fiable. Sur TV, `DropdownMenu` n'a pas de
 * focus D-pad fiable en material3 (voir `TvCategoryPicker`) — les layouts TV
 * composent leur propre variante autour du dialogue plein écran existant
 * (`VersionSelectorSheet`) plutôt que de réutiliser ce composant.
 */
@Composable
fun PlayButtonWithVersionsDropdown(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    containerColor: Color,
    contentColor: Color,
    versions: List<VersionOption>,
    onSelectVersion: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Row(modifier = modifier.height(44.dp)) {
        Button(
            onClick = onClick,
            colors = ButtonDefaults.buttonColors(containerColor = containerColor),
            shape = RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp, topEnd = 0.dp, bottomEnd = 0.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
            modifier = Modifier.weight(1f).fillMaxHeight()
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = contentColor)
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = text, color = contentColor, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }

        Box(
            modifier = Modifier
                .width(1.dp)
                .fillMaxHeight()
                .background(Color.Black.copy(alpha = 0.22f))
        )

        Box {
            Button(
                onClick = { expanded = true },
                colors = ButtonDefaults.buttonColors(containerColor = containerColor),
                shape = RoundedCornerShape(topStart = 0.dp, bottomStart = 0.dp, topEnd = 8.dp, bottomEnd = 8.dp),
                contentPadding = PaddingValues(horizontal = 12.dp),
                modifier = Modifier.fillMaxHeight()
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = stringResource(R.string.player_versions_action_label),
                    tint = contentColor
                )
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.background(com.cstv.app.presentation.theme.Surface2)
            ) {
                versions.forEach { version ->
                    DropdownMenuItem(
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = version.label,
                                    modifier = Modifier.weight(1f),
                                    fontWeight = if (version.isActive) FontWeight.Bold else FontWeight.Normal,
                                    color = if (version.isActive) MaterialTheme.colorScheme.primary else Color.White
                                )
                                if (version.isActive) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.width(16.dp)
                                    )
                                }
                            }
                        },
                        onClick = {
                            expanded = false
                            onSelectVersion(version.id)
                        }
                    )
                }
            }
        }
    }
}
