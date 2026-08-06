# 血压追踪 · Blood Pressure Record

> 一个**本地优先**的血压记录 Android 应用——为家庭成员（尤其是中老年人）设计：不注册、不联网、不复杂。数据只存在你的手机里。

[![License: MIT](https://img.shields.io/badge/license-MIT-green.svg)](./LICENSE) ![minSdk](https://img.shields.io/badge/minSdk-26%20(Android%208.0)-blue) ![targetSdk](https://img.shields.io/badge/targetSdk-35-blue) ![Version](https://img.shields.io/badge/version-1.8.1-orange)

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
| 💾 Excel 导入导出 | 备份含完整原始读数与设置，格式 v3（兼容 v2） |

## 四、你的数据安全（请放心）

1. **数据不出设备**：无服务器、无网络权限、无账号体系。App 根本不会"上传"任何东西。
2. **系统自动备份已关闭**：Room / DataStore / 健康数据均被排除在 Android 云备份与换机迁移之外。
3. **备份是主动的**：只有在"设置 → 数据管理 → 导出为 Excel"时，数据才会写入你选择的保存位置（如文件管理器、云盘——**导出文件为明文，请勿放入公共云盘**）。
4. **日历权限可选**：仅当你在设置中开启"服药提醒写入系统日历"时才申请 READ/WRITE_CALENDAR；关闭后不再使用。
5. **卸载即失**：未主动导出的数据卸载后无法找回——建议定期导出备份。

## 五、快速开始

### 安装

- **已发布渠道**：Google Play / Release APK（见 [Releases](https://github.com/yy611185/blood-pressure-record/releases)，自签名 Release 包，不经过 Play 商店）
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

- 健康数据在本地**明文存储**（Room）与**明文导出**（Excel）——依赖 Android 全盘加密与你的主动保管
- 换机只能靠手动 Excel 迁移，无自动同步
- 血压分级采用中国指南标准，其他国家/地区标准可能不同

## 八、版本历史

| 版本 | 日期 | 一句话 |
|---|---|---|
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
