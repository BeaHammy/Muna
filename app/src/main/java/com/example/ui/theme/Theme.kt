package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val AppLightColorScheme = lightColorScheme(
    primary = PrimaryBlue,
    onPrimary = OnPrimaryWhite,
    primaryContainer = PrimaryContainer,
    onPrimaryContainer = OnPrimaryContainer,
    secondary = PartnerBadgeBg,
    onSecondary = OnPrimaryWhite,
    secondaryContainer = PartnerCardBg,
    onSecondaryContainer = WarningInk,
    tertiary = SuccessGreen,
    onTertiary = OnPrimaryWhite,
    background = WarmBackground,
    onBackground = InkPrimary,
    surface = CardSurface,
    onSurface = InkPrimary,
    surfaceVariant = MineCardBg,
    onSurfaceVariant = InkSecondary,
    outline = BorderNeutral,
    error = DangerRed,
    onError = OnPrimaryWhite
)

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = AppLightColorScheme,
        typography = Typography,
        content = content
    )
}
