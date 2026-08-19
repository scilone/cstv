package com.cstv.app.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.cstv.app.R
import com.cstv.app.domain.model.BlockReason
import com.cstv.app.domain.model.ParentalPinFeedback

/**
 * Message expliquant pourquoi la lecture/le téléchargement est refusé (F44,
 * §7.2/§8.6) : distinct selon que l'œuvre est explicitement trop mature ou
 * simplement non classifiée (règle défensive).
 */
@Composable
fun parentalBlockReasonMessage(reason: BlockReason): String = when (reason) {
    BlockReason.TOO_MATURE -> stringResource(R.string.parental_block_too_mature)
    BlockReason.UNCLASSIFIED -> stringResource(R.string.parental_block_unclassified)
}

/** Retour pauvre en détail sur une tentative de PIN (§8.8 : jamais dire ce qui a précisément échoué). */
@Composable
fun parentalPinFeedbackMessage(feedback: ParentalPinFeedback): String = when (feedback) {
    ParentalPinFeedback.Incorrect -> stringResource(R.string.parental_pin_incorrect)
    is ParentalPinFeedback.Locked -> stringResource(R.string.parental_pin_locked, (feedback.remainingMillis / 1000).coerceAtLeast(1))
}

private fun String.isFourDigitPin() = length == 4 && all { it.isDigit() }

/**
 * Écran de refus + saisie PIN (F44 §7.2/§8.6) : utilisé pour débloquer
 * ponctuellement une lecture (raison affichée) ou confirmer un changement de
 * niveau autorisé (raison omise, `reason = null`).
 */
@Composable
fun ParentalPinEntryDialog(
    reason: BlockReason?,
    feedback: ParentalPinFeedback?,
    onSubmit: (String) -> Unit,
    onDismiss: () -> Unit,
    /** F44 §8.5 : absent depuis l'écran de refus lecture, proposé côté Paramètres/profil. */
    onForgotPin: (() -> Unit)? = null,
) {
    var pin by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.parental_pin_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                reason?.let { Text(parentalBlockReasonMessage(it)) }
                Text(stringResource(R.string.parental_pin_prompt))
                OutlinedTextField(
                    value = pin,
                    onValueChange = { value -> if (value.length <= 4 && value.all { it.isDigit() }) pin = value },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation(),
                    label = { Text(stringResource(R.string.parental_pin_label)) }
                )
                feedback?.let {
                    Text(parentalPinFeedbackMessage(it), color = MaterialTheme.colorScheme.error)
                }
                onForgotPin?.let {
                    TextButton(onClick = it) { Text(stringResource(R.string.parental_pin_forgotten)) }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSubmit(pin); pin = "" }, enabled = pin.isFourDigitPin()) {
                Text(stringResource(R.string.parental_pin_validate))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.profile_dialog_cancel)) }
        }
    )
}

/**
 * Première activation (F44 §8.6) : aucun PIN appareil n'existe encore, un
 * adulte doit en créer un avant d'enregistrer un profil bridé. Double saisie
 * pour éviter une faute de frappe qui verrouillerait un parent hors de son
 * propre réglage.
 */
@Composable
fun ParentalPinCreationDialog(
    onCreate: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var pin by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    val mismatch = confirmation.isNotEmpty() && pin != confirmation

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.parental_pin_creation_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.parental_pin_creation_explanation))
                OutlinedTextField(
                    value = pin,
                    onValueChange = { value -> if (value.length <= 4 && value.all { it.isDigit() }) pin = value },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation(),
                    label = { Text(stringResource(R.string.parental_pin_label)) }
                )
                OutlinedTextField(
                    value = confirmation,
                    onValueChange = { value -> if (value.length <= 4 && value.all { it.isDigit() }) confirmation = value },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation(),
                    label = { Text(stringResource(R.string.parental_pin_confirm_label)) },
                    isError = mismatch
                )
                if (mismatch) {
                    Text(stringResource(R.string.parental_pin_mismatch), color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onCreate(pin) },
                enabled = pin.isFourDigitPin() && pin == confirmation
            ) { Text(stringResource(R.string.parental_pin_validate)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.profile_dialog_cancel)) }
        }
    )
}

/**
 * PIN oublié (F44 §8.5) : réutilise le flow OTP existant en mode
 * réauthentification. Aucune réinitialisation possible hors ligne ou backend
 * indisponible — l'échec laisse simplement le PIN actuel intact.
 */
@Composable
fun ParentalPinResetDialog(
    state: com.cstv.app.presentation.profile.ParentalPinResetUiState,
    onStart: () -> Unit,
    onSubmitOtp: (String) -> Unit,
    onSubmitNewPin: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var otp by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var newPinConfirm by remember { mutableStateOf("") }

    androidx.compose.runtime.LaunchedEffect(Unit) { onStart() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.parental_pin_reset_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when (val step = state.step) {
                    is com.cstv.app.presentation.profile.ParentalPinResetStep.AwaitingOtp -> {
                        Text(stringResource(R.string.parental_pin_reset_otp_prompt, step.email))
                        OutlinedTextField(
                            value = otp,
                            onValueChange = { value -> if (value.length <= 8 && value.all { it.isDigit() }) otp = value },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            label = { Text(stringResource(R.string.parental_pin_reset_otp_label)) }
                        )
                    }
                    com.cstv.app.presentation.profile.ParentalPinResetStep.AwaitingNewPin -> {
                        Text(stringResource(R.string.parental_pin_reset_new_pin_prompt))
                        OutlinedTextField(
                            value = newPin,
                            onValueChange = { value -> if (value.length <= 4 && value.all { it.isDigit() }) newPin = value },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            visualTransformation = PasswordVisualTransformation(),
                            label = { Text(stringResource(R.string.parental_pin_label)) }
                        )
                        OutlinedTextField(
                            value = newPinConfirm,
                            onValueChange = { value -> if (value.length <= 4 && value.all { it.isDigit() }) newPinConfirm = value },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            visualTransformation = PasswordVisualTransformation(),
                            label = { Text(stringResource(R.string.parental_pin_confirm_label)) }
                        )
                    }
                    null -> Text(stringResource(R.string.parental_pin_reset_explanation))
                }
                state.errorRes?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            when (state.step) {
                is com.cstv.app.presentation.profile.ParentalPinResetStep.AwaitingOtp ->
                    TextButton(onClick = { onSubmitOtp(otp); otp = "" }, enabled = otp.isNotBlank() && !state.isSubmitting) {
                        Text(stringResource(R.string.parental_pin_validate))
                    }
                com.cstv.app.presentation.profile.ParentalPinResetStep.AwaitingNewPin ->
                    TextButton(
                        onClick = { onSubmitNewPin(newPin) },
                        enabled = newPin.isFourDigitPin() && newPin == newPinConfirm
                    ) { Text(stringResource(R.string.parental_pin_validate)) }
                null -> TextButton(onClick = onDismiss) { Text(stringResource(R.string.profile_dialog_cancel)) }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.profile_dialog_cancel)) }
        }
    )
}
