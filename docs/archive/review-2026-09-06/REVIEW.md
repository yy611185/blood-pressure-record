# 血压记录项目 Review 与改进方案

审查日期：2026-09-06  
基线：commit `65ff5b1`，应用 1.8.2（versionCode 29），Kotlin / Jetpack Compose / Room v7。  
范围：主要生产源码、数据库迁移、备份、提醒与日历、导航与状态、UI 主题和可访问性、现有测试与发布流程。未修改应用源码；本目录仅包含审查结果和测试模拟器截图。

## 1. 结论与验证范围

建议保留现有 Kotlin + Compose + Room 架构，优先修复草稿与备份的数据可靠性，再统一 UI 的主题、字号适配和交互，最后按功能拆分状态管理。当前已有显式数据库迁移、事务写入、原始读数统一校验、导入预览、备份加密、按范围查询和 CI；这些基础值得继续使用。

最需先处理的事项是：保存草稿实际上不能跨页面退出恢复；部分本机数据无法通过备份完整往返；仪器测试编译已损坏；深色主题与大字号影响核心录入操作。

证据标识：**实测**表示本轮构建、模拟器操作或确定性数据演示；**静态确认**表示已核对完整相关代码路径，未对该异常场景做设备故障注入。建议与缺陷分开列出，不把所有维护建议当成已发生的故障。

### 验证结果

- JVM 单元测试：使用 JDK 17 强制重跑 `:app:testDebugUnitTest --rerun`，**121 项通过，0 失败、0 跳过**。
- `lintDebug` 与 `assembleDebug`：通过。Lint 为 **0 error、23 warning**，其中依赖版本提示 9、资源属性 6、启动图标 5、POI 内部 TrustManager 2、UseKtx 1。
- TrustManager 警告来自 POI 依赖；主 Manifest 没有 INTERNET 权限。本轮没有证据将它认定为应用存在可利用的联网证书绕过漏洞。
- API 34 模拟器：使用已有 `bp_api34_test` 的只读、不保存快照实例；实际检查浅色首页、录入页、深色录入与系统字号 2.0。截图均为清空 Debug 应用后的测试界面，无真实健康记录。
- `connectedDebugAndroidTest`：**编译失败，测试没有执行**，详见 R4。
- Release 构建：最终结果见本报告末尾“验证补记”。
- 未执行真机 ROM 提醒投递、真实日历提供方故障注入、TalkBack 手动朗读、API 26/36 设备矩阵、宏基准。截图检查不能代替这些测试。

## 2. 按优先级排序的问题

### R1 · P1 · “保存草稿”退出后丢失【实测】

**触发与影响：**新增测量中输入第一组高压 123，返回并选择“保存草稿”，再次进入新增测量，所有输入框为空。编辑页使用相同退出机制，也存在同类生命周期问题。

**原因：**草稿仅写入该页面 ViewModel 的 SavedStateHandle；“保存草稿”按钮直接调用 onBack，导航执行普通 popBackStack。页面对应的 ViewModel 与保存状态被清理，再次进入是新页面。

**位置：**[AddMeasurementScreen.kt:92](<E:/blood pressure record/app/src/main/java/com/example/bloodpressurerecord/ui/record/AddMeasurementScreen.kt:92>)、[SessionDraftStore.kt:16](<E:/blood pressure record/app/src/main/java/com/example/bloodpressurerecord/ui/common/SessionDraftStore.kt:16>)、[AppNavigation.kt:204](<E:/blood pressure record/app/src/main/java/com/example/bloodpressurerecord/navigation/AppNavigation.kt:204>)。

**修改方案：**引入独立的 `SessionDraftRepository`，使用应用私有目录保存版本化草稿文件，按“新增”或 sessionId 区分；保留 SavedStateHandle 用于当前页状态恢复。“保存草稿”必须等待持久化成功再退出，失败则留在表单并显示原因。成功保存正式记录和用户明确放弃时清除对应草稿；“清空全部数据”同时清除草稿。提供可见的“继续上次草稿”入口。

