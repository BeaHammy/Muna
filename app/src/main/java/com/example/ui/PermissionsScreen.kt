package com.example.ui

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.ui.theme.*

object PermissionsHelper {
    fun hasNotifications(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun hasBatteryOptimizationIgnored(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun hasExactAlarm(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            am.canScheduleExactAlarms()
        } else {
            true
        }
    }

    fun hasFullScreenIntent(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.canUseFullScreenIntent()
        } else {
            true
        }
    }

    fun hasEssentialPermissions(context: Context): Boolean {
        return hasNotifications(context) &&
            hasBatteryOptimizationIgnored(context) &&
            hasExactAlarm(context) &&
            hasFullScreenIntent(context)
    }
}

@Composable
fun PermissionsScreen(onPermissionsGranted: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var notificationsGranted by remember { mutableStateOf(PermissionsHelper.hasNotifications(context)) }
    var batteryOptIgnored by remember { mutableStateOf(PermissionsHelper.hasBatteryOptimizationIgnored(context)) }
    var exactAlarmGranted by remember { mutableStateOf(PermissionsHelper.hasExactAlarm(context)) }
    var fullScreenGranted by remember { mutableStateOf(PermissionsHelper.hasFullScreenIntent(context)) }

    val notificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        notificationsGranted = granted
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                notificationsGranted = PermissionsHelper.hasNotifications(context)
                batteryOptIgnored = PermissionsHelper.hasBatteryOptimizationIgnored(context)
                exactAlarmGranted = PermissionsHelper.hasExactAlarm(context)
                fullScreenGranted = PermissionsHelper.hasFullScreenIntent(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !notificationsGranted) {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MineCardBg)
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
                    text = "Konfiguracja Uprawnień",
                    style = MaterialTheme.typography.headlineMedium,
                    color = OnPrimaryContainer,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Aby powiadomienia i alarmowe połączenia głosowe działały niezawodnie (także przy wygaszonym ekranie), włącz poniższe uprawnienia.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = InkSecondary,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(24.dp))

                PermissionRow(
                    title = "Powiadomienia",
                    granted = notificationsGranted,
                    onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            notificationsGranted = true
                        }
                    }
                )

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    Spacer(modifier = Modifier.height(14.dp))
                    PermissionRow(
                        title = "Dokładne alarmy i przypomnienia",
                        granted = exactAlarmGranted,
                        onClick = {
                            try {
                                val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                }
                                context.startActivity(intent)
                            } catch (_: Exception) {
                            }
                        }
                    )
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    Spacer(modifier = Modifier.height(14.dp))
                    PermissionRow(
                        title = "Pełnoekranowe połączenia na blokadzie",
                        granted = fullScreenGranted,
                        onClick = {
                            try {
                                val intent = Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                }
                                context.startActivity(intent)
                            } catch (_: Exception) {
                            }
                        }
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                PermissionRow(
                    title = "Praca w tle (bez oszczędzania baterii)",
                    granted = batteryOptIgnored,
                    onClick = {
                        try {
                            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                data = Uri.parse("package:${context.packageName}")
                            }
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                            context.startActivity(intent)
                        }
                    }
                )

                Spacer(modifier = Modifier.height(28.dp))

                Button(
                    onClick = onPermissionsGranted,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(58.dp)
                        .testTag("permissions_continue_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SuccessGreen,
                        contentColor = OnPrimaryWhite
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("DALEJ", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun PermissionRow(title: String, granted: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp)
        ) {
            Icon(
                imageVector = if (granted) Icons.Default.CheckCircle else Icons.Default.Warning,
                contentDescription = if (granted) "Włączone" else "Wymagane",
                tint = if (granted) SuccessGreen else PartnerBadgeBg,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = title,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = InkPrimary
            )
        }
        Button(
            onClick = onClick,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (granted) InkSecondary else PrimaryBlue,
                contentColor = OnPrimaryWhite
            ),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.defaultMinSize(minWidth = 84.dp, minHeight = 48.dp)
        ) {
            Text(
                text = if (granted) "OK" else "Włącz",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
