# Android 软件更新

App 保留版本检查与更新提醒，将 APK 下载交给外部浏览器，再由 Android 系统完成覆盖安装。正式 `applicationId` 仍为 `com.yang.bloodpressure`，本次不调整版本号、正式签名、Room、Excel/BPX 备份格式，也不创建 Tag 或 Release。健康记录、历史、提醒、用药和备份仍可离线使用。

## 更新流程

1. 启动或返回前台时，按自动检查偏好和请求间隔获取 `latest.json`；用户也可从“我的 → 应用说明与更新说明”手动检查。
2. 按实际安装的 `versionCode` 比较版本，显示最新与当前版本、发布时间和纯文本更新内容。录入、编辑、备份等操作期间暂缓自动弹窗。
3. 点击“前往浏览器下载更新”，验证清单中的 `url` 后通过 `Intent.ACTION_VIEW` 打开外部浏览器。成功打开后关闭弹窗，将该版本提醒延后 72 小时并保存在本机；立即返回或重启不会重复自动提示，手动检查与更高版本提示继续有效。
4. 浏览器负责下载。用户打开下载的 APK，按 Android 系统提示覆盖安装，无需卸载旧版或清空数据。安装来源许可由浏览器和系统处理。
5. 打开浏览器不代表下载或安装成功。App 重新启动后仍按实际安装的 `versionCode` 判断更新状态，不记录“安装成功”的推测状态。

