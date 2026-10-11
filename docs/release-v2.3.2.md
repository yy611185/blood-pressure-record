# 血压记录 v2.3.2

日期：2026-10-11。版本：2.3.2；versionCode：43；包名：`com.yang.bloodpressure`。

- 首页最近一次读数下新增四段等宽区间示意条，三角标记和加粗文字显示当前区间。
- 支持本地编辑两个轴的显示范围与分界点，校验通过后原子保存并更新位置。
- 支持 Empty、深浅色、大字、无障碍描述及系统关闭动画设置。
- 默认数字只是占位示例，不构成医学标准；实际分级及默认目标范围待产品确认。现有医学分级、风险提醒与本地数据库结构保持兼容。

APK 输出：`releases/blood-pressure-record-v2.3.2-release.apk`。沿用 v2.3.1 正式签名证书；证书 SHA-256 为 `46dfcbf4da054df8a8aca30e0df4df323161cbe4363a9eac7683a9da87d44012`。

构建与核验输出保留于 `releases/v2.3.2-release-verification.txt`，安装包 SHA-256 保留于 `releases/v2.3.2-release-sha256.txt`。本次生成本地安装包，未上传 GitHub。

230 项 JVM 测试与签名 Release 构建通过。APK 签名、旧版证书一致性、包名、版本号及 16 KB 页面 ZIP 对齐均已核验。安装包大小为 7,477,928 字节（约 7.13 MiB）；SHA-256 为 `7fd4b6b660e9693a58aafe1ccc5318310837ea6275c1524e7412b829a137338b`。
