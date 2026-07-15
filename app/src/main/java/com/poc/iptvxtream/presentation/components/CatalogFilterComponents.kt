package com.poc.iptvxtream.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import com.poc.iptvxtream.presentation.theme.AccentLavande
import com.poc.iptvxtream.presentation.theme.HankenGrotesk
import com.poc.iptvxtream.presentation.theme.Surface3
import com.poc.iptvxtream.presentation.theme.TextPrimary
import com.poc.iptvxtream.presentation.theme.TextSecondary

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
