# Blood Pressure Record 实施前独立技术审查

## 1. 独立技术审查结论

**CONDITIONAL PASS（有条件准入）**。基线为工作区 `dce088c` 和 `app/build.gradle.kts` 的 2.3.13 / 54。36 项都有决策和工作包，产品方向在现有 Compose 架构上可实施。**有 3 项实施前阻断条件**，分别涉及 Excel 直达的状态迁移、报告输出的隐私契约和删除撤销的提示调度。阻断针对相应工作包，独立的低风险工作包在授权后可先行。另有若干已定位的源码事实偏差、共享文件冲突及待真机验证事项。

本次以决策记录为裁决依据，以源码和既有测试为现状证据。审查范围为四份最终文件、原始 01 问题报告、02 视觉对齐、03 设计系统、04 实施路线图，以及根目录 DESIGN.md、PRODUCT.md。源码表中的路径均相对 `app/src/main/java/com/example/bloodpressurerecord/`，测试简写类名分别位于 `app/src/test/java` 或 `app/src/androidTest/java`。风险按潜在回归后果评定，与原审计优先级独立。

## 2. 四份最终文档一致性

| 文档 | 核查结果 |
| --- | --- |
| DESIGN-DECISION-RECORD | 逐条统计无重号或缺号。ACCEPTED 为 01、04、06、09、12、15、19、21、23、31、33。VERIFY 为 34–36。其余 22 项为 MODIFIED。AUD-21 按记录维持 ACCEPTED，具体范围受 D6-1/D7-1 收敛。 |
| DESIGN-SYSTEM-STANDARD-FINAL | 关键批准值与决策一致。若干“源码保留”及全局约束需要按第 4 节校准，尤其是医学警示表述、日历视觉尺寸与热区、字阶与主题覆盖范围。 |
| OPTIMIZATION-ROADMAP-FINAL | 36 项均可追踪。D7-1 的关键分支有结果描述，缺少从现有分卷弹层迁移到 SAF 的具体门控。D6-3 的文件清单只列预览页，验收实际跨 ViewModel 与输出端。并行和依赖建议见第 5 节。 |
| VISUAL-VERIFICATION-CHECKLIST | VERIFY 三项、三个待冻结参数及跨阶段隐私验证均有对应编号。须按第 7 节补充失败路径、实际组件视口和撤销时序。Android 可运行环境与不适用环境应分别记录。 |

已冻结的规格包括 `12/16/20/26/32dp` 圆角、普通按钮 54dp 和保存按钮 58dp、工具页 68dp、明确批准的深浅色色值、普通主题 200ms 与减弱动效瞬时按压 0.84，以及数据管理与报告的业务方向。FRZ-01 的向上展开 28dp、FRZ-02 的浅/深脉搏色、FRZ-03 的小部件实际命中区域待实测。Dock 收起 28dp 和回顶展开行为已经确定。六级分级混色、极限布局、系统栏、锁屏及外部输出是验收事项。个别实施示例如四色条衬底 alpha 0.78 和日历描边使用 mid 或 ink 应结合批准的语义及实测判定。

原始报告的采纳和拒绝可从逐 AUD 记录和第三节纠偏表追溯。实施须保留其中的明确裁决，包括保留深色 outline、保留成熟尺寸及 11sp 密集例外、主分段 16sp、首页独立问候及历史 hideOnScroll、日历取消双击、历史列表读数首行不加单位、录入不套卡、达标条无最小段宽、Excel 为主、仅两处三动作弹窗、单条时段保留清晨及版本外置。深色色板调整、手势阈值和组件合并均以最终记录为边界。没有证据要求恢复原始 03/04 的替代方案。需要补录的连带边界和验收差异列在下文。

## 3. 36 项技术可行性及风险清单

