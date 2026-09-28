package com.petermathie.vibecheck.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.RippleConfiguration
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

/**
 * The single source of visual truth. Screens consume semantic tokens from [LocalVibePalette]
 * and never embed colour literals. Adding a palette is one data object plus a registry entry.
 */
@Immutable
data class VibePalette(
    val id: String,
    val displayName: String,
    val isDark: Boolean,
    val background: Color,
    val surface: Color,
    val surfaceInset: Color,
    val surfaceRaised: Color,
    val surfaceSelected: Color,
    val surfaceFloating: Color,
    val surfaceModal: Color,
    val accent: Color,
    val onAccent: Color,
    val accentContainer: Color,
    val onAccentContainer: Color,
    val secondary: Color,
    val onSecondary: Color,
    val secondaryContainer: Color,
    val onSecondaryContainer: Color,
    val tertiary: Color,
    val onTertiary: Color,
    val tertiaryContainer: Color,
    val onTertiaryContainer: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textFaint: Color,
    val border: Color,
    val focusRing: Color,
    val scrim: Color,
    val diagramBackground: Color,
    val diagramBody: Color,
    val diagramLine: Color,
    val recencyUnder24: Color,
    val recency24To48: Color,
    val recency48To72: Color,
    val recency3To7: Color,
    val recencyOver7: Color,
    val recencyNever: Color,
    val heatmapNeutral: Color,
    val heatmapOne: Color,
    val heatmapTwo: Color,
    val heatmapThreePlus: Color,
    val danger: Color,
    val onDanger: Color,
    val dangerContainer: Color,
    val onDangerContainer: Color,
)

@Immutable
data class VibePalettePreset(
    val id: String,
    val displayName: String,
    val light: VibePalette,
    val dark: VibePalette,
)

enum class VibeThemeMode(val id: String, val displayName: String) {
    SYSTEM("system", "System"),
    DARK("dark", "Dark"),
    LIGHT("light", "Light");

    companion object {
        fun fromPreference(value: String?): VibeThemeMode =
            entries.firstOrNull { it.id == value } ?: SYSTEM
    }

    fun useDarkPalette(systemDark: Boolean): Boolean = when (this) {
        SYSTEM -> systemDark
        DARK -> true
        LIGHT -> false
    }
}

object VibePalettes {
    private fun palette(
        id: String,
        name: String,
        isDark: Boolean,
        background: Long,
        surface: Long,
        inset: Long,
        raised: Long,
        selected: Long,
        floating: Long,
        modal: Long,
        primary: Long,
        onPrimary: Long,
        primaryContainer: Long,
        onPrimaryContainer: Long,
        secondary: Long,
        onSecondary: Long,
        secondaryContainer: Long,
        onSecondaryContainer: Long,
        tertiary: Long,
        onTertiary: Long,
        tertiaryContainer: Long,
        onTertiaryContainer: Long,
        text: Long,
        textSecondary: Long,
        textFaint: Long,
        outline: Long,
        coolFresh: Long,
        heatNeutral: Long,
        heatOne: Long,
        heatTwo: Long,
        heatThree: Long,
        error: Long,
        onError: Long,
        errorContainer: Long,
        onErrorContainer: Long,
    ) = VibePalette(
        id = id,
        displayName = name,
        isDark = isDark,
        background = Color(background),
        surface = Color(surface),
        surfaceInset = Color(inset),
        surfaceRaised = Color(raised),
        surfaceSelected = Color(selected),
        surfaceFloating = Color(floating),
        surfaceModal = Color(modal),
        accent = Color(primary),
        onAccent = Color(onPrimary),
        accentContainer = Color(primaryContainer),
        onAccentContainer = Color(onPrimaryContainer),
        secondary = Color(secondary),
        onSecondary = Color(onSecondary),
        secondaryContainer = Color(secondaryContainer),
        onSecondaryContainer = Color(onSecondaryContainer),
        tertiary = Color(tertiary),
        onTertiary = Color(onTertiary),
        tertiaryContainer = Color(tertiaryContainer),
        onTertiaryContainer = Color(onTertiaryContainer),
        textPrimary = Color(text),
        textSecondary = Color(textSecondary),
        textFaint = Color(textFaint),
        border = Color(outline),
        focusRing = Color(primary),
        scrim = Color(if (isDark) 0x70000000 else 0x52000000),
        diagramBackground = Color(surface),
        diagramBody = Color(raised),
        diagramLine = Color(outline),
        recencyUnder24 = Color(error),
        recency24To48 = Color(tertiary),
        recency48To72 = Color(primary),
        recency3To7 = Color(secondary),
        recencyOver7 = Color(coolFresh),
        recencyNever = Color(textFaint),
        heatmapNeutral = Color(raised),
        heatmapOne = Color(secondaryContainer),
        heatmapTwo = Color(secondary),
        heatmapThreePlus = Color(coolFresh),
        danger = Color(error),
        onDanger = Color(onError),
        dangerContainer = Color(errorContainer),
        onDangerContainer = Color(onErrorContainer),
    )

