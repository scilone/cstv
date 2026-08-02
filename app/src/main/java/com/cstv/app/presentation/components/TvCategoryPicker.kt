package com.cstv.app.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.cstv.app.R
import com.cstv.app.presentation.theme.AccentLavande
import com.cstv.app.presentation.theme.Surface1
import com.cstv.app.presentation.theme.Surface2

@Composable
fun TvCategorySelectorTrigger(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth().clip(shape)
            .background(Surface2)
            .onFocusChanged { focused = it.isFocused }
            .border(if (focused) 3.dp else 0.dp, if (focused) AccentLavande else Color.Transparent, shape)
            .clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Text(label, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Icon(Icons.Default.KeyboardArrowDown, null, tint = AccentLavande)
    }
}

/**
 * Dialogue plein écran (pas `DropdownMenu`, dont la gestion du focus D-pad
 * n'est pas fiable en material3) : même patron que `ProfileSelectionScreen`,
 * seule liste verticale focusable plein écran éprouvée en D-pad du projet.
 * Le focus est demandé sur l'entrée sélectionnée dès l'ouverture.
 */
@Composable
fun TvCategoryPickerDialog(
    entries: List<CategorySheetEntry>, selectedId: String?, onSelect: (String) -> Unit, onDismiss: () -> Unit
) {
    val selectedFocusRequester = remember { FocusRequester() }
    val focusTargetId = entries.firstOrNull { it.id == selectedId }?.id ?: entries.firstOrNull()?.id
    LaunchedEffect(Unit) {
        runCatching { selectedFocusRequester.requestFocus() }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier = Modifier.fillMaxSize().background(Surface1).padding(32.dp)
        ) {
            Text(stringResource(R.string.tv_category_picker_title), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp,
                modifier = Modifier.padding(bottom = 16.dp))
            LazyColumn(modifier = Modifier.fillMaxSize().focusGroup()) {
                items(entries, key = { it.id }) { entry ->
                    val selected = entry.id == selectedId
                    var focused by remember { mutableStateOf(false) }
                    val rowShape = RoundedCornerShape(10.dp)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth().clip(rowShape)
                            .background(if (selected) AccentLavande.copy(alpha = .22f) else Color.Transparent)
                            .onFocusChanged { focused = it.isFocused }
                            .border(if (focused) 3.dp else 0.dp, if (focused) AccentLavande else Color.Transparent, rowShape)
                            .then(if (entry.id == focusTargetId) Modifier.focusRequester(selectedFocusRequester) else Modifier)
                            .clickable { onSelect(entry.id) }.padding(14.dp)
                    ) {
                        Text(entry.label, color = Color.White, fontSize = 16.sp, modifier = Modifier.weight(1f))
                        entry.count?.let { Text(it.toString(), color = Color.LightGray) }
                        if (selected) Icon(Icons.Default.Check, stringResource(R.string.tv_category_picker_selected_description), tint = AccentLavande, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}
