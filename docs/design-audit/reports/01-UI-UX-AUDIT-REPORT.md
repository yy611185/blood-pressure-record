# Blood Pressure Record（家庭血压记录）UI/UX 系统性设计审计报告

- **审计对象**：Blood Pressure Record（`com.yang.bloodpressure`，版本 `v2.3.13` / `versionCode 54`）
- **技术体系**：Android（`minSdk 26` / `targetSdk 36`）· Kotlin · Jetpack Compose（BOM `2024.10.00`）· Material 3 · Glance AppWidget · 原生 `PdfDocument`
- **上下文画像（Context Profile）**：`mobile-app`（Android 原生健康管理与中老年家庭血压记录应用，叠加适老化、健康数据严谨性、高频单手录入及离线隐私信任修饰器）
- **审计基准**：
  1. **Design Critique Agent（`docs/design-audit/design-audit/`）**：六大审计维度（Visual Design、UX Heuristics、UI Patterns、Human Factors、UX Copy、Information Architecture）、`mobile-app.md` 移动端规范、Nielsen 10 项可用性启发式、WCAG 2.1 AA 无障碍标准及 ASK 反馈框架。
  2. **Design Review Plugin（`docs/design-audit/design-review-plugin/`）**：FormFactor 设计学院像素级审查标准（标准尺寸阶梯 `2/4/8/12/16/20/24/32/40/48/56/64`、接近性法则、矩形原则、对齐线连续性、显式行高、脏色与微弱差异排查）。
  3. **Android Jetpack Compose & Material 3 平台规范**：边到边沉浸式（Edge-to-Edge）、48×48dp 最小触控目标、状态层（State Layer / Indication）、字体缩放（Font Scale）与动态无障碍语义（Semantics）。
- **输入证据**：用户提供的 6 张实机浅色模式界面截图（覆盖首页、历史日历、历史近期、趋势首屏与悬浮 Dock、趋势全页长图、我的页长图）+ `app/src/main` 全量 UI/Theme/Navigation/Domain/Widget/Report 源码。

---

## 一、总体评价（Executive Summary）

### 1.1 整体印象

**Blood Pressure Record** 具备远超一般个人健康工具的产品诚意与工程完成度。项目确立了鲜明且克制的**“温暖纸本血压记录”**视觉基调（`#FAF6EF` 暖纸底、`#FFFDFA` 奶白卡片、`#B94B39` 陶土红主色与鼠尾草绿健康色），在中老年核心痛点（多组读数自动连跳录入、离线本机存储安全感、跨天草稿恢复、删除全局撤销、减少动态效果 `0x` 降级、锁屏服药通知隐私保护及符合《中国高血压防治指南》的分级表达）上展现了扎实的交互功底。

但从**设计系统架构与像素级工艺**视角审视，项目在多次迭代（`v1.7` → `v2.0` → `v2.3.13`）后出现了典型的**“局部打补丁导致的系统性漂移”**：
1. **客观可用性与无障碍短板（P0）**：趋势页读数栏第 5 个操作按钮「复位视野」在常规字号下即被截断为**「复位视」**（截图 #4、#5 实锤）；浅色主题将 `outline` 错误等同于淡分割线色（对比度 **1.33:1**），且数字输入框硬编码描边实测仅 **2.15:1**（未达注释宣称的 `≥3:1`）；多处控件移除默认波纹后仅靠缩放反馈，导致开启「减少动态效果」时彻底失去按压反馈。
2. **跨组件规范分裂与语义冲突（P1）**：Material 3 `Shapes` 阶梯倒置（`medium 26dp > large 20dp`），全应用衍生出 3 种输入框圆角与 6 种卡片圆角；存在两套字号与内边距不一的分段胶囊组件；「我的」页达标值磁贴存在不可点击死区；历史日历「今天+选中」双重描边叠色变脏；脉搏折线直接复用收缩压陶土红色；首页同屏混排六级医学分级与四段自定义区间条缺乏边界提示。

### 1.2 必须保留的核心设计亮点（Strengths to Preserve）

优化过程中应**严格保留**以下高价值设计资产，切勿为追求机械统一而破坏：

1. **高效低负荷的多组录入流**：`CompactNumberField` 基于生理上限前缀（高压 `30`、低压 `20`、脉搏 `25`）的末尾单字符自动跳格逻辑（`SessionFormLogic.kt#L51-L74`），并在 TalkBack 触摸探索模式下自动关闭跳焦点，兼顾了明眼用户极速录入与视障用户焦点稳定。
2. **严谨的医学分级与急症高风险双轨机制**：`BloodPressureRules.kt` 严格遵循《中国高血压防治指南》“就高不就低”规则，并将任一原始组或均值 `≥180/120 mmHg` 独立标记为高风险读数，不因平均值摊薄而漏报单次极高值。
3. **完善的容错与隐私安全闭环**：删除记录与删除药品均接入跨生命周期 `DeletionUndoManager` 底部撤销；医生报告默认关闭「姓名与年龄」且每次重进强制重置（`ReportViewModel.kt#L100-L116`）；服药通知在锁屏隐藏药名与剂量，进入应用核对后再打卡。
4. **温暖亲和的情感化表达与数字排版意识**：「小压」（`Buddy`）随分级切换情绪状态而不过度喧宾夺主；核心血压大数字离线打包 `Bricolage Grotesque` 并广泛开启 `tnum` 等宽数字特性，防止数值变化引发同行单位抖动。
5. **系统级无障碍与动效意识**：`AppMotion.kt` 监听系统 `ANIMATOR_DURATION_SCALE == 0` 自动切换为 `0ms` 落位；`MainActivity.kt` 将应用大字模式（`1.15x`）与系统字号合成并钳制在 `2.0x` 上限，避免极端溢出崩溃。

---

## 二、审计覆盖范围与方法说明

### 2.1 页面与模块覆盖矩阵

| 模块分类 | 页面 / 组件名称 | 对应源码文件 | 实机截图覆盖 | 审计深度 |
| --- | --- | --- | --- | --- |
| **主题与设计系统** | 色彩、字体、间距、圆角、状态色、区间色、动效 | `ui/theme/Color.kt`, `Type.kt`, `DesignSystem.kt`, `Theme.kt`, `BpRangeTokens.kt`, `AppMotion.kt` | 间接覆盖（浅色） | 源码 + WCAG 数学测算 + 截图核验 |
| **全局框架与导航** | 根容器、转场、悬浮胶囊 Dock、安全区 Insets、全局 Snackbar | `MainActivity.kt`, `navigation/AppDestination.kt`, `AppNavigation.kt`, `ui/common/AppBottomDock.kt`, `AppInsets.kt` | 截图 #1–#6（特别是 #4） | 源码 + 截图核验 |
| **公共组件库** | 按钮、卡片、顶栏、分段胶囊、折叠区、表单控件、轮盘时间选择 | `ui/common/AppComponents.kt`, `ExpandableSection.kt`, `HideOnScroll.kt`, `SessionFormComponents.kt`, `WheelTimePicker.kt` | 部分覆盖（卡片/胶囊/折叠区） | 源码全量审查 + 截图核验 |
| **首页（测量概览）** | 问候头、小压与最近读数大卡、四色区间条、今日早晚槽、今日服药、本周区间卡 | `ui/home/DashboardScreen.kt`, `Buddy.kt`, `BpRangeBar.kt`, `WeekCard.kt`, `DashboardViewModel.kt` | **截图 #1（完整长图）** | 源码 + 像素级截图核验 |
| **录入与编辑流程** | 新增测量页、编辑测量页、草稿恢复条、未保存退出弹窗、异常/高风险确认弹窗 | `ui/record/AddMeasurementScreen.kt`, `ui/history/EditSessionScreen.kt`, `ui/home/HomeViewModel.kt`, `EditSessionViewModel.kt` | 未含截图（源码确认 + 列入待视觉验证） | 源码全量审查 |
| **历史记录与搜索** | 日历模式、近期（本周/本月）模式、单日列表、备注搜索页、记录详情页、删除确认 | `ui/history/HistoryScreen.kt`, `NoteSearchScreen.kt`, `HistoryDetailScreen.kt`, `HistoryViewModel.kt` | **截图 #2（日历）、截图 #3（近期）** | 源码 + 像素级截图核验 |
| **趋势分析与全屏** | 周期/粒度/指标切换、逐点读数栏、双折线图、脉搏图、期间/昼夜/达标率统计、当日明细面板、横屏全屏页 | `ui/history/TrendScreen.kt`, `TrendChart.kt`, `TrendChartDrawing.kt`, `TrendChartGestures.kt`, `TrendFullscreenScreen.kt`, `TrendInsights.kt` | **截图 #4（单屏含 Dock）、截图 #5（完整长图）** | 源码 + 像素级截图核验 |
| **我的与设置子页** | 个人资料与达标值页、四色条底部面板、提醒与服药页、药品编辑弹窗、日历同步弹窗、数据管理与加密备份弹窗、使用指南、版本更新弹窗 | `ui/settings/SettingsScreen.kt`, `SettingsSubpages.kt`, `SettingsDataManagementScreen.kt`, `AppInfoContent.kt`, `ui/home/BpZoneTargetSheet.kt`, `update/UpdateUi.kt` | **截图 #6（我的主页长图）** | 我的主页截图核验 + 全部子页/弹窗源码审查 |
| **外延触点** | 医生报告配置页、PDF 预览页、PDF A4 渲染器、桌面小部件、系统通知与服药核对弹窗 | `ui/report/ReportOptionsScreen.kt`, `ReportPreviewScreen.kt`, `export/report/PdfReportRenderer.kt`, `ReportTheme.kt`, `widget/BloodPressureWidget.kt`, `reminder/ReminderReceiver.kt`, `ui/home/MedicationReminderConfirmationDialog.kt` | 未含截图（源码确认 + 列入待视觉验证） | 源码全量审查 |

> [!NOTE]
> **关于截图 #5 右侧多个 `+` 悬浮按钮的甄别说明**：在截图 #5（趋势页滚动长截图）右侧中部与底部出现了 3 个相同的红色圆形 `+` 按钮。经核查 `AppNavigation.kt#L878-L914` 与 `AppBottomDock.kt#L128-L181` 源码，全应用仅挂载唯一一个底部 `AppBottomDock`（向下滚动时收缩为右下角 `56.dp` 圆形新增按钮）。长截图中重复出现的 `+` 属于 **Android 系统滚动截屏拼接悬浮固定层（Fixed Overlay）时的图像缝合伪影**，并非应用真实界面重影，本报告不将其误判为代码缺陷。

---

## 三、优先级统计总览

本次审计共识别并归档 **36 项**问题（按性质分为：客观缺陷 17 项、规范偏差 13 项、主观设计建议 3 项、待视觉验证专项 3 项）：

| 优先级 | 定义说明 | 数量 | 占比 | 涉及问题编号 |
| --- | --- | :---: | :---: | --- |
| **P0** | 严重影响核心功能、可访问性或正常使用 | **3** | 8.3% | `AUD-01` – `AUD-03` |
| **P1** | 明显影响用户体验、视觉质量或设计一致性 | **12** | 33.3% | `AUD-04` – `AUD-15` |
| **P2** | 局部体验及视觉细节问题 | **14** | 38.9% | `AUD-16` – `AUD-29` |
| **P3** | 非必要的设计精细化优化建议及待实机视觉验证专项 | **7** | 19.4% | `AUD-30` – `AUD-36` |
| **合计** | — | **36** | **100%** | `AUD-01` – `AUD-36` |

### 按审计维度分布

| 审计维度 | P0 | P1 | P2 | P3 | 小计 |
| --- | :-: | :-: | :-: | :-: | :-: |
| 视觉对齐与布局空间（Visual Alignment & Layout） | 1 | 3 | 3 | 1 | **8** |
| 色彩体系与无障碍对比度（Color & WCAG Accessibility） | 2 | 2 | 1 | 2 | **7** |
| 组件规范与设计令牌（Components & Design Tokens） | 0 | 2 | 2 | 1 | **5** |
| 交互体验与人因工程（Interaction & Human Factors） | 0 | 3 | 3 | 2 | **8** |
| 信息架构与健康专项体验（IA & Health Domain UX） | 0 | 2 | 3 | 0 | **5** |
| 字体排版与 UX 文案（Typography & UX Copy） | 0 | 0 | 2 | 1 | **3** |

---

## 四、跨页面与系统性设计问题清单

本节汇总影响全应用多页面、多组件的底层设计与架构问题；单页专属问题见第五节。

---

### `AUD-02` · 浅色主题 `outline` 误用淡分割线色（1.33:1），且血压输入框描边硬编码色（2.15:1）未达 WCAG 3:1 标准

- **问题性质**：客观缺陷（源码与数学对比度双重确认 + 截图 #6 开关外框印证）
- **优先级**：**P0**
- **所在页面**：跨页面（新增测量、修改读数、我的、用户资料、提醒与服药、四色条设置面板、备注搜索、数据管理及所有含输入框/开关的弹窗）
- **相关组件**：`LightColors`、`CompactNumberField`、`DateTimePillButton`、`OutlinedTextField`、`OutlinedButton`、`Switch`、`Checkbox`
- **问题描述**：
  1. 在浅色主题 `LightColors` 中，`outline` 与 `outlineVariant` 被同时赋值为 `WarmDivider (#E3DDD5)`。经 WCAG 2.1 相对亮度公式精确测算，`#E3DDD5` 在白卡 `#FFFDFA` 上的对比度仅为 **1.33:1**，在暖纸底 `#FAF6EF` 上仅 **1.25:1**，远低于 WCAG 2.1 SC 1.4.11 对非文本交互控件边界 **≥3.0:1** 的强制门槛。这导致全应用所有默认 `OutlinedTextField`、`DateTimePillButton`、未选中 `Switch`（见截图 #6「大字模式」「深色模式」右侧灰框几乎融于底色）轮廓极度模糊。
  2. `CompactNumberField` 的 KDoc 明确声明*“提升未聚焦描边对比度至 >=3:1（UX-10）”*，但代码通过 `colors.background.red < 0.5f` 嗅探浅色模式后硬编码了 `Color(0xFFB3A99D)`（且上方行内注释误写为 `#C2B8AC`）。实测 `#B3A99D` 在 `#FFFDFA` 上对比度仅 **2.28:1**，在 `#FAF6EF` 上仅 **2.15:1**，仍未达到 3:1 标准。反观深色主题 `DarkColors.outline (#7F7973)` 在 `#26201C` 上对比度为 **3.74:1**，符合标准。
