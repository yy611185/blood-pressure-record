# Blood Pressure Record 最终设计系统规范（Design System Standard — Final）

- **项目名称**：Blood Pressure Record（家庭血压记录）
- **应用标识 / 源码包名**：`applicationId = com.yang.bloodpressure` / 源码包 `com.example.bloodpressurerecord`
- **基线版本**：`v2.3.13`（`versionCode 54` · `minSdk 26` / `targetSdk 36`）
- **生效状态**：**最终确认版（经七轮产品与设计决策锁定，已消除原始四份报告间的全部数值偏差与方案冲突）**
- **配套交付文档**：`DESIGN-DECISION-RECORD.md`、`OPTIMIZATION-ROADMAP-FINAL.md`、`VISUAL-VERIFICATION-CHECKLIST.md`

---

## 一、设计定位与规范治理原则

### 1.1 核心设计气质（Design Principles）

Blood Pressure Record 面向长期居家监测血压的中老年及慢病家庭用户，定位为**“零登录、本机离线优先、温暖纸本、医学严谨的个人血压记录本”**。本规范在完整保留“暖宣纸底 + 奶白卡片 + 陶土红操作 + 鼠尾草绿健康色”品牌资产的前提下，确立四项工程约束：

1. **温和克制（Calm & Reassuring）**：以暖纸本视觉降低“白大褂焦虑”，同时确保高风险血压（`≥180/120 mmHg`）的临床警示不可削弱。
2. **效率优先（Efficient & High-Legibility）**：不为形式统一牺牲高频测量录入宽度、阅读层级与成熟手感；优先保证数字、单位、状态一瞥即读。
3. **适老包容（Elderly-Friendly & Accessible）**：全面满足 App 内触控热区 `≥ 48dp × 48dp`、交互控件边界对比度 `≥ 3.0:1`、正文对比度 `≥ 4.5:1`、最高 `2.0x` 有效字体缩放不裁切，以及减弱动态效果下的瞬时按压反馈。
4. **数据真实与隐私透明（Clinically Honest & Privacy-First）**：统计图表严格保持比例真实性，绝不人为拉伸占比面积；明确区分指南医学分级与个人参考区间；隐私状态显性透明、默认保护。

### 1.2 规范状态标记定义

| 状态标记 | 含义说明 |
| :--- | :--- |
| **`[源码保留]`** | 经七轮核验确认的真实源码实现（以 `app/src/main/java/com/example/bloodpressurerecord/` 为唯一事实基线） |
| **`[已批准调整]`** | 经产品负责人明确批准的修正或新增规格（附决策编号与 `AUD` 编号） |
| **`[暂定 · 待真机冻结]`** | 已批准方向但具体数值/色值须经真机实测验收后最终冻结的参数 |

---

## 二、Design Tokens 治理准则

1. **`[源码保留]` 集中式主题分发**：所有页面级 Composable 通过 `MaterialTheme`、`LocalBpGradePalette.current`、`LocalBpRangeColors.current`、`AppSpacing`、`AppDimensions`、`AppMotion` 获取参数。
2. **`[已批准调整]` 禁止业务组件嗅探背景色与硬编码边界色（`D1-2 / AUD-02`）**：禁止在业务组件内使用 `colors.background.red < 0.5f` 或内联硬编码边框色；深浅色语义差异统一收敛至 `Theme.kt` 或 `CompositionLocal` 调色板。
3. **`[已批准调整]` 克制清理真实死常量（`D2-4 / AUD-30`）**：
   - 删除 `Color.kt` 中零外部引用的重复/死常量（如与 `WarmTextMuted` 同值的 `WarmTextFaint` 及其他经静态扫描确认 0 引用的孤立色值）；**保留实际在用的 `Terracotta300`**（`DarkColors.tertiary`）。
   - 将悬空的 `AppDimensions.primaryButtonHeight` 修正为 **`54.dp`**（同步修正注释）并绑定至 `AppPrimaryButton`、`AppSecondaryButton`、`AppDangerButton`；保留 `saveButtonHeight = 58.dp`。
   - **严禁**为机械满足 4dp 网格而批量改动 `cardPadding = 18.dp`、`RoundIconBadge = 46.dp`、`calendarDaySize = 38.dp`、`Buddy (82×78 / 54.dp)` 等成熟视觉尺寸。
4. **`[已批准调整]` 文档版本同步（`D2-4 / AUD-30`）**：实施收口时同步更新根目录 `DESIGN.md` 与 `PRODUCT.md`，使版本号（`2.3.13 / 54`）与实际实现保持一致。

---

## 三、色彩体系规范（Color System）

### 3.1 品牌色、表面色与边界色令牌（严格对齐 `Color.kt` & `Theme.kt` 源码基线）

