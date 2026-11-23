package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.AppStrings
import com.example.data.ListMemberInfo
import com.example.data.UserListMembership
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Date

@Composable
fun ManageListsDialog(
    activeListCode: String,
    memberships: List<UserListMembership>,
    onSelectList: suspend (String) -> Result<Unit>,
    onRenameList: suspend (String, String) -> Result<Unit>,
    onCreateList: suspend (String) -> Result<String>,
    onJoinList: suspend (String, String) -> Result<String>,
    onLeaveList: suspend (String) -> Result<Unit>,
    onCreateInvite: suspend (String) -> Result<Pair<String, Date>>,
    onGetMembers: suspend (String) -> List<ListMemberInfo>,
    onRemoveMember: suspend (String, String) -> Result<Unit>,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isBusy by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var successMessage by remember { mutableStateOf<String?>(null) }

    var renameTarget by remember { mutableStateOf<UserListMembership?>(null) }
    var leaveTarget by remember { mutableStateOf<UserListMembership?>(null) }
    var inviteTargetCode by remember { mutableStateOf<String?>(null) }
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

                if (successMessage != null) {
                    Surface(
                        color = SuccessGreen.copy(alpha = 0.08f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = successMessage!!,
                            color = SuccessGreen,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                                verticalArrangement = Arrangement.spacedBy(10.dp)
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
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
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
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                                            modifier = Modifier.weight(1f).defaultMinSize(minHeight = 44.dp)
                                        ) {
                                            Text(AppStrings.get(context, "open"), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }

                                    OutlinedButton(
                                        onClick = { inviteTargetCode = membership.code },
                                        enabled = !isBusy,
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                                        modifier = Modifier.weight(1f).defaultMinSize(minHeight = 44.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.GroupAdd,
                                            contentDescription = null,
                                            tint = PrimaryBlue,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = AppStrings.get(context, "invite_other"),
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = PrimaryBlue,
                                            maxLines = 1
                                        )
                                    }

                                    OutlinedButton(
                                        onClick = { renameTarget = membership },
                                        enabled = !isBusy,
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                                        modifier = Modifier.defaultMinSize(minHeight = 44.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = null,
                                            tint = PrimaryBlue,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }

                                    if (displayedMemberships.size > 1) {
                                        OutlinedButton(
                                            onClick = { leaveTarget = membership },
                                            enabled = !isBusy,
                                            shape = RoundedCornerShape(10.dp),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                                            modifier = Modifier.defaultMinSize(minHeight = 44.dp)
                                        ) {
                                            Text(
                                                text = AppStrings.get(context, "leave"),
                                                fontSize = 13.sp,
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

    if (inviteTargetCode != null) {
        InviteDialog(
            listCode = inviteTargetCode!!,
            onCreateInvite = onCreateInvite,
            onGetMembers = onGetMembers,
            onRemoveMember = onRemoveMember,
            onDismiss = { inviteTargetCode = null }
        )
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
            onDismiss = {
                if (!isBusy) {
                    showCreateDialog = false
                    errorMessage = null
                }
            },
            onConfirm = { customName ->
                scope.launch {
                    isBusy = true
                    errorMessage = null
                    val res = onCreateList(customName)
                    isBusy = false
                    if (res.isSuccess) {
                        showCreateDialog = false
                        errorMessage = null
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
        JoinInviteSubDialog(
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
                        errorMessage = res.exceptionOrNull()?.localizedMessage
                            ?: "Nie udało się dołączyć do listy."
                    }
                }
            }
        )
    }
}

@Composable
private fun InviteDialog(
    listCode: String,
    onCreateInvite: suspend (String) -> Result<Pair<String, Date>>,
    onGetMembers: suspend (String) -> List<ListMemberInfo>,
    onRemoveMember: suspend (String, String) -> Result<Unit>,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val scope = rememberCoroutineScope()

    var inviteCode by remember { mutableStateOf<String?>(null) }
    var expiresAt by remember { mutableStateOf<Date?>(null) }
    var members by remember { mutableStateOf<List<ListMemberInfo>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var successMsg by remember { mutableStateOf<String?>(null) }

    var remainingSeconds by remember { mutableStateOf(15 * 60) }

    suspend fun loadData() {
        isLoading = true
        errorMsg = null
        val inviteRes = onCreateInvite(listCode)
        if (inviteRes.isSuccess) {
            val pair = inviteRes.getOrThrow()
            inviteCode = pair.first
            expiresAt = pair.second
            remainingSeconds = 15 * 60
        } else {
            errorMsg = inviteRes.exceptionOrNull()?.localizedMessage ?: "Nie udało się wygenerować kodu."
        }
        members = onGetMembers(listCode)
        isLoading = false
    }

    LaunchedEffect(listCode) {
        loadData()
    }

    LaunchedEffect(expiresAt) {
        while (remainingSeconds > 0) {
            delay(1000L)
            remainingSeconds--
        }
    }

    val minutes = remainingSeconds / 60
    val seconds = remainingSeconds % 60
    val timeFormatted = String.format("%02d:%02d", minutes, seconds)

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
                Text(
                    text = AppStrings.get(context, "invite_other"),
                    style = MaterialTheme.typography.titleLarge,
                    color = OnPrimaryContainer
                )

                if (isLoading) {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(120.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = PrimaryBlue)
                    }
                } else if (errorMsg != null) {
                    Text(text = errorMsg!!, color = DangerRed, fontSize = 15.sp)
                } else {
                    Text(
                        text = AppStrings.get(context, "invite_code_title"),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = InkSecondary
                    )

                    Surface(
                        color = PrimaryContainer.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, PrimaryBlue),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = inviteCode ?: "--------",
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold,
                                color = OnPrimaryContainer,
                                letterSpacing = 2.sp
                            )
                            Button(
                                onClick = {
                                    inviteCode?.let { code ->
                                        val clip = ClipData.newPlainText("Kod zaproszenia", code)
                                        clipboardManager.setPrimaryClip(clip)
                                        successMsg = AppStrings.get(context, "code_copied")
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue, contentColor = OnPrimaryWhite),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(AppStrings.get(context, "copy_code"), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Text(
                        text = "${AppStrings.get(context, "expires_in")}$timeFormatted",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (remainingSeconds > 60) InkSecondary else DangerRed
                    )

                    if (successMsg != null) {
                        Text(text = successMsg!!, color = SuccessGreen, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    }

                    HorizontalDivider(color = BorderNeutral)

                    Text(
                        text = AppStrings.get(context, "members_title"),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = InkPrimary
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        members.forEach { member ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = member.name.ifBlank { "Użytkownik" },
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = InkPrimary
                                    )
                                    Text(
                                        text = member.role.replaceFirstChar { it.uppercase() },
                                        fontSize = 13.sp,
                                        color = InkSecondary
                                    )
                                }
                                // Allow removing if more than 1 member
                                if (members.size > 1) {
                                    OutlinedButton(
                                        onClick = {
                                            scope.launch {
                                                val res = onRemoveMember(listCode, member.uid)
                                                if (res.isSuccess) {
                                                    members = onGetMembers(listCode)
                                                } else {
                                                    errorMsg = res.exceptionOrNull()?.localizedMessage ?: "Nie udało się usunąć członka."
                                                }
                                            }
                                        },
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = DangerRed),
                                        border = BorderStroke(1.dp, DangerRed),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Icon(Icons.Default.PersonRemove, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(AppStrings.get(context, "remove_access"), fontSize = 13.sp)
                                    }
                                }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                    ) {
                        Text(AppStrings.get(context, "save_close"), fontSize = 17.sp, fontWeight = FontWeight.Bold, color = PrimaryBlue)
                    }
                }
            }
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
                Text(AppStrings.get(context, "new_shared_list"), style = MaterialTheme.typography.titleLarge, color = OnPrimaryContainer)
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
                    Surface(
                        color = DangerRed.copy(alpha = 0.08f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = errorMessage,
                            color = DangerRed,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
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
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = OnPrimaryWhite, strokeWidth = 2.dp)
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
private fun JoinInviteSubDialog(
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
                Text(AppStrings.get(context, "join_invite_title"), style = MaterialTheme.typography.titleLarge, color = OnPrimaryContainer)

                OutlinedTextField(
                    value = codeInput,
                    onValueChange = {
                        codeInput = it.uppercase().take(12)
                        validationError = null
                    },
                    label = { Text(AppStrings.get(context, "invite_code_hint"), fontSize = 16.sp) },
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters,
                        keyboardType = KeyboardType.Text
                    ),
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
                            if (cleanCode.length < 6) {
                                validationError = AppStrings.get(context, "error_invite_code")
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
