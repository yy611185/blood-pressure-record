# 血压追踪（Android 本地版）

## 项目简介
这是一个面向家庭成员的血压记录应用，采用本地优先架构：
- 主数据使用 Room
- 设置项使用 DataStore
- 无登录、无服务器、无云同步

## 当前版本
- `versionName`: `1.1.4`
- UI 已按 Stitch 方案做增量落地（不改核心业务逻辑）

## 技术栈
- Kotlin
- Jetpack Compose + Material 3
- MVVM
- Navigation Compose
- Room
- DataStore
- Coroutines + Flow

## 本轮 UI 映射（13 页）
已映射到当前 Compose 工程的页面与状态：
1. 新增测量（正常态）
2. 新增测量（第3组展开 + 动态组）
3. 新增测量（异常输入态）
4. 新增测量（高风险提醒弹窗态）
5. 历史列表（日/周/月）
6. 历史详情（多组明细）
7. 历史空状态
8. 历史删除确认弹窗
9. 趋势页（7天/30天）
10. 趋势页（数据不足空状态）
11. 设置首页（一级菜单）
12. 设置二级页（用户资料/提醒/显示/数据管理）
13. 说明与免责声明页

## 双折线时间序列图（已实现）
- 7天 / 30天双折线
- 横轴：日期/时间（按每次测量点，不仅日平均）
- 纵轴：`mmHg`
- 两条线：`收缩压` / `舒张压`
- 参考线：`80`、`90`、`120`、`130`、`140`
- 图例与线尾都带文字标注，避免仅靠颜色区分

## 动态多组读数（保持业务不变）
- 默认显示第1组、第2组
- 展开第3组后可连续添加第4/5/...组
- 额外组参与平均值、分级与高风险判断
- 保存、历史详情、编辑链路均支持多组

## 如何构建
```bash
./gradlew :app:compileDebugKotlin
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleRelease
```

## APK 输出
- `app/build/outputs/apk/release/app-release.apk`