| 语义角色（`MaterialTheme.colorScheme`） | 浅色模式（Light） | 深色模式（Dark） | 状态标记与说明 |
| :--- | :--- | :--- | :--- |
| `background`（全局暖纸底） | `WarmBackground` **`#FAF6EF`** | **`#1A1512`** | **`[源码保留]`**（`D2-1` 纠正原 03 报告 `#171412` 笔误） |
| `surface`（标准奶白卡片底） | `WarmSurface` **`#FFFDFA`** | `WarmNeutral900` **`#26201C`** | **`[源码保留]`**（`D2-1` 纠正原 03 报告 `#221E1B` 笔误） |
| `surfaceVariant`（次级容器/分段槽） | `WarmSurfaceSoft` **`#F2ECE3`** | **`#2D2823`** | **`[源码保留]`** |
| `surfaceContainerLow / Container / Highest` | `WarmSurfaceSoft` **`#F2ECE3`** | **`#2D2823`** | **`[源码保留]`**（`surfaceContainerHigh` 浅 `#E9E2D7` / 深 `#38312C`） |
| `primary`（品牌主操作陶土红） | `TerracottaAction` **`#B94B39`** | `Terracotta400` **`#FFA997`** | **`[源码保留]`** |
| `onPrimary` | `OnTerracotta` **`#FFFFFF`** | `Terracotta900` **`#40201B`** | **`[源码保留]`** |
| `primaryContainer` | `Terracotta200` **`#FFDED5`** | `Terracotta800` **`#532B24`** | **`[源码保留]`**（`D2-1` 纠正原 03 报告 `#FCE5DF` 笔误） |
| `onPrimaryContainer` | `Terracotta700` **`#963828`** | `Terracotta200` **`#FFDED5`** | **`[源码保留]`** |
| `secondary`（健康鼠尾草绿） | `Sage600` **`#357D58`** | `Sage400` **`#AEBF92`** | **`[源码保留]`** |
| `onSecondary` | `Sage100` **`#F0FAE1`** | `Sage900` **`#272E1B`** | **`[源码保留]`** |
| `secondaryContainer` | `Sage200` **`#CCF3D8`** | `Sage800` **`#1B412A`** | **`[源码保留]`**（`D2-1` 纠正原 03 报告 `#DFF2E7` 笔误） |
| `onSecondaryContainer` | `Sage700` **`#006436`** | `Sage200` **`#CCF3D8`** | **`[源码保留]`**（禁止用于高风险警告图标底座，`D6-1`） |
| `tertiary` / `onTertiary` | `Terracotta400` **`#FFA997`** / `Terracotta900` **`#40201B`** | `Terracotta300` **`#FFC6A5`** / `Terracotta900` **`#40201B`** | **`[源码保留]`** |
| `tertiaryContainer` / `onTertiaryContainer` | **`#CAEDFF`** / **`#02578B`** | **`#1A3B55`** / **`#8BD2FF`** | **`[源码保留]`**（`onTertiaryContainer` 同时用于舒张压蓝线） |
| `onBackground` / `onSurface` | `WarmText` **`#27201B`** | **`#F2EEE7`** | **`[源码保留]`**（`D2-1` 纠正原 03 报告 `#231F1B` 笔误） |
| `onSurfaceVariant`（次级正文） | `WarmTextBody` **`#645C55`** | **`#B6B0A9`** | **`[源码保留]`**（`D2-1` 纠正原 03 报告 `#675F56` 笔误） |
| **`outline`（交互控件结构边框）** | **`WarmOutline` `#8A8177`** | **`#7F7973`**（保留） | **`[已批准调整]`（`D1-2 / AUD-02`）**<br/>浅色相对 `#FFFDFA` 为 `3.77:1`、相对 `#FAF6EF` 为 `3.55:1`、相对 `#F2ECE3` 为 `3.26:1`；深色 `#7F7973` 已达 `3.74:1` 保持不变 |
| `outlineVariant`（弱装饰分割线） | `WarmDivider` **`#E3DDD5`** | **`#3A342F`** | **`[源码保留]`** 专供非交互细分割线 |
| `error` / `onError` | `WarmError` **`#B3261E`** / **`#FFF7EF`** | **`#FFB4AB`** / **`#690005`** | **`[源码保留]`** |
| `errorContainer` / `onErrorContainer` | `WarmErrorContainer` **`#F9DEDC`** / **`#410E0B`** | **`#93000A`** / **`#FFDAD6`** | **`[源码保留]`** |

### 3.2 六级血压语义调色板（`BpGradePalette` · 对齐 `DesignSystem.kt#L178-L199`）

> 医学分级色仅编码《中国高血压防治指南》六档分级，**必须始终配合文字标签呈现**。深色模式 `soft` 沿用 `ink.copy(alpha = 0.24f)`，`ink` 除 `low` 单独提亮外其余沿用源码。

| 分级键值 | 浅色 `soft / mid / ink / body` | 深色 `ink`（前景色） / `mid`（圆点色） | 状态标记 |
| :--- | :--- | :--- | :--- |
| **`low`（偏低）** | `#E9E9FA / #8E8BC8 / #55509B / #9F9CD1` | **`#A3A0DE`**（提亮，约 `5.62:1`） / `#8E8BC8` | **`[已批准调整]`（`D1-2 / AUD-26`）** |
| `normal`（正常） | `#DFF4E9 / #69C49D / #287958 / #85CBA9` | `#69C49D` / `#69C49D` | **`[源码保留]`** |
| `highNormal`（正常高值） | `#EEF3D7 / #A5BF65 / #61742E / #B8CE7B` | `#A5BF65` / `#A5BF65` | **`[源码保留]`** |
| `stage1`（1级高血压） | `#F7F0CF / #D9B55E / #806625 / #E5C470` | `#D9B55E` / `#D9B55E` | **`[源码保留]`** |
| `stage2`（2级高血压） | `#FBE5D7 / #EAA27E / #9F5335 / #EEB08C` | `#EAA27E` / `#EAA27E` | **`[源码保留]`** |
| `stage3`（3级高血压） | `#FADDD9 / #E58178 / #A03F39 / #E88E84` | `#E58178` / `#E58178` | **`[源码保留]`** |

