package com.example.ui

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.example.R
import com.example.data.AppSettings
import com.example.data.ChecklistRepository
import com.example.data.UserProfile
import com.example.ui.theme.*
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlin.random.Random

@Composable
fun AuthScreen(onAuthComplete: (UserProfile) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = CredentialManager.create(context)
    val repository = remember { ChecklistRepository(context) }

    var authMethod by remember { mutableStateOf("google") } // "google" or "email"
    var emailInput by remember { mutableStateOf("") }
    var passwordInput by remember { mutableStateOf("") }
    var isRegisterMode by remember { mutableStateOf(false) }

    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showGoogleFallbackHint by remember { mutableStateOf(false) }
    var signedInUserId by remember { mutableStateOf<String?>(null) }

    var nameInput by remember { mutableStateOf("") }
    var roleInput by remember { mutableStateOf("opiekun") } // "opiekun" or "senior"
    var modeInput by remember { mutableStateOf("create") } // "create" or "join"
    var codeInput by remember { mutableStateOf("") }
    var listNameInput by remember { mutableStateOf("") }

    // Check if already signed in
    LaunchedEffect(Unit) {
        val currentUser = Firebase.auth.currentUser
        if (currentUser != null) {
            signedInUserId = currentUser.uid
            val cached = AppSettings.getCachedProfile(context, currentUser.uid)
            if (cached != null && cached.listCode.isNotBlank()) {
                onAuthComplete(cached)
                return@LaunchedEffect
            }
            val profile = repository.getUserProfile(currentUser.uid)
            if (profile != null && profile.listCode.isNotBlank()) {
                AppSettings.saveCachedProfile(context, profile)
                onAuthComplete(profile)
            } else {
                nameInput = currentUser.displayName ?: currentUser.email?.substringBefore("@") ?: ""
            }
        }
    }

    val textFieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = InkPrimary,
        unfocusedTextColor = InkPrimary,
        focusedLabelColor = PrimaryBlue,
        unfocusedLabelColor = InkSecondary,
        focusedBorderColor = PrimaryBlue,
        unfocusedBorderColor = BorderNeutral,
        cursorColor = PrimaryBlue,
        focusedContainerColor = CardSurface,
        unfocusedContainerColor = CardSurface
    )

    val radioColors = RadioButtonDefaults.colors(
        selectedColor = PrimaryBlue,
        unselectedColor = InkSecondary
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(WarmBackground)
            .safeDrawingPadding()
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 540.dp)
                .wrapContentHeight(),
            colors = CardDefaults.cardColors(
                containerColor = CardSurface,
                contentColor = InkPrimary
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Wspólna Lista",
                    style = MaterialTheme.typography.displaySmall,
                    color = OnPrimaryContainer,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Połącz Opiekuna i Seniora",
                    style = MaterialTheme.typography.bodyMedium,
                    color = InkSecondary,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(24.dp))

                if (signedInUserId == null) {
                    // Method selector tiles
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        AuthMethodTile(
                            title = "Google",
                            subtitle = "Szybko i bez hasła",
                            selected = authMethod == "google",
                            onClick = {
                                authMethod = "google"
                                errorMessage = null
                                showGoogleFallbackHint = false
                            },
                            modifier = Modifier.weight(1f)
                        )
                        AuthMethodTile(
                            title = "E-mail",
                            subtitle = "Tradycyjne hasło",
                            selected = authMethod == "email",
                            onClick = {
                                authMethod = "email"
                                errorMessage = null
                                showGoogleFallbackHint = false
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(modifier = Modifier.height(20.dp))

                    if (authMethod == "google") {
                        Button(
                            onClick = {
                                isLoading = true
                                errorMessage = null
                                onGoogleSignInClicked(
                                    context = context,
                                    credentialManager = credentialManager,
                                    onAuthSuccess = {
                                        isLoading = false
                                        signedInUserId = Firebase.auth.currentUser?.uid
                                    },
                                    onAuthError = { msg ->
                                        isLoading = false
                                        errorMessage = msg
                                    },
                                    onAuthCancelled = {
                                        isLoading = false
                                    },
                                    scope = scope
                                )
                            },
                            enabled = !isLoading,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(58.dp)
                                .testTag("google_sign_in_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = PrimaryBlue,
                                contentColor = OnPrimaryWhite
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = OnPrimaryWhite)
                            } else {
                                Text("Zaloguj przez Google", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    } else {
                        // Email / Password Form
                        OutlinedTextField(
                            value = emailInput,
                            onValueChange = { emailInput = it },
                            label = { Text("Adres e-mail", fontSize = 16.sp) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("email_input"),
                            textStyle = MaterialTheme.typography.bodyMedium.copy(color = InkPrimary),
                            colors = textFieldColors,
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = passwordInput,
                            onValueChange = { passwordInput = it },
                            label = { Text("Hasło (min. 6 znaków)", fontSize = 16.sp) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("password_input"),
                            textStyle = MaterialTheme.typography.bodyMedium.copy(color = InkPrimary),
                            colors = textFieldColors,
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = {
                                if (emailInput.isBlank() || passwordInput.length < 6) {
                                    errorMessage = "Wpisz poprawny e-mail i hasło (min. 6 znaków)."
                                    return@Button
                                }
                                scope.launch {
                                    isLoading = true
                                    errorMessage = null
                                    showGoogleFallbackHint = false
                                    try {
                                        if (isRegisterMode) {
                                            Firebase.auth.createUserWithEmailAndPassword(emailInput.trim(), passwordInput).await()
                                        } else {
                                            Firebase.auth.signInWithEmailAndPassword(emailInput.trim(), passwordInput).await()
                                        }
                                        isLoading = false
                                        signedInUserId = Firebase.auth.currentUser?.uid
                                    } catch (e: FirebaseAuthUserCollisionException) {
                                        isLoading = false
                                        isRegisterMode = false
                                        showGoogleFallbackHint = true
                                        errorMessage = "Konto z tym adresem e-mail już istnieje. Zaloguj się hasłem lub użyj logowania Google."
                                    } catch (e: FirebaseAuthWeakPasswordException) {
                                        isLoading = false
                                        errorMessage = "Hasło jest za słabe. Użyj co najmniej 6 znaków."
                                    } catch (e: FirebaseAuthInvalidCredentialsException) {
                                        isLoading = false
                                        showGoogleFallbackHint = true
                                        errorMessage = "Nieprawidłowy e-mail, hasło lub wygasłe poświadczenie. Zalecamy logowanie przez Google."
                                    } catch (e: FirebaseAuthInvalidUserException) {
                                        isLoading = false
                                        showGoogleFallbackHint = true
                                        errorMessage = "Nie znaleziono konta dla tego adresu e-mail. Utwórz konto lub zaloguj się przez Google."
                                    } catch (e: FirebaseAuthException) {
                                        isLoading = false
                                        showGoogleFallbackHint = true
                                        errorMessage = "Autoryzacja odrzucona (${e.errorCode}): Logowanie przez e-mail może być wyłączone w konsoli Firebase. Użyj logowania przez Google."
                                    } catch (e: Exception) {
                                        isLoading = false
                                        showGoogleFallbackHint = true
                                        val msg = e.message.orEmpty()
                                        errorMessage = if (msg.contains("malformed", ignoreCase = true) || msg.contains("expired", ignoreCase = true) || msg.contains("credential", ignoreCase = true) || msg.contains("recaptcha", ignoreCase = true)) {
                                            "Nieprawidłowe poświadczenie lub błąd Recaptcha. Użyj prostego i niezawodnego logowania przez Google."
                                        } else {
                                            "Błąd autoryzacji: $msg. Najlepiej użyj logowania przez Google."
                                        }
                                    }
                                }
                            },
                            enabled = !isLoading,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(58.dp)
                                .testTag("email_auth_submit_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = PrimaryBlue,
                                contentColor = OnPrimaryWhite
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = OnPrimaryWhite)
                            } else {
                                Text(
                                    text = if (isRegisterMode) "Zarejestruj się" else "Zaloguj się",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        TextButton(
                            onClick = { isRegisterMode = !isRegisterMode },
                            modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                        ) {
                            Text(
                                text = if (isRegisterMode) "Masz już konto? Zaloguj się" else "Nie masz konta? Utwórz własne konto",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = PrimaryBlue
                            )
                        }
                    }
                } else {
                    Text(
                        text = "Zalogowano pomyślnie. Skonfiguruj profil:",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = SuccessGreen,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = { nameInput = it },
                        label = { Text("Twoje imię (np. Mama, Kasia)", fontSize = 16.sp) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("profile_name_input"),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = InkPrimary),
                        colors = textFieldColors,
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    Text("Kim jesteś?", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = InkPrimary)
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .defaultMinSize(minHeight = 48.dp)
                                .clickable { roleInput = "opiekun" }
                                .padding(horizontal = 8.dp)
                        ) {
                            RadioButton(
                                selected = roleInput == "opiekun",
                                onClick = { roleInput = "opiekun" },
                                colors = radioColors
                            )
                            Text("Opiekun", fontSize = 18.sp, color = InkPrimary)
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .defaultMinSize(minHeight = 48.dp)
                                .clickable { roleInput = "senior" }
                                .padding(horizontal = 8.dp)
                        ) {
                            RadioButton(
                                selected = roleInput == "senior",
                                onClick = { roleInput = "senior" },
                                colors = radioColors
                            )
                            Text("Senior", fontSize = 18.sp, color = InkPrimary)
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))

                    Text("Wspólna lista:", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = InkPrimary)
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .defaultMinSize(minHeight = 48.dp)
                                .clickable { modeInput = "create" }
                                .padding(horizontal = 6.dp)
                        ) {
                            RadioButton(
                                selected = modeInput == "create",
                                onClick = { modeInput = "create" },
                                colors = radioColors
                            )
                            Text("Utwórz nową", fontSize = 17.sp, color = InkPrimary)
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .defaultMinSize(minHeight = 48.dp)
                                .clickable { modeInput = "join" }
                                .padding(horizontal = 6.dp)
                        ) {
                            RadioButton(
                                selected = modeInput == "join",
                                onClick = { modeInput = "join" },
                                colors = radioColors
                            )
                            Text("Dołącz z kodem", fontSize = 17.sp, color = InkPrimary)
                        }
                    }

                    if (modeInput == "create") {
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = listNameInput,
                            onValueChange = { listNameInput = it },
                            label = { Text("Nazwa listy (np. Zakupy, Leki Mamy)", fontSize = 16.sp) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("list_name_input"),
                            textStyle = MaterialTheme.typography.bodyMedium.copy(color = InkPrimary),
                            colors = textFieldColors,
                            singleLine = true
                        )
                    } else {
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = codeInput,
                            onValueChange = { codeInput = it.filter { ch -> ch.isDigit() }.take(6) },
                            label = { Text("6-cyfrowy kod listy", fontSize = 16.sp) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("list_code_input"),
                            textStyle = MaterialTheme.typography.bodyMedium.copy(color = InkPrimary),
                            colors = textFieldColors,
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = listNameInput,
                            onValueChange = { listNameInput = it },
                            label = { Text("Własna nazwa tej listy (opcjonalnie)", fontSize = 16.sp) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("list_name_input"),
                            textStyle = MaterialTheme.typography.bodyMedium.copy(color = InkPrimary),
                            colors = textFieldColors,
                            singleLine = true
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = {
                            if (nameInput.isBlank()) {
                                errorMessage = "Wpisz swoje imię."
                                return@Button
                            }
                            scope.launch {
                                isLoading = true
                                errorMessage = null
                                val finalCode = if (modeInput == "create") {
                                    String.format("%06d", Random.nextInt(100_000, 1_000_000))
                                } else {
                                    codeInput.trim()
                                }

                                if (!finalCode.matches(Regex("^[0-9]{6}$"))) {
                                    errorMessage = "Kod listy musi mieć dokładnie 6 cyfr."
                                    isLoading = false
                                    return@launch
                                }

                                val resolvedListName = if (modeInput == "create") {
                                    val custom = listNameInput.trim().take(80).ifBlank { "Wspólna Lista" }
                                    val createResult = repository.createList(finalCode, custom)
                                    if (createResult.isFailure) {
                                        errorMessage = "Nie udało się utworzyć listy: ${createResult.exceptionOrNull()?.localizedMessage ?: "błąd zapisu"}"
                                        isLoading = false
                                        return@launch
                                    }
                                    custom
                                } else {
                                    val exists = repository.checkListExists(finalCode)
                                    if (!exists) {
                                        errorMessage = "Lista o podanym kodzie nie istnieje."
                                        isLoading = false
                                        return@launch
                                    }
                                    val sharedName = repository.getSharedListName(finalCode) ?: "Lista $finalCode"
                                    listNameInput.trim().take(80).ifBlank { sharedName }
                                }

                                val profile = UserProfile(
                                    uid = signedInUserId!!,
                                    name = nameInput.trim(),
                                    listCode = finalCode,
                                    role = roleInput
                                )
                                val saveResult = repository.saveUserProfile(profile)
                                if (saveResult.isSuccess) {
                                    repository.saveListMembership(finalCode, resolvedListName)
                                    AppSettings.saveCachedProfile(context, profile)
                                    isLoading = false
                                    onAuthComplete(profile)
                                } else {
                                    isLoading = false
                                    errorMessage = "Nie udało się zapisać profilu: ${saveResult.exceptionOrNull()?.localizedMessage ?: "brak uprawnień"}"
                                }
                            }
                        },
                        enabled = !isLoading,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(58.dp)
                            .testTag("start_app_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SuccessGreen,
                            contentColor = OnPrimaryWhite
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), color = OnPrimaryWhite)
                        } else {
                            Text("Rozpocznij korzystanie", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Surface(
                        color = DangerRed.copy(alpha = 0.08f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = errorMessage!!,
                                color = DangerRed,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                                textAlign = TextAlign.Center
                            )
                            if (showGoogleFallbackHint && signedInUserId == null) {
                                Button(
                                    onClick = {
                                        authMethod = "google"
                                        errorMessage = null
                                        showGoogleFallbackHint = false
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = PrimaryBlue,
                                        contentColor = OnPrimaryWhite
                                    ),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .defaultMinSize(minHeight = 48.dp)
                                ) {
                                    Text("Przejdź do logowania Google", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AuthMethodTile(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier
            .height(84.dp)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) PrimaryBlue else BorderNeutral,
                shape = RoundedCornerShape(16.dp)
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) PrimaryContainer.copy(alpha = 0.5f) else CardSurface,
            contentColor = InkPrimary
        ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = if (selected) 4.dp else 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = if (selected) OnPrimaryContainer else InkPrimary,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = if (selected) PrimaryBlue else InkSecondary,
                maxLines = 1
            )
        }
    }
}

fun onGoogleSignInClicked(
    context: Context,
    credentialManager: CredentialManager,
    onAuthSuccess: () -> Unit,
    onAuthError: (String) -> Unit,
    onAuthCancelled: () -> Unit = {},
    scope: CoroutineScope
) {
    val clientId = try {
        context.getString(R.string.default_web_client_id)
    } catch (_: Exception) {
        onAuthError("Brak konfiguracji Google Sign-In (default_web_client_id)")
        return
    }
    val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
    val request = GetCredentialRequest.Builder().addCredentialOption(signInOption).build()
    scope.launch {
        try {
            val cred = credentialManager.getCredential(context as Activity, request).credential
            if (cred is androidx.credentials.CustomCredential && cred.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val token = GoogleIdTokenCredential.createFrom(cred.data).idToken
                Firebase.auth.signInWithCredential(GoogleAuthProvider.getCredential(token, null)).await()
                onAuthSuccess()
            } else {
                onAuthError("Nieoczekiwany typ poświadczenia")
            }
        } catch (e: GetCredentialCancellationException) {
            Log.w("Auth", "Google Sign-In cancelled", e)
            onAuthCancelled()
        } catch (e: Exception) {
            Log.e("Auth", "Google Sign-In failed", e)
            onAuthError(e.localizedMessage ?: "Logowanie nie powiodło się")
        }
    }
}
