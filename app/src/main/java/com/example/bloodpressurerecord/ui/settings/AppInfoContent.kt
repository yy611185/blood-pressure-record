package com.example.bloodpressurerecord.ui.settings

import com.example.bloodpressurerecord.BuildConfig

object AppInfoContent {
    val CURRENT_VERSION: String = BuildConfig.VERSION_NAME

    val featureSections = listOf(
        InfoSection(
            title = "本地血压记录",
            items = listOf(
                "支持一次记录多组收缩压、舒张压和脉搏，并自动计算平均值。",
                "支持备注记录，方便写下服药后、运动后、睡前等场景。",
                "数据保存在手机本地 Room 数据库中，不需要登录，也不会上传服务器。"
            )
        ),
        InfoSection(
            title = "历史与详情",
            items = listOf(
                "按日期查看历史测量记录，进入详情可查看原始多组读数。",
                "支持编辑和删除已有记录，删除前会进行确认提醒。",
                "自动显示平均血压、脉搏和血压分级，便于家庭成员快速理解。"
            )
        ),
        InfoSection(
            title = "趋势图",
            items = listOf(
                "趋势页提供收缩压、舒张压和双曲线视图。",
                "双曲线图支持最近 7 天、最近 30 天和全部记录范围切换。",
                "图表支持缩放、平移、节点点击和完整日期时间提示。"
            )
        ),
        InfoSection(
            title = "设置与备份",
            items = listOf(
                "可设置用户资料、目标血压、晨间/晚间提醒和显示偏好。",
                "支持导出 Excel 本地备份，包含使用说明、测量记录、用户资料和导出信息。",
                "支持清空本地数据，便于重新开始记录。"
            )
        ),
        InfoSection(
            title = "测量建议",
            items = listOf(
                "测量前安静休息 5 分钟，避免刚运动、饮酒、吸烟或摄入咖啡因后立即测量。",
                "尽量每天固定时间测量，每次至少记录两组，间隔 1-2 分钟。",
                "若出现持续不适或异常高值，请及时咨询医生或前往医院。"
            )
        ),
        InfoSection(
            title = "免责声明",
            items = listOf(
                "本应用仅用于家庭健康记录和趋势观察，不提供医疗诊断、治疗方案或处方建议。",
                "应用内分级、图表和提醒仅供参考，不能替代专业医生意见。",
                "请勿仅凭应用结果自行调整药物或停止治疗。"
            )
        )
    )
}

data class InfoSection(
    val title: String,
    val items: List<String>
)

data class ReleaseNote(
    val version: String,
    val summary: String,
    val changes: List<String>
)

object AppReleaseNotes {
    val notes = listOf(
        ReleaseNote(
            version = AppInfoContent.CURRENT_VERSION,
            summary = "历史页新增 Week / Month / All 范围切换，默认显示当周记录，并支持查看当月或全部历史。",
            changes = listOf(
                "历史记录页顶部新增 Week、Month、All 三段式切换按钮，视觉样式与当前 App 卡片和分段控件保持一致。",
                "Week 默认显示当周血压记录，Month 显示当月记录，All 显示从首次新增测量以来的全部记录。",
                "修复历史列表只能固定查看一小段时间的问题，同时保留默认本周视图，避免首页信息过载。",
                "同步修复历史页标题、日期、分类、空状态等当前生效界面的中文显示。",
                "版本更新至 v1.4.2。"
            )
        ),
        ReleaseNote(
            version = "1.4.1",
            summary = "Excel 备份导出升级为模板化写入，增强空表防护和导出诊断，确保历史数据能稳定写入备份文件。",
            changes = listOf(
                "新增 Excel 备份模板 backup_template_v1.xlsx，导出时优先写入模板，保持四个工作表和固定列结构稳定。",
                "导出链路增加空数据防护：未读取到历史测量记录时不再生成空表，而是提示诊断信息。",
                "导出成功信息加入新记录、原始读数和旧版记录数量，便于排查数据来源问题。",
                "收敛旧 CSV、旧 XLSX 和导入链路，当前数据管理页仅保留正式 Excel 本地备份导出。",
                "补充模板写入测试，验证历史血压数据可正确写入“测量记录”工作表。",
                "版本更新至 v1.4.1。"
            )
        ),
        ReleaseNote(
            version = "1.3.6",
            summary = "强化本地数据安全和 Excel 备份导出流程，清理趋势旧入口，并同步版本信息。",
            changes = listOf(
                "数据库升级不再使用破坏性迁移，避免后续表结构变化时误清空本地血压记录。",
                "数据管理页的 Excel 导出统一改为正式备份格式，导出前提示仅本地保存，并通过系统文件选择器保存到用户指定位置。",
                "应用说明与更新说明的当前版本号改为读取构建版本，减少版本号和更新说明不同步的风险。",
                "清理历史遗留的趋势二级路由注册，趋势入口集中在底部“趋势”栏。",
                "版本更新至 v1.3.6。"
            )
        ),
        ReleaseNote(
            version = "1.3.5",
            summary = "优化测量首页与趋势双曲线图，移除首页趋势入口，调整趋势范围切换和图表视觉细节。",
            changes = listOf(
                "测量首页删除“最近趋势”入口，让首页更聚焦最近读数、新增测量和今日概览。",
                "趋势页范围切换调整为 7天、30天、全部，删除 90天按钮。",
                "7天和30天分别限定显示对应时间范围内的数据，全部显示从有记录以来的所有数据。",
                "双曲线图采用更接近现代图表卡片的视觉：白底大圆角、轻阴影、浅边框、分段控制器和自定义图例。",
                "双曲线图新增更清晰的水平网格、90/140 参考线、白底描边节点和自定义提示卡片。"
            )
        ),
        ReleaseNote(
            version = "1.3.4",
            summary = "应用说明与更新说明重构，替换应用图标，继续优化趋势图性能和页面转场体验。",
            changes = listOf(
                "设置页“说明与免责说明”调整为“应用说明与更新说明”，并拆分为应用说明和更新说明两个三级页面。",
                "应用说明页补充当前 App 已实现功能、测量建议、数据本地化原则和免责声明。",
                "更新说明改为集中维护，后续每次版本号变更都需要同步追加版本概况，并由测试校验。",
                "使用新的心形图片作为应用启动图标，保持上传图片的视觉内容不变。",
                "趋势双折线图在拖动和缩放时减少复杂绘制，降低滑动卡顿。",
                "页面切换、进入和返回动画调整为更轻量、线性的过渡。"
            )
        )
    )

    fun hasCurrentVersion(): Boolean = notes.any { it.version == AppInfoContent.CURRENT_VERSION }
}
