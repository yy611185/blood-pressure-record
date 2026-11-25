# 首页血压区间示意条

首页最近读数下显示四段等宽示意条。位置来自本地最近测量的代表值与本地配置；离线可用，无新增依赖、网络请求或 Room 表结构变更。在“我的 → 血压 → 血压目标范围”打开范围编辑面板，校验通过后原子保存整套配置并更新首页标记。

首页主卡片移除独立分级标签及范围编辑按钮；单位和小字号脉搏位于血压大数字右侧，缺少脉搏时只显示单位。数字按可用宽度调整字号，避免小屏或大字模式截断。平均组数与分段条使用 4dp / 8dp 间距，卡片高度随内容缩短，卡片间保持原有间距；高风险标签与复测提醒继续显示。

## 配置与边界

`BpZoneConfig.Default` 是唯一默认数值来源：收缩压 cuts 为 90 / 120 / 140、显示范围 70–180；舒张压 cuts 为 60 / 80 / 90、显示范围 40–110。这些数字只是用户指定的占位示例，不构成医学标准，实际分级及默认目标范围待产品确认。

每个轴要求正整数、min < max、三个 cuts 严格递增且均在 min..max 中。恰好等于 cut 时进入更高一级；小于 min 位于首段最左，大于等于 max 位于末段最右。收缩压与舒张压取更高一级，同级取 offset 更大者。percent 为 `(zone.ordinal + offset) * 25`，限制在 2–98；示意条不是线性的 mmHg 刻度。

配置使用既有 `app_preferences` DataStore 中的 `bp_zone_config_v1` 单键保存，不影响其他设置。没有保存、payload 损坏或配置非法时回退默认；非法写入返回错误，不覆盖已保存值。DataStore 自身在 IO 协程进行文件读写。首页首次展示等待配置真实发射，读数与区间来自同份 UI 快照。

## 文件清单

均位于 `app/src/main/java/com/example/bloodpressurerecord/`，除下方单独标注的资源、测试与文档：

| 文件 | 变更 | 用途 |
| --- | --- | --- |
| `domain/model/BpZoneConfig.kt` | 新增 | 不可变配置、四区间与位置、校验、占位默认值 |
| `domain/calculator/LocateBpZoneUseCase.kt` | 新增 | 纯 Kotlin 区间与位置计算 |
| `data/repository/BpZoneConfigRepository.kt` | 新增 | Flow 与保存结果接口 |
| `data/repository/DefaultBpZoneConfigRepository.kt` | 新增 | 现有 DataStore 的本地持久化 |
| `AppContainer.kt` | 修改 | 手动注入配置 Repository |
| `AppViewModelFactory.kt` | 修改 | 为 DashboardViewModel 与 SettingsViewModel 注入同一范围配置仓库 |
| `ui/home/BpRangeUiState.kt` | 新增 | Empty / Ready，暴露 zone，无 Compose 类型 |
| `ui/home/BpZoneTargetDraft.kt` | 新增 | 编辑草稿与解析、保存状态 |
| `ui/home/DashboardViewModel.kt` | 修改 | 合并读数及配置，响应设置页保存后的范围更新 |
| `ui/settings/SettingsViewModel.kt` | 修改 | 管理范围编辑、校验、保存及错误状态 |
| `ui/settings/SettingsScreen.kt` | 修改 | 血压目标范围入口与范围编辑面板 |
| `ui/home/BpRangeBar.kt` | 新增 | 色条、400ms 标记、标签、语义、系统动画设置、8 个 Preview |
| `ui/home/BpZoneTargetSheet.kt` | 新增 | 范围编辑面板，小屏与大字自动使用单列表单 |
| `ui/home/DashboardScreen.kt` | 修改 | 紧凑读数、右侧脉搏、有读数与空状态分段条 |
| `ui/theme/BpRangeTokens.kt` | 新增 | 深浅色 token 与组件尺寸 |
| `ui/theme/Theme.kt` | 修改 | 随应用主题提供色条 token |
| `app/src/main/res/values/strings.xml` | 修改 | 本次所有展示与无障碍文案；项目当前只有中文资源 |
| `app/src/test/java/com/example/bloodpressurerecord/domain/calculator/LocateBpZoneUseCaseTest.kt` | 新增 | 分界点、极端、无效输入、自定义配置、同级位置等 |
| `app/src/test/java/com/example/bloodpressurerecord/data/repository/DefaultBpZoneConfigRepositoryTest.kt` | 新增 | 默认、拒绝非法写入、Flow 更新、真实文件持久化与损坏回退 |
| `app/src/test/java/com/example/bloodpressurerecord/ui/home/DashboardViewModelTest.kt` | 修改 | 状态流转、首次加载与设置保存后首页联动 |
| `app/src/test/java/com/example/bloodpressurerecord/ui/settings/SettingsViewModelTest.kt` | 新增 | 等待持久化配置、草稿校验、保存锁定、失败重试与最新配置 |
| `app/src/androidTest/java/com/example/bloodpressurerecord/ui/home/BpRangeBarTest.kt` | 新增 | Empty / Ready 语义、两主题大字、关闭动画、编辑操作及首页脉搏位置与文字完整性 |
| `docs/bp-range-bar.md` | 新增 | 本说明 |

