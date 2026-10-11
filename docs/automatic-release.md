# main 自动发布

`.github/workflows/build-release.yml` 在 PR 上只运行 CI；`main` 的 push 在 CI 成功后执行发布检测。标签 push 不触发此工作流。标签创建、正式签名产物构建、GitHub Release 发布都在同一个工作流内完成，不依赖 `GITHUB_TOKEN` 创建标签后触发另一个工作流。

## 版本与触发规则

在 `app/build.gradle.kts` 同时提交新的 `versionName` 和递增的 `versionCode`。`versionName` 必须是没有前导零的 `X.Y.Z`，`versionCode` 必须是 `1..2100000000` 的整数，两项都必须是唯一的字面量赋值。

控制器按数值比较三段版本号，要求两项版本都高于历史版本标签和已发布 Release 的源版本；还会检查本次 push 的 `before` 提交，首次 push 没有有效 `before` 时检查第一父提交。checkout 使用 `fetch-depth: 0`，以便读取这些提交。恢复当前版本时排除当前标签自身，避免把重跑误判为版本未递增。

| 远端状态 | 结果 |
| --- | --- |
| 新版本，尚无对应标签或 Release | 构建并验证后创建标签、草稿 Release，上传并核验四个附件，最后正式发布 |
| 对应标签指向本次提交，且正式稳定 Release 的四个附件均为非空、`uploaded` | 成功跳过，日志和 Actions summary 显示原因，不修改 Release |
| 对应标签指向本次提交，但没有 Release，或 Release/草稿缺少附件 | 验证后恢复，只补缺附件 |
| 对应标签指向其他提交 | 报错，既不覆盖也不移动标签；即使已有完整 Release，也先报错 |
| 版本未变化，且没有对应远端标签/Release | 成功跳过，显示“不是新版本提交” |
| 有 Release 但对应标签缺失、同名附件重复、已有附件状态异常、版本下降或查询失败 | 报错，停止发布 |

标签冲突规则是严格的：普通 `main` 提交若仍使用已发布版本、但对应标签指向旧提交，也会报错。提交一个同时递增名称和编号的新版本才会开始新的发布。现有 prerelease 不会自动转换为正式版。

## 签名、附件与恢复

保留现有四个 Actions Secrets：`ANDROID_KEYSTORE_BASE64`、`ANDROID_KEYSTORE_PASSWORD`、`ANDROID_KEY_ALIAS`、`ANDROID_KEY_PASSWORD`。正式签名证书 SHA-256 指纹保持为：

```text
46dfcbf4da054df8a8aca30e0df4df323161cbe4363a9eac7683a9da87d44012
```

APK 必须通过 `apksigner verify`、单一正式签名证书、包名/版本和 `zipalign` 检查。AAB 必须通过 JAR 签名验证，并由 `scripts/VerifyReleaseBundle.java` 逐项读取所有载荷，确认每个载荷只有一个正式证书签名；无签名、部分签名、Debug 签名或损坏产物不会发布。构建不运行模拟器或仪器化测试，CI 保留必要的 JVM 验证和发布控制器模拟测试。

四个 Release 附件名称保持不变：

- `blood-pressure-record-vX.Y.Z-release.apk`
- `blood-pressure-record-vX.Y.Z-release.aab`
- `release-sha256.txt`
- `release-verification.txt`

`release-sha256.txt` 继续采用 `sha256sum` 格式，文件路径分别为 `app/build/outputs/apk/release/...apk`、`app/build/outputs/bundle/release/...aab`。校验文件和报告只有在构建与签名验证成功后生成。

重跑不完整发布时，`restore-packages` 下载已经上传的 APK/AAB 到本地构建输出，再执行全部正式签名、版本和对齐验证。这保留首次上传的签名字节，避免 AAB 签名时间等非确定性差异。已经存在的 APK/AAB 和校验文件必须与验证后的本地产物 SHA-256 一致；否则停止，不删除或覆盖已有有效附件。已有报告若证明相同包名、版本、APK/AAB 正式证书和对齐验证成功，会保留原报告字节，允许诊断文本发生变化。

GitHub 上传失败可能留下 `state=starter`、`size=0` 的空占位附件。仅当 Release 仍是草稿，且全部本地产物已验证、标签及占位状态重新核验通过，才删除这个空占位并重新上传。同名已上传、非空、其他异常状态或正式 Release 的附件不会被删除。

新 Release 先创建为草稿，四个附件上传完成后逐个核验状态、大小和下载内容的 SHA-256，最后才转为正式稳定 Release。上传中断会留下可恢复草稿；已经正式发布但不完整的 Release 只补缺件，不改标题、正文、发布时间或已有附件。新的正文优先使用 `docs/release-vX.Y.Z.md`，没有该文件时生成 Release notes。

发布 job 单独授予 `contents: write`，其余工作流保留 `contents: read`。全局并发组 `android-production-release` 使用 `cancel-in-progress: false` 和 `queue: max`，发布按队列串行执行；REST 创建标签使用非 force 的 create-ref，禁止移动历史标签。GitHub API 下载跳转到资源 CDN 时会移除跨主机的 `Authorization`，token 只用于 API 请求。

## 控制器与验证

控制器只使用 Python 标准库，三个命令都接收 `GITHUB_TOKEN`、`GITHUB_REPOSITORY`、`GITHUB_SHA`，以及可选的 `GITHUB_EVENT_BEFORE`。`detect` 输出 `should_release=true/false` 到 `GITHUB_OUTPUT`，并将 `VERSION_NAME`、`VERSION_CODE`、`RELEASE_TAG` 写入 `GITHUB_ENV`。三个命令都向日志和 `GITHUB_STEP_SUMMARY` 说明结果。

```bash
python3 scripts/release_ci.py detect
python3 scripts/release_ci.py restore-packages
python3 scripts/release_ci.py publish
python3 -m unittest discover -s scripts -p 'test_*.py' -v
```

`detect` 只查询；`restore-packages` 只查询并修改本地包文件；`publish` 会修改 GitHub，必须在签名验证步骤成功后调用。Release 和附件列表使用分页查询，annotated tag 使用 peeled commit。API 权限、限流、网络和内容读取错误全部停止发布，不把错误当作“版本不存在”。

验证真实新版本时，在 `main` 同时增加两项版本，检查 Actions 中 CI → 检测 → 构建/恢复 → 签名验证 → 发布的顺序；确认标签指向该提交、Release 为正式非 prerelease，四个附件名称及 SHA-256 正确。对同一运行重跑应显示完整 Release 已存在并成功跳过；同提交草稿缺件时重跑应恢复。离线测试覆盖递增、完整跳过、冲突优先、分页、上传中断、重建包恢复、报告保留、下载跳转和失败禁止正式发布，不会写入真实 GitHub。

YAML 结构、触发条件、job 依赖、权限和每个 `run` 的 Bash 语法均可独立检查。`queue: max` 使用 [GitHub 官方并发规范](https://docs.github.com/en/actions/reference/workflows-and-actions/workflow-syntax#concurrency)；actionlint 1.7.12 尚未识别该字段，检查时只豁免这一条已知语法诊断，其他诊断保持有效：

```bash
actionlint -ignore 'unexpected key "queue" for "concurrency" section' .github/workflows/build-release.yml
```
