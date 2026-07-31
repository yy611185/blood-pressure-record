# Handoff: 血压记录 App「暖阳打卡」温暖化 UI 改版

## Overview
为 Android 应用 **yy611185/blood-pressure-record**(Kotlin + Jetpack Compose + Material 3)做的全套 UI 温暖化改版,方向为「暖阳打卡」:奶油底色 + 陶土橙/鼠尾草绿双accent、超圆角与药丸形控件、亲切口语化文案、连续记录鼓励。信息架构与现有 App 完全一致(测量/历史/趋势/设置四个 Tab + 新增测量子页),**只换视觉与文案,不改功能与数据层**。

## About the Design Files
本包内的文件是 **HTML 设计参考稿**(原型),展示目标外观与交互,**不是可直接使用的生产代码**。任务是在现有 Kotlin + Jetpack Compose 代码库中,按其既有模式(MaterialTheme、ui/theme/ 下的 Color.kt / Type.kt / Theme.kt / DesignSystem.kt、ui/common/ 下的通用组件)**重新实现**这套设计 —— 主要通过替换主题 token 和调整组件样式完成,而非引入新框架。

- `血压记录 · 温暖化设计.dc.html` — 设计稿画布:第 3 组(id 3a–3e)是**最终采纳的五个页面**;第 2 组是三个方向探索;第 1 组是现状复刻(对照用)。
- `血压记录 · 交互原型.dc.html` — 可点击交互原型(标签切换、日历点选、表单校验与保存)。
- `organic.css` — 设计系统 token 源文件(所有颜色/圆角/阴影变量)。
- 注:HTML 内引用的 `_ds/...` 路径与 `android-frame.jsx` 是原型工具的运行环境,离线打开可能不完整;实现请以本 README + token 为准。

## Fidelity
**High-fidelity(高保真)**。颜色、字号、圆角、间距、文案均为最终值,请像素级对照实现;控件请继续用 Compose Material 3 组件,替换 ColorScheme/Typography/Shapes 即可覆盖大部分样式。

## Design Tokens(替换 ui/theme/Color.kt / Theme.kt)
背景与文字:
- 页面背景 background:`#F5EAD8`(奶油)
- 卡片表面 surface:`#EBDDC5`(沙色)
- 输入框/次级表面 surfaceVariant:`#F9F4ED`
- 主文字 onBackground/onSurface:`#201E1D`
- 次级文字(muted):`#82796A`;更弱:`#A19786`;禁用/占位:`#C0B6A5`
- 分隔线 outline:`rgba(32,30,29,0.16)`

主 accent(陶土橙,primary):
- 100 `#FFF2EB` / 200 `#FFE1D0` / 300 `#FFC6A5` / 400 `#F6A06B` / 500(base)`#C67139` / 600 `#B2622D` / 700 `#8C491A` / 800 `#643312` / 900 `#402310`
- primary=`#C67139`,onPrimary=`#FFF7EF`,primaryContainer=`#FFE1D0`,onPrimaryContainer=`#643312`
- 按压态用 600,浅底上的正文级橙色文字必须用 700+

副 accent(鼠尾草绿,secondary,用于「正常/健康」语义):
- 100 `#F0FAE1` / 200 `#E1EECC` / 300 `#CCDBB2` / 400 `#AEBF92` / 500 `#8FA073` / 600 `#728157` / 700 `#56633F` / 800 `#3D472B` / 900 `#272E1B`
- secondaryContainer=`#E1EECC`,onSecondaryContainer=`#3D472B`

血压状态色映射(替换 DesignSystem.kt 的 BloodPressureStatusStyle):
- 正常 NORMAL:底 `#E1EECC`、字 `#3D472B`、图标 circle-check
- 偏高/高风险(ELEVATED/HIGH/HIGH_RISK):底 `#FFE1D0`、字 `#643312`(日历标记用底 `#FFC6A5` 字 `#402310`)、图标 triangle-alert

圆角(Shapes):small=20dp(输入框)、medium=28dp(卡片,--radius-lg)、按钮/chip/导航=**全药丸 999dp**。禁止直角与细线框。

阴影:sm `0 1px 2px rgba(46,43,37,0.14)`、md `0 3px 10px rgba(46,43,37,0.16)`(大按钮)。

间距:页面水平 18dp;卡片内边距 20dp;区块间距 14–16dp;主按钮高 60dp(表单保存 58dp);触控目标 ≥44dp。

## Typography
- 数字展示(血压值):HTML 稿用 Caprasimo(仅拉丁/数字)。Android 上请打包 **Caprasimo Regular**(Google Fonts, OFL)用于纯数字文本;或退而用系统 sans-serif Bold。
- 中文标题:HTML 稿回退 ZCOOL KuaiLe(站酷快乐体,可免费商用);Android 可打包该字体用于页面大标题,或保持系统黑体 Bold。
- 正文:系统默认(Figtree 仅拉丁,可忽略)。
- 字阶:页面大标题 30sp(历史/趋势/设置 24sp);最近血压数字 54sp/行高1;卡片标题 15sp w600;正文 14sp;次级 13sp;说明 12sp;chip 13sp;导航 13sp(选中 w700)。