- **源码证据**：
  - [Theme.kt](file:///E:/blood%20pressure%20record/app/src/main/java/com/example/bloodpressurerecord/ui/theme/Theme.kt#L44-L45)：`outline = WarmDivider, outlineVariant = WarmDivider`
  - [SessionFormComponents.kt](file:///E:/blood%20pressure%20record/app/src/main/java/com/example/bloodpressurerecord/ui/common/SessionFormComponents.kt#L496)：注释声称 `>=3:1（UX-10）`
  - [SessionFormComponents.kt](file:///E:/blood%20pressure%20record/app/src/main/java/com/example/bloodpressurerecord/ui/common/SessionFormComponents.kt#L529-L534)：`Color(0xFFB3A99D)` 硬编码及 `colors.background.red < 0.5f` 主题嗅探
  - [SessionFormComponents.kt](file:///E:/blood%20pressure%20record/app/src/main/java/com/example/bloodpressurerecord/ui/common/SessionFormComponents.kt#L284)：`DateTimePillButton` 使用 `1.dp, MaterialTheme.colorScheme.outline`
- **审计依据**：
  - `design-audit/references/ux-heuristics.md`（WCAG 2.1 AA：UI component contrast ≥ 3:1 against adjacent colors；Affordance：Input fields should look editable）
  - `PRODUCT.md`（面向自行记录血压的中老年人，晶状体黄化对低对比暖灰边界尤为敏感）
- **改进方案**：
  1. 在 `Color.kt` 新增语义描边色 `WarmOutline = Color(0xFF8A8177)`（暖灰棕，在 `#FFFDFA` 白卡上对比度 **3.77:1**、在 `#FAF6EF` 纸底上 **3.55:1**、在 `#F2ECE3` 浅灰底上 **3.26:1**，全面通过 3:1），并将 `LightColors.outline` 指向 `WarmOutline`，保留 `LightColors.outlineVariant = WarmDivider (#E3DDD5)` 专供弱分割线使用。
  2. 删除 `CompactNumberField` 中的 `colors.background.red < 0.5f` 判断与 `Color(0xFFB3A99D)` 硬编码，统一使用 `MaterialTheme.colorScheme.outline`（深浅色均自动满足 `>3.5:1`）。
- **预期收益及验证方式**：
  - **收益**：一次性修复全应用所有输入框、日期胶囊、未选中开关与复选框的边界清晰度，消除硬编码与注释失真，显著降低中老年用户找框瞄准难度。
  - **验证**：编写色彩对比度单元测试断言 `LightColors.outline` 与 `surface/background/surfaceVariant` 的对比度均 `≥ 3.0f`；在浅色实机检查录入框与设置页未开启 Switch 外框清晰度。

---

### `AUD-03` · 开启系统「减少动态效果」时，大量自定义 `indication = null` 核心控件丧失全部按压视觉反馈

- **问题性质**：客观缺陷（源码逻辑确认）
- **优先级**：**P0**
- **所在页面**：跨页面（底部悬浮 Dock、首页服药打卡、首页周卡胶囊列、历史日历单元格、历史/趋势/资料分段胶囊、录入快捷词胶囊、轮盘时间选择器）
- **相关组件**：`Modifier.pressableScale`、`AppBottomDock`、`SegmentedPillGroup`、`SegmentedControl`、`SessionChoiceChip`、`MedicationRow`、`WeekRangeDayColumn`、`CalendarDay`、`Buddy`、`WheelTimePickerDialog`
- **问题描述**：
  为避免默认直角波纹溢出或与按压缩放叠加，项目在大量核心自定义控件的 `clickable` / `selectable` 中传入了 `indication = null`，将按压态反馈完全委托给 `Modifier.pressableScale`。然而 `pressableScale` 首行写了 `if (reducedMotion || !enabled) return this`；一旦用户在系统无障碍设置中开启「移除动画 / 减少动态效果」（`ANIMATOR_DURATION_SCALE == 0`），`pressableScale` 直接退化为空操作，而这些控件（除 `AppBackIconButton` 和 `TrendDayRecordRow` 自带按下变色外）既无波纹/状态层（State Layer），也无缩放或透明度变化，手指按下瞬间呈“死屏无响应”感。此外，`WheelTimePicker.kt` 的滚轮条目即便在常规模式下也设置了 `indication = null` 且无按压态高亮。
- **源码证据**：
  - [AppComponents.kt](file:///E:/blood%20pressure%20record/app/src/main/java/com/example/bloodpressurerecord/ui/common/AppComponents.kt#L262-L280)：`if (reducedMotion || !enabled) return this`
  - [AppBottomDock.kt](file:///E:/blood%20pressure%20record/app/src/main/java/com/example/bloodpressurerecord/ui/common/AppBottomDock.kt#L221-L228) & [#L292-L300](file:///E:/blood%20pressure%20record/app/src/main/java/com/example/bloodpressurerecord/ui/common/AppBottomDock.kt#L292-L300)：中央新增按钮与底部 Tab 均为 `indication = null`
  - [AppComponents.kt](file:///E:/blood%20pressure%20record/app/src/main/java/com/example/bloodpressurerecord/ui/common/AppComponents.kt#L764-L772)：`SegmentedPillOption` 为 `indication = null`
  - [DashboardScreen.kt](file:///E:/blood%20pressure%20record/app/src/main/java/com/example/bloodpressurerecord/ui/home/DashboardScreen.kt#L879-L889)：`MedicationRow` 打卡按钮为 `indication = null`
  - [HistoryScreen.kt](file:///E:/blood%20pressure%20record/app/src/main/java/com/example/bloodpressurerecord/ui/history/HistoryScreen.kt#L828-L834)：`CalendarDay` 为 `indication = null`
  - [WheelTimePicker.kt](file:///E:/blood%20pressure%20record/app/src/main/java/com/example/bloodpressurerecord/ui/common/WheelTimePicker.kt#L338-L344)：滚轮项 `indication = null` 且无按压反馈
- **审计依据**：
  - `design-audit/references/mobile-app.md`（Android Signals & Checks：Interactive elements missing state layer / ripple feedback signals）
  - `design-audit/references/ux-heuristics.md`（#1 Visibility of System Status）
- **改进方案**：
  1. 升级 `Modifier.pressableScale`（或提供配套 `Modifier.pressableFeedback(interactionSource, shape)`）：在常规模式下执行弹簧缩放；在 `reducedMotion == true` 时不启动缩放协程，改为基于 `collectIsPressedAsState()` 同步叠加 `0ms` 瞬态 M3 状态层（如在 `drawWithContent` 中按 `shape` 叠加 `onSurface.copy(alpha = 0.10f)` 遮罩或将 `graphicsLayer.alpha` 置为 `0.82f`），保证零动画协程的同时保留即时按压确认。
  2. 为 `WheelColumn` 条目补充裁剪后的 `LocalIndication.current` 或按压底色高亮。
- **预期收益及验证方式**：
  - **收益**：在常规与无障碍减少动态效果双模式下，100% 交互控件均具备符合 M3 标准的即时触控反馈。
  - **验证**：在 `InteractionAndMotionRegressionTest` 中开启 `LocalReducedMotion provides true`，对 Dock 按钮、分段胶囊、日历格执行 `performTouchInput { down(center) }`，断言存在可见按压状态层且无挂起动画。

---

### `AUD-07` · Material 3 `AppShapes` 形状阶梯倒置（`medium = 26.dp > large = 20.dp`），导致输入框与卡片圆角跨页失序

- **问题性质**：客观缺陷 / 设计系统架构冲突（源码确认）
- **优先级**：**P1**
- **所在页面**：跨页面（全局主题及搜索、录入、历史、趋势、我的、说明子页）
- **相关组件**：`AppShapes`、`DataCard`、`OutlinedTextField`、`Card`、`Surface`
- **问题描述**：
  1. **M3 形状语义倒置**：`Theme.kt` 定义 `extraSmall = 12.dp`、`small = 16.dp`、`medium = 26.dp`、`large = 20.dp`、`extraLarge = 32.dp`，导致 `medium (26dp)` 反而比 `large (20dp)` 大 `6dp`，违背 Material 3 `Shapes` 从小到大递增的契约。
  2. **输入框圆角分裂为 3 套**：资料/药品/备份口令/四色条的 `OutlinedTextField` 未传 `shape`（使用 M3 默认 `extraSmall = 12.dp`）；血压数字框与补充情况框显式传 `shapes.large` / `20.dp`；备注搜索页顶栏输入框却传了 `shapes.medium = 26.dp`。
  3. **卡片圆角分裂为 6 套（微弱差异反模式）**：全应用并存 `32.dp`（首页大卡）、`26.dp`（`DataCard`、周卡、趋势主卡）、`24.dp`（设置分组、药品卡、更新日志卡、趋势空状态卡）、`22.dp`（早晚测量槽、服药行、周卡头部、趋势统计小卡、底部隐私卡）、`18.dp`（趋势顶部摘要卡、草稿条、高风险条）、`16.dp`（今日其他时段条、周卡选中日条、达标值磁贴）。其中 `26dp / 24dp / 22dp` 及 `18dp / 16dp` 仅差 `2dp`，肉眼既能察觉不整齐，又无法理解层级差异。
- **源码证据**：
  - [Theme.kt](file:///E:/blood%20pressure%20record/app/src/main/java/com/example/bloodpressurerecord/ui/theme/Theme.kt#L85-L91)：`medium = 26.dp, large = 20.dp`
  - [NoteSearchScreen.kt](file:///E:/blood%20pressure%20record/app/src/main/java/com/example/bloodpressurerecord/ui/history/NoteSearchScreen.kt#L121)：`shape = MaterialTheme.shapes.medium`（26dp 搜索框）
  - [SessionFormComponents.kt](file:///E:/blood%20pressure%20record/app/src/main/java/com/example/bloodpressurerecord/ui/common/SessionFormComponents.kt#L922)：`shape = RoundedCornerShape(20.dp)`
  - [SettingsSubpages.kt](file:///E:/blood%20pressure%20record/app/src/main/java/com/example/bloodpressurerecord/ui/settings/SettingsSubpages.kt#L150) & [#L561](file:///E:/blood%20pressure%20record/app/src/main/java/com/example/bloodpressurerecord/ui/settings/SettingsSubpages.kt#L561) & [#L1054](file:///E:/blood%20pressure%20record/app/src/main/java/com/example/bloodpressurerecord/ui/settings/SettingsSubpages.kt#L1054)：默认 12dp 输入框、24dp 版本卡与默认 26dp 指南卡混排
  - [TrendScreen.kt](file:///E:/blood%20pressure%20record/app/src/main/java/com/example/bloodpressurerecord/ui/history/TrendScreen.kt#L232) & [#L522](file:///E:/blood%20pressure%20record/app/src/main/java/com/example/bloodpressurerecord/ui/history/TrendScreen.kt#L522) & [#L571](file:///E:/blood%20pressure%20record/app/src/main/java/com/example/bloodpressurerecord/ui/history/TrendScreen.kt#L571)：同一页混用 `18.dp`、`22.dp`、`26.dp`
- **审计依据**：
  - `design-review-plugin/skills/design-review/knowledge-base.md`（Консистентность：*"Чуть-чуть отличается — хуже всего"* 微弱差异最伤精致感；Скругления 一致性；标准尺寸线性阶梯）
  - `design-audit/references/visual-design.md`（Consistency：Do similar elements look the same throughout?）
- **改进方案**：
  理顺 `AppShapes` 为严格单调递增的 5 档语义阶梯（详见 `03-DESIGN-SYSTEM-STANDARD.md`）：
  - `extraSmall = 12.dp`（内部嵌块、小徽章、菜单）
  - `small = 16.dp`（统一所有标准文本输入框、次级内嵌条）
  - `medium = 20.dp`（统一主次按钮、分段胶囊外框、中号列表项/服药行/早晚槽）
  - `large = 24.dp`（统一通用数据卡片 `DataCard`、设置分组、趋势卡，消除 `26/24/22dp` 微差）
  - `extraLarge = 32.dp`（首页情绪主卡 `ReadingHero`、全屏模态更新弹窗）
- **预期收益及验证方式**：
  - **收益**：彻底消除跨页面圆角跳跃与 M3 默认组件圆角错乱，使页面视觉秩序立即收拢。
  - **验证**：全局检索 `RoundedCornerShape(`，除胶囊 `50%` 与气泡特殊角外，所有业务卡片与输入框均引用 `MaterialTheme.shapes` 令牌。

---

### `AUD-08` · 存在两套视觉相近但字号、内边距与自适应逻辑不一致的分段选择器组件

- **问题性质**：规范偏差 / 重复造轮子（截图 #2/#3 vs #4 实锤 + 源码确认）
- **优先级**：**P1**
- **所在页面**：历史记录（`HistoryScreen`）、趋势（`TrendScreen`）、用户资料（`SettingsProfileScreen`）
- **相关组件**：`SegmentedPillGroup`（公共）与 `SegmentedControl`（趋势页私有）
- **问题描述**：
  代码库同时维护了两套弹簧滑块分段胶囊：
  - 历史页「日历 / 近期」「本周 / 本月」及资料页「性别」使用公共组件 `SegmentedPillGroup`：外框 `surfaceVariant`、内边距 `4.dp`、文字 **`16.sp` SemiBold**，且具备宽度测量与窄屏大字自动纵向堆叠保护。
  - 趋势页「7天…全部」「每次测量 / 每日平均」「双指标 / 收缩压 / 舒张压」使用 `TrendScreen.kt` 内部私有的 `SegmentedControl`：外框 `surfaceContainerHighest`、非标内边距 **`5.dp`**、文字仅 **`13.sp` Bold/Medium**，且无窄屏堆叠保护。
  对比截图 #3 与截图 #4，用户在底部导航从「历史」切到「趋势」时，同一顶部位置的珊瑚粉分段滑块文字突然从 `16sp` 缩水到 `13sp`，高度与边框厚度也随之微变。
- **源码证据**：
  - [AppComponents.kt](file:///E:/blood%20pressure%20record/app/src/main/java/com/example/bloodpressurerecord/ui/common/AppComponents.kt#L609-L792)：`SegmentedPillGroup`（`16.sp`、`SegmentedPillGap = 4.dp`）
  - [TrendScreen.kt](file:///E:/blood%20pressure%20record/app/src/main/java/com/example/bloodpressurerecord/ui/history/TrendScreen.kt#L875-L965)：私有 `SegmentedControl`（`labelMedium = 13.sp`、`padding(5.dp)`）
- **审计依据**：
  - `design-audit/references/ux-heuristics.md`（#4 Consistency and Standards）
  - `design-review-plugin/skills/design-review/SKILL.md`（Similar elements styled identically OR very differently — never "slightly different"；`5dp` 属非标准尺寸）
- **改进方案**：
  扩展公共 `SegmentedPillGroup` 支持两档规范尺寸变体（`Primary` 主切换：`15.sp`、`4.dp` 内距、`accent = true`；`Compact / Filter` 多项次级筛选：`13.sp–14.sp`、`4.dp` 内距、`accent = false` 白滑块），随后删除 `TrendScreen.kt` 私有的 `SegmentedControl`：「每次测量 / 每日平均」与历史页「日历 / 近期」统一用 `Primary` 档，「7天…全部」与卡片内「双指标/收缩压/舒张压」统一用 `Compact` 档（白底滑块区分主次层级）。
- **预期收益及验证方式**：
  - **收益**：消除约 90 行重复绘制代码，统一跨 Tab 顶部分段器字号与触感，同时拉开页面级主切换与卡内次级筛选的视觉层级（顺带解决 `AUD-17`）。
  - **验证**：在「历史」与「趋势」Tab 间切换，核对顶部分段器高度、圆角、字号与滑块边距一致。

---

### `AUD-13` · 四个顶级页面顶栏高度、标题字号、滚动收起策略及首屏顶距互不统一

- **问题性质**：规范偏差（截图 #1、#2、#4、#6 实锤 + 源码确认）
- **优先级**：**P1**
- **所在页面**：首页（`DashboardScreen`）、历史记录（`HistoryScreen`）、趋势（`TrendScreen`）、我的（`SettingsScreen`）
- **相关组件**：`AppTopBar`、`GreetingHeader`、`TrendScreen` 顶栏 `Row`
- **问题描述**：
  四个平级底部 Tab 页面的顶部结构各自为政：
  1. **顶栏容器与基线高度不一**：「历史记录」与「我的」调用 `AppTopBar`（`heightIn(min = 68.dp)`，`30.sp` 居中）；「血压趋势」未调用 `AppTopBar`，而是在 `Column` 内手写 `Row` 并附加 `statusBarTopPadding(extra = 12.dp)`，无 `68.dp` 最小高度约束（对比截图 #2、#4、#6，可明显看出「血压趋势」标题距状态栏时间 `23:28` 更近，基线高于「历史记录」与「我的」）；「首页」为双行日期+问候语，问候语硬编码 `fontSize = 29.sp`（偏离主题 `30.sp`）。
  2. **标题下方首屏内容间距不一**：「历史记录」`LazyColumn` 顶部 `contentPadding` 为 `0.dp`（靠 `68.dp` 顶栏自身下余量约 `15dp`）；「血压趋势」靠 `Column` `spacedBy(12.dp)`；「我的」在 `68.dp` `AppTopBar` 下方又额外叠加 `.padding(top = 16.dp)`（总间距约 `31dp`，见截图 #6「我的」与资料卡之间留白显著宽于历史页）。
  3. **滚动隐藏行为不一致**：四个顶级页中唯独「历史记录」挂载了 `rememberHideOnScrollState()` 向下滚动收起标题，其余三页标题均随页面自然滚走或常驻。
- **源码证据**：
  - [DashboardScreen.kt](file:///E:/blood%20pressure%20record/app/src/main/java/com/example/bloodpressurerecord/ui/home/DashboardScreen.kt#L143) & [#L278](file:///E:/blood%20pressure%20record/app/src/main/java/com/example/bloodpressurerecord/ui/home/DashboardScreen.kt#L278)：`extra = 12.dp`、`fontSize = 29.sp`
  - [HistoryScreen.kt](file:///E:/blood%20pressure%20record/app/src/main/java/com/example/bloodpressurerecord/ui/history/HistoryScreen.kt#L257-L271) & [#L290-L295](file:///E:/blood%20pressure%20record/app/src/main/java/com/example/bloodpressurerecord/ui/history/HistoryScreen.kt#L290-L295)：`AppTopBar` + `hideOnScroll` + `top = 0.dp`
  - [TrendScreen.kt](file:///E:/blood%20pressure%20record/app/src/main/java/com/example/bloodpressurerecord/ui/history/TrendScreen.kt#L139-L156)：手写 `Row` 替代 `AppTopBar`
  - [SettingsScreen.kt](file:///E:/blood%20pressure%20record/app/src/main/java/com/example/bloodpressurerecord/ui/settings/SettingsScreen.kt#L100-L109)：固定 `AppTopBar`（无滚动收起）+ `padding(top = 16.dp)`
- **审计依据**：
  - `design-audit/references/information-architecture.md`（Wayfinding & Global Navigation Consistency）
  - `design-review-plugin/skills/design-review/knowledge-base.md`（Выравнивание & 标准尺寸阶梯：`29sp` 为非标奇数）
- **改进方案**：
  1. 「历史记录」「血压趋势」「我的」三个标准顶级页统一使用 `AppTopBar`（`30.sp` `headlineLarge`），并将顶栏到首块内容的净间距统一校准为 `8.dp`（移除 `SettingsScreen` 多余的 `top = 16.dp` 差值，`TrendScreen` 改调 `AppTopBar(title = "血压趋势", actions = { ... })`）。
  2. 顶级页统一滚动策略：推荐将顶栏放入滚动流顶部自然随内容滚出（如首页与趋势页），或三页统一接入 `hideOnScroll`，避免「我的」页 `68.dp` 顶栏永久死占竖屏空间而「历史」页却收起。
  3. 首页问候语恢复使用标准 `MaterialTheme.typography.headlineLarge`（`30.sp`），消除 `29.sp` 硬编码。
- **预期收益及验证方式**：
  - **收益**：底部 Tab 平级切换时，页面大标题基线严丝合缝处在同一水平线上，首屏纵向节奏完全一致。
  - **验证**：截取历史、趋势、我的三页首屏叠图比对标题 Y 坐标偏差 `= 0px`。

---

### `AUD-15` · 多重 `SnackbarHost` 同位叠加存在重叠风险，且深浅色切换动画遗漏 `secondary / outline / error` 颜色通道

- **问题性质**：客观缺陷（源码确认）
- **优先级**：**P1**
- **所在页面**：跨页面（`BloodPressureAppRoot`、`DashboardScreen`、`AddMeasurementScreen`、`EditSessionScreen`、`Theme.kt`）
- **相关组件**：`SnackbarHost`、`animateColorScheme`
- **问题描述**：
  1. **双层 Snackbar 坐标重叠**：根 `Scaffold`（`AppNavigation.kt#L449-L453`）已在 `bottom = dockContentBottomPadding()` 处挂载了 `snackbar` 与 `undoSnackbar`；但 `DashboardScreen.kt#L253-L258` 又在完全相同的 `Alignment.BottomCenter.padding(bottom = bottomInset)` 处挂载了一个页面级 `SnackbarHost`（用于服药打卡撤销）。若用户在历史详情删除一条记录（触发全局 10 秒 `undoSnackbar`）后切回首页点击服药打卡，两条 Snackbar 将在屏幕底部同一位置前后叠压渲染。
  2. **主题切换颜色过渡残缺**：`Theme.kt` 的 `animateColorScheme` 仅对 `primary`、`background`、`surface`、`surfaceVariant`、`surfaceContainer*` 做了 `200ms` 动画，遗漏了 `secondary`、`secondaryContainer`、`onSecondaryContainer`、`tertiary*`、`error*`、`outline`、`outlineVariant`。当用户在「我的」页点击「深色模式」开关时，本页 5 个薄荷绿圆形图标底座、底部绿色隐私说明卡及 4 条分割线会 `0ms` 瞬间跳变，而卡片与背景却在 `200ms` 渐变，造成肉眼可见的色彩撕裂。
- **源码证据**：
  - [AppNavigation.kt](file:///E:/blood%20pressure%20record/app/src/main/java/com/example/bloodpressurerecord/navigation/AppNavigation.kt#L449-L453) vs [DashboardScreen.kt](file:///E:/blood%20pressure%20record/app/src/main/java/com/example/bloodpressurerecord/ui/home/DashboardScreen.kt#L253-L258)
  - [Theme.kt](file:///E:/blood%20pressure%20record/app/src/main/java/com/example/bloodpressurerecord/ui/theme/Theme.kt#L118-L150)：`animateColorScheme` 仅覆盖 14 个颜色属性
- **审计依据**：
  - `design-audit/references/ui-patterns.md`（Notifications & Toasts：Position consistent, never overlap）
  - `DESIGN.md`（动效规范：主题颜色过渡为 200ms）
- **改进方案**：
  1. 移除 `DashboardScreen` 内部的独立 `SnackbarHost`，将服药打卡反馈事件上浮至 `BloodPressureAppRoot` 统一通过根 `Scaffold` 队列串行展示（新提示触发前按需处理当前非撤销类提示，或在同一 `Column` 中垂直排队永不重叠）。
  2. 在 `animateColorScheme` 中补齐 `secondary`、`onSecondary`、`secondaryContainer`、`onSecondaryContainer`、`tertiaryContainer`、`onTertiaryContainer`、`error`、`errorContainer`、`onErrorContainer`、`outline`、`outlineVariant` 的同步 `200ms` 过渡。
- **预期收益及验证方式**：
  - **收益**：根除极端跨页操作下的底部提示条重影，使「我的」页深浅色模式切换丝滑一体。
  - **验证**：在「我的」页反复切换深色模式观察绿色徽章与分割线过渡；执行「删除记录 → 切首页 → 服药打卡」验证两条提示垂直排队或有序接替。

---

### `AUD-18` · `AppTypography` 漏定义 5 个实际在用的 M3 字阶，且存在多处小于 `12sp` 的微缩字号与英文默认字距污染

- **问题性质**：规范偏差（源码确认 + 截图核验）
- **优先级**：**P2**
- **所在页面**：跨页面（首页、历史、记录详情、趋势、搜索、我的、桌面小部件）
- **相关组件**：`AppTypography`、`BpRangeBar`、`WeekCard`、`AppBottomDock`、`TrendChartDrawing`、`HistoryDetailScreen`、`SessionAverageCard`
- **问题描述**：
  1. `Type.kt` 的 `AppTypography` 仅定义了 15 个 M3 字阶中的 10 个，漏掉了 `displaySmall`（`HistoryDetailScreen.kt#L154` 使用）、`headlineSmall`（`SessionAverageCard`、`HistorySessionCard`、`UpdateDialog` 使用）、`titleSmall`（`HistoryScreen`、`NoteSearchScreen`、`TrendFullscreenScreen`、`UpdateUi` 使用）以及高频调用的 `labelSmall`（全应用 16 处调用）。未定义字阶回退到 M3 英文默认规范，其中 `labelSmall` 默认为 **`11.sp` 且带 `letterSpacing = 0.5.sp`**，不仅偏小，还给中文说明强加了松散字距。
  2. 多处文字硬编码为 `10.sp`–`11.sp`：首页周卡参考轴刻度 `10.sp`、空态星期指示 `10.sp`、趋势图目标标签 `10.sp`、底部主导航胶囊文字 `11.sp`、首页四色条标签 `11.sp`、小部件辅助字 `11.sp`。面向 50 岁以上核心用户群，在未开启大字模式（如当前截图设备 `1.0x`）时，`10–11sp` 明显吃力。
- **源码证据**：
  - [Type.kt](file:///E:/blood%20pressure%20record/app/src/main/java/com/example/bloodpressurerecord/ui/theme/Type.kt#L26-L97)：缺少 `displaySmall / headlineSmall / titleSmall / labelSmall`
  - [BpRangeTokens.kt](file:///E:/blood%20pressure%20record/app/src/main/java/com/example/bloodpressurerecord/ui/theme/BpRangeTokens.kt#L48)：`labelSize = 11.sp`
  - [AppBottomDock.kt](file:///E:/blood%20pressure%20record/app/src/main/java/com/example/bloodpressurerecord/ui/common/AppBottomDock.kt#L263)：`11f` sp 导航文字
  - [WeekCard.kt](file:///E:/blood%20pressure%20record/app/src/main/java/com/example/bloodpressurerecord/ui/home/WeekCard.kt#L239) & [#L457-L463](file:///E:/blood%20pressure%20record/app/src/main/java/com/example/bloodpressurerecord/ui/home/WeekCard.kt#L457-L463) & [#L1008](file:///E:/blood%20pressure%20record/app/src/main/java/com/example/bloodpressurerecord/ui/home/WeekCard.kt#L1008)：`10.sp` / `11.sp`
  - [TrendChartDrawing.kt](file:///E:/blood%20pressure%20record/app/src/main/java/com/example/bloodpressurerecord/ui/history/TrendChartDrawing.kt#L197)：`fontSize = 10.sp`
- **审计依据**：
  - `design-audit/references/mobile-app.md`（Font Size Minimums：Secondary/caption text `12sp` absolute minimum）
  - `design-review-plugin/skills/design-review/knowledge-base.md`（Типографика：`12px, 11px, 8px — мучение для глаз`；显式设置数值行高，收敛字阶数量）
- **改进方案**：
  1. 在 `AppTypography` 补齐全部在用 M3 字阶，显式将中文 `letterSpacing` 归零（`0.sp`），将 `labelSmall` 基准设为 **`12.sp / lineHeight 16.sp`**。
  2. 将底部导航标签、四色条标签、周卡「本周平均 / 日期摘要」文字从 `11.sp` 提升至 `12.sp`（保留大字模式下的上限钳制防溢出）；仅极窄画布内的坐标轴刻度在宽度受限时允许 `11.sp` 兜底，全面淘汰 `10.sp`。
- **预期收益及验证方式**：
  - **收益**：在 `1.0x` 标准字号下全面守住 `12sp` 适老化可读底线，消除 M3 默认 `0.5.sp` 字距造成的中文排版松散。
  - **验证**：审查 `Type.kt` 覆盖率及全局 `.sp` 字面量，确认界面正文与辅助标签无 `<12.sp` 情况。

---

### `AUD-20` · 全应用对同一测量时段与高风险计数存在 5 套不同命名，增加跨页理解成本

- **问题性质**：规范偏差（UX 文案一致性，截图 #1/#2/#3/#5 实锤）
- **优先级**：**P2**
- **所在页面**：首页、历史记录、记录详情、趋势、提醒与服药、医生报告 PDF
- **相关组件**：各页面时段文案与统计项标签
- **问题描述**：
  1. **晨间时段出现 5 种叫法**：同一条 `09:41` 的记录，在首页「今天的测量」显示为**「早上 · 09:41」**（截图 #1），在历史页列表变为**「09:41 上午」**（截图 #2），在趋势页统计归入**「早间平均（05:00–11:59）」**（截图 #5），在设置提醒页叫**「晨间提醒」**，在导出的医生报告 PDF 核心栏又叫**「早晨均值」**。同理，晚间也混用「晚上 / 晚间 / 夜间」三套词。
  2. **量词不统一**：高风险记录在历史近期汇总卡写作**「高风险记录：0 条」**（截图 #3），在趋势页写作**「高风险记录：0 次」**（截图 #5），在首页周卡写作**「含 X 次高风险」**。脉搏缺省值混用 `—`（趋势/详情）与 `--`（历史列表/小部件/趋势日明细）。
- **源码证据**：
  - [DashboardScreen.kt](file:///E:/blood%20pressure%20record/app/src/main/java/com/example/bloodpressurerecord/ui/home/DashboardScreen.kt#L662-L668)（「早上/晚上」）、[MeasurementPeriod.kt](file:///E:/blood%20pressure%20record/app/src/main/java/com/example/bloodpressurerecord/domain/time/MeasurementPeriod.kt#L7-L15)（「凌晨/清晨/上午/下午/晚上」）、[TrendScreen.kt](file:///E:/blood%20pressure%20record/app/src/main/java/com/example/bloodpressurerecord/ui/history/TrendScreen.kt#L329-L355)（「早间/晚间/日间/夜间」）、[SettingsSubpages.kt](file:///E:/blood%20pressure%20record/app/src/main/java/com/example/bloodpressurerecord/ui/settings/SettingsSubpages.kt#L360)（「晨间/晚间」）、[PdfReportRenderer.kt](file:///E:/blood%20pressure%20record/app/src/main/java/com/example/bloodpressurerecord/export/report/PdfReportRenderer.kt#L145)（「早晨均值」）
  - [HistoryScreen.kt](file:///E:/blood%20pressure%20record/app/src/main/java/com/example/bloodpressurerecord/ui/history/HistoryScreen.kt#L527) vs [TrendScreen.kt](file:///E:/blood%20pressure%20record/app/src/main/java/com/example/bloodpressurerecord/ui/history/TrendScreen.kt#L497)；[HistoryViewModel.kt](file:///E:/blood%20pressure%20record/app/src/main/java/com/example/bloodpressurerecord/ui/history/HistoryViewModel.kt#L463)（`"--"` vs `"—"`）
- **审计依据**：
  - `design-audit/references/ux-heuristics.md`（#4 Consistency and Standards：Users should not have to wonder whether different words mean the same thing）
  - `design-audit/references/ux-copy.md`（Labels & Tone Consistency）
  - `design-audit/references/information-architecture.md`（Labeling Systems：same concept = same label throughout）
- **改进方案**：
  建立两层清晰的医学时段术语表（详见 `03-DESIGN-SYSTEM-STANDARD.md`）：
  1. **早晚统计与提醒口径（05:00–11:59 / 18:00–23:59）**：全应用（首页早晚槽副说明、趋势页、提醒设置、PDF 报告）统一称为**「早间 / 晚间」**（如将提醒页「晨间提醒」改为「早间提醒」，PDF「早晨均值」改为「早间均值」，首页槽保留亲切的「早间 · 09:41」或明确与趋势页「早间」对齐）。
  2. **单次记录自然细分时段**：保留 `凌晨 / 清晨 / 上午 / 下午 / 晚上` 五档用于单条记录时间戳旁补充。
  3. **量词与空值符号统一**：测量与高风险计数统一用**「次」**；无数值占位符全局统一为破折号 **`—`**（淘汰双连字符 `--`）。
- **预期收益及验证方式**：
  - **收益**：帮用户（尤其是复诊给医生看报告时）快速把首页早晚打卡、趋势早晚均值与 PDF 报告早晚均值一一对应，消除“早上怎么算上午、早晨均值是不是晨间”的认知摩擦。
  - **验证**：全局检索 `晨间`、`早晨`、`0 条`、`"--"` 并核对替换结果及对应单元测试文案。

---

### `AUD-23` · `SettingsUiState` 默认开启大字模式（`1.15x`），在用户关闭大字模式或指定深浅色时可能引发冷启动首帧跳变

- **问题性质**：客观缺陷（源码确认）
- **优先级**：**P2**
- **所在页面**：全局启动入口（`MainActivity`）
- **相关组件**：`MainActivity`、`SettingsViewModel`
- **问题描述**：
  `MainActivity.setContent` 直接以 `SettingsViewModel.uiState` 的同步初值计算 `LocalDensity.fontScale` 与 `darkTheme`。由于 `SettingsUiState` 静态默认值设为 `isLargeTextEnabled = true`、`appearanceMode = "system"`，而 DataStore 为异步读取：当用户实际已在设置中**关闭**大字模式（如本次全部 6 张截图的真实设备状态！）或手动固定了深/浅色主题时，冷启动第 1 帧会先按 `1.15x` 大字号与系统主题组合排版，待数毫秒后 DataStore 首帧发射再跳回 `1.0x` 字号并重绘系统栏，产生首屏字号缩水重排与视觉闪动。
- **源码证据**：
  - [MainActivity.kt](file:///E:/blood%20pressure%20record/app/src/main/java/com/example/bloodpressurerecord/MainActivity.kt#L54-L64)
  - [SettingsViewModel.kt](file:///E:/blood%20pressure%20record/app/src/main/java/com/example/bloodpressurerecord/ui/settings/SettingsViewModel.kt#L51-L55)
- **审计依据**：
  - `design-audit/references/ui-patterns.md`（Loading States：避免首帧布局抖动 / Flash of Unstyled Content）
- **改进方案**：
  在 `AppContainer` 初始化或 `SettingsRepository` 层缓存最近一次已知显示偏好（或在 `MainActivity` 等待 DataStore 首帧就绪前保持轻量暖纸底 `windowBackground` 不渲染主树，因本地 DataStore 读取通常 `<15ms`），确保首帧即以用户真实的 `fontScale` 与 `appearanceMode` 上屏。
- **预期收益及验证方式**：
  - **收益**：彻底消除关闭大字模式或手动指定深色模式用户的冷启动首帧闪烁与重排。
  - **验证**：关闭大字模式并手动开启深色模式，强行停止应用后冷启动录屏逐帧检查第 1 帧渲染状态。

---

## 五、逐页面与专项模块问题清单

### 5.1 首页（`DashboardScreen` / `BpRangeBar` / `WeekCard`）

---

#### `AUD-06` · 首页核心读数卡（`ReadingHero`）内部可点击区额外 `6.dp` 内边距破坏卡片左右对齐线

- **问题性质**：规范偏差（截图 #1 实锤 + 源码确认）
- **优先级**：**P1**
- **所在页面**：首页（`DashboardScreen`）
- **相关组件**：`ReadingHero`、`BpRangeBar`
- **问题描述**：
  截图 #1 顶部绿色情绪大卡（`ReadingHero`）外层设置了 `.padding(horizontal = 20.dp, vertical = 18.dp)`，第一行小压与对话气泡、第三行四色区间条（`BpRangeBar`）均严格贴合 `20.dp` 左右内边界。但中间承载「最近一次 · 今天 22:14 · 晚上」「109/77」「● 正常  3 组平均」的可点击 `Column` 为给焦点环留余量，直接加了 `.padding(6.dp)`。这导致核心大数字与标题整体向内缩进至 `26.dp`，右侧 `>` 箭头也向内缩进 `6.dp`，形成“上下宽、中间窄”的左对齐线凹陷与右边缘错位。
- **源码证据**：
  - [DashboardScreen.kt](file:///E:/blood%20pressure%20record/app/src/main/java/com/example/bloodpressurerecord/ui/home/DashboardScreen.kt#L370)：外层 `.padding(horizontal = 20.dp, vertical = 18.dp)`
  - [DashboardScreen.kt](file:///E:/blood%20pressure%20record/app/src/main/java/com/example/bloodpressurerecord/ui/home/DashboardScreen.kt#L399-L413)：中间可点击 `Column` 增加 `.padding(6.dp)`
  - [DashboardScreen.kt](file:///E:/blood%20pressure%20record/app/src/main/java/com/example/bloodpressurerecord/ui/home/DashboardScreen.kt#L492)：下方 `BpRangeBar` 未缩进，与中间读数左缘相差 `6.dp`
- **审计依据**：
  - `design-review-plugin/skills/design-review/knowledge-base.md`（Выравнивание：*"Поддерживайте линии выравнивания — двойное переключение создаёт провал для взгляда"* 保持对齐线连续，避免形成视觉凹坑；`6dp` 亦属稀有非标间距）
  - `design-audit/references/visual-design.md`（Alignment：Is left-alignment consistent?）
- **改进方案**：
  将 `ReadingHero` 外层水平内边距设为 `14.dp`、令小压行与 `BpRangeBar` 各自补 `padding(horizontal = 6.dp)`，或者保持外层 `20.dp`、通过自定义 `layout` 让可点击热区与波纹向外扩展 `8.dp`（负出血热区）而内部文字 `padding` 保持 `0.dp`，从而使小压、时间标题、`109/77` 大数字、状态胶囊与四色区间条左边缘 100% 落在同一条 `20.dp` 垂直基准线上，右侧 `>` 箭头与气泡右缘严丝合缝对齐。
- **预期收益及验证方式**：
  - **收益**：消除首页第一视觉焦点卡片的左右锯齿感，同时保留舒适的点击波纹与键盘焦点环边距。
  - **验证**：在截图或 Layout Inspector 中拉取 `x = 40dp`（页边距 20 + 卡内距 20）参考线，确认「最近一次」「109」「● 正常」与四色条左端完全共线。

---

#### `AUD-09` · 首页服药行与药品编辑缺乏剂量单位引导，且「待打卡」按钮呈禁用灰态、「已打卡」反而高亮

- **问题性质**：客观缺陷 + 可用性问题（截图 #1 真实用户数据实锤 + 源码确认）
- **优先级**：**P1**
- **所在页面**：首页（`DashboardScreen`）、提醒与服药（`SettingsReminderScreen` / `MedicationEditorDialog`）
- **相关组件**：`MedicationRow`、`MedicationEditorDialog`
- **问题描述**：
  1. **真实用户数据暴露出无单位裸数字问题**：截图 #1「今日服药」第 2、3 行显示 **`08:00 · 1`** 与 **`19:00 · 1`**（仅第 1 行为 `08:00 · 1片`）。排查 `MedicationEditorDialog` 发现，「每次用量（选填）」输入框仅在 `placeholder` 中写了 `"如：1 片 / 5 mg"`——用户点击输入框瞬间 `placeholder` 消失，输入数字 `1` 后极易遗漏单位，且无快捷单位选择。
  2. **待办与完成按钮视觉权重倒置**：截图 #1 第 1 行尚未服药的「拜新同（08:00 · 1片）」右侧「打卡」按钮使用 `surfaceContainerHighest (#F2ECE3)` 浅灰底配 `onSurfaceVariant (#645C55)` 灰字，在白卡上酷似**不可点击的 Disabled 禁用按钮**；而第 2、3 行已完成的「✓ 已打卡」却采用鲜明的薄荷绿胶囊。在 `23:28` 夜间查看时，早上 `08:00` 漏打卡的待办项反而最暗淡不起眼。
- **源码证据**：
  - [SettingsSubpages.kt](file:///E:/blood%20pressure%20record/app/src/main/java/com/example/bloodpressurerecord/ui/settings/SettingsSubpages.kt#L750-L757)：仅靠获焦消失的 `placeholder` 提示单位
  - [DashboardScreen.kt](file:///E:/blood%20pressure%20record/app/src/main/java/com/example/bloodpressurerecord/ui/home/DashboardScreen.kt#L828-L831) & [#L918-L922](file:///E:/blood%20pressure%20record/app/src/main/java/com/example/bloodpressurerecord/ui/home/DashboardScreen.kt#L918-L922)：未打卡态使用 `surfaceContainerHighest` + `onSurfaceVariant`
- **审计依据**：
  - `design-review-plugin/skills/design-review/knowledge-base.md`（Цвет：*"Бледный текст на кнопках выглядит как disabled-состояние"* 按钮淡灰字像禁用态）
  - `design-audit/references/ui-patterns.md`（Forms：Placeholder text disappears on focus — cannot replace helper/unit guidance；Buttons：CTAs prominent）
- **改进方案**：
  1. **按钮状态重塑**：将未打卡「打卡」按钮改为具行动召唤力的**主色描边浅暖底样式**（如 `primaryContainer` `#FFDED5` 底 + `onPrimaryContainer` `#963828` 字，或白底 + `1.5.dp primary` 陶土红描边 + `primary` 粗体字），已打卡「✓ 已打卡」保持柔和鼠尾草绿 `secondaryContainer` 确认态，使“待打卡 = 醒目可按，已打卡 = 安心归档”。
  2. **剂量单位防呆引导**：在 `MedicationEditorDialog`「每次用量」下方增加一行轻量常用单位点选胶囊（`片`、`粒`、`丸`、`mg`、`袋`）或常驻 `supportingText = "建议带上单位，如 1 片、5 mg"`（纯前端输入辅助，不改动数据库 `dosage: String` 字段结构与历史数据）。
- **预期收益及验证方式**：
  - **收益**：消除“待打卡按钮像灰掉不能按”的错觉，大幅减少中老年用户录入裸数字剂量。
  - **验证**：在首页对比未打卡与已打卡按钮的视觉可点击感；在添加药品弹窗输入 `1` 并点击 `片` 验证快捷拼接。

---

#### `AUD-10` · 首页读数卡上下紧邻展示「六级医学分级芯片」与「四段自定义区间条」，术语与边界不一致且缺乏同屏解释

- **问题性质**：规范偏差（健康类应用专项体验，截图 #1 实锤 + 源码确认）
- **优先级**：**P1**
- **所在页面**：首页（`DashboardScreen`）
- **相关组件**：`ReadingHero`、`GradeChip`、`BpRangeBar`
- **问题描述**：
  `ReadingHero` 在短短 `12dp` 垂直距离内同时呈现了两套血压评价标尺：上方 `GradeChip` 采用不可修改的《中国高血压防治指南》六级诊室标准（`血压偏低 / 正常 / 正常高值 / 1级高血压 / 2级高血压 / 3级高血压`），下方 `BpRangeBar` 采用用户可调的四段示意刻度（`偏低 / 正常 / 偏高 / 过高`，默认 `90/60、120/80、140/90`）。
  - 当读数为 `119/85`（如截图 #3 真实记录）时，上方芯片显示「正常高值」，下方三角却指向「偏高」并加粗「偏高」二字；
  - 若用户在「我的 → 首页四色条区间」将偏高起点调为家庭自测标准 `135/85`，则 `130/82` 会出现**上方芯片写「正常高值」、下方色条指在「正常」**的直接冲突，而同屏没有任何微文案说明两者的区别（说明深藏在三级子页《使用指南》中）。
- **源码证据**：
  - [DashboardScreen.kt](file:///E:/blood%20pressure%20record/app/src/main/java/com/example/bloodpressurerecord/ui/home/DashboardScreen.kt#L471-L492)：`GradeChip` 与 `BpRangeBar` 紧邻堆叠
  - [strings.xml](file:///E:/blood%20pressure%20record/app/src/main/res/values/strings.xml#L39-L42)：`偏低 / 正常 / 偏高 / 过高`
  - [AppInfoContent.kt](file:///E:/blood%20pressure%20record/app/src/main/java/com/example/bloodpressurerecord/ui/settings/AppInfoContent.kt#L33)：需长篇解释“分清应用里的三套参考”
- **审计依据**：
  - `design-audit/references/human-factors.md`（Cognitive Load Theory：消除外源性认知负荷，避免同屏双重标准冲突）
  - `design-audit/references/ux-heuristics.md`（#2 Match Between System and Real World & #6 Recognition Rather than Recall）
- **改进方案**：
  在不改变现有业务逻辑与自定义区间功能的前提下：
  1. 当用户的 `BpZoneConfig` **未修改（处于默认 90/60、120/80、140/90）**时，将四色条第三、四段标签与指南术语对齐或调和为**「偏低 / 正常 / 正常高值 / 偏高」**（因默认分界 `120/80` 与 `140/90` 恰好对应指南的「正常高值」与「1级及以上高血压」起点！），彻底消除默认态下「正常高值 vs 偏高」的同义异词冲突！
  2. 当用户**主动自定义修改了四色条刻度**（`config != BpZoneConfig.Default`）时，在四色条右上方或下方自动浮现一行轻量说明标签 `"自定义参考刻度"`（可点击直达刻度调整面板），做到默认零冲突、自定义有标识。
- **预期收益及验证方式**：
  - **收益**：95% 默认配置用户不再看到同屏两套打架词汇；自定义用户一眼识别色条为个人参考，提升医学专业可信度。
  - **验证**：分别在默认 `BpZoneConfig` 与自定义 `135/85` 配置下录入 `125/82`，核查首页大卡标签与提示。

---

#### `AUD-25` · 首页小压点击换话与历史日历双击定位备注缺少视觉可发现性线索，周卡图例虚线未独立成项

- **问题性质**：规范偏差（交互可发现性与图例清晰度，截图 #1/#2 实锤）
- **优先级**：**P2**
- **所在页面**：首页（`DashboardScreen` / `WeekCard`）、历史记录（`HistoryScreen`）
- **相关组件**：`Buddy`、`ReadingHero` 对话气泡、`WeekRangeLegend`、`CalendarMonth`
- **问题描述**：
  1. **小压点击交互隐藏过深**：点击 `Buddy` 可轮播 4 条实用提示（含高风险指导、静坐 5 分钟提醒、连续打卡天数、下一顿待服药时间），但除 TalkBack 语义 `"小压，点一下换一句话"` 外，气泡与小压没有任何视觉暗示（如 `1/4` 微圆点或换一换小角标），绝大多数明眼用户不知道小压可点。
  2. **日历双击定位备注不可发现**：双击带备注点的日期可自动滚至首条备注记录，但日历图例仅写「● 有备注」，无任何操作提示。
  3. **周卡底部图例虚线寄生在空心圆项后**：截图 #1 底部图例第 2 行将 `○ 空心圆：日均低压 · 虚线：四色条正常上界 120/80` 拼在同一个 `Row` 中，既没有画出虚线图样，又导致整串长文挤成一团。
- **源码证据**：
  - [DashboardScreen.kt](file:///E:/blood%20pressure%20record/app/src/main/java/com/example/bloodpressurerecord/ui/home/DashboardScreen.kt#L373-L394) & [Buddy.kt](file:///E:/blood%20pressure%20record/app/src/main/java/com/example/bloodpressurerecord/ui/home/Buddy.kt#L153)
  - [HistoryScreen.kt](file:///E:/blood%20pressure%20record/app/src/main/java/com/example/bloodpressurerecord/ui/history/HistoryScreen.kt#L831) & [#L911](file:///E:/blood%20pressure%20record/app/src/main/java/com/example/bloodpressurerecord/ui/history/HistoryScreen.kt#L911)
  - [WeekCard.kt](file:///E:/blood%20pressure%20record/app/src/main/java/com/example/bloodpressurerecord/ui/home/WeekCard.kt#L909-L952)
- **审计依据**：
  - `design-audit/references/ux-heuristics.md`（#6 Recognition Rather than Recall：hidden affordances）
  - `design-audit/references/human-factors.md`（Gestalt Proximity & Similarity：图例符号与含义一一对应）
- **改进方案**：
  1. 在首页对话气泡右下角增加极轻量的 4 枚进度微圆点（当前激活点 `6.dp` 宽、其余 `4.dp`，或点击整块气泡+小压均可切换下一句），赋予清晰可点线索。
  2. 将 `WeekRangeLegend` 拆分为 3 个独立 `FlowRow` 子项：`● 日均高压`、`○ 日均低压`、`╌╌ 正常上界 120/80`（绘制真实 `12.dp` 虚线图例）。
  3. 将日历图例「● 有备注」补充为「▪ 有备注（双击定位）」或改用微型笔记图标。
- **预期收益及验证方式**：
  - **收益**：激活已有优质隐藏功能的实际触达率，周卡图例一目了然。
  - **验证**：检查首页气泡指示点切换与周卡三项图例在常规/大字模式下的折行对齐。

---

#### `AUD-26` · 深色模式下「血压偏低」文字对比度（4.30:1）略低于 AA 标准，且首页四色条在同色系大卡底色上对比度仅 1.37:1–1.67:1

- **问题性质**：客观缺陷（数学对比度测算 + 截图 #1 核验）
- **优先级**：**P2**
- **所在页面**：首页（`DashboardScreen`）、全局深浅色分级与区间条（`DesignSystem.kt`、`BpRangeTokens.kt`）
- **相关组件**：`DarkBpGradePalette`、`BpRangeBar`
- **问题描述**：
  1. 在深色模式下，`buildBpGradePalette(dark = true)` 将卡片 `soft` 设为 `ink.copy(alpha = 0.24f)`、将前景文字 `ink` 设为 `mid`。经混合计算，「血压偏低」前景 `#8E8BC8` 在深色卡片 `#26201C` 叠加后的 `#312C3A` 底色上对比度为 **4.30:1**，略低于 `13sp` 正文/标签所需的 WCAG AA **4.5:1**；且 `ink == mid` 导致深色模式下 `StatusChip` 左侧圆点与文字颜色完全一致，丧失明暗层次。
  2. 在浅色模式截图 #1 中，`ReadingHero` 背景为 `#DFF4E9`（浅薄荷绿），其内部 `BpRangeBar` 的「偏低 `#C9CBF0`」与「正常 `#7FCAA6`」色段相对卡片底色对比度仅 **1.37:1** 与 **1.67:1**（Stage 1 黄底上「偏高 `#F2C48D`」仅 **1.40:1**），四色条边缘偏粉软。
- **源码证据**：
  - [DesignSystem.kt](file:///E:/blood%20pressure%20record/app/src/main/java/com/example/bloodpressurerecord/ui/theme/DesignSystem.kt#L179-L198)
  - [BpRangeTokens.kt](file:///E:/blood%20pressure%20record/app/src/main/java/com/example/bloodpressurerecord/ui/theme/BpRangeTokens.kt#L25-L39)
- **审计依据**：
  - `design-audit/references/ux-heuristics.md`（WCAG 2.1：Text contrast ≥ 4.5:1, UI component contrast ≥ 3:1）
- **改进方案**：
  1. 将深色模式 `low` 的文字色单独提亮至 `#A3A0DE`（在深色混合底上对比度提升至 **5.62:1**），并为深色 `BpGradeVisual` 区分略亮的 `ink` 与适中的 `mid`。
  2. 为 `BpRangeBar` 轨道外层包裹 `2.dp` 白卡衬底或 `1.dp` 柔和内描边（`surface.copy(alpha = 0.75f)`），或适度加深浅色四段色值明度差，确保在任意 `ReadingHero` 情绪底色上清晰浮现。
- **预期收益及验证方式**：
  - **收益**：六档分级在深浅色下全部 100% 满足 WCAG AA `≥4.5:1`，首页四色条层次更利落。
  - **验证**：运行色彩对比度校验测试并在深浅色预览下核对 `BpRangeBar` 全部 4 种状态。

---

### 5.2 新增测量与编辑测量页（`AddMeasurementScreen` / `EditSessionScreen`）

---

#### `AUD-21` · 新增页与编辑页错误提示相对保存按钮位置相反，且三按钮弹窗使用刚性 `Row` 在大字模式下易挤压换行

- **问题性质**：客观缺陷（源码对比确认）
- **优先级**：**P2**
- **所在页面**：新增测量（`AddMeasurementScreen`）、修改读数（`EditSessionScreen`）、数据管理（`SettingsDataManagementScreen`）
- **相关组件**：`InlineSessionAction`、`UnsavedChangesDialog`、`AlertDialog`
- **问题描述**：
  1. **错误提示位置前后矛盾**：`AddMeasurementScreen` 将 `formMessage` 放在 `InlineSessionAction`（「保存记录」主按钮）**下方**，当页面较长或软键盘打开时，点击保存触发的时间校验/保存失败红字可能落在屏幕可视区外；而号称“100% 同构”的 `EditSessionScreen` 却将错误 `message` 放在「保存修改」主按钮**上方**。
  2. **三按钮弹窗刚性 `Row` 挤压隐患**：`UnsavedChangesDialog`（「保存草稿」「放弃」「继续编辑」）、`SettingsDataManagementScreen` 明文导出弹窗（「明文导出」「取消」「加密导出（推荐）」）及清空确认弹窗（「先导出备份」「取消」「永久清空」）均在 `AlertDialog` 的 `dismissButton` 槽内手动套了一个不换行的 `Row`。在 `1.5x–2.0x` 大字号或 `360dp` 小屏下，M3 `AlertDialog` 虽会将 `dismissButton` 整体与 `confirmButton` 折行，但 `dismissButton` 内部的刚性 `Row` 无法拆行，易导致按钮文字竖排挤扁。
  3. **放弃按钮破坏性语义不一致**：`SettingsProfileScreen` 的未保存弹窗将「放弃并返回」标为 `error` 红色，而录入页 `UnsavedChangesDialog` 的「放弃」却使用普通主色。
- **源码证据**：
  - [AddMeasurementScreen.kt](file:///E:/blood%20pressure%20record/app/src/main/java/com/example/bloodpressurerecord/ui/record/AddMeasurementScreen.kt#L452-L474) vs [EditSessionScreen.kt](file:///E:/blood%20pressure%20record/app/src/main/java/com/example/bloodpressurerecord/ui/history/EditSessionScreen.kt#L354-L372)
  - [SessionFormComponents.kt](file:///E:/blood%20pressure%20record/app/src/main/java/com/example/bloodpressurerecord/ui/common/SessionFormComponents.kt#L838-L857)
  - [SettingsDataManagementScreen.kt](file:///E:/blood%20pressure%20record/app/src/main/java/com/example/bloodpressurerecord/ui/settings/SettingsDataManagementScreen.kt#L131-L148) & [#L555-L568](file:///E:/blood%20pressure%20record/app/src/main/java/com/example/bloodpressurerecord/ui/settings/SettingsDataManagementScreen.kt#L555-L568)
- **审计依据**：
  - `design-audit/references/ui-patterns.md`（Buttons：Destructive actions use danger/red styling；Modals & Forms：Error feedback immediately visible near CTA）
  - `design-audit/references/ux-heuristics.md`（#4 Consistency & #9 Help Users Recognize, Diagnose, and Recover from Errors）
- **改进方案**：
  1. 统一将表单级错误提示置于 `InlineSessionAction` 主按钮正上方（与 `disabledReason` 同一视觉焦点区）。
  2. 抽取通用 `AppThreeActionDialog`（或使用 `FlowRow` / 纵向全宽按钮栈承载三操作弹窗，如 `UpdateDialog` 的纵向全宽按钮模式），在大字号下永不挤扁文字，并将「放弃」统一标为 `MaterialTheme.colorScheme.error`。
- **预期收益及验证方式**：
  - **收益**：确保校验错误 100% 出现在大拇指点击视线正上方，彻底解决 `2.0x` 大字下三按钮弹窗挤压。
  - **验证**：在 `SessionFormComponentsTest` 中以 `320dp` 宽 + `2.0x` 字号渲染 `UnsavedChangesDialog`，断言三个按钮均单行完整显示且触控高度 `≥48dp`。

---

#### `AUD-28` · 新增/编辑测量页前两组读数直接裸露在页面纸色背景上，未与下方卡片形成一致的容器边界

- **问题性质**：规范偏差（视觉分组与矩形原则，源码确认）
- **优先级**：**P2**
- **所在页面**：新增测量（`AddMeasurementScreen`）、修改读数（`EditSessionScreen`）
- **相关组件**：`MeasurementReadingCard`
- **问题描述**：
  `MeasurementReadingCard` 虽以 `Card` 命名，实际仅为一个无背景、无边框的裸 `Column`。在录入页中，「测量时间」与「第 1 组」「第 2 组」共 6 个输入框直接散落在 `#FAF6EF` 页面底色上，组间仅靠 `16.dp` 间距分隔（且标题行 `heightIn(min = 48.dp)` 拉大了标题与自身输入框的距离），而下方的「本次平均」和「补充情况」却装在 `DataCard` 白卡里，形成“上散下聚”的版式割裂。
- **源码证据**：
  - [SessionFormComponents.kt](file:///E:/blood%20pressure%20record/app/src/main/java/com/example/bloodpressurerecord/ui/common/SessionFormComponents.kt#L339-L491)
  - [AddMeasurementScreen.kt](file:///E:/blood%20pressure%20record/app/src/main/java/com/example/bloodpressurerecord/ui/record/AddMeasurementScreen.kt#L308-L379)
- **审计依据**：
  - `design-review-plugin/skills/design-review/knowledge-base.md`（Правило близости 接近性法则 & Правило прямоугольника 矩形原则：每组信息应收敛为清晰的矩形视觉块）
- **改进方案**：
  为每组 `MeasurementReadingCard` 包裹轻量奶白卡片容器（`DataCard` 或 `16dp` 内边距 `surface` 圆角容器，输入框底色微调为 `surfaceVariant` 或保持清晰描边），并将无删除按钮时的「第 1/2 组」标题行高度从虚高的 `48.dp` 收紧至自然行高（有删除按钮时仅按钮热区保持 `48dp`），让组内标题与输入框更紧凑、组与组之间卡片分明。
- **预期收益及验证方式**：
  - **收益**：大幅增强多组录入（尤其是添加第 3、4 组时）的成组边界感，符合中老年用户按“第 1 次、第 2 次”分块核对的心智。
  - **验证**：在新增页添加至 3 组读数，检查组内间距 `<` 组间卡片间距且首屏仍能完整露出前两组核心输入区。

---

### 5.3 历史记录、搜索与详情页（`HistoryScreen` / `NoteSearchScreen` / `HistoryDetailScreen`）

---

#### `AUD-05` · 历史日历「今天且已选中」默认态双重 `.border()` 叠加，导致暖色选中框被深灰棕描边覆盖变脏

- **问题性质**：客观缺陷（截图 #2「26」号单元格实锤 + Compose 绘制机制确认）
- **优先级**：**P1**
- **所在页面**：历史记录（`HistoryScreen` - 日历模式）
- **相关组件**：`CalendarDay`
- **问题描述**：
  用户每次打开「历史」页，默认选中的正是「今天」（如截图 #2 中的 `26` 日）。此时 `today == true` 且 `selected == true`。源码在 `circleModifier` 上先后链式调用了两次 `.border()`：先加 `today` 的 `1.5.dp onSurfaceVariant (#645C55)`，再加 `selected` 的 `2.dp primary (#B94B39)`。在 Jetpack Compose 的修饰符绘制链中，靠前的 `DrawModifier`（`today`）会在内层绘制完成后**叠画在最上层**，导致深灰褐色描边直接盖在陶土红描边之上，抗锯齿混合出暗沉浑浊的深棕黑框（见截图 #2「26」号框色），完全背离了第 855 行注释*“选中态用暖色主色，避免日历里出现近黑色描边”*的预期。
- **源码证据**：
  - [HistoryScreen.kt](file:///E:/blood%20pressure%20record/app/src/main/java/com/example/bloodpressurerecord/ui/history/HistoryScreen.kt#L848-L860)：两个独立 `if` 块串行叠加 `.border()`
- **审计依据**：
  - `design-review-plugin/skills/design-review/knowledge-base.md`（Цвет：避免浑浊脏色；选中态焦点清晰度）
  - `design-audit/references/ux-heuristics.md`（#1 Visibility of System Status：当前选中态需清晰唯一）
- **改进方案**：
  将两个独立的 `if (today)` 与 `if (selected)` 合并为互斥 `when` 分支：
  - `selected` 为真时：**仅绘制** `2.dp MaterialTheme.colorScheme.primary` 陶土红选中描边（若同时是今天，可通过底部圆点或加粗字重体现，不再叠画灰色第二层边框）；
  - `today && !selected` 时：绘制 `1.5.dp MaterialTheme.colorScheme.outline`（修复 `AUD-02` 后的清晰暖灰描边）指示今天位置。
- **预期收益及验证方式**：
  - **收益**：立即让历史日历默认选中的“今天”恢复清澈醒目的陶土红选中框，消除叠色脏边。
  - **验证**：打开历史日历分别查看「今天选中」与「点选其他日期后今天的未选中态」，确认边框纯净无重影。

---

#### `AUD-12` · 历史日历图例色块对比度仅 1.12:1 几近隐形，且「有备注」使用陶土红圆点易误读为血压异常

- **问题性质**：规范偏差（色彩对比度与健康风险语义，截图 #2、#3 实锤）
- **优先级**：**P1**
- **所在页面**：历史记录（`HistoryScreen` - 日历模式与近期模式）
- **相关组件**：`CalendarLegend`、`CalendarDay`、`HistorySessionCard`
- **问题描述**：
  1. **图例色块溶解于白底**：截图 #2 日历底部图例使用 `11.dp` 的 `.soft` 浅底色块（如「正常高值 `#EEF3D7`」「1级 `#F7F0CF`」「正常 `#DFF4E9`」）直接画在 `#FFFDFA` 白卡上且无边框，实测对比度仅 **1.12:1–1.18:1**，在手机屏幕上几乎与白色背景融为一体，中老年用户根本无法分辨哪块对应哪种颜色。
  2. **代码注释与实际颜色自相矛盾（备注红点误导）**：`HistoryScreen.kt#L879` 注释明确写道：*“备注使用中性文档标记，避免与高风险警示争夺红色语义”*，但紧接着第 882 行却使用了 `MaterialTheme.colorScheme.primary (#B94B39)` 陶土红圆点！在截图 #2 中，11 月有 16 个日期右上角顶着醒目的小红点，第一眼极易让人误以为“这个月有一半天数血压异常报警”。
  3. **卡片内备注底色抢夺异常视觉焦点**：截图 #3 中，带备注的正常血压卡片（`99/66`、`108/73`）底部铺了整条珊瑚粉 `primaryContainer (#FFDED5)`，在快速纵向扫视时比真正需要关注的 `119/85 正常高值` 更像异常警告。
- **源码证据**：
  - [HistoryScreen.kt](file:///E:/blood%20pressure%20record/app/src/main/java/com/example/bloodpressurerecord/ui/history/HistoryScreen.kt#L879-L883)：注释写“中性文档标记”，代码写 `colorScheme.primary`
  - [HistoryScreen.kt](file:///E:/blood%20pressure%20record/app/src/main/java/com/example/bloodpressurerecord/ui/history/HistoryScreen.kt#L905-L923)：`LegendSwatchItem` 使用 `soft` 无描边
  - [HistoryScreen.kt](file:///E:/blood%20pressure%20record/app/src/main/java/com/example/bloodpressurerecord/ui/history/HistoryScreen.kt#L1057-L1069)：备注摘要使用 `primaryContainer`
- **审计依据**：
  - `design-review-plugin/skills/design-review/knowledge-base.md`（Цвет：浅色元素在白底上无描边会溶解；Отстранение 疏离凸显原则：高饱和暖红应留给真正需要警惕的状态或主操作）
  - `design-audit/references/visual-design.md`（Semantic color use consistent；WCAG 1.4.11 ≥ 3:1）
- **改进方案**：
  1. **图例增强**：`LegendSwatchItem` 改用 `12.dp` 圆角方块，填充保留 `.soft` 以与日历格底色一致，但**外加 `1.2.dp` 对应分级的 `.ink` 或 `.mid` 描边**（对比度立刻提升至 `>3.5:1`，且与日历格“浅底深字”语言完全同构）。
  2. **备注标记去红化**：兑现第 879 行注释的本意，将日历右上角备注点改为沉稳的中性暖灰棕 `onSurfaceVariant`（或深青蓝 `onTertiaryContainer`）小圆点/短横标；将 `HistorySessionCard` 内的备注引用框底色从 `primaryContainer` 改为温暖柔和的纸灰底 `surfaceVariant (#F2ECE3)` + `onSurfaceVariant` 文字（左缘可加 `3.dp` 暖棕竖线形成日记引用感），把红色系视觉注意力彻底归还给高血压与高风险警示。
- **预期收益及验证方式**：
  - **收益**：日历 6 档图例清晰可辨，彻底消除“写日记备注像血压报警”的心理压力与误读。
  - **验证**：在历史日历与近期列表同屏包含「正常+有备注」「正常高值」「高风险」记录时，3 秒扫视测试视觉焦点优先级。

---

#### `AUD-16` · 历史卡片（`HistorySessionCard`）三列顶部硬对齐导致基线错位，且血压/脉搏单位在各页面间缺失不一

- **问题性质**：规范偏差（排版与基线对齐，截图 #2、#3 实锤）
- **优先级**：**P2**
- **所在页面**：历史记录（`HistoryScreen`）、记录详情（`HistoryDetailScreen`）、首页早晚槽（`DashboardScreen`）
- **相关组件**：`HistorySessionCard`、`HistoryDetailScreen`、`MeasurementSlot`
- **问题描述**：
  1. **基线错位**：截图 #2、#3 的 `HistorySessionCard` 采用三列 `Row(verticalAlignment = Alignment.Top)`：左列时间 `22:14` 为 `17.sp`（行高 `24.sp`），中列血压 `109/77` 为 `24.sp`（行高 `32.sp`）。因顶部硬对齐，首行 `22:14` 的数字基线比 `109/77` 高出约 `5dp`，第二行「晚上」也比「脉搏 65」高出约 `7dp`；右列「● 正常」胶囊顶在右上角，无备注时卡片右下角显得空旷失衡。
  2. **单位展示不一致**：`HistorySessionCard`、`HistoryDetailScreen` 主读数及首页早晚槽均只写 `109/77` 和 `脉搏 65`（无 `mmHg` 与 `次/分`），而 `NoteSearchResultCard`、`TrendSelectionReadout`、`RecentSummaryCard` 又完整写出 `mmHg` 与 `次/分`。
- **源码证据**：
  - [HistoryScreen.kt](file:///E:/blood%20pressure%20record/app/src/main/java/com/example/bloodpressurerecord/ui/history/HistoryScreen.kt#L1017-L1056)
  - [HistoryDetailScreen.kt](file:///E:/blood%20pressure%20record/app/src/main/java/com/example/bloodpressurerecord/ui/history/HistoryDetailScreen.kt#L152-L186)
- **审计依据**：
  - `design-audit/references/visual-design.md`（Alignment & Consistency）
  - `PRODUCT.md`（Product Principles：数字、单位、日期与状态清楚可辨）
- **改进方案**：
  1. 在 `HistorySessionCard` 中：将外层三列 `Row` 改为 `Alignment.CenterVertically`（使右侧状态芯片在双行内容间光学垂直居中，当有高风险双芯片时依然自然上下排布），并将左列时间与中列读数的第一行通过 `Modifier.alignByBaseline()` 对齐（或微调左列顶部 `padding(top = 4.dp)` 对齐 `24sp` 数字基线），令「晚上」与「脉搏 65 次/分」也处于同一水平线。
  2. 在 `HistoryDetailScreen` 主读数旁及 `HistorySessionCard` 读数旁以轻量次级字号补齐 `mmHg`，脉搏统一为 `脉搏 65 次/分`（窄屏早晚双栏槽空间有限可保留省略 `mmHg`）。
- **预期收益及验证方式**：
  - **收益**：历史记录列表行内形成干净的两条水平阅读线，右侧胶囊重心稳固，医学单位表达完整严谨。
  - **验证**：在 `HistoryScreen` 截取单日列表，拉水平参考线验证时间与血压基线、时段与脉搏基线对齐。

---

#### `AUD-17` · 历史「近期」模式连续堆叠双层全宽分段胶囊，且「近期」统计卡与录入均值卡漏用数字字体与等宽特性

- **问题性质**：规范偏差（信息密度与字体一致性，截图 #3 实锤 + 源码确认）
- **优先级**：**P2**
- **所在页面**：历史记录（`HistoryScreen` - 近期模式）、新增/编辑测量页（`SessionAverageCard`）
- **相关组件**：`RecentPeriodSelector`、`RecentSummaryCard`、`SessionAverageCard`
- **问题描述**：
  1. **双层同级胶囊堆叠**：截图 #3 顶部连续排列「日历 / 近期」与「本周 / 本月」两个完全相同的 `48dp+` 全宽陶土粉分段胶囊，视觉上出现两个并列焦点，且吃掉首屏近 `120dp` 纵向空间。
  2. **统计卡版式松散且字体掉队**：截图 #3「本周」卡片将测量次数、高低压、脉搏、高风险分作 6 行纵向平铺，且 `110 mmHg`、`75 mmHg`、`15 次`、`71 次/分` 全部漏加 `NumberFontFamily` 与 `tabularNums()`，导致数字风格退回普通系统黑体（同理，录入页 `SessionAverageCard` 的均值也漏设了 `fontFamily = NumberFontFamily`）。
- **源码证据**：
  - [HistoryScreen.kt](file:///E:/blood%20pressure%20record/app/src/main/java/com/example/bloodpressurerecord/ui/history/HistoryScreen.kt#L393-L418) & [#L490-L543](file:///E:/blood%20pressure%20record/app/src/main/java/com/example/bloodpressurerecord/ui/history/HistoryScreen.kt#L490-L543)
  - [SessionFormComponents.kt](file:///E:/blood%20pressure%20record/app/src/main/java/com/example/bloodpressurerecord/ui/common/SessionFormComponents.kt#L1005-L1010)
- **审计依据**：
  - `design-review-plugin/skills/design-review/knowledge-base.md`（Визуальная иерархия & Консистентность：主导航与次级过滤不应长得一模一样；字体风格跨页一致）
- **改进方案**：
  1. 将二级周期切换「本周 / 本月」收敛进 `RecentSummaryCard` 卡片头部右侧（紧凑型白底胶囊，左侧显示标题与日期范围，右侧切换「本周 | 本月」），一步省去独立的一整行分段条，高度立减 `64dp`！
  2. 重构 `RecentSummaryCard` 为与趋势页一致的紧凑指标网格：主突出显示平均血压 **`110/75 mmHg`**（`NumberFontFamily` + `tabularNums` + 分级 `StatusChip`），下方三列并排展示「测量 `15 次` · 平均脉搏 `71 次/分` · 高风险 `0 次`」；同步为 `SessionAverageCard` 补上 `NumberFontFamily`。
- **预期收益及验证方式**：
  - **收益**：近期模式首屏多露出 1–2 条历史记录，统计卡视觉品质与首页、趋势页完全拉齐。
  - **验证**：切换「历史 → 近期 → 本周/本月」，检查数字字体、`tnum` 特性及窄屏/大字折行表现。

---

### 5.4 趋势分析与全屏页（`TrendScreen` / `TrendChart` / `TrendFullscreenScreen`）

---

#### `AUD-01` · 趋势页读数操作栏「复位视野」在常规字号下即发生文字截断（「复位视」）且无滚动提示

- **问题性质**：客观缺陷（截图 #4、#5 实锤 + 源码尺寸推演 100% 吻合）
- **优先级**：**P0**
- **所在页面**：趋势（`TrendScreen`）
- **相关组件**：`TrendSelectionReadout` / `ReadoutAction`
- **问题描述**：
  在截图 #4 和截图 #5 的「血压趋势」主卡内，读数信息下方横向排列了 5 个文字按钮：`上一条`、`下一条`、`明细`、`最新`、`复位视野`。在当前用户未开启大字模式（`1.0x`）的常规主流手机屏幕上，最右侧的**「复位视野」被卡片右内边缘生生截断为「复位视」**（「野」字被完全裁掉），且该 `horizontalScroll` 行无任何右侧渐隐阴影或可滚动视觉提示，呈现为明显的界面文字残缺 Bug。
  **源码根因推导**：`ReadoutAction` 直接封装了 Material 3 的 `TextButton`。虽然代码将 `contentPadding` 收小到了 `horizontal = 6.dp`，但 **M3 `TextButton` 内部硬编码了 `Modifier.defaultMinSize(minWidth = ButtonDefaults.MinWidth = 64.dp)`**！5 个按钮最小宽度之和为 `5 × 64dp + 4 × 4dp(间距) = 336dp`；而在常规 `393dp` 宽手机上，扣除页面边距 `20dp × 2` 与卡片内边距 `16dp × 2` 后，可用宽度仅为 **`321dp`**，必然溢出 **`15dp`**，恰好切掉「复位视野」最后一个半汉字！
- **源码证据**：
  - [TrendScreen.kt](file:///E:/blood%20pressure%20record/app/src/main/java/com/example/bloodpressurerecord/ui/history/TrendScreen.kt#L774-L813)：`Row(...horizontalScroll...)` 内放置 5 个 `TextButton`（`ReadoutAction`）
- **审计依据**：
  - `design-audit/references/ui-patterns.md`（Cards：Avoid truncating critical information without visible indication）
  - `design-audit/references/mobile-app.md`（Touch Targets & Safe Layout：常规屏宽下核心操作不得被隐式截断）
- **改进方案**：
  摆脱 M3 `TextButton` 强制 `64.dp` 最小宽度的束缚，改用自定义轻量胶囊操作项（保留 `heightIn(min = 48.dp)` 与 `widthIn(min = 48.dp)` 满足无障碍触控面积，`padding(horizontal = 8.dp)`）：
  - 常规屏宽（`1.0x–1.15x`）下：`上一条`(`55dp`) + `下一条`(`55dp`) + `明细`(`48dp`) + `最新`(`48dp`) + `复位`(`48dp`，或保留 `复位视野` `68dp`) + 间距 `16dp` = **`270dp–290dp ≤ 321dp`**，使用 `Row`（两端分组：左侧 `< 上一条 | 下一条 >`，右侧 `明细 · 最新 · 复位`）即可在单行 **100% 完整显示无截断**！
  - 大字模式（`≥1.3x`）或窄屏（`<360dp`）下：改用 `FlowRow` 自动优雅换为两行，彻底告别无提示横向半字裁切。
- **预期收益及验证方式**：
  - **收益**：彻底消除趋势页首屏最刺眼的半字截断缺陷，同时在所有字号倍率下保证 5 个图表操作按钮全部可见可点。
  - **验证**：在 `320dp`、`360dp`、`393dp` 屏宽及 `1.0x / 1.15x / 2.0x` 字号下运行 `TrendReadoutAccessibilityTest`，断言「复位视野」（或「复位」）完整可见且触控高度 `≥48dp`。

---

#### `AUD-11` · 脉搏趋势折线直接复用「收缩压」陶土红色，且血压图内「目标 120/80」标签与密集折线重叠干扰

- **问题性质**：客观缺陷（数据可视化与健康专项体验，截图 #5 实锤 + 源码确认）
- **优先级**：**P1**
- **所在页面**：趋势（`TrendScreen` / `TrendFullscreenScreen`）
- **相关组件**：`SessionTimeSeriesPulseChart`、`drawThresholdLines`、`TrendChartPalette`
- **问题描述**：
  1. **跨图色彩语义冲突**：截图 #5 上方血压图图例明确标示 `● 高压（收缩压）= 陶土红`，紧接着下方「脉搏趋势」卡片绘制脉搏折线与节点时，直接传入了 `palette.systolic`（同一陶土红），导致用户在上下对照时极易将脉搏曲线误看作收缩压曲线（尤其在横屏全屏双图上下紧贴联动时）。而 `Color.kt#L46` 中原有的独立折线色令牌未被利用。
  2. **图内目标文字与密集数据线碰撞**：在截图 #5（30 天 · 每次测量 · 184 次记录）中，个人目标 `120/80` 恰好穿过正常血压高频波动密集带。`drawThresholdLines` 将 `10.sp` 的 `"目标 120"` 和 `"目标 80"` 画在绘图区内部右端，尽管垫了 `0.88f` 半透明底块，仍被周围剧烈起伏的红蓝折线包围穿插（见截图 #5 右下侧蓝色折线尖峰紧贴刺入「目标 80」），既遮挡真实极值点又难以阅读。
- **源码证据**：
  - [TrendChart.kt](file:///E:/blood%20pressure%20record/app/src/main/java/com/example/bloodpressurerecord/ui/history/TrendChart.kt#L737) & [#L752](file:///E:/blood%20pressure%20record/app/src/main/java/com/example/bloodpressurerecord/ui/history/TrendChart.kt#L752)：脉搏折线与圆点均使用 `palette.systolic`
  - [TrendChartDrawing.kt](file:///E:/blood%20pressure%20record/app/src/main/java/com/example/bloodpressurerecord/ui/history/TrendChartDrawing.kt#L44-L56) & [#L193-L238](file:///E:/blood%20pressure%20record/app/src/main/java/com/example/bloodpressurerecord/ui/history/TrendChartDrawing.kt#L193-L238)
  - [Color.kt](file:///E:/blood%20pressure%20record/app/src/main/java/com/example/bloodpressurerecord/ui/theme/Color.kt#L46)
- **审计依据**：
  - `design-audit/references/human-factors.md`（Gestalt Similarity：外观相同的视觉元素会被感知为同一类指标）
  - `design-audit/references/visual-design.md`（Color & Typography Legibility）
- **改进方案**：
  1. 在 `TrendChartPalette` 中新增独立的 `pulse: Color` 角色（浅色推荐沉稳鼠尾草青绿 `Sage600 #357D58` 或暖琥珀棕 `#8F5B28`，深色对应 `Sage400 #AEBF92` 或 `#E2AD72`），与收缩压陶土红、舒张压深海蓝形成清晰易辨的三原指标体系。
  2. 将个人目标数值直接明确写入图表上方的 `TrendChartLegendRow` 图例中（如 `-- 诊室参考 140/90 · ·· 个人目标 120/80`），绘图区内仅保留干净的双色点状目标虚线（或在右侧留出窄刻度槽放置 `120` / `80` 而不压在折线之上），彻底解放数据绘图区！
- **预期收益及验证方式**：
  - **收益**：血压与脉搏一眼分清，密集折线不再与 `10sp` 微缩文字互相遮挡。
  - **验证**：在 30 天/180 天密集数据及横屏主副图同开状态下检查图例可读性与折线纯净度。

---

#### `AUD-22` · 趋势页页面主标题与首个折叠分组标题同名重复（「血压趋势」），且折叠架构在趋势与我的页略显割裂

- **问题性质**：规范偏差（信息架构与标签命名，截图 #4、#5、#6 实锤）
- **优先级**：**P2**
- **所在页面**：趋势（`TrendScreen`）、我的（`SettingsScreen`）
- **相关组件**：`ExpandableSection`、`SettingsSectionTitle`
- **问题描述**：
  1. 截图 #4 中，页面顶部 H1 大标题为**「血压趋势」**，往下经过范围与粒度胶囊后，第一个 `ExpandableSection` 的标题又叫**「血压趋势」**，同屏上下重复；且默认展开态下副摘要被隐藏，该行仅起折叠开关作用却与页头同名。
  2. 截图 #6「我的」页共有「血压」「提醒」「显示」「数据与报告」「帮助与更新」5 个分组，其中 4 个是不可折叠的静态 `SettingsSectionTitle`，唯独中间的「显示」使用了可折叠 `ExpandableSection`（且默认展开，导致标题右侧单独多出一个 `^` 箭头，底距也与其余 4 组相差 `4dp`）。
- **源码证据**：
  - [TrendScreen.kt](file:///E:/blood%20pressure%20record/app/src/main/java/com/example/bloodpressurerecord/ui/history/TrendScreen.kt#L149) & [#L180](file:///E:/blood%20pressure%20record/app/src/main/java/com/example/bloodpressurerecord/ui/history/TrendScreen.kt#L180)
  - [SettingsScreen.kt](file:///E:/blood%20pressure%20record/app/src/main/java/com/example/bloodpressurerecord/ui/settings/SettingsScreen.kt#L161-L250)
- **审计依据**：
  - `design-audit/references/information-architecture.md`（Labeling Systems & Content Organization）
  - `design-audit/references/ux-copy.md`（Section headers should be specific and non-redundant）
- **改进方案**：
  1. 将趋势页首个折叠分组标题由「血压趋势」更名为**「血压走势图」**（或直接让主图常驻展示不设折叠，仅折叠下方的「脉搏趋势」「昼夜统计」「更多统计」，减少首屏多余层级）。
  2. 在「我的」页中，由于总共仅 9 个设置项、一屏半即可浏览完毕，建议将「显示」也统一为静态 `SettingsSectionTitle("显示")`，消除混排突兀感与额外点击层级。
- **预期收益及验证方式**：
  - **收益**：消除页面标题回声重复，「我的」页 5 大分组视觉节奏完全统一。
  - **验证**：检查趋势页首屏与我的页分组标题间距及测试标签兼容性。

---

#### `AUD-27` · 趋势页「达标率」分级分布条在某分级仅占极少数（如 1/184）时被压缩为 `<2dp` 细缝

- **问题性质**：客观缺陷（数据可视化边界细节，截图 #5 实锤）
- **优先级**：**P2**
- **所在页面**：趋势（`TrendScreen`）
- **相关组件**：`TrendPeriodOverview` 分级分布条
- **问题描述**：
  截图 #5 底部「更多统计 → 达标率」卡片中，用户有 `正常 117 · 正常高值 66 · 1级 1` 共 184 次记录。横向比例条直接按 `Modifier.weight(count.toFloat())` 分配宽度，`1级` 仅分得约 `1.6dp` 宽度，小于自身 `RoundedCornerShape(5.dp)` 圆角直径，在右端退化为一条几乎无法察觉的细线（若那 1 次是高危的「3级高血压」，在分布条上同样会近乎隐形）。
- **源码证据**：
  - [TrendScreen.kt](file:///E:/blood%20pressure%20record/app/src/main/java/com/example/bloodpressurerecord/ui/history/TrendScreen.kt#L448-L453)
- **审计依据**：
  - `design-audit/references/visual-design.md`（Hierarchy & Visibility of meaningful data）
- **改进方案**：
  为每个 `count > 0` 的分级色块施加最小视觉宽度保底（如 `Modifier.widthIn(min = 8.dp)` 或在自定义 `Layout` / `Canvas` 中保证非零项至少占 `8dp` 宽度、整条外层统一裁剪 `RoundedCornerShape(6.dp)`、内部以 `2.dp` 直缝分隔），既消除内侧圆角细缝畸变，又保证偶发异常分级清晰可见。
- **预期收益及验证方式**：
  - **收益**：极低占比（`<2%`）的异常分级在横向分布条中依然清晰可辨、圆角规整。
  - **验证**：构造 `199 次正常 + 1 次 3级` 样本，验证分布条右端红色块宽度 `≥8dp` 且无圆角破碎。

---

### 5.5 我的、设置子页与数据管理（`SettingsScreen` / `SettingsSubpages` / `SettingsDataManagementScreen`）

---

#### `AUD-04` · 「我的」页个人资料卡内「达标高压 / 达标低压」两块磁贴不可点击，形成明显误导性点击死区

- **问题性质**：客观缺陷（可用性与交互可供性，截图 #6 + 源码确认）
- **优先级**：**P1**
- **所在页面**：我的（`SettingsScreen`）
- **相关组件**：顶部个人资料 `DataCard` / `TargetTile`
- **问题描述**：
  在截图 #6 顶部的个人资料白卡中，上半部分是头像、姓名「勇（53 岁 · 男）」与右侧 `>` 箭头，下半部分并列摆放了两块独立圆角灰底磁贴「达标高压 `< 120`」与「达标低压 `< 80`」。在源码中，`DataCard` 本身未传入 `onClick`，`clickable` 仅加在了上半部分的姓名 `Row` 上；下半部分两块 `TargetTile` 没有任何点击处理。然而从视觉格式塔（包含在同一张带 `>` 的卡片内，且磁贴造型酷似按钮）与用户任务心智（在趋势页看到“在「我的」设置个人达标值后可查看达标率”后来此修改）来看，用户极易直接点击「达标高压 `< 120`」磁贴，结果点击完全无反应。
- **源码证据**：
  - [SettingsScreen.kt](file:///E:/blood%20pressure%20record/app/src/main/java/com/example/bloodpressurerecord/ui/settings/SettingsScreen.kt#L112-L160)：仅第 119 行内层 `Row` 有 `clickable`，第 155–158 行 `TargetTile` 无点击事件
- **审计依据**：
  - `design-audit/references/ui-patterns.md`（Cards：Clear delineation of what is clickable — the whole card, or just a link within it?）
  - `design-audit/references/ux-heuristics.md`（Affordance & #1 Visibility of System Status）
- **改进方案**：
  直接将 `onClick = onOpenProfile` 上提至外层 `DataCard(onClick = onOpenProfile)`，移除内部姓名 `Row` 重复的 `clickable`（并为 `DataCard` 补充合并无障碍语义描述 `"个人资料，勇，53岁，男，达标高压小于120，达标低压小于80，点击编辑"`），使点击整张卡片任意区域（包括两块达标值磁贴）均可顺畅进入「用户资料」页编辑。
- **预期收益及验证方式**：
  - **收益**：彻底消除个人资料卡下半部 45% 面积的点击死区，符合整卡可点直觉。
  - **验证**：点击「达标高压」与「达标低压」磁贴区域，确认正常跳转 `SettingsProfileScreen`。

---

#### `AUD-14` · 数据管理页将不推荐的「明文导出」设为主按钮而「加密导出（推荐）」设为次按钮；我的页「高风险确认」警告图标使用健康绿色底座

- **问题性质**：客观缺陷（按钮层级颠倒与语义色错配，截图 #6 + 源码确认）
- **优先级**：**P1**
- **所在页面**：数据管理（`SettingsDataManagementScreen`）、我的（`SettingsScreen`）
- **相关组件**：`AppPrimaryButton` / `AppSecondaryButton`、`SettingSwitchRow` / `RoundIconBadge`
- **问题描述**：
  1. **数据管理页主次行动召唤颠倒**：文案与隐私设计明确推荐用户使用 `.bpx` 加密备份，但在 `SettingsDataManagementScreen` 页面上，视觉最突出的陶土红 `AppPrimaryButton`（带图标、阴影、18sp 粗体）却给了**「导出明文 Excel (.xlsx)」**，而**「加密导出（推荐 · .bpx）」**反而只是无图标的浅灰次级按钮 `AppSecondaryButton`！更矛盾的是，当用户顺着视觉引导点击主按钮「导出明文 Excel」后，弹出的警告框确认按钮又写着「加密导出（推荐）」试图把用户劝回加密导出，形成“界面推你点 A → 弹窗劝你选 B”的拧巴路径。
  2. **警告图标错配健康绿色底座 + 图标重复**：截图 #6「我的 → 显示」列表中，第 5 项「高风险保存前确认」使用了 `WarningAmber`（⚠️ 三角感叹号），但因 `SettingSwitchRow` 写死了 `secondaryContainer` 底座，导致**高风险警告三角坐落在代表“血压正常/安全”的薄荷绿圆底上**；同时「显示小压」与上方「首页四色条区间」重复使用了完全相同的 `FavoriteBorder` 心形图标。
- **源码证据**：
  - [SettingsDataManagementScreen.kt](file:///E:/blood%20pressure%20record/app/src/main/java/com/example/bloodpressurerecord/ui/settings/SettingsDataManagementScreen.kt#L112-L150) & [#L646-L671](file:///E:/blood%20pressure%20record/app/src/main/java/com/example/bloodpressurerecord/ui/settings/SettingsDataManagementScreen.kt#L646-L671)
  - [SettingsScreen.kt](file:///E:/blood%20pressure%20record/app/src/main/java/com/example/bloodpressurerecord/ui/settings/SettingsScreen.kt#L166) & [#L220](file:///E:/blood%20pressure%20record/app/src/main/java/com/example/bloodpressurerecord/ui/settings/SettingsScreen.kt#L220) & [#L233-L239](file:///E:/blood%20pressure%20record/app/src/main/java/com/example/bloodpressurerecord/ui/settings/SettingsScreen.kt#L233-L239) & [#L404-L408](file:///E:/blood%20pressure%20record/app/src/main/java/com/example/bloodpressurerecord/ui/settings/SettingsScreen.kt#L404-L408)
- **审计依据**：
  - `design-audit/references/ui-patterns.md`（Buttons：Visual weight should match importance；Only ONE primary button per view）
  - `design-audit/references/visual-design.md`（Color：Semantic color use consistent — red/amber = warning, green = success）
- **改进方案**：
  1. 在 `SettingsDataManagementScreen` 中对调两者层级：将**「加密导出（推荐 · .bpx）」提升为唯一 `AppPrimaryButton`（配锁/盾牌安全图标）**，将「导出明文 Excel (.xlsx)」降为 `AppSecondaryButton`。
  2. 为 `SettingSwitchRow` 增加可选 `badgeContainerColor / badgeContentColor` 参数：「高风险保存前确认」改用柔和警示暖橙/红底座（`primaryContainer` 或 `errorContainer`）；将「首页四色条区间」图标换为 `Icons.Outlined.Tune` 或 `LinearScale`，把心形/笑脸 `SentimentSatisfiedAlt` 留给「显示小压」，做到一功能一专属图形。
- **预期收益及验证方式**：
  - **收益**：用户直觉点击主按钮即走最安全的加密备份路径，减少一次弹窗折返；设置页图标色彩语义准确无歧义。
  - **验证**：检查数据管理页主次按钮顺序与样式，以及「我的」页各行图标唯一性与语义色匹配。

---

### 5.6 触控目标、光学对齐、外延触点与精细化问题

---

#### `AUD-19` · 部分按钮触控高度低于 `48dp`（含桌面小部件 `24dp` 按钮），且报告/备份开关与复选框未支持整行点击

- **问题性质**：客观缺陷（人因工程与无障碍触控规范，源码确认）
- **优先级**：**P2**
- **所在页面**：首页周卡（`WeekCard`）、药品编辑弹窗（`SettingsSubpages`）、医生报告（`ReportOptionsScreen` / `ReportPreviewScreen`）、备份导入预览弹窗（`SettingsDataManagementScreen`）、桌面小部件（`BloodPressureWidget`）
- **相关组件**：`WeekSelectedDayBar`、`MedicationEditorDialog`、`ReportOptionSwitchRow`、`PrivacyOption`、`Checkbox` 行、`BloodPressureWidget`
- **问题描述**：
  1. **低于 48dp 的触控高度**：首页周卡「查看当天 >」（`WeekCard.kt#L396`）与编辑药品弹窗「删除这种药品」（`SettingsSubpages.kt#L814`）硬编码 `heightIn(min = 44.dp)`，低于 Material 3 要求的 `48.dp`（即代码自身定义的 `AppDimensions.minimumTouchTarget`）；桌面小部件 `4×2 / 4×4` 右上角的 `+ 记一次` 胶囊（`BloodPressureWidget.kt#L259-L271`）字号 `11.sp` + 垂直内距 `4.dp`，总高度仅约 **`24dp`**，手指稍偏即误触外层小部件打开首页而非新增页。
  2. **开关与复选框整行不可点**：`ReportOptionsScreen`（3 行隐私开关）、`ReportPreviewScreen`（3 行隐私开关）以及 `SettingsDataManagementScreen` 导入范围预览弹窗（5 行 `Checkbox`）均把点击事件仅绑在右侧小控件上，点击左侧大段文字毫无反应，且屏幕阅读器无法将标签与开关状态合并朗读（与设置页规范的 `SettingSwitchRow` 体验割裂）。
- **源码证据**：
  - [WeekCard.kt](file:///E:/blood%20pressure%20record/app/src/main/java/com/example/bloodpressurerecord/ui/home/WeekCard.kt#L396)、[SettingsSubpages.kt](file:///E:/blood%20pressure%20record/app/src/main/java/com/example/bloodpressurerecord/ui/settings/SettingsSubpages.kt#L814)
  - [ReportOptionsScreen.kt](file:///E:/blood%20pressure%20record/app/src/main/java/com/example/bloodpressurerecord/ui/report/ReportOptionsScreen.kt#L244-L268)、[ReportPreviewScreen.kt](file:///E:/blood%20pressure%20record/app/src/main/java/com/example/bloodpressurerecord/ui/report/ReportPreviewScreen.kt#L323-L333)
  - [SettingsDataManagementScreen.kt](file:///E:/blood%20pressure%20record/app/src/main/java/com/example/bloodpressurerecord/ui/settings/SettingsDataManagementScreen.kt#L440-L484)
  - [BloodPressureWidget.kt](file:///E:/blood%20pressure%20record/app/src/main/java/com/example/bloodpressurerecord/widget/BloodPressureWidget.kt#L259-L271)
- **审计依据**：
  - `design-audit/references/mobile-app.md`（Material Design minimum：`48×48dp`）
  - `design-audit/references/human-factors.md`（Fitts's Law：整行可点大幅扩大有效目标宽度 `W`）
- **改进方案**：
  1. 将两处 `44.dp` 替换为 `AppDimensions.minimumTouchTarget (48.dp)`；将桌面小部件 `+ 记一次` 高度增至至少 `36–40dp`（受小部件头部高度限制时扩大透明外围点击 `padding`）并字号升至 `12.sp`。
  2. 为医生报告 6 处开关行与备份恢复 5 处复选框行统一挂载整行 `.toggleable(value, enabled, Role.Switch / Role.Checkbox, onValueChange)` + `heightIn(min = 48.dp)`，内部 `Switch/Checkbox` 设 `onCheckedChange = null`。
- **预期收益及验证方式**：
  - **收益**：大幅提升中老年用户在报告导出、数据恢复与桌面小部件上的点按命中率，统一 TalkBack 整行朗读体验。
  - **验证**：在 `ReportScreenTest` 中执行 `onNodeWithText("包含备注").performClick()` 验证整行可切换开关状态。

---

#### `AUD-24` · 顶栏右侧操作按钮光学内缩、图标与文字用空格字符硬拼、圆角嵌套不同心及全宽分割线穿刺

- **问题性质**：规范偏差（微观视觉工艺与对齐细节，截图 #1、#2、#4、#6 实锤）
- **优先级**：**P2**
- **所在页面**：首页、历史记录、趋势、我的
- **相关组件**：`AppTopBar`、`MeasurementSlot`、`MedicationRow`、`SettingsGroup`、`CalendarMonth`
- **问题描述**：
  （详细几何拆解见专项报告 `02-VISUAL-ALIGNMENT-AUDIT.md`）
  1. **顶栏右侧图标/文字光学内缩**：历史页顶栏搜索 `IconButton`（`48dp` 透明框含 `24dp` 图标）与趋势页「全屏」`TextButton`（含 `12dp` 水平内距）直接贴在 `20dp` 页边距内侧，导致可见图形右缘比下方卡片右缘向内缩进约 **`12dp`**（见截图 #2、#4）。
  2. **空格字符硬充当图标间距**：`MeasurementSlot` 使用 `" $label · $time"` 字面量空格拉开太阳/月亮图标与文字间距，随字体与字号缩放比例失控。
  3. **嵌套圆角不同心**：`MedicationRow` 外圆角 `22.dp`、内边距 `12.dp`，内部 `44.dp` 药丸底座与右侧打卡按钮却用了 `15.dp` 圆角（同心公式 `R_inner = R_outer - Padding = 22 - 12 = 10dp`），导致四角弧度外薄内鼓。
  4. **设置分组分割线左右顶死**：截图 #6「显示」卡片内 4 条 `HorizontalDivider` 直接贯穿整个卡片宽度，既穿过了左侧圆形图标下方的留白（违背列表缩进惯例），也制造了过多横向切割线。
  5. **日历月份标题与下拉箭头异色**：截图 #2 `2026年11月 ▾` 因套在 `TextButton` 中，文字继承了 `primary` 陶土红，而右侧 `ArrowDropDown` 手动指定了 `onSurfaceVariant` 灰色，同属一个下拉触发器却一红一灰。
- **源码证据**：
  - [HistoryScreen.kt](file:///E:/blood%20pressure%20record/app/src/main/java/com/example/bloodpressurerecord/ui/history/HistoryScreen.kt#L266) & [#L596-L630](file:///E:/blood%20pressure%20record/app/src/main/java/com/example/bloodpressurerecord/ui/history/HistoryScreen.kt#L596-L630)
  - [TrendScreen.kt](file:///E:/blood%20pressure%20record/app/src/main/java/com/example/bloodpressurerecord/ui/history/TrendScreen.kt#L154)
  - [DashboardScreen.kt](file:///E:/blood%20pressure%20record/app/src/main/java/com/example/bloodpressurerecord/ui/home/DashboardScreen.kt#L733) & [#L841-L850](file:///E:/blood%20pressure%20record/app/src/main/java/com/example/bloodpressurerecord/ui/home/DashboardScreen.kt#L841-L850)
  - [SettingsScreen.kt](file:///E:/blood%20pressure%20record/app/src/main/java/com/example/bloodpressurerecord/ui/settings/SettingsScreen.kt#L197)
- **审计依据**：
  - `design-review-plugin/skills/design-review/knowledge-base.md`（Разделители 分割线降噪、Оптический вес 光学对齐、同心圆角与标准间距）
- **改进方案**：
  1. 顶栏右侧操作统一采用与左侧 `AppBackIconButton` 对称的 `44–48dp` 浅底圆角胶囊按钮（如搜索按钮加 `surfaceVariant` 底座使右外框与 `20dp` 页边线对齐），或为纯透明图标施加负外偏移对齐光学右边缘。
  2. 将 `" $label · $time"` 改为 `Arrangement.spacedBy(4.dp)`；校准 `MedicationRow` 外圆角 `20.dp` + 内边距 `12.dp` + 内圆角 `12.dp`（或胶囊按钮）。
  3. `SettingsGroup` 内的 `HorizontalDivider` 增加 `Modifier.padding(start = 74.dp, end = 16.dp)` 与文字左边缘对齐；`CalendarMonth` 下拉箭头改为与年份标题一致的 `LocalContentColor.current`（或标题用 `onSurface`、箭头用 `primary` 胶囊衬底）。
- **预期收益及验证方式**：
  - **收益**：大幅提升全应用的像素级精致度与对齐秩序感。
  - **验证**：对照 `02-VISUAL-ALIGNMENT-AUDIT.md` 逐项走查左右边缘参考线与同心圆角。

---

#### `AUD-29` · 医生报告预览页重复平铺与配置页完全相同的 3 个隐私开关，抢占首屏报告预览空间

- **问题性质**：规范偏差（渐进式披露与空间利用，源码确认）
- **优先级**：**P2**
- **所在页面**：医生报告预览页（`ReportPreviewScreen`）
- **相关组件**：`ReportPreviewScreen` 顶部说明与 `PrivacyOption`
- **问题描述**：
  用户在 `ReportOptionsScreen` 已经刚刚确认过「包含姓名与年龄 / 包含备注 / 包含药品」三个开关并点击「生成预览」；进入 `ReportPreviewScreen` 后，除了底部常驻了较高的双层导出操作栏外，列表第 1 项又完整平铺了说明文案与同样的 3 行 `Switch`，导致用户真正想核对的 **A4 PDF 第 1 页画面被挤压到屏幕下半截**。
- **源码证据**：
  - [ReportPreviewScreen.kt](file:///E:/blood%20pressure%20record/app/src/main/java/com/example/bloodpressurerecord/ui/report/ReportPreviewScreen.kt#L274-L291)
- **审计依据**：
  - `design-audit/references/information-architecture.md`（Progressive Disclosure：按需展开次级调整项，突出当前步骤核心内容）
- **改进方案**：
  在 `ReportPreviewScreen` 顶部将 3 行开关收折为一行紧凑的当前状态摘要条（如 `"已隐藏姓名年龄 · 含备注与药品  [调整隐私 ▾]"`，点击原地展开 3 行开关或打开轻量面板），把首屏 75% 以上面积直接让给第 1 页报告大图。
- **预期收益及验证方式**：
  - **收益**：进入预览页第一眼即可完整看清报告摘要页主体，仍保留随时微调隐私的能力。
  - **验证**：在 `ReportPreviewScreen` 检查首屏第 1 页 PDF 露出比例。

---

#### `AUD-30` · 清理未使用的设计常量、文档版本滞后及零散非标奇数尺寸

- **问题性质**：规范偏差 / 工程整洁度（源码确认）
- **优先级**：**P3**
- **所在页面**：跨页面（`DesignSystem.kt`、`Color.kt`、`DESIGN.md`、`PRODUCT.md`）
- **相关组件**：设计令牌与设计说明文档
- **问题描述**：
  1. `AppDimensions.primaryButtonHeight = 56.dp`（注释写 `60dp，全药丸`）在全工程 **0 次引用**，实际按钮硬编码 `54.dp` 与 `20.dp` 圆角；`Color.kt` 中 `WarmTextFaint` 与 `WarmTextMuted` 同值重复，`Terracotta100/300/500/600/Dashed`、`Sage300/500/SageLine` 等在 Compose 代码中未被引用。
  2. `DESIGN.md` 与 `PRODUCT.md` 仍停留在 `2.3.11 (versionCode 52)`，落后于当前 `build.gradle.kts` 的 `2.3.13 (versionCode 54)`。
  3. 存在 `RoundIconBadge 46.dp`（FormFactor 点名反例）、`calendarDaySize 38.dp`（圆角 `13.dp`）、`Buddy 82×78.dp` / `54.dp`、`StatusChip padding 9.dp / 5.dp`、`SegmentedControl padding 5.dp`、`HighRisk padding 11.dp` 等非 `4dp` 网格零散奇数尺寸。
- **源码证据**：
  - [DesignSystem.kt](file:///E:/blood%20pressure%20record/app/src/main/java/com/example/bloodpressurerecord/ui/theme/DesignSystem.kt#L28-L35)、[Color.kt](file:///E:/blood%20pressure%20record/app/src/main/java/com/example/bloodpressurerecord/ui/theme/Color.kt#L12-L46)、[AppComponents.kt](file:///E:/blood%20pressure%20record/app/src/main/java/com/example/bloodpressurerecord/ui/common/AppComponents.kt#L804)、[DESIGN.md](file:///E:/blood%20pressure%20record/DESIGN.md#L4)
- **审计依据**：
  - `design-review-plugin/skills/design-review/knowledge-base.md`（Общепринятые размеры：`2, 4, 8, 12, 16, 20, 24, 32, 40, 48, 56, 64`；`46, 38` 为典型非标值）
- **改进方案**：
  按 `03-DESIGN-SYSTEM-STANDARD.md` 归并尺寸令牌（如 `RoundIconBadge` 规整为 `44.dp` 或 `48.dp`，`calendarDaySize` 规整为 `40.dp` 圆角 `12.dp`，`primaryButtonHeight` 统一绑定 `56.dp`），清理死令牌并同步更新文档版本号。
- **预期收益及验证方式**：
  - **收益**：实现“文档 — Token — 组件代码”三位一体零偏差。
  - **验证**：静态检查确认 `AppDimensions` / `AppSpacing` 无死常量、按钮统一引用高度令牌。

---

#### `AUD-31` · 「我的」页「使用指南与更新」副标题括号版本号断行产生视觉悬挂缩进

- **问题性质**：主观设计建议（截图 #6 实锤）
- **优先级**：**P3**
- **所在页面**：我的（`SettingsScreen`）
- **相关组件**：`SettingRow`（「使用指南与更新」）
- **问题描述**：
  截图 #6 中，「使用指南与更新」副标题为 `"快速上手、血压标准、隐私与版本更新（v2.3.13）"`。在常规屏宽下恰好从全角左括号处折到第二行显示 `（v2.3.13）`，全角左括号左侧留白造成第二行视觉缩进半个汉字，破坏左对齐整齐度。
- **源码证据**：
  - [SettingsScreen.kt](file:///E:/blood%20pressure%20record/app/src/main/java/com/example/bloodpressurerecord/ui/settings/SettingsScreen.kt#L254-L258)
- **审计依据**：
  - `design-review-plugin/skills/design-review/knowledge-base.md`（Типографика：换行孤字与标点悬挂细节控制）
- **改进方案**：
  将版本号从长副标题末尾剥离，改用中点连接 `"快速上手、血压标准与隐私说明"` + 在标题行右侧（`>` 箭头前）以轻量胶囊或 `labelMedium` 展示 `v2.3.13`，既一眼可见当前版本号，又让副标题单行清爽不换行。
- **预期收益及验证方式**：
  - **收益**：消除断行括号缩进，版本号展示更符合移动端设置页惯例。
  - **验证**：在 `360dp–412dp` 屏宽下检查该行排版。

---

#### `AUD-32` · 底部悬浮 Dock 向上滚动展开阈值（`18.dp`）偏灵敏，微调趋势图纵向视口位置时易频繁弹出遮挡图表下沿

- **问题性质**：主观设计建议（截图 #4 观察 + 源码确认）
- **优先级**：**P3**
- **所在页面**：四个顶级页面（重点影响 `TrendScreen` 图表浏览）
- **相关组件**：`DockScrollState`
- **问题描述**：
  `DockScrollState` 设定向下滚动 `28.dp` 收起为右下角小圆按钮，向上回滚仅 **`18.dp`** 即重新展开全宽 `64.dp` 胶囊。在趋势页（如截图 #4）用户上下微移页面将折线图居中查看时，手指轻微回拉超过 `18dp` 就会触发全宽 Dock 弹出盖住折线图底部时间轴。
- **源码证据**：
  - [DockScrollState.kt](file:///E:/blood%20pressure%20record/app/src/main/java/com/example/bloodpressurerecord/ui/common/DockScrollState.kt#L73)：`DockScrollState(28.dp.toPx(), 18.dp.toPx())`
- **审计依据**：
  - `design-audit/references/human-factors.md`（防误触滞回区间 Hysteresis 设计）
- **改进方案**：
  将向上回滚展开阈值从 `18.dp` 适度上调至 `36.dp`（当滚动容器已到达页面最顶部 `!canScrollBackward` 时立即强制展开），兼顾回顶即时唤出与中途读图抗抖动。
- **预期收益及验证方式**：
  - **收益**：浏览趋势图与长历史列表时，悬浮导航不再因手指微调而频繁弹缩抢镜。
  - **验证**：在趋势页中段小幅上下拖动 `<30dp` 验证 Dock 保持紧凑态，大幅上滑或回顶立即展开。

---

#### `AUD-33` · 趋势页「达标率」圆环卡片无障碍合并朗读先读百分比「64%」再读标题「达标率」

- **问题性质**：主观设计建议 / 无障碍细节优化（源码确认）
- **优先级**：**P3**
- **所在页面**：趋势（`TrendScreen`）
- **相关组件**：`TrendPeriodOverview` 达标率卡片
- **问题描述**：
  达标率 `Row` 使用 `Modifier.semantics(mergeDescendants = true) {}`，因左列 `Box` 包含 `Text("64%")`、右列 `Column` 包含 `Text("达标率")` 与说明，TalkBack 按子节点顺序朗读为 `"64%，达标率，按单次记录计算……"`，先报孤立数字再报指标名。
- **源码证据**：
  - [TrendScreen.kt](file:///E:/blood%20pressure%20record/app/src/main/java/com/example/bloodpressurerecord/ui/history/TrendScreen.kt#L384-L435)
- **审计依据**：
  - `design-audit/references/ux-heuristics.md`（WCAG 2.1 Understandable & Screen Reader Reading Order）
- **改进方案**：
  使用 `clearAndSetSemantics { contentDescription = "达标率 ${insights.targetRate?.let { "$it%" } ?: "暂无"}，$targetText$comparison" }` 显式组织为“指标名 → 数值 → 解释与环比”的自然语序。
- **预期收益及验证方式**：
  - **收益**：TalkBack 用户收听统计信息更符合中文语序直觉。
  - **验证**：编写语义节点测试断言该容器 `ContentDescription` 以 `"达标率"` 开头。

---

## 六、待视觉验证事项清单（Pending Real-Device Visual Verification）

遵循审计原则*“对必须依赖实际界面截图才能确认的问题，单独标记为待视觉验证，不将推测描述为事实”*，本次用户上传的 6 张截图均为**浅色模式、标准字号（大字模式关闭）、有真实数据态**下的 4 个顶级页面。以下 **3 大类共 11 个界面场景**已在源码层面完成逻辑审查，后续需补充实机/模拟器截图完成闭环视觉验收：

### `AUD-34` · 深色模式（Dark Theme）全页面实机视觉与对比度验收（优先级：P3 · 待视觉验证）
- **关联源码**：`ui/theme/Theme.kt#L53-L82`、`DesignSystem.kt#L179-L202`、`BpRangeTokens.kt#L33-L39`
- **待验证点**：
  1. 首页 `ReadingHero` 六种分级在 `#1A1512` 暖棕暗底上的 `alpha = 0.24f` 混色质感是否通透不发脏（避免 FormFactor 所称“带灰泥感的脏暗色”）。
  2. 首页小压（`Buddy`）固定浅色身体与腮红在深色气泡旁是否自然融合。
  3. Android 三键导航栏（如当前截图设备的底部三键栏）与状态栏图标在手动切换深/浅色时的反色同步表现。

### `AUD-35` · 极端大字模式（`1.15x` 应用大字 × 系统大字 → 最高 `2.0x`）与小屏/软键盘弹出态验收（优先级：P3 · 待视觉验证）
- **关联源码**：`MainActivity.kt#L56-L58`、`ui/record/AddMeasurementScreen.kt`、`ui/home/BpZoneTargetSheet.kt`、`ui/history/TrendFullscreenScreen.kt`
- **待验证点**：
  1. **新增/编辑测量页（键盘弹出态）**：软键盘占去下半屏后，`CompactNumberField`（`76.dp` 最小高、`32–38sp` 动态字号）内三位数字与上下游标是否贴边，焦点自动跳格至第 2 组时页面自动滚动定位是否把上方标签完整露出。
  2. **四色条目标设置底部面板（`BpZoneTargetSheet`）**：在 `1.25x`（未触发 `1.3f` 单列堆叠阈值前）下，`正常起点 / 偏高起点 / 过高起点` 三列并排 `OutlinedTextField` 的浮动标签是否换行截断。
  3. **横屏全屏趋势页（`TrendFullscreenScreen`）**：挖孔屏横屏 `safeDrawingPadding()` 下，顶部可滚动工具栏 + 右侧 `42%` 宽读数区 + 底部 `26%` 高脉搏副图的纵向空间分配（会否触发 `compactPulseAxis` 隐藏时间轴标签）。
  4. **记录详情页、备注搜索页及各类确认弹窗**在 `2.0x` 合成倍率下的折行与滚动逃生按钮可见性。

### `AUD-36` · 桌面小部件、系统锁屏通知及医生报告 PDF 导出物实机验收（优先级：P3 · 待视觉验证）
- **关联源码**：`widget/BloodPressureWidget.kt`、`reminder/ReminderReceiver.kt`、`export/report/PdfReportRenderer.kt`
- **待验证点**：
  1. 国产主流桌面（OriginOS / HyperOS / ColorOS / HarmonyOS）下 `2×2`、`4×2`、`4×4` 三档小部件的实际分配 `DpSize` 与圆角裁剪、迷你折线图清晰度。
  2. 锁屏状态下的服药隐私通知（`VISIBILITY_PRIVATE` + `publicVersion`）与点按后弹出的 `MedicationReminderConfirmationDialog` 核对流程。
  3. `PdfReportRenderer` 生成的多页 A4 PDF 与 150 DPI 摘要长图在微信转发预览及黑白激光打印机纸质输出下的灰阶层次与字号可读性。

---

## 七、快速高收益改进项（Quick Wins — Top 5）

以下 5 项改动代码量极小（每项仅涉及 5–25 行属性/布局微调，零业务逻辑风险），却能立竿见影消除当前最显眼的体验硬伤：

1. **修复趋势页「复位视」截断（`AUD-01`）**：将 `TrendScreen.kt#L799` 的 `ReadoutAction` 从强制 `64dp` 最小宽的 `TextButton` 改为紧凑自适应点击容器（保留 `48dp` 高），5 个操作按钮在常规屏幕瞬间完整同排显示。
2. **补齐浅色 `outline` 对比度至 `3.77:1`（`AUD-02`）**：在 `Color.kt` 定义 `WarmOutline = Color(0xFF8A8177)` 赋给 `LightColors.outline`，并移除 `CompactNumberField` 硬编码描边，全应用输入框与未开启开关轮廓即刻清晰。
3. **打通「我的」页达标值磁贴点击（`AUD-04`）**：在 `SettingsScreen.kt#L112` 将 `onClick = onOpenProfile` 移至外层 `DataCard`，瞬间激活两块达标值磁贴的点击跳转。
4. **修复历史日历「今天+选中」双重描边脏色（`AUD-05`）**：将 `HistoryScreen.kt#L848-L860` 的两个 `if` 改为 `when { selected -> primaryBorder; today -> todayBorder }`，默认选中的今天立即恢复干净陶土红边框。
5. **消除首页大卡读数区 `6.dp` 左缩进断层（`AUD-06`）**：对齐 `DashboardScreen.kt#L412` 可点击读数区与小压、四色区间条的水平基准线，让首页第一视觉中心恢复利落对齐。