| AUD / 分类 | 决策 / 工作包 | 真实路径与可行性、风险和验收 |
| --- | --- | --- |
| 01 A | D1-1 / 1.4 | **中** · `ui/history/TrendScreen.kt` 的 `TrendSelectionReadout` 用水平滚动，`TrendFullscreenScreen.kt` 也使用趋势读数能力。紧凑胶囊可行。360dp 页面扣 40dp 和卡内 32dp 后仅 288dp，按 5 项实际字体、内距和间距测量，更新旧的大字单行滚动断言。 |
| 02 M | D1-2 / 1.1 | **中** · `ui/theme/Theme.kt`、`Color.kt`、`ui/common/SessionFormComponents.kt`。`CompactNumberField` 改读 outline，核对焦点、错误、禁用态，量测真实底色对比。 |
| 03 M | D1-3 / 1.3 | **中** · `ui/common/AppComponents.kt`、`WheelTimePicker.kt`。保留 interactionSource 及常规弹簧，减弱模式只改变按压 alpha，验证取消、拖动和禁用均复原。 |
| 04 A | D1-4 / 2.3 | **低** · `ui/settings/SettingsScreen.kt` 资料卡合并为单一导航点击，覆盖达标磁贴和已有编辑入口，避免重复语义节点。 |
| 05 M | D1-5 / 3.2 | **中** · `ui/history/HistoryScreen.kt` 的 `CalendarDay` 使用互斥边框，并保留今天与选中两种语义。38dp 视觉格与有效触区单独验证。 |
| 06 A | D4-1 / 3.1 | **中** · `ui/home/DashboardScreen.kt` 的 `ReadingHero` 可按 14+6 对齐，注意点击热区、无脉搏及大字号时的 mmHg 基线。 |
| 07 M | D2-2 / 1.1 | **中** · `ui/theme/Theme.kt` 与第 4 节全部显式 medium/large 引用应同一批审查，避免边框、焦点和 clip 不同形。 |
| 08 M | D3-1 / 2.1 | **中** · `ui/common/AppComponents.kt`、`ui/history/TrendScreen.kt`。保留五范围、粒度和指标三个选择语义及选中项可达性，按实际宽度布局，见第 4 节。 |
| 09 A | D4-2 / 3.1 | **中** · `ui/home/DashboardScreen.kt`、`ui/settings/SettingsSubpages.kt`。`MedicationRow` 和编辑器只改视觉与用户主动追加的文本，已有单位、自由文本、历史剂量不得自动重写。 |
| 10 M | D4-3 / 3.1 | **中** · `ui/home/BpRangeBar.kt`、`BpRangeUiState.kt`、`BpZoneTargetSheet.kt`。用真实自定义状态标示，保持默认双字标签，避免把目标、区间位置和医学等级合并。 |
| 11 M | D5-4 / 3.3、4.1–4.2 | **中** · `ui/history/TrendChart.kt`、`TrendChartDrawing.kt`、`TrendScreen.kt`、`TrendFullscreenScreen.kt`。两种方向共用专色与目标语义，图上虚线保留，按当前指标和可空目标生成图例。 |
| 12 A | D5-1 / 3.2 | **低** · `ui/history/HistoryScreen.kt` 的图例和备注点/框使用对应描边和中性色，保留文字与备注状态。 |
| 13 M | D3-2 / 2.2 | **中** · `ui/common/AppComponents.kt`、`ui/history/HistoryScreen.kt`、`TrendScreen.kt`、`ui/settings/SettingsScreen.kt`、`ui/home/DashboardScreen.kt`。顶栏状态栏内距只能计算一次，顶层与子路由边界见第 4 节。 |
| 14 M | D6-1、D7-1 / 3.4（关联 2.3） | **高，B1** · `ui/settings/SettingsDataManagementScreen.kt`、`SettingsViewModel.kt`。UI 有两个连续弹层，单删确认状态无法实现单卷 Excel 直达。 |
| 15 A | D1-5 / 1.1、2.2 | **高，B3（提示部分）** · `ui/theme/Theme.kt`、`navigation/AppNavigation.kt`、`ui/home/DashboardScreen.kt`、`data/repository/DeletionUndoManager.kt`。主题配对已可扩展，根层提示迁移须保留撤销生命周期。 |
| 16 M | D5-2 / 3.2 | **中** · `ui/history/HistoryScreen.kt`、`HistoryDetailScreen.kt`。widthIn 放开最小宽度后仍需给核心读数和状态分配空间。列表视觉与 TalkBack 单位分别核对。 |
| 17 M | D5-3 / 2.1、3.2 | **中** · `ui/history/HistoryScreen.kt`、`ui/common/SessionFormComponents.kt`。保持独立 Compact 和本周/本月状态，在空/加载/错误态可选，均值字体改动不动统计来源。 |
| 18 M | D2-3 / 1.2 | **中** · `ui/theme/Type.kt`、`BpRangeTokens.kt`、`ui/home/WeekCard.kt`、`ui/common/SessionFormComponents.kt`。显式迁移硬编码与 M3 回退使用，见第 4 节。 |
| 19 A | D1-4 / 2.3、4.3 | **中** · `ui/home/WeekCard.kt`、`ui/settings/SettingsSubpages.kt`、`SettingsDataManagementScreen.kt`、`ui/report/ReportOptionsScreen.kt`、`ReportPreviewScreen.kt`、`widget/BloodPressureWidget.kt`。已符合的行复用，新增行角色和禁用状态绑定一致，小部件按实际可点击容器验收。 |
| 20 M | D6-4 / 3.5 | **中** · `ui/history/HistoryScreen.kt`、`HistoryViewModel.kt`、`ui/settings/SettingsSubpages.kt`、`SettingsViewModel.kt`、`export/report/PdfReportRenderer.kt`、`widget/BloodPressureWidget.kt`。限定用户可见文案，禁止全局替换持久化时段和导出列名。 |
| 21 A | D3-4、D6-1、D7-1 / 2.3、3.4 | **高，B1 涉及清空分支** · `ui/common/SessionFormComponents.kt`、`ui/record/AddMeasurementScreen.kt`、`ui/history/EditSessionScreen.kt`、`ui/settings/SettingsDataManagementScreen.kt`。保留清空勾选确认、三动作回调及表单焦点，警示 LiveRegion 避免重复读。 |
| 22 M | D5-4、D6-2 / 2.2、3.4 | **中** · `ui/history/TrendScreen.kt`、`ui/settings/SettingsScreen.kt`。仅取消指定折叠调用，保留下方折叠和公共 `ui/common/ExpandableSection.kt`。 |
| 23 A | D1-5 / 1.2 | **中** · `MainActivity.kt`、`ui/settings/SettingsViewModel.kt`、`data/datastore/AppSettingsStore.kt`。异步首帧门控可行，别把 Room、外围初始化或无期限流收集绑成阻塞条件，见第 4 节。 |
| 24 M | D2-5、D3-3、D4-2、D5-1、D6-2、D6-3 / 2.2、3.1、3.2、3.4 | **中** · `ui/common/AppComponents.kt`、`ui/home/DashboardScreen.kt`、`ui/history/HistoryScreen.kt`、`TrendScreen.kt`、`ui/settings/SettingsScreen.kt`、`ui/report/ReportPreviewScreen.kt`。6 子项分别验收，报告页底栏与正文边距应写明，保留书面光学例外。 |
| 25 M | D4-4、D5-1 / 3.1、3.2 | **中** · `ui/home/WeekCard.kt`、`DashboardScreen.kt`、`ui/history/HistoryScreen.kt`、`NoteSearchScreen.kt`、`navigation/AppNavigation.kt`。小压和气泡动作一致；日历定位复用现有滚动状态，与全局搜索定位分离。 |
| 26 M | D1-2、D4-3 / 1.1、3.1 | **低** · `ui/theme/DesignSystem.kt`、`ui/home/BpRangeBar.kt`。仅提亮 low.ink 并加衬底，基于 alpha 混合后的表面复算，六级×双主题真机核查。 |
| 27 M | D5-5 / 3.3 | **中** · `ui/history/TrendScreen.kt` 当前 3dp 段间隙和 5dp 独立圆角。可用统一裁剪和连续累计坐标，保持精确计数和极小占比的图例。 |
| 28 M | D4-5 / 3.4 | **中** · `ui/common/SessionFormComponents.kt` 的三栏输入及组标题修改只收紧无删除动作行，验证新增和编辑键盘、第二组自动跳格、草稿及保存。 |
| 29 M | D6-3 / 3.4、4.3 | **高，B2** · `ui/report/ReportPreviewScreen.kt`、`ReportViewModel.kt`、`ReportGeneration.kt`、`export/report/ReportShare.kt`。摘要和全部输出必须使用同一版隐私配置。 |
| 30 M | D2-4 / 1.1、2.3、3.4 | **低** · `ui/theme/Color.kt`、`Theme.kt`、`DesignSystem.kt`、`ui/common/AppComponents.kt`。删除零引用常量前全仓检索，Terracotta300 保留，根文档同步版本和实际行为。 |
| 31 A | D6-5 / 3.4 | **低** · `ui/settings/SettingsScreen.kt`、`AppInfoContent.kt`。兼容 SettingRow 其他调用者；版本来自 BuildConfig，debug 有后缀，实测断言按变体。 |
| 32 M | D3-5、D7-3 / 2.1、4.2 | **中** · `ui/common/DockScrollState.kt`、`navigation/AppNavigation.kt`、`ui/history/TrendChartGestures.kt` 和四个顶层页面。按活动页面真实回顶信号补充，而非扩大所有 nested scroll 来源。 |
| 33 A | D5-5 / 3.3 | **低** · `ui/history/TrendScreen.kt`。显式语义仅覆盖达标率摘要，保留分布图例可读，覆盖无环比及无可用统计场景。 |
| 34 V | D7-2 / 4.1 | **中，待实测** · `ui/theme/Theme.kt`、`MainActivity.kt`、`ui/home/DashboardScreen.kt`、`ui/history/TrendChart.kt`。暗色对比、过渡、系统栏及 FRZ-02。 |
| 35 V | D7-3 / 4.2 | **中，待实测** · `ui/common/SessionFormComponents.kt`、`ui/home/BpZoneTargetSheet.kt`、`ui/history/TrendFullscreenScreen.kt`、`ui/common/DockScrollState.kt`。测试有效倍率、IME、宽度和方向组合。 |
| 36 V | D7-4 / 4.3 | **中，待实测** · `widget/BloodPressureWidget.kt`、`reminder/ReminderReceiver.kt`、`export/report/ReportShare.kt`、`ReportPrintAdapter.kt`。外部应用与打印依赖真实环境，记录覆盖和缺口。 |

