# Android 应用内更新

本模块只负责 Android 客户端，正式 `applicationId` 为 `com.yang.bloodpressure`。健康记录功能仍可离线使用。开发本功能不增加 `versionCode`、不创建 Tag 或 Release，也不修改服务器、Cloudflare 和 GitHub 自动发布工作流。

## 架构与集成

代码位于 `app/src/main/java/com/example/bloodpressurerecord/update/`，沿用现有 MVVM、`AppContainer` 和 `AppViewModelFactory`。

| 组件 | 作用 |
|---|---|
| `UpdateManifest` 与解析器 | schema=1 模型、字段类型及地址验证 |
| `UpdateRepository` | 有界 HTTPS 请求与有效清单缓存 |
| `UpdatePolicy` | versionCode 比较、24 小时检查间隔、72 小时延后及跳过规则 |
| `UpdatePreferences` | 独立 DataStore 偏好、提醒时间及有效清单缓存 |
| `UpdateDownloadManager` | 应用范围的受控下载、网络监听、进度、暂停、续传和取消；内部 AtomicFile 保存任务元数据 |
| `UpdateVerifier` | 文件大小、SHA-256、包名、版本及平台签名身份预检 |
| `UpdateInstaller` | 安装来源授权和 FileProvider content URI 系统安装 |
| `UpdateViewModel` | 检查状态、下载状态和提醒显示协调 |
| `UpdateUi.kt` | 可滚动更新弹窗、更新设置及各下载状态操作 |

集成文件及其作用：

| 文件 | 改动 |
|---|---|
| `AppContainer.kt`、`AppViewModelFactory.kt` | 创建更新组件并注入 ViewModel，下载协调器保持应用范围 |
| `navigation/AppNavigation.kt` | 前台生命周期检查、安全页面门控、全局弹窗及安装操作接入 |
| `update/UpdatePlatformActions.kt` | 系统来源授权、安装器及用户主动打开 Release 的 Compose Activity Result 协调 |
| `update/UpdatePromptGate.kt`、`ui/settings/SettingsViewModel.kt` | 实际备份、导入、清理任务结束前持续阻止更新弹窗 |
| `ui/settings/SettingsSubpages.kt` | 在现有应用说明页嵌入更新设置、最新说明和下载进度 |
| `AndroidManifest.xml`、`res/xml/update_file_paths.xml` | 三项更新权限、禁用明文网络、仅暴露完成 APK 的 FileProvider |
| `app/build.gradle.kts` | 只增加轻量 JSON 解析库；版本号、签名与发布配置不变 |
| `README.md`、`ui/settings/AppInfoContent.kt` | 修订联网用途和更新服务日志的隐私说明 |
| `src/test/.../update/`、`src/test/resources/update/stable-schema1.json` | 固定清单、提醒策略与关键安全逻辑的 JVM 测试 |

检查状态和下载状态独立保存。一次检查失败不能清除已有下载进度或待安装文件。页面重建重新观察应用范围状态；进程重启从任务元数据和专用目录中的文件恢复。

自动弹窗只在允许的浏览页面出现。新增测量、编辑记录、设置编辑和数据管理页面暂缓弹窗；备份进行中由应用范围门控阻止自动弹窗，任务完成后再恢复提示。检查仍在后台协程执行，不阻塞医疗记录页面。

## 协议、安全与默认策略

版本清单固定为 `https://updates.0u0w0u0.com/android/stable/latest.json`。APK 地址仅接受同一可信主机的 `/android/stable/blood-pressure-record-v<version>-release.apk`。GitHub 备用地址只接受本仓库的 HTTPS Release 页面，由用户主动打开。

schema=1 的字段为 `schema`、`version`、`versionCode`、`apk`、`size`、`sha256`、`url`、`notes`、`releasesUrl`、`publishedAt`，以及可选的 `minSupportedVersionCode`。未知字段可忽略；未知 schema、错误字段类型、无效版本、无效摘要和非法地址均报协议错误，不能视为“已是最新”。`minSupportedVersionCode` 缺失或为 null 表示未设置必要更新；存在时必须是正整数且不超过最新 versionCode。

所有请求使用 HTTPS，拒绝重定向、用户信息、自定义端口、路径穿越及 APK 查询参数。连接超时 10 秒，读取超时 15 秒。JSON 最多读取 256 KiB，实际流读取有界，不依赖服务端 `Content-Length`。版本比较使用 `versionCode`。

| 设置或规则 | 默认行为 |
|---|---|
| 自动检查 | 开启；启动或返回前台检查，成功后 24 小时内不自动请求 |
| 手动检查 | 不受 24 小时、跳过版本和延后提醒限制 |
| 稍后提醒 | 延后目标 versionCode 72 小时；新版本不受旧延后影响 |
| 跳过版本 | 只跳过指定 versionCode；新版本恢复提醒 |
| 必要更新 | 优先于跳过、延后；可继续使用全部本地功能，关闭后同版本 24 小时内不重复自动弹窗 |
| 自动下载安装包 | 关闭；开启后也不会自动安装 |
| 仅 Wi-Fi 下载 | 开启；要求网络可用、Wi-Fi 且非计费；关闭后可使用移动及计费网络 |
| 自动检查失败 | 静默并短时退避；手动检查明确显示失败及重试入口 |

