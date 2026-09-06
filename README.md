# 血压追踪 · Blood Pressure Record

> 一个**本地优先**的血压记录 Android 应用——为家庭成员（尤其是中老年人）设计：不注册、不联网、不复杂。数据只存在你的手机里。

[![License: MIT](https://img.shields.io/badge/license-MIT-green.svg)](./LICENSE) ![minSdk](https://img.shields.io/badge/minSdk-26%20(Android%208.0)-blue) ![targetSdk](https://img.shields.io/badge/targetSdk-36-blue) ![Version](https://img.shields.io/badge/version-1.8.2-orange)

---

## 一、这是给谁用的？

**给血压需要被记录的人，以及记录他们的家人。**

- 界面大、字号可调，面向中老年用户优化
- 完全离线：**不接入服务器、不注册账号、不做云同步**
- 一次测量，自动计算平均值、分级、趋势，家人不用对着数字发愁
- 数据自由：随时导出 Excel 备份，换机一键恢复

> ⚠️ 应用内的血压分级仅供参考，**不替代医疗诊断**。如有异常请咨询医生。

## 二、界面一览

<!-- 截图占位：建议放 3 张 —— 首页测量 / 历史日历 / 趋势图 -->
| 首页测量 | 历史日历 | 血压趋势 |
|---|---|---|
| *(待补截图)* | *(待补截图)* | *(待补截图)* |

## 三、核心功能

| 功能 | 说明 |
|---|---|
| 📝 一次记多组 | 一次测量可录入多组收缩压/舒张压/脉搏，自动算平均值（可选"不计第一组"） |
| 🏷 自动分级 | 按《中国高血压防治指南》成人诊室标准分级：正常 / 正常高值 / 1–3 级 + 偏低提示 |
| 📅 历史日历 | 按月查看、详情、编辑、删除、**撤销** |
| 📈 趋势图 | 7/30 天/全部，支持缩放、参考线、每日聚合、当天明细 |
| 💊 服药提醒 | 晨/晚提醒 + 每日打卡 + 可选写入系统日历（需授权） |
| 📲 桌面小部件 | 不打开 App 也能看到最新血压与今日进度 |
| 💾 Excel 导入导出 | v4 完整备份测量、设置、药品、服药时间与打卡，兼容读取 v2/v3 |
| 🔐 加密备份 | 可选 .bpx 加密导出：AES-256-GCM + 口令派生密钥，导入需相同口令 |

- 支持 Excel (.xlsx) 格式导入和导出；格式 v4 保留平均策略和完整用药数据，并向后兼容 v2/v3。
- 导出文件包含使用说明、测量记录、全部原始读数、用户资料、药品、每日服药时间、打卡历史和导出信息。
- 文件交给用户选择的 Android 文件位置或文件提供方；应用不会自行上传服务器。
- 即使暂无测量记录，也可导出用户资料与设置。
- 可选择明文 Excel 或口令加密的 .bpx；两者都可能包含姓名、年龄、血压记录、症状、备注、提醒和用药数据，请妥善保存。
- 导入会先生成预览和统计信息，确认后才写入；可分别选择测量记录、用户资料、显示设置和提醒设置。
- 导入会拒绝明显的未来测量时间、重复记录 id、损坏或超过大小/解压安全上限的 xlsx；被跳过的记录不会写入。

## 四、你的数据安全（请放心）

1. **数据不出设备**：无服务器、无网络权限、无账号体系。App 根本不会"上传"任何东西。
2. **系统自动备份已关闭**：Room / DataStore / 健康数据均被排除在 Android 云备份与换机迁移之外。
3. **备份是主动的**：只有在"设置 → 数据管理 → 导出为 Excel"时，数据才会写入你选择的保存位置（如文件管理器、云盘——**导出文件为明文，请勿放入公共云盘**）。
4. **日历权限可选**：仅当你在设置中开启"服药提醒写入系统日历"时才申请 READ/WRITE_CALENDAR；关闭后不再使用。
5. **卸载即失**：未主动导出的数据卸载后无法找回——建议定期导出备份。

## 五、快速开始

### 安装

- **获取方式**：通过 [Releases](https://github.com/yy611185/blood-pressure-record/releases) 下载自签名 Release APK 安装（首次安装需允许"未知来源"）
- 最低支持 **Android 8.0 (API 26)**，建议 Android 10+

### 第一次使用（3 步）

1. 打开 App → **测量** 标签页 → 记下第一次血压
2. 右上角/设置中补充**用户资料**（姓名、年龄、目标血压——用于分级与统计）
3. （可选）设置 → 提醒，开启晨/晚提醒

### 换手机迁移

1. 旧手机：设置 → 数据管理 → **导出为 Excel**，把文件存到方便的位置（网盘/微信传输）
2. 新手机：同一页面 → **从 Excel 备份导入**

## 六、技术栈（为什么是这些）

| 技术 | 用途 | 为什么 |
|---|---|---|
| Kotlin | 开发语言 | 官方首选，空安全 |
| Jetpack Compose + Material 3 | UI | 声明式 UI，主题/无障碍友好，适配三键导航与手势区 |
| MVVM + Repository | 架构 | 数据层与 UI 解耦，便于测试 |
| Room (v7) | 本地数据库 | 编译期 SQL 校验 + 迁移框架；**明文存储**（依赖系统 FBE 加密，详见隐私节） |
| DataStore | 设置存储 | 替代 SharedPreferences，Flow 响应式 |
| Coroutines + Flow | 异步 | 主线程安全，数据库查询响应式 |
| Apache POI (5.5.x) | Excel 导入导出 | 保留原始读数结构、多 Sheet 说明页；v3 格式向后兼容 v2 |
| Glance | 桌面小部件 | Compose 风格的小部件 API，与 UI 同构 |

## 七、开发者指南

### 环境要求

- Android Studio **Koala 或更高**（compileSdk 35 需要）
- JDK 17
- Android SDK 35，Gradle 8.7

### 构建

```bash
./gradlew assembleDebug        # Debug APK（applicationId 带 .debug 后缀）
./gradlew assembleRelease      # Release APK（未配置签名时产出 unsigned 包）
./gradlew bundleRelease        # Google Play AAB
```

Release 签名通过环境变量注入（**绝不回退 Debug 密钥**）：

```bash
export BP_RELEASE_STORE_FILE=/path/to/release.jks
export BP_RELEASE_STORE_PASSWORD=...
export BP_RELEASE_KEY_ALIAS=bloodpressure
export BP_RELEASE_KEY_PASSWORD=...
```

产物：`app/build/outputs/apk/release/app-release.apk`、`app/build/outputs/bundle/release/app-release.aab`

### 测试

```bash
./gradlew test                  # 单元测试（JVM，快）
./gradlew connectedAndroidTest  # 仪器测试（需设备/模拟器）
./gradlew :baselineprofile:generateBaselineProfile  # 性能基线
```

覆盖范围（20+ 测试类）：平均值/分级/高风险规则、趋势图数学、月历布局、提醒时间计算、表单逻辑、Repository 保存读取、Excel 备份往返、数据库迁移。

### 目录结构

```text
app/src/main/java/com/example/bloodpressurerecord/
├── data/
│   ├── db/              # Room：entity / dao / AppDatabase(v7)
│   ├── datastore/       # 设置与偏好
│   ├── repository/      # 仓储层
│   │   └── backup/      # Excel 导入/导出/文件读取
│   └── calendar/        # 服药提醒 → 系统日历同步
├── domain/
│   ├── calculator/      # 平均值、分级、高风险、趋势计算
│   ├── model/           # 领域模型
│   └── time/            # 时间与日期刻度
├── reminder/            # 闹钟调度、BootReceiver、提醒接收
├── ui/                  # home / history / settings / common / theme
├── navigation/          # 路由
├── widget/              # 桌面小部件
└── util/                # 工具
```

另有 `baselineprofile/`（性能基准模块）与 `tools/`（基准查询脚本）。

### 已知限制（诚实声明）

- 健康数据在本地**明文存储**（Room，依赖 Android 全盘加密）；导出可选**明文 Excel** 或**口令加密的 .bpx 容器**
- 换机只能靠手动 Excel 迁移，无自动同步
- 血压分级采用中国指南标准，其他国家/地区标准可能不同
- Android Studio Meerkat | 2024.3.1 Patch 1 或更高版本
- JDK 17
- Android SDK Platform 36、Build Tools 36.0.0 或更高版本
- Android Gradle Plugin 8.10.1
- Gradle 8.11.1
- `minSdk = 26`，`compileSdk/targetSdk = 36`

## 八、版本历史

| 版本 | 日期 | 一句话 |
|---|---|---|
| 1.8.2 | 2026-08-21 | 口令加密备份、日历备注红点、Dock 胶囊圆角 |
| 1.8.1 | 2026-07-27 | 备份 v3 兼容、提醒自动保存、导航沉浸 |
| 1.8.0 | 2026-07-27 | 服药提醒、打卡、桌面小部件、日历同步 |
| 1.7.0 | 2026-07-26 | 中国指南分级、平均策略、跨零点修复 |
| ... | ... | 完整历史见 [RELEASE_NOTES.md](./RELEASE_NOTES.md) |

## 九、发布与贡献

- **发布清单**（Play 商店上架检查项）：见 [docs/PUBLISHING.md](./docs/PUBLISHING.md)
- **贡献**：欢迎 Issue 与 PR；提交前请运行 `./gradlew test`，遵循现有代码风格
- **许可**：MIT — 详见 [LICENSE](./LICENSE)
- **作者**：[yangabcxyz](https://github.com/yy611185) — 为家人做的应用

---

*最后更新：2026-08 · 版本 1.8.1 · 本 README 随 `RELEASE_NOTES.md` 同步维护*

- `BP_RELEASE_STORE_FILE`：签名库路径
- `BP_RELEASE_STORE_PASSWORD`：签名库密码
- `BP_RELEASE_KEY_ALIAS`：密钥别名
- `BP_RELEASE_KEY_PASSWORD`：密钥密码

成功后 APK 位于 `app/build/outputs/apk/release/app-release.apk`，Google Play
使用的 AAB 位于 `app/build/outputs/bundle/release/app-release.aab`。发布前必须
使用 `apksigner verify` 校验 APK，并使用 `jarsigner -verify` 校验 AAB。

### Google Play 发布前检查

- 正式 `applicationId` 为 `com.yang.bloodpressure`。首次正式分发后不要再改，
  否则 Android 会把新包名识别为另一个应用。
- Debug 构建使用 `com.yang.bloodpressure.debug`，可与正式版并存；亲友长期使用时
  只分发自签名 Release APK。
- 在应用内和 Play Console 中提供可公开访问、非 PDF、非地域限制的隐私政策
  URL，准确说明本地健康数据、日历权限和用户主动导出的 Excel 文件如何处理。
- 在 Play Console 完成 Health apps declaration；本应用至少涉及疾病/状况管理
  与用药/治疗管理。
- 完成 Data safety 表单。即使应用不上传或共享数据，也必须提交声明和隐私政策。
- 上传签名 AAB，并保管好上传密钥；正式发布前使用内部或封闭测试轨道验证提醒、
  日历授权、导入导出、数据库升级和各类系统导航模式。

## 使用说明

### 新增测量

1. 打开“测量”标签页。
2. 填写日期、时间、场景。
3. 输入至少 2 组血压读数：收缩压、舒张压、脉搏。
4. 系统自动计算平均值和血压分级。
5. 点击保存。

### 备份与迁移

1. 进入“设置” -> “数据管理”。
2. 点击“导出为 Excel”。
3. 选择保存位置。
4. 换机后在同一页面选择“从 Excel 备份导入”。

## 版本历史

| 版本 | 日期 | 主要更新 |
|------|------|---------|
| 1.8.2 | 2026-08-21 | 口令加密备份、日历备注红点与双击跳转、Dock 胶囊圆角 |
| 1.8.1 | 2026-07-27 | 数据往返修复、提醒自动保存、历史标题与系统导航沉浸 |
| 1.8.0 | 2026-07-27 | 服药提醒、首页打卡、桌面小部件与日历同步 |
| 1.7.0 | 2026-07-26 | 中国指南分级、偏低提示、平均策略与跨零点修复 |
| 1.6.2 | 2026-07-25 | 历史双模式、趋势图一致性与数字时间输入 |
| 1.6.1 | 2026-07-25 | 日历、单日、首页、趋势、导入与启动性能优化 |
| 1.6.0 | 2026-07-25 | 日历历史页、日期选择器、无障碍与整体体验 |
| 1.5.0 | 2026-07-23 | 趋势性能重构、备份导入、系统提醒与工程治理 |
| 1.4.4 | 2026-05-03 | 修复 Excel 导出空白文件问题 |
| 1.4.3 | 2026-05-02 | 修复 Excel 导出空白表格问题 |
| 1.4.2 | 2026-04-30 | 设置页二级菜单优化 |
| 1.4.1 | 2026-04-28 | 数据管理页面重构 |
| 1.4.0 | 2026-04-26 | 动态读数组扩展支持 |
| 1.3.6 | 2026-04-24 | 趋势图表优化 |

详细更新说明请查看 [RELEASE_NOTES.md](./RELEASE_NOTES.md)。

## 测试

```bash
# 单元测试
./gradlew testDebugUnitTest

# Lint 与 Debug 构建
./gradlew lintDebug assembleDebug

# Release 构建（未配置正式密钥时只生成 unsigned 产物）
./gradlew bundleRelease assembleRelease

# 界面测试（需要已连接的模拟器或真机）
./gradlew connectedDebugAndroidTest
```

测试覆盖：

- 平均值计算器
- 血压分级计算器
- 高风险判断
- Repository 保存/读取
- Excel 导出写入

## 维护与兼容性约束

- Room 当前版本为 v7；新增字段或表必须提供显式迁移，禁止 destructive migration/fallback。修改记录更新逻辑时不得用 `REPLACE` 重建现有 session。
- Excel 导入必须继续兼容 v2/v3；改变字段、派生平均值或风险阈值前先更新对应往返测试和迁移说明。
- Android 16 构建使用 API 36、AGP 8.10.1、Gradle 8.11.1、JDK 17。Release 没有正式签名环境时只能生成 unsigned 产物，不能回退 Debug 密钥。
- 本应用保持本地优先、无登录、无服务器、无网络上传；不要为了备份或提醒引入网络权限。
- 提醒闹钟安排与通知显示权限分离；没有通知权限时仍保留闹钟链，非法时间只取消对应类型，日历事件与提醒必须成套创建或回滚。
- 血压分级与高风险提醒是两套规则：3 级分级不自动等于高风险提醒；修改阈值必须同步更新领域测试和历史迁移逻辑。
- 依赖升级需同时检查 Gradle 依赖锁定/校验元数据和许可证；如果后续生成 `gradle/verification-metadata.xml`，CI 不得通过关闭校验来绕过哈希变化。
- 发布前至少执行 `testDebugUnitTest`、`lintDebug`、`assembleDebug`、`assembleRelease`，并在具备设备时执行 `connectedDebugAndroidTest`；使用 `apksigner verify` 检查 APK 签名。

API 36 构建需要本机接受 Android SDK Platform 36 和 Build Tools 36 的许可证。
Release 构建只在同时提供正式签名环境变量时签名；未配置时不会回退使用 Debug
密钥。

## 隐私声明

- 应用不自建服务器，也不会主动上传血压记录。
- Room、DataStore 和其他本地健康数据的 Android 系统自动备份已关闭。
- 备份必须由用户在“数据管理”中主动导出，导出文件仅保存到用户选择的位置。
- Excel 备份为明文格式；如需加密请选择“加密导出”（.bpx，AES-256-GCM，口令派生密钥且不落盘——口令遗失后备份无法恢复）。加密设计见 [`docs/backup-encryption-design.md`](docs/backup-encryption-design.md)。
- 卸载应用前未导出的数据可能丢失；应用无法保证本地数据永不丢失。

## 许可协议

MIT License

## 作者

[yangabcxyz](https://github.com/yy611185)
