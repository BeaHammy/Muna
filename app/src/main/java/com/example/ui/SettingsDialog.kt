package com.example.ui

import android.speech.tts.TextToSpeech
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VolumeUp
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
import androidx.compose.ui.window.Dialog
import com.example.data.AppSettings
import com.example.data.AppStrings
import com.example.ui.theme.*
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun SettingsDialog(
    listCode: String,
    listName: String = "",
    onOpenPermissions: () -> Unit,
    onLogout: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var speechRate by remember { mutableStateOf(AppSettings.getSpeechRate(context)) }
    var ttsReady by remember { mutableStateOf(false) }

    val tts = remember {
        var instance: TextToSpeech? = null
        instance = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val lang = AppSettings.getLanguage(context)
                instance?.setLanguage(if (lang == "en") Locale("en", "US") else Locale("pl", "PL"))
                ttsReady = true
            }
        }
        instance
    }

    DisposableEffect(Unit) {
        onDispose {
            tts.stop()
            tts.shutdown()
        }
    }

    fun speedLabel(rate: Float): String {
        val lang = AppSettings.getLanguage(context)
        return if (lang == "en") {
            when {
                rate < 0.72f -> "Very slow"
                rate < 0.92f -> "Slow (recommended)"
                rate <= 1.12f -> "Normal"
                else -> "Fast"
            }
        } else {
            when {
                rate < 0.72f -> "Bardzo wolno"
                rate < 0.92f -> "Wolno (polecane)"
                rate <= 1.12f -> "Normalnie"
                else -> "Szybko"
            }
        }
    }

    val displayPercent = (speechRate * 100).roundToInt()

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
                    text = AppStrings.get(context, "settings"),
                    style = MaterialTheme.typography.headlineMedium,
                    color = OnPrimaryContainer
                )

                Surface(
                    color = MineCardBg,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (listName.isNotBlank()) {
                            Text(
                                text = listName,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = InkPrimary,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                        Text(
                            text = if (AppSettings.getLanguage(context) == "en") "Active list code" else "Kod aktywnej listy",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = InkSecondary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = listCode,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold,
                            color = OnPrimaryContainer,
                            letterSpacing = 3.sp
                        )
                    }
                }

                HorizontalDivider(color = BorderNeutral)

                Text(
                    text = AppStrings.get(context, "voice_speed"),
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    color = InkPrimary
                )

                Surface(
                    color = PartnerCardBg,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "$displayPercent% — ${speedLabel(speechRate)}",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = WarningInk,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Slider(
                            value = speechRate,
                            onValueChange = { newValue ->
                                val rounded = (newValue * 20f).roundToInt() / 20f
                                speechRate = rounded
                                AppSettings.setSpeechRate(context, rounded)
                            },
                            valueRange = 0.5f..1.5f,
                            steps = 19,
                            colors = SliderDefaults.colors(
                                thumbColor = PrimaryBlue,
                                activeTrackColor = PrimaryBlue,
                                inactiveTrackColor = BorderNeutral
                            ),
                            modifier = Modifier.testTag("speech_rate_slider")
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val lang = AppSettings.getLanguage(context)
                            SpeedPresetButton(
                                label = if (lang == "en") "Slow" else "Wolno",
                                selected = speechRate in 0.7f..0.9f,
                                onClick = {
                                    speechRate = 0.8f
                                    AppSettings.setSpeechRate(context, 0.8f)
                                },
                                modifier = Modifier.weight(1f)
                            )
                            SpeedPresetButton(
                                label = if (lang == "en") "Normal" else "Normalnie",
                                selected = speechRate in 0.95f..1.05f,
                                onClick = {
                                    speechRate = 1.0f
                                    AppSettings.setSpeechRate(context, 1.0f)
                                },
                                modifier = Modifier.weight(1f)
                            )
                            SpeedPresetButton(
                                label = if (lang == "en") "Fast" else "Szybko",
                                selected = speechRate > 1.15f,
                                onClick = {
                                    speechRate = 1.25f
                                    AppSettings.setSpeechRate(context, 1.25f)
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Button(
                            onClick = {
                                if (ttsReady) {
                                    tts.setSpeechRate(speechRate)
                                    val lang = AppSettings.getLanguage(context)
                                    val sampleText = if (lang == "en") {
                                        "Hello, this is a sample task read with the selected speed."
                                    } else {
                                        "Cześć, to jest przykładowe zadanie odczytane z wybraną prędkością."
                                    }
                                    tts.speak(
                                        sampleText,
                                        TextToSpeech.QUEUE_FLUSH,
                                        null,
                                        "tts_preview"
                                    )
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = InfoTeal,
                                contentColor = OnPrimaryWhite
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 50.dp)
                                .testTag("test_tts_speed_button")
                        ) {
                            Icon(Icons.Default.VolumeUp, contentDescription = null, modifier = Modifier.size(22.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(AppStrings.get(context, "test_voice"), fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                HorizontalDivider(color = BorderNeutral)

                Text(
                    text = AppStrings.get(context, "language"),
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    color = InkPrimary
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val currentLang = AppSettings.getLanguage(context)
                    SpeedPresetButton(
                        label = "Polski",
                        selected = currentLang == "pl",
                        onClick = {
                            if (currentLang != "pl") {
                                AppSettings.setLanguage(context, "pl")
                                (context as? android.app.Activity)?.recreate()
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )
                    SpeedPresetButton(
                        label = "English",
                        selected = currentLang == "en",
                        onClick = {
                            if (currentLang != "en") {
                                AppSettings.setLanguage(context, "en")
                                (context as? android.app.Activity)?.recreate()
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedButton(
                    onClick = onOpenPermissions,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 52.dp)
                ) {
                    Icon(Icons.Default.Security, contentDescription = null, tint = PrimaryBlue)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = AppStrings.get(context, "permissions"),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = PrimaryBlue
                    )
                }

                OutlinedButton(
                    onClick = {
                        onDismiss()
                        onLogout()
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = DangerRed
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 52.dp)
                        .testTag("settings_logout_button")
                ) {
                    Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null, tint = DangerRed)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = AppStrings.get(context, "logout"),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = DangerRed
                    )
                }

                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PrimaryBlue,
                        contentColor = OnPrimaryWhite
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("close_settings_button")
                ) {
                    Text(AppStrings.get(context, "save_close"), fontSize = 19.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun SpeedPresetButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) PrimaryBlue else CardSurface,
            contentColor = if (selected) OnPrimaryWhite else InkPrimary
        ),
        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp),
        shape = RoundedCornerShape(8.dp),
        modifier = modifier.defaultMinSize(minHeight = 48.dp)
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}