    private fun VibePalette.withScales(
        freshness: List<Long>,
        heatmap: List<Long>,
    ): VibePalette {
        require(freshness.size == 6)
        require(heatmap.size == 4)
        return copy(
            recencyUnder24 = Color(freshness[0]),
            recency24To48 = Color(freshness[1]),
            recency48To72 = Color(freshness[2]),
            recency3To7 = Color(freshness[3]),
            recencyOver7 = Color(freshness[4]),
            recencyNever = Color(freshness[5]),
            heatmapNeutral = Color(heatmap[0]),
            heatmapOne = Color(heatmap[1]),
            heatmapTwo = Color(heatmap[2]),
            heatmapThreePlus = Color(heatmap[3]),
        )
    }

    val Ocean = VibePalettePreset(
        id = "ocean",
        displayName = "Ocean",
        light = palette("ocean", "Ocean", false, 0xFFF5FAFD, 0xFFFFFFFF, 0xFFF0F6F9, 0xFFE5F0F6, 0xFFD4E8F2, 0xFFFFFFFF, 0xFFFFFFFF, 0xFF006782, 0xFFFFFFFF, 0xFFBCE9F8, 0xFF001F29, 0xFF426277, 0xFFFFFFFF, 0xFFCBE7F7, 0xFF001E2C, 0xFF67587A, 0xFFFFFFFF, 0xFFEDDCFF, 0xFF221534, 0xFF132027, 0xFF425E6B, 0xFF657B85, 0xFF718994, 0xFF39769A, 0xFFE5F0F6, 0xFFA8D5E8, 0xFF58A7C7, 0xFF28789A, 0xFFBA1A1A, 0xFFFFFFFF, 0xFFFFDAD6, 0xFF410002).withScales(
            listOf(0xFFB3261E, 0xFFC25B20, 0xFF9A7500, 0xFF287F8C, 0xFF315F9C, 0xFF6F6F6F),
            listOf(0xFFE5F0F6, 0xFFB9DCF2, 0xFF5FA9DC, 0xFF0078D4),
        ),
        // Core roles follow https://github.com/microsoft/vscode/blob/main/extensions/theme-defaults/themes/dark_modern.json (2026-09-25).
        dark = palette("ocean", "Ocean", true, 0xFF181818, 0xFF252525, 0xFF141414, 0xFF222222, 0xFF2C3250, 0xFF303030, 0xFF343434, 0xFF0078D4, 0xFFFFFFFF, 0xFF004F8C, 0xFFFFFFFF, 0xFFC6C6C6, 0xFF181818, 0xFF303030, 0xFFD7D7D7, 0xFF8AAFD4, 0xFF152333, 0xFF29384A, 0xFFE5E5E5, 0xFFD7D7D7, 0xFFC6C6C6, 0xFF868686, 0xFF2B2B2B, 0xFF70A9D7, 0xFF222222, 0xFF294158, 0xFF3D6D94, 0xFF70A9D7, 0xFFFF3B3B, 0xFF181818, 0xFF5C1717, 0xFFFFDAD6).withScales(
            listOf(0xFFFF3B3B, 0xFFF28C45, 0xFFD6B04A, 0xFF4AA7C8, 0xFF477FB8, 0xFF868686),
            listOf(0xFF222222, 0xFF24415B, 0xFF17699F, 0xFF0078D4),
        ),
    )

