package com.poc.iptvxtream.presentation.settings

import com.poc.iptvxtream.data.local.storage.SyncFrequency
import com.poc.iptvxtream.data.local.storage.AppAccentColor
import com.poc.iptvxtream.presentation.theme.AccentAmber
import com.poc.iptvxtream.presentation.theme.AccentBlue
import com.poc.iptvxtream.presentation.theme.AccentLavande
import com.poc.iptvxtream.presentation.theme.AccentTeal
import com.poc.iptvxtream.presentation.theme.BricolageGrotesque
import com.poc.iptvxtream.presentation.theme.HankenGrotesk
import com.poc.iptvxtream.presentation.theme.Surface1
import com.poc.iptvxtream.presentation.theme.Surface2
import com.poc.iptvxtream.presentation.theme.Surface3
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.CircleShape
import com.poc.iptvxtream.domain.model.SubtitleBackground
import com.poc.iptvxtream.domain.model.SubtitleStyle
import com.poc.iptvxtream.domain.model.SubtitleTextColor
import com.poc.iptvxtream.domain.model.SubtitleTextSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button as TvButton
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme as TvTheme
import androidx.tv.material3.Text as TvText
import com.poc.iptvxtream.data.local.storage.CategorySorting

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    isTv: Boolean,
    onBack: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(if (isTv) Surface1 else Color.Transparent),
        contentAlignment = Alignment.Center
    ) {
        if (isTv) {
            TvSettingsLayout(
                state = state,
                onTvSortingChanged = { viewModel.updateTvSorting(it) },
                onVodSortingChanged = { viewModel.updateVodSorting(it) },
                onSeriesSortingChanged = { viewModel.updateSeriesSorting(it) },
                onSyncFrequencyChanged = { viewModel.updateSyncFrequency(it) },
                onForceSyncNow = { viewModel.forceSyncNow() },
                onSubtitleSizeChanged = { viewModel.updateSubtitleSize(it) },
                onSubtitleColorChanged = { viewModel.updateSubtitleColor(it) },
                onSubtitleBackgroundChanged = { viewModel.updateSubtitleBackground(it) },
                onBack = onBack,
                onLogout = onLogout
            )
        } else {
            MobileSettingsLayout(
                state = state,
                onTvSortingChanged = { viewModel.updateTvSorting(it) },
                onVodSortingChanged = { viewModel.updateVodSorting(it) },
                onSeriesSortingChanged = { viewModel.updateSeriesSorting(it) },
                onSyncFrequencyChanged = { viewModel.updateSyncFrequency(it) },
                onForceSyncNow = { viewModel.forceSyncNow() },
                onAccentColorChanged = { viewModel.updateAccentColor(it) },
                onSubtitleSizeChanged = { viewModel.updateSubtitleSize(it) },
                onSubtitleColorChanged = { viewModel.updateSubtitleColor(it) },
                onSubtitleBackgroundChanged = { viewModel.updateSubtitleBackground(it) },
                onBack = onBack,
                onLogout = onLogout
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TvSettingsLayout(
    state: SettingsState,
    onTvSortingChanged: (CategorySorting) -> Unit,
    onVodSortingChanged: (CategorySorting) -> Unit,
    onSeriesSortingChanged: (CategorySorting) -> Unit,
    onSyncFrequencyChanged: (SyncFrequency) -> Unit,
    onForceSyncNow: () -> Unit,
    onSubtitleSizeChanged: (SubtitleTextSize) -> Unit,
    onSubtitleColorChanged: (SubtitleTextColor) -> Unit,
    onSubtitleBackgroundChanged: (SubtitleBackground) -> Unit,
    onBack: () -> Unit,
    onLogout: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth(0.85f)
            .fillMaxHeight()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            TvButton(
                onClick = onBack,
                modifier = Modifier.height(40.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Retour",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    TvText("RETOUR", style = TvTheme.typography.labelMedium)
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            TvText(
                text = "PARAMÈTRES DE L'APPLICATION",
                style = TvTheme.typography.headlineMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        }

        TvText(
            text = "Configurez l'ordre d'affichage des catégories par défaut (ordre renvoyé par l'API) ou alphabétique, ainsi que la mise à jour automatique en arrière-plan.",
            color = Color.Gray,
            style = TvTheme.typography.bodyMedium,
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
        )

        // TV Configuration Sections
        TvSettingCard(
            title = "CATÉGORIES LIVE TV",
            currentSorting = state.tvSorting,
            onSortingChanged = onTvSortingChanged
        )

        TvSettingCard(
            title = "CATÉGORIES FILMS VOD",
            currentSorting = state.vodSorting,
            onSortingChanged = onVodSortingChanged
        )

        TvSettingCard(
            title = "CATÉGORIES SÉRIES",
            currentSorting = state.seriesSorting,
            onSortingChanged = onSeriesSortingChanged
        )

        TvSyncFrequencyCard(
            currentFrequency = state.syncFrequency,
            onFrequencyChanged = onSyncFrequencyChanged,
            isSyncingNow = state.isSyncingNow,
            onForceSyncNow = onForceSyncNow
        )

        TvSubtitleStyleCard(
            style = state.subtitleStyle,
            onSizeChanged = onSubtitleSizeChanged,
            onColorChanged = onSubtitleColorChanged,
            onBackgroundChanged = onSubtitleBackgroundChanged
        )

        Spacer(modifier = Modifier.height(16.dp))

        TvButton(
            onClick = onLogout,
            modifier = Modifier.fillMaxWidth().height(44.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                    contentDescription = "Déconnexion",
                    tint = Color(0xFFCF6679),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                TvText("DÉCONNEXION", style = TvTheme.typography.labelLarge, color = Color(0xFFCF6679), fontWeight = FontWeight.Bold)
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TvSettingCard(
    title: String,
    currentSorting: CategorySorting,
    onSortingChanged: (CategorySorting) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Surface3),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TvText(
                text = title,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                style = TvTheme.typography.titleMedium
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                TvSortingOptionButton(
                    label = "ORDRE PAR DÉFAUT (API)",
                    isSelected = currentSorting == CategorySorting.DEFAULT,
                    onClick = { onSortingChanged(CategorySorting.DEFAULT) },
                    modifier = Modifier.weight(1f)
                )

                TvSortingOptionButton(
                    label = "ORDRE ALPHABÉTIQUE (A-Z)",
                    isSelected = currentSorting == CategorySorting.ALPHABETICAL,
                    onClick = { onSortingChanged(CategorySorting.ALPHABETICAL) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TvSortingOptionButton(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(8.dp))
            .onFocusChanged { isFocused = it.isFocused }
            .clickable { onClick() }
            .background(
                when {
                    isSelected -> MaterialTheme.colorScheme.primary
                    isFocused -> Color.LightGray.copy(alpha = 0.2f)
                    else -> Color(0xFF2C2C35)
                }
            )
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = when {
                    isFocused -> Color.White
                    isSelected -> MaterialTheme.colorScheme.primary
                    else -> Color.Transparent
                },
                shape = RoundedCornerShape(8.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        TvText(
            text = label,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            style = TvTheme.typography.labelMedium
        )
    }
}

@Composable
private fun MobileSettingsLayout(
    state: SettingsState,
    onTvSortingChanged: (CategorySorting) -> Unit,
    onVodSortingChanged: (CategorySorting) -> Unit,
    onSeriesSortingChanged: (CategorySorting) -> Unit,
    onSyncFrequencyChanged: (SyncFrequency) -> Unit,
    onForceSyncNow: () -> Unit,
    onAccentColorChanged: (AppAccentColor) -> Unit,
    onSubtitleSizeChanged: (SubtitleTextSize) -> Unit,
    onSubtitleColorChanged: (SubtitleTextColor) -> Unit,
    onSubtitleBackgroundChanged: (SubtitleBackground) -> Unit,
    onBack: () -> Unit,
    onLogout: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
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
                text = "PARAMÈTRES DE L'APPLICATION",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }

        Text(
            text = "Configurez vos préférences de tri et de mise à jour en arrière-plan.",
            color = Color.Gray,
            fontSize = 14.sp,
            textAlign = TextAlign.Start,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        // TV Categories
        MobileSettingCard(
            title = "Télévision en Direct (Live TV)",
            description = "Ordre des catégories pour la télévision en direct.",
            currentSorting = state.tvSorting,
            onSortingChanged = onTvSortingChanged
        )

        // VOD Categories
        MobileSettingCard(
            title = "Films (VOD)",
            description = "Ordre des catégories pour le catalogue de films.",
            currentSorting = state.vodSorting,
            onSortingChanged = onVodSortingChanged
        )

        // Series Categories
        MobileSettingCard(
            title = "Séries",
            description = "Ordre des catégories pour le catalogue de séries.",
            currentSorting = state.seriesSorting,
            onSortingChanged = onSeriesSortingChanged
        )

        // Background Sync Frequency
        MobileSyncFrequencyCard(
            currentFrequency = state.syncFrequency,
            onFrequencyChanged = onSyncFrequencyChanged,
            isSyncingNow = state.isSyncingNow,
            onForceSyncNow = onForceSyncNow
        )

        // COULEUR D'ACCENT (Phase 54)
        MobileAccentColorCard(
            currentColor = state.accentColor,
            onColorChanged = onAccentColorChanged
        )

        // Subtitle appearance
        MobileSubtitleStyleCard(
            style = state.subtitleStyle,
            onSizeChanged = onSubtitleSizeChanged,
            onColorChanged = onSubtitleColorChanged,
            onBackgroundChanged = onSubtitleBackgroundChanged
        )

        // Profiles (Phase 27)
        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onLogout,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFCF6679)),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth().height(44.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                    contentDescription = "Déconnexion",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Se déconnecter",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}

@Composable
private fun MobileSettingCard(
    title: String,
    description: String,
    currentSorting: CategorySorting,
    onSortingChanged: (CategorySorting) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Surface3),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column {
                Text(
                    text = title,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                Text(
                    text = description,
                    color = Color.Gray,
                    fontSize = 12.sp
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MobileSortingOptionButton(
                    label = "Par défaut (API)",
                    isSelected = currentSorting == CategorySorting.DEFAULT,
                    onClick = { onSortingChanged(CategorySorting.DEFAULT) },
                    modifier = Modifier.weight(1f)
                )

                MobileSortingOptionButton(
                    label = "Alphabétique (A-Z)",
                    isSelected = currentSorting == CategorySorting.ALPHABETICAL,
                    onClick = { onSortingChanged(CategorySorting.ALPHABETICAL) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun MobileSortingOptionButton(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primary else Color(0xFF2C2C35),
            contentColor = Color.White
        ),
        shape = RoundedCornerShape(8.dp),
        modifier = modifier.height(38.dp),
        contentPadding = PaddingValues(0.dp)
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TvSyncFrequencyCard(
    currentFrequency: SyncFrequency,
    onFrequencyChanged: (SyncFrequency) -> Unit,
    isSyncingNow: Boolean,
    onForceSyncNow: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Surface3),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TvText(
                text = "FRÉQUENCE DE RAFRAÎCHISSEMENT DU CACHE",
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                style = TvTheme.typography.titleMedium
            )

            TvText(
                text = "Mise à jour automatique en arrière-plan des catégories et listes de chaînes, films et séries.",
                color = Color.Gray,
                style = TvTheme.typography.bodySmall
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    SyncFrequency.DISABLED to "DÉSACTIVÉ",
                    SyncFrequency.DAILY to "QUOTIDIEN (24H)",
                    SyncFrequency.WEEKLY to "HEBDOMADAIRE (7J)",
                    SyncFrequency.MONTHLY to "MENSUEL (30J)"
                ).forEach { (freq, label) ->
                    TvSortingOptionButton(
                        label = label,
                        isSelected = currentFrequency == freq,
                        onClick = { onFrequencyChanged(freq) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            TvButton(
                onClick = onForceSyncNow,
                enabled = !isSyncingNow,
                modifier = Modifier.fillMaxWidth().height(40.dp)
            ) {
                if (isSyncingNow) {
                    CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    TvText("SYNCHRONISATION...", style = TvTheme.typography.labelMedium)
                } else {
                    TvText("FORCER LA MISE À JOUR MAINTENANT", style = TvTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
private fun MobileSyncFrequencyCard(
    currentFrequency: SyncFrequency,
    onFrequencyChanged: (SyncFrequency) -> Unit,
    isSyncingNow: Boolean,
    onForceSyncNow: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Surface3),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column {
                Text(
                    text = "Mise à jour automatique du cache",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                Text(
                    text = "Mise à jour automatique en arrière-plan des catégories et listes de chaînes, films et séries.",
                    color = Color.Gray,
                    fontSize = 12.sp
                )
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MobileSortingOptionButton(
                        label = "Désactivé",
                        isSelected = currentFrequency == SyncFrequency.DISABLED,
                        onClick = { onFrequencyChanged(SyncFrequency.DISABLED) },
                        modifier = Modifier.weight(1f)
                    )

                    MobileSortingOptionButton(
                        label = "Quotidien (24h)",
                        isSelected = currentFrequency == SyncFrequency.DAILY,
                        onClick = { onFrequencyChanged(SyncFrequency.DAILY) },
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MobileSortingOptionButton(
                        label = "Hebdomadaire (7j)",
                        isSelected = currentFrequency == SyncFrequency.WEEKLY,
                        onClick = { onFrequencyChanged(SyncFrequency.WEEKLY) },
                        modifier = Modifier.weight(1f)
                    )

                    MobileSortingOptionButton(
                        label = "Mensuel (30j)",
                        isSelected = currentFrequency == SyncFrequency.MONTHLY,
                        onClick = { onFrequencyChanged(SyncFrequency.MONTHLY) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Button(
                onClick = onForceSyncNow,
                enabled = !isSyncingNow,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth().height(40.dp)
            ) {
                if (isSyncingNow) {
                    CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Synchronisation...", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                } else {
                    Text("Forcer la mise à jour maintenant", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
    }
}

// --- Sous-titres (Phase 26) ---

/** Taille approximative (sp) utilisée uniquement pour l'aperçu dans les
 *  Paramètres ; le rendu réel du player s'appuie sur la fraction Media3. */
private fun SubtitleTextSize.previewSp() = when (this) {
    SubtitleTextSize.SMALL -> 14.sp
    SubtitleTextSize.MEDIUM -> 18.sp
    SubtitleTextSize.LARGE -> 24.sp
}

@Composable
private fun SubtitlePreview(style: SubtitleStyle, modifier: Modifier = Modifier) {
    // Fond sombre imitant une image vidéo, avec le texte d'exemple stylé.
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(72.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF2A2A33)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Exemple de sous-titre",
            color = Color(style.textColor.argb),
            fontSize = style.size.previewSp(),
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .background(Color(style.background.argb))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TvSubtitleStyleCard(
    style: SubtitleStyle,
    onSizeChanged: (SubtitleTextSize) -> Unit,
    onColorChanged: (SubtitleTextColor) -> Unit,
    onBackgroundChanged: (SubtitleBackground) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Surface3),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TvText(
                text = "APPARENCE DES SOUS-TITRES",
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                style = TvTheme.typography.titleMedium
            )
            TvText(
                text = "Taille, couleur du texte et fond appliqués aux lectures de films et d'épisodes.",
                color = Color.Gray,
                style = TvTheme.typography.bodySmall
            )

            SubtitlePreview(style)

            TvText("TAILLE", color = Color.White, style = TvTheme.typography.labelMedium)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SubtitleTextSize.values().forEach { size ->
                    TvSortingOptionButton(
                        label = size.label.uppercase(),
                        isSelected = style.size == size,
                        onClick = { onSizeChanged(size) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            TvText("COULEUR DU TEXTE", color = Color.White, style = TvTheme.typography.labelMedium)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SubtitleTextColor.values().forEach { color ->
                    TvSortingOptionButton(
                        label = color.label.uppercase(),
                        isSelected = style.textColor == color,
                        onClick = { onColorChanged(color) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            TvText("FOND", color = Color.White, style = TvTheme.typography.labelMedium)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SubtitleBackground.values().forEach { bg ->
                    TvSortingOptionButton(
                        label = bg.label.uppercase(),
                        isSelected = style.background == bg,
                        onClick = { onBackgroundChanged(bg) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun MobileSubtitleStyleCard(
    style: SubtitleStyle,
    onSizeChanged: (SubtitleTextSize) -> Unit,
    onColorChanged: (SubtitleTextColor) -> Unit,
    onBackgroundChanged: (SubtitleBackground) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Surface3),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column {
                Text(
                    text = "Apparence des sous-titres",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                Text(
                    text = "Taille, couleur du texte et fond appliqués aux lectures de films et d'épisodes.",
                    color = Color.Gray,
                    fontSize = 12.sp
                )
            }

            SubtitlePreview(style)

            Text("Taille", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SubtitleTextSize.values().forEach { size ->
                    MobileSortingOptionButton(
                        label = size.label,
                        isSelected = style.size == size,
                        onClick = { onSizeChanged(size) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Text("Couleur du texte", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SubtitleTextColor.values().forEach { color ->
                    MobileSortingOptionButton(
                        label = color.label,
                        isSelected = style.textColor == color,
                        onClick = { onColorChanged(color) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Text("Fond", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SubtitleBackground.values().forEach { bg ->
                    MobileSortingOptionButton(
                        label = bg.label,
                        isSelected = style.background == bg,
                        onClick = { onBackgroundChanged(bg) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun MobileAccentColorCard(
    currentColor: AppAccentColor,
    onColorChanged: (AppAccentColor) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Surface3),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column {
                Text(
                    text = "COULEUR D'ACCENT",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    fontFamily = BricolageGrotesque
                )
                Text(
                    text = "Choisissez la couleur d'accent de l'application (boutons, bordures, etc.)",
                    color = Color.Gray,
                    fontSize = 12.sp,
                    fontFamily = HankenGrotesk
                )
            }

            // Two rows of two colors
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    AccentColorOptionButton(
                        label = "Lavande",
                        color = AccentLavande,
                        isSelected = currentColor == AppAccentColor.LAVANDE,
                        onClick = { onColorChanged(AppAccentColor.LAVANDE) },
                        modifier = Modifier.weight(1f)
                    )
                    AccentColorOptionButton(
                        label = "Bleu royal",
                        color = AccentBlue,
                        isSelected = currentColor == AppAccentColor.BLEU_ROYAL,
                        onClick = { onColorChanged(AppAccentColor.BLEU_ROYAL) },
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    AccentColorOptionButton(
                        label = "Sarcelle",
                        color = AccentTeal,
                        isSelected = currentColor == AppAccentColor.SARCELLE,
                        onClick = { onColorChanged(AppAccentColor.SARCELLE) },
                        modifier = Modifier.weight(1f)
                    )
                    AccentColorOptionButton(
                        label = "Ambre",
                        color = AccentAmber,
                        isSelected = currentColor == AppAccentColor.AMBRE,
                        onClick = { onColorChanged(AppAccentColor.AMBRE) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun AccentColorOptionButton(
    label: String,
    color: Color,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isSelected) color else Color(0xFF2C2C35),
            contentColor = if (isSelected) Color.Black else Color.White
        ),
        border = if (isSelected) BorderStroke(1.dp, Color.White.copy(alpha = 0.5f)) else null,
        shape = RoundedCornerShape(8.dp),
        modifier = modifier.height(38.dp),
        contentPadding = PaddingValues(0.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(color, shape = CircleShape)
                    .border(1.dp, if (isSelected) Color.Black else Color.White.copy(alpha = 0.2f), CircleShape)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = HankenGrotesk
            )
        }
    }
}