**验收：**退出再进入、Activity 重建、进程重建均恢复原读数/时间/标签/备注；保存或放弃后不会重新出现旧草稿。

### R2 · P1 · 旧版单次记录可导出，却会在导入时被跳过【静态确认】

**触发与影响：**设备仍有旧表 `bp_measurements` 或 `blood_pressure_records` 的记录时，导出服务将其写为只有一组读数的记录；导入校验要求至少两组，因此换机恢复会跳过这些记录。导出成功不等于这些历史数据可恢复。

**位置：**[BackupExportService.kt:138](<E:/blood pressure record/app/src/main/java/com/example/bloodpressurerecord/data/repository/backup/BackupExportService.kt:138>)、[BackupImportService.kt:258](<E:/blood pressure record/app/src/main/java/com/example/bloodpressurerecord/data/repository/backup/BackupImportService.kt:258>)。已有 `import_skipsRecordWithFewerThanTwoReadings` 测试正好锁定了拒绝行为，但没有覆盖旧表导出后的往返。

**修改方案：**区分新建表单约束与历史数据兼容约束。新建继续要求两组；历史导入、编辑和撤销允许保留单组真实读数，明确标注“单次记录”，平均策略使用 ALL。不要复制第一组来伪造第二次测量。恢复旧表来源信息，补充旧表 → 导出 → 新库的端到端往返测试。

**验收：**两种旧表的单组记录均可恢复，数量、数值、日期、备注不丢失，编辑仅改备注后仍是一组读数。

### R3 · P1 · 备份遗漏药品、服药时间与打卡历史【静态确认】

**触发与影响：**用户按当前“备份与换机迁移”流程导出并迁移后，血压与部分设置可恢复，但药品、每日服药时间和已服记录不会恢复。加密 .bpx 只是同一 xlsx 载荷的外层容器，也没有这些数据。

**位置：**[BackupModels.kt:61](<E:/blood pressure record/app/src/main/java/com/example/bloodpressurerecord/data/repository/backup/BackupModels.kt:61>)、[BackupExportService.kt:21](<E:/blood pressure record/app/src/main/java/com/example/bloodpressurerecord/data/repository/backup/BackupExportService.kt:21>)、[BackupExportService.kt:201](<E:/blood pressure record/app/src/main/java/com/example/bloodpressurerecord/data/repository/backup/BackupExportService.kt:201>)。

**修改方案：**新增 xlsx 载荷格式 v4，加入药品、时间点、打卡与相关偏好；继续读取 v2/v3，旧文件缺失用药数据应表示“不包含”，不能清空现有药品。药品/时间点引入稳定备份标识，导入时映射本地 ID，打卡引用随之映射，保证重复导入不会重复创建。日历事件 ID 与 PendingIntent ID 属于本机衍生状态，不直接恢复；完成数据提交后重新同步。预览列出各类记录数量和覆盖范围。

**验收：**新设备完整恢复，重复导入幂等，启停药品、多个时间点、打卡和无用药数据的旧备份均正确。

### R4 · P1 · 仪器测试无法编译，CI 没有覆盖这一入口【实测】

**现象：**`compileDebugAndroidTestKotlin` 在 CalendarAccessibilityTest 两处报 `No value passed for parameter 'onDoubleClick'`，使整套数据库迁移/Repository/Compose 仪器测试都无法运行。

**位置：**[CalendarAccessibilityTest.kt:35](<E:/blood pressure record/app/src/androidTest/java/com/example/bloodpressurerecord/ui/history/CalendarAccessibilityTest.kt:35>)、[CalendarAccessibilityTest.kt:57](<E:/blood pressure record/app/src/androidTest/java/com/example/bloodpressurerecord/ui/history/CalendarAccessibilityTest.kt:57>)、[build-release.yml:29](<E:/blood pressure record/.github/workflows/build-release.yml:29>)。

