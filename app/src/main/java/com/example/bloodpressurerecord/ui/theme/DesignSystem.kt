package com.example.bloodpressurerecord.ui.theme

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

object AppSpacing {
    val xSmall = 4.dp
    val small = 8.dp
    val medium = 12.dp
    val large = 16.dp
    val xLarge = 24.dp
    val xxLarge = 32.dp
}

object AppDimensions {
    /** UIredesign 页面水平边距。 */
    val pageHorizontalPadding = 20.dp
    /** 原稿通用卡片内边距。 */
    val cardPadding = 18.dp
    val minimumTouchTarget = 48.dp
    /** 普通主/次/危险按钮统一高度（表单保存主按钮单独保留 saveButtonHeight = 58.dp）。 */
    val primaryButtonHeight = 54.dp
    /** 表单保存按钮高度。 */
    val saveButtonHeight = 58.dp
    val calendarDayMinHeight = 48.dp
    /** 日历日期圆形直径。 */
    val calendarDaySize = 38.dp

    // —— 底部悬浮 Dock 几何 ——
    // Dock 与页面底部避让间距都由这几个常量推导，页面侧不要再写固定 dp。

    /** 悬浮胶囊 Dock 的高度。 */
    val dockCapsuleHeight = 64.dp
    /** 胶囊底边距导航栏安全区的距离。 */
    val dockBottomMargin = 10.dp
    /** Dock 中央「记一次血压」按钮所占的槽宽（比按钮本身宽，保证居中留白）。 */
    val dockCenterSlot = 72.dp
    /** Dock 中央按钮直径。 */
    val dockCenterButtonSize = 52.dp
    /** Dock 距屏幕左右边缘的边距。 */
    val dockHorizontalMargin = 16.dp

    /** 页面内容滚动到底后的视觉收尾间距。 */
    val pageBottomGap = 16.dp
    /** 有悬浮 Dock 的页面，内容与 Dock 胶囊顶边之间额外保留的间距。 */
    val dockContentGap = 12.dp

    /**
     * 有悬浮 Dock 的顶级页面，内容底部至少要留出的避让高度。
     *
     * 兜底下限：即使导航栏安全区在个别设备/系统版本上被报成 0，
     * 内容也仍然滚得到 Dock 胶囊上方，不会被悬浮导航遮住。
     */
    val dockMinContentClearance = 128.dp

    /**
     * 血压输入框高度（紧凑三栏布局）。
     *
     * 必须留得下「三位数字 + 光标」：数字字号上限 38sp 时，单行实际行高约 46–50dp，
     * 64dp 会把数字的上下缘贴到边框上（视觉上像被裁切），因此放宽到 76dp。
     */
    val numberFieldHeight = 76.dp
}

enum class BloodPressureVisualStatus {
    NORMAL,
    LOW,
    ELEVATED,
    HIGH,
    HIGH_RISK
}

data class BloodPressureStatusStyle(
    val label: String,
    val icon: ImageVector,
    val containerColor: Color,
    val contentColor: Color
)

/**
 * 血压状态色：
 * 正常走鼠尾草绿，偏高/高风险走陶土橙/红，偏低走柔和蓝紫色系提示。
 */
fun BloodPressureVisualStatus.style(colors: ColorScheme): BloodPressureStatusStyle = when (this) {
    BloodPressureVisualStatus.NORMAL -> BloodPressureStatusStyle(
        label = "正常",
        icon = Icons.Default.CheckCircle,
        containerColor = colors.secondaryContainer,
        contentColor = colors.onSecondaryContainer
    )
    BloodPressureVisualStatus.LOW -> BloodPressureStatusStyle(
        label = "血压偏低",
        icon = Icons.Default.Info,
        containerColor = colors.tertiaryContainer,
        contentColor = colors.onTertiaryContainer
    )
    BloodPressureVisualStatus.ELEVATED -> BloodPressureStatusStyle(
        label = "正常高值",
        icon = Icons.Default.Info,
        containerColor = colors.primaryContainer,
        contentColor = colors.onPrimaryContainer
    )
    BloodPressureVisualStatus.HIGH -> BloodPressureStatusStyle(
        label = "血压偏高",
        icon = Icons.Default.Warning,
        containerColor = colors.primaryContainer,
        contentColor = colors.onPrimaryContainer
    )
    BloodPressureVisualStatus.HIGH_RISK -> BloodPressureStatusStyle(
        label = "含高风险读数",
        icon = Icons.Default.Error,
        containerColor = colors.errorContainer,
        contentColor = colors.onErrorContainer
    )
}

fun bloodPressureVisualStatus(
    category: String,
    containsHighRiskReading: Boolean
): BloodPressureVisualStatus = when {
    containsHighRiskReading -> BloodPressureVisualStatus.HIGH_RISK
    category.equals("NORMAL", ignoreCase = true) -> BloodPressureVisualStatus.NORMAL
    category.equals("LOW", ignoreCase = true) -> BloodPressureVisualStatus.LOW
    // ELEVATED 为 v5 迁移前的旧命名，与正常高值同级
    category.equals("HIGH_NORMAL", ignoreCase = true) ||
        category.equals("ELEVATED", ignoreCase = true) -> BloodPressureVisualStatus.ELEVATED
    else -> BloodPressureVisualStatus.HIGH
}