A = ACCEPTED，M = MODIFIED，V = VERIFY。工作包列省略统一前缀 WP-。

## 4. 源码事实偏差及实施阶段纠正

| 发现 / 风险 | 关联 AUD、决策、包 | 证据与建议 |
| --- | --- | --- |
| S1 · 中 · 圆角需按组件角色迁移 | 07、24 / D2-2、D2-5 / 1.1 | 直接 `medium` / `large` 引用共 15 处。 `ui/common/AppComponents.kt` 331/380/408 为三按钮，用新 medium 保持 20dp。436/445 为卡片焦点与形状，一起改 large 保持 26dp。651/760 为分段容器与选项，应按 20/16dp 分别处理。 `SessionFormComponents.kt` 269 大数字框用新 medium，459 选择面，609 普通输入用 small。 `WheelTimePicker.kt` 111 为选中底，按原 20dp 保留。 `ui/history/NoteSearchScreen.kt` 121 改 small。 `HistoryScreen.kt` 1011 高亮边与卡片同形用 large。 `TrendScreen.kt` 286 空态卡按内容卡角色用 large，901 私有分段外框随合并删除。另逐一核对未显式传 shape 的 OutlinedTextField（资料、口令、个人区间及表单），和已硬编码 26dp 的图表卡，防止误把所有 rounded shape 纳入重构。 |
| S2 · 中 · 补字阶与迁移字号是两件事 | 18、17、06 / D2-3、D5-3、D4-1 / 1.2、3.1、3.2 | `ui/theme/Type.kt` 当前 10 槽，显式引用实数为 labelSmall 19、titleSmall 5、headlineSmall 3、displaySmall 1、displayLarge 0。类型补齐会改变所有回退用法及 M3 内部依赖槽，且现有 copy 可覆盖字体、字号、字距。重点看 `update/UpdateUi.kt` 对 headlineSmall/titleSmall 的按钮与对话框影响，`ui/history/TrendChart.kt` 窄刻度、`TrendFullscreenScreen.kt` 读数区、`ui/home/BpZoneTargetSheet.kt` 浮动标签。ReadingHero 的 58/62 仍需显式接入新槽。实际令牌名是 BpRangeDimensions.labelSize，文件才是 BpRangeTokens.kt。 |
| S3 · 中 · “全通道”需界定范围 | 15、34 / D1-5、D7-2 / 1.1、4.1 | `ui/theme/Theme.kt` 现在动画化 14 个字段。方案明确追加 secondary、tertiary、error 各四角色及两个 outline。ColorScheme 还存在其它 M3 角色，且 BpGradePalette、BpRangeColors 和系统栏有独立来源。验收应覆盖列举字段，再走查可见跳变来源。保留 reducedMotion 提前返回 target 与 `AppMotion` 200ms 规格，禁止借“全通道”扩大色板调整。 |
| S4 · 中 · 启动门控实际依赖比路线图宽 | 23、34 / D1-5、D7-2 / 1.2、4.1 | `MainActivity.kt` 首次 compose 按 SettingsUiState 默认值套 Density、主题和系统栏；`ui/settings/SettingsViewModel.kt` 从 `data/repository/DefaultSettingsRepository.kt` 收 bundle，后者把 `data/datastore/AppSettingsStore.kt` 和其它源 combine。门控以轻量偏好流已就绪/失败为信号，避免等待 Room/药品与页面 VM，不复制独立读取或进行主线程 I/O。定义读失败的可恢复出口，避免永久启动面；保持 Activity 重建、系统主题变化和 pendingDestination/服药通知消费。真机同时观察系统启动面到首个主树帧、系统栏及启动延迟。 |
| S5 · 中 · 分段规格与调用语义不可机械映射 | 08、17、32 / D3-1、D5-3、D3-5 / 2.1 | `ui/history/TrendScreen.kt` 周期当前按实际字号测量并在不足时水平滚动，私有控件周期白滑块、粒度及血压指标为强调滑块。公共组件在 `ui/common/AppComponents.kt`，资料页 `ui/settings/SettingsSubpages.kt` 也调用。复用规则中明确各调用的 Primary/Compact、全屏共享的周期及粒度选择、窄屏焦点顺序和滚动后选中项可见。多行滑块不能继续假定 index×单格宽。历史近期保留独立状态，不把加载失败变成不可切换。 |
| S6 · 中 · 顶栏合并需处理两处滚动职责 | 13、22、24 / D3-2、D3-3、D5-4 / 2.2 | `ui/common/AppComponents.kt` 的 AppTopBar 负责可选 hideOnScroll 及安全区。 `ui/history/HistoryScreen.kt` 已根据 onBack 区分顶层，与列表回顶的展开效果须保留。 `ui/history/TrendScreen.kt` 顶栏目前在纵向 ScrollState 容器内，切换时保留自然滚出、非空才显示全屏及 onBack 子入口。 `ui/settings/SettingsScreen.kt` 保留自然滚出。不要重复套 20dp 或状态栏 padding，也别将下层表单、详情及数据页的 hideOnScroll 一并移除。 |
| S7 · 中 · Dock 回顶缺少页面信号及文件范围 | 32、13 / D3-5、D7-3、D3-2 / 2.1、2.2、4.2 | `ui/common/DockScrollState.kt` 只观察已消费的 UserInput 并返回 Zero，惯性明确忽略。 `navigation/AppNavigation.kt` 根层挂接且退出全屏主动展开。应从活动页面 LazyListState/ScrollState 观察真实从可向上滚动到顶部，并复位累计距离。补列 `ui/home/DashboardScreen.kt`、`ui/history/HistoryScreen.kt`、`TrendScreen.kt`、`ui/settings/SettingsScreen.kt`。排除离屏、首测量、空态与全屏的伪信号，保持 `TrendChartGestures.kt` 手势占用，验证回顶、恢复滚动位置、快速切 Tab、图内缩放/长按/拖动及普通滚页。 |
| S8 · 中 · 日历测试明确锁定被废弃手势 | 05、12、25 / D1-5、D5-1 / 3.2 | `ui/history/HistoryScreen.kt` 现有 pendingNoteScrollDate 由 CalendarDay 双击及无障碍 action 引发，按 calendarHeaders.size + 首条备注 index 跳转，找不到时回退 0。显式入口复用同日已加载且确有备注的路径，移除旧双击和重复语义，日期/模式切换要丢弃过期请求。 `navigation/AppNavigation.kt` 中 NoteSearch → HistoryLocation(sessionId) → 详情、返回与高亮是另一条链，保留独立状态和不可用提示。更新 CalendarAccessibilityTest 的双击用例，并保留今天/选择断言。 |
| S9 · 中 · 38dp 视觉格与 48dp 热区在窄屏有几何冲突 | 05、19、35 / D1-4、D1-5、D7-3 / 2.3、3.2、4.2 | `ui/history/HistoryScreen.kt` 7 列单元至少需要 336dp 才能实现 7 个 48dp 不重叠触区，360dp 扣两侧 20dp 后只有 320dp，未计卡内距。需在验收口径记录 38dp 存量例外及邻格命中验证，提交现有约束冲突给设计责任人确认，避免偷偷扩格或宣称全面达标。 |
| S10 · 中 · 医学警示与计数用词有文档歧义 | 14、20、27 / D6-1、D6-4、D5-5 / 3.3–3.5 | `domain/calculator/BloodPressureRules.kt` 为收缩压 ≥180 **或**舒张压 ≥120，与三级分级的 ≥180 或 ≥110 分开。标准的“≥180/120”应明确 OR，避免实现者误解。 `ui/settings/SettingsViewModel.kt` 的“晨间提醒”状态消息、备份计数可见文本在路线图未列，按最终术语范围逐字筛选。 `reminder/ReminderScheduler.kt` 的 action、ID、调度不得随文案替换，`domain/time/MeasurementPeriod.kt` 的标签直接用于入库，不批量替换。 |
| S11 · 低 · 图表与外延验收用词过强 | 11、27、36 / D5-4、D5-5、D7-4 / 3.3、4.1、4.3 | `ui/history/TrendChart.kt` 当前消费 drawThresholdLines 返回的目标标签占位 bounds，移出文字要同步清理相关避让参数，保留其它坐标标签布局。 `TrendScreen.kt` 分布段使用实际次数，按累计比例绘制，最后端点精确对齐全宽。亚像素或端点圆角可令极小段物理不可见，准确计数和图例兜底。 `reminder/ReminderReceiver.kt` 服药使用 PRIVATE/publicVersion，测量提醒本身为通用文案，验收按类型检查实际输出。微信是 PDF 接收端，PNG 是保存产物，不派生应用内新增 PNG 分享动作。 |
| S12 · 低 · 交付资料中的路径、基线和参数应使用实物 | 19、24、30、31 / D1-4、D2-4、D6-2、D6-3、D6-5 / 1.1、2.3、3.4 | `app/build.gradle.kts` 54/2.3.13，DESIGN.md 的 front matter 与正文及 PRODUCT.md 仍是 2.3.11/52。 `ui/theme/DesignSystem.kt` 当前 primaryButtonHeight 56dp，三按钮已有硬编码 54dp。 `ui/settings/SettingsScreen.kt` 的分割线按 16+46+14 推导并保留末端 16。 `ui/report/ReportPreviewScreen.kt` 正文与底动作区各有独立 16dp，按 D6-3 写明应用范围。 `SettingsDataManagementScreen.kt` 清空复选框已整行 toggleable，避免重复修复。 |
| S13 · 中 · 显式设置分组形状不随令牌变化 | 07、24 / D2-2、D6-2 / 1.1、3.4 | `ui/settings/SettingsScreen.kt:261,326,335,385,431` 分别有显式 22dp 和 24dp 形状，交换 AppShapes 不会迁移它们。按决策区分标准内容卡、独立提示和行级 ripple，逐个核对外框与内层 clip，保留未获批的视觉例外。 |
| S14 · 低 · 统计的时窗不适用于提醒 | 20、34 / D6-4、D7-2 / 3.5、4.1 | 最终规范 §12.1 同一行把提醒和统计与固定时窗并列。实际 `domain/calculator/SessionStatisticsCalculator.kt` 的 05:00–11:59 与 18:00–23:59 由 `ui/history/TrendInsights.kt` 和 `domain/report/ReportBuilder.kt` 复用，而 `reminder/ReminderScheduler.kt` 使用用户设置值。术语一致即可，验收明确按相应时窗或计划时间。V-34-01 将三级和高风险并列，增加一个仅达到三级而未触发急症提示的固定数据点。 |