检查失败显示失败及重试入口，不能显示“已是最新版本”。固定的 [GitHub Releases 备用下载](https://github.com/yy611185/blood-pressure-record/releases) 不依赖清单请求成功，用户可随时主动访问。

## 架构与集成

代码位于 `app/src/main/java/com/example/bloodpressurerecord/update/`，沿用 Jetpack Compose、Material 3、MVVM、`AppContainer` 与 `AppViewModelFactory`。

| 组件 | 作用 |
|---|---|
| `UpdateManifest` 与解析器 | 保留 schema=1 兼容性、字段类型及可信地址验证 |
| `UpdateRepository` | 有界 HTTPS 元数据请求、有效清单缓存及检查失败处理 |
| `UpdatePolicy` | versionCode 比较、24 小时请求间隔、72 小时延后、跳过与必要更新策略 |
| `UpdatePreferences` | 独立 DataStore：自动检查、检查时间、提醒偏好与有效清单缓存 |
| `UpdateViewModel` | 检查状态、提示协调与外部下载后的同版本提醒抑制 |
| `UpdateUi.kt` | 可滚动更新弹窗、自动检查开关、手动检查及备用下载入口 |
| `UpdatePlatformActions.kt` | 外部浏览器打开结果与失败提示，不调用内部安装器 |
| `UpdatePromptGate.kt` | 录入、编辑、备份等关键操作的弹窗避让 |
| `LegacyUpdateCleanup` | 仅清理旧更新模块的内部专用目录 |

`navigation/AppNavigation.kt` 保留前台生命周期检查及全局提示门控；现有应用说明页嵌入精简后的更新设置。更新失败不改变健康页面的可用性，不读取健康 Repository。

本次修改更新核心、`AppContainer.kt`、`AppViewModelFactory.kt`、`BloodPressureApplication.kt`、`AppNavigation.kt`、`SettingsSubpages.kt`、`AppInfoContent.kt` 和 `AndroidManifest.xml`，同步 README、更新说明与隐私声明；新增 `LegacyUpdateCleanup.kt` 及清理、ViewModel 测试。

删除 `UpdateDownloadManager.kt`、`UpdateDownloadSafety.kt`、`UpdateHttpDownload.kt`、`UpdateVerifier.kt`、`UpdateInstaller.kt`、`UpdateInstallState.kt`、`update_file_paths.xml`，以及 `UpdateDownloadSafetyTest.kt`、`UpdateHttpDownloadTest.kt`、`UpdateInstallStateTest.kt`。进度、下载任务、续传、安装来源授权和内部 APK FileProvider 的调用已移除。自动下载与仅 Wi-Fi 下载设置不再显示。

## 协议与可信地址

元数据接口保持：

```text
https://updates.0u0w0u0.com/android/stable/latest.json
```

主按钮必须使用经过严格验证的 `manifest.url`，不写死 APK 版本号或服务器 IP。允许的主下载地址格式为：

```text
https://updates.0u0w0u0.com/android/stable/blood-pressure-record-v<version>-release.apk
```

验证 HTTPS、精确域名、无显式端口、目录和与清单版本一致的 APK 文件名；拒绝用户信息、自定义端口、查询参数、片段、路径穿越、编码绕过及非可信主机。元数据 HTTPS 请求拒绝重定向，连接超时 10 秒、读取超时 15 秒，JSON 实际读取上限 256 KiB。

schema=1 保留 `schema`、`version`、`versionCode`、`apk`、`size`、`sha256`、`url`、`notes`、`releasesUrl`、`publishedAt`，以及可选 `minSupportedVersionCode`；兼容可选的 `mirrors` 字符串数组。保留现有协议字段校验和有效清单缓存，不再使用大小或摘要执行客户端下载校验。未知字段可忽略，未知 schema、错误字段类型或非法地址属于检查失败。最低支持版本缺失或为 null 表示没有必要更新要求；存在时必须是正整数且不超过清单版本。

`https://dl.0u0w0u0.com:8443` 仅是可选的外部下载备用渠道：仅当清单 `mirrors` 显式提供该地址且独立校验 HTTPS、精确域名、8443 端口及相同目录、文件名通过后显示备用入口。未提供时不自动拼接该地址。不得将其替换进现有清单 `url`，以免旧客户端白名单拒绝。客户端不依赖服务器 IP。

GitHub 最终备用入口固定为本仓库的 HTTPS Releases 页面，不接受清单将其改到其他仓库或任意网页。打开网页后，后续下载和安装由浏览器及系统负责，App 无法证明下载或安装已完成。

2026-10-18 只读核对线上接口：schema=1、v2.3.4、versionCode 45。当前源码为 2.3.5 / 46；线上版本是观察结果，不写入客户端，也不表示本次已发布。本次不需要改动服务器、Cloudflare、GitHub Secrets、CI/CD 或发布密钥；继续保留 `/android/stable/` 协议。

## 提醒、缓存与界面

| 规则 | 行为 |
|---|---|
| 自动检查 | 默认开启；成功检查后 24 小时内不重复自动请求 |
| 手动检查 | 不受 24 小时、跳过版本、延后提醒或外部下载提醒抑制限制 |
| 72 小时后提醒 | 针对目标 versionCode 延后；更高版本不受影响 |
| 跳过此版本 | 仅跳过目标 versionCode；更高版本恢复提醒 |
| 必要更新 | 保留必要更新提醒，可延后 72 小时并继续全部本地功能，不阻止离线使用 |
| 已打开浏览器 | 关闭当前弹窗并将目标版本延后 72 小时，不标记安装成功 |
| 自动检查失败 | 保留有效缓存与既有短时退避；不打断健康功能 |
| 手动检查失败 | 明确显示失败与重试入口，保留 GitHub 备用入口 |

请求节流与提醒策略独立运行，延后到期可依据有效缓存提示。检查失败不能用缓存伪装成本次成功或“已是最新版本”。

弹窗和应用说明页沿用 Material 3、深色模式和大字支持。更新内容可滚动，操作按纵向布局适配小屏，按钮保留可读文字及无障碍语义。自动提示继续避让测量录入、编辑、设置编辑和数据管理；实际备份等任务结束前由应用范围门控持续阻止弹窗。

## 权限、旧文件与隐私

更新模块仅使用 `INTERNET` 请求版本元数据，禁用明文网络。移除 APK 安装专用的 `REQUEST_INSTALL_PACKAGES`、FileProvider 及路径配置；无其他业务依赖时移除下载网络策略专用权限。不增加第三方依赖、存储权限或后台服务。

已删除应用自身的 `ACCESS_NETWORK_STATE` 声明；合并后的 Manifest 仍由既有 `androidx.work:work-runtime:2.7.1` 声明此权限，保留现有小部件使用的 WorkManager 依赖。医生报告的 FileProvider 保留。

旧下载文件清理严格限制为应用内部 `filesDir/updates` 和 `cacheDir/updates` 旧更新专用目录，包含旧 `.part`、完成 APK 与任务元数据。清理须校验 canonical 路径且不跟随符号链接，不能递归删除用户选择的目录、数据库、DataStore、报告、Excel/BPX 或其他健康数据。

App 不读取或上传血压、症状备注、用药、个人资料、Excel/BPX 文件。更新偏好和有效清单保存在独立 DataStore，不修改健康数据库或备份格式。

元数据请求可能让更新服务及 Cloudflare 获得 IP 地址、时间、路径和基本请求日志。用户可以关闭自动检查；手动检查仍会联网。主动打开下载或 GitHub 入口后，浏览器访问相应服务并可能产生访问、下载日志；App 不向外部入口传递健康数据，不管理 APK 下载、校验或安装授权。

## 开发验证

本次仅执行必要的更新模块 JVM 测试、静态检查与 Debug 构建。自动测试使用固定 JSON、内存输入流、真实本地 DataStore，以及模拟的元数据返回和浏览器打开回调；浏览器 Intent 的实际启动留待实机验收。不修改生产清单或投放测试 APK。

```powershell
.\gradlew.bat --no-daemon :app:testDebugUnitTest --tests 'com.example.bloodpressurerecord.update.*' --tests 'com.example.bloodpressurerecord.ui.settings.AppReleaseNotesTest' :app:lintDebug :app:assembleDebug --console=plain
git diff --check
```

| 检查 | 本次结果 |
|---|---|
| 更新模块 JVM 测试 | 35 项：33 通过、0 失败、2 跳过；另有应用更新说明测试 1 项通过 |
| `lintDebug` | 通过，0 errors、35 warnings、2 hints；本次更新模块无 lint 问题，其他提示涉及既有依赖、资源和其他模块 |
| `assembleDebug` | 通过；产物 `app/build/outputs/apk/debug/app-debug.apk`，使用既有 `.debug` 包名后缀 |
| `git diff --check` | 通过 |
| 线上只读检查 | 清单 v2.3.4 / 45；清单主 APK 和固定 GitHub Releases 页面均返回 HTTP 200，未下载 APK |

ViewModel 的 11 项测试全部通过，包含真实 DataStore 写入、磁盘重建、在途手动请求和浏览器回调的竞争、失败缓存及弹窗门控。缓存清理的目录边界与幂等测试通过；2 项符号链接测试因本机创建链接受限而跳过，未将其计为通过。未变更生产环境、发布配置、版本号或签名机制。

应覆盖清单与 versionCode、主下载 URL 和固定 GitHub 入口、非法 URL 拒绝、24/72 小时边界、跳过及必要更新、成功打开浏览器后的同版本抑制、手动检查及更高版本、打开失败不误记成功，以及旧目录清理边界。自动化测试不能替代真实浏览器、系统安装器与 OEM 行为的实机验收。

测试报告位于 `app/build/reports/tests/testDebugUnitTest/` 和 `app/build/reports/lint-results-debug.html`，均为本地构建输出。本次不运行模拟器、仪器测试、Release 构建或正式发布。

## 待实机验收

1. 检查实际已安装版本与线上清单的比较；若当前正式 App 已高于线上清单，不应提示降级。更高版本场景使用独立测试环境，不修改生产接口。
2. 在有浏览器、无可用浏览器及用户取消选择器等场景验证打开结果。确认主入口指向清单中的真实可信 APK，GitHub 备用入口在断网或检查失败时仍可发起打开。
3. 成功打开浏览器后立即返回或重启，确认相同版本不自动重弹；手动检查仍可提示，更高版本不受旧抑制、跳过或延后影响。
4. 断网、超时或错误清单时，手动检查显示失败；录入、编辑、历史、提醒、用药和备份仍可使用，关键操作中不会出现自动更新弹窗。
5. 验证深浅色、小屏、系统大字体、应用大字模式和 TalkBack，确保版本、发布时间、长更新内容与所有按钮可读可达。
6. 从旧版本保留的内部更新目录升级后，确认只清理旧更新文件，记录、用户资料、提醒、用药和 Excel/BPX 均保留。
7. 使用相同正式签名与更高 versionCode 的 APK，经浏览器下载并由系统覆盖安装；安装前后核对健康记录、资料、提醒、用药及备份恢复。不要卸载旧版或清空数据。用户取消下载或安装后，App 应继续按实际已安装版本判断状态。

以上实机项目尚未执行。本次不自动提交、发布或调整生产环境，后续正式覆盖升级由用户安排验收。
