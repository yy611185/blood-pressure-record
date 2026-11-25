# 家庭血压记录 App · UI/UX 与动效总计划

> 状态核对日期：2026-11-25。本文是代码现状与后续验收清单；原始审计、冲突讨论和备选方案保存在 [UI/UX 审计](docs/archive/UI_UX_AUDIT.md)、[动效优化方案](docs/archive/MOTION_OPTIMIZATION_PLAN.md) 和 [用户体验优化方案](docs/archive/UX_OPTIMIZATION_PLAN.md)。这些归档描述的是当时的基线，不代表当前实现。

## 基线与范围

- Android 客户端 `com.yang.bloodpressure`：`versionName = 2.3.11`、`versionCode = 52`，`compileSdk / targetSdk = 36`，`minSdk = 26`；以 [app/build.gradle.kts](app/build.gradle.kts) 为准。调试版版本名追加 `-debug`，applicationId 追加 `.debug`。
- 本次仅依据 `app/src/main`、构建配置和目录核对实现；“已实现”表示代码中存在对应路径，不等于已完成自动化、无障碍或真机验收。
- 数据库当前为 Room v9；导出工作簿的 `export_format_version` 为 6，加密 `.bpx` 外层格式版本为 1。后续 UI 改动应保持既有数据和医学计算契约；详见 [备份加密设计](docs/specs/backup-encryption-design.md)、[首页区间条规格](docs/specs/bp-range-bar.md) 和 [医生报告规格](docs/specs/doctor-report.md)。
- 保留更新提示在录入、编辑、全屏趋势及备份等场景的避让，以及报告身份信息默认不包含、变更选项后重新生成的机制。不得仅凭本计划改变分级阈值或代表值计算。

## 已落地的交互决策

| 原决策 | 当前代码行为 | 主要依据 |
| :--- | :--- | :--- |
| D-01 新增保存反馈 | 保存后返回：上一页为顶层页面或不存在时导航到测量首页，否则返回上一页；全局短时 Snackbar 显示读数与分级，有有效记录 ID 时提供「再改改」。编辑保存返回详情页并提示「修改已保存」。旧 `SessionCompletedDialog` 定义仍在，但当前新增保存路径不调用。 | [AppNavigation.kt](app/src/main/java/com/example/bloodpressurerecord/navigation/AppNavigation.kt)、[AddMeasurementScreen.kt](app/src/main/java/com/example/bloodpressurerecord/ui/record/AddMeasurementScreen.kt) |
| D-02 补充情况 | 新增与编辑页面使用原地折叠的 `ExpandableSupplementCard`；编辑页保留旧备注的条件编辑入口。编辑页使用纵向多组表单。 | [SessionFormComponents.kt](app/src/main/java/com/example/bloodpressurerecord/ui/common/SessionFormComponents.kt)、[EditSessionScreen.kt](app/src/main/java/com/example/bloodpressurerecord/ui/history/EditSessionScreen.kt) |
| D-03 报告隐私 | 姓名与年龄、备注、药品开关位于报告选项页；姓名与年龄每次进入默认关闭，无资料时不可启用；按钮直接生成预览。代码仍保留旧确认弹窗及 `requestPreview()`，当前页面按钮不经过该路径。 | [ReportOptionsScreen.kt](app/src/main/java/com/example/bloodpressurerecord/ui/report/ReportOptionsScreen.kt)、[ReportViewModel.kt](app/src/main/java/com/example/bloodpressurerecord/ui/report/ReportViewModel.kt) |
| D-04 自然周 | 首页 `naturalWeekDates` 使用周一至周日；保留的 `weekStartSunday` 是兼容函数。 | [NaturalWeek.kt](app/src/main/java/com/example/bloodpressurerecord/domain/time/NaturalWeek.kt) |

详情删除后通过回调离开详情页，撤销提示由全局 `DeletionUndoManager` 驱动。报告预览的系统返回与顶栏返回均回到选项页。以上是当前行为，不再作为待产品选择事项。

## 已有实现

### 正确性与核心流程

- 首页区间条按 `HIGH > ELEVATED > LOW > NORMAL` 处理两轴，未升高时单轴偏低优先于正常；配置边界和医学分级各自仍需按其规格理解。实现见 [LocateBpZoneUseCase.kt](app/src/main/java/com/example/bloodpressurerecord/domain/calculator/LocateBpZoneUseCase.kt)。
- 首页读数及已测早晚槽可进入详情，其他时段记录有入口；录入表单支持自动跳格并避让触摸浏览，额外读数组支持撤销。报告自定义周期使用日期选择器，7 日模板按起点计算终点并限制未来日期。
- 药品编辑有输入校验，新建药品默认 `08:00`；删除需二次确认并通过全局撤销管理器处理。数据管理支持 `.xlsx` / `.bpx` 恢复、口令显隐及清空确认。
- 桌面小部件使用深浅色 `ColorProvider`、高风险文字及录入入口；测量提醒支持直达录入，服药通知提供「已服用」操作。

