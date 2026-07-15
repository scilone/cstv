package com.poc.iptvxtream.presentation.login

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button as TvButton
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme as TvTheme
import androidx.tv.material3.Text as TvText
import com.poc.iptvxtream.domain.model.Credentials
import com.poc.iptvxtream.domain.model.ServerAddressParser
import com.poc.iptvxtream.domain.model.UserInfo

@Composable
fun LoginScreen(
    viewModel: LoginViewModel,
    isTv: Boolean,
    onLoginSuccess: (UserInfo) -> Unit,
    modifier: Modifier = Modifier
) {
    val loginState by viewModel.loginState.collectAsStateWithLifecycle()
    val savedCredentials by viewModel.savedCredentials.collectAsStateWithLifecycle()

    // Champ unique "adresse du serveur" (Phase 28), remplaçant les anciens
    // champs host/port séparés. Parsé en host/port juste avant l'appel
    // player_api.php (voir ServerAddressParser) — la logique réseau existante
    // (Credentials.host/port, Credentials.baseUrl) n'est pas modifiée.
    var serverAddress by remember { mutableStateOf("") }
    var serverAddressError by remember { mutableStateOf<String?>(null) }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var rememberMe by remember { mutableStateOf(true) }

    // Pre-populate if saved credentials exist. Reconstitue l'adresse complète
    // à partir du host/port déjà stockés (Phase 1) sans forcer une ressaisie.
    LaunchedEffect(savedCredentials) {
        savedCredentials?.let { creds ->
            serverAddress = ServerAddressParser.buildDisplayAddress(creds.host, creds.port)
            username = creds.username
            password = creds.password
            rememberMe = creds.rememberMe
        }
    }

    LaunchedEffect(loginState) {
        if (loginState is LoginState.Success) {
            onLoginSuccess((loginState as LoginState.Success).userInfo)
            viewModel.resetState()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0F0F13)), // Deep luxury black/blue background
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(if (isTv) 0.5f else 0.88f)
                .verticalScroll(rememberScrollState())
                .padding(vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Header
            Text(
                text = "XTREAM CODES",
                fontSize = if (isTv) 28.sp else 24.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 2.sp
            )
            Text(
                text = "PORTAIL DE CONNEXION",
                fontSize = if (isTv) 14.sp else 12.sp,
                fontWeight = FontWeight.Light,
                color = Color.Gray,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            // Form Inputs
            LoginForm(
                serverAddress = serverAddress,
                onServerAddressChange = {
                    serverAddress = it
                    serverAddressError = null
                },
                serverAddressError = serverAddressError,
                username = username,
                onUsernameChange = { username = it },
                password = password,
                onPasswordChange = { password = it },
                rememberMe = rememberMe,
                onRememberMeChange = { rememberMe = it },
                isTv = isTv,
                onSubmit = {
                    when (val parsed = ServerAddressParser.parse(serverAddress)) {
                        is ServerAddressParser.Result.Error -> {
                            serverAddressError = parsed.message
                        }
                        is ServerAddressParser.Result.Success -> {
                            serverAddressError = null
                            viewModel.login(
                                Credentials(parsed.host, parsed.port, username, password, rememberMe)
                            )
                        }
                    }
                },
                isLoading = loginState is LoginState.Loading
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Error Display
            if (loginState is LoginState.Error) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF3F1616)),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                ) {
                    Text(
                        text = (loginState as LoginState.Error).message,
                        color = Color(0xFFFFB4B4),
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(12.dp)
                    )
                }
            }
        }
    }
}

/**
 * Couleurs de champ garantissant un contraste WCAG AA sur le fond sombre de
 * l'écran de connexion dans tous les états (vide, rempli, focus) — cf.
 * Phase 28 point 1 : les champs remplis utilisaient un fond gris foncé se
 * confondant avec le fond noir, rendant le texte saisi illisible.
 */
