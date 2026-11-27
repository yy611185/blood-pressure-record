# UI/UX 设计审计参考知识库索引

本目录收录了两套公开的 UI/UX 设计审计标准与参考知识库，供后续 UI/UX 审计、视觉对齐及设计优化时直接查阅与联合调用。所有原始文件均保持原始目录结构、文件内容及语言，未做翻译、删减或改写，且已移除嵌套 `.git` 目录。

---

## 一、两套审计标准概览

### 1. Design Critique Agent（`design-audit`）

- **来源仓库**：https://github.com/wonjyou/design-audit
- **原作者**：Won J. You
- **许可证**：仓库未附带独立 LICENSE 文件（保留原始仓库元数据与作者归属）
- **下载时间**：2026-11-26 23:34 (UTC+08:00)
- **默认分支**：`main`
- **Commit SHA**：`bfb02a8842c858cd1903803789247587ee07f875`
- **定位与用途**：面向 UI/UX 设计师、产品团队与开发者的**结构化全链路设计审计框架**。通过 5 步审计流程（上下文采集 → 场景画像分类 → 六大维度审计 → ASK 框架与四级严重度评分 → 结构化报告输出），系统评估界面在视觉、交互、可用性、无障碍、人因工程、文案及信息架构方面的综合质量。
- **适用场景**：
  - 完整的产品界面/页面 UX 评审与启发式评估（Nielsen 10 项原则、WCAG 2.1 无障碍检查）；
  - 按产品形态（移动端 `mobile-app`、Web 应用 `web-app`、营销落地页 `marketing-site`）加载差异化审计标准；
  - 需要输出面向团队/开发的标准化审计报告（Markdown / HTML 模板、优先级汇总表、Quick Wins、开发交接清单）。

### 2. Design Review Plugin（`design-review-plugin`）