**修改方案：**为这两处调用补齐双击回调并增加双击行为断言。PR CI 至少增加 `:app:assembleDebugAndroidTest`，让接口变动引起的测试编译错误及时失败；再增加 API 34 模拟器运行任务，发布前执行 API 26/36 与真机关键流程。

**验收：**仪器测试 APK 可编译，现有测试在模拟器执行通过；后续组件接口变动不能绕过测试源码编译。

### R5 · P2 · 导出规模无边界，导入最多接受 5,000 条【静态确认】

**触发与影响：**本地累积 5,001 条记录后，仍可生成完整导出文件，导入端却拒绝整个文件；同样存在导出文件超过 10 MB 的不对称问题。当前性能往返测试只到 5,000 条，未覆盖越界第一条。

**位置：**[BackupExportService.kt:35](<E:/blood pressure record/app/src/main/java/com/example/bloodpressurerecord/data/repository/backup/BackupExportService.kt:35>)、[BackupImportService.kt:28](<E:/blood pressure record/app/src/main/java/com/example/bloodpressurerecord/data/repository/backup/BackupImportService.kt:28>)、[BackupFileReader.kt:135](<E:/blood pressure record/app/src/main/java/com/example/bloodpressurerecord/data/repository/backup/BackupFileReader.kt:135>)。

**修改方案：**统一导入导出容量契约。短期在导出前检查可恢复性，超限时提供按时间范围拆分导出，明确范围与条数，不产生被标记为“完整备份”的不可恢复文件。随后改进流式读写，在保留 ZIP 解压限制的前提下再评估提高上限；不能只删除安全限制。

**验收：**4,999 / 5,000 / 5,001 条、明文与加密格式、接近字节上限均有明确结果；每个成功导出的分片都能导回。

### R6 · P2 · 导入接受 11–20 组，但编辑保存最多 10 组【静态确认】

**触发与影响：**合法导入一条 11 组读数的记录，打开编辑，仅修改备注也无法保存，必须先删掉真实读数才能符合 UI 校验。

**位置：**[SessionFormLogic.kt:36](<E:/blood pressure record/app/src/main/java/com/example/bloodpressurerecord/ui/common/SessionFormLogic.kt:36>)、[SessionFormLogic.kt:86](<E:/blood pressure record/app/src/main/java/com/example/bloodpressurerecord/ui/common/SessionFormLogic.kt:86>)、[EditSessionViewModel.kt:250](<E:/blood pressure record/app/src/main/java/com/example/bloodpressurerecord/ui/history/EditSessionViewModel.kt:250>)。

**修改方案：**把“允许新增多少组”与“允许保存已有多少组”分开。新建/增加组数仍限 10；编辑允许完整保留已有记录，校验上限使用存储的 20 组。对 11–20 组记录禁止继续增加，但不要求删除后才能保存。

**验收：**10、11、20 组记录只改备注可保存；20 组完整往返；超出存储上限明确拒绝。

### R7 · P2 · 趋势“最高一次/最低一次”可能显示从未测得的组合【实测 SQL 演示】

**触发与影响：**两次记录为 160/80 和 130/100，SQL 分别取收缩压和舒张压极值，UI 显示“最高一次 160/100”“最低一次 130/80”；这两组实际都不存在。

**位置：**[MeasurementSessionDao.kt:156](<E:/blood pressure record/app/src/main/java/com/example/bloodpressurerecord/data/db/dao/MeasurementSessionDao.kt:156>)、[TrendScreen.kt:163](<E:/blood pressure record/app/src/main/java/com/example/bloodpressurerecord/ui/history/TrendScreen.kt:163>)。

**修改方案：**保留独立极值计算，将卡片改为“收缩压范围 130–160”“舒张压范围 80–100”；如后续确需“一次”，须选择真实 session 并展示该次完整数值和时间，明确排序指标。

