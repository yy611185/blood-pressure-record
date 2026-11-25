# 家庭血压记录 App 全面 UI/UX 审计报告

- **审计对象**：家庭血压记录（`com.yang.bloodpressure`）Android 客户端
- **代码基线**：`versionName = 2.3.9`（`versionCode = 50`），`compileSdk / targetSdk = 36`，`minSdk = 26`，Jetpack Compose BOM `2024.10.00` + Material 3
- **产品与设计基准**：[PRODUCT.md](file:///e:/blood%20pressure%20record/PRODUCT.md)、[DESIGN.md](file:///e:/blood%20pressure%20record/DESIGN.md)、[IMPLEMENTATION.md](file:///e:/blood%20pressure%20record/docs/ui-redesign-2026-09-25/IMPLEMENTATION.md)
- **审计范围**：全量 14 个导航路由页面、2 个页内全屏/预览子视图、全部公共组件与弹窗/BottomSheet、6 条核心用户操作链路，以及桌面小部件（Glance）、系统提醒通知、医生报告 PDF 三个延伸触点
- **验证边界声明**：本次审计严格基于**源码静态走查、WCAG 2.1 相对亮度公式精确计算、Compose 布局几何推演与状态流分析**完成；未修改业务代码、未启动模拟器或真机、未虚构任何实机运行结果。需真机验证的事项均已单独标注并汇总于第五章。

---

## 一、总体评价与核心结论

### 1.1 综合评分：**7.8 / 10**（定位清晰、工程底子扎实，但在健康语义自洽性、跨页交互一致性与中老年防呆容错上存在高优短板）

本项目是一款难得的**克制、真诚且有温度**的本地优先健康工具：
- **产品定位极度聚焦**：免注册、无广告、健康数据纯本地存储（仅更新检查联网且安装包交外部浏览器处理），精准契合中老年用户及家属对“简单记血压、换机可备份、看病能打单”的核心诉求。
- **视觉辨识度高**：“温暖的血压记录本”设计语言（暖纸底 `#FAF6EF`、珊瑚橙主操作 `#B94B39`、鼠尾草绿健康色、`Bricolage Grotesque` 等宽大数字与“小压”陪伴形象）摆脱了传统医疗软件冰冷生硬的焦虑感。
- **关键技术细节成熟**：如 `MainActivity` 主动关闭 Android 10+ 导航栏强制黑白对比遮罩、申请高刷新率、定制 `AppInsets` 防状态栏/挖孔重叠、趋势图按原始连续段判定缺测断线、更新弹窗在录入与备份期间自动静默避让（`UpdatePromptGate`）等，均体现了较高的工程水准。

### 1.2 核心问题症结（五大核心痛点）

1. **健康数据表达存在“同卡自相矛盾”与“关键分级缺失”**：首页最核心的最近一次读数大卡（`ReadingHero`）未直接展示《中国高血压防治指南》六级分级芯片；更严重的是，下方四段区间条算法（`LocateBpZoneUseCase`）直接比较枚举 `ordinal`（`LOW=0 < NORMAL=1`），导致单轴偏低、另一轴正常（如 `85/70` 或老年常见的低舒张压 `110/55`）时，**小压提示“偏低”，但区间条却高亮“正常”并向 TalkBack 播报“属于正常区间”**（`UX-01`、`UX-03`）。
2. **中老年高频操作存在“输入丢失”与“撤销被挡”的防呆缺口**：添加药品弹窗（`MedicationEditorDialog`）点击保存时先销毁弹窗再做校验，漏添服药时间会**瞬间清空用户刚手打的药名与剂量**（`UX-02`）；详情页删除记录后的「撤销」Snackbar 未避让全面屏底部系统导航栏，在三键导航机型上与系统键重叠（`UX-04`）。
3. **新增、编辑与返回路径的心智模型割裂**：「记一次血压」纵向平铺所有读数组，而「修改读数」却变成横向单组 Chip 切换且无法查看/编辑历史 `note` 字段（`UX-08`、`UX-09`）；从历史日历或趋势明细进入编辑并保存后，页面不会回到刚看的详情/日历，而是**强制清栈跳回首页**（`UX-06`）；医生报告预览页按返回键直接退出整个报告模块而非回到周期选择（`UX-07`）。
4. **“半成品占位文案”外露与双重“目标”概念混淆**：自定义目标范围面板直接向用户展示内部研发占位语“*默认数字只是占位示例……待产品确认*”且无恢复默认按钮（`UX-05`），同时与「我的资料 → 目标收缩压/舒张压」形成两套互不相通的“目标”设置（`UX-12`）。
5. **色彩 Token 碎片化与局部适老化对比度失守**：全应用散落 **5 套独立硬编码的血压分级色盘**，端内趋势图（高压橙/低压蓝）与导出 PDF 报告（高压蓝/低压橙）颜色完全反转（`UX-13`、`UX-14`）；数字输入框未聚焦边框对比度仅 `1.12:1–1.13:1`，深色模式趋势图高风险圆环对比度仅 `2.46:1`，默认 `1.15x` 大字叠加系统 `2.0x` 大字（达 `2.30x`）时部分固定高度按钮存在文字纵向挤占风险（`UX-10`、`UX-11`、`UX-15`）。

### 1.3 值得保留的优秀设计（切勿“为了优化而优化”而破坏）

| 模块 | 现有优秀实践 | 审计建议 |
|---|---|---|
| **主操作色与正文对比度** | 浅色 `primary (#B94B39)` 对白字 `5.09:1`、对纸色底 `4.72:1`；深色 `primary (#FFA997)` 对深棕字 `7.90:1`；正文与次级文字对比度达 `6.08:1–7.48:1`。 | **完整保留**，无需调整主色板。 |
| **紧凑三栏纵向录入** | `MeasurementReadingCard` 在常规宽度一行排布高压/低压/脉搏，窄屏或大字自动退化为“上二下一”，且按 `fontScale` 反向折算输入框字号避免三位数横向裁切。 | **完整保留**，并将编辑页对齐到此模式。 |
| **底部悬浮胶囊 Dock** | 独立图层动画收起、不触发 `NavHost` 与图表重测重绘；收起时用 `clearAndSetSemantics` 屏蔽隐藏 Tab 防止无障碍幽灵焦点。 | **保留架构**，仅微调极端字号弹性高度与停靠细节。 |
| **更新弹窗免打扰门控** | `AppNavigation` + `UpdatePromptGate` 在录入、编辑、趋势全屏及备份操作期间禁止自动弹出升级框，必要更新也不阻断离线记血压。 | **完整保留**，是医疗健康工具的标杆做法。 |
| **医生报告隐私默认保护** | 每次生成报告默认不勾选姓名与年龄且不记忆上次勾选，预览页切换开关后强制重算完成才允许导出。 | **完整保留**隐私安全机制，仅优化返回与按钮布局。 |

---

## 二、分维度量化评分与诊断

| 审计维度 | 评分（10分制） | 核心亮点 | 主要失分点 |
|---|:---:|---|---|
| **1. 视觉设计** | **8.0** | 暖纸色调温润统一；等宽大数字辨识度强；主色对比度达标；圆角与留白节奏舒适。 | 5 套分级色盘各自为政；冷启动 XML 背景色与 Compose 主题底色色差；趋势图/小部件/PDF 指标色不一致；部分三级页按钮退回原生默认样式。 |
| **2. 交互体验** | **7.2** | 两组读数直录直存；日历备注双击定位；趋势图缩放/平移/长按十字线手势状态机成熟。 | 药品弹窗校验失败即销毁输入；编辑保存强跳首页丢失上下文；报告预览返回直接退出；补充情况子视图缺少显式返回读数入口；完成弹窗 5 秒静默自关。 |
| **3. 信息架构** | **7.6** | 底部“四入口 + 居中记血压”主次分明；长页面采用 `ExpandableSection` 折叠次级统计并保留摘要。 | 「目标血压」与「血压目标范围」双轨割裂；「大字模式」在设置主页与显示设置二级页重复且命名不一；首页早晚槽与趋势/报告早晚统计时段定义冲突。 |
| **4. 健康数据体验** | **7.4** | 代表值与平均策略兼容严谨；缺测断线与缺失脉搏不伪造数据；高风险与分级双轨判断清晰。 | **单轴偏低读数在首页区间条被误标为“正常”**；首页主卡缺失六级指南状态芯片；趋势选中读数栏不显示血压分级；参考线与目标线线型完全相同无法区分。 |
| **5. 无障碍与适老化** | **7.5** | 绝大多数触控区 ≥48dp；日历格、区间条、趋势读数栏与折叠头均编写了详尽的合并语义描述。 | 数字输入框未聚焦描边对比度仅 `1.13:1`；深色高风险圆环 `2.46:1`；首页 `mmHg` 仅 `2.67:1`；默认 `1.15x` 乘系统 `2.0x` 无上限兜底导致固定高按钮裁切风险；自定义周期需手打日期格式。 |
| **6. 工程实现（UI层）** | **8.3** | Edge-to-Edge 与状态栏安全区封装清晰；`SavedStateHandle` 恢复趋势视窗与草稿；动画遵循系统时长缩放。 | `AddMeasurement` 与 `EditSession` 重复建设两套表单结构；存在未接线回调（`onViewTodayRecords`）与死代码/死资源；部分子页根容器漏设背景色致滑动转场透底。 |
| **7. 边界场景** | **8.1** | 备注搜索防抖与代理对（Emoji）安全截断；超 5000 条自动分卷引导；超宽血压大数字自动缩字。 | 趋势页无数据时同时渲染两张空状态卡与一堆 `—` 统计卡；详情页初态加载闪现“未找到记录”；编辑页丢失旧版 `note` 字段的可视与编辑出口。 |

---

## 三、完整问题清单

### 3.1 全局问题速览表（共 28 项：P0 × 2，P1 × 9，P2 × 12，P3 × 5）

> **属性说明**：
> - **问题性质**：`客观缺陷`（明确的功能错误、WCAG/规范违背或逻辑矛盾） / `潜在风险`（特定设备、字号或操作组合下触发的体验退化） / `主观设计建议`（提升易用性或一致性的体验优化）
> - **验证方式**：`源码静态可验证`（由代码分支、数学公式或色值直接证实） / `需实机运行验证`（静态发现风险，需真机/模拟器确认实际渲染或读屏表现）

| 编号 | 优先级 | 问题性质 | 验证方式 | 所属页面 / 模块 | 问题摘要 |
|---|:---:|---|---|---|---|
| **UX-01** | **P0** | 客观缺陷 | 源码静态可验证 | 首页 · `BpRangeBar` / `LocateBpZoneUseCase` | 单轴偏低读数（如 `85/70`、`110/55`）被区间条判定为「正常」并错误播报，与同卡偏低提示直接矛盾 |
| **UX-02** | **P0** | 客观缺陷 | 源码静态可验证 | 提醒中心 · `MedicationEditorDialog` | 添加/编辑药品点保存先关闭弹窗再校验，漏填时间会导致用户输入全部丢失且错误提示隐蔽在页底 |
| **UX-03** | **P1** | 客观缺陷 | 源码静态可验证 | 首页 · `ReadingHero` | 首页主读数卡缺失《中国高血压防治指南》六级状态芯片，关闭“小压”后仅剩自定义四段条，违背 `DESIGN.md` 状态文字规则 |
| **UX-04** | **P1** | 客观缺陷 | 源码静态可验证 | 记录详情 · `HistoryDetailScreen` | 删除记录后的「撤销」Snackbar 未叠加底部系统导航栏安全区，三键导航下被系统底栏遮挡；初态加载闪现“未找到记录” |
| **UX-05** | **P1** | 客观缺陷 | 源码静态可验证 | 我的/首页 · `BpZoneTargetSheet` | 面向最终用户直接展示“待产品确认”内部研发占位文案，且 10 个数字框无「恢复默认范围」按钮 |
| **UX-06** | **P1** | 客观缺陷 | 源码静态可验证 | 导航栈 · `EditSessionScreen` 保存回退 | 从历史日历或趋势明细进入编辑并保存后，强制清栈跳回首页，丢失用户原本查阅的历史月份或趋势上下文 |
| **UX-07** | **P1** | 客观缺陷 | 源码静态可验证 | 医生报告 · `ReportScreen` / `ReportPreviewScreen` | 报告预览页未拦截系统返回与顶栏返回键，点击返回直接退出整个报告模块而非回到周期设置页 |
| **UX-08** | **P1** | 客观缺陷 | 源码静态可验证 | 编辑记录 · `EditSessionScreen` | 历史遗留记录的通用备注 `note` 在详情页与搜索页可见，但在编辑页完全无展示与修改入口 |
| **UX-09** | **P1** | 客观缺陷 | 源码静态可验证 | 新增 vs 编辑 · `EditSessionScreen` | 编辑页采用横向单组 Chip 切换而非新增页的纵向多组平铺，缺失高风险页内横幅，且忽略用户关闭的高风险提醒开关 |
| **UX-10** | **P1** | 客观缺陷 | 源码静态可验证 | 录入/趋势 · 色彩对比度（WCAG 2.1） | 数字输入框未聚焦描边对比度仅 `1.12:1–1.13:1`；深色趋势图高风险圆环硬编码浅色红仅 `2.46:1`；首页 `mmHg` 仅 `2.67:1` |
| **UX-11** | **P1** | 潜在风险 | 源码可算 + 需实机验证 | 全局字体缩放 · `MainActivity` + 固定高度控件 | 默认开启 `1.15x` 大字并与系统字号相乘无上限（可达 `2.30x`），保存按钮 `height(58.dp)` 与 Dock `height(64.dp)` 存在大字纵向裁切风险 |
| **UX-12** | **P2** | 客观缺陷 | 源码静态可验证 | 信息架构 · 首页 / 我的 / 历史 / 趋势 | 全应用存在两套互不连通的“血压目标”设置，以及首页与趋势/报告不一致的“早/晚”和“周起始日”口径 |
| **UX-13** | **P2** | 客观缺陷 | 源码静态可验证 | 跨模块视觉 · 趋势图 / 小部件 / PDF 报告 | 收缩压与舒张压代表色在应用内趋势图（橙/蓝）、桌面小部件（橙/绿）、医生报告 PDF（蓝/橙）三处互不一致甚至完全反转 |
| **UX-14** | **P2** | 客观缺陷 | 源码静态可验证 | 主题系统 · 分级色彩与暗色模式判定 | 散落 5 套硬编码六级血压色盘；多处用 `background.red < 0.5f` 猜测深色模式；冷启动 XML 背景色与 Compose 背景色脱节 |
| **UX-15** | **P2** | 客观缺陷 | 源码静态可验证 | 趋势图 · `TrendChartDrawing` / `TrendScreen` | 双指标折线图缺少显式图例（未选点时不知哪条是高压/低压）；140/90 参考虚线与自定义目标虚线线型透明度完全相同；选中读数栏缺分级 |
| **UX-16** | **P2** | 客观缺陷 | 源码静态可验证 | 趋势页 · `TrendScreen` 空状态与深色圆环 | 当前周期无记录时，页面连续渲染两张“暂无记录/暂无数据”卡片并展开显示全 `—` 统计卡；达标率圆环底色硬编码浅绿 `#DDE8DF` |
| **UX-17** | **P2** | 客观缺陷 | 源码静态可验证 | 医生报告 · `CustomPeriodDialog` & 预览操作区 | 自定义统计周期要求老年用户用键盘手打 `yyyy-MM-dd` 格式字符串；预览页 5 个导出操作按钮置于全部 PDF 页面最底部 |
| **UX-18** | **P2** | 客观缺陷 | 源码静态可验证 | 首页 · `DashboardScreen` 交互死胡同 | `onViewTodayRecords` 导航回调已传入但全页未绑定；主读数卡与已测量的早/晚卡片均不可点击查看详情 |
| **UX-19** | **P2** | 主观设计建议 | 源码静态可验证 | 新增/编辑 · 补充情况子视图 & 完成弹窗 | 切换到「补充情况」后页面内无“返回读数”按钮（仅靠左上角返回易被误认退出）；「记录完成」弹窗 5 秒无提示倒计时自动关闭且不显示分级 |
| **UX-20** | **P2** | 客观缺陷 | 源码静态可验证 | 新增/编辑/设置 · 页面底部反馈信息可见性 | 草稿恢复提示（且草稿保留旧测量时间）、设置保存/校验提示均渲染在可滚动长页面最底部，极易滚出视野且成功/错误同用绿色小字 |
| **UX-21** | **P2** | 主观设计建议 | 源码静态可验证 | 用户资料 & 显示设置 · `SettingsSubpages` | 「性别」使用自由文本输入框而非单选胶囊；「大字模式」在我的主页与显示设置二级页重复出现且命名不同 |
| **UX-22** | **P2** | 客观缺陷 | 源码静态可验证 | 桌面小部件 · `BloodPressureWidget` | 未适配深色模式；含高风险读数但平均值偏低级别时仅改文字颜色未写明“含高风险”；次级文字对比度 `3.84:1` 偏低 |
| **UX-23** | **P2** | 潜在风险 | 源码静态可验证 | 页面转场 · `TrendScreen` / `ReportScreen` | 趋势页、横屏全屏页与医生报告页根容器未声明 `background` 色，在父子路由 350ms 视差滑动转场时存在透底风险 |
| **UX-24** | **P3** | 主观设计建议 | 源码静态可验证 | 系统提醒通知 · `ReminderReceiver` | 测量提醒点击仅进首页而非直达「记一次血压」；服药提醒通知缺少“已服用”快捷打卡 Action |
| **UX-25** | **P3** | 客观缺陷 | 源码静态可验证 | 公共组件 · 返回按钮与原生按钮混用 | 存在 `AppBackButton`（44dp 圆形箭头，未实际生效）与 `AppTopBar`（48dp 圆角矩形左尖括号）两套返回组件；报告页/提醒页混用 40dp 原生 `Button` |
| **UX-26** | **P3** | 潜在风险 | 需实机运行验证 | 底部悬浮 Dock · `AppBottomDock` 滚动收起 | 向下滚动收起时新增按钮平移至**左下角**（右手单手拇指热区最远端），且收起期间切换 Tab 必须先反向回滚 |
| **UX-27** | **P3** | 主观设计建议 | 源码静态可验证 | 数据管理 · `SettingsDataManagementScreen` | 加密口令输入框无“显示/隐藏明文（眼睛图标）”切换，老年用户盲打 8 位以上口令两次易输错或遗忘 |
| **UX-28** | **P3** | 客观缺陷 | 源码静态可验证 | 资源与文案同步 · 冗余资源与版本说明 | 打包了未使用的字体 `caprasimo_regular.ttf` 与死代码/死文案；构建版本为 `2.3.9` 但更新说明把 `2.3.8` 镜像优化标成 `2.3.9` |

---

### 3.2 逐项问题深度取证与优化方案

#### UX-01 · [P0] 单轴偏低读数在首页血压区间条中被误判并播报为「正常」

- **所属页面 / 组件**：首页 `DashboardScreen` → `BpRangeBar` / 领域用例 `LocateBpZoneUseCase`
- **源码位置**：[LocateBpZoneUseCase.kt](file:///e:/blood%20pressure%20record/app/src/main/java/com/example/bloodpressurerecord/domain/calculator/LocateBpZoneUseCase.kt#L16-L28)、[BloodPressureRules.kt](file:///e:/blood%20pressure%20record/app/src/main/java/com/example/bloodpressurerecord/domain/calculator/BloodPressureRules.kt#L26-L35)、[BpRangeBar.kt](file:///e:/blood%20pressure%20record/app/src/main/java/com/example/bloodpressurerecord/ui/home/BpRangeBar.kt#L65-L66)
- **属性标签**：`P0` · `客观缺陷` · `源码静态可验证` · `健康数据体验 / 无障碍`
- **问题依据**：
  `BpZone` 枚举顺序为 `LOW(0), NORMAL(1), ELEVATED(2), HIGH(3)`。`LocateBpZoneUseCase` 在合并收缩压与舒张压区间时直接比较 `ordinal` 大小：
  ```kotlin
  val selected = when {
      sysPosition.zone.ordinal > diaPosition.zone.ordinal -> sysPosition
      sysPosition.zone.ordinal < diaPosition.zone.ordinal -> diaPosition
      ...
  }
  ```
  当录入 **`85 / 70 mmHg`**（高压偏低、低压正常）或中老年动脉硬化常见的 **`110 / 55 mmHg`**（高压正常、低压偏低）时：
  1. `BloodPressureRules.category()` 判定为 `LOW`（血压偏低），首页大卡背景变为浅紫色，小压气泡提示：“*有点偏低哦，起身慢一点，多喝点水～*”。
  2. 同卡正下方的 `LocateBpZoneUseCase` 因 `NORMAL.ordinal (1) > LOW.ordinal (0)`，直接丢弃 `LOW` 侧而选中 `NORMAL`！导致区间条三角形指在绿色「正常」段、「正常」二字加粗，且 TalkBack 无障碍描述朗读为：**“收缩压 85，舒张压 70，属于正常区间”**。
- **用户影响**：同一张核心健康卡片上下给出完全相反的健康定性（上说“偏低”、下标“正常”），对依赖视觉色条的中老年人及依赖 TalkBack 读屏的视障用户造成严重的健康信息误导。
- **高收益低改动优化建议**：
  调整 `LocateBpZoneUseCase` 的双轴合并优先级，与 `BloodPressureRules.category` 严格一致：**先按升高严重度判 `HIGH > ELEVATED`（就高不就低），若两轴均未升高，只要任一轴为 `LOW` 即返回 `LOW` 侧位置，仅两轴均为 `NORMAL` 时才返回 `NORMAL`**（改动不超过 8 行代码，同步更新 `LocateBpZoneUseCaseTest`）。

---

#### UX-02 · [P0] 添加/编辑药品弹窗先销毁状态再校验，漏添时间导致已填药名剂量瞬间丢失

- **所属页面 / 组件**：我的 → 提醒中心 `SettingsReminderScreen` → `MedicationEditorDialog`
- **源码位置**：[SettingsSubpages.kt](file:///e:/blood%20pressure%20record/app/src/main/java/com/example/bloodpressurerecord/ui/settings/SettingsSubpages.kt#L325-L351)、[SettingsSubpages.kt](file:///e:/blood%20pressure%20record/app/src/main/java/com/example/bloodpressurerecord/ui/settings/SettingsSubpages.kt#L475-L547)、[SettingsViewModel.kt](file:///e:/blood%20pressure%20record/app/src/main/java/com/example/bloodpressurerecord/ui/settings/SettingsViewModel.kt#L330-L339)
- **属性标签**：`P0` · `客观缺陷` · `源码静态可验证` · `交互体验 / 适老化容错`
- **问题依据**：
  1. 点击「添加药品」时，`initial.times` 默认是 `emptyList()`（没有默认时间）。
  2. 老年用户费力用中文输入法打完复杂药名（如“苯磺酸氨氯地平片”）和每次数量（“1片”），直接点击弹窗右下角始终可点的「保存」按钮：
     ```kotlin
     onConfirm = { name, dosage, times ->
         if (editor.id == null) viewModel.addMedication(name, dosage, times) ...
         editorState = null // 无条件立即关闭弹窗并清空草稿！
     }
     ```
  3. `viewModel.addMedication` 检查到 `times.isEmpty()`，仅更新 `_uiState.message = "请至少添加一个服药时间。"` 后直接 `return` 不落库。
  4. 页面最底部的 `SettingsMessage` 用 `bodySmall (12.sp)` + `colorScheme.secondary`（鼠尾草绿色，与保存成功同色！）渲染该错误文案。
- **用户影响**：用户辛苦输入的药名与剂量瞬间灰飞烟灭，列表里也没有新药，若页面较长连底部的绿色 12sp 小字都看不到，极易让老年人以为“App 坏了/没反应”而反复重输挫败。
- **高收益低改动优化建议**：
  1. 在 `MedicationEditorDialog` 内部前置校验：`name.isBlank()` 或 `times.isEmpty()` 时直接在弹窗内用红色 `error` 文字提示（或禁用「保存」并附上置灰原因“请填写药名并至少添加 1 个服药时间”），校验通过前**绝不置空 `editorState`**。
  2. 新建药品时可默认预填一个常用晨间时间（如 `"08:00"`，可修改/删除），一步减少老年用户操作层级。

---

#### UX-03 · [P1] 首页主读数卡缺失六级指南状态芯片，违背 `DESIGN.md` 状态文字强约束

- **所属页面 / 组件**：首页 `DashboardScreen` → `ReadingHero`
- **源码位置**：[DashboardScreen.kt](file:///e:/blood%20pressure%20record/app/src/main/java/com/example/bloodpressurerecord/ui/home/DashboardScreen.kt#L239-L313)、对照 [DESIGN.md](file:///e:/blood%20pressure%20record/DESIGN.md#L114-L120)
- **属性标签**：`P1` · `客观缺陷` · `源码静态可验证` · `健康数据体验 / 设计一致性`
- **问题依据**：
  [DESIGN.md](file:///e:/blood%20pressure%20record/DESIGN.md#L114-L120) 明确规定了两条铁律：
  - **The Status Text Rule**：*所有血压等级和高风险提示同时写明状态，不能只给色块。*
  - **The Number First Rule**：*数值、mmHg、日期和状态要能一眼对应。*
  然而检查 `ReadingHero` 实现：卡片底色按六级 `gradeLook(latest.category)` 变色，但卡片内仅在 `risk == true`（≥180/120）时渲染 `RiskChip("高风险读数")`，**完全没有渲染 `GradeChip` / `StatusChip` 来标明「正常 / 正常高值 / 1级高血压 / 2级高血压 / 3级高血压 / 血压偏低」**！反观下方次级的早/晚小卡片（`MeasurementSlot:451`）却有 `GradeChip`。更糟糕的是：
  - 当用户点击小压切换气泡到“戳我干嘛～”或“已连续记录 X 天”，或者在「我的」中关闭「显示小压」（`showBuddy = false`）时，主卡上除了底色和用户可自定义的 4 段条（`偏低/正常/偏高/过高`，不含指南 1/2/3 级区分）外，**完全没有任何医学指南分级文字**。
- **用户影响**：用户或家属打开首页第一眼看到 `156/96`，只看到暖黄底色却看不到明确的「1级高血压」文字结论，与录入页、历史页、小部件的分级认知脱节。
- **高收益低改动优化建议**：
  在 `ReadingHero` 的“最近一次 · 时间”行右侧或“X 组平均”同行复用现成的 `GradeChip(CategoryPresentation.label(latest.category), look)`（与 `RiskChip` 并排放入 `FlowRow`），仅需约 6 行代码即可补齐核心分级状态。

---

#### UX-04 · [P1] 记录详情页删除撤销提示条未避让系统导航栏，且打开瞬间闪现“未找到记录”

- **所属页面 / 组件**：记录详情页 `HistoryDetailScreen` / `HistoryDetailViewModel`
- **源码位置**：[HistoryDetailScreen.kt](file:///e:/blood%20pressure%20record/app/src/main/java/com/example/bloodpressurerecord/ui/history/HistoryDetailScreen.kt#L50-L60)、[HistoryDetailScreen.kt](file:///e:/blood%20pressure%20record/app/src/main/java/com/example/bloodpressurerecord/ui/history/HistoryDetailScreen.kt#L200-L203)、[HistoryDetailViewModel.kt](file:///e:/blood%20pressure%20record/app/src/main/java/com/example/bloodpressurerecord/ui/history/HistoryDetailViewModel.kt#L19-L54)
- **属性标签**：`P1` · `客观缺陷` · `源码静态可验证` · `交互体验 / 边界场景`
- **问题依据**：
  1. **系统底栏遮挡撤销按钮**：`MainActivity` 开启了全屏 Edge-to-Edge，根 `Scaffold` 将 `contentWindowInsets` 归零（要求各页自行处理底部安全区）。但 `HistoryDetailScreen` 在第 58 行和第 202 行放置私有 `SnackbarHost` 时仅写了 `Modifier.align(Alignment.BottomCenter)`，**未加 `navigationBarsPadding()` 或 `pageContentBottomPadding()`**。在老年人最常用的 Android 经典三键导航栏机型上，「记录已删除 [撤销]」提示条直接贴在屏幕物理最底边，与系统返回/主页/多任务三键完全重叠。
  2. **加载态缺失导致错误闪屏**：`HistoryDetailUiState` 没有 `loading: Boolean = true` 字段，初始值 `session = null, deleted = false`。进入详情页首帧 Room 查询尚未返回时，第 50–55 行立即命中 `if (session == null)` 分支并渲染**“未找到记录”**，几十毫秒后再跳变为正常详情卡片。
- **用户影响**：老年人误删记录后无法顺利点击「撤销」（极易误触系统 Home/返回键直接退出页面导致永久删除）；每次点开详情时的“未找到记录”闪屏造成不必要的惊吓。
- **高收益低改动优化建议**：
  1. 为 `HistoryDetailScreen` 的两处 `SnackbarHost` 补上 `.padding(bottom = pageContentBottomPadding())`。
  2. 在 `HistoryDetailUiState` 增加 `val loading: Boolean = true`，数据库首次发射设为 `false`，`loading` 期间留白或显示柔和加载提示，不渲染“未找到记录”。

---

#### UX-05 · [P1] 自定义目标范围面板外露“待产品确认”研发占位文案，且缺少「恢复默认」能力

- **所属页面 / 组件**：我的 → 血压目标范围 `BpZoneTargetSheet` + `strings.xml`
- **源码位置**：[BpZoneTargetSheet.kt](file:///e:/blood%20pressure%20record/app/src/main/java/com/example/bloodpressurerecord/ui/home/BpZoneTargetSheet.kt#L55-L99)、[strings.xml](file:///e:/blood%20pressure%20record/app/src/main/res/values/strings.xml#L46-L47)
- **属性标签**：`P1` · `客观缺陷` · `源码静态可验证` · `视觉文案 / 交互容错`
- **问题依据**：
  1. `BpZoneTargetSheet.kt:56` 直接向正式版用户渲染 `R.string.bp_zone_config_placeholder_note`：
     > *“默认数字只是占位示例，不构成医学标准，实际分级与默认目标范围待产品确认。”*
     “待产品确认”属于典型的研发期内部备注泄露到生产 UI，严重损害健康工具的专业可信度。
  2. 该面板要求用户一次性维护 **10 个严格递增的正整数输入框**（收缩压与舒张压各自的显示下限、正常起点、偏高起点、过高起点、显示上限），一旦老年用户改乱触发校验报错，面板内**没有任何「恢复默认范围」按钮**帮助用户一键还原 `70 / 90 / 120 / 140 / 180` 与 `40 / 60 / 80 / 90 / 110`。
- **用户影响**：看到“待产品确认”会让用户怀疑首页区间条数据不可信；10 个分界值改乱后难以凭记忆改回合法递增序列。
- **高收益低改动优化建议**：
  1. 将文案替换为面向用户的清晰说明：“*默认范围参考常用成人血压区间（90/60、120/80、140/90 mmHg），仅供首页色条展示，不影响医学分级记录；您可按医嘱调整。*”
  2. 在面板标题右侧或取消按钮旁增加 `TextButton("恢复默认")`，点击将 `draft` 重置为 `BpZoneTargetDraft.from(BpZoneConfig.Default)`。

---

#### UX-06 · [P1] 编辑记录保存后强制清栈跳回首页，打断历史日历与趋势回溯上下文

- **所属页面 / 组件**：全局导航 `AppNavigation` → `HistoryEdit` 路由回调
- **源码位置**：[AppNavigation.kt](file:///e:/blood%20pressure%20record/app/src/main/java/com/example/bloodpressurerecord/navigation/AppNavigation.kt#L373-L389)
- **属性标签**：`P1` · `客观缺陷` · `源码静态可验证` · `交互体验 / 导航逻辑`
- **问题依据**：
  `EditSessionScreen` 的 `onSaved` 逻辑如下：
  ```kotlin
  val openedFromSearch = runCatching {
      navController.getBackStackEntry(AppDestination.HistoryLocation.route)
  }.isSuccess
  if (openedFromSearch) {
      navController.popBackStack()
  } else {
      navController.navigate(AppDestination.Measure.route) {
          popUpTo(navController.graph.findStartDestination().id)
          launchSingleTop = true
      }
  }
  ```
  当用户从 **历史页（翻到 3 个月前某一天）→ 记录详情 → 编辑**，或从 **趋势页（放大定位某个异常节点 → 明细 → 记录详情 → 编辑）** 修改完错别字保存后，因为回退栈里没有 `HistoryLocation`，应用直接执行 `popUpTo(Measure)`（且未 `saveState`），**把用户强行传送回「首页」并清空此前的详情页与浏览路径**！反而是从备注搜索进入时能正常 `popBackStack()` 回到详情页核对修改结果。
- **用户影响**：用户在核对、整理历史多条旧记录时，每改一条就被踢回首页，不得不重新切回历史/趋势、重新翻找月份与日期，操作路径极其挫败。
- **高收益低改动优化建议**：
  统一 `HistoryEdit` 的 `onSaved` 为 `navController.popBackStack()`，让编辑保存后自然回到上一级 `HistoryDetailScreen`（详情页本身通过 `observeSession` 实时流订阅，会自动展示修改后的最新结果，用户确认无误再按返回即可回到刚才的历史日历或趋势位置）。

---

#### UX-07 · [P1] 医生报告预览页未拦截返回操作，点返回直接退出整个报告模块

- **所属页面 / 组件**：导出医生报告 `ReportScreen` / `ReportPreviewScreen`
- **源码位置**：[ReportOptionsScreen.kt](file:///e:/blood%20pressure%20record/app/src/main/java/com/example/bloodpressurerecord/ui/report/ReportOptionsScreen.kt#L42-L50)、[ReportPreviewScreen.kt](file:///e:/blood%20pressure%20record/app/src/main/java/com/example/bloodpressurerecord/ui/report/ReportPreviewScreen.kt#L64-L103)
- **属性标签**：`P1` · `客观缺陷` · `源码静态可验证` · `交互体验 / 导航逻辑`
- **问题依据**：
  `ReportScreen` 在单一路由 `settings/data/report` 内用 `if (state.artifact != null)` 切换「周期/模板选择页（`ReportOptionsScreen`）」与「PDF 预览页（`ReportPreviewScreen`）」。但传入 `ReportPreviewScreen` 的 `onBack` 仍然是外部的 `{ navController.popBackStack() }`，且未注册 `BackHandler`。用户在预览页想退回上一步改选“近 90 天”或切换“7 日监测模板”时，无论是点左上角 `<` 按钮还是滑系统返回手势，**都会直接退回「数据管理」页面并销毁 `ReportViewModel`**，只有滚到 PDF 最底端点击「重新选择周期」才能回到选项页。
- **用户影响**：严重违背 Android 层级返回直觉（对比 `AddMeasurementScreen` 的 `showSupplement` 和 `TrendScreen` 的 `fullscreen` 均正确用 `BackHandler` 退回上一子状态）。
- **高收益低改动优化建议**：
  在 `ReportPreviewScreen` 中添加 `BackHandler(enabled = !state.isSaving) { viewModel.returnToOptions() }`，并将顶栏 `AppTopBar` 的 `onBack` 绑定到 `{ if (!state.isSaving) viewModel.returnToOptions() }`。

---

#### UX-08 · [P1] 编辑记录页丢失旧版通用备注 `note` 字段的展示与修改入口

- **所属页面 / 组件**：编辑记录页 `EditSessionScreen` / `SessionSupplementFields`
- **源码位置**：[EditSessionScreen.kt](file:///e:/blood%20pressure%20record/app/src/main/java/com/example/bloodpressurerecord/ui/history/EditSessionScreen.kt#L220-L223)、[EditSessionViewModel.kt](file:///e:/blood%20pressure%20record/app/src/main/java/com/example/bloodpressurerecord/ui/history/EditSessionViewModel.kt#L126-L129)、[HistoryDetailScreen.kt](file:///e:/blood%20pressure%20record/app/src/main/java/com/example/bloodpressurerecord/ui/history/HistoryDetailScreen.kt#L169-L170)
- **属性标签**：`P1` · `客观缺陷` · `源码静态可验证` · `健康数据体验 / 边界场景`
- **问题依据**：
  v2.1.2 以前保存的历史记录或旧版 Excel 导入记录使用 `session.note` 存储备注，该字段在 `HistoryScreen`（日历备注标记与卡片摘要）、`NoteSearchScreen`（关键词搜索高亮）、`HistoryDetailScreen:169`（补充情况中的 `“$it”`）以及导出医生报告中均会正常展示。但进入 `EditSessionScreen` 点击「补充情况」后，`SessionSupplementFields` 只渲染 `symptomNote`（有没有不舒服？）和 `factorNote`（有没有可能影响血压的情况？）两个新字段，`EditSessionViewModel` 只是在后台原样回写 `note = state.note`，**界面上既看不到原 `note` 文字，也无法修改或清空它**。
- **用户影响**：用户通过「搜索历史备注」搜出一条带错别字或隐私备注的旧记录，点进详情看到备注，再点「编辑 → 补充情况」却发现两个输入框一片空白，完全改不了原备注。
- **高收益低改动优化建议**：
  按最小兼容原则：仅当 `state.note.isNotBlank()` 时，在 `EditSessionScreen` 的 `SessionSupplementFields` 下方条件渲染一个 `OutlinedTextField(value = state.note, onValueChange = viewModel::updateNote, label = { Text("原备注") })`。新记录 `note` 为空时不显示，不增加新流程负担，同时彻底打通历史记录可编辑闭环。

---

#### UX-09 · [P1] 新增页与编辑页表单交互割裂，且编辑页缺失页内高风险提示并忽略高风险提醒开关

- **所属页面 / 组件**：新增页 `AddMeasurementScreen` vs 编辑页 `EditSessionScreen` / `EditSessionViewModel`
- **源码位置**：[AddMeasurementScreen.kt](file:///e:/blood%20pressure%20record/app/src/main/java/com/example/bloodpressurerecord/ui/record/AddMeasurementScreen.kt#L246-L330) vs [EditSessionScreen.kt](file:///e:/blood%20pressure%20record/app/src/main/java/com/example/bloodpressurerecord/ui/history/EditSessionScreen.kt#L158-L219)、[EditSessionViewModel.kt](file:///e:/blood%20pressure%20record/app/src/main/java/com/example/bloodpressurerecord/ui/history/EditSessionViewModel.kt#L236-L249)
- **属性标签**：`P1` · `客观缺陷` · `源码静态可验证` · `设计一致性 / 交互体验`
- **问题依据**：
  同一个产品中的“录入血压表单”在新增和编辑两处呈现截然不同的交互形态：
  1. **多组布局割裂**：v2.0.2 已将 `AddMeasurementScreen` 升级为更直观的“纵向平铺各组读数 + 底部虚线胶囊『再加一组』”，但 `EditSessionScreen:159-174` 仍停留在旧版“横向滚动 `SessionChoiceChip` 标签栏 + 每次只显示单组 `MeasurementReadingCard` + 普通 `TextButton('添加一组')`”。老年用户编辑时无法同屏对比第 1 组与第 2 组数值。
  2. **高风险提示与开关不一致**：`AddMeasurementScreen:316` 在输入达到 ≥180/120 时实时展开醒目的红色容器横幅提示，并在点击保存时遵守 `highRiskAlertsEnabled` 用户开关；而 `EditSessionScreen` 没有实时横幅提示，`EditSessionViewModel:236`（如 [audit-2026-11-24.md](file:///e:/blood%20pressure%20record/docs/audit-2026-11-24.md#L23) 亦指出）甚至未注入 `highRiskAlertEnabled` 设置，即使用户已在显示设置中关闭高风险弹窗，编辑保存仍强制弹窗拦截。
- **用户影响**：用户需要学习两套录入界面，编辑多组读数核对效率低，且设置开关在编辑路径失效。
- **高收益低改动优化建议**：
  将 `AddMeasurementScreen` 的纵向读数组列表 + `DashedAddGroupButton` + 高风险实时提示抽为共享 `SessionReadingsFormSection` 供 `EditSessionScreen` 直接复用，并在 `EditSessionViewModel` 注入 `highRiskAlertEnabled` 保持规则一致。

---

#### UX-10 · [P1] 多处关键视觉边界与辅助文字未达 WCAG 2.1 无障碍对比度标准

- **所属页面 / 组件**：数字输入框 `CompactNumberField`、深色趋势图 `TrendChartDrawing`、首页 `ReadingHero`
- **源码位置**：[SessionFormComponents.kt](file:///e:/blood%20pressure%20record/app/src/main/java/com/example/bloodpressurerecord/ui/common/SessionFormComponents.kt#L414-L420)、[TrendChartDrawing.kt](file:///e:/blood%20pressure%20record/app/src/main/java/com/example/bloodpressurerecord/ui/history/TrendChartDrawing.kt#L56)、[DashboardScreen.kt](file:///e:/blood%20pressure%20record/app/src/main/java/com/example/bloodpressurerecord/ui/home/DashboardScreen.kt#L280-L300)
- **属性标签**：`P1` · `客观缺陷` · `源码静态可验证` · `无障碍与适老化 / 视觉设计`
- **问题依据**（基于源码精确色值按 WCAG 2.1 相对亮度公式计算）：
  1. **血压数字输入框未聚焦边框几乎隐形**：`CompactNumberField` 将 `unfocusedBorderColor` 设为 `colors.outlineVariant.copy(alpha = 0.45f)`，容器色为 `colors.surface`。
     - 浅色模式：`#E3DDD5` 叠 `0.45` 透明度于 `#FFFDFA` → 实效色 `#F2EFE9`，与输入框底对比度仅 **`1.13:1`**（且输入框 `#FFFDFA` 与页面底 `#FAF6EF` 对比度仅 **`1.05:1`**）。
     - 深色模式：`#3A342F` 叠 `0.45` 透明度于 `#26201C` → 实效色 `#2F2925`，与输入框底对比度仅 **`1.12:1`**。
     远低于 WCAG 2.1 SC 1.4.11 表单控件边界 **3:1** 的最低要求。
  2. **深色模式趋势图高风险圆环对比度不足**：`trendChartPalette()` 第 56 行未取 `scheme.error`（深色下为 `#FFB4AB`），而是硬编码浅色错误红 `outlier = WarmError (#B3261E)`。在深色图表底 `#26201C` 上对比度仅 **`2.46:1`**（低于非文本图形 3:1 标准），暗色下暗红细圈极难辨认。
  3. **首页大卡 `mmHg` 与平均组数文字偏淡**：`ReadingHero` 对 `13.sp` 的 `"mmHg"` 和 `"2 组平均"` 额外乘了 `look.ink.copy(alpha = 0.70f / 0.72f)`。在浅色「正常 (`#DFF4E9`) / 正常高值 (`#EEF3D7`) / 1级 (`#F7F0CF`)」底色上，实效对比度分别跌至 **`2.72:1` / `2.67:1` / `2.76:1`**（远低于小字号正文 WCAG AA **4.5:1** 标准；若不乘 `0.7f` 透明度则本身为 `4.56:1–5.37:1` 恰好全部达标！）。
- **用户影响**：中老年群体普遍存在晶状体黄化与对比敏感度下降，极淡的输入框轮廓容易让人看不清“点哪里输入低压/脉搏”，暗色高风险标记易被漏看。
- **高收益低改动优化建议**：
  1. `CompactNumberField` 未聚焦边框去掉 `.copy(alpha = 0.45f)` 并改用 `colors.outline`（或浅色 `#C9C0B5` / 深色 `colors.outline #7F7973`），使边框对比度提升至 `≥3:1`。
  2. `trendChartPalette()` 中将 `outlier = WarmError` 改为 `outlier = scheme.error`。
  3. `ReadingHero` 中去掉 `look.ink.copy(alpha = 0.7f / 0.72f / 0.82f)` 的二次透明度衰减，直接使用 `look.ink`（本身已是柔和彩色墨水色，对比度直接回到 `≥4.56:1`）。

---

#### UX-11 · [P1] 默认开启应用内 `1.15x` 大字且与系统大字无上限相乘，固定高度容器在大字机型上有裁切风险

- **所属页面 / 组件**：全局入口 `MainActivity`、录入保存按钮 `InlineSessionAction`、悬浮底栏 `AppBottomDock`、首页空状态按钮
- **源码位置**：[MainActivity.kt](file:///e:/blood%20pressure%20record/app/src/main/java/com/example/bloodpressurerecord/MainActivity.kt#L34-L48)、[AppSettingsStore.kt](file:///e:/blood%20pressure%20record/app/src/main/java/com/example/bloodpressurerecord/data/datastore/AppSettingsStore.kt#L54)、[SessionFormComponents.kt](file:///e:/blood%20pressure%20record/app/src/main/java/com/example/bloodpressurerecord/ui/common/SessionFormComponents.kt#L509-L514)、[AppBottomDock.kt](file:///e:/blood%20pressure%20record/app/src/main/java/com/example/bloodpressurerecord/ui/common/AppBottomDock.kt#L99-L135)、[DashboardScreen.kt](file:///e:/blood%20pressure%20record/app/src/main/java/com/example/bloodpressurerecord/ui/home/DashboardScreen.kt#L357)
- **属性标签**：`P1` · `潜在风险` · `源码可算 + 需实机运行验证` · `无障碍与适老化 / 屏幕适配`
- **问题依据**：
  1. `AppSettingsStore` 默认 `largeTextEnabled = true`，`MainActivity` 直接设置 `fontScale = currentDensity.fontScale * 1.15f` 且无上限钳制。许多长辈手机系统本身已调至 `1.5x` 甚至 `2.0x` 字体，进应用后实际 `fontScale` 叠乘达到 **`1.725x` 乃至 `2.30x`**（而现有自动化测试仅覆盖到 `2.0x`）。
  2. 虽然 `AppPrimaryButton` 正确使用了 `Modifier.heightIn(min = 54.dp)`，但以下关键控件却写了**死高度 `.height(...)`** 覆盖了最小高度：
     - `InlineSessionAction:513`（新增/编辑保存按钮）：`.height(AppDimensions.saveButtonHeight)`（固定 `58.dp`），内部 `Button` 上下 `contentPadding` 共 `24.dp`，仅剩 `34.dp` 内容高度；在 `2.30x` 下 `18.sp` 文字高达 `41.4dp`，必然上下顶边或裁切。
     - `DashboardScreen:357`（「记第一次血压」按钮）：`.height(52.dp)` 固定死高度。
     - `AddMeasurementScreen:486`（「再加一组」虚线按钮）：`.height(48.dp)` 固定死高度。
     - `AppBottomDock:99,131`：胶囊容器写死 `.height(64.dp)`，内部含 `22.dp` 图标 + `11.sp` 标签（`2.30x` 下约 `25.3dp`）+ `4.dp` 圆点 + `5.dp` 间距 = `56.3dp+` 行高，文字与圆点极度拥挤。
- **用户影响**：原本最需要系统大字 + 应用大字的高龄视弱用户，反而在最关键的「保存记录」「记第一次血压」按钮和底部导航上遇到文字上下被切边的风险。
- **高收益低改动优化建议**：
  1. 将 `InlineSessionAction` 的 `.height(AppDimensions.saveButtonHeight)`、`DashboardScreen:357` 的 `.height(52.dp)`、`DashedAddGroupButton` 的 `.height(48.dp)` 全部改为 `.heightIn(min = ...)`。
  2. 在 `MainActivity` 对合成后的 `fontScale` 增加合理安全上限（如 `(currentDensity.fontScale * appFontScale).coerceAtMost(2.0f)`），或对 `AppBottomDock` 的 `11.sp` 导航标签限制最大缩放倍率（类似系统原生 BottomNavigation 做法），确保图标与选中圆点不被挤出胶囊。

---

#### UX-12 · [P2] 两套“血压目标”概念并存，且首页与趋势/报告的“早晚”及“周起始日”口径不一

- **所属页面 / 组件**：首页 `DashboardScreen`、历史页 `HistoryScreen`、趋势页 `TrendScreen`、我的 `SettingsScreen` / `SettingsProfileScreen`
- **源码位置**：[SettingsScreen.kt](file:///e:/blood%20pressure%20record/app/src/main/java/com/example/bloodpressurerecord/ui/settings/SettingsScreen.kt#L124-L134)、[DashboardViewModel.kt](file:///e:/blood%20pressure%20record/app/src/main/java/com/example/bloodpressurerecord/ui/home/DashboardViewModel.kt#L173-L174)、[SessionStatisticsCalculator.kt](file:///e:/blood%20pressure%20record/app/src/main/java/com/example/bloodpressurerecord/domain/calculator/SessionStatisticsCalculator.kt#L9-L13)、[NaturalWeek.kt](file:///e:/blood%20pressure%20record/app/src/main/java/com/example/bloodpressurerecord/domain/time/NaturalWeek.kt#L14-L25) vs [CalendarModels.kt](file:///e:/blood%20pressure%20record/app/src/main/java/com/example/bloodpressurerecord/ui/history/CalendarModels.kt#L28-L62)
- **属性标签**：`P2` · `客观缺陷` · `源码静态可验证` · `信息架构 / 健康数据体验`
- **问题依据**：
  应用内存在三组跨页面口径冲突：
  1. **两套“目标”互不相通**：「我的」顶部资料卡显示 `目标高压 / 目标低压`（在「用户资料」编辑，作用于历史日历“达标天数”、趋势图目标虚线与“达标率”），紧接着下方又有一行「血压目标范围」（打开 `BpZoneTargetSheet` 编辑 10 个分界值，仅作用于首页四段示意条）。用户在「用户资料」设了目标 `< 135/85`，回到首页发现示意条分界还是 `120/80` 和 `140/90`；反之改了「血压目标范围」，趋势页达标率却提示“尚未在我的设置目标”。
  2. **早/晚测量时段口径不一**（关联 [audit-2026-11-24.md](file:///e:/blood%20pressure%20record/docs/audit-2026-11-24.md#L22)）：首页「今天的测量」按 `< 12:00` 归入“早上”、`>= 12:00` 归入“晚上”（下午 14:00 测一次会点亮首页“晚上”槽，凌晨 02:00 测一次会点亮“早上”槽）；但记录本身的自动时段 `MeasurementPeriod` 把 12–17 点标为“下午”，且趋势页与医生报告的早间/晚间均值仅统计 `05:00–11:59` 与 `18:00–23:59`。
  3. **一周起始日不一致**：首页「这一周」打卡点与柱状图采用**周日开头**（`日 一 二 三 四 五 六`），而切到隔壁「历史」页，月历表头与「近期 · 本周」统计却采用**周一开头**（`一 二 三 四 五 六 日`）。周日当天用户在首页看自己是“新一周第 1 天”，切到历史「本周」看的却是“上一周最后 1 天”。
- **用户影响**：增加中老年用户和查数家属的理解成本，容易产生“为什么首页显示晚上测了，趋势晚间平均却是空的？”等疑惑。
- **高收益低改动优化建议**：
  1. 将「血压目标范围」重命名为更准确的**「首页区间条分界」**（副标题注明“调整首页色条四段刻度；达标统计请在个人资料设置目标血压”），消除与医学达标值的命名混淆。
  2. 统一早/晚统计口径（首页早晚槽与 `SessionStatisticsCalculator` 对齐，或将首页 `>=12` 且 `<18` 的下午记录明确展示/提示），并将自然周起始日统一为国内习惯的**周一起始**（或保持一致说明）。

---

#### UX-13 · [P2] 收缩压与舒张压折线配色在趋势页、桌面小部件与医生报告 PDF 三处不一致甚至完全反转

- **所属页面 / 组件**：趋势图 `TrendChartDrawing`、桌面小部件 `BloodPressureWidget`、医生报告 `ReportTheme`
- **源码位置**：[TrendChartDrawing.kt](file:///e:/blood%20pressure%20record/app/src/main/java/com/example/bloodpressurerecord/ui/history/TrendChartDrawing.kt#L49-L50)、[BloodPressureWidget.kt](file:///e:/blood%20pressure%20record/app/src/main/java/com/example/bloodpressurerecord/widget/BloodPressureWidget.kt#L320-L321)、[ReportTheme.kt](file:///e:/blood%20pressure%20record/app/src/main/java/com/example/bloodpressurerecord/export/report/ReportTheme.kt#L151-L152)
- **属性标签**：`P2` · `客观缺陷` · `源码静态可验证` · `视觉设计 / 健康数据体验`
- **问题依据**：
  同一用户的两条核心血压曲线在应用三个触点使用了三套颜色映射：
  - **应用内趋势图**（`TrendChartDrawing`）：**收缩压 = 珊瑚橙**（`#B94B39` / `#FFA997`），**舒张压 = 深蓝/天蓝**（`#02578B` / `#8BD2FF`）；且脉搏副图折线也复用了同一个珊瑚橙（`palette.systolic`）。
  - **桌面小部件 4×4 趋势图**（`BloodPressureWidget`）：**收缩压 = 陶土橙**（`#963828`），**舒张压 = 鼠尾草绿**（`#357D58`），且 `Color.kt:46` 仍残留旧常量注释 `SageLine // 折线舒张压`。
  - **医生报告 PDF 趋势图**（`ReportTheme`）：`SYSTOLIC = 0xFF1F5FA8`（**蓝色**），`DIASTOLIC = 0xFFD9622B`（**橙色**）——**与端内趋势图的橙/蓝配色 100% 对调反转**！
- **用户影响**：虽然 PDF 自身带有 `● 收缩压 ◆ 舒张压` 图例，但长期习惯看 App 内“橙色是高压、蓝色是低压”的用户和家属，在导出预览页看到“蓝色是高压、橙色是低压”极易瞬间看反。
- **高收益低改动优化建议**：
  端内趋势图的“橙（高压）+ 蓝（低压）”对红绿色弱用户非常友好，建议以此为唯一基准：将 `ReportTheme.kt` 中的 `SYSTOLIC` 与 `DIASTOLIC` 颜色对调（高压用暖陶土橙 `#C4513E`/`#D9622B`，低压用深蓝 `#1F5FA8`），并将小部件低压色同步为深蓝 `#02578B`。

---

#### UX-14 · [P2] 全应用散落 5 套硬编码血压分级色盘，深色判定靠 RGB 猜测，且冷启动窗口背景色与主题脱节

- **所属页面 / 组件**：`DesignSystem.kt`、`DashboardScreen.kt`、`HistoryScreen.kt`、`TrendScreen.kt`、`BpRangeTokens.kt`、`res/values*/colors.xml`
- **源码位置**：[DesignSystem.kt](file:///e:/blood%20pressure%20record/app/src/main/java/com/example/bloodpressurerecord/ui/theme/DesignSystem.kt#L91-L176)、[DashboardScreen.kt](file:///e:/blood%20pressure%20record/app/src/main/java/com/example/bloodpressurerecord/ui/home/DashboardScreen.kt#L200-L211)、[HistoryScreen.kt](file:///e:/blood%20pressure%20record/app/src/main/java/com/example/bloodpressurerecord/ui/history/HistoryScreen.kt#L889-L910)、[TrendScreen.kt](file:///e:/blood%20pressure%20record/app/src/main/java/com/example/bloodpressurerecord/ui/history/TrendScreen.kt#L509-L516)、[colors.xml](file:///e:/blood%20pressure%20record/app/src/main/res/values/colors.xml#L4)、[values-night/colors.xml](file:///e:/blood%20pressure%20record/app/src/main/res/values-night/colors.xml#L4)
- **属性标签**：`P2` · `客观缺陷` · `源码静态可验证` · `工程实现 / 视觉一致性`
- **问题依据**：
  1. **5 套分级色值各自硬编码**：仅“血压偏低（`LOW`）”一个状态，在 `DesignSystem.bloodPressureChipColors` 与 `DashboardScreen.gradeLook` 中是**薰衣草紫**（`#E9E9FA`），在 `DesignSystem.BloodPressureVisualStatus.LOW` 中是**天蓝**（`#CAEDFF`，而上方注释却写“偏低走浅橙提示”），在 `HistoryScreen.historyGradeColors` 中是**灰蓝**（`#DDE8FA`），在 `TrendScreen.trendGradeColor` 中是**中蓝**（`#7AA9D4`），而在首页 `BpRangeTokens` 中又是**浅薄荷绿**（`#BFE3D0`，与正常绿同色系！）。
  2. **深色模式靠启发式猜测**：`DesignSystem.kt:152` 和 `DashboardScreen.kt:209` 用 `colors.background.red < 0.5f` 判断深色，而 `HistoryScreen.kt:893` 用 `luminance() < 0.5f`。
  3. **冷启动首帧色差与手动深浅色闪屏**：`values/colors.xml` 中 `@color/warm_background` 仍为旧版奶黄色 `#F5EAD8`（Compose 实际背景为 `#FAF6EF`），`values-night/colors.xml` 为 `#201E1D`（Compose 实际为 `#1A1512`）；且 `MainActivity` 在 DataStore 首帧读取完成前默认按 `system` 渲染，手动锁定深色/浅色的用户冷启动会出现首帧底色跳变。
- **用户影响**：同一用户在首页看“偏低”是紫底配绿条，去日历看是蓝格，视觉记忆难以固化；冷启动首帧底色有轻微跳变。
- **高收益低改动优化建议**：
  1. 将 `values/colors.xml` 更新为 `#FAF6EF`、`values-night/colors.xml` 更新为 `#1A1512`。
  2. 在 `Theme.kt` 通过 `CompositionLocal`（如 `LocalIsDarkTheme` 与统一的 `LocalBpGradePalette`）向下分发六级语义色，收敛 `DesignSystem`、`Dashboard`、`History`、`Trend` 四处重复硬编码；并将 `BpRangeTokens` 的 `LOW` 段由薄荷绿改为与分级一致的柔和蓝紫色系，避免“偏低”看起来像“正常绿”。

---

#### UX-15 · [P2] 趋势图双指标模式缺少颜色图例，140/90 参考线与目标线无法区分，且读数栏不显示分级

- **所属页面 / 组件**：趋势页 `TrendScreen` / `TrendFullscreenScreen` / `TrendChartDrawing`
- **源码位置**：[TrendScreen.kt](file:///e:/blood%20pressure%20record/app/src/main/java/com/example/bloodpressurerecord/ui/history/TrendScreen.kt#L564-L588)、[TrendScreen.kt](file:///e:/blood%20pressure%20record/app/src/main/java/com/example/bloodpressurerecord/ui/history/TrendScreen.kt#L669-L698)、[TrendChartDrawing.kt](file:///e:/blood%20pressure%20record/app/src/main/java/com/example/bloodpressurerecord/ui/history/TrendChartDrawing.kt#L53-L54)、[TrendChartDrawing.kt](file:///e:/blood%20pressure%20record/app/src/main/java/com/example/bloodpressurerecord/ui/history/TrendChartDrawing.kt#L177-L200)
- **属性标签**：`P2` · `客观缺陷` · `源码静态可验证` · `健康数据体验`
- **问题依据**：
  1. **缺少双折线图例与单位标注**：主血压图默认处于「双指标」且未点击节点时（`selectedPoint == null`），图上画出一橙一蓝两条折线，但既没有 `● 收缩压  ● 舒张压` 图例，左上角也没有像脉搏图（`TrendChart.kt:606` 标了 `次/分`）那样标注 `mmHg`。
  2. **参考线与目标线完全同构**：`drawThresholdLines` 中固定参考线（140/90）透明度 `REFERENCE_LINE_ALPHA = 0.42f`，用户目标线透明度 `palette.targetDim = 0.42f`，且线宽（`1.2dp`）、虚线步长（`6dp/5dp`）、颜色完全一模一样，也无任何微缩刻度标签。当用户设置了目标血压（如 `130/80`）时，图上同时出现**两条一模一样的橙色虚线和两条一模一样的蓝色虚线**，完全无法分辨哪条是指南 140/90、哪条是个人目标。
  3. **顶部读数栏缺失血压分级**：`TrendSelectionReadout` 与横屏 `TrendFullscreenReadout` 显示了时间、`135/85 mmHg`、脉搏及是否高风险，但没有展示 `point.category` 的中文分级（如 `正常高值` / `1级高血压`）。
- **用户影响**：设置过目标血压的用户面对四条无字虚线极易混淆；逐点滑动十字线查数时需心算该点属于几级高血压。
- **高收益低改动优化建议**：
  1. 在 `TrendCard` 图表左上方增加紧凑一行图例：`● 收缩压  ● 舒张压 (mmHg)`（存在目标线时补充 `-- 参考 140/90  ·· 目标`）。
  2. 将用户目标线改为点状虚线（如 `2dp, 4dp`）或在右端贴边绘制微缩文字 `目标 130`，与 `140/90` 长虚线拉开视觉区分。
  3. 在 `TrendSelectionReadout` 的 `FlowRow` 中补充 `StatusChip`（或紧凑分级文本 `CategoryPresentation.label(displayed.category)`）。

---

#### UX-16 · [P2] 趋势页空数据状态下重复堆叠多张空卡片，且达标率圆环底色未适配深色模式

- **所属页面 / 组件**：趋势页 `TrendScreen`
- **源码位置**：[TrendScreen.kt](file:///e:/blood%20pressure%20record/app/src/main/java/com/example/bloodpressurerecord/ui/history/TrendScreen.kt#L151-L205)、[TrendScreen.kt](file:///e:/blood%20pressure%20record/app/src/main/java/com/example/bloodpressurerecord/ui/history/TrendScreen.kt#L360-L384)
- **属性标签**：`P2` · `客观缺陷` · `源码静态可验证` · `边界场景 / 深色模式 / 无障碍`
- **问题依据**：
  1. **空状态叠床架屋**：当所选范围无记录（`series.points.isEmpty()`）时，第 152 行先渲染一张带主按钮的大卡 `TrendEmptySection("最近30天暂无记录")`；紧接着下方**并未提前结束**，第 163 行继续默认展开「血压趋势」折叠区，内部又渲染第二张空状态卡 `TrendEmptyState("最近30天 暂无数据")`；再往下继续默认展开「平均血压」显示三张 `—` 空白统计卡，以及「脉搏趋势」「昼夜统计」「更多统计」三个“暂无数据”折叠头。
  2. **深色模式圆环轨道突兀 + 无障碍缺失**：第 364 行达标率圆环背景轨道硬编码为浅薄荷白 `Color(0xFFDDE8DF)`，在深色卡片 `#26201C` 上产生 **`12.78:1`** 的刺眼亮白圆环（当达标率较低或未设目标 `sweep = 0` 时尤为突兀）；且该 `Canvas`+中心百分比未合并无障碍语义。
- **用户影响**：新用户首次点进「趋势」页看到满屏重复的“暂无记录 / 暂无数据 / —”，显得界面杂乱未收尾。
- **高收益低改动优化建议**：
  1. 当 `series.points.isEmpty()` 时，展示顶部的范围选择器 + 单一 `TrendEmptySection` 引导卡即可，隐藏下方空的图表折叠区与全 `—` 统计区。
  2. 将达标率圆环轨道色改为 `MaterialTheme.colorScheme.surfaceContainerHighest`，进度色复用主题 `secondary`（鼠尾草绿），并为外层 `Row` 补上 `semantics(mergeDescendants = true)`。

---

#### UX-17 · [P2] 医生报告自定义周期要求手输 `yyyy-MM-dd` 文本，且预览页操作按钮深埋在多页 PDF 最底端

- **所属页面 / 组件**：医生报告 `ReportOptionsScreen`（`CustomPeriodDialog`）与 `ReportPreviewScreen`
- **源码位置**：[ReportOptionsScreen.kt](file:///e:/blood%20pressure%20record/app/src/main/java/com/example/bloodpressurerecord/ui/report/ReportOptionsScreen.kt#L144-L179)、[ReportPreviewScreen.kt](file:///e:/blood%20pressure%20record/app/src/main/java/com/example/bloodpressurerecord/ui/report/ReportPreviewScreen.kt#L103-L197)
- **属性标签**：`P2` · `客观缺陷` · `源码静态可验证` · `适老化体验 / 操作效率`
- **问题依据**：
  1. **手打 ISO 日期反适老化**：应用在录入页和历史页都已经成熟使用了 Material 3 `DatePickerDialog`，唯独 `CustomPeriodDialog`（第 159–160 行）放了两个普通文本 `OutlinedTextField`，要求用户切换英文半角连字符手动敲入 `2026-10-01` 格式！老年用户在中文输入法下极易打出中文顿号、空格或 `2026.10.1` 导致反复报错。
  2. **预览页首尾分离与 5 按钮堆叠**：`ReportPreviewScreen` 将 3 个隐私开关放在 `LazyColumn` 第 1 项，中间平铺全部 A4 页面（90 天或 365 天明细可达 5–15 页），最后把 **5 个全宽按钮**（`分享 PDF`、`保存到文件`、`打印报告`、`保存摘要图片`、`重新选择周期`）和状态提示 `state.message` 全堆在列表最末尾。用户每次在顶部切换“包含姓名”开关后，必须重新滑过十几页 PDF 才能点到分享/保存按钮。
- **用户影响**：自定义日期门槛过高；多页报告预览导出操作费力，且点击保存后的提示文字易被忽略。
- **高收益低改动优化建议**：
  1. 将 `CustomPeriodDialog` 的两个文本输入框替换为复用 `DateTimePillButton` + `DatePickerDialog` 点选起止日期。
  2. 在 `ReportPreviewScreen` 底部固定一个精简底栏（主按钮 `分享 PDF` + 次按钮 `保存/打印…` 展开菜单或双列次级按钮，注意保留 `canExport` 需至少浏览首屏摘要或加载完成的校验提示），减少长距离往返滚动并统一使用 `AppPrimaryButton`/`AppSecondaryButton` 样式。

---

#### UX-18 · [P2] 首页传入的 `onViewTodayRecords` 未接线，主读数卡与已测早晚卡均不可点击查看详情

- **所属页面 / 组件**：首页 `DashboardScreen` + `AppNavigation`
- **源码位置**：[AppNavigation.kt](file:///e:/blood%20pressure%20record/app/src/main/java/com/example/bloodpressurerecord/navigation/AppNavigation.kt#L254-L264)、[DashboardScreen.kt](file:///e:/blood%20pressure%20record/app/src/main/java/com/example/bloodpressurerecord/ui/home/DashboardScreen.kt#L98)、[DashboardScreen.kt](file:///e:/blood%20pressure%20record/app/src/main/java/com/example/bloodpressurerecord/ui/home/DashboardScreen.kt#L239-L243)、[DashboardScreen.kt](file:///e:/blood%20pressure%20record/app/src/main/java/com/example/bloodpressurerecord/ui/home/DashboardScreen.kt#L431-L455)
- **属性标签**：`P2` · `客观缺陷` · `源码静态可验证` · `交互体验`
- **问题依据**：
  1. `AppNavigation` 专门实现了跳转到历史页并自动定位今日日期的 `onViewTodayRecords` 回调传给 `DashboardScreen`，但 `DashboardScreen` 函数体内**一次都未使用 `onViewTodayRecords` 参数**。
  2. 首页「今天的测量」早/晚卡片在 `session == null`（待测）时可点击去新增，一旦测完显示 `128/82 正常` 后（第 435 行），**立刻变成纯静态不可点击色块**；上方的 `ReadingHero` 大卡同样没有任何点击事件（除点击小压换台词外）。用户刚测完回首页看到刚才的读数想查看原始两组明细或改个备注，点击大卡和早晚卡均毫无反应。
- **用户影响**：违背移动端卡片直觉（展示具体记录的卡片通常可点进详情），浪费了已写好的跨页定位能力。
- **高收益低改动优化建议**：
  将「今天的测量」标题右侧或已测量的 `MeasurementSlot` 绑定 `onOpenDetail(session.id)`（或 `onViewTodayRecords`），并为 `ReadingHero` 的读数区域绑定打开 `HistoryDetail.route(latest.id)`，一步打通“首页看数 → 点开查改”的高频路径。

---

#### UX-19 · [P2] 「补充情况」子视图缺少显式返回读数按钮，且「记录完成」弹窗 5 秒无提示自动关闭

- **所属页面 / 组件**：`AddMeasurementScreen` / `EditSessionScreen` / `SessionCompletedDialog`
- **源码位置**：[AddMeasurementScreen.kt](file:///e:/blood%20pressure%20record/app/src/main/java/com/example/bloodpressurerecord/ui/record/AddMeasurementScreen.kt#L331-L352)、[SessionFormComponents.kt](file:///e:/blood%20pressure%20record/app/src/main/java/com/example/bloodpressurerecord/ui/common/SessionFormComponents.kt#L605-L660)
- **属性标签**：`P2` · `主观设计建议` · `源码静态可验证` · `交互体验 / 适老化`
- **问题依据**：
  1. **补充情况回看路径隐蔽**：在录入页点击底部的「补充情况（选填）」后，整个表单切换为两个备注框，底部按钮仍是「保存记录」。若老年用户填完症状想再核对一眼刚才输的高压低压，页面内容区**没有“返回读数”按钮**（第 348 行 `if (!showSupplement)` 直接隐藏了切换按钮），只能点左上角 `<`——而许多长辈担心点左上角 `<` 会退出整个记录页白填了。
  2. **完成弹窗静默倒计时 + 缺分级**：`SessionCompletedDialog` 启动了一个不可见的 `5_000ms` 协程定时关闭器，界面上既未写“5 秒后自动返回”，也未展示本次平均值的血压分级芯片。老年用户阅读速度较慢，往往刚看清数字准备伸手点左下角「修改」时，弹窗在第 5 秒突然消失并切回首页。
- **用户影响**：子步骤切换存在轻微心理不安感；5 秒静默闪退弹窗对反应较慢的长辈不够友好。
- **高收益低改动优化建议**：
  1. 在 `showSupplement == true` 时，将底部次级按钮保留并文案改为 `"返回查看读数"`（点击设 `showSupplement = false`），并在主页面“补充情况（选填）”已填写内容时显示 `"补充情况（已填写 ✓）"` 给予正向确认。
  2. `SessionCompletedDialog` 中补上一枚 `StatusChip` 显示本次分级，并将自动关闭延长至 `8 秒` 或在按钮显示 `"关闭（5s）"` 动态倒计时提示。

---

#### UX-20 · [P2] 草稿恢复与设置操作反馈均置于长页面最底端，且恢复历史草稿时易误存旧测量时间

- **所属页面 / 组件**：`AddMeasurementScreen` / `HomeViewModel`、`SettingsProfileScreen` / `SettingsReminderScreen` / `SettingsDataManagementScreen`
- **源码位置**：[HomeViewModel.kt](file:///e:/blood%20pressure%20record/app/src/main/java/com/example/bloodpressurerecord/ui/home/HomeViewModel.kt#L354-L373)、[AddMeasurementScreen.kt](file:///e:/blood%20pressure%20record/app/src/main/java/com/example/bloodpressurerecord/ui/record/AddMeasurementScreen.kt#L353-L363)、[SettingsSubpages.kt](file:///e:/blood%20pressure%20record/app/src/main/java/com/example/bloodpressurerecord/ui/settings/SettingsSubpages.kt#L119)、[SettingsSubpages.kt](file:///e:/blood%20pressure%20record/app/src/main/java/com/example/bloodpressurerecord/ui/settings/SettingsSubpages.kt#L351)、[SettingsSubpages.kt](file:///e:/blood%20pressure%20record/app/src/main/java/com/example/bloodpressurerecord/ui/settings/SettingsSubpages.kt#L724-L728)
- **属性标签**：`P2` · `客观缺陷` · `源码静态可验证` · `交互反馈 / 边界场景`
- **问题依据**：
  1. **草稿恢复提示沉底 + 旧时间陷阱**：用户三天前点进录入页输了一半选了“保存草稿”，三天后重新点「+ 记一次血压」，`toHomeUiState()` 自动恢复三天前的 `measuredAtText` 和半截数字，而提示语 `"已恢复未保存的测量草稿。"` 却渲染在页面最底部第 353 行（在两组读数卡、加一组按钮、保存按钮、补充情况按钮之下，首屏通常不可见），且没有一键“清空重记 / 更新为当前时间”入口。用户若直接改数字点保存，会把今天测的血压存到三天前的日期里！
  2. **设置反馈沉底且红绿不分**：`SettingsMessage` 放在各设置子页滚动列末尾，且无论成功（“晨间提醒已启用”）还是失败/警告（“请检查用户资料”、“日历同步失败”、“通知权限未授予”）一律使用 `12.sp` 鼠尾草绿色 `colorScheme.secondary`。用户在「提醒中心」顶部开关晨间提醒，反馈文字却在屏幕外的最底部。
- **用户影响**：跨天恢复草稿极易把新测量误记到旧日期；设置页的错误与权限警告因位置沉底且颜色像成功提示而容易被忽略。
- **高收益低改动优化建议**：
  1. 草稿恢复提示移至 `AddMeasurementScreen` 顶部「测量时间」上方，以醒目柔和提示条展示 `"已恢复 X月X日 未保存的草稿"` 并附带 `TextButton("清空重记")`（或点击“用当前时间”一键刷新时间戳）。
  2. 设置子页的操作结果改用根级/页级 `Snackbar` 浮层提示，或区分 `isError` 使用 `colorScheme.error` + `14.sp` 就近展示。

---

#### UX-21 · [P2] 「用户资料」性别采用自由键盘输入，「大字模式」开关在两级设置页重复且命名不一

- **所属页面 / 组件**：`SettingsScreen`、`SettingsProfileScreen`、`SettingsDisplayScreen`
- **源码位置**：[SettingsSubpages.kt](file:///e:/blood%20pressure%20record/app/src/main/java/com/example/bloodpressurerecord/ui/settings/SettingsSubpages.kt#L96)、[SettingsScreen.kt](file:///e:/blood%20pressure%20record/app/src/main/java/com/example/bloodpressurerecord/ui/settings/SettingsScreen.kt#L154-L174)、[SettingsSubpages.kt](file:///e:/blood%20pressure%20record/app/src/main/java/com/example/bloodpressurerecord/ui/settings/SettingsSubpages.kt#L580-L587)
- **属性标签**：`P2` · `主观设计建议` · `源码静态可验证` · `信息架构 / 录入效率`
- **问题依据**：
  1. `SettingsProfileScreen:96` 将「性别」做成了普通自由文本 `OutlinedTextField`，用户需要调出中文键盘手打“男/女”；同时该页面含 5 个输入框却未添加 `imePadding()`，聚焦底部「目标舒张压」时软键盘会遮挡「保存资料」按钮；修改未点保存直接按返回键也无未保存提醒。
  2. `SettingsScreen` 的「显示」折叠区已有 `大字模式` 开关，下方又有一栏「显示设置（趋势图和高风险提醒）」，点进 `SettingsDisplayScreen` 后只有 3 个开关，其中第 3 个又是同一个布尔状态但改名叫 `大字号显示`；而 `启用高风险提醒` 本质属于安全提醒规则而非视觉显示项。
- **用户影响**：手打性别低效；同一开关两处不同名显得拼凑，三级页层级偏碎。
- **高收益低改动优化建议**：
  1. 「性别」改为三选一 `SessionChoiceChip` / `SegmentedPillGroup`（`男` / `女` / `未填`，同时兼容展示已有自定义文本），并为 `SettingsProfileScreen` 补上 `imePadding()`。
  2. 将 `显示趋势图` 与 `启用高风险提醒` 直接合入「我的」页对应分组（或删去 `SettingsDisplayScreen` 中重复的 `大字号显示`），减少一层不必要的跳转。

---

#### UX-22 · [P2] 桌面小部件未适配深色模式，含高风险但均值非 3 级时遗漏“高风险”文字标识

- **所属页面 / 组件**：桌面小部件 `BloodPressureWidget`
- **源码位置**：[BloodPressureWidget.kt](file:///e:/blood%20pressure%20record/app/src/main/java/com/example/bloodpressurerecord/widget/BloodPressureWidget.kt#L113-L199)
- **属性标签**：`P2` · `客观缺陷` · `源码静态可验证` · `工程实现 / 健康数据体验`
- **问题依据**：
  1. 小部件硬编码纯白底 `WidgetBackground = Color(0xFFFFFDF9)` 与 `lightColorScheme()`，在系统深色壁纸/夜间模式下无法跟随切换为暖棕深色卡片。
  2. 第 186–197 行：当一次测量第 1 组为 `182/95`（触发 `containsHighRiskReading = true`）、第 2 组为 `134/85`（平均值 `158/90`，分级为 `STAGE1`“1级高血压”）时，`status` 取到了 `HIGH_RISK` 的深褐红颜色，但 `Text` 仍然只渲染 `CategoryPresentation.label(latest.category)`（即只显示“1级高血压”而不显示“· 含高风险”）。
  3. 辅助文字 `TextMuted (#8A7F76)` 在 `11.sp` 小字号下对比度为 **`3.84:1`**（低于 WCAG AA `4.5:1`，复用主题 `WarmTextBody #645C55` 即可提升至 `6.46:1`）。
  4. 空状态文案写着“*还没有记录 点击开始记一次*”，但点击仅启动 `MainActivity` 停在首页，并未直达新增记录页。
- **用户影响**：夜间看手机桌面时白底小部件刺眼；单组高风险在桌面上被掩盖；空状态点击多一步跳转。
- **高收益低改动优化建议**：
  1. 使用 Glance 的 `ColorProvider(day = ..., night = ...)` 接入项目浅色/深色暖色表面与文字 Token，并将 `TextMuted` 加深至 `#645C55`（浅）/ `#B6B0A9`（深）。
  2. 当 `latest.containsHighRiskReading` 为真时，状态文案拼接为 `"${CategoryPresentation.label(latest.category)} · 高风险"`。

---

#### UX-23 · [P2] 趋势页、横屏全屏页与医生报告页根容器漏设背景色，滑动转场存在透底风险

- **所属页面 / 组件**：`TrendScreen`、`TrendFullscreenScreen`、`ReportOptionsScreen`、`ReportPreviewScreen`
- **源码位置**：[TrendScreen.kt](file:///e:/blood%20pressure%20record/app/src/main/java/com/example/bloodpressurerecord/ui/history/TrendScreen.kt#L116-L120)、[TrendFullscreenScreen.kt](file:///e:/blood%20pressure%20record/app/src/main/java/com/example/bloodpressurerecord/ui/history/TrendFullscreenScreen.kt#L73)、[ReportOptionsScreen.kt](file:///e:/blood%20pressure%20record/app/src/main/java/com/example/bloodpressurerecord/ui/report/ReportOptionsScreen.kt#L80)、[ReportPreviewScreen.kt](file:///e:/blood%20pressure%20record/app/src/main/java/com/example/bloodpressurerecord/ui/report/ReportPreviewScreen.kt#L100)
- **属性标签**：`P2` · `潜在风险` · `源码静态可验证` · `工程实现 / 视觉动效`
- **问题依据**：
  `AppNavigation.kt:197-234` 为非顶级子路由配置了 350ms 容器级覆盖滑动转场（被覆盖的父页仅左移 `1/5` 宽度并淡出，新进入的子页从右侧滑入覆盖在父页之上）。`DashboardScreen`、`HistoryScreen`、`SettingsScreen`、`SettingsSubPageShell` 均显式声明了 `.background(MaterialTheme.colorScheme.background)`；但 `ReportOptionsScreen`、`ReportPreviewScreen`、`TrendScreen` 与 `TrendFullscreenScreen` 根布局均未设置背景色（透明底）。当从「数据管理」推入「导出医生报告」动画进行中（0–350ms）时，新页面空白处会直接透出底下正在左移的「数据管理」文字与按钮残影。
- **用户影响**：父子页面推入/弹出转场及预测式返回手势拖拽过程中出现上下两层文字重叠透底。
- **高收益低改动优化建议**：
  为上述 4 个 Composable 的根容器统一补上一行 `.background(MaterialTheme.colorScheme.background)`。

---

#### UX-24 · [P3] 系统提醒通知缺少直达录入页与通知栏一键打卡快捷操作

- **所属页面 / 组件**：提醒广播接收器 `ReminderReceiver`
- **源码位置**：[ReminderReceiver.kt](file:///e:/blood%20pressure%20record/app/src/main/java/com/example/bloodpressurerecord/reminder/ReminderReceiver.kt#L86-L133)
- **属性标签**：`P3` · `主观设计建议` · `源码静态可验证` · `操作效率`
- **问题依据**：
  晨/晚血压测量提醒与服药提醒通知的 `PendingIntent` 均仅打开裸 `Intent(context, MainActivity::class.java)`（且关联 [audit-2026-11-24.md](file:///e:/blood%20pressure%20record/docs/audit-2026-11-24.md#L11)，提前打卡后到点仍会发服药通知）。用户点测量通知进首页后还需再点一次「+」；点服药通知进首页后还需找到对应药品行点「打卡」。
- **用户影响**：日常高频提醒链路多一步点击。
- **高收益低改动优化建议**：
  在 `MainActivity` 解析 Intent Extra：测量提醒支持直接跳入 `AddMeasurement` 路由；服药通知可增加一个 `.addAction("已服用", markTakenPendingIntent)` 快捷按钮（并在发通知前跳过当日 `taken == true` 的时间点）。

---

#### UX-25 · [P3] 返回按钮与操作按钮存在新旧两套组件混用

- **所属页面 / 组件**：`AppBackButton` vs `AppTopBar`、`ReportOptionsScreen`、`ReportPreviewScreen`、`SettingsReminderScreen`、`HistoryScreen`
- **源码位置**：[AppBackButton.kt](file:///e:/blood%20pressure%20record/app/src/main/java/com/example/bloodpressurerecord/ui/common/AppBackButton.kt#L25-L60) vs [AppComponents.kt](file:///e:/blood%20pressure%20record/app/src/main/java/com/example/bloodpressurerecord/ui/common/AppComponents.kt#L251-L265)、[SettingsSubpages.kt](file:///e:/blood%20pressure%20record/app/src/main/java/com/example/bloodpressurerecord/ui/settings/SettingsSubpages.kt#L206-L213)
- **属性标签**：`P3` · `客观缺陷` · `源码静态可验证` · `设计一致性 / 组件复用`
- **问题依据**：
  1. 代码库同时维护了 `AppBackButton`（44dp 圆形底 + `ArrowBack`，仅在 `TrendScreen` `onBack != null` 分支引用，而当前路由传入恒为 `null`）和 `AppTopBar` 内联返回按钮（48dp 16dp 圆角方底 + `ChevronLeft`）。此外 `TrendScreen` 自定义了无滑动收起的标题行，字号/间距与 `HistoryScreen`/`SettingsScreen` 的 `AppTopBar` 略有差异；`Type.kt:38` 中 `headlineMedium` 注释写 `24sp` 实际却设为 `30.sp`。
  2. `DESIGN.md` 规定主次按钮至少 `54dp` 高、`20dp` 圆角，但 `ReportOptionsScreen:122`、`ReportPreviewScreen:144-194`、`SettingsReminderScreen:206,325`、`HistoryScreen:1056` 仍直接使用默认 40dp 胶囊形 Material `Button` / `OutlinedButton`。
- **用户影响**：二级页面按钮高度与圆角感偶有跳脱，40dp 默认按钮对粗手指长辈不如 `54dp` 的 `AppPrimaryButton` 好按。
- **高收益低改动优化建议**：
  清理未实际生效的 `AppBackButton`（或统一委托给同一套 48dp 返回图标实现），并将报告页、提醒中心及错误重试的默认 `Button` 替换为公共 `AppPrimaryButton` / `AppSecondaryButton`。

---

#### UX-26 · [P3] 底部悬浮 Dock 向下滚动收起时停靠左下角，右手单手触达性偏弱

- **所属页面 / 组件**：底部悬浮导航 `AppBottomDock`
- **源码位置**：[AppBottomDock.kt](file:///e:/blood%20pressure%20record/app/src/main/java/com/example/bloodpressurerecord/ui/common/AppBottomDock.kt#L104-L149)
- **属性标签**：`P3` · `潜在风险` · `需实机运行验证` · `交互体验 / 手势人体工学`
- **问题依据**：
  `buttonTravelPx = (CompactDockSize / 2 - maxWidth / 2).toPx()` 为负向偏移：当用户在历史或趋势长页面向下滑动超过 `28.dp` 时，四个导航 Tab 隐藏，中央「+」按钮向左平移停靠在**屏幕左下角**。对于绝大多数右手单手持机的大屏手机用户，左下角是拇指最难够到的死角（标准 Android Extended FAB 收起通常停靠右下角或居中）；此外若页面滚到底部后想直接切去别的 Tab，必须先反向回滑 `18.dp` 展开胶囊。
- **用户影响**：右手单手浏览长历史后点「+」距离较远；页面底部直接切 Tab 多一次回滑动作。
- **高收益低改动优化建议**（主观建议，非缺陷）：
  实机评估是否有遮挡图表左轴或卡片文字的考量（左轴文字在上方，左下角主要遮挡列表文字左端，而中文列表左对齐文字恰集中在左侧，**停靠右下角反而更少遮挡左对齐正文且符合右手拇指热区**！若改停靠右下角仅需将符号改为 `+ (maxWidth / 2 - CompactDockSize / 2)` 并同步调整 `x` 起点）。

---

#### UX-27 · [P3] 加密备份与导入的口令输入框缺少“明文可见切换”按钮

- **所属页面 / 组件**：数据管理 `SettingsDataManagementScreen`（设置备份口令 / 输入备份口令弹窗）
- **源码位置**：[SettingsDataManagementScreen.kt](file:///e:/blood%20pressure%20record/app/src/main/java/com/example/bloodpressurerecord/ui/settings/SettingsDataManagementScreen.kt#L111-L133)、[SettingsDataManagementScreen.kt](file:///e:/blood%20pressure%20record/app/src/main/java/com/example/bloodpressurerecord/ui/settings/SettingsDataManagementScreen.kt#L192-L202)
- **属性标签**：`P3` · `主观设计建议` · `源码静态可验证` · `适老化体验`
- **问题依据**：
  导出加密 `.bpx` 备份要求至少 8 位口令且“口令不会保存在设备上，遗失后无法恢复数据”，但两个口令框和导入解密口令框均写死 `visualTransformation = PasswordVisualTransformation()`，没有 `trailingIcon` 切换显示/隐藏明文，也未声明 `KeyboardType.Password`。
- **用户影响**：老年人盲打两遍 8 位以上字符极易按错，或自以为输的是 A 实际两次都误触成 B，换机恢复时可能因看不见输入内容而反复失败。
- **高收益低改动优化建议**：
  为口令 `OutlinedTextField` 增加标准的 `Visibility / VisibilityOff` 眼睛图标切换，并补上 `KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false)` 防止输入法自动联想篡改口令。

---

#### UX-28 · [P3] 仓库存在未引用的冗余字体/死代码资源，且应用内 `2.3.9` 更新说明与实际版本错位

- **所属页面 / 组件**：资源目录 `res/font`、`strings.xml`、`HistoryScreen`、`AppInfoContent`
- **源码位置**：`app/src/main/res/font/caprasimo_regular.ttf`、[strings.xml](file:///e:/blood%20pressure%20record/app/src/main/res/values/strings.xml#L44)、[HistoryScreen.kt](file:///e:/blood%20pressure%20record/app/src/main/java/com/example/bloodpressurerecord/ui/history/HistoryScreen.kt#L450-L472)、[HistoryScreen.kt](file:///e:/blood%20pressure%20record/app/src/main/java/com/example/bloodpressurerecord/ui/history/HistoryScreen.kt#L939)、[AppInfoContent.kt](file:///e:/blood%20pressure%20record/app/src/main/java/com/example/bloodpressurerecord/ui/settings/AppInfoContent.kt#L112-L119)
- **属性标签**：`P3` · `客观缺陷` · `源码静态可验证` · `工程整洁度 / 信息准确性`
- **问题依据**：
  1. `res/font/caprasimo_regular.ttf`（早期视觉候选字体）、`R.string.bp_range_edit_hint`、`HistoryScreen.MonthEncouragementBar()` 私有函数及 `HistorySessionCard` 的 `showDate` 参数均无任何有效调用点（死资源增加 APK 体积与维护迷惑）。
  2. `AppReleaseNotes` 首条直接使用动态常量 `version = AppInfoContent.CURRENT_VERSION`（当前构建为 `2.3.9`），但正文写的却是 `2.3.8` 的“香港直连镜像”更新内容（如 [audit-2026-11-24.md](file:///e:/blood%20pressure%20record/docs/audit-2026-11-24.md#L43) 亦提及），导致用户升级到 `2.3.9` 后在「更新说明」里看到错误的本版改动且缺少 `2.3.8` 独立条目。
- **高收益低改动优化建议**：
  移除未引用的 `caprasimo_regular.ttf`（及核对许可证文件）与死代码参数；`AppReleaseNotes` 改为显式填写字面版本号 `"2.3.8"` 并补充真实的 `"2.3.9"` 条目。

---

## 四、逐页面、公共组件与核心流程全景分析

### 4.1 全局主题、导航与公共组件层（`ui/theme` · `navigation` · `ui/common`）

- **现状表现**：
  - 色彩架构（`Color.kt`、`Theme.kt`）围绕珊瑚橙与鼠尾草绿构建了完整的浅色与深色暖棕表面阶梯；转场动画（`AppNavigation.kt`）区分平级 Tab 淡入淡出与父子层级视差推入，接入了 Android 13+ 预测式返回；安全区（`AppInsets.kt`）用 `safeDrawing` 顶部与 `navigationBars` 底部彻底消除了刘海屏压标题与底栏双倍留白问题。
  - 滚轮时间选择器（`WheelTimePicker.kt`）支持滚动居中吸附与无障碍点选定位，行高随字号自适应（`≥48.dp`）。
- **现存问题关联**：`UX-06`（编辑回退清栈）、`UX-10`（数字框描边过淡）、`UX-11`（大字叠乘与固定高按钮）、`UX-14`（5 套色盘与 XML 首帧底色）、`UX-25`（双套返回按钮）、`UX-26`（Dock 左下角收起）。
- **微调补充观察**：`NoteSearchScreen:97-113` 将带浮动 `label` 的 `OutlinedTextField` 塞进 `AppTopBar`（`min = 68.dp`），在超大字号下输入框高度膨胀易使顶栏显得局促，改用 `placeholder` 替代浮动 `label` 会更舒展。

### 4.2 首页看板（`DashboardScreen` · `BpRangeBar` · `Buddy`）

- **现状表现**：
  - 页面自上而下依次呈现「日期问候 → 最近读数主卡（含小压对话气泡 + 超大血压值 + 四段区间条 + 高风险红底警示）→ 今天的测量（早/晚双卡）→ 今日服药打卡（支持 Snackbar 撤销与防连点互斥锁）→ 这一周（连续天数 + 7 日打卡格 + 收缩压柱图）」，首屏核心价值传达直接、亲切。
  - `HeroPressureValue` 通过 `TextMeasurer` 实时测量宽度自动缩放超大血压字号，彻底杜绝了 `188/118` 在窄屏上断行或截断。
- **现存问题关联**：`UX-01`（**P0 区间条单轴偏低误判为正常**）、`UX-03`（**P1 主卡缺六级状态芯片**）、`UX-10`（`mmHg` 与组数文字透明度稀释致对比度不足）、`UX-11`（空状态按钮固定 52dp 高）、`UX-12`（早晚 12 点切分与周日开头口径差异）、`UX-18`（大卡与已测早晚卡不可点进详情，`onViewTodayRecords` 闲置）。
- **微小细节**：首页同时维护了页面私有 `SnackbarHost`（第 169 行）与全局根 `SnackbarHost`（`AppNavigation:176`），两套提示容器并存虽不冲突，但未来可收敛复用。

### 4.3 血压录入与编辑链路（`AddMeasurementScreen` · `EditSessionScreen` · `SessionFormComponents`）

- **现状表现**：
  - 默认展示两组必填读数，免除此前冗余的“静坐引导页”和“手动选场景/预设标签”，自动按时间识别“凌晨/清晨/上午/下午/晚上”，支持异常范围二次确认与高风险提醒，并实现了跨进程持久化的 `SessionDraftRepository` 草稿保护，核心记数效率极高。
- **现存问题关联**：`UX-06`（编辑保存跳回首页）、`UX-08`（编辑页丢失旧 `note` 字段）、`UX-09`（新增与编辑多组布局及高风险规则割裂）、`UX-10`（数字框未聚焦边框过淡）、`UX-11`（保存按钮固定 `58.dp` 高）、`UX-19`（补充情况缺显式回看读数按钮、完成弹窗 5 秒静默关闭且无分级）、`UX-20`（跨天草稿恢复提示沉底且沿用旧时间戳）。
- **适老化体验点**：`CompactNumberField` 将第 1 组、第 2 组的脉搏 `imeAction` 都设为 `ImeAction.Done`（第 302 行）。用户在填完第 1 组脉搏后按键盘右下角“完成”会直接收起键盘，而非跳到第 2 组高压（`ImeAction.Next`）——将非最后一组脉搏的 `imeAction` 设为 `ImeAction.Next` 可让用户一气呵成连输两组读数无需二次点按屏幕！

### 4.4 历史记录、备注搜索与详情页（`HistoryScreen` · `NoteSearchScreen` · `HistoryDetailScreen`）

- **现状表现**：
  - 「日历 / 近期」双模式兼顾按月全局扫视与按周/月汇总查阅；月历单元格同时编码了底色分级、测量次数圆点（1–3 点）、备注圆点与高风险感叹号 `!`，并配备自动换行的 `CalendarLegend` 图例和极完善的无障碍逐日朗读文本；备注搜索支持 250ms 防抖、代理对安全裁剪与关键词高亮，点击结果精准滚动定位并高亮对应卡片。
- **现存问题关联**：`UX-04`（**P1 详情页删除撤销提示条被系统导航栏遮挡 + 首帧闪现“未找到记录”**）、`UX-12`（周一开头与首页周日开头不一）、`UX-14`（独立硬编码 `historyGradeColors`）。
- **交互细节观察**：月历中“双击带备注日期自动滚到备注卡片”属于进阶隐藏彩蛋，普通单击同样会选中该日期并在下方列出卡片，不影响基础可用性；但月历图例中「有备注」（珊瑚橙圆点）与「2级/3级」（浅珊瑚橙圆点）在缩略图例上色相接近，可考虑给备注标记换用小书签/小圆环以进一步拉开识别度。

### 4.5 趋势分析与横屏全屏详情（`TrendScreen` · `TrendFullscreenScreen` · `TrendChart`）

- **现状表现**：
  - 完成了从基础折线图到专业健康图表的跨越：支持 `7/30/180/365天/全部` × `每次测量/每日平均` 自由组合并记忆粒度偏好；主血压图与脉搏副图共享绝对时间视窗；手势状态机妥善处理了竖屏滚动让渡、双指中心缩放、单指平移、单击热区就近命中、长按 400ms 震动吸附十字线与双击复位；横屏全屏自适应宽屏侧边明细抽屉。
- **现存问题关联**：`UX-10`（深色模式高风险圆环对比度仅 `2.46:1`）、`UX-13`（指标色与 PDF 反转、脉搏与高压同色）、`UX-15`（双指标缺图例、参考线与目标线无法区分、选中读数缺分级）、`UX-16`（空数据时重复渲染空卡片、深色圆环底色刺眼）、`UX-23`（根布局缺背景色）。
- **横屏细节观察**：`TrendFullscreenToolbar` 在小屏或大字横屏下将左侧操作区设为水平滚动（`horizontalScroll`），而「上一条 / 下一条 / 明细 / 恢复 / 回到最新」5 个高频查数动作被收进了 `MoreVert`（`⋮`）下拉菜单中——每点一次「下一条」都要重新展开一次 `⋮` 菜单（`expanded = false; viewModel.selectNext()`），在横屏连续逐点翻查时操作成本偏高（可在菜单内对「上一条/下一条」保持菜单不关闭，或在工具栏直接露出 `<` `>` 两个紧凑图标按钮）。

### 4.6 我的、提醒中心、数据管理与软件更新（`Settings*` · `BpZoneTargetSheet` · `UpdateUi`）

- **现状表现**：
  - 「我的」主页用卡片化分组 + 折叠面板清晰收纳了资料、目标、提醒、显示、备份与隐私承诺（底部鼠尾草绿盾牌卡“数据只在这台手机上”极大增强了老年用户安全感）。
  - 系统日历同步弹窗明确告知云同步/共享日历可见性风险，默认仅写入“服药提醒”脱敏标题；数据管理支持明文 `.xlsx`、AES-256-GCM 加密 `.bpx`、超 5000 条自动分卷保存与导入前多维勾选预览；软件更新弹窗整窗可滚，确保超大字体下永远能滑到底部点到关闭/跳过按钮。
- **现存问题关联**：`UX-02`（**P0 药品弹窗校验失败即销毁输入**）、`UX-05`（**P1 自定义范围面板外露“待产品确认”且无重置**）、`UX-12`（双套目标概念）、`UX-20`（设置反馈沉底且红绿同色）、`UX-21`（性别自由文本输入、无 `imePadding`、大字开关重复）、`UX-25`（提醒中心混用原生 `Button`）、`UX-27`（备份口令框无明文切换）、`UX-28`（更新说明版本号错位）。

### 4.7 延伸触点：医生报告 PDF、桌面小部件与系统通知

- **医生报告（`ReportOptionsScreen` / `ReportPreviewScreen` / `PdfReportRenderer`）**：
  - 优势：A4 排版专业克制，支持通用报告与 7 日监测双模板，自动处理长备注分页与超界箭头提示，支持分享、存 PDF、系统打印与存 150 DPI 摘要长图。
  - 待改进：`UX-07`（**P1 预览页返回键直接退出整个报告模块**）、`UX-13`（**P2 PDF 趋势图高压蓝/低压橙与端内趋势图完全反转**）、`UX-17`（自定义周期要求手输日期格式、导出按钮深埋底部）、`UX-23`（转场透底）。
- **桌面小部件（`BloodPressureWidget`）**：
  - 优势：响应式三档尺寸（`2×2` / `4×2` / `4×4`）信息递进合理。
  - 待改进：`UX-13`（低压绿色与趋势图不一致）、`UX-22`（无深色适配、单组高风险漏标文字、次级字对比度 `3.84:1`）。
- **系统通知（`ReminderReceiver`）**：
  - 待改进：`UX-24`（通知点击未直达录入页、服药通知无快捷打卡按钮，及既有审计指出的提前打卡未抑制通知问题）。

---

## 五、源码可验证边界与需实机运行验证清单

为严守客观审计原则，以下事项**仅在静态走查中识别出机制性风险或依赖系统/ROM 行为，本次未运行模拟器或真机，绝不虚构通过或失败结论**，建议后续发版前在实体设备重点核验：

| 序号 | 待实机验证项目 | 建议测试条件 | 重点观察指标 |
|---|---|---|---|
| **R-01** | **极端字体叠加排版验证** | 320dp 窄屏模式 + Android 系统字号调至 `200%` + 应用内开启「大字模式（1.15x）」 | 重点检查 `InlineSessionAction`（58dp 保存按钮）、`AppBottomDock`（64dp 胶囊标签与圆点）、`NoteSearchScreen` 顶栏输入框、`TrendFullscreenToolbar` 是否发生文字上下裁切或挤压。 |
| **R-02** | **软键盘（IME）弹出焦点跟随** | 真实手机（含第三方中文输入法如搜狗/讯飞高键盘）在「记一次血压（3组以上）」「补充情况」「用户资料」逐项输入 | 点击第 2 组低压/脉搏及「用户资料」底部目标低压时，输入框与光标是否自动平滑滚入键盘上方可视区，键盘完成/下一项焦点跳转是否顺畅。 |
| **R-03** | **TalkBack 真机逐焦走查** | 开启系统 TalkBack，盲操完成“记一次血压 → 历史日历选日 → 趋势逐点读数 → 滚轮选时间” | 验证 `WheelTimePicker`（24+60 项 LazyColumn）双指滚动与单指划动焦点效率；验证 `BpRangeBar`、`ReadingHero`、`ExpandableSection` 折叠前后焦点顺序是否自然无死循环。 |
| **R-04** | **三键导航 vs 手势导航底栏安全区** | 分别切换系统“经典三键导航”与“全面屏手势导航”（深/浅色各一次） | 重点验证 `HistoryDetailScreen` 删除后的撤销提示条遮挡情况（`UX-04`）、底部悬浮 Dock 与系统手势横条的间距及系统栏图标反色清晰度。 |
| **R-05** | **国产 ROM 中英混排基线与高刷表现** | 小米 HyperOS / 华为 HarmonyOS / OPPO / vivo 默认字体及自定义主题字体 | 检查 `Bricolage Grotesque` 大数字与系统中文字体混排时的垂直基线对齐度，以及趋势图双指缩放/长按拖动在 120Hz 下的跟手度与发热情况。 |
| **R-06** | **大屏 / 折叠屏 / 分屏方向适配** | Android 16 (`targetSdk = 36`) 平板、折叠屏展开态或手机分屏模式进入趋势「全屏」 | 验证大屏忽略 `SENSOR_LANDSCAPE` 方向限制时，`TrendFullscreenScreen` 竖向宽屏与侧边 `320.dp` 明细栏的比例及可读性。 |

---

## 六、分阶段高性价比优化路线图

所有建议均在**不改变本地数据库结构、不推翻现有暖色设计系统、不引入重型第三方库**的前提下制定，按投入产出比分为三期推进：

### 第一阶段：P0 / 高危 P1 紧急止损（预计工作量：1–1.5 天，纯局部代码修复）

1. **修正首页区间条单轴偏低误判（`UX-01`）**：调整 `LocateBpZoneUseCase` 合并顺序，确保单轴 `LOW` + 另一轴 `NORMAL` 准确落入「偏低」区间并正确朗读。
2. **修复药品编辑弹窗丢数据（`UX-02`）**：将药名与服药时间非空校验前移至 `MedicationEditorDialog` 内部，校验不通过时保留弹窗并红字提示，新建默认带入一个可改时间（如 `08:00`）。
3. **补齐首页主卡分级芯片 & 修复详情页撤销遮挡与闪屏（`UX-03`、`UX-04`）**：在 `ReadingHero` 增加 `GradeChip`；为 `HistoryDetailScreen` 的 `SnackbarHost` 补上 `pageContentBottomPadding()` 并增加 `loading` 初态。
4. **替换内部占位文案 & 修复两处返回栈断裂（`UX-05`、`UX-06`、`UX-07`）**：更新 `bp_zone_config_placeholder_note` 正式文案并加「恢复默认」按钮；`HistoryEdit` 保存后改为 `popBackStack()` 回到详情页；`ReportPreviewScreen` 增加 `BackHandler` 退回报告周期选项页。
5. **修复核心对比度硬伤与转场透底（`UX-10`、`UX-23`）**：提高 `CompactNumberField` 未聚焦边框对比度、深色趋势图高风险圆环改取 `scheme.error`、去掉首页 `mmHg` 额外透明度衰减；为趋势页与报告页根容器补上 `background` 色。

### 第二阶段：表单统一、健康图表可读性与适老化加固（预计工作量：2–3 天）

1. **对齐新增与编辑表单体验（`UX-08`、`UX-09`、`UX-19`、`UX-20`）**：将编辑页改为与新增页一致的纵向多组平铺，接入 `highRiskAlertEnabled` 开关，对非空历史 `note` 开放编辑框；补充情况页增加“返回读数”按钮；草稿恢复提示移至页首并支持一键刷新为当前时间；前序组脉搏 `ImeAction` 改为 `Next`。
2. **修复极端大字裁切风险（`UX-11`）**：将保存按钮、空状态按钮、加一组按钮的 `.height(...)` 全部改为 `.heightIn(min = ...)`，并为合成 `fontScale` 设定合理安全上限。
3. **统一健康视觉语义与图表图例（`UX-12`、`UX-13`、`UX-14`、`UX-15`、`UX-16`）**：收敛六级血压语义色 Token，同步端内趋势图、小部件与 PDF 报告的高/低压颜色；趋势图补充双指标图例、区分参考线与目标线线型、读数栏补充分级芯片、简化空数据态重复卡片；消除“双目标”命名歧义与自然周起始日差异。
4. **消灭手打文本痛点与激活首页穿透（`UX-17`、`UX-18`、`UX-21`）**：报告自定义日期改用 `DatePickerDialog`；个人资料「性别」改为点选胶囊并补 `imePadding()`；绑定首页主卡与已测早晚卡点击进入记录详情。

### 第三阶段：延伸触点升级与精细打磨（按需迭代）

1. **桌面小部件与系统通知增强（`UX-22`、`UX-24`）**：小部件接入深浅色 `ColorProvider` 并补全高风险文字后缀；测量提醒通知支持直达录入页，服药通知增加通知栏快捷打卡 Action。
2. **交互细节与工程清理（`UX-25`–`UX-28`）**：统一全局按钮组件与返回图标，为备份口令框增加显隐眼睛按钮，优化横屏趋势逐条翻查操作，清理未引用字体/死代码并对齐版本更新说明，最后按第五章清单完成一轮真机无障碍与大字验收。