## 5. 工作包依赖与调整建议

| 工作包 | 建议实施顺序、边界与退出条件 |
| --- | --- |
| 1.1 | 主题/轮廓/圆角随角色迁移合并，令牌和旧调用同批提交。退出条件为 S1、S3 与对比度复算。 |
| 1.2 | Type、启动门控可独立设计，和 1.1 的 CompactNumberField 改动由同一所有者分次落地，WeekCard 的字阶先于热区及图例。退出条件为字体引用矩阵和启动失败路径成立。 |
| 1.3 | 常规和减弱动效修改可先做，但 AppComponents、WheelTimePicker 与 1.1 和 1.4 有交叉。同文件可按函数分批，重叠节点串行。 |
| 1.4 | 依赖紧凑操作胶囊确定，完整测量趋势卡内可用宽度及正文；同步删除旧测试“2.0x 必须单行可滚动”约束。 |
| 2.1 | 分段合并和 Dock 可分别交付；与 2.2 共用 AppNavigation、HistoryScreen、TrendScreen、AppComponents；先合并三组分段调用与测试，再接活动页回顶信号或由同一导航所有者统筹。 |
| 2.2 | 顶栏、主图去折叠和 Snackbar 可独立验收。Snackbar 迁移须关闭 B3；趋势与历史的 UI 更改在 2.1 之后集成。 |
| 2.3 | 依赖基础令牌和 pressableScale，先出共用弹窗/按钮与表单行为。在 3.4 同一轮落地数据页清空和报告行交互，避免为即将折叠的开关改两次；小部件转入 4.3 签核。 |
| 3.1 | 依赖首页基础圆角、字阶、按压和首页外层结构，非所有 2.x 分支的逻辑前置。SettingsSubpages 的剂量位需与 3.5 文案串行。 |
| 3.2 | 依赖历史分段、顶栏和字阶，SessionFormComponents 的均值区独立于 3.4 的组标题，但应由单一文件所有者合并。 |
| 3.3 | 依赖趋势 1.4、2.1、2.2 集成，详细章节漏列 2.1。达标分布改绘制，不借机改统计。 |
| 3.4 | 建议拆为数据管理、报告隐私、设置/表单/文档三个交付单元。数据与报告分别关闭 B1/B2，互不依赖。设置依赖 2.2/2.3，文档以最终实现取值。 |
| 3.5 | 与已触及的 HistoryScreen、SettingsSubpages、SettingsViewModel、PDF、小部件串行收口。对其它文件无需等全部 UI 改造才能开始静态文案盘点。 |
| 4.1 | 集成双主题后进行，FRZ-02 深色与浅色分别取得证据，暗色通过不能代替 FRZ-02 全部签核。 |
| 4.2 | 集成窄屏、大字体、IME、横屏和 Dock 后进行。FRZ-01 同一构建实测并记录阈值及误触。 |
| 4.3 | 外延验证可与 4.1/4.2 在同一候选构建并行。需先落地实际小部件、报告和数据管理流，FRZ-03 以真实容器尺寸记录，缺设备保留未验证。 |

