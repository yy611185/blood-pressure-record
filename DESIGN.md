---
name: 血压记录
description: Android Compose 暖色记录界面的当前设计说明
version: "2.3.13"
colors:
  primary: "#B94B39"
  primary-soft: "#FFDED5"
  primary-ink: "#963828"
  on-primary: "#FFFFFF"
  healthy: "#357D58"
  healthy-soft: "#CCF3D8"
  background: "#FAF6EF"
  surface: "#FFFDFA"
  surface-soft: "#F2ECE3"
  text: "#27201B"
  text-secondary: "#645C55"
  outline: "#8A8177"
  divider: "#E3DDD5"
  chart-pulse: "#2E7D7B"
  dark-primary: "#FFA997"
  dark-on-primary: "#40201B"
  dark-background: "#1A1512"
  dark-surface: "#26201C"
  dark-surface-soft: "#2D2823"
  dark-text: "#F2EEE7"
  dark-outline: "#7F7973"
  dark-chart-pulse: "#80CBC4"
  risk: "#B3261E"
typography:
  display:
    fontFamily: "Bricolage Grotesque, sans-serif"
    fontSize: "58sp"
    fontWeight: 800
    lineHeight: "62sp"
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
  extra-small: "12dp"
  input: "16dp"
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
    rounded: "{rounded.card}"
    padding: "18dp"
  reading-input:
    arrangement: "compact systolic, diastolic and optional pulse fields"
    minHeight: "76dp"
    rounded: "20dp"
  bottom-navigation:
    rounded: "50%"
    height: "64dp"
    centerButton: "52dp"
---

# 设计系统：血压记录

## 当前状态与依据

当前源码版本为 **2.3.13**（versionCode 54）。设计方向是“温暖的血压记录本”：纸色背景、暖色表面、醒目的读数和珊瑚色操作。视觉来源是 [根目录原型](UIredesign/血压记录%20重构.html)及其 `bp*.jsx`、`bp.css`；Android 使用真实数据、系统栏与原生数字键盘。当前行为和数值以 `app/src/main` 为准。

首页、记录、历史、趋势、我的及其相关子页均有原生实现；下文描述源码中已实现的界面，而非对所有设备的验收结论。[初轮实现记录](docs/ui-redesign-2026-09-25/IMPLEMENTATION.md)和[设计方向记录](docs/archive/directions.md)保留当时的决策、截图及测试结果，其中尺寸和流程可能已过时。`UIredesign/v1-原稿` 是更早的历史稿。

## 颜色与文字

frontmatter 列出浅色主题主要 sRGB 值；运行时映射以 `ui/theme/Color.kt`、`DesignSystem.kt` 和 `Theme.kt` 为准。浅色主题操作主色为 `#B94B39`，浅色描边 `outline` 采用 `#8A8177`（深色保持 `#7F7973`），保障输入框与次级控件边界对比度。趋势图收缩压线为暖橙色、舒张压线为蓝色、脉搏线采用独立青绿中性指标色（浅色 `#2E7D7B` / 深色 `#80CBC4` 暂定候选）；医生报告使用固定印刷色 `#C4513E` 和 `#1F5FA8`。正常语义使用鼠尾草绿，错误和高风险使用错误色；血压分级另有偏低（深色字色 `#A3A0DE`）、正常、正常高值、1/2/3 级六套颜色。等级和风险同时以文字或图标表达，阈值以领域代码为准。

读数与统计数字使用离线打包的 Bricolage Grotesque；`AppTypography` 完整定义 Material 3 全部 15 个字阶槽位（`displayLarge 58/62sp` 至 `labelSmall 12/16sp`，中文 `letterSpacing = 0.sp`），主题大读数样式及显式调用 `tabularNums()` 的文本启用等宽数字。常规辅助与标签字号不低于 `12sp`，仅底部 Dock、密集图表刻度及紧凑小部件允许在空间受限时使用 `11sp` 兜底。应用大字模式将系统字号倍率乘以 1.15，合成倍率上限为 2.0，并在冷启动入口采用轻量偏好流首帧门控消除默认字号/主题闪跳。

## 布局与组件

