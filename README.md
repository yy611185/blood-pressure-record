# 血压追踪（Android，本地优先）

## 项目简介

本项目是一个面向家庭成员，尤其是中老年人的血压记录 Android 应用。核心目标是本地可用、简单可靠、易备份迁移。

- 不接入服务器
- 不做登录系统
- 不做云同步
- 主数据以 Room 为准
- 设置页使用 DataStore

## 最新版本

**v1.4.4** (2026-05-03)

### 更新内容

- **修复 Excel 导出空白文件问题**
  - 修正 Excel 写入顺序，先写入 workbook 再刷新输出流。
  - 固定写入模板中的“使用说明”“测量记录”“用户资料”“导出信息”四个工作表。
  - 表头写入改为复用现有单元格并覆盖标准列名，保持模板结构稳定。
  - 无历史记录时继续显示诊断提示，避免生成看似成功但没有测量数据的备份文件。

### 下载地址

- [blood-pressure-record-v1.4.4-release.apk](./blood-pressure-record-v1.4.4-release.apk)

## 功能特性

### 核心功能

- **新增测量**：支持一次记录多组收缩压、舒张压和脉搏，并自动计算平均值和血压分级。
- **历史记录**：查看、编辑、删除历史测量记录。
- **血压趋势**：7 天 / 30 天 / 全部趋势图表，支持参考线和数据点详情。
- **设置管理**：用户资料、提醒设置、显示偏好、数据管理。

### 数据导出

- 支持 Excel (.xlsx) 格式导出。
- 导出文件包含使用说明、测量记录、用户资料和导出信息。
- 文件仅保存到用户选择的位置，不上传服务器。
- 无可导出历史记录时会给出诊断提示，不生成空白备份文件。

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
│   │   └── backup/       # Excel 导出
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

- Android Studio Hedgehog 或更高版本
- JDK 17
- Android SDK 34
- Gradle 8.7

### 构建命令

```bash
# Debug 版本
./gradlew assembleDebug

# Release 版本
./gradlew assembleRelease
```

## 使用说明

### 新增测量

1. 打开“测量”标签页。
2. 填写日期、时间、场景。
3. 输入至少 2 组血压读数：收缩压、舒张压、脉搏。
4. 系统自动计算平均值和血压分级。
5. 点击保存。

### 导出数据

1. 进入“设置” -> “数据管理”。
2. 点击“导出为 Excel”。
3. 选择保存位置。
4. 导出文件包含使用说明、测量记录、用户资料和导出信息。

## 版本历史

| 版本 | 日期 | 主要更新 |
|------|------|---------|
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
./gradlew test

# 界面测试
./gradlew connectedAndroidTest
```

测试覆盖：

- 平均值计算器
- 血压分级计算器
- 高风险判断
- Repository 保存/读取
- Excel 导出写入

## 隐私声明

- 所有数据仅保存在您的手机本地。
- 不上传任何服务器。
- 不收集任何用户信息。
- 导出文件仅保存到您选择的位置。

## 许可协议

MIT License

## 作者

[yangabcxyz](https://github.com/yy611185)
