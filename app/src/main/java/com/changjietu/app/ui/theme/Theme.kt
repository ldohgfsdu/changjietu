package com.changjietu.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.darkColorScheme as miuixDarkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme as miuixLightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 品牌色：靛蓝（工具/可靠）+ 青绿（清晰）+ 琥珀（提醒/切点）。
 * 不启用 Material You 动态取色，保持产品辨识度。
 */
val BrandGradientStart = Color(0xFF3554D1)
val BrandGradientEnd = Color(0xFF0E7C86)

private val Indigo = BrandGradientStart
private val IndigoContainer = Color(0xFFDDE1FF)
private val IndigoDark = Color(0xFFB8C3FF)
private val IndigoDarkContainer = Color(0xFF203BA6)
private val Teal = BrandGradientEnd
private val TealContainer = Color(0xFFBDECEF)
private val TealDark = Color(0xFF6FD3DA)
private val TealDarkContainer = Color(0xFF004F56)
private val Amber = Color(0xFF9A5B00)
private val AmberContainer = Color(0xFFFFDDB5)
private val AmberDark = Color(0xFFFFB95C)
private val AmberDarkContainer = Color(0xFF6E3D00)

private val LightColors = lightColorScheme(
    primary = Indigo,
    onPrimary = Color.White,
    primaryContainer = IndigoContainer,
    onPrimaryContainer = Color(0xFF00105C),
    secondary = Teal,
    onSecondary = Color.White,
    secondaryContainer = TealContainer,
    onSecondaryContainer = Color(0xFF002023),
    tertiary = Amber,
    onTertiary = Color.White,
    tertiaryContainer = AmberContainer,
    onTertiaryContainer = Color(0xFF301D00),
    background = Color(0xFFF6F7FB),
    onBackground = Color(0xFF1A1B21),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1A1B21),
    surfaceVariant = Color(0xFFE9EBF3),
    onSurfaceVariant = Color(0xFF45464F),
    outline = Color(0xFF757680),
    outlineVariant = Color(0xFFC6C7D1),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
)

private val DarkColors = darkColorScheme(
    primary = IndigoDark,
    onPrimary = Color(0xFF0A2470),
    primaryContainer = IndigoDarkContainer,
    onPrimaryContainer = IndigoContainer,
    secondary = TealDark,
    onSecondary = Color(0xFF00363B),
    secondaryContainer = TealDarkContainer,
    onSecondaryContainer = TealContainer,
    tertiary = AmberDark,
    onTertiary = Color(0xFF4B2800),
    tertiaryContainer = AmberDarkContainer,
    onTertiaryContainer = AmberContainer,
    background = Color(0xFF111319),
    onBackground = Color(0xFFE3E1E9),
    surface = Color(0xFF191B22),
    onSurface = Color(0xFFE3E1E9),
    surfaceVariant = Color(0xFF44464F),
    onSurfaceVariant = Color(0xFFC5C6D0),
    outline = Color(0xFF8E9099),
    outlineVariant = Color(0xFF44464F),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)

private val AppTypography = Typography(
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.4).sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 21.sp,
        lineHeight = 27.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 21.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 18.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun ChangjietuTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    // 保留 MaterialTheme：SnackbarHost / LinearProgressIndicator 等残留 M3 组件仍需取色。
    // miuix 组件从 MiuixTheme 取色，两者叠加不冲突。
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        shapes = AppShapes,
    ) {
        MiuixTheme(
            colors = if (darkTheme) miuixDarkColorScheme() else miuixLightColorScheme(),
            content = content,
        )
    }
}