**微色块描边与色条衬底规则（`D4-3 / D5-1`）**：
- **日历图例（`AUD-12`）**：`CalendarLegend` 中 6 档 `.soft` 微色块外加 `1.2.dp` 对应分级 `.mid`（或 `.ink`）描边，消除白卡 `1.12:1` 溶解。
- **首页四色条（`AUD-26`）**：`BpRangeBar` 轨道外层包裹与 `GradeChip(elevatedOnSoft = true)` 同系的柔和 `surface.copy(alpha = 0.78f)` 细衬底（保留 `LightBpRangeColors / DarkBpRangeColors` 现有色值），防止在同色系 `ReadingHero` `.soft` 底色上融底。

### 3.3 数据可视化与中性标记色彩规范

| 语义角色 | 浅色模式 | 深色模式 | 语义与使用边界 | 状态标记 |
| :--- | :--- | :--- | :--- | :--- |
| `chartSystolic`（收缩压/高压） | `colorScheme.primary` (`#B94B39`) | `colorScheme.primary` (`#FFA997`) | 血压趋势高压折线与节点 | **`[源码保留]`** |
| `chartDiastolic`（舒张压/低压） | `colorScheme.onTertiaryContainer` (`#02578B`) | `colorScheme.onTertiaryContainer` (`#8BD2FF`) | 血压趋势低压折线与节点 | **`[源码保留]`** |
| **`chartPulse`（脉搏/心率）** | **候选 `#2E7D7B`** | **候选 `#80CBC4`** | 脉搏趋势专色；严禁复用收缩压陶土红，亦不直接绑定代表健康达标的 `secondary` 绿 | **`[暂定 · 待真机冻结]`（`D5-4 / AUD-11`）** |
| **`noteNeutral`（备注中性标记）** | 圆点 `onSurfaceVariant` (`#645C55`)<br/>框底 `surfaceVariant` (`#F2ECE3`) | 圆点 `onSurfaceVariant` (`#B6B0A9`)<br/>框底 `surfaceVariant` (`#2D2823`) | 日历“有备注”点与历史卡片备注引用框；严禁使用 `primary` 红或 `primaryContainer` 珊瑚粉与警示色争夺权重 | **`[已批准调整]`（`D5-1 / AUD-12`）** |

### 3.4 深浅色平滑过渡规范（`D1-5 / AUD-15`）

- **`[已批准调整]`**：`Theme.kt` 中 `animateColorScheme` 补齐 `secondary`、`onSecondary`、`secondaryContainer`、`onSecondaryContainer`、`tertiary`、`onTertiary`、`tertiaryContainer`、`onTertiaryContainer`、`error`、`onError`、`errorContainer`、`onErrorContainer`、`outline`、`outlineVariant` 的全通道动画。
- 严格沿用现有 `AppMotion.colorThemeSpec(reducedMotion)`（**`200ms` `SmoothOutEasing`**；`reducedMotion == true` 时 `0ms` 直接返回目标方案）。

---

## 四、字体与排版规范（Typography & Numeric System）

### 4.1 双字体分工策略

1. **`[源码保留]` 展示型数字字体（`NumberFontFamily`）**：离线打包 `Bricolage Grotesque`（Regular / SemiBold / Bold）+ `fontFeatureSettings = "tnum"`（等宽数字），用于核心读数、统计数值与日期/时间数字展示。
2. **`[已批准调整]` 补齐漏用数字字体的统计卡（`D5-3 / AUD-17`）**：`HistoryScreen` 的 `RecentSummaryCard` 与 `SessionFormComponents` 的 `SessionAverageCard` 全部核心数值统一应用 `NumberFontFamily` + `tabularNums()`。
3. **`[源码保留]` 中文与可编辑输入框字体**：中文正文与标题采用系统默认无衬线字体（`FontFamily.Default`、`letterSpacing = 0.sp`）；可编辑输入框保持系统字体 + `tnum` 确保光标度量稳定。

### 4.2 完整 15 级 `AppTypography` 字阶表（保留源码 10 级 + 补全缺失 5 级）

