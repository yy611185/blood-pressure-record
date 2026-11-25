---
name: 血压记录
description: Android Compose 暖色记录界面的当前设计说明
version: "2.3.11"
colors:
  primary: "#B94B39"
  primary-soft: "#FFDED5"
  primary-ink: "#963828"
  on-primary: "#FFFFFF"
  healthy: "#357D58"
  healthy-soft: "#CCF3D8"
  background: "#FAF6EF"
  surface: "#FFFDFA"
  surface-soft: "#F2ECE3"
  text: "#27201B"
  text-secondary: "#645C55"
  divider: "#E3DDD5"
  dark-primary: "#FFA997"
  dark-on-primary: "#40201B"
  dark-background: "#1A1512"
  dark-surface: "#26201C"
  dark-surface-soft: "#2D2823"
  dark-text: "#F2EEE7"
  risk: "#B3261E"
typography:
  display:
    fontFamily: "Bricolage Grotesque, sans-serif"
    fontSize: "54sp"
    fontWeight: 700
    lineHeight: "54sp"
    letterSpacing: "-1sp"
    fontFeature: "tnum"
  headline:
    fontFamily: "system-ui, sans-serif"
    fontSize: "30sp"
    fontWeight: 700
    lineHeight: "38sp"
  title:
    fontFamily: "system-ui, sans-serif"
    fontSize: "22sp"
    fontWeight: 600
    lineHeight: "28sp"
  body:
    fontFamily: "system-ui, sans-serif"
    fontSize: "16sp"
    fontWeight: 400
    lineHeight: "24sp"
  label:
    fontFamily: "system-ui, sans-serif"
    fontSize: "13sp"
    fontWeight: 500
    lineHeight: "18sp"
rounded:
  input: "16dp"
  button: "20dp"
  card: "26dp"
  hero: "32dp"
  pill: "50%"
spacing:
  xs: "4dp"
  sm: "8dp"
  md: "12dp"
  lg: "16dp"
  xl: "24dp"
  xxl: "32dp"
  page-gutter: "20dp"
components:
  button-primary:
    backgroundColor: "{colors.primary}"
    textColor: "{colors.on-primary}"
    rounded: "{rounded.button}"
    minHeight: "54dp"
  button-secondary:
    backgroundColor: "{colors.surface-soft}"
    textColor: "{colors.text}"
    rounded: "{rounded.button}"
    minHeight: "54dp"
  data-card:
    backgroundColor: "{colors.surface}"
    rounded: "{rounded.card}"
    padding: "18dp"
  reading-input:
    arrangement: "compact systolic, diastolic and optional pulse fields"
    minHeight: "76dp"
    rounded: "20dp"
  bottom-navigation:
    rounded: "50%"
    height: "64dp"
    centerButton: "52dp"
---

# 设计系统：血压记录

## 当前状态与依据

当前源码版本为 **2.3.11**（versionCode 52）。设计方向是“温暖的血压记录本”：纸色背景、暖色表面、醒目的读数和珊瑚色操作。视觉来源是 [根目录原型](UIredesign/血压记录%20重构.html)及其 `bp*.jsx`、`bp.css`；Android 使用真实数据、系统栏与原生数字键盘。当前行为和数值以 `app/src/main` 为准。

首页、记录、历史、趋势、我的及其相关子页均有原生实现；下文描述源码中已实现的界面，而非对所有设备的验收结论。[初轮实现记录](docs/ui-redesign-2026-09-25/IMPLEMENTATION.md)和[设计方向记录](docs/archive/directions.md)保留当时的决策、截图及测试结果，其中尺寸和流程可能已过时。`UIredesign/v1-原稿` 是更早的历史稿。

## 颜色与文字

frontmatter 列出浅色主题主要 sRGB 值；运行时映射以 `ui/theme/Color.kt` 和 `Theme.kt` 为准。浅色主题的操作主色是 `#B94B39`，`#DF6A55` 是保留的珊瑚色阶，不等同于 Material 主题的 `primary`。深色主题使用独立的暖棕表面与较亮主色。趋势图的收缩压线为暖橙色、舒张压线为蓝色；医生报告使用固定印刷色 #C4513E 和 #1F5FA8。正常语义使用绿色，错误和高风险使用错误色；血压分级另有偏低、正常、正常高值、1/2/3 级六套颜色。等级和风险应同时以文字或图标表达，阈值以领域代码为准。

