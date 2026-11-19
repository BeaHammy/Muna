package com.example.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.media.RingtoneManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.ChecklistItem
import com.example.data.ChecklistRepository
import com.example.data.UserProfile
import com.example.service.AlertDispatcher
import com.example.ui.theme.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainChecklistScreen(
    userProfile: UserProfile,
    onActiveListChanged: (String) -> Unit,
    onOpenPermissions: () -> Unit,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val repository = remember { ChecklistRepository(context) }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var itemList by remember { mutableStateOf<List<ChecklistItem>>(emptyList()) }
    var streamError by remember { mutableStateOf<String?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showManageListsDialog by remember { mutableStateOf(false) }
    var itemForCallDialog by remember { mutableStateOf<ChecklistItem?>(null) }
    var itemToDelete by remember { mutableStateOf<ChecklistItem?>(null) }
    var inAppReminder by remember { mutableStateOf<Pair<String, String>?>(null) }

    LaunchedEffect(userProfile.uid, userProfile.listCode) {
        repository.ensureMembership(userProfile.listCode)
    }

    val membershipsFlow = remember(userProfile.uid) {
        repository.observeUserMemberships()
            .let { flow ->
                kotlinx.coroutines.flow.flow {
                    try {
                        flow.collect { emit(it) }
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        val firestoreEx = generateSequence<Throwable>(e) { it.cause }
                            .filterIsInstance<com.google.firebase.firestore.FirebaseFirestoreException>()
                            .firstOrNull()
                        if (firestoreEx == null) throw e
                    } catch (_: Exception) {
                    }
                }
            }
    }
    val memberships by membershipsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val activeListName = remember(memberships, userProfile.listCode) {
        memberships.find { it.code == userProfile.listCode }?.customName
            ?.takeIf { it.isNotBlank() }
            ?: "Lista ${userProfile.listCode}"
    }

    // Observe items real-time with safe error handling
    val itemsFlow = remember(userProfile.listCode) {
        repository.observeItems(userProfile.listCode)
            .let { flow ->
                kotlinx.coroutines.flow.flow {
                    try {
                        flow.collect { emit(it) }
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        val firestoreEx = generateSequence<Throwable>(e) { it.cause }
                            .filterIsInstance<com.google.firebase.firestore.FirebaseFirestoreException>()
                            .firstOrNull()
                        if (firestoreEx == null) throw e
                        streamError = firestoreEx.localizedMessage
                    } catch (e: Exception) {
                        streamError = e.localizedMessage
                    }
                }
            }
    }
    val itemsState by itemsFlow.collectAsStateWithLifecycle(initialValue = emptyList())

    LaunchedEffect(itemsState, userProfile.listCode) {
        itemList = itemsState
        AlertDispatcher.processItems(
            context = context,
            currentUid = userProfile.uid,
            listCode = userProfile.listCode,
            items = itemsState,
            onInAppNotification = { item, senderName ->
                inAppReminder = senderName to item.text
                playNotificationChime(context)
            }
        )
    }

    val todayStr = remember { SimpleDateFormat("yyyy-MM-dd", Locale("pl", "PL")).format(Date()) }
    var selectedDate by remember { mutableStateOf(todayStr) }

    val formattedSelectedDateText = remember(selectedDate, todayStr) {
        try {
            val format = SimpleDateFormat("yyyy-MM-dd", Locale("pl", "PL"))
            val dateObj = format.parse(selectedDate) ?: Date()
            val dayFormat = SimpleDateFormat("EEEE, d MMMM", Locale("pl", "PL"))
            val dayStr = dayFormat.format(dateObj)
            if (selectedDate == todayStr) {
                "Dzisiaj ($dayStr)"
            } else {
                dayStr.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale("pl", "PL")) else it.toString() }
            }
        } catch (_: Exception) {
            selectedDate
        }
    }

    fun showMainDatePicker() {
        val cal = Calendar.getInstance()
        try {
            val parts = selectedDate.split("-")
            if (parts.size == 3) {
                cal.set(parts[0].toInt(), parts[1].toInt() - 1, parts[2].toInt())
            }
        } catch (_: Exception) {}

        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                selectedDate = String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, dayOfMonth)
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    val filteredItemList = remember(itemList, selectedDate, todayStr) {
        itemList.filter { item ->
            val itemDate = item.dueDate.takeIf { it.isNotBlank() } ?: todayStr
            itemDate == selectedDate || item.isDaily
        }
    }

    Scaffold(
        containerColor = WarmBackground,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column(
                        modifier = Modifier
                            .clickable { showManageListsDialog = true }
                            .padding(vertical = 4.dp, horizontal = 4.dp)
                            .testTag("header_list_title")
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = activeListName,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = OnPrimaryContainer,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.List,
                                contentDescription = "Zmień listę",
                                tint = PrimaryBlue,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Text(
                            text = "Kod: ${userProfile.listCode} • ${userProfile.name} (dotknij, aby zmienić listę)",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = InkSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                actions = {
                    TextButton(
                        onClick = { showSettingsDialog = true },
                        modifier = Modifier
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Ustawienia",
                            tint = PrimaryBlue,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Ustawienia", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = PrimaryBlue)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = TopBarSurface,
                    titleContentColor = InkPrimary,
                    actionIconContentColor = InkPrimary
                )
            )
        },
        floatingActionButton = {
            Button(
                onClick = { showAddDialog = true },
                modifier = Modifier
                    .navigationBarsPadding()
                    .height(60.dp)
                    .padding(horizontal = 16.dp)
                    .testTag("add_item_fab"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PrimaryBlue,
                    contentColor = OnPrimaryWhite
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp),
                shape = RoundedCornerShape(30.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(28.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("DODAJ ZADANIE", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
        },
        floatingActionButtonPosition = FabPosition.Center
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(WarmBackground)
                .padding(padding)
        ) {
            Surface(
                color = CardSurface,
                contentColor = InkPrimary,
                border = BorderStroke(1.5.dp, BorderNeutral),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "WYBRANA DATA",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = InkSecondary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = formattedSelectedDateText,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = OnPrimaryContainer,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Button(
                            onClick = { showMainDatePicker() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = PrimaryBlue,
                                contentColor = OnPrimaryWhite
                            ),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                            modifier = Modifier
                                .defaultMinSize(minHeight = 48.dp)
                                .testTag("calendar_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.CalendarMonth,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Kalendarz", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    if (selectedDate != todayStr) {
                        Button(
                            onClick = { selectedDate = todayStr },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = SuccessGreen,
                                contentColor = OnPrimaryWhite
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("return_to_today_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.WbSunny,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("WRÓĆ DO DZISIAJ", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (streamError != null && filteredItemList.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "Błąd synchronizacji listy:\n$streamError",
                            fontSize = 18.sp,
                            color = DangerRed,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(32.dp)
                        )
                    }
                } else if (filteredItemList.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "Brak zadań na wybraną datę.\nDodaj pierwsze zadanie przyciskiem poniżej.",
                            fontSize = 20.sp,
                            lineHeight = 28.sp,
                            fontWeight = FontWeight.Medium,
                            color = InkSecondary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(32.dp)
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(filteredItemList, key = { it.id }) { item ->
                            val isMine = item.senderUid == userProfile.uid
                            val isTriggeredByMe = (item.alertByUid.ifBlank { item.senderUid }) == userProfile.uid
                            ChecklistItemCard(
                                item = item,
                                isMine = isMine,
                                isTriggeredByMe = isTriggeredByMe,
                                onToggleComplete = {
                                    scope.launch {
                                        val newStatus = if (item.status == "completed") "pending" else "completed"
                                        repository.updateItemStatus(userProfile.listCode, item.id, newStatus)
                                    }
                                },
                                onSendNotification = {
                                    scope.launch {
                                        val timeStr = SimpleDateFormat("HH:mm:ss", Locale("pl", "PL")).format(Date())
                                        val res = repository.triggerItemAlert(
                                            listCode = userProfile.listCode,
                                            itemId = item.id,
                                            deliveryMode = 1L,
                                            status = "notified",
                                            scheduledTime = 0L,
                                            alertByName = userProfile.name,
                                            ackTime = timeStr
                                        )
                                        if (res.isSuccess) {
                                            snackbarHostState.showSnackbar("Wysłano powiadomienie do bliskiej osoby")
                                        }
                                    }
                                },
                                onCallClick = { itemForCallDialog = item },
                                onCancelCall = {
                                    scope.launch {
                                        repository.updateItemStatus(userProfile.listCode, item.id, "cancelled")
                                    }
                                },
                                onDeleteClick = { itemToDelete = item }
                            )
                        }
                        item {
                            Spacer(modifier = Modifier.height(88.dp))
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddItemDialog(
            listCode = userProfile.listCode,
            senderName = userProfile.name,
            initialDueDate = selectedDate,
            onDismiss = { showAddDialog = false },
            onAdd = { newItem ->
                scope.launch {
                    val result = repository.addItem(newItem)
                    if (result.isFailure) {
                        snackbarHostState.showSnackbar("Nie udało się dodać zadania")
                    }
                    showAddDialog = false
                }
            }
        )
    }

    if (showManageListsDialog) {
        ManageListsDialog(
            activeListCode = userProfile.listCode,
            memberships = memberships,
            onSelectList = { code ->
                val res = repository.setActiveList(code)
                if (res.isSuccess) {
                    onActiveListChanged(code)
                    scope.launch {
                        snackbarHostState.showSnackbar("Przełączono na listę $code")
                    }
                }
                res
            },
            onRenameList = { code, newName ->
                val res = repository.renameListMembership(code, newName)
                if (res.isSuccess) {
                    scope.launch {
                        snackbarHostState.showSnackbar("Zmieniono nazwę listy")
                    }
                }
                res
            },
            onCreateList = { customName ->
                val res = repository.createNewNamedListAndSwitch(customName)
                res.onSuccess { newCode ->
                    onActiveListChanged(newCode)
                    scope.launch {
                        snackbarHostState.showSnackbar("Utworzono nową listę (kod: $newCode)")
                    }
                }
                res
            },
            onJoinList = { code, customName ->
                val res = repository.joinNamedListAndSwitch(code, customName)
                res.onSuccess { joinedCode ->
                    onActiveListChanged(joinedCode)
                    scope.launch {
                        snackbarHostState.showSnackbar("Dołączono do listy $joinedCode")
                    }
                }
                res
            },
            onLeaveList = { code ->
                val nextCode = memberships.firstOrNull { it.code != code }?.code
                if (nextCode == null) {
                    Result.failure(IllegalStateException("Musisz należeć do co najmniej jednej listy."))
                } else {
                    val res = repository.leaveList(code, nextCode)
                    if (res.isSuccess) {
                        onActiveListChanged(nextCode)
                        scope.launch {
                            snackbarHostState.showSnackbar("Opuszczono listę $code")
                        }
                    }
                    res
                }
            },
            onDismiss = { showManageListsDialog = false }
        )
    }

    if (showSettingsDialog) {
        SettingsDialog(
            listCode = userProfile.listCode,
            listName = activeListName,
            onOpenPermissions = {
                showSettingsDialog = false
                onOpenPermissions()
            },
            onLogout = {
                showSettingsDialog = false
                onLogout()
            },
            onDismiss = { showSettingsDialog = false }
        )
    }

    if (itemForCallDialog != null) {
        CallTimingDialog(
            item = itemForCallDialog!!,
            onDismiss = { itemForCallDialog = null },
            onCallNow = { item ->
                scope.launch {
                    val res = repository.triggerItemAlert(
                        listCode = userProfile.listCode,
                        itemId = item.id,
                        deliveryMode = 2L,
                        status = "ringing",
                        scheduledTime = 0L,
                        alertByName = userProfile.name
                    )
                    itemForCallDialog = null
                    if (res.isSuccess) {
                        snackbarHostState.showSnackbar("Uruchomiono dzwonienie na telefonie bliskiej osoby")
                    }
                }
            },
            onCallScheduled = { item, scheduledMs ->
                scope.launch {
                    val res = repository.triggerItemAlert(
                        listCode = userProfile.listCode,
                        itemId = item.id,
                        deliveryMode = 2L,
                        status = "scheduled",
                        scheduledTime = scheduledMs,
                        alertByName = userProfile.name
                    )
                    itemForCallDialog = null
                    if (res.isSuccess) {
                        val timeStr = SimpleDateFormat("HH:mm", Locale("pl", "PL")).format(Date(scheduledMs))
                        snackbarHostState.showSnackbar("Zaplanowano połączenie na godzinę $timeStr")
                    }
                }
            }
        )
    }

    if (itemToDelete != null) {
        AlertDialog(
            onDismissRequest = { itemToDelete = null },
            containerColor = CardSurface,
            titleContentColor = InkPrimary,
            textContentColor = InkPrimary,
            title = {
                Text(
                    text = "Potwierdzenie usunięcia",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = InkPrimary
                )
            },
            text = {
                Text(
                    text = "Czy na pewno usunąć ten punkt?",
                    fontSize = 20.sp,
                    color = InkPrimary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            repository.deleteItem(userProfile.listCode, itemToDelete!!.id)
                            itemToDelete = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DangerRed,
                        contentColor = OnPrimaryWhite
                    ),
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                ) {
                    Text("Usuń", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { itemToDelete = null },
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                ) {
                    Text("Anuluj", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = InkSecondary)
                }
            }
        )
    }

    if (inAppReminder != null) {
        val (senderName, reminderText) = inAppReminder!!
        AlertDialog(
            onDismissRequest = { inAppReminder = null },
            containerColor = WarningSurface,
            titleContentColor = WarningInk,
            textContentColor = InkPrimary,
            title = {
                Text(
                    text = "Przypomnienie od: $senderName",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = WarningInk
                )
            },
            text = {
                Text(
                    text = reminderText,
                    fontSize = 22.sp,
                    lineHeight = 30.sp,
                    fontWeight = FontWeight.Bold,
                    color = InkPrimary
                )
            },
            confirmButton = {
                Button(
                    onClick = { inAppReminder = null },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PrimaryBlue,
                        contentColor = OnPrimaryWhite
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                ) {
                    Text("OK, ROZUMIEM", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}

@Composable
fun CallTimingDialog(
    item: ChecklistItem,
    onDismiss: () -> Unit,
    onCallNow: (ChecklistItem) -> Unit,
    onCallScheduled: (ChecklistItem, Long) -> Unit
) {
    val context = LocalContext.current
    var showTimePicker by remember { mutableStateOf(false) }

    LaunchedEffect(showTimePicker) {
        if (showTimePicker) {
            val cal = Calendar.getInstance()
            val dialog = TimePickerDialog(
                context,
                { _, hour, minute ->
                    showTimePicker = false
                    val target = Calendar.getInstance().apply {
                        set(Calendar.HOUR_OF_DAY, hour)
                        set(Calendar.MINUTE, minute)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    if (target.timeInMillis <= System.currentTimeMillis()) {
                        target.add(Calendar.DAY_OF_YEAR, 1)
                    }
                    onCallScheduled(item, target.timeInMillis)
                },
                cal.get(Calendar.HOUR_OF_DAY),
                cal.get(Calendar.MINUTE),
                true
            )
            dialog.setOnDismissListener { showTimePicker = false }
            dialog.show()
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = CardSurface,
                contentColor = InkPrimary
            )
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Zadzwoń z przypomnieniem",
                    style = MaterialTheme.typography.titleLarge,
                    color = OnPrimaryContainer
                )
                Text(
                    text = item.text,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = InkPrimary
                )

                Button(
                    onClick = { onCallNow(item) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SuccessGreen,
                        contentColor = OnPrimaryWhite
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .testTag("call_now_button")
                ) {
                    Icon(Icons.Default.Call, contentDescription = null, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Zadzwoń TERAZ", fontSize = 19.sp, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = { showTimePicker = true },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PartnerBadgeBg,
                        contentColor = OnPrimaryWhite
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .testTag("call_scheduled_button")
                ) {
                    Icon(Icons.Default.AccessTime, contentDescription = null, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("O konkretnej godzinie", fontSize = 19.sp, fontWeight = FontWeight.Bold)
                }

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .align(Alignment.End)
                        .defaultMinSize(minHeight = 48.dp)
                ) {
                    Text("Anuluj", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = InkSecondary)
                }
            }
        }
    }
}

@Composable
fun ChecklistItemCard(
    item: ChecklistItem,
    isMine: Boolean,
    isTriggeredByMe: Boolean,
    onToggleComplete: () -> Unit,
    onSendNotification: () -> Unit,
    onCallClick: () -> Unit,
    onCancelCall: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val cardBg = if (isMine) MineCardBg else PartnerCardBg
    val borderColor = if (isMine) MineCardBorder else PartnerCardBorder
    val badgeBg = if (isMine) MineBadgeBg else PartnerBadgeBg
    val headerBadge = if (isMine) "MOJE" else "OD: ${item.senderName.uppercase()}"

    val timeFormat = SimpleDateFormat("HH:mm", Locale("pl", "PL"))
    val scheduledStr = if (item.scheduledTime > 0) timeFormat.format(Date(item.scheduledTime)) else ""
    val createdStr = item.createdAt?.toDate()?.let { timeFormat.format(it) } ?: ""
    val isCompleted = item.status == "completed"
    val completedTimeStr = item.reminderAcknowledgedTime.takeIf { it.isNotBlank() && isCompleted } 
        ?: item.updatedAt?.toDate()?.let { timeFormat.format(it) } 
        ?: createdStr

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(2.dp, borderColor, RoundedCornerShape(14.dp)),
        colors = CardDefaults.cardColors(
            containerColor = cardBg,
            contentColor = InkPrimary
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = badgeBg,
                    contentColor = OnPrimaryWhite,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = headerBadge,
                        color = OnPrimaryWhite,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    when (item.status) {
                        "completed" -> {
                            Icon(Icons.Default.Check, contentDescription = null, tint = SuccessGreen)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Wykonane", color = SuccessGreen, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                        "notified" -> {
                            Text(
                                text = "Wysłano powiadomienie",
                                color = OnPrimaryContainer,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                        "answered" -> {
                            Text("Odebrano o ${item.reminderAcknowledgedTime}", color = SuccessGreen, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                        "rejected" -> {
                            Text("Odrzucono o ${item.reminderAcknowledgedTime}", color = DangerRed, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                        "pinned" -> {
                            Text("Przypomniano o ${item.reminderAcknowledgedTime}", color = WarningInk, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                        "scheduled" -> {
                            Text("Zaplanowano na $scheduledStr", color = OnPrimaryContainer, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                        "ringing" -> {
                            Text("Dzwonię (co 5 min)...", color = DangerRed, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }

                    Spacer(modifier = Modifier.width(6.dp))
                    TextButton(
                        onClick = onDeleteClick,
                        modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                        colors = ButtonDefaults.textButtonColors(contentColor = DangerRed)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Usuń", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleComplete() },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = isCompleted,
                    onCheckedChange = { onToggleComplete() },
                    colors = CheckboxDefaults.colors(checkedColor = SuccessGreen),
                    modifier = Modifier.testTag("item_checkbox_${item.id}")
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.text,
                        fontSize = 22.sp,
                        lineHeight = 30.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isCompleted) InkSecondary else InkPrimary,
                        style = if (isCompleted) MaterialTheme.typography.bodyLarge.copy(textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough) else MaterialTheme.typography.bodyLarge
                    )
                    if (createdStr.isNotBlank() || item.isDaily || isCompleted) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (isCompleted) {
                                Text(
                                    text = "Wykonano: $completedTimeStr",
                                    fontSize = 13.sp,
                                    color = SuccessGreen,
                                    fontWeight = FontWeight.Medium
                                )
                            } else if (createdStr.isNotBlank()) {
                                Text(
                                    text = "Dodano: $createdStr",
                                    fontSize = 13.sp,
                                    color = InkSecondary,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            if (item.isDaily) {
                                if (createdStr.isNotBlank() || isCompleted) {
                                    Text(text = " • ", fontSize = 13.sp, color = InkSecondary)
                                }
                                Text(
                                    text = "Codzienne",
                                    fontSize = 13.sp,
                                    color = PrimaryBlue,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            // Action row right in the list view: choose Notification or Voice Call (only for user's own tasks / isMine)
            if (isMine && !isCompleted) {
                val isRingingOrScheduled = item.deliveryMode == 2L && (item.status == "ringing" || item.status == "scheduled")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onSendNotification,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .defaultMinSize(minHeight = 52.dp)
                            .testTag("item_notify_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Notifications,
                            contentDescription = null,
                            tint = PrimaryBlue,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Powiadomienie",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryBlue,
                            maxLines = 1
                        )
                    }

                    Button(
                        onClick = onCallClick,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isRingingOrScheduled) DangerRed else PartnerBadgeBg,
                            contentColor = OnPrimaryWhite
                        ),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .defaultMinSize(minHeight = 52.dp)
                            .testTag("item_call_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Call,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isRingingOrScheduled) "Dzwoni..." else "Zadzwoń",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

private fun playNotificationChime(context: Context) {
    try {
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        RingtoneManager.getRingtone(context.applicationContext, uri)?.play()
    } catch (_: Exception) {
    }
}