文件矩阵还应补入启动偏好来源、Dock 四页回顶接线、报告输出契约和文案同文件触点。最小重构限于已批准的组件与连带行为。无需全仓统一圆角、全站 Snackbar 统一扩及表单内部反馈，或将所有折叠容器重写。

## 6. 关键业务流程与数据安全

### B1 · 高 · 明文 Excel 直达与清空分支须先明确状态合同

关联 AUD-14、21，D6-1、D7-1，WP-2.3、3.4、4.3。源码 `ui/settings/SettingsDataManagementScreen.kt:83`、`:112`、`:246`、`:503`、`:655`；`ui/settings/SettingsViewModel.kt:148`、`:850`、`:918`、`:941`、`:986`、`:1277`。

- 无未完成计划时，Excel 主按钮 → requestBackupExport → showBackupExportConfirm → UI 生成默认 basename 并调用 prepareBackupExport → repository.prepareBackupExport → showCurrentExportVolume。后者设置 exportVolumeRequestId/FileName/当前卷数，现有 UI 仍弹“保存备份文件”或“保存第 n 卷”，再由按钮 beginBackupLocation → CreateDocument。仅删除第一个弹层，单卷 Excel 仍需第二次确认，与路线图和 V-36-05 不符。
- 应预先明确分流。明文单卷准备成功自动发出一次系统文件选择请求，分卷保留必要卷进度、继续或暂停指导和确认切换保护。明文风险说明常驻“导出与备份”第一层。命名用现有命名规则且选择时冻结，不用硬编码或让 UI 与 VM 分支重复计算。保留系统 CreateDocument 的 MIME、URI 及请求 ID 门控。取消、启动失败、Compose 重组或 Activity 重建不得自动再次拉起。当前取消会发新卷请求 ID 以供重试，直接绑定“所有新 ID 自动弹出”会导致重新拉起，应将一次用户发起的自动打开意图与可恢复状态区分。
- 核心状态为 activeDataAction/isDataActionRunning/isChoosingBackupLocation、exportVolumeRequestId、exportFileName、exportEncrypted、exportVolumeIndex/Count/Records/TotalRecords、pendingExportPlan/passphrase/baseName、pendingExportFormatSwitchToEncrypted、SavedStateHandle 的 picker ID 和 exportInProgressKey。保留 begin/finishExportBackupLocation、onBackupLocationLaunchFailed、onExportLocationResult、pauseBackupExport 与过期结果拒绝。成功只写当前匹配卷，未完可继续，全部成功或明确放弃才清理计划，口令数组继续按现有时机擦除。
- 同格式重入恢复当前卷。跨格式先弹保护，取消保留计划，确认才 discardExportPlan。密转明确认转到明文准备，明转密继续口令设置及确认。检查 prepareBackupExport 可直接丢弃不同格式计划的内部防线，UI 的直达入口不得绕过 request/confirm。仅删除 showBackupExportConfirm、dismissBackupExport、对应写入/分支和专属文案。保留 showExportPassphraseDialog、导入口令与范围/预览状态、pendingImportUri、OpenDocument 和加密库。
- 清空弹窗仍由 showClearConfirm、独立 acknowledged 与 confirmClearAll 管理。“先导出备份”当前调用 requestBackupExport，替换为 dismissClearAll 加局部滚动请求，按内容真实位置及收起顶栏偏移对齐导出区。关闭后 acknowledged 随消失清掉。无格式预选、无 SAF 自动打开、无自动清空或恢复弹窗。保留 busy 门控与显式永久清空动作。
- 实施前签核上述转移及失败语义，尤其确认分卷指导与明文直达之间的范围。测试同步删除 SettingsViewModelTest 中 `showBackupExportConfirm` 的陈旧断言，替换为准备次数及 requestId、有无分卷保护、暂停恢复的可观测断言。DefaultSettingsRepositoryAndroidTest 的 request 后手工 prepare 应按真实入口更新。底层 `data/repository/backup/BackupExportPlan.kt`、BackupExportService、BackupImportService、BackupCrypto、读写和数据库结构继续原样，针对 round trip 保留基线。