**验收：**使用极值分别来自不同记录的数据，不再拼接为某次测量。

### R8 · P2 · 趋势平均值与变化摘要存在不一致【静态确认】

**触发与影响：**摘要把 Double 用 toInt 截断，图表平均值使用 roundToInt；例如平均 120.5，摘要是 120，图表是 121。变化文案又将负差值 clamp 为 0：收缩压下降 5、舒张压上升 2，会写成“高了 0/2”，丢失真实方向。

**位置：**[TrendViewModel.kt:210](<E:/blood pressure record/app/src/main/java/com/example/bloodpressurerecord/ui/history/TrendViewModel.kt:210>)、[TrendSeriesCalculator.kt:80](<E:/blood pressure record/app/src/main/java/com/example/bloodpressurerecord/domain/calculator/TrendSeriesCalculator.kt:80>)、[TrendScreen.kt:134](<E:/blood pressure record/app/src/main/java/com/example/bloodpressurerecord/ui/history/TrendScreen.kt:134>)。首页和历史的聚合展示也使用 toInt。

**修改方案：**在领域层统一统计精度与展示舍入，跨页面统一采用四舍五入；差值使用未舍入的均值相减后再格式化。文案分指标显示“收缩压下降…，舒张压上升…”，零差值显示持平。移除仅根据“下降”就自动生成“往好的方向走”的判断，先描述数值变化。

**验收：**.49/.50/.51 均值、双升、双降、一升一降、持平以及无上一周期数据。

### R9 · P2 · 深色主题核心录入数字几乎不可辨认【实测】

**现象：**输入框底色随主题变为 #474238，但数字仍固定使用 #643312，对比度约 **1.04:1**。首页血压、统计卡、分段选项等也有类似固定浅色主题色使用。浅色卡片上的次级文字 #82796A / #EBDDC5 约 **3.21:1**，普通小字亦需改善。

**位置：**[SessionFormComponents.kt:316](<E:/blood pressure record/app/src/main/java/com/example/bloodpressurerecord/ui/common/SessionFormComponents.kt:316>)、[Theme.kt:43](<E:/blood pressure record/app/src/main/java/com/example/bloodpressurerecord/ui/theme/Theme.kt:43>)、[DesignSystem.kt:71](<E:/blood pressure record/app/src/main/java/com/example/bloodpressurerecord/ui/theme/DesignSystem.kt:71>)。

**修改方案：**组件使用语义颜色，如 onSurface、onSurfaceVariant、primary、error；扩展 `AppSemanticColors` 提供血压状态、图表曲线、测量数字的亮/暗配色。所有状态的容器色与文字色成对定义，清除页面对 Terracotta700/800 等原始常量的直接依赖。