    val Sunset = VibePalettePreset(
        id = "sunset",
        displayName = "Sunset",
        light = palette("sunset", "Sunset", false, 0xFFFFF8F6, 0xFFFFFBFF, 0xFFF6EFEC, 0xFFF8EAE5, 0xFFFFDCD2, 0xFFFFFFFF, 0xFFFFFFFF, 0xFF9B3F31, 0xFFFFFFFF, 0xFFFFDAD2, 0xFF3E0500, 0xFF765844, 0xFFFFFFFF, 0xFFFFDCC2, 0xFF2C1608, 0xFF765A00, 0xFFFFFFFF, 0xFFFFDF91, 0xFF251A00, 0xFF271814, 0xFF5D4038, 0xFF816A63, 0xFF947169, 0xFF50799A, 0xFFF8EAE5, 0xFFF3C7A5, 0xFFD98C62, 0xFFB75B43, 0xFFBA1A1A, 0xFFFFFFFF, 0xFFFFDAD6, 0xFF410002).withScales(
            listOf(0xFFB3261E, 0xFFB85C1E, 0xFF8A7000, 0xFF4F8270, 0xFF416B91, 0xFF6F6F6F),
            listOf(0xFFF8EAE5, 0xFFF7D1C3, 0xFFE99170, 0xFFB94F37),
        ),
        dark = palette("sunset", "Sunset", true, 0xFF191817, 0xFF211F1E, 0xFF151413, 0xFF2A2725, 0xFF37302B, 0xFF302D2A, 0xFF35312E, 0xFFFFB4A6, 0xFF5E160D, 0xFF7D2A1E, 0xFFFFDAD2, 0xFFE0C1AD, 0xFF432B1B, 0xFF4A3830, 0xFFFFDCC2, 0xFFE7C75C, 0xFF3E2E00, 0xFF574500, 0xFFFFDF91, 0xFFF5F1EF, 0xFFD2C8C3, 0xFF99908B, 0xFF514A46, 0xFF78AEDA, 0xFF2A2725, 0xFF5C4439, 0xFF96634E, 0xFFD8916B, 0xFFFFB4AB, 0xFF690005, 0xFF93000A, 0xFFFFDAD6).withScales(
            listOf(0xFFFF6B63, 0xFFF39A62, 0xFFD9BA5A, 0xFF84B39A, 0xFF72A9CC, 0xFF99908B),
            listOf(0xFF2A2725, 0xFF553A31, 0xFFA86246, 0xFFF08A63),
        ),
    )

