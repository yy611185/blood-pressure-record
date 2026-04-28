# 血压追踪（Android，本地优先）

## 项目目标
本项目用于家庭成员日常血压记录与查看，首版本坚持本地优先：

- 不接服务器接口
- 不做登录
- 不做云同步
- 重点保证录入、查看、设置等基础能力稳定可用

## 当前阶段（工程骨架）
当前已完成可继续开发的基础工程结构，包含：

- Kotlin + Jetpack Compose
- Navigation Compose（底部导航：新增测量、历史、设置）
- Room（本地主数据存储骨架）
- DataStore（设置项存储骨架）
- Coroutines + Flow（异步与响应式数据流）
- Material 3

## 目录结构（核心）

`app/src/main/java/com/example/bloodpressurerecord/`

- `data/db/entity`
- `data/db/dao`
- `data/datastore`
- `data/repository`
- `domain/model`
- `domain/calculator`
- `ui/home`
- `ui/history`
- `ui/settings`
- `ui/common`
- `navigation`
- `util`

## 后续建议
下一阶段建议在现有骨架上依次补齐：

1. 新增测量完整表单与输入校验
2. 历史列表与详情页
3. 导入导出（应用私有目录 + 系统文件选择器）
4. 基础单元测试与界面测试
# 血压追踪（Android，本地优先）

## 项目简介
本项目是一个面向家庭成员（尤其是中老年人）的血压记录应用。核心目标是本地可用、简单可靠、易备份迁移。

- 不接入服务器
- 不做登录系统
- 不做云同步
- 主数据以 Room 为准
- 设置项使用 DataStore

## 技术栈
- Kotlin
- Jetpack Compose + Material 3
- Navigation Compose
- MVVM
- Room
- DataStore
- Coroutines + Flow
- Apache POI（XLSX 导入导出）

## 目录结构
主代码目录：`app/src/main/java/com/example/bloodpressurerecord/`

- `data/db/entity`：Room 实体
- `data/db/dao`：Room DAO
- `data/datastore`：设置项存储
- `data/repository`：仓储层
- `data/repository/transfer`：CSV/XLSX 导入导出
- `domain/model`：领域模型
- `domain/calculator`：平均值、分级、高风险、统计计算
- `ui/home`：新增测量页
- `ui/history`：历史、详情、编辑、趋势
- `ui/settings`：设置页与数据管理
- `ui/common`：通用 UI/表单逻辑
- `navigation`：导航定义与路由
- `util`：工具类

## 如何运行
1. 使用 Android Studio（建议 Jellyfish 或更高版本）打开项目根目录。
2. 等待 Gradle 同步完成（首次会下载依赖）。
3. 连接设备或启动模拟器（Android 8.0+）。
4. 点击 `Run 'app'` 启动应用。

## 如何测试
单元测试（JVM）：
- 运行 `app/src/test` 下测试。

仪器测试（设备/模拟器）：
- 运行 `app/src/androidTest` 下测试。

覆盖重点：
- 平均值计算
- 分级逻辑
- 高风险判断
- Repository 保存与读取
- 导入解析

## 如何录入数据
1. 进入“新增测量”页。
2. 填写测量时间、场景和至少两组读数（第三组可选）。
3. 系统自动计算平均值与“本次读数分级”（只读）。
4. 点击保存后写入 Room，可在历史页查看、编辑、删除。

## 如何导入导出
导出：
- 设置页 -> 数据管理 -> 导出 CSV / 导出 XLSX
- 文件写入应用私有目录 `files/backup/`

导入：
- 设置页 -> 数据管理 -> 导入 CSV / 导入 XLSX
- 当前实现会优先读取 `files/import/` 中最新同格式文件；若无则读取 `files/backup/`
- 已预留系统文件选择器接入能力：`stageImportFromUri(...)`

导入校验：
- 逐行校验，单行失败不会中断整体导入
- 失败行会统计并返回错误原因
- 要求至少包含 `datetime`、`scene`、两组收缩压/舒张压字段
# Blood Pressure Tracker (Android, Local-first)

## Project Overview
This app is a local-first blood pressure tracker for family use.
It focuses on simple data entry, history review, trend display, and local backup/migration.

## Tech Stack
- Kotlin
- Jetpack Compose + Material 3
- Navigation Compose
- MVVM
- Room (main data)
- DataStore (settings)
- Coroutines + Flow
- Apache POI (XLSX import/export)

## Directory Structure
- `app/src/main/java/com/example/bloodpressurerecord/data/db/entity`
- `app/src/main/java/com/example/bloodpressurerecord/data/db/dao`
- `app/src/main/java/com/example/bloodpressurerecord/data/datastore`
- `app/src/main/java/com/example/bloodpressurerecord/data/repository`
- `app/src/main/java/com/example/bloodpressurerecord/data/repository/transfer`
- `app/src/main/java/com/example/bloodpressurerecord/domain/model`
- `app/src/main/java/com/example/bloodpressurerecord/domain/calculator`
- `app/src/main/java/com/example/bloodpressurerecord/ui/home`
- `app/src/main/java/com/example/bloodpressurerecord/ui/history`
- `app/src/main/java/com/example/bloodpressurerecord/ui/settings`
- `app/src/main/java/com/example/bloodpressurerecord/navigation`

## Run
1. Open project in Android Studio.
2. Sync Gradle.
3. Select device/emulator (Android 8.0+).
4. Run `app`.

## Test
- Unit tests: run tests under `app/src/test`.
- Instrumentation tests: run tests under `app/src/androidTest`.

Required test areas are covered:
- average calculator
- category calculator
- high risk judge
- repository save/read
- import parser

## Data Entry
1. Open "新增测量".
2. Fill date/time, scene, and at least 2 readings.
3. Category is auto-calculated and read-only.
4. Save to Room; view/edit/delete in history.

## Import / Export
- Export CSV/XLSX from Settings -> Data Management.
- Files are written to private dir: `files/backup/`.
- Import CSV/XLSX from Settings -> Data Management.
- Current import reads latest file from `files/import/` first, then `files/backup/`.
- System file picker path is reserved by `stageImportFromUri(...)`.
- Import validates row-by-row, skips bad rows, and returns error statistics.

---

## Incremental Update Notes (Current)

### Release 1.0.4
- Home page now supports dynamic reading groups after group 2.
- Added actions for expanded mode: `收起第3组` + `添加下一组`.
- Dynamic groups are included in validation, average, category, and high-risk calculations.
- Edit flow supports loading/saving sessions with any number of readings.
- Settings is now a two-level menu with dedicated subpages.
- Existing import/export and repository architecture remain compatible.

### Dynamic Reading Groups
- Home page keeps the first 2 reading groups fixed.
- Tap `展开第3组` to open dynamic groups starting from group 3.
- In expanded state, both `收起第3组` and `添加下一组` are shown.
- Added groups (group 3, 4, 5...) are included in:
  - average calculation
  - category calculation
  - high-risk checking
- Collapse behavior: collapsing group 3 hides and clears all dynamic groups (3+), keeping data rules consistent.

### Edit Session Support
- Edit page now supports loading and editing any number of readings.
- Existing session readings are mapped as:
  - reading 1/2 => fixed slots
  - reading 3+ => dynamic list

### Settings Two-Level Menu
- Settings root page is now a menu page.
- Secondary pages are split into:
  - Profile
  - Reminder
  - Display
  - Data Management
  - Info
  - Disclaimer
- Existing DataStore/Repository logic is reused; functions remain available.

