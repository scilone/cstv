package com.cstv.app.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cstv.app.presentation.theme.AccentLavande
import com.cstv.app.presentation.theme.HankenGrotesk
import com.cstv.app.presentation.theme.Surface1
import com.cstv.app.presentation.theme.Surface2
import com.cstv.app.presentation.theme.Surface3
import com.cstv.app.presentation.theme.TextPrimary
import com.cstv.app.presentation.theme.TextSecondary

/**
 * Phase 56 : déclencheur unifié du sélecteur de catégorie (TV/Films/Séries).
 * Occupe toute la largeur disponible, fond Surface3 neutre, chevron gris —
 * conforme à la maquette (plus de bouton "Rafraîchir" à côté).
 */
@Composable
fun CategorySelectorTrigger(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .background(Surface3)
            .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Text(
            text = label,
            color = TextPrimary,
            fontFamily = HankenGrotesk,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.5.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Icon(
            imageVector = Icons.Default.KeyboardArrowDown,
            contentDescription = null,
            tint = TextSecondary,
            modifier = Modifier.size(22.dp)
        )
    }
}

/**
 * Phase 56 : champ de recherche unifié affiché lors du filtrage d'une catégorie
 * spécifique. Fond grisé (Surface3) identique au dropdown, coins arrondis (14 dp),
 * aéré, icône de recherche en accent — conforme à la maquette.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategorySearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholder, color = TextSecondary, fontSize = 13.sp) },
        leadingIcon = {
            Icon(
                Icons.Default.Search,
                contentDescription = null,
                tint = AccentLavande,
                modifier = Modifier.size(18.dp)
            )
        },
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = Surface3,
            unfocusedContainerColor = Surface3,
            focusedBorderColor = Color.White.copy(alpha = 0.12f),
            unfocusedBorderColor = Color.White.copy(alpha = 0.08f),
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            cursorColor = AccentLavande
        ),
        modifier = modifier.fillMaxWidth()
    )
}

/** Entrée générique du sélecteur de catégorie (TV/Films/Séries partagent la sheet). */
data class CategorySheetEntry(
    val id: String,
    val label: String,
    val count: Int? = null
)

/**
 * Bottom sheet de sélection de catégorie, iso-maquette (voir
 * docs/design-reference/screenshots/category-filter.png) :
 * pas de titre, champ "Filtrer les catégories…" sur fond Surface1 (plus foncé
 * que la sheet) avec loupe grise et coins 12 dp, lignes sans séparateur, entrée
 * sélectionnée sur fond lavande à 12 % arrondi avec texte blanc + coche accent,
 * compteur de médias gris à droite quand il est connu.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryFilterSheet(
    entries: List<CategorySheetEntry>,
    selectedId: String?,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Surface2,
        contentColor = Color.White,
        scrimColor = Color.Black.copy(alpha = 0.55f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                placeholder = { Text("Filtrer les catégories…", color = TextSecondary, fontSize = 13.5.sp, fontFamily = HankenGrotesk) },
                leadingIcon = {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(19.dp)
                    )
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Surface1,
                    unfocusedContainerColor = Surface1,
                    focusedBorderColor = Color.White.copy(alpha = 0.08f),
                    unfocusedBorderColor = Color.White.copy(alpha = 0.08f),
                    focusedTextColor = Color(0xFFF2F2F6),
                    unfocusedTextColor = Color(0xFFF2F2F6),
                    cursorColor = AccentLavande
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp)
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 440.dp)
                    .padding(bottom = 16.dp)
            ) {
                items(entries, key = { it.id }) { entry ->
                    val isSelected = entry.id == selectedId
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 2.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) AccentLavande.copy(alpha = 0.12f) else Color.Transparent)
                            .clickable { onSelect(entry.id) }
                            .padding(vertical = 13.dp, horizontal = 12.dp)
                    ) {
                        Text(
                            text = entry.label,
                            fontFamily = HankenGrotesk,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 14.sp,
                            color = if (isSelected) Color(0xFFF4F4F7) else Color(0xFFC7C7D1),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        entry.count?.let { count ->
                            Text(
                                text = count.toString(),
                                fontFamily = HankenGrotesk,
                                fontWeight = FontWeight.Medium,
                                fontSize = 12.sp,
                                color = Color(0xFF63636F)
                            )
                        }
                        if (isSelected) {
                            Spacer(modifier = Modifier.width(10.dp))
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Sélectionné",
                                tint = AccentLavande,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
