package com.cstv.app.presentation.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.ExperimentalTvMaterial3Api
import com.cstv.app.R
import com.cstv.app.domain.model.AppUpdateRelease
import com.cstv.app.presentation.theme.AccentLavande
import com.cstv.app.presentation.theme.Surface2
import com.cstv.app.presentation.theme.Surface3

/**
 * Dialogue partagé mobile/TV (F35, §3.4/§3.5), paramétré par [isTv] — même
 * modèle que `SettingsScreen`/`CstvGateScreen`. Une variante **obligatoire**
 * (`required = true`) n'est ni annulable au retour, ni au clic extérieur, ni
 * pendant un téléchargement en cours (F35-R3) : Annuler/Fermer y sont
 * remplacés par [onQuit] dans **tous** les états.
 *
 * F35-R5 : la branche TV utilise de vrais composants `androidx.tv.material3`
 * (focus D-pad natif) et pose un focus initial déterministe sur l'action
 * primaire de **chaque** état, pas seulement `Available`.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun AppUpdateDialog(
    state: AppUpdateUiState,
    isTv: Boolean,
    installedVersionName: String,
    onInstall: () -> Unit,
    onLater: () -> Unit,
    onIgnore: () -> Unit,
    onCancelDownload: () -> Unit,
    onOpenPermissionSettings: () -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    onQuit: () -> Unit
) {
    val required = state.isRequired()
    // Idle/Checking/UpToDate n'ont pas de représentation dans ce dialogue :
    // Idle et Checking(AUTOMATIC) sont silencieux (RG15), UpToDate et
    // Checking(MANUAL) sont montrés dans les Paramètres (§4.8).
    if (state is AppUpdateUiState.Idle || state is AppUpdateUiState.Checking || state is AppUpdateUiState.UpToDate) return

    Dialog(
        onDismissRequest = { if (!required) onDismiss() },
        properties = DialogProperties(
            dismissOnBackPress = !required,
            dismissOnClickOutside = !required
        )
    ) {
        Surface(
            shape = RoundedCornerShape(if (isTv) 16.dp else 12.dp),
            color = Surface2,
            // B28 : 600 dp sur TV. À 480 dp, la rangée d'actions de l'état
            // `Available` non obligatoire (3 boutons de 160 dp + 2 espacements de
            // 12 dp = 504 dp) dépassait les 424 dp utiles : Row écrasait le
            // dernier bouton aux ~80 dp restants, rendu en pastille tronquée.
            modifier = Modifier.width(if (isTv) 600.dp else 320.dp)
        ) {
            Column(
                modifier = Modifier.padding(if (isTv) 28.dp else 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                when (state) {
                    is AppUpdateUiState.Available -> AvailableContent(
                        release = state.release,
                        required = state.required,
                        installedVersionName = installedVersionName,
                        isTv = isTv,
                        onInstall = onInstall,
                        onLater = onLater,
                        onIgnore = onIgnore,
                        onQuit = onQuit
                    )
                    is AppUpdateUiState.PermissionRequired -> PermissionRequiredContent(
                        required = state.required,
                        isTv = isTv,
                        onOpenSettings = onOpenPermissionSettings,
                        onCancel = onDismiss,
                        onQuit = onQuit
                    )
                    is AppUpdateUiState.PermissionUnavailable -> PermissionUnavailableContent(
                        required = state.required,
                        isTv = isTv,
                        onClose = onDismiss,
                        onQuit = onQuit
                    )
                    is AppUpdateUiState.Downloading -> DownloadingContent(
                        percent = state.percent,
                        required = state.required,
                        isTv = isTv,
                        onCancel = onCancelDownload,
                        onQuit = onQuit
                    )
                    is AppUpdateUiState.ReadyToInstall -> ReadyToInstallContent(isTv = isTv)
                    is AppUpdateUiState.Error -> ErrorContent(
                        kind = state.kind,
                        required = state.required,
                        isTv = isTv,
                        onRetry = onRetry,
                        onClose = onDismiss,
                        onQuit = onQuit
                    )
                    else -> Unit
                }
            }
        }
    }
}

private fun AppUpdateUiState.isRequired(): Boolean = when (this) {
    is AppUpdateUiState.Available -> required
    is AppUpdateUiState.PermissionRequired -> required
    is AppUpdateUiState.PermissionUnavailable -> required
    is AppUpdateUiState.Downloading -> required
    is AppUpdateUiState.ReadyToInstall -> required
    is AppUpdateUiState.Error -> required
    else -> false
}

@Composable
private fun DialogTitle(text: String) = Text(text, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun AvailableContent(
    release: AppUpdateRelease,
    required: Boolean,
    installedVersionName: String,
    isTv: Boolean,
    onInstall: () -> Unit,
    onLater: () -> Unit,
    onIgnore: () -> Unit,
    onQuit: () -> Unit
) {
    val installFocusRequester = rememberInitialFocus(isTv)
    DialogTitle(stringResource(if (required) R.string.update_required_title else R.string.update_available_title))
    Text(
        text = if (required) {
            stringResource(R.string.update_required_message, release.version.displayName)
        } else {
            stringResource(R.string.update_available_message, release.version.displayName, installedVersionName)
        },
        color = Color.LightGray,
        fontSize = 14.sp
    )
    ActionsRow(isTv) {
        PrimaryButton(stringResource(R.string.update_action_install), onInstall, isTv, buttonModifier(isTv, installFocusRequester))
        if (required) {
            SecondaryButton(stringResource(R.string.update_action_quit), onQuit, isTv, buttonModifier(isTv))
        } else {
            SecondaryButton(stringResource(R.string.update_action_later), onLater, isTv, buttonModifier(isTv))
            SecondaryButton(stringResource(R.string.update_action_ignore), onIgnore, isTv, buttonModifier(isTv))
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun PermissionRequiredContent(
    required: Boolean,
    isTv: Boolean,
    onOpenSettings: () -> Unit,
    onCancel: () -> Unit,
    onQuit: () -> Unit
) {
    val focusRequester = rememberInitialFocus(isTv)
    DialogTitle(stringResource(R.string.update_permission_action))
    Text(stringResource(R.string.update_permission_message), color = Color.LightGray, fontSize = 14.sp)
    ActionsRow(isTv) {
        PrimaryButton(stringResource(R.string.update_permission_action), onOpenSettings, isTv, buttonModifier(isTv, focusRequester))
        SecondaryButton(
            stringResource(if (required) R.string.update_action_quit else R.string.update_action_cancel),
            if (required) onQuit else onCancel,
            isTv,
            buttonModifier(isTv)
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun PermissionUnavailableContent(required: Boolean, isTv: Boolean, onClose: () -> Unit, onQuit: () -> Unit) {
    val focusRequester = rememberInitialFocus(isTv)
    DialogTitle(stringResource(R.string.update_available_title))
    Text(stringResource(R.string.update_permission_unavailable), color = Color.LightGray, fontSize = 14.sp)
    ActionsRow(isTv) {
        SecondaryButton(
            stringResource(if (required) R.string.update_action_quit else R.string.update_action_close),
            if (required) onQuit else onClose,
            isTv,
            buttonModifier(isTv, focusRequester)
        )
    }
}

/**
 * F35-R3 : une mise à jour obligatoire reste obligatoire pendant le
 * téléchargement — Annuler disparaît, Quitter (qui ferme l'application, sans
 * jamais annuler la `Job` de téléchargement) prend sa place.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun DownloadingContent(percent: Int, required: Boolean, isTv: Boolean, onCancel: () -> Unit, onQuit: () -> Unit) {
    val focusRequester = rememberInitialFocus(isTv)
    DialogTitle(stringResource(R.string.update_downloading, percent))
    LinearProgressIndicator(
        progress = { percent / 100f },
        modifier = Modifier.fillMaxWidth(),
        color = AccentLavande,
        trackColor = Surface3
    )
    ActionsRow(isTv) {
        if (required) {
            SecondaryButton(stringResource(R.string.update_action_quit), onQuit, isTv, buttonModifier(isTv, focusRequester))
        } else {
            SecondaryButton(stringResource(R.string.update_action_cancel), onCancel, isTv, buttonModifier(isTv, focusRequester))
        }
    }
}

@Composable
private fun ReadyToInstallContent(isTv: Boolean) {
    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        CircularProgressIndicator(color = AccentLavande, modifier = Modifier.height(if (isTv) 28.dp else 20.dp))
        Text(stringResource(R.string.update_action_install), color = Color.White, fontSize = 14.sp)
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ErrorContent(
    kind: AppUpdateErrorKind,
    required: Boolean,
    isTv: Boolean,
    onRetry: () -> Unit,
    onClose: () -> Unit,
    onQuit: () -> Unit
) {
    val focusRequester = rememberInitialFocus(isTv)
    val message = when (kind) {
        AppUpdateErrorKind.CHECK -> stringResource(R.string.update_error_check)
        AppUpdateErrorKind.DOWNLOAD -> stringResource(R.string.update_error_download)
        AppUpdateErrorKind.INSTALL -> stringResource(R.string.update_error_install)
    }
    DialogTitle(message)
    ActionsRow(isTv) {
        PrimaryButton(stringResource(R.string.update_action_retry), onRetry, isTv, buttonModifier(isTv, focusRequester))
        SecondaryButton(
            stringResource(if (required) R.string.update_action_quit else R.string.update_action_close),
            if (required) onQuit else onClose,
            isTv,
            buttonModifier(isTv)
        )
    }
}

/**
 * B28 : sur TV, `FlowRow` et non `Row`. Un `Row` mesure ses enfants sans poids
 * dans la largeur *restante* : trois boutons de 160 dp dans une rangée trop
 * étroite laissaient au dernier les quelques dizaines de dp disponibles, rendu
 * en pastille tronquée. Le `FlowRow` passe à la ligne au lieu d'écraser, ce qui
 * tient aussi si un libellé s'allonge ou si un état gagne une action.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActionsRow(isTv: Boolean, content: @Composable () -> Unit) {
    if (isTv) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) { content() }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
    }
}

/** Focus D-pad déterministe sur l'action principale de l'état courant (F35-R5). */
@Composable
private fun rememberInitialFocus(isTv: Boolean): FocusRequester {
    val focusRequester = remember { FocusRequester() }
    if (isTv) LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }
    return focusRequester
}

