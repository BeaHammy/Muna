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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.AppStrings
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
    onShareList: suspend (String) -> Result<String>,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isBusy by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var renameTarget by remember { mutableStateOf<UserListMembership?>(null) }
    var leaveTarget by remember { mutableStateOf<UserListMembership?>(null) }
    var shareTarget by remember { mutableStateOf<UserListMembership?>(null) }
    var shareInviteCode by remember { mutableStateOf<String?>(null) }
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
                    text = AppStrings.get(context, "my_lists"),
                    style = MaterialTheme.typography.headlineMedium,
                    color = OnPrimaryContainer
                )
                Text(
                    text = AppStrings.get(context, "manage_lists_desc"),
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
                                                    text = AppStrings.get(context, "active"),
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                }

                                Text(
                                    text = "${AppStrings.get(context, "code")}${membership.code}",
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
                                            Text(AppStrings.get(context, "open"), fontSize = 15.sp, fontWeight = FontWeight.Bold)
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
                                            text = AppStrings.get(context, "rename"),
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = PrimaryBlue,
                                            maxLines = 1
                                        )
                                    }

                                    if (membership.isOwner) {
                                        OutlinedButton(
                                         onClick = {
                                             scope.launch {
                                                 isBusy = true
                                                 errorMessage = null
                                                 val res = onShareList(membership.code)
                                                 isBusy = false
                                                 if (res.isSuccess) {
                                                     shareInviteCode = res.getOrNull()
                                                     shareTarget = membership
                                                 } else {
                                                     errorMessage = res.exceptionOrNull()?.localizedMessage
                                                         ?: "Nie udało się wygenerować kodu zaproszenia."
                                                 }
                                             }
                                         },
                                         enabled = !isBusy,
                                         shape = RoundedCornerShape(10.dp),
                                         contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                                         modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                                     ) {
                                         Icon(
                                             imageVector = Icons.Default.GroupAdd,
                                             contentDescription = null,
                                             tint = PrimaryBlue,
                                             modifier = Modifier.size(18.dp)
                                         )
                                         Spacer(modifier = Modifier.width(4.dp))
                                         Text(
                                             text = AppStrings.get(context, "share"),
                                             fontSize = 14.sp,
                                             fontWeight = FontWeight.Bold,
                                             color = PrimaryBlue
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
                                                text = AppStrings.get(context, "leave"),
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
                    Text(AppStrings.get(context, "new_list"), fontSize = 18.sp, fontWeight = FontWeight.Bold)
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
                    Text(AppStrings.get(context, "join_with_code"), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }

                TextButton(
                    onClick = onDismiss,
                    enabled = !isBusy,
                    modifier = Modifier
                        .align(Alignment.End)
                        .defaultMinSize(minHeight = 48.dp)
                ) {
                    Text(AppStrings.get(context, "cancel"), fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = InkSecondary)
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
                Text(AppStrings.get(context, "leave_list_title"), fontSize = 21.sp, fontWeight = FontWeight.Bold, color = InkPrimary)
            },
            text = {
                Text(
                    text = "${AppStrings.get(context, "leave_list_confirm")} „${target.customName.ifBlank { target.code }}”?",
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
                    Text(AppStrings.get(context, "leave"), fontSize = 17.sp, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { leaveTarget = null },
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                ) {
                    Text(AppStrings.get(context, "cancel"), fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = InkSecondary)
                }
            }
        )
    }

    if (showCreateDialog) {
        CreateNamedListSubDialog(
            isBusy = isBusy,
            errorMessage = errorMessage,
            onDismiss = { showCreateDialog = false },
            onConfirm = { customName ->
                scope.launch {
                    isBusy = true
                    errorMessage = null
                    val res = onCreateList(customName)
                    isBusy = false
                    if (res.isSuccess) {
                        showCreateDialog = false
                        onDismiss()
                    } else {
                        errorMessage = res.exceptionOrNull()?.localizedMessage
                            ?: "Nie udało się utworzyć nowej listy."
                    }
                }
            }
        )
    }

    if (shareTarget != null && shareInviteCode != null) {
        ShareListSubDialog(
            listCode = shareTarget!!.code,
            listName = shareTarget!!.customName.ifBlank { "Lista ${shareTarget!!.code}" },
            inviteCode = shareInviteCode!!,
            onDismiss = {
                shareTarget = null
                shareInviteCode = null
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
}

@Composable
private fun RenameListSubDialog(
    initialName: String,
    listCode: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    val context = LocalContext.current
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
                Text(AppStrings.get(context, "rename_list_title"), style = MaterialTheme.typography.titleLarge, color = OnPrimaryContainer)
                Text("${AppStrings.get(context, "code")}$listCode", fontSize = 15.sp, color = InkSecondary)

                OutlinedTextField(
                    value = nameInput,
                    onValueChange = {
                        nameInput = it
                        validationError = null
                    },
                    label = { Text(AppStrings.get(context, "list_name_label"), fontSize = 16.sp) },
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
                        Text(AppStrings.get(context, "cancel"), fontSize = 17.sp, color = InkSecondary)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (nameInput.isBlank()) {
                                validationError = AppStrings.get(context, "error_empty_name")
                            } else {
                                onConfirm(nameInput.trim())
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue, contentColor = OnPrimaryWhite),
                        modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                    ) {
                        Text(AppStrings.get(context, "save"), fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun CreateNamedListSubDialog(
    isBusy: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    val context = LocalContext.current
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

    Dialog(onDismissRequest = { if (!isBusy) onDismiss() }) {
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = CardSurface, contentColor = InkPrimary),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(AppStrings.get(context, "new_list"), style = MaterialTheme.typography.titleLarge, color = OnPrimaryContainer)
                Text(
                    text = AppStrings.get(context, "create_list_desc"),
                    fontSize = 15.sp,
                    color = InkSecondary
                )

                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it },
                    label = { Text(AppStrings.get(context, "list_name_hint"), fontSize = 16.sp) },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = InkPrimary),
                    colors = textFieldColors,
                    singleLine = true,
                    enabled = !isBusy,
                    modifier = Modifier.fillMaxWidth()
                )

                if (errorMessage != null) {
                    Text(
                        text = errorMessage,
                        color = DangerRed,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = onDismiss,
                        enabled = !isBusy,
                        modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                    ) {
                        Text(AppStrings.get(context, "cancel"), fontSize = 17.sp, color = InkSecondary)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { onConfirm(nameInput.trim()) },
                        enabled = !isBusy,
                        colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen, contentColor = OnPrimaryWhite),
                        modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                    ) {
                        if (isBusy) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = OnPrimaryWhite,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(AppStrings.get(context, "create"), fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ShareListSubDialog(
    listCode: String,
    listName: String,
    inviteCode: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }

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
                Text(AppStrings.get(context, "share_list"), style = MaterialTheme.typography.titleLarge, color = OnPrimaryContainer)
                Text(
                    text = "$listName (${AppStrings.get(context, "code")}$listCode)",
                    fontSize = 15.sp,
                    color = InkSecondary
                )
                Text(
                    text = AppStrings.get(context, "share_list_desc"),
                    fontSize = 14.sp,
                    color = InkSecondary
                )

                Surface(
                    color = PrimaryBlue.copy(alpha = 0.08f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = AppStrings.get(context, "invite_code_title"),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryBlue
                        )
                        Text(
                            text = inviteCode,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = InkPrimary,
                            letterSpacing = 2.sp
                        )
                    }
                }

                if (copied) {
                    Text(
                        text = AppStrings.get(context, "code_copied"),
                        color = SuccessGreen,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(inviteCode))
                            copied = true
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue, contentColor = OnPrimaryWhite),
                        modifier = Modifier
                            .weight(1f)
                            .defaultMinSize(minHeight = 48.dp)
                    ) {
                        Text(AppStrings.get(context, "copy_code"), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }

                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                    ) {
                        Text(AppStrings.get(context, "cancel"), fontSize = 16.sp, color = InkSecondary)
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
    val context = LocalContext.current
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
                Text(AppStrings.get(context, "join_list"), style = MaterialTheme.typography.titleLarge, color = OnPrimaryContainer)

                OutlinedTextField(
                    value = codeInput,
                    onValueChange = {
                        codeInput = it.filter { ch -> ch.isDigit() }.take(6)
                        validationError = null
                    },
                    label = { Text(AppStrings.get(context, "6_digit_code"), fontSize = 16.sp) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = InkPrimary),
                    colors = textFieldColors,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = customNameInput,
                    onValueChange = { customNameInput = it },
                    label = { Text(AppStrings.get(context, "optional_name"), fontSize = 16.sp) },
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
                        Text(AppStrings.get(context, "cancel"), fontSize = 17.sp, color = InkSecondary)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val cleanCode = codeInput.trim()
                            if (!cleanCode.matches(Regex("^[0-9]{6}$"))) {
                                validationError = AppStrings.get(context, "error_6_digits")
                            } else {
                                onConfirm(cleanCode, customNameInput.trim())
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue, contentColor = OnPrimaryWhite),
                        modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                    ) {
                        Text(AppStrings.get(context, "join"), fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