| M3 样式槽位 | 字号 / 行高 | 字重 / 字体 | 字距 | 典型用途 | 状态标记 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **`displayLarge`** | **`58.sp / 62.sp`** | Bold (`NumberFontFamily` + `tnum`) | `-1.sp` | 首页 `ReadingHero` 核心血压大数字（收敛现状 `58sp/62sp`） | **`[已批准调整 · 补齐]`（`D2-3 / AUD-18`）** |
| `displayMedium` | `54.sp / 54.sp` | Bold (`NumberFontFamily` + `tnum`) | `-1.sp` | 展示大数字（保留源码既有定义） | **`[源码保留]`** |
| **`displaySmall`** | **`34.sp / 40.sp`** | Bold (`NumberFontFamily` + `tnum`) | `0.sp` | 记录详情页主读数（校验 `HistoryDetailScreen.kt#L154` 既有调用点） | **`[已批准调整 · 补齐]`（`D2-3 / AUD-18`）** |
| `headlineLarge` | `30.sp / 38.sp` | Bold (`Default`) | `0.sp` | 一级页面主标题与首页问候语（修正首页 `29.sp` 为标准 `30.sp`，`D3-2`） | **`[源码保留]`** |
| `headlineMedium` | `30.sp / 38.sp` | Bold (`Default`) | `0.sp` | 一级工具页标题（保留源码既有定义） | **`[源码保留]`** |
| **`headlineSmall`** | **`24.sp / 32.sp`** | Bold (`Default`) | `0.sp` | 历史卡片读数、均值卡、更新弹窗主标题（校验 3 处既有调用点） | **`[已批准调整 · 补齐]`（`D2-3 / AUD-18`）** |
| `titleLarge` | `22.sp / 28.sp` | SemiBold (`Default`) | `0.sp` | 子页顶栏标题、卡片主标题 | **`[源码保留]`** |
| `titleMedium` | `17.sp / 24.sp` | SemiBold (`Default`) | `0.sp` | 分组标题、卡片条目标题、时间列 | **`[源码保留]`** |
| **`titleSmall`** | **`15.sp / 21.sp`** | SemiBold (`Default`) | `0.sp` | 选中日摘要标题、搜索结果日期等（校验 5 处既有调用点） | **`[已批准调整 · 补齐]`（`D2-3 / AUD-18`）** |
| `bodyLarge` | `16.sp / 24.sp` | Normal (`Default`) | `0.sp` | 正文、设置项主标题、输入框正文 | **`[源码保留]`** |
| `bodyMedium` | `14.sp / 21.sp` | Normal (`Default`) | `0.sp` | 常规说明、小压气泡文案、列表正文 | **`[源码保留]`** |
| `bodySmall` | `12.sp / 17.sp` | Normal (`Default`) | `0.sp` | 辅助注释、副标题、页脚说明 | **`[源码保留]`** |
| `labelLarge` | `14.sp / 20.sp` | Medium (`Default`) | `0.sp` | 次级按钮、表单标签 | **`[源码保留]`** |
| `labelMedium` | `13.sp / 18.sp` | Medium (`Default`) | `0.sp` | 状态胶囊、紧凑分段器、读数操作项 | **`[源码保留]`** |
| **`labelSmall`** | **`12.sp / 16.sp`** | Medium (`Default`) | **`0.sp`** | 辅助微标签、四色条标签、列表次级信息（消除 M3 默认 `11.sp + 0.5.sp` 英文松散字距，须逐一核对 19 处既有调用点） | **`[已批准调整 · 补齐]`（`D2-3 / AUD-18`）** |

**最小字号分级保底与行高规范（`D2-3 / AUD-18`）**：
- 消除界面非必要 `10.sp` 微缩文字；常规辅助文字、单位与标签优先采用 **`≥ 12.sp`**；
- **合理例外**：底部悬浮 Dock 导航标签、密集图表坐标刻度及紧凑桌面小部件在空间受限时允许使用 **`11.sp` 兜底**，但不得妨碍用户正常的系统/应用字体缩放能力；
- `CompactNumberField` 须显式声明与动态字号匹配的 `lineHeight`，避免大字下上下缘紧贴。

### 4.3 字体缩放与冷启动防闪跳规范

- **`[源码保留]` 双层缩放与 `2.0x` 钳制**：应用大字模式（`1.15x`）与系统字号相乘并钳制于 `MAX_EFFECTIVE_FONT_SCALE = 2.0f`。
- **`[已批准调整]` 冷启动异步首帧门控（`D1-5 / AUD-23`）**：`MainActivity` 在首帧渲染主树前通过异步读取 + 轻量首帧门控等待用户真实 `isLargeTextEnabled` 与 `appearanceMode` 就绪，避免主线程同步阻塞与冷启动字号/主题闪跳。
- **`[已批准调整]` 禁止窄文本列固定死宽（`D5-2 / AUD-16`）**：历史卡片时间列等改用 `Modifier.widthIn(min = 54.dp)`，防止 `2.0x` 缩放下断行。

---

## 五、间距、网格与页面布局规范（Spacing & Layout）

### 5.1 4dp 主网格与书面例外白名单（`D2-5 / AUD-24`）

