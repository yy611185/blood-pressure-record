# APK 自动分发（香港 Lightsail 更新服务器）

本仓库的正式 Release 在签名与校验通过后，会自动把 APK 推送到更新服务器，
供 App 内更新检查使用。

## 终端信息

| 项目 | 值 |
|---|---|
| 更新清单 | `https://updates.0u0w0u0.com/android/stable/latest.json` |
| APK 下载 | `https://updates.0u0w0u0.com/android/stable/blood-pressure-record-v<版本>-release.apk` |
| 健康检查 | `https://updates.0u0w0u0.com/healthz` |
| 服务器 | AWS Lightsail 香港（Ubuntu 24.04 / Caddy，源站 8443 由 Cloudflare 前置） |
| 部署账号 | `deploybp`（**无 sudo**，仅能通过 SSH 强制命令执行发布） |

## 需要的 GitHub Secrets

在 `Settings → Secrets and variables → Actions` 添加：

| 名称 | 内容 | 敏感 |
|---|---|---|
| `BP_UPDATE_SSH_KEY` | `deploybp` 专用部署私钥（ed25519，OpenSSH 格式全文） | 是 |
| `BP_UPDATE_HOST_KEY` | 服务器 SSH 主机公钥行，用于固定指纹（防中间人） | 否（公钥） |
| `BP_UPDATE_HOST` | 服务器 IP（`43.199.54.40`） | 否 |
| `BP_UPDATE_USER` | `deploybp` | 否 |

## 发布流程（CI 侧）

`release` job 在 `Create tag and publish verified GitHub Release` 之后执行
`Publish signed APK to update server`：

1. 校验 APK 存在，并把它与 `release-sha256.txt` 中的摘要对齐；
2. 通过 SSH（固定主机指纹 + 专用密钥）把 APK **经 stdin 流式**送入服务器；
3. 服务器端工具接管校验与发布（见下）。

命令形状（等价）：

```bash
ssh -i deploy_key deploybp@<host> \
  "publish --version <版本> --version-code <版本码> --sha256 <摘要> --notes-b64 <说明> --file -" \
  < app-release.apk
```

## 服务器端保证（`/srv/bp-updates/bin/bp-publish`）

发布顺序固定，任何一步失败都不会更新 `latest.json`：

1. 参数白名单校验（版本号、摘要格式、文件名）；
2. **服务器重算 SHA-256**，与 CI 上报值不一致 → 拒绝（文件留在 staging）；
3. **APK 签名检查**：校验 ZIP 完整性、必需条目（`AndroidManifest.xml`/`classes.dex`），
   并检测 v1（`META-INF/*.RSA|DSA|EC`）或 v2/v3（APK Signing Block）签名；
4. **重复/覆盖防护**：同版本同内容 → 幂等成功；同版本不同内容 → 拒绝；
5. **降级防护**：版本低于线上最新 → 拒绝（回滚请用 `rollback`）；
6. **原子发布**：同一文件系统 `rename`，客户端不会读到半个文件；
7. **公网可下载校验**：经 Cloudflare 实际下载并比对 SHA-256，失败则不更新清单；
8. 才轮到**原子更新 `latest.json`**（`Cache-Control: no-store`），并追加 `versions.json` 历史。

旧版本 APK 一律保留，`rollback --version <版本>` 可把清单指回任意历史版本。

## 缓存策略

- `latest.json`：`no-store`（且 Cloudflare 对此扩展名默认不缓存）→ App 每次拿到最新版本；
- `*.apk`：`Cache-Control: public, max-age=31536000, immutable`
  → **APK 文件名必须含版本号**，否则同名覆盖会让客户端拿到旧包。

## 安全边界

- 不使用 root、不使用 `sudo`；`deploybp` 的密钥在服务器上被 `restrict` + 强制命令约束，
  只能执行 `publish` / `rollback`，无法执行任意命令、无法转发端口、无 PTY；
- 发布脚本由 `root` 拥有，部署账号不可修改；
- JKS / keystore / 用户数据从不上传（文件名由版本号推导，只能是 `.apk`）；
- CI 里签名用 keystore 仍只来自 GitHub Secrets，与分发链路完全分离。