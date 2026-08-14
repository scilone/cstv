package com.cstv.app.presentation.bootstrap

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cstv.app.R
import com.cstv.app.domain.sync.SyncFailureKind
import com.cstv.app.presentation.components.rememberTvInitialFocus
import com.cstv.app.presentation.components.tvInitialFocusTarget
import com.cstv.app.presentation.theme.appScreenBackground
import kotlinx.coroutines.delay

private const val LONG_WAIT_MILLIS = 30_000L

/** Écran stateless de préparation du catalogue, partagé entre mobile et TV. */
@Composable
fun CatalogBootstrapScreen(
    state: CatalogBootstrapUiState,
    isTv: Boolean,
    onRetry: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isLongWait by remember { mutableStateOf(false) }
    val indeterminateProgressDescription = stringResource(R.string.catalog_bootstrap_progress_indeterminate)
    LaunchedEffect(state.blocking, state.failure, state.offline) {
        isLongWait = false
        if (state.blocking && state.failure == null && !state.offline) {
            delay(LONG_WAIT_MILLIS)
            isLongWait = true
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .appScreenBackground(isTv),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 420.dp)
                .fillMaxWidth()
                .then(if (isTv) Modifier else Modifier.safeDrawingPadding())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(R.string.catalog_bootstrap_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Text(
                text = stringResource(R.string.catalog_bootstrap_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            if (!state.isResolved) {
                Spacer(Modifier.height(8.dp))
                CircularProgressIndicator()
            } else if (state.stepLabelRes != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(state.stepLabelRes),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center
                )
                LinearProgressIndicator(
                    progress = { state.progressFraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics {
                            contentDescription = "${state.stepIndex}/${state.stepCount}"
                            liveRegion = LiveRegionMode.Polite
                            progressBarRangeInfo = ProgressBarRangeInfo(state.progressFraction, 0f..1f)
                        }
                )
                Text(
                    text = stringResource(R.string.catalog_bootstrap_progress, state.stepIndex, state.stepCount),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.semantics {
                        contentDescription = "${state.stepIndex}/${state.stepCount}"
                        liveRegion = LiveRegionMode.Polite
                    }
                )
            } else if (state.showIndeterminateProgress) {
                Spacer(Modifier.height(8.dp))
                CircularProgressIndicator(
                    modifier = Modifier.semantics {
                        contentDescription = indeterminateProgressDescription
                        liveRegion = LiveRegionMode.Polite
                    }
                )
            }

            if (isLongWait) {
                Text(
                    text = stringResource(R.string.catalog_bootstrap_long_wait),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }

            state.messageRes()?.let { messageRes ->
                Text(
                    text = stringResource(messageRes),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
            }

            if (state.showActions) {
                CatalogBootstrapActions(
                    authFailure = state.failure == SyncFailureKind.AUTH,
                    isTv = isTv,
                    onRetry = onRetry,
                    onLogout = onLogout
                )
            }
        }
    }
}

/**
 * Isole le choix bootstrap/navigation du `MainActivity`, dont le contenu de
 * navigation reste fourni par l'appelant afin de conserver ses états hissés.
 */
@Composable
fun CatalogBootstrapContent(
    showBootstrap: Boolean,
    state: CatalogBootstrapUiState,
    isTv: Boolean,
    onRetry: () -> Unit,
    onLogout: () -> Unit,
    appContent: @Composable () -> Unit
) {
    if (showBootstrap) {
        CatalogBootstrapScreen(state, isTv, onRetry, onLogout)
    } else {
        appContent()
    }
}

@Composable
private fun CatalogBootstrapActions(
    authFailure: Boolean,
    isTv: Boolean,
    onRetry: () -> Unit,
    onLogout: () -> Unit
) {
    val primaryLabel = if (authFailure) R.string.catalog_bootstrap_logout else R.string.catalog_bootstrap_retry
    val primaryAction = if (authFailure) onLogout else onRetry
    val secondaryLabel = if (authFailure) R.string.catalog_bootstrap_retry else R.string.catalog_bootstrap_logout
    val secondaryAction = if (authFailure) onRetry else onLogout
    val initialFocus = rememberTvInitialFocus(
        isTv = isTv,
        ready = true,
        targetKey = primaryLabel
    )

    Spacer(Modifier.height(8.dp))
    Button(
        onClick = primaryAction,
        modifier = Modifier.tvInitialFocusTarget(initialFocus)
    ) {
        Text(stringResource(primaryLabel))
    }
    TextButton(onClick = secondaryAction) {
        Text(stringResource(secondaryLabel))
    }
}

private fun CatalogBootstrapUiState.messageRes(): Int? = when {
    offline -> R.string.catalog_bootstrap_network
    failure == null -> null
    failure == SyncFailureKind.AUTH -> R.string.catalog_bootstrap_auth
    failure == SyncFailureKind.PANEL -> R.string.catalog_bootstrap_panel
    failure == SyncFailureKind.PARSE -> R.string.catalog_bootstrap_parse
    failure == SyncFailureKind.STORAGE -> R.string.catalog_bootstrap_storage
    else -> R.string.catalog_bootstrap_unknown
}