- **`[源码保留]` 基础间距令牌（`AppSpacing`）**：`xSmall = 4.dp`、`small = 8.dp`、`medium = 12.dp`、`large = 16.dp`、`xLarge = 24.dp`、`xxLarge = 32.dp`；标准页面水平安全边距 `AppDimensions.pageHorizontalPadding = 20.dp`。
- **新组件纪律**：新增组件优先选用标准 `AppSpacing` 令牌，不随意引入无理由的奇数间距（如 `5 / 9 / 11 / 13 / 15.dp`）。
- **明确批准的光学、结构与存量例外清单（不得机械消除）**：
  1. **大圆角卡片上方分组标题 `+2.dp` 光学缩进**（补偿大圆角左上弧线视觉退让）；
  2. **底部悬浮 `AppBottomDock` 左右外边距 `16.dp`**（略宽于 `20.dp` 页面栅格以传达悬浮层级感）；
  3. **有明确视觉/基线理由的 `2.dp / 6.dp` 微调**（如 `ReadingHero` 内外 `14dp + 6dp = 20dp` 对齐补偿）；
  4. **成熟存量组件尺寸保持不变**：`cardPadding = 18.dp`、`RoundIconBadge = 46.dp`、`calendarDaySize = 38.dp`、`Buddy (82×78 / 54.dp)`、`StatusChip` 内距。
- **`[已批准调整]` 报告预览页水平边距对齐（`D6-3 / AUD-24`）**：`ReportPreviewScreen` 水平边距由 `16.dp` 统一为 `AppDimensions.pageHorizontalPadding (20.dp)`，同时保留 PDF 画布合理缩放与可读性。

### 5.2 一级页面顶栏与滚动策略规范（`D3-2 / AUD-13`、`D3-3 / AUD-24`）

| 一级页面 | 顶栏组件与高度 | 标题字阶 | 滚动行为 | 右侧操作按钮 |
| :--- | :--- | :--- | :--- | :--- |
| **首页（Dashboard）** | **独立双行情感问候头**（日期 `13sp` + 问候语） | `headlineLarge (30.sp)`（修正 `29.sp`） | 随内容自然滚出 | 无 |
| **历史（History）** | `AppTopBar`（**保留 `68.dp`**） | `headlineLarge (30.sp)` | **保留 `hideOnScroll`**（便随时召回搜索） | 柔和浅底圆角按钮（搜索） |
| **趋势（Trend）** | `AppTopBar`（**统一 `68.dp`**，消除空态跳动） | `headlineLarge (30.sp)` | 随内容自然滚出 | 柔和浅底圆角按钮（全屏） |
| **我的（Settings）** | `AppTopBar`（**保留 `68.dp`**，移除额外 `16.dp` 顶距） | `headlineLarge (30.sp)` | 随内容自然滚出 | 无 |

- **顶栏右侧操作统一（`D3-3`）**：封装与左侧 `AppBackIconButton` 同系的柔和浅底圆角公共组件（`surfaceContainerHighest`、`16.dp` 圆角、有效触控 `≥ 48.dp`、无多余阴影或高饱和色块），可见底座右缘精准对齐 `20.dp` 页面右轴线；**禁止硬编码 `offset`**。

### 5.3 卡片左对齐轴守恒（`D4-1 / AUD-06`）

- 卡片内局部可点击区域不得因添加内部波纹呼吸内距而破坏全卡左右对齐轴线；首页 `ReadingHero` 采用**外层水平 `14.dp` + 非点击兄弟行补 `6.dp` + 可点击读数区内部 `6.dp`**，使全部可见元素严格共线于 `20.dp` 内部轴线。

---

## 六、圆角与同心几何规范（Corner Radius & Concentricity）

### 6.1 单调递增的 `AppShapes` 阶梯（`D2-2 / AUD-07`）

| `AppShapes` 槽位 | 源码旧值 | **最终规范值** | 绑定组件范围 | 状态标记 |
| :--- | :---: | :---: | :--- | :--- |
| `extraSmall` | `12.dp` | **`12.dp`** | 内嵌微块、小型提示、服药行内层圆角 | **`[源码保留]`** |
| `small` | `16.dp` | **`16.dp`** | **全部普通文本输入框（`OutlinedTextField`）、备注搜索框**、达标磁贴、顶栏辅助按钮 | **`[已批准调整]`** |
| **`medium`** | `26.dp` ❌ | **`20.dp`** | **主/次/危险大按钮、大数字输入框（`CompactNumberField`）、分段胶囊外框、行级卡片（`MedicationRow`）** | **`[已批准调整]`** |
| **`large`** | `20.dp` ❌ | **`26.dp`** | **全站标准暖纸内容卡片（`DataCard`、设置分组、历史卡片、趋势卡片）** | **`[已批准调整]`** |
| `extraLarge` | `32.dp` | **`32.dp`** | 首页情绪核心大卡（`ReadingHero`）、模态大弹窗 | **`[源码保留]`** |
| `Full` (`50%`) | `50%` | **`50%`** | 底部悬浮 Dock、中央 `+` 键、`StatusChip`、`BpRangeBar` 轨道 | **`[源码保留]`** |

### 6.2 嵌套同心圆角推荐准则（`D2-5 / AUD-24`）

- 普通嵌套卡片优先遵循同心圆角公式 $R_{\text{inner}} \approx R_{\text{outer}} - P$，以 **`8.dp` 作为推荐下限**（非不可突破的硬性限制；微型组件、小型容器及特殊嵌套场景可按实际几何关系与视觉效果采用更小圆角）。
- 首页 `MedicationRow` 规范为外圆角 **`20.dp`**、内层图标块与打卡按钮 **`12.dp`**（`D4-2`）。

---

## 七、图标与视觉符号规范（Iconography）

