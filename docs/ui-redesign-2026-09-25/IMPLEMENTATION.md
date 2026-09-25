# 根目录原型的 Android 实现边界

本次视觉来源为 `UIredesign/血压记录 重构.html` 和同目录 `bp*.jsx`、`bp.css`，不是 `UIredesign/v1-原稿`。Android 代码以 Kotlin、Jetpack Compose、Material 3、Room 和 DataStore 实现页面；样例读数不进入真实首页、历史或趋势。配色、字体与间距的已提取规则见根目录 `DESIGN.md`，具体实现以 `app/src/main` 为准。

原型的假手机外框、状态栏、手势条、自绘数字键盘转为设备系统栏和原生数字 IME。录入按静坐、读数、情况、完成四步组织，保留草稿、编辑、至少两组读数、平均策略、逐组校验、异常二次确认和高风险提示。原型演示至多 10 组；Android 领域规则 `MeasurementInputRules.MAX_READING_COUNT` 为 20 组，界面继续遵守既有 20 组上限。趋势继续使用已有 `RAW` / `DAILY` 聚合模式和真实数据库记录；原型图上的示例解释不改变计算规则。

原型中的重复星期计划、漏测重提醒、备份提醒没有对应的持久化与调度能力，本次不把展示效果当成功能实现。已有定时提醒、药物提醒、数据管理和备份能力沿用原生实现。外观模式和“小压”显示偏好由 DataStore 保存，并作为 v4 备份格式的可选键；缺失旧键时使用兼容默认值，不修改既有记录结构。Bricolage Grotesque 数字字体随应用离线打包，OFL 许可随包保留。

首页从仓储读取最近会话、当天早晚最后一次测量、服药时间点与七日均值，不展示原型中的演示姓名、读数或连续天数。小压的分级表情、空状态与提醒文案取当前状态；服药打卡只在数据库写入成功后反馈，并支持撤销。导航使用首页、历史、趋势、我的四入口与中央新增按钮；趋势被设置隐藏时保留其余入口和新增操作。血压分级芯片维持偏低、正常、正常高值、1/2/3级的不同色彩，高风险另外明确标识。

通用页面边距为 20dp，卡片常用圆角 26dp，Dock 为 30dp 圆角、72dp 高，中央新增按钮为 64dp。读数录入按选定稿采用三个分开的输入格：上方收缩压格最小高度 132dp、字号 70sp；下方舒张压和选填脉搏格各最小高度 106dp、字号 44sp。三格各用 24dp 圆角；窄屏时下方两格纵排。公共主、次级按钮按内容保持至少 54dp 高，录入页底部保存按钮为 58dp。布局以系统字体缩放、真实屏幕安全区和原生 IME 为准。

用户选择的根目录原稿含有“小压”和亲切提醒；它覆盖此前 `PRODUCT.md` 草案的“中性成人语言”偏好。正式界面保留友好语气，但不把演示数据、推断的诊断或尚无持久化支持的提醒写成真实功能。`README.md` 与 `directions.md` 仍是候选阶段的历史记录。

初轮 `assembleDebug`、AndroidTest APK 构建、132 项 JVM 单测及 Android lint（0 errors）通过。最后一批代码冻结后再次执行 `assembleDebug`，并只重跑受影响的 `HistoryViewModelTest`（13 项）、`HomeViewModelDynamicTest`（11 项）和 `SessionFormLogicDynamicTest`（3 项）：共 27 项，0 失败。最终 APK 为 `app/build/outputs/apk/debug/app-debug.apk`（33,982,290 字节，SHA-256 `243177A6F59788066C9AE674A0AA433164CFDABAEDA242EAEAF109721DC2B6B1`）。按本轮收尾范围，最后一批改动没有重新运行 lint、仪器化测试或设备截图。

首轮原生模拟器截图在 `device-captures/`：包含[空数据首页](device-captures/home-empty.png)、[135/85 有数据首页](device-captures/home-with-data.png)、[静坐页](device-captures/record-rest.png)、[两组录入](device-captures/record-readings.png)、[数值键盘](device-captures/record-readings-ime.png)、[补充情况](device-captures/record-details.png)、[保存完成](device-captures/record-success.png)、[历史日历](device-captures/history-with-data.png)、[近期记录](device-captures/history-recent.png)、[趋势](device-captures/trend-with-data.png)、[我的](device-captures/my-light.png)，以及系统浅色、应用手动深色下的[历史](device-captures/history-dark-manual.png)、[趋势](device-captures/trend-dark-manual.png)和[我的](device-captures/my-dark.png)。主截图为 API 34 模拟器 1080×2340px、440dpi（约 393×851dp）、系统字号 1.0，应用大字偏好关闭；截图工具在每次捕获后恢复临时显示参数。截图使用单独创建的 Android 用户 10 和合成记录，验收后已切回原用户 0 并删除用户 10。它们早于最后一批修复，不能作为该批修复的设备画面证据；小屏大字和低压/脉搏聚焦的 IME 画面未完成。
