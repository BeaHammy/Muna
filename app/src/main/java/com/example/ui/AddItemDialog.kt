package com.example.ui

import android.app.DatePickerDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.ChecklistItem
import com.example.ui.theme.*
import java.util.Calendar
import java.util.Locale

@Composable
fun AddItemDialog(
    listCode: String,
    senderName: String,
    initialDueDate: String,
    onDismiss: () -> Unit,
    onAdd: (ChecklistItem) -> Unit
) {
    val context = LocalContext.current
    var textInput by remember { mutableStateOf("") }
    var dueDateInput by remember { mutableStateOf(initialDueDate) }
    var isDailyInput by remember { mutableStateOf(false) }
    var showError by remember { mutableStateOf(false) }

    val formattedDueDateDisplay = remember(dueDateInput) {
        try {
            val parts = dueDateInput.split("-")
            if (parts.size == 3) {
                "${parts[2]}.${parts[1]}.${parts[0]}"
            } else {
                dueDateInput
            }
        } catch (_: Exception) {
            dueDateInput
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

    fun showDatePicker() {
        val cal = Calendar.getInstance()
        try {
            val parts = dueDateInput.split("-")
            if (parts.size == 3) {
                cal.set(parts[0].toInt(), parts[1].toInt() - 1, parts[2].toInt())
            }
        } catch (_: Exception) {}

        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                dueDateInput = String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, dayOfMonth)
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    Dialog(onDismissRequest = onDismiss) {
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
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Nowe zadanie na liście",
                    style = MaterialTheme.typography.titleLarge,
                    color = OnPrimaryContainer
                )

                Text(
                    text = "Wpisz treść zadania. Obie osoby zobaczą je na wspólnej liście.",
                    fontSize = 16.sp,
                    color = InkSecondary
                )

                OutlinedTextField(
                    value = textInput,
                    onValueChange = {
                        textInput = it
                        if (it.isNotBlank()) showError = false
                    },
                    label = { Text("Treść zadania", fontSize = 17.sp) },
                    placeholder = { Text("np. Kupić mleko i leki", fontSize = 17.sp, color = InkSecondary) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("add_item_text_input"),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = InkPrimary),
                    colors = textFieldColors,
                    minLines = 2,
                    maxLines = 4
                )

                OutlinedButton(
                    onClick = { showDatePicker() },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = PrimaryBlue),
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 52.dp)
                ) {
                    Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(22.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Data: $formattedDueDateDisplay",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryBlue
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp)
                        .clickable { isDailyInput = !isDailyInput }
                ) {
                    Checkbox(
                        checked = isDailyInput,
                        onCheckedChange = { isDailyInput = it },
                        colors = CheckboxDefaults.colors(checkedColor = PrimaryBlue)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Codziennie",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = InkPrimary
                    )
                }

                if (showError) {
                    Text(
                        text = "Wpisz treść zadania przed dodaniem.",
                        color = DangerRed,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.defaultMinSize(minHeight = 52.dp)
                    ) {
                        Text("Anuluj", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = InkSecondary)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Button(
                        onClick = {
                            if (textInput.isBlank()) {
                                showError = true
                                return@Button
                            }
                            val item = ChecklistItem(
                                listCode = listCode,
                                text = textInput.trim(),
                                senderName = senderName,
                                deliveryMode = 0L,
                                scheduledTime = 0L,
                                status = "pending",
                                dueDate = dueDateInput,
                                isDaily = isDailyInput
                            )
                            onAdd(item)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PrimaryBlue,
                            contentColor = OnPrimaryWhite
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .defaultMinSize(minWidth = 120.dp, minHeight = 52.dp)
                            .testTag("confirm_add_item_button")
                    ) {
                        Text("Dodaj", fontSize = 19.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