1. **`[源码保留]` 尺寸体系**：行内图标 `16.dp`；`RoundIconBadge` 保持 `46.dp` 圆底内嵌 `22.dp` 图标（`D2-4`）；底部导航与顶栏主图标 `24.dp`。
2. **`[已批准调整]` 图标与语义底色唯一映射（`D6-1 / AUD-14`）**：
   - 「高风险保存前确认」的 `WarningAmber` 图标徽章改用暖色警示底（`primaryContainer` / `onPrimaryContainer`），严禁使用健康绿 `secondaryContainer`；
   - 「首页四色条区间」改用 `Icons.Outlined.Tune` 等刻度调节图标，空心爱心 `FavoriteBorder` 保留给「显示小压」专属。
3. **`[已批准调整]` 禁止字面量空格充当图文间距（`D4-2 / AUD-24`）**：首页早晚测量槽移除 `" $label · $time"` 前导空格，改用 `Row(horizontalArrangement = Arrangement.spacedBy(4.dp))`。

---

## 八、核心公共组件规范（Component Standards）

### 8.1 按钮体系（`D1-1`, `D2-4`, `D4-2`, `D6-1`）

| 组件名称 | 高度 / 热区 | 圆角 | 视觉与业务角色规范 | 状态标记 |
| :--- | :--- | :--- | :--- | :--- |
| `AppPrimaryButton` | **`54.dp`**（绑定 `AppDimensions.primaryButtonHeight`） | `20.dp` (`shapes.medium`) | `primary` 填充 + `onPrimary` 粗体；**数据管理页保留「导出明文 Excel（`.xlsx`）」为主按钮**，于页面第一层前置常驻风险说明（`D6-1`） | **`[已批准调整]`** |
| `AppSecondaryButton` | **`54.dp`**（绑定 `primaryButtonHeight`） | `20.dp` (`shapes.medium`) | `surfaceVariant` 填充 + `onSurface`；用于加密导出（`.bpx`）、医生报告 PDF、数据恢复等次级操作 | **`[已批准调整]`** |
| `AppDangerButton` | **`54.dp`**（绑定 `primaryButtonHeight`） | `20.dp` (`shapes.medium`) | `errorContainer` + `onErrorContainer`；清空本地数据等不可逆操作 | **`[已批准调整]`** |
| 表单保存主按钮 | **`58.dp`**（保留 `saveButtonHeight`） | `20.dp` (`shapes.medium`) | 新增/编辑测量保存主操作 | **`[源码保留]`** |
| **`AppCompactActionPill`** | 触控 **`≥ 48dp × 48dp`** | `CircleShape` / `12.dp` | 脱离 M3 `TextButton` `64.dp` 最小宽限制；配 `FlowRow` 用于趋势选点栏（保留「复位视野」完整文案，大字自然折行） | **`[已批准调整]`（`D1-1 / AUD-01`）** |
| **首页服药打卡按钮** | 触控高 `≥ 48.dp`、宽 `≥ 76.dp` | `12.dp` | **待打卡**：`primaryContainer` 暖底 + `onPrimaryContainer` 粗体字；**已打卡**：`secondaryContainer` 绿底 + `✓ 已打卡` | **`[已批准调整]`（`D4-2 / AUD-09`）** |

### 8.2 分段控制器双规格规范（`SegmentedPillGroup` · `D3-1 / AUD-08`）

| 变体 | 字号与字重 | 滑块样式 | 底槽与内距 | 适用范围 |
| :--- | :--- | :--- | :--- | :--- |
| **`Primary`** | **`16.sp` SemiBold**（保留现有字号） | `primaryContainer` 浅陶土色滑块 + `onPrimaryContainer` | `surfaceVariant`、`4.dp` 内距、`20dp / 16dp` 同心圆角 | 历史「日历/近期」、趋势「每次测量/每日平均」、资料「性别」 |
| **`Compact`** | **`13.sp` SemiBold** | `surface` 抬升滑块 + `onSurface` 加粗 | `surfaceVariant` / `surfaceContainerHighest`、`4.dp` 内距、`20dp / 16dp` 同心圆角 | 历史「本周/本月」（独立外层）、趋势「7天…全部」与「双指标/收缩压/舒张压」 |

- **多项窄屏/大字保护**：5 项选择器在窄屏或大字号下优先合理折行/自适应布局，禁止 5 项全纵向堆叠成高柱；若用水平滚动则必须配可滚动提示与选中项自动定位，不得缩小热区或裁切文字。

### 8.3 卡片、列表与表单规范