data class BloodPressureChipColors(
    val container: Color,
    val content: Color,
    val dot: Color
)

/**
 * 六级血压统一语义视觉 Token（首页大卡、分级芯片、历史日历、趋势分布条单一事实来源）。
 */
data class BpGradeVisual(
    val soft: Color,
    val ink: Color,
    val mid: Color,
    val body: Color,
    val mood: String
)

data class BpGradePalette(
    val isDark: Boolean,
    val chartPulse: Color,
    val low: BpGradeVisual,
    val normal: BpGradeVisual,
    val highNormal: BpGradeVisual,
    val stage1: BpGradeVisual,
    val stage2: BpGradeVisual,
    val stage3: BpGradeVisual
) {
    fun forCategory(category: String): BpGradeVisual = when {
        category.equals("LOW", ignoreCase = true) || category.contains("偏低") -> low
        category.equals("HIGH_NORMAL", ignoreCase = true) ||
            category.equals("ELEVATED", ignoreCase = true) ||
            category.contains("正常高值") -> highNormal
        category.equals("STAGE1", ignoreCase = true) ||
            category.contains("1级") || category.contains("一级") -> stage1
        category.equals("STAGE2", ignoreCase = true) ||
            category.contains("2级") || category.contains("二级") -> stage2
        category.equals("STAGE3", ignoreCase = true) ||
            category.contains("3级") || category.contains("三级") -> stage3
        else -> normal
    }
}

private fun buildBpGradePalette(dark: Boolean): BpGradePalette {
    fun visual(
        softHex: Long,
        inkHex: Long,
        midHex: Long,
        bodyHex: Long,
        mood: String,
        darkInkHex: Long = midHex
    ): BpGradeVisual {
        val ink = Color(inkHex)
        val mid = Color(midHex)
        return BpGradeVisual(
            soft = if (dark) ink.copy(alpha = 0.24f) else Color(softHex),
            ink = if (dark) Color(darkInkHex) else ink,
            mid = mid,
            body = Color(bodyHex),
            mood = mood
        )
    }
    return BpGradePalette(
        isDark = dark,
        chartPulse = if (dark) ChartPulseDark else ChartPulseLight,
        low = visual(0xFFE9E9FA, 0xFF55509B, 0xFF8E8BC8, 0xFF9F9CD1, "sleepy", darkInkHex = 0xFFA3A0DE),
        normal = visual(0xFFDFF4E9, 0xFF287958, 0xFF69C49D, 0xFF85CBA9, "happy"),
        highNormal = visual(0xFFEEF3D7, 0xFF61742E, 0xFFA5BF65, 0xFFB8CE7B, "calm"),
        stage1 = visual(0xFFF7F0CF, 0xFF806625, 0xFFD9B55E, 0xFFE5C470, "meh"),
        stage2 = visual(0xFFFBE5D7, 0xFF9F5335, 0xFFEAA27E, 0xFFEEB08C, "worried"),
        stage3 = visual(0xFFFADDD9, 0xFFA03F39, 0xFFE58178, 0xFFE88E84, "worried")
    )
}

val LightBpGradePalette = buildBpGradePalette(dark = false)
val DarkBpGradePalette = buildBpGradePalette(dark = true)

val LocalBpGradePalette = androidx.compose.runtime.staticCompositionLocalOf { LightBpGradePalette }

/** 与根目录原稿 GradeChip 一致的六级色；高风险由独立阈值优先覆盖。 */
fun bloodPressureChipColors(
    text: String,
    status: BloodPressureVisualStatus?,
    colors: ColorScheme,
    palette: BpGradePalette? = null
): BloodPressureChipColors {
    if (status == BloodPressureVisualStatus.HIGH_RISK || text.contains("高风险")) {
        return BloodPressureChipColors(colors.errorContainer, colors.onErrorContainer, colors.error)
    }
    val resolvedPalette = palette ?: if (colors.background.red < 0.5f) DarkBpGradePalette else LightBpGradePalette
    val visual = when {
        text.contains("偏低") || status == BloodPressureVisualStatus.LOW -> resolvedPalette.low
        text.contains("正常高值") || status == BloodPressureVisualStatus.ELEVATED -> resolvedPalette.highNormal
        text.contains("3级") || text.contains("三级") -> resolvedPalette.stage3
        text.contains("2级") || text.contains("二级") -> resolvedPalette.stage2
        text.contains("1级") || text.contains("一级") -> resolvedPalette.stage1
        text.contains("正常") || status == BloodPressureVisualStatus.NORMAL -> resolvedPalette.normal
        status == BloodPressureVisualStatus.HIGH -> resolvedPalette.stage2
        else -> resolvedPalette.normal
    }
    return BloodPressureChipColors(
        container = visual.soft,
        content = visual.ink,
        dot = visual.mid
    )
}