    val Forest = VibePalettePreset(
        id = "forest",
        displayName = "Forest",
        light = palette("forest", "Forest", false, 0xFFF6FBF6, 0xFFFBFDF8, 0xFFF0F5F0, 0xFFE8F1E7, 0xFFD7E8D5, 0xFFFFFFFF, 0xFFFFFFFF, 0xFF356A3D, 0xFFFFFFFF, 0xFFB7F0B8, 0xFF002108, 0xFF52634F, 0xFFFFFFFF, 0xFFD5E8CF, 0xFF101F10, 0xFF5B6146, 0xFFFFFFFF, 0xFFDFE6BD, 0xFF191E08, 0xFF172018, 0xFF455D48, 0xFF6B7D6D, 0xFF758A77, 0xFF47799A, 0xFFE8F1E7, 0xFFB6D7B2, 0xFF6BA873, 0xFF397846, 0xFFBA1A1A, 0xFFFFFFFF, 0xFFFFDAD6, 0xFF410002).withScales(
            listOf(0xFFB3261E, 0xFFB26524, 0xFF817600, 0xFF3F8061, 0xFF356F86, 0xFF747474),
            listOf(0xFFE8F1E7, 0xFFCDE4CD, 0xFF80B889, 0xFF397846),
        ),
        dark = palette("forest", "Forest", true, 0xFF151917, 0xFF1D221F, 0xFF121614, 0xFF252B27, 0xFF2E3831, 0xFF2C332F, 0xFF333A36, 0xFF79E6B1, 0xFF003822, 0xFF005233, 0xFF9BF6CA, 0xFFBBCDBD, 0xFF29352B, 0xFF39463B, 0xFFD5E8CF, 0xFFC3CAA1, 0xFF2D321B, 0xFF444931, 0xFFDFE6BD, 0xFFF0F4F1, 0xFFC4CEC7, 0xFF8C9890, 0xFF48524B, 0xFF72B7DE, 0xFF252B27, 0xFF315643, 0xFF4F8262, 0xFF79B58A, 0xFFFFB4AB, 0xFF690005, 0xFF93000A, 0xFFFFDAD6).withScales(
            listOf(0xFFF27D72, 0xFFD99B62, 0xFFAFBF69, 0xFF67B889, 0xFF66AFC2, 0xFF8C9890),
            listOf(0xFF252B27, 0xFF2F4937, 0xFF477B58, 0xFF69B982),
        ),
    )

    val Mono = VibePalettePreset(
        id = "mono",
        displayName = "Mono",
        light = palette("mono", "Mono", false, 0xFFF8F9FA, 0xFFFFFFFF, 0xFFF1F3F4, 0xFFECEFF1, 0xFFDDE3E7, 0xFFFFFFFF, 0xFFFFFFFF, 0xFF245C75, 0xFFFFFFFF, 0xFFCBE7F5, 0xFF001F2A, 0xFF586168, 0xFFFFFFFF, 0xFFDDE3E7, 0xFF151D21, 0xFF555F64, 0xFFFFFFFF, 0xFFDDE4E7, 0xFF121D21, 0xFF181C1E, 0xFF454B4F, 0xFF6F777B, 0xFF777F83, 0xFF557F94, 0xFFECEFF1, 0xFFCED9DE, 0xFF91A6B0, 0xFF607F8D, 0xFFBA1A1A, 0xFFFFFFFF, 0xFFFFDAD6, 0xFF410002).withScales(
            listOf(0xFF303030, 0xFF454545, 0xFF5D5D5D, 0xFF747474, 0xFF8B8B8B, 0xFFD8D8D8),
            listOf(0xFFECEFF1, 0xFFD7DCE0, 0xFF939BA1, 0xFF50565A),
        ),
        dark = palette("mono", "Mono", true, 0xFF171819, 0xFF1F2123, 0xFF141516, 0xFF282B2E, 0xFF33373B, 0xFF303337, 0xFF34373B, 0xFF9CCFE8, 0xFF17333F, 0xFF304A56, 0xFFD5EDF7, 0xFFC7C9CB, 0xFF303336, 0xFF44484B, 0xFFE0E3E5, 0xFFBBC7CC, 0xFF2B3438, 0xFF3C484D, 0xFFD7E3E8, 0xFFF0F1F2, 0xFFC8CACC, 0xFF92979B, 0xFF50555A, 0xFF73A8C1, 0xFF282B2E, 0xFF40525A, 0xFF607A86, 0xFF87A7B5, 0xFFFFB4AB, 0xFF690005, 0xFF93000A, 0xFFFFDAD6).withScales(
            listOf(0xFFF2F2F2, 0xFFD0D0D0, 0xFFAEAEAE, 0xFF8C8C8C, 0xFF6B6B6B, 0xFF3A3A3A),
            listOf(0xFF282B2E, 0xFF3C4145, 0xFF747B80, 0xFFBFC4C7),
        ),
    )