提醒策略与请求节流分开运行：延后到期时可以依据有效缓存提示，不必强行重复请求。

### 下载边界

本实现采用受控 HTTPS 下载，而非 Android `DownloadManager`。这样可以在客户端严格拒绝重定向、限制实际写入字节数并检查每次续传响应。取舍是退到后台暂停，返回前台续传，不提供长期后台服务。

下载任务身份包括 versionCode、URL、size 和 SHA-256。同一任务不重复下载，已完成文件再次验证后可复用。安装包与 `.part` 临时文件存入应用内部专用更新目录，不请求共享存储权限。

- APK 大小上限 256 MiB；开始及续传前检查剩余空间，额外保留 64 MiB。
- 每次写入前核对累计大小，超过声明大小或上限立即停止，防止异常响应耗尽存储。
- `206` 续传须匹配 `Content-Range`；`200` 表示重新开始，不能将完整响应附加到旧文件。
- 下载完成后大小必须精确一致，并重算完整 SHA-256。
- 网络中断、网络不满足 Wi-Fi 规则或应用退到后台时暂停，保留可恢复部分；返回前台且网络允许后续传。
- 进度约每 500 毫秒更新；取消删除临时文件并阻止同版本自动重启；安全校验失败删除文件。
- 更新说明作为纯文本显示，不执行 HTML、脚本或加载其中的资源。

### 安装边界

安装前必须满足：大小及摘要正确，包名为 `com.yang.bloodpressure`，候选 versionCode 与清单一致且高于已安装版本，非 Debug、非 testOnly，并且有平台可读取的签名信息。

当前正式应用的证书身份须为正式签名证书或其合法历史：

```text
46dfcbf4da054df8a8aca30e0df4df323161cbe4363a9eac7683a9da87d44012
```

Android 8.0/8.1 比较完整当前签名集合。Android 9 及以后允许相同当前签名身份，或候选的合法轮换历史包含已安装应用当前证书；仅有历史交集不够。archive 查询兼用 `GET_SIGNATURES` 与 `GET_SIGNING_CERTIFICATES`，覆盖早期平台兼容问题。

这一步称为“平台签名和身份预检”。**系统安装器负责最终密码学签名验证及覆盖安装裁决**；不能把远程清单中的 SHA-256 当作独立的开发者身份认证，也不能仅凭 APK Signing Block 存在宣称真实性已验证。

FileProvider 只暴露已完成 APK 子目录，向系统安装器授予临时读取权限并使用 content URI。安装来源未授权时，用户主动进入系统设置；返回后重新检查授权及安装包。用户取消系统安装保留待安装状态，不推断安装成功、不卸载旧版本。没有静默安装、Root 安装或绕过系统验证的路径。

## 状态流转与权限

检查：`空闲 → 检查中 → 已是最新 / 发现更新 / 检查失败`。

下载：`等待网络 → 下载中 → 暂停或失败 → 续传或重试 → 校验中 → 待安装`。用户点击安装后进入来源授权或系统安装器。关闭弹窗保留下载任务；延后、跳过或关闭自动下载会暂停自动任务，手动开始的任务继续遵循前台及网络条件。“取消下载”删除临时文件，“删除安装包”删除待安装文件。成功覆盖升级后，下次启动清理已安装版本的旧更新文件。

| Android 权限 | 原因 |
|---|---|
| `INTERNET` | HTTPS 版本检查和 APK 下载 |
| `ACCESS_NETWORK_STATE` | 网络中断、Wi-Fi 和计费网络策略 |
| `REQUEST_INSTALL_PACKAGES` | 请求系统安装器处理用户确认的升级；授权由系统管理 |

禁用明文网络，不增加存储权限、前台服务权限或长期后台服务。既有提醒和日历权限用途不变。

## 用户数据与隐私

更新模块不依赖健康 Repository，不读取或上传血压测量、症状备注、用药数据、个人资料、Excel 或 BPX 文件。更新设置与清单保存在独立 DataStore，下载任务元数据保存在内部专用目录的原子文件，不修改 Room、`AppSettings` 和健康备份格式。健康记录、历史、提醒和备份仍可离线使用。

更新请求会让更新服务及 Cloudflare 获得 IP 地址、请求时间、请求路径和基本请求日志。请求不携带健康记录，不使用账号体系或健康数据云同步。用户可以关闭自动检查和自动下载，手动检查及下载仍会联网。用户主动导出的备份由其选定的 Android 文件提供方处理，更新模块不会读取这些文件。

## 验证与首次上线

### 开发检查

