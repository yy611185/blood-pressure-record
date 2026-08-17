# 发布清单（Publishing Guide）

> 面向**维护者**（作者本人）的发布操作手册。从构建到上架，每个步骤按顺序执行。
> 最后更新：2026-08 · 适用版本：1.8.x

---

## 0. 适用范围

> **当前状态（2026-08）：未上架任何应用商店**，仅通过 GitHub Releases 自签名 APK 分发。以下路径 A 为未来上架 Play 时的流程预留，目前实际走路径 B。

本文档覆盖两条发布路径：

| 路径 | 适用场景 | 产物 |
|---|---|---|
| **A. Google Play 上架** | 公开分发 | 签名 AAB |
| **B. 自签名 APK 分发** | 亲友长期使用（不经过商店） | 签名 APK |

两条路径**共用同一套 release 签名**，不允许混用（详见 §4 警告）。

---

## 1. 构建发布包

### 1.1 前置条件

- Android Studio Koala+ / JDK 17 / Android SDK 35 / Gradle 8.7
- 本机已配置 release 签名环境变量（见 1.2）
- 干净的 git 工作区（`git status` 无未提交改动）

### 1.2 签名环境变量

Release 构建通过环境变量注入签名，**未配置时产出 unsigned 包，绝不回退 Debug 签名**：

```bash
export BP_RELEASE_STORE_FILE=/absolute/path/to/release.jks
export BP_RELEASE_STORE_PASSWORD='<store密码>'
export BP_RELEASE_KEY_ALIAS=bloodpressure
export BP_RELEASE_KEY_PASSWORD='<key密码>'
```

密钥文件与密码的保管要求：

- `release.jks` **只存在你的本机**，用 `base64 -i release.jks | tr -d '\n'` 转码后存入 GitHub Secret `KEYSTORE_BASE64`（仅 CI 构建用）
- 密码至少 16 位，与 GitHub Secret（`KEYSTORE_PASSWORD` / `KEY_ALIAS` / `KEY_PASSWORD`）一致
- **离线备份密钥库文件 + 密码**（加密压缩包存两处），丢失 = 永远无法更新已发布应用

### 1.3 构建命令

```bash
./gradlew clean
./gradlew assembleRelease     # 出 APK：app/build/outputs/apk/release/app-release.apk
./gradlew bundleRelease       # 出 AAB：app/build/outputs/bundle/release/app-release.aab
```

### 1.4 产物校验（必做）

```bash
# APK：校验签名 + 校验是否使用 release 密钥（而非 debug）
apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk
# 期望输出 CN=... 与 release 密钥一致；出现 "debug" 字样即失败

# AAB：校验签名
jarsigner -verify app/build/outputs/bundle/release/app-release.aab
```

**验证签名密钥的黄金命令**（防"用错密钥发布"事故）：

```bash
keytool -printcert -jarfile app-release.apk | grep -E "Owner|SHA256"
```

---

## 2. 版本管理

| 项 | 当前值 | 规则 |
|---|---|---|
| `versionCode` | 28 | 每次发布**必须递增**（整数，只增不减） |
| `versionName` | 1.8.1 | 语义化版本，随功能/修复调整 |
| `applicationId` | `com.yang.bloodpressure` | **首次正式分发后永不再改**——改动会被 Android 视为全新应用，旧用户无法覆盖升级 |

修改位置：`app/build.gradle.kts` → `defaultConfig`。

发布节奏建议：修复版本递增 `versionName` 末位，功能版本递增中位，大改递增首位；`versionCode` 无条件 +1。

---

## 3. Google Play 上架清单（路径 A）

### 3.1 账号与身份

- [ ] Play Console 开发者账号有效（25 美元注册费已付）
- [ ] 开发者账号与 GitHub 账号解耦管理（独立邮箱/2FA）
- [ ] 应用名与图标符合商店规范（无医疗暗示、无夸大表述）

### 3.2 应用声明（易漏项，重点检查）

- [ ] **隐私政策 URL**：可公开访问、非 PDF、非地域限制（应用内设置页同步提供）
  - 必须如实说明：① 本地健康数据存储方式 ② READ/WRITE_CALENDAR 权限的用途与处理 ③ 用户主动导出的 Excel 文件去向（用户所选位置，可能含云盘）
  - ⚠️ 导出文件为**明文**，隐私政策需明示这一事实