    val presets = listOf(Ocean, Sunset, Forest, Mono)
    val ids = presets.map { it.id }.toSet()
    private val legacyIds = setOf("midnight-lime", "graphite-coral", "ocean-cyan", "plum-orchid", "amber-slate", "forest-mint", "custom")

    fun normalizeId(value: String?): String = when (value) {
        in ids -> value!!
        in legacyIds, null -> Ocean.id
        else -> Ocean.id
    }

    fun resolve(value: String?, dark: Boolean): VibePalette {
        val preset = presets.first { it.id == normalizeId(value) }
        return if (dark) preset.dark else preset.light
    }
}

val LocalVibePalette = staticCompositionLocalOf { VibePalettes.Ocean.dark }
val LocalVibeReducedMotion = staticCompositionLocalOf { false }

object VibeSpacing {
    val xSmall = 4.dp
    val small = 8.dp
    val compact = 12.dp
    val medium = 16.dp
    val large = 24.dp
    val xLarge = 32.dp
}

object VibeShapes {
    val tooltip = 4.dp
    val small = 6.dp
    val control = 8.dp
    val card = 8.dp
    val panel = 12.dp
    val dialog = 12.dp
}

private val VibeMaterialShapes = Shapes(
    extraSmall = RoundedCornerShape(VibeShapes.tooltip),
    small = RoundedCornerShape(VibeShapes.small),
    medium = RoundedCornerShape(VibeShapes.control),
    large = RoundedCornerShape(VibeShapes.panel),
    extraLarge = RoundedCornerShape(VibeShapes.dialog),
)

val VibeDashboardTypography = DashboardTypography(
    metricXL = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 40.sp, lineHeight = 44.sp, letterSpacing = (-0.5).sp, fontFeatureSettings = "tnum"),
    metric = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 28.sp, lineHeight = 32.sp, letterSpacing = (-0.2).sp, fontFeatureSettings = "tnum"),
    metricCompact = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 24.sp, fontFeatureSettings = "tnum"),
    label = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 18.sp, letterSpacing = 0.2.sp),
    microLabel = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.7.sp),
    body = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 22.sp),
    annotation = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.1.sp, fontFeatureSettings = "tnum"),
)

@Immutable
data class DashboardTypography(
    val metricXL: TextStyle,
    val metric: TextStyle,
    val metricCompact: TextStyle,
    val label: TextStyle,
    val microLabel: TextStyle,
    val body: TextStyle,
    val annotation: TextStyle,
)