- **来源仓库**：https://github.com/gregorymm/design-review-plugin
- **原作者**：Gregory Muryn-Mukha（知识库内容源自 FormFactor 设计学院 / [@signartura](https://www.youtube.com/@signartura/videos) 46 节视频课程与实操评审）
- **许可证**：MIT License（详见 `design-review-plugin/LICENSE`）
- **下载时间**：2026-11-26 23:34 (UTC+08:00)
- **默认分支**：`main`
- **Commit SHA**：`62ebfc09299042377624abfe7c3d7b452aa89a88`
- **定位与用途**：基于 FormFactor 设计学院实战方法论的**像素级视觉规范与版式审查标准**。强调可量化的硬性设计尺度（标准尺寸阶梯、行高、最大文本宽度、对齐线、网格边距、脏色排查、Figma Auto-Layout 结构等），直接定位视觉粗糙与不专业细节。
- **适用场景**：
  - 像素级视觉走查、间距与尺寸阶梯校验（2/4/8/12/16/20/24/32...）、排版与色彩微调；
  - 排查常见视觉反模式（如“砖块式”拥挤布局、非标尺寸、自动行高、过度分割线、不一致圆角、微弱差异等）；
  - Figma 设计稿结构审查与作品集/案例展示评审。

---

## 二、文件目录与用途说明

### A. `design-audit/`（Design Critique Agent）

| 文件路径 | 主要用途 |
| --- | --- |
| `design-audit/README.md` | 项目总览：审计流程、触发方式、上下文画像、六大维度、四级严重度与报告结构说明 |
| `design-audit/SKILL.md` | 核心审计执行规范（Step 0A–Step 4）：上下文识别、输入判断、六大维度检查、ASK 反馈框架与输出格式 |
| `design-audit/references/visual-design.md` | 视觉设计审计细则：视觉层级、间距、排版、色彩、一致性与对比度 |
| `design-audit/references/ux-heuristics.md` | 可用性启发式与无障碍细则：Nielsen 10 项可用性原则、可供性（Affordance）、WCAG 2.1 无障碍标准 |
| `design-audit/references/ui-patterns.md` | 常见 UI 模式审计细则：按钮、表单、导航、弹窗、空状态与加载状态 |
| `design-audit/references/human-factors.md` | 人因工程与认知心理学细则：费茨定律、席克定律、米勒定律、格式塔原则与认知负荷 |
| `design-audit/references/ux-copy.md` | UX 文案审计细则：清晰度、CTA 措辞、标签、错误提示与语气一致性 |
| `design-audit/references/information-architecture.md` | 信息架构审计细则：导视、导航层级、标签分类与内容组织 |
| `design-audit/references/mobile-app.md` | 移动应用（iOS/Android）专属上下文审计补充标准 |
| `design-audit/references/web-app.md` | Web 应用（SaaS/仪表盘/后台）专属上下文审计补充标准 |
| `design-audit/references/marketing-site.md` | 营销站点与落地页专属上下文审计补充标准 |
| `design-audit/references/report-template.md` | Markdown 格式结构化审计报告模板 |
| `design-audit/references/report-template.html` | HTML 格式可视化审计报告模板 |

### B. `design-review-plugin/`（Design Review Plugin）

| 文件路径 | 主要用途 |
| --- | --- |
| `design-review-plugin/README.md` | 插件说明：7 大审查维度、作品集评审模式、反馈格式与知识库主题索引 |
| `design-review-plugin/LICENSE` | MIT 开源许可证原文 |
| `design-review-plugin/.claude-plugin/plugin.json` | 插件元信息配置（保留原始文件，未安装插件） |
| `design-review-plugin/.claude-plugin/marketplace.json` | 插件市场元信息配置（保留原始文件，未安装插件） |
| `design-review-plugin/skills/design-review/SKILL.md` | FormFactor 实战审查清单与 4 段式反馈规范（尺寸间距、视觉层级、排版、色彩、对齐一致性、布局构图、Figma 结构） |
| `design-review-plugin/skills/design-review/knowledge-base.md` | 完整设计知识库（含俄语专业术语与实战法则）：标准尺寸阶梯、接近性法则、矩形原则、F 型扫描、常见错误、排版、色彩理论、布局网格、CJM/线框图与作品集规范 |

---

## 三、两套标准的互补关系

| 对比维度 | Design Critique Agent (`design-audit`) | Design Review Plugin (`design-review-plugin`) | 互补价值 |
| --- | --- | --- | --- |
| **核心视角** | 产品体验、可用性、无障碍、信息架构、人因工程、UX 文案 | 像素级视觉工艺、尺寸阶梯、版式构图、色彩质感、对齐细节 | 宏观体验架构 + 微观视觉工艺全覆盖 |
| **判定方式** | 原则驱动 + 场景画像修饰器 + 四级严重度（Critical/Major/Minor/Cosmetic） | 量化阈值与硬性红线（如 4px/8px 阶梯、正文 ≥16px、行高 130–150%、最大行宽 600px） | 避免“只谈体验不落像素”或“只抠像素不看流程” |
| **报告形式** | 完整结构化报告（含优先级表、Quick Wins、保留优点、MD/HTML 模板） | 精炼直接的 4 段式实战点评（总体印象、关键问题、改进项、亮点） | 可按任务深度选择完整审计报告或快速视觉走查 |
| **典型盲区补足** | 偏重 UX 原则，对具体像素阶梯与视觉“脏色/拥挤”约束较宽 | 偏重视觉与版式，对 WCAG 无障碍、UX 文案与复杂交互状态覆盖较浅 | 联合使用可同时守住可用性底线与视觉精致度 |

---

## 四、后续 AI Agent 读取与联合使用指南

1. **明确审计目标与产品形态**：
   - 先确定当前评审对象属于 `mobile-app`、`web-app` 还是 `marketing-site`（如本项目为健康/血压记录类应用，按实际端形态选用对应画像）。
2. **主干流程读取 `design-audit`**：
   - 先阅读 `design-audit/SKILL.md` 确立审计步骤、ASK 反馈原则与四级严重度标准；
   - 按需加载 `design-audit/references/` 下对应的六大维度文件与场景画像文件（`mobile-app.md` / `web-app.md` / `marketing-site.md`）。
3. **视觉精细度叠加 `design-review-plugin`**：
   - 在执行 **Visual Design** 与 **UI Patterns** 审计时，同步加载 `design-review-plugin/skills/design-review/SKILL.md`；
   - 遇到具体的间距阶梯、排版行高、色彩脏浊、对齐线、网格与卡片分割线争议时，查阅 `design-review-plugin/skills/design-review/knowledge-base.md` 获取精确判定依据。
4. **合并去重与分级输出**：
   - 统一使用 `design-audit` 的四级严重度（🔴 Critical / 🟠 Major / 🟡 Minor / 🔵 Cosmetic）汇总两套标准的发现；
   - 每条问题同时给出**原则依据**（来自 `design-audit` 或 FormFactor 法则）与**具体像素/样式/文案修改建议**；
   - 若需输出正式报告，可直接套用 `design-audit/references/report-template.md` 或 `report-template.html`。
