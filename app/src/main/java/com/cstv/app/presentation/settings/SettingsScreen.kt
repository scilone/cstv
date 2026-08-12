package com.cstv.app.presentation.settings

import androidx.compose.ui.res.stringResource
import com.cstv.app.R
import com.cstv.app.data.local.storage.SyncFrequency
import com.cstv.app.domain.sync.CloudSyncStatus
import com.cstv.app.presentation.theme.BricolageGrotesque
import com.cstv.app.presentation.theme.HankenGrotesk
import com.cstv.app.presentation.theme.Surface1
import com.cstv.app.presentation.theme.Surface2
import com.cstv.app.presentation.theme.Surface3
import com.cstv.app.domain.model.SubtitleBackground
import com.cstv.app.domain.model.SubtitleStyle
import com.cstv.app.domain.model.SubtitleTextColor
import com.cstv.app.domain.model.SubtitleTextSize
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
import androidx.compose.material.icons.filled.Warning
import androidx.compose.ui.graphics.vector.ImageVector
import coil.compose.AsyncImage
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
import com.cstv.app.presentation.components.tvFocusHighlight
import com.cstv.app.presentation.theme.AccentLavande
import com.cstv.app.presentation.theme.AccentLavandeHover
import com.cstv.app.presentation.theme.OnRatingDislike
import com.cstv.app.presentation.theme.RatingDislike
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme as TvTheme
import androidx.tv.material3.Text as TvText
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    isTv: Boolean,
    onBack: () -> Unit,
    onLogout: () -> Unit,
    onManageCategories: () -> Unit,
    onManageDownloads: () -> Unit = {},
    appUpdateState: com.cstv.app.presentation.update.AppUpdateUiState = com.cstv.app.presentation.update.AppUpdateUiState.Idle,
    installedVersionName: String = "",
    onCheckForUpdate: () -> Unit = {},
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
                onManageCategories = onManageCategories,
                onSyncFrequencyChanged = { viewModel.updateSyncFrequency(it) },
                onForceSyncNow = { viewModel.forceSyncNow() },
                onSubtitleSizeChanged = { viewModel.updateSubtitleSize(it) },
                onSubtitleColorChanged = { viewModel.updateSubtitleColor(it) },
                onSubtitleBackgroundChanged = { viewModel.updateSubtitleBackground(it) },
                onDebugModeChanged = { viewModel.updateDebugModeEnabled(it) },
                onUploadLogs = { viewModel.uploadDiagnosticLogs() },
                cstvEmail = state.cstvEmail,
                onCstvLogout = { viewModel.signOutCstv() },
                onLogout = onLogout,
                appUpdateState = appUpdateState,
                installedVersionName = installedVersionName,
                onCheckForUpdate = onCheckForUpdate
            )
        } else {
            MobileSettingsLayout(
                state = state,
                onManageCategories = onManageCategories,
                onManageDownloads = onManageDownloads,
                onSyncFrequencyChanged = { viewModel.updateSyncFrequency(it) },
                onForceSyncNow = { viewModel.forceSyncNow() },
                onSubtitleSizeChanged = { viewModel.updateSubtitleSize(it) },
                onSubtitleColorChanged = { viewModel.updateSubtitleColor(it) },
                onSubtitleBackgroundChanged = { viewModel.updateSubtitleBackground(it) },
                onDebugModeChanged = { viewModel.updateDebugModeEnabled(it) },
                onUploadLogs = { viewModel.uploadDiagnosticLogs() },
                cstvEmail = state.cstvEmail,
                onCstvLogout = { viewModel.signOutCstv() },
                onBack = onBack,
                onLogout = onLogout,
                appUpdateState = appUpdateState,
                installedVersionName = installedVersionName,
                onCheckForUpdate = onCheckForUpdate
            )
        }

        if (state.isUploadingLogs || state.uploadedLogsUrl != null || state.uploadLogsError != null) {
            DiagnosticLogsDialog(
                isUploading = state.isUploadingLogs,
                uploadedUrl = state.uploadedLogsUrl,
                error = state.uploadLogsError,
                onDismiss = { viewModel.clearUploadStatus() }
            )
        }
    }
}

/**
 * Action de paramètres TV unifiée (F32) : `Surface3` au repos, liseré
 * `tvFocusHighlight` au focus. Porteur unique du langage visuel des actions
 * de paramètres — toute évolution future du style se fait ici.
 */