读数与统计数字使用离线打包的 Bricolage Grotesque；主题大读数样式及显式调用 `tabularNums()` 的文本启用等宽数字；中文、标题和正文使用 Android 系统字体。字体位于 `app/src/main/res/font/bricolage_grotesque.ttf`，许可位于 `app/src/main/assets/licenses/bricolage_grotesque_license.txt`。应用大字模式将系统字号倍率乘以 1.15，合成倍率上限为 2.0。窄屏及大字号下应优先保证数值、单位与状态的对应关系，通过换行或滚动容纳内容。

## 布局与组件

页面水平边距为 20dp，常用间距为 4/8/12/16/24/32dp，通用卡片内边距为 18dp。主题形状包括 16dp 输入、20dp 按钮、26dp 卡片和 32dp 大容器圆角；具体页面可按内容调整。公共主、次级和危险按钮至少 54dp 高，录入保存按钮至少 58dp，内容可使按钮增高；通用最小触摸目标为 48dp。

顶级页面的悬浮 Dock 提供首页、历史、趋势、我的及中央新增入口；设置可隐藏趋势入口。胶囊高 64dp，中央按钮直径 52dp，左右边距 16dp，底部随系统安全区避让；滚动时可收为右下角新增按钮。趋势全屏时隐藏 Dock。空状态与真实数据使用同一布局，不把原型中的样例姓名或读数当作实际记录。

## 主要交互

- 新增和编辑采用纵向多组表单。每组高压、低压和选填脉搏默认紧凑并排；空间不足时高低压并排、脉搏另起一行。数字输入框最小高 76dp，字号随字体缩放调整；系统数字键盘和焦点链辅助录入，触摸探索模式下不自动跳焦点。
- 新增须填 2–20 组。编辑旧的单组记录时允许保留一组；表单显示平均值、分级与校验信息。补充情况在原地折叠展开，支持常用词点选。退出时可保留草稿，跨天恢复时提供使用当前时间或清空重记。异常读数和高风险读数需要相应确认。
- 从顶级页面进入新增时，保存成功后回到首页；从子页进入则返回该子页。全局提示显示已保存的结果；编辑保存后返回详情并提示。记录详情、备注搜索、日历和趋势均基于本地数据。趋势支持全屏查看及图表手势，具体交互见[趋势全屏规格](docs/specs/trend-fullscreen-2026-10-03.md)。
- 首页展示最近记录、当日测量和本周区间图。周图用实心上端点表示日均高压、空心下端点表示日均低压，点选日期查看当天摘要；四色区间条和目标设置的设计与边界见[区间条规格](docs/specs/bp-range-bar.md)。数据管理、提醒、医生报告和桌面小部件沿用原生实现；报告选项与导出见[医生报告规格](docs/specs/doctor-report.md)。

## 动效与无障碍

`AppMotion.kt` 集中定义按压、切换、折叠、Dock、图表等动效：位移和缩放采用弹簧，透明度与颜色采用缓动；主题颜色过渡为 200ms。系统动画时长缩放为零时，支持减少动态效果的路径直接落位或取消过渡。状态、输入标签和操作保留文字及语义说明，不依靠颜色区分；检查时覆盖深浅色、大字、窄屏、空数据、真实数据和键盘避让。

## 相关资料

- [需求与方案](docs/specs/需求和方案.md)：产品需求与方案背景。
- [UI 重设计记录](docs/ui-redesign-2026-09-25/README.md)：原型与首轮实现的历史资料。
- [UI 审查归档](docs/archive/ui-review-2026-10-13/REVIEW.md)和[动效优化计划](docs/archive/MOTION_OPTIMIZATION_PLAN.md)：历史审查和计划，不能替代当前代码。
