package com.example.bloodpressurerecord.ui.theme

import androidx.compose.ui.graphics.Color

// UIredesign/bp.css 的 sRGB 映射。保留既有符号，供原生子页和小部件共用。

// 背景与文字
val WarmBackground = Color(0xFFFAF6EF)
val WarmSurface = Color(0xFFFFFDFA)
val WarmSurfaceSoft = Color(0xFFF2ECE3)
val WarmText = Color(0xFF27201B)
val WarmTextMuted = Color(0xFF746C65)
val WarmTextFaint = Color(0xFF746C65)       // 辅助正文使用可读对比度
val WarmTextDisabled = Color(0xFFC0B6A5)    // neutral-400
val WarmTextBody = Color(0xFF645C55)
val WarmDivider = Color(0xFFE3DDD5)
val WarmNeutral200 = Color(0xFFE9E2D7)
val WarmNeutral300 = Color(0xFFE3DDD5)
val WarmNeutral900 = Color(0xFF26201C)

// 主 accent：原稿 hue 32 珊瑚橙
val Terracotta100 = Color(0xFFFFF2EB)
val Terracotta200 = Color(0xFFFFDED5)
val Terracotta300 = Color(0xFFFFC6A5)
val Terracotta400 = Color(0xFFFFA997)
val Terracotta500 = Color(0xFFDF6A55)
val Terracotta600 = Color(0xFFC4513E)
val Terracotta700 = Color(0xFF963828)
val Terracotta800 = Color(0xFF532B24)
val Terracotta900 = Color(0xFF40201B)
val TerracottaDashed = Color(0xFFDF6A55)
val OnTerracotta = Color.White

// 副 accent：鼠尾草绿（正常/健康语义）
val Sage100 = Color(0xFFF0FAE1)
val Sage200 = Color(0xFFCCF3D8)
val Sage300 = Color(0xFFCCDBB2)
val Sage400 = Color(0xFFAEBF92)
val Sage500 = Color(0xFF45996C)
val Sage600 = Color(0xFF357D58)
val Sage700 = Color(0xFF006436)
val Sage800 = Color(0xFF1B412A)
val Sage900 = Color(0xFF272E1B)
val SageLine = Color(0xFF45996C)            // 折线舒张压

// 错误（暖化的柔和红，设计稿未定义，取与暖色系协调的值）
val WarmError = Color(0xFFB3261E)
val WarmOnError = Color(0xFFFFF7EF)
val WarmErrorContainer = Color(0xFFF9DEDC)
val WarmOnErrorContainer = Color(0xFF410E0B)