@Composable
private fun TvSettingsActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    loading: Boolean = false,
    containerColor: Color = Surface3,
    contentColor: Color = Color.White,
    focusBorderColor: Color = AccentLavandeHover,
    onFocusChanged: ((Boolean) -> Unit)? = null
) {
    var isFocused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(8.dp)

    Box(
        modifier = modifier
            .height(44.dp)
            .clip(shape)
            .background(if (enabled) containerColor else containerColor.copy(alpha = 0.5f))
            .tvFocusHighlight(focused = isFocused, shape = shape, color = focusBorderColor, strokeWidth = 2.dp)
            .onFocusChanged { isFocused = it.isFocused; onFocusChanged?.invoke(it.isFocused) }
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (loading) {
                CircularProgressIndicator(
                    color = contentColor,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
            } else if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (enabled) contentColor else contentColor.copy(alpha = 0.5f),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            TvText(
                text = text,
                color = if (enabled) contentColor else contentColor.copy(alpha = 0.5f),
                fontWeight = FontWeight.Bold,
                style = TvTheme.typography.labelMedium
            )
        }
    }
}

/** Variante destructive de [TvSettingsActionButton] : aplat rouge (`RatingDislike`), contenu foncé contrasté. */
@Composable
private fun TvSettingsDestructiveButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    TvSettingsActionButton(
        text = text,
        onClick = onClick,
        modifier = modifier,
        icon = Icons.AutoMirrored.Filled.ExitToApp,
        containerColor = RatingDislike,
        contentColor = OnRatingDislike,
        focusBorderColor = Color.White
    )
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TvSettingsLayout(
    state: SettingsState,
    onManageCategories: () -> Unit,
    onSyncFrequencyChanged: (SyncFrequency) -> Unit,
    onForceSyncNow: () -> Unit,
    onSubtitleSizeChanged: (SubtitleTextSize) -> Unit,
    onSubtitleColorChanged: (SubtitleTextColor) -> Unit,
    onSubtitleBackgroundChanged: (SubtitleBackground) -> Unit,
    onDebugModeChanged: (Boolean) -> Unit,
    onUploadLogs: () -> Unit,
    cstvEmail: String?,
    onCstvLogout: () -> Unit,
    onLogout: () -> Unit,
    appUpdateState: com.cstv.app.presentation.update.AppUpdateUiState,
    installedVersionName: String,
    onCheckForUpdate: () -> Unit
) {
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxWidth(0.85f)
            .fillMaxHeight()
            .padding(24.dp)
            .verticalScroll(scrollState),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        TvText(
            text = "PARAMÈTRES DE L'APPLICATION",
            style = TvTheme.typography.headlineMedium,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
        )

        // Gestion des catégories (Phase 58). Premier contrôle focalisable de
        // l'écran : le ramener en tête de liste dès qu'il reprend le focus
        // (remontée au D-pad depuis une section plus basse) garde le titre
        // visible, sinon le défilement implicite de Compose s'arrête dès que
        // ce bouton est visible, sans remonter jusqu'en haut de la colonne.
        TvManageCategoriesCard(
            onManageCategories = onManageCategories,
            onFocusChanged = { focused ->
                if (focused) coroutineScope.launch { scrollState.animateScrollTo(0) }
            }
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

        TvDiagnosticCard(
            debugModeEnabled = state.debugModeEnabled,
            onDebugModeChanged = onDebugModeChanged,
            onUploadLogs = onUploadLogs
        )

        TvCheckUpdateCard(
            appUpdateState = appUpdateState,
            installedVersionName = installedVersionName,
            onCheckForUpdate = onCheckForUpdate
        )

        cstvEmail?.let { email ->
            TvText(
                text = stringResource(cloudSyncStatusStringRes(state.cloudSyncStatus)),
                style = TvTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.fillMaxWidth()
            )
            TvSettingsActionButton(
                text = stringResource(R.string.cstv_logout, email),
                onClick = onCstvLogout,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        TvSettingsDestructiveButton(
            text = stringResource(R.string.settings_logout),
            onClick = onLogout,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TvManageCategoriesCard(
    onManageCategories: () -> Unit,
    onFocusChanged: ((Boolean) -> Unit)? = null
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
                text = "GESTION DES CATÉGORIES",
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                style = TvTheme.typography.titleMedium
            )
            TvText(
                text = "Masquez ou réordonnez les catégories Live TV, Films et Séries pour le profil actif.",
                color = Color.Gray,
                style = TvTheme.typography.bodySmall
            )
            TvSettingsActionButton(
                text = stringResource(R.string.settings_manage_categories_tv),
                onClick = onManageCategories,
                modifier = Modifier.fillMaxWidth(),
                onFocusChanged = onFocusChanged
            )
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
    onManageCategories: () -> Unit,
    onManageDownloads: () -> Unit,
    onSyncFrequencyChanged: (SyncFrequency) -> Unit,
    onForceSyncNow: () -> Unit,
    onSubtitleSizeChanged: (SubtitleTextSize) -> Unit,
    onSubtitleColorChanged: (SubtitleTextColor) -> Unit,
    onSubtitleBackgroundChanged: (SubtitleBackground) -> Unit,
    onDebugModeChanged: (Boolean) -> Unit,
    onUploadLogs: () -> Unit,
    cstvEmail: String?,
    onCstvLogout: () -> Unit,
    onBack: () -> Unit,
    onLogout: () -> Unit,
    appUpdateState: com.cstv.app.presentation.update.AppUpdateUiState,
    installedVersionName: String,
    onCheckForUpdate: () -> Unit
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
            text = "Gérez les catégories affichées et la mise à jour en arrière-plan.",
            color = Color.Gray,
            fontSize = 14.sp,
            textAlign = TextAlign.Start,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Gestion des catégories (Phase 58)
        MobileManageCategoriesCard(onManageCategories = onManageCategories)

        // Téléchargements hors-ligne (feature #15)
        MobileManageDownloadsCard(onManageDownloads = onManageDownloads)

        // Background Sync Frequency
        MobileSyncFrequencyCard(
            currentFrequency = state.syncFrequency,
            onFrequencyChanged = onSyncFrequencyChanged,
            isSyncingNow = state.isSyncingNow,
            onForceSyncNow = onForceSyncNow
        )

        // Subtitle appearance
        MobileSubtitleStyleCard(
            style = state.subtitleStyle,
            onSizeChanged = onSubtitleSizeChanged,
            onColorChanged = onSubtitleColorChanged,
            onBackgroundChanged = onSubtitleBackgroundChanged
        )

        MobileDiagnosticCard(
            debugModeEnabled = state.debugModeEnabled,
            onDebugModeChanged = onDebugModeChanged,
            onUploadLogs = onUploadLogs
        )

        MobileCheckUpdateCard(
            appUpdateState = appUpdateState,
            installedVersionName = installedVersionName,
            onCheckForUpdate = onCheckForUpdate
        )

        cstvEmail?.let { email ->
            Text(
                text = stringResource(cloudSyncStatusStringRes(state.cloudSyncStatus)),
                color = Color.Gray,
                fontSize = 12.sp,
                textAlign = TextAlign.Start,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedButton(onClick = onCstvLogout, modifier = Modifier.fillMaxWidth().height(44.dp)) {
                Text(stringResource(R.string.cstv_logout, email))
            }
        }

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
private fun MobileManageCategoriesCard(onManageCategories: () -> Unit) {
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
                    text = "Gestion des catégories",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                Text(
                    text = "Masquez ou réordonnez les catégories Live TV, Films et Séries pour le profil actif.",
                    color = Color.Gray,
                    fontSize = 12.sp
                )
            }

            Button(
                onClick = onManageCategories,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth().height(40.dp)
            ) {
                Text(stringResource(R.string.settings_manage_categories_mobile), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
    }
}

@Composable
private fun MobileManageDownloadsCard(onManageDownloads: () -> Unit) {
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
                    text = stringResource(R.string.downloads_settings_card_title),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                Text(
                    text = stringResource(R.string.downloads_settings_card_subtitle),
                    color = Color.Gray,
                    fontSize = 12.sp
                )
            }

            Button(
                onClick = onManageDownloads,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth().height(40.dp)
            ) {
                Text(stringResource(R.string.downloads_settings_card_button), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
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

            TvSettingsActionButton(
                text = stringResource(
                    if (isSyncingNow) R.string.settings_sync_in_progress else R.string.settings_force_sync_now
                ),
                onClick = onForceSyncNow,
                enabled = !isSyncingNow,
                loading = isSyncingNow,
                containerColor = AccentLavande,
                focusBorderColor = Color.White,
                modifier = Modifier.fillMaxWidth()
            )
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
                    Text(stringResource(R.string.settings_sync_in_progress_mobile), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                } else {
                    Text(stringResource(R.string.settings_force_sync_now_mobile), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
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

            TvText(stringResource(R.string.settings_subtitle_size_tv), color = Color.White, style = TvTheme.typography.labelMedium)
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

            TvText(stringResource(R.string.settings_subtitle_color_tv), color = Color.White, style = TvTheme.typography.labelMedium)
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

            TvText(stringResource(R.string.settings_subtitle_background_tv), color = Color.White, style = TvTheme.typography.labelMedium)
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

            Text(stringResource(R.string.settings_subtitle_size_mobile), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
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

            Text(stringResource(R.string.settings_subtitle_color_mobile), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
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

            Text(stringResource(R.string.settings_subtitle_background_mobile), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
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

// --- Diagnostic & Logs (T6) ---

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TvDiagnosticCard(
    debugModeEnabled: Boolean,
    onDebugModeChanged: (Boolean) -> Unit,
    onUploadLogs: () -> Unit
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
                text = "DIAGNOSTIC & LOGS D'ACTIVITÉ",
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                style = TvTheme.typography.titleMedium
            )
            TvText(
                text = "Activez le mode debug pour enregistrer l'activité de l'application et capturer les crashs. Vous pourrez ensuite générer un rapport sous forme de QR Code.",
                color = Color.Gray,
                style = TvTheme.typography.bodySmall
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TvSortingOptionButton(
                    label = "MODE DEBUG ACTIF",
                    isSelected = debugModeEnabled,
                    onClick = { onDebugModeChanged(true) },
                    modifier = Modifier.weight(1f)
                )
                TvSortingOptionButton(
                    label = "MODE DEBUG DÉSACTIVÉ",
                    isSelected = !debugModeEnabled,
                    onClick = { onDebugModeChanged(false) },
                    modifier = Modifier.weight(1f)
                )
            }

            TvSettingsActionButton(
                text = "EXTRAIRE LES LOGS DE DIAGNOSTIC",
                onClick = onUploadLogs,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun MobileDiagnosticCard(
    debugModeEnabled: Boolean,
    onDebugModeChanged: (Boolean) -> Unit,
    onUploadLogs: () -> Unit
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
                    text = "Diagnostic & Logs d'activité",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                Text(
                    text = "Activez le mode debug pour enregistrer l'activité de l'application et capturer les crashs. Vous pourrez ensuite générer un rapport.",
                    color = Color.Gray,
                    fontSize = 12.sp
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MobileSortingOptionButton(
                    label = "Mode Debug Actif",
                    isSelected = debugModeEnabled,
                    onClick = { onDebugModeChanged(true) },
                    modifier = Modifier.weight(1f)
                )
                MobileSortingOptionButton(
                    label = "Mode Debug Désactivé",
                    isSelected = !debugModeEnabled,
                    onClick = { onDebugModeChanged(false) },
                    modifier = Modifier.weight(1f)
                )
            }

            Button(
                onClick = onUploadLogs,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth().height(40.dp)
            ) {
                Text("EXTRAIRE LES LOGS DE DIAGNOSTIC", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
    }
}

// --- Recherche manuelle de mise à jour (F35) ---

/** T10 (F34) : état de synchronisation cloud en langage non technique. */
@androidx.annotation.StringRes
private fun cloudSyncStatusStringRes(status: CloudSyncStatus): Int = when (status) {
    is CloudSyncStatus.Idle -> R.string.cstv_sync_idle
    is CloudSyncStatus.Pending -> R.string.cstv_sync_pending
    is CloudSyncStatus.Incompatible -> R.string.cstv_sync_incompatible
    is CloudSyncStatus.Failed -> if (status.code == "PAYLOAD_TOO_LARGE") R.string.cstv_sync_too_large else R.string.cstv_sync_failed
}

@Composable
private fun updateCheckSubtitle(state: com.cstv.app.presentation.update.AppUpdateUiState, installedVersionName: String): String =
    when (state) {
        is com.cstv.app.presentation.update.AppUpdateUiState.UpToDate -> stringResource(R.string.settings_check_update_up_to_date)
        is com.cstv.app.presentation.update.AppUpdateUiState.Error ->
            if (state.release == null) stringResource(R.string.update_error_check)
            else stringResource(R.string.settings_check_update_subtitle, installedVersionName)
        else -> stringResource(R.string.settings_check_update_subtitle, installedVersionName)
    }

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TvCheckUpdateCard(
    appUpdateState: com.cstv.app.presentation.update.AppUpdateUiState,
    installedVersionName: String,
    onCheckForUpdate: () -> Unit
) {
    val isChecking = appUpdateState is com.cstv.app.presentation.update.AppUpdateUiState.Checking
    // F35-R5 : carte et action alignées sur §4.8 (Surface2 / AccentLavande),
    // pas le Surface3 générique des autres cartes de cet écran.
    Card(
        colors = CardDefaults.cardColors(containerColor = Surface2),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TvText(
                text = "MISE À JOUR",
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                style = TvTheme.typography.titleMedium
            )
            TvText(
                text = updateCheckSubtitle(appUpdateState, installedVersionName),
                color = Color.Gray,
                style = TvTheme.typography.bodySmall
            )
            TvSettingsActionButton(
                text = stringResource(R.string.settings_check_update),
                onClick = onCheckForUpdate,
                loading = isChecking,
                enabled = !isChecking,
                containerColor = AccentLavande,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun MobileCheckUpdateCard(
    appUpdateState: com.cstv.app.presentation.update.AppUpdateUiState,
    installedVersionName: String,
    onCheckForUpdate: () -> Unit
) {
    val isChecking = appUpdateState is com.cstv.app.presentation.update.AppUpdateUiState.Checking
    // F35-R5 : Surface2 (comme la variante TV ci-dessus), pas Surface3.
    Card(
        colors = CardDefaults.cardColors(containerColor = Surface2),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column {
                Text(
                    text = stringResource(R.string.settings_check_update),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                Text(
                    text = updateCheckSubtitle(appUpdateState, installedVersionName),
                    color = Color.Gray,
                    fontSize = 12.sp
                )
            }
            Button(
                onClick = onCheckForUpdate,
                enabled = !isChecking,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth().height(40.dp)
            ) {
                if (isChecking) {
                    CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                } else {
                    Text(stringResource(R.string.settings_check_update), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun DiagnosticLogsDialog(
    isUploading: Boolean,
    uploadedUrl: String?,
    error: String?,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!isUploading) onDismiss() },
        title = {
            Text(
                text = when {
                    isUploading -> "Téléversement en cours..."
                    error != null -> "Échec de l'envoi"
                    else -> "Téléversement réussi !"
                },
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (isUploading) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(48.dp)
                    )
                    Text(
                        text = "Veuillez patienter pendant le téléversement sécurisé de vos logs anonymisés vers paste.rs...",
                        color = Color.Gray,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                } else if (error != null) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Erreur",
                        tint = Color(0xFFCF6679),
                        modifier = Modifier.size(48.dp)
                    )
                    Text(
                        text = error,
                        color = Color.White,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                } else if (uploadedUrl != null) {
                    Text(
                        text = "Scannez le QR Code ci-dessous avec votre smartphone pour accéder à vos logs de diagnostic ou copiez l'adresse :",
                        color = Color.LightGray,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                    
                    Text(
                        text = uploadedUrl,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.background(Color.Black.copy(alpha = 0.3f)).padding(8.dp)
                    )

                    val qrCodeUrl = "https://chart.googleapis.com/chart?chs=250x250&cht=qr&chl=$uploadedUrl"
                    AsyncImage(
                        model = qrCodeUrl,
                        contentDescription = "QR Code pour flasher les logs",
                        modifier = Modifier
                            .size(200.dp)
                            .background(Color.White)
                            .padding(8.dp)
                    )
                }
            }
        },
        confirmButton = {
            if (!isUploading) {
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Fermer", color = Color.White)
                }
            }
        },
        containerColor = Color(0xFF1E1E24),
        shape = RoundedCornerShape(12.dp)
    )
}