## Icons
Lucide 图标(https://lucide.dev),stroke-width 2.75。用到:house, history, chart-line, settings, plus, calendar, clock, chevron-left/right, circle-check, triangle-alert, flame, sun, user, bell, eye, folder, info。替换现有 Material Icons。

## Screens / Views(对应设计稿 3a–3e 与仓库文件)

### 3a 测量首页(DashboardScreen.kt)
- 顶部:日期行 13sp muted(“7月26日 星期日 · 早晨”);标题「早上好」30sp 标题字体;副文案“记录是为了帮你观察变化,身体的感觉你最懂。”13sp muted。
- 最近一次血压卡(surface 卡,28dp 圆角,shadow-sm,内边距 20dp):行1 “最近一次血压”15sp w600 + 时间 13sp muted;行2 数字“128 / 82”54sp Caprasimo 色 #8C491A + “mmHg”14sp muted;行3 状态药丸(见状态色,内含 15dp 图标,文案“血压平稳,继续保持”/偏高时“这次偏高,注意休息”)。
- 连续记录卡:底 `#E1EECC`,flame 图标 26dp 色 #B2622D;“已连续记录 N 天”15sp w700 色 #272E1B + “坚持得很好,为自己鼓个掌”12sp;右侧一周 7 个 10dp 圆点(有记录实心 #728157,无记录 1.5dp 描边 #8FA073)。
- 主按钮:高 60dp 全药丸,底 #C67139 字 #FFF7EF 18sp(标题字体),plus 图标 22dp,shadow-md;按压 #B2622D。
- 今日概览卡:“今天已经测了 2 次,平均 126 / 80 mmHg”(数字用 Caprasimo 色 #8C491A);评语 13sp muted;链接“看看今天的记录 →”14sp w600 色 #8C491A → 跳历史页定位今天。
- 底部导航:浮动药丸条(margin 16dp,surface 底,padding 8dp,shadow-sm);4 项各高 44dp 药丸,选中底 `#FFE1D0` 字/图标 `#643312` w700,未选中 `#82796A`。

### 3b 新增测量(AddMeasurementScreen.kt + SessionFormComponents.kt)
- 顶栏:44dp 圆形返回钮(surface 底,chevron-left)+ “记一次血压”22sp 标题字体。
- “什么时候测的?”:两个 50dp 药丸按钮(底 #F9F4ED,1dp outline 边),calendar/clock 图标 17dp 色 #8C491A。
- 场景 chips“在什么情况下测的?”:药丸 chip,选中底 #C67139 字 #FFF7EF w600,未选中底 #F9F4ED 边 outline 字 #645C50。选项:晨起/睡前/居家安静/运动后/其他。
- 读数卡「第 1 组 / 第 2 组」:surface 卡;三列输入(标签 12sp muted:“收缩压(高压)/舒张压(低压)/脉搏”),输入框高 56dp 圆角 20dp 底 #F9F4ED 边 outline,数字 24sp Caprasimo 居中色 #643312,聚焦边框 #C67139;卡内提示“建议连续测两次,间隔 1-2 分钟,取平均更准。”12sp muted。
- “再加一组”:48dp 虚线药丸(1.5dp dashed #D67F48,字 #8C491A)。
- 自动结果卡(填满≥1组时显示):底 #E1EECC;“两组平均”13sp;“126 / 80”32sp Caprasimo 色 #272E1B;评语:正常→“数值很平稳,记得保持规律作息。”,偏高(≥140/90)→“数值偏高,休息几分钟再复测一次会更放心。”
- 症状 chips“有没有不舒服?”:多选;“没有,挺好的”与其他互斥。选项:没有,挺好的/头痛/头晕/心悸/胸闷或胸痛/视物模糊/其他。
- 备注:多行输入,占位“想补充点什么?比如「早饭前测的」(选填)”。
- 底部保存条:surface 底、顶部圆角 28dp、shadow-md;按钮 58dp 药丸“保存这次记录”;不可保存时按钮置灰(底 #DCD3C4 字 #82796A)并显示原因 12sp:“把两组的高压和低压都填好,就可以保存啦”/“低压要小于高压,检查一下再保存”。

### 3c 历史(HistoryScreen.kt)
- 标题“历史记录”24sp;「日历/近期」分段控件:容器药丸底 #EEE7DB padding 5dp,选中项 surface 底 + shadow-sm + 字 #643312 w700,高 44dp。
- 鼓励条:药丸底 #E1EECC,sun 图标,“7 月你已经记录了 N 天,真不错”13sp w600 色 #272E1B。
- 日历卡:月份切换行(38dp 圆形按钮 + “2026年7月”16sp w700);周标签 日–六 12sp muted;**日期为 38dp 圆形**:无记录仅数字 14sp 色 #A19786;有记录底 #CCDBB2 字 #272E1B w700;含偏高读数底 #FFC6A5 字 #402310;选中底 #B2622D 字 #FFF7EF + shadow;图例(有记录/含偏高读数/选中)11sp。
- 选中日摘要卡:“7月26日 · 共 2 次测量”+“当天平均 128 / 82 mmHg”。
- 当日记录卡:左“08:12 · 晨起”14sp w700 + “脉搏 72 次/分”12sp muted;右“128/82”22sp Caprasimo 色 #8C491A + 状态小药丸(正常/偏高)。
- 近期模式:「本周/本月」同款分段控件;摘要卡(标题、日期区间 12sp、“测了 N 次,平均 S/D mmHg · 脉搏 P”、偏高提示 13sp);按日期分组列表(组头“7月26日 星期日”14sp w700),行卡同上。

### 3d 趋势(TrendScreen.kt + TrendChart.kt)
- 标题“血压趋势”24sp;口语化摘要卡:底 #E1EECC 14sp/1.7,“这 7 天测了 **12** 次,平均 **126 / 80** mmHg,比上一周低了 2/1,整体很平稳。”(数字加粗,平均值 17sp Caprasimo)。
- 图表卡:范围分段(近 7 天/近 30 天/全部,选中底 #C67139 字 #FFF7EF);指标分段(收缩压/舒张压/双曲线,选中 surface+shadow 字 #643312)。
- 折线:收缩压 #C67139、舒张压 #7A8A5E,线宽 3.5、圆头,数据点 4.5r 实心;目标参考线 #C0B6A5 虚线(140);轴标 9sp #82796A;图例含“目标线 140”。
- 统计卡 2 列:“最高一次 138/90”(#643312)/“最低一次 118/76”(#3D472B),22sp Caprasimo。
- 底注“短期变化仅供观察,不代表医学结论。”12sp muted。

### 3e 设置(SettingsScreen.kt)
- 标题“设置”24sp;五个列表卡:46dp 圆形图标底(橙绿交替:#FFE1D0/#E1EECC,图标 22dp 色 #643312/#3D472B),标题 15sp w700 + 副标题 13sp muted,右 chevron 20dp #A19786。条目文案与现状一致。
- 页脚“数据只保存在这台手机上,不会上传。”12sp muted 居中。

## Interactions & Behavior
- Tab 切换、新增页返回/保存返回,沿用现有 Navigation Compose 结构与转场。
- 表单校验(沿用 MeasurementInputRules.kt 逻辑):数字 ≤3 位;舒张压 ≥ 收缩压时标错并提示;两组高低压填齐才可保存;平均值实时计算展示。
- 日历:仅有记录的日期可点;点选更新当日摘要与记录列表。
- 保存成功:返回首页,Toast/Snackbar 药丸样式(底 #2E2B25 字 #F9F4ED,圆角 999)文案“保存好了,今天也辛苦啦”。
- 所有可点元素需有按压态(accent 加深一级);聚焦态 2dp #C67139 描边。
- 连续记录天数 = 从今天往前连续有记录的天数;一周圆点 = 最近 7 天每日是否有记录。

## State Management
无新增状态需求,全部复用现有 ViewModel(DashboardViewModel/HomeViewModel/HistoryViewModel/TrendViewModel/SettingsViewModel)。新增派生值:连续记录天数、近 7 天打卡点、月记录天数(可由现有 DAO 日汇总查询派生)。

## Assets
- Lucide 图标(OFL/ISC,lucide.dev)
- 字体:Caprasimo(Google Fonts, OFL)、ZCOOL KuaiLe(Google Fonts, 免费商用)
- 无位图资源

## Files
- `血压记录 · 温暖化设计.dc.html` — 五页最终稿(3a–3e)+ 探索与现状对照
- `血压记录 · 交互原型.dc.html` — 可点击原型(含全部交互规则的可运行参考)
- `organic.css` — 设计 token 源(CSS 变量)

## 仓库文件映射(改哪些文件)
| 改动 | 仓库文件 |
| --- | --- |
| 颜色/主题/圆角 | ui/theme/Color.kt, Theme.kt, DesignSystem.kt |
| 字体/字阶 | ui/theme/Type.kt(+ 打包字体到 res/font) |
| 按钮/卡片/状态chip/顶栏 | ui/common/AppComponents.kt |
| 表单控件/保存条 | ui/common/SessionFormComponents.kt |
| 首页 | ui/home/DashboardScreen.kt |
| 新增测量 | ui/record/AddMeasurementScreen.kt |
| 历史/日历 | ui/history/HistoryScreen.kt |
| 趋势 | ui/history/TrendScreen.kt, TrendChart.kt |
| 设置 | ui/settings/SettingsScreen.kt |
| 底部导航 | navigation/AppNavigation.kt(NavigationBar 改自定义药丸条) |