### B2 · 高 · 报告摘要改版须保持从配置到输出的同一版门控

关联 AUD-29、19、24、36，D6-3、D1-4、D7-4，WP-2.3、3.4、4.3。源码 `ui/report/ReportOptionsScreen.kt`、`ReportPreviewScreen.kt:84`、`ReportViewModel.kt`、`ReportGeneration.kt`、`data/repository/DefaultReportRepository.kt`、`domain/report/ReportBuilder.kt`、`export/report/ReportCache.kt`、`ReportShare.kt`、`ReportPrintAdapter.kt` 和 `navigation/AppNavigation.kt`。

- 设置入口调用 onScreenEntered，视生命周期走身份默认关闭和重进重置。returnToOptions 重置 ReportOptions，保留模板而清掉旧 artifact。摘要应从 state.options 和 hasProfileIdentity 计算真实状态，展开状态可局部存储，禁止第二套隐私状态。读取“没有身份资料”和用户关掉身份均不能显示开启。保留当前模板种类、原有备注/药品默认值及是否可选条件。
- changeOptions 更新状态并用 generationId 取消旧生成。生成快照经 repository 和 ReportBuilder，输出唯一缓存目录文件及同一 layout plan。`previewOptions` 置空使旧 artifact 立即不可导出，只有完成且 options/period 匹配才提升为当前。保留 isGenerating/isSaving/selectingOutput、失败后的 retry、迟到结果 discard 和旧页不展示策略。不要用仅“artifact 非空”作为 ready。
- 预览状态另使用 viewedSummaryPath/failedPreviewPath/renderedPages，以当前文件和可见页面渲染成功判定按钮。保存 PDF/PNG 用 beginOutputSelection、selectedOutputArtifact、finishOutputSelection 的 SAF 分支，在 VM 保存时再检查对象一致和 canExport。分享与打印在点击时也复核 VM 状态与对象身份。PDF 分享/保存使用 file；打印及摘要 PNG 从同一 artifact.plan 渲染，打印系统会异步调用 onWrite。必须把“最新”定义为用户启动输出时已通过门控的配置快照；后续隐私改动不能替换已发往接收方或系统打印队列的文件。明确已有分享的 24 小时缓存保留和清空时撤销 URI 的现有语义，避免承诺追溯撤回。
- 实施前将快切三开关、失败且回拨原值、外部 picker 忙、旋转/重进、四种产物都绑定同一快照列作验收。当前已有多层防线，静态审查未认定已发生泄露。允许只改预览页完成改版，只要保留已有契约；若改变生命周期或 callback 语义须同时核查 VM/输出测试。与其重做缓存，更应从现有 ReportViewModelTest、ReportScreenTest、ReportOutputTest 衔接测试。