页面水平边距统一为 20dp，常用间距为 4/8/12/16/24/32dp，通用卡片内边距为 18dp。`AppShapes` 采用单调递增圆角阶梯：`extraSmall = 12dp`、`small = 16dp`（普通输入框与搜索框）、`medium = 20dp`（大数字框、主次按钮、行级卡与分段器外框）、`large = 26dp`（标准内容卡与设置分组）、`extraLarge = 32dp`（首页情绪大卡）。公共主、次级和危险按钮统一绑定 `AppDimensions.primaryButtonHeight = 54dp`，录入保存按钮保留 `saveButtonHeight = 58dp`；App 内独立可点击区域最小触控高度为 48dp（7 列月历保留 38dp 视觉格与窄屏单列等分宽度几何例外）。

历史、趋势、我的三个工具页共享 `68dp` `AppTopBar` 与一致的首屏基准线（首页保留独立双行日期与问候语；历史页保留 `hideOnScroll` 召回搜索，首页、趋势、我的随内容自然滚出）。全站分段选择统一复用 `SegmentedPillGroup`（`Primary 16sp` 浅陶土滑块 / `Compact 13sp` 白底滑块），窄屏或大字号下自动排布为均衡多行网格；趋势选点栏采用 `FlowRow` + `AppCompactActionPill` 自适应折行。顶级页面悬浮 Dock 胶囊高 64dp、中央按钮直径 52dp，采用对称滚动收展阈值（暂定 28dp）并在页面回到顶部时自动展开。跨页面轻提示汇总至根 `Scaffold` 单通道 `SnackbarHost`，确保删除撤销严格优先且不被普通提示误清栈。

## 主要交互

- 新增和编辑采用无卡片包裹的满宽三栏多组表单（仅可删除组标题保留 48dp 最小高），校验错误就近展示在字段下方及保存按钮正上方并支持 `LiveRegionMode.Polite` 播报；离开确认与清空本地数据确认两处三动作弹窗采用纵向全宽按钮栈。
- 首页 `ReadingHero` 全卡内容共线于 20dp 内轴，小压与气泡共同支持点击切换提示；四色区间条配柔和衬底与「个人参考区间 / 已自定义」定界标识，明确区分于《中国高血压防治指南》六级分级。首页待服药按钮采用暖色主容器强调，药品编辑提供纯文本剂量单位快捷辅助。
- 历史日历采用互斥单层边框（保留「今天」身份标识）、带细描边的六级图例与中性备注标记，取消隐式双击并在选中含备注日期时提供显式「定位备注」入口。趋势页首屏直接常驻展示主图，个人目标数值动态外置至图例，达标率分布条采用外层统一圆角裁剪并严格保持真实次数比例（不设人工最小段宽）。
- 数据管理页保留「导出明文 Excel（`.xlsx`）」为主按钮并在第一层常驻展示未加密风险说明，单卷明文准备完成后直接拉起一次系统文件保存选择器（分卷保留进度引导与跨格式切换保护）；清空弹窗点击「先导出备份」仅关闭弹窗并滚动定位至导出区。医生报告预览页顶部采用单一事实源的逐项隐私状态摘要条（支持原地展开整行开关），四路输出严格绑定通过门控的同一版快照。

## 动效与无障碍

`AppMotion.kt` 集中定义按压、切换、折叠、Dock、图表等动效：位移和缩放采用弹簧，透明度与颜色采用缓动；主题切换覆盖 28 个颜色通道同步 200ms 过渡。开启减少动态效果时，`Modifier.pressableScale` 与滚轮时间选择器均提供 0ms 即时按压视觉反馈且不启动动画协程。开关与复选框行统一支持整行 `.toggleable`，趋势达标率、历史列表与详情页均向 TalkBack 输出自然语序与完整计量单位。

## 相关资料

- [需求与方案](docs/specs/需求和方案.md)：产品需求与方案背景。
- [UI 审计最终决策记录](docs/design-audit/decisions/DESIGN-DECISION-RECORD.md)与[设计系统标准终稿](docs/design-audit/decisions/DESIGN-SYSTEM-STANDARD-FINAL.md)：2.3.13 轮次 UI/UX 优化权威规范。
- [UI 重设计记录](docs/ui-redesign-2026-09-25/README.md)、[UI 审查归档](docs/archive/ui-review-2026-10-13/REVIEW.md)与[动效优化计划](docs/archive/MOTION_OPTIMIZATION_PLAN.md)：历史阶段资料，具体行为以当前源码为准。