**验收：**浅/深色、正常/高值/高风险、禁用/错误/聚焦状态逐一检查。一般正文目标 ≥4.5:1；大字可按 ≥3:1，不能把大字例外用在 12–14sp 说明文字上。[Android 无障碍指南](https://developer.android.com/guide/topics/ui/accessibility/apps)

截图：[form-dark.png](<E:/blood pressure record/docs/review-2026-09-06/form-dark.png>)。

### R10 · P2 · 大字号导致日期裁切、三列标签重叠【实测】

**现象：**系统字号 2.0，加上应用默认的大字倍率 1.15 后，日期末尾被裁切，收缩压/舒张压/脉搏标签互相覆盖。固定高度输入框也不足以支撑更大的文字。

**位置：**[MainActivity.kt:34](<E:/blood pressure record/app/src/main/java/com/example/bloodpressurerecord/MainActivity.kt:34>)、[SessionFormComponents.kt:177](<E:/blood pressure record/app/src/main/java/com/example/bloodpressurerecord/ui/common/SessionFormComponents.kt:177>)、[SessionFormComponents.kt:291](<E:/blood pressure record/app/src/main/java/com/example/bloodpressurerecord/ui/common/SessionFormComponents.kt:291>)。

**修改方案：**保留系统字号支持，通过布局重排解决。可用宽度不足或字体倍率 ≥1.5 时，日期/时间改纵排，读数改带完整标签的纵向输入；常规模式保留三列。输入高度改为 minHeight + 内容自适应，移除标签的 `TextOverflow.Visible`，允许换行。大数字卡片给单位独立位置，并在窄屏自动改为上下布局。

**验收：**320/360/411dp，字号 1.0/1.3/1.5/2.0，应用大字开关两态；完整数字与日期可读，标签不重叠，键盘弹出后能滚动到当前字段。

截图：[form-large-text.png](<E:/blood pressure record/docs/review-2026-09-06/form-large-text.png>)。

### R11 · P2 · 输入字段缺少关联标签，图表依赖手势访问【语义树实测 + 静态确认】

**现象：**六个空的 EditText 都没有标签或 content-desc；视觉标签是独立 Text，无法可靠区分“第几组/哪个指标”。趋势 Canvas 只有 pointerInput，用户不能用可访问操作逐点浏览历史值或打开任意日详情。

**位置：**[SessionFormComponents.kt:287](<E:/blood pressure record/app/src/main/java/com/example/bloodpressurerecord/ui/common/SessionFormComponents.kt:287>)、[TrendChart.kt:214](<E:/blood pressure record/app/src/main/java/com/example/bloodpressurerecord/ui/history/TrendChart.kt:214>)。

**修改方案：**为 TextField 提供可访问的“第 N 组收缩压/舒张压/脉搏”标签；提供图表“前一点/后一点/复位/查看明细”按钮，以及同数据源的文字列表。服药整行使用带药名和时间的 toggleable 语义。自定义底部导航、分段和日历应明确保证不重叠的 48dp 触控布局；不能只根据可见 38/44dp 推断 Compose 实际点击区域，因为框架可能自动扩展。[Compose 无障碍默认行为](https://developer.android.com/develop/ui/compose/accessibility/api-defaults)

**验收：**TalkBack 顺序正确，能独立识别六个字段并完成保存；无需多指/长按就能访问图表数据和日详情。

### R12 · P2 · 日历同步失败与关闭状态没有完整处理【静态确认】

**场景一：**事件 insert 成功，但随后 Reminders insert **抛异常**，当前只处理返回 null 的情况；该 eventId 尚未返回到外层 createdEventIds，外层回滚列表漏掉它，留下无提醒的日程。

**场景二：**权限被撤销后关闭日历同步，rebuild 返回 null；Coordinator 只在“开关为开”时检查 null，UI 却显示“本应用创建的服药日程已清理”，而旧日历事件实际仍在。

**场景三：**全量重建先删旧事件，再找可写日历和创建新事件；后续失败会失去旧提醒。当前回滚只删除新建项，并不恢复旧集合。

**位置：**[MedicationCalendarSync.kt:47](<E:/blood pressure record/app/src/main/java/com/example/bloodpressurerecord/data/calendar/MedicationCalendarSync.kt:47>)、[MedicationCalendarSync.kt:152](<E:/blood pressure record/app/src/main/java/com/example/bloodpressurerecord/data/calendar/MedicationCalendarSync.kt:152>)、[MedicationReminders.kt:124](<E:/blood pressure record/app/src/main/java/com/example/bloodpressurerecord/reminder/MedicationReminders.kt:124>)、[SettingsViewModel.kt:167](<E:/blood pressure record/app/src/main/java/com/example/bloodpressurerecord/ui/settings/SettingsViewModel.kt:167>)。

**修改方案：**使用明确的 `CalendarSyncResult`（已同步/已清理/需授权/失败）；先检查目标日历可用性，事件和提醒成套提交，异常路径必须清理已创建 eventId。使用提供方支持的批处理，并验证实际回滚行为；优先差量更新以保留旧日程。无权限清理时保存 pendingCleanup，向用户如实显示待清理，授权后重试；不能宣称已删除。

**验收：**插入返回 null、抛异常、删除失败、权限撤销、无可写日历和重复同步均不产生孤儿/重复日程，状态与事实一致。

### R13 · P2 · 加密容器的 KDF 迭代次数缺少上界【静态确认】

**触发与影响：**导入头部的 iterations 只检查大于 0，随后直接进入 PBKDF2；用户选中的损坏/不可信小文件可声明极高迭代数，长期占用 CPU。GCM 对头部的认证发生在派生密钥之后，不能阻止此前的资源消耗。

**位置：**[BackupCrypto.kt:80](<E:/blood pressure record/app/src/main/java/com/example/bloodpressurerecord/data/repository/backup/BackupCrypto.kt:80>)。

**修改方案：**按容器版本限定支持的 KDF 参数。当前 v1 写入端固定 310,000 次，可只接受该值；需要调整参数时显式升级容器协议或增加经过性能验证的参数白名单。派生前验证最小密文/tag 长度，确保格式异常提前返回。不要对测试输入执行数十亿次 PBKDF2。

**验收：**v1 正常备份可恢复，0、负数、超上限和截断容器立即拒绝，错误口令与篡改仍正确报错。

### R14 · P2 · 旧趋势详情请求会清掉较新的详情【静态确认】

**触发与影响：**打开 A 日详情，关闭后立即打开 B 日；A 请求较晚完成时，当前 `takeIf { current.point.id == A.id }` 返回 null，update 把 B 的状态清掉，导致新面板意外消失。失败分支相同。

**位置：**[TrendViewModel.kt:157](<E:/blood pressure record/app/src/main/java/com/example/bloodpressurerecord/ui/history/TrendViewModel.kt:157>)。

**修改方案：**不匹配时返回 current；同时取消旧 Job 或给每次请求分配请求 ID，只接受仍有效请求的结果。关闭面板时取消对应加载。

**验收：**A 慢 B 快、A 快 B 慢、关闭后响应、旧请求失败，都不能覆盖或清空 B。

### R15 · P2 · 导入提交后的提醒失败会被包装成“导入失败”【静态确认】

**触发与影响：**Room 和设置恢复已经成功，随后 rescheduleReminders 抛异常，外层 runCatching 返回 failure，界面显示“导入失败”并保留预览，用户可能误以为没有写入而再次覆盖数据。

**位置：**[DefaultSettingsRepository.kt:288](<E:/blood pressure record/app/src/main/java/com/example/bloodpressurerecord/data/repository/DefaultSettingsRepository.kt:288>)、[SettingsViewModel.kt:578](<E:/blood pressure record/app/src/main/java/com/example/bloodpressurerecord/ui/settings/SettingsViewModel.kt:578>)。

**修改方案：**返回结构化 `BackupCommitResult`，分别记录数据库提交、设置恢复、提醒同步与小部件刷新；已提交时清理预览并报告“数据已恢复，提醒待重试”。复用 clearAllData 已采用的部分成功处理思路；重试只重做失败的后续步骤。

**验收：**数据提交后注入提醒失败，恢复结果仍准确，重试不重新导入数据。

## 3. UI 与架构改进方案

### 视觉方向：保留温暖风格，强化信息层级和可读性

现有奶油色、陶土橙和鼠尾草绿已有辨识度。主要问题不是缺少装饰，而是颜色直接写在页面、卡片内信息密度失衡、字号适配和操作语义不完整。

| 页面/组件 | 建议的调整 | 实现要点 |
|---|---|---|
| 首页 | 首屏依次为最近一次测量、记录按钮、今日概览；连续打卡下移；未配置药品时用一行入口 | 缩短问候与鼓励区域；保留数值的时间和明确状态；空状态只保留一个主要录入入口 |
| 录入 | 日期时间 → 读数 → 实时结果 → 可选场景/症状/备注；新增与编辑使用一致的保存位置 | 使用共享 SessionFormScreen，保存按钮放 Scaffold.bottomBar；处理键盘和底部 inset，显示具体不可保存原因 |
| 读数组 | 平常三列，大字号纵排；脉搏明确标注选填 | 标签、单位、组号完整；字段级校验；键盘“下一项”按组顺序导航 |
| 历史 | 日历与近期模式保留；点击日期后明确显示摘要、备注入口 | “含偏高读数”图例目前实际由 containsHighRisk 驱动，先改为“含高风险读数”；普通偏高如需标记则另加字段，不能混用 |
| 趋势 | 先给事实摘要，再给图表，范围统计放图表后；提供列表与显式控制按钮 | 极值改指标范围；修复舍入/双向变化；参考线与个人目标线分别命名；不要把“更低”自动解释成“更好” |
| 设置 | 用户资料、提醒、显示、数据管理维持分组 | 药品管理可独立子页；备份展示“包含哪些数据”；提醒展示通知、调度、日历三种状态 |
| 深色主题 | 所有语义色完整成对适配 | 数字、图标、状态卡、图表与分段项使用同一主题入口 |
| 大字/窄屏 | 重排而非强制缩小文字 | 最小高度、自适应行高、合理换行；重要数值不使用省略号 |

[浅色首页截图](<E:/blood pressure record/docs/review-2026-09-06/home-light.png>)、[深色首页截图](<E:/blood pressure record/docs/review-2026-09-06/home-dark.png>) 与 [正常字号表单截图](<E:/blood pressure record/docs/review-2026-09-06/form-light.png>) 可作为改版基线。另保留了[草稿退出重进后的字段语义树](<E:/blood pressure record/docs/review-2026-09-06/draft-reopened.xml>)。

### 状态管理与职责拆分

- **SettingsViewModel（665 行）**：拆成资料、提醒、显示、备份四个 ViewModel；根 Activity 仅订阅显示偏好，不再创建会同时订阅药品列表的完整设置 ViewModel。
- **新增/编辑表单**：共享 `SessionFormState`、字段事件和校验状态；创建模式与编辑模式只负责加载、允许的组数和保存目标，避免现在两套逻辑逐步偏离。编辑对高风险开关的行为也应与新增一致，当前编辑未接入该偏好。
- **提醒与日历**：由单一应用层协调服务负责数据变更后的重排与同步，不散落在 UI 保存成功回调；集中串行化同步，结果结构化，并允许只重试失败部分。
- **统计**：领域层返回统一的数值、范围和差值；UI 只负责格式化和展示，不自行截断/拼接极值。
- **TrendChart（902 行）与 HistoryScreen（849 行）**：分别拆成容器、计算/手势、绘制/展示组件。沿用已经存在的 TrendChartModels 与 CalendarModels，不急于引入新的模块/图表框架。
- **异常处理**：设置保存、首页打卡等 suspend 调用需要将存储失败映射为可恢复 UI 状态；捕获协程异常时重新抛出 CancellationException。首页/趋势的读取失败也应显示错误和重试，而非长期空态或未处理异常。
- **用户资料校验**：导入当前直接保存 age/target 数值，未复用资料页范围与大小关系校验；提取统一 UserProfileValidator，在预览阶段给出逐字段错误，不把无效目标静默保存。
- **日历数据去向**：当前自动选择可写主日历。应展示并允许选择目标日历及所属账号；说明日历应用可能按账号同步，修正“数据只在本机”的绝对表述。应用自身不联网与日历提供方是否同步是两个独立事实。[Android Calendar Provider](https://developer.android.com/identity/providers/calendar-provider)

### 工程与性能

- 修正宏基准 `runCoreJourneys` 使用的过期按钮文字“新增测量”；当前 UI 是“记一次血压”，找不到按钮时 helper 静默跳过。关键旅程改用稳定 testTag/resourceId，缺失就失败，避免产生没有执行目标路径的性能结果。
- PR CI 编译仪器测试；发布门禁执行数据库迁移与备份往返。补充 v5→v6 和最早支持版本→v7 的完整链路校验，目前现有测试不是完整版本矩阵。
- 分阶段升级依赖并验证兼容性，不把 Lint 的“有新版”直接当成漏洞。引入版本目录和依赖校验/锁定时单独提交。
- 大数据性能先测“全部趋势”和备份内存峰值。当前全部趋势仍将所有原始 session 载入再按日聚合，可在确有规模问题时改 DAO 日聚合与按需明细；备份同时持有 POI 对象、xlsx 字节与密文字节，应测低内存设备。
- MainActivity 主动请求最高刷新率，但本轮未测功耗或掉帧收益。先做真实基准，再决定是否保留，不把高刷新率偏好视为性能优化证据。
- 整理 README 重复的版本史/使用说明；统一 API 36、AGP 8.10.1、Gradle 8.11.1 和版本 1.8.2 信息。补正式截图，说明备份真实范围。

## 4. 推荐实施顺序与验收

### 第一阶段：数据可靠性与测试入口

1. 修复仪器测试编译，新增 CI 编译检查。
2. 修复草稿跨页面持久化。
3. 打通旧版单组记录往返及已有 11–20 组编辑。
4. 统一导出/导入容量约束；清楚显示当前备份范围。
5. 补齐用药完整备份 v4 与幂等导入。
6. 修复日历失败处理、KDF 参数边界和导入部分成功状态。

这些改动独立提交，避免备份格式演进、数据库迁移和 UI 改版混在一个大提交。增加字段/表时走显式迁移；保持既有记录、平均策略、应用包名和无网络上传原则。

### 第二阶段：核心 UI 修复与一致性

1. 修复统计极值、舍入和双向变化文案。
2. 统一语义颜色，首先修复深色录入。
3. 完成大字号/窄屏重排和输入标签。
4. 统一新增/编辑的保存区与草稿入口。
5. 修复趋势详情请求竞态，增加显式图表操作和数据列表。
6. 调整首页层级与设置数据管理反馈。

### 第三阶段：按功能拆分与发布验证

在前两阶段行为测试稳定后拆分 ViewModel/页面；补稳定的宏基准旅程，按实测结果决定大数据查询优化与依赖升级。保持一次只改变一类行为，使用旧版 APK 数据升级到候选版进行验收。

### 必须覆盖的场景

| 类别 | 用例 |
|---|---|
| 草稿 | 新增/编辑保存草稿退出重进；旋转；后台进程重建；成功保存/放弃/清空后的清理 |
| 数据兼容 | 旧表单组、2/10/11/20 组、ALL/DISCARD_FIRST、空脉搏、备注/症状保留 |
| 完整备份 | 无数据、血压与用药混合、重复导入、v2/v3/v4、明文/.bpx、错误口令/损坏文件 |
| 容量 | 4,999/5,000/5,001 条、超过字节上限、ZIP 条目/解压限制、低内存 |
| 同步 | 权限拒绝/撤销；事件/提醒插入异常；清理失败；数据成功但提醒失败 |
| 统计 | 均值舍入边界、极值分属不同 session、上一周期缺失、方向相反的指标变化 |
| UI | 320/360/411dp、字号 1.0–2.0、亮暗主题、键盘遮挡、TalkBack、三键与手势导航 |
| 发布 | unit、lint、Debug、AndroidTest 编译与执行、Release、签名验证、历史数据库升级 |

## 验证补记

联合运行仪器测试与 Release 构建时，因仪器测试源码编译错误中止。随后单独运行 `./gradlew.bat --offline :app:assembleRelease --console=plain`，**Release 构建成功**，输出 `app-release-unsigned.apk`；未做正式签名与发布。仪器测试仍因 R4 阻塞，未运行也未计为通过；本轮未修改测试源码来绕过错误。只读测试模拟器已关闭。
