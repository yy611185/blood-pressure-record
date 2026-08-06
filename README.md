# 血压追踪（Android，本地优先）

## 项目简介

本项目是一个面向家庭成员，尤其是中老年人的血压记录 Android 应用。核心目标是本地可用、简单可靠、易备份迁移。

- 不接入服务器
- 不做登录系统
- 不做云同步
- 主数据以 Room 为准
- 设置页使用 DataStore

## 最新版本

**v1.8.1** (2026-07-27)

### 更新内容

- 修复平均策略在历史编辑和 Excel 往返时可能被错误重算的问题，备份格式升级至 v3 并兼容 v2。
- 提醒时间改为选择后自动保存，移除额外保存按钮。
- “记一次血压”按实际填写流程重新排序，保存按钮移至补充说明下方的页面最底部。
- 修复历史日历页回到顶部后标题可能不再显示的问题。
- 浅色、深色模式均支持三键导航和全面屏手势区域沉浸式显示。
- 药品时间点改为事务、差量更新，避免编辑药品时误删未变化时间点的打卡历史。

## 功能特性

### 核心功能

- **新增测量**：支持一次记录多组收缩压、舒张压和脉搏，并自动计算平均值和血压分级（可选“不计第一组”策略）。
- **血压分级**：采用《中国高血压防治指南》成人诊室血压标准（正常 / 正常高值 / 1–3级），并附加“血压偏低”提示；分级仅供参考，不替代医疗诊断。
- **历史记录**：按月日历查看具体日期记录，并支持详情、编辑、删除和撤销。
- **血压趋势**：7 天 / 30 天 / 全部真实时间趋势，支持缩放、参考线、每日聚合和当天明细。
- **设置管理**：用户资料、提醒设置、显示偏好、数据管理。

### 数据导出

- 支持 Excel (.xlsx) 格式导入和导出；格式 v3 保留每条记录的平均策略，并向后兼容 v2。
- 导出文件包含使用说明、测量记录、全部原始读数、用户资料和导出信息。
- 文件交给用户选择的 Android 文件位置或文件提供方；应用不会自行上传服务器。
- 即使暂无测量记录，也可导出用户资料与设置。
- 导出的 Excel 当前未加密，可能包含姓名、年龄、血压记录、症状、备注和提醒设置；请勿放入公共设备、不受信任的云盘或与他人共享的位置。
- 导入会先生成预览和统计信息，确认后才写入；可分别选择测量记录、用户资料、显示设置和提醒设置。
- 导入会拒绝明显的未来测量时间、重复记录 id、损坏或超过大小/解压安全上限的 xlsx；被跳过的记录不会写入。

## 技术栈

| 技术 | 说明 |
|------|------|
| Kotlin | 主要开发语言 |
| Jetpack Compose + Material 3 | 现代 UI 框架 |
| Navigation Compose | 导航管理 |
| MVVM | 架构模式 |
| Room | 本地数据库 |
| DataStore | 设置页存储 |
| Coroutines + Flow | 异步与响应式 |
| Apache POI | Excel 导入导出 |

## 目录结构

```text
app/src/main/java/com/example/bloodpressurerecord/
├── data/
│   ├── db/entity/        # Room 实体
│   ├── db/dao/           # Room DAO
│   ├── datastore/        # 设置页存储
│   ├── repository/       # 仓储层
│   │   └── backup/       # Excel 导入与导出
│   └── model/            # 数据模型
├── domain/
│   ├── model/            # 领域模型
│   └── calculator/       # 平均值、分级、高风险计算
├── ui/
│   ├── home/             # 新增测量页
│   ├── history/          # 历史、详情、编辑、趋势
│   ├── settings/         # 设置页与数据管理
│   └── common/           # 通用 UI/表单逻辑
├── navigation/           # 导航定义与路由
└── util/                 # 工具类
```

## 构建

### 环境要求

- Android Studio Meerkat | 2024.3.1 Patch 1 或更高版本
- JDK 17
- Android SDK Platform 36、Build Tools 36.0.0 或更高版本
- Android Gradle Plugin 8.10.1
- Gradle 8.11.1
- `minSdk = 26`，`compileSdk/targetSdk = 36`

### 构建命令

```bash
# Debug 版本
./gradlew assembleDebug

# Release 版本
./gradlew assembleRelease

# Google Play 上架包
./gradlew bundleRelease
```

Release 构建不会回退使用 Debug 密钥。构建前需设置以下环境变量：

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
- Excel 备份当前为明文格式；用户需要自行保护导出文件，后续加密格式设计见 [`docs/backup-encryption-design.md`](docs/backup-encryption-design.md)。
- 卸载应用前未导出的数据可能丢失；应用无法保证本地数据永不丢失。

## 许可协议

MIT License

## 作者

[yangabcxyz](https://github.com/yy611185)