## 预览和测试

Android Studio 打开 `BpRangeBar.kt`，在 Design / Split 的 Compose Preview 中查看 Empty、正常、偏高、过高的浅色与深色版本。

Windows PowerShell 在项目根目录运行（项目已有本地 Gradle cache）：

```powershell
.\gradlew.bat --gradle-user-home .gradle-home :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug
```

连接 Android 设备或启动模拟器，运行本次 UI 测试：

```powershell
.\gradlew.bat --gradle-user-home .gradle-home :app:connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.example.bloodpressurerecord.ui.home.BpRangeBarTest'
```

`captureBpRangeReview=true` 可选参数会把 8 个主题/状态截图写入被测应用 cache，沿用已有 UI 测试的截图方式。测试会恢复临时修改的系统动画设置。

初版已验证：全部 230 项 JVM 测试通过；Android API 34 模拟器上 5 项 `BpRangeBarTest` 通过；Debug APK 与测试 APK 构建成功；lint 为 0 errors，已有 35 warnings、2 hints 未涉及本次变更文件。已查看浅色/深色、Empty/正常/偏高/过高共 8 张截图，320dp 宽、1.5 倍字体下标签和标记布局正常。截图保留于 `app/build/bp-range-review/`，0/1/2/3 分别对应四个状态。

本次紧凑布局与设置迁移已验证：234 项 JVM 测试通过；API 34 模拟器上 7 项 UI 检查通过，包含从设置入口打开并取消真实仓库的范围编辑面板。已查看 390dp / 320dp 浅色、320dp 1.5 倍字号深色，以及高风险、缺少脉搏的截图，血压和脉搏没有裁切或重叠。Debug APK 和测试 APK 构建成功；截图和仪器测试输出保留在 `app/build/home-card-review/`。范围设置保存后的首页更新、非法输入和保存失败由 ViewModel 测试覆盖。

## 接口与限制

`DashboardUiState.bpRange` 与最近读数来自同份快照，首页直接渲染此属性。`DashboardViewModel.bpRangeState` 可独立订阅；`BpRangeUiState.zone` 为后续卡片背景、大数字配色提供当前四段区间。`LocalBpRangeColors.current.segment(zone)` 返回随主题切换的分段颜色。

现有卡片背景、大数字、六级医学分级与高风险提示继续使用原逻辑，本次仅提供四段区间联动接口。产品需确认如何解释四段区间与既有医学分级的关系，避免将占位数字作为医学结论。

自定义示意条配置与用户资料中的单个目标收缩压/舒张压是独立设置；本次未扩展既有 Excel 备份格式，因此该配置目前仅保留在本机，清空全部数据会重置它。

项目没有 Turbine；为遵守“不新增第三方依赖”，ViewModel 测试使用已有 `kotlinx-coroutines-test` 收集并断言状态发射，覆盖请求的流转场景。实际设备上的 TalkBack 朗读与系统字体差异仍建议产品验收。