### B3 · 高 · 全局单通道必须保留删除撤销的有效期和操作语义

关联 AUD-15，D1-5，WP-2.2、4.2。源码 `navigation/AppNavigation.kt:296`、`:389`、`:451`，`ui/home/DashboardScreen.kt:118`、`DashboardViewModel.kt:129`，`data/repository/DeletionUndoManager.kt:142`，`update/UpdatePlatformActions.kt`。

- 根 Scaffold 已同位挂普通 snackbar 与 undoSnackbar，首页还有独立 Host。首页服药反馈成功可能带反向打卡动作，失败反馈也需保留。根普通消息还包括删除结果、新增后的“再改改”凭据、资料、更新等。录入内部的组撤销/重试属于局部流程。
- 删除栈顶通过 token 展示 Long，点击调用 undoAsync，Dismissed 调用 onPromptDismissedAsync；后者仅在 token 仍为栈顶时清待撤销记录。源码无一个独立固定秒数 TTL，窗口实际由 Snackbar 持续时间、无障碍推荐超时和新 token 接管决定。普通提示若调用 currentSnackbarData.dismiss() 抢占撤销，会让栈提前清空。单一 HostState 的普通 FIFO 排队也不能自然满足高优先级要求。
- 开工前定稿调度契约。删除撤销优先并可抢占普通提示，显示期间普通消息延后或按明确策略合并。区分高优先级取消低优先级协程、新 token 接管、真实用户关闭或超时，保留撤销完成/提示更新以及旧 token 保护。保持“再改改”导航凭据和首页打卡反向操作，不让过期动作在新记录或页面上误执行。用有普通提示、删除后切 Tab、连续删除、撤销期内打卡、作废失败和超时验证提示结束与动作释放行为；沿用 DeletionUndoManagerTest 验证仓库栈合同。

### 医学、统计与持久化的实施边界

- AUD-09、10、14、20、26–29 / D4-2、D4-3、D4-5、D5-5、D6-1、D6-3、D6-4 / WP-3.1、3.3–3.5，**高影响**。 `domain/calculator/BloodPressureRules.kt`、CategoryCalculator、AverageCalculator、LocateBpZoneUseCase、TrendAggregationCalculator，`domain/time/MeasurementPeriod.kt`，`ui/history/TrendInsights.kt` 与 `domain/model/BpZoneConfig.kt` 提供医学分级、高风险、记录平均、时段、个人参考区间和达标统计的独立边界。此次批准范围只读这些输入。核对六级与急症边界、空值与零、日/周/月和早晚/昼夜口径、每次测量代表值、用户默认及自定义值均沿用。
- AUD-09、14、20、28 / D4-2、D4-5、D6-1、D6-4、D7-1 / WP-3.1、3.4–3.5，**高影响**。 `data/db/AppDatabase.kt`、`data/repository/DefaultBloodPressureRepository.kt`、DefaultSettingsRepository、`data/repository/backup/` 与 `ui/settings/SettingsSubpages.kt` 的保护线为数据库版本和实体、计量值、既有药品剂量和历史数据、数据恢复及 Excel 列结构全保留。剂量胶囊只辅助输入，不批量转换，用户明确执行清空仍走原仓库事务。
- AUD-27 / D5-5 / WP-3.3，**中**。 `ui/history/TrendScreen.kt` 分布按 categoryCounts 的真实分母计算边界。对空、单分类、1/184、顺序和舍入作检查；不加最小宽度，不插入占比的装饰间隙。圆角由整条裁剪。