1. **复合入口整卡可点（`D1-4 / AUD-04`）**：我的页个人资料卡外层 `DataCard` 绑定 `onClick = onOpenProfile` 并合并无障碍语义，消除下方达标磁贴点击死区。
2. **设置分组静态化与精准内缩分割线（`D6-2 / AUD-22, 24`）**：我的页 5 大分组全部采用静态 `SettingsSectionTitle`（仅取消设置页「显示」折叠，不影响其他页面 `ExpandableSection`）；分组内 `HorizontalDivider` 内缩 `start = 76.dp, end = 16.dp`（由 `16dp + 46dp + 14dp` 语义常量推导）。
3. **历史记录卡片排版与密度（`D5-2 / AUD-16`）**：时间列 `widthIn(min = 54.dp)` 防大字折行；时间与血压首行基线对齐、右侧状态芯片垂直居中；列表首行保留简洁读数、二行补 `次/分`；详情页完整显示 `mmHg` 与 `次/分`；读屏输出完整单位。
4. **录入表单满宽效率原则（`D4-5 / AUD-28`）**：新增/编辑测量页每组 `MeasurementReadingCard` **保持无卡片包裹的满宽三栏录入布局**，仅收紧无删除按钮时组标题的 `48.dp` 虚高，通过组内/组间留白与必要弱分割线分组。
5. **药品剂量快捷单位辅助（`D4-2 / AUD-09`）**：`MedicationEditorDialog` 剂量框加常驻说明与轻量单位胶囊（纯输入辅助，不重复追加、不替换已有单位、不自动推断/换算、不改库）。

### 8.4 日历格子、三动作弹窗与全局反馈规范

1. **日历单日格子（`D1-5 / AUD-05`）**：维持 `38.dp` 成熟尺寸，采用互斥单层边框（`selected -> 2.dp primary`；`today && !selected -> 1.5.dp outline`），同时确保 `today && selected` 时保留「今天」的可识别性。
2. **表单报错位置与三动作弹窗（`D3-4 / D6-1 / D7-1 / AUD-21`）**：
   - 新增与编辑页校验错误统一位于保存按钮正上方，附 `LiveRegionMode.Polite` 与字段级反馈；
   - 两处三动作弹窗（`UnsavedChangesDialog`、清空所有本地数据确认弹窗）改为**纵向全宽按钮栈**（与 `UpdateDialog` 一致），按操作风险排序、破坏性操作标 `error` 色；
   - 普通双动作弹窗（含「确认导入范围」、分卷导出格式切换保护弹窗等）保持现有合理设计；清空弹窗点「先导出备份」关闭弹窗并定位至「导出与备份」区由用户自选格式，备份后绝不自动恢复清空弹窗或清空数据。
3. **单一全局 `SnackbarHost`（`D1-5 / AUD-15`）**：移除首页私有 `SnackbarHost`，全站轻提示汇总至根 `Scaffold`，**删除撤销等时效性提示严格优先于普通成功提示**，确保不丢失撤销能力。

---

## 九、健康数据可视化与医生报告规范（Health Data Visualization & Report）

1. **首页双标尺定界（`D4-3 / AUD-10, 26`）**：保留四色条「偏低 / 正常 / 偏高 / 过高」四双字标签与设置面板一致；默认与自定义态均配克制「参考区间 / 个人参考区间」标识（自定义态追加「已自定义」），完整说明通过信息图标或区间设置面板承载；色条加柔和 `surface` 衬底防融底；严禁改变医学分级与高风险算法。
2. **趋势图去噪与主图常驻（`D5-4 / AUD-11, 22`）**：脉搏曲线采用独立 `chartPulse` 令牌（候选浅 `#2E7D7B` / 深 `#80CBC4`）；个人目标具体数值动态外置到图表上方图例，画布内仅留干净参考线；取消首张血压主图折叠外壳使其常驻展示，下方脉搏/昼夜/更多统计保留折叠。
3. **达标率分布条严格比例真实性（`D5-5 / AUD-27`）**：**严禁设置任何人工最小分段宽度（禁止 `3–4dp` 或 `8dp` 拉伸）**；整条分布图采用统一外圆角裁剪、内部严格按真实次数比例绘制且分隔线不得吞噬极小色块，依托完整图例与精确计数传达极小占比记录。
4. **周卡三项独立图例（`D4-4 / AUD-25`）**：拆分为 `● 实心圆：日均高压`、`○ 空心圆：日均低压`、`╌╌ 虚线：个人参考正常上界 {sys}/{dia}` 三项独立图文 `FlowRow`，数值动态跟随配置。
5. **医生报告预览渐进隐私摘要（`D6-3 / AUD-29`）**：顶部收折为**逐项明示状态**的隐私摘要条（清晰呈现姓名与年龄、备注、药品三项开关真实状态 + 「调整隐私 ▾」原地展开入口）；严守姓名年龄默认关闭与重进重置规则；开关变更后同步重绘预览并确保分享/保存/打印文件与最新配置 100% 一致。

---

## 十、交互、手势与动效规范（Interaction & Motion）

### 10.1 动效时长与物理弹簧令牌（严格对齐 `AppMotion.kt` 源码基线）

| 令牌类别 | 真实源码定义（`AppMotion.kt`） | 减弱动态效果（`reducedMotion == true`） |
| :--- | :--- | :--- |
| **时长常量** | `Press=100ms`, `Micro=120ms`, `Fast=160ms`, `Normal=180ms`, `Medium=220ms`, `Dock=260ms`, `colorThemeSpec=200ms` | `0ms`（`snap()` / `EnterTransition.None`） |
| **物理弹簧族** | `springPress(NoBouncy, 900f)`、`springSnappy(0.94f, 520f)`、`springGentle(NoBouncy, 340f)`、`springCheck(0.78f, 480f)` | `snap()` |
| **光学缓动** | `SmoothOutEasing(0.22, 1.0, 0.36, 1.0)`、`SmoothInEasing(0.32, 0.0, 0.67, 0.0)` | `snap()` |