### 动效与无障碍底座

- [AppLocals.kt](app/src/main/java/com/example/bloodpressurerecord/ui/AppLocals.kt) 监听系统动画时长比例为 0 的状态，主题提供 `LocalReducedMotion`；[AppMotion.kt](app/src/main/java/com/example/bloodpressurerecord/ui/theme/AppMotion.kt) 提供四档弹簧、颜色曲线、淡入淡出、方向切换和数值过渡。相关调用点可据此选择 `snap()` 或无过渡，不能据此推断所有动画均已覆盖。
- Dock 使用 `springGentle`，收起时清除隐藏内容语义，中央按钮有按压缩放；通用按钮提供 `pressableScale`。折叠区使用进度驱动展开，滚动顶栏的 `onPostFling` 使用剩余速度吸附。时间轮盘使用 `graphicsLayer` 插值；小压的浮动读取放在绘制层，并可关闭闲置动效。
- 新增页采用上下方向的导航过渡；报告选项与预览、历史月历使用方向过渡；全屏趋势宽屏明细有横向过渡。首页周图以实心和空心端点区分日均高压和低压，两端的位置随 `springGentle` 过渡；首页四色条游标使用弹簧，系统关闭动画时可即时落位。
- `LocalBpGradePalette` 在 `ui/theme/DesignSystem.kt` 定义并由主题提供；`tabularNums()` 已提供并用于部分文本，不应表述为全应用数值均已覆盖。应用字号叠加设置了上限；具体排版与对比度仍需设备验收。

## 待核验与后续工作

以下是验收任务或候选清理，不代表已发现故障，也不代表所有历史方案都尚未实现。

1. 按下表验证不同字号、输入法、系统导航和 TalkBack 场景，记录设备、系统版本及实际结果。尤其检查全局撤销提示是否避让 Dock 与系统导航栏、表单自动跳格是否干扰触摸浏览。
2. 系统动画设为 0x 时逐一检查导航、折叠、图表、时间轮盘和小压；检查趋势手势跟手与动画中断。当前只确认代码中存在减少动效入口及部分调用，不能宣称全站均为 0ms。
3. 核对报告隐私切换后的重新生成和导出状态、7 日选期、预览返回，以及端内图表、小部件和 PDF 的高低压配色；参照 [医生报告规格](docs/specs/doctor-report.md) 与 [全屏趋势规格](docs/specs/trend-fullscreen-2026-10-03.md)。
4. 如开展代码清理，先确认旧 `SessionCompletedDialog`、报告身份确认弹窗和 `requestPreview()` 的实际引用及测试契约，再决定是否删除；不要仅因为当前界面未调用便直接删除。其他原审计中提出的色值、文案、无障碍与性能指标，也应逐项核对，不把整阶段标为完成。

## 验证清单

代码修改后按影响范围运行相关测试；需要完整构建时可在项目根目录执行：

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest
```

上述命令包含 Android 测试 APK 构建，**不等于**已在设备上运行仪器测试。涉及区间定位、自然周、表单、编辑、报告、动效或趋势视窗时，应同时检查对应现有测试；新增测试以实际行为及回归风险为依据。

| 场景 | 重点 |
| :--- | :--- |
| 窄屏与大字 | 320dp、系统大字及应用大字叠加时，检查表单、Dock、搜索栏、横屏工具栏和报告操作区。 |
| 输入与键盘 | 检查录入焦点、连续输入、额外组撤销和资料页面软键盘遮挡。 |
| TalkBack | 检查读数、区间条、月历、趋势、轮盘及折叠区的焦点和播报，确认隐藏内容不接收焦点。 |
| 系统导航 | 三键与手势导航下检查返回路径和删除撤销提示的可见性。 |
| 动效与手势 | 0x 动画、快速反向操作、趋势平移缩放及触摸接管。 |
| 数据与报告 | 检查删除撤销、编辑保存、报告隐私开关、选期和跨端颜色。 |
| 大屏与设备差异 | 检查平板/折叠屏趋势侧栏，以及不同设备的排版和帧率表现。 |

本次为文档与代码静态核对，未运行上述构建、自动化测试或真机验收。
