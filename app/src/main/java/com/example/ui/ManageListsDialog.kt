package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.UserListMembership
import com.example.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun ManageListsDialog(
    activeListCode: String,
    memberships: List<UserListMembership>,
    onSelectList: suspend (String) -> Result<Unit>,
    onRenameList: suspend (String, String) -> Result<Unit>,
    onCreateList: suspend (String) -> Result<String>,
    onJoinList: suspend (String, String) -> Result<String>,
    onLeaveList: suspend (String) -> Result<Unit>,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var isBusy by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var renameTarget by remember { mutableStateOf<UserListMembership?>(null) }
    var leaveTarget by remember { mutableStateOf<UserListMembership?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var showJoinDialog by remember { mutableStateOf(false) }

    val displayedMemberships = remember(memberships, activeListCode) {
        if (memberships.any { it.code == activeListCode }) {
            memberships
        } else {
            listOf(UserListMembership(code = activeListCode, customName = "Lista $activeListCode")) + memberships
        }
    }

    Dialog(onDismissRequest = { if (!isBusy) onDismiss() }) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = CardSurface,
                contentColor = InkPrimary
            )
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Moje listy",
                    style = MaterialTheme.typography.headlineMedium,
                    color = OnPrimaryContainer
                )
                Text(
                    text = "Zarządzaj swoimi listami.",
                    fontSize = 15.sp,
                    lineHeight = 21.sp,
                    color = InkSecondary
                )

                if (errorMessage != null) {
                    Surface(
                        color = DangerRed.copy(alpha = 0.08f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = errorMessage!!,
                            color = DangerRed,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    displayedMemberships.forEach { membership ->
                        val isActive = membership.code == activeListCode
                        val displayName = membership.customName.ifBlank { "Lista ${membership.code}" }

                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (isActive) MineCardBg else WarmBackground,
                                contentColor = InkPrimary
                            ),
                            border = BorderStroke(
                                width = if (isActive) 2.dp else 1.5.dp,
                                color = if (isActive) PrimaryBlue else BorderNeutral
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = displayName,
                                        fontSize = 19.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = InkPrimary,
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (isActive) {
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Surface(
                                            color = SuccessGreen,
                                            contentColor = OnPrimaryWhite,
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = "AKTYWNA",
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                }

                                Text(
                                    text = "Kod: ${membership.code}",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = InkSecondary
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    if (!isActive) {
                                        Button(
                                            onClick = {
                                                scope.launch {
                                                    isBusy = true
                                                    errorMessage = null
                                                    val res = onSelectList(membership.code)
                                                    isBusy = false
                                                    if (res.isSuccess) {
                                                        onDismiss()
                                                    } else {
                                                        errorMessage = res.exceptionOrNull()?.localizedMessage
                                                            ?: "Nie udało się przełączyć listy."
                                                    }
                                                }
                                            },
                                            enabled = !isBusy,
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = PrimaryBlue,
                                                contentColor = OnPrimaryWhite
                                            ),
                                            shape = RoundedCornerShape(10.dp),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                            modifier = Modifier
                                                .weight(1f)
                                                .defaultMinSize(minHeight = 48.dp)
                                        ) {
                                            Text("Otwórz", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }

                                    OutlinedButton(
                                        onClick = { renameTarget = membership },
                                        enabled = !isBusy,
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .defaultMinSize(minHeight = 48.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = null,
                                            tint = PrimaryBlue,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Zmień nazwę",
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = PrimaryBlue,
                                            maxLines = 1
                                        )
                                    }

                                    if (displayedMemberships.size > 1) {
                                        OutlinedButton(
                                            onClick = { leaveTarget = membership },
                                            enabled = !isBusy,
                                            shape = RoundedCornerShape(10.dp),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                            modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                                        ) {
                                            Text(
                                                text = "Opuść",
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = DangerRed
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = BorderNeutral)

                Button(
                    onClick = { showCreateDialog = true },
                    enabled = !isBusy,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SuccessGreen,
                        contentColor = OnPrimaryWhite
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("create_additional_list_button")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(22.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Nowa lista", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = { showJoinDialog = true },
                    enabled = !isBusy,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PrimaryBlue,
                        contentColor = OnPrimaryWhite
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("join_additional_list_button")
                ) {
                    Icon(Icons.Default.GroupAdd, contentDescription = null, modifier = Modifier.size(22.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Dołącz z kodem", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }

                TextButton(
                    onClick = onDismiss,
                    enabled = !isBusy,
                    modifier = Modifier
                        .align(Alignment.End)
                        .defaultMinSize(minHeight = 48.dp)
                ) {
                    Text("Zamknij", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = InkSecondary)
                }
            }
        }
    }

    if (renameTarget != null) {
        RenameListSubDialog(
            initialName = renameTarget!!.customName,
            listCode = renameTarget!!.code,
            onDismiss = { renameTarget = null },
            onConfirm = { newName ->
                val targetCode = renameTarget!!.code
                scope.launch {
                    isBusy = true
                    val res = onRenameList(targetCode, newName)
                    isBusy = false
                    renameTarget = null
                    if (res.isFailure) {
                        errorMessage = res.exceptionOrNull()?.localizedMessage
                            ?: "Nie udało się zmienić nazwy."
                    }
                }
            }
        )
    }

    if (leaveTarget != null) {
        val target = leaveTarget!!
        AlertDialog(
            onDismissRequest = { leaveTarget = null },
            containerColor = CardSurface,
            titleContentColor = InkPrimary,
            textContentColor = InkPrimary,
            title = {
                Text("Opuszczenie listy", fontSize = 21.sp, fontWeight = FontWeight.Bold, color = InkPrimary)
            },
            text = {
                Text(
                    text = "Czy na pewno chcesz opuścić listę „${target.customName.ifBlank { target.code }}”?",
                    fontSize = 18.sp,
                    color = InkPrimary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            isBusy = true
                            val res = onLeaveList(target.code)
                            isBusy = false
                            leaveTarget = null
                            if (res.isFailure) {
                                errorMessage = res.exceptionOrNull()?.localizedMessage
                                    ?: "Nie udało się opuścić listy."
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DangerRed,
                        contentColor = OnPrimaryWhite
                    ),
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                ) {
                    Text("Opuść listę", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { leaveTarget = null },
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                ) {
                    Text("Anuluj", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = InkSecondary)
                }
            }
        )
    }

    if (showCreateDialog) {
        CreateNamedListSubDialog(
            onDismiss = { showCreateDialog = false },
            onConfirm = { customName ->
                scope.launch {
                    isBusy = true
                    errorMessage = null
                    val res = onCreateList(customName)
                    isBusy = false
                    showCreateDialog = false
                    if (res.isSuccess) {
                        onDismiss()
                    } else {
                        errorMessage = res.exceptionOrNull()?.localizedMessage
                            ?: "Nie udało się utworzyć nowej listy."
                    }
                }
            }
        )
    }

    if (showJoinDialog) {
        JoinNamedListSubDialog(
            onDismiss = { showJoinDialog = false },
            onConfirm = { code, customName ->
                scope.launch {
                    isBusy = true
                    errorMessage = null
                    val res = onJoinList(code, customName)
                    isBusy = false
                    if (res.isSuccess) {
                        showJoinDialog = false
                        onDismiss()
                    } else {
                        showJoinDialog = false
                        errorMessage = res.exceptionOrNull()?.localizedMessage
                            ?: "Nie udało się dołączyć do listy."
                    }
                }
            }
        )
    }
}

@Composable
private fun RenameListSubDialog(
    initialName: String,
    listCode: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var nameInput by remember { mutableStateOf(initialName) }
    var validationError by remember { mutableStateOf<String?>(null) }

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

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = CardSurface, contentColor = InkPrimary),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text("Zmień nazwę listy", style = MaterialTheme.typography.titleLarge, color = OnPrimaryContainer)
                Text("Kod listy: $listCode", fontSize = 15.sp, color = InkSecondary)

                OutlinedTextField(
                    value = nameInput,
                    onValueChange = {
                        nameInput = it
                        validationError = null
                    },
                    label = { Text("Własna nazwa listy", fontSize = 16.sp) },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = InkPrimary),
                    colors = textFieldColors,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (validationError != null) {
                    Text(validationError!!, color = DangerRed, fontSize = 14.sp, textAlign = TextAlign.Start)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                    ) {
                        Text("Anuluj", fontSize = 17.sp, color = InkSecondary)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (nameInput.isBlank()) {
                                validationError = "Wpisz nazwę listy."
                            } else {
                                onConfirm(nameInput.trim())
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue, contentColor = OnPrimaryWhite),
                        modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                    ) {
                        Text("Zapisz", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun CreateNamedListSubDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var nameInput by remember { mutableStateOf("") }

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

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = CardSurface, contentColor = InkPrimary),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text("Nowa wspólna lista", style = MaterialTheme.typography.titleLarge, color = OnPrimaryContainer)
                Text(
                    text = "Wygenerujemy 6-cyfrowy kod, który możesz podać bliskiej osobie.",
                    fontSize = 15.sp,
                    color = InkSecondary
                )

                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it },
                    label = { Text("Nazwa listy (np. Zakupy, Leki)", fontSize = 16.sp) },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = InkPrimary),
                    colors = textFieldColors,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                    ) {
                        Text("Anuluj", fontSize = 17.sp, color = InkSecondary)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { onConfirm(nameInput.trim()) },
                        colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen, contentColor = OnPrimaryWhite),
                        modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                    ) {
                        Text("Utwórz", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun JoinNamedListSubDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    var codeInput by remember { mutableStateOf("") }
    var customNameInput by remember { mutableStateOf("") }
    var validationError by remember { mutableStateOf<String?>(null) }

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

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = CardSurface, contentColor = InkPrimary),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text("Dołącz do listy", style = MaterialTheme.typography.titleLarge, color = OnPrimaryContainer)

                OutlinedTextField(
                    value = codeInput,
                    onValueChange = {
                        codeInput = it.filter { ch -> ch.isDigit() }.take(6)
                        validationError = null
                    },
                    label = { Text("6-cyfrowy kod listy", fontSize = 16.sp) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = InkPrimary),
                    colors = textFieldColors,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = customNameInput,
                    onValueChange = { customNameInput = it },
                    label = { Text("Twoja nazwa tej listy (opcjonalnie)", fontSize = 16.sp) },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = InkPrimary),
                    colors = textFieldColors,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (validationError != null) {
                    Text(validationError!!, color = DangerRed, fontSize = 14.sp)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                    ) {
                        Text("Anuluj", fontSize = 17.sp, color = InkSecondary)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val cleanCode = codeInput.trim()
                            if (!cleanCode.matches(Regex("^[0-9]{6}$"))) {
                                validationError = "Wpisz dokładnie 6 cyfr."
                            } else {
                                onConfirm(cleanCode, customNameInput.trim())
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue, contentColor = OnPrimaryWhite),
                        modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                    ) {
                        Text("Dołącz", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