### 10.2 触控反馈、手势显性化与 Dock 滚动机制

1. **减弱动效按压反馈契约（`D1-3 / AUD-03`）**：`Modifier.pressableScale` 在 `reducedMotion == true && enabled && isPressed` 时不启动动画协程，立即施加 `0ms` `alpha = 0.84f` 瞬态按压反馈；`WheelTimePicker` 滚轮可点击项同步补充按压反馈。
2. **开关与复选框整行热区（`D1-4 / AUD-19`）**：全应用含 `Switch` / `Checkbox` 行统一整行 `.toggleable(...)` + `heightIn(min = 48.dp)`，内部控件回调置 `null`。
3. **显性化替代隐式手势（`D4-4 / D5-1 / AUD-25`）**：取消历史日历双击定位备注，改为选中日有备注时提供显式轻量「定位备注」入口；首页小压与对话气泡共同支持点击切换，附极克制进度微圆点。
4. **底部悬浮 Dock 滚动阈值（`D3-5 / AUD-32` · `[暂定 · 待真机冻结]`）**：向下收起保持 `28.dp`、向上展开暂定 **`28.dp`**，并补充回到页面顶部（含惯性滑顶）自动展开机制，避免图表内部手势误触。

---

## 十一、无障碍与适老化规范（Accessibility & Elderly Care）

| 维度 | 验收红线 | 关联决策 |
| :--- | :--- | :--- |
| **文本对比度** | 正文与标签 `≥ 4.5:1`；深色「偏低」提亮至 `#A3A0DE`（`~5.62:1`） | `D1-2` |
| **非文本对比度** | 浅色 `outline = #8A8177`（`3.26–3.77:1`）、深色 `outline = #7F7973`（`3.74:1`）；日历图例色块加描边、四色条加柔和衬底 | `D1-2`, `D4-3`, `D5-1` |
| **最小触控目标** | App 内所有独立可点区域 `≥ 48dp × 48dp`；开关/复选框整行可点；小部件在不损读数可读性前提下扩大热区 | `D1-4` |
| **屏幕阅读器** | 达标率卡片按“达标率 → 数值 → 说明”自然语序播报；历史列表与详情页均向读屏提供完整计量单位；表单报错加 `LiveRegionMode.Polite` | `D3-4`, `D5-2`, `D5-5` |
| **大字号弹性** | 最高 `2.0x` 下允许按钮文字与组件合理换行，严禁截断核心血压读数、操作名或状态标签 | `D1-1`, `D2-3`, `D3-1`, `D7-3` |

---

## 十二、UX 文案与医疗健康严谨性规范（UX Copy & Clinical Rigor）

### 12.1 语境分层术语表（`D6-4 / AUD-20`、`D6-5 / AUD-31`）

| 概念类别 | 最终标准写法 | 适用范围与约束 |
| :--- | :--- | :--- |
| **首页生活化测量槽** | **`早上 / 晚上`** | 首页「今天的测量」保留温暖生活口语 |
| **专业统计、提醒与医生报告** | **`早间`（`05:00–11:59`）/ `晚间`（`18:00–23:59`）** | 趋势早晚统计、提醒设置（原“晨间→早间”）、PDF 报告（原“早晨均值→早间均值”） |
| **昼夜血压节律统计** | **`日间 / 夜间`** | 趋势页昼夜统计 |
| **单条记录自然时段** | **`凌晨 / 清晨 / 上午 / 下午 / 晚上`**（完整保留 5 档） | `MeasurementPeriod.kt` 保持原 5 档划分与时间边界不变 |
| **计数单位** | 测量与高风险事件用 **`次`**；单次多组读数用 **`组`**；备注等文本记录用 **`条`** | 修正历史近期「高风险记录：0 条」为「0 次」 |
| **缺失数值占位符** | **`—`**（单字破折号 `U+2014`） | 淘汰 `--`；严禁将真实数值 `0` 替换为 `—` |
| **明文 Excel 前置风险说明** | *「Excel 文件未经加密，请妥善保管。如需保护数据隐私，可选择加密备份。」* | 数据管理页「导出与备份」第一层常驻显示（`D6-1`） |
| **「使用指南与更新」版本号** | 副标题精简为 `快速上手、血压标准与隐私说明`；动态版本 `v{BuildConfig.VERSION_NAME}` 置右侧 `>` 前 | 大字号/窄屏允许版本自然回落次级行防挤压（`D6-5`） |

### 12.2 医疗与隐私不可突破红线

1. **严禁改变医学规则与统计口径**：保留《中国高血压防治指南》六级分级（就高不就低）、单次/均值 `≥ 180/120 mmHg` 独立高风险警示及全部既有统计公式；严禁仅改展示文案时修改数据库字段、偏好 Key、备份格式或 Excel 列名。
2. **严禁削弱隐私与安全保护**：保留医生报告姓名与年龄默认关闭且每次重进强制重置、加密备份（`.bpx`）口令机制、锁屏服药通知脱敏、清空数据二次确认及本机离线透明声明。