private val VibeMaterialTypography = Typography(
    headlineLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 38.sp),
    headlineMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 26.sp, lineHeight = 32.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 21.sp, lineHeight = 27.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 17.sp, lineHeight = 23.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 18.sp),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VibeCheckTheme(
    palette: VibePalette = VibePalettes.Ocean.dark,
    visualStyle: VibeVisualStyle = VibeVisualStyle.STANDARD,
    content: @Composable () -> Unit,
) {
    val preferences=LocalContext.current.getSharedPreferences("settings",0)
    val view = LocalView.current
    var reducedMotion by remember { mutableStateOf(preferences.getBoolean("reducedMotion",false)) }
    DisposableEffect(preferences) {
        val listener=android.content.SharedPreferences.OnSharedPreferenceChangeListener { p,key ->
            if(key=="reducedMotion") reducedMotion=p.getBoolean(key,false)
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        onDispose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    val scheme = if (palette.isDark) darkColorScheme(
        primary = palette.accent,
        onPrimary = palette.onAccent,
        primaryContainer = palette.accentContainer,
        onPrimaryContainer = palette.onAccentContainer,
        secondary = palette.secondary,
        onSecondary = palette.onSecondary,
        secondaryContainer = palette.secondaryContainer,
        onSecondaryContainer = palette.onSecondaryContainer,
        tertiary = palette.tertiary,
        onTertiary = palette.onTertiary,
        tertiaryContainer = palette.tertiaryContainer,
        onTertiaryContainer = palette.onTertiaryContainer,
        background = palette.background,
        onBackground = palette.textPrimary,
        surface = palette.surface,
        surfaceDim = palette.surfaceInset,
        surfaceBright = palette.surfaceModal,
        surfaceContainerLowest = palette.surfaceInset,
        surfaceContainerLow = palette.surface,
        surfaceContainer = palette.surface,
        surfaceContainerHigh = palette.surfaceFloating,
        surfaceContainerHighest = palette.surfaceModal,
        onSurface = palette.textPrimary,
        surfaceVariant = palette.surfaceRaised,
        onSurfaceVariant = palette.textSecondary,
        outline = palette.border,
        error = palette.danger,
        onError = palette.onDanger,
        errorContainer = palette.dangerContainer,
        onErrorContainer = palette.onDangerContainer,
    ) else lightColorScheme(
        primary = palette.accent,
        onPrimary = palette.onAccent,
        primaryContainer = palette.accentContainer,
        onPrimaryContainer = palette.onAccentContainer,
        secondary = palette.secondary,
        onSecondary = palette.onSecondary,
        secondaryContainer = palette.secondaryContainer,
        onSecondaryContainer = palette.onSecondaryContainer,
        tertiary = palette.tertiary,
        onTertiary = palette.onTertiary,
        tertiaryContainer = palette.tertiaryContainer,
        onTertiaryContainer = palette.onTertiaryContainer,
        background = palette.background,
        onBackground = palette.textPrimary,
        surface = palette.surface,
        surfaceDim = palette.surfaceInset,
        surfaceBright = palette.surfaceModal,
        surfaceContainerLowest = palette.surfaceInset,
        surfaceContainerLow = palette.surface,
        surfaceContainer = palette.surface,
        surfaceContainerHigh = palette.surfaceFloating,
        surfaceContainerHighest = palette.surfaceModal,
        onSurface = palette.textPrimary,
        surfaceVariant = palette.surfaceRaised,
        onSurfaceVariant = palette.textSecondary,
        outline = palette.border,
        error = palette.danger,
        onError = palette.onDanger,
        errorContainer = palette.dangerContainer,
        onErrorContainer = palette.onDangerContainer,
    )
    if (!view.isInEditMode) {
        SideEffect {
            view.context.findActivity()?.window?.let { window ->
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !palette.isDark
                    isAppearanceLightNavigationBars = !palette.isDark
                }
            }
        }
    }
    androidx.compose.runtime.CompositionLocalProvider(
        LocalVibePalette provides palette,
        LocalVibeVisualStyle provides visualStyle,
        LocalVibeStyleTokens provides VibeVisualStyles.resolve(visualStyle, palette, reducedMotion),
        LocalVibeDashboardTypography provides VibeVisualStyles.dashboardTypography(visualStyle),
        LocalVibeReducedMotion provides reducedMotion,
        LocalVibeMotion provides VibeMotion.resolve(reducedMotion),
        LocalRippleConfiguration provides if(reducedMotion) null else RippleConfiguration(),
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = if (visualStyle == VibeVisualStyle.RETRO_FUTURE) {
                VibeMaterialTypography.copy(
                    labelLarge = VibeMaterialTypography.labelLarge.copy(
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 0.7.sp,
                    ),
                    labelMedium = VibeMaterialTypography.labelMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 0.6.sp,
                    ),
                    labelSmall = VibeMaterialTypography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 0.5.sp,
                    ),
                )
            } else {
                VibeMaterialTypography
            },
            shapes = VibeMaterialShapes,
            content = content,
        )
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
