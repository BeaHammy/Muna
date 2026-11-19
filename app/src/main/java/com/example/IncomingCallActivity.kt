package com.example

import android.app.KeyguardManager
import android.content.Context
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ChecklistRepository
import com.example.ui.theme.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class IncomingCallActivity : ComponentActivity(), TextToSpeech.OnInitListener {

    private var ringtone: Ringtone? = null
    private var vibrator: Vibrator? = null
    private var tts: TextToSpeech? = null
    private var audioManager: AudioManager? = null
    private var isAnswered by mutableStateOf(false)
    private var isDimmed by mutableStateOf(false)

    private val handler = Handler(Looper.getMainLooper())
    private var ringRunnable: Runnable? = null
    private var repeatRunnable: Runnable? = null

    private lateinit var repository: ChecklistRepository
    private var senderName: String = ""
    private var reminderText: String = ""
    private var listCode: String = ""
    private var itemId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Ensure show on lock screen & turn screen on
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val keyguardManager = getSystemService("keyguard") as KeyguardManager
            keyguardManager.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }

        repository = ChecklistRepository(this)
        senderName = intent.getStringExtra("SENDER_NAME") ?: "Bliska osoba"
        reminderText = intent.getStringExtra("REMINDER_TEXT") ?: "Ważne przypomnienie"
        listCode = intent.getStringExtra("LIST_CODE") ?: ""
        itemId = intent.getStringExtra("ITEM_ID") ?: ""

        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        tts = TextToSpeech(this, this)

        setupRingtoneAndVibration()
        startRingDimCycle()

        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = WarningSurface
                ) {
                    if (!isAnswered) {
                        IncomingCallScreen(
                            senderName = senderName,
                            reminderText = reminderText,
                            isDimmed = isDimmed,
                            onAnswer = { answerCall() },
                            onReject = { rejectCall() }
                        )
                    } else {
                        ActiveCallScreen(
                            senderName = senderName,
                            reminderText = reminderText,
                            onRepeat = { speakReminder() },
                            onDone = { markDone() },
                            onLeave = { leaveOnList() }
                        )
                    }
                }
            }
        }
    }

    private fun setupRingtoneAndVibration() {
        try {
            val alert = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ringtone = RingtoneManager.getRingtone(applicationContext, alert)
            ringtone?.audioAttributes = android.media.AudioAttributes.Builder()
                .setUsage(android.media.AudioAttributes.USAGE_ALARM)
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vm.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun startRingDimCycle() {
        startRinging()

        // After 60 seconds, dim screen and stop ringing
        ringRunnable = Runnable {
            stopRinging()
            isDimmed = true
            window.attributes = window.attributes.apply { screenBrightness = 0.05f }
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

            // After 5 minutes (300,000 ms), repeat ring cycle
            repeatRunnable = Runnable {
                isDimmed = false
                window.attributes = window.attributes.apply { screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL }
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                startRingDimCycle()
            }
            handler.postDelayed(repeatRunnable!!, 300_000L)
        }
        handler.postDelayed(ringRunnable!!, 60_000L)
    }

    private fun startRinging() {
        try {
            ringtone?.play()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 1000, 1000), 0))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(longArrayOf(0, 1000, 1000), 0)
            }
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun stopRinging() {
        try {
            ringtone?.stop()
            vibrator?.cancel()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun cancelAllCallbacks() {
        ringRunnable?.let { handler.removeCallbacks(it) }
        repeatRunnable?.let { handler.removeCallbacks(it) }
        stopRinging()
    }

    private var previousVolume: Int = -1

    private fun answerCall() {
        cancelAllCallbacks()
        com.example.service.NotificationHelper.cancelIncomingCallNotification(this, itemId)
        isAnswered = true
        window.attributes = window.attributes.apply { screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Route to speakerphone and boost volume
        audioManager?.let { am ->
            am.mode = AudioManager.MODE_IN_COMMUNICATION
            am.isSpeakerphoneOn = true
            previousVolume = am.getStreamVolume(AudioManager.STREAM_MUSIC)
            val maxVol = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            am.setStreamVolume(AudioManager.STREAM_MUSIC, (maxVol * 0.9).toInt(), 0)
        }

        val timeStr = SimpleDateFormat("HH:mm", Locale("pl", "PL")).format(Date())
        if (listCode.isNotBlank() && itemId.isNotBlank()) {
            CoroutineScope(Dispatchers.IO).launch {
                repository.updateItemStatus(listCode, itemId, "answered", timeStr)
            }
        }

        handler.postDelayed({ speakReminder() }, 800L)
    }

    private fun rejectCall() {
        cancelAllCallbacks()
        com.example.service.NotificationHelper.cancelIncomingCallNotification(this, itemId)
        val timeStr = SimpleDateFormat("HH:mm", Locale("pl", "PL")).format(Date())
        if (listCode.isNotBlank() && itemId.isNotBlank()) {
            CoroutineScope(Dispatchers.IO).launch {
                repository.updateItemStatus(listCode, itemId, "rejected", timeStr)
            }
        }
        finish()
    }

    private fun speakReminder() {
        val speechRate = com.example.data.AppSettings.getSpeechRate(this)
        tts?.setSpeechRate(speechRate)
        val lang = com.example.data.AppSettings.getLanguage(this)
        val speech = if (lang == "en") {
            "Hello, important reminder from $senderName: $reminderText"
        } else {
            "Cześć, ważne przypomnienie od $senderName: $reminderText"
        }
        tts?.speak(speech, TextToSpeech.QUEUE_FLUSH, null, "call_reminder")
    }

    private fun markDone() {
        if (listCode.isNotBlank() && itemId.isNotBlank()) {
            CoroutineScope(Dispatchers.IO).launch {
                repository.updateItemStatus(listCode, itemId, "completed")
            }
        }
        cleanupAndFinish()
    }

    private fun leaveOnList() {
        val timeStr = SimpleDateFormat("HH:mm", Locale("pl", "PL")).format(Date())
        if (listCode.isNotBlank() && itemId.isNotBlank()) {
            CoroutineScope(Dispatchers.IO).launch {
                repository.updateItemStatus(listCode, itemId, "pinned", timeStr)
            }
        }
        cleanupAndFinish()
    }

    private fun cleanupAndFinish() {
        com.example.service.NotificationHelper.cancelIncomingCallNotification(this, itemId)
        tts?.stop()
        audioManager?.let { am ->
            if (previousVolume >= 0) {
                am.setStreamVolume(AudioManager.STREAM_MUSIC, previousVolume, 0)
            }
            am.isSpeakerphoneOn = false
            am.mode = AudioManager.MODE_NORMAL
        }
        finish()
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val lang = com.example.data.AppSettings.getLanguage(this)
            val locale = if (lang == "en") Locale("en", "US") else Locale("pl", "PL")
            tts?.setLanguage(locale)
            tts?.setSpeechRate(com.example.data.AppSettings.getSpeechRate(this))
        }
    }

    override fun onDestroy() {
        cancelAllCallbacks()
        com.example.service.NotificationHelper.cancelIncomingCallNotification(this, itemId)
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}

@Composable
fun IncomingCallScreen(
    senderName: String,
    reminderText: String,
    isDimmed: Boolean,
    onAnswer: () -> Unit,
    onReject: () -> Unit
) {
    val bgColor = if (isDimmed) DimmedCallBg else WarningSurface
    val borderColor = if (isDimmed) DimmedCallBorder else WarningBorder
    val titleColor = if (isDimmed) DimmedCallInk else WarningInk
    val subTitleColor = if (isDimmed) DimmedCallInk.copy(alpha = 0.85f) else WarningSubInk
    val cardBg = if (isDimmed) DimmedCallCardBg else CardSurface
    val cardInk = if (isDimmed) DimmedCallInk else InkPrimary

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
            .safeDrawingPadding()
            .padding(20.dp)
            .border(4.dp, borderColor, RoundedCornerShape(20.dp))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(modifier = Modifier.height(20.dp))
                Icon(
                    imageVector = Icons.Default.NotificationsActive,
                    contentDescription = "Dzwonek alarmowy",
                    tint = titleColor,
                    modifier = Modifier.size(68.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "POWIADOMIENIE",
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                    color = titleColor,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "od $senderName",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = subTitleColor,
                    textAlign = TextAlign.Center
                )
            }

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = cardBg,
                    contentColor = cardInk
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(
                    text = reminderText,
                    fontSize = 26.sp,
                    lineHeight = 34.sp,
                    fontWeight = FontWeight.Bold,
                    color = cardInk,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    textAlign = TextAlign.Center
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onReject,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DangerRed,
                        contentColor = OnPrimaryWhite
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .height(70.dp)
                        .weight(1f)
                ) {
                    Icon(Icons.Default.CallEnd, contentDescription = "Odrzuć", modifier = Modifier.size(28.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("ODRZUĆ", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = onAnswer,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SuccessGreen,
                        contentColor = OnPrimaryWhite
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .height(70.dp)
                        .weight(1f)
                ) {
                    Icon(Icons.Default.Call, contentDescription = "Odbierz", modifier = Modifier.size(28.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("ODBIERZ", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun ActiveCallScreen(
    senderName: String,
    reminderText: String,
    onRepeat: () -> Unit,
    onDone: () -> Unit,
    onLeave: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TopBarSurface)
            .safeDrawingPadding()
            .padding(20.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(modifier = Modifier.height(20.dp))
                Icon(
                    imageVector = Icons.Default.VolumeUp,
                    contentDescription = "Trwająca rozmowa głosowa",
                    tint = OnPrimaryContainer,
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Rozmowa z: $senderName",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = OnPrimaryContainer,
                    textAlign = TextAlign.Center
                )
            }

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = CardSurface,
                    contentColor = InkPrimary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "TREŚĆ PRZYPOMNIENIA:",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = InkSecondary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = reminderText,
                        fontSize = 28.sp,
                        lineHeight = 36.sp,
                        fontWeight = FontWeight.Bold,
                        color = InkPrimary,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onRepeat,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = InfoTeal,
                        contentColor = OnPrimaryWhite
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(58.dp)
                ) {
                    Text("POWTÓRZ GŁOSEM", fontSize = 19.sp, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = onDone,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SuccessGreen,
                        contentColor = OnPrimaryWhite
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(58.dp)
                ) {
                    Text("ZROBIONE (WYKONANE)", fontSize = 19.sp, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = onLeave,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = LeaveAmberBg,
                        contentColor = InkPrimary
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(58.dp)
                ) {
                    Text("ZOSTAW NA LIŚCIE", fontSize = 19.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
