package com.cstv.app.presentation.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cstv.app.domain.model.CategoryType
import com.cstv.app.presentation.theme.Surface1
import com.cstv.app.presentation.theme.Surface3

/**
 * Écran de gestion des catégories par profil (Phase 58) : onglet par média
 * (TV/Films/Séries), masquage à l'œil et réordonnancement par flèches
 * haut/bas — boutons focusables, utilisables au D-pad sur Android TV.
 */
@Composable
fun CategoryManagementScreen(
    viewModel: CategoryManagementViewModel,
    isTv: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(if (isTv) Surface1 else Color.Transparent)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Retour",
                    tint = Color.White
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "GESTION DES CATÉGORIES",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }

        Text(
            text = "Masquez ou réordonnez les catégories pour le profil actif. Les catégories masquées disparaissent des grilles et de l'accueil.",
            color = Color.Gray,
            fontSize = 13.sp,
            modifier = Modifier.fillMaxWidth()
        )

        CategoryTypeTabs(
            selectedType = state.selectedType,
            onTypeSelected = { viewModel.selectType(it) }
        )

        when {
            state.isLoading -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }

            state.error != null -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(text = state.error ?: "", color = Color.Gray, fontSize = 14.sp)
            }

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(state.categories, key = { it.categoryId }) { category ->
                    CategoryManagementRow(
                        category = category,
                        onToggleHidden = { viewModel.toggleHidden(category.categoryId) },
                        onMoveUp = { viewModel.moveUp(category.categoryId) },
                        onMoveDown = { viewModel.moveDown(category.categoryId) }
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryTypeTabs(
    selectedType: CategoryType,
    onTypeSelected: (CategoryType) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        listOf(
            CategoryType.LIVE to "TV en direct",
            CategoryType.VOD to "Films",
            CategoryType.SERIES to "Séries"
        ).forEach { (type, label) ->
            Button(
                onClick = { onTypeSelected(type) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (selectedType == type) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        Color(0xFF2C2C35)
                    },
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.weight(1f).height(38.dp),
                contentPadding = PaddingValues(0.dp)
            ) {
                Text(text = label, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun CategoryManagementRow(
    category: ManagedCategory,
    onToggleHidden: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Surface3),
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = category.categoryName,
                color = if (category.hidden) Color.Gray else Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )

            IconButton(onClick = onMoveUp) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowUp,
                    contentDescription = "Monter ${category.categoryName}",
                    tint = Color.White
                )
            }
            IconButton(onClick = onMoveDown) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = "Descendre ${category.categoryName}",
                    tint = Color.White
                )
            }
            IconButton(onClick = onToggleHidden) {
                Icon(
                    imageVector = if (category.hidden) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription = if (category.hidden) {
                        "Afficher ${category.categoryName}"
                    } else {
                        "Masquer ${category.categoryName}"
                    },
                    tint = if (category.hidden) Color.Gray else MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