- [ ] **Health apps declaration**：本应用至少涉及"疾病/状况管理"与"用药/治疗管理"，按实际情况勾选并如实回答后续问题
- [ ] **Data safety 表单**：即使应用不上传、不共享任何数据，**也必须提交声明**（全部选"不上传/不共享"）
- [ ] 目标受众与内容分级：健康类应用按问卷如实填写（含医疗内容提示）

### 3.3 构建与签名

- [ ] 使用 §1 流程构建的 **release 签名 AAB**（`bundleRelease` 产物）
- [ ] 上传到 Play Console 时**保管好上传密钥**（Play App Signing 的密钥材料）
- [ ] 确认 Play App Signing 启用后，`versionName` 与商店页面文案一致

### 3.4 发布前验证（测试轨道，必做）

先在 **内部测试轨道** 推送，再用 **封闭测试轨道** 邀请 2-3 台真实设备验证：

- [ ] **提醒**：晨/晚提醒触发、精确闹钟授权流程、拒绝授权后的非精确回退、关→开通知权限后提醒是否恢复（已知问题 M2：当前版本需重进设置页，升级前注意）
- [ ] **日历授权**：开启/关闭"写入系统日历"、授权/拒绝/撤回后行为、重复日程检查
- [ ] **导入导出**：导出 v3 → 换机导入 → 数据逐项核对（平均值策略、症状、备注）；旧 v2 文件导入兼容性
- [ ] **数据库升级**：从上一个已发布版本（如 1.8.0）覆盖安装，验证 v6→v7 迁移无数据丢失
- [ ] **系统导航**：三键导航 / 全面屏手势、深色模式、大字模式、无障碍（TalkBack 关键路径）

全部通过后：

- [ ] 在生产轨道上传 AAB，填写更新说明（引用 RELEASE_NOTES.md 对应版本段落）
- [ ] 发布后 24h 内检查崩溃报告（Play Console → Android vitals）

---

## 4. 自签名 APK 分发清单（路径 B）

适用：不通过 Play 商店，直接把 APK 发给家人/亲友。

- [ ] 用 §1.3 的 `assembleRelease` 产物（**不是** debug 包，也**不是** Play 用的 AAB）
- [ ] 首次分发时向对方说明：这是自签名应用，安装需允许"未知来源"，后续更新**必须用同一把密钥签名**的 APK 才能覆盖安装
- [ ] 分发文件命名建议：`blood-pressure-v1.8.1.apk`（含版本号，避免混淆）
- [ ] 分发渠道：网盘 / 微信传输（**注意**：导出的健康数据 Excel 是明文，APK 本身不含用户数据，可放心传）
- [ ] 升级分发：直接发新版本 APK，用户覆盖安装即可（签名一致时数据保留）

> ⚠️ **红线：路径 A 与 B 不要混用签名**
> 同一个 `applicationId` 若先用自签名 APK 分发、后又上架 Play（或反之），两者签名不同，用户无法覆盖升级，会被迫卸载重装丢数据。选定一条路径后保持一致；如需切换，走"先上架 Play 再迁移用户"的流程。

---

## 5. 发布后冒烟检查（发布当天）

- [ ] 全新安装最新版，走一遍：记一次血压 → 看分级 → 看趋势 → 导出 Excel
- [ ] 从上一版本覆盖升级（保留数据路径），确认数据完整
- [ ] 检查 Play Console 崩溃报告无新增异常
- [ ] 确认 README 版本号、RELEASE_NOTES.md、商店文案三者一致

---

## 6. 常见错误速查

| 症状 | 原因 | 处理 |
|---|---|---|
| `apksigner` 显示 debug 证书 | 环境变量未生效就构建 | 重设 §1.2 变量，clean 后重建 |
| 用户无法覆盖安装 | 前后签名不一致（换过密钥/混用路径） | 只能卸载重装（丢数据）→ 从根上避免 |
| AAB 上传被拒（versionCode 已存在） | versionCode 未递增 | +1 后重新构建 |
| Play 审核问"为什么要日历权限" | 隐私政策未说明 | 补 §3.2 的日历权限说明后重新提交 |
| 商店提示缺少隐私政策 | URL 不可访问/PDF/地域限制 | 换成公开网页（如 GitHub Pages） |

---

*本文档与 README、RELEASE_NOTES.md 同步维护；发布动作以本文档为准。*
