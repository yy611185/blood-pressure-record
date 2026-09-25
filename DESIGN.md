---
name: 血压记录
description: 根目录 UIredesign 原型在 Android Compose 中的暖色记录界面
colors:
  primary: "#DF6A55"
  primary-soft: "#FFDED5"
  primary-ink: "#963828"
  on-primary: "#FFFFFF"
  healthy: "#45996C"
  healthy-soft: "#CCF3D8"
  background: "#FAF6EF"
  surface: "#FFFDFA"
  surface-soft: "#F2ECE3"
  text: "#27201B"
  text-secondary: "#645C55"
  divider: "#E3DDD5"
  dark-background: "#1A1512"
  dark-surface: "#26201C"
  dark-surface-soft: "#2D2823"
  dark-text: "#F2EEE7"
  risk: "#B3261E"
typography:
  display:
    fontFamily: "Bricolage Grotesque, sans-serif"
    fontSize: "54sp"
    fontWeight: 700
    lineHeight: "54sp"
    letterSpacing: "-1sp"
    fontFeature: "tnum"
  headline:
    fontFamily: "system-ui, sans-serif"
    fontSize: "30sp"
    fontWeight: 700
    lineHeight: "38sp"
  title:
    fontFamily: "system-ui, sans-serif"
    fontSize: "22sp"
    fontWeight: 600
    lineHeight: "28sp"
  body:
    fontFamily: "system-ui, sans-serif"
    fontSize: "16sp"
    fontWeight: 400
    lineHeight: "24sp"
  label:
    fontFamily: "system-ui, sans-serif"
    fontSize: "13sp"
    fontWeight: 500
    lineHeight: "18sp"
rounded:
  input: "16dp"
  reading-input: "24dp"
  button: "20dp"
  card: "26dp"
  hero: "32dp"
  pill: "50%"
spacing:
  xs: "4dp"
  sm: "8dp"
  md: "12dp"
  lg: "16dp"
  xl: "24dp"
  xxl: "32dp"
  page-gutter: "20dp"
components:
  button-primary:
    backgroundColor: "{colors.primary}"
    textColor: "{colors.on-primary}"
    rounded: "{rounded.button}"
    minHeight: "54dp"
  button-secondary:
    backgroundColor: "{colors.surface-soft}"
    textColor: "{colors.text}"
    rounded: "{rounded.button}"
    minHeight: "54dp"
  data-card:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.text}"
    rounded: "{rounded.card}"
    padding: "18dp"
  status-chip:
    rounded: "{rounded.pill}"
    padding: "5dp 9dp"
  reading-input:
    rounded: "{rounded.reading-input}"
    arrangement: "one prominent systolic field above separate diastolic and pulse fields"
    prominentMinHeight: "132dp"
    secondaryMinHeight: "106dp"
  bottom-navigation:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.text-secondary}"
    rounded: "30dp"
    height: "72dp"
---

# Design System: 血压记录

## Overview

**Creative North Star: "温暖的血压记录本"**

视觉依据是 [`UIredesign/血压记录 重构.html`](UIredesign/血压记录%20重构.html) 及同目录的 `bp*.jsx`、`bp.css`。根目录原型是本次选定稿；`UIredesign/v1-原稿` 和 [`docs/ui-redesign-2026-09-25/directions.md`](docs/ui-redesign-2026-09-25/directions.md) 只保留历史。原型以柔和纸色、圆角卡片、清晰的大数字和珊瑚橙操作形成连续的记录体验。Android 使用真实数据和系统控件，具体差异见 [`IMPLEMENTATION.md`](docs/ui-redesign-2026-09-25/IMPLEMENTATION.md)。

**Key Characteristics:**

- 浅色奶白底与略亮的卡片，深色保留暖棕层次。
- 血压大数字用 Bricolage Grotesque；中文与长篇正文使用系统中文字体。
- 底部四个页面入口围绕居中的新增记录按钮，状态不单靠颜色表达。

## Colors

frontmatter 记录当前 Compose 的 sRGB 映射；根目录 `bp.css` 中的 OKLCH 和默认 `--ah: 32` 是视觉来源。`Color.kt`、`Theme.kt` 是 Android 运行时颜色事实。`primary` 用于新增、保存、选中项；`healthy` 用于正常状态。背景、卡片、次级表面逐层区分；深色以独立表面值切换。分级芯片使用六级色，风险状态优先显示文字和图标，阈值由领域代码决定。

**The Status Text Rule.** 所有血压等级和高风险提示同时写明状态，不能只给色块。

## Typography

最近一次读数、统计数字使用打包的 Bricolage Grotesque 和等宽数字；中文页面标题、表单和说明使用 Android 系统字体。网页原稿用 Noto Sans SC 作为中文字体；原生实现依靠设备的中文字体回退。字体文件在 `app/src/main/res/font/bricolage_grotesque.ttf`，SIL OFL 1.1 许可文本在 `app/src/main/assets/licenses/bricolage_grotesque_license.txt`。大字选项在原生界面提高字体缩放，并须和系统字体缩放一起检查布局。

**The Number First Rule.** 数值、mmHg、日期和状态要能一眼对应；缩小屏幕时先让内容换行与滚动，不能截断读数。

## Layout

页面左右留白 20dp；常见间距来自 `AppSpacing` 的 4/8/12/16/24/32dp。卡片内边距 18dp。首页优先呈现记录入口与最近读数；历史保留日期和原始记录；趋势保留聚合视图及详情入口。底部是首页、历史、趋势、我的四个入口加居中新增按钮；趋势可按原有设置隐藏。原型的 402×874px 手机外框、假状态栏和假手势条不属于 Android 页面布局。

## Elevation & Depth

浅色卡片轻微抬起，导航和居中新增按钮更明显；深色主要靠不同明度的表面分层。原型的整机外框投影只用于网页展示，不搬入应用内容。

## Shapes

通用输入区域约 16dp；读数页三个独立输入格各为 24dp 圆角，收缩压格在上且最小高度 132dp，舒张压与脉搏格在下且最小高度 106dp。主按钮约 20dp、卡片约 26dp、首页主卡片约 32dp；状态芯片为胶囊。触摸目标至少 48dp。大圆角和宽松留白为大字与状态说明留出空间。

## Components

主按钮是珊瑚橙底与高对比文字。`AppPrimaryButton`、次级和危险按钮使用至少 54dp 高度并随大字内容增高；录入底部保存按钮按页面布局为 58dp。次级按钮用柔和表面。数据卡片承载读数和历史条目。分级芯片含色点或风险图标与明确文字。原生数字输入框调用系统数字 IME；底部导航为 72dp、30dp 圆角，中央新增入口为 64dp，随系统栏 inset 排布。录入主线为静坐、读数、情况、完成四步，编辑记录和草稿沿用既有数据规则。根目录原稿的友好文案与“小压”语气是本次选定表达，不受旧产品文档的“中性措辞”偏好限制；真实姓名和健康数据仍只取本地状态。

## Do's and Don'ts

- Do：以根目录原型对照页面层级、颜色和形状；以领域源码判断记录和分级结果。
- Do：在小屏、大字、深浅色、空数据和真实数据下检查可读性与键盘避让。
- Don't：把原型样例数据或演示控件当作持久化功能。
- Don't：用 `v1-原稿` 覆盖本次已选定的视觉基准。
