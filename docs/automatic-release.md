# Android 自动补丁版本发布

main 分支的每次新代码 push（包括合并 PR）先通过原有单元测试、Lint 和 Debug 编译。成功后，`prepare_release` 从 GitHub 最新已发布的稳定 Release 中读取版本，按以下原则处理：

- 当仓库版本与最新稳定版一致且有新提交：自动将 `versionName` 的 Patch +1，并将 `versionCode` +1，生成单独的 `chore(release)` 提交并推送到 main。
- 当开发者明确将两个版本字段提升到高于稳定版：保留手动指定的版本，不重复提升。
- 当版本倒退、仅一个字段递增、最新稳定 Tag 不是 main 的祖先，或者 main 在作业期间发生不符合预期的前进：拒绝发布，避免覆盖标签或错误版本。
- 当重试作业遇到它自己的单文件版本提交：复用该提交；GitHub Token 产生的提交不会再触发新的 push 工作流，当前运行使用版本提交 SHA 继续发布。

原有 `release_ci.py` 仍严格检查历史 Tag、APK/AAB 签名证书、版本号、zipalign、SHA-256；然后创建 GitHub Release 并通过现有 SSH 工具将签名 APK 发布到 `updates.0u0w0u0.com`，服务端更新 `/android/stable/latest.json`。签名失败不会推送生产更新清单。Android APP 比较远端 `versionCode`，提示用户下载并通过系统安装器完成升级，不提供静默安装。

普通开发/文档变更若直接合并 main 也会触发 Patch 发布；无需发布的改动应保留在功能分支，不直接提交 main。较大版本由开发者同时手动提升 `versionName` 和 `versionCode`。GitHub Actions 的 CI 失败不会改动版本，签名或服务器部署失败需检查对应运行日志，禁止通过移动旧 Tag 恢复。

初次回归验收：在 Android 手机先安装 v2.3.3，再通过 v2.3.4 清单检查检测、下载、SHA-256 校验、系统确认安装以及旧测量与提醒保留情况；自动化构建测试不能代替真机覆盖升级验收。