仅使用本地固定 JSON、内存输入流和模拟的范围响应头测试，不向正式服务发布测试清单或 APK。关键 JVM 测试覆盖清单类型/schema/地址、可选最低版本、24/72 小时边界、跳过与必要更新、超限写入、续传范围、摘要、包名、版本、签名集合及轮换方向。没有将真实 Android 网络回调或系统安装器包装为 JVM 已验证结果。

2026-10-11，在最新 main `0b27413` 基线上使用 JDK 17 与 Android Build Tools 36.0.0 执行：

```powershell
.\gradlew.bat --no-daemon :app:testDebugUnitTest --tests 'com.example.bloodpressurerecord.update.*' :app:assembleDebug :app:assembleRelease :app:lintDebug --console=plain
```

| 检查 | 实际结果 |
|---|---|
| 更新包 JVM 测试 | 22 项通过，0 失败、0 错误：解析 8 项、策略 6 项、安全 8 项 |
| `assembleDebug` | 通过 |
| `assembleRelease` | 通过，包含 R8 与资源压缩 |
| `lintDebug` | 通过，0 错误、40 警告、2 提示 |
| `apksigner verify --print-certs` | 通过，证书 SHA-256 与固定正式证书一致 |
| `git diff --check` | 通过 |

Lint 非阻断项包含既有依赖版本、组件及资源提示，以及本模块保守使用实际可用空间的建议；没有为了本功能升级其他依赖或批量修复无关代码。首轮 Lint 的新增缩进错误已修复。报告位于 `app/build/reports/tests/testDebugUnitTest/` 和 `app/build/reports/lint-results-debug.html`，均为本地构建输出，不提交到仓库。

本机已有正式签名环境，构建使用仓库现有配置；`apksigner` 验证的证书 SHA-256 与上述固定正式身份一致。构建不上传、不创建 Release，版本仍为 2.3.2 / 43。未配置正式签名环境的其他开发机仍生成 unsigned Release，绝不回退到 Debug 签名。Debug 包可与正式版并存，但不能作为正式更新身份验证的成功证据。按本次要求不运行模拟器、仪器测试或性能基准。

开发检查时线上 `latest.json` 为 404，服务端文档未包含完整清单生成代码。当前解析依据已约定 schema=1 与固定样本；首次正式发布后必须核对真实 JSON。404、超时、断网或协议错误均应允许应用正常使用。

### 真机验证：发布前

1. 在自己的 Android 手机上运行客户端，确认服务器 404、断网及超时时仍能录入、编辑、浏览、提醒和备份；手动检查显示失败，不显示“已是最新”。错误清单用固定 JVM 样本及独立本地测试环境验证，不改动生产清单。
2. 验证深浅色、小屏、系统字号和应用大字模式；长说明可以滚动，所有按钮可以到达，TalkBack 可以识别检查、开关和下载进度。
3. 改动三个更新开关，重建页面、退出再打开，确认持久化。开始录入、编辑或执行备份，确认自动提示不会打断操作。

### 真机验证：首版 A

1. 用户另行批准正式发布后，由现有签名工作流生成 A。已有旧版本没有更新模块，必须先从 GitHub Release 或可信分发入口手动下载 A，并以相同正式签名覆盖安装；不要卸载旧版本。
2. 升级前保留一份用户主动导出的备份并记下记录数量、资料、提醒与用药打卡；升级后逐项核对，再验证导出和恢复。
3. 核对 A 的包名、签名、版本号、线上真实 schema=1 清单、SHA-256 和版本化地址。A 请求指向自身版本时显示“已是最新”。
4. 验证默认只自动检查，不自动下载；网络不可用时应用仍正常使用。

### 真机验证：第二版 B 覆盖 A

1. 用户另行批准发布更高 versionCode 的 B，仍使用正式签名和现有发布流程。A 自动检查应发现 B；延后和跳过 B 后手动检查仍能显示更新，新版本应解除旧延后和跳过。
2. 在 Wi-Fi、计费 Wi-Fi、移动网络及断网间切换，核对仅 Wi-Fi 开关、暂停与恢复；下载途中退后台、返回前台、旋转、重建页面和结束进程后重启，检查进度恢复及避免重复任务。
3. 验证取消、重试、存储不足及损坏包拒绝；下载完成不自动打开安装器。首次安装点击应进入安装来源授权，拒绝后保留本地功能，允许后可重新安装。
4. 在系统安装器取消，回到 A 确认文件仍待安装；再次点击并确认，系统应覆盖升级到 B。升级后核对记录、资料、提醒、打卡、Excel/BPX 导出恢复和旧更新文件清理。
5. 必要更新提示必须允许继续离线使用。签名轮换、错误证书、无签名、Debug 包、降级包和篡改包在独立本地测试环境验收，不能投放到生产清单或更新服务。

### 尚待实机验收

线上首份清单兼容性、不同 Android/OEM 的安装来源和取消行为、合法签名轮换、首版 A 与第二版 B 的正式签名覆盖升级，均需真实手机验证。客户端编译及 JVM 测试不能替代这些结果。
