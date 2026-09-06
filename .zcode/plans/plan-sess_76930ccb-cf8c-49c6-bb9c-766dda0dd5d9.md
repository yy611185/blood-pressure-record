# 整改计划（按优先级）

## P0 — 立即（防止真实健康数据/私钥泄露）
1. **更新 `.gitignore`**：新增 `测试照片/`、`scan_samples/`、`.qoder/`、`build/ocr-diagnostics/`，确保个人健康数据永不可能被 `git add .` 提交
2. **核实 git 历史中是否曾出现 `测试照片/`、`*.jks`、APK 等敏感文件**（`git log --all --diff-filter=A --name-only` 等只读检查）；若私钥曾入库 → 提示用户必须更换密钥并轮换相关 Secret
3. **将签名私钥移出仓库目录**：把 `keystore/blood-pressure-release.jks` 移到仓库外（如 `E:\keys\`），构建时通过 `BP_RELEASE_STORE_FILE` 环境变量指向新路径；同步更新 `docs/PUBLISHING.md` 与 `scripts/generate-keystore.sh` 的 Secret 命名（统一为 CI 实际使用的 `ANDROID_KEYSTORE_*`），并移除脚本中的示例个人信息

## P1 — 本周（供应链加固）
4. **GitHub Actions pin 到 commit SHA**：`build-release.yml` 中 6 个 action（checkout、setup-java、setup-android、wrapper-validation、upload-artifact、action-gh-release）改为 `@<sha>`，附版本注释
5. **修正 `scan_samples/README.md`** 的不实声明（实际未被忽略），改为真实说明
6. **清理工作区**：删除 `build/ocr-diagnostics/` 衍生图与 `.qoder/`；提示用户将 6 个根目录 APK 归档到仓库外（不主动删除用户产物，列清单确认）

## P2 — 计划内（代码与工程改进）
7. **README 重写**：消除 targetSdk 35/36 矛盾、删除重复的版本历史表和签名说明段、页脚版本号改 1.8.2、说明 namespace（com.example...）与 applicationId（com.yang.bloodpressure）的区别
8. **迁移到 `gradle/libs.versions.toml` 版本目录**，为后续依赖升级铺路
9. **加密小加固**（可选，需同步更新 `BackupCryptoTest`）：解密时对 header iterations 设下限（如 ≥100_000）；导出单元格对 `= + - @` 开头的用户文本加 `'` 前缀转义
10. **空目录清理**：删除 `ui/mock/`、`data/local/`
11. **恢复官方 Gradle wrapper 脚本**（gradlew/gradlew.bat）
12. 添加 `.github/dependabot.yml`（gradle + actions 周期更新）

## 不做的事
- 不主动升级 Compose/Room/Kotlin 等依赖大版本（改动面大，单独排期）
- 不删除用户的 APK 产物和测试照片本体（只保证 git 忽略 + 提示）