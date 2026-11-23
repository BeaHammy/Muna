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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.AppStrings
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
                    color = Color(0xFF0F172A)
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
    val context = LocalContext.current
    val bgColor = if (isDimmed) Color(0xFF05050A) else Color(0xFF0F172A)
    val borderColor = if (isDimmed) Color(0xFF334155) else Color(0xFF4F46E5)
    val titleColor = if (isDimmed) Color(0xFF94A3B8) else Color(0xFF818CF8)
    val subTitleColor = if (isDimmed) Color(0xFF64748B) else Color(0xFFE2E8F0)
    val cardBg = Color(0xFF1E293B)
    val cardInk = Color(0xFFF8FAFC)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
            .safeDrawingPadding()
            .padding(24.dp)
            .border(2.dp, borderColor, RoundedCornerShape(28.dp))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(modifier = Modifier.height(16.dp))
                Surface(
                    shape = RoundedCornerShape(50),
                    color = Color(0xFF4F46E5).copy(alpha = 0.25f),
                    modifier = Modifier.size(88.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.NotificationsActive,
                            contentDescription = null,
                            tint = Color(0xFF818CF8),
                            modifier = Modifier.size(44.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = AppStrings.get(context, "notification").uppercase(),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                    color = titleColor,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "${AppStrings.get(context, "reminder_from")} $senderName",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
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
                    .padding(vertical = 12.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = reminderText,
                        fontSize = 26.sp,
                        lineHeight = 36.sp,
                        fontWeight = FontWeight.Bold,
                        color = cardInk,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Button(
                    onClick = onReject,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFEF4444),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier
                        .height(68.dp)
                        .weight(1f)
                ) {
                    Icon(Icons.Default.CallEnd, contentDescription = null, modifier = Modifier.size(26.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(AppStrings.get(context, "reject"), fontSize = 17.sp, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = onAnswer,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF10B981),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier
                        .height(68.dp)
                        .weight(1f)
                ) {
                    Icon(Icons.Default.Call, contentDescription = null, modifier = Modifier.size(26.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(AppStrings.get(context, "answer"), fontSize = 17.sp, fontWeight = FontWeight.Bold)
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
    val context = LocalContext.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A))
            .safeDrawingPadding()
            .padding(24.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(modifier = Modifier.height(16.dp))
                Surface(
                    shape = RoundedCornerShape(50),
                    color = Color(0xFF06B6D4).copy(alpha = 0.25f),
                    modifier = Modifier.size(88.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.VolumeUp,
                            contentDescription = null,
                            tint = Color(0xFF22D3EE),
                            modifier = Modifier.size(44.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "${AppStrings.get(context, "change_list").substringBefore(" ")}: $senderName",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFF8FAFC),
                    textAlign = TextAlign.Center
                )
            }

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF1E293B),
                    contentColor = Color(0xFFF8FAFC)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = AppStrings.get(context, "call_reminder").uppercase(),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp,
                        color = Color(0xFF94A3B8)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = reminderText,
                        fontSize = 26.sp,
                        lineHeight = 36.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFF8FAFC),
                        textAlign = TextAlign.Center
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Button(
                    onClick = onRepeat,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF0EA5E9),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                ) {
                    Text(AppStrings.get(context, "repeat_voice"), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = onDone,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF10B981),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                ) {
                    Text(AppStrings.get(context, "done_call"), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = onLeave,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFF59E0B),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                ) {
                    Text(AppStrings.get(context, "leave_call"), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