## 7. 最小充分测试与验收策略

| 层级 | 实施后建议的最小集合 |
| --- | --- |
| 静态源码检查 | 对照 36 AUD、各状态生产者/消费者、主题槽与回退调用；核对 D6-1/D6-3/D7-1 的真实路由，检查构建版本与根文档。按 diff 审查医疗、数据表、时段、备份格式及提醒调度规则边界。 |
| 编译 | 共享组件、路由和状态签名整合后做一次 debug 编译。仪器测试需要时在同一构建批次编译测试 APK，集中运行设备用例。仅对有编译错误或实现修改的批次增量重跑。 |
| 针对性单元测试 | SettingsViewModelTest 的首次/断点/双向切换、准备失败、取消或过期结果、备份中断；ReportViewModelTest 的默认/重进、快速切换、失败、旧文件拒绝与 SAF；DeletionUndoManagerTest 及调度策略的针对性用例；达标比例、对比度、必要的文案断言。纯 UI 改动无需运行全部域测试，触及领域边界时选 HighRiskJudgeTest、CategoryCalculatorTest、AverageCalculatorTest、MeasurementPeriodTest、TrendAggregationCalculatorTest、BackupExportPlanTest/BackupCryptoTest 中受影响者。 |
| 针对性设备测试 | TrendReadoutAccessibilityTest 按实际卡内视口更新单行、折行与 bounds；CalendarAccessibilityTest 替换双击，覆盖今日/选中和备注入口；InteractionAndMotionRegressionTest 验证减弱动效、可见热区和语义；必要时复用 SessionFormComponentsTest、TrendChartInteractionTest、TrendFullscreenLayoutTest；数据链路使用 DefaultSettingsRepositoryAndroidTest，报告使用 ReportScreenTest 和 ReportOutputTest 的隐私、失败及产物子集。 |
| 必须真机 | 一次候选构建执行清单 4.1–4.3。最小覆盖深浅及系统动效、360dp/2.0x 与 IME、横屏图表、Dock 手感、TalkBack、SAF 单卷/分卷/取消/切换、锁屏通知、多尺寸小部件和输出隐私。报告使用含测试姓名年龄、备注和药品的本地虚构数据，对四路输出检查明确开/关和快切/失败。PDF 外部接收与黑白实打另记证据。 |
| 环境限制 | 记录设备、版本、有效 dp/字体倍率、主题、输出路径及结果。不具备厂商设备、微信接收端、兼容 Android 的 HarmonyOS 或物理打印条件时，标“未验证”。不兼容 Android 应用的环境记“不适用”。 |

按源码颜色静态复算，浅色 outline 在 surface、background、surfaceVariant 上分别约为 3.77、3.55、3.26:1，深色 outline 在 surface 和 surfaceVariant 上分别约 3.74、3.39:1。实际组件若有透明叠层，以叠加后的可见颜色复核。现有清单已覆盖主成功路径。新增验收要点是 360dp 的实际组件可用宽度、选点按钮 disabled/focus、首读偏好失败与启动基线、撤销队列时序、历史搜索返回定位、清空按钮仅滚动、SAF 取消或重建无重复拉起、生成失败不显示或输出旧文件。检查系统打印异步边界与摘要 PNG，避免只用 PDF 预览相似度认定已通过。源码检查可以确认已有测试的意图，无法建立运行结果。本轮按授权未执行构建、测试和模拟器。

## 8. 必须在实施前解决

1. **B1** · 将单卷明文直达、分卷、取消与进程恢复写入同一迁移契约，并明确清空分支只滚动定位。
2. **B2** · 固定报告生成、预览就绪、分享、SAF 与打印的快照和失败门控，明确输出启动时刻与缓存范围。
3. **B3** · 固定删除撤销优先、提示替换、超时及 token 生命周期，确保普通反馈与撤销不互相误取消。

三项是对原决策的工程约束补全，可以在授权后写入相应工作包实施说明。无需重新选择产品设计。

## 9. 可在实施阶段处理

按工作包修正 S1–S14 的源码路径、测试断言和文件归属。统一阶段间的重叠修改，避免扩大源码改造。涉及 38dp 日历格与 48dp 热区的验收冲突，在 WP-3.2 动工前于该包登记和确认验收口径。FRZ-01–03 只在取得对应设备证据后冻结。根文档 2.3.13/54 与最终现状同步由 WP-3.4 收口。

## 10. 最终实施准入

**CONDITIONAL PASS · 3 项实施前阻断条件。** 批准的 36 项范围可落地。B1–B3 关闭后，对应业务工作包可进入实施。高影响数据与隐私链路通过定向回归，且真机事项按实际设备与外部条件记录结果后，再签核实施验收。
