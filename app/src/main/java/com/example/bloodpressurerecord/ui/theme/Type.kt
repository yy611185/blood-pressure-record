package com.example.bloodpressurerecord.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.bloodpressurerecord.R

/** 等宽数字排版特性，防止读数跳动引发同行单位文字左右抖动。 */
const val TabularFeatureSettings = "tnum"

fun TextStyle.tabularNums(): TextStyle = copy(fontFeatureSettings = TabularFeatureSettings)

/**
 * 原稿数字字体 Bricolage Grotesque（Google Fonts, OFL），离线打包；
 * 中文字符自动回退系统字体。用于血压大数字与统计数值。
 */
val NumberFontFamily = FontFamily(
    Font(R.font.bricolage_grotesque, weight = FontWeight.Normal),
    Font(R.font.bricolage_grotesque, weight = FontWeight.SemiBold),
    Font(R.font.bricolage_grotesque, weight = FontWeight.Bold)
)

val AppTypography = Typography(
    // 首页核心血压大数字：58sp / 62sp
    displayLarge = TextStyle(
        fontFamily = NumberFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 58.sp,
        lineHeight = 62.sp,
        letterSpacing = (-1).sp,
        fontFeatureSettings = TabularFeatureSettings
    ),
    // 最近血压大数字：54sp / 行高 1
    displayMedium = TextStyle(
        fontFamily = NumberFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 54.sp,
        lineHeight = 54.sp,
        letterSpacing = (-1).sp,
        fontFeatureSettings = TabularFeatureSettings
    ),
    // 记录详情页主读数：34sp / 40sp
    displaySmall = TextStyle(
        fontFamily = NumberFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 34.sp,
        lineHeight = 40.sp,
        letterSpacing = 0.sp,
        fontFeatureSettings = TabularFeatureSettings
    ),
    // 页面大标题（测量首页问候）30sp
    headlineLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 30.sp,
        lineHeight = 38.sp,
        letterSpacing = 0.sp
    ),
    // 历史/趋势/设置页标题 30sp
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 30.sp,
        lineHeight = 38.sp,
        letterSpacing = 0.sp
    ),
    // 历史卡片读数、均值卡、更新弹窗主标题 24sp
    headlineSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = 0.sp
    ),
    // 子页顶栏标题 22sp
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    // 分组标题、卡片条目标题 17sp
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.sp
    ),
    // 选中日摘要标题、搜索结果日期等 15sp
    titleSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 21.sp,
        letterSpacing = 0.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.sp
    ),
    // 正文 14sp
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 21.sp,
        letterSpacing = 0.sp
    ),
    // 说明 12sp
    bodySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 17.sp,
        letterSpacing = 0.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.sp
    ),
    // chip / 导航 13sp
    labelMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.sp
    ),
    // 辅助微标签、四色条标签、次级信息 12sp（归零中文字距）
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.sp
    )
)
