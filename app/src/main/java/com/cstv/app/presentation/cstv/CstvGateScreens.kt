package com.cstv.app.presentation.cstv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import com.cstv.app.R
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cstv.app.domain.model.*
import com.cstv.app.presentation.theme.Surface1

@Composable
fun CstvGateScreen(viewModel: CstvAuthViewModel, state: CstvSessionState, isTv: Boolean, modifier: Modifier = Modifier) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    Box(modifier.fillMaxSize().background(Surface1), contentAlignment = Alignment.Center) {
        when (state) {
            CstvSessionState.Unknown -> CircularProgressIndicator()
            is CstvSessionState.Blocked -> CstvBlockedScreen(state.reason, isTv, viewModel::retry)
            is CstvSessionState.SignedOut -> if (ui.otpRequested) CstvOtpScreen(ui, isTv, viewModel::verifyOtp, viewModel::resetOtp) else CstvEmailScreen(ui, state.reason, isTv, viewModel::updateEmail, viewModel::requestOtp)
            else -> CircularProgressIndicator()
        }
    }
}

@Composable fun CstvEmailScreen(state: CstvAuthUiState, reason: SignedOutReason, isTv: Boolean, onEmail: (String) -> Unit, onSubmit: () -> Unit) = CstvCard(stringResource(R.string.cstv_title)) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(isTv) { if (isTv) focusRequester.requestFocus() }
    if (reason == SignedOutReason.TokenExpired) Text(stringResource(R.string.cstv_session_expired))
    if (reason == SignedOutReason.SessionRejected) Text(stringResource(R.string.cstv_session_rejected))
    OutlinedTextField(state.email, onEmail, label = { Text(stringResource(R.string.cstv_email)) }, singleLine = true, keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Email), modifier = Modifier.focusRequester(focusRequester))
    Button(onClick = onSubmit, enabled = !state.loading) { Text(if (state.loading) stringResource(R.string.cstv_sending) else stringResource(R.string.cstv_request_otp)) }
    state.messageRes?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
}
@Composable fun CstvOtpScreen(state: CstvAuthUiState, isTv: Boolean, onSubmit: (String) -> Unit, onBack: () -> Unit) { var code by remember { mutableStateOf("") }; val focusRequester = remember { FocusRequester() }; LaunchedEffect(isTv) { if (isTv) focusRequester.requestFocus() }; CstvCard(stringResource(R.string.cstv_otp_title)) {
    Text(stringResource(R.string.cstv_otp_sent, state.email))
    OutlinedTextField(code, { code = it.filter(Char::isDigit).take(6) }, label = { Text(stringResource(R.string.cstv_otp_code)) }, singleLine = true, keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.focusRequester(focusRequester))
    Button(onClick = { onSubmit(code) }, enabled = !state.loading) { Text(if (state.loading) stringResource(R.string.cstv_verifying) else stringResource(R.string.cstv_verify)) }
    TextButton(onClick = onBack) { Text(stringResource(R.string.cstv_change_email)) }
    state.messageRes?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
} }
@Composable fun CstvBlockedScreen(reason: CstvBlockedReason, isTv: Boolean, onRetry: () -> Unit) = CstvCard(stringResource(R.string.cstv_title)) { val focusRequester = remember { FocusRequester() }; LaunchedEffect(isTv) { if (isTv) focusRequester.requestFocus() }; Text(stringResource(if (reason == CstvBlockedReason.Disabled) R.string.cstv_account_disabled else R.string.cstv_account_expired)); Button(onClick = onRetry, modifier = Modifier.focusRequester(focusRequester)) { Text(stringResource(R.string.cstv_retry)) } }
@Composable private fun CstvCard(title: String, content: @Composable ColumnScope.() -> Unit) = Card { Column(Modifier.padding(24.dp).widthIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) { Text(title, style = MaterialTheme.typography.headlineSmall); content() } }