private fun buttonModifier(isTv: Boolean, focusRequester: FocusRequester? = null): Modifier {
    val sized = if (isTv) Modifier.width(160.dp) else Modifier.fillMaxWidth()
    return focusRequester?.let { sized.focusRequester(it) } ?: sized
}

/**
 * F35-R5 : de vrais composants `androidx.tv.material3` sur TV (focus/scale
 * D-pad natifs), Material3 mobile inchangé.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun PrimaryButton(text: String, onClick: () -> Unit, isTv: Boolean, modifier: Modifier = Modifier) {
    if (isTv) {
        androidx.tv.material3.Button(
            onClick = onClick,
            colors = androidx.tv.material3.ButtonDefaults.colors(containerColor = AccentLavande, contentColor = Color.White),
            shape = androidx.tv.material3.ButtonDefaults.shape(RoundedCornerShape(8.dp)),
            modifier = modifier.height(44.dp)
        ) {
            androidx.tv.material3.Text(text, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
    } else {
        Button(
            onClick = onClick,
            colors = ButtonDefaults.buttonColors(containerColor = AccentLavande, contentColor = Color.White),
            shape = RoundedCornerShape(8.dp),
            modifier = modifier.height(44.dp)
        ) {
            Text(text, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SecondaryButton(text: String, onClick: () -> Unit, isTv: Boolean, modifier: Modifier = Modifier) {
    if (isTv) {
        androidx.tv.material3.OutlinedButton(
            onClick = onClick,
            shape = androidx.tv.material3.ButtonDefaults.shape(RoundedCornerShape(8.dp)),
            modifier = modifier.height(44.dp)
        ) {
            androidx.tv.material3.Text(text, fontSize = 13.sp)
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            shape = RoundedCornerShape(8.dp),
            modifier = modifier.height(44.dp)
        ) {
            Text(text, fontSize = 13.sp)
        }
    }
}
