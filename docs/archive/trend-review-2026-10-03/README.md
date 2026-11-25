# 趋势布局与 Review 验证截图

截图来自 Android API 34 模拟器与原生 Compose 测试，所有读数为固定测试数据。

| 文件 | 场景 |
|---|---|
| trend-fullscreen-light.png | 1920×1080、420 dpi，普通字号，主图占据横屏主体 |
| trend-fullscreen-dark-large.png | 同一窗口，深色、1.4 倍字体，右上读数保持可见 |
| trend-readout-normal.png | 288dp 宽读数区域，普通字号，五个按钮保持同一行 |
| trend-readout-small-large-dark.png | 248dp 宽读数区域，深色、2 倍字体，按钮水平滚动可达 |
| v2.2.1-trend-fullscreen-light.png | v2.2.1 普通字号，三点菜单紧邻“时分” |
| v2.2.1-trend-fullscreen-dark-large.png | v2.2.1 深色、1.4 倍字体，左侧菜单与右上读数保持可见 |

普通读数截图与大字读数截图来自首轮的 640 dpi 模拟器；这里的 dp 宽度与字体比例由测试指定。同行和可达性通过逐个滚动至按钮并验证同一纵坐标、至少 48dp 触控高度、点击回调；大字截图初始只显示前面按钮。

全部 199 项 JVM 测试、lint、Debug/AndroidTest 与签名 Release 构建通过。12 项定向设备用例最终通过；device-verification.txt 保存横屏与 5001 条数据库往返三项复验输出。完整变更和 F01–F10 对照见 [实施记录](../trend-fullscreen-2026-10-03.md)。

v2.2.1 菜单位置调整后，更新说明单元测试、lint 与签名构建通过，并重新运行两项横屏布局测试、检查上述两张新截图。安装包信息见 [v2.2.1 更新说明](../release-v2.2.1.md)。