@Composable
private fun loginFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
    disabledTextColor = Color.Gray,
    focusedContainerColor = Color(0xFF1E1E24),
    unfocusedContainerColor = Color(0xFF1E1E24),
    disabledContainerColor = Color(0xFF1E1E24),
    focusedBorderColor = MaterialTheme.colorScheme.primary,
    unfocusedBorderColor = Color.LightGray,
    focusedLabelColor = MaterialTheme.colorScheme.primary,
    unfocusedLabelColor = Color.LightGray,
    cursorColor = MaterialTheme.colorScheme.primary,
    focusedLeadingIconColor = MaterialTheme.colorScheme.primary,
    unfocusedLeadingIconColor = Color.LightGray,
    focusedTrailingIconColor = MaterialTheme.colorScheme.primary,
    unfocusedTrailingIconColor = Color.LightGray,
    focusedPlaceholderColor = Color.Gray,
    unfocusedPlaceholderColor = Color.Gray,
    errorContainerColor = Color(0xFF1E1E24),
    errorBorderColor = Color(0xFFFF6B6B),
    errorTextColor = Color.White,
    errorLabelColor = Color(0xFFFF6B6B),
    errorSupportingTextColor = Color(0xFFFF6B6B)
)

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun LoginForm(
    serverAddress: String,
    onServerAddressChange: (String) -> Unit,
    serverAddressError: String?,
    username: String,
    onUsernameChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    rememberMe: Boolean,
    onRememberMeChange: (Boolean) -> Unit,
    isTv: Boolean,
    onSubmit: () -> Unit,
    isLoading: Boolean
) {
    val focusManager = LocalFocusManager.current
    var passwordVisible by remember { mutableStateOf(false) }
    val fieldColors = loginFieldColors()

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        // Adresse du serveur (host + port fusionnés, Phase 28)
        OutlinedTextField(
            value = serverAddress,
            onValueChange = onServerAddressChange,
            label = { Text("Adresse du serveur") },
            placeholder = { Text("http://mondns.com:8080") },
            leadingIcon = { Icon(Icons.Default.Dns, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            singleLine = true,
            enabled = !isLoading,
            isError = serverAddressError != null,
            supportingText = serverAddressError?.let { { Text(it) } },
            colors = fieldColors,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Uri,
                imeAction = ImeAction.Next
            ),
            keyboardActions = KeyboardActions(
                onNext = { focusManager.moveFocus(FocusDirection.Down) }
            ),
            modifier = Modifier.fillMaxWidth()
        )

        // Username
        OutlinedTextField(
            value = username,
            onValueChange = onUsernameChange,
            label = { Text("Nom d'utilisateur") },
            leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            singleLine = true,
            enabled = !isLoading,
            colors = fieldColors,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Next
            ),
            keyboardActions = KeyboardActions(
                onNext = { focusManager.moveFocus(FocusDirection.Down) }
            ),
            modifier = Modifier.fillMaxWidth()
        )

        // Password
        OutlinedTextField(
            value = password,
            onValueChange = onPasswordChange,
            label = { Text("Mot de passe") },
            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            singleLine = true,
            enabled = !isLoading,
            colors = fieldColors,
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = {
                    focusManager.clearFocus()
                    if (serverAddress.isNotBlank() && username.isNotBlank() && password.isNotBlank()) {
                        onSubmit()
                    }
                }
            ),
            trailingIcon = {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Text(
                        text = if (passwordVisible) "Cacher" else "Afficher",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            modifier = Modifier.fillMaxWidth()
        )

        // Remember Me Switch/Checkbox Row
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
        ) {
            Checkbox(
                checked = rememberMe,
                onCheckedChange = onRememberMeChange,
                enabled = !isLoading,
                colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "Se souvenir de moi",
                fontSize = 14.sp,
                color = Color.LightGray
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Adaptive Login Button
        if (isTv) {
            TvButton(
                onClick = onSubmit,
                enabled = !isLoading && serverAddress.isNotBlank() && username.isNotBlank() && password.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    TvText(
                        text = "CONNEXION",
                        fontWeight = FontWeight.Bold,
                        style = TvTheme.typography.labelLarge
                    )
                }
            }
        } else {
            Button(
                onClick = onSubmit,
                enabled = !isLoading && serverAddress.isNotBlank() && username.isNotBlank() && password.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        text = "CONNEXION",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
            }
        }
    }
}
